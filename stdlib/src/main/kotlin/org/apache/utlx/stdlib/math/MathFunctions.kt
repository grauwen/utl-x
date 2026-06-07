// stdlib/src/main/kotlin/org/apache/utlx/stdlib/math/MathFunctions.kt
package org.apache.utlx.stdlib.math

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlin.math.*
import org.apache.utlx.stdlib.annotations.UTLXFunction

object MathFunctions {

    @UTLXFunction(
        description = "Performs abs operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        parameters = [
            "num: Num value",
        "exp: Exp value"
        ],
        returns = "Result of the operation",
        example = "abs(...) => result",
        tags = ["math"],
        since = "1.0"
    )
    
    fun abs(args: List<UDM>): UDM {
        requireArgs(args, 1, "abs")
        val num = args[0].asNumber()
        return UDM.Scalar(abs(num))
    }

    @UTLXFunction(
        description = "Rounds to the nearest integer, or to N decimal places when a second argument is given",
        minArgs = 1,
        maxArgs = 2,
        category = "Math",
        parameters = [
            "num: the number to round",
            "decimals: (optional) number of decimal places; omitted or <= 0 rounds to an integer"
        ],
        returns = "Result of the operation",
        example = "round(3.14159, 2) => 3.14",
        tags = ["math"],
        since = "1.0"
    )
    
    fun round(args: List<UDM>): UDM {
        // B25: optional second argument = number of decimal places. round(x) -> nearest integer;
        // round(x, n) -> rounded to n decimals. Previously round() was strict 1-arg, so any
        // round(x, n) call threw on the native/JVM path.
        if (args.size !in 1..2) {
            throw FunctionArgumentException(
                "round expects 1 or 2 argument(s), got ${args.size}. " +
                "Usage: round(x) or round(x, decimals)."
            )
        }
        val num = args[0].asNumber()
        if (args.size == 1) {
            return UDM.Scalar(round(num))
        }
        val places = args[1].asNumber().toInt()
        if (places <= 0) {
            return UDM.Scalar(round(num))
        }
        val factor = 10.0.pow(places)
        return UDM.Scalar(round(num * factor) / factor)
    }

    @UTLXFunction(
        description = "Performs ceil operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        parameters = [
            "num: Num value",
        "exp: Exp value"
        ],
        returns = "Result of the operation",
        example = "ceil(...) => result",
        tags = ["math"],
        since = "1.0"
    )
    
    fun ceil(args: List<UDM>): UDM {
        requireArgs(args, 1, "ceil")
        val num = args[0].asNumber()
        return UDM.Scalar(ceil(num))
    }

    @UTLXFunction(
        description = "Performs floor operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        parameters = [
            "num: Num value",
        "exp: Exp value"
        ],
        returns = "Result of the operation",
        example = "floor(...) => result",
        tags = ["math"],
        since = "1.0"
    )
    
    fun floor(args: List<UDM>): UDM {
        requireArgs(args, 1, "floor")
        val num = args[0].asNumber()
        return UDM.Scalar(floor(num))
    }

    @UTLXFunction(
        description = "Performs pow operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        parameters = [
            "base: Base value",
        "exp: Exp value"
        ],
        returns = "Result of the operation",
        example = "pow(...) => result",
        tags = ["math"],
        since = "1.0"
    )
    
    fun pow(args: List<UDM>): UDM {
        requireArgs(args, 2, "pow")
        val base = args[0].asNumber()
        val exp = args[1].asNumber()
        return UDM.Scalar(base.pow(exp))
    }

    @UTLXFunction(
        description = "Performs sqrt operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        parameters = [
            "num: Num value"
        ],
        returns = "Result of the operation",
        example = "sqrt(...) => result",
        tags = ["math"],
        since = "1.0"
    )
    
    fun sqrt(args: List<UDM>): UDM {
        requireArgs(args, 1, "sqrt")
        val num = args[0].asNumber()
        return UDM.Scalar(sqrt(num))
    }

    @UTLXFunction(
        description = "Performs random operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        returns = "Result of the operation",
        example = "random(...) => result",
        tags = ["math"],
        since = "1.0"
    )
    
    fun random(args: List<UDM>): UDM {
        requireArgs(args, 0, "random")
        return UDM.Scalar(Math.random())
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
