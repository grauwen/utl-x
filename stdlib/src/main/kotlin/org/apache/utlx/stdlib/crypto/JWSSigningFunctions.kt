package org.apache.utlx.stdlib.crypto

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * F11: JWS Signing and JWT Creation — completes the JWT/JWS story.
 *
 * No external dependencies — uses javax.crypto (JDK built-in).
 * Supports HMAC algorithms (HS256, HS384, HS512) for symmetric signing.
 * RSA algorithms (RS256, RS384, RS512) use RSAFunctions for key handling.
 */
object JWSSigningFunctions {

    private val base64Url = Base64.getUrlEncoder().withoutPadding()
    private val base64UrlDecoder = Base64.getUrlDecoder()

    // =========================================================================
    // createJWTSigned(claims, secret, algorithm?)
    // =========================================================================

    /**
     * Create a signed JWT token with multi-algorithm support (HS256/384/512).
     * claims: UDM object with JWT claims (sub, iss, exp, iat, etc.)
     * secret: signing key (string for HMAC)
     * algorithm: "HS256" (default), "HS384", "HS512"
     *
     * Note: Named createJWTSigned to avoid collision with JWTVerification.createJWT
     * which only supports HS256 but adds iat/exp automatically.
     */
    fun createJWTSigned(args: List<UDM>): UDM {
        if (args.size < 2) throw FunctionArgumentException("createJWTSigned expects 2-3 arguments (claims, secret, algorithm?), got ${args.size}")

        val claims = args[0]
        val secret = asString(args[1])
        val algorithm = if (args.size > 2) asString(args[2]) else "HS256"

        return try {
            // Build header
            val header = """{"alg":"$algorithm","typ":"JWT"}"""
            val headerB64 = base64Url.encodeToString(header.toByteArray(Charsets.UTF_8))

            // Build payload from UDM claims
            val payload = udmToJson(claims)
            val payloadB64 = base64Url.encodeToString(payload.toByteArray(Charsets.UTF_8))

            // Sign
            val signingInput = "$headerB64.$payloadB64"
            val signature = hmacSign(signingInput, secret, algorithm)

            UDM.Scalar("$signingInput.$signature")
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            throw FunctionArgumentException("createJWTSigned failed: ${e.message}")
        }
    }

    // =========================================================================
    // verifyJWTSignature(token, secret, algorithm?)
    // =========================================================================

    /**
     * Verify a JWT token's signature and return the claims if valid.
     * Supports HS256, HS384, HS512.
     * Returns: {valid: true/false, claims: {...}, header: {...}, error?: "..."}
     *
     * Note: Named verifyJWTSignature to avoid collision with JWTVerification.verifyJWT.
     * This version supports HS384/HS512 in addition to HS256.
     */
    fun verifyJWTSignature(args: List<UDM>): UDM {
        if (args.size < 2) throw FunctionArgumentException("verifyJWTSignature expects 2-3 arguments (token, secret, algorithm?), got ${args.size}")

        val token = asString(args[0])
        val secret = asString(args[1])
        val expectedAlgorithm = if (args.size > 2) asString(args[2]) else null

        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                return UDM.Object.of(
                    "valid" to UDM.Scalar(false),
                    "error" to UDM.Scalar("Invalid JWT format: expected 3 parts, got ${parts.size}")
                )
            }

            val headerB64 = parts[0]
            val payloadB64 = parts[1]
            val signatureB64 = parts[2]

            // Decode header to get algorithm
            val headerJson = String(base64UrlDecoder.decode(headerB64), Charsets.UTF_8)
            val algorithm = extractJsonField(headerJson, "alg")
                ?: return UDM.Object.of("valid" to UDM.Scalar(false), "error" to UDM.Scalar("Missing 'alg' in header"))

            // Check algorithm match if specified
            if (expectedAlgorithm != null && algorithm != expectedAlgorithm) {
                return UDM.Object.of(
                    "valid" to UDM.Scalar(false),
                    "error" to UDM.Scalar("Algorithm mismatch: token uses '$algorithm', expected '$expectedAlgorithm'")
                )
            }

            // Verify signature
            val signingInput = "$headerB64.$payloadB64"
            val expectedSignature = hmacSign(signingInput, secret, algorithm)

            if (signatureB64 != expectedSignature) {
                return UDM.Object.of("valid" to UDM.Scalar(false), "error" to UDM.Scalar("Signature verification failed"))
            }

            // Decode payload
            val payloadJson = String(base64UrlDecoder.decode(payloadB64), Charsets.UTF_8)

            UDM.Object.of(
                "valid" to UDM.Scalar(true),
                "header" to jsonStringToUDM(headerJson),
                "claims" to jsonStringToUDM(payloadJson)
            )
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            UDM.Object.of("valid" to UDM.Scalar(false), "error" to UDM.Scalar("Verification error: ${e.message}"))
        }
    }

    // =========================================================================
    // signJWS(payload, secret, algorithm?)
    // =========================================================================

    /**
     * Sign arbitrary data as a JWS compact serialization.
     * Returns the complete JWS token (header.payload.signature).
     */
    fun signJWS(args: List<UDM>): UDM {
        if (args.size < 2) throw FunctionArgumentException("signJWS expects 2-3 arguments (payload, secret, algorithm?), got ${args.size}")

        val payload = asString(args[0])
        val secret = asString(args[1])
        val algorithm = if (args.size > 2) asString(args[2]) else "HS256"

        return try {
            val header = """{"alg":"$algorithm"}"""
            val headerB64 = base64Url.encodeToString(header.toByteArray(Charsets.UTF_8))
            val payloadB64 = base64Url.encodeToString(payload.toByteArray(Charsets.UTF_8))

            val signingInput = "$headerB64.$payloadB64"
            val signature = hmacSign(signingInput, secret, algorithm)

            UDM.Scalar("$signingInput.$signature")
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            throw FunctionArgumentException("signJWS failed: ${e.message}")
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private fun hmacSign(data: String, secret: String, algorithm: String): String {
        val jcaAlgorithm = when (algorithm) {
            "HS256" -> "HmacSHA256"
            "HS384" -> "HmacSHA384"
            "HS512" -> "HmacSHA512"
            else -> throw FunctionArgumentException("Unsupported algorithm: '$algorithm'. Supported: HS256, HS384, HS512")
        }

        val mac = Mac.getInstance(jcaAlgorithm)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), jcaAlgorithm))
        val signature = mac.doFinal(data.toByteArray(Charsets.UTF_8))

        return base64Url.encodeToString(signature)
    }

    /**
     * Simple JSON field extractor — avoids external JSON dependency.
     * Only handles simple string fields like {"alg":"HS256"}.
     */
    private fun extractJsonField(json: String, field: String): String? {
        val pattern = """"$field"\s*:\s*"([^"]+)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    /**
     * Minimal UDM-to-JSON serializer for JWT claims.
     * Handles objects, arrays, strings, numbers, booleans, null.
     */
    private fun udmToJson(udm: UDM): String = when (udm) {
        is UDM.Scalar -> when (val v = udm.value) {
            null -> "null"
            is String -> "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            is Number -> v.toString()
            is Boolean -> v.toString()
            else -> "\"${v}\""
        }
        is UDM.Object -> {
            val entries = udm.properties.entries.joinToString(",") { (k, v) -> "\"$k\":${udmToJson(v)}" }
            "{$entries}"
        }
        is UDM.Array -> {
            val elements = udm.elements.joinToString(",") { udmToJson(it) }
            "[$elements]"
        }
        else -> "null"
    }

    /**
     * Minimal JSON-to-UDM parser for JWT header/claims.
     * Returns the JSON string as a UDM.Scalar for now — full parsing would need formats:json dependency.
     */
    private fun jsonStringToUDM(json: String): UDM {
        // Simple approach: parse key-value pairs from flat JSON objects
        // For nested objects, return as string
        if (!json.trimStart().startsWith("{")) return UDM.Scalar(json)

        val props = mutableMapOf<String, UDM>()
        val pattern = """"(\w+)"\s*:\s*("([^"]*)"|([\d.]+)|(true|false)|null)""".toRegex()
        for (match in pattern.findAll(json)) {
            val key = match.groupValues[1]
            val stringVal = match.groupValues[3]
            val numVal = match.groupValues[4]
            val boolVal = match.groupValues[5]

            props[key] = when {
                stringVal.isNotEmpty() -> UDM.Scalar(stringVal)
                numVal.isNotEmpty() -> {
                    if (numVal.contains(".")) UDM.Scalar(numVal.toDouble())
                    else UDM.Scalar(numVal.toLongOrNull() ?: numVal.toDouble())
                }
                boolVal.isNotEmpty() -> UDM.Scalar(boolVal.toBoolean())
                else -> UDM.Scalar.nullValue()
            }
        }
        return if (props.isNotEmpty()) UDM.Object(props) else UDM.Scalar(json)
    }

    private fun asString(udm: UDM): String = when (udm) {
        is UDM.Scalar -> udm.value?.toString() ?: throw FunctionArgumentException("Expected string, got null")
        else -> throw FunctionArgumentException("Expected string, got ${udm::class.simpleName}")
    }
}
