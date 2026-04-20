package org.apache.utlx.engine.validation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory

/**
 * Validates CSV-style data (as JSON arrays) against a Frictionless Table Schema (TSCH).
 *
 * The schema source is the Table Schema JSON (as defined by Frictionless Data spec).
 * The payload is expected to be a JSON array of objects (rows), where each object
 * has keys matching the field names in the schema.
 *
 * Validates:
 * - Required fields (constraints.required)
 * - Type checking (string, integer, number, boolean, date, etc.)
 * - Pattern constraints (constraints.pattern — regex)
 * - Enum constraints (constraints.enum — allowed values)
 * - Min/max length (constraints.minLength, constraints.maxLength)
 * - Min/max value (constraints.minimum, constraints.maximum)
 *
 * Init-time: parse Table Schema JSON → build field definitions (~1-5ms)
 * Runtime: validate payload rows against field definitions (~10-50μs per row)
 */
class TableSchemaValidator(schemaSource: String) : SchemaValidator {

    private val logger = LoggerFactory.getLogger(TableSchemaValidator::class.java)
    override val schemaFormat = "tsch"

    private val mapper = ObjectMapper().apply { registerModule(kotlinModule()) }
    private val schema: TschSchema

    init {
        schema = mapper.readValue(schemaSource)
        logger.debug("Table Schema compiled: {} field(s)", schema.fields.size)
    }

    override fun validate(payload: ByteArray, contentType: String): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()

        try {
            val tree = mapper.readTree(payload)

            // Payload can be a single object (one row) or an array of objects (multiple rows)
            val rows = if (tree.isArray) tree.toList() else listOf(tree)

            rows.forEachIndexed { rowIndex, row ->
                if (!row.isObject) {
                    errors.add(SchemaValidationError("Row $rowIndex: expected object, got ${row.nodeType}", "[$rowIndex]"))
                    return@forEachIndexed
                }

                val rowPath = if (rows.size > 1) "[$rowIndex]" else ""

                for (field in schema.fields) {
                    val value = row.get(field.name)
                    val fieldPath = "$rowPath.${field.name}"

                    // Required check
                    if (field.constraints?.required == true && (value == null || value.isNull)) {
                        errors.add(SchemaValidationError("Required field '${field.name}' is missing", fieldPath))
                        continue
                    }

                    if (value == null || value.isNull) continue

                    // Type check
                    val typeError = validateType(field.name, field.type, value, fieldPath)
                    if (typeError != null) {
                        errors.add(typeError)
                        continue
                    }

                    // Constraint checks
                    val constraints = field.constraints ?: continue

                    // Pattern
                    if (constraints.pattern != null && value.isTextual) {
                        if (!Regex(constraints.pattern).matches(value.asText())) {
                            errors.add(SchemaValidationError(
                                "Field '${field.name}' does not match pattern '${constraints.pattern}'",
                                fieldPath
                            ))
                        }
                    }

                    // Enum
                    if (constraints.enum != null && value.isTextual) {
                        val allowed = constraints.enum.map { it.toString() }
                        if (value.asText() !in allowed) {
                            errors.add(SchemaValidationError(
                                "Field '${field.name}' value '${value.asText()}' not in allowed values: $allowed",
                                fieldPath
                            ))
                        }
                    }

                    // Min/max length (for strings)
                    if (value.isTextual) {
                        val len = value.asText().length
                        if (constraints.minLength != null && len < constraints.minLength) {
                            errors.add(SchemaValidationError(
                                "Field '${field.name}' length $len is less than minimum ${constraints.minLength}",
                                fieldPath
                            ))
                        }
                        if (constraints.maxLength != null && len > constraints.maxLength) {
                            errors.add(SchemaValidationError(
                                "Field '${field.name}' length $len exceeds maximum ${constraints.maxLength}",
                                fieldPath
                            ))
                        }
                    }

                    // Min/max value (for numbers)
                    if (value.isNumber) {
                        val num = value.asDouble()
                        if (constraints.minimum != null) {
                            val min = constraints.minimum.toString().toDoubleOrNull()
                            if (min != null && num < min) {
                                errors.add(SchemaValidationError(
                                    "Field '${field.name}' value $num is less than minimum $min",
                                    fieldPath
                                ))
                            }
                        }
                        if (constraints.maximum != null) {
                            val max = constraints.maximum.toString().toDoubleOrNull()
                            if (max != null && num > max) {
                                errors.add(SchemaValidationError(
                                    "Field '${field.name}' value $num exceeds maximum $max",
                                    fieldPath
                                ))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add(SchemaValidationError("Failed to parse payload: ${e.message}"))
        }

        return errors
    }

    private fun validateType(
        fieldName: String,
        expectedType: String,
        value: com.fasterxml.jackson.databind.JsonNode,
        path: String
    ): SchemaValidationError? {
        val valid = when (expectedType.lowercase()) {
            "string" -> value.isTextual
            "integer" -> value.isIntegralNumber
            "number" -> value.isNumber
            "boolean" -> value.isBoolean
            "object" -> value.isObject
            "array" -> value.isArray
            "any" -> true
            // Date/time types: accept strings (format validation is lenient)
            "date", "time", "datetime", "year", "yearmonth", "duration" -> value.isTextual
            else -> true // Unknown types pass
        }
        return if (!valid) {
            SchemaValidationError("Field '$fieldName' expected type '$expectedType' but got ${value.nodeType}", path)
        } else null
    }

    // Minimal TSCH schema model for deserialization
    private data class TschSchema(
        val fields: List<TschField> = emptyList()
    )

    private data class TschField(
        val name: String,
        val type: String = "string",
        val constraints: TschConstraints? = null
    )

    private data class TschConstraints(
        val required: Boolean? = null,
        val unique: Boolean? = null,
        val enum: List<Any>? = null,
        val pattern: String? = null,
        val minimum: Any? = null,
        val maximum: Any? = null,
        val minLength: Int? = null,
        val maxLength: Int? = null
    )
}
