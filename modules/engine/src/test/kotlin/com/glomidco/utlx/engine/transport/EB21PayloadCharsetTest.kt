package com.glomidco.utlx.engine.transport

import com.glomidco.utlx.engine.UtlxEngine
import com.glomidco.utlx.engine.config.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.nio.charset.Charset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EB21: a payload must reach the engine as the bytes the caller sent, with the charset the caller
 * declared.
 *
 * All six ExecuteRequest builders in HttpTransport used `ByteString.copyFromUtf8(<String>)`, so a
 * payload was decoded to text and re-encoded as UTF-8 before the proto was built — while
 * `setContentType` still carried the caller's original charset. `TransportHandlers.handleExecute`
 * then applied that charset to bytes which were no longer in it.
 *
 * The Dapr paths were the worse half, and the only half reachable in the Azure offering: they read
 * the body as a ByteArray correctly and then discarded it on the next line
 * (`String(payloadBytes, Charsets.UTF_8)`), so a byte that is not valid UTF-8 became U+FFFD before
 * the engine was called and could not be recovered afterwards.
 *
 * Every payload here is NON-ASCII on purpose. An ASCII payload is byte-identical under UTF-8,
 * ISO-8859-1 and the rest, so an ASCII fixture passes against the defect and proves nothing.
 */
class EB21PayloadCharsetTest {

    private lateinit var engine: UtlxEngine
    private lateinit var transport: HttpTransport
    private var serverThread: Thread? = null
    private var port = 0

    /** The accented character is the whole point: one byte in Latin-1, two in UTF-8. */
    private val xmlText = """<?xml version="1.0"?><Item>Café</Item>"""

    /**
     * The Dapr binding route answers with a messaging acknowledgement, not the transformed
     * output -- in a real flow the output goes to the output binding. So the transformation has
     * to be the detector: it returns "intact" when the accented character survived and divides by
     * zero when it did not, turning the question into the ack's `success` field.
     */
    private val script = """
        %utlx 1.0
        input xml
        output json
        ---
        { v: if (${'$'}input.Item == "Café") "intact" else (1 / 0) }
    """.trimIndent()

    @BeforeEach
    fun setup() {
        // No hardcoded port (AGENTS.md): take a free one and release it immediately.
        port = ServerSocket(0).use { it.localPort }

        engine = UtlxEngine(EngineConfig(
            engine = EngineSettings(
                name = "eb21",
                monitoring = MonitoringConfig(health = HealthConfig(port = 0))
            )
        ))
        engine.initializeEmpty()

        transport = HttpTransport(engine, port = port)
        serverThread = Thread { transport.start(engine.registry) }.also {
            it.isDaemon = true; it.start()
        }
        awaitServer()

        // Register through the transport's own load route, as the other transport tests do.
        val load = """{"transformationId":"echo","utlxSource":${jsonString(script)},"strategy":"TEMPLATE"}"""
        val (code, body) = postBytes("/api/load", load.toByteArray(Charsets.UTF_8), "application/json")
        require(code == 200 && body.contains("\"success\":true")) { "load failed: $code $body" }
    }


    /**
     * Wait for the transport to accept connections instead of sleeping a fixed time.
     * A fixed sleep passes when the class runs alone and loses the race under full-suite load,
     * which surfaced as an intermittent "Connection refused" rather than as a real failure.
     */
    private fun awaitServer(deadlineMs: Long = 20_000) {
        val until = System.currentTimeMillis() + deadlineMs
        var last: Exception? = null
        while (System.currentTimeMillis() < until) {
            try {
                val c = URL("http://localhost:$port/api/health").openConnection() as HttpURLConnection
                c.connectTimeout = 500; c.readTimeout = 1000
                c.responseCode
                c.disconnect()
                return
            } catch (e: Exception) { last = e; Thread.sleep(100) }
        }
        throw IllegalStateException("transport did not start on port $port", last)
    }

    @AfterEach
    fun teardown() {
        transport.stop()
        serverThread?.interrupt()
    }

    /** POST raw bytes, so the test controls the encoding on the wire exactly. */
    private fun postBytes(path: String, body: ByteArray, contentType: String): Pair<Int, String> {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", contentType)
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.outputStream.use { it.write(body) }
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        return code to text
    }

    // ── the Dapr input binding: the only path reachable in the Azure offering ──

    private fun daprPost(bytes: ByteArray, contentType: String): String {
        val (code, body) = postBytes("/echo", bytes, contentType)
        // 200 on success, 500 when the transformation fails -- the latter is how Dapr learns to
        // abandon or retry the message. The `success` flag in the ack is the signal we read.
        assertTrue(code == 200 || code == 500, "unexpected status $code; body: $body")
        return body
    }

    @Test
    fun `dapr binding preserves an ISO-8859-1 payload`() {
        val body = daprPost(xmlText.toByteArray(Charset.forName("ISO-8859-1")),
                            "application/xml; charset=ISO-8859-1")
        assertTrue(body.contains("\"success\":true"),
            "the accented byte did not survive. Before the fix String(bytes, UTF_8) turned 0xE9 " +
            "into U+FFFD before the engine was called. Ack was: $body")
    }

    @Test
    fun `dapr binding preserves a UTF-16 payload`() {
        // parseCharsetFromContentType advertises UTF-16 in its own doc comment.
        val body = daprPost(xmlText.toByteArray(Charsets.UTF_16), "application/xml; charset=UTF-16")
        assertTrue(body.contains("\"success\":true"), "UTF-16 payload was mangled. Ack: $body")
    }

    @Test
    fun `dapr binding still handles plain UTF-8`() {
        // The case that always worked -- a regression guard on the change itself.
        val body = daprPost(xmlText.toByteArray(Charsets.UTF_8), "application/xml; charset=UTF-8")
        assertTrue(body.contains("\"success\":true"), "UTF-8 must keep working. Ack: $body")
    }

    @Test
    fun `the guard transformation really discriminates`() {
        // Negative control, and the most important test here. Latin-1 bytes declared as UTF-8 are
        // genuinely undecodable, so the transformation MUST fail. Without this, a transformation
        // that returned "intact" for everything would make the three tests above vacuous.
        val body = daprPost(xmlText.toByteArray(Charset.forName("ISO-8859-1")),
                            "application/xml; charset=UTF-8")
        assertTrue(body.contains("\"success\":false"),
            "mislabelled bytes must fail, otherwise the positive tests prove nothing. Ack: $body")
    }

    @Test
    fun `enveloped execute honours the declared charset`() {
        // The JSON document itself is UTF-8, as JSON must be; the PAYLOAD declares Latin-1, which
        // is what the caller's document was. Before the fix the engine received UTF-8 bytes and
        // was told to read them as Latin-1.
        val json = """{"payload":${jsonString(xmlText)},
                       "contentType":"application/xml; charset=ISO-8859-1"}"""
        val (code, body) = postBytes("/api/execute/echo",
            json.toByteArray(Charsets.UTF_8), "application/json")
        assertEquals(200, code, "body was: $body")
        assertTrue(body.contains("intact"),
            "the guard transformation reports \"intact\" only when the accented character " +
            "survived; got: $body")
    }

    @Test
    fun `enveloped execute still handles plain UTF-8`() {
        val json = """{"payload":${jsonString(xmlText)},
                       "contentType":"application/xml; charset=UTF-8"}"""
        val (code, body) = postBytes("/api/execute/echo",
            json.toByteArray(Charsets.UTF_8), "application/json")
        assertEquals(200, code, "body was: $body")
        assertTrue(body.contains("intact"), "UTF-8 must keep working; got: $body")
    }

    /** Vacuity guard: if the fixture ever loses its non-ASCII character, say so loudly. */
    @Test
    fun `the fixture is actually non-ASCII`() {
        assertTrue(xmlText.any { it.code > 127 },
            "every assertion here is vacuous on an ASCII payload — the byte sequences would be identical")
        val latin1 = xmlText.toByteArray(Charset.forName("ISO-8859-1")).size
        val utf8 = xmlText.toByteArray(Charsets.UTF_8).size
        assertTrue(latin1 != utf8,
            "the payload must differ in LENGTH between Latin-1 ($latin1) and UTF-8 ($utf8), " +
            "or the two encodings are indistinguishable and every assertion here is vacuous")
    }

    private fun jsonString(s: String): String =
        com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(s)
}
