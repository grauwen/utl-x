// modules/daemon/src/test/kotlin/org/apache/utlx/daemon/hover/HoverServiceTest.kt
package org.apache.utlx.daemon.hover

import org.apache.utlx.analysis.types.*
import org.apache.utlx.daemon.completion.Position
import org.apache.utlx.daemon.completion.TextDocumentIdentifier
import org.apache.utlx.daemon.state.StateManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for HoverService
 *
 * Tests hover information extraction and formatting
 */
class HoverServiceTest {

    @Test
    fun `test getHover returns null for non-existent document`() {
        val stateManager = StateManager()
        val service = HoverService(stateManager)

        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///unknown.utlx"),
            position = Position(0, 10)
        )

        val result = service.getHover(params)

        assertNull(result)
    }

    @Test
    fun `test getHover returns null when no type environment`() {
        val stateManager = StateManager()
        val service = HoverService(stateManager)

        // Open document without type environment
        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input.Order",
            version = 1,
            languageId = "utlx"
        )

        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 15)
        )

        val result = service.getHover(params)

        assertNull(result)
    }

    @Test
    fun `test getHover returns null for non-path position`() {
        val stateManager = StateManager()
        val service = HoverService(stateManager)

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER))
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)

        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = 123",
            version = 1,
            languageId = "utlx"
        )
        stateManager.setTypeEnvironment("file:///test.utlx", typeEnv)

        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 11) // On "123"
        )

        val result = service.getHover(params)

        assertNull(result)
    }

    @Test
    fun `test getHover for simple variable`() {
        val stateManager = StateManager()
        val service = HoverService(stateManager)

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

        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input",
            version = 1,
            languageId = "utlx"
        )
        stateManager.setTypeEnvironment("file:///test.utlx", typeEnv)

        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 11) // On "input"
        )

        val result = service.getHover(params)

        assertNotNull(result)
        assertEquals(MarkupKind.MARKDOWN, result!!.contents.kind)
        assertTrue(result.contents.value.contains("input"))
        assertTrue(result.contents.value.contains("Object"))
    }

    @Test
    fun `test getHover for object property`() {
        val stateManager = StateManager()
        val service = HoverService(stateManager)

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

        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input.Order",
            version = 1,
            languageId = "utlx"
        )
        stateManager.setTypeEnvironment("file:///test.utlx", typeEnv)

        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 18) // On "Order"
        )

        val result = service.getHover(params)

        assertNotNull(result)
        assertEquals(MarkupKind.MARKDOWN, result!!.contents.kind)
        assertTrue(result.contents.value.contains("input.Order"))
        assertTrue(result.contents.value.contains("Object"))
        assertTrue(result.contents.value.contains("2 properties"))
    }

    @Test
    fun `test getHover for nested property`() {
        val stateManager = StateManager()
        val service = HoverService(stateManager)

        val addressType = TypeDefinition.Object(
            properties = mapOf(
                "Street" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "City" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
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
                "Customer" to PropertyType(customerType)
            )
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(orderType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)

        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input.Order.Customer.Address",
            version = 1,
            languageId = "utlx"
        )
        stateManager.setTypeEnvironment("file:///test.utlx", typeEnv)

        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 35) // On "Address"
        )

        val result = service.getHover(params)

        assertNotNull(result)
        assertTrue(result!!.contents.value.contains("input.Order.Customer.Address"))
        assertTrue(result.contents.value.contains("Object"))
    }

    @Test
    fun `test getHover for scalar property shows type`() {
        val stateManager = StateManager()
        val service = HoverService(stateManager)

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

        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input.Order.CustomerName",
            version = 1,
            languageId = "utlx"
        )
        stateManager.setTypeEnvironment("file:///test.utlx", typeEnv)

        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 30) // On "CustomerName"
        )

        val result = service.getHover(params)

        assertNotNull(result)
        assertTrue(result!!.contents.value.contains("input.Order.CustomerName"))
        assertTrue(result.contents.value.contains("String"))
        assertTrue(result.contents.value.contains("Scalar type"))
    }

    @Test
    fun `test getHover for array property`() {
        val stateManager = StateManager()
        val service = HoverService(stateManager)

        val itemType = TypeDefinition.Object(
            properties = mapOf(
                "ProductID" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                "Price" to PropertyType(TypeDefinition.Scalar(ScalarKind.NUMBER))
            )
        )

        val itemsArray = TypeDefinition.Array(
            elementType = itemType,
            minItems = 1,
            maxItems = 100
        )

        val orderType = TypeDefinition.Object(
            properties = mapOf(
                "Items" to PropertyType(itemsArray)
            )
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(orderType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)

        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input.Order.Items",
            version = 1,
            languageId = "utlx"
        )
        stateManager.setTypeEnvironment("file:///test.utlx", typeEnv)

        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 23) // On "Items"
        )

        val result = service.getHover(params)

        assertNotNull(result)
        assertTrue(result!!.contents.value.contains("input.Order.Items"))
        assertTrue(result.contents.value.contains("Array"))
        assertTrue(result.contents.value.contains("Element type"))
        assertTrue(result.contents.value.contains("Min items"))
        assertTrue(result.contents.value.contains("1"))
        assertTrue(result.contents.value.contains("Max items"))
        assertTrue(result.contents.value.contains("100"))
    }

    @Test
    fun `test getHover for union type`() {
        val stateManager = StateManager()
        val service = HoverService(stateManager)

        val stringOrNumber = TypeDefinition.Union(
            types = listOf(
                TypeDefinition.Scalar(ScalarKind.STRING),
                TypeDefinition.Scalar(ScalarKind.NUMBER)
            )
        )

        val orderType = TypeDefinition.Object(
            properties = mapOf(
                "Reference" to PropertyType(stringOrNumber)
            )
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(orderType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)

        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input.Order.Reference",
            version = 1,
            languageId = "utlx"
        )
        stateManager.setTypeEnvironment("file:///test.utlx", typeEnv)

        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 27) // On "Reference"
        )

        val result = service.getHover(params)

        assertNotNull(result)
        assertTrue(result!!.contents.value.contains("Union type"))
        assertTrue(result.contents.value.contains("String"))
        assertTrue(result.contents.value.contains("Number"))
    }

    @Test
    fun `test getHover includes range information`() {
        val stateManager = StateManager()
        val service = HoverService(stateManager)

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER))
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)

        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input.Order",
            version = 1,
            languageId = "utlx"
        )
        stateManager.setTypeEnvironment("file:///test.utlx", typeEnv)

        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 15) // In "input.Order"
        )

        val result = service.getHover(params)

        assertNotNull(result)
        assertNotNull(result!!.range)
        assertEquals(0, result.range!!.start.line)
        assertEquals(0, result.range!!.end.line)
        assertTrue(result.range!!.start.character >= 9)
        assertTrue(result.range!!.end.character <= 20)
    }

    @Test
    fun `test getHover shows required fields for object`() {
        val stateManager = StateManager()
        val service = HoverService(stateManager)

        val orderType = TypeDefinition.Object(
            properties = mapOf(
                "OrderID" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                "CustomerName" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "OptionalNotes" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            ),
            required = setOf("OrderID", "CustomerName")
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(orderType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)

        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input.Order",
            version = 1,
            languageId = "utlx"
        )
        stateManager.setTypeEnvironment("file:///test.utlx", typeEnv)

        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 18) // On "Order"
        )

        val result = service.getHover(params)

        assertNotNull(result)
        assertTrue(result!!.contents.value.contains("Required fields"))
        assertTrue(result.contents.value.contains("OrderID"))
        assertTrue(result.contents.value.contains("CustomerName"))
        assertTrue(result.contents.value.contains("(required)"))
    }

    @Test
    fun `test getHover shows constraints for scalar`() {
        val stateManager = StateManager()
        val service = HoverService(stateManager)

        val emailType = TypeDefinition.Scalar(
            kind = ScalarKind.STRING,
            constraints = listOf(
                Constraint.Pattern("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"),
                Constraint.MaxLength(255)
            )
        )

        val orderType = TypeDefinition.Object(
            properties = mapOf(
                "CustomerEmail" to PropertyType(emailType)
            )
        )

        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "Order" to PropertyType(orderType)
            )
        )

        val typeEnv = TypeContextBuilder.standard(inputType)

        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input.Order.CustomerEmail",
            version = 1,
            languageId = "utlx"
        )
        stateManager.setTypeEnvironment("file:///test.utlx", typeEnv)

        val params = HoverParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 30) // On "CustomerEmail"
        )

        val result = service.getHover(params)

        assertNotNull(result)
        assertTrue(result!!.contents.value.contains("Constraints"))
        assertTrue(result.contents.value.contains("Pattern"))
        assertTrue(result.contents.value.contains("MaxLength"))
    }
}
