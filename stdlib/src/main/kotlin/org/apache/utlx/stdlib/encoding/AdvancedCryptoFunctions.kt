// stdlib/src/main/kotlin/org/apache/utlx/stdlib/encoding/AdvancedCryptoFunctions.kt
package org.apache.utlx.stdlib.encoding

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import org.apache.utlx.stdlib.annotations.UTLXFunction

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
    
    @UTLXFunction(
        description = "Computes HMAC-MD5 hash",
        minArgs = 2,
        maxArgs = 2,
        category = "Encoding",
        returns = "Result of the operation",
        example = "hmacMD5(...) => result",
        notes = "Example:\n```\nhmacMD5(\"message\", \"secret-key\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun hmacMD5(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException(
                "hmacMD5 expects 2 arguments, got ${args.size}. " +
                "Hint: Provide data and secret key as arguments."
            )
        }
        return hmac(args[0], args[1], "HmacMD5")
    }
    
    @UTLXFunction(
        description = "Computes HMAC-SHA1 hash",
        minArgs = 2,
        maxArgs = 2,
        category = "Encoding",
        returns = "Result of the operation",
        example = "hmacSHA1(...) => result",
        notes = "Example:\n```\nhmacSHA1(\"message\", \"secret-key\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun hmacSHA1(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException(
                "hmacSHA1 expects 2 arguments, got ${args.size}. " +
                "Hint: Provide data and secret key as arguments."
            )
        }
        return hmac(args[0], args[1], "HmacSHA1")
    }
    
    @UTLXFunction(
        description = "Computes HMAC-SHA256 hash",
        minArgs = 2,
        maxArgs = 2,
        category = "Encoding",
        returns = "Result of the operation",
        example = "hmacSHA256(...) => result",
        notes = "Example:\n```\nhmacSHA256(\"message\", \"secret-key\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun hmacSHA256(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException(
                "hmacSHA256 expects 2 arguments, got ${args.size}. " +
                "Hint: Provide data and secret key as arguments."
            )
        }
        return hmac(args[0], args[1], "HmacSHA256")
    }
    
    @UTLXFunction(
        description = "Computes HMAC-SHA384 hash",
        minArgs = 2,
        maxArgs = 2,
        category = "Encoding",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "hmacSHA384(...) => result",
        notes = "Example:\n```\nhmacSHA384(\"message\", \"secret-key\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun hmacSHA384(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException(
                "hmacSHA384 expects 2 arguments, got ${args.size}. " +
                "Hint: Provide data and secret key as arguments."
            )
        }
        return hmac(args[0], args[1], "HmacSHA384")
    }
    
    @UTLXFunction(
        description = "Computes HMAC-SHA512 hash",
        minArgs = 2,
        maxArgs = 2,
        category = "Encoding",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "hmacSHA512(...) => result",
        notes = "Example:\n```\nhmacSHA512(\"message\", \"secret-key\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun hmacSHA512(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException(
                "hmacSHA512 expects 2 arguments, got ${args.size}. " +
                "Hint: Provide data and secret key as arguments."
            )
        }
        return hmac(args[0], args[1], "HmacSHA512")
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
            is UDM.Scalar -> data.value?.toString() ?: ""
            is UDM.Binary -> String(data.data, Charsets.UTF_8)
            else -> return UDM.Scalar.nullValue()
        }
        
        val secretKey = when (key) {
            is UDM.Scalar -> key.value?.toString() ?: ""
            is UDM.Binary -> String(key.data, Charsets.UTF_8)
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
    
    @UTLXFunction(
        description = "Computes HMAC and returns as base64",
        minArgs = 2,
        maxArgs = 2,
        category = "Encoding",
        parameters = [
            "array: Input array to process",
        "key: Key value"
        ],
        returns = "Result of the operation",
        example = "hmacBase64(...) => result",
        notes = "Example:\n```\nhmacBase64(\"message\", \"secret-key\", \"HmacSHA256\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun hmacBase64(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException(
                "hmacBase64 expects at least 2 arguments, got ${args.size}. " +
                "Hint: Provide data, secret key, and optional algorithm (default: SHA256)."
            )
        }
        val data = args[0]
        val key = args[1]
        val algorithm = if (args.size > 2) args[2] else UDM.Scalar("HmacSHA256")
        val message = when (data) {
            is UDM.Scalar -> data.value?.toString() ?: ""
            is UDM.Binary -> String(data.data, Charsets.UTF_8)
            else -> return UDM.Scalar.nullValue()
        }
        
        val secretKey = when (key) {
            is UDM.Scalar -> key.value?.toString() ?: ""
            is UDM.Binary -> String(key.data, Charsets.UTF_8)
            else -> return UDM.Scalar.nullValue()
        }
        
        val algo = (algorithm as? UDM.Scalar)?.value?.toString() ?: "HmacSHA256"
        
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
    
    @UTLXFunction(
        description = "Encrypts data using AES-128-CBC",
        minArgs = 3,
        maxArgs = 3,
        category = "Encoding",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "encryptAES(...) => result",
        notes = "Example:\n```\nencryptAES(\"sensitive data\", \"16-byte-key-here\", \"16-byte-iv--here\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun encryptAES(args: List<UDM>): UDM {
        if (args.size < 3) {
            throw FunctionArgumentException(
                "encryptAES expects 3 arguments, got ${args.size}. " +
                "Hint: Provide data, encryption key, and initialization vector (IV)."
            )
        }
        return encryptDecryptAES(args[0], args[1], args[2], Cipher.ENCRYPT_MODE)
    }
    
    @UTLXFunction(
        description = "Decrypts data using AES-128-CBC",
        minArgs = 3,
        maxArgs = 3,
        category = "Encoding",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "decryptAES(...) => result",
        notes = "Example:\n```\ndecryptAES(encryptedData, \"16-byte-key-here\", \"16-byte-iv--here\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun decryptAES(args: List<UDM>): UDM {
        if (args.size < 3) {
            throw FunctionArgumentException(
                "decryptAES expects 3 arguments, got ${args.size}. " +
                "Hint: Provide encrypted data, decryption key, and initialization vector (IV)."
            )
        }
        return encryptDecryptAES(args[0], args[1], args[2], Cipher.DECRYPT_MODE)
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
            is UDM.Scalar -> (key.value?.toString() ?: "").toByteArray(Charsets.UTF_8).take(16).toByteArray()
            is UDM.Binary -> key.data.take(16).toByteArray()
            else -> return UDM.Scalar.nullValue()
        }
        
        val ivBytes = when (iv) {
            is UDM.Scalar -> (iv.value?.toString() ?: "").toByteArray(Charsets.UTF_8).take(16).toByteArray()
            is UDM.Binary -> iv.data.take(16).toByteArray()
            else -> return UDM.Scalar.nullValue()
        }
        
        val inputBytes = when {
            mode == Cipher.ENCRYPT_MODE -> when (data) {
                is UDM.Scalar -> (data.value?.toString() ?: "").toByteArray(Charsets.UTF_8)
                is UDM.Binary -> data.data
                else -> return UDM.Scalar.nullValue()
            }
            mode == Cipher.DECRYPT_MODE -> when (data) {
                is UDM.Scalar -> Base64.getDecoder().decode(data.value?.toString() ?: "")
                is UDM.Binary -> data.data
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
    
    @UTLXFunction(
        description = "Encrypts data using AES-256-CBC (requires key length of 32 bytes)",
        minArgs = 3,
        maxArgs = 3,
        category = "Encoding",
        parameters = [
            "array: Input array to process",
        "key: Key value",
        "iv: Iv value"
        ],
        returns = "Result of the operation",
        example = "encryptAES256(...) => result",
        notes = "Example:\n```\nencryptAES256(\"sensitive data\", \"32-byte-key-here-for-aes-256!\", \"16-byte-iv--here\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun encryptAES256(args: List<UDM>): UDM {
        if (args.size < 3) {
            throw FunctionArgumentException(
                "encryptAES256 expects 3 arguments, got ${args.size}. " +
                "Hint: Provide data, 256-bit encryption key, and initialization vector (IV)."
            )
        }
        val data = args[0]
        val key = args[1]
        val iv = args[2]
        val keyBytes = when (key) {
            is UDM.Scalar -> (key.value?.toString() ?: "").toByteArray(Charsets.UTF_8).take(32).toByteArray()
            is UDM.Binary -> key.data.take(32).toByteArray()
            else -> return UDM.Scalar.nullValue()
        }
        
        val ivBytes = when (iv) {
            is UDM.Scalar -> (iv.value?.toString() ?: "").toByteArray(Charsets.UTF_8).take(16).toByteArray()
            is UDM.Binary -> iv.data.take(16).toByteArray()
            else -> return UDM.Scalar.nullValue()
        }
        
        val inputBytes = when (data) {
            is UDM.Scalar -> (data.value?.toString() ?: "").toByteArray(Charsets.UTF_8)
            is UDM.Binary -> data.data
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
    
    @UTLXFunction(
        description = "Decrypts data using AES-256-CBC",
        minArgs = 3,
        maxArgs = 3,
        category = "Encoding",
        parameters = [
            "array: Input array to process",
        "key: Key value",
        "iv: Iv value"
        ],
        returns = "Result of the operation",
        example = "decryptAES256(...) => result",
        notes = "Example:\n```\ndecryptAES256(encryptedData, \"32-byte-key-here-for-aes-256!\", \"16-byte-iv--here\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun decryptAES256(args: List<UDM>): UDM {
        if (args.size < 3) {
            throw FunctionArgumentException(
                "decryptAES256 expects 3 arguments, got ${args.size}. " +
                "Hint: Provide encrypted data, 256-bit decryption key, and initialization vector (IV)."
            )
        }
        val data = args[0]
        val key = args[1]
        val iv = args[2]
        val keyBytes = when (key) {
            is UDM.Scalar -> (key.value?.toString() ?: "").toByteArray(Charsets.UTF_8).take(32).toByteArray()
            is UDM.Binary -> key.data.take(32).toByteArray()
            else -> return UDM.Scalar.nullValue()
        }
        
        val ivBytes = when (iv) {
            is UDM.Scalar -> (iv.value?.toString() ?: "").toByteArray(Charsets.UTF_8).take(16).toByteArray()
            is UDM.Binary -> iv.data.take(16).toByteArray()
            else -> return UDM.Scalar.nullValue()
        }
        
        val inputBytes = when (data) {
            is UDM.Scalar -> Base64.getDecoder().decode(data.value?.toString() ?: "")
            is UDM.Binary -> data.data
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
    
    @UTLXFunction(
        description = "Computes SHA-224 hash",
        minArgs = 1,
        maxArgs = 1,
        category = "Encoding",
        parameters = [
            "input: Input value"
        ],
        returns = "Result of the operation",
        example = "sha224(...) => result",
        notes = "Example:\n```\nsha224(\"Hello World\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun sha224(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "sha224 expects 1 argument, got 0. " +
                "Hint: Provide the data to hash."
            )
        }
        val input = args[0]
        return hash(input, "SHA-224")
    }
    
    @UTLXFunction(
        description = "Computes SHA-384 hash",
        minArgs = 1,
        maxArgs = 1,
        category = "Encoding",
        parameters = [
            "input: Input value"
        ],
        returns = "Result of the operation",
        example = "sha384(...) => result",
        notes = "Example:\n```\nsha384(\"Hello World\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun sha384(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "sha384 expects 1 argument, got 0. " +
                "Hint: Provide the data to hash."
            )
        }
        val input = args[0]
        return hash(input, "SHA-384")
    }
    
    @UTLXFunction(
        description = "Computes SHA3-256 hash (if available)",
        minArgs = 1,
        maxArgs = 1,
        category = "Encoding",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "sha3_256(...) => result",
        notes = "Example:\n```\nsha3_256(\"Hello World\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun sha3_256(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "sha3_256 expects 1 argument, got 0. " +
                "Hint: Provide the data to hash."
            )
        }
        val input = args[0]
        return hash(input, "SHA3-256")
    }
    
    @UTLXFunction(
        description = "Computes SHA3-512 hash (if available)",
        minArgs = 1,
        maxArgs = 1,
        category = "Encoding",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "sha3_512(...) => result",
        notes = "Example:\n```\nsha3_512(\"Hello World\")\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun sha3_512(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "sha3_512 expects 1 argument, got 0. " +
                "Hint: Provide the data to hash."
            )
        }
        val input = args[0]
        return hash(input, "SHA3-512")
    }
    
    /**
     * Generic hash function
     */
    private fun hash(input: UDM, algorithm: String): UDM {
        val text = when (input) {
            is UDM.Scalar -> input.value?.toString() ?: ""
            is UDM.Binary -> String(input.data, Charsets.UTF_8)
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
    
    @UTLXFunction(
        description = "Generates a random initialization vector (IV) for encryption",
        minArgs = 1,
        maxArgs = 1,
        category = "Encoding",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "generateIV(...) => result",
        notes = "Example:\n```\ngenerateIV() // Returns 16-byte random IV\ngenerateIV(32) // Returns 32-byte random IV\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun generateIV(args: List<UDM>): UDM {
        val size = if (args.isNotEmpty()) args[0] else UDM.Scalar(16.0)
        val byteSize = (size as? UDM.Scalar)?.value?.toString()?.toIntOrNull() ?: 16
        
        val random = java.security.SecureRandom()
        val iv = ByteArray(byteSize)
        random.nextBytes(iv)
        
        return UDM.Scalar(Base64.getEncoder().encodeToString(iv))
    }
    
    @UTLXFunction(
        description = "Generates a random encryption key",
        minArgs = 1,
        maxArgs = 1,
        category = "Encoding",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "generateKey(...) => result",
        notes = "Example:\n```\ngenerateKey(16) // AES-128 key\ngenerateKey(32) // AES-256 key\n```",
        tags = ["encoding"],
        since = "1.0"
    )
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
    fun generateKey(args: List<UDM>): UDM {
        val size = if (args.isNotEmpty()) args[0] else UDM.Scalar(32.0)
        val byteSize = (size as? UDM.Scalar)?.value?.toString()?.toIntOrNull() ?: 32
        
        val random = java.security.SecureRandom()
        val key = ByteArray(byteSize)
        random.nextBytes(key)
        
        return UDM.Scalar(Base64.getEncoder().encodeToString(key))
    }
}
