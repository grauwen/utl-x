package org.apache.utlx.engine.validation

import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory
import org.slf4j.LoggerFactory

/**
 * Validates Avro JSON payloads against a pre-compiled Avro schema.
 * Uses org.apache.avro.generic.GenericDatumReader (already in dependency tree via formats:avro).
 *
 * Init-time: parse Avro JSON schema → build Schema object (~5-20ms)
 * Runtime: validate JSON payload against schema via GenericDatumReader (~20-100μs per 2KB)
 */
class AvroSchemaValidator(schemaSource: String) : SchemaValidator {

    private val logger = LoggerFactory.getLogger(AvroSchemaValidator::class.java)
    override val schemaFormat = "avro"

    private val avroSchema: Schema
    private val reader: GenericDatumReader<Any>

    init {
        avroSchema = Schema.Parser().parse(schemaSource)
        reader = GenericDatumReader(avroSchema)
        logger.debug("Avro schema compiled: {}", avroSchema.fullName)
    }

    override fun validate(payload: ByteArray, contentType: String): List<SchemaValidationError> {
        return try {
            // Parse payload as Avro JSON and validate against schema
            val decoder = DecoderFactory.get().jsonDecoder(avroSchema, String(payload, Charsets.UTF_8))
            reader.read(null, decoder)
            emptyList()
        } catch (e: Exception) {
            listOf(
                SchemaValidationError(
                    message = e.message ?: "Avro validation failed",
                    severity = "ERROR"
                )
            )
        }
    }
}
