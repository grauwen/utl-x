package com.glomidco.utlx.core.interpreter

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * VB02 regression tests — the `when` applicability guard on a `validate.assert` rule.
 *
 * VB01 migrated a rule's `check` to a lambda but left `when` reading an eagerly pre-evaluated
 * boolean, so `when` had no safe spelling:
 *   - `when: $input.qty > 0`  — eager, aborts on missing/mistyped data (the VB01 crash class)
 *   - `when: (p) => ...`      — a FunctionValue the stdlib could not read, so it was **silently
 *                               ignored**: the guard never applied and the rule always ran. Worse
 *                               than the crash — a rule scoped to a subset of records ran against
 *                               all of them with no diagnostic.
 *   - `when: false`           — honoured, but a literal is useless as a guard
 *
 * VB02 resolves a lambda `when` in the interpreter alongside `check`: false skips the rule (and
 * `check` is never evaluated), true applies it, and a guard that throws or returns a non-boolean
 * becomes its own error issue with `when` forced TRUE — a broken guard must never silently
 * suppress its rule (fail-closed).
 *
 * Strategy: these are `:core` tests with no `:stdlib` on the classpath, which suits the subject —
 * VB02 changed what the interpreter *hands to* `validate.assert`, not the aggregation. A synthetic
 * `validate.assert` captures the resolved rules so the assertions are about resolution itself. A
 * `record()` call inside a `check` lambda proves laziness: a skipped rule must not evaluate it.
 */
class VB02WhenGuardTest {

    /**
     * Companion state, deliberately: `tryRegisterFromLookup` registers into the GLOBAL
     * `StandardLibraryImpl.nativeFunctions` and the stored wrapper closes over the lambda it was
     * first given, so per-instance capture would bind to the first test method's now-dead state and
     * leave every later method observing nothing. See B34LogicalShortCircuitTest for the same trap.
     */
    companion object {
        /** Rules as `validate.assert` received them — resolved to booleans by the interpreter. */
        val received = mutableListOf<Map<String, UDM>>()

        /**
         * Payloads seen by `vb02Record()` inside a check lambda — empty means the check never ran.
         *
         * The name must be UNIQUE across the module's tests. `tryRegisterFromLookup` skips a name
         * already present in the global `StandardLibraryImpl.nativeFunctions`
         * (`if (containsKey(name)) return true`), so a shared name means whichever test class runs
         * first wins and every later one observes nothing. A plain `record` collided with
         * B34LogicalShortCircuitTest: both classes pass alone, and both fail in a full-suite run.
         */
        val checkRuns = mutableListOf<Double>()

        val lookup: Map<String, (List<UDM>) -> UDM> = mapOf(
            "validate.assert" to { args ->
                received.clear()
                (args.getOrNull(1) as? UDM.Array)?.elements
                    ?.filterIsInstance<UDM.Object>()
                    ?.forEach { received.add(it.properties) }
                UDM.Object.of("valid" to UDM.Scalar.boolean(true))
            },
            "vb02Record" to { args ->
                checkRuns.add(((args[0] as UDM.Scalar).value as Number).toDouble())
                UDM.Scalar.boolean(true)
            },
        )
    }

    @BeforeEach
    fun reset() {
        received.clear()
        checkRuns.clear()
    }

    /** `$input` is `{ type: "RETAIL" }` — there is no `qty` property. */
    private fun run(rules: String) {
        val src = "%utlx 1.1\ninput json\noutput json\n---\n\$input |> validate.assert([$rules])"
        val tokens = Lexer(src).tokenize()
        val program = when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        val interpreter = Interpreter()
        interpreter.setStdlibLookup(lookup)
        interpreter.execute(program, UDM.Object.of("type" to UDM.Scalar("RETAIL")))
    }

    private fun only(): Map<String, UDM> {
        assertEquals(1, received.size, "expected exactly one rule, got ${received.size}")
        return received[0]
    }

    private fun flag(props: Map<String, UDM>, key: String): Any? = (props[key] as? UDM.Scalar)?.value
    private fun text(props: Map<String, UDM>, key: String): String? = (props[key] as? UDM.Scalar)?.value as? String

    // ── Guard against vacuous assertions ──

    @Test
    fun `the synthetic validate_assert and recorder are actually reached`() {
        run("""{ rule: "r", check: (p) => vb02Record(1), message: "m" }""")
        assertEquals(true, flag(only(), "check"))
        assertEquals(listOf(1.0), checkRuns)
    }

    // ── A false guard skips the rule, and does NOT evaluate the check ──

    @Test
    fun `lambda when returning false marks the rule skipped`() {
        run("""{ rule: "r", check: (p) => vb02Record(1), message: "m", when: (p) => false }""")
        assertEquals(false, flag(only(), "when"), "a false lambda guard must resolve to when=false")
        assertTrue(checkRuns.isEmpty(), "check was evaluated for a skipped rule: $checkRuns")
    }

    @Test
    fun `a guard comparing a field skips the rule when it does not match`() {
        run("""{ rule: "r", check: (p) => vb02Record(1), message: "m", when: (p) => p.type == "WHOLESALE" }""")
        assertEquals(false, flag(only(), "when"))
        assertTrue(checkRuns.isEmpty(), "check ran for a non-applicable rule: $checkRuns")
    }

    // ── A true guard applies the rule ──

    @Test
    fun `lambda when returning true applies the rule and evaluates the check`() {
        run("""{ rule: "r", check: (p) => vb02Record(1), message: "m", when: (p) => p.type == "RETAIL" }""")
        assertEquals(true, flag(only(), "when"))
        assertEquals(listOf(1.0), checkRuns, "check must run for an applicable rule")
    }

    // ── A broken guard must not suppress its rule (fail-closed) ──

    @Test
    fun `a guard that cannot be evaluated becomes an error issue with when forced true`() {
        // `p.qty > 0` on an absent field throws "Cannot compare these types".
        run("""{ rule: "r", check: (p) => vb02Record(1), message: "original", when: (p) => p.qty > 0 }""")
        val props = only()
        assertEquals(true, flag(props, "when"), "a broken guard must not be able to skip its rule")
        assertEquals(false, flag(props, "check"), "the rule must fail")
        assertEquals("error", text(props, "severity"))
        val msg = assertNotNull(text(props, "message"))
        assertTrue(msg.startsWith("guard 'r' could not be evaluated:"), "unexpected message: $msg")
        assertTrue(checkRuns.isEmpty(), "check ran despite the guard failing: $checkRuns")
    }

    @Test
    fun `a guard returning a non-boolean is reported rather than silently trusted`() {
        run("""{ rule: "r", check: (p) => vb02Record(1), message: "original", when: (p) => "yes" }""")
        val props = only()
        assertEquals(true, flag(props, "when"))
        assertEquals(false, flag(props, "check"))
        assertEquals("error", text(props, "severity"))
        assertTrue(assertNotNull(text(props, "message")).contains("did not return a boolean"))
    }

    @Test
    fun `a failing guard overrides a declared warning severity`() {
        run("""{ rule: "r", check: (p) => true, message: "m", severity: "warning", when: (p) => p.qty > 0 }""")
        assertEquals("error", text(only(), "severity"), "fail-closed: a broken guard escalates to error")
    }

    // ── The legacy literal form still works ──

    @Test
    fun `a literal when is passed through unchanged`() {
        run("""{ rule: "r", check: (p) => true, message: "m", when: false }""")
        assertEquals(false, flag(only(), "when"))

        reset()
        run("""{ rule: "r", check: (p) => true, message: "m", when: true }""")
        assertEquals(true, flag(only(), "when"))
    }

    @Test
    fun `a rule with a lambda when but a legacy boolean check still resolves the guard`() {
        // Pre-VB02 the resolver bailed out early unless `check` was a lambda, so a lambda `when`
        // alongside a non-lambda check was left unresolved.
        run("""{ rule: "r", check: true, message: "m", when: (p) => false }""")
        assertEquals(false, flag(only(), "when"))
    }
}
