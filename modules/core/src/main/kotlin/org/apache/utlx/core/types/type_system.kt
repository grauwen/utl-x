package org.apache.utlx.core.types

import org.apache.utlx.core.ast.*

/**
 * Type system for UTL-X
 * 
 * Provides static type checking and type inference for UTL-X programs.
 */
sealed class UTLXType {
    /**
     * Primitive types
     */
    object String : UTLXType() {
        override fun toString() = "String"
    }
    
    object Number : UTLXType() {
        override fun toString() = "Number"
    }
    
    object Boolean : UTLXType() {
        override fun toString() = "Boolean"
    }
    
    object Null : UTLXType() {
        override fun toString() = "Null"
    }

    object Date : UTLXType() {
        override fun toString() = "Date"
    }

    object Any : UTLXType() {
        override fun toString() = "Any"
    }

    object Unknown : UTLXType() {
        override fun toString() = "Unknown"
    }

    /**
     * Nullable type: T?
     */
    data class Nullable(val innerType: UTLXType) : UTLXType() {
        override fun toString() = "$innerType?"
    }
    
    /**
     * Array type: Array<T>
     */
    data class Array(val elementType: UTLXType) : UTLXType() {
        override fun toString() = "Array<$elementType>"
    }
    
    /**
     * Object type with known properties
     */
    data class Object(val properties: Map<kotlin.String, UTLXType>) : UTLXType() {
        override fun toString(): kotlin.String {
            val props = properties.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            return "{ $props }"
        }
    }
    
    /**
     * Function type: (T1, T2, ...) => R
     */
    data class Function(
        val parameterTypes: List<UTLXType>,
        val returnType: UTLXType
    ) : UTLXType() {
        override fun toString(): kotlin.String {
            val params = parameterTypes.joinToString(", ")
            return "($params) => $returnType"
        }
    }
    
    /**
     * Union type: T1 | T2 | ...
     */
    data class Union(val types: Set<UTLXType>) : UTLXType() {
        override fun toString() = types.joinToString(" | ")
        
        companion object {
            fun of(vararg types: UTLXType) = Union(types.toSet())
        }
    }
    
    /**
     * Check if this type is compatible with (can be assigned to) another type
     */
    fun isAssignableTo(other: UTLXType): kotlin.Boolean {
        return when {
            this == other -> true
            other is Any -> true
            this is Unknown || other is Unknown -> true
            this is Null && other !is Null -> false  // Null only to nullable types
            this is Union -> types.all { it.isAssignableTo(other) }
            other is Union -> other.types.any { this.isAssignableTo(it) }
            this is Array && other is Array -> elementType.isAssignableTo(other.elementType)
            this is Object && other is Object -> {
                // Check structural compatibility
                other.properties.all { (key, type) ->
                    properties[key]?.isAssignableTo(type) == true
                }
            }
            this is Function && other is Function -> {
                // Contravariant in parameters, covariant in return type
                parameterTypes.size == other.parameterTypes.size &&
                other.parameterTypes.zip(parameterTypes).all { (p1, p2) -> 
                    p1.isAssignableTo(p2) 
                } &&
                returnType.isAssignableTo(other.returnType)
            }
            else -> false
        }
    }
    
    /**
     * Get the common type between two types (used for type inference)
     */
    fun commonType(other: UTLXType): UTLXType {
        return when {
            this == other -> this
            this is Any || other is Any -> Any
            this is Unknown -> other
            other is Unknown -> this
            this is Null -> other
            other is Null -> this
            this is Union || other is Union -> {
                val allTypes = mutableSetOf<UTLXType>()
                if (this is Union) allTypes.addAll(types) else allTypes.add(this)
                if (other is Union) allTypes.addAll(other.types) else allTypes.add(other)
                Union(allTypes)
            }
            this is Array && other is Array -> {
                Array(elementType.commonType(other.elementType))
            }
            else -> Union.of(this, other)
        }
    }
}

/**
 * Type environment for tracking variable types
 */
class TypeEnvironment(private val parent: TypeEnvironment? = null) {
    private val bindings = mutableMapOf<kotlin.String, UTLXType>()
    
    fun define(name: kotlin.String, type: UTLXType) {
        bindings[name] = type
    }
    
    fun lookup(name: kotlin.String): UTLXType? {
        return bindings[name] ?: parent?.lookup(name)
    }
    
    fun createChild(): TypeEnvironment = TypeEnvironment(this)
    
    fun has(name: kotlin.String): kotlin.Boolean {
        return bindings.containsKey(name) || (parent?.has(name) == true)
    }
}

/**
 * Type error with location information
 */
data class TypeError(
    val message: kotlin.String,
    val location: Location,
    val expected: UTLXType? = null,
    val actual: UTLXType? = null
) {
    override fun toString(): kotlin.String {
        val parts = mutableListOf("Type error at ${location.line}:${location.column}: $message")
        if (expected != null && actual != null) {
            parts.add("  Expected: $expected")
            parts.add("  Actual: $actual")
        }
        return parts.joinToString("\n")
    }
}

/**
 * Result of type checking
 */
sealed class TypeCheckResult {
    data class Success(val type: UTLXType) : TypeCheckResult()
    data class Failure(val errors: List<TypeError>) : TypeCheckResult()
}

/**
 * Type inference and checking for expressions
 */
class TypeChecker(private val stdlib: StandardLibrary) {
    private val errors = mutableListOf<TypeError>()
    
    /**
     * Type check a complete program
     */
    fun check(program: Program): TypeCheckResult {
        errors.clear()
        val env = TypeEnvironment()
        
        // Add built-in 'input' identifier
        env.define("input", UTLXType.Any) // Will be refined by format parser
        
        // Check the body expression
        val bodyType = inferType(program.body, env)
        
        return if (errors.isEmpty()) {
            TypeCheckResult.Success(bodyType)
        } else {
            TypeCheckResult.Failure(errors)
        }
    }
    
    /**
     * Infer the type of an expression
     */
    fun inferType(expr: Expression, env: TypeEnvironment): UTLXType {
        return when (expr) {
            is Expression.StringLiteral -> UTLXType.String
            is Expression.NumberLiteral -> UTLXType.Number
            is Expression.BooleanLiteral -> UTLXType.Boolean
            is Expression.NullLiteral -> UTLXType.Null
            
            is Expression.Identifier -> {
                env.lookup(expr.name) ?: run {
                    errors.add(TypeError(
                        "Undefined variable: ${expr.name}",
                        expr.location
                    ))
                    UTLXType.Unknown
                }
            }
            
            is Expression.ObjectLiteral -> {
                val propTypes = mutableMapOf<String, UTLXType>()

                for (prop in expr.properties) {
                    if (prop.isSpread) {
                        // Handle spread property
                        val spreadType = inferType(prop.value, env)
                        when (spreadType) {
                            is UTLXType.Object -> {
                                // Merge properties from spread object
                                propTypes.putAll(spreadType.properties)
                            }
                            else -> {
                                // Can't spread non-object - ignore for type inference
                            }
                        }
                    } else {
                        val key = prop.key ?: error("Non-spread property must have a key")
                        propTypes[key] = inferType(prop.value, env)
                    }
                }

                UTLXType.Object(propTypes)
            }

            is Expression.SpreadElement -> {
                // SpreadElement should only be used within array/object literals
                // If type-checked directly, infer the type of the inner expression
                inferType(expr.expression, env)
            }
            
            is Expression.ArrayLiteral -> {
                if (expr.elements.isEmpty()) {
                    UTLXType.Array(UTLXType.Any)
                } else {
                    val elementTypes = expr.elements.map { element ->
                        when (element) {
                            is Expression.SpreadElement -> {
                                // For spread, get the element type of the array being spread
                                val spreadType = inferType(element.expression, env)
                                when (spreadType) {
                                    is UTLXType.Array -> spreadType.elementType
                                    else -> UTLXType.Any // Can't spread non-array
                                }
                            }
                            else -> inferType(element, env)
                        }
                    }
                    val commonType = elementTypes.reduce { acc, type ->
                        acc.commonType(type)
                    }
                    UTLXType.Array(commonType)
                }
            }
            
            is Expression.MemberAccess -> {
                val targetType = inferType(expr.target, env)
                when (targetType) {
                    is UTLXType.Object -> {
                        targetType.properties[expr.property] ?: run {
                            errors.add(TypeError(
                                "Property '${expr.property}' does not exist on object",
                                expr.location,
                                actual = targetType
                            ))
                            UTLXType.Unknown
                        }
                    }
                    is UTLXType.Any, is UTLXType.Unknown -> UTLXType.Any
                    else -> {
                        errors.add(TypeError(
                            "Cannot access property on non-object type",
                            expr.location,
                            actual = targetType
                        ))
                        UTLXType.Unknown
                    }
                }
            }

            is Expression.SafeNavigation -> {
                // Safe navigation always returns nullable type
                val targetType = inferType(expr.target, env)

                // Get the property type (same logic as MemberAccess)
                val propertyType = when (targetType) {
                    is UTLXType.Nullable -> {
                        // If target is already nullable, unwrap it first
                        when (val innerType = targetType.innerType) {
                            is UTLXType.Object -> {
                                innerType.properties[expr.property] ?: UTLXType.Unknown
                            }
                            is UTLXType.Any, is UTLXType.Unknown -> UTLXType.Any
                            else -> UTLXType.Unknown
                        }
                    }
                    is UTLXType.Object -> {
                        targetType.properties[expr.property] ?: UTLXType.Unknown
                    }
                    is UTLXType.Any, is UTLXType.Unknown -> UTLXType.Any
                    else -> UTLXType.Unknown
                }

                // Always wrap result in Nullable (safe navigation returns null if target is null or property doesn't exist)
                when (propertyType) {
                    is UTLXType.Nullable -> propertyType  // Already nullable
                    else -> UTLXType.Nullable(propertyType)
                }
            }

            is Expression.IndexAccess -> {
                val targetType = inferType(expr.target, env)
                val indexType = inferType(expr.index, env)
                
                if (!indexType.isAssignableTo(UTLXType.Number)) {
                    errors.add(TypeError(
                        "Array index must be a number",
                        expr.location,
                        expected = UTLXType.Number,
                        actual = indexType
                    ))
                }
                
                when (targetType) {
                    is UTLXType.Array -> targetType.elementType
                    is UTLXType.Any, is UTLXType.Unknown -> UTLXType.Any
                    else -> {
                        errors.add(TypeError(
                            "Cannot index non-array type",
                            expr.location,
                            actual = targetType
                        ))
                        UTLXType.Unknown
                    }
                }
            }
            
            is Expression.BinaryOp -> inferBinaryOpType(expr, env)

            is Expression.UnaryOp -> inferUnaryOpType(expr, env)

            is Expression.Ternary -> {
                val condType = inferType(expr.condition, env)
                if (!condType.isAssignableTo(UTLXType.Boolean) &&
                    condType !is UTLXType.Any && condType !is UTLXType.Unknown) {
                    errors.add(TypeError(
                        "Ternary condition must be boolean",
                        expr.location,
                        expected = UTLXType.Boolean,
                        actual = condType
                    ))
                }

                val thenType = inferType(expr.thenExpr, env)
                val elseType = inferType(expr.elseExpr, env)

                thenType.commonType(elseType)
            }

            is Expression.Conditional -> {
                val condType = inferType(expr.condition, env)
                if (!condType.isAssignableTo(UTLXType.Boolean) && 
                    condType !is UTLXType.Any && condType !is UTLXType.Unknown) {
                    errors.add(TypeError(
                        "Condition must be boolean",
                        expr.location,
                        expected = UTLXType.Boolean,
                        actual = condType
                    ))
                }
                
                val thenType = inferType(expr.thenBranch, env)
                val elseType = expr.elseBranch?.let { inferType(it, env) } ?: UTLXType.Null
                
                thenType.commonType(elseType)
            }
            
            is Expression.LetBinding -> {
                val valueType = inferType(expr.value, env)

                // Check type annotation if present
                if (expr.typeAnnotation != null) {
                    val annotatedType = expr.typeAnnotation.toUTLXType()
                    if (!valueType.isAssignableTo(annotatedType)) {
                        errors.add(TypeError(
                            "Type mismatch in let binding '${expr.name}': " +
                            "expected ${annotatedType}, got ${valueType}",
                            expr.location
                        ))
                    }
                    // Use the annotated type (more specific than inferred)
                    env.define(expr.name, annotatedType)
                    annotatedType
                } else {
                    env.define(expr.name, valueType)
                    valueType
                }
            }
            
            is Expression.FunctionCall -> {
                val funcType = inferType(expr.function, env)
                
                // Check if it's a known stdlib function
                if (expr.function is Expression.Identifier) {
                    val funcName = expr.function.name
                    stdlib.getFunction(funcName)?.let { stdFunc ->
                        // Validate argument count
                        if (expr.arguments.size != stdFunc.parameterTypes.size) {
                            errors.add(TypeError(
                                "Function '$funcName' expects ${stdFunc.parameterTypes.size} arguments, got ${expr.arguments.size}",
                                expr.location
                            ))
                        }
                        
                        // Check argument types
                        expr.arguments.zip(stdFunc.parameterTypes).forEach { (arg, expectedType) ->
                            val argType = inferType(arg, env)
                            if (!argType.isAssignableTo(expectedType)) {
                                errors.add(TypeError(
                                    "Argument type mismatch",
                                    arg.location,
                                    expected = expectedType,
                                    actual = argType
                                ))
                            }
                        }
                        
                        return stdFunc.returnType
                    }
                }
                
                // Generic function call
                when (funcType) {
                    is UTLXType.Function -> {
                        expr.arguments.zip(funcType.parameterTypes).forEach { (arg, expectedType) ->
                            val argType = inferType(arg, env)
                            if (!argType.isAssignableTo(expectedType)) {
                                errors.add(TypeError(
                                    "Argument type mismatch",
                                    arg.location,
                                    expected = expectedType,
                                    actual = argType
                                ))
                            }
                        }
                        funcType.returnType
                    }
                    is UTLXType.Any, is UTLXType.Unknown -> UTLXType.Any
                    else -> {
                        errors.add(TypeError(
                            "Cannot call non-function type",
                            expr.location,
                            actual = funcType
                        ))
                        UTLXType.Unknown
                    }
                }
            }
            
            is Expression.Lambda -> {
                val childEnv = env.createChild()
                val paramTypes = expr.parameters.map { param ->
                    val type = param.type?.toUTLXType() ?: UTLXType.Any
                    childEnv.define(param.name, type)
                    type
                }

                val inferredReturnType = inferType(expr.body, childEnv)

                // Check return type annotation if present
                val actualReturnType = if (expr.returnType != null) {
                    val annotatedReturnType = expr.returnType.toUTLXType()
                    if (!inferredReturnType.isAssignableTo(annotatedReturnType)) {
                        errors.add(TypeError(
                            "Type mismatch in lambda return type: " +
                            "expected ${annotatedReturnType}, got ${inferredReturnType}",
                            expr.location
                        ))
                    }
                    annotatedReturnType
                } else {
                    inferredReturnType
                }

                UTLXType.Function(paramTypes, actualReturnType)
            }
            
            is Expression.Pipe -> {
                val sourceType = inferType(expr.source, env)
                // In a pipe, the source becomes the first argument to the target
                inferType(expr.target, env)
            }
            
            is Expression.Block -> {
                if (expr.expressions.isEmpty()) {
                    UTLXType.Null
                } else {
                    expr.expressions.map { inferType(it, env) }.last()
                }
            }
            
            is Expression.Match -> {
                val valueType = inferType(expr.value, env)
                val caseTypes = expr.cases.map { case ->
                    inferType(case.expression, env)
                }

                if (caseTypes.isEmpty()) {
                    UTLXType.Null
                } else {
                    caseTypes.reduce { acc, type -> acc.commonType(type) }
                }
            }

            is Expression.TryCatch -> {
                // Try-catch returns the common type of both try and catch blocks
                val tryType = inferType(expr.tryBlock, env)
                val catchType = inferType(expr.catchBlock, env)
                tryType.commonType(catchType)
            }

            is Expression.TemplateApplication -> {
                // Templates produce UDM structures, typically objects
                UTLXType.Any
            }
        }
    }
    
    private fun inferBinaryOpType(expr: Expression.BinaryOp, env: TypeEnvironment): UTLXType {
        val leftType = inferType(expr.left, env)
        val rightType = inferType(expr.right, env)
        
        return when (expr.operator) {
            BinaryOperator.PLUS -> {
                when {
                    leftType.isAssignableTo(UTLXType.String) || rightType.isAssignableTo(UTLXType.String) -> {
                        UTLXType.String  // String concatenation
                    }
                    leftType.isAssignableTo(UTLXType.Number) && rightType.isAssignableTo(UTLXType.Number) -> {
                        UTLXType.Number
                    }
                    leftType is UTLXType.Any || rightType is UTLXType.Any -> UTLXType.Any
                    else -> {
                        errors.add(TypeError(
                            "Invalid operands for + operator",
                            expr.location,
                            actual = UTLXType.Union.of(leftType, rightType)
                        ))
                        UTLXType.Unknown
                    }
                }
            }
            
            BinaryOperator.MINUS, BinaryOperator.MULTIPLY,
            BinaryOperator.DIVIDE, BinaryOperator.MODULO, BinaryOperator.EXPONENT -> {
                if (!leftType.isAssignableTo(UTLXType.Number) || !rightType.isAssignableTo(UTLXType.Number)) {
                    if (leftType !is UTLXType.Any && rightType !is UTLXType.Any) {
                        errors.add(TypeError(
                            "Arithmetic operators require numeric operands",
                            expr.location,
                            expected = UTLXType.Number,
                            actual = UTLXType.Union.of(leftType, rightType)
                        ))
                    }
                }
                UTLXType.Number
            }
            
            BinaryOperator.EQUAL, BinaryOperator.NOT_EQUAL,
            BinaryOperator.LESS_THAN, BinaryOperator.LESS_EQUAL,
            BinaryOperator.GREATER_THAN, BinaryOperator.GREATER_EQUAL -> {
                UTLXType.Boolean
            }
            
            BinaryOperator.AND, BinaryOperator.OR -> {
                if (!leftType.isAssignableTo(UTLXType.Boolean) || !rightType.isAssignableTo(UTLXType.Boolean)) {
                    if (leftType !is UTLXType.Any && rightType !is UTLXType.Any) {
                        errors.add(TypeError(
                            "Logical operators require boolean operands",
                            expr.location,
                            expected = UTLXType.Boolean,
                            actual = UTLXType.Union.of(leftType, rightType)
                        ))
                    }
                }
                UTLXType.Boolean
            }

            BinaryOperator.NULLISH_COALESCE -> {
                // Nullish coalescing: left ?? right
                // If left is null, returns right; otherwise returns left
                // Result type is the non-nullable version of left unified with right
                val unwrappedLeft = when (leftType) {
                    is UTLXType.Nullable -> leftType.innerType
                    else -> leftType
                }
                // Return the common type of unwrapped left and right
                unwrappedLeft.commonType(rightType)
            }
        }
    }
    
    private fun inferUnaryOpType(expr: Expression.UnaryOp, env: TypeEnvironment): UTLXType {
        val operandType = inferType(expr.operand, env)
        
        return when (expr.operator) {
            UnaryOperator.MINUS -> {
                if (!operandType.isAssignableTo(UTLXType.Number) && operandType !is UTLXType.Any) {
                    errors.add(TypeError(
                        "Unary minus requires numeric operand",
                        expr.location,
                        expected = UTLXType.Number,
                        actual = operandType
                    ))
                }
                UTLXType.Number
            }
            
            UnaryOperator.NOT -> {
                if (!operandType.isAssignableTo(UTLXType.Boolean) && operandType !is UTLXType.Any) {
                    errors.add(TypeError(
                        "Logical NOT requires boolean operand",
                        expr.location,
                        expected = UTLXType.Boolean,
                        actual = operandType
                    ))
                }
                UTLXType.Boolean
            }
        }
    }
}

/**
 * Convert AST type to UTLXType
 */
fun Type.toUTLXType(): UTLXType {
    return when (this) {
        is Type.String -> UTLXType.String
        is Type.Number -> UTLXType.Number
        is Type.Boolean -> UTLXType.Boolean
        is Type.Null -> UTLXType.Null
        is Type.Date -> UTLXType.Date
        is Type.Any -> UTLXType.Any
        is Type.Array -> UTLXType.Array(this.elementType.toUTLXType())
        is Type.Object -> UTLXType.Object(this.properties.mapValues { it.value.toUTLXType() })
        is Type.Function -> UTLXType.Function(
            this.parameters.map { it.toUTLXType() },
            this.returnType.toUTLXType()
        )
        is Type.Union -> UTLXType.Union(this.types.map { it.toUTLXType() }.toSet())
        is Type.Nullable -> UTLXType.Nullable(this.innerType.toUTLXType())
    }
}

/**
 * Standard library function signature
 */
data class StdLibFunction(
    val name: kotlin.String,
    val parameterTypes: List<UTLXType>,
    val returnType: UTLXType,
    val description: kotlin.String
)

/**
 * Standard library type information
 */
class StandardLibrary {
    private val functions = mutableMapOf<kotlin.String, StdLibFunction>()
    
    init {
        // String functions
        register("upper", listOf(UTLXType.String), UTLXType.String, "Convert to uppercase")
        register("lower", listOf(UTLXType.String), UTLXType.String, "Convert to lowercase")
        register("trim", listOf(UTLXType.String), UTLXType.String, "Remove whitespace")
        register("length", listOf(UTLXType.String), UTLXType.Number, "Get string length")
        register("substring", listOf(UTLXType.String, UTLXType.Number, UTLXType.Number), 
                 UTLXType.String, "Get substring")
        
        // Array functions
        register("map", listOf(UTLXType.Array(UTLXType.Any), 
                               UTLXType.Function(listOf(UTLXType.Any), UTLXType.Any)),
                 UTLXType.Array(UTLXType.Any), "Map function over array")
        register("filter", listOf(UTLXType.Array(UTLXType.Any),
                                  UTLXType.Function(listOf(UTLXType.Any), UTLXType.Boolean)),
                 UTLXType.Array(UTLXType.Any), "Filter array")
        register("sum", listOf(UTLXType.Array(UTLXType.Number)), UTLXType.Number, "Sum numbers")
        register("count", listOf(UTLXType.Array(UTLXType.Any)), UTLXType.Number, "Count elements")
        
        // Math functions
        register("abs", listOf(UTLXType.Number), UTLXType.Number, "Absolute value")
        register("round", listOf(UTLXType.Number), UTLXType.Number, "Round to nearest integer")
        register("ceil", listOf(UTLXType.Number), UTLXType.Number, "Round up")
        register("floor", listOf(UTLXType.Number), UTLXType.Number, "Round down")
        register("min", listOf(UTLXType.Number, UTLXType.Number), UTLXType.Number, "Minimum")
        register("max", listOf(UTLXType.Number, UTLXType.Number), UTLXType.Number, "Maximum")
    }
    
    private fun register(
        name: kotlin.String,
        paramTypes: List<UTLXType>,
        returnType: UTLXType,
        description: kotlin.String
    ) {
        functions[name] = StdLibFunction(name, paramTypes, returnType, description)
    }
    
    fun getFunction(name: kotlin.String): StdLibFunction? = functions[name]
    
    fun getAllFunctions(): List<StdLibFunction> = functions.values.toList()
}
