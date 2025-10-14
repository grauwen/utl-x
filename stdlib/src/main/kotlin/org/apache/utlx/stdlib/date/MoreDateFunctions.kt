// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/MoreDateFunctions.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlinx.datetime.*

/**
 * Additional date/time functions for complete parity with TIBCO BW
 */
object MoreDateFunctions {
    
    /**
     * Add months to date
     * Usage: addMonths(now(), 3) => 3 months from now
     */
    fun addMonths(args: List<UDM>): UDM {
        requireArgs(args, 2, "addMonths")
        val date = args[0].asDateTime()
        val months = args[1].asNumber().toInt()
        
        val result = date.plus(months, DateTimeUnit.MONTH, TimeZone.UTC)
        return UDM.DateTime(result)
    }
    
    /**
     * Add years to date
     * Usage: addYears(now(), 1) => 1 year from now
     */
    fun addYears(args: List<UDM>): UDM {
        requireArgs(args, 2, "addYears")
        val date = args[0].asDateTime()
        val years = args[1].asNumber().toInt()
        
        val result = date.plus(years, DateTimeUnit.YEAR, TimeZone.UTC)
        return UDM.DateTime(result)
    }
    
    /**
     * Add minutes to date
     * Usage: addMinutes(now(), 30) => 30 minutes from now
     */
    fun addMinutes(args: List<UDM>): UDM {
        requireArgs(args, 2, "addMinutes")
        val date = args[0].asDateTime()
        val minutes = args[1].asNumber().toInt()
        
        val result = date.plus(minutes, DateTimeUnit.MINUTE, TimeZone.UTC)
        return UDM.DateTime(result)
    }
    
    /**
     * Add seconds to date
     * Usage: addSeconds(now(), 45) => 45 seconds from now
     */
    fun addSeconds(args: List<UDM>): UDM {
        requireArgs(args, 2, "addSeconds")
        val date = args[0].asDateTime()
        val seconds = args[1].asNumber().toInt()
        
        val result = date.plus(seconds, DateTimeUnit.SECOND, TimeZone.UTC)
        return UDM.DateTime(result)
    }
    
    /**
     * Get timezone offset from datetime
     * Usage: getTimezone(now()) => "+00:00" or "-05:00"
     */
    fun getTimezone(args: List<UDM>): UDM {
        requireArgs(args, 1, "getTimezone")
        val date = args[0].asDateTime()
        
        // Get UTC offset for the current system timezone
        val localDateTime = date.toLocalDateTime(TimeZone.currentSystemDefault())
        val offset = TimeZone.currentSystemDefault().offsetAt(date)
        
        val hours = offset.totalSeconds / 3600
        val minutes = (offset.totalSeconds % 3600) / 60
        val sign = if (hours >= 0) "+" else "-"
        val timezone = String.format("%s%02d:%02d", sign, kotlin.math.abs(hours), kotlin.math.abs(minutes))
        
        return UDM.Scalar(timezone)
    }
    
    /**
     * Difference in hours between two dates
     * Usage: diffHours(date1, date2) => 24.5
     */
    fun diffHours(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffHours")
        val date1 = args[0].asDateTime()
        val date2 = args[1].asDateTime()
        
        val diff = date2.minus(date1, TimeZone.UTC)
        val hours = diff.inWholeHours + (diff.inWholeMinutes % 60) / 60.0
        
        return UDM.Scalar(hours)
    }
    
    /**
     * Difference in minutes between two dates
     * Usage: diffMinutes(date1, date2) => 1440
     */
    fun diffMinutes(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffMinutes")
        val date1 = args[0].asDateTime()
        val date2 = args[1].asDateTime()
        
        val diff = date2.minus(date1, TimeZone.UTC)
        val minutes = diff.inWholeMinutes.toDouble()
        
        return UDM.Scalar(minutes)
    }
    
    /**
     * Difference in seconds between two dates
     * Usage: diffSeconds(date1, date2) => 86400
     */
    fun diffSeconds(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffSeconds")
        val date1 = args[0].asDateTime()
        val date2 = args[1].asDateTime()
        
        val diff = date2.minus(date1, TimeZone.UTC)
        val seconds = diff.inWholeSeconds.toDouble()
        
        return UDM.Scalar(seconds)
    }
    
    /**
     * Get current date only (no time component)
     * Usage: currentDate() => "2025-10-14"
     */
    fun currentDate(args: List<UDM>): UDM {
        requireArgs(args, 0, "currentDate")
        val now = Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.UTC).date
        return UDM.Scalar(localDate.toString())
    }
    
    /**
     * Get current time only (no date component)
     * Usage: currentTime() => "14:30:45"
     */
    fun currentTime(args: List<UDM>): UDM {
        requireArgs(args, 0, "currentTime")
        val now = Clock.System.now()
        val localTime = now.toLocalDateTime(TimeZone.UTC).time
        return UDM.Scalar(localTime.toString())
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asDateTime(): Instant = when (this) {
        is UDM.DateTime -> instant
        else -> throw FunctionArgumentException("Expected datetime value")
    }
    
    private fun UDM.asNumber(): Double = when (this) {
        is UDM.Scalar -> when (value) {
            is Number -> value.toDouble()
            else -> throw FunctionArgumentException("Expected number value")
        }
        else -> throw FunctionArgumentException("Expected number value")
    }
}
