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
                if (indexNum < 0) {
                    throw RuntimeError("Array index out of bounds: negative index $indexNum", expr.location)
                }
                if (indexNum >= target.elements.size) {
                    throw RuntimeError("Array index out of bounds: index $indexNum, size ${target.elements.size}", expr.location)
                }
                target.elements[indexNum]
            }
            is RuntimeValue.UDMValue -> {
                when (val udm = target.udm) {
                    is UDM.Array -> {
                        if (indexNum < 0) {
                            throw RuntimeError("Array index out of bounds: negative index $indexNum", expr.location)
                        }
                        if (indexNum >= udm.size()) {
                            throw RuntimeError("Array index out of bounds: index $indexNum, size ${udm.size()}", expr.location)
                        }
                        val element = udm.get(indexNum)
                        RuntimeValue.UDMValue(element!!)
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
                    left is RuntimeValue.StringValue || right is RuntimeValue.StringValue || 
                    (left is RuntimeValue.UDMValue && left.udm is UDM.Scalar && left.udm.value is String) ||
                    (right is RuntimeValue.UDMValue && right.udm is UDM.Scalar && right.udm.value is String) -> {
                        val leftStr = extractStringValue(left)
                        val rightStr = extractStringValue(right)
                        RuntimeValue.StringValue(leftStr + rightStr)
                    }
                    else -> {
                        try {
                            val l = extractNumber(left, "Left operand must be number for arithmetic", expr.location)
                            val r = extractNumber(right, "Right operand must be number for arithmetic", expr.location)
                            RuntimeValue.NumberValue(l + r)
                        } catch (e: RuntimeError) {
                            throw RuntimeError("Invalid operands for +", expr.location)
                        }
                    }
                }
            }
            
            BinaryOperator.MINUS -> {
                val l = extractNumber(left, "Left operand must be number", expr.location)
                val r = extractNumber(right, "Right operand must be number", expr.location)
                RuntimeValue.NumberValue(l - r)
            }
            
            BinaryOperator.MULTIPLY -> {
                val l = extractNumber(left, "Left operand must be number", expr.location)
                val r = extractNumber(right, "Right operand must be number", expr.location)
                RuntimeValue.NumberValue(l * r)
            }
            
            BinaryOperator.DIVIDE -> {
                val l = extractNumber(left, "Left operand must be number", expr.location)
                val r = extractNumber(right, "Right operand must be number", expr.location)
                if (r == 0.0) throw RuntimeError("Division by zero", expr.location)
                RuntimeValue.NumberValue(l / r)
            }
            
            BinaryOperator.MODULO -> {
                val l = extractNumber(left, "Left operand must be number", expr.location)
                val r = extractNumber(right, "Right operand must be number", expr.location)
                if (r == 0.0) throw RuntimeError("Division by zero", expr.location)
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
            
            // Handle UDM value comparisons
            left is RuntimeValue.UDMValue && right is RuntimeValue.StringValue -> {
                val udm = left.udm
                if (udm is UDM.Scalar && udm.value is String) {
                    udm.value == right.value
                } else false
            }
            left is RuntimeValue.StringValue && right is RuntimeValue.UDMValue -> {
                val udm = right.udm
                if (udm is UDM.Scalar && udm.value is String) {
                    left.value == udm.value
                } else false
            }
            left is RuntimeValue.UDMValue && right is RuntimeValue.NumberValue -> {
                val udm = left.udm
                if (udm is UDM.Scalar && udm.value is Number) {
                    udm.value.toDouble() == right.value
                } else false
            }
            left is RuntimeValue.NumberValue && right is RuntimeValue.UDMValue -> {
                val udm = right.udm
                if (udm is UDM.Scalar && udm.value is Number) {
                    left.value == udm.value.toDouble()
                } else false
            }
            left is RuntimeValue.UDMValue && right is RuntimeValue.UDMValue -> {
                val leftUdm = left.udm
                val rightUdm = right.udm
                if (leftUdm is UDM.Scalar && rightUdm is UDM.Scalar) {
                    leftUdm.value == rightUdm.value
                } else false
            }
            
            else -> false
        }
    }
    
    private fun compareValues(left: RuntimeValue, right: RuntimeValue, location: Location): Int {
        // Try to extract numbers for comparison
        try {
            val leftNum = when (left) {
                is RuntimeValue.NumberValue -> left.value
                is RuntimeValue.UDMValue -> {
                    when (val udm = left.udm) {
                        is UDM.Scalar -> {
                            when (val value = udm.value) {
                                is Number -> value.toDouble()
                                else -> null
                            }
                        }
                        else -> null
                    }
                }
                else -> null
            }
            
            val rightNum = when (right) {
                is RuntimeValue.NumberValue -> right.value
                is RuntimeValue.UDMValue -> {
                    when (val udm = right.udm) {
                        is UDM.Scalar -> {
                            when (val value = udm.value) {
                                is Number -> value.toDouble()
                                else -> null
                            }
                        }
                        else -> null
                    }
                }
                else -> null
            }
            
            if (leftNum != null && rightNum != null) {
                return leftNum.compareTo(rightNum)
            }
        } catch (e: Exception) {
            // Fall through to string comparison
        }
        
        // Try string comparison
        return when {
            left is RuntimeValue.StringValue && right is RuntimeValue.StringValue -> 
                left.value.compareTo(right.value)
            else -> throw RuntimeError("Cannot compare these types", location)
        }
    }
    
    private fun evaluateFunctionCall(expr: Expression.FunctionCall, env: Environment): RuntimeValue {
        // First try to resolve the function from the environment
        val functionName = (expr.function as? Expression.Identifier)?.name
        
        try {
            val function = evaluate(expr.function, env)
            val args = expr.arguments.map { evaluate(it, env) }
            
            return when (function) {
                is RuntimeValue.FunctionValue -> {
                    // User-defined function or lambda
                    if (function.parameters.isEmpty() && functionName != null && StandardLibraryImpl.nativeFunctions.containsKey(functionName)) {
                        // Native function
                        val nativeImpl = StandardLibraryImpl.nativeFunctions[functionName]!!
                        nativeImpl(args)
                    } else {
                        // User-defined function
                        if (args.size != function.parameters.size) {
                            throw RuntimeError("Function expects ${function.parameters.size} arguments, got ${args.size}", expr.location)
                        }
                        
                        val funcEnv = function.closure.createChild()
                        for ((param, arg) in function.parameters.zip(args)) {
                            funcEnv.define(param, arg)
                        }
                        
                        evaluate(function.body, funcEnv)
                    }
                }
                else -> throw RuntimeError("Cannot call non-function value", expr.location)
            }
        } catch (e: RuntimeError) {
            // If function is not found in environment, try dynamic loading from stdlib
            if (functionName != null && e.message?.contains("Undefined variable") == true) {
                return tryLoadStdlibFunction(functionName, expr.arguments, env, expr.location)
            }
            throw e
        }
    }
    
    /**
     * Dynamic function loader - attempts to load stdlib functions on demand
     */
    private fun tryLoadStdlibFunction(functionName: String, arguments: List<Expression>, env: Environment, location: Location): RuntimeValue {
        // Try direct function invocation first for problematic functions, then fall back to registry
        try {
            return tryDirectFunctionInvocation(functionName, arguments, env, location)
        } catch (e: RuntimeError) {
            // Direct invocation failed, try the registry approach
            try {
                val stdlibClass = Class.forName("org.apache.utlx.stdlib.StandardLibrary")
                
                // Get the Kotlin object instance (INSTANCE field for object singletons)
                val instanceField = stdlibClass.getField("INSTANCE")
                val stdlibInstance = instanceField.get(null)
                
                val getAllFunctionsMethod = stdlibClass.getMethod("getAllFunctions")
                val functions = getAllFunctionsMethod.invoke(stdlibInstance) as Map<String, Any>
                
                if (functions.containsKey(functionName) && functions[functionName] != null) {
                    val stdlibFunction = functions[functionName]!!
                    val executeMethod = stdlibFunction.javaClass.getMethod("execute", List::class.java)
                    
                    // Evaluate arguments and convert to UDM
                    val args = arguments.map { evaluate(it, env) }
                    val udmArgs = args.map { runtimeValueToUDM(it) }
                    
                    // Execute stdlib function
                    val result = executeMethod.invoke(stdlibFunction, udmArgs)
                    
                    // Convert result back to RuntimeValue
                    return udmToRuntimeValue(result)
                }
            } catch (ex: Exception) {
                // Registry approach also failed
            }
        }
        
        throw RuntimeError("Undefined function: $functionName", location)
    }
    
    /**
     * Fallback method to invoke stdlib functions directly when they're null in registry
     */
    private fun tryDirectFunctionInvocation(functionName: String, arguments: List<Expression>, env: Environment, location: Location): RuntimeValue {
        // Implement common stdlib functions directly to bypass registration issues
        try {
            when (functionName) {
                "base64Encode" -> {
                    if (arguments.size != 1) throw RuntimeError("base64Encode expects 1 argument", location)
                    val arg = evaluate(arguments[0], env)
                    val str = when (arg) {
                        is RuntimeValue.StringValue -> arg.value
                        else -> throw RuntimeError("base64Encode expects string argument", location)
                    }
                    val encoded = java.util.Base64.getEncoder().encodeToString(str.toByteArray())
                    return RuntimeValue.StringValue(encoded)
                }
                "base64Decode" -> {
                    if (arguments.size != 1) throw RuntimeError("base64Decode expects 1 argument", location)
                    val arg = evaluate(arguments[0], env)
                    val str = when (arg) {
                        is RuntimeValue.StringValue -> arg.value
                        else -> throw RuntimeError("base64Decode expects string argument", location)
                    }
                    try {
                        val decoded = String(java.util.Base64.getDecoder().decode(str))
                        return RuntimeValue.StringValue(decoded)
                    } catch (e: Exception) {
                        throw RuntimeError("Invalid base64 string", location)
                    }
                }
                "urlEncode" -> {
                    if (arguments.size != 1) throw RuntimeError("urlEncode expects 1 argument", location)
                    val arg = evaluate(arguments[0], env)
                    val str = when (arg) {
                        is RuntimeValue.StringValue -> arg.value
                        else -> throw RuntimeError("urlEncode expects string argument", location)
                    }
                    val encoded = java.net.URLEncoder.encode(str, "UTF-8")
                    return RuntimeValue.StringValue(encoded)
                }
                "urlDecode" -> {
                    if (arguments.size != 1) throw RuntimeError("urlDecode expects 1 argument", location)
                    val arg = evaluate(arguments[0], env)
                    val str = when (arg) {
                        is RuntimeValue.StringValue -> arg.value
                        else -> throw RuntimeError("urlDecode expects string argument", location)
                    }
                    val decoded = java.net.URLDecoder.decode(str, "UTF-8")
                    return RuntimeValue.StringValue(decoded)
                }
                "md5" -> {
                    if (arguments.size != 1) throw RuntimeError("md5 expects 1 argument", location)
                    val arg = evaluate(arguments[0], env)
                    val str = when (arg) {
                        is RuntimeValue.StringValue -> arg.value
                        else -> throw RuntimeError("md5 expects string argument", location)
                    }
                    val md = java.security.MessageDigest.getInstance("MD5")
                    val hashBytes = md.digest(str.toByteArray(Charsets.UTF_8))
                    val hexString = hashBytes.joinToString("") { "%02x".format(it) }
                    return RuntimeValue.StringValue(hexString)
                }
                "length" -> {
                    if (arguments.size != 1) throw RuntimeError("length expects 1 argument", location)
                    val arg = evaluate(arguments[0], env)
                    val len = when (arg) {
                        is RuntimeValue.StringValue -> arg.value.length
                        is RuntimeValue.ArrayValue -> arg.elements.size
                        is RuntimeValue.ObjectValue -> arg.properties.size
                        else -> throw RuntimeError("length expects string, array, or object argument", location)
                    }
                    return RuntimeValue.NumberValue(len.toDouble())
                }
                "count" -> {
                    if (arguments.size != 1) throw RuntimeError("count expects 1 argument", location)
                    val arg = evaluate(arguments[0], env)
                    val count = when (arg) {
                        is RuntimeValue.ArrayValue -> arg.elements.size
                        is RuntimeValue.ObjectValue -> arg.properties.size
                        is RuntimeValue.StringValue -> arg.value.length
                        else -> throw RuntimeError("count expects array, object, or string argument", location)
                    }
                    return RuntimeValue.NumberValue(count.toDouble())
                }
                "toString" -> {
                    if (arguments.size != 1) throw RuntimeError("toString expects 1 argument", location)
                    val arg = evaluate(arguments[0], env)
                    val str = when (arg) {
                        is RuntimeValue.StringValue -> arg.value
                        is RuntimeValue.NumberValue -> arg.value.toString()
                        is RuntimeValue.BooleanValue -> arg.value.toString()
                        is RuntimeValue.NullValue -> "null"
                        else -> arg.toString()
                    }
                    return RuntimeValue.StringValue(str)
                }
                "distance" -> {
                    if (arguments.size != 4) throw RuntimeError("distance expects 4 arguments (lat1, lon1, lat2, lon2)", location)
                    val args = arguments.map { evaluate(it, env) }
                    val lat1 = (args[0] as? RuntimeValue.NumberValue)?.value ?: throw RuntimeError("distance expects numeric arguments", location)
                    val lon1 = (args[1] as? RuntimeValue.NumberValue)?.value ?: throw RuntimeError("distance expects numeric arguments", location)
                    val lat2 = (args[2] as? RuntimeValue.NumberValue)?.value ?: throw RuntimeError("distance expects numeric arguments", location)
                    val lon2 = (args[3] as? RuntimeValue.NumberValue)?.value ?: throw RuntimeError("distance expects numeric arguments", location)
                    
                    // Haversine formula for distance calculation
                    val R = 6371.0 // Earth's radius in kilometers
                    val dLat = Math.toRadians(lat2 - lat1)
                    val dLon = Math.toRadians(lon2 - lon1)
                    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
                    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
                    val distance = R * c
                    
                    return RuntimeValue.NumberValue(distance)
                }
                "now" -> {
                    if (arguments.isNotEmpty()) throw RuntimeError("now expects no arguments", location)
                    val currentTime = java.time.Instant.now().toString()
                    return RuntimeValue.StringValue(currentTime)
                }
            }
        } catch (e: RuntimeError) {
            throw e
        } catch (e: Exception) {
            throw RuntimeError("Error in function '$functionName': ${e.message}", location)
        }
        
        throw RuntimeError("Function '$functionName' not implemented in direct invocation", location)
    }
    
    /**
     * Convert RuntimeValue to UDM for stdlib function calls
     */
    private fun runtimeValueToUDM(value: RuntimeValue): org.apache.utlx.core.udm.UDM {
        return when (value) {
            is RuntimeValue.StringValue -> org.apache.utlx.core.udm.UDM.Scalar(value.value)
            is RuntimeValue.NumberValue -> org.apache.utlx.core.udm.UDM.Scalar(value.value)
            is RuntimeValue.BooleanValue -> org.apache.utlx.core.udm.UDM.Scalar(value.value)
            is RuntimeValue.NullValue -> org.apache.utlx.core.udm.UDM.Scalar(null)
            is RuntimeValue.ArrayValue -> org.apache.utlx.core.udm.UDM.Array(value.elements.map { runtimeValueToUDM(it) })
            is RuntimeValue.ObjectValue -> org.apache.utlx.core.udm.UDM.Object(value.properties.mapValues { runtimeValueToUDM(it.value) })
            is RuntimeValue.UDMValue -> value.udm
            else -> throw RuntimeError("Cannot convert ${value::class.simpleName} to UDM for stdlib function call")
        }
    }
    
    /**
     * Convert UDM result back to RuntimeValue
     */
    private fun udmToRuntimeValue(result: Any?): RuntimeValue {
        return when (result) {
            is org.apache.utlx.core.udm.UDM.Scalar -> {
                when (val value = result.value) {
                    is String -> RuntimeValue.StringValue(value)
                    is Number -> RuntimeValue.NumberValue(value.toDouble())
                    is Boolean -> RuntimeValue.BooleanValue(value)
                    null -> RuntimeValue.NullValue
                    else -> RuntimeValue.StringValue(value.toString())
                }
            }
            is org.apache.utlx.core.udm.UDM.Array -> RuntimeValue.ArrayValue(result.elements.map { udmToRuntimeValue(it) })
            is org.apache.utlx.core.udm.UDM.Object -> RuntimeValue.ObjectValue(result.properties.mapValues { udmToRuntimeValue(it.value) })
            is org.apache.utlx.core.udm.UDM -> RuntimeValue.UDMValue(result)
            else -> RuntimeValue.StringValue(result.toString())
        }
    }
    
    private fun extractNumber(value: RuntimeValue, errorMessage: String, location: Location): Double {
        return when (value) {
            is RuntimeValue.NumberValue -> value.value
            is RuntimeValue.UDMValue -> {
                when (val udm = value.udm) {
                    is UDM.Scalar -> {
                        when (val scalarValue = udm.value) {
                            is Number -> scalarValue.toDouble()
                            is String -> scalarValue.toDoubleOrNull() 
                                ?: throw RuntimeError(errorMessage, location)
                            else -> throw RuntimeError(errorMessage, location)
                        }
                    }
                    else -> throw RuntimeError(errorMessage, location)
                }
            }
            else -> throw RuntimeError(errorMessage, location)
        }
    }
    
    private fun extractStringValue(value: RuntimeValue): String {
        return when (value) {
            is RuntimeValue.StringValue -> value.value
            is RuntimeValue.NumberValue -> value.value.toString()
            is RuntimeValue.BooleanValue -> value.value.toString()
            is RuntimeValue.NullValue -> "null"
            is RuntimeValue.UDMValue -> {
                when (val udm = value.udm) {
                    is UDM.Scalar -> udm.value.toString()
                    else -> value.toString()
                }
            }
            else -> value.toString()
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
    private fun extractNumber(value: RuntimeValue, errorMessage: String): Double {
        return when (value) {
            is RuntimeValue.NumberValue -> value.value
            is RuntimeValue.UDMValue -> {
                when (val udm = value.udm) {
                    is UDM.Scalar -> {
                        when (val scalarValue = udm.value) {
                            is Number -> scalarValue.toDouble()
                            is String -> scalarValue.toDoubleOrNull() 
                                ?: throw RuntimeError(errorMessage)
                            else -> throw RuntimeError(errorMessage)
                        }
                    }
                    else -> throw RuntimeError(errorMessage)
                }
            }
            else -> throw RuntimeError(errorMessage)
        }
    }
    
    fun registerAll(env: Environment) {
        // String functions
        registerFunction(env, "upper") { args ->
            val arg = args[0]
            val str = when (arg) {
                is RuntimeValue.StringValue -> arg.value
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Scalar -> {
                            val value = udm.value
                            if (value is String) value
                            else throw RuntimeError("upper() requires a string argument, got ${value?.javaClass?.simpleName ?: "null"}")
                        }
                        is UDM.Array -> throw RuntimeError("upper() requires a string argument, got array")
                        is UDM.Object -> throw RuntimeError("upper() requires a string argument, got object")
                        else -> throw RuntimeError("upper() requires a string argument, got ${udm.javaClass.simpleName}")
                    }
                }
                is RuntimeValue.NumberValue -> throw RuntimeError("upper() requires a string argument, got number")
                is RuntimeValue.BooleanValue -> throw RuntimeError("upper() requires a string argument, got boolean")
                is RuntimeValue.ArrayValue -> throw RuntimeError("upper() requires a string argument, got array")
                is RuntimeValue.ObjectValue -> throw RuntimeError("upper() requires a string argument, got object")
                is RuntimeValue.NullValue -> throw RuntimeError("upper() requires a string argument, got null")
                else -> throw RuntimeError("upper() requires a string argument, got ${arg.javaClass.simpleName}")
            }
            RuntimeValue.StringValue(str.uppercase())
        }
        
        registerFunction(env, "lower") { args ->
            val arg = args[0]
            val str = when (arg) {
                is RuntimeValue.StringValue -> arg.value
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Scalar -> {
                            val value = udm.value
                            if (value is String) value
                            else throw RuntimeError("lower() requires a string argument, got ${value?.javaClass?.simpleName ?: "null"}")
                        }
                        is UDM.Array -> throw RuntimeError("lower() requires a string argument, got array")
                        is UDM.Object -> throw RuntimeError("lower() requires a string argument, got object")
                        else -> throw RuntimeError("lower() requires a string argument, got ${udm.javaClass.simpleName}")
                    }
                }
                is RuntimeValue.NumberValue -> throw RuntimeError("lower() requires a string argument, got number")
                is RuntimeValue.BooleanValue -> throw RuntimeError("lower() requires a string argument, got boolean")
                is RuntimeValue.ArrayValue -> throw RuntimeError("lower() requires a string argument, got array")
                is RuntimeValue.ObjectValue -> throw RuntimeError("lower() requires a string argument, got object")
                is RuntimeValue.NullValue -> throw RuntimeError("lower() requires a string argument, got null")
                else -> throw RuntimeError("lower() requires a string argument, got ${arg.javaClass.simpleName}")
            }
            RuntimeValue.StringValue(str.lowercase())
        }
        
        registerFunction(env, "trim") { args ->
            val arg = args[0]
            val str = when (arg) {
                is RuntimeValue.StringValue -> arg.value
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Scalar -> {
                            val value = udm.value
                            if (value is String) value
                            else throw RuntimeError("trim() requires a string argument, got ${value?.javaClass?.simpleName ?: "null"}")
                        }
                        is UDM.Array -> throw RuntimeError("trim() requires a string argument, got array")
                        is UDM.Object -> throw RuntimeError("trim() requires a string argument, got object")
                        else -> throw RuntimeError("trim() requires a string argument, got ${udm.javaClass.simpleName}")
                    }
                }
                is RuntimeValue.NumberValue -> throw RuntimeError("trim() requires a string argument, got number")
                is RuntimeValue.BooleanValue -> throw RuntimeError("trim() requires a string argument, got boolean")
                is RuntimeValue.ArrayValue -> throw RuntimeError("trim() requires a string argument, got array")
                is RuntimeValue.ObjectValue -> throw RuntimeError("trim() requires a string argument, got object")
                is RuntimeValue.NullValue -> throw RuntimeError("trim() requires a string argument, got null")
                else -> throw RuntimeError("trim() requires a string argument, got ${arg.javaClass.simpleName}")
            }
            RuntimeValue.StringValue(str.trim())
        }
        
        // Array functions
        registerFunction(env, "sum") { args ->
            val arg = args[0]
            val arr = when (arg) {
                is RuntimeValue.ArrayValue -> arg.elements
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Array -> udm.elements.map { RuntimeValue.UDMValue(it) }
                        is UDM.Scalar -> throw RuntimeError("sum() requires an array argument, got ${udm.value?.javaClass?.simpleName ?: "null"}")
                        is UDM.Object -> throw RuntimeError("sum() requires an array argument, got object")
                        else -> throw RuntimeError("sum() requires an array argument, got ${udm.javaClass.simpleName}")
                    }
                }
                is RuntimeValue.StringValue -> throw RuntimeError("sum() requires an array argument, got string")
                is RuntimeValue.NumberValue -> throw RuntimeError("sum() requires an array argument, got number")
                is RuntimeValue.BooleanValue -> throw RuntimeError("sum() requires an array argument, got boolean")
                is RuntimeValue.ObjectValue -> throw RuntimeError("sum() requires an array argument, got object")
                is RuntimeValue.NullValue -> throw RuntimeError("sum() requires an array argument, got null")
                else -> throw RuntimeError("sum() requires an array argument, got ${arg.javaClass.simpleName}")
            }
            
            if (arr.isEmpty()) {
                return@registerFunction RuntimeValue.NumberValue(0.0)
            }
            
            val sum = arr.sumOf { element ->
                extractNumber(element, "sum() requires array of numbers")
            }
            RuntimeValue.NumberValue(sum)
        }
        
        registerFunction(env, "count") { args ->
            val arg = args[0]
            val arr = when (arg) {
                is RuntimeValue.ArrayValue -> arg.elements
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Array -> udm.elements
                        is UDM.Scalar -> throw RuntimeError("count() requires an array argument, got ${udm.value?.javaClass?.simpleName ?: "null"}")
                        is UDM.Object -> throw RuntimeError("count() requires an array argument, got object")
                        else -> throw RuntimeError("count() requires an array argument, got ${udm.javaClass.simpleName}")
                    }
                }
                is RuntimeValue.StringValue -> throw RuntimeError("count() requires an array argument, got string")
                is RuntimeValue.NumberValue -> throw RuntimeError("count() requires an array argument, got number")
                is RuntimeValue.BooleanValue -> throw RuntimeError("count() requires an array argument, got boolean")
                is RuntimeValue.ObjectValue -> throw RuntimeError("count() requires an array argument, got object")
                is RuntimeValue.NullValue -> throw RuntimeError("count() requires an array argument, got null")
                else -> throw RuntimeError("count() requires an array argument, got ${arg.javaClass.simpleName}")
            }
            RuntimeValue.NumberValue(arr.size.toDouble())
        }
        
        registerFunction(env, "first") { args ->
            val arg = args[0]
            val arr = when (arg) {
                is RuntimeValue.ArrayValue -> arg.elements
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Array -> udm.elements.map { RuntimeValue.UDMValue(it) }
                        is UDM.Scalar -> throw RuntimeError("first() requires an array argument, got ${udm.value?.javaClass?.simpleName ?: "null"}")
                        is UDM.Object -> throw RuntimeError("first() requires an array argument, got object")
                        else -> throw RuntimeError("first() requires an array argument, got ${udm.javaClass.simpleName}")
                    }
                }
                is RuntimeValue.StringValue -> throw RuntimeError("first() requires an array argument, got string")
                is RuntimeValue.NumberValue -> throw RuntimeError("first() requires an array argument, got number")
                is RuntimeValue.BooleanValue -> throw RuntimeError("first() requires an array argument, got boolean")
                is RuntimeValue.ObjectValue -> throw RuntimeError("first() requires an array argument, got object")
                is RuntimeValue.NullValue -> throw RuntimeError("first() requires an array argument, got null")
                else -> throw RuntimeError("first() requires an array argument, got ${arg.javaClass.simpleName}")
            }
            
            if (arr.isEmpty()) {
                throw RuntimeError("first() called on empty array")
            }
            
            arr.firstOrNull() ?: RuntimeValue.NullValue
        }
        
        registerFunction(env, "last") { args ->
            val arg = args[0]
            val arr = when (arg) {
                is RuntimeValue.ArrayValue -> arg.elements
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Array -> udm.elements.map { RuntimeValue.UDMValue(it) }
                        is UDM.Scalar -> throw RuntimeError("last() requires an array argument, got ${udm.value?.javaClass?.simpleName ?: "null"}")
                        is UDM.Object -> throw RuntimeError("last() requires an array argument, got object")
                        else -> throw RuntimeError("last() requires an array argument, got ${udm.javaClass.simpleName}")
                    }
                }
                is RuntimeValue.StringValue -> throw RuntimeError("last() requires an array argument, got string")
                is RuntimeValue.NumberValue -> throw RuntimeError("last() requires an array argument, got number")
                is RuntimeValue.BooleanValue -> throw RuntimeError("last() requires an array argument, got boolean")
                is RuntimeValue.ObjectValue -> throw RuntimeError("last() requires an array argument, got object")
                is RuntimeValue.NullValue -> throw RuntimeError("last() requires an array argument, got null")
                else -> throw RuntimeError("last() requires an array argument, got ${arg.javaClass.simpleName}")
            }
            
            if (arr.isEmpty()) {
                throw RuntimeError("last() called on empty array")
            }
            
            arr.lastOrNull() ?: RuntimeValue.NullValue
        }
        
        // Math functions
        registerFunction(env, "abs") { args ->
            val num = extractNumber(args[0], "abs() requires number argument")
            RuntimeValue.NumberValue(abs(num))
        }
        
        registerFunction(env, "round") { args ->
            val num = extractNumber(args[0], "round() requires number argument")
            RuntimeValue.NumberValue(round(num))
        }
        
        registerFunction(env, "ceil") { args ->
            val num = extractNumber(args[0], "ceil() requires number argument")
            RuntimeValue.NumberValue(ceil(num))
        }
        
        registerFunction(env, "floor") { args ->
            val num = extractNumber(args[0], "floor() requires number argument")
            RuntimeValue.NumberValue(floor(num))
        }
        
        registerFunction(env, "min") { args ->
            val a = extractNumber(args[0], "min() requires number arguments")
            val b = extractNumber(args[1], "min() requires number arguments")
            RuntimeValue.NumberValue(min(a, b))
        }
        
        registerFunction(env, "max") { args ->
            val a = extractNumber(args[0], "max() requires number arguments")
            val b = extractNumber(args[1], "max() requires number arguments")
            RuntimeValue.NumberValue(max(a, b))
        }
        
        registerFunction(env, "pow") { args ->
            val base = extractNumber(args[0], "pow() requires number arguments")
            val exponent = extractNumber(args[1], "pow() requires number arguments")
            RuntimeValue.NumberValue(base.pow(exponent))
        }
        
        registerFunction(env, "sqrt") { args ->
            val num = extractNumber(args[0], "sqrt() requires number argument")
            RuntimeValue.NumberValue(sqrt(num))
        }
        
        // Array functions with lambdas
        registerFunction(env, "map") { args ->
            val arr = when (val arg = args[0]) {
                is RuntimeValue.ArrayValue -> arg.elements
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Array -> udm.elements.map { RuntimeValue.UDMValue(it) }
                        else -> throw RuntimeError("map() requires array as first argument")
                    }
                }
                else -> throw RuntimeError("map() requires array as first argument")
            }
            val lambda = args[1] as? RuntimeValue.FunctionValue
                ?: throw RuntimeError("map() requires function as second argument")
            
            val results = arr.map { element ->
                // Create environment for lambda execution
                val lambdaEnv = lambda.closure.createChild()
                if (lambda.parameters.isNotEmpty()) {
                    lambdaEnv.define(lambda.parameters[0], element)
                }
                // Execute lambda body
                val interpreter = Interpreter()
                interpreter.evaluate(lambda.body, lambdaEnv)
            }
            RuntimeValue.ArrayValue(results)
        }
        
        // Generic filter function - works on arrays, objects, and strings
        registerFunction(env, "filter") { args ->
            if (args.size < 2) {
                throw RuntimeError("filter() requires 2 arguments")
            }
            
            val value = args[0]
            val predicate = args[1] as? RuntimeValue.FunctionValue
                ?: throw RuntimeError("filter() requires function as second argument")

            when (value) {
                is RuntimeValue.ArrayValue -> {
                    val filteredElements = value.elements.filter { element ->
                        val lambdaEnv = predicate.closure.createChild()
                        if (predicate.parameters.isNotEmpty()) {
                            lambdaEnv.define(predicate.parameters[0], element)
                        }
                        val interpreter = Interpreter()
                        val result = interpreter.evaluate(predicate.body, lambdaEnv)
                        result.isTruthy()
                    }
                    RuntimeValue.ArrayValue(filteredElements)
                }
                is RuntimeValue.UDMValue -> {
                    when (val udm = value.udm) {
                        is UDM.Array -> {
                            val filteredElements = udm.elements.filter { element ->
                                val lambdaEnv = predicate.closure.createChild()
                                if (predicate.parameters.isNotEmpty()) {
                                    lambdaEnv.define(predicate.parameters[0], RuntimeValue.UDMValue(element))
                                }
                                val interpreter = Interpreter()
                                val result = interpreter.evaluate(predicate.body, lambdaEnv)
                                result.isTruthy()
                            }
                            RuntimeValue.UDMValue(UDM.Array(filteredElements))
                        }
                        is UDM.Object -> {
                            val filteredProperties = udm.properties.filter { (key, objValue) ->
                                val lambdaEnv = predicate.closure.createChild()
                                if (predicate.parameters.size >= 2) {
                                    lambdaEnv.define(predicate.parameters[0], RuntimeValue.StringValue(key))
                                    lambdaEnv.define(predicate.parameters[1], RuntimeValue.UDMValue(objValue))
                                } else if (predicate.parameters.isNotEmpty()) {
                                    lambdaEnv.define(predicate.parameters[0], RuntimeValue.UDMValue(objValue))
                                }
                                val interpreter = Interpreter()
                                val result = interpreter.evaluate(predicate.body, lambdaEnv)
                                result.isTruthy()
                            }
                            RuntimeValue.UDMValue(UDM.Object(filteredProperties, udm.attributes))
                        }
                        is UDM.Scalar -> {
                            val scalarValue = udm.value
                            if (scalarValue is String) {
                                val filteredChars = scalarValue.filter { char ->
                                    val lambdaEnv = predicate.closure.createChild()
                                    if (predicate.parameters.isNotEmpty()) {
                                        lambdaEnv.define(predicate.parameters[0], RuntimeValue.StringValue(char.toString()))
                                    }
                                    val interpreter = Interpreter()
                                    val result = interpreter.evaluate(predicate.body, lambdaEnv)
                                    result.isTruthy()
                                }
                                RuntimeValue.StringValue(filteredChars)
                            } else {
                                throw RuntimeError("filter() on scalars only supports strings")
                            }
                        }
                        else -> throw RuntimeError("filter() first argument must be an array, object, or string")
                    }
                }
                is RuntimeValue.StringValue -> {
                    val filteredChars = value.value.filter { char ->
                        val lambdaEnv = predicate.closure.createChild()
                        if (predicate.parameters.isNotEmpty()) {
                            lambdaEnv.define(predicate.parameters[0], RuntimeValue.StringValue(char.toString()))
                        }
                        val interpreter = Interpreter()
                        val result = interpreter.evaluate(predicate.body, lambdaEnv)
                        result.isTruthy()
                    }
                    RuntimeValue.StringValue(filteredChars)
                }
                is RuntimeValue.ObjectValue -> {
                    val filteredProperties = value.properties.filter { (key, objValue) ->
                        val lambdaEnv = predicate.closure.createChild()
                        if (predicate.parameters.size >= 2) {
                            lambdaEnv.define(predicate.parameters[0], RuntimeValue.StringValue(key))
                            lambdaEnv.define(predicate.parameters[1], objValue)
                        } else if (predicate.parameters.isNotEmpty()) {
                            lambdaEnv.define(predicate.parameters[0], objValue)
                        }
                        val interpreter = Interpreter()
                        val result = interpreter.evaluate(predicate.body, lambdaEnv)
                        result.isTruthy()
                    }
                    RuntimeValue.ObjectValue(filteredProperties)
                }
                else -> throw RuntimeError("filter() first argument must be an array, object, or string")
            }
        }

        // find function - returns first element matching predicate
        registerFunction(env, "find") { args ->
            if (args.size < 2) {
                throw RuntimeError("find() requires 2 arguments")
            }

            val array = when (val arg = args[0]) {
                is RuntimeValue.ArrayValue -> arg.elements
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Array -> udm.elements.map { RuntimeValue.UDMValue(it) }
                        else -> throw RuntimeError("find() requires array as first argument")
                    }
                }
                else -> throw RuntimeError("find() requires array as first argument")
            }

            val predicate = args[1] as? RuntimeValue.FunctionValue
                ?: throw RuntimeError("find() requires function as second argument")

            val found = array.firstOrNull { element ->
                val lambdaEnv = predicate.closure.createChild()
                if (predicate.parameters.isNotEmpty()) {
                    lambdaEnv.define(predicate.parameters[0], element)
                }
                val interpreter = Interpreter()
                val result = interpreter.evaluate(predicate.body, lambdaEnv)
                result.isTruthy()
            }

            found ?: RuntimeValue.NullValue
        }

        // findIndex function - returns index of first element matching predicate
        registerFunction(env, "findIndex") { args ->
            if (args.size < 2) {
                throw RuntimeError("findIndex() requires 2 arguments")
            }

            val array = when (val arg = args[0]) {
                is RuntimeValue.ArrayValue -> arg.elements
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Array -> udm.elements.map { RuntimeValue.UDMValue(it) }
                        else -> throw RuntimeError("findIndex() requires array as first argument")
                    }
                }
                else -> throw RuntimeError("findIndex() requires array as first argument")
            }

            val predicate = args[1] as? RuntimeValue.FunctionValue
                ?: throw RuntimeError("findIndex() requires function as second argument")

            val index = array.indexOfFirst { element ->
                val lambdaEnv = predicate.closure.createChild()
                if (predicate.parameters.isNotEmpty()) {
                    lambdaEnv.define(predicate.parameters[0], element)
                }
                val interpreter = Interpreter()
                val result = interpreter.evaluate(predicate.body, lambdaEnv)
                result.isTruthy()
            }

            if (index >= 0) {
                RuntimeValue.NumberValue(index.toDouble())
            } else {
                RuntimeValue.NumberValue(-1.0)
            }
        }

        registerFunction(env, "reduce") { args ->
            val arr = when (val arg = args[0]) {
                is RuntimeValue.ArrayValue -> arg.elements
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Array -> udm.elements.map { RuntimeValue.UDMValue(it) }
                        else -> throw RuntimeError("reduce() requires array as first argument")
                    }
                }
                else -> throw RuntimeError("reduce() requires array as first argument")
            }
            val lambda = args[1] as? RuntimeValue.FunctionValue
                ?: throw RuntimeError("reduce() requires function as second argument")
            
            if (arr.isEmpty()) {
                return@registerFunction args.getOrNull(2) ?: RuntimeValue.NullValue
            }
            
            val hasInitial = args.size > 2
            var accumulator = if (hasInitial) args[2] else arr[0]
            val startIndex = if (hasInitial) 0 else 1
            
            for (i in startIndex until arr.size) {
                val element = arr[i]
                val lambdaEnv = lambda.closure.createChild()
                if (lambda.parameters.size >= 2) {
                    lambdaEnv.define(lambda.parameters[0], accumulator)
                    lambdaEnv.define(lambda.parameters[1], element)
                }
                val interpreter = Interpreter()
                accumulator = interpreter.evaluate(lambda.body, lambdaEnv)
            }
            accumulator
        }
        
        // Conversion functions
        registerFunction(env, "toString") { args ->
            val value = args[0]
            val str = when (value) {
                is RuntimeValue.StringValue -> value.value
                is RuntimeValue.NumberValue -> {
                    if (value.value % 1.0 == 0.0) {
                        value.value.toInt().toString()
                    } else {
                        value.value.toString()
                    }
                }
                is RuntimeValue.BooleanValue -> value.value.toString()
                is RuntimeValue.NullValue -> "null"
                is RuntimeValue.UDMValue -> {
                    when (val udm = value.udm) {
                        is UDM.Scalar -> when (val scalarValue = udm.value) {
                            is Number -> {
                                val doubleValue = scalarValue.toDouble()
                                if (doubleValue % 1.0 == 0.0) {
                                    doubleValue.toInt().toString()
                                } else {
                                    doubleValue.toString()
                                }
                            }
                            else -> scalarValue.toString()
                        }
                        else -> value.toString()
                    }
                }
                else -> value.toString()
            }
            RuntimeValue.StringValue(str)
        }
        
        registerFunction(env, "typeOf") { args ->
            val value = args[0]
            val typeName = when (value) {
                is RuntimeValue.StringValue -> "string"
                is RuntimeValue.NumberValue -> "number"
                is RuntimeValue.BooleanValue -> "boolean"
                is RuntimeValue.NullValue -> "null"
                is RuntimeValue.ArrayValue -> "array"
                is RuntimeValue.ObjectValue -> "object"
                is RuntimeValue.FunctionValue -> "function"
                is RuntimeValue.UDMValue -> {
                    when (val udm = value.udm) {
                        is UDM.Scalar -> when (udm.value) {
                            is String -> "string"
                            is Number -> "number"
                            is Boolean -> "boolean"
                            else -> "unknown"
                        }
                        is UDM.Array -> "array"
                        is UDM.Object -> "object"
                        else -> "unknown"
                    }
                }
            }
            RuntimeValue.StringValue(typeName)
        }
        
        registerFunction(env, "toNumber") { args ->
            val arg = args[0]
            val str = when (arg) {
                is RuntimeValue.StringValue -> arg.value
                is RuntimeValue.NumberValue -> return@registerFunction arg // Already a number
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Scalar -> {
                            when (val value = udm.value) {
                                is String -> value
                                is Number -> return@registerFunction RuntimeValue.NumberValue(value.toDouble())
                                else -> throw RuntimeError("toNumber() requires string or number argument, got ${value?.javaClass?.simpleName ?: "null"}")
                            }
                        }
                        is UDM.Array -> throw RuntimeError("toNumber() requires string or number argument, got array")
                        is UDM.Object -> throw RuntimeError("toNumber() requires string or number argument, got object")
                        else -> throw RuntimeError("toNumber() requires string or number argument, got ${udm.javaClass.simpleName}")
                    }
                }
                is RuntimeValue.BooleanValue -> throw RuntimeError("toNumber() requires string or number argument, got boolean")
                is RuntimeValue.ArrayValue -> throw RuntimeError("toNumber() requires string or number argument, got array")
                is RuntimeValue.ObjectValue -> throw RuntimeError("toNumber() requires string or number argument, got object")
                is RuntimeValue.NullValue -> throw RuntimeError("toNumber() requires string or number argument, got null")
                else -> throw RuntimeError("toNumber() requires string or number argument, got ${arg.javaClass.simpleName}")
            }
            try {
                RuntimeValue.NumberValue(str.toDouble())
            } catch (e: NumberFormatException) {
                throw RuntimeError("Cannot convert '$str' to number")
            }
        }
        
        // Date/time functions
        registerFunction(env, "now") { args ->
            RuntimeValue.StringValue(java.time.Instant.now().toString())
        }
        
        // Crypto functions (simplified implementations)
        registerFunction(env, "sha256") { args ->
            val str = when (val arg = args[0]) {
                is RuntimeValue.StringValue -> arg.value
                is RuntimeValue.UDMValue -> {
                    when (val udm = arg.udm) {
                        is UDM.Scalar -> udm.value.toString()
                        else -> throw RuntimeError("sha256() requires string argument")
                    }
                }
                else -> throw RuntimeError("sha256() requires string argument")
            }
            try {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(str.toByteArray())
                val hexString = hash.joinToString("") { "%02x".format(it) }
                RuntimeValue.StringValue(hexString)
            } catch (e: Exception) {
                throw RuntimeError("Error computing SHA-256: ${e.message}")
            }
        }
        
        // Geospatial functions (using Haversine formula)
        registerFunction(env, "distance") { args ->
            val lat1 = extractNumber(args[0], "distance() requires number arguments")
            val lon1 = extractNumber(args[1], "distance() requires number arguments")
            val lat2 = extractNumber(args[2], "distance() requires number arguments")
            val lon2 = extractNumber(args[3], "distance() requires number arguments")
            
            val earthRadius = 6371.0 // Earth radius in kilometers
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val distance = earthRadius * c
            
            RuntimeValue.NumberValue(distance)
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
