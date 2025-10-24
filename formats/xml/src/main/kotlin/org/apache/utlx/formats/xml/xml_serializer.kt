package org.apache.utlx.formats.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.core.interpreter.RuntimeValue
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
                // Scalar at top level - wrap in element
                writeIndent(writer, depth)
                writer.write("<$elementName>")
                writeEscaped(writer, formatScalarValue(udm.value))
                writer.write("</$elementName>")
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
                val hasTextProperty = udm.properties.containsKey("_text")
                val otherProperties = udm.properties.filterKeys { it != "_text" }
                
                when {
                    udm.properties.isEmpty() -> {
                        // Empty element
                        writer.write("/>")
                        if (prettyPrint) writer.write("\n")
                    }
                    
                    hasTextProperty && otherProperties.isEmpty() -> {
                        // Text-only content
                        writer.write(">")
                        val textValue = udm.properties["_text"]!!
                        when (textValue) {
                            is UDM.Scalar -> writeEscaped(writer, formatScalarValue(textValue.value))
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
                            val textValue = udm.properties["_text"]!!
                            if (textValue is UDM.Scalar) {
                                writeIndent(writer, depth + 1)
                                writeEscaped(writer, formatScalarValue(textValue.value))
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
                writeEscaped(writer, value.value)
                writer.write("</$elementName>")
                if (prettyPrint) writer.write("\n")
            }
            
            is RuntimeValue.NumberValue -> {
                writeIndent(writer, depth)
                writer.write("<$elementName>")
                writer.write(if (value.value % 1.0 == 0.0) value.value.toLong().toString() else value.value.toString())
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
    
    private fun writeEscaped(writer: Writer, text: String) {
        for (c in text) {
            when (c) {
                '<' -> writer.write("&lt;")
                '>' -> writer.write("&gt;")
                '&' -> writer.write("&amp;")
                '\'' -> writer.write("&apos;")
                '"' -> writer.write("&quot;")
                else -> writer.write(c.toString())
            }
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
                val d = value.toDouble()
                if (d == d.toLong().toDouble()) {
                    // It's an integer, format without decimal point
                    d.toLong().toString()
                } else {
                    // It's a decimal, keep as is
                    d.toString()
                }
            }
            else -> value.toString()
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
    fun parse(xml: String): UDM {
        return XML.parse(xml)
    }
}
