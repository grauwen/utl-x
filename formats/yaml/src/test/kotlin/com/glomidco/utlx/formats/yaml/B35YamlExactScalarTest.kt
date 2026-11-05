package com.glomidco.utlx.formats.yaml

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B35 — YAML is the one format whose scalars are constructed by a third-party library, so its fix
 * is a *subclass* of SnakeYAML's constructor rather than a call to the shared helper. That makes it
 * the riskiest part of the change and the part most in need of its own tests.
 *
 * The override takes over exactly one case — the plain decimal form, which SnakeYAML returns as a
 * `Double` — and delegates everything else it calls a float to SnakeYAML's own construct. The
 * delegation is the half worth guarding: YAML floats also cover `.inf`, `-.inf`, `.nan`, underscore
 * grouping and YAML 1.1 sexagesimals, none of which this project should be reimplementing. A later
 * edit that replaces the float construct outright instead of delegating would break those silently,
 * and these tests are what would catch it.
 *
 * Integers were already exact before the fix, because SnakeYAML returns `Long`/`BigInteger` for
 * them — which is why YAML preserved int64 when CSV and XML did not. That is asserted too, so a
 * regression in the *other* direction is also visible.
 */
class B35YamlExactScalarTest {

    private fun scalarAt(yaml: String, key: String): Any? {
        val obj = YAMLParser.parseYAML(yaml) as UDM.Object
        return (obj.properties[key] as UDM.Scalar).value
    }

    // ── The case the override takes over ──

    @Test
    fun `a plain decimal is exact and keeps its scale`() {
        assertEquals(BigDecimal("19.99"), scalarAt("price: 19.99", "price"))
        val trailing = scalarAt("total: 100.10", "total")
        assertEquals(BigDecimal("100.10"), trailing)
        assertEquals(2, (trailing as BigDecimal).scale())
    }

    @Test
    fun `a decimal beyond Double precision is exact`() {
        assertEquals(
            BigDecimal("12345678901234567.89"),
            scalarAt("v: 12345678901234567.89", "v")
        )
    }

    @Test
    fun `a negative decimal is exact`() {
        assertEquals(BigDecimal("-0.50"), scalarAt("v: -0.50", "v"))
    }

    // ── Integers: already exact, must stay so ──

    @Test
    fun `an integer above 2^53 is preserved`() {
        assertEquals(9007199254740993L, scalarAt("id: 9007199254740993", "id"))
    }

    @Test
    fun `a small integer stays integral rather than becoming a decimal`() {
        // SnakeYAML hands back an Integer for a small integer and a Long only when it needs one,
        // so the assertion is on integrality rather than on a particular box. (JSON produces a Long
        // here, so the two formats differ in TYPE while agreeing in VALUE — harmless, because
        // comparison canonicalises, but worth pinning so it is a known difference.)
        val v = scalarAt("n: 42", "n")
        assertTrue(v is Int || v is Long, "expected an integral box, got ${v?.javaClass}")
        assertEquals(42L, (v as Number).toLong())
    }

    // ── Delegation: everything else SnakeYAML calls a float stays on its own path ──

    @Test
    fun `positive and negative infinity delegate to SnakeYAML`() {
        assertEquals(Double.POSITIVE_INFINITY, scalarAt("v: .inf", "v"))
        assertEquals(Double.NEGATIVE_INFINITY, scalarAt("v: -.inf", "v"))
    }

    @Test
    fun `not-a-number delegates to SnakeYAML`() {
        val v = scalarAt("v: .nan", "v")
        assertTrue(v is Double && v.isNaN(), "expected NaN, got $v")
    }

    @Test
    fun `exponent notation delegates and stays a Double`() {
        assertEquals(1500.0, scalarAt("v: 1.5e3", "v"))
        assertTrue(scalarAt("v: 2.5E-1", "v") is Double)
    }

    @Test
    fun `underscore grouping delegates — this project does not reimplement it`() {
        assertEquals(1000.5, scalarAt("v: 1_000.5", "v"))
    }

    @Test
    fun `YAML 1_1 sexagesimal delegates`() {
        // 1:30 is 90 in YAML 1.1. Nothing in the exactness rule should recognise this form.
        assertEquals(90L, (scalarAt("v: 1:30", "v") as Number).toLong())
    }

    @Test
    fun `a quoted number stays a string`() {
        assertEquals("19.99", scalarAt("price: \"19.99\"", "price"))
    }

    // ── Round trip through the serialiser, which needed no change ──

    @Test
    fun `a preserved decimal serialises as written`() {
        val udm = YAMLParser.parseYAML("total: 100.10")
        val out = YAMLSerializer().serialize(udm)
        assertTrue(out.contains("100.10"), "trailing zero lost on output: $out")
    }
}
