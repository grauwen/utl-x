package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.core.udm.PayloadBytes
import com.glomidco.utlx.engine.config.TransformConfig

interface ExecutionStrategy {
    fun initialize(source: String, config: TransformConfig)
    fun execute(input: String): ExecutionResult
    fun executeBatch(inputs: List<String>): List<ExecutionResult> = inputs.map { execute(it) }
    fun shutdown()
    val name: String

    /** Schema info extracted from the .utlx header after compilation. Null if not parsed yet. */
    fun getHeaderSchemaInfo(): HeaderSchemaInfo? = null

    /**
     * EB01: Execute with raw bytes — preserves original encoding for non-UTF-8 content.
     * Default implementation decodes to String (backward compat). Strategies can override
     * to pass raw bytes directly to format parsers for encoding-aware parsing.
     */
    fun execute(input: PayloadBytes): ExecutionResult = execute(input.decodeToString())

    /** EB01: Batch execute with raw bytes. */
    fun executeBatchBytes(inputs: List<PayloadBytes>): List<ExecutionResult> = inputs.map { execute(it) }
}

/**
 * Schema references from the parsed .utlx header.
 * Used by the Admin API to resolve schemas from the SchemaStore and create validators.
 */
data class HeaderSchemaInfo(
    val inputFormat: String,                // "json", "xml", "csv", etc. (primary input)
    val inputSchemaRef: String? = null,     // e.g., "order.xsd" from {schema: "order.xsd"} (primary input)
    val outputFormat: String,
    val outputSchemaRef: String? = null,
    val allInputSchemas: Map<String, InputSchemaRef> = emptyMap()  // name → (format, schemaRef) for all inputs
)

data class InputSchemaRef(
    val format: String,
    val schemaRef: String?
)

data class ExecutionResult(
    val output: String,
    val validationErrors: List<ValidationError> = emptyList(),
    val metadata: Map<String, Any> = emptyMap(),
    /**
     * EF23: what looking at the output for a `validate.*` verdict found. Defaults to
     * [com.glomidco.utlx.engine.routing.VerdictCheck.NotInspected] on purpose — a strategy that
     * does not set it is recorded as *unknown*, never as *nothing to gate*.
     */
    val verdict: com.glomidco.utlx.engine.routing.VerdictCheck =
        com.glomidco.utlx.engine.routing.VerdictCheck.NotInspected,
)

data class ValidationError(
    val message: String,
    val path: String? = null,
    val severity: String = "ERROR"
)

/**
 * EB15: reject a `%utlx` header this engine cannot honour, before a strategy decides how to run it.
 *
 * The gate lived only in `Interpreter`, so it fired only where the interpreter ran. A *compilable*
 * script never reached it: `%utlx 2.0` executed as if it were 1.0 on COPY and COMPILED — fail-OPEN
 * on a version gate, and on the engine's default strategy. Every strategy now asks at
 * `initialize`, which is the earliest point the header is known.
 *
 * Throws the same `RuntimeError` the interpreter throws, with the header's own location, so the
 * three strategies report an unsupported version identically.
 */
internal fun requireSupportedLanguageVersion(program: com.glomidco.utlx.core.ast.Program) {
    com.glomidco.utlx.core.semantic.LanguageVersions
        .unsupportedReason(program.header.version)
        ?.let {
            throw com.glomidco.utlx.core.interpreter.RuntimeError(it, program.header.location)
        }
}
