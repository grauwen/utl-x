// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/StringFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * String manipulation functions for UTL-X
 */
object StringFunctions {
    
    @UTLXFunction(
        description = "Convert string to uppercase",
        minArgs = 2,
        maxArgs = 2,
        category = "String",
        parameters = [
            "str: Str value",
        "start: Start value"
        ],
        returns = "Result of the operation",
        example = "upper(\"hello\") => \"HELLO\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Convert string to uppercase
     * Usage: upper("hello") => "HELLO"
     */
    fun upper(args: List<UDM>): UDM {
        requireArgs(args, 1, "upper")
        val str = args[0].asString()
        return UDM.Scalar(str.uppercase())
    }
    
    @UTLXFunction(
        description = "Convert string to lowercase",
        minArgs = 2,
        maxArgs = 2,
        category = "String",
        parameters = [
            "array: Input array to process",
        "start: Start value"
        ],
        returns = "Result of the operation",
        example = "lower(\"HELLO\") => \"hello\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Convert string to lowercase
     * Usage: lower("HELLO") => "hello"
     */
    fun lower(args: List<UDM>): UDM {
        requireArgs(args, 1, "lower")
        val str = args[0].asString()
        return UDM.Scalar(str.lowercase())
    }

    @UTLXFunction(
        description = "Convert string to title case (capitalize first letter of each word)",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: String to convert"
        ],
        returns = "Title cased string",
        example = "toTitleCase(\"john doe\") => \"John Doe\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Convert string to title case
     * Usage: toTitleCase("john doe") => "John Doe"
     */
    fun toTitleCase(args: List<UDM>): UDM {
        requireArgs(args, 1, "toTitleCase")
        val str = args[0].asString()
        val titleCased = str.split(" ")
            .joinToString(" ") { word ->
                if (word.isEmpty()) word
                else word.lowercase().replaceFirstChar { it.uppercase() }
            }
        return UDM.Scalar(titleCased)
    }

    @UTLXFunction(
        description = "Trim whitespace from both ends",
        minArgs = 2,
        maxArgs = 2,
        category = "String",
        parameters = [
            "array: Input array to process",
        "start: Start value"
        ],
        returns = "Result of the operation",
        example = "trim(\"  hello  \") => \"hello\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Trim whitespace from both ends
     * Usage: trim("  hello  ") => "hello"
     */
    fun trim(args: List<UDM>): UDM {
        requireArgs(args, 1, "trim")
        val str = args[0].asString()
        return UDM.Scalar(str.trim())
    }
    
    @UTLXFunction(
        description = "Extract substring",
        minArgs = 2,
        maxArgs = 2,
        category = "String",
        parameters = [
            "array: Input array to process",
        "start: Start value"
        ],
        returns = "Result of the operation",
        example = "substring(\"hello\", 1, 4) => \"ell\"",
        tags = ["string"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Concatenate strings",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "array: Input array to process",
        "delimiter: Delimiter value",
        "replacement: Replacement value"
        ],
        returns = "Result of the operation",
        example = "concat(\"hello\", \" \", \"world\") => \"hello world\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Concatenate strings
     * Usage: concat("hello", " ", "world") => "hello world"
     */
    fun concat(args: List<UDM>): UDM {
        if (args.isEmpty()) return UDM.Scalar("")
        val result = args.joinToString("") { it.asString() }
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Split string by delimiter",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "array: Input array to process",
        "delimiter: Delimiter value",
        "replacement: Replacement value"
        ],
        returns = "Result of the operation",
        example = "split(\"a,b,c\", \",\") => [\"a\", \"b\", \"c\"]",
        tags = ["string"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Join array elements with delimiter",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "array: Input array to process",
        "delimiter: Delimiter value",
        "replacement: Replacement value"
        ],
        returns = "Result of the operation",
        example = "join([\"a\", \"b\", \"c\"], \",\") => \"a,b,c\"",
        tags = ["string"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Replace occurrences in string",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "search: Search value",
        "replacement: Replacement value"
        ],
        returns = "Result of the operation",
        example = "replace(\"hello world\", \"world\", \"there\") => \"hello there\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Replace occurrences in string
     * Usage: replace("hello world", "world", "there") => "hello there"
     */
    fun replace(args: List<UDM>): UDM {
        requireArgs(args, 3, "replace")
        val str = args[0].asString()
        val pattern = args[1].asString()
        val replacement = args[2].asString()
        // Try as regex first, fallback to literal if regex is invalid
        return try {
            UDM.Scalar(str.replace(Regex(pattern), replacement))
        } catch (e: Exception) {
            // If regex is invalid, do literal replacement
            UDM.Scalar(str.replace(pattern, replacement))
        }
    }
    
    @UTLXFunction(
        description = "Check if string contains substring",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "search: Search value"
        ],
        returns = "Result of the operation",
        example = "contains(\"hello world\", \"world\") => true",
        tags = ["string"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Check if string starts with prefix",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "prefix: Prefix value"
        ],
        returns = "Result of the operation",
        example = "startsWith(\"hello\", \"he\") => true",
        tags = ["string"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Check if string ends with suffix",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "suffix: Suffix value"
        ],
        returns = "Result of the operation",
        example = "endsWith(\"hello\", \"lo\") => true",
        tags = ["string"],
        since = "1.0"
    )
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
    
    @UTLXFunction(
        description = "Get length of string or array",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "value: String or Array to get length of"
        ],
        returns = "Length of string or array",
        example = "length(\"hello\") => 5, length([1,2,3]) => 3",
        tags = ["string", "array"],
        since = "1.0"
    )
    /**
     * Get length of string or array (polymorphic)
     * Usage: length("hello") => 5, length([1,2,3]) => 3
     */
    fun length(args: List<UDM>): UDM {
        requireArgs(args, 1, "length")
        val value = args[0]
        return when (value) {
            is UDM.Array -> UDM.Scalar(value.elements.size.toDouble())
            is UDM.Scalar -> {
                val str = value.asString()
                UDM.Scalar(str.length.toDouble())
            }
            is UDM.Object -> UDM.Scalar(value.properties.size.toDouble())
            else -> {
                val str = value.asString()
                UDM.Scalar(str.length.toDouble())
            }
        }
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
            is UDM.Scalar -> {
                val v = value
                when (v) {
                    is String -> v
                    is Number -> v.toString()
                    is Boolean -> v.toString()
                    null -> ""
                    else -> v.toString()
                }
            }
            else -> throw FunctionArgumentException("Expected string value, got ${this::class.simpleName}")
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
