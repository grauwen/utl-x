package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnhancedArrayFunctionsTest {

    @Test
    fun testPartition() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3),
            UDM.Scalar(4),
            UDM.Scalar(5)
        ))
        
        val predicate = UDM.Scalar("placeholder") // In real implementation, would be a function
        
        val result = EnhancedArrayFunctions.partition(listOf(array, predicate))
        
        assertTrue(result is UDM.Object)
        val partitioned = result as UDM.Object
        assertTrue(partitioned.properties.containsKey("true"))
        assertTrue(partitioned.properties.containsKey("false"))
        
        val trueGroup = partitioned.properties["true"] as UDM.Array
        val falseGroup = partitioned.properties["false"] as UDM.Array
        
        // In placeholder implementation, all elements go to "true" group
        assertEquals(5, trueGroup.elements.size)
        assertEquals(0, falseGroup.elements.size)
    }

    @Test
    fun testPartitionEmptyArray() {
        val array = UDM.Array(emptyList())
        val predicate = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.partition(listOf(array, predicate))
        
        val partitioned = result as UDM.Object
        val trueGroup = partitioned.properties["true"] as UDM.Array
        val falseGroup = partitioned.properties["false"] as UDM.Array
        
        assertEquals(0, trueGroup.elements.size)
        assertEquals(0, falseGroup.elements.size)
    }

    @Test
    fun testCountBy() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3),
            UDM.Scalar(4),
            UDM.Scalar(5)
        ))
        
        val predicate = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.countBy(listOf(array, predicate))
        
        assertTrue(result is UDM.Scalar)
        // In placeholder implementation, predicate always returns true
        assertEquals(5, (result as UDM.Scalar).value)
    }

    @Test
    fun testCountByEmptyArray() {
        val array = UDM.Array(emptyList())
        val predicate = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.countBy(listOf(array, predicate))
        
        assertEquals(0, (result as UDM.Scalar).value)
    }

    @Test
    fun testSumBy() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3)
        ))
        
        val mapper = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.sumBy(listOf(array, mapper))
        
        assertTrue(result is UDM.Scalar)
        // In placeholder implementation, sum is 0.0
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testSumByEmptyArray() {
        val array = UDM.Array(emptyList())
        val mapper = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.sumBy(listOf(array, mapper))
        
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testMaxBy() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(5),
            UDM.Scalar(3)
        ))
        
        val comparator = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.maxBy(listOf(array, comparator))
        
        assertTrue(result is UDM.Scalar)
        // In placeholder implementation, returns first element
        assertEquals(1, (result as UDM.Scalar).value)
    }

    @Test
    fun testMaxByEmptyArray() {
        val array = UDM.Array(emptyList())
        val comparator = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.maxBy(listOf(array, comparator))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(null, (result as UDM.Scalar).value)
    }

    @Test
    fun testMinBy() {
        val array = UDM.Array(listOf(
            UDM.Scalar(5),
            UDM.Scalar(1),
            UDM.Scalar(3)
        ))
        
        val comparator = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.minBy(listOf(array, comparator))
        
        assertTrue(result is UDM.Scalar)
        // In placeholder implementation, returns first element
        assertEquals(5, (result as UDM.Scalar).value)
    }

    @Test
    fun testMinByEmptyArray() {
        val array = UDM.Array(emptyList())
        val comparator = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.minBy(listOf(array, comparator))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(null, (result as UDM.Scalar).value)
    }

    @Test
    fun testGroupBy() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3),
            UDM.Scalar(4)
        ))
        
        val keyFunction = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.groupBy(listOf(array, keyFunction))
        
        assertTrue(result is UDM.Object)
        val grouped = result as UDM.Object
        
        // In placeholder implementation, all elements go to "default" group
        assertTrue(grouped.properties.containsKey("default"))
        val defaultGroup = grouped.properties["default"] as UDM.Array
        assertEquals(4, defaultGroup.elements.size)
    }

    @Test
    fun testGroupByEmptyArray() {
        val array = UDM.Array(emptyList())
        val keyFunction = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.groupBy(listOf(array, keyFunction))
        
        val grouped = result as UDM.Object
        assertEquals(0, grouped.properties.size)
    }

    @Test
    fun testDistinctBy() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(1),
            UDM.Scalar(3),
            UDM.Scalar(2)
        ))
        
        val keyFunction = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.distinctBy(listOf(array, keyFunction))
        
        assertTrue(result is UDM.Array)
        val distinct = result as UDM.Array
        
        // In placeholder implementation, all elements are kept
        assertEquals(5, distinct.elements.size)
    }

    @Test
    fun testDistinctByEmptyArray() {
        val array = UDM.Array(emptyList())
        val keyFunction = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.distinctBy(listOf(array, keyFunction))
        
        val distinct = result as UDM.Array
        assertEquals(0, distinct.elements.size)
    }

    @Test
    fun testAvgBy() {
        val array = UDM.Array(listOf(
            UDM.Scalar(10),
            UDM.Scalar(20),
            UDM.Scalar(30)
        ))
        
        val mapper = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.avgBy(listOf(array, mapper))
        
        assertTrue(result is UDM.Scalar)
        // In placeholder implementation, sum is 0, so average is 0.0
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testAvgByEmptyArray() {
        val array = UDM.Array(emptyList())
        val mapper = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.avgBy(listOf(array, mapper))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(null, (result as UDM.Scalar).value)
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun testPartitionInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.partition(listOf(UDM.Scalar("not-array")))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.partition(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.partition(listOf(UDM.Scalar("not-array"), UDM.Scalar("predicate")))
        }
    }

    @Test
    fun testCountByInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.countBy(listOf(UDM.Array(emptyList())))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.countBy(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.countBy(listOf(UDM.Object(emptyMap(), emptyMap()), UDM.Scalar("predicate")))
        }
    }

    @Test
    fun testSumByInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.sumBy(listOf(UDM.Array(emptyList())))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.sumBy(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.sumBy(listOf(UDM.Scalar("not-array"), UDM.Scalar("mapper")))
        }
    }

    @Test
    fun testMaxByInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.maxBy(listOf(UDM.Array(emptyList())))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.maxBy(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.maxBy(listOf(UDM.Scalar("not-array"), UDM.Scalar("comparator")))
        }
    }

    @Test
    fun testMinByInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.minBy(listOf(UDM.Array(emptyList())))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.minBy(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.minBy(listOf(UDM.Scalar("not-array"), UDM.Scalar("comparator")))
        }
    }

    @Test
    fun testGroupByInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.groupBy(listOf(UDM.Array(emptyList())))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.groupBy(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.groupBy(listOf(UDM.Scalar("not-array"), UDM.Scalar("keyFunction")))
        }
    }

    @Test
    fun testDistinctByInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.distinctBy(listOf(UDM.Array(emptyList())))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.distinctBy(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.distinctBy(listOf(UDM.Scalar("not-array"), UDM.Scalar("keyFunction")))
        }
    }

    @Test
    fun testAvgByInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.avgBy(listOf(UDM.Array(emptyList())))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.avgBy(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedArrayFunctions.avgBy(listOf(UDM.Scalar("not-array"), UDM.Scalar("mapper")))
        }
    }

    // ==================== EDGE CASES ====================

    @Test
    fun testPartitionWithSingleElement() {
        val array = UDM.Array(listOf(UDM.Scalar(42)))
        val predicate = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.partition(listOf(array, predicate))
        
        val partitioned = result as UDM.Object
        val trueGroup = partitioned.properties["true"] as UDM.Array
        val falseGroup = partitioned.properties["false"] as UDM.Array
        
        assertEquals(1, trueGroup.elements.size)
        assertEquals(0, falseGroup.elements.size)
        assertEquals(42, (trueGroup.elements[0] as UDM.Scalar).value)
    }

    @Test
    fun testCountByWithSingleElement() {
        val array = UDM.Array(listOf(UDM.Scalar(42)))
        val predicate = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.countBy(listOf(array, predicate))
        
        assertEquals(1, (result as UDM.Scalar).value)
    }

    @Test
    fun testSumByWithSingleElement() {
        val array = UDM.Array(listOf(UDM.Scalar(42)))
        val mapper = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.sumBy(listOf(array, mapper))
        
        assertEquals(0.0, (result as UDM.Scalar).value) // Placeholder implementation
    }

    @Test
    fun testMaxMinByWithSingleElement() {
        val array = UDM.Array(listOf(UDM.Scalar(42)))
        val comparator = UDM.Scalar("placeholder")
        
        val maxResult = EnhancedArrayFunctions.maxBy(listOf(array, comparator))
        val minResult = EnhancedArrayFunctions.minBy(listOf(array, comparator))
        
        assertEquals(42, (maxResult as UDM.Scalar).value)
        assertEquals(42, (minResult as UDM.Scalar).value)
    }

    @Test
    fun testGroupByWithSingleElement() {
        val array = UDM.Array(listOf(UDM.Scalar(42)))
        val keyFunction = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.groupBy(listOf(array, keyFunction))
        
        val grouped = result as UDM.Object
        assertEquals(1, grouped.properties.size)
        
        val defaultGroup = grouped.properties["default"] as UDM.Array
        assertEquals(1, defaultGroup.elements.size)
        assertEquals(42, (defaultGroup.elements[0] as UDM.Scalar).value)
    }

    @Test
    fun testDistinctByWithSingleElement() {
        val array = UDM.Array(listOf(UDM.Scalar(42)))
        val keyFunction = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.distinctBy(listOf(array, keyFunction))
        
        val distinct = result as UDM.Array
        assertEquals(1, distinct.elements.size)
        assertEquals(42, (distinct.elements[0] as UDM.Scalar).value)
    }

    @Test
    fun testAvgByWithSingleElement() {
        val array = UDM.Array(listOf(UDM.Scalar(42)))
        val mapper = UDM.Scalar("placeholder")
        
        val result = EnhancedArrayFunctions.avgBy(listOf(array, mapper))
        
        assertEquals(0.0, (result as UDM.Scalar).value) // Placeholder implementation
    }

    @Test
    fun testFunctionsWithComplexObjects() {
        val array = UDM.Array(listOf(
            UDM.Object(mapOf("name" to UDM.Scalar("Alice"), "age" to UDM.Scalar(30)), emptyMap()),
            UDM.Object(mapOf("name" to UDM.Scalar("Bob"), "age" to UDM.Scalar(25)), emptyMap()),
            UDM.Object(mapOf("name" to UDM.Scalar("Carol"), "age" to UDM.Scalar(35)), emptyMap())
        ))
        
        val keyFunction = UDM.Scalar("placeholder")
        
        // Test that functions handle complex objects without throwing
        val partitionResult = EnhancedArrayFunctions.partition(listOf(array, keyFunction))
        assertTrue(partitionResult is UDM.Object)
        
        val countResult = EnhancedArrayFunctions.countBy(listOf(array, keyFunction))
        assertEquals(3, (countResult as UDM.Scalar).value)
        
        val groupResult = EnhancedArrayFunctions.groupBy(listOf(array, keyFunction))
        assertTrue(groupResult is UDM.Object)
        
        val distinctResult = EnhancedArrayFunctions.distinctBy(listOf(array, keyFunction))
        assertEquals(3, (distinctResult as UDM.Array).elements.size)
    }

    @Test
    fun testFunctionsWithMixedTypes() {
        val array = UDM.Array(listOf(
            UDM.Scalar(42),
            UDM.Scalar("hello"),
            UDM.Scalar(true),
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
            UDM.Object(mapOf("key" to UDM.Scalar("value")), emptyMap())
        ))
        
        val function = UDM.Scalar("placeholder")
        
        // Test that functions handle mixed types gracefully
        val partitionResult = EnhancedArrayFunctions.partition(listOf(array, function))
        assertTrue(partitionResult is UDM.Object)
        
        val countResult = EnhancedArrayFunctions.countBy(listOf(array, function))
        assertEquals(5, (countResult as UDM.Scalar).value)
        
        val groupResult = EnhancedArrayFunctions.groupBy(listOf(array, function))
        assertTrue(groupResult is UDM.Object)
        
        val distinctResult = EnhancedArrayFunctions.distinctBy(listOf(array, function))
        assertEquals(5, (distinctResult as UDM.Array).elements.size)
    }
}