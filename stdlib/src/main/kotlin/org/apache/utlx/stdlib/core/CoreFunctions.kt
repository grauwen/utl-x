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
