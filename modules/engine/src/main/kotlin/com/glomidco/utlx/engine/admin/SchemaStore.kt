package com.glomidco.utlx.engine.admin

import com.glomidco.utlx.bundle.BundleStore
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * EF02: In-memory schema store with optional disk persistence.
 * Schemas are shared resources — referenced by multiple transformations.
 * Stored separately from transformations in {dataDir}/schemas/.
 *
 * IF19: disk persistence now goes through the shared [BundleStore] (modules/bundle), so utlxe and
 * utlxd write the identical `schemas/` layout through the same code (and share its path-traversal
 * guard). The in-memory cache is retained for fast runtime validation lookups.
 */
class SchemaStore(private val dataDirPath: Path? = null) {

    private val logger = LoggerFactory.getLogger(SchemaStore::class.java)
    private val schemas = ConcurrentHashMap<String, SchemaEntry>()

    /** Shared file layer; null when no data dir is configured (in-memory only). */
    private val bundle: BundleStore? = dataDirPath?.let { BundleStore(it.toFile()) }

    data class SchemaEntry(
        val filename: String,
        val content: ByteArray,
        val uploadedAt: java.time.Instant = java.time.Instant.now()
    )

    fun put(filename: String, content: ByteArray) {
        schemas[filename] = SchemaEntry(filename, content)
        // Persist to disk via the shared bundle layer
        if (bundle != null) {
            try {
                bundle.putSchemaBytes(filename, content)
                logger.debug("Schema '{}' persisted to disk", filename)
            } catch (e: Exception) {
                logger.warn("Failed to persist schema '{}': {}", filename, e.message)
            }
        }
    }

    fun get(filename: String): SchemaEntry? = schemas[filename]

    fun getContent(filename: String): ByteArray? = schemas[filename]?.content

    fun list(): List<SchemaEntry> = schemas.values.toList()

    fun remove(filename: String): Boolean {
        val removed = schemas.remove(filename) != null
        if (removed && bundle != null) {
            try {
                bundle.deleteSchema(filename)
            } catch (e: Exception) {
                logger.warn("Failed to delete schema '{}' from disk: {}", filename, e.message)
            }
        }
        return removed
    }

    fun clear() {
        val names = schemas.keys.toList()
        schemas.clear()
        if (bundle != null) {
            names.forEach { name ->
                try {
                    bundle.deleteSchema(name)
                } catch (_: Exception) {}
            }
        }
    }

    /** Scan disk for previously persisted schemas and load into memory (via the shared bundle layer). */
    fun scanFromDisk() {
        val b = bundle ?: return
        var loaded = 0
        for (schema in b.listSchemas()) {
            val content = b.getSchemaBytes(schema.name) ?: continue
            schemas[schema.name] = SchemaEntry(schema.name, content)
            loaded++
        }
        if (loaded > 0) {
            logger.info("Loaded {} schema(s) from {}/schemas", loaded, dataDirPath)
        }
    }
}
