package org.apache.utlx.engine.health

import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.utlx.engine.EngineState
import org.apache.utlx.engine.UtlxEngine
import org.slf4j.LoggerFactory
import java.net.BindException

class HealthEndpoint(
    private val engine: UtlxEngine,
    private val maxRetries: Int = MAX_PORT_RETRIES
) {

    private val logger = LoggerFactory.getLogger(HealthEndpoint::class.java)
    private var server: ApplicationEngine? = null
    private val requestedPort = engine.config.engine.monitoring.health.port

    var boundPort: Int = requestedPort
        private set

    fun start() {
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            val tryPort = requestedPort + attempt
            try {
                logger.info("Trying health endpoint on port {}", tryPort)
                server = embeddedServer(Netty, port = tryPort, host = "0.0.0.0") {
                    configureHealth(engine)
                }.start(wait = false)

                boundPort = tryPort
                if (attempt > 0) {
                    logger.warn(
                        "Port {} was in use. Health endpoint bound to port {} instead (requested: {})",
                        requestedPort, tryPort, requestedPort
                    )
                } else {
                    logger.info("Health endpoint started on port {}", tryPort)
                }
                return
            } catch (e: Exception) {
                if (isPortInUse(e)) {
                    logger.debug("Port {} in use, trying next", tryPort)
                    lastException = e
                } else {
                    throw e
                }
            }
        }

        throw BindException(
            "Health endpoint failed to bind: ports $requestedPort-${requestedPort + maxRetries} " +
                "all in use. Last error: ${lastException?.message}"
        )
    }

    fun stop() {
        logger.info("Stopping health endpoint on port {}", boundPort)
        server?.stop(1000, 2000)
    }

    companion object {
        const val MAX_PORT_RETRIES = 10

        private fun isPortInUse(e: Exception): Boolean {
            var cause: Throwable? = e
            while (cause != null) {
                if (cause is BindException) return true
                if (cause.message?.contains("Address already in use") == true) return true
                cause = cause.cause
            }
            return false
        }
    }
}

fun Application.configureHealth(engine: UtlxEngine) {
    install(ContentNegotiation) {
        jackson {
            registerModule(kotlinModule())
        }
    }
    routing {
        get("/health") {
            val transformations = engine.registry.list().associate { tx ->
                tx.name to mapOf(
                    "status" to engine.state.name,
                    "strategy" to tx.strategy.name
                )
            }

            val response = mapOf(
                "status" to if (engine.state == EngineState.RUNNING) "UP" else "DOWN",
                "engine" to mapOf(
                    "state" to engine.state.name,
                    "uptime" to formatUptime(engine.uptimeMs()),
                    "transformations" to engine.registry.list().size
                ),
                "transformations" to transformations
            )

            val statusCode = if (engine.state == EngineState.RUNNING)
                HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(statusCode, response)
        }

        get("/health/live") {
            call.respond(HttpStatusCode.OK, mapOf(
                "status" to "UP"
            ))
        }

        // Prometheus metrics endpoint — always on, zero overhead
        get("/metrics") {
            val metricsText = MetricsCollector.collect(engine)
            call.respondText(metricsText, ContentType("text", "plain", listOf(
                HeaderValueParam("version", "0.0.4"),
                HeaderValueParam("charset", "utf-8")
            )))
        }

        get("/health/ready") {
            if (engine.state == EngineState.RUNNING) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to "UP",
                    "state" to engine.state.name
                ))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                    "status" to "DOWN",
                    "state" to engine.state.name
                ))
            }
        }
    }
}

private fun formatUptime(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}
