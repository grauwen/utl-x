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

    // =========================================================================
    // COMPILED Strategy Tests
    // =========================================================================

    @Test
    fun `compiled strategy - load and execute single input`() {
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{name: upperCase(\$input.name), done: true}\n"

        val loadResp = blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("compiled-single")
                .setUtlxSource(source)
                .setStrategy("COMPILED")
                .build()
        )
        assertTrue(loadResp.success, "Load COMPILED should succeed: ${loadResp.error}")

        val execResp = blockingStub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("compiled-single")
                .setPayload(ByteString.copyFromUtf8("""{"name": "alice"}"""))
                .setContentType("application/json")
                .setCorrelationId("comp-1")
                .build()
        )

        assertTrue(execResp.success, "Execute should succeed: ${execResp.error}")
        assertEquals("comp-1", execResp.correlationId)
        val output = execResp.output.toStringUtf8()
        assertTrue(output.contains("ALICE"), "upperCase should work: $output")
        assertTrue(output.contains("true"), "done field: $output")
    }

    @Test
    fun `compiled strategy - multi-input JSON envelope`() {
        val source = "%utlx 1.0\ninput: order json, customer json\noutput json\n---\n{orderId: @order.id, name: @customer.name}\n"

        val loadResp = blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("compiled-multi")
                .setUtlxSource(source)
                .setStrategy("COMPILED")
                .build()
        )
        assertTrue(loadResp.success, "Load should succeed: ${loadResp.error}")

        val envelope = """{"order": {"id": "ORD-G01"}, "customer": {"name": "TechCorp"}}"""
        val execResp = blockingStub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("compiled-multi")
                .setPayload(ByteString.copyFromUtf8(envelope))
                .setContentType("application/json")
                .setCorrelationId("comp-multi-1")
                .build()
        )

        assertTrue(execResp.success, "Execute should succeed: ${execResp.error}")
        val output = execResp.output.toStringUtf8()
        assertTrue(output.contains("ORD-G01"), "Order ID: $output")
        assertTrue(output.contains("TechCorp"), "Customer name: $output")
    }

    @Test
    fun `compiled strategy - batch execute`() {
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{id: \$input.id, processed: true}\n"

        blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("compiled-batch")
                .setUtlxSource(source)
                .setStrategy("COMPILED")
                .build()
        )

        val batchResp = blockingStub.executeBatch(
            ExecuteBatchRequest.newBuilder()
                .setTransformationId("compiled-batch")
                .addItems(BatchItem.newBuilder().setPayload(ByteString.copyFromUtf8("""{"id": "A"}""")).setCorrelationId("cb-1").build())
                .addItems(BatchItem.newBuilder().setPayload(ByteString.copyFromUtf8("""{"id": "B"}""")).setCorrelationId("cb-2").build())
                .addItems(BatchItem.newBuilder().setPayload(ByteString.copyFromUtf8("""{"id": "C"}""")).setCorrelationId("cb-3").build())
                .build()
        )

        assertEquals(3, batchResp.resultsCount)
        assertTrue(batchResp.resultsList.all { it.success }, "All batch items should succeed")
        assertTrue(batchResp.getResults(0).output.toStringUtf8().contains("A"))
        assertTrue(batchResp.getResults(2).output.toStringUtf8().contains("C"))
    }

    @Test
    fun `compiled strategy - mixed format JSON and XML inputs`() {
        val source = "%utlx 1.0\ninput: order json, product xml\noutput json\n---\n{orderId: @order.id, productName: @product.item.name}\n"

        val loadResp = blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("compiled-mixed-xml")
                .setUtlxSource(source)
                .setStrategy("COMPILED")
                .build()
        )
        assertTrue(loadResp.success, "Load should succeed: ${loadResp.error}")

        val envelope = """{"order": {"id": "ORD-MX1"}, "product": "<item><name>Sensor Pro</name><price>89.50</price></item>"}"""
        val execResp = blockingStub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("compiled-mixed-xml")
                .setPayload(ByteString.copyFromUtf8(envelope))
                .setContentType("application/json")
                .setCorrelationId("mx-1")
                .build()
        )

        assertTrue(execResp.success, "Execute should succeed: ${execResp.error}")
        val output = execResp.output.toStringUtf8()
        assertTrue(output.contains("ORD-MX1"), "Order ID: $output")
        assertTrue(output.contains("Sensor Pro"), "Product from XML: $output")
    }

    @Test
    fun `compiled strategy - mixed format JSON and YAML inputs`() {
        val source = "%utlx 1.0\ninput: order json, config yaml\noutput json\n---\n{orderId: @order.id, region: @config.shipping.region}\n"

        val loadResp = blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("compiled-mixed-yaml")
                .setUtlxSource(source)
                .setStrategy("COMPILED")
                .build()
        )
        assertTrue(loadResp.success, "Load should succeed: ${loadResp.error}")

        val envelope = """{"order": {"id": "ORD-MY1"}, "config": "shipping:\n  region: EU-WEST\n  warehouse: Rotterdam"}"""
        val execResp = blockingStub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("compiled-mixed-yaml")
                .setPayload(ByteString.copyFromUtf8(envelope))
                .setContentType("application/json")
                .setCorrelationId("my-1")
                .build()
        )

        assertTrue(execResp.success, "Execute should succeed: ${execResp.error}")
        val output = execResp.output.toStringUtf8()
        assertTrue(output.contains("ORD-MY1"), "Order ID: $output")
        assertTrue(output.contains("EU-WEST"), "Region from YAML: $output")
    }

    @Test
    fun `compiled strategy - three formats JSON XML YAML`() {
        val source = "%utlx 1.0\ninput: order json, catalog xml, settings yaml\noutput json\n---\n{orderId: @order.id, product: @catalog.item.name, warehouse: @settings.fulfillment.warehouse}\n"

        val loadResp = blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("compiled-3fmt")
                .setUtlxSource(source)
                .setStrategy("COMPILED")
                .build()
        )
        assertTrue(loadResp.success, "Load should succeed: ${loadResp.error}")

        val envelope = """{"order": {"id": "ORD-3F1", "qty": 2}, "catalog": "<item><name>Widget X</name><sku>WX-100</sku></item>", "settings": "fulfillment:\n  warehouse: Amsterdam\n  carrier: DHL"}"""
        val execResp = blockingStub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("compiled-3fmt")
                .setPayload(ByteString.copyFromUtf8(envelope))
                .setContentType("application/json")
                .setCorrelationId("3f-1")
                .build()
        )

        assertTrue(execResp.success, "Execute should succeed: ${execResp.error}")
        val output = execResp.output.toStringUtf8()
        assertTrue(output.contains("ORD-3F1"), "Order ID (JSON): $output")
        assertTrue(output.contains("Widget X"), "Product (XML): $output")
        assertTrue(output.contains("Amsterdam"), "Warehouse (YAML): $output")
    }

    @Test
    fun `compiled strategy - full lifecycle`() {
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{greeting: concat(\"Hello \", \$input.name)}\n"

        // Load
        val loadResp = blockingStub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("compiled-lc")
                .setUtlxSource(source)
                .setStrategy("COMPILED")
                .build()
        )
        assertTrue(loadResp.success)

        // Execute 3 times
        for (i in 1..3) {
            val resp = blockingStub.execute(
                ExecuteRequest.newBuilder()
                    .setTransformationId("compiled-lc")
                    .setPayload(ByteString.copyFromUtf8("""{"name": "User$i"}"""))
                    .setCorrelationId("clc-$i")
                    .build()
            )
            assertTrue(resp.success)
            assertTrue(resp.output.toStringUtf8().contains("Hello User$i"))
        }

        // Health
        val health = blockingStub.health(HealthRequest.getDefaultInstance())
        assertEquals(1, health.loadedTransformations)
        assertEquals(3, health.totalExecutions)

        // Unload
        val unloadResp = blockingStub.unloadTransformation(
            UnloadTransformationRequest.newBuilder().setTransformationId("compiled-lc").build()
        )
        assertTrue(unloadResp.success)
        assertEquals(0, engine.registry.size())
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
