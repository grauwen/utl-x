package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.core.interpreter.Interpreter
import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.engine.strategy.compiled.ASTCompiler
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * EB12 and EB13 — two defects that only showed together, in the same two fixtures.
 *
 * **EB12: a compiled `try`/`catch` emitted a class the verifier rejects.** At an exception handler
 * the JVM discards the operand stack down to the exception, so a try region entered while an
 * enclosing object literal still has a half-built array on the stack produces:
 *
 * ```
 * java.lang.VerifyError: Instruction type does not match stack map
 *   Current Frame:  stack: { UDM$Scalar }
 *   Stackmap Frame: stack: { Object, [UDM, [UDM, integer, UDM }
 * ```
 *
 * A bare top-level `try { … } catch { … }` compiled and ran fine, which is why it went unnoticed —
 * it was only wrong in the position people actually write it. `isCompilable` is now positional:
 * `try`/`catch` is compilable only where the operand stack is provably empty, which is the root of
 * `execute` and the root of each generated lambda method. Anywhere else the transformation goes to
 * the interpreter, which is correct and merely slower.
 *
 * **EB13: compiled arithmetic turned a non-numeric operand into zero.** Every op used
 * `toNumber(x) ?: 0.0` where the interpreter's `extractNumber` raises. So with `price: null`:
 *
 * ```
 * interpreter   raises  →  the catch fires  →  status "error"
 * compiled      0       →  no exception     →  status "success", taxedPrice 0
 * ```
 *
 * A plausible-looking record reporting success for a row that had no price. In a money mapping a
 * missing field becoming zero is the worst available answer, and it is also what hid EB12's
 * remaining fixture: the catch never ran, so the try never mattered.
 */
class EB12TryCatchAndNullArithmeticTest {

    private val input = UDM.Object.of(
        "v" to UDM.Scalar(10L),
        "zero" to UDM.Scalar(0L),
        "nothing" to UDM.Scalar(null),
        "text" to UDM.Scalar("abc"),
        "numText" to UDM.Scalar("5")
    )

    private fun program(body: String) =
        "%utlx 1.0\ninput json\noutput json\n---\n$body".let { src ->
            when (val r = Parser(Lexer(src).tokenize(), src).parse()) {
                is ParseResult.Success -> r.program
                is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
            }
        }

    private fun compiledResult(body: String): Result<String>? {
        val fn = ASTCompiler().compile(program(body)) ?: return null   // null = falls back
        return runCatching { fn.execute(mapOf("input" to input)).toString() }
    }

    private fun interpretedResult(body: String): Result<String> =
        runCatching { Interpreter().execute(program(body), input).toUDM().toString() }

    /** Where it compiles, it must agree; where it does not, the fallback covers it. */
    private fun assertAgreeOrFallBack(body: String) {
        val c = compiledResult(body) ?: return
        val i = interpretedResult(body)
        if (i.isFailure) {
            assertTrue(c.isFailure, "COMPILED produced a value where the interpreter raised: `$body` -> ${c.getOrNull()}")
            return
        }
        assertTrue(c.isSuccess, "COMPILED raised where the interpreter did not: `$body` -> ${c.exceptionOrNull()?.message}")
        assertEquals(i.getOrThrow(), c.getOrThrow(), "outputs differ for `$body`")
    }

    // ── EB12: the positional gate ──

    @Test
    fun `a bare top-level try catch still compiles`() {
        val body = "try { \$input.v / \$input.zero } catch { -1 }"
        assertNotNull(ASTCompiler().compile(program(body)), "the working position must stay compilable")
        assertAgreeOrFallBack(body)
    }

    @Test
    fun `a try catch inside an object literal is not compiled, rather than mis-compiled`() {
        // Previously this emitted a class that failed verification at load time.
        val body = "{ r: try { \$input.v / \$input.zero } catch { -1 }, m: \"x\" }"
        assertNull(ASTCompiler().compile(program(body)),
            "a try/catch with a non-empty operand stack must not be compiled")
        assertAgreeOrFallBack(body)
    }

    @Test
    fun `a try catch inside an array literal is not compiled`() {
        assertNull(ASTCompiler().compile(program("[ try { \$input.v / \$input.zero } catch { -1 } ]")))
    }

    @Test
    fun `a try catch as an operator operand is not compiled`() {
        assertNull(ASTCompiler().compile(program("1 + try { \$input.v } catch { 0 }")))
    }

    @Test
    fun `a try catch at a lambda body root is still compilable`() {
        // A lambda becomes its own method, so the stack is empty at its root again.
        val body = "map([1], (x) -> try { x } catch { 0 })"
        assertNotNull(ASTCompiler().compile(program(body)))
        assertAgreeOrFallBack(body)
    }

    // ── EB13: a non-numeric operand raises rather than becoming zero ──

    @Test
    fun `arithmetic on null raises on both paths instead of yielding zero`() {
        for (op in listOf("+", "-", "*", "/")) {
            val body = "{ r: \$input.nothing $op 2 }"
            val i = interpretedResult(body)
            assertTrue(i.isFailure, "the interpreter was expected to raise for `$body`")
            val c = compiledResult(body) ?: continue
            assertTrue(c.isFailure, "COMPILED yielded ${c.getOrNull()} where the interpreter raised: `$body`")
        }
    }

    @Test
    fun `arithmetic on non-numeric text raises on both paths`() {
        assertAgreeOrFallBack("{ r: \$input.text * 2 }")
        val c = compiledResult("{ r: \$input.text * 2 }")
        if (c != null) assertTrue(c.isFailure, "non-numeric text must not become zero")
    }

    @Test
    fun `a numeric string still coerces, as the interpreter does`() {
        // extractNumber accepts a numeric string, so this must keep working on both paths.
        assertAgreeOrFallBack("{ r: \$input.numText * 2 }")
    }

    @Test
    fun `unary minus on null raises rather than yielding zero`() {
        val body = "{ r: -\$input.nothing }"
        assertTrue(interpretedResult(body).isFailure)
        val c = compiledResult(body)
        if (c != null) assertTrue(c.isFailure, "unary minus on null must not become 0")
    }

    @Test
    fun `ordinary arithmetic is unaffected`() {
        assertAgreeOrFallBack("{ a: \$input.v + 1, b: \$input.v * 2, c: \$input.v - 3, d: -\$input.v }")
    }

    @Test
    fun `the catch now fires because the arithmetic raises`() {
        // The two defects together: with price null the compiled path reported success.
        val body = "map([1], (x) -> try { { t: \$input.nothing * 2, s: \"success\" } } catch { { s: \"error\" } })"
        assertAgreeOrFallBack(body)
        val i = interpretedResult(body)
        assertTrue(i.isSuccess && i.getOrThrow().contains("error"),
            "the interpreter should route to the catch: ${i.getOrNull()}")
    }
}
