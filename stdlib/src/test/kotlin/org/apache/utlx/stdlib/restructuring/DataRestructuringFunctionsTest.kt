package org.apache.utlx.stdlib.restructuring

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataRestructuringFunctionsTest {

    // ── groupBy tests ──

    @Test
    fun testGroupBy() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3),
            UDM.Scalar(4)
        ))

        val keyFunction = UDM.Lambda { args ->
            val value = (args[0] as UDM.Scalar).value as Number
            if (value.toInt() % 2 == 0) UDM.Scalar("even") else UDM.Scalar("odd")
        }

        val result = DataRestructuringFunctions.groupBy(listOf(array, keyFunction))

        assertTrue(result is UDM.Object, "groupBy should return UDM.Object")
        val grouped = result as UDM.Object
        assertEquals(2, grouped.properties.size)
        assertTrue(grouped.properties.containsKey("even"))
        assertTrue(grouped.properties.containsKey("odd"))

        val evenGroup = grouped.properties["even"] as UDM.Array
        assertEquals(2, evenGroup.elements.size)
        assertTrue(evenGroup.elements.any { (it as UDM.Scalar).value == 2 })
        assertTrue(evenGroup.elements.any { (it as UDM.Scalar).value == 4 })

        val oddGroup = grouped.properties["odd"] as UDM.Array
        assertEquals(2, oddGroup.elements.size)
        assertTrue(oddGroup.elements.any { (it as UDM.Scalar).value == 1 })
        assertTrue(oddGroup.elements.any { (it as UDM.Scalar).value == 3 })
    }

    @Test
    fun testGroupByEmptyArray() {
        val array = UDM.Array(emptyList())
        val keyFunction = UDM.Lambda { args ->
            val value = (args[0] as UDM.Scalar).value as Number
            if (value.toInt() % 2 == 0) UDM.Scalar("even") else UDM.Scalar("odd")
        }

        val result = DataRestructuringFunctions.groupBy(listOf(array, keyFunction))

        assertTrue(result is UDM.Object)
        assertEquals(0, (result as UDM.Object).properties.size)
    }

    @Test
    fun testGroupByIndexAccess() {
        val array = UDM.Array(listOf(
            UDM.Object(mapOf("orderId" to UDM.Scalar("A"), "product" to UDM.Scalar("Widget")), emptyMap()),
            UDM.Object(mapOf("orderId" to UDM.Scalar("A"), "product" to UDM.Scalar("Gadget")), emptyMap()),
            UDM.Object(mapOf("orderId" to UDM.Scalar("B"), "product" to UDM.Scalar("Gizmo")), emptyMap())
        ))

        val keyFunction = UDM.Lambda { args ->
            val obj = args[0] as UDM.Object
            obj.properties["orderId"] ?: UDM.Scalar("null")
        }

        val result = DataRestructuringFunctions.groupBy(listOf(array, keyFunction))

        assertTrue(result is UDM.Object)
        val grouped = result as UDM.Object
        assertEquals(2, grouped.properties.size)
        assertTrue(grouped.properties.containsKey("A"))
        assertTrue(grouped.properties.containsKey("B"))

        val groupA = grouped.properties["A"] as UDM.Array
        assertEquals(2, groupA.elements.size)

        val groupB = grouped.properties["B"] as UDM.Array
        assertEquals(1, groupB.elements.size)
        assertEquals("Gizmo", ((groupB.elements[0] as UDM.Object).properties["product"] as UDM.Scalar).value)
    }

    @Test
    fun testGroupByWithSingleElement() {
        val array = UDM.Array(listOf(UDM.Scalar(42)))
        val keyFunction = UDM.Lambda { args ->
            val value = (args[0] as UDM.Scalar).value as Number
            if (value.toInt() % 2 == 0) UDM.Scalar("even") else UDM.Scalar("odd")
        }

        val result = DataRestructuringFunctions.groupBy(listOf(array, keyFunction))

        assertTrue(result is UDM.Object)
        val grouped = result as UDM.Object
        assertEquals(1, grouped.properties.size)
        assertTrue(grouped.properties.containsKey("even"))
        val evenGroup = grouped.properties["even"] as UDM.Array
        assertEquals(1, evenGroup.elements.size)
        assertEquals(42, (evenGroup.elements[0] as UDM.Scalar).value)
    }

    @Test
    fun testGroupByInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            DataRestructuringFunctions.groupBy(listOf(UDM.Array(emptyList())))
        }
        assertThrows<IllegalArgumentException> {
            DataRestructuringFunctions.groupBy(listOf())
        }
        assertThrows<IllegalArgumentException> {
            DataRestructuringFunctions.groupBy(listOf(UDM.Scalar("not-array"), UDM.Scalar("keyFunction")))
        }
    }

    // ── mapGroups tests ──

    @Test
    fun testMapGroups() {
        val array = UDM.Array(listOf(
            UDM.Object(mapOf("dept" to UDM.Scalar("Eng"), "name" to UDM.Scalar("Alice")), emptyMap()),
            UDM.Object(mapOf("dept" to UDM.Scalar("Sales"), "name" to UDM.Scalar("Bob")), emptyMap()),
            UDM.Object(mapOf("dept" to UDM.Scalar("Eng"), "name" to UDM.Scalar("Carol")), emptyMap())
        ))

        val keySelector = UDM.Scalar("dept")

        val transform = UDM.Lambda { args ->
            val group = args[0] as UDM.Object
            val key = group.properties["key"] ?: UDM.Scalar("null")
            val value = group.properties["value"] as UDM.Array
            UDM.Object(mapOf(
                "department" to key,
                "headcount" to UDM.Scalar(value.elements.size)
            ))
        }

        val result = DataRestructuringFunctions.mapGroups(listOf(array, keySelector, transform))

        assertTrue(result is UDM.Array, "mapGroups should return UDM.Array")
        val resultArray = result as UDM.Array
        assertEquals(2, resultArray.elements.size)

        val engGroup = resultArray.elements.find {
            (it as UDM.Object).properties["department"]?.asString() == "Eng"
        } as UDM.Object
        assertEquals(2, (engGroup.properties["headcount"] as UDM.Scalar).value)

        val salesGroup = resultArray.elements.find {
            (it as UDM.Object).properties["department"]?.asString() == "Sales"
        } as UDM.Object
        assertEquals(1, (salesGroup.properties["headcount"] as UDM.Scalar).value)
    }

    @Test
    fun testMapGroupsWithLambdaKey() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(4)
        ))

        val keySelector = UDM.Lambda { args ->
            val value = (args[0] as UDM.Scalar).value as Number
            if (value.toInt() % 2 == 0) UDM.Scalar("even") else UDM.Scalar("odd")
        }

        val transform = UDM.Lambda { args ->
            val group = args[0] as UDM.Object
            val key = group.properties["key"]!!
            val members = group.properties["value"] as UDM.Array
            UDM.Object(mapOf(
                "type" to key,
                "count" to UDM.Scalar(members.elements.size)
            ))
        }

        val result = DataRestructuringFunctions.mapGroups(listOf(array, keySelector, transform))

        assertTrue(result is UDM.Array)
        assertEquals(2, (result as UDM.Array).elements.size)
    }

    // ── lookupBy tests ──

    @Test
    fun testLookupByMatch() {
        val customers = UDM.Array(listOf(
            UDM.Object(mapOf("id" to UDM.Scalar("C-41"), "name" to UDM.Scalar("Globex Inc")), emptyMap()),
            UDM.Object(mapOf("id" to UDM.Scalar("C-42"), "name" to UDM.Scalar("Acme Corp")), emptyMap()),
            UDM.Object(mapOf("id" to UDM.Scalar("C-43"), "name" to UDM.Scalar("Initech BV")), emptyMap())
        ))

        val keyFn = UDM.Lambda { args ->
            (args[0] as UDM.Object).properties["id"] ?: UDM.Scalar("null")
        }

        val result = DataRestructuringFunctions.lookupBy(listOf(UDM.Scalar("C-42"), customers, keyFn))

        assertTrue(result is UDM.Object, "lookupBy should return matching Object")
        assertEquals("Acme Corp", ((result as UDM.Object).properties["name"] as UDM.Scalar).value)
    }

    @Test
    fun testLookupByNoMatch() {
        val customers = UDM.Array(listOf(
            UDM.Object(mapOf("id" to UDM.Scalar("C-41"), "name" to UDM.Scalar("Globex Inc")), emptyMap())
        ))

        val keyFn = UDM.Lambda { args ->
            (args[0] as UDM.Object).properties["id"] ?: UDM.Scalar("null")
        }

        val result = DataRestructuringFunctions.lookupBy(listOf(UDM.Scalar("C-99"), customers, keyFn))

        assertTrue(result is UDM.Scalar)
        assertNull((result as UDM.Scalar).value)
    }

    @Test
    fun testLookupByFirstMatch() {
        val items = UDM.Array(listOf(
            UDM.Object(mapOf("code" to UDM.Scalar("A"), "version" to UDM.Scalar(1)), emptyMap()),
            UDM.Object(mapOf("code" to UDM.Scalar("A"), "version" to UDM.Scalar(2)), emptyMap()),
            UDM.Object(mapOf("code" to UDM.Scalar("B"), "version" to UDM.Scalar(1)), emptyMap())
        ))

        val keyFn = UDM.Lambda { args ->
            (args[0] as UDM.Object).properties["code"] ?: UDM.Scalar("null")
        }

        val result = DataRestructuringFunctions.lookupBy(listOf(UDM.Scalar("A"), items, keyFn))

        assertTrue(result is UDM.Object)
        assertEquals(1, ((result as UDM.Object).properties["version"] as UDM.Scalar).value)
    }

    @Test
    fun testLookupByEmptyArray() {
        val empty = UDM.Array(emptyList())
        val keyFn = UDM.Lambda { args -> UDM.Scalar("x") }

        val result = DataRestructuringFunctions.lookupBy(listOf(UDM.Scalar("anything"), empty, keyFn))

        assertTrue(result is UDM.Scalar)
        assertNull((result as UDM.Scalar).value)
    }
}
