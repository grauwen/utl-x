// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/diagnostics/DiagnosticsPublisher.kt
package org.apache.utlx.daemon.diagnostics

import org.apache.utlx.daemon.protocol.JsonRpcRequest
import org.apache.utlx.daemon.state.StateManager
import org.apache.utlx.daemon.transport.Transport
import org.slf4j.LoggerFactory

/**
 * Diagnostics Publisher
 *
 * Analyzes documents and publishes diagnostics (errors, warnings) to the client.
 */
class DiagnosticsPublisher(
    private val stateManager: StateManager
) {
    private val logger = LoggerFactory.getLogger(DiagnosticsPublisher::class.java)
    private val analyzer = DiagnosticsAnalyzer()

    private var transport: Transport? = null

    /**
     * Set the transport for publishing diagnostics
     */
    fun setTransport(transport: Transport) {
        this.transport = transport
    }

    /**
     * Analyze a document and publish diagnostics
     *
     * @param uri Document URI
     */
    fun publishDiagnostics(uri: String) {
        val currentTransport = transport
        if (currentTransport == null) {
            logger.warn("No transport available for publishing diagnostics")
            return
        }

        logger.debug("Publishing diagnostics for: $uri")

        // Get document text
        val text = stateManager.getDocumentText(uri)
        if (text == null) {
            logger.warn("Document not found: $uri")
            return
        }

        // Get type environment
        val typeEnv = stateManager.getTypeEnvironment(uri)

        // Analyze document (or return empty diagnostics if no type environment)
        val diagnostics = if (typeEnv != null) {
            analyzer.analyze(text, typeEnv)
        } else {
            // No type environment yet - no diagnostics
            emptyList()
        }

        logger.debug("Found ${diagnostics.size} diagnostic(s) for $uri")

        // Create publish diagnostics params
        val params = PublishDiagnosticsParams(
            uri = uri,
            diagnostics = diagnostics
        )

        // Send as notification
        sendDiagnosticsNotification(currentTransport, params)
    }

    /**
     * Clear diagnostics for a document
     *
     * @param uri Document URI
     */
    fun clearDiagnostics(uri: String) {
        val currentTransport = transport ?: return

        logger.debug("Clearing diagnostics for: $uri")

        val params = PublishDiagnosticsParams(
            uri = uri,
            diagnostics = emptyList()
        )

        sendDiagnosticsNotification(currentTransport, params)
    }

    /**
     * Send diagnostics notification through transport
     */
    private fun sendDiagnosticsNotification(transport: Transport, params: PublishDiagnosticsParams) {
        val notification = JsonRpcRequest(
            id = null, // Notifications have no id
            method = "textDocument/publishDiagnostics",
            params = mapOf(
                "uri" to params.uri,
                "diagnostics" to params.diagnostics.map { diagnostic ->
                    mutableMapOf<String, Any>(
                        "range" to mapOf(
                            "start" to mapOf(
                                "line" to diagnostic.range.start.line,
                                "character" to diagnostic.range.start.character
                            ),
                            "end" to mapOf(
                                "line" to diagnostic.range.end.line,
                                "character" to diagnostic.range.end.character
                            )
                        ),
                        "severity" to diagnostic.severity.value,
                        "source" to diagnostic.source,
                        "message" to diagnostic.message
                    ).apply {
                        diagnostic.code?.let { put("code", it) }
                    }
                }
            )
        )

        transport.sendNotification(notification)
    }
}
