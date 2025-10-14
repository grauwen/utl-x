// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/TimezoneFunctions.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlinx.datetime.*

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
        val date = args[0].asDateTime()
        val fromTz = args[1].asString()
        val toTz = args[2].asString()
        
        return try {
            val fromZone = TimeZone.of(fromTz)
            val toZone = TimeZone.of(toTz)
            
            // Convert through UTC
            val localInFrom = date.toLocalDateTime(fromZone)
            val instantInUTC = localInFrom.toInstant(fromZone)
            val localInTo = instantInUTC.toLocalDateTime(toZone)
            
            // Return the instant (which is timezone-agnostic)
            UDM.DateTime(instantInUTC)
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
        val date = args[0].asDateTime()
        val tzId = args[1].asString()
        
        return try {
            val tz = TimeZone.of(tzId)
            val offset = tz.offsetAt(date)
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
        val date = args[0].asDateTime()
        val tzId = args[1].asString()
        
        return try {
            val tz = TimeZone.of(tzId)
            val offset = tz.offsetAt(date)
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
            UDM.DateTime(instant)
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
        val date = args[0].asDateTime()
        val tzId = args[1].asString()
        val format = if (args.size > 2) args[2].asString() else "yyyy-MM-dd'T'HH:mm:ss"
        
        return try {
            val tz = TimeZone.of(tzId)
            val localDateTime = date.toLocalDateTime(tz)
            
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
        val date = args[0].asDateTime()
        val tzId = args[1].asString()
        
        return try {
            val tz = TimeZone.of(tzId)
            val localDateTime = date.toLocalDateTime(tz)
            val utcInstant = localDateTime.toInstant(tz)
            UDM.DateTime(utcInstant)
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
        val utcDate = args[0].asDateTime()
        val tzId = args[1].asString()
        
        return try {
            val tz = TimeZone.of(tzId)
            val localDateTime = utcDate.toLocalDateTime(tz)
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
    
    private fun UDM.asDateTime(): Instant = when (this) {
        is UDM.DateTime -> instant
        else -> throw FunctionArgumentException("Expected datetime value")
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value")
    }
}
