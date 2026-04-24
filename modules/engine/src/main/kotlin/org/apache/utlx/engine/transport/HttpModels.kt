package org.apache.utlx.engine.transport

/**
 * JSON request/response models for the HTTP transport.
 * These mirror the proto messages but use Jackson-friendly data classes.
 */

data class TransformRequest(
    val transformationId: String,
    val utlxSource: String,
    val payload: String,
    val contentType: String = "application/json",
    val strategy: String = "TEMPLATE",
    val correlationId: String? = null
)

data class TransformResponse(
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
    val durationUs: Long = 0,
    val correlationId: String? = null
)

data class LoadRequest(
    val transformationId: String,
    val utlxSource: String,
    val strategy: String = "TEMPLATE",
    val validationPolicy: String = "SKIP",
    val maxConcurrent: Int = 1,
    val config: Map<String, String> = emptyMap()
)

data class LoadResponse(
    val success: Boolean,
    val error: String? = null,
    val compileDurationUs: Long = 0
)

data class ExecuteRequestBody(
    val payload: String,
    val contentType: String = "application/json",
    val correlationId: String? = null
)

data class ExecuteResponseBody(
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
    val errorPhase: String? = null,
    val durationUs: Long = 0,
    val correlationId: String? = null,
    val validationErrors: List<ValidationErrorDto> = emptyList()
)

data class ValidationErrorDto(
    val message: String,
    val path: String? = null,
    val severity: String = "ERROR"
)

data class BatchItemRequest(
    val payload: String,
    val contentType: String = "application/json",
    val correlationId: String? = null
)

data class BatchRequest(
    val items: List<BatchItemRequest>
)

data class BatchResponse(
    val results: List<ExecuteResponseBody>
)

data class PipelineRequest(
    val transformationIds: List<String>,
    val payload: String,
    val contentType: String = "application/json",
    val correlationId: String? = null
)

data class PipelineResponse(
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
    val stagesCompleted: Int = 0,
    val totalDurationUs: Long = 0,
    val correlationId: String? = null
)

data class HealthResponseDto(
    val state: String,
    val uptimeMs: Long,
    val loadedTransformations: Int,
    val totalExecutions: Long,
    val totalErrors: Long
)
