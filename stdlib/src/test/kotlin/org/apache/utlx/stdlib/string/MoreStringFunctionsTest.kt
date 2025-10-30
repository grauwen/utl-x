package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class MoreStringFunctionsTest {

    @Test
    fun testLeftTrim() {
        // Test basic left trim
        val result1 = MoreStringFunctions.leftTrim(listOf(UDM.Scalar("  hello  ")))
        assertEquals("hello  ", (result1 as UDM.Scalar).value)

        // Test with tabs and newlines
        val result2 = MoreStringFunctions.leftTrim(listOf(UDM.Scalar("\t\n  hello")))
        assertEquals("hello", (result2 as UDM.Scalar).value)

        // Test with no leading whitespace
        val result3 = MoreStringFunctions.leftTrim(listOf(UDM.Scalar("hello  ")))
        assertEquals("hello  ", (result3 as UDM.Scalar).value)

        // Test with only whitespace
        val result4 = MoreStringFunctions.leftTrim(listOf(UDM.Scalar("   ")))
        assertEquals("", (result4 as UDM.Scalar).value)

        // Test with empty string
        val result5 = MoreStringFunctions.leftTrim(listOf(UDM.Scalar("")))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test with mixed whitespace
        val result6 = MoreStringFunctions.leftTrim(listOf(UDM.Scalar(" \t\r\n hello world \t ")))
        assertEquals("hello world \t ", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testRightTrim() {
        // Test basic right trim
        val result1 = MoreStringFunctions.rightTrim(listOf(UDM.Scalar("  hello  ")))
        assertEquals("  hello", (result1 as UDM.Scalar).value)

        // Test with tabs and newlines
        val result2 = MoreStringFunctions.rightTrim(listOf(UDM.Scalar("hello\t\n  ")))
        assertEquals("hello", (result2 as UDM.Scalar).value)

        // Test with no trailing whitespace
        val result3 = MoreStringFunctions.rightTrim(listOf(UDM.Scalar("  hello")))
        assertEquals("  hello", (result3 as UDM.Scalar).value)

        // Test with only whitespace
        val result4 = MoreStringFunctions.rightTrim(listOf(UDM.Scalar("   ")))
        assertEquals("", (result4 as UDM.Scalar).value)

        // Test with empty string
        val result5 = MoreStringFunctions.rightTrim(listOf(UDM.Scalar("")))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test with mixed whitespace
        val result6 = MoreStringFunctions.rightTrim(listOf(UDM.Scalar(" \t hello world \t\r\n ")))
        assertEquals(" \t hello world", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testTranslate() {
        // Test basic character translation
        val result1 = MoreStringFunctions.translate(listOf(UDM.Scalar("hello"), UDM.Scalar("helo"), UDM.Scalar("HELO")))
        assertEquals("HELLO", (result1 as UDM.Scalar).value)

        // Test with character deletion (shorter replace string)
        val result2 = MoreStringFunctions.translate(listOf(UDM.Scalar("hello"), UDM.Scalar("lo"), UDM.Scalar("L")))
        assertEquals("heLL", (result2 as UDM.Scalar).value) // 'l'→'L', 'o'→deleted

        // Test with character removal (empty replace for some chars)
        val result3 = MoreStringFunctions.translate(listOf(UDM.Scalar("hello world"), UDM.Scalar("l "), UDM.Scalar("L")))
        assertEquals("heLLoworLd", (result3 as UDM.Scalar).value) // 'l'→'L', ' '→deleted

        // Test complete character removal
        val result4 = MoreStringFunctions.translate(listOf(UDM.Scalar("hello"), UDM.Scalar("lo"), UDM.Scalar("")))
        assertEquals("he", (result4 as UDM.Scalar).value)

        // Test no matching characters
        val result5 = MoreStringFunctions.translate(listOf(UDM.Scalar("hello"), UDM.Scalar("xyz"), UDM.Scalar("ABC")))
        assertEquals("hello", (result5 as UDM.Scalar).value)

        // Test empty strings
        val result6 = MoreStringFunctions.translate(listOf(UDM.Scalar(""), UDM.Scalar("abc"), UDM.Scalar("123")))
        assertEquals("", (result6 as UDM.Scalar).value)

        // Test ROT13-like transformation
        val result7 = MoreStringFunctions.translate(listOf(UDM.Scalar("abc"), UDM.Scalar("abc"), UDM.Scalar("123")))
        assertEquals("123", (result7 as UDM.Scalar).value)

        // Test with special characters - dash replaced with space, underscore deleted
        val result8 = MoreStringFunctions.translate(listOf(UDM.Scalar("a-b_c"), UDM.Scalar("-_"), UDM.Scalar(" ")))
        assertEquals("a bc", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testReverse() {
        // Test basic string reversal
        val result1 = MoreStringFunctions.reverse(listOf(UDM.Scalar("hello")))
        assertEquals("olleh", (result1 as UDM.Scalar).value)

        // Test palindrome
        val result2 = MoreStringFunctions.reverse(listOf(UDM.Scalar("racecar")))
        assertEquals("racecar", (result2 as UDM.Scalar).value)

        // Test single character
        val result3 = MoreStringFunctions.reverse(listOf(UDM.Scalar("a")))
        assertEquals("a", (result3 as UDM.Scalar).value)

        // Test empty string
        val result4 = MoreStringFunctions.reverse(listOf(UDM.Scalar("")))
        assertEquals("", (result4 as UDM.Scalar).value)

        // Test with spaces
        val result5 = MoreStringFunctions.reverse(listOf(UDM.Scalar("hello world")))
        assertEquals("dlrow olleh", (result5 as UDM.Scalar).value)

        // Test with numbers
        val result6 = MoreStringFunctions.reverse(listOf(UDM.Scalar("12345")))
        assertEquals("54321", (result6 as UDM.Scalar).value)

        // Test with special characters
        val result7 = MoreStringFunctions.reverse(listOf(UDM.Scalar("!@#$%")))
        assertEquals("%$#@!", (result7 as UDM.Scalar).value)

        // Test with Unicode characters
        val result8 = MoreStringFunctions.reverse(listOf(UDM.Scalar("café")))
        assertEquals("éfac", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testIsEmpty() {
        // Test empty string
        val result1 = MoreStringFunctions.isEmpty(listOf(UDM.Scalar("")))
        assertTrue((result1 as UDM.Scalar).value as Boolean)

        // Test non-empty string
        val result2 = MoreStringFunctions.isEmpty(listOf(UDM.Scalar("hello")))
        assertFalse((result2 as UDM.Scalar).value as Boolean)

        // Test whitespace string (not empty)
        val result3 = MoreStringFunctions.isEmpty(listOf(UDM.Scalar("   ")))
        assertFalse((result3 as UDM.Scalar).value as Boolean)

        // Test single character
        val result4 = MoreStringFunctions.isEmpty(listOf(UDM.Scalar("a")))
        assertFalse((result4 as UDM.Scalar).value as Boolean)

        // Test null converted to string
        val result5 = MoreStringFunctions.isEmpty(listOf(UDM.Scalar(null)))
        assertTrue((result5 as UDM.Scalar).value as Boolean) // asString() converts null to ""
    }

    @Test
    fun testIsBlank() {
        // Test empty string
        val result1 = MoreStringFunctions.isBlank(listOf(UDM.Scalar("")))
        assertTrue((result1 as UDM.Scalar).value as Boolean)

        // Test whitespace only
        val result2 = MoreStringFunctions.isBlank(listOf(UDM.Scalar("   ")))
        assertTrue((result2 as UDM.Scalar).value as Boolean)

        // Test tabs and newlines
        val result3 = MoreStringFunctions.isBlank(listOf(UDM.Scalar("\t\n\r ")))
        assertTrue((result3 as UDM.Scalar).value as Boolean)

        // Test non-blank string
        val result4 = MoreStringFunctions.isBlank(listOf(UDM.Scalar("hello")))
        assertFalse((result4 as UDM.Scalar).value as Boolean)

        // Test string with content and whitespace
        val result5 = MoreStringFunctions.isBlank(listOf(UDM.Scalar("  a  ")))
        assertFalse((result5 as UDM.Scalar).value as Boolean)

        // Test single non-whitespace character
        val result6 = MoreStringFunctions.isBlank(listOf(UDM.Scalar("a")))
        assertFalse((result6 as UDM.Scalar).value as Boolean)
    }

    @Test
    fun testCharAt() {
        // Test basic character access
        val result1 = MoreStringFunctions.charAt(listOf(UDM.Scalar("hello"), UDM.Scalar(1)))
        assertEquals("e", (result1 as UDM.Scalar).value)

        // Test first character
        val result2 = MoreStringFunctions.charAt(listOf(UDM.Scalar("hello"), UDM.Scalar(0)))
        assertEquals("h", (result2 as UDM.Scalar).value)

        // Test last character
        val result3 = MoreStringFunctions.charAt(listOf(UDM.Scalar("hello"), UDM.Scalar(4)))
        assertEquals("o", (result3 as UDM.Scalar).value)

        // Test single character string
        val result4 = MoreStringFunctions.charAt(listOf(UDM.Scalar("a"), UDM.Scalar(0)))
        assertEquals("a", (result4 as UDM.Scalar).value)

        // Test with space
        val result5 = MoreStringFunctions.charAt(listOf(UDM.Scalar("a b"), UDM.Scalar(1)))
        assertEquals(" ", (result5 as UDM.Scalar).value)

        // Test with special characters
        val result6 = MoreStringFunctions.charAt(listOf(UDM.Scalar("!@#"), UDM.Scalar(1)))
        assertEquals("@", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testCharAtEdgeCases() {
        // Test index out of bounds (negative)
        assertThrows<FunctionArgumentException> {
            MoreStringFunctions.charAt(listOf(UDM.Scalar("hello"), UDM.Scalar(-1)))
        }

        // Test index out of bounds (too large)
        assertThrows<FunctionArgumentException> {
            MoreStringFunctions.charAt(listOf(UDM.Scalar("hello"), UDM.Scalar(5)))
        }

        // Test index out of bounds on empty string
        assertThrows<FunctionArgumentException> {
            MoreStringFunctions.charAt(listOf(UDM.Scalar(""), UDM.Scalar(0)))
        }
    }

    @Test
    fun testCharCodeAt() {
        // Test basic character code
        val result1 = MoreStringFunctions.charCodeAt(listOf(UDM.Scalar("A"), UDM.Scalar(0)))
        assertEquals(65.0, (result1 as UDM.Scalar).value)

        // Test lowercase
        val result2 = MoreStringFunctions.charCodeAt(listOf(UDM.Scalar("a"), UDM.Scalar(0)))
        assertEquals(97.0, (result2 as UDM.Scalar).value)

        // Test number character
        val result3 = MoreStringFunctions.charCodeAt(listOf(UDM.Scalar("0"), UDM.Scalar(0)))
        assertEquals(48.0, (result3 as UDM.Scalar).value)

        // Test space character
        val result4 = MoreStringFunctions.charCodeAt(listOf(UDM.Scalar(" "), UDM.Scalar(0)))
        assertEquals(32.0, (result4 as UDM.Scalar).value)

        // Test special character
        val result5 = MoreStringFunctions.charCodeAt(listOf(UDM.Scalar("!"), UDM.Scalar(0)))
        assertEquals(33.0, (result5 as UDM.Scalar).value)

        // Test multiple characters
        val result6 = MoreStringFunctions.charCodeAt(listOf(UDM.Scalar("Hello"), UDM.Scalar(1)))
        assertEquals(101.0, (result6 as UDM.Scalar).value) // 'e'
    }

    @Test
    fun testCharCodeAtEdgeCases() {
        // Test index out of bounds
        assertThrows<FunctionArgumentException> {
            MoreStringFunctions.charCodeAt(listOf(UDM.Scalar("A"), UDM.Scalar(-1)))
        }

        assertThrows<FunctionArgumentException> {
            MoreStringFunctions.charCodeAt(listOf(UDM.Scalar("A"), UDM.Scalar(1)))
        }

        // Test empty string
        assertThrows<FunctionArgumentException> {
            MoreStringFunctions.charCodeAt(listOf(UDM.Scalar(""), UDM.Scalar(0)))
        }
    }

    @Test
    fun testFromCharCode() {
        // Test basic character creation
        val result1 = MoreStringFunctions.fromCharCode(listOf(UDM.Scalar(65)))
        assertEquals("A", (result1 as UDM.Scalar).value)

        // Test lowercase
        val result2 = MoreStringFunctions.fromCharCode(listOf(UDM.Scalar(97)))
        assertEquals("a", (result2 as UDM.Scalar).value)

        // Test number character
        val result3 = MoreStringFunctions.fromCharCode(listOf(UDM.Scalar(48)))
        assertEquals("0", (result3 as UDM.Scalar).value)

        // Test space character
        val result4 = MoreStringFunctions.fromCharCode(listOf(UDM.Scalar(32)))
        assertEquals(" ", (result4 as UDM.Scalar).value)

        // Test special character
        val result5 = MoreStringFunctions.fromCharCode(listOf(UDM.Scalar(33)))
        assertEquals("!", (result5 as UDM.Scalar).value)

        // Test newline
        val result6 = MoreStringFunctions.fromCharCode(listOf(UDM.Scalar(10)))
        assertEquals("\n", (result6 as UDM.Scalar).value)

        // Test tab
        val result7 = MoreStringFunctions.fromCharCode(listOf(UDM.Scalar(9)))
        assertEquals("\t", (result7 as UDM.Scalar).value)
    }

    @Test
    fun testFromCharCodeEdgeCases() {
        // Test invalid character codes
        assertThrows<FunctionArgumentException> {
            MoreStringFunctions.fromCharCode(listOf(UDM.Scalar(-1)))
        }

        assertThrows<FunctionArgumentException> {
            MoreStringFunctions.fromCharCode(listOf(UDM.Scalar(0x110000))) // Beyond Unicode range
        }

        // Test boundary values
        val result1 = MoreStringFunctions.fromCharCode(listOf(UDM.Scalar(0)))
        assertEquals("\u0000", (result1 as UDM.Scalar).value)

        val result2 = MoreStringFunctions.fromCharCode(listOf(UDM.Scalar(0x10FFFF)))
        // This should work for the highest valid Unicode code point
        assertTrue(result2 is UDM.Scalar)
    }

    @Test
    fun testCapitalize() {
        // Test basic capitalization
        val result1 = MoreStringFunctions.capitalize(listOf(UDM.Scalar("hello world")))
        assertEquals("Hello world", (result1 as UDM.Scalar).value)

        // Test already capitalized
        val result2 = MoreStringFunctions.capitalize(listOf(UDM.Scalar("Hello world")))
        assertEquals("Hello world", (result2 as UDM.Scalar).value)

        // Test all uppercase
        val result3 = MoreStringFunctions.capitalize(listOf(UDM.Scalar("HELLO")))
        assertEquals("HELLO", (result3 as UDM.Scalar).value)

        // Test single character
        val result4 = MoreStringFunctions.capitalize(listOf(UDM.Scalar("a")))
        assertEquals("A", (result4 as UDM.Scalar).value)

        // Test empty string
        val result5 = MoreStringFunctions.capitalize(listOf(UDM.Scalar("")))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test starting with number
        val result6 = MoreStringFunctions.capitalize(listOf(UDM.Scalar("123abc")))
        assertEquals("123abc", (result6 as UDM.Scalar).value)

        // Test starting with special character
        val result7 = MoreStringFunctions.capitalize(listOf(UDM.Scalar("!hello")))
        assertEquals("!hello", (result7 as UDM.Scalar).value)
    }

    @Test
    fun testTitleCase() {
        // Test basic title case
        val result1 = MoreStringFunctions.titleCase(listOf(UDM.Scalar("hello world")))
        assertEquals("Hello World", (result1 as UDM.Scalar).value)

        // Test multiple words
        val result2 = MoreStringFunctions.titleCase(listOf(UDM.Scalar("the quick brown fox")))
        assertEquals("The Quick Brown Fox", (result2 as UDM.Scalar).value)

        // Test already title case
        val result3 = MoreStringFunctions.titleCase(listOf(UDM.Scalar("Hello World")))
        assertEquals("Hello World", (result3 as UDM.Scalar).value)

        // Test single word
        val result4 = MoreStringFunctions.titleCase(listOf(UDM.Scalar("hello")))
        assertEquals("Hello", (result4 as UDM.Scalar).value)

        // Test empty string
        val result5 = MoreStringFunctions.titleCase(listOf(UDM.Scalar("")))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test with extra spaces
        val result6 = MoreStringFunctions.titleCase(listOf(UDM.Scalar("hello  world")))
        assertEquals("Hello  World", (result6 as UDM.Scalar).value)

        // Test with mixed case
        val result7 = MoreStringFunctions.titleCase(listOf(UDM.Scalar("hELLo WoRLd")))
        assertEquals("HELLo WoRLd", (result7 as UDM.Scalar).value)
    }

    @Test
    fun testArgumentValidation() {
        // Test wrong number of arguments
        assertThrows<FunctionArgumentException> {
            MoreStringFunctions.leftTrim(emptyList())
        }

        assertThrows<FunctionArgumentException> {
            MoreStringFunctions.translate(listOf(UDM.Scalar("test"), UDM.Scalar("a")))
        }

        assertThrows<FunctionArgumentException> {
            MoreStringFunctions.charAt(listOf(UDM.Scalar("test")))
        }

        assertThrows<FunctionArgumentException> {
            MoreStringFunctions.fromCharCode(listOf(UDM.Scalar(65), UDM.Scalar(66)))
        }
    }

    // Note: testInvalidArgumentTypes removed - validation is handled at runtime by the UTL-X engine via @UTLXFunction annotations

    @Test
    fun testRoundTripOperations() {
        // Test charAt and charCodeAt round trip
        val original = "Hello World!"
        for (i in original.indices) {
            val char = MoreStringFunctions.charAt(listOf(UDM.Scalar(original), UDM.Scalar(i)))
            val code = MoreStringFunctions.charCodeAt(listOf(UDM.Scalar(original), UDM.Scalar(i)))
            val reconstructed = MoreStringFunctions.fromCharCode(listOf(code))
            assertEquals(char, reconstructed)
        }

        // Test reverse round trip
        val testString = "racecar"
        val reversed = MoreStringFunctions.reverse(listOf(UDM.Scalar(testString)))
        val doubleReversed = MoreStringFunctions.reverse(listOf(reversed))
        assertEquals(testString, (doubleReversed as UDM.Scalar).value)
    }

    @Test
    fun testComplexTranslations() {
        // Test ROT13
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        val rot13 = "nopqrstuvwxyzabcdefghijklm"
        val result1 = MoreStringFunctions.translate(listOf(UDM.Scalar("hello"), UDM.Scalar(alphabet), UDM.Scalar(rot13)))
        assertEquals("uryyb", (result1 as UDM.Scalar).value)

        // Test number to letter mapping
        val result2 = MoreStringFunctions.translate(listOf(UDM.Scalar("1234"), UDM.Scalar("1234567890"), UDM.Scalar("ABCDEFGHIJ")))
        assertEquals("ABCD", (result2 as UDM.Scalar).value)

        // Test removing vowels
        val result3 = MoreStringFunctions.translate(listOf(UDM.Scalar("hello world"), UDM.Scalar("aeiou"), UDM.Scalar("")))
        assertEquals("hll wrld", (result3 as UDM.Scalar).value)
    }

    @Test
    fun testUnicodeSupport() {
        // Test Unicode characters in various functions
        val unicode = "café 🌍 naïve"
        
        val reversed = MoreStringFunctions.reverse(listOf(UDM.Scalar(unicode)))
        assertEquals("evïan 🌍 éfac", (reversed as UDM.Scalar).value)
        
        val capitalized = MoreStringFunctions.capitalize(listOf(UDM.Scalar(unicode)))
        assertEquals("Café 🌍 naïve", (capitalized as UDM.Scalar).value)
        
        val isEmpty = MoreStringFunctions.isEmpty(listOf(UDM.Scalar(unicode)))
        assertFalse((isEmpty as UDM.Scalar).value as Boolean)
        
        val isBlank = MoreStringFunctions.isBlank(listOf(UDM.Scalar(unicode)))
        assertFalse((isBlank as UDM.Scalar).value as Boolean)
    }
}