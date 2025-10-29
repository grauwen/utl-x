// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/ExtendedDateFunctions.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlinx.datetime.*
import java.time.Instant as JavaInstant
import org.apache.utlx.stdlib.annotations.UTLXFunction

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
    
    @UTLXFunction(
        description = "Extract day component",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        returns = "Result of the operation",
        example = "day(now()) => 14",
        tags = ["date"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Extract month component (1-12)",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        returns = "Result of the operation",
        example = "month(now()) => 10",
        tags = ["date"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Extract year component",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        returns = "Result of the operation",
        example = "year(now()) => 2025",
        tags = ["date"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Extract hours component (0-23)",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        returns = "Result of the operation",
        example = "hours(now()) => 14",
        tags = ["date"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Extract minutes component (0-59)",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "pattern: Pattern value",
        "dateStr: Datestr value"
        ],
        returns = "Result of the operation",
        example = "minutes(now()) => 30",
        tags = ["date"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Extract seconds component (0-59)",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "pattern: Pattern value",
        "dateStr: Datestr value"
        ],
        returns = "Result of the operation",
        example = "seconds(now()) => 45",
        tags = ["date"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Compare two dates",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "pattern: Pattern value",
        "dateStr: Datestr value"
        ],
        returns = ": -1 if date1 < date2, 0 if equal, 1 if date1 > date2",
        example = "compare-dates(date1, date2)",
        notes = "Returns: -1 if date1 < date2, 0 if equal, 1 if date1 > date2",
        tags = ["date"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Validate date string (ISO 8601 format)",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "dateStr: Date string to validate"
        ],
        returns = "Boolean indicating if the string is a valid ISO 8601 date",
        example = "validate-date(\"2025-10-14T00:00:00Z\") => true",
        tags = ["date"],
        since = "1.0"
    )
    /**
     * Validate date string against a pattern or ISO 8601 format
     * Usage: validate-date("yyyy-MM-dd", "2025-10-14T00:00:00Z") => true
     * Usage: validate-date("ISO8601", "2025-10-14T00:00:00Z") => true
     */
    fun validateDate(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw FunctionArgumentException(
                "validate-date expects 2 arguments, got ${args.size}. " +
                "Hint: Use validate-date(pattern, date)"
            )
        }

        // Validate that first argument is a string pattern
        val patternArg = args[0]
        if (patternArg !is UDM.Scalar || patternArg.value !is String) {
            throw FunctionArgumentException(
                "validate-date expects pattern to be a string, got ${getTypeDescription(patternArg)}. " +
                "Hint: Use a date pattern like 'yyyy-MM-dd' or 'ISO8601'"
            )
        }

        // Validate that second argument is a string date
        val dateArg = args[1]
        if (dateArg !is UDM.Scalar) {
            throw FunctionArgumentException(
                "validate-date expects date to be a string, got ${getTypeDescription(dateArg)}. " +
                "Hint: Provide the date as a string"
            )
        }

        // Handle null date value - return false instead of throwing exception
        if (dateArg.value == null) {
            return UDM.Scalar(false)
        }

        if (dateArg.value !is String) {
            throw FunctionArgumentException(
                "validate-date expects date to be a string, got ${getTypeDescription(dateArg)}. " +
                "Hint: Provide the date as a string"
            )
        }

        val pattern = patternArg.value as String
        val dateStr = dateArg.value as String

        return try {
            // Special handling for ISO8601 pattern
            if (pattern.equals("ISO8601", ignoreCase = true)) {
                Instant.parse(dateStr)
                return UDM.Scalar(true)
            }

            // Parse the date string to extract date part
            val datePart = when {
                dateStr.contains('T') -> {
                    // ISO format with time - extract just the date part for pattern matching
                    dateStr.substringBefore('T')
                }
                else -> dateStr
            }

            // Try to parse with the given pattern
            val formatter = java.time.format.DateTimeFormatter.ofPattern(pattern)
            java.time.LocalDate.parse(datePart, formatter)
            UDM.Scalar(true)
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
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
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value, but got ${getTypeDescription(this)}. Hint: Use toString() to convert values to strings.")
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
