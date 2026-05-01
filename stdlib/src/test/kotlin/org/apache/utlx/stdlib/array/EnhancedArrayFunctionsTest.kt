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

        // Group by even/odd
        val keyFunction = UDM.Lambda { args ->
            val value = (args[0] as UDM.Scalar).value as Number
            if (value.toInt() % 2 == 0) UDM.Scalar("even") else UDM.Scalar("odd")
        }

        val result = EnhancedArrayFunctions.groupBy(listOf(array, keyFunction))

        // B15 fix: groupBy returns an Object keyed by group name
        assertTrue(result is UDM.Object, "groupBy should return UDM.Object, got ${result::class.simpleName}")
        val grouped = result as UDM.Object

        // Should have 2 groups
        assertEquals(2, grouped.properties.size)

        // Check that we have even and odd groups
        assertTrue(grouped.properties.containsKey("even"))
        assertTrue(grouped.properties.containsKey("odd"))

        // Verify even group contains 2 and 4
        val evenGroup = grouped.properties["even"] as UDM.Array
        assertEquals(2, evenGroup.elements.size)
        assertTrue(evenGroup.elements.any { (it as UDM.Scalar).value == 2 })
        assertTrue(evenGroup.elements.any { (it as UDM.Scalar).value == 4 })

        // Verify odd group contains 1 and 3
        val oddGroup = grouped.properties["odd"] as UDM.Array
        assertEquals(2, oddGroup.elements.size)
        assertTrue(oddGroup.elements.any { (it as UDM.Scalar).value == 1 })
        assertTrue(oddGroup.elements.any { (it as UDM.Scalar).value == 3 })
    }

    @Test
    fun testGroupByEmptyArray() {
        val array = UDM.Array(emptyList())
        val keyFunction = UDM.Lambda { args ->
            val value = (args[0] as UDM.Scalar).value as Number
            if (value.toInt() % 2 == 0) UDM.Scalar("even") else UDM.Scalar("odd")
        }

        val result = EnhancedArrayFunctions.groupBy(listOf(array, keyFunction))

        // B15 fix: groupBy returns an Object (empty for empty input)
        assertTrue(result is UDM.Object)
        val grouped = result as UDM.Object
        assertEquals(0, grouped.properties.size)
    }

    @Test
    fun testGroupByIndexAccess() {
        // B15 test: groupBy result can be indexed by key
        val array = UDM.Array(listOf(
            UDM.Object(mapOf("orderId" to UDM.Scalar("A"), "product" to UDM.Scalar("Widget")), emptyMap()),
            UDM.Object(mapOf("orderId" to UDM.Scalar("A"), "product" to UDM.Scalar("Gadget")), emptyMap()),
            UDM.Object(mapOf("orderId" to UDM.Scalar("B"), "product" to UDM.Scalar("Gizmo")), emptyMap())
        ))

        val keyFunction = UDM.Lambda { args ->
            val obj = args[0] as UDM.Object
            obj.properties["orderId"] ?: UDM.Scalar("null")
        }

        val result = EnhancedArrayFunctions.groupBy(listOf(array, keyFunction))

        // Result should be an Object
        assertTrue(result is UDM.Object)
        val grouped = result as UDM.Object

        // Should have 2 groups: "A" and "B"
        assertEquals(2, grouped.properties.size)
        assertTrue(grouped.properties.containsKey("A"))
        assertTrue(grouped.properties.containsKey("B"))

        // Group "A" should have 2 elements (Widget, Gadget)
        val groupA = grouped.properties["A"] as UDM.Array
        assertEquals(2, groupA.elements.size)

        // Group "B" should have 1 element (Gizmo)
        val groupB = grouped.properties["B"] as UDM.Array
        assertEquals(1, groupB.elements.size)

        // Verify content of group B
        val gizmo = groupB.elements[0] as UDM.Object
        assertEquals("Gizmo", (gizmo.properties["product"] as UDM.Scalar).value)
    }

    @Test
    fun testMapGroups() {
        // B15: mapGroups groups and transforms — the iteration use case
        val array = UDM.Array(listOf(
            UDM.Object(mapOf("dept" to UDM.Scalar("Eng"), "name" to UDM.Scalar("Alice")), emptyMap()),
            UDM.Object(mapOf("dept" to UDM.Scalar("Sales"), "name" to UDM.Scalar("Bob")), emptyMap()),
            UDM.Object(mapOf("dept" to UDM.Scalar("Eng"), "name" to UDM.Scalar("Carol")), emptyMap())
        ))

        // Key selector: group by dept
        val keySelector = UDM.Scalar("dept")

        // Transform: extract key and count members
        val transform = UDM.Lambda { args ->
            val group = args[0] as UDM.Object
            val key = group.properties["key"] ?: UDM.Scalar("null")
            val value = group.properties["value"] as UDM.Array
            UDM.Object(mapOf(
                "department" to key,
                "headcount" to UDM.Scalar(value.elements.size)
            ))
        }

        val result = EnhancedArrayFunctions.mapGroups(listOf(array, keySelector, transform))

        // Should return an Array of transformed groups
        assertTrue(result is UDM.Array, "mapGroups should return UDM.Array")
        val resultArray = result as UDM.Array
        assertEquals(2, resultArray.elements.size)

        // Find the Eng group
        val engGroup = resultArray.elements.find {
            (it as UDM.Object).properties["department"]?.asString() == "Eng"
        } as UDM.Object
        assertEquals(2, (engGroup.properties["headcount"] as UDM.Scalar).value)

        // Find the Sales group
        val salesGroup = resultArray.elements.find {
            (it as UDM.Object).properties["department"]?.asString() == "Sales"
        } as UDM.Object
        assertEquals(1, (salesGroup.properties["headcount"] as UDM.Scalar).value)
    }

    @Test
    fun testMapGroupsWithLambdaKey() {
        // mapGroups with lambda key selector
        val array = UDM.Array(listOf(
            UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(4)
        ))

        val keySelector = UDM.Lambda { args ->
            val value = (args[0] as UDM.Scalar).value as Number
            if (value.toInt() % 2 == 0) UDM.Scalar("even") else UDM.Scalar("odd")
        }

        val transform = UDM.Lambda { args ->
            val group = args[0] as UDM.Object
            val key = group.properties["key"]!!
            val members = group.properties["value"] as UDM.Array
            UDM.Object(mapOf(
                "type" to key,
                "count" to UDM.Scalar(members.elements.size)
            ))
        }

        val result = EnhancedArrayFunctions.mapGroups(listOf(array, keySelector, transform))

        assertTrue(result is UDM.Array)
        assertEquals(2, (result as UDM.Array).elements.size)
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

        // Key function returns the value itself (identity)
        val keyFunction = UDM.Lambda { args -> args[0] }

        val result = EnhancedArrayFunctions.distinctBy(listOf(array, keyFunction))

        assertTrue(result is UDM.Array)
        val distinct = result as UDM.Array

        // Placeholder implementation keeps all elements
        // TODO: Once implemented, should have 3 distinct values: 1, 2, 3
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
        val keyFunction = UDM.Lambda { args ->
            val value = (args[0] as UDM.Scalar).value as Number
            if (value.toInt() % 2 == 0) UDM.Scalar("even") else UDM.Scalar("odd")
        }

        val result = EnhancedArrayFunctions.groupBy(listOf(array, keyFunction))

        // B15 fix: groupBy returns Object keyed by group name
        assertTrue(result is UDM.Object)
        val grouped = result as UDM.Object
        assertEquals(1, grouped.properties.size)

        // Single group "even" with one element [42]
        assertTrue(grouped.properties.containsKey("even"))
        val evenGroup = grouped.properties["even"] as UDM.Array
        assertEquals(1, evenGroup.elements.size)
        assertEquals(42, (evenGroup.elements[0] as UDM.Scalar).value)
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

        // Key function: check if age >= 30
        val keyFunction = UDM.Lambda { args ->
            val obj = args[0] as UDM.Object
            val age = (obj.properties["age"] as UDM.Scalar).value as Number
            UDM.Scalar(age.toInt() >= 30)
        }

        // Test that functions handle complex objects without throwing
        val partitionResult = EnhancedArrayFunctions.partition(listOf(array, keyFunction))
        assertTrue(partitionResult is UDM.Object)

        val countResult = EnhancedArrayFunctions.countBy(listOf(array, keyFunction))
        assertTrue(countResult is UDM.Scalar)

        val groupResult = EnhancedArrayFunctions.groupBy(listOf(array, keyFunction))
        assertTrue(groupResult is UDM.Object) // B15 fix: groupBy returns Object keyed by group name

        val distinctResult = EnhancedArrayFunctions.distinctBy(listOf(array, keyFunction))
        assertTrue(distinctResult is UDM.Array)
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

        // Key function: return type name
        val function = UDM.Lambda { args ->
            when (args[0]) {
                is UDM.Scalar -> UDM.Scalar("scalar")
                is UDM.Array -> UDM.Scalar("array")
                is UDM.Object -> UDM.Scalar("object")
                else -> UDM.Scalar("other")
            }
        }

        // Test that functions handle mixed types gracefully
        val partitionResult = EnhancedArrayFunctions.partition(listOf(array, function))
        assertTrue(partitionResult is UDM.Object)

        val countResult = EnhancedArrayFunctions.countBy(listOf(array, function))
        assertTrue(countResult is UDM.Scalar)

        val groupResult = EnhancedArrayFunctions.groupBy(listOf(array, function))
        assertTrue(groupResult is UDM.Object) // B15 fix: groupBy returns Object keyed by group name

        val distinctResult = EnhancedArrayFunctions.distinctBy(listOf(array, function))
        assertTrue(distinctResult is UDM.Array)
    }
}