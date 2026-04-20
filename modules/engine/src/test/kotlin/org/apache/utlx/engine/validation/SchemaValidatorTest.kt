package org.apache.utlx.engine.validation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaValidatorTest {

    // =========================================================================
    // JSON Schema Validator
    // =========================================================================

    private val personSchema = """
    {
      "${'$'}schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "required": ["name", "age"],
      "properties": {
        "name": { "type": "string", "minLength": 1 },
        "age": { "type": "integer", "minimum": 0 },
        "email": { "type": "string", "format": "email" }
      },
      "additionalProperties": false
    }
    """.trimIndent()

    @Test
    fun `JSON Schema - valid payload passes`() {
        val validator = JsonSchemaValidator(personSchema)
        val errors = validator.validate(
            """{"name": "Alice", "age": 30}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.isEmpty(), "Valid payload should produce no errors: $errors")
    }

    @Test
    fun `JSON Schema - missing required field fails`() {
        val validator = JsonSchemaValidator(personSchema)
        val errors = validator.validate(
            """{"name": "Alice"}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.isNotEmpty(), "Missing 'age' should produce errors")
        assertTrue(errors.any { it.message.contains("age") }, "Error should mention 'age': $errors")
    }

    @Test
    fun `JSON Schema - wrong type fails`() {
        val validator = JsonSchemaValidator(personSchema)
        val errors = validator.validate(
            """{"name": "Alice", "age": "thirty"}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.isNotEmpty(), "String age should fail integer check")
    }

    @Test
    fun `JSON Schema - additional property fails`() {
        val validator = JsonSchemaValidator(personSchema)
        val errors = validator.validate(
            """{"name": "Alice", "age": 30, "phone": "123"}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.isNotEmpty(), "Additional property should fail")
    }

    @Test
    fun `JSON Schema - invalid JSON returns parse error`() {
        val validator = JsonSchemaValidator(personSchema)
        val errors = validator.validate(
            "not json".toByteArray(),
            "application/json"
        )
        assertTrue(errors.isNotEmpty(), "Invalid JSON should produce error")
        assertTrue(errors.first().message.contains("parse") || errors.first().message.contains("JSON"),
            "Error should mention parse/JSON failure")
    }

    @Test
    fun `JSON Schema - validator is reusable across multiple calls`() {
        val validator = JsonSchemaValidator(personSchema)

        val valid = validator.validate("""{"name": "A", "age": 1}""".toByteArray(), "")
        assertTrue(valid.isEmpty())

        val invalid = validator.validate("""{"name": "B"}""".toByteArray(), "")
        assertTrue(invalid.isNotEmpty())

        val valid2 = validator.validate("""{"name": "C", "age": 2}""".toByteArray(), "")
        assertTrue(valid2.isEmpty(), "Validator should be reusable and stateless")
    }

    // =========================================================================
    // XSD Validator
    // =========================================================================

    private val personXsd = """
    <?xml version="1.0" encoding="UTF-8"?>
    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
      <xs:element name="person">
        <xs:complexType>
          <xs:sequence>
            <xs:element name="name" type="xs:string"/>
            <xs:element name="age" type="xs:integer"/>
          </xs:sequence>
        </xs:complexType>
      </xs:element>
    </xs:schema>
    """.trimIndent()

    @Test
    fun `XSD - valid XML passes`() {
        val validator = XsdValidator(personXsd)
        val xml = """<person><name>Alice</name><age>30</age></person>"""
        val errors = validator.validate(xml.toByteArray(), "application/xml")
        assertTrue(errors.isEmpty(), "Valid XML should pass: $errors")
    }

    @Test
    fun `XSD - missing required element fails`() {
        val validator = XsdValidator(personXsd)
        val xml = """<person><name>Alice</name></person>"""
        val errors = validator.validate(xml.toByteArray(), "application/xml")
        assertTrue(errors.isNotEmpty(), "Missing 'age' element should fail")
    }

    @Test
    fun `XSD - wrong element type fails`() {
        val validator = XsdValidator(personXsd)
        val xml = """<person><name>Alice</name><age>not-a-number</age></person>"""
        val errors = validator.validate(xml.toByteArray(), "application/xml")
        assertTrue(errors.isNotEmpty(), "Non-integer age should fail")
    }

    @Test
    fun `XSD - invalid XML returns error`() {
        val validator = XsdValidator(personXsd)
        val errors = validator.validate("not xml".toByteArray(), "application/xml")
        assertTrue(errors.isNotEmpty(), "Invalid XML should produce error")
    }

    // =========================================================================
    // Avro Schema Validator
    // =========================================================================

    private val avroPersonSchema = """
    {
      "type": "record",
      "name": "Person",
      "fields": [
        {"name": "name", "type": "string"},
        {"name": "age", "type": "int"}
      ]
    }
    """.trimIndent()

    @Test
    fun `Avro - valid JSON payload passes`() {
        val validator = AvroSchemaValidator(avroPersonSchema)
        val errors = validator.validate(
            """{"name": "Alice", "age": 30}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.isEmpty(), "Valid Avro JSON should pass: $errors")
    }

    @Test
    fun `Avro - missing required field fails`() {
        val validator = AvroSchemaValidator(avroPersonSchema)
        val errors = validator.validate(
            """{"name": "Alice"}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.isNotEmpty(), "Missing 'age' should fail Avro validation")
    }

    // =========================================================================
    // SchemaValidatorFactory
    // =========================================================================

    @Test
    fun `factory creates JSON Schema validator`() {
        val validator = SchemaValidatorFactory.create(personSchema, "json-schema")
        assertEquals("json-schema", validator.schemaFormat)
    }

    @Test
    fun `factory creates JSON Schema validator via jsch alias`() {
        val validator = SchemaValidatorFactory.create(personSchema, "jsch")
        assertEquals("json-schema", validator.schemaFormat)
    }

    @Test
    fun `factory creates XSD validator`() {
        val validator = SchemaValidatorFactory.create(personXsd, "xsd")
        assertEquals("xsd", validator.schemaFormat)
    }

    @Test
    fun `factory creates Avro validator`() {
        val validator = SchemaValidatorFactory.create(avroPersonSchema, "avro")
        assertEquals("avro", validator.schemaFormat)
    }

    @Test
    fun `factory rejects unsupported format`() {
        try {
            SchemaValidatorFactory.create("{}", "unknown-format")
            throw AssertionError("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Unsupported"))
        }
    }
}
