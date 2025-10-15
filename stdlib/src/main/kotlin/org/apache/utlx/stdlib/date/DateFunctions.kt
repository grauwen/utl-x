// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/DateFunctions.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlinx.datetime.*
import kotlinx.datetime.format.*
import java.time.Instant as JavaInstant

object DateFunctions {
    
    // Helper functions to convert between java.time.Instant and kotlinx.datetime.Instant
    private fun JavaInstant.toKotlinxInstant(): Instant {
        return Instant.fromEpochSeconds(this.epochSecond, this.nano)
    }
    
    private fun Instant.toJavaInstant(): JavaInstant {
        return JavaInstant.ofEpochSecond(this.epochSeconds, this.nanosecondsOfSecond.toLong())
    }
    
    fun now(args: List<UDM>): UDM {
        requireArgs(args, 0, "now")
        val now = Clock.System.now()
        return UDM.DateTime(JavaInstant.ofEpochSecond(now.epochSeconds, now.nanosecondsOfSecond.toLong()))
    }
    
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
    
    fun formatDate(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "formatDate")
        val date = args[0].asDateTime()
        val format = if (args.size > 1) args[1].asString() else "yyyy-MM-dd'T'HH:mm:ss'Z'"
        
        val formatted = date.toString()
        return UDM.Scalar(formatted)
    }
    
    fun addDays(args: List<UDM>): UDM {
        requireArgs(args, 2, "addDays")
        val date = args[0].asDateTime()
        val days = args[1].asNumber().toInt()
        
        val kotlinxDate = date.toKotlinxInstant()
        val result = kotlinxDate.plus(days, DateTimeUnit.DAY, TimeZone.UTC)
        return UDM.DateTime(result.toJavaInstant())
    }
    
    fun addHours(args: List<UDM>): UDM {
        requireArgs(args, 2, "addHours")
        val date = args[0].asDateTime()
        val hours = args[1].asNumber().toInt()
        
        val kotlinxDate = date.toKotlinxInstant()
        val result = kotlinxDate.plus(hours, DateTimeUnit.HOUR, TimeZone.UTC)
        return UDM.DateTime(result.toJavaInstant())
    }
    
    fun diffDays(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffDays")
        val date1 = args[0].asDateTime()
        val date2 = args[1].asDateTime()
        
        val kotlinxDate1 = date1.toKotlinxInstant()
        val kotlinxDate2 = date2.toKotlinxInstant()
        val diff = kotlinxDate2.minus(kotlinxDate1, TimeZone.UTC)
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
    
    private fun UDM.asDateTime(): JavaInstant = when (this) {
        is UDM.DateTime -> instant
        else -> throw FunctionArgumentException("Expected datetime value")
    }
}
