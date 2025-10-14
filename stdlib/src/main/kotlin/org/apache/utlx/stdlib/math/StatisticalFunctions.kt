//stdlib/src/main/kotlin/org/apache/utlx/stdlib/math/StatisticalFunctions.kt
package org.apache.utlx.stdlib.math

import org.apache.utlx.core.udm.UDM
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Statistical math functions - nice-to-have additions
 */
object StatisticalFunctions {
    
    /**
     * Calculate median (middle value)
     * 
     * Usage: median([1, 3, 5, 7, 9]) => 5
     * Usage: median([1, 2, 3, 4]) => 2.5  (average of middle two)
     * Usage: median([85, 90, 78, 92, 88]) => 88
     * 
     * Essential for statistical analysis where mean can be skewed by outliers
     */
    fun median(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("median expects 1 argument, got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("median expects an array")
        }
        
        if (array.elements.isEmpty()) {
            throw IllegalArgumentException("median requires non-empty array")
        }
        
        // Extract numbers
        val numbers = array.elements.mapNotNull { element ->
            if (element is UDM.Scalar && element.value is Number) {
                (element.value as Number).toDouble()
            } else {
                null
            }
        }
        
        if (numbers.isEmpty()) {
            throw IllegalArgumentException("median requires array of numbers")
        }
        
        // Sort the numbers
        val sorted = numbers.sorted()
        
        val median = when {
            sorted.size % 2 == 1 -> {
                // Odd number of elements - return middle
                sorted[sorted.size / 2]
            }
            else -> {
                // Even number of elements - return average of two middle
                val mid1 = sorted[sorted.size / 2 - 1]
                val mid2 = sorted[sorted.size / 2]
                (mid1 + mid2) / 2.0
            }
        }
        
        return UDM.Scalar(median)
    }
    
    /**
     * Calculate mode (most frequent value)
     * 
     * Usage: mode([1, 2, 2, 3, 3, 3, 4]) => 3
     * Usage: mode(["a", "b", "b", "c"]) => "b"
     * 
     * If multiple modes exist, returns the first one
     * Returns null for empty array
     */
    fun mode(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("mode expects 1 argument, got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("mode expects an array")
        }
        
        if (array.elements.isEmpty()) {
            return UDM.Scalar(null)
        }
        
        // Count frequencies
        val frequencies = mutableMapOf<String, Pair<Int, UDM>>()
        
        array.elements.forEach { element ->
            val key = when (element) {
                is UDM.Scalar -> element.value.toString()
                is UDM.Array -> "[Array]"
                is UDM.Object -> "[Object]"
            }
            
            val (count, _) = frequencies[key] ?: (0 to element)
            frequencies[key] = (count + 1) to element
        }
        
        // Find most frequent
        val (_, mostFrequentElement) = frequencies.values.maxByOrNull { (count, _) -> count }
            ?: return UDM.Scalar(null)
        
        return mostFrequentElement
    }
    
    /**
     * Calculate standard deviation
     * 
     * Usage: stdDev([2, 4, 4, 4, 5, 5, 7, 9]) => ~2.0
     * 
     * Measures spread of values around the mean
     * Uses sample standard deviation (n-1 denominator)
     */
    fun stdDev(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("stdDev expects 1 argument, got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("stdDev expects an array")
        }
        
        if (array.elements.size < 2) {
            throw IllegalArgumentException("stdDev requires at least 2 elements")
        }
        
        // Extract numbers
        val numbers = array.elements.mapNotNull { element ->
            if (element is UDM.Scalar && element.value is Number) {
                (element.value as Number).toDouble()
            } else {
                null
            }
        }
        
        if (numbers.size < 2) {
            throw IllegalArgumentException("stdDev requires at least 2 numbers")
        }
        
        // Calculate mean
        val mean = numbers.average()
        
        // Calculate variance (sum of squared differences from mean)
        val variance = numbers.map { (it - mean).pow(2) }.sum() / (numbers.size - 1)
        
        // Standard deviation is square root of variance
        val stdDev = sqrt(variance)
        
        return UDM.Scalar(stdDev)
    }
    
    /**
     * Calculate variance
     * 
     * Usage: variance([2, 4, 4, 4, 5, 5, 7, 9]) => ~4.0
     * 
     * Measures spread of values (standard deviation squared)
     * Uses sample variance (n-1 denominator)
     */
    fun variance(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("variance expects 1 argument, got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("variance expects an array")
        }
        
        if (array.elements.size < 2) {
            throw IllegalArgumentException("variance requires at least 2 elements")
        }
        
        // Extract numbers
        val numbers = array.elements.mapNotNull { element ->
            if (element is UDM.Scalar && element.value is Number) {
                (element.value as Number).toDouble()
            } else {
                null
            }
        }
        
        if (numbers.size < 2) {
            throw IllegalArgumentException("variance requires at least 2 numbers")
        }
        
        // Calculate mean
        val mean = numbers.average()
        
        // Calculate variance
        val variance = numbers.map { (it - mean).pow(2) }.sum() / (numbers.size - 1)
        
        return UDM.Scalar(variance)
    }
    
    /**
     * Calculate percentile
     * 
     * Usage: percentile([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 50) => 5.5  (median)
     * Usage: percentile([1, 2, 3, 4, 5], 90) => 4.6
     * 
     * Returns the value below which a percentage of data falls
     */
    fun percentile(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("percentile expects 2 arguments (array, percent), got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("percentile expects array as first argument")
        }
        
        val percent = args[1]
        if (percent !is UDM.Scalar || percent.value !is Number) {
            throw IllegalArgumentException("percentile expects number as second argument")
        }
        
        val p = (percent.value as Number).toDouble()
        
        if (p < 0 || p > 100) {
            throw IllegalArgumentException("Percentile must be between 0 and 100, got $p")
        }
        
        // Extract and sort numbers
        val numbers = array.elements.mapNotNull { element ->
            if (element is UDM.Scalar && element.value is Number) {
                (element.value as Number).toDouble()
            } else {
                null
            }
        }.sorted()
        
        if (numbers.isEmpty()) {
            throw IllegalArgumentException("percentile requires array of numbers")
        }
        
        // Calculate percentile using linear interpolation
        val rank = (p / 100.0) * (numbers.size - 1)
        val lowerIndex = rank.toInt()
        val upperIndex = (lowerIndex + 1).coerceAtMost(numbers.size - 1)
        val fraction = rank - lowerIndex
        
        val result = numbers[lowerIndex] + fraction * (numbers[upperIndex] - numbers[lowerIndex])
        
        return UDM.Scalar(result)
    }
    
    /**
     * Calculate quartiles (Q1, Q2/median, Q3)
     * 
     * Usage: quartiles([1, 2, 3, 4, 5, 6, 7, 8, 9]) => [2.5, 5, 7.5]
     * 
     * Returns array of [Q1, Q2, Q3]
     * Useful for box plots and understanding data distribution
     */
    fun quartiles(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("quartiles expects 1 argument, got ${args.size}")
        }
        
        val q1 = percentile(listOf(args[0], UDM.Scalar(25.0)))
        val q2 = percentile(listOf(args[0], UDM.Scalar(50.0)))
        val q3 = percentile(listOf(args[0], UDM.Scalar(75.0)))
        
        return UDM.Array(listOf(q1, q2, q3))
    }
    
    /**
     * Calculate interquartile range (IQR)
     * 
     * Usage: iqr([1, 2, 3, 4, 5, 6, 7, 8, 9]) => 5.0  (Q3 - Q1)
     * 
     * Measures statistical dispersion, useful for outlier detection
     */
    fun iqr(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("iqr expects 1 argument, got ${args.size}")
        }
        
        val quartiles = quartiles(args) as UDM.Array
        val q1 = (quartiles.elements[0] as UDM.Scalar).value as Number
        val q3 = (quartiles.elements[2] as UDM.Scalar).value as Number
        
        val iqr = q3.toDouble() - q1.toDouble()
        
        return UDM.Scalar(iqr)
    }
}

/**
 * Registration in Functions.kt:
 * 
 * Add these to the registerMathFunctions() method:
 * 
 * // Statistical functions
 * register("median", StatisticalFunctions::median)
 * register("mode", StatisticalFunctions::mode)
 * register("std-dev", StatisticalFunctions::stdDev)
 * register("variance", StatisticalFunctions::variance)
 * register("percentile", StatisticalFunctions::percentile)
 * register("quartiles", StatisticalFunctions::quartiles)
 * register("iqr", StatisticalFunctions::iqr)
 */
