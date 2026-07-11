package com.glomidco.utlx.engine.admin

import com.glomidco.utlx.bundle.BundleStore
import com.glomidco.utlx.engine.UtlxEngine
import com.glomidco.utlx.engine.config.*
import com.glomidco.utlx.engine.health.HealthEndpoint
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import com.glomidco.utlx.engine.testutil.freeOrEnvPort
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * IF19: verifies EF03's deploy/delete now persist through the shared [BundleStore] (true lockstep).
 * A transformation POSTed to the Admin API must land on disk in the exact layout utlxd reads back,
 * and be removed on DELETE.
 */
class AdminEndpointPersistenceTest {

    private lateinit var engine: UtlxEngine
    private lateinit var healthEndpoint: HealthEndpoint
    // Env override → preferred 18082 (only if free) → OS-assigned free port. Never a hard literal.
    private val adminPort = freeOrEnvPort("UTLXE_TEST_ADMIN_PORT", preferred = 18082)
    private val adminKey = "test-admin-key"

    @TempDir
    lateinit var dataDir: File

    @BeforeEach
    fun setup() {
        engine = UtlxEngine(EngineConfig(
            engine = EngineSettings(
                name = "test-persist",
                monitoring = MonitoringConfig(health = HealthConfig(port = adminPort))
            )
        ))
        engine.initializeEmpty()

        // Transition to RUNNING (normally done by engine.start()) so admin mutations are allowed.
        val stateField = engine.javaClass.getDeclaredField("stateRef")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateRef = stateField.get(engine) as java.util.concurrent.atomic.AtomicReference<Any>
        stateRef.set(com.glomidco.utlx.engine.EngineState.RUNNING)

        healthEndpoint = HealthEndpoint(engine, adminKey = adminKey, dataDir = dataDir.absolutePath)
        healthEndpoint.start()
        Thread.sleep(500)
    }

    @AfterEach
    fun teardown() {
        healthEndpoint.stop()
    }

    @Test
    fun `deploy persists via BundleStore and delete removes it`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent()

        val (postStatus, postBody) = adminRequest("POST", "/admin/transformations/order-ack", source)
        assertEquals(200, postStatus, "deploy should succeed: $postBody")

        // The shared BundleStore (the SAME layer utlxd uses) sees the persisted files → lockstep.
        val store = BundleStore(dataDir)
        val tx = store.getTransformation("order-ack")
        assertNotNull(tx, "transformation should be persisted on disk via BundleStore")
        assertTrue(tx!!.source!!.contains("%utlx 1.0"))
        assertTrue(File(dataDir, "transformations/order-ack/order-ack.utlx").isFile)

        val (delStatus, _) = adminRequest("DELETE", "/admin/transformations/order-ack", null)
        assertEquals(200, delStatus)
        assertNull(store.getTransformation("order-ack"))
        assertFalse(File(dataDir, "transformations/order-ack").exists())
    }

    private fun adminRequest(method: String, path: String, body: String?): Pair<Int, String> {
        val conn = URL("http://localhost:$adminPort$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("X-Admin-Key", adminKey)
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        if (body != null) {
            conn.setRequestProperty("Content-Type", "text/plain")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val status = conn.responseCode
        val resp = try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        return status to resp
    }
}
