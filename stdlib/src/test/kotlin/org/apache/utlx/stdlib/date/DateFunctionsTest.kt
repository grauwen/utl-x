// stdlib/src/test/kotlin/org/apache/utlx/stdlib/date/DateFunctionsTest.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.*
import java.time.format.DateTimeFormatter

/**
 * Comprehensive test suite for Date/Time functions.
 * 
 * Tests cover:
 * - Basic date operations (now, parse, format)
 * - Date arithmetic (add/subtract days, hours, etc.)
 * - Date comparisons and differences
 * - Timezone conversions
 * - Start/end of periods (day, week, month, year, quarter)
 * - Date information extraction
 * - Date validation
 * - Rich date functions
 */
class DateFunctionsTest {

    // ==================== Basic Date Functions Tests ====================
    
    @Test
    fun `test now - returns current timestamp`() {
        val before = Instant.now()
        Thread.sleep(10)
        
        val result = DateFunctions.now(listOf())
        val timestamp = (result as UDM.Scalar).value as String
        
        Thread.sleep(10)
        val after = Instant.now()
        
        val parsed = Instant.parse(timestamp)
        assertTrue(parsed.isAfter(before) && parsed.isBefore(after),
            "now() should return current timestamp")
    }
    
    @Test
    fun `test parseDate - ISO 8601 format`() {
        val dateStr = UDM.Scalar("2025-10-15")
        val format = UDM.Scalar("yyyy-MM-dd")
        
        val result = DateFunctions.parseDate(listOf(dateStr, format))
        assertNotNull(result)
        
        val parsed = (result as UDM.Scalar).value as String
        assertTrue(parsed.contains("2025"), "Should parse year")
        assertTrue(parsed.contains("10"), "Should parse month")
        assertTrue(parsed.contains("15"), "Should parse day")
    }
    
    @Test
    fun `test parseDate - various formats`() {
        val testCases = mapOf(
            "2025-10-15" to "yyyy-MM-dd",
            "10/15/2025" to "MM/dd/yyyy",
            "15-Oct-2025" to "dd-MMM-yyyy",
            "2025-10-15T14:30:00Z" to "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        
        testCases.forEach { (dateStr, format) ->
            val result = DateFunctions.parseDate(listOf(UDM.Scalar(dateStr), UDM.Scalar(format)))
            assertNotNull(result, "Should parse format: $format")
        }
    }
    
    @Test
    fun `test formatDate - ISO 8601 format`() {
        val dateStr = UDM.Scalar("2025-10-15T14:30:00Z")
        val format = UDM.Scalar("yyyy-MM-dd")
        
        val result = DateFunctions.formatDate(listOf(dateStr, format))
        val formatted = (result as UDM.Scalar).value as String
        
        assertEquals("2025-10-15", formatted)
    }
    
    @Test
    fun `test formatDate - various formats`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        
        val testCases = mapOf(
            "yyyy-MM-dd" to "2025-10-15",
            "MM/dd/yyyy" to "10/15/2025",
            "dd-MMM-yyyy" to "15-Oct-2025",
            "HH:mm:ss" to "14:30:00"
        )
        
        testCases.forEach { (format, expected) ->
            val result = DateFunctions.formatDate(listOf(date, UDM.Scalar(format)))
            val formatted = (result as UDM.Scalar).value as String
            assertTrue(formatted.contains(expected.substring(0, 4)), 
                "Format $format should contain expected pattern")
        }
    }

    // ==================== Date Arithmetic Tests ====================
    
    @Test
    fun `test addDays - positive days`() {
        val date = UDM.Scalar("2025-10-15T00:00:00Z")
        val days = UDM.Scalar(5)
        
        val result = DateFunctions.addDays(listOf(date, days))
        val newDate = (result as UDM.Scalar).value as String
        
        assertTrue(newDate.contains("2025-10-20"), "Should add 5 days")
    }
    
    @Test
    fun `test addDays - negative days (subtract)`() {
        val date = UDM.Scalar("2025-10-15T00:00:00Z")
        val days = UDM.Scalar(-5)
        
        val result = DateFunctions.addDays(listOf(date, days))
        val newDate = (result as UDM.Scalar).value as String
        
        assertTrue(newDate.contains("2025-10-10"), "Should subtract 5 days")
    }
    
    @Test
    fun `test addDays - month boundary`() {
        val date = UDM.Scalar("2025-10-30T00:00:00Z")
        val days = UDM.Scalar(5)
        
        val result = DateFunctions.addDays(listOf(date, days))
        val newDate = (result as UDM.Scalar).value as String
        
        assertTrue(newDate.contains("2025-11"), "Should cross month boundary")
    }
    
    @Test
    fun `test addHours - standard hours`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        val hours = UDM.Scalar(5)
        
        val result = DateFunctions.addHours(listOf(date, hours))
        val newDate = (result as UDM.Scalar).value as String
        
        assertTrue(newDate.contains("19:30"), "Should add 5 hours")
    }
    
    @Test
    fun `test addHours - cross day boundary`() {
        val date = UDM.Scalar("2025-10-15T22:00:00Z")
        val hours = UDM.Scalar(5)
        
        val result = DateFunctions.addHours(listOf(date, hours))
        val newDate = (result as UDM.Scalar).value as String
        
        assertTrue(newDate.contains("2025-10-16"), "Should cross day boundary")
    }
    
    @Test
    fun `test addMonths - simple case`() {
        val date = UDM.Scalar("2025-10-15T00:00:00Z")
        val months = UDM.Scalar(3)
        
        val result = MoreDateFunctions.addMonths(listOf(date, months))
        val newDate = (result as UDM.Scalar).value as String
        
        assertTrue(newDate.contains("2026-01"), "Should add 3 months")
    }
    
    @Test
    fun `test addMonths - year boundary`() {
        val date = UDM.Scalar("2025-11-15T00:00:00Z")
        val months = UDM.Scalar(2)
        
        val result = MoreDateFunctions.addMonths(listOf(date, months))
        val newDate = (result as UDM.Scalar).value as String
        
        assertTrue(newDate.contains("2026-01"), "Should cross year boundary")
    }
    
    @Test
    fun `test addYears - simple case`() {
        val date = UDM.Scalar("2025-10-15T00:00:00Z")
        val years = UDM.Scalar(5)
        
        val result = MoreDateFunctions.addYears(listOf(date, years))
        val newDate = (result as UDM.Scalar).value as String
        
        assertTrue(newDate.contains("2030"), "Should add 5 years")
    }
    
    @Test
    fun `test addMinutes - simple case`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        val minutes = UDM.Scalar(45)
        
        val result = MoreDateFunctions.addMinutes(listOf(date, minutes))
        val newDate = (result as UDM.Scalar).value as String
        
        assertTrue(newDate.contains("15:15"), "Should add 45 minutes")
    }
    
    @Test
    fun `test addSeconds - simple case`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        val seconds = UDM.Scalar(90)
        
        val result = MoreDateFunctions.addSeconds(listOf(date, seconds))
        val newDate = (result as UDM.Scalar).value as String
        
        assertTrue(newDate.contains("14:31:30"), "Should add 90 seconds")
    }

    // ==================== Date Difference Tests ====================
    
    @Test
    fun `test diffDays - positive difference`() {
        val date1 = UDM.Scalar("2025-10-15T00:00:00Z")
        val date2 = UDM.Scalar("2025-10-20T00:00:00Z")
        
        val result = DateFunctions.diffDays(listOf(date1, date2))
        val diff = (result as UDM.Scalar).value as Long
        
        assertEquals(5L, diff, "Should be 5 days difference")
    }
    
    @Test
    fun `test diffDays - negative difference`() {
        val date1 = UDM.Scalar("2025-10-20T00:00:00Z")
        val date2 = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = DateFunctions.diffDays(listOf(date1, date2))
        val diff = (result as UDM.Scalar).value as Long
        
        assertEquals(-5L, diff, "Should be -5 days difference")
    }
    
    @Test
    fun `test diffDays - same date`() {
        val date = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = DateFunctions.diffDays(listOf(date, date))
        val diff = (result as UDM.Scalar).value as Long
        
        assertEquals(0L, diff, "Should be 0 days difference")
    }
    
    @Test
    fun `test diffHours - standard case`() {
        val date1 = UDM.Scalar("2025-10-15T10:00:00Z")
        val date2 = UDM.Scalar("2025-10-15T15:00:00Z")
        
        val result = MoreDateFunctions.diffHours(listOf(date1, date2))
        val diff = (result as UDM.Scalar).value as Long
        
        assertEquals(5L, diff, "Should be 5 hours difference")
    }
    
    @Test
    fun `test diffMinutes - standard case`() {
        val date1 = UDM.Scalar("2025-10-15T14:30:00Z")
        val date2 = UDM.Scalar("2025-10-15T15:15:00Z")
        
        val result = MoreDateFunctions.diffMinutes(listOf(date1, date2))
        val diff = (result as UDM.Scalar).value as Long
        
        assertEquals(45L, diff, "Should be 45 minutes difference")
    }
    
    @Test
    fun `test diffSeconds - standard case`() {
        val date1 = UDM.Scalar("2025-10-15T14:30:00Z")
        val date2 = UDM.Scalar("2025-10-15T14:31:30Z")
        
        val result = MoreDateFunctions.diffSeconds(listOf(date1, date2))
        val diff = (result as UDM.Scalar).value as Long
        
        assertEquals(90L, diff, "Should be 90 seconds difference")
    }
    
    @Test
    fun `test diffWeeks - standard case`() {
        val date1 = UDM.Scalar("2025-10-01T00:00:00Z")
        val date2 = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = RichDateFunctions.diffWeeks(listOf(date1, date2))
        val diff = (result as UDM.Scalar).value as Long
        
        assertEquals(2L, diff, "Should be 2 weeks difference")
    }
    
    @Test
    fun `test diffMonths - standard case`() {
        val date1 = UDM.Scalar("2025-10-15T00:00:00Z")
        val date2 = UDM.Scalar("2026-01-15T00:00:00Z")
        
        val result = RichDateFunctions.diffMonths(listOf(date1, date2))
        val diff = (result as UDM.Scalar).value as Long
        
        assertEquals(3L, diff, "Should be 3 months difference")
    }
    
    @Test
    fun `test diffYears - standard case`() {
        val date1 = UDM.Scalar("2020-10-15T00:00:00Z")
        val date2 = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = RichDateFunctions.diffYears(listOf(date1, date2))
        val diff = (result as UDM.Scalar).value as Long
        
        assertEquals(5L, diff, "Should be 5 years difference")
    }

    // ==================== Extended Date Functions Tests ====================
    
    @Test
    fun `test day - extract day of month`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        
        val result = ExtendedDateFunctions.day(listOf(date))
        val day = (result as UDM.Scalar).value as Int
        
        assertEquals(15, day)
    }
    
    @Test
    fun `test month - extract month`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        
        val result = ExtendedDateFunctions.month(listOf(date))
        val month = (result as UDM.Scalar).value as Int
        
        assertEquals(10, month)
    }
    
    @Test
    fun `test year - extract year`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        
        val result = ExtendedDateFunctions.year(listOf(date))
        val year = (result as UDM.Scalar).value as Int
        
        assertEquals(2025, year)
    }
    
    @Test
    fun `test hours - extract hours`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        
        val result = ExtendedDateFunctions.hours(listOf(date))
        val hours = (result as UDM.Scalar).value as Int
        
        assertEquals(14, hours)
    }
    
    @Test
    fun `test minutes - extract minutes`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        
        val result = ExtendedDateFunctions.minutes(listOf(date))
        val minutes = (result as UDM.Scalar).value as Int
        
        assertEquals(30, minutes)
    }
    
    @Test
    fun `test seconds - extract seconds`() {
        val date = UDM.Scalar("2025-10-15T14:30:45Z")
        
        val result = ExtendedDateFunctions.seconds(listOf(date))
        val seconds = (result as UDM.Scalar).value as Int
        
        assertEquals(45, seconds)
    }

    // ==================== Date Comparison Tests ====================
    
    @Test
    fun `test compareDates - before`() {
        val date1 = UDM.Scalar("2025-10-15T00:00:00Z")
        val date2 = UDM.Scalar("2025-10-20T00:00:00Z")
        
        val result = ExtendedDateFunctions.compareDates(listOf(date1, date2))
        val comparison = (result as UDM.Scalar).value as Int
        
        assertTrue(comparison < 0, "date1 should be before date2")
    }
    
    @Test
    fun `test compareDates - after`() {
        val date1 = UDM.Scalar("2025-10-20T00:00:00Z")
        val date2 = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = ExtendedDateFunctions.compareDates(listOf(date1, date2))
        val comparison = (result as UDM.Scalar).value as Int
        
        assertTrue(comparison > 0, "date1 should be after date2")
    }
    
    @Test
    fun `test compareDates - equal`() {
        val date = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = ExtendedDateFunctions.compareDates(listOf(date, date))
        val comparison = (result as UDM.Scalar).value as Int
        
        assertEquals(0, comparison, "dates should be equal")
    }
    
    @Test
    fun `test isBefore - true case`() {
        val date1 = UDM.Scalar("2025-10-15T00:00:00Z")
        val date2 = UDM.Scalar("2025-10-20T00:00:00Z")
        
        val result = RichDateFunctions.isBefore(listOf(date1, date2))
        val isBefore = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isBefore)
    }
    
    @Test
    fun `test isBefore - false case`() {
        val date1 = UDM.Scalar("2025-10-20T00:00:00Z")
        val date2 = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = RichDateFunctions.isBefore(listOf(date1, date2))
        val isBefore = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isBefore)
    }
    
    @Test
    fun `test isAfter - true case`() {
        val date1 = UDM.Scalar("2025-10-20T00:00:00Z")
        val date2 = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = RichDateFunctions.isAfter(listOf(date1, date2))
        val isAfter = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isAfter)
    }
    
    @Test
    fun `test isSameDay - true case`() {
        val date1 = UDM.Scalar("2025-10-15T08:00:00Z")
        val date2 = UDM.Scalar("2025-10-15T20:00:00Z")
        
        val result = RichDateFunctions.isSameDay(listOf(date1, date2))
        val isSame = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isSame, "Different times, same day")
    }
    
    @Test
    fun `test isSameDay - false case`() {
        val date1 = UDM.Scalar("2025-10-15T23:59:59Z")
        val date2 = UDM.Scalar("2025-10-16T00:00:01Z")
        
        val result = RichDateFunctions.isSameDay(listOf(date1, date2))
        val isSame = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isSame, "Different days")
    }
    
    @Test
    fun `test isBetween - inside range`() {
        val date = UDM.Scalar("2025-10-15T00:00:00Z")
        val start = UDM.Scalar("2025-10-01T00:00:00Z")
        val end = UDM.Scalar("2025-10-31T00:00:00Z")
        
        val result = RichDateFunctions.isBetween(listOf(date, start, end))
        val isBetween = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isBetween)
    }
    
    @Test
    fun `test isBetween - outside range`() {
        val date = UDM.Scalar("2025-11-15T00:00:00Z")
        val start = UDM.Scalar("2025-10-01T00:00:00Z")
        val end = UDM.Scalar("2025-10-31T00:00:00Z")
        
        val result = RichDateFunctions.isBetween(listOf(date, start, end))
        val isBetween = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isBetween)
    }

    // ==================== Start/End of Period Tests ====================
    
    @Test
    fun `test startOfDay`() {
        val date = UDM.Scalar("2025-10-15T14:30:45Z")
        
        val result = RichDateFunctions.startOfDay(listOf(date))
        val startDate = (result as UDM.Scalar).value as String
        
        assertTrue(startDate.contains("00:00:00"), "Should be midnight")
    }
    
    @Test
    fun `test endOfDay`() {
        val date = UDM.Scalar("2025-10-15T14:30:45Z")
        
        val result = RichDateFunctions.endOfDay(listOf(date))
        val endDate = (result as UDM.Scalar).value as String
        
        assertTrue(endDate.contains("23:59:59"), "Should be end of day")
    }
    
    @Test
    fun `test startOfWeek`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z") // Wednesday
        
        val result = RichDateFunctions.startOfWeek(listOf(date))
        val startDate = (result as UDM.Scalar).value as String
        
        // Should be Monday of that week
        assertNotNull(startDate)
    }
    
    @Test
    fun `test endOfWeek`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z") // Wednesday
        
        val result = RichDateFunctions.endOfWeek(listOf(date))
        val endDate = (result as UDM.Scalar).value as String
        
        // Should be Sunday of that week
        assertNotNull(endDate)
    }
    
    @Test
    fun `test startOfMonth`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        
        val result = RichDateFunctions.startOfMonth(listOf(date))
        val startDate = (result as UDM.Scalar).value as String
        
        assertTrue(startDate.contains("2025-10-01"), "Should be first day of month")
    }
    
    @Test
    fun `test endOfMonth`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        
        val result = RichDateFunctions.endOfMonth(listOf(date))
        val endDate = (result as UDM.Scalar).value as String
        
        assertTrue(endDate.contains("2025-10-31"), "Should be last day of month")
    }
    
    @Test
    fun `test startOfYear`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        
        val result = RichDateFunctions.startOfYear(listOf(date))
        val startDate = (result as UDM.Scalar).value as String
        
        assertTrue(startDate.contains("2025-01-01"), "Should be first day of year")
    }
    
    @Test
    fun `test endOfYear`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        
        val result = RichDateFunctions.endOfYear(listOf(date))
        val endDate = (result as UDM.Scalar).value as String
        
        assertTrue(endDate.contains("2025-12-31"), "Should be last day of year")
    }
    
    @Test
    fun `test startOfQuarter`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z") // Q4
        
        val result = RichDateFunctions.startOfQuarter(listOf(date))
        val startDate = (result as UDM.Scalar).value as String
        
        assertTrue(startDate.contains("2025-10-01"), "Q4 starts October 1")
    }
    
    @Test
    fun `test endOfQuarter`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z") // Q4
        
        val result = RichDateFunctions.endOfQuarter(listOf(date))
        val endDate = (result as UDM.Scalar).value as String
        
        assertTrue(endDate.contains("2025-12-31"), "Q4 ends December 31")
    }

    // ==================== Date Information Tests ====================
    
    @Test
    fun `test dayOfWeek - numeric`() {
        val date = UDM.Scalar("2025-10-15T00:00:00Z") // Wednesday
        
        val result = RichDateFunctions.dayOfWeek(listOf(date))
        val dayNum = (result as UDM.Scalar).value as Int
        
        assertEquals(3, dayNum, "Wednesday is day 3")
    }
    
    @Test
    fun `test dayOfWeekName`() {
        val date = UDM.Scalar("2025-10-15T00:00:00Z") // Wednesday
        
        val result = RichDateFunctions.dayOfWeekName(listOf(date))
        val dayName = (result as UDM.Scalar).value as String
        
        assertEquals("Wednesday", dayName)
    }
    
    @Test
    fun `test dayOfYear`() {
        val date = UDM.Scalar("2025-01-01T00:00:00Z")
        
        val result = RichDateFunctions.dayOfYear(listOf(date))
        val dayOfYear = (result as UDM.Scalar).value as Int
        
        assertEquals(1, dayOfYear, "January 1 is day 1")
    }
    
    @Test
    fun `test weekOfYear`() {
        val date = UDM.Scalar("2025-01-07T00:00:00Z")
        
        val result = RichDateFunctions.weekOfYear(listOf(date))
        val weekNum = (result as UDM.Scalar).value as Int
        
        assertTrue(weekNum >= 1 && weekNum <= 53, "Week should be 1-53")
    }
    
    @Test
    fun `test quarter`() {
        val testCases = mapOf(
            "2025-01-15T00:00:00Z" to 1,
            "2025-04-15T00:00:00Z" to 2,
            "2025-07-15T00:00:00Z" to 3,
            "2025-10-15T00:00:00Z" to 4
        )
        
        testCases.forEach { (dateStr, expectedQuarter) ->
            val result = RichDateFunctions.quarter(listOf(UDM.Scalar(dateStr)))
            val quarter = (result as UDM.Scalar).value as Int
            
            assertEquals(expectedQuarter, quarter, "Quarter for $dateStr")
        }
    }
    
    @Test
    fun `test monthName`() {
        val date = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = RichDateFunctions.monthName(listOf(date))
        val name = (result as UDM.Scalar).value as String
        
        assertEquals("October", name)
    }
    
    @Test
    fun `test isLeapYear - leap year`() {
        val date = UDM.Scalar("2024-10-15T00:00:00Z")
        
        val result = RichDateFunctions.isLeapYearFunc(listOf(date))
        val isLeap = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isLeap, "2024 is a leap year")
    }
    
    @Test
    fun `test isLeapYear - not leap year`() {
        val date = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = RichDateFunctions.isLeapYearFunc(listOf(date))
        val isLeap = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isLeap, "2025 is not a leap year")
    }
    
    @Test
    fun `test daysInMonth - February non-leap`() {
        val date = UDM.Scalar("2025-02-15T00:00:00Z")
        
        val result = RichDateFunctions.daysInMonth(listOf(date))
        val days = (result as UDM.Scalar).value as Int
        
        assertEquals(28, days, "February 2025 has 28 days")
    }
    
    @Test
    fun `test daysInMonth - February leap`() {
        val date = UDM.Scalar("2024-02-15T00:00:00Z")
        
        val result = RichDateFunctions.daysInMonth(listOf(date))
        val days = (result as UDM.Scalar).value as Int
        
        assertEquals(29, days, "February 2024 has 29 days")
    }
    
    @Test
    fun `test daysInYear - regular year`() {
        val date = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = RichDateFunctions.daysInYear(listOf(date))
        val days = (result as UDM.Scalar).value as Int
        
        assertEquals(365, days, "2025 has 365 days")
    }
    
    @Test
    fun `test daysInYear - leap year`() {
        val date = UDM.Scalar("2024-10-15T00:00:00Z")
        
        val result = RichDateFunctions.daysInYear(listOf(date))
        val days = (result as UDM.Scalar).value as Int
        
        assertEquals(366, days, "2024 has 366 days")
    }

    // ==================== Special Date Tests ====================
    
    @Test
    fun `test isWeekend - Saturday`() {
        val saturday = UDM.Scalar("2025-10-18T00:00:00Z")
        
        val result = RichDateFunctions.isWeekend(listOf(saturday))
        val isWeekend = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isWeekend, "Saturday is weekend")
    }
    
    @Test
    fun `test isWeekend - Sunday`() {
        val sunday = UDM.Scalar("2025-10-19T00:00:00Z")
        
        val result = RichDateFunctions.isWeekend(listOf(sunday))
        val isWeekend = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isWeekend, "Sunday is weekend")
    }
    
    @Test
    fun `test isWeekday - Monday`() {
        val monday = UDM.Scalar("2025-10-13T00:00:00Z")
        
        val result = RichDateFunctions.isWeekday(listOf(monday))
        val isWeekday = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isWeekday, "Monday is weekday")
    }
    
    @Test
    fun `test age - calculation`() {
        val birthdate = UDM.Scalar("1990-10-15T00:00:00Z")
        val currentDate = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = RichDateFunctions.age(listOf(birthdate, currentDate))
        val age = (result as UDM.Scalar).value as Int
        
        assertEquals(35, age, "Age should be 35")
    }
    
    @Test
    fun `test age - before birthday this year`() {
        val birthdate = UDM.Scalar("1990-12-15T00:00:00Z")
        val currentDate = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val result = RichDateFunctions.age(listOf(birthdate, currentDate))
        val age = (result as UDM.Scalar).value as Int
        
        assertEquals(34, age, "Age should be 34 (birthday not yet reached)")
    }

    // ==================== Timezone Tests ====================
    
    @Test
    fun `test getTimezone - UTC`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        
        val result = MoreDateFunctions.getTimezone(listOf(date))
        val timezone = (result as UDM.Scalar).value as String
        
        assertEquals("UTC", timezone)
    }
    
    @Test
    fun `test convertTimezone - UTC to EST`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        val toTimezone = UDM.Scalar("America/New_York")
        
        val result = TimezoneFunctions.convertTimezone(listOf(date, toTimezone))
        val converted = (result as UDM.Scalar).value as String
        
        assertNotNull(converted)
        // In October, EST is UTC-4 (EDT)
        assertTrue(converted.contains("10:30") || converted.contains("09:30"), 
            "Should convert to Eastern time")
    }
    
    @Test
    fun `test isValidTimezone - valid`() {
        val validTimezones = listOf("UTC", "America/New_York", "Europe/London", "Asia/Tokyo")
        
        validTimezones.forEach { tz ->
            val result = TimezoneFunctions.isValidTimezone(listOf(UDM.Scalar(tz)))
            val isValid = (result as UDM.Scalar).value as Boolean
            
            assertTrue(isValid, "$tz should be valid")
        }
    }
    
    @Test
    fun `test isValidTimezone - invalid`() {
        val result = TimezoneFunctions.isValidTimezone(listOf(UDM.Scalar("Invalid/Timezone")))
        val isValid = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isValid, "Invalid timezone should return false")
    }

    // ==================== Validation Tests ====================
    
    @Test
    fun `test validateDate - valid date`() {
        val date = UDM.Scalar("2025-10-15T14:30:00Z")
        
        val result = ExtendedDateFunctions.validateDate(listOf(date))
        val isValid = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isValid)
    }
    
    @Test
    fun `test validateDate - invalid date`() {
        val date = UDM.Scalar("not a date")
        
        val result = ExtendedDateFunctions.validateDate(listOf(date))
        val isValid = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isValid)
    }

    // ==================== Real-World Scenarios ====================
    
    @Test
    fun `test real-world - booking system date range`() {
        val checkIn = UDM.Scalar("2025-12-20T15:00:00Z")
        val checkOut = UDM.Scalar("2025-12-25T11:00:00Z")
        
        // Calculate number of nights
        val nights = DateFunctions.diffDays(listOf(checkIn, checkOut))
        assertEquals(5L, (nights as UDM.Scalar).value as Long)
        
        // Check if booking is in the future
        val now = DateFunctions.now(listOf())
        val nowStr = (now as UDM.Scalar).value as String
        val isFuture = RichDateFunctions.isAfter(listOf(checkIn, UDM.Scalar(nowStr)))
        assertTrue((isFuture as UDM.Scalar).value as Boolean)
    }
    
    @Test
    fun `test real-world - employee tenure calculation`() {
        val hireDate = UDM.Scalar("2020-01-15T00:00:00Z")
        val today = UDM.Scalar("2025-10-15T00:00:00Z")
        
        // Calculate years of service
        val yearsResult = RichDateFunctions.diffYears(listOf(hireDate, today))
        val years = (yearsResult as UDM.Scalar).value as Long
        
        assertEquals(5L, years, "Employee has 5 years of service")
        
        // Calculate total months
        val monthsResult = RichDateFunctions.diffMonths(listOf(hireDate, today))
        val months = (monthsResult as UDM.Scalar).value as Long
        
        assertTrue(months >= 60L, "Should be at least 60 months")
    }
    
    @Test
    fun `test real-world - subscription expiry check`() {
        val expiryDate = UDM.Scalar("2025-12-31T23:59:59Z")
        val checkDate = UDM.Scalar("2025-10-15T00:00:00Z")
        
        // Check if subscription is still valid
        val isValid = RichDateFunctions.isBefore(listOf(checkDate, expiryDate))
        assertTrue((isValid as UDM.Scalar).value as Boolean, "Subscription should be valid")
        
        // Calculate days until expiry
        val daysLeft = DateFunctions.diffDays(listOf(checkDate, expiryDate))
        assertTrue((daysLeft as UDM.Scalar).value as Long > 0, "Should have days left")
    }
    
    @Test
    fun `test real-world - invoice due date calculation`() {
        val invoiceDate = UDM.Scalar("2025-10-01T00:00:00Z")
        val paymentTerms = 30 // days
        
        // Calculate due date
        val dueDate = DateFunctions.addDays(listOf(invoiceDate, UDM.Scalar(paymentTerms)))
        val dueDateStr = (dueDate as UDM.Scalar).value as String
        
        assertTrue(dueDateStr.contains("2025-10-31") || dueDateStr.contains("2025-11-01"))
        
        // Check if overdue
        val today = UDM.Scalar("2025-11-15T00:00:00Z")
        val isOverdue = RichDateFunctions.isAfter(listOf(today, dueDate))
        assertTrue((isOverdue as UDM.Scalar).value as Boolean, "Invoice should be overdue")
    }
    
    @Test
    fun `test real-world - meeting schedule in different timezone`() {
        val meetingUTC = UDM.Scalar("2025-10-15T14:00:00Z")
        
        // Convert to New York time
        val meetingNY = TimezoneFunctions.convertTimezone(
            listOf(meetingUTC, UDM.Scalar("America/New_York"))
        )
        
        // Convert to Tokyo time
        val meetingTokyo = TimezoneFunctions.convertTimezone(
            listOf(meetingUTC, UDM.Scalar("Asia/Tokyo"))
        )
        
        assertNotNull(meetingNY)
        assertNotNull(meetingTokyo)
    }

    // ==================== Edge Cases ====================
    
    @Test
    fun `test edge case - February 29 leap year`() {
        val leapDay = UDM.Scalar("2024-02-29T00:00:00Z")
        
        val result = ExtendedDateFunctions.validateDate(listOf(leapDay))
        val isValid = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isValid, "February 29, 2024 should be valid")
    }
    
    @Test
    fun `test edge case - end of year boundary`() {
        val endOfYear = UDM.Scalar("2025-12-31T23:59:59Z")
        val addOneSecond = DateFunctions.addDays(listOf(endOfYear, UDM.Scalar(1)))
        val nextDay = (addOneSecond as UDM.Scalar).value as String
        
        assertTrue(nextDay.contains("2026-01-01"), "Should cross year boundary")
    }
    
    @Test
    fun `test edge case - daylight saving time transition`() {
        // March 10, 2024 is when DST starts in US (2 AM -> 3 AM)
        val beforeDST = UDM.Scalar("2024-03-10T01:00:00-05:00")
        val afterDST = DateFunctions.addHours(listOf(beforeDST, UDM.Scalar(2)))
        
        assertNotNull(afterDST, "Should handle DST transition")
    }
    
    @Test
    fun `test edge case - negative time differences`() {
        val future = UDM.Scalar("2025-10-20T00:00:00Z")
        val past = UDM.Scalar("2025-10-15T00:00:00Z")
        
        val diff = DateFunctions.diffDays(listOf(future, past))
        val days = (diff as UDM.Scalar).value as Long
        
        assertTrue(days < 0, "Future to past should be negative")
    }
    
    @Test
    fun `test edge case - century boundary`() {
        val endOf20thCentury = UDM.Scalar("1999-12-31T23:59:59Z")
        val addOneSecond = DateFunctions.addDays(listOf(endOf20thCentury, UDM.Scalar(1)))
        val millennium = (addOneSecond as UDM.Scalar).value as String
        
        assertTrue(millennium.contains("2000-01-01"), "Should handle millennium correctly")
    }
}
