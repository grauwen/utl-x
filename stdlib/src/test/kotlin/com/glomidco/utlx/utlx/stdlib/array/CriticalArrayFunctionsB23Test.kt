package com.glomidco.utlx.stdlib.array

import com.glomidco.utlx.core.udm.UDM
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
    // findLastIndex(array, predicate)
    // =========================================================================

    @Test fun `findLastIndex - finds last match`() {
        val result = CriticalArrayFunctions.findLastIndex(listOf(nums(1, 2, 3, 2, 1), lambda { UDM.Scalar(it.asNumber() == 2.0) }))
        assertEquals(3.0, (result as UDM.Scalar).value)  // last 2 is at index 3
    }

    // B73: these two asserted -1 until 2026-11-26. The value changed deliberately, as part of
    // settling what "not found" means across the whole library: `indexOf`, `lastIndexOf`, `find`
    // and (after B73) `findIndex` all answer null, so -1 was the outlier here. The old value stays
    // reachable at the call site - `findLastIndex(...) ?? -1` - whereas recovering null from -1
    // needs a comparison and evaluates the search twice.
    @Test fun `findLastIndex - no match returns null`() {
        val result = CriticalArrayFunctions.findLastIndex(listOf(nums(1, 2, 3), lambda { UDM.Scalar(it.asNumber() > 100) }))
        assertEquals(null, (result as UDM.Scalar).value)
    }

    @Test fun `findLastIndex - empty array returns null`() {
        val result = CriticalArrayFunctions.findLastIndex(listOf(nums(), lambda { UDM.Scalar(true) }))
        assertEquals(null, (result as UDM.Scalar).value)
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
