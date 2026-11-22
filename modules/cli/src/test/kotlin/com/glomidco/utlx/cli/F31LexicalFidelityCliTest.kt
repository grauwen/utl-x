package com.glomidco.utlx.cli

import com.glomidco.utlx.cli.commands.TransformCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * F31 — `--preserve-numeric-lexeme`, end to end through the readers.
 *
 * The predicate's own boundaries are pinned in core's `F31LexicalFidelityTest`. What that test
 * cannot see is the wiring: whether the flag actually reaches `ParseOptions`, whether each reader
 * consults it, and whether the formats that are deliberately *excluded* stayed excluded.
 *
 * The scope is the [com.glomidco.utlx.core.format.ExactNumber.inferredOrNull] domain — the readers
 * that must guess whether free text is numeric. **JSON is not in it**, by design: its grammar has
 * already typed the token, so preserving the lexeme there would emit `"1e3"` with quotes, trading
 * a lost notation for a changed type. That exclusion is asserted here, not just documented, because
 * it is the kind of thing a later "make it consistent" change would quietly undo.
 */
class F31LexicalFidelityCliTest {

    @TempDir
    lateinit var tempDir: Path

    /** Transform [inputText] (named `in.<ext>`) with [script], returning the output text. */
    private fun run(script: String, ext: String, inputText: String, vararg extra: String): String {
        val tag = (script + ext + inputText + extra.joinToString()).hashCode()
        val input = tempDir.resolve("in-$tag.$ext").toFile().apply { writeText(inputText) }
        val scriptFile = tempDir.resolve("s-$tag.utlx").toFile().apply { writeText(script) }
        val out = tempDir.resolve("out-$tag.txt").toFile()
        val args = arrayOf(scriptFile.absolutePath, input.absolutePath, "-o", out.absolutePath) + extra
        val result = TransformCommand.execute(args)
        assertTrue(result is CommandResult.Success, "expected success, got $result")
        return out.readText().trim()
    }

    private val toJson = { fmt: String -> """
        %utlx 1.0
        input $fmt
        output json
        ---
        ${if (fmt == "csv") "\$input[0]" else "\$input.r"}
    """.trimIndent() }

    private val xmlIn = "<r><a>-0</a><b>1e3</b><c>+2</c><d>002</d><e>100.10</e></r>"
    private val csvIn = "a,b,c,d,e\n-0,1e3,+2,002,100.10\n"
    private val yamlIn = "a: -0\nb: 1e3\nc: +2\nd: 002\ne: 100.10\n"

    private fun csvScript() = """
        %utlx 1.0
        input csv
        output json
        ---
        ${'$'}input[0]
    """.trimIndent()

    private fun script(fmt: String, root: String) = """
        %utlx 1.0
        input $fmt
        output json
        ---
        ${'$'}input$root
    """.trimIndent()

    // ── the flag changes the three shapes, in every free-text reader ──

    @Test
    fun `XML keeps the written form when asked`() {
        val off = run(script("xml", ".r"), "xml", xmlIn)
        val on = run(script("xml", ".r"), "xml", xmlIn, "--preserve-numeric-lexeme")
        assertNotEquals(off, on, "the flag must actually reach the XML reader")
        assertTrue(off.contains("\"a\": 0") && off.contains("\"b\": 1000"),
            "default behaviour must be unchanged: $off")
        assertTrue(on.contains("\"a\": \"-0\"") && on.contains("\"b\": \"1e3\""),
            "negative zero and exponent notation must come back verbatim: $on")
    }

    @Test
    fun `CSV keeps the written form when asked`() {
        val off = run(csvScript(), "csv", csvIn)
        val on = run(csvScript(), "csv", csvIn, "--preserve-numeric-lexeme")
        assertNotEquals(off, on, "the flag must actually reach the CSV reader")
        assertTrue(off.contains("\"a\": 0") && off.contains("\"b\": 1000"), off)
        assertTrue(on.contains("\"a\": \"-0\"") && on.contains("\"b\": \"1e3\""), on)
    }

    @Test
    fun `YAML keeps the written form when asked`() {
        val off = run(script("yaml", ""), "yaml", yamlIn)
        val on = run(script("yaml", ""), "yaml", yamlIn, "--preserve-numeric-lexeme")
        assertNotEquals(off, on, "the flag must actually reach the YAML reader")
        assertTrue(off.contains("\"a\": 0") && off.contains("\"b\": 1000"), off)
        assertTrue(on.contains("\"a\": \"-0\"") && on.contains("\"b\": \"1e3\""), on)
    }

    // ── the written plus: one format differed, and that is why it is in the predicate ──

    @Test
    fun `a written plus already survives XML and CSV`() {
        // B39 declines a leading `+` as non-canonical, so `+2` has always been a string in the
        // free-text formats. Asserted in BOTH modes: this is the invariant the F31 flag must not
        // disturb while fixing the format that behaved differently.
        for (extra in listOf(emptyArray<String>(), arrayOf("--preserve-numeric-lexeme"))) {
            assertTrue(run(script("xml", ".r"), "xml", xmlIn, *extra).contains("\"c\": \"+2\""),
                "XML, mode=${extra.toList()}")
            assertTrue(run(csvScript(), "csv", csvIn, *extra).contains("\"c\": \"+2\""),
                "CSV, mode=${extra.toList()}")
        }
    }

    @Test
    fun `YAML was the one format that dropped a written plus`() {
        // SnakeYAML reads `+2` as an INT, so YAML alone normalised `+2` to `2` — the same
        // cross-format divergence B35 was written to end, in a shape B35 did not cover. The flag
        // closes it; the default is left alone because changing it would be a silent type change.
        assertTrue(run(script("yaml", ""), "yaml", yamlIn).contains("\"c\": 2"),
            "default YAML behaviour is documented, not accidental")
        assertTrue(run(script("yaml", ""), "yaml", yamlIn, "--preserve-numeric-lexeme")
            .contains("\"c\": \"+2\""), "under the flag YAML agrees with XML and CSV")
    }

    // ── the narrowness guarantee ──

    @Test
    fun `nothing else moves`() {
        // B39's leading-zero identifier and B35's decimal scale must read identically in both
        // modes. If the flag widened to these, every arithmetic script under it would break.
        for (extra in listOf(emptyArray<String>(), arrayOf("--preserve-numeric-lexeme"))) {
            val out = run(script("xml", ".r"), "xml", xmlIn, *extra)
            assertTrue(out.contains("\"d\": \"002\""), "B39 leading zero, mode=${extra.toList()}: $out")
            assertTrue(out.contains("\"e\": 100.10"), "B35 scale, mode=${extra.toList()}: $out")
        }
    }

    // ── the deliberate exclusion ──

    @Test
    fun `JSON is not affected by the flag`() {
        val json = """{"a":-0,"b":1e3,"c":100.10}"""
        val s = """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
        """.trimIndent()
        val off = run(s, "json", json)
        val on = run(s, "json", json, "--preserve-numeric-lexeme")
        assertEquals(off, on,
            "JSON's grammar already typed the token; keeping the lexeme would add quotes, which is " +
            "a changed type rather than a preserved form. See B65.")
        assertTrue(on.contains("\"a\": 0") && on.contains("\"b\": 1000"), on)
    }

    // ── the cost the option carries ──

    @Test
    fun `under the flag a preserved value is a string, so arithmetic needs toNumber`() {
        // Stated as a test because it is the whole price of option A, and a reader who turns the
        // flag on needs to find it written down somewhere executable.
        val s = """
            %utlx 1.0
            input xml
            output json
            ---
            { n: toNumber(${'$'}input.r.b), raw: ${'$'}input.r.b }
        """.trimIndent()
        val on = run(s, "xml", xmlIn, "--preserve-numeric-lexeme")
        assertTrue(on.contains("\"n\": 1000"), "toNumber recovers the value: $on")
        assertTrue(on.contains("\"raw\": \"1e3\""), "the written form is still there: $on")
    }

    // ── the round-trip the option exists for ──

    @Test
    fun `an XML round-trip is byte-identical under the flag`() {
        val s = """
            %utlx 1.0
            input xml
            output xml
            ---
            { r: ${'$'}input.r }
        """.trimIndent()
        val on = run(s, "xml", xmlIn, "--preserve-numeric-lexeme")
        for (lexeme in listOf("-0", "1e3", "+2", "002", "100.10")) {
            assertTrue(on.contains(">$lexeme<"), "'$lexeme' must be rebuilt verbatim: $on")
        }
    }
}
