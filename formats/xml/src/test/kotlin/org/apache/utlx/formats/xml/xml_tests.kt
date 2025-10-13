package org.apache.utlx.formats.xml

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class XMLParserTest {
    @Test
    fun `parse simple element`() {
        val xml = "<root>Hello</root>"
        val result = XML.parse(xml) as UDM.Object
        
        assertEquals("root", result.name)
        assertEquals("Hello", result.get("_text")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse element with attributes`() {
        val xml = """<Order id="123" date="2025-10-01">Content</Order>"""
        val result = XML.parse(xml) as UDM.Object
        
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
        
        val result = XML.parse(xml) as UDM.Object
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
        
        val result = XML.parse(xml) as UDM.Object
        val items = result.get("Item")?.asArray()
        
        assertNotNull(items)
        assertEquals(3, items?.size())
        assertEquals("Widget", (items?.get(0) as? UDM.Object)?.get("_text")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse self-closing element`() {
        val xml = """<Empty/>"""
        val result = XML.parse(xml) as UDM.Object
        
        assertEquals("Empty", result.name)
        assertTrue(result.properties.isEmpty())
    }
    
    @Test
    fun `parse element with attributes only`() {
        val xml = """<Item sku="WIDGET-001" price="29.99"/>"""
        val result = XML.parse(xml) as UDM.Object
        
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
        
        val result = XML.parse(xml) as UDM.Object
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
        
        val result = XML.parse(xml) as UDM.Object
        assertNotNull(result.get("data"))
    }
    
    @Test
    fun `parse with CDATA`() {
        val xml = """<root><![CDATA[<special>content</special>]]></root>"""
        val result = XML.parse(xml) as UDM.Object
        
        assertEquals("<special>content</special>", result.get("_text")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse with entity references`() {
        val xml = """<root>Tom &amp; Jerry &lt;heroes&gt;</root>"""
        val result = XML.parse(xml) as UDM.Object
        
        assertEquals("Tom & Jerry <heroes>", result.get("_text")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse numeric text as number`() {
        val xml = """<Price>49.99</Price>"""
        val result = XML.parse(xml) as UDM.Object
        
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
        
        val result = XML.parse(xml) as UDM.Object
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
        
        // Compare structures
        val obj1 = parsed as UDM.Object
        val obj2 = reparsed as UDM.Object
        
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
        val order = inputUDM as UDM.Object
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
        
        val result = XML.parse(xml) as UDM.Object
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
        
        val result = XML.parse(xml) as UDM.Object
        
        // Should have both text and child element
        assertNotNull(result.get("_text"))
        assertNotNull(result.get("emphasis"))
    }
}
