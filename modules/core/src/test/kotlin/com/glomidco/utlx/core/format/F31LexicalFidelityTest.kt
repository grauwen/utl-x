package com.glomidco.utlx.core.format

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F31 — **lexical fidelity: the written form of a number, where no numeric type can hold it.**
 *
 * B35 made the numeric model exact, so `100.10` keeps its scale and `9007199254740993` survives.
 * Three shapes still arrive with the right *value* and the wrong *spelling*, because the loss is
 * not precision — it is notation, and notation has nowhere to live in a `Long`, a `BigInteger` or
 * a `BigDecimal`:
 *
 *   -0  → 0        (BigDecimal has no negative zero; Long cannot hold one)
 *   1e3 → 1000     (Double cannot record that an exponent was written)
 *   +2  → 2        (nothing records that a sign was written)
 *
 * `isLexicallyLossy` names exactly those shapes, and under
 * [ParseOptions.preserveNumericLexeme] the free-text readers decline to make them numbers at all —
 * the same move B39/B40 already make for a zero-padded `007`.
 *
 * The boundaries are the test. A predicate that is too wide silently turns real numbers into
 * strings; too narrow and the round-trip the option exists for still fails.
 */
class F31LexicalFidelityTest {

    // ── the three lossy shapes ──

    @Test
    fun `negative zero is lossy in every spelling`() {
        for (s in listOf("-0", "-0.", "-0.0", "-0.00", "-00", "-0.000000")) {
            assertTrue(ExactNumber.isLexicallyLossy(s), "'$s' is a negative zero")
        }
    }

    @Test
    fun `exponent notation is lossy`() {
        for (s in listOf("1e3", "1E3", "1.5e2", "1e-3", "-2.5E+10", ".5e1", "1.e4", "+1e2")) {
            assertTrue(ExactNumber.isLexicallyLossy(s), "'$s' is written with an exponent")
        }
    }

    @Test
    fun `an explicit plus is lossy`() {
        for (s in listOf("+2", "+1.5", "+0", "+.5", "+2.")) {
            assertTrue(ExactNumber.isLexicallyLossy(s), "'$s' carries a written sign")
        }
    }

    // ── the boundaries: everything the numeric model CAN spell back ──

    @Test
    fun `an ordinary number is not lossy`() {
        // The important half. If any of these matched, the option would turn real numbers into
        // strings and every arithmetic script under it would break.
        for (s in listOf("0", "0.0", "0.00", "2", "-1", "-10", "-0.1", "-1.5", "100.10",
                         "9007199254740993", "1.0")) {
            assertFalse(ExactNumber.isLexicallyLossy(s), "'$s' round-trips exactly as written")
        }
    }

    @Test
    fun `zero is not a negative zero`() {
        // `-0+` must not be read as "zero, optionally signed".
        assertFalse(ExactNumber.isLexicallyLossy("0"))
        assertFalse(ExactNumber.isLexicallyLossy("00"))   // zero-padded — B40's business, not F31's
    }

    @Test
    fun `YAML's deliberate spellings are left alone`() {
        // B40's warning: these are intentional YAML, not accidents, and they must keep reaching
        // SnakeYAML's own constructs.
        for (s in listOf("0x1F", "0o17", "1_000", "1:30", ".inf", "-.inf", ".nan", "0x1p3")) {
            assertFalse(ExactNumber.isLexicallyLossy(s), "'$s' is a deliberate spelling")
        }
    }

    @Test
    fun `non-numeric text is not lossy`() {
        for (s in listOf("", "   ", "abc", "-0abc", "1e", "e3", "1e+", "--0", "+-2", "-")) {
            assertFalse(ExactNumber.isLexicallyLossy(s), "'$s' is not a number at all")
        }
    }

    @Test
    fun `surrounding whitespace is ignored, like every other predicate here`() {
        assertTrue(ExactNumber.isLexicallyLossy("  -0  "))
        assertTrue(ExactNumber.isLexicallyLossy("\t1e3\n"))
    }

    // ── the option is a third axis, not a rung on the strictness ladder ──

    @Test
    fun `no profile turns fidelity on`() {
        // A guard wants STRICT *and* fidelity. Folding fidelity into the ladder would force a
        // choice between them, so the ladder must leave it alone in all three positions.
        for (p in ParseProfile.values()) {
            assertFalse(ParseOptions.of(p).preserveNumericLexeme,
                "profile $p must not imply lexical fidelity")
        }
        assertFalse(ParseOptions.DEFAULT.preserveNumericLexeme)
    }

    @Test
    fun `fidelity composes with every profile`() {
        for (p in ParseProfile.values()) {
            val o = ParseOptions.of(p, preserveNumericLexeme = true)
            assertTrue(o.preserveNumericLexeme, "fidelity must be selectable under $p")
            assertEquals(p, o.profile, "turning fidelity on must not move the profile")
        }
    }

    @Test
    fun `fidelity does not disturb the profile's own fields`() {
        // Orthogonality, asserted rather than assumed: strict stays strict in every other respect.
        val strict = ParseOptions.of(ParseProfile.STRICT)
        val strictPlus = ParseOptions.of(ParseProfile.STRICT, preserveNumericLexeme = true)
        assertEquals(strict.copy(preserveNumericLexeme = true), strictPlus)
        assertEquals(64, strictPlus.maxDepth)
        assertFalse(strictPlus.allowDuplicateKeys)
        assertFalse(strictPlus.allowDoctype)
    }
}
