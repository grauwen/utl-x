package org.apache.utlx.engine.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Path

data class EngineConfig(
    val engine: EngineSettings = EngineSettings()
) {
    fun withHealthPort(port: Int): EngineConfig = copy(
        engine = engine.copy(
            monitoring = engine.monitoring.copy(
                health = engine.monitoring.health.copy(port = port)
            )
        )
    )

    companion object {
        private val yamlMapper = ObjectMapper(YAMLFactory()).apply {
            registerModule(kotlinModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            propertyNamingStrategy = PropertyNamingStrategies.LOWER_CAMEL_CASE
        }

        fun load(path: Path): EngineConfig {
            return yamlMapper.readValue(path.toFile())
        }

        fun default(): EngineConfig = EngineConfig()
    }
}

data class EngineSettings(
    val name: String = "utlxe",
    val threads: ThreadsConfig = ThreadsConfig(),
    val memory: MemoryConfig = MemoryConfig(),
    val monitoring: MonitoringConfig = MonitoringConfig(),
    val defaultStrategy: String = "TEMPLATE"
)

data class ThreadsConfig(
    val sharedPoolSize: Int = 0  // 0 = CPU cores
)

data class MemoryConfig(
    val maxHeap: String = "2g"
)

data class MonitoringConfig(
    val health: HealthConfig = HealthConfig(),
    val metrics: MetricsConfig = MetricsConfig()
)

data class HealthConfig(
    val port: Int = 8081,
    val path: String = "/health"
)

data class MetricsConfig(
    val enabled: Boolean = false,
    val prometheus: PrometheusConfig = PrometheusConfig()
)

data class PrometheusConfig(
    val port: Int = 9090,
    val path: String = "/metrics"
)

data class PipeConfig(
    val inputs: List<InputPipeConfig> = emptyList(),
    val output: OutputPipeConfig = OutputPipeConfig()
)

data class InputPipeConfig(
    val name: String = "",
    val transport: String = "stdin"
)

data class OutputPipeConfig(
    val transport: String = "stdout"
)
