// stdlib/src/main/kotlin/com/glomidco/utlx/stdlib/math/ExtendedMathFunctions.kt
package com.glomidco.utlx.stdlib.math

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.FunctionArgumentException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.glomidco.utlx.stdlib.annotations.UTLXFunction

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
        example = "formatNumber(1234.56, \"#,##0.00\") => \"1,234.56\"",
        tags = ["math"],
        since = "1.0"
    )
    /**
     * Format number with pattern
     * Usage: formatNumber(1234.56, "#,##0.00") => "1,234.56"
     */
    fun formatNumber(args: List<UDM>): UDM {
        requireArgs(args, 2, "formatNumber")
        val number = args[0].asNumber()
        val pattern = args[1].asString()
        
        return try {
            val formatter = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
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
        example = "parseInt(\"42\") => 42",
        tags = ["math"],
        since = "1.0"
    )
    /**
     * Parse integer from string
     * Usage: parseInt("42") => 42
     */
    fun parseInt(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseInt")
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
    
    // `parseFloat` lived here until B25 moved it to ConversionFunctions, where it sits with its
    // conversion siblings and delegates to `parseNumber`:
    //
    //     register("parseFloat", ConversionFunctions::parseFloat)     // Functions.kt
    //
    // The implementation removed from here did a plain `str.toDouble()`, which threw on grouping
    // and currency separators — `parseFloat("1,234.56")` and `parseFloat("$1,234.56")` now return
    // 1234.56 instead of failing. It also threw a FunctionArgumentException on unparseable input
    // where the current one returns null, so the replacement is fail-soft rather than fail-loud.
    //
    // The function stayed here, unregistered and unreachable, with an `@UTLXFunction` annotation
    // documenting it as `parse-float(...)` — a name that never resolved (B55). Removed 2026-11-12.
    // `parseInt` and `formatNumber` above are still registered from this class and are live.

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
