package org.apache.utlx.engine.transport

import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.EngineConfig
import org.apache.utlx.engine.config.EngineSettings
import org.apache.utlx.engine.config.HealthConfig
import org.apache.utlx.engine.config.MonitoringConfig
import org.apache.utlx.engine.proto.*
import org.junit.Rule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the gRPC transport using grpc-testing's in-process server.
 * No real network sockets needed — everything runs in-memory.
 */
class GrpcTransportTest {

    private lateinit var engine: UtlxEngine
    private lateinit var channel: ManagedChannel
    private lateinit var blockingStub: UtlxeServiceGrpc.UtlxeServiceBlockingStub
    private lateinit var server: io.grpc.Server

    private val identityUtlx = "%utlx 1.0\ninput json\noutput json\n---\n\$input\n"

    @BeforeEach
    fun setup() {
        engine = UtlxEngine(
            EngineConfig(
                engine = EngineSettings(
                    name = "test-grpc",
                    monitoring = MonitoringConfig(health = HealthConfig(port = 0))
                )
            )
        )
        engine.initializeEmpty()

        val serverName = InProcessServerBuilder.generateName()

        val serviceImpl = UtlxeServiceImpl(engine, engine.registry)

        server = InProcessServerBuilder
            .forName(serverName)
            .directExecutor()
            .addService(serviceImpl)
            .build()
            .start()

        channel = InProcessChannelBuilder
            .forName(serverName)
            .directExecutor()
            .build()

        blockingStub = UtlxeServiceGrpc.newBlockingStub(channel)
    }

    @AfterEach
    fun teardown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    // =========================================================================
    // LoadTransformation Tests
    // =========================================================================

    @Test
    fun `load transformation via gRPC succeeds`() {
        val resp = blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("grpc-tx")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build()
        )

        assertTrue(resp.success, "Load should succeed: ${resp.error}")
        assertTrue(resp.metrics.totalDurationUs > 0)
        assertEquals(1, engine.registry.size())
    }

    @Test
    fun `load transformation with invalid source fails`() {
        val resp = blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("bad-tx")
                .setUtlxSource("not valid utlx")
                .setStrategy("TEMPLATE")
                .build()
        )

        assertFalse(resp.success)
        assertTrue(resp.error.isNotEmpty())
    }

    // =========================================================================
    // Execute Tests
    // =========================================================================

    @Test
    fun `execute transformation via gRPC returns result`() {
        // Load first
        val loadResp = blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("exec-tx")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build()
        )
        assertTrue(loadResp.success)

        // Execute
        val execResp = blockingStub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("exec-tx")
                .setPayload(ByteString.copyFromUtf8("""{"name": "gRPC"}"""))
                .setContentType("application/json")
                .setCorrelationId("grpc-corr-1")
                .build()
        )

        assertTrue(execResp.success, "Execute should succeed: ${execResp.error}")
        assertEquals("grpc-corr-1", execResp.correlationId)
        assertTrue(execResp.output.toStringUtf8().contains("name"))
        assertTrue(execResp.metrics.executeDurationUs > 0)
    }

    @Test
    fun `execute with unknown transformation returns PERMANENT error`() {
        val resp = blockingStub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("nonexistent")
                .setPayload(ByteString.copyFromUtf8("""{}"""))
                .setCorrelationId("err-1")
                .build()
        )

        assertFalse(resp.success)
        assertEquals(ErrorClass.PERMANENT, resp.errorClass)
        assertEquals(ErrorPhase.INTERNAL, resp.errorPhase)
        assertEquals("err-1", resp.correlationId)
    }

    // =========================================================================
    // ExecuteBatch Test
    // =========================================================================

    @Test
    fun `execute batch via gRPC processes multiple items`() {
        blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("batch-tx")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build()
        )

        val batchResp = blockingStub.executeBatch(
            ExecuteBatchRequest.newBuilder()
                .setTransformationId("batch-tx")
                .addItems(
                    BatchItem.newBuilder()
                        .setPayload(ByteString.copyFromUtf8("""{"i": 1}"""))
                        .setCorrelationId("b1")
                        .build()
                )
                .addItems(
                    BatchItem.newBuilder()
                        .setPayload(ByteString.copyFromUtf8("""{"i": 2}"""))
                        .setCorrelationId("b2")
                        .build()
                )
                .addItems(
                    BatchItem.newBuilder()
                        .setPayload(ByteString.copyFromUtf8("""{"i": 3}"""))
                        .setCorrelationId("b3")
                        .build()
                )
                .build()
        )

        assertEquals(3, batchResp.resultsCount)
        assertTrue(batchResp.getResults(0).success)
        assertTrue(batchResp.getResults(1).success)
        assertTrue(batchResp.getResults(2).success)
        assertEquals("b1", batchResp.getResults(0).correlationId)
        assertEquals("b3", batchResp.getResults(2).correlationId)
    }

    // =========================================================================
    // Unload Tests
    // =========================================================================

    @Test
    fun `unload transformation via gRPC succeeds`() {
        blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("remove-me")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build()
        )
        assertEquals(1, engine.registry.size())

        val resp = blockingStub.unloadTransformation(
            UnloadTransformationRequest.newBuilder()
                .setTransformationId("remove-me")
                .build()
        )

        assertTrue(resp.success)
        assertEquals(0, engine.registry.size())
    }

    @Test
    fun `unload nonexistent transformation returns false`() {
        val resp = blockingStub.unloadTransformation(
            UnloadTransformationRequest.newBuilder()
                .setTransformationId("ghost")
                .build()
        )
        assertFalse(resp.success)
    }

    // =========================================================================
    // Health Tests
    // =========================================================================

    @Test
    fun `health returns engine state and metrics`() {
        val resp = blockingStub.health(HealthRequest.getDefaultInstance())

        assertTrue(resp.state.isNotEmpty())
        assertTrue(resp.uptimeMs >= 0)
        assertEquals(0, resp.loadedTransformations)
        assertEquals(0, resp.totalExecutions)
    }

    // =========================================================================
    // Full Lifecycle Test
    // =========================================================================

    @Test
    fun `full gRPC lifecycle - load, execute, health, unload`() {
        // 1. Load
        val loadResp = blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("lifecycle")
                .setUtlxSource(identityUtlx)
                .setStrategy("TEMPLATE")
                .build()
        )
        assertTrue(loadResp.success)

        // 2. Execute twice
        for (i in 1..2) {
            val execResp = blockingStub.execute(
                ExecuteRequest.newBuilder()
                    .setTransformationId("lifecycle")
                    .setPayload(ByteString.copyFromUtf8("""{"run": $i}"""))
                    .setCorrelationId("lc-$i")
                    .build()
            )
            assertTrue(execResp.success)
        }

        // 3. Health — 1 transform, 2 executions
        val health1 = blockingStub.health(HealthRequest.getDefaultInstance())
        assertEquals(1, health1.loadedTransformations)
        assertEquals(2, health1.totalExecutions)
        assertEquals(0, health1.totalErrors)

        // 4. Unload
        val unloadResp = blockingStub.unloadTransformation(
            UnloadTransformationRequest.newBuilder()
                .setTransformationId("lifecycle")
                .build()
        )
        assertTrue(unloadResp.success)

        // 5. Health again — 0 transforms
        val health2 = blockingStub.health(HealthRequest.getDefaultInstance())
        assertEquals(0, health2.loadedTransformations)
    }
}
