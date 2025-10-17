package org.apache.utlx.stdlib.core

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

class CoreFunctionsTest {

    @Test
    fun testIfThenElse() {
        // Test true condition
        val result1 = CoreFunctions.ifThenElse(listOf(
            UDM.Scalar(true),
            UDM.Scalar("expensive"),
            UDM.Scalar("affordable")
        ))
        
        assertTrue(result1 is UDM.Scalar)
        assertEquals("expensive", (result1 as UDM.Scalar).value)
        
        // Test false condition
        val result2 = CoreFunctions.ifThenElse(listOf(
            UDM.Scalar(false),
            UDM.Scalar("expensive"),
            UDM.Scalar("affordable")
        ))
        
        assertTrue(result2 is UDM.Scalar)
        assertEquals("affordable", (result2 as UDM.Scalar).value)
        
        // Test with numeric condition (non-zero is true)
        val result3 = CoreFunctions.ifThenElse(listOf(
            UDM.Scalar(100),
            UDM.Scalar("positive"),
            UDM.Scalar("zero or negative")
        ))
        
        assertTrue(result3 is UDM.Scalar)
        assertEquals("positive", (result3 as UDM.Scalar).value)
        
        // Test with zero (false)
        val result4 = CoreFunctions.ifThenElse(listOf(
            UDM.Scalar(0),
            UDM.Scalar("positive"),
            UDM.Scalar("zero or negative")
        ))
        
        assertTrue(result4 is UDM.Scalar)
        assertEquals("zero or negative", (result4 as UDM.Scalar).value)
    }

    @Test
    fun testCoalesce() {
        // Test with first value non-null
        val result1 = CoreFunctions.coalesce(listOf(
            UDM.Scalar("first"),
            UDM.Scalar("second"),
            UDM.Scalar("third")
        ))
        
        assertTrue(result1 is UDM.Scalar)
        assertEquals("first", (result1 as UDM.Scalar).value)
        
        // Test with first value null
        val result2 = CoreFunctions.coalesce(listOf(
            UDM.Scalar(null),
            UDM.Scalar("second"),
            UDM.Scalar("third")
        ))
        
        assertTrue(result2 is UDM.Scalar)
        assertEquals("second", (result2 as UDM.Scalar).value)
        
        // Test with all values null
        val result3 = CoreFunctions.coalesce(listOf(
            UDM.Scalar(null),
            UDM.Scalar(null),
            UDM.Scalar(null)
        ))
        
        assertTrue(result3 is UDM.Scalar)
        assertEquals(null, (result3 as UDM.Scalar).value)
        
        // Test with empty list
        val result4 = CoreFunctions.coalesce(listOf())
        
        assertTrue(result4 is UDM.Scalar)
        assertEquals(null, (result4 as UDM.Scalar).value)
        
        // Test with objects and arrays (non-null)
        val result5 = CoreFunctions.coalesce(listOf(
            UDM.Scalar(null),
            UDM.Object(mapOf("key" to UDM.Scalar("value"))),
            UDM.Scalar("fallback")
        ))
        
        assertTrue(result5 is UDM.Object)
        assertEquals("value", ((result5 as UDM.Object).properties["key"] as UDM.Scalar).value)
    }

    @Test
    fun testGenerateUuid() {
        val result1 = CoreFunctions.generateUuid(listOf())
        
        assertTrue(result1 is UDM.Scalar)
        val uuid1 = (result1 as UDM.Scalar).value as String
        
        // Check UUID format (36 characters with hyphens)
        assertEquals(36, uuid1.length)
        assertTrue(uuid1.contains("-"))
        assertTrue(uuid1.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        
        // Generate another UUID and ensure they're different
        val result2 = CoreFunctions.generateUuid(listOf())
        val uuid2 = (result2 as UDM.Scalar).value as String
        
        assertNotEquals(uuid1, uuid2)
    }

    @Test
    fun testDefault() {
        // Test with null value
        val result1 = CoreFunctions.default(listOf(
            UDM.Scalar(null),
            UDM.Scalar("default-value")
        ))
        
        assertTrue(result1 is UDM.Scalar)
        assertEquals("default-value", (result1 as UDM.Scalar).value)
        
        // Test with non-null value
        val result2 = CoreFunctions.default(listOf(
            UDM.Scalar("actual-value"),
            UDM.Scalar("default-value")
        ))
        
        assertTrue(result2 is UDM.Scalar)
        assertEquals("actual-value", (result2 as UDM.Scalar).value)
        
        // Test with empty string (should not use default)
        val result3 = CoreFunctions.default(listOf(
            UDM.Scalar(""),
            UDM.Scalar("default-value")
        ))
        
        assertTrue(result3 is UDM.Scalar)
        assertEquals("", (result3 as UDM.Scalar).value)
        
        // Test with zero (should not use default)
        val result4 = CoreFunctions.default(listOf(
            UDM.Scalar(0),
            UDM.Scalar("default-value")
        ))
        
        assertTrue(result4 is UDM.Scalar)
        assertEquals(0, (result4 as UDM.Scalar).value)
        
        // Test with false (should not use default)
        val result5 = CoreFunctions.default(listOf(
            UDM.Scalar(false),
            UDM.Scalar("default-value")
        ))
        
        assertTrue(result5 is UDM.Scalar)
        assertEquals(false, (result5 as UDM.Scalar).value)
        
        // Test with object (should not use default)
        val obj = UDM.Object(mapOf("key" to UDM.Scalar("value")))
        val result6 = CoreFunctions.default(listOf(
            obj,
            UDM.Scalar("default-value")
        ))
        
        assertTrue(result6 is UDM.Object)
        assertEquals(obj, result6)
    }

    @Test
    fun testInvalidArguments() {
        // Test ifThenElse with wrong number of arguments
        assertThrows<FunctionArgumentException> {
            CoreFunctions.ifThenElse(listOf(UDM.Scalar(true), UDM.Scalar("then")))
        }
        
        assertThrows<FunctionArgumentException> {
            CoreFunctions.ifThenElse(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            CoreFunctions.ifThenElse(listOf(UDM.Scalar(true), UDM.Scalar("then"), UDM.Scalar("else"), UDM.Scalar("extra")))
        }
        
        // Test default with wrong number of arguments
        assertThrows<FunctionArgumentException> {
            CoreFunctions.default(listOf(UDM.Scalar("value")))
        }
        
        assertThrows<FunctionArgumentException> {
            CoreFunctions.default(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            CoreFunctions.default(listOf(UDM.Scalar("value"), UDM.Scalar("default"), UDM.Scalar("extra")))
        }
        
        // Test generateUuid with arguments (should take none)
        assertThrows<FunctionArgumentException> {
            CoreFunctions.generateUuid(listOf(UDM.Scalar("arg")))
        }
    }

    @Test
    fun testEdgeCases() {
        // Test ifThenElse with different types
        val objectResult = CoreFunctions.ifThenElse(listOf(
            UDM.Scalar(true),
            UDM.Object(mapOf("success" to UDM.Scalar(true))),
            UDM.Array(listOf(UDM.Scalar("error")))
        ))
        
        assertTrue(objectResult is UDM.Object)
        
        // Test coalesce with mixed types
        val mixedResult = CoreFunctions.coalesce(listOf(
            UDM.Scalar(null),
            UDM.Scalar(null),
            UDM.Scalar(42)
        ))
        
        assertTrue(mixedResult is UDM.Scalar)
        assertEquals(42, (mixedResult as UDM.Scalar).value)
        
        // Test default with complex objects
        val complexObj = UDM.Object(mapOf(
            "nested" to UDM.Object(mapOf(
                "value" to UDM.Scalar("complex")
            ))
        ))
        
        val complexResult = CoreFunctions.default(listOf(
            UDM.Scalar(null),
            complexObj
        ))
        
        assertEquals(complexObj, complexResult)
    }

    @Test
    fun testBooleanConversions() {
        // Test ifThenElse with various boolean conversions
        
        // String "false" should be true (non-empty string)
        val stringFalseResult = CoreFunctions.ifThenElse(listOf(
            UDM.Scalar("false"),
            UDM.Scalar("truthy"),
            UDM.Scalar("falsy")
        ))
        assertEquals("truthy", (stringFalseResult as UDM.Scalar).value)
        
        // Empty string should be false
        val emptyStringResult = CoreFunctions.ifThenElse(listOf(
            UDM.Scalar(""),
            UDM.Scalar("truthy"),
            UDM.Scalar("falsy")
        ))
        assertEquals("falsy", (emptyStringResult as UDM.Scalar).value)
        
        // Null should be false
        val nullResult = CoreFunctions.ifThenElse(listOf(
            UDM.Scalar(null),
            UDM.Scalar("truthy"),
            UDM.Scalar("falsy")
        ))
        assertEquals("falsy", (nullResult as UDM.Scalar).value)
    }
}