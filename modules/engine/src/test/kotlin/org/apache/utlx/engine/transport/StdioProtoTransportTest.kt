package org.apache.utlx.engine.transport

import com.google.protobuf.ByteString
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for StdioProtoTransport using piped streams to simulate stdin/stdout.
 *
 * Each test:
 * 1. Writes proto messages to the transport's "stdin" (via toTransport pipe)
 * 2. Runs the transport on a background thread
 * 3. Reads responses from the transport's "stdout" (via fromTransport pipe)
 */
class StdioProtoTransportTest {

    private lateinit var engine: UtlxEngine

    // Pipe: test writes → transport reads (simulates stdin)
    private lateinit var toTransportOut: PipedOutputStream
    private lateinit var toTransportIn: PipedInputStream

    // Pipe: transport writes → test reads (simulates stdout)
    private lateinit var fromTransportOut: PipedOutputStream
    private lateinit var fromTransportIn: PipedInputStream

    private var transportThread: Thread? = null

    private val identityUtlx = "%utlx 1.0\ninput json\noutput json\n---\n\$input\n"

    @BeforeEach
    fun setup() {
        engine = UtlxEngine(
            EngineConfig(
                engine = EngineSettings(
                    name = "test-proto",
                    monitoring = MonitoringConfig(health = HealthConfig(port = 0))
                )
            )
        )
        engine.initializeEmpty()

        toTransportOut = PipedOutputStream()
        toTransportIn = PipedInputStream(toTransportOut, 65536)

        fromTransportOut = PipedOutputStream()
        fromTransportIn = PipedInputStream(fromTransportOut, 65536)
    }

    @AfterEach
    fun teardown() {
        try { toTransportOut.close() } catch (_: Exception) {}
        transportThread?.join(5000)
    }

    /**
     * Start the transport on a background thread with redirected stdin/stdout.
     * After sending all messages, close the input pipe to trigger EOF.
     */
    private fun startTransport() {
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

    // =========================================================================
    // LoadTransformation Tests
    // =========================================================================

    @Test
    fun `load transformation succeeds`() {
        startTransport()

        val req = LoadTransformationRequest.newBuilder()
            .setTransformationId("test-tx")
            .setUtlxSource(identityUtlx)
            .setStrategy("TEMPLATE")
            .build()
        sendEnvelope(MessageType.LOAD_TRANSFORMATION_REQUEST, req)

        // Close input to signal EOF so transport exits
        toTransportOut.close()

        val respEnvelope = readEnvelope()
        assertEquals(MessageType.LOAD_TRANSFORMATION_RESPONSE, respEnvelope.type)

        val resp = LoadTransformationResponse.parseFrom(respEnvelope.payload)
        assertTrue(resp.success, "Load should succeed: ${resp.error}")
        assertTrue(resp.metrics.totalDurationUs > 0, "Should have timing metrics")

        // Verify transformation is registered
        transportThread?.join(5000)
        assertNotNull(engine.registry.get("test-tx"))
    }

    @Test
    fun `load transformation with invalid source fails`() {
        startTransport()

        val req = LoadTransformationRequest.newBuilder()
            .setTransformationId("bad-tx")
            .setUtlxSource("this is not valid utlx")
            .setStrategy("TEMPLATE")
            .build()
        sendEnvelope(MessageType.LOAD_TRANSFORMATION_REQUEST, req)
        toTransportOut.close()

        val respEnvelope = readEnvelope()
        val resp = LoadTransformationResponse.parseFrom(respEnvelope.payload)
        assertFalse(resp.success, "Load should fail for invalid source")
        assertTrue(resp.error.isNotEmpty(), "Should have error message")
    }

    // =========================================================================
    // Execute Tests
    // =========================================================================

    @Test
    fun `execute transformation returns result`() {
        startTransport()

        // First load a transformation
        val loadReq = LoadTransformationRequest.newBuilder()
            .setTransformationId("identity")
            .setUtlxSource(identityUtlx)
            .setStrategy("TEMPLATE")
            .build()
        sendEnvelope(MessageType.LOAD_TRANSFORMATION_REQUEST, loadReq)

        val loadResp = readEnvelope()
        val loadResult = LoadTransformationResponse.parseFrom(loadResp.payload)
        assertTrue(loadResult.success, "Load should succeed: ${loadResult.error}")

        // Now execute
        val execReq = ExecuteRequest.newBuilder()
            .setTransformationId("identity")
            .setPayload(ByteString.copyFromUtf8("""{"name": "test"}"""))
            .setContentType("application/json")
            .setCorrelationId("corr-001")
            .build()
        sendEnvelope(MessageType.EXECUTE_REQUEST, execReq)
        toTransportOut.close()

        val execResp = readEnvelope()
        assertEquals(MessageType.EXECUTE_RESPONSE, execResp.type)

        val result = ExecuteResponse.parseFrom(execResp.payload)
        assertTrue(result.success, "Execute should succeed: ${result.error}")
        assertEquals("corr-001", result.correlationId, "Correlation ID should echo back")
        assertTrue(result.output.toStringUtf8().contains("name"), "Output should contain transformed data")
        assertTrue(result.metrics.executeDurationUs > 0, "Should have timing metrics")
    }

    @Test
    fun `execute with unknown transformation returns error`() {
        startTransport()

        val execReq = ExecuteRequest.newBuilder()
            .setTransformationId("nonexistent")
            .setPayload(ByteString.copyFromUtf8("""{"x": 1}"""))
            .setCorrelationId("corr-err")
            .build()
        sendEnvelope(MessageType.EXECUTE_REQUEST, execReq)
        toTransportOut.close()

        val execResp = readEnvelope()
        val result = ExecuteResponse.parseFrom(execResp.payload)
        assertFalse(result.success)
        assertEquals(ErrorClass.PERMANENT, result.errorClass)
        assertEquals(ErrorPhase.INTERNAL, result.errorPhase)
        assertEquals("corr-err", result.correlationId)
        assertTrue(result.error.contains("nonexistent"))
    }

    // =========================================================================
    // Unload Tests
    // =========================================================================

    @Test
    fun `unload transformation succeeds`() {
        startTransport()

        // Load first
        val loadReq = LoadTransformationRequest.newBuilder()
            .setTransformationId("to-remove")
            .setUtlxSource(identityUtlx)
            .setStrategy("TEMPLATE")
            .build()
        sendEnvelope(MessageType.LOAD_TRANSFORMATION_REQUEST, loadReq)
        readEnvelope() // consume load response

        // Unload
        val unloadReq = UnloadTransformationRequest.newBuilder()
            .setTransformationId("to-remove")
            .build()
        sendEnvelope(MessageType.UNLOAD_TRANSFORMATION_REQUEST, unloadReq)
        toTransportOut.close()

        val unloadResp = readEnvelope()
        assertEquals(MessageType.UNLOAD_TRANSFORMATION_RESPONSE, unloadResp.type)
        val result = UnloadTransformationResponse.parseFrom(unloadResp.payload)
        assertTrue(result.success)
    }

    // =========================================================================
    // Health Tests
    // =========================================================================

    @Test
    fun `health request returns engine state`() {
        startTransport()

        sendEnvelope(MessageType.HEALTH_REQUEST, HealthRequest.getDefaultInstance())
        toTransportOut.close()

        val healthResp = readEnvelope()
        assertEquals(MessageType.HEALTH_RESPONSE, healthResp.type)
        val result = HealthResponse.parseFrom(healthResp.payload)
        assertTrue(result.state.isNotEmpty(), "Should return engine state")
        assertTrue(result.uptimeMs >= 0, "Should have uptime")
        assertEquals(0, result.loadedTransformations)
    }

    // =========================================================================
    // Full Lifecycle Test
    // =========================================================================

    @Test
    fun `full lifecycle - load, execute, health, unload`() {
        startTransport()

        // 1. Load transformation
        sendEnvelope(
            MessageType.LOAD_TRANSFORMATION_REQUEST,
            LoadTransformationRequest.newBuilder()
                .setTransformationId("lifecycle-tx")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build()
        )
        val loadResp = LoadTransformationResponse.parseFrom(readEnvelope().payload)
        assertTrue(loadResp.success)

        // 2. Execute
        sendEnvelope(
            MessageType.EXECUTE_REQUEST,
            ExecuteRequest.newBuilder()
                .setTransformationId("lifecycle-tx")
                .setPayload(ByteString.copyFromUtf8("""{"hello": "world"}"""))
                .setContentType("application/json")
                .setCorrelationId("lc-1")
                .build()
        )
        val execResp = ExecuteResponse.parseFrom(readEnvelope().payload)
        assertTrue(execResp.success)
        assertEquals("lc-1", execResp.correlationId)

        // 3. Health — should show 1 loaded transformation, 1 execution
        sendEnvelope(MessageType.HEALTH_REQUEST, HealthRequest.getDefaultInstance())
        val healthResp = HealthResponse.parseFrom(readEnvelope().payload)
        assertEquals(1, healthResp.loadedTransformations)
        assertEquals(1, healthResp.totalExecutions)
        assertEquals(0, healthResp.totalErrors)

        // 4. Unload
        sendEnvelope(
            MessageType.UNLOAD_TRANSFORMATION_REQUEST,
            UnloadTransformationRequest.newBuilder()
                .setTransformationId("lifecycle-tx")
                .build()
        )
        val unloadResp = UnloadTransformationResponse.parseFrom(readEnvelope().payload)
        assertTrue(unloadResp.success)

        // 5. Health again — should show 0 transformations
        sendEnvelope(MessageType.HEALTH_REQUEST, HealthRequest.getDefaultInstance())
        toTransportOut.close()
        val health2 = HealthResponse.parseFrom(readEnvelope().payload)
        assertEquals(0, health2.loadedTransformations)
    }

    // =========================================================================
    // Batch Execute Test
    // =========================================================================

    @Test
    fun `execute batch processes multiple items`() {
        startTransport()

        // Load
        sendEnvelope(
            MessageType.LOAD_TRANSFORMATION_REQUEST,
            LoadTransformationRequest.newBuilder()
                .setTransformationId("batch-tx")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build()
        )
        assertTrue(LoadTransformationResponse.parseFrom(readEnvelope().payload).success)

        // Batch execute
        val batchReq = ExecuteBatchRequest.newBuilder()
            .setTransformationId("batch-tx")
            .addItems(
                BatchItem.newBuilder()
                    .setPayload(ByteString.copyFromUtf8("""{"item": 1}"""))
                    .setContentType("application/json")
                    .setCorrelationId("b-1")
                    .build()
            )
            .addItems(
                BatchItem.newBuilder()
                    .setPayload(ByteString.copyFromUtf8("""{"item": 2}"""))
                    .setContentType("application/json")
                    .setCorrelationId("b-2")
                    .build()
            )
            .build()
        sendEnvelope(MessageType.EXECUTE_BATCH_REQUEST, batchReq)
        toTransportOut.close()

        val batchResp = readEnvelope()
        assertEquals(MessageType.EXECUTE_BATCH_RESPONSE, batchResp.type)
        val result = ExecuteBatchResponse.parseFrom(batchResp.payload)
        assertEquals(2, result.resultsCount, "Should have 2 results")
        assertTrue(result.getResults(0).success)
        assertTrue(result.getResults(1).success)
        assertEquals("b-1", result.getResults(0).correlationId)
        assertEquals("b-2", result.getResults(1).correlationId)
    }

    // =========================================================================
    // Concurrent Execution Tests (workers > 1)
    // =========================================================================

    private fun startTransportWithWorkers(workerCount: Int) {
        transportThread = Thread {
            val originalIn = System.`in`
            val originalOut = System.out
            try {
                System.setIn(toTransportIn)
                System.setOut(PrintStream(fromTransportOut, true))

                val transport = StdioProtoTransport(engine, workers = workerCount)
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

    @Test
    fun `concurrent - multiple execute requests are all processed`() {
        startTransportWithWorkers(4)

        // Load transformation first
        sendEnvelope(
            MessageType.LOAD_TRANSFORMATION_REQUEST,
            LoadTransformationRequest.newBuilder()
                .setTransformationId("concurrent-tx")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build()
        )
        val loadResp = LoadTransformationResponse.parseFrom(readEnvelope().payload)
        assertTrue(loadResp.success)

        // Send 10 execute requests rapidly
        val requestCount = 10
        for (i in 1..requestCount) {
            sendEnvelope(
                MessageType.EXECUTE_REQUEST,
                ExecuteRequest.newBuilder()
                    .setTransformationId("concurrent-tx")
                    .setPayload(ByteString.copyFromUtf8("""{"i": $i}"""))
                    .setContentType("application/json")
                    .setCorrelationId("c-$i")
                    .build()
            )
        }
        toTransportOut.close()

        // Collect all responses (may arrive out of order)
        val responses = mutableListOf<ExecuteResponse>()
        for (i in 1..requestCount) {
            val env = readEnvelope()
            assertEquals(MessageType.EXECUTE_RESPONSE, env.type)
            responses.add(ExecuteResponse.parseFrom(env.payload))
        }

        // All should succeed
        assertEquals(requestCount, responses.size)
        assertTrue(responses.all { it.success }, "All concurrent requests should succeed")

        // All correlation IDs should be present (possibly out of order)
        val correlationIds = responses.map { it.correlationId }.toSet()
        for (i in 1..requestCount) {
            assertTrue("c-$i" in correlationIds, "Missing correlation ID c-$i")
        }
    }

    @Test
    fun `concurrent - correlation IDs match responses to requests`() {
        startTransportWithWorkers(2)

        // Load
        sendEnvelope(
            MessageType.LOAD_TRANSFORMATION_REQUEST,
            LoadTransformationRequest.newBuilder()
                .setTransformationId("corr-tx")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build()
        )
        assertTrue(LoadTransformationResponse.parseFrom(readEnvelope().payload).success)

        // Send 5 requests with distinct correlation IDs
        for (i in 1..5) {
            sendEnvelope(
                MessageType.EXECUTE_REQUEST,
                ExecuteRequest.newBuilder()
                    .setTransformationId("corr-tx")
                    .setPayload(ByteString.copyFromUtf8("""{"val": $i}"""))
                    .setCorrelationId("req-$i")
                    .build()
            )
        }
        toTransportOut.close()

        val responses = (1..5).map {
            ExecuteResponse.parseFrom(readEnvelope().payload)
        }

        // Each response should have a correlation ID from our request set
        val ids = responses.map { it.correlationId }.toSet()
        assertEquals(5, ids.size, "All 5 unique correlation IDs should be present")
        for (i in 1..5) {
            assertTrue("req-$i" in ids)
        }
    }
}
