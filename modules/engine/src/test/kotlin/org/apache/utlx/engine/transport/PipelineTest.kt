package org.apache.utlx.engine.transport

import com.google.protobuf.ByteString
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.EngineConfig
import org.apache.utlx.engine.config.EngineSettings
import org.apache.utlx.engine.config.HealthConfig
import org.apache.utlx.engine.config.MonitoringConfig
import org.apache.utlx.engine.proto.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PipelineTest {

    private lateinit var engine: UtlxEngine
    private lateinit var channel: io.grpc.ManagedChannel
    private lateinit var stub: UtlxeServiceGrpc.UtlxeServiceBlockingStub
    private lateinit var server: io.grpc.Server

    @BeforeEach
    fun setup() {
        engine = UtlxEngine(
            EngineConfig(
                engine = EngineSettings(
                    name = "test-pipeline",
                    monitoring = MonitoringConfig(health = HealthConfig(port = 0))
                )
            )
        )
        engine.initializeEmpty()

        val serverName = InProcessServerBuilder.generateName()
        val service = UtlxeServiceImpl(engine, engine.registry)
        server = InProcessServerBuilder.forName(serverName).directExecutor().addService(service).build().start()
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        stub = UtlxeServiceGrpc.newBlockingStub(channel)
    }

    @AfterEach
    fun teardown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    private fun loadTransform(id: String, source: String) {
        val resp = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId(id)
                .setUtlxSource(source)
                .setStrategy("TEMPLATE")
                .build()
        )
        assertTrue(resp.success, "Load '$id' failed: ${resp.error}")
    }

    @Test
    fun `two-stage pipeline chains output to input`() {
        loadTransform("stage1", """
            %utlx 1.0
            input json
            output json
            ---
            { name: lowerCase(${'$'}input.NAME), age: ${'$'}input.AGE }
        """.trimIndent())

        loadTransform("stage2", """
            %utlx 1.0
            input json
            output json
            ---
            { name: ${'$'}input.name, age: ${'$'}input.age, isAdult: ${'$'}input.age >= 18 }
        """.trimIndent())

        val resp = stub.executePipeline(
            ExecutePipelineRequest.newBuilder()
                .addTransformationIds("stage1")
                .addTransformationIds("stage2")
                .setPayload(ByteString.copyFromUtf8("""{"NAME": "ALICE", "AGE": 28}"""))
                .setCorrelationId("pipe-1")
                .build()
        )

        assertTrue(resp.success, "Pipeline failed: ${resp.error}")
        assertEquals(2, resp.stagesCompleted)
        assertEquals("pipe-1", resp.correlationId)
        assertTrue(resp.totalDurationUs > 0)

        val output = resp.output.toStringUtf8()
        assertTrue(output.contains("alice"), "Name should be lowercased: $output")
        assertTrue(output.contains("isAdult"), "Should have computed field: $output")
    }

    @Test
    fun `three-stage pipeline with format conversion`() {
        loadTransform("to-xml", """
            %utlx 1.0
            input json
            output xml
            ---
            { item: { id: ${'$'}input.id, name: ${'$'}input.name } }
        """.trimIndent())

        loadTransform("xml-transform", """
            %utlx 1.0
            input xml
            output xml
            ---
            { product: { code: ${'$'}input.item.id, label: upperCase(${'$'}input.item.name) } }
        """.trimIndent())

        loadTransform("to-json", """
            %utlx 1.0
            input xml
            output json
            ---
            { productCode: ${'$'}input.product.code, productLabel: ${'$'}input.product.label }
        """.trimIndent())

        val resp = stub.executePipeline(
            ExecutePipelineRequest.newBuilder()
                .addTransformationIds("to-xml")
                .addTransformationIds("xml-transform")
                .addTransformationIds("to-json")
                .setPayload(ByteString.copyFromUtf8("""{"id": "P001", "name": "widget"}"""))
                .build()
        )

        assertTrue(resp.success, "Pipeline failed: ${resp.error}")
        assertEquals(3, resp.stagesCompleted)

        val output = resp.output.toStringUtf8()
        assertTrue(output.contains("P001"))
        assertTrue(output.contains("WIDGET"))
    }

    @Test
    fun `pipeline fails at second stage reports correct stage count`() {
        loadTransform("good-stage", """
            %utlx 1.0
            input json
            output json
            ---
            { value: ${'$'}input.x }
        """.trimIndent())

        // Don't load "missing-stage" — it doesn't exist

        val resp = stub.executePipeline(
            ExecutePipelineRequest.newBuilder()
                .addTransformationIds("good-stage")
                .addTransformationIds("missing-stage")
                .setPayload(ByteString.copyFromUtf8("""{"x": 42}"""))
                .build()
        )

        assertFalse(resp.success)
        assertEquals(1, resp.stagesCompleted, "Should have completed 1 stage before failure")
        assertTrue(resp.error.contains("missing-stage"))
    }

    @Test
    fun `empty pipeline returns error`() {
        val resp = stub.executePipeline(
            ExecutePipelineRequest.newBuilder()
                .setPayload(ByteString.copyFromUtf8("{}"))
                .build()
        )

        assertFalse(resp.success)
        assertTrue(resp.error.contains("at least one"))
    }

    @Test
    fun `single-stage pipeline works like regular execute`() {
        loadTransform("solo", """
            %utlx 1.0
            input json
            output json
            ---
            { result: ${'$'}input.value * 2 }
        """.trimIndent())

        val resp = stub.executePipeline(
            ExecutePipelineRequest.newBuilder()
                .addTransformationIds("solo")
                .setPayload(ByteString.copyFromUtf8("""{"value": 21}"""))
                .setCorrelationId("solo-1")
                .build()
        )

        assertTrue(resp.success)
        assertEquals(1, resp.stagesCompleted)
        assertEquals("solo-1", resp.correlationId)
        assertTrue(resp.output.toStringUtf8().contains("42"))
    }

    @Test
    fun `pipeline with COPY strategy stages`() {
        val resp1 = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("copy-stage")
                .setUtlxSource("""
                    %utlx 1.0
                    input json
                    output json
                    ---
                    { name: upperCase(${'$'}input.name), processed: true }
                """.trimIndent())
                .setStrategy("COPY")
                .build()
        )
        assertTrue(resp1.success, "COPY load failed: ${resp1.error}")

        loadTransform("template-stage", """
            %utlx 1.0
            input json
            output json
            ---
            { name: ${'$'}input.name, processed: ${'$'}input.processed, final: true }
        """.trimIndent())

        val resp = stub.executePipeline(
            ExecutePipelineRequest.newBuilder()
                .addTransformationIds("copy-stage")
                .addTransformationIds("template-stage")
                .setPayload(ByteString.copyFromUtf8("""{"name": "alice"}"""))
                .build()
        )

        assertTrue(resp.success, "Mixed strategy pipeline failed: ${resp.error}")
        assertEquals(2, resp.stagesCompleted)
        val output = resp.output.toStringUtf8()
        assertTrue(output.contains("ALICE"))
        assertTrue(output.contains("final"))
    }
}
