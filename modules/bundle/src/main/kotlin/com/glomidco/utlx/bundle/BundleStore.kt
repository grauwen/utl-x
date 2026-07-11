package com.glomidco.utlx.bundle

import org.slf4j.LoggerFactory
import java.io.File

/**
 * File-level CRUD over a UTL-X bundle (a `.utlxp` project directory), per the canonical
 * Bundle Format (docs/architecture/bundle-format.md §2, §9).
 *
 * **"File-level"** means it manages the on-disk files (`.utlx`, `transform.yaml`, schema files,
 * `engine.yaml`) as text blobs. It does **not** deep-parse `transform.yaml` into a typed model —
 * that, and reconciliation with the engine's `TransformConfig`, happens when EF03 is refactored
 * onto this layer (a later slice; the engine is untouched for now).
 *
 * Shared by **utlxd** (over the IDE workspace) and, later, **utlxe/EF03**. The only state is the
 * root path; every name is resolved defensively (see [BundleNames]) so a request can never escape
 * the bundle root.
 */
class BundleStore(root: File) {
    private val logger = LoggerFactory.getLogger(BundleStore::class.java)

    /** Canonical bundle root; all path resolution is confined beneath it. */
    private val root: File = root.canonicalFile

    private val transformationsDir: File get() = File(root, TRANSFORMATIONS)
    private val schemasDir: File get() = File(root, SCHEMAS)

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
        val dir = resolveTxDir(name)
        if (!dir.isDirectory) return null
        return Transformation(
            name = name,
            source = sourceFile(dir, name)?.readText(),
            config = File(dir, TRANSFORM_YAML).takeIf { it.isFile }?.readText(),
            testInputs = testInputs(dir),
        )
    }

    /**
     * Create or update a transformation: writes `transformations/<name>/<name>.utlx` and, when
     * provided, `transform.yaml`. The IDE authoring path should pass a `config` so the result is
     * loadable by the `.utlxp` loader (which SKIPS a dir without `transform.yaml` — §2).
     */
    fun putTransformation(name: String, source: String, config: String?) {
        val safe = BundleNames.requireTransformationName(name)
        val dir = resolveTxDir(safe)
        dir.mkdirs()
        File(dir, "$safe.utlx").writeText(source)
        if (config != null) File(dir, TRANSFORM_YAML).writeText(config)
        logger.debug("put transformation '{}' ({} bytes source)", safe, source.length)
    }

    /** @return true if a transformation directory existed and was removed. */
    fun deleteTransformation(name: String): Boolean {
        val dir = resolveTxDir(name)
        if (!dir.isDirectory) return false
        return dir.deleteRecursively()
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
        return if (f.isFile) f.readBytes() else null
    }

    fun putSchema(name: String, content: String) = putSchemaBytes(name, content.toByteArray(Charsets.UTF_8))

    fun putSchemaBytes(name: String, content: ByteArray) {
        val f = resolveSchema(name)   // confined() enforces the path-traversal guard
        f.parentFile.mkdirs()
        f.writeBytes(content)
    }

    /** @return true if the schema file existed and was removed. */
    fun deleteSchema(name: String): Boolean {
        val f = resolveSchema(name)
        if (!f.isFile) return false
        return f.delete()
    }

    // ---------- Engine config ----------

    fun getEngineConfig(): String? = File(root, ENGINE_YAML).takeIf { it.isFile }?.readText()

    fun putEngineConfig(content: String) {
        File(root, ENGINE_YAML).writeText(content)
    }

    // ---------- internals ----------

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
