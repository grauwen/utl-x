// stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/ArrayFunctions.kt
package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Array manipulation functions for UTL-X
 */
object ArrayFunctions {
    
    @UTLXFunction(
        description = "Map function over array",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "map([1, 2, 3], x => x * 2) => [2, 4, 6]",
        tags = ["array", "transform"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Filter array by predicate",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "New array with filtered elements",
        example = "filter([1, 2, 3, 4], x => x > 2) => [3, 4]",
        tags = ["array", "filter"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Reduce array to single value",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "reduce([1, 2, 3, 4], (acc, x) => acc + x, 0) => 10",
        tags = ["aggregate", "array"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Find first element matching predicate",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "First matching element, or null if none found",
        example = "find([1, 2, 3, 4], x => x > 2) => 3",
        tags = ["array", "search"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Find index of first element matching predicate",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Index of the element",
        example = "findIndex([1, 2, 3, 4], x => x > 2) => 2",
        tags = ["array", "index", "search"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Check if all elements match predicate",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "every([2, 4, 6], x => x % 2 == 0) => true",
        tags = ["array", "predicate"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Check if any element matches predicate",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "some([1, 2, 3], x => x > 2) => true",
        tags = ["array", "predicate"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Flatten nested arrays",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "flatten([[1, 2], [3, 4]]) => [1, 2, 3, 4]",
        tags = ["array"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Reverse array",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "reverse([1, 2, 3]) => [3, 2, 1]",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Reverse array
     * Usage: reverse([1, 2, 3]) => [3, 2, 1]
     */
    fun reverse(args: List<UDM>): UDM {
        requireArgs(args, 1, "reverse")
        val array = args[0].asArray() ?: throw FunctionArgumentException("reverse: first argument must be an array")
        return UDM.Array(array.elements.reversed())
    }
    
    @UTLXFunction(
        description = "Sort array",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "sort([3, 1, 2]) => [1, 2, 3]",
        tags = ["array", "sort"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Sort array by key function",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "sortBy([{age: 30}, {age: 20}], x => x.age) => [{age: 20}, {age: 30}]",
        tags = ["array", "sort"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Get first element",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "n: N value"
        ],
        returns = "Result of the operation",
        example = "first([1, 2, 3]) => 1",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Get first element
     * Usage: first([1, 2, 3]) => 1
     */
    fun first(args: List<UDM>): UDM {
        requireArgs(args, 1, "first")
        val arg = args[0]
        val array = arg.asArray() ?: throw FunctionArgumentException(
            "first() requires an array argument, got ${getTypeDescription(arg)}"
        )
        if (array.elements.isEmpty()) {
            throw FunctionArgumentException("first() called on empty array")
        }
        return array.elements.first()
    }
    
    @UTLXFunction(
        description = "Get last element",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "n: N value"
        ],
        returns = "Result of the operation",
        example = "last([1, 2, 3]) => 3",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Get last element
     * Usage: last([1, 2, 3]) => 3
     */
    fun last(args: List<UDM>): UDM {
        requireArgs(args, 1, "last")
        val arg = args[0]
        val array = arg.asArray() ?: throw FunctionArgumentException(
            "last() requires an array argument, got ${getTypeDescription(arg)}"
        )
        if (array.elements.isEmpty()) {
            throw FunctionArgumentException("last() called on empty array")
        }
        return array.elements.last()
    }
    
    @UTLXFunction(
        description = "Take first n elements",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "n: N value"
        ],
        returns = "Result of the operation",
        example = "take([1, 2, 3, 4], 2) => [1, 2]",
        tags = ["array"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Drop first n elements",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "n: N value"
        ],
        returns = "Result of the operation",
        example = "drop([1, 2, 3, 4], 2) => [3, 4]",
        tags = ["array"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Get unique elements",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "array2: Array2 value"
        ],
        returns = "Result of the operation",
        example = "unique([1, 2, 2, 3, 1]) => [1, 2, 3]",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Get unique elements
     * Usage: unique([1, 2, 2, 3, 1]) => [1, 2, 3]
     */
    fun unique(args: List<UDM>): UDM {
        requireArgs(args, 1, "unique")
        val array = args[0].asArray() ?: throw FunctionArgumentException("unique: first argument must be an array")
        return UDM.Array(array.elements.distinct())
    }
    
    @UTLXFunction(
        description = "Zip two arrays together",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "array2: Array2 value"
        ],
        returns = "Result of the operation",
        example = "zip([1, 2], [\"a\", \"b\"]) => [[1, \"a\"], [2, \"b\"]]",
        tags = ["array"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Performs size operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "index: Index value"
        ],
        returns = "the size/length of an array",
        example = "size(...) => result",
        notes = "Returns the size/length of an array",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Returns the size/length of an array
     * @param array The input array
     * @return Number of elements
     */
    fun size(args: List<UDM>): UDM {
        requireArgs(args, 1, "size")
        val arg = args[0]
        val array = arg.asArray() ?: throw FunctionArgumentException(
            "size() requires an array argument, got ${getTypeDescription(arg)}"
        )
        return UDM.Scalar(array.elements.size.toDouble())
    }
    
    @UTLXFunction(
        description = "Gets element at specific index (0-based)",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "index: Index value"
        ],
        returns = "Result of the operation",
        example = "get(...) => result",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Gets element at specific index (0-based)
     * @param array The input array
     * @param index Zero-based index
     * @return Element at index, or null if out of bounds
     */
    fun get(args: List<UDM>): UDM {
        requireArgs(args, 2, "get")
        val array = args[0].asArray() ?: throw FunctionArgumentException("get: first argument must be an array")
        val index = args[1].asNumber().toInt()
        
        return if (index >= 0 && index < array.elements.size) {
            array.elements[index]
        } else {
            UDM.Scalar(null)
        }
    }
    
    @UTLXFunction(
        description = "Performs tail operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "all elements except the first (functional programming convention)",
        example = "tail(...) => result",
        notes = "Returns all elements except the first (functional programming convention)",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Returns all elements except the first (functional programming convention)
     * @param array The input array
     * @return New array with all elements except first
     */
    fun tail(args: List<UDM>): UDM {
        requireArgs(args, 1, "tail")
        val array = args[0].asArray() ?: throw FunctionArgumentException("tail: argument must be an array")
        
        return if (array.elements.isEmpty()) {
            UDM.Array(emptyList())
        } else {
            UDM.Array(array.elements.drop(1))
        }
    }
    
    @UTLXFunction(
        description = "Removes duplicate elements from array",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "distinct(...) => result",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Removes duplicate elements from array
     * @param array The input array
     * @return New array with duplicates removed
     */
    fun distinct(args: List<UDM>): UDM {
        requireArgs(args, 1, "distinct")
        val array = args[0].asArray() ?: throw FunctionArgumentException("distinct: argument must be an array")
        
        val uniqueElements = array.elements.distinct()
        return UDM.Array(uniqueElements)
    }
    
    @UTLXFunction(
        description = "Removes duplicates based on function result",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "distinctBy(...) => result",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Removes duplicates based on function result
     * @param array The input array
     * @param selector Function to extract comparison value
     * @return New array with duplicates removed
     */
    fun distinctBy(args: List<UDM>): UDM {
        requireArgs(args, 2, "distinctBy")
        val array = args[0].asArray() ?: throw FunctionArgumentException("distinctBy: first argument must be an array")
        val lambda = args[1] as? UDM.Lambda 
            ?: throw FunctionArgumentException("distinctBy: second argument must be a lambda")
        
        val uniqueElements = array.elements.distinctBy { element ->
            lambda.apply(listOf(element))
        }
        
        return UDM.Array(uniqueElements)
    }
    
    @UTLXFunction(
        description = "Combines two arrays and removes duplicates (set union: A ∪ B)",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "array2: Array2 value"
        ],
        returns = "union of two arrays (all elements, no duplicates)",
        example = "union(...) => result",
        notes = "Returns union of two arrays (all elements, no duplicates)",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Returns union of two arrays (all elements, no duplicates)
     * Combines two arrays and removes duplicates (set union: A ∪ B)
     * @param array1 First array
     * @param array2 Second array
     * @return New array with all unique elements from both arrays
     */
    fun union(args: List<UDM>): UDM {
        requireArgs(args, 2, "union")
        val array1 = args[0].asArray() ?: throw FunctionArgumentException("union: first argument must be an array")
        val array2 = args[1].asArray() ?: throw FunctionArgumentException("union: second argument must be an array")
        
        val combined = array1.elements + array2.elements
        val uniqueElements = combined.distinct()
        return UDM.Array(uniqueElements)
    }
    
    @UTLXFunction(
        description = "Performs intersect operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "array2: Array2 value"
        ],
        returns = "intersection of two arrays (elements in both)",
        example = "intersect(...) => result",
        notes = "Returns intersection of two arrays (elements in both)",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Returns intersection of two arrays (elements in both)
     * @param array1 First array
     * @param array2 Second array
     * @return New array with elements present in both arrays
     */
    fun intersect(args: List<UDM>): UDM {
        requireArgs(args, 2, "intersect")
        val array1 = args[0].asArray() ?: throw FunctionArgumentException("intersect: first argument must be an array")
        val array2 = args[1].asArray() ?: throw FunctionArgumentException("intersect: second argument must be an array")
        
        val intersection = array1.elements.filter { element ->
            array2.elements.contains(element)
        }.distinct()
        
        return UDM.Array(intersection)
    }
    
    @UTLXFunction(
        description = "Performs difference operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "difference of two arrays (elements in first but not second)",
        example = "difference(...) => result",
        notes = "Returns difference of two arrays (elements in first but not second)",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Returns difference of two arrays (elements in first but not second)
     * @param array1 First array
     * @param array2 Second array
     * @return New array with elements in array1 not in array2
     */
    fun difference(args: List<UDM>): UDM {
        requireArgs(args, 2, "difference")
        val array1 = args[0].asArray() ?: throw FunctionArgumentException("difference: first argument must be an array")
        val array2 = args[1].asArray() ?: throw FunctionArgumentException("difference: second argument must be an array")
        
        val diff = array1.elements.filter { element ->
            !array2.elements.contains(element)
        }
        
        return UDM.Array(diff)
    }
    
    @UTLXFunction(
        description = "Symmetric difference - elements in either array but not both",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "symmetricDifference(...) => result",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Symmetric difference - elements in either array but not both
     * @param array1 First array
     * @param array2 Second array  
     * @return New array with elements in either but not both
     */
    fun symmetricDifference(args: List<UDM>): UDM {
        requireArgs(args, 2, "symmetricDifference")
        val array1 = args[0].asArray() ?: throw FunctionArgumentException("symmetricDifference: first argument must be an array")
        val array2 = args[1].asArray() ?: throw FunctionArgumentException("symmetricDifference: second argument must be an array")
        
        val diff1 = array1.elements.filter { !array2.elements.contains(it) }
        val diff2 = array2.elements.filter { !array1.elements.contains(it) }
        
        return UDM.Array(diff1 + diff2)
    }
    
    @UTLXFunction(
        description = "Maps each element using function, then flattens result",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "flatMap(...) => result",
        notes = "Example: [1,2,3].flatMap(x => [x, x*2]) => [1,2,2,4,3,6]",
        tags = ["array", "transform"],
        since = "1.0"
    )
    /**
     * Maps each element using function, then flattens result
     * Example: [1,2,3].flatMap(x => [x, x*2]) => [1,2,2,4,3,6]
     * @param array The input array
     * @param mapper Function that returns an array for each element
     * @return Mapped and flattened array
     */
    fun flatMap(args: List<UDM>): UDM {
        requireArgs(args, 2, "flatMap")
        val array = args[0].asArray() ?: throw FunctionArgumentException("flatMap: first argument must be an array")
        val lambda = args[1] as? UDM.Lambda 
            ?: throw FunctionArgumentException("flatMap: second argument must be a lambda")
        
        val result = array.elements.flatMap { element ->
            val mapped = lambda.apply(listOf(element))
            when (mapped) {
                is UDM.Array -> mapped.elements
                else -> listOf(mapped)
            }
        }
        
        return UDM.Array(result)
    }
    
    @UTLXFunction(
        description = "Deep flatten - flattens all nested levels",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "chunkSize: Chunksize value"
        ],
        returns = "Result of the operation",
        example = "flattenDeep(...) => result",
        notes = "Example: [1,[2,[3,[4]]]] => [1,2,3,4]",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Deep flatten - flattens all nested levels
     * Example: [1,[2,[3,[4]]]] => [1,2,3,4]
     * @param array The input array
     * @return Fully flattened array
     */
    fun flattenDeep(args: List<UDM>): UDM {
        requireArgs(args, 1, "flattenDeep")
        val array = args[0].asArray() ?: throw FunctionArgumentException("flattenDeep: argument must be an array")
        
        fun flattenRecursive(elements: List<UDM>): List<UDM> {
            val result = mutableListOf<UDM>()
            for (element in elements) {
                when (element) {
                    is UDM.Array -> result.addAll(flattenRecursive(element.elements))
                    else -> result.add(element)
                }
            }
            return result
        }
        
        return UDM.Array(flattenRecursive(array.elements))
    }
    
    @UTLXFunction(
        description = "Chunks array into arrays of specified size",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "chunkSize: Chunksize value"
        ],
        returns = "Result of the operation",
        example = "chunk(...) => result",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Chunks array into arrays of specified size
     * @param array The input array
     * @param size Chunk size
     * @return Array of arrays, each of specified size (last may be smaller)
     */
    fun chunk(args: List<UDM>): UDM {
        requireArgs(args, 2, "chunk")
        val array = args[0].asArray() ?: throw FunctionArgumentException("chunk: first argument must be an array")
        val chunkSize = args[1].asNumber().toInt()
        
        if (chunkSize <= 0) {
            throw FunctionArgumentException("chunk: chunk size must be positive")
        }
        
        val chunks = array.elements.chunked(chunkSize).map { chunk ->
            UDM.Array(chunk)
        }
        
        return UDM.Array(chunks)
    }
    
    @UTLXFunction(
        description = "Joins array elements into string",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "joinToString(...) => result",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Joins array elements into string
     * @param array The input array
     * @param separator String to insert between elements
     * @return Joined string
     */
    fun joinToString(args: List<UDM>): UDM {
        if (args.size < 1 || args.size > 2) {
            throw FunctionArgumentException("joinToString expects 1 or 2 arguments, got ${args.size}")
        }
        
        val array = args[0].asArray() ?: throw FunctionArgumentException("joinToString: first argument must be an array")
        val separator = if (args.size > 1) {
            (args[1] as? UDM.Scalar)?.value?.toString() ?: ","
        } else {
            ","
        }
        
        val joined = array.elements.joinToString(separator) { element ->
            when (element) {
                is UDM.Scalar -> element.value?.toString() ?: "null"
                else -> element.toString()
            }
        }
        
        return UDM.Scalar(joined)
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
    
    /**
     * Get type description for error messages
     */
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
            is UDM.Lambda -> "lambda"
            else -> udm.javaClass.simpleName
        }
    }
}
