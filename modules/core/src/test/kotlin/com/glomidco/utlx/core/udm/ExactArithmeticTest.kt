package com.glomidco.utlx.core.udm

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B35 Stage 2 — the contract of exact `+`, `-` and `*`, pinned directly rather than only through
 * the two execution strategies that call it.
 *
 * Three properties carry the design:
 *
 *  - **exact when both operands are exact** — the whole point;
 *  - **fall back, never fabricate** — if either operand is a `Double` there is nothing exact to
 *    preserve, so the caller's `Double` result is returned unchanged. Promoting a `Double` to
 *    `BigDecimal` would invent precision the value never had;
 *  - **scale is derived, not chosen** — adding keeps the larger scale, multiplying adds them. No
 *    rounding happens anywhere, which is exactly why division is absent from this object.
 */
class ExactArithmeticTest {

    // ── What counts as exact ──

    @Test
    fun `exact types are recognised`() {
        assertEquals(BigDecimal("1.50"), ExactArithmetic.exactOrNull(BigDecimal("1.50")))
        assertEquals(BigDecimal("42"), ExactArithmetic.exactOrNull(42L))
        assertEquals(BigDecimal("42"), ExactArithmetic.exactOrNull(42))
        assertEquals(BigDecimal("42"), ExactArithmetic.exactOrNull(BigInteger("42")))
    }

    @Test
    fun `a binary float is NOT exact`() {
        // Returning BigDecimal(0.1) here would manufacture the 55-digit value a double really
        // holds and present it as though the author had written it.
        assertNull(ExactArithmetic.exactOrNull(0.1))
        assertNull(ExactArithmetic.exactOrNull(0.1f))
        assertNull(ExactArithmetic.exactOrNull(Double.NaN))
        assertNull(ExactArithmetic.exactOrNull(null))
    }

    // ── The headline cases ──

    @Test
    fun `money multiplication is exact`() {
        assertEquals(BigDecimal("1999.00"), ExactArithmetic.multiply(BigDecimal("19.99"), 100L, -1.0))
        assertTrue(ExactArithmetic.multiply(BigDecimal("19.99"), 100L, -1.0).toDouble() == 1999.0)
    }

    @Test
    fun `the classic float artefact disappears`() {
        val sum = ExactArithmetic.add(BigDecimal("0.1"), BigDecimal("0.2"), -1.0)
        assertEquals(BigDecimal("0.3"), sum)
        // The double computation it replaces: 0.1 + 0.2 == 0.30000000000000004
        assertTrue(0.1 + 0.2 != 0.3, "the artefact being fixed really exists")
    }

    @Test
    fun `subtraction above 2^53 is exact`() {
        assertEquals(
            BigDecimal("1"),
            ExactArithmetic.subtract(9007199254740993L, 9007199254740992L, -1.0)
        )
        // The double computation it replaces collapses to zero.
        assertEquals(0.0, 9007199254740993.0 - 9007199254740992.0)
    }

    // ── Fall back, never fabricate ──

    @Test
    fun `a Double on either side returns the caller's fallback untouched`() {
        assertEquals(7.5, ExactArithmetic.multiply(1.5, 5L, 7.5))
        assertEquals(7.5, ExactArithmetic.multiply(5L, 1.5, 7.5))
        assertEquals(7.5, ExactArithmetic.add(1.5, 1.5, 7.5))
        assertEquals(7.5, ExactArithmetic.subtract(1.5f, 1L, 7.5))
    }

    @Test
    fun `a missing operand returns the fallback`() {
        assertEquals(1.0, ExactArithmetic.add(null, 1L, 1.0))
        assertEquals(1.0, ExactArithmetic.add(1L, null, 1.0))
    }

    // ── Scale is derived ──

    @Test
    fun `adding keeps the larger scale`() {
        assertEquals(BigDecimal("4.00"), ExactArithmetic.add(BigDecimal("1.50"), BigDecimal("2.50"), -1.0))
        assertEquals(BigDecimal("3.50"), ExactArithmetic.add(BigDecimal("1.00"), BigDecimal("2.5"), -1.0))
    }

    @Test
    fun `multiplying adds the scales`() {
        assertEquals(BigDecimal("29.985"), ExactArithmetic.multiply(BigDecimal("19.99"), BigDecimal("1.5"), -1.0))
        assertEquals(BigDecimal("8.00"), ExactArithmetic.multiply(100L, BigDecimal("0.08"), -1.0))
    }

    @Test
    fun `repeated multiplication grows the scale linearly — known and documented`() {
        // 200 multiplications of a scale-2 value give scale 400. Correct, but worth knowing about
        // in a long reduction: round explicitly when a presentation value is wanted.
        //
        // B46 trims a product's trailing zeros, which shaved this from 402 to 400 — the padding
        // zeros are gone, but the SIGNIFICANT digits still accumulate, so the lesson is unchanged.
        // `1.05^n` is `105^n / 100^n` and `105^n` never ends in a zero, so there is nothing to trim
        // after the first step: growth is a property of the arithmetic, not of the presentation.
        var acc = BigDecimal("1.00")
        repeat(200) { acc = ExactArithmetic.multiply(acc, BigDecimal("1.05"), -1.0) as BigDecimal }
        assertEquals(400, acc.scale())
    }

    // ── No rounding anywhere ──

    @Test
    fun `there is no division — rounding is never applied`() {
        // Division is absent on purpose: it is the only one of the five operators with no exact
        // answer, so it cannot be done without choosing a digit count and a rounding mode. Those
        // are prescribed by law in some tax regimes, which makes them a business decision.
        val names = ExactArithmetic::class.java.methods.map { it.name }.toSet()
        assertTrue("add" in names && "subtract" in names && "multiply" in names)
        assertTrue(names.none { it.contains("divid", ignoreCase = true) },
            "a divide method appeared — it cannot exist without a documented rounding decision")
    }
}
