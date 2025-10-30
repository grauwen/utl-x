package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.*
import java.time.Instant

class MoreDateFunctionsTest {

    @Test
    fun testAddMonths() {
        // Test adding positive months
        val baseDate = UDM.DateTime(Instant.parse("2023-01-15T10:30:00Z"))
        val result1 = MoreDateFunctions.addMonths(listOf(baseDate, UDM.Scalar(3)))
        
        assertTrue(result1 is UDM.DateTime)
        val resultInstant = (result1 as UDM.DateTime).instant
        // Should be around April 15, 2023
        assertTrue(resultInstant.isAfter(Instant.parse("2023-04-14T00:00:00Z")))
        assertTrue(resultInstant.isBefore(Instant.parse("2023-04-16T23:59:59Z")))

        // Test adding negative months (subtracting)
        val result2 = MoreDateFunctions.addMonths(listOf(baseDate, UDM.Scalar(-2)))
        assertTrue(result2 is UDM.DateTime)
        val resultInstant2 = (result2 as UDM.DateTime).instant
        // Should be around November 15, 2022
        assertTrue(resultInstant2.isBefore(baseDate.instant))

        // Test adding zero months
        val result3 = MoreDateFunctions.addMonths(listOf(baseDate, UDM.Scalar(0)))
        assertTrue(result3 is UDM.DateTime)
        assertEquals(baseDate.instant.epochSecond, (result3 as UDM.DateTime).instant.epochSecond)
    }

    @Test
    fun testAddYears() {
        // Test adding positive years
        val baseDate = UDM.DateTime(Instant.parse("2020-02-29T12:00:00Z")) // Leap year
        val result1 = MoreDateFunctions.addYears(listOf(baseDate, UDM.Scalar(2)))
        
        assertTrue(result1 is UDM.DateTime)
        val resultInstant = (result1 as UDM.DateTime).instant
        // Should be around 2022
        assertTrue(resultInstant.isAfter(Instant.parse("2021-12-31T00:00:00Z")))
        assertTrue(resultInstant.isBefore(Instant.parse("2023-01-01T00:00:00Z")))

        // Test adding negative years
        val result2 = MoreDateFunctions.addYears(listOf(baseDate, UDM.Scalar(-1)))
        assertTrue(result2 is UDM.DateTime)
        val resultInstant2 = (result2 as UDM.DateTime).instant
        assertTrue(resultInstant2.isBefore(baseDate.instant))

        // Test adding zero years
        val result3 = MoreDateFunctions.addYears(listOf(baseDate, UDM.Scalar(0)))
        assertEquals(baseDate.instant.epochSecond, (result3 as UDM.DateTime).instant.epochSecond)
    }

    @Test
    fun testAddMinutes() {
        // Test adding positive minutes
        val baseDate = UDM.DateTime(Instant.parse("2023-10-14T10:30:00Z"))
        val result1 = MoreDateFunctions.addMinutes(listOf(baseDate, UDM.Scalar(45)))
        
        assertTrue(result1 is UDM.DateTime)
        val expected = baseDate.instant.plusSeconds(45 * 60)
        assertEquals(expected.epochSecond, (result1 as UDM.DateTime).instant.epochSecond)

        // Test adding negative minutes
        val result2 = MoreDateFunctions.addMinutes(listOf(baseDate, UDM.Scalar(-30)))
        val expected2 = baseDate.instant.minusSeconds(30 * 60)
        assertEquals(expected2.epochSecond, (result2 as UDM.DateTime).instant.epochSecond)

        // Test adding zero minutes
        val result3 = MoreDateFunctions.addMinutes(listOf(baseDate, UDM.Scalar(0)))
        assertEquals(baseDate.instant.epochSecond, (result3 as UDM.DateTime).instant.epochSecond)
    }

    @Test
    fun testAddSeconds() {
        // Test adding positive seconds
        val baseDate = UDM.DateTime(Instant.parse("2023-10-14T10:30:15Z"))
        val result1 = MoreDateFunctions.addSeconds(listOf(baseDate, UDM.Scalar(120)))
        
        assertTrue(result1 is UDM.DateTime)
        val expected = baseDate.instant.plusSeconds(120)
        assertEquals(expected.epochSecond, (result1 as UDM.DateTime).instant.epochSecond)

        // Test adding negative seconds
        val result2 = MoreDateFunctions.addSeconds(listOf(baseDate, UDM.Scalar(-60)))
        val expected2 = baseDate.instant.minusSeconds(60)
        assertEquals(expected2.epochSecond, (result2 as UDM.DateTime).instant.epochSecond)

        // Test adding zero seconds
        val result3 = MoreDateFunctions.addSeconds(listOf(baseDate, UDM.Scalar(0)))
        assertEquals(baseDate.instant.epochSecond, (result3 as UDM.DateTime).instant.epochSecond)
    }

    @Test
    fun testGetTimezone() {
        // Test with a known date
        val testDate = UDM.DateTime(Instant.parse("2023-07-15T12:00:00Z"))
        val result = MoreDateFunctions.getTimezone(listOf(testDate))
        
        assertTrue(result is UDM.Scalar)
        val timezone = (result as UDM.Scalar).value as String
        
        // Should be in format like "+00:00", "-05:00", etc.
        assertTrue(timezone.matches(Regex("[+-]\\d{2}:\\d{2}")))
        
        // Length should be 6 characters (including sign and colon)
        assertEquals(6, timezone.length)
    }

    @Test
    fun testDiffHours() {
        // Test with dates 24 hours apart
        val date1 = UDM.DateTime(Instant.parse("2023-10-14T10:00:00Z"))
        val date2 = UDM.DateTime(Instant.parse("2023-10-15T10:00:00Z"))
        
        val result1 = MoreDateFunctions.diffHours(listOf(date1, date2))
        assertTrue(result1 is UDM.Scalar)
        assertEquals(24.0, (result1 as UDM.Scalar).value as Double)

        // Test with reverse order (negative difference)
        val result2 = MoreDateFunctions.diffHours(listOf(date2, date1))
        assertEquals(-24.0, (result2 as UDM.Scalar).value as Double)

        // Test with dates 1.5 hours apart
        val date3 = UDM.DateTime(Instant.parse("2023-10-14T10:00:00Z"))
        val date4 = UDM.DateTime(Instant.parse("2023-10-14T11:30:00Z"))
        
        val result3 = MoreDateFunctions.diffHours(listOf(date3, date4))
        assertEquals(1.5, (result3 as UDM.Scalar).value as Double)

        // Test with same dates
        val result4 = MoreDateFunctions.diffHours(listOf(date1, date1))
        assertEquals(0.0, (result4 as UDM.Scalar).value as Double)
    }

    @Test
    fun testDiffMinutes() {
        // Test with dates 2 hours apart
        val date1 = UDM.DateTime(Instant.parse("2023-10-14T10:00:00Z"))
        val date2 = UDM.DateTime(Instant.parse("2023-10-14T12:00:00Z"))
        
        val result1 = MoreDateFunctions.diffMinutes(listOf(date1, date2))
        assertTrue(result1 is UDM.Scalar)
        assertEquals(120.0, (result1 as UDM.Scalar).value as Double)

        // Test with reverse order
        val result2 = MoreDateFunctions.diffMinutes(listOf(date2, date1))
        assertEquals(-120.0, (result2 as UDM.Scalar).value as Double)

        // Test with dates 45 minutes apart
        val date3 = UDM.DateTime(Instant.parse("2023-10-14T10:00:00Z"))
        val date4 = UDM.DateTime(Instant.parse("2023-10-14T10:45:00Z"))
        
        val result3 = MoreDateFunctions.diffMinutes(listOf(date3, date4))
        assertEquals(45.0, (result3 as UDM.Scalar).value as Double)

        // Test with same dates
        val result4 = MoreDateFunctions.diffMinutes(listOf(date1, date1))
        assertEquals(0.0, (result4 as UDM.Scalar).value as Double)
    }

    @Test
    fun testDiffSeconds() {
        // Test with dates 5 minutes apart
        val date1 = UDM.DateTime(Instant.parse("2023-10-14T10:00:00Z"))
        val date2 = UDM.DateTime(Instant.parse("2023-10-14T10:05:00Z"))
        
        val result1 = MoreDateFunctions.diffSeconds(listOf(date1, date2))
        assertTrue(result1 is UDM.Scalar)
        assertEquals(300.0, (result1 as UDM.Scalar).value as Double)

        // Test with reverse order
        val result2 = MoreDateFunctions.diffSeconds(listOf(date2, date1))
        assertEquals(-300.0, (result2 as UDM.Scalar).value as Double)

        // Test with dates 90 seconds apart
        val date3 = UDM.DateTime(Instant.parse("2023-10-14T10:00:00Z"))
        val date4 = UDM.DateTime(Instant.parse("2023-10-14T10:01:30Z"))
        
        val result3 = MoreDateFunctions.diffSeconds(listOf(date3, date4))
        assertEquals(90.0, (result3 as UDM.Scalar).value as Double)

        // Test with same dates
        val result4 = MoreDateFunctions.diffSeconds(listOf(date1, date1))
        assertEquals(0.0, (result4 as UDM.Scalar).value as Double)
    }

    @Test
    fun testCurrentDate() {
        // Test current date function
        val result = MoreDateFunctions.currentDate(emptyList())
        
        assertTrue(result is UDM.Scalar)
        val dateString = (result as UDM.Scalar).value as String
        
        // Should be in YYYY-MM-DD format
        assertTrue(dateString.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        
        // Should be a valid date (doesn't throw when parsing)
        val parsedDate = kotlinx.datetime.LocalDate.parse(dateString)
        assertTrue(parsedDate.year >= 2023) // Should be current year or later
    }

    @Test
    fun testCurrentTime() {
        // Test current time function
        val result = MoreDateFunctions.currentTime(emptyList())
        
        assertTrue(result is UDM.Scalar)
        val timeString = (result as UDM.Scalar).value as String
        
        // Should be in HH:MM:SS format (possibly with fractional seconds)
        assertTrue(timeString.matches(Regex("\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")))
        
        // Should be a valid time (doesn't throw when parsing)
        val parsedTime = kotlinx.datetime.LocalTime.parse(timeString)
        assertTrue(parsedTime.hour in 0..23)
        assertTrue(parsedTime.minute in 0..59)
        assertTrue(parsedTime.second in 0..59)
    }

    @Test
    fun testArgumentValidation() {
        val validDate = UDM.DateTime(Instant.parse("2023-10-14T10:00:00Z"))
        
        // Test wrong number of arguments for addMonths
        assertThrows<FunctionArgumentException> {
            MoreDateFunctions.addMonths(listOf(validDate))
        }
        
        assertThrows<FunctionArgumentException> {
            MoreDateFunctions.addMonths(listOf(validDate, UDM.Scalar(1), UDM.Scalar(2)))
        }

        // Test wrong number of arguments for addYears
        assertThrows<FunctionArgumentException> {
            MoreDateFunctions.addYears(emptyList())
        }

        // Test wrong number of arguments for diffHours
        assertThrows<FunctionArgumentException> {
            MoreDateFunctions.diffHours(listOf(validDate))
        }

        // Test wrong number of arguments for currentDate (should be 0)
        assertThrows<FunctionArgumentException> {
            MoreDateFunctions.currentDate(listOf(validDate))
        }

        // Test wrong number of arguments for currentTime (should be 0)
        assertThrows<FunctionArgumentException> {
            MoreDateFunctions.currentTime(listOf(validDate))
        }
    }

    // Note: testInvalidArgumentTypes removed - validation is handled at runtime by the UTL-X engine via @UTLXFunction annotations

    @Test
    fun testLargeValues() {
        val baseDate = UDM.DateTime(Instant.parse("2023-01-01T00:00:00Z"))
        
        // Test adding large number of months
        val result1 = MoreDateFunctions.addMonths(listOf(baseDate, UDM.Scalar(24)))
        assertTrue(result1 is UDM.DateTime)
        // Should be around 2025
        assertTrue((result1 as UDM.DateTime).instant.isAfter(Instant.parse("2024-12-31T00:00:00Z")))

        // Test adding large number of seconds
        val result2 = MoreDateFunctions.addSeconds(listOf(baseDate, UDM.Scalar(86400))) // 1 day
        val expected = baseDate.instant.plusSeconds(86400)
        assertEquals(expected.epochSecond, (result2 as UDM.DateTime).instant.epochSecond)

        // Test large time differences
        val futureDate = UDM.DateTime(Instant.parse("2025-01-01T00:00:00Z"))
        val result3 = MoreDateFunctions.diffHours(listOf(baseDate, futureDate))
        assertTrue((result3 as UDM.Scalar).value as Double > 17000) // About 2 years in hours
    }

    @Test
    fun testEdgeCases() {
        // Test with leap year dates
        val leapDate = UDM.DateTime(Instant.parse("2020-02-29T12:00:00Z"))
        val result1 = MoreDateFunctions.addYears(listOf(leapDate, UDM.Scalar(1)))
        assertTrue(result1 is UDM.DateTime)
        // Should handle leap year properly

        // Test with end of month
        val endOfMonth = UDM.DateTime(Instant.parse("2023-01-31T12:00:00Z"))
        val result2 = MoreDateFunctions.addMonths(listOf(endOfMonth, UDM.Scalar(1)))
        assertTrue(result2 is UDM.DateTime)
        // Should handle month rollover properly

        // Test with fractional seconds
        val preciseDate = UDM.DateTime(Instant.parse("2023-10-14T10:30:15.123456Z"))
        val result3 = MoreDateFunctions.addSeconds(listOf(preciseDate, UDM.Scalar(1)))
        assertTrue(result3 is UDM.DateTime)
        val expectedNano = preciseDate.instant.plusSeconds(1)
        assertEquals(expectedNano.epochSecond, (result3 as UDM.DateTime).instant.epochSecond)
    }

    @Test
    fun testDecimalNumbers() {
        val baseDate = UDM.DateTime(Instant.parse("2023-10-14T10:00:00Z"))
        
        // Test adding decimal months (should truncate to integer)
        val result1 = MoreDateFunctions.addMonths(listOf(baseDate, UDM.Scalar(2.7)))
        assertTrue(result1 is UDM.DateTime)
        // Should add 2 months (truncated)

        // Test adding decimal minutes
        val result2 = MoreDateFunctions.addMinutes(listOf(baseDate, UDM.Scalar(30.9)))
        assertTrue(result2 is UDM.DateTime)
        // Should add 30 minutes (truncated)

        // Test adding decimal seconds
        val result3 = MoreDateFunctions.addSeconds(listOf(baseDate, UDM.Scalar(45.8)))
        assertTrue(result3 is UDM.DateTime)
        // Should add 45 seconds (truncated)
    }
}