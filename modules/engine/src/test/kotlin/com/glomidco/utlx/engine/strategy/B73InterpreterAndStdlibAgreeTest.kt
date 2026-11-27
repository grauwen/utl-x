package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.engine.config.InputSlot
import com.glomidco.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * B73: seven language functions have **two** implementations, and nothing compared them.
 *
 * `modules/core` cannot depend on `:stdlib` — the dependency runs the other way — so the
 * interpreter owns its own `map`, `filter`, `find`, `findIndex`, `reduce`, `first` and `last` in
 * order to evaluate a script standalone. `StandardLibraryIntegrationTest` pins that on purpose.
 * The compiled path has no such copies: `StdlibDispatch` resolves the name through
 * `StandardLibrary`, so it runs the stdlib implementation instead.
 *
 * Two implementations of one language function is therefore **by design here**, which is exactly
 * why it needs a control. They had drifted: `findIndex` with no match returned `-1` from the
 * interpreter and `null` from stdlib, so one script gave two answers depending on which strategy
 * ran it — the CLI (TEMPLATE) disagreeing with the engine (COMPILED, what ships). A developer
 * could verify a transformation at the prompt and have it behave differently in production.
 *
 * `ConformanceStrategyParityTest` is the right shape of control but could not catch this: it
 * auto-discovers the corpus, and no fixture called `findIndex` with no match. 421 parity tests,
 * and that path was never travelled.
 *
 * So this test runs the cases that distinguish the two implementations through TEMPLATE and
 * COMPILED and requires identical output. It is not about the *value* chosen — null rather than
 * -1 — but about the two implementations never disagreeing again, whatever the value is.
 */
class B73InterpreterAndStdlibAgreeTest {

    private fun run(expr: String, input: String): Pair<String, String> {
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{ r: $expr }\n"
        val config = TransformConfig(inputs = listOf(InputSlot(name = "input")))

        val template = TemplateStrategy()
        template.initialize(source, config.copy(strategy = "TEMPLATE"))

        val compiled = CompiledStrategy()
        compiled.initialize(source, config.copy(strategy = "COMPILED"))

        return template.execute(input).output to compiled.execute(input).output
    }

    /** The cases where the two implementations could differ: empty input, and no match. */
    private val cases = listOf(
        "findIndex(\$input.a, x => x > 100)" to """{"a": [1, 2, 3]}""",
        "findIndex(\$input.a, x => x > 1)" to """{"a": [1, 2, 3]}""",
        "findIndex(\$input.a, x => true)" to """{"a": []}""",
        "findLastIndex(\$input.a, x => x > 100)" to """{"a": [1, 2, 3]}""",
        "find(\$input.a, x => x > 100)" to """{"a": [1, 2, 3]}""",
        "map(\$input.a, x => x * 2)" to """{"a": [1, 2, 3]}""",
        "filter(\$input.a, x => x > 1)" to """{"a": [1, 2, 3]}""",
        "reduce(\$input.a, (acc, x) => acc + x, 0)" to """{"a": [1, 2, 3]}""",
        "first(\$input.a)" to """{"a": [1, 2, 3]}""",
        "last(\$input.a)" to """{"a": [1, 2, 3]}""",
    )

    @Test
    fun `the interpreter's own functions agree with stdlib's on every distinguishing case`() {
        val disagreements = cases.mapNotNull { (expr, input) ->
            val (template, compiled) = run(expr, input)
            if (template.filterNot(Char::isWhitespace) == compiled.filterNot(Char::isWhitespace)) null
            else "$expr  on  $input\n    TEMPLATE: ${template.trim()}\n    COMPILED: ${compiled.trim()}"
        }
        assertEquals(emptyList(), disagreements,
            "the interpreter's implementation and stdlib's disagree, so the same script gives " +
            "different answers on the CLI and in the engine:\n" + disagreements.joinToString("\n"))
    }

    @Test
    fun `not-found is null, consistently, and the old value stays reachable`() {
        // The decision B73 settled: null rather than -1, matching indexOf / lastIndexOf / find.
        // Pinned on the COMPILED path too, since that is what production runs.
        for (expr in listOf("findIndex(\$input.a, x => x > 100)", "findLastIndex(\$input.a, x => x > 100)")) {
            val (template, compiled) = run(expr, """{"a": [1, 2, 3]}""")
            assertEquals(true, template.contains("null"), "TEMPLATE should answer null: $template")
            assertEquals(true, compiled.contains("null"), "COMPILED should answer null: $compiled")
        }
        // `?? -1` recovers the pre-B73 value; the reverse was not expressible without searching twice.
        val (t, c) = run("findIndex(\$input.a, x => x > 100) ?? -1", """{"a": [1, 2, 3]}""")
        assertEquals(true, t.contains("-1"), "TEMPLATE: $t")
        assertEquals(true, c.contains("-1"), "COMPILED: $c")
    }
}
