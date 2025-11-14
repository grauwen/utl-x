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

        // Test camelCase -> fromCamelCase -> titleCase
        val original2 = "helloWorldTest"
        val fromCamelCased = CaseConversionFunctions.fromCamelCase(listOf(UDM.Scalar(original2)))
        val titled = CaseConversionFunctions.titleCase(listOf(fromCamelCased))
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
        val toTitle = CaseConversionFunctions.titleCase(listOf(fromPascal))
        assertEquals("Hello World", (toTitle as UDM.Scalar).value)

        // kebab-case -> fromKebabCase -> snakeCase -> fromSnakeCase
        val kebabOriginal = "hello-world-test"
        val fromKebab = CaseConversionFunctions.fromKebabCase(listOf(UDM.Scalar(kebabOriginal)))
        assertEquals("hello world test", (fromKebab as UDM.Scalar).value)
        val toSnake = CaseConversionFunctions.snakeCase(listOf(fromKebab))
        assertEquals("hello_world_test", (toSnake as UDM.Scalar).value)
        val fromSnake = CaseConversionFunctions.fromSnakeCase(listOf(toSnake))
        assertEquals("hello world test", (fromSnake as UDM.Scalar).value)

        // CONSTANT_CASE -> fromConstantCase -> wordCase
        val constantOriginal = "HELLO_WORLD"
        val fromConstant = CaseConversionFunctions.fromConstantCase(listOf(UDM.Scalar(constantOriginal)))
        assertEquals("hello world", (fromConstant as UDM.Scalar).value)
        val wordCased = CaseConversionFunctions.wordCase(listOf(fromConstant))
        assertEquals("Hello world", (wordCased as UDM.Scalar).value)
    }
}