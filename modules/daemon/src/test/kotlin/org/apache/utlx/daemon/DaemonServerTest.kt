// modules/daemon/src/test/kotlin/org/apache/utlx/daemon/DaemonServerTest.kt
package org.apache.utlx.daemon

import org.apache.utlx.daemon.protocol.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Integration tests for UTLXDaemon server
 *
 * Tests the request routing and LSP lifecycle without starting actual transports.
 */
class DaemonServerTest {

    @Test
    fun `test daemon statistics initially empty`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        val stats = daemon.getStatistics()

        assertEquals(false, stats["initialized"])
        assertEquals("STDIO", stats["transport"])

        @Suppress("UNCHECKED_CAST")
        val stateStats = stats["state"] as Map<String, Any>
        assertEquals(0, stateStats["openDocuments"])
        assertEquals(0, stateStats["cachedTypeEnvironments"])
        assertEquals(0, stateStats["cachedSchemas"])
        assertEquals(0, stateStats["cachedAsts"])
    }

    @Test
    fun `test daemon with socket transport type`() {
        val daemon = UTLXDaemon(TransportType.SOCKET, 8888)

        val stats = daemon.getStatistics()

        assertEquals("SOCKET", stats["transport"])
    }

    @Test
    fun `test daemon stop before start does not throw`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        // Should not throw exception
        assertDoesNotThrow {
            daemon.stop()
        }
    }

    /**
     * Helper to invoke private handleRequest method via reflection
     */
    private fun invokeHandleRequest(daemon: UTLXDaemon, request: JsonRpcRequest): JsonRpcResponse {
        val method = UTLXDaemon::class.java.getDeclaredMethod(
            "handleRequest",
            JsonRpcRequest::class.java
        )
        method.isAccessible = true
        return method.invoke(daemon, request) as JsonRpcResponse
    }

    @Test
    fun `test initialize request`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        val request = JsonRpcRequest(
            id = RequestId.NumberId(1),
            method = "initialize",
            params = mapOf("processId" to 1234)
        )

        val response = invokeHandleRequest(daemon, request)

        assertNotNull(response.result)
        assertNull(response.error)

        @Suppress("UNCHECKED_CAST")
        val result = response.result as Map<*, *>
        assertTrue(result.containsKey("capabilities"))
        assertTrue(result.containsKey("serverInfo"))

        @Suppress("UNCHECKED_CAST")
        val capabilities = result["capabilities"] as Map<*, *>
        assertTrue(capabilities.containsKey("textDocumentSync"))
        assertTrue(capabilities.containsKey("completionProvider"))
    }

    @Test
    fun `test shutdown request`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        val request = JsonRpcRequest(
            id = RequestId.NumberId(2),
            method = "shutdown"
        )

        val response = invokeHandleRequest(daemon, request)

        // Shutdown returns null result (success with no data)
        assertNull(response.error)
        assertEquals(RequestId.NumberId(2), response.id)
    }

    @Test
    fun `test textDocument didOpen`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        val request = JsonRpcRequest(
            id = RequestId.NumberId(3),
            method = "textDocument/didOpen",
            params = mapOf(
                "textDocument" to mapOf(
                    "uri" to "file:///test.utlx",
                    "languageId" to "utlx",
                    "version" to 1,
                    "text" to "output = input.name"
                )
            )
        )

        val response = invokeHandleRequest(daemon, request)

        assertNull(response.error)
    }

    @Test
    fun `test textDocument didChange`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        // First open the document
        val openRequest = JsonRpcRequest(
            id = RequestId.NumberId(1),
            method = "textDocument/didOpen",
            params = mapOf(
                "textDocument" to mapOf(
                    "uri" to "file:///test.utlx",
                    "languageId" to "utlx",
                    "version" to 1,
                    "text" to "output = input.name"
                )
            )
        )
        invokeHandleRequest(daemon,openRequest)

        // Now change it
        val changeRequest = JsonRpcRequest(
            id = RequestId.NumberId(2),
            method = "textDocument/didChange",
            params = mapOf(
                "textDocument" to mapOf(
                    "uri" to "file:///test.utlx",
                    "version" to 2
                ),
                "contentChanges" to listOf(
                    mapOf("text" to "output = input.id")
                )
            )
        )

        val response = invokeHandleRequest(daemon,changeRequest)

        assertNull(response.error)
    }

    @Test
    fun `test textDocument didClose`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        // First open the document
        val openRequest = JsonRpcRequest(
            id = RequestId.NumberId(1),
            method = "textDocument/didOpen",
            params = mapOf(
                "textDocument" to mapOf(
                    "uri" to "file:///test.utlx",
                    "languageId" to "utlx",
                    "version" to 1,
                    "text" to "output = input.name"
                )
            )
        )
        invokeHandleRequest(daemon,openRequest)

        // Now close it
        val closeRequest = JsonRpcRequest(
            id = RequestId.NumberId(2),
            method = "textDocument/didClose",
            params = mapOf(
                "textDocument" to mapOf(
                    "uri" to "file:///test.utlx"
                )
            )
        )

        val response = invokeHandleRequest(daemon,closeRequest)

        assertNull(response.error)
    }

    @Test
    fun `test unknown method returns method not found`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        val request = JsonRpcRequest(
            id = RequestId.NumberId(1),
            method = "unknownMethod"
        )

        val response = invokeHandleRequest(daemon, request)

        assertNotNull(response.error)
        assertEquals(-32601, response.error?.code)
        assertTrue(response.error?.message?.contains("unknownMethod") == true)
    }

    @Test
    fun `test not-yet-implemented methods return internal error`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        // Test hover (Phase 2, not yet implemented)
        val request = JsonRpcRequest(
            id = RequestId.NumberId(1),
            method = "textDocument/hover"
        )

        val response = invokeHandleRequest(daemon, request)

        assertNotNull(response.error)
        assertEquals(-32603, response.error?.code)
        assertTrue(response.error?.message?.contains("not yet implemented") == true)
    }

    @Test
    fun `test invalid params for didOpen`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        // Missing required textDocument param
        val request = JsonRpcRequest(
            id = RequestId.NumberId(1),
            method = "textDocument/didOpen",
            params = mapOf("invalid" to "param")
        )

        val response = invokeHandleRequest(daemon, request)

        assertNotNull(response.error)
        assertEquals(-32602, response.error?.code)
    }

    @Test
    fun `test didOpen with missing uri`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        val request = JsonRpcRequest(
            id = RequestId.NumberId(1),
            method = "textDocument/didOpen",
            params = mapOf(
                "textDocument" to mapOf(
                    "languageId" to "utlx",
                    "version" to 1,
                    "text" to "output = input"
                    // Missing uri
                )
            )
        )

        val response = invokeHandleRequest(daemon, request)

        assertNotNull(response.error)
        assertEquals(-32602, response.error?.code)
        assertTrue(response.error?.message?.contains("uri") == true)
    }

    @Test
    fun `test didChange with missing contentChanges`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        val request = JsonRpcRequest(
            id = RequestId.NumberId(1),
            method = "textDocument/didChange",
            params = mapOf(
                "textDocument" to mapOf(
                    "uri" to "file:///test.utlx",
                    "version" to 2
                )
                // Missing contentChanges
            )
        )

        val response = invokeHandleRequest(daemon, request)

        assertNotNull(response.error)
        assertEquals(-32602, response.error?.code)
    }

    @Test
    fun `test notification handling (no response expected)`() {
        val daemon = UTLXDaemon(TransportType.STDIO)

        // initialized is a notification (no id)
        val request = JsonRpcRequest(
            id = null,
            method = "initialized"
        )

        assertTrue(request.isNotification)

        val response = invokeHandleRequest(daemon, request)

        // Even notifications get a response in our implementation (for simplicity)
        // but client should not expect one
        assertNotNull(response)
    }
}
