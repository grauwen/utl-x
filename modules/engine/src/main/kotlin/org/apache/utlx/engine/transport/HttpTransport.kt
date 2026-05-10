package org.apache.utlx.engine.transport

import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.protobuf.ByteString
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.proto.*
import org.apache.utlx.engine.registry.TransformationRegistry
import org.apache.utlx.engine.util.UuidV7
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * HttpTransport — REST API over HTTP for UTLXe.
 *
 * Provides a JSON REST API on a configurable port (default 8085).
 * Delegates all logic to TransportHandlers — same behavior as gRPC and stdio-proto.
 *
 * Used for: Azure Container Apps, Docker deployments, any HTTP client.
 * Not Azure-specific — works on any platform.
 *
 * Safeguards:
 * - Request body limit: 10MB (configurable)
 * - Request timeout: handled by Ktor/Netty (non-blocking, no thread exhaustion)
 * - Concurrent requests: bounded by Netty worker threads (default: 2 × CPU cores)
 *
 * Health/metrics endpoint stays on port 8081 (separate Ktor server in HealthEndpoint).
 * This transport runs on its own port (default 8085) for the data plane.
 */
class HttpTransport(
    private val engine: UtlxEngine,
    private val port: Int = DEFAULT_PORT,
    private val host: String = "0.0.0.0",
    private val maxRequestBodyBytes: Long = MAX_REQUEST_BODY_BYTES
) : TransportServer {

    private val logger = LoggerFactory.getLogger(HttpTransport::class.java)
    private var server: ApplicationEngine? = null

    // EF05: Track which bindings Dapr probed at startup (for /admin/dapr/bindings validation)
    private val daprProbedBindings = ConcurrentHashMap.newKeySet<String>()

    override val supportsDynamicLoading = true

    /** EF05: Get the set of Dapr binding names that were probed via OPTIONS. */
    fun getDaprProbedBindings(): Set<String> = daprProbedBindings.toSet()

    companion object {
        const val DEFAULT_PORT = 8085
        // Default 10MB — protects against oversized payloads that would exhaust heap.
        // XML expands 10-50× in UDM; a 10MB XML payload could use 100-500MB of heap.
        const val MAX_REQUEST_BODY_BYTES = 10L * 1024 * 1024
    }

    override fun start(registry: TransformationRegistry) {
        logger.info("HttpTransport: starting on {}:{}", host, port)

        server = embeddedServer(Netty, port = port, host = host) {
            install(ContentNegotiation) {
                jackson {
                    registerModule(kotlinModule())
                }
            }

            // Request body size guard — reject oversized payloads before parsing
            intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins) {
                val contentLength = call.request.header("Content-Length")?.toLongOrNull()
                if (contentLength != null && contentLength > maxRequestBodyBytes) {
                    call.respond(HttpStatusCode.PayloadTooLarge, mapOf(
                        "error" to "Request body too large: ${contentLength} bytes (max: ${maxRequestBodyBytes})",
                        "maxBytes" to maxRequestBodyBytes,
                        "hint" to "XML/JSON expands 10-50× in memory. A 10MB payload could use 100-500MB of heap."
                    ))
                    finish()
                }
            }

            routing {
                // ── Combined load + execute (convenience endpoint) ──
                post("/api/transform") {
                    val req = call.receive<TransformRequest>()

                    // Load (or reload) the transformation
                    val loadProto = LoadTransformationRequest.newBuilder()
                        .setTransformationId(req.transformationId)
                        .setUtlxSource(req.utlxSource)
                        .setStrategy(req.strategy)
                        .build()
                    val loadResp = TransportHandlers.handleLoadTransformation(loadProto, engine)

                    if (!loadResp.success) {
                        call.respond(HttpStatusCode.BadRequest, TransformResponse(
                            success = false,
                            error = "Compilation failed: ${loadResp.error}"
                        ))
                        return@post
                    }

                    // Execute
                    val execProto = ExecuteRequest.newBuilder()
                        .setTransformationId(req.transformationId)
                        .setPayload(ByteString.copyFromUtf8(req.payload))
                        .setContentType(req.contentType)
                        .setCorrelationId(req.correlationId ?: "")
                        .build()
                    val execResp = TransportHandlers.handleExecute(execProto, engine)

                    val status = if (execResp.success) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity
                    call.respond(status, TransformResponse(
                        success = execResp.success,
                        output = if (execResp.success) execResp.output.toStringUtf8() else null,
                        error = if (!execResp.success) execResp.error else null,
                        durationUs = execResp.metrics?.executeDurationUs ?: 0,
                        correlationId = req.correlationId
                    ))
                }

                // ── Load transformation ──
                post("/api/load") {
                    val req = call.receive<LoadRequest>()

                    val proto = LoadTransformationRequest.newBuilder()
                        .setTransformationId(req.transformationId)
                        .setUtlxSource(req.utlxSource)
                        .setStrategy(req.strategy)
                        .setValidationPolicy(req.validationPolicy)
                        .setMaxConcurrent(req.maxConcurrent)
                    req.config.forEach { (k, v) -> proto.putConfig(k, v) }

                    val resp = TransportHandlers.handleLoadTransformation(proto.build(), engine)

                    val status = if (resp.success) HttpStatusCode.OK else HttpStatusCode.BadRequest
                    call.respond(status, LoadResponse(
                        success = resp.success,
                        error = if (!resp.success) resp.error else null,
                        compileDurationUs = resp.metrics?.totalDurationUs ?: 0
                    ))
                }

                // ── Execute transformation ──
                post("/api/execute/{id}") {
                    val id = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing transformation ID"))
                    val req = call.receive<ExecuteRequestBody>()

                    val proto = ExecuteRequest.newBuilder()
                        .setTransformationId(id)
                        .setPayload(ByteString.copyFromUtf8(req.payload))
                        .setContentType(req.contentType)
                        .setCorrelationId(req.correlationId ?: "")
                        .build()
                    val resp = TransportHandlers.handleExecute(proto, engine)

                    val status = if (resp.success) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity
                    call.respond(status, ExecuteResponseBody(
                        success = resp.success,
                        output = if (resp.success) resp.output.toStringUtf8() else null,
                        error = if (!resp.success) resp.error else null,
                        errorPhase = if (!resp.success && resp.errorPhase != ErrorPhase.ERROR_PHASE_UNSPECIFIED) resp.errorPhase.name else null,
                        durationUs = resp.metrics?.executeDurationUs ?: 0,
                        correlationId = req.correlationId,
                        validationErrors = resp.validationErrorsList.map {
                            ValidationErrorDto(it.message, it.path, it.severity)
                        }
                    ))
                }

                // ── Batch execute ──
                post("/api/execute-batch/{id}") {
                    val id = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing transformation ID"))
                    val req = call.receive<BatchRequest>()

                    val proto = ExecuteBatchRequest.newBuilder()
                        .setTransformationId(id)
                    req.items.forEach { item ->
                        proto.addItems(BatchItem.newBuilder()
                            .setPayload(ByteString.copyFromUtf8(item.payload))
                            .setContentType(item.contentType)
                            .setCorrelationId(item.correlationId ?: "")
                            .build())
                    }
                    val resp = TransportHandlers.handleExecuteBatch(proto.build(), engine)

                    call.respond(HttpStatusCode.OK, BatchResponse(
                        results = resp.resultsList.map { r ->
                            ExecuteResponseBody(
                                success = r.success,
                                output = if (r.success) r.output.toStringUtf8() else null,
                                error = if (!r.success) r.error else null,
                                durationUs = r.metrics?.executeDurationUs ?: 0,
                                correlationId = r.correlationId
                            )
                        }
                    ))
                }

                // ── Pipeline execute ──
                post("/api/execute-pipeline") {
                    val req = call.receive<PipelineRequest>()

                    val proto = ExecutePipelineRequest.newBuilder()
                        .addAllTransformationIds(req.transformationIds)
                        .setPayload(ByteString.copyFromUtf8(req.payload))
                        .setContentType(req.contentType)
                        .setCorrelationId(req.correlationId ?: "")
                        .build()
                    val resp = TransportHandlers.handleExecutePipeline(proto, engine)

                    val status = if (resp.success) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity
                    call.respond(status, PipelineResponse(
                        success = resp.success,
                        output = if (resp.success) resp.output.toStringUtf8() else null,
                        error = if (!resp.success) resp.error else null,
                        stagesCompleted = resp.stagesCompleted,
                        totalDurationUs = resp.totalDurationUs,
                        correlationId = req.correlationId
                    ))
                }

                // ── Unload transformation ──
                delete("/api/transform/{id}") {
                    val id = call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing transformation ID"))

                    val proto = UnloadTransformationRequest.newBuilder()
                        .setTransformationId(id)
                        .build()
                    val resp = TransportHandlers.handleUnload(proto, engine)

                    call.respond(HttpStatusCode.OK, mapOf("success" to resp.success))
                }

                // ── EF03: Data plane discovery (no auth required) ──
                get("/api/transformations") {
                    val transformations = registry.list().map { tx ->
                        mapOf(
                            "name" to tx.name,
                            "status" to if (tx.paused) "paused" else "ready",
                            "strategy" to tx.strategy.name
                        )
                    }
                    call.respond(HttpStatusCode.OK, mapOf(
                        "transformations" to transformations
                    ))
                }

                // ── Health (data plane) ──
                get("/api/health") {
                    val resp = TransportHandlers.handleHealth(engine)

                    call.respond(HttpStatusCode.OK, HealthResponseDto(
                        state = resp.state,
                        uptimeMs = resp.uptimeMs,
                        loadedTransformations = resp.loadedTransformations,
                        totalExecutions = resp.totalExecutions,
                        totalErrors = resp.totalErrors
                    ))
                }

                // ── EF05: Dapr OPTIONS probe handler ──
                // Dapr sends OPTIONS /{bindingName} at startup to check if the app handles this binding.
                // Always respond 200 — transformations may be uploaded later via Admin API.
                options("/{bindingName}") {
                    val bindingName = call.parameters["bindingName"] ?: "unknown"
                    daprProbedBindings.add(bindingName)
                    val hasTransform = registry.get(bindingName) != null
                    logger.info("Dapr OPTIONS probe: binding='{}' — accepted (transformation loaded: {}, total probed: {})",
                        bindingName, hasTransform, daprProbedBindings.size)
                    call.respond(HttpStatusCode.OK)
                }

                // ── EF05: Dapr default root path for input binding delivery ──
                // Dapr delivers messages to POST /{component-name} by default.
                // Also keep /api/dapr/input/{bindingName} for backward compatibility.
                post("/{bindingName}") {
                    handleDaprInput(call, registry)
                }
                post("/api/dapr/input/{bindingName}") {
                    handleDaprInput(call, registry)
                }

                // ── EF06: Dapr pub/sub subscription endpoint ──
                // Returns subscriptions derived from loaded transformations with topic config.
                // Dapr calls this once at sidecar startup.
                get("/dapr/subscribe") {
                    val subscriptions = registry.list()
                        .filter { it.config.input?.topic != null }
                        .map { tx ->
                            mapOf(
                                "pubsubname" to "utlxe-servicebus",
                                "topic" to tx.config.input!!.topic!!,
                                "route" to "/pubsub/${tx.name}"
                            )
                        }
                    logger.info("Dapr /dapr/subscribe — returning {} subscription(s)", subscriptions.size)
                    if (logger.isDebugEnabled && subscriptions.isNotEmpty()) {
                        logger.debug("/dapr/subscribe: {}", subscriptions)
                    }
                    call.respond(HttpStatusCode.OK, subscriptions)
                }

                // ── EF06: Dapr pub/sub message delivery ──
                // Dapr delivers pub/sub messages to POST /pubsub/{name} (route from /dapr/subscribe).
                // Messages arrive as CloudEvents (structured or binary mode).
                post("/pubsub/{name}") {
                    handlePubSubInput(call, registry)
                }
            }
        }.start(wait = false)

        logger.info("HttpTransport: listening on {}:{}", host, port)

        // Block until shutdown (same contract as other transports)
        Thread.currentThread().join()
    }

    /**
     * EF04 + EF05: Dapr input binding handler.
     * Shared by POST /{bindingName} and POST /api/dapr/input/{bindingName}.
     *
     * Reads correlation metadata from Dapr headers (EF04),
     * returns 503 if transformation not loaded (EF05),
     * propagates traceparent/tracestate to output binding (EF04),
     * forwards messaging triad and custom properties to output (EF04).
     */
    private suspend fun handleDaprInput(call: ApplicationCall, registry: TransformationRegistry) {
        // Heap backpressure — reject before processing to prevent OOM
        if (engine.isHeapPressure()) {
            logger.warn("Heap backpressure — rejecting request (threshold: {}%)", (engine.heapBackpressureThreshold * 100).toInt())
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                "success" to false,
                "error" to "Heap memory pressure — message will be retried",
                "error_code" to "HEAP_PRESSURE"
            ))
            return
        }

        val bindingName = call.parameters["bindingName"] ?: "default"
        val transformId = call.request.header("X-UTLXe-Transform") ?: bindingName

        // EF05: Return 503 if transformation not loaded (not 500 — it's "not ready yet")
        val instance = registry.get(transformId)
        if (instance == null) {
            logger.warn("Dapr binding '{}' — no transformation '{}' loaded. Upload via Admin API.", bindingName, transformId)
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                "success" to false,
                "error" to "No transformation loaded for binding '$transformId'",
                "error_code" to "BUNDLE_NOT_LOADED",
                "binding" to bindingName
            ))
            return
        }

        // EF03: Return 503 if transformation is paused
        if (instance.paused) {
            val rejectedMsgId = call.request.header("metadata.MessageId") ?: call.request.header("MessageId") ?: "unknown"
            logger.info("Dapr binding '{}' — transformation '{}' PAUSED, rejecting MessageId={}", bindingName, transformId, rejectedMsgId)
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                "success" to false,
                "error" to "Transformation '$transformId' is paused",
                "error_code" to "TRANSFORMATION_NOT_FOUND",
                "binding" to bindingName
            ))
            return
        }

        // Resolve output: env var > new messaging config > legacy outputBinding > header > none
        val envOverride = System.getenv("UTLXE_OUTPUT_BINDING_${bindingName.replace("-", "_").uppercase()}")
        val messagingOutput = instance.config.outputMessaging
        val configBinding = instance.config.outputBinding
        val headerBinding = call.request.header("X-UTLXe-Output-Binding")
        // EF10: Prefer new messaging config (queue/topic/eventhub), fall back to legacy outputBinding
        val outputBinding = envOverride
            ?: messagingOutput?.resourceName
            ?: configBinding
            ?: headerBinding
        val outputIsPubSub = messagingOutput?.isPubSub == true && envOverride == null

        // Dapr sends the raw message payload in the body
        val payload = call.receiveText()
        val contentType = call.request.contentType().toString()

        // EF04: Read correlation metadata from Dapr headers (metadata.* prefix per Dapr convention)
        val incomingMessageId = call.request.header("metadata.MessageId")
            ?: call.request.header("MessageId")
            ?: UuidV7.generate()
        val incomingCorrelationId = call.request.header("metadata.CorrelationId")
            ?: call.request.header("CorrelationId")
            ?: ""
        val incomingCausationId = call.request.header("metadata.CausationId")
            ?: call.request.header("CausationId")
            ?: ""
        val traceparent = call.request.header("traceparent") ?: ""
        val tracestate = call.request.header("tracestate") ?: ""

        logger.info("[{}] MessageId={} CorrelationId={} binding={} payload={}B",
            transformId, incomingMessageId, incomingCorrelationId, bindingName, payload.length)

        if (logger.isDebugEnabled) {
            val headers = call.request.headers.entries()
                .filter { it.key.startsWith("metadata.") || it.key.startsWith("ce-") || it.key == "traceparent" }
                .joinToString { "${it.key}=${it.value.firstOrNull()}" }
            logger.debug("[{}] Dapr input headers: {}", transformId, headers)
            logger.debug("[{}] CausationId={} contentType={} outputBinding={} isPubSub={}",
                transformId, incomingCausationId, contentType, outputBinding, outputIsPubSub)
        }

        // Build the execute request with full messaging triad
        val execProto = ExecuteRequest.newBuilder()
            .setTransformationId(transformId)
            .setPayload(ByteString.copyFromUtf8(payload))
            .setContentType(contentType)
            .setCorrelationId(incomingCorrelationId)
            .setMessageId(incomingMessageId)
            .setCausationId(incomingCausationId)
            .setTraceparent(traceparent)
            .setTracestate(tracestate)
            .build()
        val execResp = TransportHandlers.handleExecute(execProto, engine)

        // Generate output message ID (UUIDv7)
        val outputMessageId = UuidV7.generate()

        if (!execResp.success) {
            logger.error("[{}] MessageId={} CorrelationId={} FAILED: {}",
                transformId, incomingMessageId, incomingCorrelationId, execResp.error)
            // EF03: Record error in ring buffer
            instance.recordErrorDetail(org.apache.utlx.engine.registry.ErrorEntry(
                message = execResp.error ?: "Unknown error",
                phase = execResp.errorPhase?.name,
                messageId = incomingMessageId,
                correlationId = incomingCorrelationId,
                inputPreview = payload.take(200)
            ))
            call.respond(HttpStatusCode.InternalServerError, mapOf(
                "success" to false,
                "error" to execResp.error,
                "error_code" to "TRANSFORMATION_FAILED",
                "binding" to bindingName,
                "message_id" to outputMessageId,
                "correlation_id" to incomingCorrelationId,
                "causation_id" to incomingMessageId
            ))
            return
        }

        val output = execResp.output.toStringUtf8()

        // EF04/EF10: Forward transformed result to Dapr (binding or pub/sub) with messaging triad
        if (outputBinding != null) {
            try {
                // EF10: Use different Dapr endpoint for pub/sub vs binding
                val daprUrl = if (outputIsPubSub) {
                    "http://localhost:3500/v1.0/publish/utlxe-servicebus/$outputBinding"
                } else {
                    "http://localhost:3500/v1.0/bindings/$outputBinding"
                }

                // Build output metadata including messaging triad
                val outputMetadata = mutableMapOf(
                    "source-binding" to bindingName,
                    "transform-id" to transformId,
                    "MessageId" to outputMessageId,
                    "CausationId" to incomingMessageId
                )
                if (incomingCorrelationId.isNotEmpty()) {
                    outputMetadata["CorrelationId"] = incomingCorrelationId
                }

                // Forward custom properties from input (metadata.* headers)
                call.request.headers.entries().forEach { (key, values) ->
                    if (key.startsWith("metadata.") && key !in listOf(
                            "metadata.MessageId", "metadata.CorrelationId", "metadata.CausationId")) {
                        outputMetadata[key.removePrefix("metadata.")] = values.first()
                    }
                }

                // Dapr binding vs pub/sub have different payload formats
                val daprPayload = if (outputIsPubSub) {
                    // Pub/sub: just the data (metadata via headers)
                    output
                } else {
                    // Binding: operation + data + metadata envelope
                    com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(mapOf(
                        "operation" to "create",
                        "data" to output,
                        "metadata" to outputMetadata
                    ))
                }

                logger.debug("[{}] Output → {} ({} bytes)", transformId, daprUrl, daprPayload.length)

                val outputStart = System.nanoTime()
                val url = java.net.URL(daprUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                // EF04: Propagate W3C Trace Context to output
                if (traceparent.isNotEmpty()) conn.setRequestProperty("traceparent", traceparent)
                if (tracestate.isNotEmpty()) conn.setRequestProperty("tracestate", tracestate)
                // EF10: For pub/sub, propagate metadata as headers
                if (outputIsPubSub) {
                    outputMetadata.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                }
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 10000
                conn.outputStream.write(daprPayload.toByteArray())
                conn.outputStream.flush()
                val daprStatus = conn.responseCode
                val outputMs = (System.nanoTime() - outputStart) / 1_000_000
                val daprResponseBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                conn.disconnect()

                if (daprStatus !in 200..299) {
                    logger.warn("Dapr output {} '{}' returned {} ({}ms) body={}",
                        if (outputIsPubSub) "topic" else "binding", outputBinding, daprStatus, outputMs,
                        daprResponseBody?.take(500))
                } else {
                    logger.debug("[{}] Output → {} '{}' OK ({}ms) OutputMessageId={}",
                        transformId, if (outputIsPubSub) "topic" else "binding", outputBinding, outputMs, outputMessageId)
                }
            } catch (e: Exception) {
                logger.error("Failed to send to Dapr output '{}': {}", outputBinding, e.message)
            }
        }

        logger.info("[{}] MessageId={} → OutputMessageId={} CorrelationId={} in {}us",
            transformId, incomingMessageId, outputMessageId, incomingCorrelationId,
            execResp.metrics?.executeDurationUs ?: 0)

        // Return success to Dapr (acknowledges the message)
        call.respond(HttpStatusCode.OK, mapOf(
            "success" to true,
            "binding" to bindingName,
            "outputBinding" to outputBinding,
            "message_id" to outputMessageId,
            "correlation_id" to incomingCorrelationId,
            "causation_id" to incomingMessageId,
            "durationUs" to (execResp.metrics?.executeDurationUs ?: 0)
        ))
    }

    /**
     * EF06: Dapr pub/sub input handler.
     * Receives CloudEvents from Dapr pub/sub delivery (POST /pubsub/{name}).
     *
     * Handles both CloudEvents modes:
     * - Structured: body is CloudEvents JSON with `data` field containing the payload
     * - Binary: body is raw payload, CloudEvents metadata in `ce-*` headers
     *
     * Unwraps CloudEvents, extracts correlation metadata, delegates to transformation.
     */
    private suspend fun handlePubSubInput(call: ApplicationCall, registry: TransformationRegistry) {
        // Heap backpressure — reject before processing to prevent OOM
        if (engine.isHeapPressure()) {
            logger.warn("Heap backpressure — rejecting pub/sub request (threshold: {}%)", (engine.heapBackpressureThreshold * 100).toInt())
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                "success" to false,
                "error" to "Heap memory pressure — message will be retried",
                "error_code" to "HEAP_PRESSURE"
            ))
            return
        }

        val transformName = call.parameters["name"] ?: "default"

        val instance = registry.get(transformName)
        if (instance == null) {
            logger.warn("Pub/sub delivery for '{}' — no transformation loaded", transformName)
            call.respond(HttpStatusCode.NotFound, mapOf(
                "success" to false,
                "error" to "No transformation loaded for '$transformName'",
                "error_code" to "TRANSFORMATION_NOT_FOUND"
            ))
            return
        }

        if (instance.paused) {
            logger.info("Pub/sub delivery for '{}' — transformation is paused", transformName)
            call.respond(HttpStatusCode.TooManyRequests, mapOf(
                "success" to false,
                "error" to "Transformation '$transformName' is paused by operator",
                "error_code" to "TRANSFORMATION_PAUSED",
                "retry_after_seconds" to 300
            ))
            call.response.header("Retry-After", "300")
            return
        }

        val rawBody = call.receiveText()

        // Detect CloudEvents mode and extract payload + metadata
        val ceSpecVersion = call.request.header("ce-specversion")
        val (payload, ceId, ceSource, ceType) = if (ceSpecVersion != null) {
            logger.debug("[{}] PubSub binary CloudEvents: ce-specversion={} ce-type={} ce-source={} ce-id={}",
                transformName, ceSpecVersion, call.request.header("ce-type"),
                call.request.header("ce-source"), call.request.header("ce-id"))
            CloudEventsData(rawBody,
                call.request.header("ce-id"),
                call.request.header("ce-source"),
                call.request.header("ce-type"))
        } else {
            val result = unwrapCloudEvents(rawBody)
            if (result.id != null) {
                logger.debug("[{}] PubSub structured CloudEvents: id={} source={} type={} unwrapped={}B",
                    transformName, result.id, result.source, result.type, result.payload.length)
            } else {
                logger.debug("[{}] PubSub raw payload (no CloudEvents envelope): {}B", transformName, rawBody.length)
            }
            result
        }

        // EF04: Resolve correlation metadata (CloudEvents → Dapr headers → generate)
        val incomingMessageId = call.request.header("metadata.MessageId")
            ?: ceId
            ?: UuidV7.generate()
        val incomingCorrelationId = call.request.header("metadata.CorrelationId")
            ?: call.request.header("CorrelationId")
            ?: ""
        val incomingCausationId = call.request.header("metadata.CausationId")
            ?: call.request.header("CausationId")
            ?: ""
        val traceparent = call.request.header("traceparent") ?: ""
        val tracestate = call.request.header("tracestate") ?: ""

        logger.info("[{}] PubSub MessageId={} CorrelationId={} ceSource={} ceType={} payload={}B",
            transformName, incomingMessageId, incomingCorrelationId, ceSource, ceType, payload.length)

        // Build execute request
        val contentType = call.request.header("ce-datacontenttype")
            ?: call.request.contentType().toString()
        val execProto = ExecuteRequest.newBuilder()
            .setTransformationId(transformName)
            .setPayload(ByteString.copyFromUtf8(payload))
            .setContentType(contentType)
            .setCorrelationId(incomingCorrelationId)
            .setMessageId(incomingMessageId)
            .setCausationId(incomingCausationId)
            .setTraceparent(traceparent)
            .setTracestate(tracestate)
            .build()
        val execResp = TransportHandlers.handleExecute(execProto, engine)

        val outputMessageId = UuidV7.generate()

        if (!execResp.success) {
            logger.error("[{}] PubSub MessageId={} FAILED: {}", transformName, incomingMessageId, execResp.error)
            instance.recordErrorDetail(org.apache.utlx.engine.registry.ErrorEntry(
                message = execResp.error ?: "Unknown error",
                phase = execResp.errorPhase?.name,
                messageId = incomingMessageId,
                correlationId = incomingCorrelationId,
                inputPreview = payload.take(200)
            ))
            call.respond(HttpStatusCode.InternalServerError, mapOf(
                "success" to false,
                "error" to execResp.error,
                "error_code" to "TRANSFORMATION_FAILED"
            ))
            return
        }

        val output = execResp.output.toStringUtf8()

        // Output routing (reuses same logic as bindings — outputMessaging handles pub/sub)
        val messagingOutput = instance.config.outputMessaging
        val outputBinding = messagingOutput?.resourceName ?: instance.config.outputBinding
        val outputIsPubSub = messagingOutput?.isPubSub == true

        if (outputBinding != null) {
            try {
                val daprUrl = if (outputIsPubSub) {
                    "http://localhost:3500/v1.0/publish/utlxe-servicebus/$outputBinding"
                } else {
                    "http://localhost:3500/v1.0/bindings/$outputBinding"
                }

                val outputMetadata = mutableMapOf(
                    "transform-id" to transformName,
                    "MessageId" to outputMessageId,
                    "CausationId" to incomingMessageId
                )
                if (incomingCorrelationId.isNotEmpty()) {
                    outputMetadata["CorrelationId"] = incomingCorrelationId
                }

                val daprPayload = if (outputIsPubSub) output
                else com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(mapOf(
                    "operation" to "create", "data" to output, "metadata" to outputMetadata
                ))

                val url = java.net.URL(daprUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                if (traceparent.isNotEmpty()) conn.setRequestProperty("traceparent", traceparent)
                if (tracestate.isNotEmpty()) conn.setRequestProperty("tracestate", tracestate)
                if (outputIsPubSub) {
                    outputMetadata.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                }
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 10000
                conn.outputStream.write(daprPayload.toByteArray())
                conn.outputStream.flush()
                val daprStatus = conn.responseCode
                conn.disconnect()

                if (daprStatus !in 200..299) {
                    logger.warn("Dapr output {} '{}' returned {}", if (outputIsPubSub) "topic" else "binding", outputBinding, daprStatus)
                }
            } catch (e: Exception) {
                logger.error("Failed to send to Dapr output '{}': {}", outputBinding, e.message)
            }
        }

        logger.info("[{}] PubSub MessageId={} → OutputMessageId={} in {}us",
            transformName, incomingMessageId, outputMessageId, execResp.metrics?.executeDurationUs ?: 0)

        // Return success to Dapr (acknowledges the pub/sub message)
        call.respond(HttpStatusCode.OK, mapOf("success" to true))
    }

    /**
     * EF06: Unwrap CloudEvents structured envelope.
     * If the JSON body has a `specversion` field, extract `data` as the payload.
     * Otherwise return the raw body as-is (not CloudEvents).
     */
    private fun unwrapCloudEvents(rawBody: String): CloudEventsData {
        return try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val tree = mapper.readTree(rawBody)
            if (tree.has("specversion")) {
                val dataNode = tree.get("data")
                val data = when {
                    dataNode == null -> rawBody
                    dataNode.isTextual -> dataNode.asText()
                    else -> mapper.writeValueAsString(dataNode)
                }
                CloudEventsData(data,
                    tree.get("id")?.asText(),
                    tree.get("source")?.asText(),
                    tree.get("type")?.asText())
            } else {
                CloudEventsData(rawBody, null, null, null)
            }
        } catch (_: Exception) {
            CloudEventsData(rawBody, null, null, null)
        }
    }

    override fun stop() {
        server?.let { s ->
            logger.info("HttpTransport: shutting down...")
            s.stop(1, 5, TimeUnit.SECONDS)
        }
        logger.info("HttpTransport stopped")
    }
}

/** EF06: CloudEvents metadata extracted from a pub/sub message. */
private data class CloudEventsData(
    val payload: String,
    val id: String?,
    val source: String?,
    val type: String?
)
