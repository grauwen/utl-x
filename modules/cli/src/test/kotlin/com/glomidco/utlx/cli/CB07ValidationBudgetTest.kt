package com.glomidco.utlx.cli

import com.glomidco.utlx.cli.commands.TransformCommand
import com.glomidco.utlx.stdlib.validate.ValidationRoutingPolicy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * CB07 — **gating the exit code on a `validate.*` verdict.**
 *
 * A failing validation used to exit 0. The verdict was correct and complete on stdout, and every
 * caller that follows shell convention — `set -e`, `&&`, a CI step, a route checking the status —
 * concluded the message had passed and forwarded it. That is the failure mode validation exists
 * to prevent.
 *
 * ### Why a flag rather than a new default
 *
 * `%utlx 1.1` deliberately does **not** imply "fail the pipeline". `validate.*` is pure: it
 * reports counts and never applies a threshold. The forward/reject decision is deployment policy —
 * the decide / enforce / lock split — so the same script serves as a guard (reject) and as a
 * reporter (emit the verdict, exit 0). The flag is where that decision belongs on this surface,
 * not a patch for a missing default.
 *
 * ### Why it delegates to [ValidationRoutingPolicy]
 *
 * That is the type the engine gates on. `--validation-budget warnings=3` must mean exactly what
 * the same budget means in a deployment, or a check that passes locally rejects in production.
 * Re-deriving `failed <= max` in the CLI would be the two-implementations divergence that produced
 * B34, B35, EB03 and B48 — which is why the policy was moved to `:stdlib`, the one module both the
 * CLI and the engine already depend on.
 */
class CB07ValidationBudgetTest {

    @TempDir
    lateinit var tempDir: Path

    /** A script whose single rule fails when `amount` is outside 1..1000. */
    private val errorScript = """
        %utlx 1.1
        input json
        output json
        ---
        ${"$"}input |> validate.assert([
          { rule: "amount-range", inRange: { field: "amount", min: 1, max: 1000 } }
        ])
    """.trimIndent()

    /** Four warning-severity rules, so the number of warnings can be dialled by the payload. */
    private val warningScript = """
        %utlx 1.1
        input json
        output json
        ---
        ${"$"}input |> validate.assert([
          { rule: "w1", inRange: { field: "a", min: 100, max: 200 }, severity: "warning" },
          { rule: "w2", inRange: { field: "b", min: 100, max: 200 }, severity: "warning" },
          { rule: "w3", inRange: { field: "c", min: 100, max: 200 }, severity: "warning" },
          { rule: "w4", inRange: { field: "d", min: 100, max: 200 }, severity: "warning" }
        ])
    """.trimIndent()

    private val plainScript = """
        %utlx 1.0
        input json
        output json
        ---
        { r: ${"$"}input.amount }
    """.trimIndent()

    private var n = 0

    /** Run a script over a payload and return (exitCode, stdout-as-written-to-the-output-file). */
    private fun run(script: String, payload: String, vararg flags: String): Pair<Int, String> {
        val s = tempDir.resolve("s${n}.utlx").toFile().apply { writeText(script) }
        val i = tempDir.resolve("i${n}.json").toFile().apply { writeText(payload) }
        val o = tempDir.resolve("o${n++}.json").toFile()
        val result = TransformCommand.execute(
            arrayOf(*flags, s.absolutePath, i.absolutePath, "-o", o.absolutePath)
        )
        val code = when (result) {
            is CommandResult.Failure -> result.exitCode
            else -> 0
        }
        return code to (if (o.exists()) o.readText() else "")
    }

    /** `n` of the four warning rules out of range. */
    private fun warningPayload(n: Int): String {
        val fields = listOf("a", "b", "c", "d")
            .mapIndexed { idx, k -> "\"$k\": ${if (idx < n) 1 else 150}" }
        return "{${fields.joinToString(",")}}"
    }

    // ── the default must not change ──

    @Test
    fun `without a flag an invalid verdict still exits 0`() {
        val (code, _) = run(errorScript, """{"amount":99999}""")
        assertEquals(0, code, "the historic behaviour must be preserved when no flag is given")
    }

    // ── --fail-on-invalid ──

    @Test
    fun `fail-on-invalid exits 1 on an invalid verdict`() {
        val (code, _) = run(errorScript, """{"amount":99999}""", "--fail-on-invalid")
        assertEquals(1, code)
    }

    @Test
    fun `fail-on-invalid exits 0 on a valid verdict`() {
        val (code, _) = run(errorScript, """{"amount":500}""", "--fail-on-invalid")
        assertEquals(0, code)
    }

    @Test
    fun `the verdict is still written in full when the run fails`() {
        // The whole point of CB07: before, you could have the exit code or the diagnostics, never
        // both — the only workaround reduced the verdict to a bare `false`.
        val (code, out) = run(errorScript, """{"amount":99999}""", "--fail-on-invalid")
        assertEquals(1, code)
        assertTrue(out.contains("\"valid\""), "the verdict must still be written: $out")
        assertTrue(out.contains("amount-range"), "including which rule failed: $out")
        assertTrue(out.contains("99999"), "and the offending value: $out")
    }

    // ── budgets ──

    @Test
    fun `a warning budget forwards at the budget and rejects above it`() {
        assertEquals(0, run(warningScript, warningPayload(3), "--validation-budget", "warnings=3").first,
            "3 warnings is within a budget of 3")
        assertEquals(1, run(warningScript, warningPayload(4), "--validation-budget", "warnings=3").first,
            "4 warnings is over a budget of 3")
    }

    @Test
    fun `an error budget forwards at the budget and rejects above it`() {
        assertEquals(0, run(errorScript, """{"amount":99999}""", "--validation-budget", "errors=1").first,
            "1 error is within a budget of 1")
        assertEquals(1, run(errorScript, """{"amount":99999}""", "--validation-budget", "errors=0").first,
            "1 error is over a budget of 0")
    }

    @Test
    fun `a bare validation-budget is fail-closed`() {
        assertEquals(1, run(errorScript, """{"amount":99999}""", "--validation-budget", "").first,
            "no budgets given means both default to 0")
    }

    // ── scripts that do not validate ──

    @Test
    fun `a plain mapping is unaffected by the flag`() {
        val (code, _) = run(plainScript, """{"amount":99999}""", "--fail-on-invalid")
        assertEquals(0, code, "decide() forwards anything that is not a ValidationResult")
    }

    // ── the spec parser rejects rather than ignores ──

    @Test
    fun `a non-numeric budget is rejected`() {
        val e = assertFailsWith<IllegalArgumentException> {
            TransformCommand.parseValidationBudget("warnings=abc")
        }
        assertTrue(e.message!!.contains("whole number"), e.message!!)
    }

    @Test
    fun `a negative budget is rejected`() {
        assertFailsWith<IllegalArgumentException> { TransformCommand.parseValidationBudget("errors=-1") }
    }

    @Test
    fun `an unknown key is rejected rather than silently ignored`() {
        // A dropped budget is the worst outcome: the user believes a gate is in place when the run
        // is ungated.
        val e = assertFailsWith<IllegalArgumentException> {
            TransformCommand.parseValidationBudget("typo=3")
        }
        assertTrue(e.message!!.contains("unknown key"), e.message!!)
    }

    @Test
    fun `a notices budget is rejected, because notices never gate`() {
        val e = assertFailsWith<IllegalArgumentException> {
            TransformCommand.parseValidationBudget("notices=5")
        }
        assertTrue(e.message!!.contains("never gate"), e.message!!)
    }

    @Test
    fun `the parser produces the same budgets the policy would`() {
        val parsed = TransformCommand.parseValidationBudget("errors=2,warnings=5")
        assertEquals(2, parsed.maxErrors)
        assertEquals(5, parsed.maxWarnings)
        val bare = TransformCommand.parseValidationBudget("")
        assertEquals(ValidationRoutingPolicy.FAIL_CLOSED.maxErrors, bare.maxErrors)
        assertEquals(ValidationRoutingPolicy.FAIL_CLOSED.maxWarnings, bare.maxWarnings)
    }

    // ── CLI and engine must agree ──

    @Test
    fun `the CLI outcome matches ValidationRoutingPolicy on the same counts`() {
        // The guard against the two implementations drifting. If the CLI ever stops delegating,
        // this is what fails.
        val policy = ValidationRoutingPolicy(maxErrors = 0, maxWarnings = 3)
        assertTrue(policy.decide(failed = 0, warnings = 3).forwarded, "policy: 3 warnings forwards")
        assertEquals(0, run(warningScript, warningPayload(3), "--validation-budget", "warnings=3").first)

        assertTrue(!policy.decide(failed = 0, warnings = 4).forwarded, "policy: 4 warnings rejects")
        assertEquals(1, run(warningScript, warningPayload(4), "--validation-budget", "warnings=3").first)
    }
}
