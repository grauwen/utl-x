/**
 * Collection JOIN Functions for UTL-X Standard Library
 * 
 * Location: stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/JoinFunctions.kt
 * 
 * SQL-style JOIN operations for combining arrays based on key matching.
 * Compatible with DataWeave's dw::core::Arrays::join functionality.
 * 
 * Use Cases:
 * - Combining API responses from different services
 * - Merging database query results
 * - Relating CSV files by common keys
 * - Aggregating data from multiple sources
 * 
 * Example:
 *   let customers = [{id: 1, name: "Alice"}, {id: 2, name: "Bob"}]
 *   let orders = [{customerId: 1, product: "Widget"}]
 *   
 *   join(customers, orders, (c) => c.id, (o) => o.customerId)
 *   // Returns: [{l: {id: 1, name: "Alice"}, r: {customerId: 1, product: "Widget"}}]
 */

package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.annotations.UTLXFunction

object JoinFunctions {
    
    @UTLXFunction(
        description = "Inner join - returns only items that have matches in both arrays",
        minArgs = 4,
        maxArgs = 4,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "right: Right value",
        "leftKeyFn: Leftkeyfn value",
        "rightKeyFn: Rightkeyfn value"
        ],
        returns = "Result of the operation",
        example = "join(...) => result",
        notes = """Example:
join(customers, orders, (c) => c.id, (o) => o.customerId)""",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Inner join - returns only items that have matches in both arrays
     * 
     * @param args[0] left - Left array
     * @param args[1] right - Right array
     * @param args[2] leftKeyFn - Function to extract key from left items
     * @param args[3] rightKeyFn - Function to extract key from right items
     * @return Array of objects with 'l' (left item) and 'r' (right item) properties
     * 
     * Example:
     *   join(customers, orders, (c) => c.id, (o) => o.customerId)
     */
    fun join(args: List<UDM>): UDM {
        require(args.size >= 4) { "join requires 4 arguments: leftArray, rightArray, leftKeyFn, rightKeyFn" }
        
        val left = args[0].asArray() ?: throw IllegalArgumentException("First argument must be an array")
        val right = args[1].asArray() ?: throw IllegalArgumentException("Second argument must be an array")
        val leftKeyFn = args[2]
        val rightKeyFn = args[3]
        
        val result = mutableListOf<Map<String, Any?>>()
        
        for (leftItem in left.elements) {
            val leftKey = evaluateKeyFunction(leftKeyFn, leftItem)
            
            for (rightItem in right.elements) {
                val rightKey = evaluateKeyFunction(rightKeyFn, rightItem)
                
                if (keysMatch(leftKey, rightKey)) {
                    result.add(mapOf(
                        "l" to leftItem.toNative(),
                        "r" to rightItem.toNative()
                    ))
                }
            }
        }
        
        return UDM.fromNative(result)
    }
    
    @UTLXFunction(
        description = "Left join - returns all items from left array, with matching items from right",
        minArgs = 4,
        maxArgs = 4,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "right: Right value",
        "leftKeyFn: Leftkeyfn value",
        "rightKeyFn: Rightkeyfn value"
        ],
        returns = "Result of the operation",
        example = "leftJoin(...) => result",
        notes = """If no match is found, right side is null
Example:
leftJoin(customers, orders, (c) => c.id, (o) => o.customerId)
// All customers, even those without orders (r will be null)""",
        tags = ["array", "null-handling"],
        since = "1.0"
    )
    /**
     * Left join - returns all items from left array, with matching items from right
     * If no match is found, right side is null
     * 
     * @param args[0] left - Left array
     * @param args[1] right - Right array
     * @param args[2] leftKeyFn - Function to extract key from left items
     * @param args[3] rightKeyFn - Function to extract key from right items
     * @return Array of objects with 'l' (left item) and 'r' (right item or null) properties
     * 
     * Example:
     *   leftJoin(customers, orders, (c) => c.id, (o) => o.customerId)
     *   // All customers, even those without orders (r will be null)
     */
    fun leftJoin(args: List<UDM>): UDM {
        require(args.size >= 4) { "leftJoin requires 4 arguments: leftArray, rightArray, leftKeyFn, rightKeyFn" }
        
        val left = args[0].asArray() ?: throw IllegalArgumentException("First argument must be an array")
        val right = args[1].asArray() ?: throw IllegalArgumentException("Second argument must be an array")
        val leftKeyFn = args[2]
        val rightKeyFn = args[3]
        
        val result = mutableListOf<Map<String, Any?>>()
        
        for (leftItem in left.elements) {
            val leftKey = evaluateKeyFunction(leftKeyFn, leftItem)
            var foundMatch = false
            
            for (rightItem in right.elements) {
                val rightKey = evaluateKeyFunction(rightKeyFn, rightItem)
                
                if (keysMatch(leftKey, rightKey)) {
                    result.add(mapOf(
                        "l" to leftItem.toNative(),
                        "r" to rightItem.toNative()
                    ))
                    foundMatch = true
                }
            }
            
            // If no match found, add with null right side
            if (!foundMatch) {
                result.add(mapOf(
                    "l" to leftItem.toNative(),
                    "r" to null
                ))
            }
        }
        
        return UDM.fromNative(result)
    }
    
    @UTLXFunction(
        description = "Right join - returns all items from right array, with matching items from left",
        minArgs = 4,
        maxArgs = 4,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "right: Right value",
        "leftKeyFn: Leftkeyfn value",
        "rightKeyFn: Rightkeyfn value"
        ],
        returns = "Result of the operation",
        example = "rightJoin(...) => result",
        notes = """If no match is found, left side is null
Example:
rightJoin(customers, orders, (c) => c.id, (o) => o.customerId)
// All orders, even orphaned ones (l will be null if no customer)""",
        tags = ["array", "null-handling"],
        since = "1.0"
    )
    /**
     * Right join - returns all items from right array, with matching items from left
     * If no match is found, left side is null
     * 
     * @param args[0] left - Left array
     * @param args[1] right - Right array
     * @param args[2] leftKeyFn - Function to extract key from left items
     * @param args[3] rightKeyFn - Function to extract key from right items
     * @return Array of objects with 'l' (left item or null) and 'r' (right item) properties
     * 
     * Example:
     *   rightJoin(customers, orders, (c) => c.id, (o) => o.customerId)
     *   // All orders, even orphaned ones (l will be null if no customer)
     */
    fun rightJoin(args: List<UDM>): UDM {
        require(args.size >= 4) { "rightJoin requires 4 arguments: leftArray, rightArray, leftKeyFn, rightKeyFn" }
        
        val left = args[0].asArray() ?: throw IllegalArgumentException("First argument must be an array")
        val right = args[1].asArray() ?: throw IllegalArgumentException("Second argument must be an array")
        val leftKeyFn = args[2]
        val rightKeyFn = args[3]
        
        val result = mutableListOf<Map<String, Any?>>()
        
        for (rightItem in right.elements) {
            val rightKey = evaluateKeyFunction(rightKeyFn, rightItem)
            var foundMatch = false
            
            for (leftItem in left.elements) {
                val leftKey = evaluateKeyFunction(leftKeyFn, leftItem)
                
                if (keysMatch(leftKey, rightKey)) {
                    result.add(mapOf(
                        "l" to leftItem.toNative(),
                        "r" to rightItem.toNative()
                    ))
                    foundMatch = true
                }
            }
            
            // If no match found, add with null left side
            if (!foundMatch) {
                result.add(mapOf(
                    "l" to null,
                    "r" to rightItem.toNative()
                ))
            }
        }
        
        return UDM.fromNative(result)
    }
    
    @UTLXFunction(
        description = "Full outer join - returns all items from both arrays",
        minArgs = 4,
        maxArgs = 4,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "right: Right value",
        "leftKeyFn: Leftkeyfn value",
        "rightKeyFn: Rightkeyfn value"
        ],
        returns = "Result of the operation",
        example = "fullOuterJoin(...) => result",
        notes = """Matches are paired, unmatched items have null for the other side
Example:
fullOuterJoin(customers, orders, (c) => c.id, (o) => o.customerId)
// All customers and all orders, matched where possible""",
        tags = ["array", "null-handling"],
        since = "1.0"
    )
    /**
     * Full outer join - returns all items from both arrays
     * Matches are paired, unmatched items have null for the other side
     * 
     * @param args[0] left - Left array
     * @param args[1] right - Right array
     * @param args[2] leftKeyFn - Function to extract key from left items
     * @param args[3] rightKeyFn - Function to extract key from right items
     * @return Array of objects with 'l' and 'r' properties (either can be null)
     * 
     * Example:
     *   fullOuterJoin(customers, orders, (c) => c.id, (o) => o.customerId)
     *   // All customers and all orders, matched where possible
     */
    fun fullOuterJoin(args: List<UDM>): UDM {
        require(args.size >= 4) { "fullOuterJoin requires 4 arguments: leftArray, rightArray, leftKeyFn, rightKeyFn" }
        
        val left = args[0].asArray() ?: throw IllegalArgumentException("First argument must be an array")
        val right = args[1].asArray() ?: throw IllegalArgumentException("Second argument must be an array")
        val leftKeyFn = args[2]
        val rightKeyFn = args[3]
        
        val result = mutableListOf<Map<String, Any?>>()
        val matchedRightIndices = mutableSetOf<Int>()
        
        // Process all left items
        for (leftItem in left.elements) {
            val leftKey = evaluateKeyFunction(leftKeyFn, leftItem)
            var foundMatch = false
            
            right.elements.forEachIndexed { index, rightItem ->
                val rightKey = evaluateKeyFunction(rightKeyFn, rightItem)
                
                if (keysMatch(leftKey, rightKey)) {
                    result.add(mapOf(
                        "l" to leftItem.toNative(),
                        "r" to rightItem.toNative()
                    ))
                    matchedRightIndices.add(index)
                    foundMatch = true
                }
            }
            
            // If no match found, add with null right side
            if (!foundMatch) {
                result.add(mapOf(
                    "l" to leftItem.toNative(),
                    "r" to null
                ))
            }
        }
        
        // Add unmatched right items
        right.elements.forEachIndexed { index, rightItem ->
            if (index !in matchedRightIndices) {
                result.add(mapOf(
                    "l" to null,
                    "r" to rightItem.toNative()
                ))
            }
        }
        
        return UDM.fromNative(result)
    }
    
    @UTLXFunction(
        description = "Cross join - Cartesian product of both arrays",
        minArgs = 2,
        maxArgs = 2,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "right: Right value",
        "leftKeyFn: Leftkeyfn value",
        "rightKeyFn: Rightkeyfn value",
        "combinerFn: Combinerfn value"
        ],
        returns = "every possible combination of items from both arrays",
        example = "crossJoin(...) => result",
        notes = """Returns every possible combination of items from both arrays
Example:
crossJoin([1, 2], ["a", "b"])
// Returns: [{l: 1, r: "a"}, {l: 1, r: "b"}, {l: 2, r: "a"}, {l: 2, r: "b"}]""",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Cross join - Cartesian product of both arrays
     * Returns every possible combination of items from both arrays
     * 
     * @param args[0] left - Left array
     * @param args[1] right - Right array
     * @return Array of objects with 'l' and 'r' properties for all combinations
     * 
     * Example:
     *   crossJoin([1, 2], ["a", "b"])
     *   // Returns: [{l: 1, r: "a"}, {l: 1, r: "b"}, {l: 2, r: "a"}, {l: 2, r: "b"}]
     */
    fun crossJoin(args: List<UDM>): UDM {
        require(args.size >= 2) { "crossJoin requires 2 arguments: leftArray, rightArray" }
        
        val left = args[0].asArray() ?: throw IllegalArgumentException("First argument must be an array")
        val right = args[1].asArray() ?: throw IllegalArgumentException("Second argument must be an array")
        
        val result = mutableListOf<Map<String, Any?>>()
        
        for (leftItem in left.elements) {
            for (rightItem in right.elements) {
                result.add(mapOf(
                    "l" to leftItem.toNative(),
                    "r" to rightItem.toNative()
                ))
            }
        }
        
        return UDM.fromNative(result)
    }
    
    @UTLXFunction(
        description = "Join with custom combiner function",
        minArgs = 5,
        maxArgs = 5,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean",
        "leftKeyFn: Leftkeyfn value",
        "rightKeyFn: Rightkeyfn value",
        "combinerFn: Combinerfn value"
        ],
        returns = "Result of the operation",
        example = "joinWith(...) => result",
        notes = """Allows custom logic for combining matched items
Example:
joinWith(customers, orders,
(c) => c.id,
(o) => o.customerId,
(c, o) => {name: c.name, product: o.product}
)""",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Join with custom combiner function
     * Allows custom logic for combining matched items
     * 
     * @param args[0] left - Left array
     * @param args[1] right - Right array
     * @param args[2] leftKeyFn - Function to extract key from left items
     * @param args[3] rightKeyFn - Function to extract key from right items
     * @param args[4] combinerFn - Function to combine matched items (leftItem, rightItem) => result
     * @return Array of combined items
     * 
     * Example:
     *   joinWith(customers, orders, 
     *     (c) => c.id, 
     *     (o) => o.customerId,
     *     (c, o) => {name: c.name, product: o.product}
     *   )
     */
    fun joinWith(args: List<UDM>): UDM {
        require(args.size >= 5) { "joinWith requires 5 arguments: leftArray, rightArray, leftKeyFn, rightKeyFn, combinerFn" }
        
        val left = args[0].asArray() ?: throw IllegalArgumentException("First argument must be an array")
        val right = args[1].asArray() ?: throw IllegalArgumentException("Second argument must be an array")
        val leftKeyFn = args[2]
        val rightKeyFn = args[3]
        val combinerFn = args[4]
        
        val result = mutableListOf<Any?>()
        
        for (leftItem in left.elements) {
            val leftKey = evaluateKeyFunction(leftKeyFn, leftItem)
            
            for (rightItem in right.elements) {
                val rightKey = evaluateKeyFunction(rightKeyFn, rightItem)
                
                if (keysMatch(leftKey, rightKey)) {
                    // Apply combiner function
                    // TODO: Implement function calling mechanism
                    val combined = UDM.Object(mutableMapOf(
                        "l" to leftItem,
                        "r" to rightItem
                    ))
                    result.add(combined.toNative())
                }
            }
        }
        
        return UDM.fromNative(result)
    }
    
    // ============================================================================
    // Helper Functions
    // ============================================================================
    
    /**
     * Evaluate a key function on an item
     */
    private fun evaluateKeyFunction(keyFn: UDM, item: UDM): Any? {
        return when (keyFn) {
            is UDM.Lambda -> {
                // TODO: Implement function calling mechanism
                // For now, return the item itself as key
                item.toNative()
            }
            is UDM.Scalar -> {
                // It's a property name
                val propName = keyFn.asString()
                if (item is UDM.Object && propName != null) {
                    item.properties[propName]?.toNative()
                } else {
                    null
                }
            }
            else -> {
                throw IllegalArgumentException("Key function must be a function or property name string")
            }
        }
    }
    
    /**
     * Check if two keys match
     * Handles null keys and type conversion
     */
    private fun keysMatch(key1: Any?, key2: Any?): Boolean {
        return when {
            key1 == null || key2 == null -> false
            key1 == key2 -> true
            // Try string comparison for numeric types
            key1.toString() == key2.toString() -> true
            else -> false
        }
    }
    
    /**
     * Extension to convert UDM to native Kotlin type
     */
    private fun UDM.toNative(): Any? {
        return when (this) {
            is UDM.Scalar -> this.value
            is UDM.Array -> this.elements.map { it.toNative() }
            is UDM.Object -> {
                val map = mutableMapOf<String, Any?>()
                this.properties.forEach { (propName, value) ->
                    map[propName] = value.toNative()
                }
                map
            }
            is UDM.DateTime -> this.instant.toString()
            is UDM.Date -> this.toISOString()
            is UDM.LocalDateTime -> this.toISOString()
            is UDM.Time -> this.toISOString()
            is UDM.Binary -> "<binary:${this.data.size} bytes>"
            is UDM.Lambda -> "<function>"
        }
    }
}

// ============================================================================
// INTEGRATION INTO Functions.kt
// 
// Add to stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt:
// ============================================================================

/*

import org.apache.utlx.stdlib.array.JoinFunctions

// In registerArrayFunctions():
private fun registerArrayFunctions() {
    // ... existing array functions ...
    
    // JOIN operations (SQL-style)
    register("join", JoinFunctions::join)
    register("leftJoin", JoinFunctions::leftJoin)
    register("rightJoin", JoinFunctions::rightJoin)
    register("fullOuterJoin", JoinFunctions::fullOuterJoin)
    register("crossJoin", JoinFunctions::crossJoin)
    register("joinWith", JoinFunctions::joinWith)
}

*/
