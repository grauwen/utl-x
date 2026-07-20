package com.glomidco.utlx.bundle

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * File-level CRUD over a UTL-X bundle (a `.utlxp` project directory), per the canonical
 * Bundle Format (docs/architecture/bundle-format.md §2, §9).
 *
 * **"File-level"** means it manages the on-disk files (`.utlx`, `transform.yaml`, schema files,
 * `engine.yaml`) as text blobs. It does **not** deep-parse `transform.yaml` into a typed model.
 *
 * Shared by **utlxd** (over the IDE workspace) and **utlxe/EF03**. Every name is resolved
 * defensively (see [BundleNames]) so a request can never escape the bundle root.
 *
 * **Thread-safety.** This store is safe under concurrent callers — required because utlxe's admin
 * API (EF03) shares it and is multi-client. Two guarantees:
 *  - **Atomic writes** — every write goes to a sibling temp file that is then renamed over the
 *    target, so a reader never observes a half-written file.
 *  - **Per-entry locking** — reads and writes of a *single* entry (a transformation or a schema)
 *    serialize on a per-name lock, so two concurrent deploys of the same transformation can't
 *    interleave the two-file (`.utlx` + `transform.yaml`) write into a mismatched pair. Different
 *    entries proceed in parallel; `list*`/`info` are lock-free snapshots.
 */
class BundleStore(root: File) {
    private val logger = LoggerFactory.getLogger(BundleStore::class.java)

    /** Canonical bundle root; all path resolution is confined beneath it. */
    private val root: File = root.canonicalFile

    private val transformationsDir: File get() = File(root, TRANSFORMATIONS)
    private val schemasDir: File get() = File(root, SCHEMAS)

    /** Per-entry locks, keyed "tx:<name>" / "schema:<name>" / [ENGINE_YAML]. */
    private val locks = ConcurrentHashMap<String, ReentrantLock>()
    private fun <T> locked(key: String, block: () -> T): T =
        locks.computeIfAbsent(key) { ReentrantLock() }.withLock(block)

    // ---------- Bundle ----------

    fun info(): BundleInfo = BundleInfo(
        name = root.name,
        transformationCount = listTransformations().size,
        schemaCount = listSchemas().size,
        hasEngineConfig = File(root, ENGINE_YAML).isFile,
    )

    // ---------- Transformations ----------

    fun listTransformations(): List<TransformationInfo> {
        val dirs = transformationsDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.sortedBy { it.name }.map { txInfo(it) }
    }

    fun getTransformation(name: String): Transformation? {
        val dir = resolveTxDir(name)  // validates before we touch a lock (unsafe names throw here)
        return locked(txKey(name)) {
            if (!dir.isDirectory) null
            else Transformation(
                name = name,
                source = sourceFile(dir, name)?.readText(),
                config = File(dir, TRANSFORM_YAML).takeIf { it.isFile }?.readText(),
                testInputs = testInputs(dir),
            )
        }
    }

    /**
     * Create or update a transformation: writes `transformations/<name>/<name>.utlx` and, when
     * provided, `transform.yaml`. Both writes happen under one lock, so the pair is applied
     * atomically with respect to other operations on the same transformation.
     */
    fun putTransformation(name: String, source: String, config: String?) {
        val safe = BundleNames.requireTransformationName(name)
        val dir = resolveTxDir(safe)
        locked(txKey(safe)) {
            dir.mkdirs()
            atomicWrite(File(dir, "$safe.utlx"), source.toByteArray(Charsets.UTF_8))
            if (config != null) atomicWrite(File(dir, TRANSFORM_YAML), config.toByteArray(Charsets.UTF_8))
        }
        logger.debug("put transformation '{}' ({} bytes source)", safe, source.length)
    }

    /** @return true if a transformation directory existed and was removed. */
    fun deleteTransformation(name: String): Boolean {
        val dir = resolveTxDir(name)
        return locked(txKey(name)) {
            if (!dir.isDirectory) false else dir.deleteRecursively()
        }
    }

    // ---------- Schemas ----------

    fun listSchemas(): List<SchemaInfo> {
        val files = schemasDir.listFiles { f -> f.isFile && !f.name.startsWith(".") } ?: return emptyList()
        return files.sortedBy { it.name }.map { SchemaInfo(it.name, it.length()) }
    }

    fun getSchema(name: String): String? = getSchemaBytes(name)?.toString(Charsets.UTF_8)

    /** Byte-fidelity read — schemas may be any (text) format; bytes avoid any charset round-tripping. */
    fun getSchemaBytes(name: String): ByteArray? {
        val f = resolveSchema(name)
        return locked(schemaKey(name)) { if (f.isFile) f.readBytes() else null }
    }

    fun putSchema(name: String, content: String) = putSchemaBytes(name, content.toByteArray(Charsets.UTF_8))

    fun putSchemaBytes(name: String, content: ByteArray) {
        val f = resolveSchema(name)  // confined() enforces the path-traversal guard
        locked(schemaKey(name)) { atomicWrite(f, content) }
    }

    /** @return true if the schema file existed and was removed. */
    fun deleteSchema(name: String): Boolean {
        val f = resolveSchema(name)
        return locked(schemaKey(name)) { if (!f.isFile) false else f.delete() }
    }

    // ---------- Engine config ----------

    fun getEngineConfig(): String? = locked(ENGINE_YAML) {
        File(root, ENGINE_YAML).takeIf { it.isFile }?.readText()
    }

    fun putEngineConfig(content: String) = locked(ENGINE_YAML) {
        atomicWrite(File(root, ENGINE_YAML), content.toByteArray(Charsets.UTF_8))
    }

    // ---------- internals ----------

    private fun txKey(name: String) = "tx:$name"
    private fun schemaKey(name: String) = "schema:$name"

    /**
     * Write [content] to [target] atomically: write a hidden sibling temp file, then rename it over
     * the target. A concurrent reader therefore sees the old file or the new one — never a truncated
     * write. Falls back to a plain replace on filesystems without atomic move. The temp is hidden
     * (`.`-prefixed) and `.tmp`-suffixed, so it is never picked up by `listSchemas`/`*.utlx` scans.
     */
    private fun atomicWrite(target: File, content: ByteArray) {
        val dir = target.parentFile
        dir.mkdirs()
        val tmp = File.createTempFile(".${target.name}.", ".tmp", dir)
        try {
            tmp.writeBytes(content)
            try {
                Files.move(
                    tmp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun txInfo(dir: File): TransformationInfo = TransformationInfo(
        name = dir.name,
        hasSource = sourceFile(dir, dir.name) != null,
        hasConfig = File(dir, TRANSFORM_YAML).isFile,
        testInputs = testInputs(dir),
    )

    /** Engine rule: prefer `<tx>.utlx`, else the first `*.utlx` (BundleLoader.findUtlxSource). */
    private fun sourceFile(dir: File, name: String): File? {
        val preferred = File(dir, "$name.utlx")
        if (preferred.isFile) return preferred
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".utlx") }
            ?.sortedBy { it.name }?.firstOrNull()
    }

    private fun testInputs(dir: File): List<String> =
        dir.listFiles { f -> f.isFile && f.name.startsWith(TEST_INPUT_PREFIX) }
            ?.map { it.name }?.sorted() ?: emptyList()

    private fun resolveTxDir(name: String): File = confined(transformationsDir, name)
    private fun resolveSchema(name: String): File = confined(schemasDir, name)

    /**
     * Resolve `segment` under `base`, guaranteeing the canonical result stays within the bundle
     * root. [BundleNames.requireSafeSegment] already rejects separators and `..`; the containment
     * check is defense in depth against symlink / canonicalization surprises.
     */
    private fun confined(base: File, segment: String): File {
        BundleNames.requireSafeSegment(segment)
        val resolved = File(base, segment).canonicalFile
        require(resolved.toPath().startsWith(root.toPath())) {
            "resolved path escapes the bundle root: '$segment'"
        }
        return resolved
    }

    companion object {
        const val TRANSFORMATIONS = "transformations"
        const val SCHEMAS = "schemas"
        const val ENGINE_YAML = "engine.yaml"
        const val TRANSFORM_YAML = "transform.yaml"
        const val TEST_INPUT_PREFIX = "test-input-"
    }
}
