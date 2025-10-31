// modules/daemon/src/test/kotlin/org/apache/utlx/daemon/completion/PathCompleterTest.kt
package org.apache.utlx.daemon.completion

import org.apache.utlx.analysis.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for PathCompleter
 *
 * Tests type-aware path completion logic
 */
class PathCompleterTest {

    @Test
    fun `test complete empty path returns input suggestion`() {
        val typeEnv = TypeContextBuilder.empty()
        val completer = PathCompleter(typeEnv)

        val items = completer.complete("")

        assertEquals(1, items.size)
        assertEquals("input", items[0].label)
        assertEquals(CompletionItemKind.VARIABLE, items[0].kind)
    }

    @Test
    fun `test complete simple object properties`() {
        val orderType = TypeDefinition.Object(
            properties = mapOf(
                "OrderID" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                "CustomerName" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "Total" to PropertyType(TypeDefinition.Scalar(ScalarKind.NUMBER))
            )
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(orderType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)
        val completer = PathCompleter(typeEnv)

        val items = completer.complete("input.Order.")

        assertEquals(3, items.size)

        val labels = items.map { it.label }.toSet()
        assertTrue(labels.contains("OrderID"))
        assertTrue(labels.contains("CustomerName"))
        assertTrue(labels.contains("Total"))
    }

    @Test
    fun `test complete with partial match filters results`() {
        val orderType = TypeDefinition.Object(
            properties = mapOf(
                "OrderID" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                "OrderDate" to PropertyType(TypeDefinition.Scalar(ScalarKind.DATE)),
                "CustomerName" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            )
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(orderType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)
        val completer = PathCompleter(typeEnv)

        val items = completer.complete("input.Order.Ord")

        assertEquals(2, items.size)

        val labels = items.map { it.label }.toSet()
        assertTrue(labels.contains("OrderID"))
        assertTrue(labels.contains("OrderDate"))
        assertFalse(labels.contains("CustomerName"))
    }

    @Test
    fun `test complete case insensitive filtering`() {
        val orderType = TypeDefinition.Object(
            properties = mapOf(
                "OrderID" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                "orderDate" to PropertyType(TypeDefinition.Scalar(ScalarKind.DATE))
            )
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(orderType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)
        val completer = PathCompleter(typeEnv)

        val items = completer.complete("input.Order.order")

        assertEquals(2, items.size) // Both should match case-insensitively
    }

    @Test
    fun `test complete nested object properties`() {
        val addressType = TypeDefinition.Object(
            properties = mapOf(
                "Street" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "City" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "ZipCode" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            )
        )

        val customerType = TypeDefinition.Object(
            properties = mapOf(
                "Name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "Address" to PropertyType(addressType)
            )
        )

        val orderType = TypeDefinition.Object(
            properties = mapOf(
                "OrderID" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                "Customer" to PropertyType(customerType)
            )
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(orderType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)
        val completer = PathCompleter(typeEnv)

        val items = completer.complete("input.Order.Customer.Address.")

        assertEquals(3, items.size)

        val labels = items.map { it.label }.toSet()
        assertTrue(labels.contains("Street"))
        assertTrue(labels.contains("City"))
        assertTrue(labels.contains("ZipCode"))
    }

    @Test
    fun `test complete array element properties`() {
        val itemType = TypeDefinition.Object(
            properties = mapOf(
                "ProductID" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                "Name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "Price" to PropertyType(TypeDefinition.Scalar(ScalarKind.NUMBER))
            )
        )

        val itemsArray = TypeDefinition.Array(elementType = itemType)

        val orderType = TypeDefinition.Object(
            properties = mapOf(
                "OrderID" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                "Items" to PropertyType(itemsArray)
            )
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(orderType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)
        val completer = PathCompleter(typeEnv)

        // Completing properties of array element type
        val items = completer.complete("input.Order.Items.")

        // Should suggest array access operators AND element properties
        assertTrue(items.size >= 3)

        val labels = items.map { it.label }.toSet()
        assertTrue(labels.contains("ProductID"))
        assertTrue(labels.contains("Name"))
        assertTrue(labels.contains("Price"))
    }

    @Test
    fun `test complete returns empty for invalid path`() {
        val orderType = TypeDefinition.Object(
            properties = mapOf(
                "OrderID" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER))
            )
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(orderType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)
        val completer = PathCompleter(typeEnv)

        // NonExistent property
        val items = completer.complete("input.Order.NonExistent.")

        assertTrue(items.isEmpty())
    }

    @Test
    fun `test complete union type merges suggestions`() {
        val addressType1 = TypeDefinition.Object(
            properties = mapOf(
                "Street" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "City" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            )
        )

        val addressType2 = TypeDefinition.Object(
            properties = mapOf(
                "Street" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "PostalCode" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            )
        )

        val unionAddress = TypeDefinition.Union(types = listOf(addressType1, addressType2))

        val customerType = TypeDefinition.Object(
            properties = mapOf(
                "Name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "Address" to PropertyType(unionAddress)
            )
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Customer" to PropertyType(customerType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)
        val completer = PathCompleter(typeEnv)

        val items = completer.complete("input.Customer.Address.")

        // Should include properties from both union members
        val labels = items.map { it.label }.toSet()
        assertTrue(labels.contains("Street")) // Common to both
        assertTrue(labels.contains("City"))   // From type1
        assertTrue(labels.contains("PostalCode")) // From type2
    }

    @Test
    fun `test completion item has correct metadata`() {
        val orderType = TypeDefinition.Object(
            properties = mapOf(
                "OrderID" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                "Total" to PropertyType(TypeDefinition.Scalar(ScalarKind.NUMBER))
            )
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(orderType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)
        val completer = PathCompleter(typeEnv)

        val items = completer.complete("input.Order.")

        val orderIdItem = items.find { it.label == "OrderID" }
        assertNotNull(orderIdItem)
        assertEquals(CompletionItemKind.PROPERTY, orderIdItem!!.kind)
        assertEquals("Integer", orderIdItem.detail)
        assertNotNull(orderIdItem.documentation)
        assertTrue(orderIdItem.documentation!!.contains("OrderID"))
        assertTrue(orderIdItem.documentation!!.contains("Integer"))
    }

    @Test
    fun `test completion for root-level input`() {
        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                "Customer" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)
        val completer = PathCompleter(typeEnv)

        val items = completer.complete("input.")

        assertEquals(2, items.size)

        val labels = items.map { it.label }.toSet()
        assertTrue(labels.contains("Order"))
        assertTrue(labels.contains("Customer"))
    }

    @Test
    fun `test completion returns appropriate kind for different types`() {
        val nestedObject = TypeDefinition.Object(
            properties = mapOf(
                "Field" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            )
        )

        val arrayType = TypeDefinition.Array(
            elementType = TypeDefinition.Scalar(ScalarKind.INTEGER)
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "StringProp" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "ObjectProp" to PropertyType(nestedObject),
                "ArrayProp" to PropertyType(arrayType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)
        val completer = PathCompleter(typeEnv)

        val items = completer.complete("input.")

        val stringItem = items.find { it.label == "StringProp" }
        val objectItem = items.find { it.label == "ObjectProp" }
        val arrayItem = items.find { it.label == "ArrayProp" }

        assertEquals(CompletionItemKind.PROPERTY, stringItem?.kind)
        assertEquals(CompletionItemKind.CLASS, objectItem?.kind)
        assertEquals(CompletionItemKind.ARRAY, arrayItem?.kind)
    }
}
