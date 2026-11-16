package com.glomidco.utlx.cli

import com.glomidco.utlx.cli.commands.TransformCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Discoverability of `--parse-profile` at the moment of rejection.**
 *
 * F23 gave the parsers three strictness profiles and B31 made JSON and YAML agree on them, so a
 * caller *can* decide how much malformed input to tolerate. A user who meets the rejection had no
 * way to learn that: the message said only
 *
 * ```
 * Error parsing input: JSON parse error at 1:28 - Duplicate object key: 'id'
 * ```
 *
 * and the parsers live in `:formats`, where naming a CLI flag would be wrong — the engine and the
 * daemon reach the same code and have no such flag. So the hint is added in the CLI service,
 * which knows both the active profile and the flag.
 *
 * ### Why these tests are mostly about SILENCE
 *
 * A hint that fires on every parse failure is noise, and a hint that promises relief it cannot
 * deliver is worse. The first version of this hint claimed lenient would allow deeper nesting —
 * it does not: `maxDepth` is 256 under **both** standard and lenient, and only strict differs at
 * 64. So the implementation asks the profile table whether lenient is genuinely more permissive
 * for the specific setting involved, and these tests pin each case where it must stay quiet.
 */
class ParseProfileHintTest {

    @TempDir
    lateinit var tempDir: Path

    private val identity = """
        %utlx 1.0
        input json
        output json
        ---
        ${"$"}input
    """.trimIndent()

    /** Run a transform and return everything the user would see. */
    private fun run(inputJson: String, vararg extra: String): String {
        val script = tempDir.resolve("s.utlx").toFile().apply { writeText(identity) }
        val input = tempDir.resolve("in-${inputJson.hashCode()}.json").toFile().apply { writeText(inputJson) }
        val result = TransformCommand.execute(
            arrayOf(*extra, script.absolutePath, input.absolutePath)
        )
        return when (result) {
            is CommandResult.Failure -> result.message
            else -> ""
        }
    }

    private val hint = "--parse-profile lenient"

    // ── vacuity guard ──

    @Test
    fun `a well-formed document produces no failure at all`() {
        assertTrue(run("""{"a":1}""").isEmpty(), "valid input must not fail")
    }

    // ── where the hint belongs ──

    @Test
    fun `a duplicate key under the default profile suggests lenient`() {
        val out = run("""{"id":"FIRST","id":"SECOND"}""")
        assertTrue(out.contains("Duplicate object key"), "the original diagnosis must survive: $out")
        assertTrue(out.contains(hint), "the rejection should name the escape hatch: $out")
        assertTrue(out.contains("last value wins"), "and say what lenient would do: $out")
    }

    @Test
    fun `excessive depth under strict suggests lenient, naming both limits`() {
        val deep = "[".repeat(100) + "]".repeat(100)
        val out = run(deep, "--parse-profile", "strict")
        assertTrue(out.contains("nesting depth"), "the original diagnosis must survive: $out")
        assertTrue(out.contains(hint), "strict (64) can be relieved by lenient (256): $out")
        assertTrue(out.contains("64 to 256"), "and should name the actual limits: $out")
    }

    // ── where it must stay silent ──

    @Test
    fun `no hint when already on lenient`() {
        // Lenient accepts duplicates, so this must not even fail; the point is that nothing
        // suggests a profile when there is nothing stricter to blame.
        val out = run("""{"id":"FIRST","id":"SECOND"}""", "--parse-profile", "lenient")
        assertFalse(out.contains(hint), "nothing to suggest when already lenient: $out")
    }

    @Test
    fun `no hint for excessive depth under standard, because lenient has the same limit`() {
        // maxDepth is 256 for BOTH standard and lenient. Suggesting a profile change here would
        // send the user to a dead end — this is the case that caught the first implementation.
        val tooDeep = "[".repeat(300) + "]".repeat(300)
        val out = run(tooDeep)
        assertTrue(out.contains("nesting depth"), "it should still fail, loudly: $out")
        assertFalse(out.contains(hint), "no profile relieves depth beyond 256: $out")
    }

    @Test
    fun `no hint for a plain syntax error`() {
        val out = run("""{"broken": """)
        assertTrue(out.contains("Error parsing input"), "it should still fail: $out")
        assertFalse(out.contains(hint), "no profile makes truncated JSON parse: $out")
    }
}
