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
import java.util.concurrent.atomic.AtomicLong

/**
 * StdioProtoTransport — varint-delimited protobuf over stdin/stdout.
 *
 * Concurrency model:
 * - Reader thread (main): reads envelopes from stdin. Init-time messages (Load, Unload, Health)
 *   handled synchronously. Execute/ExecuteBatch/ExecutePipeline dispatched to worker pool.
 * - Worker pool: processes requests concurrently. Results go to the response queue.
 * - Writer thread: drains response queue, writes to stdout (single writer, no interleaving).
 *
 * Back-pressure:
 * - The response queue is bounded (default: workers * 128).
 * - When the queue is full, worker threads block on put() — they can't submit more results.
 * - This causes the worker pool's task queue to fill up.
 * - When the pool's task queue is full, submitWork() blocks the reader thread.
 * - When the reader blocks, stdin pipe buffer fills → Go wrapper's write() blocks.
 * - Pressure propagates: UTLXe → pipe → Go wrapper → upstream source.
 * - When the writer drains the queue, everything unblocks automatically.
 *
 * @param workers Number of worker threads (default: CPU cores). workers=1 → sequential, no pool.
 * @param queueCapacity Max pending responses before back-pressure kicks in. 0 = unbounded.
 */
class StdioProtoTransport(
    private val engine: UtlxEngine,
    private val workers: Int = Runtime.getRuntime().availableProcessors(),
    private val queueCapacity: Int = 0 // 0 = auto (workers * 128)
) : TransportServer {

    private val logger = LoggerFactory.getLogger(StdioProtoTransport::class.java)
    private val running = AtomicBoolean(false)
    private lateinit var responseQueue: BlockingQueue<StdioEnvelope>
    private var writerThread: Thread? = null
    private var workerPool: ExecutorService? = null
    private val backPressureCount = AtomicLong(0)

    override val supportsDynamicLoading = true

    override fun start(registry: TransformationRegistry) {
        running.set(true)

        val input = BufferedInputStream(System.`in`)
        val output = BufferedOutputStream(System.out)

        val effectiveWorkers = workers.coerceAtLeast(1)
        val effectiveCapacity = if (queueCapacity > 0) queueCapacity else effectiveWorkers * 128

        // Bounded response queue — back-pressure when full
        responseQueue = ArrayBlockingQueue(effectiveCapacity)

        // Start writer thread — sole owner of stdout
        writerThread = Thread({
            writerLoop(output)
        }, "utlxe-writer").also {
            it.isDaemon = true
            it.start()
        }

        // Start worker pool with bounded task queue for back-pressure
        workerPool = if (effectiveWorkers > 1) {
            val taskQueue = ArrayBlockingQueue<Runnable>(effectiveWorkers * 4)
            ThreadPoolExecutor(
                effectiveWorkers, effectiveWorkers,
                60L, TimeUnit.SECONDS,
                taskQueue,
                object : ThreadFactory {
                    private val counter = java.util.concurrent.atomic.AtomicInteger(0)
                    override fun newThread(r: Runnable) = Thread(r, "utlxe-worker-${counter.incrementAndGet()}").also {
                        it.isDaemon = true
                    }
                },
                // CallerRunsPolicy: when pool + task queue are full, the reader thread
                // executes the task itself — this naturally blocks stdin reading (back-pressure)
                ThreadPoolExecutor.CallerRunsPolicy()
            )
        } else null // workers=1 → inline execution, no pool overhead

        logger.info("StdioProtoTransport: ready (workers={}{}, queue={})",
            effectiveWorkers,
            if (effectiveWorkers == 1) ", sequential" else ", multiplexed",
            effectiveCapacity)

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

        shutdown()

        if (backPressureCount.get() > 0) {
            logger.info("StdioProtoTransport: back-pressure triggered {} time(s) during session",
                backPressureCount.get())
        }
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
            // CallerRunsPolicy: if pool + task queue are full, this runs on the reader thread.
            // That blocks stdin reading — back-pressure propagates to the caller.
            if ((pool as ThreadPoolExecutor).queue.remainingCapacity() == 0) {
                backPressureCount.incrementAndGet()
                logger.debug("Back-pressure: worker pool task queue full, reader thread will execute task")
            }
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
