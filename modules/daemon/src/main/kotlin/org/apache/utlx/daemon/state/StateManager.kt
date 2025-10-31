// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/state/StateManager.kt
package org.apache.utlx.daemon.state

import org.apache.utlx.analysis.types.TypeContext
import org.apache.utlx.core.ast.Program
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * State Manager for LSP Daemon
 *
 * Manages:
 * - Open documents (URIs, content, versions)
 * - AST cache (parsed programs)
 * - Type environments (per document)
 * - Schema cache (loaded schemas)
 *
 * Thread-safe for concurrent access from multiple LSP handlers.
 */
class StateManager {

    private val logger = LoggerFactory.getLogger(StateManager::class.java)

    // Document state indexed by URI
    private val documents = ConcurrentHashMap<String, DocumentState>()

    // Type environments indexed by URI
    private val typeEnvironments = ConcurrentHashMap<String, TypeContext>()

    // Schema cache indexed by schema URI
    private val schemas = ConcurrentHashMap<String, SchemaInfo>()

    /**
     * Open or update a document
     */
    fun openDocument(uri: String, text: String, version: Int, languageId: String = "utlx") {
        logger.info("Opening document: $uri (version $version)")

        val doc = DocumentState(
            uri = uri,
            text = text,
            version = version,
            languageId = languageId,
            ast = null  // Will be parsed lazily
        )

        documents[uri] = doc
    }

    /**
     * Update document content (from didChange event)
     */
    fun updateDocument(uri: String, text: String, version: Int) {
        logger.info("Updating document: $uri (version $version)")

        val existing = documents[uri]
        if (existing == null) {
            logger.warn("Attempt to update non-existent document: $uri")
            return
        }

        val updated = existing.copy(
            text = text,
            version = version,
            ast = null  // Invalidate AST cache
        )

        documents[uri] = updated

        // Invalidate type environment
        typeEnvironments.remove(uri)
    }

    /**
     * Close a document
     */
    fun closeDocument(uri: String) {
        logger.info("Closing document: $uri")
        documents.remove(uri)
        typeEnvironments.remove(uri)
    }

    /**
     * Get document state
     */
    fun getDocument(uri: String): DocumentState? {
        return documents[uri]
    }

    /**
     * Get document text
     */
    fun getDocumentText(uri: String): String? {
        return documents[uri]?.text
    }

    /**
     * Get all open document URIs
     */
    fun getOpenDocuments(): Set<String> {
        return documents.keys.toSet()
    }

    /**
     * Update or cache parsed AST for a document
     */
    fun setAst(uri: String, ast: Program) {
        documents[uri]?.let { doc ->
            documents[uri] = doc.copy(ast = ast)
        }
    }

    /**
     * Get cached AST for a document
     */
    fun getAst(uri: String): Program? {
        return documents[uri]?.ast
    }

    /**
     * Set type environment for a document
     */
    fun setTypeEnvironment(uri: String, typeEnv: TypeContext) {
        logger.debug("Caching type environment for: $uri")
        typeEnvironments[uri] = typeEnv
    }

    /**
     * Get type environment for a document
     */
    fun getTypeEnvironment(uri: String): TypeContext? {
        return typeEnvironments[uri]
    }

    /**
     * Register a schema (XSD, JSON Schema, etc.)
     */
    fun registerSchema(uri: String, content: String, format: SchemaFormat) {
        logger.info("Registering schema: $uri (format: $format)")
        schemas[uri] = SchemaInfo(uri, content, format)
    }

    /**
     * Get registered schema
     */
    fun getSchema(uri: String): SchemaInfo? {
        return schemas[uri]
    }

    /**
     * Clear all state (for testing)
     */
    fun clear() {
        documents.clear()
        typeEnvironments.clear()
        schemas.clear()
    }

    /**
     * Get statistics for debugging
     */
    fun getStatistics(): StateStatistics {
        return StateStatistics(
            openDocuments = documents.size,
            cachedTypeEnvironments = typeEnvironments.size,
            cachedSchemas = schemas.size,
            cachedAsts = documents.values.count { it.ast != null }
        )
    }
}

/**
 * Document state for an open file
 */
data class DocumentState(
    val uri: String,
    val text: String,
    val version: Int,
    val languageId: String,
    val ast: Program?  // Cached parsed AST
)

/**
 * Schema information
 */
data class SchemaInfo(
    val uri: String,
    val content: String,
    val format: SchemaFormat
)

/**
 * Schema format enumeration
 */
enum class SchemaFormat {
    XSD,
    JSON_SCHEMA,
    AVRO,
    UNKNOWN
}

/**
 * Statistics about daemon state
 */
data class StateStatistics(
    val openDocuments: Int,
    val cachedTypeEnvironments: Int,
    val cachedSchemas: Int,
    val cachedAsts: Int
)
