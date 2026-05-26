package org.apache.utlx.stdlib.crypto

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * F11: RSA Cryptographic Functions — sign, verify, encrypt, decrypt, key generation.
 *
 * All keys are Base64-encoded DER format (standard Java key encoding).
 * No external dependencies — uses java.security (JDK built-in).
 */
object RSAFunctions {

    // =========================================================================
    // Key Generation
    // =========================================================================

    /**
     * generateRSAKeyPair(keySize?) -> {publicKey, privateKey}
     * keySize defaults to 2048 bits.
     */
    fun generateRSAKeyPair(args: List<UDM>): UDM {
        val keySize = if (args.isNotEmpty()) args[0].asNumber().toInt() else 2048
        if (keySize !in listOf(1024, 2048, 4096)) {
            throw FunctionArgumentException("generateRSAKeyPair: keySize must be 1024, 2048, or 4096, got $keySize")
        }

        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(keySize)
        val keyPair = keyPairGen.generateKeyPair()

        return UDM.Object.of(
            "publicKey" to UDM.Scalar(Base64.getEncoder().encodeToString(keyPair.public.encoded)),
            "privateKey" to UDM.Scalar(Base64.getEncoder().encodeToString(keyPair.private.encoded)),
            "algorithm" to UDM.Scalar("RSA"),
            "keySize" to UDM.Scalar(keySize)
        )
    }

    // =========================================================================
    // Sign / Verify
    // =========================================================================

    /**
     * rsaSign(data, privateKeyBase64, algorithm?) -> signature (Base64)
     * algorithm defaults to "SHA256withRSA"
     */
    fun rsaSign(args: List<UDM>): UDM {
        if (args.size < 2) throw FunctionArgumentException("rsaSign expects 2-3 arguments (data, privateKey, algorithm?), got ${args.size}")

        val data = args[0].asString()
        val privateKeyB64 = args[1].asString()
        val algorithm = if (args.size > 2) args[2].asString() else "SHA256withRSA"

        return try {
            val keyBytes = Base64.getDecoder().decode(privateKeyB64)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)

            val sig = Signature.getInstance(algorithm)
            sig.initSign(privateKey)
            sig.update(data.toByteArray(Charsets.UTF_8))

            UDM.Scalar(Base64.getEncoder().encodeToString(sig.sign()))
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            throw FunctionArgumentException("rsaSign failed: ${e.message}")
        }
    }

    /**
     * rsaVerify(data, signatureBase64, publicKeyBase64, algorithm?) -> boolean
     */
    fun rsaVerify(args: List<UDM>): UDM {
        if (args.size < 3) throw FunctionArgumentException("rsaVerify expects 3-4 arguments (data, signature, publicKey, algorithm?), got ${args.size}")

        val data = args[0].asString()
        val signatureB64 = args[1].asString()
        val publicKeyB64 = args[2].asString()
        val algorithm = if (args.size > 3) args[3].asString() else "SHA256withRSA"

        return try {
            val keyBytes = Base64.getDecoder().decode(publicKeyB64)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)

            val sig = Signature.getInstance(algorithm)
            sig.initVerify(publicKey)
            sig.update(data.toByteArray(Charsets.UTF_8))

            val signatureBytes = Base64.getDecoder().decode(signatureB64)
            UDM.Scalar(sig.verify(signatureBytes))
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            throw FunctionArgumentException("rsaVerify failed: ${e.message}")
        }
    }

    // =========================================================================
    // Encrypt / Decrypt
    // =========================================================================

    /**
     * rsaEncrypt(data, publicKeyBase64) -> encrypted (Base64)
     * Note: RSA encryption is limited to small data (keySize/8 - 11 bytes for PKCS1).
     * For large data, use AES with RSA-encrypted key (hybrid encryption).
     */
    fun rsaEncrypt(args: List<UDM>): UDM {
        if (args.size != 2) throw FunctionArgumentException("rsaEncrypt expects 2 arguments (data, publicKey), got ${args.size}")

        val data = args[0].asString()
        val publicKeyB64 = args[1].asString()

        return try {
            val keyBytes = Base64.getDecoder().decode(publicKeyB64)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

            UDM.Scalar(Base64.getEncoder().encodeToString(encrypted))
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            throw FunctionArgumentException("rsaEncrypt failed: ${e.message}. Hint: RSA can only encrypt data smaller than key size minus padding (e.g., 245 bytes for 2048-bit key).")
        }
    }

    /**
     * rsaDecrypt(encryptedBase64, privateKeyBase64) -> decrypted string
     */
    fun rsaDecrypt(args: List<UDM>): UDM {
        if (args.size != 2) throw FunctionArgumentException("rsaDecrypt expects 2 arguments (encrypted, privateKey), got ${args.size}")

        val encryptedB64 = args[0].asString()
        val privateKeyB64 = args[1].asString()

        return try {
            val keyBytes = Base64.getDecoder().decode(privateKeyB64)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            val decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedB64))

            UDM.Scalar(String(decrypted, Charsets.UTF_8))
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            throw FunctionArgumentException("rsaDecrypt failed: ${e.message}")
        }
    }

    // Helper
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: throw FunctionArgumentException("Expected string, got null")
        else -> throw FunctionArgumentException("Expected string, got ${this::class.simpleName}")
    }

    private fun UDM.asNumber(): Double = when (this) {
        is UDM.Scalar -> when (value) {
            is Number -> (value as Number).toDouble()
            else -> throw FunctionArgumentException("Expected number")
        }
        else -> throw FunctionArgumentException("Expected number")
    }
}
