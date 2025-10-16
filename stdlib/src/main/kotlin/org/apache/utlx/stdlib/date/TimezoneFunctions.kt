// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/TimezoneFunctions.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlinx.datetime.*
import java.time.Instant as JavaInstant

/**
 * Comprehensive timezone manipulation functions
 */
object TimezoneFunctions {
    
    /**
     * Convert datetime between timezones
     * Usage: convertTimezone(now(), "UTC", "America/New_York")
     */
    fun convertTimezone(args: List<UDM>): UDM {
        requireArgs(args, 3, "convertTimezone")
        val date = extractDateTime(args[0])
        val fromTz = args[1].asString()
        val toTz = args[2].asString()
        
        return try {
            val fromZone = TimeZone.of(fromTz)
            val toZone = TimeZone.of(toTz)
            
            // Convert through UTC
            val kotlinxDate = toKotlinxInstant(date)
            val localInFrom = kotlinxDate.toLocalDateTime(fromZone)
            val instantInUTC = localInFrom.toInstant(fromZone)
            val localInTo = instantInUTC.toLocalDateTime(toZone)
            
            // Return the instant (which is timezone-agnostic)
            UDM.DateTime(toJavaInstant(instantInUTC))
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid timezone: $fromTz or $toTz")
        }
    }
    
    /**
     * Get timezone name/ID for current system
     * Usage: getTimezoneName() => "America/New_York"
     */
    fun getTimezoneName(args: List<UDM>): UDM {
        requireArgs(args, 0, "getTimezoneName")
        return UDM.Scalar(TimeZone.currentSystemDefault().id)
    }
    
    /**
     * Get timezone offset in seconds
     * Usage: getTimezoneOffsetSeconds(now(), "America/New_York") => -18000
     */
    fun getTimezoneOffsetSeconds(args: List<UDM>): UDM {
        requireArgs(args, 2, "getTimezoneOffsetSeconds")
        val date = extractDateTime(args[0])
        val tzId = args[1].asString()
        
        return try {
            val tz = TimeZone.of(tzId)
            val kotlinxDate = toKotlinxInstant(date)
            val offset = tz.offsetAt(kotlinxDate)
            UDM.Scalar(offset.totalSeconds.toDouble())
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid timezone: $tzId")
        }
    }
    
    /**
     * Get timezone offset in hours
     * Usage: getTimezoneOffsetHours(now(), "America/New_York") => -5.0
     */
    fun getTimezoneOffsetHours(args: List<UDM>): UDM {
        requireArgs(args, 2, "getTimezoneOffsetHours")
        val date = extractDateTime(args[0])
        val tzId = args[1].asString()
        
        return try {
            val tz = TimeZone.of(tzId)
            val kotlinxDate = toKotlinxInstant(date)
            val offset = tz.offsetAt(kotlinxDate)
            UDM.Scalar(offset.totalSeconds / 3600.0)
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid timezone: $tzId")
        }
    }
    
    /**
     * Parse datetime with timezone
     * Usage: parseDateTimeWithTimezone("2025-10-14T12:00:00", "America/New_York")
     */
    fun parseDateTimeWithTimezone(args: List<UDM>): UDM {
        requireArgs(args, 2, "parseDateTimeWithTimezone")
        val dateStr = args[0].asString()
        val tzId = args[1].asString()
        
        return try {
            val tz = TimeZone.of(tzId)
            val localDateTime = LocalDateTime.parse(dateStr)
            val instant = localDateTime.toInstant(tz)
            UDM.DateTime(toJavaInstant(instant))
        } catch (e: Exception) {
            throw FunctionArgumentException("Cannot parse datetime: $dateStr with timezone: $tzId")
        }
    }
    
    /**
     * Format datetime in specific timezone
     * Usage: formatDateTimeInTimezone(now(), "America/New_York", "yyyy-MM-dd HH:mm:ss z")
     */
    fun formatDateTimeInTimezone(args: List<UDM>): UDM {
        requireArgs(args, 2..3, "formatDateTimeInTimezone")
        val date = extractDateTime(args[0])
        val tzId = args[1].asString()
        val format = if (args.size > 2) args[2].asString() else "yyyy-MM-dd'T'HH:mm:ss"
        
        return try {
            val tz = TimeZone.of(tzId)
            val kotlinxDate = toKotlinxInstant(date)
            val localDateTime = kotlinxDate.toLocalDateTime(tz)
            
            // Simple formatting (could be enhanced with more patterns)
            val formatted = when (format) {
                "yyyy-MM-dd'T'HH:mm:ss" -> localDateTime.toString()
                "yyyy-MM-dd HH:mm:ss" -> "${localDateTime.date} ${localDateTime.time}"
                "yyyy-MM-dd" -> localDateTime.date.toString()
                "HH:mm:ss" -> localDateTime.time.toString()
                else -> localDateTime.toString()
            }
            
            UDM.Scalar(formatted)
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid timezone: $tzId")
        }
    }
    
    /**
     * Check if timezone is valid
     * Usage: isValidTimezone("America/New_York") => true
     */
    fun isValidTimezone(args: List<UDM>): UDM {
        requireArgs(args, 1, "isValidTimezone")
        val tzId = args[0].asString()
        
        return try {
            TimeZone.of(tzId)
            UDM.Scalar(true)
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }
    
    /**
     * Get UTC datetime from local datetime and timezone
     * Usage: toUTC(localDateTime, "America/New_York")
     */
    fun toUTC(args: List<UDM>): UDM {
        requireArgs(args, 2, "toUTC")
        val date = extractDateTime(args[0])
        val tzId = args[1].asString()
        
        return try {
            val tz = TimeZone.of(tzId)
            val kotlinxDate = toKotlinxInstant(date)
            val localDateTime = kotlinxDate.toLocalDateTime(tz)
            val utcInstant = localDateTime.toInstant(tz)
            UDM.DateTime(toJavaInstant(utcInstant))
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid timezone: $tzId")
        }
    }
    
    /**
     * Get local datetime from UTC in specified timezone
     * Usage: fromUTC(utcDateTime, "America/New_York")
     */
    fun fromUTC(args: List<UDM>): UDM {
        requireArgs(args, 2, "fromUTC")
        val utcDate = extractDateTime(args[0])
        val tzId = args[1].asString()
        
        return try {
            val tz = TimeZone.of(tzId)
            val kotlinxUtcDate = toKotlinxInstant(utcDate)
            val localDateTime = kotlinxUtcDate.toLocalDateTime(tz)
            // Return as instant but user understands it's in that timezone
            UDM.DateTime(utcDate)
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid timezone: $tzId")
        }
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun requireArgs(args: List<UDM>, range: IntRange, functionName: String) {
        if (args.size !in range) {
            throw FunctionArgumentException("$functionName expects ${range.first}..${range.last} arguments, got ${args.size}")
        }
    }
    
    // Helper functions to convert between java.time.Instant and kotlinx.datetime.Instant
    private fun toKotlinxInstant(javaInstant: JavaInstant): kotlinx.datetime.Instant {
        return kotlinx.datetime.Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
    }
    
    private fun toJavaInstant(kotlinxInstant: kotlinx.datetime.Instant): JavaInstant {
        return JavaInstant.ofEpochSecond(kotlinxInstant.epochSeconds, kotlinxInstant.nanosecondsOfSecond.toLong())
    }
    
    private fun extractDateTime(udm: UDM): JavaInstant = when (udm) {
        is UDM.DateTime -> udm.instant
        else -> throw FunctionArgumentException("Expected datetime value")
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value")
    }
}
