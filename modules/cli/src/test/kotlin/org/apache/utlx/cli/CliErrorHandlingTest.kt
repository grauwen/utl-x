// modules/cli/src/test/kotlin/org/apache/utlx/cli/CliErrorHandlingTest.kt
package org.apache.utlx.cli

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B26 — CLI error-handling / input-validation regression tests (GitHub issue #2).
 *
 * These are INTEGRATION tests: each spawns the assembled fat JAR as a real subprocess and asserts on
 * exit code + stdout/stderr + a hard timeout. That is the only level at which the B26 bugs are
 * observable — they live in process behavior (duplicate stderr lines, a leaked stack trace, a hang),
 * not in transformation output, so neither in-process unit tests nor the conformance suite can express
 * them.
 *
 * The JAR path is provided by Gradle via the `utlx.cli.jar` system property (see build.gradle.kts).
 * Running outside Gradle (e.g. an IDE without that property) skips these tests.
 */
class CliErrorHandlingTest {

    @TempDir
    lateinit var tempDir: Path

    private data class CliResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean
    )

    /** Run `java [sysProps] -jar <cli.jar> <args>`, feeding [stdin], with a hard [timeoutSec]. */
    private fun runCli(
        vararg args: String,
        stdin: String? = null,
        sysProps: Map<String, String> = emptyMap(),
        timeoutSec: Long = 30
    ): CliResult {
        val jar = System.getProperty("utlx.cli.jar")
        assumeTrue(jar != null && File(jar).exists()) {
            "utlx.cli.jar not set (run via Gradle: ./gradlew :modules:cli:test)"
        }
        val javaBin = File(System.getProperty("java.home"), "bin/java").absolutePath

        val cmd = mutableListOf(javaBin)
        sysProps.forEach { (k, v) -> cmd.add("-D$k=$v") }
        cmd.add("-jar"); cmd.add(jar!!); cmd.addAll(args)

        val proc = ProcessBuilder(cmd).redirectErrorStream(false).start()

        // Write (and close) stdin so a piped read sees EOF; close even when null.
        proc.outputStream.use { os -> if (stdin != null) os.write(stdin.toByteArray()) }

        // Drain both pipes concurrently so a full buffer can't deadlock the child.
        val out = StringBuilder()
        val err = StringBuilder()
        val tOut = Thread { proc.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
        val tErr = Thread { proc.errorStream.bufferedReader().forEachLine { err.appendLine(it) } }
        tOut.start(); tErr.start()

        val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            tOut.join(2000); tErr.join(2000)
            return CliResult(-1, out.toString(), err.toString(), timedOut = true)
        }
        tOut.join(5000); tErr.join(5000)
        return CliResult(proc.exitValue(), out.toString(), err.toString(), timedOut = false)
    }

    private fun String.countOf(sub: String): Int =
        if (sub.isEmpty()) 0 else split(sub).size - 1

    /** A stack trace leaked to the user — frame lines and/or the exception class name. */
    private fun assertNoStackTrace(stderr: String) {
        assertFalse(stderr.contains("\tat "), "stderr leaked a stack frame:\n$stderr")
        assertFalse(stderr.contains("\tat org.apache"), "stderr leaked a stack frame:\n$stderr")
        assertFalse(stderr.contains("ParseException"), "stderr leaked the exception class:\n$stderr")
        // the parser's logged line ("ERROR o.a.utlx.core.parser ... Parse exception")
        assertFalse(stderr.contains("Parse exception"), "stderr leaked the parser log line:\n$stderr")
    }

    private fun validScript(): File = tempDir.resolve("script.utlx").toFile().apply {
        writeText(
            """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input
            """.trimIndent()
        )
    }

    private fun inputJson(): File = tempDir.resolve("input.json").toFile().apply {
        writeText("""{"a": 1}""")
    }

    // ── Bug 1: validation errors must be printed exactly once ─────────────────────────────────

    @Test
    fun `missing script file prints the error once, no stack trace`() {
        val missing = tempDir.resolve("does-not-exist.utlx").toFile()
        val r = runCli("transform", missing.absolutePath, inputJson().absolutePath)

        assertEquals(1, r.exitCode, "should fail with exit 1")
        assertEquals(
            1, r.stderr.countOf("Script file not found"),
            "the error must appear exactly once (was duplicated). stderr:\n${r.stderr}"
        )
        assertNoStackTrace(r.stderr)
    }

    @Test
    fun `missing input file prints the error once with the input name`() {
        val missingInput = tempDir.resolve("no-input.json").toFile()
        val r = runCli("transform", validScript().absolutePath, missingInput.absolutePath)

        assertEquals(1, r.exitCode)
        assertEquals(
            1, r.stderr.countOf("Input file not found"),
            "the error must appear exactly once. stderr:\n${r.stderr}"
        )
        assertTrue(r.stderr.contains("(input: input)"), "should keep the input-name detail")
        assertNoStackTrace(r.stderr)
    }

    @Test
    fun `unknown option prints the error once`() {
        val r = runCli("transform", validScript().absolutePath, "--bogus")
        assertEquals(1, r.exitCode)
        assertEquals(
            1, r.stderr.countOf("Unknown option: --bogus"),
            "the error must appear exactly once. stderr:\n${r.stderr}"
        )
        assertNoStackTrace(r.stderr)
    }

    // ── Bug 2: parse failures — no stack trace, no duplicate block; empty script is friendly ──

    @Test
    fun `parse error prints once with no stack trace`() {
        // Non-blank but invalid: missing the '---' separator after the header.
        val bad = tempDir.resolve("bad.utlx").toFile()
        bad.writeText(
            """
            %utlx 1.0
            input json
            output json
            { ok: 1 }
            """.trimIndent()
        )
        val r = runCli("transform", bad.absolutePath, inputJson().absolutePath)

        assertEquals(1, r.exitCode)
        assertEquals(
            1, r.stderr.countOf("Parse errors:"),
            "the 'Parse errors:' block must appear exactly once (was duplicated). stderr:\n${r.stderr}"
        )
        assertTrue(
            r.stderr.contains("Expected '---' separator"),
            "should surface the actual parse message. stderr:\n${r.stderr}"
        )
        assertNoStackTrace(r.stderr)
    }

    @Test
    fun `empty script gives a friendly message, not parser internals`() {
        val empty = tempDir.resolve("empty.utlx").toFile()
        empty.writeText("   \n  \n")  // blank
        val r = runCli("transform", empty.absolutePath, inputJson().absolutePath)

        assertEquals(1, r.exitCode)
        assertTrue(
            r.stderr.contains("Script file is empty"),
            "should report an empty script. stderr:\n${r.stderr}"
        )
        assertFalse(
            r.stderr.contains("Expected '---' separator"),
            "must not surface the parser-internal separator message. stderr:\n${r.stderr}"
        )
        assertNoStackTrace(r.stderr)
    }

    // ── Bug 3: no input on an interactive terminal must NOT hang ──────────────────────────────

    @Test
    fun `no input on an interactive stdin fails fast and does not hang`() {
        // Simulate a TTY via the test seam (a subprocess has no real console).
        val r = runCli(
            "transform", validScript().absolutePath,
            sysProps = mapOf("utlx.stdin.interactive" to "true"),
            timeoutSec = 15
        )
        assertFalse(r.timedOut, "CLI hung waiting for stdin instead of failing fast")
        assertEquals(1, r.exitCode)
        assertTrue(
            r.stderr.contains("No input provided"),
            "should tell the user to pass an input file or pipe stdin. stderr:\n${r.stderr}"
        )
    }

    @Test
    fun `piped stdin is still read normally (guard does not break valid input)`() {
        // Not interactive: data on stdin must be consumed and transformed.
        val r = runCli(
            "transform", validScript().absolutePath,
            stdin = """{"a": 42}""",
            sysProps = mapOf("utlx.stdin.interactive" to "false"),
            timeoutSec = 20
        )
        assertFalse(r.timedOut)
        assertEquals(0, r.exitCode, "valid piped input should succeed. stderr:\n${r.stderr}")
        assertTrue(r.stdout.contains("42"), "output should reflect the piped input. stdout:\n${r.stdout}")
    }
}
