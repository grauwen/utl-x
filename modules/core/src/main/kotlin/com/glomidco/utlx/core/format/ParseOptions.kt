package com.glomidco.utlx.core.format

/**
 * Parser strictness profile — the deployment policy governing how tolerant the format readers are of
 * imperfect input, and how tight their resource bounds are. See
 * `docs/architecture/parser-strictness-profiles.md` (the spec) and `test-corpora/expectations/`
 * (the conformance: `profiles/{strict,standard,lenient}/` + `bounds.yaml`).
 *
 * The profile is set by the deployment, NOT by the `%utlx` header or the data; on a content guard it
 * is pinned to STRICT and locked. Default is STANDARD (spec-compliant, pragmatic).
 */
enum class ParseProfile { LENIENT, STANDARD, STRICT }

/**
 * Governance resolver for the parse profile (F23 Phase 2; see parser-strictness-profiles.md §9.1).
 *
 * The effective profile is resolved from the layered sources — a guard/deployment **lock**, a bundle- or
 * CLI-**requested** profile, and the standard default — by the rule **"strictest wins, never weaken a
 * lock"**:
 *
 *   effective = strictest of ( lock , requested , STANDARD )
 *
 * A lock is a hard **floor**: a request *at or above* it is honoured (raising strictness is always safe);
 * a request *below* it is either clamped up to the lock (fail-safe — for a transient CLI flag) or
 * refused outright (loud — for a deployed bundle that *declares* a weaker profile). Either way a lock can
 * never be weakened by anything downstream of it, so a guard pinned to STRICT stays STRICT.
 */
object ParseProfilePolicy {
    /** Strictness order: LENIENT (0) < STANDARD (1) < STRICT (2). */
    fun rank(p: ParseProfile): Int = when (p) {
        ParseProfile.LENIENT -> 0
        ParseProfile.STANDARD -> 1
        ParseProfile.STRICT -> 2
    }

    /** Raised when a requested profile would weaken a locked floor and [resolve] is told to refuse (fail-closed). */
    class PolicyViolation(message: String) : RuntimeException(message)

    /**
     * Resolve the effective profile.
     *
     * @param lock     the guard/deployment floor, or null for an unlocked deployment.
     * @param requested the bundle- or CLI-requested profile, or null → the STANDARD default.
     * @param rejectBelowLock  when a request is weaker than [lock]: true → throw [PolicyViolation]
     *   (a deployed bundle that declares weaker-than-locked is a misconfiguration — surface it);
     *   false → clamp up to the lock (fail-safe; the right behaviour for an ignorable transient flag).
     */
    fun resolve(lock: ParseProfile?, requested: ParseProfile?, rejectBelowLock: Boolean = false): ParseProfile {
        val req = requested ?: ParseProfile.STANDARD
        if (lock == null) return req
        if (rank(req) < rank(lock)) {
            if (rejectBelowLock) throw PolicyViolation(
                "Requested parse profile '$req' is weaker than the locked '$lock' — refused (fail-closed).")
            return lock   // fail-safe: never weaken below the lock
        }
        return req        // req >= lock → honour it (raising strictness is safe)
    }
}

/**
 * Resolved, per-reader parse options for a [ParseProfile]. Readers adopt fields incrementally; a field
 * a given reader doesn't consume yet is simply inert. Values mirror `test-corpora/expectations/bounds.yaml`
 * and the §4 toggle matrix of the strictness-profiles doc.
 *
 * Carries BOTH axes the doc keeps separate:
 *   - resource bounds (always-on; DoS) — [maxDepth] (B32) …
 *   - grammar leniency (§4)            — [allowDuplicateKeys] (B31), [allowComments], … [allowUndefinedEntities]/[allowDoctype] (B29)
 */
data class ParseOptions(
    val profile: ParseProfile = ParseProfile.STANDARD,

    // ── resource bounds (always-on axis; see bounds.yaml) ──
    // ONE profile policy value — NOT a per-format constant. Each reader ENFORCES it per-format and may
    // clamp it to a format-specific SAFETY CEILING: CSV ignores it (flat, no nesting); YAML → SnakeYAML
    // LoaderOptions.nestingDepthLimit; avro/protobuf → the library recursion limit; jsch/xsd/osch/tsch
    // inherit the underlying JSON/XML depth. See parser-strictness-profiles.md §4.1.
    val maxDepth: Int = 256,

    // ── grammar leniency (§4) ──
    /** false → a duplicate object/mapping key is a parse error; true → last-wins (B31). */
    val allowDuplicateKeys: Boolean = false,
    /** JSON: tolerate `//`/`/* */` comments (lenient). */
    val allowComments: Boolean = false,
    /** JSON: tolerate a trailing comma (lenient). */
    val allowTrailingCommas: Boolean = false,
    /** JSON: tolerate unquoted object keys (lenient). */
    val allowUnquotedKeys: Boolean = false,

    // ── XML entity / DOCTYPE (B29) ──
    /** false → reject a `<!DOCTYPE …>` outright (strict); true → skip it (lenient/standard). */
    val allowDoctype: Boolean = true,
    /** false → an undefined entity reference is a well-formedness error; true → keep it as literal text. */
    val allowUndefinedEntities: Boolean = false,
) {
    companion object {
        fun strict() = ParseOptions(
            profile = ParseProfile.STRICT,
            maxDepth = 64,
            allowDuplicateKeys = false,
            allowComments = false,
            allowTrailingCommas = false,
            allowUnquotedKeys = false,
            allowDoctype = false,
            allowUndefinedEntities = false,
        )

        fun standard() = ParseOptions(
            profile = ParseProfile.STANDARD,
            maxDepth = 256,   // doubled from 128; ~15x margin below the recursive-parser crash (B32); bounds ⟂ grammar
            allowDuplicateKeys = false,   // ambiguous → reject (§4 standard)
            allowComments = false,
            allowTrailingCommas = false,
            allowUnquotedKeys = false,
            allowDoctype = true,          // tolerated in a prolog; only STRICT rejects outright
            allowUndefinedEntities = false,  // undefined entity = well-formedness error
        )

        fun lenient() = ParseOptions(
            profile = ParseProfile.LENIENT,
            maxDepth = 256,
            allowDuplicateKeys = true,    // last-wins
            allowComments = true,
            allowTrailingCommas = true,
            allowUnquotedKeys = true,
            allowDoctype = true,
            allowUndefinedEntities = true,   // keep as literal
        )

        fun of(profile: ParseProfile): ParseOptions = when (profile) {
            ParseProfile.STRICT -> strict()
            ParseProfile.STANDARD -> standard()
            ParseProfile.LENIENT -> lenient()
        }

        /** The pragmatic default when no deployment policy is set. */
        val DEFAULT = standard()
    }
}
