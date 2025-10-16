// stdlib/src/main/kotlin/org/apache/utlx/stdlib/core/CoreFunctions.kt
package org.apache.utlx.stdlib.core

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

/**
 * Core control flow and utility functions
 */
object CoreFunctions {
    
    /**
     * Inline if-then-else conditional
     * Usage: if(price > 100, "expensive", "affordable")
     * 
     * This is CRITICAL for concise transformations
     */
    fun ifThenElse(args: List<UDM>): UDM {
        requireArgs(args, 3, "if")
        
        val condition = args[0].asBoolean()
        return if (condition) args[1] else args[2]
    }
    
    /**
     * Coalesce - return first non-null value
     * Usage: coalesce(input.email, input.contact.email, "no-email@example.com")
     */
    fun coalesce(args: List<UDM>): UDM {
        for (arg in args) {
            if (arg !is UDM.Scalar || arg.value != null) {
                return arg
            }
        }
        return UDM.Scalar(null)
    }
    
    /**
     * Generate UUID/GUID
     * Usage: generate-uuid()
     */
    fun generateUuid(args: List<UDM>): UDM {
        requireArgs(args, 0, "generate-uuid")
        return UDM.Scalar(java.util.UUID.randomUUID().toString())
    }
    
    /**
     * Default value if undefined or null
     * Usage: default(input.optional, "default-value")
     */
    fun default(args: List<UDM>): UDM {
        requireArgs(args, 2, "default")
        val value = args[0]
        return if (value is UDM.Scalar && value.value == null) {
            args[1]
        } else {
            value
        }
    }
    
    /**
     * Checks if a value is empty
     * Works with strings, arrays, objects
     * @param value The value to check
     * @return true if empty
     * 
     * Examples:
     * ```
     * isEmpty("") // true
     * isEmpty("hello") // false
     * isEmpty([]) // true
     * isEmpty([1, 2]) // false
     * isEmpty({}) // true
     * isEmpty({"name": "John"}) // false
     * isEmpty(null) // true
     * ```
     */
    fun isEmpty(args: List<UDM>): UDM {
        requireArgs(args, 1, "isEmpty")
        val value = args[0]
        
        val empty = when (value) {
            is UDM.Scalar -> {
                val v = value.value
                when (v) {
                    null -> true
                    is String -> v.isEmpty()
                    is Number -> false  // Numbers are never considered "empty"
                    is Boolean -> false // Booleans are never considered "empty"
                    else -> false
                }
            }
            is UDM.Array -> value.elements.isEmpty()
            is UDM.Object -> value.properties.isEmpty()
            is UDM.DateTime -> false // DateTime values are never considered "empty"
            is UDM.Binary -> value.data.isEmpty()
            is UDM.Lambda -> false // Functions are never considered "empty"
            else -> true
        }
        
        return UDM.Scalar(empty)
    }
    
    /**
     * Checks if a value contains an element, substring, or key
     * 
     * Works with:
     * - Strings: checks if string contains substring
     * - Arrays: checks if array contains element
     * - Objects: checks if object contains key (key existence only, not value)
     * 
     * @param collection The collection to search in
     * @param element The element/substring/key to search for
     * @return true if found, false otherwise
     * 
     * Examples:
     * ```
     * contains("hello world", "world") // true
     * contains("hello", "xyz") // false
     * contains([1, 2, 3], 2) // true
     * contains([1, 2, 3], 5) // false
     * contains({"name": "John", "age": 30}, "name") // true (key exists)
     * contains({"name": "John"}, "email") // false (key doesn't exist)
     * ```
     */
    fun contains(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw FunctionArgumentException("contains expects 2 arguments, got ${args.size}")
        }
        
        val collection = args[0]
        val element = args[1]
        
        val result = when (collection) {
            is UDM.Scalar -> {
                // String contains substring
                val str = collection.value?.toString() ?: ""
                val searchStr = (element as? UDM.Scalar)?.value?.toString() ?: ""
                str.contains(searchStr)
            }
            is UDM.Array -> {
                // Array contains element
                collection.elements.any { it == element }
            }
            is UDM.Object -> {
                // Object contains key (convention: check keys only)
                val key = (element as? UDM.Scalar)?.value?.toString() ?: ""
                collection.properties.containsKey(key)
            }
            is UDM.Binary -> {
                // Binary data - check if contains byte sequence
                if (element is UDM.Binary) {
                    val searchBytes = element.data
                    val dataBytes = collection.data
                    if (searchBytes.isEmpty()) {
                        true
                    } else {
                        // Simple byte sequence search
                        var found = false
                        for (i in 0..(dataBytes.size - searchBytes.size)) {
                            var matches = true
                            for (j in searchBytes.indices) {
                                if (dataBytes[i + j] != searchBytes[j]) {
                                    matches = false
                                    break
                                }
                            }
                            if (matches) {
                                found = true
                                break
                            }
                        }
                        found
                    }
                } else {
                    false
                }
            }
            else -> false
        }
        
        return UDM.Scalar(result)
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asBoolean(): Boolean = when (this) {
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
