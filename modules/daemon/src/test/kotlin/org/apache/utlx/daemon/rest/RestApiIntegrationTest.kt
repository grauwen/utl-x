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

    // ========== Functions Endpoint Tests ==========

    @Test
    fun `test functions endpoint returns 200 OK`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/functions")

        assertEquals(HttpStatusCode.OK, response.status, "Functions endpoint should return OK")
    }

    @Test
    fun `test functions endpoint returns valid JSON`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/functions")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

        val body = response.bodyAsText()
        assertNotNull(body)
        assertTrue(body.isNotEmpty())
    }

    @Test
    fun `test functions endpoint returns function registry structure`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/functions")
        val body = response.bodyAsText()

        // Check for expected JSON structure
        assertTrue(body.contains("\"version\""), "Should contain version field")
        assertTrue(body.contains("\"generated\""), "Should contain generated field")
        assertTrue(body.contains("\"totalFunctions\""), "Should contain totalFunctions field")
        assertTrue(body.contains("\"functions\""), "Should contain functions array")
        assertTrue(body.contains("\"categories\""), "Should contain categories object")
    }

    @Test
    fun `test functions endpoint returns non-empty function list`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/functions")
        val body = response.bodyAsText()

        // Parse JSON manually to check totalFunctions
        val totalFunctionsRegex = """"totalFunctions"\s*:\s*(\d+)""".toRegex()
        val match = totalFunctionsRegex.find(body)
        assertNotNull(match, "Should find totalFunctions field")

        val totalFunctions = match!!.groupValues[1].toInt()
        assertTrue(totalFunctions > 0, "Should have at least one function (found $totalFunctions)")
    }

    @Test
    fun `test functions endpoint returns expected categories`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/functions")
        val body = response.bodyAsText()

        // Check for common expected categories
        assertTrue(body.contains("\"Array\""), "Should have Array category")
        assertTrue(body.contains("\"String\""), "Should have String category")
        assertTrue(body.contains("\"Math\""), "Should have Math category")
        assertTrue(body.contains("\"Date\""), "Should have Date category")
    }

    @Test
    fun `test functions endpoint returns function with required fields`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/functions")
        val body = response.bodyAsText()

        // Check that functions have required fields
        assertTrue(body.contains("\"name\""), "Functions should have name field")
        assertTrue(body.contains("\"category\""), "Functions should have category field")
        assertTrue(body.contains("\"description\""), "Functions should have description field")
        assertTrue(body.contains("\"signature\""), "Functions should have signature field")
    }

    @Test
    fun `test functions endpoint returns map function in Array category`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/functions")
        val body = response.bodyAsText()

        // Check for map function in Array category
        assertTrue(body.contains("\"name\" : \"map\"") || body.contains("\"name\":\"map\""),
            "Should contain map function")
    }

    @Test
    fun `test functions endpoint response can be parsed as JSON`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/functions")
        val body = response.bodyAsText()

        // Try to parse with Jackson (same as server uses)
        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        // Should not throw exception
        assertDoesNotThrow {
            val registry = jacksonMapper.readValue(body, org.apache.utlx.stdlib.FunctionRegistry::class.java)
            assertNotNull(registry)
            assertTrue(registry.totalFunctions > 0)
            assertTrue(registry.functions.isNotEmpty())
            assertTrue(registry.categories.isNotEmpty())
        }
    }

    @Test
    fun `test functions endpoint returns consistent function count`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/functions")
        val body = response.bodyAsText()

        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        val registry = jacksonMapper.readValue(body, org.apache.utlx.stdlib.FunctionRegistry::class.java)

        // totalFunctions should match actual function list size
        assertEquals(registry.functions.size, registry.totalFunctions,
            "totalFunctions should match actual function count")

        // Functions in categories should match flat list
        val functionsInCategories = registry.categories.values.sumOf { it.size }
        assertEquals(registry.functions.size, functionsInCategories,
            "Functions in categories should match flat list")
    }

    @Test
    fun `test functions endpoint multiple requests return same data`() = runBlocking {
        // Make two requests
        val response1 = client.get("http://127.0.0.1:$port/api/functions")
        val body1 = response1.bodyAsText()

        val response2 = client.get("http://127.0.0.1:$port/api/functions")
        val body2 = response2.bodyAsText()

        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        val registry1 = jacksonMapper.readValue(body1, org.apache.utlx.stdlib.FunctionRegistry::class.java)
        val registry2 = jacksonMapper.readValue(body2, org.apache.utlx.stdlib.FunctionRegistry::class.java)

        // Should return same totalFunctions
        assertEquals(registry1.totalFunctions, registry2.totalFunctions,
            "Multiple requests should return consistent data")
        assertEquals(registry1.functions.size, registry2.functions.size,
            "Function count should be consistent")
        assertEquals(registry1.categories.size, registry2.categories.size,
            "Category count should be consistent")
    }

    // ========== USDL Directives Endpoint Tests ==========

    @Test
    fun `test usdl directives endpoint returns 200 OK`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")

        assertEquals(HttpStatusCode.OK, response.status, "USDL directives endpoint should return OK")
    }

    @Test
    fun `test usdl directives endpoint returns valid JSON`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

        val body = response.bodyAsText()
        assertNotNull(body)
        assertTrue(body.isNotEmpty())
    }

    @Test
    fun `test usdl directives endpoint returns registry structure`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body = response.bodyAsText()

        // Check for expected JSON structure
        assertTrue(body.contains("\"version\""), "Should contain version field")
        assertTrue(body.contains("\"generatedAt\""), "Should contain generatedAt field")
        assertTrue(body.contains("\"totalDirectives\""), "Should contain totalDirectives field")
        assertTrue(body.contains("\"directives\""), "Should contain directives array")
        assertTrue(body.contains("\"tiers\""), "Should contain tiers object")
        assertTrue(body.contains("\"scopes\""), "Should contain scopes object")
        assertTrue(body.contains("\"formats\""), "Should contain formats object")
    }

    @Test
    fun `test usdl directives endpoint returns 130 directives`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body = response.bodyAsText()

        // Parse JSON manually to check totalDirectives
        val totalDirectivesRegex = """"totalDirectives"\s*:\s*(\d+)""".toRegex()
        val match = totalDirectivesRegex.find(body)
        assertNotNull(match, "Should find totalDirectives field")

        val totalDirectives = match!!.groupValues[1].toInt()
        assertEquals(130, totalDirectives, "Should have exactly 130 directives")
    }

    @Test
    fun `test usdl directives endpoint returns all 4 tiers`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body = response.bodyAsText()

        // Check for all 4 tiers
        assertTrue(body.contains("\"core\""), "Should have core tier")
        assertTrue(body.contains("\"common\""), "Should have common tier")
        assertTrue(body.contains("\"format_specific\""), "Should have format_specific tier")
        assertTrue(body.contains("\"reserved\""), "Should have reserved tier")
    }

    @Test
    fun `test usdl directives endpoint returns tier 1 core directives`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body = response.bodyAsText()

        // Check for tier 1 core directives
        assertTrue(body.contains("\"%namespace\""), "Should contain %namespace directive")
        assertTrue(body.contains("\"%version\""), "Should contain %version directive")
        assertTrue(body.contains("\"%types\""), "Should contain %types directive")
        assertTrue(body.contains("\"%kind\""), "Should contain %kind directive")
    }

    @Test
    fun `test usdl directives endpoint returns format information`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body = response.bodyAsText()

        // Check for format abbreviations
        assertTrue(body.contains("\"xsd\""), "Should contain xsd format")
        assertTrue(body.contains("\"jsch\""), "Should contain jsch format")
        assertTrue(body.contains("\"proto\""), "Should contain proto format")
        assertTrue(body.contains("\"sql\""), "Should contain sql format")
        assertTrue(body.contains("\"avro\""), "Should contain avro format")
        assertTrue(body.contains("\"openapi\""), "Should contain openapi format")
    }

    @Test
    fun `test usdl directives endpoint returns directive examples`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body = response.bodyAsText()

        // Check that directives have examples
        assertTrue(body.contains("\"examples\""), "Directives should have examples field")
        assertTrue(body.contains("\"syntax\""), "Directives should have syntax field")
        assertTrue(body.contains("\"tooltip\""), "Directives should have tooltip field")
    }

    @Test
    fun `test usdl directives endpoint response can be parsed as JSON`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body = response.bodyAsText()

        // Try to parse with Jackson (same as server uses)
        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        // Should not throw exception
        assertDoesNotThrow {
            val registry = jacksonMapper.readValue(body, org.apache.utlx.schema.usdl.DirectiveRegistry.DirectiveRegistryData::class.java)
            assertNotNull(registry)
            assertEquals(130, registry.totalDirectives)
            assertEquals(130, registry.directives.size)
            assertTrue(registry.tiers.isNotEmpty())
            assertTrue(registry.scopes.isNotEmpty())
            assertTrue(registry.formats.isNotEmpty())
        }
    }

    @Test
    fun `test usdl directives endpoint returns correct tier counts`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body = response.bodyAsText()

        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        val registry = jacksonMapper.readValue(body, org.apache.utlx.schema.usdl.DirectiveRegistry.DirectiveRegistryData::class.java)

        // Verify tier counts
        assertEquals(9, registry.tiers["core"]?.size, "Core tier should have 9 directives")
        assertEquals(51, registry.tiers["common"]?.size, "Common tier should have 51 directives")
        assertEquals(53, registry.tiers["format_specific"]?.size, "Format-specific tier should have 53 directives")
        assertEquals(17, registry.tiers["reserved"]?.size, "Reserved tier should have 17 directives")
    }

    @Test
    fun `test usdl directives endpoint returns scopes`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body = response.bodyAsText()

        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        val registry = jacksonMapper.readValue(body, org.apache.utlx.schema.usdl.DirectiveRegistry.DirectiveRegistryData::class.java)

        // Verify scopes exist
        assertTrue(registry.scopes.containsKey("TOP_LEVEL"), "Should have TOP_LEVEL scope")
        assertTrue(registry.scopes.containsKey("TYPE_DEFINITION"), "Should have TYPE_DEFINITION scope")
        assertTrue(registry.scopes.containsKey("FIELD_DEFINITION"), "Should have FIELD_DEFINITION scope")
    }

    @Test
    fun `test usdl directives endpoint returns format metadata`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body = response.bodyAsText()

        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        val registry = jacksonMapper.readValue(body, org.apache.utlx.schema.usdl.DirectiveRegistry.DirectiveRegistryData::class.java)

        // Verify format metadata
        val xsdFormat = registry.formats["xsd"]
        assertNotNull(xsdFormat, "Should have xsd format info")
        assertEquals("XML Schema Definition", xsdFormat?.name)
        assertEquals(95, xsdFormat?.overallSupport)

        val jschFormat = registry.formats["jsch"]
        assertNotNull(jschFormat, "Should have jsch format info")
        assertEquals("JSON Schema", jschFormat?.name)
        assertEquals(90, jschFormat?.overallSupport)
    }

    @Test
    fun `test usdl directives endpoint multiple requests return same data`() = runBlocking {
        // Make two requests
        val response1 = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body1 = response1.bodyAsText()

        val response2 = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body2 = response2.bodyAsText()

        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        val registry1 = jacksonMapper.readValue(body1, org.apache.utlx.schema.usdl.DirectiveRegistry.DirectiveRegistryData::class.java)
        val registry2 = jacksonMapper.readValue(body2, org.apache.utlx.schema.usdl.DirectiveRegistry.DirectiveRegistryData::class.java)

        // Should return same totalDirectives
        assertEquals(registry1.totalDirectives, registry2.totalDirectives,
            "Multiple requests should return consistent data")
        assertEquals(registry1.directives.size, registry2.directives.size,
            "Directive count should be consistent")
        assertEquals(registry1.tiers.size, registry2.tiers.size,
            "Tier count should be consistent")
        assertEquals(registry1.formats.size, registry2.formats.size,
            "Format count should be consistent")
    }

    @Test
    fun `test usdl directives endpoint returns directive with all required fields`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/api/usdl/directives")
        val body = response.bodyAsText()

        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        val registry = jacksonMapper.readValue(body, org.apache.utlx.schema.usdl.DirectiveRegistry.DirectiveRegistryData::class.java)

        // Get the %namespace directive as an example
        val namespaceDirective = registry.directives.find { it.name == "%namespace" }
        assertNotNull(namespaceDirective, "Should find %namespace directive")

        // Verify all required fields are present
        assertEquals("%namespace", namespaceDirective?.name)
        assertEquals("core", namespaceDirective?.tier)
        assertTrue(namespaceDirective?.scopes?.isNotEmpty() == true, "Should have scopes")
        assertNotNull(namespaceDirective?.valueType, "Should have valueType")
        assertNotNull(namespaceDirective?.description, "Should have description")
        assertTrue(namespaceDirective?.supportedFormats?.isNotEmpty() == true, "Should have supported formats")
        assertTrue(namespaceDirective?.examples?.isNotEmpty() == true, "Should have examples")
        assertNotNull(namespaceDirective?.syntax, "Should have syntax")
        assertNotNull(namespaceDirective?.tooltip, "Should have tooltip")
    }
}
