// stdlib/src/test/kotlin/org/apache/utlx/stdlib/string/CharacterFunctionsTest.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterFunctionsTest {
    
    // ============================================
    // isAlpha() TESTS
    // ============================================
    
    @Test
    fun `test isAlpha with pure alphabetic strings`() {
        assertTrue(CharacterFunctions.isAlpha(listOf(UDM.Scalar("Hello"))).asBoolean())
        assertTrue(CharacterFunctions.isAlpha(listOf(UDM.Scalar("abc"))).asBoolean())
        assertTrue(CharacterFunctions.isAlpha(listOf(UDM.Scalar("XYZ"))).asBoolean())
    }
    
    @Test
    fun `test isAlpha with unicode letters`() {
        assertTrue(CharacterFunctions.isAlpha(listOf(UDM.Scalar("Ñoño"))).asBoolean())
        assertTrue(CharacterFunctions.isAlpha(listOf(UDM.Scalar("café"))).asBoolean())
        assertTrue(CharacterFunctions.isAlpha(listOf(UDM.Scalar("Москва"))).asBoolean())
    }
    
    @Test
    fun `test isAlpha with mixed content returns false`() {
        assertFalse(CharacterFunctions.isAlpha(listOf(UDM.Scalar("Hello123"))).asBoolean())
        assertFalse(CharacterFunctions.isAlpha(listOf(UDM.Scalar("Hello World"))).asBoolean())
        assertFalse(CharacterFunctions.isAlpha(listOf(UDM.Scalar("abc-def"))).asBoolean())
    }
    
    @Test
    fun `test isAlpha with empty string returns false`() {
        assertFalse(CharacterFunctions.isAlpha(listOf(UDM.Scalar(""))).asBoolean())
    }
    
    @Test
    fun `test isAlpha with numbers only returns false`() {
        assertFalse(CharacterFunctions.isAlpha(listOf(UDM.Scalar("123"))).asBoolean())
    }
    
    // ============================================
    // isNumeric() TESTS
    // ============================================
    
    @Test
    fun `test isNumeric with pure numeric strings`() {
        assertTrue(CharacterFunctions.isNumeric(listOf(UDM.Scalar("12345"))).asBoolean())
        assertTrue(CharacterFunctions.isNumeric(listOf(UDM.Scalar("0"))).asBoolean())
        assertTrue(CharacterFunctions.isNumeric(listOf(UDM.Scalar("999"))).asBoolean())
    }
    
    @Test
    fun `test isNumeric with decimal point returns false`() {
        assertFalse(CharacterFunctions.isNumeric(listOf(UDM.Scalar("123.45"))).asBoolean())
    }
    
    @Test
    fun `test isNumeric with mixed content returns false`() {
        assertFalse(CharacterFunctions.isNumeric(listOf(UDM.Scalar("123abc"))).asBoolean())
        assertFalse(CharacterFunctions.isNumeric(listOf(UDM.Scalar("1 2 3"))).asBoolean())
    }
    
    @Test
    fun `test isNumeric with negative sign returns false`() {
        assertFalse(CharacterFunctions.isNumeric(listOf(UDM.Scalar("-123"))).asBoolean())
    }
    
    @Test
    fun `test isNumeric with empty string returns false`() {
        assertFalse(CharacterFunctions.isNumeric(listOf(UDM.Scalar(""))).asBoolean())
    }
    
    // ============================================
    // isAlphanumeric() TESTS
    // ============================================
    
    @Test
    fun `test isAlphanumeric with mixed letters and numbers`() {
        assertTrue(CharacterFunctions.isAlphanumeric(listOf(UDM.Scalar("Hello123"))).asBoolean())
        assertTrue(CharacterFunctions.isAlphanumeric(listOf(UDM.Scalar("abc123xyz"))).asBoolean())
    }
    
    @Test
    fun `test isAlphanumeric with only letters`() {
        assertTrue(CharacterFunctions.isAlphanumeric(listOf(UDM.Scalar("abc"))).asBoolean())
    }
    
    @Test
    fun `test isAlphanumeric with only numbers`() {
        assertTrue(CharacterFunctions.isAlphanumeric(listOf(UDM.Scalar("123"))).asBoolean())
    }
    
    @Test
    fun `test isAlphanumeric with special characters returns false`() {
        assertFalse(CharacterFunctions.isAlphanumeric(listOf(UDM.Scalar("Hello 123"))).asBoolean())
        assertFalse(CharacterFunctions.isAlphanumeric(listOf(UDM.Scalar("abc-123"))).asBoolean())
        assertFalse(CharacterFunctions.isAlphanumeric(listOf(UDM.Scalar("test@123"))).asBoolean())
    }
    
    @Test
    fun `test isAlphanumeric with empty string returns false`() {
        assertFalse(CharacterFunctions.isAlphanumeric(listOf(UDM.Scalar(""))).asBoolean())
    }
    
    // ============================================
    // isWhitespace() TESTS
    // ============================================
    
    @Test
    fun `test isWhitespace with spaces`() {
        assertTrue(CharacterFunctions.isWhitespace(listOf(UDM.Scalar("   "))).asBoolean())
    }
    
    @Test
    fun `test isWhitespace with tabs and newlines`() {
        assertTrue(CharacterFunctions.isWhitespace(listOf(UDM.Scalar("\t\n\r"))).asBoolean())
    }
    
    @Test
    fun `test isWhitespace with mixed whitespace`() {
        assertTrue(CharacterFunctions.isWhitespace(listOf(UDM.Scalar(" \t \n "))).asBoolean())
    }
    
    @Test
    fun `test isWhitespace with text returns false`() {
        assertFalse(CharacterFunctions.isWhitespace(listOf(UDM.Scalar("Hello World"))).asBoolean())
        assertFalse(CharacterFunctions.isWhitespace(listOf(UDM.Scalar(" a "))).asBoolean())
    }
    
    @Test
    fun `test isWhitespace with empty string returns false`() {
        assertFalse(CharacterFunctions.isWhitespace(listOf(UDM.Scalar(""))).asBoolean())
    }
    
    // ============================================
    // isUpperCase() TESTS
    // ============================================
    
    @Test
    fun `test isUpperCase with all uppercase letters`() {
        assertTrue(CharacterFunctions.isUpperCase(listOf(UDM.Scalar("HELLO"))).asBoolean())
        assertTrue(CharacterFunctions.isUpperCase(listOf(UDM.Scalar("ABC"))).asBoolean())
    }
    
    @Test
    fun `test isUpperCase ignores non-letters`() {
        assertTrue(CharacterFunctions.isUpperCase(listOf(UDM.Scalar("HELLO123"))).asBoolean())
        assertTrue(CharacterFunctions.isUpperCase(listOf(UDM.Scalar("HELLO WORLD"))).asBoolean())
        assertTrue(CharacterFunctions.isUpperCase(listOf(UDM.Scalar("A-B-C"))).asBoolean())
    }
    
    @Test
    fun `test isUpperCase with no letters returns true`() {
        assertTrue(CharacterFunctions.isUpperCase(listOf(UDM.Scalar("123"))).asBoolean())
        assertTrue(CharacterFunctions.isUpperCase(listOf(UDM.Scalar("!@#"))).asBoolean())
    }
    
    @Test
    fun `test isUpperCase with mixed case returns false`() {
        assertFalse(CharacterFunctions.isUpperCase(listOf(UDM.Scalar("Hello"))).asBoolean())
        assertFalse(CharacterFunctions.isUpperCase(listOf(UDM.Scalar("HeLLo"))).asBoolean())
    }
    
    @Test
    fun `test isUpperCase with all lowercase returns false`() {
        assertFalse(CharacterFunctions.isUpperCase(listOf(UDM.Scalar("hello"))).asBoolean())
    }
    
    @Test
    fun `test isUpperCase with empty string returns false`() {
        assertFalse(CharacterFunctions.isUpperCase(listOf(UDM.Scalar(""))).asBoolean())
    }
    
    // ============================================
    // isLowerCase() TESTS
    // ============================================
    
    @Test
    fun `test isLowerCase with all lowercase letters`() {
        assertTrue(CharacterFunctions.isLowerCase(listOf(UDM.Scalar("hello"))).asBoolean())
        assertTrue(CharacterFunctions.isLowerCase(listOf(UDM.Scalar("abc"))).asBoolean())
    }
    
    @Test
    fun `test isLowerCase ignores non-letters`() {
        assertTrue(CharacterFunctions.isLowerCase(listOf(UDM.Scalar("hello123"))).asBoolean())
        assertTrue(CharacterFunctions.isLowerCase(listOf(UDM.Scalar("hello world"))).asBoolean())
        assertTrue(CharacterFunctions.isLowerCase(listOf(UDM.Scalar("a-b-c"))).asBoolean())
    }
    
    @Test
    fun `test isLowerCase with no letters returns true`() {
        assertTrue(CharacterFunctions.isLowerCase(listOf(UDM.Scalar("123"))).asBoolean())
    }
    
    @Test
    fun `test isLowerCase with mixed case returns false`() {
        assertFalse(CharacterFunctions.isLowerCase(listOf(UDM.Scalar("Hello"))).asBoolean())
    }
    
    @Test
    fun `test isLowerCase with empty string returns false`() {
        assertFalse(CharacterFunctions.isLowerCase(listOf(UDM.Scalar(""))).asBoolean())
    }
    
    // ============================================
    // isPrintable() TESTS
    // ============================================
    
    @Test
    fun `test isPrintable with normal text`() {
        assertTrue(CharacterFunctions.isPrintable(listOf(UDM.Scalar("Hello World!"))).asBoolean())
        assertTrue(CharacterFunctions.isPrintable(listOf(UDM.Scalar("abc123!@#"))).asBoolean())
    }
    
    @Test
    fun `test isPrintable with control characters returns false`() {
        assertFalse(CharacterFunctions.isPrintable(listOf(UDM.Scalar("Line1\nLine2"))).asBoolean())
        assertFalse(CharacterFunctions.isPrintable(listOf(UDM.Scalar("Tab\there"))).asBoolean())
    }
    
    @Test
    fun `test isPrintable with empty string returns false`() {
        assertFalse(CharacterFunctions.isPrintable(listOf(UDM.Scalar(""))).asBoolean())
    }
    
    // ============================================
    // isAscii() TESTS
    // ============================================
    
    @Test
    fun `test isAscii with ASCII characters`() {
        assertTrue(CharacterFunctions.isAscii(listOf(UDM.Scalar("Hello"))).asBoolean())
        assertTrue(CharacterFunctions.isAscii(listOf(UDM.Scalar("Hello123"))).asBoolean())
        assertTrue(CharacterFunctions.isAscii(listOf(UDM.Scalar("!@#$%^&*()"))).asBoolean())
    }
    
    @Test
    fun `test isAscii with unicode characters returns false`() {
        assertFalse(CharacterFunctions.isAscii(listOf(UDM.Scalar("Ñoño"))).asBoolean())
        assertFalse(CharacterFunctions.isAscii(listOf(UDM.Scalar("café"))).asBoolean())
        assertFalse(CharacterFunctions.isAscii(listOf(UDM.Scalar("你好"))).asBoolean())
    }
    
    @Test
    fun `test isAscii with empty string returns false`() {
        assertFalse(CharacterFunctions.isAscii(listOf(UDM.Scalar(""))).asBoolean())
    }
    
    // ============================================
    // isHexadecimal() TESTS
    // ============================================
    
    @Test
    fun `test isHexadecimal with valid hex`() {
        assertTrue(CharacterFunctions.isHexadecimal(listOf(UDM.Scalar("1A2F"))).asBoolean())
        assertTrue(CharacterFunctions.isHexadecimal(listOf(UDM.Scalar("ABCDEF"))).asBoolean())
        assertTrue(CharacterFunctions.isHexadecimal(listOf(UDM.Scalar("123456"))).asBoolean())
        assertTrue(CharacterFunctions.isHexadecimal(listOf(UDM.Scalar("abcdef"))).asBoolean())
    }
    
    @Test
    fun `test isHexadecimal with invalid characters returns false`() {
        assertFalse(CharacterFunctions.isHexadecimal(listOf(UDM.Scalar("123xyz"))).asBoolean())
        assertFalse(CharacterFunctions.isHexadecimal(listOf(UDM.Scalar("0x1A2F"))).asBoolean())
        assertFalse(CharacterFunctions.isHexadecimal(listOf(UDM.Scalar("G123"))).asBoolean())
    }
    
    @Test
    fun `test isHexadecimal with empty string returns false`() {
        assertFalse(CharacterFunctions.isHexadecimal(listOf(UDM.Scalar(""))).asBoolean())
    }
    
    // ============================================
    // hasAlpha() TESTS
    // ============================================
    
    @Test
    fun `test hasAlpha with letters present`() {
        assertTrue(CharacterFunctions.hasAlpha(listOf(UDM.Scalar("Hello123"))).asBoolean())
        assertTrue(CharacterFunctions.hasAlpha(listOf(UDM.Scalar("123a"))).asBoolean())
        assertTrue(CharacterFunctions.hasAlpha(listOf(UDM.Scalar("abc"))).asBoolean())
    }
    
    @Test
    fun `test hasAlpha with no letters returns false`() {
        assertFalse(CharacterFunctions.hasAlpha(listOf(UDM.Scalar("123"))).asBoolean())
        assertFalse(CharacterFunctions.hasAlpha(listOf(UDM.Scalar("!@#"))).asBoolean())
    }
    
    @Test
    fun `test hasAlpha with empty string returns false`() {
        assertFalse(CharacterFunctions.hasAlpha(listOf(UDM.Scalar(""))).asBoolean())
    }
    
    // ============================================
    // hasNumeric() TESTS
    // ============================================
    
    @Test
    fun `test hasNumeric with digits present`() {
        assertTrue(CharacterFunctions.hasNumeric(listOf(UDM.Scalar("Hello123"))).asBoolean())
        assertTrue(CharacterFunctions.hasNumeric(listOf(UDM.Scalar("a1"))).asBoolean())
        assertTrue(CharacterFunctions.hasNumeric(listOf(UDM.Scalar("123"))).asBoolean())
    }
    
    @Test
    fun `test hasNumeric with no digits returns false`() {
        assertFalse(CharacterFunctions.hasNumeric(listOf(UDM.Scalar("Hello"))).asBoolean())
        assertFalse(CharacterFunctions.hasNumeric(listOf(UDM.Scalar("abc"))).asBoolean())
    }
    
    @Test
    fun `test hasNumeric with empty string returns false`() {
        assertFalse(CharacterFunctions.hasNumeric(listOf(UDM.Scalar(""))).asBoolean())
    }
    
    // ============================================
    // EDGE CASES & ERROR HANDLING
    // ============================================
    
    @Test
    fun `test functions with single character strings`() {
        assertTrue(CharacterFunctions.isAlpha(listOf(UDM.Scalar("a"))).asBoolean())
        assertTrue(CharacterFunctions.isNumeric(listOf(UDM.Scalar("1"))).asBoolean())
        assertTrue(CharacterFunctions.isAlphanumeric(listOf(UDM.Scalar("a"))).asBoolean())
        assertTrue(CharacterFunctions.isWhitespace(listOf(UDM.Scalar(" "))).asBoolean())
    }
    
    @Test
    fun `test functions with long strings`() {
        val longAlpha = "a".repeat(1000)
        assertTrue(CharacterFunctions.isAlpha(listOf(UDM.Scalar(longAlpha))).asBoolean())
        
        val longNumeric = "1".repeat(1000)
        assertTrue(CharacterFunctions.isNumeric(listOf(UDM.Scalar(longNumeric))).asBoolean())
    }
    
    @Test
    fun `test mixed unicode and ASCII`() {
        assertFalse(CharacterFunctions.isAscii(listOf(UDM.Scalar("Hello世界"))).asBoolean())
        assertTrue(CharacterFunctions.isAlpha(listOf(UDM.Scalar("Hello世界"))).asBoolean())
    }
}
