package org.apache.utlx.stdlib.util

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B23: Tests for treeMap and treeFilter with real lambda arguments.
 */
class TreeFunctionsB23Test {

    private fun lambda(fn: (UDM) -> UDM): UDM.Lambda = UDM.Lambda { args -> fn(args[0]) }

    // =========================================================================
    // treeMap(tree, function)
    // =========================================================================

    @Test fun `treeMap - doubles all leaf values`() {
        val tree = UDM.Object.of("a" to UDM.Scalar(1), "b" to UDM.Scalar(2), "c" to UDM.Scalar(3))
        val result = TreeFunctions.treeMap(listOf(tree, lambda { UDM.Scalar(it.asNumber() * 2) })) as UDM.Object
        assertEquals(2.0, result.get("a")?.asNumber())
        assertEquals(4.0, result.get("b")?.asNumber())
        assertEquals(6.0, result.get("c")?.asNumber())
    }

    @Test fun `treeMap - nested objects`() {
        val tree = UDM.Object.of(
            "x" to UDM.Scalar(10),
            "nested" to UDM.Object.of("y" to UDM.Scalar(20))
        )
        val result = TreeFunctions.treeMap(listOf(tree, lambda { UDM.Scalar(it.asNumber() + 1) })) as UDM.Object
        assertEquals(11.0, result.get("x")?.asNumber())
        assertEquals(21.0, (result.get("nested") as UDM.Object).get("y")?.asNumber())
    }

    @Test fun `treeMap - array in tree`() {
        val tree = UDM.Object.of(
            "items" to UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        )
        val result = TreeFunctions.treeMap(listOf(tree, lambda { UDM.Scalar(it.asNumber() * 10) })) as UDM.Object
        val items = result.get("items") as UDM.Array
        assertEquals(10.0, (items.elements[0] as UDM.Scalar).value)
        assertEquals(20.0, (items.elements[1] as UDM.Scalar).value)
        assertEquals(30.0, (items.elements[2] as UDM.Scalar).value)
    }

    @Test fun `treeMap - strings to uppercase`() {
        val tree = UDM.Object.of("name" to UDM.Scalar("alice"), "city" to UDM.Scalar("amsterdam"))
        val result = TreeFunctions.treeMap(listOf(tree, lambda {
            if (it is UDM.Scalar && it.value is String) UDM.Scalar((it.value as String).uppercase()) else it
        })) as UDM.Object
        assertEquals("ALICE", result.get("name")?.asString())
        assertEquals("AMSTERDAM", result.get("city")?.asString())
    }

    @Test fun `treeMap - empty object`() {
        val tree = UDM.Object(emptyMap())
        val result = TreeFunctions.treeMap(listOf(tree, lambda { it })) as UDM.Object
        assertTrue(result.properties.isEmpty())
    }

    @Test fun `treeMap - identity function returns same structure`() {
        val tree = UDM.Object.of("a" to UDM.Scalar(1), "b" to UDM.Scalar(2))
        val result = TreeFunctions.treeMap(listOf(tree, lambda { it })) as UDM.Object
        assertEquals(1, (result.get("a") as UDM.Scalar).value)
        assertEquals(2, (result.get("b") as UDM.Scalar).value)
    }

    @Test fun `treeMap - wrong arg count throws`() {
        assertThrows<IllegalArgumentException> { TreeFunctions.treeMap(listOf(UDM.Scalar(1))) }
    }

    @Test fun `treeMap - not lambda throws`() {
        assertThrows<IllegalArgumentException> { TreeFunctions.treeMap(listOf(UDM.Object(emptyMap()), UDM.Scalar("x"))) }
    }

    // =========================================================================
    // treeFilter(tree, predicate)
    // =========================================================================

    @Test fun `treeFilter - keeps matching leaves`() {
        val tree = UDM.Object.of("a" to UDM.Scalar(1), "b" to UDM.Scalar(2), "c" to UDM.Scalar(3))
        val result = TreeFunctions.treeFilter(listOf(tree, lambda { UDM.Scalar(it.asNumber() > 1) })) as UDM.Object
        assertNull(result.get("a"))  // 1 is not > 1
        assertEquals(2, (result.get("b") as UDM.Scalar).value)
        assertEquals(3, (result.get("c") as UDM.Scalar).value)
    }

    @Test fun `treeFilter - nested keeps structure`() {
        val tree = UDM.Object.of(
            "keep" to UDM.Scalar(10),
            "nested" to UDM.Object.of("keep" to UDM.Scalar(20), "drop" to UDM.Scalar(0))
        )
        val result = TreeFunctions.treeFilter(listOf(tree, lambda { UDM.Scalar(it.asNumber() > 5) })) as UDM.Object
        assertEquals(10, (result.get("keep") as UDM.Scalar).value)
        val nested = result.get("nested") as UDM.Object
        assertEquals(20, (nested.get("keep") as UDM.Scalar).value)
        assertNull(nested.get("drop"))
    }

    @Test fun `treeFilter - all filtered returns null`() {
        val tree = UDM.Object.of("a" to UDM.Scalar(1), "b" to UDM.Scalar(2))
        val result = TreeFunctions.treeFilter(listOf(tree, lambda { UDM.Scalar(false) }))
        assertTrue(result is UDM.Scalar && result.value == null)
    }

    @Test fun `treeFilter - all kept`() {
        val tree = UDM.Object.of("a" to UDM.Scalar(1), "b" to UDM.Scalar(2))
        val result = TreeFunctions.treeFilter(listOf(tree, lambda { UDM.Scalar(true) })) as UDM.Object
        assertEquals(2, result.properties.size)
    }

    @Test fun `treeFilter - array elements filtered`() {
        val tree = UDM.Object.of(
            "items" to UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(5), UDM.Scalar(2), UDM.Scalar(8)))
        )
        val result = TreeFunctions.treeFilter(listOf(tree, lambda { UDM.Scalar(it.asNumber() > 3) })) as UDM.Object
        val items = result.get("items") as UDM.Array
        assertEquals(2, items.elements.size)  // 5 and 8
    }

    @Test fun `treeFilter - wrong arg count throws`() {
        assertThrows<IllegalArgumentException> { TreeFunctions.treeFilter(listOf(UDM.Scalar(1))) }
    }

    @Test fun `treeFilter - not lambda throws`() {
        assertThrows<IllegalArgumentException> { TreeFunctions.treeFilter(listOf(UDM.Object(emptyMap()), UDM.Scalar("x"))) }
    }
}
