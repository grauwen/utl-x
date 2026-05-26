package org.apache.utlx.stdlib

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.string.ExtendedStringFunctions
import org.apache.utlx.stdlib.string.StringFunctions
import org.apache.utlx.stdlib.objects.ObjectFunctions
import org.apache.utlx.stdlib.xml.XMLEncodingBomFunctions
import org.apache.utlx.stdlib.xml.XmlUtilityFunctions
import org.apache.utlx.stdlib.serialization.SerializationFunctions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * F19: Tests for remaining single-function gaps.
 * extractBetween, fromEntries, normalizeXMLEncoding, normalizeBOM, hasContent, parseOdata, renderOdata.
 */
class F19MiscGapsTest {

    // =========================================================================
    // extractBetween(str, start, end) — 3 args
    // =========================================================================

    @Test fun `extractBetween - basic`() {
        val result = ExtendedStringFunctions.extractBetween(listOf(UDM.Scalar("Hello [World] Foo"), UDM.Scalar("["), UDM.Scalar("]")))
        assertEquals("World", (result as UDM.Scalar).value)
    }

    @Test fun `extractBetween - XML tags`() {
        val result = ExtendedStringFunctions.extractBetween(listOf(UDM.Scalar("<name>Alice</name>"), UDM.Scalar("<name>"), UDM.Scalar("</name>")))
        assertEquals("Alice", (result as UDM.Scalar).value)
    }

    @Test fun `extractBetween - no start match returns empty`() {
        val result = ExtendedStringFunctions.extractBetween(listOf(UDM.Scalar("hello world"), UDM.Scalar("["), UDM.Scalar("]")))
        assertEquals("", (result as UDM.Scalar).value)
    }

    @Test fun `extractBetween - no end match returns empty`() {
        val result = ExtendedStringFunctions.extractBetween(listOf(UDM.Scalar("Hello [World"), UDM.Scalar("["), UDM.Scalar("]")))
        assertEquals("", (result as UDM.Scalar).value)
    }

    @Test fun `extractBetween - empty between`() {
        val result = ExtendedStringFunctions.extractBetween(listOf(UDM.Scalar("[]"), UDM.Scalar("["), UDM.Scalar("]")))
        assertEquals("", (result as UDM.Scalar).value)
    }

    @Test fun `extractBetween - wrong arg count throws`() {
        assertThrows<Exception> { ExtendedStringFunctions.extractBetween(listOf(UDM.Scalar("x"), UDM.Scalar("y"))) }
    }

    // =========================================================================
    // fromEntries(array) — 1 arg
    // =========================================================================

    @Test fun `fromEntries - basic`() {
        val entries = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar("name"), UDM.Scalar("Alice"))),
            UDM.Array(listOf(UDM.Scalar("age"), UDM.Scalar(30)))
        ))
        val result = ObjectFunctions.fromEntries(listOf(entries)) as UDM.Object
        assertEquals("Alice", result.get("name")?.asString())
        assertEquals(30, (result.get("age") as UDM.Scalar).value)
    }

    @Test fun `fromEntries - empty array`() {
        val result = ObjectFunctions.fromEntries(listOf(UDM.Array(emptyList()))) as UDM.Object
        assertTrue(result.properties.isEmpty())
    }

    @Test fun `fromEntries - single entry`() {
        val entries = UDM.Array(listOf(UDM.Array(listOf(UDM.Scalar("key"), UDM.Scalar("value")))))
        val result = ObjectFunctions.fromEntries(listOf(entries)) as UDM.Object
        assertEquals("value", result.get("key")?.asString())
    }

    @Test fun `fromEntries - not array throws`() {
        assertThrows<Exception> { ObjectFunctions.fromEntries(listOf(UDM.Scalar("x"))) }
    }

    @Test fun `fromEntries - wrong pair size throws`() {
        val entries = UDM.Array(listOf(UDM.Array(listOf(UDM.Scalar("key")))))
        assertThrows<Exception> { ObjectFunctions.fromEntries(listOf(entries)) }
    }

    // =========================================================================
    // normalizeXMLEncoding(xml, targetEncoding) — 2 args
    // =========================================================================

    @Test fun `normalizeXMLEncoding - changes encoding declaration`() {
        val xml = """<?xml version="1.0" encoding="ISO-8859-1"?><root/>"""
        val result = XMLEncodingBomFunctions.normalizeXMLEncoding(listOf(UDM.Scalar(xml), UDM.Scalar("UTF-8")))
        val output = (result as UDM.Scalar).value as String
        assertTrue(output.contains("UTF-8"), "Should have UTF-8 encoding: $output")
    }

    @Test fun `normalizeXMLEncoding - same encoding unchanged`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><root/>"""
        val result = XMLEncodingBomFunctions.normalizeXMLEncoding(listOf(UDM.Scalar(xml), UDM.Scalar("UTF-8")))
        val output = (result as UDM.Scalar).value as String
        assertEquals(xml, output)
    }

    // =========================================================================
    // normalizeBOM(binary, targetEncoding, addBOM) — 3 args
    // =========================================================================

    @Test fun `normalizeBOM - strip BOM from UTF-8 data`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val text = "Hello".toByteArray(Charsets.UTF_8)
        val withBOM = bom + text
        val result = XMLEncodingBomFunctions.normalizeBOM(listOf(UDM.Binary(withBOM), UDM.Scalar("UTF-8"), UDM.Scalar(false)))
        assertTrue(result is UDM.Binary)
        val bytes = (result as UDM.Binary).data
        assertEquals("Hello", String(bytes, Charsets.UTF_8))
    }

    @Test fun `normalizeBOM - add BOM to data`() {
        val text = "Hello".toByteArray(Charsets.UTF_8)
        val result = XMLEncodingBomFunctions.normalizeBOM(listOf(UDM.Binary(text), UDM.Scalar("UTF-8"), UDM.Scalar(true)))
        assertTrue(result is UDM.Binary)
        val bytes = (result as UDM.Binary).data
        // UTF-8 BOM is 3 bytes + "Hello" is 5 bytes = 8 bytes
        assertEquals(8, bytes.size)
    }

    // =========================================================================
    // hasContent(xml) — 1 arg
    // =========================================================================

    @Test fun `hasContent - element with text`() {
        val xml = UDM.Object.of("_text" to UDM.Scalar("hello"))
        val result = XmlUtilityFunctions.hasContent(listOf(xml))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test fun `hasContent - empty element`() {
        val xml = UDM.Object(emptyMap())
        val result = XmlUtilityFunctions.hasContent(listOf(xml))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test fun `hasContent - element with children`() {
        val xml = UDM.Object.of("child" to UDM.Scalar("value"))
        val result = XmlUtilityFunctions.hasContent(listOf(xml))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    // =========================================================================
    // parseOdata / renderOdata — already tested in RenderFunctionsB23Test
    // Additional edge cases here
    // =========================================================================

    @Test fun `parseOdata - empty object`() {
        val result = SerializationFunctions.parseOdata(listOf(UDM.Object(emptyMap())))
        assertNotNull(result)
    }

    @Test fun `renderOdata - simple object`() {
        val result = SerializationFunctions.renderOdata(listOf(UDM.Object.of("id" to UDM.Scalar(1))))
        val output = (result as UDM.Scalar).value as String
        assertTrue(output.contains("id"), "Should contain id: $output")
        assertTrue(output.contains("1"), "Should contain value: $output")
    }
}
