package com.glomidco.utlx.cli

import com.glomidco.utlx.cli.commands.TransformCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CB04 — **compact jq expressions, without changing what already works.**
 *
 * `-e` expands a leading `.` to `$input.`, and decided whether to expand by asking whether the
 * character *before* the dot was one of six it listed (`(`, `,`, space, tab, newline, `>`). So the
 * idiomatic jq spellings were parse errors — `{p:.a}`, `[.a]`, `.a+.b`, `!.a` — because `:`, `[`,
 * `=` and the arithmetic operators are not on that list, and a whitelist grown one spelling at a
 * time never would be. The `>` entry was added for `=>`, which is the tell.
 *
 * ### Why this is a fallback and not a better rule
 *
 * Widening the rule outright is **unsafe**, and eagerly inserting whitespace around operators is
 * worse: `$input.a-b` is the key `"a-b"`, not `a` minus `b`, so spacing it changes a working
 * expression's meaning silently. `*` fails the same way — `.items.*.n` would lose its wildcard.
 *
 * So the conservative expansion runs first and, **only if it fails to parse**, the wider rule is
 * tried. That ordering is the whole safety argument: anything that parses today never meets the
 * wider rule, so no working expression can change meaning. The ambiguous cases stop being
 * blockers — `.a-b` and `.items.*.n` both parse at step one and are served as written — which is
 * why this no longer waits on the hyphen decision in B54.
 *
 * The fallback triggers on a **parse** failure only. `.a-.b` parses (as the key `a-`) and fails at
 * *runtime*; retrying there would re-execute a transformation and dress a data error up as a
 * syntax one.
 */
class CB04DotShorthandFallbackTest {

    @TempDir
    lateinit var tempDir: Path

    private val payload = """
        {"a":10,"b":2,"s":"hi","a-b":99,"items":[{"n":"x","p":5},{"n":"y","p":15}]}
    """.trimIndent()

    private var n = 0

    /** Run an inline expression and return the rendered output. */
    private fun run(expr: String, vararg extra: String): String {
        val input = tempDir.resolve("in.json").toFile().apply { writeText(payload) }
        val out = tempDir.resolve("out-${n++}.txt").toFile()
        val result = TransformCommand.execute(
            arrayOf("-e", expr, "-i", input.absolutePath, "-o", out.absolutePath) + extra
        )
        assertTrue(result is CommandResult.Success, "expected success for -e '$expr', got $result")
        return out.readText().trim().replace(Regex("\\s+"), "")
    }

    private fun failure(expr: String): CommandResult {
        val input = tempDir.resolve("in2.json").toFile().apply { writeText(payload) }
        return TransformCommand.execute(arrayOf("-e", expr, "-i", input.absolutePath))
    }

    // ── guard against a vacuous harness ──

    @Test
    fun `the harness really runs the expression`() {
        assertEquals("10", run("\$input.a"))
    }

    // ── what the fallback fixes ──

    @Test
    fun `compact object construction`() {
        assertEquals("{\"p\":10}", run("{p:.a}"))
        assertEquals("{\"x\":{\"y\":10}}", run("{x:{y:.a}}"))
    }

    @Test
    fun `compact array literal`() {
        assertEquals("[10]", run("[.a]"))
    }

    @Test
    fun `tight arithmetic and comparison`() {
        assertEquals("12", run(".a+.b"))
        assertEquals("20", run(".a*.b"))
        assertEquals("false", run(".a==.b"))
    }

    @Test
    fun `unary operators`() {
        assertEquals("-10", run("-.a"))
        assertEquals("false", run("!.a"))
    }

    @Test
    fun `a mixed expression expands the dots the explicit reference used to disable`() {
        // The conservative pass bailed out entirely on seeing one literal `$input`
        // (`if (expression.contains("\$input")) return expression`), so `.b` was left unexpanded
        // and the whole expression failed. More likely now that --arg/--argjson encourage `$name`.
        assertEquals("12", run("\$input.a + .b"))
        assertEquals("{\"u\":10,\"v\":2}", run("{u:\$input.a, v:.b}"))
    }

    // ── what must NOT change: these parse conservatively and never reach the wider rule ──

    @Test
    fun `a kebab property access still reads the key, not a subtraction`() {
        // The payload holds both "a-b" (99) and a, b (10, 2). `-` is an identifier character, so
        // `.a-b` is the key. If the wider rule ever ran on an expression that already parses, this
        // would silently return 8.
        assertEquals("99", run(".a-b"))
    }

    @Test
    fun `a wildcard projection keeps its wildcard`() {
        assertEquals("[\"x\",\"y\"]", run(".items.*.n"))
    }

    @Test
    fun `spaced operators are untouched`() {
        assertEquals("8", run(".a - .b"))
        assertEquals("12", run(".a + .b"))
    }

    @Test
    fun `ordinary expressions are untouched`() {
        assertEquals("10", run(".a"))
        assertEquals("3", run("count(.items)+1"))
        assertEquals("\"X\"", run("upper(.items[0].n)"))
        assertEquals("\"HI\"", run("upper(.s)"))
    }

    @Test
    fun `a decimal literal is not mistaken for a path`() {
        // `1 + .5` used to be rewritten to `1 + $input.5` and reported "Expected property name" —
        // the conservative rule misfiring in exactly the way conservatism was meant to prevent.
        // `.5` is not valid UTL-X in any spelling, so this still fails; what matters is that the
        // error now refers to the text the user wrote.
        assertEquals("1.5", run("1 + 0.5"))
        assertTrue(failure("1 + .5") is CommandResult.Failure)
    }

    // ── a dot in a string is not a path, under either rule ──

    @Test
    fun `dots inside string literals are never expanded`() {
        assertEquals("\"a.b\"", run("\"a.b\""))
        assertEquals("{\"k\":\"x.y\"}", run("{k:\"x.y\"}"))
    }
}
