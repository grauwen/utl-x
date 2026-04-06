package org.apache.utlx.engine.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EngineConfigTest {

    @Test
    fun `default config has sensible defaults`() {
        val config = EngineConfig.default()
        assertEquals("utlxe", config.engine.name)
        assertEquals(0, config.engine.threads.sharedPoolSize)
        assertEquals("2g", config.engine.memory.maxHeap)
        assertEquals(8081, config.engine.monitoring.health.port)
        assertEquals("/health", config.engine.monitoring.health.path)
        assertEquals(false, config.engine.monitoring.metrics.enabled)
        assertEquals("TEMPLATE", config.engine.defaultStrategy)
    }

    @Test
    fun `load config from YAML file`(@TempDir tempDir: Path) {
        val configFile = tempDir.resolve("engine.yaml")
        configFile.toFile().writeText("""
            engine:
              name: test-engine
              threads:
                sharedPoolSize: 8
              memory:
                maxHeap: 4g
              monitoring:
                health:
                  port: 9999
                metrics:
                  enabled: true
              defaultStrategy: AUTO
        """.trimIndent())

        val config = EngineConfig.load(configFile)
        assertEquals("test-engine", config.engine.name)
        assertEquals(8, config.engine.threads.sharedPoolSize)
        assertEquals("4g", config.engine.memory.maxHeap)
        assertEquals(9999, config.engine.monitoring.health.port)
        assertEquals(true, config.engine.monitoring.metrics.enabled)
        assertEquals("AUTO", config.engine.defaultStrategy)
    }

    @Test
    fun `load config with partial YAML uses defaults for missing fields`(@TempDir tempDir: Path) {
        val configFile = tempDir.resolve("engine.yaml")
        configFile.toFile().writeText("""
            engine:
              name: partial-engine
        """.trimIndent())

        val config = EngineConfig.load(configFile)
        assertEquals("partial-engine", config.engine.name)
        assertEquals(0, config.engine.threads.sharedPoolSize)
        assertEquals("TEMPLATE", config.engine.defaultStrategy)
    }

    @Test
    fun `TransformConfig loads from YAML`(@TempDir tempDir: Path) {
        val configFile = tempDir.resolve("transform.yaml")
        configFile.toFile().writeText("""
            strategy: COPY
            validationPolicy: WARN
            inputs:
              - name: order
                schema: schemas/order-v1.xsd
              - name: inventory
                schema: schemas/inventory-v1.json
            output:
              schema: schemas/invoice-v2.json
            maxConcurrent: 4
        """.trimIndent())

        val config = TransformConfig.load(configFile)
        assertEquals("COPY", config.strategy)
        assertEquals("WARN", config.validationPolicy)
        assertEquals(2, config.inputs.size)
        assertEquals("order", config.inputs[0].name)
        assertEquals("schemas/order-v1.xsd", config.inputs[0].schema)
        assertEquals("inventory", config.inputs[1].name)
        assertNotNull(config.output.schema)
        assertEquals(4, config.maxConcurrent)
    }

    @Test
    fun `TransformConfig defaults`() {
        val config = TransformConfig()
        assertEquals("TEMPLATE", config.strategy)
        assertEquals("SKIP", config.validationPolicy)
        assertEquals(0, config.inputs.size)
        assertEquals(1, config.maxConcurrent)
    }

    @Test
    fun `withHealthPort overrides only the health port`() {
        val config = EngineConfig.default()
        assertEquals(8081, config.engine.monitoring.health.port)

        val overridden = config.withHealthPort(9999)
        assertEquals(9999, overridden.engine.monitoring.health.port)
        // Everything else unchanged
        assertEquals(config.engine.name, overridden.engine.name)
        assertEquals(config.engine.defaultStrategy, overridden.engine.defaultStrategy)
        assertEquals(config.engine.monitoring.metrics.enabled, overridden.engine.monitoring.metrics.enabled)
    }
}
