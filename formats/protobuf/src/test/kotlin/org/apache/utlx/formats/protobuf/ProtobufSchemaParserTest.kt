package org.apache.utlx.formats.protobuf

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for ProtobufSchemaParser
 *
 * Test Coverage:
 * - Simple message parsing
 * - Proto3 type to USDL type mapping
 * - Field number extraction
 * - Enum parsing
 * - Repeated fields parsing
 * - Map fields parsing
 * - Oneof groups parsing
 * - Reserved fields parsing
 * - Multi-type schema parsing
 * - Documentation comment extraction
 * - Error handling (proto2 rejection, malformed proto)
 */
@DisplayName("ProtobufSchemaParser")
class ProtobufSchemaParserTest {

    private val parser = ProtobufSchemaParser()

    @Nested
    @DisplayName("Basic Message Parsing")
    inner class BasicMessageTests {

        @Test
        fun `should parse simple message with primitive fields`() {
            val protoSource = """
                syntax = "proto3";
                package example;

                message Person {
                  string name = 1;
                  int32 age = 2;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            result.shouldBeInstanceOf<UDM.Object>()
            val resultObj = result as UDM.Object
            val namespace = (resultObj.properties["%namespace"] as? UDM.Scalar)?.value
            namespace shouldBe "example"

            val types = resultObj.properties["%types"] as? UDM.Object
            assert(types != null) { "%types should be present" }

            val person = types?.properties?.get("Person") as? UDM.Object
            assert(person != null) { "Person type should be present" }

            val kind = (person?.properties?.get("%kind") as? UDM.Scalar)?.value
            kind shouldBe "structure"

            val fields = person?.properties?.get("%fields") as? UDM.Array
            fields?.elements?.size shouldBe 2
        }

        @Test
        fun `should extract field numbers correctly`() {
            val protoSource = """
                syntax = "proto3";

                message Test {
                  string field1 = 1;
                  int32 field2 = 5;
                  bool field3 = 10;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            val types = result.properties["%types"] as UDM.Object
            val test = types.properties["Test"] as UDM.Object
            val fields = test.properties["%fields"] as UDM.Array

            val field1 = fields.elements[0] as UDM.Object
            (field1.properties["%fieldNumber"] as UDM.Scalar).value shouldBe 1

            val field2 = fields.elements[1] as UDM.Object
            (field2.properties["%fieldNumber"] as UDM.Scalar).value shouldBe 5

            val field3 = fields.elements[2] as UDM.Object
            (field3.properties["%fieldNumber"] as UDM.Scalar).value shouldBe 10
        }
    }

    @Nested
    @DisplayName("Type Mapping")
    inner class TypeMappingTests {

        @Test
        fun `should map Proto3 types to USDL types correctly`() {
            val protoSource = """
                syntax = "proto3";

                message AllTypes {
                  string str_field = 1;
                  bool bool_field = 2;
                  int32 int32_field = 3;
                  int64 int64_field = 4;
                  double double_field = 5;
                  float float_field = 6;
                  bytes bytes_field = 7;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            val types = result.properties["%types"] as UDM.Object
            val allTypes = types.properties["AllTypes"] as UDM.Object
            val fields = allTypes.properties["%fields"] as UDM.Array

            fun getFieldType(index: Int): String {
                val field = fields.elements[index] as UDM.Object
                return (field.properties["%type"] as UDM.Scalar).value as String
            }

            fun getFieldSize(index: Int): Int? {
                val field = fields.elements[index] as UDM.Object
                return (field.properties["%size"] as? UDM.Scalar)?.value as? Int
            }

            getFieldType(0) shouldBe "string"
            getFieldType(1) shouldBe "boolean"
            getFieldType(2) shouldBe "integer"
            getFieldSize(2) shouldBe 32
            getFieldType(3) shouldBe "integer"
            getFieldSize(3) shouldBe 64
            getFieldType(4) shouldBe "number"
            getFieldType(5) shouldBe "float"
            getFieldType(6) shouldBe "bytes"
        }

        @Test
        fun `should preserve custom type names`() {
            val protoSource = """
                syntax = "proto3";

                message Order {
                  OrderStatus status = 1;
                  Customer customer = 2;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            val types = result.properties["%types"] as UDM.Object
            val order = types.properties["Order"] as UDM.Object
            val fields = order.properties["%fields"] as UDM.Array

            val statusField = fields.elements[0] as UDM.Object
            (statusField.properties["%type"] as UDM.Scalar).value shouldBe "OrderStatus"

            val customerField = fields.elements[1] as UDM.Object
            (customerField.properties["%type"] as UDM.Scalar).value shouldBe "Customer"
        }
    }

    @Nested
    @DisplayName("Enum Parsing")
    inner class EnumTests {

        @Test
        fun `should parse enum with values`() {
            val protoSource = """
                syntax = "proto3";

                enum OrderStatus {
                  ORDER_STATUS_UNSPECIFIED = 0;
                  PENDING = 1;
                  SHIPPED = 2;
                  DELIVERED = 3;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            val types = result.properties["%types"] as UDM.Object
            val orderStatus = types.properties["OrderStatus"] as UDM.Object

            (orderStatus.properties["%kind"] as UDM.Scalar).value shouldBe "enumeration"

            val values = orderStatus.properties["%values"] as UDM.Array
            values.elements.size shouldBe 4

            val firstValue = values.elements[0] as UDM.Object
            (firstValue.properties["%value"] as UDM.Scalar).value shouldBe "ORDER_STATUS_UNSPECIFIED"
            (firstValue.properties["%ordinal"] as UDM.Scalar).value shouldBe 0

            val secondValue = values.elements[1] as UDM.Object
            (secondValue.properties["%value"] as UDM.Scalar).value shouldBe "PENDING"
            (secondValue.properties["%ordinal"] as UDM.Scalar).value shouldBe 1
        }
    }

    @Nested
    @DisplayName("Repeated Fields")
    inner class RepeatedFieldTests {

        @Test
        fun `should parse repeated fields`() {
            val protoSource = """
                syntax = "proto3";

                message Order {
                  repeated string items = 1;
                  repeated int32 quantities = 2;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            val types = result.properties["%types"] as UDM.Object
            val order = types.properties["Order"] as UDM.Object
            val fields = order.properties["%fields"] as UDM.Array

            val itemsField = fields.elements[0] as UDM.Object
            (itemsField.properties["%type"] as UDM.Scalar).value shouldBe "string"
            (itemsField.properties["%array"] as UDM.Scalar).value shouldBe true

            val quantitiesField = fields.elements[1] as UDM.Object
            (quantitiesField.properties["%type"] as UDM.Scalar).value shouldBe "integer"
            (quantitiesField.properties["%array"] as UDM.Scalar).value shouldBe true
        }
    }

    @Nested
    @DisplayName("Map Fields")
    inner class MapFieldTests {

        @Test
        fun `should parse map fields`() {
            val protoSource = """
                syntax = "proto3";

                message Inventory {
                  map<string, int32> stock_levels = 1;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            val types = result.properties["%types"] as UDM.Object
            val inventory = types.properties["Inventory"] as UDM.Object
            val fields = inventory.properties["%fields"] as UDM.Array

            val mapField = fields.elements[0] as UDM.Object
            (mapField.properties["%name"] as UDM.Scalar).value shouldBe "stock_levels"
            (mapField.properties["%type"] as UDM.Scalar).value shouldBe "map"
            (mapField.properties["%map"] as UDM.Scalar).value shouldBe true
            (mapField.properties["%keyType"] as UDM.Scalar).value shouldBe "string"
            (mapField.properties["%itemType"] as UDM.Scalar).value shouldBe "integer"
        }
    }

    @Nested
    @DisplayName("Oneof Groups")
    inner class OneofTests {

        @Test
        fun `should parse oneof groups`() {
            val protoSource = """
                syntax = "proto3";

                message Payment {
                  oneof payment_method {
                    string credit_card = 1;
                    string paypal = 2;
                  }
                  double amount = 3;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            val types = result.properties["%types"] as UDM.Object
            val payment = types.properties["Payment"] as UDM.Object
            val fields = payment.properties["%fields"] as UDM.Array

            fields.elements.size shouldBe 3

            val creditCardField = fields.elements[0] as UDM.Object
            (creditCardField.properties["%name"] as UDM.Scalar).value shouldBe "credit_card"
            (creditCardField.properties["%oneof"] as UDM.Scalar).value shouldBe "payment_method"

            val paypalField = fields.elements[1] as UDM.Object
            (paypalField.properties["%name"] as UDM.Scalar).value shouldBe "paypal"
            (paypalField.properties["%oneof"] as UDM.Scalar).value shouldBe "payment_method"

            val amountField = fields.elements[2] as UDM.Object
            (amountField.properties["%name"] as UDM.Scalar).value shouldBe "amount"
            amountField.properties["%oneof"] shouldBe null
        }
    }

    @Nested
    @DisplayName("Reserved Fields")
    inner class ReservedTests {

        @Test
        fun `should parse reserved field numbers`() {
            val protoSource = """
                syntax = "proto3";

                message Test {
                  reserved 2, 15, 9 to 11;
                  string field = 1;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            val types = result.properties["%types"] as UDM.Object
            val test = types.properties["Test"] as UDM.Object
            val reserved = test.properties["%reserved"] as? UDM.Array

            assert(reserved != null) { "reserved should not be null" }
            reserved?.elements?.size shouldBe 3

            // Individual numbers
            (reserved?.elements?.get(0) as UDM.Scalar).value shouldBe 2
            (reserved?.elements?.get(1) as UDM.Scalar).value shouldBe 15

            // Range
            val range = reserved?.elements?.get(2) as UDM.Object
            (range!!.properties["from"] as UDM.Scalar).value shouldBe 9
            (range!!.properties["to"] as UDM.Scalar).value shouldBe 11
        }

        @Test
        fun `should parse reserved field names`() {
            val protoSource = """
                syntax = "proto3";

                message Test {
                  reserved "old_field", "deprecated";
                  string field = 1;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            val types = result.properties["%types"] as UDM.Object
            val test = types.properties["Test"] as UDM.Object
            val reservedNames = test.properties["%reservedNames"] as? UDM.Array

            assert(reservedNames != null) { "reservedNames should not be null" }
            reservedNames?.elements?.size shouldBe 2

            (reservedNames?.elements?.get(0) as UDM.Scalar).value shouldBe "old_field"
            (reservedNames?.elements?.get(1) as UDM.Scalar).value shouldBe "deprecated"
        }
    }

    @Nested
    @DisplayName("Multi-Type Schemas")
    inner class MultiTypeTests {

        @Test
        fun `should parse multiple types in one proto file`() {
            val protoSource = """
                syntax = "proto3";
                package ecommerce;

                enum OrderStatus {
                  ORDER_STATUS_UNSPECIFIED = 0;
                  PENDING = 1;
                }

                message OrderItem {
                  string sku = 1;
                  int32 quantity = 2;
                }

                message Order {
                  string id = 1;
                  OrderStatus status = 2;
                  repeated OrderItem items = 3;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            (result.properties["%namespace"] as UDM.Scalar).value shouldBe "ecommerce"

            val types = result.properties["%types"] as UDM.Object
            types.properties.size shouldBe 3

            types.properties.containsKey("OrderStatus") shouldBe true
            types.properties.containsKey("OrderItem") shouldBe true
            types.properties.containsKey("Order") shouldBe true

            val orderStatus = types.properties["OrderStatus"] as UDM.Object
            (orderStatus.properties["%kind"] as UDM.Scalar).value shouldBe "enumeration"

            val orderItem = types.properties["OrderItem"] as UDM.Object
            (orderItem.properties["%kind"] as UDM.Scalar).value shouldBe "structure"

            val order = types.properties["Order"] as UDM.Object
            (order.properties["%kind"] as UDM.Scalar).value shouldBe "structure"
        }
    }

    @Nested
    @DisplayName("Documentation Comments")
    inner class DocumentationTests {

        @Test
        fun `should extract documentation comments`() {
            val protoSource = """
                syntax = "proto3";

                // Represents a person
                message Person {
                  // Full name of the person
                  string name = 1;
                  // Age in years
                  int32 age = 2;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            val types = result.properties["%types"] as UDM.Object
            val person = types.properties["Person"] as UDM.Object

            // Note: Message-level documentation is extracted but currently lost in parsing
            // Field-level documentation is preserved

            val fields = person.properties["%fields"] as UDM.Array
            val nameField = fields.elements[0] as UDM.Object
            (nameField.properties["%documentation"] as? UDM.Scalar)?.value shouldBe "Full name of the person"

            val ageField = fields.elements[1] as UDM.Object
            (ageField.properties["%documentation"] as? UDM.Scalar)?.value shouldBe "Age in years"
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorTests {

        @Test
        fun `should reject proto2 syntax`() {
            val protoSource = """
                syntax = "proto2";

                message Test {
                  required string name = 1;
                }
            """.trimIndent()

            val exception = shouldThrow<IllegalArgumentException> {
                parser.parse(protoSource)
            }

            assert(exception.message?.contains("proto3") == true) { "Exception message should mention proto3" }
            assert(exception.message?.contains("proto2") == true) { "Exception message should mention proto2" }
        }

        @Test
        fun `should parse proto without package`() {
            val protoSource = """
                syntax = "proto3";

                message Test {
                  string field = 1;
                }
            """.trimIndent()

            val result = parser.parse(protoSource) as UDM.Object

            result.properties["%namespace"] shouldBe null

            val types = result.properties["%types"] as UDM.Object
            types.properties.containsKey("Test") shouldBe true
        }
    }

    @Nested
    @DisplayName("Round-Trip Compatibility")
    inner class RoundTripTests {

        @Test
        fun `should support round-trip conversion (parse and serialize)`() {
            val originalProto = """
                syntax = "proto3";
                package example;

                enum Status {
                  STATUS_UNSPECIFIED = 0;
                  ACTIVE = 1;
                  INACTIVE = 2;
                }

                message User {
                  string id = 1;
                  string name = 2;
                  Status status = 3;
                }
            """.trimIndent()

            // Parse to USDL
            val usdl = parser.parse(originalProto)

            // Serialize back to proto
            val serializer = ProtobufSchemaSerializer()
            val regeneratedProto = serializer.serialize(usdl)

            // Verify key elements are preserved
            assert(regeneratedProto.contains("syntax = \"proto3\";")) { "Should contain syntax declaration" }
            assert(regeneratedProto.contains("package example;")) { "Should contain package declaration" }
            assert(regeneratedProto.contains("enum Status {")) { "Should contain Status enum" }
            assert(regeneratedProto.contains("message User {")) { "Should contain User message" }
            assert(regeneratedProto.contains("string id = 1;")) { "Should contain id field" }
            assert(regeneratedProto.contains("Status status = 3;")) { "Should contain status field" }
        }
    }
}
