package org.apache.utlx.stdlib.jwt

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JWTFunctionsTest {

    // Sample JWT token (header.payload.signature)
    // Header: {"typ":"JWT","alg":"HS256"}
    // Payload: {"sub":"1234567890","name":"John Doe","iat":1516239022}
    private val sampleJWT = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

    @Test
    fun testDecodeJWT() {
        val result = JWTFunctions.decodeJWT(listOf(UDM.Scalar(sampleJWT)))
        
        assertTrue(result is UDM.Object)
        val decoded = result as UDM.Object
        
        // Check structure
        assertTrue(decoded.properties.containsKey("header"))
        assertTrue(decoded.properties.containsKey("payload"))
        assertTrue(decoded.properties.containsKey("signature"))
        assertTrue(decoded.properties.containsKey("verified"))
        
        // Check verified flag is false (security warning compliance)
        assertEquals(false, (decoded.properties["verified"] as UDM.Scalar).value)
        
        // Check header
        val header = decoded.properties["header"] as UDM.Object
        assertEquals("JWT", (header.properties["typ"] as UDM.Scalar).value)
        assertEquals("HS256", (header.properties["alg"] as UDM.Scalar).value)
        
        // Check payload
        val payload = decoded.properties["payload"] as UDM.Object
        assertEquals("1234567890", (payload.properties["sub"] as UDM.Scalar).value)
        assertEquals("John Doe", (payload.properties["name"] as UDM.Scalar).value)
        assertEquals(1516239022L, (payload.properties["iat"] as UDM.Scalar).value)
        
        // Check signature
        assertEquals("SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c", 
                    (decoded.properties["signature"] as UDM.Scalar).value)
    }

    @Test
    fun testGetJWTClaims() {
        val result = JWTFunctions.getJWTClaims(listOf(UDM.Scalar(sampleJWT)))
        
        assertTrue(result is UDM.Object)
        val claims = result as UDM.Object
        
        assertEquals("1234567890", (claims.properties["sub"] as UDM.Scalar).value)
        assertEquals("John Doe", (claims.properties["name"] as UDM.Scalar).value)
        assertEquals(1516239022L, (claims.properties["iat"] as UDM.Scalar).value)
    }

    @Test
    fun testGetJWTSubject() {
        val result = JWTFunctions.getJWTSubject(listOf(UDM.Scalar(sampleJWT)))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("1234567890", (result as UDM.Scalar).value)
    }

    @Test
    fun testGetJWTIssuer() {
        // Test with JWT that doesn't have issuer
        val result = JWTFunctions.getJWTIssuer(listOf(UDM.Scalar(sampleJWT)))
        
        // Should return null for JWT without issuer
        assertTrue(result is UDM.Scalar)
        assertTrue((result as UDM.Scalar).value == null)
    }

    @Test
    fun testGetJWTAudience() {
        // Test with JWT that doesn't have audience
        val result = JWTFunctions.getJWTAudience(listOf(UDM.Scalar(sampleJWT)))
        
        // Should return null for JWT without audience
        assertTrue(result is UDM.Scalar)
        assertTrue((result as UDM.Scalar).value == null)
    }

    @Test
    fun testIsJWTExpired() {
        // Test with JWT that doesn't have exp claim
        val result = JWTFunctions.isJWTExpired(listOf(UDM.Scalar(sampleJWT)))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(false, (result as UDM.Scalar).value)  // No exp claim means not expired
    }

    @Test
    fun testGetJWTClaim() {
        val result = JWTFunctions.getJWTClaim(listOf(UDM.Scalar(sampleJWT), UDM.Scalar("name")))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("John Doe", (result as UDM.Scalar).value)
        
        // Test non-existent claim
        val result2 = JWTFunctions.getJWTClaim(listOf(UDM.Scalar(sampleJWT), UDM.Scalar("nonexistent")))
        
        assertTrue(result2 is UDM.Scalar)
        assertTrue((result2 as UDM.Scalar).value == null)
    }

    @Test
    fun testInvalidJWTFormat() {
        // Test with invalid JWT (wrong number of parts)
        val invalidJWT = "invalid.jwt"
        
        assertThrows<FunctionArgumentException> {
            JWTFunctions.decodeJWT(listOf(UDM.Scalar(invalidJWT)))
        }
        
        assertThrows<FunctionArgumentException> {
            JWTFunctions.getJWTClaims(listOf(UDM.Scalar(invalidJWT)))
        }
    }

    @Test
    fun testInvalidArguments() {
        // Test with wrong number of arguments
        assertThrows<FunctionArgumentException> {
            JWTFunctions.decodeJWT(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            JWTFunctions.getJWTClaim(listOf(UDM.Scalar(sampleJWT)))
        }
        
        assertThrows<FunctionArgumentException> {
            JWTFunctions.decodeJWT(listOf(UDM.Scalar(sampleJWT), UDM.Scalar("extra")))
        }
        
        // Test with wrong argument types
        assertThrows<FunctionArgumentException> {
            JWTFunctions.decodeJWT(listOf(UDM.Array(listOf())))
        }
    }

    @Test
    fun testSecurityWarnings() {
        // Verify that the decoded JWT is marked as unverified
        val result = JWTFunctions.decodeJWT(listOf(UDM.Scalar(sampleJWT)))
        val decoded = result as UDM.Object
        
        // Should always be marked as unverified for security
        assertEquals(false, (decoded.properties["verified"] as UDM.Scalar).value)
    }

    @Test
    fun testEdgeCases() {
        // Test with minimal JWT structure
        val minimalJWT = "eyJ0eXAiOiJKV1QifQ.e30.signature"  // {"typ":"JWT"} . {} . signature
        
        val result = JWTFunctions.decodeJWT(listOf(UDM.Scalar(minimalJWT)))
        assertTrue(result is UDM.Object)
        
        val decoded = result as UDM.Object
        val header = decoded.properties["header"] as UDM.Object
        assertEquals("JWT", (header.properties["typ"] as UDM.Scalar).value)
        
        val payload = decoded.properties["payload"] as UDM.Object
        assertEquals(0, payload.properties.size)  // Empty payload
    }
}