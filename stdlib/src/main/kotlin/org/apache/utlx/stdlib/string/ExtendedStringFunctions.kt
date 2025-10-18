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
        val result = if (index >= 0) str.substring(0, index) else ""
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
        return UDM.Scalar(str.repeat(times))
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value")
    }
    
    private fun UDM.asNumber(): Double = when (this) {
        is UDM.Scalar -> {
            val v = value
            when (v) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: throw FunctionArgumentException("Cannot convert to number")
                else -> throw FunctionArgumentException("Expected number value")
            }
        }
        else -> throw FunctionArgumentException("Expected number value")
    }
}
