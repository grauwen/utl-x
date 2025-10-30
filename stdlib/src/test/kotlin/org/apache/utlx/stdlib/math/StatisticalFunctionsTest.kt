package org.apache.utlx.stdlib.math

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatisticalFunctionsTest {

    @Test
    fun testMedian() {
        // Odd number of elements
        val result1 = StatisticalFunctions.median(listOf(UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(3),
            UDM.Scalar(5),
            UDM.Scalar(7),
            UDM.Scalar(9)
        ))))
        assertEquals(5.0, (result1 as UDM.Scalar).value)
        
        // Even number of elements
        val result2 = StatisticalFunctions.median(listOf(UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3),
            UDM.Scalar(4)
        ))))
        assertEquals(2.5, (result2 as UDM.Scalar).value)
        
        // Single element
        val result3 = StatisticalFunctions.median(listOf(UDM.Array(listOf(UDM.Scalar(42)))))
        assertEquals(42.0, (result3 as UDM.Scalar).value)
        
        // Unsorted input
        val result4 = StatisticalFunctions.median(listOf(UDM.Array(listOf(
            UDM.Scalar(85),
            UDM.Scalar(90),
            UDM.Scalar(78),
            UDM.Scalar(92),
            UDM.Scalar(88)
        ))))
        assertEquals(88.0, (result4 as UDM.Scalar).value)
    }

    @Test
    fun testMedianWithDecimals() {
        val result = StatisticalFunctions.median(listOf(UDM.Array(listOf(
            UDM.Scalar(1.5),
            UDM.Scalar(2.3),
            UDM.Scalar(3.7),
            UDM.Scalar(4.1)
        ))))
        assertEquals(3.0, (result as UDM.Scalar).value) // (2.3 + 3.7) / 2
    }

    @Test
    fun testMode() {
        // Clear mode
        val result1 = StatisticalFunctions.mode(listOf(UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(2),
            UDM.Scalar(3),
            UDM.Scalar(3),
            UDM.Scalar(3),
            UDM.Scalar(4)
        ))))
        assertEquals(3, (result1 as UDM.Scalar).value)
        
        // String mode
        val result2 = StatisticalFunctions.mode(listOf(UDM.Array(listOf(
            UDM.Scalar("a"),
            UDM.Scalar("b"),
            UDM.Scalar("b"),
            UDM.Scalar("c")
        ))))
        assertEquals("b", (result2 as UDM.Scalar).value)
        
        // All elements appear once (returns first encountered)
        val result3 = StatisticalFunctions.mode(listOf(UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3)
        ))))
        assertEquals(1, (result3 as UDM.Scalar).value)
        
        // Empty array
        val result4 = StatisticalFunctions.mode(listOf(UDM.Array(emptyList())))
        assertEquals(null, (result4 as UDM.Scalar).value)
    }

    @Test
    fun testModeWithMixedTypes() {
        val result = StatisticalFunctions.mode(listOf(UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar("hello"),
            UDM.Scalar(1),
            UDM.Scalar("hello"),
            UDM.Scalar("hello"),
            UDM.Array(emptyList())
        ))))
        assertEquals("hello", (result as UDM.Scalar).value)
    }

    @Test
    fun testStdDev() {
        val result = StatisticalFunctions.stdDev(listOf(UDM.Array(listOf(
            UDM.Scalar(2),
            UDM.Scalar(4),
            UDM.Scalar(4),
            UDM.Scalar(4),
            UDM.Scalar(5),
            UDM.Scalar(5),
            UDM.Scalar(7),
            UDM.Scalar(9)
        ))))

        val stdDevValue = (result as UDM.Scalar).value as Double
        // mean=5, variance=32/7≈4.571, stdDev=sqrt(4.571)≈2.138
        assertTrue(stdDevValue > 2.1 && stdDevValue < 2.2) // Approximately 2.138
    }

    @Test
    fun testStdDevSimple() {
        // Simple case: [1, 2, 3]
        // Mean = 2, Variance = ((1-2)² + (2-2)² + (3-2)²) / (3-1) = (1 + 0 + 1) / 2 = 1
        // StdDev = sqrt(1) = 1
        val result = StatisticalFunctions.stdDev(listOf(UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3)
        ))))
        
        assertEquals(1.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testVariance() {
        val result = StatisticalFunctions.variance(listOf(UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3)
        ))))
        
        assertEquals(1.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testVarianceWithLargerDataset() {
        val result = StatisticalFunctions.variance(listOf(UDM.Array(listOf(
            UDM.Scalar(2),
            UDM.Scalar(4),
            UDM.Scalar(4),
            UDM.Scalar(4),
            UDM.Scalar(5),
            UDM.Scalar(5),
            UDM.Scalar(7),
            UDM.Scalar(9)
        ))))

        val varianceValue = (result as UDM.Scalar).value as Double
        // mean=5, sum of squares=32, variance=32/(8-1)=32/7≈4.571
        assertTrue(varianceValue > 4.5 && varianceValue < 4.7) // Approximately 4.571
    }

    @Test
    fun testPercentile() {
        // Test 50th percentile (median)
        val result1 = StatisticalFunctions.percentile(listOf(
            UDM.Array(listOf(
                UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(4), 
                UDM.Scalar(5), UDM.Scalar(6), UDM.Scalar(7), UDM.Scalar(8), 
                UDM.Scalar(9), UDM.Scalar(10)
            )),
            UDM.Scalar(50)
        ))
        assertEquals(5.5, (result1 as UDM.Scalar).value)
        
        // Test 0th percentile (minimum)
        val result2 = StatisticalFunctions.percentile(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3))),
            UDM.Scalar(0)
        ))
        assertEquals(1.0, (result2 as UDM.Scalar).value)
        
        // Test 100th percentile (maximum)
        val result3 = StatisticalFunctions.percentile(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3))),
            UDM.Scalar(100)
        ))
        assertEquals(3.0, (result3 as UDM.Scalar).value)
    }

    @Test
    fun testPercentileWithUnsortedData() {
        val result = StatisticalFunctions.percentile(listOf(
            UDM.Array(listOf(
                UDM.Scalar(9), UDM.Scalar(1), UDM.Scalar(5), UDM.Scalar(3), UDM.Scalar(7)
            )),
            UDM.Scalar(50)
        ))
        assertEquals(5.0, (result as UDM.Scalar).value) // Median of [1, 3, 5, 7, 9]
    }

    @Test
    fun testQuartiles() {
        val result = StatisticalFunctions.quartiles(listOf(UDM.Array(listOf(
            UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(4), 
            UDM.Scalar(5), UDM.Scalar(6), UDM.Scalar(7), UDM.Scalar(8), UDM.Scalar(9)
        ))))
        
        assertTrue(result is UDM.Array)
        val quartiles = (result as UDM.Array).elements
        assertEquals(3, quartiles.size)
        
        val q1 = (quartiles[0] as UDM.Scalar).value as Double
        val q2 = (quartiles[1] as UDM.Scalar).value as Double
        val q3 = (quartiles[2] as UDM.Scalar).value as Double
        
        assertEquals(3.0, q1) // 25th percentile
        assertEquals(5.0, q2) // 50th percentile (median)
        assertEquals(7.0, q3) // 75th percentile
    }

    @Test
    fun testIqr() {
        val result = StatisticalFunctions.iqr(listOf(UDM.Array(listOf(
            UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(4), 
            UDM.Scalar(5), UDM.Scalar(6), UDM.Scalar(7), UDM.Scalar(8), UDM.Scalar(9)
        ))))
        
        assertEquals(4.0, (result as UDM.Scalar).value) // Q3 (7) - Q1 (3) = 4
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun testMedianInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.median(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.median(listOf(UDM.Scalar("not-array")))
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.median(listOf(UDM.Array(emptyList())))
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.median(listOf(UDM.Array(listOf(UDM.Scalar("not-number")))))
        }
    }

    @Test
    fun testModeInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.mode(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.mode(listOf(UDM.Scalar("not-array")))
        }
    }

    @Test
    fun testStdDevInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.stdDev(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.stdDev(listOf(UDM.Scalar("not-array")))
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.stdDev(listOf(UDM.Array(listOf(UDM.Scalar(1)))))
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.stdDev(listOf(UDM.Array(listOf(UDM.Scalar("not-number"), UDM.Scalar("also-not-number")))))
        }
    }

    @Test
    fun testVarianceInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.variance(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.variance(listOf(UDM.Array(listOf(UDM.Scalar(1)))))
        }
    }

    @Test
    fun testPercentileInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.percentile(listOf(UDM.Array(listOf(UDM.Scalar(1)))))
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.percentile(listOf(UDM.Scalar("not-array"), UDM.Scalar(50)))
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.percentile(listOf(UDM.Array(listOf(UDM.Scalar(1))), UDM.Scalar("not-number")))
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.percentile(listOf(UDM.Array(listOf(UDM.Scalar(1))), UDM.Scalar(-1)))
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.percentile(listOf(UDM.Array(listOf(UDM.Scalar(1))), UDM.Scalar(101)))
        }
        
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.percentile(listOf(UDM.Array(emptyList()), UDM.Scalar(50)))
        }
    }

    @Test
    fun testQuartilesInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.quartiles(listOf())
        }
    }

    @Test
    fun testIqrInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            StatisticalFunctions.iqr(listOf())
        }
    }

    // ==================== EDGE CASES ====================

    @Test
    fun testMedianWithNegativeNumbers() {
        val result = StatisticalFunctions.median(listOf(UDM.Array(listOf(
            UDM.Scalar(-5),
            UDM.Scalar(-2),
            UDM.Scalar(0),
            UDM.Scalar(3),
            UDM.Scalar(8)
        ))))
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testStdDevIdenticalValues() {
        val result = StatisticalFunctions.stdDev(listOf(UDM.Array(listOf(
            UDM.Scalar(5),
            UDM.Scalar(5),
            UDM.Scalar(5),
            UDM.Scalar(5)
        ))))
        
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testVarianceIdenticalValues() {
        val result = StatisticalFunctions.variance(listOf(UDM.Array(listOf(
            UDM.Scalar(3),
            UDM.Scalar(3),
            UDM.Scalar(3)
        ))))
        
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testModeWithComplexObjects() {
        val result = StatisticalFunctions.mode(listOf(UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
            UDM.Object(mapOf("key" to UDM.Scalar("value")), emptyMap()),
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
            UDM.Object(mapOf("key" to UDM.Scalar("value")), emptyMap()),
            UDM.Object(mapOf("key" to UDM.Scalar("value")), emptyMap())
        ))))
        
        // Should return one of the Object instances (most frequent)
        assertTrue(result is UDM.Object)
    }

    @Test
    fun testPercentileWithDuplicates() {
        val result = StatisticalFunctions.percentile(listOf(
            UDM.Array(listOf(
                UDM.Scalar(1), UDM.Scalar(1), UDM.Scalar(2), 
                UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(3)
            )),
            UDM.Scalar(50)
        ))
        
        assertEquals(2.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testStatisticsWithDecimals() {
        val data = UDM.Array(listOf(
            UDM.Scalar(1.1),
            UDM.Scalar(2.2),
            UDM.Scalar(3.3),
            UDM.Scalar(4.4),
            UDM.Scalar(5.5)
        ))
        
        val medianResult = StatisticalFunctions.median(listOf(data))
        assertEquals(3.3, (medianResult as UDM.Scalar).value)
        
        val percentileResult = StatisticalFunctions.percentile(listOf(data, UDM.Scalar(50)))
        assertEquals(3.3, (percentileResult as UDM.Scalar).value)
    }

    @Test
    fun testStatisticsWithMixedNumberTypes() {
        val data = UDM.Array(listOf(
            UDM.Scalar(1),        // Int
            UDM.Scalar(2.5),      // Double
            UDM.Scalar(3L),       // Long
            UDM.Scalar(4.0f)      // Float
        ))
        
        val medianResult = StatisticalFunctions.median(listOf(data))
        assertEquals(2.75, (medianResult as UDM.Scalar).value) // (2.5 + 3.0) / 2
        
        val modeResult = StatisticalFunctions.mode(listOf(data))
        assertEquals(1, (modeResult as UDM.Scalar).value) // First element since all appear once
    }

    @Test
    fun testLargeDataset() {
        // Test with larger dataset
        val elements = (1..100).map { UDM.Scalar(it) }
        val data = UDM.Array(elements)

        val medianResult = StatisticalFunctions.median(listOf(data))
        assertEquals(50.5, (medianResult as UDM.Scalar).value) // (50 + 51) / 2

        // Percentile uses linear interpolation: rank = p/100 * (n-1)
        // For 25%: rank = 0.25 * 99 = 24.75 -> numbers[24] + 0.75 * (numbers[25] - numbers[24]) = 25 + 0.75 = 25.75
        val percentile25 = StatisticalFunctions.percentile(listOf(data, UDM.Scalar(25)))
        assertEquals(25.75, (percentile25 as UDM.Scalar).value)

        // For 75%: rank = 0.75 * 99 = 74.25 -> numbers[74] + 0.25 * (numbers[75] - numbers[74]) = 75 + 0.25 = 75.25
        val percentile75 = StatisticalFunctions.percentile(listOf(data, UDM.Scalar(75)))
        assertEquals(75.25, (percentile75 as UDM.Scalar).value)

        val iqrResult = StatisticalFunctions.iqr(listOf(data))
        assertEquals(49.5, (iqrResult as UDM.Scalar).value) // 75.25 - 25.75 = 49.5
    }
}