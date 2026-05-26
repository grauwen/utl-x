package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CriticalArrayFunctionsTest {

    @Test
    fun testCompact() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1.0),
            UDM.Scalar(null),
            UDM.Scalar(2.0),
            UDM.Scalar(""),
            UDM.Scalar(3.0),
            UDM.Scalar(false),
            UDM.Scalar(0.0),
            UDM.Scalar(4.0)
        ))

        val result = CriticalArrayFunctions.compact(listOf(array))

        assertTrue(result is UDM.Array)
        val filtered = (result as UDM.Array).elements
        // compact removes null and "" only, keeps 0, false, and all other values
        assertEquals(6, filtered.size)
        assertEquals(1.0, (filtered[0] as UDM.Scalar).value)
        assertEquals(2.0, (filtered[1] as UDM.Scalar).value)
        assertEquals(3.0, (filtered[2] as UDM.Scalar).value)
        assertEquals(false, (filtered[3] as UDM.Scalar).value)
        assertEquals(0.0, (filtered[4] as UDM.Scalar).value)
        assertEquals(4.0, (filtered[5] as UDM.Scalar).value)
    }

    @Test
    fun testCompactKeepsValidStructures() {
        val array = UDM.Array(listOf(
            UDM.Scalar(null),
            UDM.Array(emptyList()),
            UDM.Object(emptyMap(), emptyMap()),
            UDM.Scalar("")
        ))
        
        val result = CriticalArrayFunctions.compact(listOf(array))
        
        val filtered = (result as UDM.Array).elements
        assertEquals(2, filtered.size)
        assertTrue(filtered[0] is UDM.Array)
        assertTrue(filtered[1] is UDM.Object)
    }

    @Test
    fun testFindIndex() {
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(4)))
        val predicate = UDM.Lambda { args -> UDM.Scalar(args[0].asNumber() > 2) }

        val result = CriticalArrayFunctions.findIndex(listOf(array, predicate))
        assertTrue(result is UDM.Scalar)
        assertEquals(2.0, (result as UDM.Scalar).value) // index of 3 (first > 2)
    }

    @Test
    fun testFindLastIndex() {
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(4)))
        val predicate = UDM.Lambda { args -> UDM.Scalar(args[0].asNumber() > 2) }

        val result = CriticalArrayFunctions.findLastIndex(listOf(array, predicate))
        assertTrue(result is UDM.Scalar)
        assertEquals(3.0, (result as UDM.Scalar).value) // index of 4 (last > 2)
    }

    @Test
    fun testScan() {
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(4)))
        val reducer = UDM.Lambda { args -> UDM.Scalar(args[0].asNumber() + args[1].asNumber()) }
        val initial = UDM.Scalar(0)

        val result = CriticalArrayFunctions.scan(listOf(array, reducer, initial))
        assertTrue(result is UDM.Array)
        val scanResults = (result as UDM.Array).elements
        assertEquals(4, scanResults.size)
        assertEquals(1.0, (scanResults[0] as UDM.Scalar).value)   // 0+1
        assertEquals(3.0, (scanResults[1] as UDM.Scalar).value)   // 1+2
        assertEquals(6.0, (scanResults[2] as UDM.Scalar).value)   // 3+3
        assertEquals(10.0, (scanResults[3] as UDM.Scalar).value)  // 6+4
    }

    @Test
    fun testWindowed() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3),
            UDM.Scalar(4),
            UDM.Scalar(5)
        ))
        
        val windowSize = UDM.Scalar(3)
        
        val result = CriticalArrayFunctions.windowed(listOf(array, windowSize))
        
        assertTrue(result is UDM.Array)
        val windows = (result as UDM.Array).elements
        assertEquals(3, windows.size) // 5 - 3 + 1 = 3 windows
        
        // Check first window
        val firstWindow = windows[0] as UDM.Array
        assertEquals(3, firstWindow.elements.size)
        assertEquals(1, (firstWindow.elements[0] as UDM.Scalar).value)
        assertEquals(2, (firstWindow.elements[1] as UDM.Scalar).value)
        assertEquals(3, (firstWindow.elements[2] as UDM.Scalar).value)
        
        // Check last window
        val lastWindow = windows[2] as UDM.Array
        assertEquals(3, lastWindow.elements.size)
        assertEquals(3, (lastWindow.elements[0] as UDM.Scalar).value)
        assertEquals(4, (lastWindow.elements[1] as UDM.Scalar).value)
        assertEquals(5, (lastWindow.elements[2] as UDM.Scalar).value)
    }

    @Test
    fun testWindowedWithStep() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3),
            UDM.Scalar(4),
            UDM.Scalar(5)
        ))
        
        val windowSize = UDM.Scalar(2)
        val step = UDM.Scalar(2)
        
        val result = CriticalArrayFunctions.windowed(listOf(array, windowSize, step))
        
        val windows = (result as UDM.Array).elements
        assertEquals(2, windows.size) // [[1,2], [3,4]]
        
        val firstWindow = windows[0] as UDM.Array
        assertEquals(1, (firstWindow.elements[0] as UDM.Scalar).value)
        assertEquals(2, (firstWindow.elements[1] as UDM.Scalar).value)
        
        val secondWindow = windows[1] as UDM.Array
        assertEquals(3, (secondWindow.elements[0] as UDM.Scalar).value)
        assertEquals(4, (secondWindow.elements[1] as UDM.Scalar).value)
    }

    @Test
    fun testZipAll() {
        val arrays = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3))),
            UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b")))
        ))
        
        val defaultValue = UDM.Scalar(null)
        
        val result = CriticalArrayFunctions.zipAll(listOf(arrays, defaultValue))
        
        assertTrue(result is UDM.Array)
        val zipped = (result as UDM.Array).elements
        assertEquals(3, zipped.size) // Length of longest array
        
        // Check first tuple: [1, "a"]
        val firstTuple = zipped[0] as UDM.Array
        assertEquals(1, (firstTuple.elements[0] as UDM.Scalar).value)
        assertEquals("a", (firstTuple.elements[1] as UDM.Scalar).value)
        
        // Check second tuple: [2, "b"]
        val secondTuple = zipped[1] as UDM.Array
        assertEquals(2, (secondTuple.elements[0] as UDM.Scalar).value)
        assertEquals("b", (secondTuple.elements[1] as UDM.Scalar).value)
        
        // Check third tuple: [3, null]
        val thirdTuple = zipped[2] as UDM.Array
        assertEquals(3, (thirdTuple.elements[0] as UDM.Scalar).value)
        assertEquals(null, (thirdTuple.elements[1] as UDM.Scalar).value)
    }

    @Test
    fun testZipAllEmptyArrays() {
        val arrays = UDM.Array(emptyList())
        val defaultValue = UDM.Scalar(null)
        
        val result = CriticalArrayFunctions.zipAll(listOf(arrays, defaultValue))
        
        assertTrue(result is UDM.Array)
        val zipped = (result as UDM.Array).elements
        assertEquals(0, zipped.size)
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun testCompactInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            CriticalArrayFunctions.compact(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            CriticalArrayFunctions.compact(listOf(UDM.Scalar("not-array")))
        }
    }

    @Test
    fun testFindIndexInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            CriticalArrayFunctions.findIndex(listOf(UDM.Array(emptyList())))
        }
        
        assertThrows<IllegalArgumentException> {
            CriticalArrayFunctions.findIndex(listOf(UDM.Scalar("not-array"), UDM.Scalar("predicate")))
        }
    }

    @Test
    fun testWindowedInvalidArguments() {
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        
        assertThrows<IllegalArgumentException> {
            CriticalArrayFunctions.windowed(listOf(array))
        }
        
        assertThrows<IllegalArgumentException> {
            CriticalArrayFunctions.windowed(listOf(UDM.Scalar("not-array"), UDM.Scalar(2)))
        }
        
        assertThrows<IllegalArgumentException> {
            CriticalArrayFunctions.windowed(listOf(array, UDM.Scalar("not-number")))
        }
        
        assertThrows<IllegalArgumentException> {
            CriticalArrayFunctions.windowed(listOf(array, UDM.Scalar(0))) // Window size must be positive
        }
        
        assertThrows<IllegalArgumentException> {
            CriticalArrayFunctions.windowed(listOf(array, UDM.Scalar(2), UDM.Scalar(0))) // Step must be positive
        }
    }

    @Test
    fun testZipAllInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            CriticalArrayFunctions.zipAll(listOf(UDM.Scalar("not-array"), UDM.Scalar(null)))
        }
        
        assertThrows<IllegalArgumentException> {
            CriticalArrayFunctions.zipAll(listOf(
                UDM.Array(listOf(UDM.Scalar("not-array"))), 
                UDM.Scalar(null)
            ))
        }
    }

    // ==================== EDGE CASES ====================

    @Test
    fun testCompactEmptyArray() {
        val array = UDM.Array(emptyList())
        
        val result = CriticalArrayFunctions.compact(listOf(array))
        
        assertTrue(result is UDM.Array)
        assertEquals(0, (result as UDM.Array).elements.size)
    }

    @Test
    fun testWindowedExactSize() {
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        val windowSize = UDM.Scalar(3) // Same size as array
        
        val result = CriticalArrayFunctions.windowed(listOf(array, windowSize))
        
        val windows = (result as UDM.Array).elements
        assertEquals(1, windows.size)
        
        val window = windows[0] as UDM.Array
        assertEquals(3, window.elements.size)
    }

    @Test
    fun testWindowedLargerThanArray() {
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        val windowSize = UDM.Scalar(5) // Larger than array
        
        val result = CriticalArrayFunctions.windowed(listOf(array, windowSize))
        
        val windows = (result as UDM.Array).elements
        assertEquals(0, windows.size) // No windows possible
    }

    @Test
    fun testZipAllSingleArray() {
        val arrays = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        ))
        
        val defaultValue = UDM.Scalar("default")
        
        val result = CriticalArrayFunctions.zipAll(listOf(arrays, defaultValue))
        
        val zipped = (result as UDM.Array).elements
        assertEquals(3, zipped.size)
        
        // Each tuple should have only one element
        zipped.forEachIndexed { index, tuple ->
            val tupleArray = tuple as UDM.Array
            assertEquals(1, tupleArray.elements.size)
            assertEquals(index + 1, (tupleArray.elements[0] as UDM.Scalar).value)
        }
    }
}