package org.apache.utlx.stdlib.binary

import org.apache.utlx.core.udm.UDM
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * Binary Data Operations
 * 
 * Implements DataWeave's dw::core::Binaries module.
 * Functions for reading, writing, and manipulating binary data.
 * 
 * Location: stdlib/src/main/kotlin/org/apache/utlx/stdlib/binary/BinaryFunctions.kt
 */
object BinaryFunctions {
    
    // ==================== BINARY CREATION ====================
    
    /**
     * Create binary from string
     * 
     * Usage: toBinary("Hello World") => Binary data
     * Usage: toBinary("Hello", "UTF-16") => Binary with specific encoding
     */
    fun toBinary(args: List<UDM>): UDM {
        if (args.size !in 1..2) {
            throw IllegalArgumentException("toBinary expects 1-2 arguments, got ${args.size}")
        }
        
        val value = args[0]
        if (value !is UDM.Scalar || value.value !is String) {
            throw IllegalArgumentException("toBinary expects string")
        }
        
        val encoding = if (args.size == 2) {
            val enc = args[1]
            if (enc !is UDM.Scalar || enc.value !is String) {
                throw IllegalArgumentException("Encoding must be string")
            }
            enc.value as String
        } else {
            "UTF-8"
        }
        
        val bytes = (value.value as String).toByteArray(charset(encoding))
        
        return createBinaryUDM(bytes)
    }
    
    /**
     * Create binary from byte array
     * 
     * Usage: fromBytes([72, 101, 108, 108, 111]) => Binary "Hello"
     */
    fun fromBytes(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("fromBytes expects 1 argument, got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("fromBytes expects array")
        }
        
        val bytes = array.elements.map { element ->
            if (element is UDM.Scalar && element.value is Number) {
                (element.value as Number).toInt().toByte()
            } else {
                throw IllegalArgumentException("Array must contain numbers")
            }
        }.toByteArray()
        
        return createBinaryUDM(bytes)
    }
    
    /**
     * Create binary from Base64 string
     * 
     * Usage: fromBase64("SGVsbG8gV29ybGQ=") => Binary "Hello World"
     */
    fun fromBase64(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("fromBase64 expects 1 argument, got ${args.size}")
        }
        
        val base64 = args[0]
        if (base64 !is UDM.Scalar || base64.value !is String) {
            throw IllegalArgumentException("fromBase64 expects string")
        }
        
        val bytes = Base64.getDecoder().decode(base64.value as String)
        
        return createBinaryUDM(bytes)
    }
    
    /**
     * Create binary from hex string
     * 
     * Usage: fromHex("48656c6c6f") => Binary "Hello"
     */
    fun fromHex(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("fromHex expects 1 argument, got ${args.size}")
        }
        
        val hex = args[0]
        if (hex !is UDM.Scalar || hex.value !is String) {
            throw IllegalArgumentException("fromHex expects string")
        }
        
        val hexString = (hex.value as String).replace(" ", "")
        
        if (hexString.length % 2 != 0) {
            throw IllegalArgumentException("Hex string must have even length")
        }
        
        val bytes = hexString.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        
        return createBinaryUDM(bytes)
    }
    
    // ==================== BINARY CONVERSION ====================
    
    /**
     * Convert binary to string
     * 
     * Usage: binaryToString(binary) => "Hello World"
     * Usage: binaryToString(binary, "UTF-16") => String with encoding
     */
    fun binaryToString(args: List<UDM>): UDM {
        if (args.size !in 1..2) {
            throw IllegalArgumentException("binaryToString expects 1-2 arguments, got ${args.size}")
        }
        
        val bytes = extractBytes(args[0])
        
        val encoding = if (args.size == 2) {
            val enc = args[1]
            if (enc !is UDM.Scalar || enc.value !is String) {
                throw IllegalArgumentException("Encoding must be string")
            }
            enc.value as String
        } else {
            "UTF-8"
        }
        
        val str = String(bytes, charset(encoding))
        
        return UDM.Scalar(str)
    }
    
    /**
     * Convert binary to byte array
     * 
     * Usage: toBytes(binary) => [72, 101, 108, 108, 111]
     */
    fun toBytes(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("toBytes expects 1 argument, got ${args.size}")
        }
        
        val bytes = extractBytes(args[0])
        
        val array = bytes.map { UDM.Scalar(it.toInt().toDouble()) }
        
        return UDM.Array(array)
    }
    
    /**
     * Convert binary to Base64 string
     * 
     * Usage: toBase64(binary) => "SGVsbG8gV29ybGQ="
     */
    fun toBase64(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("toBase64 expects 1 argument, got ${args.size}")
        }
        
        val bytes = extractBytes(args[0])
        val base64 = Base64.getEncoder().encodeToString(bytes)
        
        return UDM.Scalar(base64)
    }
    
    /**
     * Convert binary to hex string
     * 
     * Usage: toHex(binary) => "48656c6c6f"
     */
    fun toHex(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("toHex expects 1 argument, got ${args.size}")
        }
        
        val bytes = extractBytes(args[0])
        val hex = bytes.joinToString("") { "%02x".format(it) }
        
        return UDM.Scalar(hex)
    }
    
    // ==================== BINARY OPERATIONS ====================
    
    /**
     * Get binary length in bytes
     * 
     * Usage: binaryLength(binary) => 11
     */
    fun binaryLength(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("binaryLength expects 1 argument, got ${args.size}")
        }
        
        val bytes = extractBytes(args[0])
        
        return UDM.Scalar(bytes.size.toDouble())
    }
    
    /**
     * Concatenate binary data
     * 
     * Usage: binaryConcat(binary1, binary2) => Combined binary
     */
    fun binaryConcat(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("binaryConcat expects at least 2 arguments, got ${args.size}")
        }
        
        val output = ByteArrayOutputStream()
        
        args.forEach { arg ->
            val bytes = extractBytes(arg)
            output.write(bytes)
        }
        
        return createBinaryUDM(output.toByteArray())
    }
    
    /**
     * Slice binary data
     * 
     * Usage: binarySlice(binary, 0, 5) => First 5 bytes
     */
    fun binarySlice(args: List<UDM>): UDM {
        if (args.size != 3) {
            throw IllegalArgumentException("binarySlice expects 3 arguments (binary, start, end), got ${args.size}")
        }
        
        val bytes = extractBytes(args[0])
        
        val start = args[1]
        if (start !is UDM.Scalar || start.value !is Number) {
            throw IllegalArgumentException("Start index must be number")
        }
        
        val end = args[2]
        if (end !is UDM.Scalar || end.value !is Number) {
            throw IllegalArgumentException("End index must be number")
        }
        
        val startIdx = (start.value as Number).toInt()
        val endIdx = (end.value as Number).toInt()
        
        val sliced = bytes.copyOfRange(startIdx, endIdx)
        
        return createBinaryUDM(sliced)
    }
    
    /**
     * Compare two binary values
     * 
     * Usage: binaryEquals(binary1, binary2) => true/false
     */
    fun binaryEquals(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("binaryEquals expects 2 arguments, got ${args.size}")
        }
        
        val bytes1 = extractBytes(args[0])
        val bytes2 = extractBytes(args[1])
        
        val equal = bytes1.contentEquals(bytes2)
        
        return UDM.Scalar(equal)
    }
    
    // ==================== BINARY READING (BIG ENDIAN) ====================
    
    /**
     * Read 16-bit integer from binary (big endian)
     * 
     * Usage: readInt16(binary, 0) => 256
     */
    fun readInt16(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("readInt16 expects 2 arguments (binary, offset), got ${args.size}")
        }
        
        val bytes = extractBytes(args[0])
        val offset = extractOffset(args[1])
        
        val buffer = ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.BIG_ENDIAN)
        val value = buffer.short
        
        return UDM.Scalar(value.toDouble())
    }
    
    /**
     * Read 32-bit integer from binary (big endian)
     * 
     * Usage: readInt32(binary, 0) => 16777216
     */
    fun readInt32(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("readInt32 expects 2 arguments (binary, offset), got ${args.size}")
        }
        
        val bytes = extractBytes(args[0])
        val offset = extractOffset(args[1])
        
        val buffer = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN)
        val value = buffer.int
        
        return UDM.Scalar(value.toDouble())
    }
    
    /**
     * Read 64-bit integer from binary (big endian)
     * 
     * Usage: readInt64(binary, 0) => 72057594037927936
     */
    fun readInt64(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("readInt64 expects 2 arguments (binary, offset), got ${args.size}")
        }
        
        val bytes = extractBytes(args[0])
        val offset = extractOffset(args[1])
        
        val buffer = ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN)
        val value = buffer.long
        
        return UDM.Scalar(value.toDouble())
    }
    
    /**
     * Read float (32-bit) from binary
     * 
     * Usage: readFloat(binary, 0) => 3.14
     */
    fun readFloat(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("readFloat expects 2 arguments (binary, offset), got ${args.size}")
        }
        
        val bytes = extractBytes(args[0])
        val offset = extractOffset(args[1])
        
        val buffer = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN)
        val value = buffer.float
        
        return UDM.Scalar(value.toDouble())
    }
    
    /**
     * Read double (64-bit) from binary
     * 
     * Usage: readDouble(binary, 0) => 3.141592653589793
     */
    fun readDouble(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("readDouble expects 2 arguments (binary, offset), got ${args.size}")
        }
        
        val bytes = extractBytes(args[0])
        val offset = extractOffset(args[1])
        
        val buffer = ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN)
        val value = buffer.double
        
        return UDM.Scalar(value)
    }
    
    /**
     * Read single byte
     * 
     * Usage: readByte(binary, 0) => 72
     */
    fun readByte(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("readByte expects 2 arguments (binary, offset), got ${args.size}")
        }
        
        val bytes = extractBytes(args[0])
        val offset = extractOffset(args[1])
        
        return UDM.Scalar(bytes[offset].toInt().toDouble())
    }
    
    // ==================== BINARY WRITING ====================
    
    /**
     * Write 16-bit integer to binary (big endian)
     * 
     * Usage: writeInt16(256) => Binary with 2 bytes
     */
    fun writeInt16(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("writeInt16 expects 1 argument, got ${args.size}")
        }
        
        val value = args[0]
        if (value !is UDM.Scalar || value.value !is Number) {
            throw IllegalArgumentException("Value must be number")
        }
        
        val buffer = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort((value.value as Number).toInt().toShort())
        
        return createBinaryUDM(buffer.array())
    }
    
    /**
     * Write 32-bit integer to binary (big endian)
     * 
     * Usage: writeInt32(16777216) => Binary with 4 bytes
     */
    fun writeInt32(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("writeInt32 expects 1 argument, got ${args.size}")
        }
        
        val value = args[0]
        if (value !is UDM.Scalar || value.value !is Number) {
            throw IllegalArgumentException("Value must be number")
        }
        
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt((value.value as Number).toInt())
        
        return createBinaryUDM(buffer.array())
    }
    
    /**
     * Write 64-bit integer to binary (big endian)
     * 
     * Usage: writeInt64(72057594037927936) => Binary with 8 bytes
     */
    fun writeInt64(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("writeInt64 expects 1 argument, got ${args.size}")
        }
        
        val value = args[0]
        if (value !is UDM.Scalar || value.value !is Number) {
            throw IllegalArgumentException("Value must be number")
        }
        
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buffer.putLong((value.value as Number).toLong())
        
        return createBinaryUDM(buffer.array())
    }
    
    /**
     * Write float (32-bit) to binary
     * 
     * Usage: writeFloat(3.14) => Binary with 4 bytes
     */
    fun writeFloat(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("writeFloat expects 1 argument, got ${args.size}")
        }
        
        val value = args[0]
        if (value !is UDM.Scalar || value.value !is Number) {
            throw IllegalArgumentException("Value must be number")
        }
        
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        buffer.putFloat((value.value as Number).toFloat())
        
        return createBinaryUDM(buffer.array())
    }
    
    /**
     * Write double (64-bit) to binary
     * 
     * Usage: writeDouble(3.141592653589793) => Binary with 8 bytes
     */
    fun writeDouble(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("writeDouble expects 1 argument, got ${args.size}")
        }
        
        val value = args[0]
        if (value !is UDM.Scalar || value.value !is Number) {
            throw IllegalArgumentException("Value must be number")
        }
        
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buffer.putDouble((value.value as Number).toDouble())
        
        return createBinaryUDM(buffer.array())
    }
    
    /**
     * Write single byte
     * 
     * Usage: writeByte(72) => Binary with 1 byte
     */
    fun writeByte(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("writeByte expects 1 argument, got ${args.size}")
        }
        
        val value = args[0]
        if (value !is UDM.Scalar || value.value !is Number) {
            throw IllegalArgumentException("Value must be number")
        }
        
        val byte = (value.value as Number).toInt().toByte()
        
        return createBinaryUDM(byteArrayOf(byte))
    }

   // ============================================
    // BIT OPERATIONS
    // ============================================
    
    /**
     * Performs bitwise AND operation on two binaries
     * 
     * @param binary1 First binary
     * @param binary2 Second binary
     * @return Result of AND operation
     * 
     * Example:
     * ```
     * bitwiseAnd(binary1, binary2)
     * ```
     */
    fun bitwiseAnd(binary1: UDM, binary2: UDM): UDM {
        val bytes1 = (binary1 as? UDM.Binary)?.data ?: return UDM.Scalar(null)
        val bytes2 = (binary2 as? UDM.Binary)?.data ?: return UDM.Scalar(null)
        
        val minLen = minOf(bytes1.size, bytes2.size)
        val result = ByteArray(minLen) { i ->
            (bytes1[i].toInt() and bytes2[i].toInt()).toByte()
        }
        
        return UDM.Binary(result)
    }
    
    /**
     * Performs bitwise OR operation on two binaries
     * 
     * @param binary1 First binary
     * @param binary2 Second binary
     * @return Result of OR operation
     * 
     * Example:
     * ```
     * bitwiseOr(binary1, binary2)
     * ```
     */
    fun bitwiseOr(binary1: UDM, binary2: UDM): UDM {
        val bytes1 = (binary1 as? UDM.Binary)?.data ?: return UDM.Scalar(null)
        val bytes2 = (binary2 as? UDM.Binary)?.data ?: return UDM.Scalar(null)
        
        val minLen = minOf(bytes1.size, bytes2.size)
        val result = ByteArray(minLen) { i ->
            (bytes1[i].toInt() or bytes2[i].toInt()).toByte()
        }
        
        return UDM.Binary(result)
    }
    
    /**
     * Performs bitwise XOR operation on two binaries
     * 
     * @param binary1 First binary
     * @param binary2 Second binary
     * @return Result of XOR operation
     * 
     * Example:
     * ```
     * bitwiseXor(binary1, binary2)
     * ```
     */
    fun bitwiseXor(binary1: UDM, binary2: UDM): UDM {
        val bytes1 = (binary1 as? UDM.Binary)?.data ?: return UDM.Scalar(null)
        val bytes2 = (binary2 as? UDM.Binary)?.data ?: return UDM.Scalar(null)
        
        val minLen = minOf(bytes1.size, bytes2.size)
        val result = ByteArray(minLen) { i ->
            (bytes1[i].toInt() xor bytes2[i].toInt()).toByte()
        }
        
        return UDM.Binary(result)
    }
    
    /**
     * Performs bitwise NOT operation (inversion)
     * 
     * @param binary The binary data
     * @return Inverted binary
     * 
     * Example:
     * ```
     * bitwiseNot(binary)
     * ```
     */
    fun bitwiseNot(binary: UDM): UDM {
        val bytes = (binary as? UDM.Binary)?.data ?: return UDM.Scalar(null)
        
        val result = ByteArray(bytes.size) { i ->
            (bytes[i].toInt().inv()).toByte()
        }
        
        return UDM.Binary(result)
    }
    
    /**
     * Shifts bits left by specified positions
     * 
     * @param binary The binary data
     * @param positions Number of positions to shift
     * @return Shifted binary
     * 
     * Example:
     * ```
     * shiftLeft(binary, 2) // Shift left by 2 bits
     * ```
     */
    fun shiftLeft(binary: UDM, positions: UDM): UDM {
        val bytes = (binary as? UDM.Binary)?.data ?: return UDM.Scalar(null)
        val shift = (positions as? UDM.Scalar)?.value?.toString()?.toIntOrNull() ?: 0
        
        val result = ByteArray(bytes.size) { i ->
            (bytes[i].toInt() shl shift).toByte()
        }
        
        return UDM.Binary(result)
    }
    
    /**
     * Shifts bits right by specified positions
     * 
     * @param binary The binary data
     * @param positions Number of positions to shift
     * @return Shifted binary
     * 
     * Example:
     * ```
     * shiftRight(binary, 2) // Shift right by 2 bits
     * ```
     */
    fun shiftRight(binary: UDM, positions: UDM): UDM {
        val bytes = (binary as? UDM.Binary)?.data ?: return UDM.Scalar(null)
        val shift = (positions as? UDM.Scalar)?.value?.toString()?.toIntOrNull() ?: 0
        
        val result = ByteArray(bytes.size) { i ->
            (bytes[i].toInt() shr shift).toByte()
        }
        
        return UDM.Binary(result)
    }

    // ============================================
    // BINARY COMPARISON
    // ============================================
    
    /**
     * Compares two binaries for equality
     * 
     * @param binary1 First binary
     * @param binary2 Second binary
     * @return true if binaries are equal
     * 
     * Example:
     * ```
     * equals(binary1, binary2)
     * ```
     */
    fun equals(binary1: UDM, binary2: UDM): UDM {
        val bytes1 = (binary1 as? UDM.Binary)?.data ?: return UDM.Scalar(false)
        val bytes2 = (binary2 as? UDM.Binary)?.data ?: return UDM.Scalar(false)
        
        return UDM.Scalar(bytes1.contentEquals(bytes2))
    }
    
    // ==================== HELPER FUNCTIONS ====================
    
    private fun createBinaryUDM(bytes: ByteArray): UDM {
        // Store as Base64-encoded string in UDM for transport
        // In a real implementation, UDM might have a Binary type
        val base64 = Base64.getEncoder().encodeToString(bytes)
        
        return UDM.Object(mapOf(
            "_type" to UDM.Scalar("binary"),
            "data" to UDM.Scalar(base64),
            "length" to UDM.Scalar(bytes.size.toDouble())
        ), emptyMap())
    }
    
    private fun extractBytes(value: UDM): ByteArray {
        return when (value) {
            is UDM.Object -> {
                // Extract from binary object
                val type = (value.properties["_type"] as? UDM.Scalar)?.value
                if (type == "binary") {
                    val data = (value.properties["data"] as? UDM.Scalar)?.value as? String
                    if (data != null) {
                        Base64.getDecoder().decode(data)
                    } else {
                        throw IllegalArgumentException("Invalid binary object")
                    }
                } else {
                    throw IllegalArgumentException("Not a binary object")
                }
            }
            is UDM.Scalar -> {
                // Try to decode from Base64 string
                if (value.value is String) {
                    try {
                        Base64.getDecoder().decode(value.value as String)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Invalid binary data")
                    }
                } else {
                    throw IllegalArgumentException("Expected binary data")
                }
            }
            else -> throw IllegalArgumentException("Expected binary data")
        }
    }
    
    private fun extractOffset(value: UDM): Int {
        if (value !is UDM.Scalar || value.value !is Number) {
            throw IllegalArgumentException("Offset must be number")
        }
        return (value.value as Number).toInt()
    }
}

// UDM.Binary is already defined in the core module

/**
 * Registration in Functions.kt:
 * 
 * Add these to a new registerBinaryFunctions() method:
 * 
 * private fun registerBinaryFunctions() {
 *     // Binary creation
 *     register("toBinary", BinaryFunctions::toBinary)
 *     register("fromBytes", BinaryFunctions::fromBytes)
 *     register("fromBase64", BinaryFunctions::fromBase64)
 *     register("fromHex", BinaryFunctions::fromHex)
 *     
 *     // Binary conversion
 *     register("binaryToString", BinaryFunctions::binaryToString)
 *     register("toBytes", BinaryFunctions::toBytes)
 *     register("toBase64", BinaryFunctions::toBase64)
 *     register("toHex", BinaryFunctions::toHex)
 *     
 *     // Binary operations
 *     register("binaryLength", BinaryFunctions::binaryLength)
 *     register("binaryConcat", BinaryFunctions::binaryConcat)
 *     register("binarySlice", BinaryFunctions::binarySlice)
 *     register("binaryEquals", BinaryFunctions::binaryEquals)
 *     
 *     // Binary reading
 *     register("readInt16", BinaryFunctions::readInt16)
 *     register("readInt32", BinaryFunctions::readInt32)
 *     register("readInt64", BinaryFunctions::readInt64)
 *     register("readFloat", BinaryFunctions::readFloat)
 *     register("readDouble", BinaryFunctions::readDouble)
 *     register("readByte", BinaryFunctions::readByte)
 *     
 *     // Binary writing
 *     register("writeInt16", BinaryFunctions::writeInt16)
 *     register("writeInt32", BinaryFunctions::writeInt32)
 *     register("writeInt64", BinaryFunctions::writeInt64)
 *     register("writeFloat", BinaryFunctions::writeFloat)
 *     register("writeDouble", BinaryFunctions::writeDouble)
 *     register("writeByte", BinaryFunctions::writeByte)
 *
 *     // BIT operations
 *      register("bitwiseAnd", BinaryFunctions::bitwiseAnd)
 *      register("bitwiseOr", BinaryFunctions::bitwiseOr)
 *      register("bitwiseXor", BinaryFunctions::bitwiseXor)
 *      register("bitwiseNot", BinaryFunctions::bitwiseNot)
 *      register("shiftLeft", BinaryFunctions::shiftLeft)
 *      register("shiftRight", BinaryFunctions::shiftRight)
 *
 *     // BINARY COMPARISON
 *     register("equalsBinary", BinaryFunctions::equals)
 * }
 * 
 * Then call in init block:
 * init {
 *     registerConversionFunctions()
 *     registerURLFunctions()
 *     registerTreeFunctions()
 *     registerCoercionFunctions()
 *     registerTimerFunctions()
 *     registerValueFunctions()
 *     registerDiffFunctions()
 *     registerMimeFunctions()
 *     registerMultipartFunctions()
 *     registerBinaryFunctions()      // ADD THIS!
 *     // ... rest
 * }
 */
