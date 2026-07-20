package com.glomidco.utlx.daemon.rest

import com.glomidco.utlx.bundle.BundleStore
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * IF19: exercises the utlxd /api/bundle routes through a real (in-test) Ktor server, over a
 * throwaway workspace — the wiring the manual smoke test covered, now automated.
 */
class BundleRoutesTest {

    private fun withRoutes(store: BundleStore?, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { registerBundleRoutes(store) }
        }
        block()
    }

    @Test
    fun `503 when no workspace is configured`() = withRoutes(store = null) {
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/bundle").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/bundle/transformations").status)
    }

    @Test
    fun `transformation CRUD round-trip over HTTP`(@TempDir root: File) = withRoutes(store = BundleStore(root)) {
        assertEquals(HttpStatusCode.OK, client.get("/api/bundle/transformations").status)

        val put = client.put("/api/bundle/transformations/order-ack") {
            contentType(ContentType.Application.Json)
            setBody("""{"source":"%utlx 1.0\n---\n${'$'}input","config":"strategy: TEMPLATE\n"}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)

        val got = client.get("/api/bundle/transformations/order-ack")
        assertEquals(HttpStatusCode.OK, got.status)
        assertTrue(got.bodyAsText().contains("%utlx 1.0"))

        assertEquals(HttpStatusCode.OK, client.delete("/api/bundle/transformations/order-ack").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/bundle/transformations/order-ack").status)
    }

    @Test
    fun `schema CRUD round-trip over HTTP`(@TempDir root: File) = withRoutes(store = BundleStore(root)) {
        val put = client.put("/api/bundle/schemas/order.json") {
            contentType(ContentType.Application.Json)
            setBody("""{"content":"{\"type\":\"object\"}"}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)

        val list = client.get("/api/bundle/schemas")
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains("order.json"))

        assertTrue(client.get("/api/bundle/schemas/order.json").bodyAsText().contains("type"))

        assertEquals(HttpStatusCode.OK, client.delete("/api/bundle/schemas/order.json").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/bundle/schemas/order.json").status)
    }

    @Test
    fun `engine-config GET then PUT round-trips`(@TempDir root: File) = withRoutes(store = BundleStore(root)) {
        assertEquals(HttpStatusCode.NotFound, client.get("/api/bundle/engine-config").status)
        val put = client.put("/api/bundle/engine-config") {
            contentType(ContentType.Application.Json)
            setBody("""{"content":"threads: 4\n"}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)
        val got = client.get("/api/bundle/engine-config")
        assertEquals(HttpStatusCode.OK, got.status)
        assertTrue(got.bodyAsText().contains("threads: 4"))
    }

    @Test
    fun `malformed body is a client error, not a server error`(@TempDir root: File) = withRoutes(store = BundleStore(root)) {
        // missing the required "source" field → must be 4xx (a bad request), never 5xx
        val resp = client.put("/api/bundle/transformations/x") {
            contentType(ContentType.Application.Json)
            setBody("""{"config":"strategy: TEMPLATE"}""")
        }
        assertTrue(resp.status.value in 400..499, "expected 4xx, got ${resp.status}")
    }

    @Test
    fun `bundle info reflects a populated workspace`(@TempDir root: File) {
        File(root, "transformations/a").mkdirs()
        File(root, "transformations/a/a.utlx").writeText("src")
        File(root, "schemas").mkdirs()
        File(root, "schemas/s.json").writeText("{}")
        File(root, "engine.yaml").writeText("threads: 4\n")
        withRoutes(store = BundleStore(root)) {
            val body = client.get("/api/bundle").bodyAsText().replace(" ", "")
            assertTrue(body.contains("\"transformationCount\":1"), body)
            assertTrue(body.contains("\"schemaCount\":1"), body)
            assertTrue(body.contains("\"hasEngineConfig\":true"), body)
        }
    }
}
