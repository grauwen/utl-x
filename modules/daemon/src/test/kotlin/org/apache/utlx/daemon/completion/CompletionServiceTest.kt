// modules/daemon/src/test/kotlin/org/apache/utlx/daemon/completion/CompletionServiceTest.kt
package org.apache.utlx.daemon.completion

import org.apache.utlx.analysis.types.*
import org.apache.utlx.daemon.state.StateManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for CompletionService
 *
 * Tests path extraction and completion coordination
 */
class CompletionServiceTest {

    @Test
    fun `test extractPathAtPosition with simple path`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        val text = "output = input.Order"
        val position = Position(line = 0, character = 20) // After "input.Order"

        val path = service.extractPathAtPosition(text, position)

        assertEquals("input.Order", path)
    }

    @Test
    fun `test extractPathAtPosition with partial word`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        val text = "output = input.Ord"
        val position = Position(line = 0, character = 18) // After "input.Ord"

        val path = service.extractPathAtPosition(text, position)

        assertEquals("input.Ord", path)
    }

    @Test
    fun `test extractPathAtPosition after dot`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        val text = "output = input.Order."
        val position = Position(line = 0, character = 21) // After "input.Order."

        val path = service.extractPathAtPosition(text, position)

        assertEquals("input.Order.", path)
    }

    @Test
    fun `test extractPathAtPosition with nested path`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        val text = "output = input.Order.Items.Item"
        val position = Position(line = 0, character = 31) // After "input.Order.Items.Item"

        val path = service.extractPathAtPosition(text, position)

        assertEquals("input.Order.Items.Item", path)
    }

    @Test
    fun `test extractPathAtPosition mid-expression`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        val text = "output = sum(input.Order.Items.Item.price)"
        val position = Position(line = 0, character = 41) // After "price"

        val path = service.extractPathAtPosition(text, position)

        assertEquals("input.Order.Items.Item.price", path)
    }

    @Test
    fun `test extractPathAtPosition with attribute`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        val text = "output = input.Order.Items.@count"
        val position = Position(line = 0, character = 33) // After "@count"

        val path = service.extractPathAtPosition(text, position)

        assertEquals("input.Order.Items.@count", path)
    }

    @Test
    fun `test extractPathAtPosition at start of line`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        val text = "input"
        val position = Position(line = 0, character = 5) // After "input"

        val path = service.extractPathAtPosition(text, position)

        assertEquals("input", path)
    }

    @Test
    fun `test extractPathAtPosition returns null for non-path context`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        val text = "output = 123"
        val position = Position(line = 0, character = 12) // After "123"

        val path = service.extractPathAtPosition(text, position)

        assertNull(path)
    }

    @Test
    fun `test extractPathAtPosition with whitespace`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        val text = "output =   input.Order"
        val position = Position(line = 0, character = 22) // After "input.Order"

        val path = service.extractPathAtPosition(text, position)

        assertEquals("input.Order", path)
    }

    @Test
    fun `test getCompletions returns empty for non-existent document`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        val params = CompletionParams(
            textDocument = TextDocumentIdentifier("file:///unknown.utlx"),
            position = Position(0, 10)
        )

        val result = service.getCompletions(params)

        assertFalse(result.isIncomplete)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `test getCompletions with valid document but no type environment`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        // Open document without setting up type environment
        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input.Order",
            version = 1,
            languageId = "utlx"
        )

        val params = CompletionParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 20)
        )

        val result = service.getCompletions(params)

        // Should return empty list when no type environment
        assertFalse(result.isIncomplete)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `test getCompletions with simple object type`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        // Create a simple type environment
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

        // Open document and set type environment
        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input.Order.",
            version = 1,
            languageId = "utlx"
        )
        stateManager.setTypeEnvironment("file:///test.utlx", typeEnv)

        val params = CompletionParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 21) // After "input.Order."
        )

        val result = service.getCompletions(params)

        assertFalse(result.isIncomplete)
        assertEquals(3, result.items.size)

        val labels = result.items.map { it.label }.toSet()
        assertTrue(labels.contains("OrderID"))
        assertTrue(labels.contains("CustomerName"))
        assertTrue(labels.contains("Total"))
    }

    @Test
    fun `test getCompletions with partial match`() {
        val stateManager = StateManager()
        val service = CompletionService(stateManager)

        // Create a type environment
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

        // Open document
        stateManager.openDocument(
            uri = "file:///test.utlx",
            text = "output = input.Order.Ord",
            version = 1,
            languageId = "utlx"
        )
        stateManager.setTypeEnvironment("file:///test.utlx", typeEnv)

        val params = CompletionParams(
            textDocument = TextDocumentIdentifier("file:///test.utlx"),
            position = Position(0, 24) // After "input.Order.Ord"
        )

        val result = service.getCompletions(params)

        assertFalse(result.isIncomplete)
        assertEquals(2, result.items.size) // Should match OrderID and OrderDate

        val labels = result.items.map { it.label }.toSet()
        assertTrue(labels.contains("OrderID"))
        assertTrue(labels.contains("OrderDate"))
        assertFalse(labels.contains("CustomerName")) // Should not match
    }
}
