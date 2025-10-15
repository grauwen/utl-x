/**
 * Serialization Functions for UTL-X Standard Library
 * 
 * Location: stdlib/src/main/kotlin/org/apache/utlx/stdlib/serialization/SerializationFunctions.kt
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

package org.apache.utlx.stdlib.serialization

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.json.JSONParser
import org.apache.utlx.formats.json.JSONSerializer
import org.apache.utlx.formats.xml.XMLParser
import org.apache.utlx.formats.xml.XMLSerializer
import org.apache.utlx.formats.yaml.YAMLParser
import org.apache.utlx.formats.yaml.YAMLSerializer
import org.apache.utlx.formats.csv.CSVParser
import org.apache.utlx.formats.csv.CSVSerializer

/**
 * CSV parsing options
 */
data class CsvOptions(
    val delimiter: String = ",",
    val quote: String = "\"",
    val escape: String = "\\",
    val headers: Boolean = true,
    val skipEmptyLines: Boolean = true,
    val trim: Boolean = true
)

/**
 * XML rendering options
 */
data class XmlOptions(
    val pretty: Boolean = false,
    val indent: String = "  ",
    val declaration: Boolean = true,
    val encoding: String = "UTF-8",
    val rootElement: String? = null
)

/**
 * JSON rendering options
 */
data class JsonOptions(
    val pretty: Boolean = false,
    val indent: String = "  ",
    val sortKeys: Boolean = false
)

// ============================================================================
// JSON PARSE/RENDER
// ============================================================================

/**
 * Parse a JSON string into a UDM object
 * 
 * @param jsonString The JSON string to parse
 * @return UDM object representing the parsed JSON
 * @throws ParseException if the JSON is malformed
 * 
 * Example:
 *   let embedded = parseJson(payload.data.jsonField)
 *   embedded.customer.name
 */
fun parseJson(jsonString: String): Any {
    if (jsonString.isBlank()) {
        throw IllegalArgumentException("Cannot parse empty JSON string")
    }
    
    return try {
        JSONParser().parse(jsonString.byteInputStream())
    } catch (e: Exception) {
        throw ParseException("Failed to parse JSON: ${e.message}", e)
    }
}

/**
 * Render a UDM object as a JSON string
 * 
 * @param obj The object to serialize
 * @param pretty Whether to pretty-print (default: false)
 * @param indent Indentation string (default: "  ")
 * @return JSON string representation
 * 
 * Example:
 *   let jsonStr = renderJson(customer, pretty=true)
 */
fun renderJson(obj: Any, pretty: Boolean = false, indent: String = "  "): String {
    val options = JsonOptions(pretty = pretty, indent = indent)
    return renderJson(obj, options)
}

/**
 * Render a UDM object as a JSON string with full options
 */
fun renderJson(obj: Any, options: JsonOptions): String {
    return try {
        val serializer = JSONSerializer(
            pretty = options.pretty,
            indent = options.indent,
            sortKeys = options.sortKeys
        )
        serializer.serialize(obj)
    } catch (e: Exception) {
        throw SerializationException("Failed to render JSON: ${e.message}", e)
    }
}

// ============================================================================
// XML PARSE/RENDER
// ============================================================================

/**
 * Parse an XML string into a UDM object
 * 
 * @param xmlString The XML string to parse
 * @return UDM object representing the parsed XML
 * @throws ParseException if the XML is malformed
 * 
 * Example:
 *   let soapRequest = parseXml(payload.embeddedSoap)
 *   soapRequest.Envelope.Body.GetCustomer.customerId
 */
fun parseXml(xmlString: String): Any {
    if (xmlString.isBlank()) {
        throw IllegalArgumentException("Cannot parse empty XML string")
    }
    
    return try {
        XMLParser().parse(xmlString.byteInputStream())
    } catch (e: Exception) {
        throw ParseException("Failed to parse XML: ${e.message}", e)
    }
}

/**
 * Render a UDM object as an XML string
 * 
 * @param obj The object to serialize
 * @param pretty Whether to pretty-print (default: false)
 * @param declaration Whether to include XML declaration (default: true)
 * @return XML string representation
 * 
 * Example:
 *   let xmlStr = renderXml(customer, pretty=true)
 */
fun renderXml(
    obj: Any, 
    pretty: Boolean = false, 
    declaration: Boolean = true,
    rootElement: String? = null
): String {
    val options = XmlOptions(
        pretty = pretty,
        declaration = declaration,
        rootElement = rootElement
    )
    return renderXml(obj, options)
}

/**
 * Render a UDM object as an XML string with full options
 */
fun renderXml(obj: Any, options: XmlOptions): String {
    return try {
        val serializer = XMLSerializer(
            pretty = options.pretty,
            indent = options.indent,
            declaration = options.declaration,
            encoding = options.encoding,
            rootElement = options.rootElement
        )
        serializer.serialize(obj)
    } catch (e: Exception) {
        throw SerializationException("Failed to render XML: ${e.message}", e)
    }
}

// ============================================================================
// YAML PARSE/RENDER
// ============================================================================

/**
 * Parse a YAML string into a UDM object
 * 
 * @param yamlString The YAML string to parse
 * @return UDM object representing the parsed YAML
 * @throws ParseException if the YAML is malformed
 * 
 * Example:
 *   let config = parseYaml(payload.configData)
 *   config.database.host
 */
fun parseYaml(yamlString: String): Any {
    if (yamlString.isBlank()) {
        throw IllegalArgumentException("Cannot parse empty YAML string")
    }
    
    return try {
        YAMLParser().parse(yamlString.byteInputStream())
    } catch (e: Exception) {
        throw ParseException("Failed to parse YAML: ${e.message}", e)
    }
}

/**
 * Render a UDM object as a YAML string
 * 
 * @param obj The object to serialize
 * @return YAML string representation
 * 
 * Example:
 *   let yamlStr = renderYaml(config)
 */
fun renderYaml(obj: Any): String {
    return try {
        YAMLSerializer().serialize(obj)
    } catch (e: Exception) {
        throw SerializationException("Failed to render YAML: ${e.message}", e)
    }
}

// ============================================================================
// CSV PARSE/RENDER
// ============================================================================

/**
 * Parse a CSV string into an array of objects
 * 
 * @param csvString The CSV string to parse
 * @param delimiter Field delimiter (default: ",")
 * @param headers Whether first row contains headers (default: true)
 * @return Array of objects (if headers=true) or array of arrays
 * @throws ParseException if the CSV is malformed
 * 
 * Example:
 *   let customers = parseCsv(payload.csvData)
 *   customers[0].name
 */
fun parseCsv(
    csvString: String,
    delimiter: String = ",",
    headers: Boolean = true,
    skipEmptyLines: Boolean = true
): Any {
    if (csvString.isBlank()) {
        throw IllegalArgumentException("Cannot parse empty CSV string")
    }
    
    val options = CsvOptions(
        delimiter = delimiter,
        headers = headers,
        skipEmptyLines = skipEmptyLines
    )
    return parseCsv(csvString, options)
}

/**
 * Parse a CSV string with full options
 */
fun parseCsv(csvString: String, options: CsvOptions): Any {
    return try {
        val parser = CSVParser(
            delimiter = options.delimiter,
            quote = options.quote,
            escape = options.escape,
            headers = options.headers,
            skipEmptyLines = options.skipEmptyLines,
            trim = options.trim
        )
        parser.parse(csvString.byteInputStream())
    } catch (e: Exception) {
        throw ParseException("Failed to parse CSV: ${e.message}", e)
    }
}

/**
 * Render an array of objects as a CSV string
 * 
 * @param arr Array of objects to serialize
 * @param delimiter Field delimiter (default: ",")
 * @param headers Whether to include header row (default: true)
 * @return CSV string representation
 * 
 * Example:
 *   let csvStr = renderCsv(customers)
 */
fun renderCsv(
    arr: Any,
    delimiter: String = ",",
    headers: Boolean = true
): String {
    val options = CsvOptions(
        delimiter = delimiter,
        headers = headers
    )
    return renderCsv(arr, options)
}

/**
 * Render an array as CSV with full options
 */
fun renderCsv(arr: Any, options: CsvOptions): String {
    return try {
        val serializer = CSVSerializer(
            delimiter = options.delimiter,
            quote = options.quote,
            escape = options.escape,
            headers = options.headers
        )
        serializer.serialize(arr)
    } catch (e: Exception) {
        throw SerializationException("Failed to render CSV: ${e.message}", e)
    }
}

// ============================================================================
// GENERIC PARSE/RENDER (with auto-detection or explicit format)
// ============================================================================

/**
 * Parse a string with automatic format detection
 * 
 * @param str The string to parse
 * @return UDM object
 * 
 * Detection order: JSON, XML, YAML, CSV
 */
fun parse(str: String): Any {
    if (str.isBlank()) {
        throw IllegalArgumentException("Cannot parse empty string")
    }
    
    val trimmed = str.trim()
    
    // Try JSON first (most common in APIs)
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        return try {
            parseJson(str)
        } catch (e: Exception) {
            // Not JSON, continue
        }
    }
    
    // Try XML
    if (trimmed.startsWith("<")) {
        return try {
            parseXml(str)
        } catch (e: Exception) {
            // Not XML, continue
        }
    }
    
    // Try YAML
    return try {
        parseYaml(str)
    } catch (e: Exception) {
        // Try CSV as last resort
        try {
            parseCsv(str)
        } catch (e2: Exception) {
            throw ParseException("Could not parse string as JSON, XML, YAML, or CSV")
        }
    }
}

/**
 * Parse a string with explicit format specification
 * 
 * @param str The string to parse
 * @param format Format name: "json", "xml", "yaml", "csv"
 * @return UDM object
 */
fun parse(str: String, format: String): Any {
    return when (format.lowercase()) {
        "json" -> parseJson(str)
        "xml" -> parseXml(str)
        "yaml", "yml" -> parseYaml(str)
        "csv" -> parseCsv(str)
        else -> throw IllegalArgumentException("Unknown format: $format")
    }
}

/**
 * Render a UDM object with explicit format specification
 * 
 * @param obj The object to serialize
 * @param format Format name: "json", "xml", "yaml", "csv"
 * @param pretty Whether to pretty-print (where applicable)
 * @return String representation
 */
fun render(obj: Any, format: String, pretty: Boolean = false): String {
    return when (format.lowercase()) {
        "json" -> renderJson(obj, pretty = pretty)
        "xml" -> renderXml(obj, pretty = pretty)
        "yaml", "yml" -> renderYaml(obj)
        "csv" -> renderCsv(obj)
        else -> throw IllegalArgumentException("Unknown format: $format")
    }
}

// ============================================================================
// EXCEPTIONS
// ============================================================================

class ParseException(message: String, cause: Throwable? = null) : 
    RuntimeException(message, cause)

class SerializationException(message: String, cause: Throwable? = null) : 
    RuntimeException(message, cause)

// ============================================================================
// INTEGRATION INTO Functions.kt
// 
// Add to stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt:
// ============================================================================

/*

import org.apache.utlx.stdlib.serialization.*

// In the function registry map, add:

// Serialization Functions
"parseJson" to ::parseJson,
"renderJson" to ::renderJson,
"parseXml" to ::parseXml,
"renderXml" to ::renderXml,
"parseYaml" to ::parseYaml,
"renderYaml" to ::renderYaml,
"parseCsv" to ::parseCsv,
"renderCsv" to ::renderCsv,
"parse" to ::parse,
"render" to ::render,

// Aliases for compatibility with Tibco BW
"tibco_parse" to ::parse,
"tibco_render" to ::render,

*/
