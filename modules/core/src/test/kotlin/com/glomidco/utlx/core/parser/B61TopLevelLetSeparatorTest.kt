package com.glomidco.utlx.core.parser

import com.glomidco.utlx.core.lexer.Lexer
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * B61 — **a top-level `let` rejected the `;` separator that blocks accept.**
 *
 * F02 made the separator after a `let` optional — newline, `;` or `,` — but only the block half
 * was implemented. `parseProgram()`'s body loop called `parseExpression()` repeatedly with no
 * separator handling at all, so newlines "worked" by being whitespace the parser never sees while
 * a `;` was left in the stream and the next iteration died on it:
 *
 * ```
 * let x = 10; let y = 20; {result: x + y}
 *   => Expected expression
 * ```
 *
 * That line is not an invention — it is the example F02's own feature document gives under "The
 * Target State", and the same document's "What Stays the Same" promises "Semicolons as optional
 * single-line separators: still work". So this was a documented half of F02 that was never wired
 * up, which is why it is filed as a bug rather than a feature.
 *
 * Nothing in the corpus wrote the form, because the guide's other examples all use newlines; the
 * one place it appeared was a lint fixture in a suite no CI workflow runs.
 *
 * The fix consumes **one** optional separator per expression. A doubled separator stays an error:
 * `let a = 1;;` is a typo, not a style, and silently swallowing it would hide a dropped expression.
 */
class B61TopLevelLetSeparatorTest {

    private fun parse(body: String): ParseResult {
        val source = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        return Parser(Lexer(source).tokenize(), source).parse()
    }

    private fun assertParses(body: String, why: String) {
        val r = parse(body)
        assertIs<ParseResult.Success>(r, "$why — expected a parse, got: ${(r as? ParseResult.Failure)?.errors}")
    }

    private fun assertRejects(body: String, why: String) {
        assertTrue(parse(body) is ParseResult.Failure, "$why — expected a parse error, but it parsed")
    }

    // ── vacuity guard: the harness must really be parsing ──

    @Test
    fun `the harness parses a plain script and rejects obvious nonsense`() {
        assertParses("{ v: 1 }", "a bare object literal is valid")
        assertRejects("{ v: ", "an unclosed object is not")
    }

    // ── what B61 fixes ──

    @Test
    fun `F02's own single-line example parses`() {
        assertParses("let x = 10; let y = 20; {result: x + y}", "the example F02 documents as valid")
    }

    @Test
    fun `a top-level let accepts a trailing semicolon`() {
        assertParses("let a = 1;\n{ v: a }", "semicolon after a top-level let")
    }

    @Test
    fun `consecutive top-level lets accept semicolons`() {
        assertParses("let a = 1;\nlet b = 2;\n{ v: a + b }", "two bindings, both terminated")
    }

    @Test
    fun `a top-level let accepts a comma, as blocks do`() {
        assertParses("let a = 1,\nlet b = 2,\n{ v: a + b }", "comma is the other F02 separator")
    }

    @Test
    fun `a separator after the final expression is accepted`() {
        assertParses("let a = 1\n{ v: a };", "a trailing separator ends the script cleanly")
    }

    // ── what must NOT change ──

    @Test
    fun `newline separation still works`() {
        assertParses("let a = 1\nlet b = 2\n{ v: a + b }", "the form the whole corpus uses")
    }

    @Test
    fun `a function definition still follows a let`() {
        assertParses(
            "let a = 2;\n{ v: Double(a) }\nfunction Double(x) { x * 2 }",
            "hoisted function definitions are unaffected"
        )
    }

    @Test
    fun `blocks keep their own separator handling`() {
        assertParses("{ r: { let a = 1; a + 1 } }", "the block rule F02 already implemented")
    }

    // ── the limit: one separator, not a run ──

    @Test
    fun `a doubled separator is still an error`() {
        assertRejects("let a = 1;;\n{ v: a }", "`;;` is a typo and must not be swallowed")
        assertRejects("let a = 1,,\n{ v: a }", "the same for commas")
    }

    @Test
    fun `a separator with nothing before it is still an error`() {
        assertRejects(";\n{ v: 1 }", "a leading separator has no expression to terminate")
    }
}
