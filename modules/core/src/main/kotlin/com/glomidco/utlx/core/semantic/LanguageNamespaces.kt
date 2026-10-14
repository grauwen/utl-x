package com.glomidco.utlx.core.semantic

/**
 * The single source of truth for **which stdlib namespace requires which `%utlx` language version**
 * (F21). A language feature arrives as a namespace: `validate.*` at `%utlx 1.1` (a future `ai.*` → `2.0`).
 *
 * Used by BOTH:
 *   - the interpreter's runtime gate (reject a `validate.*` call under `%utlx 1.0`), and
 *   - `StandardLibrary.exportRegistry(version)` (hide `validate.*` from the IDE function list unless the
 *     context is ≥ 1.1).
 *
 * Note this is distinct from `@UTLXFunction(since)`, which is an informational "added-in release" tag
 * (already set >1.0 on functions valid under 1.0, e.g. crypto `since="1.2"`). Gate on the NAMESPACE here,
 * never on `since`.
 */
object LanguageNamespaces {

    /** namespace → minimum `%utlx` language version that makes it available. */
    val NAMESPACE_MIN_VERSION: Map<String, String> = mapOf(
        "validate" to "1.1",
    )

    /** The namespace of a (possibly dotted) function name, or "" for a flat/un-namespaced name. */
    fun namespaceOf(functionName: String): String = functionName.substringBefore('.', missingDelimiterValue = "")

    /** The minimum `%utlx` version a function's namespace requires, or null if ungated (always available). */
    fun minVersionFor(functionName: String): String? = NAMESPACE_MIN_VERSION[namespaceOf(functionName)]

    /** Is [functionName] available to a script declaring [languageVersion]? Ungated namespaces: always. */
    fun isAvailableAt(functionName: String, languageVersion: String): Boolean {
        val required = minVersionFor(functionName) ?: return true
        return compareVersions(languageVersion, required) >= 0
    }

    /** Compare `%utlx` versions by numeric components ("1.0" < "1.1" < "2.0"); tolerates a `%utlx ` prefix. */
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.trim().removePrefix("%utlx").trim().split('.').map { it.toIntOrNull() ?: 0 }
        val pa = parts(a); val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}
