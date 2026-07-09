package com.glomidco.utlx.bundle

/**
 * Data model for the file-level Bundle layer (IF19), per the canonical Bundle Format
 * (docs/architecture/bundle-format.md §2). These are plain, serialization-framework-agnostic
 * data classes — the daemon (or engine) maps them to its own transport DTOs so this module
 * stays dependency-light.
 */

/** Summary of a bundle (a `.utlxp` project directory). */
data class BundleInfo(
    val name: String,
    val transformationCount: Int,
    val schemaCount: Int,
    val hasEngineConfig: Boolean,
)

/** Lightweight listing entry for a transformation (`transformations/<name>/`). */
data class TransformationInfo(
    val name: String,
    /** `<name>.utlx` (or, failing that, the first `*.utlx`) is present. */
    val hasSource: Boolean,
    /** `transform.yaml` is present (required by the `.utlxp` loader — §2). */
    val hasConfig: Boolean,
    /** `test-input-<slot>.<ext>` fixture filenames present in the dir (§5). */
    val testInputs: List<String>,
)

/** Full file-level content of a single transformation. */
data class Transformation(
    val name: String,
    /** `.utlx` source text, or null if absent. */
    val source: String?,
    /** `transform.yaml` text, or null if absent (stored/served as a blob — not parsed here). */
    val config: String?,
    val testInputs: List<String>,
)

/** Listing entry for a shared schema (`schemas/<name>.<ext>`). */
data class SchemaInfo(
    /** The schema filename, e.g. `order.json`. */
    val name: String,
    val sizeBytes: Long,
)
