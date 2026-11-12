package com.glomidco.utlx.core.types

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B52 — **a function call must be resolved as a function, not as a variable.**
 *
 * Root cause: the `Expression.FunctionCall` branch opened with
 * `val funcType = inferType(expr.function, env)`, run first and unconditionally. A named
 * callee is an `Expression.Identifier`, so it went through variable resolution, missed, and
 * was recorded as **"Undefined variable"** — before the stdlib lookup further down ever ran.
 *
 * The result was a diagnostic carrying no information either way: `upper("a")` warned exactly
 * as loudly as a genuine typo, so `utlx validate` could not do the one thing level-2
 * validation is for.
 *
 * Two design points are pinned here, because getting either wrong is worse than the bug:
 *
 *  - **Silence without the name set.** `:core` holds 15 type signatures and cannot see
 *    `:stdlib`'s ~750-function registry (the dependency runs stdlib -> core). With no
 *    authoritative set the checker must stay quiet rather than guess — erroring would make
 *    `--strict-types` fail on every function core has no signature for.
 *  - **A let-bound lambda is still a value.** `let f = (x) -> x * 2` then `f(21)` must keep
 *    resolving through variable lookup, so the function-first path has to fall through to it.
 */
class B52FunctionCallTypeCheckTest {

    private fun check(body: String, known: Set<String> = emptySet()): List<TypeError> {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        val tokens = Lexer(src).tokenize()
        val program = when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        return when (val r = TypeChecker(StandardLibrary(), known).check(program)) {
            is TypeCheckResult.Success -> emptyList()
            is TypeCheckResult.Failure -> r.errors
        }
    }

    private val realNames = setOf("upper", "count", "keys", "join", "sum", "sortBy", "innerJoin")

    // ── guard against a vacuous harness ──

    @Test
    fun `the harness really type-checks — argument errors are still reported`() {
        val errs = check("{ r: upper(42) }")
        assertTrue(errs.any { it.message.contains("Argument type mismatch") },
            "harness is not type-checking: ${errs.map { it.message }}")
    }

    // ── the bug ──

    @Test
    fun `a function core has a signature for produces no error`() {
        for (body in listOf("""{ r: upper("a") }""", "{ r: count([1,2]) }", "{ r: sum([1,2]) }")) {
            assertEquals(emptyList(), check(body).map { it.message }, "for $body")
        }
    }

    @Test
    fun `a function core has NO signature for produces no error without a name set`() {
        // keys/sortBy/join are not among core's 15 signatures. Before B52 each produced
        // "Undefined variable"; now the checker stays silent, because it cannot know.
        for (body in listOf("""{ r: keys({"a":1}) }""", "{ r: sortBy([2,1],(x)->x) }",
                            """{ r: join(["a","b"],"-") }""")) {
            assertEquals(emptyList(), check(body).map { it.message }, "for $body")
        }
    }

    @Test
    fun `an unknown function IS reported once the authoritative name set is supplied`() {
        val errs = check("""{ r: totallyMadeUpFunction("x") }""", realNames)
        assertEquals(1, errs.size, "expected exactly one error, got: ${errs.map { it.message }}")
        assertTrue(errs[0].message.contains("Undefined function: totallyMadeUpFunction"),
            "wrong message: ${errs[0].message}")
    }

    @Test
    fun `the message says FUNCTION, not variable`() {
        // The whole point: a name in call position resolving nowhere is an undefined
        // function. Calling it a variable hid the actual class of error.
        val errs = check("""{ r: nope123("x") }""", realNames)
        assertTrue(errs.single().message.startsWith("Undefined function:"),
            "got: ${errs.single().message}")
    }

    @Test
    fun `a known name is accepted even with no core type signature`() {
        assertEquals(emptyList(), check("""{ r: join(["a"],"-") }""", realNames).map { it.message })
        assertEquals(emptyList(),
            check("{ r: innerJoin([],[],(l)->l,(r)->r) }", realNames).map { it.message })
    }

    // ── the two things a careless fix breaks ──

    @Test
    fun `a let-bound lambda still resolves as a value`() {
        assertEquals(emptyList(),
            check("{ let f = (x) -> x * 2, r: f(21) }", realNames).map { it.message })
    }

    @Test
    fun `arity and argument types are still checked for signatures core does have`() {
        assertTrue(check("""{ r: upper("a","b") }""").any { it.message.contains("expects 1 arguments") },
            "arity check lost")
        assertTrue(check("{ r: upper(42) }").any { it.message.contains("Argument type mismatch") },
            "argument type check lost")
    }

    @Test
    fun `the error points at the callee, not at the whole call`() {
        val errs = check("""{ r: nope123("x") }""", realNames)
        // "{ r: " is five characters, so the name starts at column 6.
        assertEquals(6, errs.single().location.column, "caret must sit under the mistyped name")
    }
}
