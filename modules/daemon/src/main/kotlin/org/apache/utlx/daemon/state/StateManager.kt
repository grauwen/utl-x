// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/state/StateManager.kt
package org.apache.utlx.daemon.state

import org.apache.utlx.analysis.types.TypeContext
import org.apache.utlx.core.ast.Program
import org.apache.utlx.daemon.analysis.DocumentAnalyzer
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Document mode enumeration
 *
 * Determines how the LSP daemon processes a document:
 * - DESIGN_TIME: Schema-based type checking (no execution)
 * - RUNTIME: Instance data transformation and execution
 */
enum class DocumentMode {
    /**
     * Design-Time Mode: Type-checking with schemas
     *
     * In this mode:
     * - External schemas (XSD, JSON Schema) are loaded
     * - Path expressions are validated against schema types
     * - Output schema can be inferred
     * - No actual data transformation occurs
     */
    DESIGN_TIME,

    /**
     * Runtime Mode: Data transformation and execution
     *
     * In this mode:
     * - Instance data (XML, JSON, etc.) is processed
     * - Transformations are executed
     * - Parser diagnostics are enabled
     * - Performance can be measured
     */
    RUNTIME
}

/**
 * State Manager for LSP Daemon
 *
 * Manages:
 * - Open documents (URIs, content, versions)
 * - AST cache (parsed programs)
 * - Type environments (per document)
 * - Schema cache (loaded schemas)
 * - Document modes (design-time vs runtime)
 *
 * Thread-safe for concurrent access from multiple LSP handlers.
 */
class StateManager {

    private val logger = LoggerFactory.getLogger(StateManager::class.java)
    private val analyzer = DocumentAnalyzer()

    // Document state indexed by URI
    private val documents = ConcurrentHashMap<String, DocumentState>()

    // Type environments indexed by URI
    private val typeEnvironments = ConcurrentHashMap<String, TypeContext>()

    // Schema cache indexed by schema URI
    private val schemas = ConcurrentHashMap<String, SchemaInfo>()

    // Document modes indexed by URI (default: RUNTIME)
    private val documentModes = ConcurrentHashMap<String, DocumentMode>()

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

        // Automatically analyze document and create type environment
        inferTypeEnvironment(uri, text)
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

        // Re-infer type environment from updated content
        inferTypeEnvironment(uri, text)
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
     * Infer type environment from document content
     *
     * Analyzes the document text and extracts type information
     * from input declarations. Automatically caches the result.
     * If no type can be inferred, clears any existing type environment.
     */
    private fun inferTypeEnvironment(uri: String, text: String) {
        try {
            val typeContext = analyzer.analyzeDocument(text)
            if (typeContext != null) {
                setTypeEnvironment(uri, typeContext)
                logger.debug("Inferred type environment for: $uri")
            } else {
                // Clear old type environment if we can't infer a new one
                typeEnvironments.remove(uri)
                logger.debug("Could not infer type environment for: $uri (cleared)")
            }
        } catch (e: Exception) {
            logger.error("Error inferring type environment for: $uri", e)
            // Clear type environment on error
            typeEnvironments.remove(uri)
        }
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
     * Set document mode (design-time vs runtime)
     *
     * @param uri Document URI
     * @param mode DocumentMode (DESIGN_TIME or RUNTIME)
     */
    fun setDocumentMode(uri: String, mode: DocumentMode) {
        logger.info("Setting mode for $uri: $mode")
        documentModes[uri] = mode
    }

    /**
     * Get document mode
     *
     * @param uri Document URI
     * @return DocumentMode (defaults to RUNTIME if not set)
     */
    fun getDocumentMode(uri: String): DocumentMode {
        return documentModes[uri] ?: DocumentMode.RUNTIME
    }

    /**
     * Clear all state (for testing)
     */
    fun clear() {
        documents.clear()
        typeEnvironments.clear()
        schemas.clear()
        documentModes.clear()
    }

    /**
     * Get statistics for debugging
     */
    fun getStatistics(): StateStatistics {
        return StateStatistics(
            openDocuments = documents.size,
            cachedTypeEnvironments = typeEnvironments.size,
            cachedSchemas = schemas.size,
            cachedAsts = documents.values.count { it.ast != null },
            designTimeDocs = documentModes.values.count { it == DocumentMode.DESIGN_TIME },
            runtimeDocs = documentModes.size - documentModes.values.count { it == DocumentMode.DESIGN_TIME }
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
    val cachedAsts: Int,
    val designTimeDocs: Int,
    val runtimeDocs: Int
)
