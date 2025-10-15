// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/MoreStringFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

/**
 * Additional string utility functions
 */
object MoreStringFunctions {
    
    /**
     * Remove leading whitespace only
     * Usage: leftTrim("  hello  ") => "hello  "
     */
    fun leftTrim(args: List<UDM>): UDM {
        requireArgs(args, 1, "leftTrim")
        val str = args[0].asString()
        return UDM.Scalar(str.trimStart())
    }
    
    /**
     * Remove trailing whitespace only
     * Usage: rightTrim("  hello  ") => "  hello"
     */
    fun rightTrim(args: List<UDM>): UDM {
        requireArgs(args, 1, "rightTrim")
        val str = args[0].asString()
        return UDM.Scalar(str.trimEnd())
    }
    
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
    
    /**
     * Reverse a string
     * Usage: reverse("hello") => "olleh"
     */
    fun reverse(args: List<UDM>): UDM {
        requireArgs(args, 1, "reverse")
        val str = args[0].asString()
        return UDM.Scalar(str.reversed())
    }
    
    /**
     * Check if string is empty (length 0)
     * Usage: isEmpty("") => true
     */
    fun isEmpty(args: List<UDM>): UDM {
        requireArgs(args, 1, "isEmpty")
        val str = args[0].asString()
        return UDM.Scalar(str.isEmpty())
    }
    
    /**
     * Check if string is blank (empty or only whitespace)
     * Usage: isBlank("   ") => true
     */
    fun isBlank(args: List<UDM>): UDM {
        requireArgs(args, 1, "isBlank")
        val str = args[0].asString()
        return UDM.Scalar(str.isBlank())
    }
    
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
    
    /**
     * Capitalize first letter of string
     * Usage: capitalize("hello world") => "Hello world"
     */
    fun capitalize(args: List<UDM>): UDM {
        requireArgs(args, 1, "capitalize")
        val str = args[0].asString()
        return UDM.Scalar(str.replaceFirstChar { it.uppercase() })
    }
    
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
