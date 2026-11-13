package com.glomidco.utlx.stdlib.math

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals

/**
 * B02 — **number formatting must not depend on the machine's locale.**
 *
 * `formatNumber` built its `DecimalFormat` from the platform default, so `#,##0.00` produced
 * `1,234.56` in the US and `1.234,56` in Germany or France — the same transformation emitting
 * different data depending on where it ran. Tests passed on the author's machine and failed in
 * CI, which is the shape that makes this kind of defect expensive.
 *
 * The fix pins `DecimalFormatSymbols(Locale.US)` at the call site. That is easy to undo by
 * accident — `DecimalFormat(pattern)` is the obvious spelling and silently reintroduces the
 * dependency — so the guarantee is asserted here rather than left to a comment.
 *
 * A caller who *wants* locale-specific output has the regional functions for it
 * (`formatEUNumber`, `formatFrenchNumber`, …), which is why this one is deliberately fixed.
 */
class B02LocaleIndependenceTest {

    private fun format(pattern: String, value: Double): String {
        val r = ExtendedMathFunctions.formatNumber(listOf(UDM.Scalar(value), UDM.Scalar(pattern)))
        return ((r as UDM.Scalar).value as String)
    }

    /** Run [block] with the JVM default locale temporarily changed, then restore it. */
    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        return try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `grouping and decimal separators are the same in every locale`() {
        // Germany and France swap '.' and ',' relative to the US; India groups differently again.
        val locales = listOf(
            Locale.US, Locale.GERMANY, Locale.FRANCE, Locale.ITALY,
            Locale("hi", "IN"), Locale.JAPAN
        )
        for (locale in locales) {
            assertEquals("1,234.56", withLocale(locale) { format("#,##0.00", 1234.56) },
                "formatNumber must not follow the default locale, but did under $locale")
        }
    }

    @Test
    fun `larger groupings are stable too`() {
        // India's default grouping is 2,2,3 rather than 3,3,3 — a pattern that would show up
        // here if the symbols came from the platform.
        for (locale in listOf(Locale.US, Locale.GERMANY, Locale("hi", "IN"))) {
            assertEquals("1,234,567.89", withLocale(locale) { format("#,##0.00", 1234567.89) },
                "grouping changed under $locale")
        }
    }

    @Test
    fun `a pattern without grouping is unaffected as well`() {
        for (locale in listOf(Locale.US, Locale.FRANCE)) {
            assertEquals("1234.56", withLocale(locale) { format("0.00", 1234.56) })
        }
    }

    @Test
    fun `the default locale is left as it was found`() {
        // The helper restores it in a finally; if it did not, every test after this one in the
        // same JVM would inherit whatever was set last.
        val before = Locale.getDefault()
        withLocale(Locale.GERMANY) { format("#,##0.00", 1.0) }
        assertEquals(before, Locale.getDefault())
    }
}
