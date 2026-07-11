package com.glomidco.utlx.bundle

/**
 * Naming rules from Bundle Format §3.
 *
 * §3 rule 1 calls a transformation name a "free filesystem string." That is true for a *local*
 * loader, but this layer backs a **stateful REST API over the filesystem**, so a request name is
 * additionally constrained to a **safe single path segment** — otherwise `../` in a name would let
 * a caller read/write/delete outside the bundle root. This is the module's primary security guard;
 * `BundleStore` also re-checks containment against the canonical root as defense in depth.
 */
object BundleNames {

    /** A safe single path segment: non-blank, no separators, not `.`/`..`, no NUL. */
    fun requireSafeSegment(name: String): String {
        require(name.isNotBlank()) { "name must not be blank" }
        require('/' !in name && '\\' !in name) { "name must be a single path segment (no '/' or '\\')" }
        require(name != "." && name != "..") { "name must not be '.' or '..'" }
        require(name.none { it.code == 0 }) { "name must not contain a NUL character" }
        return name
    }

    /** Transformation name — a safe segment, kept verbatim (§3 rule 1: no stripping). */
    fun requireTransformationName(name: String): String = requireSafeSegment(name)
}
