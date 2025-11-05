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
        val schemaContent = """{"${'$'}schema": "..."}"""
        val request = ParseSchemaRequest(
            schema = schemaContent,
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
