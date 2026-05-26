package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B23: Tests for JoinFunctions with real lambda key functions and combiners.
 */
class JoinFunctionsB23Test {

    private fun lambda(fn: (UDM) -> UDM): UDM.Lambda = UDM.Lambda { args -> fn(args[0]) }
    private fun lambda2(fn: (UDM, UDM) -> UDM): UDM.Lambda = UDM.Lambda { args -> fn(args[0], args[1]) }

    private fun customers() = UDM.Array(listOf(
        UDM.Object.of("id" to UDM.Scalar(1), "name" to UDM.Scalar("Alice")),
        UDM.Object.of("id" to UDM.Scalar(2), "name" to UDM.Scalar("Bob")),
        UDM.Object.of("id" to UDM.Scalar(3), "name" to UDM.Scalar("Charlie"))
    ))

    private fun orders() = UDM.Array(listOf(
        UDM.Object.of("customerId" to UDM.Scalar(1), "product" to UDM.Scalar("Widget")),
        UDM.Object.of("customerId" to UDM.Scalar(2), "product" to UDM.Scalar("Gadget")),
        UDM.Object.of("customerId" to UDM.Scalar(1), "product" to UDM.Scalar("Sensor"))
    ))

    // =========================================================================
    // joinWith — lambda key functions
    // =========================================================================

    @Test fun `joinWith - lambda key functions match correctly`() {
        val leftKeyFn = lambda { (it as UDM.Object).get("id")!! }
        val rightKeyFn = lambda { (it as UDM.Object).get("customerId")!! }
        val combiner = lambda2 { l, r ->
            UDM.Object.of(
                "name" to (l as UDM.Object).get("name")!!,
                "product" to (r as UDM.Object).get("product")!!
            )
        }

        val result = JoinFunctions.joinWith(listOf(customers(), orders(), leftKeyFn, rightKeyFn, combiner))
        assertTrue(result is UDM.Array)
        val arr = result as UDM.Array
        // Alice has 2 orders (Widget, Sensor), Bob has 1 (Gadget), Charlie has 0
        assertEquals(3, arr.elements.size)
    }

    @Test fun `joinWith - combiner receives correct items`() {
        val left = UDM.Array(listOf(UDM.Object.of("k" to UDM.Scalar(1), "v" to UDM.Scalar("left"))))
        val right = UDM.Array(listOf(UDM.Object.of("k" to UDM.Scalar(1), "v" to UDM.Scalar("right"))))

        val keyFn = lambda { (it as UDM.Object).get("k")!! }
        val combiner = lambda2 { l, r ->
            UDM.Object.of(
                "lv" to (l as UDM.Object).get("v")!!,
                "rv" to (r as UDM.Object).get("v")!!
            )
        }

        val result = JoinFunctions.joinWith(listOf(left, right, keyFn, keyFn, combiner)) as UDM.Array
        assertEquals(1, result.elements.size)
        val joined = result.elements[0] as UDM.Object
        assertEquals("left", joined.get("lv")?.asString())
        assertEquals("right", joined.get("rv")?.asString())
    }

    @Test fun `joinWith - no matches returns empty`() {
        val left = UDM.Array(listOf(UDM.Object.of("k" to UDM.Scalar(1))))
        val right = UDM.Array(listOf(UDM.Object.of("k" to UDM.Scalar(2))))

        val keyFn = lambda { (it as UDM.Object).get("k")!! }
        val combiner = lambda2 { l, r -> UDM.Object.of("l" to l, "r" to r) }

        val result = JoinFunctions.joinWith(listOf(left, right, keyFn, keyFn, combiner)) as UDM.Array
        assertEquals(0, result.elements.size)
    }

    @Test fun `joinWith - string key functions still work`() {
        // String keys (property name) should still work as before
        val result = JoinFunctions.joinWith(listOf(
            customers(), orders(),
            UDM.Scalar("id"), UDM.Scalar("customerId"),
            lambda2 { l, r -> UDM.Object.of("n" to (l as UDM.Object).get("name")!!, "p" to (r as UDM.Object).get("product")!!) }
        )) as UDM.Array
        assertEquals(3, result.elements.size)
    }

    @Test fun `joinWith - empty left array`() {
        val keyFn = lambda { it }
        val combiner = lambda2 { l, r -> UDM.Object.of("l" to l, "r" to r) }
        val result = JoinFunctions.joinWith(listOf(UDM.Array(emptyList()), UDM.Array(listOf(UDM.Scalar(1))), keyFn, keyFn, combiner)) as UDM.Array
        assertEquals(0, result.elements.size)
    }

    @Test fun `joinWith - empty right array`() {
        val keyFn = lambda { it }
        val combiner = lambda2 { l, r -> UDM.Object.of("l" to l, "r" to r) }
        val result = JoinFunctions.joinWith(listOf(UDM.Array(listOf(UDM.Scalar(1))), UDM.Array(emptyList()), keyFn, keyFn, combiner)) as UDM.Array
        assertEquals(0, result.elements.size)
    }
}
