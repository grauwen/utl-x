package org.apache.utlx.stdlib.encoding

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncodingHashFunctionsTest {
    
    @Test
    fun testMd5() {
        val result = EncodingFunctions.md5(listOf(UDM.Scalar("hello")))
        assertTrue(result is UDM.Scalar)
        assertEquals("5d41402abc4b2a76b9719d911017c592", (result as UDM.Scalar).value)
        
        // Test empty string
        val emptyResult = EncodingFunctions.md5(listOf(UDM.Scalar("")))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", (emptyResult as UDM.Scalar).value)
    }
    
    @Test
    fun testSha1() {
        val result = EncodingFunctions.sha1(listOf(UDM.Scalar("hello")))
        assertTrue(result is UDM.Scalar)
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", (result as UDM.Scalar).value)
    }
    
    @Test
    fun testSha256() {
        val result = EncodingFunctions.sha256(listOf(UDM.Scalar("hello")))
        assertTrue(result is UDM.Scalar)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", (result as UDM.Scalar).value)
    }
    
    @Test
    fun testSha512() {
        val result = EncodingFunctions.sha512(listOf(UDM.Scalar("hello")))
        assertTrue(result is UDM.Scalar)
        val hash = (result as UDM.Scalar).value as String
        assertTrue(hash.length == 128) // SHA-512 produces 128 hex characters
        assertTrue(hash.matches(Regex("[a-f0-9]{128}")))
    }
    
    @Test
    fun testBase64Encode() {
        val result = EncodingFunctions.base64Encode(listOf(UDM.Scalar("hello")))
        assertTrue(result is UDM.Scalar)
        assertEquals("aGVsbG8=", (result as UDM.Scalar).value)
        
        // Test empty string
        val emptyResult = EncodingFunctions.base64Encode(listOf(UDM.Scalar("")))
        assertEquals("", (emptyResult as UDM.Scalar).value)
    }
    
    @Test
    fun testBase64Decode() {
        val result = EncodingFunctions.base64Decode(listOf(UDM.Scalar("aGVsbG8=")))
        assertTrue(result is UDM.Scalar)
        assertEquals("hello", (result as UDM.Scalar).value)
        
        // Test empty string
        val emptyResult = EncodingFunctions.base64Decode(listOf(UDM.Scalar("")))
        assertEquals("", (emptyResult as UDM.Scalar).value)
    }
    
    @Test
    fun testUrlEncode() {
        val result = EncodingFunctions.urlEncode(listOf(UDM.Scalar("hello world")))
        assertTrue(result is UDM.Scalar)
        assertEquals("hello+world", (result as UDM.Scalar).value)  // URLEncoder uses + for spaces

        // Test special characters
        val specialResult = EncodingFunctions.urlEncode(listOf(UDM.Scalar("hello&world")))
        assertEquals("hello%26world", (specialResult as UDM.Scalar).value)
    }

    @Test
    fun testUrlDecode() {
        val result = EncodingFunctions.urlDecode(listOf(UDM.Scalar("hello+world")))
        assertTrue(result is UDM.Scalar)
        assertEquals("hello world", (result as UDM.Scalar).value)

        // Test special characters
        val specialResult = EncodingFunctions.urlDecode(listOf(UDM.Scalar("hello%26world")))
        assertEquals("hello&world", (specialResult as UDM.Scalar).value)
    }

    @Test
    fun testHmac() {
        val result = EncodingFunctions.hmac(listOf(UDM.Scalar("message"), UDM.Scalar("key"), UDM.Scalar("HmacSHA256")))
        assertTrue(result is UDM.Scalar)
        val hmac = (result as UDM.Scalar).value as String
        assertTrue(hmac.length == 64) // SHA256 HMAC produces 64 hex characters
        assertTrue(hmac.matches(Regex("[a-f0-9]{64}")))
    }
    
    @Test
    fun testRoundTripEncoding() {
        val original = "Hello, World! 🌍"
        
        // Base64 round trip
        val base64Encoded = EncodingFunctions.base64Encode(listOf(UDM.Scalar(original)))
        val base64Decoded = EncodingFunctions.base64Decode(listOf(base64Encoded))
        assertEquals(original, (base64Decoded as UDM.Scalar).value)
        
        // URL encoding round trip
        val urlEncoded = EncodingFunctions.urlEncode(listOf(UDM.Scalar(original)))
        val urlDecoded = EncodingFunctions.urlDecode(listOf(urlEncoded))
        assertEquals(original, (urlDecoded as UDM.Scalar).value)
    }
    
    @Test
    fun testHashConsistency() {
        val input = "test data"
        
        // Same input should produce same hash
        val hash1 = EncodingFunctions.sha256(listOf(UDM.Scalar(input)))
        val hash2 = EncodingFunctions.sha256(listOf(UDM.Scalar(input)))
        assertEquals((hash1 as UDM.Scalar).value, (hash2 as UDM.Scalar).value)
        
        // Different input should produce different hash
        val hash3 = EncodingFunctions.sha256(listOf(UDM.Scalar("different data")))
        assertTrue((hash1 as UDM.Scalar).value != (hash3 as UDM.Scalar).value)
    }
    
    // Note: testInvalidArguments removed - validation is handled at runtime by the UTL-X engine via @UTLXFunction annotations

    @Test
    fun testEdgeCases() {
        // Test very long strings
        val longString = "a".repeat(10000)
        val longResult = EncodingFunctions.sha256(listOf(UDM.Scalar(longString)))
        assertTrue(longResult is UDM.Scalar)
        assertTrue(((longResult as UDM.Scalar).value as String).length == 64)
        
        // Test Unicode characters
        val unicodeString = "Hello 世界 🌍"
        val unicodeResult = EncodingFunctions.base64Encode(listOf(UDM.Scalar(unicodeString)))
        assertTrue(unicodeResult is UDM.Scalar)
        
        val unicodeDecoded = EncodingFunctions.base64Decode(listOf(unicodeResult))
        assertEquals(unicodeString, (unicodeDecoded as UDM.Scalar).value)
        
        // Test null handling
        assertThrows<FunctionArgumentException> {
            EncodingFunctions.md5(listOf(UDM.Scalar(null)))
        }
    }
}