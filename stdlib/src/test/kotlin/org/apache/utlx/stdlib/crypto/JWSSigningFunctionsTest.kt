package org.apache.utlx.stdlib.crypto

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.core.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JWSSigningFunctionsTest {

    // =========================================================================
    // signJWS
    // =========================================================================

    @Test
    fun `signJWS should produce valid JWS compact serialization`() {
        val result = JWSSigningFunctions.signJWS(listOf(
            UDM.Scalar("test payload"),
            UDM.Scalar("secret-key")
        ))

        assertTrue(result is UDM.Scalar)
        val jws = (result as UDM.Scalar).value as String
        val parts = jws.split(".")
        assertEquals(3, parts.size) // header.payload.signature
    }

    @Test
    fun `signJWS should default to HS256`() {
        val result = JWSSigningFunctions.signJWS(listOf(
            UDM.Scalar("payload"),
            UDM.Scalar("key")
        ))
        val jws = (result as UDM.Scalar).value as String

        // Decode header to check algorithm
        val headerB64 = jws.split(".")[0]
        val header = String(java.util.Base64.getUrlDecoder().decode(headerB64))
        assertTrue(header.contains("\"alg\":\"HS256\""))
    }

    @Test
    fun `signJWS should support HS384 and HS512`() {
        for (algo in listOf("HS384", "HS512")) {
            val result = JWSSigningFunctions.signJWS(listOf(
                UDM.Scalar("payload"),
                UDM.Scalar("key"),
                UDM.Scalar(algo)
            ))
            val jws = (result as UDM.Scalar).value as String
            val header = String(java.util.Base64.getUrlDecoder().decode(jws.split(".")[0]))
            assertTrue(header.contains("\"alg\":\"$algo\""), "Expected $algo in header")
        }
    }

    @Test
    fun `signJWS should reject unsupported algorithms`() {
        assertThrows<FunctionArgumentException> {
            JWSSigningFunctions.signJWS(listOf(
                UDM.Scalar("payload"),
                UDM.Scalar("key"),
                UDM.Scalar("RS256")
            ))
        }
    }

    @Test
    fun `signJWS should throw with too few arguments`() {
        assertThrows<FunctionArgumentException> {
            JWSSigningFunctions.signJWS(listOf(UDM.Scalar("payload")))
        }
    }

    @Test
    fun `signJWS should produce different signatures for different secrets`() {
        val jws1 = (JWSSigningFunctions.signJWS(listOf(
            UDM.Scalar("payload"), UDM.Scalar("secret1")
        )) as UDM.Scalar).value as String

        val jws2 = (JWSSigningFunctions.signJWS(listOf(
            UDM.Scalar("payload"), UDM.Scalar("secret2")
        )) as UDM.Scalar).value as String

        // Same payload but different secrets should produce different signatures
        assertTrue(jws1.split(".")[2] != jws2.split(".")[2])
    }

    // =========================================================================
    // createJWTSigned
    // =========================================================================

    @Test
    fun `createJWTSigned should produce valid JWT`() {
        val claims = UDM.Object(mapOf(
            "sub" to UDM.Scalar("user1"),
            "name" to UDM.Scalar("Test User")
        ))

        val result = JWSSigningFunctions.createJWTSigned(listOf(claims, UDM.Scalar("secret")))

        assertTrue(result is UDM.Scalar)
        val jwt = (result as UDM.Scalar).value as String
        val parts = jwt.split(".")
        assertEquals(3, parts.size)

        // Header should contain typ: JWT
        val header = String(java.util.Base64.getUrlDecoder().decode(parts[0]))
        assertTrue(header.contains("\"typ\":\"JWT\""))
        assertTrue(header.contains("\"alg\":\"HS256\""))
    }

    @Test
    fun `createJWTSigned should support HS512`() {
        val claims = UDM.Object(mapOf("sub" to UDM.Scalar("user1")))

        val result = JWSSigningFunctions.createJWTSigned(listOf(
            claims, UDM.Scalar("secret"), UDM.Scalar("HS512")
        ))

        val jwt = (result as UDM.Scalar).value as String
        val header = String(java.util.Base64.getUrlDecoder().decode(jwt.split(".")[0]))
        assertTrue(header.contains("\"alg\":\"HS512\""))
    }

    @Test
    fun `createJWTSigned should throw with too few arguments`() {
        assertThrows<FunctionArgumentException> {
            JWSSigningFunctions.createJWTSigned(listOf(UDM.Scalar("claims")))
        }
    }

    // =========================================================================
    // verifyJWTSignature
    // =========================================================================

    @Test
    fun `verifyJWTSignature should verify token created by createJWTSigned`() {
        val claims = UDM.Object(mapOf(
            "sub" to UDM.Scalar("user1"),
            "role" to UDM.Scalar("admin")
        ))
        val secret = "test-secret"

        val jwt = (JWSSigningFunctions.createJWTSigned(listOf(claims, UDM.Scalar(secret))) as UDM.Scalar).value as String

        val result = JWSSigningFunctions.verifyJWTSignature(listOf(UDM.Scalar(jwt), UDM.Scalar(secret)))

        assertTrue(result is UDM.Object)
        val obj = result as UDM.Object
        assertEquals(true, (obj.properties["valid"] as UDM.Scalar).value)
        assertNotNull(obj.properties["claims"])
        assertNotNull(obj.properties["header"])
    }

    @Test
    fun `verifyJWTSignature should fail with wrong secret`() {
        val claims = UDM.Object(mapOf("sub" to UDM.Scalar("user1")))

        val jwt = (JWSSigningFunctions.createJWTSigned(listOf(claims, UDM.Scalar("correct"))) as UDM.Scalar).value as String

        val result = JWSSigningFunctions.verifyJWTSignature(listOf(UDM.Scalar(jwt), UDM.Scalar("wrong")))

        assertTrue(result is UDM.Object)
        assertEquals(false, ((result as UDM.Object).properties["valid"] as UDM.Scalar).value)
    }

    @Test
    fun `verifyJWTSignature should detect algorithm mismatch`() {
        val claims = UDM.Object(mapOf("sub" to UDM.Scalar("user1")))
        val secret = "secret"

        // Create with HS256
        val jwt = (JWSSigningFunctions.createJWTSigned(listOf(claims, UDM.Scalar(secret))) as UDM.Scalar).value as String

        // Verify expecting HS512
        val result = JWSSigningFunctions.verifyJWTSignature(listOf(
            UDM.Scalar(jwt), UDM.Scalar(secret), UDM.Scalar("HS512")
        ))

        assertTrue(result is UDM.Object)
        assertEquals(false, ((result as UDM.Object).properties["valid"] as UDM.Scalar).value)
        assertTrue(((result).properties["error"] as UDM.Scalar).value.toString().contains("Algorithm mismatch"))
    }

    @Test
    fun `verifyJWTSignature should handle malformed token`() {
        val result = JWSSigningFunctions.verifyJWTSignature(listOf(
            UDM.Scalar("not.a.valid"),
            UDM.Scalar("secret")
        ))

        // Should not crash — returns valid=false
        assertTrue(result is UDM.Object)
        assertEquals(false, ((result as UDM.Object).properties["valid"] as UDM.Scalar).value)
    }

    @Test
    fun `verifyJWTSignature should handle token with only 2 parts`() {
        val result = JWSSigningFunctions.verifyJWTSignature(listOf(
            UDM.Scalar("header.payload"),
            UDM.Scalar("secret")
        ))

        assertTrue(result is UDM.Object)
        assertEquals(false, ((result as UDM.Object).properties["valid"] as UDM.Scalar).value)
    }

    @Test
    fun `verifyJWTSignature should throw with too few arguments`() {
        assertThrows<FunctionArgumentException> {
            JWSSigningFunctions.verifyJWTSignature(listOf(UDM.Scalar("token")))
        }
    }

    // =========================================================================
    // Round-trip: createJWTSigned + verifyJWTSignature with all algorithms
    // =========================================================================

    @Test
    fun `round-trip should work for all supported HMAC algorithms`() {
        val claims = UDM.Object(mapOf(
            "sub" to UDM.Scalar("roundtrip-test"),
            "data" to UDM.Scalar("some value")
        ))
        val secret = "a-sufficiently-long-secret-key-for-testing"

        for (algo in listOf("HS256", "HS384", "HS512")) {
            val jwt = (JWSSigningFunctions.createJWTSigned(listOf(
                claims, UDM.Scalar(secret), UDM.Scalar(algo)
            )) as UDM.Scalar).value as String

            val result = JWSSigningFunctions.verifyJWTSignature(listOf(
                UDM.Scalar(jwt), UDM.Scalar(secret), UDM.Scalar(algo)
            )) as UDM.Object

            assertEquals(true, (result.properties["valid"] as UDM.Scalar).value,
                "Round-trip failed for $algo")
        }
    }
}
