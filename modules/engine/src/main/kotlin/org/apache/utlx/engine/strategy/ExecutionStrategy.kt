package org.apache.utlx.engine.strategy

import org.apache.utlx.engine.config.TransformConfig

interface ExecutionStrategy {
    fun initialize(source: String, config: TransformConfig)
    fun execute(input: String): ExecutionResult
    fun executeBatch(inputs: List<String>): List<ExecutionResult> = inputs.map { execute(it) }
    fun shutdown()
    val name: String

    /** Schema info extracted from the .utlx header after compilation. Null if not parsed yet. */
    fun getHeaderSchemaInfo(): HeaderSchemaInfo? = null
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
    val metadata: Map<String, Any> = emptyMap()
)

data class ValidationError(
    val message: String,
    val path: String? = null,
    val severity: String = "ERROR"
)
