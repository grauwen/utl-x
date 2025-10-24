// stdlib/src/main/kotlin/org/apache/utlx/stdlib/xml/XMLEncodingBomFunctions.kt
package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * XML Encoding and BOM (Byte Order Mark) functions for UTL-X
 * 
 * These functions solve critical real-world problems:
 * - XML encoding detection and conversion (SAP, legacy systems)
 * - BOM handling for cross-platform compatibility (Windows/Unix)
 * - Enterprise integration scenarios
 */
object XMLEncodingBomFunctions {
    
    // ================================
    // XML ENCODING FUNCTIONS
    // ================================
    
    @UTLXFunction(
        description = "Detect encoding from XML declaration or BOM",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "array: Input array to process",
        "fromEncoding: Fromencoding value",
        "toEncoding: Toencoding value"
        ],
        returns = ": \"ISO-8859-1\"",
        example = "detectXMLEncoding('<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><root/>')",
        notes = "Returns: \"ISO-8859-1\"",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Detect encoding from XML declaration or BOM
     * Usage: detectXMLEncoding('<?xml version="1.0" encoding="ISO-8859-1"?><root/>')
     * Returns: "ISO-8859-1"
     */
    fun detectXMLEncoding(args: List<UDM>): UDM {
        requireArgs(args, 1, "detectXMLEncoding")

        // Check if input is a UDM.Object with preserved encoding in metadata
        if (args[0] is UDM.Object) {
            val obj = args[0] as UDM.Object
            val preservedEncoding = obj.getMetadata("xmlEncoding")
            if (preservedEncoding != null) {
                return UDM.Scalar(preservedEncoding.uppercase())
            }
        }

        // Fall back to string-based detection for raw XML strings
        val xml = args[0].asString() ?: throw FunctionArgumentException(
            "detectXMLEncoding() requires a string or XML object argument, got ${getTypeDescription(args[0])}"
        )

        // Check BOM first (if string starts with BOM character)
        if (xml.startsWith('\uFEFF')) {
            return UDM.Scalar("UTF-8")
        }

        // Parse XML declaration for encoding attribute
        val encodingRegex = """encoding\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
        val match = encodingRegex.find(xml)
        val encoding = match?.groupValues?.get(1)?.uppercase() ?: "UTF-8"

        return UDM.Scalar(encoding)
    }
    
    @UTLXFunction(
        description = "Convert XML from one encoding to another",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "array: Input array to process",
        "fromEncoding: Fromencoding value",
        "toEncoding: Toencoding value"
        ],
        returns = "Result of the operation",
        example = "convertXMLEncoding(xml, \"ISO-8859-1\", \"UTF-8\")",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Convert XML from one encoding to another
     * Usage: convertXMLEncoding(xml, "ISO-8859-1", "UTF-8")
     */
    fun convertXMLEncoding(args: List<UDM>): UDM {
        requireArgs(args, 3, "convertXMLEncoding")
        val xml = args[0].asString() ?: throw FunctionArgumentException(
            "convertXMLEncoding() first argument must be a string"
        )
        val fromEncoding = args[1].asString() ?: throw FunctionArgumentException(
            "convertXMLEncoding() second argument must be a string (source encoding)"
        )
        val toEncoding = args[2].asString() ?: throw FunctionArgumentException(
            "convertXMLEncoding() third argument must be a string (target encoding)"
        )
        
        try {
            // Validate encodings
            val sourceCharset = Charset.forName(fromEncoding)
            val targetCharset = Charset.forName(toEncoding)
            
            // Convert encoding (simulate byte-level conversion)
            val bytes = xml.toByteArray(sourceCharset)
            val converted = String(bytes, targetCharset)
            
            // Update XML declaration
            val result = updateXMLEncodingInternal(converted, toEncoding)
            return UDM.Scalar(result)
            
        } catch (e: Exception) {
            throw FunctionArgumentException("convertXMLEncoding() failed: ${e.message}")
        }
    }
    
    @UTLXFunction(
        description = "Update encoding in XML declaration",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "xml: Xml value",
        "encoding: Encoding value"
        ],
        returns = "Result of the operation",
        example = "updateXMLEncoding(xml, \"UTF-8\")",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Update encoding in XML declaration
     * Usage: updateXMLEncoding(xml, "UTF-8")
     */
    fun updateXMLEncoding(args: List<UDM>): UDM {
        requireArgs(args, 2, "updateXMLEncoding")
        val xml = args[0].asString() ?: throw FunctionArgumentException(
            "updateXMLEncoding() first argument must be a string"
        )
        val encoding = args[1].asString() ?: throw FunctionArgumentException(
            "updateXMLEncoding() second argument must be a string (encoding)"
        )
        
        val result = updateXMLEncodingInternal(xml, encoding)
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Check if encoding name is valid",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "encoding: Encoding value",
        "targetEncoding: Targetencoding value"
        ],
        returns = "Result of the operation",
        example = "validateEncoding(\"UTF-8\") -> true",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Check if encoding name is valid
     * Usage: validateEncoding("UTF-8") -> true
     */
    fun validateEncoding(args: List<UDM>): UDM {
        requireArgs(args, 1, "validateEncoding")
        val encoding = args[0].asString() ?: throw FunctionArgumentException(
            "validateEncoding() requires a string argument"
        )
        
        val isValid = try {
            Charset.forName(encoding)
            true
        } catch (e: Exception) {
            false
        }
        
        return UDM.Scalar(isValid)
    }
    
    @UTLXFunction(
        description = "Auto-detect and convert to target encoding",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "xml: Xml value",
        "targetEncoding: Targetencoding value"
        ],
        returns = "Result of the operation",
        example = "normalizeXMLEncoding(xml, \"UTF-8\")",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Auto-detect and convert to target encoding
     * Usage: normalizeXMLEncoding(xml, "UTF-8")
     */
    fun normalizeXMLEncoding(args: List<UDM>): UDM {
        requireArgs(args, 2, "normalizeXMLEncoding")
        val xml = args[0].asString() ?: throw FunctionArgumentException(
            "normalizeXMLEncoding() first argument must be a string"
        )
        val targetEncoding = args[1].asString() ?: throw FunctionArgumentException(
            "normalizeXMLEncoding() second argument must be a string (target encoding)"
        )
        
        // Detect current encoding
        val currentEncodingResult = detectXMLEncoding(listOf(args[0]))
        val currentEncoding = (currentEncodingResult as UDM.Scalar).value as String
        
        // Convert if different
        return if (currentEncoding.equals(targetEncoding, ignoreCase = true)) {
            UDM.Scalar(xml)
        } else {
            convertXMLEncoding(listOf(args[0], UDM.Scalar(currentEncoding), UDM.Scalar(targetEncoding)))
        }
    }
    
    // ================================
    // BOM (BYTE ORDER MARK) FUNCTIONS  
    // ================================
    
    @UTLXFunction(
        description = "Detect BOM type from binary data",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "data: Data value",
        "encoding: Encoding value"
        ],
        returns = "Result of the operation",
        example = "detectBOM(data) -> \"UTF-8\", \"UTF-16LE\", \"UTF-16BE\", etc.",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Detect BOM type from binary data
     * Usage: detectBOM(data) -> "UTF-8", "UTF-16LE", "UTF-16BE", etc.
     */
    fun detectBOM(args: List<UDM>): UDM {
        requireArgs(args, 1, "detectBOM")
        val data = args[0].asBinary()?.data ?: throw FunctionArgumentException(
            "detectBOM() requires a binary argument"
        )
        
        val bomType = detectBOMInternal(data)
        return UDM.Scalar(bomType)
    }
    
    @UTLXFunction(
        description = "Add BOM to data for specified encoding",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "data: Data value",
        "encoding: Encoding value"
        ],
        returns = "Result of the operation",
        example = "addBOM(data, \"UTF-8\")",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Add BOM to data for specified encoding
     * Usage: addBOM(data, "UTF-8")
     */
    fun addBOM(args: List<UDM>): UDM {
        requireArgs(args, 2, "addBOM")
        val data = args[0].asBinary()?.data ?: throw FunctionArgumentException(
            "addBOM() first argument must be binary data"
        )
        val encoding = args[1].asString() ?: throw FunctionArgumentException(
            "addBOM() second argument must be a string (encoding)"
        )
        
        val bomBytes = getBOMBytesInternal(encoding)
        return if (bomBytes.isNotEmpty()) {
            UDM.Binary(bomBytes + data)
        } else {
            UDM.Binary(data)
        }
    }
    
    @UTLXFunction(
        description = "Remove BOM if present",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "data: Data value"
        ],
        returns = "Result of the operation",
        example = "removeBOM(data)",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Remove BOM if present
     * Usage: removeBOM(data)
     */
    fun removeBOM(args: List<UDM>): UDM {
        requireArgs(args, 1, "removeBOM")
        val data = args[0].asBinary()?.data ?: throw FunctionArgumentException(
            "removeBOM() requires a binary argument"
        )
        
        val withoutBOM = removeBOMInternal(data)
        return UDM.Binary(withoutBOM)
    }
    
    @UTLXFunction(
        description = "Check if data starts with BOM",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "data: Data value"
        ],
        returns = "Boolean indicating the result",
        example = "hasBOM(data) -> true/false",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Check if data starts with BOM
     * Usage: hasBOM(data) -> true/false
     */
    fun hasBOM(args: List<UDM>): UDM {
        requireArgs(args, 1, "hasBOM")
        val data = args[0].asBinary()?.data ?: throw FunctionArgumentException(
            "hasBOM() requires a binary argument"
        )
        
        val hasBoM = detectBOMInternal(data) != null
        return UDM.Scalar(hasBoM)
    }
    
    @UTLXFunction(
        description = "Get BOM bytes for encoding",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "encoding: Encoding value",
        "targetEncoding: Targetencoding value",
        "addBOMFlag: Addbomflag value"
        ],
        returns = "Result of the operation",
        example = "getBOMBytes(\"UTF-8\") -> [0xEF, 0xBB, 0xBF]",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Get BOM bytes for encoding
     * Usage: getBOMBytes("UTF-8") -> [0xEF, 0xBB, 0xBF]
     */
    fun getBOMBytes(args: List<UDM>): UDM {
        requireArgs(args, 1, "getBOMBytes")
        val encoding = args[0].asString() ?: throw FunctionArgumentException(
            "getBOMBytes() requires a string argument (encoding)"
        )
        
        val bomBytes = getBOMBytesInternal(encoding)
        return UDM.Binary(bomBytes)
    }
    
    @UTLXFunction(
        description = "Remove BOM character from string (U+FEFF)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "text: Text value",
        "targetEncoding: Targetencoding value",
        "addBOMFlag: Addbomflag value"
        ],
        returns = "Result of the operation",
        example = "stripBOM(\"\\uFEFF<?xml version='1.0'?>\") -> \"<?xml version='1.0'?>\"",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Remove BOM character from string (U+FEFF)
     * Usage: stripBOM("\uFEFF<?xml version='1.0'?>") -> "<?xml version='1.0'?>"
     */
    fun stripBOM(args: List<UDM>): UDM {
        requireArgs(args, 1, "stripBOM")
        val text = args[0].asString() ?: throw FunctionArgumentException(
            "stripBOM() requires a string argument"
        )
        
        val result = if (text.startsWith('\uFEFF')) {
            text.substring(1)
        } else {
            text
        }
        
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Convert to target encoding with BOM handling",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "data: Data value",
        "targetEncoding: Targetencoding value",
        "addBOMFlag: Addbomflag value"
        ],
        returns = "Result of the operation",
        example = "normalizeBOM(data, \"UTF-8\", false)",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Convert to target encoding with BOM handling
     * Usage: normalizeBOM(data, "UTF-8", false)
     */
    fun normalizeBOM(args: List<UDM>): UDM {
        requireArgs(args, 3, "normalizeBOM")
        val data = args[0].asBinary()?.data ?: throw FunctionArgumentException(
            "normalizeBOM() first argument must be binary data"
        )
        val targetEncoding = args[1].asString() ?: throw FunctionArgumentException(
            "normalizeBOM() second argument must be a string (target encoding)"
        )
        val addBOMFlag = args[2].asBoolean() ?: throw FunctionArgumentException(
            "normalizeBOM() third argument must be a boolean (add BOM)"
        )
        
        // Remove existing BOM
        val withoutBOM = removeBOMInternal(data)
        
        // Add BOM if requested
        val result = if (addBOMFlag) {
            val bomBytes = getBOMBytesInternal(targetEncoding)
            bomBytes + withoutBOM
        } else {
            withoutBOM
        }
        
        return UDM.Binary(result)
    }
    
    // ================================
    // HELPER FUNCTIONS
    // ================================
    
    private fun updateXMLEncodingInternal(xml: String, encoding: String): String {
        // Pattern to match XML declaration
        val xmlDeclRegex = """(<\?xml[^>]*?)encoding\s*=\s*["'][^"']*["']([^>]*\?>)""".toRegex(RegexOption.IGNORE_CASE)
        
        return if (xmlDeclRegex.containsMatchIn(xml)) {
            // Replace existing encoding
            xmlDeclRegex.replace(xml) { match ->
                "${match.groupValues[1]}encoding=\"$encoding\"${match.groupValues[2]}"
            }
        } else {
            // Add encoding to existing declaration
            val addEncodingRegex = """(<\?xml\s+version\s*=\s*["'][^"']*["'])([^>]*\?>)""".toRegex(RegexOption.IGNORE_CASE)
            if (addEncodingRegex.containsMatchIn(xml)) {
                addEncodingRegex.replace(xml) { match ->
                    "${match.groupValues[1]} encoding=\"$encoding\"${match.groupValues[2]}"
                }
            } else {
                // No XML declaration, add one
                "<?xml version=\"1.0\" encoding=\"$encoding\"?>\n$xml"
            }
        }
    }
    
    private fun detectBOMInternal(data: ByteArray): String? {
        return when {
            // UTF-8 BOM: EF BB BF
            data.size >= 3 && 
            data[0] == 0xEF.toByte() && 
            data[1] == 0xBB.toByte() && 
            data[2] == 0xBF.toByte() -> "UTF-8"
            
            // UTF-32 Big Endian: 00 00 FE FF (check before UTF-16)
            data.size >= 4 && 
            data[0] == 0x00.toByte() && 
            data[1] == 0x00.toByte() &&
            data[2] == 0xFE.toByte() && 
            data[3] == 0xFF.toByte() -> "UTF-32BE"
            
            // UTF-32 Little Endian: FF FE 00 00 (check before UTF-16)  
            data.size >= 4 && 
            data[0] == 0xFF.toByte() && 
            data[1] == 0xFE.toByte() &&
            data[2] == 0x00.toByte() && 
            data[3] == 0x00.toByte() -> "UTF-32LE"
            
            // UTF-16 Big Endian: FE FF
            data.size >= 2 && 
            data[0] == 0xFE.toByte() && 
            data[1] == 0xFF.toByte() -> "UTF-16BE"
            
            // UTF-16 Little Endian: FF FE
            data.size >= 2 && 
            data[0] == 0xFF.toByte() && 
            data[1] == 0xFE.toByte() -> "UTF-16LE"
            
            else -> null
        }
    }
    
    private fun getBOMBytesInternal(encoding: String): ByteArray {
        return when (encoding.uppercase()) {
            "UTF-8" -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            "UTF-16BE" -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
            "UTF-16LE" -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            "UTF-32BE" -> byteArrayOf(0x00.toByte(), 0x00.toByte(), 0xFE.toByte(), 0xFF.toByte())
            "UTF-32LE" -> byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())
            else -> byteArrayOf() // No BOM for other encodings
        }
    }
    
    private fun removeBOMInternal(data: ByteArray): ByteArray {
        val bomType = detectBOMInternal(data)
        return when (bomType) {
            "UTF-8" -> data.drop(3).toByteArray()
            "UTF-16BE", "UTF-16LE" -> data.drop(2).toByteArray()
            "UTF-32BE", "UTF-32LE" -> data.drop(4).toByteArray()
            else -> data // No BOM to remove
        }
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
    }

    private fun getTypeDescription(udm: UDM): String {
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    is String -> "string"
                    is Number -> "number"
                    is Boolean -> "boolean"
                    null -> "null"
                    else -> value.javaClass.simpleName
                }
            }
            is UDM.Array -> "array"
            is UDM.Object -> "object"
            is UDM.Binary -> "binary"
            is UDM.DateTime -> "datetime"
            is UDM.Date -> "date"
            is UDM.LocalDateTime -> "localdatetime"
            is UDM.Time -> "time"
            is UDM.Lambda -> "lambda"
            else -> udm.javaClass.simpleName
        }
    }

    // Extension functions for UDM type checking
    private fun UDM.asString(): String? = (this as? UDM.Scalar)?.value as? String
    private fun UDM.asBoolean(): Boolean? = (this as? UDM.Scalar)?.value as? Boolean
    private fun UDM.asBinary(): UDM.Binary? = this as? UDM.Binary
}