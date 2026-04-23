package org.apache.utlx.engine.strategy

import org.apache.utlx.engine.config.InputSlot
import org.apache.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Strategy Parity Tests — verifies that TEMPLATE, COPY, and COMPILED
 * produce identical output for the same input. Any divergence is a bug.
 */
class StrategyParityTest {

    companion object {
        @JvmStatic
        fun testCases() = listOf(
            // name, utlx source, input JSON, description
            ParityCase(
                "identity",
                "%utlx 1.0\ninput json\noutput json\n---\n\$input\n",
                """{"name": "Alice", "age": 30}""",
                "Identity passthrough"
            ),
            ParityCase(
                "property_access",
                "%utlx 1.0\ninput json\noutput json\n---\n{name: \$input.name, city: \$input.address.city}\n",
                """{"name": "Alice", "address": {"city": "Amsterdam"}}""",
                "Nested property access"
            ),
            ParityCase(
                "stdlib_concat",
                "%utlx 1.0\ninput json\noutput json\n---\n{full: concat(\$input.first, \" \", \$input.last)}\n",
                """{"first": "Marcel", "last": "Grauwen"}""",
                "Stdlib concat function"
            ),
            ParityCase(
                "stdlib_lowerCase",
                "%utlx 1.0\ninput json\noutput json\n---\n{email: lowerCase(\$input.email)}\n",
                """{"email": "ALICE@TEST.COM"}""",
                "Stdlib lowerCase"
            ),
            ParityCase(
                "stdlib_upperCase",
                "%utlx 1.0\ninput json\noutput json\n---\n{name: upperCase(\$input.name)}\n",
                """{"name": "alice"}""",
                "Stdlib upperCase"
            ),
            ParityCase(
                "arithmetic",
                "%utlx 1.0\ninput json\noutput json\n---\n{sum: \$input.a + \$input.b, product: \$input.a * \$input.b}\n",
                """{"a": 7, "b": 3}""",
                "Arithmetic operators"
            ),
            ParityCase(
                "comparison",
                "%utlx 1.0\ninput json\noutput json\n---\n{adult: \$input.age >= 18, senior: \$input.age >= 65}\n",
                """{"age": 45}""",
                "Comparison operators"
            ),
            ParityCase(
                "boolean_logic",
                "%utlx 1.0\ninput json\noutput json\n---\n{both: \$input.a && \$input.b, either: \$input.a || \$input.b}\n",
                """{"a": true, "b": false}""",
                "Boolean AND/OR"
            ),
            ParityCase(
                "conditional",
                "%utlx 1.0\ninput json\noutput json\n---\n{grade: if (\$input.score >= 90) \"A\" else if (\$input.score >= 80) \"B\" else \"C\"}\n",
                """{"score": 85}""",
                "Chained if-else"
            ),
            ParityCase(
                "let_binding",
                "%utlx 1.0\ninput json\noutput json\n---\n{let total = \$input.price * \$input.qty, item: \$input.name, total: total}\n",
                """{"name": "Widget", "price": 10, "qty": 5}""",
                "Let binding with computed value"
            ),
            ParityCase(
                "array_literal",
                "%utlx 1.0\ninput json\noutput json\n---\n{tags: [\$input.a, \$input.b, \"fixed\"]}\n",
                """{"a": "hello", "b": "world"}""",
                "Array construction"
            ),
            ParityCase(
                "nested_object",
                "%utlx 1.0\ninput json\noutput json\n---\n{order: {id: \$input.orderId, customer: {name: \$input.name}}}\n",
                """{"orderId": "ORD-001", "name": "Contoso"}""",
                "Nested object construction"
            ),
            ParityCase(
                "null_handling",
                "%utlx 1.0\ninput json\noutput json\n---\n{value: \$input.missing ?? \"default\"}\n",
                """{"name": "Alice"}""",
                "Null coalescing"
            ),
            ParityCase(
                "unary_not",
                "%utlx 1.0\ninput json\noutput json\n---\n{inverted: !\$input.flag}\n",
                """{"flag": true}""",
                "Unary not"
            ),
            ParityCase(
                "filter_lambda",
                "%utlx 1.0\ninput json\noutput json\n---\nfilter(\$input.items, (x) -> x > 3)\n",
                """{"items": [1, 2, 3, 4, 5]}""",
                "Filter with lambda"
            ),
            ParityCase(
                "map_lambda",
                "%utlx 1.0\ninput json\noutput json\n---\nmap(\$input.items, (x) -> x * 2)\n",
                """{"items": [1, 2, 3]}""",
                "Map with lambda"
            ),
            ParityCase(
                "pipe_filter_map",
                "%utlx 1.0\ninput json\noutput json\n---\n\$input.numbers |> filter((n) -> n > 2) |> map((n) -> n * 10)\n",
                """{"numbers": [1, 2, 3, 4]}""",
                "Pipe with filter and map"
            ),
            ParityCase(
                "user_function",
                "%utlx 1.0\ninput json\noutput json\n---\n{let Double = (x) -> x * 2, result: Double(\$input.value)}\n",
                """{"value": 21}""",
                "User-defined function via let"
            ),
            ParityCase(
                "size_function",
                "%utlx 1.0\ninput json\noutput json\n---\n{count: size(\$input.items)}\n",
                """{"items": ["a", "b", "c", "d"]}""",
                "Stdlib size function"
            ),
            ParityCase(
                "json_to_xml",
                "%utlx 1.0\ninput json\noutput xml\n---\n{item: {id: \$input.id, name: \$input.name}}\n",
                """{"id": "X001", "name": "Widget"}""",
                "Format conversion JSON to XML"
            ),
        )
    }

    data class ParityCase(
        val name: String,
        val source: String,
        val input: String,
        val description: String
    ) {
        override fun toString() = name
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testCases")
    fun `all strategies produce identical output`(case: ParityCase) {
        val config = TransformConfig(inputs = listOf(InputSlot(name = "input")))

        // TEMPLATE (interpreted)
        val template = TemplateStrategy()
        template.initialize(case.source, config.copy(strategy = "TEMPLATE"))
        val templateOutput = template.execute(case.input).output

        // COMPILED (bytecode)
        val compiled = CompiledStrategy()
        compiled.initialize(case.source, config.copy(strategy = "COMPILED"))
        val compiledOutput = compiled.execute(case.input).output

        // COPY (skeleton + auto-compiled)
        val copy = CopyStrategy()
        copy.initialize(case.source, config.copy(strategy = "COPY"))
        val copyOutput = copy.execute(case.input).output

        // All three must match
        assertEquals(templateOutput, compiledOutput,
            "${case.name}: COMPILED output differs from TEMPLATE\n  TEMPLATE: $templateOutput\n  COMPILED: $compiledOutput")
        assertEquals(templateOutput, copyOutput,
            "${case.name}: COPY output differs from TEMPLATE\n  TEMPLATE: $templateOutput\n  COPY:     $copyOutput")
    }

    @Test
    fun `strategies produce identical output across multiple executions`() {
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{name: upperCase(\$input.name), done: true}\n"
        val config = TransformConfig(inputs = listOf(InputSlot(name = "input")))

        val template = TemplateStrategy().also { it.initialize(source, config.copy(strategy = "TEMPLATE")) }
        val compiled = CompiledStrategy().also { it.initialize(source, config.copy(strategy = "COMPILED")) }
        val copy = CopyStrategy().also { it.initialize(source, config.copy(strategy = "COPY")) }

        // Run 10 different inputs through each
        for (i in 1..10) {
            val input = """{"name": "user$i"}"""
            val tOut = template.execute(input).output
            val cOut = compiled.execute(input).output
            val cpOut = copy.execute(input).output

            assertEquals(tOut, cOut, "Execution $i: COMPILED differs")
            assertEquals(tOut, cpOut, "Execution $i: COPY differs")
        }
    }
}
