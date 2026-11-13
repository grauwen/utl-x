package com.glomidco.utlx.core.parser

import com.glomidco.utlx.core.interpreter.Interpreter
import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B56 — **a reserved word is a legal property name.**
 *
 * The object-literal parser always allowed it (`{ template: v, match: v, input: v }`), but the
 * path steps required an `IDENTIFIER` token, so a document could be *written* with an `input`
 * element and not *read* back:
 *
 * ```
 * { input: 1 }      // {"input":1}
 * $input.input      // Parse errors: Expected property name
 * ```
 *
 * That is not a theoretical gap for a format-agnostic language: `input`/`output` appear in
 * workflow definitions (`<input>` is an HTML element), `return` in tax and financial XML, and
 * `map`, `filter`, `json`, `xml`, `as` are all perfectly ordinary element names. A name the
 * *source system* chose must not collide with our grammar.
 *
 * The change is strictly additive — every program that parsed before parses identically, and
 * only previously-rejected ones become valid — so the tests that matter most are the ones below
 * asserting that the neighbouring syntax still means what it did.
 */
class B56KeywordPropertyNameTest {

    private fun eval(body: String, input: UDM): UDM {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        val program = when (val r = Parser(Lexer(src).tokenize(), src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed for `$body`: ${r.errors}")
        }
        return Interpreter().execute(program, input).toUDM()
    }

    private fun scalarOf(result: UDM): Any? =
        ((result as UDM.Object).properties["r"] as UDM.Scalar).value

    /**
     * Exactly the set the object literal accepts — `Token.isKeyword()`. A table, because this
     * defect was "one list was updated and the other was not", and the next gap will be a word
     * somebody adds to the lexer without revisiting either.
     */
    private val keywords = listOf(
        "input", "output", "template", "match", "function", "let", "in", "if", "else",
        "try", "catch", "map", "filter", "reduce", "apply", "import", "as", "auto",
        "xml", "json", "csv", "yaml", "xsd", "jsch", "avro", "proto", "odata", "osch", "tsch"
    )

    @Test
    fun `every keyword can be read as a property`() {
        val doc = UDM.Object(keywords.associateWith { UDM.Scalar(it.length.toLong()) })
        val failures = mutableListOf<String>()
        for (k in keywords) {
            val got = runCatching { scalarOf(eval("{ r: \$input.$k }", doc)) }
            if (got.isFailure || got.getOrNull() != k.length.toLong()) {
                failures.add("$k -> ${got.exceptionOrNull()?.message ?: got.getOrNull()}")
            }
        }
        assertEquals(emptyList(), failures, "these keywords are still unreadable as properties")
    }

    @Test
    fun `every keyword can be read through safe navigation`() {
        // `?.` had its own `consume(IDENTIFIER, …)` line and would otherwise have been missed.
        val doc = UDM.Object(keywords.associateWith { UDM.Scalar(it.length.toLong()) })
        val failures = mutableListOf<String>()
        for (k in keywords) {
            val got = runCatching { scalarOf(eval("{ r: \$input?.$k }", doc)) }
            if (got.isFailure || got.getOrNull() != k.length.toLong()) failures.add(k)
        }
        assertEquals(emptyList(), failures, "these keywords fail after '?.'")
    }

    @Test
    fun `a keyword property chains like any other`() {
        val doc = UDM.Object.of("input" to UDM.Object.of("output" to UDM.Scalar(7L)))
        assertEquals(7L, scalarOf(eval("{ r: \$input.input.output }", doc)))
    }

    @Test
    fun `write and read are symmetric`() {
        // The asymmetry *is* the bug: emitting `{ input: 1 }` always worked.
        val emitted = eval("{ input: 1 }", UDM.Object.of()) as UDM.Object
        assertTrue("input" in emitted.properties)
        assertEquals(1L, ((emitted.properties["input"]) as UDM.Scalar).value)
        assertEquals(1L, scalarOf(eval("{ r: \$input.input }", emitted)))
    }

    // ── what must not change ──

    @Test
    fun `the literals stay rejected, as they are in object literals too`() {
        // `{ true: 1 }` is also a parse error, so there is no asymmetry here to fix. Widening to
        // these would be a separate decision; this test records that it was not taken.
        for (lit in listOf("true", "false", "null")) {
            val src = "%utlx 1.0\ninput json\noutput json\n---\n{ r: \$input.$lit }"
            val r = Parser(Lexer(src).tokenize(), src).parse()
            assertTrue(r is ParseResult.Failure, "`\$input.$lit` should not parse")
        }
    }

    @Test
    fun `wildcard, attribute and index steps are unaffected`() {
        // These are the continuations a looser property rule could have swallowed: each is a
        // distinct token matched before the property name is read.
        val doc = UDM.Object.of(
            "a" to UDM.Object.of("b" to UDM.Scalar(1L)),
            "items" to UDM.Array(listOf(UDM.Object.of("n" to UDM.Scalar("x"))))
        )
        assertEquals("[1]", (eval("{ r: \$input.a.* }", doc) as UDM.Object)
            .properties["r"].let { (it as UDM.Array).elements.map { e -> (e as UDM.Scalar).value } }
            .toString())
        assertEquals("x", ((eval("{ r: \$input.items[0].n }", doc) as UDM.Object)
            .properties["r"] as UDM.Scalar).value)
    }

    @Test
    fun `keywords still work as keywords`() {
        // The whole risk of this change is a keyword being eaten as a property somewhere it was
        // doing its job. These use the words in keyword position in the same programs.
        val doc = UDM.Object.of(
            "a" to UDM.Scalar(1L),
            "items" to UDM.Array(listOf(UDM.Scalar(2L), UDM.Scalar(3L)))
        )
        assertEquals("y", scalarOf(eval("{ r: if (\$input.a == 1) \"y\" else \"n\" }", doc)))
        assertEquals(1L, scalarOf(eval("{ let x = \$input.a, r: x }", doc)))
        val mapped = (eval("{ r: map(\$input.items, (i) -> i) }", doc) as UDM.Object)
            .properties["r"] as UDM.Array
        assertEquals(2, mapped.elements.size)
    }
}
