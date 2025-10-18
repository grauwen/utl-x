package org.apache.utlx.stdlib.jwt

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import java.util.Base64
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * JWT (JSON Web Token) Functions for UTL-X
 * 
 * SECURITY WARNING: These functions decode JWT tokens WITHOUT verification.
 * They should only be used for trusted tokens or in development environments.
 * For production use, implement proper JWT verification with signature validation.
 */
object JWTFunctions {

    @UTLXFunction(
        description = "Decodes a JWT token WITHOUT verification",
        minArgs = 1,
        maxArgs = 1,
        category = "Security",
        parameters = [
            "token: Token value"
        ],
        returns = "Result of the operation",
        example = "decodeJWT(...) => result",
        notes = "SECURITY WARNING: Does not verify signature - use only for trusted tokens\nExample:\n```\ndecodeJWT(\"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...\")\n// Returns: {\n//   \"header\": {...},\n//   \"payload\": {...},\n//   \"signature\": \"...\",\n//   \"verified\": false\n// }\n```",
        tags = ["security"],
        since = "1.0"
    )
    /**
     * Decodes a JWT token WITHOUT verification
     * SECURITY WARNING: Does not verify signature - use only for trusted tokens
     * 
     * @param args List containing JWT token string
     * @return UDM Object with header, payload, signature, and verified flag
     * 
     * Example:
     * ```
     * decodeJWT("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...")
     * // Returns: {
     * //   "header": {...},
     * //   "payload": {...},
     * //   "signature": "...",
     * //   "verified": false
     * // }
     * ```
     */
    fun decodeJWT(args: List<UDM>): UDM {
        requireArgs(args, 1, "decodeJWT")
        val token = args[0].asString()
        
        val parts = token.split(".")
        if (parts.size != 3) {
            throw FunctionArgumentException("Invalid JWT format - expected 3 parts separated by dots")
        }
        
        return try {
            val header = decodeJWTPart(parts[0])
            val payload = decodeJWTPart(parts[1])
            
            UDM.Object(mapOf(
                "header" to header,
                "payload" to payload,
                "signature" to UDM.Scalar(parts[2]),
                "verified" to UDM.Scalar(false)  // Explicitly mark as unverified
            ))
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to decode JWT: ${e.message}")
        }
    }
    
    @UTLXFunction(
        description = "Extracts claims from JWT payload WITHOUT verification",
        minArgs = 1,
        maxArgs = 1,
        category = "Security",
        parameters = [
            "token: Token value",
        "claimName: Claimname value"
        ],
        returns = "Result of the operation",
        example = "getJWTClaims(...) => result",
        notes = "SECURITY WARNING: Does not verify signature - use only for trusted tokens\nExample:\n```\ngetJWTClaims(\"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...\")\n// Returns: {\"sub\": \"1234567890\", \"name\": \"John Doe\", \"iat\": 1516239022}\n```",
        tags = ["security"],
        since = "1.0"
    )
    /**
     * Extracts claims from JWT payload WITHOUT verification
     * SECURITY WARNING: Does not verify signature - use only for trusted tokens
     * 
     * @param args List containing JWT token string
     * @return UDM Object with decoded claims
     * 
     * Example:
     * ```
     * getJWTClaims("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...")
     * // Returns: {"sub": "1234567890", "name": "John Doe", "iat": 1516239022}
     * ```
     */
    fun getJWTClaims(args: List<UDM>): UDM {
        requireArgs(args, 1, "getJWTClaims")
        val token = args[0].asString()
        
        val parts = token.split(".")
        if (parts.size != 3) {
            throw FunctionArgumentException("Invalid JWT format")
        }
        
        return try {
            decodeJWTPart(parts[1])
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to decode JWT claims: ${e.message}")
        }
    }
    
    @UTLXFunction(
        description = "Gets a specific claim from JWT",
        minArgs = 1,
        maxArgs = 1,
        category = "Security",
        parameters = [
            "token: Token value",
        "claimName: Claimname value"
        ],
        returns = "Result of the operation",
        example = "getJWTClaim(...) => result",
        notes = "SECURITY WARNING: Does not verify signature\nExample:\n```\ngetJWTClaim(\"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...\", \"sub\")\n// Returns: \"1234567890\"\n```",
        tags = ["security"],
        since = "1.0"
    )
    /**
     * Gets a specific claim from JWT
     * SECURITY WARNING: Does not verify signature
     * 
     * @param args List containing JWT token string and claim name
     * @return UDM value of the claim, or null if not found
     * 
     * Example:
     * ```
     * getJWTClaim("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...", "sub")
     * // Returns: "1234567890"
     * ```
     */
    fun getJWTClaim(args: List<UDM>): UDM {
        requireArgs(args, 2, "getJWTClaim")
        val claimName = args[1].asString()
        
        val claims = getJWTClaims(listOf(args[0]))
        return when (claims) {
            is UDM.Object -> claims.properties[claimName] ?: UDM.Scalar(null)
            else -> UDM.Scalar(null)
        }
    }

    @UTLXFunction(
        description = "Checks if JWT is expired based on 'exp' claim",
        minArgs = 1,
        maxArgs = 1,
        category = "Security",
        parameters = [
            "token: Token value"
        ],
        returns = "Boolean indicating the result",
        example = "isJWTExpired(...) => result",
        notes = "SECURITY WARNING: Does not verify signature\nExample:\n```\nisJWTExpired(\"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...\")\n// Returns: true or false\n```",
        tags = ["security"],
        since = "1.0"
    )
    /**
     * Checks if JWT is expired based on 'exp' claim
     * SECURITY WARNING: Does not verify signature
     * 
     * @param args List containing JWT token string
     * @return UDM Boolean indicating if token is expired
     * 
     * Example:
     * ```
     * isJWTExpired("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...")
     * // Returns: true or false
     * ```
     */
    fun isJWTExpired(args: List<UDM>): UDM {
        requireArgs(args, 1, "isJWTExpired")
        val token = args[0]
        
        val expClaim = getJWTClaim(listOf(token, UDM.Scalar("exp")))
        
        return when (expClaim) {
            is UDM.Scalar -> {
                val exp = expClaim.value
                when (exp) {
                    is Number -> {
                        val currentTime = System.currentTimeMillis() / 1000
                        UDM.Scalar(currentTime > exp.toLong())
                    }
                    else -> UDM.Scalar(false) // No valid expiration claim
                }
            }
            else -> UDM.Scalar(false)
        }
    }

    @UTLXFunction(
        description = "Gets the subject (sub) claim from JWT",
        minArgs = 1,
        maxArgs = 1,
        category = "Security",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "getJWTSubject(...) => result",
        notes = "SECURITY WARNING: Does not verify signature",
        tags = ["security"],
        since = "1.0"
    )
    /**
     * Gets the subject (sub) claim from JWT
     * SECURITY WARNING: Does not verify signature
     * 
     * @param args List containing JWT token string
     * @return UDM String with subject, or null if not found
     */
    fun getJWTSubject(args: List<UDM>): UDM {
        requireArgs(args, 1, "getJWTSubject")
        return getJWTClaim(listOf(args[0], UDM.Scalar("sub")))
    }

    @UTLXFunction(
        description = "Gets the issuer (iss) claim from JWT",
        minArgs = 1,
        maxArgs = 1,
        category = "Security",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "getJWTIssuer(...) => result",
        notes = "SECURITY WARNING: Does not verify signature",
        tags = ["security"],
        since = "1.0"
    )
    /**
     * Gets the issuer (iss) claim from JWT
     * SECURITY WARNING: Does not verify signature
     * 
     * @param args List containing JWT token string
     * @return UDM String with issuer, or null if not found
     */
    fun getJWTIssuer(args: List<UDM>): UDM {
        requireArgs(args, 1, "getJWTIssuer")
        return getJWTClaim(listOf(args[0], UDM.Scalar("iss")))
    }

    @UTLXFunction(
        description = "Gets the audience (aud) claim from JWT",
        minArgs = 1,
        maxArgs = 1,
        category = "Security",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "getJWTAudience(...) => result",
        notes = "SECURITY WARNING: Does not verify signature",
        tags = ["security"],
        since = "1.0"
    )
    /**
     * Gets the audience (aud) claim from JWT
     * SECURITY WARNING: Does not verify signature
     * 
     * @param args List containing JWT token string
     * @return UDM String or Array with audience, or null if not found
     */
    fun getJWTAudience(args: List<UDM>): UDM {
        requireArgs(args, 1, "getJWTAudience")
        return getJWTClaim(listOf(args[0], UDM.Scalar("aud")))
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

    private fun decodeJWTPart(part: String): UDM {
        val decoded = base64UrlDecode(part)
        return parseJSON(decoded)
    }
    
    private fun base64UrlDecode(str: String): String {
        val base64 = str.replace('-', '+').replace('_', '/')
        val padding = "=".repeat((4 - base64.length % 4) % 4)
        return String(Base64.getDecoder().decode(base64 + padding))
    }
    
    private fun parseJSON(jsonString: String): UDM {
        return try {
            // Simple JSON parsing for JWT headers and payloads
            // This is a basic implementation - for production use, consider a proper JSON library
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                parseJSONObject(trimmed)
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                parseJSONArray(trimmed)
            } else {
                UDM.Scalar(jsonString) // Return as string if not valid JSON
            }
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to parse JSON: ${e.message}")
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

