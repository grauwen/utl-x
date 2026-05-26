package org.apache.utlx.stdlib.binary

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * F19: Tests for readInt64, readDouble, writeInt32, writeInt64, writeDouble.
 * Binary functions work with Base64-encoded UDM objects representing byte arrays.
 */
class BinaryReadWriteF19Test {

    // =========================================================================
    // writeInt32 → readInt32 round-trip
    // =========================================================================

    @Test fun `writeInt32 - produces binary object`() {
        val result = BinaryFunctions.writeInt32(listOf(UDM.Scalar(42)))
        assertTrue(result is UDM.Object)
        assertEquals("binary", (result as UDM.Object).get("_type")?.asString())
        assertNotNull(result.get("data"))
    }

    @Test fun `writeInt32 - wrong arg count throws`() {
        assertThrows<IllegalArgumentException> { BinaryFunctions.writeInt32(emptyList()) }
    }

    @Test fun `writeInt32 - non-number throws`() {
        assertThrows<IllegalArgumentException> { BinaryFunctions.writeInt32(listOf(UDM.Scalar("text"))) }
    }

    // =========================================================================
    // writeInt64
    // =========================================================================

    @Test fun `writeInt64 - produces binary object`() {
        val result = BinaryFunctions.writeInt64(listOf(UDM.Scalar(123456789L)))
        assertTrue(result is UDM.Object)
        assertEquals("binary", (result as UDM.Object).get("_type")?.asString())
    }

    @Test fun `writeInt64 - zero`() {
        val result = BinaryFunctions.writeInt64(listOf(UDM.Scalar(0)))
        assertTrue(result is UDM.Object)
    }

    @Test fun `writeInt64 - negative`() {
        val result = BinaryFunctions.writeInt64(listOf(UDM.Scalar(-1)))
        assertTrue(result is UDM.Object)
    }

    @Test fun `writeInt64 - wrong arg count throws`() {
        assertThrows<IllegalArgumentException> { BinaryFunctions.writeInt64(emptyList()) }
    }

    // =========================================================================
    // writeDouble
    // =========================================================================

    @Test fun `writeDouble - produces binary object`() {
        val result = BinaryFunctions.writeDouble(listOf(UDM.Scalar(3.141592653589793)))
        assertTrue(result is UDM.Object)
        assertEquals("binary", (result as UDM.Object).get("_type")?.asString())
    }

    @Test fun `writeDouble - zero`() {
        val result = BinaryFunctions.writeDouble(listOf(UDM.Scalar(0.0)))
        assertTrue(result is UDM.Object)
    }

    @Test fun `writeDouble - negative`() {
        val result = BinaryFunctions.writeDouble(listOf(UDM.Scalar(-99.99)))
        assertTrue(result is UDM.Object)
    }

    @Test fun `writeDouble - wrong arg count throws`() {
        assertThrows<IllegalArgumentException> { BinaryFunctions.writeDouble(emptyList()) }
    }

    @Test fun `writeDouble - non-number throws`() {
        assertThrows<IllegalArgumentException> { BinaryFunctions.writeDouble(listOf(UDM.Scalar("text"))) }
    }

    // =========================================================================
    // readInt64(binary, offset) and readDouble(binary, offset)
    // These require a binary UDM object produced by write functions
    // =========================================================================

    @Test fun `readInt64 - round trip with writeInt64`() {
        val written = BinaryFunctions.writeInt64(listOf(UDM.Scalar(42)))
        val read = BinaryFunctions.readInt64(listOf(written, UDM.Scalar(0)))
        assertEquals(42.0, (read as UDM.Scalar).value)
    }

    @Test fun `readInt64 - large value round trip`() {
        val written = BinaryFunctions.writeInt64(listOf(UDM.Scalar(Long.MAX_VALUE.toDouble())))
        val read = BinaryFunctions.readInt64(listOf(written, UDM.Scalar(0)))
        assertNotNull((read as UDM.Scalar).value)
    }

    @Test fun `readInt64 - wrong arg count throws`() {
        assertThrows<IllegalArgumentException> { BinaryFunctions.readInt64(listOf(UDM.Scalar(0))) }
    }

    @Test fun `readDouble - round trip with writeDouble`() {
        val written = BinaryFunctions.writeDouble(listOf(UDM.Scalar(3.14159)))
        val read = BinaryFunctions.readDouble(listOf(written, UDM.Scalar(0)))
        val value = (read as UDM.Scalar).value as Double
        assertEquals(3.14159, value, 0.00001)
    }

    @Test fun `readDouble - zero round trip`() {
        val written = BinaryFunctions.writeDouble(listOf(UDM.Scalar(0.0)))
        val read = BinaryFunctions.readDouble(listOf(written, UDM.Scalar(0)))
        assertEquals(0.0, (read as UDM.Scalar).value)
    }

    @Test fun `readDouble - negative round trip`() {
        val written = BinaryFunctions.writeDouble(listOf(UDM.Scalar(-99.99)))
        val read = BinaryFunctions.readDouble(listOf(written, UDM.Scalar(0)))
        val value = (read as UDM.Scalar).value as Double
        assertEquals(-99.99, value, 0.001)
    }

    @Test fun `readDouble - wrong arg count throws`() {
        assertThrows<IllegalArgumentException> { BinaryFunctions.readDouble(listOf(UDM.Scalar(0))) }
    }
}
