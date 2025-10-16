package org.apache.utlx.stdlib.jwt

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

object JWTFunctions {

      /**
       * Decodes a JWT token WITHOUT verification
       * SECURITY WARNING: Does not verify signature - use only for trusted tokens
       * 
       * @param token JWT token string
       * @return Object with header and payload (decoded but NOT verified)
       */
        fun decodeJWT(token: String): Map<String, Any?> {
            val parts = token.split(".")
            if (parts.size != 3) {
                throw IllegalArgumentException("Invalid JWT format")
            }
            
            return mapOf(
                "header" to decodeJWTPart(parts[0]),
                "payload" to decodeJWTPart(parts[1]),
                "signature" to parts[2],
                "verified" to false  // Explicitly mark as unverified
            )
        }
        
        /**
         * Extracts claims from JWT payload WITHOUT verification
         * SECURITY WARNING: Does not verify signature - use only for trusted tokens
         * 
         * @param token JWT token string
         * @return Decoded claims object
         */
        fun getJWTClaims(token: String): Map<String, Any?> {
            return decodeJWT(token)["payload"] as Map<String, Any?>
        }
        
        /**
         * Gets a specific claim from JWT
         * SECURITY WARNING: Does not verify signature
         */
        fun getJWTClaim(token: String, claimName: String): Any? {
            return getJWTClaims(token)[claimName]
        }

        /**
         * Checks if JWT is expired based on 'exp' claim
         * SECURITY WARNING: Does not verify signature
         */
        fun isJWTExpired(token: String): Boolean {
            val claims = getJWTClaims(token)
            val exp = claims["exp"] as? Long ?: return false
            return System.currentTimeMillis() / 1000 > exp
        }

     // Helper functions

    private fun decodeJWTPart(part: String): Map<String, Any?> {
        val decoded = base64UrlDecode(part)
        return parseJSON(decoded)
    }
    
    private fun base64UrlDecode(str: String): String {
        val base64 = str.replace('-', '+').replace('_', '/')
        val padding = "=".repeat((4 - base64.length % 4) % 4)
        return String(Base64.getDecoder().decode(base64 + padding))
    }



}

