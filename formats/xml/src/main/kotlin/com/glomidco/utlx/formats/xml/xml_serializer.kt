package com.glomidco.utlx.formats.xml

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.core.interpreter.RuntimeValue
import com.glomidco.utlx.core.format.ParseOptions
import com.glomidco.utlx.core.format.ExactNumber
import java.io.Writer
import java.io.StringWriter

/**
 * XML Serializer - Converts UDM or RuntimeValue to XML
 * 
 * Mapping:
 * - UDM.Object → XML Element
 * - UDM.Object.attributes → XML Attributes
 * - UDM.Object.name → Element name (or "root" if null)
 * - UDM.Array → Multiple elements with same name
 * - UDM.Scalar → Text content or attribute value
 * 
 * Options:
 * - Pretty printing with indentation
 * - XML declaration
 * - CDATA for text with special characters
 */
class XMLSerializer(
    private val prettyPrint: Boolean = true,
    private val indent: String = "  ",
    private val includeDeclaration: Boolean = true,
    private val outputEncoding: String? = null  // null = use metadata or UTF-8, "NONE" = no encoding
) {
    /**
     * Serialize UDM to XML string
     */
    fun serialize(udm: UDM, rootName: String = "root"): String {
        val writer = StringWriter()
        serialize(udm, writer, rootName)
        return writer.toString()
    }

    /**
     * B20: Serialize UDM to XML bytes using the configured output encoding.
     * Defaults to UTF-8. If outputEncoding is "UTF-16", produces UTF-16 bytes with BOM.
     */
    fun serializeToBytes(udm: UDM, rootName: String = "root"): ByteArray {
        val xmlString = serialize(udm, rootName)
        val charset = resolveOutputCharset(udm)
        return xmlString.toByteArray(charset)
    }

    private fun resolveOutputCharset(udm: UDM): java.nio.charset.Charset {
        val enc = when {
            outputEncoding != null && outputEncoding.uppercase() == "NONE" -> "UTF-8"
            outputEncoding != null -> outputEncoding
            else -> (udm as? UDM.Object)?.getMetadata("xmlEncoding") ?: "UTF-8"
        }
        return try { java.nio.charset.Charset.forName(enc) } catch (_: Exception) { Charsets.UTF_8 }
    }
    
    /**
     * Serialize RuntimeValue to XML string
     */
    fun serialize(value: RuntimeValue, rootName: String = "root"): String {
        val writer = StringWriter()
        serialize(value, writer, rootName)
        return writer.toString()
    }
    
    /**
     * Serialize UDM to Writer
     */
    fun serialize(udm: UDM, writer: Writer, rootName: String = "root") {
        if (includeDeclaration) {
            // Determine encoding: outputEncoding > metadata > UTF-8
            val encoding = when {
                outputEncoding != null && outputEncoding.uppercase() == "NONE" -> null
                outputEncoding != null -> outputEncoding
                else -> (udm as? UDM.Object)?.getMetadata("xmlEncoding") ?: "UTF-8"
            }

            // Write declaration with or without encoding
            if (encoding != null) {
                writer.write("<?xml version=\"1.0\" encoding=\"$encoding\"?>\n")
            } else {
                writer.write("<?xml version=\"1.0\"?>\n")
            }
        }

        // Special case: If UDM is an Object with a single property and no attributes,
        // unwrap it and use that property as the root element
        val actualUdm = if (udm is UDM.Object &&
                             udm.properties.size == 1 &&
                             udm.attributes.isEmpty() &&
                             udm.name == null) {
            // Unwrap: serialize the single property value with the property name as element name
            val (propertyName, propertyValue) = udm.properties.entries.first()
            serializeUDM(propertyValue, writer, 0, propertyName)
            return
        } else {
            udm
        }

        serializeUDM(actualUdm, writer, 0, rootName)
    }
    
    /**
     * Serialize RuntimeValue to Writer
     */
    fun serialize(value: RuntimeValue, writer: Writer, rootName: String = "root") {
        if (includeDeclaration) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        }

        // Special case: If RuntimeValue is an ObjectValue with a single property,
        // unwrap it and use that property as the root element
        if (value is RuntimeValue.ObjectValue &&
            value.properties.size == 1) {
            val (propertyName, propertyValue) = value.properties.entries.first()
            serializeRuntimeValue(propertyValue, writer, 0, propertyName)
            return
        }

        serializeRuntimeValue(value, writer, 0, rootName)
    }
    
    private fun serializeUDM(udm: UDM, writer: Writer, depth: Int, elementName: String) {
        when (udm) {
            is UDM.Scalar -> {
                writeIndent(writer, depth)
                if (udm.value == null) {
                    writer.write("<$elementName/>")
                } else {
                    writer.write("<$elementName>")
                    writeEscapedText(writer, formatScalarValue(udm.value))
                    writer.write("</$elementName>")
                }
                if (prettyPrint) writer.write("\n")
            }
            
            is UDM.Array -> {
                // Array - Check if this is an array of single-property objects (unwrap pattern)
                // Example: Shipments: [{Shipment: {...}}, {Shipment: {...}}]
                // Should output: <Shipments><Shipment>..</Shipment><Shipment>..</Shipment></Shipments>
                val isUnwrapPattern = udm.elements.all { element ->
                    element is UDM.Object &&
                    element.properties.size == 1 &&
                    element.attributes.isEmpty() &&
                    element.name == null
                }

                if (isUnwrapPattern && udm.elements.isNotEmpty()) {
                    // Unwrap: output parent element, then each child with their own names
                    writeIndent(writer, depth)
                    writer.write("<$elementName>")
                    if (prettyPrint) writer.write("\n")

                    udm.elements.forEach { element ->
                        val obj = element as UDM.Object
                        val (childName, childValue) = obj.properties.entries.first()
                        serializeUDM(childValue, writer, depth + 1, childName)
                    }

                    writeIndent(writer, depth)
                    writer.write("</$elementName>")
                    if (prettyPrint) writer.write("\n")
                } else {
                    // Standard array handling - serialize each element with same name
                    udm.elements.forEach { element ->
                        serializeUDM(element, writer, depth, elementName)
                    }
                }
            }
            
            is UDM.Object -> {
                // F09: normalize #text to _text (accept both as text content marker)
                val properties = if (udm.properties.containsKey("#text") && !udm.properties.containsKey("_text")) {
                    udm.properties.mapKeys { (k, _) -> if (k == "#text") "_text" else k }
                } else {
                    udm.properties
                }

                val name = udm.name ?: elementName
                writeIndent(writer, depth)
                writer.write("<$name")

                // Write attributes
                udm.attributes.forEach { (key, value) ->
                    writer.write(" $key=\"")
                    writeEscaped(writer, value)
                    writer.write("\"")
                }

                // Check if element has content
                val hasTextProperty = properties.containsKey("_text")
                val otherProperties = properties.filterKeys { it != "_text" }
                
                when {
                    udm.properties.isEmpty() -> {
                        // Empty element
                        writer.write("/>")
                        if (prettyPrint) writer.write("\n")
                    }
                    
                    hasTextProperty && otherProperties.isEmpty() -> {
                        // Text-only content
                        writer.write(">")
                        val textValue = properties["_text"]!!
                        when (textValue) {
                            is UDM.Scalar -> writeEscapedText(writer, formatScalarValue(textValue.value))
                            else -> serializeUDM(textValue, writer, depth + 1, "value")
                        }
                        writer.write("</$name>")
                        if (prettyPrint) writer.write("\n")
                    }
                    
                    else -> {
                        // Child elements
                        writer.write(">")
                        if (prettyPrint) writer.write("\n")
                        
                        // Serialize text first if present
                        if (hasTextProperty) {
                            val textValue = properties["_text"]!!
                            if (textValue is UDM.Scalar) {
                                writeIndent(writer, depth + 1)
                                writeEscapedText(writer, formatScalarValue(textValue.value))
                                if (prettyPrint) writer.write("\n")
                            }
                        }
                        
                        // Serialize child elements
                        otherProperties.forEach { (childName, childValue) ->
                            serializeUDM(childValue, writer, depth + 1, childName)
                        }
                        
                        writeIndent(writer, depth)
                        writer.write("</$name>")
                        if (prettyPrint) writer.write("\n")
                    }
                }
            }
            
            is UDM.DateTime -> {
                writeIndent(writer, depth)
                writer.write("<$elementName>")
                writer.write(udm.toISOString())  // "2020-03-15T10:30:00Z"
                writer.write("</$elementName>")
                if (prettyPrint) writer.write("\n")
            }

            is UDM.Date -> {
                writeIndent(writer, depth)
                writer.write("<$elementName>")
                writer.write(udm.toISOString())  // "2020-03-15"
                writer.write("</$elementName>")
                if (prettyPrint) writer.write("\n")
            }

            is UDM.LocalDateTime -> {
                writeIndent(writer, depth)
                writer.write("<$elementName>")
                writer.write(udm.toISOString())  // "2020-03-15T10:30:00"
                writer.write("</$elementName>")
                if (prettyPrint) writer.write("\n")
            }

            is UDM.Time -> {
                writeIndent(writer, depth)
                writer.write("<$elementName>")
                writer.write(udm.toISOString())  // "14:30:00"
                writer.write("</$elementName>")
                if (prettyPrint) writer.write("\n")
            }

            is UDM.Binary -> {
                writeIndent(writer, depth)
                writer.write("<$elementName>")
                writer.write("<binary:${udm.data.size} bytes>")
                writer.write("</$elementName>")
                if (prettyPrint) writer.write("\n")
            }
            
            is UDM.Lambda -> {
                writeIndent(writer, depth)
                writer.write("<$elementName>")
                writer.write("<function>")
                writer.write("</$elementName>")
                if (prettyPrint) writer.write("\n")
            }
        }
    }
    
    private fun serializeRuntimeValue(value: RuntimeValue, writer: Writer, depth: Int, elementName: String) {
        when (value) {
            is RuntimeValue.StringValue -> {
                writeIndent(writer, depth)
                writer.write("<$elementName>")
                writeEscapedText(writer, value.value)
                writer.write("</$elementName>")
                if (prettyPrint) writer.write("\n")
            }
            
            is RuntimeValue.NumberValue -> {
                writeIndent(writer, depth)
                writer.write("<$elementName>")
                // Was a second, divergent renderer carrying the `% 1.0 == 0.0` predicate B37
                // replaced — so a large double saturated to Long.MAX_VALUE here while the UDM path
                // rendered it correctly, and non-finite values skipped the XSD form above. B37's
                // note said the predicate was "now shared"; these overloads were missed. One
                // renderer, as CSV already does.
                writer.write(formatScalarValue(value.value))
                writer.write("</$elementName>")
                if (prettyPrint) writer.write("\n")
            }
            
            is RuntimeValue.BooleanValue -> {
                writeIndent(writer, depth)
                writer.write("<$elementName>")
                writer.write(value.value.toString())
                writer.write("</$elementName>")
                if (prettyPrint) writer.write("\n")
            }
            
            is RuntimeValue.NullValue -> {
                writeIndent(writer, depth)
                writer.write("<$elementName/>")
                if (prettyPrint) writer.write("\n")
            }
            
            is RuntimeValue.ArrayValue -> {
                value.elements.forEach { element ->
                    serializeRuntimeValue(element, writer, depth, elementName)
                }
            }
            
            is RuntimeValue.ObjectValue -> {
                writeIndent(writer, depth)
                writer.write("<$elementName>")
                if (prettyPrint) writer.write("\n")
                
                value.properties.forEach { (childName, childValue) ->
                    serializeRuntimeValue(childValue, writer, depth + 1, childName)
                }
                
                writeIndent(writer, depth)
                writer.write("</$elementName>")
                if (prettyPrint) writer.write("\n")
            }
            
            is RuntimeValue.FunctionValue -> {
                writeIndent(writer, depth)
                writer.write("<$elementName>&lt;function&gt;</$elementName>")
                if (prettyPrint) writer.write("\n")
            }
            
            is RuntimeValue.UDMValue -> {
                serializeUDM(value.udm, writer, depth, elementName)
            }
        }
    }
    
    private fun writeIndent(writer: Writer, depth: Int) {
        if (prettyPrint && depth > 0) {
            writer.write(indent.repeat(depth))
        }
    }
    
    /** Attribute values and anywhere the surrounding whitespace is not significant. */
    private fun writeEscaped(writer: Writer, text: String) {
        for (c in text) writeEscapedChar(writer, c)
    }

    private fun writeEscapedChar(writer: Writer, c: Char) {
        when (c) {
            '<' -> writer.write("&lt;")
            '>' -> writer.write("&gt;")
            '&' -> writer.write("&amp;")
            '\'' -> writer.write("&apos;")
            '"' -> writer.write("&quot;")
            else -> writer.write(c.toString())
        }
    }

    /**
     * Element **text**, escaped so that re-reading the document gives the same value back.
     *
     * B43: as well as the markup characters, whitespace at the **start or end** of the run is
     * written as a character reference. A parser trims literal whitespace there — this one included
     * — so emitting it literally drops it silently on the next read: the value survives in memory
     * but not through XML. [B41](../../../../../../../../docs/bugs-fixed) made the reading side keep
     * escaped whitespace; this is the writing side of the same rule, and without both the round trip
     * is lossy.
     *
     * Interior whitespace is left alone, because trimming only reaches the edges. Surrogate pairs
     * are safe: a character reference is only ever emitted for whitespace, which is always BMP.
     */
    private fun writeEscapedText(writer: Writer, text: String) {
        val lead = text.indexOfFirst { !it.isWhitespace() }
        if (lead < 0) {
            // All whitespace — every character is an edge, including the empty case (writes nothing).
            for (c in text) writer.write("&#${c.code};")
            return
        }
        val trail = text.indexOfLast { !it.isWhitespace() }
        for ((i, c) in text.withIndex()) {
            if (i < lead || i > trail) writer.write("&#${c.code};") else writeEscapedChar(writer, c)
        }
    }

    /**
     * Format scalar value for XML output
     * - Numbers: Format integers without decimal point (1 instead of 1.0)
     * - Others: Use default toString()
     */
    private fun formatScalarValue(value: Any?): String {
        return when (value) {
            null -> ""
            is Number -> {
                // B38 (XML half): XSD 1.0 §3.2.5 fixes the lexical space of `xs:double` — the
                // special values are written INF, -INF and NaN (XSD 1.1 also allows +INF).
                // `Infinity`, which is what Double.toString() produces, is NOT in that lexical
                // space, so `<x>Infinity</x>` fails validation against an xs:double element. This
                // half needs no policy decision, unlike the JSON half, which has no representation
                // to be correct about and is still open.
                xsdNonFiniteOrNull(value)
                    // B35: exact types render as written. Narrowing every Number through toDouble()
                    // just to test for integrality corrupted Long and BigDecimal on the way out.
                    ?: ExactNumber.renderExactOrNull(value)
                    ?: run {
                        // B37: this predicate was already the correct one — a Long round-trip — and
                        // is now shared, so the other serialisers cannot drift away from it again.
                        val d = value.toDouble()
                        ExactNumber.integralLongOrNull(d)?.toString() ?: d.toString()
                    }
            }
            else -> value.toString()
        }
    }

    /** `INF` / `-INF` / `NaN` per XSD, or null for every finite number and every exact type. */
    private fun xsdNonFiniteOrNull(value: Number): String? {
        val d = when (value) {
            is Double -> value
            is Float -> value.toDouble()
            else -> return null   // Long, BigInteger and BigDecimal cannot be non-finite
        }
        return when {
            d.isNaN() -> "NaN"
            d == Double.POSITIVE_INFINITY -> "INF"
            d == Double.NEGATIVE_INFINITY -> "-INF"
            else -> null
        }
    }
}

/**
 * Convenience extension for XML serialization
 */
object XMLFormat {
    /**
     * Serialize to pretty-printed XML
     */
    fun stringify(udm: UDM, rootName: String = "root"): String {
        return XMLSerializer(prettyPrint = true).serialize(udm, rootName)
    }
    
    fun stringify(value: RuntimeValue, rootName: String = "root"): String {
        return XMLSerializer(prettyPrint = true).serialize(value, rootName)
    }
    
    /**
     * Serialize to compact XML (minimal whitespace)
     */
    fun stringifyCompact(udm: UDM, rootName: String = "root"): String {
        return XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(udm, rootName)
    }
    
    fun stringifyCompact(value: RuntimeValue, rootName: String = "root"): String {
        return XMLSerializer(prettyPrint = false, includeDeclaration = false).serialize(value, rootName)
    }
    
    /**
     * Parse XML string to UDM
     */
    fun parse(xml: String, options: ParseOptions = ParseOptions.standard()): UDM {
        return XML.parse(xml, options = options)
    }
}
