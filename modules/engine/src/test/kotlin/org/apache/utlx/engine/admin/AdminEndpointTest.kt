package org.apache.utlx.engine.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.*
import org.apache.utlx.engine.health.HealthEndpoint
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Integration tests for EF03 Admin API on port 8081.
 * Starts a real health+admin server and tests via HTTP calls.
 */
class AdminEndpointTest {

    private val mapper = ObjectMapper().apply { registerModule(kotlinModule()) }
    private lateinit var engine: UtlxEngine
    private lateinit var healthEndpoint: HealthEndpoint
    private val adminPort = 18081
    private val adminKey = "test-admin-key"

    @BeforeEach
    fun setup() {
        engine = UtlxEngine(EngineConfig(
            engine = EngineSettings(
                name = "test-admin",
                monitoring = MonitoringConfig(health = HealthConfig(port = adminPort))
            )
        ))
        engine.initializeEmpty()

        // Manually transition to RUNNING (normally done by engine.start())
        val stateField = engine.javaClass.getDeclaredField("stateRef")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateRef = stateField.get(engine) as java.util.concurrent.atomic.AtomicReference<Any>
        stateRef.set(org.apache.utlx.engine.EngineState.RUNNING)

        healthEndpoint = HealthEndpoint(engine, adminKey = adminKey)
        healthEndpoint.start()

        Thread.sleep(500) // wait for server
    }

    @AfterEach
    fun teardown() {
        healthEndpoint.stop()
    }

    // ── Helpers ──

    private fun adminPost(path: String, body: String, key: String = adminKey): Pair<Int, String> {
        val url = URL("http://localhost:$adminPort$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "text/plain")
        conn.setRequestProperty("X-Admin-Key", key)
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

    private fun adminGet(path: String, key: String = adminKey): Pair<Int, String> {
        val url = URL("http://localhost:$adminPort$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("X-Admin-Key", key)
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

    private fun adminDelete(path: String, key: String = adminKey): Pair<Int, String> {
        val url = URL("http://localhost:$adminPort$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("X-Admin-Key", key)
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

    // ── Auth tests ──

    @Test
    fun `admin endpoint requires X-Admin-Key`() {
        val (status, body) = adminGet("/admin/transformations", key = "")
        assertEquals(403, status)
        assertTrue(body.contains("Invalid"), "Should mention invalid key: $body")
    }

    @Test
    fun `admin endpoint rejects wrong key`() {
        val (status, body) = adminGet("/admin/transformations", key = "wrong-key")
        assertEquals(403, status)
    }

    @Test
    fun `health endpoints do not require admin key`() {
        val url = URL("http://localhost:$adminPort/health")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        // No X-Admin-Key header
        val status = conn.responseCode
        assertEquals(200, status)
    }

    // ── Upload + list + delete ──

    @Test
    fun `upload transformation and list it`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {greeting: concat("Hello, ", ${'$'}input.name)}
        """.trimIndent()

        val (uploadStatus, uploadBody) = adminPost("/admin/transformations/hello", source)
        assertEquals(200, uploadStatus, "Upload failed: $uploadBody")
        assertTrue(uploadBody.contains("deployed"), "Should say deployed: $uploadBody")

        val (listStatus, listBody) = adminGet("/admin/transformations")
        assertEquals(200, listStatus)
        assertTrue(listBody.contains("hello"), "Should list 'hello': $listBody")
    }

    @Test
    fun `upload invalid source returns 400`() {
        val (status, body) = adminPost("/admin/transformations/bad", "this is not valid utlx")
        assertEquals(400, status)
        assertTrue(body.contains("rejected"), "Should say rejected: $body")
    }

    @Test
    fun `delete transformation`() {
        // Upload first
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent()
        adminPost("/admin/transformations/temp", source)

        // Delete
        val (status, body) = adminDelete("/admin/transformations/temp")
        assertEquals(200, status)
        assertTrue(body.contains("true"), "Should succeed: $body")

        // List — should be gone
        val (_, listBody) = adminGet("/admin/transformations")
        assertFalse(listBody.contains("\"temp\""), "Should not list 'temp': $listBody")
    }

    @Test
    fun `delete nonexistent returns 404`() {
        val (status, _) = adminDelete("/admin/transformations/nonexistent")
        assertEquals(404, status)
    }

    // ── Test endpoint ──

    @Test
    fun `test transformation with sample input`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {greeting: concat("Hello, ", ${'$'}input.name, "!")}
        """.trimIndent()
        adminPost("/admin/transformations/greet", source)

        val (status, body) = adminPost(
            "/admin/transformations/greet/test",
            """{"name": "World"}"""
        )
        assertEquals(200, status)
        assertTrue(body.contains("ok"), "Should be ok: $body")
        assertTrue(body.contains("Hello, World!"), "Should contain greeting: $body")
    }

    @Test
    fun `test nonexistent transformation returns 404`() {
        val (status, body) = adminPost(
            "/admin/transformations/ghost/test",
            """{"x": 1}"""
        )
        assertEquals(404, status)
        assertTrue(body.contains("TRANSFORMATION_NOT_FOUND"), "Should have error code: $body")
    }

    // ── Info endpoint ──

    @Test
    fun `info endpoint returns engine metadata`() {
        val (status, body) = adminGet("/admin/info")
        assertEquals(200, status)
        assertTrue(body.contains("version"), "Should have version: $body")
        assertTrue(body.contains("uptime_seconds"), "Should have uptime: $body")
        assertTrue(body.contains("admin_key_set"), "Should have admin_key_set: $body")
    }

    // ── Readiness ──

    @Test
    fun `readiness returns not ready when no transformations`() {
        val url = URL("http://localhost:$adminPort/health/ready")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        val status = conn.responseCode
        assertEquals(503, status, "Should be 503 with no transformations")
    }

    @Test
    fun `readiness returns ready after uploading transformation`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent()
        adminPost("/admin/transformations/test-ready", source)

        val url = URL("http://localhost:$adminPort/health/ready")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        val status = conn.responseCode
        assertEquals(200, status, "Should be 200 after upload")
    }

    // ── Get details ──

    @Test
    fun `get transformation details includes source`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent()
        adminPost("/admin/transformations/detail-test", source)

        val (status, body) = adminGet("/admin/transformations/detail-test")
        assertEquals(200, status)
        assertTrue(body.contains("detail-test"), "Should contain name: $body")
        assertTrue(body.contains("utlx"), "Should contain source: $body")
    }

    // ── Bundle helpers ──

    private fun createBundleZip(transformations: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, source) in transformations) {
                zos.putNextEntry(ZipEntry("transformations/$name/$name.utlx"))
                zos.write(source.toByteArray())
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun adminPostZip(path: String, zipBytes: ByteArray): Pair<Int, String> {
        val url = URL("http://localhost:$adminPort$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/zip")
        conn.setRequestProperty("X-Admin-Key", adminKey)
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.outputStream.write(zipBytes)
        conn.outputStream.flush()
        val status = conn.responseCode
        val response = try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        return status to response
    }

    private fun adminGetBytes(path: String): Pair<Int, ByteArray> {
        val url = URL("http://localhost:$adminPort$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("X-Admin-Key", adminKey)
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        val status = conn.responseCode
        val bytes = try {
            conn.inputStream.readBytes()
        } catch (e: Exception) {
            conn.errorStream?.readBytes() ?: ByteArray(0)
        }
        return status to bytes
    }

    // ── Bundle tests ──

    @Test
    fun `upload ZIP bundle deploys all transformations`() {
        val zip = createBundleZip(mapOf(
            "greet" to """
                %utlx 1.0
                input json
                output json
                ---
                {hello: ${'$'}input.name}
            """.trimIndent(),
            "double" to """
                %utlx 1.0
                input json
                output json
                ---
                {result: ${'$'}input.x * 2}
            """.trimIndent()
        ))

        val (status, body) = adminPostZip("/admin/bundle", zip)
        assertEquals(200, status, "Bundle upload failed: $body")
        assertTrue(body.contains("deployed"), "Should say deployed: $body")
        assertTrue(body.contains("greet"), "Should list greet: $body")
        assertTrue(body.contains("double"), "Should list double: $body")

        // Verify both work
        val (_, testBody) = adminPost("/admin/transformations/greet/test", """{"name":"ZIP"}""")
        assertTrue(testBody.contains("ZIP"), "Greet should work: $testBody")

        val (_, testBody2) = adminPost("/admin/transformations/double/test", """{"x":21}""")
        assertTrue(testBody2.contains("42"), "Double should work: $testBody2")
    }

    @Test
    fun `export bundle returns valid ZIP with all transformations`() {
        // Upload two transformations first
        adminPost("/admin/transformations/export-a", """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent())
        adminPost("/admin/transformations/export-b", """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent())

        // Export
        val (status, zipBytes) = adminGetBytes("/admin/bundle")
        assertEquals(200, status)
        assertTrue(zipBytes.size > 0, "ZIP should not be empty")

        // Parse ZIP and verify contents
        val entries = mutableListOf<String>()
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        assertTrue(entries.any { it.contains("export-a") }, "ZIP should contain export-a: $entries")
        assertTrue(entries.any { it.contains("export-b") }, "ZIP should contain export-b: $entries")
    }

    @Test
    fun `delete bundle removes all transformations`() {
        // Upload
        adminPost("/admin/transformations/del-a", """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent())
        adminPost("/admin/transformations/del-b", """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent())

        // Verify both exist
        val (_, listBefore) = adminGet("/admin/transformations")
        assertTrue(listBefore.contains("del-a"))
        assertTrue(listBefore.contains("del-b"))

        // Delete all
        val (status, body) = adminDelete("/admin/bundle")
        assertEquals(200, status)
        assertTrue(body.contains("del-a"), "Should list deleted: $body")
        assertTrue(body.contains("del-b"), "Should list deleted: $body")

        // Verify empty
        val (_, listAfter) = adminGet("/admin/transformations")
        assertFalse(listAfter.contains("del-a"), "Should be gone: $listAfter")
        assertFalse(listAfter.contains("del-b"), "Should be gone: $listAfter")
    }

    @Test
    fun `upload invalid ZIP returns 400`() {
        val (status, body) = adminPostZip("/admin/bundle", "not a zip".toByteArray())
        assertEquals(400, status)
        assertTrue(body.contains("rejected"), "Should say rejected: $body")
    }
}
