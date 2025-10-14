// stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/Aggregations.kt
package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

/**
 * Array aggregation functions for UTL-X
 */
object Aggregations {
    
    /**
     * Sum of array elements
     * Usage: sum([1, 2, 3, 4]) => 10
     */
    fun sum(args: List<UDM>): UDM {
        requireArgs(args, 1, "sum")
        val array = args[0].asArray()
        
        val total = array.elements.sumOf { element ->
            element.asNumber()
        }
        
        return UDM.Scalar(total)
    }
    
    /**
     * Average of array elements
     * Usage: avg([1, 2, 3, 4]) => 2.5
     */
    fun avg(args: List<UDM>): UDM {
        requireArgs(args, 1, "avg")
        val array = args[0].asArray()
        
        if (array.elements.isEmpty()) {
            return UDM.Scalar(null)
        }
        
        val total = array.elements.sumOf { element ->
            element.asNumber()
        }
        
        return UDM.Scalar(total / array.elements.size)
    }
    
    /**
     * Minimum value in array
     * Usage: min([3, 1, 4, 1, 5]) => 1
     */
    fun min(args: List<UDM>): UDM {
        requireArgs(args, 1, "min")
        val array = args[0].asArray()
        
        if (array.elements.isEmpty()) {
            return UDM.Scalar(null)
        }
        
        val minValue = array.elements.minOf { element ->
            element.asNumber()
        }
        
        return UDM.Scalar(minValue)
    }
    
    /**
     * Maximum value in array
     * Usage: max([3, 1, 4, 1, 5]) => 5
     */
    fun max(args: List<UDM>): UDM {
        requireArgs(args, 1, "max")
        val array = args[0].asArray()
        
        if (array.elements.isEmpty()) {
            return UDM.Scalar(null)
        }
        
        val maxValue = array.elements.maxOf { element ->
            element.asNumber()
        }
        
        return UDM.Scalar(maxValue)
    }
    
    /**
     * Count of array elements
     * Usage: count([1, 2, 3, 4]) => 4
     */
    fun count(args: List<UDM>): UDM {
        requireArgs(args, 1, "count")
        val array = args[0].asArray()
        return UDM.Scalar(array.elements.size.toDouble())
    }
    
    // Helper functions
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}"
            )
        }
    }
    
    private fun UDM.asArray(): UDM.Array {
        return this as? UDM.Array
            ?: throw FunctionArgumentException("Expected array value, got ${this::class.simpleName}")
    }
    
    private fun UDM.asNumber(): Double {
        return when (this) {
            is UDM.Scalar -> when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                    ?: throw FunctionArgumentException("Cannot convert '$value' to number")
                else -> throw FunctionArgumentException("Expected number value, got $value")
            }
            else -> throw FunctionArgumentException("Expected number value, got ${this::class.simpleName}")
        }
    }
}
