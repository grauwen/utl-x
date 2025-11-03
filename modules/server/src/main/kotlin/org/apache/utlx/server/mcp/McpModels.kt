// modules/server/src/main/kotlin/org/apache/utlx/server/mcp/McpModels.kt
package org.apache.utlx.server.mcp

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * MCP (Model Context Protocol) JSON-RPC 2.0 Models
 *
 * Based on Anthropic's MCP specification
 */

// ============================================
// JSON-RPC 2.0 Base Models
// ============================================

/**
 * JSON-RPC 2.0 Request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Any? = null,  // String or Number
    val method: String,
    val params: Map<String, Any>? = null
)

/**
 * JSON-RPC 2.0 Success Response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Any?,  // String or Number
    val result: Any? = null,
    val error: JsonRpcError? = null
)

/**
 * JSON-RPC 2.0 Error
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
) {
    companion object {
        // Standard JSON-RPC error codes
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603

        // MCP-specific error codes
        const val TRANSFORM_ERROR = -32000
        const val VALIDATION_ERROR = -32001
        const val TIMEOUT_ERROR = -32002
        const val SIZE_LIMIT_ERROR = -32003

        fun parseError(message: String = "Parse error") = JsonRpcError(PARSE_ERROR, message)
        fun invalidRequest(message: String = "Invalid request") = JsonRpcError(INVALID_REQUEST, message)
        fun methodNotFound(message: String = "Method not found") = JsonRpcError(METHOD_NOT_FOUND, message)
        fun invalidParams(message: String = "Invalid params") = JsonRpcError(INVALID_PARAMS, message)
        fun internalError(message: String = "Internal error") = JsonRpcError(INTERNAL_ERROR, message)
        fun transformError(message: String) = JsonRpcError(TRANSFORM_ERROR, message)
        fun validationError(message: String) = JsonRpcError(VALIDATION_ERROR, message)
        fun timeoutError(message: String) = JsonRpcError(TIMEOUT_ERROR, message)
        fun sizeLimitError(message: String) = JsonRpcError(SIZE_LIMIT_ERROR, message)
    }
}

// ============================================
// MCP Tool Models
// ============================================

/**
 * MCP Tool Definition
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>
)

/**
 * Response for tools/list method
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ToolsListResponse(
    val tools: List<McpTool>
)

// ============================================
// UTL-X Transform Models
// ============================================

/**
 * Transform request parameters
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TransformRequest(
    val script: String,
    val input: Any? = null,
    @JsonProperty("input_format")
    val inputFormat: String? = "json",
    @JsonProperty("output_format")
    val outputFormat: String? = "json",
    val options: Map<String, Any>? = null
)

/**
 * Transform response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TransformResponse(
    val output: Any,
    val format: String = "json",
    val metadata: Map<String, Any>? = null
)

// ============================================
// UTL-X Validate Models
// ============================================

/**
 * Validation request parameters
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValidateRequest(
    val script: String,
    val schema: Any? = null,
    val level: String = "syntax"  // syntax | semantic | schema
)

/**
 * Validation response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValidateResponse(
    val valid: Boolean,
    val errors: List<ValidationError> = emptyList(),
    val warnings: List<ValidationWarning> = emptyList()
)

/**
 * Validation error
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValidationError(
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val severity: String = "error",
    val code: String? = null
)

/**
 * Validation warning
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValidationWarning(
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val severity: String = "warning",
    val code: String? = null
)

// ============================================
// Schema Generation Models
// ============================================

/**
 * Schema generation request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GenerateSchemaRequest(
    val script: String,
    @JsonProperty("schema_type")
    val schemaType: String = "json_schema",  // json_schema | avro | xsd
    val options: Map<String, Any>? = null
)

/**
 * Schema generation response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GenerateSchemaResponse(
    val schema: Any,
    val format: String,
    val metadata: Map<String, Any>? = null
)

// ============================================
// Server Status Models
// ============================================

/**
 * Server status response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ServerStatusResponse(
    val status: String = "running",
    val version: String,
    val uptime: Long,
    @JsonProperty("active_sessions")
    val activeSessions: Int,
    @JsonProperty("total_requests")
    val totalRequests: Long,
    val features: Map<String, Boolean>
)

// ============================================
// Event Models (for SSE)
// ============================================

/**
 * Server-sent event
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ServerEvent(
    val type: String,
    val data: Any,
    val timestamp: Long = System.currentTimeMillis()
)
