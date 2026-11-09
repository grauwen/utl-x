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
 * EB07 — **index access must mean the same thing on both strategies.**
 *
 * `compileIndexAccess` carried the comment "for now, only support integer index literals" and, for
 * any other index expression, called `asNumber()` on it. A **string** key therefore became `0.0`,
 * so `byType["%types"]` read *element 0 of an object* and produced null — with no error at all.
 * Anything addressing an object by a computed key was silently wrong on the compiled path.
 *
 * It was the single largest divergence in the corpus. All 37 fixtures previously filed under
 * "COPY/COMPILED see a different input UDM" were this: the schema formats address their UDM as
 * `$input["%types"]` and `$input["%namespace"]`, so the earlier diagnosis — that `parseInputPublic`
 * failed to reproduce the full pipeline — was wrong. The input was fine; the subscript was not.
 *
 * A second divergence came with it. The interpreter **raises** on an out-of-range or negative index;
 * the compiled path returned null. Two strategies disagreeing about whether something is an error
 * is as much a divergence as disagreeing about a value, so the runtime now raises too.
 */
class EB07IndexAccessTest {

    private val input = UDM.Object.of(
        "byKey" to UDM.Object.of(
            "a" to UDM.Scalar(1L),
            "b" to UDM.Scalar(2L),
            "%special" to UDM.Scalar("sigil")
        ),
        "items" to UDM.Array(listOf(UDM.Scalar("x"), UDM.Scalar("y"), UDM.Scalar("z"))),
        "key" to UDM.Scalar("b"),
        "idx" to UDM.Scalar(1L),
        "missing" to UDM.Scalar("nope")
    )

    private fun program(body: String): Program {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        return when (val r = Parser(Lexer(src).tokenize(), src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
    }

    private fun compiled(body: String): Result<String> {
        val fn = ASTCompiler().compile(program(body))
        assertNotNull(fn, "`$body` did not compile — the compiled path was not exercised")
        return runCatching { fn.execute(mapOf("input" to input)).toString() }
    }

    private fun interpreted(body: String): Result<String> =
        runCatching { Interpreter().execute(program(body), input).toUDM().toString() }

    private fun assertAgree(body: String) {
        val c = compiled(body)
        val i = interpreted(body)
        assertTrue(i.isSuccess, "the interpreter itself failed on `$body`: ${i.exceptionOrNull()?.message}")
        assertTrue(c.isSuccess, "COMPILED threw where the interpreter did not on `$body`: ${c.exceptionOrNull()?.message}")
        assertEquals(i.getOrThrow(), c.getOrThrow(), "outputs differ for `$body`")
    }

    private fun assertBothRaise(body: String) {
        assertTrue(interpreted(body).isFailure, "the interpreter unexpectedly accepted `$body`")
        assertTrue(compiled(body).isFailure, "COMPILED returned a value where the interpreter raised, on `$body`")
    }

    // ── A string key addresses an object ──

    @Test
    fun `an object is addressable by a literal string key`() {
        assertAgree("\$input.byKey[\"a\"]")
    }

    @Test
    fun `an object is addressable by a computed string key`() {
        // The case that was silently wrong: the key is an expression, not a literal.
        assertAgree("\$input.byKey[\$input.key]")
    }

    @Test
    fun `a sigil-prefixed key works — this is how the schema formats are addressed`() {
        assertAgree("\$input.byKey[\"%special\"]")
    }

    @Test
    fun `a missing key yields null on both strategies`() {
        assertAgree("\$input.byKey[\$input.missing] ?? \"absent\"")
    }

    // ── A number still indexes an array ──

    @Test
    fun `an array is indexable by a literal and by a computed number`() {
        assertAgree("\$input.items[1]")
        assertAgree("\$input.items[\$input.idx]")
    }

    // ── Error parity: an error on one strategy must be an error on both ──

    @Test
    fun `an out-of-range index raises on both strategies`() {
        assertBothRaise("\$input.items[10]")
        assertBothRaise("\$input.items[\$input.idx + 99]")
    }

    @Test
    fun `indexing an object with a number raises on both strategies`() {
        assertBothRaise("\$input.byKey[0]")
    }

    @Test
    fun `indexing an array with a string raises on both strategies`() {
        assertBothRaise("\$input.items[\$input.key]")
    }
}
