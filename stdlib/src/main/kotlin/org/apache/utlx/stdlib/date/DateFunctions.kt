// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/DateFunctions.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlinx.datetime.*
import kotlinx.datetime.format.*
import java.time.Instant as JavaInstant
import org.apache.utlx.stdlib.annotations.UTLXFunction

object DateFunctions {
    
    // Helper functions to convert between java.time.Instant and kotlinx.datetime.Instant
    private fun toKotlinxInstant(javaInstant: JavaInstant): kotlinx.datetime.Instant {
        return kotlinx.datetime.Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
    }
    
    private fun toJavaInstant(kotlinxInstant: kotlinx.datetime.Instant): JavaInstant {
        return JavaInstant.ofEpochSecond(kotlinxInstant.epochSeconds, kotlinxInstant.nanosecondsOfSecond.toLong())
    }

    @UTLXFunction(
        description = "Performs now operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "dateStr: Datestr value",
        "days: Days value"
        ],
        returns = "Result of the operation",
        example = "now(...) => result",
        tags = ["date"],
        since = "1.0"
    )
    
    fun now(args: List<UDM>): UDM {
        requireArgs(args, 0, "now")
        val now = Clock.System.now()
        return UDM.DateTime(JavaInstant.ofEpochSecond(now.epochSeconds, now.nanosecondsOfSecond.toLong()))
    }

    @UTLXFunction(
        description = "Smart date parser - auto-detects Date vs DateTime based on input format",
        minArgs = 1,
        maxArgs = 2,
        category = "Date",
        parameters = [
            "dateStr: Date string in ISO format or custom format",
            "format: Optional custom format pattern"
        ],
        returns = "UDM.Date for date-only strings, UDM.DateTime for timestamps",
        example = "parseDate(\"2020-03-15\") => Date, parseDate(\"2020-03-15T10:30:00Z\") => DateTime",
        tags = ["date"],
        since = "1.0"
    )

    fun parseDate(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "parseDate")
        val dateStr = args[0].asString()
        val format = if (args.size > 1) args[1].asString() else null

        return try {
            // Auto-detect based on input format
            when {
                // Date-only: "2020-03-15", "2020/03/15"
                format == null && dateStr.matches(Regex("^\\d{4}[-/]\\d{2}[-/]\\d{2}$")) -> {
                    val localDate = java.time.LocalDate.parse(dateStr.replace('/', '-'))
                    UDM.Date(localDate)
                }

                // DateTime with time component: has 'T' or space separator
                format == null && (dateStr.contains('T') || dateStr.contains(' ')) -> {
                    try {
                        // Try parsing as ISO instant (with timezone)
                        val instant = Instant.parse(dateStr)
                        UDM.DateTime(JavaInstant.ofEpochSecond(instant.epochSeconds, instant.nanosecondsOfSecond.toLong()))
                    } catch (e: Exception) {
                        // If that fails, try parsing as LocalDateTime (no timezone)
                        val localDateTime = java.time.LocalDateTime.parse(dateStr.replace(' ', 'T'))
                        UDM.LocalDateTime(localDateTime)
                    }
                }

                // Custom format specified
                format != null -> {
                    // TODO: Implement custom format parsing using java.time.format.DateTimeFormatter
                    throw FunctionArgumentException("Custom date format parsing not yet implemented")
                }

                // Unknown format
                else -> {
                    throw FunctionArgumentException("Cannot parse date: $dateStr - unknown format")
                }
            }
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            throw FunctionArgumentException("Cannot parse date: $dateStr (format: ${format ?: "ISO-8601"}) - ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Performs formatDate operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "dateUdm: Dateudm value",
        "days: Days value"
        ],
        returns = "Result of the operation",
        example = "formatDate(...) => result",
        tags = ["date"],
        since = "1.0"
    )
    
    fun formatDate(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "formatDate")
        val date = extractDateTime(args[0])
        val format = if (args.size > 1) args[1].asString() else "yyyy-MM-dd'T'HH:mm:ss'Z'"
        
        val formatted = date.toString()
        return UDM.Scalar(formatted)
    }

    @UTLXFunction(
        description = "Add days to a date or datetime",
        minArgs = 2,
        maxArgs = 2,
        category = "Date",
        parameters = [
            "date: Date or DateTime value",
            "days: Number of days to add (can be negative)"
        ],
        returns = "Date or DateTime with days added (preserves input type)",
        example = "addDays(parseDate(\"2020-03-15\"), 7) => \"2020-03-22\"",
        tags = ["date"],
        since = "1.0"
    )

    fun addDays(args: List<UDM>): UDM {
        requireArgs(args, 2, "addDays")
        val dateUdm = args[0]
        val days = args[1].asNumber().toLong()

        return when (dateUdm) {
            is UDM.Date -> {
                val result = dateUdm.date.plusDays(days)
                UDM.Date(result)
            }
            is UDM.DateTime -> {
                val kotlinxDate = toKotlinxInstant(dateUdm.instant)
                val result = kotlinxDate.plus(days.toInt(), DateTimeUnit.DAY, TimeZone.UTC)
                UDM.DateTime(toJavaInstant(result))
            }
            is UDM.LocalDateTime -> {
                val result = dateUdm.dateTime.plusDays(days)
                UDM.LocalDateTime(result)
            }
            else -> throw FunctionArgumentException("addDays requires a Date or DateTime value")
        }
    }

    @UTLXFunction(
        description = "Performs addHours operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "hours: Hours value"
        ],
        returns = "Result of the operation",
        example = "addHours(...) => result",
        tags = ["date"],
        since = "1.0"
    )
    
    fun addHours(args: List<UDM>): UDM {
        requireArgs(args, 2, "addHours")
        val date = extractDateTime(args[0])
        val hours = args[1].asNumber().toInt()
        
        val kotlinxDate = toKotlinxInstant(date)
        val result = kotlinxDate.plus(hours, DateTimeUnit.HOUR, TimeZone.UTC)
        return UDM.DateTime(toJavaInstant(result))
    }

    @UTLXFunction(
        description = "Calculate difference in days between two dates",
        minArgs = 2,
        maxArgs = 2,
        category = "Date",
        parameters = [
            "date1: First date or datetime",
            "date2: Second date or datetime"
        ],
        returns = "Number of days between date1 and date2 (date2 - date1)",
        example = "diffDays(parseDate(\"2020-01-01\"), parseDate(\"2020-01-31\")) => 30",
        tags = ["date"],
        since = "1.0"
    )

    fun diffDays(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffDays")

        val days = when {
            args[0] is UDM.Date && args[1] is UDM.Date -> {
                val date1 = (args[0] as UDM.Date).date
                val date2 = (args[1] as UDM.Date).date
                java.time.temporal.ChronoUnit.DAYS.between(date1, date2).toDouble()
            }
            args[0] is UDM.DateTime && args[1] is UDM.DateTime -> {
                val date1 = toKotlinxInstant((args[0] as UDM.DateTime).instant)
                val date2 = toKotlinxInstant((args[1] as UDM.DateTime).instant)
                date2.minus(date1).inWholeDays.toDouble()
            }
            args[0] is UDM.LocalDateTime && args[1] is UDM.LocalDateTime -> {
                val date1 = (args[0] as UDM.LocalDateTime).dateTime
                val date2 = (args[1] as UDM.LocalDateTime).dateTime
                java.time.temporal.ChronoUnit.DAYS.between(date1, date2).toDouble()
            }
            else -> throw FunctionArgumentException("diffDays requires two dates of the same type (Date, DateTime, or LocalDateTime)")
        }

        return UDM.Scalar(days)
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
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value")
    }
    
    private fun UDM.asNumber(): Double = when (this) {
        is UDM.Scalar -> {
            val v = value
            when (v) {
                is Number -> v.toDouble()
                else -> throw FunctionArgumentException("Expected number value")
            }
        }
        else -> throw FunctionArgumentException("Expected number value")
    }
    
    private fun extractDateTime(udm: UDM): JavaInstant = when (udm) {
        is UDM.DateTime -> udm.instant
        else -> throw FunctionArgumentException("Expected datetime value")
    }
}
