package org.apache.utlx.engine.transport

import com.google.protobuf.ByteString
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.proto.*
import org.apache.utlx.engine.registry.TransformationInstance
import org.apache.utlx.engine.registry.TransformationRegistry
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * StdioProtoTransport — varint-delimited protobuf over stdin/stdout.
 *
 * Protocol: each message is a StdioEnvelope (type + payload) written as
 * varint-delimited bytes using protobuf's writeDelimitedTo/parseDelimitedFrom.
 *
 * Used for Open-M integration: the Go wrapper sends LoadTransformation requests
 * at startup, then Execute requests during runtime. All logging goes to stderr.
 *
 * Phase B: single-threaded sequential processing (Model 1).
 * Phase E will add multiplexed concurrent processing (Model 2).
 */
class StdioProtoTransport(private val engine: UtlxEngine) : TransportServer {

    private val logger = LoggerFactory.getLogger(StdioProtoTransport::class.java)
    private val running = AtomicBoolean(false)

    override val supportsDynamicLoading = true

    override fun start(registry: TransformationRegistry) {
        running.set(true)

        val input = BufferedInputStream(System.`in`)
        val output = BufferedOutputStream(System.out)

        logger.info("StdioProtoTransport: ready for messages")

        while (running.get()) {
            val envelope = try {
                StdioEnvelope.parseDelimitedFrom(input) ?: break // EOF
            } catch (e: Exception) {
                if (!running.get()) break // shutdown
                logger.error("Error reading envelope: {}", e.message, e)
                break
            }

            val responseEnvelope = dispatch(envelope, registry)

            try {
                responseEnvelope.writeDelimitedTo(output)
                output.flush()
            } catch (e: Exception) {
                if (!running.get()) break
                logger.error("Error writing response: {}", e.message, e)
                break
            }
        }

        logger.info("StdioProtoTransport: message loop ended")
    }

    override fun stop() {
        running.set(false)
        logger.info("StdioProtoTransport stopped")
    }

    private fun dispatch(envelope: StdioEnvelope, registry: TransformationRegistry): StdioEnvelope {
        return when (envelope.type) {
            MessageType.LOAD_TRANSFORMATION_REQUEST -> {
                val req = LoadTransformationRequest.parseFrom(envelope.payload)
                val resp = handleLoadTransformation(req, registry)
                StdioEnvelope.newBuilder()
                    .setType(MessageType.LOAD_TRANSFORMATION_RESPONSE)
                    .setPayload(resp.toByteString())
                    .build()
            }

            MessageType.EXECUTE_REQUEST -> {
                val req = ExecuteRequest.parseFrom(envelope.payload)
                val resp = handleExecute(req, registry)
                StdioEnvelope.newBuilder()
                    .setType(MessageType.EXECUTE_RESPONSE)
                    .setPayload(resp.toByteString())
                    .build()
            }

            MessageType.EXECUTE_BATCH_REQUEST -> {
                val req = ExecuteBatchRequest.parseFrom(envelope.payload)
                val resp = handleExecuteBatch(req, registry)
                StdioEnvelope.newBuilder()
                    .setType(MessageType.EXECUTE_BATCH_RESPONSE)
                    .setPayload(resp.toByteString())
                    .build()
            }

            MessageType.UNLOAD_TRANSFORMATION_REQUEST -> {
                val req = UnloadTransformationRequest.parseFrom(envelope.payload)
                val resp = handleUnload(req, registry)
                StdioEnvelope.newBuilder()
                    .setType(MessageType.UNLOAD_TRANSFORMATION_RESPONSE)
                    .setPayload(resp.toByteString())
                    .build()
            }

            MessageType.HEALTH_REQUEST -> {
                val resp = handleHealth(registry)
                StdioEnvelope.newBuilder()
                    .setType(MessageType.HEALTH_RESPONSE)
                    .setPayload(resp.toByteString())
                    .build()
            }

            else -> {
                logger.warn("Unknown message type: {}", envelope.type)
                // Return an error as an ExecuteResponse (best effort)
                val resp = ExecuteResponse.newBuilder()
                    .setSuccess(false)
                    .setError("Unknown message type: ${envelope.type}")
                    .setErrorClass(ErrorClass.PERMANENT)
                    .setErrorPhase(ErrorPhase.INTERNAL)
                    .build()
                StdioEnvelope.newBuilder()
                    .setType(MessageType.EXECUTE_RESPONSE)
                    .setPayload(resp.toByteString())
                    .build()
            }
        }
    }

    private fun handleLoadTransformation(
        req: LoadTransformationRequest,
        registry: TransformationRegistry
    ): LoadTransformationResponse {
        val startTime = System.nanoTime()

        try {
            val id = req.transformationId
            val source = req.utlxSource
            val strategyName = req.strategy.ifEmpty { "TEMPLATE" }
            val validationPolicy = req.validationPolicy.ifEmpty { "SKIP" }
            val maxConcurrent = if (req.maxConcurrent > 0) req.maxConcurrent else 1

            logger.info("Loading transformation '{}' [strategy={}]", id, strategyName)

            val config = TransformConfig(
                strategy = strategyName,
                validationPolicy = validationPolicy,
                maxConcurrent = maxConcurrent
            )

            val strategy = engine.createStrategy(config)
            val parseStart = System.nanoTime()
            strategy.initialize(source, config)
            val parseEnd = System.nanoTime()

            val instance = TransformationInstance(
                name = id,
                source = source,
                strategy = strategy,
                config = config
            )
            registry.register(id, instance)

            val totalDuration = (System.nanoTime() - startTime) / 1000 // microseconds

            logger.info("Transformation '{}' loaded in {}μs", id, totalDuration)

            return LoadTransformationResponse.newBuilder()
                .setSuccess(true)
                .setMetrics(
                    LoadMetrics.newBuilder()
                        .setParseDurationUs((parseEnd - parseStart) / 1000)
                        .setTotalDurationUs(totalDuration)
                        .build()
                )
                .build()

        } catch (e: Exception) {
            logger.error("Failed to load transformation '{}': {}", req.transformationId, e.message, e)
            return LoadTransformationResponse.newBuilder()
                .setSuccess(false)
                .setError(e.message ?: "Unknown error")
                .build()
        }
    }

    private fun handleExecute(
        req: ExecuteRequest,
        registry: TransformationRegistry
    ): ExecuteResponse {
        val startTime = System.nanoTime()

        val instance = registry.get(req.transformationId)
            ?: return ExecuteResponse.newBuilder()
                .setSuccess(false)
                .setError("Transformation not found: ${req.transformationId}")
                .setErrorClass(ErrorClass.PERMANENT)
                .setErrorPhase(ErrorPhase.INTERNAL)
                .setCorrelationId(req.correlationId)
                .build()

        return try {
            val input = req.payload.toStringUtf8()
            instance.recordExecution()

            val result = instance.strategy.execute(input)
            val durationUs = (System.nanoTime() - startTime) / 1000

            val builder = ExecuteResponse.newBuilder()
                .setSuccess(true)
                .setOutput(ByteString.copyFromUtf8(result.output))
                .setCorrelationId(req.correlationId)
                .setMetrics(
                    ExecuteMetrics.newBuilder()
                        .setExecuteDurationUs(durationUs)
                        .build()
                )

            // Include any validation warnings from the strategy
            result.validationErrors.forEach { err ->
                builder.addValidationErrors(
                    org.apache.utlx.engine.proto.ValidationError.newBuilder()
                        .setMessage(err.message)
                        .setPath(err.path ?: "")
                        .setSeverity(err.severity)
                        .build()
                )
            }

            builder.build()

        } catch (e: Exception) {
            instance.recordError()
            val durationUs = (System.nanoTime() - startTime) / 1000
            logger.error("Execution error for '{}': {}", req.transformationId, e.message)

            ExecuteResponse.newBuilder()
                .setSuccess(false)
                .setError(e.message ?: "Unknown error")
                .setErrorClass(ErrorClass.PERMANENT)
                .setErrorPhase(ErrorPhase.TRANSFORMATION)
                .setCorrelationId(req.correlationId)
                .setMetrics(
                    ExecuteMetrics.newBuilder()
                        .setExecuteDurationUs(durationUs)
                        .build()
                )
                .build()
        }
    }

    private fun handleExecuteBatch(
        req: ExecuteBatchRequest,
        registry: TransformationRegistry
    ): ExecuteBatchResponse {
        val instance = registry.get(req.transformationId)
        val builder = ExecuteBatchResponse.newBuilder()

        if (instance == null) {
            // Return error for each item
            req.itemsList.forEach { item ->
                builder.addResults(
                    ExecuteResponse.newBuilder()
                        .setSuccess(false)
                        .setError("Transformation not found: ${req.transformationId}")
                        .setErrorClass(ErrorClass.PERMANENT)
                        .setErrorPhase(ErrorPhase.INTERNAL)
                        .setCorrelationId(item.correlationId)
                        .build()
                )
            }
            return builder.build()
        }

        req.itemsList.forEach { item ->
            val startTime = System.nanoTime()
            try {
                instance.recordExecution()
                val result = instance.strategy.execute(item.payload.toStringUtf8())
                val durationUs = (System.nanoTime() - startTime) / 1000

                builder.addResults(
                    ExecuteResponse.newBuilder()
                        .setSuccess(true)
                        .setOutput(ByteString.copyFromUtf8(result.output))
                        .setCorrelationId(item.correlationId)
                        .setMetrics(
                            ExecuteMetrics.newBuilder()
                                .setExecuteDurationUs(durationUs)
                                .build()
                        )
                        .build()
                )
            } catch (e: Exception) {
                instance.recordError()
                val durationUs = (System.nanoTime() - startTime) / 1000
                builder.addResults(
                    ExecuteResponse.newBuilder()
                        .setSuccess(false)
                        .setError(e.message ?: "Unknown error")
                        .setErrorClass(ErrorClass.PERMANENT)
                        .setErrorPhase(ErrorPhase.TRANSFORMATION)
                        .setCorrelationId(item.correlationId)
                        .setMetrics(
                            ExecuteMetrics.newBuilder()
                                .setExecuteDurationUs(durationUs)
                                .build()
                        )
                        .build()
                )
            }
        }

        return builder.build()
    }

    private fun handleUnload(
        req: UnloadTransformationRequest,
        registry: TransformationRegistry
    ): UnloadTransformationResponse {
        val success = registry.unload(req.transformationId)
        if (success) {
            logger.info("Unloaded transformation '{}'", req.transformationId)
        } else {
            logger.warn("Transformation '{}' not found for unload", req.transformationId)
        }
        return UnloadTransformationResponse.newBuilder()
            .setSuccess(success)
            .build()
    }

    private fun handleHealth(registry: TransformationRegistry): HealthResponse {
        val totalExecutions = registry.list().sumOf { it.executionCount.get() }
        val totalErrors = registry.list().sumOf { it.errorCount.get() }

        return HealthResponse.newBuilder()
            .setState(engine.state.name)
            .setUptimeMs(engine.uptimeMs())
            .setLoadedTransformations(registry.size())
            .setTotalExecutions(totalExecutions)
            .setTotalErrors(totalErrors)
            .build()
    }
}
