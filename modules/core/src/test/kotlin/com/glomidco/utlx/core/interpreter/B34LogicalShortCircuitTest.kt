package com.glomidco.utlx.core.interpreter

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B34 root-cause regression tests — `&&` and `||` must SHORT-CIRCUIT.
 *
 * Root cause: `Interpreter.evaluateBinaryOp` evaluated BOTH operands up front, before dispatching
 * on the operator, so the `AND`/`OR` branches saw two already-computed values. The Kotlin `&&` in
 * those branches short-circuited over the computed values and therefore provided no short-circuit
 * at the UTL-X level. Consequences: a logically dead operand still ran (`false && (1 / 0)` raised
 * "Division by zero") and no guard could protect the comparison behind it (`x != null && x > 0`
 * threw "Cannot compare these types" on absent `x` — exactly the data a guard exists for).
 *
 * The fix adds an early return for AND/OR ahead of the eager operand pair, mirroring the
 * NULLISH_COALESCE block directly above it.
 *
 * Strategy: "absence of a throw" is a weak assertion — it cannot distinguish "the right operand was
 * skipped" from "the right operand ran but happened not to fail". So these tests register a
 * synthetic stdlib function `record` that APPENDS to a list and returns true. Asserting the list is
 * empty proves the right operand was never evaluated. The arithmetic cases (`1 / 0`) are kept
 * alongside as the author-visible symptom.
 *
 * Result type: `&&`/`||` are boolean-coercing, NOT value-returning — `1 || 2` is `true`, not `1`.
 * That was the decision recorded in B34 (`??` owns the default-value idiom), and it is asserted
 * here so a later switch to value-returning cannot happen silently.
 */
class B34LogicalShortCircuitTest {

    /**
     * `calls` and the lookup are COMPANION state, deliberately.
     *
     * `Interpreter.tryRegisterFromLookup` registers into `StandardLibraryImpl.nativeFunctions`, a
     * global mutable map, and the wrapper it stores closes over the lambda it was given. JUnit
     * builds a fresh test instance per method, so per-instance state would mean the FIRST method to
     * call `record` pins the closure to its own (then-dead) list, and every later method would see
     * an empty list — making the `calls.isEmpty()` assertions pass vacuously. Companion state plus
     * a @BeforeEach reset keeps one stable list across instances.
     */
    companion object {
        val calls = mutableListOf<Double>()

        val recordingLookup: Map<String, (List<UDM>) -> UDM> = mapOf(
            "record" to { args ->
                calls.add(((args[0] as UDM.Scalar).value as Number).toDouble())
                UDM.Scalar.boolean(true)
            }
        )
    }

    @BeforeEach
    fun resetRecorder() = calls.clear()

    /** Runs `body` with `$input` bound to `{ "price": 5 }` — note: no `qty` property. */
    private fun run(body: String): UDM {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        val tokens = Lexer(src).tokenize()
        val program = when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        val interpreter = Interpreter()
        interpreter.setStdlibLookup(recordingLookup)
        val input = UDM.Object.of("price" to UDM.Scalar(5.0))
        return interpreter.execute(program, input).toUDM()
    }

    private fun bool(udm: UDM): Any? = (udm as UDM.Scalar).value

    // ── Guard against vacuous assertions ──

    @Test
    fun `the recorder itself works — without this the isEmpty assertions prove nothing`() {
        assertEquals(true, bool(run("record(1)")))
        assertEquals(listOf(1.0), calls)
    }

    // ── The right operand must not be EVALUATED (not merely "must not throw") ──

    @Test
    fun `AND with falsy left does not evaluate the right operand`() {
        assertEquals(false, bool(run("false && record(1)")))
        assertTrue(calls.isEmpty(), "right operand of && was evaluated: $calls")
    }

    @Test
    fun `OR with truthy left does not evaluate the right operand`() {
        assertEquals(true, bool(run("true || record(1)")))
        assertTrue(calls.isEmpty(), "right operand of || was evaluated: $calls")
    }

    @Test
    fun `AND with truthy left does evaluate the right operand`() {
        assertEquals(true, bool(run("true && record(1)")))
        assertEquals(listOf(1.0), calls)
    }

    @Test
    fun `OR with falsy left does evaluate the right operand`() {
        assertEquals(true, bool(run("false || record(1)")))
        assertEquals(listOf(1.0), calls)
    }

    @Test
    fun `chained AND stops at the first falsy operand`() {
        assertEquals(false, bool(run("false && record(1) && record(2)")))
        assertTrue(calls.isEmpty(), "chain did not stop at the first falsy operand: $calls")
    }

    // ── The author-visible symptoms B34 was reported for ──

    @Test
    fun `dead operand of AND is not evaluated so division by zero never happens`() {
        assertEquals(false, bool(run("false && (1 / 0)")))
    }

    @Test
    fun `dead operand of OR is not evaluated so division by zero never happens`() {
        assertEquals(true, bool(run("true || (1 / 0)")))
    }

    @Test
    fun `null guard protects the comparison behind it`() {
        // `qty` is absent: the comparison would throw "Cannot compare these types" if reached.
        assertEquals(false, bool(run("\$input.qty != null && \$input.qty > 0")))
    }

    @Test
    fun `null guard via OR treats absent as not applicable`() {
        assertEquals(true, bool(run("\$input.qty == null || \$input.qty > 0")))
    }

    @Test
    fun `a guard that passes still evaluates the comparison`() {
        assertEquals(true, bool(run("\$input.price != null && \$input.price > 0")))
    }

    // ── Regressions: the fix must not change results, only evaluation order ──

    @Test
    fun `AND truth table`() {
        assertEquals(true, bool(run("true && true")))
        assertEquals(false, bool(run("true && false")))
        assertEquals(false, bool(run("false && true")))
        assertEquals(false, bool(run("false && false")))
    }

    @Test
    fun `OR truth table`() {
        assertEquals(true, bool(run("true || true")))
        assertEquals(true, bool(run("true || false")))
        assertEquals(true, bool(run("false || true")))
        assertEquals(false, bool(run("false || false")))
    }

    @Test
    fun `logical operators coerce to boolean rather than returning the operand`() {
        // Value-returning semantics would give 1, "x" and 0 here. B34 chose boolean.
        assertEquals(true, bool(run("1 || 2")))
        assertEquals(true, bool(run("\"\" || \"x\"")))
        assertEquals(false, bool(run("0 && 5")))
    }

    @Test
    fun `AND binds tighter than OR`() {
        // true || (false && false) == true;  (true || false) && false would be false.
        assertEquals(true, bool(run("true || false && false")))
    }

    @Test
    fun `nullish coalesce still short-circuits`() {
        // The AND/OR early return sits next to the ?? block — guard against disturbing it.
        assertEquals(5.0, (run("\$input.price ?? record(1)") as UDM.Scalar).value.let {
            (it as Number).toDouble()
        })
        assertTrue(calls.isEmpty(), "?? evaluated its right operand for a non-null left: $calls")
    }
}
