// stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/MoreArrayFunctions.kt
package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Additional array utility functions
 */
object MoreArrayFunctions {
    
    @UTLXFunction(
        description = "Remove element at index from array",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "index: Index value",
        "value: Value value"
        ],
        returns = "Result of the operation",
        example = "remove([1, 2, 3, 4], 2) => [1, 2, 4]",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Remove element at index from array
     * Usage: remove([1, 2, 3, 4], 2) => [1, 2, 4]
     */
    fun remove(args: List<UDM>): UDM {
        requireArgs(args, 2, "remove")
        val array = args[0].asArray() ?: throw FunctionArgumentException("remove: first argument must be an array")
        val index = args[1].asNumber().toInt()
        
        if (index < 0 || index >= array.elements.size) {
            throw FunctionArgumentException("Index $index out of bounds for array of size ${array.elements.size}")
        }
        
        val newElements = array.elements.toMutableList()
        newElements.removeAt(index)
        
        return UDM.Array(newElements)
    }
    
    @UTLXFunction(
        description = "Insert element before index in array",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "index: Index value",
        "value: Value value"
        ],
        returns = "Result of the operation",
        example = "insertBefore([1, 2, 4], 2, 3) => [1, 2, 3, 4]",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Insert element before index in array
     * Usage: insertBefore([1, 2, 4], 2, 3) => [1, 2, 3, 4]
     */
    fun insertBefore(args: List<UDM>): UDM {
        requireArgs(args, 3, "insertBefore")
        val array = args[0].asArray() ?: throw FunctionArgumentException("insertBefore: first argument must be an array")
        val index = args[1].asNumber().toInt()
        val value = args[2]
        
        if (index < 0 || index > array.elements.size) {
            throw FunctionArgumentException("Index $index out of bounds for array of size ${array.elements.size}")
        }
        
        val newElements = array.elements.toMutableList()
        newElements.add(index, value)
        
        return UDM.Array(newElements)
    }
    
    @UTLXFunction(
        description = "Insert element after index in array",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "index: Index value",
        "value: Value value"
        ],
        returns = "Result of the operation",
        example = "insertAfter([1, 2, 4], 1, 3) => [1, 2, 3, 4]",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Insert element after index in array
     * Usage: insertAfter([1, 2, 4], 1, 3) => [1, 2, 3, 4]
     */
    fun insertAfter(args: List<UDM>): UDM {
        requireArgs(args, 3, "insertAfter")
        val array = args[0].asArray() ?: throw FunctionArgumentException("insertAfter: first argument must be an array")
        val index = args[1].asNumber().toInt()
        val value = args[2]
        
        if (index < 0 || index >= array.elements.size) {
            throw FunctionArgumentException("Index $index out of bounds for array of size ${array.elements.size}")
        }
        
        val newElements = array.elements.toMutableList()
        newElements.add(index + 1, value)
        
        return UDM.Array(newElements)
    }
    
    @UTLXFunction(
        description = "Find index of value in array (simple equality)",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "array: Array to search",
        "predicate: Condition to match"
        ],
        returns = "Index of the element",
        example = "indexOf([1, 2, 3, 2], 2) => 1 (first occurrence)",
        tags = ["array", "index"],
        since = "1.0"
    )
    /**
     * Find index of value in array (simple equality)
     * Usage: indexOf([1, 2, 3, 2], 2) => 1 (first occurrence)
     */
    fun indexOf(args: List<UDM>): UDM {
        requireArgs(args, 2, "indexOf")
        val array = args[0].asArray() ?: throw FunctionArgumentException("indexOf: first argument must be an array")
        val searchValue = args[1]
        
        val index = array.elements.indexOfFirst { element ->
            elementsEqual(element, searchValue)
        }
        
        return UDM.Scalar(if (index >= 0) index.toDouble() else null)
    }
    
    @UTLXFunction(
        description = "Find last index of value in array",
        minArgs = 2,
        maxArgs = 2,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "array: Array to search",
        "predicate: Condition to match"
        ],
        returns = "Index of the element",
        example = "lastIndexOf([1, 2, 3, 2], 2) => 3 (last occurrence)",
        tags = ["array", "index"],
        since = "1.0"
    )
    /**
     * Find last index of value in array
     * Usage: lastIndexOf([1, 2, 3, 2], 2) => 3 (last occurrence)
     */
    fun lastIndexOf(args: List<UDM>): UDM {
        requireArgs(args, 2, "lastIndexOf")
        val array = args[0].asArray() ?: throw FunctionArgumentException("lastIndexOf: first argument must be an array")
        val searchValue = args[1]
        
        val index = array.elements.indexOfLast { element ->
            elementsEqual(element, searchValue)
        }
        
        return UDM.Scalar(if (index >= 0) index.toDouble() else null)
    }
    
    @UTLXFunction(
        description = "Check if array includes value",
        minArgs = 2,
        maxArgs = 2,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "searchValue: Searchvalue value"
        ],
        returns = "Result of the operation",
        example = "includes([1, 2, 3], 2) => true",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Check if array includes value
     * Usage: includes([1, 2, 3], 2) => true
     */
    fun includes(args: List<UDM>): UDM {
        requireArgs(args, 2, "includes")
        val array = args[0].asArray() ?: throw FunctionArgumentException("includes: first argument must be an array")
        val searchValue = args[1]
        
        val found = array.elements.any { element ->
            elementsEqual(element, searchValue)
        }
        
        return UDM.Scalar(found)
    }
    
    @UTLXFunction(
        description = "Slice array from start to end index",
        minArgs = 2,
        maxArgs = 2,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "start: Start value"
        ],
        returns = "Result of the operation",
        example = "slice([1, 2, 3, 4, 5], 1, 4) => [2, 3, 4]",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Slice array from start to end index
     * Usage: slice([1, 2, 3, 4, 5], 1, 4) => [2, 3, 4]
     */
    fun slice(args: List<UDM>): UDM {
        requireArgs(args, 2..3, "slice")
        val array = args[0].asArray() ?: throw FunctionArgumentException("slice: first argument must be an array")
        val start = args[1].asNumber().toInt()
        val end = if (args.size > 2) args[2].asNumber().toInt() else array.elements.size
        
        if (start < 0 || start > array.elements.size) {
            throw FunctionArgumentException("Start index $start out of bounds")
        }
        if (end < start || end > array.elements.size) {
            throw FunctionArgumentException("End index $end out of bounds")
        }
        
        val sliced = array.elements.subList(start, end)
        return UDM.Array(sliced)
    }
    
    @UTLXFunction(
        description = "Concatenate multiple arrays",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "concat([1, 2], [3, 4], [5]) => [1, 2, 3, 4, 5]",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Concatenate multiple arrays
     * Usage: concat([1, 2], [3, 4], [5]) => [1, 2, 3, 4, 5]
     */
    fun concat(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            return UDM.Array(emptyList())
        }
        
        val result = mutableListOf<UDM>()
        for (arg in args) {
            val array = arg.asArray() ?: throw FunctionArgumentException("concat: all arguments must be arrays")
            result.addAll(array.elements)
        }
        
        return UDM.Array(result)
    }
    
    // Helper functions
    
    private fun elementsEqual(a: UDM, b: UDM): Boolean {
        return when {
            a is UDM.Scalar && b is UDM.Scalar -> a.value == b.value
            a is UDM.Array && b is UDM.Array -> {
                a.elements.size == b.elements.size && 
                a.elements.zip(b.elements).all { (x, y) -> elementsEqual(x, y) }
            }
            a is UDM.Object && b is UDM.Object -> {
                a.properties.size == b.properties.size &&
                a.properties.all { (key, value) -> 
                    b.properties[key]?.let { elementsEqual(value, it) } ?: false
                }
            }
            else -> false
        }
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun requireArgs(args: List<UDM>, range: IntRange, functionName: String) {
        if (args.size !in range) {
            throw FunctionArgumentException("$functionName expects ${range.first}..${range.last} arguments, got ${args.size}")
        }
    }
    
    private fun UDM.asArray(): UDM.Array? {
        return this as? UDM.Array
    }
    
    private fun UDM.asNumber(): Double {
        return when (this) {
            is UDM.Scalar -> {
                val v = value
                when (v) {
                    is Number -> v.toDouble()
                    is String -> v.toDoubleOrNull()
                        ?: throw FunctionArgumentException("Cannot convert '$v' to number")
                    else -> throw FunctionArgumentException("Expected number value, got $v")
                }
            }
            else -> throw FunctionArgumentException("Expected number value, got ${this::class.simpleName}")
        }
    }
}
