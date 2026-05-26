package org.apache.utlx.stdlib.json

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B22: Tests for isCanonicalJSON with both string and UDM.Object input.
 * Before B22, isCanonicalJSON always returned false for UDM.Object input.
 */
class JSONB22IsCanonicalTest {

    // ========== String input (original path — was working for string validation) ==========

    @Test
    fun `isCanonicalJSON true for valid canonical string`() {
        val input = UDM.Scalar("""{"a":1,"b":2}""")
        val result = JSONCanonicalizationFunctions.isCanonicalJSON(listOf(input))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test
    fun `isCanonicalJSON false for string with whitespace`() {
        val input = UDM.Scalar("""{ "a": 1, "b": 2 }""")
        val result = JSONCanonicalizationFunctions.isCanonicalJSON(listOf(input))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun `isCanonicalJSON false for string with leading whitespace`() {
        val input = UDM.Scalar("""  {"a":1}""")
        val result = JSONCanonicalizationFunctions.isCanonicalJSON(listOf(input))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun `isCanonicalJSON false for string with trailing whitespace`() {
        val input = UDM.Scalar("""{"a":1}  """)
        val result = JSONCanonicalizationFunctions.isCanonicalJSON(listOf(input))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun `isCanonicalJSON true for compact array string`() {
        val input = UDM.Scalar("""[1,2,3]""")
        val result = JSONCanonicalizationFunctions.isCanonicalJSON(listOf(input))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test
    fun `isCanonicalJSON true for string scalar`() {
        val input = UDM.Scalar(""""hello"""")
        val result = JSONCanonicalizationFunctions.isCanonicalJSON(listOf(input))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    // ========== UDM.Object input (B22 fix — was always returning false) ==========

    @Test
    fun `isCanonicalJSON true for UDM Object`() {
        val input = UDM.Object.of(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2)
        )
        val result = JSONCanonicalizationFunctions.isCanonicalJSON(listOf(input))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test
    fun `isCanonicalJSON true for UDM Array`() {
        val input = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        val result = JSONCanonicalizationFunctions.isCanonicalJSON(listOf(input))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test
    fun `isCanonicalJSON true for nested UDM Object`() {
        val input = UDM.Object.of(
            "a" to UDM.Scalar(true),
            "z" to UDM.Object.of(
                "x" to UDM.Scalar(1),
                "y" to UDM.Scalar(2)
            )
        )
        val result = JSONCanonicalizationFunctions.isCanonicalJSON(listOf(input))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    // ========== Consistency: canonicalizeJSON + isCanonicalJSON ==========

    @Test
    fun `canonicalized output is canonical`() {
        val input = UDM.Object.of(
            "z" to UDM.Scalar(3),
            "a" to UDM.Scalar(1),
            "m" to UDM.Scalar(2)
        )
        val canonical = JSONCanonicalizationFunctions.canonicalizeJSON(listOf(input))
        assertTrue(canonical is UDM.Scalar)

        // The canonical output as a string should pass isCanonicalJSON
        val isCanonical = JSONCanonicalizationFunctions.isCanonicalJSON(listOf(canonical))
        assertEquals(true, (isCanonical as UDM.Scalar).value,
            "Output of canonicalizeJSON should always be canonical")
    }
}
