// modules/daemon/src/test/kotlin/org/apache/utlx/daemon/state/StateManagerTest.kt
package org.apache.utlx.daemon.state

import org.apache.utlx.analysis.types.TypeContext
import org.apache.utlx.core.ast.*
import org.apache.utlx.core.ast.Expression.StringLiteral
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * Tests for StateManager - document and cache management
 */
class StateManagerTest {

    private lateinit var stateManager: StateManager

    @BeforeEach
    fun setup() {
        stateManager = StateManager()
    }

    @Test
    fun `test open document`() {
        val uri = "file:///test.utlx"
        val text = "output = input.name"
        val version = 1

        stateManager.openDocument(uri, text, version, "utlx")

        val doc = stateManager.getDocument(uri)
        assertNotNull(doc)
        assertEquals(uri, doc?.uri)
        assertEquals(text, doc?.text)
        assertEquals(version, doc?.version)
        assertEquals("utlx", doc?.languageId)
        assertNull(doc?.ast) // AST not parsed yet
    }

    @Test
    fun `test get document text`() {
        val uri = "file:///test.utlx"
        val text = "output = input.name"

        stateManager.openDocument(uri, text, 1)

        val retrievedText = stateManager.getDocumentText(uri)
        assertEquals(text, retrievedText)
    }

    @Test
    fun `test update document`() {
        val uri = "file:///test.utlx"
        stateManager.openDocument(uri, "old content", 1)

        val newText = "new content"
        stateManager.updateDocument(uri, newText, 2)

        val doc = stateManager.getDocument(uri)
        assertEquals(newText, doc?.text)
        assertEquals(2, doc?.version)
    }

    @Test
    fun `test update non-existent document does nothing`() {
        val uri = "file:///nonexistent.utlx"

        // Should not throw exception
        stateManager.updateDocument(uri, "some text", 1)

        assertNull(stateManager.getDocument(uri))
    }

    @Test
    fun `test close document`() {
        val uri = "file:///test.utlx"
        stateManager.openDocument(uri, "content", 1)

        assertNotNull(stateManager.getDocument(uri))

        stateManager.closeDocument(uri)

        assertNull(stateManager.getDocument(uri))
        assertNull(stateManager.getDocumentText(uri))
    }

    @Test
    fun `test get open documents`() {
        stateManager.openDocument("file:///test1.utlx", "content1", 1)
        stateManager.openDocument("file:///test2.utlx", "content2", 1)
        stateManager.openDocument("file:///test3.utlx", "content3", 1)

        val openDocs = stateManager.getOpenDocuments()
        assertEquals(3, openDocs.size)
        assertTrue(openDocs.contains("file:///test1.utlx"))
        assertTrue(openDocs.contains("file:///test2.utlx"))
        assertTrue(openDocs.contains("file:///test3.utlx"))
    }

    @Test
    fun `test set and get AST`() {
        val uri = "file:///test.utlx"
        stateManager.openDocument(uri, "output = input", 1)

        // Create a minimal AST (using Location and dummy expression)
        val location = Location(1, 1)
        val header = Header(
            version = "1.0",
            inputs = emptyList(),
            outputs = emptyList(),
            location = location
        )
        val body = StringLiteral("test", location) // Minimal expression
        val ast = Program(header, body, location)

        stateManager.setAst(uri, ast)

        val retrievedAst = stateManager.getAst(uri)
        assertNotNull(retrievedAst)
        assertNotNull(retrievedAst?.header)
    }

    @Test
    fun `test update document invalidates AST`() {
        val uri = "file:///test.utlx"
        stateManager.openDocument(uri, "output = input", 1)

        val location = Location(1, 1)
        val header = Header("1.0", emptyList(), emptyList(), location)
        val body = StringLiteral("test", location)
        val ast = Program(header, body, location)
        stateManager.setAst(uri, ast)

        assertNotNull(stateManager.getAst(uri))

        // Update document - should invalidate AST
        stateManager.updateDocument(uri, "new content", 2)

        assertNull(stateManager.getAst(uri))
    }

    @Test
    fun `test set and get type environment`() {
        val uri = "file:///test.utlx"
        stateManager.openDocument(uri, "output = input", 1)

        val typeEnv = TypeContext()

        stateManager.setTypeEnvironment(uri, typeEnv)

        val retrievedEnv = stateManager.getTypeEnvironment(uri)
        assertNotNull(retrievedEnv)
    }

    @Test
    fun `test update document invalidates type environment`() {
        val uri = "file:///test.utlx"
        stateManager.openDocument(uri, "output = input", 1)

        val typeEnv = TypeContext()
        stateManager.setTypeEnvironment(uri, typeEnv)

        assertNotNull(stateManager.getTypeEnvironment(uri))

        // Update document - should invalidate type environment
        stateManager.updateDocument(uri, "new content", 2)

        assertNull(stateManager.getTypeEnvironment(uri))
    }

    @Test
    fun `test register and get schema`() {
        val uri = "file:///schema.xsd"
        val content = """<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>"""

        stateManager.registerSchema(uri, content, SchemaFormat.XSD)

        val schema = stateManager.getSchema(uri)
        assertNotNull(schema)
        assertEquals(uri, schema?.uri)
        assertEquals(content, schema?.content)
        assertEquals(SchemaFormat.XSD, schema?.format)
    }

    @Test
    fun `test get non-existent schema returns null`() {
        val schema = stateManager.getSchema("file:///nonexistent.xsd")
        assertNull(schema)
    }

    @Test
    fun `test clear state`() {
        stateManager.openDocument("file:///test1.utlx", "content", 1)
        stateManager.openDocument("file:///test2.utlx", "content", 1)
        stateManager.registerSchema("file:///schema.xsd", "<xs:schema/>", SchemaFormat.XSD)

        assertEquals(2, stateManager.getOpenDocuments().size)

        stateManager.clear()

        assertEquals(0, stateManager.getOpenDocuments().size)
        assertNull(stateManager.getSchema("file:///schema.xsd"))
    }

    @Test
    fun `test statistics tracking`() {
        stateManager.openDocument("file:///test1.utlx", "content1", 1)
        stateManager.openDocument("file:///test2.utlx", "content2", 1)

        val location = Location(1, 1)
        val header = Header("1.0", emptyList(), emptyList(), location)
        val body = StringLiteral("test", location)
        val ast = Program(header, body, location)
        stateManager.setAst("file:///test1.utlx", ast)

        val typeEnv = TypeContext()
        stateManager.setTypeEnvironment("file:///test1.utlx", typeEnv)

        stateManager.registerSchema("file:///schema.xsd", "<xs:schema/>", SchemaFormat.XSD)

        val stats = stateManager.getStatistics()

        assertEquals(2, stats.openDocuments)
        assertEquals(1, stats.cachedAsts)
        assertEquals(1, stats.cachedTypeEnvironments)
        assertEquals(1, stats.cachedSchemas)
    }

    @Test
    fun `test close document removes type environment`() {
        val uri = "file:///test.utlx"
        stateManager.openDocument(uri, "content", 1)

        val typeEnv = TypeContext()
        stateManager.setTypeEnvironment(uri, typeEnv)

        assertNotNull(stateManager.getTypeEnvironment(uri))

        stateManager.closeDocument(uri)

        assertNull(stateManager.getTypeEnvironment(uri))
    }

    @Test
    fun `test multiple schema formats`() {
        stateManager.registerSchema("file:///test.xsd", "<xs:schema/>", SchemaFormat.XSD)
        stateManager.registerSchema("file:///test.json", """{"${'$'}schema":"..."}""", SchemaFormat.JSON_SCHEMA)
        stateManager.registerSchema("file:///test.avsc", """{"type":"record"}""", SchemaFormat.AVRO)

        val xsdSchema = stateManager.getSchema("file:///test.xsd")
        assertEquals(SchemaFormat.XSD, xsdSchema?.format)

        val jsonSchema = stateManager.getSchema("file:///test.json")
        assertEquals(SchemaFormat.JSON_SCHEMA, jsonSchema?.format)

        val avroSchema = stateManager.getSchema("file:///test.avsc")
        assertEquals(SchemaFormat.AVRO, avroSchema?.format)
    }

    @Test
    fun `test concurrent document operations`() {
        val uri1 = "file:///test1.utlx"
        val uri2 = "file:///test2.utlx"

        // Simulate concurrent opens
        stateManager.openDocument(uri1, "content1", 1)
        stateManager.openDocument(uri2, "content2", 1)

        assertNotNull(stateManager.getDocument(uri1))
        assertNotNull(stateManager.getDocument(uri2))

        // Simulate concurrent updates
        stateManager.updateDocument(uri1, "new content1", 2)
        stateManager.updateDocument(uri2, "new content2", 2)

        assertEquals("new content1", stateManager.getDocumentText(uri1))
        assertEquals("new content2", stateManager.getDocumentText(uri2))
    }

    @Test
    fun `test document version tracking`() {
        val uri = "file:///test.utlx"
        stateManager.openDocument(uri, "v1", 1)
        assertEquals(1, stateManager.getDocument(uri)?.version)

        stateManager.updateDocument(uri, "v2", 2)
        assertEquals(2, stateManager.getDocument(uri)?.version)

        stateManager.updateDocument(uri, "v3", 3)
        assertEquals(3, stateManager.getDocument(uri)?.version)
    }
}
