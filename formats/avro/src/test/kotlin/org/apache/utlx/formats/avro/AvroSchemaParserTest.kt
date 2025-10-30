package org.apache.utlx.formats.avro

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for AvroSchemaParser
 *
 * Tests cover:
 * - Basic Avro schema parsing (JSON to UDM)
 * - Record type parsing
 * - Enum type parsing
 * - Primitive and logical types
 * - Union types (nullable fields)
 * - Array and map types
 * - toUSDL() conversion (Avro schema to USDL)
 * - Round-trip transformation (USDL -> Avro -> USDL)
 */
class AvroSchemaParserTest {

    // ========== Basic Parsing Tests ==========

    @Test
    fun `parse simple Avro record schema`() {
        // Given: A simple Avro record schema
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "Person",
              "namespace": "com.example",
              "fields": [
                {"name": "name", "type": "string"},
                {"name": "age", "type": "int"}
              ]
            }
        """.trimIndent()

        // When: Parse
        val parser = AvroSchemaParser()
        val udm = parser.parse(avroSchemaJson)

        // Then: Should be parsed as UDM.Object
        assertTrue(udm is UDM.Object)
        val obj = udm as UDM.Object

        // Verify record structure
        assertEquals("record", (obj.properties["type"] as? UDM.Scalar)?.value)
        assertEquals("Person", (obj.properties["name"] as? UDM.Scalar)?.value)
        assertEquals("com.example", (obj.properties["namespace"] as? UDM.Scalar)?.value)

        // Verify fields array
        val fields = obj.properties["fields"] as? UDM.Array
        assertNotNull(fields)
        assertEquals(2, fields!!.elements.size)
    }

    @Test
    fun `parse Avro enum schema`() {
        // Given: An Avro enum schema
        val avroSchemaJson = """
            {
              "type": "enum",
              "name": "Status",
              "namespace": "com.example",
              "symbols": ["ACTIVE", "INACTIVE", "PENDING"]
            }
        """.trimIndent()

        // When: Parse
        val parser = AvroSchemaParser()
        val udm = parser.parse(avroSchemaJson)

        // Then: Should contain enum data
        assertTrue(udm is UDM.Object)
        val obj = udm as UDM.Object

        assertEquals("enum", (obj.properties["type"] as? UDM.Scalar)?.value)
        assertEquals("Status", (obj.properties["name"] as? UDM.Scalar)?.value)

        val symbols = obj.properties["symbols"] as? UDM.Array
        assertNotNull(symbols)
        assertEquals(3, symbols!!.elements.size)
    }

    @Test
    fun `parse Avro schema with logical type`() {
        // Given: Avro schema with timestamp-millis logical type
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "Event",
              "fields": [
                {
                  "name": "timestamp",
                  "type": {
                    "type": "long",
                    "logicalType": "timestamp-millis"
                  }
                }
              ]
            }
        """.trimIndent()

        // When: Parse
        val parser = AvroSchemaParser()
        val udm = parser.parse(avroSchemaJson)

        // Then: Should preserve logical type
        assertTrue(udm is UDM.Object)
        val obj = udm as UDM.Object

        val fields = obj.properties["fields"] as? UDM.Array
        assertNotNull(fields)

        val field = fields!!.elements[0] as UDM.Object
        val fieldType = field.properties["type"] as UDM.Object
        assertEquals("timestamp-millis", (fieldType.properties["logicalType"] as? UDM.Scalar)?.value)
    }

    @Test
    fun `parse Avro schema with nullable field (union)`() {
        // Given: Avro schema with nullable field
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "Person",
              "fields": [
                {"name": "email", "type": ["null", "string"]}
              ]
            }
        """.trimIndent()

        // When: Parse
        val parser = AvroSchemaParser()
        val udm = parser.parse(avroSchemaJson)

        // Then: Should preserve union type
        assertTrue(udm is UDM.Object)
        val obj = udm as UDM.Object

        val fields = obj.properties["fields"] as? UDM.Array
        val field = fields!!.elements[0] as UDM.Object
        val fieldType = field.properties["type"]

        assertTrue(fieldType is UDM.Array)
        val union = fieldType as UDM.Array
        assertEquals(2, union.elements.size)
    }

    @Test
    fun `parse Avro schema with array type`() {
        // Given: Avro schema with array field
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "Person",
              "fields": [
                {
                  "name": "tags",
                  "type": {
                    "type": "array",
                    "items": "string"
                  }
                }
              ]
            }
        """.trimIndent()

        // When: Parse
        val parser = AvroSchemaParser()
        val udm = parser.parse(avroSchemaJson)

        // Then: Should preserve array structure
        assertTrue(udm is UDM.Object)
        val obj = udm as UDM.Object

        val fields = obj.properties["fields"] as? UDM.Array
        val field = fields!!.elements[0] as UDM.Object
        val fieldType = field.properties["type"] as UDM.Object

        assertEquals("array", (fieldType.properties["type"] as? UDM.Scalar)?.value)
        assertEquals("string", (fieldType.properties["items"] as? UDM.Scalar)?.value)
    }

    @Test
    fun `parse Avro schema with map type`() {
        // Given: Avro schema with map field
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "Person",
              "fields": [
                {
                  "name": "metadata",
                  "type": {
                    "type": "map",
                    "values": "string"
                  }
                }
              ]
            }
        """.trimIndent()

        // When: Parse
        val parser = AvroSchemaParser()
        val udm = parser.parse(avroSchemaJson)

        // Then: Should preserve map structure
        assertTrue(udm is UDM.Object)
        val obj = udm as UDM.Object

        val fields = obj.properties["fields"] as? UDM.Array
        val field = fields!!.elements[0] as UDM.Object
        val fieldType = field.properties["type"] as UDM.Object

        assertEquals("map", (fieldType.properties["type"] as? UDM.Scalar)?.value)
        assertEquals("string", (fieldType.properties["values"] as? UDM.Scalar)?.value)
    }

    // ========== toUSDL() Conversion Tests ==========

    @Test
    fun `convert Avro record to USDL`() {
        // Given: Avro schema as UDM
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "Person",
              "namespace": "com.example",
              "doc": "A person record",
              "fields": [
                {"name": "name", "type": "string"},
                {"name": "age", "type": "int"}
              ]
            }
        """.trimIndent()

        val parser = AvroSchemaParser()
        val avroUdm = parser.parse(avroSchemaJson)

        // When: Convert to USDL
        val usdl = parser.toUSDL(avroUdm)

        // Then: Should have USDL structure
        assertTrue(usdl is UDM.Object)
        val obj = usdl as UDM.Object

        // Should have %types directive
        val types = obj.properties["%types"] as? UDM.Object
        assertNotNull(types)

        // Should have Person type definition
        val personType = types!!.properties["Person"] as? UDM.Object
        assertNotNull(personType)

        // Verify type definition
        assertEquals("structure", (personType!!.properties["%kind"] as? UDM.Scalar)?.value)
        assertEquals("com.example", (personType.properties["%namespace"] as? UDM.Scalar)?.value)
        assertEquals("A person record", (personType.properties["%documentation"] as? UDM.Scalar)?.value)

        // Verify fields
        val fields = personType.properties["%fields"] as? UDM.Array
        assertNotNull(fields)
        assertEquals(2, fields!!.elements.size)
    }

    @Test
    fun `convert Avro enum to USDL`() {
        // Given: Avro enum schema
        val avroSchemaJson = """
            {
              "type": "enum",
              "name": "Status",
              "namespace": "com.example",
              "doc": "Status enumeration",
              "symbols": ["ACTIVE", "INACTIVE"]
            }
        """.trimIndent()

        val parser = AvroSchemaParser()
        val avroUdm = parser.parse(avroSchemaJson)

        // When: Convert to USDL
        val usdl = parser.toUSDL(avroUdm)

        // Then: Should have USDL enumeration structure
        assertTrue(usdl is UDM.Object)
        val obj = usdl as UDM.Object

        val types = obj.properties["%types"] as? UDM.Object
        assertNotNull(types)

        val statusType = types!!.properties["Status"] as? UDM.Object
        assertNotNull(statusType)

        assertEquals("enumeration", (statusType!!.properties["%kind"] as? UDM.Scalar)?.value)
        assertEquals("Status enumeration", (statusType.properties["%documentation"] as? UDM.Scalar)?.value)

        val values = statusType.properties["%values"] as? UDM.Array
        assertNotNull(values)
        assertEquals(2, values!!.elements.size)
    }

    @Test
    fun `convert Avro field with logical type to USDL`() {
        // Given: Avro schema with uuid logical type
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "Person",
              "fields": [
                {
                  "name": "id",
                  "type": {
                    "type": "string",
                    "logicalType": "uuid"
                  }
                }
              ]
            }
        """.trimIndent()

        val parser = AvroSchemaParser()
        val avroUdm = parser.parse(avroSchemaJson)

        // When: Convert to USDL
        val usdl = parser.toUSDL(avroUdm)

        // Then: Should preserve logical type
        val obj = usdl as UDM.Object
        val types = obj.properties["%types"] as UDM.Object
        val personType = types.properties["Person"] as UDM.Object
        val fields = personType.properties["%fields"] as UDM.Array
        val field = fields.elements[0] as UDM.Object

        assertEquals("uuid", (field.properties["%logicalType"] as? UDM.Scalar)?.value)
    }

    @Test
    fun `convert nullable Avro field to USDL optional field`() {
        // Given: Avro schema with nullable field
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "Person",
              "fields": [
                {
                  "name": "email",
                  "type": ["null", "string"],
                  "default": null
                }
              ]
            }
        """.trimIndent()

        val parser = AvroSchemaParser()
        val avroUdm = parser.parse(avroSchemaJson)

        // When: Convert to USDL
        val usdl = parser.toUSDL(avroUdm)

        // Then: Should mark field as not required
        val obj = usdl as UDM.Object
        val types = obj.properties["%types"] as UDM.Object
        val personType = types.properties["Person"] as UDM.Object
        val fields = personType.properties["%fields"] as UDM.Array
        val field = fields.elements[0] as UDM.Object

        // Field should not have %required=true (nullable)
        val required = field.properties["%required"] as? UDM.Scalar
        if (required != null) {
            assertFalse(required.value as Boolean)
        }
    }

    @Test
    fun `convert Avro array field to USDL array field`() {
        // Given: Avro schema with array field
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "Person",
              "fields": [
                {
                  "name": "tags",
                  "type": {
                    "type": "array",
                    "items": "string"
                  }
                }
              ]
            }
        """.trimIndent()

        val parser = AvroSchemaParser()
        val avroUdm = parser.parse(avroSchemaJson)

        // When: Convert to USDL
        val usdl = parser.toUSDL(avroUdm)

        // Then: Should have %array directive
        val obj = usdl as UDM.Object
        val types = obj.properties["%types"] as UDM.Object
        val personType = types.properties["Person"] as UDM.Object
        val fields = personType.properties["%fields"] as UDM.Array
        val field = fields.elements[0] as UDM.Object

        assertEquals(true, (field.properties["%array"] as? UDM.Scalar)?.value)
    }

    @Test
    fun `convert Avro map field to USDL map field`() {
        // Given: Avro schema with map field
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "Person",
              "fields": [
                {
                  "name": "metadata",
                  "type": {
                    "type": "map",
                    "values": "string"
                  }
                }
              ]
            }
        """.trimIndent()

        val parser = AvroSchemaParser()
        val avroUdm = parser.parse(avroSchemaJson)

        // When: Convert to USDL
        val usdl = parser.toUSDL(avroUdm)

        // Then: Should have %map directive
        val obj = usdl as UDM.Object
        val types = obj.properties["%types"] as UDM.Object
        val personType = types.properties["Person"] as UDM.Object
        val fields = personType.properties["%fields"] as UDM.Array
        val field = fields.elements[0] as UDM.Object

        assertEquals(true, (field.properties["%map"] as? UDM.Scalar)?.value)
    }

    // ========== Round-Trip Tests ==========

    @Test
    fun `round-trip USDL to Avro to USDL`() {
        // Given: Original USDL schema
        val originalUsdl = UDM.Object(
            properties = mapOf(
                "%namespace" to UDM.Scalar("com.example.roundtrip"),
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Person" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%documentation" to UDM.Scalar("A person record"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("name"),
                                            "%type" to UDM.Scalar("string"),
                                            "%required" to UDM.Scalar(true)
                                        )),
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("age"),
                                            "%type" to UDM.Scalar("integer"),
                                            "%required" to UDM.Scalar(true)
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Convert USDL -> Avro -> USDL
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroJson = serializer.serialize(originalUsdl)

        val parser = AvroSchemaParser()
        val avroUdm = parser.parse(avroJson)
        val reconvertedUsdl = parser.toUSDL(avroUdm)

        // Then: Should preserve key elements
        assertTrue(reconvertedUsdl is UDM.Object)
        val obj = reconvertedUsdl as UDM.Object

        val types = obj.properties["%types"] as? UDM.Object
        assertNotNull(types)

        val personType = types!!.properties["Person"] as? UDM.Object
        assertNotNull(personType)

        assertEquals("structure", (personType!!.properties["%kind"] as? UDM.Scalar)?.value)
        assertEquals("A person record", (personType.properties["%documentation"] as? UDM.Scalar)?.value)

        val fields = personType.properties["%fields"] as? UDM.Array
        assertNotNull(fields)
        assertEquals(2, fields!!.elements.size)
    }

    @Test
    fun `round-trip preserves logical types`() {
        // Given: USDL with logical types
        val originalUsdl = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Event" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("id"),
                                            "%type" to UDM.Scalar("string"),
                                            "%logicalType" to UDM.Scalar("uuid")
                                        )),
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("timestamp"),
                                            "%type" to UDM.Scalar("integer"),
                                            "%logicalType" to UDM.Scalar("timestamp-millis")
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Round-trip
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroJson = serializer.serialize(originalUsdl)

        val parser = AvroSchemaParser()
        val avroUdm = parser.parse(avroJson)
        val reconvertedUsdl = parser.toUSDL(avroUdm)

        // Then: Should preserve logical types
        val obj = reconvertedUsdl as UDM.Object
        val types = obj.properties["%types"] as UDM.Object
        val eventType = types.properties["Event"] as UDM.Object
        val fields = eventType.properties["%fields"] as UDM.Array

        val idField = fields.elements[0] as UDM.Object
        assertEquals("uuid", (idField.properties["%logicalType"] as? UDM.Scalar)?.value)

        val timestampField = fields.elements[1] as UDM.Object
        assertEquals("timestamp-millis", (timestampField.properties["%logicalType"] as? UDM.Scalar)?.value)
    }

    @Test
    fun `dual namespace - record has namespace at both schema and type level`() {
        // Given: Avro record with namespace
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "Person",
              "namespace": "com.example",
              "doc": "A person record",
              "fields": [
                {"name": "name", "type": "string"},
                {"name": "age", "type": "int"}
              ]
            }
        """.trimIndent()

        val parser = AvroSchemaParser()
        val avroUdm = parser.parse(avroSchemaJson)

        // When: Convert to USDL
        val usdl = parser.toUSDL(avroUdm)

        // Then: Should have namespace at BOTH schema and type level
        assertTrue(usdl is UDM.Object)
        val obj = usdl as UDM.Object

        // Check schema-level namespace
        val schemaNamespace = obj.properties["%namespace"] as? UDM.Scalar
        assertNotNull(schemaNamespace, "Schema-level namespace should exist")
        assertEquals("com.example", schemaNamespace!!.value)

        // Check type-level namespace
        val types = obj.properties["%types"] as? UDM.Object
        assertNotNull(types)
        val personType = types!!.properties["Person"] as? UDM.Object
        assertNotNull(personType)
        val typeNamespace = personType!!.properties["%namespace"] as? UDM.Scalar
        assertNotNull(typeNamespace, "Type-level namespace should exist")
        assertEquals("com.example", typeNamespace!!.value)
    }

    @Test
    fun `dual namespace - enum has namespace at both schema and type level`() {
        // Given: Avro enum with namespace
        val avroSchemaJson = """
            {
              "type": "enum",
              "name": "Status",
              "namespace": "com.example.enums",
              "doc": "Order status enumeration",
              "symbols": ["PENDING", "PROCESSING", "SHIPPED", "DELIVERED"]
            }
        """.trimIndent()

        val parser = AvroSchemaParser()
        val avroUdm = parser.parse(avroSchemaJson)

        // When: Convert to USDL
        val usdl = parser.toUSDL(avroUdm)

        // Then: Should have namespace at BOTH schema and type level (consistent with records)
        assertTrue(usdl is UDM.Object)
        val obj = usdl as UDM.Object

        // Check schema-level namespace
        val schemaNamespace = obj.properties["%namespace"] as? UDM.Scalar
        assertNotNull(schemaNamespace, "Schema-level namespace should exist for enums")
        assertEquals("com.example.enums", schemaNamespace!!.value)

        // Check type-level namespace
        val types = obj.properties["%types"] as? UDM.Object
        assertNotNull(types)
        val statusType = types!!.properties["Status"] as? UDM.Object
        assertNotNull(statusType)
        val typeNamespace = statusType!!.properties["%namespace"] as? UDM.Scalar
        assertNotNull(typeNamespace, "Type-level namespace should exist for enums")
        assertEquals("com.example.enums", typeNamespace!!.value)

        // Verify it's an enumeration
        assertEquals("enumeration", (statusType.properties["%kind"] as? UDM.Scalar)?.value)
    }

    @Test
    fun `no namespace pollution - type without namespace has no namespace fields`() {
        // Given: Avro record WITHOUT namespace
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "SimpleRecord",
              "fields": [
                {"name": "id", "type": "int"},
                {"name": "value", "type": "string"}
              ]
            }
        """.trimIndent()

        val parser = AvroSchemaParser()
        val avroUdm = parser.parse(avroSchemaJson)

        // When: Convert to USDL
        val usdl = parser.toUSDL(avroUdm)

        // Then: Should NOT have namespace fields
        assertTrue(usdl is UDM.Object)
        val obj = usdl as UDM.Object

        // Schema-level namespace should NOT exist
        assertFalse(obj.properties.containsKey("%namespace"), "Schema should not have namespace field")

        // Type-level namespace should NOT exist
        val types = obj.properties["%types"] as? UDM.Object
        assertNotNull(types)
        val simpleType = types!!.properties["SimpleRecord"] as? UDM.Object
        assertNotNull(simpleType)
        assertFalse(simpleType!!.properties.containsKey("%namespace"), "Type should not have namespace field")

        // But should still have proper structure
        assertEquals("structure", (simpleType.properties["%kind"] as? UDM.Scalar)?.value)
        val fields = simpleType.properties["%fields"] as? UDM.Array
        assertNotNull(fields)
        assertEquals(2, fields!!.elements.size)
    }

    @Test
    fun `versioned namespace - supports API evolution patterns`() {
        // Given: Avro record with versioned namespace
        val avroSchemaJson = """
            {
              "type": "record",
              "name": "CustomerV2",
              "namespace": "com.example.api.v2",
              "doc": "Customer schema version 2",
              "fields": [
                {"name": "customerId", "type": "string"},
                {"name": "email", "type": "string"},
                {"name": "registrationDate", "type": "long"}
              ]
            }
        """.trimIndent()

        val parser = AvroSchemaParser()
        val avroUdm = parser.parse(avroSchemaJson)

        // When: Convert to USDL
        val usdl = parser.toUSDL(avroUdm)

        // Then: Versioned namespace should be preserved
        assertTrue(usdl is UDM.Object)
        val obj = usdl as UDM.Object

        // Check versioned namespace at schema level
        val schemaNamespace = obj.properties["%namespace"] as? UDM.Scalar
        assertNotNull(schemaNamespace)
        assertEquals("com.example.api.v2", schemaNamespace!!.value)
        assertTrue(schemaNamespace.value.toString().contains(".v2"), "Namespace should contain version")

        // Check versioned namespace at type level
        val types = obj.properties["%types"] as? UDM.Object
        assertNotNull(types)
        val customerType = types!!.properties["CustomerV2"] as? UDM.Object
        assertNotNull(customerType)
        val typeNamespace = customerType!!.properties["%namespace"] as? UDM.Scalar
        assertNotNull(typeNamespace)
        assertEquals("com.example.api.v2", typeNamespace!!.value)

        // Verify documentation is preserved
        assertEquals("Customer schema version 2", (customerType.properties["%documentation"] as? UDM.Scalar)?.value)
    }
}
