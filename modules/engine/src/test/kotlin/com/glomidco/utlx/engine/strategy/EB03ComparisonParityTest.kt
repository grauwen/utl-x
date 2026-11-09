package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.core.ast.Program
import com.glomidco.utlx.core.interpreter.Interpreter
import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.engine.strategy.compiled.ASTCompiler
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * EB03 — **comparison must not depend on which executable ran.**
 *
 * `utlx`, `utlxd` and `utlxe` share one core, and the guide states the contract plainly: *"A
 * transformation that works in one executable works identically in all three... the conformance
 * suite validates behavior across executables."* It did not. The conformance suite drives the CLI,
 * which interprets, so the COMPILED path — the one `utlxe` uses in production, and the engine's
 * default strategy — was never measured against those fixtures. Seven comparisons disagreed:
 *
 * ```
 *   "5" == 5        compiled true    interpreted false
 *   5 == "5"        compiled true    interpreted false
 *   "5" != 5        compiled false   interpreted true
 *   "5" < 6         compiled true    interpreted ERROR
 *   "5" > 4         compiled true    interpreted ERROR
 *   true == 1       compiled true    interpreted false
 *   true == "true"  compiled true    interpreted false
 * ```
 *
 * `RuntimeOps.equal` was `compareValues(...) == 0`, and `compareValues` coerced through `toNumber`,
 * which converts a numeric string and a boolean. That is **type confusion**, not imprecision: a
 * rule written `check: p.id == 5` admitted the string `"5"` in production while rejecting it on the
 * developer's machine. Ordering was worse — the compiled path returned a confident verdict where
 * the interpreter raised an error.
 *
 * The interpreter's semantics won: stricter, fail-closed, consistent with the validation functions
 * (the string `"50"` is not the number 50), and the semantics the 554 conformance fixtures actually
 * pin. Every case below asserts the two strategies agree **and** what they agree on, so aligning
 * them later in the wrong direction is also a failure.
 *
 * Numeric exactness parity lives in [B35CompiledExactNumericTest]; this covers the type axis.
 */
class EB03ComparisonParityTest {

    private val input = UDM.Object.of(
        "s5" to UDM.Scalar("5"),
        "n5" to UDM.Scalar(5L),
        "flag" to UDM.Scalar(true),
        "nul" to UDM.Scalar(null)
    )

    private fun program(body: String): Program {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n{ v: $body }"
        val tokens = Lexer(src).tokenize()
        return when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
    }

    private fun compiled(body: String): Any? {
        val fn = ASTCompiler().compile(program(body))
        assertNotNull(fn, "`$body` did not compile — the compiled path was not exercised")
        val out = fn.execute(mapOf("input" to input)) as UDM.Object
        return (out.properties["v"] as UDM.Scalar).value
    }

    private fun interpreted(body: String): Any? {
        val out = Interpreter().execute(program(body), input).toUDM() as UDM.Object
        return (out.properties["v"] as UDM.Scalar).value
    }

    /** Both strategies must agree, and agree on [expected]. */
    private fun assertBoth(body: String, expected: Any?) {
        assertEquals(expected, compiled(body), "COMPILED disagreed for `$body`")
        assertEquals(expected, interpreted(body), "INTERPRETED disagreed for `$body`")
    }

    /** Both strategies must refuse, rather than one inventing a verdict. */
    private fun assertBothThrow(body: String) {
        var compiledThrew = false
        try { compiled(body) } catch (e: Exception) { compiledThrew = true }
        var interpretedThrew = false
        try { interpreted(body) } catch (e: Exception) { interpretedThrew = true }
        assertTrue(compiledThrew, "COMPILED returned a verdict for `$body` instead of refusing")
        assertTrue(interpretedThrew, "INTERPRETED unexpectedly accepted `$body`")
    }

    // ── A string is not a number ──

    @Test
    fun `a numeric string does not equal the number`() {
        assertBoth("\$input.s5 == \$input.n5", false)
        assertBoth("\$input.n5 == \$input.s5", false)
        assertBoth("\$input.s5 == 5", false)
        assertBoth("\"5\" == 5", false)
    }

    @Test
    fun `inequality is the exact negation`() {
        assertBoth("\$input.s5 != \$input.n5", true)
        assertBoth("\$input.n5 != 5", false)
    }

    // ── A boolean is not a number, and not its own spelling ──

    @Test
    fun `a boolean does not equal a number`() {
        assertBoth("\$input.flag == 1", false)
        assertBoth("\$input.flag == 0", false)
    }

    @Test
    fun `a boolean does not equal its string spelling`() {
        assertBoth("\$input.flag == \"true\"", false)
    }

    @Test
    fun `a boolean still equals a boolean`() {
        assertBoth("\$input.flag == true", true)
        assertBoth("\$input.flag == false", false)
    }

    // ── Null ──

    @Test
    fun `null equals null and nothing else`() {
        assertBoth("\$input.nul == \$input.nul", true)
        assertBoth("\$input.nul == 0", false)
        assertBoth("\$input.nul == \"\"", false)
        assertBoth("\$input.nul == false", false)
    }

    // ── Ordering across unlike types is an error, not a verdict ──

    @Test
    fun `ordering a string against a number is refused on both strategies`() {
        assertBothThrow("\$input.s5 < 6")
        assertBothThrow("\$input.s5 > 4")
        assertBothThrow("\$input.flag < 1")
    }

    // ── What must keep working ──

    @Test
    fun `like types still compare normally`() {
        assertBoth("\$input.n5 == 5", true)
        assertBoth("\$input.n5 < 6", true)
        assertBoth("\$input.n5 > 6", false)
        assertBoth("\"abc\" < \"abd\"", true)
        assertBoth("\"abc\" == \"abc\"", true)
        assertBoth("\"abc\" == 0", false)
    }

    @Test
    fun `numeric representation still does not matter — B35 is not undone`() {
        assertBoth("\$input.n5 == 5.0", true)
        assertBoth("5 == 5.00", true)
        assertBoth("9007199254740993 == 9007199254740992", false)
    }
}
