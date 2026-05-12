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
import org.apache.utlx.engine.registry.TransformationInstance
import org.apache.utlx.engine.strategy.HeaderSchemaInfo
import org.apache.utlx.engine.util.UuidV7
import org.apache.utlx.engine.validation.SchemaValidatorFactory
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
    dataDir: String? = null,
    daprIntegration: DaprIntegration? = null
) {
    val registry = engine.registry
    val dataDirPath = dataDir?.let { Path.of(it) }
    val schemaStore = SchemaStore(dataDirPath)
    val validationOverrides = engine.validationOverrides
    val dapr = daprIntegration ?: DaprIntegration()

    // Load persisted schemas from disk
    schemaStore.scanFromDisk()

    // Probe Dapr sidecar on startup
    dapr.probeSidecar()

    // EF09: Locked mode response helper
    val isLocked = engine.isLocked
    val bundleInfo = engine.bundleInfo

    app.routing {

        // ── Auth check + access logging for all /admin/* routes ──
        route("/admin") {
            intercept(ApplicationCallPipeline.Call) {
                val method = call.request.httpMethod.value
                val path = call.request.uri
                logger.debug("Admin API: {} {}", method, path)

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
                    val syncState = dapr.getSyncState(tx.name)
                    val messaging = buildMessagingResponse(tx.config, syncState)
                    mapOf(
                        "name" to tx.name,
                        "strategy" to tx.strategy.name,
                        "status" to if (tx.paused) "paused" else "ready",
                        "deployed_at" to tx.loadedAt.toString(),
                        "messages_processed" to tx.executionCount.get(),
                        "errors" to tx.errorCount.get(),
                        "sync_status" to syncState.status,
                        "messaging" to messaging
                    )
                }
                call.respond(HttpStatusCode.OK, mapOf(
                    "transformations" to transformations,
                    "dapr_mode" to dapr.mode
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
                if (isLocked) {
                    call.respond(HttpStatusCode.Forbidden, lockedResponse(bundleInfo))
                    return@post
                }
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
                val loadResp = TransportHandlers.handleLoadTransformation(loadReq, engine)

                if (!loadResp.success) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "status" to "rejected",
                        "error" to loadResp.error,
                        "error_code" to "INPUT_PARSE_FAILED"
                    ))
                    return@post
                }

                // EF02: Resolve schema references from .utlx header against SchemaStore
                val instance = registry.get(name)
                if (instance != null) {
                    val schemaInfo = instance.strategy.getHeaderSchemaInfo()
                    if (schemaInfo != null) {
                        resolveSchemaValidators(name, schemaInfo, instance, schemaStore)
                    }
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

            // ── Delete a transformation (auto-syncs: removes Dapr components immediately) ──
            delete("/transformations/{name}") {
                if (isLocked) {
                    call.respond(HttpStatusCode.Forbidden, lockedResponse(bundleInfo))
                    return@delete
                }
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
                // Auto-remove Dapr components (deletion is always immediate, no draft state)
                dapr.removeOnDelete(name)

                logger.info("Admin: deleted transformation '{}' (Dapr components removed)", name)
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

                // EB01: Read raw bytes for test execute — supports non-UTF-8 payloads
                val inputBytes = call.receive<ByteArray>()
                val contentType = call.request.contentType().toString()

                val execReq = ExecuteRequest.newBuilder()
                    .setTransformationId(name)
                    .setPayload(ByteString.copyFrom(inputBytes))
                    .setContentType(if (contentType.contains("json")) "application/json" else contentType)
                    .setMessageId(UuidV7.generate())
                    .build()
                val execResp = TransportHandlers.handleExecute(execReq, engine)

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

            // ── Upload bundle (ZIP / .utlar) — auto-syncs messaging to Dapr ──
            post("/bundle") {
                if (isLocked) {
                    call.respond(HttpStatusCode.Forbidden, lockedResponse(bundleInfo))
                    return@post
                }
                val zipBytes = call.receive<ByteArray>()
                val startTime = System.nanoTime()
                var loaded = 0
                val names = mutableListOf<String>()
                val errors = mutableListOf<String>()

                // Two-pass: first collect transform.yaml configs + schemas, then load .utlx files
                val configMap = mutableMapOf<String, org.apache.utlx.engine.config.TransformConfig>()
                val sourceMap = mutableMapOf<String, String>()
                val schemaMap = mutableMapOf<String, ByteArray>()

                try {
                    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val path = entry.name
                            if (!entry.isDirectory && path.startsWith("transformations/")) {
                                val parts = path.removePrefix("transformations/").split("/")
                                if (parts.size == 2) {
                                    val name = parts[0]
                                    when {
                                        path.endsWith(".utlx") -> {
                                            sourceMap[name] = zis.readBytes().toString(Charsets.UTF_8)
                                        }
                                        path.endsWith("transform.yaml") -> {
                                            val yamlContent = zis.readBytes()
                                            try {
                                                val yamlMapper = org.apache.utlx.engine.config.TransformConfig.yamlMapper()
                                                configMap[name] = yamlMapper.readValue(yamlContent, org.apache.utlx.engine.config.TransformConfig::class.java)
                                            } catch (e: Exception) {
                                                logger.warn("Bundle: failed to parse transform.yaml for '{}': {}", name, e.message)
                                            }
                                        }
                                    }
                                }
                            } else if (!entry.isDirectory && path.startsWith("schemas/")) {
                                val filename = path.removePrefix("schemas/")
                                if (filename.isNotEmpty() && !filename.contains("/")) {
                                    schemaMap[filename] = zis.readBytes()
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }

                    // Load schemas from bundle into SchemaStore
                    for ((filename, content) in schemaMap) {
                        schemaStore.put(filename, content)
                        logger.info("Bundle: loaded schema '{}'", filename)
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "status" to "rejected",
                        "error" to "Invalid ZIP: ${e.message}",
                        "error_code" to "INPUT_PARSE_FAILED"
                    ))
                    return@post
                }

                // Load all .utlx sources
                for ((name, source) in sourceMap) {
                    val config = configMap[name]
                    val loadReq = LoadTransformationRequest.newBuilder()
                        .setTransformationId(name)
                        .setUtlxSource(source)
                        .setStrategy(config?.strategy?.ifEmpty { "COMPILED" } ?: "COMPILED")
                        .build()
                    val loadResp = TransportHandlers.handleLoadTransformation(loadReq, engine)

                    if (loadResp.success) {
                        // Apply transform config (including messaging) to the registered instance
                        if (config != null) {
                            val instance = registry.get(name)
                            if (instance != null) {
                                val updatedInstance = TransformationInstance(
                                    name = instance.name, source = instance.source,
                                    strategy = instance.strategy, config = config,
                                    inputValidator = instance.inputValidator,
                                    inputValidators = instance.inputValidators,
                                    outputValidator = instance.outputValidator
                                )
                                registry.register(name, updatedInstance)
                            }
                        }
                        names.add(name)
                        loaded++
                        // Persist to disk
                        if (dataDirPath != null) {
                            val txDir = dataDirPath.resolve("transformations").resolve(name)
                            Files.createDirectories(txDir)
                            Files.writeString(txDir.resolve("$name.utlx"), source)
                            if (config != null) {
                                val yamlMapper = org.apache.utlx.engine.config.TransformConfig.yamlMapper()
                                Files.writeString(txDir.resolve("transform.yaml"), yamlMapper.writeValueAsString(config))
                            }
                        }
                    } else {
                        errors.add("$name: ${loadResp.error}")
                    }
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

                // EF10: Auto-sync messaging to Dapr (bundle is a coherent deployment unit)
                val syncResults = mutableListOf<Map<String, Any?>>()
                for (name in names) {
                    val tx = registry.get(name) ?: continue
                    val input = tx.config.input
                    val output = tx.config.outputMessaging
                    if (input != null || output != null) {
                        val syncResult = dapr.sync(name, input, output)
                        syncResults.add(mapOf("name" to name, "synced" to syncResult.success))
                    } else {
                        dapr.markNoDapr(name)
                    }
                }

                if (errors.isNotEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "status" to "partial",
                        "transformations_loaded" to loaded,
                        "errors" to errors,
                        "compiled_in_ms" to compiledMs
                    ))
                } else {
                    logger.info("Admin: bundle deployed — {} transformation(s) in {}ms, {} synced to Dapr",
                        loaded, compiledMs, syncResults.count { it["synced"] == true })
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "deployed",
                        "transformations" to names,
                        "compiled_in_ms" to compiledMs,
                        "messaging_synced" to syncResults
                    ))
                }
            }

            // ── Export bundle as ZIP (zips the entire data directory tree) ──
            get("/bundle") {
                val baos = ByteArrayOutputStream()

                if (dataDirPath != null && Files.exists(dataDirPath)) {
                    // Zip the entire data directory tree — includes .utlx, transform.yaml, schemas, everything
                    ZipOutputStream(baos).use { zos ->
                        Files.walk(dataDirPath).use { stream ->
                            stream.filter { Files.isRegularFile(it) }
                                .filter { !it.fileName.toString().equals("bundle.utlar") } // don't include the .utlar itself
                                .forEach { file ->
                                    val relativePath = dataDirPath.relativize(file).toString()
                                    zos.putNextEntry(ZipEntry(relativePath))
                                    Files.copy(file, zos)
                                    zos.closeEntry()
                                }
                        }
                    }
                } else {
                    // No data dir — export from memory (transformations + schemas)
                    val yamlMapper = org.apache.utlx.engine.config.TransformConfig.yamlMapper()
                    ZipOutputStream(baos).use { zos ->
                        for (schema in schemaStore.list()) {
                            val content = schemaStore.getContent(schema.filename)
                            if (content != null) {
                                zos.putNextEntry(ZipEntry("schemas/${schema.filename}"))
                                zos.write(content)
                                zos.closeEntry()
                            }
                        }
                        for (tx in registry.list()) {
                            zos.putNextEntry(ZipEntry("transformations/${tx.name}/${tx.name}.utlx"))
                            zos.write(tx.source.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                            val config = tx.config
                            if (config.input != null || config.outputMessaging != null ||
                                config.validationPolicy != "SKIP" || config.strategy != "TEMPLATE") {
                                zos.putNextEntry(ZipEntry("transformations/${tx.name}/transform.yaml"))
                                zos.write(yamlMapper.writeValueAsBytes(config))
                                zos.closeEntry()
                            }
                        }
                    }
                }

                call.response.header("Content-Disposition", "attachment; filename=\"bundle.utlar\"")
                call.respondBytes(baos.toByteArray(), ContentType.Application.Zip)
            }

            // ── Delete all (remove bundle) ──
            delete("/bundle") {
                if (isLocked) {
                    call.respond(HttpStatusCode.Forbidden, lockedResponse(bundleInfo))
                    return@delete
                }
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

            // ── Dapr status ──
            get("/dapr") {
                dapr.probeSidecar()
                call.respond(HttpStatusCode.OK, mapOf(
                    "mode" to dapr.mode,
                    "sidecar_reachable" to dapr.sidecarReachable,
                    "sidecar_version" to dapr.sidecarVersion,
                    "components_dir" to dapr.componentsDir?.toString(),
                    "servicebus_namespace" to dapr.servicebusNamespace,
                    "eventhub_namespace" to dapr.eventhubNamespace,
                    "loaded_components" to dapr.loadedComponents.map { comp ->
                        mapOf("name" to comp.name, "type" to comp.type, "version" to comp.version)
                    }
                ))
            }

            // ── Messaging config per transformation ──
            get("/transformations/{name}/messaging") {
                val name = call.parameters["name"] ?: ""
                val tx = registry.get(name)
                if (tx == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found",
                        "error_code" to "TRANSFORMATION_NOT_FOUND"
                    ))
                    return@get
                }
                val syncState = dapr.getSyncState(name)
                val response = buildMessagingDetailResponse(tx.config, syncState, dapr)
                call.respond(HttpStatusCode.OK, response)
            }

            // ── Set messaging config (draft — not synced to Dapr) ──
            post("/transformations/{name}/messaging") {
                if (isLocked) {
                    call.respond(HttpStatusCode.Forbidden, lockedResponse(bundleInfo))
                    return@post
                }
                val name = call.parameters["name"] ?: ""
                val tx = registry.get(name)
                if (tx == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found",
                        "error_code" to "TRANSFORMATION_NOT_FOUND"
                    ))
                    return@post
                }

                val body = call.receiveText()
                val jsonMapper = com.fasterxml.jackson.databind.ObjectMapper().apply {
                    registerModule(kotlinModule())
                    configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
                val msgConfig = try {
                    jsonMapper.readTree(body)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Invalid JSON: ${e.message}",
                        "error_code" to "INPUT_PARSE_FAILED"
                    ))
                    return@post
                }

                // Parse input and output messaging endpoints
                val inputEndpoint = parseMessagingEndpoint(msgConfig.get("input"))
                val outputEndpoint = parseMessagingEndpoint(msgConfig.get("output"))

                if (inputEndpoint == null && outputEndpoint == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "At least one of 'input' or 'output' must be specified with queue, topic, or eventhub",
                        "error_code" to "INPUT_PARSE_FAILED"
                    ))
                    return@post
                }

                // Update the config on the instance (create new config with messaging)
                val updatedConfig = tx.config.copy(
                    input = inputEndpoint,
                    outputMessaging = outputEndpoint
                )

                // Re-register with updated config
                val updatedInstance = TransformationInstance(
                    name = tx.name,
                    source = tx.source,
                    strategy = tx.strategy,
                    config = updatedConfig,
                    loadedAt = tx.loadedAt,
                    executionCount = tx.executionCount,
                    errorCount = tx.errorCount,
                    inputValidator = tx.inputValidator,
                    inputValidators = tx.inputValidators,
                    outputValidator = tx.outputValidator,
                    paused = tx.paused,
                    recentErrors = tx.recentErrors
                )
                registry.register(name, updatedInstance)

                // Persist to transform.yaml on disk
                if (dataDirPath != null) {
                    persistMessagingConfig(dataDirPath, name, updatedConfig)
                }

                // Mark as draft
                val changes = mutableListOf<String>()
                if (inputEndpoint != null) changes.add("messaging.input")
                if (outputEndpoint != null) changes.add("messaging.output")
                dapr.markDraft(name, changes)

                logger.info("Admin: messaging config set for '{}' (draft): input={}, output={}", name, inputEndpoint, outputEndpoint)
                val syncState = dapr.getSyncState(name)
                call.respond(HttpStatusCode.OK, mapOf(
                    "name" to name,
                    "sync_status" to syncState.status,
                    "input" to inputEndpoint?.let { endpointToMap(it, "unsynced") },
                    "output" to outputEndpoint?.let { endpointToMap(it, "unsynced") },
                    "message" to "Messaging config saved (draft). Call POST /admin/transformations/$name/sync to push to Dapr."
                ))
            }

            // ── Remove messaging config (draft removal) ──
            delete("/transformations/{name}/messaging") {
                if (isLocked) {
                    call.respond(HttpStatusCode.Forbidden, lockedResponse(bundleInfo))
                    return@delete
                }
                val name = call.parameters["name"] ?: ""
                val tx = registry.get(name)
                if (tx == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found",
                        "error_code" to "TRANSFORMATION_NOT_FOUND"
                    ))
                    return@delete
                }

                // Clear messaging from config
                val updatedConfig = tx.config.copy(input = null, outputMessaging = null)
                val updatedInstance = TransformationInstance(
                    name = tx.name, source = tx.source, strategy = tx.strategy,
                    config = updatedConfig, loadedAt = tx.loadedAt,
                    executionCount = tx.executionCount, errorCount = tx.errorCount,
                    inputValidator = tx.inputValidator, inputValidators = tx.inputValidators,
                    outputValidator = tx.outputValidator, paused = tx.paused,
                    recentErrors = tx.recentErrors
                )
                registry.register(name, updatedInstance)

                // Persist removal to disk
                if (dataDirPath != null) {
                    persistMessagingConfig(dataDirPath, name, updatedConfig)
                }

                dapr.markDraft(name, listOf("messaging.removed"))
                logger.info("Admin: messaging config removed for '{}' (draft — sync to apply)", name)
                call.respond(HttpStatusCode.OK, mapOf(
                    "name" to name,
                    "sync_status" to "draft",
                    "message" to "Messaging config removed (draft). Call POST /admin/transformations/$name/sync to remove Dapr components."
                ))
            }

            // ── Get transformation config ──
            get("/transformations/{name}/config") {
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
                    "name" to name,
                    "strategy" to tx.config.strategy,
                    "validationPolicy" to tx.config.validationPolicy,
                    "maxConcurrent" to tx.config.maxConcurrent,
                    "maxInputSize" to tx.config.maxInputSize,
                    "inputs" to tx.config.inputs.map { mapOf("name" to it.name, "schema" to it.schema) },
                    "output_schema" to tx.config.output.schema
                ))
            }

            // ── Update transformation config (persisted to transform.yaml) ──
            post("/transformations/{name}/config") {
                if (isLocked) {
                    call.respond(HttpStatusCode.Forbidden, lockedResponse(bundleInfo))
                    return@post
                }
                val name = call.parameters["name"] ?: ""
                val tx = registry.get(name)
                if (tx == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found",
                        "error_code" to "TRANSFORMATION_NOT_FOUND"
                    ))
                    return@post
                }

                val body = call.receiveText()
                val jsonMapper = com.fasterxml.jackson.databind.ObjectMapper().apply {
                    registerModule(com.fasterxml.jackson.module.kotlin.kotlinModule())
                    configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
                val tree = try {
                    jsonMapper.readTree(body)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Invalid JSON: ${e.message}",
                        "error_code" to "INPUT_PARSE_FAILED"
                    ))
                    return@post
                }

                // Build updated config from current + overrides
                val newStrategy = tree.get("strategy")?.asText() ?: tx.config.strategy
                val newValidationPolicy = tree.get("validationPolicy")?.asText() ?: tx.config.validationPolicy
                val newMaxConcurrent = tree.get("maxConcurrent")?.asInt() ?: tx.config.maxConcurrent
                val newMaxInputSize = if (tree.has("maxInputSize")) tree.get("maxInputSize")?.asText() else tx.config.maxInputSize

                // Schema bindings: input schema(s) and output schema
                val newInputs = if (tree.has("inputs")) {
                    tree.get("inputs").map { node ->
                        org.apache.utlx.engine.config.InputSlot(
                            name = node.get("name")?.asText() ?: "input",
                            schema = node.get("schema")?.asText()
                        )
                    }
                } else {
                    tx.config.inputs
                }
                val newOutputSchema = if (tree.has("output_schema")) {
                    tree.get("output_schema")?.asText()
                } else {
                    tx.config.output.schema
                }

                val updatedConfig = tx.config.copy(
                    strategy = newStrategy,
                    validationPolicy = newValidationPolicy,
                    maxConcurrent = newMaxConcurrent,
                    maxInputSize = newMaxInputSize,
                    inputs = newInputs,
                    output = org.apache.utlx.engine.config.OutputSlot(schema = newOutputSchema)
                )

                // Re-register with updated config
                val updatedInstance = TransformationInstance(
                    name = tx.name, source = tx.source, strategy = tx.strategy,
                    config = updatedConfig, loadedAt = tx.loadedAt,
                    executionCount = tx.executionCount, errorCount = tx.errorCount,
                    inputValidator = tx.inputValidator, inputValidators = tx.inputValidators,
                    outputValidator = tx.outputValidator, paused = tx.paused,
                    recentErrors = tx.recentErrors
                )
                registry.register(name, updatedInstance)

                // Persist to disk
                if (dataDirPath != null) {
                    persistMessagingConfig(dataDirPath, name, updatedConfig)
                }

                logger.info("Admin: config updated for '{}' (strategy={}, validation={}, maxConcurrent={}, maxInputSize={})",
                    name, newStrategy, newValidationPolicy, newMaxConcurrent, newMaxInputSize)
                call.respond(HttpStatusCode.OK, mapOf<String, Any?>(
                    "name" to name,
                    "strategy" to newStrategy,
                    "validationPolicy" to newValidationPolicy,
                    "maxConcurrent" to newMaxConcurrent,
                    "maxInputSize" to newMaxInputSize,
                    "inputs" to newInputs.map { mapOf("name" to it.name, "schema" to it.schema) },
                    "output_schema" to newOutputSchema
                ))
            }

            // ── Sync single transformation to Dapr ──
            post("/transformations/{name}/sync") {
                val name = call.parameters["name"] ?: ""
                val tx = registry.get(name)
                if (tx == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found",
                        "error_code" to "TRANSFORMATION_NOT_FOUND"
                    ))
                    return@post
                }

                val result = dapr.sync(name, tx.config.input, tx.config.outputMessaging)
                val syncState = dapr.getSyncState(name)

                call.respond(
                    if (result.success) HttpStatusCode.OK else HttpStatusCode.InternalServerError,
                    mapOf(
                        "name" to name,
                        "sync_status" to syncState.status,
                        "synced_at" to syncState.lastSynced?.toString(),
                        "dapr_mode" to dapr.mode,
                        "actions" to result.actions.map { a ->
                            mapOf("action" to a.action, "component" to a.component, "type" to a.type)
                        },
                        "warnings" to result.warnings,
                        "message" to result.message
                    )
                )
            }

            // ── Sync all draft transformations to Dapr ──
            post("/sync") {
                val results = mutableListOf<Map<String, Any?>>()
                val errors = mutableListOf<Map<String, Any?>>()
                val skipped = mutableListOf<String>()

                for (tx in registry.list()) {
                    val syncState = dapr.getSyncState(tx.name)
                    if (syncState.status != "draft") {
                        if (syncState.status == "no_dapr") skipped.add(tx.name)
                        continue
                    }

                    val result = dapr.sync(tx.name, tx.config.input, tx.config.outputMessaging)
                    if (result.success) {
                        results.add(mapOf(
                            "name" to tx.name,
                            "actions" to result.actions.map { a ->
                                mapOf("action" to a.action, "component" to a.component)
                            }
                        ))
                    } else {
                        errors.add(mapOf("name" to tx.name, "error" to result.message))
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf(
                    "synced" to results,
                    "errors" to errors,
                    "skipped" to skipped,
                    "message" to "${results.size} transformation(s) synced, ${errors.size} error(s), ${skipped.size} skipped (no messaging config)."
                ))
            }

            // ── Sync status overview ──
            get("/sync") {
                val transformations = registry.list().map { tx ->
                    val syncState = dapr.getSyncState(tx.name)
                    val messaging = buildMessagingResponse(tx.config, syncState)
                    mapOf(
                        "name" to tx.name,
                        "sync_status" to syncState.status,
                        "last_synced" to syncState.lastSynced?.toString(),
                        "pending_changes" to syncState.pendingChanges,
                        "error" to syncState.error,
                        "messaging" to messaging
                    )
                }
                val statusCounts = transformations.groupBy { it["sync_status"] as String }
                call.respond(HttpStatusCode.OK, mapOf(
                    "dapr_mode" to dapr.mode,
                    "transformations" to transformations,
                    "draft_count" to (statusCounts["draft"]?.size ?: 0),
                    "error_count" to (statusCounts["error"]?.size ?: 0),
                    "synced_count" to (statusCounts["synced"]?.size ?: 0),
                    "no_dapr_count" to (statusCounts["no_dapr"]?.size ?: 0)
                ))
            }

            // ── Schema endpoints ──

            get("/schemas") {
                val schemas = schemaStore.list().map { s ->
                    mapOf(
                        "filename" to s.filename,
                        "size_bytes" to s.content.size,
                        "uploaded_at" to s.uploadedAt.toString()
                    )
                }
                call.respond(HttpStatusCode.OK, mapOf("schemas" to schemas))
            }

            get("/schemas/{filename}") {
                val filename = call.parameters["filename"] ?: ""
                val entry = schemaStore.get(filename)
                if (entry == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Schema '$filename' not found"
                    ))
                    return@get
                }
                call.respondBytes(entry.content, ContentType.Application.OctetStream)
            }

            post("/schemas/{filename}") {
                if (isLocked) {
                    call.respond(HttpStatusCode.Forbidden, lockedResponse(bundleInfo))
                    return@post
                }
                val filename = call.parameters["filename"] ?: ""
                val content = call.receive<ByteArray>()
                schemaStore.put(filename, content)
                logger.info("Admin: uploaded schema '{}' ({} bytes)", filename, content.size)
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "filename" to filename,
                    "size_bytes" to content.size
                ))
            }

            delete("/schemas/{filename}") {
                if (isLocked) {
                    call.respond(HttpStatusCode.Forbidden, lockedResponse(bundleInfo))
                    return@delete
                }
                val filename = call.parameters["filename"] ?: ""
                val removed = schemaStore.remove(filename)
                if (!removed) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Schema '$filename' not found"
                    ))
                    return@delete
                }
                logger.info("Admin: deleted schema '{}'", filename)
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "filename" to filename))
            }

            // ── Validation override endpoints ──

            get("/transformations/{name}/validation") {
                val name = call.parameters["name"] ?: ""
                val tx = registry.get(name)
                if (tx == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found"
                    ))
                    return@get
                }
                val override = validationOverrides.get(name)
                val effectivePolicy = validationOverrides.effectivePolicy(name, tx.config.validationPolicy)
                call.respond(HttpStatusCode.OK, mapOf(
                    "effective_policy" to effectivePolicy,
                    "source" to if (override != null) "runtime-override" else "config",
                    "config_policy" to tx.config.validationPolicy,
                    "override_policy" to override?.policy
                ))
            }

            post("/transformations/{name}/validation") {
                val name = call.parameters["name"] ?: ""
                if (registry.get(name) == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found"
                    ))
                    return@post
                }
                val body = call.receiveText()
                val mapper = com.fasterxml.jackson.databind.ObjectMapper()
                val policy = try {
                    mapper.readTree(body).get("policy")?.asText() ?: "off"
                } catch (_: Exception) { "off" }

                validationOverrides.set(name, policy)
                logger.info("Admin: validation override for '{}' set to '{}'", name, policy)
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "name" to name,
                    "effective_policy" to policy,
                    "source" to "runtime-override"
                ))
            }

            delete("/transformations/{name}/validation") {
                val name = call.parameters["name"] ?: ""
                if (registry.get(name) == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "Transformation '$name' not found"
                    ))
                    return@delete
                }
                validationOverrides.remove(name)
                val tx = registry.get(name)!!
                logger.info("Admin: validation override for '{}' removed", name)
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "name" to name,
                    "effective_policy" to tx.config.validationPolicy,
                    "source" to "config"
                ))
            }

            // ── Config ──
            get("/config") {
                val config = engine.config
                call.respond(HttpStatusCode.OK, mapOf(
                    "name" to config.engine.name,
                    "defaultStrategy" to config.engine.defaultStrategy,
                    "sharedPoolSize" to config.engine.threads.sharedPoolSize,
                    "healthPort" to config.engine.monitoring.health.port,
                    "dataDir" to dataDir,
                    "persistence" to if (dataDir != null) "disk" else "memory-only"
                ))
            }

            // ── Engine info ──
            get("/info") {
                call.respond(HttpStatusCode.OK, mapOf<String, Any?>(
                    "version" to "1.0.1",
                    "mode" to bundleInfo.mode,
                    "bundle_version" to bundleInfo.bundleVersion,
                    "bundle_checksum" to bundleInfo.bundleChecksum,
                    "bundle_created" to bundleInfo.bundleCreated,
                    "uptime_seconds" to engine.uptimeMs() / 1000,
                    "transformations" to registry.list().size,
                    "schemas" to schemaStore.list().size,
                    "ready" to (engine.state == org.apache.utlx.engine.EngineState.RUNNING && registry.list().isNotEmpty()),
                    "admin_key_set" to adminKey.isNotEmpty(),
                    "data_dir" to dataDir,
                    "persistence" to if (dataDir != null) "disk" else "memory-only",
                    "dapr_mode" to dapr.mode,
                    "dapr_sidecar" to dapr.sidecarReachable,
                    "log_level" to LogBuffer.getLevel(),
                    "log_buffer_size" to LogBuffer.size(),
                    "tracing" to org.apache.utlx.engine.telemetry.Tracing.isActive,
                    "tracing_agent" to if (org.apache.utlx.engine.telemetry.Tracing.isActive) "Azure Monitor agent loaded" else "disabled (set APPLICATIONINSIGHTS_CONNECTION_STRING to enable)",
                    "heap_backpressure_threshold" to "${(engine.heapBackpressureThreshold * 100).toInt()}%",
                    "heap_usage" to "${engine.heapUsagePercent()}%",
                    "heap_pressure" to engine.isHeapPressure()
                ))
            }

            // ── Log level management (allowed in locked mode — operational) ──

            get("/log/level") {
                call.respond(HttpStatusCode.OK, mapOf(
                    "level" to LogBuffer.getLevel()
                ))
            }

            post("/log/level") {
                val body = call.receiveText()
                val mapper = com.fasterxml.jackson.databind.ObjectMapper()
                val tree = try { mapper.readTree(body) } catch (_: Exception) { null }
                val level = tree?.get("level")?.asText()
                    ?: body.trim().uppercase().ifEmpty { null }

                if (level == null || level !in listOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR")) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Invalid log level. Valid: TRACE, DEBUG, INFO, WARN, ERROR",
                        "error_code" to "INPUT_PARSE_FAILED"
                    ))
                    return@post
                }

                val revertMinutes = tree?.get("revert_after_minutes")?.asInt()
                val previousLevel = LogBuffer.getLevel()
                LogBuffer.setLevel(level, revertMinutes)

                logger.info("Admin: log level changed {} → {} {}", previousLevel, level,
                    revertMinutes?.let { "(auto-revert in ${it}m)" } ?: "")

                call.respond(HttpStatusCode.OK, mapOf(
                    "previous_level" to previousLevel,
                    "level" to level,
                    "revert_after_minutes" to revertMinutes
                ))
            }

            // ── Log buffer download ──

            get("/logs") {
                val rawLimit = call.request.queryParameters["limit"]?.toIntOrNull()
                val limit = when {
                    rawLimit == null -> 100          // default: last 100
                    rawLimit <= 0 -> Int.MAX_VALUE   // 0 or negative: all entries
                    else -> rawLimit
                }
                val level = call.request.queryParameters["level"]
                val contains = call.request.queryParameters["contains"]
                val since = call.request.queryParameters["since"]?.let {
                    try { java.time.Instant.parse(it) } catch (_: Exception) { null }
                }

                val entries = LogBuffer.entries(limit, level, contains, since)
                call.respond(HttpStatusCode.OK, mapOf(
                    "entries" to entries.map { e ->
                        mapOf(
                            "timestamp" to e.timestamp.toString(),
                            "level" to e.level,
                            "logger" to e.logger,
                            "message" to e.message,
                            "thread" to e.thread
                        )
                    },
                    "total_buffered" to LogBuffer.size(),
                    "showing" to entries.size,
                    "current_level" to LogBuffer.getLevel()
                ))
            }

            delete("/logs") {
                LogBuffer.clear()
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "message" to "Log buffer cleared"))
            }

            // ── Heap backpressure (allowed in locked mode — operational) ──

            get("/backpressure") {
                val rt = Runtime.getRuntime()
                call.respond(HttpStatusCode.OK, mapOf(
                    "threshold" to "${(engine.heapBackpressureThreshold * 100).toInt()}%",
                    "heap_used_mb" to (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024),
                    "heap_max_mb" to rt.maxMemory() / (1024 * 1024),
                    "heap_usage" to "${engine.heapUsagePercent()}%",
                    "pressure" to engine.isHeapPressure()
                ))
            }

            post("/backpressure") {
                val body = call.receiveText()
                val mapper = com.fasterxml.jackson.databind.ObjectMapper()
                val tree = try { mapper.readTree(body) } catch (_: Exception) { null }
                val threshold = tree?.get("threshold")?.asInt()

                if (threshold == null || threshold < 50 || threshold > 99) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Invalid threshold. Must be 50-99 (percent).",
                        "error_code" to "INPUT_PARSE_FAILED"
                    ))
                    return@post
                }

                val previous = (engine.heapBackpressureThreshold * 100).toInt()
                engine.heapBackpressureThreshold = threshold / 100.0
                logger.info("Admin: heap backpressure threshold changed {}% → {}%", previous, threshold)
                call.respond(HttpStatusCode.OK, mapOf(
                    "previous_threshold" to "$previous%",
                    "threshold" to "$threshold%"
                ))
            }
        }
    }
}

// ── EF09: Locked mode helper ──

private fun lockedResponse(bundleInfo: BundleInfo) = mapOf(
    "error" to "Production mode — transformations are deployed via CI/CD. Place a new bundle.utlar on the volume and restart.",
    "error_code" to "BUNDLE_LOCKED",
    "mode" to "locked",
    "bundle_version" to bundleInfo.bundleVersion
)

// ── Messaging helper functions ──

private fun parseMessagingEndpoint(node: com.fasterxml.jackson.databind.JsonNode?): org.apache.utlx.engine.config.MessagingEndpoint? {
    if (node == null || node.isNull) return null
    val queue = node.get("queue")?.asText()
    val topic = node.get("topic")?.asText()
    val eventhub = node.get("eventhub")?.asText()
    val subscription = node.get("subscription")?.asText()
    val consumerGroup = node.get("consumerGroup")?.asText()
    if (queue == null && topic == null && eventhub == null) return null
    return org.apache.utlx.engine.config.MessagingEndpoint(
        queue = queue, topic = topic, eventhub = eventhub,
        subscription = subscription, consumerGroup = consumerGroup
    )
}

private fun endpointToMap(ep: org.apache.utlx.engine.config.MessagingEndpoint, daprStatus: String): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    ep.queue?.let { map["queue"] = it }
    ep.topic?.let { map["topic"] = it }
    ep.eventhub?.let { map["eventhub"] = it }
    ep.subscription?.let { map["subscription"] = it }
    ep.consumerGroup?.let { map["consumerGroup"] = it }
    map["dapr_status"] = daprStatus
    map["dapr_component_type"] = ep.daprComponentType
    return map
}

private fun buildMessagingResponse(
    config: org.apache.utlx.engine.config.TransformConfig,
    syncState: SyncState
): Map<String, Any?>? {
    val input = config.input
    val output = config.outputMessaging
    if (input == null && output == null) return null

    val daprStatus = when (syncState.status) {
        "synced" -> "active"
        "draft" -> "unsynced"
        "error" -> "error"
        else -> "not_configured"
    }
    val result = mutableMapOf<String, Any?>()
    input?.let { result["input"] = endpointToMap(it, daprStatus) }
    output?.let { result["output"] = endpointToMap(it, daprStatus) }
    return result
}

private fun buildMessagingDetailResponse(
    config: org.apache.utlx.engine.config.TransformConfig,
    syncState: SyncState,
    dapr: DaprIntegration
): Map<String, Any?> {
    val daprStatus = when (syncState.status) {
        "synced" -> "active"
        "draft" -> "unsynced"
        "error" -> "error"
        else -> "not_configured"
    }
    return mapOf(
        "input" to config.input?.let { endpointToMap(it, daprStatus) },
        "output" to config.outputMessaging?.let { endpointToMap(it, daprStatus) },
        "sync_status" to syncState.status,
        "last_synced" to syncState.lastSynced?.toString(),
        "pending_changes" to syncState.pendingChanges,
        "error" to syncState.error,
        "dapr_mode" to dapr.mode
    )
}

private fun persistMessagingConfig(
    dataDirPath: Path,
    name: String,
    config: org.apache.utlx.engine.config.TransformConfig
) {
    try {
        val txDir = dataDirPath.resolve("transformations").resolve(name)
        Files.createDirectories(txDir)
        val yamlFile = txDir.resolve("transform.yaml")

        // Build YAML content from config
        val yamlMapper = org.apache.utlx.engine.config.TransformConfig.yamlMapper()
        // Write a clean representation
        val yamlContent = mutableMapOf<String, Any?>()
        yamlContent["strategy"] = config.strategy
        yamlContent["validationPolicy"] = config.validationPolicy
        yamlContent["maxConcurrent"] = config.maxConcurrent
        config.maxInputSize?.let { yamlContent["maxInputSize"] = it }
        config.input?.let { ep ->
            val inputMap = mutableMapOf<String, String>()
            ep.queue?.let { inputMap["queue"] = it }
            ep.topic?.let { inputMap["topic"] = it }
            ep.eventhub?.let { inputMap["eventhub"] = it }
            ep.subscription?.let { inputMap["subscription"] = it }
            ep.consumerGroup?.let { inputMap["consumerGroup"] = it }
            yamlContent["input"] = inputMap
        }
        config.outputMessaging?.let { ep ->
            val outputMap = mutableMapOf<String, String>()
            ep.queue?.let { outputMap["queue"] = it }
            ep.topic?.let { outputMap["topic"] = it }
            ep.eventhub?.let { outputMap["eventhub"] = it }
            yamlContent["output_messaging"] = outputMap
        }
        Files.writeString(yamlFile, yamlMapper.writeValueAsString(yamlContent))
        logger.debug("Admin: persisted transform.yaml for '{}'", name)
    } catch (e: Exception) {
        logger.warn("Admin: failed to persist messaging config for '{}': {}", name, e.message)
    }
}

/**
 * EF02: Resolve schema references from the .utlx header against the SchemaStore.
 * If a transformation declares `input xml {schema: "order.xsd"}`, look up "order.xsd"
 * in the SchemaStore and create a validator on the TransformationInstance.
 */
private val formatToSchemaFormat = mapOf(
    "xml" to "xsd",
    "json" to "json-schema",
    "yaml" to "yaml",
    "csv" to "tsch",
    "avro" to "avro",
    "odata" to "osch",
    "protobuf" to "protobuf"
)

private fun resolveSchemaValidators(
    name: String,
    schemaInfo: HeaderSchemaInfo,
    instance: TransformationInstance,
    schemaStore: SchemaStore
) {
    // Resolve input schemas — use singular for single-input, map for multi-input
    val schemasWithRefs = schemaInfo.allInputSchemas.filter { it.value.schemaRef != null }

    if (schemasWithRefs.size <= 1 && schemaInfo.inputSchemaRef != null) {
        // Single-input: use inputValidator (singular)
        val validator = createValidatorFromStore(schemaInfo.inputSchemaRef, schemaInfo.inputFormat, schemaStore)
        if (validator != null) {
            val field = TransformationInstance::class.java.getDeclaredField("inputValidator")
            field.isAccessible = true
            field.set(instance, validator)
            logger.info("Schema '{}' resolved as {} validator for '{}' input",
                schemaInfo.inputSchemaRef, formatToSchemaFormat[schemaInfo.inputFormat], name)
        }
    } else if (schemasWithRefs.isNotEmpty()) {
        // Multi-input: use inputValidators (map) — each named input gets its own
        val multiValidators = mutableMapOf<String, org.apache.utlx.engine.validation.SchemaValidator>()
        for ((inputName, ref) in schemasWithRefs) {
            val validator = createValidatorFromStore(ref.schemaRef!!, ref.format, schemaStore)
            if (validator != null) {
                multiValidators[inputName] = validator
                logger.info("Schema '{}' resolved as {} validator for '{}' input '{}'",
                    ref.schemaRef, formatToSchemaFormat[ref.format], name, inputName)
            }
        }
        if (multiValidators.isNotEmpty()) {
            val field = TransformationInstance::class.java.getDeclaredField("inputValidators")
            field.isAccessible = true
            field.set(instance, multiValidators.toMap())
        }
    }

    // Resolve output schema
    if (schemaInfo.outputSchemaRef != null) {
        val validator = createValidatorFromStore(schemaInfo.outputSchemaRef, schemaInfo.outputFormat, schemaStore)
        if (validator != null) {
            val field = TransformationInstance::class.java.getDeclaredField("outputValidator")
            field.isAccessible = true
            field.set(instance, validator)
            logger.info("Schema '{}' resolved as {} validator for '{}' output",
                schemaInfo.outputSchemaRef, formatToSchemaFormat[schemaInfo.outputFormat], name)
        }
    }
}

private fun createValidatorFromStore(
    schemaRef: String,
    format: String,
    schemaStore: SchemaStore
): org.apache.utlx.engine.validation.SchemaValidator? {
    val content = schemaStore.getContent(schemaRef)
    if (content == null) {
        logger.warn("Schema '{}' not found in SchemaStore", schemaRef)
        return null
    }
    val schemaFormat = formatToSchemaFormat[format] ?: "json-schema"
    return try {
        SchemaValidatorFactory.create(String(content, Charsets.UTF_8), schemaFormat)
    } catch (e: Exception) {
        logger.warn("Failed to create validator from schema '{}': {}", schemaRef, e.message)
        null
    }
}
