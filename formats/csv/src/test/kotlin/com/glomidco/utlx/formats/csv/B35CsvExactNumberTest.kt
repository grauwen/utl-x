package com.glomidco.utlx.formats.csv

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B35 — CSV had no integer branch at all: every numeric field went through `toDoubleOrNull`, so it
 * corrupted int64 values that JSON preserved and dropped decimal precision. These tests pin both
 * the exactness and the thing that makes the change safe — **the accept/reject decision is
 * unchanged**, so no field that used to parse as text now parses as a number, or the reverse.
 */
class B35CsvExactNumberTest {

    private fun firstRow(csv: String): Map<String, UDM> {
        val arr = CSVParser(csv.toByteArray()).parse() as UDM.Array
        return (arr.elements[0] as UDM.Object).properties
    }

    private fun cell(csv: String, key: String): Any? = (firstRow(csv)[key] as UDM.Scalar).value

    @Test
    fun `an integer above 2^53 is preserved`() {
        assertEquals(9007199254740993L, cell("id\n9007199254740993\n", "id"))
    }

    @Test
    fun `a decimal keeps its scale`() {
        val v = cell("price\n100.10\n", "price")
        assertEquals(BigDecimal("100.10"), v)
        assertEquals(2, (v as BigDecimal).scale())
    }

    @Test
    fun `a decimal beyond Double precision is exact`() {
        assertEquals(BigDecimal("12345678901234567.89"), cell("v\n12345678901234567.89\n", "v"))
    }

    @Test
    fun `a negative decimal is exact`() {
        assertEquals(BigDecimal("-0.50"), cell("v\n-0.50\n", "v"))
    }

    @Test
    fun `exponent notation stays a Double`() {
        assertTrue(cell("v\n1.5e3\n", "v") is Double)
    }

    @Test
    fun `surrounding whitespace is still tolerated`() {
        assertEquals(42L, cell("v\n  42  \n", "v"))
    }

    // ── Acceptance is unchanged: these must still be strings, and these must still be numbers ──

    @Test
    fun `non-numeric fields are still strings`() {
        for (text in listOf("abc", "1,5", "1.2.3", "12a", "--1", "0x")) {
            val v = cell("v\n\"$text\"\n", "v")
            assertTrue(v is String, "'$text' should still parse as a string, got ${v?.javaClass}")
        }
    }

    @Test
    fun `an empty field is still null`() {
        // Needs a real row: a trailing newline alone produces no rows at all.
        assertEquals(null, cell("a,b\n,1\n", "a"))
    }

    @Test
    fun `booleans are still booleans, not numbers`() {
        assertEquals(true, cell("v\ntrue\n", "v"))
    }

    @Test
    fun `a preserved decimal serialises back as written`() {
        val udm = CSVParser("price\n100.10\n".toByteArray()).parse()
        val out = CSVSerializer(includeHeaders = true).serialize(udm)
        assertTrue(out.contains("100.10"), "trailing zero lost on output: $out")
    }
}
