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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
    adminKey: String = System.getenv("UTLXE_ADMIN_KEY") ?: "",
    dataDir: String? = null
) {
    val registry = engine.registry
    val dataDirPath = dataDir?.let { Path.of(it) }

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
                        "status" to if (tx.paused) "paused" else "ready",
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

                // Persist to disk (if data dir configured)
                if (dataDirPath != null) {
                    try {
                        val txDir = dataDirPath.resolve("transformations").resolve(name)
                        Files.createDirectories(txDir)
                        Files.writeString(txDir.resolve("$name.utlx"), source)
                        logger.debug("Admin: persisted '{}' to {}", name, txDir)
                    } catch (e: Exception) {
                        logger.warn("Admin: failed to persist '{}' to disk: {}", name, e.message)
                        // Don't fail the upload — in-memory registration succeeded
                    }
                }

                logger.info("Admin: deployed transformation '{}' in {}ms (persisted={})", name, compiledMs, dataDirPath != null)
                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to "deployed",
                    "name" to name,
                    "strategy" to "COMPILED",
                    "config" to if (dataDirPath != null) "persisted" else "memory-only",
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
                // Remove from disk
                if (dataDirPath != null) {
                    try {
                        val txDir = dataDirPath.resolve("transformations").resolve(name)
                        if (Files.exists(txDir)) {
                            Files.walk(txDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                        }
                    } catch (e: Exception) {
                        logger.warn("Admin: failed to delete '{}' from disk: {}", name, e.message)
                    }
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

            // ── Upload bundle (ZIP / .utlar) ──
            post("/bundle") {
                val zipBytes = call.receive<ByteArray>()
                val startTime = System.nanoTime()
                var loaded = 0
                val names = mutableListOf<String>()
                val errors = mutableListOf<String>()

                try {
                    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            // Look for transformations/{name}/{name}.utlx
                            val path = entry.name
                            if (!entry.isDirectory && path.endsWith(".utlx") && path.startsWith("transformations/")) {
                                val parts = path.removePrefix("transformations/").split("/")
                                if (parts.size == 2) {
                                    val name = parts[0]
                                    val source = zis.readBytes().toString(Charsets.UTF_8)

                                    val loadReq = LoadTransformationRequest.newBuilder()
                                        .setTransformationId(name)
                                        .setUtlxSource(source)
                                        .setStrategy("COMPILED")
                                        .build()
                                    val loadResp = TransportHandlers.handleLoadTransformation(loadReq, engine, registry)

                                    if (loadResp.success) {
                                        names.add(name)
                                        loaded++
                                        // Persist to disk
                                        if (dataDirPath != null) {
                                            val txDir = dataDirPath.resolve("transformations").resolve(name)
                                            Files.createDirectories(txDir)
                                            Files.writeString(txDir.resolve("$name.utlx"), source)
                                        }
                                    } else {
                                        errors.add("$name: ${loadResp.error}")
                                    }
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "status" to "rejected",
                        "error" to "Invalid ZIP: ${e.message}",
                        "error_code" to "INPUT_PARSE_FAILED"
                    ))
                    return@post
                }

                val compiledMs = (System.nanoTime() - startTime) / 1_000_000

                if (loaded == 0 && errors.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "status" to "rejected",
                        "error" to "No transformations found in ZIP. Expected: transformations/{name}/{name}.utlx",
                        "error_code" to "INPUT_PARSE_FAILED"
                    ))
                    return@post
                }

                if (errors.isNotEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "status" to "partial",
                        "transformations_loaded" to loaded,
                        "errors" to errors,
                        "compiled_in_ms" to compiledMs
                    ))
                } else {
                    logger.info("Admin: bundle deployed — {} transformation(s) in {}ms", loaded, compiledMs)
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "deployed",
                        "transformations" to names,
                        "compiled_in_ms" to compiledMs
                    ))
                }
            }

            // ── Export bundle as ZIP ──
            get("/bundle") {
                val baos = ByteArrayOutputStream()
                ZipOutputStream(baos).use { zos ->
                    for (tx in registry.list()) {
                        val entryPath = "transformations/${tx.name}/${tx.name}.utlx"
                        zos.putNextEntry(ZipEntry(entryPath))
                        zos.write(tx.source.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                }
                call.response.header("Content-Disposition", "attachment; filename=\"bundle.utlar\"")
                call.respondBytes(baos.toByteArray(), ContentType.Application.Zip)
            }

            // ── Delete all (remove bundle) ──
            delete("/bundle") {
                val names = registry.list().map { it.name }
                names.forEach { name ->
                    registry.unload(name)
                    if (dataDirPath != null) {
                        try {
                            val txDir = dataDirPath.resolve("transformations").resolve(name)
                            if (Files.exists(txDir)) {
                                Files.walk(txDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                            }
                        } catch (_: Exception) {}
                    }
                }
                logger.info("Admin: deleted all {} transformation(s)", names.size)
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "deleted" to names
                ))
            }

            // ── Pause transformation ──
            post("/transformations/{name}/pause") {
                val name = call.parameters["name"] ?: ""
                val tx = registry.get(name)
                if (tx == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found",
                        "error_code" to "TRANSFORMATION_NOT_FOUND"
                    ))
                    return@post
                }
                tx.paused = true
                logger.info("Admin: paused transformation '{}'", name)
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "name" to name, "status" to "paused"))
            }

            // ── Resume transformation ──
            post("/transformations/{name}/resume") {
                val name = call.parameters["name"] ?: ""
                val tx = registry.get(name)
                if (tx == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found",
                        "error_code" to "TRANSFORMATION_NOT_FOUND"
                    ))
                    return@post
                }
                tx.paused = false
                logger.info("Admin: resumed transformation '{}'", name)
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "name" to name, "status" to "ready"))
            }

            // ── Error ring buffer ──
            get("/transformations/{name}/errors") {
                val name = call.parameters["name"] ?: ""
                val tx = registry.get(name)
                if (tx == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found",
                        "error_code" to "TRANSFORMATION_NOT_FOUND"
                    ))
                    return@get
                }
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val errors = tx.recentErrors.take(limit).map { err ->
                    mapOf(
                        "timestamp" to err.timestamp.toString(),
                        "message" to err.message,
                        "line" to err.line,
                        "phase" to err.phase,
                        "message_id" to err.messageId,
                        "correlation_id" to err.correlationId,
                        "input_preview" to err.inputPreview
                    )
                }
                call.respond(HttpStatusCode.OK, mapOf(
                    "errors" to errors,
                    "total_errors" to tx.errorCount.get(),
                    "showing" to errors.size
                ))
            }

            // ── Bundle validate (dry run) ──
            post("/bundle/validate") {
                val zipBytes = call.receive<ByteArray>()
                var found = 0
                val errors = mutableListOf<String>()

                try {
                    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val path = entry.name
                            if (!entry.isDirectory && path.endsWith(".utlx") && path.startsWith("transformations/")) {
                                val parts = path.removePrefix("transformations/").split("/")
                                if (parts.size == 2) {
                                    val name = parts[0]
                                    val source = zis.readBytes().toString(Charsets.UTF_8)
                                    // Try to compile without registering
                                    try {
                                        val strategy = engine.createStrategy(
                                            org.apache.utlx.engine.config.TransformConfig(strategy = "COMPILED")
                                        )
                                        strategy.initialize(source, org.apache.utlx.engine.config.TransformConfig())
                                        strategy.shutdown()
                                        found++
                                    } catch (e: Exception) {
                                        errors.add("$name: ${e.message}")
                                    }
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "status" to "invalid",
                        "error" to "Invalid ZIP: ${e.message}"
                    ))
                    return@post
                }

                if (errors.isNotEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "status" to "invalid",
                        "valid" to found,
                        "errors" to errors
                    ))
                } else if (found == 0) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "status" to "invalid",
                        "error" to "No transformations found in ZIP"
                    ))
                } else {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "valid",
                        "transformations" to found
                    ))
                }
            }

            // ── Dapr binding validation ──
            get("/dapr/bindings") {
                // Compare Dapr-probed bindings against loaded transformations
                val httpTransport = engine.dataDir  // Access probed bindings via transport if available
                // For now, list transformations and their status
                val transformations = registry.list().map { tx ->
                    mapOf(
                        "name" to tx.name,
                        "status" to if (tx.paused) "paused" else "ready",
                        "messages_processed" to tx.executionCount.get(),
                        "errors" to tx.errorCount.get()
                    )
                }
                call.respond(HttpStatusCode.OK, mapOf(
                    "transformations" to transformations
                ))
            }

            // ── Engine info ──
            get("/info") {
                call.respond(HttpStatusCode.OK, mapOf(
                    "version" to "1.0.1",
                    "uptime_seconds" to engine.uptimeMs() / 1000,
                    "transformations" to registry.list().size,
                    "ready" to (engine.state == org.apache.utlx.engine.EngineState.RUNNING && registry.list().isNotEmpty()),
                    "admin_key_set" to adminKey.isNotEmpty(),
                    "data_dir" to dataDir,
                    "persistence" to if (dataDir != null) "disk" else "memory-only"
                ))
            }
        }
    }
}
