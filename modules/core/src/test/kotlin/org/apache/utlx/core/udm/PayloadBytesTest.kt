package org.apache.utlx.core.udm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * B20: Tests for PayloadBytes data class.
 */
class PayloadBytesTest {

    @Test
    fun `fromString creates UTF-8 bytes`() {
        val pb = PayloadBytes.fromString("Hello World", "application/json")
        assertEquals("Hello World", pb.decodeToString())
        assertEquals(Charsets.UTF_8, pb.detectedCharset)
        assertEquals("application/json", pb.contentType)
        assertEquals(11, pb.size)
    }

    @Test
    fun `decodeToString uses detected charset`() {
        val text = "Grüße"
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        val pb = PayloadBytes(bytes, Charsets.ISO_8859_1)

        assertEquals("Grüße", pb.decodeToString())
    }

    @Test
    fun `decodeToString defaults to UTF-8`() {
        val text = "Hello"
        val pb = PayloadBytes(text.toByteArray(Charsets.UTF_8))

        assertEquals("Hello", pb.decodeToString())
    }

    @Test
    fun `equality checks byte content`() {
        val a = PayloadBytes("test".toByteArray(), Charsets.UTF_8)
        val b = PayloadBytes("test".toByteArray(), Charsets.UTF_8)
        val c = PayloadBytes("other".toByteArray(), Charsets.UTF_8)

        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `equality considers charset`() {
        val bytes = "test".toByteArray()
        val a = PayloadBytes(bytes, Charsets.UTF_8)
        val b = PayloadBytes(bytes, Charsets.ISO_8859_1)

        assertNotEquals(a, b)
    }
}
