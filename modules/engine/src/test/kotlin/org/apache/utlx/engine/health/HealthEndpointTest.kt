package org.apache.utlx.engine.health

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.BindException
import java.net.ServerSocket
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HealthEndpointTest {

    private val mapper = ObjectMapper().apply { registerModule(kotlinModule()) }

    private fun testConfig(port: Int = 0) = EngineConfig(
        engine = EngineSettings(
            name = "test-engine",
            monitoring = MonitoringConfig(health = HealthConfig(port = port))
        )
    )

    private fun createMinimalBundle(tempDir: Path) {
        val txDir = tempDir.resolve("transformations/identity")
        txDir.toFile().mkdirs()
        txDir.resolve("transform.yaml").toFile().writeText(
            "strategy: TEMPLATE\ninputs:\n  - name: input\nmaxConcurrent: 1"
        )
        txDir.resolve("identity.utlx").toFile().writeText(
            "%utlx 1.0\ninput json\noutput json\n---\ninput\n"
        )
    }

    // --- Routing tests (via Ktor testApplication) ---

    @Test
    fun `liveness probe always returns UP`() = testApplication {
        val engine = UtlxEngine(testConfig())
        application { configureHealth(engine) }

        val response = client.get("/health/live")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = mapper.readValue<Map<String, Any>>(response.bodyAsText())
        assertEquals("UP", body["status"])
    }

    @Test
    fun `readiness probe returns DOWN when engine is not RUNNING`() = testApplication {
        val engine = UtlxEngine(testConfig())
        application { configureHealth(engine) }

        val response = client.get("/health/ready")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val body = mapper.readValue<Map<String, Any>>(response.bodyAsText())
        assertEquals("DOWN", body["status"])
        assertEquals("CREATED", body["state"])
    }

    @Test
    fun `readiness probe returns DOWN in READY state`(@TempDir tempDir: Path) = testApplication {
        createMinimalBundle(tempDir)
        val engine = UtlxEngine(testConfig())
        engine.initialize(tempDir)
        application { configureHealth(engine) }

        val response = client.get("/health/ready")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val body = mapper.readValue<Map<String, Any>>(response.bodyAsText())
        assertEquals("DOWN", body["status"])
        assertEquals("READY", body["state"])
    }

    @Test
    fun `health endpoint returns engine state and transformation count`(@TempDir tempDir: Path) = testApplication {
        createMinimalBundle(tempDir)
        val engine = UtlxEngine(testConfig())
        engine.initialize(tempDir)
        application { configureHealth(engine) }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val body = mapper.readValue<Map<String, Any>>(response.bodyAsText())
        assertEquals("DOWN", body["status"])

        @Suppress("UNCHECKED_CAST")
        val engineInfo = body["engine"] as Map<String, Any>
        assertEquals("READY", engineInfo["state"])
        assertEquals(1, engineInfo["transformations"])
        assertTrue((engineInfo["uptime"] as String).isNotBlank())
    }

    @Test
    fun `health endpoint lists registered transformations`(@TempDir tempDir: Path) = testApplication {
        createMinimalBundle(tempDir)
        val engine = UtlxEngine(testConfig())
        engine.initialize(tempDir)
        application { configureHealth(engine) }

        val response = client.get("/health")
        val body = mapper.readValue<Map<String, Any>>(response.bodyAsText())

        @Suppress("UNCHECKED_CAST")
        val transformations = body["transformations"] as Map<String, Any>
        assertTrue(transformations.containsKey("identity"))

        @Suppress("UNCHECKED_CAST")
        val identityInfo = transformations["identity"] as Map<String, Any>
        assertEquals("TEMPLATE", identityInfo["strategy"])
    }

    @Test
    fun `health endpoint returns correct JSON content type`() = testApplication {
        val engine = UtlxEngine(testConfig())
        application { configureHealth(engine) }

        val response = client.get("/health")
        assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)
    }

    @Test
    fun `stopped engine reports DOWN on health`() = testApplication {
        val engine = UtlxEngine(testConfig())
        engine.stop()
        application { configureHealth(engine) }

        val healthResponse = client.get("/health")
        val body = mapper.readValue<Map<String, Any>>(healthResponse.bodyAsText())
        assertEquals("DOWN", body["status"])

        @Suppress("UNCHECKED_CAST")
        val engineInfo = body["engine"] as Map<String, Any>
        assertEquals("STOPPED", engineInfo["state"])

        val readyResponse = client.get("/health/ready")
        assertEquals(HttpStatusCode.ServiceUnavailable, readyResponse.status)
    }

    // --- Port binding / retry tests (real server) ---

    @Test
    fun `health endpoint binds to configured port`() {
        // Find a free port
        val freePort = ServerSocket(0).use { it.localPort }
        val engine = UtlxEngine(testConfig(freePort))
        val endpoint = HealthEndpoint(engine)

        try {
            endpoint.start()
            assertEquals(freePort, endpoint.boundPort)
        } finally {
            endpoint.stop()
        }
    }

    @Test
    fun `health endpoint retries on port conflict`() {
        // Occupy a port
        val blocker = ServerSocket(0)
        val occupiedPort = blocker.localPort

        val engine = UtlxEngine(testConfig(occupiedPort))
        val endpoint = HealthEndpoint(engine)

        try {
            endpoint.start()
            // Should have bound to a higher port
            assertTrue(endpoint.boundPort > occupiedPort)
            assertTrue(endpoint.boundPort <= occupiedPort + HealthEndpoint.MAX_PORT_RETRIES)
        } finally {
            endpoint.stop()
            blocker.close()
        }
    }

    @Test
    fun `health endpoint fails after exhausting retries`() {
        // Occupy a range of ports
        val blockers = mutableListOf<ServerSocket>()
        val basePort: Int

        try {
            // Grab 12 consecutive ports (more than MAX_PORT_RETRIES + 1)
            val first = ServerSocket(0)
            blockers.add(first)
            basePort = first.localPort

            for (offset in 1..HealthEndpoint.MAX_PORT_RETRIES) {
                try {
                    blockers.add(ServerSocket(basePort + offset))
                } catch (_: Exception) {
                    // Some port in the range might not be available; skip test
                    return
                }
            }

            val engine = UtlxEngine(testConfig(basePort))
            val endpoint = HealthEndpoint(engine)

            assertFailsWith<BindException> {
                endpoint.start()
            }
        } finally {
            blockers.forEach { it.close() }
        }
    }

    @Test
    fun `boundPort reflects actual port after retry`() {
        // Occupy a port, verify boundPort is the retried one
        val blocker = ServerSocket(0)
        val occupiedPort = blocker.localPort

        val engine = UtlxEngine(testConfig(occupiedPort))
        val endpoint = HealthEndpoint(engine)

        try {
            endpoint.start()
            assertNotEquals(occupiedPort, endpoint.boundPort)
            assertTrue(endpoint.boundPort > occupiedPort)
        } finally {
            endpoint.stop()
            blocker.close()
        }
    }
}
