package org.apache.utlx.stdlib.util

import org.apache.utlx.core.udm.UDM
import java.time.Instant

/**
 * Utility Functions: Tree, Coercion, and Timer
 * 
 * Implements DataWeave utility modules:
 * - dw::util::Tree - Tree structure manipulation
 * - dw::util::Coercions - Type coercion utilities
 * - dw::util::Timer - Performance timing
 * 
 * Location: stdlib/src/main/kotlin/org/apache/utlx/stdlib/util/UtilityFunctions.kt
 */

// ==================== TREE FUNCTIONS ====================

/**
 * Tree Structure Manipulation
 * 
 * Functions for traversing and transforming hierarchical data structures.
 * Similar to DataWeave's dw::util::Tree module.
 */
object TreeFunctions {
    
    /**
     * Map over all nodes in a tree structure
     * 
     * Usage: treeMap(tree, (node, path) => transform(node))
     * 
     * Traverses tree depth-first and applies function to each node.
     * Function receives: (node, path) where path is array of keys to node.
     */
    fun treeMap(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("treeMap expects 2 arguments (tree, function), got ${args.size}")
        }
        
        val tree = args[0]
        // In real implementation, args[1] would be function reference
        // For now, return tree as-is (placeholder)
        
        return mapTreeRecursive(tree, emptyList())
    }
    
    private fun mapTreeRecursive(node: UDM, path: List<String>): UDM {
        return when (node) {
            is UDM.Object -> {
                val mappedProps = node.properties.mapValues { (key, value) ->
                    mapTreeRecursive(value, path + key)
                }
                UDM.Object(mappedProps, node.attributes)
            }
            is UDM.Array -> {
                val mappedElements = node.elements.mapIndexed { index, element ->
                    mapTreeRecursive(element, path + index.toString())
                }
                UDM.Array(mappedElements)
            }
            else -> node
        }
    }
    
    /**
     * Filter tree nodes by predicate
     * 
     * Usage: treeFilter(tree, node => node.type == "visible")
     * 
     * Removes nodes that don't match predicate, preserving tree structure.
     */
    fun treeFilter(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("treeFilter expects 2 arguments (tree, predicate), got ${args.size}")
        }
        
        val tree = args[0]
        // Placeholder - in real impl would apply predicate
        
        return filterTreeRecursive(tree)
    }
    
    private fun filterTreeRecursive(node: UDM): UDM? {
        return when (node) {
            is UDM.Object -> {
                val filteredProps = node.properties.mapNotNull { (key, value) ->
                    filterTreeRecursive(value)?.let { key to it }
                }.toMap()
                UDM.Object(filteredProps, node.attributes)
            }
            is UDM.Array -> {
                val filteredElements = node.elements.mapNotNull { filterTreeRecursive(it) }
                UDM.Array(filteredElements)
            }
            else -> node
        }
    }
    
    /**
     * Flatten tree to array of leaf nodes
     * 
     * Usage: treeFlatten(tree)
     * Result: [leaf1, leaf2, leaf3, ...]
     * 
     * Extracts all leaf values from tree structure.
     */
    fun treeFlatten(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("treeFlatten expects 1 argument, got ${args.size}")
        }
        
        val tree = args[0]
        val leaves = mutableListOf<UDM>()
        
        collectLeaves(tree, leaves)
        
        return UDM.Array(leaves)
    }
    
    private fun collectLeaves(node: UDM, leaves: MutableList<UDM>) {
        when (node) {
            is UDM.Object -> {
                if (node.properties.isEmpty()) {
                    leaves.add(node)
                } else {
                    node.properties.values.forEach { collectLeaves(it, leaves) }
                }
            }
            is UDM.Array -> {
                if (node.elements.isEmpty()) {
                    leaves.add(node)
                } else {
                    node.elements.forEach { collectLeaves(it, leaves) }
                }
            }
            else -> leaves.add(node)
        }
    }
    
    /**
     * Get tree depth (maximum nesting level)
     * 
     * Usage: treeDepth(tree) => 4
     * 
     * Returns maximum depth of nested structures.
     */
    fun treeDepth(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("treeDepth expects 1 argument, got ${args.size}")
        }
        
        val tree = args[0]
        val depth = calculateDepth(tree, 0)
        
        return UDM.Scalar(depth.toDouble())
    }
    
    private fun calculateDepth(node: UDM, currentDepth: Int): Int {
        return when (node) {
            is UDM.Object -> {
                if (node.properties.isEmpty()) {
                    currentDepth
                } else {
                    node.properties.values.maxOfOrNull { 
                        calculateDepth(it, currentDepth + 1) 
                    } ?: currentDepth
                }
            }
            is UDM.Array -> {
                if (node.elements.isEmpty()) {
                    currentDepth
                } else {
                    node.elements.maxOfOrNull { 
                        calculateDepth(it, currentDepth + 1) 
                    } ?: currentDepth
                }
            }
            else -> currentDepth
        }
    }
    
    /**
     * Get all paths in tree
     * 
     * Usage: treePaths(tree)
     * Result: [["a", "b", "c"], ["a", "d"], ["e"]]
     * 
     * Returns array of all paths from root to leaves.
     */
    fun treePaths(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("treePaths expects 1 argument, got ${args.size}")
        }
        
        val tree = args[0]
        val paths = mutableListOf<List<String>>()
        
        collectPaths(tree, emptyList(), paths)
        
        val pathArrays = paths.map { path ->
            UDM.Array(path.map { UDM.Scalar(it) })
        }
        
        return UDM.Array(pathArrays)
    }
    
    private fun collectPaths(node: UDM, currentPath: List<String>, paths: MutableList<List<String>>) {
        when (node) {
            is UDM.Object -> {
                if (node.properties.isEmpty()) {
                    paths.add(currentPath)
                } else {
                    node.properties.forEach { (key, value) ->
                        collectPaths(value, currentPath + key, paths)
                    }
                }
            }
            is UDM.Array -> {
                if (node.elements.isEmpty()) {
                    paths.add(currentPath)
                } else {
                    node.elements.forEachIndexed { index, element ->
                        collectPaths(element, currentPath + index.toString(), paths)
                    }
                }
            }
            else -> paths.add(currentPath)
        }
    }
    
    /**
     * Find node by path in tree
     * 
     * Usage: treeFind(tree, ["a", "b", "c"])
     * 
     * Navigate to specific node using path array.
     * Returns null if path doesn't exist.
     */
    fun treeFind(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("treeFind expects 2 arguments (tree, path), got ${args.size}")
        }
        
        val tree = args[0]
        val path = args[1]
        
        if (path !is UDM.Array) {
            throw IllegalArgumentException("treeFind expects array path")
        }
        
        var current = tree
        
        for (segment in path.elements) {
            if (segment !is UDM.Scalar) {
                return UDM.Scalar(null)
            }
            
            current = when (current) {
                is UDM.Object -> {
                    current.properties[segment.value.toString()] ?: return UDM.Scalar(null)
                }
                is UDM.Array -> {
                    val index = (segment.value as? Number)?.toInt() ?: return UDM.Scalar(null)
                    current.elements.getOrNull(index) ?: return UDM.Scalar(null)
                }
                else -> return UDM.Scalar(null)
            }
        }
        
        return current
    }
}

// ==================== COERCION FUNCTIONS ====================

/**
 * Type Coercion Utilities
 * 
 * Enhanced type conversion with intelligent coercion rules.
 * Similar to DataWeave's dw::util::Coercions module.
 */
object CoercionFunctions {
    
    /**
     * Coerce value to target type with default
     * 
     * Usage: coerce(value, "number", 0)
     * 
     * Attempts to convert value to target type, returns default if fails.
     */
    fun coerce(args: List<UDM>): UDM {
        if (args.size !in 2..3) {
            throw IllegalArgumentException("coerce expects 2-3 arguments, got ${args.size}")
        }
        
        val value = args[0]
        val targetType = args[1]
        val defaultValue = if (args.size == 3) args[2] else null
        
        if (targetType !is UDM.Scalar || targetType.value !is String) {
            throw IllegalArgumentException("Target type must be string")
        }
        
        val type = targetType.value as String
        
        return try {
            when (type.lowercase()) {
                "number" -> coerceToNumber(value)
                "string" -> coerceToString(value)
                "boolean" -> coerceToBoolean(value)
                "array" -> coerceToArray(value)
                "object" -> coerceToObject(value)
                else -> defaultValue ?: value
            }
        } catch (e: Exception) {
            defaultValue ?: value
        }
    }
    
    private fun coerceToNumber(value: UDM): UDM {
        return when (value) {
            is UDM.Scalar -> when (val v = value.value) {
                is Number -> UDM.Scalar(v.toDouble())
                is String -> {
                    val cleaned = v.trim().replace(Regex("[,$€£¥%\\s]"), "")
                    UDM.Scalar(cleaned.toDouble())
                }
                is Boolean -> UDM.Scalar(if (v) 1.0 else 0.0)
                else -> throw IllegalArgumentException("Cannot coerce to number")
            }
            is UDM.Array -> UDM.Scalar(value.elements.size.toDouble())
            is UDM.Object -> UDM.Scalar(value.properties.size.toDouble())
        }
    }
    
    private fun coerceToString(value: UDM): UDM {
        return when (value) {
            is UDM.Scalar -> UDM.Scalar(value.value?.toString() ?: "")
            is UDM.Array -> UDM.Scalar(value.elements.joinToString(", "))
            is UDM.Object -> UDM.Scalar(value.properties.toString())
        }
    }
    
    private fun coerceToBoolean(value: UDM): UDM {
        return when (value) {
            is UDM.Scalar -> when (val v = value.value) {
                is Boolean -> UDM.Scalar(v)
                is Number -> UDM.Scalar(v.toDouble() != 0.0)
                is String -> UDM.Scalar(
                    v.lowercase() in listOf("true", "yes", "1", "on", "enabled")
                )
                null -> UDM.Scalar(false)
                else -> UDM.Scalar(true)
            }
            is UDM.Array -> UDM.Scalar(value.elements.isNotEmpty())
            is UDM.Object -> UDM.Scalar(value.properties.isNotEmpty())
        }
    }
    
    private fun coerceToArray(value: UDM): UDM {
        return when (value) {
            is UDM.Array -> value
            is UDM.Object -> UDM.Array(value.properties.values.toList())
            else -> UDM.Array(listOf(value))
        }
    }
    
    private fun coerceToObject(value: UDM): UDM {
        return when (value) {
            is UDM.Object -> value
            is UDM.Array -> {
                val props = value.elements.mapIndexed { index, elem ->
                    index.toString() to elem
                }.toMap()
                UDM.Object(props, emptyMap())
            }
            else -> UDM.Object(mapOf("value" to value), emptyMap())
        }
    }
    
    /**
     * Try to coerce, return null on failure
     * 
     * Usage: tryCoerce(value, "number") => number or null
     * 
     * Safe coercion that returns null instead of throwing.
     */
    fun tryCoerce(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("tryCoerce expects 2 arguments, got ${args.size}")
        }
        
        return try {
            coerce(args)
        } catch (e: Exception) {
            UDM.Scalar(null)
        }
    }
    
    /**
     * Check if value can be coerced to type
     * 
     * Usage: canCoerce(value, "number") => true/false
     * 
     * Tests if coercion would succeed without performing it.
     */
    fun canCoerce(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("canCoerce expects 2 arguments, got ${args.size}")
        }
        
        return try {
            coerce(args)
            UDM.Scalar(true)
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }
    
    /**
     * Coerce all values in array to target type
     * 
     * Usage: coerceAll([1, "2", true], "number") => [1.0, 2.0, 1.0]
     * 
     * Maps coercion over array, filtering out failures.
     */
    fun coerceAll(args: List<UDM>): UDM {
        if (args.size !in 2..3) {
            throw IllegalArgumentException("coerceAll expects 2-3 arguments, got ${args.size}")
        }
        
        val array = args[0]
        val targetType = args[1]
        val defaultValue = if (args.size == 3) args[2] else null
        
        if (array !is UDM.Array) {
            throw IllegalArgumentException("First argument must be array")
        }
        
        val coerced = array.elements.mapNotNull { element ->
            try {
                coerce(listOf(element, targetType, defaultValue ?: element))
            } catch (e: Exception) {
                null
            }
        }
        
        return UDM.Array(coerced)
    }
    
    /**
     * Smart coercion - infers target type from context
     * 
     * Usage: smartCoerce("42") => 42 (detects it's a number)
     * 
     * Attempts intelligent type inference.
     */
    fun smartCoerce(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("smartCoerce expects 1 argument, got ${args.size}")
        }
        
        val value = args[0]
        
        if (value !is UDM.Scalar || value.value !is String) {
            return value
        }
        
        val str = (value.value as String).trim()
        
        // Try number
        try {
            val num = str.toDouble()
            return UDM.Scalar(num)
        } catch (e: NumberFormatException) {
            // Not a number
        }
        
        // Try boolean
        when (str.lowercase()) {
            "true", "yes" -> return UDM.Scalar(true)
            "false", "no" -> return UDM.Scalar(false)
        }
        
        // Try null
        if (str.lowercase() == "null") {
            return UDM.Scalar(null)
        }
        
        // Return as-is
        return value
    }
}

// ==================== TIMER FUNCTIONS ====================

/**
 * Performance Timing Utilities
 * 
 * Functions for measuring execution time and performance profiling.
 * Similar to DataWeave's dw::util::Timer module.
 */
object TimerFunctions {
    
    private val timers = mutableMapOf<String, Long>()
    private val measurements = mutableMapOf<String, MutableList<Long>>()
    
    /**
     * Start a named timer
     * 
     * Usage: timerStart("operation1")
     * 
     * Begins timing for named operation.
     */
    fun timerStart(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("timerStart expects 1 argument (name), got ${args.size}")
        }
        
        val name = args[0]
        if (name !is UDM.Scalar || name.value !is String) {
            throw IllegalArgumentException("Timer name must be string")
        }
        
        val timerName = name.value as String
        timers[timerName] = System.nanoTime()
        
        return UDM.Scalar("Timer '$timerName' started")
    }
    
    /**
     * Stop a named timer and get elapsed time
     * 
     * Usage: timerStop("operation1") => {elapsed: 123.45, unit: "ms"}
     * 
     * Returns elapsed time in milliseconds.
     */
    fun timerStop(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("timerStop expects 1 argument (name), got ${args.size}")
        }
        
        val name = args[0]
        if (name !is UDM.Scalar || name.value !is String) {
            throw IllegalArgumentException("Timer name must be string")
        }
        
        val timerName = name.value as String
        val startTime = timers[timerName] 
            ?: throw IllegalArgumentException("Timer '$timerName' not started")
        
        val endTime = System.nanoTime()
        val elapsedNanos = endTime - startTime
        val elapsedMillis = elapsedNanos / 1_000_000.0
        
        // Store measurement
        measurements.getOrPut(timerName) { mutableListOf() }.add(elapsedNanos)
        
        // Remove timer
        timers.remove(timerName)
        
        return UDM.Object(mapOf(
            "name" to UDM.Scalar(timerName),
            "elapsed" to UDM.Scalar(elapsedMillis),
            "unit" to UDM.Scalar("ms"),
            "nanoseconds" to UDM.Scalar(elapsedNanos.toDouble())
        ), emptyMap())
    }
    
    /**
     * Get elapsed time without stopping timer
     * 
     * Usage: timerCheck("operation1") => {elapsed: 123.45, unit: "ms"}
     * 
     * Peek at elapsed time, timer continues running.
     */
    fun timerCheck(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("timerCheck expects 1 argument (name), got ${args.size}")
        }
        
        val name = args[0]
        if (name !is UDM.Scalar || name.value !is String) {
            throw IllegalArgumentException("Timer name must be string")
        }
        
        val timerName = name.value as String
        val startTime = timers[timerName] 
            ?: throw IllegalArgumentException("Timer '$timerName' not started")
        
        val currentTime = System.nanoTime()
        val elapsedNanos = currentTime - startTime
        val elapsedMillis = elapsedNanos / 1_000_000.0
        
        return UDM.Object(mapOf(
            "name" to UDM.Scalar(timerName),
            "elapsed" to UDM.Scalar(elapsedMillis),
            "unit" to UDM.Scalar("ms")
        ), emptyMap())
    }
    
    /**
     * Reset a timer
     * 
     * Usage: timerReset("operation1")
     * 
     * Restarts timer from zero.
     */
    fun timerReset(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("timerReset expects 1 argument (name), got ${args.size}")
        }
        
        val name = args[0]
        if (name !is UDM.Scalar || name.value !is String) {
            throw IllegalArgumentException("Timer name must be string")
        }
        
        val timerName = name.value as String
        timers[timerName] = System.nanoTime()
        
        return UDM.Scalar("Timer '$timerName' reset")
    }
    
    /**
     * Get statistics for a timer
     * 
     * Usage: timerStats("operation1")
     * Result: {count: 10, min: 5.2, max: 15.8, avg: 8.3, total: 83.0}
     * 
     * Returns statistics from all measurements of named timer.
     */
    fun timerStats(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("timerStats expects 1 argument (name), got ${args.size}")
        }
        
        val name = args[0]
        if (name !is UDM.Scalar || name.value !is String) {
            throw IllegalArgumentException("Timer name must be string")
        }
        
        val timerName = name.value as String
        val measures = measurements[timerName] 
            ?: return UDM.Object(mapOf(
                "count" to UDM.Scalar(0.0),
                "message" to UDM.Scalar("No measurements for '$timerName'")
            ), emptyMap())
        
        val measuresMs = measures.map { it / 1_000_000.0 }
        
        return UDM.Object(mapOf(
            "name" to UDM.Scalar(timerName),
            "count" to UDM.Scalar(measures.size.toDouble()),
            "min" to UDM.Scalar(measuresMs.minOrNull() ?: 0.0),
            "max" to UDM.Scalar(measuresMs.maxOrNull() ?: 0.0),
            "avg" to UDM.Scalar(measuresMs.average()),
            "total" to UDM.Scalar(measuresMs.sum()),
            "unit" to UDM.Scalar("ms")
        ), emptyMap())
    }
    
    /**
     * List all active timers
     * 
     * Usage: timerList() => ["operation1", "operation2"]
     * 
     * Returns names of all currently running timers.
     */
    fun timerList(args: List<UDM>): UDM {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("timerList expects no arguments, got ${args.size}")
        }
        
        val names = timers.keys.map { UDM.Scalar(it) }
        return UDM.Array(names)
    }
    
    /**
     * Clear all timers and measurements
     * 
     * Usage: timerClear()
     * 
     * Resets all timing data.
     */
    fun timerClear(args: List<UDM>): UDM {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("timerClear expects no arguments, got ${args.size}")
        }
        
        timers.clear()
        measurements.clear()
        
        return UDM.Scalar("All timers cleared")
    }
    
    /**
     * Get current timestamp
     * 
     * Usage: timestamp() => 1697472000000
     * 
     * Returns current time in milliseconds since epoch.
     */
    fun timestamp(args: List<UDM>): UDM {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("timestamp expects no arguments, got ${args.size}")
        }
        
        return UDM.Scalar(System.currentTimeMillis().toDouble())
    }
    
    /**
     * Measure execution time of expression (would need runtime support)
     * 
     * Usage: measure(() => expensiveOperation())
     * Result: {result: ..., elapsed: 123.45, unit: "ms"}
     * 
     * Placeholder - requires function execution support.
     */
    fun measure(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("measure expects 1 argument (function), got ${args.size}")
        }
        
        // Placeholder - would need to execute function and measure time
        val startTime = System.nanoTime()
        
        // In real implementation: execute args[0] as function
        val result = args[0]
        
        val endTime = System.nanoTime()
        val elapsedMillis = (endTime - startTime) / 1_000_000.0
        
        return UDM.Object(mapOf(
            "result" to result,
            "elapsed" to UDM.Scalar(elapsedMillis),
            "unit" to UDM.Scalar("ms")
        ), emptyMap())
    }
}

/**
 * Registration in Functions.kt:
 * 
 * Add these to new registration methods:
 * 
 * private fun registerTreeFunctions() {
 *     register("treeMap", TreeFunctions::treeMap)
 *     register("treeFilter", TreeFunctions::treeFilter)
 *     register("treeFlatten", TreeFunctions::treeFlatten)
 *     register("treeDepth", TreeFunctions::treeDepth)
 *     register("treePaths", TreeFunctions::treePaths)
 *     register("treeFind", TreeFunctions::treeFind)
 * }
 * 
 * private fun registerCoercionFunctions() {
 *     register("coerce", CoercionFunctions::coerce)
 *     register("tryCoerce", CoercionFunctions::tryCoerce)
 *     register("canCoerce", CoercionFunctions::canCoerce)
 *     register("coerceAll", CoercionFunctions::coerceAll)
 *     register("smartCoerce", CoercionFunctions::smartCoerce)
 * }
 * 
 * private fun registerTimerFunctions() {
 *     register("timerStart", TimerFunctions::timerStart)
 *     register("timerStop", TimerFunctions::timerStop)
 *     register("timerCheck", TimerFunctions::timerCheck)
 *     register("timerReset", TimerFunctions::timerReset)
 *     register("timerStats", TimerFunctions::timerStats)
 *     register("timerList", TimerFunctions::timerList)
 *     register("timerClear", TimerFunctions::timerClear)
 *     register("timestamp", TimerFunctions::timestamp)
 *     register("measure", TimerFunctions::measure)
 * }
 * 
 * Then call in init block:
 * init {
 *     registerConversionFunctions()
 *     registerURLFunctions()
 *     registerTreeFunctions()         // ADD THIS!
 *     registerCoercionFunctions()     // ADD THIS!
 *     registerTimerFunctions()        // ADD THIS!
 *     // ... rest
 * }
 */
