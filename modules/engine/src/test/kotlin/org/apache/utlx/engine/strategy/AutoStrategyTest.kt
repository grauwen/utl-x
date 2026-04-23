package org.apache.utlx.engine.strategy

import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AUTO strategy selection tests — verifies AUTO picks the right strategy
 * based on schema availability.
 */
class AutoStrategyTest {

    private fun testEngine(): UtlxEngine {
        val engine = UtlxEngine(EngineConfig(
            engine = EngineSettings(
                name = "test-auto",
                monitoring = MonitoringConfig(health = HealthConfig(port = 0))
            )
        ))
        engine.initializeEmpty()
        return engine
    }

    @Test
    fun `AUTO without schema selects TEMPLATE`() {
        val engine = testEngine()
        val config = TransformConfig(strategy = "AUTO")
        val strategy = engine.createStrategy(config)
        assertEquals("TEMPLATE", strategy.name)
    }

    @Test
    fun `AUTO with schema selects COPY`() {
        val engine = testEngine()
        val config = TransformConfig(
            strategy = "AUTO",
            inputs = listOf(InputSlot(name = "input", schema = """{"type": "object"}"""))
        )
        val strategy = engine.createStrategy(config)
        assertEquals("COPY", strategy.name)
    }

    @Test
    fun `INTERPRETED alias works`() {
        val engine = testEngine()
        val config = TransformConfig(strategy = "INTERPRETED")
        val strategy = engine.createStrategy(config)
        assertEquals("TEMPLATE", strategy.name)
    }

    @Test
    fun `AUTO strategy produces correct output`() {
        val engine = testEngine()
        val config = TransformConfig(strategy = "AUTO")
        val strategy = engine.createStrategy(config)

        val source = "%utlx 1.0\ninput json\noutput json\n---\n{name: upperCase(\$input.name)}\n"
        strategy.initialize(source, config)

        val result = strategy.execute("""{"name": "alice"}""")
        assertTrue(result.output.contains("ALICE"))
    }
}
