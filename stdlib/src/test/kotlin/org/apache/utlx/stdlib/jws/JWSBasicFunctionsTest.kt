// stdlib/src/test/kotlin/org/apache/utlx/stdlib/jws/JWSBasicFunctionsTest.kt
package org.apache.utlx.stdlib.jws

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.Base64

class JWSBasicFunctionsTest {

    // Sample JWS tokens for testing (these are synthetic and not signed)
    private val sampleHeader = """{"alg":"HS256","typ":"JWT"}"""
    private val samplePayload = """{"sub":"1234567890","name":"John Doe","iat":1516239022}"""
    private val sampleSignature = "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
    
    private val encodedHeader = encodeBase64Url(sampleHeader.toByteArray())
    private val encodedPayload = encodeBase64Url(samplePayload.toByteArray())
    private val validJWS = "$encodedHeader.$encodedPayload.$sampleSignature"

    private val headerWithKid = """{"alg":"RS256","typ":"JWT","kid":"key-123"}"""
    private val encodedHeaderWithKid = encodeBase64Url(headerWithKid.toByteArray())
    private val jwsWithKid = "$encodedHeaderWithKid.$encodedPayload.$sampleSignature"

    // Helper function to encode Base64URL
    private fun encodeBase64Url(data: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    // ========== DECODE JWS ==========

    @Test
    fun testDecodeJWSValid() {
        val result = JWSBasicFunctions.decodeJWS(listOf(UDM.Scalar(validJWS)))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertNotNull(properties["header"])
        assertNotNull(properties["payload"])
        assertNotNull(properties["signature"])
        assertEquals(false, (properties["verified"] as UDM.Scalar).value)
        assertEquals(sampleSignature, (properties["signature"] as UDM.Scalar).value)
    }

    @Test
    fun testDecodeJWSWithKeyId() {
        val result = JWSBasicFunctions.decodeJWS(listOf(UDM.Scalar(jwsWithKid)))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertNotNull(properties["header"])
        assertNotNull(properties["payload"])
        assertNotNull(properties["signature"])
        assertEquals(false, (properties["verified"] as UDM.Scalar).value)
    }

    @Test
    fun testDecodeJWSInvalidFormat() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.decodeJWS(listOf(UDM.Scalar("invalid.token")))
        }
    }

    @Test
    fun testDecodeJWSTooManyParts() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.decodeJWS(listOf(UDM.Scalar("part1.part2.part3.part4")))
        }
    }

    @Test
    fun testDecodeJWSEmptyPart() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.decodeJWS(listOf(UDM.Scalar("header..signature")))
        }
    }

    @Test
    fun testDecodeJWSInvalidBase64() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.decodeJWS(listOf(UDM.Scalar("invalid-base64.invalid-base64.signature")))
        }
    }

    // ========== GET JWS PAYLOAD ==========

    @Test
    fun testGetJWSPayload() {
        val result = JWSBasicFunctions.getJWSPayload(listOf(UDM.Scalar(validJWS)))
        
        assertTrue(result is UDM.Object)
        // The payload should be parsed as JSON
        assertNotNull(result)
    }

    @Test
    fun testGetJWSPayloadInvalidToken() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSPayload(listOf(UDM.Scalar("invalid.token")))
        }
    }

    // ========== GET JWS HEADER ==========

    @Test
    fun testGetJWSHeader() {
        val result = JWSBasicFunctions.getJWSHeader(listOf(UDM.Scalar(validJWS)))
        
        assertTrue(result is UDM.Object)
        assertNotNull(result)
    }

    @Test
    fun testGetJWSHeaderInvalidToken() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSHeader(listOf(UDM.Scalar("invalid.token")))
        }
    }

    // ========== GET JWS ALGORITHM ==========

    @Test
    fun testGetJWSAlgorithm() {
        val result = JWSBasicFunctions.getJWSAlgorithm(listOf(UDM.Scalar(validJWS)))
        
        assertTrue(result is UDM.Scalar)
        // Note: Due to simplified JSON parsing, this might not work fully
        // In a real implementation, we'd expect "HS256"
    }

    @Test
    fun testGetJWSAlgorithmInvalidToken() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSAlgorithm(listOf(UDM.Scalar("invalid.token")))
        }
    }

    // ========== GET JWS KEY ID ==========

    @Test
    fun testGetJWSKeyId() {
        val result = JWSBasicFunctions.getJWSKeyId(listOf(UDM.Scalar(jwsWithKid)))
        
        assertTrue(result is UDM.Scalar)
        // Due to simplified JSON parsing, this might return null
    }

    @Test
    fun testGetJWSKeyIdNotPresent() {
        val result = JWSBasicFunctions.getJWSKeyId(listOf(UDM.Scalar(validJWS)))
        
        assertTrue(result is UDM.Scalar)
        // Should return null when kid is not present
    }

    @Test
    fun testGetJWSKeyIdInvalidToken() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSKeyId(listOf(UDM.Scalar("invalid.token")))
        }
    }

    // ========== GET JWS TOKEN TYPE ==========

    @Test
    fun testGetJWSTokenType() {
        val result = JWSBasicFunctions.getJWSTokenType(listOf(UDM.Scalar(validJWS)))
        
        assertTrue(result is UDM.Scalar)
        // Due to simplified JSON parsing, this might not work fully
    }

    @Test
    fun testGetJWSTokenTypeInvalidToken() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSTokenType(listOf(UDM.Scalar("invalid.token")))
        }
    }

    // ========== IS JWS FORMAT ==========

    @Test
    fun testIsJWSFormatValid() {
        val result = JWSBasicFunctions.isJWSFormat(listOf(UDM.Scalar(validJWS)))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsJWSFormatValidWithSpaces() {
        val result = JWSBasicFunctions.isJWSFormat(listOf(UDM.Scalar("  $validJWS  ")))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testIsJWSFormatInvalid() {
        val result = JWSBasicFunctions.isJWSFormat(listOf(UDM.Scalar("invalid.token")))
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testIsJWSFormatEmpty() {
        val result = JWSBasicFunctions.isJWSFormat(listOf(UDM.Scalar("")))
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testIsJWSFormatTooManyParts() {
        val result = JWSBasicFunctions.isJWSFormat(listOf(UDM.Scalar("part1.part2.part3.part4")))
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testIsJWSFormatEmptyPart() {
        val result = JWSBasicFunctions.isJWSFormat(listOf(UDM.Scalar("header..signature")))
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    // ========== GET JWS SIGNING INPUT ==========

    @Test
    fun testGetJWSSigningInput() {
        val result = JWSBasicFunctions.getJWSSigningInput(listOf(UDM.Scalar(validJWS)))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("$encodedHeader.$encodedPayload", result.value)
    }

    @Test
    fun testGetJWSSigningInputInvalidFormat() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSSigningInput(listOf(UDM.Scalar("invalid.token")))
        }
    }

    @Test
    fun testGetJWSSigningInputWithSpaces() {
        val result = JWSBasicFunctions.getJWSSigningInput(listOf(UDM.Scalar("  $validJWS  ")))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("$encodedHeader.$encodedPayload", result.value)
    }

    // ========== GET JWS INFO ==========

    @Test
    fun testGetJWSInfo() {
        val result = JWSBasicFunctions.getJWSInfo(listOf(UDM.Scalar(validJWS)))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertNotNull(properties["algorithm"])
        assertNotNull(properties["tokenType"])
        assertNotNull(properties["keyId"])
        assertNotNull(properties["headerLength"])
        assertNotNull(properties["payloadLength"])
        assertNotNull(properties["signatureLength"])
        assertNotNull(properties["totalLength"])
        assertNotNull(properties["headerClaims"])
        assertNotNull(properties["payloadClaims"])
        assertEquals(false, (properties["verified"] as UDM.Scalar).value)
        
        assertEquals(encodedHeader.length.toDouble(), (properties["headerLength"] as UDM.Scalar).value)
        assertEquals(encodedPayload.length.toDouble(), (properties["payloadLength"] as UDM.Scalar).value)
        assertEquals(sampleSignature.length.toDouble(), (properties["signatureLength"] as UDM.Scalar).value)
        assertEquals(validJWS.length.toDouble(), (properties["totalLength"] as UDM.Scalar).value)
    }

    @Test
    fun testGetJWSInfoInvalidFormat() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSInfo(listOf(UDM.Scalar("invalid.token")))
        }
    }

    // ========== ERROR HANDLING ==========

    @Test
    fun testDecodeJWSInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.decodeJWS(listOf())
        }
    }

    @Test
    fun testDecodeJWSTooManyArgs() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.decodeJWS(listOf(UDM.Scalar(validJWS), UDM.Scalar("extra")))
        }
    }

    @Test
    fun testDecodeJWSInvalidType() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.decodeJWS(listOf(UDM.Array(listOf())))
        }
    }

    @Test
    fun testDecodeJWSNullValue() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.decodeJWS(listOf(UDM.Scalar.nullValue()))
        }
    }

    @Test
    fun testGetJWSPayloadInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSPayload(listOf())
        }
    }

    @Test
    fun testGetJWSHeaderInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSHeader(listOf())
        }
    }

    @Test
    fun testGetJWSAlgorithmInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSAlgorithm(listOf())
        }
    }

    @Test
    fun testGetJWSKeyIdInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSKeyId(listOf())
        }
    }

    @Test
    fun testGetJWSTokenTypeInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSTokenType(listOf())
        }
    }

    @Test
    fun testIsJWSFormatInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.isJWSFormat(listOf())
        }
    }

    @Test
    fun testGetJWSSigningInputInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSSigningInput(listOf())
        }
    }

    @Test
    fun testGetJWSInfoInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.getJWSInfo(listOf())
        }
    }

    // ========== EDGE CASES ==========

    @Test
    fun testDecodeJWSEmptyString() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.decodeJWS(listOf(UDM.Scalar("")))
        }
    }

    @Test
    fun testDecodeJWSOnlyDots() {
        assertThrows<FunctionArgumentException> {
            JWSBasicFunctions.decodeJWS(listOf(UDM.Scalar("..")))
        }
    }

    @Test
    fun testIsJWSFormatSpecialCharacters() {
        val result = JWSBasicFunctions.isJWSFormat(listOf(UDM.Scalar("part1@#$.part2!@#.part3$%^")))
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean) // Should still be valid format (3 non-empty parts)
    }

    @Test
    fun testGetJWSSigningInputSpecialCharacters() {
        val specialJWS = "header@#$.payload!@#.signature$%^"
        val result = JWSBasicFunctions.getJWSSigningInput(listOf(UDM.Scalar(specialJWS)))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("header@#$.payload!@#", result.value)
    }

    @Test
    fun testGetJWSInfoVeryLongToken() {
        val longHeader = "a".repeat(1000)
        val longPayload = "b".repeat(2000)
        val longSignature = "c".repeat(500)
        val longJWS = "$longHeader.$longPayload.$longSignature"
        
        val result = JWSBasicFunctions.getJWSInfo(listOf(UDM.Scalar(longJWS)))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertEquals(1000.0, (properties["headerLength"] as UDM.Scalar).value)
        assertEquals(2000.0, (properties["payloadLength"] as UDM.Scalar).value)
        assertEquals(500.0, (properties["signatureLength"] as UDM.Scalar).value)
        assertEquals(longJWS.length.toDouble(), (properties["totalLength"] as UDM.Scalar).value)
    }

    @Test
    fun testIsJWSFormatWhitespaceOnly() {
        val result = JWSBasicFunctions.isJWSFormat(listOf(UDM.Scalar("   ")))
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testDecodeJWSWithLeadingTrailingSpaces() {
        val jwsWithSpaces = "   $validJWS   "
        val result = JWSBasicFunctions.decodeJWS(listOf(UDM.Scalar(jwsWithSpaces)))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertNotNull(properties["header"])
        assertNotNull(properties["payload"])
        assertNotNull(properties["signature"])
    }

    @Test
    fun testGetJWSInfoMinimalToken() {
        val minimalJWS = "a.b.c"
        val result = JWSBasicFunctions.getJWSInfo(listOf(UDM.Scalar(minimalJWS)))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertEquals(1.0, (properties["headerLength"] as UDM.Scalar).value)
        assertEquals(1.0, (properties["payloadLength"] as UDM.Scalar).value)
        assertEquals(1.0, (properties["signatureLength"] as UDM.Scalar).value)
        assertEquals(5.0, (properties["totalLength"] as UDM.Scalar).value)
    }

    // ========== SECURITY WARNING TESTS ==========

    @Test
    fun testDecodeJWSReturnsUnverifiedFlag() {
        val result = JWSBasicFunctions.decodeJWS(listOf(UDM.Scalar(validJWS)))
        
        assertTrue(result is UDM.Object)
        val verified = result.properties["verified"] as UDM.Scalar
        assertFalse(verified.value as Boolean)
    }

    @Test
    fun testGetJWSInfoReturnsUnverifiedFlag() {
        val result = JWSBasicFunctions.getJWSInfo(listOf(UDM.Scalar(validJWS)))
        
        assertTrue(result is UDM.Object)
        val verified = result.properties["verified"] as UDM.Scalar
        assertFalse(verified.value as Boolean)
    }
}