// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/ExtendedStringFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Extended string manipulation functions
 */
object ExtendedStringFunctions {
    
    @UTLXFunction(
        description = "Substring before first occurrence",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "delimiter: Delimiter value"
        ],
        returns = "Result of the operation",
        example = "substring-before(\"hello-world\", \"-\") => \"hello\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Substring before first occurrence
     * Usage: substring-before("hello-world", "-") => "hello"
     */
    fun substringBefore(args: List<UDM>): UDM {
        requireArgs(args, 2, "substring-before")
        val str = args[0].asString()
        val delimiter = args[1].asString()

        val index = str.indexOf(delimiter)
        // If delimiter not found, return original string (consistent with Kotlin stdlib behavior)
        val result = if (index >= 0) str.substring(0, index) else str
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Substring after first occurrence",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "delimiter: Delimiter value",
        "padChar: Padchar value"
        ],
        returns = "Result of the operation",
        example = "substring-after(\"hello-world\", \"-\") => \"world\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Substring after first occurrence
     * Usage: substring-after("hello-world", "-") => "world"
     */
    fun substringAfter(args: List<UDM>): UDM {
        requireArgs(args, 2, "substring-after")
        val str = args[0].asString()
        val delimiter = args[1].asString()
        
        val index = str.indexOf(delimiter)
        val result = if (index >= 0) str.substring(index + delimiter.length) else ""
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Substring before last occurrence",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "delimiter: Delimiter value",
        "padChar: Padchar value"
        ],
        returns = "Result of the operation",
        example = "substring-before-last(\"a/b/c.txt\", \"/\") => \"a/b\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Substring before last occurrence
     * Usage: substring-before-last("a/b/c.txt", "/") => "a/b"
     */
    fun substringBeforeLast(args: List<UDM>): UDM {
        requireArgs(args, 2, "substring-before-last")
        val str = args[0].asString()
        val delimiter = args[1].asString()
        
        val index = str.lastIndexOf(delimiter)
        val result = if (index >= 0) str.substring(0, index) else ""
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Substring after last occurrence",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "delimiter: Delimiter value",
        "padChar: Padchar value"
        ],
        returns = "Result of the operation",
        example = "substring-after-last(\"a/b/c.txt\", \"/\") => \"c.txt\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Substring after last occurrence
     * Usage: substring-after-last("a/b/c.txt", "/") => "c.txt"
     */
    fun substringAfterLast(args: List<UDM>): UDM {
        requireArgs(args, 2, "substring-after-last")
        val str = args[0].asString()
        val delimiter = args[1].asString()
        
        val index = str.lastIndexOf(delimiter)
        val result = if (index >= 0) str.substring(index + delimiter.length) else ""
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Pad string to length with character",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "length: Length value",
        "padChar: Padchar value"
        ],
        returns = "Result of the operation",
        example = "pad(\"42\", 5, \"0\") => \"00042\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Pad string to length with character
     * Usage: pad("42", 5, "0") => "00042"
     */
    fun pad(args: List<UDM>): UDM {
        requireArgs(args, 3, "pad")
        val str = args[0].asString()
        val length = args[1].asNumber().toInt()
        val padChar = args[2].asString().firstOrNull() ?: ' '

        // If length is negative or less than string length, return original string
        if (length <= 0 || length <= str.length) {
            return UDM.Scalar(str)
        }

        val result = str.padStart(length, padChar)
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Pad string on right",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "length: Length value",
        "padChar: Padchar value"
        ],
        returns = "Result of the operation",
        example = "pad-right(\"42\", 5, \"0\") => \"42000\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Pad string on right
     * Usage: pad-right("42", 5, "0") => "42000"
     */
    fun padRight(args: List<UDM>): UDM {
        requireArgs(args, 3, "pad-right")
        val str = args[0].asString()
        val length = args[1].asNumber().toInt()
        val padChar = args[2].asString().firstOrNull() ?: ' '

        // If length is negative or less than string length, return original string
        if (length <= 0 || length <= str.length) {
            return UDM.Scalar(str)
        }

        val result = str.padEnd(length, padChar)
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Normalize whitespace (collapse multiple spaces to single space)",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "times: Times value"
        ],
        returns = "Result of the operation",
        example = "normalize-space(\"hello    world\") => \"hello world\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Normalize whitespace (collapse multiple spaces to single space)
     * Usage: normalize-space("hello    world") => "hello world"
     */
    fun normalizeSpace(args: List<UDM>): UDM {
        requireArgs(args, 1, "normalize-space")
        val str = args[0].asString()
        val normalized = str.trim().replace(Regex("\\s+"), " ")
        return UDM.Scalar(normalized)
    }
    
    @UTLXFunction(
        description = "Repeat string n times",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "times: Times value"
        ],
        returns = "Result of the operation",
        example = "repeat(\"*\", 5) => \"*****\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Repeat string n times
     * Usage: repeat("*", 5) => "*****"
     */
    fun repeat(args: List<UDM>): UDM {
        requireArgs(args, 2, "repeat")
        val str = args[0].asString()
        val times = args[1].asNumber().toInt()

        // Handle negative count - return empty string
        if (times < 0) {
            return UDM.Scalar("")
        }

        return UDM.Scalar(str.repeat(times))
    }

    @UTLXFunction(
        description = "Extract substring between two delimiters",
        minArgs = 3,
        maxArgs = 3,
        category = "String",
        parameters = [
            "str: Input string",
            "startDelimiter: Start delimiter",
            "endDelimiter: End delimiter"
        ],
        returns = "Substring between delimiters, or empty string if not found",
        example = "extractBetween(\"\${ENV:-production}\", \"\${}\", \"}\") => \"ENV:-production\"",
        tags = ["string", "parsing"],
        since = "1.0"
    )
    /**
     * Extract substring between two delimiters
     * Usage: extractBetween("${ENV:-production}", "${", "}") => "ENV:-production"
     *
     * Returns empty string if delimiters not found
     */
    fun extractBetween(args: List<UDM>): UDM {
        requireArgs(args, 3, "extractBetween")
        val str = args[0].asString()
        val startDelimiter = args[1].asString()
        val endDelimiter = args[2].asString()

        // Find start delimiter
        val startIndex = str.indexOf(startDelimiter)
        if (startIndex < 0) {
            return UDM.Scalar("")
        }

        // Find end delimiter after start delimiter
        val searchStart = startIndex + startDelimiter.length
        val endIndex = str.indexOf(endDelimiter, searchStart)
        if (endIndex < 0) {
            return UDM.Scalar("")
        }

        // Extract substring between delimiters
        val result = str.substring(searchStart, endIndex)
        return UDM.Scalar(result)
    }

    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value, but got ${getTypeDescription(this)}. Hint: Use toString() to convert values to strings.")
    }
    
    private fun UDM.asNumber(): Double = when (this) {
        is UDM.Scalar -> {
            val v = value
            when (v) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: throw FunctionArgumentException("Cannot convert to number")
                else -> throw FunctionArgumentException("Expected number value, but got ${getTypeDescription(this)}. Hint: Use toNumber() to convert values to numbers.")
            }
        }
        else -> throw FunctionArgumentException("Expected number value, but got ${getTypeDescription(this)}. Hint: Use toNumber() to convert values to numbers.")
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
