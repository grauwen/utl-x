// stdlib/src/main/kotlin/org/apache/utlx/stdlib/encoding/AdvancedCryptoFunctions.kt
package org.apache.utlx.stdlib.encoding

import org.apache.utlx.core.udm.*
import org.apache.utlx.stdlib.binary.UDMBinary
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

/**
 * Advanced cryptographic functions for UTL-X
 * 
 * Provides HMAC, encryption, and advanced hashing capabilities.
 * 
 * @since 1.0.0
 */
object AdvancedCryptoFunctions {
    
    // ============================================
    // HMAC FUNCTIONS
    // ============================================
    
    /**
     * Computes HMAC-MD5 hash
     * 
     * @param data The data to hash
     * @param key The secret key
     * @return HMAC-MD5 hash (hex string)
     * 
     * Example:
     * ```
     * hmacMD5("message", "secret-key")
     * ```
     */
    fun hmacMD5(data: UDMValue, key: UDMValue): UDMValue {
        return hmac(data, key, "HmacMD5")
    }
    
    /**
     * Computes HMAC-SHA1 hash
     * 
     * @param data The data to hash
     * @param key The secret key
     * @return HMAC-SHA1 hash (hex string)
     * 
     * Example:
     * ```
     * hmacSHA1("message", "secret-key")
     * ```
     */
    fun hmacSHA1(data: UDMValue, key: UDMValue): UDMValue {
        return hmac(data, key, "HmacSHA1")
    }
    
    /**
     * Computes HMAC-SHA256 hash
     * 
     * @param data The data to hash
     * @param key The secret key
     * @return HMAC-SHA256 hash (hex string)
     * 
     * Example:
     * ```
     * hmacSHA256("message", "secret-key")
     * ```
     */
    fun hmacSHA256(data: UDMValue, key: UDMValue): UDMValue {
        return hmac(data, key, "HmacSHA256")
    }
    
    /**
     * Computes HMAC-SHA384 hash
     * 
     * @param data The data to hash
     * @param key The secret key
     * @return HMAC-SHA384 hash (hex string)
     * 
     * Example:
     * ```
     * hmacSHA384("message", "secret-key")
     * ```
     */
    fun hmacSHA384(data: UDMValue, key: UDMValue): UDMValue {
        return hmac(data, key, "HmacSHA384")
    }
    
    /**
     * Computes HMAC-SHA512 hash
     * 
     * @param data The data to hash
     * @param key The secret key
     * @return HMAC-SHA512 hash (hex string)
     * 
     * Example:
     * ```
     * hmacSHA512("message", "secret-key")
     * ```
     */
    fun hmacSHA512(data: UDMValue, key: UDMValue): UDMValue {
        return hmac(data, key, "HmacSHA512")
    }
    
    /**
     * Generic HMAC computation
     * 
     * @param data The data to hash
     * @param key The secret key
     * @param algorithm HMAC algorithm name
     * @return HMAC hash (hex string)
     */
    private fun hmac(data: UDMValue, key: UDMValue, algorithm: String): UDMValue {
        val message = when (data) {
            is UDMString -> data.value
            is UDMBinary -> String(data.data, Charsets.UTF_8)
            else -> return UDMNull
        }
        
        val secretKey = when (key) {
            is UDMString -> key.value
            is UDMBinary -> String(key.data, Charsets.UTF_8)
            else -> return UDMNull
        }
        
        return try {
            val keySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), algorithm)
            val mac = Mac.getInstance(algorithm)
            mac.init(keySpec)
            
            val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
            UDMString(bytesToHex(bytes))
        } catch (e: Exception) {
            UDMNull
        }
    }
    
    /**
     * Computes HMAC and returns as base64
     * 
     * @param data The data to hash
     * @param key The secret key
     * @param algorithm HMAC algorithm (default: HmacSHA256)
     * @return HMAC hash (base64 string)
     * 
     * Example:
     * ```
     * hmacBase64("message", "secret-key", "HmacSHA256")
     * ```
     */
    fun hmacBase64(
        data: UDMValue, 
        key: UDMValue, 
        algorithm: UDMValue = UDMString("HmacSHA256")
    ): UDMValue {
        val message = when (data) {
            is UDMString -> data.value
            is UDMBinary -> String(data.data, Charsets.UTF_8)
            else -> return UDMNull
        }
        
        val secretKey = when (key) {
            is UDMString -> key.value
            is UDMBinary -> String(key.data, Charsets.UTF_8)
            else -> return UDMNull
        }
        
        val algo = (algorithm as? UDMString)?.value ?: "HmacSHA256"
        
        return try {
            val keySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), algo)
            val mac = Mac.getInstance(algo)
            mac.init(keySpec)
            
            val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
            UDMString(Base64.getEncoder().encodeToString(bytes))
        } catch (e: Exception) {
            UDMNull
        }
    }
    
    // ============================================
    // SYMMETRIC ENCRYPTION (AES)
    // ============================================
    
    /**
     * Encrypts data using AES-128-CBC
     * 
     * @param data The data to encrypt (string or binary)
     * @param key The encryption key (16 bytes for AES-128)
     * @param iv Initialization vector (16 bytes)
     * @return Encrypted data (base64 encoded)
     * 
     * Example:
     * ```
     * encryptAES("sensitive data", "16-byte-key-here", "16-byte-iv--here")
     * ```
     */
    fun encryptAES(data: UDMValue, key: UDMValue, iv: UDMValue): UDMValue {
        return encryptDecryptAES(data, key, iv, Cipher.ENCRYPT_MODE)
    }
    
    /**
     * Decrypts data using AES-128-CBC
     * 
     * @param data The encrypted data (base64 encoded)
     * @param key The decryption key (16 bytes for AES-128)
     * @param iv Initialization vector (16 bytes)
     * @return Decrypted data (string)
     * 
     * Example:
     * ```
     * decryptAES(encryptedData, "16-byte-key-here", "16-byte-iv--here")
     * ```
     */
    fun decryptAES(data: UDMValue, key: UDMValue, iv: UDMValue): UDMValue {
        return encryptDecryptAES(data, key, iv, Cipher.DECRYPT_MODE)
    }
    
    /**
     * Common AES encryption/decryption logic
     */
    private fun encryptDecryptAES(
        data: UDMValue, 
        key: UDMValue, 
        iv: UDMValue, 
        mode: Int
    ): UDMValue {
        val keyBytes = when (key) {
            is UDMString -> key.value.toByteArray(Charsets.UTF_8).take(16).toByteArray()
            is UDMBinary -> key.data.take(16).toByteArray()
            else -> return UDMNull
        }
        
        val ivBytes = when (iv) {
            is UDMString -> iv.value.toByteArray(Charsets.UTF_8).take(16).toByteArray()
            is UDMBinary -> iv.data.take(16).toByteArray()
            else -> return UDMNull
        }
        
        val inputBytes = when {
            mode == Cipher.ENCRYPT_MODE -> when (data) {
                is UDMString -> data.value.toByteArray(Charsets.UTF_8)
                is UDMBinary -> data.data
                else -> return UDMNull
            }
            mode == Cipher.DECRYPT_MODE -> when (data) {
                is UDMString -> Base64.getDecoder().decode(data.value)
                is UDMBinary -> data.data
                else -> return UDMNull
            }
            else -> return UDMNull
        }
        
        return try {
            val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(mode, secretKey, IvParameterSpec(ivBytes))
            
            val outputBytes = cipher.doFinal(inputBytes)
            
            when (mode) {
                Cipher.ENCRYPT_MODE -> UDMString(Base64.getEncoder().encodeToString(outputBytes))
                Cipher.DECRYPT_MODE -> UDMString(String(outputBytes, Charsets.UTF_8))
                else -> UDMNull
            }
        } catch (e: Exception) {
            UDMNull
        }
    }
    
    /**
     * Encrypts data using AES-256-CBC (requires key length of 32 bytes)
     * 
     * @param data The data to encrypt
     * @param key The encryption key (32 bytes for AES-256)
     * @param iv Initialization vector (16 bytes)
     * @return Encrypted data (base64 encoded)
     * 
     * Example:
     * ```
     * encryptAES256("sensitive data", "32-byte-key-here-for-aes-256!", "16-byte-iv--here")
     * ```
     */
    fun encryptAES256(data: UDMValue, key: UDMValue, iv: UDMValue): UDMValue {
        val keyBytes = when (key) {
            is UDMString -> key.value.toByteArray(Charsets.UTF_8).take(32).toByteArray()
            is UDMBinary -> key.data.take(32).toByteArray()
            else -> return UDMNull
        }
        
        val ivBytes = when (iv) {
            is UDMString -> iv.value.toByteArray(Charsets.UTF_8).take(16).toByteArray()
            is UDMBinary -> iv.data.take(16).toByteArray()
            else -> return UDMNull
        }
        
        val inputBytes = when (data) {
            is UDMString -> data.value.toByteArray(Charsets.UTF_8)
            is UDMBinary -> data.data
            else -> return UDMNull
        }
        
        return try {
            val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(ivBytes))
            
            val outputBytes = cipher.doFinal(inputBytes)
            UDMString(Base64.getEncoder().encodeToString(outputBytes))
        } catch (e: Exception) {
            UDMNull
        }
    }
    
    /**
     * Decrypts data using AES-256-CBC
     * 
     * @param data The encrypted data (base64 encoded)
     * @param key The decryption key (32 bytes for AES-256)
     * @param iv Initialization vector (16 bytes)
     * @return Decrypted data (string)
     * 
     * Example:
     * ```
     * decryptAES256(encryptedData, "32-byte-key-here-for-aes-256!", "16-byte-iv--here")
     * ```
     */
    fun decryptAES256(data: UDMValue, key: UDMValue, iv: UDMValue): UDMValue {
        val keyBytes = when (key) {
            is UDMString -> key.value.toByteArray(Charsets.UTF_8).take(32).toByteArray()
            is UDMBinary -> key.data.take(32).toByteArray()
            else -> return UDMNull
        }
        
        val ivBytes = when (iv) {
            is UDMString -> iv.value.toByteArray(Charsets.UTF_8).take(16).toByteArray()
            is UDMBinary -> iv.data.take(16).toByteArray()
            else -> return UDMNull
        }
        
        val inputBytes = when (data) {
            is UDMString -> Base64.getDecoder().decode(data.value)
            is UDMBinary -> data.data
            else -> return UDMNull
        }
        
        return try {
            val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(ivBytes))
            
            val outputBytes = cipher.doFinal(inputBytes)
            UDMString(String(outputBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            UDMNull
        }
    }
    
    // ============================================
    // ADDITIONAL HASH FUNCTIONS
    // ============================================
    
    /**
     * Computes SHA-224 hash
     * 
     * @param input The data to hash
     * @return SHA-224 hash (hex string)
     * 
     * Example:
     * ```
     * sha224("Hello World")
     * ```
     */
    fun sha224(input: UDMValue): UDMValue {
        return hash(input, "SHA-224")
    }
    
    /**
     * Computes SHA-384 hash
     * 
     * @param input The data to hash
     * @return SHA-384 hash (hex string)
     * 
     * Example:
     * ```
     * sha384("Hello World")
     * ```
     */
    fun sha384(input: UDMValue): UDMValue {
        return hash(input, "SHA-384")
    }
    
    /**
     * Computes SHA3-256 hash (if available)
     * 
     * @param input The data to hash
     * @return SHA3-256 hash (hex string)
     * 
     * Example:
     * ```
     * sha3_256("Hello World")
     * ```
     */
    fun sha3_256(input: UDMValue): UDMValue {
        return hash(input, "SHA3-256")
    }
    
    /**
     * Computes SHA3-512 hash (if available)
     * 
     * @param input The data to hash
     * @return SHA3-512 hash (hex string)
     * 
     * Example:
     * ```
     * sha3_512("Hello World")
     * ```
     */
    fun sha3_512(input: UDMValue): UDMValue {
        return hash(input, "SHA3-512")
    }
    
    /**
     * Generic hash function
     */
    private fun hash(input: UDMValue, algorithm: String): UDMValue {
        val text = when (input) {
            is UDMString -> input.value
            is UDMBinary -> String(input.data, Charsets.UTF_8)
            else -> return UDMNull
        }
        
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
            UDMString(bytesToHex(bytes))
        } catch (e: Exception) {
            UDMNull
        }
    }
    
    // ============================================
    // UTILITY FUNCTIONS
    // ============================================
    
    /**
     * Converts byte array to hexadecimal string
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Generates a random initialization vector (IV) for encryption
     * 
     * @param size Size in bytes (default: 16)
     * @return Random IV (base64 encoded)
     * 
     * Example:
     * ```
     * generateIV() // Returns 16-byte random IV
     * generateIV(32) // Returns 32-byte random IV
     * ```
     */
    fun generateIV(size: UDMValue = UDMNumber(16.0)): UDMValue {
        val byteSize = (size as? UDMNumber)?.value?.toInt() ?: 16
        
        val random = java.security.SecureRandom()
        val iv = ByteArray(byteSize)
        random.nextBytes(iv)
        
        return UDMString(Base64.getEncoder().encodeToString(iv))
    }
    
    /**
     * Generates a random encryption key
     * 
     * @param size Size in bytes (16 for AES-128, 32 for AES-256)
     * @return Random key (base64 encoded)
     * 
     * Example:
     * ```
     * generateKey(16) // AES-128 key
     * generateKey(32) // AES-256 key
     * ```
     */
    fun generateKey(size: UDMValue = UDMNumber(32.0)): UDMValue {
        val byteSize = (size as? UDMNumber)?.value?.toInt() ?: 32
        
        val random = java.security.SecureRandom()
        val key = ByteArray(byteSize)
        random.nextBytes(key)
        
        return UDMString(Base64.getEncoder().encodeToString(key))
    }
}
