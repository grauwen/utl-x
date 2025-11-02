package org.apache.utlx.stdlib.objects

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnhancedObjectFunctionsTest {

    @Test
    fun testDivideBy() {
        // Test dividing into chunks of 2
        val obj1 = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(3),
            "d" to UDM.Scalar(4),
            "e" to UDM.Scalar(5)
        ))
        
        val result1 = EnhancedObjectFunctions.divideBy(listOf(obj1, UDM.Scalar(2)))
        assertTrue(result1 is UDM.Array)
        val chunks = (result1 as UDM.Array).elements
        assertEquals(3, chunks.size) // 5 entries divided by 2 = 3 chunks
        
        // First chunk should have 2 entries
        val firstChunk = chunks[0] as UDM.Object
        assertEquals(2, firstChunk.properties.size)
        
        // Last chunk should have 1 entry
        val lastChunk = chunks[2] as UDM.Object
        assertEquals(1, lastChunk.properties.size)

        // Test dividing into chunks of 1
        val result2 = EnhancedObjectFunctions.divideBy(listOf(obj1, UDM.Scalar(1)))
        val chunks2 = (result2 as UDM.Array).elements
        assertEquals(5, chunks2.size) // 5 entries divided by 1 = 5 chunks
        chunks2.forEach { chunk ->
            assertEquals(1, (chunk as UDM.Object).properties.size)
        }

        // Test with chunk size larger than object
        val smallObj = UDM.Object(mutableMapOf(
            "x" to UDM.Scalar(10),
            "y" to UDM.Scalar(20)
        ))
        val result3 = EnhancedObjectFunctions.divideBy(listOf(smallObj, UDM.Scalar(5)))
        val chunks3 = (result3 as UDM.Array).elements
        assertEquals(1, chunks3.size) // All entries fit in one chunk
        assertEquals(2, (chunks3[0] as UDM.Object).properties.size)
    }

    @Test
    fun testDivideByEdgeCases() {
        // Test empty object
        val emptyObj = UDM.Object(mutableMapOf())
        val result1 = EnhancedObjectFunctions.divideBy(listOf(emptyObj, UDM.Scalar(2)))
        assertEquals(0, (result1 as UDM.Array).elements.size)

        // Test invalid chunk size
        val obj = UDM.Object(mutableMapOf("a" to UDM.Scalar(1)))
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.divideBy(listOf(obj, UDM.Scalar(0)))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.divideBy(listOf(obj, UDM.Scalar(-1)))
        }

        // Test non-object argument
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.divideBy(listOf(UDM.Scalar("not object"), UDM.Scalar(2)))
        }

        // Test insufficient arguments
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.divideBy(listOf(obj))
        }
    }

    @Test
    fun testSomeEntry() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(5),
            "b" to UDM.Scalar(15),
            "c" to UDM.Scalar(3)
        ))

        // Test basic functionality - predicate returns true for all entries
        val dummyPredicate = UDM.Lambda { _ -> UDM.Scalar(true) }
        val result = EnhancedObjectFunctions.someEntry(listOf(obj, dummyPredicate))

        assertTrue(result is UDM.Scalar)
        // Since predicate returns true for all entries, someEntry should return true
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test
    fun testEveryEntry() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(3)
        ))
        
        // Test basic functionality (currently returns true due to placeholder implementation)
        val dummyPredicate = UDM.Lambda { _ -> UDM.Scalar(true) }
        val result = EnhancedObjectFunctions.everyEntry(listOf(obj, dummyPredicate))
        
        assertTrue(result is UDM.Scalar)
        // Note: Due to placeholder implementation, this will be true
        assertEquals(true, (result as UDM.Scalar).value)

        // Test empty object (should return true)
        val emptyObj = UDM.Object(mutableMapOf())
        val result2 = EnhancedObjectFunctions.everyEntry(listOf(emptyObj, dummyPredicate))
        assertEquals(true, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testMapEntries() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2)
        ))

        // Mapper that returns object with key and value properties (keeps entries unchanged)
        val dummyMapper = UDM.Lambda { args ->
            UDM.Object(mutableMapOf(
                "key" to args[0],
                "value" to args[1]
            ))
        }
        val result = EnhancedObjectFunctions.mapEntries(listOf(obj, dummyMapper))

        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object

        // Mapper keeps entries unchanged, so original keys and values remain
        assertEquals(2, resultObj.properties.size)
        assertTrue(resultObj.properties.containsKey("a"))
        assertTrue(resultObj.properties.containsKey("b"))
        assertEquals(1, (resultObj.properties["a"] as UDM.Scalar).value)
        assertEquals(2, (resultObj.properties["b"] as UDM.Scalar).value)
    }

    @Test
    fun testFilterEntries() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(3),
            "d" to UDM.Scalar(4)
        ))
        
        // Test basic functionality (currently returns all entries due to placeholder)
        val dummyPredicate = UDM.Lambda { _ -> UDM.Scalar(true) }
        val result = EnhancedObjectFunctions.filterEntries(listOf(obj, dummyPredicate))
        
        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object
        
        // In placeholder implementation, should return all entries
        assertEquals(4, resultObj.properties.size)
        assertTrue(resultObj.properties.containsKey("a"))
        assertTrue(resultObj.properties.containsKey("b"))
        assertTrue(resultObj.properties.containsKey("c"))
        assertTrue(resultObj.properties.containsKey("d"))

        // Test empty object
        val emptyObj = UDM.Object(mutableMapOf())
        val result2 = EnhancedObjectFunctions.filterEntries(listOf(emptyObj, dummyPredicate))
        assertEquals(0, (result2 as UDM.Object).properties.size)
    }

    @Test
    fun testReduceEntries() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(3)
        ))

        // Lambda that always returns true
        val dummyReducer = UDM.Lambda { _ -> UDM.Scalar(true) }
        val initialValue = UDM.Scalar(0)

        // The reducer will be called for each entry and returns true
        val result = EnhancedObjectFunctions.reduceEntries(listOf(obj, dummyReducer, initialValue))

        // After processing entries with dummy reducer that returns true, result should be true
        assertEquals(true, (result as UDM.Scalar).value)

        // Test with different initial value - reducer still returns true
        val initialString = UDM.Scalar("start")
        val result2 = EnhancedObjectFunctions.reduceEntries(listOf(obj, dummyReducer, initialString))
        assertEquals(true, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testCountEntries() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(3),
            "d" to UDM.Scalar(4)
        ))
        
        val dummyPredicate = UDM.Lambda { _ -> UDM.Scalar(true) }
        val result = EnhancedObjectFunctions.countEntries(listOf(obj, dummyPredicate))
        
        assertTrue(result is UDM.Scalar)
        // In placeholder implementation, counts all entries (predicate always returns true)
        assertEquals(4, (result as UDM.Scalar).value)

        // Test empty object
        val emptyObj = UDM.Object(mutableMapOf())
        val result2 = EnhancedObjectFunctions.countEntries(listOf(emptyObj, dummyPredicate))
        assertEquals(0, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testMapKeys() {
        val obj = UDM.Object(mutableMapOf(
            "firstName" to UDM.Scalar("Alice"),
            "lastName" to UDM.Scalar("Smith")
        ))

        // Mapper that transforms all keys to "mapped"
        val dummyMapper = UDM.Lambda { _ -> UDM.Scalar("mapped") }
        val result = EnhancedObjectFunctions.mapKeys(listOf(obj, dummyMapper))

        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object

        // Since all keys become "mapped", duplicate keys collapse into 1 property
        // The last entry wins (lastName's value "Smith")
        assertEquals(1, resultObj.properties.size)
        assertTrue(resultObj.properties.containsKey("mapped"))
        assertEquals("Smith", (resultObj.properties["mapped"] as UDM.Scalar).value)
    }

    @Test
    fun testMapValues() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2),
            "c" to UDM.Scalar(3)
        ))

        // Mapper that transforms all values to "mapped"
        val dummyMapper = UDM.Lambda { _ -> UDM.Scalar("mapped") }
        val result = EnhancedObjectFunctions.mapValues(listOf(obj, dummyMapper))

        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object

        // All values should be transformed to "mapped"
        assertEquals(3, resultObj.properties.size)
        assertEquals("mapped", (resultObj.properties["a"] as UDM.Scalar).value)
        assertEquals("mapped", (resultObj.properties["b"] as UDM.Scalar).value)
        assertEquals("mapped", (resultObj.properties["c"] as UDM.Scalar).value)
    }

    @Test
    fun testArgumentValidation() {
        val validObj = UDM.Object(mutableMapOf("key" to UDM.Scalar("value")))
        val dummyFunction = UDM.Lambda { _ -> UDM.Scalar(true) }
        
        // Test insufficient arguments for various functions
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.someEntry(listOf(validObj))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.everyEntry(listOf(validObj))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.mapEntries(listOf(validObj))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.filterEntries(listOf(validObj))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.reduceEntries(listOf(validObj, dummyFunction))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.countEntries(listOf(validObj))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.mapKeys(listOf(validObj))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.mapValues(listOf(validObj))
        }

        // Test non-object arguments
        val nonObject = UDM.Scalar("not an object")
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.someEntry(listOf(nonObject, dummyFunction))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.everyEntry(listOf(nonObject, dummyFunction))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.mapEntries(listOf(nonObject, dummyFunction))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.filterEntries(listOf(nonObject, dummyFunction))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.reduceEntries(listOf(nonObject, dummyFunction, UDM.Scalar(0)))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.countEntries(listOf(nonObject, dummyFunction))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.mapKeys(listOf(nonObject, dummyFunction))
        }
        
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.mapValues(listOf(nonObject, dummyFunction))
        }
    }

    @Test
    fun testComplexObjectDivision() {
        // Test with object containing different value types
        val complexObj = UDM.Object(mutableMapOf(
            "string" to UDM.Scalar("hello"),
            "number" to UDM.Scalar(42),
            "boolean" to UDM.Scalar(true),
            "array" to UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
            "object" to UDM.Object(mutableMapOf("nested" to UDM.Scalar("value"))),
            "null" to UDM.Scalar(null)
        ))
        
        val result = EnhancedObjectFunctions.divideBy(listOf(complexObj, UDM.Scalar(3)))
        val chunks = (result as UDM.Array).elements
        
        assertEquals(2, chunks.size) // 6 entries divided by 3 = 2 chunks
        assertEquals(3, (chunks[0] as UDM.Object).properties.size)
        assertEquals(3, (chunks[1] as UDM.Object).properties.size)
        
        // Verify that all original entries are preserved
        val allEntries = mutableMapOf<String, UDM>()
        chunks.forEach { chunk ->
            val chunkObj = chunk as UDM.Object
            allEntries.putAll(chunkObj.properties)
        }
        
        assertEquals(6, allEntries.size)
        assertTrue(allEntries.containsKey("string"))
        assertTrue(allEntries.containsKey("number"))
        assertTrue(allEntries.containsKey("boolean"))
        assertTrue(allEntries.containsKey("array"))
        assertTrue(allEntries.containsKey("object"))
        assertTrue(allEntries.containsKey("null"))
    }

    @Test
    fun testFunctionsWithEmptyObjects() {
        val emptyObj = UDM.Object(mutableMapOf())
        val dummyFunction = UDM.Lambda { _ -> UDM.Scalar(true) }
        
        // Test all functions with empty objects
        val divideResult = EnhancedObjectFunctions.divideBy(listOf(emptyObj, UDM.Scalar(2)))
        assertEquals(0, (divideResult as UDM.Array).elements.size)
        
        val someResult = EnhancedObjectFunctions.someEntry(listOf(emptyObj, dummyFunction))
        assertEquals(false, (someResult as UDM.Scalar).value)
        
        val everyResult = EnhancedObjectFunctions.everyEntry(listOf(emptyObj, dummyFunction))
        assertEquals(true, (everyResult as UDM.Scalar).value) // Vacuously true
        
        val mapEntriesResult = EnhancedObjectFunctions.mapEntries(listOf(emptyObj, dummyFunction))
        assertEquals(0, (mapEntriesResult as UDM.Object).properties.size)
        
        val filterResult = EnhancedObjectFunctions.filterEntries(listOf(emptyObj, dummyFunction))
        assertEquals(0, (filterResult as UDM.Object).properties.size)
        
        val reduceResult = EnhancedObjectFunctions.reduceEntries(listOf(emptyObj, dummyFunction, UDM.Scalar("initial")))
        assertEquals("initial", (reduceResult as UDM.Scalar).value)
        
        val countResult = EnhancedObjectFunctions.countEntries(listOf(emptyObj, dummyFunction))
        assertEquals(0, (countResult as UDM.Scalar).value)
        
        val mapKeysResult = EnhancedObjectFunctions.mapKeys(listOf(emptyObj, dummyFunction))
        assertEquals(0, (mapKeysResult as UDM.Object).properties.size)
        
        val mapValuesResult = EnhancedObjectFunctions.mapValues(listOf(emptyObj, dummyFunction))
        assertEquals(0, (mapValuesResult as UDM.Object).properties.size)
    }

    private fun UDM.asNumber(): Double = when (this) {
        is UDM.Scalar -> {
            val v = value
            when (v) {
                is Number -> v.toDouble()
                else -> throw IllegalArgumentException("Expected number value")
            }
        }
        else -> throw IllegalArgumentException("Expected number value")
    }

    @Test
    fun testMapTreeWithStrings() {
        // Test transforming all strings to uppercase
        val input = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("hello"),
            "nested" to UDM.Object(mutableMapOf(
                "title" to UDM.Scalar("world")
            ))
        ))

        val transformer = UDM.Lambda { args ->
            val value = args[0]
            if (value is UDM.Scalar && value.value is String) {
                UDM.Scalar((value.value as String).uppercase())
            } else {
                value
            }
        }

        val result = EnhancedObjectFunctions.mapTree(listOf(input, transformer))
        assertTrue(result is UDM.Object)

        val obj = result as UDM.Object
        assertEquals("HELLO", (obj.properties["name"] as UDM.Scalar).value)
        val nested = obj.properties["nested"] as UDM.Object
        assertEquals("WORLD", (nested.properties["title"] as UDM.Scalar).value)
    }

    @Test
    fun testMapTreeWithArrays() {
        // Test transforming numbers in arrays
        val input = UDM.Object(mutableMapOf(
            "data" to UDM.Array(mutableListOf(
                UDM.Scalar(1),
                UDM.Scalar(2),
                UDM.Array(mutableListOf(
                    UDM.Scalar(3),
                    UDM.Scalar(4)
                ))
            ))
        ))

        val transformer = UDM.Lambda { args ->
            val value = args[0]
            if (value is UDM.Scalar && value.value is Number) {
                UDM.Scalar((value.value as Number).toDouble() * 2)
            } else {
                value
            }
        }

        val result = EnhancedObjectFunctions.mapTree(listOf(input, transformer))
        assertTrue(result is UDM.Object)

        val obj = result as UDM.Object
        val arr = obj.properties["data"] as UDM.Array
        assertEquals(2.0, (arr.elements[0] as UDM.Scalar).value)
        assertEquals(4.0, (arr.elements[1] as UDM.Scalar).value)

        val nested = arr.elements[2] as UDM.Array
        assertEquals(6.0, (nested.elements[0] as UDM.Scalar).value)
        assertEquals(8.0, (nested.elements[1] as UDM.Scalar).value)
    }

    @Test
    fun testMapTreeWithPath() {
        // Test that transformer receives path parameter
        var capturedPaths = mutableListOf<String>()

        val input = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Object(mutableMapOf(
                "c" to UDM.Scalar(2)
            ))
        ))

        val transformer = UDM.Lambda { args ->
            val value = args[0]
            val path = (args[1] as UDM.Scalar).value as String
            capturedPaths.add(path)
            value
        }

        EnhancedObjectFunctions.mapTree(listOf(input, transformer))

        // Verify paths were captured (order is depth-first)
        assertTrue(capturedPaths.contains("$"))
        assertTrue(capturedPaths.contains("$.a"))
        assertTrue(capturedPaths.contains("$.b"))
        assertTrue(capturedPaths.contains("$.b.c"))
    }

    @Test
    fun testMapTreePreservesStructure() {
        // Test that mapTree preserves object/array structure
        val input = UDM.Object(mutableMapOf(
            "obj" to UDM.Object(mutableMapOf(
                "key" to UDM.Scalar("value")
            )),
            "arr" to UDM.Array(mutableListOf(
                UDM.Scalar(1),
                UDM.Scalar(2)
            ))
        ))

        // Identity transformer
        val transformer = UDM.Lambda { args -> args[0] }

        val result = EnhancedObjectFunctions.mapTree(listOf(input, transformer))
        assertTrue(result is UDM.Object)

        val obj = result as UDM.Object
        assertTrue(obj.properties["obj"] is UDM.Object)
        assertTrue(obj.properties["arr"] is UDM.Array)
    }

    @Test
    fun testMapTreeWithScalar() {
        // Test mapTree with scalar input
        val input = UDM.Scalar("test")

        val transformer = UDM.Lambda { args ->
            val value = args[0]
            if (value is UDM.Scalar && value.value is String) {
                UDM.Scalar((value.value as String).uppercase())
            } else {
                value
            }
        }

        val result = EnhancedObjectFunctions.mapTree(listOf(input, transformer))
        assertTrue(result is UDM.Scalar)
        assertEquals("TEST", (result as UDM.Scalar).value)
    }

    @Test
    fun testMapTreeInvalidArguments() {
        // Test with wrong number of arguments
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.mapTree(listOf(UDM.Scalar(1)))
        }

        // Test with non-lambda second argument
        assertThrows<IllegalArgumentException> {
            EnhancedObjectFunctions.mapTree(listOf(
                UDM.Scalar(1),
                UDM.Scalar("not a lambda")
            ))
        }
    }
}