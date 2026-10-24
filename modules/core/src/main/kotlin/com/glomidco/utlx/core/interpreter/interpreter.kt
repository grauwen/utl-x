package com.glomidco.utlx.core.interpreter

import mu.KotlinLogging
import com.glomidco.utlx.core.ast.*
import com.glomidco.utlx.core.udm.UDM
import kotlin.math.*

private val logger = KotlinLogging.logger {}

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
        is UDMValue -> {
            // Handle UDM values (from input data)
            when (val udm = this.udm) {
                is UDM.Scalar -> {
                    when (val scalarValue = udm.value) {
                        null -> false
                        is Boolean -> scalarValue
                        is Number -> scalarValue.toDouble() != 0.0
                        is String -> scalarValue.isNotEmpty()
                        else -> true
                    }
                }
                else -> true // Arrays and Objects are truthy
            }
        }
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
class Environment(
    private val parent: Environment? = null,
    private val inputMetadata: Map<String, InterpreterErrorEnhancer.InputMetadata>? = null
) {
    private val bindings = mutableMapOf<String, RuntimeValue>()

    fun define(name: String, value: RuntimeValue) {
        bindings[name] = value
    }

    fun get(name: String): RuntimeValue {
        return bindings[name] ?: parent?.get(name)
            ?: throw InterpreterErrorEnhancer.enhance(
                InterpreterErrorEnhancer.ErrorContext(
                    error = RuntimeError("Undefined variable: $name"),
                    node = null,
                    env = this,
                    program = null,
                    source = null,
                    inputMetadata = inputMetadata ?: parent?.inputMetadata,
                    currentFunction = null
                )
            )
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

    fun createChild(): Environment = Environment(this, inputMetadata)
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
class Interpreter(
    /**
     * The highest `%utlx` language version this engine instance will accept (the deployment "mode").
     * Defaults to the latest the build understands (accept 1.0 and 1.1). Construct with a lower value
     * (e.g. "1.0") for a 1.0-only ("UTLXe") deployment that rejects `%utlx 1.1`. See [LanguageVersions].
     */
    private val maxLanguageVersion: String = com.glomidco.utlx.core.semantic.LanguageVersions.LATEST
) {
    private val globalEnv = Environment()
    private val stdlib = StandardLibraryImpl()
    
    init {
        // Register standard library functions. Pass `this` as the owner so the higher-order
        // builtins (map/filter/reduce/find/findIndex) evaluate their lambda bodies on THIS
        // interpreter — which holds the stdlib lookup (B25 Part 2).
        stdlib.registerAll(globalEnv, this)
    }

    /**
     * Register an external stdlib function that operates on UDM values.
     * Called by CLI/engine layers to register all stdlib functions at startup,
     * avoiding reflective loading at runtime (B19 — GraalVM native-image compatibility).
     */
    /**
     * B19: Set the stdlib lookup map for lazy registration.
     * Functions are registered on first use, not at startup — zero startup cost.
     * No reflection needed — the lookup map contains direct function references.
     */
    private var stdlibLookup: Map<String, (List<UDM>) -> UDM> = emptyMap()

    fun setStdlibLookup(lookup: Map<String, (List<UDM>) -> UDM>, eager: Boolean = false) {
        stdlibLookup = lookup
        if (eager) {
            // Pre-register all functions now — eliminates first-call latency.
            // Use for long-running processes (utlxe) where startup cost is acceptable.
            lookup.keys.forEach { name -> tryRegisterFromLookup(name) }
        }
    }

    // ── F21 language-version gate (by NAMESPACE, not `since`) ──
    // A `validate.*` call is a `%utlx 1.1` LANGUAGE feature; a script declaring an older version may not
    // use it. Keys on the NAMESPACE via the shared `LanguageNamespaces` policy (NOT `@UTLXFunction(since)`,
    // which is an informational "added-in release" tag already set >1.0 on 1.0-valid functions like
    // crypto@1.2). Enforced at stdlib resolution, so it fires only when the base really is a namespace
    // (the variable path resolves first), never for a local variable named like a namespace.
    private var declaredLanguageVersion: String = "1.0"

    /** VB05: the program most recently cleared by the static namespace gate (identity compare). */
    private var gatedProgram: com.glomidco.utlx.core.ast.Program? = null

    private fun enforceLanguageVersion(name: String, location: Location) {
        if (com.glomidco.utlx.core.semantic.LanguageNamespaces.isAvailableAt(name, declaredLanguageVersion)) return
        val required = com.glomidco.utlx.core.semantic.LanguageNamespaces.minVersionFor(name)
        val ns = com.glomidco.utlx.core.semantic.LanguageNamespaces.namespaceOf(name)
        throw RuntimeError(
            "$name requires %utlx $required (script declares %utlx $declaredLanguageVersion); " +
            "the $ns.* namespace is a $required language feature.", location)
    }

    // ── F21 version-header handling (the engine "mode") ──
    // Reject a header the engine can't honour, BEFORE running the body: (1) a version this build doesn't
    // understand at all (typo / future version), and (2) a version above this engine's configured mode
    // (e.g. `%utlx 1.1` on a 1.0-only / UTLXe deployment). A blank version is treated as the baseline.
    private fun enforceSupportedVersion(version: String, location: Location) {
        val v = com.glomidco.utlx.core.semantic.LanguageVersions.normalize(version)
        if (v.isEmpty()) return  // no/blank header → treated as baseline 1.0 (back-compat)

        if (!com.glomidco.utlx.core.semantic.LanguageVersions.isSupported(v)) {
            throw RuntimeError(
                "unsupported %utlx version '$v'; this engine understands " +
                com.glomidco.utlx.core.semantic.LanguageVersions.SUPPORTED.joinToString(", "), location)
        }
        if (com.glomidco.utlx.core.semantic.LanguageNamespaces.compareVersions(v, maxLanguageVersion) > 0) {
            throw RuntimeError(
                "%utlx $v is not enabled in this engine (configured for %utlx $maxLanguageVersion, " +
                "1.0-only mode); run a 1.1-mode engine to execute this script.", location)
        }
    }

    /**
     * Try to find and register a stdlib function from the lookup map (no reflection).
     * Called lazily on first use of each function — O(1) HashMap lookup.
     */
    private fun tryRegisterFromLookup(name: String): Boolean {
        val execute = stdlibLookup[name] ?: return false
        // Don't override functions already registered by StandardLibraryImpl
        if (StandardLibraryImpl.nativeFunctions.containsKey(name)) return true

        val wrapper: (List<RuntimeValue>) -> RuntimeValue = { args ->
            val udmArgs = args.map { runtimeValueToUDM(it) }
            val result = execute(udmArgs)
            udmToRuntimeValue(result)
        }
        StandardLibraryImpl.nativeFunctions[name] = wrapper
        val functionValue = RuntimeValue.FunctionValue(
            parameters = listOf(),
            body = Expression.NullLiteral(Location(0, 0)),
            closure = globalEnv
        )
        globalEnv.define(name, functionValue)
        return true
    }

    /**
     * Execute a program with input data (single input - backward compatible)
     */
    fun execute(program: Program, inputData: UDM): RuntimeValue {
        return execute(program, mapOf("input" to inputData))
    }

    /**
     * Execute a program with multiple named inputs
     */
    fun execute(program: Program, namedInputs: Map<String, UDM>): RuntimeValue {
        logger.debug { "Starting execution with ${namedInputs.size} input(s): ${namedInputs.keys.joinToString()}" }

        // F21: capture the declared %utlx version for the namespace language-version gate,
        // then enforce the engine mode (reject unsupported / above-mode versions up front).
        declaredLanguageVersion = program.header.version
        enforceSupportedVersion(program.header.version, program.header.location)

        // VB05: enforce the namespace gate STATICALLY, before any data is touched. The runtime gate
        // in tryLoadStdlibFunction only fires when a namespaced call is actually reached, so a
        // %utlx 1.0 script could carry validate.* calls on an untaken branch and run fine — passing
        // tests and failing in production on the first payload to reach them. A version gate is a
        // claim about the PROGRAM, so it is decided once here; the runtime gate stays as a backstop.
        // Memoised by program identity: execute() runs per payload, and the gate is a property of
        // the program, not of the data — so a batch of N records must not pay for N walks.
        if (gatedProgram !== program) {
            com.glomidco.utlx.core.semantic.LanguageVersionStaticGate.check(program, declaredLanguageVersion)
            gatedProgram = program
        }

        // Extract metadata from all inputs for smart error enhancement
        val inputFormats = program.header.inputs.associate { (name, spec) ->
            name to (spec?.type?.name?.lowercase() ?: "unknown")
        }
        val inputMetadata = InputMetadataExtractor.extractAll(namedInputs, inputFormats)

        logger.trace { "Extracted metadata for ${inputMetadata.size} input(s)" }
        inputMetadata.forEach { (name, metadata) ->
            logger.trace { "  $name: ${metadata.fields?.size ?: 0} fields, ${metadata.recordCount ?: 0} records" }
        }

        // Create environment with metadata for error enhancement
        val env = Environment(globalEnv, inputMetadata)

        // Bind all named inputs to environment
        namedInputs.forEach { (name, data) ->
            logger.trace { "Binding input '$name' to environment" }
            env.define(name, RuntimeValue.UDMValue(data))
        }

        // Backward compatibility: if no "input" was provided, use first input
        if (!namedInputs.containsKey("input") && namedInputs.isNotEmpty()) {
            val firstInput = namedInputs.values.first()
            logger.trace { "Binding first input as 'input' for backward compatibility" }
            env.define("input", RuntimeValue.UDMValue(firstInput))
        }

        // Evaluate body
        logger.debug { "Evaluating transformation body" }
        val result = evaluate(program.body, env)
        logger.debug { "Execution completed, result type: ${result::class.simpleName}" }
        return result
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
                // Create a new environment for let bindings
                val objectEnv = if (expr.letBindings.isNotEmpty()) {
                    val childEnv = env.createChild()
                    // Define all let bindings in the new scope
                    for (binding in expr.letBindings) {
                        val value = evaluate(binding.value, childEnv)
                        childEnv.define(binding.name, value)
                    }
                    childEnv
                } else {
                    env
                }

                val properties = mutableMapOf<String, RuntimeValue>()
                val attributes = mutableMapOf<String, String>()

                // Evaluate properties in the environment with let bindings
                for (prop in expr.properties) {
                    if (prop.isSpread) {
                        // Handle spread property: ...obj
                        val spreadValue = evaluate(prop.value, objectEnv)
                        when (spreadValue) {
                            is RuntimeValue.ObjectValue -> {
                                // Merge all properties from the spread object
                                properties.putAll(spreadValue.properties)
                            }
                            is RuntimeValue.UDMValue -> {
                                // If it's a UDM object, extract its properties
                                when (val udm = spreadValue.udm) {
                                    is UDM.Object -> {
                                        // Convert UDM properties to RuntimeValue and merge
                                        udm.properties.forEach { (key, value) ->
                                            properties[key] = RuntimeValue.UDMValue(value)
                                        }
                                    }
                                    else -> {
                                        // Can't spread non-object UDM values - ignore
                                    }
                                }
                            }
                            else -> {
                                // Can't spread non-object values - ignore
                            }
                        }
                        continue
                    }

                    val value = evaluate(prop.value, objectEnv)
                    val key = if (prop.computedKey != null) {
                        // Computed property name: [expr]: value
                        val computedKeyValue = evaluate(prop.computedKey, objectEnv)
                        when (computedKeyValue) {
                            is RuntimeValue.StringValue -> computedKeyValue.value
                            is RuntimeValue.NumberValue -> {
                                val d = computedKeyValue.value
                                if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                            }
                            else -> computedKeyValue.toString()
                        }
                    } else {
                        prop.key ?: error("Non-spread property must have a key")
                    }

                    if (prop.isAttribute) {
                        // Attributes must be strings - extract string value
                        val attrValue = when (value) {
                            is RuntimeValue.StringValue -> value.value
                            is RuntimeValue.NumberValue -> {
                                // Format integers without decimal point, floats with decimal
                                val d = value.value
                                if (d == d.toLong().toDouble()) {
                                    d.toLong().toString()  // Integer: "42"
                                } else {
                                    d.toString()            // Float: "3.14"
                                }
                            }
                            is RuntimeValue.BooleanValue -> value.value.toString()
                            is RuntimeValue.NullValue -> ""
                            is RuntimeValue.UDMValue -> {
                                // Extract scalar value from UDM
                                when (val udm = value.udm) {
                                    is UDM.Scalar -> {
                                        // Handle different scalar types based on actual type, not coercion
                                        when (val scalarValue = udm.value) {
                                            is Boolean -> scalarValue.toString()  // "true" or "false"
                                            is Number -> {
                                                // Format integers without decimal point
                                                val d = scalarValue.toDouble()
                                                if (d == d.toLong().toDouble()) {
                                                    d.toLong().toString()
                                                } else {
                                                    d.toString()
                                                }
                                            }
                                            null -> ""
                                            else -> scalarValue.toString()  // String or other
                                        }
                                    }
                                    else -> udm.toString()
                                }
                            }
                            else -> value.toString()
                        }
                        attributes[key] = attrValue
                    } else {
                        properties[key] = value
                    }
                }

                // If there are attributes, create a UDM.Object; otherwise use RuntimeValue.ObjectValue
                if (attributes.isNotEmpty()) {
                    val udmProperties = properties.mapValues { it.value.toUDM() }
                    RuntimeValue.UDMValue(UDM.Object(udmProperties, attributes))
                } else {
                    RuntimeValue.ObjectValue(properties)
                }
            }
            
            is Expression.ArrayLiteral -> {
                val elements = mutableListOf<RuntimeValue>()
                for (element in expr.elements) {
                    if (element is Expression.SpreadElement) {
                        // Handle spread element: [...arr]
                        val spreadValue = evaluate(element.expression, env)
                        when (spreadValue) {
                            is RuntimeValue.ArrayValue -> {
                                // Flatten the spread array into the result
                                elements.addAll(spreadValue.elements)
                            }
                            is RuntimeValue.UDMValue -> {
                                // If it's a UDM array, extract its elements
                                when (val udm = spreadValue.udm) {
                                    is UDM.Array -> {
                                        // Convert UDM elements to RuntimeValue and add
                                        udm.elements.forEach { value ->
                                            elements.add(RuntimeValue.UDMValue(value))
                                        }
                                    }
                                    else -> {
                                        // Can't spread non-array UDM values - ignore
                                    }
                                }
                            }
                            else -> {
                                // Can't spread non-array values - ignore
                            }
                        }
                    } else {
                        elements.add(evaluate(element, env))
                    }
                }
                RuntimeValue.ArrayValue(elements)
            }

            is Expression.SpreadElement -> {
                // SpreadElement should only be used within array/object literals
                // If evaluated directly, just evaluate the inner expression
                evaluate(expr.expression, env)
            }
            
            is Expression.MemberAccess -> evaluateMemberAccess(expr, env)

            is Expression.SafeNavigation -> evaluateSafeNavigation(expr, env)

            is Expression.IndexAccess -> evaluateIndexAccess(expr, env)
            
            is Expression.BinaryOp -> evaluateBinaryOp(expr, env)

            is Expression.UnaryOp -> evaluateUnaryOp(expr, env)

            is Expression.Ternary -> {
                val condition = evaluate(expr.condition, env)
                if (condition.isTruthy()) {
                    evaluate(expr.thenExpr, env)
                } else {
                    evaluate(expr.elseExpr, env)
                }
            }

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

            is Expression.Block -> {
                var result: RuntimeValue = RuntimeValue.NullValue
                for (expression in expr.expressions) {
                    result = evaluate(expression, env)
                }
                result
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
                    is Expression.Lambda -> {
                        // Lambda piped directly: value |> (x => expr)
                        // Evaluate the lambda to get a FunctionValue, then call it with the piped value
                        val lambda = expr.target
                        if (lambda.parameters.isEmpty()) {
                            // No parameters - just evaluate the body with $ available
                            evaluate(lambda.body, pipeEnv)
                        } else {
                            // Bind the piped value to the first parameter
                            val lambdaEnv = env.createChild()
                            lambdaEnv.define(lambda.parameters[0].name, sourceValue)
                            evaluate(lambda.body, lambdaEnv)
                        }
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

            is Expression.TryCatch -> evaluateTryCatch(expr, env)

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
                // Handle wildcard selector for regular objects
                if (expr.property == "*") {
                    val children = target.properties.values.toList()
                    RuntimeValue.ArrayValue(children)
                } else {
                    target.properties[expr.property] ?: RuntimeValue.NullValue
                }
            }
            is RuntimeValue.UDMValue -> {
                when (val udm = target.udm) {
                    is UDM.Object -> {
                        // Handle wildcard selector for UDM objects
                        if (expr.property == "*") {
                            if (expr.isAttribute) {
                                // Wildcard attributes: return all attribute values as array
                                val attrValues = udm.attributes.values.map { RuntimeValue.StringValue(it) }
                                RuntimeValue.ArrayValue(attrValues)
                            } else {
                                // Wildcard properties: return all child elements as array
                                // Exclude _text from wildcard results (it's not a child element)
                                val children = udm.properties.filterKeys { it != "_text" }.values.map { child ->
                                    RuntimeValue.UDMValue(unwrapTextNode(child))
                                }
                                RuntimeValue.ArrayValue(children)
                            }
                        } else if (expr.isAttribute) {
                            val attrValue = udm.getAttribute(expr.property)
                            attrValue?.let { RuntimeValue.StringValue(it) } ?: RuntimeValue.NullValue
                        } else if (expr.isMetadata) {
                            // Handle metadata access: $input^schemaType
                            val metadataValue = udm.getMetadata(expr.property)
                            metadataValue?.let { RuntimeValue.StringValue(it) } ?: RuntimeValue.NullValue
                        } else {
                            val propValue = udm.get(expr.property)
                            if (propValue != null) {
                                // Auto-unwrap _text property for XML text nodes
                                val unwrapped = unwrapTextNode(propValue)
                                RuntimeValue.UDMValue(unwrapped)
                            } else {
                                RuntimeValue.NullValue
                            }
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

    /**
     * Evaluate safe navigation operator: target?.property
     * Returns null if target is null, otherwise accesses property
     */
    private fun evaluateSafeNavigation(expr: Expression.SafeNavigation, env: Environment): RuntimeValue {
        val target = evaluate(expr.target, env)

        // If target is null, return null instead of throwing an error
        if (target is RuntimeValue.NullValue) {
            return RuntimeValue.NullValue
        }

        // Otherwise, access the property normally (same logic as MemberAccess)
        return when (target) {
            is RuntimeValue.ObjectValue -> {
                target.properties[expr.property] ?: RuntimeValue.NullValue
            }
            is RuntimeValue.UDMValue -> {
                when (val udm = target.udm) {
                    is UDM.Object -> {
                        val propValue = udm.get(expr.property)
                        if (propValue != null) {
                            // Auto-unwrap _text property for XML text nodes
                            val unwrapped = unwrapTextNode(propValue)
                            RuntimeValue.UDMValue(unwrapped)
                        } else {
                            RuntimeValue.NullValue
                        }
                    }
                    else -> RuntimeValue.NullValue
                }
            }
            else -> RuntimeValue.NullValue
        }
    }

    /**
     * Unwrap XML text nodes that have only a _text property.
     * This makes XML text content directly accessible without needing to access ._text
     */
    private fun unwrapTextNode(udm: UDM): UDM {
        return when (udm) {
            is UDM.Object -> {
                // If object has only _text property and no real attributes (ignoring xmlns declarations), unwrap it
                val nonXmlnsAttributes = udm.attributes.filterKeys { !it.startsWith("xmlns") && it != "xmlns" }
                if (udm.properties.size == 1 && udm.properties.containsKey("_text") && nonXmlnsAttributes.isEmpty()) {
                    udm.properties["_text"]!!
                } else {
                    udm
                }
            }
            else -> udm
        }
    }

    private fun evaluateIndexAccess(expr: Expression.IndexAccess, env: Environment): RuntimeValue {
        val target = evaluate(expr.target, env)
        val rawIndex = evaluate(expr.index, env)

        // B15 fix: unwrap UDM.Scalar values to native RuntimeValue types for indexing
        // When the index comes from a UDM property (e.g., order.orderId), it arrives as
        // RuntimeValue.UDMValue(UDM.Scalar("ORD-001")) — unwrap to RuntimeValue.StringValue("ORD-001")
        val index = when (rawIndex) {
            is RuntimeValue.UDMValue -> when (val udm = rawIndex.udm) {
                is UDM.Scalar -> when (val value = udm.value) {
                    is String -> RuntimeValue.StringValue(value)
                    is Number -> RuntimeValue.NumberValue(value.toDouble())
                    is Boolean -> RuntimeValue.StringValue(value.toString())
                    null -> RuntimeValue.NullValue
                    else -> rawIndex
                }
                else -> rawIndex
            }
            else -> rawIndex
        }

        // Handle both numeric indices (for arrays) and string indices (for object properties)
        return when (index) {
            is RuntimeValue.NumberValue -> {
                // Numeric index - array access
                val indexNum = index.value.toInt()
                when (target) {
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
                            else -> throw RuntimeError("Cannot index non-array UDM type with number", expr.location)
                        }
                    }
                    else -> throw RuntimeError("Cannot index ${target::class.simpleName} with number", expr.location)
                }
            }
            is RuntimeValue.StringValue -> {
                // String index - object property access
                val propertyName = index.value
                when (target) {
                    is RuntimeValue.ObjectValue -> {
                        target.properties[propertyName] ?: RuntimeValue.NullValue
                    }
                    is RuntimeValue.UDMValue -> {
                        when (val udm = target.udm) {
                            is UDM.Object -> {
                                // Check if accessing metadata (starts with ^)
                                if (propertyName.startsWith("^")) {
                                    val metadataKey = propertyName.substring(1) // Remove ^ prefix
                                    val metadataValue = udm.getMetadata(metadataKey) ?: udm.getMetadata(propertyName)
                                    if (metadataValue != null) {
                                        RuntimeValue.StringValue(metadataValue)
                                    } else {
                                        RuntimeValue.NullValue
                                    }
                                }
                                // Check if accessing an attribute (starts with @)
                                else if (propertyName.startsWith("@")) {
                                    val attrName = propertyName.substring(1) // Remove @ prefix
                                    val attrValue = udm.getAttribute(attrName) ?: udm.getAttribute(propertyName)
                                    if (attrValue != null) {
                                        RuntimeValue.StringValue(attrValue)
                                    } else {
                                        RuntimeValue.NullValue
                                    }
                                } else {
                                    // Regular property access
                                    val propValue = udm.get(propertyName)
                                    if (propValue != null) {
                                        RuntimeValue.UDMValue(unwrapTextNode(propValue))
                                    } else {
                                        RuntimeValue.NullValue
                                    }
                                }
                            }
                            else -> throw RuntimeError("Cannot index non-object UDM type with string", expr.location)
                        }
                    }
                    else -> throw RuntimeError("Cannot index ${target::class.simpleName} with string", expr.location)
                }
            }
            else -> throw RuntimeError("Index must be a number or string, got ${index::class.simpleName}", expr.location)
        }
    }
    
    private fun evaluateBinaryOp(expr: Expression.BinaryOp, env: Environment): RuntimeValue {
        // Handle nullish coalescing with short-circuit evaluation
        if (expr.operator == BinaryOperator.NULLISH_COALESCE) {
            val left = evaluate(expr.left, env)
            // Check if left is null (either RuntimeValue.NullValue or UDM.Scalar(null))
            val isNull = when (left) {
                is RuntimeValue.NullValue -> true
                is RuntimeValue.UDMValue -> {
                    left.udm is UDM.Scalar && left.udm.value == null
                }
                else -> false
            }
            return if (isNull) {
                evaluate(expr.right, env)
            } else {
                left
            }
        }

        // B34: logical AND/OR must SHORT-CIRCUIT — the right operand is evaluated only when the
        // left does not already decide the result. Previously both operands were forced by the
        // eager pair below, so a logically dead operand still ran (`false && (1 / 0)` raised
        // Division by zero) and no guard (`x != null && x > 0`) could protect the comparison
        // behind it. Same shape as the NULLISH_COALESCE early return above. The result stays
        // boolean-coerced (`??` owns the default-value idiom); ASTCompiler coerces to match.
        if (expr.operator == BinaryOperator.AND || expr.operator == BinaryOperator.OR) {
            val leftTruthy = evaluate(expr.left, env).isTruthy()
            if (expr.operator == BinaryOperator.AND && !leftTruthy) return RuntimeValue.BooleanValue(false)
            if (expr.operator == BinaryOperator.OR && leftTruthy) return RuntimeValue.BooleanValue(true)
            return RuntimeValue.BooleanValue(evaluate(expr.right, env).isTruthy())
        }

        // For all other operators, evaluate both sides
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

            BinaryOperator.EXPONENT -> {
                val base = extractNumber(left, "Base must be number", expr.location)
                val exponent = extractNumber(right, "Exponent must be number", expr.location)
                RuntimeValue.NumberValue(base.pow(exponent))
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
            
            BinaryOperator.AND, BinaryOperator.OR -> {
                // Unreachable: short-circuited in the early return above (B34).
                throw RuntimeError("Logical ${expr.operator} should have been handled earlier", expr.location)
            }

            BinaryOperator.NULLISH_COALESCE -> {
                // This case should never be reached due to early return above
                throw RuntimeError("Nullish coalesce should have been handled earlier", expr.location)
            }
        }
    }
    
    private fun evaluateUnaryOp(expr: Expression.UnaryOp, env: Environment): RuntimeValue {
        val operand = evaluate(expr.operand, env)

        return when (expr.operator) {
            UnaryOperator.MINUS -> {
                val value = extractNumber(operand, "Unary minus requires number", expr.location)
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
            // Create a child environment for pattern variables
            val caseEnv = env.createChild()

            // Check if pattern matches
            val matches = when (val pattern = case.pattern) {
                is Pattern.Wildcard -> true
                is Pattern.Literal -> valuesEqual(value, literalToRuntimeValue(pattern.value))
                is Pattern.Variable -> {
                    // Bind the matched value to the variable name in the case environment
                    caseEnv.define(pattern.name, value)
                    true
                }
            }

            // If pattern matches, check guard (if present)
            if (matches) {
                val guardPasses = if (case.guard != null) {
                    val guardResult = evaluate(case.guard, caseEnv)
                    guardResult.isTruthy()
                } else {
                    true // No guard means it passes
                }

                if (guardPasses) {
                    return evaluate(case.expression, caseEnv)
                }
            }
        }

        throw RuntimeError("No matching case in match expression", expr.location)
    }

    private fun evaluateTryCatch(expr: Expression.TryCatch, env: Environment): RuntimeValue {
        return try {
            // Try to evaluate the try block
            evaluate(expr.tryBlock, env)
        } catch (e: Exception) {
            // If an error occurs, evaluate the catch block
            val catchEnv = env.createChild()

            // If an error variable is specified, bind the error message to it
            if (expr.errorVariable != null) {
                val errorMessage = e.message ?: "Unknown error"
                catchEnv.define(expr.errorVariable, RuntimeValue.StringValue(errorMessage))
            }

            evaluate(expr.catchBlock, catchEnv)
        }
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
            left is RuntimeValue.UDMValue && right is RuntimeValue.BooleanValue -> {
                val udm = left.udm
                if (udm is UDM.Scalar && udm.value is Boolean) {
                    udm.value == right.value
                } else false
            }
            left is RuntimeValue.BooleanValue && right is RuntimeValue.UDMValue -> {
                val udm = right.udm
                if (udm is UDM.Scalar && udm.value is Boolean) {
                    left.value == udm.value
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
        // Resolve the call's function name for the stdlib fallback below.
        //  - a bare Identifier `fn(...)`            → "fn"
        //  - a MemberAccess on an Identifier base   → "ns.fn"  (NAMESPACED stdlib call, e.g.
        //    `validate.required(...)` → "validate.required"). F21: `validate.*` resolution.
        // This name is only *consumed* in the "Undefined variable" fallback (catch below). A real
        // member access on a *defined* variable evaluates successfully first, so this never hijacks it —
        // the namespace path only triggers when the base identifier is undefined (a genuine namespace).
        // Attribute/metadata accesses (@attr / ^meta) are excluded — those are never function namespaces.
        val functionName = when (val fn = expr.function) {
            is Expression.Identifier -> fn.name
            is Expression.MemberAccess ->
                (fn.target as? Expression.Identifier)
                    ?.takeIf { !fn.isAttribute && !fn.isMetadata }
                    ?.let { "${it.name}.${fn.property}" }
            else -> null
        }
        
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
     * Invoke a rule predicate lambda (`check` / `when`) with the payload. Returns the boolean it
     * produced, or null if it produced something that is not a boolean. Throws whatever the body
     * throws — callers isolate that per rule (VB01/VB02).
     */
    private fun invokeRulePredicate(fn: RuntimeValue.FunctionValue, payload: RuntimeValue): Boolean? {
        val childEnv = fn.closure.createChild()
        if (fn.parameters.isNotEmpty()) childEnv.define(fn.parameters[0], payload)
        val result = evaluate(fn.body, childEnv)
        return (result as? RuntimeValue.BooleanValue)?.value
    }

    /**
     * VB01/VB02: evaluate `validate.assert(payload, rules)` with LAZY per-rule lambda checks.
     *
     * Each rule is
     * `{ rule?, check: (p) => <bool>, when?: (p) => <bool>, message?, field?/fields?, severity? }`.
     *
     * **VB01 — `check`.** Invoked with the payload under its own try/catch:
     *   - returns true  → the rule passes
     *   - returns false → the rule fails (message/field/severity as given)
     *   - THROWS        → the check could not be evaluated (e.g. missing/mistyped field) → a reported
     *                     error-severity issue, NOT a process-aborting crash
     *
     * **VB02 — `when`.** The applicability guard, same lambda contract, resolved FIRST:
     *   - returns true  → the rule applies; `check` is then evaluated
     *   - returns false → the rule is skipped and counts as passed; `check` is NEVER evaluated
     *   - THROWS, or does not return a boolean → the guard could not be evaluated → its own
     *     error-severity issue. `when` is forced true so the rule FAILS: a broken guard must never
     *     silently suppress a rule (fail-closed).
     *
     * After resolving `when`/`check` to booleans, the (now UDM-safe) rules are handed to the
     * UNCHANGED stdlib `validate.assert`, which owns the ValidationResult aggregation and already
     * skips a rule whose `when` is false. All rules are always evaluated (no short-circuit). A rule
     * with neither a lambda `check` nor a lambda `when` is passed through untouched (legacy form).
     */
    private fun evaluateValidateAssert(arguments: List<Expression>, env: Environment, location: Location): RuntimeValue {
        if (arguments.size < 2) {
            throw RuntimeError("validate.assert expects (payload, rules), got ${arguments.size} argument(s)", location)
        }
        val payload = evaluate(arguments[0], env)
        val rulesValue = evaluate(arguments[1], env)
        val rules = (rulesValue as? RuntimeValue.ArrayValue)?.elements
            ?: throw RuntimeError("validate.assert: the rules argument must be an array", location)

        val resolvedRules = rules.map { rule ->
            if (rule !is RuntimeValue.ObjectValue) return@map rule  // defer to stdlib for odd shapes
            val checkVal = rule.properties["check"]
            val whenVal = rule.properties["when"]
            // Legacy form — nothing deferred to resolve. Leave as-is.
            if (checkVal !is RuntimeValue.FunctionValue && whenVal !is RuntimeValue.FunctionValue) return@map rule

            val props = rule.properties.toMutableMap()
            val ruleName = (props["rule"] as? RuntimeValue.StringValue)?.value

            // ── VB02: resolve the `when` guard first; a false guard skips the rule entirely ──
            if (whenVal is RuntimeValue.FunctionValue) {
                try {
                    val applies = invokeRulePredicate(whenVal, payload)
                        ?: throw RuntimeError("guard did not return a boolean", location)
                    props["when"] = RuntimeValue.BooleanValue(applies)
                    if (!applies) {
                        // Not applicable: the stdlib skips the rule and counts it as passed without
                        // ever reading `check`, so resolve `check` to a placeholder for UDM-safety.
                        props["check"] = RuntimeValue.BooleanValue(true)
                        return@map RuntimeValue.ObjectValue(props)
                    }
                } catch (e: Exception) {
                    // Non-evaluable guard → its own error issue. `when` is forced TRUE so the rule
                    // fails: a broken guard must never silently suppress a rule (fail-closed).
                    props["when"] = RuntimeValue.BooleanValue(true)
                    props["check"] = RuntimeValue.BooleanValue(false)
                    props["severity"] = RuntimeValue.StringValue("error")
                    props["message"] = RuntimeValue.StringValue(
                        "guard ${ruleName?.let { "'$it' " } ?: ""}could not be evaluated: ${e.message ?: e.javaClass.simpleName}"
                    )
                    return@map RuntimeValue.ObjectValue(props)
                }
            }

            // ── VB01: resolve the `check` ──
            if (checkVal is RuntimeValue.FunctionValue) {
                try {
                    props["check"] = RuntimeValue.BooleanValue(invokeRulePredicate(checkVal, payload) == true)
                } catch (e: Exception) {
                    // Non-evaluable check → reported error issue (fail-closed), never an aborting throw.
                    props["check"] = RuntimeValue.BooleanValue(false)
                    props["severity"] = RuntimeValue.StringValue("error")
                    props["message"] = RuntimeValue.StringValue(
                        "check ${ruleName?.let { "'$it' " } ?: ""}could not be evaluated: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
            RuntimeValue.ObjectValue(props)
        }

        // Dispatch to the unchanged stdlib validate.assert (now sees only boolean checks → UDM-safe).
        tryRegisterFromLookup("validate.assert")
        val nativeImpl = StandardLibraryImpl.nativeFunctions["validate.assert"]
            ?: throw RuntimeError("validate.assert is not available (stdlib not loaded)", location)
        return nativeImpl(listOf(payload, RuntimeValue.ArrayValue(resolvedRules)))
    }

    /**
     * Dynamic function loader - attempts to load stdlib functions on demand
     */
    private fun tryLoadStdlibFunction(functionName: String, arguments: List<Expression>, env: Environment, location: Location): RuntimeValue {
        // F21: language-version gate — reject a namespaced call whose namespace needs a newer %utlx than
        // the script declares (e.g. validate.* under %utlx 1.0). Fires before resolution.
        enforceLanguageVersion(functionName, location)

        // VB01: `validate.assert` is higher-order — each rule's `check` is a LAMBDA evaluated lazily here
        // (not a pre-evaluated boolean in the UDM-level stdlib). Deferring the check into the interpreter
        // lets us invoke it per-rule under try/catch, so a check that cannot evaluate (missing/mistyped
        // field) becomes a reported error issue instead of aborting the whole validation. Handled in :core
        // (not :stdlib) because a lambda is a RuntimeValue.FunctionValue that cannot be converted to UDM;
        // once each check is resolved to a boolean, dispatch flows to the UNCHANGED stdlib validate.assert.
        if (functionName == "validate.assert") {
            return evaluateValidateAssert(arguments, env, location)
        }
        // B19: Try lazy lookup registration first (no reflection, GraalVM safe)
        if (tryRegisterFromLookup(functionName)) {
            // Function now registered — re-evaluate the call
            val args = arguments.map { evaluate(it, env) }
            val nativeImpl = StandardLibraryImpl.nativeFunctions[functionName]
            if (nativeImpl != null) return nativeImpl(args)
        }

        // Try direct function invocation for problematic functions, then fall back to registry
        try {
            return tryDirectFunctionInvocation(functionName, arguments, env, location)
        } catch (e: RuntimeError) {
            // Direct invocation failed, try the registry approach
            try {
                val stdlibClass = Class.forName("com.glomidco.utlx.stdlib.StandardLibrary")
                
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
                // Unwrap InvocationTargetException to get the real cause
                val actualException = if (ex is java.lang.reflect.InvocationTargetException && ex.cause != null) {
                    ex.cause!!
                } else {
                    ex
                }

                // For user-friendly errors, throw them directly with better message
                if (actualException is com.glomidco.utlx.core.FunctionArgumentException ||
                    actualException is IllegalArgumentException) {
                    throw RuntimeError(
                        "Error in function '$functionName': ${actualException.message}",
                        location
                    )
                }

                // For other errors, show debug info
                System.err.println("DEBUG: Failed to execute stdlib function '$functionName':")
                System.err.println("  ${actualException.javaClass.simpleName}: ${actualException.message}")
                actualException.printStackTrace(System.err)
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
                // "now" removed - use stdlib DateFunctions::now which returns UDM.DateTime instead of string
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
    private fun runtimeValueToUDM(value: RuntimeValue): com.glomidco.utlx.core.udm.UDM {
        return when (value) {
            is RuntimeValue.StringValue -> com.glomidco.utlx.core.udm.UDM.Scalar(value.value)
            is RuntimeValue.NumberValue -> com.glomidco.utlx.core.udm.UDM.Scalar(value.value)
            is RuntimeValue.BooleanValue -> com.glomidco.utlx.core.udm.UDM.Scalar(value.value)
            is RuntimeValue.NullValue -> com.glomidco.utlx.core.udm.UDM.Scalar(null)
            is RuntimeValue.ArrayValue -> com.glomidco.utlx.core.udm.UDM.Array(value.elements.map { runtimeValueToUDM(it) })
            is RuntimeValue.ObjectValue -> com.glomidco.utlx.core.udm.UDM.Object(value.properties.mapValues { runtimeValueToUDM(it.value) })
            is RuntimeValue.UDMValue -> value.udm
            is RuntimeValue.FunctionValue -> {
                // Convert RuntimeValue.FunctionValue to UDM.Lambda
                // This enables stdlib functions to receive lambda arguments
                val fnValue = value
                com.glomidco.utlx.core.udm.UDM.Lambda { udmArgs ->
                    // Create lambda environment with arguments
                    val lambdaEnv = fnValue.closure.createChild()
                    for ((param, arg) in fnValue.parameters.zip(udmArgs)) {
                        lambdaEnv.define(param, RuntimeValue.UDMValue(arg))
                    }
                    // Execute lambda body in the context of this interpreter
                    val result = this@Interpreter.evaluate(fnValue.body, lambdaEnv)
                    // Convert result back to UDM
                    runtimeValueToUDM(result)
                }
            }
            else -> throw RuntimeError("Cannot convert ${value::class.simpleName} to UDM for stdlib function call")
        }
    }
    
    /**
     * Convert UDM result back to RuntimeValue
     */
    private fun udmToRuntimeValue(result: Any?): RuntimeValue {
        return when (result) {
            is com.glomidco.utlx.core.udm.UDM.Scalar -> {
                when (val value = result.value) {
                    is String -> RuntimeValue.StringValue(value)
                    is Number -> RuntimeValue.NumberValue(value.toDouble())
                    is Boolean -> RuntimeValue.BooleanValue(value)
                    null -> RuntimeValue.NullValue
                    else -> RuntimeValue.StringValue(value.toString())
                }
            }
            is com.glomidco.utlx.core.udm.UDM.Array -> RuntimeValue.ArrayValue(result.elements.map { udmToRuntimeValue(it) })
            is com.glomidco.utlx.core.udm.UDM.Object -> RuntimeValue.ObjectValue(result.properties.mapValues { udmToRuntimeValue(it.value) })
            is com.glomidco.utlx.core.udm.UDM -> RuntimeValue.UDMValue(result)
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

    // B25 Part 2: `owner` is the interpreter whose lambda bodies these higher-order builtins
    // should run on. When provided (the normal execution path) map/filter/reduce/find/findIndex
    // reuse it instead of constructing a throwaway `Interpreter()` that lacks the stdlib lookup —
    // which is why stdlib functions called inside a lambda failed on the native binary. Defaults
    // to null so other callers (REPL, tests) keep their previous behaviour.
    fun registerAll(env: Environment, owner: Interpreter? = null) {
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
                val interpreter = owner ?: Interpreter()
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
                        val interpreter = owner ?: Interpreter()
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
                                val interpreter = owner ?: Interpreter()
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
                                val interpreter = owner ?: Interpreter()
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
                                    val interpreter = owner ?: Interpreter()
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
                        val interpreter = owner ?: Interpreter()
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
                        val interpreter = owner ?: Interpreter()
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
                val interpreter = owner ?: Interpreter()
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
                val interpreter = owner ?: Interpreter()
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
                val interpreter = owner ?: Interpreter()
                accumulator = interpreter.evaluate(lambda.body, lambdaEnv)
            }
            accumulator
        }

        // B25: first/last stay interpreter-side. Like find, they return an *element of the input*,
        // so routing them through the canonical lookup would round-trip that element through
        // UDM<->RuntimeValue and lose fidelity (Int collapses to Double, XML attributes/elements
        // are dropped). They take no lambda, but they are NOT pure value transforms.
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
