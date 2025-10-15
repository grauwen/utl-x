// stdlib/src/main/kotlin/org/apache/utlx/stdlib/type/TypeFunctions.kt
package org.apache.utlx.stdlib.type

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

object TypeFunctions {
    
    fun typeOf(args: List<UDM>): UDM {
        requireArgs(args, 1, "typeOf")
        val value = args[0]
        val typeName = when (value) {
            is UDM.Scalar -> when (value.value) {
                is String -> "string"
                is Number -> "number"
                is Boolean -> "boolean"
                null -> "null"
                else -> "unknown"
            }
            is UDM.Array -> "array"
            is UDM.Object -> "object"
            is UDM.DateTime -> "datetime"
            is UDM.Binary -> "binary"
            is UDM.Lambda -> "function"
        }
        return UDM.Scalar(typeName)
    }
    
    fun isString(args: List<UDM>): UDM {
        requireArgs(args, 1, "isString")
        val arg = args[0]
        val result = arg is UDM.Scalar && arg.value is String
        return UDM.Scalar(result)
    }
    
    fun isNumber(args: List<UDM>): UDM {
        requireArgs(args, 1, "isNumber")
        val arg = args[0]
        val result = arg is UDM.Scalar && arg.value is Number
        return UDM.Scalar(result)
    }
    
    fun isBoolean(args: List<UDM>): UDM {
        requireArgs(args, 1, "isBoolean")
        val arg = args[0]
        val result = arg is UDM.Scalar && arg.value is Boolean
        return UDM.Scalar(result)
    }
    
    fun isArray(args: List<UDM>): UDM {
        requireArgs(args, 1, "isArray")
        return UDM.Scalar(args[0] is UDM.Array)
    }
    
    fun isObject(args: List<UDM>): UDM {
        requireArgs(args, 1, "isObject")
        return UDM.Scalar(args[0] is UDM.Object)
    }
    
    fun isNull(args: List<UDM>): UDM {
        requireArgs(args, 1, "isNull")
        val arg = args[0]
        val result = arg is UDM.Scalar && arg.value == null
        return UDM.Scalar(result)
    }
    
    fun isDefined(args: List<UDM>): UDM {
        requireArgs(args, 1, "isDefined")
        val arg = args[0]
        val result = !(arg is UDM.Scalar && arg.value == null)
        return UDM.Scalar(result)
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
}
