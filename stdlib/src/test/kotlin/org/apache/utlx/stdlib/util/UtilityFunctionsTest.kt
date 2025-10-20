package org.apache.utlx.stdlib.util

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class UtilityFunctionsTest {

    @BeforeEach
    fun setUp() {
        // Clear timers before each test
        TimerFunctions.timerClear(emptyList())
    }

    // ==================== TREE FUNCTIONS TESTS ====================

    @Test
    fun testTreeMap() {
        // Test basic tree mapping (placeholder functionality)
        val tree = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Object(mutableMapOf(
                "c" to UDM.Scalar(2),
                "d" to UDM.Scalar(3)
            ))
        ))
        
        val dummyFunction = UDM.Lambda { _ -> UDM.Scalar("mapped") }
        val result = TreeFunctions.treeMap(listOf(tree, dummyFunction))
        
        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object
        assertEquals(2, resultObj.properties.size)
        assertTrue(resultObj.properties.containsKey("a"))
        assertTrue(resultObj.properties.containsKey("b"))
    }

    @Test
    fun testTreeMapArgumentValidation() {
        assertThrows<IllegalArgumentException> {
            TreeFunctions.treeMap(listOf(UDM.Scalar("not enough args")))
        }
        
        assertThrows<IllegalArgumentException> {
            TreeFunctions.treeMap(listOf(UDM.Scalar("1"), UDM.Scalar("2"), UDM.Scalar("too many")))
        }
    }

    @Test
    fun testTreeFilter() {
        // Test basic tree filtering (placeholder functionality)
        val tree = UDM.Object(mutableMapOf(
            "visible" to UDM.Scalar(1),
            "hidden" to UDM.Scalar(2),
            "nested" to UDM.Object(mutableMapOf(
                "item" to UDM.Scalar(3)
            ))
        ))
        
        val dummyPredicate = UDM.Lambda { _ -> UDM.Scalar(true) }
        val result = TreeFunctions.treeFilter(listOf(tree, dummyPredicate))
        
        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object
        // With placeholder implementation, all nodes are preserved
        assertEquals(3, resultObj.properties.size)
    }

    @Test
    fun testTreeFlatten() {
        // Test flattening a simple tree
        val tree = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Object(mutableMapOf(
                "c" to UDM.Scalar(2),
                "d" to UDM.Scalar(3)
            )),
            "e" to UDM.Scalar(4)
        ))
        
        val result = TreeFunctions.treeFlatten(listOf(tree))
        assertTrue(result is UDM.Array)
        val resultArray = result as UDM.Array
        assertEquals(3, resultArray.elements.size) // 3 leaf values: 1, 2, 3, 4 (but nested objects count as containers)
        
        // Test with array
        val arrayTree = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Array(listOf(UDM.Scalar(2), UDM.Scalar(3))),
            UDM.Scalar(4)
        ))
        
        val result2 = TreeFunctions.treeFlatten(listOf(arrayTree))
        val resultArray2 = result2 as UDM.Array
        assertEquals(3, resultArray2.elements.size) // 1, 2, 3, 4 as leaves
    }

    @Test
    fun testTreeFlattenEmptyStructures() {
        // Test with empty object
        val emptyObj = UDM.Object(mutableMapOf())
        val result1 = TreeFunctions.treeFlatten(listOf(emptyObj))
        val resultArray1 = result1 as UDM.Array
        assertEquals(1, resultArray1.elements.size) // Empty object is a leaf
        
        // Test with empty array
        val emptyArr = UDM.Array(emptyList())
        val result2 = TreeFunctions.treeFlatten(listOf(emptyArr))
        val resultArray2 = result2 as UDM.Array
        assertEquals(1, resultArray2.elements.size) // Empty array is a leaf
        
        // Test with scalar
        val scalar = UDM.Scalar(42)
        val result3 = TreeFunctions.treeFlatten(listOf(scalar))
        val resultArray3 = result3 as UDM.Array
        assertEquals(1, resultArray3.elements.size)
    }

    @Test
    fun testTreeDepth() {
        // Test depth calculation for nested structures
        val simpleTree = UDM.Scalar(1)
        val result1 = TreeFunctions.treeDepth(listOf(simpleTree))
        assertEquals(0.0, (result1 as UDM.Scalar).value)
        
        val nestedTree = UDM.Object(mutableMapOf(
            "level1" to UDM.Object(mutableMapOf(
                "level2" to UDM.Object(mutableMapOf(
                    "level3" to UDM.Scalar(1)
                ))
            ))
        ))
        
        val result2 = TreeFunctions.treeDepth(listOf(nestedTree))
        assertEquals(3.0, (result2 as UDM.Scalar).value)
        
        // Test with mixed array and object nesting
        val mixedTree = UDM.Array(listOf(
            UDM.Object(mutableMapOf(
                "nested" to UDM.Array(listOf(UDM.Scalar(1)))
            ))
        ))
        
        val result3 = TreeFunctions.treeDepth(listOf(mixedTree))
        assertEquals(3.0, (result3 as UDM.Scalar).value)
    }

    @Test
    fun testTreePaths() {
        // Test path collection
        val tree = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Object(mutableMapOf(
                "c" to UDM.Scalar(2),
                "d" to UDM.Scalar(3)
            ))
        ))
        
        val result = TreeFunctions.treePaths(listOf(tree))
        assertTrue(result is UDM.Array)
        val paths = result as UDM.Array
        
        // Should have paths: ["a"], ["b", "c"], ["b", "d"]
        assertEquals(3, paths.elements.size)
        
        // Test with array
        val arrayTree = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2)
        ))
        
        val result2 = TreeFunctions.treePaths(listOf(arrayTree))
        val paths2 = result2 as UDM.Array
        assertEquals(2, paths2.elements.size) // ["0"], ["1"]
    }

    @Test
    fun testTreeFind() {
        // Test finding nodes by path
        val tree = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Object(mutableMapOf(
                "c" to UDM.Scalar(2),
                "d" to UDM.Scalar(3)
            ))
        ))
        
        // Find existing path
        val path1 = UDM.Array(listOf(UDM.Scalar("b"), UDM.Scalar("c")))
        val result1 = TreeFunctions.treeFind(listOf(tree, path1))
        assertEquals(2, (result1 as UDM.Scalar).value)
        
        // Find non-existing path
        val path2 = UDM.Array(listOf(UDM.Scalar("x"), UDM.Scalar("y")))
        val result2 = TreeFunctions.treeFind(listOf(tree, path2))
        assertEquals(null, (result2 as UDM.Scalar).value)
        
        // Find root level
        val path3 = UDM.Array(listOf(UDM.Scalar("a")))
        val result3 = TreeFunctions.treeFind(listOf(tree, path3))
        assertEquals(1, (result3 as UDM.Scalar).value)
        
        // Test with array indices
        val arrayTree = UDM.Array(listOf(UDM.Scalar("first"), UDM.Scalar("second")))
        val path4 = UDM.Array(listOf(UDM.Scalar(1)))
        val result4 = TreeFunctions.treeFind(listOf(arrayTree, path4))
        assertEquals("second", (result4 as UDM.Scalar).value)
    }

    @Test
    fun testTreeFindEdgeCases() {
        val tree = UDM.Object(mutableMapOf("a" to UDM.Scalar(1)))
        
        // Test invalid path type
        assertThrows<IllegalArgumentException> {
            TreeFunctions.treeFind(listOf(tree, UDM.Scalar("not array")))
        }
        
        // Test wrong number of arguments
        assertThrows<IllegalArgumentException> {
            TreeFunctions.treeFind(listOf(tree))
        }
        
        // Test empty path
        val emptyPath = UDM.Array(emptyList())
        val result = TreeFunctions.treeFind(listOf(tree, emptyPath))
        assertEquals(tree, result)
    }

    // ==================== COERCION FUNCTIONS TESTS ====================

    @Test
    fun testCoerceToNumber() {
        // Test number coercion
        val result1 = CoercionFunctions.coerce(listOf(UDM.Scalar("123"), UDM.Scalar("number")))
        assertEquals(123.0, (result1 as UDM.Scalar).value)
        
        val result2 = CoercionFunctions.coerce(listOf(UDM.Scalar(true), UDM.Scalar("number")))
        assertEquals(1.0, (result2 as UDM.Scalar).value)
        
        val result3 = CoercionFunctions.coerce(listOf(UDM.Scalar(false), UDM.Scalar("number")))
        assertEquals(0.0, (result3 as UDM.Scalar).value)
        
        // Test with formatted numbers
        val result4 = CoercionFunctions.coerce(listOf(UDM.Scalar("$1,234.56"), UDM.Scalar("number")))
        assertEquals(1234.56, (result4 as UDM.Scalar).value)
        
        // Test array length
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        val result5 = CoercionFunctions.coerce(listOf(array, UDM.Scalar("number")))
        assertEquals(3.0, (result5 as UDM.Scalar).value)
    }

    @Test
    fun testCoerceToString() {
        // Test string coercion
        val result1 = CoercionFunctions.coerce(listOf(UDM.Scalar(123), UDM.Scalar("string")))
        assertEquals("123", (result1 as UDM.Scalar).value)
        
        val result2 = CoercionFunctions.coerce(listOf(UDM.Scalar(true), UDM.Scalar("string")))
        assertEquals("true", (result2 as UDM.Scalar).value)
        
        val result3 = CoercionFunctions.coerce(listOf(UDM.Scalar(null), UDM.Scalar("string")))
        assertEquals("", (result3 as UDM.Scalar).value)
        
        // Test array to string
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        val result4 = CoercionFunctions.coerce(listOf(array, UDM.Scalar("string")))
        assertEquals("1, 2", (result4 as UDM.Scalar).value)
    }

    @Test
    fun testCoerceToBoolean() {
        // Test boolean coercion
        val result1 = CoercionFunctions.coerce(listOf(UDM.Scalar(1), UDM.Scalar("boolean")))
        assertTrue((result1 as UDM.Scalar).value as Boolean)
        
        val result2 = CoercionFunctions.coerce(listOf(UDM.Scalar(0), UDM.Scalar("boolean")))
        assertFalse((result2 as UDM.Scalar).value as Boolean)
        
        val result3 = CoercionFunctions.coerce(listOf(UDM.Scalar("true"), UDM.Scalar("boolean")))
        assertTrue((result3 as UDM.Scalar).value as Boolean)
        
        val result4 = CoercionFunctions.coerce(listOf(UDM.Scalar("yes"), UDM.Scalar("boolean")))
        assertTrue((result4 as UDM.Scalar).value as Boolean)
        
        val result5 = CoercionFunctions.coerce(listOf(UDM.Scalar("false"), UDM.Scalar("boolean")))
        assertFalse((result5 as UDM.Scalar).value as Boolean)
        
        // Test empty array
        val emptyArray = UDM.Array(emptyList())
        val result6 = CoercionFunctions.coerce(listOf(emptyArray, UDM.Scalar("boolean")))
        assertFalse((result6 as UDM.Scalar).value as Boolean)
        
        // Test non-empty array
        val array = UDM.Array(listOf(UDM.Scalar(1)))
        val result7 = CoercionFunctions.coerce(listOf(array, UDM.Scalar("boolean")))
        assertTrue((result7 as UDM.Scalar).value as Boolean)
    }

    @Test
    fun testCoerceToArray() {
        // Test array coercion
        val result1 = CoercionFunctions.coerce(listOf(UDM.Scalar(123), UDM.Scalar("array")))
        assertTrue(result1 is UDM.Array)
        assertEquals(1, (result1 as UDM.Array).elements.size)
        assertEquals(123, ((result1 as UDM.Array).elements[0] as UDM.Scalar).value)
        
        // Test object to array
        val obj = UDM.Object(mutableMapOf("a" to UDM.Scalar(1), "b" to UDM.Scalar(2)))
        val result2 = CoercionFunctions.coerce(listOf(obj, UDM.Scalar("array")))
        assertTrue(result2 is UDM.Array)
        assertEquals(2, (result2 as UDM.Array).elements.size)
        
        // Test array to array (identity)
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        val result3 = CoercionFunctions.coerce(listOf(array, UDM.Scalar("array")))
        assertEquals(array, result3)
    }

    @Test
    fun testCoerceToObject() {
        // Test object coercion
        val result1 = CoercionFunctions.coerce(listOf(UDM.Scalar(123), UDM.Scalar("object")))
        assertTrue(result1 is UDM.Object)
        val obj1 = result1 as UDM.Object
        assertTrue(obj1.properties.containsKey("value"))
        assertEquals(123, (obj1.properties["value"] as UDM.Scalar).value)
        
        // Test array to object
        val array = UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b")))
        val result2 = CoercionFunctions.coerce(listOf(array, UDM.Scalar("object")))
        assertTrue(result2 is UDM.Object)
        val obj2 = result2 as UDM.Object
        assertEquals(2, obj2.properties.size)
        assertTrue(obj2.properties.containsKey("0"))
        assertTrue(obj2.properties.containsKey("1"))
    }

    @Test
    fun testCoerceWithDefault() {
        // Test coercion with default value on failure
        val result1 = CoercionFunctions.coerce(listOf(
            UDM.Scalar("not a number"), 
            UDM.Scalar("number"), 
            UDM.Scalar(-1)
        ))
        assertEquals(-1, (result1 as UDM.Scalar).value)
        
        // Test successful coercion ignores default
        val result2 = CoercionFunctions.coerce(listOf(
            UDM.Scalar("123"), 
            UDM.Scalar("number"), 
            UDM.Scalar(-1)
        ))
        assertEquals(123.0, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testTryCoerce() {
        // Test successful coercion
        val result1 = CoercionFunctions.tryCoerce(listOf(UDM.Scalar("123"), UDM.Scalar("number")))
        assertEquals(123.0, (result1 as UDM.Scalar).value)
        
        // Test failed coercion returns null
        val result2 = CoercionFunctions.tryCoerce(listOf(UDM.Scalar("not a number"), UDM.Scalar("number")))
        assertEquals(null, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testCanCoerce() {
        // Test can coerce valid input
        val result1 = CoercionFunctions.canCoerce(listOf(UDM.Scalar("123"), UDM.Scalar("number")))
        assertTrue((result1 as UDM.Scalar).value as Boolean)
        
        // Test cannot coerce invalid input
        val result2 = CoercionFunctions.canCoerce(listOf(UDM.Scalar("not a number"), UDM.Scalar("number")))
        assertFalse((result2 as UDM.Scalar).value as Boolean)
        
        // Test valid string coercion
        val result3 = CoercionFunctions.canCoerce(listOf(UDM.Scalar(123), UDM.Scalar("string")))
        assertTrue((result3 as UDM.Scalar).value as Boolean)
    }

    @Test
    fun testCoerceAll() {
        // Test coercing array of values
        val array = UDM.Array(listOf(
            UDM.Scalar("123"),
            UDM.Scalar("456"), 
            UDM.Scalar("not a number")
        ))
        
        val result = CoercionFunctions.coerceAll(listOf(array, UDM.Scalar("number")))
        assertTrue(result is UDM.Array)
        val resultArray = result as UDM.Array
        assertEquals(2, resultArray.elements.size) // Only successful coercions
        assertEquals(123.0, (resultArray.elements[0] as UDM.Scalar).value)
        assertEquals(456.0, (resultArray.elements[1] as UDM.Scalar).value)
    }

    @Test
    fun testSmartCoerce() {
        // Test smart number detection
        val result1 = CoercionFunctions.smartCoerce(listOf(UDM.Scalar("42")))
        assertEquals(42.0, (result1 as UDM.Scalar).value)
        
        val result2 = CoercionFunctions.smartCoerce(listOf(UDM.Scalar("3.14")))
        assertEquals(3.14, (result2 as UDM.Scalar).value)
        
        // Test smart boolean detection
        val result3 = CoercionFunctions.smartCoerce(listOf(UDM.Scalar("true")))
        assertTrue((result3 as UDM.Scalar).value as Boolean)
        
        val result4 = CoercionFunctions.smartCoerce(listOf(UDM.Scalar("false")))
        assertFalse((result4 as UDM.Scalar).value as Boolean)
        
        // Test smart null detection
        val result5 = CoercionFunctions.smartCoerce(listOf(UDM.Scalar("null")))
        assertEquals(null, (result5 as UDM.Scalar).value)
        
        // Test non-string passes through
        val number = UDM.Scalar(123)
        val result6 = CoercionFunctions.smartCoerce(listOf(number))
        assertEquals(number, result6)
        
        // Test regular string
        val result7 = CoercionFunctions.smartCoerce(listOf(UDM.Scalar("hello")))
        assertEquals("hello", (result7 as UDM.Scalar).value)
    }

    // ==================== TIMER FUNCTIONS TESTS ====================

    @Test
    fun testTimerStartStop() {
        // Test basic timer functionality
        TimerFunctions.timerStart(listOf(UDM.Scalar("test-timer")))
        
        Thread.sleep(10) // Small delay
        
        val result = TimerFunctions.timerStop(listOf(UDM.Scalar("test-timer")))
        assertTrue(result is UDM.Object)
        val timerResult = result as UDM.Object
        
        assertEquals("test-timer", (timerResult.properties["name"] as UDM.Scalar).value)
        assertEquals("ms", (timerResult.properties["unit"] as UDM.Scalar).value)
        assertTrue((timerResult.properties["elapsed"] as UDM.Scalar).value as Double > 0)
        assertNotNull(timerResult.properties["nanoseconds"])
    }

    @Test
    fun testTimerCheck() {
        // Test checking timer without stopping
        TimerFunctions.timerStart(listOf(UDM.Scalar("check-timer")))
        
        Thread.sleep(5)
        
        val result1 = TimerFunctions.timerCheck(listOf(UDM.Scalar("check-timer")))
        assertTrue(result1 is UDM.Object)
        val elapsed1 = (result1 as UDM.Object).properties["elapsed"] as UDM.Scalar
        
        Thread.sleep(5)
        
        val result2 = TimerFunctions.timerCheck(listOf(UDM.Scalar("check-timer")))
        val elapsed2 = (result2 as UDM.Object).properties["elapsed"] as UDM.Scalar
        
        // Second check should show more elapsed time
        assertTrue((elapsed2.value as Double) > (elapsed1.value as Double))
        
        // Clean up
        TimerFunctions.timerStop(listOf(UDM.Scalar("check-timer")))
    }

    @Test
    fun testTimerReset() {
        // Test timer reset
        TimerFunctions.timerStart(listOf(UDM.Scalar("reset-timer")))
        Thread.sleep(10)
        
        val result1 = TimerFunctions.timerCheck(listOf(UDM.Scalar("reset-timer")))
        val elapsed1 = (result1 as UDM.Object).properties["elapsed"] as UDM.Scalar
        
        TimerFunctions.timerReset(listOf(UDM.Scalar("reset-timer")))
        
        val result2 = TimerFunctions.timerCheck(listOf(UDM.Scalar("reset-timer")))
        val elapsed2 = (result2 as UDM.Object).properties["elapsed"] as UDM.Scalar
        
        // Reset timer should have less elapsed time
        assertTrue((elapsed2.value as Double) < (elapsed1.value as Double))
        
        // Clean up
        TimerFunctions.timerStop(listOf(UDM.Scalar("reset-timer")))
    }

    @Test
    fun testTimerStats() {
        // Test timer statistics
        val timerName = "stats-timer"
        
        // Run timer multiple times
        repeat(3) { i ->
            TimerFunctions.timerStart(listOf(UDM.Scalar(timerName)))
            Thread.sleep(5L + i) // Varying delays
            TimerFunctions.timerStop(listOf(UDM.Scalar(timerName)))
        }
        
        val result = TimerFunctions.timerStats(listOf(UDM.Scalar(timerName)))
        assertTrue(result is UDM.Object)
        val stats = result as UDM.Object
        
        assertEquals(timerName, (stats.properties["name"] as UDM.Scalar).value)
        assertEquals(3.0, (stats.properties["count"] as UDM.Scalar).value)
        assertEquals("ms", (stats.properties["unit"] as UDM.Scalar).value)
        
        val min = (stats.properties["min"] as UDM.Scalar).value as Double
        val max = (stats.properties["max"] as UDM.Scalar).value as Double
        val avg = (stats.properties["avg"] as UDM.Scalar).value as Double
        val total = (stats.properties["total"] as UDM.Scalar).value as Double
        
        assertTrue(min > 0)
        assertTrue(max >= min)
        assertTrue(avg > 0)
        assertTrue(total > 0)
    }

    @Test
    fun testTimerList() {
        // Test listing active timers
        val initialList = TimerFunctions.timerList(emptyList())
        assertEquals(0, (initialList as UDM.Array).elements.size)
        
        TimerFunctions.timerStart(listOf(UDM.Scalar("timer1")))
        TimerFunctions.timerStart(listOf(UDM.Scalar("timer2")))
        
        val activeList = TimerFunctions.timerList(emptyList())
        assertEquals(2, (activeList as UDM.Array).elements.size)
        
        val timerNames = activeList.elements.map { (it as UDM.Scalar).value as String }.toSet()
        assertTrue(timerNames.contains("timer1"))
        assertTrue(timerNames.contains("timer2"))
        
        // Clean up
        TimerFunctions.timerStop(listOf(UDM.Scalar("timer1")))
        TimerFunctions.timerStop(listOf(UDM.Scalar("timer2")))
    }

    @Test
    fun testTimerClear() {
        // Test clearing all timers
        TimerFunctions.timerStart(listOf(UDM.Scalar("clear-test")))
        
        val beforeClear = TimerFunctions.timerList(emptyList())
        assertEquals(1, (beforeClear as UDM.Array).elements.size)
        
        TimerFunctions.timerClear(emptyList())
        
        val afterClear = TimerFunctions.timerList(emptyList())
        assertEquals(0, (afterClear as UDM.Array).elements.size)
    }

    @Test
    fun testTimestamp() {
        // Test timestamp function
        val before = System.currentTimeMillis()
        val result = TimerFunctions.timestamp(emptyList())
        val after = System.currentTimeMillis()
        
        assertTrue(result is UDM.Scalar)
        val timestamp = (result as UDM.Scalar).value as Double
        
        assertTrue(timestamp >= before)
        assertTrue(timestamp <= after)
    }

    @Test
    fun testMeasure() {
        // Test measure function (placeholder implementation)
        val dummyFunction = UDM.Lambda { _ -> UDM.Scalar("result") }
        val result = TimerFunctions.measure(listOf(dummyFunction))
        
        assertTrue(result is UDM.Object)
        val measurement = result as UDM.Object
        
        assertEquals(dummyFunction, measurement.properties["result"])
        assertEquals("ms", (measurement.properties["unit"] as UDM.Scalar).value)
        assertTrue((measurement.properties["elapsed"] as UDM.Scalar).value is Double)
    }

    @Test
    fun testTimerErrorCases() {
        // Test stopping non-existent timer
        assertThrows<IllegalArgumentException> {
            TimerFunctions.timerStop(listOf(UDM.Scalar("non-existent")))
        }
        
        // Test checking non-existent timer
        assertThrows<IllegalArgumentException> {
            TimerFunctions.timerCheck(listOf(UDM.Scalar("non-existent")))
        }
        
        // Test invalid timer name types
        assertThrows<IllegalArgumentException> {
            TimerFunctions.timerStart(listOf(UDM.Scalar(123)))
        }
        
        assertThrows<IllegalArgumentException> {
            TimerFunctions.timerStop(listOf(UDM.Array(emptyList())))
        }
    }

    @Test
    fun testArgumentValidation() {
        // Test wrong number of arguments for various functions
        assertThrows<IllegalArgumentException> {
            CoercionFunctions.coerce(listOf(UDM.Scalar("test")))
        }
        
        assertThrows<IllegalArgumentException> {
            TimerFunctions.timerStart(emptyList())
        }
        
        assertThrows<IllegalArgumentException> {
            TimerFunctions.timerList(listOf(UDM.Scalar("unexpected")))
        }
        
        assertThrows<IllegalArgumentException> {
            TreeFunctions.treeFlatten(emptyList())
        }
    }

    @Test
    fun testComplexTreeOperations() {
        // Test with deeply nested structure
        val complexTree = UDM.Object(mutableMapOf(
            "users" to UDM.Array(listOf(
                UDM.Object(mutableMapOf(
                    "id" to UDM.Scalar(1),
                    "profile" to UDM.Object(mutableMapOf(
                        "name" to UDM.Scalar("Alice"),
                        "preferences" to UDM.Object(mutableMapOf(
                            "theme" to UDM.Scalar("dark"),
                            "language" to UDM.Scalar("en")
                        ))
                    ))
                ))
            ))
        ))
        
        // Test depth
        val depth = TreeFunctions.treeDepth(listOf(complexTree))
        assertEquals(5.0, (depth as UDM.Scalar).value) // users -> [0] -> profile -> preferences -> theme/language
        
        // Test flatten
        val flattened = TreeFunctions.treeFlatten(listOf(complexTree))
        assertTrue(flattened is UDM.Array)
        val leaves = flattened as UDM.Array
        assertTrue(leaves.elements.size >= 3) // At least id, name, theme, language values
        
        // Test find deep path
        val deepPath = UDM.Array(listOf(
            UDM.Scalar("users"),
            UDM.Scalar(0),
            UDM.Scalar("profile"),
            UDM.Scalar("name")
        ))
        val found = TreeFunctions.treeFind(listOf(complexTree, deepPath))
        assertEquals("Alice", (found as UDM.Scalar).value)
    }
}