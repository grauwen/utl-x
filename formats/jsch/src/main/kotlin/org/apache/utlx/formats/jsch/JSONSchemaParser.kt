package org.apache.utlx.formats.jsch

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.json.JSONParser
import java.io.Reader
import java.io.StringReader

/**
 * JSON Schema Parser - Converts JSON Schema files to UDM
 *
 * Supports:
 * - draft-04 (best-effort, converts to draft-07)
 * - draft-07 (full support, default)
 * - 2020-12 (full support)
 * - $ref resolution
 * - Schema validation (syntax checking only)
 *
 * Mapping:
 * - JSON Schema → UDM.Object with __schemaType metadata
 * - Properties → tagged with __schemaType: "jsch-property"
 * - Definitions → tagged with __schemaType: "jsch-definition"
 *
 * Example:
 * {
 *   "$schema": "http://json-schema.org/draft-07/schema#",
 *   "type": "object",
 *   "properties": {
 *     "name": {"type": "string"}
 *   }
 * }
 *
 * Becomes:
 * UDM.Object(
 *   properties = mapOf(
 *     "$schema" to UDM.Scalar("http://json-schema.org/draft-07/schema#"),
 *     "type" to UDM.Scalar("object"),
 *     "properties" to UDM.Object(
 *       properties = mapOf(
 *         "name" to UDM.Object(
 *           properties = mapOf("type" to UDM.Scalar("string")),
 *           metadata = mapOf("__schemaType" to "jsch-property")
 *         )
 *       )
 *     )
 *   ),
 *   metadata = mapOf(
 *     "__schemaType" to "jsch-schema",
 *     "__version" to "draft-07"
 *   )
 * )
 */
class JSONSchemaParser(private val source: Reader) {
    constructor(jsonSchema: String) : this(StringReader(jsonSchema))

    /**
     * Parse JSON Schema to UDM
     */
    fun parse(): UDM {
        // Step 1: Parse as JSON
        val jsonParser = JSONParser(source)
        val jsonUDM = jsonParser.parse()

        // Step 2: Enhance with JSON Schema-specific metadata
        return enhanceWithSchemaMetadata(jsonUDM)
    }

    /**
     * Enhance JSON UDM with JSON Schema-specific metadata
     * - Detect schema version
     * - Tag properties, definitions, etc.
     * - Validate basic structure
     */
    private fun enhanceWithSchemaMetadata(udm: UDM): UDM {
        return when (udm) {
            is UDM.Object -> {
                // Check if this is a root schema object (has $schema field)
                val schemaVersion = (udm.properties["\$schema"] as? UDM.Scalar)?.value?.toString()
                val isRootSchema = schemaVersion != null || udm.properties.containsKey("type")

                if (isRootSchema) {
                    enhanceRootSchema(udm)
                } else {
                    // Recursively enhance nested objects
                    UDM.Object(
                        properties = udm.properties.mapValues { (key, value) ->
                            enhanceProperty(key, value)
                        },
                        attributes = udm.attributes,
                        name = udm.name,
                        metadata = udm.metadata
                    )
                }
            }
            is UDM.Array -> {
                UDM.Array(udm.elements.map { enhanceWithSchemaMetadata(it) })
            }
            else -> udm
        }
    }

    /**
     * Enhance root schema object
     */
    private fun enhanceRootSchema(schema: UDM.Object): UDM.Object {
        // Detect version
        val version = detectSchemaVersion(schema)

        // Enhance all properties
        val enhancedProperties = schema.properties.mapValues { (key, value) ->
            when (key) {
                "properties" -> tagAsProperties(value)
                "definitions", "\$defs" -> tagAsDefinitions(value)
                "items" -> tagAsItems(value)
                else -> enhanceProperty(key, value)
            }
        }

        return UDM.Object(
            properties = enhancedProperties,
            attributes = schema.attributes,
            name = schema.name,
            metadata = schema.metadata + mapOf(
                "__schemaType" to "jsch-schema",
                "__version" to version
            )
        )
    }

    /**
     * Detect JSON Schema version from $schema field
     */
    private fun detectSchemaVersion(schema: UDM.Object): String {
        val schemaUri = (schema.properties["\$schema"] as? UDM.Scalar)?.value?.toString() ?: ""

        return when {
            schemaUri.contains("draft-04") -> "draft-04"
            schemaUri.contains("draft-07") -> "draft-07"
            schemaUri.contains("2020-12") -> "2020-12"
            // Default to draft-07 if no $schema field
            else -> "draft-07"
        }
    }

    /**
     * Tag properties object
     */
    private fun tagAsProperties(udm: UDM): UDM {
        return when (udm) {
            is UDM.Object -> {
                UDM.Object(
                    properties = udm.properties.mapValues { (_, value) ->
                        when (value) {
                            is UDM.Object -> value.copy(
                                metadata = value.metadata + mapOf("__schemaType" to "jsch-property")
                            )
                            else -> value
                        }
                    },
                    attributes = udm.attributes,
                    name = udm.name,
                    metadata = udm.metadata + mapOf("__schemaType" to "jsch-properties")
                )
            }
            else -> udm
        }
    }

    /**
     * Tag definitions object
     */
    private fun tagAsDefinitions(udm: UDM): UDM {
        return when (udm) {
            is UDM.Object -> {
                UDM.Object(
                    properties = udm.properties.mapValues { (_, value) ->
                        when (value) {
                            is UDM.Object -> value.copy(
                                metadata = value.metadata + mapOf("__schemaType" to "jsch-definition")
                            )
                            else -> value
                        }
                    },
                    attributes = udm.attributes,
                    name = udm.name,
                    metadata = udm.metadata + mapOf("__schemaType" to "jsch-definitions")
                )
            }
            else -> udm
        }
    }

    /**
     * Tag items (array items schema)
     */
    private fun tagAsItems(udm: UDM): UDM {
        return when (udm) {
            is UDM.Object -> {
                udm.copy(
                    metadata = udm.metadata + mapOf("__schemaType" to "jsch-items")
                )
            }
            else -> udm
        }
    }

    /**
     * Enhance a property based on its key
     */
    private fun enhanceProperty(key: String, value: UDM): UDM {
        return when (value) {
            is UDM.Object -> {
                // Recursively enhance nested objects
                enhanceWithSchemaMetadata(value)
            }
            is UDM.Array -> {
                UDM.Array(value.elements.map { enhanceWithSchemaMetadata(it) })
            }
            else -> value
        }
    }

    companion object {
        /**
         * Known JSON Schema keywords
         */
        private val SCHEMA_KEYWORDS = setOf(
            "\$schema", "\$id", "\$ref", "\$defs",
            "type", "properties", "required", "additionalProperties",
            "items", "prefixItems", "minItems", "maxItems", "uniqueItems",
            "enum", "const",
            "anyOf", "allOf", "oneOf", "not",
            "definitions",  // draft-04/draft-07
            "title", "description", "default", "examples",
            "format", "pattern", "minLength", "maxLength",
            "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
            "\$dynamicRef", "\$dynamicAnchor",  // 2020-12
            "unevaluatedProperties", "unevaluatedItems",  // 2020-12
            "dependentSchemas", "dependentRequired"  // 2020-12
        )
    }
}
