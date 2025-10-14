// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/types/AdvancedTypeInference.kt
package org.apache.utlx.analysis.types

import org.apache.utlx.core.ast.*
import org.apache.utlx.core.types.Type

/**
 * Advanced type inference engine for UTL-X transformations
 * 
 * Analyzes transformation AST and infers output types by:
 * - Tracking data flow through operations
 * - Handling conditionals and branching
 * - Processing map/filter/reduce operations
 * - Resolving function calls
 * - Managing variable scopes
 */
class AdvancedTypeInference(private val inputType: TypeDefinition) {
    
    private val typeContext = TypeContext()
    private val functionRegistry = FunctionRegistry()
    
    init {
        // Register built-in functions
        registerBuiltInFunctions()
    }
    
    /**
     * Infer output type from transformation program
     */
    fun inferOutputType(program: Program): TypeDefinition {
        // Process let bindings and function definitions first
        program.declarations.forEach { declaration ->
            when (declaration) {
                is LetDeclaration -> {
                    val type = inferExpression(declaration.value)
                    typeContext.bind(declaration.name, type)
                }
                is FunctionDeclaration -> {
                    functionRegistry.register(declaration)
                }
            }
        }
        
        // Infer type of main expression
        return inferExpression(program.mainExpression)
    }
    
    /**
     * Infer type of an expression
     */
    private fun inferExpression(expr: Expression): TypeDefinition {
        return when (expr) {
            // Literals
            is StringLiteral -> TypeDefinition.Scalar(ScalarKind.STRING)
            is NumberLiteral -> {
                if (expr.value % 1.0 == 0.0) {
                    TypeDefinition.Scalar(ScalarKind.INTEGER)
                } else {
                    TypeDefinition.Scalar(ScalarKind.NUMBER)
                }
            }
            is BooleanLiteral -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            is NullLiteral -> TypeDefinition.Scalar(ScalarKind.NULL)
            
            // Object construction
            is ObjectExpression -> inferObjectExpression(expr)
            
            // Array construction
            is ArrayExpression -> inferArrayExpression(expr)
            
            // Path expressions (input.Order.Customer.Name)
            is PathExpression -> inferPathExpression(expr)
            
            // Binary operations
            is BinaryOperation -> inferBinaryOperation(expr)
            
            // Unary operations
            is UnaryOperation -> inferUnaryOperation(expr)
            
            // Function calls
            is FunctionCall -> inferFunctionCall(expr)
            
            // Conditionals
            is IfExpression -> inferIfExpression(expr)
            
            // Match expressions
            is MatchExpression -> inferMatchExpression(expr)
            
            // Let expressions
            is LetExpression -> inferLetExpression(expr)
            
            // Variable references
            is VariableReference -> inferVariableReference(expr)
            
            // Pipe operations
            is PipeExpression -> inferPipeExpression(expr)
            
            // Lambda expressions
            is LambdaExpression -> inferLambdaExpression(expr)
            
            else -> TypeDefinition.Any
        }
    }
    
    /**
     * Infer type of object expression
     */
    private fun inferObjectExpression(obj: ObjectExpression): TypeDefinition {
        val properties = mutableMapOf<String, PropertyType>()
        val required = mutableSetOf<String>()
        
        obj.properties.forEach { (key, value) ->
            val propType = inferExpression(value)
            properties[key] = PropertyType(
                type = propType,
                nullable = false,
                description = extractDescription(value)
            )
            required.add(key)
        }
        
        return TypeDefinition.Object(
            properties = properties,
            required = required
        )
    }
    
    /**
     * Infer type of array expression
     */
    private fun inferArrayExpression(arr: ArrayExpression): TypeDefinition {
        return when {
            // Empty array
            arr.elements.isEmpty() -> TypeDefinition.Array(TypeDefinition.Any)
            
            // Array with explicit elements
            arr.elements.isNotEmpty() -> {
                val elementTypes = arr.elements.map { inferExpression(it) }
                val commonType = findCommonType(elementTypes)
                TypeDefinition.Array(commonType, minItems = arr.elements.size)
            }
            
            else -> TypeDefinition.Array(TypeDefinition.Any)
        }
    }
    
    /**
     * Infer type from path expression (e.g., input.Order.Customer.Name)
     */
    private fun inferPathExpression(path: PathExpression): TypeDefinition {
        var currentType = inputType
        
        path.segments.forEach { segment ->
            currentType = when (currentType) {
                is TypeDefinition.Object -> {
                    val property = currentType.properties[segment.name]
                    property?.type ?: TypeDefinition.Any
                }
                is TypeDefinition.Array -> {
                    // Array indexing or iteration
                    if (segment.isArrayAccess) {
                        currentType.elementType
                    } else {
                        // Implicit map operation
                        TypeDefinition.Array(
                            inferPathInType(currentType.elementType, segment.name)
                        )
                    }
                }
                else -> TypeDefinition.Any
            }
        }
        
        return currentType
    }
    
    /**
     * Helper to resolve path within a type
     */
    private fun inferPathInType(type: TypeDefinition, path: String): TypeDefinition {
        return when (type) {
            is TypeDefinition.Object -> {
                type.properties[path]?.type ?: TypeDefinition.Any
            }
            else -> TypeDefinition.Any
        }
    }
    
    /**
     * Infer type from binary operation
     */
    private fun inferBinaryOperation(op: BinaryOperation): TypeDefinition {
        val leftType = inferExpression(op.left)
        val rightType = inferExpression(op.right)
        
        return when (op.operator) {
            // Arithmetic operations
            "+", "-", "*", "/" -> {
                when {
                    leftType is TypeDefinition.Scalar && leftType.kind == ScalarKind.INTEGER &&
                    rightType is TypeDefinition.Scalar && rightType.kind == ScalarKind.INTEGER ->
                        TypeDefinition.Scalar(ScalarKind.INTEGER)
                    else -> TypeDefinition.Scalar(ScalarKind.NUMBER)
                }
            }
            
            // Comparison operations
            "==", "!=", "<", ">", "<=", ">=" -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            
            // Logical operations
            "&&", "||" -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            
            // String concatenation
            "+" when leftType is TypeDefinition.Scalar && leftType.kind == ScalarKind.STRING ->
                TypeDefinition.Scalar(ScalarKind.STRING)
            
            else -> TypeDefinition.Any
        }
    }
    
    /**
     * Infer type from unary operation
     */
    private fun inferUnaryOperation(op: UnaryOperation): TypeDefinition {
        val operandType = inferExpression(op.operand)
        
        return when (op.operator) {
            "!" -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            "-" -> operandType
            "+" -> operandType
            else -> TypeDefinition.Any
        }
    }
    
    /**
     * Infer type from function call
     */
    private fun inferFunctionCall(call: FunctionCall): TypeDefinition {
        // Check built-in functions
        val builtInType = functionRegistry.getReturnType(call.name, call.arguments.map { inferExpression(it) })
        if (builtInType != null) {
            return builtInType
        }
        
        // Check user-defined functions
        val funcDecl = functionRegistry.getFunction(call.name)
        if (funcDecl != null) {
            // Create new context with parameter bindings
            val savedContext = typeContext.snapshot()
            funcDecl.parameters.zip(call.arguments).forEach { (param, arg) ->
                typeContext.bind(param.name, inferExpression(arg))
            }
            
            val returnType = inferExpression(funcDecl.body)
            typeContext.restore(savedContext)
            return returnType
        }
        
        return TypeDefinition.Any
    }
    
    /**
     * Infer type from if expression
     */
    private fun inferIfExpression(ifExpr: IfExpression): TypeDefinition {
        val thenType = inferExpression(ifExpr.thenBranch)
        val elseType = if (ifExpr.elseBranch != null) {
            inferExpression(ifExpr.elseBranch)
        } else {
            TypeDefinition.Scalar(ScalarKind.NULL)
        }
        
        return findCommonType(listOf(thenType, elseType))
    }
    
    /**
     * Infer type from match expression
     */
    private fun inferMatchExpression(match: MatchExpression): TypeDefinition {
        val branchTypes = match.cases.map { case ->
            inferExpression(case.result)
        }
        
        return findCommonType(branchTypes)
    }
    
    /**
     * Infer type from let expression
     */
    private fun inferLetExpression(let: LetExpression): TypeDefinition {
        val savedContext = typeContext.snapshot()
        
        let.bindings.forEach { (name, value) ->
            typeContext.bind(name, inferExpression(value))
        }
        
        val bodyType = inferExpression(let.body)
        
        typeContext.restore(savedContext)
        return bodyType
    }
    
    /**
     * Infer type from variable reference
     */
    private fun inferVariableReference(ref: VariableReference): TypeDefinition {
        return typeContext.lookup(ref.name) ?: TypeDefinition.Any
    }
    
    /**
     * Infer type from pipe expression
     */
    private fun inferPipeExpression(pipe: PipeExpression): TypeDefinition {
        var currentType = inferExpression(pipe.input)
        
        pipe.operations.forEach { operation ->
            currentType = inferPipeOperation(currentType, operation)
        }
        
        return currentType
    }
    
    /**
     * Infer type from pipe operation
     */
    private fun inferPipeOperation(inputType: TypeDefinition, operation: Expression): TypeDefinition {
        return when (operation) {
            is FunctionCall -> {
                when (operation.name) {
                    "map" -> {
                        if (inputType is TypeDefinition.Array) {
                            val lambda = operation.arguments.first() as? LambdaExpression
                            if (lambda != null) {
                                val savedContext = typeContext.snapshot()
                                typeContext.bind(lambda.parameter, inputType.elementType)
                                val mappedType = inferExpression(lambda.body)
                                typeContext.restore(savedContext)
                                TypeDefinition.Array(mappedType)
                            } else {
                                inputType
                            }
                        } else {
                            inputType
                        }
                    }
                    
                    "filter" -> inputType  // Filter preserves type
                    
                    "reduce" -> {
                        val lambda = operation.arguments.first() as? LambdaExpression
                        val initialValue = operation.arguments.getOrNull(1)
                        
                        if (lambda != null && initialValue != null) {
                            inferExpression(initialValue)
                        } else {
                            TypeDefinition.Any
                        }
                    }
                    
                    "sum", "avg", "min", "max" -> TypeDefinition.Scalar(ScalarKind.NUMBER)
                    
                    "count", "length" -> TypeDefinition.Scalar(ScalarKind.INTEGER)
                    
                    "first", "last" -> {
                        if (inputType is TypeDefinition.Array) {
                            inputType.elementType
                        } else {
                            TypeDefinition.Any
                        }
                    }
                    
                    "sortBy" -> inputType  // Sort preserves type
                    
                    "groupBy" -> {
                        if (inputType is TypeDefinition.Array) {
                            TypeDefinition.Object(
                                properties = mapOf(
                                    "key" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                                    "items" to PropertyType(inputType)
                                ),
                                required = setOf("key", "items")
                            )
                        } else {
                            inputType
                        }
                    }
                    
                    else -> inferFunctionCall(operation)
                }
            }
            else -> inferExpression(operation)
        }
    }
    
    /**
     * Infer type from lambda expression
     */
    private fun inferLambdaExpression(lambda: LambdaExpression): TypeDefinition {
        // Lambda type is a function type (not directly representable in current type system)
        // For now, return Any
        return TypeDefinition.Any
    }
    
    /**
     * Find common type among multiple types (for union types)
     */
    private fun findCommonType(types: List<TypeDefinition>): TypeDefinition {
        if (types.isEmpty()) return TypeDefinition.Any
        if (types.size == 1) return types.first()
        
        // Check if all types are identical
        if (types.all { it == types.first() }) {
            return types.first()
        }
        
        // Check if all are scalars of same kind
        if (types.all { it is TypeDefinition.Scalar }) {
            val scalars = types.map { it as TypeDefinition.Scalar }
            if (scalars.all { it.kind == scalars.first().kind }) {
                return scalars.first()
            }
        }
        
        // Check if can coerce numbers
        if (types.all { it is TypeDefinition.Scalar && 
            (it.kind == ScalarKind.INTEGER || it.kind == ScalarKind.NUMBER) }) {
            return TypeDefinition.Scalar(ScalarKind.NUMBER)
        }
        
        // Otherwise, create union type
        return TypeDefinition.Union(types.distinct())
    }
    
    /**
     * Extract description from expression (for documentation)
     */
    private fun extractDescription(expr: Expression): String? {
        // Could extract from comments in the AST
        return null
    }
    
    /**
     * Register built-in functions and their return types
     */
    private fun registerBuiltInFunctions() {
        // String functions
        functionRegistry.registerBuiltIn("upper") { args ->
            TypeDefinition.Scalar(ScalarKind.STRING)
        }
        functionRegistry.registerBuiltIn("lower") { args ->
            TypeDefinition.Scalar(ScalarKind.STRING)
        }
        functionRegistry.registerBuiltIn("trim") { args ->
            TypeDefinition.Scalar(ScalarKind.STRING)
        }
        functionRegistry.registerBuiltIn("substring") { args ->
            TypeDefinition.Scalar(ScalarKind.STRING)
        }
        functionRegistry.registerBuiltIn("concat") { args ->
            TypeDefinition.Scalar(ScalarKind.STRING)
        }
        
        // Array functions
        functionRegistry.registerBuiltIn("sum") { args ->
            TypeDefinition.Scalar(ScalarKind.NUMBER)
        }
        functionRegistry.registerBuiltIn("avg") { args ->
            TypeDefinition.Scalar(ScalarKind.NUMBER)
        }
        functionRegistry.registerBuiltIn("min") { args ->
            TypeDefinition.Scalar(ScalarKind.NUMBER)
        }
        functionRegistry.registerBuiltIn("max") { args ->
            TypeDefinition.Scalar(ScalarKind.NUMBER)
        }
        functionRegistry.registerBuiltIn("count") { args ->
            TypeDefinition.Scalar(ScalarKind.INTEGER)
        }
        functionRegistry.registerBuiltIn("length") { args ->
            TypeDefinition.Scalar(ScalarKind.INTEGER)
        }
        
        // Date functions
        functionRegistry.registerBuiltIn("now") { args ->
            TypeDefinition.Scalar(ScalarKind.DATETIME)
        }
        functionRegistry.registerBuiltIn("parseDate") { args ->
            TypeDefinition.Scalar(ScalarKind.DATE)
        }
        functionRegistry.registerBuiltIn("formatDate") { args ->
            TypeDefinition.Scalar(ScalarKind.STRING)
        }
        
        // Type functions
        functionRegistry.registerBuiltIn("parseInt") { args ->
            TypeDefinition.Scalar(ScalarKind.INTEGER)
        }
        functionRegistry.registerBuiltIn("parseDecimal") { args ->
            TypeDefinition.Scalar(ScalarKind.NUMBER)
        }
        functionRegistry.registerBuiltIn("parseBoolean") { args ->
            TypeDefinition.Scalar(ScalarKind.BOOLEAN)
        }
        functionRegistry.registerBuiltIn("toString") { args ->
            TypeDefinition.Scalar(ScalarKind.STRING)
        }
    }
}

/**
 * Type context for tracking variable bindings
 */
class TypeContext {
    private val scopes = mutableListOf(mutableMapOf<String, TypeDefinition>())
    
    fun bind(name: String, type: TypeDefinition) {
        scopes.last()[name] = type
    }
    
    fun lookup(name: String): TypeDefinition? {
        for (scope in scopes.asReversed()) {
            scope[name]?.let { return it }
        }
        return null
    }
    
    fun pushScope() {
        scopes.add(mutableMapOf())
    }
    
    fun popScope() {
        if (scopes.size > 1) {
            scopes.removeLast()
        }
    }
    
    fun snapshot(): List<Map<String, TypeDefinition>> {
        return scopes.map { it.toMap() }
    }
    
    fun restore(snapshot: List<Map<String, TypeDefinition>>) {
        scopes.clear()
        scopes.addAll(snapshot.map { it.toMutableMap() })
    }
}

/**
 * Registry for function signatures
 */
class FunctionRegistry {
    private val builtInFunctions = mutableMapOf<String, (List<TypeDefinition>) -> TypeDefinition>()
    private val userFunctions = mutableMapOf<String, FunctionDeclaration>()
    
    fun registerBuiltIn(name: String, typeInference: (List<TypeDefinition>) -> TypeDefinition) {
        builtInFunctions[name] = typeInference
    }
    
    fun register(function: FunctionDeclaration) {
        userFunctions[function.name] = function
    }
    
    fun getReturnType(name: String, argTypes: List<TypeDefinition>): TypeDefinition? {
        return builtInFunctions[name]?.invoke(argTypes)
    }
    
    fun getFunction(name: String): FunctionDeclaration? {
        return userFunctions[name]
    }
}
