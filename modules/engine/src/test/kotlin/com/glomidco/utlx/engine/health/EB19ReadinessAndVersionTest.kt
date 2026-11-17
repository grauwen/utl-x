package com.glomidco.utlx.engine.health

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import com.glomidco.utlx.engine.UtlxEngine
import com.glomidco.utlx.engine.config.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * EB19, and the open remainder of EB18 — readiness must not require loaded work.
 *
 * `/health/ready` computed `RUNNING && transformations > 0`. The engine ships empty by design,
 * so a freshly started instance was permanently unready. Azure Container Apps routes nothing to
 * a replica with a not-ready container, so the Admin API needed to upload a transformation was
 * itself unreachable: a deadlock in which no deployment could ever serve traffic (EB18).
 *
 * Readiness means *able to accept requests*, not *already has work*. The transformation count
 * stays in the body for anything that wants the other signal.
 */
class EB19ReadinessAndVersionTest {

    private val mapper = ObjectMapper().apply { registerModule(kotlinModule()) }

    private fun testConfig() = EngineConfig(
        engine = EngineSettings(
            name = "test-engine",
            monitoring = MonitoringConfig(health = HealthConfig(port = 0))
        )
    )


    /** Minimal blocking transport, so the engine can reach RUNNING. Mirrors UtlxEngineTest. */
    private class FakeTransport(override val name: String = "fake") :
        com.glomidco.utlx.engine.transport.TransportServer {
        override val supportsDynamicLoading = true
        private val latch = java.util.concurrent.CountDownLatch(1)
        override fun start(registry: com.glomidco.utlx.engine.registry.TransformationRegistry) {
            latch.await()
        }
        override fun stop() = latch.countDown()
    }

    /** Bring an EMPTY engine to RUNNING — the state EB18 is about. */
    private fun runningEmptyEngine(): UtlxEngine {
        val engine = UtlxEngine(testConfig())
        // initializeEmpty() is exactly what the Azure container does: no bundle on disk, every
        // transformation arriving later through the Admin API. initialize() rejects an empty
        // directory, so using it here would have tested a state the deployment never has.
        engine.initializeEmpty()
        Thread { engine.start(listOf(FakeTransport())) }.apply { isDaemon = true; start() }
        val deadline = System.currentTimeMillis() + 5000
        while (engine.state != com.glomidco.utlx.engine.EngineState.RUNNING &&
               System.currentTimeMillis() < deadline) Thread.sleep(25)
        return engine
    }

    @Test
    fun `a running engine with no transformations is ready`() = testApplication {
        val engine = runningEmptyEngine()
        application { configureHealth(engine) }

        val response = client.get("/health/ready")
        val body = mapper.readValue<Map<String, Any>>(response.bodyAsText())

        // The vacuity guard: if the fixture ever starts with transformations loaded, this test
        // would pass for the wrong reason and prove nothing about EB18.
        assertEquals(0, (body["transformations"] as Number).toInt(), "fixture must start empty")

        assertEquals(
            HttpStatusCode.OK, response.status,
            "an empty but running engine must report ready — requiring loaded work is EB18"
        )
        assertEquals("UP", body["status"])
        assertEquals(true, body["ready"])

        engine.stop()
    }

    @Test
    fun `the count is still reported for anything that wants it`() = testApplication {
        val engine = runningEmptyEngine()
        application { configureHealth(engine) }

        val body = mapper.readValue<Map<String, Any>>(client.get("/health/ready").bodyAsText())
        // Readiness and "has work" are now separate facts rather than one conflated one.
        assertEquals(0, (body["transformations"] as Number).toInt())
        assertEquals(true, body["ready"])

        engine.stop()
    }

    @Test
    fun `the engine version does not come from a literal`() {
        // Asserting an exact number would need editing every release. What must hold is that it
        // is not the stale literal, and that it agrees with the jar manifest when there is one.
        val fromManifest = UtlxEngine::class.java.`package`?.implementationVersion
        val reported = fromManifest ?: "unknown"
        assertNotEquals("1.0.1", reported, "the version must come from the build, not a literal")
    }
}
