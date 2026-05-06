package org.apache.utlx.engine.admin

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * EF02: In-memory schema store with optional disk persistence.
 * Schemas are shared resources — referenced by multiple transformations.
 * Stored separately from transformations in {dataDir}/schemas/.
 */
class SchemaStore(private val dataDirPath: Path? = null) {

    private val logger = LoggerFactory.getLogger(SchemaStore::class.java)
    private val schemas = ConcurrentHashMap<String, SchemaEntry>()

    data class SchemaEntry(
        val filename: String,
        val content: ByteArray,
        val uploadedAt: java.time.Instant = java.time.Instant.now()
    )

    fun put(filename: String, content: ByteArray) {
        schemas[filename] = SchemaEntry(filename, content)
        // Persist to disk
        if (dataDirPath != null) {
            try {
                val schemasDir = dataDirPath.resolve("schemas")
                Files.createDirectories(schemasDir)
                Files.write(schemasDir.resolve(filename), content)
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
        if (removed && dataDirPath != null) {
            try {
                Files.deleteIfExists(dataDirPath.resolve("schemas").resolve(filename))
            } catch (e: Exception) {
                logger.warn("Failed to delete schema '{}' from disk: {}", filename, e.message)
            }
        }
        return removed
    }

    fun clear() {
        val names = schemas.keys.toList()
        schemas.clear()
        if (dataDirPath != null) {
            names.forEach { name ->
                try {
                    Files.deleteIfExists(dataDirPath.resolve("schemas").resolve(name))
                } catch (_: Exception) {}
            }
        }
    }

    /** Scan disk for previously persisted schemas and load into memory. */
    fun scanFromDisk() {
        if (dataDirPath == null) return
        val schemasDir = dataDirPath.resolve("schemas")
        if (!Files.exists(schemasDir) || !Files.isDirectory(schemasDir)) return

        var loaded = 0
        Files.list(schemasDir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .forEach { file ->
                    val filename = file.fileName.toString()
                    val content = Files.readAllBytes(file)
                    schemas[filename] = SchemaEntry(filename, content)
                    loaded++
                }
        }
        if (loaded > 0) {
            logger.info("Loaded {} schema(s) from {}", loaded, schemasDir)
        }
    }
}
