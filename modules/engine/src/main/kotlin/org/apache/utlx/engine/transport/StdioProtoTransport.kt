package org.apache.utlx.engine.transport

import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.proto.*
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
                val resp = TransportHandlers.handleLoadTransformation(req, engine, registry)
                StdioEnvelope.newBuilder()
                    .setType(MessageType.LOAD_TRANSFORMATION_RESPONSE)
                    .setPayload(resp.toByteString())
                    .build()
            }

            MessageType.EXECUTE_REQUEST -> {
                val req = ExecuteRequest.parseFrom(envelope.payload)
                val resp = TransportHandlers.handleExecute(req, registry)
                StdioEnvelope.newBuilder()
                    .setType(MessageType.EXECUTE_RESPONSE)
                    .setPayload(resp.toByteString())
                    .build()
            }

            MessageType.EXECUTE_BATCH_REQUEST -> {
                val req = ExecuteBatchRequest.parseFrom(envelope.payload)
                val resp = TransportHandlers.handleExecuteBatch(req, registry)
                StdioEnvelope.newBuilder()
                    .setType(MessageType.EXECUTE_BATCH_RESPONSE)
                    .setPayload(resp.toByteString())
                    .build()
            }

            MessageType.UNLOAD_TRANSFORMATION_REQUEST -> {
                val req = UnloadTransformationRequest.parseFrom(envelope.payload)
                val resp = TransportHandlers.handleUnload(req, registry)
                StdioEnvelope.newBuilder()
                    .setType(MessageType.UNLOAD_TRANSFORMATION_RESPONSE)
                    .setPayload(resp.toByteString())
                    .build()
            }

            MessageType.HEALTH_REQUEST -> {
                val resp = TransportHandlers.handleHealth(engine, registry)
                StdioEnvelope.newBuilder()
                    .setType(MessageType.HEALTH_RESPONSE)
                    .setPayload(resp.toByteString())
                    .build()
            }

            else -> {
                logger.warn("Unknown message type: {}", envelope.type)
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
}
