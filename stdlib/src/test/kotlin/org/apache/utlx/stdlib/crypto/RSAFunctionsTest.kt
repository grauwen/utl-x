package org.apache.utlx.stdlib.crypto

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.core.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RSAFunctionsTest {

    // =========================================================================
    // generateRSAKeyPair
    // =========================================================================

    @Test
    fun `generateRSAKeyPair should generate 2048-bit key pair by default`() {
        val result = RSAFunctions.generateRSAKeyPair(emptyList())

        assertTrue(result is UDM.Object)
        val obj = result as UDM.Object
        assertNotNull(obj.properties["publicKey"])
        assertNotNull(obj.properties["privateKey"])
        assertEquals("RSA", (obj.properties["algorithm"] as UDM.Scalar).value)
        assertEquals(2048, (obj.properties["keySize"] as UDM.Scalar).value)

        // Keys should be non-empty Base64 strings
        val pubKey = (obj.properties["publicKey"] as UDM.Scalar).value as String
        val privKey = (obj.properties["privateKey"] as UDM.Scalar).value as String
        assertTrue(pubKey.isNotEmpty())
        assertTrue(privKey.isNotEmpty())
    }

    @Test
    fun `generateRSAKeyPair should accept explicit key size`() {
        val result = RSAFunctions.generateRSAKeyPair(listOf(UDM.Scalar(1024.0)))

        assertTrue(result is UDM.Object)
        assertEquals(1024, ((result as UDM.Object).properties["keySize"] as UDM.Scalar).value)
    }

    @Test
    fun `generateRSAKeyPair should reject invalid key sizes`() {
        assertThrows<FunctionArgumentException> {
            RSAFunctions.generateRSAKeyPair(listOf(UDM.Scalar(512.0)))
        }
    }

    // =========================================================================
    // rsaSign / rsaVerify round-trip
    // =========================================================================

    @Test
    fun `rsaSign and rsaVerify should round-trip successfully`() {
        // Generate key pair
        val keyPair = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val publicKey = (keyPair.properties["publicKey"] as UDM.Scalar).value as String
        val privateKey = (keyPair.properties["privateKey"] as UDM.Scalar).value as String

        val data = "Hello, RSA signing!"

        // Sign
        val signature = RSAFunctions.rsaSign(listOf(
            UDM.Scalar(data),
            UDM.Scalar(privateKey)
        ))
        assertTrue(signature is UDM.Scalar)
        val sigValue = (signature as UDM.Scalar).value as String
        assertTrue(sigValue.isNotEmpty())

        // Verify
        val verified = RSAFunctions.rsaVerify(listOf(
            UDM.Scalar(data),
            UDM.Scalar(sigValue),
            UDM.Scalar(publicKey)
        ))
        assertEquals(true, (verified as UDM.Scalar).value)
    }

    @Test
    fun `rsaVerify should fail with wrong public key`() {
        val keyPair1 = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val keyPair2 = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val privateKey1 = (keyPair1.properties["privateKey"] as UDM.Scalar).value as String
        val publicKey2 = (keyPair2.properties["publicKey"] as UDM.Scalar).value as String

        val data = "Test data"
        val signature = RSAFunctions.rsaSign(listOf(UDM.Scalar(data), UDM.Scalar(privateKey1)))
        val sigValue = (signature as UDM.Scalar).value as String

        val verified = RSAFunctions.rsaVerify(listOf(
            UDM.Scalar(data),
            UDM.Scalar(sigValue),
            UDM.Scalar(publicKey2)
        ))
        assertEquals(false, (verified as UDM.Scalar).value)
    }

    @Test
    fun `rsaVerify should fail with tampered data`() {
        val keyPair = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val publicKey = (keyPair.properties["publicKey"] as UDM.Scalar).value as String
        val privateKey = (keyPair.properties["privateKey"] as UDM.Scalar).value as String

        val signature = RSAFunctions.rsaSign(listOf(UDM.Scalar("original"), UDM.Scalar(privateKey)))
        val sigValue = (signature as UDM.Scalar).value as String

        val verified = RSAFunctions.rsaVerify(listOf(
            UDM.Scalar("tampered"),
            UDM.Scalar(sigValue),
            UDM.Scalar(publicKey)
        ))
        assertEquals(false, (verified as UDM.Scalar).value)
    }

    @Test
    fun `rsaSign should support SHA512withRSA algorithm`() {
        val keyPair = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val publicKey = (keyPair.properties["publicKey"] as UDM.Scalar).value as String
        val privateKey = (keyPair.properties["privateKey"] as UDM.Scalar).value as String

        val data = "Test with SHA512"
        val algo = "SHA512withRSA"

        val signature = RSAFunctions.rsaSign(listOf(UDM.Scalar(data), UDM.Scalar(privateKey), UDM.Scalar(algo)))
        val sigValue = (signature as UDM.Scalar).value as String

        val verified = RSAFunctions.rsaVerify(listOf(
            UDM.Scalar(data),
            UDM.Scalar(sigValue),
            UDM.Scalar(publicKey),
            UDM.Scalar(algo)
        ))
        assertEquals(true, (verified as UDM.Scalar).value)
    }

    @Test
    fun `rsaSign should throw with too few arguments`() {
        assertThrows<FunctionArgumentException> {
            RSAFunctions.rsaSign(listOf(UDM.Scalar("data")))
        }
    }

    @Test
    fun `rsaVerify should throw with too few arguments`() {
        assertThrows<FunctionArgumentException> {
            RSAFunctions.rsaVerify(listOf(UDM.Scalar("data"), UDM.Scalar("sig")))
        }
    }

    // =========================================================================
    // rsaEncrypt / rsaDecrypt round-trip
    // =========================================================================

    @Test
    fun `rsaEncrypt and rsaDecrypt should round-trip successfully`() {
        val keyPair = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val publicKey = (keyPair.properties["publicKey"] as UDM.Scalar).value as String
        val privateKey = (keyPair.properties["privateKey"] as UDM.Scalar).value as String

        val plaintext = "Sensitive data to encrypt"

        val encrypted = RSAFunctions.rsaEncrypt(listOf(UDM.Scalar(plaintext), UDM.Scalar(publicKey)))
        val encValue = (encrypted as UDM.Scalar).value as String
        assertTrue(encValue.isNotEmpty())
        assertTrue(encValue != plaintext) // should be different from original

        val decrypted = RSAFunctions.rsaDecrypt(listOf(UDM.Scalar(encValue), UDM.Scalar(privateKey)))
        assertEquals(plaintext, (decrypted as UDM.Scalar).value)
    }

    @Test
    fun `rsaDecrypt should fail with wrong private key`() {
        val keyPair1 = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val keyPair2 = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val publicKey1 = (keyPair1.properties["publicKey"] as UDM.Scalar).value as String
        val privateKey2 = (keyPair2.properties["privateKey"] as UDM.Scalar).value as String

        val encrypted = RSAFunctions.rsaEncrypt(listOf(UDM.Scalar("secret"), UDM.Scalar(publicKey1)))

        assertThrows<FunctionArgumentException> {
            RSAFunctions.rsaDecrypt(listOf(encrypted, UDM.Scalar(privateKey2)))
        }
    }

    @Test
    fun `rsaEncrypt should throw with wrong number of arguments`() {
        assertThrows<FunctionArgumentException> {
            RSAFunctions.rsaEncrypt(listOf(UDM.Scalar("data")))
        }
    }

    @Test
    fun `rsaDecrypt should throw with wrong number of arguments`() {
        assertThrows<FunctionArgumentException> {
            RSAFunctions.rsaDecrypt(listOf(UDM.Scalar("data")))
        }
    }

    @Test
    fun `rsaEncrypt should handle empty string`() {
        val keyPair = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val publicKey = (keyPair.properties["publicKey"] as UDM.Scalar).value as String
        val privateKey = (keyPair.properties["privateKey"] as UDM.Scalar).value as String

        val encrypted = RSAFunctions.rsaEncrypt(listOf(UDM.Scalar(""), UDM.Scalar(publicKey)))
        val decrypted = RSAFunctions.rsaDecrypt(listOf(encrypted, UDM.Scalar(privateKey)))
        assertEquals("", (decrypted as UDM.Scalar).value)
    }

    @Test
    fun `rsaEncrypt should handle special characters`() {
        val keyPair = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val publicKey = (keyPair.properties["publicKey"] as UDM.Scalar).value as String
        val privateKey = (keyPair.properties["privateKey"] as UDM.Scalar).value as String

        val plaintext = "Special: \u00E9\u00E8\u00EA \u00FC\u00F6\u00E4 \u2603 \uD83D\uDE00"
        val encrypted = RSAFunctions.rsaEncrypt(listOf(UDM.Scalar(plaintext), UDM.Scalar(publicKey)))
        val decrypted = RSAFunctions.rsaDecrypt(listOf(encrypted, UDM.Scalar(privateKey)))
        assertEquals(plaintext, (decrypted as UDM.Scalar).value)
    }
}
