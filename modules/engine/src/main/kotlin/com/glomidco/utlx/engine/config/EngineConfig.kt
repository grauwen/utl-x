package com.glomidco.utlx.engine.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.glomidco.utlx.core.format.ParseProfile
import com.glomidco.utlx.core.format.ParseProfilePolicy
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

    // ── F23: parse strictness profile (parser-strictness-profiles.md §9/§9.1) ──

    /** The configured parse profile. **Throws on an invalid value** — a typo must fail the engine, never
     *  silently fall back to a weaker profile (that would be fail-open; cf. §7). */
    fun parseProfile(): ParseProfile = when (engine.parse.profile.lowercase()) {
        "strict" -> ParseProfile.STRICT
        "standard" -> ParseProfile.STANDARD
        "lenient" -> ParseProfile.LENIENT
        else -> throw IllegalArgumentException(
            "Invalid engine config parse.profile '${engine.parse.profile}' (expected: strict | standard | lenient)")
    }

    /** The lock floor: the configured profile when `locked` (a guard pins + forbids weakening), else null. */
    fun parseLock(): ParseProfile? = if (engine.parse.locked) parseProfile() else null

    /** Effective profile given an optional lower-precedence [requested] (bundle/CLI/request), per §9.1
     *  "strictest wins, never weaken a lock." With no request, this is the configured profile. */
    fun effectiveParseProfile(requested: ParseProfile? = null, rejectBelowLock: Boolean = false): ParseProfile =
        ParseProfilePolicy.resolve(parseLock(), requested ?: parseProfile(), rejectBelowLock)

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
    val defaultStrategy: String = "TEMPLATE",
    val parse: ParseConfig = ParseConfig()
)

/**
 * F23: parser strictness policy for this deployment. `profile` is the selected profile
 * (`strict | standard | lenient`); `locked` pins it as a floor that no bundle/request may weaken
 * (a content guard sets `profile: strict, locked: true`). Default is the pragmatic `standard`, unlocked.
 */
data class ParseConfig(
    val profile: String = "standard",
    val locked: Boolean = false
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
