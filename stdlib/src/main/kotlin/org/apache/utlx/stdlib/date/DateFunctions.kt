// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/DateFunctions.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlinx.datetime.*
import kotlinx.datetime.format.*

object DateFunctions {
    
    fun now(args: List<UDM>): UDM {
        requireArgs(args, 0, "now")
        val now = Clock.System.now()
        return UDM.DateTime(now)
    }
    
    fun parseDate(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "parseDate")
        val dateStr = args[0].asString()
        val format = if (args.size > 1) args[1].asString() else "yyyy-MM-dd'T'HH:mm:ss'Z'"
        
        return try {
            val instant = Instant.parse(dateStr)
            UDM.DateTime(instant)
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
        
        val result = date.plus(days, DateTimeUnit.DAY, TimeZone.UTC)
        return UDM.DateTime(result)
    }
    
    fun addHours(args: List<UDM>): UDM {
        requireArgs(args, 2, "addHours")
        val date = args[0].asDateTime()
        val hours = args[1].asNumber().toInt()
        
        val result = date.plus(hours, DateTimeUnit.HOUR, TimeZone.UTC)
        return UDM.DateTime(result)
    }
    
    fun diffDays(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffDays")
        val date1 = args[0].asDateTime()
        val date2 = args[1].asDateTime()
        
        val diff = date2.minus(date1, TimeZone.UTC)
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
        is UDM.Scalar -> when (value) {
            is Number -> value.toDouble()
            else -> throw FunctionArgumentException("Expected number value")
        }
        else -> throw FunctionArgumentException("Expected number value")
    }
    
    private fun UDM.asDateTime(): Instant = when (this) {
        is UDM.DateTime -> instant
        else -> throw FunctionArgumentException("Expected datetime value")
    }
}
