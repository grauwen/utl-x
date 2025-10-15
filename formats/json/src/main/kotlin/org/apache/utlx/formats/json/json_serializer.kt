package org.apache.utlx.formats.json

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.core.interpreter.RuntimeValue
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
    private val indent: String = "  "
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
            is UDM.DateTime -> writer.write("\"${udm.toISOString()}\"")
            is UDM.Binary -> writer.write("\"<binary:${udm.data.size} bytes>\"")
            is UDM.Lambda -> writer.write("\"<function>\"")
        }
    }
    
    private fun serializeScalar(scalar: UDM.Scalar, writer: Writer) {
        when (val value = scalar.value) {
            null -> writer.write("null")
            is Boolean -> writer.write(value.toString())
            is Number -> {
                // Format numbers nicely
                if (value is Double && value % 1.0 == 0.0 && !value.isInfinite() && !value.isNaN()) {
                    writer.write(value.toLong().toString())
                } else {
                    writer.write(value.toString())
                }
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
        // Combine properties and attributes (attributes with @ prefix)
        val allProperties = mutableMapOf<String, UDM>()
        
        // Add attributes with @ prefix
        obj.attributes.forEach { (key, value) ->
            allProperties["@$key"] = UDM.Scalar.string(value)
        }
        
        // Add regular properties
        allProperties.putAll(obj.properties)
        
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
    
    private fun serializeRuntimeValue(value: RuntimeValue, writer: Writer, depth: Int) {
        when (value) {
            is RuntimeValue.StringValue -> writer.write(escapeString(value.value))
            is RuntimeValue.NumberValue -> {
                if (value.value % 1.0 == 0.0 && !value.value.isInfinite() && !value.value.isNaN()) {
                    writer.write(value.value.toLong().toString())
                } else {
                    writer.write(value.value.toString())
                }
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
    fun parse(json: String): UDM {
        return JSONParser(json).parse()
    }
}
