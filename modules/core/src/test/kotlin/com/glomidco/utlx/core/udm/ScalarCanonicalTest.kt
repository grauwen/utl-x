package com.glomidco.utlx.core.udm

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B35 — `ScalarCanonical` is the single definition of numeric sameness, shared by the comparison
 * operators and by the declarative validation rule kinds. Those two used to disagree, so the
 * contract is pinned here directly rather than only through the interpreter.
 *
 * The two halves of the contract pull in opposite directions and both matter:
 *
 *  - **representation must not matter** — `5`, `5.0` and `5.00` are one value, however they were
 *    written and whichever box they arrived in;
 *  - **type must still matter** — a numeric string is not a number and a boolean is not 1, because
 *    the validation kinds built on this are controlled-value checks, not coercions.
 */
class ScalarCanonicalTest {

    // ── Representation must not matter ──

    @Test
    fun `scale does not change equality`() {
        assertTrue(ScalarCanonical.numbersEqual(BigDecimal("100.10"), BigDecimal("100.1")))
        assertTrue(ScalarCanonical.numbersEqual(BigDecimal("5.00"), BigDecimal("5")))
    }

    @Test
    fun `integral and decimal spellings of one value are equal`() {
        assertTrue(ScalarCanonical.numbersEqual(5L, BigDecimal("5.0")))
        assertTrue(ScalarCanonical.numbersEqual(5, 5.0))
        assertTrue(ScalarCanonical.numbersEqual(BigInteger("5"), BigDecimal("5.000")))
    }

    @Test
    fun `exponent and plain spellings of one value are equal`() {
        assertTrue(ScalarCanonical.numbersEqual(1e10, 10000000000L))
        assertTrue(ScalarCanonical.numbersEqual(BigDecimal("5E+1"), 50L))
    }

    @Test
    fun `canonical form of a Double introduces no binary-float noise`() {
        // Going through toString() rather than the binary value is what keeps 0.1 canonicalising to
        // exactly 0.1 instead of 0.1000000000000000055511151231257827.
        assertEquals(BigDecimal("0.1"), ScalarCanonical.canonical(0.1))
    }

    // ── Exactness above 2^53 ──

    @Test
    fun `two different integers above 2^53 are not equal`() {
        assertFalse(ScalarCanonical.numbersEqual(9007199254740993L, 9007199254740992L))
        assertFalse(ScalarCanonical.numbersEqual(BigInteger("9007199254740993"), 9007199254740992L))
    }

    @Test
    fun `ordering above 2^53 is exact`() {
        assertTrue(ScalarCanonical.compareNumbers(9007199254740993L, 9007199254740992L) > 0)
        assertTrue(ScalarCanonical.compareNumbers(9007199254740992L, 9007199254740993L) < 0)
        assertEquals(0, ScalarCanonical.compareNumbers(9007199254740993L, BigDecimal("9007199254740993")))
    }

    @Test
    fun `ordering across mixed representations`() {
        assertTrue(ScalarCanonical.compareNumbers(5L, BigDecimal("5.5")) < 0)
        assertEquals(0, ScalarCanonical.compareNumbers(5L, BigDecimal("5.00")))
        assertTrue(ScalarCanonical.compareNumbers(BigDecimal("100.10"), 100.1) == 0)
    }

    // ── Type must still matter ──

    @Test
    fun `a boolean is kept out of the numeric path`() {
        // Boolean is not a Number in Kotlin, so this is really a guard against someone "helpfully"
        // adding a branch that maps true to 1.
        assertEquals(true, ScalarCanonical.canonical(true))
        assertEquals(false, ScalarCanonical.canonical(false))
    }

    @Test
    fun `a numeric string is returned unchanged, not converted`() {
        assertEquals("50", ScalarCanonical.canonical("50"))
    }

    @Test
    fun `null and other values pass through`() {
        assertEquals(null, ScalarCanonical.canonical(null))
        val marker = Any()
        assertEquals(marker, ScalarCanonical.canonical(marker))
    }

    // ── Non-finite doubles: BigDecimal cannot hold them, so they keep their boxed form ──

    @Test
    fun `non-finite doubles keep their boxed form`() {
        assertEquals(Double.NaN, ScalarCanonical.canonical(Double.NaN))
        assertEquals(Double.POSITIVE_INFINITY, ScalarCanonical.canonical(Double.POSITIVE_INFINITY))
        assertEquals(Double.NEGATIVE_INFINITY, ScalarCanonical.canonical(Double.NEGATIVE_INFINITY))
        assertEquals(Float.NaN, ScalarCanonical.canonical(Float.NaN))
    }

    @Test
    fun `non-finite comparison falls back to Double semantics`() {
        // Boxed Double.equals compares bits, so NaN equals NaN here — the pre-existing behaviour,
        // deliberately preserved rather than redefined by this change.
        assertTrue(ScalarCanonical.numbersEqual(Double.NaN, Double.NaN))
        assertFalse(ScalarCanonical.numbersEqual(Double.NaN, 1.0))
        assertTrue(ScalarCanonical.compareNumbers(Double.POSITIVE_INFINITY, 1.0) > 0)
        assertTrue(ScalarCanonical.compareNumbers(Double.NEGATIVE_INFINITY, 1.0) < 0)
        assertEquals(0, ScalarCanonical.compareNumbers(Double.NaN, Double.NaN))
    }

    @Test
    fun `an infinite and a finite value do not compare equal`() {
        assertFalse(ScalarCanonical.numbersEqual(Double.POSITIVE_INFINITY, BigDecimal("1")))
    }

    // ── Signed zero ──

    @Test
    fun `negative zero equals zero`() {
        // Boxed Double.equals says otherwise (different bits), so canonicalising changes this —
        // in the direction of what -0.0 == 0.0 means arithmetically.
        assertTrue(ScalarCanonical.numbersEqual(-0.0, 0.0))
        assertTrue(ScalarCanonical.numbersEqual(BigDecimal("-0.0"), 0L))
    }
}
