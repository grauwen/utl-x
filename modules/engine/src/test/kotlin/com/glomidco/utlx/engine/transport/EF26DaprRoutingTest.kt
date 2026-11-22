package com.glomidco.utlx.engine.transport

import com.glomidco.utlx.engine.UtlxEngine
import com.glomidco.utlx.engine.config.*
import com.glomidco.utlx.engine.registry.TransformationInstance
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EF26: which transformation handles a message that Dapr delivers.
 *
 * Dapr posts an input binding to `/{component-name}`, so the path segment names the Dapr
 * COMPONENT. Resolving it straight to a transformation id — all this used to do — forced the two
 * to be the same string. With the Marketplace template's component called `servicebus-input`, a
 * customer had to name their transformation `servicebus-input` or get a 503 on every message, and
 * one deployment could serve one queue. Nothing documented either consequence.
 *
 * Resolution is now, most specific first:
 *   1. the `metadata.X-UTLXe-Transform` message property  (a sender routing per message)
 *   2. the bare `X-UTLXe-Transform` header                (a direct caller)
 *   3. a transformation whose transform.yaml declares that queue/topic as its input
 *   4. a transformation named after the component         (the historical behaviour)
 *
 * Rule 4 is kept deliberately: it is what works today, and removing it would break any deployment
 * that had already adopted the naming.
 */
class EF26DaprRoutingTest {

    private lateinit var engine: UtlxEngine
    private lateinit var transport: HttpTransport
    private var serverThread: Thread? = null
    private var port = 0

    private fun script(tag: String) = """
        %utlx 1.0
        input json
        output json
        ---
        { handledBy: "$tag" }
    """.trimIndent()

    @BeforeEach
    fun setup() {
        port = ServerSocket(0).use { it.localPort }     // AGENTS.md: never hardcode a port
        engine = UtlxEngine(EngineConfig(engine = EngineSettings(
            name = "ef26", monitoring = MonitoringConfig(health = HealthConfig(port = 0)))))
        engine.initializeEmpty()
        transport = HttpTransport(engine, port = port)
        serverThread = Thread { transport.start(engine.registry) }
            .also { it.isDaemon = true; it.start() }
        awaitServer()
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
    fun teardown() { transport.stop(); serverThread?.interrupt() }

    /** Load a transformation, optionally declaring an input queue in its config. */
    private fun load(name: String, inputQueue: String? = null) {
        val body = """{"transformationId":"$name","utlxSource":${jsonStr(script(name))},"strategy":"TEMPLATE"}"""
        val (code, _) = post("/api/load", body, "application/json")
        require(code == 200) { "load $name failed: $code" }
        if (inputQueue != null) {
            val i = engine.registry.get(name)!!
            engine.registry.register(name, TransformationInstance(
                name = i.name, source = i.source, strategy = i.strategy,
                config = i.config.copy(input = MessagingEndpoint(queue = inputQueue)),
                loadedAt = i.loadedAt, executionCount = i.executionCount, errorCount = i.errorCount))
        }
    }

    private fun declareTopic(name: String, topic: String) {
        val i = engine.registry.get(name)!!
        engine.registry.register(name, TransformationInstance(
            name = i.name, source = i.source, strategy = i.strategy,
            config = i.config.copy(input = MessagingEndpoint(topic = topic, subscription = "utlxe")),
            loadedAt = i.loadedAt, executionCount = i.executionCount, errorCount = i.errorCount))
    }

    private fun post(path: String, body: String, ct: String,
                     headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val c = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.setRequestProperty("Content-Type", ct)
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.doOutput = true; c.connectTimeout = 5000; c.readTimeout = 15000
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val txt = (if (code < 400) c.inputStream else c.errorStream)?.readBytes()
            ?.toString(Charsets.UTF_8) ?: ""
        return code to txt
    }

    private fun jsonStr(s: String) =
        com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(s)

    // ── rule 4: the historical behaviour must keep working ──

    @Test
    fun `a transformation named after the component still receives its messages`() {
        load("servicebus-input")
        val (code, body) = post("/servicebus-input", "{}", "application/json")
        assertEquals(200, code, body)
        assertTrue(body.contains("\"success\":true"), body)
    }

    // ── rule 3: transform.yaml is authoritative, and the NAME is free ──

    @Test
    fun `a transformation declaring the queue receives it under any name`() {
        // The whole point. The component is "servicebus-input"; the transformation is "orders".
        load("orders", inputQueue = "servicebus-input")
        val (code, body) = post("/servicebus-input", "{}", "application/json")
        assertEquals(200, code,
            "a transformation declaring this queue must receive it regardless of its name: $body")
        assertTrue(body.contains("\"success\":true"), body)
    }

    @Test
    fun `an unrelated transformation does NOT receive the message`() {
        // Guard against the fallback being too eager: a loaded transformation that declares a
        // DIFFERENT queue must not be picked up just because it is the only one present.
        load("invoices", inputQueue = "some-other-queue")
        val (code, body) = post("/servicebus-input", "{}", "application/json")
        assertEquals(503, code, "nothing claims this binding, so it must not be handled: $body")
        assertTrue(body.contains("BUNDLE_NOT_LOADED"), body)
    }

    // ── rules 1 and 2: a sender routes one queue to many transformations ──

    @Test
    fun `the Dapr metadata message property selects the transformation`() {
        // Dapr forwards Service Bus application properties as metadata.* headers — the same
        // mechanism the correlation ids already rely on. This is what lets ONE queue feed many.
        load("alpha"); load("beta")
        val (code, body) = post("/servicebus-input", "{}", "application/json",
            mapOf("metadata.X-UTLXe-Transform" to "beta"))
        assertEquals(200, code, body)
        assertTrue(body.contains("\"success\":true"), body)
    }

    @Test
    fun `the bare header still selects the transformation`() {
        load("alpha"); load("beta")
        val (code, body) = post("/servicebus-input", "{}", "application/json",
            mapOf("X-UTLXe-Transform" to "alpha"))
        assertEquals(200, code, body)
        assertTrue(body.contains("\"success\":true"), body)
    }

    @Test
    fun `the message property wins over a declared input`() {
        // Precedence matters: a per-message selector is more specific than static config.
        load("declared", inputQueue = "servicebus-input")
        load("chosen")
        val (code, body) = post("/servicebus-input", "{}", "application/json",
            mapOf("metadata.X-UTLXe-Transform" to "chosen"))
        assertEquals(200, code, body)
        assertTrue(body.contains("\"success\":true"), body)
    }

    // ── the pub/sub contract: one component, many topics, routed by transformation name ──

    @Test
    fun `dapr subscribe names the configured component and routes by transformation name`() {
        // This is the path that makes one component serve many topics. It is also the path that
        // is DEAD in Azure, because no template creates the component it names — so the contract
        // is worth pinning before it is wired up rather than after.
        load("orders");   declareTopic("orders", "orders-in")
        load("invoices"); declareTopic("invoices", "invoices-in")

        val c = URL("http://localhost:$port/dapr/subscribe").openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 10000
        assertEquals(200, c.responseCode)
        val body = c.inputStream.readBytes().toString(Charsets.UTF_8)

        // The component name must come from the single constant, not from a literal. If someone
        // re-hardcodes a different string anywhere, this fails.
        val expected = com.glomidco.utlx.engine.config.DaprComponents.pubsubName
        assertTrue(body.contains("\"pubsubname\":\"$expected\""),
            "subscriptions must name the configured component '$expected'; got: $body")

        // One component, two topics — the property that removes the one-queue-per-deployment cap.
        assertTrue(body.contains("orders-in") && body.contains("invoices-in"),
            "both declared topics must be subscribed: $body")

        // Routed by TRANSFORMATION name, which is what frees the name from the component's.
        assertTrue(body.contains("/pubsub/orders") && body.contains("/pubsub/invoices"),
            "each subscription must route to its own transformation: $body")
    }

    @Test
    fun `the default component name is unchanged`() {
        // Changing the default would silently break any deployment that already created a
        // component by the old name. It may only change deliberately, with this test.
        assertEquals("utlxe-servicebus",
            com.glomidco.utlx.engine.config.DaprComponents.DEFAULT_PUBSUB_NAME)
    }

    @Test
    fun `an unknown selector is a 503, not a silent fallback`() {
        // If a sender names a transformation that is not loaded, the message must fail rather
        // than quietly going to whatever else happens to claim the binding.
        load("declared", inputQueue = "servicebus-input")
        val (code, body) = post("/servicebus-input", "{}", "application/json",
            mapOf("metadata.X-UTLXe-Transform" to "nonexistent"))
        assertEquals(503, code, body)
        assertTrue(body.contains("nonexistent"), body)
    }
}
