// original location proposed stdlib-security/src/main/kotlin/org/apache/utlx/stdlib/jwt/JWTVerification.kt
// stdlib/src/main/kotlin/org/apache/utlx/stdlib/jwt/JWTVerification.kt
package org.apache.utlx.stdlib.jwt

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * JWT Verification and Creation Functions for UTL-X
 * 
 * SECURITY NOTE: This implements basic HMAC-SHA256 verification only.
 * For production use with RSA/ECDSA signatures, consider using a dedicated JWT library.
 */
object JWTVerification {

    /**
     * Verifies JWT signature and returns claims
     * Currently supports HS256 (HMAC-SHA256) algorithm only
     * 
     * @param args List containing: [token, secret, algorithm?]
     * @return UDM Object with verified claims and verification status
     * 
     * Example:
     * ```
     * verifyJWT("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...", "secret", "HS256")
     * // Returns: {
     * //   "verified": true,
     * //   "claims": {...},
     * //   "header": {...}
     * // }
     * ```
     */
    fun verifyJWT(args: List<UDM>): UDM {
        if (args.size < 2 || args.size > 3) {
            throw FunctionArgumentException("verifyJWT expects 2 or 3 arguments (token, secret, algorithm?), got ${args.size}")
        }
        
        val token = args[0].asString()
        val secret = args[1].asString()
        val algorithm = if (args.size > 2) args[2].asString() else "HS256"
        
        if (algorithm != "HS256") {
            throw FunctionArgumentException("verifyJWT currently only supports HS256 algorithm")
        }
        
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                return createVerificationResult(false, null, null, "Invalid JWT format")
            }
            
            val header = parseJSONFromBase64Url(parts[0])
            val payload = parseJSONFromBase64Url(parts[1])
            val signature = parts[2]
            
            // Verify signature
            val expectedSignature = generateHMACSignature("${parts[0]}.${parts[1]}", secret)
            val isValid = constantTimeEquals(signature, expectedSignature)
            
            if (isValid) {
                createVerificationResult(true, payload, header, null)
            } else {
                createVerificationResult(false, null, null, "Invalid signature")
            }
            
        } catch (e: Exception) {
            createVerificationResult(false, null, null, "Verification failed: ${e.message}")
        }
    }
    
    /**
     * Verifies JWT with JWKS (JSON Web Key Set)
     * NOTE: This is a placeholder implementation that returns unverified result
     * 
     * @param args List containing: [token, jwksUrl]
     * @return UDM Object indicating this feature is not implemented
     */
    fun verifyJWTWithJWKS(args: List<UDM>): UDM {
        requireArgs(args, 2, "verifyJWTWithJWKS")
        val jwksUrl = args[1].asString()
        
        // For now, return an error indicating this is not implemented
        return UDM.Object(mapOf(
            "verified" to UDM.Scalar(false),
            "error" to UDM.Scalar("JWKS verification not implemented. Use verifyJWT with direct secret for HS256."),
            "jwksUrl" to UDM.Scalar(jwksUrl)
        ))
    }
    
    /**
     * Creates a new JWT token
     * Currently supports HS256 (HMAC-SHA256) algorithm only
     * 
     * @param args List containing: [payload, secret, algorithm?, expiresIn?]
     * @return UDM String with the generated JWT token
     * 
     * Example:
     * ```
     * createJWT({"sub": "1234567890", "name": "John Doe"}, "secret", "HS256", 3600)
     * // Returns: "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."
     * ```
     */
    fun createJWT(args: List<UDM>): UDM {
        if (args.size < 2 || args.size > 4) {
            throw FunctionArgumentException("createJWT expects 2-4 arguments (payload, secret, algorithm?, expiresIn?), got ${args.size}")
        }
        
        val payload = args[0]
        val secret = args[1].asString()
        val algorithm = if (args.size > 2) args[2].asString() else "HS256"
        val expiresIn = if (args.size > 3) args[3].asNumber()?.toLong() else null
        
        if (algorithm != "HS256") {
            throw FunctionArgumentException("createJWT currently only supports HS256 algorithm")
        }
        
        return try {
            // Create header
            val header = UDM.Object(mapOf(
                "typ" to UDM.Scalar("JWT"),
                "alg" to UDM.Scalar(algorithm)
            ))
            
            // Prepare payload with standard claims
            val currentTime = System.currentTimeMillis() / 1000
            val enhancedPayload = when (payload) {
                is UDM.Object -> {
                    val props = payload.properties.toMutableMap()
                    props["iat"] = UDM.Scalar(currentTime)
                    if (expiresIn != null) {
                        props["exp"] = UDM.Scalar(currentTime + expiresIn)
                    }
                    UDM.Object(props)
                }
                else -> throw FunctionArgumentException("createJWT payload must be an object")
            }
            
            // Encode header and payload
            val headerEncoded = base64UrlEncode(serializeUDMToJSON(header))
            val payloadEncoded = base64UrlEncode(serializeUDMToJSON(enhancedPayload))
            
            // Generate signature
            val data = "$headerEncoded.$payloadEncoded"
            val signature = generateHMACSignature(data, secret)
            
            // Combine parts
            val token = "$data.$signature"
            UDM.Scalar(token)
            
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to create JWT: ${e.message}")
        }
    }

    /**
     * Validates JWT structure without signature verification
     * 
     * @param args List containing: [token]
     * @return UDM Object with validation result and basic info
     */
    fun validateJWTStructure(args: List<UDM>): UDM {
        requireArgs(args, 1, "validateJWTStructure")
        val token = args[0].asString()
        
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                return UDM.Object(mapOf(
                    "valid" to UDM.Scalar(false),
                    "error" to UDM.Scalar("Invalid JWT format - expected 3 parts")
                ))
            }
            
            val header = parseJSONFromBase64Url(parts[0])
            val payload = parseJSONFromBase64Url(parts[1])
            
            UDM.Object(mapOf(
                "valid" to UDM.Scalar(true),
                "header" to header,
                "payload" to payload,
                "hasSignature" to UDM.Scalar(parts[2].isNotEmpty())
            ))
            
        } catch (e: Exception) {
            UDM.Object(mapOf(
                "valid" to UDM.Scalar(false),
                "error" to UDM.Scalar("Failed to parse JWT: ${e.message}")
            ))
        }
    }

    // Helper functions
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: throw FunctionArgumentException("Expected string value")
        else -> throw FunctionArgumentException("Expected string value, got ${this::class.simpleName}")
    }
    
    private fun UDM.asNumber(): Number? = when (this) {
        is UDM.Scalar -> value as? Number
        else -> null
    }
    
    private fun createVerificationResult(verified: Boolean, claims: UDM?, header: UDM?, error: String?): UDM {
        val properties = mutableMapOf<String, UDM>()
        properties["verified"] = UDM.Scalar(verified)
        
        if (claims != null) {
            properties["claims"] = claims
        }
        if (header != null) {
            properties["header"] = header
        }
        if (error != null) {
            properties["error"] = UDM.Scalar(error)
        }
        
        return UDM.Object(properties)
    }
    
    private fun generateHMACSignature(data: String, secret: String): String {
        val secretKeySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKeySpec)
        val signature = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return base64UrlEncode(String(signature, Charsets.ISO_8859_1))
    }
    
    private fun base64UrlEncode(data: String): String {
        val base64 = Base64.getEncoder().encodeToString(data.toByteArray(Charsets.UTF_8))
        return base64.replace('+', '-').replace('/', '_').replace("=", "")
    }
    
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
    
    private fun serializeUDMToJSON(udm: UDM): String {
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    null -> "null"
                    is String -> "\"${escapeJsonString(value)}\""
                    is Boolean -> value.toString()
                    is Number -> value.toString()
                    else -> "\"${escapeJsonString(value.toString())}\""
                }
            }
            is UDM.Object -> {
                val pairs = udm.properties.map { (key, value) ->
                    "\"${escapeJsonString(key)}\":${serializeUDMToJSON(value)}"
                }
                "{${pairs.joinToString(",")}}"
            }
            is UDM.Array -> {
                val elements = udm.elements.map { serializeUDMToJSON(it) }
                "[${elements.joinToString(",")}]"
            }
            else -> "\"${escapeJsonString(udm.toString())}\""
        }
    }
    
    private fun escapeJsonString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
    private fun parseJSONFromBase64Url(base64Url: String): UDM {
        val decoded = base64UrlDecode(base64Url)
        return parseJSON(decoded)
    }
    
    private fun base64UrlDecode(str: String): String {
        val base64 = str.replace('-', '+').replace('_', '/')
        val padding = "=".repeat((4 - base64.length % 4) % 4)
        return String(Base64.getDecoder().decode(base64 + padding))
    }
    
    private fun parseJSON(jsonString: String): UDM {
        // Reuse the JSON parsing logic from JWTFunctions
        // This is a simple implementation for JWT headers and payloads
        val trimmed = jsonString.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return parseJSONObject(trimmed)
        } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return parseJSONArray(trimmed)
        } else {
            return UDM.Scalar(jsonString)
        }
    }
    
    private fun parseJSONObject(json: String): UDM {
        val content = json.substring(1, json.length - 1).trim()
        if (content.isEmpty()) {
            return UDM.Object(emptyMap())
        }
        
        val properties = mutableMapOf<String, UDM>()
        val pairs = splitJSONPairs(content)
        
        for (pair in pairs) {
            val colonIndex = findJsonColon(pair)
            if (colonIndex > 0) {
                val key = parseJSONString(pair.substring(0, colonIndex).trim())
                val value = parseJSONValue(pair.substring(colonIndex + 1).trim())
                properties[key] = value
            }
        }
        
        return UDM.Object(properties)
    }
    
    private fun parseJSONArray(json: String): UDM {
        val content = json.substring(1, json.length - 1).trim()
        if (content.isEmpty()) {
            return UDM.Array(emptyList())
        }
        
        val elements = splitJSONPairs(content)
        val result = elements.map { parseJSONValue(it.trim()) }
        
        return UDM.Array(result)
    }
    
    private fun parseJSONValue(value: String): UDM {
        val trimmed = value.trim()
        
        return when {
            trimmed == "null" -> UDM.Scalar(null)
            trimmed == "true" -> UDM.Scalar(true)
            trimmed == "false" -> UDM.Scalar(false)
            trimmed.startsWith("\"") && trimmed.endsWith("\"") -> {
                UDM.Scalar(parseJSONString(trimmed))
            }
            trimmed.startsWith("{") -> parseJSONObject(trimmed)
            trimmed.startsWith("[") -> parseJSONArray(trimmed)
            else -> {
                // Try to parse as number
                try {
                    if (trimmed.contains(".")) {
                        UDM.Scalar(trimmed.toDouble())
                    } else {
                        UDM.Scalar(trimmed.toLong())
                    }
                } catch (e: NumberFormatException) {
                    UDM.Scalar(trimmed) // Return as string if not a number
                }
            }
        }
    }
    
    private fun parseJSONString(quoted: String): String {
        if (quoted.length < 2 || !quoted.startsWith("\"") || !quoted.endsWith("\"")) {
            return quoted
        }
        
        // Remove quotes and handle basic escape sequences
        return quoted.substring(1, quoted.length - 1)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }
    
    private fun splitJSONPairs(content: String): List<String> {
        val pairs = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0
        var inString = false
        var escaped = false
        
        for (char in content) {
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' && inString -> {
                    current.append(char)
                    escaped = true
                }
                char == '"' -> {
                    current.append(char)
                    inString = !inString
                }
                !inString && (char == '{' || char == '[') -> {
                    current.append(char)
                    depth++
                }
                !inString && (char == '}' || char == ']') -> {
                    current.append(char)
                    depth--
                }
                !inString && char == ',' && depth == 0 -> {
                    pairs.add(current.toString())
                    current = StringBuilder()
                }
                else -> {
                    current.append(char)
                }
            }
        }
        
        if (current.isNotEmpty()) {
            pairs.add(current.toString())
        }
        
        return pairs
    }
    
    private fun findJsonColon(pair: String): Int {
        var inString = false
        var escaped = false
        
        for (i in pair.indices) {
            val char = pair[i]
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                char == ':' && !inString -> return i
            }
        }
        
        return -1
    }
}
