package org.apache.utlx.engine.validation

import org.slf4j.LoggerFactory

/**
 * Creates pre-compiled SchemaValidator instances from schema source and format.
 * Each validator is compiled once (init time) and reused for every message (runtime).
 */
object SchemaValidatorFactory {

    private val logger = LoggerFactory.getLogger(SchemaValidatorFactory::class.java)

    /**
     * Compile a schema source into a reusable, thread-safe validator.
     *
     * @param schemaSource The schema content (JSON Schema JSON, XSD XML, Avro JSON, etc.)
     * @param schemaFormat The schema format identifier
     * @return A compiled SchemaValidator ready for per-message validation
     * @throws IllegalArgumentException if the schema format is not supported
     * @throws Exception if the schema fails to compile
     */
    fun create(schemaSource: String, schemaFormat: String): SchemaValidator {
        logger.debug("Compiling schema validator for format '{}'", schemaFormat)

        return when (schemaFormat.lowercase()) {
            "json-schema", "jsch" -> JsonSchemaValidator(schemaSource)
            "xsd" -> XsdValidator(schemaSource)
            "avro" -> AvroSchemaValidator(schemaSource)
            else -> throw IllegalArgumentException(
                "Unsupported schema format: '$schemaFormat'. " +
                "Supported: json-schema, jsch, xsd, avro"
            )
        }
    }

    /** Returns the list of supported schema format identifiers. */
    fun supportedFormats(): List<String> = listOf("json-schema", "jsch", "xsd", "avro")
}
