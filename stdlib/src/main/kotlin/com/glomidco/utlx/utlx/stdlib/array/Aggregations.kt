// stdlib/src/main/kotlin/com/glomidco/utlx/stdlib/array/Aggregations.kt
package com.glomidco.utlx.stdlib.array

import com.glomidco.utlx.core.udm.ExactArithmetic
import com.glomidco.utlx.core.udm.ScalarCanonical
import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.FunctionArgumentException
import com.glomidco.utlx.stdlib.annotations.UTLXFunction

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
        
        // B45: fold through ExactArithmetic so `sum([1.50, 1.50])` is 3.00 — the same answer the
        // `+` operator already gives. `sumOf` takes a Double selector, so the whole aggregation was
        // computed in Double and the scale was gone before the result was built. ExactArithmetic
        // falls back to the Double sum the moment any element is a Double, so a mixed array behaves
        // exactly as it did.
        var total: Number = 0L
        var fallback = 0.0
        for (element in array.elements) {
            val n = element.numberAsWritten()
            fallback += n.toDouble()
            total = ExactArithmetic.add(total, n, fallback)
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
            // B45: return the ORIGINAL element rather than a Double recomputed from it, so
            // `min([1.50, 2.50])` keeps its scale. Ordering goes through ScalarCanonical, the one
            // definition of numeric ordering in the product.
            //
            // Every element is validated FIRST: `reduce` never calls its lambda on a single-element
            // list, so validating inside the comparator would have let `min(["a"])` through.
            array.elements.forEach { it.numberAsWritten() }
            return array.elements.reduce { a, b ->
                if (ScalarCanonical.compareNumbers(a.numberAsWritten(), b.numberAsWritten()) <= 0) a else b
            }.asSelectedNumber()
        }

        // Otherwise treat all arguments as numbers to compare
        // B45: same for the varargs form, validated first for the same reason.
        args.forEach { it.numberAsWritten() }
        return args.reduce { a, b ->
            if (ScalarCanonical.compareNumbers(a.numberAsWritten(), b.numberAsWritten()) <= 0) a else b
        }.asSelectedNumber()
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
            // B45: return the ORIGINAL element rather than a Double recomputed from it, so
            // `max([1.50, 2.50])` keeps its scale. Ordering goes through ScalarCanonical, the one
            // definition of numeric ordering in the product.
            //
            // Every element is validated FIRST: `reduce` never calls its lambda on a single-element
            // list, so validating inside the comparator would have let `max(["a"])` through.
            array.elements.forEach { it.numberAsWritten() }
            return array.elements.reduce { a, b ->
                if (ScalarCanonical.compareNumbers(a.numberAsWritten(), b.numberAsWritten()) >= 0) a else b
            }.asSelectedNumber()
        }

        // Otherwise treat all arguments as numbers to compare
        // B45: same for the varargs form, validated first for the same reason.
        args.forEach { it.numberAsWritten() }
        return args.reduce { a, b ->
            if (ScalarCanonical.compareNumbers(a.numberAsWritten(), b.numberAsWritten()) >= 0) a else b
        }.asSelectedNumber()
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

    /**
     * B45: the validated numeric value **as written** — a `BigDecimal` or `Long` stays one.
     *
     * [asNumberStrict] narrows to `Double`, which is right where a `Double` is genuinely wanted
     * (`avg`, which divides) and wrong where the value is only summed or compared.
     *
     * Anything that is not already a `Number` — a numeric string included — falls through to
     * [asNumberStrict], which keeps ownership of both the string conversion and the error messages.
     *
     * **Not named `asNumber`.** `UDM` has a *member* `asNumber(): Double` that returns `0.0` for a
     * null, a boolean or any non-numeric value, and a member always wins over an extension of the
     * same name. Calling it `asNumber` here compiled, ran, and silently resolved to that member — so
     * the exact fold below fell back to Double on every element and `min(["a"])` returned `"a"`
     * instead of raising. The name has to differ for the resolution to be visible.
     */
    private fun UDM.numberAsWritten(): Number {
        val v = (this as? UDM.Scalar)?.value
        return if (v is Number) v else asNumberStrict()
    }

    /**
     * The element [min]/[max] selected, as the value to return.
     *
     * A numeric scalar is handed back **unchanged**, which is the point of B45 — `min([1.50, 2.50])`
     * keeps its scale. Anything else (a numeric string, an XML text node) is returned as the number
     * it converts to, because returning the raw element would change the result's *type*:
     * `min(["10", "20.5"])` must stay a number, not become the string `"10"`.
     */
    private fun UDM.asSelectedNumber(): UDM {
        val v = (this as? UDM.Scalar)?.value
        return if (v is Number) this else UDM.Scalar(asNumberStrict())
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
