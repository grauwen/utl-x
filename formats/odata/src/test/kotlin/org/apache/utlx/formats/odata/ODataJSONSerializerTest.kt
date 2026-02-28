package org.apache.utlx.formats.odata

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ODataJSONSerializerTest {

    // ==================== Minimal Metadata ====================

    @Nested
    @DisplayName("Minimal Metadata Mode")
    inner class MinimalMetadata {

        @Test
        fun `adds @odata context from options`() {
            val udm = UDM.Object.of(
                "ID" to UDM.Scalar.number(1),
                "Name" to UDM.Scalar.string("Widget")
            )

            val output = ODataJSONSerializer(
                mapOf("context" to "\$metadata#Products/\$entity")
            ).serialize(udm)

            output shouldContain "\"@odata.context\""
            output shouldContain "\$metadata#Products/\$entity"
            output shouldContain "\"ID\""
            output shouldContain "\"Name\""
        }

        @Test
        fun `adds @odata context from UDM attributes`() {
            val udm = UDM.Object(
                properties = mapOf(
                    "ID" to UDM.Scalar.number(1)
                ),
                attributes = mapOf(
                    "odata.context" to "\$metadata#Products/\$entity"
                )
            )

            val output = ODataJSONSerializer().serialize(udm)

            output shouldContain "\"@odata.context\""
            output shouldContain "\$metadata#Products/\$entity"
        }

        @Test
        fun `options context takes precedence over UDM attribute`() {
            val udm = UDM.Object(
                properties = mapOf("ID" to UDM.Scalar.number(1)),
                attributes = mapOf("odata.context" to "from-attribute")
            )

            val output = ODataJSONSerializer(
                mapOf("context" to "from-options")
            ).serialize(udm)

            output shouldContain "from-options"
            output shouldNotContain "from-attribute"
        }

        @Test
        fun `minimal mode does not add @odata type or id`() {
            val udm = UDM.Object(
                properties = mapOf("ID" to UDM.Scalar.number(1)),
                attributes = mapOf(
                    "odata.type" to "#Products.Product",
                    "odata.id" to "Products(1)"
                )
            )

            val output = ODataJSONSerializer(
                mapOf("metadata" to "minimal")
            ).serialize(udm)

            output shouldNotContain "\"@odata.type\""
            output shouldNotContain "\"@odata.id\""
        }
    }

    // ==================== Full Metadata ====================

    @Nested
    @DisplayName("Full Metadata Mode")
    inner class FullMetadata {

        @Test
        fun `full mode adds @odata type, id, and etag from attributes`() {
            val udm = UDM.Object(
                properties = mapOf(
                    "ID" to UDM.Scalar.number(1),
                    "Name" to UDM.Scalar.string("Widget")
                ),
                attributes = mapOf(
                    "odata.type" to "#Products.Product",
                    "odata.id" to "Products(1)",
                    "odata.etag" to "W/\"abc\""
                )
            )

            val output = ODataJSONSerializer(
                mapOf("metadata" to "full")
            ).serialize(udm)

            output shouldContain "\"@odata.type\""
            output shouldContain "#Products.Product"
            output shouldContain "\"@odata.id\""
            output shouldContain "Products(1)"
            output shouldContain "\"@odata.etag\""
        }

        @Test
        fun `full mode adds @odata count for wrapped collections`() {
            val udm = UDM.Array.of(
                UDM.Object.of("ID" to UDM.Scalar.number(1)),
                UDM.Object.of("ID" to UDM.Scalar.number(2))
            )

            val output = ODataJSONSerializer(
                mapOf("metadata" to "full", "context" to "\$metadata#Products")
            ).serialize(udm)

            output shouldContain "\"@odata.count\""
            output shouldContain "\"value\""
        }
    }

    // ==================== None Metadata ====================

    @Nested
    @DisplayName("None Metadata Mode")
    inner class NoneMetadata {

        @Test
        fun `none mode does not add OData annotations to plain UDM`() {
            val udm = UDM.Object.of(
                "ID" to UDM.Scalar.number(1),
                "Name" to UDM.Scalar.string("Widget")
            )

            val output = ODataJSONSerializer(
                mapOf("metadata" to "none", "context" to "\$metadata#Products")
            ).serialize(udm)

            // "none" mode should not inject @odata.context even though options provide it
            output shouldNotContain "@odata"
            output shouldContain "\"ID\""
            output shouldContain "\"Name\""
        }

        @Test
        fun `none mode delegates to plain JSONSerializer preserving existing attributes`() {
            // UDM already has attributes — JSONSerializer always re-emits them as @key
            val udm = UDM.Object(
                properties = mapOf(
                    "ID" to UDM.Scalar.number(1)
                ),
                attributes = mapOf(
                    "odata.context" to "\$metadata#Products/\$entity"
                )
            )

            val output = ODataJSONSerializer(
                mapOf("metadata" to "none")
            ).serialize(udm)

            // Existing attributes pass through via JSONSerializer (this is expected)
            output shouldContain "\"@odata.context\""
            output shouldContain "\"ID\""
        }
    }

    // ==================== Collection Wrapping ====================

    @Nested
    @DisplayName("Collection Wrapping")
    inner class CollectionWrapping {

        @Test
        fun `arrays wrapped in value wrapper by default`() {
            val udm = UDM.Array.of(
                UDM.Object.of("ID" to UDM.Scalar.number(1)),
                UDM.Object.of("ID" to UDM.Scalar.number(2))
            )

            val output = ODataJSONSerializer().serialize(udm)

            output shouldContain "\"value\""
        }

        @Test
        fun `wrapCollection false produces bare array`() {
            val udm = UDM.Array.of(
                UDM.Object.of("ID" to UDM.Scalar.number(1))
            )

            val output = ODataJSONSerializer(
                mapOf("wrapCollection" to false)
            ).serialize(udm)

            // Should be a bare JSON array, not wrapped in { "value": [...] }
            output.trim().startsWith("[") shouldBe true
        }

        @Test
        fun `wrapped collection includes @odata context when provided`() {
            val udm = UDM.Array.of(
                UDM.Object.of("ID" to UDM.Scalar.number(1))
            )

            val output = ODataJSONSerializer(
                mapOf("context" to "\$metadata#Products")
            ).serialize(udm)

            output shouldContain "\"@odata.context\""
            output shouldContain "\$metadata#Products"
            output shouldContain "\"value\""
        }
    }

    // ==================== Recursive Annotation ====================

    @Nested
    @DisplayName("Recursive Annotation")
    inner class RecursiveAnnotation {

        @Test
        fun `nested objects are recursively processed`() {
            val udm = UDM.Object.of(
                "Order" to UDM.Object.of(
                    "Items" to UDM.Array.of(
                        UDM.Object.of("Name" to UDM.Scalar.string("Widget"))
                    )
                )
            )

            val output = ODataJSONSerializer(
                mapOf("context" to "\$metadata#Orders")
            ).serialize(udm)

            output shouldContain "\"Order\""
            output shouldContain "\"Items\""
            output shouldContain "\"Widget\""
        }
    }

    // ==================== Round-Trip ====================

    @Nested
    @DisplayName("Round-Trip: Parse then Serialize")
    inner class RoundTrip {

        @Test
        fun `parse and reserialize preserves data properties`() {
            val input = """
                {
                    "@odata.context": "${'$'}metadata#Products/${'$'}entity",
                    "ID": 1,
                    "Name": "Widget",
                    "Price": 29.99
                }
            """.trimIndent()

            // Parse
            val udm = ODataJSONParser(input).parse()

            // Serialize back with context from parsed attributes
            val output = ODataJSONSerializer(mapOf("metadata" to "minimal")).serialize(udm)

            // Data properties preserved
            output shouldContain "\"ID\""
            output shouldContain "\"Name\""
            output shouldContain "\"Price\""
            output shouldContain "\"Widget\""

            // Context annotation re-emitted from UDM attributes
            output shouldContain "\"@odata.context\""
        }

        @Test
        fun `parse minimal then reserialize as none does not add extra annotations`() {
            // Parse: annotations become UDM attributes
            val input = """
                {
                    "@odata.context": "${'$'}metadata#Products/${'$'}entity",
                    "ID": 1,
                    "Name": "Widget"
                }
            """.trimIndent()

            val udm = ODataJSONParser(input).parse()

            // Serialize with none — does not ADD any new annotations,
            // but existing UDM attributes still pass through via JSONSerializer
            val output = ODataJSONSerializer(mapOf("metadata" to "none")).serialize(udm)

            // Data preserved
            output shouldContain "\"ID\""
            output shouldContain "\"Name\""

            // Context attribute passes through from UDM (JSONSerializer re-emits attributes)
            output shouldContain "\"@odata.context\""
        }

        @Test
        fun `collection round-trip preserves elements`() {
            val input = """
                {
                    "@odata.context": "${'$'}metadata#Products",
                    "value": [
                        { "ID": 1, "Name": "Widget" },
                        { "ID": 2, "Name": "Gadget" }
                    ]
                }
            """.trimIndent()

            val udm = ODataJSONParser(input).parse()
            val output = ODataJSONSerializer().serialize(udm)

            output shouldContain "\"Widget\""
            output shouldContain "\"Gadget\""
        }
    }
}
