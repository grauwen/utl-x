package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class CaseConversionFunctionsTest {

   
    @Test
    fun testFromCamelCase() {
        // Test basic uncamelization
        val result1 = CaseConversionFunctions.fromCamelCase(listOf(UDM.Scalar("helloWorld")))
        assertEquals("hello world", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.fromCamelCase(listOf(UDM.Scalar("firstName")))
        assertEquals("first name", (result2 as UDM.Scalar).value)

        // Test multiple capital letters
        val result3 = CaseConversionFunctions.fromCamelCase(listOf(UDM.Scalar("firstNameLastName")))
        assertEquals("first name last name", (result3 as UDM.Scalar).value)

        // Test PascalCase
        val result4 = CaseConversionFunctions.fromCamelCase(listOf(UDM.Scalar("HelloWorld")))
        assertEquals("hello world", (result4 as UDM.Scalar).value)

        // Test single word lowercase
        val result5 = CaseConversionFunctions.fromCamelCase(listOf(UDM.Scalar("word")))
        assertEquals("word", (result5 as UDM.Scalar).value)

        // Test single word uppercase
        val result6 = CaseConversionFunctions.fromCamelCase(listOf(UDM.Scalar("Word")))
        assertEquals("word", (result6 as UDM.Scalar).value)

        // Test empty string
        val result7 = CaseConversionFunctions.fromCamelCase(listOf(UDM.Scalar("")))
        assertEquals("", (result7 as UDM.Scalar).value)

        // Test consecutive capitals
        val result8 = CaseConversionFunctions.fromCamelCase(listOf(UDM.Scalar("XMLParser")))
        assertEquals("x m l parser", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testTruncate() {
        // Test basic truncation
        val result1 = CaseConversionFunctions.truncate(listOf(UDM.Scalar("Hello World"), UDM.Scalar(8)))
        assertEquals("Hello...", (result1 as UDM.Scalar).value)

        // Test no truncation needed
        val result2 = CaseConversionFunctions.truncate(listOf(UDM.Scalar("Short"), UDM.Scalar(10)))
        assertEquals("Short", (result2 as UDM.Scalar).value)

        // Test custom ellipsis
        val result3 = CaseConversionFunctions.truncate(listOf(UDM.Scalar("Hello World"), UDM.Scalar(8), UDM.Scalar(">>")))
        assertEquals("Hello>>" , (result3 as UDM.Scalar).value)

        // Test exact length
        val result4 = CaseConversionFunctions.truncate(listOf(UDM.Scalar("Hello"), UDM.Scalar(5)))
        assertEquals("Hello", (result4 as UDM.Scalar).value)

        // Test very short max length
        val result5 = CaseConversionFunctions.truncate(listOf(UDM.Scalar("Hello World"), UDM.Scalar(3)))
        assertEquals("...", (result5 as UDM.Scalar).value)

        // Test ellipsis longer than max length
        val result6 = CaseConversionFunctions.truncate(listOf(UDM.Scalar("Hello"), UDM.Scalar(2), UDM.Scalar("...")))
        assertEquals("..", (result6 as UDM.Scalar).value)

        // Test empty string
        val result7 = CaseConversionFunctions.truncate(listOf(UDM.Scalar(""), UDM.Scalar(5)))
        assertEquals("", (result7 as UDM.Scalar).value)

        // Test zero length
        val result8 = CaseConversionFunctions.truncate(listOf(UDM.Scalar("Hello"), UDM.Scalar(0)))
        assertEquals("", (result8 as UDM.Scalar).value)

        // Test negative length (should behave like zero)
        val result9 = CaseConversionFunctions.truncate(listOf(UDM.Scalar("Hello"), UDM.Scalar(-5)))
        assertEquals("...", (result9 as UDM.Scalar).value)
    }

    /**
     * Regression test for B04: truncate silent crash
     *
     * Previously, calling truncate with 2 arguments caused a silent crash (exit 1, no output)
     * because the @UTLXFunction annotation incorrectly required 3 arguments (minArgs=3)
     * while the implementation accepts 2-3 arguments (ellipsis is optional).
     *
     * @see docs/bugs-fixed/B04-truncate-silent-crash.md
     */
    @Test
    fun testTruncateB04RegressionTwoArguments() {
        // Exact scenario from B04 bug report:
        // truncate("Cloud Infrastructure Services - Premium Tier", 50)
        // String is 44 chars, limit is 50, should return unchanged
        val description = "Cloud Infrastructure Services - Premium Tier"
        assertEquals(44, description.length, "Test precondition: string should be 44 chars")

        val result = CaseConversionFunctions.truncate(listOf(
            UDM.Scalar(description),
            UDM.Scalar(50)  // 2 arguments only - ellipsis defaults to "..."
        ))
        assertEquals(description, (result as UDM.Scalar).value,
            "String shorter than limit should be returned unchanged with 2 args")

        // Additional 2-argument tests to verify the fix
        val result2 = CaseConversionFunctions.truncate(listOf(
            UDM.Scalar("Short text"),
            UDM.Scalar(100)
        ))
        assertEquals("Short text", (result2 as UDM.Scalar).value,
            "Short string with 2 args should work")

        // Test 2-argument call that actually truncates
        val result3 = CaseConversionFunctions.truncate(listOf(
            UDM.Scalar("This is a long string that needs truncation"),
            UDM.Scalar(20)
        ))
        assertEquals("This is a long st...", (result3 as UDM.Scalar).value,
            "Truncation with 2 args should work with default ellipsis")
    }

    @Test
    fun testTruncateThreeArguments() {
        // Verify 3-argument calls still work correctly
        // "Hello World Example" (19 chars), limit 12, ellipsis "..." (3 chars)
        // truncateAt = 12 - 3 = 9, take first 9 chars "Hello Wor", trimEnd, add "..."
        val result1 = CaseConversionFunctions.truncate(listOf(
            UDM.Scalar("Hello World Example"),
            UDM.Scalar(12),
            UDM.Scalar("...")
        ))
        assertEquals("Hello Wor...", (result1 as UDM.Scalar).value,
            "3-arg truncation with standard ellipsis")

        val result2 = CaseConversionFunctions.truncate(listOf(
            UDM.Scalar("Hello World Example"),
            UDM.Scalar(12),
            UDM.Scalar("→")
        ))
        assertEquals("Hello World→", (result2 as UDM.Scalar).value,
            "3-arg truncation with custom single-char ellipsis")

        val result3 = CaseConversionFunctions.truncate(listOf(
            UDM.Scalar("Short"),
            UDM.Scalar(100),
            UDM.Scalar("[...]")
        ))
        assertEquals("Short", (result3 as UDM.Scalar).value,
            "3-arg call with string shorter than limit returns unchanged")
    }

    @Test
    fun testSlugify() {
        // Test basic slugification
        val result1 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("Hello World!")))
        assertEquals("hello-world", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("  Foo & Bar  ")))
        assertEquals("foo-and-bar", (result2 as UDM.Scalar).value)

        val result3 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("C++ Programming")))
        assertEquals("c-plus-plus-programming", (result3 as UDM.Scalar).value)

        // Test symbols replacement
        val result4 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("John @ Company")))
        assertEquals("john-at-company", (result4 as UDM.Scalar).value)

        // Test special characters removal
        val result5 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("Hello, World! #123")))
        assertEquals("hello-world-123", (result5 as UDM.Scalar).value)

        // Test multiple spaces and dashes
        val result6 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("Too   Many    Spaces")))
        assertEquals("too-many-spaces", (result6 as UDM.Scalar).value)

        val result7 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("Already-has---dashes")))
        assertEquals("already-has-dashes", (result7 as UDM.Scalar).value)

        // Test empty string
        val result8 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("")))
        assertEquals("", (result8 as UDM.Scalar).value)

        // Test only special characters (@ becomes "at" and & becomes "and")
        val result9 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("!@#$%^&*()")))
        assertEquals("at-and", (result9 as UDM.Scalar).value)

        // Test leading/trailing dashes
        val result10 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("-Leading and Trailing-")))
        assertEquals("leading-and-trailing", (result10 as UDM.Scalar).value)

        // Test numbers
        val result11 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("Version 2.0")))
        assertEquals("version-20", (result11 as UDM.Scalar).value)
    }

    @Test
    fun testArgumentValidation() {
        // Test wrong number of arguments
        assertThrows<IllegalArgumentException> {
            CaseConversionFunctions.truncate(listOf(UDM.Scalar("test")))
        }

        assertThrows<IllegalArgumentException> {
            CaseConversionFunctions.truncate(listOf(UDM.Scalar("test"), UDM.Scalar(5), UDM.Scalar("..."), UDM.Scalar("extra")))
        }

        // Test wrong argument types

        assertThrows<IllegalArgumentException> {
            CaseConversionFunctions.truncate(listOf(UDM.Scalar("test"), UDM.Scalar("not a number")))
        }

        assertThrows<IllegalArgumentException> {
            CaseConversionFunctions.truncate(listOf(UDM.Scalar("test"), UDM.Scalar(5), UDM.Scalar(123)))
        }
    }

    @Test
    fun testEdgeCases() {
        // Test unicode characters
        val result2 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("Café & Naïve")))
        assertEquals("caf-and-nave", (result2 as UDM.Scalar).value)

    }



    @Test
    fun testSpecialCharacterHandling() {
        // Test with various special characters
        val result1 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("Rock & Roll + Jazz @ Home")))
        assertEquals("rock-and-roll-plus-jazz-at-home", (result1 as UDM.Scalar).value)

        // Test with punctuation
        val result2 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("Hello, World! How are you?")))
        assertEquals("hello-world-how-are-you", (result2 as UDM.Scalar).value)

        // Test with parentheses and brackets
        val result3 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("Test (Version 1.0) [Beta]")))
        assertEquals("test-version-10-beta", (result3 as UDM.Scalar).value)
    }

    @Test
    fun testFromPascalCase() {
        // Test basic PascalCase conversion
        val result1 = CaseConversionFunctions.fromPascalCase(listOf(UDM.Scalar("HelloWorld")))
        assertEquals("hello world", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.fromPascalCase(listOf(UDM.Scalar("FirstName")))
        assertEquals("first name", (result2 as UDM.Scalar).value)

        // Test multiple words
        val result3 = CaseConversionFunctions.fromPascalCase(listOf(UDM.Scalar("FirstNameLastName")))
        assertEquals("first name last name", (result3 as UDM.Scalar).value)

        // Test single word uppercase
        val result4 = CaseConversionFunctions.fromPascalCase(listOf(UDM.Scalar("Word")))
        assertEquals("word", (result4 as UDM.Scalar).value)

        // Test single word lowercase (edge case)
        val result5 = CaseConversionFunctions.fromPascalCase(listOf(UDM.Scalar("word")))
        assertEquals("word", (result5 as UDM.Scalar).value)

        // Test empty string
        val result6 = CaseConversionFunctions.fromPascalCase(listOf(UDM.Scalar("")))
        assertEquals("", (result6 as UDM.Scalar).value)

        // Test consecutive capitals
        val result7 = CaseConversionFunctions.fromPascalCase(listOf(UDM.Scalar("XMLParser")))
        assertEquals("x m l parser", (result7 as UDM.Scalar).value)
    }

    @Test
    fun testFromKebabCase() {
        // Test basic kebab-case conversion
        val result1 = CaseConversionFunctions.fromKebabCase(listOf(UDM.Scalar("hello-world")))
        assertEquals("hello world", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.fromKebabCase(listOf(UDM.Scalar("first-name")))
        assertEquals("first name", (result2 as UDM.Scalar).value)

        // Test multiple words
        val result3 = CaseConversionFunctions.fromKebabCase(listOf(UDM.Scalar("first-name-last-name")))
        assertEquals("first name last name", (result3 as UDM.Scalar).value)

        // Test single word (no dashes)
        val result4 = CaseConversionFunctions.fromKebabCase(listOf(UDM.Scalar("word")))
        assertEquals("word", (result4 as UDM.Scalar).value)

        // Test empty string
        val result5 = CaseConversionFunctions.fromKebabCase(listOf(UDM.Scalar("")))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test with numbers
        val result6 = CaseConversionFunctions.fromKebabCase(listOf(UDM.Scalar("test-123-abc")))
        assertEquals("test 123 abc", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testFromSnakeCase() {
        // Test basic snake_case conversion
        val result1 = CaseConversionFunctions.fromSnakeCase(listOf(UDM.Scalar("hello_world")))
        assertEquals("hello world", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.fromSnakeCase(listOf(UDM.Scalar("first_name")))
        assertEquals("first name", (result2 as UDM.Scalar).value)

        // Test multiple words
        val result3 = CaseConversionFunctions.fromSnakeCase(listOf(UDM.Scalar("first_name_last_name")))
        assertEquals("first name last name", (result3 as UDM.Scalar).value)

        // Test single word (no underscores)
        val result4 = CaseConversionFunctions.fromSnakeCase(listOf(UDM.Scalar("word")))
        assertEquals("word", (result4 as UDM.Scalar).value)

        // Test empty string
        val result5 = CaseConversionFunctions.fromSnakeCase(listOf(UDM.Scalar("")))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test with numbers
        val result6 = CaseConversionFunctions.fromSnakeCase(listOf(UDM.Scalar("test_123_abc")))
        assertEquals("test 123 abc", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testFromConstantCase() {
        // Test basic CONSTANT_CASE conversion
        val result1 = CaseConversionFunctions.fromConstantCase(listOf(UDM.Scalar("HELLO_WORLD")))
        assertEquals("hello world", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.fromConstantCase(listOf(UDM.Scalar("FIRST_NAME")))
        assertEquals("first name", (result2 as UDM.Scalar).value)

        // Test multiple words
        val result3 = CaseConversionFunctions.fromConstantCase(listOf(UDM.Scalar("FIRST_NAME_LAST_NAME")))
        assertEquals("first name last name", (result3 as UDM.Scalar).value)

        // Test single word
        val result4 = CaseConversionFunctions.fromConstantCase(listOf(UDM.Scalar("WORD")))
        assertEquals("word", (result4 as UDM.Scalar).value)

        // Test empty string
        val result5 = CaseConversionFunctions.fromConstantCase(listOf(UDM.Scalar("")))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test with numbers
        val result6 = CaseConversionFunctions.fromConstantCase(listOf(UDM.Scalar("TEST_123_ABC")))
        assertEquals("test 123 abc", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testFromTitleCase() {
        // Test basic Title Case conversion
        val result1 = CaseConversionFunctions.fromTitleCase(listOf(UDM.Scalar("Hello World")))
        assertEquals("hello world", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.fromTitleCase(listOf(UDM.Scalar("First Name")))
        assertEquals("first name", (result2 as UDM.Scalar).value)

        // Test multiple words
        val result3 = CaseConversionFunctions.fromTitleCase(listOf(UDM.Scalar("First Name Last Name")))
        assertEquals("first name last name", (result3 as UDM.Scalar).value)

        // Test single word
        val result4 = CaseConversionFunctions.fromTitleCase(listOf(UDM.Scalar("Word")))
        assertEquals("word", (result4 as UDM.Scalar).value)

        // Test empty string
        val result5 = CaseConversionFunctions.fromTitleCase(listOf(UDM.Scalar("")))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test with numbers
        val result6 = CaseConversionFunctions.fromTitleCase(listOf(UDM.Scalar("Test 123 Abc")))
        assertEquals("test 123 abc", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testFromDotCase() {
        // Test basic dot.case conversion
        val result1 = CaseConversionFunctions.fromDotCase(listOf(UDM.Scalar("hello.world")))
        assertEquals("hello world", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.fromDotCase(listOf(UDM.Scalar("first.name")))
        assertEquals("first name", (result2 as UDM.Scalar).value)

        // Test multiple words
        val result3 = CaseConversionFunctions.fromDotCase(listOf(UDM.Scalar("first.name.last.name")))
        assertEquals("first name last name", (result3 as UDM.Scalar).value)

        // Test single word (no dots)
        val result4 = CaseConversionFunctions.fromDotCase(listOf(UDM.Scalar("word")))
        assertEquals("word", (result4 as UDM.Scalar).value)

        // Test empty string
        val result5 = CaseConversionFunctions.fromDotCase(listOf(UDM.Scalar("")))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test with numbers
        val result6 = CaseConversionFunctions.fromDotCase(listOf(UDM.Scalar("test.123.abc")))
        assertEquals("test 123 abc", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testFromPathCase() {
        // Test basic path/case conversion
        val result1 = CaseConversionFunctions.fromPathCase(listOf(UDM.Scalar("hello/world")))
        assertEquals("hello world", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.fromPathCase(listOf(UDM.Scalar("first/name")))
        assertEquals("first name", (result2 as UDM.Scalar).value)

        // Test multiple words
        val result3 = CaseConversionFunctions.fromPathCase(listOf(UDM.Scalar("first/name/last/name")))
        assertEquals("first name last name", (result3 as UDM.Scalar).value)

        // Test single word (no slashes)
        val result4 = CaseConversionFunctions.fromPathCase(listOf(UDM.Scalar("word")))
        assertEquals("word", (result4 as UDM.Scalar).value)

        // Test empty string
        val result5 = CaseConversionFunctions.fromPathCase(listOf(UDM.Scalar("")))
        assertEquals("", (result5 as UDM.Scalar).value)

        // Test with numbers
        val result6 = CaseConversionFunctions.fromPathCase(listOf(UDM.Scalar("test/123/abc")))
        assertEquals("test 123 abc", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testWordCase() {
        // Test basic word case (capitalize first letter, lowercase rest)
        val result1 = CaseConversionFunctions.wordCase(listOf(UDM.Scalar("hello")))
        assertEquals("Hello", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.wordCase(listOf(UDM.Scalar("HELLO")))
        assertEquals("Hello", (result2 as UDM.Scalar).value)

        val result3 = CaseConversionFunctions.wordCase(listOf(UDM.Scalar("hElLo")))
        assertEquals("Hello", (result3 as UDM.Scalar).value)

        // Test multiple words (only first letter capitalized)
        val result4 = CaseConversionFunctions.wordCase(listOf(UDM.Scalar("hello world")))
        assertEquals("Hello world", (result4 as UDM.Scalar).value)

        // Test single character
        val result5 = CaseConversionFunctions.wordCase(listOf(UDM.Scalar("a")))
        assertEquals("A", (result5 as UDM.Scalar).value)

        // Test empty string
        val result6 = CaseConversionFunctions.wordCase(listOf(UDM.Scalar("")))
        assertEquals("", (result6 as UDM.Scalar).value)

        // Test with numbers
        val result7 = CaseConversionFunctions.wordCase(listOf(UDM.Scalar("123abc")))
        assertEquals("123abc", (result7 as UDM.Scalar).value)
    }

    @Test
    fun testNewFunctionsRoundTrip() {
        // Test round-trip conversions with new functions

        // PascalCase -> fromPascalCase -> titleCase -> fromTitleCase
        val pascalOriginal = "HelloWorld"
        val fromPascal = CaseConversionFunctions.fromPascalCase(listOf(UDM.Scalar(pascalOriginal)))
        assertEquals("hello world", (fromPascal as UDM.Scalar).value)
 

        // kebab-case -> fromKebabCase -> snakeCase -> fromSnakeCase
        val kebabOriginal = "hello-world-test"
        val fromKebab = CaseConversionFunctions.fromKebabCase(listOf(UDM.Scalar(kebabOriginal)))
        assertEquals("hello world test", (fromKebab as UDM.Scalar).value)


        // CONSTANT_CASE -> fromConstantCase -> wordCase
        val constantOriginal = "HELLO_WORLD"
        val fromConstant = CaseConversionFunctions.fromConstantCase(listOf(UDM.Scalar(constantOriginal)))
        assertEquals("hello world", (fromConstant as UDM.Scalar).value)
        val wordCased = CaseConversionFunctions.wordCase(listOf(fromConstant))
        assertEquals("Hello world", (wordCased as UDM.Scalar).value)
    }
}