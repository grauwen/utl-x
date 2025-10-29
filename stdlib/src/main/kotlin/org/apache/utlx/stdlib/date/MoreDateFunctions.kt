// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/MoreDateFunctions.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlinx.datetime.*
import java.time.Instant as JavaInstant
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Additional date/time functions for complete parity with TIBCO BW
 */
object MoreDateFunctions {
    
    // Helper functions to convert between java.time.Instant and kotlinx.datetime.Instant
    private fun toKotlinxInstant(javaInstant: JavaInstant): kotlinx.datetime.Instant {
        return kotlinx.datetime.Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
    }
    
    private fun toJavaInstant(kotlinxInstant: kotlinx.datetime.Instant): JavaInstant {
        return JavaInstant.ofEpochSecond(kotlinxInstant.epochSeconds, kotlinxInstant.nanosecondsOfSecond.toLong())
    }
    
    @UTLXFunction(
        description = "Add months to date",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "months: Months value"
        ],
        returns = "Result of the operation",
        example = "addMonths(now(), 3) => 3 months from now",
        tags = ["date"],
        since = "1.0"
    )
    /**
     * Add months to date
     * Usage: addMonths(now(), 3) => 3 months from now
     */
    fun addMonths(args: List<UDM>): UDM {
        requireArgs(args, 2, "addMonths")
        val date = extractDateTime(args[0])
        val months = args[1].asNumber().toInt()
        
        val result = toKotlinxInstant(date).plus(months, DateTimeUnit.MONTH, TimeZone.UTC)
        return UDM.DateTime(toJavaInstant(result))
    }
    
    @UTLXFunction(
        description = "Add years to date",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "years: Years value"
        ],
        returns = "Result of the operation",
        example = "addYears(now(), 1) => 1 year from now",
        tags = ["date"],
        since = "1.0"
    )
    /**
     * Add years to date
     * Usage: addYears(now(), 1) => 1 year from now
     */
    fun addYears(args: List<UDM>): UDM {
        requireArgs(args, 2, "addYears")
        val date = extractDateTime(args[0])
        val years = args[1].asNumber().toInt()
        
        val result = toKotlinxInstant(date).plus(years, DateTimeUnit.YEAR, TimeZone.UTC)
        return UDM.DateTime(toJavaInstant(result))
    }
    
    @UTLXFunction(
        description = "Add minutes to date",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "minutes: Minutes value"
        ],
        returns = "Result of the operation",
        example = "addMinutes(now(), 30) => 30 minutes from now",
        tags = ["date"],
        since = "1.0"
    )
    /**
     * Add minutes to date
     * Usage: addMinutes(now(), 30) => 30 minutes from now
     */
    fun addMinutes(args: List<UDM>): UDM {
        requireArgs(args, 2, "addMinutes")
        val date = extractDateTime(args[0])
        val minutes = args[1].asNumber().toInt()
        
        val result = toKotlinxInstant(date).plus(minutes, DateTimeUnit.MINUTE, TimeZone.UTC)
        return UDM.DateTime(toJavaInstant(result))
    }
    
    @UTLXFunction(
        description = "Add seconds to date",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "seconds: Seconds value"
        ],
        returns = "Result of the operation",
        example = "addSeconds(now(), 45) => 45 seconds from now",
        tags = ["date"],
        since = "1.0"
    )
    /**
     * Add seconds to date
     * Usage: addSeconds(now(), 45) => 45 seconds from now
     */
    fun addSeconds(args: List<UDM>): UDM {
        requireArgs(args, 2, "addSeconds")
        val date = extractDateTime(args[0])
        val seconds = args[1].asNumber().toInt()
        
        val result = toKotlinxInstant(date).plus(seconds, DateTimeUnit.SECOND, TimeZone.UTC)
        return UDM.DateTime(toJavaInstant(result))
    }
    
    @UTLXFunction(
        description = "Get timezone offset from datetime",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        returns = "Result of the operation",
        example = "getTimezone(now()) => \"+00:00\" or \"-05:00\"",
        tags = ["date"],
        since = "1.0"
    )
    /**
     * Get timezone offset from datetime
     * Usage: getTimezone(now()) => "+00:00" or "-05:00"
     */
    fun getTimezone(args: List<UDM>): UDM {
        requireArgs(args, 1, "getTimezone")
        val date = extractDateTime(args[0])
        
        // Get UTC offset for the current system timezone
        val kotlinxDate = toKotlinxInstant(date)
        val localDateTime = kotlinxDate.toLocalDateTime(TimeZone.currentSystemDefault())
        val offset = TimeZone.currentSystemDefault().offsetAt(kotlinxDate)
        
        val hours = offset.totalSeconds / 3600
        val minutes = (offset.totalSeconds % 3600) / 60
        val sign = if (hours >= 0) "+" else "-"
        val timezone = String.format("%s%02d:%02d", sign, kotlin.math.abs(hours), kotlin.math.abs(minutes))
        
        return UDM.Scalar(timezone)
    }
    
    @UTLXFunction(
        description = "Difference in hours between two dates",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        returns = "Result of the operation",
        example = "diffHours(date1, date2) => 24.5",
        tags = ["date"],
        since = "1.0"
    )
    /**
     * Difference in hours between two dates
     * Usage: diffHours(date1, date2) => 24.5
     */
    fun diffHours(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffHours")
        val date1 = extractDateTime(args[0])
        val date2 = extractDateTime(args[1])
        
        val kotlinxDate1 = toKotlinxInstant(date1)
        val kotlinxDate2 = toKotlinxInstant(date2)
        val diff = kotlinxDate2.minus(kotlinxDate1)
        val hours = diff.inWholeHours + (diff.inWholeMinutes % 60) / 60.0
        
        return UDM.Scalar(hours)
    }
    
    @UTLXFunction(
        description = "Difference in minutes between two dates",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        returns = "Result of the operation",
        example = "diffMinutes(date1, date2) => 1440",
        tags = ["date"],
        since = "1.0"
    )
    /**
     * Difference in minutes between two dates
     * Usage: diffMinutes(date1, date2) => 1440
     */
    fun diffMinutes(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffMinutes")
        val date1 = extractDateTime(args[0])
        val date2 = extractDateTime(args[1])
        
        val kotlinxDate1 = toKotlinxInstant(date1)
        val kotlinxDate2 = toKotlinxInstant(date2)
        val diff = kotlinxDate2.minus(kotlinxDate1)
        val minutes = diff.inWholeMinutes.toDouble()
        
        return UDM.Scalar(minutes)
    }
    
    @UTLXFunction(
        description = "Difference in seconds between two dates",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        returns = "Result of the operation",
        example = "diffSeconds(date1, date2) => 86400",
        tags = ["date"],
        since = "1.0"
    )
    /**
     * Difference in seconds between two dates
     * Usage: diffSeconds(date1, date2) => 86400
     */
    fun diffSeconds(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffSeconds")
        val date1 = extractDateTime(args[0])
        val date2 = extractDateTime(args[1])
        
        val kotlinxDate1 = toKotlinxInstant(date1)
        val kotlinxDate2 = toKotlinxInstant(date2)
        val diff = kotlinxDate2.minus(kotlinxDate1)
        val seconds = diff.inWholeSeconds.toDouble()
        
        return UDM.Scalar(seconds)
    }
    
    @UTLXFunction(
        description = "Get current date only (no time component)",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        returns = "Result of the operation",
        example = "currentDate() => \"2025-10-14\"",
        tags = ["date"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Get current time only (no date component)",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        returns = "Result of the operation",
        example = "currentTime() => \"14:30:45\"",
        tags = ["date"],
        since = "1.0"
    )
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
    
    private fun extractDateTime(udm: UDM): JavaInstant = when (udm) {
        is UDM.DateTime -> udm.instant
        is UDM.Date -> udm.date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        is UDM.Scalar -> {
            val value = udm.value
            if (value is String) {
                // Auto-parse ISO 8601 strings for convenience
                try {
                    val kotlinInstant = kotlinx.datetime.Instant.parse(value)
                    JavaInstant.ofEpochSecond(kotlinInstant.epochSeconds, kotlinInstant.nanosecondsOfSecond.toLong())
                } catch (e: Exception) {
                    throw FunctionArgumentException(
                        "Expected datetime value, but got string '$value'. " +
                        "Hint: Use parseDate() to parse non-ISO date strings, or use ISO 8601 format (e.g., '2025-10-15T14:30:00Z')."
                    )
                }
            } else {
                throw FunctionArgumentException(
                    "Expected datetime value, but got ${getTypeDescription(udm)}. " +
                    "Hint: Use parseDate() or now() to create datetime values."
                )
            }
        }
        else -> throw FunctionArgumentException(
            "Expected datetime value, but got ${getTypeDescription(udm)}. " +
            "Hint: Use parseDate() or now() to create datetime values."
        )
    }

    private fun UDM.asNumber(): Double = when (this) {
        is UDM.Scalar -> {
            val v = value
            when (v) {
                is Number -> v.toDouble()
                else -> throw FunctionArgumentException(
                    "Expected number value, but got ${getTypeDescription(this)}. " +
                    "Hint: Use toNumber() to convert strings to numbers."
                )
            }
        }
        else -> throw FunctionArgumentException(
            "Expected number value, but got ${getTypeDescription(this)}. " +
            "Hint: Use toNumber() to convert values to numbers."
        )
    }

    private fun getTypeDescription(udm: UDM): String {
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    is String -> "string"
                    is Number -> "number"
                    is Boolean -> "boolean"
                    null -> "null"
                    else -> value.javaClass.simpleName
                }
            }
            is UDM.Array -> "array"
            is UDM.Object -> "object"
            is UDM.Binary -> "binary"
            is UDM.DateTime -> "datetime"
            is UDM.Date -> "date"
            is UDM.LocalDateTime -> "localdatetime"
            is UDM.Time -> "time"
            is UDM.Lambda -> "lambda"
            else -> udm.javaClass.simpleName
        }
    }
}
