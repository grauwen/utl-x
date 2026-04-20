package org.apache.utlx.engine.validation

import org.slf4j.LoggerFactory
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

/**
 * Validates XML payloads against a pre-compiled XSD schema.
 * Uses javax.xml.validation (JDK built-in) — no additional dependencies.
 *
 * Init-time: parse XSD source → compile Schema object (~50-200ms)
 * Runtime: validate payload bytes against compiled schema (~100-500μs per 10KB XML)
 */
class XsdValidator(schemaSource: String) : SchemaValidator {

    private val logger = LoggerFactory.getLogger(XsdValidator::class.java)
    override val schemaFormat = "xsd"

    private val compiledSchema: Schema

    init {
        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        // Disable external entity resolution for security
        try {
            factory.setProperty("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            factory.setProperty("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        } catch (_: Exception) {
            // Some JDK implementations may not support these properties
        }
        compiledSchema = factory.newSchema(StreamSource(StringReader(schemaSource)))
        logger.debug("XSD schema compiled")
    }

    override fun validate(payload: ByteArray, contentType: String): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()

        try {
            val validator = compiledSchema.newValidator()
            try {
                validator.setProperty("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                validator.setProperty("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            } catch (_: Exception) { }

            validator.errorHandler = object : ErrorHandler {
                override fun warning(e: SAXParseException) {
                    errors.add(
                        SchemaValidationError(
                            message = e.message ?: "XSD warning",
                            path = locationPath(e),
                            severity = "WARNING"
                        )
                    )
                }

                override fun error(e: SAXParseException) {
                    errors.add(
                        SchemaValidationError(
                            message = e.message ?: "XSD validation error",
                            path = locationPath(e),
                            severity = "ERROR"
                        )
                    )
                }

                override fun fatalError(e: SAXParseException) {
                    errors.add(
                        SchemaValidationError(
                            message = e.message ?: "XSD fatal error",
                            path = locationPath(e),
                            severity = "ERROR"
                        )
                    )
                }
            }

            validator.validate(StreamSource(ByteArrayInputStream(payload)))
        } catch (e: Exception) {
            // SAXParseException errors are already captured by the error handler.
            // Other exceptions indicate a fundamental problem.
            if (e !is SAXParseException && errors.isEmpty()) {
                errors.add(
                    SchemaValidationError(
                        message = "XML validation failed: ${e.message}",
                        severity = "ERROR"
                    )
                )
            }
        }

        return errors
    }

    private fun locationPath(e: SAXParseException): String {
        val line = e.lineNumber
        val col = e.columnNumber
        return if (line > 0) "line:$line:col:$col" else ""
    }
}
