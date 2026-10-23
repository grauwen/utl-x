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

/**
 * B34 cross-strategy invariant for `&&` / `||`.
 *
 * B34 was two defects wearing one hat:
 *
 *  1. The `:core` interpreter did not short-circuit (both operands were evaluated before the
 *     operator was dispatched on). Fixed in `evaluateBinaryOp`; guarded by
 *     `B34LogicalShortCircuitTest` in `:core`.
 *  2. The COMPILED path (`ASTCompiler.compileShortCircuit`) *did* short-circuit — correctly — but
 *     left the deciding **operand** on the stack, i.e. JS-style value-returning, where the
 *     interpreter returns a coerced boolean. So the same script could yield `1` on one strategy
 *     and `true` on the other. B34 resolved this in favour of boolean (`??` owns the
 *     default-value idiom) by emitting `RuntimeOps.toBoolean` after the short-circuit label.
 *
 * This test covers (2) — the half that was previously verified only by *reading* the emitted
 * bytecode. It drives `ASTCompiler` directly rather than through `CompiledStrategy`, because
 * `CompiledStrategy` silently falls back to the interpreter when `ASTCompiler.compile` returns
 * null: going through it, a test could pass while measuring the interpreter and prove nothing
 * about the compiled path. Here every case asserts `compile(...) != null` first, so a future
 * `isCompilable` change that stops compiling logical operators surfaces as a failure instead of
 * quietly voiding this test.
 */
class B34CompiledShortCircuitTest {

    /** `$input` for every case — note there is no `qty` property. */
    private val input = UDM.Object.of("price" to UDM.Scalar(5.0))

    private fun program(body: String): Program {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        val tokens = Lexer(src).tokenize()
        return when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
    }

    /** Executes `body` on the COMPILED path, asserting it genuinely compiled (no fallback). */
    private fun compiled(body: String): UDM {
        val fn = ASTCompiler().compile(program(body))
        assertNotNull(fn, "`$body` did not compile — the compiled path was not exercised")
        return fn.execute(mapOf("input" to input))
    }

    /** Executes `body` on the interpreted path. */
    private fun interpreted(body: String): UDM =
        Interpreter().execute(program(body), input).toUDM()

    private fun value(udm: UDM): Any? = (udm as UDM.Scalar).value

    // ── The compiled path must short-circuit ──

    @Test
    fun `compiled AND does not evaluate a dead right operand`() {
        // Reaching `1 / 0` would raise "Division by zero".
        assertEquals(false, value(compiled("false && (1 / 0)")))
    }

    @Test
    fun `compiled OR does not evaluate a dead right operand`() {
        assertEquals(true, value(compiled("true || (1 / 0)")))
    }

    @Test
    fun `compiled null guard protects the comparison behind it`() {
        assertEquals(false, value(compiled("\$input.qty != null && \$input.qty > 0")))
        assertEquals(true, value(compiled("\$input.qty == null || \$input.qty > 0")))
    }

    // ── The compiled path must return a BOOLEAN, not the deciding operand (the toBoolean fix) ──

    @Test
    fun `compiled logical operators coerce to boolean rather than returning the operand`() {
        // Pre-fix, compileShortCircuit left the operand on the stack: 1, "x" and 0 respectively.
        assertEquals(true, value(compiled("1 || 2")))
        assertEquals(true, value(compiled("\"\" || \"x\"")))
        assertEquals(false, value(compiled("0 && 5")))
    }

    // ── The invariant itself: the two strategies must agree ──

    @Test
    fun `interpreter and compiled agree on every logical expression`() {
        val cases = listOf(
            "true && true", "true && false", "false && true", "false && false",
            "true || true", "true || false", "false || true", "false || false",
            "false && (1 / 0)", "true || (1 / 0)",
            "1 || 2", "\"\" || \"x\"", "0 && 5",
            "true || false && false",
            "\$input.price != null && \$input.price > 0",
            "\$input.qty != null && \$input.qty > 0",
            "\$input.qty == null || \$input.qty > 0",
        )
        for (body in cases) {
            assertEquals(
                value(interpreted(body)),
                value(compiled(body)),
                "strategy divergence on `$body`",
            )
        }
    }
}
