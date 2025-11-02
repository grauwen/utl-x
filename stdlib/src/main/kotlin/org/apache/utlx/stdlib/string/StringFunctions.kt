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
            ?: throw FunctionArgumentException(
                "join requires an array as first argument, but got ${getTypeDescription(args[0])}. " +
                "Hint: Provide an array of values to join, e.g., [\"a\", \"b\", \"c\"]."
            )
        val delimiter = args[1].asString()
        val result = array.elements.joinToString(delimiter) { it.asString() }
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Replace occurrences in string. Supports single replacement or multiple replacements via object/array.",
        minArgs = 2,
        maxArgs = 3,
        category = "String",
        parameters = [
            "str: String value",
            "search: Search value (string) OR replacements map (object)",
            "replacement: Replacement value (string, optional if using replacements map)"
        ],
        returns = "String with replacements applied",
        example = "replace(\"hello world\", \"world\", \"there\") => \"hello there\"\nreplace(\"a\\nb\\tc\", {\"\\n\": \"\", \"\\t\": \" \"}) => \"a b c\"",
        tags = ["string"],
        since = "1.0",
        notes = "Mode 1 (single): replace(str, search, replacement)\nMode 2 (multiple): replace(str, {search1: repl1, search2: repl2, ...})\nMode 3 (multiple): replace(str, [[search1, repl1], [search2, repl2], ...])"
    )
    /**
     * Replace occurrences in string
     *
     * Supports three modes:
     * 1. Single replacement: replace("hello world", "world", "there") => "hello there"
     * 2. Multiple via object: replace("a\nb\tc", {"\n": "", "\t": " "}) => "a b c"
     * 3. Multiple via array: replace("a\nb\tc", [["\n", ""], ["\t", " "]]) => "a b c"
     */
    fun replace(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("replace() requires at least 2 arguments")
        }

        val str = args[0].asString()

        // Mode detection based on argument count and type
        return when {
            // Mode 1: replace(str, search, replacement) - single replacement
            args.size == 3 -> {
                val pattern = args[1].asString()
                val replacement = args[2].asString()
                // Try as regex first, fallback to literal if regex is invalid
                try {
                    UDM.Scalar(str.replace(Regex(pattern), replacement))
                } catch (e: Exception) {
                    // If regex is invalid, do literal replacement
                    UDM.Scalar(str.replace(pattern, replacement))
                }
            }

            // Mode 2: replace(str, {search1: repl1, search2: repl2}) - multiple via object
            args.size == 2 && args[1] is UDM.Object -> {
                val replacements = args[1] as UDM.Object
                var result = str

                // Apply each replacement in order
                replacements.properties.forEach { (search, replacement) ->
                    val replacementStr = replacement.asString()
                    result = result.replace(search, replacementStr)
                }

                UDM.Scalar(result)
            }

            // Mode 3: replace(str, [[search1, repl1], [search2, repl2]]) - multiple via array
            args.size == 2 && args[1] is UDM.Array -> {
                val replacements = args[1] as UDM.Array
                var result = str

                // Each element should be a 2-element array [search, replacement]
                replacements.elements.forEach { pair ->
                    if (pair !is UDM.Array || pair.elements.size != 2) {
                        throw IllegalArgumentException("replace() with array mode requires each element to be [search, replacement] pair")
                    }
                    val search = pair.elements[0].asString()
                    val replacement = pair.elements[1].asString()
                    result = result.replace(search, replacement)
                }

                UDM.Scalar(result)
            }

            else -> {
                throw IllegalArgumentException("replace() invalid arguments. Use: replace(str, search, replacement) OR replace(str, {search: replacement, ...}) OR replace(str, [[search, replacement], ...])")
            }
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
                "$functionName expects $expected argument(s), got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
    }

    private fun requireArgs(args: List<UDM>, range: IntRange, functionName: String) {
        if (args.size !in range) {
            throw FunctionArgumentException(
                "$functionName expects ${range.first}..${range.last} arguments, got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
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
            else -> throw FunctionArgumentException(
                "Expected string value, but got ${getTypeDescription(this)}. " +
                "Hint: Use toString() to convert values to strings."
            )
        }
    }

    private fun UDM.asNumber(): Double {
        return when (this) {
            is UDM.Scalar -> {
                val v = value
                when (v) {
                    is Number -> v.toDouble()
                    is String -> v.toDoubleOrNull()
                        ?: throw FunctionArgumentException(
                            "Cannot convert '$v' to number. " +
                            "Hint: Ensure the string contains a valid numeric value."
                        )
                    else -> throw FunctionArgumentException(
                        "Expected number value, but got ${getTypeDescription(this)}. " +
                        "Hint: Use toNumber() to convert values to numbers."
                    )
                }
            }
            else -> throw FunctionArgumentException(
                "Expected number value, but got ${getTypeDescription(this)}. " +
                "Hint: Use toNumber() to convert values to numbers."
            )
        }
    }

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
            is UDM.Date -> "date"
            is UDM.LocalDateTime -> "localdatetime"
            is UDM.Time -> "time"
            is UDM.Lambda -> "lambda"
            else -> udm.javaClass.simpleName
        }
    }
}
