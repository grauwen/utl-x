// stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/EnhancedArrayFunctions.kt
package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM

/**
 * Enhanced Array Functions
 * 
 * Advanced array operations to achieve parity with DataWeave's array functions.
 * Includes partition, counting, and enhanced aggregation functions.
 * 
 * Functions:
 * - partition: Split array into matching/non-matching groups
 * - countBy: Count elements matching predicate
 * - sumBy: Sum with mapping function
 * - maxBy: Find maximum by comparator
 * - minBy: Find minimum by comparator
 * - groupBy: Group elements by key function
 * - distinctBy: Get unique elements by key function
 * 
 * @since UTL-X 1.1
 */
object EnhancedArrayFunctions {
    
    /**
     * Splits an array into two groups based on a predicate function.
     * 
     * Returns an object with two keys:
     * - 'true': array of elements that matched the predicate
     * - 'false': array of elements that didn't match
     * 
     * This is useful for separating data into categories.
     * 
     * @param args [0] array to partition
     *             [1] predicate function (item) => boolean
     * @return object with 'true' and 'false' arrays
     * 
     * Example:
     * ```
     * [1, 2, 3, 4, 5] partition (x) => x > 3
     * → {true: [4, 5], false: [1, 2, 3]}
     * 
     * ["apple", "apricot", "banana"] partition (x) => startsWith(x, "a")
     * → {true: ["apple", "apricot"], false: ["banana"]}
     * 
     * [{age: 25}, {age: 35}, {age: 18}] partition (x) => x.age >= 21
     * → {true: [{age: 25}, {age: 35}], false: [{age: 18}]}
     * ```
     */
    fun partition(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("partition() requires 2 arguments: array, predicate")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("partition() first argument must be an array")
        }
        
        val predicateArg = args[1]
        // TODO: Implement function calling mechanism
        
        // Placeholder implementation
        val matching = mutableListOf<UDM>()
        val nonMatching = mutableListOf<UDM>()
        
        array.elements.forEach { element ->
            // if (predicate(element).asBoolean()) {
            //     matching.add(element)
            // } else {
            //     nonMatching.add(element)
            // }
            
            // Temporary: put all in matching
            matching.add(element)
        }
        
        return UDM.Object(mutableMapOf(
            "true" to UDM.Array(matching),
            "false" to UDM.Array(nonMatching)
        ))
    }
    
    /**
     * Counts the number of elements in an array that match a predicate.
     * 
     * This is more efficient than filtering and getting the length.
     * 
     * @param args [0] array to count
     *             [1] predicate function (item) => boolean
     * @return number of matching elements
     * 
     * Example:
     * ```
     * [1, 2, 3, 4, 5] countBy (x) => x > 3
     * → 2
     * 
     * ["apple", "apricot", "banana"] countBy (x) => startsWith(x, "a")
     * → 2
     * 
     * [{status: "active"}, {status: "inactive"}, {status: "active"}] 
     *   countBy (x) => x.status == "active"
     * → 2
     * ```
     */
    fun countBy(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("countBy() requires 2 arguments: array, predicate")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("countBy() first argument must be an array")
        }
        
        val predicateArg = args[1]
        // TODO: Implement function calling mechanism
        
        val count = array.elements.count { element ->
            // predicate(element).asBoolean()
            true // Placeholder
        }
        
        return UDM.Scalar(count)
    }
    
    /**
     * Maps each element with a function and sums the results.
     * 
     * Equivalent to: sum(map(array, mapper))
     * More efficient and expressive for calculating totals.
     * 
     * @param args [0] array to sum
     *             [1] mapper function (item) => number
     * @return sum of mapped values
     * 
     * Example:
     * ```
     * [1, 2, 3, 4] sumBy (x) => x * 2
     * → 20
     * 
     * [{qty: 2, price: 10}, {qty: 3, price: 20}] sumBy (x) => x.qty * x.price
     * → 80
     * 
     * ["hello", "world"] sumBy (x) => length(x)
     * → 10
     * ```
     */
    fun sumBy(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("sumBy() requires 2 arguments: array, mapper")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("sumBy() first argument must be an array")
        }
        
        val mapperArg = args[1]
        // TODO: Implement function calling mechanism
        
        var sum = 0.0
        array.elements.forEach { element ->
            // val value = mapper(element).asNumber()
            // sum += value
        }
        
        return UDM.Scalar(sum)
    }
    
    /**
     * Finds the element with the maximum value according to a comparator function.
     * 
     * The comparator extracts a comparable value from each element.
     * Returns null if the array is empty.
     * 
     * @param args [0] array to search
     *             [1] comparator function (item) => comparable value
     * @return element with maximum value, or null
     * 
     * Example:
     * ```
     * [{name: "Alice", age: 30}, {name: "Bob", age: 25}] maxBy (x) => x.age
     * → {name: "Alice", age: 30}
     * 
     * ["cat", "elephant", "dog"] maxBy (x) => length(x)
     * → "elephant"
     * 
     * [5, 2, 8, 1] maxBy (x) => x
     * → 8
     * 
     * [] maxBy (x) => x
     * → null
     * ```
     */
    fun maxBy(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("maxBy() requires 2 arguments: array, comparator")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("maxBy() first argument must be an array")
        }
        
        if (array.elements.isEmpty()) {
            return UDM.Null()
        }
        
        val comparatorArg = args[1]
        // TODO: Implement function calling mechanism
        
        // Placeholder: return first element
        return array.elements.firstOrNull() ?: UDM.Null()
        
        /* Actual implementation would be:
        return array.elements.maxByOrNull { element ->
            comparator(element).asNumber()
        } ?: UDM.Null()
        */
    }
    
    /**
     * Finds the element with the minimum value according to a comparator function.
     * 
     * The comparator extracts a comparable value from each element.
     * Returns null if the array is empty.
     * 
     * @param args [0] array to search
     *             [1] comparator function (item) => comparable value
     * @return element with minimum value, or null
     * 
     * Example:
     * ```
     * [{name: "Alice", age: 30}, {name: "Bob", age: 25}] minBy (x) => x.age
     * → {name: "Bob", age: 25}
     * 
     * ["cat", "elephant", "dog"] minBy (x) => length(x)
     * → "cat"
     * 
     * [5, 2, 8, 1] minBy (x) => x
     * → 1
     * 
     * [] minBy (x) => x
     * → null
     * ```
     */
    fun minBy(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("minBy() requires 2 arguments: array, comparator")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("minBy() first argument must be an array")
        }
        
        if (array.elements.isEmpty()) {
            return UDM.Null()
        }
        
        val comparatorArg = args[1]
        // TODO: Implement function calling mechanism
        
        // Placeholder: return first element
        return array.elements.firstOrNull() ?: UDM.Null()
        
        /* Actual implementation would be:
        return array.elements.minByOrNull { element ->
            comparator(element).asNumber()
        } ?: UDM.Null()
        */
    }
    
    /**
     * Groups array elements by a key function.
     * 
     * Returns an object where keys are the results of the key function
     * and values are arrays of elements with that key.
     * 
     * @param args [0] array to group
     *             [1] key function (item) => string key
     * @return object mapping keys to arrays of elements
     * 
     * Example:
     * ```
     * [1, 2, 3, 4, 5, 6] groupBy (x) => if (x % 2 == 0) "even" else "odd"
     * → {odd: [1, 3, 5], even: [2, 4, 6]}
     * 
     * [{city: "NYC", name: "Alice"}, {city: "LA", name: "Bob"}, {city: "NYC", name: "Carol"}]
     *   groupBy (x) => x.city
     * → {NYC: [{city: "NYC", name: "Alice"}, {city: "NYC", name: "Carol"}],
     *    LA: [{city: "LA", name: "Bob"}]}
     * 
     * ["apple", "apricot", "banana", "blueberry"]
     *   groupBy (x) => substring(x, 0, 1)
     * → {a: ["apple", "apricot"], b: ["banana", "blueberry"]}
     * ```
     */
    fun groupBy(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("groupBy() requires 2 arguments: array, keyFunction")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("groupBy() first argument must be an array")
        }
        
        val keyFunctionArg = args[1]
        // TODO: Implement function calling mechanism
        
        val groups = mutableMapOf<String, MutableList<UDM>>()
        
        array.elements.forEach { element ->
            // val key = keyFunction(element).asString()
            // groups.getOrPut(key) { mutableListOf() }.add(element)
            
            // Placeholder: single group
            groups.getOrPut("default") { mutableListOf() }.add(element)
        }
        
        val result = groups.mapValues { (_, elements) ->
            UDM.Array(elements)
        }
        
        return UDM.Object(result.toMutableMap())
    }
    
    /**
     * Returns unique elements based on a key function.
     * 
     * Similar to distinct(), but uses a key function to determine uniqueness.
     * First occurrence of each unique key is kept.
     * 
     * @param args [0] array to deduplicate
     *             [1] key function (item) => comparison key
     * @return array with unique elements
     * 
     * Example:
     * ```
     * [{id: 1, name: "Alice"}, {id: 2, name: "Bob"}, {id: 1, name: "Alice2"}]
     *   distinctBy (x) => x.id
     * → [{id: 1, name: "Alice"}, {id: 2, name: "Bob"}]
     * 
     * ["APPLE", "apple", "BANANA", "banana"]
     *   distinctBy (x) => lower(x)
     * → ["APPLE", "BANANA"]
     * 
     * [1.1, 1.9, 2.1, 2.9]
     *   distinctBy (x) => floor(x)
     * → [1.1, 2.1]
     * ```
     */
    fun distinctBy(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("distinctBy() requires 2 arguments: array, keyFunction")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("distinctBy() first argument must be an array")
        }
        
        val keyFunctionArg = args[1]
        // TODO: Implement function calling mechanism
        
        val seen = mutableSetOf<String>()
        val result = mutableListOf<UDM>()
        
        array.elements.forEach { element ->
            // val key = keyFunction(element).asString()
            // if (key !in seen) {
            //     seen.add(key)
            //     result.add(element)
            // }
            
            // Placeholder: keep all
            result.add(element)
        }
        
        return UDM.Array(result)
    }
    
    /**
     * Calculates the average value using a mapping function.
     * 
     * Maps each element and returns the arithmetic mean.
     * Returns null for empty arrays.
     * 
     * @param args [0] array to average
     *             [1] mapper function (item) => number
     * @return average of mapped values, or null
     * 
     * Example:
     * ```
     * [1, 2, 3, 4, 5] avgBy (x) => x
     * → 3.0
     * 
     * [{score: 80}, {score: 90}, {score: 85}] avgBy (x) => x.score
     * → 85.0
     * 
     * [] avgBy (x) => x
     * → null
     * ```
     */
    fun avgBy(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("avgBy() requires 2 arguments: array, mapper")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("avgBy() first argument must be an array")
        }
        
        if (array.elements.isEmpty()) {
            return UDM.Null()
        }
        
        val mapperArg = args[1]
        // TODO: Implement function calling mechanism
        
        var sum = 0.0
        array.elements.forEach { element ->
            // sum += mapper(element).asNumber()
        }
        
        val avg = sum / array.elements.size
        return UDM.Scalar(avg)
    }
}
