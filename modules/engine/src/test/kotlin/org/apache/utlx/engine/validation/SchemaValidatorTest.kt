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
    // YAML Schema Validator (JSON Schema applied to YAML payloads)
    // =========================================================================

    @Test
    fun `YAML - valid YAML payload passes`() {
        val validator = YamlSchemaValidator(personSchema)
        val yaml = "name: Alice\nage: 30\n"
        val errors = validator.validate(yaml.toByteArray(), "application/yaml")
        assertTrue(errors.isEmpty(), "Valid YAML should pass: $errors")
    }

    @Test
    fun `YAML - missing required field fails`() {
        val validator = YamlSchemaValidator(personSchema)
        val yaml = "name: Alice\n"
        val errors = validator.validate(yaml.toByteArray(), "application/yaml")
        assertTrue(errors.isNotEmpty(), "Missing 'age' should fail")
    }

    @Test
    fun `YAML - wrong type fails`() {
        val validator = YamlSchemaValidator(personSchema)
        val yaml = "name: Alice\nage: thirty\n"
        val errors = validator.validate(yaml.toByteArray(), "application/yaml")
        assertTrue(errors.isNotEmpty(), "String age should fail integer check")
    }

    @Test
    fun `YAML - invalid YAML returns parse error`() {
        val validator = YamlSchemaValidator(personSchema)
        val errors = validator.validate(":::bad yaml[[[".toByteArray(), "application/yaml")
        assertTrue(errors.isNotEmpty(), "Invalid YAML should produce error")
    }

    // =========================================================================
    // Table Schema (TSCH) Validator
    // =========================================================================

    private val tableSchema = """
    {
      "fields": [
        {"name": "id", "type": "integer", "constraints": {"required": true}},
        {"name": "name", "type": "string", "constraints": {"required": true, "minLength": 1, "maxLength": 50}},
        {"name": "score", "type": "number", "constraints": {"minimum": 0, "maximum": 100}},
        {"name": "status", "type": "string", "constraints": {"enum": ["active", "inactive"]}}
      ]
    }
    """.trimIndent()

    @Test
    fun `TSCH - valid row passes`() {
        val validator = TableSchemaValidator(tableSchema)
        val errors = validator.validate(
            """{"id": 1, "name": "Alice", "score": 85, "status": "active"}""".toByteArray(),
            "text/csv"
        )
        assertTrue(errors.isEmpty(), "Valid row should pass: $errors")
    }

    @Test
    fun `TSCH - missing required field fails`() {
        val validator = TableSchemaValidator(tableSchema)
        val errors = validator.validate(
            """{"name": "Alice"}""".toByteArray(),
            "text/csv"
        )
        assertTrue(errors.any { it.message.contains("id") }, "Missing 'id' should fail: $errors")
    }

    @Test
    fun `TSCH - wrong type fails`() {
        val validator = TableSchemaValidator(tableSchema)
        val errors = validator.validate(
            """{"id": "not-a-number", "name": "Alice"}""".toByteArray(),
            "text/csv"
        )
        assertTrue(errors.isNotEmpty(), "String id should fail integer check")
    }

    @Test
    fun `TSCH - enum constraint fails`() {
        val validator = TableSchemaValidator(tableSchema)
        val errors = validator.validate(
            """{"id": 1, "name": "Alice", "status": "deleted"}""".toByteArray(),
            "text/csv"
        )
        assertTrue(errors.any { it.message.contains("deleted") }, "Invalid enum should fail: $errors")
    }

    @Test
    fun `TSCH - maxLength constraint fails`() {
        val validator = TableSchemaValidator(tableSchema)
        val longName = "A".repeat(51)
        val errors = validator.validate(
            """{"id": 1, "name": "$longName"}""".toByteArray(),
            "text/csv"
        )
        assertTrue(errors.any { it.message.contains("maxLength") || it.message.contains("exceeds") }, "Long name should fail: $errors")
    }

    @Test
    fun `TSCH - min max value constraint fails`() {
        val validator = TableSchemaValidator(tableSchema)
        val errors = validator.validate(
            """{"id": 1, "name": "Alice", "score": 150}""".toByteArray(),
            "text/csv"
        )
        assertTrue(errors.any { it.message.contains("maximum") || it.message.contains("exceeds") }, "Score > 100 should fail: $errors")
    }

    @Test
    fun `TSCH - validates array of rows`() {
        val validator = TableSchemaValidator(tableSchema)
        val errors = validator.validate(
            """[{"id": 1, "name": "Alice"}, {"id": 2, "name": "Bob"}]""".toByteArray(),
            "text/csv"
        )
        assertTrue(errors.isEmpty(), "Valid rows should pass: $errors")
    }

    // =========================================================================
    // OData Schema (OSCH) Validator
    // =========================================================================

    private val odataSchema = """
    {
      "entityTypes": {
        "Customer": {
          "properties": {
            "ID": {"type": "Edm.Int32", "nullable": false},
            "Name": {"type": "Edm.String", "nullable": false, "maxLength": 100},
            "Email": {"type": "Edm.String", "nullable": true},
            "Active": {"type": "Edm.Boolean", "nullable": true}
          }
        }
      }
    }
    """.trimIndent()

    @Test
    fun `OSCH - valid entity passes`() {
        val validator = ODataSchemaValidator(odataSchema)
        val errors = validator.validate(
            """{"ID": 1, "Name": "Contoso", "Email": "info@contoso.com", "Active": true}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.isEmpty(), "Valid OData entity should pass: $errors")
    }

    @Test
    fun `OSCH - missing non-nullable property fails`() {
        val validator = ODataSchemaValidator(odataSchema)
        val errors = validator.validate(
            """{"ID": 1}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.any { it.message.contains("Name") }, "Missing non-nullable Name should fail: $errors")
    }

    @Test
    fun `OSCH - wrong Edm type fails`() {
        val validator = ODataSchemaValidator(odataSchema)
        val errors = validator.validate(
            """{"ID": "not-a-number", "Name": "Contoso"}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.any { it.message.contains("Edm.Int32") }, "String ID should fail Int32 check: $errors")
    }

    @Test
    fun `OSCH - maxLength exceeded fails`() {
        val validator = ODataSchemaValidator(odataSchema)
        val longName = "A".repeat(101)
        val errors = validator.validate(
            """{"ID": 1, "Name": "$longName"}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.any { it.message.contains("maxLength") }, "Name > 100 should fail: $errors")
    }

    @Test
    fun `OSCH - nullable property with null value passes`() {
        val validator = ODataSchemaValidator(odataSchema)
        val errors = validator.validate(
            """{"ID": 1, "Name": "Contoso", "Email": null}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.isEmpty(), "Null on nullable field should pass: $errors")
    }

    // =========================================================================
    // Protobuf Schema Validator
    // =========================================================================

    private val protoSchema = """
    {
      "messageName": "Order",
      "fields": {
        "id": {"type": "string", "number": 1, "label": "SINGULAR"},
        "total": {"type": "int64", "number": 2, "label": "SINGULAR"},
        "active": {"type": "bool", "number": 3, "label": "SINGULAR"},
        "items": {"type": "OrderItem", "number": 4, "label": "REPEATED"},
        "tags": {"type": "string", "number": 5, "label": "MAP"}
      }
    }
    """.trimIndent()

    @Test
    fun `Protobuf - valid payload passes`() {
        val validator = ProtobufValidator(protoSchema)
        val errors = validator.validate(
            """{"id": "order-1", "total": 1000, "active": true, "items": [{}], "tags": {"key": "val"}}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.isEmpty(), "Valid protobuf payload should pass: $errors")
    }

    @Test
    fun `Protobuf - wrong type fails`() {
        val validator = ProtobufValidator(protoSchema)
        val errors = validator.validate(
            """{"id": 123, "total": 1000}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.any { it.message.contains("string") }, "Number id should fail string check: $errors")
    }

    @Test
    fun `Protobuf - repeated field not array fails`() {
        val validator = ProtobufValidator(protoSchema)
        val errors = validator.validate(
            """{"id": "order-1", "total": 1000, "items": "not-array"}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.any { it.message.contains("repeated") || it.message.contains("array") }, "Non-array repeated should fail: $errors")
    }

    @Test
    fun `Protobuf - map field not object fails`() {
        val validator = ProtobufValidator(protoSchema)
        val errors = validator.validate(
            """{"id": "order-1", "tags": "not-object"}""".toByteArray(),
            "application/json"
        )
        assertTrue(errors.any { it.message.contains("map") || it.message.contains("object") }, "Non-object map should fail: $errors")
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
    fun `factory creates YAML validator`() {
        val validator = SchemaValidatorFactory.create(personSchema, "yaml")
        assertEquals("yaml", validator.schemaFormat)
    }

    @Test
    fun `factory creates TSCH validator`() {
        val validator = SchemaValidatorFactory.create(tableSchema, "tsch")
        assertEquals("tsch", validator.schemaFormat)
    }

    @Test
    fun `factory creates OSCH validator`() {
        val validator = SchemaValidatorFactory.create(odataSchema, "osch")
        assertEquals("osch", validator.schemaFormat)
    }

    @Test
    fun `factory creates Protobuf validator`() {
        val validator = SchemaValidatorFactory.create(protoSchema, "protobuf")
        assertEquals("protobuf", validator.schemaFormat)
    }

    @Test
    fun `factory creates Protobuf validator via proto alias`() {
        val validator = SchemaValidatorFactory.create(protoSchema, "proto")
        assertEquals("protobuf", validator.schemaFormat)
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
