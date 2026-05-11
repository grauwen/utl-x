package org.apache.utlx.formats.json

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * B20: Tests for JSON ByteArray parsing.
 * JSON is always UTF-8 per RFC 8259.
 */
class JSONEncodingTest {

    @Test
    fun `parse UTF-8 bytes`() {
        val json = """{"name": "Alice", "age": 30}"""
        val bytes = json.toByteArray(Charsets.UTF_8)

        val result = JSONParser(bytes).parse() as UDM.Object
        assertEquals("Alice", result.get("name")?.asScalar()?.value)
        assertEquals(30.0, (result.get("age")?.asScalar()?.value as Number).toDouble())
    }

    @Test
    fun `parse UTF-8 bytes with BOM`() {
        val json = """{"city": "Amsterdam"}"""
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + json.toByteArray(Charsets.UTF_8)

        val result = JSONParser(bytes).parse() as UDM.Object
        assertEquals("Amsterdam", result.get("city")?.asScalar()?.value)
    }

    @Test
    fun `parse UTF-8 bytes with non-ASCII characters`() {
        val json = """{"greeting": "Grüße aus Zürich", "emoji": "Hello \u2764"}"""
        val bytes = json.toByteArray(Charsets.UTF_8)

        val result = JSONParser(bytes).parse() as UDM.Object
        assertEquals("Grüße aus Zürich", result.get("greeting")?.asScalar()?.value)
    }

    @Test
    fun `serialize to UTF-8 bytes`() {
        val udm = UDM.Object.of("name" to UDM.Scalar("Müller"), "city" to UDM.Scalar("München"))

        val bytes = JSONSerializer(prettyPrint = false).serializeToBytes(udm)
        val decoded = String(bytes, Charsets.UTF_8)

        assertTrue(decoded.contains("Müller"), "Output: $decoded")
        assertTrue(decoded.contains("München"), "Output: $decoded")
    }

    @Test
    fun `ByteArray constructor produces same result as String constructor`() {
        val json = """{"items": [1, 2, 3], "nested": {"key": "value"}}"""
        val fromString = JSONParser(json).parse()
        val fromBytes = JSONParser(json.toByteArray(Charsets.UTF_8)).parse()

        assertEquals(fromString, fromBytes)
    }

    @Test
    fun `empty JSON object from bytes`() {
        val bytes = "{}".toByteArray(Charsets.UTF_8)
        val result = JSONParser(bytes).parse()
        assertTrue(result is UDM.Object)
    }

    @Test
    fun `JSON array from bytes`() {
        val bytes = """[1, 2, 3]""".toByteArray(Charsets.UTF_8)
        val result = JSONParser(bytes).parse()
        assertTrue(result is UDM.Array)
        assertEquals(3, (result as UDM.Array).elements.size)
    }
}
