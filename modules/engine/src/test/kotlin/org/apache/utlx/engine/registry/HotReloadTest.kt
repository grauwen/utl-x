package org.apache.utlx.engine.registry

import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.EngineConfig
import org.apache.utlx.engine.config.EngineSettings
import org.apache.utlx.engine.config.HealthConfig
import org.apache.utlx.engine.config.MonitoringConfig
import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.strategy.TemplateStrategy
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Hot Reload Tests — verify that transformations can be replaced at runtime
 * without restart, and that in-flight behavior is correct.
 */
class HotReloadTest {

    private fun testEngine(): UtlxEngine {
        val engine = UtlxEngine(EngineConfig(
            engine = EngineSettings(
                name = "test-hot-reload",
                monitoring = MonitoringConfig(health = HealthConfig(port = 0))
            )
        ))
        engine.initializeEmpty()
        return engine
    }

    @Test
    fun `reload replaces transformation`() {
        val engine = testEngine()
        val config = TransformConfig(strategy = "TEMPLATE")

        // Load v1
        val v1Source = "%utlx 1.0\ninput json\noutput json\n---\n{version: \"v1\", name: \$input.name}\n"
        val strategy1 = engine.createStrategy(config)
        strategy1.initialize(v1Source, config)
        val instance1 = TransformationInstance(
            name = "my-transform", source = v1Source, strategy = strategy1, config = config
        )
        engine.registry.register("my-transform", instance1)

        val result1 = engine.registry.get("my-transform")!!.strategy.execute("""{"name": "Alice"}""")
        assertTrue(result1.output.contains("v1"))

        // Load v2 (same ID, new source)
        val v2Source = "%utlx 1.0\ninput json\noutput json\n---\n{version: \"v2\", name: upperCase(\$input.name)}\n"
        val strategy2 = engine.createStrategy(config)
        strategy2.initialize(v2Source, config)
        val instance2 = TransformationInstance(
            name = "my-transform", source = v2Source, strategy = strategy2, config = config
        )
        engine.registry.register("my-transform", instance2)

        val result2 = engine.registry.get("my-transform")!!.strategy.execute("""{"name": "Alice"}""")
        assertTrue(result2.output.contains("v2"), "Should use v2: ${result2.output}")
        assertTrue(result2.output.contains("ALICE"), "v2 should uppercase: ${result2.output}")
    }

    @Test
    fun `reload preserves other transformations`() {
        val engine = testEngine()
        val config = TransformConfig(strategy = "TEMPLATE")

        // Load two transforms
        val sourceA = "%utlx 1.0\ninput json\noutput json\n---\n{from: \"A\"}\n"
        val stratA = engine.createStrategy(config)
        stratA.initialize(sourceA, config)
        engine.registry.register("transform-a", TransformationInstance(
            name = "transform-a", source = sourceA, strategy = stratA, config = config
        ))

        val sourceB = "%utlx 1.0\ninput json\noutput json\n---\n{from: \"B\"}\n"
        val stratB = engine.createStrategy(config)
        stratB.initialize(sourceB, config)
        engine.registry.register("transform-b", TransformationInstance(
            name = "transform-b", source = sourceB, strategy = stratB, config = config
        ))

        assertEquals(2, engine.registry.size())

        // Reload transform-a with new source
        val sourceA2 = "%utlx 1.0\ninput json\noutput json\n---\n{from: \"A-v2\"}\n"
        val stratA2 = engine.createStrategy(config)
        stratA2.initialize(sourceA2, config)
        engine.registry.register("transform-a", TransformationInstance(
            name = "transform-a", source = sourceA2, strategy = stratA2, config = config
        ))

        // transform-b should be unchanged
        assertEquals(2, engine.registry.size())
        val resultB = engine.registry.get("transform-b")!!.strategy.execute("{}")
        assertTrue(resultB.output.contains("\"B\""), "B should be unchanged: ${resultB.output}")

        val resultA = engine.registry.get("transform-a")!!.strategy.execute("{}")
        assertTrue(resultA.output.contains("A-v2"), "A should be v2: ${resultA.output}")
    }

    @Test
    fun `reload with different strategy`() {
        val engine = testEngine()

        // Load as TEMPLATE
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{name: \$input.name}\n"
        val configT = TransformConfig(strategy = "TEMPLATE")
        val stratT = engine.createStrategy(configT)
        stratT.initialize(source, configT)
        engine.registry.register("switch-strategy", TransformationInstance(
            name = "switch-strategy", source = source, strategy = stratT, config = configT
        ))

        assertEquals("TEMPLATE", engine.registry.get("switch-strategy")!!.strategy.name)

        // Reload as COMPILED
        val configC = TransformConfig(strategy = "COMPILED")
        val stratC = engine.createStrategy(configC)
        stratC.initialize(source, configC)
        engine.registry.register("switch-strategy", TransformationInstance(
            name = "switch-strategy", source = source, strategy = stratC, config = configC
        ))

        assertEquals("COMPILED", engine.registry.get("switch-strategy")!!.strategy.name)

        // Should still produce same output
        val result = engine.registry.get("switch-strategy")!!.strategy.execute("""{"name": "Alice"}""")
        assertTrue(result.output.contains("Alice"))
    }

    @Test
    fun `execution count resets on reload`() {
        val engine = testEngine()
        val config = TransformConfig(strategy = "TEMPLATE")

        val source = "%utlx 1.0\ninput json\noutput json\n---\n\$input\n"
        val strat = engine.createStrategy(config)
        strat.initialize(source, config)
        val instance = TransformationInstance(
            name = "counter-test", source = source, strategy = strat, config = config
        )
        engine.registry.register("counter-test", instance)

        // Execute 5 times
        repeat(5) { instance.recordExecution() }
        assertEquals(5, engine.registry.get("counter-test")!!.executionCount.get())

        // Reload
        val strat2 = engine.createStrategy(config)
        strat2.initialize(source, config)
        val instance2 = TransformationInstance(
            name = "counter-test", source = source, strategy = strat2, config = config
        )
        engine.registry.register("counter-test", instance2)

        // Counter should reset to 0
        assertEquals(0, engine.registry.get("counter-test")!!.executionCount.get())
    }
}
