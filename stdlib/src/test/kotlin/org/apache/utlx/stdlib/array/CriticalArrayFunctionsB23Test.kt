package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B23: Tests for CriticalArrayFunctions with real lambda arguments.
 * Covers findIndex, findLastIndex, scan.
 */
class CriticalArrayFunctionsB23Test {

    private fun lambda(fn: (UDM) -> UDM): UDM.Lambda = UDM.Lambda { args -> fn(args[0]) }
    private fun lambda2(fn: (UDM, UDM) -> UDM): UDM.Lambda = UDM.Lambda { args -> fn(args[0], args[1]) }
    private fun nums(vararg values: Int) = UDM.Array(values.map { UDM.Scalar(it) })

    // =========================================================================
    // findIndex(array, predicate)
    // =========================================================================

    @Test fun `findIndex - finds first match`() {
        val result = CriticalArrayFunctions.findIndex(listOf(nums(10, 20, 30, 40), lambda { UDM.Scalar(it.asNumber() > 25) }))
        assertEquals(2.0, (result as UDM.Scalar).value)  // index of 30
    }

    @Test fun `findIndex - returns first not last`() {
        val result = CriticalArrayFunctions.findIndex(listOf(nums(1, 2, 3, 2, 1), lambda { UDM.Scalar(it.asNumber() == 2.0) }))
        assertEquals(1.0, (result as UDM.Scalar).value)  // first 2 is at index 1
    }

    @Test fun `findIndex - no match returns -1`() {
        val result = CriticalArrayFunctions.findIndex(listOf(nums(1, 2, 3), lambda { UDM.Scalar(it.asNumber() > 100) }))
        assertEquals(-1.0, (result as UDM.Scalar).value)
    }

    @Test fun `findIndex - empty array returns -1`() {
        val result = CriticalArrayFunctions.findIndex(listOf(nums(), lambda { UDM.Scalar(true) }))
        assertEquals(-1.0, (result as UDM.Scalar).value)
    }

    @Test fun `findIndex - single element matches`() {
        val result = CriticalArrayFunctions.findIndex(listOf(nums(42), lambda { UDM.Scalar(it.asNumber() == 42.0) }))
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test fun `findIndex - single element no match`() {
        val result = CriticalArrayFunctions.findIndex(listOf(nums(42), lambda { UDM.Scalar(it.asNumber() == 99.0) }))
        assertEquals(-1.0, (result as UDM.Scalar).value)
    }

    @Test fun `findIndex - wrong arg count throws`() {
        assertThrows<IllegalArgumentException> { CriticalArrayFunctions.findIndex(listOf(nums(1))) }
    }

    @Test fun `findIndex - not array throws`() {
        assertThrows<IllegalArgumentException> { CriticalArrayFunctions.findIndex(listOf(UDM.Scalar(1), lambda { UDM.Scalar(true) })) }
    }

    @Test fun `findIndex - not lambda throws`() {
        assertThrows<IllegalArgumentException> { CriticalArrayFunctions.findIndex(listOf(nums(1), UDM.Scalar("x"))) }
    }

    // =========================================================================
    // findLastIndex(array, predicate)
    // =========================================================================

    @Test fun `findLastIndex - finds last match`() {
        val result = CriticalArrayFunctions.findLastIndex(listOf(nums(1, 2, 3, 2, 1), lambda { UDM.Scalar(it.asNumber() == 2.0) }))
        assertEquals(3.0, (result as UDM.Scalar).value)  // last 2 is at index 3
    }

    @Test fun `findLastIndex - no match returns -1`() {
        val result = CriticalArrayFunctions.findLastIndex(listOf(nums(1, 2, 3), lambda { UDM.Scalar(it.asNumber() > 100) }))
        assertEquals(-1.0, (result as UDM.Scalar).value)
    }

    @Test fun `findLastIndex - empty array returns -1`() {
        val result = CriticalArrayFunctions.findLastIndex(listOf(nums(), lambda { UDM.Scalar(true) }))
        assertEquals(-1.0, (result as UDM.Scalar).value)
    }

    @Test fun `findLastIndex - single element matches`() {
        val result = CriticalArrayFunctions.findLastIndex(listOf(nums(42), lambda { UDM.Scalar(true) }))
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test fun `findLastIndex - all match returns last index`() {
        val result = CriticalArrayFunctions.findLastIndex(listOf(nums(1, 2, 3), lambda { UDM.Scalar(true) }))
        assertEquals(2.0, (result as UDM.Scalar).value)
    }

    @Test fun `findLastIndex - wrong arg count throws`() {
        assertThrows<IllegalArgumentException> { CriticalArrayFunctions.findLastIndex(listOf(nums(1))) }
    }

    @Test fun `findLastIndex - not lambda throws`() {
        assertThrows<IllegalArgumentException> { CriticalArrayFunctions.findLastIndex(listOf(nums(1), UDM.Scalar("x"))) }
    }

    // =========================================================================
    // scan(array, reducer, initial)
    // =========================================================================

    @Test fun `scan - running sum`() {
        val reducer = lambda2 { acc, x -> UDM.Scalar(acc.asNumber() + x.asNumber()) }
        val result = CriticalArrayFunctions.scan(listOf(nums(1, 2, 3, 4), reducer, UDM.Scalar(0))) as UDM.Array
        assertEquals(4, result.elements.size)
        assertEquals(1.0, (result.elements[0] as UDM.Scalar).value)   // 0+1
        assertEquals(3.0, (result.elements[1] as UDM.Scalar).value)   // 1+2
        assertEquals(6.0, (result.elements[2] as UDM.Scalar).value)   // 3+3
        assertEquals(10.0, (result.elements[3] as UDM.Scalar).value)  // 6+4
    }

    @Test fun `scan - running product`() {
        val reducer = lambda2 { acc, x -> UDM.Scalar(acc.asNumber() * x.asNumber()) }
        val result = CriticalArrayFunctions.scan(listOf(nums(1, 2, 3), reducer, UDM.Scalar(1))) as UDM.Array
        assertEquals(3, result.elements.size)
        assertEquals(1.0, (result.elements[0] as UDM.Scalar).value)   // 1*1
        assertEquals(2.0, (result.elements[1] as UDM.Scalar).value)   // 1*2
        assertEquals(6.0, (result.elements[2] as UDM.Scalar).value)   // 2*3
    }

    @Test fun `scan - empty array`() {
        val reducer = lambda2 { acc, x -> UDM.Scalar(acc.asNumber() + x.asNumber()) }
        val result = CriticalArrayFunctions.scan(listOf(nums(), reducer, UDM.Scalar(0))) as UDM.Array
        assertEquals(0, result.elements.size)
    }

    @Test fun `scan - single element`() {
        val reducer = lambda2 { acc, x -> UDM.Scalar(acc.asNumber() + x.asNumber()) }
        val result = CriticalArrayFunctions.scan(listOf(nums(5), reducer, UDM.Scalar(10))) as UDM.Array
        assertEquals(1, result.elements.size)
        assertEquals(15.0, (result.elements[0] as UDM.Scalar).value)  // 10+5
    }

    @Test fun `scan - string concatenation`() {
        val arr = UDM.Array(listOf(UDM.Scalar("b"), UDM.Scalar("c"), UDM.Scalar("d")))
        val reducer = lambda2 { acc, x -> UDM.Scalar(acc.asString() + x.asString()) }
        val result = CriticalArrayFunctions.scan(listOf(arr, reducer, UDM.Scalar("a"))) as UDM.Array
        assertEquals(3, result.elements.size)
        assertEquals("ab", (result.elements[0] as UDM.Scalar).value)
        assertEquals("abc", (result.elements[1] as UDM.Scalar).value)
        assertEquals("abcd", (result.elements[2] as UDM.Scalar).value)
    }

    @Test fun `scan - wrong arg count throws`() {
        assertThrows<IllegalArgumentException> { CriticalArrayFunctions.scan(listOf(nums(1), lambda { it })) }
    }

    @Test fun `scan - not array throws`() {
        assertThrows<IllegalArgumentException> { CriticalArrayFunctions.scan(listOf(UDM.Scalar(1), lambda { it }, UDM.Scalar(0))) }
    }

    @Test fun `scan - not lambda throws`() {
        assertThrows<IllegalArgumentException> { CriticalArrayFunctions.scan(listOf(nums(1), UDM.Scalar("x"), UDM.Scalar(0))) }
    }
}
