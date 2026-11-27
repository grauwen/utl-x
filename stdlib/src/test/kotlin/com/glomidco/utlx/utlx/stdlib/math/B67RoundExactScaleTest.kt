package com.glomidco.utlx.stdlib.math

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.finance.FinancialFunctions
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B67 — `round(x, n)` narrowed to `Double`, so it lost scale **and rounded the wrong way**.
 *
 *     val num = args[0].asNumber()                      // scale gone here
 *     val factor = 10.0.pow(places)
 *     return UDM.Scalar(round(num * factor) / factor)   // and the arithmetic is Double
 *
 * Two consequences from one line. The scale loss undid
 * [B35][com.glomidco.utlx.core.format.ExactNumber] at the one place a money value is most likely to
 * pass through — `round(20.00, 2)` gave `20`. The second is worse and is why this was High rather
 * than cosmetic: `1.015` is `1.01499…` as a `Double`, so `× 100` rounds **down** to `101` and
 * `round(1.015, 2)` returned `1.01` instead of `1.02`. A different number, silently.
 *
 * `BigDecimal.setScale(n, HALF_UP)` answers both at once: it rounds correctly and the result
 * carries the scale it rounded to.
 *
 * The wrong-value tests come first here on purpose. A test asserting only the scale would pass
 * against a fix that rounded incorrectly, and a reader of this file should meet the serious half
 * first.
 */
class B67RoundExactScaleTest {

    private fun num(n: Number) = UDM.Scalar(n)
    private fun round(x: Number, places: Number) =
        (MathFunctions.round(listOf(num(x), num(places))) as UDM.Scalar).value
    private fun round(x: Number) =
        (MathFunctions.round(listOf(num(x))) as UDM.Scalar).value

    private fun bd(s: String) = BigDecimal(s)

    /** What a user sees: the same renderer every output path uses. */
    private fun plain(x: Number, places: Number) =
        com.glomidco.utlx.core.format.ExactNumber.renderScalarText(round(x, places) as Number)

    // ── the wrong-value half ──

    @Test
    fun `a half that is not representable as a Double still rounds up`() {
        // The headline defect. Each of these came back one unit of the last place too low, because
        // scaling by a power of ten in Double rounds the already-inexact value down.
        assertEquals("1.02", plain(1.015, 2), "round(1.015, 2)")
        assertEquals("1.01", plain(1.005, 2), "round(1.005, 2)")
    }

    @Test
    fun `halves that happened to work still work`() {
        // These were correct before by luck of the binary representation. They must stay correct,
        // or the fix would have traded one set of wrong answers for another.
        assertEquals("2.68", plain(2.675, 2))
        assertEquals("8.84", plain(8.835, 2))
    }

    @Test
    fun `rounding down is unaffected`() {
        assertEquals("29.99", plain(29.994, 2))
        assertEquals("0.12", plain(0.1234, 2))
    }

    @Test
    fun `HALF_UP is symmetric about zero`() {
        // Stated rather than inherited: Math.round is NOT symmetric (it rounds -2.5 to -2), so the
        // mode has to be asserted or a later "simplification" could reintroduce that asymmetry.
        assertEquals("2.5", plain(2.45, 1))
        assertEquals("-2.5", plain(-2.45, 1))
    }

    // ── the scale half ──

    @Test
    fun `the result carries the scale that was asked for`() {
        // Asking for two decimals and getting one figure is the odd behaviour, and UTL-X can do
        // better than the float languages: B35 made a plain decimal a BigDecimal, so the scale is
        // representable. Among languages that HAVE the choice the vote is unanimous — SQL's
        // ROUND on NUMERIC keeps it, as does Python's Decimal.quantize; Python floats, R and PHP
        // drop it only because a float cannot hold one.
        //
        // Four conformance fixtures changed with this, and they were each internally ragged in the
        // way that prompted it — a years-employed column reading 4.6, 5.3, 3, 6.8, 2.5, where the
        // one whole number lost its decimal. They now read 3.0 and match their neighbours.
        assertEquals("20.00", plain(20.00, 2))
        assertEquals("20.00", plain(20, 2), "an integer rounded to 2 places gains the scale")
        assertEquals("30.00", plain(29.995, 2), "rounding up to a whole number keeps it")
        assertEquals("3.0", plain(3.04, 1))
        assertEquals("1200.0", plain(1200.0, 1))
        assertEquals("1.000", plain(1, 3))
    }

    @Test
    fun `the scale comes from the request, not from the input`() {
        assertEquals("1.5", plain(1.4999, 1))
        assertEquals("1.50", plain(1.4999, 2))
        assertEquals("29.99", plain(29.994, 2))
    }

    // ── what must NOT change ──

    @Test
    fun `one argument still rounds to an integer`() {
        // Documented behaviour that scripts depend on: round(x) is integral, not x.00.
        assertEquals(2.0, round(1.5))
        assertEquals(3.0, round(2.675))
        assertEquals(-2.0, round(-1.5))
    }

    @Test
    fun `places of zero gives a whole number`() {
        assertEquals("2", plain(1.5, 0))
        assertEquals("30", plain(29.5, 0))
    }

    @Test
    fun `a negative places count keeps the old integral path`() {
        // BigDecimal reads a negative scale as "round to tens", which this function has never
        // meant, so the pre-B67 behaviour is preserved deliberately.
        assertEquals(2.0, round(1.5, -1))
    }

    @Test
    fun `a non-finite value is passed through rather than throwing`() {
        // BigDecimal(Double.NaN.toString()) would throw; the old Double path is kept for these.
        assertTrue((round(Double.NaN, 2) as Double).isNaN())
        assertEquals(Double.POSITIVE_INFINITY, round(Double.POSITIVE_INFINITY, 2))
    }

    @Test
    fun `a Double is read by its shortest representation, not its binary expansion`() {
        // BigDecimal(0.1) is 0.1000000000000000055511151231257827; BigDecimal("0.1") is 0.1.
        // Reading through toString() is what keeps round(0.1, 3) from becoming nonsense — the
        // value is 0.100 at the requested scale, not a 55-digit expansion of it.
        assertEquals("0.100", plain(0.1, 3))
    }

    // ── the finance variants must agree ──

    private fun rendered(udm: UDM) =
        com.glomidco.utlx.core.format.ExactNumber.renderScalarText((udm as UDM.Scalar).value as Number)

    @Test
    fun `roundToCents carries two decimals, as its own documentation promised`() {
        // This one was a contract violation rather than a preference: the KDoc already said
        // `roundToCents(9.999) → 10.00` while the code returned `10`. Every caller in
        // examples/json/ is an invoice, payroll or billing figure.
        assertEquals("10.00", rendered(FinancialFunctions.roundToCents(listOf(num(9.999)))))
        assertEquals("20.00", rendered(FinancialFunctions.roundToCents(listOf(num(20)))))
        assertEquals("1.23", rendered(FinancialFunctions.roundToCents(listOf(num(1.234)))))
        assertEquals("1.24", rendered(FinancialFunctions.roundToCents(listOf(num(1.235)))))
    }

    @Test
    fun `roundToDecimalPlaces agrees with round, scale included`() {
        // Same operation under two names, so they must not differ — including in representation.
        assertEquals("20.00", rendered(FinancialFunctions.roundToDecimalPlaces(listOf(num(20), num(2)))))
        assertEquals("20.00", plain(20, 2))
    }

    @Test
    fun `all three functions round one value to the same NUMBER`() {
        // roundToDecimalPlaces used HALF_EVEN while roundToCents and round used HALF_UP, so the
        // same amount could round two ways depending on which function a script reached for.
        val x = 1.015
        val viaRound = round(x, 2)
        val viaCents = (FinancialFunctions.roundToCents(listOf(num(x))) as UDM.Scalar).value
        val viaPlaces = (FinancialFunctions.roundToDecimalPlaces(listOf(num(x), num(2))) as UDM.Scalar).value
        // Agreement is on the VALUE. roundToCents additionally carries the cents scale, which is
        // a representation difference, so compare numerically rather than by equals (BigDecimal's
        // equals is scale-sensitive: 1.02 != 1.020).
        assertEquals("1.02", com.glomidco.utlx.core.format.ExactNumber.renderScalarText(viaRound as Number))
        assertEquals(0, (viaRound as java.math.BigDecimal).compareTo(viaCents as java.math.BigDecimal),
            "roundToCents must agree with round on the value")
        assertEquals(0, viaRound.compareTo(viaPlaces as java.math.BigDecimal),
            "roundToDecimalPlaces must agree with round on the value")
    }
}
