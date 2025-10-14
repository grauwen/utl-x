// stdlib/src/test/kotlin/org/apache/utlx/stdlib/encoding/EncodingHashFunctionsTest.kt
package org.apache.utlx.stdlib.encoding

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Tests for hash functions (MD5, SHA-256, SHA-512, HMAC)
 */
class EncodingHashFunctionsTest {
    
    @Nested
    @DisplayName("MD5 Hash Function")
    inner class MD5Tests {
        
        @Test
        fun `md5() hashes simple string`() {
            val input = UDM.Scalar("hello")
            val result = EncodingFunctions.md5(input)
            
            // Known MD5 hash of "hello"
            assertEquals("5d41402abc4b2a76b9719d911017c592", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `md5() hashes empty string`() {
            val input = UDM.Scalar("")
            val result = EncodingFunctions.md5(input)
            
            // Known MD5 hash of ""
            assertEquals("d41d8cd98f00b204e9800998ecf8427e", (result as UDM.Scalar).value)
        }
        
        @Test
        fun `md5() returns 32 character hex string`() {
            val input = UDM.Scalar("test")
            val result = EncodingFunctions.md5(input)
            val hash = (result as UDM.Scalar).value as String
            
            assertEquals(32, hash.length)
            assertTrue(hash.matches(Regex("[0-9a-f]{32}")))
        }
        
        @Test
        fun `md5() hashes unicode correctly`() {
            val input = UDM.Scalar("hello 世界")
            val result = EncodingFunctions.md5(input)
            
            // Hash should be deterministic
            val hash = (result as UDM.Scalar).value as String
            assertEquals(32, hash.length)
        }
        
        @Test
        fun `md5() is deterministic`() {
            val input = UDM.Scalar("test")
            val result1 = EncodingFunctions.md5(input)
            val result2 = EncodingFunctions.md5(input)
            
            assertEquals(result1, result2)
        }
        
        @Test
        fun `md5() throws on non-string input`() {
            assertThrows<IllegalArgumentException> {
                EncodingFunctions.md5(UDM.Scalar(123))
            }
        }
    }
    
    @Nested
    @DisplayName("SHA-256 Hash Function")
    inner class SHA256Tests {
        
        @Test
        fun `sha256() hashes simple string`() {
            val input = UDM.Scalar("hello")
            val result = EncodingFunctions.sha256(input)
            
            // Known SHA-256 hash of "hello"
            assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                (result as UDM.Scalar).value
            )
        }
        
        @Test
        fun `sha256() hashes empty string`() {
            val input = UDM.Scalar("")
            val result = EncodingFunctions.sha256(input)
            
            // Known SHA-256 hash of ""
            assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                (result as UDM.Scalar).value
            )
        }
        
        @Test
        fun `sha256() returns 64 character hex string`() {
            val input = UDM.Scalar("test")
            val result = EncodingFunctions.sha256(input)
            val hash = (result as UDM.Scalar).value as String
            
            assertEquals(64, hash.length)
            assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
        }
        
        @Test
        fun `sha256() hashes long text correctly`() {
            val longText = "a".repeat(10000)
            val input = UDM.Scalar(longText)
            val result = EncodingFunctions.sha256(input)
            
            val hash = (result as UDM.Scalar).value as String
            assertEquals(64, hash.length)
        }
        
        @Test
        fun `sha256() is deterministic`() {
            val input = UDM.Scalar("test")
            val result1 = EncodingFunctions.sha256(input)
            val result2 = EncodingFunctions.sha256(input)
            
            assertEquals(result1, result2)
        }
        
        @Test
        fun `sha256() throws on non-string input`() {
            assertThrows<IllegalArgumentException> {
                EncodingFunctions.sha256(UDM.Scalar(123))
            }
        }
    }
    
    @Nested
    @DisplayName("SHA-512 Hash Function")
    inner class SHA512Tests {
        
        @Test
        fun `sha512() hashes simple string`() {
            val input = UDM.Scalar("hello")
            val result = EncodingFunctions.sha512(input)
            
            // Known SHA-512 hash of "hello"
            assertEquals(
                "9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043",
                (result as UDM.Scalar).value
            )
        }
        
        @Test
        fun `sha512() hashes empty string`() {
            val input = UDM.Scalar("")
            val result = EncodingFunctions.sha512(input)
            
            // Known SHA-512 hash of ""
            assertEquals(
                "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
                (result as UDM.Scalar).value
            )
        }
        
        @Test
        fun `sha512() returns 128 character hex string`() {
            val input = UDM.Scalar("test")
            val result = EncodingFunctions.sha512(input)
            val hash = (result as UDM.Scalar).value as String
            
            assertEquals(128, hash.length)
            assertTrue(hash.matches(Regex("[0-9a-f]{128}")))
        }
        
        @Test
        fun `sha512() is deterministic`() {
            val input = UDM.Scalar("test")
            val result1 = EncodingFunctions.sha512(input)
            val result2 = EncodingFunctions.sha512(input)
            
            assertEquals(result1, result2)
        }
        
        @Test
        fun `sha512() throws on non-string input`() {
            assertThrows<IllegalArgumentException> {
                EncodingFunctions.sha512(UDM.Scalar(123))
            }
        }
    }
    
    @Nested
    @DisplayName("Generic Hash Function")
    inner class GenericHashTests {
        
        @Test
        fun `hash() with MD5 algorithm`() {
            val input = UDM.Scalar("hello")
            val algorithm = UDM.Scalar("MD5")
            val result = EncodingFunctions.hash(input, algorithm)
            
            // Should match md5() result
            val md5Result = EncodingFunctions.md5(input)
            assertEquals(md5Result, result)
        }
        
        @Test
        fun `hash() with SHA-256 algorithm`() {
            val input = UDM.Scalar("hello")
            val algorithm = UDM.Scalar("SHA-256")
            val result = EncodingFunctions.hash(input, algorithm)
            
            // Should match sha256() result
            val sha256Result = EncodingFunctions.sha256(input)
            assertEquals(sha256Result, result)
        }
        
        @Test
        fun `hash() with SHA-512 algorithm`() {
            val input = UDM.Scalar("hello")
            val algorithm = UDM.Scalar("SHA-512")
            val result = EncodingFunctions.hash(input, algorithm)
            
            // Should match sha512() result
            val sha512Result = EncodingFunctions.sha512(input)
            assertEquals(sha512Result, result)
        }
        
        @Test
        fun `hash() throws on unsupported algorithm`() {
            val input = UDM.Scalar("hello")
            val algorithm = UDM.Scalar("INVALID-ALGO")
            
            assertThrows<IllegalArgumentException> {
                EncodingFunctions.hash(input, algorithm)
            }
        }
    }
    
    @Nested
    @DisplayName("HMAC Function")
    inner class HMACTests {
        
        @Test
        fun `hmac() calculates HMAC-SHA256`() {
            val input = UDM.Scalar("hello")
            val key = UDM.Scalar("secret")
            val algorithm = UDM.Scalar("HmacSHA256")
            
            val result = EncodingFunctions.hmac(input, key, algorithm)
            val hash = (result as UDM.Scalar).value as String
            
            // Should be 64 character hex string for SHA-256
            assertEquals(64, hash.length)
            assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
        }
        
        @Test
        fun `hmac() uses default algorithm HmacSHA256`() {
            val input = UDM.Scalar("hello")
            val key = UDM.Scalar("secret")
            
            // Call without algorithm parameter
            val result = EncodingFunctions.hmac(input, key)
            val hash = (result as UDM.Scalar).value as String
            
            assertEquals(64, hash.length)
        }
        
        @Test
        fun `hmac() with different keys produces different hashes`() {
            val input = UDM.Scalar("hello")
            val key1 = UDM.Scalar("secret1")
            val key2 = UDM.Scalar("secret2")
            
            val result1 = EncodingFunctions.hmac(input, key1)
            val result2 = EncodingFunctions.hmac(input, key2)
            
            assertNotEquals(result1, result2)
        }
        
        @Test
        fun `hmac() is deterministic with same key`() {
            val input = UDM.Scalar("hello")
            val key = UDM.Scalar("secret")
            
            val result1 = EncodingFunctions.hmac(input, key)
            val result2 = EncodingFunctions.hmac(input, key)
            
            assertEquals(result1, result2)
        }
        
        @Test
        fun `hmac() supports HmacSHA512`() {
            val input = UDM.Scalar("hello")
            val key = UDM.Scalar("secret")
            val algorithm = UDM.Scalar("HmacSHA512")
            
            val result = EncodingFunctions.hmac(input, key, algorithm)
            val hash = (result as UDM.Scalar).value as String
            
            // Should be 128 character hex string for SHA-512
            assertEquals(128, hash.length)
        }
    }
    
    @Nested
    @DisplayName("Hash Comparison Tests")
    inner class HashComparisonTests {
        
        @Test
        fun `different hash algorithms produce different results`() {
            val input = UDM.Scalar("hello")
            
            val md5Hash = EncodingFunctions.md5(input)
            val sha256Hash = EncodingFunctions.sha256(input)
            val sha512Hash = EncodingFunctions.sha512(input)
            
            // All should be different
            assertNotEquals(md5Hash, sha256Hash)
            assertNotEquals(sha256Hash, sha512Hash)
            assertNotEquals(md5Hash, sha512Hash)
        }
        
        @Test
        fun `hash lengths are correct for each algorithm`() {
            val input = UDM.Scalar("test")
            
            val md5Hash = (EncodingFunctions.md5(input) as UDM.Scalar).value as String
            val sha256Hash = (EncodingFunctions.sha256(input) as UDM.Scalar).value as String
            val sha512Hash = (EncodingFunctions.sha512(input) as UDM.Scalar).value as String
            
            assertEquals(32, md5Hash.length)   // MD5: 128 bits = 32 hex chars
            assertEquals(64, sha256Hash.length) // SHA-256: 256 bits = 64 hex chars
            assertEquals(128, sha512Hash.length) // SHA-512: 512 bits = 128 hex chars
        }
        
        @Test
        fun `small input change produces completely different hash`() {
            val input1 = UDM.Scalar("hello")
            val input2 = UDM.Scalar("hallo") // One character different
            
            val hash1 = EncodingFunctions.sha256(input1)
            val hash2 = EncodingFunctions.sha256(input2)
            
            assertNotEquals(hash1, hash2)
            
            // Hashes should be completely different (avalanche effect)
            val str1 = (hash1 as UDM.Scalar).value as String
            val str2 = (hash2 as UDM.Scalar).value as String
            
            val differentChars = str1.zip(str2).count { (c1, c2) -> c1 != c2 }
            
            // Should have many different characters due to avalanche effect
            assertTrue(differentChars > 50, "Expected avalanche effect, got $differentChars different chars")
        }
    }
    
    @Nested
    @DisplayName("Performance Tests")
    inner class PerformanceTests {
        
        @Test
        fun `hash functions complete in reasonable time`() {
            val largeInput = UDM.Scalar("x".repeat(100000)) // 100KB
            
            val startTime = System.currentTimeMillis()
            EncodingFunctions.sha256(largeInput)
            val duration = System.currentTimeMillis() - startTime
            
            // Should complete in under 100ms even for large input
            assertTrue(duration < 100, "SHA-256 took ${duration}ms for 100KB, expected <100ms")
        }
        
        @Test
        fun `multiple hash calculations are efficient`() {
            val input = UDM.Scalar("test")
            
            val startTime = System.currentTimeMillis()
            repeat(1000) {
                EncodingFunctions.sha256(input)
            }
            val duration = System.currentTimeMillis() - startTime
            
            // 1000 hashes should complete in under 100ms
            assertTrue(duration < 100, "1000 SHA-256 operations took ${duration}ms, expected <100ms")
        }
    }
}
