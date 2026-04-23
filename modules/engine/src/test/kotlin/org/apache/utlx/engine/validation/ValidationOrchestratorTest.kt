package org.apache.utlx.engine.validation

import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.registry.TransformationInstance
import org.apache.utlx.engine.strategy.ExecutionResult
import org.apache.utlx.engine.strategy.ExecutionStrategy
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationOrchestratorTest {

    private val personSchema = """
    {
      "${'$'}schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "required": ["name"],
      "properties": {
        "name": { "type": "string" }
      }
    }
    """.trimIndent()

    private val identityStrategy = object : ExecutionStrategy {
        override val name = "TEST"
        override fun initialize(source: String, config: TransformConfig) {}
        override fun execute(input: String) = ExecutionResult(output = input)
        override fun shutdown() {}
    }

    private val failingStrategy = object : ExecutionStrategy {
        override val name = "FAILING"
        override fun initialize(source: String, config: TransformConfig) {}
        override fun execute(input: String): ExecutionResult {
            throw RuntimeException("Transform exploded")
        }
        override fun shutdown() {}
    }

    // =========================================================================
    // No Validation (SKIP policy)
    // =========================================================================

    @Test
    fun `SKIP policy - transform succeeds without validators`() {
        val instance = TransformationInstance(
            name = "test",
            source = "",
            strategy = identityStrategy,
            config = TransformConfig(validationPolicy = "SKIP")
        )

        val result = ValidationOrchestrator.execute(instance, """{"name": "Alice"}""")
        assertTrue(result.success)
        assertTrue(result.validationErrors.isEmpty())
    }

    // =========================================================================
    // STRICT Pre-Validation
    // =========================================================================

    @Test
    fun `STRICT - valid input passes pre-validation`() {
        val validator = JsonSchemaValidator(personSchema)
        val instance = TransformationInstance(
            name = "test",
            source = "",
            strategy = identityStrategy,
            config = TransformConfig(validationPolicy = "STRICT"),
            inputValidator = validator
        )

        val result = ValidationOrchestrator.execute(instance, """{"name": "Alice"}""")
        assertTrue(result.success)
    }

    @Test
    fun `STRICT - invalid input rejected at PRE_VALIDATION`() {
        val validator = JsonSchemaValidator(personSchema)
        val instance = TransformationInstance(
            name = "test",
            source = "",
            strategy = identityStrategy,
            config = TransformConfig(validationPolicy = "STRICT"),
            inputValidator = validator
        )

        val result = ValidationOrchestrator.execute(instance, """{"age": 30}""")
        assertFalse(result.success)
        assertEquals(ErrorPhase.PRE_VALIDATION, result.phase)
        assertTrue(result.validationErrors.isNotEmpty())
        // Output should NOT be produced (rejected before transform)
        assertEquals(null, result.output)
    }

    // =========================================================================
    // STRICT Post-Validation
    // =========================================================================

    @Test
    fun `STRICT - valid output passes post-validation`() {
        val validator = JsonSchemaValidator(personSchema)
        val instance = TransformationInstance(
            name = "test",
            source = "",
            strategy = identityStrategy,
            config = TransformConfig(validationPolicy = "STRICT"),
            outputValidator = validator
        )

        val result = ValidationOrchestrator.execute(instance, """{"name": "Alice"}""")
        assertTrue(result.success)
    }

    @Test
    fun `STRICT - invalid output rejected at POST_VALIDATION`() {
        // Strategy that produces output missing required field
        val badOutputStrategy = object : ExecutionStrategy {
            override val name = "BAD_OUTPUT"
            override fun initialize(source: String, config: TransformConfig) {}
            override fun execute(input: String) = ExecutionResult(output = """{"age": 30}""")
            override fun shutdown() {}
        }

        val validator = JsonSchemaValidator(personSchema)
        val instance = TransformationInstance(
            name = "test",
            source = "",
            strategy = badOutputStrategy,
            config = TransformConfig(validationPolicy = "STRICT"),
            outputValidator = validator
        )

        val result = ValidationOrchestrator.execute(instance, """{"name": "Alice"}""")
        assertFalse(result.success)
        assertEquals(ErrorPhase.POST_VALIDATION, result.phase)
        assertTrue(result.validationErrors.isNotEmpty())
    }

    // =========================================================================
    // WARN Policy
    // =========================================================================

    @Test
    fun `WARN - invalid input produces warnings but continues`() {
        val validator = JsonSchemaValidator(personSchema)
        val instance = TransformationInstance(
            name = "test",
            source = "",
            strategy = identityStrategy,
            config = TransformConfig(validationPolicy = "WARN"),
            inputValidator = validator
        )

        val result = ValidationOrchestrator.execute(instance, """{"age": 30}""")
        assertTrue(result.success, "WARN policy should not reject")
        assertTrue(result.validationErrors.isNotEmpty(), "Should have warnings")
        assertTrue(result.output != null, "Output should be produced")
    }

    @Test
    fun `WARN - invalid output produces warnings but succeeds`() {
        val badOutputStrategy = object : ExecutionStrategy {
            override val name = "BAD_OUTPUT"
            override fun initialize(source: String, config: TransformConfig) {}
            override fun execute(input: String) = ExecutionResult(output = """{"age": 30}""")
            override fun shutdown() {}
        }

        val validator = JsonSchemaValidator(personSchema)
        val instance = TransformationInstance(
            name = "test",
            source = "",
            strategy = badOutputStrategy,
            config = TransformConfig(validationPolicy = "WARN"),
            outputValidator = validator
        )

        val result = ValidationOrchestrator.execute(instance, """{"name": "Alice"}""")
        assertTrue(result.success)
        assertTrue(result.validationErrors.isNotEmpty(), "Should have output validation warnings")
    }

    // =========================================================================
    // Transformation Errors
    // =========================================================================

    @Test
    fun `transformation error returns TRANSFORMATION phase`() {
        val instance = TransformationInstance(
            name = "test",
            source = "",
            strategy = failingStrategy,
            config = TransformConfig(validationPolicy = "STRICT")
        )

        val result = ValidationOrchestrator.execute(instance, """{"name": "Alice"}""")
        assertFalse(result.success)
        assertEquals(ErrorPhase.TRANSFORMATION, result.phase)
        assertTrue(result.error!!.contains("exploded"))
    }

    // =========================================================================
    // Combined Pre + Post Validation
    // =========================================================================

    @Test
    fun `STRICT - both pre and post validation with valid data`() {
        val inputValidator = JsonSchemaValidator(personSchema)
        val outputValidator = JsonSchemaValidator(personSchema)
        val instance = TransformationInstance(
            name = "test",
            source = "",
            strategy = identityStrategy,
            config = TransformConfig(validationPolicy = "STRICT"),
            inputValidator = inputValidator,
            outputValidator = outputValidator
        )

        val result = ValidationOrchestrator.execute(instance, """{"name": "Alice"}""")
        assertTrue(result.success)
        assertTrue(result.validationErrors.isEmpty())
    }
}
