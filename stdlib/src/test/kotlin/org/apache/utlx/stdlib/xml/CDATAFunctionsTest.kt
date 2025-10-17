package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CDATAFunctionsTest {

    @Test
    fun testCreateCDATA() {
        val content = "Price: <$100 & tax"
        val result = CDATAFunctions.createCDATA(listOf(UDM.Scalar(content)))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("<![CDATA[Price: <$100 & tax]]>", (result as UDM.Scalar).value)
    }
    
    @Test
    fun testCreateCDATAWithInvalidContent() {
        val contentWithEndMarker = "Some text ]]> more text"
        
        assertThrows<FunctionArgumentException> {
            CDATAFunctions.createCDATA(listOf(UDM.Scalar(contentWithEndMarker)))
        }
    }
    
    @Test
    fun testIsCDATA() {
        val cdataText = "<![CDATA[Some content]]>"
        val result = CDATAFunctions.isCDATA(listOf(UDM.Scalar(cdataText)))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(true, (result as UDM.Scalar).value)
        
        val normalText = "Regular text"
        val result2 = CDATAFunctions.isCDATA(listOf(UDM.Scalar(normalText)))
        
        assertTrue(result2 is UDM.Scalar)
        assertEquals(false, (result2 as UDM.Scalar).value)
    }
    
    @Test
    fun testExtractCDATA() {
        val cdataText = "<![CDATA[Hello <world>]]>"
        val result = CDATAFunctions.extractCDATA(listOf(UDM.Scalar(cdataText)))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("Hello <world>", (result as UDM.Scalar).value)
        
        // Test with non-CDATA text
        val normalText = "Hello world"
        val result2 = CDATAFunctions.extractCDATA(listOf(UDM.Scalar(normalText)))
        
        assertTrue(result2 is UDM.Scalar)
        assertEquals("Hello world", (result2 as UDM.Scalar).value)
    }
    
    @Test
    fun testUnwrapCDATA() {
        val cdataText = "<![CDATA[Content here]]>"
        val result = CDATAFunctions.unwrapCDATA(listOf(UDM.Scalar(cdataText)))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("Content here", (result as UDM.Scalar).value)
    }
    
    @Test
    fun testShouldUseCDATA() {
        // Content with many special characters
        val specialContent = "Price: <$100> & 'tax' \"included\""
        val result = CDATAFunctions.shouldUseCDATA(listOf(UDM.Scalar(specialContent)))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(true, (result as UDM.Scalar).value)
        
        // Simple content
        val simpleContent = "Hello world"
        val result2 = CDATAFunctions.shouldUseCDATA(listOf(UDM.Scalar(simpleContent)))
        
        assertTrue(result2 is UDM.Scalar)
        assertEquals(false, (result2 as UDM.Scalar).value)
        
        // Content with HTML tags
        val htmlContent = "<div>Hello</div>"
        val result3 = CDATAFunctions.shouldUseCDATA(listOf(UDM.Scalar(htmlContent)))
        
        assertTrue(result3 is UDM.Scalar)
        assertEquals(true, (result3 as UDM.Scalar).value)
    }
    
    @Test
    fun testShouldUseCDATAWithThreshold() {
        val content = "Price: <$10"  // Only 2 special chars
        
        // Default threshold (3)
        val result1 = CDATAFunctions.shouldUseCDATA(listOf(UDM.Scalar(content)))
        assertEquals(false, (result1 as UDM.Scalar).value)
        
        // Lower threshold (2)
        val result2 = CDATAFunctions.shouldUseCDATA(listOf(UDM.Scalar(content), UDM.Scalar(2)))
        assertEquals(true, (result2 as UDM.Scalar).value)
    }
    
    @Test
    fun testWrapIfNeeded() {
        // Content that should be wrapped
        val specialContent = "Price: <$100> & 'tax'"
        val result = CDATAFunctions.wrapIfNeeded(listOf(UDM.Scalar(specialContent)))
        
        assertTrue(result is UDM.Scalar)
        val wrapped = (result as UDM.Scalar).value as String
        assertTrue(wrapped.startsWith("<![CDATA["))
        assertTrue(wrapped.endsWith("]]>"))
        
        // Simple content that shouldn't be wrapped
        val simpleContent = "Hello world"
        val result2 = CDATAFunctions.wrapIfNeeded(listOf(UDM.Scalar(simpleContent)))
        
        assertTrue(result2 is UDM.Scalar)
        assertEquals("Hello world", (result2 as UDM.Scalar).value)
    }
    
    @Test
    fun testWrapIfNeededWithForce() {
        val simpleContent = "Hello world"
        
        // Force wrap
        val result = CDATAFunctions.wrapIfNeeded(listOf(UDM.Scalar(simpleContent), UDM.Scalar(true)))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("<![CDATA[Hello world]]>", (result as UDM.Scalar).value)
    }
    
    @Test
    fun testWrapIfNeededWithInvalidContent() {
        val invalidContent = "Content with ]]> inside"
        
        assertThrows<FunctionArgumentException> {
            CDATAFunctions.wrapIfNeeded(listOf(UDM.Scalar(invalidContent), UDM.Scalar(true)))
        }
    }
    
    @Test
    fun testEscapeXML() {
        val content = "Price: <$100> & 'tax' \"included\""
        val result = CDATAFunctions.escapeXML(listOf(UDM.Scalar(content)))
        
        assertTrue(result is UDM.Scalar)
        val escaped = (result as UDM.Scalar).value as String
        assertTrue(escaped.contains("&lt;"))
        assertTrue(escaped.contains("&gt;"))
        assertTrue(escaped.contains("&amp;"))
        assertTrue(escaped.contains("&apos;"))
        assertTrue(escaped.contains("&quot;"))
    }
    
    @Test
    fun testUnescapeXML() {
        val escaped = "Price: &lt;$100&gt; &amp; &apos;tax&apos; &quot;included&quot;"
        val result = CDATAFunctions.unescapeXML(listOf(UDM.Scalar(escaped)))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("Price: <$100> & 'tax' \"included\"", (result as UDM.Scalar).value)
    }
    
    @Test
    fun testUnescapeXMLWithNumericReferences() {
        // Test decimal numeric references
        val withDecimal = "&#65;&#66;&#67;"  // ABC
        val result1 = CDATAFunctions.unescapeXML(listOf(UDM.Scalar(withDecimal)))
        assertEquals("ABC", (result1 as UDM.Scalar).value)
        
        // Test hex numeric references
        val withHex = "&#x41;&#x42;&#x43;"  // ABC
        val result2 = CDATAFunctions.unescapeXML(listOf(UDM.Scalar(withHex)))
        assertEquals("ABC", (result2 as UDM.Scalar).value)
    }
    
    @Test
    fun testRoundTripEscapeUnescape() {
        val original = "Price: <$100> & 'tax' \"included\""
        
        val escaped = CDATAFunctions.escapeXML(listOf(UDM.Scalar(original)))
        val unescaped = CDATAFunctions.unescapeXML(listOf(escaped))
        
        assertEquals(original, (unescaped as UDM.Scalar).value)
    }
    
    @Test
    fun testInvalidArguments() {
        // Test with wrong number of arguments
        assertThrows<FunctionArgumentException> {
            CDATAFunctions.createCDATA(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            CDATAFunctions.createCDATA(listOf(UDM.Scalar("test"), UDM.Scalar("extra")))
        }
        
        assertThrows<FunctionArgumentException> {
            CDATAFunctions.shouldUseCDATA(listOf(UDM.Scalar("test"), UDM.Scalar(2), UDM.Scalar("extra")))
        }
        
        // Test with wrong argument types
        assertThrows<FunctionArgumentException> {
            CDATAFunctions.createCDATA(listOf(UDM.Array(listOf())))
        }
    }
    
    @Test
    fun testEdgeCases() {
        // Empty content
        val emptyResult = CDATAFunctions.createCDATA(listOf(UDM.Scalar("")))
        assertEquals("<![CDATA[]]>", (emptyResult as UDM.Scalar).value)
        
        // Whitespace-only content
        val whitespaceResult = CDATAFunctions.createCDATA(listOf(UDM.Scalar("   \n\t  ")))
        assertEquals("<![CDATA[   \n\t  ]]>", (whitespaceResult as UDM.Scalar).value)
        
        // Already CDATA content
        val alreadyCDATA = "<![CDATA[content]]>"
        val shouldUseResult = CDATAFunctions.shouldUseCDATA(listOf(UDM.Scalar(alreadyCDATA)))
        assertEquals(false, (shouldUseResult as UDM.Scalar).value)
    }
}