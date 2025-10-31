// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/protocol/JsonRpc.kt
package org.apache.utlx.daemon.protocol

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * JSON-RPC 2.0 Protocol Implementation
 *
 * Specification: https://www.jsonrpc.org/specification
 * Used by LSP: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/
 */

/**
 * JSON-RPC 2.0 Request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: RequestId?,
    val method: String,
    val params: Any? = null
) {
    init {
        require(jsonrpc == "2.0") { "JSON-RPC version must be '2.0'" }
    }

    val isNotification: Boolean get() = id == null
}

/**
 * JSON-RPC 2.0 Response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: RequestId?,
    val result: Any? = null,
    val error: JsonRpcError? = null
) {
    init {
        require(jsonrpc == "2.0") { "JSON-RPC version must be '2.0'" }
        require((result == null) != (error == null)) {
            "Response must have either result or error, but not both"
        }
    }

    companion object {
        fun success(id: RequestId?, result: Any? = null) = JsonRpcResponse(
            id = id,
            result = result
        )

        fun error(id: RequestId?, error: JsonRpcError) = JsonRpcResponse(
            id = id,
            error = error
        )

        fun error(id: RequestId?, code: ErrorCode, message: String, data: Any? = null) = JsonRpcResponse(
            id = id,
            error = JsonRpcError(code.code, message, data)
        )

        fun parseError(message: String = "Parse error") = error(
            id = null,
            code = ErrorCode.PARSE_ERROR,
            message = message
        )

        fun invalidRequest(id: RequestId? = null, message: String = "Invalid Request") = error(
            id = id,
            code = ErrorCode.INVALID_REQUEST,
            message = message
        )

        fun methodNotFound(id: RequestId?, method: String = "unknown") = error(
            id = id,
            code = ErrorCode.METHOD_NOT_FOUND,
            message = "Method not found: $method"
        )

        fun invalidParams(id: RequestId?, message: String = "Invalid params") = error(
            id = id,
            code = ErrorCode.INVALID_PARAMS,
            message = message
        )

        fun internalError(id: RequestId?, message: String = "Internal error", data: Any? = null) = error(
            id = id,
            code = ErrorCode.INTERNAL_ERROR,
            message = message,
            data = data
        )
    }
}

/**
 * JSON-RPC 2.0 Error Object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

/**
 * Standard JSON-RPC 2.0 Error Codes
 */
enum class ErrorCode(val code: Int) {
    PARSE_ERROR(-32700),
    INVALID_REQUEST(-32600),
    METHOD_NOT_FOUND(-32601),
    INVALID_PARAMS(-32602),
    INTERNAL_ERROR(-32603);

    // Server error range: -32000 to -32099
    companion object {
        const val SERVER_ERROR_START = -32000
        const val SERVER_ERROR_END = -32099
    }
}

/**
 * Request ID can be string, number, or null
 */
sealed class RequestId {
    data class StringId(val value: String) : RequestId()
    data class NumberId(val value: Long) : RequestId()

    companion object {
        fun from(value: Any?): RequestId? = when (value) {
            null -> null
            is String -> StringId(value)
            is Number -> NumberId(value.toLong())
            else -> throw IllegalArgumentException("Request ID must be string or number, got: ${value::class}")
        }
    }

    fun toAny(): Any = when (this) {
        is StringId -> value
        is NumberId -> value
    }
}

/**
 * JSON-RPC Message Parser
 */
class JsonRpcParser(private val mapper: ObjectMapper = jacksonObjectMapper()) {

    /**
     * Parse a JSON-RPC message (request or response)
     */
    fun parseMessage(json: String): JsonRpcMessage {
        val node = try {
            mapper.readTree(json)
        } catch (e: Exception) {
            throw JsonRpcParseException("Failed to parse JSON: ${e.message}", e)
        }

        // Check JSON-RPC version
        val version = node.get("jsonrpc")?.asText()
        if (version != "2.0") {
            throw JsonRpcParseException("Invalid JSON-RPC version: $version (expected '2.0')")
        }

        // Determine if it's a request or response
        return when {
            node.has("method") -> parseRequest(node)
            node.has("result") || node.has("error") -> parseResponse(node)
            else -> throw JsonRpcParseException("Invalid JSON-RPC message: must have 'method' or 'result'/'error'")
        }
    }

    private fun parseRequest(node: JsonNode): JsonRpcMessage.Request {
        val id = node.get("id")?.let { RequestId.from(parseId(it)) }
        val method = node.get("method")?.asText()
            ?: throw JsonRpcParseException("Missing 'method' field")
        val params = node.get("params")

        return JsonRpcMessage.Request(
            JsonRpcRequest(
                id = id,
                method = method,
                params = params
            )
        )
    }

    private fun parseResponse(node: JsonNode): JsonRpcMessage.Response {
        val id = node.get("id")?.let { RequestId.from(parseId(it)) }
        val result = node.get("result")
        val error = node.get("error")?.let { parseError(it) }

        return JsonRpcMessage.Response(
            JsonRpcResponse(
                id = id,
                result = result,
                error = error
            )
        )
    }

    private fun parseId(node: JsonNode): Any? = when {
        node.isTextual -> node.asText()
        node.isIntegralNumber -> node.asLong()
        node.isNull -> null
        else -> throw JsonRpcParseException("Invalid id type: ${node.nodeType}")
    }

    private fun parseError(node: JsonNode): JsonRpcError {
        val code = node.get("code")?.asInt()
            ?: throw JsonRpcParseException("Missing 'code' in error object")
        val message = node.get("message")?.asText()
            ?: throw JsonRpcParseException("Missing 'message' in error object")
        val data = node.get("data")

        return JsonRpcError(code, message, data)
    }

    /**
     * Serialize a JSON-RPC request to JSON string
     */
    fun serializeRequest(request: JsonRpcRequest): String {
        return mapper.writeValueAsString(request)
    }

    /**
     * Serialize a JSON-RPC response to JSON string
     */
    fun serializeResponse(response: JsonRpcResponse): String {
        return mapper.writeValueAsString(response)
    }
}

/**
 * Sealed class representing either a request or response
 */
sealed class JsonRpcMessage {
    data class Request(val request: JsonRpcRequest) : JsonRpcMessage()
    data class Response(val response: JsonRpcResponse) : JsonRpcMessage()
}

/**
 * Exception thrown during JSON-RPC parsing
 */
class JsonRpcParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
