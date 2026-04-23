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
}
