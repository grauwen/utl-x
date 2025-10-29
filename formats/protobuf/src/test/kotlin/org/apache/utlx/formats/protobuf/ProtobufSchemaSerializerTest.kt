package org.apache.utlx.formats.protobuf

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for ProtobufSchemaSerializer
 *
 * Test Coverage:
 * - Simple message generation
 * - Primitive type mapping
 * - Field number handling (explicit and auto-assigned)
 * - Enum generation and validation
 * - Repeated fields (arrays)
 * - Map fields
 * - Oneof groups
 * - Reserved fields and names
 * - Multi-type schemas
 * - Documentation comments
 * - Error handling and validation
 */
@DisplayName("ProtobufSchemaSerializer")
class ProtobufSchemaSerializerTest {

    private val serializer = ProtobufSchemaSerializer()

    @Nested
    @DisplayName("Basic Message Serialization")
    inner class BasicMessageTests {

        @Test
        fun `should serialize simple message with primitive fields`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%namespace" to UDM.Scalar("example"),
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Person" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("name"),
                                                    "%type" to UDM.Scalar("string"),
                                                    "%fieldNumber" to UDM.Scalar(1)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("age"),
                                                    "%type" to UDM.Scalar("integer"),
                                                    "%size" to UDM.Scalar(32),
                                                    "%fieldNumber" to UDM.Scalar(2)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "syntax = \"proto3\";"
            result shouldContain "package example;"
            result shouldContain "message Person {"
            result shouldContain "string name = 1;"
            result shouldContain "int32 age = 2;"
        }

        @Test
        fun `should auto-assign field numbers when not specified`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Person" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("name"),
                                                    "%type" to UDM.Scalar("string")
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("age"),
                                                    "%type" to UDM.Scalar("integer")
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "string name = 1;"
            result shouldContain "int32 age = 2;"
        }
    }

    @Nested
    @DisplayName("Type Mapping")
    inner class TypeMappingTests {

        @Test
        fun `should map USDL types to Proto3 types correctly`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "AllTypes" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("str_field"),
                                                    "%type" to UDM.Scalar("string"),
                                                    "%fieldNumber" to UDM.Scalar(1)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("bool_field"),
                                                    "%type" to UDM.Scalar("boolean"),
                                                    "%fieldNumber" to UDM.Scalar(2)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("int32_field"),
                                                    "%type" to UDM.Scalar("integer"),
                                                    "%size" to UDM.Scalar(32),
                                                    "%fieldNumber" to UDM.Scalar(3)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("int64_field"),
                                                    "%type" to UDM.Scalar("integer"),
                                                    "%size" to UDM.Scalar(64),
                                                    "%fieldNumber" to UDM.Scalar(4)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("double_field"),
                                                    "%type" to UDM.Scalar("number"),
                                                    "%fieldNumber" to UDM.Scalar(5)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("float_field"),
                                                    "%type" to UDM.Scalar("float"),
                                                    "%fieldNumber" to UDM.Scalar(6)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("bytes_field"),
                                                    "%type" to UDM.Scalar("bytes"),
                                                    "%fieldNumber" to UDM.Scalar(7)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "string str_field = 1;"
            result shouldContain "bool bool_field = 2;"
            result shouldContain "int32 int32_field = 3;"
            result shouldContain "int64 int64_field = 4;"
            result shouldContain "double double_field = 5;"
            result shouldContain "float float_field = 6;"
            result shouldContain "bytes bytes_field = 7;"
        }

        @Test
        fun `should use custom type names for message and enum references`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Order" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("status"),
                                                    "%type" to UDM.Scalar("OrderStatus"),
                                                    "%fieldNumber" to UDM.Scalar(1)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("customer"),
                                                    "%type" to UDM.Scalar("Customer"),
                                                    "%fieldNumber" to UDM.Scalar(2)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "OrderStatus status = 1;"
            result shouldContain "Customer customer = 2;"
        }
    }

    @Nested
    @DisplayName("Enum Serialization")
    inner class EnumTests {

        @Test
        fun `should serialize enum with first value = 0`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "OrderStatus" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("enumeration"),
                                    "%values" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%value" to UDM.Scalar("ORDER_STATUS_UNSPECIFIED"),
                                                    "%ordinal" to UDM.Scalar(0)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%value" to UDM.Scalar("PENDING"),
                                                    "%ordinal" to UDM.Scalar(1)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%value" to UDM.Scalar("SHIPPED"),
                                                    "%ordinal" to UDM.Scalar(2)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "enum OrderStatus {"
            result shouldContain "ORDER_STATUS_UNSPECIFIED = 0;"
            result shouldContain "PENDING = 1;"
            result shouldContain "SHIPPED = 2;"
        }

        @Test
        fun `should throw error when enum first value is not 0`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Status" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("enumeration"),
                                    "%values" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%value" to UDM.Scalar("ACTIVE"),
                                                    "%ordinal" to UDM.Scalar(1)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val exception = shouldThrow<IllegalStateException> {
                serializer.serialize(usdl)
            }

            exception.message shouldContain "must have ordinal 0"
            exception.message shouldContain "proto3 requirement"
        }

        @Test
        fun `should auto-assign enum ordinals when not specified`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Color" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("enumeration"),
                                    "%values" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%value" to UDM.Scalar("COLOR_UNSPECIFIED")
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%value" to UDM.Scalar("RED")
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%value" to UDM.Scalar("GREEN")
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "COLOR_UNSPECIFIED = 0;"
            result shouldContain "RED = 1;"
            result shouldContain "GREEN = 2;"
        }
    }

    @Nested
    @DisplayName("Repeated Fields (Arrays)")
    inner class RepeatedFieldTests {

        @Test
        fun `should serialize repeated fields`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Order" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("items"),
                                                    "%type" to UDM.Scalar("string"),
                                                    "%array" to UDM.Scalar(true),
                                                    "%fieldNumber" to UDM.Scalar(1)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("quantities"),
                                                    "%type" to UDM.Scalar("integer"),
                                                    "%size" to UDM.Scalar(32),
                                                    "%array" to UDM.Scalar(true),
                                                    "%fieldNumber" to UDM.Scalar(2)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "repeated string items = 1;"
            result shouldContain "repeated int32 quantities = 2;"
        }
    }

    @Nested
    @DisplayName("Map Fields")
    inner class MapFieldTests {

        @Test
        fun `should serialize map fields`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Inventory" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("stock_levels"),
                                                    "%type" to UDM.Scalar("map"),
                                                    "%map" to UDM.Scalar(true),
                                                    "%keyType" to UDM.Scalar("string"),
                                                    "%itemType" to UDM.Scalar("integer"),
                                                    "%fieldNumber" to UDM.Scalar(1)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "map<string, int32> stock_levels = 1;"
        }
    }

    @Nested
    @DisplayName("Oneof Groups")
    inner class OneofTests {

        @Test
        fun `should serialize oneof groups`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Payment" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("credit_card"),
                                                    "%type" to UDM.Scalar("string"),
                                                    "%oneof" to UDM.Scalar("payment_method"),
                                                    "%fieldNumber" to UDM.Scalar(1)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("paypal"),
                                                    "%type" to UDM.Scalar("string"),
                                                    "%oneof" to UDM.Scalar("payment_method"),
                                                    "%fieldNumber" to UDM.Scalar(2)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("amount"),
                                                    "%type" to UDM.Scalar("number"),
                                                    "%fieldNumber" to UDM.Scalar(3)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "oneof payment_method {"
            result shouldContain "string credit_card = 1;"
            result shouldContain "string paypal = 2;"
            result shouldContain "double amount = 3;"
            // The oneof group should be present in the message
        }
    }

    @Nested
    @DisplayName("Reserved Fields")
    inner class ReservedTests {

        @Test
        fun `should serialize reserved field numbers`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Message" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%reserved" to UDM.Array(
                                        elements = listOf(
                                            UDM.Scalar(5),
                                            UDM.Scalar(6),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "from" to UDM.Scalar(10),
                                                    "to" to UDM.Scalar(15)
                                                )
                                            )
                                        )
                                    ),
                                    "%fields" to UDM.Array(emptyList())
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "reserved 5, 6, 10 to 15;"
        }

        @Test
        fun `should serialize reserved field names`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Message" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%reservedNames" to UDM.Array(
                                        elements = listOf(
                                            UDM.Scalar("old_field"),
                                            UDM.Scalar("deprecated")
                                        )
                                    ),
                                    "%fields" to UDM.Array(emptyList())
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "reserved \"old_field\", \"deprecated\";"
        }
    }

    @Nested
    @DisplayName("Multi-Type Schemas")
    inner class MultiTypeTests {

        @Test
        fun `should serialize multiple types in one proto file`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%namespace" to UDM.Scalar("ecommerce"),
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "OrderStatus" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("enumeration"),
                                    "%values" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%value" to UDM.Scalar("UNSPECIFIED"),
                                                    "%ordinal" to UDM.Scalar(0)
                                                )
                                            )
                                        )
                                    )
                                )
                            ),
                            "OrderItem" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("sku"),
                                                    "%type" to UDM.Scalar("string"),
                                                    "%fieldNumber" to UDM.Scalar(1)
                                                )
                                            )
                                        )
                                    )
                                )
                            ),
                            "Order" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("id"),
                                                    "%type" to UDM.Scalar("string"),
                                                    "%fieldNumber" to UDM.Scalar(1)
                                                )
                                            ),
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("status"),
                                                    "%type" to UDM.Scalar("OrderStatus"),
                                                    "%fieldNumber" to UDM.Scalar(2)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            // Should have all three types
            result shouldContain "enum OrderStatus {"
            result shouldContain "message OrderItem {"
            result shouldContain "message Order {"

            // Enum should come before messages (enums are serialized first)
            val enumIndex = result.indexOf("enum OrderStatus")
            val orderItemIndex = result.indexOf("message OrderItem")
            val orderIndex = result.indexOf("message Order")

            assert(enumIndex >= 0) { "Enum should be present" }
            assert(orderItemIndex >= 0) { "OrderItem message should be present" }
            assert(orderIndex >= 0) { "Order message should be present" }
            assert(enumIndex < orderItemIndex || enumIndex < orderIndex) { "Enum should come before at least one message" }
        }
    }

    @Nested
    @DisplayName("Documentation Comments")
    inner class DocumentationTests {

        @Test
        fun `should generate documentation comments for types and fields`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Person" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%documentation" to UDM.Scalar("Represents a person"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("name"),
                                                    "%type" to UDM.Scalar("string"),
                                                    "%documentation" to UDM.Scalar("Full name"),
                                                    "%fieldNumber" to UDM.Scalar(1)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "// Represents a person"
            result shouldContain "// Full name"
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorTests {

        @Test
        fun `should throw error for invalid field number - too small`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Test" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("field"),
                                                    "%type" to UDM.Scalar("string"),
                                                    "%fieldNumber" to UDM.Scalar(0)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val exception = shouldThrow<IllegalArgumentException> {
                serializer.serialize(usdl)
            }

            exception.message shouldContain "invalid field number"
            exception.message shouldContain "must be between 1 and 536,870,911"
        }

        @Test
        fun `should throw error for reserved field number range 19000-19999`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Test" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(
                                        elements = listOf(
                                            UDM.Object(
                                                properties = mapOf(
                                                    "%name" to UDM.Scalar("field"),
                                                    "%type" to UDM.Scalar("string"),
                                                    "%fieldNumber" to UDM.Scalar(19500)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val exception = shouldThrow<IllegalArgumentException> {
                serializer.serialize(usdl)
            }

            exception.message shouldContain "reserved field number"
            exception.message shouldContain "19000-19999"
        }

        @Test
        fun `should throw error when USDL is not an object`() {
            val exception = shouldThrow<IllegalArgumentException> {
                serializer.serialize(UDM.Scalar("not an object"))
            }

            exception.message shouldContain "must be an object"
        }

        @Test
        fun `should throw error when no types are defined`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%namespace" to UDM.Scalar("example")
                )
            )

            val exception = shouldThrow<IllegalArgumentException> {
                serializer.serialize(usdl)
            }

            exception.message shouldContain "must have %types directive"
        }
    }

    @Nested
    @DisplayName("Package Handling")
    inner class PackageTests {

        @Test
        fun `should generate proto without package when namespace is not specified`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Test" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(emptyList())
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "syntax = \"proto3\";"
            result shouldNotContain "package"
            result shouldContain "message Test {"
        }

        @Test
        fun `should generate package declaration when namespace is specified`() {
            val usdl = UDM.Object(
                properties = mapOf(
                    "%namespace" to UDM.Scalar("com.example.api"),
                    "%types" to UDM.Object(
                        properties = mapOf(
                            "Test" to UDM.Object(
                                properties = mapOf(
                                    "%kind" to UDM.Scalar("structure"),
                                    "%fields" to UDM.Array(emptyList())
                                )
                            )
                        )
                    )
                )
            )

            val result = serializer.serialize(usdl)

            result shouldContain "package com.example.api;"
        }
    }
}
