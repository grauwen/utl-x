// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/rest/RestApiTypes.kt
package org.apache.utlx.daemon.rest

import kotlinx.serialization.Serializable

/**
 * Data Transfer Objects for REST API endpoints
 */

// Health endpoint
@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val uptime: Long
)

// Validation endpoint
@Serializable
data class ValidationRequest(
    val utlx: String,
    val strict: Boolean = false
)

@Serializable
data class Diagnostic(
    val severity: String, // "error", "warning", "info"
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val source: String? = null
)

@Serializable
data class ValidationResponse(
    val valid: Boolean,
    val diagnostics: List<Diagnostic>
)

// Execution endpoint
@Serializable
data class ExecutionRequest(
    val utlx: String,
    val input: String,
    val inputFormat: String = "json", // json, xml, csv, yaml
    val outputFormat: String = "json"
)

@Serializable
data class ExecutionResponse(
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
    val executionTimeMs: Long
)

// Schema inference endpoint
@Serializable
data class InferSchemaRequest(
    val utlx: String,
    val inputSchema: String? = null,
    val format: String = "json-schema" // json-schema, xsd
)

@Serializable
data class InferSchemaResponse(
    val success: Boolean,
    val schema: String? = null,
    val schemaFormat: String,
    val confidence: Double = 1.0,
    val error: String? = null
)

// Schema parsing endpoint
@Serializable
data class ParseSchemaRequest(
    val schema: String,
    val format: String // xsd, json-schema, csv-header, yaml
)

@Serializable
data class ParseSchemaResponse(
    val success: Boolean,
    val normalized: String? = null, // Normalized to common format
    val error: String? = null
)

// Error response
@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
