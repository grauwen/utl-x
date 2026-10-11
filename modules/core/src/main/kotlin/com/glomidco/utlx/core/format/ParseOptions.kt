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
