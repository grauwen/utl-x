// stdlib/src/test/kotlin/org/apache/utlx/stdlib/encoding/AdvancedCryptoFunctionsTest.kt
package org.apache.utlx.stdlib.encoding

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.Base64

class AdvancedCryptoFunctionsTest {

    private val testMessage = "Hello, World!"
    private val testKey = "secret-key-12345"
    private val aes128Key = "1234567890123456" // 16 bytes
    private val aes256Key = "12345678901234567890123456789012" // 32 bytes
    private val testIV = "abcdef1234567890" // 16 bytes

    // ========== HMAC FUNCTIONS ==========

    @Test
    fun testHmacMD5() {
        val result = AdvancedCryptoFunctions.hmacMD5(
            listOf(UDM.Scalar(testMessage), UDM.Scalar(testKey))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertEquals(32, hash.length) // MD5 produces 32-char hex string
        assertTrue(hash.matches(Regex("[0-9a-f]{32}")))
    }

    @Test
    fun testHmacSHA1() {
        val result = AdvancedCryptoFunctions.hmacSHA1(
            listOf(UDM.Scalar(testMessage), UDM.Scalar(testKey))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertEquals(40, hash.length) // SHA1 produces 40-char hex string
        assertTrue(hash.matches(Regex("[0-9a-f]{40}")))
    }

    @Test
    fun testHmacSHA256() {
        val result = AdvancedCryptoFunctions.hmacSHA256(
            listOf(UDM.Scalar(testMessage), UDM.Scalar(testKey))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertEquals(64, hash.length) // SHA256 produces 64-char hex string
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun testHmacSHA384() {
        val result = AdvancedCryptoFunctions.hmacSHA384(
            listOf(UDM.Scalar(testMessage), UDM.Scalar(testKey))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertEquals(96, hash.length) // SHA384 produces 96-char hex string
        assertTrue(hash.matches(Regex("[0-9a-f]{96}")))
    }

    @Test
    fun testHmacSHA512() {
        val result = AdvancedCryptoFunctions.hmacSHA512(
            listOf(UDM.Scalar(testMessage), UDM.Scalar(testKey))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertEquals(128, hash.length) // SHA512 produces 128-char hex string
        assertTrue(hash.matches(Regex("[0-9a-f]{128}")))
    }

    @Test
    fun testHmacWithBinaryData() {
        val binaryData = UDM.Binary(testMessage.toByteArray())
        val result = AdvancedCryptoFunctions.hmacSHA256(
            listOf(binaryData, UDM.Scalar(testKey))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertEquals(64, hash.length)
    }

    @Test
    fun testHmacWithBinaryKey() {
        val binaryKey = UDM.Binary(testKey.toByteArray())
        val result = AdvancedCryptoFunctions.hmacSHA256(
            listOf(UDM.Scalar(testMessage), binaryKey)
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertEquals(64, hash.length)
    }

    @Test
    fun testHmacDifferentKeys() {
        val result1 = AdvancedCryptoFunctions.hmacSHA256(
            listOf(UDM.Scalar(testMessage), UDM.Scalar("key1"))
        )
        val result2 = AdvancedCryptoFunctions.hmacSHA256(
            listOf(UDM.Scalar(testMessage), UDM.Scalar("key2"))
        )
        
        assertTrue(result1 is UDM.Scalar)
        assertTrue(result2 is UDM.Scalar)
        assertNotEquals(result1.value, result2.value)
    }

    @Test
    fun testHmacBase64() {
        val result = AdvancedCryptoFunctions.hmacBase64(
            listOf(UDM.Scalar(testMessage), UDM.Scalar(testKey))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        // Should be valid base64
        assertNotNull(Base64.getDecoder().decode(hash))
    }

    @Test
    fun testHmacBase64WithAlgorithm() {
        val result = AdvancedCryptoFunctions.hmacBase64(
            listOf(UDM.Scalar(testMessage), UDM.Scalar(testKey), UDM.Scalar("HmacSHA1"))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertNotNull(Base64.getDecoder().decode(hash))
    }

    // ========== AES ENCRYPTION/DECRYPTION ==========

    @Test
    fun testEncryptDecryptAES() {
        val encrypted = AdvancedCryptoFunctions.encryptAES(
            listOf(UDM.Scalar(testMessage), UDM.Scalar(aes128Key), UDM.Scalar(testIV))
        )
        
        assertTrue(encrypted is UDM.Scalar)
        val encryptedData = encrypted.value as String
        assertTrue(encryptedData.isNotEmpty())
        assertNotEquals(testMessage, encryptedData)
        
        val decrypted = AdvancedCryptoFunctions.decryptAES(
            listOf(UDM.Scalar(encryptedData), UDM.Scalar(aes128Key), UDM.Scalar(testIV))
        )
        
        assertTrue(decrypted is UDM.Scalar)
        assertEquals(testMessage, decrypted.value)
    }

    @Test
    fun testEncryptDecryptAES256() {
        val encrypted = AdvancedCryptoFunctions.encryptAES256(
            listOf(UDM.Scalar(testMessage), UDM.Scalar(aes256Key), UDM.Scalar(testIV))
        )
        
        assertTrue(encrypted is UDM.Scalar)
        val encryptedData = encrypted.value as String
        assertTrue(encryptedData.isNotEmpty())
        assertNotEquals(testMessage, encryptedData)
        
        val decrypted = AdvancedCryptoFunctions.decryptAES256(
            listOf(UDM.Scalar(encryptedData), UDM.Scalar(aes256Key), UDM.Scalar(testIV))
        )
        
        assertTrue(decrypted is UDM.Scalar)
        assertEquals(testMessage, decrypted.value)
    }

    @Test
    fun testEncryptAESWithBinaryData() {
        val binaryData = UDM.Binary(testMessage.toByteArray())
        val encrypted = AdvancedCryptoFunctions.encryptAES(
            listOf(binaryData, UDM.Scalar(aes128Key), UDM.Scalar(testIV))
        )
        
        assertTrue(encrypted is UDM.Scalar)
        val encryptedData = encrypted.value as String
        assertTrue(encryptedData.isNotEmpty())
        
        val decrypted = AdvancedCryptoFunctions.decryptAES(
            listOf(UDM.Scalar(encryptedData), UDM.Scalar(aes128Key), UDM.Scalar(testIV))
        )
        
        assertTrue(decrypted is UDM.Scalar)
        assertEquals(testMessage, decrypted.value)
    }

    @Test
    fun testEncryptAESWithBinaryKey() {
        val binaryKey = UDM.Binary(aes128Key.toByteArray())
        val encrypted = AdvancedCryptoFunctions.encryptAES(
            listOf(UDM.Scalar(testMessage), binaryKey, UDM.Scalar(testIV))
        )
        
        assertTrue(encrypted is UDM.Scalar)
        val encryptedData = encrypted.value as String
        assertTrue(encryptedData.isNotEmpty())
        
        val decrypted = AdvancedCryptoFunctions.decryptAES(
            listOf(UDM.Scalar(encryptedData), binaryKey, UDM.Scalar(testIV))
        )
        
        assertTrue(decrypted is UDM.Scalar)
        assertEquals(testMessage, decrypted.value)
    }

    @Test
    fun testEncryptAESDifferentIVs() {
        val iv1 = "1234567890123456"
        val iv2 = "6543210987654321"
        
        val encrypted1 = AdvancedCryptoFunctions.encryptAES(
            listOf(UDM.Scalar(testMessage), UDM.Scalar(aes128Key), UDM.Scalar(iv1))
        )
        val encrypted2 = AdvancedCryptoFunctions.encryptAES(
            listOf(UDM.Scalar(testMessage), UDM.Scalar(aes128Key), UDM.Scalar(iv2))
        )
        
        assertTrue(encrypted1 is UDM.Scalar)
        assertTrue(encrypted2 is UDM.Scalar)
        assertNotEquals(encrypted1.value, encrypted2.value)
    }

    @Test
    fun testDecryptAESInvalidData() {
        val result = AdvancedCryptoFunctions.decryptAES(
            listOf(UDM.Scalar("invalid-base64"), UDM.Scalar(aes128Key), UDM.Scalar(testIV))
        )
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value == null) // Should return null for invalid data
    }

    // ========== ADDITIONAL HASH FUNCTIONS ==========

    @Test
    fun testSHA224() {
        val result = AdvancedCryptoFunctions.sha224(listOf(UDM.Scalar(testMessage)))
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertEquals(56, hash.length) // SHA224 produces 56-char hex string
        assertTrue(hash.matches(Regex("[0-9a-f]{56}")))
    }

    @Test
    fun testSHA384() {
        val result = AdvancedCryptoFunctions.sha384(listOf(UDM.Scalar(testMessage)))
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertEquals(96, hash.length) // SHA384 produces 96-char hex string
        assertTrue(hash.matches(Regex("[0-9a-f]{96}")))
    }

    @Test
    fun testSHA3_256() {
        val result = AdvancedCryptoFunctions.sha3_256(listOf(UDM.Scalar(testMessage)))
        
        // SHA3 might not be available on all systems
        assertTrue(result is UDM.Scalar)
        // If available, should produce 64-char hex string
        val hash = result.value
        if (hash != null && hash != "") {
            assertEquals(64, (hash as String).length)
            assertTrue((hash as String).matches(Regex("[0-9a-f]{64}")))
        }
    }

    @Test
    fun testSHA3_512() {
        val result = AdvancedCryptoFunctions.sha3_512(listOf(UDM.Scalar(testMessage)))
        
        // SHA3 might not be available on all systems
        assertTrue(result is UDM.Scalar)
        // If available, should produce 128-char hex string
        val hash = result.value
        if (hash != null && hash != "") {
            assertEquals(128, (hash as String).length)
            assertTrue((hash as String).matches(Regex("[0-9a-f]{128}")))
        }
    }

    @Test
    fun testHashWithBinaryInput() {
        val binaryData = UDM.Binary(testMessage.toByteArray())
        val result = AdvancedCryptoFunctions.sha224(listOf(binaryData))
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertEquals(56, hash.length)
    }

    @Test
    fun testHashConsistency() {
        val result1 = AdvancedCryptoFunctions.sha224(listOf(UDM.Scalar(testMessage)))
        val result2 = AdvancedCryptoFunctions.sha224(listOf(UDM.Scalar(testMessage)))
        
        assertTrue(result1 is UDM.Scalar)
        assertTrue(result2 is UDM.Scalar)
        assertEquals(result1.value, result2.value)
    }

    // ========== UTILITY FUNCTIONS ==========

    @Test
    fun testGenerateIVDefault() {
        val result = AdvancedCryptoFunctions.generateIV(listOf())
        
        assertTrue(result is UDM.Scalar)
        val iv = result.value as String
        assertTrue(iv.isNotEmpty())
        
        // Should be valid base64
        val decoded = Base64.getDecoder().decode(iv)
        assertEquals(16, decoded.size) // Default size is 16 bytes
    }

    @Test
    fun testGenerateIVCustomSize() {
        val result = AdvancedCryptoFunctions.generateIV(listOf(UDM.Scalar(32.0)))
        
        assertTrue(result is UDM.Scalar)
        val iv = result.value as String
        assertTrue(iv.isNotEmpty())
        
        val decoded = Base64.getDecoder().decode(iv)
        assertEquals(32, decoded.size)
    }

    @Test
    fun testGenerateIVUnique() {
        val result1 = AdvancedCryptoFunctions.generateIV(listOf())
        val result2 = AdvancedCryptoFunctions.generateIV(listOf())
        
        assertTrue(result1 is UDM.Scalar)
        assertTrue(result2 is UDM.Scalar)
        assertNotEquals(result1.value, result2.value) // Should be different
    }

    @Test
    fun testGenerateKeyDefault() {
        val result = AdvancedCryptoFunctions.generateKey(listOf())
        
        assertTrue(result is UDM.Scalar)
        val key = result.value as String
        assertTrue(key.isNotEmpty())
        
        val decoded = Base64.getDecoder().decode(key)
        assertEquals(32, decoded.size) // Default size is 32 bytes (AES-256)
    }

    @Test
    fun testGenerateKeyAES128() {
        val result = AdvancedCryptoFunctions.generateKey(listOf(UDM.Scalar(16.0)))
        
        assertTrue(result is UDM.Scalar)
        val key = result.value as String
        assertTrue(key.isNotEmpty())
        
        val decoded = Base64.getDecoder().decode(key)
        assertEquals(16, decoded.size) // AES-128 key size
    }

    @Test
    fun testGenerateKeyUnique() {
        val result1 = AdvancedCryptoFunctions.generateKey(listOf())
        val result2 = AdvancedCryptoFunctions.generateKey(listOf())
        
        assertTrue(result1 is UDM.Scalar)
        assertTrue(result2 is UDM.Scalar)
        assertNotEquals(result1.value, result2.value) // Should be different
    }

    // ========== ERROR HANDLING ==========

    @Test
    fun testHmacMD5InsufficientArgs() {
        assertThrows<FunctionArgumentException> {
            AdvancedCryptoFunctions.hmacMD5(listOf(UDM.Scalar(testMessage)))
        }
    }

    @Test
    fun testHmacSHA256InsufficientArgs() {
        assertThrows<FunctionArgumentException> {
            AdvancedCryptoFunctions.hmacSHA256(listOf(UDM.Scalar(testMessage)))
        }
    }

    @Test
    fun testEncryptAESInsufficientArgs() {
        assertThrows<FunctionArgumentException> {
            AdvancedCryptoFunctions.encryptAES(listOf(UDM.Scalar(testMessage), UDM.Scalar(aes128Key)))
        }
    }

    @Test
    fun testDecryptAESInsufficientArgs() {
        assertThrows<FunctionArgumentException> {
            AdvancedCryptoFunctions.decryptAES(listOf(UDM.Scalar("encrypted"), UDM.Scalar(aes128Key)))
        }
    }

    @Test
    fun testSHA224InsufficientArgs() {
        assertThrows<FunctionArgumentException> {
            AdvancedCryptoFunctions.sha224(listOf())
        }
    }

    @Test
    fun testHmacWithNullData() {
        val result = AdvancedCryptoFunctions.hmacSHA256(
            listOf(UDM.Scalar.nullValue(), UDM.Scalar(testKey))
        )
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value == null) // Should return null
    }

    @Test
    fun testHmacWithInvalidType() {
        val result = AdvancedCryptoFunctions.hmacSHA256(
            listOf(UDM.Array(listOf()), UDM.Scalar(testKey))
        )
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value == null) // Should return null for unsupported type
    }

    @Test
    fun testEncryptAESWithNullData() {
        val result = AdvancedCryptoFunctions.encryptAES(
            listOf(UDM.Scalar.nullValue(), UDM.Scalar(aes128Key), UDM.Scalar(testIV))
        )
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value == null) // Should return null
    }

    @Test
    fun testHashWithNullData() {
        val result = AdvancedCryptoFunctions.sha224(listOf(UDM.Scalar.nullValue()))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value == null) // Should return null
    }

    // ========== EDGE CASES ==========

    @Test
    fun testHmacEmptyMessage() {
        val result = AdvancedCryptoFunctions.hmacSHA256(
            listOf(UDM.Scalar(""), UDM.Scalar(testKey))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertEquals(64, hash.length)
    }

    @Test
    fun testHmacEmptyKey() {
        val result = AdvancedCryptoFunctions.hmacSHA256(
            listOf(UDM.Scalar(testMessage), UDM.Scalar(""))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertTrue(hash.isNotEmpty())
        assertEquals(64, hash.length)
    }

    @Test
    fun testEncryptAESEmptyMessage() {
        val result = AdvancedCryptoFunctions.encryptAES(
            listOf(UDM.Scalar(""), UDM.Scalar(aes128Key), UDM.Scalar(testIV))
        )
        
        assertTrue(result is UDM.Scalar)
        val encrypted = result.value as String
        assertTrue(encrypted.isNotEmpty()) // Even empty strings get padded
    }

    @Test
    fun testEncryptAESLongMessage() {
        val longMessage = "A".repeat(1000)
        val encrypted = AdvancedCryptoFunctions.encryptAES(
            listOf(UDM.Scalar(longMessage), UDM.Scalar(aes128Key), UDM.Scalar(testIV))
        )
        
        assertTrue(encrypted is UDM.Scalar)
        val encryptedData = encrypted.value as String
        assertTrue(encryptedData.isNotEmpty())
        
        val decrypted = AdvancedCryptoFunctions.decryptAES(
            listOf(UDM.Scalar(encryptedData), UDM.Scalar(aes128Key), UDM.Scalar(testIV))
        )
        
        assertTrue(decrypted is UDM.Scalar)
        assertEquals(longMessage, decrypted.value)
    }

    @Test
    fun testGenerateIVInvalidSize() {
        val result = AdvancedCryptoFunctions.generateIV(listOf(UDM.Scalar("invalid")))
        
        assertTrue(result is UDM.Scalar)
        val iv = result.value as String
        assertTrue(iv.isNotEmpty())
        
        val decoded = Base64.getDecoder().decode(iv)
        assertEquals(16, decoded.size) // Should default to 16
    }

    @Test
    fun testGenerateKeyInvalidSize() {
        val result = AdvancedCryptoFunctions.generateKey(listOf(UDM.Scalar("invalid")))
        
        assertTrue(result is UDM.Scalar)
        val key = result.value as String
        assertTrue(key.isNotEmpty())
        
        val decoded = Base64.getDecoder().decode(key)
        assertEquals(32, decoded.size) // Should default to 32
    }
}