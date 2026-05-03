package org.apache.utlx.core.parser

import org.apache.utlx.core.lexer.Lexer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * F02: Let Binding Separator Consistency
 *
 * Tests that newlines work as separators everywhere:
 * - Inside object literals (previously required commas)
 * - Before array returns (previously required semicolons)
 * - In lambda bodies (previously required commas)
 * - Backward compatibility: commas and semicolons still work
 */
class F02LetBindingSeparatorTest {

    private val header = "%utlx 1.0\ninput json\noutput json\n---\n"

    private fun parse(source: String): ParseResult {
        val tokens = Lexer(header + source).tokenize()
        return Parser(tokens).parse()
    }

    private fun assertParses(source: String, description: String = "") {
        val result = parse(source)
        assertTrue(result is ParseResult.Success, "Expected successful parse $description but got: $result")
    }

    private fun assertFails(source: String, description: String = "") {
        val result = parse(source)
        assertTrue(result is ParseResult.Failure, "Expected parse failure $description but parsed successfully")
    }

    // ── Newlines inside object literals (F02 fix) ──

    @Test
    fun `let inside object with newlines only`() {
        assertParses("""
            {
              let a = 1
              let b = 2
              result: a + b
            }
        """.trimIndent(), "let inside object with newlines")
    }

    @Test
    fun `single let inside object with newline`() {
        assertParses("""
            {
              let x = 42
              result: x
            }
        """.trimIndent(), "single let inside object with newline")
    }

    @Test
    fun `chained lets inside object with newlines`() {
        assertParses("""
            {
              let a = 1
              let b = a + 1
              let c = b + 1
              result: c
            }
        """.trimIndent(), "chained lets inside object")
    }

    // ── Newlines before array return (F02 fix) ──

    @Test
    fun `let before array return with newlines`() {
        assertParses("""
            let a = 1
            let b = 2
            [a, b]
        """.trimIndent(), "let before array return")
    }

    @Test
    fun `single let before array return`() {
        assertParses("""
            let x = 42
            [x, x + 1]
        """.trimIndent(), "single let before array")
    }

    // ── Same-line [ must still be index access ──

    @Test
    fun `same-line bracket is index access`() {
        // arr[0] on same line must be index, not array literal
        assertParses("""
            let arr = [1, 2, 3]
            arr[0]
        """.trimIndent(), "same-line bracket is index")
    }

    @Test
    fun `property index access on same line`() {
        assertParses("""
            input.items[0]
        """.trimIndent(), "property index on same line")
    }

    // ── Backward compatibility: commas still work ──

    @Test
    fun `let inside object with commas still works`() {
        assertParses("""
            {let a = 1, let b = 2, result: a + b}
        """.trimIndent(), "commas backward compat")
    }

    @Test
    fun `let inside object with semicolons still works`() {
        assertParses("""
            {let a = 1; let b = 2; result: a + b}
        """.trimIndent(), "semicolons backward compat")
    }

    // ── Top-level lets (unchanged — always worked) ──

    @Test
    fun `top-level lets with newlines`() {
        assertParses("""
            let a = 1
            let b = 2
            {result: a + b}
        """.trimIndent(), "top-level lets")
    }

    @Test
    fun `let returning bare expression`() {
        assertParses("""
            let a = 10
            a + 1
        """.trimIndent(), "let returning bare expression")
    }

    // ── Edge cases ──

    @Test
    fun `let on same line as property without separator should fail`() {
        // No newline, no comma, no semicolon — must fail
        assertFails("""
            {let a = 1 result: a}
        """.trimIndent(), "no separator on same line")
    }

    // ── Newlines between properties (no commas) ──

    @Test
    fun `properties separated by newlines only`() {
        assertParses("""
            {
              name: "Alice"
              age: 30
              active: true
            }
        """.trimIndent(), "properties with newlines only")
    }

    @Test
    fun `map with let and properties separated by newlines`() {
        assertParses("""
            map([1, 2], (x) -> {
              let doubled = x * 2
              let taxed = doubled * 1.21
              product: doubled
              withTax: taxed
            })
        """.trimIndent(), "map with let + properties newlines")
    }

    // ── Mixed styles ──

    @Test
    fun `mixed newlines and commas`() {
        assertParses("""
            {
              let a = 1,
              let b = 2
              result: a + b
            }
        """.trimIndent(), "mixed newlines and commas")
    }
}
