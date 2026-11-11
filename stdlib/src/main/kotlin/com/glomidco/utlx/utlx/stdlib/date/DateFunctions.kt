// stdlib/src/main/kotlin/com/glomidco/utlx/stdlib/date/DateFunctions.kt
package com.glomidco.utlx.stdlib.date

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.FunctionArgumentException
import kotlinx.datetime.*
import kotlinx.datetime.format.*
import java.time.Instant as JavaInstant
import com.glomidco.utlx.stdlib.annotations.UTLXFunction

object DateFunctions {
    
    // Helper functions to convert between java.time.Instant and kotlinx.datetime.Instant
    private fun toKotlinxInstant(javaInstant: JavaInstant): kotlinx.datetime.Instant {
        return kotlinx.datetime.Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
    }
    
    private fun toJavaInstant(kotlinxInstant: kotlinx.datetime.Instant): JavaInstant {
        return JavaInstant.ofEpochSecond(kotlinxInstant.epochSeconds, kotlinxInstant.nanosecondsOfSecond.toLong())
    }

    // ── F26: epoch conversion (GitHub issue #7) ──────────────────────────────────────────────────

    /**
     * The instant [value] denotes, for the epoch functions below.
     *
     * Accepts what a mapping actually has to hand: an ISO-8601 string, or any of the UDM date
     * types, so `toEpochMillis(now())` and `toEpochMillis($input.Timestamp)` both work rather than
     * only the string form.
     *
     * A value with **no offset** (a bare `2026-11-04T14:36:30`, a date, a local date-time) is
     * ambiguous — it names a wall-clock reading, not an instant — so it needs the optional zone
     * argument. Assuming UTC silently would be wrong by up to a day at the worst possible moment,
     * so it raises instead and says what to pass.
     */
    private fun instantOf(value: UDM, zoneId: String?, fn: String): JavaInstant {
        fun zone(): java.time.ZoneId = try {
            java.time.ZoneId.of(zoneId ?: throw FunctionArgumentException(
                "$fn: '${value.asString()}' has no UTC offset, so it does not identify an instant. " +
                "Pass a zone as the second argument, e.g. $fn(value, \"Europe/Amsterdam\"), " +
                "or use a value that carries an offset (…+01:00 or …Z)."))
        } catch (e: java.time.DateTimeException) {
            throw FunctionArgumentException("$fn: unknown time zone '$zoneId'")
        }

        return when (value) {
            is UDM.DateTime -> value.instant
            is UDM.LocalDateTime -> value.dateTime.atZone(zone()).toInstant()
            is UDM.Date -> value.date.atStartOfDay(zone()).toInstant()
            is UDM.Scalar -> {
                val text = value.value?.toString()?.trim()
                    ?: throw FunctionArgumentException("$fn: value is null")

                // An offset or a Z makes the text an instant outright.
                val withOffset = runCatching { java.time.OffsetDateTime.parse(text).toInstant() }.getOrNull()
                    ?: runCatching { JavaInstant.parse(text) }.getOrNull()
                if (withOffset != null) return withOffset

                // Otherwise it is a wall-clock reading. Resolve the SHAPE first and apply the zone
                // after, so the "pass a zone" message survives — calling zone() inside a
                // runCatching swallowed it and reported the generic parse failure instead.
                val local: (java.time.ZoneId) -> JavaInstant =
                    runCatching { java.time.LocalDateTime.parse(text) }.getOrNull()
                        ?.let { ldt -> { z: java.time.ZoneId -> ldt.atZone(z).toInstant() } }
                        ?: runCatching { java.time.LocalDate.parse(text) }.getOrNull()
                            ?.let { d -> { z: java.time.ZoneId -> d.atStartOfDay(z).toInstant() } }
                        ?: throw FunctionArgumentException(
                            "$fn: cannot read '$text' as a date-time. Expected ISO-8601, for example " +
                            "2026-11-04T14:36:30.438+01:00, 2026-11-04T13:36:30Z, or 2026-11-04T14:36:30 " +
                            "together with a zone argument.")
                local(zone())
            }
            else -> throw FunctionArgumentException(
                "$fn: expected a date-time or an ISO-8601 string, got ${value::class.simpleName}")
        }
    }

    @UTLXFunction(
        description = "Epoch milliseconds for an ISO-8601 date-time (or any UTL-X date value).",
        minArgs = 1,
        maxArgs = 2,
        category = "Date",
        parameters = [
            "value: ISO-8601 string with an offset (…+01:00 or …Z), or a date/date-time value",
            "zone: optional IANA zone, required only when the value carries no offset"
        ],
        returns = "Number - whole milliseconds since 1970-01-01T00:00:00Z",
        example = "toEpochMillis(\"2026-11-04T14:36:30.438+01:00\") => 1762263390438",
        tags = ["date", "epoch", "timestamp"],
        since = "1.0"
    )
    fun toEpochMillis(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "toEpochMillis")
        val zone = if (args.size > 1) args[1].asString() else null
        // A Long, deliberately: issue #7 asks for "a plain integer in JSON (not 1.79E15)", and a
        // Double would both lose precision past 2^53 and serialise in exponent form.
        return UDM.Scalar(instantOf(args[0], zone, "toEpochMillis").toEpochMilli())
    }

    @UTLXFunction(
        description = "Epoch microseconds for an ISO-8601 date-time (or any UTL-X date value).",
        minArgs = 1,
        maxArgs = 2,
        category = "Date",
        parameters = [
            "value: ISO-8601 string with an offset (…+01:00 or …Z), or a date/date-time value",
            "zone: optional IANA zone, required only when the value carries no offset"
        ],
        returns = "Number - whole microseconds since 1970-01-01T00:00:00Z",
        example = "toEpochMicros(\"2026-11-04T14:36:30.438+01:00\") => 1762263390438000",
        tags = ["date", "epoch", "timestamp"],
        since = "1.0"
    )
    fun toEpochMicros(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "toEpochMicros")
        val zone = if (args.size > 1) args[1].asString() else null
        val instant = instantOf(args[0], zone, "toEpochMicros")
        // Not millis * 1000: that would throw away sub-millisecond digits the source may carry.
        // Micros for year 1 .. 9999 fit a Long with room to spare (Long.MAX is ~year 294247).
        return UDM.Scalar(
            Math.addExact(
                Math.multiplyExact(instant.epochSecond, 1_000_000L),
                instant.nano / 1_000L
            )
        )
    }

    @UTLXFunction(
        description = "The date-time at a given number of epoch milliseconds — the inverse of toEpochMillis.",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = ["millis: milliseconds since 1970-01-01T00:00:00Z"],
        returns = "DateTime",
        example = "fromEpochMillis(1762263390438) => 2026-11-04T13:36:30.438Z",
        tags = ["date", "epoch", "timestamp"],
        since = "1.0"
    )
    fun fromEpochMillis(args: List<UDM>): UDM {
        requireArgs(args, 1, "fromEpochMillis")
        val n = (args[0] as? UDM.Scalar)?.value as? Number
            ?: throw FunctionArgumentException("fromEpochMillis: expected a number of milliseconds")
        return UDM.DateTime(JavaInstant.ofEpochMilli(n.toLong()))
    }

    @UTLXFunction(
        description = "The date-time at a given number of epoch microseconds — the inverse of toEpochMicros.",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = ["micros: microseconds since 1970-01-01T00:00:00Z"],
        returns = "DateTime",
        example = "fromEpochMicros(1762263390438000) => 2026-11-04T13:36:30.438Z",
        tags = ["date", "epoch", "timestamp"],
        since = "1.0"
    )
    fun fromEpochMicros(args: List<UDM>): UDM {
        requireArgs(args, 1, "fromEpochMicros")
        val n = (args[0] as? UDM.Scalar)?.value as? Number
            ?: throw FunctionArgumentException("fromEpochMicros: expected a number of microseconds")
        val micros = n.toLong()
        return UDM.DateTime(
            JavaInstant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L),
                                      Math.floorMod(micros, 1_000_000L) * 1_000L)
        )
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
        description = "Smart date parser with auto-detection and custom pattern support",
        minArgs = 1,
        maxArgs = 3,
        category = "Date",
        parameters = [
            "dateStr: Date string in ISO format or custom format",
            "pattern: Optional format pattern (e.g., 'dd/MM/yyyy', 'MMM dd, yyyy')",
            "locale: Optional BCP 47 locale tag (e.g., 'nl-NL', 'en-US') for month/day name parsing"
        ],
        returns = "UDM.Date for date-only, UDM.DateTime for timestamps, UDM.LocalDateTime for datetime without timezone",
        example = "parseDate(\"2020-03-15\") => Date, parseDate(\"15 maart 2020\", \"dd MMMM yyyy\", \"nl-NL\") => Date",
        tags = ["date"],
        since = "1.0"
    )

    fun parseDate(args: List<UDM>): UDM {
        requireArgs(args, 1..3, "parseDate")
        val dateStr = args[0].asString()
        val pattern = if (args.size > 1) args[1].asString() else null
        val locale = if (args.size > 2) args[2].asString() else "en-US"

        return try {
            // Auto-detect based on input format
            when {
                // Date-only: "2020-03-15", "2020/03/15"
                pattern == null && dateStr.matches(Regex("^\\d{4}[-/]\\d{2}[-/]\\d{2}$")) -> {
                    val localDate = java.time.LocalDate.parse(dateStr.replace('/', '-'))
                    UDM.Date(localDate)
                }

                // DateTime with time component: has 'T' or space separator
                pattern == null && (dateStr.contains('T') || dateStr.contains(' ')) -> {
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

                // Custom pattern specified
                pattern != null -> {
                    val javaLocale = java.util.Locale.forLanguageTag(locale)
                    val formatter = java.time.format.DateTimeFormatter.ofPattern(pattern, javaLocale)

                    // Determine what type to return based on the pattern
                    when {
                        // Pattern has time components (H, m, s, a)
                        pattern.matches(Regex(".*[HhmsaSn].*")) -> {
                            try {
                                // Try parsing as LocalDateTime
                                val localDateTime = java.time.LocalDateTime.parse(dateStr, formatter)
                                UDM.LocalDateTime(localDateTime)
                            } catch (e: Exception) {
                                // If that fails, try parsing as LocalTime
                                try {
                                    val time = java.time.LocalTime.parse(dateStr, formatter)
                                    UDM.Time(time)
                                } catch (e2: Exception) {
                                    throw FunctionArgumentException("Cannot parse time: $dateStr with pattern: $pattern")
                                }
                            }
                        }
                        // Date-only pattern
                        else -> {
                            val localDate = java.time.LocalDate.parse(dateStr, formatter)
                            UDM.Date(localDate)
                        }
                    }
                }

                // Unknown format
                else -> {
                    throw FunctionArgumentException("Cannot parse date: $dateStr - unknown format")
                }
            }
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            throw FunctionArgumentException("Cannot parse date: $dateStr (pattern: ${pattern ?: "ISO-8601"}, locale: $locale) - ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Convert string to date (alias for parseDate, simpler for single argument)",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "dateStr: Date string in ISO format (e.g., '2020-03-15', '2020-03-15T10:30:00Z')"
        ],
        returns = "UDM.Date for date-only, UDM.DateTime for timestamps",
        example = "toDate(\"2020-03-15\") => Date",
        tags = ["date", "conversion"],
        since = "1.0"
    )

    fun toDate(args: List<UDM>): UDM {
        requireArgs(args, 1, "toDate")
        return parseDate(args)
    }

    @UTLXFunction(
        description = "Format a date/time value with optional pattern and locale",
        minArgs = 1,
        maxArgs = 3,
        category = "Date",
        parameters = [
            "date: Date, DateTime, LocalDateTime, or Time value",
            "pattern: Optional format pattern (ISO, SHORT, MEDIUM, LONG, FULL, or custom pattern)",
            "locale: Optional BCP 47 locale tag (e.g., 'nl-NL', 'en-US')"
        ],
        returns = "Formatted date/time string",
        example = "formatDate(parseDate(\"2020-03-15\"), \"LONG\", \"nl-NL\") => \"15 maart 2020\"",
        tags = ["date"],
        since = "1.0"
    )

    fun formatDate(args: List<UDM>): UDM {
        requireArgs(args, 1..3, "formatDate")
        val dateValueRaw = args[0]
        val pattern = if (args.size > 1) args[1].asString() else "ISO"
        val locale = if (args.size > 2) args[2].asString() else "en-US"

        // Auto-parse ISO 8601 strings
        val dateValue = if (dateValueRaw is UDM.Scalar && dateValueRaw.value is String) {
            try {
                val instant = extractDateTime(dateValueRaw)
                UDM.DateTime(instant)
            } catch (e: Exception) {
                throw FunctionArgumentException(
                    "formatDate requires a Date, DateTime, LocalDateTime, or Time value, but got string '${dateValueRaw.value}'. " +
                    "Hint: Use parseDate() to parse non-ISO date strings, or use ISO 8601 format (e.g., '2025-10-15T14:30:00Z')."
                )
            }
        } else {
            dateValueRaw
        }

        val cldrProvider = JvmCldrProvider()

        // Determine the actual pattern to use
        val actualPattern = when (pattern.uppercase()) {
            "ISO" -> null  // Use default ISO format
            "SHORT", "MEDIUM", "LONG", "FULL" -> {
                cldrProvider.getDatePattern(locale, pattern)
            }
            else -> pattern  // Custom pattern
        }

        // Format based on the date type
        val formatted = when (dateValue) {
            is UDM.Date -> {
                if (actualPattern == null) {
                    dateValue.date.toString()  // ISO: "2020-03-15"
                } else {
                    val formatter = java.time.format.DateTimeFormatter.ofPattern(actualPattern, java.util.Locale.forLanguageTag(locale))
                    dateValue.date.format(formatter)
                }
            }
            is UDM.DateTime -> {
                if (actualPattern == null) {
                    dateValue.instant.toString()  // ISO: "2020-03-15T10:30:00Z"
                } else {
                    val formatter = java.time.format.DateTimeFormatter.ofPattern(actualPattern, java.util.Locale.forLanguageTag(locale))
                    val zdt = java.time.ZonedDateTime.ofInstant(dateValue.instant, java.time.ZoneId.of("UTC"))
                    zdt.format(formatter)
                }
            }
            is UDM.LocalDateTime -> {
                if (actualPattern == null) {
                    dateValue.dateTime.toString()  // ISO: "2020-03-15T10:30:00"
                } else {
                    val formatter = java.time.format.DateTimeFormatter.ofPattern(actualPattern, java.util.Locale.forLanguageTag(locale))
                    dateValue.dateTime.format(formatter)
                }
            }
            is UDM.Time -> {
                if (actualPattern == null) {
                    dateValue.time.toString()  // ISO: "14:30:00"
                } else {
                    val formatter = java.time.format.DateTimeFormatter.ofPattern(actualPattern, java.util.Locale.forLanguageTag(locale))
                    dateValue.time.format(formatter)
                }
            }
            else -> throw FunctionArgumentException(
                "formatDate requires a Date, DateTime, LocalDateTime, or Time value, but got ${dateValue.javaClass.simpleName}. " +
                "Hint: Use parseDate() to create a date from a string."
            )
        }

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

        // Handle ISO 8601 strings by parsing first
        val parsedDate = if (dateUdm is UDM.Scalar && dateUdm.value is String) {
            try {
                val instant = extractDateTime(dateUdm)
                UDM.DateTime(instant)
            } catch (e: Exception) {
                throw FunctionArgumentException(
                    "addDays requires a Date or DateTime value, but got string '${dateUdm.value}'. " +
                    "Hint: Use parseDate() to parse non-ISO date strings, or use ISO 8601 format (e.g., '2025-10-15T14:30:00Z')."
                )
            }
        } else {
            dateUdm
        }

        return when (parsedDate) {
            is UDM.Date -> {
                val result = parsedDate.date.plusDays(days)
                UDM.Date(result)
            }
            is UDM.DateTime -> {
                val kotlinxDate = toKotlinxInstant(parsedDate.instant)
                val result = kotlinxDate.plus(days.toInt(), DateTimeUnit.DAY, TimeZone.UTC)
                UDM.DateTime(toJavaInstant(result))
            }
            is UDM.LocalDateTime -> {
                val result = parsedDate.dateTime.plusDays(days)
                UDM.LocalDateTime(result)
            }
            else -> throw FunctionArgumentException(
                "addDays requires a Date or DateTime value, but got ${parsedDate.javaClass.simpleName}. " +
                "Hint: Use parseDate() or currentDate() to create a date value."
            )
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

        // Helper function to convert any date type to Instant for comparison
        fun toInstant(date: UDM): kotlinx.datetime.Instant {
            return when (date) {
                is UDM.Date -> {
                    // Convert LocalDate to Instant at start of day UTC
                    val localDateTime = date.date.atStartOfDay()
                    val zonedDateTime = localDateTime.atZone(java.time.ZoneId.of("UTC"))
                    toKotlinxInstant(zonedDateTime.toInstant())
                }
                is UDM.DateTime -> toKotlinxInstant(date.instant)
                is UDM.LocalDateTime -> {
                    // Convert LocalDateTime to Instant assuming UTC
                    val zonedDateTime = date.dateTime.atZone(java.time.ZoneId.of("UTC"))
                    toKotlinxInstant(zonedDateTime.toInstant())
                }
                is UDM.Scalar -> {
                    // Auto-parse ISO 8601 strings
                    val value = date.value
                    if (value is String) {
                        try {
                            kotlinx.datetime.Instant.parse(value)
                        } catch (e: Exception) {
                            throw FunctionArgumentException(
                                "diffDays requires Date, DateTime, or LocalDateTime values, but got string '$value'. " +
                                "Hint: Use parseDate() to parse non-ISO date strings, or use ISO 8601 format (e.g., '2025-10-15T14:30:00Z')."
                            )
                        }
                    } else {
                        throw FunctionArgumentException(
                            "diffDays requires Date, DateTime, or LocalDateTime values, but got ${getTypeDescription(date)}. " +
                            "Hint: Use parseDate() to create date values."
                        )
                    }
                }
                else -> throw FunctionArgumentException(
                    "diffDays requires Date, DateTime, or LocalDateTime values, but got ${getTypeDescription(date)}. " +
                    "Hint: Use parseDate() to create date values."
                )
            }
        }

        val instant1 = toInstant(args[0])
        val instant2 = toInstant(args[1])
        val days = instant2.minus(instant1).inWholeDays.toDouble()

        return UDM.Scalar(days)
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
    }

    private fun requireArgs(args: List<UDM>, range: IntRange, functionName: String) {
        if (args.size !in range) {
            throw FunctionArgumentException(
                "$functionName expects ${range.first}..${range.last} arguments, got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
    }

    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException(
            "Expected string value, but got ${getTypeDescription(this)}. " +
            "Hint: Use toString() to convert values to strings."
        )
    }

    private fun UDM.asNumber(): Double = when (this) {
        is UDM.Scalar -> {
            val v = value
            when (v) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: throw FunctionArgumentException(
                    "Cannot convert '$v' to number. " +
                    "Hint: Ensure the string contains a valid numeric value."
                )
                else -> throw FunctionArgumentException(
                    "Expected number value, but got ${getTypeDescription(this)}. " +
                    "Hint: Use toNumber() to convert values to numbers."
                )
            }
        }
        else -> throw FunctionArgumentException(
            "Expected number value, but got ${getTypeDescription(this)}. " +
            "Hint: Use toNumber() to convert values to numbers."
        )
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
