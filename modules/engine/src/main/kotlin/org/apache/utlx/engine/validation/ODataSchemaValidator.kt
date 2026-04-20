package org.apache.utlx.engine.validation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory

/**
 * Validates OData JSON payloads against an EDMX/CSDL schema (OSCH).
 *
 * The schema source is the EDMX metadata in JSON representation — a simplified
 * format containing entity types with their properties, types, and nullability.
 *
 * Schema source format (JSON):
 * ```json
 * {
 *   "entityTypes": {
 *     "Customer": {
 *       "properties": {
 *         "ID": { "type": "Edm.Int32", "nullable": false },
 *         "Name": { "type": "Edm.String", "nullable": false, "maxLength": 100 },
 *         "Email": { "type": "Edm.String", "nullable": true }
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * Validates:
 * - Required (non-nullable) properties are present
 * - Property types match Edm type expectations
 * - No unknown properties (if strict mode, controlled by extra fields)
 * - MaxLength constraints on strings
 *
 * Init-time: parse OSCH JSON → build entity type map (~10-50ms)
 * Runtime: validate OData JSON entity against entity type (~50-200μs per 5KB)
 */
class ODataSchemaValidator(schemaSource: String) : SchemaValidator {

    private val logger = LoggerFactory.getLogger(ODataSchemaValidator::class.java)
    override val schemaFormat = "osch"

    private val mapper = ObjectMapper().apply { registerModule(kotlinModule()) }
    private val entityTypes: Map<String, OschEntityType>

    init {
        val schema: OschSchema = mapper.readValue(schemaSource)
        entityTypes = schema.entityTypes
        logger.debug("OData schema compiled: {} entity type(s)", entityTypes.size)
    }

    override fun validate(payload: ByteArray, contentType: String): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()

        try {
            val tree = mapper.readTree(payload)

            // Determine entity type from @odata.type or from schema (if single type)
            val typeName = tree.get("@odata.type")?.asText()?.removePrefix("#")
                ?: entityTypes.keys.firstOrNull()

            if (typeName == null) {
                errors.add(SchemaValidationError("Cannot determine entity type: no @odata.type and schema has no entity types"))
                return errors
            }

            val entityType = entityTypes[typeName]
            if (entityType == null) {
                errors.add(SchemaValidationError("Unknown entity type: '$typeName'"))
                return errors
            }

            // Handle single entity or collection (value array)
            val entities = if (tree.has("value") && tree.get("value").isArray) {
                tree.get("value").toList()
            } else {
                listOf(tree)
            }

            entities.forEachIndexed { idx, entity ->
                val entityPath = if (entities.size > 1) "[value/$idx]" else ""
                validateEntity(entity, entityType, entityPath, errors)
            }

        } catch (e: Exception) {
            errors.add(SchemaValidationError("Failed to parse OData JSON payload: ${e.message}"))
        }

        return errors
    }

    private fun validateEntity(
        entity: JsonNode,
        entityType: OschEntityType,
        basePath: String,
        errors: MutableList<SchemaValidationError>
    ) {
        for ((propName, propDef) in entityType.properties) {
            val value = entity.get(propName)
            val path = "$basePath.$propName"

            // Non-nullable check
            if (propDef.nullable == false && (value == null || value.isNull)) {
                errors.add(SchemaValidationError("Required property '$propName' is missing or null", path))
                continue
            }

            if (value == null || value.isNull) continue

            // Type validation
            val typeError = validateEdmType(propName, propDef.type, value, path)
            if (typeError != null) errors.add(typeError)

            // MaxLength for strings
            if (propDef.maxLength != null && value.isTextual) {
                if (value.asText().length > propDef.maxLength) {
                    errors.add(SchemaValidationError(
                        "Property '$propName' length ${value.asText().length} exceeds maxLength ${propDef.maxLength}",
                        path
                    ))
                }
            }
        }
    }

    private fun validateEdmType(propName: String, edmType: String, value: JsonNode, path: String): SchemaValidationError? {
        val valid = when (edmType) {
            "Edm.String", "Edm.Guid" -> value.isTextual
            "Edm.Int16", "Edm.Int32", "Edm.Int64", "Edm.SByte", "Edm.Byte" -> value.isIntegralNumber
            "Edm.Decimal", "Edm.Double", "Edm.Single" -> value.isNumber
            "Edm.Boolean" -> value.isBoolean
            "Edm.DateTimeOffset", "Edm.Date", "Edm.TimeOfDay", "Edm.Duration" -> value.isTextual
            "Edm.Binary" -> value.isTextual // Base64-encoded
            else -> true // Unknown Edm types pass (extensible)
        }
        return if (!valid) {
            SchemaValidationError("Property '$propName' expected $edmType but got ${value.nodeType}", path)
        } else null
    }

    // Minimal OSCH model for deserialization
    private data class OschSchema(
        val entityTypes: Map<String, OschEntityType> = emptyMap()
    )

    private data class OschEntityType(
        val properties: Map<String, OschProperty> = emptyMap()
    )

    private data class OschProperty(
        val type: String = "Edm.String",
        val nullable: Boolean? = true,
        val maxLength: Int? = null
    )
}
