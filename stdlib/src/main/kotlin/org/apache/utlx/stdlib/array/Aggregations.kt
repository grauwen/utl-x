// stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/Aggregations.kt
package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

/**
 * Array aggregation functions for UTL-X
 */
object Aggregations {
    
    /**
     * Sum of array elements
     * Usage: sum([1, 2, 3, 4]) => 10
     */
    fun sum(args: List<UDM>): UDM {
        requireArgs(args, 1, "sum")
        val array = args[0].asArray()
            ?: throw FunctionArgumentException(
                "sum requires an array as argument, but got ${getTypeDescription(args[0])}. " +
                "Hint: Use an array like [1,2,3] or ensure your value is an array."
            )
        
        val total = array.elements.sumOf { element ->
            element.asNumber()
        }
        
        return UDM.Scalar(total)
    }
    
    /**
     * Average of array elements
     * Usage: avg([1, 2, 3, 4]) => 2.5
     */
    fun avg(args: List<UDM>): UDM {
        requireArgs(args, 1, "avg")
        val array = args[0].asArray()
            ?: throw FunctionArgumentException(
                "avg requires an array as argument, but got ${getTypeDescription(args[0])}. " +
                "Hint: Use an array like [1,2,3] to calculate the average."
            )
        
        if (array.elements.isEmpty()) {
            return UDM.Scalar(null)
        }
        
        val total = array.elements.sumOf { element ->
            element.asNumber()
        }
        
        return UDM.Scalar(total / array.elements.size)
    }
    
    /**
     * Minimum value - supports both array and variadic arguments
     * Usage:
     *   min([3, 1, 4, 1, 5]) => 1
     *   min(3, 1) => 1
     *   min(5, 2, 8, 1) => 1
     */
    fun min(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "min requires at least 1 argument, got 0. " +
                "Hint: Usage is min([1,2,3]) or min(1, 2, 3) to find the minimum value."
            )
        }

        // If single argument that's an array, find min of array elements
        if (args.size == 1 && args[0] is UDM.Array) {
            val array = args[0] as UDM.Array
            if (array.elements.isEmpty()) {
                return UDM.Scalar(null)
            }
            val minValue = array.elements.minOf { it.asNumber() }
            return UDM.Scalar(minValue)
        }

        // Otherwise treat all arguments as numbers to compare
        val minValue = args.minOf { it.asNumber() }
        return UDM.Scalar(minValue)
    }

    /**
     * Maximum value - supports both array and variadic arguments
     * Usage:
     *   max([3, 1, 4, 1, 5]) => 5
     *   max(3, 1) => 3
     *   max(5, 2, 8, 1) => 8
     */
    fun max(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "max requires at least 1 argument, got 0. " +
                "Hint: Usage is max([1,2,3]) or max(1, 2, 3) to find the maximum value."
            )
        }

        // If single argument that's an array, find max of array elements
        if (args.size == 1 && args[0] is UDM.Array) {
            val array = args[0] as UDM.Array
            if (array.elements.isEmpty()) {
                return UDM.Scalar(null)
            }
            val maxValue = array.elements.maxOf { it.asNumber() }
            return UDM.Scalar(maxValue)
        }

        // Otherwise treat all arguments as numbers to compare
        val maxValue = args.maxOf { it.asNumber() }
        return UDM.Scalar(maxValue)
    }
    
    /**
     * Count of array elements
     * Usage: count([1, 2, 3, 4]) => 4
     */
    fun count(args: List<UDM>): UDM {
        requireArgs(args, 1, "count")
        val array = args[0].asArray()
            ?: throw FunctionArgumentException(
                "count requires an array as argument, but got ${getTypeDescription(args[0])}. " +
                "Hint: Use an array like [1,2,3] to count elements."
            )
        return UDM.Scalar(array.elements.size.toDouble())
    }
    
    // Helper functions
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}"
            )
        }
    }
    
    private fun UDM.asArray(): UDM.Array {
        return this as? UDM.Array
            ?: throw FunctionArgumentException(
                "Expected array value, but got ${getTypeDescription(this)}. " +
                "Hint: Ensure the value is an array."
            )
    }

    private fun UDM.asNumber(): Double {
        return when (this) {
            is UDM.Scalar -> {
                val v = value
                when (v) {
                    is Number -> v.toDouble()
                    is String -> v.toDoubleOrNull()
                        ?: throw FunctionArgumentException(
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
