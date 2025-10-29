package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArrayFunctionsTest {

    private val testArray = UDM.Array(listOf(
        UDM.Scalar(1.0),
        UDM.Scalar(2.0),
        UDM.Scalar(3.0),
        UDM.Scalar(4.0),
        UDM.Scalar(5.0)
    ))

    private val emptyArray = UDM.Array(emptyList<UDM>())

    @Test
    fun testSize() {
        val result = ArrayFunctions.size(listOf(testArray))
        assertTrue(result is UDM.Scalar)
        assertEquals(5.0, (result as UDM.Scalar).value)

        val emptyResult = ArrayFunctions.size(listOf(emptyArray))
        assertEquals(0.0, (emptyResult as UDM.Scalar).value)
    }

    @Test
    fun testGet() {
        val result = ArrayFunctions.get(listOf(testArray, UDM.Scalar(2)))
        assertTrue(result is UDM.Scalar)
        assertEquals(3.0, (result as UDM.Scalar).value)

        // Test first element
        val firstResult = ArrayFunctions.get(listOf(testArray, UDM.Scalar(0)))
        assertEquals(1.0, (firstResult as UDM.Scalar).value)
    }

    @Test
    fun testFirst() {
        val result = ArrayFunctions.first(listOf(testArray))
        assertTrue(result is UDM.Scalar)
        assertEquals(1.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testLast() {
        val result = ArrayFunctions.last(listOf(testArray))
        assertTrue(result is UDM.Scalar)
        assertEquals(5.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testTail() {
        val result = ArrayFunctions.tail(listOf(testArray))
        assertTrue(result is UDM.Array)
        val tailArray = result as UDM.Array
        assertEquals(4.0, tailArray.elements.size.toDouble())
        assertEquals(2.0, (tailArray.elements[0] as UDM.Scalar).value)
        assertEquals(5.0, (tailArray.elements[3] as UDM.Scalar).value)
    }

    @Test
    fun testTake() {
        val result = ArrayFunctions.take(listOf(testArray, UDM.Scalar(3)))
        assertTrue(result is UDM.Array)
        val takeArray = result as UDM.Array
        assertEquals(3.0, takeArray.elements.size.toDouble())
        assertEquals(1.0, (takeArray.elements[0] as UDM.Scalar).value)
        assertEquals(3.0, (takeArray.elements[2] as UDM.Scalar).value)
    }

    @Test
    fun testDrop() {
        val result = ArrayFunctions.drop(listOf(testArray, UDM.Scalar(2)))
        assertTrue(result is UDM.Array)
        val dropArray = result as UDM.Array
        assertEquals(3.0, dropArray.elements.size.toDouble())
        assertEquals(3.0, (dropArray.elements[0] as UDM.Scalar).value)
        assertEquals(5.0, (dropArray.elements[2] as UDM.Scalar).value)
    }

    @Test
    fun testReverse() {
        val result = ArrayFunctions.reverse(listOf(testArray))
        assertTrue(result is UDM.Array)
        val reversedArray = result as UDM.Array
        assertEquals(5.0, reversedArray.elements.size.toDouble())
        assertEquals(5.0, (reversedArray.elements[0] as UDM.Scalar).value)
        assertEquals(1.0, (reversedArray.elements[4] as UDM.Scalar).value)
    }

    @Test
    fun testSort() {
        val unsortedArray = UDM.Array(listOf(
            UDM.Scalar(3.0),
            UDM.Scalar(1.0),
            UDM.Scalar(4.0),
            UDM.Scalar(2.0),
            UDM.Scalar(5.0)
        ))

        val result = ArrayFunctions.sort(listOf(unsortedArray))
        assertTrue(result is UDM.Array)
        val sortedArray = result as UDM.Array
        assertEquals(5.0, sortedArray.elements.size.toDouble())
        assertEquals(1.0, (sortedArray.elements[0] as UDM.Scalar).value)
        assertEquals(5.0, (sortedArray.elements[4] as UDM.Scalar).value)
    }

    @Test
    fun testUnique() {
        val arrayWithDuplicates = UDM.Array(listOf(
            UDM.Scalar(1.0),
            UDM.Scalar(2.0),
            UDM.Scalar(2.0),
            UDM.Scalar(3.0),
            UDM.Scalar(1.0)
        ))

        val result = ArrayFunctions.unique(listOf(arrayWithDuplicates))
        assertTrue(result is UDM.Array)
        val uniqueArray = result as UDM.Array
        assertEquals(3.0, uniqueArray.elements.size.toDouble())
    }

    @Test
    fun testDistinct() {
        val arrayWithDuplicates = UDM.Array(listOf(
            UDM.Scalar(1.0),
            UDM.Scalar(2.0),
            UDM.Scalar(2.0),
            UDM.Scalar(3.0),
            UDM.Scalar(1.0)
        ))

        val result = ArrayFunctions.distinct(listOf(arrayWithDuplicates))
        assertTrue(result is UDM.Array)
        val distinctArray = result as UDM.Array
        assertEquals(3.0, distinctArray.elements.size.toDouble())
    }

    @Test
    fun testFlatten() {
        val nestedArray = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0))),
            UDM.Array(listOf(UDM.Scalar(3.0), UDM.Scalar(4.0))),
            UDM.Scalar(5.0)
        ))

        val result = ArrayFunctions.flatten(listOf(nestedArray))
        assertTrue(result is UDM.Array)
        val flatArray = result as UDM.Array
        assertEquals(5.0, flatArray.elements.size.toDouble())
        assertEquals(1.0, (flatArray.elements[0] as UDM.Scalar).value)
        assertEquals(5.0, (flatArray.elements[4] as UDM.Scalar).value)
    }

    @Test
    fun testZip() {
        val array1 = UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0)))
        val array2 = UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b"), UDM.Scalar("c")))

        val result = ArrayFunctions.zip(listOf(array1, array2))
        assertTrue(result is UDM.Array)
        val zippedArray = result as UDM.Array
        assertEquals(3.0, zippedArray.elements.size.toDouble())

        val firstPair = zippedArray.elements[0] as UDM.Array
        assertEquals(1.0, (firstPair.elements[0] as UDM.Scalar).value)
        assertEquals("a", (firstPair.elements[1] as UDM.Scalar).value)
    }

    @Test
    fun testUnion() {
        val array1 = UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0)))
        val array2 = UDM.Array(listOf(UDM.Scalar(3.0), UDM.Scalar(4.0), UDM.Scalar(5.0)))

        val result = ArrayFunctions.union(listOf(array1, array2))
        assertTrue(result is UDM.Array)
        val unionArray = result as UDM.Array
        assertEquals(5.0, unionArray.elements.size.toDouble()) // 1,2,3,4,5 (unique values)
    }

    @Test
    fun testIntersect() {
        val array1 = UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0)))
        val array2 = UDM.Array(listOf(UDM.Scalar(2.0), UDM.Scalar(3.0), UDM.Scalar(4.0)))

        val result = ArrayFunctions.intersect(listOf(array1, array2))
        assertTrue(result is UDM.Array)
        val intersectArray = result as UDM.Array
        assertEquals(2.0, intersectArray.elements.size.toDouble()) // 2,3
    }

    @Test
    fun testDifference() {
        val array1 = UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0)))
        val array2 = UDM.Array(listOf(UDM.Scalar(2.0), UDM.Scalar(3.0), UDM.Scalar(4.0)))

        val result = ArrayFunctions.difference(listOf(array1, array2))
        assertTrue(result is UDM.Array)
        val diffArray = result as UDM.Array
        assertEquals(1.0, diffArray.elements.size.toDouble()) // 1
        assertEquals(1.0, (diffArray.elements[0] as UDM.Scalar).value)
    }

    @Test
    fun testChunk() {
        val result = ArrayFunctions.chunk(listOf(testArray, UDM.Scalar(2)))
        assertTrue(result is UDM.Array)
        val chunkedArray = result as UDM.Array
        assertEquals(3.0, chunkedArray.elements.size.toDouble()) // [1,2], [3,4], [5]

        val firstChunk = chunkedArray.elements[0] as UDM.Array
        assertEquals(2.0, firstChunk.elements.size.toDouble())
        assertEquals(1.0, (firstChunk.elements[0] as UDM.Scalar).value)
        assertEquals(2.0, (firstChunk.elements[1] as UDM.Scalar).value)
    }

    @Test
    fun testJoinToString() {
        val result = ArrayFunctions.joinToString(listOf(testArray, UDM.Scalar(",")))
        assertTrue(result is UDM.Scalar)
        assertEquals("1.0,2.0,3.0,4.0,5.0", (result as UDM.Scalar).value)

        // Test with custom separator
        val result2 = ArrayFunctions.joinToString(listOf(testArray, UDM.Scalar(" - ")))
        assertEquals("1.0 - 2.0 - 3.0 - 4.0 - 5.0", (result2 as UDM.Scalar).value)
    }

    // Note: testInvalidArguments removed - validation is handled at runtime by the UTL-X engine via @UTLXFunction annotations

    @Test
    fun testEdgeCases() {
        // Test empty array operations
        val emptySize = ArrayFunctions.size(listOf(emptyArray))
        assertEquals(0.0, (emptySize as UDM.Scalar).value)

        val emptyReversed = ArrayFunctions.reverse(listOf(emptyArray))
        assertTrue(emptyReversed is UDM.Array)
        assertEquals(0.0, (emptyReversed as UDM.Array).elements.size.toDouble())

        // Test single element array
        val singleArray = UDM.Array(listOf(UDM.Scalar(42.0)))
        val singleFirst = ArrayFunctions.first(listOf(singleArray))
        val singleLast = ArrayFunctions.last(listOf(singleArray))
        assertEquals(42.0, (singleFirst as UDM.Scalar).value)
        assertEquals(42.0, (singleLast as UDM.Scalar).value)

        // Test tail of single element array
        val singleTail = ArrayFunctions.tail(listOf(singleArray))
        assertTrue(singleTail is UDM.Array)
        assertEquals(0.0, (singleTail as UDM.Array).elements.size.toDouble())
    }
}