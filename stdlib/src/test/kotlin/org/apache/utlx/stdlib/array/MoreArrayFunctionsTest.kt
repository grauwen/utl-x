package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class MoreArrayFunctionsTest {

    @Test
    fun testRemove() {
        // Test basic removal
        val result1 = MoreArrayFunctions.remove(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0), UDM.Scalar(4.0))),
            UDM.Scalar(2)
        ))
        val expected1 = UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(4.0)))
        assertEquals(expected1.elements.size, (result1 as UDM.Array).elements.size)
        assertEquals(1.0, ((result1 as UDM.Array).elements[0] as UDM.Scalar).value)
        assertEquals(2.0, ((result1 as UDM.Array).elements[1] as UDM.Scalar).value)
        assertEquals(4.0, ((result1 as UDM.Array).elements[2] as UDM.Scalar).value)

        // Test remove first element
        val result2 = MoreArrayFunctions.remove(listOf(
            UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b"), UDM.Scalar("c"))),
            UDM.Scalar(0)
        ))
        assertEquals(2, (result2 as UDM.Array).elements.size)
        assertEquals("b", ((result2 as UDM.Array).elements[0] as UDM.Scalar).value)

        // Test remove last element (index 2 from ["a", "b", "c"] removes "c")
        val result3 = MoreArrayFunctions.remove(listOf(
            UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b"), UDM.Scalar("c"))),
            UDM.Scalar(2)
        ))
        assertEquals(2, (result3 as UDM.Array).elements.size)
        assertEquals("a", ((result3 as UDM.Array).elements[0] as UDM.Scalar).value)
        assertEquals("b", ((result3 as UDM.Array).elements[1] as UDM.Scalar).value)
    }

    @Test
    fun testRemoveEdgeCases() {
        // Test out of bounds index
        assertThrows<FunctionArgumentException> {
            MoreArrayFunctions.remove(listOf(
                UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
                UDM.Scalar(5)
            ))
        }

        // Test negative index
        assertThrows<FunctionArgumentException> {
            MoreArrayFunctions.remove(listOf(
                UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
                UDM.Scalar(-1)
            ))
        }

        // Test wrong number of arguments
        assertThrows<FunctionArgumentException> {
            MoreArrayFunctions.remove(listOf(UDM.Array(listOf(UDM.Scalar(1)))))
        }

        // Test non-array first argument
        assertThrows<FunctionArgumentException> {
            MoreArrayFunctions.remove(listOf(UDM.Scalar("not array"), UDM.Scalar(0)))
        }
    }

    @Test
    fun testInsertBefore() {
        // Test basic insertion
        val result1 = MoreArrayFunctions.insertBefore(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(4.0))),
            UDM.Scalar(2),
            UDM.Scalar(3.0)
        ))
        val expected = listOf(1.0, 2.0, 3.0, 4.0)
        assertEquals(4, (result1 as UDM.Array).elements.size)
        assertEquals(expected[0], ((result1 as UDM.Array).elements[0] as UDM.Scalar).value)
        assertEquals(expected[1], ((result1 as UDM.Array).elements[1] as UDM.Scalar).value)
        assertEquals(expected[2], ((result1 as UDM.Array).elements[2] as UDM.Scalar).value)
        assertEquals(expected[3], ((result1 as UDM.Array).elements[3] as UDM.Scalar).value)

        // Test insert at beginning
        val result2 = MoreArrayFunctions.insertBefore(listOf(
            UDM.Array(listOf(UDM.Scalar("b"), UDM.Scalar("c"))),
            UDM.Scalar(0),
            UDM.Scalar("a")
        ))
        assertEquals(3, (result2 as UDM.Array).elements.size)
        assertEquals("a", ((result2 as UDM.Array).elements[0] as UDM.Scalar).value)

        // Test insert at end
        val result3 = MoreArrayFunctions.insertBefore(listOf(
            UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b"))),
            UDM.Scalar(2),
            UDM.Scalar("c")
        ))
        assertEquals(3, (result3 as UDM.Array).elements.size)
        assertEquals("c", ((result3 as UDM.Array).elements[2] as UDM.Scalar).value)
    }

    @Test
    fun testInsertAfter() {
        // Test basic insertion
        val result1 = MoreArrayFunctions.insertAfter(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(4.0))),
            UDM.Scalar(1),
            UDM.Scalar(3.0)
        ))
        val expected = listOf(1.0, 2.0, 3.0, 4.0)
        assertEquals(4, (result1 as UDM.Array).elements.size)
        assertEquals(expected[0], ((result1 as UDM.Array).elements[0] as UDM.Scalar).value)
        assertEquals(expected[1], ((result1 as UDM.Array).elements[1] as UDM.Scalar).value)
        assertEquals(expected[2], ((result1 as UDM.Array).elements[2] as UDM.Scalar).value)
        assertEquals(expected[3], ((result1 as UDM.Array).elements[3] as UDM.Scalar).value)

        // Test insert after first
        val result2 = MoreArrayFunctions.insertAfter(listOf(
            UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("c"))),
            UDM.Scalar(0),
            UDM.Scalar("b")
        ))
        assertEquals(3, (result2 as UDM.Array).elements.size)
        assertEquals("b", ((result2 as UDM.Array).elements[1] as UDM.Scalar).value)

        // Test insert after last
        val result3 = MoreArrayFunctions.insertAfter(listOf(
            UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b"))),
            UDM.Scalar(1),
            UDM.Scalar("c")
        ))
        assertEquals(3, (result3 as UDM.Array).elements.size)
        assertEquals("c", ((result3 as UDM.Array).elements[2] as UDM.Scalar).value)
    }

    @Test
    fun testIndexOf() {
        // Test finding existing element
        val result1 = MoreArrayFunctions.indexOf(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(2))),
            UDM.Scalar(2)
        ))
        assertEquals(1.0, (result1 as UDM.Scalar).value)

        // Test not found
        val result2 = MoreArrayFunctions.indexOf(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3))),
            UDM.Scalar(4)
        ))
        assertNull((result2 as UDM.Scalar).value)

        // Test string search
        val result3 = MoreArrayFunctions.indexOf(listOf(
            UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b"), UDM.Scalar("c"))),
            UDM.Scalar("b")
        ))
        assertEquals(1.0, (result3 as UDM.Scalar).value)

        // Test empty array
        val result4 = MoreArrayFunctions.indexOf(listOf(
            UDM.Array(emptyList()),
            UDM.Scalar(1)
        ))
        assertNull((result4 as UDM.Scalar).value)
    }

    @Test
    fun testLastIndexOf() {
        // Test finding last occurrence
        val result1 = MoreArrayFunctions.lastIndexOf(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(2))),
            UDM.Scalar(2)
        ))
        assertEquals(3.0, (result1 as UDM.Scalar).value)

        // Test single occurrence
        val result2 = MoreArrayFunctions.lastIndexOf(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3))),
            UDM.Scalar(2)
        ))
        assertEquals(1.0, (result2 as UDM.Scalar).value)

        // Test not found
        val result3 = MoreArrayFunctions.lastIndexOf(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3))),
            UDM.Scalar(4)
        ))
        assertNull((result3 as UDM.Scalar).value)
    }

    @Test
    fun testIncludes() {
        // Test element exists
        val result1 = MoreArrayFunctions.includes(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3))),
            UDM.Scalar(2)
        ))
        assertTrue((result1 as UDM.Scalar).value as Boolean)

        // Test element doesn't exist
        val result2 = MoreArrayFunctions.includes(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3))),
            UDM.Scalar(4)
        ))
        assertFalse((result2 as UDM.Scalar).value as Boolean)

        // Test string search
        val result3 = MoreArrayFunctions.includes(listOf(
            UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b"), UDM.Scalar("c"))),
            UDM.Scalar("b")
        ))
        assertTrue((result3 as UDM.Scalar).value as Boolean)

        // Test empty array
        val result4 = MoreArrayFunctions.includes(listOf(
            UDM.Array(emptyList()),
            UDM.Scalar(1)
        ))
        assertFalse((result4 as UDM.Scalar).value as Boolean)
    }

    @Test
    fun testSlice() {
        // Test basic slice
        val result1 = MoreArrayFunctions.slice(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0), UDM.Scalar(4.0), UDM.Scalar(5.0))),
            UDM.Scalar(1),
            UDM.Scalar(4)
        ))
        assertEquals(3, (result1 as UDM.Array).elements.size)
        assertEquals(2.0, ((result1 as UDM.Array).elements[0] as UDM.Scalar).value)
        assertEquals(3.0, ((result1 as UDM.Array).elements[1] as UDM.Scalar).value)
        assertEquals(4.0, ((result1 as UDM.Array).elements[2] as UDM.Scalar).value)

        // Test slice without end (goes to end of array)
        val result2 = MoreArrayFunctions.slice(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0))),
            UDM.Scalar(1)
        ))
        assertEquals(2, (result2 as UDM.Array).elements.size)
        assertEquals(2.0, ((result2 as UDM.Array).elements[0] as UDM.Scalar).value)
        assertEquals(3.0, ((result2 as UDM.Array).elements[1] as UDM.Scalar).value)

        // Test slice from beginning
        val result3 = MoreArrayFunctions.slice(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0))),
            UDM.Scalar(0),
            UDM.Scalar(2)
        ))
        assertEquals(2, (result3 as UDM.Array).elements.size)
        assertEquals(1.0, ((result3 as UDM.Array).elements[0] as UDM.Scalar).value)
        assertEquals(2.0, ((result3 as UDM.Array).elements[1] as UDM.Scalar).value)

        // Test empty slice
        val result4 = MoreArrayFunctions.slice(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0), UDM.Scalar(3.0))),
            UDM.Scalar(1),
            UDM.Scalar(1)
        ))
        assertEquals(0, (result4 as UDM.Array).elements.size)
    }

    @Test
    fun testSliceEdgeCases() {
        // Test invalid start index
        assertThrows<FunctionArgumentException> {
            MoreArrayFunctions.slice(listOf(
                UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
                UDM.Scalar(-1),
                UDM.Scalar(1)
            ))
        }

        // Test start index greater than array size
        assertThrows<FunctionArgumentException> {
            MoreArrayFunctions.slice(listOf(
                UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
                UDM.Scalar(3),
                UDM.Scalar(4)
            ))
        }

        // Test end index less than start
        assertThrows<FunctionArgumentException> {
            MoreArrayFunctions.slice(listOf(
                UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3))),
                UDM.Scalar(2),
                UDM.Scalar(1)
            ))
        }
    }

    @Test
    fun testConcat() {
        // Test basic concatenation
        val result1 = MoreArrayFunctions.concat(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0))),
            UDM.Array(listOf(UDM.Scalar(3.0), UDM.Scalar(4.0))),
            UDM.Array(listOf(UDM.Scalar(5.0)))
        ))
        assertEquals(5, (result1 as UDM.Array).elements.size)
        assertEquals(1.0, ((result1 as UDM.Array).elements[0] as UDM.Scalar).value)
        assertEquals(2.0, ((result1 as UDM.Array).elements[1] as UDM.Scalar).value)
        assertEquals(3.0, ((result1 as UDM.Array).elements[2] as UDM.Scalar).value)
        assertEquals(4.0, ((result1 as UDM.Array).elements[3] as UDM.Scalar).value)
        assertEquals(5.0, ((result1 as UDM.Array).elements[4] as UDM.Scalar).value)

        // Test with empty arrays
        val result2 = MoreArrayFunctions.concat(listOf(
            UDM.Array(listOf(UDM.Scalar(1.0))),
            UDM.Array(emptyList()),
            UDM.Array(listOf(UDM.Scalar(2.0)))
        ))
        assertEquals(2, (result2 as UDM.Array).elements.size)
        assertEquals(1.0, ((result2 as UDM.Array).elements[0] as UDM.Scalar).value)
        assertEquals(2.0, ((result2 as UDM.Array).elements[1] as UDM.Scalar).value)

        // Test no arguments
        val result3 = MoreArrayFunctions.concat(emptyList())
        assertEquals(0, (result3 as UDM.Array).elements.size)

        // Test single array
        val result4 = MoreArrayFunctions.concat(listOf(
            UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b")))
        ))
        assertEquals(2, (result4 as UDM.Array).elements.size)
        assertEquals("a", ((result4 as UDM.Array).elements[0] as UDM.Scalar).value)
        assertEquals("b", ((result4 as UDM.Array).elements[1] as UDM.Scalar).value)
    }

    @Test
    fun testConcatEdgeCases() {
        // Test non-array argument
        assertThrows<FunctionArgumentException> {
            MoreArrayFunctions.concat(listOf(
                UDM.Array(listOf(UDM.Scalar(1))),
                UDM.Scalar("not array")
            ))
        }
    }

    @Test
    fun testComplexDataTypes() {
        // Test with nested arrays
        val nestedArray = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        val result1 = MoreArrayFunctions.indexOf(listOf(
            UDM.Array(listOf(UDM.Scalar("a"), nestedArray, UDM.Scalar("b"))),
            nestedArray
        ))
        assertEquals(1.0, (result1 as UDM.Scalar).value)

        // Test with objects
        val obj = UDM.Object(mapOf("key" to UDM.Scalar("value")))
        val result2 = MoreArrayFunctions.includes(listOf(
            UDM.Array(listOf(UDM.Scalar("a"), obj, UDM.Scalar("b"))),
            obj
        ))
        assertTrue((result2 as UDM.Scalar).value as Boolean)
    }
}