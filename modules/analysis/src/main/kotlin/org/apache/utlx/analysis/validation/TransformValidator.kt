// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/validation/TransformValidator.kt
package org.apache.utlx.analysis.validation

import org.apache.utlx.analysis.types.*
import org.apache.utlx.core.ast.*

/**
 * Validator for UTL-X transformations
 * 
 * Validates that:
 * - All referenced paths exist in input schema
 * - Type operations are valid
 * - Output matches expected schema (if provided)
 * - Required fields are populated
 * - Type conversions are safe
 */
class TransformValidator {
    
    /**
     * Validate a transformation against input and optionally output schema
     */
    fun validate(
        program: Program,
        inputType: TypeDefinition,
        expectedOutputType: TypeDefinition? = null
    ): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Create validation context
        val context = ValidationContext(inputType, errors, warnings)
        
        // Validate declarations (functions, variables)
        program.declarations.forEach { declaration ->
            validateDeclaration(declaration, context)
        }
        
        // Validate main expression
        validateExpression(program.mainExpression, context)
        
        // If expected output provided, validate structure matches
        if (expectedOutputType != null) {
            val inference = AdvancedTypeInference(inputType)
            val actualOutputType = inference.inferOutputType(program)
            
            validateTypeCompatibility(
                actualOutputType,
                expectedOutputType,
                "output",
                context
            )
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validate a declaration (function or variable)
     */
    private fun validateDeclaration(declaration: Declaration, context: ValidationContext) {
        when (declaration) {
            is LetDeclaration -> {
                val valueType = inferExpressionType(declaration.value, context)
                context.bindVariable(declaration.name, valueType)
            }
            is FunctionDeclaration -> {
                // Validate function body
                val functionContext = context.createChildContext()
                declaration.parameters.forEach { param ->
                    // For now, use Any type for parameters
                    // In a full implementation, we'd use declared types
                    functionContext.bindVariable(param.name, TypeDefinition.Any)
                }
                validateExpression(declaration.body, functionContext)
            }
        }
    }
    
    /**
     * Validate an expression
     */
    private fun validateExpression(expr: Expression, context: ValidationContext) {
        when (expr) {
            is PathExpression -> validatePathExpression(expr, context)
            is ObjectExpression -> validateObjectExpression(expr, context)
            is ArrayExpression -> validateArrayExpression(expr, context)
            is BinaryOperation -> validateBinaryOperation(expr, context)
            is FunctionCall -> validateFunctionCall(expr, context)
            is IfExpression -> validateIfExpression(expr, context)
            is MatchExpression -> validateMatchExpression(expr, context)
            is LetExpression -> validateLetExpression(expr, context)
            is PipeExpression -> validatePipeExpression(expr, context)
            is VariableReference -> validateVariableReference(expr, context)
            // Literals are always valid
            is StringLiteral, is NumberLiteral, is BooleanLiteral, is NullLiteral -> {}
            else -> context.addWarning("Unknown expression type: ${expr::class.simpleName}")
        }
    }
    
    /**
     * Validate path expression (e.g., input.Order.Customer.Name)
     */
    private fun validatePathExpression(path: PathExpression, context: ValidationContext) {
        var currentType = context.inputType
        val fullPath = path.segments.joinToString(".") { it.name }
        
        path.segments.forEachIndexed { index, segment ->
            when (currentType) {
                is TypeDefinition.Object -> {
                    val property = currentType.properties[segment.name]
                    if (property == null) {
                        context.addError(
                            "Path '$fullPath': field '${segment.name}' not found in object. " +
                            "Available fields: ${currentType.properties.keys.joinToString(", ")}"
                        )
                        return
                    }
                    currentType = property.type
                }
                
                is TypeDefinition.Array -> {
                    if (segment.isArrayAccess) {
                        currentType = currentType.elementType
                    } else {
                        // Implicit iteration over array
                        context.addWarning(
                            "Path '$fullPath': implicit array iteration at '${segment.name}'. " +
                            "Consider using explicit map operation for clarity."
                        )
                        currentType = currentType.elementType
                    }
                }
                
                is TypeDefinition.Any -> {
                    context.addWarning(
                        "Path '$fullPath': cannot validate path segment '${segment.name}' " +
                        "(type is Any)"
                    )
                    return
                }
                
                else -> {
                    context.addError(
                        "Path '$fullPath': cannot access field '${segment.name}' on type ${currentType::class.simpleName}"
                    )
                    return
                }
            }
        }
    }
    
    /**
     * Validate object construction
     */
    private fun validateObjectExpression(obj: ObjectExpression, context: ValidationContext) {
        obj.properties.values.forEach { expr ->
            validateExpression(expr, context)
        }
    }
    
    /**
     * Validate array construction
     */
    private fun validateArrayExpression(arr: ArrayExpression, context: ValidationContext) {
        arr.elements.forEach { expr ->
            validateExpression(expr, context)
        }
    }
    
    /**
     * Validate binary operation
     */
    private fun validateBinaryOperation(op: BinaryOperation, context: ValidationContext) {
        validateExpression(op.left, context)
        validateExpression(op.right, context)
        
        val leftType = inferExpressionType(op.left, context)
        val rightType = inferExpressionType(op.right, context)
        
        // Check type compatibility for operations
        when (op.operator) {
            "+", "-", "*", "/", "%"-> {
                if (!isNumericType(leftType) || !isNumericType(rightType)) {
                    context.addError(
                        "Operator '${op.operator}' requires numeric operands, " +
                        "got ${leftType::class.simpleName} and ${rightType::class.simpleName}"
                    )
                }
            }
            "==", "!=" -> {
                // Comparison works on any types
            }
            "<", ">", "<=", ">=" -> {
                if (!isComparableType(leftType) || !isComparableType(rightType)) {
                    context.addWarning(
                        "Comparison operator '${op.operator}' may not work with " +
                        "${leftType::class.simpleName} and ${rightType::class.simpleName}"
                    )
                }
            }
            "&&", "||" -> {
                if (!isBooleanType(leftType) || !isBooleanType(rightType)) {
                    context.addError(
                        "Logical operator '${op.operator}' requires boolean operands, " +
                        "got ${leftType::class.simpleName} and ${rightType::class.simpleName}"
                    )
                }
            }
        }
    }
    
    /**
     * Validate function call
     */
    private fun validateFunctionCall(call: FunctionCall, context: ValidationContext) {
        // Validate arguments
        call.arguments.forEach { arg ->
            validateExpression(arg, context)
        }
        
        // Check if function exists (built-in or user-defined)
        if (!isBuiltInFunction(call.name) && !context.hasFunction(call.name)) {
            context.addError("Unknown function: ${call.name}")
        }
        
        // Validate argument count and types for known functions
        validateFunctionArguments(call, context)
    }
    
    /**
     * Validate if expression
     */
    private fun validateIfExpression(ifExpr: IfExpression, context: ValidationContext) {
        validateExpression(ifExpr.condition, context)
        
        val conditionType = inferExpressionType(ifExpr.condition, context)
        if (!isBooleanType(conditionType)) {
            context.addWarning(
                "If condition should be boolean, got ${conditionType::class.simpleName}"
            )
        }
        
        validateExpression(ifExpr.thenBranch, context)
        ifExpr.elseBranch?.let { validateExpression(it, context) }
    }
    
    /**
     * Validate match expression
     */
    private fun validateMatchExpression(match: MatchExpression, context: ValidationContext) {
        validateExpression(match.input, context)
        
        match.cases.forEach { case ->
            validateExpression(case.pattern, context)
            validateExpression(case.result, context)
        }
    }
    
    /**
     * Validate let expression
     */
    private fun validateLetExpression(let: LetExpression, context: ValidationContext) {
        val letContext = context.createChildContext()
        
        let.bindings.forEach { (name, value) ->
            validateExpression(value, letContext)
            val valueType = inferExpressionType(value, letContext)
            letContext.bindVariable(name, valueType)
        }
        
        validateExpression(let.body, letContext)
    }
    
    /**
     * Validate pipe expression
     */
    private fun validatePipeExpression(pipe: PipeExpression, context: ValidationContext) {
        validateExpression(pipe.input, context)
        
        var currentType = inferExpressionType(pipe.input, context)
        
        pipe.operations.forEach { operation ->
            validateExpression(operation, context)
            
            // Check operation is valid for current type
            if (operation is FunctionCall) {
                when (operation.name) {
                    "map", "filter", "reduce", "sortBy", "groupBy" -> {
                        if (currentType !is TypeDefinition.Array) {
                            context.addError(
                                "Operation '${operation.name}' requires array input, " +
                                "got ${currentType::class.simpleName}"
                            )
                        }
                    }
                }
                
                // Update current type based on operation
                currentType = inferPipeOperationType(currentType, operation, context)
            }
        }
    }
    
    /**
     * Validate variable reference
     */
    private fun validateVariableReference(ref: VariableReference, context: ValidationContext) {
        if (!context.hasVariable(ref.name)) {
            context.addError("Undefined variable: ${ref.name}")
        }
    }
    
    /**
     * Validate type compatibility
     */
    private fun validateTypeCompatibility(
        actualType: TypeDefinition,
        expectedType: TypeDefinition,
        path: String,
        context: ValidationContext
    ) {
        when {
            actualType::class == expectedType::class -> {
                // Same type class, check details
                when (actualType) {
                    is TypeDefinition.Scalar -> {
                        val expected = expectedType as TypeDefinition.Scalar
                        if (actualType.kind != expected.kind) {
                            context.addError(
                                "Type mismatch at '$path': expected ${expected.kind}, " +
                                "got ${actualType.kind}"
                            )
                        }
                    }
                    
                    is TypeDefinition.Array -> {
                        val expected = expectedType as TypeDefinition.Array
                        validateTypeCompatibility(
                            actualType.elementType,
                            expected.elementType,
                            "$path[]",
                            context
                        )
                    }
                    
                    is TypeDefinition.Object -> {
                        val expected = expectedType as TypeDefinition.Object
                        
                        // Check required fields are present
                        expected.required.forEach { requiredField ->
                            if (!actualType.properties.containsKey(requiredField)) {
                                context.addError(
                                    "Missing required field '$path.$requiredField'"
                                )
                            }
                        }
                        
                        // Check field types match
                        expected.properties.forEach { (fieldName, expectedProp) ->
                            val actualProp = actualType.properties[fieldName]
                            if (actualProp != null) {
                                validateTypeCompatibility(
                                    actualProp.type,
                                    expectedProp.type,
                                    "$path.$fieldName",
                                    context
                                )
                            }
                        }
                    }
                }
            }
            
            actualType is TypeDefinition.Any || expectedType is TypeDefinition.Any -> {
                // Any type matches anything
                context.addWarning(
                    "Cannot validate type compatibility at '$path' (Any type involved)"
                )
            }
            
            else -> {
                context.addError(
                    "Type mismatch at '$path': expected ${expectedType::class.simpleName}, " +
                    "got ${actualType::class.simpleName}"
                )
            }
        }
    }
    
    // Helper methods
    
    private fun inferExpressionType(expr: Expression, context: ValidationContext): TypeDefinition {
        // Simplified type inference - in practice, delegate to AdvancedTypeInference
        return TypeDefinition.Any
    }
    
    private fun inferPipeOperationType(
        inputType: TypeDefinition,
        operation: Expression,
        context: ValidationContext
    ): TypeDefinition {
        // Simplified - in practice, handle each operation properly
        return inputType
    }
    
    private fun isNumericType(type: TypeDefinition): Boolean {
        return type is TypeDefinition.Scalar && 
            (type.kind == ScalarKind.INTEGER || type.kind == ScalarKind.NUMBER)
    }
    
    private fun isComparableType(type: TypeDefinition): Boolean {
        return type is TypeDefinition.Scalar &&
            type.kind in setOf(ScalarKind.INTEGER, ScalarKind.NUMBER, ScalarKind.DATE, ScalarKind.DATETIME)
    }
    
    private fun isBooleanType(type: TypeDefinition): Boolean {
        return type is TypeDefinition.Scalar && type.kind == ScalarKind.BOOLEAN
    }
    
    private fun isBuiltInFunction(name: String): Boolean {
        return name in setOf(
            "map", "filter", "reduce", "sum", "avg", "min", "max", "count", "length",
            "upper", "lower", "trim", "concat", "split", "join",
            "now", "parseDate", "formatDate",
            "parseInt", "parseDecimal", "toString"
        )
    }
    
    private fun validateFunctionArguments(call: FunctionCall, context: ValidationContext) {
        val expectedArgs = when (call.name) {
            "map", "filter" -> 1 // Array + lambda
            "reduce" -> 2 // Array + lambda + initial
            "sum", "avg", "min", "max", "count", "length" -> 0 // Just the piped input
            "upper", "lower", "trim" -> 0 // String operations
            "concat", "split", "join" -> 1 // String + delimiter/other
            "parseDate" -> 2 // String + format
            "formatDate" -> 2 // Date + format
            else -> return // Unknown function, don't validate args
        }
        
        if (call.arguments.size != expectedArgs) {
            context.addError(
                "Function '${call.name}' expects $expectedArgs argument(s), " +
                "got ${call.arguments.size}"
            )
        }
    }
}

/**
 * Validation context for tracking state during validation
 */
class ValidationContext(
    val inputType: TypeDefinition,
    private val errors: MutableList<String>,
    private val warnings: MutableList<String>
) {
    private val variables = mutableMapOf<String, TypeDefinition>()
    private val functions = mutableSetOf<String>()
    
    fun addError(message: String) {
        errors.add(message)
    }
    
    fun addWarning(message: String) {
        warnings.add(message)
    }
    
    fun bindVariable(name: String, type: TypeDefinition) {
        variables[name] = type
    }
    
    fun hasVariable(name: String): Boolean = variables.containsKey(name)
    
    fun getVariableType(name: String): TypeDefinition? = variables[name]
    
    fun registerFunction(name: String) {
        functions.add(name)
    }
    
    fun hasFunction(name: String): Boolean = functions.contains(name)
    
    fun createChildContext(): ValidationContext {
        val child = ValidationContext(inputType, errors, warnings)
        child.variables.putAll(this.variables)
        child.functions.addAll(this.functions)
        return child
    }
}

/**
 * Result of transformation validation
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    fun hasErrors(): Boolean = errors.isNotEmpty()
    fun hasWarnings(): Boolean = warnings.isNotEmpty()
    
    override fun toString(): String = buildString {
        if (isValid) {
            appendLine("✓ Validation successful")
        } else {
            appendLine("✗ Validation failed with ${errors.size} error(s)")
        }
        
        if (errors.isNotEmpty()) {
            appendLine("\nErrors:")
            errors.forEach { appendLine("  ✗ $it") }
        }
        
        if (warnings.isNotEmpty()) {
            appendLine("\nWarnings:")
            warnings.forEach { appendLine("  ⚠ $it") }
        }
    }
}
