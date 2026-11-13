package com.glomidco.utlx.stdlib.math

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals

/**
 * B38 — `isNaN`, `isInfinite`, `isFinite`.
 *
 * Added with the serialiser's refusal to emit a non-finite number, because rejecting without a
 * way to test for the condition leaves the author nowhere to go: there was no predicate for it
 * anywhere in the stdlib, so the only recourse was `toString(x)`, which changes the field's type.
 *
 * With these, a mapping that might overflow can decide for itself:
 *
 * ```utlx
 * { rate: if (isFinite(computed)) computed else null }
 * ```
 *
 * The values can only arise from the transformation's own arithmetic — JSON rejects the bare
 * token on input, and XML and CSV hand back the string `"Infinity"` — so these predicates are
 * about guarding what you compute, not about sanitising what you were given.
 */
class B38NonFinitePredicatesTest {

    private fun call(f: (List<UDM>) -> UDM, v: Any?) =
        ((f(listOf(UDM.Scalar(v)))) as UDM.Scalar).value as Boolean

    private val isNaN = MathFunctions::isNaN
    private val isInfinite = MathFunctions::isInfinite
    private val isFinite = MathFunctions::isFinite

    @Test
    fun `NaN is recognised by exactly one predicate`() {
        assertEquals(true, call(isNaN, Double.NaN))
        assertEquals(false, call(isInfinite, Double.NaN))
        assertEquals(false, call(isFinite, Double.NaN))
    }

    @Test
    fun `both infinities are recognised by exactly one predicate`() {
        for (v in listOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(true, call(isInfinite, v), "isInfinite($v)")
            assertEquals(false, call(isNaN, v), "isNaN($v)")
            assertEquals(false, call(isFinite, v), "isFinite($v)")
        }
    }

    @Test
    fun `ordinary numbers are finite and nothing else`() {
        for (v in listOf(0.0, 1.5, -7.25, 1e308, -1e308)) {
            assertEquals(true, call(isFinite, v), "isFinite($v)")
            assertEquals(false, call(isNaN, v), "isNaN($v)")
            assertEquals(false, call(isInfinite, v), "isInfinite($v)")
        }
    }

    @Test
    fun `exact types are finite by construction and are not converted`() {
        // Going through asNumber() would narrow a BigDecimal to Double, and a large enough one
        // would then answer "infinite" — a finite value reported as non-finite. The predicates
        // classify without converting, which this pins.
        val huge = BigDecimal("1E+400")          // finite, but Double.MAX_VALUE is ~1.8E308
        assertEquals(true, call(isFinite, huge), "a BigDecimal beyond Double's range is finite")
        assertEquals(false, call(isInfinite, huge))
        assertEquals(true, call(isFinite, Long.MAX_VALUE))
        assertEquals(true, call(isFinite, 42))
    }

    @Test
    fun `Float is classified too`() {
        assertEquals(true, call(isNaN, Float.NaN))
        assertEquals(true, call(isInfinite, Float.POSITIVE_INFINITY))
        assertEquals(true, call(isFinite, 1.5f))
    }

    @Test
    fun `a non-numeric argument is refused`() {
        assertThrows<FunctionArgumentException> { isFinite(listOf(UDM.Scalar("x"))) }
        assertThrows<FunctionArgumentException> { isNaN(listOf(UDM.Array(emptyList()))) }
    }

    @Test
    fun `arity is enforced`() {
        assertThrows<FunctionArgumentException> { isFinite(emptyList()) }
        assertThrows<FunctionArgumentException> {
            isFinite(listOf(UDM.Scalar(1.0), UDM.Scalar(2.0)))
        }
    }
}
