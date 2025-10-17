package org.apache.utlx.stdlib.json

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JSONCanonicalizationFunctionsTest {

    @Test
    fun testCanonicalizeJSON() {
        // Test basic object canonicalization (keys should be sorted)
        val input = UDM.Object(mapOf(
            "b" to UDM.Scalar(2),
            "a" to UDM.Scalar(1),
            "c" to UDM.Scalar(3)
        ))
        
        val result = JSONCanonicalizationFunctions.canonicalizeJSON(listOf(input))
        assertTrue(result is UDM.Scalar)
        
        val canonical = (result as UDM.Scalar).value as String
        assertEquals("{\"a\":1,\"b\":2,\"c\":3}", canonical)
    }
    
    @Test
    fun testCanonicalizeJSONWithNestedObjects() {
        val input = UDM.Object(mapOf(
            "outer" to UDM.Object(mapOf(
                "z" to UDM.Scalar("last"),
                "a" to UDM.Scalar("first")
            )),
            "simple" to UDM.Scalar("value")
        ))
        
        val result = JSONCanonicalizationFunctions.canonicalizeJSON(listOf(input))
        assertTrue(result is UDM.Scalar)
        
        val canonical = (result as UDM.Scalar).value as String
        assertTrue(canonical.contains("\"a\":\"first\""))
        assertTrue(canonical.contains("\"z\":\"last\""))
        assertTrue(canonical.indexOf("\"outer\"") < canonical.indexOf("\"simple\""))
    }
    
    @Test
    fun testCanonicalizeJSONWithArrays() {
        val input = UDM.Object(mapOf(
            "array" to UDM.Array(listOf(
                UDM.Scalar(3),
                UDM.Scalar(1),
                UDM.Scalar(2)
            )),
            "name" to UDM.Scalar("test")
        ))
        
        val result = JSONCanonicalizationFunctions.canonicalizeJSON(listOf(input))
        assertTrue(result is UDM.Scalar)
        
        val canonical = (result as UDM.Scalar).value as String
        // Arrays should preserve order, objects should be sorted
        assertTrue(canonical.contains("[3,1,2]"))
        assertTrue(canonical.contains("\"array\""))
        assertTrue(canonical.contains("\"name\""))
    }
    
    @Test
    fun testJCSAlias() {
        val input = UDM.Object(mapOf(
            "b" to UDM.Scalar(2),
            "a" to UDM.Scalar(1)
        ))
        
        val canonicalResult = JSONCanonicalizationFunctions.canonicalizeJSON(listOf(input))
        val jcsResult = JSONCanonicalizationFunctions.jcs(listOf(input))
        
        assertEquals(canonicalResult, jcsResult)
    }
    
    @Test
    fun testCanonicalJSONHash() {
        val input = UDM.Object(mapOf(
            "id" to UDM.Scalar(123),
            "name" to UDM.Scalar("test")
        ))
        
        // Test with default algorithm (SHA-256)
        val hashResult = JSONCanonicalizationFunctions.canonicalJSONHash(listOf(input))
        assertTrue(hashResult is UDM.Scalar)
        val hash = (hashResult as UDM.Scalar).value as String
        assertTrue(hash.length == 64) // SHA-256 produces 64-character hex string
        assertTrue(hash.matches(Regex("[a-f0-9]{64}")))
        
        // Test with explicit algorithm
        val hashResult2 = JSONCanonicalizationFunctions.canonicalJSONHash(listOf(input, UDM.Scalar("SHA-256")))
        assertEquals(hash, (hashResult2 as UDM.Scalar).value)
    }
    
    @Test
    fun testJSONEquals() {
        val obj1 = UDM.Object(mapOf(
            "b" to UDM.Scalar(2),
            "a" to UDM.Scalar(1)
        ))
        
        val obj2 = UDM.Object(mapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2)
        ))
        
        val obj3 = UDM.Object(mapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(3)
        ))
        
        // Same content, different order - should be equal
        val result1 = JSONCanonicalizationFunctions.jsonEquals(listOf(obj1, obj2))
        assertTrue(result1 is UDM.Scalar)
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        // Different content - should not be equal
        val result2 = JSONCanonicalizationFunctions.jsonEquals(listOf(obj1, obj3))
        assertTrue(result2 is UDM.Scalar)
        assertEquals(false, (result2 as UDM.Scalar).value)
    }
    
    @Test
    fun testIsCanonicalJSON() {
        // Valid canonical JSON (no whitespace, sorted keys)
        val validCanonical = UDM.Scalar("{\"a\":1,\"b\":2}")
        val result1 = JSONCanonicalizationFunctions.isCanonicalJSON(listOf(validCanonical))
        assertTrue(result1 is UDM.Scalar)
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        // Invalid canonical JSON (has whitespace)
        val invalidCanonical = UDM.Scalar("{ \"a\": 1, \"b\": 2 }")
        val result2 = JSONCanonicalizationFunctions.isCanonicalJSON(listOf(invalidCanonical))
        assertTrue(result2 is UDM.Scalar)
        assertEquals(false, (result2 as UDM.Scalar).value)
    }
    
    @Test
    fun testCanonicalJSONSize() {
        val input = UDM.Object(mapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2)
        ))
        
        val result = JSONCanonicalizationFunctions.canonicalJSONSize(listOf(input))
        assertTrue(result is UDM.Scalar)
        
        val size = (result as UDM.Scalar).value as Double
        assertTrue(size > 0)
        // The canonical form should be: {"a":1,"b":2}
        assertEquals(13.0, size)
    }
    
    @Test
    fun testNumberCanonicalization() {
        // Test integer values
        val intInput = UDM.Object(mapOf("num" to UDM.Scalar(42)))
        val intResult = JSONCanonicalizationFunctions.canonicalizeJSON(listOf(intInput))
        val intCanonical = (intResult as UDM.Scalar).value as String
        assertTrue(intCanonical.contains("42"))
        
        // Test decimal values
        val decimalInput = UDM.Object(mapOf("num" to UDM.Scalar(3.14)))
        val decimalResult = JSONCanonicalizationFunctions.canonicalizeJSON(listOf(decimalInput))
        val decimalCanonical = (decimalResult as UDM.Scalar).value as String
        assertTrue(decimalCanonical.contains("3.14"))
    }
    
    @Test
    fun testBooleanAndNullCanonicalization() {
        val input = UDM.Object(mapOf(
            "bool_true" to UDM.Scalar(true),
            "bool_false" to UDM.Scalar(false),
            "null_val" to UDM.Scalar(null)
        ))
        
        val result = JSONCanonicalizationFunctions.canonicalizeJSON(listOf(input))
        val canonical = (result as UDM.Scalar).value as String
        
        assertTrue(canonical.contains("true"))
        assertTrue(canonical.contains("false"))
        assertTrue(canonical.contains("null"))
    }
    
    @Test
    fun testStringEscaping() {
        val input = UDM.Object(mapOf(
            "quote" to UDM.Scalar("He said \"Hello\""),
            "newline" to UDM.Scalar("Line 1\nLine 2"),
            "backslash" to UDM.Scalar("Path\\to\\file")
        ))
        
        val result = JSONCanonicalizationFunctions.canonicalizeJSON(listOf(input))
        val canonical = (result as UDM.Scalar).value as String
        
        assertTrue(canonical.contains("\\\""))
        assertTrue(canonical.contains("\\n"))
        assertTrue(canonical.contains("\\\\"))
    }
    
    @Test
    fun testInvalidArguments() {
        // Test with wrong number of arguments
        assertThrows<FunctionArgumentException> {
            JSONCanonicalizationFunctions.canonicalizeJSON(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            JSONCanonicalizationFunctions.jsonEquals(listOf(UDM.Scalar("test")))
        }
        
        assertThrows<FunctionArgumentException> {
            JSONCanonicalizationFunctions.canonicalJSONHash(listOf())
        }
    }
    
    @Test
    fun testDeterministicHashing() {
        val input = UDM.Object(mapOf(
            "data" to UDM.Scalar("test"),
            "timestamp" to UDM.Scalar(1234567890)
        ))
        
        // Hash should be deterministic
        val hash1 = JSONCanonicalizationFunctions.canonicalJSONHash(listOf(input))
        val hash2 = JSONCanonicalizationFunctions.canonicalJSONHash(listOf(input))
        
        assertEquals(hash1, hash2)
        
        // Different input should produce different hash
        val differentInput = UDM.Object(mapOf(
            "data" to UDM.Scalar("different"),
            "timestamp" to UDM.Scalar(1234567890)
        ))
        
        val differentHash = JSONCanonicalizationFunctions.canonicalJSONHash(listOf(differentInput))
        assertNotEquals(hash1, differentHash)
    }
}