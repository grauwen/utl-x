package com.glomidco.utlx.core.format

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B35 — `ExactNumber` is the single place the format parsers decide what numeric type a written
 * value becomes, so the rule it implements is pinned here rather than inferred from four parsers.
 *
 * The rule is **exactness follows the written form**. Two properties matter more than any individual
 * case and are asserted as properties, not examples:
 *
 *  - **acceptance parity** — [ExactNumber.ofOrNull] returns null for exactly the inputs
 *    `toDoubleOrNull()` rejects. This is what makes the change safe to apply inside CSV and XML,
 *    which use it to decide whether text is a number at all: no text that was a number stops being
 *    one, and no text that was a string starts being one. Only the representation improves.
 *  - **round-trip** — a value that came in as a plain form renders back as written, which is the
 *    property that was actually broken: the data model always accepted exact numbers, the parsers
 *    simply never produced them.
 */
class ExactNumberTest {

    // ── Integral forms stay integral ──

    @Test
    fun `plain integer becomes Long`() {
        assertEquals(42L, ExactNumber.ofOrNull("42"))
        assertEquals(-7L, ExactNumber.ofOrNull("-7"))
        assertEquals(0L, ExactNumber.ofOrNull("0"))
    }

    @Test
    fun `integer above 2^53 is kept exactly — the original complaint`() {
        // As a Double this is 9007199254740992, which is how two different integers came to
        // compare equal.
        assertEquals(9007199254740993L, ExactNumber.ofOrNull("9007199254740993"))
    }

    @Test
    fun `integer wider than Long becomes BigInteger rather than Infinity`() {
        val digits = "1".repeat(400)
        assertEquals(BigInteger(digits), ExactNumber.ofOrNull(digits))
        // The previous Double path produced Infinity here, which JSON cannot even represent.
        assertEquals(BigInteger("9223372036854775808"), ExactNumber.ofOrNull("9223372036854775808"))
    }

    // ── Decimal forms keep their scale ──

    @Test
    fun `plain decimal keeps its scale so trailing zeros survive`() {
        val v = ExactNumber.ofOrNull("100.10")
        assertEquals(BigDecimal("100.10"), v)
        // Scale is the point: 100.10 and 100.1 are the same quantity but not the same written value.
        assertEquals(2, (v as BigDecimal).scale())
    }

    @Test
    fun `decimal beyond Double precision is kept exactly`() {
        assertEquals(BigDecimal("12345678901234567.89"), ExactNumber.ofOrNull("12345678901234567.89"))
    }

    @Test
    fun `decimal with digits on only one side of the point`() {
        assertEquals(BigDecimal(".5"), ExactNumber.ofOrNull(".5"))
        assertEquals(BigDecimal("5."), ExactNumber.ofOrNull("5."))
    }

    // ── Exponent and special forms stay on the Double path ──

    @Test
    fun `exponent notation stays a Double`() {
        assertTrue(ExactNumber.ofOrNull("1e10") is Double)
        assertTrue(ExactNumber.ofOrNull("2.5E-1") is Double)
        assertTrue(ExactNumber.ofOrNull("-1.5e-3") is Double)
    }

    @Test
    fun `non-finite and suffixed forms stay a Double`() {
        assertTrue(ExactNumber.ofOrNull("NaN") is Double)
        assertTrue(ExactNumber.ofOrNull("Infinity") is Double)
        assertTrue(ExactNumber.ofOrNull("1d") is Double)
    }

    // ── Acceptance parity: the property that makes this safe in CSV and XML ──

    @Test
    fun `accepts and rejects exactly what toDoubleOrNull does`() {
        val samples = listOf(
            // numeric
            "0", "42", "-7", "+3", "100.10", ".5", "5.", "1e10", "2.5E-1", "-1.5e-3",
            "NaN", "Infinity", "-Infinity", "1d", "1f", "0x1p3", "  12  ", "9".repeat(400),
            // not numeric
            "", "   ", "abc", "1,5", "1.2.3", "--1", "1-", "0x", "null", "true", "12a", "€5"
        )
        for (s in samples) {
            val double = s.trim().toDoubleOrNull()
            val exact = ExactNumber.ofOrNull(s)
            assertEquals(
                double == null, exact == null,
                "acceptance diverged for '$s': toDoubleOrNull=$double ofOrNull=$exact"
            )
        }
    }

    @Test
    fun `whitespace is ignored, matching Double parsing`() {
        assertEquals(12L, ExactNumber.ofOrNull("  12  "))
        assertNull(ExactNumber.ofOrNull("   "))
    }

    // ── Rendering ──

    @Test
    fun `renders exact types as written`() {
        assertEquals("100.10", ExactNumber.renderExactOrNull(BigDecimal("100.10")))
        assertEquals("9007199254740993", ExactNumber.renderExactOrNull(9007199254740993L))
        assertEquals("42", ExactNumber.renderExactOrNull(BigInteger("42")))
    }

    @Test
    fun `renders small decimals plainly rather than in exponent form`() {
        // BigDecimal.toString() would give 1E-7 here, which is exact but not what was written.
        assertEquals("0.0000001", ExactNumber.renderExactOrNull(BigDecimal("0.0000001")))
    }

    @Test
    fun `leaves Double and Float to the caller's own formatting`() {
        assertNull(ExactNumber.renderExactOrNull(1.5))
        assertNull(ExactNumber.renderExactOrNull(1.5f))
        assertNull(ExactNumber.renderExactOrNull(Double.NaN))
    }

    @Test
    fun `parse then render round-trips every plain form`() {
        for (s in listOf("0", "42", "-7", "100.10", "12345678901234567.89", "9007199254740993",
                         "0.0000001", "9223372036854775808")) {
            val parsed = ExactNumber.ofOrNull(s)!!
            assertEquals(s, ExactNumber.renderExactOrNull(parsed), "round trip failed for '$s'")
        }
    }

    // ── B37: integrality is a Long round-trip, not a modulo test ──

    @Test
    fun `an integral double within Long range round-trips`() {
        assertEquals(1L, ExactNumber.integralLongOrNull(1.0))
        assertEquals(-7L, ExactNumber.integralLongOrNull(-7.0))
        assertEquals(0L, ExactNumber.integralLongOrNull(0.0))
        assertEquals(9007199254740992L, ExactNumber.integralLongOrNull(9007199254740992.0))
    }

    @Test
    fun `a double too large for Long is refused rather than saturated`() {
        // `d % 1.0 == 0.0` is true for both of these, and Double.toLong() SATURATES, so the old
        // predicate printed 9223372036854775807 for each — the same wrong number for two values
        // thirty orders of magnitude apart.
        assertNull(ExactNumber.integralLongOrNull(1e30))
        assertNull(ExactNumber.integralLongOrNull(1e300))
        assertNull(ExactNumber.integralLongOrNull(-1e30))
        assertTrue(1e30 % 1.0 == 0.0, "the old predicate really did accept 1e30")
        assertEquals(Long.MAX_VALUE, 1e30.toLong(), "toLong() really does saturate")
    }

    @Test
    fun `a non-integral double is refused`() {
        assertNull(ExactNumber.integralLongOrNull(1.5))
        assertNull(ExactNumber.integralLongOrNull(-0.25))
    }

    @Test
    fun `a non-finite double is refused`() {
        assertNull(ExactNumber.integralLongOrNull(Double.NaN))
        assertNull(ExactNumber.integralLongOrNull(Double.POSITIVE_INFINITY))
        assertNull(ExactNumber.integralLongOrNull(Double.NEGATIVE_INFINITY))
    }

    // ── B39: inferring a number from free TEXT is stricter than parsing a known lexeme ──

    /**
     * Reported from the field: a Centric ORS POS XML feed where `RetailStoreID` is `04.6166` and
     * article numbers look like `0100031012`. Every numeric-looking text node became a number, the
     * leading zeros went with it, and nothing in a script could recover them — the digits are gone
     * before the script runs, and `toString()` returns the normalised form.
     *
     * The rule is JSON's own number grammar: `0` or `[1-9]\d*`, so a leading zero is not a number.
     */
    @Test
    fun `a leading zero means it is an identifier, not a number`() {
        assertNull(ExactNumber.inferredOrNull("04.6166"), "a store id must stay text")
        assertNull(ExactNumber.inferredOrNull("0100031012"), "an article number must stay text")
        assertNull(ExactNumber.inferredOrNull("007"))
        assertNull(ExactNumber.inferredOrNull("-007"))
    }

    @Test
    fun `ordinary numbers are still inferred`() {
        assertEquals(12L, ExactNumber.inferredOrNull("12"))
        assertEquals(-7L, ExactNumber.inferredOrNull("-7"))
        assertEquals(0L, ExactNumber.inferredOrNull("0"))
        assertEquals(BigDecimal("1.50"), ExactNumber.inferredOrNull("1.50"))
        assertEquals(BigDecimal("0.5"), ExactNumber.inferredOrNull("0.5"))
        assertTrue(ExactNumber.inferredOrNull("1e5") is Double)
    }

    @Test
    fun `a long identifier that is canonical stays an exact number`() {
        // An 18-digit barcode with no leading zero is a canonical number, and it is now exact —
        // the digit corruption that prompted the report is gone. Whether such a code *should* be
        // typed as a number is a separate question from whether its digits survive.
        assertEquals(123456789012345678L, ExactNumber.inferredOrNull("123456789012345678"))
        assertEquals(BigInteger("2200000000000000000012"),
            ExactNumber.inferredOrNull("2200000000000000000012"))
    }

    @Test
    fun `text that is not a number at all stays text`() {
        for (t in listOf("", "   ", "abc", "1,5", "1.2.3", "+5", "1_000", "0x1F", "NaN", "Infinity")) {
            assertNull(ExactNumber.inferredOrNull(t), "'$t' should not be inferred as a number")
        }
    }

    @Test
    fun `inference is stricter than parsing a known lexeme`() {
        // The two entry points answer different questions, and the asymmetry is deliberate: JSON's
        // lexer has already accepted its token, so refusing it there would turn a parse into an
        // error.
        assertNull(ExactNumber.inferredOrNull("007"))
        assertEquals(7L, ExactNumber.ofOrNull("007"))
        assertNull(ExactNumber.inferredOrNull("NaN"))
        assertTrue(ExactNumber.ofOrNull("NaN") is Double)
    }

    // ── B40: the narrower zero-padded test, for parsers with their own number syntax ──

    @Test
    fun `isZeroPadded recognises a padded decimal`() {
        assertTrue(ExactNumber.isZeroPadded("04.6166"))
        assertTrue(ExactNumber.isZeroPadded("0100031012"))
        assertTrue(ExactNumber.isZeroPadded("007"))
        assertTrue(ExactNumber.isZeroPadded("-007"))
    }

    @Test
    fun `isZeroPadded does not fire on a canonical number`() {
        // The zero in `0` and `0.5` is not padding.
        for (t in listOf("0", "0.5", "0.50", "12", "-7", "1.50", "1e5")) {
            assertTrue(!ExactNumber.isZeroPadded(t), "'$t' is not zero-padded")
        }
    }

    @Test
    fun `isZeroPadded leaves other number syntaxes alone`() {
        // These are deliberate YAML spellings, not padded decimals, so YAML keeps resolving them.
        for (t in listOf("0x1F", "0o17", "1_000", "1:30", "0b101", ".inf", "abc")) {
            assertTrue(!ExactNumber.isZeroPadded(t), "'$t' should be left to the library")
        }
    }

    // ── The narrow variant used by parsers that delegate scalar construction ──

    @Test
    fun `plainDecimalOrNull takes only the plain decimal form`() {
        assertEquals(BigDecimal("19.99"), ExactNumber.plainDecimalOrNull("19.99"))
        assertNull(ExactNumber.plainDecimalOrNull("42"))        // integral — the library's own path
        assertNull(ExactNumber.plainDecimalOrNull("1e10"))
        assertNull(ExactNumber.plainDecimalOrNull(".inf"))
        assertNull(ExactNumber.plainDecimalOrNull(".nan"))
        assertNull(ExactNumber.plainDecimalOrNull("1_000.5"))
        assertNull(ExactNumber.plainDecimalOrNull("abc"))
    }
}
