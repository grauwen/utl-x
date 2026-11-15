package com.glomidco.utlx.core.types

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B62 — **a duplicate key at the same level silently discards one of the mappings.**
 *
 * ```
 * { id: $input.a.id, id: $input.b.id }   =>  {"id":"FROM-B"}
 * validate => passed        lint => no issues found
 * ```
 *
 * Two deliberate mappings, one output, and nothing anywhere said so. That is the worst shape for
 * a drag & drop mapper: the user makes a mapping, sees it in the UI, and it is absent from the
 * output with no diagnostic to explain why.
 *
 * Reported as a **type warning** rather than a parse error: it is non-breaking (no script in the
 * corpus has one — the two candidates found by a scan were an if/else pair and two array
 * elements), `--strict` promotes it to an error, and the daemon already turns type errors into
 * LSP diagnostics, which is how the mapper gets it at drop time.
 *
 * ### Same level only
 *
 * The check is deliberately narrow. These three are legitimate and must stay silent:
 *
 *   - `{ ...base, id: "NEW" }`  — a spread followed by a deliberate override
 *   - `{ @id: "a", id: "b" }`   — attributes and properties are separate namespaces
 *   - `{ a: { id: 1 }, b: { id: 2 } }` — different objects, not a duplicate at all
 *
 * so the detector tracks only *explicit, non-spread, non-computed* keys, pairing each with its
 * attribute flag.
 */
class B62DuplicateObjectKeyTest {

    private fun check(body: String): List<TypeError> {
        val source = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        val parsed = Parser(Lexer(source).tokenize(), source).parse()
        val program = (parsed as? ParseResult.Success)?.program
            ?: error("fixture does not parse: ${(parsed as ParseResult.Failure).errors}")
        // core's own StandardLibrary, not :stdlib's — the dependency runs stdlib -> core, so a
        // core test cannot see the real registry (see B52). An empty name set is fine here:
        // these fixtures call no functions.
        val result = TypeChecker(StandardLibrary(), emptySet()).check(program)
        return (result as? TypeCheckResult.Failure)?.errors ?: emptyList()
    }

    private fun duplicateWarnings(body: String) =
        check(body).filter { it.message.contains("Duplicate", ignoreCase = true) }

    // ── vacuity guard ──

    @Test
    fun `the checker runs and a clean script produces no duplicate warning`() {
        assertTrue(duplicateWarnings("{ a: 1, b: 2 }").isEmpty(), "a clean object must be silent")
    }

    // ── the defect ──

    @Test
    fun `a duplicate key at the same level is reported`() {
        val w = duplicateWarnings("{ id: 1, id: 2 }")
        assertEquals(1, w.size, "expected exactly one duplicate warning, got: ${w.map { it.message }}")
        assertTrue(w.single().message.contains("'id'"), "the message must name the key: ${w.single().message}")
    }

    @Test
    fun `the warning points at the second occurrence`() {
        val w = duplicateWarnings("{\n  id: 1,\n  id: 2\n}").single()
        assertEquals(7, w.location.line, "should flag the later property, on line 7 of the full script")
    }

    @Test
    fun `three occurrences report twice`() {
        assertEquals(
            2, duplicateWarnings("{ id: 1, id: 2, id: 3 }").size,
            "each repeat after the first is its own warning"
        )
    }

    @Test
    fun `a duplicate attribute is reported too`() {
        val w = duplicateWarnings("{ @id: \"a\", @id: \"b\" }")
        assertEquals(1, w.size, "attributes collide with each other")
        assertTrue(w.single().message.contains("attribute"), "and are named as attributes")
    }

    // ── same level ONLY: these must stay silent ──

    @Test
    fun `the same name in different nested objects is not a duplicate`() {
        assertTrue(
            duplicateWarnings("{ a: { id: 1 }, b: { id: 2 } }").isEmpty(),
            "two objects each with their own `id` is the normal shape of a mapping"
        )
    }

    @Test
    fun `a spread followed by an override is not a duplicate`() {
        assertTrue(
            duplicateWarnings("let base = ({id: \"OLD\"})\n{ ...base, id: \"NEW\" }").isEmpty(),
            "overriding a spread field is the documented way to rename one entry"
        )
    }

    @Test
    fun `an attribute and a property of the same name are not duplicates`() {
        assertTrue(
            duplicateWarnings("{ @id: \"a\", id: \"b\" }").isEmpty(),
            "@id and id live in different namespaces — XML attributes vs element content"
        )
    }

    @Test
    fun `computed keys are not flagged`() {
        assertTrue(
            duplicateWarnings("let k = \"x\"\n{ [k]: 1, [k]: 2 }").isEmpty(),
            "a computed key cannot be resolved statically, so silence is the honest answer"
        )
    }

    @Test
    fun `keys repeated across sibling array elements are not duplicates`() {
        assertTrue(
            duplicateWarnings("{ rows: [ { id: 1 }, { id: 2 } ] }").isEmpty(),
            "each array element is its own object"
        )
    }
}
