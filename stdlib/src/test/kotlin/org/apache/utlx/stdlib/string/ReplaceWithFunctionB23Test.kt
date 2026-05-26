package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * B23: Tests for replaceWithFunction with real lambda arguments.
 */
class ReplaceWithFunctionB23Test {

    private fun lambda(fn: (UDM) -> UDM): UDM.Lambda = UDM.Lambda { args -> fn(args[0]) }

    @Test fun `replaceWithFunction - wrap digits in brackets`() {
        val result = AdvancedRegexFunctions.replaceWithFunction(listOf(
            UDM.Scalar("hello123world456"),
            UDM.Scalar("\\d+"),
            lambda { m -> UDM.Scalar("[${m.asString()}]") }
        ))
        assertEquals("hello[123]world[456]", (result as UDM.Scalar).value)
    }

    @Test fun `replaceWithFunction - uppercase matches`() {
        val result = AdvancedRegexFunctions.replaceWithFunction(listOf(
            UDM.Scalar("hello world"),
            UDM.Scalar("[a-z]+"),
            lambda { m -> UDM.Scalar(m.asString().uppercase()) }
        ))
        assertEquals("HELLO WORLD", (result as UDM.Scalar).value)
    }

    @Test fun `replaceWithFunction - no matches returns original`() {
        val result = AdvancedRegexFunctions.replaceWithFunction(listOf(
            UDM.Scalar("hello world"),
            UDM.Scalar("\\d+"),
            lambda { UDM.Scalar("X") }
        ))
        assertEquals("hello world", (result as UDM.Scalar).value)
    }

    @Test fun `replaceWithFunction - conditional replacement`() {
        val result = AdvancedRegexFunctions.replaceWithFunction(listOf(
            UDM.Scalar("MAT-100 CUST-42"),
            UDM.Scalar("[A-Z]+-\\d+"),
            lambda { m ->
                val s = m.asString()
                if (s.startsWith("MAT-")) UDM.Scalar("Material:${s.removePrefix("MAT-")}")
                else if (s.startsWith("CUST-")) UDM.Scalar("Customer:${s.removePrefix("CUST-")}")
                else m
            }
        ))
        assertEquals("Material:100 Customer:42", (result as UDM.Scalar).value)
    }

    @Test fun `replaceWithFunction - empty string`() {
        val result = AdvancedRegexFunctions.replaceWithFunction(listOf(
            UDM.Scalar(""),
            UDM.Scalar("\\d+"),
            lambda { UDM.Scalar("X") }
        ))
        assertEquals("", (result as UDM.Scalar).value)
    }

    @Test fun `replaceWithFunction - single character matches`() {
        val result = AdvancedRegexFunctions.replaceWithFunction(listOf(
            UDM.Scalar("abc"),
            UDM.Scalar("."),
            lambda { m -> UDM.Scalar("${m.asString()}${m.asString()}") }
        ))
        assertEquals("aabbcc", (result as UDM.Scalar).value)
    }

    @Test fun `replaceWithFunction - missing args throws`() {
        assertThrows<IllegalArgumentException> {
            AdvancedRegexFunctions.replaceWithFunction(listOf(UDM.Scalar("text"), UDM.Scalar("\\d+")))
        }
    }

    @Test fun `replaceWithFunction - not lambda throws`() {
        assertThrows<IllegalArgumentException> {
            AdvancedRegexFunctions.replaceWithFunction(listOf(
                UDM.Scalar("text"), UDM.Scalar("\\d+"), UDM.Scalar("not a function")
            ))
        }
    }

    @Test fun `replaceWithFunction - invalid regex throws`() {
        assertThrows<IllegalArgumentException> {
            AdvancedRegexFunctions.replaceWithFunction(listOf(
                UDM.Scalar("text"), UDM.Scalar("[invalid"), lambda { it }
            ))
        }
    }
}
