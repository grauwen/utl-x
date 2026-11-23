package com.glomidco.utlx.cli

import com.glomidco.utlx.cli.commands.TransformCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * CB09 + CB10 — two defects that made `-e` unable to do what its own usage line documents.
 *
 * **CB09.** Both synthesis sites hardcoded `%utlx 1.0`, so an inline expression always *declared*
 * 1.0 and the whole `%utlx 1.1` namespace — `validate.*`, the one the content guard is built on —
 * was unreachable from a one-liner. The error blamed a declaration the user never wrote, and
 * `--language-mode 1.1` did not help because that **caps the engine** rather than declaring the
 * script. Writing the `validate.*` demo, this forced every step onto disk.
 *
 * **CB10.** The arg loop classifies the first positional as a script, because flag order is not
 * fixed and it cannot know `-e` is coming. With `-e` there IS no script, so the lone positional can
 * only be input — but the user got "Cannot use -e with a script file" about a conflict they never
 * created, while the usage line says `utlx transform -e '<expression>' [input-file] [options]`.
 *
 * Both fixes are narrow, so the tests pin the boundaries hardest: a 1.0 cap must still refuse
 * `validate.*`, and a genuine `-e` + `.utlx` conflict must still be an error.
 */
class CB09CB10InlineExpressionTest {

    @TempDir
    lateinit var tempDir: Path

    private fun file(name: String, text: String) =
        tempDir.resolve(name).toFile().apply { writeText(text) }

    private fun exec(vararg args: String): CommandResult = TransformCommand.execute(arrayOf(*args))

    private fun outputOf(vararg args: String): String {
        val out = tempDir.resolve("out-${args.joinToString().hashCode()}.txt").toFile()
        val r = TransformCommand.execute(arrayOf(*args, "-o", out.absolutePath))
        assertTrue(r is CommandResult.Success, "expected success for ${args.toList()}, got $r")
        return out.readText()
    }

    // ── CB09 ──

    @Test
    fun `an inline expression can call validate star`() {
        val input = file("q.json", """{"qty":5}""")
        val out = outputOf("-e", "\$input |> validate.required([\"qty\"])", "-i", input.absolutePath)
        assertTrue(out.contains("\"valid\": true"), out)
        assertTrue(out.contains("\"passed\": 1"), out)
    }

    @Test
    fun `a 1_0 engine cap still refuses the 1_1 namespace`() {
        // The boundary. --language-mode is a CAP; with it set to 1.0 the synthesized script
        // honestly declares 1.0, so the refusal is correct AND its message is now true.
        val input = file("q2.json", """{"qty":5}""")
        val r = exec("-e", "\$input |> validate.required([\"qty\"])",
                     "-i", input.absolutePath, "--language-mode", "1.0")
        assertTrue(r is CommandResult.Failure, "a 1.0 cap must refuse validate.*")
        assertTrue((r as CommandResult.Failure).message.contains("requires %utlx 1.1"), r.message)
    }

    @Test
    fun `a plain 1_0 expression is unaffected`() {
        val input = file("q3.json", """{"a":1,"b":2}""")
        assertTrue(outputOf("-e", "\$input.a + \$input.b", "-i", input.absolutePath, "-r").trim() == "3")
    }

    // ── CB10 ──

    @Test
    fun `with -e a single positional file is the input`() {
        // Exactly the usage line: utlx transform -e '<expression>' [input-file]
        val input = file("rows.json", """[{"sku":"X"},{"sku":"Y"}]""")
        val out = outputOf("-e", "count(\$input)", input.absolutePath, "-r")
        assertTrue(out.trim() == "2", "got: ${out.trim()}")
    }

    @Test
    fun `with -e a utlx positional is still the real conflict`() {
        val script = file("s.utlx", "%utlx 1.0\ninput json\noutput json\n---\n\$input")
        val r = exec("-e", "\$input", script.absolutePath)
        assertTrue(r is CommandResult.Failure)
        assertTrue((r as CommandResult.Failure).message.contains("script file"), r.message)
    }

    @Test
    fun `with -e two positionals are refused, and neither is called a script`() {
        // The misleading-error habit these fixes are about: with -e neither file can be a script,
        // so the message must say "only one positional" rather than naming one as a script.
        val a = file("a.json", "{}")
        val b = file("b.json", "{}")
        val r = exec("-e", "\$input", a.absolutePath, b.absolutePath)
        assertTrue(r is CommandResult.Failure)
        val msg = (r as CommandResult.Failure).message
        assertTrue(msg.contains("only one positional file"), msg)
        assertTrue(!msg.contains("with a script file"), "must not call a .json a script: $msg")
    }

    @Test
    fun `without -e the first positional is still the script`() {
        // The regression that matters: normal two-positional usage is untouched.
        val script = file("t.utlx", "%utlx 1.0\ninput json\noutput json\n---\n\$input.a")
        val input = file("in.json", """{"a":42}""")
        assertTrue(outputOf(script.absolutePath, input.absolutePath, "-r").trim() == "42")
    }
}
