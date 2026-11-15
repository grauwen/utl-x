package com.glomidco.utlx.core.parser

import com.glomidco.utlx.core.lexer.Lexer
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * F30 — **a trailing comma is accepted in literals, argument lists and match cases.**
 *
 * `syntax.md` documented "Trailing comma OK" for objects and arrays on both branches, and nothing
 * accepted it: `[1, 2, 3,]`, `{a: 1, b: 2,}`, `max(1, 2,)` and a trailing comma after the last
 * match case were all parse errors.
 *
 * The reason to support it is not taste. UTL-X scripts are emitted by machines — the IDE's mapping
 * builder and the MCP server's `generate_utlx_from_prompt` — and an emitter that may append `",\n"`
 * after every element needs no last-element special case. F02 made the same argument when it rated
 * the "AI generation impact" of separator confusion HIGH. It also halves the diff when a long-lived
 * mapping gains a field.
 *
 * ### The limit, which is the whole safety argument
 *
 * One separator directly before the closer is ignored. **Empty elements are still errors**:
 *
 * ```
 * [1,,2]   => Expected expression      (a dropped value, not a style)
 * [,1]     => Expected expression
 * ```
 *
 * Anything laxer would silently swallow a typo that loses data. These negative cases matter more
 * than the positive ones and are asserted below.
 *
 * Header lists (`input: a xml, b json`) are deliberately **not** covered: a trailing comma after
 * the last declared input reads like a dropped input, and headers are hand-written once rather
 * than emitted repeatedly.
 */
class F30TrailingCommaTest {

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

    // ── vacuity guard ──

    @Test
    fun `the harness parses valid input and rejects invalid input`() {
        assertParses("{ r: [1, 2] }", "a plain array")
        assertRejects("{ r: [1, 2 }", "an unclosed array")
    }

    // ── what F30 adds ──

    @Test
    fun `array literal accepts a trailing comma`() {
        assertParses("{ r: [1, 2, 3,] }", "single-line array")
        assertParses("{ r: [\n  1,\n  2,\n  3,\n] }", "multi-line array, the documented example")
    }

    @Test
    fun `object literal accepts a trailing comma`() {
        assertParses("{ a: 1, b: 2, }", "single-line object")
        assertParses("{\n  a: 1,\n  b: 2,\n}", "multi-line object")
    }

    @Test
    fun `call arguments accept a trailing comma`() {
        assertParses("{ r: max(1, 2,) }", "argument list")
    }

    @Test
    fun `lambda parameters accept a trailing comma`() {
        assertParses("{ r: ((a, b,) -> a + b)(1, 2) }", "multi-parameter lambda")
    }

    @Test
    fun `match cases accept a trailing comma`() {
        assertParses("{ r: match (1) { 1 => \"one\", _ => \"other\", } }", "match arms")
    }

    @Test
    fun `a spread may carry the trailing comma`() {
        // Keep the let at the top level. Writing it as an object-literal prelude
        // (`{ let base = (...), ...base, }`) tests F02's block-separator rule rather than this
        // one, and that mix is a parse error for reasons unrelated to the trailing comma.
        assertParses("let base = ({x: 1})\n{ ...base, }", "spread as the last entry")
        assertParses("let xs = [1, 2]\n{ r: [...xs, 3,] }", "spread inside an array, trailing comma")
    }

    @Test
    fun `trailing comma combines with F02 newline separators`() {
        assertParses("{\n  a: 1\n  b: 2,\n}", "newline-separated properties, comma before the closer")
    }

    // ── the limit: a separator, never an empty element ──

    @Test
    fun `a doubled comma is still an error`() {
        assertRejects("{ r: [1,,2] }", "`[1,,2]` is a dropped value")
        assertRejects("{ a: 1,, b: 2 }", "the same inside an object")
        assertRejects("{ r: max(1,,2) }", "and in an argument list")
    }

    @Test
    fun `a leading comma is still an error`() {
        assertRejects("{ r: [,1] }", "nothing precedes the separator")
    }

    @Test
    fun `a comma alone is not a literal`() {
        assertRejects("{ r: [,] }", "an array of one empty element is not a thing")
    }

    // ── unchanged behaviour ──

    @Test
    fun `literals without a trailing comma still parse`() {
        assertParses("{ r: [1, 2, 3] }", "the form the whole corpus uses")
        assertParses("{ a: 1, b: 2 }", "object without a trailing comma")
        assertParses("{ r: [] }", "empty array")
        assertParses("{ }", "empty object")
    }
}
