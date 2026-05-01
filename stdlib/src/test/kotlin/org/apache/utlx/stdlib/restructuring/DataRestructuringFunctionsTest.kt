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

    // ── nestBy tests ──

    @Test
    fun testNestByBasic() {
        // F03: nest order lines under orders
        val orders = UDM.Array(listOf(
            UDM.Object(mapOf("orderId" to UDM.Scalar("A"), "customer" to UDM.Scalar("Alice")), emptyMap()),
            UDM.Object(mapOf("orderId" to UDM.Scalar("B"), "customer" to UDM.Scalar("Bob")), emptyMap())
        ))
        val lines = UDM.Array(listOf(
            UDM.Object(mapOf("orderId" to UDM.Scalar("A"), "product" to UDM.Scalar("Widget"), "qty" to UDM.Scalar(10)), emptyMap()),
            UDM.Object(mapOf("orderId" to UDM.Scalar("A"), "product" to UDM.Scalar("Gadget"), "qty" to UDM.Scalar(5)), emptyMap()),
            UDM.Object(mapOf("orderId" to UDM.Scalar("B"), "product" to UDM.Scalar("Gizmo"), "qty" to UDM.Scalar(3)), emptyMap())
        ))
        val parentKeyFn = UDM.Lambda { args -> (args[0] as UDM.Object).properties["orderId"]!! }
        val childKeyFn = UDM.Lambda { args -> (args[0] as UDM.Object).properties["orderId"]!! }

        val result = DataRestructuringFunctions.nestBy(listOf(orders, lines, parentKeyFn, childKeyFn, UDM.Scalar("lines")))

        assertTrue(result is UDM.Array)
        val resultArr = result as UDM.Array
        assertEquals(2, resultArr.elements.size)

        // Order A should have 2 lines
        val orderA = resultArr.elements[0] as UDM.Object
        assertEquals("Alice", (orderA.properties["customer"] as UDM.Scalar).value)
        val linesA = orderA.properties["lines"] as UDM.Array
        assertEquals(2, linesA.elements.size)

        // Order B should have 1 line
        val orderB = resultArr.elements[1] as UDM.Object
        assertEquals("Bob", (orderB.properties["customer"] as UDM.Scalar).value)
        val linesB = orderB.properties["lines"] as UDM.Array
        assertEquals(1, linesB.elements.size)
        assertEquals("Gizmo", ((linesB.elements[0] as UDM.Object).properties["product"] as UDM.Scalar).value)
    }

    @Test
    fun testNestByNoMatchingChildren() {
        // Parent with no children gets empty array
        val orders = UDM.Array(listOf(
            UDM.Object(mapOf("orderId" to UDM.Scalar("X"), "customer" to UDM.Scalar("Nobody")), emptyMap())
        ))
        val lines = UDM.Array(listOf(
            UDM.Object(mapOf("orderId" to UDM.Scalar("A"), "product" to UDM.Scalar("Widget")), emptyMap())
        ))
        val parentKeyFn = UDM.Lambda { args -> (args[0] as UDM.Object).properties["orderId"]!! }
        val childKeyFn = UDM.Lambda { args -> (args[0] as UDM.Object).properties["orderId"]!! }

        val result = DataRestructuringFunctions.nestBy(listOf(orders, lines, parentKeyFn, childKeyFn, UDM.Scalar("lines")))

        val orderX = (result as UDM.Array).elements[0] as UDM.Object
        val linesX = orderX.properties["lines"] as UDM.Array
        assertEquals(0, linesX.elements.size)  // empty array, not null
    }

    @Test
    fun testNestByEmptyChildren() {
        val orders = UDM.Array(listOf(
            UDM.Object(mapOf("id" to UDM.Scalar("1")), emptyMap())
        ))
        val emptyChildren = UDM.Array(emptyList())
        val pKey = UDM.Lambda { args -> (args[0] as UDM.Object).properties["id"]!! }
        val cKey = UDM.Lambda { args -> UDM.Scalar("x") }

        val result = DataRestructuringFunctions.nestBy(listOf(orders, emptyChildren, pKey, cKey, UDM.Scalar("items")))

        val parent = (result as UDM.Array).elements[0] as UDM.Object
        assertTrue(parent.properties.containsKey("items"))
        assertEquals(0, (parent.properties["items"] as UDM.Array).elements.size)
    }

    @Test
    fun testNestByPreservesParentProperties() {
        // Verify all original parent properties are preserved
        val orders = UDM.Array(listOf(
            UDM.Object(mapOf(
                "orderId" to UDM.Scalar("A"),
                "customer" to UDM.Scalar("Alice"),
                "status" to UDM.Scalar("ACTIVE"),
                "total" to UDM.Scalar(299.99)
            ), emptyMap())
        ))
        val lines = UDM.Array(emptyList())
        val pKey = UDM.Lambda { args -> (args[0] as UDM.Object).properties["orderId"]!! }
        val cKey = UDM.Lambda { args -> UDM.Scalar("x") }

        val result = DataRestructuringFunctions.nestBy(listOf(orders, lines, pKey, cKey, UDM.Scalar("lines")))

        val order = (result as UDM.Array).elements[0] as UDM.Object
        assertEquals("A", (order.properties["orderId"] as UDM.Scalar).value)
        assertEquals("Alice", (order.properties["customer"] as UDM.Scalar).value)
        assertEquals("ACTIVE", (order.properties["status"] as UDM.Scalar).value)
        assertEquals(299.99, (order.properties["total"] as UDM.Scalar).value)
        assertTrue(order.properties.containsKey("lines"))  // new property added
    }

    // ── chunkBy tests ──

    @Test
    fun testChunkByBasic() {
        // F05: chunk segments by header type
        val segments = UDM.Array(listOf(
            UDM.Object(mapOf("type" to UDM.Scalar("HEADER"), "id" to UDM.Scalar("H1")), emptyMap()),
            UDM.Object(mapOf("type" to UDM.Scalar("LINE"), "product" to UDM.Scalar("A")), emptyMap()),
            UDM.Object(mapOf("type" to UDM.Scalar("LINE"), "product" to UDM.Scalar("B")), emptyMap()),
            UDM.Object(mapOf("type" to UDM.Scalar("HEADER"), "id" to UDM.Scalar("H2")), emptyMap()),
            UDM.Object(mapOf("type" to UDM.Scalar("LINE"), "product" to UDM.Scalar("C")), emptyMap())
        ))

        val predicate = UDM.Lambda { args ->
            val obj = args[0] as UDM.Object
            UDM.Scalar((obj.properties["type"] as UDM.Scalar).value == "HEADER")
        }

        val result = DataRestructuringFunctions.chunkBy(listOf(segments, predicate))

        assertTrue(result is UDM.Array)
        val chunks = result as UDM.Array
        assertEquals(2, chunks.elements.size)

        // First chunk: header H1 + 2 lines
        val chunk1 = chunks.elements[0] as UDM.Array
        assertEquals(3, chunk1.elements.size)
        assertEquals("H1", ((chunk1.elements[0] as UDM.Object).properties["id"] as UDM.Scalar).value)

        // Second chunk: header H2 + 1 line
        val chunk2 = chunks.elements[1] as UDM.Array
        assertEquals(2, chunk2.elements.size)
        assertEquals("H2", ((chunk2.elements[0] as UDM.Object).properties["id"] as UDM.Scalar).value)
    }

    @Test
    fun testChunkByEmptyArray() {
        val result = DataRestructuringFunctions.chunkBy(listOf(
            UDM.Array(emptyList()),
            UDM.Lambda { UDM.Scalar(true) }
        ))

        assertTrue(result is UDM.Array)
        assertEquals(0, (result as UDM.Array).elements.size)
    }

    @Test
    fun testChunkByNoPredicateMatch() {
        // No element triggers a new chunk — everything in one chunk
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        val predicate = UDM.Lambda { UDM.Scalar(false) }

        val result = DataRestructuringFunctions.chunkBy(listOf(array, predicate))

        val chunks = result as UDM.Array
        assertEquals(1, chunks.elements.size)
        assertEquals(3, (chunks.elements[0] as UDM.Array).elements.size)
    }

    @Test
    fun testChunkByEveryElementIsChunk() {
        // Every element starts a new chunk
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        val predicate = UDM.Lambda { UDM.Scalar(true) }

        val result = DataRestructuringFunctions.chunkBy(listOf(array, predicate))

        val chunks = result as UDM.Array
        assertEquals(3, chunks.elements.size)
        assertEquals(1, (chunks.elements[0] as UDM.Array).elements.size)
        assertEquals(1, (chunks.elements[1] as UDM.Array).elements.size)
        assertEquals(1, (chunks.elements[2] as UDM.Array).elements.size)
    }

    @Test
    fun testNestByInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            DataRestructuringFunctions.nestBy(listOf(UDM.Array(emptyList())))  // too few args
        }
        assertThrows<IllegalArgumentException> {
            DataRestructuringFunctions.nestBy(listOf(
                UDM.Scalar("not-array"), UDM.Array(emptyList()),
                UDM.Lambda { UDM.Scalar("x") }, UDM.Lambda { UDM.Scalar("x") }, UDM.Scalar("prop")
            ))  // first arg not array
        }
    }

    // ── unnest tests ──

    @Test
    fun testUnnestBasic() {
        // F06: flatten orders with nested lines into flat rows
        val orders = UDM.Array(listOf(
            UDM.Object(mapOf(
                "orderId" to UDM.Scalar("A"),
                "customer" to UDM.Scalar("Alice"),
                "lines" to UDM.Array(listOf(
                    UDM.Object(mapOf("product" to UDM.Scalar("Widget"), "qty" to UDM.Scalar(10)), emptyMap()),
                    UDM.Object(mapOf("product" to UDM.Scalar("Gadget"), "qty" to UDM.Scalar(5)), emptyMap())
                ))
            ), emptyMap()),
            UDM.Object(mapOf(
                "orderId" to UDM.Scalar("B"),
                "customer" to UDM.Scalar("Bob"),
                "lines" to UDM.Array(listOf(
                    UDM.Object(mapOf("product" to UDM.Scalar("Gizmo"), "qty" to UDM.Scalar(3)), emptyMap())
                ))
            ), emptyMap())
        ))

        val result = DataRestructuringFunctions.unnest(listOf(orders, UDM.Scalar("lines")))

        assertTrue(result is UDM.Array)
        val flat = result as UDM.Array
        assertEquals(3, flat.elements.size)  // 2 + 1 = 3 flat rows

        // First row: order A + Widget
        val row1 = flat.elements[0] as UDM.Object
        assertEquals("A", (row1.properties["orderId"] as UDM.Scalar).value)
        assertEquals("Alice", (row1.properties["customer"] as UDM.Scalar).value)
        assertEquals("Widget", (row1.properties["product"] as UDM.Scalar).value)
        assertEquals(10, (row1.properties["qty"] as UDM.Scalar).value)
        assertNull(row1.properties["lines"])  // child property removed

        // Third row: order B + Gizmo
        val row3 = flat.elements[2] as UDM.Object
        assertEquals("B", (row3.properties["orderId"] as UDM.Scalar).value)
        assertEquals("Gizmo", (row3.properties["product"] as UDM.Scalar).value)
    }

    @Test
    fun testUnnestEmptyChildren() {
        // Parent with empty lines array — excluded from output
        val orders = UDM.Array(listOf(
            UDM.Object(mapOf(
                "orderId" to UDM.Scalar("A"),
                "lines" to UDM.Array(emptyList())
            ), emptyMap())
        ))

        val result = DataRestructuringFunctions.unnest(listOf(orders, UDM.Scalar("lines")))

        assertEquals(0, (result as UDM.Array).elements.size)
    }

    @Test
    fun testUnnestMissingProperty() {
        // Parent without the named property — excluded from output
        val orders = UDM.Array(listOf(
            UDM.Object(mapOf("orderId" to UDM.Scalar("A")), emptyMap())
        ))

        val result = DataRestructuringFunctions.unnest(listOf(orders, UDM.Scalar("lines")))

        assertEquals(0, (result as UDM.Array).elements.size)
    }

    @Test
    fun testUnnestChildOverridesParent() {
        // Child field "id" overrides parent field "id"
        val data = UDM.Array(listOf(
            UDM.Object(mapOf(
                "id" to UDM.Scalar("PARENT-1"),
                "name" to UDM.Scalar("ParentName"),
                "children" to UDM.Array(listOf(
                    UDM.Object(mapOf("id" to UDM.Scalar("CHILD-1"), "value" to UDM.Scalar(42)), emptyMap())
                ))
            ), emptyMap())
        ))

        val result = DataRestructuringFunctions.unnest(listOf(data, UDM.Scalar("children")))

        val row = (result as UDM.Array).elements[0] as UDM.Object
        assertEquals("CHILD-1", (row.properties["id"] as UDM.Scalar).value)  // child wins
        assertEquals("ParentName", (row.properties["name"] as UDM.Scalar).value)  // parent preserved
        assertEquals(42, (row.properties["value"] as UDM.Scalar).value)  // child field
    }
}
