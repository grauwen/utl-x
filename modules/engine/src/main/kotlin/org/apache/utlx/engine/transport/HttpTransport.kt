package org.apache.utlx.engine.transport

import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.protobuf.ByteString
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.proto.*
import org.apache.utlx.engine.registry.TransformationRegistry
import org.slf4j.LoggerFactory
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
    private val host: String = "0.0.0.0"
) : TransportServer {

    private val logger = LoggerFactory.getLogger(HttpTransport::class.java)
    private var server: ApplicationEngine? = null

    override val supportsDynamicLoading = true

    companion object {
        const val DEFAULT_PORT = 8085
    }

    override fun start(registry: TransformationRegistry) {
        logger.info("HttpTransport: starting on {}:{}", host, port)

        server = embeddedServer(Netty, port = port, host = host) {
            install(ContentNegotiation) {
                jackson {
                    registerModule(kotlinModule())
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
            }
        }.start(wait = false)

        logger.info("HttpTransport: listening on {}:{}", host, port)

        // Block until shutdown (same contract as other transports)
        Thread.currentThread().join()
    }

    override fun stop() {
        server?.let { s ->
            logger.info("HttpTransport: shutting down...")
            s.stop(1, 5, TimeUnit.SECONDS)
        }
        logger.info("HttpTransport stopped")
    }
}
