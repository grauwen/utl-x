// stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/Aggregations.kt
package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Array aggregation functions for UTL-X
 */
object Aggregations {

    @UTLXFunction(
        description = "Calculate the sum of all numeric elements in an array",
        category = "Array",
        parameters = ["array: Array<Number>"],
        returns = "Number - The sum of all elements",
        example = "sum([1, 2, 3, 4]) => 10"
    )
    fun sum(args: List<UDM>): UDM {
        requireArgs(args, 1, "sum")
        val array = args[0].asArray()
            ?: throw FunctionArgumentException(
                "sum requires an array as argument, but got ${getTypeDescription(args[0])}. " +
                "Hint: Use an array like [1,2,3] or ensure your value is an array."
            )
        
        val total = array.elements.sumOf { element ->
            element.asNumberStrict()
        }
        
        return UDM.Scalar(total)
    }

    @UTLXFunction(
        description = "Calculate the average (mean) of all numeric elements in an array",
        category = "Array",
        parameters = ["array: Array<Number>"],
        returns = "Number - The average of all elements, or null if array is empty",
        example = "avg([1, 2, 3, 4]) => 2.5"
    )
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
            element.asNumberStrict()
        }
        
        return UDM.Scalar(total / array.elements.size)
    }

    @UTLXFunction(
        description = "Find the minimum value from an array or multiple arguments",
        category = "Array",
        parameters = ["values: Array<Number> | Number..."],
        returns = "Number - The minimum value, or null if array is empty",
        example = "min([3, 1, 4, 1, 5]) => 1\nmin(3, 1, 4) => 1"
    )
    fun min(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "min requires at least 1 argument, got 0. " +
                "Hint: Usage is min([1,2,3]) or min(1, 2, 3) to find the minimum value."
            )
        }

        // If single argument, it must be an array
        if (args.size == 1) {
            if (args[0] !is UDM.Array) {
                throw FunctionArgumentException(
                    "min with single argument requires an array, but got ${getTypeDescription(args[0])}. " +
                    "Hint: Use min([1,2,3]) with an array, or min(1, 2, 3) with multiple arguments."
                )
            }
            val array = args[0] as UDM.Array
            if (array.elements.isEmpty()) {
                return UDM.Scalar(null)
            }
            val minValue = array.elements.minOf { it.asNumberStrict() }
            return UDM.Scalar(minValue)
        }

        // Otherwise treat all arguments as numbers to compare
        val minValue = args.minOf { it.asNumberStrict() }
        return UDM.Scalar(minValue)
    }

    @UTLXFunction(
        description = "Find the maximum value from an array or multiple arguments",
        category = "Array",
        parameters = ["values: Array<Number> | Number..."],
        returns = "Number - The maximum value, or null if array is empty",
        example = "max([3, 1, 4, 1, 5]) => 5\nmax(3, 1, 8) => 8"
    )
    fun max(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "max requires at least 1 argument, got 0. " +
                "Hint: Usage is max([1,2,3]) or max(1, 2, 3) to find the maximum value."
            )
        }

        // If single argument, it must be an array
        if (args.size == 1) {
            if (args[0] !is UDM.Array) {
                throw FunctionArgumentException(
                    "max with single argument requires an array, but got ${getTypeDescription(args[0])}. " +
                    "Hint: Use max([1,2,3]) with an array, or max(1, 2, 3) with multiple arguments."
                )
            }
            val array = args[0] as UDM.Array
            if (array.elements.isEmpty()) {
                return UDM.Scalar(null)
            }
            val maxValue = array.elements.maxOf { it.asNumberStrict() }
            return UDM.Scalar(maxValue)
        }

        // Otherwise treat all arguments as numbers to compare
        val maxValue = args.maxOf { it.asNumberStrict() }
        return UDM.Scalar(maxValue)
    }

    @UTLXFunction(
        description = "Count the number of elements in an array",
        category = "Array",
        parameters = ["array: Array"],
        returns = "Number - The count of elements in the array",
        example = "count([1, 2, 3, 4]) => 4\ncount([\"a\", \"b\", \"c\"]) => 3"
    )
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

    private fun UDM.asNumberStrict(): Double {
        return when (this) {
            is UDM.Scalar -> {
                when (val v = value) {
                    null -> throw FunctionArgumentException(
                        "Cannot use null value in numeric aggregation. " +
                        "Hint: Remove null values from the array or use a default value."
                    )
                    is Number -> v.toDouble()
                    is Boolean -> throw FunctionArgumentException(
                        "Cannot use boolean value in numeric aggregation. " +
                        "Hint: Convert boolean to number (e.g., true -> 1, false -> 0) before aggregation."
                    )
                    is String -> v.toDoubleOrNull()
                        ?: throw FunctionArgumentException(
                            "Cannot convert '$v' to number. " +
                            "Hint: Ensure the string contains a valid numeric value."
                        )
                    else -> throw FunctionArgumentException(
                        "Expected number value, but got ${v.javaClass.simpleName}. " +
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
