package org.apache.utlx.formats.tsch

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.json.JSONParser
import java.io.Reader
import java.io.StringReader

/**
 * Table Schema Parser - Converts Frictionless Table Schema JSON to UDM
 *
 * Supports:
 * - All 15 field types (string, integer, number, boolean, date, time, datetime,
 *   year, yearmonth, duration, geopoint, geojson, object, array, any)
 * - Field constraints (required, unique, enum, pattern, minimum, maximum, minLength, maxLength)
 * - Primary keys (single and composite)
 * - Foreign keys (including self-referencing)
 * - Missing values
 * - Field descriptor properties (rdfType, trueValues, falseValues, decimalChar, groupChar, bareNumber)
 *
 * Mapping:
 * - Table Schema → UDM.Object with __schemaType metadata
 * - Fields → tagged with __schemaType: "tsch-field"
 *
 * Example:
 * {
 *   "fields": [
 *     {"name": "id", "type": "integer", "constraints": {"required": true}},
 *     {"name": "name", "type": "string"}
 *   ],
 *   "primaryKey": "id"
 * }
 *
 * Becomes:
 * UDM.Object(
 *   properties = mapOf(
 *     "fields" to UDM.Array([...]),
 *     "primaryKey" to UDM.Scalar("id")
 *   ),
 *   metadata = mapOf("__schemaType" to "tsch-schema")
 * )
 */
class TableSchemaParser(private val source: Reader) {
    constructor(tableSchema: String) : this(StringReader(tableSchema))

    /**
     * Parse Table Schema to UDM
     */
    fun parse(): UDM {
        // Step 1: Parse as JSON
        val jsonParser = JSONParser(source)
        val jsonUDM = jsonParser.parse()

        // Step 2: Enhance with Table Schema-specific metadata
        return enhanceWithTableSchemaMetadata(jsonUDM)
    }

    /**
     * Enhance JSON UDM with Table Schema-specific metadata
     * - Tag root as tsch-schema
     * - Tag individual fields as tsch-field
     */
    private fun enhanceWithTableSchemaMetadata(udm: UDM): UDM {
        return when (udm) {
            is UDM.Object -> {
                // Check if this is a Table Schema (has "fields" array)
                val hasFields = udm.properties.containsKey("fields")

                if (hasFields) {
                    enhanceRootSchema(udm)
                } else {
                    // Recursively enhance nested objects
                    UDM.Object(
                        properties = udm.properties.mapValues { (_, value) ->
                            enhanceWithTableSchemaMetadata(value)
                        },
                        attributes = udm.attributes,
                        name = udm.name,
                        metadata = udm.metadata
                    )
                }
            }
            is UDM.Array -> {
                UDM.Array(udm.elements.map { enhanceWithTableSchemaMetadata(it) })
            }
            else -> udm
        }
    }

    /**
     * Enhance root Table Schema object
     */
    private fun enhanceRootSchema(schema: UDM.Object): UDM.Object {
        // Enhance all properties
        val enhancedProperties = schema.properties.mapValues { (key, value) ->
            when (key) {
                "fields" -> tagFields(value)
                else -> value
            }
        }

        // Build metadata
        val fullMetadata = schema.metadata + mapOf(
            TableSchemaMetadata.SCHEMA_TYPE to TableSchemaMetadata.TSCH_SCHEMA
        )

        return UDM.Object(
            properties = enhancedProperties,
            attributes = schema.attributes,
            name = schema.name,
            metadata = fullMetadata
        )
    }

    /**
     * Tag fields array - each field object gets tsch-field metadata
     */
    private fun tagFields(udm: UDM): UDM {
        return when (udm) {
            is UDM.Array -> {
                UDM.Array(udm.elements.map { element ->
                    when (element) {
                        is UDM.Object -> element.copy(
                            metadata = element.metadata + mapOf(
                                TableSchemaMetadata.SCHEMA_TYPE to TableSchemaMetadata.TSCH_FIELD
                            )
                        )
                        else -> element
                    }
                })
            }
            else -> udm
        }
    }

    /**
     * Convert Table Schema to USDL format
     *
     * This is the inverse of TableSchemaSerializer.transformUniversalDSL()
     * Converts Table Schema structure → USDL with % directives
     *
     * Mapping:
     * - fields → %types with single structure type named "Record"
     * - field.name → %name
     * - field.type → %type (via TableSchemaTypeMapping.toUtlxType)
     * - field.description → %description
     * - field.constraints.required → %required
     * - field.constraints.enum → preserved as metadata
     * - primaryKey → %key
     * - foreignKeys → %foreignKeys
     */
    fun toUSDL(tableSchema: UDM): UDM {
        if (tableSchema !is UDM.Object) {
            throw IllegalArgumentException("Table Schema must be an object")
        }

        // Extract fields array
        val fieldsArray = tableSchema.properties["fields"] as? UDM.Array
            ?: throw IllegalArgumentException("Table Schema must have a 'fields' array")

        // Extract primaryKey
        val primaryKey = extractPrimaryKey(tableSchema)

        // Extract foreignKeys
        val foreignKeys = tableSchema.properties["foreignKeys"]

        // Convert fields to USDL fields
        val usdlFields = mutableListOf<UDM>()
        fieldsArray.elements.forEach { fieldUdm ->
            if (fieldUdm is UDM.Object) {
                usdlFields.add(fieldToUSDL(fieldUdm, primaryKey))
            }
        }

        // Build structure type
        val structureProps = mutableMapOf<String, UDM>(
            "%kind" to UDM.Scalar("structure"),
            "%fields" to UDM.Array(usdlFields)
        )

        // Add primary key info
        if (primaryKey.isNotEmpty()) {
            structureProps["%key"] = if (primaryKey.size == 1) {
                UDM.Scalar(primaryKey[0])
            } else {
                UDM.Array(primaryKey.map { UDM.Scalar(it) })
            }
        }

        // Add foreign keys
        if (foreignKeys != null) {
            structureProps["%foreignKeys"] = foreignKeys
        }

        // Build %types
        val typesMap = mapOf("Record" to UDM.Object(properties = structureProps))

        // Build top-level USDL object
        val topLevelProps = mutableMapOf<String, UDM>(
            "%types" to UDM.Object(properties = typesMap)
        )

        return UDM.Object(properties = topLevelProps)
    }

    /**
     * Extract primaryKey as a list of field names
     */
    private fun extractPrimaryKey(schema: UDM.Object): List<String> {
        val pk = schema.properties["primaryKey"] ?: return emptyList()

        return when (pk) {
            is UDM.Scalar -> listOf(pk.value.toString())
            is UDM.Array -> pk.elements.mapNotNull { (it as? UDM.Scalar)?.value?.toString() }
            else -> emptyList()
        }
    }

    /**
     * Convert a Table Schema field to a USDL field
     */
    private fun fieldToUSDL(field: UDM.Object, primaryKey: List<String>): UDM {
        val name = (field.properties["name"] as? UDM.Scalar)?.value?.toString() ?: ""
        val type = (field.properties["type"] as? UDM.Scalar)?.value?.toString() ?: "string"
        val description = (field.properties["description"] as? UDM.Scalar)?.value?.toString()

        // Extract constraints
        val constraints = field.properties["constraints"] as? UDM.Object
        val isRequired = (constraints?.properties?.get("required") as? UDM.Scalar)?.value as? Boolean ?: false
        val enumValues = constraints?.properties?.get("enum")

        val fieldProps = mutableMapOf<String, UDM>(
            "%name" to UDM.Scalar(name),
            "%type" to UDM.Scalar(TableSchemaTypeMapping.toUtlxType(type))
        )

        if (isRequired) {
            fieldProps["%required"] = UDM.Scalar(true)
        }

        if (description != null) {
            fieldProps["%description"] = UDM.Scalar(description)
        }

        if (enumValues != null) {
            fieldProps["%enum"] = enumValues
        }

        return UDM.Object(properties = fieldProps)
    }
}
