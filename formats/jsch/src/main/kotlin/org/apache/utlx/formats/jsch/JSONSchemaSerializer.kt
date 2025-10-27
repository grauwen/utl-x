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
     * Serialization modes
     */
    private enum class SerializationMode {
        LOW_LEVEL,      // User provides JSON Schema structure
        UNIVERSAL_DSL   // User provides Universal Schema DSL
    }

    /**
     * Serialize UDM to JSON Schema via Writer
     */
    fun serialize(udm: UDM, writer: Writer) {
        // Step 1: Detect mode and transform if needed
        val mode = detectMode(udm)
        val schemaStructure = when (mode) {
            SerializationMode.UNIVERSAL_DSL -> transformUniversalDSL(udm as UDM.Object)
            SerializationMode.LOW_LEVEL -> udm
        }

        // Step 2: Validate UDM represents valid JSON Schema structure
        if (strict) {
            validateJSONSchemaStructure(schemaStructure)
        }

        // Step 3: Inject descriptions if requested
        val enhanced = if (addDescriptions) {
            injectDescriptions(schemaStructure)
        } else {
            schemaStructure
        }

        // Step 4: Add $schema if not present
        val final = ensureSchemaDeclaration(enhanced)

        // Step 5: Serialize using JSON serializer
        val jsonSerializer = JSONSerializer(prettyPrint)
        writer.write(jsonSerializer.serialize(final))
    }

    /**
     * Detect serialization mode based on UDM structure
     */
    private fun detectMode(udm: UDM): SerializationMode {
        return when (udm) {
            is UDM.Object -> {
                when {
                    // Low-level: Has JSON Schema keywords
                    udm.properties.containsKey("type") -> SerializationMode.LOW_LEVEL
                    udm.properties.containsKey("properties") -> SerializationMode.LOW_LEVEL
                    udm.properties.containsKey("\$schema") -> SerializationMode.LOW_LEVEL

                    // USDL mode: Has %types directive
                    udm.properties.containsKey("%types") -> SerializationMode.UNIVERSAL_DSL

                    // Default: Low-level
                    else -> SerializationMode.LOW_LEVEL
                }
            }
            else -> SerializationMode.LOW_LEVEL
        }
    }

    /**
     * Transform USDL (Universal Schema Definition Language) to JSON Schema UDM structure
     *
     * Supports USDL 1.0 Tier 1 and Tier 2 directives for JSON Schema generation.
     *
     * Required USDL directives:
     * - %types: Object mapping type names to type definitions
     * - %kind: "structure" for objects, "enumeration" for enums
     * - %fields: Array of field definitions (for structures)
     * - %name: Field name
     * - %type: Field type
     *
     * Optional USDL directives:
     * - %title: Schema title
     * - %documentation: Type-level description
     * - %description: Field-level description
     * - %required: Boolean indicating if field is required
     * - %default: Default value
     */
    private fun transformUniversalDSL(schema: UDM.Object): UDM {
        // Extract metadata using USDL % directives
        val title = (schema.properties["%title"] as? UDM.Scalar)?.value as? String
        val description = (schema.properties["%documentation"] as? UDM.Scalar)?.value as? String

        // Extract types using %types directive
        val types = schema.properties["%types"] as? UDM.Object
            ?: throw IllegalArgumentException("USDL schema requires '%types' directive")

        // Build JSON Schema definitions
        val definitions = mutableMapOf<String, UDM>()

        types.properties.forEach { (typeName, typeDef) ->
            if (typeDef !is UDM.Object) return@forEach

            // Check %kind directive
            val kind = (typeDef.properties["%kind"] as? UDM.Scalar)?.value as? String

            when (kind) {
                "structure" -> {
                    // Extract %fields directive
                    val fields = typeDef.properties["%fields"] as? UDM.Array ?: return@forEach

                    // Extract %documentation directive
                    val typeDoc = (typeDef.properties["%documentation"] as? UDM.Scalar)?.value as? String

                    // Build properties object and required array
                    val properties = mutableMapOf<String, UDM>()
                    val requiredFields = mutableListOf<String>()

                    fields.elements.forEach { fieldUdm ->
                        if (fieldUdm !is UDM.Object) return@forEach

                        // Extract field directives
                        val name = (fieldUdm.properties["%name"] as? UDM.Scalar)?.value as? String ?: return@forEach
                        val type = (fieldUdm.properties["%type"] as? UDM.Scalar)?.value as? String ?: return@forEach
                        val required = (fieldUdm.properties["%required"] as? UDM.Scalar)?.value as? Boolean ?: false
                        val fieldDesc = (fieldUdm.properties["%description"] as? UDM.Scalar)?.value as? String

                        // Map USDL types to JSON Schema types
                        val jsonSchemaType = mapUSDLTypeToJSONSchema(type)

                        val fieldProps = mutableMapOf<String, UDM>("type" to UDM.Scalar(jsonSchemaType))
                        if (fieldDesc != null) {
                            fieldProps["_description"] = UDM.Scalar(fieldDesc)
                        }

                        properties[name] = UDM.Object(properties = fieldProps)

                        if (required) {
                            requiredFields.add(name)
                        }
                    }

                    // Build type definition
                    val typeDefProps = mutableMapOf<String, UDM>(
                        "type" to UDM.Scalar("object"),
                        "properties" to UDM.Object(properties = properties)
                    )

                    if (requiredFields.isNotEmpty()) {
                        typeDefProps["required"] = UDM.Array(requiredFields.map { UDM.Scalar(it) })
                    }

                    if (typeDoc != null) {
                        typeDefProps["_description"] = UDM.Scalar(typeDoc)
                    }

                    definitions[typeName] = UDM.Object(properties = typeDefProps)
                }

                "enumeration" -> {
                    // Extract %values directive
                    val values = typeDef.properties["%values"] as? UDM.Array
                    val typeDoc = (typeDef.properties["%documentation"] as? UDM.Scalar)?.value as? String

                    val enumProps = mutableMapOf<String, UDM>("type" to UDM.Scalar("string"))

                    if (values != null) {
                        enumProps["enum"] = values
                    }

                    if (typeDoc != null) {
                        enumProps["_description"] = UDM.Scalar(typeDoc)
                    }

                    definitions[typeName] = UDM.Object(properties = enumProps)
                }
            }
        }

        // Build root schema
        val rootProps = mutableMapOf<String, UDM>()

        if (title != null) {
            rootProps["title"] = UDM.Scalar(title)
        }

        if (description != null) {
            rootProps["_description"] = UDM.Scalar(description)
        }

        if (definitions.isNotEmpty()) {
            rootProps["\$defs"] = UDM.Object(properties = definitions)
        }

        return UDM.Object(properties = rootProps)
    }

    /**
     * Map USDL type to JSON Schema type
     */
    private fun mapUSDLTypeToJSONSchema(usdlType: String): String {
        return when (usdlType) {
            "string" -> "string"
            "number" -> "number"
            "integer" -> "integer"
            "boolean" -> "boolean"
            "array" -> "array"
            "object" -> "object"
            "null" -> "null"
            else -> "string" // Default to string for unknown types
        }
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
