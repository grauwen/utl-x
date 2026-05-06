package org.apache.utlx.engine.admin

import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.protobuf.ByteString
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.proto.*
import org.apache.utlx.engine.registry.TransformationRegistry
import org.apache.utlx.engine.transport.TransportHandlers
import org.apache.utlx.engine.util.UuidV7
import org.slf4j.LoggerFactory
import java.time.Instant

private val logger = LoggerFactory.getLogger("AdminEndpoint")

/**
 * EF03: Admin REST API on port 8081 (alongside health and metrics).
 *
 * Provides bundle management: upload, list, delete, test transformations.
 * Protected by X-Admin-Key header (UTLXE_ADMIN_KEY env var).
 *
 * This is NOT the data plane (port 8085). Admin operations are internal
 * to the VNet and not exposed via Container App ingress.
 */
fun configureAdmin(
    app: Application,
    engine: UtlxEngine,
    adminKey: String = System.getenv("UTLXE_ADMIN_KEY") ?: ""
) {
    val registry = engine.registry

    app.routing {

        // ── Auth check for all /admin/* routes ──
        route("/admin") {
            intercept(ApplicationCallPipeline.Call) {
                if (adminKey.isEmpty()) {
                    call.respond(HttpStatusCode.Forbidden, mapOf(
                        "error" to "UTLXE_ADMIN_KEY not set. Admin API is locked.",
                        "error_code" to "INTERNAL_ERROR"
                    ))
                    finish()
                    return@intercept
                }
                val provided = call.request.header("X-Admin-Key") ?: ""
                if (provided != adminKey) {
                    call.respond(HttpStatusCode.Forbidden, mapOf(
                        "error" to "Invalid or missing X-Admin-Key",
                        "error_code" to "INTERNAL_ERROR"
                    ))
                    finish()
                    return@intercept
                }
            }

            // ── List all transformations ──
            get("/transformations") {
                val transformations = registry.list().map { tx ->
                    mapOf(
                        "name" to tx.name,
                        "strategy" to tx.strategy.name,
                        "status" to "ready",
                        "deployed_at" to tx.loadedAt.toString(),
                        "messages_processed" to tx.executionCount.get(),
                        "errors" to tx.errorCount.get()
                    )
                }
                call.respond(HttpStatusCode.OK, mapOf(
                    "transformations" to transformations
                ))
            }

            // ── Get transformation details ──
            get("/transformations/{name}") {
                val name = call.parameters["name"] ?: ""
                val tx = registry.get(name)
                if (tx == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found",
                        "error_code" to "TRANSFORMATION_NOT_FOUND"
                    ))
                    return@get
                }
                call.respond(HttpStatusCode.OK, mapOf(
                    "name" to tx.name,
                    "strategy" to tx.strategy.name,
                    "status" to "ready",
                    "source" to tx.source,
                    "deployed_at" to tx.loadedAt.toString(),
                    "messages_processed" to tx.executionCount.get(),
                    "errors" to tx.errorCount.get()
                ))
            }

            // ── Upload / update a transformation ──
            post("/transformations/{name}") {
                val name = call.parameters["name"] ?: ""

                // Accept plain text body = .utlx source
                // Usage: curl -X POST -H "X-Admin-Key: $KEY" -d @file.utlx .../admin/transformations/name
                val source = call.receiveText()

                if (source.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Empty transformation source",
                        "error_code" to "INPUT_PARSE_FAILED"
                    ))
                    return@post
                }

                // Load/compile via the standard handler
                val startTime = System.nanoTime()
                val loadReq = LoadTransformationRequest.newBuilder()
                    .setTransformationId(name)
                    .setUtlxSource(source)
                    .setStrategy("COMPILED")
                    .build()
                val loadResp = TransportHandlers.handleLoadTransformation(loadReq, engine, registry)

                if (!loadResp.success) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "status" to "rejected",
                        "error" to loadResp.error,
                        "error_code" to "INPUT_PARSE_FAILED"
                    ))
                    return@post
                }

                val compiledMs = (System.nanoTime() - startTime) / 1_000_000

                logger.info("Admin: deployed transformation '{}' in {}ms", name, compiledMs)
                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to "deployed",
                    "name" to name,
                    "strategy" to "COMPILED",
                    "compiled_in_ms" to compiledMs
                ))
            }

            // ── Delete a transformation ──
            delete("/transformations/{name}") {
                val name = call.parameters["name"] ?: ""
                val removed = registry.unload(name)
                if (!removed) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found",
                        "error_code" to "TRANSFORMATION_NOT_FOUND"
                    ))
                    return@delete
                }
                logger.info("Admin: deleted transformation '{}'", name)
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "name" to name
                ))
            }

            // ── Test a transformation with sample input ──
            post("/transformations/{name}/test") {
                val name = call.parameters["name"] ?: ""
                val tx = registry.get(name)
                if (tx == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found",
                        "error_code" to "TRANSFORMATION_NOT_FOUND"
                    ))
                    return@post
                }

                val input = call.receiveText()
                val contentType = call.request.contentType().toString()

                val execReq = ExecuteRequest.newBuilder()
                    .setTransformationId(name)
                    .setPayload(ByteString.copyFromUtf8(input))
                    .setContentType(if (contentType.contains("json")) "application/json" else contentType)
                    .setMessageId(UuidV7.generate())
                    .build()
                val execResp = TransportHandlers.handleExecute(execReq, registry)

                if (execResp.success) {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "ok",
                        "output" to execResp.output.toStringUtf8(),
                        "duration_ms" to (execResp.metrics?.executeDurationUs ?: 0) / 1000
                    ))
                } else {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "error",
                        "error" to execResp.error,
                        "error_code" to execResp.errorCode.name
                    ))
                }
                // Test calls are NOT counted in metrics (don't call recordExecution)
            }

            // ── Engine info ──
            get("/info") {
                call.respond(HttpStatusCode.OK, mapOf(
                    "version" to "1.0.1",
                    "uptime_seconds" to engine.uptimeMs() / 1000,
                    "transformations" to registry.list().size,
                    "ready" to (engine.state == org.apache.utlx.engine.EngineState.RUNNING && registry.list().isNotEmpty()),
                    "admin_key_set" to adminKey.isNotEmpty()
                ))
            }
        }
    }
}
