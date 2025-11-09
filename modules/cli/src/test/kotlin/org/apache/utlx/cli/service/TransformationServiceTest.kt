// modules/cli/src/test/kotlin/org/apache/utlx/cli/service/TransformationServiceTest.kt
package org.apache.utlx.cli.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TransformationServiceTest {

    private val service = TransformationService()

    @Test
    fun `test simple JSON to JSON transformation`() {
        val utlx = """
            %utlx 1.0
            input json
            output json
            ---
            {
              greeting: "Hello, " + ${'$'}input.name,
              age: ${'$'}input.age
            }
        """.trimIndent()

        val input = """{"name": "Alice", "age": 25}"""

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "json"
            )
        )

        val (output, format) = service.transform(utlx, inputs)

        assertEquals("json", format)
        assertTrue(output.contains("\"greeting\""))
        assertTrue(output.contains("Hello, Alice"))
        assertTrue(output.contains("\"age\""))
        assertTrue(output.contains("25"))
    }

    @Test
    fun `test JSON to XML transformation`() {
        val utlx = """
            %utlx 1.0
            input json
            output xml
            ---
            {
              person: {
                name: ${'$'}input.name,
                age: ${'$'}input.age
              }
            }
        """.trimIndent()

        val input = """{"name": "Bob", "age": 30}"""

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "json"
            )
        )

        val (output, format) = service.transform(utlx, inputs)

        assertEquals("xml", format)
        assertTrue(output.contains("<person>"))
        assertTrue(output.contains("<name>Bob</name>"))
        assertTrue(output.contains("<age>30</age>"))
        assertTrue(output.contains("</person>"))
    }

    @Test
    fun `test XML to JSON transformation`() {
        val utlx = """
            %utlx 1.0
            input xml
            output json
            ---
            {
              fullName: ${'$'}input.person.firstName + " " + ${'$'}input.person.lastName,
              country: ${'$'}input.person.address.country
            }
        """.trimIndent()

        val input = """
            <person>
                <firstName>John</firstName>
                <lastName>Doe</lastName>
                <address>
                    <country>USA</country>
                </address>
            </person>
        """.trimIndent()

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "xml"
            )
        )

        val (output, format) = service.transform(utlx, inputs)

        assertEquals("json", format)
        assertTrue(output.contains("\"fullName\""))
        assertTrue(output.contains("John Doe"))
        assertTrue(output.contains("\"country\""))
        assertTrue(output.contains("USA"))
    }

    @Test
    fun `test CSV to JSON transformation`() {
        val utlx = """
            %utlx 1.0
            input csv
            output json
            ---
            {
              people: ${'$'}input,
              count: count(${'$'}input)
            }
        """.trimIndent()

        val input = """
            name,age,city
            Alice,25,NYC
            Bob,30,LA
        """.trimIndent()

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "csv"
            )
        )

        val (output, format) = service.transform(utlx, inputs)

        assertEquals("json", format)
        assertTrue(output.contains("\"people\""))
        assertTrue(output.contains("\"count\""))
        assertTrue(output.contains("2"))
        assertTrue(output.contains("Alice"))
        assertTrue(output.contains("Bob"))
    }

    @Test
    fun `test override output format`() {
        val utlx = """
            %utlx 1.0
            input json
            output json
            ---
            {
              message: "test"
            }
        """.trimIndent()

        val input = """{"test": true}"""

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "json"
            )
        )

        val options = TransformationService.TransformOptions(
            overrideOutputFormat = "xml"
        )

        val (output, format) = service.transform(utlx, inputs, options)

        assertEquals("xml", format)
        assertTrue(output.contains("<message>"))
        assertTrue(output.contains("test"))
    }

    @Test
    fun `test input format auto-detection from header`() {
        val utlx = """
            %utlx 1.0
            input json
            output json
            ---
            {
              value: ${'$'}input.test
            }
        """.trimIndent()

        val input = """{"test": "hello"}"""

        // Don't specify format - should use header
        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = null
            )
        )

        val (output, format) = service.transform(utlx, inputs)

        assertEquals("json", format)
        assertTrue(output.contains("hello"))
    }

    @Test
    fun `test arithmetic operations`() {
        val utlx = """
            %utlx 1.0
            input json
            output json
            ---
            {
              sum: ${'$'}input.a + ${'$'}input.b,
              product: ${'$'}input.a * ${'$'}input.b,
              difference: ${'$'}input.a - ${'$'}input.b
            }
        """.trimIndent()

        val input = """{"a": 10, "b": 5}"""

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "json"
            )
        )

        val (output, format) = service.transform(utlx, inputs)

        assertEquals("json", format)
        assertTrue(output.contains("\"sum\"") && output.contains("15"))
        assertTrue(output.contains("\"product\"") && output.contains("50"))
        assertTrue(output.contains("\"difference\"") && output.contains("5"))
    }

    @Test
    fun `test array transformation with map`() {
        val utlx = """
            %utlx 1.0
            input json
            output json
            ---
            {
              doubled: map(${'$'}input.numbers, x => x * 2)
            }
        """.trimIndent()

        val input = """{"numbers": [1, 2, 3, 4, 5]}"""

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "json"
            )
        )

        val (output, format) = service.transform(utlx, inputs)

        assertEquals("json", format)
        assertTrue(output.contains("\"doubled\""))
        assertTrue(output.contains("2"))
        assertTrue(output.contains("4"))
        assertTrue(output.contains("6"))
        assertTrue(output.contains("8"))
        assertTrue(output.contains("10"))
    }

    @Test
    fun `test conditional logic`() {
        val utlx = """
            %utlx 1.0
            input json
            output json
            ---
            {
              status: if (${'$'}input.age >= 18) "adult" else "minor",
              canVote: ${'$'}input.age >= 18
            }
        """.trimIndent()

        val input = """{"age": 20}"""

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "json"
            )
        )

        val (output, format) = service.transform(utlx, inputs)

        assertEquals("json", format)
        assertTrue(output.contains("\"status\""))
        assertTrue(output.contains("adult"))
        assertTrue(output.contains("\"canVote\""))
        assertTrue(output.contains("true"))
    }

    @Test
    fun `test parse error handling`() {
        val utlx = """
            %utlx 1.0
            input json
            output json
            ---
            {
              invalid syntax here
            }
        """.trimIndent()

        val input = """{"test": true}"""

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "json"
            )
        )

        assertThrows(IllegalStateException::class.java) {
            service.transform(utlx, inputs)
        }
    }

    @Test
    fun `test verbose mode`() {
        val utlx = """
            %utlx 1.0
            input json
            output json
            ---
            { result: "test" }
        """.trimIndent()

        val input = """{"test": true}"""

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "json"
            )
        )

        val options = TransformationService.TransformOptions(verbose = true)

        // Should not throw, just print verbose output
        val (output, format) = service.transform(utlx, inputs, options)

        assertEquals("json", format)
        assertTrue(output.contains("test"))
    }

    @Test
    fun `test pretty printing disabled`() {
        val utlx = """
            %utlx 1.0
            input json
            output json
            ---
            {
              a: 1,
              b: 2,
              c: 3
            }
        """.trimIndent()

        val input = """{"test": true}"""

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "json"
            )
        )

        val options = TransformationService.TransformOptions(pretty = false)

        val (output, format) = service.transform(utlx, inputs, options)

        assertEquals("json", format)
        // Non-pretty JSON should be more compact
        assertFalse(output.contains("\n  "))
    }

    @Test
    fun `test YAML to JSON transformation`() {
        val utlx = """
            %utlx 1.0
            input yaml
            output json
            ---
            {
              title: ${'$'}input.title,
              count: count(${'$'}input.items)
            }
        """.trimIndent()

        val input = """
            title: My Document
            items:
              - one
              - two
              - three
        """.trimIndent()

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "yaml"
            )
        )

        val (output, format) = service.transform(utlx, inputs)

        assertEquals("json", format)
        assertTrue(output.contains("\"title\""))
        assertTrue(output.contains("My Document"))
        assertTrue(output.contains("\"count\""))
        assertTrue(output.contains("3"))
    }

    @Test
    fun `test string operations`() {
        val utlx = """
            %utlx 1.0
            input json
            output json
            ---
            {
              upper: upper(${'$'}input.text),
              lower: lower(${'$'}input.text),
              length: length(${'$'}input.text)
            }
        """.trimIndent()

        val input = """{"text": "Hello World"}"""

        val inputs = mapOf(
            "input" to TransformationService.InputData(
                content = input,
                format = "json"
            )
        )

        val (output, format) = service.transform(utlx, inputs)

        assertEquals("json", format)
        assertTrue(output.contains("HELLO WORLD"))
        assertTrue(output.contains("hello world"))
        assertTrue(output.contains("11"))
    }
}
