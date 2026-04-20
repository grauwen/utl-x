package org.apache.utlx.engine.validation

import org.apache.utlx.engine.registry.TransformationInstance
import org.slf4j.LoggerFactory

/**
 * Orchestrates pre-validation → transformation → post-validation.
 *
 * Validation is an engine-level concern — a pre/post step around the transformation.
 * It is NOT part of the UTL-X language. A .utlx script describes structural mapping;
 * validation policy belongs in deployment configuration.
 */
object ValidationOrchestrator {

    private val logger = LoggerFactory.getLogger(ValidationOrchestrator::class.java)

    /**
     * Execute a transformation with optional pre/post schema validation.
     *
     * @param instance The transformation instance (with cached validators and policy)
     * @param input The input payload string
     * @return ValidationResult with output, errors, and phase information
     */
    fun execute(instance: TransformationInstance, input: String): ValidationResult {
        val allWarnings = mutableListOf<SchemaValidationError>()

        // ── PRE-VALIDATION ──
        if (instance.inputValidator != null) {
            val inputErrors = instance.inputValidator.validate(
                input.toByteArray(Charsets.UTF_8),
                "application/octet-stream"
            )
            if (inputErrors.isNotEmpty()) {
                when (instance.config.validationPolicy.uppercase()) {
                    "STRICT" -> return ValidationResult(
                        success = false,
                        phase = ErrorPhase.PRE_VALIDATION,
                        validationErrors = inputErrors,
                        error = "Input validation failed: ${inputErrors.size} error(s)"
                    )
                    "WARN" -> {
                        logger.warn("Input validation warnings for '{}': {}", instance.name, inputErrors.size)
                        allWarnings.addAll(inputErrors)
                    }
                    // SKIP — unreachable (validator would be null), but handle gracefully
                }
            }
        }

        // ── TRANSFORMATION ──
        val output = try {
            instance.strategy.execute(input).output
        } catch (e: Exception) {
            return ValidationResult(
                success = false,
                phase = ErrorPhase.TRANSFORMATION,
                error = e.message ?: "Transformation failed"
            )
        }

        // ── POST-VALIDATION ──
        if (instance.outputValidator != null) {
            val outputErrors = instance.outputValidator.validate(
                output.toByteArray(Charsets.UTF_8),
                "application/octet-stream"
            )
            if (outputErrors.isNotEmpty()) {
                when (instance.config.validationPolicy.uppercase()) {
                    "STRICT" -> return ValidationResult(
                        success = false,
                        output = output,
                        phase = ErrorPhase.POST_VALIDATION,
                        validationErrors = outputErrors,
                        error = "Output validation failed: ${outputErrors.size} error(s)"
                    )
                    "WARN" -> {
                        logger.warn("Output validation warnings for '{}': {}", instance.name, outputErrors.size)
                        allWarnings.addAll(outputErrors)
                    }
                }
            }
        }

        return ValidationResult(
            success = true,
            output = output,
            validationErrors = allWarnings
        )
    }
}

data class ValidationResult(
    val success: Boolean,
    val output: String? = null,
    val phase: ErrorPhase = ErrorPhase.UNSPECIFIED,
    val validationErrors: List<SchemaValidationError> = emptyList(),
    val error: String? = null
)

enum class ErrorPhase {
    UNSPECIFIED,
    PRE_VALIDATION,
    TRANSFORMATION,
    POST_VALIDATION,
    INTERNAL
}
