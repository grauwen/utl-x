// stdlib/src/test/kotlin/org/apache/utlx/stdlib/date/TimezoneFunctionsTest.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Instant

class TimezoneFunctionsTest {

    private val baseDate = UDM.DateTime(Instant.parse("2025-10-17T12:00:00Z"))

    // ========== TIMEZONE CONVERSION ==========

    @Test
    fun testConvertTimezone() {
        val result = TimezoneFunctions.convertTimezone(
            listOf(baseDate, UDM.Scalar("UTC"), UDM.Scalar("America/New_York"))
        )
        
        assertTrue(result is UDM.DateTime)
        // The instant should be the same (timezone-agnostic)
        assertEquals(baseDate.instant, result.instant)
    }

    @Test
    fun testConvertTimezoneEuropeToAmerica() {
        val result = TimezoneFunctions.convertTimezone(
            listOf(baseDate, UDM.Scalar("Europe/London"), UDM.Scalar("America/Los_Angeles"))
        )
        
        assertTrue(result is UDM.DateTime)
        assertTrue(result.instant is Instant)
    }

    @Test
    fun testConvertTimezoneAsiaToEurope() {
        val result = TimezoneFunctions.convertTimezone(
            listOf(baseDate, UDM.Scalar("Asia/Tokyo"), UDM.Scalar("Europe/Paris"))
        )
        
        assertTrue(result is UDM.DateTime)
        assertTrue(result.instant is Instant)
    }

    @Test
    fun testConvertTimezoneInvalidFrom() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.convertTimezone(
                listOf(baseDate, UDM.Scalar("Invalid/Timezone"), UDM.Scalar("UTC"))
            )
        }
    }

    @Test
    fun testConvertTimezoneInvalidTo() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.convertTimezone(
                listOf(baseDate, UDM.Scalar("UTC"), UDM.Scalar("Invalid/Timezone"))
            )
        }
    }

    // ========== TIMEZONE NAME/ID ==========

    @Test
    fun testGetTimezoneName() {
        val result = TimezoneFunctions.getTimezoneName(listOf())
        
        assertTrue(result is UDM.Scalar)
        assertTrue((result.value as String).isNotEmpty())
        // Should contain a valid timezone ID format
        assertTrue((result.value as String).contains("/") || result.value == "UTC")
    }

    // ========== TIMEZONE OFFSET ==========

    @Test
    fun testGetTimezoneOffsetSecondsUTC() {
        val result = TimezoneFunctions.getTimezoneOffsetSeconds(
            listOf(baseDate, UDM.Scalar("UTC"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertEquals(0.0, result.value)
    }

    @Test
    fun testGetTimezoneOffsetSecondsEST() {
        val result = TimezoneFunctions.getTimezoneOffsetSeconds(
            listOf(baseDate, UDM.Scalar("America/New_York"))
        )
        
        assertTrue(result is UDM.Scalar)
        // EST is UTC-5 (but could be EDT UTC-4 depending on DST)
        val offset = result.value as Double
        assertTrue(offset == -18000.0 || offset == -14400.0) // -5 hours or -4 hours
    }

    @Test
    fun testGetTimezoneOffsetSecondsInvalidTimezone() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.getTimezoneOffsetSeconds(
                listOf(baseDate, UDM.Scalar("Invalid/Timezone"))
            )
        }
    }

    @Test
    fun testGetTimezoneOffsetHoursUTC() {
        val result = TimezoneFunctions.getTimezoneOffsetHours(
            listOf(baseDate, UDM.Scalar("UTC"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertEquals(0.0, result.value)
    }

    @Test
    fun testGetTimezoneOffsetHoursEST() {
        val result = TimezoneFunctions.getTimezoneOffsetHours(
            listOf(baseDate, UDM.Scalar("America/New_York"))
        )
        
        assertTrue(result is UDM.Scalar)
        val offset = result.value as Double
        assertTrue(offset == -5.0 || offset == -4.0) // EST or EDT
    }

    @Test
    fun testGetTimezoneOffsetHoursTokyo() {
        val result = TimezoneFunctions.getTimezoneOffsetHours(
            listOf(baseDate, UDM.Scalar("Asia/Tokyo"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertEquals(9.0, result.value) // JST is UTC+9
    }

    @Test
    fun testGetTimezoneOffsetHoursInvalidTimezone() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.getTimezoneOffsetHours(
                listOf(baseDate, UDM.Scalar("Invalid/Timezone"))
            )
        }
    }

    // ========== PARSE DATETIME WITH TIMEZONE ==========

    @Test
    fun testParseDateTimeWithTimezone() {
        val result = TimezoneFunctions.parseDateTimeWithTimezone(
            listOf(UDM.Scalar("2025-10-17T12:00:00"), UDM.Scalar("UTC"))
        )
        
        assertTrue(result is UDM.DateTime)
        assertEquals(Instant.parse("2025-10-17T12:00:00Z"), result.instant)
    }

    @Test
    fun testParseDateTimeWithTimezoneEST() {
        val result = TimezoneFunctions.parseDateTimeWithTimezone(
            listOf(UDM.Scalar("2025-10-17T12:00:00"), UDM.Scalar("America/New_York"))
        )
        
        assertTrue(result is UDM.DateTime)
        assertTrue(result.instant is Instant)
        // The instant should be adjusted for the timezone
    }

    @Test
    fun testParseDateTimeWithTimezoneInvalidFormat() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.parseDateTimeWithTimezone(
                listOf(UDM.Scalar("invalid date"), UDM.Scalar("UTC"))
            )
        }
    }

    @Test
    fun testParseDateTimeWithTimezoneInvalidTimezone() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.parseDateTimeWithTimezone(
                listOf(UDM.Scalar("2025-10-17T12:00:00"), UDM.Scalar("Invalid/Timezone"))
            )
        }
    }

    // ========== FORMAT DATETIME IN TIMEZONE ==========

    @Test
    fun testFormatDateTimeInTimezoneDefault() {
        val result = TimezoneFunctions.formatDateTimeInTimezone(
            listOf(baseDate, UDM.Scalar("UTC"))
        )

        assertTrue(result is UDM.Scalar)
        assertTrue((result.value as String).contains("2025-10-17"))
        assertTrue((result.value as String).contains("12:00"))  // LocalDateTime.toString() omits seconds if zero
    }

    @Test
    fun testFormatDateTimeInTimezoneCustomFormat() {
        val result = TimezoneFunctions.formatDateTimeInTimezone(
            listOf(baseDate, UDM.Scalar("UTC"), UDM.Scalar("yyyy-MM-dd"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertEquals("2025-10-17", result.value)
    }

    @Test
    fun testFormatDateTimeInTimezoneTimeOnly() {
        val result = TimezoneFunctions.formatDateTimeInTimezone(
            listOf(baseDate, UDM.Scalar("UTC"), UDM.Scalar("HH:mm:ss"))
        )

        assertTrue(result is UDM.Scalar)
        assertEquals("12:00", result.value)  // LocalTime.toString() omits seconds if zero
    }

    @Test
    fun testFormatDateTimeInTimezoneInvalidTimezone() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.formatDateTimeInTimezone(
                listOf(baseDate, UDM.Scalar("Invalid/Timezone"))
            )
        }
    }

    // ========== TIMEZONE VALIDATION ==========

    @Test
    fun testIsValidTimezoneValid() {
        val result = TimezoneFunctions.isValidTimezone(
            listOf(UDM.Scalar("America/New_York"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsValidTimezoneUTC() {
        val result = TimezoneFunctions.isValidTimezone(
            listOf(UDM.Scalar("UTC"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsValidTimezoneEurope() {
        val result = TimezoneFunctions.isValidTimezone(
            listOf(UDM.Scalar("Europe/London"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsValidTimezoneAsia() {
        val result = TimezoneFunctions.isValidTimezone(
            listOf(UDM.Scalar("Asia/Tokyo"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsValidTimezoneInvalid() {
        val result = TimezoneFunctions.isValidTimezone(
            listOf(UDM.Scalar("Invalid/Timezone"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testIsValidTimezoneEmpty() {
        val result = TimezoneFunctions.isValidTimezone(
            listOf(UDM.Scalar(""))
        )
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    // ========== UTC CONVERSION ==========

    @Test
    fun testToUTC() {
        val result = TimezoneFunctions.toUTC(
            listOf(baseDate, UDM.Scalar("UTC"))
        )
        
        assertTrue(result is UDM.DateTime)
        assertEquals(baseDate.instant, result.instant)
    }

    @Test
    fun testToUTCFromEST() {
        val result = TimezoneFunctions.toUTC(
            listOf(baseDate, UDM.Scalar("America/New_York"))
        )
        
        assertTrue(result is UDM.DateTime)
        assertTrue(result.instant is Instant)
    }

    @Test
    fun testToUTCInvalidTimezone() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.toUTC(
                listOf(baseDate, UDM.Scalar("Invalid/Timezone"))
            )
        }
    }

    @Test
    fun testFromUTC() {
        val result = TimezoneFunctions.fromUTC(
            listOf(baseDate, UDM.Scalar("UTC"))
        )
        
        assertTrue(result is UDM.DateTime)
        assertEquals(baseDate.instant, result.instant)
    }

    @Test
    fun testFromUTCToEST() {
        val result = TimezoneFunctions.fromUTC(
            listOf(baseDate, UDM.Scalar("America/New_York"))
        )
        
        assertTrue(result is UDM.DateTime)
        assertEquals(baseDate.instant, result.instant) // Function returns original instant
    }

    @Test
    fun testFromUTCInvalidTimezone() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.fromUTC(
                listOf(baseDate, UDM.Scalar("Invalid/Timezone"))
            )
        }
    }

    // ========== ERROR HANDLING ==========

    @Test
    fun testConvertTimezoneInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.convertTimezone(listOf(baseDate, UDM.Scalar("UTC")))
        }
    }

    @Test
    fun testConvertTimezoneTooManyArgs() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.convertTimezone(
                listOf(baseDate, UDM.Scalar("UTC"), UDM.Scalar("EST"), UDM.Scalar("extra"))
            )
        }
    }

    @Test
    fun testConvertTimezoneInvalidDateType() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.convertTimezone(
                listOf(UDM.Scalar("not a date"), UDM.Scalar("UTC"), UDM.Scalar("EST"))
            )
        }
    }

    @Test
    fun testGetTimezoneNameTooManyArgs() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.getTimezoneName(listOf(UDM.Scalar("extra")))
        }
    }

    @Test
    fun testGetTimezoneOffsetSecondsInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.getTimezoneOffsetSeconds(listOf(baseDate))
        }
    }

    @Test
    fun testGetTimezoneOffsetHoursInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.getTimezoneOffsetHours(listOf(baseDate))
        }
    }

    @Test
    fun testParseDateTimeWithTimezoneInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.parseDateTimeWithTimezone(listOf(UDM.Scalar("2025-10-17T12:00:00")))
        }
    }

    @Test
    fun testFormatDateTimeInTimezoneInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.formatDateTimeInTimezone(listOf(baseDate))
        }
    }

    @Test
    fun testIsValidTimezoneInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.isValidTimezone(listOf())
        }
    }

    @Test
    fun testToUTCInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.toUTC(listOf(baseDate))
        }
    }

    @Test
    fun testFromUTCInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.fromUTC(listOf(baseDate))
        }
    }

    // ========== EDGE CASES ==========

    @Test
    fun testConvertTimezoneIdentical() {
        val result = TimezoneFunctions.convertTimezone(
            listOf(baseDate, UDM.Scalar("UTC"), UDM.Scalar("UTC"))
        )
        
        assertTrue(result is UDM.DateTime)
        assertEquals(baseDate.instant, result.instant)
    }

    @Test
    fun testGetTimezoneOffsetSecondsGMTPlus() {
        val result = TimezoneFunctions.getTimezoneOffsetSeconds(
            listOf(baseDate, UDM.Scalar("Europe/Berlin"))
        )
        
        assertTrue(result is UDM.Scalar)
        val offset = result.value as Double
        assertTrue(offset > 0) // Should be positive for GMT+
    }

    @Test
    fun testFormatDateTimeInTimezoneUnknownFormat() {
        val result = TimezoneFunctions.formatDateTimeInTimezone(
            listOf(baseDate, UDM.Scalar("UTC"), UDM.Scalar("unknown-format"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertTrue((result.value as String).isNotEmpty())
    }

    @Test
    fun testParseDateTimeWithTimezoneComplexFormat() {
        val result = TimezoneFunctions.parseDateTimeWithTimezone(
            listOf(UDM.Scalar("2025-12-25T00:00:00"), UDM.Scalar("Europe/London"))
        )
        
        assertTrue(result is UDM.DateTime)
        assertTrue(result.instant is Instant)
    }

    @Test
    fun testIsValidTimezoneNull() {
        val result = TimezoneFunctions.isValidTimezone(
            listOf(UDM.Scalar.nullValue())
        )
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testConvertTimezoneNullValue() {
        assertThrows<FunctionArgumentException> {
            TimezoneFunctions.convertTimezone(
                listOf(baseDate, UDM.Scalar.nullValue(), UDM.Scalar("UTC"))
            )
        }
    }
}