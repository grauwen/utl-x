package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArrayFunctionsTest {

    private val testArray = UDM.Array(listOf(
        UDM.Scalar(1),
        UDM.Scalar(2),
        UDM.Scalar(3),
        UDM.Scalar(4),
        UDM.Scalar(5)
    ))

    private val emptyArray = UDM.Array(emptyList<UDM>())

    @Test
    fun testSize() {
        val result = ArrayFunctions.size(listOf(testArray))
        assertTrue(result is UDM.Scalar)
        assertEquals(5, (result as UDM.Scalar).value)
        
        val emptyResult = ArrayFunctions.size(listOf(emptyArray))
        assertEquals(0, (emptyResult as UDM.Scalar).value)
    }

    @Test
    fun testGet() {
        val result = ArrayFunctions.get(listOf(testArray, UDM.Scalar(2)))
        assertTrue(result is UDM.Scalar)
        assertEquals(3, (result as UDM.Scalar).value)
        
        // Test first element
        val firstResult = ArrayFunctions.get(listOf(testArray, UDM.Scalar(0)))
        assertEquals(1, (firstResult as UDM.Scalar).value)
    }

    @Test
    fun testFirst() {
        val result = ArrayFunctions.first(listOf(testArray))
        assertTrue(result is UDM.Scalar)
        assertEquals(1, (result as UDM.Scalar).value)
    }

    @Test
    fun testLast() {
        val result = ArrayFunctions.last(listOf(testArray))
        assertTrue(result is UDM.Scalar)
        assertEquals(5, (result as UDM.Scalar).value)
    }

    @Test
    fun testTail() {
        val result = ArrayFunctions.tail(listOf(testArray))
        assertTrue(result is UDM.Array)
        val tailArray = result as UDM.Array
        assertEquals(4, tailArray.elements.size)
        assertEquals(2, (tailArray.elements[0] as UDM.Scalar).value)
        assertEquals(5, (tailArray.elements[3] as UDM.Scalar).value)
    }

    @Test
    fun testTake() {
        val result = ArrayFunctions.take(listOf(testArray, UDM.Scalar(3)))
        assertTrue(result is UDM.Array)
        val takeArray = result as UDM.Array
        assertEquals(3, takeArray.elements.size)
        assertEquals(1, (takeArray.elements[0] as UDM.Scalar).value)
        assertEquals(3, (takeArray.elements[2] as UDM.Scalar).value)
    }

    @Test
    fun testDrop() {
        val result = ArrayFunctions.drop(listOf(testArray, UDM.Scalar(2)))
        assertTrue(result is UDM.Array)
        val dropArray = result as UDM.Array
        assertEquals(3, dropArray.elements.size)
        assertEquals(3, (dropArray.elements[0] as UDM.Scalar).value)
        assertEquals(5, (dropArray.elements[2] as UDM.Scalar).value)
    }

    @Test
    fun testReverse() {
        val result = ArrayFunctions.reverse(listOf(testArray))
        assertTrue(result is UDM.Array)
        val reversedArray = result as UDM.Array
        assertEquals(5, reversedArray.elements.size)
        assertEquals(5, (reversedArray.elements[0] as UDM.Scalar).value)
        assertEquals(1, (reversedArray.elements[4] as UDM.Scalar).value)
    }

    @Test
    fun testSort() {
        val unsortedArray = UDM.Array(listOf(
            UDM.Scalar(3),
            UDM.Scalar(1),
            UDM.Scalar(4),
            UDM.Scalar(2),
            UDM.Scalar(5)
        ))
        
        val result = ArrayFunctions.sort(listOf(unsortedArray))
        assertTrue(result is UDM.Array)
        val sortedArray = result as UDM.Array
        assertEquals(5, sortedArray.elements.size)
        assertEquals(1, (sortedArray.elements[0] as UDM.Scalar).value)
        assertEquals(5, (sortedArray.elements[4] as UDM.Scalar).value)
    }

    @Test
    fun testUnique() {
        val arrayWithDuplicates = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(2),
            UDM.Scalar(3),
            UDM.Scalar(1)
        ))
        
        val result = ArrayFunctions.unique(listOf(arrayWithDuplicates))
        assertTrue(result is UDM.Array)
        val uniqueArray = result as UDM.Array
        assertEquals(3, uniqueArray.elements.size)
    }

    @Test
    fun testDistinct() {
        val arrayWithDuplicates = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(2),
            UDM.Scalar(3),
            UDM.Scalar(1)
        ))
        
        val result = ArrayFunctions.distinct(listOf(arrayWithDuplicates))
        assertTrue(result is UDM.Array)
        val distinctArray = result as UDM.Array
        assertEquals(3, distinctArray.elements.size)
    }

    @Test
    fun testFlatten() {
        val nestedArray = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
            UDM.Array(listOf(UDM.Scalar(3), UDM.Scalar(4))),
            UDM.Scalar(5)
        ))
        
        val result = ArrayFunctions.flatten(listOf(nestedArray))
        assertTrue(result is UDM.Array)
        val flatArray = result as UDM.Array
        assertEquals(5, flatArray.elements.size)
        assertEquals(1, (flatArray.elements[0] as UDM.Scalar).value)
        assertEquals(5, (flatArray.elements[4] as UDM.Scalar).value)
    }

    @Test
    fun testZip() {
        val array1 = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        val array2 = UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b"), UDM.Scalar("c")))
        
        val result = ArrayFunctions.zip(listOf(array1, array2))
        assertTrue(result is UDM.Array)
        val zippedArray = result as UDM.Array
        assertEquals(3, zippedArray.elements.size)
        
        val firstPair = zippedArray.elements[0] as UDM.Array
        assertEquals(1, (firstPair.elements[0] as UDM.Scalar).value)
        assertEquals("a", (firstPair.elements[1] as UDM.Scalar).value)
    }

    @Test
    fun testUnion() {
        val array1 = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        val array2 = UDM.Array(listOf(UDM.Scalar(3), UDM.Scalar(4), UDM.Scalar(5)))
        
        val result = ArrayFunctions.union(listOf(array1, array2))
        assertTrue(result is UDM.Array)
        val unionArray = result as UDM.Array
        assertEquals(5, unionArray.elements.size) // 1,2,3,4,5 (unique values)
    }

    @Test
    fun testIntersect() {
        val array1 = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        val array2 = UDM.Array(listOf(UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(4)))
        
        val result = ArrayFunctions.intersect(listOf(array1, array2))
        assertTrue(result is UDM.Array)
        val intersectArray = result as UDM.Array
        assertEquals(2, intersectArray.elements.size) // 2,3
    }

    @Test
    fun testDifference() {
        val array1 = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        val array2 = UDM.Array(listOf(UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(4)))
        
        val result = ArrayFunctions.difference(listOf(array1, array2))
        assertTrue(result is UDM.Array)
        val diffArray = result as UDM.Array
        assertEquals(1, diffArray.elements.size) // 1
        assertEquals(1, (diffArray.elements[0] as UDM.Scalar).value)
    }

    @Test
    fun testChunk() {
        val result = ArrayFunctions.chunk(listOf(testArray, UDM.Scalar(2)))
        assertTrue(result is UDM.Array)
        val chunkedArray = result as UDM.Array
        assertEquals(3, chunkedArray.elements.size) // [1,2], [3,4], [5]
        
        val firstChunk = chunkedArray.elements[0] as UDM.Array
        assertEquals(2, firstChunk.elements.size)
        assertEquals(1, (firstChunk.elements[0] as UDM.Scalar).value)
        assertEquals(2, (firstChunk.elements[1] as UDM.Scalar).value)
    }

    @Test
    fun testJoinToString() {
        val result = ArrayFunctions.joinToString(listOf(testArray, UDM.Scalar(",")))
        assertTrue(result is UDM.Scalar)
        assertEquals("1,2,3,4,5", (result as UDM.Scalar).value)
        
        // Test with custom separator
        val result2 = ArrayFunctions.joinToString(listOf(testArray, UDM.Scalar(" - ")))
        assertEquals("1 - 2 - 3 - 4 - 5", (result2 as UDM.Scalar).value)
    }

    @Test
    fun testInvalidArguments() {
        // Test with wrong number of arguments
        assertThrows<FunctionArgumentException> {
            ArrayFunctions.size(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            ArrayFunctions.get(listOf(testArray))
        }
        
        assertThrows<FunctionArgumentException> {
            ArrayFunctions.take(listOf(testArray))
        }
        
        // Test with wrong argument types
        assertThrows<FunctionArgumentException> {
            ArrayFunctions.size(listOf(UDM.Scalar("not an array")))
        }
        
        assertThrows<FunctionArgumentException> {
            ArrayFunctions.get(listOf(testArray, UDM.Scalar("not a number")))
        }
    }

    @Test
    fun testEdgeCases() {
        // Test empty array operations
        val emptySize = ArrayFunctions.size(listOf(emptyArray))
        assertEquals(0, (emptySize as UDM.Scalar).value)
        
        val emptyReversed = ArrayFunctions.reverse(listOf(emptyArray))
        assertTrue(emptyReversed is UDM.Array)
        assertEquals(0, (emptyReversed as UDM.Array).elements.size)
        
        // Test single element array
        val singleArray = UDM.Array(listOf(UDM.Scalar(42)))
        val singleFirst = ArrayFunctions.first(listOf(singleArray))
        val singleLast = ArrayFunctions.last(listOf(singleArray))
        assertEquals(42, (singleFirst as UDM.Scalar).value)
        assertEquals(42, (singleLast as UDM.Scalar).value)
        
        // Test tail of single element array
        val singleTail = ArrayFunctions.tail(listOf(singleArray))
        assertTrue(singleTail is UDM.Array)
        assertEquals(0, (singleTail as UDM.Array).elements.size)
    }
}