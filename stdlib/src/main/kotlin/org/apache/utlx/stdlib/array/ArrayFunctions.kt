// stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/ArrayFunctions.kt
package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

/**
 * Array manipulation functions for UTL-X
 */
object ArrayFunctions {
    
    /**
     * Map function over array
     * Usage: map([1, 2, 3], x => x * 2) => [2, 4, 6]
     */
    fun map(args: List<UDM>): UDM {
        requireArgs(args, 2, "map")
        val array = args[0].asArray() ?: throw FunctionArgumentException("map: first argument must be an array")
        val lambda = args[1] as? UDM.Lambda 
            ?: throw FunctionArgumentException("map: second argument must be a lambda")
        
        val result = array.elements.map { element ->
            lambda.apply(listOf(element))
        }
        
        return UDM.Array(result)
    }
    
    /**
     * Filter array by predicate
     * Usage: filter([1, 2, 3, 4], x => x > 2) => [3, 4]
     */
    fun filter(args: List<UDM>): UDM {
        requireArgs(args, 2, "filter")
        val array = args[0].asArray() ?: throw FunctionArgumentException("filter: first argument must be an array")
        val lambda = args[1] as? UDM.Lambda
            ?: throw FunctionArgumentException("filter: second argument must be a lambda")
        
        val result = array.elements.filter { element ->
            val predicateResult = lambda.apply(listOf(element))
            predicateResult.asBoolean()
        }
        
        return UDM.Array(result)
    }
    
    /**
     * Reduce array to single value
     * Usage: reduce([1, 2, 3, 4], (acc, x) => acc + x, 0) => 10
     */
    fun reduce(args: List<UDM>): UDM {
        requireArgs(args, 3, "reduce")
        val array = args[0].asArray() ?: throw FunctionArgumentException("reduce: first argument must be an array")
        val lambda = args[1] as? UDM.Lambda
            ?: throw FunctionArgumentException("reduce: second argument must be a lambda")
        var accumulator = args[2]
        
        for (element in array.elements) {
            accumulator = lambda.apply(listOf(accumulator, element))
        }
        
        return accumulator
    }
    
    /**
     * Find first element matching predicate
     * Usage: find([1, 2, 3, 4], x => x > 2) => 3
     */
    fun find(args: List<UDM>): UDM {
        requireArgs(args, 2, "find")
        val array = args[0].asArray() ?: throw FunctionArgumentException("find: first argument must be an array")
        val lambda = args[1] as? UDM.Lambda
            ?: throw FunctionArgumentException("find: second argument must be a lambda")
        
        return array.elements.firstOrNull { element ->
            lambda.apply(listOf(element)).asBoolean()
        } ?: UDM.Scalar(null)
    }
    
    /**
     * Find index of first element matching predicate
     * Usage: findIndex([1, 2, 3, 4], x => x > 2) => 2
     */
    fun findIndex(args: List<UDM>): UDM {
        requireArgs(args, 2, "findIndex")
        val array = args[0].asArray() ?: throw FunctionArgumentException("findIndex: first argument must be an array")
        val lambda = args[1] as? UDM.Lambda
            ?: throw FunctionArgumentException("findIndex: second argument must be a lambda")
        
        val index = array.elements.indexOfFirst { element ->
            lambda.apply(listOf(element)).asBoolean()
        }
        
        return UDM.Scalar(if (index >= 0) index.toDouble() else null)
    }
    
    /**
     * Check if all elements match predicate
     * Usage: every([2, 4, 6], x => x % 2 == 0) => true
     */
    fun every(args: List<UDM>): UDM {
        requireArgs(args, 2, "every")
        val array = args[0].asArray() ?: throw FunctionArgumentException("every: first argument must be an array")
        val lambda = args[1] as? UDM.Lambda
            ?: throw FunctionArgumentException("every: second argument must be a lambda")
        
        val result = array.elements.all { element ->
            lambda.apply(listOf(element)).asBoolean()
        }
        
        return UDM.Scalar(result)
    }
    
    /**
     * Check if any element matches predicate
     * Usage: some([1, 2, 3], x => x > 2) => true
     */
    fun some(args: List<UDM>): UDM {
        requireArgs(args, 2, "some")
        val array = args[0].asArray() ?: throw FunctionArgumentException("some: first argument must be an array")
        val lambda = args[1] as? UDM.Lambda
            ?: throw FunctionArgumentException("some: second argument must be a lambda")
        
        val result = array.elements.any { element ->
            lambda.apply(listOf(element)).asBoolean()
        }
        
        return UDM.Scalar(result)
    }
    
    /**
     * Flatten nested arrays
     * Usage: flatten([[1, 2], [3, 4]]) => [1, 2, 3, 4]
     */
    fun flatten(args: List<UDM>): UDM {
        requireArgs(args, 1, "flatten")
        val array = args[0].asArray() ?: throw FunctionArgumentException("flatten: first argument must be an array")
        
        val flattened = mutableListOf<UDM>()
        for (element in array.elements) {
            if (element is UDM.Array) {
                flattened.addAll(element.elements)
            } else {
                flattened.add(element)
            }
        }
        
        return UDM.Array(flattened)
    }
    
    /**
     * Reverse array
     * Usage: reverse([1, 2, 3]) => [3, 2, 1]
     */
    fun reverse(args: List<UDM>): UDM {
        requireArgs(args, 1, "reverse")
        val array = args[0].asArray() ?: throw FunctionArgumentException("reverse: first argument must be an array")
        return UDM.Array(array.elements.reversed())
    }
    
    /**
     * Sort array
     * Usage: sort([3, 1, 2]) => [1, 2, 3]
     */
    fun sort(args: List<UDM>): UDM {
        requireArgs(args, 1, "sort")
        val array = args[0].asArray() ?: throw FunctionArgumentException("sort: first argument must be an array")
        
        val sorted = array.elements.sortedWith(compareBy { element ->
            when (element) {
                is UDM.Scalar -> when (val v = element.value) {
                    is Number -> v.toDouble()
                    is String -> v
                    else -> v.toString()
                }
                else -> element.toString()
            }
        })
        
        return UDM.Array(sorted)
    }
    
    /**
     * Sort array by key function
     * Usage: sortBy([{age: 30}, {age: 20}], x => x.age) => [{age: 20}, {age: 30}]
     */
    fun sortBy(args: List<UDM>): UDM {
        requireArgs(args, 2, "sortBy")
        val array = args[0].asArray() ?: throw FunctionArgumentException("sortBy: first argument must be an array")
        val lambda = args[1] as? UDM.Lambda
            ?: throw FunctionArgumentException("sortBy: second argument must be a lambda")
        
        val sorted = array.elements.sortedBy { element ->
            val key = lambda.apply(listOf(element))
            when (key) {
                is UDM.Scalar -> when (val v = key.value) {
                    is Number -> v.toString()
                    is String -> v
                    else -> v.toString()
                }
                else -> key.toString()
            }
        }
        
        return UDM.Array(sorted)
    }
    
    /**
     * Get first element
     * Usage: first([1, 2, 3]) => 1
     */
    fun first(args: List<UDM>): UDM {
        requireArgs(args, 1, "first")
        val array = args[0].asArray() ?: throw FunctionArgumentException("first: first argument must be an array")
        return array.elements.firstOrNull() ?: UDM.Scalar(null)
    }
    
    /**
     * Get last element
     * Usage: last([1, 2, 3]) => 3
     */
    fun last(args: List<UDM>): UDM {
        requireArgs(args, 1, "last")
        val array = args[0].asArray() ?: throw FunctionArgumentException("last: first argument must be an array")
        return array.elements.lastOrNull() ?: UDM.Scalar(null)
    }
    
    /**
     * Take first n elements
     * Usage: take([1, 2, 3, 4], 2) => [1, 2]
     */
    fun take(args: List<UDM>): UDM {
        requireArgs(args, 2, "take")
        val array = args[0].asArray() ?: throw FunctionArgumentException("take: first argument must be an array")
        val n = args[1].asNumber().toInt()
        return UDM.Array(array.elements.take(n))
    }
    
    /**
     * Drop first n elements
     * Usage: drop([1, 2, 3, 4], 2) => [3, 4]
     */
    fun drop(args: List<UDM>): UDM {
        requireArgs(args, 2, "drop")
        val array = args[0].asArray() ?: throw FunctionArgumentException("drop: first argument must be an array")
        val n = args[1].asNumber().toInt()
        return UDM.Array(array.elements.drop(n))
    }
    
    /**
     * Get unique elements
     * Usage: unique([1, 2, 2, 3, 1]) => [1, 2, 3]
     */
    fun unique(args: List<UDM>): UDM {
        requireArgs(args, 1, "unique")
        val array = args[0].asArray() ?: throw FunctionArgumentException("unique: first argument must be an array")
        return UDM.Array(array.elements.distinct())
    }
    
    /**
     * Zip two arrays together
     * Usage: zip([1, 2], ["a", "b"]) => [[1, "a"], [2, "b"]]
     */
    fun zip(args: List<UDM>): UDM {
        requireArgs(args, 2, "zip")
        val array1 = args[0].asArray() ?: throw FunctionArgumentException("zip: first argument must be an array")
        val array2 = args[1].asArray() ?: throw FunctionArgumentException("zip: second argument must be an array")
        
        val zipped = array1.elements.zip(array2.elements).map { (a, b) ->
            UDM.Array(listOf(a, b))
        }
        
        return UDM.Array(zipped)
    }
    
    // Helper functions
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}"
            )
        }
    }
    
    private fun UDM.asArray(): UDM.Array? {
        return this as? UDM.Array
    }
    
    private fun UDM.asBoolean(): Boolean {
        return when (this) {
            is UDM.Scalar -> {
                val v = value
                when (v) {
                    is Boolean -> v
                    is Number -> v.toDouble() != 0.0
                    is String -> v.isNotEmpty()
                    null -> false
                    else -> true
                }
            }
            is UDM.Array -> elements.isNotEmpty()
            is UDM.Object -> properties.isNotEmpty()
            else -> true
        }
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
