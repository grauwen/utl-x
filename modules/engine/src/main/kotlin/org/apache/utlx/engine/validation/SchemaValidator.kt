package org.apache.utlx.engine.validation

/**
 * Validates payload bytes against a pre-compiled schema.
 * Implementations are thread-safe and reusable — compiled once at init time,
 * applied per message at runtime.
 */
interface SchemaValidator {

    /** The schema format this validator handles (e.g., "json-schema", "xsd", "avro"). */
    val schemaFormat: String

    /**
     * Validate payload bytes against the pre-compiled schema.
     * Returns an empty list if validation passes.
     *
     * @param payload Raw payload bytes
     * @param contentType MIME type hint (e.g., "application/json", "application/xml")
     * @return List of validation errors (empty = valid)
     */
    fun validate(payload: ByteArray, contentType: String): List<SchemaValidationError>
}

data class SchemaValidationError(
    val message: String,
    val path: String? = null,
    val severity: String = "ERROR"
)
