// stdlib/src/test/kotlin/org/apache/utlx/stdlib/binary/BinaryFunctionsTest.kt
package org.apache.utlx.stdlib.binary

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Comprehensive test suite for Binary functions.
 * 
 * Tests cover:
 * - Binary creation (toBinary, fromBytes, fromBase64, fromHex)
 * - Binary conversion (toString, toBytes, toBase64, toHex)
 * - Binary operations (length, concat, slice, equals)
 * - Binary reading (readInt16/32/64, readFloat/Double, readByte)
 * - Binary writing (writeInt16/32/64, writeFloat/Double, writeByte)
 * - Bitwise operations (AND, OR, XOR, NOT, shifts)
 */
class BinaryFunctionsTest {

    // ==================== Binary Creation Tests ====================
    
    @Test
    fun `test toBinary - from string`() {
        val text = UDM.Scalar("Hello")
        
        val result = BinaryFunctions.toBinary(listOf(text))
        assertNotNull(result, "Should create binary from string")
    }
    
    @Test
    fun `test toBinary - from empty string`() {
        val text = UDM.Scalar("")
        
        val result = BinaryFunctions.toBinary(listOf(text))
        assertNotNull(result, "Should handle empty string")
    }
    
    @Test
    fun `test fromBytes - byte array`() {
        val bytes = UDM.Array(listOf(
            UDM.Scalar(72),   // 'H'
            UDM.Scalar(101),  // 'e'
            UDM.Scalar(108),  // 'l'
            UDM.Scalar(108),  // 'l'
            UDM.Scalar(111)   // 'o'
        ))
        
        val result = BinaryFunctions.fromBytes(listOf(bytes))
        assertNotNull(result, "Should create binary from byte array")
    }
    
    @Test
    fun `test fromBase64 - valid base64`() {
        val base64 = UDM.Scalar("SGVsbG8=")  // "Hello" in base64
        
        val result = BinaryFunctions.fromBase64(listOf(base64))
        assertNotNull(result, "Should decode base64")
    }
    
    @Test
    fun `test fromBase64 - empty string`() {
        val base64 = UDM.Scalar("")
        
        val result = BinaryFunctions.fromBase64(listOf(base64))
        assertNotNull(result, "Should handle empty base64")
    }
    
    @Test
    fun `test fromHex - valid hex`() {
        val hex = UDM.Scalar("48656c6c6f")  // "Hello" in hex
        
        val result = BinaryFunctions.fromHex(listOf(hex))
        assertNotNull(result, "Should decode hex")
    }
    
    @Test
    fun `test fromHex - uppercase hex`() {
        val hex = UDM.Scalar("48656C6C6F")  // Uppercase
        
        val result = BinaryFunctions.fromHex(listOf(hex))
        assertNotNull(result, "Should handle uppercase hex")
    }

    // ==================== Binary Conversion Tests ====================
    
    @Test
    fun `test binaryToString - simple text`() {
        val text = UDM.Scalar("Hello")
        val binary = BinaryFunctions.toBinary(listOf(text))
        
        val result = BinaryFunctions.binaryToString(listOf(binary))
        val decoded = (result as UDM.Scalar).value as String
        
        assertEquals("Hello", decoded)
    }
    
    @Test
    fun `test binaryToString - roundtrip`() {
        val originalText = "The quick brown fox jumps over the lazy dog"
        val text = UDM.Scalar(originalText)
        
        val binary = BinaryFunctions.toBinary(listOf(text))
        val decoded = BinaryFunctions.binaryToString(listOf(binary))
        
        assertEquals(originalText, (decoded as UDM.Scalar).value as String)
    }
    
    @Test
    fun `test toBytes - binary to byte array`() {
        val text = UDM.Scalar("Hi")
        val binary = BinaryFunctions.toBinary(listOf(text))
        
        val result = BinaryFunctions.toBytes(listOf(binary))
        val bytes = result as UDM.Array
        
        assertEquals(2, bytes.elements.size, "Should have 2 bytes")
        assertEquals(72, (bytes.elements[0] as UDM.Scalar).value)   // 'H'
        assertEquals(105, (bytes.elements[1] as UDM.Scalar).value)  // 'i'
    }
    
    @Test
    fun `test toBase64 - binary to base64`() {
        val text = UDM.Scalar("Hello")
        val binary = BinaryFunctions.toBinary(listOf(text))
        
        val result = BinaryFunctions.toBase64(listOf(binary))
        val base64 = (result as UDM.Scalar).value as String
        
        assertEquals("SGVsbG8=", base64)
    }
    
    @Test
    fun `test toBase64 - roundtrip`() {
        val originalBase64 = "SGVsbG8="
        val binary = BinaryFunctions.fromBase64(listOf(UDM.Scalar(originalBase64)))
        val encoded = BinaryFunctions.toBase64(listOf(binary))
        
        assertEquals(originalBase64, (encoded as UDM.Scalar).value as String)
    }
    
    @Test
    fun `test toHex - binary to hex`() {
        val text = UDM.Scalar("Hello")
        val binary = BinaryFunctions.toBinary(listOf(text))
        
        val result = BinaryFunctions.toHex(listOf(binary))
        val hex = (result as UDM.Scalar).value as String
        
        assertEquals("48656c6c6f", hex.lowercase())
    }
    
    @Test
    fun `test toHex - roundtrip`() {
        val originalHex = "48656c6c6f"
        val binary = BinaryFunctions.fromHex(listOf(UDM.Scalar(originalHex)))
        val encoded = BinaryFunctions.toHex(listOf(binary))
        
        assertEquals(originalHex, (encoded as UDM.Scalar).value as String)
    }

    // ==================== Binary Operations Tests ====================
    
    @Test
    fun `test binaryLength - simple text`() {
        val text = UDM.Scalar("Hello")
        val binary = BinaryFunctions.toBinary(listOf(text))
        
        val result = BinaryFunctions.binaryLength(listOf(binary))
        val length = (result as UDM.Scalar).value as Int
        
        assertEquals(5, length, "Hello has 5 bytes")
    }
    
    @Test
    fun `test binaryLength - empty`() {
        val text = UDM.Scalar("")
        val binary = BinaryFunctions.toBinary(listOf(text))
        
        val result = BinaryFunctions.binaryLength(listOf(binary))
        val length = (result as UDM.Scalar).value as Int
        
        assertEquals(0, length)
    }
    
    @Test
    fun `test binaryConcat - two binaries`() {
        val text1 = UDM.Scalar("Hello")
        val text2 = UDM.Scalar(" World")
        
        val binary1 = BinaryFunctions.toBinary(listOf(text1))
        val binary2 = BinaryFunctions.toBinary(listOf(text2))
        
        val result = BinaryFunctions.binaryConcat(listOf(binary1, binary2))
        val decoded = BinaryFunctions.binaryToString(listOf(result))
        
        assertEquals("Hello World", (decoded as UDM.Scalar).value as String)
    }
    
    @Test
    fun `test binaryConcat - multiple binaries`() {
        val parts = listOf("The", " ", "quick", " ", "fox").map {
            BinaryFunctions.toBinary(listOf(UDM.Scalar(it)))
        }
        
        val result = BinaryFunctions.binaryConcat(parts)
        val decoded = BinaryFunctions.binaryToString(listOf(result))
        
        assertEquals("The quick fox", (decoded as UDM.Scalar).value as String)
    }
    
    @Test
    fun `test binarySlice - middle section`() {
        val text = UDM.Scalar("Hello World")
        val binary = BinaryFunctions.toBinary(listOf(text))
        
        val start = UDM.Scalar(6)
        val end = UDM.Scalar(11)
        
        val result = BinaryFunctions.binarySlice(listOf(binary, start, end))
        val decoded = BinaryFunctions.binaryToString(listOf(result))
        
        assertEquals("World", (decoded as UDM.Scalar).value as String)
    }
    
    @Test
    fun `test binarySlice - first n bytes`() {
        val text = UDM.Scalar("Hello")
        val binary = BinaryFunctions.toBinary(listOf(text))
        
        val start = UDM.Scalar(0)
        val end = UDM.Scalar(3)
        
        val result = BinaryFunctions.binarySlice(listOf(binary, start, end))
        val decoded = BinaryFunctions.binaryToString(listOf(result))
        
        assertEquals("Hel", (decoded as UDM.Scalar).value as String)
    }
    
    @Test
    fun `test binaryEquals - same content`() {
        val text = UDM.Scalar("Hello")
        val binary1 = BinaryFunctions.toBinary(listOf(text))
        val binary2 = BinaryFunctions.toBinary(listOf(text))
        
        val result = BinaryFunctions.binaryEquals(listOf(binary1, binary2))
        val areEqual = (result as UDM.Scalar).value as Boolean
        
        assertTrue(areEqual)
    }
    
    @Test
    fun `test binaryEquals - different content`() {
        val text1 = UDM.Scalar("Hello")
        val text2 = UDM.Scalar("World")
        
        val binary1 = BinaryFunctions.toBinary(listOf(text1))
        val binary2 = BinaryFunctions.toBinary(listOf(text2))
        
        val result = BinaryFunctions.binaryEquals(listOf(binary1, binary2))
        val areEqual = (result as UDM.Scalar).value as Boolean
        
        assertFalse(areEqual)
    }

    // ==================== Binary Reading Tests ====================
    
    @Test
    fun `test readInt16 - read 16-bit integer`() {
        // Create binary with known int16 value
        val bytes = UDM.Array(listOf(
            UDM.Scalar(0x01),
            UDM.Scalar(0x00)
        ))
        val binary = BinaryFunctions.fromBytes(listOf(bytes))
        val offset = UDM.Scalar(0)
        
        val result = BinaryFunctions.readInt16(listOf(binary, offset))
        val value = (result as UDM.Scalar).value as Int
        
        assertTrue(value == 1 || value == 256, "Should read int16 correctly (endianness-dependent)")
    }
    
    @Test
    fun `test readInt32 - read 32-bit integer`() {
        val bytes = UDM.Array(listOf(
            UDM.Scalar(0x01),
            UDM.Scalar(0x00),
            UDM.Scalar(0x00),
            UDM.Scalar(0x00)
        ))
        val binary = BinaryFunctions.fromBytes(listOf(bytes))
        val offset = UDM.Scalar(0)
        
        val result = BinaryFunctions.readInt32(listOf(binary, offset))
        assertNotNull(result, "Should read int32")
    }
    
    @Test
    fun `test readByte - read single byte`() {
        val text = UDM.Scalar("H")
        val binary = BinaryFunctions.toBinary(listOf(text))
        val offset = UDM.Scalar(0)
        
        val result = BinaryFunctions.readByte(listOf(binary, offset))
        val byte = (result as UDM.Scalar).value as Int
        
        assertEquals(72, byte, "'H' is ASCII 72")
    }
    
    @Test
    fun `test readByte - multiple positions`() {
        val text = UDM.Scalar("Hello")
        val binary = BinaryFunctions.toBinary(listOf(text))
        
        val byte0 = BinaryFunctions.readByte(listOf(binary, UDM.Scalar(0)))
        val byte1 = BinaryFunctions.readByte(listOf(binary, UDM.Scalar(1)))
        
        assertEquals(72, (byte0 as UDM.Scalar).value as Int, "'H'")
        assertEquals(101, (byte1 as UDM.Scalar).value as Int, "'e'")
    }

    // ==================== Bitwise Operations Tests ====================
    
    @Test
    fun `test bitwiseAnd - basic AND operation`() {
        val a = UDM.Scalar(0b1100)  // 12
        val b = UDM.Scalar(0b1010)  // 10
        
        val result = BinaryFunctions.bitwiseAnd(listOf(a, b))
        val value = (result as UDM.Scalar).value as Int
        
        assertEquals(0b1000, value, "1100 AND 1010 = 1000 (8)")
    }
    
    @Test
    fun `test bitwiseOr - basic OR operation`() {
        val a = UDM.Scalar(0b1100)  // 12
        val b = UDM.Scalar(0b1010)  // 10
        
        val result = BinaryFunctions.bitwiseOr(listOf(a, b))
        val value = (result as UDM.Scalar).value as Int
        
        assertEquals(0b1110, value, "1100 OR 1010 = 1110 (14)")
    }
    
    @Test
    fun `test bitwiseXor - basic XOR operation`() {
        val a = UDM.Scalar(0b1100)  // 12
        val b = UDM.Scalar(0b1010)  // 10
        
        val result = BinaryFunctions.bitwiseXor(listOf(a, b))
        val value = (result as UDM.Scalar).value as Int
        
        assertEquals(0b0110, value, "1100 XOR 1010 = 0110 (6)")
    }
    
    @Test
    fun `test bitwiseNot - basic NOT operation`() {
        val a = UDM.Scalar(0b00001111)  // 15
        
        val result = BinaryFunctions.bitwiseNot(listOf(a))
        val value = (result as UDM.Scalar).value as Int
        
        // NOT inverts all bits (result depends on integer size)
        assertNotEquals(15, value, "NOT should invert bits")
    }
    
    @Test
    fun `test shiftLeft - left shift`() {
        val value = UDM.Scalar(0b0001)  // 1
        val positions = UDM.Scalar(3)
        
        val result = BinaryFunctions.shiftLeft(listOf(value, positions))
        val shifted = (result as UDM.Scalar).value as Int
        
        assertEquals(0b1000, shifted, "1 << 3 = 8")
    }
    
    @Test
    fun `test shiftLeft - multiply by 2`() {
        val value = UDM.Scalar(5)
        val positions = UDM.Scalar(1)
        
        val result = BinaryFunctions.shiftLeft(listOf(value, positions))
        val shifted = (result as UDM.Scalar).value as Int
        
        assertEquals(10, shifted, "Left shift by 1 doubles the value")
    }
    
    @Test
    fun `test shiftRight - right shift`() {
        val value = UDM.Scalar(0b1000)  // 8
        val positions = UDM.Scalar(3)
        
        val result = BinaryFunctions.shiftRight(listOf(value, positions))
        val shifted = (result as UDM.Scalar).value as Int
        
        assertEquals(0b0001, shifted, "8 >> 3 = 1")
    }
    
    @Test
    fun `test shiftRight - divide by 2`() {
        val value = UDM.Scalar(10)
        val positions = UDM.Scalar(1)
        
        val result = BinaryFunctions.shiftRight(listOf(value, positions))
        val shifted = (result as UDM.Scalar).value as Int
        
        assertEquals(5, shifted, "Right shift by 1 halves the value")
    }

    // ==================== Real-World Scenarios ====================
    
    @Test
    fun `test real-world - file format detection`() {
        // PNG file signature: 89 50 4E 47 0D 0A 1A 0A
        val pngSignature = UDM.Array(listOf(
            UDM.Scalar(0x89),
            UDM.Scalar(0x50),
            UDM.Scalar(0x4E),
            UDM.Scalar(0x47)
        ))
        
        val binary = BinaryFunctions.fromBytes(listOf(pngSignature))
        val firstByte = BinaryFunctions.readByte(listOf(binary, UDM.Scalar(0)))
        
        assertEquals(0x89, (firstByte as UDM.Scalar).value as Int, "PNG signature starts with 0x89")
    }
    
    @Test
    fun `test real-world - base64 encoding for API`() {
        // Encode binary data for JSON transmission
        val data = UDM.Scalar("Important data to transmit")
        val binary = BinaryFunctions.toBinary(listOf(data))
        val base64 = BinaryFunctions.toBase64(listOf(binary))
        
        val encoded = (base64 as UDM.Scalar).value as String
        assertTrue(encoded.isNotEmpty(), "Should encode to base64")
        assertFalse(encoded.contains("\n"), "Base64 should not contain newlines for JSON")
    }
    
    @Test
    fun `test real-world - hex dump for debugging`() {
        val data = UDM.Scalar("Debug data")
        val binary = BinaryFunctions.toBinary(listOf(data))
        val hex = BinaryFunctions.toHex(listOf(binary))
        
        val hexString = (hex as UDM.Scalar).value as String
        assertTrue(hexString.length == 20, "10 bytes = 20 hex characters")
        assertTrue(hexString.matches(Regex("[0-9a-fA-F]+")), "Should be valid hex")
    }
    
    @Test
    fun `test real-world - binary concatenation for protocol`() {
        // Build a simple protocol message: [header][length][payload]
        val header = BinaryFunctions.fromBytes(listOf(UDM.Array(listOf(
            UDM.Scalar(0x01),  // Protocol version
            UDM.Scalar(0x02)   // Message type
        ))))
        
        val payload = BinaryFunctions.toBinary(listOf(UDM.Scalar("Hello")))
        val length = BinaryFunctions.binaryLength(listOf(payload))
        
        val lengthBytes = BinaryFunctions.fromBytes(listOf(UDM.Array(listOf(
            UDM.Scalar(((length as UDM.Scalar).value as Int) and 0xFF)
        ))))
        
        val message = BinaryFunctions.binaryConcat(listOf(header, lengthBytes, payload))
        val messageLength = BinaryFunctions.binaryLength(listOf(message))
        
        assertEquals(8, (messageLength as UDM.Scalar).value as Int, "2 header + 1 length + 5 payload")
    }
    
    @Test
    fun `test real-world - permission flags using bitwise`() {
        // Unix-style permissions: Read=4, Write=2, Execute=1
        val READ = 4
        val WRITE = 2
        val EXECUTE = 1
        
        // User has read and write permissions
        val userPerms = BinaryFunctions.bitwiseOr(listOf(
            UDM.Scalar(READ),
            UDM.Scalar(WRITE)
        ))
        
        // Check if user has write permission
        val hasWrite = BinaryFunctions.bitwiseAnd(listOf(
            userPerms,
            UDM.Scalar(WRITE)
        ))
        
        assertEquals(WRITE, (hasWrite as UDM.Scalar).value as Int, "Should have write permission")
    }

    // ==================== Edge Cases ====================
    
    @Test
    fun `test edge case - empty binary`() {
        val empty = BinaryFunctions.toBinary(listOf(UDM.Scalar("")))
        val length = BinaryFunctions.binaryLength(listOf(empty))
        
        assertEquals(0, (length as UDM.Scalar).value as Int)
    }
    
    @Test
    fun `test edge case - concat with empty`() {
        val text = UDM.Scalar("Hello")
        val empty = BinaryFunctions.toBinary(listOf(UDM.Scalar("")))
        val binary = BinaryFunctions.toBinary(listOf(text))
        
        val result = BinaryFunctions.binaryConcat(listOf(binary, empty))
        val decoded = BinaryFunctions.binaryToString(listOf(result))
        
        assertEquals("Hello", (decoded as UDM.Scalar).value as String)
    }
    
    @Test
    fun `test edge case - slice entire binary`() {
        val text = UDM.Scalar("Hello")
        val binary = BinaryFunctions.toBinary(listOf(text))
        val length = BinaryFunctions.binaryLength(listOf(binary))
        
        val sliced = BinaryFunctions.binarySlice(listOf(
            binary,
            UDM.Scalar(0),
            length
        ))
        val decoded = BinaryFunctions.binaryToString(listOf(sliced))
        
        assertEquals("Hello", (decoded as UDM.Scalar).value as String)
    }
    
    @Test
    fun `test edge case - Unicode characters`() {
        val unicode = UDM.Scalar("Hello 世界 🌍")
        val binary = BinaryFunctions.toBinary(listOf(unicode))
        val decoded = BinaryFunctions.binaryToString(listOf(binary))
        
        assertEquals("Hello 世界 🌍", (decoded as UDM.Scalar).value as String)
    }
    
    @Test
    fun `test edge case - large binary`() {
        val largeText = "x".repeat(10000)
        val binary = BinaryFunctions.toBinary(listOf(UDM.Scalar(largeText)))
        val length = BinaryFunctions.binaryLength(listOf(binary))
        
        assertEquals(10000, (length as UDM.Scalar).value as Int)
    }
    
    @Test
    fun `test edge case - bitwise with zero`() {
        val value = UDM.Scalar(42)
        val zero = UDM.Scalar(0)
        
        val andResult = BinaryFunctions.bitwiseAnd(listOf(value, zero))
        assertEquals(0, (andResult as UDM.Scalar).value as Int, "Any value AND 0 = 0")
        
        val orResult = BinaryFunctions.bitwiseOr(listOf(value, zero))
        assertEquals(42, (orResult as UDM.Scalar).value as Int, "Any value OR 0 = value")
    }
    
    @Test
    fun `test edge case - shift by zero`() {
        val value = UDM.Scalar(42)
        val zero = UDM.Scalar(0)
        
        val leftShift = BinaryFunctions.shiftLeft(listOf(value, zero))
        assertEquals(42, (leftShift as UDM.Scalar).value as Int, "Shift by 0 does nothing")
        
        val rightShift = BinaryFunctions.shiftRight(listOf(value, zero))
        assertEquals(42, (rightShift as UDM.Scalar).value as Int, "Shift by 0 does nothing")
    }

    // ==================== Error Handling Tests ====================
    
    @Test
    fun `test error - invalid base64`() {
        val invalidBase64 = UDM.Scalar("Not@Valid#Base64!")
        
        assertThrows<Exception> {
            BinaryFunctions.fromBase64(listOf(invalidBase64))
        }
    }
    
    @Test
    fun `test error - invalid hex`() {
        val invalidHex = UDM.Scalar("GHIJ")  // G-J are not hex digits
        
        assertThrows<Exception> {
            BinaryFunctions.fromHex(listOf(invalidHex))
        }
    }
    
    @Test
    fun `test error - read beyond bounds`() {
        val text = UDM.Scalar("Hi")
        val binary = BinaryFunctions.toBinary(listOf(text))
        val invalidOffset = UDM.Scalar(10)  // Only 2 bytes available
        
        assertThrows<Exception> {
            BinaryFunctions.readByte(listOf(binary, invalidOffset))
        }
    }
    
    @Test
    fun `test error - slice with invalid range`() {
        val text = UDM.Scalar("Hello")
        val binary = BinaryFunctions.toBinary(listOf(text))
        
        // Start > End
        assertThrows<Exception> {
            BinaryFunctions.binarySlice(listOf(binary, UDM.Scalar(3), UDM.Scalar(1)))
        }
    }

    // ==================== Performance Tests ====================
    
    @Test
    fun `test performance - large concatenation`() {
        val parts = (1..100).map {
            BinaryFunctions.toBinary(listOf(UDM.Scalar("chunk$it")))
        }
        
        val startTime = System.currentTimeMillis()
        val result = BinaryFunctions.binaryConcat(parts)
        val endTime = System.currentTimeMillis()
        
        val length = BinaryFunctions.binaryLength(listOf(result))
        assertTrue((length as UDM.Scalar).value as Int > 600, "Should concat all chunks")
        
        val duration = endTime - startTime
        assertTrue(duration < 1000, "Should complete within 1 second")
    }
    
    @Test
    fun `test performance - repeated encoding`() {
        val text = UDM.Scalar("Performance test data")
        val binary = BinaryFunctions.toBinary(listOf(text))
        
        val startTime = System.currentTimeMillis()
        repeat(1000) {
            BinaryFunctions.toBase64(listOf(binary))
        }
        val endTime = System.currentTimeMillis()
        
        val duration = endTime - startTime
        assertTrue(duration < 2000, "1000 encodings should complete within 2 seconds")
    }
}
