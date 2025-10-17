package org.apache.utlx.stdlib.jwt

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JWTVerificationTest {

    @Test
    fun `verifyJWT should verify valid HS256 token successfully`() {
        val payload = UDM.Object(mapOf(
            "sub" to UDM.Scalar("1234567890"),
            "name" to UDM.Scalar("John Doe"),
            "admin" to UDM.Scalar(true)
        ))
        val secret = "your-256-bit-secret"
        
        // First create a valid token
        val createResult = JWTVerification.createJWT(listOf(payload, UDM.Scalar(secret)))
        val token = (createResult as UDM.Scalar).value as String
        
        // Then verify it
        val result = JWTVerification.verifyJWT(listOf(UDM.Scalar(token), UDM.Scalar(secret)))
        
        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object
        assertTrue((resultObj.properties["verified"] as UDM.Scalar).value as Boolean)
        assertNotNull(resultObj.properties["claims"])
        assertNotNull(resultObj.properties["header"])
    }

    @Test
    fun `verifyJWT should fail with invalid signature`() {
        val payload = UDM.Object(mapOf(
            "sub" to UDM.Scalar("1234567890"),
            "name" to UDM.Scalar("John Doe")
        ))
        val secret = "your-256-bit-secret"
        val wrongSecret = "wrong-secret"
        
        // Create token with one secret
        val createResult = JWTVerification.createJWT(listOf(payload, UDM.Scalar(secret)))
        val token = (createResult as UDM.Scalar).value as String
        
        // Try to verify with different secret
        val result = JWTVerification.verifyJWT(listOf(UDM.Scalar(token), UDM.Scalar(wrongSecret)))
        
        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object
        assertFalse((resultObj.properties["verified"] as UDM.Scalar).value as Boolean)
        assertEquals("Invalid signature", (resultObj.properties["error"] as UDM.Scalar).value)
    }

    @Test
    fun `verifyJWT should handle malformed token`() {
        val malformedToken = "invalid.token"
        val secret = "secret"
        
        val result = JWTVerification.verifyJWT(listOf(UDM.Scalar(malformedToken), UDM.Scalar(secret)))
        
        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object
        assertFalse((resultObj.properties["verified"] as UDM.Scalar).value as Boolean)
        assertEquals("Invalid JWT format", (resultObj.properties["error"] as UDM.Scalar).value)
    }

    @Test
    fun `verifyJWT should reject non-HS256 algorithms`() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        val secret = "secret"
        
        assertThrows<FunctionArgumentException> {
            JWTVerification.verifyJWT(listOf(UDM.Scalar(token), UDM.Scalar(secret), UDM.Scalar("RS256")))
        }
    }

    @Test
    fun `verifyJWT should throw exception for wrong number of arguments`() {
        assertThrows<FunctionArgumentException> {
            JWTVerification.verifyJWT(listOf(UDM.Scalar("token")))
        }
        
        assertThrows<FunctionArgumentException> {
            JWTVerification.verifyJWT(listOf(
                UDM.Scalar("token"),
                UDM.Scalar("secret"),
                UDM.Scalar("HS256"),
                UDM.Scalar("extra")
            ))
        }
    }

    @Test
    fun `verifyJWT should use HS256 as default algorithm`() {
        val payload = UDM.Object(mapOf("test" to UDM.Scalar("value")))
        val secret = "secret"
        
        val createResult = JWTVerification.createJWT(listOf(payload, UDM.Scalar(secret)))
        val token = (createResult as UDM.Scalar).value as String
        
        // Verify without specifying algorithm (should default to HS256)
        val result = JWTVerification.verifyJWT(listOf(UDM.Scalar(token), UDM.Scalar(secret)))
        
        assertTrue(result is UDM.Object)
        assertTrue((result.properties["verified"] as UDM.Scalar).value as Boolean)
    }

    @Test
    fun `createJWT should generate valid token with required claims`() {
        val payload = UDM.Object(mapOf(
            "sub" to UDM.Scalar("1234567890"),
            "name" to UDM.Scalar("John Doe"),
            "admin" to UDM.Scalar(true)
        ))
        val secret = "your-256-bit-secret"
        
        val result = JWTVerification.createJWT(listOf(payload, UDM.Scalar(secret)))
        
        assertTrue(result is UDM.Scalar)
        val token = (result as UDM.Scalar).value as String
        
        // Token should have 3 parts separated by dots
        val parts = token.split(".")
        assertEquals(3, parts.size)
        
        // Verify the token can be parsed back
        val verifyResult = JWTVerification.verifyJWT(listOf(UDM.Scalar(token), UDM.Scalar(secret)))
        assertTrue((verifyResult as UDM.Object).properties["verified"] as UDM.Scalar).value as Boolean)
    }

    @Test
    fun `createJWT should add iat claim automatically`() {
        val payload = UDM.Object(mapOf("test" to UDM.Scalar("value")))
        val secret = "secret"
        
        val result = JWTVerification.createJWT(listOf(payload, UDM.Scalar(secret)))
        val token = (result as UDM.Scalar).value as String
        
        val verifyResult = JWTVerification.verifyJWT(listOf(UDM.Scalar(token), UDM.Scalar(secret)))
        val claims = (verifyResult as UDM.Object).properties["claims"] as UDM.Object
        
        assertNotNull(claims.properties["iat"])
        assertTrue(claims.properties["iat"] is UDM.Scalar)
    }

    @Test
    fun `createJWT should add exp claim when expiresIn is provided`() {
        val payload = UDM.Object(mapOf("test" to UDM.Scalar("value")))
        val secret = "secret"
        val expiresIn = 3600L // 1 hour
        
        val result = JWTVerification.createJWT(listOf(
            payload,
            UDM.Scalar(secret),
            UDM.Scalar("HS256"),
            UDM.Scalar(expiresIn)
        ))
        val token = (result as UDM.Scalar).value as String
        
        val verifyResult = JWTVerification.verifyJWT(listOf(UDM.Scalar(token), UDM.Scalar(secret)))
        val claims = (verifyResult as UDM.Object).properties["claims"] as UDM.Object
        
        assertNotNull(claims.properties["exp"])
        assertTrue(claims.properties["exp"] is UDM.Scalar)
        
        val iat = (claims.properties["iat"] as UDM.Scalar).value as Long
        val exp = (claims.properties["exp"] as UDM.Scalar).value as Long
        assertEquals(expiresIn, exp - iat)
    }

    @Test
    fun `createJWT should use HS256 as default algorithm`() {
        val payload = UDM.Object(mapOf("test" to UDM.Scalar("value")))
        val secret = "secret"
        
        val result = JWTVerification.createJWT(listOf(payload, UDM.Scalar(secret)))
        val token = (result as UDM.Scalar).value as String
        
        // Verify the header contains HS256
        val parts = token.split(".")
        val structureResult = JWTVerification.validateJWTStructure(listOf(UDM.Scalar(token)))
        val header = ((structureResult as UDM.Object).properties["header"] as UDM.Object)
        
        assertEquals("HS256", (header.properties["alg"] as UDM.Scalar).value)
        assertEquals("JWT", (header.properties["typ"] as UDM.Scalar).value)
    }

    @Test
    fun `createJWT should reject non-HS256 algorithms`() {
        val payload = UDM.Object(mapOf("test" to UDM.Scalar("value")))
        val secret = "secret"
        
        assertThrows<FunctionArgumentException> {
            JWTVerification.createJWT(listOf(payload, UDM.Scalar(secret), UDM.Scalar("RS256")))
        }
    }

    @Test
    fun `createJWT should throw exception for non-object payload`() {
        val payload = UDM.Scalar("invalid")
        val secret = "secret"
        
        assertThrows<FunctionArgumentException> {
            JWTVerification.createJWT(listOf(payload, UDM.Scalar(secret)))
        }
    }

    @Test
    fun `createJWT should throw exception for wrong number of arguments`() {
        assertThrows<FunctionArgumentException> {
            JWTVerification.createJWT(listOf(UDM.Scalar("payload")))
        }
        
        assertThrows<FunctionArgumentException> {
            JWTVerification.createJWT(listOf(
                UDM.Object(emptyMap()),
                UDM.Scalar("secret"),
                UDM.Scalar("HS256"),
                UDM.Scalar(3600),
                UDM.Scalar("extra")
            ))
        }
    }

    @Test
    fun `validateJWTStructure should validate well-formed token`() {
        val payload = UDM.Object(mapOf("test" to UDM.Scalar("value")))
        val secret = "secret"
        
        val createResult = JWTVerification.createJWT(listOf(payload, UDM.Scalar(secret)))
        val token = (createResult as UDM.Scalar).value as String
        
        val result = JWTVerification.validateJWTStructure(listOf(UDM.Scalar(token)))
        
        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object
        assertTrue((resultObj.properties["valid"] as UDM.Scalar).value as Boolean)
        assertNotNull(resultObj.properties["header"])
        assertNotNull(resultObj.properties["payload"])
        assertTrue((resultObj.properties["hasSignature"] as UDM.Scalar).value as Boolean)
    }

    @Test
    fun `validateJWTStructure should reject malformed token`() {
        val malformedToken = "invalid.token"
        
        val result = JWTVerification.validateJWTStructure(listOf(UDM.Scalar(malformedToken)))
        
        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object
        assertFalse((resultObj.properties["valid"] as UDM.Scalar).value as Boolean)
        assertEquals("Invalid JWT format - expected 3 parts", (resultObj.properties["error"] as UDM.Scalar).value)
    }

    @Test
    fun `validateJWTStructure should handle invalid base64 encoding`() {
        val invalidToken = "invalid.base64.encoding"
        
        val result = JWTVerification.validateJWTStructure(listOf(UDM.Scalar(invalidToken)))
        
        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object
        assertFalse((resultObj.properties["valid"] as UDM.Scalar).value as Boolean)
        assertTrue((resultObj.properties["error"] as UDM.Scalar).value as String).startsWith("Failed to parse JWT:")
    }

    @Test
    fun `validateJWTStructure should detect empty signature`() {
        val tokenWithoutSignature = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidmFsdWUifQ."
        
        val result = JWTVerification.validateJWTStructure(listOf(UDM.Scalar(tokenWithoutSignature)))
        
        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object
        assertTrue((resultObj.properties["valid"] as UDM.Scalar).value as Boolean)
        assertFalse((resultObj.properties["hasSignature"] as UDM.Scalar).value as Boolean)
    }

    @Test
    fun `validateJWTStructure should throw exception for wrong number of arguments`() {
        assertThrows<FunctionArgumentException> {
            JWTVerification.validateJWTStructure(emptyList())
        }
        
        assertThrows<FunctionArgumentException> {
            JWTVerification.validateJWTStructure(listOf(UDM.Scalar("token"), UDM.Scalar("extra")))
        }
    }

    @Test
    fun `verifyJWTWithJWKS should return not implemented error`() {
        val token = "test.token"
        val jwksUrl = "https://example.com/.well-known/jwks.json"
        
        val result = JWTVerification.verifyJWTWithJWKS(listOf(UDM.Scalar(token), UDM.Scalar(jwksUrl)))
        
        assertTrue(result is UDM.Object)
        val resultObj = result as UDM.Object
        assertFalse((resultObj.properties["verified"] as UDM.Scalar).value as Boolean)
        assertEquals("JWKS verification not implemented. Use verifyJWT with direct secret for HS256.", 
                    (resultObj.properties["error"] as UDM.Scalar).value)
        assertEquals(jwksUrl, (resultObj.properties["jwksUrl"] as UDM.Scalar).value)
    }

    @Test
    fun `verifyJWTWithJWKS should throw exception for wrong number of arguments`() {
        assertThrows<FunctionArgumentException> {
            JWTVerification.verifyJWTWithJWKS(listOf(UDM.Scalar("token")))
        }
        
        assertThrows<FunctionArgumentException> {
            JWTVerification.verifyJWTWithJWKS(listOf(
                UDM.Scalar("token"),
                UDM.Scalar("jwksUrl"),
                UDM.Scalar("extra")
            ))
        }
    }

    @Test
    fun `JWT workflow should work end-to-end with complex payload`() {
        val complexPayload = UDM.Object(mapOf(
            "sub" to UDM.Scalar("user123"),
            "name" to UDM.Scalar("Jane Smith"),
            "email" to UDM.Scalar("jane@example.com"),
            "roles" to UDM.Array(listOf(
                UDM.Scalar("user"),
                UDM.Scalar("admin")
            )),
            "metadata" to UDM.Object(mapOf(
                "department" to UDM.Scalar("Engineering"),
                "level" to UDM.Scalar(5)
            )),
            "active" to UDM.Scalar(true)
        ))
        val secret = "complex-secret-key-for-testing"
        
        // Create token
        val createResult = JWTVerification.createJWT(listOf(complexPayload, UDM.Scalar(secret)))
        val token = (createResult as UDM.Scalar).value as String
        
        // Validate structure
        val structureResult = JWTVerification.validateJWTStructure(listOf(UDM.Scalar(token)))
        assertTrue((structureResult as UDM.Object).properties["valid"] as UDM.Scalar).value as Boolean)
        
        // Verify token
        val verifyResult = JWTVerification.verifyJWT(listOf(UDM.Scalar(token), UDM.Scalar(secret)))
        val resultObj = verifyResult as UDM.Object
        
        assertTrue((resultObj.properties["verified"] as UDM.Scalar).value as Boolean)
        
        val claims = resultObj.properties["claims"] as UDM.Object
        assertEquals("user123", (claims.properties["sub"] as UDM.Scalar).value)
        assertEquals("Jane Smith", (claims.properties["name"] as UDM.Scalar).value)
        assertEquals("jane@example.com", (claims.properties["email"] as UDM.Scalar).value)
        assertTrue((claims.properties["active"] as UDM.Scalar).value as Boolean)
        
        // Verify nested objects and arrays are preserved
        val roles = claims.properties["roles"] as UDM.Array
        assertEquals(2, roles.elements.size)
        assertEquals("user", (roles.elements[0] as UDM.Scalar).value)
        assertEquals("admin", (roles.elements[1] as UDM.Scalar).value)
        
        val metadata = claims.properties["metadata"] as UDM.Object
        assertEquals("Engineering", (metadata.properties["department"] as UDM.Scalar).value)
        assertEquals(5L, (metadata.properties["level"] as UDM.Scalar).value)
        
        // Verify automatic claims were added
        assertNotNull(claims.properties["iat"])
    }

    @Test
    fun `JWT should handle special characters in payload`() {
        val payloadWithSpecialChars = UDM.Object(mapOf(
            "message" to UDM.Scalar("Hello, World! @#$%^&*()"),
            "unicode" to UDM.Scalar("🚀 UTF-8 test: café, naïve, résumé"),
            "quotes" to UDM.Scalar("\"Double quotes\" and 'single quotes'"),
            "newlines" to UDM.Scalar("Line 1\nLine 2\r\nLine 3"),
            "tabs" to UDM.Scalar("Tab\tSeparated\tValues")
        ))
        val secret = "special-chars-secret"
        
        // Create and verify token
        val createResult = JWTVerification.createJWT(listOf(payloadWithSpecialChars, UDM.Scalar(secret)))
        val token = (createResult as UDM.Scalar).value as String
        
        val verifyResult = JWTVerification.verifyJWT(listOf(UDM.Scalar(token), UDM.Scalar(secret)))
        val resultObj = verifyResult as UDM.Object
        
        assertTrue((resultObj.properties["verified"] as UDM.Scalar).value as Boolean)
        
        val claims = resultObj.properties["claims"] as UDM.Object
        assertEquals("Hello, World! @#$%^&*()", (claims.properties["message"] as UDM.Scalar).value)
        assertEquals("🚀 UTF-8 test: café, naïve, résumé", (claims.properties["unicode"] as UDM.Scalar).value)
        assertEquals("\"Double quotes\" and 'single quotes'", (claims.properties["quotes"] as UDM.Scalar).value)
        assertEquals("Line 1\nLine 2\r\nLine 3", (claims.properties["newlines"] as UDM.Scalar).value)
        assertEquals("Tab\tSeparated\tValues", (claims.properties["tabs"] as UDM.Scalar).value)
    }

    @Test
    fun `JWT should handle numeric values correctly`() {
        val numericPayload = UDM.Object(mapOf(
            "integer" to UDM.Scalar(42L),
            "double" to UDM.Scalar(3.14159),
            "negative" to UDM.Scalar(-123L),
            "zero" to UDM.Scalar(0L),
            "large" to UDM.Scalar(9223372036854775807L), // Long.MAX_VALUE
            "decimal" to UDM.Scalar(0.0001)
        ))
        val secret = "numeric-secret"
        
        val createResult = JWTVerification.createJWT(listOf(numericPayload, UDM.Scalar(secret)))
        val token = (createResult as UDM.Scalar).value as String
        
        val verifyResult = JWTVerification.verifyJWT(listOf(UDM.Scalar(token), UDM.Scalar(secret)))
        val claims = ((verifyResult as UDM.Object).properties["claims"] as UDM.Object)
        
        assertEquals(42L, (claims.properties["integer"] as UDM.Scalar).value)
        assertEquals(3.14159, (claims.properties["double"] as UDM.Scalar).value)
        assertEquals(-123L, (claims.properties["negative"] as UDM.Scalar).value)
        assertEquals(0L, (claims.properties["zero"] as UDM.Scalar).value)
        assertEquals(9223372036854775807L, (claims.properties["large"] as UDM.Scalar).value)
        assertEquals(0.0001, (claims.properties["decimal"] as UDM.Scalar).value)
    }

    @Test
    fun `JWT should handle null and boolean values`() {
        val mixedPayload = UDM.Object(mapOf(
            "nullValue" to UDM.Scalar(null),
            "trueValue" to UDM.Scalar(true),
            "falseValue" to UDM.Scalar(false),
            "emptyString" to UDM.Scalar(""),
            "emptyArray" to UDM.Array(emptyList()),
            "emptyObject" to UDM.Object(emptyMap())
        ))
        val secret = "mixed-types-secret"
        
        val createResult = JWTVerification.createJWT(listOf(mixedPayload, UDM.Scalar(secret)))
        val token = (createResult as UDM.Scalar).value as String
        
        val verifyResult = JWTVerification.verifyJWT(listOf(UDM.Scalar(token), UDM.Scalar(secret)))
        val claims = ((verifyResult as UDM.Object).properties["claims"] as UDM.Object)
        
        assertEquals(null, (claims.properties["nullValue"] as UDM.Scalar).value)
        assertEquals(true, (claims.properties["trueValue"] as UDM.Scalar).value)
        assertEquals(false, (claims.properties["falseValue"] as UDM.Scalar).value)
        assertEquals("", (claims.properties["emptyString"] as UDM.Scalar).value)
        assertEquals(0, (claims.properties["emptyArray"] as UDM.Array).elements.size)
        assertEquals(0, (claims.properties["emptyObject"] as UDM.Object).properties.size)
    }
}