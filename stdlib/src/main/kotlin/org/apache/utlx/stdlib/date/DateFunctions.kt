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
        description = "Performs parseDate operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "dateStr: Datestr value",
        "days: Days value"
        ],
        returns = "Result of the operation",
        example = "parseDate(...) => result",
        tags = ["date"],
        since = "1.0"
    )
    
    fun parseDate(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "parseDate")
        val dateStr = args[0].asString()
        val format = if (args.size > 1) args[1].asString() else "yyyy-MM-dd'T'HH:mm:ss'Z'"
        
        return try {
            val instant = Instant.parse(dateStr)
            UDM.DateTime(JavaInstant.ofEpochSecond(instant.epochSeconds, instant.nanosecondsOfSecond.toLong()))
        } catch (e: Exception) {
            throw FunctionArgumentException("Cannot parse date: $dateStr")
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
        description = "Performs addDays operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        parameters = [
            "dateUdm: Dateudm value",
        "days: Days value"
        ],
        returns = "Result of the operation",
        example = "addDays(...) => result",
        tags = ["date"],
        since = "1.0"
    )
    
    fun addDays(args: List<UDM>): UDM {
        requireArgs(args, 2, "addDays")
        val dateUdm = args[0]
        val date: JavaInstant = extractDateTime(dateUdm)
        val days = args[1].asNumber().toInt()
        
        val kotlinxDate = toKotlinxInstant(date)
        val result = kotlinxDate.plus(days, DateTimeUnit.DAY, TimeZone.UTC)
        return UDM.DateTime(toJavaInstant(result))
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
        description = "Performs diffDays operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Date",
        returns = "Result of the operation",
        example = "diffDays(...) => result",
        tags = ["date"],
        since = "1.0"
    )
    
    fun diffDays(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffDays")
        val date1 = extractDateTime(args[0])
        val date2 = extractDateTime(args[1])
        
        val kotlinxDate1 = toKotlinxInstant(date1)
        val kotlinxDate2 = toKotlinxInstant(date2)
        val diff = kotlinxDate2.minus(kotlinxDate1)
        val days = diff.inWholeDays.toDouble()
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
