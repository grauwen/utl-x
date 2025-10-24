// stdlib/src/main/kotlin/org/apache/utlx/stdlib/math/ExtendedMathFunctions.kt
package org.apache.utlx.stdlib.math

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import java.text.DecimalFormat
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Extended numeric functions
 */
object ExtendedMathFunctions {
    
    @UTLXFunction(
        description = "Format number with pattern",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        parameters = [
            "number: Number value",
        "pattern: Pattern value"
        ],
        returns = "Result of the operation",
        example = "format-number(1234.56, \"#,##0.00\") => \"1,234.56\"",
        tags = ["math"],
        since = "1.0"
    )
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
            throw FunctionArgumentException(
                "Invalid number format pattern: $pattern. " +
                "Hint: Use patterns like '#,##0.00' for decimal formatting or '#,##0' for integers."
            )
        }
    }
    
    @UTLXFunction(
        description = "Parse integer from string",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        parameters = [
            "str: Str value"
        ],
        returns = "Result of the operation",
        example = "parse-int(\"42\") => 42",
        tags = ["math"],
        since = "1.0"
    )
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
            throw FunctionArgumentException(
                "Cannot parse integer from '$str'. " +
                "Hint: Ensure the string contains a valid integer value (e.g., '42', '-10')."
            )
        }
    }
    
    @UTLXFunction(
        description = "Parse float from string",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        parameters = [
            "str: Str value"
        ],
        returns = "Result of the operation",
        example = "parse-float(\"3.14\") => 3.14",
        tags = ["math"],
        since = "1.0"
    )
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
            throw FunctionArgumentException(
                "Cannot parse float from '$str'. " +
                "Hint: Ensure the string contains a valid decimal value (e.g., '3.14', '-2.5')."
            )
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

    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException(
            "Expected string value, but got ${getTypeDescription(this)}. " +
            "Hint: Use toString() to convert values to strings."
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
