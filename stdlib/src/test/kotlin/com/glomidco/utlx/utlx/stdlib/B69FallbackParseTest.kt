package com.glomidco.utlx.stdlib

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.date.DateFunctions
import com.glomidco.utlx.stdlib.math.ExtendedMathFunctions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * B69: `parseIntOr` / `parseDateOr` — parse with a fallback instead of an exception.
 *
 * The capability was found unreachable: `ConversionFunctions` held a `parseInt` and a `parseDate`
 * whose extra argument was a fallback for unparseable input, but their registrations had been
 * commented out as duplicates since 2025-10-18 and only the annotations were left, advertising
 * signatures nothing dispatched to.
 *
 * It was worth exposing rather than deleting, because nothing else reaches it. `parseInt` throws,
 * and a thrown error is not a value UTL-X can defend against — `parseInt(x) ?? 0` and
 * `coalesce(parseInt(x), 0)` both fail — so for a CSV column that is mostly numeric with the odd
 * `"N/A"`, there was no way to say "default it to 0" without pre-validating every field.
 *
 * Exposed as **delegating wrappers** rather than by registering the old copies, which had drifted:
 * `ConversionFunctions::parseInt` returned a Double via `parseNumber`, and its `parseDate` returned
 * `UDM.Scalar(date.toString())` — a string, not a typed date, with a comment admitting as much. A
 * wrapper cannot drift from the function it calls, which is the whole point after
 * [B73], where two implementations of one function disagreed for months.
 */
class B69FallbackParseTest {

    private fun intOr(text: Any?, fallback: Any?) =
        (ExtendedMathFunctions.parseIntOr(listOf(UDM.Scalar(text), UDM.Scalar(fallback))) as UDM.Scalar).value

    @Test
    fun `a valid integer parses exactly as parseInt does`() {
        assertEquals(42.0, intOr("42", 0))
        assertEquals(-10.0, intOr("-10", 0))
        // Delegation, not reimplementation: the same input through both must agree.
        assertEquals(
            (ExtendedMathFunctions.parseInt(listOf(UDM.Scalar("7"))) as UDM.Scalar).value,
            intOr("7", 0)
        )
    }

    @Test
    fun `unparseable input yields the fallback instead of throwing`() {
        // The fallback is returned VERBATIM, not coerced: pass Int 0 and you get Int 0 back, where
        // a successful parse yields a Double (parseInt does `.toInt().toDouble()`). That asymmetry
        // is deliberate — coercing would break a null or textual fallback, which are the useful
        // cases — and it is pinned here because it is the kind of detail that surprises later.
        assertEquals(0, intOr("N/A", 0))
        assertEquals(0, intOr("", 0))
        assertEquals(-1, intOr("twelve", -1))
        assertEquals(null, intOr("nope", null))
        assertEquals("unknown", intOr("nope", "unknown"))
    }

    @Test
    fun `a successful parse is numeric, and the fallback is whatever was given`() {
        // Stated as its own test so the contract is explicit rather than incidental.
        assertEquals(42.0, intOr("42", 0))      // parsed -> Double, as parseInt returns
        assertEquals(0, intOr("x", 0))          // fallback -> exactly what the caller passed
    }

    @Test
    fun `the canonical function still throws, so the fallback form is the only way`() {
        // This is why the wrapper exists rather than a `??` at the call site: there is no null to
        // coalesce, the call fails outright.
        assertFailsWith<FunctionArgumentException> {
            ExtendedMathFunctions.parseInt(listOf(UDM.Scalar("N/A")))
        }
    }

    @Test
    fun `parseDateOr keeps the TYPED date, which the retired copy did not`() {
        val ok = DateFunctions.parseDateOr(listOf(UDM.Scalar("2020-03-15"), UDM.Scalar(null)))
        // UDM.Date, not UDM.Scalar("2020-03-15"): the unregistered copy returned a string, and
        // that weakening is exactly what delegating avoids.
        assertTrue(ok is UDM.Date, "expected a typed date, got ${ok::class.simpleName}: $ok")
    }

    @Test
    fun `parseDateOr falls back on junk, and honours an explicit pattern`() {
        val bad = DateFunctions.parseDateOr(listOf(UDM.Scalar("not a date"), UDM.Scalar(null)))
        assertEquals(null, (bad as UDM.Scalar).value)

        // Third argument is the PATTERN, passed through to parseDate — the fallback is second,
        // because parseDate's own third slot is the locale and could not be reused.
        val withPattern = DateFunctions.parseDateOr(
            listOf(UDM.Scalar("15/03/2020"), UDM.Scalar(null), UDM.Scalar("dd/MM/yyyy"))
        )
        assertTrue(withPattern is UDM.Date, "expected a typed date, got $withPattern")
    }

    @Test
    fun `both names are registered and callable`() {
        // The defect this closes was precisely that the capability existed and was NOT callable.
        assertTrue(StandardLibrary.hasFunction("parseIntOr"), "parseIntOr must be registered")
        assertTrue(StandardLibrary.hasFunction("parseDateOr"), "parseDateOr must be registered")
    }
}
