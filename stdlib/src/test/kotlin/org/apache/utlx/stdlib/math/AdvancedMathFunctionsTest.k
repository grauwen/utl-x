// stdlib/src/test/kotlin/org/apache/utlx/stdlib/math/AdvancedMathFunctionsTest.kt
package org.apache.utlx.stdlib.math

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.PI
import kotlin.math.E
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdvancedMathFunctionsTest {
    
    private val epsilon = 0.000001 // Tolerance for floating-point comparisons
    
    // ============================================
    // TRIGONOMETRIC FUNCTIONS TESTS
    // ============================================
    
    @Test
    fun `test sin with common angles`() {
        assertEquals(0.0, AdvancedMathFunctions.sin(listOf(UDM.Scalar(0.0))).asNumber(), epsilon)
        assertEquals(1.0, AdvancedMathFunctions.sin(listOf(UDM.Scalar(PI / 2))).asNumber(), epsilon)
        assertEquals(0.0, AdvancedMathFunctions.sin(listOf(UDM.Scalar(PI))).asNumber(), epsilon)
        assertEquals(-1.0, AdvancedMathFunctions.sin(listOf(UDM.Scalar(3 * PI / 2))).asNumber(), epsilon)
    }
    
    @Test
    fun `test cos with common angles`() {
        assertEquals(1.0, AdvancedMathFunctions.cos(listOf(UDM.Scalar(0.0))).asNumber(), epsilon)
        assertEquals(0.0, AdvancedMathFunctions.cos(listOf(UDM.Scalar(PI / 2))).asNumber(), epsilon)
        assertEquals(-1.0, AdvancedMathFunctions.cos(listOf(UDM.Scalar(PI))).asNumber(), epsilon)
    }
    
    @Test
    fun `test tan with common angles`() {
        assertEquals(0.0, AdvancedMathFunctions.tan(listOf(UDM.Scalar(0.0))).asNumber(), epsilon)
        assertEquals(1.0, AdvancedMathFunctions.tan(listOf(UDM.Scalar(PI / 4))).asNumber(), epsilon)
        assertEquals(0.0, AdvancedMathFunctions.tan(listOf(UDM.Scalar(PI))).asNumber(), epsilon)
    }
    
    @Test
    fun `test asin with valid range`() {
        assertEquals(0.0, AdvancedMathFunctions.asin(listOf(UDM.Scalar(0.0))).asNumber(), epsilon)
        assertEquals(PI / 2, AdvancedMathFunctions.asin(listOf(UDM.Scalar(1.0))).asNumber(), epsilon)
        assertEquals(-PI / 2, AdvancedMathFunctions.asin(listOf(UDM.Scalar(-1.0))).asNumber(), epsilon)
    }
    
    @Test
    fun `test asin with invalid range throws exception`() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.asin(listOf(UDM.Scalar(1.5)))
        }
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.asin(listOf(UDM.Scalar(-1.5)))
        }
    }
    
    @Test
    fun `test acos with valid range`() {
        assertEquals(PI / 2, AdvancedMathFunctions.acos(listOf(UDM.Scalar(0.0))).asNumber(), epsilon)
        assertEquals(0.0, AdvancedMathFunctions.acos(listOf(UDM.Scalar(1.0))).asNumber(), epsilon)
        assertEquals(PI, AdvancedMathFunctions.acos(listOf(UDM.Scalar(-1.0))).asNumber(), epsilon)
    }
    
    @Test
    fun `test atan with common values`() {
        assertEquals(0.0, AdvancedMathFunctions.atan(listOf(UDM.Scalar(0.0))).asNumber(), epsilon)
        assertEquals(PI / 4, AdvancedMathFunctions.atan(listOf(UDM.Scalar(1.0))).asNumber(), epsilon)
        assertEquals(-PI / 4, AdvancedMathFunctions.atan(listOf(UDM.Scalar(-1.0))).asNumber(), epsilon)
    }
    
    @Test
    fun `test atan2 with quadrants`() {
        // First quadrant
        val angle1 = AdvancedMathFunctions.atan2(listOf(UDM.Scalar(1.0), UDM.Scalar(1.0))).asNumber()
        assertEquals(PI / 4, angle1, epsilon)
        
        // Second quadrant
        val angle2 = AdvancedMathFunctions.atan2(listOf(UDM.Scalar(1.0), UDM.Scalar(-1.0))).asNumber()
        assertEquals(3 * PI / 4, angle2, epsilon)
        
        // Third quadrant
        val angle3 = AdvancedMathFunctions.atan2(listOf(UDM.Scalar(-1.0), UDM.Scalar(-1.0))).asNumber()
        assertEquals(-3 * PI / 4, angle3, epsilon)
        
        // Fourth quadrant
        val angle4 = AdvancedMathFunctions.atan2(listOf(UDM.Scalar(-1.0), UDM.Scalar(1.0))).asNumber()
        assertEquals(-PI / 4, angle4, epsilon)
    }
    
    @Test
    fun `test atan2 with axes`() {
        assertEquals(PI / 2, AdvancedMathFunctions.atan2(listOf(UDM.Scalar(1.0), UDM.Scalar(0.0))).asNumber(), epsilon)
        assertEquals(0.0, AdvancedMathFunctions.atan2(listOf(UDM.Scalar(0.0), UDM.Scalar(1.0))).asNumber(), epsilon)
        assertEquals(-PI / 2, AdvancedMathFunctions.atan2(listOf(UDM.Scalar(-1.0), UDM.Scalar(0.0))).asNumber(), epsilon)
    }
    
    // ============================================
    // HYPERBOLIC FUNCTIONS TESTS
    // ============================================
    
    @Test
    fun `test sinh with common values`() {
        assertEquals(0.0, AdvancedMathFunctions.sinh(listOf(UDM.Scalar(0.0))).asNumber(), epsilon)
        
        val sinh1 = AdvancedMathFunctions.sinh(listOf(UDM.Scalar(1.0))).asNumber()
        assertEquals(1.1752011936, sinh1, 0.0001)
    }
    
    @Test
    fun `test cosh with common values`() {
        assertEquals(1.0, AdvancedMathFunctions.cosh(listOf(UDM.Scalar(0.0))).asNumber(), epsilon)
        
        val cosh1 = AdvancedMathFunctions.cosh(listOf(UDM.Scalar(1.0))).asNumber()
        assertEquals(1.5430806348, cosh1, 0.0001)
    }
    
    @Test
    fun `test tanh with common values`() {
        assertEquals(0.0, AdvancedMathFunctions.tanh(listOf(UDM.Scalar(0.0))).asNumber(), epsilon)
        
        val tanh1 = AdvancedMathFunctions.tanh(listOf(UDM.Scalar(1.0))).asNumber()
        assertEquals(0.7615941559, tanh1, 0.0001)
    }
    
    @Test
    fun `test hyperbolic identity sinh^2 - cosh^2 = -1`() {
        val x = 2.0
        val sinhX = AdvancedMathFunctions.sinh(listOf(UDM.Scalar(x))).asNumber()
        val coshX = AdvancedMathFunctions.cosh(listOf(UDM.Scalar(x))).asNumber()
        
        val identity = coshX * coshX - sinhX * sinhX
        assertEquals(1.0, identity, epsilon)
    }
    
    // ============================================
    // LOGARITHMIC FUNCTIONS TESTS
    // ============================================
    
    @Test
    fun `test ln with common values`() {
        assertEquals(0.0, AdvancedMathFunctions.ln(listOf(UDM.Scalar(1.0))).asNumber(), epsilon)
        assertEquals(1.0, AdvancedMathFunctions.ln(listOf(UDM.Scalar(E))).asNumber(), epsilon)
        assertEquals(2.302585093, AdvancedMathFunctions.ln(listOf(UDM.Scalar(10.0))).asNumber(), 0.0001)
    }
    
    @Test
    fun `test ln with invalid input throws exception`() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.ln(listOf(UDM.Scalar(0.0)))
        }
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.ln(listOf(UDM.Scalar(-1.0)))
        }
    }
    
    @Test
    fun `test log with different bases`() {
        // log base 10
        assertEquals(2.0, AdvancedMathFunctions.log(listOf(UDM.Scalar(100.0), UDM.Scalar(10.0))).asNumber(), epsilon)
        
        // log base 2
        assertEquals(3.0, AdvancedMathFunctions.log(listOf(UDM.Scalar(8.0), UDM.Scalar(2.0))).asNumber(), epsilon)
        
        // natural log (no base specified)
        assertEquals(1.0, AdvancedMathFunctions.log(listOf(UDM.Scalar(E))).asNumber(), epsilon)
    }
    
    @Test
    fun `test log10 with powers of 10`() {
        assertEquals(0.0, AdvancedMathFunctions.log10(listOf(UDM.Scalar(1.0))).asNumber(), epsilon)
        assertEquals(1.0, AdvancedMathFunctions.log10(listOf(UDM.Scalar(10.0))).asNumber(), epsilon)
        assertEquals(2.0, AdvancedMathFunctions.log10(listOf(UDM.Scalar(100.0))).asNumber(), epsilon)
        assertEquals(3.0, AdvancedMathFunctions.log10(listOf(UDM.Scalar(1000.0))).asNumber(), epsilon)
    }
    
    @Test
    fun `test log2 with powers of 2`() {
        assertEquals(0.0, AdvancedMathFunctions.log2(listOf(UDM.Scalar(1.0))).asNumber(), epsilon)
        assertEquals(1.0, AdvancedMathFunctions.log2(listOf(UDM.Scalar(2.0))).asNumber(), epsilon)
        assertEquals(3.0, AdvancedMathFunctions.log2(listOf(UDM.Scalar(8.0))).asNumber(), epsilon)
        assertEquals(10.0, AdvancedMathFunctions.log2(listOf(UDM.Scalar(1024.0))).asNumber(), epsilon)
    }
    
    @Test
    fun `test exp with common values`() {
        assertEquals(1.0, AdvancedMathFunctions.exp(listOf(UDM.Scalar(0.0))).asNumber(), epsilon)
        assertEquals(E, AdvancedMathFunctions.exp(listOf(UDM.Scalar(1.0))).asNumber(), epsilon)
        assertEquals(7.389056099, AdvancedMathFunctions.exp(listOf(UDM.Scalar(2.0))).asNumber(), 0.0001)
    }
    
    @Test
    fun `test exp and ln are inverses`() {
        val x = 5.0
        val result = AdvancedMathFunctions.ln(
            listOf(AdvancedMathFunctions.exp(listOf(UDM.Scalar(x))))
        ).asNumber()
        assertEquals(x, result, epsilon)
    }
    
    // ============================================
    // ANGLE CONVERSION TESTS
    // ============================================
    
    @Test
    fun `test toRadians conversion`() {
        assertEquals(0.0, AdvancedMathFunctions.toRadians(listOf(UDM.Scalar(0.0))).asNumber(), epsilon)
        assertEquals(PI / 2, AdvancedMathFunctions.toRadians(listOf(UDM.Scalar(90.0))).asNumber(), epsilon)
        assertEquals(PI, AdvancedMathFunctions.toRadians(listOf(UDM.Scalar(180.0))).asNumber(), epsilon)
        assertEquals(2 * PI, AdvancedMathFunctions.toRadians(listOf(UDM.Scalar(360.0))).asNumber(), epsilon)
    }
    
    @Test
    fun `test toDegrees conversion`() {
        assertEquals(0.0, AdvancedMathFunctions.toDegrees(listOf(UDM.Scalar(0.0))).asNumber(), epsilon)
        assertEquals(90.0, AdvancedMathFunctions.toDegrees(listOf(UDM.Scalar(PI / 2))).asNumber(), epsilon)
        assertEquals(180.0, AdvancedMathFunctions.toDegrees(listOf(UDM.Scalar(PI))).asNumber(), epsilon)
        assertEquals(360.0, AdvancedMathFunctions.toDegrees(listOf(UDM.Scalar(2 * PI))).asNumber(), epsilon)
    }
    
    @Test
    fun `test angle conversion round trip`() {
        val degrees = 45.0
        val radians = AdvancedMathFunctions.toRadians(listOf(UDM.Scalar(degrees))).asNumber()
        val backToDegrees = AdvancedMathFunctions.toDegrees(listOf(UDM.Scalar(radians))).asNumber()
        assertEquals(degrees, backToDegrees, epsilon)
    }
    
    // ============================================
    // MATHEMATICAL CONSTANTS TESTS
    // ============================================
    
    @Test
    fun `test pi constant`() {
        val piValue = AdvancedMathFunctions.pi(emptyList()).asNumber()
        assertEquals(3.14159265358979, piValue, 0.00000000001)
    }
    
    @Test
    fun `test e constant`() {
        val eValue = AdvancedMathFunctions.e(emptyList()).asNumber()
        assertEquals(2.71828182845905, eValue, 0.00000000001)
    }
    
    @Test
    fun `test golden ratio constant`() {
        val phi = AdvancedMathFunctions.goldenRatio(emptyList()).asNumber()
        assertEquals(1.61803398874989, phi, 0.00000000001)
        
        // Verify golden ratio property: phi^2 = phi + 1
        assertEquals(phi * phi, phi + 1, epsilon)
    }
    
    // ============================================
    // ERROR HANDLING TESTS
    // ============================================
    
    @Test
    fun `test functions throw on missing arguments`() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.sin(emptyList())
        }
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.ln(emptyList())
        }
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.atan2(listOf(UDM.Scalar(1.0))) // needs 2 args
        }
    }
    
    @Test
    fun `test log with invalid base throws exception`() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.log(listOf(UDM.Scalar(10.0), UDM.Scalar(1.0))) // base = 1
        }
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.log(listOf(UDM.Scalar(10.0), UDM.Scalar(-2.0))) // negative base
        }
    }
}
