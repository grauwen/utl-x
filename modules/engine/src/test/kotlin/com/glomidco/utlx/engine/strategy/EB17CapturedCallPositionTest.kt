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
 * EB17 — **a captured binding must be callable, not merely readable.**
 *
 * EB06 gave a lambda method a third parameter carrying the enclosing frame's bindings, and taught
 * `compileIdentifier` to read a name from that map when it is not a local slot. It left **call
 * position** behind: `compileFunctionCall` looked the callee up in `ctx.locals` and, finding
 * nothing, emitted a `StdlibDispatch.call(name, args)` — so a lambda bound by `let` and invoked
 * from inside another lambda compiled cleanly and then threw `Undefined function: dbl` at runtime:
 *
 * ```utlx
 * { let dbl = (x) -> x * 2, r: map($input.ns, (n) -> dbl(n)) }
 * ```
 *
 * The interpreter runs that correctly, so this was a silent divergence on the engine's default
 * strategy — the same shape as B34, B35, EB03 and B48, and found the same way: by adding a
 * conformance fixture (`language/let-bindings/b52_object_let_lambda_scope.yaml`) and letting
 * [ConformanceStrategyParityTest] run it through all three strategies. Nothing in the 573-test
 * corpus had ever called a let-bound lambda from inside another lambda.
 *
 * Note what is *not* the bug: an object-literal `let` does get a real local slot, and the captured
 * map is built from `ctx.locals.keys + ctx.capturedNames`, so the binding was present and
 * readable the whole time. Only the call site could not see it.
 *
 * **Precedence** is locals, then captured, then stdlib — chosen to match the interpreter, where a
 * `let` shadows a stdlib function of the same name.
 */
class EB17CapturedCallPositionTest {

    private val input = UDM.Object.of(
        "ns" to UDM.Array(listOf(UDM.Scalar(1L), UDM.Scalar(2L), UDM.Scalar(3L))),
        "words" to UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b")))
    )

    private fun program(body: String): Program {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        return when (val r = Parser(Lexer(src).tokenize(), src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
    }

    /** Assert COMPILED agrees with the interpreter, and that it really compiled. */
    private fun assertAgree(label: String, body: String) {
        val fn = ASTCompiler().compile(program(body))
        assertNotNull(fn, "[$label] did not compile — the compiled path was not exercised, so " +
            "CompiledStrategy would have fallen back to the interpreter and proved nothing")
        val compiled = runCatching { fn.execute(mapOf("input" to input)).toString() }
        val interpreted = runCatching { Interpreter().execute(program(body), input).toUDM().toString() }
        if (interpreted.isFailure) throw AssertionError(
            "[$label] the interpreter itself failed: ${interpreted.exceptionOrNull()?.message}")
        if (compiled.isFailure) throw AssertionError(
            "[$label] COMPILED threw where the interpreter did not: " +
                "${compiled.exceptionOrNull()?.message}")
        assertEquals(interpreted.getOrThrow(), compiled.getOrThrow(), "[$label] outputs differ")
    }

    // ── the bug ──

    @Test
    fun `an object-literal let lambda is callable from inside a lambda`() {
        assertAgree("object let, called in map",
            "{ let dbl = (x) -> x * 2, r: map(\$input.ns, (n) -> dbl(n)) }")
    }

    @Test
    fun `a script-level let lambda is callable from inside a lambda`() {
        assertAgree("script let, called in map",
            "let dbl = (x) -> x * 2\n{ r: map(\$input.ns, (n) -> dbl(n)) }")
    }

    @Test
    fun `an outer object's let lambda is callable from a nested object's lambda`() {
        assertAgree("nested object, outer let",
            "{ let f = (x) -> x + 1, o: { let g = (x) -> f(x) * 10, v: g(4) } }")
    }

    @Test
    fun `a captured lambda is callable two lambdas deep`() {
        // The captured set accumulates through nesting (EB06), so the inner lambda reaches `dbl`
        // through the middle lambda's own captured map rather than a local slot.
        assertAgree("two deep",
            "{ let dbl = (x) -> x * 2, r: map(\$input.ns, (n) -> map([n], (m) -> dbl(m))) }")
    }

    // ── precedence ──

    @Test
    fun `a let shadows a stdlib function of the same name, through the capture`() {
        // If the captured branch were placed after the stdlib branch, this would call the real
        // `upper` and the two paths would silently disagree on a script that runs.
        assertAgree("shadowing through capture",
            "{ let upper = (x) -> \"SHADOW\", r: map(\$input.words, (s) -> upper(s)) }")
    }

    @Test
    fun `a stdlib call inside a lambda is unaffected`() {
        // The guard is `funcName in ctx.capturedNames`; a name that is not captured must still
        // reach StdlibDispatch.
        assertAgree("stdlib in lambda", "{ r: map(\$input.words, (s) -> upper(s)) }")
    }

    @Test
    fun `a lambda parameter shadows a captured binding of the same name`() {
        assertAgree("param shadows capture",
            "{ let f = (x) -> x * 2, r: map(\$input.ns, (f) -> f + 1) }")
    }

    // ── the second defect, found by the same fixture ──

    @Test
    fun `a nested object's let does not leak into later properties of the enclosing object`() {
        // `allocLocal` writes into `ctx.locals`, which has no scope stack, so the inner `dbl`
        // overwrote the outer one for the REST of the enclosing object: `stillOuter` returned 101
        // compiled against 2 interpreted. Only a property placed *after* the nested object can see
        // this, which is why no existing fixture did.
        assertAgree("nested let must not leak",
            "{ let dbl = (x) -> x * 2, " +
            "  shadowed: { let dbl = (x) -> x + 100, v: dbl(1) }, " +
            "  stillOuter: dbl(1) }")
    }

    @Test
    fun `a nested object's let does not leak into a later lambda either`() {
        // The capture set is `ctx.locals.keys + ctx.capturedNames`, read when the lambda VALUE is
        // created — so a leaked slot would be snapshotted into the lambda too.
        assertAgree("leak into later lambda",
            "{ let dbl = (x) -> x * 2, " +
            "  shadowed: { let dbl = (x) -> x + 100, v: dbl(1) }, " +
            "  later: map(\$input.ns, (n) -> dbl(n)) }")
    }

    @Test
    fun `a nested object's let of a NEW name does not survive either`() {
        // The restore must remove a name that had no previous binding, not merely rewind one.
        // If it only rewound, `fresh` would still resolve after the nested object closed and the
        // compiled path would differ from the interpreter, which errors.
        val body = "{ a: { let fresh = 7, v: fresh }, b: 1 }"
        assertAgree("new name does not survive", body)
    }

    // ── what already worked, pinned so the fix cannot regress it ──

    @Test
    fun `a let lambda called directly still works`() {
        assertAgree("direct call", "{ let f = (x) -> x * 2, r: f(21) }")
    }

    @Test
    fun `a let lambda that uses an earlier let still works`() {
        assertAgree("uses earlier let", "{ let k = 10, let s = (x) -> x * k, r: s(7) }")
    }

    @Test
    fun `a nested let of the same name shadows the outer one`() {
        assertAgree("shadowed in nested object",
            "{ let f = (x) -> x + 1, a: f(1), i: { let f = (x) -> x + 100, b: f(1) } }")
    }
}
