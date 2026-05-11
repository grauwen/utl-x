package org.apache.utlx.formats.yaml

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * B20: Tests for YAML ByteArray parsing.
 */
class YAMLEncodingTest {

    @Test
    fun `parse UTF-8 bytes`() {
        val yaml = "name: Alice\nage: 30\n"
        val bytes = yaml.toByteArray(Charsets.UTF_8)

        val result = YAMLParser().parse(bytes) as UDM.Object
        assertEquals("Alice", result.get("name")?.asScalar()?.value)
    }

    @Test
    fun `parse UTF-8 bytes with non-ASCII`() {
        val yaml = "city: Zürich\ngreeting: Grüße\n"
        val bytes = yaml.toByteArray(Charsets.UTF_8)

        val result = YAMLParser().parse(bytes) as UDM.Object
        assertEquals("Zürich", result.get("city")?.asScalar()?.value)
        assertEquals("Grüße", result.get("greeting")?.asScalar()?.value)
    }

    @Test
    fun `serialize to UTF-8 bytes`() {
        val udm = UDM.Object.of("name" to UDM.Scalar("Müller"))

        val bytes = YAMLSerializer().serializeToBytes(udm)
        val decoded = String(bytes, Charsets.UTF_8)

        assertTrue(decoded.contains("Müller"), "Output: $decoded")
    }

    @Test
    fun `ByteArray parse matches String parse`() {
        val yaml = "items:\n  - alpha\n  - beta\ncount: 2\n"
        val fromString = YAMLParser().parse(yaml)
        val fromBytes = YAMLParser().parse(yaml.toByteArray(Charsets.UTF_8))

        assertEquals(fromString, fromBytes)
    }
}
