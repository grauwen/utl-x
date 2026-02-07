package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringFunctionsTest {

    @Test
    fun testUpper() {
        val result = StringFunctions.upper(listOf(UDM.Scalar("hello world")))
        assertTrue(result is UDM.Scalar)
        assertEquals("HELLO WORLD", (result as UDM.Scalar).value)
        
        // Test empty string
        val emptyResult = StringFunctions.upper(listOf(UDM.Scalar("")))
        assertEquals("", (emptyResult as UDM.Scalar).value)
    }

    @Test
    fun testLower() {
        val result = StringFunctions.lower(listOf(UDM.Scalar("HELLO WORLD")))
        assertTrue(result is UDM.Scalar)
        assertEquals("hello world", (result as UDM.Scalar).value)
        
        // Test mixed case
        val mixedResult = StringFunctions.lower(listOf(UDM.Scalar("Hello World")))
        assertEquals("hello world", (mixedResult as UDM.Scalar).value)
    }

    @Test
    fun testTrim() {
        val result = StringFunctions.trim(listOf(UDM.Scalar("  hello world  ")))
        assertTrue(result is UDM.Scalar)
        assertEquals("hello world", (result as UDM.Scalar).value)
        
        // Test tabs and newlines
        val complexResult = StringFunctions.trim(listOf(UDM.Scalar("\t\n  hello  \n\t")))
        assertEquals("hello", (complexResult as UDM.Scalar).value)
    }

    @Test
    fun testSubstring() {
        val result = StringFunctions.substring(listOf(UDM.Scalar("hello world"), UDM.Scalar(0), UDM.Scalar(5)))
        assertTrue(result is UDM.Scalar)
        assertEquals("hello", (result as UDM.Scalar).value)

        // Test from index only
        val fromResult = StringFunctions.substring(listOf(UDM.Scalar("hello world"), UDM.Scalar(6)))
        assertEquals("world", (fromResult as UDM.Scalar).value)
    }

    /**
     * Test for B03: substring out-of-bounds should clamp indices, not throw exception.
     *
     * Previously, substring(str, 0, 80) on a 52-char string would throw
     * StringIndexOutOfBoundsException, which was masked as "Undefined function: substring".
     *
     * @see docs/bugs-fixed/B03-substring-out-of-bounds-masked-as-undefined-function.md
     */
    @Test
    fun testSubstringBoundsClamping() {
        // Reproduce the exact scenario from the bug report:
        // substring("Implemented authentication middleware for API gateway", 0, 80)
        // The string is 53 chars, end index 80 should be clamped to 53
        val description = "Implemented authentication middleware for API gateway"
        assertEquals(53, description.length, "Test precondition: string should be 53 chars")

        val result = StringFunctions.substring(listOf(
            UDM.Scalar(description),
            UDM.Scalar(0),
            UDM.Scalar(80)  // end index > string length
        ))
        assertTrue(result is UDM.Scalar)
        assertEquals(description, (result as UDM.Scalar).value,
            "End index beyond string length should be clamped, returning full string")

        // Test: start index beyond string length should return empty string
        val emptyResult = StringFunctions.substring(listOf(
            UDM.Scalar("hello"),
            UDM.Scalar(100),  // start index > string length
            UDM.Scalar(200)
        ))
        assertEquals("", (emptyResult as UDM.Scalar).value,
            "Start index beyond string length should return empty string")

        // Test: negative start index should be clamped to 0
        val negativeStartResult = StringFunctions.substring(listOf(
            UDM.Scalar("hello"),
            UDM.Scalar(-5),
            UDM.Scalar(3)
        ))
        assertEquals("hel", (negativeStartResult as UDM.Scalar).value,
            "Negative start index should be clamped to 0")

        // Test: both indices out of bounds
        val bothOutOfBounds = StringFunctions.substring(listOf(
            UDM.Scalar("test"),
            UDM.Scalar(-10),
            UDM.Scalar(100)
        ))
        assertEquals("test", (bothOutOfBounds as UDM.Scalar).value,
            "Both indices out of bounds should be clamped, returning full string")

        // Test: start > end after clamping should return empty string
        val invertedResult = StringFunctions.substring(listOf(
            UDM.Scalar("hello"),
            UDM.Scalar(10),  // clamped to 5
            UDM.Scalar(3)    // clamped to 3, but start > end
        ))
        assertEquals("", (invertedResult as UDM.Scalar).value,
            "When start > end after clamping, should return empty string")
    }

    @Test
    fun testConcat() {
        val result = StringFunctions.concat(listOf(UDM.Scalar("hello"), UDM.Scalar(" "), UDM.Scalar("world")))
        assertTrue(result is UDM.Scalar)
        assertEquals("hello world", (result as UDM.Scalar).value)
        
        // Test with numbers
        val numResult = StringFunctions.concat(listOf(UDM.Scalar("number: "), UDM.Scalar(42)))
        assertEquals("number: 42", (numResult as UDM.Scalar).value)
    }

    @Test
    fun testSplit() {
        val result = StringFunctions.split(listOf(UDM.Scalar("hello,world,test"), UDM.Scalar(",")))
        assertTrue(result is UDM.Array)
        val splitArray = result as UDM.Array
        assertEquals(3, splitArray.elements.size)
        assertEquals("hello", (splitArray.elements[0] as UDM.Scalar).value)
        assertEquals("world", (splitArray.elements[1] as UDM.Scalar).value)
        assertEquals("test", (splitArray.elements[2] as UDM.Scalar).value)
    }

    @Test
    fun testJoin() {
        val array = UDM.Array(listOf(UDM.Scalar("hello"), UDM.Scalar("world"), UDM.Scalar("test")))
        val result = StringFunctions.join(listOf(array, UDM.Scalar(",")))
        assertTrue(result is UDM.Scalar)
        assertEquals("hello,world,test", (result as UDM.Scalar).value)
        
        // Test with different separator
        val spaceResult = StringFunctions.join(listOf(array, UDM.Scalar(" ")))
        assertEquals("hello world test", (spaceResult as UDM.Scalar).value)
    }

    @Test
    fun testReplace() {
        val result = StringFunctions.replace(listOf(UDM.Scalar("hello world"), UDM.Scalar("world"), UDM.Scalar("universe")))
        assertTrue(result is UDM.Scalar)
        assertEquals("hello universe", (result as UDM.Scalar).value)

        // Test multiple occurrences
        val multiResult = StringFunctions.replace(listOf(UDM.Scalar("test test test"), UDM.Scalar("test"), UDM.Scalar("example")))
        assertEquals("example example example", (multiResult as UDM.Scalar).value)
    }

    @Test
    fun testReplaceMultipleViaObject() {
        // Mode 2: replace(str, {search1: repl1, search2: repl2, ...})
        val replacements = UDM.Object(mutableMapOf(
            "\n" to UDM.Scalar(""),
            "\t" to UDM.Scalar(" "),
            "\r" to UDM.Scalar("")
        ))

        val input = "Hello\n\tWorld\r\n"
        val result = StringFunctions.replace(listOf(UDM.Scalar(input), replacements))

        assertTrue(result is UDM.Scalar)
        assertEquals("Hello World", (result as UDM.Scalar).value)
    }

    @Test
    fun testReplaceMultipleViaObjectComplex() {
        // Test multiple string replacements in order
        val replacements = UDM.Object(mutableMapOf(
            "foo" to UDM.Scalar("bar"),
            "baz" to UDM.Scalar("qux"),
            " " to UDM.Scalar("_")
        ))

        val input = "foo baz test"
        val result = StringFunctions.replace(listOf(UDM.Scalar(input), replacements))

        assertTrue(result is UDM.Scalar)
        assertEquals("bar_qux_test", (result as UDM.Scalar).value)
    }

    @Test
    fun testReplaceMultipleViaArray() {
        // Mode 3: replace(str, [[search1, repl1], [search2, repl2], ...])
        val replacements = UDM.Array(mutableListOf(
            UDM.Array(mutableListOf(UDM.Scalar("\n"), UDM.Scalar(""))),
            UDM.Array(mutableListOf(UDM.Scalar("\t"), UDM.Scalar(" "))),
            UDM.Array(mutableListOf(UDM.Scalar("\r"), UDM.Scalar("")))
        ))

        val input = "Hello\n\tWorld\r\n"
        val result = StringFunctions.replace(listOf(UDM.Scalar(input), replacements))

        assertTrue(result is UDM.Scalar)
        assertEquals("Hello World", (result as UDM.Scalar).value)
    }

    @Test
    fun testReplaceMultipleViaArrayComplex() {
        // Test ordered replacements via array
        val replacements = UDM.Array(mutableListOf(
            UDM.Array(mutableListOf(UDM.Scalar("hello"), UDM.Scalar("HELLO"))),
            UDM.Array(mutableListOf(UDM.Scalar("world"), UDM.Scalar("WORLD")))
        ))

        val input = "hello world"
        val result = StringFunctions.replace(listOf(UDM.Scalar(input), replacements))

        assertTrue(result is UDM.Scalar)
        assertEquals("HELLO WORLD", (result as UDM.Scalar).value)
    }

    @Test
    fun testReplaceBackwardCompatibility() {
        // Ensure Mode 1 (3 arguments) still works as before
        val result = StringFunctions.replace(listOf(
            UDM.Scalar("test string"),
            UDM.Scalar("string"),
            UDM.Scalar("text")
        ))

        assertTrue(result is UDM.Scalar)
        assertEquals("test text", (result as UDM.Scalar).value)
    }

    @Test
    fun testReplaceInvalidArguments() {
        // Test with wrong number of arguments
        assertThrows<IllegalArgumentException> {
            StringFunctions.replace(listOf())
        }

        // Test with invalid array format (not pairs)
        val invalidArray = UDM.Array(mutableListOf(
            UDM.Array(mutableListOf(UDM.Scalar("only_one")))  // Should be [search, replacement]
        ))

        assertThrows<IllegalArgumentException> {
            StringFunctions.replace(listOf(UDM.Scalar("test"), invalidArray))
        }
    }

    @Test
    fun testReplaceEmptyReplacements() {
        // Test with empty object (no replacements)
        val emptyObj = UDM.Object(mutableMapOf())
        val result = StringFunctions.replace(listOf(UDM.Scalar("test"), emptyObj))

        assertTrue(result is UDM.Scalar)
        assertEquals("test", (result as UDM.Scalar).value)

        // Test with empty array (no replacements)
        val emptyArray = UDM.Array(mutableListOf())
        val result2 = StringFunctions.replace(listOf(UDM.Scalar("test"), emptyArray))

        assertTrue(result2 is UDM.Scalar)
        assertEquals("test", (result2 as UDM.Scalar).value)
    }

    @Test
    fun testContains() {
        val result = StringFunctions.contains(listOf(UDM.Scalar("hello world"), UDM.Scalar("world")))
        assertTrue(result is UDM.Scalar)
        assertEquals(true, (result as UDM.Scalar).value)
        
        val falseResult = StringFunctions.contains(listOf(UDM.Scalar("hello world"), UDM.Scalar("universe")))
        assertEquals(false, (falseResult as UDM.Scalar).value)
    }

    @Test
    fun testStartsWith() {
        val result = StringFunctions.startsWith(listOf(UDM.Scalar("hello world"), UDM.Scalar("hello")))
        assertTrue(result is UDM.Scalar)
        assertEquals(true, (result as UDM.Scalar).value)
        
        val falseResult = StringFunctions.startsWith(listOf(UDM.Scalar("hello world"), UDM.Scalar("world")))
        assertEquals(false, (falseResult as UDM.Scalar).value)
    }

    @Test
    fun testEndsWith() {
        val result = StringFunctions.endsWith(listOf(UDM.Scalar("hello world"), UDM.Scalar("world")))
        assertTrue(result is UDM.Scalar)
        assertEquals(true, (result as UDM.Scalar).value)
        
        val falseResult = StringFunctions.endsWith(listOf(UDM.Scalar("hello world"), UDM.Scalar("hello")))
        assertEquals(false, (falseResult as UDM.Scalar).value)
    }

    @Test
    fun testLength() {
        val result = StringFunctions.length(listOf(UDM.Scalar("hello world")))
        assertTrue(result is UDM.Scalar)
        assertEquals(11.0, (result as UDM.Scalar).value)

        val emptyResult = StringFunctions.length(listOf(UDM.Scalar("")))
        assertEquals(0.0, (emptyResult as UDM.Scalar).value)
    }

    @Test
    fun testSubstringBefore() {
        val result = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("hello,world"), UDM.Scalar(",")))
        assertTrue(result is UDM.Scalar)
        assertEquals("hello", (result as UDM.Scalar).value)
        
        // Test when delimiter not found
        val notFoundResult = ExtendedStringFunctions.substringBefore(listOf(UDM.Scalar("hello world"), UDM.Scalar(",")))
        assertEquals("hello world", (notFoundResult as UDM.Scalar).value)
    }

    @Test
    fun testSubstringAfter() {
        val result = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("hello,world"), UDM.Scalar(",")))
        assertTrue(result is UDM.Scalar)
        assertEquals("world", (result as UDM.Scalar).value)
        
        // Test when delimiter not found
        val notFoundResult = ExtendedStringFunctions.substringAfter(listOf(UDM.Scalar("hello world"), UDM.Scalar(",")))
        assertEquals("", (notFoundResult as UDM.Scalar).value)
    }

    @Test
    fun testSubstringBeforeLast() {
        val result = ExtendedStringFunctions.substringBeforeLast(listOf(UDM.Scalar("hello.world.test"), UDM.Scalar(".")))
        assertTrue(result is UDM.Scalar)
        assertEquals("hello.world", (result as UDM.Scalar).value)
    }

    @Test
    fun testSubstringAfterLast() {
        val result = ExtendedStringFunctions.substringAfterLast(listOf(UDM.Scalar("hello.world.test"), UDM.Scalar(".")))
        assertTrue(result is UDM.Scalar)
        assertEquals("test", (result as UDM.Scalar).value)
    }

    // Note: testInvalidArguments removed - validation is handled at runtime by the UTL-X engine via @UTLXFunction annotations

    @Test
    fun testEdgeCases() {
        // Test empty strings
        val emptyUpper = StringFunctions.upper(listOf(UDM.Scalar("")))
        assertEquals("", (emptyUpper as UDM.Scalar).value)

        val emptySplit = StringFunctions.split(listOf(UDM.Scalar(""), UDM.Scalar(",")))
        assertTrue(emptySplit is UDM.Array)
        assertEquals(1, (emptySplit as UDM.Array).elements.size)
        assertEquals("", ((emptySplit as UDM.Array).elements[0] as UDM.Scalar).value)

        // Test single character operations
        val singleChar = StringFunctions.upper(listOf(UDM.Scalar("a")))
        assertEquals("A", (singleChar as UDM.Scalar).value)

        // Test Unicode support
        val unicodeResult = StringFunctions.upper(listOf(UDM.Scalar("café")))
        assertEquals("CAFÉ", (unicodeResult as UDM.Scalar).value)

        // Null handling: null values are converted to empty string by asString()
        val nullResult = StringFunctions.upper(listOf(UDM.Scalar(null)))
        assertEquals("", (nullResult as UDM.Scalar).value)
    }

    @Test
    fun testComplexOperations() {
        // Test chaining operations conceptually
        val input = "  Hello, World!  "
        
        // Trim first
        val trimmed = StringFunctions.trim(listOf(UDM.Scalar(input)))
        assertEquals("Hello, World!", (trimmed as UDM.Scalar).value)
        
        // Then split
        val split = StringFunctions.split(listOf(trimmed, UDM.Scalar(", ")))
        assertTrue(split is UDM.Array)
        val splitArray = split as UDM.Array
        assertEquals(2, splitArray.elements.size)
        assertEquals("Hello", (splitArray.elements[0] as UDM.Scalar).value)
        assertEquals("World!", (splitArray.elements[1] as UDM.Scalar).value)
        
        // Then join with different separator
        val joined = StringFunctions.join(listOf(split, UDM.Scalar(" - ")))
        assertEquals("Hello - World!", (joined as UDM.Scalar).value)
    }
}