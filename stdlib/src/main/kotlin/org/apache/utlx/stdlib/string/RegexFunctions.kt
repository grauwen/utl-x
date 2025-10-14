// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/RegexFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

object RegexFunctions {
    
    fun matches(args: List<UDM>): UDM {
        requireArgs(args, 2, "matches")
        val str = args[0].asString()
        val pattern = args[1].asString()
        
        return try {
            val regex = Regex(pattern)
            UDM.Scalar(regex.matches(str))
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid regex pattern: $pattern")
        }
    }
    
    fun replaceRegex(args: List<UDM>): UDM {
        requireArgs(args, 3, "replaceRegex")
        val str = args[0].asString()
        val pattern = args[1].asString()
        val replacement = args[2].asString()
        
        return try {
            val regex = Regex(pattern)
            UDM.Scalar(regex.replace(str, replacement))
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid regex pattern: $pattern")
        }
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
}
