package com.glomidco.utlx.cli

import com.glomidco.utlx.cli.commands.TransformCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F33 — `--format table`: output **presentation**, distinct from output **format**.
 *
 *     --to / --output-format    what the data IS      json, xml, csv — parseable, round-trippable
 *     --format                  how it is SHOWN       table — read once by a human, never parsed
 *
 * There is deliberately **no `-f`** here, though `lint` and `validate` have one: `-f` means *file*
 * across the ecosystem, and `transform` takes its script positionally. Pinned by a test below.
 *
 * The first attempt put `table` in `--to` and needed two special cases to survive: a refusal in
 * `parseInput` (every other `--to` value is also a valid `--from` value) and a substitution in the
 * `-e` header synthesis (`output table` is not in the `%utlx` grammar). Both are gone. The tests
 * that matter most here are therefore the ones that pin the AXIS, not the rendering: `--to table`
 * must fail with the fix, and `--format table` must work in every mode including the two that synthesize
 * a header.
 */
class F33PresentationTableTest {

    @TempDir
    lateinit var tempDir: Path

    /** Run transform, returning stdout-equivalent text written via -o. */
    private fun run(inputText: String, ext: String, vararg extra: String): String {
        val tag = (inputText + ext + extra.joinToString()).hashCode()
        val input = tempDir.resolve("in-$tag.$ext").toFile().apply { writeText(inputText) }
        val out = tempDir.resolve("out-$tag.txt").toFile()
        val args = arrayOf("-e", "\$input", "-i", input.absolutePath, "-o", out.absolutePath) + extra
        val result = TransformCommand.execute(args)
        assertTrue(result is CommandResult.Success, "expected success, got $result")
        return out.readText().trimEnd()
    }

    private fun failure(inputText: String, vararg extra: String): CommandResult {
        val input = tempDir.resolve("bad-${extra.joinToString().hashCode()}.json")
            .toFile().apply { writeText(inputText) }
        return TransformCommand.execute(
            arrayOf("-e", "\$input", "-i", input.absolutePath) + extra)
    }

    private val rows = """[{"sku":"X","qty":2},{"sku":"Y","qty":10}]"""

    // ── the axis: the point of the whole feature ──

    @Test
    fun `--to table is refused, and the error names the right flag`() {
        val r = failure(rows, "--to", "table")
        assertTrue(r is CommandResult.Failure, "a presentation must not be accepted as a format")
        val msg = (r as CommandResult.Failure).message
        assertTrue(msg.contains("--format table"), "the error must name the fix; got: $msg")
        assertTrue(msg.contains("presentation"), msg)
    }

    @Test
    fun `--format table works in expression mode, which synthesizes a header`() {
        // The mode that broke when `table` was a format: the synthesized `%utlx` header carried
        // `output table`, which the parser rejects. A presentation never reaches the header.
        val out = run(rows, "json", "--format", "table")
        assertTrue(out.startsWith("sku | qty"), out)
    }

    @Test
    fun `there is deliberately no -f short form on transform`() {
        // `-f` means a FILE in jq, kubectl, tar, make, grep and `docker build`, and this command
        // takes its script positionally — so `-f report.utlx data.json` would eat the script as a
        // format value and treat the DATA as the script, failing with "Expected '---' separator".
        // An unrelated error from correct muscle memory. lint/validate keep their pre-existing -f
        // because neither takes a script positionally.
        val r = failure(rows, "-f", "table")
        assertTrue(r is CommandResult.Failure, "-f must not be accepted here")
    }

    @Test
    fun `an unknown presentation is refused`() {
        val r = failure(rows, "--format", "wide")
        assertTrue(r is CommandResult.Failure)
        assertTrue((r as CommandResult.Failure).message.contains("Unknown --format"), r.message)
    }

    // ── the rendering ──

    @Test
    fun `an array of objects becomes a header, a rule and one row each`() {
        val lines = run(rows, "json", "--format", "table").lines()
        assertEquals("sku | qty", lines[0])
        assertEquals("-".repeat(lines[0].length), lines[1])
        assertEquals(listOf("X   | 2", "Y   | 10"), lines.drop(2))
    }

    @Test
    fun `a comma inside a cell is not a column break`() {
        // The regression that forced the rewrite. Composing CSV + prettyPrintCSV split on the
        // delimiter without honouring quoting, so this value became two columns and kept its
        // quotes. Any message, address or list in a cell would have been mangled.
        val out = run("""[{"id":"A","note":"one, two, three"}]""", "json", "--format", "table")
        assertTrue(out.endsWith("A  | one, two, three"), out)
        assertFalse(out.contains("\""), "quoting must not leak into the view: $out")
    }

    @Test
    fun `a missing key leaves a blank cell rather than shifting the row`() {
        val out = run("""[{"a":1,"b":2},{"a":3}]""", "json", "--format", "table")
        val lines = out.lines()
        assertEquals("a | b", lines[0])
        assertEquals("1 | 2", lines[2])
        // "3 |" and not "3": the separator stays so the reader sees a second column that is
        // EMPTY, rather than a row that looks one column short. Only the padding is trimmed, so
        // there is no trailing whitespace.
        assertEquals("3 |", lines[3])
        assertFalse(lines.any { it != it.trimEnd() }, "no line may carry trailing whitespace")
    }

    @Test
    fun `a nested value renders as compact JSON, never a Kotlin dump`() {
        val out = run("""[{"id":"A","tags":["x","y"]}]""", "json", "--format", "table")
        assertTrue(out.contains("""["x","y"]"""), out)
        assertFalse(out.contains("Scalar(") || out.contains("Array(elements"),
            "a UDM toString() dump is what the CSV route produced: $out")
    }

    @Test
    fun `numbers keep the scale they were written with`() {
        // Same path as every other output (B35), so the view cannot disagree with the data.
        val out = run("""[{"price":10.50},{"price":1234.5}]""", "json", "--format", "table")
        assertTrue(out.contains("10.50"), "trailing zero must survive: $out")
        assertTrue(out.contains("1234.5") && !out.contains("1234.50"), out)
    }

    @Test
    fun `a single object is one row`() {
        val out = run("""{"a":1,"b":"x"}""", "json", "--format", "table")
        assertEquals(listOf("a | b", "-----", "1 | x"), out.lines())
    }

    @Test
    fun `an array of scalars becomes one column`() {
        val out = run("""["a","bb","ccc"]""", "json", "--format", "table")
        assertEquals("value", out.lines()[0])
        assertEquals(listOf("a", "bb", "ccc"), out.lines().drop(2))
    }

    @Test
    fun `no rows renders nothing, not a bare header`() {
        assertEquals("", run("[]", "json", "--format", "table"))
    }
}
