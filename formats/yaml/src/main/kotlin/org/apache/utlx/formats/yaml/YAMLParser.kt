// formats/yaml/src/main/kotlin/org/apache/utlx/formats/yaml/YAMLParser.kt
package org.apache.utlx.formats.yaml

import org.apache.utlx.core.udm.*
import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * YAML Parser for UTL-X
 * 
 * Parses YAML documents into the Universal Data Model (UDM).
 * Supports:
 * - Basic types (strings, numbers, booleans, null)
 * - Collections (lists, maps)
 * - Date/time values
 * - Multi-document YAML files
 * - Anchors and aliases
 * - Custom tags
 * 
 * @author UTL-X Contributors
 */
class YAMLParser {
    
    private val yaml = Yaml()
    
    /**
     * Parse options for YAML parsing
     */
    data class ParseOptions(
        val multiDocument: Boolean = false,
        val preserveOrder: Boolean = true,
        val parseTimestamps: Boolean = true,
        val allowDuplicateKeys: Boolean = false
    )
    
    /**
     * Parse YAML from a string
     */
    fun parse(yamlString: String, options: ParseOptions = ParseOptions()): UDM {
        return parse(StringReader(yamlString), options)
    }
    
    /**
     * Parse YAML from an InputStream
     */
    fun parse(input: InputStream, options: ParseOptions = ParseOptions()): UDM {
        return parse(input.reader(), options)
    }
    
    /**
     * Parse YAML from a Reader
     */
    fun parse(reader: Reader, options: ParseOptions = ParseOptions()): UDM {
        return try {
            if (options.multiDocument) {
                parseMultiDocument(reader, options)
            } else {
                val yamlObject = yaml.load<Any?>(reader)
                convertToUDM(yamlObject, options)
            }
        } catch (e: Exception) {
            throw YAMLParseException("Failed to parse YAML: ${e.message}", e)
        }
    }
    
    /**
     * Parse multi-document YAML (documents separated by ---)
     */
    private fun parseMultiDocument(reader: Reader, options: ParseOptions): UDM {
        val documents = mutableListOf<UDM>()
        
        for (document in yaml.loadAll(reader)) {
            documents.add(convertToUDM(document, options))
        }
        
        return if (documents.size == 1) {
            documents[0]
        } else {
            UDM.Array(documents)
        }
    }
    
    /**
     * Convert parsed YAML object to UDM
     */
    private fun convertToUDM(obj: Any?, options: ParseOptions): UDM {
        return when (obj) {
            null -> UDM.Scalar.nullValue()
            
            is String -> UDM.Scalar.string(obj)
            
            is Number -> UDM.Scalar.number(obj)
            
            is Boolean -> UDM.Scalar.boolean(obj)
            
            is Date -> if (options.parseTimestamps) {
                UDM.DateTime(obj.toInstant())
            } else {
                UDM.Scalar.string(obj.toString())
            }
            
            is List<*> -> {
                val elements = obj.mapNotNull { element ->
                    convertToUDM(element, options)
                }
                UDM.Array(elements)
            }
            
            is Map<*, *> -> {
                val properties = if (options.preserveOrder) {
                    LinkedHashMap<String, UDM>()
                } else {
                    HashMap<String, UDM>()
                }
                
                obj.forEach { (key, value) ->
                    val keyStr = key?.toString() ?: ""
                    
                    if (!options.allowDuplicateKeys && properties.containsKey(keyStr)) {
                        throw YAMLParseException("Duplicate key found: $keyStr")
                    }
                    
                    properties[keyStr] = convertToUDM(value, options)
                }
                
                UDM.Object(properties, emptyMap())
            }
            
            else -> {
                // Handle custom objects by converting to string
                UDM.Scalar.string(obj.toString())
            }
        }
    }
    
    companion object {
        /**
         * Quick parse method for simple YAML strings
         */
        fun parseYAML(yaml: String): UDM {
            return YAMLParser().parse(yaml)
        }
        
        /**
         * Parse YAML with custom options
         */
        fun parseYAML(yaml: String, options: ParseOptions): UDM {
            return YAMLParser().parse(yaml, options)
        }
    }
}

/**
 * Exception thrown when YAML parsing fails
 */
class YAMLParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Extension function for String to parse as YAML
 */
fun String.parseAsYAML(): UDM = YAMLParser.parseYAML(this)

/**
 * Extension function for InputStream to parse as YAML
 */
fun InputStream.parseAsYAML(options: YAMLParser.ParseOptions = YAMLParser.ParseOptions()): UDM {
    return YAMLParser().parse(this, options)
}
