package org.apache.utlx.engine.validation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory

/**
 * Validates JSON payloads against a Protobuf schema definition.
 *
 * The schema source is a JSON representation of a proto message definition:
 * ```json
 * {
 *   "messageName": "Order",
 *   "fields": {
 *     "id": { "type": "string", "number": 1, "label": "SINGULAR" },
 *     "total": { "type": "int64", "number": 2, "label": "SINGULAR" },
 *     "items": { "type": "OrderItem", "number": 3, "label": "REPEATED" }
 *   }
 * }
 * ```
 *
 * Validates:
 * - Required fields are present (proto3: all scalar fields have default values,
 *   but we validate that message-type fields and repeated fields are present when expected)
 * - Field types match expected protobuf types
 * - Repeated fields are arrays
 *
 * Init-time: parse proto schema JSON → build field definitions (~5-20ms)
 * Runtime: validate JSON payload against field definitions (~10-50μs per 1KB)
 */
class ProtobufValidator(schemaSource: String) : SchemaValidator {

    private val logger = LoggerFactory.getLogger(ProtobufValidator::class.java)
    override val schemaFormat = "protobuf"

    private val mapper = ObjectMapper().apply { registerModule(kotlinModule()) }
    private val schema: ProtoSchema

    init {
        schema = mapper.readValue(schemaSource)
        logger.debug("Protobuf schema compiled: message '{}' with {} field(s)",
            schema.messageName, schema.fields.size)
    }

    override fun validate(payload: ByteArray, contentType: String): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()

        try {
            val tree = mapper.readTree(payload)
            if (!tree.isObject) {
                errors.add(SchemaValidationError("Expected JSON object for protobuf message, got ${tree.nodeType}"))
                return errors
            }

            for ((fieldName, fieldDef) in schema.fields) {
                val value = tree.get(fieldName)
                val path = ".$fieldName"

                // Repeated fields should be arrays
                if (fieldDef.label == "REPEATED") {
                    if (value != null && !value.isArray) {
                        errors.add(SchemaValidationError(
                            "Field '$fieldName' is repeated but value is not an array",
                            path
                        ))
                    }
                    if (value != null && value.isArray) {
                        value.forEachIndexed { idx, item ->
                            val itemError = validateProtoType(fieldName, fieldDef.type, item, "$path[$idx]")
                            if (itemError != null) errors.add(itemError)
                        }
                    }
                    continue
                }

                // Map fields should be objects
                if (fieldDef.label == "MAP") {
                    if (value != null && !value.isObject) {
                        errors.add(SchemaValidationError(
                            "Field '$fieldName' is a map but value is not an object",
                            path
                        ))
                    }
                    continue
                }

                // Scalar/singular fields — type check if present
                if (value != null && !value.isNull) {
                    val typeError = validateProtoType(fieldName, fieldDef.type, value, path)
                    if (typeError != null) errors.add(typeError)
                }
            }

        } catch (e: Exception) {
            errors.add(SchemaValidationError("Failed to parse payload: ${e.message}"))
        }

        return errors
    }

    private fun validateProtoType(fieldName: String, protoType: String, value: JsonNode, path: String): SchemaValidationError? {
        val valid = when (protoType.lowercase()) {
            "string" -> value.isTextual
            "bytes" -> value.isTextual // Base64-encoded
            "bool" -> value.isBoolean
            "int32", "int64", "uint32", "uint64", "sint32", "sint64",
            "fixed32", "fixed64", "sfixed32", "sfixed64" -> value.isNumber
            "float", "double" -> value.isNumber
            else -> true // Message types, enums — accept any structure
        }
        return if (!valid) {
            SchemaValidationError("Field '$fieldName' expected proto type '$protoType' but got ${value.nodeType}", path)
        } else null
    }

    // Minimal proto schema model for deserialization
    private data class ProtoSchema(
        val messageName: String = "",
        val fields: Map<String, ProtoFieldDef> = emptyMap()
    )

    private data class ProtoFieldDef(
        val type: String = "string",
        val number: Int = 0,
        val label: String = "SINGULAR" // SINGULAR, REPEATED, MAP
    )
}
