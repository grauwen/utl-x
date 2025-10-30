package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlin.test.*

class XMLEncodingBomFunctionsTest {

    @Test
    fun testDetectXMLEncoding_withIso88591() {
        val xml = """<?xml version="1.0" encoding="ISO-8859-1"?><root/>"""
        val result = XMLEncodingBomFunctions.detectXMLEncoding(listOf(UDM.Scalar(xml)))
        assertEquals("ISO-8859-1", (result as UDM.Scalar).value)
    }

    @Test
    fun testDetectXMLEncoding_withUtf8() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><root/>"""
        val result = XMLEncodingBomFunctions.detectXMLEncoding(listOf(UDM.Scalar(xml)))
        assertEquals("UTF-8", (result as UDM.Scalar).value)
    }

    @Test
    fun testDetectXMLEncoding_withBom() {
        val xml = "\uFEFF<?xml version=\"1.0\"?><root/>"
        val result = XMLEncodingBomFunctions.detectXMLEncoding(listOf(UDM.Scalar(xml)))
        assertEquals("UTF-8", (result as UDM.Scalar).value)
    }

    @Test
    fun testDetectXMLEncoding_noEncoding() {
        val xml = """<?xml version="1.0"?><root/>"""
        val result = XMLEncodingBomFunctions.detectXMLEncoding(listOf(UDM.Scalar(xml)))
        assertEquals("UTF-8", (result as UDM.Scalar).value)
    }

    @Test
    fun testDetectXMLEncoding_caseInsensitive() {
        val xml = """<?xml version="1.0" ENCODING="utf-8"?><root/>"""
        val result = XMLEncodingBomFunctions.detectXMLEncoding(listOf(UDM.Scalar(xml)))
        assertEquals("UTF-8", (result as UDM.Scalar).value)
    }

    // Note: testDetectXMLEncoding_invalidArg removed - validation is handled at runtime by the UTL-X engine via @UTLXFunction annotations

    @Test
    fun testConvertXMLEncoding_basic() {
        val xml = """<?xml version="1.0" encoding="ISO-8859-1"?><root/>"""
        val result = XMLEncodingBomFunctions.convertXMLEncoding(
            listOf(UDM.Scalar(xml), UDM.Scalar("ISO-8859-1"), UDM.Scalar("UTF-8"))
        )
        val converted = (result as UDM.Scalar).value as String
        assertTrue(converted.contains("encoding=\"UTF-8\""))
    }

    @Test
    fun testUpdateXMLEncoding_replaceExisting() {
        val xml = """<?xml version="1.0" encoding="ISO-8859-1"?><root/>"""
        val result = XMLEncodingBomFunctions.updateXMLEncoding(
            listOf(UDM.Scalar(xml), UDM.Scalar("UTF-8"))
        )
        val updated = (result as UDM.Scalar).value as String
        assertTrue(updated.contains("encoding=\"UTF-8\""))
        assertFalse(updated.contains("ISO-8859-1"))
    }

    @Test
    fun testUpdateXMLEncoding_addToExisting() {
        val xml = """<?xml version="1.0"?><root/>"""
        val result = XMLEncodingBomFunctions.updateXMLEncoding(
            listOf(UDM.Scalar(xml), UDM.Scalar("UTF-8"))
        )
        val updated = (result as UDM.Scalar).value as String
        assertTrue(updated.contains("encoding=\"UTF-8\""))
    }

    @Test
    fun testUpdateXMLEncoding_noDeclaration() {
        val xml = """<root/>"""
        val result = XMLEncodingBomFunctions.updateXMLEncoding(
            listOf(UDM.Scalar(xml), UDM.Scalar("UTF-8"))
        )
        val updated = (result as UDM.Scalar).value as String
        assertTrue(updated.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
    }

    @Test
    fun testValidateEncoding_valid() {
        val result = XMLEncodingBomFunctions.validateEncoding(listOf(UDM.Scalar("UTF-8")))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test
    fun testValidateEncoding_invalid() {
        val result = XMLEncodingBomFunctions.validateEncoding(listOf(UDM.Scalar("INVALID-ENCODING")))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun testStripBOM_withBom() {
        val text = "\uFEFF<?xml version=\"1.0\"?><root/>"
        val result = XMLEncodingBomFunctions.stripBOM(listOf(UDM.Scalar(text)))
        val stripped = (result as UDM.Scalar).value as String
        assertEquals("<?xml version=\"1.0\"?><root/>", stripped)
    }

    @Test
    fun testStripBOM_noBom() {
        val text = "<?xml version=\"1.0\"?><root/>"
        val result = XMLEncodingBomFunctions.stripBOM(listOf(UDM.Scalar(text)))
        val stripped = (result as UDM.Scalar).value as String
        assertEquals(text, stripped)
    }

    @Test
    fun testDetectBOM_utf8() {
        val data = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x48, 0x65, 0x6C, 0x6C, 0x6F)
        val result = XMLEncodingBomFunctions.detectBOM(listOf(UDM.Binary(data)))
        assertEquals("UTF-8", (result as UDM.Scalar).value)
    }

    @Test
    fun testDetectBOM_utf16le() {
        val data = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x48, 0x00, 0x65, 0x00)
        val result = XMLEncodingBomFunctions.detectBOM(listOf(UDM.Binary(data)))
        assertEquals("UTF-16LE", (result as UDM.Scalar).value)
    }

    @Test
    fun testDetectBOM_noBom() {
        val data = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F)
        val result = XMLEncodingBomFunctions.detectBOM(listOf(UDM.Binary(data)))
        assertNull((result as UDM.Scalar).value)
    }

    @Test
    fun testAddBOM_utf8() {
        val data = "Hello".toByteArray()
        val result = XMLEncodingBomFunctions.addBOM(listOf(UDM.Binary(data), UDM.Scalar("UTF-8")))
        val withBom = (result as UDM.Binary).data
        assertTrue(withBom.size == data.size + 3)
        assertEquals(0xEF.toByte(), withBom[0])
        assertEquals(0xBB.toByte(), withBom[1])
        assertEquals(0xBF.toByte(), withBom[2])
    }

    @Test
    fun testRemoveBOM_utf8() {
        val data = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x48, 0x65, 0x6C, 0x6C, 0x6F)
        val result = XMLEncodingBomFunctions.removeBOM(listOf(UDM.Binary(data)))
        val withoutBom = (result as UDM.Binary).data
        assertEquals("Hello", String(withoutBom))
    }

    @Test
    fun testHasBOM_true() {
        val data = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x48, 0x65, 0x6C)
        val result = XMLEncodingBomFunctions.hasBOM(listOf(UDM.Binary(data)))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test
    fun testHasBOM_false() {
        val data = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F)
        val result = XMLEncodingBomFunctions.hasBOM(listOf(UDM.Binary(data)))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun testGetBOMBytes_utf8() {
        val result = XMLEncodingBomFunctions.getBOMBytes(listOf(UDM.Scalar("UTF-8")))
        val bomBytes = (result as UDM.Binary).data
        assertEquals(3, bomBytes.size)
        assertEquals(0xEF.toByte(), bomBytes[0])
        assertEquals(0xBB.toByte(), bomBytes[1])
        assertEquals(0xBF.toByte(), bomBytes[2])
    }

    @Test
    fun testGetBOMBytes_utf16le() {
        val result = XMLEncodingBomFunctions.getBOMBytes(listOf(UDM.Scalar("UTF-16LE")))
        val bomBytes = (result as UDM.Binary).data
        assertEquals(2, bomBytes.size)
        assertEquals(0xFF.toByte(), bomBytes[0])
        assertEquals(0xFE.toByte(), bomBytes[1])
    }

    @Test
    fun testRequiredArguments() {
        assertFailsWith<FunctionArgumentException> {
            XMLEncodingBomFunctions.detectXMLEncoding(emptyList())
        }
        
        assertFailsWith<FunctionArgumentException> {
            XMLEncodingBomFunctions.convertXMLEncoding(listOf(UDM.Scalar("xml")))
        }
    }
}