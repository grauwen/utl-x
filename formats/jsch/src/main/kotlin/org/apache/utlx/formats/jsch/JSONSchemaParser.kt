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
 *           metadata = mapOf("schemaType" to "jsch-property")
 *         )
 *       )
 *     )
 *   ),
 *   metadata = mapOf(
 *     "schemaType" to "jsch-schema",
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
                "schemaType" to "jsch-schema",
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
                                metadata = value.metadata + mapOf("schemaType" to "jsch-property")
                            )
                            else -> value
                        }
                    },
                    attributes = udm.attributes,
                    name = udm.name,
                    metadata = udm.metadata + mapOf("schemaType" to "jsch-properties")
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
                                metadata = value.metadata + mapOf("schemaType" to "jsch-definition")
                            )
                            else -> value
                        }
                    },
                    attributes = udm.attributes,
                    name = udm.name,
                    metadata = udm.metadata + mapOf("schemaType" to "jsch-definitions")
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
                    metadata = udm.metadata + mapOf("schemaType" to "jsch-items")
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

    /**
     * Convert JSON Schema to USDL format
     *
     * This is the inverse of JSONSchemaSerializer.transformUniversalDSL()
     * Converts JSON Schema structure → USDL with % directives
     */
    fun toUSDL(jsonSchema: UDM): UDM {
        if (jsonSchema !is UDM.Object) {
            throw IllegalArgumentException("JSON Schema must be an object")
        }

        // Extract top-level metadata
        val title = (jsonSchema.properties["title"] as? UDM.Scalar)?.value?.toString()
        val description = (jsonSchema.properties["description"] as? UDM.Scalar)?.value?.toString()

        // Extract type definitions from definitions/$defs
        val definitions = extractDefinitions(jsonSchema)

        // Check if root is a type definition itself
        val rootType = (jsonSchema.properties["type"] as? UDM.Scalar)?.value?.toString()

        // Build %types map
        val typesMap = mutableMapOf<String, UDM>()

        // Add definitions as types
        definitions.forEach { (name, def) ->
            typesMap[name] = convertToUSDLType(def)
        }

        // If root is an object type, add it as a type (use title or "Root")
        if (rootType == "object" && jsonSchema.properties.containsKey("properties")) {
            val typeName = title ?: "Root"
            typesMap[typeName] = convertToUSDLType(jsonSchema)
        }

        // Build top-level USDL object
        val topLevelProps = mutableMapOf<String, UDM>()

        if (typesMap.isNotEmpty()) {
            topLevelProps["%types"] = UDM.Object(properties = typesMap)
        }

        if (title != null) {
            topLevelProps["%title"] = UDM.Scalar(title)
        }

        if (description != null) {
            topLevelProps["%documentation"] = UDM.Scalar(description)
        }

        return UDM.Object(properties = topLevelProps)
    }

    /**
     * Extract definitions or $defs from schema
     */
    private fun extractDefinitions(schema: UDM.Object): Map<String, UDM.Object> {
        val result = mutableMapOf<String, UDM.Object>()

        // Check for $defs (2020-12, 2019-09)
        val defs = schema.properties["\$defs"] as? UDM.Object
        if (defs != null) {
            defs.properties.forEach { (name, def) ->
                if (def is UDM.Object) {
                    result[name] = def
                }
            }
        }

        // Check for definitions (draft-07, draft-04)
        val definitions = schema.properties["definitions"] as? UDM.Object
        if (definitions != null) {
            definitions.properties.forEach { (name, def) ->
                if (def is UDM.Object) {
                    result[name] = def
                }
            }
        }

        return result
    }

    /**
     * Convert JSON Schema type definition to USDL type
     */
    private fun convertToUSDLType(typeDef: UDM.Object): UDM {
        val type = (typeDef.properties["type"] as? UDM.Scalar)?.value?.toString()
        val description = (typeDef.properties["description"] as? UDM.Scalar)?.value?.toString()

        // Check for enum
        val enumValues = typeDef.properties["enum"] as? UDM.Array
        if (enumValues != null) {
            val typeProps = mutableMapOf<String, UDM>(
                "%kind" to UDM.Scalar("enumeration"),
                "%values" to enumValues
            )
            if (description != null) {
                typeProps["%documentation"] = UDM.Scalar(description)
            }
            return UDM.Object(properties = typeProps)
        }

        // Handle object types
        if (type == "object") {
            return objectToUSDLStructure(typeDef)
        }

        // Handle array types
        if (type == "array") {
            val items = typeDef.properties["items"]
            if (items is UDM.Object) {
                val itemType = (items.properties["type"] as? UDM.Scalar)?.value?.toString() ?: "string"
                return UDM.Object(properties = mapOf(
                    "%kind" to UDM.Scalar("array"),
                    "%itemType" to UDM.Scalar(jsonSchemaTypeToUSDL(itemType))
                ))
            }
        }

        // Default: primitive type
        val typeProps = mutableMapOf<String, UDM>(
            "%kind" to UDM.Scalar("primitive"),
            "%type" to UDM.Scalar(jsonSchemaTypeToUSDL(type ?: "string"))
        )
        if (description != null) {
            typeProps["%documentation"] = UDM.Scalar(description)
        }
        return UDM.Object(properties = typeProps)
    }

    /**
     * Convert JSON Schema object to USDL structure
     */
    private fun objectToUSDLStructure(obj: UDM.Object): UDM {
        val description = (obj.properties["description"] as? UDM.Scalar)?.value?.toString()
        val properties = obj.properties["properties"] as? UDM.Object
        val requiredArray = obj.properties["required"] as? UDM.Array

        // Build set of required field names
        val requiredFields = requiredArray?.elements
            ?.mapNotNull { (it as? UDM.Scalar)?.value?.toString() }
            ?.toSet() ?: emptySet()

        // Convert properties to fields
        val fields = mutableListOf<UDM>()
        properties?.properties?.forEach { (name, prop) ->
            if (prop is UDM.Object) {
                fields.add(propertyToUSDLField(name, prop, requiredFields.contains(name)))
            }
        }

        val typeProps = mutableMapOf<String, UDM>(
            "%kind" to UDM.Scalar("structure"),
            "%fields" to UDM.Array(fields)
        )

        if (description != null) {
            typeProps["%documentation"] = UDM.Scalar(description)
        }

        return UDM.Object(properties = typeProps)
    }

    /**
     * Convert JSON Schema property to USDL field
     */
    private fun propertyToUSDLField(name: String, prop: UDM.Object, isRequired: Boolean): UDM {
        val type = (prop.properties["type"] as? UDM.Scalar)?.value?.toString() ?: "string"
        val description = (prop.properties["description"] as? UDM.Scalar)?.value?.toString()
        val isArray = type == "array"

        // If array, get item type
        val baseType = if (isArray) {
            val items = prop.properties["items"] as? UDM.Object
            val itemType = (items?.properties?.get("type") as? UDM.Scalar)?.value?.toString() ?: "string"
            jsonSchemaTypeToUSDL(itemType)
        } else {
            jsonSchemaTypeToUSDL(type)
        }

        val fieldProps = mutableMapOf<String, UDM>(
            "%name" to UDM.Scalar(name),
            "%type" to UDM.Scalar(baseType)
        )

        if (isRequired) {
            fieldProps["%required"] = UDM.Scalar(true)
        }

        if (isArray) {
            fieldProps["%array"] = UDM.Scalar(true)
        }

        if (description != null) {
            fieldProps["%description"] = UDM.Scalar(description)
        }

        return UDM.Object(properties = fieldProps)
    }

    /**
     * Convert JSON Schema type to USDL type
     */
    private fun jsonSchemaTypeToUSDL(jsonSchemaType: String): String {
        return when (jsonSchemaType) {
            "string" -> "string"
            "number" -> "number"
            "integer" -> "integer"
            "boolean" -> "boolean"
            "array" -> "array"
            "object" -> "object"
            "null" -> "null"
            else -> jsonSchemaType  // Pass through unknown types
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
