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
    fun execute(
        instance: TransformationInstance,
        input: String,
        policyOverride: String? = null
    ): ValidationResult {
        val allWarnings = mutableListOf<SchemaValidationError>()
        val effectivePolicy = (policyOverride ?: instance.config.validationPolicy).uppercase()

        // ── PRE-VALIDATION ──
        // Validate ALL inputs first, collect ALL errors, then decide based on policy.
        // This ensures the customer sees every validation error in one report,
        // not just the first one (especially important for multi-input).
        //
        // NOTE: The input is a Java String at this point — re-encoded to UTF-8 bytes
        // for the validator. This is correct for JSON (always UTF-8) and most modern XML.
        // For legacy XML with non-UTF-8 encodings (UTF-16, ISO-8859-1), the re-encoding
        // may alter the bytes. A future improvement would pass the original raw bytes
        // through the pipeline instead of converting to String and back.
        if (effectivePolicy != "OFF" && effectivePolicy != "SKIP") {
            val preErrors = mutableListOf<SchemaValidationError>()

            // Single-input validation
            if (instance.inputValidator != null) {
                val errors = instance.inputValidator.validate(
                    input.toByteArray(Charsets.UTF_8),
                    "application/octet-stream"
                )
                preErrors.addAll(errors)
            }

            // Multi-input validation — validate each named input in the envelope
            if (instance.inputValidators.isNotEmpty()) {
                try {
                    val mapper = com.fasterxml.jackson.databind.ObjectMapper()
                    val envelope = mapper.readTree(input)
                    for ((inputName, validator) in instance.inputValidators) {
                        val node = envelope.get(inputName) ?: continue
                        val inputBytes = node.toString().toByteArray(Charsets.UTF_8)
                        val errors = validator.validate(inputBytes, "application/octet-stream")
                        if (errors.isNotEmpty()) {
                            preErrors.addAll(errors.map { err ->
                                SchemaValidationError("$inputName: ${err.message}", err.path, err.severity)
                            })
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("Multi-input validation skipped (not a JSON envelope): {}", e.message)
                }
            }

            // Apply policy to collected errors
            if (preErrors.isNotEmpty()) {
                when (effectivePolicy) {
                    "STRICT" -> {
                        val failedInputs = preErrors.map { it.message.substringBefore(":").trim() }.distinct()
                        return ValidationResult(
                            success = false,
                            phase = ErrorPhase.PRE_VALIDATION,
                            validationErrors = preErrors,
                            error = "Input validation failed: ${preErrors.size} error(s) in ${failedInputs.size} input(s)"
                        )
                    }
                    "WARN" -> {
                        logger.warn("Input validation warnings for '{}': {} error(s)", instance.name, preErrors.size)
                        allWarnings.addAll(preErrors)
                    }
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
                validationErrors = allWarnings, // include any warnings collected so far
                error = e.message ?: "${e::class.simpleName}: ${e.stackTrace.firstOrNull()}"
            )
        }

        // ── POST-VALIDATION ──
        if (instance.outputValidator != null && effectivePolicy != "OFF" && effectivePolicy != "SKIP") {
            val outputErrors = instance.outputValidator.validate(
                output.toByteArray(Charsets.UTF_8),
                "application/octet-stream"
            )
            if (outputErrors.isNotEmpty()) {
                when (effectivePolicy) {
                    "STRICT" -> return ValidationResult(
                        success = false,
                        output = output,   // include the output — customer may want to inspect it
                        phase = ErrorPhase.POST_VALIDATION,
                        validationErrors = allWarnings + outputErrors, // pre-warnings + post-errors
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
