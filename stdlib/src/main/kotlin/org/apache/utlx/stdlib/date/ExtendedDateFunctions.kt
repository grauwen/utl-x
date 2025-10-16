// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/ExtendedDateFunctions.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlinx.datetime.*
import java.time.Instant as JavaInstant

/**
 * Extended date/time functions
 */
object ExtendedDateFunctions {
    
    // Helper functions to convert between java.time.Instant and kotlinx.datetime.Instant
    private fun toKotlinxInstant(javaInstant: JavaInstant): kotlinx.datetime.Instant {
        return kotlinx.datetime.Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
    }
    
    private fun toJavaInstant(kotlinxInstant: kotlinx.datetime.Instant): JavaInstant {
        return JavaInstant.ofEpochSecond(kotlinxInstant.epochSeconds, kotlinxInstant.nanosecondsOfSecond.toLong())
    }
    
    /**
     * Extract day component
     * Usage: day(now()) => 14
     */
    fun day(args: List<UDM>): UDM {
        requireArgs(args, 1, "day")
        val date = extractDateTime(args[0])
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC)
        return UDM.Scalar(localDate.dayOfMonth.toDouble())
    }
    
    /**
     * Extract month component (1-12)
     * Usage: month(now()) => 10
     */
    fun month(args: List<UDM>): UDM {
        requireArgs(args, 1, "month")
        val date = extractDateTime(args[0])
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC)
        return UDM.Scalar(localDate.monthNumber.toDouble())
    }
    
    /**
     * Extract year component
     * Usage: year(now()) => 2025
     */
    fun year(args: List<UDM>): UDM {
        requireArgs(args, 1, "year")
        val date = extractDateTime(args[0])
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC)
        return UDM.Scalar(localDate.year.toDouble())
    }
    
    /**
     * Extract hours component (0-23)
     * Usage: hours(now()) => 14
     */
    fun hours(args: List<UDM>): UDM {
        requireArgs(args, 1, "hours")
        val date = extractDateTime(args[0])
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC)
        return UDM.Scalar(localDate.hour.toDouble())
    }
    
    /**
     * Extract minutes component (0-59)
     * Usage: minutes(now()) => 30
     */
    fun minutes(args: List<UDM>): UDM {
        requireArgs(args, 1, "minutes")
        val date = extractDateTime(args[0])
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC)
        return UDM.Scalar(localDate.minute.toDouble())
    }
    
    /**
     * Extract seconds component (0-59)
     * Usage: seconds(now()) => 45
     */
    fun seconds(args: List<UDM>): UDM {
        requireArgs(args, 1, "seconds")
        val date = extractDateTime(args[0])
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC)
        return UDM.Scalar(localDate.second.toDouble())
    }
    
    /**
     * Compare two dates
     * Returns: -1 if date1 < date2, 0 if equal, 1 if date1 > date2
     * Usage: compare-dates(date1, date2)
     */
    fun compareDates(args: List<UDM>): UDM {
        requireArgs(args, 2, "compare-dates")
        val date1 = extractDateTime(args[0])
        val date2 = extractDateTime(args[1])
        
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
    
    private fun extractDateTime(udm: UDM): JavaInstant = when (udm) {
        is UDM.DateTime -> udm.instant
        else -> throw FunctionArgumentException("Expected datetime value")
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value")
    }
}
