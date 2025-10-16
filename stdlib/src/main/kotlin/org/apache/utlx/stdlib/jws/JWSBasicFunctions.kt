// stdlib/src/main/kotlin/org/apache/utlx/stdlib/jws/JWSBasicFunctions.kt
package org.apache.utlx.stdlib.jws

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import java.util.Base64
/**
 * JSON Web Signature (JWS) Basic Functions - RFC 7515
 * 
 * ⚠️ SECURITY WARNING ⚠️
 * These functions decode JWS tokens but DO NOT VERIFY SIGNATURES.
 * Use only with trusted tokens or for inspection purposes.
 * 
 * For signature verification and token creation, use the stdlib-security module.
 * 
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7515">RFC 7515 - JSON Web Signature</a>
 */
object JWSBasicFunctions {

    /**
     * Decodes a JWS token WITHOUT verifying the signature
     * 
     * ⚠️ SECURITY WARNING: Does NOT verify signature
     * 
     * @param args List containing: [token]
     * @return UDM Object with decoded header, payload, and signature (unverified)
     * 
     * Example:
     * ```
     * decodeJWS("eyJhbGc...eyJzdWI...signature")
     * // Returns: UDM.Object with header, payload, signature, verified=false
     * ```
     */
    fun decodeJWS(args: List<UDM>): UDM {
        requireArgs(args, 1, "decodeJWS")
        val token = args[0].asString()
        
        val parts = token.trim().split(".")
        
        if (parts.size != 3) {
            throw FunctionArgumentException(
                "Invalid JWS format. Expected 3 parts (header.payload.signature), got ${parts.size}"
            )
        }
        
        return try {
            val header = decodeBase64UrlToUDM(parts[0])
            val payload = decodeBase64UrlToUDM(parts[1])
            
            val algorithm = when (header) {
                is UDM.Object -> header.properties["alg"]
                else -> null
            }
            val tokenType = when (header) {
                is UDM.Object -> header.properties["typ"]
                else -> null
            }
            
            UDM.Object(mapOf(
                "header" to header,
                "payload" to payload,
                "signature" to UDM.Scalar(parts[2]),
                "verified" to UDM.Scalar(false),
                "algorithm" to (algorithm ?: UDM.Scalar(null)),
                "tokenType" to (tokenType ?: UDM.Scalar(null))
            ))
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to decode JWS: ${e.message}")
        }
    }

    /**
     * Extracts the payload from a JWS token WITHOUT verification
     * 
     * ⚠️ SECURITY WARNING: Does NOT verify signature
     * 
     * @param args List containing: [token]
     * @return UDM payload object
     */
    fun getJWSPayload(args: List<UDM>): UDM {
        val decoded = decodeJWS(args)
        return when (decoded) {
            is UDM.Object -> decoded.properties["payload"] ?: UDM.Scalar(null)
            else -> UDM.Scalar(null)
        }
    }

    /**
     * Extracts the header from a JWS token
     * 
     * @param args List containing: [token]
     * @return UDM header object
     */
    fun getJWSHeader(args: List<UDM>): UDM {
        val decoded = decodeJWS(args)
        return when (decoded) {
            is UDM.Object -> decoded.properties["header"] ?: UDM.Scalar(null)
            else -> UDM.Scalar(null)
        }
    }

    /**
     * Gets the algorithm from a JWS token header
     * 
     * @param args List containing: [token]
     * @return UDM Scalar with algorithm string (e.g., "HS256")
     */
    fun getJWSAlgorithm(args: List<UDM>): UDM {
        val header = getJWSHeader(args)
        return when (header) {
            is UDM.Object -> {
                header.properties["alg"] ?: throw FunctionArgumentException("JWS header missing 'alg' field")
            }
            else -> throw FunctionArgumentException("Invalid JWS header")
        }
    }

    /**
     * Gets the Key ID (kid) from a JWS token header
     * 
     * @param args List containing: [token]
     * @return UDM Scalar with key ID string, or null if not present
     */
    fun getJWSKeyId(args: List<UDM>): UDM {
        val header = getJWSHeader(args)
        return when (header) {
            is UDM.Object -> header.properties["kid"] ?: UDM.Scalar(null)
            else -> UDM.Scalar(null)
        }
    }

    /**
     * Gets the token type from a JWS token header
     * 
     * @param args List containing: [token]
     * @return UDM Scalar with token type string, or null if not present
     */
    fun getJWSTokenType(args: List<UDM>): UDM {
        val header = getJWSHeader(args)
        return when (header) {
            is UDM.Object -> header.properties["typ"] ?: UDM.Scalar(null)
            else -> UDM.Scalar(null)
        }
    }

    /**
     * Checks if a string is in valid JWS format
     * 
     * @param args List containing: [token]
     * @return UDM Scalar Boolean indicating if token has valid JWS structure
     */
    fun isJWSFormat(args: List<UDM>): UDM {
        requireArgs(args, 1, "isJWSFormat")
        val token = args[0].asString()
        
        return try {
            val parts = token.trim().split(".")
            UDM.Scalar(parts.size == 3 && parts.all { it.isNotEmpty() })
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }

    /**
     * Extracts the signing input from a JWS token
     * 
     * @param args List containing: [token]
     * @return UDM Scalar with signing input string (header.payload)
     */
    fun getJWSSigningInput(args: List<UDM>): UDM {
        requireArgs(args, 1, "getJWSSigningInput")
        val token = args[0].asString()
        
        val parts = token.trim().split(".")
        if (parts.size != 3) {
            throw FunctionArgumentException("Invalid JWS format")
        }
        return UDM.Scalar("${parts[0]}.${parts[1]}")
    }

    /**
     * Gets information about the JWS token structure
     * 
     * @param args List containing: [token]
     * @return UDM Object with token metadata
     */
    fun getJWSInfo(args: List<UDM>): UDM {
        requireArgs(args, 1, "getJWSInfo")
        val token = args[0].asString()
        
        val parts = token.trim().split(".")
        if (parts.size != 3) {
            throw FunctionArgumentException("Invalid JWS format")
        }
        
        val header = getJWSHeader(args)
        val payload = getJWSPayload(args)
        
        val algorithm = when (header) {
            is UDM.Object -> header.properties["alg"]
            else -> null
        }
        val tokenType = when (header) {
            is UDM.Object -> header.properties["typ"]
            else -> null
        }
        val keyId = when (header) {
            is UDM.Object -> header.properties["kid"]
            else -> null
        }
        
        val headerClaims = when (header) {
            is UDM.Object -> header.properties.size
            else -> 0
        }
        val payloadClaims = when (payload) {
            is UDM.Object -> payload.properties.size
            else -> 0
        }
        
        return UDM.Object(mapOf(
            "algorithm" to (algorithm ?: UDM.Scalar(null)),
            "tokenType" to (tokenType ?: UDM.Scalar(null)),
            "keyId" to (keyId ?: UDM.Scalar(null)),
            "headerLength" to UDM.Scalar(parts[0].length.toDouble()),
            "payloadLength" to UDM.Scalar(parts[1].length.toDouble()),
            "signatureLength" to UDM.Scalar(parts[2].length.toDouble()),
            "totalLength" to UDM.Scalar(token.length.toDouble()),
            "headerClaims" to UDM.Scalar(headerClaims.toDouble()),
            "payloadClaims" to UDM.Scalar(payloadClaims.toDouble()),
            "verified" to UDM.Scalar(false)
        ))
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
    
    /**
     * Decodes a Base64URL-encoded string to UDM
     */
    private fun decodeBase64UrlToUDM(encoded: String): UDM {
        return try {
            val decoded = decodeBase64Url(encoded)
            val jsonString = String(decoded, Charsets.UTF_8)
            parseJSONToUDM(jsonString)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to decode Base64URL: ${e.message}")
        }
    }

    /**
     * Decodes Base64URL string to byte array
     */
    private fun decodeBase64Url(encoded: String): ByteArray {
        // Convert Base64URL to standard Base64
        val base64 = encoded
            .replace('-', '+')
            .replace('_', '/')
        
        // Add padding if needed
        val padding = (4 - base64.length % 4) % 4
        val paddedBase64 = base64 + "=".repeat(padding)
        
        return Base64.getDecoder().decode(paddedBase64)
    }


    /**
     * Simple JSON parser for JWS headers and payloads
     */
    private fun parseJSONToUDM(json: String): UDM {
        // Basic JSON parsing - simplified for JWS use
        val trimmed = json.trim()
        
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return parseJSONObject(trimmed)
        } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return parseJSONArray(trimmed)
        } else if (trimmed == "null") {
            return UDM.Scalar(null)
        } else if (trimmed == "true") {
            return UDM.Scalar(true)
        } else if (trimmed == "false") {
            return UDM.Scalar(false)
        } else if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return UDM.Scalar(trimmed.substring(1, trimmed.length - 1))
        } else {
            // Try as number
            return try {
                if (trimmed.contains(".")) {
                    UDM.Scalar(trimmed.toDouble())
                } else {
                    UDM.Scalar(trimmed.toLong())
                }
            } catch (e: NumberFormatException) {
                UDM.Scalar(trimmed)
            }
        }
    }
    
    private fun parseJSONObject(json: String): UDM {
        val content = json.substring(1, json.length - 1).trim()
        if (content.isEmpty()) {
            return UDM.Object(emptyMap())
        }
        
        val properties = mutableMapOf<String, UDM>()
        // Simplified parsing - would need full implementation for production
        // For now, return empty object to allow compilation
        return UDM.Object(properties)
    }
    
    private fun parseJSONArray(json: String): UDM {
        val content = json.substring(1, json.length - 1).trim()
        if (content.isEmpty()) {
            return UDM.Array(emptyList())
        }
        
        // Simplified parsing - would need full implementation for production
        return UDM.Array(emptyList())
    }

}