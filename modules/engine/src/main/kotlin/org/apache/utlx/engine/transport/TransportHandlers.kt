package org.apache.utlx.engine.transport

import com.google.protobuf.ByteString
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.proto.*
import org.apache.utlx.engine.registry.TransformationInstance
import org.apache.utlx.engine.registry.TransformationRegistry
import org.apache.utlx.engine.validation.SchemaValidatorFactory
import org.apache.utlx.engine.validation.ValidationOrchestrator
import org.apache.utlx.engine.validation.ErrorPhase as VErrorPhase
import org.slf4j.LoggerFactory

/**
 * Shared request handler logic used by all transports (HTTP, gRPC, stdio-proto).
 * Ensures identical behavior (including validation) across transport modes.
 *
 * All handlers take `engine: UtlxEngine` as the single context parameter.
 * Registry, validation overrides, and other state are accessed via the engine.
 */
object TransportHandlers {

    private val logger = LoggerFactory.getLogger(TransportHandlers::class.java)

    fun handleLoadTransformation(
        req: LoadTransformationRequest,
        engine: UtlxEngine
    ): LoadTransformationResponse {
        val registry = engine.registry
        val startTime = System.nanoTime()

        try {
            val id = req.transformationId
            val source = req.utlxSource
            val strategyName = req.strategy.ifEmpty { "TEMPLATE" }
            val validationPolicy = req.validationPolicy.ifEmpty { "SKIP" }
            val maxConcurrent = if (req.maxConcurrent > 0) req.maxConcurrent else 1

            logger.info("Loading transformation '{}' [strategy={}, validation={}]", id, strategyName, validationPolicy)

            val config = TransformConfig(
                strategy = strategyName,
                validationPolicy = validationPolicy,
                maxConcurrent = maxConcurrent
            )

            val strategy = engine.createStrategy(config)
            val parseStart = System.nanoTime()
            strategy.initialize(source, config)
            val parseEnd = System.nanoTime()

            // Compile schema validators from config map (if provided)
            val configMap = req.configMap
            val inputValidator = if (configMap["validate_input"] == "true") {
                val schemaSource = configMap["input_schema"]
                val schemaFormat = configMap["input_schema_format"]
                if (schemaSource != null && schemaFormat != null) {
                    SchemaValidatorFactory.create(schemaSource, schemaFormat)
                } else {
                    logger.warn("validate_input=true but input_schema or input_schema_format missing for '{}'", id)
                    null
                }
            } else null

            val outputValidator = if (configMap["validate_output"] == "true") {
                val schemaSource = configMap["output_schema"]
                val schemaFormat = configMap["output_schema_format"]
                if (schemaSource != null && schemaFormat != null) {
                    SchemaValidatorFactory.create(schemaSource, schemaFormat)
                } else {
                    logger.warn("validate_output=true but output_schema or output_schema_format missing for '{}'", id)
                    null
                }
            } else null

            val instance = TransformationInstance(
                name = id,
                source = source,
                strategy = strategy,
                config = config,
                inputValidator = inputValidator,
                outputValidator = outputValidator
            )
            registry.register(id, instance)

            val totalDuration = (System.nanoTime() - startTime) / 1000

            logger.info("Transformation '{}' loaded in {}μs (inputValidator={}, outputValidator={})",
                id, totalDuration, inputValidator != null, outputValidator != null)

            return LoadTransformationResponse.newBuilder()
                .setSuccess(true)
                .setMetrics(
                    LoadMetrics.newBuilder()
                        .setParseDurationUs((parseEnd - parseStart) / 1000)
                        .setTotalDurationUs(totalDuration)
                        .build()
                )
                .build()

        } catch (e: Exception) {
            logger.error("Failed to load transformation '{}': {}", req.transformationId, e.message, e)
            return LoadTransformationResponse.newBuilder()
                .setSuccess(false)
                .setError(e.message ?: "Unknown error")
                .build()
        }
    }

    fun handleExecute(
        req: ExecuteRequest,
        engine: UtlxEngine
    ): ExecuteResponse {
        val registry = engine.registry
        val startTime = System.nanoTime()

        val instance = registry.get(req.transformationId)
            ?: return ExecuteResponse.newBuilder()
                .setSuccess(false)
                .setError("Transformation not found: ${req.transformationId}")
                .setErrorClass(ErrorClass.PERMANENT)
                .setErrorPhase(ErrorPhase.INTERNAL)
                .setCorrelationId(req.correlationId)
                .build()

        instance.recordExecution()
        val input = req.payload.toStringUtf8()

        // EF02: Resolve effective validation policy (runtime override > config > default)
        val policyOverride = engine.validationOverrides.get(req.transformationId)?.policy

        val result = ValidationOrchestrator.execute(instance, input, policyOverride)
        val durationUs = (System.nanoTime() - startTime) / 1000

        if (!result.success) {
            instance.recordError()
        }

        val builder = ExecuteResponse.newBuilder()
            .setSuccess(result.success)
            .setCorrelationId(req.correlationId)
            .setMetrics(
                ExecuteMetrics.newBuilder()
                    .setExecuteDurationUs(durationUs)
                    .build()
            )

        // Include output when available — even on POST_VALIDATION failure (for debugging)
        if (result.output != null) {
            builder.setOutput(ByteString.copyFromUtf8(result.output))
        }

        if (result.error != null) {
            builder.setError(result.error)
            builder.setErrorClass(ErrorClass.PERMANENT)
            builder.setErrorPhase(mapErrorPhase(result.phase))
        }

        result.validationErrors.forEach { err ->
            builder.addValidationErrors(
                ValidationError.newBuilder()
                    .setMessage(err.message)
                    .setPath(err.path ?: "")
                    .setSeverity(err.severity)
                    .build()
            )
        }

        return builder.build()
    }

    fun handleExecuteBatch(
        req: ExecuteBatchRequest,
        engine: UtlxEngine
    ): ExecuteBatchResponse {
        val registry = engine.registry
        val instance = registry.get(req.transformationId)
        val builder = ExecuteBatchResponse.newBuilder()

        if (instance == null) {
            req.itemsList.forEach { item ->
                builder.addResults(
                    ExecuteResponse.newBuilder()
                        .setSuccess(false)
                        .setError("Transformation not found: ${req.transformationId}")
                        .setErrorClass(ErrorClass.PERMANENT)
                        .setErrorPhase(ErrorPhase.INTERNAL)
                        .setCorrelationId(item.correlationId)
                        .build()
                )
            }
            return builder.build()
        }

        val batchOverride = engine.validationOverrides.get(req.transformationId)?.policy

        req.itemsList.forEach { item ->
            val startTime = System.nanoTime()
            instance.recordExecution()
            val input = item.payload.toStringUtf8()

            val result = ValidationOrchestrator.execute(instance, input, batchOverride)
            val durationUs = (System.nanoTime() - startTime) / 1000

            if (!result.success) {
                instance.recordError()
            }

            val respBuilder = ExecuteResponse.newBuilder()
                .setSuccess(result.success)
                .setCorrelationId(item.correlationId)
                .setMetrics(
                    ExecuteMetrics.newBuilder()
                        .setExecuteDurationUs(durationUs)
                        .build()
                )

            if (result.success && result.output != null) {
                respBuilder.setOutput(ByteString.copyFromUtf8(result.output))
            }
            if (result.error != null) {
                respBuilder.setError(result.error)
                respBuilder.setErrorClass(ErrorClass.PERMANENT)
                respBuilder.setErrorPhase(mapErrorPhase(result.phase))
            }
            result.validationErrors.forEach { err ->
                respBuilder.addValidationErrors(
                    ValidationError.newBuilder()
                        .setMessage(err.message)
                        .setPath(err.path ?: "")
                        .setSeverity(err.severity)
                        .build()
                )
            }

            builder.addResults(respBuilder.build())
        }

        return builder.build()
    }

    fun handleExecutePipeline(
        req: ExecutePipelineRequest,
        engine: UtlxEngine
    ): ExecutePipelineResponse {
        val registry = engine.registry
        val startTime = System.nanoTime()
        val transformIds = req.transformationIdsList
        var currentPayload = req.payload.toStringUtf8()
        var stagesCompleted = 0
        val allWarnings = mutableListOf<ValidationError>()

        if (transformIds.isEmpty()) {
            return ExecutePipelineResponse.newBuilder()
                .setSuccess(false)
                .setError("Pipeline requires at least one transformation_id")
                .setErrorClass(ErrorClass.PERMANENT)
                .setErrorPhase(ErrorPhase.INTERNAL)
                .setCorrelationId(req.correlationId)
                .build()
        }

        for (transformId in transformIds) {
            val instance = registry.get(transformId)
                ?: return ExecutePipelineResponse.newBuilder()
                    .setSuccess(false)
                    .setError("Transformation not found: $transformId (stage ${stagesCompleted + 1})")
                    .setErrorClass(ErrorClass.PERMANENT)
                    .setErrorPhase(ErrorPhase.INTERNAL)
                    .setCorrelationId(req.correlationId)
                    .setStagesCompleted(stagesCompleted)
                    .build()

            instance.recordExecution()

            // EF01: If this stage has additional inputs, construct a multi-input envelope
            val stageInputs = req.stageInputsMap[transformId]
            val effectivePayload = if (stageInputs != null && stageInputs.additionalInputsCount > 0) {
                val mapper = com.fasterxml.jackson.databind.ObjectMapper()
                val envelope = mapper.createObjectNode()
                envelope.set<com.fasterxml.jackson.databind.JsonNode>(
                    "input", mapper.readTree(currentPayload)
                )
                stageInputs.additionalInputsMap.forEach { (name, bytes) ->
                    val content = bytes.toStringUtf8()
                    try {
                        envelope.set<com.fasterxml.jackson.databind.JsonNode>(name, mapper.readTree(content))
                    } catch (_: Exception) {
                        envelope.put(name, content)
                    }
                }
                logger.debug("Pipeline stage '{}' with additional inputs: {}", transformId,
                    stageInputs.additionalInputsMap.keys)
                envelope.toString()
            } else {
                currentPayload
            }

            // EF02: Resolve effective validation policy per stage
            val stageOverride = engine.validationOverrides.get(transformId)?.policy
            val result = ValidationOrchestrator.execute(instance, effectivePayload, stageOverride)

            if (!result.success) {
                instance.recordError()
                val durationUs = (System.nanoTime() - startTime) / 1000
                val builder = ExecutePipelineResponse.newBuilder()
                    .setSuccess(false)
                    .setError("Pipeline failed at stage ${stagesCompleted + 1} ($transformId): ${result.error}")
                    .setErrorClass(ErrorClass.PERMANENT)
                    .setErrorPhase(mapErrorPhase(result.phase))
                    .setCorrelationId(req.correlationId)
                    .setStagesCompleted(stagesCompleted)
                    .setTotalDurationUs(durationUs)
                result.validationErrors.forEach { err ->
                    builder.addValidationErrors(
                        ValidationError.newBuilder()
                            .setMessage(err.message)
                            .setPath(err.path ?: "")
                            .setSeverity(err.severity)
                            .build()
                    )
                }
                return builder.build()
            }

            result.validationErrors.forEach { err ->
                allWarnings.add(
                    ValidationError.newBuilder()
                        .setMessage(err.message)
                        .setPath(err.path ?: "")
                        .setSeverity(err.severity)
                        .build()
                )
            }

            currentPayload = result.output ?: ""
            stagesCompleted++
        }

        val durationUs = (System.nanoTime() - startTime) / 1000

        val builder = ExecutePipelineResponse.newBuilder()
            .setSuccess(true)
            .setOutput(ByteString.copyFromUtf8(currentPayload))
            .setCorrelationId(req.correlationId)
            .setStagesCompleted(stagesCompleted)
            .setTotalDurationUs(durationUs)

        allWarnings.forEach { builder.addValidationErrors(it) }

        return builder.build()
    }

    fun handleUnload(
        req: UnloadTransformationRequest,
        engine: UtlxEngine
    ): UnloadTransformationResponse {
        val success = engine.registry.unload(req.transformationId)
        if (success) {
            logger.info("Unloaded transformation '{}'", req.transformationId)
        } else {
            logger.warn("Transformation '{}' not found for unload", req.transformationId)
        }
        return UnloadTransformationResponse.newBuilder()
            .setSuccess(success)
            .build()
    }

    fun handleHealth(
        engine: UtlxEngine
    ): HealthResponse {
        val registry = engine.registry
        val totalExecutions = registry.list().sumOf { it.executionCount.get() }
        val totalErrors = registry.list().sumOf { it.errorCount.get() }

        return HealthResponse.newBuilder()
            .setState(engine.state.name)
            .setUptimeMs(engine.uptimeMs())
            .setLoadedTransformations(registry.size())
            .setTotalExecutions(totalExecutions)
            .setTotalErrors(totalErrors)
            .build()
    }

    private fun mapErrorPhase(phase: VErrorPhase): ErrorPhase {
        return when (phase) {
            VErrorPhase.PRE_VALIDATION -> ErrorPhase.PRE_VALIDATION
            VErrorPhase.TRANSFORMATION -> ErrorPhase.TRANSFORMATION
            VErrorPhase.POST_VALIDATION -> ErrorPhase.POST_VALIDATION
            VErrorPhase.INTERNAL -> ErrorPhase.INTERNAL
            VErrorPhase.UNSPECIFIED -> ErrorPhase.ERROR_PHASE_UNSPECIFIED
        }
    }
}
