// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/types/TypeContext.kt
package org.apache.utlx.analysis.types

/**
 * Type context for tracking variable bindings and scopes
 *
 * Manages:
 * - Variable type bindings
 * - Nested scopes (for let expressions, functions, etc.)
 * - Scope snapshots and restoration
 * - Input type for path resolution
 * - Function registry for type checking
 */
class TypeContext(
    /**
     * The input type for this transformation context
     */
    val inputType: TypeDefinition? = null
) {

    /**
     * Stack of scopes, with the current scope at the end
     */
    private val scopes = mutableListOf<Scope>()

    init {
        // Initialize with global scope
        scopes.add(Scope("global"))
    }
    
    /**
     * Bind a variable to a type in the current scope
     */
    fun bind(name: String, type: TypeDefinition) {
        currentScope().bindings[name] = type
    }
    
    /**
     * Look up a variable's type
     * Searches from current scope back to global scope
     */
    fun lookup(name: String): TypeDefinition? {
        // Search from innermost to outermost scope
        for (scope in scopes.asReversed()) {
            scope.bindings[name]?.let { return it }
        }
        return null
    }
    
    /**
     * Check if a variable exists in any scope
     */
    fun contains(name: String): Boolean {
        return lookup(name) != null
    }
    
    /**
     * Push a new scope onto the stack
     */
    fun pushScope(name: String = "anonymous") {
        scopes.add(Scope(name))
    }
    
    /**
     * Pop the current scope from the stack
     * Cannot pop the global scope
     */
    fun popScope(): Scope? {
        if (scopes.size <= 1) {
            return null // Cannot pop global scope
        }
        return scopes.removeLast()
    }
    
    /**
     * Get the current scope
     */
    fun currentScope(): Scope {
        return scopes.last()
    }
    
    /**
     * Get the number of active scopes
     */
    fun scopeDepth(): Int {
        return scopes.size
    }
    
    /**
     * Execute a block with a new scope
     */
    inline fun <T> withScope(name: String = "anonymous", block: () -> T): T {
        pushScope(name)
        try {
            return block()
        } finally {
            popScope()
        }
    }
    
    /**
     * Create a snapshot of the current context
     * Returns a copy of all scopes
     */
    fun snapshot(): List<Scope> {
        return scopes.map { it.copy() }
    }
    
    /**
     * Restore context from a snapshot
     */
    fun restore(snapshot: List<Scope>) {
        scopes.clear()
        scopes.addAll(snapshot.map { it.copy() })
    }
    
    /**
     * Get all bindings from all scopes (for debugging)
     */
    fun allBindings(): Map<String, TypeDefinition> {
        val result = mutableMapOf<String, TypeDefinition>()
        // Add from outermost to innermost so inner scopes override
        for (scope in scopes) {
            result.putAll(scope.bindings)
        }
        return result
    }
    
    /**
     * Clear all scopes except global
     */
    fun reset() {
        val globalBindings = scopes.first().bindings.toMap()
        scopes.clear()
        scopes.add(Scope("global", globalBindings.toMutableMap()))
    }
    
    /**
     * Get information about current scopes (for debugging)
     */
    fun debugInfo(): String = buildString {
        appendLine("TypeContext [${scopes.size} scope(s)]:")
        scopes.forEachIndexed { index, scope ->
            val indent = "  ".repeat(index)
            appendLine("$indent- ${scope.name} [${scope.bindings.size} binding(s)]")
            scope.bindings.forEach { (name, type) ->
                appendLine("$indent  $name: ${type::class.simpleName}")
            }
        }
    }

    /**
     * Get the type at a specific path in the input or a type
     * Supports navigating through objects, arrays, and wildcards
     */
    fun getPathType(path: List<String>, rootType: TypeDefinition? = inputType): TypeDefinition {
        if (rootType == null) {
            return TypeDefinition.Never
        }

        var currentType: TypeDefinition = rootType

        for (segment in path) {
            currentType = when {
                segment == "*" -> {
                    // Wildcard access on arrays
                    when (currentType) {
                        is TypeDefinition.Array -> currentType.elementType
                        else -> TypeDefinition.Never
                    }
                }
                segment.toIntOrNull() != null -> {
                    // Numeric index on arrays
                    when (currentType) {
                        is TypeDefinition.Array -> currentType.elementType
                        else -> TypeDefinition.Never
                    }
                }
                else -> {
                    // Property access on objects
                    when (currentType) {
                        is TypeDefinition.Object -> {
                            currentType.properties[segment]?.type ?: TypeDefinition.Never
                        }
                        else -> TypeDefinition.Never
                    }
                }
            }

            // If we hit Never, stop early
            if (currentType is TypeDefinition.Never) {
                return currentType
            }
        }

        return currentType
    }

    /**
     * Look up a function by name
     */
    fun lookupFunction(name: String): FunctionSignature? {
        return FunctionTypeRegistry.getFunctionSignature(name)
    }

    /**
     * Infer the result type of a binary operation
     */
    fun inferBinaryOpType(operator: String, left: TypeDefinition, right: TypeDefinition): TypeDefinition {
        return when (operator) {
            // Arithmetic operators
            "+", "-", "*", "/", "%" -> inferArithmeticType(left, right)

            // Comparison operators always return boolean
            ">", "<", ">=", "<=", "==", "!=" -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)

            // Logical operators
            "&&", "||" -> {
                if (left is TypeDefinition.Scalar && left.kind == ScalarKind.BOOLEAN &&
                    right is TypeDefinition.Scalar && right.kind == ScalarKind.BOOLEAN) {
                    TypeDefinition.Scalar(ScalarKind.BOOLEAN)
                } else {
                    TypeDefinition.Never
                }
            }

            // Concatenation
            "++" -> {
                when {
                    left is TypeDefinition.Scalar && left.kind == ScalarKind.STRING &&
                    right is TypeDefinition.Scalar && right.kind == ScalarKind.STRING -> {
                        TypeDefinition.Scalar(ScalarKind.STRING)
                    }
                    left is TypeDefinition.Array && right is TypeDefinition.Array -> {
                        // Array concatenation - union of element types
                        TypeDefinition.Array(TypeCompatibility.union(left.elementType, right.elementType))
                    }
                    else -> TypeDefinition.Never
                }
            }

            else -> TypeDefinition.Unknown
        }
    }

    /**
     * Infer the result type of a unary operation
     */
    fun inferUnaryOpType(operator: String, operand: TypeDefinition): TypeDefinition {
        return when (operator) {
            "!" -> {
                // Logical negation
                if (operand is TypeDefinition.Scalar && operand.kind == ScalarKind.BOOLEAN) {
                    TypeDefinition.Scalar(ScalarKind.BOOLEAN)
                } else {
                    TypeDefinition.Never
                }
            }
            "-" -> {
                // Numeric negation
                when (operand) {
                    is TypeDefinition.Scalar -> {
                        when (operand.kind) {
                            ScalarKind.INTEGER, ScalarKind.NUMBER -> operand
                            else -> TypeDefinition.Never
                        }
                    }
                    else -> TypeDefinition.Never
                }
            }
            "+" -> {
                // Unary plus
                when (operand) {
                    is TypeDefinition.Scalar -> {
                        when (operand.kind) {
                            ScalarKind.INTEGER, ScalarKind.NUMBER -> operand
                            else -> TypeDefinition.Never
                        }
                    }
                    else -> TypeDefinition.Never
                }
            }
            else -> TypeDefinition.Unknown
        }
    }

    /**
     * Infer the result type of arithmetic operations
     */
    private fun inferArithmeticType(left: TypeDefinition, right: TypeDefinition): TypeDefinition {
        if (left !is TypeDefinition.Scalar || right !is TypeDefinition.Scalar) {
            return TypeDefinition.Never
        }

        val leftKind = left.kind
        val rightKind = right.kind

        // Integer + Integer = Integer
        if (leftKind == ScalarKind.INTEGER && rightKind == ScalarKind.INTEGER) {
            return TypeDefinition.Scalar(ScalarKind.INTEGER)
        }

        // Integer + Number = Number or Number + Integer = Number or Number + Number = Number
        if ((leftKind == ScalarKind.INTEGER || leftKind == ScalarKind.NUMBER) &&
            (rightKind == ScalarKind.INTEGER || rightKind == ScalarKind.NUMBER)) {
            return TypeDefinition.Scalar(ScalarKind.NUMBER)
        }

        return TypeDefinition.Never
    }

    override fun toString(): String {
        return "TypeContext(scopes=${scopes.size}, bindings=${allBindings().size})"
    }
}

/**
 * Represents a single scope with variable bindings
 */
data class Scope(
    val name: String,
    val bindings: MutableMap<String, TypeDefinition> = mutableMapOf()
) {
    /**
     * Create a deep copy of this scope
     */
    fun copy(): Scope {
        return Scope(name, bindings.toMutableMap())
    }
    
    /**
     * Check if this scope has a binding for a variable
     */
    fun has(name: String): Boolean {
        return bindings.containsKey(name)
    }
    
    /**
     * Get a binding from this scope
     */
    operator fun get(name: String): TypeDefinition? {
        return bindings[name]
    }
    
    /**
     * Set a binding in this scope
     */
    operator fun set(name: String, type: TypeDefinition) {
        bindings[name] = type
    }
    
    override fun toString(): String {
        return "Scope($name, ${bindings.size} bindings)"
    }
}

/**
 * Builder for creating type contexts with predefined bindings
 */
class TypeContextBuilder {
    private val context = TypeContext()
    
    fun bind(name: String, type: TypeDefinition): TypeContextBuilder {
        context.bind(name, type)
        return this
    }
    
    fun withScope(name: String, block: TypeContextBuilder.() -> Unit): TypeContextBuilder {
        context.pushScope(name)
        this.block()
        return this
    }
    
    fun build(): TypeContext {
        return context
    }
    
    companion object {
        /**
         * Create a context with common bindings
         */
        fun standard(inputType: TypeDefinition): TypeContext {
            return TypeContextBuilder()
                .bind("input", inputType)
                .build()
        }
        
        /**
         * Create an empty context
         */
        fun empty(): TypeContext {
            return TypeContext()
        }
    }
}

/**
 * Extension functions for easier context usage
 */

/**
 * Execute a block with bindings
 */
inline fun <T> TypeContext.withBindings(
    bindings: Map<String, TypeDefinition>,
    block: () -> T
): T {
    val snapshot = snapshot()
    try {
        bindings.forEach { (name, type) -> bind(name, type) }
        return block()
    } finally {
        restore(snapshot)
    }
}

/**
 * Execute a block with a single binding
 */
inline fun <T> TypeContext.withBinding(
    name: String,
    type: TypeDefinition,
    block: () -> T
): T {
    return withBindings(mapOf(name to type), block)
}
