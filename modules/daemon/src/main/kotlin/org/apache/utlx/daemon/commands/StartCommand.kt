// modules/server/src/main/kotlin/org/apache/utlx/server/commands/StartCommand.kt
package org.apache.utlx.daemon.commands

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
// import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import org.apache.utlx.daemon.UTLXDaemon
import org.apache.utlx.daemon.TransportType
import org.apache.utlx.daemon.CommandResult
import org.apache.utlx.daemon.config.DaemonConfig
import org.apache.utlx.daemon.mcp.*
import org.apache.utlx.daemon.session.SessionManager
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Start UTL-X daemon with LSP and/or REST API
 *
 * Supports concurrent requests from both LSP and REST API clients.
 */
object StartCommand {
    private val logger = LoggerFactory.getLogger(StartCommand::class.java)
    private val objectMapper = jacksonObjectMapper()
    private val sessionManager = SessionManager()
    private val mcpService = McpService()
    private val startTime = Instant.now()
    private val requestCounter = AtomicLong(0)

    fun execute(args: Array<String>): CommandResult {
        return try {
            // Parse command-line arguments
            val options = parseArguments(args)

            // Load configuration with CLI overrides
            val config = DaemonConfig.load(
                configPath = options["config"] as? String,
                cliOverrides = buildConfigOverrides(options)
            )

            // Validate transport configuration: only one service can use stdio
            val lspUsesStdio = config.server.lsp.enabled &&
                              (options["lsp"] as? Boolean != false) &&
                              config.server.lsp.transport == "stdio"
            val apiUsesStdio = false // REST API typically doesn't use stdio, but check for future

            if (lspUsesStdio && apiUsesStdio) {
                System.err.println("Error: Only one service can use stdio transport at a time")
                System.err.println("Please configure either LSP or REST API to use socket transport")
                return CommandResult.Failure("Conflicting stdio transport configuration", 1)
            }

            // Determine which services to enable
            val apiEnabled = options["api"] as? Boolean ?: false
            val lspEnabled = options["lsp"] as? Boolean ?: false

            if (!apiEnabled && !lspEnabled) {
                System.err.println("Error: No services enabled. Use --api or --lsp")
                return CommandResult.Failure("No services enabled", 1)
            }

            // Extract transport configuration to this scope so we can use it for logging
            // Determine LSP transport configuration
            val lspTransportString = if (lspEnabled) {
                options["server.lsp.transport"] as? String ?: config.server.lsp.transport
            } else {
                "socket"
            }

            val lspTransport = if (lspTransportString == "stdio") {
                TransportType.STDIO
            } else {
                TransportType.SOCKET
            }

            val lspPort = if (lspEnabled && lspTransport == TransportType.SOCKET) {
                options["server.lsp.socketPort"] as? Int ?: config.server.lsp.socketPort
            } else {
                0
            }

            // REST API always uses socket transport (port 7779)
            val apiPort = if (apiEnabled) {
                options["api-port"] as? Int ?: 7779
            } else {
                0
            }

            // Create a SINGLE UTLXDaemon instance with both LSP and/or API enabled
            val jobs = mutableListOf<Job>()
            jobs.add(startDaemon(config, options, lspEnabled, apiEnabled, lspTransport, lspPort, apiPort))

            println("UTL-X Daemon started successfully!")
            if (apiEnabled) {
                val apiHost = options["api-host"] as? String ?: "0.0.0.0"
                println("API: http://$apiHost:$apiPort")
            }
            if (lspEnabled) {
                if (lspTransport == TransportType.SOCKET) {
                    println("LSP: socket transport (port $lspPort)")
                } else {
                    println("LSP: JSON-RPC 2.0 over stdio (stdin/stdout)")
                }
            }
            println("Press Ctrl+C to stop")

            // Wait for all jobs to complete (they run indefinitely)
            runBlocking {
                jobs.forEach { it.join() }
            }

            CommandResult.Success
        } catch (e: Exception) {
            logger.error("Failed to start daemon: ${e.message}", e)
            System.err.println("Error starting daemon: ${e.message}")
            CommandResult.Failure(e.message ?: "Unknown error", 1)
        }
    }

    /**
     * Start REST API server with MCP endpoints
     */
    private fun startRestApiServer(config: DaemonConfig): Job {
        return GlobalScope.launch(Dispatchers.IO) {
            val server = embeddedServer(
                Netty,
                port = config.server.restApi.port,
                host = config.server.restApi.host
            ) {
                install(ContentNegotiation) {
                    jackson {
                        // Use default Jackson configuration
                    }
                }

                // TODO: Add CallLogging plugin once Ktor dependency is correct
                // install(CallLogging) {
                //     level = when (config.logging.level) {
                //         "DEBUG" -> org.slf4j.event.Level.DEBUG
                //         "INFO" -> org.slf4j.event.Level.INFO
                //         "WARN" -> org.slf4j.event.Level.WARN
                //         "ERROR" -> org.slf4j.event.Level.ERROR
                //         else -> org.slf4j.event.Level.INFO
                //     }
                // }

                install(CORS) {
                    if (config.server.restApi.cors.enabled) {
                        config.server.restApi.cors.allowedOrigins.forEach { origin ->
                            // Handle wildcard patterns
                            if (origin.contains("*")) {
                                anyHost()
                            } else {
                                // Parse origin to separate scheme and host
                                // Format: scheme://host or just host
                                val parts = origin.split("://")
                                if (parts.size == 2) {
                                    val scheme = parts[0]
                                    val host = parts[1]
                                    allowHost(host, schemes = listOf(scheme))
                                } else {
                                    // No scheme specified, allow both http and https
                                    allowHost(origin, schemes = listOf("http", "https"))
                                }
                            }
                        }
                        allowHeader(HttpHeaders.ContentType)
                        allowMethod(HttpMethod.Get)
                        allowMethod(HttpMethod.Post)
                        allowMethod(HttpMethod.Options)
                    }
                }

                routing {
                    configureMcpRoutes()
                }
            }

            server.start(wait = true)
        }
    }

    /**
     * Configure MCP REST API routes
     */
    private fun Routing.configureMcpRoutes() {
        // Health check
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }

        // MCP JSON-RPC 2.0 endpoint
        post("/mcp") {
            handleJsonRpcRequest(call)
        }

        // List available tools
        get("/mcp/tools") {
            try {
                val response = mcpService.listTools()
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error listing tools", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        // Transform endpoint
        post("/mcp/transform") {
            try {
                val request = call.receive<TransformRequest>()
                requestCounter.incrementAndGet()

                val response = mcpService.transform(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: McpException) {
                call.respond(HttpStatusCode.BadRequest, e.error)
            } catch (e: Exception) {
                logger.error("Transform error", e)
                call.respond(HttpStatusCode.InternalServerError,
                    JsonRpcError.internalError(e.message ?: "Unknown error"))
            }
        }

        // Validate endpoint
        post("/mcp/validate") {
            try {
                val request = call.receive<ValidateRequest>()
                requestCounter.incrementAndGet()

                val response = mcpService.validate(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: McpException) {
                call.respond(HttpStatusCode.BadRequest, e.error)
            } catch (e: Exception) {
                logger.error("Validate error", e)
                call.respond(HttpStatusCode.InternalServerError,
                    JsonRpcError.internalError(e.message ?: "Unknown error"))
            }
        }

        // Generate schema endpoint
        post("/mcp/schema/generate") {
            try {
                val request = call.receive<GenerateSchemaRequest>()
                requestCounter.incrementAndGet()

                val response = mcpService.generateSchema(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: McpException) {
                call.respond(HttpStatusCode.BadRequest, e.error)
            } catch (e: Exception) {
                logger.error("Schema generation error", e)
                call.respond(HttpStatusCode.InternalServerError,
                    JsonRpcError.internalError(e.message ?: "Unknown error"))
            }
        }

        // Server status
        get("/mcp/status") {
            try {
                val uptime = java.time.Duration.between(startTime, Instant.now()).seconds
                val response = mcpService.getStatus(
                    uptime = uptime,
                    activeSessions = sessionManager.getSessionCount(),
                    totalRequests = requestCounter.get()
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Status error", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        // Session management
        post("/mcp/session/create") {
            try {
                val metadata = try {
                    call.receive<Map<String, Any>>()
                } catch (e: Exception) {
                    emptyMap()
                }

                val session = sessionManager.createSession(metadata = metadata)
                call.respond(HttpStatusCode.Created, session.getStats())
            } catch (e: Exception) {
                logger.error("Session creation error", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        get("/mcp/sessions") {
            try {
                val sessions = sessionManager.getAllSessions()
                val stats = sessions.map { it.getStats() }
                call.respond(HttpStatusCode.OK, mapOf("sessions" to stats))
            } catch (e: Exception) {
                logger.error("Sessions list error", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        // TODO: Implement Server-Sent Events when Ktor 3.x is available
        // SSE support is better in Ktor 3.0+
        get("/mcp/events") {
            call.respond(HttpStatusCode.NotImplemented, mapOf(
                "message" to "SSE support coming in future version",
                "alternative" to "Use polling on /mcp/status endpoint"
            ))
        }
    }

    /**
     * Handle JSON-RPC 2.0 request
     */
    private suspend fun handleJsonRpcRequest(call: ApplicationCall) {
        try {
            val requestText = call.receiveText()
            val request = objectMapper.readValue<JsonRpcRequest>(requestText)
            requestCounter.incrementAndGet()

            val result = when (request.method) {
                "tools/list" -> mcpService.listTools()
                "transform" -> {
                    val params = objectMapper.convertValue(request.params, TransformRequest::class.java)
                    mcpService.transform(params)
                }
                "validate" -> {
                    val params = objectMapper.convertValue(request.params, ValidateRequest::class.java)
                    mcpService.validate(params)
                }
                "schema/generate" -> {
                    val params = objectMapper.convertValue(request.params, GenerateSchemaRequest::class.java)
                    mcpService.generateSchema(params)
                }
                else -> throw McpException(JsonRpcError.methodNotFound("Unknown method: ${request.method}"))
            }

            val response = JsonRpcResponse(id = request.id, result = result)
            call.respond(HttpStatusCode.OK, response)

        } catch (e: McpException) {
            call.respond(
                HttpStatusCode.OK,
                JsonRpcResponse(id = null, error = e.error)
            )
        } catch (e: Exception) {
            logger.error("JSON-RPC error", e)
            call.respond(
                HttpStatusCode.OK,
                JsonRpcResponse(
                    id = null,
                    error = JsonRpcError.internalError(e.message ?: "Unknown error")
                )
            )
        }
    }

    /**
     * Start a single UTLXDaemon instance with LSP and/or API enabled
     *
     * Architecture:
     * - ONE UTLXDaemon instance can run both LSP and REST API simultaneously
     * - LSP transport: STDIO or Socket on port 7777 (for IDE integration)
     * - REST API: Socket on port 7779 only (for MCP Server and other clients)
     * - Both services share the same daemon instance and state manager
     */
    private fun startDaemon(
        config: DaemonConfig,
        options: Map<String, Any>,
        lspEnabled: Boolean,
        apiEnabled: Boolean,
        lspTransport: TransportType,
        lspPort: Int,
        apiPort: Int
    ): Job {
        return GlobalScope.launch(Dispatchers.IO) {
            try {
                // Transport configuration
                // REST API always uses socket transport (port 7779)
                // LSP can use either stdio or socket (port 7777)
                val daemonTransport: TransportType
                val daemonPort: Int

                when {
                    lspTransport == TransportType.STDIO -> {
                        // LSP uses stdio
                        daemonTransport = TransportType.STDIO
                        daemonPort = 0
                    }
                    lspEnabled -> {
                        // LSP uses socket
                        daemonTransport = lspTransport
                        daemonPort = lspPort
                    }
                    else -> {
                        // API-only mode (API always uses socket)
                        daemonTransport = TransportType.SOCKET
                        daemonPort = 0  // No LSP port needed
                    }
                }

                logger.info("Starting UTL-X Daemon:")
                if (lspEnabled) {
                    logger.info("  LSP: $lspTransport transport" +
                        if (lspTransport == TransportType.SOCKET) " (port $lspPort)" else " (JSON-RPC 2.0 over stdin/stdout)")
                }
                if (apiEnabled) {
                    logger.info("  REST API: HTTP server on port $apiPort")
                }

                // Create single daemon instance with both services
                val daemon = UTLXDaemon(
                    transportType = daemonTransport,
                    port = daemonPort,
                    enableRestApi = apiEnabled,
                    restApiPort = apiPort,
                    enableLsp = lspEnabled
                )

                daemon.start()
            } catch (e: Exception) {
                logger.error("Daemon failed", e)
                throw e
            }
        }
    }

    /**
     * Parse command-line arguments
     */
    private fun parseArguments(args: Array<String>): Map<String, Any> {
        val options = mutableMapOf<String, Any>()
        var i = 0

        while (i < args.size) {
            when (args[i]) {
                // API parameters (port 7779 - used by MCP Server)
                "--api" -> options["api"] = true
                "--api-port" -> {
                    i++
                    options["api-port"] = args[i].toInt()
                }

                // LSP parameters
                "--lsp" -> options["lsp"] = true
                "--lsp-port" -> {
                    i++
                    options["server.lsp.socketPort"] = args[i].toInt()
                }
                "--lsp-transport" -> {
                    i++
                    options["server.lsp.transport"] = args[i]
                }

                // Other options
                "--host" -> {
                    i++
                    options["api-host"] = args[i]
                }
                "--log-level" -> {
                    i++
                    options["logging.level"] = args[i]
                }
                "--config" -> {
                    i++
                    options["config"] = args[i]
                }
                "--help", "-h" -> {
                    printHelp()
                    System.exit(0)
                }
            }
            i++
        }

        return options
    }

    /**
     * Build configuration overrides from CLI options
     */
    private fun buildConfigOverrides(options: Map<String, Any>): Map<String, Any> {
        return options.filterKeys { it != "config" && it != "rest-api" && it != "lsp" }
    }

    /**
     * Print help message
     */
    private fun printHelp() {
        println("""
            |UTL-X Daemon - Start Command
            |
            |Usage: utlxd start [options]
            |
            |Options:
            |  --api                   Enable REST API server (HTTP endpoints for MCP Server)
            |  --api-port PORT         REST API port (default: 7779)
            |  --host HOST             REST API host (default: 0.0.0.0)
            |
            |  --lsp                   Enable LSP server (Language Server for IDE/Theia)
            |  --lsp-port PORT         LSP socket port (default: 7777, ignored if --lsp-transport stdio)
            |  --lsp-transport TYPE    LSP transport: stdio|socket (default: socket)
            |
            |  --log-level LEVEL       Logging level: DEBUG|INFO|WARN|ERROR (default: INFO)
            |  --config PATH           Configuration file path
            |  --help, -h              Show this help message
            |
            |Transport Details:
            |  LSP Transport:
            |    - socket: LSP JSON-RPC over TCP socket on port 7777 (default)
            |    - stdio:  LSP JSON-RPC over standard input/output (for IDE plugins)
            |
            |  REST API Transport:
            |    - socket: HTTP server on TCP port 7779 (default, fully implemented)
            |    - stdio:  JSON-RPC 2.0 over stdin/stdout (EXPERIMENTAL - basic infrastructure implemented,
            |                                                JSON-RPC method handlers need to be added to UTLXDaemon)
            |
            |  Note: When using stdio, only ONE service can use stdio at a time.
            |        The other service must use socket transport.
            |        Currently recommended: Use socket transport for REST API.
            |
            |Examples:
            |  # Start with both LSP and API - all defaults (socket transport, default ports)
            |  utlxd start --lsp --api
            |
            |  # Start with LSP on socket, API on socket (explicit)
            |  utlxd start --lsp --lsp-transport socket --lsp-port 7777 --api --api-port 7779
            |
            |  # Start with LSP on stdio, API on socket
            |  utlxd start --lsp --lsp-transport stdio --api --api-port 7779
            |
            |  # Start with LSP only (STDIO transport, for IDE plugins)
            |  utlxd start --lsp --lsp-transport stdio
            |
            |  # Start with API only (socket transport on port 7779, for MCP Server)
            |  utlxd start --api
            |
            |  # Use custom configuration file
            |  utlxd start --config /etc/utlx/production.yaml
            |
            |Architecture:
            |  - UTLXDaemon: Single daemon instance that can host both LSP and REST API
            |  - LSP Server: JSON-RPC 2.0 protocol for IDE integration (Theia, VS Code, etc.)
            |    * Supports stdio (standard streams) or socket (TCP port 7777)
            |  - REST API: HTTP/JSON endpoints for MCP Server integration
            |    * Currently only supports socket (TCP port 7779)
            |  - Both services share the same transformation engine and state manager
            |
            |Configuration Precedence:
            |  1. Command-line arguments (highest)
            |  2. Environment variables (UTLXD_*)
            |  3. Configuration file (~/.utlx/config.yaml)
            |  4. Built-in defaults (lowest)
        """.trimMargin())
    }
}
