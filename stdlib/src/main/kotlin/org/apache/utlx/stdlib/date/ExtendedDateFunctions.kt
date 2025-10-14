// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/ExtendedDateFunctions.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlinx.datetime.*

/**
 * Extended date/time functions
 */
object ExtendedDateFunctions {
    
    /**
     * Extract day component
     * Usage: day(now()) => 14
     */
    fun day(args: List<UDM>): UDM {
        requireArgs(args, 1, "day")
        val date = args[0].asDateTime()
        val localDate = date.toLocalDateTime(TimeZone.UTC)
        return UDM.Scalar(localDate.dayOfMonth.toDouble())
    }
    
    /**
     * Extract month component (1-12)
     * Usage: month(now()) => 10
     */
    fun month(args: List<UDM>): UDM {
        requireArgs(args, 1, "month")
        val date = args[0].asDateTime()
        val localDate = date.toLocalDateTime(TimeZone.UTC)
        return UDM.Scalar(localDate.monthNumber.toDouble())
    }
    
    /**
     * Extract year component
     * Usage: year(now()) => 2025
     */
    fun year(args: List<UDM>): UDM {
        requireArgs(args, 1, "year")
        val date = args[0].asDateTime()
        val localDate = date.toLocalDateTime(TimeZone.UTC)
        return UDM.Scalar(localDate.year.toDouble())
    }
    
    /**
     * Extract hours component (0-23)
     * Usage: hours(now()) => 14
     */
    fun hours(args: List<UDM>): UDM {
        requireArgs(args, 1, "hours")
        val date = args[0].asDateTime()
        val localDate = date.toLocalDateTime(TimeZone.UTC)
        return UDM.Scalar(localDate.hour.toDouble())
    }
    
    /**
     * Extract minutes component (0-59)
     * Usage: minutes(now()) => 30
     */
    fun minutes(args: List<UDM>): UDM {
        requireArgs(args, 1, "minutes")
        val date = args[0].asDateTime()
        val localDate = date.toLocalDateTime(TimeZone.UTC)
        return UDM.Scalar(localDate.minute.toDouble())
    }
    
    /**
     * Extract seconds component (0-59)
     * Usage: seconds(now()) => 45
     */
    fun seconds(args: List<UDM>): UDM {
        requireArgs(args, 1, "seconds")
        val date = args[0].asDateTime()
        val localDate = date.toLocalDateTime(TimeZone.UTC)
        return UDM.Scalar(localDate.second.toDouble())
    }
    
    /**
     * Compare two dates
     * Returns: -1 if date1 < date2, 0 if equal, 1 if date1 > date2
     * Usage: compare-dates(date1, date2)
     */
    fun compareDates(args: List<UDM>): UDM {
        requireArgs(args, 2, "compare-dates")
        val date1 = args[0].asDateTime()
        val date2 = args[1].asDateTime()
        
        val result = when {
            date1 < date2 -> -1.0
            date1 > date2 -> 1.0
            else -> 0.0
        }
        return UDM.Scalar(result)
    }
    
    /**
     * Validate date string against pattern
     * Usage: validate-date("yyyy-MM-dd", "2025-10-14") => true
     */
    fun validateDate(args: List<UDM>): UDM {
        requireArgs(args, 2, "validate-date")
        val pattern = args[0].asString()
        val dateStr = args[1].asString()
        
        return try {
            // Attempt to parse - if successful, it's valid
            Instant.parse(dateStr)
            UDM.Scalar(true)
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
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
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value")
    }
}
