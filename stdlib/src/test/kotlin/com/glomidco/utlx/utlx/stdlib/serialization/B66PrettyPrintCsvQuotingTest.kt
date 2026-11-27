package com.glomidco.utlx.stdlib.serialization

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B66 — `prettyPrintCSV` split on the delimiter with no notion of quoting.
 *
 *     in    ccy-allowed,"Field 'ccy' value 'GBP' is not one of [EUR, USD]"
 *     out   ccy-allowed | "Field 'ccy' value 'GBP' is not one of [EUR | USD]"
 *
 * Two defects in one line: the comma **inside the quoted value** became a column break, so a
 * two-column header sat over a three-field row; and the quotes **survived into the output**, where
 * they are not data but CSV's escaping, which an aligned view should consume.
 *
 * What made it worth fixing rather than documenting: the function is public, it is in the
 * catalogue, and the failure is not a refusal but a *wrong answer that looks right* — the table
 * renders, the columns line up, and one row quietly has an extra field. Anyone using it for a
 * report would believe it.
 *
 * Found while implementing [F33]: `--format table` first composed the CSV serializer with this
 * function, and the composition failed because of this. The shipped `--format table` renders from
 * the UDM directly and never used it.
 *
 * The fix shares `CSVParser.parseRows()` — the same reader every other CSV path uses — rather than
 * growing a second, weaker CSV reader beside it.
 */
class B66PrettyPrintCsvQuotingTest {

    private fun pretty(csv: String): String =
        (PrettyPrintFunctions.prettyPrintCSV(listOf(UDM.Scalar(csv))) as UDM.Scalar).value.toString()

    // ── the reported case ──

    @Test
    fun `a delimiter inside a quoted value is not a column break`() {
        val out = pretty("rule,message\nccy-allowed,\"Field 'ccy' value 'GBP' is not one of [EUR, USD]\"")
        val rows = out.lines()
        assertEquals(3, rows.size, "header, rule, one data row — got:\n$out")
        assertTrue(rows[2].trimEnd().endsWith("Field 'ccy' value 'GBP' is not one of [EUR, USD]"), out)
        // Exactly one separator on the data row: two columns, not three.
        assertEquals(1, rows[2].count { it == '|' }, "the quoted comma must not add a column:\n$out")
    }

    @Test
    fun `the quoting does not survive into the view`() {
        val out = pretty("a,b\nx,\"has, a comma\"")
        assertFalse(out.contains("\""), "CSV escaping is not data:\n$out")
    }

    @Test
    fun `an escaped quote renders as one quote`() {
        val out = pretty("a,b\nx,\"he said \"\"hi\"\"\"")
        assertTrue(out.contains("he said \"hi\""), out)
        assertFalse(out.contains("\"\""), "a doubled quote is CSV's escape, not the value: $out")
    }

    // ── regressions: the behaviour that was already right ──

    @Test
    fun `plain CSV aligns exactly as before`() {
        assertEquals(
            listOf("a | bb", "------", "1 | 2"),
            pretty("a,bb\n1,2").lines(),
        )
    }

    @Test
    fun `columns are padded to the widest cell`() {
        val rows = pretty("id,name\n1,Alice\n22,Bo").lines()
        // Note the trailing space on the header: this function pads every cell including the last,
        // so lines can end in whitespace. Pre-existing behaviour, asserted here so the quoting fix
        // is not blamed for it later — and deliberately NOT changed, since that would be a second,
        // unrelated change inside a bug fix. (F33's own renderer trims; the two differ.)
        assertEquals("id | name ", rows[0])
        assertEquals("1  | Alice", rows[2])
        assertEquals("22 | Bo", rows[3])
    }

    @Test
    fun `a single column still works`() {
        assertEquals(listOf("a", "-", "1"), pretty("a\n1").lines())
    }

    // ── it is a formatter: it must not throw on input that is not CSV ──

    @Test
    fun `unparseable input falls back rather than failing a report`() {
        // A formatter that throws turns a cosmetic problem into a broken pipeline, so the quote-
        // aware path falls back to the naive split rather than propagating a parse error.
        val out = pretty("a,b\n\"unterminated,x")
        assertTrue(out.isNotEmpty(), "must render something")
    }
}
