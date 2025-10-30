package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class CaseFunctionsTest {

    @Test
    fun testCamelize() {
        // Test basic camelization
        val result1 = CaseFunctions.camelize(listOf(UDM.Scalar("hello world")))
        assertEquals("helloWorld", (result1 as UDM.Scalar).value)

        val result2 = CaseFunctions.camelize(listOf(UDM.Scalar("hello-world")))
        assertEquals("helloWorld", (result2 as UDM.Scalar).value)

        val result3 = CaseFunctions.camelize(listOf(UDM.Scalar("hello_world")))
        assertEquals("helloWorld", (result3 as UDM.Scalar).value)

        // Test PascalCase to camelCase
        val result4 = CaseFunctions.camelize(listOf(UDM.Scalar("HelloWorld")))
        assertEquals("helloWorld", (result4 as UDM.Scalar).value)

        // Test all uppercase
        val result5 = CaseFunctions.camelize(listOf(UDM.Scalar("HELLO WORLD")))
        assertEquals("helloWorld", (result5 as UDM.Scalar).value)

        // Test multiple words
        val result6 = CaseFunctions.camelize(listOf(UDM.Scalar("first-name-last-name")))
        assertEquals("firstNameLastName", (result6 as UDM.Scalar).value)

        // Test mixed delimiters
        val result7 = CaseFunctions.camelize(listOf(UDM.Scalar("hello-world_test case")))
        assertEquals("helloWorldTestCase", (result7 as UDM.Scalar).value)

        // Test single word
        val result8 = CaseFunctions.camelize(listOf(UDM.Scalar("word")))
        assertEquals("word", (result8 as UDM.Scalar).value)

        // Test empty string
        val result9 = CaseFunctions.camelize(listOf(UDM.Scalar("")))
        assertEquals("", (result9 as UDM.Scalar).value)

        // Test already camelCase
        val result10 = CaseFunctions.camelize(listOf(UDM.Scalar("alreadyCamelCase")))
        assertEquals("alreadyCamelCase", (result10 as UDM.Scalar).value)
    }

    @Test
    fun testPascalCase() {
        // Test basic Pascal case
        val result1 = CaseFunctions.pascalCase(listOf(UDM.Scalar("hello world")))
        assertEquals("HelloWorld", (result1 as UDM.Scalar).value)

        val result2 = CaseFunctions.pascalCase(listOf(UDM.Scalar("hello-world")))
        assertEquals("HelloWorld", (result2 as UDM.Scalar).value)

        val result3 = CaseFunctions.pascalCase(listOf(UDM.Scalar("hello_world")))
        assertEquals("HelloWorld", (result3 as UDM.Scalar).value)

        // Test camelCase to PascalCase
        val result4 = CaseFunctions.pascalCase(listOf(UDM.Scalar("helloWorld")))
        assertEquals("HelloWorld", (result4 as UDM.Scalar).value)

        // Test multiple words
        val result5 = CaseFunctions.pascalCase(listOf(UDM.Scalar("first-name-last-name")))
        assertEquals("FirstNameLastName", (result5 as UDM.Scalar).value)

        // Test single word
        val result6 = CaseFunctions.pascalCase(listOf(UDM.Scalar("word")))
        assertEquals("Word", (result6 as UDM.Scalar).value)

        // Test empty string
        val result7 = CaseFunctions.pascalCase(listOf(UDM.Scalar("")))
        assertEquals("", (result7 as UDM.Scalar).value)

        // Test already PascalCase
        val result8 = CaseFunctions.pascalCase(listOf(UDM.Scalar("AlreadyPascalCase")))
        assertEquals("AlreadyPascalCase", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testKebabCase() {
        // Test basic kebab case
        val result1 = CaseFunctions.kebabCase(listOf(UDM.Scalar("hello world")))
        assertEquals("hello-world", (result1 as UDM.Scalar).value)

        val result2 = CaseFunctions.kebabCase(listOf(UDM.Scalar("helloWorld")))
        assertEquals("hello-world", (result2 as UDM.Scalar).value)

        val result3 = CaseFunctions.kebabCase(listOf(UDM.Scalar("hello_world")))
        assertEquals("hello-world", (result3 as UDM.Scalar).value)

        val result4 = CaseFunctions.kebabCase(listOf(UDM.Scalar("HelloWorld")))
        assertEquals("hello-world", (result4 as UDM.Scalar).value)

        // Test multiple words
        val result5 = CaseFunctions.kebabCase(listOf(UDM.Scalar("FirstNameLastName")))
        assertEquals("first-name-last-name", (result5 as UDM.Scalar).value)

        // Test with multiple delimiters
        val result6 = CaseFunctions.kebabCase(listOf(UDM.Scalar("hello   world__test")))
        assertEquals("hello-world-test", (result6 as UDM.Scalar).value)

        // Test single word
        val result7 = CaseFunctions.kebabCase(listOf(UDM.Scalar("word")))
        assertEquals("word", (result7 as UDM.Scalar).value)

        // Test empty string
        val result8 = CaseFunctions.kebabCase(listOf(UDM.Scalar("")))
        assertEquals("", (result8 as UDM.Scalar).value)

        // Test already kebab-case
        val result9 = CaseFunctions.kebabCase(listOf(UDM.Scalar("already-kebab-case")))
        assertEquals("already-kebab-case", (result9 as UDM.Scalar).value)
    }

    @Test
    fun testSnakeCase() {
        // Test basic snake case
        val result1 = CaseFunctions.snakeCase(listOf(UDM.Scalar("hello world")))
        assertEquals("hello_world", (result1 as UDM.Scalar).value)

        val result2 = CaseFunctions.snakeCase(listOf(UDM.Scalar("helloWorld")))
        assertEquals("hello_world", (result2 as UDM.Scalar).value)

        val result3 = CaseFunctions.snakeCase(listOf(UDM.Scalar("hello-world")))
        assertEquals("hello_world", (result3 as UDM.Scalar).value)

        val result4 = CaseFunctions.snakeCase(listOf(UDM.Scalar("HelloWorld")))
        assertEquals("hello_world", (result4 as UDM.Scalar).value)

        // Test multiple words
        val result5 = CaseFunctions.snakeCase(listOf(UDM.Scalar("FirstNameLastName")))
        assertEquals("first_name_last_name", (result5 as UDM.Scalar).value)

        // Test with multiple delimiters
        val result6 = CaseFunctions.snakeCase(listOf(UDM.Scalar("hello   world--test")))
        assertEquals("hello_world_test", (result6 as UDM.Scalar).value)

        // Test single word
        val result7 = CaseFunctions.snakeCase(listOf(UDM.Scalar("word")))
        assertEquals("word", (result7 as UDM.Scalar).value)

        // Test empty string
        val result8 = CaseFunctions.snakeCase(listOf(UDM.Scalar("")))
        assertEquals("", (result8 as UDM.Scalar).value)

        // Test already snake_case
        val result9 = CaseFunctions.snakeCase(listOf(UDM.Scalar("already_snake_case")))
        assertEquals("already_snake_case", (result9 as UDM.Scalar).value)
    }

    @Test
    fun testConstantCase() {
        // Test basic constant case
        val result1 = CaseFunctions.constantCase(listOf(UDM.Scalar("hello world")))
        assertEquals("HELLO_WORLD", (result1 as UDM.Scalar).value)

        val result2 = CaseFunctions.constantCase(listOf(UDM.Scalar("helloWorld")))
        assertEquals("HELLO_WORLD", (result2 as UDM.Scalar).value)

        val result3 = CaseFunctions.constantCase(listOf(UDM.Scalar("hello-world")))
        assertEquals("HELLO_WORLD", (result3 as UDM.Scalar).value)

        val result4 = CaseFunctions.constantCase(listOf(UDM.Scalar("HelloWorld")))
        assertEquals("HELLO_WORLD", (result4 as UDM.Scalar).value)

        // Test multiple words
        val result5 = CaseFunctions.constantCase(listOf(UDM.Scalar("FirstNameLastName")))
        assertEquals("FIRST_NAME_LAST_NAME", (result5 as UDM.Scalar).value)

        // Test single word
        val result6 = CaseFunctions.constantCase(listOf(UDM.Scalar("word")))
        assertEquals("WORD", (result6 as UDM.Scalar).value)

        // Test empty string
        val result7 = CaseFunctions.constantCase(listOf(UDM.Scalar("")))
        assertEquals("", (result7 as UDM.Scalar).value)

        // Test already CONSTANT_CASE
        val result8 = CaseFunctions.constantCase(listOf(UDM.Scalar("ALREADY_CONSTANT_CASE")))
        assertEquals("ALREADY_CONSTANT_CASE", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testTitleCase() {
        // Test basic title case
        val result1 = CaseFunctions.titleCase(listOf(UDM.Scalar("hello world")))
        assertEquals("Hello World", (result1 as UDM.Scalar).value)

        val result2 = CaseFunctions.titleCase(listOf(UDM.Scalar("HELLO WORLD")))
        assertEquals("Hello World", (result2 as UDM.Scalar).value)

        val result3 = CaseFunctions.titleCase(listOf(UDM.Scalar("hello-world")))
        assertEquals("Hello-world", (result3 as UDM.Scalar).value)

        // Test multiple words with various spacing
        val result4 = CaseFunctions.titleCase(listOf(UDM.Scalar("the quick brown fox")))
        assertEquals("The Quick Brown Fox", (result4 as UDM.Scalar).value)

        // Test mixed case
        val result5 = CaseFunctions.titleCase(listOf(UDM.Scalar("hELLo WoRLd")))
        assertEquals("Hello World", (result5 as UDM.Scalar).value)

        // Test single word
        val result6 = CaseFunctions.titleCase(listOf(UDM.Scalar("word")))
        assertEquals("Word", (result6 as UDM.Scalar).value)

        // Test empty string
        val result7 = CaseFunctions.titleCase(listOf(UDM.Scalar("")))
        assertEquals("", (result7 as UDM.Scalar).value)

        // Test with extra spaces
        val result8 = CaseFunctions.titleCase(listOf(UDM.Scalar("hello  world")))
        assertEquals("Hello  World", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testDotCase() {
        // Test basic dot case
        val result1 = CaseFunctions.dotCase(listOf(UDM.Scalar("hello world")))
        assertEquals("hello.world", (result1 as UDM.Scalar).value)

        val result2 = CaseFunctions.dotCase(listOf(UDM.Scalar("helloWorld")))
        assertEquals("hello.world", (result2 as UDM.Scalar).value)

        val result3 = CaseFunctions.dotCase(listOf(UDM.Scalar("hello_world")))
        assertEquals("hello.world", (result3 as UDM.Scalar).value)

        val result4 = CaseFunctions.dotCase(listOf(UDM.Scalar("hello-world")))
        assertEquals("hello.world", (result4 as UDM.Scalar).value)

        // Test multiple words
        val result5 = CaseFunctions.dotCase(listOf(UDM.Scalar("FirstNameLastName")))
        assertEquals("first.name.last.name", (result5 as UDM.Scalar).value)

        // Test with multiple delimiters
        val result6 = CaseFunctions.dotCase(listOf(UDM.Scalar("hello   world__test")))
        assertEquals("hello.world.test", (result6 as UDM.Scalar).value)

        // Test single word
        val result7 = CaseFunctions.dotCase(listOf(UDM.Scalar("word")))
        assertEquals("word", (result7 as UDM.Scalar).value)

        // Test empty string
        val result8 = CaseFunctions.dotCase(listOf(UDM.Scalar("")))
        assertEquals("", (result8 as UDM.Scalar).value)

        // Test already dot.case
        val result9 = CaseFunctions.dotCase(listOf(UDM.Scalar("already.dot.case")))
        assertEquals("already.dot.case", (result9 as UDM.Scalar).value)
    }

    @Test
    fun testPathCase() {
        // Test basic path case
        val result1 = CaseFunctions.pathCase(listOf(UDM.Scalar("hello world")))
        assertEquals("hello/world", (result1 as UDM.Scalar).value)

        val result2 = CaseFunctions.pathCase(listOf(UDM.Scalar("helloWorld")))
        assertEquals("hello/world", (result2 as UDM.Scalar).value)

        val result3 = CaseFunctions.pathCase(listOf(UDM.Scalar("hello_world")))
        assertEquals("hello/world", (result3 as UDM.Scalar).value)

        val result4 = CaseFunctions.pathCase(listOf(UDM.Scalar("hello-world")))
        assertEquals("hello/world", (result4 as UDM.Scalar).value)

        // Test multiple words
        val result5 = CaseFunctions.pathCase(listOf(UDM.Scalar("FirstNameLastName")))
        assertEquals("first/name/last/name", (result5 as UDM.Scalar).value)

        // Test with multiple delimiters
        val result6 = CaseFunctions.pathCase(listOf(UDM.Scalar("hello   world__test")))
        assertEquals("hello/world/test", (result6 as UDM.Scalar).value)

        // Test single word
        val result7 = CaseFunctions.pathCase(listOf(UDM.Scalar("word")))
        assertEquals("word", (result7 as UDM.Scalar).value)

        // Test empty string
        val result8 = CaseFunctions.pathCase(listOf(UDM.Scalar("")))
        assertEquals("", (result8 as UDM.Scalar).value)

        // Test already path/case
        val result9 = CaseFunctions.pathCase(listOf(UDM.Scalar("already/path/case")))
        assertEquals("already/path/case", (result9 as UDM.Scalar).value)
    }

    @Test
    fun testArgumentValidation() {
        // Test missing arguments
        assertThrows<FunctionArgumentException> {
            CaseFunctions.camelize(emptyList())
        }

        assertThrows<FunctionArgumentException> {
            CaseFunctions.pascalCase(emptyList())
        }

        assertThrows<FunctionArgumentException> {
            CaseFunctions.kebabCase(emptyList())
        }

        assertThrows<FunctionArgumentException> {
            CaseFunctions.snakeCase(emptyList())
        }

        assertThrows<FunctionArgumentException> {
            CaseFunctions.constantCase(emptyList())
        }

        assertThrows<FunctionArgumentException> {
            CaseFunctions.titleCase(emptyList())
        }

        assertThrows<FunctionArgumentException> {
            CaseFunctions.dotCase(emptyList())
        }

        assertThrows<FunctionArgumentException> {
            CaseFunctions.pathCase(emptyList())
        }
    }

    @Test
    fun testInvalidArgumentTypes() {
        // Test non-string arguments
        assertThrows<FunctionArgumentException> {
            CaseFunctions.camelize(listOf(UDM.Scalar(123)))
        }

        assertThrows<FunctionArgumentException> {
            CaseFunctions.pascalCase(listOf(UDM.Array(emptyList())))
        }

        assertThrows<FunctionArgumentException> {
            CaseFunctions.kebabCase(listOf(UDM.Object(mutableMapOf())))
        }

        assertThrows<FunctionArgumentException> {
            CaseFunctions.snakeCase(listOf(UDM.Scalar(true)))
        }

        assertThrows<FunctionArgumentException> {
            CaseFunctions.constantCase(listOf(UDM.Scalar(null)))
        }

        assertThrows<FunctionArgumentException> {
            CaseFunctions.titleCase(listOf(UDM.Scalar(3.14)))
        }
    }

    @Test
    fun testComplexCaseConversions() {
        // Test complex inputs with mixed formats
        val complexInput = "XMLHttpRequestFactory"

        val camelized = CaseFunctions.camelize(listOf(UDM.Scalar(complexInput)))
        assertEquals("xmlhttpRequestFactory", (camelized as UDM.Scalar).value)  // Consecutive capitals become lowercase

        val snaked = CaseFunctions.snakeCase(listOf(UDM.Scalar(complexInput)))
        assertEquals("xmlhttp_request_factory", (snaked as UDM.Scalar).value)  // Consecutive capitals treated as one word

        val kebabbed = CaseFunctions.kebabCase(listOf(UDM.Scalar(complexInput)))
        assertEquals("xmlhttp-request-factory", (kebabbed as UDM.Scalar).value)  // Consecutive capitals treated as one word

        val constant = CaseFunctions.constantCase(listOf(UDM.Scalar(complexInput)))
        assertEquals("XMLHTTP_REQUEST_FACTORY", (constant as UDM.Scalar).value)  // Consecutive capitals treated as one word

        // Test with numbers
        val numberInput = "html5Parser"
        val numberSnaked = CaseFunctions.snakeCase(listOf(UDM.Scalar(numberInput)))
        assertEquals("html5parser", (numberSnaked as UDM.Scalar).value)  // Numbers don't create word boundaries
    }

    @Test
    fun testRoundTripConversions() {
        // Test converting between different case formats
        val original = "hello world test"

        // Convert to various formats
        val camelized = CaseFunctions.camelize(listOf(UDM.Scalar(original)))
        val pascalized = CaseFunctions.pascalCase(listOf(UDM.Scalar(original)))
        val kebabbed = CaseFunctions.kebabCase(listOf(UDM.Scalar(original)))
        val snaked = CaseFunctions.snakeCase(listOf(UDM.Scalar(original)))
        val dotted = CaseFunctions.dotCase(listOf(UDM.Scalar(original)))
        val pathed = CaseFunctions.pathCase(listOf(UDM.Scalar(original)))

        assertEquals("helloWorldTest", (camelized as UDM.Scalar).value)
        assertEquals("HelloWorldTest", (pascalized as UDM.Scalar).value)
        assertEquals("hello-world-test", (kebabbed as UDM.Scalar).value)
        assertEquals("hello_world_test", (snaked as UDM.Scalar).value)
        assertEquals("hello.world.test", (dotted as UDM.Scalar).value)
        assertEquals("hello/world/test", (pathed as UDM.Scalar).value)

        // Test converting camelCase back to other formats
        val backToKebab = CaseFunctions.kebabCase(listOf(camelized))
        assertEquals("hello-world-test", (backToKebab as UDM.Scalar).value)

        val backToSnake = CaseFunctions.snakeCase(listOf(camelized))
        assertEquals("hello_world_test", (backToSnake as UDM.Scalar).value)
    }

    @Test
    fun testEdgeCases() {
        // Test with special characters
        val special = "hello@world#test"
        val specialCamel = CaseFunctions.camelize(listOf(UDM.Scalar(special)))
        assertEquals("hello@world#test", (specialCamel as UDM.Scalar).value)

        // Test with numbers
        val withNumbers = "test123Example456"
        val numbersCamel = CaseFunctions.camelize(listOf(UDM.Scalar(withNumbers)))
        assertEquals("test123example456", (numbersCamel as UDM.Scalar).value)  // Capital after number becomes lowercase

        // Test with only delimiters
        val onlyDelimiters = "---___   "
        val delimitersCamel = CaseFunctions.camelize(listOf(UDM.Scalar(onlyDelimiters)))
        assertEquals("", (delimitersCamel as UDM.Scalar).value)

        // Test single characters
        val singleChar = "a"
        val singleCamel = CaseFunctions.camelize(listOf(UDM.Scalar(singleChar)))
        assertEquals("a", (singleCamel as UDM.Scalar).value)

        val singlePascal = CaseFunctions.pascalCase(listOf(UDM.Scalar(singleChar)))
        assertEquals("A", (singlePascal as UDM.Scalar).value)

        // Test consecutive uppercase letters
        val consecutiveUpper = "XMLParser"
        val consecutiveCamel = CaseFunctions.camelize(listOf(UDM.Scalar(consecutiveUpper)))
        assertEquals("xmlparser", (consecutiveCamel as UDM.Scalar).value)  // Consecutive capitals treated as one word

        val consecutiveSnake = CaseFunctions.snakeCase(listOf(UDM.Scalar(consecutiveUpper)))
        assertEquals("xmlparser", (consecutiveSnake as UDM.Scalar).value)  // Consecutive capitals treated as one word
    }

    @Test
    fun testUnicodeSupport() {
        // Test with Unicode characters
        val unicode = "café naïve"
        
        val unicodeCamel = CaseFunctions.camelize(listOf(UDM.Scalar(unicode)))
        assertEquals("caféNaïve", (unicodeCamel as UDM.Scalar).value)

        val unicodeKebab = CaseFunctions.kebabCase(listOf(UDM.Scalar(unicode)))
        assertEquals("café-naïve", (unicodeKebab as UDM.Scalar).value)

        val unicodeSnake = CaseFunctions.snakeCase(listOf(UDM.Scalar(unicode)))
        assertEquals("café_naïve", (unicodeSnake as UDM.Scalar).value)

        val unicodeTitle = CaseFunctions.titleCase(listOf(UDM.Scalar(unicode)))
        assertEquals("Café Naïve", (unicodeTitle as UDM.Scalar).value)
    }

    @Test
    fun testCommonUseCases() {
        // Test common programming scenarios
        
        // Database column names
        val dbColumn = "user_id"
        val apiField = CaseFunctions.camelize(listOf(UDM.Scalar(dbColumn)))
        assertEquals("userId", (apiField as UDM.Scalar).value)

        // CSS class names
        val cssClass = "btn-primary"
        val jsVariable = CaseFunctions.camelize(listOf(UDM.Scalar(cssClass)))
        assertEquals("btnPrimary", (jsVariable as UDM.Scalar).value)

        // Environment variables
        val envVar = "API_BASE_URL"
        val configKey = CaseFunctions.camelize(listOf(UDM.Scalar(envVar)))
        assertEquals("apiBaseUrl", (configKey as UDM.Scalar).value)

        // Class names to constants
        val className = "HttpStatusCode"
        val constantName = CaseFunctions.constantCase(listOf(UDM.Scalar(className)))
        assertEquals("HTTP_STATUS_CODE", (constantName as UDM.Scalar).value)

        // File paths
        val fileName = "UserProfile"
        val filePath = CaseFunctions.pathCase(listOf(UDM.Scalar(fileName)))
        assertEquals("user/profile", (filePath as UDM.Scalar).value)
    }
}