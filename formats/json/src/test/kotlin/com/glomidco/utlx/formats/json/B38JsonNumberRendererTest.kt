package com.glomidco.utlx.formats.json

import com.glomidco.utlx.core.interpreter.RuntimeValue
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * B38 / B37 — the JSON serialiser had two number renderers, and only one of them had been fixed.
 *
 * `serializeScalar` (the UDM path, which every production caller reaches) uses the Long round-trip
 * B37 introduced. `serializeRuntimeValue` kept `% 1.0 == 0.0`, which is true for every large double,
 * so `1e30` saturated to `Long.MAX_VALUE` there while the UDM path rendered it correctly. B37's note
 * said the predicate was "now shared"; this overload was missed.
 *
 * The non-finite question itself — JSON has no literal for `Infinity` or `NaN` — is **still open**,
 * and the test below pins the current behaviour so that changing it is a deliberate act rather than
 * a surprise. See `docs/bugs-fixed/B38-*.md`.
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
    fun `non-finite JSON output is unchanged and still invalid, pending the B38 decision`() {
        // NOT an endorsement. `Infinity` and `NaN` are not in JSON's grammar and UTL-X's own parser
        // rejects them; this records the behaviour so the eventual fix is a visible change.
        assertEquals("Infinity", viaUdm(Double.POSITIVE_INFINITY))
        assertEquals("NaN", viaUdm(Double.NaN))
        assertEquals(viaUdm(Double.POSITIVE_INFINITY), viaRuntime(Double.POSITIVE_INFINITY))
    }
}
