// stdlib/src/main/kotlin/org/apache/utlx/stdlib/serialization/SerializationFunctions.kt
package org.apache.utlx.stdlib.serialization

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

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
            throw FunctionArgumentException("Cannot parse empty JSON string")
        }
        
        return try {
            // Simple JSON parsing - in real implementation would use JSONParser
            UDM.Scalar(jsonString) // Placeholder implementation
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to parse JSON: ${e.message}")
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
            // Simple JSON rendering - in real implementation would use JSONSerializer
            UDM.Scalar(obj.toString()) // Placeholder implementation
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to render JSON: ${e.message}")
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
            throw FunctionArgumentException("Cannot parse empty XML string")
        }
        
        return try {
            // Simple XML parsing - in real implementation would use XMLParser
            UDM.Scalar(xmlString) // Placeholder implementation
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to parse XML: ${e.message}")
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
            throw FunctionArgumentException("Failed to render XML: ${e.message}")
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
            throw FunctionArgumentException("Cannot parse empty YAML string")
        }
        
        return try {
            // Simple YAML parsing - in real implementation would use YAMLParser
            UDM.Scalar(yamlString) // Placeholder implementation
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to parse YAML: ${e.message}")
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
            throw FunctionArgumentException("Failed to render YAML: ${e.message}")
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
        val hasHeaders = if (args.size > 1) args[1].asBoolean() else true
        
        if (csvString.isBlank()) {
            throw FunctionArgumentException("Cannot parse empty CSV string")
        }
        
        return try {
            // Simple CSV parsing - in real implementation would use CSVParser
            UDM.Array(listOf(UDM.Scalar(csvString))) // Placeholder implementation
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to parse CSV: ${e.message}")
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
            throw FunctionArgumentException("Failed to render CSV: ${e.message}")
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
            else -> throw FunctionArgumentException("Unsupported format: $format")
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
            else -> throw FunctionArgumentException("Unsupported format: $format")
        }
    }
    
    // Helper functions
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun requireArgs(args: List<UDM>, range: IntRange, functionName: String) {
        if (args.size !in range) {
            throw FunctionArgumentException("$functionName expects ${range.first}..${range.last} arguments, got ${args.size}")
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
            else -> throw FunctionArgumentException("Expected string value, got ${this::class.simpleName}")
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