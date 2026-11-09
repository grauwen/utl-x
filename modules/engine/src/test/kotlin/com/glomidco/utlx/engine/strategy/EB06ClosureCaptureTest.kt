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
 * EB06 — **a lambda body must be able to reach a binding from an enclosing scope.**
 *
 * A `let` in `execute` is a real local slot; a lambda is a *static* method and cannot see it. So
 * the group-then-look-up idiom, which is ordinary UTL-X:
 *
 * ```utlx
 * let byOrder = groupBy($input.lines, (l) -> l.orderId)
 * map($input.orders, (o) -> { lines: byOrder[o.orderId] ?? [] })
 * ```
 *
 * compiled cleanly and then **threw at runtime**: `compileIdentifier` found no local called
 * `byOrder`, fell through to the named-input lookup, got null, and the generated code died in a
 * non-null parameter check. Unlike a compilation failure — which `CompiledStrategy` catches and
 * falls back from — this reached the caller, so a correct transformation failed on the engine's
 * default strategy while working on the CLI.
 *
 * The lambda now receives the enclosing bindings as a third parameter, snapshotted where the lambda
 * *value* is created. Snapshotting rather than sharing a frame is what lets a lambda outlive the
 * expression that made it.
 */
class EB06ClosureCaptureTest {

    private val input = UDM.Object.of(
        "orders" to UDM.Array(listOf(
            UDM.Object.of("id" to UDM.Scalar("A")),
            UDM.Object.of("id" to UDM.Scalar("B"))
        )),
        "lines" to UDM.Array(listOf(
            UDM.Object.of("oid" to UDM.Scalar("A"), "sku" to UDM.Scalar("x")),
            UDM.Object.of("oid" to UDM.Scalar("A"), "sku" to UDM.Scalar("y")),
            UDM.Object.of("oid" to UDM.Scalar("B"), "sku" to UDM.Scalar("z"))
        )),
        "factor" to UDM.Scalar(3L)
    )

    private fun program(body: String): Program {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        return when (val r = Parser(Lexer(src).tokenize(), src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
    }

    private fun assertAgree(label: String, body: String) {
        val fn = ASTCompiler().compile(program(body))
        assertNotNull(fn, "[$label] did not compile — the compiled path was not exercised")
        val compiled = runCatching { fn.execute(mapOf("input" to input)).toString() }
        val interpreted = runCatching { Interpreter().execute(program(body), input).toUDM().toString() }
        if (interpreted.isFailure) throw AssertionError(
            "[$label] the interpreter itself failed: ${interpreted.exceptionOrNull()?.message}")
        if (compiled.isFailure) throw AssertionError(
            "[$label] COMPILED threw where the interpreter did not: " +
                "${compiled.exceptionOrNull()?.message}")
        assertEquals(interpreted.getOrThrow(), compiled.getOrThrow(), "[$label] outputs differ")
    }

    // ── A scalar binding, the simplest capture ──

    @Test
    fun `a lambda reads an enclosing let holding a scalar`() {
        assertAgree("scalar", "{ let n = 2, out: map(\$input.orders, (o) -> n) }")
    }

    @Test
    fun `a lambda reads a program-level let`() {
        assertAgree("program-level", "let n = 2\nmap(\$input.orders, (o) -> n)")
    }

    // ── The group-then-look-up idiom, which is what broke ──

    @Test
    fun `a lambda indexes an enclosing let with a dynamic key`() {
        assertAgree("groupBy index",
            "let byOrder = groupBy(\$input.lines, (l) -> l.oid)\n" +
            "map(\$input.orders, (o) -> { id: o.id, lines: byOrder[o.id] ?? [] })")
    }

    @Test
    fun `the same enclosing binding can be read twice in one lambda`() {
        assertAgree("twice",
            "let byOrder = groupBy(\$input.lines, (l) -> l.oid)\n" +
            "map(\$input.orders, (o) -> { a: count(byOrder[o.id] ?? []), b: count(byOrder[o.id] ?? []) })")
    }

    @Test
    fun `a captured lookup feeding another function call`() {
        assertAgree("first of lookup",
            "let byOrder = groupBy(\$input.lines, (l) -> l.oid)\n" +
            "map(\$input.orders, (o) -> first(byOrder[o.id] ?? [])?.sku ?? \"none\")")
    }

    // ── Nesting: the captured set must accumulate ──

    @Test
    fun `a nested lambda reads a binding from two levels out`() {
        assertAgree("two levels",
            "let n = 2\nmap(\$input.orders, (o) -> map(\$input.lines, (l) -> n))")
    }

    @Test
    fun `a nested lambda reads the outer lambda's own let`() {
        assertAgree("outer lambda let",
            "map(\$input.orders, (o) -> { let k = o.id, out: map(\$input.lines, (l) -> k) })")
    }

    // ── Shadowing and precedence ──

    @Test
    fun `a lambda parameter shadows an enclosing binding of the same name`() {
        assertAgree("shadow", "{ let o = 99, out: map(\$input.orders, (o) -> o.id) }")
    }

    @Test
    fun `an enclosing binding does not shadow a named input`() {
        assertAgree("input still reachable",
            "let n = 2\nmap(\$input.orders, (o) -> count(\$input.lines))")
    }

    @Test
    fun `a lambda sees both an enclosing binding and a named input`() {
        assertAgree("both",
            "let n = 2\nmap(\$input.orders, (o) -> { a: n, b: count(\$input.lines), c: \$input.factor })")
    }
}
