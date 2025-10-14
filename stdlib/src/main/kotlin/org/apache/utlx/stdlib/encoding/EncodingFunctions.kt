// stdlib/src/main/kotlin/org/apache/utlx/stdlib/encoding/EncodingFunctions.kt
package org.apache.utlx.stdlib.encoding

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import java.util.Base64
import java.net.URLEncoder
import java.net.URLDecoder

/**
 * Encoding and decoding functions
 */
object EncodingFunctions {
    
    /**
     * Base64 encode
     * Usage: base64-encode("Hello World")
     */
    fun base64Encode(args: List<UDM>): UDM {
        requireArgs(args, 1, "base64-encode")
        val str = args[0].asString()
        val encoded = Base64.getEncoder().encodeToString(str.toByteArray())
        return UDM.Scalar(encoded)
    }
    
    /**
     * Base64 decode
     * Usage: base64-decode("SGVsbG8gV29ybGQ=")
     */
    fun base64Decode(args: List<UDM>): UDM {
        requireArgs(args, 1, "base64-decode")
        val str = args[0].asString()
        return try {
            val decoded = String(Base64.getDecoder().decode(str))
            UDM.Scalar(decoded)
        } catch (e: IllegalArgumentException) {
            throw FunctionArgumentException("Invalid base64 string: $str")
        }
    }
    
    /**
     * URL encode
     * Usage: url-encode("hello world!")
     */
    fun urlEncode(args: List<UDM>): UDM {
        requireArgs(args, 1, "url-encode")
        val str = args[0].asString()
        val encoded = URLEncoder.encode(str, "UTF-8")
        return UDM.Scalar(encoded)
    }
    
    /**
     * URL decode
     * Usage: url-decode("hello+world%21")
     */
    fun urlDecode(args: List<UDM>): UDM {
        requireArgs(args, 1, "url-decode")
        val str = args[0].asString()
        val decoded = URLDecoder.decode(str, "UTF-8")
        return UDM.Scalar(decoded)
    }
    
    /**
     * Hex encode
     * Usage: hex-encode("Hello")
     */
    fun hexEncode(args: List<UDM>): UDM {
        requireArgs(args, 1, "hex-encode")
        val str = args[0].asString()
        val hex = str.toByteArray().joinToString("") { 
            "%02x".format(it) 
        }
        return UDM.Scalar(hex)
    }
    
    /**
     * Hex decode
     * Usage: hex-decode("48656c6c6f")
     */
    fun hexDecode(args: List<UDM>): UDM {
        requireArgs(args, 1, "hex-decode")
        val hex = args[0].asString()
        return try {
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            UDM.Scalar(String(bytes))
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid hex string: $hex")
        }
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value")
    }
}
