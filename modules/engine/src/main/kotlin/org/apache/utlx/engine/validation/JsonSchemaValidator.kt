package org.apache.utlx.engine.validation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.JsonSchema
import org.slf4j.LoggerFactory

/**
 * Validates JSON payloads against a pre-compiled JSON Schema (draft-07 / 2020-12).
 * Uses networknt/json-schema-validator for full spec compliance including $ref resolution.
 *
 * Init-time: parse JSON Schema source → compile validator tree (~10-50ms)
 * Runtime: validate payload bytes against compiled schema (~50-200μs per 5KB JSON)
 */
class JsonSchemaValidator(schemaSource: String) : SchemaValidator {

    private val logger = LoggerFactory.getLogger(JsonSchemaValidator::class.java)
    override val schemaFormat = "json-schema"

    private val objectMapper = ObjectMapper()
    private val compiledSchema: JsonSchema

    init {
        val schemaNode = objectMapper.readTree(schemaSource)
        val version = detectVersion(schemaNode)
        val factory = JsonSchemaFactory.getInstance(version)
        compiledSchema = factory.getSchema(schemaNode)
        logger.debug("JSON Schema compiled ({})", version)
    }

    override fun validate(payload: ByteArray, contentType: String): List<SchemaValidationError> {
        return try {
            val jsonNode = objectMapper.readTree(payload)
            val errors = compiledSchema.validate(jsonNode)
            errors.map { error ->
                SchemaValidationError(
                    message = error.message,
                    path = error.instanceLocation?.toString() ?: error.evaluationPath?.toString(),
                    severity = "ERROR"
                )
            }
        } catch (e: Exception) {
            listOf(
                SchemaValidationError(
                    message = "Failed to parse payload as JSON: ${e.message}",
                    severity = "ERROR"
                )
            )
        }
    }

    private fun detectVersion(schemaNode: JsonNode): SpecVersion.VersionFlag {
        val schemaUri = schemaNode.get("\$schema")?.asText() ?: ""
        return when {
            schemaUri.contains("2020-12") -> SpecVersion.VersionFlag.V202012
            schemaUri.contains("2019-09") -> SpecVersion.VersionFlag.V201909
            schemaUri.contains("draft-07") || schemaUri.contains("draft/7") -> SpecVersion.VersionFlag.V7
            schemaUri.contains("draft-06") || schemaUri.contains("draft/6") -> SpecVersion.VersionFlag.V6
            schemaUri.contains("draft-04") || schemaUri.contains("draft/4") -> SpecVersion.VersionFlag.V4
            else -> SpecVersion.VersionFlag.V7 // default to draft-07
        }
    }
}
