package org.apache.utlx.core.interpreter

import org.apache.utlx.core.ast.Node
import org.apache.utlx.core.ast.Program
import org.apache.utlx.core.ast.Location

/**
 * Enhances interpreter runtime error messages by analyzing the full context
 * and providing more helpful suggestions based on common mistakes and lessons learned.
 *
 * This enhancer integrates lessons learned from real-world UTLX code errors,
 * documented in /docs/gen-ai/lsp-diagnostics-schema.json
 *
 * The enhancer has access to:
 * - The full AST (for structural analysis)
 * - The environment (for variable inspection)
 * - Input metadata (for field name suggestions from CSV headers, JSON keys, etc.)
 * - Source code (for context-aware messages)
 * - Current execution context (for detecting lambda vs global scope)
 */
object InterpreterErrorEnhancer {

    /**
     * Context information for enhanced error analysis
     */
    data class ErrorContext(
        val error: RuntimeError,
        val node: Node? = null,
        val env: Environment? = null,
        val program: Program? = null,
        val source: String? = null,
        val inputMetadata: Map<String, InputMetadata>? = null,
        val currentFunction: String? = null  // Name of function being executed (filter, map, etc.)
    )

    /**
     * Metadata about input data for smarter field suggestions
     */
    data class InputMetadata(
        val name: String,
        val format: String,
        val fields: List<String>? = null,  // Available field names from CSV headers, JSON keys, etc.
        val sampleValue: RuntimeValue? = null,
        val recordCount: Int? = null
    )

    /**
     * Enhance a runtime error with a more helpful error message
     *
     * @param context The error context with all available information
     * @return Enhanced runtime error with better error message
     */
    fun enhance(context: ErrorContext): RuntimeError {
        val enhancedMessage = detectCommonMistakes(context)

        return if (enhancedMessage != null) {
            RuntimeError(enhancedMessage, context.error.location)
        } else {
            context.error // Return original if no enhancement found
        }
    }

    /**
     * Simple overload for backward compatibility
     */
    fun enhance(error: RuntimeError, node: Node? = null, env: Environment? = null): RuntimeError {
        return enhance(ErrorContext(error, node, env))
    }

    /**
     * Detect common mistake patterns and return enhanced error message
     */
    private fun detectCommonMistakes(context: ErrorContext): String? {
        val originalMessage = context.error.message ?: return null

        // UTLX-002: Undefined variable in lambda context
        // Common mistake: Missing lambda parameter reference
        if (originalMessage.startsWith("Undefined variable:")) {
            val varName = originalMessage.substringAfter("Undefined variable: ").trim()
            return detectMissingLambdaParameter(varName, context)
        }

        return null
    }

    /**
     * Detect missing lambda parameter in field access (UTLX-002)
     * Example: filter($employees, Department == "Sales") should be filter($employees, e => e.Department == "Sales")
     *
     * This function now uses input metadata to provide SMART suggestions:
     * - Checks if the undefined variable matches an actual field name in the input data
     * - Suggests the correct syntax with the actual field name
     * - Shows available fields if the name doesn't match (typo detection)
     */
    private fun detectMissingLambdaParameter(varName: String, context: ErrorContext): String? {
        // Check if this looks like a field name (PascalCase or camelCase starting with uppercase)
        val looksLikeFieldName = varName.matches(Regex("^[A-Z][a-zA-Z0-9]*$"))

        if (looksLikeFieldName) {
            // Smart analysis: Check if this field exists in any input metadata
            val matchingInput = context.inputMetadata?.entries?.find { (_, metadata) ->
                metadata.fields?.any { it.equals(varName, ignoreCase = true) } == true
            }

            if (matchingInput != null) {
                val inputName = matchingInput.key
                val actualFieldName = matchingInput.value.fields?.find { it.equals(varName, ignoreCase = true) }

                // We know this is a real field from the input data!
                return """
                    |Undefined variable: '$varName' (UTLX-002)
                    |
                    |This field exists in input '$inputName' but requires a lambda parameter to access it.
                    |
                    |Lambda expressions require explicit parameter references:
                    |
                    |❌ Incorrect: filter(\$$inputName, $actualFieldName == "value")
                    |✅ Correct:   filter(\$$inputName, e => e.$actualFieldName == "value")
                    |
                    |❌ Incorrect: map(\$$inputName, { id: $actualFieldName })
                    |✅ Correct:   map(\$$inputName, item => { id: item.$actualFieldName })
                    |
                    |Available fields in '\$$inputName': ${matchingInput.value.fields?.joinToString(", ")}
                    |
                    |See: https://utlx-lang.org/docs/variables#scoping
                """.trimMargin()
            }

            // Check for potential typos by finding similar field names
            val similarFields = context.inputMetadata?.values?.flatMap { metadata ->
                metadata.fields?.filter { field ->
                    levenshteinDistance(field.lowercase(), varName.lowercase()) <= 2
                } ?: emptyList()
            }?.distinct()

            if (!similarFields.isNullOrEmpty()) {
                return """
                    |Undefined variable: '$varName' (UTLX-002)
                    |
                    |Field '$varName' not found. Did you mean one of these?
                    |${similarFields.joinToString("\n") { "  • $it" }}
                    |
                    |Lambda expressions require explicit parameter references:
                    |
                    |❌ Incorrect: filter(${'$'}collection, $varName == "value")
                    |✅ Correct:   filter(${'$'}collection, item => item.FieldName == "value")
                    |
                    |Tip: Check your CSV headers, JSON keys, or XML elements for the exact field name.
                    |
                    |See: https://utlx-lang.org/docs/variables#scoping
                """.trimMargin()
            }

            // Generic field name error with context from current function
            val functionContext = if (context.currentFunction != null) {
                "in ${context.currentFunction}()"
            } else {
                ""
            }

            return """
                |Undefined variable: '$varName' (UTLX-002) $functionContext
                |
                |This error often occurs when accessing fields without a lambda parameter.
                |
                |Lambda expressions require explicit parameter references:
                |
                |❌ Incorrect: filter(${'$'}employees, Department == "Sales")
                |✅ Correct:   filter(${'$'}employees, e => e.Department == "Sales")
                |
                |❌ Incorrect: map(${'$'}employees, { id: EmployeeID })
                |✅ Correct:   map(${'$'}employees, emp => { id: emp.EmployeeID })
                |
                |Suggestion: Add a lambda parameter and reference it
                |Example: filter(${'$'}collection, item => item.$varName == value)
                |${getAvailableFieldsHint(context)}
                |See: https://utlx-lang.org/docs/variables#scoping
            """.trimMargin()
        }

        // Check if missing $ prefix for input variable
        val couldBeInputVar = varName.matches(Regex("^(input|data|employees|customers|orders|products?)\\d*$"))

        // Smart check: Does this match an actual input name?
        val isActualInputName = context.inputMetadata?.containsKey(varName) == true

        if (couldBeInputVar || isActualInputName) {
            val availableInputs = context.inputMetadata?.keys?.joinToString(", ") { "\$$it" }
                ?: "Check your input declarations"

            return """
                |Undefined variable: '$varName' (UTLX-003)
                |
                |Input variables must be prefixed with '\$'.
                |${if (isActualInputName) "The input '$varName' is declared in your header." else ""}
                |
                |❌ Incorrect: filter($varName, e => e.active)
                |✅ Correct:   filter(\$$varName, e => e.active)
                |
                |Input variables declared in the header (e.g., 'input $varName csv')
                |are referenced with a '\$' prefix in the transformation code.
                |
                |Available inputs: $availableInputs
                |
                |See: https://utlx-lang.org/docs/input-output#input-variables
            """.trimMargin()
        }

        return null
    }

    /**
     * Get hint about available fields from input metadata
     */
    private fun getAvailableFieldsHint(context: ErrorContext): String {
        val allFields = context.inputMetadata?.values
            ?.flatMap { it.fields ?: emptyList() }
            ?.distinct()

        return if (!allFields.isNullOrEmpty()) {
            "\nAvailable fields: ${allFields.take(10).joinToString(", ")}${if (allFields.size > 10) "..." else ""}"
        } else {
            ""
        }
    }

    /**
     * Calculate Levenshtein distance for typo detection
     * Returns the minimum number of single-character edits needed to change one string into another
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[len1][len2]
    }

    /**
     * Check if environment has input variables (variables starting with $)
     *
     * Note: This is a simplified check. Since we're in an execution context,
     * we assume input variables exist. A more sophisticated check would
     * traverse the environment chain, but that requires access to internal bindings.
     */
    private fun hasInputVariables(env: Environment): Boolean {
        return true // Assume we're in execution context with inputs
    }

    /**
     * Enhance error message for type mismatches
     */
    fun enhanceTypeMismatch(expectedType: String, actualType: String, location: Location? = null): String {
        return """
            |Type mismatch (UTLX-008)
            |
            |Expected: $expectedType
            |Actual:   $actualType
            |
            |Common causes:
            |• Comparing number to string: x.count == "10" should be x.count == 10
            |• Missing quotes on string: status == Sales should be status == "Sales"
            |• Wrong field type: Ensure the field contains the expected data type
            |
            |See: https://utlx-lang.org/docs/types#type-system
        """.trimMargin()
    }

    /**
     * Enhance error message for assignment operator misuse
     */
    fun enhanceAssignmentOperator(location: Location? = null): String {
        return """
            |Assignment operator '=' used in comparison (UTLX-006)
            |
            |Use '==' for equality comparison, not '=' which is for assignment.
            |
            |❌ Incorrect: filter(${'$'}data, x => x.value = 10)
            |✅ Correct:   filter(${'$'}data, x => x.value == 10)
            |
            |UTLX Comparison Operators:
            |• ==  equality
            |• !=  inequality
            |• >   greater than
            |• <   less than
            |• >=  greater than or equal
            |• <=  less than or equal
            |
            |See: https://utlx-lang.org/docs/operators#comparison
        """.trimMargin()
    }
}
