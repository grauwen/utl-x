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
import kotlin.test.assertNotNull

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
            engine.start(org.apache.utlx.engine.transport.StdioJsonTransport())
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

    // =========================================================================
    // Multi-Transformation Tests
    // =========================================================================

    @Test
    fun `initialize loads all transformations from bundle`(@TempDir tempDir: Path) {
        createMultiTransformBundle(tempDir)
        val engine = UtlxEngine(testConfig())

        engine.initialize(tempDir)

        assertEquals(EngineState.READY, engine.state)
        assertEquals(3, engine.registry.size(), "Should load all 3 transformations")
        assertEquals(3, engine.registry.list().size)
    }

    @Test
    fun `all bundle transformations are accessible by name`(@TempDir tempDir: Path) {
        createMultiTransformBundle(tempDir)
        val engine = UtlxEngine(testConfig())

        engine.initialize(tempDir)

        assertNotNull(engine.registry.get("alpha"), "Should find 'alpha'")
        assertNotNull(engine.registry.get("beta"), "Should find 'beta'")
        assertNotNull(engine.registry.get("gamma"), "Should find 'gamma'")
    }

    // =========================================================================
    // initializeEmpty Tests
    // =========================================================================

    @Test
    fun `initializeEmpty transitions to READY with no transforms`() {
        val engine = UtlxEngine(testConfig())

        engine.initializeEmpty()

        assertEquals(EngineState.READY, engine.state)
        assertEquals(0, engine.registry.size(), "Registry should be empty")
    }

    @Test
    fun `cannot initializeEmpty twice`() {
        val engine = UtlxEngine(testConfig())

        engine.initializeEmpty()

        assertFailsWith<IllegalArgumentException> {
            engine.initializeEmpty()
        }
    }

    @Test
    fun `cannot initializeEmpty after initialize`(@TempDir tempDir: Path) {
        createMinimalBundle(tempDir)
        val engine = UtlxEngine(testConfig())

        engine.initialize(tempDir)

        assertFailsWith<IllegalArgumentException> {
            engine.initializeEmpty()
        }
    }

    // =========================================================================
    // EF07: Multi-transport tests
    // =========================================================================

    @Test
    fun `start with single transport in list`(@TempDir tempDir: Path) {
        createMinimalBundle(tempDir)
        val engine = UtlxEngine(testConfig())
        engine.initialize(tempDir)

        val transport = FakeTransport("fake-1")
        val thread = Thread {
            engine.start(listOf(transport))
        }.apply { isDaemon = true; start() }

        Thread.sleep(200)
        assertEquals(EngineState.RUNNING, engine.state)
        assertEquals(1, transport.startCount)

        engine.stop()
        thread.interrupt()
    }

    @Test
    fun `start with multiple transports starts all`(@TempDir tempDir: Path) {
        createMinimalBundle(tempDir)
        val engine = UtlxEngine(testConfig())
        engine.initialize(tempDir)

        val t1 = FakeTransport("bg-transport")
        val t2 = FakeTransport("main-transport")
        val thread = Thread {
            engine.start(listOf(t1, t2))
        }.apply { isDaemon = true; start() }

        Thread.sleep(500) // allow background threads to start
        assertEquals(EngineState.RUNNING, engine.state)
        assertEquals(1, t1.startCount, "Background transport should have started")
        assertEquals(1, t2.startCount, "Main transport should have started")

        engine.stop()
        assertEquals(1, t1.stopCount, "Background transport should be stopped")
        assertEquals(1, t2.stopCount, "Main transport should be stopped")
        thread.interrupt()
    }

    @Test
    fun `start with empty transport list fails`() {
        val engine = UtlxEngine(testConfig())
        engine.initializeEmpty()

        assertFailsWith<IllegalArgumentException> {
            engine.start(emptyList())
        }
    }

    @Test
    fun `stop drains all transports`(@TempDir tempDir: Path) {
        createMinimalBundle(tempDir)
        val engine = UtlxEngine(testConfig())
        engine.initialize(tempDir)

        val transports = listOf(FakeTransport("a"), FakeTransport("b"), FakeTransport("c"))
        val thread = Thread {
            engine.start(transports)
        }.apply { isDaemon = true; start() }

        Thread.sleep(500)
        engine.stop()
        transports.forEach { t ->
            assertEquals(1, t.stopCount, "${t.name} should be stopped exactly once")
        }
        thread.interrupt()
    }

    @Test
    fun `TransportServer name defaults to class simple name`() {
        val transport = FakeTransport("custom-name")
        // The interface default is javaClass.simpleName
        // But FakeTransport overrides name
        assertEquals("custom-name", transport.name)
    }

    /** Minimal transport for testing multi-transport engine start/stop. */
    private class FakeTransport(override val name: String) : org.apache.utlx.engine.transport.TransportServer {
        @Volatile var startCount = 0
        @Volatile var stopCount = 0
        override val supportsDynamicLoading = true
        private val latch = java.util.concurrent.CountDownLatch(1)

        override fun start(registry: org.apache.utlx.engine.registry.TransformationRegistry) {
            startCount++
            latch.await() // block until stopped
        }

        override fun stop() {
            stopCount++
            latch.countDown()
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
            "%utlx 1.0\ninput json\noutput json\n---\n\$input\n"
        )
    }

    private fun createMultiTransformBundle(tempDir: Path) {
        for (name in listOf("alpha", "beta", "gamma")) {
            val txDir = tempDir.resolve("transformations/$name")
            txDir.toFile().mkdirs()

            txDir.resolve("transform.yaml").toFile().writeText("""
                strategy: TEMPLATE
                inputs:
                  - name: input
                maxConcurrent: 1
            """.trimIndent())

            txDir.resolve("$name.utlx").toFile().writeText(
                "%utlx 1.0\ninput json\noutput json\n---\n{source: \"$name\"}\n"
            )
        }
    }
}
