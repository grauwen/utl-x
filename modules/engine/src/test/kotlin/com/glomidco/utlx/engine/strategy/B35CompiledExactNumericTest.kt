package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.core.ast.Program
import com.glomidco.utlx.core.interpreter.Interpreter
import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.engine.strategy.compiled.ASTCompiler
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * B35 cross-strategy invariant — **a number must not depend on which strategy ran.**
 *
 * The same shape of problem B34 had: the `:core` interpreter was fixed to carry a literal's exact
 * written form, while the COMPILED path emitted `Double.valueOf` for every numeric literal. COMPILED
 * is the engine's default strategy, so without this the default path would still serialise
 * `9007199254740993` as `…992` and `100.10` as `100.1`, and the two strategies would disagree on
 * the value of the same script. For a deployment whose validation result is consumed as an oracle,
 * a strategy-dependent number is worse than a uniformly lossy one.
 *
 * Drives `ASTCompiler` directly rather than `CompiledStrategy`, which silently falls back to the
 * interpreter when `compile` returns null — going through it, a test could pass while measuring the
 * interpreter and prove nothing. Every case asserts `compile(...) != null` first.
 */
class B35CompiledExactNumericTest {

    private val input = UDM.Object.of(
        "big" to UDM.Scalar(9007199254740993L),
        "dec" to UDM.Scalar(BigDecimal("100.10"))
    )

    private fun program(body: String): Program {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        val tokens = Lexer(src).tokenize()
        return when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
    }

    private fun compiled(body: String): UDM {
        val fn = ASTCompiler().compile(program(body))
        assertNotNull(fn, "`$body` did not compile — the compiled path was not exercised")
        return fn.execute(mapOf("input" to input))
    }

    private fun interpreted(body: String): UDM =
        Interpreter().execute(program(body), input).toUDM()

    private fun scalar(udm: UDM, key: String): Any? =
        ((udm as UDM.Object).properties[key] as UDM.Scalar).value

    /** Both strategies must agree, and on the exact value. */
    private fun assertBoth(body: String, key: String, expected: Any?) {
        assertEquals(expected, scalar(compiled(body), key), "COMPILED disagreed for `$body`")
        assertEquals(expected, scalar(interpreted(body), key), "INTERPRETED disagreed for `$body`")
    }

    @Test
    fun `an integer literal above 2^53 survives the compiled path`() {
        assertBoth("{ v: 9007199254740993 }", "v", 9007199254740993L)
    }

    @Test
    fun `a decimal literal keeps its scale on the compiled path`() {
        assertBoth("{ v: 100.10 }", "v", BigDecimal("100.10"))
    }

    @Test
    fun `a decimal beyond Double precision survives the compiled path`() {
        assertBoth("{ v: 12345678901234567.89 }", "v", BigDecimal("12345678901234567.89"))
    }

    @Test
    fun `an integer literal wider than Long survives the compiled path`() {
        assertBoth("{ v: 9223372036854775808 }", "v", java.math.BigInteger("9223372036854775808"))
    }

    @Test
    fun `a plain small integer is unchanged`() {
        assertBoth("{ v: 42 }", "v", 42L)
    }

    @Test
    fun `an exponent literal stays a Double on both strategies`() {
        assertBoth("{ v: 1.5e3 }", "v", 1500.0)
    }

    // ── Comparison parity: the compiled path narrowed to Double here too ──

    @Test
    fun `equality above 2^53 agrees across strategies`() {
        assertBoth("{ v: 9007199254740993 == 9007199254740992 }", "v", false)
        assertBoth("{ v: \$input.big == 9007199254740993 }", "v", true)
        assertBoth("{ v: \$input.big == 9007199254740992 }", "v", false)
    }

    @Test
    fun `ordering above 2^53 agrees across strategies`() {
        assertBoth("{ v: \$input.big > 9007199254740992 }", "v", true)
        assertBoth("{ v: 9007199254740992 < 9007199254740993 }", "v", true)
    }

    @Test
    fun `scale-insensitive equality agrees across strategies`() {
        assertBoth("{ v: \$input.dec == 100.1 }", "v", true)
        assertBoth("{ v: \$input.dec == 100.10 }", "v", true)
        assertBoth("{ v: 5 == 5.0 }", "v", true)
    }

    // ── Stage 2 arithmetic parity: both strategies must compute the same number ──

    @Test
    fun `exact multiplication agrees across strategies`() {
        assertBoth("{ v: 19.99 * 100 }", "v", BigDecimal("1999.00"))
        assertBoth("{ v: \$input.dec * 2 }", "v", BigDecimal("200.20"))
        assertBoth("{ v: 19.99 * 100 == 1999 }", "v", true)
    }

    @Test
    fun `exact addition and subtraction agree across strategies`() {
        assertBoth("{ v: 0.1 + 0.2 }", "v", BigDecimal("0.3"))
        assertBoth("{ v: 0.1 + 0.2 > 0.3 }", "v", false)
        assertBoth("{ v: 10.00 - 9.99 }", "v", BigDecimal("0.01"))
        assertBoth("{ v: \$input.big - 9007199254740992 }", "v", BigDecimal("1"))
    }

    @Test
    fun `division stays approximate on both strategies`() {
        assertBoth("{ v: 10 / 3 }", "v", 3.3333333333333335)
        assertBoth("{ v: (10 / 3) * 3 }", "v", 10.0)
    }

    @Test
    fun `a Double operand keeps both strategies on the approximate path`() {
        assertBoth("{ v: 1e2 * 1.5 }", "v", 150.0)
    }

    @Test
    fun `a payload value passes through identically on both strategies`() {
        assertBoth("{ v: \$input.big }", "v", 9007199254740993L)
        assertBoth("{ v: \$input.dec }", "v", BigDecimal("100.10"))
    }
}
