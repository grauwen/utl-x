// stdlib/src/main/kotlin/org/apache/utlx/stdlib/math/MathFunctions.kt
package org.apache.utlx.stdlib.math

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlin.math.*

object MathFunctions {
    
    fun abs(args: List<UDM>): UDM {
        requireArgs(args, 1, "abs")
        val num = args[0].asNumber()
        return UDM.Scalar(abs(num))
    }
    
    fun round(args: List<UDM>): UDM {
        requireArgs(args, 1, "round")
        val num = args[0].asNumber()
        return UDM.Scalar(round(num))
    }
    
    fun ceil(args: List<UDM>): UDM {
        requireArgs(args, 1, "ceil")
        val num = args[0].asNumber()
        return UDM.Scalar(ceil(num))
    }
    
    fun floor(args: List<UDM>): UDM {
        requireArgs(args, 1, "floor")
        val num = args[0].asNumber()
        return UDM.Scalar(floor(num))
    }
    
    fun pow(args: List<UDM>): UDM {
        requireArgs(args, 2, "pow")
        val base = args[0].asNumber()
        val exp = args[1].asNumber()
        return UDM.Scalar(base.pow(exp))
    }
    
    fun sqrt(args: List<UDM>): UDM {
        requireArgs(args, 1, "sqrt")
        val num = args[0].asNumber()
        return UDM.Scalar(sqrt(num))
    }
    
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
