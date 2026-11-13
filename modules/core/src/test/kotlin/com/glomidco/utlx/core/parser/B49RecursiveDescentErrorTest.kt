package com.glomidco.utlx.core.parser

import com.glomidco.utlx.core.lexer.Lexer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B49 — **`..` answers for itself instead of failing as a bare syntax error.**
 *
 * `TokenType.DOTDOT` has existed since the lexer was written, commented `// .. (recursive
 * descent)`, and the specification documented `input..ProductCode`. The parser never had a
 * branch for it, so the token was produced and met nothing: the result was
 * `Expected expression` at a column that explains nothing — a missing feature wearing the
 * costume of a syntax error.
 *
 * The decision was **not to implement descent**, for reasons recorded in the report: the
 * relational case (SAP IDoc segments, EDI, CSV exports with foreign keys) is already served by
 * `nestBy()`, a step over repeated elements already projects across them (B48), and breaking
 * loudly when a source changes shape is usually what a mapping contract wants — descent trades
 * that away for a convenience that belongs to query languages.
 *
 * Deleting the token would have left a generic "Expected property name". Keeping it and
 * answering here costs a few lines and tells the author what to do instead, which is the same
 * reasoning as B38's refusal naming `isFinite`: if you are going to say no, say what to do.
 */
class B49RecursiveDescentErrorTest {

    private fun parseErrors(body: String): List<String> {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        return when (val r = Parser(Lexer(src).tokenize(), src).parse()) {
            is ParseResult.Success -> emptyList()
            is ParseResult.Failure -> r.errors.map { it.message }
        }
    }

    @Test
    fun `a descent path is refused by name, not as a generic syntax error`() {
        val errors = parseErrors("{ r: \$input..name }")
        assertTrue(errors.isNotEmpty(), "`..` must not parse")
        val message = errors.first()
        assertTrue("Recursive descent" in message, "should name the construct: $message")
        assertTrue("not supported" in message, message)
    }

    @Test
    fun `the message names both alternatives`() {
        // Without these the author is told no and left to guess. `nestBy` is the answer for the
        // relational shape; naming the steps is the answer for a known depth.
        val message = parseErrors("{ r: \$input..name }").first()
        assertTrue("nestBy" in message, "should point at the relational answer: $message")
        assertTrue("name the steps" in message, "should point at the explicit path: $message")
    }

    @Test
    fun `it reports at the dots, not somewhere downstream`() {
        // The old failure surfaced wherever the parser happened to give up. This asserts the
        // location is the construct itself — column 12 of `{ r: $input..name }`.
        val src = "%utlx 1.0\ninput json\noutput json\n---\n{ r: \$input..name }"
        val result = Parser(Lexer(src).tokenize(), src).parse()
        assertTrue(result is ParseResult.Failure)
        val location = (result as ParseResult.Failure).errors.first().location
        assertEquals(5, location.line)
        assertEquals(12, location.column)
    }

    @Test
    fun `a single dot is unaffected`() {
        // The branch must not swallow ordinary member access; `..` is a distinct token, but a
        // careless guard could match the first dot of any path.
        assertEquals(emptyList(), parseErrors("{ r: \$input.a.b.c }"))
        assertEquals(emptyList(), parseErrors("{ r: \$input.a.* }"))
        assertEquals(emptyList(), parseErrors("{ r: \$input.a.@attr }"))
    }

    @Test
    fun `the spread operator is unaffected`() {
        // `...` lexes to SPREAD by maximal munch and never reaches this branch — the three-way
        // split (`...` / `..` / `.`) that made `..` unambiguous in the first place. If the
        // lexer's longest-match ordering were disturbed, `...base` would arrive here as DOTDOT
        // followed by DOT and this would start failing with the descent message.
        assertEquals(emptyList(), parseErrors("{ ...\$input, y: 2 }"))
        assertEquals(emptyList(), parseErrors("let base = { x: 1 }\n{ ...base, y: 2 }"))
    }
}
