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

    // ========== Function Registry Tests ==========

    @Test
    fun `test StandardLibrary exportRegistry returns valid registry`() {
        val registry = org.apache.utlx.stdlib.StandardLibrary.exportRegistry()

        assertNotNull(registry)
        assertNotNull(registry.version)
        assertNotNull(registry.generatedAt)
        assertTrue(registry.totalFunctions > 0, "Registry should contain functions")
        assertFalse(registry.functions.isEmpty(), "Functions list should not be empty")
        assertFalse(registry.categories.isEmpty(), "Categories map should not be empty")
    }

    @Test
    fun `test function registry has expected categories`() {
        val registry = org.apache.utlx.stdlib.StandardLibrary.exportRegistry()

        // Check for common expected categories
        val categoryKeys = registry.categories.keys
        assertTrue(categoryKeys.contains("Array"), "Should have Array category")
        assertTrue(categoryKeys.contains("String"), "Should have String category")
        assertTrue(categoryKeys.contains("Math"), "Should have Math category")
        assertTrue(categoryKeys.contains("Date"), "Should have Date category")
    }

    @Test
    fun `test function registry functions have required fields`() {
        val registry = org.apache.utlx.stdlib.StandardLibrary.exportRegistry()

        // Test first function has all required fields
        val firstFunction = registry.functions.firstOrNull()
        assertNotNull(firstFunction)

        firstFunction?.let { fn ->
            assertNotNull(fn.name, "Function should have name")
            assertNotNull(fn.category, "Function should have category")
            assertNotNull(fn.description, "Function should have description")
            assertNotNull(fn.signature, "Function should have signature")
        }
    }

    @Test
    fun `test function registry categories match function list`() {
        val registry = org.apache.utlx.stdlib.StandardLibrary.exportRegistry()

        // Count functions in categories
        val functionsInCategories = registry.categories.values.sumOf { it.size }

        // Should match total functions in flat list
        assertEquals(registry.functions.size, functionsInCategories,
            "Functions in categories should match total functions")
    }

    @Test
    fun `test function registry has map function in Array category`() {
        val registry = org.apache.utlx.stdlib.StandardLibrary.exportRegistry()

        val arrayFunctions = registry.categories["Array"]
        assertNotNull(arrayFunctions, "Array category should exist")

        val mapFunction = arrayFunctions?.find { it.name == "map" }
        assertNotNull(mapFunction, "map function should exist in Array category")

        mapFunction?.let { fn ->
            assertEquals("Array", fn.category)
            assertTrue(fn.description.isNotEmpty())
            assertTrue(fn.signature.contains("map"))
        }
    }

    @Test
    fun `test function info has parameters and returns`() {
        val registry = org.apache.utlx.stdlib.StandardLibrary.exportRegistry()

        // Find a function with parameters (like map)
        val mapFunction = registry.functions.find { it.name == "map" }
        assertNotNull(mapFunction)

        mapFunction?.let { fn ->
            // map should have parameters
            assertTrue(fn.parameters.isNotEmpty(), "map should have parameters")

            // Check parameter structure
            fn.parameters.forEach { param ->
                assertNotNull(param.name, "Parameter should have name")
                assertNotNull(param.type, "Parameter should have type")
            }

            // Check returns structure if present
            fn.returns?.let { ret ->
                assertNotNull(ret.type, "Return should have type")
            }
        }
    }

    @Test
    fun `test function registry JSON serialization with Jackson`() {
        val registry = org.apache.utlx.stdlib.StandardLibrary.exportRegistry()

        // Use Jackson to serialize (same as endpoint)
        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        val json = jacksonMapper.writeValueAsString(registry)

        assertNotNull(json)
        assertTrue(json.contains("\"version\""))
        assertTrue(json.contains("\"totalFunctions\""))
        assertTrue(json.contains("\"functions\""))
        assertTrue(json.contains("\"categories\""))
    }

    @Test
    fun `test function registry round-trip serialization`() {
        val registry = org.apache.utlx.stdlib.StandardLibrary.exportRegistry()

        // Serialize to JSON
        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        val json = jacksonMapper.writeValueAsString(registry)

        // Deserialize back
        val deserialized = jacksonMapper.readValue(json, org.apache.utlx.stdlib.FunctionRegistry::class.java)

        assertEquals(registry.version, deserialized.version)
        assertEquals(registry.totalFunctions, deserialized.totalFunctions)
        assertEquals(registry.functions.size, deserialized.functions.size)
        assertEquals(registry.categories.size, deserialized.categories.size)
    }

    // ========== Operator Registry Tests ==========

    @Test
    fun `test OperatorRegistry exportRegistry returns valid registry`() {
        val registry = org.apache.utlx.stdlib.OperatorRegistry.exportRegistry()

        assertNotNull(registry)
        assertNotNull(registry.version)
        assertTrue(registry.count > 0, "Registry should contain operators")
        assertFalse(registry.operators.isEmpty(), "Operators list should not be empty")
        assertEquals(registry.operators.size, registry.count, "Count should match operators list size")
    }

    @Test
    fun `test operator registry has expected count`() {
        val registry = org.apache.utlx.stdlib.OperatorRegistry.exportRegistry()

        // We expect 20 operators
        assertEquals(20, registry.count, "Should have 20 operators")
        assertEquals(20, registry.operators.size, "Operators list should have 20 items")
    }

    @Test
    fun `test operator registry operators have required fields`() {
        val registry = org.apache.utlx.stdlib.OperatorRegistry.exportRegistry()

        // Test first operator has all required fields
        val firstOperator = registry.operators.firstOrNull()
        assertNotNull(firstOperator)

        firstOperator?.let { op ->
            assertNotNull(op.symbol, "Operator should have symbol")
            assertNotNull(op.name, "Operator should have name")
            assertNotNull(op.category, "Operator should have category")
            assertNotNull(op.description, "Operator should have description")
            assertNotNull(op.syntax, "Operator should have syntax")
            assertTrue(op.precedence > 0, "Operator should have positive precedence")
            assertTrue(op.associativity in listOf("left", "right"), "Operator should have valid associativity")
            assertNotNull(op.examples, "Operator should have examples")
            assertNotNull(op.tooltip, "Operator should have tooltip")
        }
    }

    @Test
    fun `test operator registry has expected categories`() {
        val registry = org.apache.utlx.stdlib.OperatorRegistry.exportRegistry()

        // Group by category to verify all expected categories exist
        val categories = registry.operators.groupBy { it.category }

        assertTrue(categories.containsKey("Arithmetic"), "Should have Arithmetic category")
        assertTrue(categories.containsKey("Comparison"), "Should have Comparison category")
        assertTrue(categories.containsKey("Logical"), "Should have Logical category")
        assertTrue(categories.containsKey("Special"), "Should have Special category")
    }

    @Test
    fun `test operator registry has expected operators`() {
        val registry = org.apache.utlx.stdlib.OperatorRegistry.exportRegistry()

        val symbols = registry.operators.map { it.symbol }

        // Test some common operators
        assertTrue(symbols.contains("+"), "Should have + operator")
        assertTrue(symbols.contains("-"), "Should have - operator")
        assertTrue(symbols.contains("*"), "Should have * operator")
        assertTrue(symbols.contains("/"), "Should have / operator")
        assertTrue(symbols.contains("=="), "Should have == operator")
        assertTrue(symbols.contains("!="), "Should have != operator")
        assertTrue(symbols.contains("&&"), "Should have && operator")
        assertTrue(symbols.contains("||"), "Should have || operator")
        assertTrue(symbols.contains("|>"), "Should have |> operator")
    }

    @Test
    fun `test operator registry JSON serialization with Jackson`() {
        val registry = org.apache.utlx.stdlib.OperatorRegistry.exportRegistry()

        // Use Jackson to serialize (same as endpoint)
        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        val json = jacksonMapper.writeValueAsString(registry)

        assertNotNull(json)
        assertTrue(json.contains("\"version\""))
        assertTrue(json.contains("\"count\""))
        assertTrue(json.contains("\"operators\""))
        assertTrue(json.contains("\"symbol\""))
        assertTrue(json.contains("\"precedence\""))
    }

    @Test
    fun `test operator registry round-trip serialization`() {
        val registry = org.apache.utlx.stdlib.OperatorRegistry.exportRegistry()

        // Serialize to JSON
        val jacksonMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

        val json = jacksonMapper.writeValueAsString(registry)

        // Deserialize back
        val deserialized = jacksonMapper.readValue(json, org.apache.utlx.stdlib.OperatorRegistry.OperatorRegistryData::class.java)

        assertEquals(registry.version, deserialized.version)
        assertEquals(registry.count, deserialized.count)
        assertEquals(registry.operators.size, deserialized.operators.size)
    }

    @Test
    fun `test operator has correct precedence and associativity`() {
        val registry = org.apache.utlx.stdlib.OperatorRegistry.exportRegistry()

        // Find addition operator
        val plusOp = registry.operators.find { it.symbol == "+" }
        assertNotNull(plusOp)

        plusOp?.let { op ->
            assertEquals(5, op.precedence, "+ should have precedence 5")
            assertEquals("left", op.associativity, "+ should be left-associative")
        }

        // Find pipe operator
        val pipeOp = registry.operators.find { it.symbol == "|>" }
        assertNotNull(pipeOp)

        pipeOp?.let { op ->
            assertEquals(12, op.precedence, "|> should have precedence 12")
            assertEquals("right", op.associativity, "|> should be right-associative")
        }
    }
}
