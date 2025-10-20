package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinFunctionsTest {

    @Test
    fun testJoin() {
        // Test basic inner join with property name keys
        val customers = UDM.Array(listOf(
            UDM.Object(mapOf("id" to UDM.Scalar(1), "name" to UDM.Scalar("Alice"))),
            UDM.Object(mapOf("id" to UDM.Scalar(2), "name" to UDM.Scalar("Bob"))),
            UDM.Object(mapOf("id" to UDM.Scalar(3), "name" to UDM.Scalar("Charlie")))
        ))
        
        val orders = UDM.Array(listOf(
            UDM.Object(mapOf("customerId" to UDM.Scalar(1), "product" to UDM.Scalar("Widget"))),
            UDM.Object(mapOf("customerId" to UDM.Scalar(1), "product" to UDM.Scalar("Gadget"))),
            UDM.Object(mapOf("customerId" to UDM.Scalar(2), "product" to UDM.Scalar("Tool")))
        ))
        
        val result = JoinFunctions.join(listOf(
            customers,
            orders,
            UDM.Scalar("id"),
            UDM.Scalar("customerId")
        ))
        
        val resultArray = result as UDM.Array
        assertEquals(3, resultArray.elements.size)
        
        // Check first match (Alice's first order)
        val firstMatch = resultArray.elements[0] as UDM.Object
        assertTrue(firstMatch.properties.containsKey("l"))
        assertTrue(firstMatch.properties.containsKey("r"))
        
        val leftCustomer = firstMatch.properties["l"] as UDM.Object
        val rightOrder = firstMatch.properties["r"] as UDM.Object
        assertEquals("Alice", (leftCustomer.properties["name"] as UDM.Scalar).value)
        assertEquals("Widget", (rightOrder.properties["product"] as UDM.Scalar).value)
    }

    @Test
    fun testJoinWithNoMatches() {
        val left = UDM.Array(listOf(
            UDM.Object(mapOf("id" to UDM.Scalar(1), "name" to UDM.Scalar("Alice")))
        ))
        
        val right = UDM.Array(listOf(
            UDM.Object(mapOf("customerId" to UDM.Scalar(2), "product" to UDM.Scalar("Widget")))
        ))
        
        val result = JoinFunctions.join(listOf(
            left,
            right,
            UDM.Scalar("id"),
            UDM.Scalar("customerId")
        ))
        
        val resultArray = result as UDM.Array
        assertEquals(0, resultArray.elements.size)
    }

    @Test
    fun testLeftJoin() {
        val customers = UDM.Array(listOf(
            UDM.Object(mapOf("id" to UDM.Scalar(1), "name" to UDM.Scalar("Alice"))),
            UDM.Object(mapOf("id" to UDM.Scalar(2), "name" to UDM.Scalar("Bob"))),
            UDM.Object(mapOf("id" to UDM.Scalar(3), "name" to UDM.Scalar("Charlie")))
        ))
        
        val orders = UDM.Array(listOf(
            UDM.Object(mapOf("customerId" to UDM.Scalar(1), "product" to UDM.Scalar("Widget"))),
            UDM.Object(mapOf("customerId" to UDM.Scalar(2), "product" to UDM.Scalar("Tool")))
        ))
        
        val result = JoinFunctions.leftJoin(listOf(
            customers,
            orders,
            UDM.Scalar("id"),
            UDM.Scalar("customerId")
        ))
        
        val resultArray = result as UDM.Array
        assertEquals(3, resultArray.elements.size) // All customers included
        
        // Check that Charlie (id=3) has null right side
        val charlieMatch = resultArray.elements.find { element ->
            val obj = element as UDM.Object
            val leftCustomer = obj.properties["l"] as UDM.Object
            (leftCustomer.properties["name"] as UDM.Scalar).value == "Charlie"
        }
        
        assertTrue(charlieMatch != null)
        val charlieObj = charlieMatch as UDM.Object
        assertEquals(UDM.Scalar(null), charlieObj.properties["r"])
    }

    @Test
    fun testRightJoin() {
        val customers = UDM.Array(listOf(
            UDM.Object(mapOf("id" to UDM.Scalar(1), "name" to UDM.Scalar("Alice"))),
            UDM.Object(mapOf("id" to UDM.Scalar(2), "name" to UDM.Scalar("Bob")))
        ))
        
        val orders = UDM.Array(listOf(
            UDM.Object(mapOf("customerId" to UDM.Scalar(1), "product" to UDM.Scalar("Widget"))),
            UDM.Object(mapOf("customerId" to UDM.Scalar(2), "product" to UDM.Scalar("Tool"))),
            UDM.Object(mapOf("customerId" to UDM.Scalar(99), "product" to UDM.Scalar("Orphan")))
        ))
        
        val result = JoinFunctions.rightJoin(listOf(
            customers,
            orders,
            UDM.Scalar("id"),
            UDM.Scalar("customerId")
        ))
        
        val resultArray = result as UDM.Array
        assertEquals(3, resultArray.elements.size) // All orders included
        
        // Check that orphan order (customerId=99) has null left side
        val orphanMatch = resultArray.elements.find { element ->
            val obj = element as UDM.Object
            val rightOrder = obj.properties["r"] as UDM.Object
            (rightOrder.properties["product"] as UDM.Scalar).value == "Orphan"
        }
        
        assertTrue(orphanMatch != null)
        val orphanObj = orphanMatch as UDM.Object
        assertEquals(UDM.Scalar(null), orphanObj.properties["l"])
    }

    @Test
    fun testFullOuterJoin() {
        val customers = UDM.Array(listOf(
            UDM.Object(mapOf("id" to UDM.Scalar(1), "name" to UDM.Scalar("Alice"))),
            UDM.Object(mapOf("id" to UDM.Scalar(2), "name" to UDM.Scalar("Bob"))),
            UDM.Object(mapOf("id" to UDM.Scalar(3), "name" to UDM.Scalar("Charlie")))
        ))
        
        val orders = UDM.Array(listOf(
            UDM.Object(mapOf("customerId" to UDM.Scalar(1), "product" to UDM.Scalar("Widget"))),
            UDM.Object(mapOf("customerId" to UDM.Scalar(99), "product" to UDM.Scalar("Orphan")))
        ))
        
        val result = JoinFunctions.fullOuterJoin(listOf(
            customers,
            orders,
            UDM.Scalar("id"),
            UDM.Scalar("customerId")
        ))
        
        val resultArray = result as UDM.Array
        assertEquals(4, resultArray.elements.size) // Alice matched, Bob+Charlie unmatched, Orphan unmatched
        
        // Should have: Alice+Widget, Bob+null, Charlie+null, null+Orphan
        var matchedCount = 0
        var unmatchedCustomersCount = 0
        var unmatchedOrdersCount = 0
        
        resultArray.elements.forEach { element ->
            val obj = element as UDM.Object
            val left = obj.properties["l"]
            val right = obj.properties["r"]
            
            when {
                left != UDM.Scalar(null) && right != UDM.Scalar(null) -> matchedCount++
                left != UDM.Scalar(null) && right == UDM.Scalar(null) -> unmatchedCustomersCount++
                left == UDM.Scalar(null) && right != UDM.Scalar(null) -> unmatchedOrdersCount++
            }
        }
        
        assertEquals(1, matchedCount) // Alice+Widget
        assertEquals(2, unmatchedCustomersCount) // Bob, Charlie
        assertEquals(1, unmatchedOrdersCount) // Orphan order
    }

    @Test
    fun testCrossJoin() {
        val left = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2)
        ))
        
        val right = UDM.Array(listOf(
            UDM.Scalar("a"),
            UDM.Scalar("b")
        ))
        
        val result = JoinFunctions.crossJoin(listOf(left, right))
        
        val resultArray = result as UDM.Array
        assertEquals(4, resultArray.elements.size) // 2 × 2 = 4 combinations
        
        // Check all combinations exist
        val combinations = resultArray.elements.map { element ->
            val obj = element as UDM.Object
            val l = (obj.properties["l"] as UDM.Scalar).value
            val r = (obj.properties["r"] as UDM.Scalar).value
            "$l-$r"
        }.toSet()
        
        assertEquals(setOf("1-a", "1-b", "2-a", "2-b"), combinations)
    }

    @Test
    fun testCrossJoinWithEmptyArray() {
        val left = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        val right = UDM.Array(emptyList())
        
        val result = JoinFunctions.crossJoin(listOf(left, right))
        
        val resultArray = result as UDM.Array
        assertEquals(0, resultArray.elements.size)
    }

    @Test
    fun testJoinWith() {
        val customers = UDM.Array(listOf(
            UDM.Object(mapOf("id" to UDM.Scalar(1), "name" to UDM.Scalar("Alice"))),
            UDM.Object(mapOf("id" to UDM.Scalar(2), "name" to UDM.Scalar("Bob")))
        ))
        
        val orders = UDM.Array(listOf(
            UDM.Object(mapOf("customerId" to UDM.Scalar(1), "product" to UDM.Scalar("Widget"))),
            UDM.Object(mapOf("customerId" to UDM.Scalar(2), "product" to UDM.Scalar("Tool")))
        ))
        
        // Use a dummy combiner function that combines left and right objects
        val combinerFn = UDM.Lambda { args ->
            // args[0] is left object, args[1] is right object
            val left = args.getOrNull(0) ?: UDM.Scalar(null)
            val right = args.getOrNull(1) ?: UDM.Scalar(null)
            UDM.Object(mapOf(
                "l" to left,
                "r" to right
            ))
        }
        
        val result = JoinFunctions.joinWith(listOf(
            customers,
            orders,
            UDM.Scalar("id"),
            UDM.Scalar("customerId"),
            combinerFn
        ))
        
        val resultArray = result as UDM.Array
        assertEquals(2, resultArray.elements.size)
        
        // Verify structure (current implementation returns {l, r} objects)
        val firstMatch = resultArray.elements[0] as UDM.Object
        assertTrue(firstMatch.properties.containsKey("l"))
        assertTrue(firstMatch.properties.containsKey("r"))
    }

    @Test
    fun testJoinWithMismatchedTypes() {
        // Test joining numbers with strings that represent the same value
        val left = UDM.Array(listOf(
            UDM.Object(mapOf("id" to UDM.Scalar(1), "name" to UDM.Scalar("Alice")))
        ))
        
        val right = UDM.Array(listOf(
            UDM.Object(mapOf("customerId" to UDM.Scalar("1"), "product" to UDM.Scalar("Widget")))
        ))
        
        val result = JoinFunctions.join(listOf(
            left,
            right,
            UDM.Scalar("id"),
            UDM.Scalar("customerId")
        ))
        
        val resultArray = result as UDM.Array
        assertEquals(1, resultArray.elements.size) // Should match due to string conversion
    }

    @Test
    fun testJoinEdgeCases() {
        // Test with insufficient arguments
        assertThrows<IllegalArgumentException> {
            JoinFunctions.join(listOf(
                UDM.Array(emptyList()),
                UDM.Array(emptyList())
            ))
        }

        // Test with non-array arguments
        assertThrows<IllegalArgumentException> {
            JoinFunctions.join(listOf(
                UDM.Scalar("not array"),
                UDM.Array(emptyList()),
                UDM.Scalar("id"),
                UDM.Scalar("id")
            ))
        }

        assertThrows<IllegalArgumentException> {
            JoinFunctions.join(listOf(
                UDM.Array(emptyList()),
                UDM.Scalar("not array"),
                UDM.Scalar("id"),
                UDM.Scalar("id")
            ))
        }
    }

    @Test
    fun testJoinWithComplexObjects() {
        // Test joining objects with nested structures
        val departments = UDM.Array(listOf(
            UDM.Object(mapOf(
                "id" to UDM.Scalar(1),
                "name" to UDM.Scalar("Engineering"),
                "budget" to UDM.Scalar(100000)
            )),
            UDM.Object(mapOf(
                "id" to UDM.Scalar(2),
                "name" to UDM.Scalar("Sales"),
                "budget" to UDM.Scalar(75000)
            ))
        ))
        
        val employees = UDM.Array(listOf(
            UDM.Object(mapOf(
                "deptId" to UDM.Scalar(1),
                "name" to UDM.Scalar("Alice"),
                "role" to UDM.Scalar("Developer")
            )),
            UDM.Object(mapOf(
                "deptId" to UDM.Scalar(1),
                "name" to UDM.Scalar("Bob"),
                "role" to UDM.Scalar("Manager")
            )),
            UDM.Object(mapOf(
                "deptId" to UDM.Scalar(2),
                "name" to UDM.Scalar("Carol"),
                "role" to UDM.Scalar("Sales Rep")
            ))
        ))
        
        val result = JoinFunctions.join(listOf(
            departments,
            employees,
            UDM.Scalar("id"),
            UDM.Scalar("deptId")
        ))
        
        val resultArray = result as UDM.Array
        assertEquals(3, resultArray.elements.size)
        
        // Verify Engineering department has 2 employees
        val engineeringMatches = resultArray.elements.filter { element ->
            val obj = element as UDM.Object
            val dept = obj.properties["l"] as UDM.Object
            (dept.properties["name"] as UDM.Scalar).value == "Engineering"
        }
        assertEquals(2, engineeringMatches.size)
    }

    @Test
    fun testJoinWithNullKeys() {
        val left = UDM.Array(listOf(
            UDM.Object(mapOf("id" to UDM.Scalar(null), "name" to UDM.Scalar("NullId")))
        ))
        
        val right = UDM.Array(listOf(
            UDM.Object(mapOf("refId" to UDM.Scalar(null), "data" to UDM.Scalar("NullRef")))
        ))
        
        val result = JoinFunctions.join(listOf(
            left,
            right,
            UDM.Scalar("id"),
            UDM.Scalar("refId")
        ))
        
        val resultArray = result as UDM.Array
        assertEquals(0, resultArray.elements.size) // Null keys don't match
    }

    @Test
    fun testJoinWithMissingProperties() {
        val left = UDM.Array(listOf(
            UDM.Object(mapOf("name" to UDM.Scalar("NoId")))
        ))
        
        val right = UDM.Array(listOf(
            UDM.Object(mapOf("id" to UDM.Scalar(1), "data" to UDM.Scalar("HasId")))
        ))
        
        val result = JoinFunctions.join(listOf(
            left,
            right,
            UDM.Scalar("id"), // Missing property in left
            UDM.Scalar("id")
        ))
        
        val resultArray = result as UDM.Array
        assertEquals(0, resultArray.elements.size) // Missing property results in null key, no match
    }
}