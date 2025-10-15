// stdlib/src/main/kotlin/org/apache/utlx/stdlib/math/AdvancedMathFunctions.kt
package org.apache.utlx.stdlib.math

import org.apache.utlx.core.udm.UDM
import kotlin.math.*

/**
 * Advanced Mathematical Functions
 * 
 * Provides trigonometric, logarithmic, and hyperbolic functions
 * to achieve parity with XSLT 3.0 and scientific computing needs.
 * 
 * Functions:
 * - Trigonometric: sin, cos, tan, asin, acos, atan, atan2
 * - Hyperbolic: sinh, cosh, tanh
 * - Logarithmic: ln, log, log10, exp
 * - Constants: PI, E, GOLDEN_RATIO
 * 
 * @since UTL-X 1.1
 */
object AdvancedMathFunctions {
    
    // ============================================
    // TRIGONOMETRIC FUNCTIONS
    // ============================================
    
    /**
     * Returns the sine of an angle in radians.
     * 
     * @param args [0] angle in radians (Number)
     * @return sine value between -1.0 and 1.0
     * 
     * Example:
     * ```
     * sin(0) → 0.0
     * sin(PI/2) → 1.0
     * sin(PI) → 0.0 (approximately)
     * ```
     */
    fun sin(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("sin() requires 1 argument: angle in radians")
        }
        val angle = args[0].asNumber()
        return UDM.Scalar(sin(angle))
    }
    
    /**
     * Returns the cosine of an angle in radians.
     * 
     * @param args [0] angle in radians (Number)
     * @return cosine value between -1.0 and 1.0
     * 
     * Example:
     * ```
     * cos(0) → 1.0
     * cos(PI) → -1.0
     * cos(PI/2) → 0.0 (approximately)
     * ```
     */
    fun cos(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("cos() requires 1 argument: angle in radians")
        }
        val angle = args[0].asNumber()
        return UDM.Scalar(cos(angle))
    }
    
    /**
     * Returns the tangent of an angle in radians.
     * 
     * @param args [0] angle in radians (Number)
     * @return tangent value
     * 
     * Example:
     * ```
     * tan(0) → 0.0
     * tan(PI/4) → 1.0
     * ```
     */
    fun tan(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("tan() requires 1 argument: angle in radians")
        }
        val angle = args[0].asNumber()
        return UDM.Scalar(tan(angle))
    }
    
    /**
     * Returns the arc sine (inverse sine) of a value.
     * 
     * @param args [0] value between -1.0 and 1.0 (Number)
     * @return angle in radians between -π/2 and π/2
     * 
     * Example:
     * ```
     * asin(0) → 0.0
     * asin(1) → π/2 (1.5707963...)
     * asin(-1) → -π/2
     * ```
     */
    fun asin(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("asin() requires 1 argument: value between -1 and 1")
        }
        val value = args[0].asNumber()
        if (value < -1.0 || value > 1.0) {
            throw IllegalArgumentException("asin() argument must be between -1 and 1, got: $value")
        }
        return UDM.Scalar(asin(value))
    }
    
    /**
     * Returns the arc cosine (inverse cosine) of a value.
     * 
     * @param args [0] value between -1.0 and 1.0 (Number)
     * @return angle in radians between 0 and π
     * 
     * Example:
     * ```
     * acos(1) → 0.0
     * acos(0) → π/2 (1.5707963...)
     * acos(-1) → π
     * ```
     */
    fun acos(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("acos() requires 1 argument: value between -1 and 1")
        }
        val value = args[0].asNumber()
        if (value < -1.0 || value > 1.0) {
            throw IllegalArgumentException("acos() argument must be between -1 and 1, got: $value")
        }
        return UDM.Scalar(acos(value))
    }
    
    /**
     * Returns the arc tangent (inverse tangent) of a value.
     * 
     * @param args [0] value (Number)
     * @return angle in radians between -π/2 and π/2
     * 
     * Example:
     * ```
     * atan(0) → 0.0
     * atan(1) → π/4 (0.785398...)
     * atan(-1) → -π/4
     * ```
     */
    fun atan(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("atan() requires 1 argument: value")
        }
        val value = args[0].asNumber()
        return UDM.Scalar(atan(value))
    }
    
    /**
     * Returns the angle theta from the conversion of rectangular 
     * coordinates (x, y) to polar coordinates (r, theta).
     * 
     * This is useful for calculating bearing/direction between two points.
     * 
     * @param args [0] y coordinate (Number)
     *             [1] x coordinate (Number)
     * @return angle in radians between -π and π
     * 
     * Example:
     * ```
     * atan2(1, 1) → π/4 (0.785398... = 45 degrees)
     * atan2(1, 0) → π/2 (90 degrees)
     * atan2(0, 1) → 0.0 (0 degrees)
     * atan2(-1, 0) → -π/2 (-90 degrees)
     * ```
     */
    fun atan2(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("atan2() requires 2 arguments: y, x")
        }
        val y = args[0].asNumber()
        val x = args[1].asNumber()
        return UDM.Scalar(atan2(y, x))
    }
    
    // ============================================
    // HYPERBOLIC FUNCTIONS
    // ============================================
    
    /**
     * Returns the hyperbolic sine of a value.
     * 
     * @param args [0] value (Number)
     * @return hyperbolic sine
     * 
     * Example:
     * ```
     * sinh(0) → 0.0
     * sinh(1) → 1.175201...
     * ```
     */
    fun sinh(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("sinh() requires 1 argument: value")
        }
        val value = args[0].asNumber()
        return UDM.Scalar(sinh(value))
    }
    
    /**
     * Returns the hyperbolic cosine of a value.
     * 
     * @param args [0] value (Number)
     * @return hyperbolic cosine
     * 
     * Example:
     * ```
     * cosh(0) → 1.0
     * cosh(1) → 1.543080...
     * ```
     */
    fun cosh(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("cosh() requires 1 argument: value")
        }
        val value = args[0].asNumber()
        return UDM.Scalar(cosh(value))
    }
    
    /**
     * Returns the hyperbolic tangent of a value.
     * 
     * @param args [0] value (Number)
     * @return hyperbolic tangent
     * 
     * Example:
     * ```
     * tanh(0) → 0.0
     * tanh(1) → 0.761594...
     * ```
     */
    fun tanh(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("tanh() requires 1 argument: value")
        }
        val value = args[0].asNumber()
        return UDM.Scalar(tanh(value))
    }
    
    // ============================================
    // LOGARITHMIC & EXPONENTIAL FUNCTIONS
    // ============================================
    
    /**
     * Returns the natural logarithm (base e) of a value.
     * 
     * @param args [0] value (must be positive)
     * @return natural logarithm
     * 
     * Example:
     * ```
     * ln(1) → 0.0
     * ln(E) → 1.0
     * ln(10) → 2.302585...
     * ```
     */
    fun ln(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("ln() requires 1 argument: positive value")
        }
        val value = args[0].asNumber()
        if (value <= 0) {
            throw IllegalArgumentException("ln() argument must be positive, got: $value")
        }
        return UDM.Scalar(ln(value))
    }
    
    /**
     * Returns the logarithm of a value with specified base.
     * If no base is provided, uses natural logarithm (base e).
     * 
     * @param args [0] value (must be positive)
     *             [1] base (optional, defaults to e)
     * @return logarithm
     * 
     * Example:
     * ```
     * log(100, 10) → 2.0
     * log(8, 2) → 3.0
     * log(E) → 1.0 (natural log)
     * ```
     */
    fun log(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("log() requires at least 1 argument: positive value")
        }
        val value = args[0].asNumber()
        if (value <= 0) {
            throw IllegalArgumentException("log() argument must be positive, got: $value")
        }
        
        val base = if (args.size > 1) {
            val b = args[1].asNumber()
            if (b <= 0 || b == 1.0) {
                throw IllegalArgumentException("log() base must be positive and not equal to 1, got: $b")
            }
            b
        } else {
            E // Natural log by default
        }
        
        return UDM.Scalar(log(value, base))
    }
    
    /**
     * Returns the base-10 logarithm of a value.
     * 
     * @param args [0] value (must be positive)
     * @return base-10 logarithm
     * 
     * Example:
     * ```
     * log10(100) → 2.0
     * log10(1000) → 3.0
     * log10(1) → 0.0
     * ```
     */
    fun log10(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("log10() requires 1 argument: positive value")
        }
        val value = args[0].asNumber()
        if (value <= 0) {
            throw IllegalArgumentException("log10() argument must be positive, got: $value")
        }
        return UDM.Scalar(log10(value))
    }
    
    /**
     * Returns e raised to the power of the given value.
     * 
     * @param args [0] exponent (Number)
     * @return e^value
     * 
     * Example:
     * ```
     * exp(0) → 1.0
     * exp(1) → 2.718281... (e)
     * exp(2) → 7.389056...
     * ```
     */
    fun exp(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("exp() requires 1 argument: exponent")
        }
        val value = args[0].asNumber()
        return UDM.Scalar(exp(value))
    }
    
    /**
     * Returns the base-2 logarithm of a value.
     * 
     * @param args [0] value (must be positive)
     * @return base-2 logarithm
     * 
     * Example:
     * ```
     * log2(8) → 3.0
     * log2(1024) → 10.0
     * log2(1) → 0.0
     * ```
     */
    fun log2(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("log2() requires 1 argument: positive value")
        }
        val value = args[0].asNumber()
        if (value <= 0) {
            throw IllegalArgumentException("log2() argument must be positive, got: $value")
        }
        return UDM.Scalar(log2(value))
    }
    
    // ============================================
    // ANGLE CONVERSION UTILITIES
    // ============================================
    
    /**
     * Converts degrees to radians.
     * 
     * @param args [0] angle in degrees
     * @return angle in radians
     * 
     * Example:
     * ```
     * toRadians(180) → π (3.14159...)
     * toRadians(90) → π/2 (1.5707...)
     * toRadians(0) → 0.0
     * ```
     */
    fun toRadians(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("toRadians() requires 1 argument: degrees")
        }
        val degrees = args[0].asNumber()
        return UDM.Scalar(Math.toRadians(degrees))
    }
    
    /**
     * Converts radians to degrees.
     * 
     * @param args [0] angle in radians
     * @return angle in degrees
     * 
     * Example:
     * ```
     * toDegrees(PI) → 180.0
     * toDegrees(PI/2) → 90.0
     * toDegrees(0) → 0.0
     * ```
     */
    fun toDegrees(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("toDegrees() requires 1 argument: radians")
        }
        val radians = args[0].asNumber()
        return UDM.Scalar(Math.toDegrees(radians))
    }
    
    // ============================================
    // MATHEMATICAL CONSTANTS
    // ============================================
    
    /**
     * Returns the mathematical constant π (pi).
     * Approximately 3.14159265358979323846
     */
    fun pi(args: List<UDM>): UDM = UDM.Scalar(PI)
    
    /**
     * Returns the mathematical constant e (Euler's number).
     * Approximately 2.71828182845904523536
     */
    fun e(args: List<UDM>): UDM = UDM.Scalar(E)
    
    /**
     * Returns the golden ratio φ (phi).
     * Approximately 1.61803398874989484820
     */
    fun goldenRatio(args: List<UDM>): UDM {
        val phi = (1.0 + sqrt(5.0)) / 2.0
        return UDM.Scalar(phi)
    }
}
