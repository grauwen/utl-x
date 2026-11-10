package com.glomidco.utlx.formats.xml

import com.glomidco.utlx.core.interpreter.RuntimeValue
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

/**
 * B38, XML half — a non-finite number must use XSD's lexical form, not `Double.toString()`'s.
 *
 * XSD 1.0 §3.2.5 fixes the lexical space of `xs:double`: the special values are written **`INF`**,
 * **`-INF`** and **`NaN`**. `Infinity` is not in that space, so `<x>Infinity</x>` fails validation
 * against an `xs:double` element.
 *
 * Only the XML half of B38 is settled by a standard. The JSON half has no representation to be
 * correct about — JSON's grammar admits no non-finite literal at all — so it stays open pending a
 * serialisation strictness profile. See `docs/bugs-fixed/B38-*.md`.
 *
 * These tests also pin the **second** thing found here: `serializeRuntimeValue` was a divergent
 * renderer still carrying the `% 1.0 == 0.0` predicate [B37] replaced, so the two entry points of
 * the same serialiser disagreed about large doubles.
 */
class B38XmlNonFiniteTest {

    private fun leaf(value: Any?): String {
        val udm = UDM.Object(mapOf("x" to UDM.Scalar(value)), emptyMap(), "r")
        return XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(udm)
    }

    private fun text(xml: String): String =
        Regex("<x>(.*?)</x>").find(xml)!!.groupValues[1]

    // ── The XSD lexical forms ──

    @Test
    fun `positive infinity is INF`() {
        assertEquals("INF", text(leaf(Double.POSITIVE_INFINITY)))
    }

    @Test
    fun `negative infinity is minus INF`() {
        assertEquals("-INF", text(leaf(Double.NEGATIVE_INFINITY)))
    }

    @Test
    fun `not-a-number is NaN`() {
        assertEquals("NaN", text(leaf(Double.NaN)))
    }

    @Test
    fun `floats use the same forms`() {
        assertEquals("INF", text(leaf(Float.POSITIVE_INFINITY)))
        assertEquals("-INF", text(leaf(Float.NEGATIVE_INFINITY)))
        assertEquals("NaN", text(leaf(Float.NaN)))
    }

    // ── Finite numbers must be untouched: B35 and B37 both live in this method ──

    @Test
    fun `exact types still render as written`() {
        assertEquals("1.50", text(leaf(BigDecimal("1.50"))))
        assertEquals("123456789012345678", text(leaf(123456789012345678L)))
        assertEquals("0.5", text(leaf(BigDecimal("0.5"))))
    }

    @Test
    fun `a large double is still not saturated`() {
        // B37: `% 1.0 == 0.0` was true here and toLong() saturated to Long.MAX_VALUE.
        val out = text(leaf(1e30))
        assertEquals(false, out.contains("9223372036854775807"), out)
        assertEquals(1e30, out.toDouble())
    }

    @Test
    fun `ordinary numbers are unchanged`() {
        assertEquals("12", text(leaf(12L)))
        assertEquals("-7", text(leaf(-7L)))
        assertEquals("0", text(leaf(0L)))
    }

    // ── One renderer, not two ──

    @Test
    fun `the RuntimeValue path agrees with the UDM path on non-finite values`() {
        val viaRuntime = XMLSerializer(prettyPrint = false, includeDeclaration = false)
            .serialize(RuntimeValue.NumberValue(Double.POSITIVE_INFINITY), "x")
        assertEquals("INF", text(viaRuntime))
    }

    @Test
    fun `the RuntimeValue path agrees with the UDM path on a large double`() {
        // The divergence: this path kept B37's replaced predicate and saturated.
        val viaRuntime = XMLSerializer(prettyPrint = false, includeDeclaration = false)
            .serialize(RuntimeValue.NumberValue(1e30), "x")
        assertEquals(text(leaf(1e30)), text(viaRuntime))
        assertEquals(false, viaRuntime.contains("9223372036854775807"), viaRuntime)
    }

    @Test
    fun `the RuntimeValue path still renders integral doubles without a decimal point`() {
        val viaRuntime = XMLSerializer(prettyPrint = false, includeDeclaration = false)
            .serialize(RuntimeValue.NumberValue(12.0), "x")
        assertEquals("12", text(viaRuntime))
    }
}
