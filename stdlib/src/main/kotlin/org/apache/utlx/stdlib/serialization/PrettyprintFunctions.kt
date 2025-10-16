/**
 * Pretty-Print Functions for UTL-X Standard Library
 * 
 * Location: stdlib/src/main/kotlin/org/apache/utlx/stdlib/serialization/PrettyPrintFunctions.kt
 * 
 * Provides formatting/pretty-printing functions for various data formats.
 * Used for:
 * - Reformatting existing serialized strings
 * - Debug output
 * - Log-friendly formatting
 * - Human-readable representations
 * 
 * Note: These are different from serialization options - these operate on
 * already-serialized strings or UDM objects.
 */

package org.apache.utlx.stdlib.serialization

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

/**
 * Pretty-Print Functions for various data formats
 * 
 * Provides formatting/pretty-printing functions for JSON, XML, YAML, CSV and debug output.
 * All functions follow UDM compliance pattern for stdlib registration.
 */
object PrettyPrintFunctions {

    /**
     * Pretty-prints a JSON string with optional indentation
     * 
     * @param args List containing: [jsonString, indent?]
     * @return UDM Scalar with pretty-printed JSON string
     * 
     * Example:
     * ```
     * prettyPrintJSON("{\"name\":\"Alice\",\"age\":30}", 2)
     * // Returns: UDM.Scalar with formatted JSON
     * ```
     */
    fun prettyPrintJSON(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 2) {
            throw FunctionArgumentException("prettyPrintJSON expects 1 or 2 arguments (jsonString, indent?), got ${args.size}")
        }
        
        val jsonString = args[0].asString()
        val indent = if (args.size > 1) {
            when (val indentUDM = args[1]) {
                is UDM.Scalar -> (indentUDM.value as? Number)?.toInt() ?: 2
                else -> 2
            }
        } else 2
        
        return try {
            val result = prettyPrintJSONInternal(jsonString, indent)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid JSON string: ${e.message}")
        }
    }

    /**
     * Pretty-prints a UDM object as JSON
     * 
     * @param args List containing: [udm, indent?]
     * @return UDM Scalar with pretty-printed JSON string
     */
    fun udmToJSON(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 2) {
            throw FunctionArgumentException("udmToJSON expects 1 or 2 arguments (udm, indent?), got ${args.size}")
        }
        
        val udm = args[0]
        val indent = if (args.size > 1) {
            when (val indentUDM = args[1]) {
                is UDM.Scalar -> (indentUDM.value as? Number)?.toInt() ?: 2
                else -> 2
            }
        } else 2
        
        return try {
            val result = udmToJSONInternal(udm, indent)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to serialize UDM to JSON: ${e.message}")
        }
    }

    /**
     * Compacts a JSON string (removes all unnecessary whitespace)
     * 
     * @param args List containing: [jsonString]
     * @return UDM Scalar with compacted JSON string
     */
    fun compactJSON(args: List<UDM>): UDM {
        requireArgs(args, 1, "compactJSON")
        val jsonString = args[0].asString()
        
        return try {
            val result = compactJSONInternal(jsonString)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid JSON string: ${e.message}")
        }
    }

    /**
     * Pretty-prints an XML string with optional formatting options
     * 
     * @param args List containing: [xmlString, indent?, preserveWhitespace?]
     * @return UDM Scalar with pretty-printed XML string
     */
    fun prettyPrintXML(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException("prettyPrintXML expects 1-3 arguments (xmlString, indent?, preserveWhitespace?), got ${args.size}")
        }
        
        val xmlString = args[0].asString()
        val indent = if (args.size > 1) {
            when (val indentUDM = args[1]) {
                is UDM.Scalar -> (indentUDM.value as? Number)?.toInt() ?: 2
                else -> 2
            }
        } else 2
        val preserveWhitespace = if (args.size > 2) {
            when (val preserveUDM = args[2]) {
                is UDM.Scalar -> preserveUDM.value as? Boolean ?: false
                else -> false
            }
        } else false
        
        return try {
            val result = prettyPrintXMLInternal(xmlString, indent, preserveWhitespace)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid XML string: ${e.message}")
        }
    }

    /**
     * Pretty-prints a UDM object as XML
     * 
     * @param args List containing: [udm, indent?, preserveWhitespace?]
     * @return UDM Scalar with pretty-printed XML string
     */
    fun udmToXML(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException("udmToXML expects 1-3 arguments (udm, indent?, preserveWhitespace?), got ${args.size}")
        }
        
        val udm = args[0]
        val indent = if (args.size > 1) {
            when (val indentUDM = args[1]) {
                is UDM.Scalar -> (indentUDM.value as? Number)?.toInt() ?: 2
                else -> 2
            }
        } else 2
        val preserveWhitespace = if (args.size > 2) {
            when (val preserveUDM = args[2]) {
                is UDM.Scalar -> preserveUDM.value as? Boolean ?: false
                else -> false
            }
        } else false
        
        return try {
            val result = udmToXMLInternal(udm, indent, preserveWhitespace)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to serialize UDM to XML: ${e.message}")
        }
    }

    /**
     * Compacts an XML string (removes unnecessary whitespace)
     * 
     * @param args List containing: [xmlString]
     * @return UDM Scalar with compacted XML string
     */
    fun compactXML(args: List<UDM>): UDM {
        requireArgs(args, 1, "compactXML")
        val xmlString = args[0].asString()
        
        return try {
            val result = compactXMLInternal(xmlString)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid XML string: ${e.message}")
        }
    }

    /**
     * Pretty-prints a YAML string with optional formatting options
     * 
     * @param args List containing: [yamlString, indent?, flowStyle?]
     * @return UDM Scalar with pretty-printed YAML string
     */
    fun prettyPrintYAML(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException("prettyPrintYAML expects 1-3 arguments (yamlString, indent?, flowStyle?), got ${args.size}")
        }
        
        val yamlString = args[0].asString()
        val indent = if (args.size > 1) {
            when (val indentUDM = args[1]) {
                is UDM.Scalar -> (indentUDM.value as? Number)?.toInt() ?: 2
                else -> 2
            }
        } else 2
        val flowStyle = if (args.size > 2) {
            when (val flowUDM = args[2]) {
                is UDM.Scalar -> flowUDM.value as? Boolean ?: false
                else -> false
            }
        } else false
        
        return try {
            val result = prettyPrintYAMLInternal(yamlString, indent, flowStyle)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid YAML string: ${e.message}")
        }
    }

    /**
     * Pretty-prints a UDM object as YAML
     * 
     * @param args List containing: [udm, indent?, flowStyle?]
     * @return UDM Scalar with pretty-printed YAML string
     */
    fun udmToYAML(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException("udmToYAML expects 1-3 arguments (udm, indent?, flowStyle?), got ${args.size}")
        }
        
        val udm = args[0]
        val indent = if (args.size > 1) {
            when (val indentUDM = args[1]) {
                is UDM.Scalar -> (indentUDM.value as? Number)?.toInt() ?: 2
                else -> 2
            }
        } else 2
        val flowStyle = if (args.size > 2) {
            when (val flowUDM = args[2]) {
                is UDM.Scalar -> flowUDM.value as? Boolean ?: false
                else -> false
            }
        } else false
        
        return try {
            val result = udmToYAMLInternal(udm, indent, flowStyle)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to serialize UDM to YAML: ${e.message}")
        }
    }

    /**
     * Formats a CSV string with aligned columns
     * 
     * @param args List containing: [csvString, delimiter?, alignColumns?]
     * @return UDM Scalar with formatted CSV string
     */
    fun prettyPrintCSV(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException("prettyPrintCSV expects 1-3 arguments (csvString, delimiter?, alignColumns?), got ${args.size}")
        }
        
        val csvString = args[0].asString()
        val delimiter = if (args.size > 1) {
            when (val delimiterUDM = args[1]) {
                is UDM.Scalar -> delimiterUDM.value?.toString() ?: ","
                else -> ","
            }
        } else ","
        val alignColumns = if (args.size > 2) {
            when (val alignUDM = args[2]) {
                is UDM.Scalar -> alignUDM.value as? Boolean ?: true
                else -> true
            }
        } else true
        
        return try {
            val result = prettyPrintCSVInternal(csvString, delimiter, alignColumns)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Invalid CSV string: ${e.message}")
        }
    }

    /**
     * Compacts a CSV string (removes extra whitespace)
     * 
     * @param args List containing: [csvString, delimiter?]
     * @return UDM Scalar with compacted CSV string
     */
    fun compactCSV(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 2) {
            throw FunctionArgumentException("compactCSV expects 1 or 2 arguments (csvString, delimiter?), got ${args.size}")
        }
        
        val csvString = args[0].asString()
        val delimiter = if (args.size > 1) {
            when (val delimiterUDM = args[1]) {
                is UDM.Scalar -> delimiterUDM.value?.toString() ?: ","
                else -> ","
            }
        } else ","
        
        return try {
            val result = csvString.split("\n")
                .map { line ->
                    line.split(delimiter)
                        .joinToString(delimiter) { it.trim() }
                }
                .joinToString("\n")
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to compact CSV: ${e.message}")
        }
    }

    /**
     * Automatically detects format and pretty-prints
     * 
     * @param args List containing: [data, indent?]
     * @return UDM Scalar with pretty-printed string
     */
    fun prettyPrint(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 2) {
            throw FunctionArgumentException("prettyPrint expects 1 or 2 arguments (data, indent?), got ${args.size}")
        }
        
        val data = args[0].asString()
        val indent = if (args.size > 1) {
            when (val indentUDM = args[1]) {
                is UDM.Scalar -> (indentUDM.value as? Number)?.toInt() ?: 2
                else -> 2
            }
        } else 2
        
        return try {
            val result = prettyPrintAutoDetect(data, indent)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to pretty-print data: ${e.message}")
        }
    }

    /**
     * Pretty-prints a UDM object in the specified format
     * 
     * @param args List containing: [udm, format, indent?]
     * @return UDM Scalar with pretty-printed string in specified format
     */
    fun prettyPrintFormat(args: List<UDM>): UDM {
        if (args.size < 2 || args.size > 3) {
            throw FunctionArgumentException("prettyPrintFormat expects 2 or 3 arguments (udm, format, indent?), got ${args.size}")
        }
        
        val udm = args[0]
        val format = args[1].asString()
        val indent = if (args.size > 2) {
            when (val indentUDM = args[2]) {
                is UDM.Scalar -> (indentUDM.value as? Number)?.toInt() ?: 2
                else -> 2
            }
        } else 2
        
        return try {
            val result = when (format.lowercase()) {
                "json" -> udmToJSONInternal(udm, indent)
                "xml" -> udmToXMLInternal(udm, indent)
                "yaml" -> udmToYAMLInternal(udm, indent)
                "csv" -> udmToCSVInternal(udm)
                else -> throw FunctionArgumentException("Unsupported format: $format")
            }
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to format UDM as $format: ${e.message}")
        }
    }

    /**
     * Creates a human-readable debug representation of UDM
     * 
     * @param args List containing: [udm, indent?, maxDepth?]
     * @return UDM Scalar with human-readable string representation
     */
    fun debugPrint(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException("debugPrint expects 1-3 arguments (udm, indent?, maxDepth?), got ${args.size}")
        }
        
        val udm = args[0]
        val indent = if (args.size > 1) {
            when (val indentUDM = args[1]) {
                is UDM.Scalar -> (indentUDM.value as? Number)?.toInt() ?: 2
                else -> 2
            }
        } else 2
        val maxDepth = if (args.size > 2) {
            when (val depthUDM = args[2]) {
                is UDM.Scalar -> (depthUDM.value as? Number)?.toInt() ?: 10
                else -> 10
            }
        } else 10
        
        return try {
            val result = debugPrintInternal(udm, 0, indent, maxDepth)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to create debug representation: ${e.message}")
        }
    }

    /**
     * Creates a compact single-line debug representation
     * 
     * @param args List containing: [udm, maxLength?]
     * @return UDM Scalar with compact string representation
     */
    fun debugPrintCompact(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 2) {
            throw FunctionArgumentException("debugPrintCompact expects 1 or 2 arguments (udm, maxLength?), got ${args.size}")
        }
        
        val udm = args[0]
        val maxLength = if (args.size > 1) {
            when (val maxLengthUDM = args[1]) {
                is UDM.Scalar -> (maxLengthUDM.value as? Number)?.toInt() ?: 200
                else -> 200
            }
        } else 200
        
        return try {
            val result = when (udm) {
                is UDM.Scalar -> {
                    when (val value = udm.value) {
                        null -> "null"
                        is Boolean -> value.toString()
                        is Number -> value.toString()
                        is String -> "\"$value\""
                        else -> value.toString()
                    }
                }
                is UDM.Array -> "[${udm.elements.size} items]"
                is UDM.Object -> "{${udm.properties.size} props}"
                else -> udm.toString()
            }
            
            val finalResult = if (result.length > maxLength) {
                result.take(maxLength) + "..."
            } else {
                result
            }
            UDM.Scalar(finalResult)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to create compact debug representation: ${e.message}")
        }
    }

    // Helper functions
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: throw FunctionArgumentException("Expected string value")
        else -> throw FunctionArgumentException("Expected string value, got ${this::class.simpleName}")
    }

    // Internal helper functions for simplified pretty-printing
    private fun prettyPrintJSONInternal(jsonString: String, indent: Int): String {
        // Simplified JSON pretty-printing
        return formatJSONString(jsonString, " ".repeat(indent))
    }

    private fun compactJSONInternal(jsonString: String): String {
        // Remove all unnecessary whitespace from JSON
        return jsonString.replace(Regex("\\s+"), "")
            .replace(": ", ":")
            .replace(", ", ",")
    }

    private fun udmToJSONInternal(udm: UDM, indent: Int = 2): String {
        return serializeUDMToJSON(udm, 0, " ".repeat(indent))
    }

    private fun prettyPrintXMLInternal(xmlString: String, indent: Int, preserveWhitespace: Boolean): String {
        // Simplified XML pretty-printing
        return formatXMLString(xmlString, " ".repeat(indent), preserveWhitespace)
    }

    private fun compactXMLInternal(xmlString: String): String {
        // Remove unnecessary whitespace between tags
        return xmlString.replace(Regex(">\\s+<"), "><")
    }

    private fun udmToXMLInternal(udm: UDM, indent: Int = 2, preserveWhitespace: Boolean = false): String {
        return serializeUDMToXML(udm, 0, " ".repeat(indent))
    }

    private fun prettyPrintYAMLInternal(yamlString: String, indent: Int, flowStyle: Boolean): String {
        // Simplified YAML formatting
        return yamlString // For now, return as-is
    }

    private fun udmToYAMLInternal(udm: UDM, indent: Int = 2, flowStyle: Boolean = false): String {
        return serializeUDMToYAML(udm, 0, " ".repeat(indent))
    }

    private fun udmToCSVInternal(udm: UDM): String {
        return serializeUDMToCSV(udm)
    }

    private fun prettyPrintCSVInternal(
        csvString: String,
        delimiter: String,
        alignColumns: Boolean
    ): String {
        val lines = csvString.trim().split("\n")
        if (lines.isEmpty()) return ""
        
        if (!alignColumns) {
            return lines.joinToString("\n") { it.trim() }
        }
        
        val rows = lines.map { line ->
            line.split(delimiter).map { it.trim() }
        }
        
        val columnWidths = mutableListOf<Int>()
        val maxCols = rows.maxOfOrNull { it.size } ?: 0
        
        for (col in 0 until maxCols) {
            val maxWidth = rows.mapNotNull { row ->
                row.getOrNull(col)?.length
            }.maxOrNull() ?: 0
            columnWidths.add(maxWidth)
        }
        
        val formatted = rows.map { row ->
            row.mapIndexed { index, value ->
                value.padEnd(columnWidths.getOrElse(index) { value.length })
            }.joinToString(" | ")
        }
        
        val result = StringBuilder()
        if (formatted.isNotEmpty()) {
            result.append(formatted[0]).append("\n")
            result.append("-".repeat(formatted[0].length)).append("\n")
            formatted.drop(1).forEach { row ->
                result.append(row).append("\n")
            }
        }
        
        return result.toString().trimEnd()
    }
    
    private fun prettyPrintAutoDetect(data: String, indent: Int): String {
        val trimmed = data.trim()
        
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                prettyPrintJSONInternal(data, indent)
            }
            trimmed.startsWith("<") -> {
                prettyPrintXMLInternal(data, indent, false)
            }
            trimmed.contains(Regex("^\\w+:\\s*\\S+", RegexOption.MULTILINE)) -> {
                prettyPrintYAMLInternal(data, indent, false)
            }
            trimmed.contains(",") && trimmed.split("\n").size > 1 -> {
                prettyPrintCSVInternal(data, ",", true)
            }
            else -> data
        }
    }
    
    private fun debugPrintInternal(
        udm: UDM,
        depth: Int,
        indent: Int,
        maxDepth: Int
    ): String {
        if (depth > maxDepth) {
            return "..."
        }
        
        val padding = " ".repeat(depth * indent)
        val nextPadding = " ".repeat((depth + 1) * indent)
        
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    null -> "${padding}null"
                    is Boolean -> "${padding}$value (Boolean)"
                    is Number -> "${padding}$value (Number)"
                    is String -> {
                        val preview = if (value.length > 50) {
                            value.take(50) + "..."
                        } else {
                            value
                        }
                        """${padding}"$preview" (String)"""
                    }
                    else -> "${padding}$value (${value::class.simpleName})"
                }
            }
            is UDM.Array -> {
                if (udm.elements.isEmpty()) {
                    "${padding}Array (empty)"
                } else {
                    val elements = udm.elements.take(10).mapIndexed { index, elem ->
                        "${nextPadding}[$index]: " + 
                        debugPrintInternal(elem, depth + 1, indent, maxDepth)
                            .trimStart()
                    }
                    val more = if (udm.elements.size > 10) {
                        "\n${nextPadding}... (${udm.elements.size - 10} more)"
                    } else {
                        ""
                    }
                    "${padding}Array (${udm.elements.size} elements) [\n" +
                    elements.joinToString(",\n") + more + "\n${padding}]"
                }
            }
            is UDM.Object -> {
                if (udm.properties.isEmpty()) {
                    "${padding}Object (empty)"
                } else {
                    val props = udm.properties.entries.take(20).map { (key, value) ->
                        "${nextPadding}$key: " +
                        debugPrintInternal(value, depth + 1, indent, maxDepth)
                            .trimStart()
                    }
                    val more = if (udm.properties.size > 20) {
                        "\n${nextPadding}... (${udm.properties.size - 20} more properties)"
                    } else {
                        ""
                    }
                    "${padding}Object (${udm.properties.size} properties) {\n" +
                    props.joinToString(",\n") + more + "\n${padding}}"
                }
            }
            else -> "${padding}${udm::class.simpleName}"
        }
    }

    // Simplified serialization helpers
    private fun serializeUDMToJSON(udm: UDM, depth: Int = 0, indentStr: String = "  "): String {
        val currentIndent = indentStr.repeat(depth)
        val nextIndent = indentStr.repeat(depth + 1)
        
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    null -> "null"
                    is Boolean -> value.toString()
                    is Number -> value.toString()
                    is String -> "\"${escapeJsonString(value)}\""
                    else -> "\"${escapeJsonString(value.toString())}\""
                }
            }
            is UDM.Array -> {
                if (udm.elements.isEmpty()) {
                    "[]"
                } else {
                    val elements = udm.elements.joinToString(",\n$nextIndent") { 
                        serializeUDMToJSON(it, depth + 1, indentStr) 
                    }
                    "[\n$nextIndent$elements\n$currentIndent]"
                }
            }
            is UDM.Object -> {
                if (udm.properties.isEmpty()) {
                    "{}"
                } else {
                    val properties = udm.properties.entries.joinToString(",\n$nextIndent") { (key, value) ->
                        "\"${escapeJsonString(key)}\": ${serializeUDMToJSON(value, depth + 1, indentStr)}"
                    }
                    "{\n$nextIndent$properties\n$currentIndent}"
                }
            }
            else -> "null"
        }
    }

    private fun serializeUDMToXML(udm: UDM, depth: Int = 0, indentStr: String = "  "): String {
        val currentIndent = indentStr.repeat(depth)
        
        return when (udm) {
            is UDM.Scalar -> {
                val value = udm.value?.toString() ?: ""
                escapeXmlContent(value)
            }
            is UDM.Array -> {
                udm.elements.joinToString("\n") { element ->
                    "$currentIndent<item>${serializeUDMToXML(element, depth + 1, indentStr)}</item>"
                }
            }
            is UDM.Object -> {
                udm.properties.entries.joinToString("\n") { (key, value) ->
                    val content = serializeUDMToXML(value, depth + 1, indentStr)
                    if (content.contains('\n')) {
                        "$currentIndent<$key>\n${indentStr.repeat(depth + 1)}$content\n$currentIndent</$key>"
                    } else {
                        "$currentIndent<$key>$content</$key>"
                    }
                }
            }
            else -> ""
        }
    }

    private fun serializeUDMToYAML(udm: UDM, depth: Int = 0, indentStr: String = "  "): String {
        val currentIndent = indentStr.repeat(depth)
        
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    null -> "null"
                    is Boolean -> value.toString()
                    is Number -> value.toString()
                    is String -> if (value.contains('\n') || value.contains(':')) "\"$value\"" else value
                    else -> value.toString()
                }
            }
            is UDM.Array -> {
                udm.elements.joinToString("\n") { element ->
                    "$currentIndent- ${serializeUDMToYAML(element, depth, indentStr)}"
                }
            }
            is UDM.Object -> {
                udm.properties.entries.joinToString("\n") { (key, value) ->
                    "$currentIndent$key: ${serializeUDMToYAML(value, depth + 1, indentStr)}"
                }
            }
            else -> "null"
        }
    }

    private fun serializeUDMToCSV(udm: UDM): String {
        return when (udm) {
            is UDM.Array -> {
                udm.elements.joinToString("\n") { element ->
                    when (element) {
                        is UDM.Array -> element.elements.joinToString(",") { 
                            (it as? UDM.Scalar)?.value?.toString() ?: "" 
                        }
                        is UDM.Object -> element.properties.values.joinToString(",") { 
                            (it as? UDM.Scalar)?.value?.toString() ?: "" 
                        }
                        else -> element.toString()
                    }
                }
            }
            is UDM.Object -> {
                val headers = udm.properties.keys.joinToString(",")
                val values = udm.properties.values.joinToString(",") { 
                    (it as? UDM.Scalar)?.value?.toString() ?: "" 
                }
                "$headers\n$values"
            }
            else -> udm.toString()
        }
    }

    private fun formatJSONString(json: String, indentStr: String): String {
        // Simple JSON formatting (this is a basic implementation)
        val result = StringBuilder()
        var indentLevel = 0
        var inString = false
        var escape = false
        
        for (char in json) {
            when {
                escape -> {
                    result.append(char)
                    escape = false
                }
                char == '\\' && inString -> {
                    result.append(char)
                    escape = true
                }
                char == '"' -> {
                    result.append(char)
                    inString = !inString
                }
                !inString -> {
                    when (char) {
                        '{', '[' -> {
                            result.append(char)
                            result.append('\n')
                            indentLevel++
                            result.append(indentStr.repeat(indentLevel))
                        }
                        '}', ']' -> {
                            result.append('\n')
                            indentLevel--
                            result.append(indentStr.repeat(indentLevel))
                            result.append(char)
                        }
                        ',' -> {
                            result.append(char)
                            result.append('\n')
                            result.append(indentStr.repeat(indentLevel))
                        }
                        ':' -> {
                            result.append(char)
                            result.append(' ')
                        }
                        else -> {
                            if (!char.isWhitespace()) {
                                result.append(char)
                            }
                        }
                    }
                }
                else -> result.append(char)
            }
        }
        
        return result.toString()
    }

    private fun formatXMLString(xml: String, indentStr: String, preserveWhitespace: Boolean): String {
        // Basic XML formatting
        return xml.replace("><", ">\n<")
    }

    private fun escapeJsonString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun escapeXmlContent(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}