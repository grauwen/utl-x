package com.glomidco.utlx.formats.json

import com.glomidco.utlx.core.interpreter.RuntimeValue
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * B38 / B37 — the JSON serialiser had two number renderers, and only one of them had been fixed.
 *
 * `serializeScalar` (the UDM path, which every production caller reaches) uses the Long round-trip
 * B37 introduced. `serializeRuntimeValue` kept `% 1.0 == 0.0`, which is true for every large double,
 * so `1e30` saturated to `Long.MAX_VALUE` there while the UDM path rendered it correctly. B37's note
 * said the predicate was "now shared"; this overload was missed.
 *
 * The non-finite question itself — JSON has no literal for `Infinity` or `NaN` — was settled in
 * 2026-11-13: the serialiser **refuses** such a value rather than emitting output a strict parser
 * rejects, and `isFinite`/`isNaN`/`isInfinite` were added so a mapping can test for it first.
 * See `docs/bugs-fixed/B38-*.md`.
 */
class B38JsonNumberRendererTest {

    private fun viaUdm(value: Any?): String {
        val sw = java.io.StringWriter()
        JSONSerializer(prettyPrint = false).serialize(UDM.Scalar(value), sw)
        return sw.toString()
    }

    private fun viaRuntime(value: Double): String {
        val sw = java.io.StringWriter()
        JSONSerializer(prettyPrint = false).serialize(RuntimeValue.NumberValue(value), sw)
        return sw.toString()
    }

    @Test
    fun `a large double is not saturated on either path`() {
        assertEquals(false, viaUdm(1e30).contains("9223372036854775807"), viaUdm(1e30))
        assertEquals(false, viaRuntime(1e30).contains("9223372036854775807"), viaRuntime(1e30))
    }

    @Test
    fun `the two paths agree`() {
        for (d in listOf(1e30, 1.5, 12.0, 0.0, -7.0, 1e-30)) {
            assertEquals(viaUdm(d), viaRuntime(d), "disagreed on $d")
        }
    }

    @Test
    fun `integral doubles still render without a decimal point`() {
        assertEquals("12", viaRuntime(12.0))
        assertEquals("12", viaUdm(12.0))
    }

    @Test
    fun `a non-finite number is refused rather than emitted`() {
        // The decision B38 was left open on. `Infinity` and `NaN` are not in JSON's grammar —
        // UTL-X's own parser rejects them on the way in — so emitting them produced output that a
        // strict consumer cannot read, *after* the transformation reported success. Rejecting is
        // the only option that does not lie: `null` makes the three values indistinguishable from
        // a legitimate null, and a string changes the field's type.
        for (value in listOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
            assertFailsWith<IllegalArgumentException>("$value should be refused") { viaUdm(value) }
        }
    }

    @Test
    fun `both renderers refuse — there is only one number path`() {
        // The header above records why this matters: B37 fixed one of two renderers and left the
        // other saturating large doubles. They were later consolidated, `serializeRuntimeValue`
        // delegating to `serializeScalar`, so a guard in one covers both. This asserts that is
        // still true rather than assuming it.
        assertFailsWith<IllegalArgumentException> { viaUdm(Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { viaRuntime(Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { viaRuntime(Double.NaN) }
    }

    @Test
    fun `the message names a way out`() {
        // The error necessarily fires at serialisation, far from the arithmetic that produced the
        // value, so it has to carry the remedy or the author is stuck.
        val message = runCatching { viaUdm(Double.NaN) }.exceptionOrNull()?.message ?: ""
        assertTrue("isFinite" in message, "should point at the guard: $message")
        assertTrue("toString" in message, "should offer the text escape hatch: $message")
    }

    @Test
    fun `finite values are untouched`() {
        // The guard costs two type checks; nothing else about rendering changes.
        assertEquals("1.5", viaUdm(1.5))
        assertEquals("42", viaUdm(42L))
        assertEquals("12", viaUdm(12.0))
        assertEquals("0", viaUdm(0.0))
        assertEquals("-1.5", viaUdm(-1.5))
    }
}
