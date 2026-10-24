package com.glomidco.utlx.core.interpreter

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * F24 — quantified rules: `every` / `some` over a named array.
 *
 * The structural analogue of a Schematron context-rule: `every: "lines"` with a `check` is
 * `<rule context="lines/item">` + `<assert test="…">`. The check receives each ELEMENT rather than the
 * payload, and `every` EXPANDS into one boolean rule per element, so each failing element becomes
 * its own issue carrying a JSON-Pointer `path` (`/lines/1`). That per-element locality is what the
 * vocabulary audit identified as the biggest gap against Schematron and FEEL, and it is the
 * prerequisite for a meaningful SVRL projection — SVRL's `location` has nothing to say about a
 * single whole-array issue.
 *
 * These tests assert on what the interpreter hands to `validate.assert` (see
 * [ValidateAssertCapture]), because expansion is interpreter behaviour; aggregation is `:stdlib`.
 */
class F24QuantifiedRulesTest {

    @BeforeEach fun reset() = ValidateAssertCapture.reset()

    private fun run(rules: String, payload: String = "three") {
        val src = "%utlx 1.1\ninput json\noutput json\n---\n\$input |> validate.assert([$rules])"
        val tokens = Lexer(src).tokenize()
        val program = when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        val interpreter = Interpreter()
        interpreter.setStdlibLookup(ValidateAssertCapture.lookup())
        interpreter.execute(program, parseJsonish(payload))
    }

    private fun parseJsonish(which: String): UDM = when (which) {
        "three" -> UDM.Object.of("lines" to UDM.Array(listOf(
            UDM.Object.of("qty" to UDM.Scalar(5)),
            UDM.Object.of("qty" to UDM.Scalar(-2)),
            UDM.Object.of("qty" to UDM.Scalar(-9)),
        )))
        "empty" -> UDM.Object.of("lines" to UDM.Array(emptyList()))
        "notArray" -> UDM.Object.of("lines" to UDM.Scalar(7))
        "oneUnevaluable" -> UDM.Object.of("lines" to UDM.Array(listOf(
            UDM.Object.of("qty" to UDM.Scalar(5)),
            UDM.Object.of("sku" to UDM.Scalar("no-qty-here")),
        )))
        else -> error("unknown payload $which")
    }

    private val positiveQty = """{ rule: "qty-positive", check: (l) => l.qty > 0, message: "qty must be positive" """

    // ── every: one rule per element, each with a path ──

    @Test fun `every expands to one rule per element, in order`() {
        run("""$positiveQty, every: "lines" }""")
        assertEquals(
            listOf<Any?>(true, false, false),
            ValidateAssertCapture.checks(),
            "expected one rule per element, resolved in array order",
        )
    }

    @Test fun `every evaluates all elements — one failure never hides another`() {
        run("""$positiveQty, every: "lines" }""")
        assertEquals(2, ValidateAssertCapture.received.count { ValidateAssertCapture.flag(it, "check") == false })
    }

    @Test fun `every over an empty array is vacuously true and still counts one check`() {
        run("""$positiveQty, every: "lines" }""", "empty")
        assertEquals(listOf<Any?>(true), ValidateAssertCapture.checks())
    }

    @Test fun `an element whose check cannot be evaluated is an error issue for THAT element`() {
        run("""$positiveQty, every: "lines" }""", "oneUnevaluable")
        val failing = ValidateAssertCapture.received.single { ValidateAssertCapture.flag(it, "check") == false }
        assertEquals("error", ValidateAssertCapture.text(failing, "severity"))
        val msg = assertNotNull(ValidateAssertCapture.text(failing, "message"))
        // Prose, not a path expression — see the class KDoc on why there is no location syntax.
        assertTrue(msg.contains("element 1 of 'lines'"), "unexpected message: $msg")
    }

    // ── some: one rule for the whole array ──

    @Test fun `some collapses to a single passing rule when any element satisfies the check`() {
        run("""$positiveQty, some: "lines" }""")
        assertEquals(listOf<Any?>(true), ValidateAssertCapture.checks())
    }

    @Test fun `some fails when no element satisfies the check`() {
        run("""{ rule: "r", check: (l) => l.qty > 100, message: "m", some: "lines" }""")
        assertEquals(false, ValidateAssertCapture.flag(ValidateAssertCapture.single(), "check"))
    }

    @Test fun `some over an empty array is false`() {
        run("""$positiveQty, some: "lines" }""", "empty")
        assertEquals(false, ValidateAssertCapture.flag(ValidateAssertCapture.single(), "check"))
    }

    // ── fail-closed on a quantifier that cannot run ──

    private fun assertMalformedQuantifier(rules: String, payload: String, fragment: String) {
        run(rules, payload)
        val props = ValidateAssertCapture.single()
        assertEquals(false, ValidateAssertCapture.flag(props, "check"), "a malformed quantifier must fail")
        assertEquals(true, ValidateAssertCapture.flag(props, "when"), "it must not be skippable")
        assertEquals("error", ValidateAssertCapture.text(props, "severity"))
        val msg = assertNotNull(ValidateAssertCapture.text(props, "message"))
        assertTrue(msg.contains("is malformed"), "should name the rule malformed: $msg")
        assertTrue(msg.contains(fragment), "expected '$fragment' in: $msg")
    }

    @Test fun `a quantifier over a non-array is malformed`() {
        assertMalformedQuantifier("""$positiveQty, every: "lines" }""", "notArray", "is not an array")
    }

    @Test fun `a quantifier with a non-lambda check is malformed, not silently passed`() {
        // Regression: the resolver's legacy-form early return used to send this straight to the
        // stdlib as `check: true`, so a malformed quantifier failed OPEN.
        assertMalformedQuantifier(
            """{ rule: "r", check: true, message: "m", every: "lines" }""", "three", "needs `check` as a lambda")
    }

    @Test fun `every and some on the same rule is malformed`() {
        assertMalformedQuantifier(
            """$positiveQty, every: "lines", some: "lines" }""", "three", "not both")
    }

    // ── interaction with the `when` guard ──

    @Test fun `a false when guard skips the rule without quantifying`() {
        run("""$positiveQty, every: "lines", when: (p) => false }""")
        val props = ValidateAssertCapture.single()
        assertEquals(false, ValidateAssertCapture.flag(props, "when"))
        assertEquals(1, ValidateAssertCapture.received.size, "a skipped rule must not expand")
    }

    @Test fun `a true when guard still quantifies`() {
        run("""$positiveQty, every: "lines", when: (p) => true }""")
        assertEquals(3, ValidateAssertCapture.received.size)
    }

    // ── the quantifier keys must not leak into the rule handed to the stdlib ──

    @Test fun `the every and some keys are consumed, and no synthetic key is injected`() {
        run("""$positiveQty, every: "lines" }""")
        assertTrue(
            ValidateAssertCapture.received.none { it.containsKey("every") || it.containsKey("some") },
            "quantifier keys should be removed once expanded: ${ValidateAssertCapture.describe()}",
        )
        // The rule record is the author's object; the engine must not stuff computed values into it.
        assertTrue(
            ValidateAssertCapture.received.none { it.containsKey("path") || it.containsKey("index") },
            "no location key should be injected into the rule record",
        )
    }
}
