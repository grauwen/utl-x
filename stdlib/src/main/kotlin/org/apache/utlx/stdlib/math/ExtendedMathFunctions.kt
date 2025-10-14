// stdlib/src/main/kotlin/org/apache/utlx/stdlib/math/ExtendedMathFunctions.kt
package org.apache.utlx.stdlib.math

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import java.text.DecimalFormat

/**
 * Extended numeric functions
 */
object ExtendedMathFunctions {
    
    /**
     * Format number with pattern
     * Usage: format-number(1234.56, "#,##0.00") => "1,234.56"
     */
    fun formatNumber(args: List<UDM>): UDM {
        requireArgs(args, 2, "format-number")
        val number = args[0].asNumber()
        val pattern = args[1].asString()
        
        return try {
            val formatter = DecimalFormat(pattern)
            UDM.Scalar(formatter.format(number))
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid number format pattern: $pattern")
        }
    }
    
    /**
     * Parse integer from string
     * Usage: parse-int("42") => 42
     */
    fun parseInt(args: List<UDM>): UDM {
        requireArgs(args, 1, "parse-int")
        val str = args[0].asString()
        return try {
            UDM.Scalar(str.toInt().toDouble())
        } catch (e: NumberFormatException) {
            throw FunctionArgumentException("Cannot parse integer: $str")
        }
    }
    
    /**
     * Parse float from string
     * Usage: parse-float("3.14") => 3.14
     */
    fun parseFloat(args: List<UDM>): UDM {
        requireArgs(args, 1, "parse-float")
        val str = args[0].asString()
        return try {
            UDM.Scalar(str.toDouble())
        } catch (e: NumberFormatException) {
            throw FunctionArgumentException("Cannot parse float: $str")
        }
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asNumber(): Double = when (this) {
        is UDM.Scalar -> when (value) {
            is Number -> value.toDouble()
            else -> throw FunctionArgumentException("Expected number value")
        }
        else -> throw FunctionArgumentException("Expected number value")
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value")
    }
}
