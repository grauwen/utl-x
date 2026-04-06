package org.apache.utlx.engine.strategy

import org.apache.utlx.engine.config.TransformConfig

interface ExecutionStrategy {
    fun initialize(source: String, config: TransformConfig)
    fun execute(input: String): ExecutionResult
    fun executeBatch(inputs: List<String>): List<ExecutionResult> = inputs.map { execute(it) }
    fun shutdown()
    val name: String
}

data class ExecutionResult(
    val output: String,
    val validationErrors: List<ValidationError> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

data class ValidationError(
    val message: String,
    val path: String? = null,
    val severity: String = "ERROR"
)
