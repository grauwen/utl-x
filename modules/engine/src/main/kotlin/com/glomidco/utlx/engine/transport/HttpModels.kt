package com.glomidco.utlx.engine.transport

import com.google.protobuf.ByteString
import com.glomidco.utlx.formats.xlsx.XlsxSerializer

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
    val correlationId: String? = null,
    val payloadEncoding: String = "text"
)

data class TransformResponse(
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
    val durationUs: Long = 0,
    val correlationId: String? = null,
    val outputContentType: String? = null,
    val outputEncoding: String? = null
)

data class LoadRequest(
    val transformationId: String,
    val utlxSource: String,
    val strategy: String = "TEMPLATE",
    val validationPolicy: String = "SKIP",
    val maxConcurrent: Int = 0,   // 0 = unlimited
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
    val correlationId: String? = null,
    val payloadEncoding: String = "text"
)

data class ExecuteResponseBody(
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
    val errorPhase: String? = null,
    val durationUs: Long = 0,
    val correlationId: String? = null,
    val validationErrors: List<ValidationErrorDto> = emptyList(),
    val outputContentType: String? = null,
    val outputEncoding: String? = null
)

data class ValidationErrorDto(
    val message: String,
    val path: String? = null,
    val severity: String = "ERROR"
)

data class BatchItemRequest(
    val payload: String,
    val contentType: String = "application/json",
    val correlationId: String? = null,
    val payloadEncoding: String = "text"
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
    val correlationId: String? = null,
    val payloadEncoding: String = "text"
)

data class PipelineResponse(
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
    val stagesCompleted: Int = 0,
    val totalDurationUs: Long = 0,
    val correlationId: String? = null,
    val outputContentType: String? = null,
    val outputEncoding: String? = null
)

data class HealthResponseDto(
    val state: String,
    val uptimeMs: Long,
    val loadedTransformations: Int,
    val totalExecutions: Long,
    val totalErrors: Long
)

/** Binary payloads in JSON responses use base64; raw HTTP and protobuf retain the original bytes. */
internal fun encodeResponseOutput(bytes: ByteString, contentType: String): String =
    if (contentType == XlsxSerializer.CONTENT_TYPE) java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())
    else bytes.toStringUtf8()

internal fun responseOutputEncoding(contentType: String): String =
    if (contentType == XlsxSerializer.CONTENT_TYPE) "base64" else "text"
