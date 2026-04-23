package org.apache.utlx.engine.transport

import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.proto.*
import org.apache.utlx.engine.registry.TransformationRegistry
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * StdioProtoTransport — varint-delimited protobuf over stdin/stdout.
 *
 * Protocol: each message is a StdioEnvelope (type + payload) written as
 * varint-delimited bytes using protobuf's writeDelimitedTo/parseDelimitedFrom.
 *
 * Concurrency model (Phase E):
 * - Reader thread: reads envelopes from stdin. Init-time messages (Load, Unload, Health)
 *   are handled synchronously on the reader thread. Execute/ExecuteBatch are dispatched
 *   to the worker thread pool.
 * - Worker pool: processes Execute requests concurrently. Results go to the response queue.
 * - Writer thread: drains the response queue and writes to stdout. Single writer prevents
 *   interleaved varint-delimited messages on the pipe.
 * - workers=1 gives sequential Model 1 behavior (no pool overhead).
 *
 * Correlation IDs in ExecuteRequest/ExecuteResponse allow the Go wrapper to match
 * out-of-order responses to waiting goroutines.
 */
class StdioProtoTransport(
    private val engine: UtlxEngine,
    private val workers: Int = Runtime.getRuntime().availableProcessors()
) : TransportServer {

    private val logger = LoggerFactory.getLogger(StdioProtoTransport::class.java)
    private val running = AtomicBoolean(false)
    private val responseQueue = LinkedBlockingQueue<StdioEnvelope>()
    private var writerThread: Thread? = null
    private var workerPool: ExecutorService? = null

    override val supportsDynamicLoading = true

    override fun start(registry: TransformationRegistry) {
        running.set(true)

        val input = BufferedInputStream(System.`in`)
        val output = BufferedOutputStream(System.out)

        val effectiveWorkers = workers.coerceAtLeast(1)

        // Start writer thread — sole owner of stdout
        writerThread = Thread({
            writerLoop(output)
        }, "utlxe-writer").also {
            it.isDaemon = true
            it.start()
        }

        // Start worker pool for Execute requests
        workerPool = if (effectiveWorkers > 1) {
            Executors.newFixedThreadPool(effectiveWorkers, object : ThreadFactory {
                private val counter = java.util.concurrent.atomic.AtomicInteger(0)
                override fun newThread(r: Runnable) = Thread(r, "utlxe-worker-${counter.incrementAndGet()}").also {
                    it.isDaemon = true
                }
            })
        } else null // workers=1 → inline execution, no pool overhead

        logger.info("StdioProtoTransport: ready (workers={}{})",
            effectiveWorkers, if (effectiveWorkers == 1) ", sequential" else ", multiplexed")

        // Reader loop — runs on the calling thread (main thread)
        while (running.get()) {
            val envelope = try {
                StdioEnvelope.parseDelimitedFrom(input) ?: break // EOF
            } catch (e: Exception) {
                if (!running.get()) break
                logger.error("Error reading envelope: {}", e.message, e)
                break
            }

            dispatch(envelope, registry)
        }

        // Shutdown: drain workers, then signal writer to stop
        shutdown()
        logger.info("StdioProtoTransport: message loop ended")
    }

    override fun stop() {
        running.set(false)
        shutdown()
        logger.info("StdioProtoTransport stopped")
    }

    private fun shutdown() {
        // Shutdown worker pool and wait for in-flight executions
        val pool = workerPool
        if (pool != null) {
            pool.shutdown()
            try {
                val terminated = pool.awaitTermination(30, TimeUnit.SECONDS)
                if (!terminated) pool.shutdownNow()
            } catch (_: InterruptedException) {
                pool.shutdownNow()
            }
        }

        // Signal writer thread to stop by posting a poison pill
        responseQueue.put(POISON_PILL)
        writerThread?.join(5000)
    }

    private fun writerLoop(output: OutputStream) {
        try {
            while (true) {
                val envelope = responseQueue.take()
                if (envelope === POISON_PILL) break
                envelope.writeDelimitedTo(output)
                output.flush()
            }
        } catch (e: Exception) {
            if (running.get()) {
                logger.error("Writer thread error: {}", e.message, e)
            }
        }
    }

    private fun dispatch(envelope: StdioEnvelope, registry: TransformationRegistry) {
        when (envelope.type) {
            // Init-time messages: handled synchronously on reader thread
            // (sequential ordering required — Load must complete before Execute)
            MessageType.LOAD_TRANSFORMATION_REQUEST -> {
                val req = LoadTransformationRequest.parseFrom(envelope.payload)
                val resp = TransportHandlers.handleLoadTransformation(req, engine, registry)
                responseQueue.put(wrapResponse(MessageType.LOAD_TRANSFORMATION_RESPONSE, resp))
            }

            MessageType.UNLOAD_TRANSFORMATION_REQUEST -> {
                val req = UnloadTransformationRequest.parseFrom(envelope.payload)
                val resp = TransportHandlers.handleUnload(req, registry)
                responseQueue.put(wrapResponse(MessageType.UNLOAD_TRANSFORMATION_RESPONSE, resp))
            }

            MessageType.HEALTH_REQUEST -> {
                val resp = TransportHandlers.handleHealth(engine, registry)
                responseQueue.put(wrapResponse(MessageType.HEALTH_RESPONSE, resp))
            }

            // Runtime messages: dispatched to worker pool (or inline if workers=1)
            MessageType.EXECUTE_REQUEST -> {
                val req = ExecuteRequest.parseFrom(envelope.payload)
                submitWork {
                    val resp = TransportHandlers.handleExecute(req, registry)
                    responseQueue.put(wrapResponse(MessageType.EXECUTE_RESPONSE, resp))
                }
            }

            MessageType.EXECUTE_BATCH_REQUEST -> {
                val req = ExecuteBatchRequest.parseFrom(envelope.payload)
                submitWork {
                    val resp = TransportHandlers.handleExecuteBatch(req, registry)
                    responseQueue.put(wrapResponse(MessageType.EXECUTE_BATCH_RESPONSE, resp))
                }
            }

            MessageType.EXECUTE_PIPELINE_REQUEST -> {
                val req = ExecutePipelineRequest.parseFrom(envelope.payload)
                submitWork {
                    val resp = TransportHandlers.handleExecutePipeline(req, registry)
                    responseQueue.put(wrapResponse(MessageType.EXECUTE_PIPELINE_RESPONSE, resp))
                }
            }

            else -> {
                logger.warn("Unknown message type: {}", envelope.type)
                val resp = ExecuteResponse.newBuilder()
                    .setSuccess(false)
                    .setError("Unknown message type: ${envelope.type}")
                    .setErrorClass(ErrorClass.PERMANENT)
                    .setErrorPhase(ErrorPhase.INTERNAL)
                    .build()
                responseQueue.put(wrapResponse(MessageType.EXECUTE_RESPONSE, resp))
            }
        }
    }

    private fun submitWork(task: () -> Unit) {
        val pool = workerPool
        if (pool != null) {
            pool.submit { task() }
        } else {
            // workers=1: execute inline on reader thread (Model 1 behavior)
            task()
        }
    }

    private fun wrapResponse(type: MessageType, payload: com.google.protobuf.MessageLite): StdioEnvelope {
        return StdioEnvelope.newBuilder()
            .setType(type)
            .setPayload(payload.toByteString())
            .build()
    }

    companion object {
        // Sentinel envelope to signal writer thread shutdown
        private val POISON_PILL = StdioEnvelope.getDefaultInstance()
    }
}
