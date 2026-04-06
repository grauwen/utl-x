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

// Multipart execution (for /api/execute-multipart endpoint)
/**
 * Metadata for a single multipart input.
 * The actual binary content is in the multipart file part.
 */
data class MultipartInputMetadata(
    val name: String,
    val format: String, // json, xml, csv, yaml, etc.
    val encoding: String = "UTF-8", // UTF-8, UTF-16LE, UTF-16BE, ISO-8859-1, etc.
    val hasBOM: Boolean = false // Whether input has Byte Order Mark
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

// UDM export endpoint (Format → UDM → .udm)
@Serializable
data class UDMExportRequest(
    val content: String,
    val format: String, // json, xml, csv, yaml, jsonschema, xsd, avro, protobuf
    val prettyPrint: Boolean = true,
    // Format-specific options
    val delimiter: String? = null,        // CSV: delimiter character (default: ',')
    val hasHeaders: Boolean? = null,      // CSV: has headers (default: true)
    val regional: String? = null,         // CSV: regional format (usa, european, french, swiss)
    val arrayHints: String? = null,       // XML/XSD: comma-separated array element names
    val rootName: String? = null,         // XML: root element name
    val encoding: String? = null,         // XML: output encoding
    val multiDoc: Boolean? = null,        // YAML: multi-document
    val draft: String? = null,            // JSON Schema: draft version
    val version: String? = null,          // XSD: version (1.0, 1.1)
    val namespace: String? = null,        // XSD/Avro: target namespace
    val pattern: String? = null,          // XSD: pattern (russian-doll, salami-slice, etc.)
    val validate: Boolean? = null,        // Avro: validate schema
    // Source metadata
    val sourceFile: String? = null
)

@Serializable
data class UDMExportResponse(
    val success: Boolean,
    val udmLanguage: String? = null,
    val sourceFormat: String? = null,
    val parsedAt: String? = null,
    val error: String? = null
)

// UDM import endpoint (.udm → UDM → Format)
@Serializable
data class UDMImportRequest(
    val udmLanguage: String,
    val targetFormat: String, // json, xml, csv, yaml, jsonschema, xsd, avro, protobuf
    val prettyPrint: Boolean = true,
    // Format-specific options (same as export)
    val delimiter: String? = null,
    val hasHeaders: Boolean? = null,
    val regional: String? = null,
    val arrayHints: String? = null,
    val rootName: String? = null,
    val encoding: String? = null,
    val multiDoc: Boolean? = null,
    val draft: String? = null,
    val version: String? = null,
    val namespace: String? = null,
    val pattern: String? = null,
    val validate: Boolean? = null
)

@Serializable
data class UDMImportResponse(
    val success: Boolean,
    val output: String? = null,
    val targetFormat: String? = null,
    val sourceInfo: Map<String, String>? = null,
    val error: String? = null
)

// UDM validate endpoint
@Serializable
data class UDMValidateRequest(
    val udmLanguage: String
)

@Serializable
data class UDMValidateResponse(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val udmVersion: String? = null,
    val sourceInfo: Map<String, String>? = null
)

// Error response
@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
