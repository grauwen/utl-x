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
        description = "Performs round operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Math",
        parameters = [
            "num: Num value",
        "exp: Exp value"
        ],
        returns = "Result of the operation",
        example = "round(...) => result",
        tags = ["math"],
        since = "1.0"
    )
    
    fun round(args: List<UDM>): UDM {
        requireArgs(args, 1, "round")
        val num = args[0].asNumber()
        return UDM.Scalar(round(num))
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
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asNumber(): Double = when (this) {
        is UDM.Scalar -> {
            val v = value
            when (v) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: throw FunctionArgumentException("Cannot convert '$v' to number")
                else -> throw FunctionArgumentException("Expected number value")
            }
        }
        else -> throw FunctionArgumentException("Expected number value")
    }
}
