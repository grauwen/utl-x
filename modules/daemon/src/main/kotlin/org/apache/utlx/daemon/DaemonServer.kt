// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/DaemonServer.kt
package org.apache.utlx.daemon

import org.apache.utlx.daemon.protocol.*
import org.apache.utlx.daemon.state.StateManager
import org.apache.utlx.daemon.transport.SocketTransport
import org.apache.utlx.daemon.transport.StdioTransport
import org.apache.utlx.daemon.transport.Transport
import org.slf4j.LoggerFactory

/**
 * UTL-X Daemon: Long-running LSP server for design-time analysis
 *
 * Architecture:
 * - Daemon = Long-running background process (architectural pattern)
 * - LSP = Language Server Protocol using JSON-RPC 2.0 (communication protocol)
 * - STDIO/Socket = Transport mechanism (physical layer)
 *
 * The daemon always uses LSP/JSON-RPC 2.0 protocol.
 * Only the transport differs (STDIO vs Socket).
 */
class UTLXDaemon(
    private val transportType: TransportType = TransportType.STDIO,
    private val port: Int = 7777
) {

    private val logger = LoggerFactory.getLogger(UTLXDaemon::class.java)
    private val stateManager = StateManager()

    private var transport: Transport? = null
    private var initialized = false

    /**
     * Start the daemon server
     */
    fun start() {
        logger.info("Starting UTL-X Daemon (transport: $transportType)")

        // Create transport based on type
        transport = when (transportType) {
            TransportType.STDIO -> {
                logger.info("Using STDIO transport (standard streams)")
                StdioTransport()
            }
            TransportType.SOCKET -> {
                logger.info("Using Socket transport (port: $port)")
                SocketTransport(port)
            }
        }

        // Start transport with message handler
        transport!!.start { request -> handleRequest(request) }

        logger.info("UTL-X Daemon stopped")
    }

    /**
     * Stop the daemon server
     */
    fun stop() {
        logger.info("Stopping UTL-X Daemon...")
        transport?.stop()
        initialized = false
    }

    /**
     * Handle incoming JSON-RPC request
     *
     * Routes requests to appropriate handlers based on method name.
     */
    private fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        logger.debug("Handling request: ${request.method}")

        return try {
            when (request.method) {
                // LSP lifecycle
                "initialize" -> handleInitialize(request)
                "initialized" -> handleInitialized(request)
                "shutdown" -> handleShutdown(request)
                "exit" -> handleExit(request)

                // Document synchronization
                "textDocument/didOpen" -> handleDidOpen(request)
                "textDocument/didChange" -> handleDidChange(request)
                "textDocument/didClose" -> handleDidClose(request)

                // Language features (placeholders for Phase 2)
                "textDocument/completion" -> notImplemented(request, "Completion not yet implemented (Phase 2)")
                "textDocument/hover" -> notImplemented(request, "Hover not yet implemented (Phase 2)")

                // UTL-X custom methods (placeholders for Phase 2/3)
                "utlx/complete" -> notImplemented(request, "Path completion not yet implemented (Phase 2)")
                "utlx/graph" -> notImplemented(request, "Graph API not yet implemented (Phase 3)")
                "utlx/visualize" -> notImplemented(request, "Visualization not yet implemented (Phase 3)")

                else -> JsonRpcResponse.methodNotFound(request.id, request.method)
            }
        } catch (e: Exception) {
            logger.error("Error handling request: ${request.method}", e)
            JsonRpcResponse.internalError(request.id, "Internal error: ${e.message}")
        }
    }

    /**
     * Handle initialize request (first message from client)
     */
    private fun handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
        logger.info("Initialize request received")

        initialized = true

        val result = mapOf(
            "capabilities" to mapOf(
                "textDocumentSync" to mapOf(
                    "openClose" to true,
                    "change" to 1  // Full sync
                ),
                // Placeholders for Phase 2
                "completionProvider" to mapOf(
                    "triggerCharacters" to listOf(".", "$")
                ),
                "hoverProvider" to false  // TODO: Phase 2
            ),
            "serverInfo" to mapOf(
                "name" to "UTL-X Language Server",
                "version" to "1.0.0-SNAPSHOT"
            )
        )

        return JsonRpcResponse.success(request.id, result)
    }

    /**
     * Handle initialized notification
     */
    private fun handleInitialized(request: JsonRpcRequest): JsonRpcResponse {
        logger.info("Client confirmed initialization")
        // Notification - no response needed, but we return success for simplicity
        return JsonRpcResponse.success(request.id, null)
    }

    /**
     * Handle shutdown request
     */
    private fun handleShutdown(request: JsonRpcRequest): JsonRpcResponse {
        logger.info("Shutdown request received")
        return JsonRpcResponse.success(request.id, null)
    }

    /**
     * Handle exit notification
     */
    private fun handleExit(request: JsonRpcRequest): JsonRpcResponse {
        logger.info("Exit notification received - stopping daemon")
        // Exit is a notification, but we stop the daemon
        stop()
        return JsonRpcResponse.success(request.id, null)
    }

    /**
     * Handle textDocument/didOpen notification
     */
    private fun handleDidOpen(request: JsonRpcRequest): JsonRpcResponse {
        @Suppress("UNCHECKED_CAST")
        val params = request.params as? Map<*, *>
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing params")

        @Suppress("UNCHECKED_CAST")
        val textDocument = params["textDocument"] as? Map<*, *>
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing textDocument")

        val uri = textDocument["uri"] as? String
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing uri")
        val text = textDocument["text"] as? String
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing text")
        val version = (textDocument["version"] as? Number)?.toInt() ?: 0
        val languageId = textDocument["languageId"] as? String ?: "utlx"

        logger.info("Document opened: $uri")
        stateManager.openDocument(uri, text, version, languageId)

        return JsonRpcResponse.success(request.id, null)
    }

    /**
     * Handle textDocument/didChange notification
     */
    private fun handleDidChange(request: JsonRpcRequest): JsonRpcResponse {
        @Suppress("UNCHECKED_CAST")
        val params = request.params as? Map<*, *>
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing params")

        @Suppress("UNCHECKED_CAST")
        val textDocument = params["textDocument"] as? Map<*, *>
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing textDocument")

        val uri = textDocument["uri"] as? String
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing uri")
        val version = (textDocument["version"] as? Number)?.toInt() ?: 0

        @Suppress("UNCHECKED_CAST")
        val contentChanges = params["contentChanges"] as? List<*>
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing contentChanges")

        // Full document sync (change = 1)
        @Suppress("UNCHECKED_CAST")
        val firstChange = contentChanges.firstOrNull() as? Map<*, *>
            ?: return JsonRpcResponse.invalidParams(request.id, "Empty contentChanges")

        val text = firstChange["text"] as? String
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing text in change")

        logger.debug("Document changed: $uri (version $version)")
        stateManager.updateDocument(uri, text, version)

        return JsonRpcResponse.success(request.id, null)
    }

    /**
     * Handle textDocument/didClose notification
     */
    private fun handleDidClose(request: JsonRpcRequest): JsonRpcResponse {
        @Suppress("UNCHECKED_CAST")
        val params = request.params as? Map<*, *>
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing params")

        @Suppress("UNCHECKED_CAST")
        val textDocument = params["textDocument"] as? Map<*, *>
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing textDocument")

        val uri = textDocument["uri"] as? String
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing uri")

        logger.info("Document closed: $uri")
        stateManager.closeDocument(uri)

        return JsonRpcResponse.success(request.id, null)
    }

    /**
     * Placeholder for not-yet-implemented methods
     */
    private fun notImplemented(request: JsonRpcRequest, message: String): JsonRpcResponse {
        logger.warn("Method not implemented: ${request.method}")
        return JsonRpcResponse.error(
            request.id,
            ErrorCode.INTERNAL_ERROR,
            message
        )
    }

    /**
     * Get daemon statistics (for debugging)
     */
    fun getStatistics(): Map<String, Any> {
        val stats = stateManager.getStatistics()
        return mapOf(
            "initialized" to initialized,
            "transport" to transportType.name,
            "state" to mapOf(
                "openDocuments" to stats.openDocuments,
                "cachedTypeEnvironments" to stats.cachedTypeEnvironments,
                "cachedSchemas" to stats.cachedSchemas,
                "cachedAsts" to stats.cachedAsts
            )
        )
    }
}

/**
 * Transport type enumeration
 *
 * Both transports use identical LSP/JSON-RPC 2.0 protocol.
 * The difference is only in the physical communication channel.
 */
enum class TransportType {
    STDIO,   // Standard streams (for IDE plugins) - Default
    SOCKET   // TCP socket (for remote access, debugging)
}
