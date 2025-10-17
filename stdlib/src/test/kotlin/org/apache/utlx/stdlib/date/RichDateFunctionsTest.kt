// stdlib/src/test/kotlin/org/apache/utlx/stdlib/date/RichDateFunctionsTest.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Instant

class RichDateFunctionsTest {

    private val baseDate = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z"))
    private val baseDate2 = UDM.DateTime(Instant.parse("2025-11-15T15:30:00Z"))
    private val birthday = UDM.DateTime(Instant.parse("1995-03-15T00:00:00Z"))

    // ========== PERIOD CALCULATIONS ==========

    @Test
    fun testAddWeeks() {
        val result = RichDateFunctions.addWeeks(listOf(baseDate, UDM.Scalar(2)))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-10-31T12:00:00Z"), result.instant)
    }

    @Test
    fun testAddWeeksNegative() {
        val result = RichDateFunctions.addWeeks(listOf(baseDate, UDM.Scalar(-1)))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-10-10T12:00:00Z"), result.instant)
    }

    @Test
    fun testAddQuarters() {
        val result = RichDateFunctions.addQuarters(listOf(baseDate, UDM.Scalar(1)))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2026-01-17T12:00:00Z"), result.instant)
    }

    @Test
    fun testAddQuartersNegative() {
        val result = RichDateFunctions.addQuarters(listOf(baseDate, UDM.Scalar(-1)))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-07-17T12:00:00Z"), result.instant)
    }

    @Test
    fun testDiffWeeks() {
        val date1 = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z"))
        val date2 = UDM.DateTime(Instant.parse("2025-10-31T12:00:00Z"))
        val result = RichDateFunctions.diffWeeks(listOf(date1, date2))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(2.0, result.value)
    }

    @Test
    fun testDiffWeeksReverse() {
        val date1 = UDM.DateTime(Instant.parse("2025-10-31T12:00:00Z"))
        val date2 = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z"))
        val result = RichDateFunctions.diffWeeks(listOf(date1, date2))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(-2.0, result.value)
    }

    @Test
    fun testDiffMonths() {
        val result = RichDateFunctions.diffMonths(listOf(baseDate, baseDate2))
        
        assertTrue(result is UDM.Scalar)
        assertTrue((result.value as Double) > 0.9 && (result.value as Double) < 1.1)
    }

    @Test
    fun testDiffYears() {
        val date1 = UDM.DateTime(Instant.parse("2023-10-17T12:00:00Z"))
        val date2 = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z"))
        val result = RichDateFunctions.diffYears(listOf(date1, date2))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(2.0, result.value)
    }

    // ========== START/END OF PERIOD ==========

    @Test
    fun testStartOfDay() {
        val result = RichDateFunctions.startOfDay(listOf(baseDate))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-10-17T00:00:00Z"), result.instant)
    }

    @Test
    fun testEndOfDay() {
        val result = RichDateFunctions.endOfDay(listOf(baseDate))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-10-17T23:59:59.999999999Z"), result.instant)
    }

    @Test
    fun testStartOfWeek() {
        val result = RichDateFunctions.startOfWeek(listOf(baseDate))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-10-13T00:00:00Z"), result.instant)
    }

    @Test
    fun testEndOfWeek() {
        val result = RichDateFunctions.endOfWeek(listOf(baseDate))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-10-19T23:59:59.999999999Z"), result.instant)
    }

    @Test
    fun testStartOfMonth() {
        val result = RichDateFunctions.startOfMonth(listOf(baseDate))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-10-01T00:00:00Z"), result.instant)
    }

    @Test
    fun testEndOfMonth() {
        val result = RichDateFunctions.endOfMonth(listOf(baseDate))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-10-31T23:59:59.999999999Z"), result.instant)
    }

    @Test
    fun testStartOfYear() {
        val result = RichDateFunctions.startOfYear(listOf(baseDate))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), result.instant)
    }

    @Test
    fun testEndOfYear() {
        val result = RichDateFunctions.endOfYear(listOf(baseDate))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-12-31T23:59:59.999999999Z"), result.instant)
    }

    @Test
    fun testStartOfQuarter() {
        val result = RichDateFunctions.startOfQuarter(listOf(baseDate))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-10-01T00:00:00Z"), result.instant)
    }

    @Test
    fun testEndOfQuarter() {
        val result = RichDateFunctions.endOfQuarter(listOf(baseDate))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-12-31T23:59:59.999999999Z"), result.instant)
    }

    @Test
    fun testStartOfQuarterQ2() {
        val q2Date = UDM.DateTime(Instant.parse("2025-05-15T12:00:00Z"))
        val result = RichDateFunctions.startOfQuarter(listOf(q2Date))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-04-01T00:00:00Z"), result.instant)
    }

    @Test
    fun testEndOfQuarterQ2() {
        val q2Date = UDM.DateTime(Instant.parse("2025-05-15T12:00:00Z"))
        val result = RichDateFunctions.endOfQuarter(listOf(q2Date))
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-06-30T23:59:59.999999999Z"), result.instant)
    }

    // ========== DATE INFORMATION ==========

    @Test
    fun testDayOfWeek() {
        val fridayDate = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z")) // Friday
        val result = RichDateFunctions.dayOfWeek(listOf(fridayDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(5.0, result.value) // Friday = 5
    }

    @Test
    fun testDayOfWeekName() {
        val fridayDate = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z"))
        val result = RichDateFunctions.dayOfWeekName(listOf(fridayDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("Friday", result.value)
    }

    @Test
    fun testDayOfYear() {
        val result = RichDateFunctions.dayOfYear(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(290.0, result.value) // Oct 17 is day 290
    }

    @Test
    fun testWeekOfYear() {
        val result = RichDateFunctions.weekOfYear(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertTrue((result.value as Double) > 40.0 && (result.value as Double) < 45.0)
    }

    @Test
    fun testQuarter() {
        val result = RichDateFunctions.quarter(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(4.0, result.value) // October is Q4
    }

    @Test
    fun testQuarterQ1() {
        val q1Date = UDM.DateTime(Instant.parse("2025-03-15T12:00:00Z"))
        val result = RichDateFunctions.quarter(listOf(q1Date))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(1.0, result.value)
    }

    @Test
    fun testMonthName() {
        val result = RichDateFunctions.monthName(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("October", result.value)
    }

    @Test
    fun testIsLeapYearFunc() {
        val leapYearDate = UDM.DateTime(Instant.parse("2024-02-29T12:00:00Z"))
        val result1 = RichDateFunctions.isLeapYearFunc(listOf(leapYearDate))
        
        assertTrue(result1 is UDM.Scalar)
        assertTrue(result1.value as Boolean)

        val nonLeapYearDate = UDM.DateTime(Instant.parse("2025-02-28T12:00:00Z"))
        val result2 = RichDateFunctions.isLeapYearFunc(listOf(nonLeapYearDate))
        
        assertTrue(result2 is UDM.Scalar)
        assertFalse(result2.value as Boolean)
    }

    @Test
    fun testDaysInMonth() {
        val result = RichDateFunctions.daysInMonth(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(31.0, result.value) // October has 31 days
    }

    @Test
    fun testDaysInMonthFebruary() {
        val febDate = UDM.DateTime(Instant.parse("2024-02-15T12:00:00Z")) // Leap year
        val result = RichDateFunctions.daysInMonth(listOf(febDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(29.0, result.value) // February 2024 has 29 days
    }

    @Test
    fun testDaysInYear() {
        val result = RichDateFunctions.daysInYear(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(365.0, result.value) // 2025 is not a leap year
    }

    @Test
    fun testDaysInLeapYear() {
        val leapYearDate = UDM.DateTime(Instant.parse("2024-06-15T12:00:00Z"))
        val result = RichDateFunctions.daysInYear(listOf(leapYearDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(366.0, result.value) // 2024 is a leap year
    }

    // ========== DATE COMPARISONS ==========

    @Test
    fun testIsBefore() {
        val result = RichDateFunctions.isBefore(listOf(baseDate, baseDate2))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsAfter() {
        val result = RichDateFunctions.isAfter(listOf(baseDate2, baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsSameDay() {
        val sameDay = UDM.DateTime(Instant.parse("2025-10-17T18:30:00Z"))
        val result = RichDateFunctions.isSameDay(listOf(baseDate, sameDay))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsSameDayFalse() {
        val result = RichDateFunctions.isSameDay(listOf(baseDate, baseDate2))
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testIsBetween() {
        val middleDate = UDM.DateTime(Instant.parse("2025-10-25T12:00:00Z"))
        val result = RichDateFunctions.isBetween(listOf(middleDate, baseDate, baseDate2))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsBetweenFalse() {
        val outsideDate = UDM.DateTime(Instant.parse("2025-12-01T12:00:00Z"))
        val result = RichDateFunctions.isBetween(listOf(outsideDate, baseDate, baseDate2))
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testIsToday() {
        val todayDate = UDM.DateTime(Instant.now())
        val result = RichDateFunctions.isToday(listOf(todayDate))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsTodayFalse() {
        val result = RichDateFunctions.isToday(listOf(baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testIsWeekend() {
        val saturdayDate = UDM.DateTime(Instant.parse("2025-10-18T12:00:00Z")) // Saturday
        val result = RichDateFunctions.isWeekend(listOf(saturdayDate))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsWeekendFalse() {
        val fridayDate = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z")) // Friday
        val result = RichDateFunctions.isWeekend(listOf(fridayDate))
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testIsWeekday() {
        val fridayDate = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z")) // Friday
        val result = RichDateFunctions.isWeekday(listOf(fridayDate))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsWeekdayFalse() {
        val sundayDate = UDM.DateTime(Instant.parse("2025-10-19T12:00:00Z")) // Sunday
        val result = RichDateFunctions.isWeekday(listOf(sundayDate))
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    // ========== AGE CALCULATIONS ==========

    @Test
    fun testAge() {
        val result = RichDateFunctions.age(listOf(birthday))
        
        assertTrue(result is UDM.Scalar)
        assertTrue((result.value as Double) >= 29.0 && (result.value as Double) <= 31.0) // Approximate age check
    }

    @Test
    fun testAgeNotYetBirthday() {
        val futurebirthDate = UDM.DateTime(Instant.parse("1995-12-25T00:00:00Z"))
        val result = RichDateFunctions.age(listOf(futurebirthDate))
        
        assertTrue(result is UDM.Scalar)
        assertTrue((result.value as Double) >= 29.0 && (result.value as Double) <= 30.0)
    }

    // ========== ERROR HANDLING ==========

    @Test
    fun testAddWeeksInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            RichDateFunctions.addWeeks(listOf(baseDate))
        }
    }

    @Test
    fun testAddWeeksInvalidDateType() {
        assertThrows<FunctionArgumentException> {
            RichDateFunctions.addWeeks(listOf(UDM.Scalar("not a date"), UDM.Scalar(1)))
        }
    }

    @Test
    fun testAddWeeksInvalidNumberType() {
        assertThrows<FunctionArgumentException> {
            RichDateFunctions.addWeeks(listOf(baseDate, UDM.Scalar("not a number")))
        }
    }

    @Test
    fun testDiffWeeksInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            RichDateFunctions.diffWeeks(listOf(baseDate))
        }
    }

    @Test
    fun testStartOfDayInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            RichDateFunctions.startOfDay(listOf())
        }
    }

    @Test
    fun testDayOfWeekInvalidDateType() {
        assertThrows<FunctionArgumentException> {
            RichDateFunctions.dayOfWeek(listOf(UDM.Scalar("not a date")))
        }
    }

    @Test
    fun testIsBeforeInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            RichDateFunctions.isBefore(listOf(baseDate))
        }
    }

    @Test
    fun testIsBetweenInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            RichDateFunctions.isBetween(listOf(baseDate, baseDate2))
        }
    }

    @Test
    fun testAgeInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            RichDateFunctions.age(listOf())
        }
    }

    // ========== EDGE CASES ==========

    @Test
    fun testEndOfMonthFebruary() {
        val febDate = UDM.DateTime(Instant.parse("2024-02-15T12:00:00Z")) // Leap year
        val result = RichDateFunctions.endOfMonth(listOf(febDate))
        
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2024-02-29T23:59:59.999999999Z"), result.instant)
    }

    @Test
    fun testEndOfMonthFebruaryNonLeap() {
        val febDate = UDM.DateTime(Instant.parse("2025-02-15T12:00:00Z")) // Non-leap year
        val result = RichDateFunctions.endOfMonth(listOf(febDate))
        
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-02-28T23:59:59.999999999Z"), result.instant)
    }

    @Test
    fun testAddWeeksZero() {
        val result = RichDateFunctions.addWeeks(listOf(baseDate, UDM.Scalar(0)))
        assertTrue(result is UDM.DateTime)
        assertEquals(baseDate.instant, result.instant)
    }

    @Test
    fun testAddQuartersZero() {
        val result = RichDateFunctions.addQuarters(listOf(baseDate, UDM.Scalar(0)))
        assertTrue(result is UDM.DateTime)
        assertEquals(baseDate.instant, result.instant)
    }

    @Test
    fun testDiffWeeksSameDate() {
        val result = RichDateFunctions.diffWeeks(listOf(baseDate, baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(0.0, result.value)
    }

    @Test
    fun testIsSameDayIdentical() {
        val result = RichDateFunctions.isSameDay(listOf(baseDate, baseDate))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsBetweenEdgeCase() {
        val result = RichDateFunctions.isBetween(listOf(baseDate, baseDate, baseDate2))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean) // Inclusive boundary
    }
}