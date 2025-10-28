package org.apache.utlx.formats.avro

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.json.JSONParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for AvroSchemaSerializer (USDL to Avro schema transformation)
 *
 * These tests verify the internal API behavior.
 * Feature tests are in conformance-suite/tests/formats/avro/
 */
class AvroSchemaSerializerTest {

    // ========== Basic Structure Tests ==========

    @Test
    fun `serialize basic USDL structure to Avro record`() {
        // Given: Basic USDL structure
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Customer" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("id"),
                                            "%type" to UDM.Scalar("integer"),
                                            "%required" to UDM.Scalar(true)
                                        )),
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("email"),
                                            "%type" to UDM.Scalar("string"),
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

        // When: Serialize to Avro
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should produce valid Avro record
        assertTrue(avroSchema.contains("\"type\":\"record\""))
        assertTrue(avroSchema.contains("\"name\":\"Customer\""))
        assertTrue(avroSchema.contains("\"fields\""))
        assertTrue(avroSchema.contains("\"id\""))
        assertTrue(avroSchema.contains("\"email\""))
    }

    @Test
    fun `serialize structure with namespace`() {
        // Given: USDL structure with namespace
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%namespace" to UDM.Scalar("com.example"),
                "%types" to UDM.Object(
                    properties = mapOf(
                        "User" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("name"),
                                            "%type" to UDM.Scalar("string")
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should include namespace
        assertTrue(avroSchema.contains("\"namespace\":\"com.example\""))
        assertTrue(avroSchema.contains("\"name\":\"User\""))
    }

    @Test
    fun `serialize structure with documentation`() {
        // Given: USDL structure with doc
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Product" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%documentation" to UDM.Scalar("Product catalog entry"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("sku"),
                                            "%type" to UDM.Scalar("string")
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should include doc
        assertTrue(avroSchema.contains("\"doc\":\"Product catalog entry\""))
    }

    // ========== Primitive Type Tests ==========

    @Test
    fun `map USDL primitive types to Avro types`() {
        // Test cases: (USDL type -> Avro type)
        val testCases = listOf(
            "string" to "string",
            "integer" to "int",
            "long" to "long",
            "number" to "float",
            "double" to "double",
            "boolean" to "boolean",
            "binary" to "bytes"
        )

        testCases.forEach { (usdlType, avroType) ->
            // Given: USDL field with primitive type
            val usdlSchema = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Test" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(properties = mapOf(
                                                "%name" to UDM.Scalar("field"),
                                                "%type" to UDM.Scalar(usdlType)
                                            ))
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            // When: Serialize
            val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
            val avroSchema = serializer.serialize(usdlSchema)

            // Then: Should map to correct Avro type
            assertTrue(avroSchema.contains("\"type\":\"$avroType\""),
                "Expected $usdlType to map to $avroType")
        }
    }

    // ========== Logical Type Tests ==========

    @Test
    fun `serialize field with timestamp-millis logical type`() {
        // Given: Field with timestamp-millis
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Event" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
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

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should produce logical type
        assertTrue(avroSchema.contains("\"logicalType\":\"timestamp-millis\""))
        assertTrue(avroSchema.contains("\"type\":\"long\""))
    }

    @Test
    fun `serialize field with date logical type`() {
        // Given: Field with date logical type
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Person" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("birthDate"),
                                            "%type" to UDM.Scalar("integer"),
                                            "%logicalType" to UDM.Scalar("date")
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should produce date logical type
        assertTrue(avroSchema.contains("\"logicalType\":\"date\""))
        assertTrue(avroSchema.contains("\"type\":\"int\""))
    }

    @Test
    fun `serialize field with decimal logical type`() {
        // Given: Field with decimal (precision and scale)
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Order" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("price"),
                                            "%type" to UDM.Scalar("number"),
                                            "%logicalType" to UDM.Scalar("decimal"),
                                            "%precision" to UDM.Scalar(10),
                                            "%scale" to UDM.Scalar(2)
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should include precision and scale
        assertTrue(avroSchema.contains("\"logicalType\":\"decimal\""))
        assertTrue(avroSchema.contains("\"precision\":10"))
        assertTrue(avroSchema.contains("\"scale\":2"))
    }

    @Test
    fun `serialize field with uuid logical type`() {
        // Given: Field with UUID
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Transaction" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("transactionId"),
                                            "%type" to UDM.Scalar("string"),
                                            "%logicalType" to UDM.Scalar("uuid")
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should produce UUID logical type
        assertTrue(avroSchema.contains("\"logicalType\":\"uuid\""))
    }

    // ========== Enumeration Tests ==========

    @Test
    fun `serialize USDL enumeration to Avro enum`() {
        // Given: USDL enumeration
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "OrderStatus" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("enumeration"),
                                "%values" to UDM.Array(
                                    elements = listOf(
                                        UDM.Scalar("PENDING"),
                                        UDM.Scalar("PROCESSING"),
                                        UDM.Scalar("SHIPPED"),
                                        UDM.Scalar("DELIVERED")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should produce Avro enum
        assertTrue(avroSchema.contains("\"type\":\"enum\""))
        assertTrue(avroSchema.contains("\"name\":\"OrderStatus\""))
        assertTrue(avroSchema.contains("\"symbols\""))
        assertTrue(avroSchema.contains("\"PENDING\""))
        assertTrue(avroSchema.contains("\"SHIPPED\""))
    }

    // ========== Array Tests ==========

    @Test
    fun `serialize field with array type`() {
        // Given: Field marked as array
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Product" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("tags"),
                                            "%type" to UDM.Scalar("string"),
                                            "%array" to UDM.Scalar(true)
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should produce array type
        assertTrue(avroSchema.contains("\"type\":\"array\""))
        assertTrue(avroSchema.contains("\"items\":\"string\""))
    }

    // ========== Map Tests ==========

    @Test
    fun `serialize field with map type`() {
        // Given: Field marked as map
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Configuration" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("metadata"),
                                            "%type" to UDM.Scalar("string"),
                                            "%map" to UDM.Scalar(true)
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should produce map type
        assertTrue(avroSchema.contains("\"type\":\"map\""))
        assertTrue(avroSchema.contains("\"values\":\"string\""))
    }

    // ========== Nullable Field Tests ==========

    @Test
    fun `serialize nullable field as union with null`() {
        // Given: Optional field (not required)
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "User" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("middleName"),
                                            "%type" to UDM.Scalar("string"),
                                            "%required" to UDM.Scalar(false),
                                            "%default" to UDM.Scalar(null)
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should produce union with null
        assertTrue(avroSchema.contains("[\"null\",\"string\"]") ||
                   avroSchema.contains("[\"null\",{") ||
                   avroSchema.contains("\"type\":[\"null\""))
        assertTrue(avroSchema.contains("\"default\":null"))
    }

    // ========== Aliases Tests (Schema Evolution) ==========

    @Test
    fun `serialize type with aliases`() {
        // Given: Type with aliases
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Customer" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%aliases" to UDM.Array(
                                    elements = listOf(
                                        UDM.Scalar("CustomerV1"),
                                        UDM.Scalar("LegacyCustomer")
                                    )
                                ),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("id"),
                                            "%type" to UDM.Scalar("integer")
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should include aliases
        assertTrue(avroSchema.contains("\"aliases\""))
        assertTrue(avroSchema.contains("\"CustomerV1\""))
        assertTrue(avroSchema.contains("\"LegacyCustomer\""))
    }

    @Test
    fun `serialize field with aliases`() {
        // Given: Field with aliases
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Product" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("customerId"),
                                            "%type" to UDM.Scalar("integer"),
                                            "%aliases" to UDM.Array(
                                                elements = listOf(
                                                    UDM.Scalar("id"),
                                                    UDM.Scalar("customer_id")
                                                )
                                            )
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Field should have aliases
        assertTrue(avroSchema.contains("\"aliases\""))
        assertTrue(avroSchema.contains("\"id\""))
        assertTrue(avroSchema.contains("\"customer_id\""))
    }

    // ========== Validation Tests ==========

    @Test
    fun `validate generated schema with Apache Avro library`() {
        // Given: Valid USDL schema
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "SimpleRecord" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("field1"),
                                            "%type" to UDM.Scalar("string")
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize with validation enabled
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = true)

        // Then: Should not throw exception
        assertDoesNotThrow {
            serializer.serialize(usdlSchema)
        }
    }

    @Test
    fun `reject invalid schema when validation enabled`() {
        // Given: Invalid USDL (missing required %fields)
        val invalidSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Invalid" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure")
                                // Missing %fields
                            )
                        )
                    )
                )
            )
        )

        // When/Then: Should throw exception
        val serializer = AvroSchemaSerializer(validate = true)
        assertThrows<IllegalArgumentException> {
            serializer.serialize(invalidSchema)
        }
    }

    // ========== Mode Detection Tests ==========

    @Test
    fun `detect USDL mode when types directive present`() {
        // Given: Schema with %types directive
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(properties = mapOf(
                    "Test" to UDM.Object(properties = mapOf(
                        "%kind" to UDM.Scalar("structure"),
                        "%fields" to UDM.Array(elements = emptyList())
                    ))
                ))
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val result = serializer.serialize(usdlSchema)

        // Then: Should transform via USDL mode
        assertTrue(result.contains("\"type\":\"record\""))
    }

    @Test
    fun `detect low-level mode when Avro keywords present`() {
        // Given: Direct Avro schema structure
        val avroSchema = UDM.Object(
            properties = mapOf(
                "type" to UDM.Scalar("record"),
                "name" to UDM.Scalar("DirectRecord"),
                "fields" to UDM.Array(elements = emptyList())
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = false, validate = false)
        val result = serializer.serialize(avroSchema)

        // Then: Should pass through without transformation
        assertTrue(result.contains("\"DirectRecord\""))
    }

    // ========== Complex Integration Tests ==========

    @Test
    fun `serialize complex schema with multiple features`() {
        // Given: Complex schema with namespace, docs, arrays, logical types
        val usdlSchema = UDM.Object(
            properties = mapOf(
                "%namespace" to UDM.Scalar("com.example.complex"),
                "%types" to UDM.Object(
                    properties = mapOf(
                        "ComplexRecord" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%documentation" to UDM.Scalar("A complex record"),
                                "%aliases" to UDM.Array(elements = listOf(UDM.Scalar("OldComplex"))),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("id"),
                                            "%type" to UDM.Scalar("string"),
                                            "%logicalType" to UDM.Scalar("uuid")
                                        )),
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("tags"),
                                            "%type" to UDM.Scalar("string"),
                                            "%array" to UDM.Scalar(true)
                                        )),
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("metadata"),
                                            "%type" to UDM.Scalar("string"),
                                            "%map" to UDM.Scalar(true)
                                        )),
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("createdAt"),
                                            "%type" to UDM.Scalar("integer"),
                                            "%logicalType" to UDM.Scalar("timestamp-millis")
                                        )),
                                        UDM.Object(properties = mapOf(
                                            "%name" to UDM.Scalar("optional"),
                                            "%type" to UDM.Scalar("string"),
                                            "%required" to UDM.Scalar(false),
                                            "%default" to UDM.Scalar(null)
                                        ))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // When: Serialize
        val serializer = AvroSchemaSerializer(prettyPrint = true, validate = false)
        val avroSchema = serializer.serialize(usdlSchema)

        // Then: Should include all features
        // Note: JSONSerializer uses ": " format (colon + space, no space before colon)
        assertTrue(avroSchema.contains("\"namespace\": \"com.example.complex\""),
            "Expected namespace in schema")
        assertTrue(avroSchema.contains("\"doc\": \"A complex record\""),
            "Expected doc in schema")
        assertTrue(avroSchema.contains("\"aliases\""),
            "Expected aliases in schema")
        assertTrue(avroSchema.contains("\"logicalType\": \"uuid\""),
            "Expected uuid logical type in schema")
        assertTrue(avroSchema.contains("\"type\": \"array\""),
            "Expected array type in schema")
        assertTrue(avroSchema.contains("\"type\": \"map\""),
            "Expected map type in schema")
        assertTrue(avroSchema.contains("\"logicalType\": \"timestamp-millis\""),
            "Expected timestamp-millis logical type in schema")

        // Check for union type (nullable field)
        val hasCompactUnion = avroSchema.contains("[\"null\",\"string\"]")
        val hasPrettyUnion = avroSchema.contains("\"null\"") && avroSchema.contains("\"string\"")
        assertTrue(hasCompactUnion || hasPrettyUnion,
            "Expected union type with null and string. Schema:\n$avroSchema")
    }
}
