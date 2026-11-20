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
 * EF24: `POST /api/transformations/{id}/run` — the route the Azure offering publishes.
 *
 * Three things distinguish it from `/api/execute/{id}`, and each has a test here:
 *  - the body is the document, not a JSON envelope with the document escaped into a string;
 *  - the response is the output in the transformation's OWN format, not JSON wrapping an
 *    escaped string;
 *  - failures are status codes, not a `success: false` inside a 200.
 *
 * It authenticates nothing. That is deliberate and asserted below: the credential belongs in the
 * proxy (see docs/features/EF24-*.md), so a deployment that publishes this route without one has
 * an unauthenticated remote execution path.
 */
class EF24RawBodyRouteTest {

    private lateinit var engine: UtlxEngine
    private lateinit var transport: HttpTransport
    private var serverThread: Thread? = null
    private var port = 0

    private val xmlToJson = """
        %utlx 1.0
        input xml
        output json
        ---
        { v: ${'$'}input.Item }
    """.trimIndent()

    /** Output format other than JSON, to prove the response content type follows the header. */
    private val jsonToCsv = """
        %utlx 1.0
        input json
        output csv
        ---
        ${'$'}input.rows
    """.trimIndent()

    /** OData serialises to JSON, which is easy to confuse with `osch` (EDMX, which is XML). */
    private val jsonToOData = """
        %utlx 1.0
        input json
        output odata
        ---
        { value: ${'$'}input.rows }
    """.trimIndent()

    @BeforeEach
    fun setup() {
        port = ServerSocket(0).use { it.localPort }   // AGENTS.md: never hardcode a port
        engine = UtlxEngine(EngineConfig(
            engine = EngineSettings(
                name = "ef24",
                monitoring = MonitoringConfig(health = HealthConfig(port = 0))
            )
        ))
        engine.initializeEmpty()

        transport = HttpTransport(engine, port = port)
        serverThread = Thread { transport.start(engine.registry) }.also {
            it.isDaemon = true; it.start()
        }
        Thread.sleep(1200)

        load("echo", xmlToJson)
        load("tocsv", jsonToCsv)
        load("toodata", jsonToOData)
    }

    @AfterEach
    fun teardown() {
        transport.stop()
        serverThread?.interrupt()
    }

    private fun load(name: String, src: String) {
        val body = """{"transformationId":"$name","utlxSource":${json(src)},"strategy":"TEMPLATE"}"""
        val (code, resp) = post("/api/load", body.toByteArray(), "application/json")
        require(code == 200 && resp.second.contains("\"success\":true")) { "load $name failed: $code" }
    }

    /** Returns status to (contentType, body). */
    private fun post(path: String, body: ByteArray, contentType: String,
                     headers: Map<String, String> = emptyMap()): Pair<Int, Pair<String, String>> {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", contentType)
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 15000
        conn.outputStream.use { it.write(body) }
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        val ct = conn.getHeaderField("Content-Type") ?: ""
        return code to (ct to text)
    }

    private fun json(s: String) =
        com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(s)

    // ── the body is the document ──

    @Test
    fun `raw XML needs no envelope and returns the output directly`() {
        val (code, r) = post("/api/transformations/echo/run",
            "<Item>hello</Item>".toByteArray(), "application/xml")
        assertEquals(200, code, "body: ${r.second}")
        // The output itself, not {"success":true,"output":"{\n \"v\": ...}"}
        assertTrue(r.second.contains("\"v\""), "expected the transformed output; got: ${r.second}")
        assertTrue(!r.second.contains("\"success\""),
            "the response must be the output, not a status envelope: ${r.second}")
    }

    @Test
    fun `the response content type follows the transformation output format`() {
        val (code, r) = post("/api/transformations/tocsv/run",
            """{"rows":[{"a":1,"b":"x"}]}""".toByteArray(), "application/json")
        assertEquals(200, code, "body: ${r.second}")
        assertTrue(r.first.startsWith("text/csv"),
            "a transformation declaring `output csv` must answer text/csv; got '${r.first}'")
        assertTrue(r.second.trimStart().startsWith("a,b"), "expected CSV; got: ${r.second}")
    }

    @Test
    fun `an OData output answers application json, not octet-stream`() {
        // OData is the format most likely to be mis-typed here: its name suggests a type of its
        // own, but ODataJSONSerializer emits JSON. Getting this wrong makes a conforming client
        // refuse a perfectly good response.
        val (code, r) = post("/api/transformations/toodata/run",
            """{"rows":[{"id":1,"name":"Acme"}]}""".toByteArray(), "application/json")
        assertEquals(200, code, "body: ${r.second}")
        assertTrue(r.first.startsWith("application/json"),
            "`output odata` serialises to JSON, so the response must say so; got '${r.first}'")
        assertTrue(r.second.contains("\"value\""), "expected the OData value array; got: ${r.second}")
    }

    // ── byte fidelity: the property that makes this route necessary ──

    @Test
    fun `a payload that is not valid UTF-8 survives`() {
        // 0xE9 alone is a valid ISO-8859-1 'e-acute' and an INVALID UTF-8 sequence. This is the
        // property Avro and Protobuf need and the enveloped route cannot provide, since a JSON
        // string holds text rather than arbitrary bytes.
        //
        // A binary Avro fixture would demonstrate it more directly, but none exists in the repo --
        // every Avro conformance fixture feeds JSON and tests schema serialisation. Byte fidelity
        // for an invalid-UTF-8 sequence is the same property, tested with what is here.
        val bytes = "<Item>Café</Item>".toByteArray(Charset.forName("ISO-8859-1"))
        assertTrue(bytes.any { it.toInt() and 0xFF == 0xE9 }, "fixture must contain the raw 0xE9 byte")

        val (code, r) = post("/api/transformations/echo/run", bytes, "application/xml; charset=ISO-8859-1")
        assertEquals(200, code, "body: ${r.second}")
        assertTrue(r.second.contains("Café"),
            "the accented character must survive; a UTF-8 decode would have made it U+FFFD: ${r.second}")
    }

    // ── failures are status codes ──

    @Test
    fun `an unknown transformation is 404, not 503 and not 500`() {
        val (code, r) = post("/api/transformations/nope/run", "<Item/>".toByteArray(), "application/xml")
        assertEquals(404, code, "a mistyped id must not be reported as retry-later: ${r.second}")
        assertTrue(r.second.contains("TRANSFORMATION_NOT_FOUND"), "got: ${r.second}")
    }

    @Test
    fun `a failing transformation answers 422, not 200 with a flag`() {
        val (code, r) = post("/api/transformations/echo/run",
            "this is not xml at all".toByteArray(), "application/xml")
        assertTrue(code == 422 || code == 503,
            "a transformation failure must be a status code, not a 200; got $code / ${r.second}")
        assertTrue(!r.second.contains("\"success\":true"), "got: ${r.second}")
    }

    // ── headers in, headers out ──

    @Test
    fun `X-Correlation-Id is echoed back`() {
        val cid = "corr-01a1-test"
        val conn = URL("http://localhost:$port/api/transformations/echo/run").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/xml")
        conn.setRequestProperty("X-Correlation-Id", cid)
        conn.doOutput = true
        conn.outputStream.use { it.write("<Item>x</Item>".toByteArray()) }
        assertEquals(200, conn.responseCode)
        assertEquals(cid, conn.getHeaderField("X-Correlation-Id"),
            "a caller correlating request to response needs its id back")
    }

    @Test
    fun `an oversized body is rejected on Content-Length`() {
        // The app-wide guard in HttpTransport covers this route too; asserted so a future
        // route-scoped change cannot quietly drop it.
        val conn = URL("http://localhost:$port/api/transformations/echo/run").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/xml")
        conn.setRequestProperty("Content-Length", (11L * 1024 * 1024).toString())
        conn.setFixedLengthStreamingMode(11L * 1024 * 1024)
        conn.doOutput = true
        val code = try {
            conn.outputStream.use { it.write(ByteArray(1024)) }
            conn.responseCode
        } catch (_: Exception) {
            // The server answers 413 and closes before the body is finished, which surfaces as an
            // IO error on write. Either way it refused before parsing.
            413
        }
        assertEquals(413, code, "11 MB must be refused against the 10 MB limit")
    }

    // ── the security contract ──

    @Test
    fun `the engine does NOT authenticate this route`() {
        // Pinned deliberately. The credential lives in the proxy (EF24), so nobody should read
        // this route's existence as "the engine protects it". If authentication is ever added
        // here, this test fails and the EF24 design decision gets revisited on purpose rather
        // than by accident.
        val (code, _) = post("/api/transformations/echo/run", "<Item>x</Item>".toByteArray(), "application/xml")
        assertEquals(200, code,
            "no key was sent and the call succeeded: publishing 8085 without a proxy credential " +
            "is an unauthenticated remote execution path")
    }
}
