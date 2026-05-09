package org.apache.utlx.engine.admin

import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.*
import org.apache.utlx.engine.health.HealthEndpoint
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EF09: Tests for locked mode (production bundle mode).
 * Engine is set to locked mode — mutating Admin API endpoints return 403.
 */
class LockedModeTest {

    private lateinit var engine: UtlxEngine
    private lateinit var healthEndpoint: HealthEndpoint
    private val adminPort = 18082  // different port from AdminEndpointTest
    private val adminKey = "test-locked-key"
    private lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("utlxe-locked-test")

        // Create a .utlar with one transformation
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write("""{"format":"utlar","version":"v1.0.0"}""".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("transformations/locked-tx/locked-tx.utlx"))
            zos.write("%utlx 1.0\ninput json\noutput json\n---\n\$input".toByteArray())
            zos.closeEntry()
        }
        Files.write(tempDir.resolve("bundle.utlar"), baos.toByteArray())

        engine = UtlxEngine(EngineConfig(
            engine = EngineSettings(
                name = "test-locked",
                monitoring = MonitoringConfig(health = HealthConfig(port = adminPort))
            )
        ))
        engine.initializeEmpty()

        // Detect locked mode and load .utlar
        engine.bundleInfo = detectBundleMode(tempDir)
        loadUtlar(tempDir.resolve("bundle.utlar"), engine)

        // Transition to RUNNING
        val stateField = engine.javaClass.getDeclaredField("stateRef")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateRef = stateField.get(engine) as java.util.concurrent.atomic.AtomicReference<Any>
        stateRef.set(org.apache.utlx.engine.EngineState.RUNNING)

        healthEndpoint = HealthEndpoint(engine, adminKey = adminKey)
        healthEndpoint.start()
        Thread.sleep(500)
    }

    @AfterEach
    fun teardown() {
        healthEndpoint.stop()
        tempDir.toFile().deleteRecursively()
    }

    private fun adminPost(path: String, body: String): Pair<Int, String> {
        val url = URL("http://localhost:$adminPort$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "text/plain")
        conn.setRequestProperty("X-Admin-Key", adminKey)
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.outputStream.write(body.toByteArray())
        conn.outputStream.flush()
        val status = conn.responseCode
        val response = try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        return status to response
    }

    private fun adminGet(path: String): Pair<Int, String> {
        val url = URL("http://localhost:$adminPort$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("X-Admin-Key", adminKey)
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        val status = conn.responseCode
        val response = try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        return status to response
    }

    private fun adminDelete(path: String): Pair<Int, String> {
        val url = URL("http://localhost:$adminPort$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("X-Admin-Key", adminKey)
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        val status = conn.responseCode
        val response = try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        return status to response
    }

    // ── Locked mode is detected ──

    @Test
    fun `engine is in locked mode`() {
        assertTrue(engine.isLocked)
        assertEquals("locked", engine.bundleInfo.mode)
    }

    @Test
    fun `transformation loaded from utlar`() {
        val tx = engine.registry.get("locked-tx")
        assertTrue(tx != null, "Should have loaded locked-tx from .utlar")
    }

    // ── Mutating endpoints blocked (403) ──

    @Test
    fun `upload transformation blocked in locked mode`() {
        val (status, body) = adminPost("/admin/transformations/new-tx", "%utlx 1.0\ninput json\noutput json\n---\n\$input")
        assertEquals(403, status)
        assertTrue(body.contains("BUNDLE_LOCKED"), "Should contain error code: $body")
        assertTrue(body.contains("locked"), "Should mention locked: $body")
    }

    @Test
    fun `delete transformation blocked in locked mode`() {
        val (status, body) = adminDelete("/admin/transformations/locked-tx")
        assertEquals(403, status)
        assertTrue(body.contains("BUNDLE_LOCKED"), "Should contain error code: $body")
    }

    @Test
    fun `upload bundle blocked in locked mode`() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("transformations/x/x.utlx"))
            zos.write("%utlx 1.0\ninput json\noutput json\n---\n\$input".toByteArray())
            zos.closeEntry()
        }
        val url = URL("http://localhost:$adminPort/admin/bundle")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/zip")
        conn.setRequestProperty("X-Admin-Key", adminKey)
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.outputStream.write(baos.toByteArray())
        conn.outputStream.flush()
        assertEquals(403, conn.responseCode)
    }

    @Test
    fun `delete bundle blocked in locked mode`() {
        val (status, _) = adminDelete("/admin/bundle")
        assertEquals(403, status)
    }

    @Test
    fun `upload schema blocked in locked mode`() {
        val (status, _) = adminPost("/admin/schemas/test.xsd", "<xs:schema/>")
        assertEquals(403, status)
    }

    @Test
    fun `delete schema blocked in locked mode`() {
        val (status, _) = adminDelete("/admin/schemas/test.xsd")
        assertEquals(403, status)
    }

    @Test
    fun `set messaging blocked in locked mode`() {
        val url = URL("http://localhost:$adminPort/admin/transformations/locked-tx/messaging")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Admin-Key", adminKey)
        conn.doOutput = true
        conn.outputStream.write("""{"input":{"queue":"q1"}}""".toByteArray())
        conn.outputStream.flush()
        assertEquals(403, conn.responseCode)
    }

    // ── Read + operational endpoints still work ──

    @Test
    fun `list transformations allowed in locked mode`() {
        val (status, body) = adminGet("/admin/transformations")
        assertEquals(200, status)
        assertTrue(body.contains("locked-tx"), "Should list locked-tx: $body")
    }

    @Test
    fun `get info shows locked mode and bundle version`() {
        val (status, body) = adminGet("/admin/info")
        assertEquals(200, status)
        assertTrue(body.contains("\"mode\":\"locked\""), "Should show locked mode: $body")
        assertTrue(body.contains("v1.0.0"), "Should show bundle version: $body")
    }

    @Test
    fun `test transformation allowed in locked mode`() {
        val (status, body) = adminPost("/admin/transformations/locked-tx/test", """{"test": true}""")
        assertEquals(200, status)
        assertTrue(body.contains("ok") || body.contains("true"), "Test should work: $body")
    }

    @Test
    fun `pause and resume allowed in locked mode`() {
        val (pauseStatus, _) = adminPost("/admin/transformations/locked-tx/pause", "")
        assertEquals(200, pauseStatus)

        val (resumeStatus, _) = adminPost("/admin/transformations/locked-tx/resume", "")
        assertEquals(200, resumeStatus)
    }

    @Test
    fun `export bundle allowed in locked mode`() {
        val (status, _) = adminGet("/admin/bundle")
        assertEquals(200, status)
    }

    @Test
    fun `validation override allowed in locked mode`() {
        val url = URL("http://localhost:$adminPort/admin/transformations/locked-tx/validation")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Admin-Key", adminKey)
        conn.doOutput = true
        conn.outputStream.write("""{"policy":"off"}""".toByteArray())
        conn.outputStream.flush()
        assertEquals(200, conn.responseCode)
    }

    // ── Config endpoints in locked mode ──

    @Test
    fun `get config allowed in locked mode`() {
        val (status, body) = adminGet("/admin/transformations/locked-tx/config")
        assertEquals(200, status)
        assertTrue(body.contains("strategy"), "Should contain strategy: $body")
    }

    @Test
    fun `update config blocked in locked mode`() {
        val url = URL("http://localhost:$adminPort/admin/transformations/locked-tx/config")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Admin-Key", adminKey)
        conn.doOutput = true
        conn.outputStream.write("""{"strategy":"TEMPLATE"}""".toByteArray())
        conn.outputStream.flush()
        assertEquals(403, conn.responseCode)
    }
}
