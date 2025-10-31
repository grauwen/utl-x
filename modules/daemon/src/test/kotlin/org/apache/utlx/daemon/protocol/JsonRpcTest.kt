// modules/daemon/src/test/kotlin/org/apache/utlx/daemon/protocol/JsonRpcTest.kt
package org.apache.utlx.daemon.protocol

import org.apache.utlx.daemon.transport.LspMessageFormat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for JSON-RPC 2.0 protocol implementation
 */
class JsonRpcTest {

    private val parser = JsonRpcParser()

    @Test
    fun `test parse simple request with string id`() {
        val json = """{"jsonrpc":"2.0","id":"test-1","method":"initialize","params":{"processId":1234}}"""

        val message = parser.parseMessage(json)

        assertTrue(message is JsonRpcMessage.Request)
        val request = (message as JsonRpcMessage.Request).request

        assertEquals("2.0", request.jsonrpc)
        assertEquals("initialize", request.method)
        assertTrue(request.id is RequestId.StringId)
        assertEquals("test-1", (request.id as RequestId.StringId).value)
        assertNotNull(request.params)
    }

    @Test
    fun `test parse request with number id`() {
        val json = """{"jsonrpc":"2.0","id":42,"method":"textDocument/didOpen"}"""

        val message = parser.parseMessage(json)

        assertTrue(message is JsonRpcMessage.Request)
        val request = (message as JsonRpcMessage.Request).request

        assertTrue(request.id is RequestId.NumberId)
        assertEquals(42L, (request.id as RequestId.NumberId).value)
    }

    @Test
    fun `test parse notification (no id)`() {
        val json = """{"jsonrpc":"2.0","method":"initialized"}"""

        val message = parser.parseMessage(json)

        assertTrue(message is JsonRpcMessage.Request)
        val request = (message as JsonRpcMessage.Request).request

        assertNull(request.id)
        assertTrue(request.isNotification)
    }

    @Test
    fun `test parse response with result`() {
        val json = """{"jsonrpc":"2.0","id":1,"result":{"capabilities":{"textDocumentSync":1}}}"""

        val message = parser.parseMessage(json)

        assertTrue(message is JsonRpcMessage.Response)
        val response = (message as JsonRpcMessage.Response).response

        assertNotNull(response.result)
        assertNull(response.error)
    }

    @Test
    fun `test parse response with error`() {
        val json = """{"jsonrpc":"2.0","id":1,"error":{"code":-32600,"message":"Invalid Request"}}"""

        val message = parser.parseMessage(json)

        assertTrue(message is JsonRpcMessage.Response)
        val response = (message as JsonRpcMessage.Response).response

        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(-32600, response.error?.code)
        assertEquals("Invalid Request", response.error?.message)
    }

    @Test
    fun `test serialize request`() {
        val request = JsonRpcRequest(
            id = RequestId.StringId("test-1"),
            method = "initialize",
            params = mapOf("processId" to 1234)
        )

        val json = parser.serializeRequest(request)

        assertTrue(json.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(json.contains("\"id\":\"test-1\""))
        assertTrue(json.contains("\"method\":\"initialize\""))
        assertTrue(json.contains("\"params\""))
    }

    @Test
    fun `test serialize response with result`() {
        val response = JsonRpcResponse.success(
            RequestId.NumberId(42),
            mapOf("status" to "ok")
        )

        val json = parser.serializeResponse(response)

        assertTrue(json.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(json.contains("\"id\":42"))
        assertTrue(json.contains("\"result\""))
        assertFalse(json.contains("\"error\""))
    }

    @Test
    fun `test serialize response with error`() {
        val response = JsonRpcResponse.error(
            RequestId.StringId("test-1"),
            ErrorCode.INVALID_PARAMS,
            "Missing required parameter"
        )

        val json = parser.serializeResponse(response)

        assertTrue(json.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(json.contains("\"id\":\"test-1\""))
        assertTrue(json.contains("\"error\""))
        assertTrue(json.contains("\"code\":-32602"))
        assertTrue(json.contains("\"message\":\"Missing required parameter\""))
    }

    @Test
    fun `test error code constants`() {
        assertEquals(-32700, ErrorCode.PARSE_ERROR.code)
        assertEquals(-32600, ErrorCode.INVALID_REQUEST.code)
        assertEquals(-32601, ErrorCode.METHOD_NOT_FOUND.code)
        assertEquals(-32602, ErrorCode.INVALID_PARAMS.code)
        assertEquals(-32603, ErrorCode.INTERNAL_ERROR.code)
    }

    @Test
    fun `test convenience methods for common errors`() {
        val parseError = JsonRpcResponse.parseError("Bad JSON")
        assertNull(parseError.id)
        assertEquals(-32700, parseError.error?.code)

        val methodNotFound = JsonRpcResponse.methodNotFound(
            RequestId.NumberId(1),
            "unknownMethod"
        )
        assertEquals(-32601, methodNotFound.error?.code)

        val invalidParams = JsonRpcResponse.invalidParams(
            RequestId.NumberId(2),
            "Missing param"
        )
        assertEquals(-32602, invalidParams.error?.code)

        val internalError = JsonRpcResponse.internalError(
            RequestId.NumberId(3),
            "Server error"
        )
        assertEquals(-32603, internalError.error?.code)
    }

    @Test
    fun `test parse invalid JSON throws exception`() {
        val invalidJson = """{"jsonrpc":"2.0","id":1"""

        assertThrows(JsonRpcParseException::class.java) {
            parser.parseMessage(invalidJson)
        }
    }

    @Test
    fun `test parse message without jsonrpc field`() {
        val json = """{"id":1,"method":"test"}"""

        // Should still parse but default to "2.0"
        val message = parser.parseMessage(json)
        assertTrue(message is JsonRpcMessage.Request)
    }

    @Test
    fun `test RequestId from factory`() {
        val stringId = RequestId.from("test")
        assertTrue(stringId is RequestId.StringId)
        assertEquals("test", (stringId as RequestId.StringId).value)

        val numberId = RequestId.from(42)
        assertTrue(numberId is RequestId.NumberId)
        assertEquals(42L, (numberId as RequestId.NumberId).value)

        val nullId = RequestId.from(null)
        assertNull(nullId)
    }

    @Test
    fun `test LSP message format encoding`() {
        val json = """{"jsonrpc":"2.0","id":1,"method":"test"}"""
        val encoded = LspMessageFormat.encode(json)

        assertTrue(encoded.startsWith("Content-Length: "))
        assertTrue(encoded.contains("\r\n\r\n"))
        assertTrue(encoded.endsWith(json))

        // Content-Length should match the byte length of JSON
        val contentLength = json.toByteArray(Charsets.UTF_8).size
        assertTrue(encoded.contains("Content-Length: $contentLength\r\n\r\n"))
    }

    @Test
    fun `test response with null result`() {
        val response = JsonRpcResponse.success(RequestId.NumberId(1), null)
        val json = parser.serializeResponse(response)

        assertTrue(json.contains("\"result\":null"))
    }

    @Test
    fun `test request with complex params`() {
        val params = mapOf(
            "textDocument" to mapOf(
                "uri" to "file:///test.utlx",
                "languageId" to "utlx",
                "version" to 1,
                "text" to "test content"
            )
        )

        val request = JsonRpcRequest(
            id = RequestId.NumberId(1),
            method = "textDocument/didOpen",
            params = params
        )

        val json = parser.serializeRequest(request)
        val parsed = parser.parseMessage(json)

        assertTrue(parsed is JsonRpcMessage.Request)
        val parsedRequest = (parsed as JsonRpcMessage.Request).request
        assertEquals("textDocument/didOpen", parsedRequest.method)
        assertNotNull(parsedRequest.params)
    }
}
