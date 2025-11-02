// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/diagnostics/DiagnosticsPublisher.kt
package org.apache.utlx.daemon.diagnostics

import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.daemon.completion.Position
import org.apache.utlx.daemon.hover.Range
import org.apache.utlx.daemon.protocol.JsonRpcRequest
import org.apache.utlx.daemon.state.StateManager
import org.apache.utlx.daemon.state.DocumentMode
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

        // Collect diagnostics from multiple sources
        val diagnostics = mutableListOf<Diagnostic>()

        // Get document mode (design-time vs runtime)
        val mode = stateManager.getDocumentMode(uri)
        val isDesignTime = mode == DocumentMode.DESIGN_TIME

        logger.debug("Publishing diagnostics for $uri in ${mode.name} mode")

        // 1. Parser-based diagnostics (syntax and structure errors)
        // In design-time mode, skip parser diagnostics (schema validation only)
        // In runtime mode, include full parser diagnostics
        val parserDiagnostics = if (!isDesignTime) {
            getParserDiagnostics(text, uri)
        } else {
            // Even in design-time mode, parse and cache AST for schema inference
            // but don't report parser errors as diagnostics
            getParserDiagnostics(text, uri)
            emptyList()
        }
        diagnostics.addAll(parserDiagnostics)

        // 2. Path-based diagnostics (semantic validation - only if no parse errors)
        // Works in both modes: validates paths against type environment
        if (parserDiagnostics.isEmpty()) {
            val typeEnv = stateManager.getTypeEnvironment(uri)
            if (typeEnv != null) {
                val pathDiagnostics = analyzer.analyze(text, typeEnv)
                diagnostics.addAll(pathDiagnostics)
            } else if (isDesignTime) {
                // In design-time mode, warn if no type environment (schema not loaded)
                logger.warn("Design-time mode active for $uri but no type environment. Load schema via utlx/loadSchema")
            }
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
     * Get parser-based diagnostics (syntax and structure errors)
     *
     * Also caches the AST for successful parses (needed for output schema inference)
     */
    private fun getParserDiagnostics(text: String, uri: String): List<Diagnostic> {
        return try {
            val lexer = Lexer(text)
            val tokens = lexer.tokenize()
            val parser = Parser(tokens)
            val parseResult = parser.parse()

            when (parseResult) {
                is ParseResult.Success -> {
                    // Cache the AST for output schema inference
                    stateManager.setAst(uri, parseResult.program)
                    logger.debug("Cached AST for $uri")
                    emptyList()
                }
                is ParseResult.Failure -> {
                    parseResult.errors.map { parseError ->
                        // Convert ParseError to LSP Diagnostic
                        Diagnostic(
                            range = Range(
                                start = Position(
                                    line = maxOf(0, parseError.location.line - 1), // 0-based
                                    character = maxOf(0, parseError.location.column - 1) // 0-based
                                ),
                                end = Position(
                                    line = maxOf(0, parseError.location.line - 1),
                                    character = maxOf(0, parseError.location.column + 10) // Highlight ~10 chars
                                )
                            ),
                            severity = DiagnosticSeverity.ERROR,
                            code = "UTLX_${parseError.section.name}",
                            source = "utlx-${parseError.section.displayName().lowercase()}",
                            message = parseError.message
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing document for diagnostics", e)
            emptyList()
        }
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
