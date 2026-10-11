package com.glomidco.utlx.core.format

import org.junit.jupiter.api.Test
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
