package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ExtendedStringFunctionsTest {

    @Test
    fun testSubstringBefore() {
        // Test basic substring before
        val result1 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("hello-world"), UDM.Scalar("-")))
        assertEquals("hello", (result1 as UDM.Scalar).value)

        // Test with multiple occurrences (should use first)
        val result2 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("a-b-c-d"), UDM.Scalar("-")))
        assertEquals("a", (result2 as UDM.Scalar).value)

        // Test when delimiter not found - returns original string (consistent with Kotlin stdlib)
        val result3 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("hello world"), UDM.Scalar("-")))
        assertEquals("hello world", (result3 as UDM.Scalar).value)

        // Test with empty string
        val result4 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar(""), UDM.Scalar("-")))
        assertEquals("", (result4 as UDM.Scalar).value)

        // Test with delimiter at beginning
        val result5 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("-hello"), UDM.Scalar("-")))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test with multi-character delimiter
        val result6 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("hello::world"), UDM.Scalar("::")))
        assertEquals("hello", (result6 as UDM.Scalar).value)

        // Test with same string as delimiter
        val result7 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("test"), UDM.Scalar("test")))
        assertEquals("", (result7 as UDM.Scalar).value)

        // Test with empty delimiter
        val result8 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("hello"), UDM.Scalar("")))
        assertEquals("", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testSubstringAfter() {
        // Test basic substring after
        val result1 = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("hello-world"), UDM.Scalar("-")))
        assertEquals("world", (result1 as UDM.Scalar).value)

        // Test with multiple occurrences (should use first)
        val result2 = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("a-b-c-d"), UDM.Scalar("-")))
        assertEquals("b-c-d", (result2 as UDM.Scalar).value)

        // Test when delimiter not found
        val result3 = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("hello world"), UDM.Scalar("-")))
        assertEquals("", (result3 as UDM.Scalar).value)

        // Test with empty string
        val result4 = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar(""), UDM.Scalar("-")))
        assertEquals("", (result4 as UDM.Scalar).value)

        // Test with delimiter at end
        val result5 = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("hello-"), UDM.Scalar("-")))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test with multi-character delimiter
        val result6 = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("hello::world"), UDM.Scalar("::")))
        assertEquals("world", (result6 as UDM.Scalar).value)

        // Test with same string as delimiter
        val result7 = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("test"), UDM.Scalar("test")))
        assertEquals("", (result7 as UDM.Scalar).value)

        // Test with empty delimiter
        val result8 = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("hello"), UDM.Scalar("")))
        assertEquals("hello", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testSubstringBeforeLast() {
        // Test basic substring before last
        val result1 = ExtendedStringFunctions.substringBeforeLast(listOf(UDM.Scalar("a/b/c.txt"), UDM.Scalar("/")))
        assertEquals("a/b", (result1 as UDM.Scalar).value)

        // Test with single occurrence
        val result2 = ExtendedStringFunctions.substringBeforeLast(listOf(UDM.Scalar("hello-world"), UDM.Scalar("-")))
        assertEquals("hello", (result2 as UDM.Scalar).value)

        // Test when delimiter not found
        val result3 = ExtendedStringFunctions.substringBeforeLast(listOf(UDM.Scalar("hello world"), UDM.Scalar("-")))
        assertEquals("", (result3 as UDM.Scalar).value)

        // Test with empty string
        val result4 = ExtendedStringFunctions.substringBeforeLast(listOf(UDM.Scalar(""), UDM.Scalar("/")))
        assertEquals("", (result4 as UDM.Scalar).value)

        // Test with delimiter at end
        val result5 = ExtendedStringFunctions.substringBeforeLast(listOf(UDM.Scalar("hello/"), UDM.Scalar("/")))
        assertEquals("hello", (result5 as UDM.Scalar).value)

        // Test with multiple consecutive delimiters
        val result6 = ExtendedStringFunctions.substringBeforeLast(listOf(UDM.Scalar("a//b//c"), UDM.Scalar("//")))
        assertEquals("a//b", (result6 as UDM.Scalar).value)

        // Test file path example
        val result7 = ExtendedStringFunctions.substringBeforeLast(listOf(UDM.Scalar("/path/to/file.txt"), UDM.Scalar("/")))
        assertEquals("/path/to", (result7 as UDM.Scalar).value)
    }

    @Test
    fun testSubstringAfterLast() {
        // Test basic substring after last
        val result1 = ExtendedStringFunctions.substringAfterLast(listOf(UDM.Scalar("a/b/c.txt"), UDM.Scalar("/")))
        assertEquals("c.txt", (result1 as UDM.Scalar).value)

        // Test with single occurrence
        val result2 = ExtendedStringFunctions.substringAfterLast(listOf(UDM.Scalar("hello-world"), UDM.Scalar("-")))
        assertEquals("world", (result2 as UDM.Scalar).value)

        // Test when delimiter not found
        val result3 = ExtendedStringFunctions.substringAfterLast(listOf(UDM.Scalar("hello world"), UDM.Scalar("-")))
        assertEquals("", (result3 as UDM.Scalar).value)

        // Test with empty string
        val result4 = ExtendedStringFunctions.substringAfterLast(listOf(UDM.Scalar(""), UDM.Scalar("/")))
        assertEquals("", (result4 as UDM.Scalar).value)

        // Test with delimiter at beginning
        val result5 = ExtendedStringFunctions.substringAfterLast(listOf(UDM.Scalar("/hello"), UDM.Scalar("/")))
        assertEquals("hello", (result5 as UDM.Scalar).value)

        // Test with multiple consecutive delimiters
        val result6 = ExtendedStringFunctions.substringAfterLast(listOf(UDM.Scalar("a//b//c"), UDM.Scalar("//")))
        assertEquals("c", (result6 as UDM.Scalar).value)

        // Test file extension example
        val result7 = ExtendedStringFunctions.substringAfterLast(listOf(UDM.Scalar("document.backup.txt"), UDM.Scalar(".")))
        assertEquals("txt", (result7 as UDM.Scalar).value)

        // Test URL parsing example
        val result8 = ExtendedStringFunctions.substringAfterLast(listOf(UDM.Scalar("https://example.com/path/file.html"), UDM.Scalar("/")))
        assertEquals("file.html", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testPad() {
        // Test basic left padding with zeros
        val result1 = ExtendedStringFunctions.pad(listOf(UDM.Scalar("42"), UDM.Scalar(5), UDM.Scalar("0")))
        assertEquals("00042", (result1 as UDM.Scalar).value)

        // Test padding with spaces
        val result2 = ExtendedStringFunctions.pad(listOf(UDM.Scalar("hello"), UDM.Scalar(10), UDM.Scalar(" ")))
        assertEquals("     hello", (result2 as UDM.Scalar).value)

        // Test when string is already long enough
        val result3 = ExtendedStringFunctions.pad(listOf(UDM.Scalar("hello"), UDM.Scalar(3), UDM.Scalar("*")))
        assertEquals("hello", (result3 as UDM.Scalar).value)

        // Test when string is exactly the target length
        val result4 = ExtendedStringFunctions.pad(listOf(UDM.Scalar("hello"), UDM.Scalar(5), UDM.Scalar("*")))
        assertEquals("hello", (result4 as UDM.Scalar).value)

        // Test with empty string
        val result5 = ExtendedStringFunctions.pad(listOf(UDM.Scalar(""), UDM.Scalar(3), UDM.Scalar("x")))
        assertEquals("xxx", (result5 as UDM.Scalar).value)

        // Test with special characters
        val result6 = ExtendedStringFunctions.pad(listOf(UDM.Scalar("test"), UDM.Scalar(7), UDM.Scalar("-")))
        assertEquals("---test", (result6 as UDM.Scalar).value)

        // Test with zero length
        val result7 = ExtendedStringFunctions.pad(listOf(UDM.Scalar("hello"), UDM.Scalar(0), UDM.Scalar("*")))
        assertEquals("hello", (result7 as UDM.Scalar).value)

        // Test with empty pad character (should default to space)
        val result8 = ExtendedStringFunctions.pad(listOf(UDM.Scalar("hi"), UDM.Scalar(5), UDM.Scalar("")))
        assertEquals("   hi", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testPadRight() {
        // Test basic right padding with zeros
        val result1 = ExtendedStringFunctions.padRight(listOf(UDM.Scalar("42"), UDM.Scalar(5), UDM.Scalar("0")))
        assertEquals("42000", (result1 as UDM.Scalar).value)

        // Test padding with spaces
        val result2 = ExtendedStringFunctions.padRight(listOf(UDM.Scalar("hello"), UDM.Scalar(10), UDM.Scalar(" ")))
        assertEquals("hello     ", (result2 as UDM.Scalar).value)

        // Test when string is already long enough
        val result3 = ExtendedStringFunctions.padRight(listOf(UDM.Scalar("hello"), UDM.Scalar(3), UDM.Scalar("*")))
        assertEquals("hello", (result3 as UDM.Scalar).value)

        // Test when string is exactly the target length
        val result4 = ExtendedStringFunctions.padRight(listOf(UDM.Scalar("hello"), UDM.Scalar(5), UDM.Scalar("*")))
        assertEquals("hello", (result4 as UDM.Scalar).value)

        // Test with empty string
        val result5 = ExtendedStringFunctions.padRight(listOf(UDM.Scalar(""), UDM.Scalar(3), UDM.Scalar("x")))
        assertEquals("xxx", (result5 as UDM.Scalar).value)

        // Test with special characters
        val result6 = ExtendedStringFunctions.padRight(listOf(UDM.Scalar("test"), UDM.Scalar(7), UDM.Scalar("-")))
        assertEquals("test---", (result6 as UDM.Scalar).value)

        // Test with zero length
        val result7 = ExtendedStringFunctions.padRight(listOf(UDM.Scalar("hello"), UDM.Scalar(0), UDM.Scalar("*")))
        assertEquals("hello", (result7 as UDM.Scalar).value)

        // Test with empty pad character (should default to space)
        val result8 = ExtendedStringFunctions.padRight(listOf(UDM.Scalar("hi"), UDM.Scalar(5), UDM.Scalar("")))
        assertEquals("hi   ", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testNormalizeSpace() {
        // Test basic space normalization
        val result1 = ExtendedStringFunctions.normalizeSpace(listOf(UDM.Scalar("hello    world")))
        assertEquals("hello world", (result1 as UDM.Scalar).value)

        // Test with leading and trailing spaces
        val result2 = ExtendedStringFunctions.normalizeSpace(listOf(UDM.Scalar("  hello  world  ")))
        assertEquals("hello world", (result2 as UDM.Scalar).value)

        // Test with mixed whitespace (tabs, newlines)
        val result3 = ExtendedStringFunctions.normalizeSpace(listOf(UDM.Scalar("hello\t\nworld")))
        assertEquals("hello world", (result3 as UDM.Scalar).value)

        // Test with multiple types of whitespace
        val result4 = ExtendedStringFunctions.normalizeSpace(listOf(UDM.Scalar("a \t\n  b   \r\n  c")))
        assertEquals("a b c", (result4 as UDM.Scalar).value)

        // Test with no extra spaces
        val result5 = ExtendedStringFunctions.normalizeSpace(listOf(UDM.Scalar("hello world")))
        assertEquals("hello world", (result5 as UDM.Scalar).value)

        // Test with only spaces
        val result6 = ExtendedStringFunctions.normalizeSpace(listOf(UDM.Scalar("   \t\n   ")))
        assertEquals("", (result6 as UDM.Scalar).value)

        // Test with empty string
        val result7 = ExtendedStringFunctions.normalizeSpace(listOf(UDM.Scalar("")))
        assertEquals("", (result7 as UDM.Scalar).value)

        // Test with single word and spaces
        val result8 = ExtendedStringFunctions.normalizeSpace(listOf(UDM.Scalar("  hello  ")))
        assertEquals("hello", (result8 as UDM.Scalar).value)

        // Test with consecutive spaces between words
        val result9 = ExtendedStringFunctions.normalizeSpace(listOf(UDM.Scalar("one     two     three")))
        assertEquals("one two three", (result9 as UDM.Scalar).value)
    }

    @Test
    fun testRepeat() {
        // Test basic string repetition
        val result1 = ExtendedStringFunctions.repeat(listOf(UDM.Scalar("*"), UDM.Scalar(5)))
        assertEquals("*****", (result1 as UDM.Scalar).value)

        // Test repeating word
        val result2 = ExtendedStringFunctions.repeat(listOf(UDM.Scalar("hello"), UDM.Scalar(3)))
        assertEquals("hellohellohello", (result2 as UDM.Scalar).value)

        // Test zero repetitions
        val result3 = ExtendedStringFunctions.repeat(listOf(UDM.Scalar("test"), UDM.Scalar(0)))
        assertEquals("", (result3 as UDM.Scalar).value)

        // Test single repetition
        val result4 = ExtendedStringFunctions.repeat(listOf(UDM.Scalar("single"), UDM.Scalar(1)))
        assertEquals("single", (result4 as UDM.Scalar).value)

        // Test repeating empty string
        val result5 = ExtendedStringFunctions.repeat(listOf(UDM.Scalar(""), UDM.Scalar(5)))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test repeating space
        val result6 = ExtendedStringFunctions.repeat(listOf(UDM.Scalar(" "), UDM.Scalar(4)))
        assertEquals("    ", (result6 as UDM.Scalar).value)

        // Test repeating multi-character string
        val result7 = ExtendedStringFunctions.repeat(listOf(UDM.Scalar("ab"), UDM.Scalar(4)))
        assertEquals("abababab", (result7 as UDM.Scalar).value)

        // Test repeating special characters
        val result8 = ExtendedStringFunctions.repeat(listOf(UDM.Scalar("-="), UDM.Scalar(3)))
        assertEquals("-=-=-=", (result8 as UDM.Scalar).value) // 3 repetitions of "-=" = 6 characters
    }

    @Test
    fun testArgumentValidation() {
        // Test wrong number of arguments
        assertThrows<FunctionArgumentException> {
            ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("test")))
        }

        assertThrows<FunctionArgumentException> {
            ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("test"), UDM.Scalar("-"), UDM.Scalar("extra")))
        }

        assertThrows<FunctionArgumentException> {
            ExtendedStringFunctions.pad(listOf(UDM.Scalar("test"), UDM.Scalar(5)))
        }

        assertThrows<FunctionArgumentException> {
            ExtendedStringFunctions.normalizeSpace(emptyList())
        }

        assertThrows<FunctionArgumentException> {
            ExtendedStringFunctions.repeat(listOf(UDM.Scalar("test")))
        }
    }

    // Note: testInvalidArgumentTypes removed - validation is handled at runtime by the UTL-X engine via @UTLXFunction annotations

    @Test
    fun testEdgeCases() {
        // Test negative padding length
        val result1 = ExtendedStringFunctions.pad(listOf(UDM.Scalar("test"), UDM.Scalar(-5), UDM.Scalar("*")))
        assertEquals("test", (result1 as UDM.Scalar).value)

        // Test negative repeat count (should behave like zero)
        val result2 = ExtendedStringFunctions.repeat(listOf(UDM.Scalar("test"), UDM.Scalar(-3)))
        assertEquals("", (result2 as UDM.Scalar).value)

        // Test very long strings
        val longString = "a".repeat(1000)
        val result3 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar(longString + "delimiter" + longString), UDM.Scalar("delimiter")))
        assertEquals(longString, (result3 as UDM.Scalar).value)

        // Test with special Unicode characters
        val result4 = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("Hello 🌍 World"), UDM.Scalar("🌍")))
        assertEquals(" World", (result4 as UDM.Scalar).value)

        // Test with null values converted to string
        val result5 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar(null), UDM.Scalar("-")))
        assertEquals("", (result5 as UDM.Scalar).value)
    }

    @Test
    fun testComplexDelimiters() {
        // Test with regex special characters as delimiters
        val result1 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("a.b.c"), UDM.Scalar(".")))
        assertEquals("a", (result1 as UDM.Scalar).value)

        val result2 = ExtendedStringFunctions.substringAfterLast(listOf(UDM.Scalar("a.b.c"), UDM.Scalar(".")))
        assertEquals("c", (result2 as UDM.Scalar).value)

        // Test with brackets and parentheses
        val result3 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("function(param)"), UDM.Scalar("(")))
        assertEquals("function", (result3 as UDM.Scalar).value)

        val result4 = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("array[index]"), UDM.Scalar("[")))
        assertEquals("index]", (result4 as UDM.Scalar).value)

        // Test with quotes
        val result5 = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("'quoted string'"), UDM.Scalar("'")))
        assertEquals("", (result5 as UDM.Scalar).value)

        val result6 = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("'quoted string'"), UDM.Scalar("'")))
        assertEquals("quoted string'", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testCombinedOperations() {
        // Test combining multiple string operations
        val input = "  /path/to/some/file.txt  "
        
        // First normalize spaces
        val normalized = ExtendedStringFunctions.normalizeSpace(listOf(UDM.Scalar(input)))
        
        // Then get file name (after last /)
        val fileName = ExtendedStringFunctions.substringAfterLast(listOf(normalized, UDM.Scalar("/")))
        assertEquals("file.txt", (fileName as UDM.Scalar).value)
        
        // Then get file extension (after last .)
        val extension = ExtendedStringFunctions.substringAfterLast(listOf(fileName, UDM.Scalar(".")))
        assertEquals("txt", (extension as UDM.Scalar).value)
        
        // Get base name (before last .)
        val baseName = ExtendedStringFunctions.substringBeforeLast(listOf(fileName, UDM.Scalar(".")))
        assertEquals("file", (baseName as UDM.Scalar).value)
    }
}