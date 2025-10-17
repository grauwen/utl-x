package org.apache.utlx.stdlib.math

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MathFunctionsTest {

    @Test
    fun testAbs() {
        val result1 = MathFunctions.abs(listOf(UDM.Scalar(-5)))
        assertEquals(5.0, (result1 as UDM.Scalar).value)
        
        val result2 = MathFunctions.abs(listOf(UDM.Scalar(3)))
        assertEquals(3.0, (result2 as UDM.Scalar).value)
        
        val result3 = MathFunctions.abs(listOf(UDM.Scalar(0)))
        assertEquals(0.0, (result3 as UDM.Scalar).value)
        
        val result4 = MathFunctions.abs(listOf(UDM.Scalar(-3.14)))
        assertEquals(3.14, (result4 as UDM.Scalar).value)
    }

    @Test
    fun testRound() {
        val result1 = MathFunctions.round(listOf(UDM.Scalar(3.14159)))
        assertEquals(3.0, (result1 as UDM.Scalar).value)
        
        val result2 = MathFunctions.round(listOf(UDM.Scalar(3.6)))
        assertEquals(4.0, (result2 as UDM.Scalar).value)
        
        val result3 = MathFunctions.round(listOf(UDM.Scalar(-2.5)))
        assertEquals(-2.0, (result3 as UDM.Scalar).value)
        
        val result4 = MathFunctions.round(listOf(UDM.Scalar(0.0)))
        assertEquals(0.0, (result4 as UDM.Scalar).value)
    }

    @Test
    fun testCeil() {
        val result1 = MathFunctions.ceil(listOf(UDM.Scalar(3.14)))
        assertEquals(4.0, (result1 as UDM.Scalar).value)
        
        val result2 = MathFunctions.ceil(listOf(UDM.Scalar(-2.1)))
        assertEquals(-2.0, (result2 as UDM.Scalar).value)
        
        val result3 = MathFunctions.ceil(listOf(UDM.Scalar(5.0)))
        assertEquals(5.0, (result3 as UDM.Scalar).value)
        
        val result4 = MathFunctions.ceil(listOf(UDM.Scalar(0.1)))
        assertEquals(1.0, (result4 as UDM.Scalar).value)
    }

    @Test
    fun testFloor() {
        val result1 = MathFunctions.floor(listOf(UDM.Scalar(3.99)))
        assertEquals(3.0, (result1 as UDM.Scalar).value)
        
        val result2 = MathFunctions.floor(listOf(UDM.Scalar(-2.1)))
        assertEquals(-3.0, (result2 as UDM.Scalar).value)
        
        val result3 = MathFunctions.floor(listOf(UDM.Scalar(5.0)))
        assertEquals(5.0, (result3 as UDM.Scalar).value)
        
        val result4 = MathFunctions.floor(listOf(UDM.Scalar(0.9)))
        assertEquals(0.0, (result4 as UDM.Scalar).value)
    }

    @Test
    fun testPow() {
        val result1 = MathFunctions.pow(listOf(UDM.Scalar(2), UDM.Scalar(3)))
        assertEquals(8.0, (result1 as UDM.Scalar).value)
        
        val result2 = MathFunctions.pow(listOf(UDM.Scalar(5), UDM.Scalar(0)))
        assertEquals(1.0, (result2 as UDM.Scalar).value)
        
        val result3 = MathFunctions.pow(listOf(UDM.Scalar(9), UDM.Scalar(0.5)))
        assertEquals(3.0, (result3 as UDM.Scalar).value)
        
        val result4 = MathFunctions.pow(listOf(UDM.Scalar(-2), UDM.Scalar(2)))
        assertEquals(4.0, (result4 as UDM.Scalar).value)
    }

    @Test
    fun testSqrt() {
        val result1 = MathFunctions.sqrt(listOf(UDM.Scalar(9)))
        assertEquals(3.0, (result1 as UDM.Scalar).value)
        
        val result2 = MathFunctions.sqrt(listOf(UDM.Scalar(16)))
        assertEquals(4.0, (result2 as UDM.Scalar).value)
        
        val result3 = MathFunctions.sqrt(listOf(UDM.Scalar(0)))
        assertEquals(0.0, (result3 as UDM.Scalar).value)
        
        val result4 = MathFunctions.sqrt(listOf(UDM.Scalar(2)))
        val sqrtTwo = (result4 as UDM.Scalar).value as Double
        assertTrue(Math.abs(sqrtTwo - 1.4142135623730951) < 0.000001)
    }

    @Test
    fun testSqrtNegativeNumber() {
        val result = MathFunctions.sqrt(listOf(UDM.Scalar(-4)))
        val value = (result as UDM.Scalar).value as Double
        assertTrue(value.isNaN(), "Square root of negative number should be NaN")
    }

    @Test
    fun testRandom() {
        val result = MathFunctions.random(listOf())
        
        assertTrue(result is UDM.Scalar)
        val value = (result as UDM.Scalar).value as Double
        assertTrue(value >= 0.0 && value < 1.0, "Random should be between 0 (inclusive) and 1 (exclusive)")
        
        // Test multiple random calls to ensure they're different
        val result2 = MathFunctions.random(listOf())
        val value2 = (result2 as UDM.Scalar).value as Double
        
        // While theoretically possible they could be equal, it's extremely unlikely
        assertTrue(value != value2, "Two random calls should produce different values")
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun testAbsInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            MathFunctions.abs(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            MathFunctions.abs(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        }
        
        assertThrows<FunctionArgumentException> {
            MathFunctions.abs(listOf(UDM.Scalar("not-a-number")))
        }
        
        assertThrows<FunctionArgumentException> {
            MathFunctions.abs(listOf(UDM.Array(emptyList())))
        }
    }

    @Test
    fun testRoundInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            MathFunctions.round(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            MathFunctions.round(listOf(UDM.Scalar("not-a-number")))
        }
        
        assertThrows<FunctionArgumentException> {
            MathFunctions.round(listOf(UDM.Object(emptyMap(), emptyMap())))
        }
    }

    @Test
    fun testCeilInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            MathFunctions.ceil(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            MathFunctions.ceil(listOf(UDM.Scalar(true)))
        }
    }

    @Test
    fun testFloorInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            MathFunctions.floor(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            MathFunctions.floor(listOf(UDM.Scalar(null)))
        }
    }

    @Test
    fun testPowInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            MathFunctions.pow(listOf(UDM.Scalar(2)))
        }
        
        assertThrows<FunctionArgumentException> {
            MathFunctions.pow(listOf(UDM.Scalar("2"), UDM.Scalar(3)))
        }
        
        assertThrows<FunctionArgumentException> {
            MathFunctions.pow(listOf(UDM.Scalar(2), UDM.Scalar("3")))
        }
    }

    @Test
    fun testSqrtInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            MathFunctions.sqrt(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            MathFunctions.sqrt(listOf(UDM.Scalar("not-a-number")))
        }
    }

    @Test
    fun testRandomInvalidArguments() {
        // random() should accept zero arguments, but not more
        assertThrows<FunctionArgumentException> {
            MathFunctions.random(listOf(UDM.Scalar(1)))
        }
    }

    // ==================== EDGE CASES ====================

    @Test
    fun testMathWithIntegers() {
        // Test that integer inputs work correctly
        val result1 = MathFunctions.abs(listOf(UDM.Scalar(-5)))
        assertEquals(5.0, (result1 as UDM.Scalar).value)
        
        val result2 = MathFunctions.pow(listOf(UDM.Scalar(2), UDM.Scalar(3)))
        assertEquals(8.0, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testMathWithZero() {
        assertEquals(0.0, (MathFunctions.abs(listOf(UDM.Scalar(0))) as UDM.Scalar).value)
        assertEquals(0.0, (MathFunctions.round(listOf(UDM.Scalar(0))) as UDM.Scalar).value)
        assertEquals(0.0, (MathFunctions.ceil(listOf(UDM.Scalar(0))) as UDM.Scalar).value)
        assertEquals(0.0, (MathFunctions.floor(listOf(UDM.Scalar(0))) as UDM.Scalar).value)
        assertEquals(0.0, (MathFunctions.sqrt(listOf(UDM.Scalar(0))) as UDM.Scalar).value)
        assertEquals(1.0, (MathFunctions.pow(listOf(UDM.Scalar(0), UDM.Scalar(0))) as UDM.Scalar).value)
    }

    @Test
    fun testMathWithLargeNumbers() {
        val largeNumber = 1e10
        val result1 = MathFunctions.abs(listOf(UDM.Scalar(-largeNumber)))
        assertEquals(largeNumber, (result1 as UDM.Scalar).value)
        
        val result2 = MathFunctions.sqrt(listOf(UDM.Scalar(largeNumber)))
        val expectedSqrt = Math.sqrt(largeNumber)
        assertEquals(expectedSqrt, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testMathWithVerySmallNumbers() {
        val smallNumber = 1e-10
        val result1 = MathFunctions.abs(listOf(UDM.Scalar(-smallNumber)))
        assertEquals(smallNumber, (result1 as UDM.Scalar).value)
        
        val result2 = MathFunctions.ceil(listOf(UDM.Scalar(smallNumber)))
        assertEquals(1.0, (result2 as UDM.Scalar).value)
        
        val result3 = MathFunctions.floor(listOf(UDM.Scalar(smallNumber)))
        assertEquals(0.0, (result3 as UDM.Scalar).value)
    }

    @Test
    fun testPowSpecialCases() {
        // Any number to the power of 0 is 1
        assertEquals(1.0, (MathFunctions.pow(listOf(UDM.Scalar(42), UDM.Scalar(0))) as UDM.Scalar).value)
        
        // 1 to any power is 1
        assertEquals(1.0, (MathFunctions.pow(listOf(UDM.Scalar(1), UDM.Scalar(100))) as UDM.Scalar).value)
        
        // Any number to the power of 1 is itself
        assertEquals(42.0, (MathFunctions.pow(listOf(UDM.Scalar(42), UDM.Scalar(1))) as UDM.Scalar).value)
        
        // Test negative base with even exponent
        assertEquals(4.0, (MathFunctions.pow(listOf(UDM.Scalar(-2), UDM.Scalar(2))) as UDM.Scalar).value)
        
        // Test negative base with odd exponent
        assertEquals(-8.0, (MathFunctions.pow(listOf(UDM.Scalar(-2), UDM.Scalar(3))) as UDM.Scalar).value)
    }

    @Test
    fun testRoundingEdgeCases() {
        // Test 0.5 rounding (should round to even)
        assertEquals(2.0, (MathFunctions.round(listOf(UDM.Scalar(2.5))) as UDM.Scalar).value)
        assertEquals(4.0, (MathFunctions.round(listOf(UDM.Scalar(3.5))) as UDM.Scalar).value)
        
        // Test negative 0.5 rounding
        assertEquals(-2.0, (MathFunctions.round(listOf(UDM.Scalar(-2.5))) as UDM.Scalar).value)
        assertEquals(-4.0, (MathFunctions.round(listOf(UDM.Scalar(-3.5))) as UDM.Scalar).value)
    }

    @Test
    fun testRandomMultipleCalls() {
        val results = mutableSetOf<Double>()
        
        // Generate 10 random numbers and check they're all different
        repeat(10) {
            val result = MathFunctions.random(listOf())
            val value = (result as UDM.Scalar).value as Double
            assertTrue(value >= 0.0 && value < 1.0)
            results.add(value)
        }
        
        // All should be unique (extremely high probability)
        assertEquals(10, results.size, "All random numbers should be unique")
    }
}