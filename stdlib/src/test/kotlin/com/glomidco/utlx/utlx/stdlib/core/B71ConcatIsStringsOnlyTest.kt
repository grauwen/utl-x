package com.glomidco.utlx.stdlib.core

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.FunctionArgumentException
import com.glomidco.utlx.stdlib.array.MoreArrayFunctions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * B71: `concat` was four unrelated operations behind one name.
 *
 * It dispatched on the first argument's type, so it was string-concatenation, array-concatenation,
 * object-merge **and** number-addition — and the last of those meant `concat(42, 58)` returned
 * `100`. Three of the four already had a proper name (`merge`, `sum`, and the unregistered
 * array implementation that is now `arrayConcat`), and all 70 call sites in the repository used
 * the string form, so the overloading bought nothing and cost a surprising result.
 *
 * The root cause of the *catalogue* symptom is [B69]: two further objects annotated a method named
 * `concat` without registering it, so `utlx functions info concat` answered with three different
 * signatures, only one of which described what ran.
 */
class B71ConcatIsStringsOnlyTest {

    private fun concat(vararg args: Any?) =
        CoreFunctions.concat(args.map { UDM.Scalar(it) })

    @Test
    fun `strings concatenate, which is what every call site does`() {
        assertEquals("Hello World", (concat("Hello", " ", "World") as UDM.Scalar).value)
        assertEquals("abc", (concat("a", "b", "c") as UDM.Scalar).value)
    }

    @Test
    fun `a single argument is rendered, not passed through`() {
        // It used to `return args[0]` unchanged, so concat([1,2]) handed back an ARRAY from a
        // function whose declared return type is a string.
        assertEquals("42", (concat(42) as UDM.Scalar).value)
    }

    @Test
    fun `numbers are rendered as text, not added`() {
        // The regression this test exists for: concat(42, 58) == 100.
        assertEquals("4258", (concat(42, 58) as UDM.Scalar).value)
    }

    @Test
    fun `exact numerics keep their scale, as they do with the plus operator`() {
        // B35: rendering goes through asString(), the same path `+` uses. A naive
        // `value.toString()` on a Double would render 100.10 as "100.1".
        val exact = UDM.Scalar(java.math.BigDecimal("100.10"))
        assertEquals("x100.10", (CoreFunctions.concat(listOf(UDM.Scalar("x"), exact)) as UDM.Scalar).value)
    }

    @Test
    fun `an array is refused, naming the function that does the job`() {
        val e = assertFailsWith<FunctionArgumentException> {
            CoreFunctions.concat(listOf(UDM.Array(listOf(UDM.Scalar(1))), UDM.Array(listOf(UDM.Scalar(2)))))
        }
        // The diagnosis must carry the alternative: this is the operation concat used to perform,
        // so a bare "wrong type" would strand whoever relied on it.
        assertTrue(e.message!!.contains("arrayConcat"), "should point at arrayConcat: ${e.message}")
    }

    @Test
    fun `an object is refused, pointing at merge`() {
        val e = assertFailsWith<FunctionArgumentException> {
            CoreFunctions.concat(listOf(UDM.Object(linkedMapOf("a" to UDM.Scalar(1))),
                                        UDM.Object(linkedMapOf("b" to UDM.Scalar(2)))))
        }
        assertTrue(e.message!!.contains("merge"), "should point at merge: ${e.message}")
    }

    @Test
    fun `arrayConcat keeps duplicates, which is what distinguishes it from union`() {
        // If these ever agree, one of them is wrong: union de-duplicates by definition, and the
        // whole reason array-concat needed its own name is that union could not serve.
        val result = MoreArrayFunctions.arrayConcat(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
            UDM.Array(listOf(UDM.Scalar(2), UDM.Scalar(3)))
        )) as UDM.Array
        assertEquals(4, result.elements.size, "duplicates must survive: $result")
        assertEquals(listOf(1, 2, 2, 3), result.elements.map { (it as UDM.Scalar).value })
    }
}
