// stdlib/src/main/kotlin/org/apache/utlx/stdlib/logical/LogicalFunctions.kt
package org.apache.utlx.stdlib.logical

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

/**
 * Logical operations for functional programming and complex boolean logic
 * 
 * These functions provide boolean operations in functional form,
 * enabling them to be used in higher-order functions and providing
 * operations (like XOR) not available as operators.
 */
object LogicalFunctions {
    
    /**
     * Logical NOT
     * Usage: not(true) => false
     * 
     * Useful for functional composition:
     * filter(items, not(isEmpty))
     */
    fun not(args: List<UDM>): UDM {
        requireArgs(args, 1, "not")
        val value = args[0].asBoolean()
        return UDM.Scalar(!value)
    }
    
    /**
     * Logical XOR (Exclusive OR)
     * Usage: xor(true, false) => true
     * 
     * Returns true if exactly one argument is true.
     * XOR truth table:
     *   false XOR false => false
     *   false XOR true  => true
     *   true  XOR false => true
     *   true  XOR true  => false
     */
    fun xor(args: List<UDM>): UDM {
        requireArgs(args, 2, "xor")
        val a = args[0].asBoolean()
        val b = args[1].asBoolean()
        
        // XOR: true if exactly one is true
        return UDM.Scalar(a != b)
    }
    
    /**
     * Logical AND (variadic)
     * Usage: and(true, true, true) => true
     * 
     * Returns true if ALL arguments are true.
     * Short-circuits on first false value.
     * 
     * Especially useful for multiple conditions:
     * and(price > 100, quantity > 5, inStock, isActive)
     */
    fun and(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            return UDM.Scalar(true) // Empty AND is true (identity)
        }
        
        // Short-circuit evaluation
        for (arg in args) {
            if (!arg.asBoolean()) {
                return UDM.Scalar(false)
            }
        }
        
        return UDM.Scalar(true)
    }
    
    /**
     * Logical OR (variadic)
     * Usage: or(false, false, true) => true
     * 
     * Returns true if ANY argument is true.
     * Short-circuits on first true value.
     * 
     * Especially useful for multiple conditions:
     * or(isVIP, isEmployee, hasDiscount, isPromoActive)
     */
    fun or(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            return UDM.Scalar(false) // Empty OR is false (identity)
        }
        
        // Short-circuit evaluation
        for (arg in args) {
            if (arg.asBoolean()) {
                return UDM.Scalar(true)
            }
        }
        
        return UDM.Scalar(false)
    }
    
    /**
     * Logical NAND (NOT AND)
     * Usage: nand(true, true) => false
     * 
     * Returns false if ALL arguments are true.
     * Equivalent to: not(and(a, b))
     * 
     * NAND is a universal gate (can create any logic circuit).
     */
    fun nand(args: List<UDM>): UDM {
        requireArgs(args, 2, "nand")
        val a = args[0].asBoolean()
        val b = args[1].asBoolean()
        
        return UDM.Scalar(!(a && b))
    }
    
    /**
     * Logical NOR (NOT OR)
     * Usage: nor(false, false) => true
     * 
     * Returns true if ALL arguments are false.
     * Equivalent to: not(or(a, b))
     * 
     * NOR is a universal gate (can create any logic circuit).
     */
    fun nor(args: List<UDM>): UDM {
        requireArgs(args, 2, "nor")
        val a = args[0].asBoolean()
        val b = args[1].asBoolean()
        
        return UDM.Scalar(!(a || b))
    }
    
    /**
     * Logical XNOR (Exclusive NOR / Equivalence)
     * Usage: xnor(true, true) => true
     * 
     * Returns true if both arguments have the same boolean value.
     * Equivalent to: not(xor(a, b)) or a == b
     * 
     * Useful for equality checks in boolean logic.
     */
    fun xnor(args: List<UDM>): UDM {
        requireArgs(args, 2, "xnor")
        val a = args[0].asBoolean()
        val b = args[1].asBoolean()
        
        return UDM.Scalar(a == b)
    }
    
    /**
     * Logical IMPLIES (Material Implication)
     * Usage: implies(true, false) => false
     * 
     * Returns false only if first argument is true and second is false.
     * Equivalent to: not(a) or b
     * 
     * Truth table:
     *   false => false => true  (false implies anything)
     *   false => true  => true  (false implies anything)
     *   true  => false => false (true doesn't imply false)
     *   true  => true  => true  (true implies true)
     */
    fun implies(args: List<UDM>): UDM {
        requireArgs(args, 2, "implies")
        val a = args[0].asBoolean()
        val b = args[1].asBoolean()
        
        return UDM.Scalar(!a || b)
    }
    
    /**
     * Check if all values in array are true
     * Usage: all([true, true, true]) => true
     * 
     * Convenient for checking array of boolean values.
     * Equivalent to: and(...array)
     */
    fun all(args: List<UDM>): UDM {
        requireArgs(args, 1, "all")
        val array = args[0].asArray()
        
        return UDM.Scalar(array.elements.all { it.asBoolean() })
    }
    
    /**
     * Check if any value in array is true
     * Usage: any([false, false, true]) => true
     * 
     * Convenient for checking array of boolean values.
     * Equivalent to: or(...array)
     */
    fun any(args: List<UDM>): UDM {
        requireArgs(args, 1, "any")
        val array = args[0].asArray()
        
        return UDM.Scalar(array.elements.any { it.asBoolean() })
    }
    
    /**
     * Check if no values in array are true (all false)
     * Usage: none([false, false, false]) => true
     * 
     * Equivalent to: not(any(array))
     */
    fun none(args: List<UDM>): UDM {
        requireArgs(args, 1, "none")
        val array = args[0].asArray()
        
        return UDM.Scalar(array.elements.none { it.asBoolean() })
    }
    
    // Helper functions
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asBoolean(): Boolean = when (this) {
        is UDM.Scalar -> when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> value.isNotEmpty() && value != "false" && value != "0"
            null -> false
            else -> true
        }
        is UDM.Array -> elements.isNotEmpty()
        is UDM.Object -> properties.isNotEmpty()
        else -> true
    }
    
    private fun UDM.asArray(): UDM.Array = when (this) {
        is UDM.Array -> this
        else -> throw FunctionArgumentException("Expected array value")
    }
}
