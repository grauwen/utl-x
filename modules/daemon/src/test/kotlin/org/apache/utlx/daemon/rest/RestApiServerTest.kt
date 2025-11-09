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

    // ========== Multipart Execution Tests ==========

    @Test
    fun `test MultipartInputMetadata DTO with default values`() {
        val metadata = MultipartInputMetadata(
            name = "customers",
            format = "json"
        )

        assertEquals("customers", metadata.name)
        assertEquals("json", metadata.format)
        assertEquals("UTF-8", metadata.encoding)
        assertFalse(metadata.hasBOM)
    }

    @Test
    fun `test MultipartInputMetadata DTO with UTF-8 and BOM`() {
        val metadata = MultipartInputMetadata(
            name = "input1",
            format = "xml",
            encoding = "UTF-8",
            hasBOM = true
        )

        assertEquals("input1", metadata.name)
        assertEquals("xml", metadata.format)
        assertEquals("UTF-8", metadata.encoding)
        assertTrue(metadata.hasBOM)
    }

    @Test
    fun `test MultipartInputMetadata DTO with UTF-16LE`() {
        val metadata = MultipartInputMetadata(
            name = "data",
            format = "csv",
            encoding = "UTF-16LE",
            hasBOM = false
        )

        assertEquals("data", metadata.name)
        assertEquals("csv", metadata.format)
        assertEquals("UTF-16LE", metadata.encoding)
        assertFalse(metadata.hasBOM)
    }

    @Test
    fun `test MultipartInputMetadata DTO with UTF-16BE and BOM`() {
        val metadata = MultipartInputMetadata(
            name = "records",
            format = "yaml",
            encoding = "UTF-16BE",
            hasBOM = true
        )

        assertEquals("records", metadata.name)
        assertEquals("yaml", metadata.format)
        assertEquals("UTF-16BE", metadata.encoding)
        assertTrue(metadata.hasBOM)
    }

    @Test
    fun `test MultipartInputMetadata DTO with ISO-8859-1`() {
        val metadata = MultipartInputMetadata(
            name = "legacy_data",
            format = "json",
            encoding = "ISO-8859-1",
            hasBOM = false
        )

        assertEquals("legacy_data", metadata.name)
        assertEquals("json", metadata.format)
        assertEquals("ISO-8859-1", metadata.encoding)
        assertFalse(metadata.hasBOM)
    }

    @Test
    fun `test MultipartInputMetadata DTO with Windows-1252`() {
        val metadata = MultipartInputMetadata(
            name = "windows_file",
            format = "csv",
            encoding = "Windows-1252",
            hasBOM = false
        )

        assertEquals("windows_file", metadata.name)
        assertEquals("csv", metadata.format)
        assertEquals("Windows-1252", metadata.encoding)
        assertFalse(metadata.hasBOM)
    }

    @Test
    fun `test MultipartInputMetadata with XSD schema format`() {
        val metadata = MultipartInputMetadata(
            name = "schema",
            format = "xsd",
            encoding = "UTF-8",
            hasBOM = false
        )

        assertEquals("schema", metadata.name)
        assertEquals("xsd", metadata.format)
        assertEquals("UTF-8", metadata.encoding)
        assertFalse(metadata.hasBOM)
    }

    @Test
    fun `test MultipartInputMetadata with JSON Schema format`() {
        val metadata = MultipartInputMetadata(
            name = "json_schema",
            format = "jsch",
            encoding = "UTF-8",
            hasBOM = false
        )

        assertEquals("json_schema", metadata.name)
        assertEquals("jsch", metadata.format)
        assertEquals("UTF-8", metadata.encoding)
        assertFalse(metadata.hasBOM)
    }

    @Test
    fun `test MultipartInputMetadata with Avro schema format`() {
        val metadata = MultipartInputMetadata(
            name = "avro_schema",
            format = "avro",
            encoding = "UTF-8",
            hasBOM = false
        )

        assertEquals("avro_schema", metadata.name)
        assertEquals("avro", metadata.format)
        assertEquals("UTF-8", metadata.encoding)
        assertFalse(metadata.hasBOM)
    }

    @Test
    fun `test MultipartInputMetadata with Protobuf schema format`() {
        val metadata = MultipartInputMetadata(
            name = "proto_schema",
            format = "proto",
            encoding = "UTF-8",
            hasBOM = false
        )

        assertEquals("proto_schema", metadata.name)
        assertEquals("proto", metadata.format)
        assertEquals("UTF-8", metadata.encoding)
        assertFalse(metadata.hasBOM)
    }

    @Test
    fun `test multiple MultipartInputMetadata instances with different encodings`() {
        val inputs = listOf(
            MultipartInputMetadata("file1", "json", "UTF-8", false),
            MultipartInputMetadata("file2", "xml", "UTF-16LE", true),
            MultipartInputMetadata("file3", "csv", "ISO-8859-1", false)
        )

        assertEquals(3, inputs.size)
        assertEquals("UTF-8", inputs[0].encoding)
        assertEquals("UTF-16LE", inputs[1].encoding)
        assertTrue(inputs[1].hasBOM)
        assertEquals("ISO-8859-1", inputs[2].encoding)
    }
}
