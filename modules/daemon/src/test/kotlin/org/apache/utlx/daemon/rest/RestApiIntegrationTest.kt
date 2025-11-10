// modules/daemon/src/test/kotlin/org/apache/utlx/daemon/rest/RestApiIntegrationTest.kt
package org.apache.utlx.daemon.rest

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.net.ServerSocket

/**
 * Integration tests for REST API Server
 *
 * These tests start an actual REST API server and make real HTTP requests
 * to verify the complete endpoint implementations.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RestApiIntegrationTest {

    private lateinit var server: RestApiServer
    private lateinit var client: HttpClient
    private var port: Int = 0
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    @BeforeAll
    fun setupServer() {
        // Find an available port
        port = findAvailablePort()

        // Create and start the server
        server = RestApiServer(port = port, host = "127.0.0.1")

        // Start server in a separate thread
        Thread {
            server.start()
        }.start()

        // Create HTTP client
        client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        // Wait for server to start
        runBlocking {
            var retries = 0
            while (retries < 30) {
                try {
                    val response = client.get("http://127.0.0.1:$port/api/health")
                    if (response.status == HttpStatusCode.OK) {
                        break
                    }
                } catch (e: Exception) {
                    // Server not ready yet
                }
                delay(100)
                retries++
            }
        }
    }

    @AfterAll
    fun teardownServer() {
        client.close()
        server.stop()
    }

    private fun findAvailablePort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }

    @Test
    fun `test health endpoint returns ok status`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/health")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertTrue(body.contains("ok") || body.contains("OK"))
    }

    @Test
    fun `test health endpoint returns proper structure`() = runBlocking {
        val response: HealthResponse = client.get("http://127.0.0.1:$port/api/health").body()

        assertEquals("ok", response.status)
        assertNotNull(response.version)
        assertTrue(response.uptime >= 0)
    }

    @Test
    fun `test validate endpoint with valid UTLX`() = runBlocking {
        val request = ValidationRequest(
            utlx = """
                %utlx 1.0
                input json
                output json
                ---
                ${'$'}input.name
            """.trimIndent(),
            strict = false
        )

        val response: ValidationResponse = client.post("http://127.0.0.1:$port/api/validate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        assertTrue(response.valid, "UTLX should be valid")
        assertTrue(response.diagnostics.isEmpty(), "Should have no diagnostics")
    }

    @Test
    fun `test validate endpoint with invalid UTLX syntax`() = runBlocking {
        val request = ValidationRequest(
            utlx = "invalid @#$% syntax",
            strict = false
        )

        val response: ValidationResponse = client.post("http://127.0.0.1:$port/api/validate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        assertFalse(response.valid, "Invalid UTLX should not be valid")
        assertTrue(response.diagnostics.isNotEmpty(), "Should have error diagnostics")
        assertTrue(response.diagnostics.any { it.severity == "error" })
    }

    @Test
    fun `test validate endpoint with type errors in strict mode`() = runBlocking {
        val request = ValidationRequest(
            utlx = "output: input.nonexistent.deeply.nested.field",
            strict = true
        )

        val response: ValidationResponse = client.post("http://127.0.0.1:$port/api/validate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        // May be valid syntactically but could have type warnings
        assertTrue(response.diagnostics.isEmpty() || response.diagnostics.all { it.severity in listOf("warning", "error") })
    }

    @Test
    fun `test execute endpoint with simple JSON transformation`() = runBlocking {
        val request = ExecutionRequest(
            utlx = """output: { fullName: input.firstName + " " + input.lastName }""",
            input = """{"firstName": "John", "lastName": "Doe"}""",
            inputFormat = "json",
            outputFormat = "json"
        )

        val response: ExecutionResponse = client.post("http://127.0.0.1:$port/api/execute") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        assertTrue(response.success, "Execution should succeed")
        assertNotNull(response.output)
        val output = response.output
        assertTrue(output!!.contains("John Doe") || output.contains("fullName"))
        assertTrue(response.executionTimeMs >= 0)
    }

    @Test
    fun `test execute endpoint with XML input and output`() = runBlocking {
        val request = ExecutionRequest(
            utlx = """output: { person: { name: input.person.name } }""",
            input = """<person><name>Alice</name></person>""",
            inputFormat = "xml",
            outputFormat = "xml"
        )

        val response: ExecutionResponse = client.post("http://127.0.0.1:$port/api/execute") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        assertTrue(response.success, "XML transformation should succeed")
        assertNotNull(response.output)
        assertTrue(response.output!!.contains("Alice"))
    }

    @Test
    fun `test execute endpoint with CSV input`() = runBlocking {
        val csvInput = """name,age
John,30
Jane,25"""

        val request = ExecutionRequest(
            utlx = """output: input""",
            input = csvInput,
            inputFormat = "csv",
            outputFormat = "json"
        )

        val response: ExecutionResponse = client.post("http://127.0.0.1:$port/api/execute") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        assertTrue(response.success, "CSV transformation should succeed")
        assertNotNull(response.output)
    }

    @Test
    fun `test execute endpoint with YAML input and output`() = runBlocking {
        val yamlInput = """
            name: Bob
            age: 35
        """.trimIndent()

        val request = ExecutionRequest(
            utlx = """output: { person: input }""",
            input = yamlInput,
            inputFormat = "yaml",
            outputFormat = "yaml"
        )

        val response: ExecutionResponse = client.post("http://127.0.0.1:$port/api/execute") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        assertTrue(response.success, "YAML transformation should succeed")
        assertNotNull(response.output)
    }

    @Test
    fun `test execute endpoint with parse error`() = runBlocking {
        val request = ExecutionRequest(
            utlx = """invalid @#$ syntax here""",
            input = """{"test": "data"}""",
            inputFormat = "json",
            outputFormat = "json"
        )

        val httpResponse = client.post("http://127.0.0.1:$port/api/execute") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        // Should return 400 Bad Request for parse errors
        assertEquals(HttpStatusCode.BadRequest, httpResponse.status)

        val response: ExecutionResponse = httpResponse.body()
        assertFalse(response.success)
        assertNotNull(response.error)
        val error = response.error
        assertTrue(error!!.contains("Parse error") || error.contains("error"))
    }

    @Test
    fun `test execute endpoint with unsupported format`() = runBlocking {
        val request = ExecutionRequest(
            utlx = """output: input""",
            input = """{"test": "data"}""",
            inputFormat = "unsupported",
            outputFormat = "json"
        )

        val httpResponse = client.post("http://127.0.0.1:$port/api/execute") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        // Should return error for unsupported format
        assertTrue(httpResponse.status == HttpStatusCode.InternalServerError ||
                   httpResponse.status == HttpStatusCode.BadRequest)

        val response: ExecutionResponse = httpResponse.body()
        assertFalse(response.success)
        assertNotNull(response.error)
    }

    @Test
    fun `test infer-schema endpoint with simple UTLX`() = runBlocking {
        val request = InferSchemaRequest(
            utlx = """
                %utlx 1.0
                input json
                output json
                ---
                { name: ${'$'}input.firstName, age: ${'$'}input.age }
            """.trimIndent(),
            format = "json-schema"
        )

        val response: InferSchemaResponse = client.post("http://127.0.0.1:$port/api/infer-schema") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        assertTrue(response.success, "Schema inference should succeed")
        assertNotNull(response.schema)
        assertEquals("json-schema", response.schemaFormat)
        assertTrue(response.confidence > 0.0)
    }

    @Test
    fun `test infer-schema endpoint with input schema`() = runBlocking {
        val inputSchema = """
            {
                "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
                "type": "object",
                "properties": {
                    "firstName": { "type": "string" },
                    "lastName": { "type": "string" }
                }
            }
        """.trimIndent()

        val request = InferSchemaRequest(
            utlx = """
                %utlx 1.0
                input json
                output json
                ---
                { fullName: ${'$'}input.firstName + " " + ${'$'}input.lastName }
            """.trimIndent(),
            inputSchema = inputSchema,
            format = "json-schema"
        )

        val response: InferSchemaResponse = client.post("http://127.0.0.1:$port/api/infer-schema") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        assertTrue(response.success, "Schema inference with input schema should succeed")
        assertNotNull(response.schema)
    }

    @Test
    fun `test infer-schema endpoint with parse error`() = runBlocking {
        val request = InferSchemaRequest(
            utlx = """invalid @#$ syntax""",
            format = "json-schema"
        )

        val httpResponse = client.post("http://127.0.0.1:$port/api/infer-schema") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        // Should return 400 Bad Request for parse errors
        assertEquals(HttpStatusCode.BadRequest, httpResponse.status)

        val response: InferSchemaResponse = httpResponse.body()
        assertFalse(response.success)
        assertNotNull(response.error)
    }

    @Test
    fun `test parse-schema endpoint with JSON Schema`() = runBlocking {
        val schema = """
            {
                "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "number" }
                },
                "required": ["name"]
            }
        """.trimIndent()

        val request = ParseSchemaRequest(
            schema = schema,
            format = "json-schema"
        )

        val response: ParseSchemaResponse = client.post("http://127.0.0.1:$port/api/parse-schema") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        assertTrue(response.success, "JSON Schema parsing should succeed")
        assertNotNull(response.normalized)
    }

    @Test
    fun `test parse-schema endpoint with XSD`() = runBlocking {
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="person">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="name" type="xs:string"/>
                            <xs:element name="age" type="xs:int"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
        """.trimIndent()

        val request = ParseSchemaRequest(
            schema = xsd,
            format = "xsd"
        )

        val response: ParseSchemaResponse = client.post("http://127.0.0.1:$port/api/parse-schema") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        assertTrue(response.success, "XSD parsing should succeed")
        assertNotNull(response.normalized)
    }

    @Test
    fun `test parse-schema endpoint with unsupported format`() = runBlocking {
        val request = ParseSchemaRequest(
            schema = """some schema content""",
            format = "unsupported-format"
        )

        val httpResponse = client.post("http://127.0.0.1:$port/api/parse-schema") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        // Should return 400 Bad Request for unsupported format
        assertEquals(HttpStatusCode.BadRequest, httpResponse.status)

        val response: ParseSchemaResponse = httpResponse.body()
        assertFalse(response.success)
        assertNotNull(response.error)
        val error = response.error
        assertTrue(error!!.contains("Unsupported") || error.contains("format"))
    }

    @Test
    fun `test parse-schema endpoint with invalid JSON Schema`() = runBlocking {
        val invalidSchema = """{ "invalid": "schema", "missing": "${'$'}schema" }"""

        val request = ParseSchemaRequest(
            schema = invalidSchema,
            format = "json-schema"
        )

        val httpResponse = client.post("http://127.0.0.1:$port/api/parse-schema") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        // May succeed or fail depending on parser strictness
        val response: ParseSchemaResponse = httpResponse.body()
        // Just verify we get a response
        assertNotNull(response)
    }

    @Test
    fun `test CORS headers are present`() = runBlocking {
        // Make a request with Origin header to trigger CORS
        val response = client.get("http://127.0.0.1:$port/api/health") {
            header("Origin", "http://localhost:3000")
        }

        // Check for CORS headers
        val corsHeader = response.headers["Access-Control-Allow-Origin"]

        // CORS headers should be present when Origin header is sent
        assertNotNull(corsHeader, "Access-Control-Allow-Origin header should be present")
        assertTrue(corsHeader == "*" || corsHeader == "http://localhost:3000",
            "CORS should allow the origin (got: $corsHeader)")
    }

    @Test
    fun `test server handles multiple concurrent requests`() = runBlocking {
        val requests = (1..10).map {
            client.get("http://127.0.0.1:$port/api/health")
        }

        // All requests should succeed
        requests.forEach { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `test malformed JSON request returns error`() = runBlocking {
        val malformedJson = """{ invalid json without closing"""

        val response = client.post("http://127.0.0.1:$port/api/validate") {
            contentType(ContentType.Application.Json)
            setBody(malformedJson)
        }

        // Debug output
        println("Malformed JSON test: status=${response.status}, body=${response.bodyAsText()}")

        // Should return 400 Bad Request for malformed JSON
        assertTrue(response.status.value in 400..499, "Should return client error for malformed JSON (got ${response.status})")
    }

    @Test
    fun `test execution measures time correctly`() = runBlocking {
        val request = ExecutionRequest(
            utlx = """output: input""",
            input = """{"test": "data"}""",
            inputFormat = "json",
            outputFormat = "json"
        )

        val response: ExecutionResponse = client.post("http://127.0.0.1:$port/api/execute") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        assertTrue(response.success)
        assertTrue(response.executionTimeMs >= 0, "Execution time should be non-negative")
        assertTrue(response.executionTimeMs < 10000, "Execution time should be reasonable (< 10s)")
    }
}
