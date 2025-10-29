package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AggregationsTest {

    @Test
    fun testSum() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1.0),
            UDM.Scalar(2.0),
            UDM.Scalar(3.0),
            UDM.Scalar(4.0)
        ))

        val result = Aggregations.sum(listOf(array))

        assertTrue(result is UDM.Scalar)
        assertEquals(10.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testSumWithDecimals() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1.5),
            UDM.Scalar(2.3),
            UDM.Scalar(3.2)
        ))

        val result = Aggregations.sum(listOf(array))

        assertEquals(7.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testSumWithNegativeNumbers() {
        val array = UDM.Array(listOf(
            UDM.Scalar(-1.0),
            UDM.Scalar(2.0),
            UDM.Scalar(-3.0),
            UDM.Scalar(4.0)
        ))

        val result = Aggregations.sum(listOf(array))

        assertEquals(2.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testSumEmptyArray() {
        val array = UDM.Array(emptyList())
        
        val result = Aggregations.sum(listOf(array))
        
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testSumWithStringNumbers() {
        val array = UDM.Array(listOf(
            UDM.Scalar("1"),
            UDM.Scalar("2.5"),
            UDM.Scalar("3")
        ))
        
        val result = Aggregations.sum(listOf(array))
        
        assertEquals(6.5, (result as UDM.Scalar).value)
    }

    @Test
    fun testAvg() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1.0),
            UDM.Scalar(2.0),
            UDM.Scalar(3.0),
            UDM.Scalar(4.0)
        ))

        val result = Aggregations.avg(listOf(array))

        assertTrue(result is UDM.Scalar)
        assertEquals(2.5, (result as UDM.Scalar).value)
    }

    @Test
    fun testAvgWithDecimals() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1.0),
            UDM.Scalar(3.0),
            UDM.Scalar(5.0)
        ))

        val result = Aggregations.avg(listOf(array))

        assertEquals(3.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testAvgEmptyArray() {
        val array = UDM.Array(emptyList())

        val result = Aggregations.avg(listOf(array))

        assertTrue(result is UDM.Scalar)
        assertEquals(null, (result as UDM.Scalar).value)
    }

    @Test
    fun testAvgSingleElement() {
        val array = UDM.Array(listOf(UDM.Scalar(42.0)))

        val result = Aggregations.avg(listOf(array))

        assertEquals(42.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testMin() {
        val array = UDM.Array(listOf(
            UDM.Scalar(3.0),
            UDM.Scalar(1.0),
            UDM.Scalar(4.0),
            UDM.Scalar(1.0),
            UDM.Scalar(5.0)
        ))

        val result = Aggregations.min(listOf(array))

        assertTrue(result is UDM.Scalar)
        assertEquals(1.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testMinWithDecimals() {
        val array = UDM.Array(listOf(
            UDM.Scalar(3.7),
            UDM.Scalar(1.2),
            UDM.Scalar(4.8),
            UDM.Scalar(2.1)
        ))

        val result = Aggregations.min(listOf(array))

        assertEquals(1.2, (result as UDM.Scalar).value)
    }

    @Test
    fun testMinWithNegativeNumbers() {
        val array = UDM.Array(listOf(
            UDM.Scalar(-3.0),
            UDM.Scalar(1.0),
            UDM.Scalar(-5.0),
            UDM.Scalar(2.0)
        ))

        val result = Aggregations.min(listOf(array))

        assertEquals(-5.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testMinEmptyArray() {
        val array = UDM.Array(emptyList())

        val result = Aggregations.min(listOf(array))

        assertTrue(result is UDM.Scalar)
        assertEquals(null, (result as UDM.Scalar).value)
    }

    @Test
    fun testMinSingleElement() {
        val array = UDM.Array(listOf(UDM.Scalar(42.0)))

        val result = Aggregations.min(listOf(array))

        assertEquals(42.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testMax() {
        val array = UDM.Array(listOf(
            UDM.Scalar(3.0),
            UDM.Scalar(1.0),
            UDM.Scalar(4.0),
            UDM.Scalar(1.0),
            UDM.Scalar(5.0)
        ))

        val result = Aggregations.max(listOf(array))

        assertTrue(result is UDM.Scalar)
        assertEquals(5.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testMaxWithDecimals() {
        val array = UDM.Array(listOf(
            UDM.Scalar(3.7),
            UDM.Scalar(1.2),
            UDM.Scalar(4.8),
            UDM.Scalar(2.1)
        ))
        
        val result = Aggregations.max(listOf(array))
        
        assertEquals(4.8, (result as UDM.Scalar).value)
    }

    @Test
    fun testMaxWithNegativeNumbers() {
        val array = UDM.Array(listOf(
            UDM.Scalar(-3.0),
            UDM.Scalar(-1.0),
            UDM.Scalar(-5.0),
            UDM.Scalar(-2.0)
        ))

        val result = Aggregations.max(listOf(array))

        assertEquals(-1.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testMaxEmptyArray() {
        val array = UDM.Array(emptyList())

        val result = Aggregations.max(listOf(array))

        assertTrue(result is UDM.Scalar)
        assertEquals(null, (result as UDM.Scalar).value)
    }

    @Test
    fun testMaxSingleElement() {
        val array = UDM.Array(listOf(UDM.Scalar(42.0)))

        val result = Aggregations.max(listOf(array))

        assertEquals(42.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testCount() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1.0),
            UDM.Scalar(2.0),
            UDM.Scalar(3.0),
            UDM.Scalar(4.0)
        ))

        val result = Aggregations.count(listOf(array))

        assertTrue(result is UDM.Scalar)
        assertEquals(4.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testCountEmptyArray() {
        val array = UDM.Array(emptyList())
        
        val result = Aggregations.count(listOf(array))
        
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testCountSingleElement() {
        val array = UDM.Array(listOf(UDM.Scalar("element")))
        
        val result = Aggregations.count(listOf(array))
        
        assertEquals(1.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testCountWithMixedTypes() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar("hello"),
            UDM.Scalar(true),
            UDM.Object(emptyMap(), emptyMap()),
            UDM.Array(emptyList())
        ))
        
        val result = Aggregations.count(listOf(array))
        
        assertEquals(5.0, (result as UDM.Scalar).value)
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun testSumInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            Aggregations.sum(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            Aggregations.sum(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        }
        
        assertThrows<FunctionArgumentException> {
            Aggregations.sum(listOf(UDM.Scalar("not-array")))
        }
        
        assertThrows<FunctionArgumentException> {
            Aggregations.sum(listOf(UDM.Object(emptyMap(), emptyMap())))
        }
    }

    @Test
    fun testAvgInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            Aggregations.avg(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            Aggregations.avg(listOf(UDM.Scalar("not-array")))
        }
    }

    @Test
    fun testMinInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            Aggregations.min(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            Aggregations.min(listOf(UDM.Scalar("not-array")))
        }
    }

    @Test
    fun testMaxInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            Aggregations.max(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            Aggregations.max(listOf(UDM.Scalar("not-array")))
        }
    }

    @Test
    fun testCountInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            Aggregations.count(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            Aggregations.count(listOf(UDM.Scalar("not-array")))
        }
    }

    @Test
    fun testSumWithInvalidNumbers() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar("not-a-number"),
            UDM.Scalar(3)
        ))
        
        assertThrows<FunctionArgumentException> {
            Aggregations.sum(listOf(array))
        }
    }

    @Test
    fun testAvgWithInvalidNumbers() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(true),
            UDM.Scalar(3)
        ))
        
        assertThrows<FunctionArgumentException> {
            Aggregations.avg(listOf(array))
        }
    }

    @Test
    fun testMinWithInvalidNumbers() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Object(emptyMap(), emptyMap()),
            UDM.Scalar(3)
        ))
        
        assertThrows<FunctionArgumentException> {
            Aggregations.min(listOf(array))
        }
    }

    @Test
    fun testMaxWithInvalidNumbers() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Array(emptyList()),
            UDM.Scalar(3)
        ))
        
        assertThrows<FunctionArgumentException> {
            Aggregations.max(listOf(array))
        }
    }

    @Test
    fun testSumWithNullValues() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(null),
            UDM.Scalar(3)
        ))
        
        assertThrows<FunctionArgumentException> {
            Aggregations.sum(listOf(array))
        }
    }

    // ==================== EDGE CASES ====================

    @Test
    fun testSumWithZeros() {
        val array = UDM.Array(listOf(
            UDM.Scalar(0),
            UDM.Scalar(0),
            UDM.Scalar(0)
        ))
        
        val result = Aggregations.sum(listOf(array))
        
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testAvgWithZeros() {
        val array = UDM.Array(listOf(
            UDM.Scalar(0),
            UDM.Scalar(0),
            UDM.Scalar(0)
        ))
        
        val result = Aggregations.avg(listOf(array))
        
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testMinMaxWithSameValues() {
        val array = UDM.Array(listOf(
            UDM.Scalar(5),
            UDM.Scalar(5),
            UDM.Scalar(5)
        ))
        
        val minResult = Aggregations.min(listOf(array))
        val maxResult = Aggregations.max(listOf(array))
        
        assertEquals(5.0, (minResult as UDM.Scalar).value)
        assertEquals(5.0, (maxResult as UDM.Scalar).value)
    }

    @Test
    fun testSumWithVeryLargeNumbers() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1e15),
            UDM.Scalar(2e15),
            UDM.Scalar(3e15)
        ))
        
        val result = Aggregations.sum(listOf(array))
        
        assertEquals(6e15, (result as UDM.Scalar).value)
    }

    @Test
    fun testSumWithVerySmallNumbers() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1e-15),
            UDM.Scalar(2e-15),
            UDM.Scalar(3e-15)
        ))
        
        val result = Aggregations.sum(listOf(array))
        
        assertEquals(6e-15, (result as UDM.Scalar).value)
    }

    @Test
    fun testAvgWithPrecision() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2)
        ))
        
        val result = Aggregations.avg(listOf(array))
        
        assertEquals(1.5, (result as UDM.Scalar).value)
    }

    @Test
    fun testStringNumberConversion() {
        val array = UDM.Array(listOf(
            UDM.Scalar("10"),
            UDM.Scalar("20.5"),
            UDM.Scalar("30")
        ))
        
        val sumResult = Aggregations.sum(listOf(array))
        val avgResult = Aggregations.avg(listOf(array))
        val minResult = Aggregations.min(listOf(array))
        val maxResult = Aggregations.max(listOf(array))
        
        assertEquals(60.5, (sumResult as UDM.Scalar).value)
        assertEquals(20.166666666666668, (avgResult as UDM.Scalar).value)
        assertEquals(10.0, (minResult as UDM.Scalar).value)
        assertEquals(30.0, (maxResult as UDM.Scalar).value)
    }

    @Test
    fun testMixedNumberTypes() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),        // Int
            UDM.Scalar(2.5),      // Double
            UDM.Scalar(3L),       // Long
            UDM.Scalar(4.0f)      // Float
        ))
        
        val sumResult = Aggregations.sum(listOf(array))
        val avgResult = Aggregations.avg(listOf(array))
        
        assertEquals(10.5, (sumResult as UDM.Scalar).value)
        assertEquals(2.625, (avgResult as UDM.Scalar).value)
    }
}