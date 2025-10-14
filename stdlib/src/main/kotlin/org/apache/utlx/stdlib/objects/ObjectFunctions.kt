// stdlib/src/main/kotlin/org/apache/utlx/stdlib/objects/ObjectFunctions.kt
package org.apache.utlx.stdlib.objects

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

object ObjectFunctions {
    
    fun keys(args: List<UDM>): UDM {
        requireArgs(args, 1, "keys")
        val obj = args[0].asObject()
        val keysList = obj.properties.keys.map { UDM.Scalar(it) }
        return UDM.Array(keysList)
    }
    
    fun values(args: List<UDM>): UDM {
        requireArgs(args, 1, "values")
        val obj = args[0].asObject()
        return UDM.Array(obj.properties.values.toList())
    }
    
    fun entries(args: List<UDM>): UDM {
        requireArgs(args, 1, "entries")
        val obj = args[0].asObject()
        val entriesList = obj.properties.map { (key, value) ->
            UDM.Array(listOf(UDM.Scalar(key), value))
        }
        return UDM.Array(entriesList)
    }
    
    fun merge(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            return UDM.Object(emptyMap(), emptyMap())
        }
        
        val mergedProps = mutableMapOf<String, UDM>()
        val mergedAttrs = mutableMapOf<String, String>()
        
        for (arg in args) {
            val obj = arg.asObject()
            mergedProps.putAll(obj.properties)
            mergedAttrs.putAll(obj.attributes)
        }
        
        return UDM.Object(mergedProps, mergedAttrs)
    }
    
    fun pick(args: List<UDM>): UDM {
        requireArgs(args, 2, "pick")
        val obj = args[0].asObject()
        val keys = args[1].asArray().elements.map { it.asString() }
        
        val picked = obj.properties.filterKeys { it in keys }
        return UDM.Object(picked, emptyMap())
    }
    
    fun omit(args: List<UDM>): UDM {
        requireArgs(args, 2, "omit")
        val obj = args[0].asObject()
        val keys = args[1].asArray().elements.map { it.asString() }
        
        val omitted = obj.properties.filterKeys { it !in keys }
        return UDM.Object(omitted, obj.attributes)
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asObject(): UDM.Object {
        return this as? UDM.Object ?: throw FunctionArgumentException("Expected object value")
    }
    
    private fun UDM.asArray(): UDM.Array {
        return this as? UDM.Array ?: throw FunctionArgumentException("Expected array value")
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value")
    }
}
