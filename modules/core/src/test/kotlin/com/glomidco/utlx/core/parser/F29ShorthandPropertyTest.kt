package com.glomidco.utlx.core.parser

import com.glomidco.utlx.core.ast.Expression
import com.glomidco.utlx.core.lexer.Lexer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * F29 — **shorthand object properties: `{ name }` means `{ name: name }`.**
 *
 * `syntax.md` documented the form on both branches with no caveat, and it did not parse
 * (`Expected ':' after property name`). Everything else in that section — computed keys, spread,
 * quoted keys — was genuine, so this was an isolated gap.
 *
 * It desugars at **parse time** to the node the explicit form already builds, so the interpreter,
 * `ASTCompiler` and `RuntimeOps` see nothing new. That is what makes it cheap compared with F28:
 * there is no second implementation of the semantics to keep in step.
 *
 * ### The exclusions are the interesting part
 *
 * Shorthand applies only to a plain identifier. The one that matters is **keywords**: B56 allows a
 * reserved word as a property name (`{ input: v }`), and bare `input` still resolves to the whole
 * input document — so `{ input }` would silently emit the entire document under the key `input`.
 * That is a data leak dressed as sugar, so keyword keys keep requiring their colon. `@attr`,
 * `%directive` and quoted names are excluded for weaker but similar reasons, and each is asserted
 * below rather than left to inspection.
 */
class F29ShorthandPropertyTest {

    private fun parse(body: String): ParseResult {
        val source = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        return Parser(Lexer(source).tokenize(), source).parse()
    }

    private fun assertParses(body: String, why: String): ParseResult.Success {
        val r = parse(body)
        assertIs<ParseResult.Success>(r, "$why — expected a parse, got: ${(r as? ParseResult.Failure)?.errors}")
        return r
    }

    private fun assertRejects(body: String, why: String) {
        assertTrue(parse(body) is ParseResult.Failure, "$why — expected a parse error, but it parsed")
    }

    // ── vacuity guard ──

    @Test
    fun `the harness parses valid input and rejects invalid input`() {
        assertParses("{ a: 1 }", "an explicit property")
        assertRejects("{ a: }", "a property with no value")
    }

    // ── the desugaring ──

    @Test
    fun `shorthand produces exactly the AST the explicit form produces`() {
        // The whole safety argument for "no engine change" is that these two are the same tree.
        val shorthand = assertParses("let name = \"A\"\n{ name }", "shorthand")
        val explicit = assertParses("let name = \"A\"\n{ name: name }", "explicit")

        fun soleProperty(r: ParseResult.Success): Pair<String?, Expression> {
            val block = r.program.body
            val obj = (block as? Expression.Block)?.expressions?.filterIsInstance<Expression.ObjectLiteral>()?.last()
                ?: block as Expression.ObjectLiteral
            val p = obj.properties.single()
            return p.key to p.value
        }

        val (shortKey, shortValue) = soleProperty(shorthand)
        val (longKey, longValue) = soleProperty(explicit)

        assertEquals(longKey, shortKey, "the key must match the explicit form")
        assertIs<Expression.Identifier>(shortValue, "shorthand's value must be an identifier reference")
        assertEquals(
            (longValue as Expression.Identifier).name, shortValue.name,
            "shorthand must reference the same name the explicit form does"
        )
    }

    @Test
    fun `the documented example parses`() {
        assertParses(
            "let name = \"Alice\"\nlet age = 30\n{\n  name,\n  age\n}",
            "the example syntax.md publishes"
        )
    }

    @Test
    fun `a single shorthand property parses`() {
        assertParses("let name = \"A\"\n{ name }", "one property")
    }

    @Test
    fun `shorthand mixes with explicit properties`() {
        assertParses("let name = \"A\"\nlet age = 2\n{ name, age: age + 1 }", "mixed form")
        assertParses("let name = \"A\"\nlet age = 2\n{ age: age + 1, name }", "mixed, shorthand last")
    }

    @Test
    fun `shorthand works with F02 newline separators`() {
        assertParses("let name = \"A\"\nlet age = 2\n{\n  name\n  age\n}", "newline-separated")
    }

    @Test
    fun `shorthand works with an F30 trailing comma`() {
        assertParses("let name = \"A\"\n{ name, }", "trailing comma after a shorthand")
    }

    @Test
    fun `shorthand coexists with a spread`() {
        assertParses("let base = ({x: 1})\nlet name = \"A\"\n{ ...base, name }", "spread then shorthand")
    }

    // ── the exclusions ──

    @Test
    fun `a keyword key still requires a colon`() {
        // The important one: `{ input }` would desugar to `input: input`, and bare `input` resolves
        // to the whole document — silently emitting the entire input under the key "input".
        assertRejects("{ input }", "keyword `input` must not be shorthand")
        assertRejects("{ match }", "nor any other keyword permitted as a property name by B56")
    }

    @Test
    fun `a keyword key with a colon still works, as B56 allows`() {
        assertParses("{ input: 1 }", "B56 must be unaffected")
        assertParses("{ match: 2 }", "likewise")
    }

    @Test
    fun `an attribute key still requires a colon`() {
        assertRejects("let id = \"x\"\n{ @id }", "`@id: id` would be coincidence, not intent")
    }

    @Test
    fun `a quoted key still requires a colon`() {
        assertRejects("let name = \"A\"\n{ \"name\" }", "a quoted name implies no variable")
    }

    @Test
    fun `a trailing token after a shorthand is still an error`() {
        assertRejects("let name = \"A\"\n{ name 5 }", "`{ name 5 }` is a mistake, not shorthand")
    }

    // ── unchanged behaviour ──

    @Test
    fun `explicit properties are unaffected`() {
        assertParses("{ a: 1, b: 2 }", "the form the whole corpus uses")
        assertParses("let k = \"x\"\n{ [k]: 1 }", "computed keys")
        assertParses("{ }", "empty object")
    }
}
