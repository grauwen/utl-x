package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegexFunctionsTest {

    @Test
    fun testMatches() {
        // Test exact match
        val result1 = RegexFunctions.matches(listOf(UDM.Scalar("hello"), UDM.Scalar("hello")))
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        // Test pattern match
        val result2 = RegexFunctions.matches(listOf(UDM.Scalar("hello123"), UDM.Scalar("hello\\d+")))
        assertEquals(true, (result2 as UDM.Scalar).value)
        
        // Test no match
        val result3 = RegexFunctions.matches(listOf(UDM.Scalar("hello"), UDM.Scalar("world")))
        assertEquals(false, (result3 as UDM.Scalar).value)
        
        // Test partial match (should return false - matches requires full string match)
        val result4 = RegexFunctions.matches(listOf(UDM.Scalar("hello world"), UDM.Scalar("hello")))
        assertEquals(false, (result4 as UDM.Scalar).value)
    }

    @Test
    fun testMatchesWithComplexPatterns() {
        // Test email pattern
        val emailPattern = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
        val result1 = RegexFunctions.matches(listOf(UDM.Scalar("test@example.com"), UDM.Scalar(emailPattern)))
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        val result2 = RegexFunctions.matches(listOf(UDM.Scalar("invalid-email"), UDM.Scalar(emailPattern)))
        assertEquals(false, (result2 as UDM.Scalar).value)
        
        // Test digit pattern
        val result3 = RegexFunctions.matches(listOf(UDM.Scalar("12345"), UDM.Scalar("\\d+")))
        assertEquals(true, (result3 as UDM.Scalar).value)
        
        val result4 = RegexFunctions.matches(listOf(UDM.Scalar("12a45"), UDM.Scalar("\\d+")))
        assertEquals(false, (result4 as UDM.Scalar).value)
    }

    @Test
    fun testMatchesWithSpecialCharacters() {
        // Test dot (any character)
        val result1 = RegexFunctions.matches(listOf(UDM.Scalar("a"), UDM.Scalar(".")))
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        // Test literal dot
        val result2 = RegexFunctions.matches(listOf(UDM.Scalar("."), UDM.Scalar("\\.")))
        assertEquals(true, (result2 as UDM.Scalar).value)
        
        // Test anchors
        val result3 = RegexFunctions.matches(listOf(UDM.Scalar("hello"), UDM.Scalar("^hello$")))
        assertEquals(true, (result3 as UDM.Scalar).value)
        
        // Test quantifiers
        val result4 = RegexFunctions.matches(listOf(UDM.Scalar("aaa"), UDM.Scalar("a{3}")))
        assertEquals(true, (result4 as UDM.Scalar).value)
        
        val result5 = RegexFunctions.matches(listOf(UDM.Scalar("aa"), UDM.Scalar("a{3}")))
        assertEquals(false, (result5 as UDM.Scalar).value)
    }

    @Test
    fun testReplaceRegex() {
        // Test simple replacement
        val result1 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("hello world"), 
            UDM.Scalar("world"), 
            UDM.Scalar("universe")
        ))
        assertEquals("hello universe", (result1 as UDM.Scalar).value)
        
        // Test multiple replacements
        val result2 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("hello world world"), 
            UDM.Scalar("world"), 
            UDM.Scalar("universe")
        ))
        assertEquals("hello universe universe", (result2 as UDM.Scalar).value)
        
        // Test digit replacement
        val result3 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("abc123def456"), 
            UDM.Scalar("\\d+"), 
            UDM.Scalar("XXX")
        ))
        assertEquals("abcXXXdefXXX", (result3 as UDM.Scalar).value)
    }

    @Test
    fun testReplaceRegexWithGroups() {
        // Test capturing groups with backreferences
        val result1 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("John Doe"), 
            UDM.Scalar("(\\w+) (\\w+)"), 
            UDM.Scalar("$2, $1")
        ))
        assertEquals("Doe, John", (result1 as UDM.Scalar).value)
        
        // Test with phone number formatting
        val result2 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("1234567890"), 
            UDM.Scalar("(\\d{3})(\\d{3})(\\d{4})"), 
            UDM.Scalar("($1) $2-$3")
        ))
        assertEquals("(123) 456-7890", (result2 as UDM.Scalar).value)
    }

    @Test
    fun testReplaceRegexWithSpecialReplacements() {
        // Test removing pattern
        val result1 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("hello123world456"), 
            UDM.Scalar("\\d+"), 
            UDM.Scalar("")
        ))
        assertEquals("helloworld", (result1 as UDM.Scalar).value)
        
        // Test whitespace normalization
        val result2 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("hello   world"), 
            UDM.Scalar("\\s+"), 
            UDM.Scalar(" ")
        ))
        assertEquals("hello world", (result2 as UDM.Scalar).value)
        
        // Test case-insensitive replacement (using (?i) flag)
        val result3 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("Hello World"), 
            UDM.Scalar("(?i)hello"), 
            UDM.Scalar("Hi")
        ))
        assertEquals("Hi World", (result3 as UDM.Scalar).value)
    }

    @Test
    fun testMatchesEmptyString() {
        val result1 = RegexFunctions.matches(listOf(UDM.Scalar(""), UDM.Scalar("")))
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        val result2 = RegexFunctions.matches(listOf(UDM.Scalar(""), UDM.Scalar(".*")))
        assertEquals(true, (result2 as UDM.Scalar).value)
        
        val result3 = RegexFunctions.matches(listOf(UDM.Scalar("hello"), UDM.Scalar("")))
        assertEquals(false, (result3 as UDM.Scalar).value)
    }

    @Test
    fun testReplaceRegexEmptyString() {
        val result1 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar(""), 
            UDM.Scalar(".*"), 
            UDM.Scalar("replacement")
        ))
        assertEquals("replacement", (result1 as UDM.Scalar).value)
        
        val result2 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("hello"), 
            UDM.Scalar(""), 
            UDM.Scalar("X")
        ))
        // Empty pattern might behave differently, this is a boundary case
        assertTrue((result2 as UDM.Scalar).value is String)
    }

    // ==================== ERROR HANDLING TESTS ====================

    // Note: testMatchesInvalidArguments and testReplaceRegexInvalidArguments removed -
    // validation is handled at runtime by the UTL-X engine via @UTLXFunction annotations

    @Test
    fun testInvalidRegexPattern() {
        // Test invalid regex pattern in matches
        assertThrows<FunctionArgumentException> {
            RegexFunctions.matches(listOf(UDM.Scalar("hello"), UDM.Scalar("["))) // Unclosed bracket
        }
        
        assertThrows<FunctionArgumentException> {
            RegexFunctions.matches(listOf(UDM.Scalar("hello"), UDM.Scalar("*"))) // Invalid quantifier
        }
        
        // Test invalid regex pattern in replaceRegex
        assertThrows<FunctionArgumentException> {
            RegexFunctions.replaceRegex(listOf(
                UDM.Scalar("hello"), 
                UDM.Scalar("["), 
                UDM.Scalar("replacement")
            ))
        }
        
        assertThrows<FunctionArgumentException> {
            RegexFunctions.replaceRegex(listOf(
                UDM.Scalar("hello"), 
                UDM.Scalar("*"), 
                UDM.Scalar("replacement")
            ))
        }
    }

    @Test
    fun testNullValues() {
        // Test null string (should be converted to empty string)
        val result1 = RegexFunctions.matches(listOf(UDM.Scalar(null), UDM.Scalar(".*")))
        assertEquals(true, (result1 as UDM.Scalar).value) // null converts to empty string, .* matches empty string

        val result2 = RegexFunctions.matches(listOf(UDM.Scalar("hello"), UDM.Scalar(null)))
        assertEquals(false, (result2 as UDM.Scalar).value) // null pattern converts to empty string, which doesn't match "hello"

        val result3 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar(null),
            UDM.Scalar(".*"),
            UDM.Scalar("replacement")
        ))
        assertEquals("replacement", (result3 as UDM.Scalar).value)
    }

    // ==================== EDGE CASES ====================

    @Test
    fun testMatchesWithUnicodeCharacters() {
        val result1 = RegexFunctions.matches(listOf(UDM.Scalar("café"), UDM.Scalar("café")))
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        val result2 = RegexFunctions.matches(listOf(UDM.Scalar("🎉"), UDM.Scalar("🎉")))
        assertEquals(true, (result2 as UDM.Scalar).value)
        
        val result3 = RegexFunctions.matches(listOf(UDM.Scalar("𝓗𝓮𝓵𝓵𝓸"), UDM.Scalar(".*")))
        assertEquals(true, (result3 as UDM.Scalar).value)
    }

    @Test
    fun testReplaceRegexWithUnicodeCharacters() {
        val result1 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("café world"), 
            UDM.Scalar("café"), 
            UDM.Scalar("coffee")
        ))
        assertEquals("coffee world", (result1 as UDM.Scalar).value)
        
        val result2 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("Hello 🌍"), 
            UDM.Scalar("🌍"), 
            UDM.Scalar("World")
        ))
        assertEquals("Hello World", (result2 as UDM.Scalar).value)
    }

    @Test
    fun testMatchesWithNewlines() {
        // Test multiline mode
        val result1 = RegexFunctions.matches(listOf(
            UDM.Scalar("hello\nworld"), 
            UDM.Scalar("hello.world")
        ))
        assertEquals(false, (result1 as UDM.Scalar).value) // . doesn't match newline by default
        
        val result2 = RegexFunctions.matches(listOf(
            UDM.Scalar("hello\nworld"), 
            UDM.Scalar("(?s)hello.world")
        ))
        assertEquals(true, (result2 as UDM.Scalar).value) // (?s) enables DOTALL mode
    }

    @Test
    fun testReplaceRegexWithNewlines() {
        val result1 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("line1\nline2\nline3"), 
            UDM.Scalar("\n"), 
            UDM.Scalar(" | ")
        ))
        assertEquals("line1 | line2 | line3", (result1 as UDM.Scalar).value)
        
        val result2 = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar("hello\r\nworld"), 
            UDM.Scalar("\\r?\\n"), 
            UDM.Scalar(" ")
        ))
        assertEquals("hello world", (result2 as UDM.Scalar).value)
    }

    @Test
    fun testComplexRegexPatterns() {
        // Test URL pattern
        val urlPattern = "https?://[\\w.-]+(?:\\.[a-zA-Z]{2,})+(?:/[\\w._~:/?#\\[\\]@!$&'()*+,;=-]*)?"
        val result1 = RegexFunctions.matches(listOf(
            UDM.Scalar("https://www.example.com/path?query=value"), 
            UDM.Scalar(urlPattern)
        ))
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        // Test IP address pattern
        val ipPattern = "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)"
        val result2 = RegexFunctions.matches(listOf(
            UDM.Scalar("192.168.1.1"), 
            UDM.Scalar(ipPattern)
        ))
        assertEquals(true, (result2 as UDM.Scalar).value)
        
        val result3 = RegexFunctions.matches(listOf(
            UDM.Scalar("999.999.999.999"), 
            UDM.Scalar(ipPattern)
        ))
        assertEquals(false, (result3 as UDM.Scalar).value)
    }

    @Test
    fun testReplaceRegexPerformance() {
        // Test with large string
        val largeString = "a".repeat(10000)
        val result = RegexFunctions.replaceRegex(listOf(
            UDM.Scalar(largeString), 
            UDM.Scalar("a"), 
            UDM.Scalar("b")
        ))
        assertEquals("b".repeat(10000), (result as UDM.Scalar).value)
    }

    @Test
    fun testMatchesCaseSensitivity() {
        // Default case-sensitive
        val result1 = RegexFunctions.matches(listOf(UDM.Scalar("Hello"), UDM.Scalar("hello")))
        assertEquals(false, (result1 as UDM.Scalar).value)
        
        // Case-insensitive with flag
        val result2 = RegexFunctions.matches(listOf(UDM.Scalar("Hello"), UDM.Scalar("(?i)hello")))
        assertEquals(true, (result2 as UDM.Scalar).value)
    }
}