package com.glomidco.utlx.core.types

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B54 — **the hyphen trap, detected instead of merely documented.**
 *
 * `-` is a legal identifier character, because JSON keys, XML element and attribute names and
 * YAML keys all permit hyphens and `$input.content-type` has to mean that key. The cost is that
 * `$input.total-discount` reads a key nobody defined and yields `null` — silently — while the
 * author believes they wrote arithmetic. Documentation only helps someone who reads it *before*
 * making the mistake; this is found at runtime, in output, by someone who did not.
 *
 * Two things had to be true for a diagnostic to be possible, and neither was:
 *
 *  - `$input` had to have a known shape. It was hard-coded to [UTLXType.Any], with a note
 *    saying it would "be refined by format parser" — nothing ever refined it, so the
 *    `MemberAccess` branch answered `Any` for every property and the "does not exist" check
 *    the type system already contained could never fire on input data.
 *  - `--schema` had to reach this checker. It did not: `ValidateCommand` parsed it and handed
 *    it to `AdvancedTypeInference` in `:modules:analysis`, a different type system that only
 *    infers the *output* shape.
 *
 * With the schema bridged in, the existing check fires and this adds the sentence that makes
 * it actionable. The evidence bar is deliberately high — the name must contain a hyphen, must
 * not be a property, and **both halves must be** — so a document that genuinely has a
 * `content-type` key never reaches it, because the property exists.
 */
class B54SubtractionHintTest {

    private fun errorsFor(body: String, inputType: UTLXType?): List<String> {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        val program = when (val r = Parser(Lexer(src).tokenize(), src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        val result = TypeChecker(StandardLibrary(), setOf("upper")).check(program, inputType)
        return when (result) {
            is TypeCheckResult.Success -> emptyList()
            is TypeCheckResult.Failure -> result.errors.map { it.message }
        }
    }

    /** `{ total: Number, discount: Number, name: String }` — as a schema would give it. */
    private val schema = UTLXType.Object(
        mapOf(
            "total" to UTLXType.Number,
            "discount" to UTLXType.Number,
            "name" to UTLXType.String
        )
    )

    @Test
    fun `an intended subtraction is named as such`() {
        val errors = errorsFor("{ r: \$input.total-discount }", schema)
        assertEquals(1, errors.size, "expected exactly one error, got $errors")
        assertTrue("does not exist" in errors[0], errors[0])
        assertTrue("Did you mean 'total - discount'" in errors[0],
            "the hint is what makes this actionable: ${errors[0]}")
    }

    @Test
    fun `real subtraction is not flagged`() {
        assertEquals(emptyList(), errorsFor("{ r: \$input.total - \$input.discount }", schema))
    }

    @Test
    fun `an existing hyphenated key is not flagged at all`() {
        // The commonest legitimate case — `content-type`, `xml-lang`. The property exists, so
        // the check never runs and the hint never appears.
        val withKebab = UTLXType.Object(mapOf("content-type" to UTLXType.String))
        assertEquals(emptyList(), errorsFor("{ r: \$input.content-type }", withKebab))
    }

    // ── the hint must stay quiet unless the evidence is there ──

    @Test
    fun `no hint when only one half is a property`() {
        val errors = errorsFor("{ r: \$input.total-nosuch }", schema)
        assertEquals(1, errors.size)
        assertTrue("does not exist" in errors[0])
        assertTrue("Did you mean" !in errors[0],
            "only one half resolves, so subtraction is a guess: ${errors[0]}")
    }

    @Test
    fun `no hint when neither half is a property`() {
        val errors = errorsFor("{ r: \$input.alpha-beta }", schema)
        assertEquals(1, errors.size)
        assertTrue("Did you mean" !in errors[0], errors[0])
    }

    @Test
    fun `no hint for a plain missing property`() {
        val errors = errorsFor("{ r: \$input.nosuch }", schema)
        assertEquals(1, errors.size)
        assertTrue("Did you mean" !in errors[0], errors[0])
    }

    @Test
    fun `a multi-hyphen name is left alone`() {
        // `a-b-c` has no single obvious reading; guessing one would be worse than silence.
        val three = UTLXType.Object(
            mapOf("a" to UTLXType.Number, "b" to UTLXType.Number, "c" to UTLXType.Number)
        )
        val errors = errorsFor("{ r: \$input.a-b-c }", three)
        assertEquals(1, errors.size)
        assertTrue("Did you mean" !in errors[0], errors[0])
    }

    // ── without a schema, nothing is asserted ──

    @Test
    fun `with no input type, property checks stay silent`() {
        // `Any` means "I don't know", so claiming a property is missing would be a false alarm.
        assertEquals(emptyList(), errorsFor("{ r: \$input.total-discount }", null))
        assertEquals(emptyList(), errorsFor("{ r: \$input.anythingAtAll }", null))
    }
}
