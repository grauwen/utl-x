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
 * EB04 — **a lambda inside a lambda must not collide.**
 *
 * `ASTCompiler` names each lambda's generated static method `lambda_N` from a counter that lived on
 * `CompileContext`. `compileLambda` builds a *fresh* context for the lambda body, so the counter
 * restarted at zero inside every lambda: a lambda nested in another lambda emitted a second method
 * called `lambda_0`, and the JVM rejected the class with
 * `Duplicate method name "lambda_0" with signature "(Ljava.util.List;)..."`.
 *
 * The failure was invisible in normal use because `CompiledStrategy` catches it and **falls back to
 * the interpreter**, so the transformation still produced the right answer — just never compiled.
 * Eight conformance fixtures were silently running interpreted on the COMPILED strategy. It was
 * found by the cross-strategy conformance run, which counts how many fixtures genuinely reach
 * bytecode; that count is the only thing a silent fallback cannot hide from.
 *
 * These tests therefore assert `compile(...) != null` — *that it compiled at all* is the property
 * under test, not merely that the output is right.
 */
class EB04NestedLambdaTest {

    private val input = UDM.Object.of(
        "groups" to UDM.Array(listOf(
            UDM.Object.of("tags" to UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b")))),
            UDM.Object.of("tags" to UDM.Array(listOf(UDM.Scalar("c"))))
        ))
    )

    private fun program(body: String): Program {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        return when (val r = Parser(Lexer(src).tokenize(), src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
    }

    /** Compiles and runs, failing if compilation fell back instead of producing bytecode. */
    private fun compiled(body: String): UDM {
        val fn = ASTCompiler().compile(program(body))
        assertNotNull(fn, "`$body` did not compile — this is the EB04 symptom, not a pass")
        return fn.execute(mapOf("input" to input))
    }

    private fun interpreted(body: String): UDM =
        Interpreter().execute(program(body), input).toUDM()

    @Test
    fun `a lambda nested in a lambda compiles`() {
        val body = "map(\$input.groups, (g) => map(g.tags, (t) => upper(t)))"
        assertEquals(interpreted(body).toString(), compiled(body).toString())
    }

    @Test
    fun `three levels of nesting compile`() {
        val body = "map(\$input.groups, (g) => map(g.tags, (t) => map([t], (x) => upper(x))))"
        assertEquals(interpreted(body).toString(), compiled(body).toString())
    }

    @Test
    fun `sibling lambdas still get distinct names`() {
        // The pre-EB04 counter handled this case correctly; it is here so a fix that shares one
        // counter too aggressively cannot break it.
        val body = "{ a: map(\$input.groups, (g) => count(g.tags)), " +
            "b: map(\$input.groups, (g) => count(g.tags)) }"
        assertEquals(interpreted(body).toString(), compiled(body).toString())
    }

    @Test
    fun `nested and sibling lambdas together compile`() {
        val body = "{ x: map(\$input.groups, (g) => map(g.tags, (t) => upper(t))), " +
            "y: map(\$input.groups, (g) => map(g.tags, (t) => lower(t))) }"
        assertEquals(interpreted(body).toString(), compiled(body).toString())
    }
}
