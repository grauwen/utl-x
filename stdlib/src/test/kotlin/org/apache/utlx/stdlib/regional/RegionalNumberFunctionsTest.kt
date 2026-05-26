package org.apache.utlx.stdlib.regional

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F19: Complete test coverage for RegionalNumberFunctions.
 * 8 functions × (happy path + edge cases + arg variants + errors) = comprehensive.
 */
class RegionalNumberFunctionsTest {

    // =========================================================================
    // parseUSNumber(string) — 1 arg
    // =========================================================================

    @Test fun `parseUSNumber - standard format`() {
        assertEquals(1234.56, result("parseUSNumber", "1,234.56"))
    }

    @Test fun `parseUSNumber - no thousands separator`() {
        assertEquals(1234.56, result("parseUSNumber", "1234.56"))
    }

    @Test fun `parseUSNumber - integer`() {
        assertEquals(1234.0, result("parseUSNumber", "1,234"))
    }

    @Test fun `parseUSNumber - zero`() {
        assertEquals(0.0, result("parseUSNumber", "0"))
    }

    @Test fun `parseUSNumber - negative`() {
        assertEquals(-1234.56, result("parseUSNumber", "-1,234.56"))
    }

    @Test fun `parseUSNumber - large number`() {
        assertEquals(1234567.89, result("parseUSNumber", "1,234,567.89"))
    }

    @Test fun `parseUSNumber - no decimals`() {
        assertEquals(1000.0, result("parseUSNumber", "1,000"))
    }

    @Test fun `parseUSNumber - many decimals`() {
        assertEquals(1.123456, result("parseUSNumber", "1.123456"))
    }

    @Test fun `parseUSNumber - invalid throws`() {
        assertThrows<Exception> { RegionalNumberFunctions.parseUSNumber(listOf(UDM.Scalar("abc"))) }
    }

    @Test fun `parseUSNumber - missing args throws`() {
        assertThrows<Exception> { RegionalNumberFunctions.parseUSNumber(emptyList()) }
    }

    // =========================================================================
    // parseEUNumber(string) — 1 arg
    // =========================================================================

    @Test fun `parseEUNumber - standard format`() {
        assertEquals(1234.56, result("parseEUNumber", "1.234,56"))
    }

    @Test fun `parseEUNumber - no thousands separator`() {
        assertEquals(1234.56, result("parseEUNumber", "1234,56"))
    }

    @Test fun `parseEUNumber - integer`() {
        assertEquals(1234.0, result("parseEUNumber", "1.234"))
    }

    @Test fun `parseEUNumber - zero`() {
        assertEquals(0.0, result("parseEUNumber", "0"))
    }

    @Test fun `parseEUNumber - negative`() {
        assertEquals(-1234.56, result("parseEUNumber", "-1.234,56"))
    }

    @Test fun `parseEUNumber - large number`() {
        assertEquals(1234567.89, result("parseEUNumber", "1.234.567,89"))
    }

    @Test fun `parseEUNumber - invalid throws`() {
        assertThrows<Exception> { RegionalNumberFunctions.parseEUNumber(listOf(UDM.Scalar("abc"))) }
    }

    // =========================================================================
    // parseFrenchNumber(string) — 1 arg
    // =========================================================================

    @Test fun `parseFrenchNumber - standard format with space`() {
        assertEquals(1234.56, result("parseFrenchNumber", "1 234,56"))
    }

    @Test fun `parseFrenchNumber - non-breaking space`() {
        assertEquals(1234.56, result("parseFrenchNumber", "1\u00A0234,56"))
    }

    @Test fun `parseFrenchNumber - narrow no-break space`() {
        assertEquals(1234.56, result("parseFrenchNumber", "1\u202F234,56"))
    }

    @Test fun `parseFrenchNumber - no thousands`() {
        assertEquals(1234.56, result("parseFrenchNumber", "1234,56"))
    }

    @Test fun `parseFrenchNumber - zero`() {
        assertEquals(0.0, result("parseFrenchNumber", "0"))
    }

    @Test fun `parseFrenchNumber - negative`() {
        assertEquals(-1234.56, result("parseFrenchNumber", "-1 234,56"))
    }

    @Test fun `parseFrenchNumber - invalid throws`() {
        assertThrows<Exception> { RegionalNumberFunctions.parseFrenchNumber(listOf(UDM.Scalar("abc"))) }
    }

    // =========================================================================
    // parseSwissNumber(string) — 1 arg
    // =========================================================================

    @Test fun `parseSwissNumber - standard format`() {
        assertEquals(1234.56, result("parseSwissNumber", "1'234.56"))
    }

    @Test fun `parseSwissNumber - no thousands`() {
        assertEquals(1234.56, result("parseSwissNumber", "1234.56"))
    }

    @Test fun `parseSwissNumber - zero`() {
        assertEquals(0.0, result("parseSwissNumber", "0"))
    }

    @Test fun `parseSwissNumber - negative`() {
        assertEquals(-1234.56, result("parseSwissNumber", "-1'234.56"))
    }

    @Test fun `parseSwissNumber - large number`() {
        assertEquals(1234567.89, result("parseSwissNumber", "1'234'567.89"))
    }

    @Test fun `parseSwissNumber - invalid throws`() {
        assertThrows<Exception> { RegionalNumberFunctions.parseSwissNumber(listOf(UDM.Scalar("abc"))) }
    }

    // =========================================================================
    // renderUSNumber(value, decimals?, thousands?) — 1-3 args
    // =========================================================================

    @Test fun `renderUSNumber - 1 arg default`() {
        assertEquals("1,234.56", render("renderUSNumber", 1234.56))
    }

    @Test fun `renderUSNumber - 2 args custom decimals`() {
        assertEquals("1,234.6", render("renderUSNumber", 1234.56, 1))
    }

    @Test fun `renderUSNumber - 3 args no thousands`() {
        assertEquals("1234.56", render("renderUSNumber", 1234.56, 2, false))
    }

    @Test fun `renderUSNumber - zero`() {
        assertEquals("0.00", render("renderUSNumber", 0.0))
    }

    @Test fun `renderUSNumber - negative`() {
        assertEquals("-1,234.56", render("renderUSNumber", -1234.56))
    }

    @Test fun `renderUSNumber - no decimals`() {
        assertEquals("1,235", render("renderUSNumber", 1234.56, 0))
    }

    @Test fun `renderUSNumber - large number`() {
        assertEquals("1,234,567.89", render("renderUSNumber", 1234567.89))
    }

    @Test fun `renderUSNumber - wrong arg count throws`() {
        assertThrows<Exception> { RegionalNumberFunctions.renderUSNumber(emptyList()) }
    }

    @Test fun `renderUSNumber - string input converted`() {
        val result = RegionalNumberFunctions.renderUSNumber(listOf(UDM.Scalar("1234.56")))
        assertEquals("1,234.56", (result as UDM.Scalar).value)
    }

    // =========================================================================
    // renderEUNumber(value, decimals?, thousands?) — 1-3 args
    // =========================================================================

    @Test fun `renderEUNumber - 1 arg default`() {
        assertEquals("1.234,56", render("renderEUNumber", 1234.56))
    }

    @Test fun `renderEUNumber - 2 args custom decimals`() {
        assertEquals("1.234,6", render("renderEUNumber", 1234.56, 1))
    }

    @Test fun `renderEUNumber - 3 args no thousands`() {
        assertEquals("1234,56", render("renderEUNumber", 1234.56, 2, false))
    }

    @Test fun `renderEUNumber - zero`() {
        assertEquals("0,00", render("renderEUNumber", 0.0))
    }

    @Test fun `renderEUNumber - negative`() {
        assertEquals("-1.234,56", render("renderEUNumber", -1234.56))
    }

    @Test fun `renderEUNumber - no decimals`() {
        assertEquals("1.235", render("renderEUNumber", 1234.56, 0))
    }

    // =========================================================================
    // renderSwissNumber(value, decimals?, thousands?) — 1-3 args
    // =========================================================================

    @Test fun `renderSwissNumber - 1 arg default`() {
        assertEquals("1'234.56", render("renderSwissNumber", 1234.56))
    }

    @Test fun `renderSwissNumber - 2 args custom decimals`() {
        assertEquals("1'234.6", render("renderSwissNumber", 1234.56, 1))
    }

    @Test fun `renderSwissNumber - 3 args no thousands`() {
        assertEquals("1234.56", render("renderSwissNumber", 1234.56, 2, false))
    }

    @Test fun `renderSwissNumber - zero`() {
        assertEquals("0.00", render("renderSwissNumber", 0.0))
    }

    @Test fun `renderSwissNumber - negative`() {
        assertEquals("-1'234.56", render("renderSwissNumber", -1234.56))
    }

    // =========================================================================
    // renderFrenchNumber(value, decimals?, thousands?) — 1-3 args
    // =========================================================================

    @Test fun `renderFrenchNumber - 1 arg default`() {
        val result = render("renderFrenchNumber", 1234.56)
        // French uses narrow no-break space (U+202F) or non-breaking space (U+00A0)
        assertTrue(result.contains("234") && result.contains(",56"), "Should be French format: $result")
        assertTrue(result.contains("1") && result.endsWith(",56"), "Should have comma decimal: $result")
    }

    @Test fun `renderFrenchNumber - 3 args no thousands`() {
        assertEquals("1234,56", render("renderFrenchNumber", 1234.56, 2, false))
    }

    @Test fun `renderFrenchNumber - zero`() {
        assertEquals("0,00", render("renderFrenchNumber", 0.0))
    }

    @Test fun `renderFrenchNumber - negative`() {
        val result = render("renderFrenchNumber", -1234.56)
        assertTrue(result.startsWith("-"), "Should be negative: $result")
        assertTrue(result.endsWith(",56"), "Should have comma decimal: $result")
    }

    // =========================================================================
    // Round-trip tests: parse → render → parse
    // =========================================================================

    @Test fun `roundtrip US`() {
        val original = 1234567.89
        val rendered = render("renderUSNumber", original)
        val parsed = result("parseUSNumber", rendered)
        assertEquals(original, parsed)
    }

    @Test fun `roundtrip EU`() {
        val original = 1234567.89
        val rendered = render("renderEUNumber", original)
        val parsed = result("parseEUNumber", rendered)
        assertEquals(original, parsed)
    }

    @Test fun `roundtrip Swiss`() {
        val original = 1234567.89
        val rendered = render("renderSwissNumber", original)
        val parsed = result("parseSwissNumber", rendered)
        assertEquals(original, parsed)
    }

    @Test fun `roundtrip French`() {
        val original = 1234567.89
        val rendered = render("renderFrenchNumber", original, 2, false)  // without thousands to avoid space issues
        val parsed = result("parseFrenchNumber", rendered)
        assertEquals(original, parsed)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun result(fn: String, input: String): Double {
        val r = when (fn) {
            "parseUSNumber" -> RegionalNumberFunctions.parseUSNumber(listOf(UDM.Scalar(input)))
            "parseEUNumber" -> RegionalNumberFunctions.parseEUNumber(listOf(UDM.Scalar(input)))
            "parseFrenchNumber" -> RegionalNumberFunctions.parseFrenchNumber(listOf(UDM.Scalar(input)))
            "parseSwissNumber" -> RegionalNumberFunctions.parseSwissNumber(listOf(UDM.Scalar(input)))
            else -> throw IllegalArgumentException("Unknown function: $fn")
        }
        return (r as UDM.Scalar).value as Double
    }

    private fun render(fn: String, value: Double, decimals: Int? = null, thousands: Boolean? = null): String {
        val args = mutableListOf<UDM>(UDM.Scalar(value))
        if (decimals != null) args.add(UDM.Scalar(decimals))
        if (thousands != null) args.add(UDM.Scalar(thousands))

        val r = when (fn) {
            "renderUSNumber" -> RegionalNumberFunctions.renderUSNumber(args)
            "renderEUNumber" -> RegionalNumberFunctions.renderEUNumber(args)
            "renderSwissNumber" -> RegionalNumberFunctions.renderSwissNumber(args)
            "renderFrenchNumber" -> RegionalNumberFunctions.renderFrenchNumber(args)
            else -> throw IllegalArgumentException("Unknown function: $fn")
        }
        return (r as UDM.Scalar).value as String
    }
}
