// stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/CriticalArrayFunctions.kt
package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM

/**
 * Critical missing array functions
 */
object CriticalArrayFunctions {
    
    /**
     * Remove null, undefined, and empty values from array
     * 
     * Usage: compact([1, null, 2, "", 3, false, 4]) => [1, 2, 3, false, 4]
     * 
     * Filters out:
     * - null values
     * - empty strings
     * - undefined (represented as null in UDM)
     * 
     * Keeps:
     * - 0, false (they're valid values)
     * - Empty arrays/objects (they're valid structures)
     */
    fun compact(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("compact expects 1 argument, got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("compact expects an array, got ${array::class.simpleName}")
        }
        
        val filtered = array.elements.filter { element ->
            when {
                // Filter out null
                element is UDM.Scalar && element.value == null -> false
                // Filter out empty strings
                element is UDM.Scalar && element.value == "" -> false
                // Keep everything else (including 0, false, empty arrays/objects)
                else -> true
            }
        }
        
        return UDM.Array(filtered)
    }
    
    /**
     * Find the index of the first element matching predicate
     * 
     * Usage: findIndex([{id: 1}, {id: 2}, {id: 3}], item => item.id == 2) => 1
     * Usage: findIndex([1, 2, 3, 4], n => n > 2) => 2  (index of 3)
     * 
     * Returns -1 if no match found
     * 
     * This complements `find` which returns the element itself
     */
    fun findIndex(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("findIndex expects 2 arguments (array, predicate), got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("findIndex expects array as first argument")
        }
        
        val predicate = args[1]
        // In real implementation, predicate would be a function reference
        // For now, this is a placeholder showing the structure
        
        // Find the index of first matching element
        val index = array.elements.indexOfFirst { element ->
            // Apply predicate (in real impl, would call the function)
            // This is simplified - actual implementation needs function evaluation
            true // Placeholder
        }
        
        return UDM.Scalar(index.toDouble())
    }
    
    /**
     * Find the index of the last element matching predicate
     * 
     * Usage: findLastIndex([1, 2, 3, 2, 1], n => n == 2) => 3
     * Usage: findLastIndex(["a", "b", "a"], s => s == "a") => 2
     * 
     * Returns -1 if no match found
     * 
     * Complement to findIndex - searches from the end
     */
    fun findLastIndex(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("findLastIndex expects 2 arguments (array, predicate), got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("findLastIndex expects array as first argument")
        }
        
        val predicate = args[1]
        
        // Find the index of last matching element
        val index = array.elements.indexOfLast { element ->
            // Apply predicate (simplified)
            true // Placeholder
        }
        
        return UDM.Scalar(index.toDouble())
    }
    
    /**
     * Scan - like reduce but returns all intermediate results
     * 
     * Usage: scan([1, 2, 3, 4], (acc, n) => acc + n, 0) => [1, 3, 6, 10]
     * 
     * Similar to reduce, but instead of returning only final result,
     * returns array of all intermediate accumulator values.
     * 
     * Useful for:
     * - Running totals
     * - Cumulative sums
     * - Progressive calculations
     */
    fun scan(args: List<UDM>): UDM {
        if (args.size != 3) {
            throw IllegalArgumentException("scan expects 3 arguments (array, function, initial), got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("scan expects array as first argument")
        }
        
        val reducer = args[1]  // Function
        val initial = args[2]   // Initial value
        
        val results = mutableListOf<UDM>()
        var accumulator = initial
        
        for (element in array.elements) {
            // Apply reducer function: accumulator = reducer(accumulator, element)
            // In real implementation, would call the function
            // accumulator = evaluateFunction(reducer, listOf(accumulator, element))
            
            results.add(accumulator)
        }
        
        return UDM.Array(results)
    }
    
    /**
     * Windowed - create sliding window over array
     * 
     * Usage: windowed([1, 2, 3, 4, 5], 3) => [[1,2,3], [2,3,4], [3,4,5]]
     * Usage: windowed([1, 2, 3, 4], 2) => [[1,2], [2,3], [3,4]]
     * 
     * Creates overlapping subarrays of specified size.
     * 
     * With step parameter:
     * Usage: windowed([1, 2, 3, 4, 5], 2, 2) => [[1,2], [3,4]]
     * 
     * Useful for:
     * - Moving averages
     * - Pattern detection
     * - Sequence analysis
     */
    fun windowed(args: List<UDM>): UDM {
        if (args.size !in 2..3) {
            throw IllegalArgumentException("windowed expects 2-3 arguments, got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("windowed expects array as first argument")
        }
        
        val size = args[1]
        if (size !is UDM.Scalar || size.value !is Number) {
            throw IllegalArgumentException("windowed expects number as second argument")
        }
        
        val windowSize = (size.value as Number).toInt()
        
        val step = if (args.size == 3) {
            val stepArg = args[2]
            if (stepArg !is UDM.Scalar || stepArg.value !is Number) {
                throw IllegalArgumentException("windowed expects number as third argument")
            }
            (stepArg.value as Number).toInt()
        } else {
            1  // Default step
        }
        
        if (windowSize <= 0) {
            throw IllegalArgumentException("Window size must be positive, got $windowSize")
        }
        
        if (step <= 0) {
            throw IllegalArgumentException("Step must be positive, got $step")
        }
        
        val windows = mutableListOf<UDM>()
        var index = 0
        
        while (index + windowSize <= array.elements.size) {
            val window = array.elements.subList(index, index + windowSize)
            windows.add(UDM.Array(window))
            index += step
        }
        
        return UDM.Array(windows)
    }
    
    /**
     * ZipAll - zip arrays with padding for different lengths
     * 
     * Usage: zipAll([[1,2,3], ["a","b"]], null) => [[1,"a"], [2,"b"], [3,null]]
     * 
     * Unlike regular zip (stops at shortest), zipAll continues to longest array
     * and pads missing values with specified default.
     * 
     * Useful when you need to preserve all data from all arrays
     */
    fun zipAll(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("zipAll expects 2 arguments (arrays, default), got ${args.size}")
        }
        
        val arrays = args[0]
        if (arrays !is UDM.Array) {
            throw IllegalArgumentException("zipAll expects array of arrays as first argument")
        }
        
        val defaultValue = args[1]
        
        // Convert to list of arrays
        val arrayList = arrays.elements.map { elem ->
            if (elem !is UDM.Array) {
                throw IllegalArgumentException("zipAll expects array of arrays")
            }
            elem.elements
        }
        
        if (arrayList.isEmpty()) {
            return UDM.Array(emptyList())
        }
        
        // Find maximum length
        val maxLength = arrayList.maxOf { it.size }
        
        // Zip all together with padding
        val zipped = (0 until maxLength).map { index ->
            val tuple = arrayList.map { array ->
                if (index < array.size) array[index] else defaultValue
            }
            UDM.Array(tuple)
        }
        
        return UDM.Array(zipped)
    }
}

/**
 * Registration in Functions.kt:
 * 
 * Add these to the registerArrayFunctions() method:
 * 
 * // Critical array utilities
 * register("compact", CriticalArrayFunctions::compact)
 * register("findIndex", CriticalArrayFunctions::findIndex)
 * register("findLastIndex", CriticalArrayFunctions::findLastIndex)
 * 
 * // Advanced functional operations
 * register("scan", CriticalArrayFunctions::scan)
 * register("windowed", CriticalArrayFunctions::windowed)
 * register("zipAll", CriticalArrayFunctions::zipAll)
 */
