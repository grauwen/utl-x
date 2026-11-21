package com.glomidco.utlx.engine.transport

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
import com.glomidco.utlx.engine.UtlxEngine
import com.glomidco.utlx.engine.proto.*
import com.glomidco.utlx.engine.registry.TransformationRegistry
import com.glomidco.utlx.engine.util.UuidV7
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
    private val maxRequestBodyBytes: Long = MAX_REQUEST_BODY_BYTES,
    /**
     * The Dapr sidecar's HTTP API port. Dapr publishes it to the app as DAPR_HTTP_PORT, so read
     * that first and fall back to Dapr's documented default — the port was previously written as
     * a literal at four call sites, which made it unconfigurable in any deployment that moves it.
     * `DaprIntegration` already took it as a parameter; this class simply had not followed suit.
     */
    private val daprPort: Int =
        System.getenv("DAPR_HTTP_PORT")?.toIntOrNull() ?: DEFAULT_DAPR_PORT
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
        /** Dapr's documented default sidecar HTTP port, used when DAPR_HTTP_PORT is absent. */
        const val DEFAULT_DAPR_PORT = 3500
        // Default 10MB — protects against oversized payloads that would exhaust heap.
        // XML expands 10-50× in UDM; a 10MB XML payload could use 100-500MB of heap.
        const val MAX_REQUEST_BODY_BYTES = 10L * 1024 * 1024
    }

    /**
     * EB21: turn a payload that arrived as TEXT into the bytes the proto field expects.
     *
     * The `payload` field of ExecuteRequest is `bytes`, and `TransportHandlers.handleExecute`
     * decodes it with the charset named in the Content-Type. Encoding it as UTF-8 regardless
     * handed the engine UTF-8 bytes while telling it they were something else, so a caller who
     * declared `charset=ISO-8859-1` honestly got mojibake back with HTTP 200.
     *
     * Encoding with the DECLARED charset closes the round trip: the caller decoded the document
     * to text to put it in a JSON string, and this re-encodes it the same way, so the engine
     * receives the bytes the caller started with.
     *
     * This cannot rescue a payload that was never text -- a JSON string holds no arbitrary bytes,
     * so Avro and Protobuf stay inexpressible on the enveloped routes. Paths that still HAVE the
     * original bytes must pass them with `ByteString.copyFrom`, never through here.
     */
    /**
     * EF24: the media type to answer with, from the transformation's declared output format.
     *
     * Unknown or absent falls back to octet-stream rather than guessing JSON -- a caller is better
     * told "bytes" than told the wrong thing.
     *
     * Two entries need explaining:
     *
     * `odata` serialises to JSON (`ODataJSONSerializer`, with `@odata.*` annotations), so the type
     * is `application/json`. The OData v4 content type carries a metadata level --
     * `application/json;odata.metadata=minimal|full|none` -- but the level is a serializer option
     * that is not visible here, and naming the wrong one is worse than omitting it. An OData client
     * treats a missing parameter as the default.
     *
     * `osch` is EDMX, the OData CSDL schema document (`EDMXSerializer`), which is XML and not JSON
     * -- an easy pair to conflate, since both have "odata" in the name.
     *
     * `usa`, `european`, `french` and `swiss` are regional CSV dialects: separator and decimal
     * conventions differ, the media type does not.
     */
    private fun contentTypeFor(outputFormat: String?): io.ktor.http.ContentType = when (outputFormat?.lowercase()) {
        "json"                -> io.ktor.http.ContentType.Application.Json
        "odata"               -> io.ktor.http.ContentType.Application.Json
        "jsch"                -> io.ktor.http.ContentType("application", "schema+json")
        "xml"                 -> io.ktor.http.ContentType.Application.Xml
        "xsd", "osch"         -> io.ktor.http.ContentType.Application.Xml
        "csv",
        "usa", "european",
        "french", "swiss"     -> io.ktor.http.ContentType.Text.CSV
        "yaml", "yml"         -> io.ktor.http.ContentType("application", "yaml")
        "avro"                -> io.ktor.http.ContentType("application", "avro")
        "protobuf"            -> io.ktor.http.ContentType("application", "x-protobuf")
        "text", "txt"         -> io.ktor.http.ContentType.Text.Plain
        else                  -> io.ktor.http.ContentType.Application.OctetStream
    }

    private fun encodeDeclared(text: String, contentType: String): ByteString {
        val charset = TransportHandlers.parseCharsetFromContentType(contentType) ?: Charsets.UTF_8
        return ByteString.copyFrom(text.toByteArray(charset))
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
                        .setPayload(encodeDeclared(req.payload, req.contentType))
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
                    // EF15: Heap backpressure — reject before processing to prevent OOM
                    if (engine.isHeapPressure()) {
                        call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                            "success" to false,
                            "error" to "Heap memory pressure — retry later",
                            "error_code" to "HEAP_PRESSURE"
                        ))
                        return@post
                    }
                    val id = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing transformation ID"))
                    // Note: paused check is in TransportHandlers.handleExecute() — covers all transports
                    val req = call.receive<ExecuteRequestBody>()

                    val proto = ExecuteRequest.newBuilder()
                        .setTransformationId(id)
                        .setPayload(encodeDeclared(req.payload, req.contentType))
                        .setContentType(req.contentType)
                        .setCorrelationId(req.correlationId ?: "")
                        .build()
                    val resp = TransportHandlers.handleExecute(proto, engine)

                    val status = when {
                        resp.success -> HttpStatusCode.OK
                        resp.errorClass == ErrorClass.TRANSIENT -> HttpStatusCode.ServiceUnavailable
                        else -> HttpStatusCode.UnprocessableEntity
                    }
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

                // ── EF24: the published data path ──
                // Raw body, id in the path, Content-Type honoured, output returned in its own
                // format. This is the shape `handleDaprInput`, `handlePubSubInput` and
                // `/admin/transformations/{name}/test` already use, so it is a proven path rather
                // than a new one -- see docs/features/EF24-*.md.
                //
                // Named to the convention the admin API already follows 13 times over --
                // transformations/{name}/{action} -- rather than under a /v1/ prefix nothing else
                // in this codebase uses. An unversioned path does not foreclose a versioned one
                // later; a new path can simply appear if the shape ever has to break. The plural
                // and the /run suffix also keep it clear of `DELETE /api/transform/{id}`, which
                // unloads a transformation and must never be published.
                //
                // Deliberately NOT a variant of /api/execute/{id}: that route carries the payload
                // as a JSON string, which cannot hold arbitrary bytes, so Avro and Protobuf are
                // inexpressible on it (EB21). Nothing here authenticates -- the credential belongs
                // in the proxy, and a deployment that publishes this route without one is a
                // remote execution path.
                post("/api/transformations/{id}/run") {
                    // EF15: reject before processing, as /api/execute/{id} does.
                    if (engine.isHeapPressure()) {
                        call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                            "error" to "Heap memory pressure \u2014 retry later",
                            "error_code" to "HEAP_PRESSURE"
                        ))
                        return@post
                    }
                    val id = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest,
                            mapOf("error" to "Missing transformation ID",
                                  "error_code" to "BAD_REQUEST"))

                    // 404 rather than letting handleExecute answer 503 for an unknown name: a
                    // caller that typed the wrong id should not be told to retry later.
                    val instance = registry.get(id)
                        ?: return@post call.respond(HttpStatusCode.NotFound,
                            mapOf("error" to "No transformation '$id' is loaded",
                                  "error_code" to "TRANSFORMATION_NOT_FOUND"))

                    val bytes = call.receive<ByteArray>()
                    val correlationId = call.request.header("X-Correlation-Id") ?: ""
                    val proto = ExecuteRequest.newBuilder()
                        .setTransformationId(id)
                        // EB21: the body as sent. handleExecute derives the charset from the
                        // content type, so nothing here may decode or re-encode it.
                        .setPayload(ByteString.copyFrom(bytes))
                        .setContentType(call.request.contentType().toString())
                        .setCorrelationId(correlationId)
                        .setMessageId(call.request.header("X-Message-Id") ?: UuidV7.generate())
                        .build()
                    val resp = TransportHandlers.handleExecute(proto, engine)

                    if (correlationId.isNotEmpty()) {
                        call.response.header("X-Correlation-Id", correlationId)
                    }

                    if (resp.success) {
                        // The output in its OWN format, not nested inside a JSON object. A caller
                        // that asked for CSV gets CSV.
                        call.respondBytes(
                            resp.output.toByteArray(),
                            contentTypeFor(instance.strategy.getHeaderSchemaInfo()?.outputFormat),
                            HttpStatusCode.OK
                        )
                    } else {
                        // Status codes, not a success flag in a 200 body. Same classification
                        // /api/execute/{id} uses.
                        val status = if (resp.errorClass == ErrorClass.TRANSIENT)
                            HttpStatusCode.ServiceUnavailable else HttpStatusCode.UnprocessableEntity
                        call.respond(status, mapOf(
                            "error" to resp.error,
                            "error_code" to resp.errorCode.name,
                            "errorPhase" to if (resp.errorPhase != ErrorPhase.ERROR_PHASE_UNSPECIFIED)
                                resp.errorPhase.name else null,
                            "validationErrors" to resp.validationErrorsList.map {
                                mapOf("message" to it.message, "path" to it.path, "severity" to it.severity)
                            }
                        ))
                    }
                }

                // ── Batch execute ──
                post("/api/execute-batch/{id}") {
                    if (engine.isHeapPressure()) {
                        call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                            "success" to false,
                            "error" to "Heap memory pressure — retry later",
                            "error_code" to "HEAP_PRESSURE"
                        ))
                        return@post
                    }
                    val id = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing transformation ID"))
                    val req = call.receive<BatchRequest>()

                    val proto = ExecuteBatchRequest.newBuilder()
                        .setTransformationId(id)
                    req.items.forEach { item ->
                        proto.addItems(BatchItem.newBuilder()
                            .setPayload(encodeDeclared(item.payload, item.contentType))
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
                    if (engine.isHeapPressure()) {
                        call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                            "success" to false,
                            "error" to "Heap memory pressure — retry later",
                            "error_code" to "HEAP_PRESSURE"
                        ))
                        return@post
                    }
                    val req = call.receive<PipelineRequest>()

                    val proto = ExecutePipelineRequest.newBuilder()
                        .addAllTransformationIds(req.transformationIds)
                        .setPayload(encodeDeclared(req.payload, req.contentType))
                        .setContentType(req.contentType)
                        .setCorrelationId(req.correlationId ?: "")
                        .build()
                    val resp = TransportHandlers.handleExecutePipeline(proto, engine)

                    val status = when {
                        resp.success -> HttpStatusCode.OK
                        resp.errorClass == ErrorClass.TRANSIENT -> HttpStatusCode.ServiceUnavailable
                        else -> HttpStatusCode.UnprocessableEntity
                    }
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
                                "pubsubname" to com.glomidco.utlx.engine.config.DaprComponents.pubsubName,
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
    /**
     * EF26: find a transformation whose own `transform.yaml` claims this queue or topic as its
     * input. Only consulted when no per-message selector was sent and no transformation is named
     * after the component, so it costs nothing on the paths that already worked.
     */
    private fun resolveByDeclaredInput(registry: TransformationRegistry, bindingName: String): String? {
        val matches = registry.list()
            .filter { it.config.input?.queue == bindingName || it.config.input?.topic == bindingName }
            .sortedBy { it.name }
        if (matches.isEmpty()) return null
        if (matches.size > 1) {
            // Ambiguous configuration, not a routing decision. Sorted so the choice is at least
            // deterministic, and logged so it is fixable.
            logger.warn("Dapr binding '{}' — {} transformations declare it as their input ({}). " +
                "Using '{}'. Give them distinct queues, or route per message with " +
                "metadata.X-UTLXe-Transform.",
                bindingName, matches.size, matches.joinToString { it.name }, matches.first().name)
        }
        return matches.first().name
    }

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

        // EF26: decide WHICH transformation handles this message, most specific first.
        //
        // Dapr delivers an input binding to POST /{component-name}, so `bindingName` is the Dapr
        // COMPONENT's name -- not a transformation's. Treating it directly as a transformation id,
        // which is all this used to do, forced the two to be identical: with the Marketplace
        // template's component called "servicebus-input", a customer had to name their
        // transformation "servicebus-input" or every message got a 503. Nothing documented that,
        // and it also capped a deployment at one queue.
        //
        //   1. metadata.X-UTLXe-Transform -- a PER-MESSAGE selector. Dapr forwards Service Bus
        //      application properties as metadata.* headers; the correlation reads further down
        //      depend on exactly that. So one queue can feed many transformations, chosen by the
        //      sender.
        //   2. X-UTLXe-Transform -- the unprefixed form, for a caller posting directly.
        //   3. a transformation whose own transform.yaml declares this queue or topic as its
        //      input. This makes the transformation's config authoritative and frees its name.
        //   4. failing all that, a transformation named after the component -- the historical
        //      behaviour, kept so nothing that works today stops working.
        val selector = call.request.header("metadata.X-UTLXe-Transform")
            ?: call.request.header("X-UTLXe-Transform")

        // Order matters for cost as well as for precedence. The exact-name lookup is a
        // ConcurrentHashMap hit; scanning the registry for a declared input is O(n) plus a sort,
        // and this runs on every delivered message. So try the cheap path first and scan only
        // when it misses. That also makes this change strictly ADDITIVE: whenever the old
        // one-line resolution found a transformation, it still finds the same one by the same
        // route, and the new behaviour applies only where the old code answered 503.
        val transformId = selector
            ?: registry.get(bindingName)?.name
            ?: resolveByDeclaredInput(registry, bindingName)
            ?: bindingName

        // EF05: Return 503 if transformation not loaded (not 500 — it's "not ready yet")
        val instance = registry.get(transformId)
        if (instance == null) {
            logger.warn("Dapr binding '{}' — no transformation '{}' loaded. Name a transformation " +
                "after the binding, declare the binding as its input in transform.yaml, or set " +
                "the metadata.X-UTLXe-Transform message property.", bindingName, transformId)
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

        // EB01: Read raw bytes from Dapr — preserves encoding for non-UTF-8 payloads
        val payloadBytes = call.receive<ByteArray>()
        val contentType = call.request.contentType().toString()
        val payload = String(payloadBytes, Charsets.UTF_8)  // String fallback for logging/existing code

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
            // EB21: the raw request body, not the UTF-8 String above. A Service Bus message in
            // ISO-8859-1 or UTF-16 lost bytes outright here: String(bytes, UTF_8) turns an invalid
            // sequence into U+FFFD, and no later charset handling can recover it.
            .setPayload(ByteString.copyFrom(payloadBytes))
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
            instance.recordErrorDetail(com.glomidco.utlx.engine.registry.ErrorEntry(
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
                    "http://localhost:$daprPort/v1.0/publish/${com.glomidco.utlx.engine.config.DaprComponents.pubsubName}/$outputBinding"
                } else {
                    "http://localhost:$daprPort/v1.0/bindings/$outputBinding"
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

        // EB01: Read raw bytes — preserves encoding for non-UTF-8 payloads
        val rawBodyBytes = call.receive<ByteArray>()
        val rawBody = String(rawBodyBytes, Charsets.UTF_8)  // String for CloudEvents parsing

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
            // EB21: when nothing was unwrapped, `payload` IS the body and the original bytes are
            // exact. unwrapCloudEvents returns rawBody unchanged where there is no envelope, so the
            // comparison is reliable. A payload extracted from a structured CloudEvents document
            // came out of a JSON string and can only be re-encoded, not recovered.
            .setPayload(
                if (payload == rawBody) ByteString.copyFrom(rawBodyBytes)
                else encodeDeclared(payload, contentType)
            )
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
            instance.recordErrorDetail(com.glomidco.utlx.engine.registry.ErrorEntry(
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
                    "http://localhost:$daprPort/v1.0/publish/${com.glomidco.utlx.engine.config.DaprComponents.pubsubName}/$outputBinding"
                } else {
                    "http://localhost:$daprPort/v1.0/bindings/$outputBinding"
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
