package org.apache.utlx.formats.odata

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ODataJSONParserTest {

    // ==================== Annotation Extraction ====================

    @Nested
    @DisplayName("OData Annotation Extraction")
    inner class AnnotationExtraction {

        @Test
        fun `top-level @odata annotations become UDM attributes`() {
            val json = """
                {
                    "@odata.context": "${'$'}metadata#Products/${'$'}entity",
                    "@odata.etag": "W/\"12345\"",
                    "ID": 1,
                    "Name": "Widget"
                }
            """.trimIndent()

            val result = ODataJSONParser(json).parse()

            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result as UDM.Object

            // Annotations stored as attributes (without @ prefix)
            obj.attributes["odata.context"] shouldBe "\$metadata#Products/\$entity"
            obj.attributes["odata.etag"] shouldBe "W/\"12345\""

            // Data properties preserved
            obj.properties.containsKey("ID") shouldBe true
            obj.properties.containsKey("Name") shouldBe true

            // Annotations NOT in properties
            obj.properties.containsKey("@odata.context") shouldBe false
            obj.properties.containsKey("@odata.etag") shouldBe false
        }

        @Test
        fun `per-property @odata annotations always extracted as attributes`() {
            val json = """
                {
                    "Name": "Widget",
                    "Name@odata.type": "#String"
                }
            """.trimIndent()

            val result = ODataJSONParser(json).parse()

            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result as UDM.Object

            // Per-property annotation should NOT appear as a data property
            obj.properties.containsKey("Name@odata.type") shouldBe false
            obj.properties.containsKey("Name") shouldBe true
        }

        @Test
        fun `multiple OData annotations on single entity`() {
            val json = """
                {
                    "@odata.context": "${'$'}metadata#Products/${'$'}entity",
                    "@odata.type": "#Products.Product",
                    "@odata.id": "Products(1)",
                    "@odata.etag": "W/\"abc\"",
                    "ID": 1,
                    "Name": "Widget",
                    "Price": 29.99
                }
            """.trimIndent()

            val result = ODataJSONParser(json).parse() as UDM.Object

            result.attributes["odata.context"] shouldNotBe null
            result.attributes["odata.type"] shouldBe "#Products.Product"
            result.attributes["odata.id"] shouldBe "Products(1)"
            result.attributes["odata.etag"] shouldBe "W/\"abc\""

            // All three data properties present
            result.properties.size shouldBe 3
            (result.properties["ID"] as UDM.Scalar).value shouldBe 1L
            (result.properties["Name"] as UDM.Scalar).value shouldBe "Widget"
            (result.properties["Price"] as UDM.Scalar).value shouldBe 29.99
        }
    }

    // ==================== Collection Unwrapping ====================

    @Nested
    @DisplayName("Collection Unwrapping")
    inner class CollectionUnwrapping {

        @Test
        fun `unwrap value array collection with no annotations`() {
            val json = """
                {
                    "value": [
                        { "ID": 1, "Name": "Widget" },
                        { "ID": 2, "Name": "Gadget" }
                    ]
                }
            """.trimIndent()

            val result = ODataJSONParser(json).parse()

            // Should unwrap to array since no OData annotations present
            result.shouldBeInstanceOf<UDM.Array>()
            val arr = result as UDM.Array
            arr.elements.size shouldBe 2

            val first = arr.elements[0] as UDM.Object
            (first.properties["Name"] as UDM.Scalar).value shouldBe "Widget"
        }

        @Test
        fun `preserve wrapper when collection has OData annotations`() {
            val json = """
                {
                    "@odata.context": "${'$'}metadata#Products",
                    "@odata.count": 42,
                    "value": [
                        { "ID": 1, "Name": "Widget" }
                    ]
                }
            """.trimIndent()

            val result = ODataJSONParser(json).parse()

            // Should keep wrapper because OData attributes need to be preserved
            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result as UDM.Object
            obj.attributes["odata.context"] shouldNotBe null
            obj.attributes["odata.count"] shouldBe "42"

            val valueArr = obj.properties["value"] as UDM.Array
            valueArr.elements.size shouldBe 1
        }

        @Test
        fun `do not unwrap when value is not an array`() {
            val json = """
                {
                    "value": "not an array"
                }
            """.trimIndent()

            val result = ODataJSONParser(json).parse()

            // Should stay as object since "value" is not an array
            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result as UDM.Object
            (obj.properties["value"] as UDM.Scalar).value shouldBe "not an array"
        }

        @Test
        fun `do not unwrap when object has other data properties besides value`() {
            val json = """
                {
                    "value": [{ "ID": 1 }],
                    "otherProp": "keep me"
                }
            """.trimIndent()

            val result = ODataJSONParser(json).parse()

            // Should stay as object since there's more than just "value"
            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result as UDM.Object
            obj.properties.containsKey("value") shouldBe true
            obj.properties.containsKey("otherProp") shouldBe true
        }
    }

    // ==================== Full Extraction ====================

    @Nested
    @DisplayName("Full Annotation Extraction")
    inner class FullExtraction {

        @Test
        fun `all annotations always extracted as attributes`() {
            val odataJson = """
                {
                    "@odata.context": "${'$'}metadata#Products/${'$'}entity",
                    "@odata.type": "#Products.Product",
                    "ID": 1,
                    "Name": "Widget"
                }
            """.trimIndent()

            val result = ODataJSONParser(odataJson).parse() as UDM.Object

            // All annotations extracted as attributes
            result.attributes["odata.context"] shouldNotBe null
            result.attributes["odata.type"] shouldBe "#Products.Product"

            // Only data properties remain
            result.properties.size shouldBe 2
            result.properties.containsKey("@odata.context") shouldBe false
            result.properties.containsKey("@odata.type") shouldBe false
        }

        @Test
        fun `options parameter is accepted but metadata level is ignored`() {
            val odataJson = """
                {
                    "@odata.context": "${'$'}metadata#Products/${'$'}entity",
                    "ID": 1
                }
            """.trimIndent()

            // Parser accepts options map for forward compatibility but always does full extraction
            val result = ODataJSONParser(odataJson, mapOf("someOption" to "value")).parse() as UDM.Object

            result.attributes.containsKey("odata.context") shouldBe true
            result.properties.containsKey("@odata.context") shouldBe false
        }
    }

    // ==================== Recursive Processing ====================

    @Nested
    @DisplayName("Recursive Processing")
    inner class RecursiveProcessing {

        @Test
        fun `nested objects have their OData annotations extracted`() {
            val json = """
                {
                    "@odata.context": "${'$'}metadata#Orders/${'$'}entity",
                    "ID": 100,
                    "Customer": {
                        "@odata.type": "#Namespace.Customer",
                        "Name": "Alice"
                    }
                }
            """.trimIndent()

            val result = ODataJSONParser(json).parse() as UDM.Object

            // Root annotations
            result.attributes["odata.context"] shouldNotBe null

            // Nested object annotations
            val customer = result.properties["Customer"] as UDM.Object
            customer.attributes["odata.type"] shouldBe "#Namespace.Customer"
            customer.properties.containsKey("@odata.type") shouldBe false
            (customer.properties["Name"] as UDM.Scalar).value shouldBe "Alice"
        }

        @Test
        fun `array elements have their OData annotations extracted`() {
            val json = """
                {
                    "value": [
                        { "@odata.type": "#Products.Product", "ID": 1 },
                        { "@odata.type": "#Products.Service", "ID": 2 }
                    ]
                }
            """.trimIndent()

            val result = ODataJSONParser(json).parse()

            // Unwrapped to array (no collection-level annotations)
            result.shouldBeInstanceOf<UDM.Array>()
            val arr = result as UDM.Array

            val first = arr.elements[0] as UDM.Object
            first.attributes["odata.type"] shouldBe "#Products.Product"

            val second = arr.elements[1] as UDM.Object
            second.attributes["odata.type"] shouldBe "#Products.Service"
        }
    }

    // ==================== Auto-Detection ====================

    @Nested
    @DisplayName("Auto-Detection")
    inner class AutoDetection {

        @Test
        fun `detects OData JSON by @odata context`() {
            val json = """{ "@odata.context": "${'$'}metadata#Products" }"""
            ODataJSONParser.looksLikeODataJSON(json) shouldBe true
        }

        @Test
        fun `detects OData JSON by @odata type`() {
            val json = """{ "@odata.type": "#Products.Product" }"""
            ODataJSONParser.looksLikeODataJSON(json) shouldBe true
        }

        @Test
        fun `detects OData JSON by @odata id`() {
            val json = """{ "@odata.id": "Products(1)" }"""
            ODataJSONParser.looksLikeODataJSON(json) shouldBe true
        }

        @Test
        fun `plain JSON is not detected as OData`() {
            val json = """{ "name": "Widget", "price": 29.99 }"""
            ODataJSONParser.looksLikeODataJSON(json) shouldBe false
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        fun `entity with no OData annotations parses as plain object`() {
            val json = """{ "ID": 1, "Name": "Widget" }"""

            val result = ODataJSONParser(json).parse() as UDM.Object

            result.attributes.isEmpty() shouldBe true
            result.properties.size shouldBe 2
        }

        @Test
        fun `empty collection wrapper unwraps to empty array`() {
            val json = """{ "value": [] }"""

            val result = ODataJSONParser(json).parse()

            result.shouldBeInstanceOf<UDM.Array>()
            (result as UDM.Array).elements.size shouldBe 0
        }

        @Test
        fun `null annotation value stored as empty string attribute`() {
            val json = """
                {
                    "@odata.context": null,
                    "ID": 1
                }
            """.trimIndent()

            val result = ODataJSONParser(json).parse() as UDM.Object

            result.attributes.containsKey("odata.context") shouldBe true
            result.attributes["odata.context"] shouldBe ""
        }

        @Test
        fun `navigation link annotation extracted`() {
            val json = """
                {
                    "ID": 1,
                    "Category@odata.navigationLink": "Products(1)/Category"
                }
            """.trimIndent()

            val result = ODataJSONParser(json).parse() as UDM.Object

            // Per-property annotation should be stripped from properties
            result.properties.containsKey("Category@odata.navigationLink") shouldBe false
            result.properties.containsKey("ID") shouldBe true
        }
    }
}
