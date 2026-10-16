package com.glomidco.utlx.core.semantic

/**
 * Single source of truth for the **`%utlx` language versions this engine understands**, and the
 * deployment **mode** that caps which of them a running engine will accept (F21, build-plan Phase 1).
 *
 * UTL-X ships ONE multi-version engine. By default it accepts the whole [SUPPORTED] set ([LATEST] mode).
 * A deployment may restrict it to an older maximum — e.g. a 1.0-only ("UTLXe") posture **rejects**
 * `%utlx 1.1` — by constructing the interpreter with a lower `maxLanguageVersion`. This is the
 * "one engine + a mode switch (not two artifacts)" decision from the 1.1 build plan.
 *
 * Distinct from [LanguageNamespaces] (which gates *namespaces* like `validate.*` by minimum version):
 * this object answers "does this engine accept a script that declares `%utlx X.Y` at all?".
 */
object LanguageVersions {

    /** Every `%utlx` language version this engine understands, oldest → newest. */
    val SUPPORTED: List<String> = listOf("1.0", "1.1")

    /** The oldest understood version — the baseline a header-less or blank version is treated as. */
    val BASELINE: String = SUPPORTED.first()

    /** The newest understood version — the default engine mode (accept everything up to here). */
    val LATEST: String = SUPPORTED.last()

    /** Strip a leading `%utlx` and surrounding whitespace from a declared version string. */
    fun normalize(version: String): String = version.trim().removePrefix("%utlx").trim()

    /** Is [version] a language version this engine knows at all (ignoring mode)? */
    fun isSupported(version: String): Boolean {
        val norm = normalize(version)
        return SUPPORTED.any { normalize(it) == norm }
    }
}
