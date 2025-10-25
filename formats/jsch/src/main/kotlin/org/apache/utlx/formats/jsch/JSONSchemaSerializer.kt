package org.apache.utlx.formats.jsch

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.json.JSONSerializer
import java.io.Writer
import java.io.StringWriter

/**
 * JSON Schema Serializer - Converts UDM to JSON Schema
 *
 * Supports:
 * - Draft-07 (most widely supported)
 * - 2019-09 (adds if/then/else, $vocabulary)
 * - 2020-12 (latest stable, adds prefixItems, $dynamicRef)
 *
 * Features:
 * - Automatic description injection from _description properties
 * - Schema validation
 * - $schema and $id handling
 * - Proper type inference
 */
class JSONSchemaSerializer(
    private val draft: String = "2020-12",
    private val addDescriptions: Boolean = true,
    private val prettyPrint: Boolean = true,
    private val strict: Boolean = true
) {

    companion object {
        private val VALID_DRAFTS = setOf(
            "draft-07",
            "2019-09",
            "2020-12"
        )

        private val SCHEMA_URIS = mapOf(
            "draft-07" to "http://json-schema.org/draft-07/schema#",
            "2019-09" to "https://json-schema.org/draft/2019-09/schema",
            "2020-12" to "https://json-schema.org/draft/2020-12/schema"
        )
    }

    init {
        require(draft in VALID_DRAFTS) {
            "Unsupported JSON Schema draft: $draft. Supported: ${VALID_DRAFTS.joinToString()}"
        }
    }

    /**
     * Serialize UDM to JSON Schema string
     */
    fun serialize(udm: UDM): String {
        val writer = StringWriter()
        serialize(udm, writer)
        return writer.toString()
    }

    /**
     * Serialize UDM to JSON Schema via Writer
     */
    fun serialize(udm: UDM, writer: Writer) {
        // Step 1: Validate UDM represents valid JSON Schema structure
        if (strict) {
            validateJSONSchemaStructure(udm)
        }

        // Step 2: Inject descriptions if requested
        val enhanced = if (addDescriptions) {
            injectDescriptions(udm)
        } else {
            udm
        }

        // Step 3: Add $schema if not present
        val final = ensureSchemaDeclaration(enhanced)

        // Step 4: Serialize using JSON serializer
        val jsonSerializer = JSONSerializer(prettyPrint)
        writer.write(jsonSerializer.serialize(final))
    }

    /**
     * Validate that UDM represents valid JSON Schema structure
     */
    private fun validateJSONSchemaStructure(udm: UDM) {
        when (udm) {
            is UDM.Object -> {
                // JSON Schema must have a type or be a boolean schema
                val hasType = udm.properties.containsKey("type")
                val hasProperties = udm.properties.containsKey("properties")
                val hasItems = udm.properties.containsKey("items")
                val hasRef = udm.properties.containsKey("\$ref")
                val hasAnyOf = udm.properties.containsKey("anyOf")
                val hasOneOf = udm.properties.containsKey("oneOf")
                val hasAllOf = udm.properties.containsKey("allOf")

                if (!hasType && !hasProperties && !hasItems && !hasRef &&
                    !hasAnyOf && !hasOneOf && !hasAllOf) {
                    throw IllegalArgumentException(
                        "UDM does not represent valid JSON Schema. " +
                        "Expected at least 'type', 'properties', 'items', '\$ref', or composition keywords. " +
                        "Got properties: ${udm.properties.keys}"
                    )
                }
            }
            is UDM.Scalar -> {
                // Boolean schemas (true/false) are valid in newer drafts
                val value = udm.value
                if (value !is Boolean) {
                    throw IllegalArgumentException(
                        "Scalar JSON Schema must be boolean. Got: ${value?.let { it::class.simpleName } ?: "null"}"
                    )
                }
            }
            else -> throw IllegalArgumentException(
                "UDM must be Object or Boolean Scalar for JSON Schema. Got: ${udm::class.simpleName}"
            )
        }
    }

    /**
     * Inject description fields from _description properties
     */
    private fun injectDescriptions(udm: UDM): UDM {
        return when (udm) {
            is UDM.Object -> {
                // Check if this schema has _description property
                val description = udm.properties["_description"] as? UDM.Scalar

                val enhancedProperties = if (description != null) {
                    // Remove _description and add description
                    val newProps = udm.properties.toMutableMap()
                    newProps.remove("_description")
                    newProps["description"] = description
                    newProps
                } else {
                    udm.properties
                }

                // Recursively process all properties
                UDM.Object(
                    properties = enhancedProperties.mapValues { (_, value) ->
                        injectDescriptions(value)
                    },
                    attributes = udm.attributes,
                    name = udm.name,
                    metadata = udm.metadata
                )
            }
            is UDM.Array -> {
                UDM.Array(udm.elements.map { injectDescriptions(it) })
            }
            else -> udm
        }
    }

    /**
     * Ensure $schema declaration is present
     */
    private fun ensureSchemaDeclaration(udm: UDM): UDM {
        return when (udm) {
            is UDM.Object -> {
                // Check if $schema already exists
                if (udm.properties.containsKey("\$schema")) {
                    return udm
                }

                // Add $schema declaration
                val schemaUri = SCHEMA_URIS[draft]
                    ?: throw IllegalStateException("No URI mapping for draft: $draft")

                UDM.Object(
                    properties = mapOf("\$schema" to UDM.Scalar(schemaUri)) + udm.properties,
                    attributes = udm.attributes,
                    name = udm.name,
                    metadata = udm.metadata
                )
            }
            else -> udm
        }
    }

    /**
     * Validate property types match JSON Schema specification
     */
    private fun validatePropertyTypes(udm: UDM.Object) {
        // Check type field if present
        val typeValue = udm.properties["type"]
        if (typeValue is UDM.Scalar) {
            val validTypes = setOf(
                "null", "boolean", "object", "array", "number", "string", "integer"
            )
            val type = typeValue.value.toString()
            require(type in validTypes) {
                "Invalid JSON Schema type: $type. Valid types: ${validTypes.joinToString()}"
            }
        } else if (typeValue is UDM.Array) {
            // Array of types is also valid
            typeValue.elements.forEach { elem ->
                if (elem is UDM.Scalar) {
                    val validTypes = setOf(
                        "null", "boolean", "object", "array", "number", "string", "integer"
                    )
                    val type = elem.value.toString()
                    require(type in validTypes) {
                        "Invalid JSON Schema type in array: $type"
                    }
                }
            }
        }

        // Recursively validate nested schemas
        udm.properties.forEach { (key, value) ->
            when {
                key == "properties" && value is UDM.Object -> {
                    // Validate each property schema
                    value.properties.values.forEach { propSchema ->
                        if (propSchema is UDM.Object) {
                            validatePropertyTypes(propSchema)
                        }
                    }
                }
                key == "items" && value is UDM.Object -> {
                    validatePropertyTypes(value)
                }
                key in setOf("anyOf", "oneOf", "allOf") && value is UDM.Array -> {
                    value.elements.forEach { elem ->
                        if (elem is UDM.Object) {
                            validatePropertyTypes(elem)
                        }
                    }
                }
            }
        }
    }
}
