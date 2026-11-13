// stdlib/src/main/kotlin/com/glomidco/utlx/stdlib/math/MathFunctions.kt
package com.glomidco.utlx.stdlib.math

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.FunctionArgumentException
import kotlin.math.*
import com.glomidco.utlx.stdlib.annotations.UTLXFunction

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

    // ── B38: non-finite predicates ──
    //
    // JSON has no literal for Infinity, -Infinity or NaN, so the serialiser refuses to emit one
    // rather than produce output a strict parser rejects. These exist so that refusal is
    // something a transformation can work with instead of a wall: a mapping that might overflow
    // (`1e308 * 10`), take a root of a negative (`sqrt(-1)`) or raise a large power can test the
    // result and substitute.
    //
    //     { rate: if (isFinite(computed)) computed else null }
    //
    // Only Double and Float can be non-finite; every exact type UTL-X carries (BigDecimal, Long)
    // is finite by construction, so the integral cases answer without converting.

    @UTLXFunction(
        description = "True when a number is NaN (not a number)",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        parameters = [
            "num: The number to test"
        ],
        returns = "true if the value is NaN",
        example = "isNaN(sqrt(-1)) => true",
        notes = "JSON cannot represent NaN, so test before emitting a computed value.",
        tags = ["math", "predicate"],
        since = "1.4"
    )
    fun isNaN(args: List<UDM>): UDM {
        requireArgs(args, 1, "isNaN")
        return UDM.Scalar(nonFiniteState(args[0]) == NonFinite.NAN)
    }

    @UTLXFunction(
        description = "True when a number is positive or negative infinity",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        parameters = [
            "num: The number to test"
        ],
        returns = "true if the value is infinite",
        example = "isInfinite(1.0e308 * 10) => true",
        notes = "JSON cannot represent Infinity, so test before emitting a computed value.",
        tags = ["math", "predicate"],
        since = "1.4"
    )
    fun isInfinite(args: List<UDM>): UDM {
        requireArgs(args, 1, "isInfinite")
        return UDM.Scalar(nonFiniteState(args[0]) == NonFinite.INFINITE)
    }

    @UTLXFunction(
        description = "True when a number is finite — neither infinite nor NaN",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        parameters = [
            "num: The number to test"
        ],
        returns = "true if the value is finite",
        example = "isFinite(1.5) => true",
        notes = "The one to reach for when guarding output: `if (isFinite(x)) x else null`.",
        tags = ["math", "predicate"],
        since = "1.4"
    )
    fun isFinite(args: List<UDM>): UDM {
        requireArgs(args, 1, "isFinite")
        return UDM.Scalar(nonFiniteState(args[0]) == NonFinite.FINITE)
    }

    private enum class NonFinite { FINITE, INFINITE, NAN }

    /**
     * Classify without going through `asNumber()`, which would convert an exact type to Double
     * and could itself overflow — turning a finite BigDecimal into an "infinite" answer.
     */
    private fun nonFiniteState(value: UDM): NonFinite {
        val n = (value as? UDM.Scalar)?.value as? Number
            ?: throw FunctionArgumentException(
                "Expected a number, got ${getTypeDescription(value)}. " +
                "Hint: these predicates test numeric values only."
            )
        return when (n) {
            is Double -> when {
                n.isNaN() -> NonFinite.NAN
                n.isInfinite() -> NonFinite.INFINITE
                else -> NonFinite.FINITE
            }
            is Float -> when {
                n.isNaN() -> NonFinite.NAN
                n.isInfinite() -> NonFinite.INFINITE
                else -> NonFinite.FINITE
            }
            else -> NonFinite.FINITE   // BigDecimal, Long, Int … finite by construction
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
