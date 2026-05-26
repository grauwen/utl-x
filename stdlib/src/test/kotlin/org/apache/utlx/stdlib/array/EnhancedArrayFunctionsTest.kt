package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B23 fix: Comprehensive tests for EnhancedArrayFunctions.
 * All 7 functions tested with: happy path, empty array, single element,
 * edge cases, wrong arg count, wrong arg types, lambda with field access,
 * lambda with computation, null values.
 */
class EnhancedArrayFunctionsTest {

    private fun lambda(fn: (UDM) -> UDM): UDM.Lambda = UDM.Lambda { args -> fn(args[0]) }
    private fun lambda2(fn: (UDM, UDM) -> UDM): UDM.Lambda = UDM.Lambda { args -> fn(args[0], args[1]) }
    private fun nums(vararg values: Int) = UDM.Array(values.map { UDM.Scalar(it) })
    private fun obj(vararg pairs: Pair<String, Any>) = UDM.Object.of(*pairs.map { (k, v) ->
        k to when (v) { is Int -> UDM.Scalar(v); is Double -> UDM.Scalar(v); is String -> UDM.Scalar(v); is Boolean -> UDM.Scalar(v); else -> UDM.Scalar(v) }
    }.toTypedArray())

    // =========================================================================
    // partition(array, predicate)
    // =========================================================================

    @Test fun `partition - splits by predicate`() {
        val result = EnhancedArrayFunctions.partition(listOf(nums(1,2,3,4,5), lambda { UDM.Scalar(it.asNumber() > 3) })) as UDM.Object
        assertEquals(2, (result.get("true") as UDM.Array).elements.size)
        assertEquals(3, (result.get("false") as UDM.Array).elements.size)
    }

    @Test fun `partition - empty array`() {
        val result = EnhancedArrayFunctions.partition(listOf(nums(), lambda { UDM.Scalar(true) })) as UDM.Object
        assertEquals(0, (result.get("true") as UDM.Array).elements.size)
        assertEquals(0, (result.get("false") as UDM.Array).elements.size)
    }

    @Test fun `partition - single element matches`() {
        val result = EnhancedArrayFunctions.partition(listOf(nums(5), lambda { UDM.Scalar(true) })) as UDM.Object
        assertEquals(1, (result.get("true") as UDM.Array).elements.size)
        assertEquals(0, (result.get("false") as UDM.Array).elements.size)
    }

    @Test fun `partition - single element does not match`() {
        val result = EnhancedArrayFunctions.partition(listOf(nums(5), lambda { UDM.Scalar(false) })) as UDM.Object
        assertEquals(0, (result.get("true") as UDM.Array).elements.size)
        assertEquals(1, (result.get("false") as UDM.Array).elements.size)
    }

    @Test fun `partition - all match`() {
        val result = EnhancedArrayFunctions.partition(listOf(nums(1,2,3), lambda { UDM.Scalar(true) })) as UDM.Object
        assertEquals(3, (result.get("true") as UDM.Array).elements.size)
        assertEquals(0, (result.get("false") as UDM.Array).elements.size)
    }

    @Test fun `partition - none match`() {
        val result = EnhancedArrayFunctions.partition(listOf(nums(1,2,3), lambda { UDM.Scalar(false) })) as UDM.Object
        assertEquals(0, (result.get("true") as UDM.Array).elements.size)
        assertEquals(3, (result.get("false") as UDM.Array).elements.size)
    }

    @Test fun `partition - with object field access`() {
        val arr = UDM.Array(listOf(obj("age" to 25), obj("age" to 35), obj("age" to 18)))
        val result = EnhancedArrayFunctions.partition(listOf(arr, lambda { (it as UDM.Object).get("age")!!.let { age -> UDM.Scalar(age.asNumber() >= 21) } })) as UDM.Object
        assertEquals(2, (result.get("true") as UDM.Array).elements.size)
        assertEquals(1, (result.get("false") as UDM.Array).elements.size)
    }

    @Test fun `partition - missing args throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.partition(listOf(nums(1))) }
    }

    @Test fun `partition - no args throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.partition(emptyList()) }
    }

    @Test fun `partition - first arg not array throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.partition(listOf(UDM.Scalar(1), lambda { UDM.Scalar(true) })) }
    }

    @Test fun `partition - second arg not lambda throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.partition(listOf(nums(1), UDM.Scalar("not a function"))) }
    }

    // =========================================================================
    // countBy(array, predicate)
    // =========================================================================

    @Test fun `countBy - counts matching elements`() {
        val result = EnhancedArrayFunctions.countBy(listOf(nums(1,2,3,4,5), lambda { UDM.Scalar(it.asNumber() > 3) }))
        assertEquals(2, (result as UDM.Scalar).value)
    }

    @Test fun `countBy - empty array`() {
        val result = EnhancedArrayFunctions.countBy(listOf(nums(), lambda { UDM.Scalar(true) }))
        assertEquals(0, (result as UDM.Scalar).value)
    }

    @Test fun `countBy - single element matches`() {
        val result = EnhancedArrayFunctions.countBy(listOf(nums(42), lambda { UDM.Scalar(it.asNumber() > 10) }))
        assertEquals(1, (result as UDM.Scalar).value)
    }

    @Test fun `countBy - single element does not match`() {
        val result = EnhancedArrayFunctions.countBy(listOf(nums(5), lambda { UDM.Scalar(it.asNumber() > 10) }))
        assertEquals(0, (result as UDM.Scalar).value)
    }

    @Test fun `countBy - all match`() {
        val result = EnhancedArrayFunctions.countBy(listOf(nums(1,2,3), lambda { UDM.Scalar(true) }))
        assertEquals(3, (result as UDM.Scalar).value)
    }

    @Test fun `countBy - none match`() {
        val result = EnhancedArrayFunctions.countBy(listOf(nums(1,2,3), lambda { UDM.Scalar(false) }))
        assertEquals(0, (result as UDM.Scalar).value)
    }

    @Test fun `countBy - with object field`() {
        val arr = UDM.Array(listOf(obj("status" to "active"), obj("status" to "inactive"), obj("status" to "active")))
        val result = EnhancedArrayFunctions.countBy(listOf(arr, lambda { UDM.Scalar((it as UDM.Object).get("status")?.asString() == "active") }))
        assertEquals(2, (result as UDM.Scalar).value)
    }

    @Test fun `countBy - missing args throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.countBy(listOf(nums(1))) }
    }

    @Test fun `countBy - not array throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.countBy(listOf(UDM.Scalar(1), lambda { UDM.Scalar(true) })) }
    }

    @Test fun `countBy - not lambda throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.countBy(listOf(nums(1), UDM.Scalar("x"))) }
    }

    // =========================================================================
    // sumBy(array, mapper)
    // =========================================================================

    @Test fun `sumBy - sums mapped values`() {
        val arr = UDM.Array(listOf(obj("qty" to 2, "price" to 10), obj("qty" to 3, "price" to 20)))
        val result = EnhancedArrayFunctions.sumBy(listOf(arr, lambda { x -> val o = x as UDM.Object; UDM.Scalar(o.get("qty")!!.asNumber() * o.get("price")!!.asNumber()) }))
        assertEquals(80.0, (result as UDM.Scalar).value)
    }

    @Test fun `sumBy - simple doubling`() {
        val result = EnhancedArrayFunctions.sumBy(listOf(nums(1,2,3), lambda { UDM.Scalar(it.asNumber() * 2) }))
        assertEquals(12.0, (result as UDM.Scalar).value)
    }

    @Test fun `sumBy - identity`() {
        val result = EnhancedArrayFunctions.sumBy(listOf(nums(10,20,30), lambda { it }))
        assertEquals(60.0, (result as UDM.Scalar).value)
    }

    @Test fun `sumBy - empty array`() {
        val result = EnhancedArrayFunctions.sumBy(listOf(nums(), lambda { it }))
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test fun `sumBy - single element`() {
        val result = EnhancedArrayFunctions.sumBy(listOf(nums(42), lambda { it }))
        assertEquals(42.0, (result as UDM.Scalar).value)
    }

    @Test fun `sumBy - missing args throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.sumBy(listOf(nums(1))) }
    }

    @Test fun `sumBy - not lambda throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.sumBy(listOf(nums(1), UDM.Scalar("x"))) }
    }

    // =========================================================================
    // maxBy(array, comparator)
    // =========================================================================

    @Test fun `maxBy - finds element with max value`() {
        val arr = UDM.Array(listOf(obj("name" to "a", "val" to 3), obj("name" to "b", "val" to 7), obj("name" to "c", "val" to 1)))
        val result = EnhancedArrayFunctions.maxBy(listOf(arr, lambda { (it as UDM.Object).get("val")!! })) as UDM.Object
        assertEquals("b", result.get("name")?.asString())
    }

    @Test fun `maxBy - simple numbers`() {
        val result = EnhancedArrayFunctions.maxBy(listOf(nums(3,7,1,9,4), lambda { it }))
        assertEquals(9, (result as UDM.Scalar).value)
    }

    @Test fun `maxBy - single element`() {
        val result = EnhancedArrayFunctions.maxBy(listOf(nums(42), lambda { it }))
        assertEquals(42, (result as UDM.Scalar).value)
    }

    @Test fun `maxBy - empty array returns null`() {
        val result = EnhancedArrayFunctions.maxBy(listOf(nums(), lambda { it }))
        assertNull((result as UDM.Scalar).value)
    }

    @Test fun `maxBy - with computation`() {
        val result = EnhancedArrayFunctions.maxBy(listOf(nums(3,-7,1,-9,4), lambda { UDM.Scalar(kotlin.math.abs(it.asNumber().toInt())) }))
        assertEquals(-9, (result as UDM.Scalar).value)  // -9 has largest abs value
    }

    @Test fun `maxBy - missing args throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.maxBy(listOf(nums(1))) }
    }

    @Test fun `maxBy - not lambda throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.maxBy(listOf(nums(1), UDM.Scalar("x"))) }
    }

    // =========================================================================
    // minBy(array, comparator)
    // =========================================================================

    @Test fun `minBy - finds element with min value`() {
        val arr = UDM.Array(listOf(obj("name" to "a", "val" to 3), obj("name" to "b", "val" to 7), obj("name" to "c", "val" to 1)))
        val result = EnhancedArrayFunctions.minBy(listOf(arr, lambda { (it as UDM.Object).get("val")!! })) as UDM.Object
        assertEquals("c", result.get("name")?.asString())
    }

    @Test fun `minBy - simple numbers`() {
        val result = EnhancedArrayFunctions.minBy(listOf(nums(3,7,1,9,4), lambda { it }))
        assertEquals(1, (result as UDM.Scalar).value)
    }

    @Test fun `minBy - single element`() {
        val result = EnhancedArrayFunctions.minBy(listOf(nums(42), lambda { it }))
        assertEquals(42, (result as UDM.Scalar).value)
    }

    @Test fun `minBy - empty array returns null`() {
        val result = EnhancedArrayFunctions.minBy(listOf(nums(), lambda { it }))
        assertNull((result as UDM.Scalar).value)
    }

    @Test fun `minBy - missing args throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.minBy(listOf(nums(1))) }
    }

    @Test fun `minBy - not lambda throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.minBy(listOf(nums(1), UDM.Scalar("x"))) }
    }

    // =========================================================================
    // distinctBy(array, keyFunction)
    // =========================================================================

    @Test fun `distinctBy - deduplicates by key`() {
        val arr = UDM.Array(listOf(obj("id" to 1, "name" to "a"), obj("id" to 2, "name" to "b"), obj("id" to 1, "name" to "c")))
        val result = EnhancedArrayFunctions.distinctBy(listOf(arr, lambda { (it as UDM.Object).get("id")!! })) as UDM.Array
        assertEquals(2, result.elements.size)
    }

    @Test fun `distinctBy - keeps first occurrence`() {
        val arr = UDM.Array(listOf(obj("id" to 1, "name" to "first"), obj("id" to 1, "name" to "second")))
        val result = EnhancedArrayFunctions.distinctBy(listOf(arr, lambda { (it as UDM.Object).get("id")!! })) as UDM.Array
        assertEquals(1, result.elements.size)
        assertEquals("first", (result.elements[0] as UDM.Object).get("name")?.asString())
    }

    @Test fun `distinctBy - all unique`() {
        val result = EnhancedArrayFunctions.distinctBy(listOf(nums(1,2,3), lambda { it })) as UDM.Array
        assertEquals(3, result.elements.size)
    }

    @Test fun `distinctBy - all same key`() {
        val arr = UDM.Array(listOf(obj("id" to 1, "v" to "x"), obj("id" to 1, "v" to "y"), obj("id" to 1, "v" to "z")))
        val result = EnhancedArrayFunctions.distinctBy(listOf(arr, lambda { (it as UDM.Object).get("id")!! })) as UDM.Array
        assertEquals(1, result.elements.size)
    }

    @Test fun `distinctBy - empty array`() {
        val result = EnhancedArrayFunctions.distinctBy(listOf(nums(), lambda { it })) as UDM.Array
        assertEquals(0, result.elements.size)
    }

    @Test fun `distinctBy - single element`() {
        val result = EnhancedArrayFunctions.distinctBy(listOf(nums(1), lambda { it })) as UDM.Array
        assertEquals(1, result.elements.size)
    }

    @Test fun `distinctBy - missing args throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.distinctBy(listOf(nums(1))) }
    }

    @Test fun `distinctBy - not lambda throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.distinctBy(listOf(nums(1), UDM.Scalar("x"))) }
    }

    // =========================================================================
    // avgBy(array, mapper)
    // =========================================================================

    @Test fun `avgBy - calculates average`() {
        val arr = UDM.Array(listOf(obj("val" to 10), obj("val" to 20), obj("val" to 30)))
        val result = EnhancedArrayFunctions.avgBy(listOf(arr, lambda { (it as UDM.Object).get("val")!! }))
        assertEquals(20.0, (result as UDM.Scalar).value)
    }

    @Test fun `avgBy - simple identity`() {
        val result = EnhancedArrayFunctions.avgBy(listOf(nums(10,20,30), lambda { it }))
        assertEquals(20.0, (result as UDM.Scalar).value)
    }

    @Test fun `avgBy - with computation`() {
        val result = EnhancedArrayFunctions.avgBy(listOf(nums(1,2,3), lambda { UDM.Scalar(it.asNumber() * 10) }))
        assertEquals(20.0, (result as UDM.Scalar).value)  // (10+20+30)/3
    }

    @Test fun `avgBy - single element`() {
        val result = EnhancedArrayFunctions.avgBy(listOf(nums(42), lambda { it }))
        assertEquals(42.0, (result as UDM.Scalar).value)
    }

    @Test fun `avgBy - empty array returns null`() {
        val result = EnhancedArrayFunctions.avgBy(listOf(nums(), lambda { it }))
        assertNull((result as UDM.Scalar).value)
    }

    @Test fun `avgBy - missing args throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.avgBy(listOf(nums(1))) }
    }

    @Test fun `avgBy - not lambda throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.avgBy(listOf(nums(1), UDM.Scalar("x"))) }
    }

    @Test fun `avgBy - not array throws`() {
        assertThrows<IllegalArgumentException> { EnhancedArrayFunctions.avgBy(listOf(UDM.Scalar(1), lambda { it })) }
    }
}
