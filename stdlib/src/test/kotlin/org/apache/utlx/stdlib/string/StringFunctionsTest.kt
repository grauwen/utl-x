// stdlib/src/test/kotlin/org/apache/utlx/stdlib/string/StringFunctionsTest.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Comprehensive unit tests for String Functions
 * 
 * Tests all 33 string functions with:
 * - Normal cases
 * - Edge cases (empty strings, nulls)
 * - Error conditions
 * - Unicode handling
 * - Performance benchmarks
 */
class StringFunctionsTest {
    
    // ============================================================================
    // Basic String Operations
    // ============================================================================
    
    @Nested
    @DisplayName("Basic String Transformations")
    inner class BasicTransformations {
        
        @Test
        fun `upper() converts to uppercase`() {
            val input = UDM.Scalar("hello world")
            val result = StringFunctions.upper(input)
            assertEquals("HELLO WORLD", (result as UDM.Scalar).value)
        }
        
        @ParameterizedTest
        @CsvSource(
            "'hello', 'HELLO'",
            "'Hello World', 'HELLO WORLD'",
            "'123abc', '123ABC'",
            "'', ''",
            "'ñoño', 'ÑOÑO'"  // Unicode test
        )
        fun `upper() handles various inputs`(input: String, expected: String) {
            val result = StringFunctions.upper(UDM.Scalar(input))
            assertEquals(expected, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `lower() converts to lowercase`() {
            val input = UDM.Scalar("HELLO WORLD")
            val result = StringFunctions.lower(input)
            assertEquals("hello world", (result as UDM.Scalar).value)
        }
        
        @ParameterizedTest
        @CsvSource(
            "'HELLO', 'hello'",
            "'Hello World', 'hello world'",
            "'123ABC', '123abc'",
            "'', ''",
            "'ÑOÑO', 'ñoño'"
        )
        fun `lower() handles various inputs`(input: String, expected: String) {
            val result = StringFunctions.lower(UDM.Scalar(input))
            assertEquals(expected, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `trim() removes leading and trailing whitespace`() {
            val input = UDM.Scalar("  hello world  ")
            val result = StringFunctions.trim(input)
            assertEquals("hello world", (result as UDM.Scalar).value)
        }
        
        @ParameterizedTest
        @CsvSource(
            "'  hello  ', 'hello'",
            "'\thello\t', 'hello'",
            "'\nhello\n', 'hello'",
            "'hello', 'hello'",
            "'  ', ''",
            "'', ''"
        )
        fun `trim() handles various whitespace`(input: String, expected: String) {
            val result = StringFunctions.trim(UDM.Scalar(input))
            assertEquals(expected, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `leftTrim() removes only leading whitespace`() {
            val input = UDM.Scalar("  hello  ")
            val result = StringFunctions.leftTrim(input)
            assertEquals("hello  ", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `rightTrim() removes only trailing whitespace`() {
            val input = UDM.Scalar("  hello  ")
            val result = StringFunctions.rightTrim(input)
            assertEquals("  hello", (result as UDM.Scalar).value)
        }
    }
    
    // ============================================================================
    // String Extraction
    // ============================================================================
    
    @Nested
    @DisplayName("String Extraction Functions")
    inner class ExtractionFunctions {
        
        @Test
        fun `substring() extracts middle portion`() {
            val str = UDM.Scalar("hello world")
            val start = UDM.Scalar(0)
            val end = UDM.Scalar(5)
            val result = StringFunctions.substring(str, start, end)
            assertEquals("hello", (result as UDM.Scalar).value)
        }
        
        @ParameterizedTest
        @CsvSource(
            "'hello world', 0, 5, 'hello'",
            "'hello world', 6, 11, 'world'",
            "'hello world', 0, 11, 'hello world'",
            "'hello world', 3, 8, 'lo wo'"
        )
        fun `substring() handles various ranges`(
            input: String, 
            start: Int, 
            end: Int, 
            expected: String
        ) {
            val result = StringFunctions.substring(
                UDM.Scalar(input),
                UDM.Scalar(start),
                UDM.Scalar(end)
            )
            assertEquals(expected, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `substring() throws on invalid range`() {
            assertThrows<IllegalArgumentException> {
                StringFunctions.substring(
                    UDM.Scalar("hello"),
                    UDM.Scalar(5),
                    UDM.Scalar(3)  // End before start
                )
            }
        }
        
        @Test
        fun `substring-before() extracts text before delimiter`() {
            val str = UDM.Scalar("hello-world")
            val delimiter = UDM.Scalar("-")
            val result = StringFunctions.substringBefore(str, delimiter)
            assertEquals("hello", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `substring-after() extracts text after delimiter`() {
            val str = UDM.Scalar("hello-world")
            val delimiter = UDM.Scalar("-")
            val result = StringFunctions.substringAfter(str, delimiter)
            assertEquals("world", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `substring-before-last() uses last occurrence`() {
            val str = UDM.Scalar("a-b-c-d")
            val delimiter = UDM.Scalar("-")
            val result = StringFunctions.substringBeforeLast(str, delimiter)
            assertEquals("a-b-c", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `substring-after-last() uses last occurrence`() {
            val str = UDM.Scalar("a-b-c-d")
            val delimiter = UDM.Scalar("-")
            val result = StringFunctions.substringAfterLast(str, delimiter)
            assertEquals("d", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `left() extracts from beginning`() {
            val str = UDM.Scalar("hello world")
            val length = UDM.Scalar(5)
            val result = StringFunctions.left(str, length)
            assertEquals("hello", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `right() extracts from end`() {
            val str = UDM.Scalar("hello world")
            val length = UDM.Scalar(5)
            val result = StringFunctions.right(str, length)
            assertEquals("world", (result as UDM.Scalar).value)
        }
    }
    
    // ============================================================================
    // String Manipulation
    // ============================================================================
    
    @Nested
    @DisplayName("String Manipulation Functions")
    inner class ManipulationFunctions {
        
        @Test
        fun `concat() joins multiple strings`() {
            val parts = UDM.Array(listOf(
                UDM.Scalar("hello"),
                UDM.Scalar(" "),
                UDM.Scalar("world")
            ))
            val result = StringFunctions.concat(parts)
            assertEquals("hello world", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `split() separates string by delimiter`() {
            val str = UDM.Scalar("a,b,c")
            val delimiter = UDM.Scalar(",")
            val result = StringFunctions.split(str, delimiter) as UDM.Array
            
            assertEquals(3, result.elements.size)
            assertEquals("a", (result.elements[0] as UDM.Scalar).value)
            assertEquals("b", (result.elements[1] as UDM.Scalar).value)
            assertEquals("c", (result.elements[2] as UDM.Scalar).value)
        }
        
        @Test
        fun `join() combines array with delimiter`() {
            val arr = UDM.Array(listOf(
                UDM.Scalar("a"),
                UDM.Scalar("b"),
                UDM.Scalar("c")
            ))
            val delimiter = UDM.Scalar(",")
            val result = StringFunctions.join(arr, delimiter)
            assertEquals("a,b,c", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `replace() substitutes occurrences`() {
            val str = UDM.Scalar("hello hello")
            val search = UDM.Scalar("hello")
            val replace = UDM.Scalar("hi")
            val result = StringFunctions.replace(str, search, replace)
            assertEquals("hi hi", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `replace-first() substitutes only first occurrence`() {
            val str = UDM.Scalar("hello hello")
            val search = UDM.Scalar("hello")
            val replace = UDM.Scalar("hi")
            val result = StringFunctions.replaceFirst(str, search, replace)
            assertEquals("hi hello", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `reverse() reverses string`() {
            val str = UDM.Scalar("hello")
            val result = StringFunctions.reverse(str)
            assertEquals("olleh", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `repeat() duplicates string n times`() {
            val str = UDM.Scalar("*")
            val count = UDM.Scalar(5)
            val result = StringFunctions.repeat(str, count)
            assertEquals("*****", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `pad-left() adds padding to left`() {
            val str = UDM.Scalar("hello")
            val length = UDM.Scalar(10)
            val pad = UDM.Scalar(" ")
            val result = StringFunctions.padLeft(str, length, pad)
            assertEquals("     hello", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `pad-right() adds padding to right`() {
            val str = UDM.Scalar("hello")
            val length = UDM.Scalar(10)
            val pad = UDM.Scalar(" ")
            val result = StringFunctions.padRight(str, length, pad)
            assertEquals("hello     ", (result as UDM.Scalar).value)
        }
    }
    
    // ============================================================================
    // String Analysis
    // ============================================================================
    
    @Nested
    @DisplayName("String Analysis Functions")
    inner class AnalysisFunctions {
        
        @Test
        fun `length() returns character count`() {
            val str = UDM.Scalar("hello")
            val result = StringFunctions.length(str)
            assertEquals(5, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `contains() checks for substring`() {
            val str = UDM.Scalar("hello world")
            val search = UDM.Scalar("world")
            val result = StringFunctions.contains(str, search)
            assertTrue((result as UDM.Scalar).value as Boolean)
        }
        
        @Test
        fun `starts-with() checks prefix`() {
            val str = UDM.Scalar("hello world")
            val prefix = UDM.Scalar("hello")
            val result = StringFunctions.startsWith(str, prefix)
            assertTrue((result as UDM.Scalar).value as Boolean)
        }
        
        @Test
        fun `ends-with() checks suffix`() {
            val str = UDM.Scalar("hello world")
            val suffix = UDM.Scalar("world")
            val result = StringFunctions.endsWith(str, suffix)
            assertTrue((result as UDM.Scalar).value as Boolean)
        }
        
        @Test
        fun `index-of() finds first occurrence`() {
            val str = UDM.Scalar("hello hello")
            val search = UDM.Scalar("hello")
            val result = StringFunctions.indexOf(str, search)
            assertEquals(0, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `last-index-of() finds last occurrence`() {
            val str = UDM.Scalar("hello hello")
            val search = UDM.Scalar("hello")
            val result = StringFunctions.lastIndexOf(str, search)
            assertEquals(6, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `compare() performs case-sensitive comparison`() {
            val str1 = UDM.Scalar("abc")
            val str2 = UDM.Scalar("abc")
            val result = StringFunctions.compare(str1, str2)
            assertEquals(0, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `compare-ignore-case() performs case-insensitive comparison`() {
            val str1 = UDM.Scalar("ABC")
            val str2 = UDM.Scalar("abc")
            val result = StringFunctions.compareIgnoreCase(str1, str2)
            assertEquals(0, (result as UDM.Scalar).value)
        }
    }
    
    // ============================================================================
    // Regular Expressions
    // ============================================================================
    
    @Nested
    @DisplayName("Regular Expression Functions")
    inner class RegexFunctions {
        
        @Test
        fun `matches() tests regex pattern`() {
            val str = UDM.Scalar("hello123")
            val pattern = UDM.Scalar("[a-z]+\\d+")
            val result = org.apache.utlx.stdlib.string.RegexFunctions.matches(str, pattern)
            assertTrue((result as UDM.Scalar).value as Boolean)
        }
        
        @Test
        fun `replace-regex() substitutes pattern matches`() {
            val str = UDM.Scalar("hello123world456")
            val pattern = UDM.Scalar("\\d+")
            val replacement = UDM.Scalar("X")
            val result = org.apache.utlx.stdlib.string.RegexFunctions.replaceRegex(
                str, pattern, replacement
            )
            assertEquals("helloXworldX", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `extract-regex() finds pattern matches`() {
            val str = UDM.Scalar("Order: 123, Total: 456")
            val pattern = UDM.Scalar("\\d+")
            val result = org.apache.utlx.stdlib.string.RegexFunctions.extractRegex(
                str, pattern
            ) as UDM.Array
            
            assertEquals(2, result.elements.size)
            assertEquals("123", (result.elements[0] as UDM.Scalar).value)
            assertEquals("456", (result.elements[1] as UDM.Scalar).value)
        }
    }
    
    // ============================================================================
    // Edge Cases and Error Handling
    // ============================================================================
    
    @Nested
    @DisplayName("Edge Cases and Error Handling")
    inner class EdgeCases {
        
        @Test
        fun `handles empty strings`() {
            val empty = UDM.Scalar("")
            
            assertEquals("", (StringFunctions.upper(empty) as UDM.Scalar).value)
            assertEquals("", (StringFunctions.lower(empty) as UDM.Scalar).value)
            assertEquals("", (StringFunctions.trim(empty) as UDM.Scalar).value)
            assertEquals(0, (StringFunctions.length(empty) as UDM.Scalar).value)
        }
        
        @Test
        fun `handles null values gracefully`() {
            val nullValue = UDM.Scalar(null)
            
            assertThrows<IllegalArgumentException> {
                StringFunctions.upper(nullValue)
            }
        }
        
        @Test
        fun `handles unicode correctly`() {
            val unicode = UDM.Scalar("héllo wörld 你好")
            val result = StringFunctions.upper(unicode)
            assertEquals("HÉLLO WÖRLD 你好", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `handles very long strings efficiently`() {
            val longString = UDM.Scalar("a".repeat(100000))
            val startTime = System.currentTimeMillis()
            val result = StringFunctions.length(longString)
            val duration = System.currentTimeMillis() - startTime
            
            assertEquals(100000, (result as UDM.Scalar).value)
            assertTrue(duration < 100, "Should complete in under 100ms")
        }
    }
    
    // ============================================================================
    // Integration Tests
    // ============================================================================
    
    @Nested
    @DisplayName("Integration Scenarios")
    inner class IntegrationTests {
        
        @Test
        fun `chaining multiple string operations`() {
            // Scenario: Clean and format user input
            val input = UDM.Scalar("  JOHN.DOE@EXAMPLE.COM  ")
            
            // trim -> lower -> split -> join
            val trimmed = StringFunctions.trim(input)
            val lower = StringFunctions.lower(trimmed)
            val parts = StringFunctions.split(lower, UDM.Scalar("@")) as UDM.Array
            val username = (parts.elements[0] as UDM.Scalar).value
            
            assertEquals("john.doe", username)
        }
        
        @Test
        fun `parsing structured text`() {
            // Scenario: Extract data from formatted string
            val log = UDM.Scalar("[2025-10-14 10:30:45] ERROR: Connection timeout")
            
            // Extract timestamp
            val afterBracket = StringFunctions.substringAfter(log, UDM.Scalar("["))
            val timestamp = StringFunctions.substringBefore(afterBracket, UDM.Scalar("]"))
            assertEquals("2025-10-14 10:30:45", (timestamp as UDM.Scalar).value)
            
            // Extract level
            val afterTimestamp = StringFunctions.substringAfter(log, UDM.Scalar("] "))
            val level = StringFunctions.substringBefore(afterTimestamp, UDM.Scalar(":"))
            assertEquals("ERROR", (level as UDM.Scalar).value)
        }
    }
}
