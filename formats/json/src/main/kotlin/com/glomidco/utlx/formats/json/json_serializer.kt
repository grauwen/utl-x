package com.glomidco.utlx.formats.json

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.core.interpreter.RuntimeValue
import com.glomidco.utlx.core.format.ParseOptions
import com.glomidco.utlx.core.format.ExactNumber
import java.io.Writer
import java.io.StringWriter

/**
 * JSON Serializer - Converts UDM or RuntimeValue to JSON strings
 * 
 * Supports:
 * - Pretty printing with indentation
 * - Compact output (single line)
 * - Streaming to Writer
 * - Proper escaping of strings
 */
class JSONSerializer(
    private val prettyPrint: Boolean = true,
    private val indent: String = "  ",
    private val writeAttributes: Boolean = false  // When true, leaf text+attribute elements preserve attributes (DataWeave-compatible)
) {
    /**
     * Serialize UDM to JSON string
     */
    fun serialize(udm: UDM): String {
        val writer = StringWriter()
        serialize(udm, writer)
        return writer.toString()
    }

    /**
     * B20: Serialize UDM to JSON bytes (always UTF-8 per RFC 8259).
     */
    fun serializeToBytes(udm: UDM): ByteArray = serialize(udm).toByteArray(Charsets.UTF_8)
    
    /**
     * Serialize RuntimeValue to JSON string
     */
    fun serialize(value: RuntimeValue): String {
        val writer = StringWriter()
        serialize(value, writer)
        return writer.toString()
    }
    
    /**
     * Serialize UDM to Writer
     */
    fun serialize(udm: UDM, writer: Writer) {
        serializeUDM(udm, writer, 0)
    }
    
    /**
     * Serialize RuntimeValue to Writer
     */
    fun serialize(value: RuntimeValue, writer: Writer) {
        serializeRuntimeValue(value, writer, 0)
    }
    
    private fun serializeUDM(udm: UDM, writer: Writer, depth: Int) {
        when (udm) {
            is UDM.Scalar -> serializeScalar(udm, writer)
            is UDM.Array -> serializeArray(udm, writer, depth)
            is UDM.Object -> serializeObject(udm, writer, depth)
            is UDM.DateTime -> writer.write("\"${udm.toISOString()}\"")  // "2020-03-15T10:30:00Z"
            is UDM.Date -> writer.write("\"${udm.toISOString()}\"")      // "2020-03-15"
            is UDM.LocalDateTime -> writer.write("\"${udm.toISOString()}\"")  // "2020-03-15T10:30:00"
            is UDM.Time -> writer.write("\"${udm.toISOString()}\"")      // "14:30:00"
            is UDM.Binary -> writer.write("\"<binary:${udm.data.size} bytes>\"")
            is UDM.Lambda -> writer.write("\"<function>\"")
        }
    }
    
    private fun serializeScalar(scalar: UDM.Scalar, writer: Writer) {
        when (val value = scalar.value) {
            null -> writer.write("null")
            is Boolean -> writer.write(value.toString())
            is Number -> {
                // B38: JSON has no representation for Infinity, -Infinity or NaN. Emitting the
                // bare tokens — Python's documented extension, and what this did — produces
                // output that a strict parser rejects, *after* the transformation reported
                // success. The failure then surfaces at the consumer, which is the most
                // expensive place for it to surface.
                //
                // Rejecting is the only option that does not lie. `null` loses which of the
                // three values it was (and is indistinguishable from a real null), and a string
                // changes the field's type and breaks schema-validated consumers. Both remain
                // reasonable as *opt-in* behaviour once the serialiser takes options; neither is
                // safe as a default for an engine whose job is to hand correct data to the next
                // hop. This matches `jcs()`, which already throws for these values per RFC 8785
                // §3.2.2, and division by zero, which already errors.
                rejectNonFinite(value)
                // B35/B37: exact types render as written, integral doubles print as integers
                // only when they round-trip through Long. B51 moved that sequence into
                // ExactNumber.renderScalarText so the stdlib's toString() shares it rather
                // than re-deriving it — three call sites did, and only this one was right.
                writer.write(ExactNumber.renderScalarText(value))
            }
            is String -> writer.write(escapeString(value))
            else -> writer.write(escapeString(value.toString()))
        }
    }
    
    private fun serializeArray(array: UDM.Array, writer: Writer, depth: Int) {
        if (array.isEmpty()) {
            writer.write("[]")
            return
        }
        
        writer.write("[")
        
        if (prettyPrint) {
            writer.write("\n")
            array.elements.forEachIndexed { index, element ->
                writer.write(indent.repeat(depth + 1))
                serializeUDM(element, writer, depth + 1)
                if (index < array.elements.size - 1) {
                    writer.write(",")
                }
                writer.write("\n")
            }
            writer.write(indent.repeat(depth))
        } else {
            array.elements.forEachIndexed { index, element ->
                serializeUDM(element, writer, depth + 1)
                if (index < array.elements.size - 1) {
                    writer.write(",")
                }
            }
        }
        
        writer.write("]")
    }
    
    private fun serializeObject(obj: UDM.Object, writer: Writer, depth: Int) {
        // Unwrap XML text nodes: if object has only _text (and optionally _attributes),
        // serialize the text value directly instead of the wrapper object.
        // This ensures <Customer>Alice</Customer> outputs as "Alice" not {"_text":"Alice"}
        if (isXmlTextNode(obj)) {
            serializeUDM(obj.properties["_text"]!!, writer, depth)
            return
        }

        // Combine properties and attributes (attributes with @ prefix)
        val allProperties = mutableMapOf<String, UDM>()
        
        // Add attributes with @ prefix
        obj.attributes.forEach { (key, value) ->
            allProperties["@$key"] = UDM.Scalar.string(value)
        }
        
        // Add regular properties (rename internal _text to #text for output)
        obj.properties.forEach { (key, value) ->
            allProperties[if (key == "_text") "#text" else key] = value
        }
        
        if (allProperties.isEmpty()) {
            writer.write("{}")
            return
        }
        
        writer.write("{")
        
        if (prettyPrint) {
            writer.write("\n")
            val entries = allProperties.entries.toList()
            entries.forEachIndexed { index, (key, value) ->
                writer.write(indent.repeat(depth + 1))
                writer.write(escapeString(key))
                writer.write(": ")
                serializeUDM(value, writer, depth + 1)
                if (index < entries.size - 1) {
                    writer.write(",")
                }
                writer.write("\n")
            }
            writer.write(indent.repeat(depth))
        } else {
            val entries = allProperties.entries.toList()
            entries.forEachIndexed { index, (key, value) ->
                writer.write(escapeString(key))
                writer.write(":")
                serializeUDM(value, writer, depth + 1)
                if (index < entries.size - 1) {
                    writer.write(",")
                }
            }
        }
        
        writer.write("}")
    }
    
    /**
     * Check if a UDM.Object is an XML text node wrapper that should be unwrapped during serialization.
     * Returns true for objects that have _text as their only real property (ignoring xmlns attributes).
     */
    private fun isXmlTextNode(obj: UDM.Object): Boolean {
        if (!obj.properties.containsKey("_text")) return false
        // All properties must be _text only (no child elements)
        if (!obj.properties.keys.all { it == "_text" }) return false
        // When writeAttributes is enabled, don't unwrap if element has real attributes —
        // they need to be output as @key alongside #text
        if (writeAttributes) {
            val hasRealAttributes = obj.attributes.any { (key, _) ->
                !key.startsWith("xmlns") && key != "xmlns"
            }
            if (hasRealAttributes) return false
        }
        return true
    }

    private fun serializeRuntimeValue(value: RuntimeValue, writer: Writer, depth: Int) {
        when (value) {
            is RuntimeValue.StringValue -> writer.write(escapeString(value.value))
            is RuntimeValue.NumberValue -> {
                // Was a second, divergent renderer: it guarded non-finite values but kept the
                // `% 1.0 == 0.0` predicate B37 replaced, so a large double saturated to
                // Long.MAX_VALUE here while the UDM path rendered it correctly. One renderer.
                serializeScalar(UDM.Scalar(value.value), writer)
            }
            is RuntimeValue.BooleanValue -> writer.write(value.value.toString())
            is RuntimeValue.NullValue -> writer.write("null")
            is RuntimeValue.ArrayValue -> serializeRuntimeArray(value, writer, depth)
            is RuntimeValue.ObjectValue -> serializeRuntimeObject(value, writer, depth)
            is RuntimeValue.FunctionValue -> writer.write("\"<function>\"")
            is RuntimeValue.UDMValue -> serializeUDM(value.udm, writer, depth)
        }
    }
    
    private fun serializeRuntimeArray(array: RuntimeValue.ArrayValue, writer: Writer, depth: Int) {
        if (array.elements.isEmpty()) {
            writer.write("[]")
            return
        }
        
        writer.write("[")
        
        if (prettyPrint) {
            writer.write("\n")
            array.elements.forEachIndexed { index, element ->
                writer.write(indent.repeat(depth + 1))
                serializeRuntimeValue(element, writer, depth + 1)
                if (index < array.elements.size - 1) {
                    writer.write(",")
                }
                writer.write("\n")
            }
            writer.write(indent.repeat(depth))
        } else {
            array.elements.forEachIndexed { index, element ->
                serializeRuntimeValue(element, writer, depth + 1)
                if (index < array.elements.size - 1) {
                    writer.write(",")
                }
            }
        }
        
        writer.write("]")
    }
    
    private fun serializeRuntimeObject(obj: RuntimeValue.ObjectValue, writer: Writer, depth: Int) {
        if (obj.properties.isEmpty()) {
            writer.write("{}")
            return
        }
        
        writer.write("{")
        
        if (prettyPrint) {
            writer.write("\n")
            val entries = obj.properties.entries.toList()
            entries.forEachIndexed { index, (key, value) ->
                writer.write(indent.repeat(depth + 1))
                writer.write(escapeString(key))
                writer.write(": ")
                serializeRuntimeValue(value, writer, depth + 1)
                if (index < entries.size - 1) {
                    writer.write(",")
                }
                writer.write("\n")
            }
            writer.write(indent.repeat(depth))
        } else {
            val entries = obj.properties.entries.toList()
            entries.forEachIndexed { index, (key, value) ->
                writer.write(escapeString(key))
                writer.write(":")
                serializeRuntimeValue(value, writer, depth + 1)
                if (index < entries.size - 1) {
                    writer.write(",")
                }
            }
        }
        
        writer.write("}")
    }
    
    /**
     * Escape string for JSON
     */
    /**
     * B38: refuse to emit a value JSON cannot represent.
     *
     * Only `Double` and `Float` can be non-finite; every exact type (`BigDecimal`, `Long`, …)
     * is finite by construction, so this costs two type checks on the common path.
     *
     * The message names the value and the field-free fact that JSON has no such literal, because
     * the author's next question is always "what should I write instead?" — and the answer
     * depends on their consumer, which is why this is a rejection rather than a silent choice.
     */
    private fun rejectNonFinite(value: Number) {
        val nonFinite = when (value) {
            is Double -> !value.isFinite()
            is Float -> !value.isFinite()
            else -> false
        }
        if (nonFinite) {
            // The error necessarily fires at serialisation, which is far from the arithmetic
            // that produced the value — so the message carries the likely causes rather than
            // leaving the author to find them.
            throw IllegalArgumentException(
                "Cannot serialise $value to JSON: JSON has no literal for Infinity, -Infinity " +
                "or NaN (RFC 8259), so emitting it would produce output a strict parser " +
                "rejects. This value was computed, not read from the input — the usual causes " +
                "are overflow (1.0e308 * 10), a root of a negative (sqrt(-1)) or a large power " +
                "(pow(10, 400)). " +
                "Hint: guard it with isFinite(x), e.g. `if (isFinite(x)) x else null`; or emit " +
                "it as text with toString(x), which yields \"$value\"."
            )
        }
    }

    private fun escapeString(str: String): String {
        val sb = StringBuilder()
        sb.append('"')
        
        for (c in str) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c < ' ') {
                        // Escape control characters
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        
        sb.append('"')
        return sb.toString()
    }
}

/**
 * Convenience functions for JSON serialization
 */
object JSON {
    /**
     * Serialize to pretty-printed JSON
     */
    fun stringify(udm: UDM): String {
        return JSONSerializer(prettyPrint = true).serialize(udm)
    }
    
    fun stringify(value: RuntimeValue): String {
        return JSONSerializer(prettyPrint = true).serialize(value)
    }
    
    /**
     * Serialize to compact JSON (no whitespace)
     */
    fun stringifyCompact(udm: UDM): String {
        return JSONSerializer(prettyPrint = false).serialize(udm)
    }
    
    fun stringifyCompact(value: RuntimeValue): String {
        return JSONSerializer(prettyPrint = false).serialize(value)
    }
    
    /**
     * Parse JSON string to UDM
     */
    fun parse(json: String, options: ParseOptions = ParseOptions.standard()): UDM {
        return JSONParser(json, options).parse()
    }
}
