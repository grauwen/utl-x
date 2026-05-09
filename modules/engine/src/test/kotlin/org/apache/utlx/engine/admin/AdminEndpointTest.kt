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

    // ── Pause / Resume tests ──

    @Test
    fun `pause and resume transformation`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent()
        adminPost("/admin/transformations/pausable", source)

        // Pause
        val (pauseStatus, pauseBody) = adminPost("/admin/transformations/pausable/pause", "")
        assertEquals(200, pauseStatus, "Pause failed: $pauseBody")
        assertTrue(pauseBody.contains("paused"), "Should say paused: $pauseBody")

        // List shows paused
        val (_, listBody) = adminGet("/admin/transformations")
        assertTrue(listBody.contains("paused"), "List should show paused: $listBody")

        // Resume
        val (resumeStatus, resumeBody) = adminPost("/admin/transformations/pausable/resume", "")
        assertEquals(200, resumeStatus, "Resume failed: $resumeBody")
        assertTrue(resumeBody.contains("ready"), "Should say ready: $resumeBody")
    }

    @Test
    fun `pause nonexistent returns 404`() {
        val (status, _) = adminPost("/admin/transformations/ghost/pause", "")
        assertEquals(404, status)
    }

    // ── Error ring buffer tests ──

    @Test
    fun `errors endpoint returns empty list for healthy transformation`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent()
        adminPost("/admin/transformations/healthy", source)

        val (status, body) = adminGet("/admin/transformations/healthy/errors")
        assertEquals(200, status)
        assertTrue(body.contains("\"errors\":[]") || body.contains("\"errors\" : []") || body.contains("\"showing\":0") || body.contains("\"showing\" : 0"),
            "Should have empty errors: $body")
    }

    @Test
    fun `errors endpoint for nonexistent returns 404`() {
        val (status, _) = adminGet("/admin/transformations/ghost/errors")
        assertEquals(404, status)
    }

    // ── Bundle validate (dry run) tests ──

    @Test
    fun `validate valid bundle returns ok`() {
        val zip = createBundleZip(mapOf(
            "valid-a" to """
                %utlx 1.0
                input json
                output json
                ---
                ${'$'}input
            """.trimIndent()
        ))
        val (status, body) = adminPostZip("/admin/bundle/validate", zip)
        assertEquals(200, status, "Validate failed: $body")
        assertTrue(body.contains("valid"), "Should say valid: $body")
    }

    @Test
    fun `validate invalid bundle returns errors`() {
        val zip = createBundleZip(mapOf(
            "bad" to "not valid utlx"
        ))
        val (status, body) = adminPostZip("/admin/bundle/validate", zip)
        assertEquals(400, status)
        assertTrue(body.contains("invalid"), "Should say invalid: $body")
    }

    // ── Schema endpoints ──

    @Test
    fun `upload and list schema`() {
        val schemaContent = """{"type":"object","properties":{"name":{"type":"string"}}}"""
        val (uploadStatus, uploadBody) = adminPost("/admin/schemas/order.json", schemaContent)
        assertEquals(200, uploadStatus, "Schema upload failed: $uploadBody")
        assertTrue(uploadBody.contains("order.json"), "Should contain filename: $uploadBody")

        val (listStatus, listBody) = adminGet("/admin/schemas")
        assertEquals(200, listStatus)
        assertTrue(listBody.contains("order.json"), "Should list schema: $listBody")
    }

    @Test
    fun `delete schema`() {
        adminPost("/admin/schemas/temp.xsd", "<xs:schema/>")
        val (status, _) = adminDelete("/admin/schemas/temp.xsd")
        assertEquals(200, status)

        val (_, listBody) = adminGet("/admin/schemas")
        assertFalse(listBody.contains("temp.xsd"), "Should be gone: $listBody")
    }

    @Test
    fun `delete nonexistent schema returns 404`() {
        val (status, _) = adminDelete("/admin/schemas/ghost.json")
        assertEquals(404, status)
    }

    // ── Validation override endpoints ──

    @Test
    fun `set and get validation override`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent()
        adminPost("/admin/transformations/val-test", source)

        // Check default (no override)
        val (getStatus, getBody) = adminGet("/admin/transformations/val-test/validation")
        assertEquals(200, getStatus)
        assertTrue(getBody.contains("config"), "Source should be config: $getBody")

        // Set override
        val (setStatus, setBody) = adminPost(
            "/admin/transformations/val-test/validation",
            """{"policy":"off"}"""
        )
        assertEquals(200, setStatus)
        assertTrue(setBody.contains("runtime-override"), "Should say runtime-override: $setBody")
        assertTrue(setBody.contains("off"), "Should be off: $setBody")

        // Verify override is active
        val (_, getBody2) = adminGet("/admin/transformations/val-test/validation")
        assertTrue(getBody2.contains("runtime-override"), "Should be overridden: $getBody2")
        assertTrue(getBody2.contains("\"effective_policy\":\"off\"") || getBody2.contains("\"effective_policy\" : \"off\""),
            "Effective should be off: $getBody2")

        // Remove override
        val (delStatus, delBody) = adminDelete("/admin/transformations/val-test/validation")
        assertEquals(200, delStatus)
        assertTrue(delBody.contains("config"), "Should revert to config: $delBody")
    }

    @Test
    fun `validation override on nonexistent returns 404`() {
        val (status, _) = adminGet("/admin/transformations/ghost/validation")
        assertEquals(404, status)
    }

    // ── Dapr binding validation ──

    @Test
    fun `dapr status endpoint returns mode and sidecar info`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent()
        adminPost("/admin/transformations/dapr-test", source)

        val (status, body) = adminGet("/admin/dapr")
        assertEquals(200, status)
        assertTrue(body.contains("mode"), "Should contain mode: $body")
        assertTrue(body.contains("sidecar_reachable"), "Should contain sidecar_reachable: $body")
    }

    // ── Messaging endpoints ──

    private fun adminPostJson(path: String, json: String, key: String = adminKey): Pair<Int, String> {
        val url = URL("http://localhost:$adminPort$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Admin-Key", key)
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.outputStream.write(json.toByteArray())
        conn.outputStream.flush()
        val status = conn.responseCode
        val response = try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        return status to response
    }

    private fun uploadTestTransformation(name: String) {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent()
        adminPost("/admin/transformations/$name", source)
    }

    @Test
    fun `set messaging config returns draft status`() {
        uploadTestTransformation("orders-in")

        val (status, body) = adminPostJson(
            "/admin/transformations/orders-in/messaging",
            """{"input": {"queue": "orders-in"}, "output": {"queue": "orders-out"}}"""
        )
        assertEquals(200, status)
        assertTrue(body.contains("draft"), "Should be draft: $body")
        assertTrue(body.contains("orders-in"), "Should contain queue name: $body")
    }

    @Test
    fun `get messaging config returns endpoints and sync status`() {
        uploadTestTransformation("orders-in")
        adminPostJson(
            "/admin/transformations/orders-in/messaging",
            """{"input": {"queue": "orders-in"}}"""
        )

        val (status, body) = adminGet("/admin/transformations/orders-in/messaging")
        assertEquals(200, status)
        assertTrue(body.contains("orders-in"), "Should contain queue name: $body")
        assertTrue(body.contains("draft"), "Should show draft status: $body")
        assertTrue(body.contains("unsynced"), "Should show unsynced dapr_status: $body")
    }

    @Test
    fun `set messaging with topic and subscription`() {
        uploadTestTransformation("invoices")

        val (status, body) = adminPostJson(
            "/admin/transformations/invoices/messaging",
            """{"input": {"topic": "raw-invoices", "subscription": "utlxe"}, "output": {"topic": "normalized"}}"""
        )
        assertEquals(200, status)
        assertTrue(body.contains("raw-invoices"), "Should contain topic: $body")
    }

    @Test
    fun `set messaging with mixed queue and topic`() {
        uploadTestTransformation("mixed")

        val (status, body) = adminPostJson(
            "/admin/transformations/mixed/messaging",
            """{"input": {"queue": "orders-in"}, "output": {"topic": "processed-orders"}}"""
        )
        assertEquals(200, status)
        assertTrue(body.contains("orders-in"), "Should contain queue: $body")
        assertTrue(body.contains("processed-orders"), "Should contain topic: $body")
    }

    @Test
    fun `set messaging on nonexistent transformation returns 404`() {
        val (status, _) = adminPostJson(
            "/admin/transformations/nonexistent/messaging",
            """{"input": {"queue": "q1"}}"""
        )
        assertEquals(404, status)
    }

    @Test
    fun `set messaging with empty body returns 400`() {
        uploadTestTransformation("test")
        val (status, _) = adminPostJson(
            "/admin/transformations/test/messaging",
            """{}"""
        )
        assertEquals(400, status)
    }

    @Test
    fun `delete messaging config marks as draft`() {
        uploadTestTransformation("orders-in")
        adminPostJson(
            "/admin/transformations/orders-in/messaging",
            """{"input": {"queue": "orders-in"}}"""
        )

        val (status, body) = adminDelete("/admin/transformations/orders-in/messaging")
        assertEquals(200, status)
        assertTrue(body.contains("draft"), "Should be draft after delete: $body")
    }

    // ── Sync endpoints ──

    @Test
    fun `sync single transformation in http-only mode`() {
        uploadTestTransformation("orders-in")
        adminPostJson(
            "/admin/transformations/orders-in/messaging",
            """{"input": {"queue": "orders-in"}}"""
        )

        val (status, body) = adminPostJson("/admin/transformations/orders-in/sync", "")
        assertEquals(200, status)
        assertTrue(body.contains("synced") || body.contains("http-only"), "Should sync: $body")
    }

    @Test
    fun `sync all draft transformations`() {
        uploadTestTransformation("t1")
        uploadTestTransformation("t2")
        adminPostJson("/admin/transformations/t1/messaging", """{"input": {"queue": "q1"}}""")
        adminPostJson("/admin/transformations/t2/messaging", """{"input": {"queue": "q2"}}""")

        val (status, body) = adminPostJson("/admin/sync", "")
        assertEquals(200, status)
        assertTrue(body.contains("synced"), "Should report synced: $body")
    }

    @Test
    fun `get sync status overview`() {
        uploadTestTransformation("t1")
        uploadTestTransformation("t2")
        adminPostJson("/admin/transformations/t1/messaging", """{"input": {"queue": "q1"}}""")

        val (status, body) = adminGet("/admin/sync")
        assertEquals(200, status)
        assertTrue(body.contains("draft_count"), "Should have draft_count: $body")
        assertTrue(body.contains("dapr_mode"), "Should have dapr_mode: $body")
    }

    @Test
    fun `transformation list includes sync_status and messaging`() {
        uploadTestTransformation("orders-in")
        adminPostJson(
            "/admin/transformations/orders-in/messaging",
            """{"input": {"queue": "orders-in"}, "output": {"queue": "orders-out"}}"""
        )

        val (status, body) = adminGet("/admin/transformations")
        assertEquals(200, status)
        assertTrue(body.contains("sync_status"), "Should include sync_status: $body")
        assertTrue(body.contains("dapr_mode"), "Should include dapr_mode: $body")
        assertTrue(body.contains("messaging"), "Should include messaging: $body")
    }

    @Test
    fun `info endpoint includes dapr_mode`() {
        val (status, body) = adminGet("/admin/info")
        assertEquals(200, status)
        assertTrue(body.contains("dapr_mode"), "Should include dapr_mode: $body")
    }

    @Test
    fun `delete transformation clears sync state`() {
        uploadTestTransformation("orders-in")
        adminPostJson(
            "/admin/transformations/orders-in/messaging",
            """{"input": {"queue": "orders-in"}}"""
        )

        adminDelete("/admin/transformations/orders-in")

        // Sync status should be gone
        val (status, body) = adminGet("/admin/sync")
        assertEquals(200, status)
        assertFalse(body.contains("orders-in"), "Should not contain deleted transform: $body")
    }

    // ── Log management endpoints ──

    @Test
    fun `get log level returns current level`() {
        val (status, body) = adminGet("/admin/log/level")
        assertEquals(200, status)
        assertTrue(body.contains("level"), "Should contain level: $body")
    }

    @Test
    fun `set log level to DEBUG and back`() {
        val (getStatus, getBefore) = adminGet("/admin/log/level")
        assertEquals(200, getStatus)

        val (setStatus, setBody) = adminPostJson("/admin/log/level", """{"level": "DEBUG"}""")
        assertEquals(200, setStatus)
        assertTrue(setBody.contains("DEBUG"), "Should confirm DEBUG: $setBody")

        // Verify it changed
        val (verifyStatus, verifyBody) = adminGet("/admin/log/level")
        assertEquals(200, verifyStatus)
        assertTrue(verifyBody.contains("DEBUG"), "Should be DEBUG now: $verifyBody")

        // Restore to INFO
        adminPostJson("/admin/log/level", """{"level": "INFO"}""")
    }

    @Test
    fun `set log level with auto-revert`() {
        val (status, body) = adminPostJson("/admin/log/level",
            """{"level": "DEBUG", "revert_after_minutes": 60}""")
        assertEquals(200, status)
        assertTrue(body.contains("revert_after_minutes"), "Should confirm revert: $body")

        // Restore
        adminPostJson("/admin/log/level", """{"level": "INFO"}""")
    }

    @Test
    fun `set invalid log level returns 400`() {
        val (status, _) = adminPostJson("/admin/log/level", """{"level": "INVALID"}""")
        assertEquals(400, status)
    }

    @Test
    fun `get logs returns entries`() {
        val (status, body) = adminGet("/admin/logs")
        assertEquals(200, status)
        assertTrue(body.contains("entries"), "Should contain entries: $body")
        assertTrue(body.contains("total_buffered"), "Should contain total_buffered: $body")
        assertTrue(body.contains("current_level"), "Should contain current_level: $body")
    }

    @Test
    fun `get logs with level filter`() {
        val (status, body) = adminGet("/admin/logs?level=ERROR&limit=10")
        assertEquals(200, status)
        assertTrue(body.contains("entries"), "Should contain entries: $body")
    }

    @Test
    fun `get logs with contains filter`() {
        val (status, body) = adminGet("/admin/logs?contains=Admin&limit=10")
        assertEquals(200, status)
        assertTrue(body.contains("entries"), "Should contain entries: $body")
    }

    @Test
    fun `clear logs`() {
        val (status, body) = adminDelete("/admin/logs")
        assertEquals(200, status)
        assertTrue(body.contains("true"), "Should succeed: $body")
    }

    @Test
    fun `info endpoint includes log level`() {
        val (status, body) = adminGet("/admin/info")
        assertEquals(200, status)
        assertTrue(body.contains("log_level"), "Should include log_level: $body")
    }

    // ── Transformation config endpoints ──

    @Test
    fun `get transformation config returns defaults`() {
        uploadTestTransformation("cfg-test")

        val (status, body) = adminGet("/admin/transformations/cfg-test/config")
        assertEquals(200, status)
        assertTrue(body.contains("strategy"), "Should contain strategy: $body")
        assertTrue(body.contains("validationPolicy"), "Should contain validationPolicy: $body")
        assertTrue(body.contains("maxConcurrent"), "Should contain maxConcurrent: $body")
    }

    @Test
    fun `get config for nonexistent transformation returns 404`() {
        val (status, _) = adminGet("/admin/transformations/nonexistent/config")
        assertEquals(404, status)
    }

    @Test
    fun `update transformation config changes strategy and policy`() {
        uploadTestTransformation("cfg-test")

        val (status, body) = adminPostJson(
            "/admin/transformations/cfg-test/config",
            """{"strategy": "TEMPLATE", "validationPolicy": "strict", "maxConcurrent": 8}"""
        )
        assertEquals(200, status)
        assertTrue(body.contains("TEMPLATE"), "Should contain new strategy: $body")
        assertTrue(body.contains("strict"), "Should contain new policy: $body")
        assertTrue(body.contains("8"), "Should contain new maxConcurrent: $body")

        // Verify it persisted
        val (getStatus, getBody) = adminGet("/admin/transformations/cfg-test/config")
        assertEquals(200, getStatus)
        assertTrue(getBody.contains("TEMPLATE"), "GET should reflect update: $getBody")
        assertTrue(getBody.contains("strict"), "GET should reflect policy: $getBody")
    }

    @Test
    fun `update config with schema bindings`() {
        uploadTestTransformation("cfg-schema")

        val (status, body) = adminPostJson(
            "/admin/transformations/cfg-schema/config",
            """{"inputs": [{"name": "input", "schema": "order.xsd"}], "output_schema": "invoice.xsd"}"""
        )
        assertEquals(200, status)
        assertTrue(body.contains("order.xsd"), "Should contain input schema: $body")
        assertTrue(body.contains("invoice.xsd"), "Should contain output schema: $body")
    }

    @Test
    fun `update config partial update preserves other fields`() {
        uploadTestTransformation("cfg-partial")

        // Set initial config
        adminPostJson(
            "/admin/transformations/cfg-partial/config",
            """{"strategy": "TEMPLATE", "validationPolicy": "warn"}"""
        )

        // Partial update — only maxConcurrent
        val (status, body) = adminPostJson(
            "/admin/transformations/cfg-partial/config",
            """{"maxConcurrent": 16}"""
        )
        assertEquals(200, status)
        assertTrue(body.contains("TEMPLATE"), "Strategy should be preserved: $body")
        assertTrue(body.contains("warn"), "Policy should be preserved: $body")
        assertTrue(body.contains("16"), "MaxConcurrent should be updated: $body")
    }

    @Test
    fun `update config for nonexistent transformation returns 404`() {
        val (status, _) = adminPostJson(
            "/admin/transformations/nonexistent/config",
            """{"strategy": "COMPILED"}"""
        )
        assertEquals(404, status)
    }
}
