// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/DaemonServer.kt
package org.apache.utlx.daemon

import org.apache.utlx.daemon.protocol.*
import org.apache.utlx.daemon.state.StateManager
import org.apache.utlx.daemon.state.SchemaFormat
import org.apache.utlx.daemon.state.DocumentMode
import org.apache.utlx.daemon.transport.SocketTransport
import org.apache.utlx.daemon.transport.StdioTransport
import org.apache.utlx.daemon.transport.Transport
import org.apache.utlx.daemon.completion.*
import org.apache.utlx.daemon.hover.*
import org.apache.utlx.daemon.diagnostics.*
import org.apache.utlx.daemon.schema.SchemaTypeContextFactory
import org.apache.utlx.daemon.schema.OutputSchemaInferenceService
import org.apache.utlx.daemon.schema.InferenceResult
import org.apache.utlx.daemon.rest.RestApiServer
import org.apache.utlx.analysis.schema.XSDSchemaParser
import org.apache.utlx.analysis.schema.JSONSchemaParser
import org.apache.utlx.analysis.schema.SchemaFormat as AnalysisSchemaFormat
import org.slf4j.LoggerFactory

/**
 * UTL-X Daemon: Long-running LSP server for design-time analysis
 *
 * Architecture:
 * - Daemon = Long-running background process (architectural pattern)
 * - LSP = Language Server Protocol using JSON-RPC 2.0 (communication protocol)
 * - REST API = HTTP/JSON API for MCP integration (always uses HTTP server on socket)
 * - STDIO/Socket = Transport mechanism (physical layer)
 *
 * The daemon supports dual-mode operation:
 * - LSP server via STDIO or Socket transport
 * - REST API server via HTTP socket only (port 7779)
 *
 * Transport configuration:
 * - When LSP uses stdio: daemon transport = STDIO, handles LSP JSON-RPC methods
 * - When LSP uses socket: daemon transport = SOCKET (for LSP)
 * - REST API always uses separate HTTP server on socket (port 7779)
 */
class UTLXDaemon(
    private val transportType: TransportType = TransportType.STDIO,
    private val port: Int = 7777,
    private val enableRestApi: Boolean = false,
    private val restApiPort: Int = 7779,
    private val enableLsp: Boolean = true
) {

    private val logger = LoggerFactory.getLogger(UTLXDaemon::class.java)
    private val stateManager = StateManager()
    private val completionService = CompletionService(stateManager)
    private val hoverService = HoverService(stateManager)
    private val diagnosticsPublisher = DiagnosticsPublisher(stateManager)
    private val schemaTypeContextFactory = SchemaTypeContextFactory()
    private val outputSchemaService = OutputSchemaInferenceService(stateManager)

    private var transport: Transport? = null
    private var restApiServer: RestApiServer? = null
    private var initialized = false

    /**
     * Start the daemon server
     */
    fun start() {
        logger.info("Starting UTL-X Daemon")
        logger.info("  LSP enabled: $enableLsp")
        logger.info("  REST API enabled: $enableRestApi")
        logger.info("  Daemon transport: $transportType")

        // Start REST API HTTP server if enabled (always uses socket)
        if (enableRestApi) {
            logger.info("Starting REST API HTTP server on port $restApiPort")
            restApiServer = RestApiServer(port = restApiPort)
            restApiServer!!.start()
        }

        // Create transport based on type (for LSP)
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

        // Set transport for diagnostics publisher (only needed for LSP)
        if (enableLsp) {
            diagnosticsPublisher.setTransport(transport!!)
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
        restApiServer?.stop()
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

                // Language features
                "textDocument/completion" -> handleCompletion(request)
                "textDocument/hover" -> handleHover(request)

                // UTL-X custom methods - Schema loading and mode switching
                "utlx/loadSchema" -> handleLoadSchema(request)
                "utlx/setMode" -> handleSetMode(request)
                "utlx/inferOutputSchema" -> handleInferOutputSchema(request)

                // UTL-X custom methods (future features)
                "utlx/complete" -> notImplemented(request, "Path completion not yet implemented")
                "utlx/graph" -> notImplemented(request, "Graph API not yet implemented")
                "utlx/visualize" -> notImplemented(request, "Visualization not yet implemented")

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
                // Phase 2 features
                "completionProvider" to mapOf(
                    "triggerCharacters" to listOf(".", "$")
                ),
                "hoverProvider" to true  // Implemented in Phase 2
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

        // Publish diagnostics for the newly opened document
        diagnosticsPublisher.publishDiagnostics(uri)

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

        // Publish diagnostics for the updated document
        diagnosticsPublisher.publishDiagnostics(uri)

        return JsonRpcResponse.success(request.id, null)
    }

    /**
     * Handle textDocument/completion request
     */
    private fun handleCompletion(request: JsonRpcRequest): JsonRpcResponse {
        @Suppress("UNCHECKED_CAST")
        val params = request.params as? Map<*, *>
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing params")

        try {
            // Parse LSP completion params
            @Suppress("UNCHECKED_CAST")
            val textDocument = params["textDocument"] as? Map<*, *>
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing textDocument")

            val uri = textDocument["uri"] as? String
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing uri")

            @Suppress("UNCHECKED_CAST")
            val positionMap = params["position"] as? Map<*, *>
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing position")

            val line = (positionMap["line"] as? Number)?.toInt()
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing line")
            val character = (positionMap["character"] as? Number)?.toInt()
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing character")

            // Parse context (optional)
            @Suppress("UNCHECKED_CAST")
            val contextMap = params["context"] as? Map<*, *>
            val context = if (contextMap != null) {
                val triggerKind = (contextMap["triggerKind"] as? Number)?.toInt() ?: 1
                val triggerChar = contextMap["triggerCharacter"] as? String
                CompletionContext(
                    triggerKind = CompletionTriggerKind.values().firstOrNull { it.value == triggerKind }
                        ?: CompletionTriggerKind.INVOKED,
                    triggerCharacter = triggerChar
                )
            } else null

            // Create completion params
            val completionParams = CompletionParams(
                textDocument = TextDocumentIdentifier(uri),
                position = Position(line, character),
                context = context
            )

            // Get completions
            val completionList = completionService.getCompletions(completionParams)

            // Convert to LSP format
            val result = mapOf(
                "isIncomplete" to completionList.isIncomplete,
                "items" to completionList.items.map { item ->
                    mapOf(
                        "label" to item.label,
                        "kind" to item.kind.value,
                        "detail" to item.detail,
                        "documentation" to item.documentation,
                        "insertText" to item.insertText,
                        "sortText" to item.sortText,
                        "filterText" to item.filterText
                    )
                }
            )

            logger.debug("Returning ${completionList.items.size} completion items")

            return JsonRpcResponse.success(request.id, result)
        } catch (e: Exception) {
            logger.error("Error handling completion request", e)
            return JsonRpcResponse.internalError(
                request.id,
                "Error processing completion: ${e.message}"
            )
        }
    }

    /**
     * Handle textDocument/hover request
     */
    private fun handleHover(request: JsonRpcRequest): JsonRpcResponse {
        @Suppress("UNCHECKED_CAST")
        val params = request.params as? Map<*, *>
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing params")

        try {
            // Parse LSP hover params
            @Suppress("UNCHECKED_CAST")
            val textDocument = params["textDocument"] as? Map<*, *>
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing textDocument")

            val uri = textDocument["uri"] as? String
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing uri")

            @Suppress("UNCHECKED_CAST")
            val positionMap = params["position"] as? Map<*, *>
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing position")

            val line = (positionMap["line"] as? Number)?.toInt()
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing line")
            val character = (positionMap["character"] as? Number)?.toInt()
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing character")

            // Create hover params
            val hoverParams = HoverParams(
                textDocument = TextDocumentIdentifier(uri),
                position = Position(line, character)
            )

            // Get hover information
            val hover = hoverService.getHover(hoverParams)

            // If no hover information available, return null result
            if (hover == null) {
                return JsonRpcResponse.success(request.id, null)
            }

            // Convert to LSP format
            val result = mutableMapOf<String, Any>(
                "contents" to mapOf(
                    "kind" to hover.contents.kind.value,
                    "value" to hover.contents.value
                )
            )

            // Add range if available
            hover.range?.let { range ->
                result["range"] = mapOf(
                    "start" to mapOf(
                        "line" to range.start.line,
                        "character" to range.start.character
                    ),
                    "end" to mapOf(
                        "line" to range.end.line,
                        "character" to range.end.character
                    )
                )
            }

            logger.debug("Returning hover information for $uri")

            return JsonRpcResponse.success(request.id, result)
        } catch (e: Exception) {
            logger.error("Error handling hover request", e)
            return JsonRpcResponse.internalError(
                request.id,
                "Error processing hover: ${e.message}"
            )
        }
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

        // Clear diagnostics for closed document
        diagnosticsPublisher.clearDiagnostics(uri)

        return JsonRpcResponse.success(request.id, null)
    }

    /**
     * Handle utlx/loadSchema request
     *
     * Load an external schema (XSD or JSON Schema) for design-time type checking.
     *
     * Request params:
     * {
     *   "uri": "file:///path/to/document.utlx",  // Document URI to associate schema with
     *   "schemaContent": "<?xml version=\"1.0\"?>...",  // Schema content as string
     *   "format": "xsd" | "jsonschema"  // Schema format
     * }
     *
     * Response:
     * {
     *   "success": true,
     *   "message": "Schema loaded successfully",
     *   "typeCount": 15  // Number of types parsed
     * }
     */
    private fun handleLoadSchema(request: JsonRpcRequest): JsonRpcResponse {
        @Suppress("UNCHECKED_CAST")
        val params = request.params as? Map<*, *>
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing params")

        try {
            // Parse parameters
            val uri = params["uri"] as? String
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing 'uri' parameter")

            val schemaContent = params["schemaContent"] as? String
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing 'schemaContent' parameter")

            val formatStr = params["format"] as? String
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing 'format' parameter")

            logger.info("Loading schema for $uri (format: $formatStr)")

            // Parse format
            val analysisFormat = when (formatStr.lowercase()) {
                "xsd" -> AnalysisSchemaFormat.XSD
                "jsonschema", "json-schema", "json_schema" -> AnalysisSchemaFormat.JSON_SCHEMA
                else -> return JsonRpcResponse.invalidParams(
                    request.id,
                    "Invalid format '$formatStr'. Must be 'xsd' or 'jsonschema'"
                )
            }

            val daemonFormat = when (formatStr.lowercase()) {
                "xsd" -> SchemaFormat.XSD
                "jsonschema", "json-schema", "json_schema" -> SchemaFormat.JSON_SCHEMA
                else -> SchemaFormat.UNKNOWN
            }

            // Parse schema using appropriate parser
            val typeDef = when (analysisFormat) {
                AnalysisSchemaFormat.XSD -> {
                    val parser = XSDSchemaParser()
                    parser.parse(schemaContent, analysisFormat)
                }
                AnalysisSchemaFormat.JSON_SCHEMA -> {
                    val parser = JSONSchemaParser()
                    parser.parse(schemaContent, analysisFormat)
                }
                else -> return JsonRpcResponse.error(
                    request.id,
                    ErrorCode.INVALID_PARAMS,
                    "Unsupported schema format: $formatStr"
                )
            }

            // Convert TypeDefinition to TypeContext
            val typeContext = schemaTypeContextFactory.createFromSchema(typeDef)

            // Register schema in state manager
            stateManager.registerSchema(uri, schemaContent, daemonFormat)

            // Set type environment for this document
            stateManager.setTypeEnvironment(uri, typeContext)

            logger.info("Schema loaded successfully for $uri")

            // Re-publish diagnostics with new type environment
            diagnosticsPublisher.publishDiagnostics(uri)

            // Return success response
            val result = mapOf(
                "success" to true,
                "message" to "Schema loaded successfully",
                "format" to formatStr,
                "typeInfo" to typeDef::class.simpleName
            )

            return JsonRpcResponse.success(request.id, result)

        } catch (e: Exception) {
            logger.error("Error loading schema", e)
            return JsonRpcResponse.internalError(
                request.id,
                "Failed to load schema: ${e.message}"
            )
        }
    }

    /**
     * Handle utlx/setMode request
     *
     * Switch between design-time and runtime modes for a document.
     *
     * Request params:
     * {
     *   "uri": "file:///path/to/document.utlx",  // Document URI
     *   "mode": "design-time" | "runtime"  // Mode to set
     * }
     *
     * Response:
     * {
     *   "success": true,
     *   "mode": "design-time",
     *   "message": "Mode switched to design-time"
     * }
     */
    private fun handleSetMode(request: JsonRpcRequest): JsonRpcResponse {
        @Suppress("UNCHECKED_CAST")
        val params = request.params as? Map<*, *>
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing params")

        try {
            // Parse parameters
            val uri = params["uri"] as? String
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing 'uri' parameter")

            val modeStr = params["mode"] as? String
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing 'mode' parameter")

            logger.info("Setting mode for $uri: $modeStr")

            // Parse mode
            val mode = when (modeStr.lowercase()) {
                "design-time", "design_time", "designtime" -> DocumentMode.DESIGN_TIME
                "runtime", "run-time", "run_time" -> DocumentMode.RUNTIME
                else -> return JsonRpcResponse.invalidParams(
                    request.id,
                    "Invalid mode '$modeStr'. Must be 'design-time' or 'runtime'"
                )
            }

            // Set mode in state manager
            stateManager.setDocumentMode(uri, mode)

            logger.info("Mode set successfully for $uri: $mode")

            // Re-publish diagnostics with mode awareness
            diagnosticsPublisher.publishDiagnostics(uri)

            // Return success response
            val result = mapOf(
                "success" to true,
                "mode" to modeStr,
                "message" to "Mode switched to ${mode.name.lowercase().replace("_", "-")}"
            )

            return JsonRpcResponse.success(request.id, result)

        } catch (e: Exception) {
            logger.error("Error setting mode", e)
            return JsonRpcResponse.internalError(
                request.id,
                "Failed to set mode: ${e.message}"
            )
        }
    }

    /**
     * Handle utlx/inferOutputSchema request
     *
     * Generate JSON Schema for the output of a UTL-X transformation.
     * This is used in design-time mode to understand the structure of
     * transformed data without executing the transformation.
     *
     * Request params:
     * {
     *   "uri": "file:///path/to/document.utlx",  // Document URI
     *   "pretty": true,  // Optional: pretty-print schema (default: true)
     *   "includeComments": true  // Optional: include comments (default: true)
     * }
     *
     * Response:
     * {
     *   "success": true,
     *   "schema": "{\"$schema\": \"https://json-schema.org/...\", ...}"
     * }
     */
    private fun handleInferOutputSchema(request: JsonRpcRequest): JsonRpcResponse {
        @Suppress("UNCHECKED_CAST")
        val params = request.params as? Map<*, *>
            ?: return JsonRpcResponse.invalidParams(request.id, "Missing params")

        try {
            // Parse parameters
            val uri = params["uri"] as? String
                ?: return JsonRpcResponse.invalidParams(request.id, "Missing 'uri' parameter")

            val pretty = params["pretty"] as? Boolean ?: true
            val includeComments = params["includeComments"] as? Boolean ?: true

            logger.info("Inferring output schema for $uri")

            // Use service to infer schema with validation
            val result = outputSchemaService.inferOutputSchemaWithValidation(uri, pretty, includeComments)

            return when (result) {
                is InferenceResult.Success -> {
                    logger.info("Successfully inferred output schema for $uri")

                    val response = mapOf(
                        "success" to true,
                        "schema" to result.schema
                    )

                    JsonRpcResponse.success(request.id, response)
                }
                is InferenceResult.Failure -> {
                    logger.warn("Failed to infer output schema for $uri: ${result.error}")

                    JsonRpcResponse.error(
                        request.id,
                        ErrorCode.INTERNAL_ERROR,
                        result.error
                    )
                }
            }

        } catch (e: Exception) {
            logger.error("Error inferring output schema", e)
            return JsonRpcResponse.internalError(
                request.id,
                "Failed to infer output schema: ${e.message}"
            )
        }
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
