// stdlib/src/main/kotlin/org/apache/utlx/stdlib/encoding/EncodingFunctions.kt
package org.apache.utlx.stdlib.encoding

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import java.util.Base64
import java.net.URLEncoder
import java.net.URLDecoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Encoding and Hashing Functions
 * 
 * Provides 12 functions:
 * - Base64: encode, decode
 * - URL: encode, decode
 * - Hex: encode, decode
 * - Hash: md5, sha256, sha512, sha1
 * - Advanced: hash, hmac
 */

/**
 * Encoding and decoding functions
 */
object EncodingFunctions {
    
    /**
     * Base64 encode
     * Usage: base64-encode("Hello World")
     */
    fun base64Encode(args: List<UDM>): UDM {
        requireArgs(args, 1, "base64-encode")
        val str = args[0].asString()
        val encoded = Base64.getEncoder().encodeToString(str.toByteArray())
        return UDM.Scalar(encoded)
    }
    
    /**
     * Base64 decode
     * Usage: base64-decode("SGVsbG8gV29ybGQ=")
     */
    fun base64Decode(args: List<UDM>): UDM {
        requireArgs(args, 1, "base64-decode")
        val str = args[0].asString()
        return try {
            val decoded = String(Base64.getDecoder().decode(str))
            UDM.Scalar(decoded)
        } catch (e: IllegalArgumentException) {
            throw FunctionArgumentException("Invalid base64 string: $str")
        }
    }
    
    /**
     * URL encode
     * Usage: url-encode("hello world!")
     */
    fun urlEncode(args: List<UDM>): UDM {
        requireArgs(args, 1, "url-encode")
        val str = args[0].asString()
        val encoded = URLEncoder.encode(str, "UTF-8")
        return UDM.Scalar(encoded)
    }
    
    /**
     * URL decode
     * Usage: url-decode("hello+world%21")
     */
    fun urlDecode(args: List<UDM>): UDM {
        requireArgs(args, 1, "url-decode")
        val str = args[0].asString()
        val decoded = URLDecoder.decode(str, "UTF-8")
        return UDM.Scalar(decoded)
    }
    
    /**
     * Hex encode
     * Usage: hex-encode("Hello")
     */
    fun hexEncode(args: List<UDM>): UDM {
        requireArgs(args, 1, "hex-encode")
        val str = args[0].asString()
        val hex = str.toByteArray().joinToString("") { 
            "%02x".format(it) 
        }
        return UDM.Scalar(hex)
    }
    
    /**
     * Hex decode
     * Usage: hex-decode("48656c6c6f")
     */
    fun hexDecode(args: List<UDM>): UDM {
        requireArgs(args, 1, "hex-decode")
        val hex = args[0].asString()
        return try {
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            UDM.Scalar(String(bytes))
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid hex string: $hex")
        }
    }

        /**
     * Calculate MD5 hash of a string
     * 
     * Returns hexadecimal string representation of the MD5 hash.
     * 
     * @param str Input string to hash
     * @return MD5 hash as hex string (32 characters)
     * 
     * @example
     * ```kotlin
     * md5(UDM.Scalar("hello"))
     * // Returns: UDM.Scalar("5d41402abc4b2a76b9719d911017c592")
     * ```
     */
    fun md5(args: List<UDM>): UDM {
        requireArgs(args, 1, "md5")
        val str = args[0]
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw FunctionArgumentException("md5() requires a string argument")
        
        val md = MessageDigest.getInstance("MD5")
        val hashBytes = md.digest(value.toByteArray(Charsets.UTF_8))
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }
        
        return UDM.Scalar(hexString)
    }
    
    /**
     * Calculate SHA-256 hash of a string
     * 
     * Returns hexadecimal string representation of the SHA-256 hash.
     * SHA-256 is part of the SHA-2 family and is cryptographically secure.
     * 
     * @param str Input string to hash
     * @return SHA-256 hash as hex string (64 characters)
     * 
     * @example
     * ```kotlin
     * sha256(UDM.Scalar("hello"))
     * // Returns: UDM.Scalar("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")
     * ```
     */
    fun sha256(args: List<UDM>): UDM {
        requireArgs(args, 1, "sha256")
        val str = args[0]
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw FunctionArgumentException("sha256() requires a string argument")
        
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(value.toByteArray(Charsets.UTF_8))
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }
        
        return UDM.Scalar(hexString)
    }
    
    /**
     * Calculate SHA-512 hash of a string
     * 
     * Returns hexadecimal string representation of the SHA-512 hash.
     * SHA-512 is part of the SHA-2 family and provides higher security than SHA-256.
     * 
     * @param str Input string to hash
     * @return SHA-512 hash as hex string (128 characters)
     * 
     * @example
     * ```kotlin
     * sha512(UDM.Scalar("hello"))
     * // Returns: UDM.Scalar("9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043")
     * ```
     */
    fun sha512(args: List<UDM>): UDM {
        requireArgs(args, 1, "sha512")
        val str = args[0]
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw FunctionArgumentException("sha512() requires a string argument")
        
        val md = MessageDigest.getInstance("SHA-512")
        val hashBytes = md.digest(value.toByteArray(Charsets.UTF_8))
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }
        
        return UDM.Scalar(hexString)
    }
    
    /**
     * Calculate SHA-1 hash of a string (DEPRECATED - use SHA-256 instead)
     * 
     * SHA-1 is considered cryptographically broken and should not be used
     * for security purposes. Use SHA-256 or SHA-512 instead.
     * Provided for compatibility with legacy systems only.
     * 
     * @param str Input string to hash
     * @return SHA-1 hash as hex string (40 characters)
     * 
     * @deprecated Use sha256() or sha512() instead
     */
    @Deprecated(
        message = "SHA-1 is cryptographically broken. Use sha256() or sha512() instead.",
        replaceWith = ReplaceWith("sha256(str)")
    )
    fun sha1(args: List<UDM>): UDM {
        requireArgs(args, 1, "sha1")
        val str = args[0]
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw FunctionArgumentException("sha1() requires a string argument")
        
        val md = MessageDigest.getInstance("SHA-1")
        val hashBytes = md.digest(value.toByteArray(Charsets.UTF_8))
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }
        
        return UDM.Scalar(hexString)
    }
    
    /**
     * Calculate hash using specified algorithm
     * 
     * Generic hash function that supports multiple algorithms.
     * 
     * @param str Input string to hash
     * @param algorithm Hash algorithm name (e.g., "MD5", "SHA-256", "SHA-512")
     * @return Hash as hex string
     * 
     * @example
     * ```kotlin
     * hash(UDM.Scalar("hello"), UDM.Scalar("SHA-256"))
     * // Returns same as sha256("hello")
     * ```
     * 
     * Supported algorithms:
     * - MD5
     * - SHA-1 (deprecated)
     * - SHA-256
     * - SHA-512
     * - SHA-384
     */
    fun hash(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("hash expects at least 1 argument")
        }
        val str = args[0]
        val algorithm = if (args.size > 1) args[1] else UDM.Scalar("SHA-256")
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw FunctionArgumentException("hash() requires a string as first argument")
        
        val alg = (algorithm as? UDM.Scalar)?.value as? String
            ?: "SHA-256"
        
        try {
            val md = MessageDigest.getInstance(alg)
            val hashBytes = md.digest(value.toByteArray(Charsets.UTF_8))
            val hexString = hashBytes.joinToString("") { "%02x".format(it) }
            
            return UDM.Scalar(hexString)
        } catch (e: java.security.NoSuchAlgorithmException) {
            throw FunctionArgumentException("Unsupported hash algorithm: $alg. Supported: MD5, SHA-1, SHA-256, SHA-512")
        }
    }
    
    /**
     * Calculate HMAC (Hash-based Message Authentication Code)
     * 
     * HMAC provides message authentication using a cryptographic hash function
     * in combination with a secret key.
     * 
     * @param str Input string to hash
     * @param key Secret key for HMAC
     * @param algorithm Hash algorithm (default: "HmacSHA256")
     * @return HMAC as hex string
     * 
     * @example
     * ```kotlin
     * hmac(UDM.Scalar("hello"), UDM.Scalar("secret-key"), UDM.Scalar("HmacSHA256"))
     * // Returns HMAC-SHA256 hash
     * ```
     * 
     * Supported algorithms:
     * - HmacMD5
     * - HmacSHA1
     * - HmacSHA256 (recommended)
     * - HmacSHA512
     */
    fun hmac(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException("hmac expects at least 2 arguments")
        }
        val str = args[0]
        val key = args[1]
        val algorithm = if (args.size > 2) args[2] else UDM.Scalar("HmacSHA256")
        
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw FunctionArgumentException("hmac() requires a string as first argument")
        
        val keyValue = (key as? UDM.Scalar)?.value as? String
            ?: throw FunctionArgumentException("hmac() requires a key as second argument")
        
        val alg = (algorithm as? UDM.Scalar)?.value as? String ?: "HmacSHA256"
        
        try {
            val mac = javax.crypto.Mac.getInstance(alg)
            val secretKey = javax.crypto.spec.SecretKeySpec(
                keyValue.toByteArray(Charsets.UTF_8),
                alg
            )
            mac.init(secretKey)
            
            val hashBytes = mac.doFinal(value.toByteArray(Charsets.UTF_8))
            val hexString = hashBytes.joinToString("") { "%02x".format(it) }
            
            return UDM.Scalar(hexString)
        } catch (e: Exception) {
            throw IllegalArgumentException("HMAC calculation failed: ${e.message}", e)
        }
    }

    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value")
    }
}
