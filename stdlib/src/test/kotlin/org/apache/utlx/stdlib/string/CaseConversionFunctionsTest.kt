package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class CaseConversionFunctionsTest {

    @Test
    fun testCamelize() {
        // Test basic camelization
        val result1 = CaseConversionFunctions.camelize(listOf(UDM.Scalar("hello-world")))
        assertEquals("helloWorld", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.camelize(listOf(UDM.Scalar("hello_world")))
        assertEquals("helloWorld", (result2 as UDM.Scalar).value)

        val result3 = CaseConversionFunctions.camelize(listOf(UDM.Scalar("hello world")))
        assertEquals("helloWorld", (result3 as UDM.Scalar).value)

        // Test multiple words
        val result4 = CaseConversionFunctions.camelize(listOf(UDM.Scalar("first-name-last-name")))
        assertEquals("firstNameLastName", (result4 as UDM.Scalar).value)

        // Test already camelCase
        val result5 = CaseConversionFunctions.camelize(listOf(UDM.Scalar("alreadyCamelCase")))
        assertEquals("alreadycamelcase", (result5 as UDM.Scalar).value)

        // Test single word
        val result6 = CaseConversionFunctions.camelize(listOf(UDM.Scalar("word")))
        assertEquals("word", (result6 as UDM.Scalar).value)

        // Test empty string
        val result7 = CaseConversionFunctions.camelize(listOf(UDM.Scalar("")))
        assertEquals("", (result7 as UDM.Scalar).value)

        // Test mixed delimiters
        val result8 = CaseConversionFunctions.camelize(listOf(UDM.Scalar("hello-world_test case")))
        assertEquals("helloWorldTestCase", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testSnakeCase() {
        // Test basic snake case conversion
        val result1 = CaseConversionFunctions.snakeCase(listOf(UDM.Scalar("helloWorld")))
        assertEquals("hello_world", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.snakeCase(listOf(UDM.Scalar("HelloWorld")))
        assertEquals("hello_world", (result2 as UDM.Scalar).value)

        val result3 = CaseConversionFunctions.snakeCase(listOf(UDM.Scalar("hello-world")))
        assertEquals("hello_world", (result3 as UDM.Scalar).value)

        // Test multiple words
        val result4 = CaseConversionFunctions.snakeCase(listOf(UDM.Scalar("FirstNameLastName")))
        assertEquals("first_name_last_name", (result4 as UDM.Scalar).value)

        // Test with spaces
        val result5 = CaseConversionFunctions.snakeCase(listOf(UDM.Scalar("hello world test")))
        assertEquals("hello_world_test", (result5 as UDM.Scalar).value)

        // Test already snake_case
        val result6 = CaseConversionFunctions.snakeCase(listOf(UDM.Scalar("already_snake_case")))
        assertEquals("already_snake_case", (result6 as UDM.Scalar).value)

        // Test single word
        val result7 = CaseConversionFunctions.snakeCase(listOf(UDM.Scalar("word")))
        assertEquals("word", (result7 as UDM.Scalar).value)

        // Test empty string
        val result8 = CaseConversionFunctions.snakeCase(listOf(UDM.Scalar("")))
        assertEquals("", (result8 as UDM.Scalar).value)

        // Test mixed delimiters
        val result9 = CaseConversionFunctions.snakeCase(listOf(UDM.Scalar("hello-world test_case")))
        assertEquals("hello_world_test_case", (result9 as UDM.Scalar).value)
    }

    @Test
    fun testTitleCase() {
        // Test basic title case
        val result1 = CaseConversionFunctions.titleCase(listOf(UDM.Scalar("hello world")))
        assertEquals("Hello World", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.titleCase(listOf(UDM.Scalar("hello-world")))
        assertEquals("Hello World", (result2 as UDM.Scalar).value)

        val result3 = CaseConversionFunctions.titleCase(listOf(UDM.Scalar("HELLO WORLD")))
        assertEquals("Hello World", (result3 as UDM.Scalar).value)

        // Test with underscores
        val result4 = CaseConversionFunctions.titleCase(listOf(UDM.Scalar("hello_world_test")))
        assertEquals("Hello World Test", (result4 as UDM.Scalar).value)

        // Test mixed case input
        val result5 = CaseConversionFunctions.titleCase(listOf(UDM.Scalar("hElLo WoRlD")))
        assertEquals("Hello World", (result5 as UDM.Scalar).value)

        // Test single word
        val result6 = CaseConversionFunctions.titleCase(listOf(UDM.Scalar("word")))
        assertEquals("Word", (result6 as UDM.Scalar).value)

        // Test empty string
        val result7 = CaseConversionFunctions.titleCase(listOf(UDM.Scalar("")))
        assertEquals("", (result7 as UDM.Scalar).value)

        // Test camelCase input
        val result8 = CaseConversionFunctions.titleCase(listOf(UDM.Scalar("helloWorldTest")))
        assertEquals("Helloworldtest", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testUncamelize() {
        // Test basic uncamelization
        val result1 = CaseConversionFunctions.uncamelize(listOf(UDM.Scalar("helloWorld")))
        assertEquals("hello world", (result1 as UDM.Scalar).value)

        val result2 = CaseConversionFunctions.uncamelize(listOf(UDM.Scalar("firstName")))
        assertEquals("first name", (result2 as UDM.Scalar).value)

        // Test multiple capital letters
        val result3 = CaseConversionFunctions.uncamelize(listOf(UDM.Scalar("firstNameLastName")))
        assertEquals("first name last name", (result3 as UDM.Scalar).value)

        // Test PascalCase
        val result4 = CaseConversionFunctions.uncamelize(listOf(UDM.Scalar("HelloWorld")))
        assertEquals("hello world", (result4 as UDM.Scalar).value)

        // Test single word lowercase
        val result5 = CaseConversionFunctions.uncamelize(listOf(UDM.Scalar("word")))
        assertEquals("word", (result5 as UDM.Scalar).value)

        // Test single word uppercase
        val result6 = CaseConversionFunctions.uncamelize(listOf(UDM.Scalar("Word")))
        assertEquals("word", (result6 as UDM.Scalar).value)

        // Test empty string
        val result7 = CaseConversionFunctions.uncamelize(listOf(UDM.Scalar("")))
        assertEquals("", (result7 as UDM.Scalar).value)

        // Test consecutive capitals
        val result8 = CaseConversionFunctions.uncamelize(listOf(UDM.Scalar("XMLParser")))
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
            CaseConversionFunctions.camelize(emptyList())
        }

        assertThrows<IllegalArgumentException> {
            CaseConversionFunctions.camelize(listOf(UDM.Scalar("test"), UDM.Scalar("extra")))
        }

        assertThrows<IllegalArgumentException> {
            CaseConversionFunctions.truncate(listOf(UDM.Scalar("test")))
        }

        assertThrows<IllegalArgumentException> {
            CaseConversionFunctions.truncate(listOf(UDM.Scalar("test"), UDM.Scalar(5), UDM.Scalar("..."), UDM.Scalar("extra")))
        }

        // Test wrong argument types
        assertThrows<IllegalArgumentException> {
            CaseConversionFunctions.camelize(listOf(UDM.Scalar(123)))
        }

        assertThrows<IllegalArgumentException> {
            CaseConversionFunctions.snakeCase(listOf(UDM.Array(emptyList())))
        }

        assertThrows<IllegalArgumentException> {
            CaseConversionFunctions.truncate(listOf(UDM.Scalar("test"), UDM.Scalar("not a number")))
        }

        assertThrows<IllegalArgumentException> {
            CaseConversionFunctions.truncate(listOf(UDM.Scalar("test"), UDM.Scalar(5), UDM.Scalar(123)))
        }
    }

    @Test
    fun testEdgeCases() {
        // Test very long strings
        val longString = "a".repeat(1000)
        val result1 = CaseConversionFunctions.camelize(listOf(UDM.Scalar(longString)))
        assertEquals(longString, (result1 as UDM.Scalar).value)

        // Test unicode characters
        val result2 = CaseConversionFunctions.slugify(listOf(UDM.Scalar("Café & Naïve")))
        assertEquals("caf-and-nave", (result2 as UDM.Scalar).value)

        // Test numbers in strings
        val result3 = CaseConversionFunctions.camelize(listOf(UDM.Scalar("test-123-abc")))
        assertEquals("test123Abc", (result3 as UDM.Scalar).value)

        val result4 = CaseConversionFunctions.snakeCase(listOf(UDM.Scalar("test123Abc")))
        assertEquals("test123abc", (result4 as UDM.Scalar).value)

        // Test all delimiters in one string
        val result5 = CaseConversionFunctions.camelize(listOf(UDM.Scalar("hello world-test_case")))
        assertEquals("helloWorldTestCase", (result5 as UDM.Scalar).value)
    }

    @Test
    fun testRoundTripConversions() {
        // Test camelCase -> snake_case -> back (camelize correctly reconstructs camelCase)
        val original = "helloWorldTest"
        val snaked = CaseConversionFunctions.snakeCase(listOf(UDM.Scalar(original)))
        val camelized = CaseConversionFunctions.camelize(listOf(snaked))
        assertEquals("helloWorldTest", (camelized as UDM.Scalar).value) // Camelize capitalizes after underscores

        // Test camelCase -> uncamelize -> titleCase
        val original2 = "helloWorldTest"
        val uncamelized = CaseConversionFunctions.uncamelize(listOf(UDM.Scalar(original2)))
        val titled = CaseConversionFunctions.titleCase(listOf(uncamelized))
        assertEquals("Hello World Test", (titled as UDM.Scalar).value)
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
}