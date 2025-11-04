// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/rest/RestApiServer.kt
package org.apache.utlx.daemon.rest

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * REST API server for UTL-X daemon
 * Provides HTTP endpoints for MCP server integration
 */
class RestApiServer(
    private val port: Int = 7779,
    private val host: String = "0.0.0.0"
) {
    private val logger = LoggerFactory.getLogger(RestApiServer::class.java)
    private var server: NettyApplicationEngine? = null
    private val startTime = Instant.now().toEpochMilli()
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Start the REST API server
     */
    fun start() {
        logger.info("Starting REST API server on $host:$port")

        server = embeddedServer(Netty, port = port, host = host) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
            }

            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    logger.error("Unhandled exception", cause)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            error = "internal_error",
                            message = cause.message ?: "Unknown error"
                        )
                    )
                }
            }

            routing {
                // Health check endpoint
                get("/api/health") {
                    call.respond(
                        HealthResponse(
                            status = "ok",
                            version = "1.0.0-SNAPSHOT",
                            uptime = System.currentTimeMillis() - startTime
                        )
                    )
                }

                // Validation endpoint
                post("/api/validate") {
                    val request = call.receive<ValidationRequest>()
                    logger.debug("Validation request: strict=${request.strict}")

                    // TODO: Implement actual validation logic
                    call.respond(
                        ValidationResponse(
                            valid = true,
                            diagnostics = emptyList()
                        )
                    )
                }

                // Execution endpoint
                post("/api/execute") {
                    val request = call.receive<ExecutionRequest>()
                    logger.debug("Execution request: inputFormat=${request.inputFormat}, outputFormat=${request.outputFormat}")

                    val startExec = System.currentTimeMillis()

                    // TODO: Implement actual execution logic
                    call.respond(
                        ExecutionResponse(
                            success = true,
                            output = "{}",
                            executionTimeMs = System.currentTimeMillis() - startExec
                        )
                    )
                }

                // Schema inference endpoint
                post("/api/infer-schema") {
                    val request = call.receive<InferSchemaRequest>()
                    logger.debug("Schema inference request: format=${request.format}")

                    // TODO: Implement actual schema inference logic
                    call.respond(
                        InferSchemaResponse(
                            success = true,
                            schema = "{}",
                            schemaFormat = request.format
                        )
                    )
                }

                // Schema parsing endpoint
                post("/api/parse-schema") {
                    val request = call.receive<ParseSchemaRequest>()
                    logger.debug("Parse schema request: format=${request.format}")

                    // TODO: Implement actual schema parsing logic
                    call.respond(
                        ParseSchemaResponse(
                            success = true,
                            normalized = "{}"
                        )
                    )
                }
            }
        }

        scope.launch {
            server?.start(wait = false)
            logger.info("REST API server started successfully on $host:$port")
        }
    }

    /**
     * Stop the REST API server
     */
    fun stop() {
        logger.info("Stopping REST API server")
        server?.stop(1000, 2000)
        logger.info("REST API server stopped")
    }
}
