package org.apache.utlx.formats.xml

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * B20: Tests for XML ByteArray parsing and encoding detection.
 * Verifies UTF-8, UTF-16LE, UTF-16BE input and configurable output encoding.
 */
class XMLEncodingTest {

    @Test
    fun `parse UTF-8 bytes without BOM`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><order><id>ORD-001</id></order>"""
        val fromString = XMLParser(xml).parse()
        val fromBytes = XMLParser(xml.toByteArray(Charsets.UTF_8)).parse()

        assertEquals(fromString, fromBytes)
        assertTrue(fromBytes.toString().contains("ORD-001"), "Should contain ORD-001: $fromBytes")
    }

    @Test
    fun `parse UTF-8 bytes with BOM`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><item><name>Widget</name></item>"""
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + xml.toByteArray(Charsets.UTF_8)

        val result = XMLParser(bytes).parse()
        assertTrue(result.toString().contains("Widget"), "Should contain Widget: $result")
    }

    @Test
    fun `parse UTF-16LE bytes with BOM`() {
        val xml = """<?xml version="1.0" encoding="UTF-16"?><product><name>Sensor Pro</name></product>"""
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val bytes = bom + xml.toByteArray(Charsets.UTF_16LE)

        val result = XMLParser(bytes).parse()
        assertTrue(result.toString().contains("Sensor Pro"), "Should contain Sensor Pro: $result")
    }

    @Test
    fun `parse UTF-16BE bytes with BOM`() {
        val xml = """<?xml version="1.0" encoding="UTF-16"?><order><customer>Müller GmbH</customer></order>"""
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val bytes = bom + xml.toByteArray(Charsets.UTF_16BE)

        val result = XMLParser(bytes).parse()
        assertTrue(result.toString().contains("Müller GmbH"), "Should contain Müller GmbH: $result")
    }

    @Test
    fun `parse UTF-8 XML with non-ASCII characters`() {
        val xml = """<data><city>Zürich</city><greeting>Grüße</greeting></data>"""
        val bytes = xml.toByteArray(Charsets.UTF_8)

        val result = XMLParser(bytes).parse()
        val str = result.toString()
        assertTrue(str.contains("Zürich"), "Should contain Zürich: $str")
        assertTrue(str.contains("Grüße"), "Should contain Grüße: $str")
    }

    @Test
    fun `serialize to UTF-16 bytes`() {
        val udm = UDM.Object(
            properties = mapOf("name" to UDM.Scalar("Alice")),
            name = "item"
        )
        val wrapper = UDM.Object(properties = mapOf("item" to udm))

        val serializer = XMLSerializer(prettyPrint = false, outputEncoding = "UTF-16")
        val bytes = serializer.serializeToBytes(wrapper)

        val decoded = String(bytes, Charsets.UTF_16)
        assertTrue(decoded.contains("Alice"), "UTF-16 output should contain 'Alice': $decoded")
        assertTrue(decoded.contains("encoding=\"UTF-16\""), "Should declare UTF-16 encoding: $decoded")
    }

    @Test
    fun `serialize to UTF-8 bytes by default`() {
        val udm = UDM.Object(
            properties = mapOf("city" to UDM.Scalar("Zürich")),
            name = "data"
        )
        val wrapper = UDM.Object(properties = mapOf("data" to udm))

        val serializer = XMLSerializer(prettyPrint = false)
        val bytes = serializer.serializeToBytes(wrapper)

        val decoded = String(bytes, Charsets.UTF_8)
        assertTrue(decoded.contains("Zürich"), "UTF-8 output: $decoded")
    }

    @Test
    fun `round-trip UTF-16 XML`() {
        val xml = """<?xml version="1.0" encoding="UTF-16"?><invoice><name>Schröder</name></invoice>"""

        // Serialize to UTF-16 bytes
        val utf16Bytes = xml.toByteArray(Charsets.UTF_16)

        // Parse the UTF-16 bytes back
        val parsed = XMLParser(utf16Bytes).parse()
        assertTrue(parsed.toString().contains("Schröder"), "Round-trip should preserve Schröder: $parsed")
    }

    @Test
    fun `ByteArray constructor produces same result as String constructor`() {
        val xml = """<order><id>ORD-42</id><total>999.99</total></order>"""
        val fromString = XMLParser(xml).parse()
        val fromBytes = XMLParser(xml.toByteArray(Charsets.UTF_8)).parse()

        assertEquals(fromString, fromBytes)
    }
}
