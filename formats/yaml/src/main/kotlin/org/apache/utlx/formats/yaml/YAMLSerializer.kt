// formats/yaml/src/main/kotlin/org/apache/utlx/formats/yaml/YAMLSerializer.kt
package org.apache.utlx.formats.yaml

import org.apache.utlx.core.udm.*
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.OutputStream
import java.io.StringWriter
import java.io.Writer
import java.time.format.DateTimeFormatter

/**
 * YAML Serializer for UTL-X
 * 
 * Serializes the Universal Data Model (UDM) to YAML format.
 * Supports:
 * - Pretty printing with configurable indentation
 * - Flow style (inline) or block style formatting
 * - Custom date/time formatting
 * - Multi-document output
 * - Anchor and alias generation for repeated values
 * 
 * @author UTL-X Contributors
 */
class YAMLSerializer {
    
    /**
     * Serialization options for YAML output
     */
    data class SerializeOptions(
        val pretty: Boolean = true,
        val indent: Int = 2,
        val defaultFlowStyle: DumperOptions.FlowStyle = DumperOptions.FlowStyle.BLOCK,
        val lineBreak: DumperOptions.LineBreak = DumperOptions.LineBreak.UNIX,
        val explicitStart: Boolean = true,
        val explicitEnd: Boolean = false,
        val canonicalOutput: Boolean = false,
        val allowUnicode: Boolean = true,
        val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT,
        val maxSimpleKeyLength: Int = 128,
        val splitLines: Boolean = true,
        val width: Int = 80
    )
    
    /**
     * Serialize UDM to YAML string
     */
    fun serialize(udm: UDM, options: SerializeOptions = SerializeOptions()): String {
        val writer = StringWriter()
        serialize(udm, writer, options)
        return writer.toString()
    }
    
    /**
     * Serialize UDM to OutputStream
     */
    fun serialize(udm: UDM, output: OutputStream, options: SerializeOptions = SerializeOptions()) {
        serialize(udm, output.writer(), options)
    }
    
    /**
     * Serialize UDM to Writer
     */
    fun serialize(udm: UDM, writer: Writer, options: SerializeOptions = SerializeOptions()) {
        val dumperOptions = createDumperOptions(options)
        val yaml = Yaml(dumperOptions)
        
        try {
            val yamlObject = convertFromUDM(udm, options)
            yaml.dump(yamlObject, writer)
        } catch (e: Exception) {
            throw YAMLSerializeException("Failed to serialize to YAML: ${e.message}", e)
        }
    }
    
    /**
     * Serialize multiple UDM documents to YAML (multi-document)
     */
    fun serializeMultiDocument(
        documents: List<UDM>,
        options: SerializeOptions = SerializeOptions()
    ): String {
        val writer = StringWriter()
        val dumperOptions = createDumperOptions(options)
        val yaml = Yaml(dumperOptions)
        
        try {
            val yamlObjects = documents.map { convertFromUDM(it, options) }
            yaml.dumpAll(yamlObjects.iterator(), writer)
            return writer.toString()
        } catch (e: Exception) {
            throw YAMLSerializeException("Failed to serialize multi-document YAML: ${e.message}", e)
        }
    }
    
    /**
     * Create SnakeYAML DumperOptions from our SerializeOptions
     */
    private fun createDumperOptions(options: SerializeOptions): DumperOptions {
        return DumperOptions().apply {
            indent = options.indent
            defaultFlowStyle = options.defaultFlowStyle
            lineBreak = options.lineBreak
            isExplicitStart = options.explicitStart
            isExplicitEnd = options.explicitEnd
            isCanonical = options.canonicalOutput
            isAllowUnicode = options.allowUnicode
            maxSimpleKeyLength = options.maxSimpleKeyLength
            // isSplitLines = options.splitLines // This property may not exist in the version of SnakeYAML being used
            width = options.width
            isPrettyFlow = options.pretty
        }
    }
    
    /**
     * Convert UDM to YAML-compatible object
     */
    private fun convertFromUDM(udm: UDM, options: SerializeOptions): Any? {
        return when (udm) {
            is UDM.Scalar -> {
                // Handle numbers specially to preserve integer vs float distinction
                when (val value = udm.value) {
                    is Double -> {
                        // Convert whole numbers to Long for proper YAML serialization
                        if (value.isFinite() && value % 1.0 == 0.0) {
                            value.toLong()
                        } else {
                            value
                        }
                    }
                    is Float -> {
                        // Convert whole numbers to Int for proper YAML serialization
                        if (value.isFinite() && value % 1.0f == 0.0f) {
                            value.toInt()
                        } else {
                            value
                        }
                    }
                    else -> value
                }
            }

            is UDM.DateTime -> {
                // Format date using specified formatter
                options.dateTimeFormat.format(udm.instant)
            }

            is UDM.Date -> {
                // Format date as ISO string
                udm.toISOString()
            }

            is UDM.LocalDateTime -> {
                // Format local datetime as ISO string
                udm.toISOString()
            }

            is UDM.Time -> {
                // Format time as ISO string
                udm.toISOString()
            }

            is UDM.Binary -> {
                "<binary:${udm.data.size} bytes>"
            }

            is UDM.Array -> {
                udm.elements.map { convertFromUDM(it, options) }
            }

            is UDM.Object -> {
                val map = LinkedHashMap<String, Any?>()
                udm.properties.forEach { (key, value) ->
                    map[key] = convertFromUDM(value, options)
                }

                // Add attributes as metadata (if any)
                if (udm.attributes.isNotEmpty()) {
                    val attrMap = LinkedHashMap<String, String>()
                    udm.attributes.forEach { (key, value) ->
                        attrMap["@$key"] = value
                    }
                    map["_attributes"] = attrMap
                }

                map
            }

            is UDM.Lambda -> {
                "<function>"
            }
        }
    }
    
    companion object {
        /**
         * Quick serialize method for UDM to YAML string
         */
        fun toYAML(udm: UDM, pretty: Boolean = true): String {
            return YAMLSerializer().serialize(
                udm,
                SerializeOptions(pretty = pretty)
            )
        }
        
        /**
         * Serialize with custom options
         */
        fun toYAML(udm: UDM, options: SerializeOptions): String {
            return YAMLSerializer().serialize(udm, options)
        }
        
        /**
         * Common flow style preset for compact output
         */
        fun toCompactYAML(udm: UDM): String {
            return YAMLSerializer().serialize(
                udm,
                SerializeOptions(
                    pretty = false,
                    defaultFlowStyle = DumperOptions.FlowStyle.FLOW
                )
            )
        }
    }
}

/**
 * Exception thrown when YAML serialization fails
 */
class YAMLSerializeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Extension function for UDM to serialize as YAML
 */
fun UDM.toYAML(pretty: Boolean = true): String {
    return YAMLSerializer.toYAML(this, pretty)
}

/**
 * Extension function for UDM to serialize as compact YAML
 */
fun UDM.toCompactYAML(): String {
    return YAMLSerializer.toCompactYAML(this)
}

/**
 * Extension function for UDM to serialize to OutputStream
 */
fun UDM.toYAML(
    output: OutputStream,
    options: YAMLSerializer.SerializeOptions = YAMLSerializer.SerializeOptions()
) {
    YAMLSerializer().serialize(this, output, options)
}
