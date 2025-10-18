// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/RegexFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

object RegexFunctions {

    @UTLXFunction(
        description = "Performs matches operation",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "pattern: Pattern value",
        "replacement: Replacement value"
        ],
        returns = "Result of the operation",
        example = "matches(...) => result",
        tags = ["string"],
        since = "1.0"
    )
    
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

    @UTLXFunction(
        description = "Performs replaceRegex operation",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value",
        "pattern: Pattern value",
        "replacement: Replacement value"
        ],
        returns = "Result of the operation",
        example = "replaceRegex(...) => result",
        tags = ["string"],
        since = "1.0"
    )
    
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
