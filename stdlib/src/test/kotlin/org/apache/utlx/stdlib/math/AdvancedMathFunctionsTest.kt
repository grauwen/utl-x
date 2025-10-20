// stdlib/src/test/kotlin/org/apache/utlx/stdlib/math/AdvancedMathFunctionsTest.kt
package org.apache.utlx.stdlib.math

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdvancedMathFunctionsTest {

    private val epsilon = 1e-10 // Tolerance for floating point comparisons

    // Helper function to assert floating point equality with tolerance
    private fun assertEqualsWithTolerance(expected: Double, actual: Double, tolerance: Double = epsilon) {
        assertTrue(abs(expected - actual) < tolerance, 
            "Expected $expected but was $actual (difference: ${abs(expected - actual)})")
    }

    // ========== TRIGONOMETRIC FUNCTIONS ==========

    @Test
    fun testSin() {
        val result = AdvancedMathFunctions.sin(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double)
    }

    @Test
    fun testSinPiOver2() {
        val result = AdvancedMathFunctions.sin(listOf(UDM.Scalar(PI / 2)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(1.0, result.value as Double)
    }

    @Test
    fun testSinPi() {
        val result = AdvancedMathFunctions.sin(listOf(UDM.Scalar(PI)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double)
    }

    @Test
    fun testSinNegative() {
        val result = AdvancedMathFunctions.sin(listOf(UDM.Scalar(-PI / 2)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(-1.0, result.value as Double)
    }

    @Test
    fun testCos() {
        val result = AdvancedMathFunctions.cos(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(1.0, result.value as Double)
    }

    @Test
    fun testCosPi() {
        val result = AdvancedMathFunctions.cos(listOf(UDM.Scalar(PI)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(-1.0, result.value as Double)
    }

    @Test
    fun testCosPiOver2() {
        val result = AdvancedMathFunctions.cos(listOf(UDM.Scalar(PI / 2)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double, 1e-10)
    }

    @Test
    fun testTan() {
        val result = AdvancedMathFunctions.tan(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double)
    }

    @Test
    fun testTanPiOver4() {
        val result = AdvancedMathFunctions.tan(listOf(UDM.Scalar(PI / 4)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(1.0, result.value as Double)
    }

    @Test
    fun testAsin() {
        val result = AdvancedMathFunctions.asin(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double)
    }

    @Test
    fun testAsinOne() {
        val result = AdvancedMathFunctions.asin(listOf(UDM.Scalar(1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(PI / 2, result.value as Double)
    }

    @Test
    fun testAsinMinusOne() {
        val result = AdvancedMathFunctions.asin(listOf(UDM.Scalar(-1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(-PI / 2, result.value as Double)
    }

    @Test
    fun testAsinOutOfRange() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.asin(listOf(UDM.Scalar(1.5)))
        }
    }

    @Test
    fun testAsinOutOfRangeNegative() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.asin(listOf(UDM.Scalar(-1.5)))
        }
    }

    @Test
    fun testAcos() {
        val result = AdvancedMathFunctions.acos(listOf(UDM.Scalar(1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double)
    }

    @Test
    fun testAcosZero() {
        val result = AdvancedMathFunctions.acos(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(PI / 2, result.value as Double)
    }

    @Test
    fun testAcosMinusOne() {
        val result = AdvancedMathFunctions.acos(listOf(UDM.Scalar(-1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(PI, result.value as Double)
    }

    @Test
    fun testAcosOutOfRange() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.acos(listOf(UDM.Scalar(2.0)))
        }
    }

    @Test
    fun testAtan() {
        val result = AdvancedMathFunctions.atan(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double)
    }

    @Test
    fun testAtanOne() {
        val result = AdvancedMathFunctions.atan(listOf(UDM.Scalar(1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(PI / 4, result.value as Double)
    }

    @Test
    fun testAtanMinusOne() {
        val result = AdvancedMathFunctions.atan(listOf(UDM.Scalar(-1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(-PI / 4, result.value as Double)
    }

    @Test
    fun testAtan2() {
        val result = AdvancedMathFunctions.atan2(listOf(UDM.Scalar(1.0), UDM.Scalar(1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(PI / 4, result.value as Double)
    }

    @Test
    fun testAtan2Quadrants() {
        // First quadrant
        val q1 = AdvancedMathFunctions.atan2(listOf(UDM.Scalar(1.0), UDM.Scalar(1.0)))
        assertEqualsWithTolerance(PI / 4, (q1 as UDM.Scalar).value as Double)

        // Second quadrant  
        val q2 = AdvancedMathFunctions.atan2(listOf(UDM.Scalar(1.0), UDM.Scalar(-1.0)))
        assertEqualsWithTolerance(3 * PI / 4, (q2 as UDM.Scalar).value as Double)

        // Third quadrant
        val q3 = AdvancedMathFunctions.atan2(listOf(UDM.Scalar(-1.0), UDM.Scalar(-1.0)))
        assertEqualsWithTolerance(-3 * PI / 4, (q3 as UDM.Scalar).value as Double)

        // Fourth quadrant
        val q4 = AdvancedMathFunctions.atan2(listOf(UDM.Scalar(-1.0), UDM.Scalar(1.0)))
        assertEqualsWithTolerance(-PI / 4, (q4 as UDM.Scalar).value as Double)
    }

    @Test
    fun testAtan2SpecialCases() {
        // Positive y-axis
        val pos_y = AdvancedMathFunctions.atan2(listOf(UDM.Scalar(1.0), UDM.Scalar(0.0)))
        assertEqualsWithTolerance(PI / 2, (pos_y as UDM.Scalar).value as Double)

        // Negative y-axis
        val neg_y = AdvancedMathFunctions.atan2(listOf(UDM.Scalar(-1.0), UDM.Scalar(0.0)))
        assertEqualsWithTolerance(-PI / 2, (neg_y as UDM.Scalar).value as Double)

        // Positive x-axis
        val pos_x = AdvancedMathFunctions.atan2(listOf(UDM.Scalar(0.0), UDM.Scalar(1.0)))
        assertEqualsWithTolerance(0.0, (pos_x as UDM.Scalar).value as Double)
    }

    // ========== HYPERBOLIC FUNCTIONS ==========

    @Test
    fun testSinh() {
        val result = AdvancedMathFunctions.sinh(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double)
    }

    @Test
    fun testSinhOne() {
        val result = AdvancedMathFunctions.sinh(listOf(UDM.Scalar(1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(sinh(1.0), result.value as Double)
    }

    @Test
    fun testCosh() {
        val result = AdvancedMathFunctions.cosh(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(1.0, result.value as Double)
    }

    @Test
    fun testCoshOne() {
        val result = AdvancedMathFunctions.cosh(listOf(UDM.Scalar(1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(cosh(1.0), result.value as Double)
    }

    @Test
    fun testTanh() {
        val result = AdvancedMathFunctions.tanh(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double)
    }

    @Test
    fun testTanhOne() {
        val result = AdvancedMathFunctions.tanh(listOf(UDM.Scalar(1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(tanh(1.0), result.value as Double)
    }

    @Test
    fun testTanhLimits() {
        // tanh approaches 1 as x approaches infinity
        val largeTanh = AdvancedMathFunctions.tanh(listOf(UDM.Scalar(10.0)))
        assertTrue((largeTanh as UDM.Scalar).value as Double > 0.99)

        // tanh approaches -1 as x approaches negative infinity
        val negLargeTanh = AdvancedMathFunctions.tanh(listOf(UDM.Scalar(-10.0)))
        val negLargeTanhValue = (negLargeTanh as UDM.Scalar).value as Double
        assertTrue(negLargeTanhValue < -0.99)
    }

    // ========== LOGARITHMIC & EXPONENTIAL FUNCTIONS ==========

    @Test
    fun testLn() {
        val result = AdvancedMathFunctions.ln(listOf(UDM.Scalar(1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double)
    }

    @Test
    fun testLnE() {
        val result = AdvancedMathFunctions.ln(listOf(UDM.Scalar(E)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(1.0, result.value as Double)
    }

    @Test
    fun testLnTen() {
        val result = AdvancedMathFunctions.ln(listOf(UDM.Scalar(10.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(ln(10.0), result.value as Double)
    }

    @Test
    fun testLnNegative() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.ln(listOf(UDM.Scalar(-1.0)))
        }
    }

    @Test
    fun testLnZero() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.ln(listOf(UDM.Scalar(0.0)))
        }
    }

    @Test
    fun testLogNatural() {
        val result = AdvancedMathFunctions.log(listOf(UDM.Scalar(E)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(1.0, result.value as Double)
    }

    @Test
    fun testLogBase10() {
        val result = AdvancedMathFunctions.log(listOf(UDM.Scalar(100.0), UDM.Scalar(10.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(2.0, result.value as Double)
    }

    @Test
    fun testLogBase2() {
        val result = AdvancedMathFunctions.log(listOf(UDM.Scalar(8.0), UDM.Scalar(2.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(3.0, result.value as Double)
    }

    @Test
    fun testLogInvalidValue() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.log(listOf(UDM.Scalar(-1.0)))
        }
    }

    @Test
    fun testLogInvalidBase() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.log(listOf(UDM.Scalar(10.0), UDM.Scalar(1.0)))
        }
    }

    @Test
    fun testLogNegativeBase() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.log(listOf(UDM.Scalar(10.0), UDM.Scalar(-2.0)))
        }
    }

    @Test
    fun testLog10() {
        val result = AdvancedMathFunctions.log10(listOf(UDM.Scalar(100.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(2.0, result.value as Double)
    }

    @Test
    fun testLog10Thousand() {
        val result = AdvancedMathFunctions.log10(listOf(UDM.Scalar(1000.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(3.0, result.value as Double)
    }

    @Test
    fun testLog10One() {
        val result = AdvancedMathFunctions.log10(listOf(UDM.Scalar(1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double)
    }

    @Test
    fun testLog10Negative() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.log10(listOf(UDM.Scalar(-10.0)))
        }
    }

    @Test
    fun testExp() {
        val result = AdvancedMathFunctions.exp(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(1.0, result.value as Double)
    }

    @Test
    fun testExpOne() {
        val result = AdvancedMathFunctions.exp(listOf(UDM.Scalar(1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(E, result.value as Double)
    }

    @Test
    fun testExpTwo() {
        val result = AdvancedMathFunctions.exp(listOf(UDM.Scalar(2.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(exp(2.0), result.value as Double)
    }

    @Test
    fun testLog2() {
        val result = AdvancedMathFunctions.log2(listOf(UDM.Scalar(8.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(3.0, result.value as Double)
    }

    @Test
    fun testLog2PowersOfTwo() {
        val result1024 = AdvancedMathFunctions.log2(listOf(UDM.Scalar(1024.0)))
        assertEqualsWithTolerance(10.0, (result1024 as UDM.Scalar).value as Double)

        val result1 = AdvancedMathFunctions.log2(listOf(UDM.Scalar(1.0)))
        assertEqualsWithTolerance(0.0, (result1 as UDM.Scalar).value as Double)

        val result2 = AdvancedMathFunctions.log2(listOf(UDM.Scalar(2.0)))
        assertEqualsWithTolerance(1.0, (result2 as UDM.Scalar).value as Double)
    }

    @Test
    fun testLog2Negative() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.log2(listOf(UDM.Scalar(-8.0)))
        }
    }

    // ========== ANGLE CONVERSION ==========

    @Test
    fun testToRadians() {
        val result = AdvancedMathFunctions.toRadians(listOf(UDM.Scalar(180.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(PI, result.value as Double)
    }

    @Test
    fun testToRadians90() {
        val result = AdvancedMathFunctions.toRadians(listOf(UDM.Scalar(90.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(PI / 2, result.value as Double)
    }

    @Test
    fun testToRadiansZero() {
        val result = AdvancedMathFunctions.toRadians(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double)
    }

    @Test
    fun testToDegrees() {
        val result = AdvancedMathFunctions.toDegrees(listOf(UDM.Scalar(PI)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(180.0, result.value as Double)
    }

    @Test
    fun testToDegreesPiOver2() {
        val result = AdvancedMathFunctions.toDegrees(listOf(UDM.Scalar(PI / 2)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(90.0, result.value as Double)
    }

    @Test
    fun testToDegreesZero() {
        val result = AdvancedMathFunctions.toDegrees(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(0.0, result.value as Double)
    }

    // ========== MATHEMATICAL CONSTANTS ==========

    @Test
    fun testPi() {
        val result = AdvancedMathFunctions.pi(listOf())
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(PI, result.value as Double)
    }

    @Test
    fun testE() {
        val result = AdvancedMathFunctions.e(listOf())
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(E, result.value as Double)
    }

    @Test
    fun testGoldenRatio() {
        val result = AdvancedMathFunctions.goldenRatio(listOf())
        assertTrue(result is UDM.Scalar)
        val expectedPhi = (1.0 + sqrt(5.0)) / 2.0
        assertEqualsWithTolerance(expectedPhi, result.value as Double)
    }

    // ========== ERROR HANDLING ==========

    @Test
    fun testSinNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.sin(listOf())
        }
    }

    @Test
    fun testCosNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.cos(listOf())
        }
    }

    @Test
    fun testTanNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.tan(listOf())
        }
    }

    @Test
    fun testAsinNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.asin(listOf())
        }
    }

    @Test
    fun testAcosNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.acos(listOf())
        }
    }

    @Test
    fun testAtanNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.atan(listOf())
        }
    }

    @Test
    fun testAtan2InsufficientArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.atan2(listOf(UDM.Scalar(1.0)))
        }
    }

    @Test
    fun testSinhNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.sinh(listOf())
        }
    }

    @Test
    fun testCoshNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.cosh(listOf())
        }
    }

    @Test
    fun testTanhNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.tanh(listOf())
        }
    }

    @Test
    fun testLnNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.ln(listOf())
        }
    }

    @Test
    fun testLogNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.log(listOf())
        }
    }

    @Test
    fun testLog10NoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.log10(listOf())
        }
    }

    @Test
    fun testExpNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.exp(listOf())
        }
    }

    @Test
    fun testLog2NoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.log2(listOf())
        }
    }

    @Test
    fun testToRadiansNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.toRadians(listOf())
        }
    }

    @Test
    fun testToDegreesNoArgs() {
        assertThrows<IllegalArgumentException> {
            AdvancedMathFunctions.toDegrees(listOf())
        }
    }

    // ========== EDGE CASES ==========

    @Test
    fun testTrigWithLargeValues() {
        // Test with values outside typical range
        val largeSin = AdvancedMathFunctions.sin(listOf(UDM.Scalar(100 * PI)))
        assertTrue(abs((largeSin as UDM.Scalar).value as Double) < 1e-10) // Should be close to 0

        val largeCos = AdvancedMathFunctions.cos(listOf(UDM.Scalar(100 * PI)))
        assertEqualsWithTolerance(1.0, (largeCos as UDM.Scalar).value as Double, 1e-10)
    }

    @Test
    fun testExpWithNegativeValues() {
        val result = AdvancedMathFunctions.exp(listOf(UDM.Scalar(-1.0)))
        assertTrue(result is UDM.Scalar)
        assertEqualsWithTolerance(1.0 / E, result.value as Double)
    }

    @Test
    fun testLogExpInverse() {
        // Test that log(exp(x)) = x
        val x = 2.5
        val expResult = AdvancedMathFunctions.exp(listOf(UDM.Scalar(x)))
        val logResult = AdvancedMathFunctions.ln(listOf(expResult))
        
        assertEqualsWithTolerance(x, (logResult as UDM.Scalar).value as Double)
    }

    @Test
    fun testSinCosIdentity() {
        // Test that sin²(x) + cos²(x) = 1
        val angle = PI / 3
        val sinResult = AdvancedMathFunctions.sin(listOf(UDM.Scalar(angle)))
        val cosResult = AdvancedMathFunctions.cos(listOf(UDM.Scalar(angle)))
        
        val sinVal = (sinResult as UDM.Scalar).value as Double
        val cosVal = (cosResult as UDM.Scalar).value as Double
        
        assertEqualsWithTolerance(1.0, sinVal * sinVal + cosVal * cosVal)
    }

    @Test
    fun testHyperbolicIdentity() {
        // Test that cosh²(x) - sinh²(x) = 1
        val x = 1.5
        val sinhResult = AdvancedMathFunctions.sinh(listOf(UDM.Scalar(x)))
        val coshResult = AdvancedMathFunctions.cosh(listOf(UDM.Scalar(x)))
        
        val sinhVal = (sinhResult as UDM.Scalar).value as Double
        val coshVal = (coshResult as UDM.Scalar).value as Double
        
        assertEqualsWithTolerance(1.0, coshVal * coshVal - sinhVal * sinhVal)
    }
}