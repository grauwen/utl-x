// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/StringFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

/**
 * String manipulation functions for UTL-X
 */
object StringFunctions {
    
    /**
     * Convert string to uppercase
     * Usage: upper("hello") => "HELLO"
     */
    fun upper(args: List<UDM>): UDM {
        requireArgs(args, 1, "upper")
        val str = args[0].asString()
        return UDM.Scalar(str.uppercase())
    }
    
    /**
     * Convert string to lowercase
     * Usage: lower("HELLO") => "hello"
     */
    fun lower(args: List<UDM>): UDM {
        requireArgs(args, 1, "lower")
        val str = args[0].asString()
        return UDM.Scalar(str.lowercase())
    }
    
    /**
     * Trim whitespace from both ends
     * Usage: trim("  hello  ") => "hello"
     */
    fun trim(args: List<UDM>): UDM {
        requireArgs(args, 1, "trim")
        val str = args[0].asString()
        return UDM.Scalar(str.trim())
    }
    
    /**
     * Extract substring
     * Usage: substring("hello", 1, 4) => "ell"
     */
    fun substring(args: List<UDM>): UDM {
        requireArgs(args, 2..3, "substring")
        val str = args[0].asString()
        val start = args[1].asNumber().toInt()
        val end = if (args.size > 2) args[2].asNumber().toInt() else str.length
        
        return UDM.Scalar(str.substring(start, end))
    }
    
    /**
     * Concatenate strings
     * Usage: concat("hello", " ", "world") => "hello world"
     */
    fun concat(args: List<UDM>): UDM {
        if (args.isEmpty()) return UDM.Scalar("")
        val result = args.joinToString("") { it.asString() }
        return UDM.Scalar(result)
    }
    
    /**
     * Split string by delimiter
     * Usage: split("a,b,c", ",") => ["a", "b", "c"]
     */
    fun split(args: List<UDM>): UDM {
        requireArgs(args, 2, "split")
        val str = args[0].asString()
        val delimiter = args[1].asString()
        val parts = str.split(delimiter)
        return UDM.Array(parts.map { UDM.Scalar(it) })
    }
    
    /**
     * Join array elements with delimiter
     * Usage: join(["a", "b", "c"], ",") => "a,b,c"
     */
    fun join(args: List<UDM>): UDM {
        requireArgs(args, 2, "join")
        val array = args[0] as? UDM.Array 
            ?: throw FunctionArgumentException("join: first argument must be an array")
        val delimiter = args[1].asString()
        val result = array.elements.joinToString(delimiter) { it.asString() }
        return UDM.Scalar(result)
    }
    
    /**
     * Replace occurrences in string
     * Usage: replace("hello world", "world", "there") => "hello there"
     */
    fun replace(args: List<UDM>): UDM {
        requireArgs(args, 3, "replace")
        val str = args[0].asString()
        val search = args[1].asString()
        val replacement = args[2].asString()
        return UDM.Scalar(str.replace(search, replacement))
    }
    
    /**
     * Check if string contains substring
     * Usage: contains("hello world", "world") => true
     */
    fun contains(args: List<UDM>): UDM {
        requireArgs(args, 2, "contains")
        val str = args[0].asString()
        val search = args[1].asString()
        return UDM.Scalar(str.contains(search))
    }
    
    /**
     * Check if string starts with prefix
     * Usage: startsWith("hello", "he") => true
     */
    fun startsWith(args: List<UDM>): UDM {
        requireArgs(args, 2, "startsWith")
        val str = args[0].asString()
        val prefix = args[1].asString()
        return UDM.Scalar(str.startsWith(prefix))
    }
    
    /**
     * Check if string ends with suffix
     * Usage: endsWith("hello", "lo") => true
     */
    fun endsWith(args: List<UDM>): UDM {
        requireArgs(args, 2, "endsWith")
        val str = args[0].asString()
        val suffix = args[1].asString()
        return UDM.Scalar(str.endsWith(suffix))
    }
    
    /**
     * Get string length
     * Usage: length("hello") => 5
     */
    fun length(args: List<UDM>): UDM {
        requireArgs(args, 1, "length")
        val str = args[0].asString()
        return UDM.Scalar(str.length.toDouble())
    }
    
    // Helper functions
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}"
            )
        }
    }
    
    private fun requireArgs(args: List<UDM>, range: IntRange, functionName: String) {
        if (args.size !in range) {
            throw FunctionArgumentException(
                "$functionName expects ${range.first}..${range.last} arguments, got ${args.size}"
            )
        }
    }
    
    private fun UDM.asString(): String {
        return when (this) {
            is UDM.Scalar -> when (value) {
                is String -> value
                is Number -> value.toString()
                is Boolean -> value.toString()
                null -> ""
                else -> value.toString()
            }
            else -> throw FunctionArgumentException("Expected string value, got ${this::class.simpleName}")
        }
    }
    
    private fun UDM.asNumber(): Double {
        return when (this) {
            is UDM.Scalar -> when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() 
                    ?: throw FunctionArgumentException("Cannot convert '$value' to number")
                else -> throw FunctionArgumentException("Expected number value, got $value")
            }
            else -> throw FunctionArgumentException("Expected number value, got ${this::class.simpleName}")
        }
    }
}
