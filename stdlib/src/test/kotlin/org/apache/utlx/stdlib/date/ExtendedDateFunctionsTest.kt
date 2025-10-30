// stdlib/src/test/kotlin/org/apache/utlx/stdlib/date/ExtendedDateFunctionsTest.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Instant

class ExtendedDateFunctionsTest {

    private val baseDate = UDM.DateTime(Instant.parse("2025-10-17T14:30:45Z"))

    // ========== DATE COMPONENT EXTRACTION ==========

    @Test
    fun testDay() {
        val result = ExtendedDateFunctions.day(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(17.0, result.value)
    }

    @Test
    fun testDayFirstOfMonth() {
        val firstDate = UDM.DateTime(Instant.parse("2025-01-01T00:00:00Z"))
        val result = ExtendedDateFunctions.day(listOf(firstDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(1.0, result.value)
    }

    @Test
    fun testDayLastOfMonth() {
        val lastDate = UDM.DateTime(Instant.parse("2025-12-31T23:59:59Z"))
        val result = ExtendedDateFunctions.day(listOf(lastDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(31.0, result.value)
    }

    @Test
    fun testMonth() {
        val result = ExtendedDateFunctions.month(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(10.0, result.value) // October
    }

    @Test
    fun testMonthJanuary() {
        val janDate = UDM.DateTime(Instant.parse("2025-01-15T12:00:00Z"))
        val result = ExtendedDateFunctions.month(listOf(janDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(1.0, result.value)
    }

    @Test
    fun testMonthDecember() {
        val decDate = UDM.DateTime(Instant.parse("2025-12-25T12:00:00Z"))
        val result = ExtendedDateFunctions.month(listOf(decDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(12.0, result.value)
    }

    @Test
    fun testYear() {
        val result = ExtendedDateFunctions.year(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(2025.0, result.value)
    }

    @Test
    fun testYearLeapYear() {
        val leapYearDate = UDM.DateTime(Instant.parse("2024-02-29T12:00:00Z"))
        val result = ExtendedDateFunctions.year(listOf(leapYearDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(2024.0, result.value)
    }

    @Test
    fun testYearFutureYear() {
        val futureDate = UDM.DateTime(Instant.parse("2030-06-15T12:00:00Z"))
        val result = ExtendedDateFunctions.year(listOf(futureDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(2030.0, result.value)
    }

    @Test
    fun testHours() {
        val result = ExtendedDateFunctions.hours(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(14.0, result.value) // 2:30 PM
    }

    @Test
    fun testHoursMidnight() {
        val midnightDate = UDM.DateTime(Instant.parse("2025-10-17T00:00:00Z"))
        val result = ExtendedDateFunctions.hours(listOf(midnightDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(0.0, result.value)
    }

    @Test
    fun testHoursNoon() {
        val noonDate = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z"))
        val result = ExtendedDateFunctions.hours(listOf(noonDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(12.0, result.value)
    }

    @Test
    fun testHoursLateEvening() {
        val eveningDate = UDM.DateTime(Instant.parse("2025-10-17T23:30:00Z"))
        val result = ExtendedDateFunctions.hours(listOf(eveningDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(23.0, result.value)
    }

    @Test
    fun testMinutes() {
        val result = ExtendedDateFunctions.minutes(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(30.0, result.value)
    }

    @Test
    fun testMinutesZero() {
        val topOfHourDate = UDM.DateTime(Instant.parse("2025-10-17T14:00:00Z"))
        val result = ExtendedDateFunctions.minutes(listOf(topOfHourDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(0.0, result.value)
    }

    @Test
    fun testMinutesLastMinute() {
        val lastMinuteDate = UDM.DateTime(Instant.parse("2025-10-17T14:59:00Z"))
        val result = ExtendedDateFunctions.minutes(listOf(lastMinuteDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(59.0, result.value)
    }

    @Test
    fun testSeconds() {
        val result = ExtendedDateFunctions.seconds(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(45.0, result.value)
    }

    @Test
    fun testSecondsZero() {
        val topOfMinuteDate = UDM.DateTime(Instant.parse("2025-10-17T14:30:00Z"))
        val result = ExtendedDateFunctions.seconds(listOf(topOfMinuteDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(0.0, result.value)
    }

    @Test
    fun testSecondsLastSecond() {
        val lastSecondDate = UDM.DateTime(Instant.parse("2025-10-17T14:30:59Z"))
        val result = ExtendedDateFunctions.seconds(listOf(lastSecondDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(59.0, result.value)
    }

    // ========== DATE COMPARISON ==========

    @Test
    fun testCompareDatesEqual() {
        val date1 = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z"))
        val date2 = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z"))
        val result = ExtendedDateFunctions.compareDates(listOf(date1, date2))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(0.0, result.value)
    }

    @Test
    fun testCompareDatesFirstLess() {
        val date1 = UDM.DateTime(Instant.parse("2025-10-16T12:00:00Z"))
        val date2 = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z"))
        val result = ExtendedDateFunctions.compareDates(listOf(date1, date2))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(-1.0, result.value)
    }

    @Test
    fun testCompareDatesFirstGreater() {
        val date1 = UDM.DateTime(Instant.parse("2025-10-18T12:00:00Z"))
        val date2 = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z"))
        val result = ExtendedDateFunctions.compareDates(listOf(date1, date2))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(1.0, result.value)
    }

    @Test
    fun testCompareDatesSameDay() {
        val date1 = UDM.DateTime(Instant.parse("2025-10-17T08:00:00Z"))
        val date2 = UDM.DateTime(Instant.parse("2025-10-17T20:00:00Z"))
        val result = ExtendedDateFunctions.compareDates(listOf(date1, date2))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(-1.0, result.value) // Morning is before evening
    }

    @Test
    fun testCompareDatesMilliseconds() {
        val date1 = UDM.DateTime(Instant.parse("2025-10-17T12:00:00.000Z"))
        val date2 = UDM.DateTime(Instant.parse("2025-10-17T12:00:00.001Z"))
        val result = ExtendedDateFunctions.compareDates(listOf(date1, date2))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(-1.0, result.value)
    }

    // ========== DATE VALIDATION ==========

    @Test
    fun testValidateDateValidISO() {
        val result = ExtendedDateFunctions.validateDate(
            listOf(UDM.Scalar("2025-10-17T12:00:00Z"), UDM.Scalar("yyyy-MM-dd"))
        )

        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testValidateDateValidSimple() {
        val result = ExtendedDateFunctions.validateDate(
            listOf(UDM.Scalar("2025-10-17T00:00:00Z"), UDM.Scalar("yyyy-MM-dd"))
        )

        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testValidateDateInvalidFormat() {
        val result = ExtendedDateFunctions.validateDate(
            listOf(UDM.Scalar("2025-13-45"), UDM.Scalar("yyyy-MM-dd"))
        )

        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testValidateDateInvalidString() {
        val result = ExtendedDateFunctions.validateDate(
            listOf(UDM.Scalar("not a date"), UDM.Scalar("yyyy-MM-dd"))
        )

        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testValidateDateEmptyString() {
        val result = ExtendedDateFunctions.validateDate(
            listOf(UDM.Scalar(""), UDM.Scalar("yyyy-MM-dd"))
        )

        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testValidateDateNullValue() {
        val result = ExtendedDateFunctions.validateDate(
            listOf(UDM.Scalar.nullValue())
        )

        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    // ========== ERROR HANDLING ==========

    @Test
    fun testDayInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.day(listOf())
        }
    }

    @Test
    fun testDayTooManyArgs() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.day(listOf(baseDate, baseDate))
        }
    }

    @Test
    fun testDayInvalidType() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.day(listOf(UDM.Scalar("not a date")))
        }
    }

    @Test
    fun testMonthInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.month(listOf())
        }
    }

    @Test
    fun testMonthInvalidType() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.month(listOf(UDM.Scalar(123)))
        }
    }

    @Test
    fun testYearInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.year(listOf())
        }
    }

    @Test
    fun testHoursInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.hours(listOf())
        }
    }

    @Test
    fun testMinutesInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.minutes(listOf())
        }
    }

    @Test
    fun testSecondsInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.seconds(listOf())
        }
    }

    @Test
    fun testCompareDatesInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.compareDates(listOf(baseDate))
        }
    }

    @Test
    fun testCompareDatesTooManyArgs() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.compareDates(listOf(baseDate, baseDate, baseDate))
        }
    }

    @Test
    fun testCompareDatesInvalidType() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.compareDates(listOf(UDM.Scalar("not a date"), baseDate))
        }
    }

    @Test
    fun testValidateDateInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.validateDate(listOf())
        }
    }

    @Test
    fun testValidateDateTooManyArgs() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.validateDate(
                listOf(UDM.Scalar("date"), UDM.Scalar("pattern"), UDM.Scalar("extra"))
            )
        }
    }

    @Test
    fun testValidateDateInvalidPatternType() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.validateDate(listOf(UDM.Scalar("2025-10-17"), baseDate))
        }
    }

    @Test
    fun testValidateDateInvalidDateType() {
        assertThrows<FunctionArgumentException> {
            ExtendedDateFunctions.validateDate(listOf(baseDate))
        }
    }

    // ========== EDGE CASES ==========

    @Test
    fun testLeapYearDateComponents() {
        val leapDate = UDM.DateTime(Instant.parse("2024-02-29T13:45:30Z"))
        
        val day = ExtendedDateFunctions.day(listOf(leapDate))
        val month = ExtendedDateFunctions.month(listOf(leapDate))
        val year = ExtendedDateFunctions.year(listOf(leapDate))
        
        assertEquals(29.0, (day as UDM.Scalar).value)
        assertEquals(2.0, (month as UDM.Scalar).value)
        assertEquals(2024.0, (year as UDM.Scalar).value)
    }

    @Test
    fun testNewYearsEve() {
        val nyeDate = UDM.DateTime(Instant.parse("2025-12-31T23:59:59Z"))
        
        val day = ExtendedDateFunctions.day(listOf(nyeDate))
        val month = ExtendedDateFunctions.month(listOf(nyeDate))
        val year = ExtendedDateFunctions.year(listOf(nyeDate))
        val hours = ExtendedDateFunctions.hours(listOf(nyeDate))
        val minutes = ExtendedDateFunctions.minutes(listOf(nyeDate))
        val seconds = ExtendedDateFunctions.seconds(listOf(nyeDate))
        
        assertEquals(31.0, (day as UDM.Scalar).value)
        assertEquals(12.0, (month as UDM.Scalar).value)
        assertEquals(2025.0, (year as UDM.Scalar).value)
        assertEquals(23.0, (hours as UDM.Scalar).value)
        assertEquals(59.0, (minutes as UDM.Scalar).value)
        assertEquals(59.0, (seconds as UDM.Scalar).value)
    }

    @Test
    fun testNewYearsDay() {
        val nydDate = UDM.DateTime(Instant.parse("2026-01-01T00:00:00Z"))
        
        val day = ExtendedDateFunctions.day(listOf(nydDate))
        val month = ExtendedDateFunctions.month(listOf(nydDate))
        val year = ExtendedDateFunctions.year(listOf(nydDate))
        val hours = ExtendedDateFunctions.hours(listOf(nydDate))
        val minutes = ExtendedDateFunctions.minutes(listOf(nydDate))
        val seconds = ExtendedDateFunctions.seconds(listOf(nydDate))
        
        assertEquals(1.0, (day as UDM.Scalar).value)
        assertEquals(1.0, (month as UDM.Scalar).value)
        assertEquals(2026.0, (year as UDM.Scalar).value)
        assertEquals(0.0, (hours as UDM.Scalar).value)
        assertEquals(0.0, (minutes as UDM.Scalar).value)
        assertEquals(0.0, (seconds as UDM.Scalar).value)
    }

    @Test
    fun testValidateDateWithComplexISO() {
        val result = ExtendedDateFunctions.validateDate(
            listOf(UDM.Scalar("2025-10-17T14:30:45.123456789Z"), UDM.Scalar("ISO8601"))
        )

        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }
}