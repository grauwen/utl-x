package com.glomidco.utlx.cli

import com.glomidco.utlx.cli.CommandResult
import com.glomidco.utlx.cli.commands.TransformCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F27 — **a result must be usable from a shell without a cleanup pipeline.**
 *
 * `-e/--expression` already existed (undocumented); what was missing was raw output over a
 * *sequence* and a way to pass a value in from the shell. Two regressions are pinned here
 * because the first attempt at each produced plausible-looking wrong output:
 *
 *  - **numbers.** Rendering scalars with `value.toString()` gave `2.0` for `count()` where
 *    every other output path gives `2` (B37), and `10.5` where an exact decimal is `10.50`
 *    (B35). `-r` must differ from the normal path *only* by the absence of string quoting.
 *  - **strings.** The original `-r` stripped JSON quotes and then un-escaped with sequential
 *    `replace` calls. That cannot undo JSON escaping: unescaping `\\` first leaves a
 *    backslash which pairs with the next character, so `a\tb` came back as `a<TAB>b` and
 *    `C:\tmp\x` as `C:\<TAB>mp\x`. A String is now returned verbatim from the UDM and never
 *    round-tripped through JSON at all.
 */
class F27ShellErgonomicsTest {

    @TempDir
    lateinit var tempDir: Path

    private val payload = """
        {"tag":"v3.0.4","flag":true,"big":9007199254740993,
         "items":[{"n":"a","p":10.50},{"n":"b","p":25.00}]}
    """.trimIndent()

    /** Run an inline expression and return stdout-equivalent text (written via -o). */
    private fun run(expr: String, vararg extra: String): String {
        val input = tempDir.resolve("in.json").toFile().apply { writeText(payload) }
        val out = tempDir.resolve("out-${extra.joinToString("_").hashCode()}.txt").toFile()
        val args = arrayOf("-e", expr, "-i", input.absolutePath, "-o", out.absolutePath) + extra
        val result = TransformCommand.execute(args)
        assertTrue(result is CommandResult.Success,
            "expected success for -e '$expr' ${extra.toList()}, got $result")
        return out.readText().trim()
    }

    private fun failure(vararg args: String): CommandResult {
        val input = tempDir.resolve("in2.json").toFile().apply { writeText(payload) }
        return TransformCommand.execute(arrayOf(*args, "-i", input.absolutePath))
    }

    // ── guard against a vacuous harness ──

    @Test
    fun `the harness really runs the expression`() {
        // If -e were ignored, every assertion below would pass against the whole document.
        assertEquals("\"v3.0.4\"", run("\$input.tag"))
    }

    // ── -r on scalars: identical to the normal path except for quoting ──

    @Test
    fun `raw strips quotes from a string and nothing else`() {
        assertEquals("\"v3.0.4\"", run("\$input.tag"))
        assertEquals("v3.0.4", run("\$input.tag", "-r"))
    }

    @Test
    fun `raw renders an integer-valued number as an integer, not 2 point 0`() {
        // The regression: value.toString() on the boxed Double gave "2.0".
        assertEquals("2", run("count(\$input.items)"))
        assertEquals("2", run("count(\$input.items)", "-r"))
    }

    @Test
    fun `raw preserves decimal scale`() {
        assertEquals("10.50", run("\$input.items[0].p"))
        assertEquals("10.50", run("\$input.items[0].p", "-r"))
        assertEquals("35.50", run("sum(\$input.items[*].p)", "-r"))
    }

    @Test
    fun `raw passes booleans, large integers and null through unchanged`() {
        assertEquals("true", run("\$input.flag", "-r"))
        assertEquals("9007199254740993", run("\$input.big", "-r"))
        assertEquals("null", run("\$input.missing", "-r"))
    }

    // ── -r on sequences: one per line ──

    @Test
    fun `raw prints a sequence of strings one per line`() {
        assertEquals("a\nb", run("\$input.items[*].n", "-r"))
    }

    @Test
    fun `raw prints a sequence of numbers one per line, keeping scale`() {
        assertEquals("10.50\n25.00", run("\$input.items[*].p", "-r"))
    }

    @Test
    fun `raw leaves structure as JSON rather than inventing a flattening`() {
        val obj = run("\$input.items[0]", "-r")
        assertTrue(obj.startsWith("{") && obj.contains("\"n\""), "expected JSON object, got: $obj")
    }

    // ── --arg ──

    @Test
    fun `arg binds a string usable in the expression`() {
        assertEquals("world", run("\$name", "-r", "--arg", "name=world"))
    }

    @Test
    fun `arg values are strings and coerce with toNumber`() {
        assertEquals("b", run("filter(\$input.items, (i) -> i.p > toNumber(\$min)).*.n",
            "-r", "--arg", "min=12"))
    }

    @Test
    fun `several args can be bound`() {
        assertEquals("x-y", run("\$a + \"-\" + \$b", "-r", "--arg", "a=x", "--arg", "b=y"))
    }

    @Test
    fun `an arg value round-trips verbatim — the sequential-unescape regression`() {
        // Each of these was corrupted by the original quote-strip-then-replace chain.
        assertEquals("""a\tb""", run("\$p", "-r", "--arg", """p=a\tb"""))
        assertEquals("""C:\tmp\x""", run("\$p", "-r", "--arg", """p=C:\tmp\x"""))
        assertEquals("""a\\b""", run("\$p", "-r", "--arg", """p=a\\b"""))
        assertEquals("""say "hi"""", run("\$p", "-r", "--arg", """p=say "hi""""))
        assertEquals("a=b", run("\$p", "-r", "--arg", "p=a=b"))
        assertEquals("hello there", run("\$p", "-r", "--arg", "p=hello there"))
    }

    // ── rejections, so a mistake is loud ──

    @Test
    fun `arg without an equals sign is rejected`() {
        assertTrue(failure("-e", "\$x", "--arg", "bad") is CommandResult.Failure)
    }

    @Test
    fun `arg with a non-identifier name is rejected rather than emitting bad source`() {
        // Without validation this becomes `let 1x = "v"` and surfaces as a parse error in
        // generated source the user never wrote.
        assertTrue(failure("-e", "\$x", "--arg", "1x=v") is CommandResult.Failure)
    }

    @Test
    fun `arg with a script file is rejected — there is no synthesis point`() {
        val script = tempDir.resolve("s.utlx").toFile().apply {
            writeText("%utlx 1.0\ninput json\noutput json\n---\n\$input.tag")
        }
        assertTrue(failure(script.absolutePath, "--arg", "x=1") is CommandResult.Failure)
    }

    // ── --argjson: structured values, read by the real JSON parser ──

    @Test
    fun `argjson binds a structured value with its types intact`() {
        assertEquals("12", run("\$cfg.min", "-r", "--argjson", "cfg={\"min\":12}"))
        // Arithmetic proves it stayed a number rather than becoming a string.
        assertEquals("13", run("\$cfg.min + 1", "-r", "--argjson", "cfg={\"min\":12}"))
    }

    @Test
    fun `argjson accepts arrays and nesting`() {
        assertEquals("3", run("count(\$ids)", "-r", "--argjson", "ids=[1,2,3]"))
        assertEquals("deep", run("\$c.a.b.c", "-r", "--argjson",
            "c={\"a\":{\"b\":{\"c\":\"deep\"}}}"))
    }

    @Test
    fun `argjson preserves decimal scale, like any other JSON input`() {
        assertEquals("10.50", run("\$cfg.v", "-r", "--argjson", "cfg={\"v\":10.50}"))
    }

    @Test
    fun `argjson and arg can be combined`() {
        assertEquals("lbl:7", run("\$s + \":\" + toString(\$c.n)", "-r",
            "--arg", "s=lbl", "--argjson", "c={\"n\":7}"))
    }

    /**
     * The reason --argjson routes through the JSON reader rather than being spliced into the
     * script as a literal. The expression lexer agrees with the JSON parser on every numeric
     * and string edge case tried — but NOT on duplicate keys, where it silently takes the last
     * value. That is B01's failure mode, and F23's parse profiles exist to govern it. Going
     * through the reader means one JSON policy, not two.
     */
    @Test
    fun `argjson is governed by --parse-profile, not by the expression lexer`() {
        val dup = "c={\"a\":1,\"a\":2}"
        assertTrue(failure("-e", "\$c", "--argjson", dup, "--parse-profile", "standard")
            is CommandResult.Failure)
        assertTrue(failure("-e", "\$c", "--argjson", dup, "--parse-profile", "strict")
            is CommandResult.Failure)
        // lenient permits last-wins
        assertEquals("2", run("\$c.a", "-r", "--argjson", dup, "--parse-profile", "lenient"))
    }

    @Test
    fun `argjson does not become the primary input`() {
        // Appended after the real inputs, so `inputs.values.first()` is unaffected.
        assertEquals("a", run("\$input.items[0].n", "-r", "--argjson", "c={\"x\":1}"))
    }

    @Test
    fun `argjson name colliding with an input is rejected`() {
        assertTrue(failure("-e", "\$x", "--argjson", "input={}") is CommandResult.Failure)
    }

    @Test
    fun `malformed argjson json is rejected`() {
        assertTrue(failure("-e", "\$c", "--argjson", "c={bad") is CommandResult.Failure)
    }

    @Test
    fun `argjson without an equals sign, or with a bad name, is rejected`() {
        assertTrue(failure("-e", "\$x", "--argjson", "bad") is CommandResult.Failure)
        assertTrue(failure("-e", "\$x", "--argjson", "1x={}") is CommandResult.Failure)
    }
}
