package org.apache.utlx.engine.validation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.JsonSchema
import org.slf4j.LoggerFactory

/**
 * Validates YAML payloads against a JSON Schema.
 *
 * YAML is a superset of JSON — the standard approach is to parse YAML to a JSON tree
 * and validate that tree against JSON Schema. This is the same approach used by all
 * major YAML validation tools.
 *
 * The schema source is JSON Schema (same as JsonSchemaValidator). The difference
 * is that the payload is parsed as YAML instead of JSON.
 *
 * Init-time: parse JSON Schema source → compile validator tree (~10-50ms)
 * Runtime: parse YAML → JSON tree → validate (~100-300μs per 5KB YAML)
 */
class YamlSchemaValidator(schemaSource: String) : SchemaValidator {

    private val logger = LoggerFactory.getLogger(YamlSchemaValidator::class.java)
    override val schemaFormat = "yaml"

    private val yamlMapper = ObjectMapper(YAMLFactory())
    private val jsonMapper = ObjectMapper()
    private val compiledSchema: JsonSchema

    init {
        val schemaNode = jsonMapper.readTree(schemaSource)
        val schemaUri = schemaNode.get("\$schema")?.asText() ?: ""
        val version = when {
            schemaUri.contains("2020-12") -> SpecVersion.VersionFlag.V202012
            schemaUri.contains("2019-09") -> SpecVersion.VersionFlag.V201909
            schemaUri.contains("draft-07") || schemaUri.contains("draft/7") -> SpecVersion.VersionFlag.V7
            else -> SpecVersion.VersionFlag.V7
        }
        val factory = JsonSchemaFactory.getInstance(version)
        compiledSchema = factory.getSchema(schemaNode)
        logger.debug("YAML schema validator compiled (JSON Schema {})", version)
    }

    override fun validate(payload: ByteArray, contentType: String): List<SchemaValidationError> {
        return try {
            // Parse YAML to Jackson JsonNode tree
            val yamlTree = yamlMapper.readTree(payload)
            // Validate the tree against JSON Schema
            val errors = compiledSchema.validate(yamlTree)
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
                    message = "Failed to parse payload as YAML: ${e.message}",
                    severity = "ERROR"
                )
            )
        }
    }
}
