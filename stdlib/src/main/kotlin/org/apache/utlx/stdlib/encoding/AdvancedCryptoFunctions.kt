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
    fun hmacMD5(data: UDM, key: UDM): UDM {
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
    fun hmacSHA1(data: UDM, key: UDM): UDM {
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
    fun hmacSHA256(data: UDM, key: UDM): UDM {
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
    fun hmacSHA384(data: UDM, key: UDM): UDM {
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
    fun hmacSHA512(data: UDM, key: UDM): UDM {
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
    private fun hmac(data: UDM, key: UDM, algorithm: String): UDM {
        val message = when (data) {
            is UDM.Scalar -> data.value
            is UDMBinary -> String(data.data, Charsets.UTF_8)
            else -> return UDM.Scalar.nullValue()
        }
        
        val secretKey = when (key) {
            is UDM.Scalar -> key.value
            is UDMBinary -> String(key.data, Charsets.UTF_8)
            else -> return UDM.Scalar.nullValue()
        }
        
        return try {
            val keySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), algorithm)
            val mac = Mac.getInstance(algorithm)
            mac.init(keySpec)
            
            val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
            UDM.Scalar(bytesToHex(bytes))
        } catch (e: Exception) {
            UDM.Scalar.nullValue()
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
        data: UDM, 
        key: UDM, 
        algorithm: UDM = UDM.Scalar("HmacSHA256")
    ): UDM {
        val message = when (data) {
            is UDM.Scalar -> data.value
            is UDMBinary -> String(data.data, Charsets.UTF_8)
            else -> return UDM.Scalar.nullValue()
        }
        
        val secretKey = when (key) {
            is UDM.Scalar -> key.value
            is UDMBinary -> String(key.data, Charsets.UTF_8)
            else -> return UDM.Scalar.nullValue()
        }
        
        val algo = (algorithm as? UDM.Scalar)?.value ?: "HmacSHA256"
        
        return try {
            val keySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), algo)
            val mac = Mac.getInstance(algo)
            mac.init(keySpec)
            
            val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
            UDM.Scalar(Base64.getEncoder().encodeToString(bytes))
        } catch (e: Exception) {
            UDM.Scalar.nullValue()
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
    fun encryptAES(data: UDM, key: UDM, iv: UDM): UDM {
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
    fun decryptAES(data: UDM, key: UDM, iv: UDM): UDM {
        return encryptDecryptAES(data, key, iv, Cipher.DECRYPT_MODE)
    }
    
    /**
     * Common AES encryption/decryption logic
     */
    private fun encryptDecryptAES(
        data: UDM, 
        key: UDM, 
        iv: UDM, 
        mode: Int
    ): UDM {
        val keyBytes = when (key) {
            is UDM.Scalar -> key.value.toByteArray(Charsets.UTF_8).take(16).toByteArray()
            is UDMBinary -> key.data.take(16).toByteArray()
            else -> return UDM.Scalar.nullValue()
        }
        
        val ivBytes = when (iv) {
            is UDM.Scalar -> iv.value.toByteArray(Charsets.UTF_8).take(16).toByteArray()
            is UDMBinary -> iv.data.take(16).toByteArray()
            else -> return UDM.Scalar.nullValue()
        }
        
        val inputBytes = when {
            mode == Cipher.ENCRYPT_MODE -> when (data) {
                is UDM.Scalar -> data.value.toByteArray(Charsets.UTF_8)
                is UDMBinary -> data.data
                else -> return UDM.Scalar.nullValue()
            }
            mode == Cipher.DECRYPT_MODE -> when (data) {
                is UDM.Scalar -> Base64.getDecoder().decode(data.value)
                is UDMBinary -> data.data
                else -> return UDM.Scalar.nullValue()
            }
            else -> return UDM.Scalar.nullValue()
        }
        
        return try {
            val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(mode, secretKey, IvParameterSpec(ivBytes))
            
            val outputBytes = cipher.doFinal(inputBytes)
            
            when (mode) {
                Cipher.ENCRYPT_MODE -> UDM.Scalar(Base64.getEncoder().encodeToString(outputBytes))
                Cipher.DECRYPT_MODE -> UDM.Scalar(String(outputBytes, Charsets.UTF_8))
                else -> UDM.Scalar.nullValue()
            }
        } catch (e: Exception) {
            UDM.Scalar.nullValue()
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
    fun encryptAES256(data: UDM, key: UDM, iv: UDM): UDM {
        val keyBytes = when (key) {
            is UDM.Scalar -> key.value.toByteArray(Charsets.UTF_8).take(32).toByteArray()
            is UDMBinary -> key.data.take(32).toByteArray()
            else -> return UDM.Scalar.nullValue()
        }
        
        val ivBytes = when (iv) {
            is UDM.Scalar -> iv.value.toByteArray(Charsets.UTF_8).take(16).toByteArray()
            is UDMBinary -> iv.data.take(16).toByteArray()
            else -> return UDM.Scalar.nullValue()
        }
        
        val inputBytes = when (data) {
            is UDM.Scalar -> data.value.toByteArray(Charsets.UTF_8)
            is UDMBinary -> data.data
            else -> return UDM.Scalar.nullValue()
        }
        
        return try {
            val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(ivBytes))
            
            val outputBytes = cipher.doFinal(inputBytes)
            UDM.Scalar(Base64.getEncoder().encodeToString(outputBytes))
        } catch (e: Exception) {
            UDM.Scalar.nullValue()
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
    fun decryptAES256(data: UDM, key: UDM, iv: UDM): UDM {
        val keyBytes = when (key) {
            is UDM.Scalar -> key.value.toByteArray(Charsets.UTF_8).take(32).toByteArray()
            is UDMBinary -> key.data.take(32).toByteArray()
            else -> return UDM.Scalar.nullValue()
        }
        
        val ivBytes = when (iv) {
            is UDM.Scalar -> iv.value.toByteArray(Charsets.UTF_8).take(16).toByteArray()
            is UDMBinary -> iv.data.take(16).toByteArray()
            else -> return UDM.Scalar.nullValue()
        }
        
        val inputBytes = when (data) {
            is UDM.Scalar -> Base64.getDecoder().decode(data.value)
            is UDMBinary -> data.data
            else -> return UDM.Scalar.nullValue()
        }
        
        return try {
            val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(ivBytes))
            
            val outputBytes = cipher.doFinal(inputBytes)
            UDM.Scalar(String(outputBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            UDM.Scalar.nullValue()
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
    fun sha224(input: UDM): UDM {
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
    fun sha384(input: UDM): UDM {
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
    fun sha3_256(input: UDM): UDM {
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
    fun sha3_512(input: UDM): UDM {
        return hash(input, "SHA3-512")
    }
    
    /**
     * Generic hash function
     */
    private fun hash(input: UDM, algorithm: String): UDM {
        val text = when (input) {
            is UDM.Scalar -> input.value
            is UDMBinary -> String(input.data, Charsets.UTF_8)
            else -> return UDM.Scalar.nullValue()
        }
        
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
            UDM.Scalar(bytesToHex(bytes))
        } catch (e: Exception) {
            UDM.Scalar.nullValue()
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
    fun generateIV(size: UDM = UDM.Scalar(16.0)): UDM {
        val byteSize = (size as? UDM.Scalar)?.value?.toInt() ?: 16
        
        val random = java.security.SecureRandom()
        val iv = ByteArray(byteSize)
        random.nextBytes(iv)
        
        return UDM.Scalar(Base64.getEncoder().encodeToString(iv))
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
    fun generateKey(size: UDM = UDM.Scalar(32.0)): UDM {
        val byteSize = (size as? UDM.Scalar)?.value?.toInt() ?: 32
        
        val random = java.security.SecureRandom()
        val key = ByteArray(byteSize)
        random.nextBytes(key)
        
        return UDM.Scalar(Base64.getEncoder().encodeToString(key))
    }
}
