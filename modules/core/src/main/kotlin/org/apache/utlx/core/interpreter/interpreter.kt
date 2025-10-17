package org.apache.utlx.core.interpreter

import org.apache.utlx.core.ast.*
import org.apache.utlx.core.udm.UDM
import kotlin.math.*

/**
 * Runtime value - result of evaluating an expression
 */
sealed class RuntimeValue {
    data class StringValue(val value: String) : RuntimeValue()
    data class NumberValue(val value: Double) : RuntimeValue()
    data class BooleanValue(val value: Boolean) : RuntimeValue()
    object NullValue : RuntimeValue()
    data class ArrayValue(val elements: List<RuntimeValue>) : RuntimeValue()
    data class ObjectValue(val properties: Map<String, RuntimeValue>) : RuntimeValue()
    data class FunctionValue(
        val parameters: List<String>,
        val body: Expression,
        val closure: Environment
    ) : RuntimeValue()
    data class UDMValue(val udm: UDM) : RuntimeValue()  // Wrap UDM structures
    
    fun toUDM(): UDM = when (this) {
        is StringValue -> UDM.Scalar.string(value)
        is NumberValue -> UDM.Scalar.number(value)
        is BooleanValue -> UDM.Scalar.boolean(value)
        is NullValue -> UDM.Scalar.nullValue()
        is ArrayValue -> UDM.Array(elements.map { it.toUDM() })
        is ObjectValue -> UDM.Object(properties.mapValues { it.value.toUDM() })
        is FunctionValue -> throw RuntimeError("Cannot convert function to UDM")
        is UDMValue -> udm
    }
    
    fun isTruthy(): Boolean = when (this) {
        is BooleanValue -> value
        is NullValue -> false
        is NumberValue -> value != 0.0
        is StringValue -> value.isNotEmpty()
        else -> true
    }
    
    override fun toString(): String = when (this) {
        is StringValue -> value
        is NumberValue -> if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
        is BooleanValue -> value.toString()
        is NullValue -> "null"
        is ArrayValue -> "[" + elements.joinToString(", ") + "]"
        is ObjectValue -> "{" + properties.entries.joinToString(", ") { "${it.key}: ${it.value}" } + "}"
        is FunctionValue -> "<function>"
        is UDMValue -> udm.toString()
    }
}

/**
 * Runtime environment for variable bindings
 */
class Environment(private val parent: Environment? = null) {
    private val bindings = mutableMapOf<String, RuntimeValue>()
    
    fun define(name: String, value: RuntimeValue) {
        bindings[name] = value
    }
    
    fun get(name: String): RuntimeValue {
        return bindings[name] ?: parent?.get(name) 
            ?: throw RuntimeError("Undefined variable: $name")
    }
    
    fun has(name: String): Boolean {
        return bindings.containsKey(name) || (parent?.has(name) == true)
    }
    
    fun set(name: String, value: RuntimeValue) {
        if (bindings.containsKey(name)) {
            bindings[name] = value
        } else if (parent != null && parent.has(name)) {
            parent.set(name, value)
        } else {
            throw RuntimeError("Cannot assign to undefined variable: $name")
        }
    }
    
    fun createChild(): Environment = Environment(this)
}

/**
 * Runtime error
 */
class RuntimeError(message: String, val location: Location? = null) : Exception(message) {
    override fun toString(): String {
        return if (location != null) {
            "Runtime error at ${location.line}:${location.column}: $message"
        } else {
            "Runtime error: $message"
        }
    }
}

/**
 * Interpreter - executes UTL-X AST
 */
class Interpreter {
    private val globalEnv = Environment()
    private val stdlib = StandardLibraryImpl()
    
    init {
        // Register standard library functions
        stdlib.registerAll(globalEnv)
    }
    
    /**
     * Execute a program with input data
     */
    fun execute(program: Program, inputData: UDM): RuntimeValue {
        val env = globalEnv.createChild()
        
        // Bind input data
        env.define("input", RuntimeValue.UDMValue(inputData))
        
        // Evaluate body
        return evaluate(program.body, env)
    }
    
    /**
     * Evaluate an expression
     */
    fun evaluate(expr: Expression, env: Environment): RuntimeValue {
        return when (expr) {
            is Expression.StringLiteral -> RuntimeValue.StringValue(expr.value)
            is Expression.NumberLiteral -> RuntimeValue.NumberValue(expr.value)
            is Expression.BooleanLiteral -> RuntimeValue.BooleanValue(expr.value)
            is Expression.NullLiteral -> RuntimeValue.NullValue
            
            is Expression.Identifier -> env.get(expr.name)
            
            is Expression.ObjectLiteral -> {
                val properties = mutableMapOf<String, RuntimeValue>()
                for (prop in expr.properties) {
                    properties[prop.key] = evaluate(prop.value, env)
                }
                RuntimeValue.ObjectValue(properties)
            }
            
            is Expression.ArrayLiteral -> {
                val elements = expr.elements.map { evaluate(it, env) }
                RuntimeValue.ArrayValue(elements)
            }
            
            is Expression.MemberAccess -> evaluateMemberAccess(expr, env)
            
            is Expression.IndexAccess -> evaluateIndexAccess(expr, env)
            
            is Expression.BinaryOp -> evaluateBinaryOp(expr, env)
            
            is Expression.UnaryOp -> evaluateUnaryOp(expr, env)
            
            is Expression.Conditional -> {
                val condition = evaluate(expr.condition, env)
                if (condition.isTruthy()) {
                    evaluate(expr.thenBranch, env)
                } else {
                    expr.elseBranch?.let { evaluate(it, env) } ?: RuntimeValue.NullValue
                }
            }
            
            is Expression.LetBinding -> {
                val value = evaluate(expr.value, env)
                env.define(expr.name, value)
                value
            }
            
            is Expression.Lambda -> {
                val paramNames = expr.parameters.map { it.name }
                RuntimeValue.FunctionValue(paramNames, expr.body, env)
            }
            
            is Expression.FunctionCall -> evaluateFunctionCall(expr, env)
            
            is Expression.Pipe -> {
                val sourceValue = evaluate(expr.source, env)
                // Create temporary environment with piped value
                val pipeEnv = env.createChild()
                pipeEnv.define("$", sourceValue)  // Pipe value available as $
                
                when (expr.target) {
                    is Expression.FunctionCall -> {
                        // Inject piped value as first argument
                        val args = listOf(expr.source) + expr.target.arguments
                        val modifiedCall = Expression.FunctionCall(
                            expr.target.function,
                            args,
                            expr.target.location
                        )
                        evaluate(modifiedCall, env)
                    }
                    else -> evaluate(expr.target, pipeEnv)
                }
            }
            
            is Expression.Block -> {
                var result: RuntimeValue = RuntimeValue.NullValue
                for (e in expr.expressions) {
                    result = evaluate(e, env)
                }
                result
            }
            
            is Expression.Match -> evaluateMatch(expr, env)
            
            is Expression.TemplateApplication -> {
                // Template application not yet implemented
                throw RuntimeError("Template application not yet implemented", expr.location)
            }
        }
    }
    
    private fun evaluateMemberAccess(expr: Expression.MemberAccess, env: Environment): RuntimeValue {
        val target = evaluate(expr.target, env)
        
        return when (target) {
            is RuntimeValue.ObjectValue -> {
                target.properties[expr.property] ?: RuntimeValue.NullValue
            }
            is RuntimeValue.UDMValue -> {
                when (val udm = target.udm) {
                    is UDM.Object -> {
                        if (expr.isAttribute) {
                            val attrValue = udm.getAttribute(expr.property)
                            attrValue?.let { RuntimeValue.StringValue(it) } ?: RuntimeValue.NullValue
                        } else {
                            val propValue = udm.get(expr.property)
                            propValue?.let { RuntimeValue.UDMValue(it) } ?: RuntimeValue.NullValue
                        }
                    }
                    else -> throw RuntimeError(
                        "Cannot access property on non-object UDM type",
                        expr.location
                    )
                }
            }
            else -> throw RuntimeError(
                "Cannot access property '${expr.property}' on ${target::class.simpleName}",
                expr.location
            )
        }
    }
    
    private fun evaluateIndexAccess(expr: Expression.IndexAccess, env: Environment): RuntimeValue {
        val target = evaluate(expr.target, env)
        val index = evaluate(expr.index, env)
        
        val indexNum = when (index) {
            is RuntimeValue.NumberValue -> index.value.toInt()
            else -> throw RuntimeError("Array index must be a number", expr.location)
        }
        
        return when (target) {
            is RuntimeValue.ArrayValue -> {
                if (indexNum < 0 || indexNum >= target.elements.size) {
                    RuntimeValue.NullValue
                } else {
                    target.elements[indexNum]
                }
            }
            is RuntimeValue.UDMValue -> {
                when (val udm = target.udm) {
                    is UDM.Array -> {
                        val element = udm.get(indexNum)
                        element?.let { RuntimeValue.UDMValue(it) } ?: RuntimeValue.NullValue
                    }
                    else -> throw RuntimeError("Cannot index non-array UDM type", expr.location)
                }
            }
            else -> throw RuntimeError("Cannot index ${target::class.simpleName}", expr.location)
        }
    }
    
    private fun evaluateBinaryOp(expr: Expression.BinaryOp, env: Environment): RuntimeValue {
        val left = evaluate(expr.left, env)
        val right = evaluate(expr.right, env)
        
        return when (expr.operator) {
            BinaryOperator.PLUS -> {
                when {
                    left is RuntimeValue.StringValue || right is RuntimeValue.StringValue -> {
                        RuntimeValue.StringValue(left.toString() + right.toString())
                    }
                    left is RuntimeValue.NumberValue && right is RuntimeValue.NumberValue -> {
                        RuntimeValue.NumberValue(left.value + right.value)
                    }
                    else -> throw RuntimeError("Invalid operands for +", expr.location)
                }
            }
            
            BinaryOperator.MINUS -> {
                val l = (left as? RuntimeValue.NumberValue)?.value 
                    ?: throw RuntimeError("Left operand must be number", expr.location)
                val r = (right as? RuntimeValue.NumberValue)?.value
                    ?: throw RuntimeError("Right operand must be number", expr.location)
                RuntimeValue.NumberValue(l - r)
            }
            
            BinaryOperator.MULTIPLY -> {
                val l = (left as? RuntimeValue.NumberValue)?.value
                    ?: throw RuntimeError("Left operand must be number", expr.location)
                val r = (right as? RuntimeValue.NumberValue)?.value
                    ?: throw RuntimeError("Right operand must be number", expr.location)
                RuntimeValue.NumberValue(l * r)
            }
            
            BinaryOperator.DIVIDE -> {
                val l = (left as? RuntimeValue.NumberValue)?.value
                    ?: throw RuntimeError("Left operand must be number", expr.location)
                val r = (right as? RuntimeValue.NumberValue)?.value
                    ?: throw RuntimeError("Right operand must be number", expr.location)
                if (r == 0.0) throw RuntimeError("Division by zero", expr.location)
                RuntimeValue.NumberValue(l / r)
            }
            
            BinaryOperator.MODULO -> {
                val l = (left as? RuntimeValue.NumberValue)?.value
                    ?: throw RuntimeError("Left operand must be number", expr.location)
                val r = (right as? RuntimeValue.NumberValue)?.value
                    ?: throw RuntimeError("Right operand must be number", expr.location)
                RuntimeValue.NumberValue(l % r)
            }
            
            BinaryOperator.EQUAL -> RuntimeValue.BooleanValue(valuesEqual(left, right))
            BinaryOperator.NOT_EQUAL -> RuntimeValue.BooleanValue(!valuesEqual(left, right))
            
            BinaryOperator.LESS_THAN -> {
                val cmp = compareValues(left, right, expr.location)
                RuntimeValue.BooleanValue(cmp < 0)
            }
            
            BinaryOperator.LESS_EQUAL -> {
                val cmp = compareValues(left, right, expr.location)
                RuntimeValue.BooleanValue(cmp <= 0)
            }
            
            BinaryOperator.GREATER_THAN -> {
                val cmp = compareValues(left, right, expr.location)
                RuntimeValue.BooleanValue(cmp > 0)
            }
            
            BinaryOperator.GREATER_EQUAL -> {
                val cmp = compareValues(left, right, expr.location)
                RuntimeValue.BooleanValue(cmp >= 0)
            }
            
            BinaryOperator.AND -> {
                RuntimeValue.BooleanValue(left.isTruthy() && right.isTruthy())
            }
            
            BinaryOperator.OR -> {
                RuntimeValue.BooleanValue(left.isTruthy() || right.isTruthy())
            }
        }
    }
    
    private fun evaluateUnaryOp(expr: Expression.UnaryOp, env: Environment): RuntimeValue {
        val operand = evaluate(expr.operand, env)
        
        return when (expr.operator) {
            UnaryOperator.MINUS -> {
                val value = (operand as? RuntimeValue.NumberValue)?.value
                    ?: throw RuntimeError("Unary minus requires number", expr.location)
                RuntimeValue.NumberValue(-value)
            }
            
            UnaryOperator.NOT -> {
                RuntimeValue.BooleanValue(!operand.isTruthy())
            }
        }
    }
    
    private fun evaluateFunctionCall(expr: Expression.FunctionCall, env: Environment): RuntimeValue {
        // Check if it's a built-in function
        if (expr.function is Expression.Identifier) {
            val funcName = expr.function.name
            
            // Check if it's a native stdlib function
            if (StandardLibraryImpl.nativeFunctions.containsKey(funcName)) {
                val nativeFunc = StandardLibraryImpl.nativeFunctions[funcName]!!
                val argValues = expr.arguments.map { evaluate(it, env) }
                return nativeFunc(argValues)
            }
            
            if (env.has(funcName)) {
                val func = env.get(funcName)
                if (func is RuntimeValue.FunctionValue) {
                    return callFunction(func, expr.arguments, env, expr.location)
                }
            }
        }
        
        val func = evaluate(expr.function, env)
        if (func !is RuntimeValue.FunctionValue) {
            throw RuntimeError("Cannot call non-function", expr.location)
        }
        
        return callFunction(func, expr.arguments, env, expr.location)
    }
    
    private fun callFunction(
        func: RuntimeValue.FunctionValue,
        argExprs: List<Expression>,
        callEnv: Environment,
        location: Location
    ): RuntimeValue {
        if (argExprs.size != func.parameters.size) {
            throw RuntimeError(
                "Function expects ${func.parameters.size} arguments, got ${argExprs.size}",
                location
            )
        }
        
        // Evaluate arguments in caller's environment
        val argValues = argExprs.map { evaluate(it, callEnv) }
        
        // Create new environment with function's closure
        val funcEnv = func.closure.createChild()
        
        // Bind parameters
        func.parameters.zip(argValues).forEach { (param, value) ->
            funcEnv.define(param, value)
        }
        
        // Execute function body
        return evaluate(func.body, funcEnv)
    }
    
    private fun evaluateMatch(expr: Expression.Match, env: Environment): RuntimeValue {
        val value = evaluate(expr.value, env)
        
        for (case in expr.cases) {
            val matches = when (val pattern = case.pattern) {
                is Pattern.Wildcard -> true
                is Pattern.Literal -> valuesEqual(value, literalToRuntimeValue(pattern.value))
                is Pattern.Variable -> {
                    env.define(pattern.name, value)
                    true
                }
            }
            
            if (matches) {
                return evaluate(case.expression, env)
            }
        }
        
        throw RuntimeError("No matching case in match expression", expr.location)
    }
    
    private fun valuesEqual(left: RuntimeValue, right: RuntimeValue): Boolean {
        return when {
            left is RuntimeValue.StringValue && right is RuntimeValue.StringValue -> 
                left.value == right.value
            left is RuntimeValue.NumberValue && right is RuntimeValue.NumberValue -> 
                left.value == right.value
            left is RuntimeValue.BooleanValue && right is RuntimeValue.BooleanValue -> 
                left.value == right.value
            left is RuntimeValue.NullValue && right is RuntimeValue.NullValue -> true
            else -> false
        }
    }
    
    private fun compareValues(left: RuntimeValue, right: RuntimeValue, location: Location): Int {
        return when {
            left is RuntimeValue.NumberValue && right is RuntimeValue.NumberValue -> 
                left.value.compareTo(right.value)
            left is RuntimeValue.StringValue && right is RuntimeValue.StringValue -> 
                left.value.compareTo(right.value)
            else -> throw RuntimeError("Cannot compare these types", location)
        }
    }
    
    private fun literalToRuntimeValue(value: Any?): RuntimeValue {
        return when (value) {
            is String -> RuntimeValue.StringValue(value)
            is Number -> RuntimeValue.NumberValue(value.toDouble())
            is Boolean -> RuntimeValue.BooleanValue(value)
            null -> RuntimeValue.NullValue
            else -> RuntimeValue.NullValue
        }
    }
}

/**
 * Standard library implementations
 */
class StandardLibraryImpl {
    fun registerAll(env: Environment) {
        // String functions
        registerFunction(env, "upper") { args ->
            val str = (args[0] as? RuntimeValue.StringValue)?.value
                ?: throw RuntimeError("upper() requires string argument")
            RuntimeValue.StringValue(str.uppercase())
        }
        
        registerFunction(env, "lower") { args ->
            val str = (args[0] as? RuntimeValue.StringValue)?.value
                ?: throw RuntimeError("lower() requires string argument")
            RuntimeValue.StringValue(str.lowercase())
        }
        
        registerFunction(env, "trim") { args ->
            val str = (args[0] as? RuntimeValue.StringValue)?.value
                ?: throw RuntimeError("trim() requires string argument")
            RuntimeValue.StringValue(str.trim())
        }
        
        // Array functions
        registerFunction(env, "sum") { args ->
            val arr = (args[0] as? RuntimeValue.ArrayValue)?.elements
                ?: throw RuntimeError("sum() requires array argument")
            val sum = arr.sumOf { 
                (it as? RuntimeValue.NumberValue)?.value 
                    ?: throw RuntimeError("sum() requires array of numbers")
            }
            RuntimeValue.NumberValue(sum)
        }
        
        registerFunction(env, "count") { args ->
            val arr = (args[0] as? RuntimeValue.ArrayValue)?.elements
                ?: throw RuntimeError("count() requires array argument")
            RuntimeValue.NumberValue(arr.size.toDouble())
        }
        
        registerFunction(env, "first") { args ->
            val arr = (args[0] as? RuntimeValue.ArrayValue)?.elements
                ?: throw RuntimeError("first() requires array argument")
            arr.firstOrNull() ?: RuntimeValue.NullValue
        }
        
        registerFunction(env, "last") { args ->
            val arr = (args[0] as? RuntimeValue.ArrayValue)?.elements
                ?: throw RuntimeError("last() requires array argument")
            arr.lastOrNull() ?: RuntimeValue.NullValue
        }
        
        // Math functions
        registerFunction(env, "abs") { args ->
            val num = (args[0] as? RuntimeValue.NumberValue)?.value
                ?: throw RuntimeError("abs() requires number argument")
            RuntimeValue.NumberValue(abs(num))
        }
        
        registerFunction(env, "round") { args ->
            val num = (args[0] as? RuntimeValue.NumberValue)?.value
                ?: throw RuntimeError("round() requires number argument")
            RuntimeValue.NumberValue(round(num))
        }
        
        registerFunction(env, "ceil") { args ->
            val num = (args[0] as? RuntimeValue.NumberValue)?.value
                ?: throw RuntimeError("ceil() requires number argument")
            RuntimeValue.NumberValue(ceil(num))
        }
        
        registerFunction(env, "floor") { args ->
            val num = (args[0] as? RuntimeValue.NumberValue)?.value
                ?: throw RuntimeError("floor() requires number argument")
            RuntimeValue.NumberValue(floor(num))
        }
        
        registerFunction(env, "min") { args ->
            val a = (args[0] as? RuntimeValue.NumberValue)?.value
                ?: throw RuntimeError("min() requires number arguments")
            val b = (args[1] as? RuntimeValue.NumberValue)?.value
                ?: throw RuntimeError("min() requires number arguments")
            RuntimeValue.NumberValue(min(a, b))
        }
        
        registerFunction(env, "max") { args ->
            val a = (args[0] as? RuntimeValue.NumberValue)?.value
                ?: throw RuntimeError("max() requires number arguments")
            val b = (args[1] as? RuntimeValue.NumberValue)?.value
                ?: throw RuntimeError("max() requires number arguments")
            RuntimeValue.NumberValue(max(a, b))
        }
    }
    
    private fun registerFunction(
        env: Environment,
        name: String,
        impl: (List<RuntimeValue>) -> RuntimeValue
    ) {
        // Wrap native function in a RuntimeValue.FunctionValue
        // For simplicity, we'll use a special marker
        val wrapper = RuntimeValue.FunctionValue(
            parameters = listOf(), // Will be checked by impl
            body = Expression.NullLiteral(Location(0, 0)), // Placeholder
            closure = env
        )
        
        // Store implementation in a map for lookup
        nativeFunctions[name] = impl
        env.define(name, wrapper)
    }
    
    companion object {
        val nativeFunctions = mutableMapOf<String, (List<RuntimeValue>) -> RuntimeValue>()
    }
}
