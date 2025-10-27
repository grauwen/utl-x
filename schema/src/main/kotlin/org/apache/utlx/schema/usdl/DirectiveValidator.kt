package org.apache.utlx.schema.usdl

import mu.KotlinLogging
import org.apache.utlx.core.udm.UDM

private val logger = KotlinLogging.logger {}

/**
 * USDL Directive Validator
 *
 * Validates USDL directives against the USDL 1.0 specification catalog.
 * Provides helpful error messages with suggestions for typos.
 */
class DirectiveValidator(
    private val strictMode: Boolean = true,
    private val targetFormat: String? = null
) {

    /**
     * Validation result
     */
    sealed class ValidationResult {
        data class Success(val message: String? = null) : ValidationResult()
        data class Warning(val message: String, val directive: String) : ValidationResult()
        data class Error(val message: String, val directive: String, val suggestion: String? = null) : ValidationResult()
    }

    /**
     * Validate USDL schema UDM
     */
    fun validate(udm: UDM): List<ValidationResult> {
        if (udm !is UDM.Object) {
            return listOf(ValidationResult.Error(
                message = "USDL schema must be an object type",
                directive = "<root>"
            ))
        }

        val results = mutableListOf<ValidationResult>()

        // Check for required %types directive
        if (!udm.properties.containsKey("%types")) {
            results.add(ValidationResult.Error(
                message = "USDL 1.0 requires %types property",
                directive = "%types",
                suggestion = "Add %types: { TypeName: { %kind: \"structure\", ... } }"
            ))
            return results // Cannot continue without %types
        }

        // Validate top-level directives
        results.addAll(validateObject(udm, USDL10.Scope.TOP_LEVEL))

        // Validate type definitions within %types
        val types = udm.properties["%types"]
        if (types is UDM.Object) {
            types.properties.forEach { (typeName, typeDef) ->
                if (typeDef is UDM.Object) {
                    results.addAll(validateTypeDefinition(typeName, typeDef))
                }
            }
        }

        return results
    }

    /**
     * Validate an object's directives against allowed scope
     */
    private fun validateObject(obj: UDM.Object, scope: USDL10.Scope): List<ValidationResult> {
        val results = mutableListOf<ValidationResult>()

        obj.properties.keys.filter { it.startsWith("%") }.forEach { key ->
            results.addAll(validateDirective(key, scope))
        }

        return results
    }

    /**
     * Validate a type definition
     */
    private fun validateTypeDefinition(typeName: String, typeDef: UDM.Object): List<ValidationResult> {
        val results = mutableListOf<ValidationResult>()

        // Check for required %kind directive
        val kind = typeDef.properties["%kind"]
        if (kind == null) {
            results.add(ValidationResult.Error(
                message = "Type '$typeName' missing required %kind directive",
                directive = "%kind",
                suggestion = "Add %kind: \"structure\" (or \"enumeration\", \"primitive\", \"array\", \"union\")"
            ))
            return results
        }

        // Validate kind value
        val kindValue = (kind as? UDM.Scalar)?.value as? String
        val validKinds = setOf("structure", "enumeration", "primitive", "array", "union", "interface")
        if (kindValue !in validKinds) {
            results.add(ValidationResult.Error(
                message = "Invalid %kind value: '$kindValue'",
                directive = "%kind",
                suggestion = "Valid kinds: ${validKinds.joinToString(", ")}"
            ))
        }

        // Validate type-level directives
        results.addAll(validateObject(typeDef, USDL10.Scope.TYPE_DEFINITION))

        // Validate fields if present
        val fields = typeDef.properties["%fields"]
        if (fields is UDM.Array) {
            fields.elements.forEach { fieldUdm ->
                if (fieldUdm is UDM.Object) {
                    results.addAll(validateFieldDefinition(typeName, fieldUdm))
                }
            }
        }

        // Validate enumeration values if present
        val values = typeDef.properties["%values"]
        if (values is UDM.Array) {
            values.elements.forEach { valueUdm ->
                if (valueUdm is UDM.Object) {
                    results.addAll(validateObject(valueUdm, USDL10.Scope.ENUMERATION))
                }
            }
        }

        return results
    }

    /**
     * Validate a field definition
     */
    private fun validateFieldDefinition(typeName: String, field: UDM.Object): List<ValidationResult> {
        val results = mutableListOf<ValidationResult>()

        // Check for required %name directive
        if (!field.properties.containsKey("%name")) {
            results.add(ValidationResult.Error(
                message = "Field in type '$typeName' missing required %name directive",
                directive = "%name"
            ))
        }

        // Check for required %type directive
        if (!field.properties.containsKey("%type")) {
            results.add(ValidationResult.Error(
                message = "Field in type '$typeName' missing required %type directive",
                directive = "%type"
            ))
        }

        // Validate field-level directives
        results.addAll(validateObject(field, USDL10.Scope.FIELD_DEFINITION))

        // Validate constraints if present
        val constraints = field.properties["%constraints"]
        if (constraints is UDM.Object) {
            results.addAll(validateObject(constraints, USDL10.Scope.CONSTRAINT))
        }

        return results
    }

    /**
     * Validate a single directive
     */
    private fun validateDirective(directiveName: String, scope: USDL10.Scope): List<ValidationResult> {
        val results = mutableListOf<ValidationResult>()

        // Check if directive exists in USDL 1.0
        val directive = USDL10.getDirective(directiveName)

        if (directive == null) {
            // Unknown directive - check for typos
            val suggestion = findClosestMatch(directiveName, USDL10.VALID_DIRECTIVE_NAMES)
            results.add(ValidationResult.Error(
                message = "Unknown USDL directive '$directiveName' at scope ${scope.name}",
                directive = directiveName,
                suggestion = suggestion?.let { "Did you mean '$it'?" }
            ))
            return results
        }

        // Check if directive is valid in this scope
        if (scope !in directive.scopes) {
            val validScopes = directive.scopes.joinToString(", ") { it.name }
            results.add(ValidationResult.Error(
                message = "Directive '$directiveName' not valid in scope ${scope.name}",
                directive = directiveName,
                suggestion = "Valid scopes: $validScopes"
            ))
            return results
        }

        // Check if directive is reserved (Tier 4)
        if (directive.tier == USDL10.Tier.RESERVED) {
            if (strictMode) {
                results.add(ValidationResult.Error(
                    message = "Directive '$directiveName' is reserved for future USDL versions",
                    directive = directiveName,
                    suggestion = "This directive will be available in USDL 2.0"
                ))
            } else {
                results.add(ValidationResult.Warning(
                    message = "Directive '$directiveName' is reserved for future USDL versions",
                    directive = directiveName
                ))
            }
            return results
        }

        // Check if directive is supported for target format
        if (targetFormat != null && !USDL10.isDirectiveSupportedForFormat(directiveName, targetFormat)) {
            val supportedFormats = USDL10.getSupportedFormats(directiveName)
            results.add(ValidationResult.Warning(
                message = "Directive '$directiveName' not fully supported for format '$targetFormat'",
                directive = directiveName
            ))
            logger.debug { "Directive $directiveName supported formats: $supportedFormats" }
        }

        return results
    }

    /**
     * Find closest matching directive name (for typo suggestions)
     * Uses Levenshtein distance
     */
    private fun findClosestMatch(input: String, candidates: Set<String>, threshold: Int = 3): String? {
        return candidates
            .map { it to levenshteinDistance(input, it) }
            .filter { it.second <= threshold }
            .minByOrNull { it.second }
            ?.first
    }

    /**
     * Calculate Levenshtein distance between two strings
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

    companion object {
        /**
         * Quick validation without instance
         */
        fun quickValidate(udm: UDM, targetFormat: String? = null): List<ValidationResult> {
            return DirectiveValidator(strictMode = true, targetFormat = targetFormat).validate(udm)
        }

        /**
         * Lenient validation (warnings instead of errors for reserved directives)
         */
        fun lenientValidate(udm: UDM, targetFormat: String? = null): List<ValidationResult> {
            return DirectiveValidator(strictMode = false, targetFormat = targetFormat).validate(udm)
        }
    }
}
