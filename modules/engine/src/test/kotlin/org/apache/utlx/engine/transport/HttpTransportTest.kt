package org.apache.utlx.engine.transport

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the HTTP transport.
 * Starts a real HTTP server on a random port and tests via HTTP calls.
 */
class HttpTransportTest {

    private val mapper = ObjectMapper().apply { registerModule(kotlinModule()) }
    private lateinit var engine: UtlxEngine
    private lateinit var transport: HttpTransport
    private var serverThread: Thread? = null
    private val port = 18085 // test port to avoid conflicts

    @BeforeEach
    fun setup() {
        engine = UtlxEngine(EngineConfig(
            engine = EngineSettings(
                name = "test-http",
                monitoring = MonitoringConfig(health = HealthConfig(port = 0))
            )
        ))
        engine.initializeEmpty()

        transport = HttpTransport(engine, port = port)
        serverThread = Thread {
            transport.start(engine.registry)
        }.also {
            it.isDaemon = true
            it.start()
        }

        // Wait for server to start
        Thread.sleep(1000)
    }

    @AfterEach
    fun teardown() {
        transport.stop()
        serverThread?.interrupt()
    }

    private fun post(path: String, body: String): Pair<Int, String> {
        val url = URL("http://localhost:$port$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
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

    private fun get(path: String): Pair<Int, String> {
        val url = URL("http://localhost:$port$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        val status = conn.responseCode
        val response = conn.inputStream.bufferedReader().readText()
        return status to response
    }

    @Test
    fun `health endpoint returns engine state`() {
        val (status, body) = get("/api/health")
        assertEquals(200, status)
        assertTrue(body.contains("loadedTransformations"), "Should have loadedTransformations: $body")
        assertTrue(body.contains("uptimeMs"), "Should have uptimeMs: $body")
    }

    @Test
    fun `transform endpoint loads and executes`() {
        val utlx = "%utlx 1.0\\ninput json\\noutput json\\n---\\n\$input"
        val (status, body) = post("/api/transform", """
            {"transformationId":"test","utlxSource":"$utlx","payload":"{\"name\":\"Alice\"}","strategy":"TEMPLATE"}
        """.trimIndent())

        assertEquals(200, status)
        assertTrue(body.contains("Alice"), "Should contain Alice: $body")
        assertTrue(body.contains("true"), "Should be successful: $body")
    }

    @Test
    fun `transform with invalid source returns 400`() {
        val (status, body) = post("/api/transform", """
            {"transformationId":"bad","utlxSource":"not valid utlx","payload":"{}","strategy":"TEMPLATE"}
        """.trimIndent())

        assertEquals(400, status)
        assertTrue(body.contains("false"), "Should indicate failure: $body")
    }

    @Test
    fun `load and execute separately`() {
        // Load
        val utlx = "%utlx 1.0\\ninput json\\noutput json\\n---\\n{name: upperCase(\$input.name)}"
        val (loadStatus, loadBody) = post("/api/load", """
            {"transformationId":"separate","utlxSource":"$utlx","strategy":"TEMPLATE"}
        """.trimIndent())
        assertEquals(200, loadStatus)
        assertTrue(loadBody.contains("true"), "Load should succeed: $loadBody")

        // Execute
        val (execStatus, execBody) = post("/api/execute/separate", """
            {"payload":"{\"name\":\"alice\"}","contentType":"application/json"}
        """.trimIndent())
        assertEquals(200, execStatus)
        assertTrue(execBody.contains("ALICE"), "Should contain uppercased name: $execBody")
    }

    @Test
    fun `execute unknown transformation returns error`() {
        val (status, body) = post("/api/execute/nonexistent", """
            {"payload":"{}"}
        """.trimIndent())

        assertEquals(422, status) // UnprocessableEntity
        assertTrue(body.contains("false"), "Should indicate failure: $body")
        assertTrue(body.contains("nonexistent") || body.contains("not found"), "Should mention missing transform: $body")
    }

    @Test
    fun `delete transformation`() {
        // Load first
        val utlx = "%utlx 1.0\\ninput json\\noutput json\\n---\\n\$input"
        post("/api/load", """{"transformationId":"to-delete","utlxSource":"$utlx","strategy":"TEMPLATE"}""")

        // Delete
        val url = URL("http://localhost:$port/api/transform/to-delete")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.connectTimeout = 5000
        val status = conn.responseCode
        val body = conn.inputStream.bufferedReader().readText()

        assertEquals(200, status)
        assertTrue(body.contains("true"), "Should succeed: $body")
    }
}
