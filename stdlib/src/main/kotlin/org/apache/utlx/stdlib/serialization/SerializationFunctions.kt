// stdlib/src/main/kotlin/org/apache/utlx/stdlib/serialization/SerializationFunctions.kt
package org.apache.utlx.stdlib.serialization

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.apache.utlx.formats.xml.XMLParser
import org.apache.utlx.formats.yaml.YAMLParser
import org.apache.utlx.formats.csv.CSVParser
import org.apache.utlx.formats.csv.CSVDialect

/**
 * Serialization Functions for UTL-X Standard Library
 * 
 * Provides parse/render functions for handling nested/embedded formats within documents.
 * Similar to Tibco BW parse() and render() functions.
 * 
 * Common Use Cases:
 * - REST APIs with embedded SOAP XML
 * - XML documents with CSV in CDATA sections
 * - JSON payloads containing serialized XML
 * - Message queues with nested format strings
 */
object SerializationFunctions {
    
    @UTLXFunction(
        description = "Parse a JSON string into a UDM object",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "jsonString: Jsonstring value"
        ],
        returns = "Result of the operation",
        example = "parseJson(\"{'name': 'John'}\")",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Parse a JSON string into a UDM object
     * Usage: parseJson("{'name': 'John'}")
     */
    fun parseJson(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseJson")
        val jsonString = args[0].asString()

        if (jsonString.isBlank()) {
            throw FunctionArgumentException(
                "parseJson cannot parse empty or blank JSON string. " +
                "Hint: Provide a valid JSON string like '{\"key\": \"value\"}' or '[1,2,3]'."
            )
        }

        return try {
            val mapper = ObjectMapper()
            val jsonNode = mapper.readTree(jsonString)
            jsonNodeToUDM(jsonNode)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "parseJson failed to parse JSON string: ${e.message}. " +
                "Hint: Ensure the string is valid JSON. Common issues include missing quotes, trailing commas, or unescaped characters."
            )
        }
    }

    /**
     * Convert Jackson JsonNode to UDM
     */
    private fun jsonNodeToUDM(node: com.fasterxml.jackson.databind.JsonNode): UDM {
        return when {
            node.isNull -> UDM.Scalar(null)
            node.isBoolean -> UDM.Scalar(node.booleanValue())
            node.isNumber -> UDM.Scalar(node.doubleValue())
            node.isTextual -> UDM.Scalar(node.textValue())
            node.isArray -> {
                val elements = node.elements().asSequence().map { jsonNodeToUDM(it) }.toList()
                UDM.Array(elements)
            }
            node.isObject -> {
                val properties = node.fields().asSequence()
                    .associate { (key, value) -> key to jsonNodeToUDM(value) }
                UDM.Object(properties)
            }
            else -> UDM.Scalar(node.toString())
        }
    }
    
    @UTLXFunction(
        description = "Render a UDM object as a JSON string",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "obj: Obj value"
        ],
        returns = "Result of the operation",
        example = "renderJson(data, pretty?)",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Render a UDM object as a JSON string
     * Usage: renderJson(data, pretty?)
     */
    fun renderJson(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "renderJson")
        val obj = args[0]
        val pretty = if (args.size > 1) args[1].asBoolean() else false

        return try {
            val mapper = ObjectMapper()
            if (pretty) {
                mapper.enable(SerializationFeature.INDENT_OUTPUT)
            }

            // Convert UDM to JSON-serializable structure
            val jsonValue = udmToJsonValue(obj)
            val jsonString = mapper.writeValueAsString(jsonValue)

            UDM.Scalar(jsonString)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "renderJson failed to serialize to JSON: ${e.message}. " +
                "Hint: Ensure the data structure is serializable. Complex types may need conversion."
            )
        }
    }

    /**
     * Convert UDM to a structure that Jackson can serialize
     */
    private fun udmToJsonValue(udm: UDM): Any? {
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    null -> null
                    is String -> value
                    is Number -> {
                        // If it's a whole number, convert to Int/Long for proper JSON output
                        val doubleValue = value.toDouble()
                        if (doubleValue.isFinite() && doubleValue == doubleValue.toLong().toDouble()) {
                            doubleValue.toLong()
                        } else {
                            doubleValue
                        }
                    }
                    is Boolean -> value
                    else -> value.toString()
                }
            }
            is UDM.Array -> {
                udm.elements.map { udmToJsonValue(it) }
            }
            is UDM.Object -> {
                udm.properties.mapValues { (_, v) -> udmToJsonValue(v) }
            }
            is UDM.DateTime -> udm.toISOString()
            is UDM.Date -> udm.toISOString()
            is UDM.LocalDateTime -> udm.toISOString()
            is UDM.Time -> udm.toISOString()
            is UDM.Binary -> {
                // Encode binary data as base64 string
                java.util.Base64.getEncoder().encodeToString(udm.data)
            }
            is UDM.Lambda -> "<lambda>"
        }
    }
    
    @UTLXFunction(
        description = "Parse an XML string into a UDM object",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "xmlString: Xmlstring value"
        ],
        returns = "Result of the operation",
        example = "parseXml(\"<root><item>value</item></root>\")",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Parse an XML string into a UDM object
     * Usage: parseXml("<root><item>value</item></root>")
     */
    fun parseXml(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseXml")
        val xmlString = args[0].asString()

        if (xmlString.isBlank()) {
            throw FunctionArgumentException(
                "parseXml cannot parse empty or blank XML string. " +
                "Hint: Provide a valid XML string like '<root><item>value</item></root>'."
            )
        }

        return try {
            val parser = XMLParser(xmlString)
            val parsed = parser.parse()
            // Unwrap leaf text elements for simpler access
            unwrapXmlLeafElements(parsed)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "parseXml failed to parse XML string: ${e.message}. " +
                "Hint: Ensure the string is well-formed XML. Check for matching tags and proper escaping."
            )
        }
    }

    /**
     * Recursively unwrap leaf text elements in XML structures.
     * Converts objects with only "_text" property and no real attributes to scalars.
     * This makes accessing simple text values more convenient: obj.name instead of obj.name._text
     */
    private fun unwrapXmlLeafElements(udm: UDM): UDM {
        return when (udm) {
            is UDM.Object -> {
                // Check if this is a leaf text element (only _text property, no real attributes)
                val hasOnlyTextProperty = udm.properties.size == 1 && udm.properties.containsKey("_text")
                val hasNoRealAttributes = udm.attributes.all { (key, _) ->
                    key.startsWith("xmlns") || key == "xmlns"
                }

                if (hasOnlyTextProperty && hasNoRealAttributes) {
                    // Return the text value directly (already a Scalar)
                    udm.properties["_text"]!!
                } else {
                    // Recursively unwrap properties
                    val unwrappedProperties = udm.properties.mapValues { (_, value) ->
                        unwrapXmlLeafElements(value)
                    }
                    UDM.Object(unwrappedProperties, udm.attributes, udm.name, udm.metadata)
                }
            }
            is UDM.Array -> {
                // Recursively unwrap array elements
                UDM.Array(udm.elements.map { unwrapXmlLeafElements(it) })
            }
            else -> udm // Scalars, Binary, DateTime, etc. pass through unchanged
        }
    }
    
    @UTLXFunction(
        description = "Render a UDM object as an XML string",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "obj: Obj value"
        ],
        returns = "Result of the operation",
        example = "renderXml(data, pretty?)",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Render a UDM object as an XML string
     * Usage: renderXml(data, pretty?)
     */
    fun renderXml(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "renderXml")
        val obj = args[0]
        val pretty = if (args.size > 1) args[1].asBoolean() else false
        
        return try {
            // Simple XML rendering - in real implementation would use XMLSerializer
            UDM.Scalar(obj.toString()) // Placeholder implementation
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "renderXml failed to serialize to XML: ${e.message}. " +
                "Hint: Ensure the data structure can be represented as XML."
            )
        }
    }
    
    @UTLXFunction(
        description = "Parse a YAML string into a UDM object",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "parseYaml(\"name: John\\nage: 30\")",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Parse a YAML string into a UDM object
     * Usage: parseYaml("name: John\nage: 30")
     */
    fun parseYaml(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseYaml")
        val yamlString = args[0].asString()

        if (yamlString.isBlank()) {
            throw FunctionArgumentException(
                "parseYaml cannot parse empty or blank YAML string. " +
                "Hint: Provide a valid YAML string like 'name: John\\nage: 30'."
            )
        }

        return try {
            val parser = YAMLParser()
            parser.parse(yamlString)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "parseYaml failed to parse YAML string: ${e.message}. " +
                "Hint: Ensure the string is valid YAML. Check indentation and syntax."
            )
        }
    }
    
    @UTLXFunction(
        description = "Render a UDM object as a YAML string",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "renderYaml(data)",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Render a UDM object as a YAML string
     * Usage: renderYaml(data)
     */
    fun renderYaml(args: List<UDM>): UDM {
        requireArgs(args, 1, "renderYaml")
        val obj = args[0]
        
        return try {
            // Simple YAML rendering - in real implementation would use YAMLSerializer
            UDM.Scalar(obj.toString()) // Placeholder implementation
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "renderYaml failed to serialize to YAML: ${e.message}. " +
                "Hint: Ensure the data structure can be represented as YAML."
            )
        }
    }
    
    @UTLXFunction(
        description = "Parse a CSV string into a UDM array",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "parseCsv(\"name,age\\nJohn,30\")",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Parse a CSV string into a UDM array
     * Usage: parseCsv("name,age\nJohn,30")
     */
    fun parseCsv(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "parseCsv")
        val csvString = args[0].asString()

        // Parse options if provided
        var hasHeaders = true
        var delimiter = ','

        if (args.size > 1) {
            val options = args[1] as? UDM.Object
            if (options != null) {
                // Check for headers option
                val headersOption = options.properties["headers"]
                if (headersOption is UDM.Scalar) {
                    hasHeaders = headersOption.asBoolean()
                }

                // Check for delimiter option
                val delimiterOption = options.properties["delimiter"]
                if (delimiterOption is UDM.Scalar) {
                    val delimiterStr = delimiterOption.asString()
                    if (delimiterStr.isNotEmpty()) {
                        delimiter = delimiterStr[0]
                    }
                }
            }
        }

        if (csvString.isBlank()) {
            throw FunctionArgumentException(
                "parseCsv cannot parse empty or blank CSV string. " +
                "Hint: Provide a valid CSV string like 'name,age\\nJohn,30'."
            )
        }

        return try {
            val dialect = CSVDialect(delimiter = delimiter)
            val parser = CSVParser(csvString, dialect)
            parser.parse(hasHeaders)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "parseCsv failed to parse CSV string: ${e.message}. " +
                "Hint: Ensure the string is valid CSV. Check for proper delimiters and quoted fields."
            )
        }
    }
    
    @UTLXFunction(
        description = "Render a UDM array as a CSV string",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "obj: Obj value",
        "format: Format value"
        ],
        returns = "Result of the operation",
        example = "renderCsv(data, includeHeaders?)",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Render a UDM array as a CSV string
     * Usage: renderCsv(data, includeHeaders?)
     */
    fun renderCsv(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "renderCsv")
        val obj = args[0]
        val includeHeaders = if (args.size > 1) args[1].asBoolean() else true
        
        return try {
            // Simple CSV rendering - in real implementation would use CSVSerializer
            UDM.Scalar(obj.toString()) // Placeholder implementation
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "renderCsv failed to serialize to CSV: ${e.message}. " +
                "Hint: Ensure the data is an array of objects with consistent fields."
            )
        }
    }
    
    @UTLXFunction(
        description = "Auto-detect format and parse",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "dataString: Datastring value",
        "format: Format value"
        ],
        returns = "Result of the operation",
        example = "parse(data, format?)",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Auto-detect format and parse
     * Usage: parse(data, format?)
     */
    fun parse(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "parse")
        val dataString = args[0].asString()
        val format = if (args.size > 1) args[1].asString() else "auto"
        
        return when (format.lowercase()) {
            "json" -> parseJson(listOf(args[0]))
            "xml" -> parseXml(listOf(args[0]))
            "yaml", "yml" -> parseYaml(listOf(args[0]))
            "csv" -> parseCsv(listOf(args[0]))
            "auto" -> {
                // Simple auto-detection based on first character
                when {
                    dataString.trim().startsWith("{") || dataString.trim().startsWith("[") -> 
                        parseJson(listOf(args[0]))
                    dataString.trim().startsWith("<") -> 
                        parseXml(listOf(args[0]))
                    else ->
                        UDM.Scalar(dataString)
                }
            }
            else -> throw FunctionArgumentException(
                "parse does not support format '$format'. " +
                "Hint: Supported formats are: json, xml, yaml, yml, csv, auto."
            )
        }
    }
    
    @UTLXFunction(
        description = "Render object to specified format",
        minArgs = 2,
        maxArgs = 2,
        category = "Other",
        parameters = [
            "obj: Obj value",
        "format: Format value"
        ],
        returns = "Result of the operation",
        example = "render(data, format, pretty?)",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Render object to specified format
     * Usage: render(data, format, pretty?)
     */
    fun render(args: List<UDM>): UDM {
        requireArgs(args, 2..3, "render")
        val obj = args[0]
        val format = args[1].asString()
        val pretty = if (args.size > 2) args[2].asBoolean() else false
        
        return when (format.lowercase()) {
            "json" -> renderJson(listOf(obj, UDM.Scalar(pretty)))
            "xml" -> renderXml(listOf(obj, UDM.Scalar(pretty)))
            "yaml", "yml" -> renderYaml(listOf(obj))
            "csv" -> renderCsv(listOf(obj))
            else -> throw FunctionArgumentException(
                "render does not support format '$format'. " +
                "Hint: Supported formats are: json, xml, yaml, yml, csv."
            )
        }
    }
    
    // Helper functions
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
    }

    private fun requireArgs(args: List<UDM>, range: IntRange, functionName: String) {
        if (args.size !in range) {
            throw FunctionArgumentException(
                "$functionName expects ${range.first}..${range.last} arguments, got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
    }
    
    private fun UDM.asString(): String {
        return when (this) {
            is UDM.Scalar -> {
                val v = value
                when (v) {
                    is String -> v
                    is Number -> v.toString()
                    is Boolean -> v.toString()
                    null -> ""
                    else -> v.toString()
                }
            }
            else -> throw FunctionArgumentException(
                "Expected string value, but got ${getTypeDescription(this)}. " +
                "Hint: Use toString() to convert values to strings."
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
    
    private fun UDM.asBoolean(): Boolean {
        return when (this) {
            is UDM.Scalar -> {
                val v = value
                when (v) {
                    is Boolean -> v
                    is Number -> v.toDouble() != 0.0
                    is String -> v.isNotEmpty() && v.lowercase() in listOf("true", "yes", "1")
                    null -> false
                    else -> true
                }
            }
            is UDM.Array -> elements.isNotEmpty()
            is UDM.Object -> properties.isNotEmpty()
            else -> true
        }
    }
}