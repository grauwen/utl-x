// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/MoreStringFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Additional string utility functions
 */
object MoreStringFunctions {
    
    @UTLXFunction(
        description = "Remove leading whitespace only",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "searchChars: Searchchars value",
        "replaceChars: Replacechars value"
        ],
        returns = "Result of the operation",
        example = "leftTrim(\"  hello  \") => \"hello  \"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Remove leading whitespace only
     * Usage: leftTrim("  hello  ") => "hello  "
     */
    fun leftTrim(args: List<UDM>): UDM {
        requireArgs(args, 1, "leftTrim")
        val str = args[0].asString()
        return UDM.Scalar(str.trimStart())
    }
    
    @UTLXFunction(
        description = "Remove trailing whitespace only",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "searchChars: Searchchars value",
        "replaceChars: Replacechars value"
        ],
        returns = "Result of the operation",
        example = "rightTrim(\"  hello  \") => \"  hello\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Remove trailing whitespace only
     * Usage: rightTrim("  hello  ") => "  hello"
     */
    fun rightTrim(args: List<UDM>): UDM {
        requireArgs(args, 1, "rightTrim")
        val str = args[0].asString()
        return UDM.Scalar(str.trimEnd())
    }
    
    @UTLXFunction(
        description = "Translate characters in string (character mapping)",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "searchChars: Searchchars value",
        "replaceChars: Replacechars value"
        ],
        returns = "Result of the operation",
        example = "translate(\"hello\", \"helo\", \"HELO\") => \"HELLO\"",
        notes = """Each character in searchChars is replaced by corresponding character
in replaceChars. If replaceChars is shorter, characters are deleted.""",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Translate characters in string (character mapping)
     * Usage: translate("hello", "helo", "HELO") => "HELLO"
     * 
     * Each character in searchChars is replaced by corresponding character
     * in replaceChars. If replaceChars is shorter, characters are deleted.
     */
    fun translate(args: List<UDM>): UDM {
        requireArgs(args, 3, "translate")
        val str = args[0].asString()
        val searchChars = args[1].asString()
        val replaceChars = args[2].asString()
        
        val mapping = mutableMapOf<Char, Char?>()
        for (i in searchChars.indices) {
            val searchChar = searchChars[i]
            val replaceChar = if (i < replaceChars.length) replaceChars[i] else null
            mapping[searchChar] = replaceChar
        }
        
        val result = StringBuilder()
        for (char in str) {
            when {
                char in mapping -> mapping[char]?.let { result.append(it) }
                else -> result.append(char)
            }
        }
        
        return UDM.Scalar(result.toString())
    }
    
    @UTLXFunction(
        description = "Reverse a string",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "index: Index value"
        ],
        returns = "Result of the operation",
        example = "reverse(\"hello\") => \"olleh\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Reverse a string
     * Usage: reverse("hello") => "olleh"
     */
    fun reverse(args: List<UDM>): UDM {
        requireArgs(args, 1, "reverse")
        val str = args[0].asString()
        return UDM.Scalar(str.reversed())
    }
    
    @UTLXFunction(
        description = "Check if string is empty (length 0)",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "index: Index value"
        ],
        returns = "Boolean indicating the result",
        example = "isEmpty(\"\") => true",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Check if string is empty (length 0)
     * Usage: isEmpty("") => true
     */
    fun isEmpty(args: List<UDM>): UDM {
        requireArgs(args, 1, "isEmpty")
        val str = args[0].asString()
        return UDM.Scalar(str.isEmpty())
    }
    
    @UTLXFunction(
        description = "Check if string is blank (empty or only whitespace)",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "index: Index value"
        ],
        returns = "Boolean indicating the result",
        example = "isBlank(\"   \") => true",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Check if string is blank (empty or only whitespace)
     * Usage: isBlank("   ") => true
     */
    fun isBlank(args: List<UDM>): UDM {
        requireArgs(args, 1, "isBlank")
        val str = args[0].asString()
        return UDM.Scalar(str.isBlank())
    }
    
    @UTLXFunction(
        description = "Get character at index",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "index: Index value"
        ],
        returns = "Result of the operation",
        example = "charAt(\"hello\", 1) => \"e\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Get character at index
     * Usage: charAt("hello", 1) => "e"
     */
    fun charAt(args: List<UDM>): UDM {
        requireArgs(args, 2, "charAt")
        val str = args[0].asString()
        val index = args[1].asNumber().toInt()
        
        if (index < 0 || index >= str.length) {
            throw FunctionArgumentException("Index $index out of bounds for string of length ${str.length}")
        }
        
        return UDM.Scalar(str[index].toString())
    }
    
    @UTLXFunction(
        description = "Get character code (Unicode code point) at index",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "index: Index value"
        ],
        returns = "Result of the operation",
        example = "charCodeAt(\"A\", 0) => 65",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Get character code (Unicode code point) at index
     * Usage: charCodeAt("A", 0) => 65
     */
    fun charCodeAt(args: List<UDM>): UDM {
        requireArgs(args, 2, "charCodeAt")
        val str = args[0].asString()
        val index = args[1].asNumber().toInt()
        
        if (index < 0 || index >= str.length) {
            throw FunctionArgumentException("Index $index out of bounds for string of length ${str.length}")
        }
        
        return UDM.Scalar(str[index].code.toDouble())
    }
    
    @UTLXFunction(
        description = "Create string from character code",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "code: Code value"
        ],
        returns = "Result of the operation",
        example = "fromCharCode(65) => \"A\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Create string from character code
     * Usage: fromCharCode(65) => "A"
     */
    fun fromCharCode(args: List<UDM>): UDM {
        requireArgs(args, 1, "fromCharCode")
        val code = args[0].asNumber().toInt()
        
        if (code < 0 || code > 0x10FFFF) {
            throw FunctionArgumentException("Invalid character code: $code")
        }
        
        return UDM.Scalar(Char(code).toString())
    }
    
    @UTLXFunction(
        description = "Capitalize first letter of string",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value"
        ],
        returns = "Result of the operation",
        example = "capitalize(\"hello world\") => \"Hello world\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Capitalize first letter of string
     * Usage: capitalize("hello world") => "Hello world"
     */
    fun capitalize(args: List<UDM>): UDM {
        requireArgs(args, 1, "capitalize")
        val str = args[0].asString()
        return UDM.Scalar(str.replaceFirstChar { it.uppercase() })
    }
    
    @UTLXFunction(
        description = "Capitalize first letter of each word",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value"
        ],
        returns = "Result of the operation",
        example = "titleCase(\"hello world\") => \"Hello World\"",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Capitalize first letter of each word
     * Usage: titleCase("hello world") => "Hello World"
     */
    fun titleCase(args: List<UDM>): UDM {
        requireArgs(args, 1, "titleCase")
        val str = args[0].asString()
        val result = str.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
        return UDM.Scalar(result)
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
                is String -> v.toDoubleOrNull()
                    ?: throw FunctionArgumentException("Cannot convert '$v' to number")
                else -> throw FunctionArgumentException("Expected number value")
            }
        }
        else -> throw FunctionArgumentException("Expected number value")
    }
}
