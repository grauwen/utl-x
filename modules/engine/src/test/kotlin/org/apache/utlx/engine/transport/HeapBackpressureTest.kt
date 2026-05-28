package org.apache.utlx.engine.transport

import com.google.protobuf.ByteString
import org.apache.utlx.engine.EngineState
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.EngineConfig
import org.apache.utlx.engine.config.EngineSettings
import org.apache.utlx.engine.config.HealthConfig
import org.apache.utlx.engine.config.MonitoringConfig
import org.apache.utlx.engine.proto.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for EF15/EB02: Heap backpressure across all transports.
 *
 * Two layers of testing:
 * 1. Monitor tests — verify the background thread produces real heap values
 *    and that pressure triggers when real usage crosses a realistic threshold.
 * 2. Transport tests — verify all execution paths reject when isHeapPressure()
 *    returns true. These use forced thresholds (0.0/1.0) because they test the
 *    dispatch path, not the monitoring. Separating concerns.
 */
class HeapBackpressureTest {

    private lateinit var engine: UtlxEngine

    private val identityUtlx = "%utlx 1.0\ninput json\noutput json\n---\n\$input\n"

    @BeforeEach
    fun setup() {
        engine = UtlxEngine(
            EngineConfig(
                engine = EngineSettings(
                    name = "test-backpressure",
                    monitoring = MonitoringConfig(health = HealthConfig(port = 0))
                )
            )
        )
        engine.initializeEmpty()
    }

    @AfterEach
    fun teardown() {
        engine.stop()
    }

    // ── Helper: force engine to RUNNING state ──

    private fun setRunning() {
        val stateField = engine.javaClass.getDeclaredField("stateRef")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateRef = stateField.get(engine) as java.util.concurrent.atomic.AtomicReference<Any>
        stateRef.set(EngineState.RUNNING)
    }

    // ── Helpers: force heap pressure on/off for transport tests ──
    // These test the dispatch path ("does the transport reject?"), not the monitor.

    private fun forceHeapPressure() {
        // Set threshold just below actual usage — triggers real pressure
        // Set resume to 0.0 so hysteresis never clears (test heap usage is ~30%, well below 80%)
        Thread.sleep(200) // wait for monitor to have a real value
        engine.heapBackpressureResume = 0.0
        engine.heapBackpressureThreshold = engine.heapUsage - 0.01
        assertTrue(engine.isHeapPressure(), "Precondition: pressure should be active")
    }

    private fun clearHeapPressure() {
        // Set threshold above any realistic usage and resume below actual usage
        // to ensure hysteresis clears (backpressureActive = false)
        engine.heapBackpressureThreshold = 1.0
        engine.heapBackpressureResume = 1.0  // any usage is below 1.0, so hysteresis clears
        engine.isHeapPressure()  // trigger the hysteresis check to clear the flag
        engine.heapBackpressureResume = 0.80  // restore default
        assertFalse(engine.isHeapPressure(), "Precondition: pressure should be cleared")
    }

    // =========================================================================
    // Heap monitor thread tests — verify the background thread works correctly
    // =========================================================================

    @Test
    fun `heap monitor thread stops on engine shutdown`() {
        val heapThread = Thread.getAllStackTraces().keys.find { it.name == "heap-monitor" }
        assertTrue(heapThread != null && heapThread.isAlive, "heap-monitor thread should be running")

        engine.stop()
        Thread.sleep(300) // allow interrupt to propagate

        assertFalse(heapThread!!.isAlive, "heap-monitor thread should have stopped after engine.stop()")
    }

    @Test
    fun `heap monitor updates heapUsage to a realistic value`() {
        // heapUsage starts at 0.0 (default). After the monitor runs (every 100ms),
        // it should reflect actual JVM heap usage — always > 0 and < 1.
        Thread.sleep(200)
        assertTrue(engine.heapUsage > 0.0,
            "heapUsage should reflect actual usage (got ${engine.heapUsage}), not default 0.0")
        assertTrue(engine.heapUsage < 1.0,
            "heapUsage should be less than 100% (got ${engine.heapUsage})")
    }

    @Test
    fun `heap pressure triggers when real usage exceeds threshold`() {
        Thread.sleep(200) // wait for monitor to produce a real value
        val realUsage = engine.heapUsage
        assertTrue(realUsage > 0.0, "Monitor should have produced a real value by now")

        // Set threshold just below actual usage — should trigger
        engine.heapBackpressureThreshold = realUsage - 0.01
        assertTrue(engine.isHeapPressure(),
            "Should be under pressure when threshold (${ "%.3f".format(engine.heapBackpressureThreshold)}) < usage (${"%.3f".format(realUsage)})")

        // Set threshold above actual usage AND resume threshold below usage — hysteresis clears
        engine.heapBackpressureThreshold = realUsage + 0.05
        engine.heapBackpressureResume = 1.0  // force hysteresis to clear (usage < 1.0)
        engine.isHeapPressure()  // trigger hysteresis clear
        engine.heapBackpressureResume = 0.80  // restore default
        assertFalse(engine.isHeapPressure(),
            "Should not be under pressure when threshold (${"%.3f".format(engine.heapBackpressureThreshold)}) > usage (${"%.3f".format(realUsage)})")
    }

    @Test
    fun `heapUsagePercent returns integer percentage`() {
        Thread.sleep(200)
        val percent = engine.heapUsagePercent()
        assertTrue(percent in 1..99,
            "heapUsagePercent should be between 1-99% (got $percent)")
        // Verify it's consistent with heapUsage
        assertEquals((engine.heapUsage * 100).toInt(), percent,
            "heapUsagePercent should match heapUsage * 100")
    }

    // =========================================================================
    // Gap 3a: StdioProtoTransport backpressure
    // =========================================================================

    // ── Stdio helpers ──

    private lateinit var toTransportOut: PipedOutputStream
    private lateinit var toTransportIn: PipedInputStream
    private lateinit var fromTransportOut: PipedOutputStream
    private lateinit var fromTransportIn: PipedInputStream
    private var transportThread: Thread? = null

    private fun setupStdioPipes() {
        toTransportOut = PipedOutputStream()
        toTransportIn = PipedInputStream(toTransportOut, 65536)
        fromTransportOut = PipedOutputStream()
        fromTransportIn = PipedInputStream(fromTransportOut, 65536)
    }

    private fun startStdioTransport() {
        transportThread = Thread {
            val originalIn = System.`in`
            val originalOut = System.out
            try {
                System.setIn(toTransportIn)
                System.setOut(PrintStream(fromTransportOut, true))
                val transport = StdioProtoTransport(engine, workers = 1)
                transport.start(engine.registry)
            } finally {
                System.setIn(originalIn)
                System.setOut(originalOut)
                try { fromTransportOut.close() } catch (_: Exception) {}
            }
        }.also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun sendEnvelope(type: MessageType, payload: com.google.protobuf.MessageLite) {
        val envelope = StdioEnvelope.newBuilder()
            .setType(type)
            .setPayload(payload.toByteString())
            .build()
        envelope.writeDelimitedTo(toTransportOut)
        toTransportOut.flush()
    }

    private fun readEnvelope(): StdioEnvelope {
        return StdioEnvelope.parseDelimitedFrom(fromTransportIn)
            ?: throw IOException("Unexpected EOF reading response")
    }

    private fun loadTransformation(id: String) {
        val req = LoadTransformationRequest.newBuilder()
            .setTransformationId(id)
            .setUtlxSource(identityUtlx)
            .setStrategy("TEMPLATE")
            .build()
        sendEnvelope(MessageType.LOAD_TRANSFORMATION_REQUEST, req)
        val resp = LoadTransformationResponse.parseFrom(readEnvelope().payload)
        assertTrue(resp.success, "Load should succeed: ${resp.error}")
    }

    @Test
    fun `stdio execute rejected under heap pressure`() {
        setupStdioPipes()
        startStdioTransport()
        loadTransformation("identity")

        // Force heap pressure
        forceHeapPressure()
        Thread.sleep(200) // wait for heap monitor to update

        // Send execute request
        val req = ExecuteRequest.newBuilder()
            .setTransformationId("identity")
            .setPayload(ByteString.copyFromUtf8("""{"name":"test"}"""))
            .setContentType("application/json")
            .setRequestId("req-bp-1")
            .build()
        sendEnvelope(MessageType.EXECUTE_REQUEST, req)

        toTransportOut.close()

        val respEnvelope = readEnvelope()
        assertEquals(MessageType.EXECUTE_RESPONSE, respEnvelope.type)

        val resp = ExecuteResponse.parseFrom(respEnvelope.payload)
        assertFalse(resp.success, "Execute should be rejected under heap pressure")
        assertEquals(ErrorCode.HEAP_PRESSURE, resp.errorCode, "Error code should be HEAP_PRESSURE")
        assertEquals(ErrorClass.TRANSIENT, resp.errorClass, "Error class should be TRANSIENT")
        assertEquals(ErrorPhase.INTERNAL, resp.errorPhase, "Error phase should be INTERNAL")
        assertEquals("req-bp-1", resp.requestId, "Request ID should be echoed")
        assertTrue(resp.error.contains("Heap memory pressure"), "Error message should mention heap pressure")

        transportThread?.join(5000)
    }

    @Test
    fun `stdio execute succeeds when no heap pressure`() {
        setupStdioPipes()
        startStdioTransport()
        loadTransformation("identity")

        // Ensure no heap pressure
        clearHeapPressure()

        val req = ExecuteRequest.newBuilder()
            .setTransformationId("identity")
            .setPayload(ByteString.copyFromUtf8("""{"name":"test"}"""))
            .setContentType("application/json")
            .setRequestId("req-ok-1")
            .build()
        sendEnvelope(MessageType.EXECUTE_REQUEST, req)

        toTransportOut.close()

        val respEnvelope = readEnvelope()
        val resp = ExecuteResponse.parseFrom(respEnvelope.payload)
        assertTrue(resp.success, "Execute should succeed without heap pressure: ${resp.error}")
        assertEquals("req-ok-1", resp.requestId)

        transportThread?.join(5000)
    }

    @Test
    fun `stdio batch execute rejected under heap pressure`() {
        setupStdioPipes()
        startStdioTransport()
        loadTransformation("identity")

        forceHeapPressure()
        Thread.sleep(200)

        val req = ExecuteBatchRequest.newBuilder()
            .setTransformationId("identity")
            .addItems(
                BatchItem.newBuilder()
                    .setPayload(ByteString.copyFromUtf8("""{"x":1}"""))
                    .setContentType("application/json")
            )
            .build()
        sendEnvelope(MessageType.EXECUTE_BATCH_REQUEST, req)

        toTransportOut.close()

        val respEnvelope = readEnvelope()
        assertEquals(MessageType.EXECUTE_BATCH_RESPONSE, respEnvelope.type)

        val resp = ExecuteBatchResponse.parseFrom(respEnvelope.payload)
        assertTrue(resp.resultsList.isNotEmpty(), "Should have at least one result")
        assertFalse(resp.resultsList[0].success, "Batch item should be rejected")
        assertEquals(ErrorCode.HEAP_PRESSURE, resp.resultsList[0].errorCode)

        transportThread?.join(5000)
    }

    @Test
    fun `stdio pipeline execute rejected under heap pressure`() {
        setupStdioPipes()
        startStdioTransport()
        loadTransformation("identity")

        forceHeapPressure()
        Thread.sleep(200)

        val req = ExecutePipelineRequest.newBuilder()
            .addTransformationIds("identity")
            .setPayload(ByteString.copyFromUtf8("""{"x":1}"""))
            .setContentType("application/json")
            .setRequestId("pipe-bp-1")
            .build()
        sendEnvelope(MessageType.EXECUTE_PIPELINE_REQUEST, req)

        toTransportOut.close()

        val respEnvelope = readEnvelope()
        assertEquals(MessageType.EXECUTE_PIPELINE_RESPONSE, respEnvelope.type)

        val resp = ExecutePipelineResponse.parseFrom(respEnvelope.payload)
        assertFalse(resp.success, "Pipeline should be rejected under heap pressure")
        assertEquals(ErrorCode.HEAP_PRESSURE, resp.errorCode)
        assertEquals("pipe-bp-1", resp.requestId)

        transportThread?.join(5000)
    }

    @Test
    fun `stdio execute recovers after heap pressure clears`() {
        setupStdioPipes()
        startStdioTransport()
        loadTransformation("identity")

        // First: under pressure → rejected
        forceHeapPressure()
        Thread.sleep(200)

        val req1 = ExecuteRequest.newBuilder()
            .setTransformationId("identity")
            .setPayload(ByteString.copyFromUtf8("""{"phase":"pressure"}"""))
            .setContentType("application/json")
            .setRequestId("req-phase1")
            .build()
        sendEnvelope(MessageType.EXECUTE_REQUEST, req1)

        val resp1 = ExecuteResponse.parseFrom(readEnvelope().payload)
        assertFalse(resp1.success, "Should be rejected under pressure")
        assertEquals(ErrorCode.HEAP_PRESSURE, resp1.errorCode)

        // Second: clear pressure → succeeds
        clearHeapPressure()

        val req2 = ExecuteRequest.newBuilder()
            .setTransformationId("identity")
            .setPayload(ByteString.copyFromUtf8("""{"phase":"recovered"}"""))
            .setContentType("application/json")
            .setRequestId("req-phase2")
            .build()
        sendEnvelope(MessageType.EXECUTE_REQUEST, req2)

        toTransportOut.close()

        val resp2 = ExecuteResponse.parseFrom(readEnvelope().payload)
        assertTrue(resp2.success, "Should succeed after pressure clears: ${resp2.error}")
        assertEquals("req-phase2", resp2.requestId)

        transportThread?.join(5000)
    }

    // =========================================================================
    // Gap 3b: GrpcTransport backpressure
    // (tested via UtlxeServiceImpl directly — no network needed)
    // =========================================================================

    @Test
    fun `grpc execute rejected under heap pressure`() {
        setRunning()
        val service = UtlxeServiceImpl(engine, engine.registry)

        // Load a transformation directly
        val loadResp = TransportHandlers.handleLoadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("identity")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build(),
            engine
        )
        assertTrue(loadResp.success)

        forceHeapPressure()
        Thread.sleep(200)

        val observer = TestStreamObserver<ExecuteResponse>()
        service.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("identity")
                .setPayload(ByteString.copyFromUtf8("""{"name":"test"}"""))
                .setContentType("application/json")
                .build(),
            observer
        )

        assertTrue(observer.error != null, "Should return an error under heap pressure")
        assertTrue(observer.error!!.message?.contains("Heap memory pressure") == true,
            "Error should mention heap pressure: ${observer.error!!.message}")
    }

    @Test
    fun `grpc execute succeeds without heap pressure`() {
        setRunning()
        val service = UtlxeServiceImpl(engine, engine.registry)

        val loadResp = TransportHandlers.handleLoadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("identity")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build(),
            engine
        )
        assertTrue(loadResp.success)

        clearHeapPressure()

        val observer = TestStreamObserver<ExecuteResponse>()
        service.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("identity")
                .setPayload(ByteString.copyFromUtf8("""{"name":"test"}"""))
                .setContentType("application/json")
                .build(),
            observer
        )

        assertTrue(observer.error == null, "Should not error without pressure: ${observer.error}")
        assertTrue(observer.values.isNotEmpty(), "Should have a response")
        assertTrue(observer.values[0].success, "Execute should succeed: ${observer.values[0].error}")
    }

    @Test
    fun `grpc batch execute rejected under heap pressure`() {
        setRunning()
        val service = UtlxeServiceImpl(engine, engine.registry)

        TransportHandlers.handleLoadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("identity")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build(),
            engine
        )

        forceHeapPressure()
        Thread.sleep(200)

        val observer = TestStreamObserver<ExecuteBatchResponse>()
        service.executeBatch(
            ExecuteBatchRequest.newBuilder()
                .setTransformationId("identity")
                .addItems(
                    BatchItem.newBuilder()
                        .setPayload(ByteString.copyFromUtf8("""{"x":1}"""))
                        .setContentType("application/json")
                )
                .build(),
            observer
        )

        assertTrue(observer.error != null, "Batch should be rejected under heap pressure")
        assertTrue(observer.error!!.message?.contains("Heap memory pressure") == true)
    }

    @Test
    fun `grpc pipeline execute rejected under heap pressure`() {
        setRunning()
        val service = UtlxeServiceImpl(engine, engine.registry)

        TransportHandlers.handleLoadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("identity")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build(),
            engine
        )

        forceHeapPressure()
        Thread.sleep(200)

        val observer = TestStreamObserver<ExecutePipelineResponse>()
        service.executePipeline(
            ExecutePipelineRequest.newBuilder()
                .addTransformationIds("identity")
                .setPayload(ByteString.copyFromUtf8("""{"x":1}"""))
                .setContentType("application/json")
                .build(),
            observer
        )

        assertTrue(observer.error != null, "Pipeline should be rejected under heap pressure")
        assertTrue(observer.error!!.message?.contains("Heap memory pressure") == true)
    }

    // ── Test helper: captures gRPC StreamObserver responses ──

    private class TestStreamObserver<T> : io.grpc.stub.StreamObserver<T> {
        val values = mutableListOf<T>()
        var error: Throwable? = null
        var completed = false

        override fun onNext(value: T) { values.add(value) }
        override fun onError(t: Throwable) { error = t }
        override fun onCompleted() { completed = true }
    }
}
