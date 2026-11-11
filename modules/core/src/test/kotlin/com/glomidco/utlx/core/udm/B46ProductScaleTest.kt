package com.glomidco.utlx.core.udm

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

/**
 * B46 — a product's trailing zeros are trimmed, with a floor at the widest operand's scale.
 *
 * `BigDecimal`, ANSI SQL and IEEE 754-2008 decimal all define a product's scale as the **sum** of
 * the operand scales. That is how the digits fall out of long multiplication — and for
 * multiplication, unlike addition, it manufactures places no operand had:
 *
 * ```
 * 1.5 * 1.08 -> 1.620     the third place is an artifact; 1.620 IS 1.62
 * ```
 *
 * Addition cannot do that — `max(s1, s2)` never exceeds an operand's own scale — which is why
 * `1.50 + 1.50 = 3.00` is informative and `0.75 * 1.08 = 0.8100` is not.
 *
 * The floor is what keeps the zeros that *were* in the data, so `1.50 * 3` stays `4.50`.
 */
class B46ProductScaleTest {

    private fun product(a: String, b: String): String {
        val r = ExactArithmetic.multiply(BigDecimal(a), BigDecimal(b), Double.NaN)
        return (r as BigDecimal).toPlainString()
    }

    private fun sum(a: String, b: String): String {
        val r = ExactArithmetic.add(BigDecimal(a), BigDecimal(b), Double.NaN)
        return (r as BigDecimal).toPlainString()
    }

    // ── artifacts removed ──

    @Test
    fun `places no operand had are trimmed`() {
        assertEquals("1.62", product("1.5", "1.08"))      // was 1.620
        assertEquals("0.81", product("0.75", "1.08"))     // was 0.8100
        assertEquals("2.25", product("1.50", "1.50"))     // was 2.2500
        assertEquals("2.16", product("2.0", "1.08"))      // was 2.160
    }

    // ── the floor keeps what was in the data ──

    @Test
    fun `a money operand keeps its two places`() {
        assertEquals("4.50", product("1.50", "3"))
        assertEquals("2500.00", product("2500.00", "1"))
    }

    @Test
    fun `a one-place operand keeps its one place`() {
        assertEquals("3.0", product("1.5", "2"))
    }

    @Test
    fun `the floor never caps a value that needs more places`() {
        // 6 places are required to represent the product; the floor of 3 must not truncate.
        assertEquals("0.000001", product("0.001", "0.001"))
        assertEquals("0.0001", product("0.01", "0.01"))
    }

    @Test
    fun `integers stay plain, not exponent form`() {
        // stripTrailingZeros() turns 2000 into 2E+3; raising to the floor restores plain form.
        assertEquals("2000", product("1000", "2"))
        assertEquals("1000000", product("1000", "1000"))
        assertEquals("6", product("2", "3"))
        assertEquals("0", product("0", "5"))
    }

    @Test
    fun `negatives behave the same`() {
        assertEquals("-1.62", product("-1.5", "1.08"))
        assertEquals("-4.50", product("1.50", "-3"))
    }

    // ── addition is untouched: max(scale) is already right ──

    @Test
    fun `addition keeps the max-scale rule`() {
        assertEquals("3.00", sum("1.50", "1.50"))
        assertEquals("2500.00", sum("2500.00", "0"))
        assertEquals("0.3", sum("0.1", "0.2"))
        assertEquals("1.62", sum("1.50", "0.12"))
    }

    // ── the fallback path is unaffected ──

    @Test
    fun `a double operand still falls back`() {
        assertEquals(6.0, ExactArithmetic.multiply(1.5, 4.0, 6.0))
        assertEquals(6.0, ExactArithmetic.multiply(BigDecimal("1.5"), 4.0, 6.0))
    }

    @Test
    fun `the value is unchanged - only how many places it reports`() {
        // 1.620 and 1.62 are the same number; the rule is presentation, not arithmetic.
        assertEquals(0, BigDecimal("1.620").compareTo(BigDecimal(product("1.5", "1.08"))))
        assertEquals(0, BigDecimal("0.8100").compareTo(BigDecimal(product("0.75", "1.08"))))
    }
}
