package com.glomidco.utlx.stdlib.date

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * F26 — epoch conversion (GitHub issue #7).
 *
 * `toEpochMillis` / `toEpochMicros` turn a date-time into the **number** a timestamp column wants;
 * `fromEpochMillis` / `fromEpochMicros` go back. The reporter's sink (Databricks Zerobus) validates
 * against a Delta table schema and needs `TIMESTAMP` as epoch microseconds.
 */
class F26EpochConversionTest {

    private fun s(v: String) = UDM.Scalar(v)
    private fun millis(vararg a: UDM) = (DateFunctions.toEpochMillis(a.toList()) as UDM.Scalar).value
    private fun micros(vararg a: UDM) = (DateFunctions.toEpochMicros(a.toList()) as UDM.Scalar).value

    // ── The reported case ──

    @Test
    fun `the reported example gives the reported values`() {
        val iso = "2026-11-04T14:36:30.438+01:00"
        assertEquals(1793799390438L, millis(s(iso)))
        assertEquals(1793799390438000L, micros(s(iso)))
    }

    @Test
    fun `the result is a Long, so JSON gets a plain integer`() {
        // Issue #7 asks for "a plain integer in JSON (not 1.79E15)". A Double would render in
        // exponent form above 1e7 AND lose precision past 2^53.
        assertTrue(millis(s("2026-11-04T13:36:30.438Z")) is Long)
        assertTrue(micros(s("2026-11-04T13:36:30.438Z")) is Long)
    }

    @Test
    fun `an offset is actually applied`() {
        assertEquals(millis(s("2026-11-04T13:36:30.438Z")), millis(s("2026-11-04T14:36:30.438+01:00")))
        assertEquals(millis(s("2026-11-04T13:36:30.438Z")), millis(s("2026-11-04T08:36:30.438-05:00")))
    }

    // ── Precision ──

    @Test
    fun `micros keeps sub-millisecond digits`() {
        // NOT millis * 1000, which would give ...438000 and silently drop the source's precision.
        assertEquals(1793799390438123L, micros(s("2026-11-04T13:36:30.438123Z")))
        assertEquals(1793799390438L, millis(s("2026-11-04T13:36:30.438123Z")))
    }

    @Test
    fun `a missing fractional part is zero`() {
        assertEquals(1793799390000L, millis(s("2026-11-04T13:36:30Z")))
        assertEquals(1793799390000000L, micros(s("2026-11-04T13:36:30Z")))
    }

    // ── It takes a value, not only a string ──

    @Test
    fun `a DateTime value works, so it composes with now and parseDate`() {
        val dt = UDM.DateTime(Instant.ofEpochMilli(1793799390438L))
        assertEquals(1793799390438L, millis(dt))
        assertEquals(1793799390438000L, micros(dt))
    }

    // ── A wall-clock reading is not an instant ──

    @Test
    fun `a value with no offset is rejected and the message says what to pass`() {
        val e = assertFailsWith<FunctionArgumentException> { millis(s("2026-11-04T14:36:30")) }
        assertTrue((e.message ?: "").contains("no UTC offset"), e.message)
        assertTrue((e.message ?: "").contains("second argument"), e.message)
    }

    @Test
    fun `a zone resolves a wall-clock reading`() {
        assertEquals(1793799390000L, millis(s("2026-11-04T14:36:30"), s("Europe/Amsterdam")))
        assertEquals(1793799390000L, millis(s("2026-11-04T13:36:30"), s("UTC")))
    }

    @Test
    fun `a date with a zone is midnight THERE, not midnight UTC`() {
        // The case a silent UTC assumption gets wrong by a whole day: 2026-11-04 in Amsterdam
        // begins at 23:00Z on the 3rd.
        assertEquals(1793746800000L, millis(s("2026-11-04"), s("Europe/Amsterdam")))
        assertEquals(1793750400000L, millis(s("2026-11-04"), s("UTC")))
    }

    @Test
    fun `an unknown zone and unreadable text are reported distinctly`() {
        assertTrue(assertFailsWith<FunctionArgumentException> {
            millis(s("2026-11-04T14:36:30"), s("Nowhere/Nothing"))
        }.message!!.contains("unknown time zone"))
        assertTrue(assertFailsWith<FunctionArgumentException> {
            millis(s("not a date"))
        }.message!!.contains("cannot read"))
    }

    // ── The inverses, asserted absolutely ──

    @Test
    fun `the inverses are anchored at the epoch itself`() {
        // A round trip alone cannot catch two errors that cancel; 0 -> 1970-01-01T00:00:00Z can.
        assertEquals(Instant.EPOCH, (DateFunctions.fromEpochMillis(listOf(UDM.Scalar(0L))) as UDM.DateTime).instant)
        assertEquals(Instant.EPOCH, (DateFunctions.fromEpochMicros(listOf(UDM.Scalar(0L))) as UDM.DateTime).instant)
    }

    @Test
    fun `the inverses produce the right instant`() {
        assertEquals(
            Instant.parse("2026-11-04T13:36:30.438Z"),
            (DateFunctions.fromEpochMillis(listOf(UDM.Scalar(1793799390438L))) as UDM.DateTime).instant
        )
        assertEquals(
            Instant.parse("2026-11-04T13:36:30.438123Z"),
            (DateFunctions.fromEpochMicros(listOf(UDM.Scalar(1793799390438123L))) as UDM.DateTime).instant
        )
    }

    @Test
    fun `before the epoch, micros floor rather than truncate toward zero`() {
        // A naive division lands a second out for negative values.
        assertEquals(
            Instant.parse("1969-12-31T23:59:59.999999Z"),
            (DateFunctions.fromEpochMicros(listOf(UDM.Scalar(-1L))) as UDM.DateTime).instant
        )
        assertEquals(-1L, micros(UDM.DateTime(Instant.parse("1969-12-31T23:59:59.999999Z"))))
    }

    @Test
    fun `round trips hold for both units`() {
        for (n in listOf(0L, 1000L, -1L, 1793799390438L)) {
            assertEquals(n, millis(DateFunctions.fromEpochMillis(listOf(UDM.Scalar(n)))))
        }
        for (n in listOf(0L, -1L, 1793799390438123L)) {
            assertEquals(n, micros(DateFunctions.fromEpochMicros(listOf(UDM.Scalar(n)))))
        }
    }
}
