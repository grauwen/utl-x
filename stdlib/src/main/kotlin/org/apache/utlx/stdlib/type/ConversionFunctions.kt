//stdlib/src/main/kotlin/org/apache/utlx/stdlib/type/ConversionFunctions.kt
package org.apache.utlx.stdlib.type

import org.apache.utlx.core.udm.UDM
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * CRITICAL: Type Conversion and Parsing Functions
 * 
 * These functions are essential for data transformation and are used throughout examples.
 * Without these, basic transformations cannot work properly.
 */
object ConversionFunctions {
    
    // ==================== NUMBER CONVERSIONS ====================
    
    /**
     * 
     * Usage: parseNumber("123.45") => 123.45
     * Usage: parseNumber("1,234.56") => 1234.56  (removes commas)
     * Usage: parseNumber("$99.99") => 99.99  (removes currency symbols)
     * Usage: parseNumber("not a number") => null  (safe parsing)
     * 
     * This is the PRIMARY function for converting XML/CSV string values to numbers
     */
    fun parseNumber(args: List<UDM>): UDM {
        if (args.size !in 1..2) {
            throw IllegalArgumentException("parseNumber expects 1-2 arguments, got ${args.size}")
        }
        
        val value = args[0]
        val defaultValue = if (args.size == 2) args[1] else null
        
        return when (value) {
            is UDM.Scalar -> when (val v = value.value) {
                is Number -> UDM.Scalar(v.toDouble())
                is String -> {
                    // Clean the string: remove currency symbols, commas, spaces
                    val cleaned = v.trim()
                        .replace(Regex("[,$€£¥%\\s]"), "")
                        .replace(Regex("^[+]"), "")
                    
                    try {
                        UDM.Scalar(cleaned.toDouble())
                    } catch (e: NumberFormatException) {
                        defaultValue ?: UDM.Scalar(null)
                    }
                }
                null -> defaultValue ?: UDM.Scalar(null)
                else -> defaultValue ?: UDM.Scalar(null)
            }
            else -> defaultValue ?: UDM.Scalar(null)
        }
    }
    
    /**
     * Convert value to number (alias for parseNumber, stricter)
     * 
     * Usage: toNumber("42") => 42
     * Usage: toNumber(42) => 42
     * 
     * Throws error if conversion fails (unlike parseNumber which returns null)
     */
    fun toNumber(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("toNumber expects 1 argument, got ${args.size}")
        }
        
        val result = parseNumber(args)
        if (result is UDM.Scalar && result.value == null) {
            throw IllegalArgumentException("Cannot convert to number: ${args[0]}")
        }
        
        return result
    }
    
    /**
     * Parse integer from string
     * 
     * Usage: parseInt("42") => 42
     * Usage: parseInt("42.7") => 42  (truncates)
     * Usage: parseInt("not a number") => null
     */
    fun parseInt(args: List<UDM>): UDM {
        if (args.size !in 1..2) {
            throw IllegalArgumentException("parseInt expects 1-2 arguments, got ${args.size}")
        }
        
        val numberResult = parseNumber(listOf(args[0]))
        
        return when (numberResult) {
            is UDM.Scalar -> when (val v = numberResult.value) {
                is Number -> UDM.Scalar(v.toInt().toDouble())
                null -> if (args.size == 2) args[1] else UDM.Scalar(null)
                else -> if (args.size == 2) args[1] else UDM.Scalar(null)
            }
            else -> if (args.size == 2) args[1] else UDM.Scalar(null)
        }
    }
    
    /**
     * Parse float/decimal from string (alias for parseNumber)
     */
    fun parseFloat(args: List<UDM>): UDM = parseNumber(args)
    
    /**
     * Parse double from string (alias for parseNumber)
     */
    fun parseDouble(args: List<UDM>): UDM = parseNumber(args)
    
    // ==================== STRING CONVERSIONS ====================
    
    /**
     * Convert any value to string
     * 
     * Usage: toString(42) => "42"
     * Usage: toString(true) => "true"
     * Usage: toString([1,2,3]) => "[1, 2, 3]"
     * Usage: toString(null) => ""
     */
    fun toString(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("toString expects 1 argument, got ${args.size}")
        }
        
        val value = args[0]
        
        val result = when (value) {
            is UDM.Scalar -> when (val v = value.value) {
                null -> ""
                is String -> v
                is Number -> {
                    // Format numbers nicely (no unnecessary decimals)
                    val d = v.toDouble()
                    if (d == d.toLong().toDouble()) {
                        d.toLong().toString()
                    } else {
                        d.toString()
                    }
                }
                is Boolean -> v.toString()
                else -> v.toString()
            }
            is UDM.Array -> {
                val elements = value.elements.map { 
                    (toString(listOf(it)) as UDM.Scalar).value 
                }
                "[${elements.joinToString(", ")}]"
            }
            is UDM.Object -> {
                val props = value.properties.entries.joinToString(", ") { (k, v) ->
                    "$k: ${(toString(listOf(v)) as UDM.Scalar).value}"
                }
                "{$props}"
            }
        }
        
        return UDM.Scalar(result)
    }
    
    // ==================== BOOLEAN CONVERSIONS ====================
    
    /**
     * Parse boolean from string or number
     * 
     * Usage: parseBoolean("true") => true
     * Usage: parseBoolean("yes") => true
     * Usage: parseBoolean("1") => true
     * Usage: parseBoolean(1) => true
     * Usage: parseBoolean("false") => false
     * Usage: parseBoolean("no") => false
     * Usage: parseBoolean("0") => false
     * Usage: parseBoolean(0) => false
     * Usage: parseBoolean("other") => null
     */
    fun parseBoolean(args: List<UDM>): UDM {
        if (args.size !in 1..2) {
            throw IllegalArgumentException("parseBoolean expects 1-2 arguments, got ${args.size}")
        }
        
        val value = args[0]
        val defaultValue = if (args.size == 2) args[1] else null
        
        val result = when (value) {
            is UDM.Scalar -> when (val v = value.value) {
                is Boolean -> v
                is Number -> v.toDouble() != 0.0
                is String -> when (v.trim().lowercase()) {
                    "true", "yes", "y", "1", "on", "enabled" -> true
                    "false", "no", "n", "0", "off", "disabled" -> false
                    else -> return defaultValue ?: UDM.Scalar(null)
                }
                null -> return defaultValue ?: UDM.Scalar(null)
                else -> return defaultValue ?: UDM.Scalar(null)
            }
            else -> return defaultValue ?: UDM.Scalar(null)
        }
        
        return UDM.Scalar(result)
    }
    
    /**
     * Convert value to boolean (stricter than parseBoolean)
     * 
     * Usage: toBoolean(1) => true
     * Usage: toBoolean(0) => false
     * Usage: toBoolean("true") => true
     * 
     * Throws error if conversion fails
     */
    fun toBoolean(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("toBoolean expects 1 argument, got ${args.size}")
        }
        
        val result = parseBoolean(args)
        if (result is UDM.Scalar && result.value == null) {
            throw IllegalArgumentException("Cannot convert to boolean: ${args[0]}")
        }
        
        return result
    }
    
    // ==================== DATE CONVERSIONS ====================
    
    /**
     * Parse date from string with optional format
     * 
     * Usage: parseDate("2025-10-15") => Date
     * Usage: parseDate("10/15/2025", "MM/dd/yyyy") => Date
     * Usage: parseDate("2025-10-15T14:30:00Z") => DateTime
     * Usage: parseDate("invalid") => null
     */
    fun parseDate(args: List<UDM>): UDM {
        if (args.size !in 1..3) {
            throw IllegalArgumentException("parseDate expects 1-3 arguments, got ${args.size}")
        }
        
        val value = args[0]
        if (value !is UDM.Scalar || value.value !is String) {
            return if (args.size == 3) args[2] else UDM.Scalar(null)
        }
        
        val dateStr = value.value as String
        
        // If format provided, use it
        if (args.size >= 2) {
            val format = args[1]
            if (format !is UDM.Scalar || format.value !is String) {
                throw IllegalArgumentException("parseDate format must be a string")
            }
            
            return try {
                val formatter = DateTimeFormatter.ofPattern(format.value as String)
                val date = LocalDate.parse(dateStr, formatter)
                // Convert to UDM - you may need to adjust based on your UDM date representation
                UDM.Scalar(date.toString())
            } catch (e: DateTimeParseException) {
                if (args.size == 3) args[2] else UDM.Scalar(null)
            }
        }
        
        // Try common ISO formats
        return try {
            // Try ISO instant format (with time)
            val instant = try {
                Instant.parse(dateStr)
            } catch (e: DateTimeParseException) {
                // Try local date format
                val localDate = LocalDate.parse(dateStr)
                localDate.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant()
            }
            
            UDM.Scalar(instant.toString())
        } catch (e: DateTimeParseException) {
            if (args.size == 3) args[2] else UDM.Scalar(null)
        }
    }
    
    // ==================== ARRAY/OBJECT CONVERSIONS ====================
    
    /**
     * Convert value to array
     * 
     * Usage: toArray(42) => [42]
     * Usage: toArray([1,2,3]) => [1,2,3]
     * Usage: toArray("hello") => ["hello"]
     */
    fun toArray(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("toArray expects 1 argument, got ${args.size}")
        }
        
        val value = args[0]
        
        return when (value) {
            is UDM.Array -> value
            is UDM.Object -> {
                // Convert object to array of [key, value] pairs
                val pairs = value.properties.map { (key, v) ->
                    UDM.Array(listOf(UDM.Scalar(key), v))
                }
                UDM.Array(pairs)
            }
            else -> UDM.Array(listOf(value))
        }
    }
    
    /**
     * Try to convert value to object
     * 
     * Usage: toObject([["a", 1], ["b", 2]]) => {a: 1, b: 2}
     */
    fun toObject(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("toObject expects 1 argument, got ${args.size}")
        }
        
        val value = args[0]
        
        return when (value) {
            is UDM.Object -> value
            is UDM.Array -> {
                // Convert array of [key, value] pairs to object
                val properties = mutableMapOf<String, UDM>()
                
                for (element in value.elements) {
                    if (element is UDM.Array && element.elements.size == 2) {
                        val key = element.elements[0]
                        val v = element.elements[1]
                        
                        if (key is UDM.Scalar) {
                            properties[key.value.toString()] = v
                        }
                    }
                }
                
                UDM.Object(properties, emptyMap())
            }
            else -> throw IllegalArgumentException("Cannot convert to object: ${value::class.simpleName}")
        }
    }
    
    // ==================== SAFE CONVERSION WITH DEFAULTS ====================
    
    /**
     * Safely convert to number with default
     * 
     * Usage: numberOrDefault("123", 0) => 123
     * Usage: numberOrDefault("invalid", 0) => 0
     */
    fun numberOrDefault(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("numberOrDefault expects 2 arguments, got ${args.size}")
        }
        
        return parseNumber(args)
    }
    
    /**
     * Safely convert to string with default
     * 
     * Usage: stringOrDefault(null, "N/A") => "N/A"
     */
    fun stringOrDefault(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("stringOrDefault expects 2 arguments, got ${args.size}")
        }
        
        val value = args[0]
        val default = args[1]
        
        if (value is UDM.Scalar && value.value == null) {
            return default
        }
        
        return toString(listOf(value))
    }
}

/**
 * Registration in Functions.kt:
 * 
 * Add these to a new registerConversionFunctions() method:
 * 
 * private fun registerConversionFunctions() {
 *     // CRITICAL: Number parsing (used in examples!)
 *     register("parseNumber", ConversionFunctions::parseNumber)
 *     register("toNumber", ConversionFunctions::toNumber)
 *     register("parseInt", ConversionFunctions::parseInt)
 *     register("parseFloat", ConversionFunctions::parseFloat)
 *     register("parseDouble", ConversionFunctions::parseDouble)
 *     
 *     // String conversion
 *     register("toString", ConversionFunctions::toString)
 *     
 *     // Boolean conversion
 *     register("parseBoolean", ConversionFunctions::parseBoolean)
 *     register("toBoolean", ConversionFunctions::toBoolean)
 *     
 *     // Date conversion
 *     register("parseDate", ConversionFunctions::parseDate)
 *     
 *     // Collection conversion
 *     register("toArray", ConversionFunctions::toArray)
 *     register("toObject", ConversionFunctions::toObject)
 *     
 *     // Safe conversion with defaults
 *     register("numberOrDefault", ConversionFunctions::numberOrDefault)
 *     register("stringOrDefault", ConversionFunctions::stringOrDefault)
 * }
 * 
 * Then call this in the init block:
 * init {
 *     registerConversionFunctions()  // ADD THIS!
 *     registerStringFunctions()
 *     registerArrayFunctions()
 *     // ... rest
 * }
 */
