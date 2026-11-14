package com.glomidco.utlx.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.glomidco.utlx.cli.commands.FunctionsCommand
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CB05 — **`functions list --category` ignored the filter in text output.**
 *
 * `listFunctions()` built the filtered list and handed it to `printTextList(functions, registry)`,
 * which then iterated `registry.categories` and never read `functions`. So the JSON path filtered
 * correctly while the default text path printed the whole library — 754 lines for a one-category
 * request — under a header claiming the full total. The two output formats answered different
 * questions, and the text one is the path a human types.
 *
 * The compiler had said so for as long as the bug existed: `Parameter 'functions' is never used`.
 * That warning sat inside a build emitting 256 of them, which is the reason it went unread and the
 * reason the 1.4.0 cleanup pass went looking.
 *
 * The fix groups the list it is given. These tests pin both halves of the contract: the filter is
 * honoured, **and** an unfiltered listing still shows everything — because "filter correctly" is
 * trivially satisfiable by printing nothing, and "print everything" by ignoring the filter again.
 */
class CB05FunctionsCategoryFilterTest {

    /** Capture stdout while the command runs; FunctionsCommand prints rather than returning text. */
    private fun capture(vararg args: String): String {
        val buffer = ByteArrayOutputStream()
        val original = System.out
        try {
            System.setOut(PrintStream(buffer, true))
            val result = FunctionsCommand.execute(arrayOf(*args))
            assertTrue(
                result is CommandResult.Success,
                "expected success for 'functions ${args.joinToString(" ")}', got $result"
            )
        } finally {
            System.setOut(original)
        }
        return buffer.toString()
    }

    /** A category heading looks like `Array (66):` at the start of a line. */
    private val headingRegex = Regex("""^(.+) \((\d+)\):$""", RegexOption.MULTILINE)

    private fun headings(output: String): List<String> =
        headingRegex.findAll(output).map { it.groupValues[1] }.toList()

    // ── vacuity guards: the harness must really be reading a populated registry ──

    @Test
    fun `an unfiltered listing shows many categories and many functions`() {
        val out = capture("list")
        val found = headings(out)
        assertTrue(found.size > 10, "expected the whole library, got ${found.size} categories")
        assertTrue(out.contains("Array ("), "expected an Array category heading in:\n$out")
        assertTrue(out.contains("String ("), "expected a String category heading")
    }

    @Test
    fun `the Array category is not empty, so filtering to it proves something`() {
        val out = capture("list", "--category", "Array")
        val count = headingRegex.find(out)?.groupValues?.get(2)?.toInt() ?: 0
        assertTrue(count > 20, "Array should hold a substantial number of functions, saw $count")
    }

    // ── the defect ──

    @Test
    fun `filtering by category prints only that category`() {
        val out = capture("list", "--category", "Array")
        assertEquals(
            listOf("Array"), headings(out),
            "text output must contain exactly one category heading when --category is given"
        )
    }

    @Test
    fun `filtering by category does not print the whole library`() {
        val all = capture("list")
        val filtered = capture("list", "--category", "Array")
        assertTrue(
            filtered.lines().size * 4 < all.lines().size,
            "a one-category listing (${filtered.lines().size} lines) should be far shorter " +
                "than the full library (${all.lines().size} lines)"
        )
    }

    @Test
    fun `the header reports the filtered count, not the library total`() {
        val out = capture("list", "--category", "Array")
        val header = out.lineSequence().first()
        val arrayCount = headingRegex.find(out)!!.groupValues[2]
        assertTrue(
            header.contains("$arrayCount of "),
            "header should read '<filtered> of <total>', was: $header"
        )
    }

    @Test
    fun `an unfiltered listing keeps the plain total in its header`() {
        val header = capture("list").lineSequence().first()
        assertTrue(
            header.contains("total"),
            "an unfiltered listing should report the library total, was: $header"
        )
    }

    // ── the two output paths must now agree ──

    @Test
    fun `text and json agree on how many functions a category has`() {
        val text = capture("list", "--category", "Array")
        // Sum every heading rather than reading the first. Reading only the first heading does not
        // discriminate: on the buggy output "Array" sorts first, so its count matched the JSON
        // count while the listing below it still held all 705 functions. Summing catches that.
        val textCount = headingRegex.findAll(text).sumOf { it.groupValues[2].toInt() }

        val json = capture("list", "--category", "Array", "--format", "json")
        // Parse it. Counting `"name":` occurrences does not work: every entry's `parameters`
        // array carries its own `name`, so a regex count returns parameters as well as functions
        // (209 rather than 66) — which is how this test first failed against a correct fix.
        val jsonCount = ObjectMapper().readTree(json).size()

        assertEquals(
            textCount, jsonCount,
            "text said $textCount functions, json said $jsonCount — the formats must not diverge"
        )
    }

    // ── edges ──

    @Test
    fun `an unknown category prints no headings and says so`() {
        val out = capture("list", "--category", "NoSuchCategory")
        assertEquals(emptyList(), headings(out), "no category heading should be printed")
        assertTrue(
            out.contains("No functions matched", ignoreCase = true),
            "an empty result should say so rather than printing an empty list:\n$out"
        )
    }
}
