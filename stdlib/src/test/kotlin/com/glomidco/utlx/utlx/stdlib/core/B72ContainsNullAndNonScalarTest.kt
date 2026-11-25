package com.glomidco.utlx.stdlib.core

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * B72: `contains` searched for "" whenever the value to look for was null or not a single value.
 *
 * Root cause: the string and object branches read the search value as
 * `(element as? UDM.Scalar)?.value?.toString() ?: ""`. A null has no text and a list or object is
 * not a scalar, so both fell through to the empty string — and every string contains "". The
 * answer was therefore `true` for any null or non-scalar, regardless of the text searched.
 *
 * It mattered because of a common pattern, used at four call sites in three example scripts:
 * `contains("TX,OK,NM,AZ", $input.warehouse.location.state)` classified a warehouse with NO state
 * as SOUTHWEST.
 *
 * The tests also pin the two behaviours that were deliberately kept — numbers compared as text,
 * arrays compared strictly — so a later "fix" of those is a visible decision, not an accident.
 */
class B72ContainsNullAndNonScalarTest {

    private fun contains(a: UDM, b: UDM) = (CoreFunctions.contains(listOf(a, b)) as UDM.Scalar).value

    private val nul = UDM.Scalar(null)
    private val list = UDM.Array(listOf(UDM.Scalar(1)))

    @Test
    fun `a null search value finds nothing in a string`() {
        assertEquals(false, contains(UDM.Scalar("abc"), nul))
        // The case that made it matter: a missing state is not one of these states.
        assertEquals(false, contains(UDM.Scalar("TX,OK,NM,AZ"), nul))
    }

    @Test
    fun `nothing is found in a null`() {
        // Previously null became "" here too, so contains(null, "") was true.
        assertEquals(false, contains(nul, UDM.Scalar("")))
        assertEquals(false, contains(nul, UDM.Scalar("a")))
    }

    @Test
    fun `a null key is not a key of any object`() {
        val obj = UDM.Object(mapOf("" to UDM.Scalar(1), "a" to UDM.Scalar(2)))
        // Vacuity guard: the object HAS an empty-string key, which is exactly what null used
        // to be turned into — so before the fix this returned true.
        assertEquals(true, contains(obj, UDM.Scalar("")))
        assertEquals(false, contains(obj, nul))
    }

    @Test
    fun `searching text or keys for a list or object is a script error`() {
        assertFailsWith<FunctionArgumentException> { contains(UDM.Scalar("abc"), list) }
        assertFailsWith<FunctionArgumentException> {
            contains(UDM.Scalar("abc"), UDM.Object(mapOf("x" to UDM.Scalar(1))))
        }
        assertFailsWith<FunctionArgumentException> {
            contains(UDM.Object(mapOf("a" to UDM.Scalar(1))), list)
        }
    }

    @Test
    fun `ordinary searches are unchanged`() {
        assertEquals(true, contains(UDM.Scalar("TX,OK,NM,AZ"), UDM.Scalar("TX")))
        assertEquals(false, contains(UDM.Scalar("TX,OK,NM,AZ"), UDM.Scalar("CA")))
        // The empty string is still found in every string, as in Java, JavaScript and Python:
        // that is a real value, not a missing one.
        assertEquals(true, contains(UDM.Scalar("abc"), UDM.Scalar("")))
    }

    @Test
    fun `kept on purpose - numbers are compared as text`() {
        // So digits read from XML or CSV (text) and from JSON (numbers) give the same answer.
        assertEquals(true, contains(UDM.Scalar(12345), UDM.Scalar(23)))
        assertEquals(true, contains(UDM.Scalar("12345"), UDM.Scalar(23)))
    }

    @Test
    fun `kept on purpose - arrays compare elements strictly, and may hold null or lists`() {
        val numbers = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        assertEquals(false, contains(numbers, UDM.Scalar("2")))
        assertEquals(true, contains(UDM.Array(listOf(UDM.Scalar("a"), nul)), nul))
        assertEquals(true, contains(UDM.Array(listOf(list)), list))
    }
}
