package org.apache.utlx.formats.xml

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

/**
 * Helper function to unwrap the root element from the wrapper object
 *
 * Since XML parser now wraps root elements to preserve the root element name,
 * <root>...</root> becomes { "root": UDM.Object(...) }
 * This helper extracts the actual root element.
 */
fun UDM.Object.unwrapRoot(): UDM.Object {
    // Wrapper should have exactly one property which is the root element
    val rootPropertyName = this.properties.keys.first()
    return this.get(rootPropertyName)?.asObject()
        ?: throw IllegalStateException("Expected root element in wrapper")
}

class XMLParserTest {
    @Test
    fun `parse simple element`() {
        val xml = "<root>Hello</root>"
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()

        assertEquals("root", result.name)
        assertEquals("Hello", result.get("_text")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse element with attributes`() {
        val xml = """<Order id="123" date="2025-10-01">Content</Order>"""
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        
        assertEquals("Order", result.name)
        assertEquals("123", result.getAttribute("id"))
        assertEquals("2025-10-01", result.getAttribute("date"))
    }
    
    @Test
    fun `parse nested elements`() {
        val xml = """
            <Order>
              <Customer>Alice</Customer>
              <Total>299.99</Total>
            </Order>
        """.trimIndent()
        
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        assertEquals("Order", result.name)
        
        val customer = result.get("Customer")?.asObject()
        assertNotNull(customer)
        assertEquals("Alice", customer?.get("_text")?.asScalar()?.asString())
        
        val total = result.get("Total")?.asObject()
        assertEquals(299.99, total?.get("_text")?.asScalar()?.asNumber())
    }
    
    @Test
    fun `parse multiple same-name elements as array`() {
        val xml = """
            <Items>
              <Item>Widget</Item>
              <Item>Gadget</Item>
              <Item>Tool</Item>
            </Items>
        """.trimIndent()
        
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        val items = result.get("Item")?.asArray()
        
        assertNotNull(items)
        assertEquals(3, items?.size())
        assertEquals("Widget", (items?.get(0) as? UDM.Object)?.get("_text")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse self-closing element`() {
        val xml = """<Empty/>"""
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        
        assertEquals("Empty", result.name)
        assertTrue(result.properties.isEmpty())
    }
    
    @Test
    fun `parse element with attributes only`() {
        val xml = """<Item sku="WIDGET-001" price="29.99"/>"""
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        
        assertEquals("Item", result.name)
        assertEquals("WIDGET-001", result.getAttribute("sku"))
        assertEquals("29.99", result.getAttribute("price"))
    }
    
    @Test
    fun `parse with XML declaration`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>Content</root>
        """.trimIndent()
        
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        assertEquals("root", result.name)
    }
    
    @Test
    fun `parse with comments`() {
        val xml = """
            <root>
              <!-- This is a comment -->
              <data>Value</data>
            </root>
        """.trimIndent()
        
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        assertNotNull(result.get("data"))
    }
    
    @Test
    fun `parse with CDATA`() {
        val xml = """<root><![CDATA[<special>content</special>]]></root>"""
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        
        assertEquals("<special>content</special>", result.get("_text")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse with entity references`() {
        val xml = """<root>Tom &amp; Jerry &lt;heroes&gt;</root>"""
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        
        assertEquals("Tom & Jerry <heroes>", result.get("_text")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse numeric text as number`() {
        val xml = """<Price>49.99</Price>"""
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        
        assertEquals(49.99, result.get("_text")?.asScalar()?.asNumber())
    }
    
    @Test
    fun `parse complex nested structure`() {
        val xml = """
            <Order id="ORD-001" date="2025-10-01">
              <Customer type="VIP">
                <Name>Alice Johnson</Name>
                <Email>alice@example.com</Email>
              </Customer>
              <Items>
                <Item sku="WIDGET-001" quantity="2" price="75.00"/>
                <Item sku="GADGET-002" quantity="1" price="150.00"/>
              </Items>
              <Total>300.00</Total>
            </Order>
        """.trimIndent()
        
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        assertEquals("Order", result.name)
        assertEquals("ORD-001", result.getAttribute("id"))
        
        val customer = result.get("Customer")?.asObject()
        assertEquals("VIP", customer?.getAttribute("type"))
        assertEquals("Alice Johnson", 
            customer?.get("Name")?.asObject()?.get("_text")?.asScalar()?.asString())
        
        val items = result.get("Items")?.asObject()?.get("Item")?.asArray()
        assertEquals(2, items?.size())
    }
    
    @Test
    fun `error on mismatched tags`() {
        assertThrows<XMLParseException> {
            XML.parse("<start>content</end>")
        }
    }
    
    @Test
    fun `error on unclosed tag`() {
        assertThrows<XMLParseException> {
            XML.parse("<root>content")
        }
    }
    
    @Test
    fun `error on invalid attribute syntax`() {
        assertThrows<XMLParseException> {
            XML.parse("<root attr=value></root>")
        }
    }
}

class XMLSerializerTest {
    @Test
    fun `serialize simple scalar`() {
        val udm = UDM.Scalar.string("Hello")
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(udm, "root")
        
        assertEquals("<root>Hello</root>", xml.trim())
    }
    
    @Test
    fun `serialize object with text`() {
        val udm = UDM.Object(
            properties = mapOf("_text" to UDM.Scalar.string("Content")),
            name = "element"
        )
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(udm)
        
        assertEquals("<element>Content</element>", xml.trim())
    }
    
    @Test
    fun `serialize object with attributes`() {
        val udm = UDM.Object(
            properties = mapOf("_text" to UDM.Scalar.string("Value")),
            attributes = mapOf("id" to "123", "type" to "test"),
            name = "Item"
        )
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(udm)
        
        assertTrue(xml.contains("id=\"123\""))
        assertTrue(xml.contains("type=\"test\""))
        assertTrue(xml.contains(">Value</Item>"))
    }
    
    @Test
    fun `serialize nested structure`() {
        val udm = UDM.Object(
            properties = mapOf(
                "Customer" to UDM.Object(
                    properties = mapOf("_text" to UDM.Scalar.string("Alice")),
                    name = "Customer"
                ),
                "Total" to UDM.Object(
                    properties = mapOf("_text" to UDM.Scalar.number(299.99)),
                    name = "Total"
                )
            ),
            name = "Order"
        )
        
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(udm)
        
        assertTrue(xml.contains("<Order>"))
        assertTrue(xml.contains("<Customer>Alice</Customer>"))
        assertTrue(xml.contains("<Total>299.99</Total>"))
        assertTrue(xml.contains("</Order>"))
    }
    
    @Test
    fun `serialize array as multiple elements`() {
        val udm = UDM.Array(listOf(
            UDM.Object(mapOf("_text" to UDM.Scalar.string("First")), name = "Item"),
            UDM.Object(mapOf("_text" to UDM.Scalar.string("Second")), name = "Item"),
            UDM.Object(mapOf("_text" to UDM.Scalar.string("Third")), name = "Item")
        ))
        
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(udm, "Item")
        
        assertEquals(3, xml.split("<Item>").size - 1)
    }
    
    @Test
    fun `serialize empty element`() {
        val udm = UDM.Object(emptyMap(), name = "Empty")
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(udm)
        
        assertEquals("<Empty/>", xml.trim())
    }
    
    @Test
    fun `serialize with special characters escaped`() {
        val udm = UDM.Object(
            properties = mapOf("_text" to UDM.Scalar.string("Tom & Jerry <heroes>")),
            name = "root"
        )
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(udm)
        
        assertTrue(xml.contains("Tom &amp; Jerry &lt;heroes&gt;"))
    }
    
    @Test
    fun `serialize with XML declaration`() {
        val udm = UDM.Scalar.string("Content")
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = true).serialize(udm, "root")
        
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
    }
    
    @Test
    fun `serialize with pretty print`() {
        val udm = UDM.Object(
            properties = mapOf(
                "Child" to UDM.Object(mapOf("_text" to UDM.Scalar.string("Value")), name = "Child")
            ),
            name = "Root"
        )
        
        val xml = XMLSerializer(prettyPrint = true, includeDeclaration = false).serialize(udm)
        
        assertTrue(xml.contains("\n"))
        assertTrue(xml.contains("  ")) // Indentation
    }
    
    @Test
    fun `round trip - parse and serialize`() {
        val original = """
            <Order id="123">
              <Customer>Alice</Customer>
              <Total>299.99</Total>
            </Order>
        """.trimIndent()
        
        val parsed = XML.parse(original)
        val serialized = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(parsed)
        val reparsed = XML.parse(serialized)
        
        // Compare structures (unwrap root elements)
        val obj1 = (parsed as UDM.Object).unwrapRoot()
        val obj2 = (reparsed as UDM.Object).unwrapRoot()
        
        assertEquals(obj1.name, obj2.name)
        assertEquals(obj1.getAttribute("id"), obj2.getAttribute("id"))
    }
}

class XMLIntegrationTest {
    @Test
    fun `complete transformation - XML to XML`() {
        val inputXML = """
            <Order id="ORD-001" date="2025-10-01">
              <Customer type="VIP">
                <Name>Alice Johnson</Name>
                <Email>alice@example.com</Email>
              </Customer>
              <Total>299.99</Total>
            </Order>
        """.trimIndent()
        
        // Parse
        val inputUDM = XML.parse(inputXML)
        
        // Transform (would use UTL-X interpreter here)
        // For now, just verify structure
        val order = (inputUDM as UDM.Object).unwrapRoot()
        assertEquals("Order", order.name)
        assertEquals("ORD-001", order.getAttribute("id"))
        
        // Serialize back
        val outputXML = XMLFormat.stringify(inputUDM)
        
        // Verify can be parsed again
        val reparsed = XML.parse(outputXML)
        assertTrue(reparsed is UDM.Object)
    }
    
    @Test
    fun `parse complex real-world XML`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <catalog>
              <book id="bk101" category="programming">
                <author>Gamma, Erich</author>
                <title>Design Patterns</title>
                <price>44.95</price>
                <publish_date>1994-10-01</publish_date>
              </book>
              <book id="bk102" category="programming">
                <author>Fowler, Martin</author>
                <title>Refactoring</title>
                <price>39.95</price>
                <publish_date>1999-07-01</publish_date>
              </book>
            </catalog>
        """.trimIndent()
        
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        assertEquals("catalog", result.name)
        
        val books = result.get("book")?.asArray()
        assertNotNull(books)
        assertEquals(2, books?.size())
        
        val firstBook = books?.get(0)?.asObject()
        assertEquals("bk101", firstBook?.getAttribute("id"))
        assertEquals("programming", firstBook?.getAttribute("category"))
    }
    
    @Test
    fun `handle mixed content appropriately`() {
        val xml = """
            <paragraph>
              This is some text with <emphasis>emphasized</emphasis> content.
            </paragraph>
        """.trimIndent()
        
        val result = (XML.parse(xml) as UDM.Object).unwrapRoot()
        
        // Should have both text and child element
        assertNotNull(result.get("_text"))
        assertNotNull(result.get("emphasis"))
    }
}

/**
 * Tests for XML @attribute syntax in UTL-X transformations
 *
 * These tests verify that the parser accepts @attribute notation for:
 * 1. Writing XML attributes in output (@id:, @name:, etc.)
 * 2. Reading XML attributes from input ($input.Order.@id)
 *
 * IMPORTANT: These tests currently FAIL due to parser bug documented in CONFORMANCE_ISSUES.md
 * Parser rejects @attribute syntax with error: "Object key must be a string"
 *
 * Once the parser is fixed to accept @ prefix in object keys, these tests should pass.
 */
class XMLAttributeSyntaxTest {

    // Helper to parse and execute a UTL-X transformation
    private fun executeTransformation(transformation: String, input: UDM): UDM {
        val tokens = org.apache.utlx.core.lexer.Lexer(transformation).tokenize()
        val parseResult = org.apache.utlx.core.parser.Parser(tokens).parse()

        if (parseResult is org.apache.utlx.core.parser.ParseResult.Failure) {
            val errors = parseResult.errors.joinToString("\n") { "  - $it" }
            throw RuntimeException("Parse failed:\n$errors")
        }

        val program = (parseResult as org.apache.utlx.core.parser.ParseResult.Success).program
        val runtimeValue = org.apache.utlx.core.interpreter.Interpreter().execute(program, input)
        return runtimeValue.toUDM()
    }

    @Test
    fun `parse and accept @attribute syntax for single attribute output`() {
        val transformation = """
            %utlx 1.0
            input json
            output xml
            ---
            {
              Order: {
                @id: "ORD-123",
                Customer: "Alice"
              }
            }
        """.trimIndent()

        val input = org.apache.utlx.formats.json.JSON.parse("""{"customer": "Alice"}""")
        val result = executeTransformation(transformation, input)
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(result)

        assertTrue(xml.contains("<Order id=\"ORD-123\">"))
        assertTrue(xml.contains("<Customer>Alice</Customer>"))
    }

    @Test
    fun `parse and accept @attribute syntax for multiple attributes`() {
        val transformation = """
            %utlx 1.0
            input json
            output xml
            ---
            {
              Order: {
                @id: "123",
                @date: "2025-10-31",
                @status: "pending"
              }
            }
        """.trimIndent()

        val input = org.apache.utlx.formats.json.JSON.parse("""{}""")
        val result = executeTransformation(transformation, input)
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(result)

        assertTrue(xml.contains("id=\"123\""))
        assertTrue(xml.contains("date=\"2025-10-31\""))
        assertTrue(xml.contains("status=\"pending\""))
    }

    @Test
    fun `parse and accept @attribute syntax combined with _text content`() {
        val transformation = """
            %utlx 1.0
            input json
            output xml
            ---
            {
              Customer: {
                @email: "alice@example.com",
                _text: "Alice Johnson"
              }
            }
        """.trimIndent()

        val input = org.apache.utlx.formats.json.JSON.parse("""{}""")
        val result = executeTransformation(transformation, input)
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(result)

        assertTrue(xml.contains("<Customer email=\"alice@example.com\">Alice Johnson</Customer>"))
    }

    @Test
    fun `parse and accept @attribute syntax with nested elements`() {
        val transformation = """
            %utlx 1.0
            input json
            output xml
            ---
            {
              Order: {
                @id: "ORD-456",
                Customer: {
                  Name: "Bob"
                },
                Total: 299.99
              }
            }
        """.trimIndent()

        val input = org.apache.utlx.formats.json.JSON.parse("""{}""")
        val result = executeTransformation(transformation, input)
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(result)

        assertTrue(xml.contains("<Order id=\"ORD-456\">"))
        assertTrue(xml.contains("<Customer>"))
        assertTrue(xml.contains("<Name>Bob</Name>"))
        assertTrue(xml.contains("<Total>299.99</Total>"))
    }

    @Test
    fun `parse and accept @attribute syntax with expression values`() {
        val transformation = """
            %utlx 1.0
            input json
            output xml
            ---
            {
              Invoice: {
                @number: "INV-" + ${"$"}input.year + "-" + ${"$"}input.id,
                Status: "generated"
              }
            }
        """.trimIndent()

        val input = org.apache.utlx.formats.json.JSON.parse("""{"year": "2025", "id": "123"}""")
        val result = executeTransformation(transformation, input)
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(result)

        assertTrue(xml.contains("<Invoice number=\"INV-2025-123\">"))
        assertTrue(xml.contains("<Status>generated</Status>"))
    }

    @Test
    fun `read XML attributes from input using @attribute syntax`() {
        val transformation = """
            %utlx 1.0
            input xml
            output json
            ---
            {
              orderId: ${"$"}input.Order.@id,
              orderDate: ${"$"}input.Order.@date,
              customer: ${"$"}input.Order.Customer
            }
        """.trimIndent()

        val inputXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Order id="ORD-789" date="2025-10-31">
              <Customer>Charlie</Customer>
            </Order>
        """.trimIndent()

        val input = XML.parse(inputXml)
        val result = executeTransformation(transformation, input)
        val json = org.apache.utlx.formats.json.JSON.stringify(result)

        assertTrue(json.contains("\"orderId\""))
        assertTrue(json.contains("\"ORD-789\""))
        assertTrue(json.contains("\"orderDate\""))
        assertTrue(json.contains("\"2025-10-31\""))
        assertTrue(json.contains("\"customer\""))
        assertTrue(json.contains("\"Charlie\""))
    }

    @Test
    fun `transform XML to XML preserving and modifying attributes`() {
        val transformation = """
            %utlx 1.0
            input xml
            output xml
            ---
            {
              Invoice: {
                @number: "INV-" + ${"$"}input.Order.@id,
                @invoiceType: ${"$"}input.Order.@type,
                CustomerName: ${"$"}input.Order.Customer,
                Amount: ${"$"}input.Order.Total
              }
            }
        """.trimIndent()

        val inputXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Order id="ORD-999" type="standard">
              <Customer>Dave</Customer>
              <Total>499.99</Total>
            </Order>
        """.trimIndent()

        val input = XML.parse(inputXml)
        val result = executeTransformation(transformation, input)
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(result)

        assertTrue(xml.contains("<Invoice"))
        assertTrue(xml.contains("number=\"INV-ORD-999\""))
        assertTrue(xml.contains("invoiceType=\"standard\""))
        assertTrue(xml.contains("<CustomerName>Dave</CustomerName>"))
        assertTrue(xml.contains("<Amount>499.99</Amount>"))
    }

    @Test
    fun `handle attribute values with special characters requiring escaping`() {
        val transformation = """
            %utlx 1.0
            input json
            output xml
            ---
            {
              Company: {
                @name: ${"$"}input.title,
                @note: ${"$"}input.note
              }
            }
        """.trimIndent()

        val input = org.apache.utlx.formats.json.JSON.parse("""{"title": "Smith & Co", "note": "A < B"}""")
        val result = executeTransformation(transformation, input)
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(result)

        assertTrue(xml.contains("name=\"Smith &amp; Co\""))
        assertTrue(xml.contains("note=\"A &lt; B\""))
    }

    @Test
    fun `handle numeric and boolean attribute values`() {
        val transformation = """
            %utlx 1.0
            input json
            output xml
            ---
            {
              Product: {
                @quantity: ${"$"}input.quantity,
                @price: ${"$"}input.price,
                @inStock: ${"$"}input.inStock
              }
            }
        """.trimIndent()

        val input = org.apache.utlx.formats.json.JSON.parse("""{"quantity": 42, "price": 29.99, "inStock": true}""")
        val result = executeTransformation(transformation, input)
        val xml = XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(result)

        assertTrue(xml.contains("quantity=\"42\""))
        assertTrue(xml.contains("price=\"29.99\""))
        assertTrue(xml.contains("inStock=\"true\""))
    }
}
