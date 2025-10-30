package org.apache.utlx.stdlib.encoding

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlin.test.*

class EncodingFunctionsTest {

    @Test
    fun testBase64Encode() {
        val input = "Hello World"
        val result = EncodingFunctions.base64Encode(listOf(UDM.Scalar(input)))
        assertEquals("SGVsbG8gV29ybGQ=", (result as UDM.Scalar).value)
    }

    @Test
    fun testBase64Decode() {
        val input = "SGVsbG8gV29ybGQ="
        val result = EncodingFunctions.base64Decode(listOf(UDM.Scalar(input)))
        assertEquals("Hello World", (result as UDM.Scalar).value)
    }

    @Test
    fun testUrlEncode() {
        val input = "Hello World"
        val result = EncodingFunctions.urlEncode(listOf(UDM.Scalar(input)))
        assertEquals("Hello+World", (result as UDM.Scalar).value)
    }

    @Test
    fun testUrlDecode() {
        val input = "Hello+World"
        val result = EncodingFunctions.urlDecode(listOf(UDM.Scalar(input)))
        assertEquals("Hello World", (result as UDM.Scalar).value)
    }

    @Test
    fun testHexEncode() {
        val input = "Hello"
        val result = EncodingFunctions.hexEncode(listOf(UDM.Scalar(input)))
        assertEquals("48656c6c6f", (result as UDM.Scalar).value)
    }

    @Test
    fun testHexDecode() {
        val input = "48656c6c6f"
        val result = EncodingFunctions.hexDecode(listOf(UDM.Scalar(input)))
        assertEquals("Hello", (result as UDM.Scalar).value)
    }

    @Test
    fun testMd5() {
        val input = "Hello"
        val result = EncodingFunctions.md5(listOf(UDM.Scalar(input)))
        assertEquals("8b1a9953c4611296a827abf8c47804d7", (result as UDM.Scalar).value)
    }

    @Test
    fun testSha256() {
        val input = "Hello"
        val result = EncodingFunctions.sha256(listOf(UDM.Scalar(input)))
        assertEquals("185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969", (result as UDM.Scalar).value)
    }

    @Test
    fun testSha512() {
        val input = "Hello"
        val result = EncodingFunctions.sha512(listOf(UDM.Scalar(input)))
        val expected = "3615f80c9d293ed7402687f94b22d58e529b8cc7916f8fac7fddf7fbd5af4cf777d3d795a7a00a16bf7e7f3fb9561ee9baae480da9fe7a18769e71886b03f315"
        assertEquals(expected, (result as UDM.Scalar).value)
    }

    @Test
    fun testSha1() {
        val input = "Hello"
        val result = EncodingFunctions.sha1(listOf(UDM.Scalar(input)))
        assertEquals("f7ff9e8b7bb2e09b70935a5d785e0cc5d9d0abf0", (result as UDM.Scalar).value)
    }

    @Test
    fun testHash_sha256() {
        val input = "Hello"
        val algorithm = "SHA-256"
        val result = EncodingFunctions.hash(listOf(UDM.Scalar(input), UDM.Scalar(algorithm)))
        assertEquals("185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969", (result as UDM.Scalar).value)
    }

    @Test
    fun testHmac() {
        val message = "Hello"
        val key = "secret"
        val algorithm = "HmacSHA256"
        val result = EncodingFunctions.hmac(listOf(UDM.Scalar(message), UDM.Scalar(key), UDM.Scalar(algorithm)))
        assertNotNull((result as UDM.Scalar).value)
        assertTrue((result.value as String).isNotEmpty())
    }

    // Note: testInvalidArguments removed - validation is handled at runtime by the UTL-X engine via @UTLXFunction annotations

    @Test
    fun testBase64RoundTrip() {
        val original = "Hello World! 123 @#$%"
        val encoded = EncodingFunctions.base64Encode(listOf(UDM.Scalar(original)))
        val decoded = EncodingFunctions.base64Decode(listOf(encoded))
        assertEquals(original, (decoded as UDM.Scalar).value)
    }

    @Test
    fun testUrlRoundTrip() {
        val original = "Hello World! & =+"
        val encoded = EncodingFunctions.urlEncode(listOf(UDM.Scalar(original)))
        val decoded = EncodingFunctions.urlDecode(listOf(encoded))
        assertEquals(original, (decoded as UDM.Scalar).value)
    }

    @Test
    fun testHexRoundTrip() {
        val original = "Hello World! 123"
        val encoded = EncodingFunctions.hexEncode(listOf(UDM.Scalar(original)))
        val decoded = EncodingFunctions.hexDecode(listOf(encoded))
        assertEquals(original, (decoded as UDM.Scalar).value)
    }
}