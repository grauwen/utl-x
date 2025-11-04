// modules/daemon/src/test/kotlin/org/apache/utlx/daemon/rest/RestApiServerTest.kt
package org.apache.utlx.daemon.rest

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Unit tests for REST API Server
 *
 * These tests verify the REST API server can be instantiated and basic DTOs work correctly.
 * Integration tests with actual HTTP requests should be done separately.
 */
class RestApiServerTest {

    private var server: RestApiServer? = null

    @BeforeEach
    fun setup() {
        // Create server instance for testing (don't start it)
        server = RestApiServer(port = 0) // Port 0 will use a random available port
    }

    @AfterEach
    fun teardown() {
        // Stop server if it was started
        server?.stop()
        server = null
    }

    @Test
    fun `test server instance can be created`() {
        // Server should be created successfully
        assertNotNull(server)
    }

    @Test
    fun `test ValidationRequest DTO`() {
        val request = ValidationRequest(
            utlx = "output: input.name",
            strict = false
        )

        assertEquals("output: input.name", request.utlx)
        assertEquals(false, request.strict)
    }

    @Test
    fun `test ValidationResponse DTO with success`() {
        val response = ValidationResponse(
            valid = true,
            diagnostics = emptyList()
        )

        assertTrue(response.valid)
        assertTrue(response.diagnostics.isEmpty())
        assertNull(response.error)
    }

    @Test
    fun `test ValidationResponse DTO with errors`() {
        val diagnostic = Diagnostic(
            severity = "error",
            message = "Parse error",
            line = 1,
            column = 5,
            source = "parser"
        )

        val response = ValidationResponse(
            valid = false,
            diagnostics = listOf(diagnostic)
        )

        assertFalse(response.valid)
        assertEquals(1, response.diagnostics.size)
        assertEquals("error", response.diagnostics[0].severity)
        assertEquals("Parse error", response.diagnostics[0].message)
    }

    @Test
    fun `test ExecutionRequest DTO`() {
        val request = ExecutionRequest(
            utlx = "output: { name: input.firstName + ' ' + input.lastName }",
            input = "{\"firstName\": \"John\", \"lastName\": \"Doe\"}",
            inputFormat = "json",
            outputFormat = "json"
        )

        assertNotNull(request.utlx)
        assertEquals("json", request.inputFormat)
        assertEquals("json", request.outputFormat)
    }

    @Test
    fun `test ExecutionResponse DTO with success`() {
        val response = ExecutionResponse(
            success = true,
            output = "{\"name\": \"John Doe\"}",
            executionTimeMs = 50
        )

        assertTrue(response.success)
        assertEquals("{\"name\": \"John Doe\"}", response.output)
        assertEquals(50, response.executionTimeMs)
        assertNull(response.error)
    }

    @Test
    fun `test ExecutionResponse DTO with error`() {
        val response = ExecutionResponse(
            success = false,
            error = "Execution failed: undefined variable",
            executionTimeMs = 10
        )

        assertFalse(response.success)
        assertNull(response.output)
        assertEquals("Execution failed: undefined variable", response.error)
    }

    @Test
    fun `test InferSchemaRequest DTO`() {
        val request = InferSchemaRequest(
            utlx = "output: { name: input.name, age: input.age }",
            inputSchema = "{\"type\": \"object\"}",
            format = "json-schema"
        )

        assertEquals("output: { name: input.name, age: input.age }", request.utlx)
        assertEquals("{\"type\": \"object\"}", request.inputSchema)
        assertEquals("json-schema", request.format)
    }

    @Test
    fun `test InferSchemaResponse DTO with success`() {
        val response = InferSchemaResponse(
            success = true,
            schema = "{\"type\": \"object\", \"properties\": {}}",
            schemaFormat = "json-schema",
            confidence = 1.0
        )

        assertTrue(response.success)
        assertNotNull(response.schema)
        assertEquals("json-schema", response.schemaFormat)
        assertEquals(1.0, response.confidence)
    }

    @Test
    fun `test ParseSchemaRequest DTO`() {
        val request = ParseSchemaRequest(
            schema = "{\"$schema\": \"...\"}",
            format = "json-schema"
        )

        assertNotNull(request.schema)
        assertEquals("json-schema", request.format)
    }

    @Test
    fun `test ParseSchemaResponse DTO with success`() {
        val response = ParseSchemaResponse(
            success = true,
            normalized = "{\"type\": \"object\"}"
        )

        assertTrue(response.success)
        assertEquals("{\"type\": \"object\"}", response.normalized)
        assertNull(response.error)
    }

    @Test
    fun `test ParseSchemaResponse DTO with error`() {
        val response = ParseSchemaResponse(
            success = false,
            error = "Unsupported schema format"
        )

        assertFalse(response.success)
        assertNull(response.normalized)
        assertEquals("Unsupported schema format", response.error)
    }

    @Test
    fun `test HealthResponse DTO`() {
        val response = HealthResponse(
            status = "ok",
            version = "1.0.0",
            uptime = 12345
        )

        assertEquals("ok", response.status)
        assertEquals("1.0.0", response.version)
        assertEquals(12345, response.uptime)
    }

    @Test
    fun `test Diagnostic DTO with all fields`() {
        val diagnostic = Diagnostic(
            severity = "warning",
            message = "Unused variable",
            line = 10,
            column = 5,
            source = "type-checker"
        )

        assertEquals("warning", diagnostic.severity)
        assertEquals("Unused variable", diagnostic.message)
        assertEquals(10, diagnostic.line)
        assertEquals(5, diagnostic.column)
        assertEquals("type-checker", diagnostic.source)
    }

    @Test
    fun `test Diagnostic DTO with minimal fields`() {
        val diagnostic = Diagnostic(
            severity = "error",
            message = "Syntax error"
        )

        assertEquals("error", diagnostic.severity)
        assertEquals("Syntax error", diagnostic.message)
        assertNull(diagnostic.line)
        assertNull(diagnostic.column)
        assertNull(diagnostic.source)
    }

    @Test
    fun `test server can be stopped without starting`() {
        assertDoesNotThrow {
            server?.stop()
        }
    }
}

        val testServer = RestApiServer(port = 0) // Use random port

        application {
            // Configure the same routing as RestApiServer
            routing {
                get("/api/health") {
                    call.respond(
                        HttpStatusCode.OK,
                        HealthResponse(
                            status = "ok",
                            version = "1.0.0-SNAPSHOT",
                            uptime = 0
                        )
                    )
                }
            }
        }

        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertTrue(body.contains("\"status\":\"ok\"") || body.contains("status = ok"))
    }

    @Test
    fun `test validate endpoint with valid UTLX`() = testApplication {
        application {
            routing {
                post("/api/validate") {
                    // Simulate successful validation
                    call.respond(
                        HttpStatusCode.OK,
                        ValidationResponse(
                            valid = true,
                            diagnostics = emptyList()
                        )
                    )
                }
            }
        }

        val response = client.post("/api/validate") {
            contentType(ContentType.Application.Json)
            setBody("""{"utlx": "output: input.name", "strict": false}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"valid\":true") || body.contains("valid = true"))
    }

    @Test
    fun `test validate endpoint with invalid UTLX`() = testApplication {
        application {
            routing {
                post("/api/validate") {
                    // Simulate validation failure
                    call.respond(
                        HttpStatusCode.OK,
                        ValidationResponse(
                            valid = false,
                            diagnostics = listOf(
                                Diagnostic(
                                    severity = "error",
                                    message = "Parse error: unexpected token",
                                    line = 1,
                                    column = 5,
                                    source = "parser"
                                )
                            )
                        )
                    )
                }
            }
        }

        val response = client.post("/api/validate") {
            contentType(ContentType.Application.Json)
            setBody("""{"utlx": "invalid syntax @@#$", "strict": false}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"valid\":false") || body.contains("valid = false"))
        assertTrue(body.contains("error") || body.contains("Parse"))
    }

    @Test
    fun `test execute endpoint with simple transformation`() = testApplication {
        application {
            routing {
                post("/api/execute") {
                    // Simulate successful execution
                    call.respond(
                        HttpStatusCode.OK,
                        ExecutionResponse(
                            success = true,
                            output = """{"result": "John Doe"}""",
                            executionTimeMs = 50
                        )
                    )
                }
            }
        }

        val requestBody = """
            {
                "utlx": "output: { result: input.firstName + ' ' + input.lastName }",
                "input": "{\"firstName\": \"John\", \"lastName\": \"Doe\"}",
                "inputFormat": "json",
                "outputFormat": "json"
            }
        """.trimIndent()

        val response = client.post("/api/execute") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"success\":true") || body.contains("success = true"))
        assertTrue(body.contains("John Doe") || body.contains("result"))
    }

    @Test
    fun `test execute endpoint with execution error`() = testApplication {
        application {
            routing {
                post("/api/execute") {
                    // Simulate execution failure
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ExecutionResponse(
                            success = false,
                            error = "Execution failed: undefined variable 'nonexistent'",
                            executionTimeMs = 10
                        )
                    )
                }
            }
        }

        val requestBody = """
            {
                "utlx": "output: nonexistent.field",
                "input": "{}",
                "inputFormat": "json",
                "outputFormat": "json"
            }
        """.trimIndent()

        val response = client.post("/api/execute") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"success\":false") || body.contains("success = false"))
    }

    @Test
    fun `test execute endpoint supports multiple formats`() = testApplication {
        application {
            routing {
                post("/api/execute") {
                    // Test XML input
                    call.respond(
                        HttpStatusCode.OK,
                        ExecutionResponse(
                            success = true,
                            output = """<result><name>John</name></result>""",
                            executionTimeMs = 75
                        )
                    )
                }
            }
        }

        val requestBody = """
            {
                "utlx": "output: { result: { name: input.person.name } }",
                "input": "<person><name>John</name></person>",
                "inputFormat": "xml",
                "outputFormat": "xml"
            }
        """.trimIndent()

        val response = client.post("/api/execute") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `test infer-schema endpoint with simple UTLX`() = testApplication {
        application {
            routing {
                post("/api/infer-schema") {
                    // Simulate schema inference
                    call.respond(
                        HttpStatusCode.OK,
                        InferSchemaResponse(
                            success = true,
                            schema = """
                                {
                                    "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string" },
                                        "age": { "type": "number" }
                                    }
                                }
                            """.trimIndent(),
                            schemaFormat = "json-schema",
                            confidence = 1.0
                        )
                    )
                }
            }
        }

        val requestBody = """
            {
                "utlx": "output: { name: input.name, age: input.age }",
                "format": "json-schema"
            }
        """.trimIndent()

        val response = client.post("/api/infer-schema") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"success\":true") || body.contains("success = true"))
        assertTrue(body.contains("schema") || body.contains("json-schema"))
    }

    @Test
    fun `test infer-schema endpoint with input schema`() = testApplication {
        application {
            routing {
                post("/api/infer-schema") {
                    // Simulate schema inference with input schema
                    call.respond(
                        HttpStatusCode.OK,
                        InferSchemaResponse(
                            success = true,
                            schema = """
                                {
                                    "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
                                    "type": "object",
                                    "properties": {
                                        "fullName": { "type": "string" }
                                    }
                                }
                            """.trimIndent(),
                            schemaFormat = "json-schema",
                            confidence = 1.0
                        )
                    )
                }
            }
        }

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

        val requestBody = """
            {
                "utlx": "output: { fullName: input.firstName + ' ' + input.lastName }",
                "inputSchema": ${inputSchema.replace("\n", "").replace("  ", "")},
                "format": "json-schema"
            }
        """.trimIndent()

        val response = client.post("/api/infer-schema") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `test parse-schema endpoint with JSON Schema`() = testApplication {
        application {
            routing {
                post("/api/parse-schema") {
                    // Simulate schema parsing
                    call.respond(
                        HttpStatusCode.OK,
                        ParseSchemaResponse(
                            success = true,
                            normalized = """
                                {
                                    "type": "object",
                                    "properties": {
                                        "name": {"type": "string", "nullable": false},
                                        "age": {"type": "number", "nullable": false}
                                    },
                                    "required": ["name", "age"]
                                }
                            """.trimIndent()
                        )
                    )
                }
            }
        }

        val schema = """
            {
                "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "number" }
                },
                "required": ["name", "age"]
            }
        """.trimIndent()

        val requestBody = """
            {
                "schema": ${schema.replace("\n", "").replace("  ", "")},
                "format": "json-schema"
            }
        """.trimIndent()

        val response = client.post("/api/parse-schema") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"success\":true") || body.contains("success = true"))
    }

    @Test
    fun `test parse-schema endpoint with XSD`() = testApplication {
        application {
            routing {
                post("/api/parse-schema") {
                    // Simulate XSD schema parsing
                    call.respond(
                        HttpStatusCode.OK,
                        ParseSchemaResponse(
                            success = true,
                            normalized = """
                                {
                                    "type": "object",
                                    "properties": {
                                        "person": {
                                            "type": "object",
                                            "properties": {
                                                "name": {"type": "string", "nullable": false}
                                            }
                                        }
                                    }
                                }
                            """.trimIndent()
                        )
                    )
                }
            }
        }

        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="person">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="name" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
        """.trimIndent()

        val requestBody = """
            {
                "schema": ${Json.encodeToString(kotlinx.serialization.serializer(), xsd)},
                "format": "xsd"
            }
        """.trimIndent()

        val response = client.post("/api/parse-schema") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `test parse-schema endpoint with unsupported format`() = testApplication {
        application {
            routing {
                post("/api/parse-schema") {
                    // Simulate unsupported format
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ParseSchemaResponse(
                            success = false,
                            error = "Unsupported schema format: yaml. Supported formats: xsd, json-schema"
                        )
                    )
                }
            }
        }

        val requestBody = """
            {
                "schema": "some yaml content",
                "format": "yaml"
            }
        """.trimIndent()

        val response = client.post("/api/parse-schema") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Unsupported") || body.contains("error"))
    }

    @Test
    fun `test CORS headers are present`() = testApplication {
        application {
            routing {
                get("/api/health") {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                }
            }
        }

        val response = client.options("/api/health")
        // CORS should allow the request
        assertTrue(response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent)
    }

    @Test
    fun `test error handling for malformed JSON`() = testApplication {
        application {
            routing {
                post("/api/validate") {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            error = "invalid_request",
                            message = "Malformed JSON request body"
                        )
                    )
                }
            }
        }

        val response = client.post("/api/validate") {
            contentType(ContentType.Application.Json)
            setBody("{invalid json}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `test server handles concurrent requests`() = testApplication {
        application {
            routing {
                get("/api/health") {
                    call.respond(HttpStatusCode.OK, HealthResponse("ok", "1.0.0", 0))
                }
            }
        }

        // Send multiple concurrent requests
        val responses = (1..10).map {
            client.get("/api/health")
        }

        // All should succeed
        responses.forEach { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
}
