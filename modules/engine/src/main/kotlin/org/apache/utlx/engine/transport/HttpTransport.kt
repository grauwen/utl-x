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
                    val loadResp = TransportHandlers.handleLoadTransformation(loadProto, engine, registry)

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
                    val execResp = TransportHandlers.handleExecute(execProto, registry)

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

                    val resp = TransportHandlers.handleLoadTransformation(proto.build(), engine, registry)

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
                    val resp = TransportHandlers.handleExecute(proto, registry)

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
                    val resp = TransportHandlers.handleExecuteBatch(proto.build(), registry)

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
                    val resp = TransportHandlers.handleExecutePipeline(proto, registry)

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
                    val resp = TransportHandlers.handleUnload(proto, registry)

                    call.respond(HttpStatusCode.OK, mapOf("success" to resp.success))
                }

                // ── Health (data plane) ──
                get("/api/health") {
                    val resp = TransportHandlers.handleHealth(engine, registry)

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
                    logger.info("Dapr OPTIONS probe for binding '{}' — accepted", bindingName)
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

                // Dapr pub/sub subscription endpoint
                get("/dapr/subscribe") {
                    call.respond(HttpStatusCode.OK, emptyList<Any>())
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

        // Resolve output binding: env var > transform config > header > none
        val envOverride = System.getenv("UTLXE_OUTPUT_BINDING_${bindingName.replace("-", "_").uppercase()}")
        val configBinding = instance.config.outputBinding
        val headerBinding = call.request.header("X-UTLXe-Output-Binding")
        val outputBinding = envOverride ?: configBinding ?: headerBinding

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

        logger.info("[{}] MessageId={} CorrelationId={} CausationId={} binding={} payload={}B",
            transformId, incomingMessageId, incomingCorrelationId, incomingCausationId,
            bindingName, payload.length)

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
        val execResp = TransportHandlers.handleExecute(execProto, registry)

        // Generate output message ID (UUIDv7)
        val outputMessageId = UuidV7.generate()

        if (!execResp.success) {
            logger.error("[{}] MessageId={} CorrelationId={} FAILED: {}",
                transformId, incomingMessageId, incomingCorrelationId, execResp.error)
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

        // EF04: Forward transformed result to Dapr output binding with messaging triad
        if (outputBinding != null) {
            try {
                val daprUrl = "http://localhost:3500/v1.0/bindings/$outputBinding"

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

                val daprPayload = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(mapOf(
                    "operation" to "create",
                    "data" to output,
                    "metadata" to outputMetadata
                ))

                val url = java.net.URL(daprUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                // EF04: Propagate W3C Trace Context to output binding
                if (traceparent.isNotEmpty()) conn.setRequestProperty("traceparent", traceparent)
                if (tracestate.isNotEmpty()) conn.setRequestProperty("tracestate", tracestate)
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 10000
                conn.outputStream.write(daprPayload.toByteArray())
                conn.outputStream.flush()
                val daprStatus = conn.responseCode
                conn.disconnect()

                if (daprStatus !in 200..299) {
                    logger.warn("Dapr output binding '{}' returned status {}", outputBinding, daprStatus)
                } else {
                    logger.debug("[{}] output to binding '{}' succeeded. OutputMessageId={}",
                        transformId, outputBinding, outputMessageId)
                }
            } catch (e: Exception) {
                logger.error("Failed to send to Dapr output binding '{}': {}", outputBinding, e.message)
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

    override fun stop() {
        server?.let { s ->
            logger.info("HttpTransport: shutting down...")
            s.stop(1, 5, TimeUnit.SECONDS)
        }
        logger.info("HttpTransport stopped")
    }
}
