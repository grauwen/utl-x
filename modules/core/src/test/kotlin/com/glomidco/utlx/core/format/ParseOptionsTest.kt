package com.glomidco.utlx.core.format

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Locks the profile VALUE CONTRACT — the §4 toggle matrix and the `bounds.yaml` depths encoded in the
 * factories — so it can't silently drift, plus the structural invariants. ParseOptions has no logic
 * beyond the factories, so this guards the SPEC, not behaviour (per-reader behaviour is covered by the
 * JSON/XML/YAML/CSV tests). Keep these numbers in step with `test-corpora/expectations/bounds.yaml`.
 */
class ParseOptionsTest {

    @Test
    fun `per-profile max depth matches bounds-yaml (64 256 256)`() {
        assertEquals(64, ParseOptions.strict().maxDepth)
        assertEquals(256, ParseOptions.standard().maxDepth)   // doubled from 128
        assertEquals(256, ParseOptions.lenient().maxDepth)
    }

    @Test
    fun `duplicate-key policy - reject under standard-strict, last-wins under lenient`() {
        assertFalse(ParseOptions.strict().allowDuplicateKeys)
        assertFalse(ParseOptions.standard().allowDuplicateKeys)
        assertTrue(ParseOptions.lenient().allowDuplicateKeys)
    }

    @Test
    fun `DOCTYPE rejected only under strict`() {
        assertFalse(ParseOptions.strict().allowDoctype)
        assertTrue(ParseOptions.standard().allowDoctype)
        assertTrue(ParseOptions.lenient().allowDoctype)
    }

    @Test
    fun `undefined entities tolerated only under lenient`() {
        assertFalse(ParseOptions.strict().allowUndefinedEntities)
        assertFalse(ParseOptions.standard().allowUndefinedEntities)
        assertTrue(ParseOptions.lenient().allowUndefinedEntities)
    }

    @Test
    fun `permissiveness is monotonic - strict less-eq standard less-eq lenient`() {
        val s = ParseOptions.strict(); val m = ParseOptions.standard(); val l = ParseOptions.lenient()
        // every boolean toggle: once allowed at a stricter profile it must stay allowed at looser ones
        // (false < true). Catches an accidental "strict allows X but lenient forbids X" inversion.
        fun mono(a: Boolean, b: Boolean, c: Boolean) = assertTrue((!a || b) && (!b || c))
        mono(s.allowDuplicateKeys, m.allowDuplicateKeys, l.allowDuplicateKeys)
        mono(s.allowComments, m.allowComments, l.allowComments)
        mono(s.allowTrailingCommas, m.allowTrailingCommas, l.allowTrailingCommas)
        mono(s.allowUnquotedKeys, m.allowUnquotedKeys, l.allowUnquotedKeys)
        mono(s.allowDoctype, m.allowDoctype, l.allowDoctype)
        mono(s.allowUndefinedEntities, m.allowUndefinedEntities, l.allowUndefinedEntities)
        assertTrue(s.maxDepth <= m.maxDepth && m.maxDepth <= l.maxDepth)
    }

    @Test
    fun `of maps each profile to its factory, and DEFAULT is standard`() {
        assertEquals(ParseOptions.strict(), ParseOptions.of(ParseProfile.STRICT))
        assertEquals(ParseOptions.standard(), ParseOptions.of(ParseProfile.STANDARD))
        assertEquals(ParseOptions.lenient(), ParseOptions.of(ParseProfile.LENIENT))
        assertEquals(ParseOptions.standard(), ParseOptions.DEFAULT)
        assertEquals(ParseProfile.STANDARD, ParseOptions.DEFAULT.profile)
    }
}

/**
 * The governance resolver (F23 Phase 2 / §9.1): "strictest wins, never weaken a lock."
 */
class ParseProfilePolicyTest {
    private val L = ParseProfile.LENIENT
    private val S = ParseProfile.STANDARD
    private val X = ParseProfile.STRICT
    private fun r(lock: ParseProfile?, req: ParseProfile?, reject: Boolean = false) =
        ParseProfilePolicy.resolve(lock, req, reject)

    @Test fun `no lock - requested wins, null requested is standard`() {
        assertEquals(S, r(null, null))
        assertEquals(L, r(null, L))
        assertEquals(X, r(null, X))
    }

    @Test fun `lock is a floor - a request at or above it is honoured (raising is safe)`() {
        assertEquals(X, r(X, X))        // equal
        assertEquals(X, r(S, X))        // raise standard→strict allowed
        assertEquals(S, r(L, S))        // raise lenient→standard allowed
    }

    @Test fun `a weaker-than-lock request is clamped up to the lock (fail-safe)`() {
        assertEquals(X, r(X, L))        // guard pinned strict; a lenient request can't weaken it
        assertEquals(X, r(X, S))
        assertEquals(S, r(S, L))
    }

    @Test fun `a guard-locked strict stays strict even with a null request`() {
        assertEquals(X, r(X, null))
    }

    @Test fun `rejectBelowLock makes a weakening request a loud policy violation`() {
        assertThrows<ParseProfilePolicy.PolicyViolation> { r(X, L, reject = true) }
        // but an at-or-above request is still fine under reject mode
        assertEquals(X, r(X, X, reject = true))
        assertEquals(X, r(S, X, reject = true))
    }

    @Test fun `strictness ranking is total and ordered`() {
        assertTrue(ParseProfilePolicy.rank(L) < ParseProfilePolicy.rank(S))
        assertTrue(ParseProfilePolicy.rank(S) < ParseProfilePolicy.rank(X))
    }
}
