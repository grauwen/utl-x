package org.apache.utlx.formats.tsch

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.json.JSONSerializer
import java.io.Writer
import java.io.StringWriter

/**
 * Table Schema Serializer - Converts UDM to Frictionless Table Schema JSON
 *
 * Supports two serialization modes:
 * - LOW_LEVEL: User provides Table Schema JSON structure directly (has "fields" key)
 * - UNIVERSAL_DSL: User provides USDL with % directives (has "%types" key)
 *
 * Features:
 * - Bidirectional type mapping via TableSchemaTypeMapping
 * - Constraint preservation
 * - Primary key and foreign key support
 * - Pretty printing
 */
class TableSchemaSerializer(
    private val prettyPrint: Boolean = true,
    private val strict: Boolean = true
) {

    /**
     * Serialization modes
     */
    private enum class SerializationMode {
        LOW_LEVEL,      // User provides Table Schema structure
        UNIVERSAL_DSL   // User provides Universal Schema DSL
    }

    /**
     * Serialize UDM to Table Schema JSON string
     */
    fun serialize(udm: UDM): String {
        val writer = StringWriter()
        serialize(udm, writer)
        return writer.toString()
    }

    /**
     * Serialize UDM to Table Schema JSON via Writer
     */
    fun serialize(udm: UDM, writer: Writer) {
        // Step 1: Detect mode and transform if needed
        val mode = detectMode(udm)
        val schemaStructure = when (mode) {
            SerializationMode.UNIVERSAL_DSL -> transformUniversalDSL(udm as UDM.Object)
            SerializationMode.LOW_LEVEL -> udm
        }

        // Step 2: Validate structure
        if (strict) {
            validateTableSchemaStructure(schemaStructure)
        }

        // Step 3: Serialize using JSON serializer
        val jsonSerializer = JSONSerializer(prettyPrint)
        writer.write(jsonSerializer.serialize(schemaStructure))
    }

    /**
     * Detect serialization mode based on UDM structure
     */
    private fun detectMode(udm: UDM): SerializationMode {
        return when (udm) {
            is UDM.Object -> {
                when {
                    // Low-level: Has Table Schema keywords
                    udm.properties.containsKey("fields") -> SerializationMode.LOW_LEVEL

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
     * Transform USDL (Universal Schema Definition Language) to Table Schema UDM structure
     *
     * Converts USDL directives to Table Schema JSON:
     * - %types → single structure → fields array
     * - %kind: "structure", %fields → Table Schema fields
     * - %name → field.name
     * - %type → field.type (via TableSchemaTypeMapping.toTableSchemaType)
     * - %required → field.constraints.required
     * - %description → field.description
     * - %key → primaryKey
     * - %foreignKeys → foreignKeys
     */
    private fun transformUniversalDSL(schema: UDM.Object): UDM {
        // Extract types using %types directive
        val types = schema.properties["%types"] as? UDM.Object
            ?: throw IllegalArgumentException("USDL schema requires '%types' directive")

        // Find the first structure type (Table Schema describes a single table)
        var structureDef: UDM.Object? = null
        types.properties.forEach { (_, typeDef) ->
            if (typeDef is UDM.Object) {
                val kind = (typeDef.properties["%kind"] as? UDM.Scalar)?.value as? String
                if (kind == "structure" && structureDef == null) {
                    structureDef = typeDef
                }
            }
        }

        val structure = structureDef
            ?: throw IllegalArgumentException("USDL schema must contain at least one structure type for Table Schema")

        // Extract %fields directive
        val fields = structure.properties["%fields"] as? UDM.Array
            ?: throw IllegalArgumentException("Structure type must have '%fields' directive")

        // Convert fields to Table Schema field descriptors
        val tableSchemaFields = mutableListOf<UDM>()
        val requiredFieldNames = mutableListOf<String>()

        fields.elements.forEach { fieldUdm ->
            if (fieldUdm !is UDM.Object) return@forEach

            val name = (fieldUdm.properties["%name"] as? UDM.Scalar)?.value as? String ?: return@forEach
            val type = (fieldUdm.properties["%type"] as? UDM.Scalar)?.value as? String ?: "string"
            val required = (fieldUdm.properties["%required"] as? UDM.Scalar)?.value as? Boolean ?: false
            val description = (fieldUdm.properties["%description"] as? UDM.Scalar)?.value as? String
            val enumValues = fieldUdm.properties["%enum"]

            // Build field properties
            val fieldProps = mutableMapOf<String, UDM>(
                "name" to UDM.Scalar(name),
                "type" to UDM.Scalar(TableSchemaTypeMapping.toTableSchemaType(type))
            )

            if (description != null) {
                fieldProps["description"] = UDM.Scalar(description)
            }

            // Build constraints
            val constraintProps = mutableMapOf<String, UDM>()
            if (required) {
                constraintProps["required"] = UDM.Scalar(true)
                requiredFieldNames.add(name)
            }
            if (enumValues != null) {
                constraintProps["enum"] = enumValues
            }
            if (constraintProps.isNotEmpty()) {
                fieldProps["constraints"] = UDM.Object(properties = constraintProps)
            }

            tableSchemaFields.add(UDM.Object(properties = fieldProps))
        }

        // Build root Table Schema object
        val rootProps = mutableMapOf<String, UDM>(
            "fields" to UDM.Array(tableSchemaFields)
        )

        // Add primaryKey from %key directive
        val key = structure.properties["%key"]
        if (key != null) {
            rootProps["primaryKey"] = key
        }

        // Add foreignKeys from %foreignKeys directive
        val foreignKeys = structure.properties["%foreignKeys"]
        if (foreignKeys != null) {
            rootProps["foreignKeys"] = foreignKeys
        }

        return UDM.Object(properties = rootProps)
    }

    /**
     * Validate that UDM represents valid Table Schema structure
     */
    private fun validateTableSchemaStructure(udm: UDM) {
        when (udm) {
            is UDM.Object -> {
                // Table Schema must have a "fields" array
                val fields = udm.properties["fields"]
                if (fields == null) {
                    throw IllegalArgumentException(
                        "UDM does not represent valid Table Schema. " +
                        "Expected 'fields' array. " +
                        "Got properties: ${udm.properties.keys}"
                    )
                }
                if (fields !is UDM.Array) {
                    throw IllegalArgumentException(
                        "Table Schema 'fields' must be an array. " +
                        "Got: ${fields::class.simpleName}"
                    )
                }

                // Validate each field has a name
                fields.elements.forEach { field ->
                    if (field is UDM.Object) {
                        if (!field.properties.containsKey("name")) {
                            throw IllegalArgumentException(
                                "Table Schema field must have a 'name' property"
                            )
                        }
                    }
                }
            }
            else -> throw IllegalArgumentException(
                "UDM must be Object for Table Schema. Got: ${udm::class.simpleName}"
            )
        }
    }
}
