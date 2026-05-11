package org.apache.utlx.formats.csv

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.charset.Charset

/**
 * B20: Tests for CSV ByteArray parsing with different charsets.
 * CSV has no self-describing encoding — charset comes from caller.
 */
class CSVEncodingTest {

    @Test
    fun `parse UTF-8 bytes`() {
        val csv = "name,city\nAlice,Amsterdam\nBob,Berlin"
        val bytes = csv.toByteArray(Charsets.UTF_8)

        val result = CSVParser(bytes).parse() as UDM.Array
        assertEquals(2, result.elements.size)
        assertEquals("Alice", (result.elements[0] as UDM.Object).get("name")?.asScalar()?.value)
    }

    @Test
    fun `parse ISO-8859-1 bytes with European characters`() {
        // ISO-8859-1 encodes ä, ö, ü as single bytes
        val csv = "name,city\nMüller,Zürich\nSchröder,Düsseldorf"
        val bytes = csv.toByteArray(Charsets.ISO_8859_1)

        val result = CSVParser(bytes, Charsets.ISO_8859_1).parse() as UDM.Array
        assertEquals(2, result.elements.size)

        val row1 = result.elements[0] as UDM.Object
        assertEquals("Müller", row1.get("name")?.asScalar()?.value)
        assertEquals("Zürich", row1.get("city")?.asScalar()?.value)

        val row2 = result.elements[1] as UDM.Object
        assertEquals("Schröder", row2.get("name")?.asScalar()?.value)
        assertEquals("Düsseldorf", row2.get("city")?.asScalar()?.value)
    }

    @Test
    fun `parse Windows-1252 bytes`() {
        // Windows-1252 has smart quotes and em dashes in 0x80-0x9F range
        val csv = "name,note\nTest,Hello"
        val bytes = csv.toByteArray(Charset.forName("Windows-1252"))

        val result = CSVParser(bytes, Charset.forName("Windows-1252")).parse() as UDM.Array
        assertEquals(1, result.elements.size)
    }

    @Test
    fun `serialize to ISO-8859-1 bytes`() {
        val udm = UDM.Array(listOf(
            UDM.Object.of("name" to UDM.Scalar("Müller"), "city" to UDM.Scalar("Zürich"))
        ))

        val bytes = CSVSerializer().serializeToBytes(udm, Charsets.ISO_8859_1)
        val decoded = String(bytes, Charsets.ISO_8859_1)

        assertTrue(decoded.contains("Müller"), "Output: $decoded")
        assertTrue(decoded.contains("Zürich"), "Output: $decoded")
    }

    @Test
    fun `ByteArray constructor default UTF-8 matches String constructor`() {
        val csv = "a,b\n1,2\n3,4"
        val fromString = CSVParser(csv).parse()
        val fromBytes = CSVParser(csv.toByteArray(Charsets.UTF_8)).parse()

        assertEquals(fromString, fromBytes)
    }

    @Test
    fun `semicolon dialect with ISO-8859-1`() {
        val csv = "Name;Ort\nMüller;München"
        val bytes = csv.toByteArray(Charsets.ISO_8859_1)

        val result = CSVParser(bytes, Charsets.ISO_8859_1, CSVDialect.SEMICOLON).parse() as UDM.Array
        val row = result.elements[0] as UDM.Object
        assertEquals("Müller", row.get("Name")?.asScalar()?.value)
        assertEquals("München", row.get("Ort")?.asScalar()?.value)
    }
}
