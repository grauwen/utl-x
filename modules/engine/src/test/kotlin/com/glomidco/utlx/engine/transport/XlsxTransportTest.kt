package com.glomidco.utlx.engine.transport

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.core.udm.PayloadBytes
import com.glomidco.utlx.engine.UtlxEngine
import com.glomidco.utlx.engine.config.EngineConfig
import com.glomidco.utlx.engine.proto.BatchItem
import com.glomidco.utlx.engine.proto.ExecuteBatchRequest
import com.glomidco.utlx.engine.proto.ExecutePipelineRequest
import com.glomidco.utlx.engine.proto.ExecuteRequest
import com.glomidco.utlx.engine.proto.LoadTransformationRequest
import com.glomidco.utlx.engine.validation.ValidationOrchestrator
import com.glomidco.utlx.engine.validation.SchemaValidator
import com.glomidco.utlx.engine.validation.SchemaValidationError
import com.glomidco.utlx.formats.json.JSONParser
import com.glomidco.utlx.formats.xlsx.XlsxParser
import com.glomidco.utlx.formats.xlsx.XlsxSerializer
import com.google.protobuf.ByteString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class XlsxTransportTest {
    private val writeSource =
        "%utlx 1.0\ninput json\noutput xlsx {sheet: \"Collection\"}\n---\n\$input"
    private val readSource =
        "%utlx 1.0\ninput xlsx {sheet: \"Collection\"}\noutput json\n---\n\$input"
    private val json = "[{\"Name\":\"Reserve\",\"Remaining\":0}]"

    @Test
    fun `pipeline retains binary data between stages and in its final result`() {
        val engine = UtlxEngine(EngineConfig())
        engine.initializeEmpty()
        try {
            load(engine, "write", writeSource)
            load(engine, "read", readSource)
            val request = ExecutePipelineRequest.newBuilder()
                .addAllTransformationIds(listOf("write", "read", "write"))
                .setPayload(ByteString.copyFromUtf8(json))
                .setContentType("application/json")
                .build()
            val result = TransportHandlers.handleExecutePipeline(request, engine)
            assertTrue(result.success, result.error)
            assertEquals(3, result.stagesCompleted)
            assertEquals(XlsxSerializer.CONTENT_TYPE, result.outputContentType)
            assertEquals(1, XlsxParser.parse(result.output.toByteArray(), "Collection").elements.size)
        } finally {
            engine.stop()
        }
    }

    @Test
    fun `batch response preserves each workbook and its content type`() {
        val engine = UtlxEngine(EngineConfig())
        engine.initializeEmpty()
        try {
            load(engine, "write", writeSource)
            val request = ExecuteBatchRequest.newBuilder().setTransformationId("write")
            repeat(2) {
                request.addItems(BatchItem.newBuilder().setPayload(ByteString.copyFromUtf8(json)))
            }
            val result = TransportHandlers.handleExecuteBatch(request.build(), engine)
            assertEquals(2, result.resultsCount)
            for (item in result.resultsList) {
                assertTrue(item.success, item.error)
                assertEquals(XlsxSerializer.CONTENT_TYPE, item.outputContentType)
                assertEquals(1, XlsxParser.parse(item.output.toByteArray(), "Collection").elements.size)
            }
        } finally {
            engine.stop()
        }
    }

    @Test
    fun `named protobuf inputs retain workbook bytes`() {
        val engine = UtlxEngine(EngineConfig())
        engine.initializeEmpty()
        try {
            load(engine, "combine",
                "%utlx 1.0\ninput: inventory xlsx, pricing json\noutput json\n---\n" +
                    "{name: first(\$inventory).Name, price: \$pricing.price}")
            val workbook = XlsxSerializer.serialize(
                UDM.Array(listOf(UDM.Object.of("Name" to UDM.Scalar.string("Reserve"))))
            )
            val request = ExecuteRequest.newBuilder()
                .setTransformationId("combine")
                .putNamedInputs("inventory", ByteString.copyFrom(workbook))
                .putNamedInputs("pricing", ByteString.copyFromUtf8("{\"price\":17.5}"))
                .build()
            val result = TransportHandlers.handleExecute(request, engine)
            assertTrue(result.success, result.error)
            val row = JSONParser(result.output.toStringUtf8()).parse() as UDM.Object
            assertEquals("Reserve", (row.get("name") as UDM.Scalar).asString())
            assertEquals(17.5, (row.get("price") as UDM.Scalar).asNumber())
        } finally {
            engine.stop()
        }
    }

    @Test
    fun `post-validation receives the workbook bytes and retains them on failure`() {
        val engine = UtlxEngine(EngineConfig())
        engine.initializeEmpty()
        try {
            load(engine, "write", writeSource)
            val instance = engine.registry.get("write")!!.copy(outputValidator = object : SchemaValidator {
                override val schemaFormat = "test"
                override fun validate(payload: ByteArray, contentType: String): List<SchemaValidationError> {
                    assertEquals(0x50, payload[0].toInt())
                    assertEquals(1, XlsxParser.parse(payload, "Collection").elements.size)
                    return listOf(SchemaValidationError("Rejected for this test."))
                }
            })
            val result = ValidationOrchestrator.execute(instance, PayloadBytes.fromString(json), "STRICT")
            assertFalse(result.success)
            assertEquals(1, XlsxParser.parse(assertNotNull(result.outputBytes), "Collection").elements.size)
        } finally {
            engine.stop()
        }
    }

    private fun load(engine: UtlxEngine, id: String, source: String) {
        val request = LoadTransformationRequest.newBuilder()
            .setTransformationId(id)
            .setUtlxSource(source)
            .setStrategy("COMPILED")
            .build()
        val result = TransportHandlers.handleLoadTransformation(request, engine)
        assertTrue(result.success, result.error)
    }
}
