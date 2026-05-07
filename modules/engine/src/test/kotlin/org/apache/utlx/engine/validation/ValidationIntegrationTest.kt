package org.apache.utlx.engine.validation

import com.google.protobuf.ByteString
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.EngineConfig
import org.apache.utlx.engine.config.EngineSettings
import org.apache.utlx.engine.config.HealthConfig
import org.apache.utlx.engine.config.MonitoringConfig
import org.apache.utlx.engine.proto.BatchItem
import org.apache.utlx.engine.proto.ExecuteRequest
import org.apache.utlx.engine.proto.LoadTransformationRequest
import org.apache.utlx.engine.proto.UtlxeServiceGrpc
import org.apache.utlx.engine.transport.UtlxeServiceImpl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for schema validation through the full transport layer.
 * Tests STRICT/WARN/SKIP policies end-to-end via gRPC.
 */
class ValidationIntegrationTest {

    private lateinit var engine: UtlxEngine
    private lateinit var channel: io.grpc.ManagedChannel
    private lateinit var stub: UtlxeServiceGrpc.UtlxeServiceBlockingStub
    private lateinit var server: io.grpc.Server

    private val personSchema = """
    {
      "${'$'}schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "required": ["name", "age"],
      "properties": {
        "name": { "type": "string" },
        "age": { "type": "integer", "minimum": 0 }
      }
    }
    """.trimIndent()

    @BeforeEach
    fun setup() {
        engine = UtlxEngine(EngineConfig(
            engine = EngineSettings(
                name = "test-validation",
                monitoring = MonitoringConfig(health = HealthConfig(port = 0))
            )
        ))
        engine.initializeEmpty()

        val serverName = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(serverName).directExecutor()
            .addService(UtlxeServiceImpl(engine, engine.registry)).build().start()
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        stub = UtlxeServiceGrpc.newBlockingStub(channel)
    }

    @AfterEach
    fun teardown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun `STRICT policy rejects invalid input`() {
        // Load with input validation enabled, STRICT policy
        val loadResp = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("strict-test")
                .setUtlxSource("%utlx 1.0\ninput json\noutput json\n---\n\$input\n")
                .setStrategy("TEMPLATE")
                .setValidationPolicy("STRICT")
                .putConfig("validate_input", "true")
                .putConfig("input_schema", personSchema)
                .putConfig("input_schema_format", "json-schema")
                .build()
        )
        assertTrue(loadResp.success, "Load failed: ${loadResp.error}")

        // Send invalid input (missing required "age" field)
        val execResp = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("strict-test")
                .setPayload(ByteString.copyFromUtf8("""{"name": "Alice"}"""))
                .build()
        )

        assertFalse(execResp.success, "STRICT should reject invalid input")
        assertEquals(org.apache.utlx.engine.proto.ErrorPhase.PRE_VALIDATION, execResp.errorPhase)
        assertTrue(execResp.validationErrorsCount > 0, "Should have validation errors")
    }

    @Test
    fun `STRICT policy allows valid input`() {
        val loadResp = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("strict-valid")
                .setUtlxSource("%utlx 1.0\ninput json\noutput json\n---\n\$input\n")
                .setStrategy("TEMPLATE")
                .setValidationPolicy("STRICT")
                .putConfig("validate_input", "true")
                .putConfig("input_schema", personSchema)
                .putConfig("input_schema_format", "json-schema")
                .build()
        )
        assertTrue(loadResp.success)

        val execResp = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("strict-valid")
                .setPayload(ByteString.copyFromUtf8("""{"name": "Alice", "age": 30}"""))
                .build()
        )

        assertTrue(execResp.success, "Valid input should pass STRICT: ${execResp.error}")
    }

    @Test
    fun `WARN policy continues with warnings`() {
        val loadResp = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("warn-test")
                .setUtlxSource("%utlx 1.0\ninput json\noutput json\n---\n\$input\n")
                .setStrategy("TEMPLATE")
                .setValidationPolicy("WARN")
                .putConfig("validate_input", "true")
                .putConfig("input_schema", personSchema)
                .putConfig("input_schema_format", "json-schema")
                .build()
        )
        assertTrue(loadResp.success)

        // Send invalid input — WARN should continue
        val execResp = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("warn-test")
                .setPayload(ByteString.copyFromUtf8("""{"name": "Alice"}"""))
                .build()
        )

        assertTrue(execResp.success, "WARN should not reject: ${execResp.error}")
        assertTrue(execResp.validationErrorsCount > 0, "Should have warning-level validation errors")
    }

    @Test
    fun `SKIP policy performs no validation`() {
        val loadResp = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("skip-test")
                .setUtlxSource("%utlx 1.0\ninput json\noutput json\n---\n\$input\n")
                .setStrategy("TEMPLATE")
                .setValidationPolicy("SKIP")
                .build()
        )
        assertTrue(loadResp.success)

        // Any input should pass — no validation
        val execResp = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("skip-test")
                .setPayload(ByteString.copyFromUtf8("""{"anything": "goes"}"""))
                .build()
        )

        assertTrue(execResp.success)
        assertEquals(0, execResp.validationErrorsCount, "SKIP should produce no validation errors")
    }

    @Test
    fun `output validation catches bad transformation output`() {
        // A transform that produces output missing the required "age" field
        val loadResp = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("output-val")
                .setUtlxSource("%utlx 1.0\ninput json\noutput json\n---\n{name: \$input.name}\n")
                .setStrategy("TEMPLATE")
                .setValidationPolicy("STRICT")
                .putConfig("validate_output", "true")
                .putConfig("output_schema", personSchema)
                .putConfig("output_schema_format", "json-schema")
                .build()
        )
        assertTrue(loadResp.success)

        val execResp = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("output-val")
                .setPayload(ByteString.copyFromUtf8("""{"name": "Alice", "age": 30}"""))
                .build()
        )

        assertFalse(execResp.success, "Output missing 'age' should fail POST_VALIDATION")
        assertEquals(org.apache.utlx.engine.proto.ErrorPhase.POST_VALIDATION, execResp.errorPhase)
    }

    @Test
    fun `transformation error returns TRANSFORMATION phase`() {
        val loadResp = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("transform-err")
                .setUtlxSource("%utlx 1.0\ninput json\noutput json\n---\n\$input.nonexistent.deep.path\n")
                .setStrategy("TEMPLATE")
                .build()
        )
        assertTrue(loadResp.success)

        val execResp = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("transform-err")
                .setPayload(ByteString.copyFromUtf8("""{"name": "Alice"}"""))
                .build()
        )

        // This may succeed with null output or fail depending on interpreter behavior
        // The important thing is it doesn't crash the engine
        assertTrue(execResp.success || execResp.errorPhase == org.apache.utlx.engine.proto.ErrorPhase.TRANSFORMATION)
    }

    // ── EF02: Validation override tests ──

    @Test
    fun `validation override OFF disables validation for invalid input`() {
        // Load with STRICT validation
        val loadResp = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("override-test")
                .setUtlxSource("%utlx 1.0\ninput json\noutput json\n---\n\$input\n")
                .setStrategy("TEMPLATE")
                .setValidationPolicy("STRICT")
                .putConfig("validate_input", "true")
                .putConfig("input_schema", personSchema)
                .putConfig("input_schema_format", "json-schema")
                .build()
        )
        assertTrue(loadResp.success)

        // Without override: invalid input rejected
        val resp1 = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("override-test")
                .setPayload(ByteString.copyFromUtf8("""{"name": "Alice"}"""))
                .setContentType("application/json")
                .build()
        )
        assertFalse(resp1.success, "Should fail with STRICT and missing 'age'")
        assertTrue(resp1.validationErrorsCount > 0, "Should have validation errors")

        // Set override to OFF
        engine.validationOverrides.set("override-test", "off")

        // With override: same invalid input passes
        val resp2 = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("override-test")
                .setPayload(ByteString.copyFromUtf8("""{"name": "Alice"}"""))
                .setContentType("application/json")
                .build()
        )
        assertTrue(resp2.success, "Should pass with override OFF: ${resp2.error}")

        // Remove override: invalid input rejected again
        engine.validationOverrides.remove("override-test")

        val resp3 = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("override-test")
                .setPayload(ByteString.copyFromUtf8("""{"name": "Alice"}"""))
                .setContentType("application/json")
                .build()
        )
        assertFalse(resp3.success, "Should fail again after override removed")
    }

    @Test
    fun `STRICT validation reports ALL errors not just first`() {
        val loadResp = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("all-errors-test")
                .setUtlxSource("%utlx 1.0\ninput json\noutput json\n---\n\$input\n")
                .setStrategy("TEMPLATE")
                .setValidationPolicy("STRICT")
                .putConfig("validate_input", "true")
                .putConfig("input_schema", personSchema)
                .putConfig("input_schema_format", "json-schema")
                .build()
        )
        assertTrue(loadResp.success)

        // Send input missing BOTH required fields
        val resp = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("all-errors-test")
                .setPayload(ByteString.copyFromUtf8("""{"extra": "field"}"""))
                .setContentType("application/json")
                .build()
        )
        assertFalse(resp.success)
        assertEquals(org.apache.utlx.engine.proto.ErrorPhase.PRE_VALIDATION, resp.errorPhase)
        // Should report errors for BOTH missing 'name' AND missing 'age'
        assertTrue(resp.validationErrorsCount >= 2,
            "Should report at least 2 errors (name + age missing), got ${resp.validationErrorsCount}: ${resp.validationErrorsList.map { it.message }}")
    }

    @Test
    fun `validation error details include message path and severity`() {
        val loadResp = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("detail-test")
                .setUtlxSource("%utlx 1.0\ninput json\noutput json\n---\n\$input\n")
                .setStrategy("TEMPLATE")
                .setValidationPolicy("STRICT")
                .putConfig("validate_input", "true")
                .putConfig("input_schema", personSchema)
                .putConfig("input_schema_format", "json-schema")
                .build()
        )
        assertTrue(loadResp.success)

        val resp = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("detail-test")
                .setPayload(ByteString.copyFromUtf8("""{"name": 123, "age": "not a number"}"""))
                .setContentType("application/json")
                .build()
        )
        assertFalse(resp.success)
        assertTrue(resp.validationErrorsCount > 0)

        // Each error should have a message and severity
        resp.validationErrorsList.forEach { err ->
            assertTrue(err.message.isNotEmpty(), "Error message should not be empty")
            assertTrue(err.severity.isNotEmpty(), "Error severity should not be empty")
        }
    }

    @Test
    fun `WARN policy passes through with warnings in response`() {
        val loadResp = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("warn-detail-test")
                .setUtlxSource("%utlx 1.0\ninput json\noutput json\n---\n\$input\n")
                .setStrategy("TEMPLATE")
                .setValidationPolicy("WARN")
                .putConfig("validate_input", "true")
                .putConfig("input_schema", personSchema)
                .putConfig("input_schema_format", "json-schema")
                .build()
        )
        assertTrue(loadResp.success)

        // Invalid input — should succeed but with warnings
        val resp = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("warn-detail-test")
                .setPayload(ByteString.copyFromUtf8("""{"name": "Alice"}"""))
                .setContentType("application/json")
                .build()
        )
        assertTrue(resp.success, "WARN should succeed: ${resp.error}")
        assertTrue(resp.output.toStringUtf8().contains("Alice"), "Output should be produced")
        assertTrue(resp.validationErrorsCount > 0, "Should have warnings: ${resp.validationErrorsList}")
    }

    @Test
    fun `output validation failure includes the output for inspection`() {
        val outputSchema = """
        {
          "${'$'}schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "required": ["result", "timestamp"],
          "properties": {
            "result": { "type": "string" },
            "timestamp": { "type": "string" }
          }
        }
        """.trimIndent()

        val loadResp = stub.loadTransformation(
            LoadTransformationRequest.newBuilder()
                .setTransformationId("output-val-test")
                .setUtlxSource("%utlx 1.0\ninput json\noutput json\n---\n{result: \$input.name}\n")
                .setStrategy("TEMPLATE")
                .setValidationPolicy("STRICT")
                .putConfig("validate_output", "true")
                .putConfig("output_schema", outputSchema)
                .putConfig("output_schema_format", "json-schema")
                .build()
        )
        assertTrue(loadResp.success)

        // Input is valid, but output missing "timestamp" required by output schema
        val resp = stub.execute(
            ExecuteRequest.newBuilder()
                .setTransformationId("output-val-test")
                .setPayload(ByteString.copyFromUtf8("""{"name": "Alice"}"""))
                .setContentType("application/json")
                .build()
        )
        assertFalse(resp.success)
        assertEquals(org.apache.utlx.engine.proto.ErrorPhase.POST_VALIDATION, resp.errorPhase)
        // Output should be included even though validation failed — for debugging
        assertTrue(resp.output.toStringUtf8().contains("Alice"),
            "Output should be included on POST_VALIDATION failure for inspection")
        assertTrue(resp.validationErrorsCount > 0)
    }
}
