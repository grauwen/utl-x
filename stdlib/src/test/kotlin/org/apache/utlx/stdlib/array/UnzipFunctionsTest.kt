package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test suite for unzip and related array transformation functions
 * Location: stdlib/src/test/kotlin/org/apache/utlx/stdlib/array/UnzipFunctionsTest.kt
 */
class UnzipFunctionsTest {
    
    // ==================== UNZIP TESTS ====================
    
    @Test
    fun `unzip basic pairs`() {
        val input = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar("a"))),
            UDM.Array(listOf(UDM.Scalar(2.0), UDM.Scalar("b"))),
            UDM.Array(listOf(UDM.Scalar(3.0), UDM.Scalar("c")))
        ))
        
        val result = UnzipFunctions.unzip(listOf(input))
        
        assertTrue(result is UDM.Array)
        assertEquals(2, result.elements.size)
        
        val first = result.elements[0] as UDM.Array
        val second = result.elements[1] as UDM.Array
        
        assertEquals(3, first.elements.size)
        assertEquals(3, second.elements.size)
        
        assertEquals(1.0, (first.elements[0] as UDM.Scalar).value)
        assertEquals(2.0, (first.elements[1] as UDM.Scalar).value)
        assertEquals(3.0, (first.elements[2] as UDM.Scalar).value)
        
        assertEquals("a", (second.elements[0] as UDM.Scalar).value)
        assertEquals("b", (second.elements[1] as UDM.Scalar).value)
        assertEquals("c", (second.elements[2] as UDM.Scalar).value)
    }
    
    @Test
    fun `unzip empty array`() {
        val input = UDM.Array(emptyList())
        val result = UnzipFunctions.unzip(listOf(input))
        
        assertTrue(result is UDM.Array)
        assertEquals(2, result.elements.size)
        
        val first = result.elements[0] as UDM.Array
        val second = result.elements[1] as UDM.Array
        
        assertTrue(first.elements.isEmpty())
        assertTrue(second.elements.isEmpty())
    }
    
    @Test
    fun `unzip is inverse of zip`() {
        // Create zipped data
        val zipped = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar("a"))),
            UDM.Array(listOf(UDM.Scalar(2.0), UDM.Scalar("b")))
        ))
        
        // Unzip it
        val unzipped = UnzipFunctions.unzip(listOf(zipped))
        val arrays = unzipped as UDM.Array
        
        // Verify we get two separate arrays
        assertEquals(2, arrays.elements.size)
        
        val nums = arrays.elements[0] as UDM.Array
        val letters = arrays.elements[1] as UDM.Array
        
        assertEquals(2, nums.elements.size)
        assertEquals(2, letters.elements.size)
    }
    
    @Test
    fun `unzip throws on non-array input`() {
        val input = UDM.Scalar(42.0)
        
        assertThrows<IllegalArgumentException> {
            UnzipFunctions.unzip(listOf(input))
        }
    }
    
    @Test
    fun `unzip throws on non-pair elements`() {
        val input = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0))),  // Only 1 element, not a pair
            UDM.Array(listOf(UDM.Scalar(2.0), UDM.Scalar("b")))
        ))
        
        assertThrows<IllegalArgumentException> {
            UnzipFunctions.unzip(listOf(input))
        }
    }
    
    @Test
    fun `unzip throws on non-array elements`() {
        val input = UDM.Array(listOf(
            UDM.Scalar(1.0),  // Not an array
            UDM.Array(listOf(UDM.Scalar(2.0), UDM.Scalar("b")))
        ))
        
        assertThrows<IllegalArgumentException> {
            UnzipFunctions.unzip(listOf(input))
        }
    }
    
    // ==================== UNZIPN TESTS ====================
    
    @Test
    fun `unzipN with triples`() {
        val input = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar("a"), UDM.Scalar(true))),
            UDM.Array(listOf(UDM.Scalar(2.0), UDM.Scalar("b"), UDM.Scalar(false)))
        ))
        
        val result = UnzipFunctions.unzipN(listOf(input))
        
        assertTrue(result is UDM.Array)
        assertEquals(3, result.elements.size)
        
        val first = result.elements[0] as UDM.Array
        val second = result.elements[1] as UDM.Array
        val third = result.elements[2] as UDM.Array
        
        assertEquals(2, first.elements.size)
        assertEquals(2, second.elements.size)
        assertEquals(2, third.elements.size)
        
        assertEquals(1.0, (first.elements[0] as UDM.Scalar).value)
        assertEquals(2.0, (first.elements[1] as UDM.Scalar).value)
        
        assertEquals("a", (second.elements[0] as UDM.Scalar).value)
        assertEquals("b", (second.elements[1] as UDM.Scalar).value)
        
        assertEquals(true, (third.elements[0] as UDM.Scalar).value)
        assertEquals(false, (third.elements[1] as UDM.Scalar).value)
    }
    
    @Test
    fun `unzipN with empty array`() {
        val input = UDM.Array(emptyList())
        val result = UnzipFunctions.unzipN(listOf(input))
        
        assertTrue(result is UDM.Array)
        assertTrue(result.elements.isEmpty())
    }
    
    @Test
    fun `unzipN throws on inconsistent tuple sizes`() {
        val input = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar("a"))),
            UDM.Array(listOf(UDM.Scalar(2.0), UDM.Scalar("b"), UDM.Scalar(true)))  // Different size!
        ))
        
        assertThrows<IllegalArgumentException> {
            UnzipFunctions.unzipN(listOf(input))
        }
    }
    
    // ==================== TRANSPOSE TESTS ====================
    
    @Test
    fun `transpose rectangular matrix`() {
        val input = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0))),
            UDM.Array(listOf(UDM.Scalar(4.0), UDM.Scalar(5.0), UDM.Scalar(6.0)))
        ))
        
        val result = UnzipFunctions.transpose(listOf(input))
        
        assertTrue(result is UDM.Array)
        assertEquals(3, result.elements.size)  // 3 columns become 3 rows
        
        val row1 = result.elements[0] as UDM.Array
        val row2 = result.elements[1] as UDM.Array
        val row3 = result.elements[2] as UDM.Array
        
        assertEquals(2, row1.elements.size)
        assertEquals(2, row2.elements.size)
        assertEquals(2, row3.elements.size)
        
        // First column: [1, 4]
        assertEquals(1.0, (row1.elements[0] as UDM.Scalar).value)
        assertEquals(4.0, (row1.elements[1] as UDM.Scalar).value)
        
        // Second column: [2, 5]
        assertEquals(2.0, (row2.elements[0] as UDM.Scalar).value)
        assertEquals(5.0, (row2.elements[1] as UDM.Scalar).value)
        
        // Third column: [3, 6]
        assertEquals(3.0, (row3.elements[0] as UDM.Scalar).value)
        assertEquals(6.0, (row3.elements[1] as UDM.Scalar).value)
    }
    
    @Test
    fun `transpose jagged array`() {
        val input = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0))),
            UDM.Array(listOf(UDM.Scalar(3.0), UDM.Scalar(4.0), UDM.Scalar(5.0)))  // Longer row
        ))
        
        val result = UnzipFunctions.transpose(listOf(input))
        
        assertTrue(result is UDM.Array)
        assertEquals(3, result.elements.size)
        
        val row1 = result.elements[0] as UDM.Array
        val row2 = result.elements[1] as UDM.Array
        val row3 = result.elements[2] as UDM.Array
        
        assertEquals(2, row1.elements.size)  // [1, 3]
        assertEquals(2, row2.elements.size)  // [2, 4]
        assertEquals(1, row3.elements.size)  // [5] - only from second row
    }
    
    @Test
    fun `transpose is self-inverse for rectangular matrices`() {
        val original = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0))),
            UDM.Array(listOf(UDM.Scalar(3.0), UDM.Scalar(4.0)))
        ))
        
        val transposed = UnzipFunctions.transpose(listOf(original))
        val doubleTransposed = UnzipFunctions.transpose(listOf(transposed))
        
        // Should get back to original shape
        val result = doubleTransposed as UDM.Array
        assertEquals(2, result.elements.size)
        
        val row1 = result.elements[0] as UDM.Array
        val row2 = result.elements[1] as UDM.Array
        
        assertEquals(2, row1.elements.size)
        assertEquals(2, row2.elements.size)
    }
    
    // ==================== ZIPWITH TESTS ====================
    
    @Test
    fun `zipWith two arrays`() {
        val arr1 = UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0)))
        val arr2 = UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b"), UDM.Scalar("c")))
        
        val result = UnzipFunctions.zipWith(listOf(arr1, arr2))
        
        assertTrue(result is UDM.Array)
        assertEquals(3, result.elements.size)
        
        val pair1 = result.elements[0] as UDM.Array
        assertEquals(2, pair1.elements.size)
        assertEquals(1.0, (pair1.elements[0] as UDM.Scalar).value)
        assertEquals("a", (pair1.elements[1] as UDM.Scalar).value)
    }
    
    @Test
    fun `zipWith three arrays`() {
        val arr1 = UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0)))
        val arr2 = UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b")))
        val arr3 = UDM.Array(listOf(UDM.Scalar(true), UDM.Scalar(false)))
        
        val result = UnzipFunctions.zipWith(listOf(arr1, arr2, arr3))
        
        assertTrue(result is UDM.Array)
        assertEquals(2, result.elements.size)
        
        val triple1 = result.elements[0] as UDM.Array
        assertEquals(3, triple1.elements.size)
        assertEquals(1.0, (triple1.elements[0] as UDM.Scalar).value)
        assertEquals("a", (triple1.elements[1] as UDM.Scalar).value)
        assertEquals(true, (triple1.elements[2] as UDM.Scalar).value)
    }
    
    @Test
    fun `zipWith stops at shortest array`() {
        val arr1 = UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0)))
        val arr2 = UDM.Array(listOf(UDM.Scalar("a")))  // Only 1 element
        
        val result = UnzipFunctions.zipWith(listOf(arr1, arr2))
        
        assertTrue(result is UDM.Array)
        assertEquals(1, result.elements.size)  // Stops at shortest
    }
    
    @Test
    fun `zipWith with empty array returns empty`() {
        val arr1 = UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0)))
        val arr2 = UDM.Array(emptyList())
        
        val result = UnzipFunctions.zipWith(listOf(arr1, arr2))
        
        assertTrue(result is UDM.Array)
        assertTrue(result.elements.isEmpty())
    }
    
    @Test
    fun `zipWith is inverse of unzipN`() {
        val arr1 = UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0)))
        val arr2 = UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b")))
        val arr3 = UDM.Array(listOf(UDM.Scalar(true), UDM.Scalar(false)))
        
        // Zip three arrays
        val zipped = UnzipFunctions.zipWith(listOf(arr1, arr2, arr3))
        
        // Unzip back
        val unzipped = UnzipFunctions.unzipN(listOf(zipped))
        
        assertTrue(unzipped is UDM.Array)
        assertEquals(3, unzipped.elements.size)
        
        val resultArr1 = unzipped.elements[0] as UDM.Array
        val resultArr2 = unzipped.elements[1] as UDM.Array
        val resultArr3 = unzipped.elements[2] as UDM.Array
        
        assertEquals(2, resultArr1.elements.size)
        assertEquals(2, resultArr2.elements.size)
        assertEquals(2, resultArr3.elements.size)
    }
    
    // ==================== ZIPWITHINDEX TESTS ====================
    
    @Test
    fun `zipWithIndex basic`() {
        val input = UDM.Array(listOf(
            UDM.Scalar("a"),
            UDM.Scalar("b"),
            UDM.Scalar("c")
        ))
        
        val result = UnzipFunctions.zipWithIndex(listOf(input))
        
        assertTrue(result is UDM.Array)
        assertEquals(3, result.elements.size)
        
        val pair0 = result.elements[0] as UDM.Array
        assertEquals(0.0, (pair0.elements[0] as UDM.Scalar).value)
        assertEquals("a", (pair0.elements[1] as UDM.Scalar).value)
        
        val pair1 = result.elements[1] as UDM.Array
        assertEquals(1.0, (pair1.elements[0] as UDM.Scalar).value)
        assertEquals("b", (pair1.elements[1] as UDM.Scalar).value)
        
        val pair2 = result.elements[2] as UDM.Array
        assertEquals(2.0, (pair2.elements[0] as UDM.Scalar).value)
        assertEquals("c", (pair2.elements[1] as UDM.Scalar).value)
    }
    
    @Test
    fun `zipWithIndex empty array`() {
        val input = UDM.Array(emptyList())
        val result = UnzipFunctions.zipWithIndex(listOf(input))
        
        assertTrue(result is UDM.Array)
        assertTrue(result.elements.isEmpty())
    }
    
    @Test
    fun `zipWithIndex complex elements`() {
        val input = UDM.Array(listOf(
            UDM.Object(mapOf("name" to UDM.Scalar("Alice")), emptyMap()),
            UDM.Object(mapOf("name" to UDM.Scalar("Bob")), emptyMap())
        ))
        
        val result = UnzipFunctions.zipWithIndex(listOf(input))
        
        assertTrue(result is UDM.Array)
        assertEquals(2, result.elements.size)
        
        val pair0 = result.elements[0] as UDM.Array
        assertEquals(0.0, (pair0.elements[0] as UDM.Scalar).value)
        assertTrue(pair0.elements[1] is UDM.Object)
    }
    
    // ==================== INTEGRATION TESTS ====================
    
    @Test
    fun `real-world scenario - CSV data transformation`() {
        // Simulate CSV-like data: [[id, name, score], ...]
        val csvData = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar("Alice"), UDM.Scalar(95.0))),
            UDM.Array(listOf(UDM.Scalar(2.0), UDM.Scalar("Bob"), UDM.Scalar(87.0))),
            UDM.Array(listOf(UDM.Scalar(3.0), UDM.Scalar("Charlie"), UDM.Scalar(92.0)))
        ))
        
        // Unzip to get columns
        val columns = UnzipFunctions.unzipN(listOf(csvData))
        assertTrue(columns is UDM.Array)
        assertEquals(3, columns.elements.size)
        
        val ids = columns.elements[0] as UDM.Array
        val names = columns.elements[1] as UDM.Array
        val scores = columns.elements[2] as UDM.Array
        
        assertEquals(3, ids.elements.size)
        assertEquals(3, names.elements.size)
        assertEquals(3, scores.elements.size)
    }
    
    @Test
    fun `real-world scenario - matrix operations`() {
        // Original matrix
        val matrix = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0))),
            UDM.Array(listOf(UDM.Scalar(4.0), UDM.Scalar(5.0), UDM.Scalar(6.0)))
        ))
        
        // Transpose to get columns
        val transposed = UnzipFunctions.transpose(listOf(matrix))
        
        // Verify shape changed from 2x3 to 3x2
        assertTrue(transposed is UDM.Array)
        assertEquals(3, transposed.elements.size)
        
        (transposed.elements[0] as UDM.Array).let { col ->
            assertEquals(2, col.elements.size)
        }
    }
}
