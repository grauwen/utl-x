package org.apache.utlx.engine

import org.apache.utlx.engine.config.EngineConfig
import org.apache.utlx.engine.config.EngineSettings
import org.apache.utlx.engine.config.MonitoringConfig
import org.apache.utlx.engine.config.HealthConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UtlxEngineTest {

    private fun testConfig() = EngineConfig(
        engine = EngineSettings(
            name = "test-engine",
            monitoring = MonitoringConfig(
                health = HealthConfig(port = 0)
            )
        )
    )

    @Test
    fun `engine starts in CREATED state`() {
        val engine = UtlxEngine(testConfig())
        assertEquals(EngineState.CREATED, engine.state)
    }

    @Test
    fun `initialize transitions to READY`(@TempDir tempDir: Path) {
        createMinimalBundle(tempDir)
        val engine = UtlxEngine(testConfig())

        engine.initialize(tempDir)

        assertEquals(EngineState.READY, engine.state)
        assertEquals(1, engine.registry.list().size)
    }

    @Test
    fun `initialize fails with empty bundle`(@TempDir tempDir: Path) {
        tempDir.resolve("transformations").toFile().mkdirs()
        val engine = UtlxEngine(testConfig())

        assertFailsWith<IllegalStateException> {
            engine.initialize(tempDir)
        }
        assertEquals(EngineState.STOPPED, engine.state)
    }

    @Test
    fun `cannot initialize twice`(@TempDir tempDir: Path) {
        createMinimalBundle(tempDir)
        val engine = UtlxEngine(testConfig())

        engine.initialize(tempDir)

        assertFailsWith<IllegalArgumentException> {
            engine.initialize(tempDir)
        }
    }

    @Test
    fun `cannot start without initialize`() {
        val engine = UtlxEngine(testConfig())

        assertFailsWith<IllegalArgumentException> {
            engine.start()
        }
    }

    @Test
    fun `stop from READY transitions to STOPPED`(@TempDir tempDir: Path) {
        createMinimalBundle(tempDir)
        val engine = UtlxEngine(testConfig())

        engine.initialize(tempDir)
        engine.stop()

        assertEquals(EngineState.STOPPED, engine.state)
    }

    @Test
    fun `stop is idempotent`() {
        val engine = UtlxEngine(testConfig())
        engine.stop()
        engine.stop()
        assertEquals(EngineState.STOPPED, engine.state)
    }

    private fun createMinimalBundle(tempDir: Path) {
        val txDir = tempDir.resolve("transformations/identity")
        txDir.toFile().mkdirs()

        txDir.resolve("transform.yaml").toFile().writeText("""
            strategy: TEMPLATE
            inputs:
              - name: input
            maxConcurrent: 1
        """.trimIndent())

        txDir.resolve("identity.utlx").toFile().writeText(
            "%utlx 1.0\ninput json\noutput json\n---\ninput\n"
        )
    }
}
