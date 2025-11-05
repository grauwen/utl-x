// modules/server/src/main/kotlin/org/apache/utlx/server/commands/StartCommand.kt
package org.apache.utlx.server.commands

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
import org.apache.utlx.server.CommandResult
import org.apache.utlx.server.config.DaemonConfig
import org.apache.utlx.server.mcp.*
import org.apache.utlx.server.session.SessionManager
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

            // Start services based on configuration
            val jobs = mutableListOf<Job>()

            // Start API server if enabled (port 7779 - used by MCP Server)
            val apiEnabled = options["api"] as? Boolean ?: false
            if (apiEnabled) {
                val apiPort = options["api-port"] as? Int ?: 7779
                val apiHost = options["api-host"] as? String ?: "0.0.0.0"
                val apiTransport = options["api-transport"] as? String ?: "socket"

                logger.info("Starting API server on port $apiPort with $apiTransport transport")
                jobs.add(startApiServer(apiPort, apiHost, apiTransport))
            }

            // Start LSP server if enabled
            val lspEnabled = options["lsp"] as? Boolean ?: false
            if (lspEnabled) {
                logger.info("Starting LSP server with ${config.server.lsp.transport} transport")
                jobs.add(startLspServer(config))
            }

            if (jobs.isEmpty()) {
                System.err.println("Error: No services enabled. Use --api or --lsp")
                return CommandResult.Failure("No services enabled", 1)
            }

            println("UTL-X Daemon started successfully!")
            if (apiEnabled) {
                val apiPort = options["api-port"] as? Int ?: 7779
                val apiHost = options["api-host"] as? String ?: "0.0.0.0"
                println("API: http://$apiHost:$apiPort")
            }
            if (lspEnabled) {
                println("LSP: ${config.server.lsp.transport} transport")
                if (config.server.lsp.transport == "socket") {
                    println("LSP Port: ${config.server.lsp.socketPort}")
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
     * Start standalone API server (port 7779 - used by MCP Server)
     */
    private fun startApiServer(port: Int, host: String, transport: String): Job {
        return GlobalScope.launch(Dispatchers.IO) {
            try {
                val transportType = if (transport == "stdio") {
                    TransportType.STDIO
                } else {
                    TransportType.SOCKET
                }

                val daemon = UTLXDaemon(
                    transportType = transportType,
                    port = port,
                    enableRestApi = true,
                    restApiPort = port
                )

                daemon.start()
            } catch (e: Exception) {
                logger.error("API server failed", e)
                throw e
            }
        }
    }

    /**
     * Start LSP server (port 7777 - used by Theia extension)
     */
    private fun startLspServer(config: DaemonConfig): Job {
        return GlobalScope.launch(Dispatchers.IO) {
            try {
                val transport = if (config.server.lsp.transport == "stdio") {
                    TransportType.STDIO
                } else {
                    TransportType.SOCKET
                }

                val daemon = UTLXDaemon(
                    transportType = transport,
                    port = config.server.lsp.socketPort,
                    enableRestApi = false,
                    restApiPort = 0
                )

                daemon.start()
            } catch (e: Exception) {
                logger.error("LSP server failed", e)
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
                "--api-transport" -> {
                    i++
                    options["api-transport"] = args[i]
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
            |  --api                   Enable API server (used by MCP Server)
            |  --api-port PORT         API port (default: 7779)
            |  --api-transport TYPE    API transport: stdio|socket (default: socket)
            |  --host HOST             API host (default: 0.0.0.0)
            |
            |  --lsp                   Enable LSP server (used by IDE/Theia)
            |  --lsp-port PORT         LSP socket port (default: 7777)
            |  --lsp-transport TYPE    LSP transport: stdio|socket (default: socket)
            |
            |  --log-level LEVEL       Logging level: DEBUG|INFO|WARN|ERROR (default: INFO)
            |  --config PATH           Configuration file path
            |  --help, -h              Show this help message
            |
            |Important Notes:
            |  - Only ONE service can use stdio transport at a time (stdio is not multiplexable)
            |  - If LSP uses stdio, API must use socket, and vice versa
            |
            |Examples:
            |  # Start with both LSP and API - all defaults (socket transport, default ports)
            |  utlxd start --lsp [--lsp-transport socket|stdio] [--lsp-port 7777] --api [--api-transport socket|stdio] [--api-port 7779]
            |
            |  # Start with LSP only (STDIO transport)
            |  utlxd start --lsp --lsp-transport stdio
            |
            |  # Start with API only (default socket transport and port)
            |  utlxd start --api [--api-transport socket|stdio] [--api-port 7779]
            |
            |  # Use custom configuration file
            |  utlxd start --config /etc/utlx/production.yaml
            |
            |Architecture:
            |  - API (port 7779): REST API used by MCP Server for transformations
            |  - LSP (port 7777): Language Server Protocol for IDE integration (Theia)
            |
            |Configuration Precedence:
            |  1. Command-line arguments (highest)
            |  2. Environment variables (UTLXD_*)
            |  3. Configuration file (~/.utlx/config.yaml)
            |  4. Built-in defaults (lowest)
        """.trimMargin())
    }
}
