// stdlib/src/main/kotlin/org/apache/utlx/stdlib/objects/CriticalObjectFunctions.kt
package org.apache.utlx.stdlib.objects

import org.apache.utlx.core.udm.UDM

/**
 * Critical missing object functions
 */
object CriticalObjectFunctions {
    
    /**
     * Invert object - swap keys and values
     * 
     * Usage: invert({US: "United States", UK: "United Kingdom"})
     *        => {"United States": "US", "United Kingdom": "UK"}
     * 
     * Usage: invert({a: 1, b: 2, c: 1})
     *        => {1: "c", 2: "b"}  // Note: duplicate values keep last key
     * 
     * Essential for:
     * - Creating reverse lookup tables
     * - Code-to-name mappings
     * - Bidirectional maps
     */
    fun invert(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("invert expects 1 argument, got ${args.size}")
        }
        
        val obj = args[0]
        if (obj !is UDM.Object) {
            throw IllegalArgumentException("invert expects an object, got ${obj::class.simpleName}")
        }
        
        val inverted = mutableMapOf<String, UDM>()
        
        obj.properties.forEach { (key, value) ->
            // Convert value to string for use as key
            val newKey = when (value) {
                is UDM.Scalar -> value.value.toString()
                is UDM.Array -> "[Array]"
                is UDM.Object -> "[Object]"
            }
            
            // If duplicate values exist, last one wins
            inverted[newKey] = UDM.Scalar(key)
        }
        
        return UDM.Object(inverted, emptyMap())
    }
    
    /**
     * Deep merge objects recursively
     * 
     * Usage: 
     *   deepMerge(
     *     {a: {b: 1, c: 2}, d: 3},
     *     {a: {c: 4, e: 5}, f: 6}
     *   )
     *   => {a: {b: 1, c: 4, e: 5}, d: 3, f: 6}
     * 
     * Unlike shallow merge which overwrites nested objects entirely,
     * deepMerge recursively merges nested structures.
     * 
     * Critical for:
     * - Configuration merging
     * - Deep object updates
     * - Layered settings
     * 
     * Comparison with shallow merge:
     * 
     * Shallow merge:
     *   merge({a: {b: 1}}, {a: {c: 2}}) => {a: {c: 2}}  // Overwrites!
     * 
     * Deep merge:
     *   deepMerge({a: {b: 1}}, {a: {c: 2}}) => {a: {b: 1, c: 2}}  // Merges!
     */
    fun deepMerge(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("deepMerge expects 2 arguments, got ${args.size}")
        }
        
        val obj1 = args[0]
        val obj2 = args[1]
        
        if (obj1 !is UDM.Object || obj2 !is UDM.Object) {
            throw IllegalArgumentException("deepMerge expects two objects")
        }
        
        return deepMergeRecursive(obj1, obj2)
    }
    
    /**
     * Recursive helper for deepMerge
     */
    private fun deepMergeRecursive(obj1: UDM.Object, obj2: UDM.Object): UDM.Object {
        val result = obj1.properties.toMutableMap()
        
        obj2.properties.forEach { (key, value2) ->
            val value1 = result[key]
            
            result[key] = when {
                // If both values are objects, merge them recursively
                value1 is UDM.Object && value2 is UDM.Object -> {
                    deepMergeRecursive(value1, value2)
                }
                // If both values are arrays, concatenate them
                value1 is UDM.Array && value2 is UDM.Array -> {
                    UDM.Array(value1.elements + value2.elements)
                }
                // Otherwise, value2 overwrites value1
                else -> value2
            }
        }
        
        // Merge attributes (prefer obj2's attributes)
        val mergedAttributes = obj1.attributes.toMutableMap()
        mergedAttributes.putAll(obj2.attributes)
        
        return UDM.Object(result, mergedAttributes)
    }
    
    /**
     * Deep merge multiple objects
     * 
     * Usage: deepMergeAll([{a: 1}, {b: 2}, {c: 3}]) => {a: 1, b: 2, c: 3}
     * 
     * Convenience function for merging more than 2 objects
     */
    fun deepMergeAll(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("deepMergeAll expects 1 argument (array of objects), got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("deepMergeAll expects an array")
        }
        
        if (array.elements.isEmpty()) {
            return UDM.Object(emptyMap(), emptyMap())
        }
        
        // Start with first object
        var result = array.elements[0]
        if (result !is UDM.Object) {
            throw IllegalArgumentException("deepMergeAll expects array of objects")
        }
        
        // Merge remaining objects
        for (i in 1 until array.elements.size) {
            val next = array.elements[i]
            if (next !is UDM.Object) {
                throw IllegalArgumentException("deepMergeAll expects array of objects")
            }
            result = deepMergeRecursive(result, next)
        }
        
        return result
    }
    
    /**
     * Deep clone an object (recursive copy)
     * 
     * Usage: deepClone({a: {b: {c: 1}}}) => {a: {b: {c: 1}}}
     * 
     * Creates a completely independent copy with no shared references
     */
    fun deepClone(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("deepClone expects 1 argument, got ${args.size}")
        }
        
        return deepCloneRecursive(args[0])
    }
    
    /**
     * Recursive helper for deepClone
     */
    private fun deepCloneRecursive(value: UDM): UDM {
        return when (value) {
            is UDM.Scalar -> {
                // Scalars are immutable, safe to return as-is
                UDM.Scalar(value.value)
            }
            is UDM.Array -> {
                // Clone each element
                val clonedElements = value.elements.map { deepCloneRecursive(it) }
                UDM.Array(clonedElements)
            }
            is UDM.Object -> {
                // Clone each property
                val clonedProperties = value.properties.mapValues { (_, v) ->
                    deepCloneRecursive(v)
                }
                // Clone attributes
                val clonedAttributes = value.attributes.toMap()
                UDM.Object(clonedProperties, clonedAttributes)
            }
        }
    }
    
    /**
     * Get nested value using path
     * 
     * Usage: getPath({a: {b: {c: 1}}}, ["a", "b", "c"]) => 1
     * Usage: getPath({users: [{name: "Alice"}]}, ["users", 0, "name"]) => "Alice"
     * 
     * Returns null if path doesn't exist
     * 
     * Safer alternative to direct navigation when path might not exist
     */
    fun getPath(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("getPath expects 2 arguments (object, path), got ${args.size}")
        }
        
        var current = args[0]
        val path = args[1]
        
        if (path !is UDM.Array) {
            throw IllegalArgumentException("getPath expects array as path")
        }
        
        for (segment in path.elements) {
            current = when (current) {
                is UDM.Object -> {
                    if (segment !is UDM.Scalar) {
                        return UDM.Scalar(null)
                    }
                    val key = segment.value.toString()
                    current.properties[key] ?: return UDM.Scalar(null)
                }
                is UDM.Array -> {
                    if (segment !is UDM.Scalar || segment.value !is Number) {
                        return UDM.Scalar(null)
                    }
                    val index = (segment.value as Number).toInt()
                    if (index < 0 || index >= current.elements.size) {
                        return UDM.Scalar(null)
                    }
                    current.elements[index]
                }
                else -> return UDM.Scalar(null)
            }
        }
        
        return current
    }
    
    /**
     * Set nested value using path
     * 
     * Usage: setPath({a: {b: 1}}, ["a", "c"], 2) => {a: {b: 1, c: 2}}
     * Usage: setPath({}, ["a", "b", "c"], 1) => {a: {b: {c: 1}}}  // Creates path
     * 
     * Creates intermediate objects if they don't exist
     */
    fun setPath(args: List<UDM>): UDM {
        if (args.size != 3) {
            throw IllegalArgumentException("setPath expects 3 arguments (object, path, value), got ${args.size}")
        }
        
        val obj = args[0]
        if (obj !is UDM.Object) {
            throw IllegalArgumentException("setPath expects object as first argument")
        }
        
        val path = args[1]
        if (path !is UDM.Array) {
            throw IllegalArgumentException("setPath expects array as path")
        }
        
        val value = args[2]
        
        if (path.elements.isEmpty()) {
            return value
        }
        
        // Clone the object to avoid mutation
        return setPathRecursive(deepCloneRecursive(obj), path.elements, value)
    }
    
    /**
     * Recursive helper for setPath
     */
    private fun setPathRecursive(current: UDM, path: List<UDM>, value: UDM): UDM {
        if (path.isEmpty()) {
            return value
        }
        
        val key = path[0]
        if (key !is UDM.Scalar) {
            throw IllegalArgumentException("Path segments must be scalars")
        }
        
        val keyStr = key.value.toString()
        
        return when (current) {
            is UDM.Object -> {
                val newProperties = current.properties.toMutableMap()
                
                if (path.size == 1) {
                    // Last segment - set the value
                    newProperties[keyStr] = value
                } else {
                    // Intermediate segment - recurse
                    val next = newProperties[keyStr] ?: UDM.Object(emptyMap(), emptyMap())
                    newProperties[keyStr] = setPathRecursive(next, path.drop(1), value)
                }
                
                UDM.Object(newProperties, current.attributes)
            }
            else -> {
                throw IllegalArgumentException("Cannot set path on non-object")
            }
        }
    }
}

/**
 * Registration in Functions.kt:
 * 
 * Add these to the registerObjectFunctions() method:
 * 
 * // Critical object utilities
 * register("invert", CriticalObjectFunctions::invert)
 * register("deep-merge", CriticalObjectFunctions::deepMerge)
 * register("deep-merge-all", CriticalObjectFunctions::deepMergeAll)
 * register("deep-clone", CriticalObjectFunctions::deepClone)
 * register("get-path", CriticalObjectFunctions::getPath)
 * register("set-path", CriticalObjectFunctions::setPath)
 */
