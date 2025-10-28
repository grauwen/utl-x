// stdlib/src/main/kotlin/org/apache/utlx/stdlib/schema/SchemaSerializationFunctions.kt
package org.apache.utlx.stdlib.schema

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction
import org.apache.utlx.formats.avro.AvroSchemaParser
import org.apache.utlx.formats.avro.AvroSchemaSerializer

/**
 * Schema Serialization Functions for UTL-X Standard Library
 *
 * Provides parse/render functions for schema format conversion.
 * These functions handle SCHEMA documents (not data documents).
 *
 * USDL (Universal Schema Definition Language) serves as the intermediate format.
 *
 * **Tier 2: Schema Serialization**
 * - parseAvroSchema() - Avro schema JSON → USDL
 * - renderAvroSchema() - USDL → Avro schema JSON
 * - parseXSDSchema() - XSD → USDL (future)
 * - renderXSDSchema() - USDL → XSD (future)
 * - parseJSONSchema() - JSON Schema → USDL (future)
 * - renderJSONSchema() - USDL → JSON Schema (future)
 *
 * **Comparison with Data Functions:**
 * - Data: parseJson(jsonData) - deserializes JSON DATA
 * - Schema: parseAvroSchema(avroSchema) - deserializes Avro SCHEMA
 *
 * **Use Cases:**
 * - Schema round-trip testing
 * - Schema format migration
 * - Schema validation and introspection
 * - Programmatic schema generation
 */
object SchemaSerializationFunctions {

    @UTLXFunction(
        description = "Parse an Avro schema JSON string into USDL format",
        minArgs = 1,
        maxArgs = 1,
        category = "Schema",
        parameters = [
            "avroSchemaString: Avro schema JSON string"
        ],
        returns = "USDL schema object with %types, %namespace, etc.",
        example = "parseAvroSchema(\"{\"type\": \"record\", \"name\": \"User\", \"fields\": [...]}\")",
        tags = ["schema", "avro", "usdl"],
        since = "1.0"
    )
    /**
     * Parse an Avro schema JSON string into USDL format
     *
     * Converts Apache Avro schema to Universal Schema Definition Language (USDL).
     *
     * Example:
     * ```utlx
     * let avroSchema = "{\"type\": \"record\", \"name\": \"User\", ...}"
     * let usdlSchema = parseAvroSchema(avroSchema)
     * # usdlSchema now has %types, %namespace, etc.
     * ```
     */
    fun parseAvroSchema(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseAvroSchema")
        val avroSchemaString = args[0].asString()

        if (avroSchemaString.isBlank()) {
            throw FunctionArgumentException(
                "parseAvroSchema cannot parse empty or blank Avro schema string. " +
                "Hint: Provide a valid Avro schema JSON like '{\"type\": \"record\", \"name\": \"User\", \"fields\": [...]}'."
            )
        }

        return try {
            val parser = AvroSchemaParser()
            val avroSchemaUdm = parser.parse(avroSchemaString)
            // Convert Avro schema format to USDL format
            parser.toUSDL(avroSchemaUdm)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "parseAvroSchema failed to parse Avro schema: ${e.message}. " +
                "Hint: Ensure the string is a valid Avro schema (JSON format). " +
                "Check for required fields like 'type', 'name', 'fields' for records."
            )
        }
    }

    @UTLXFunction(
        description = "Render a USDL schema object as an Avro schema JSON string",
        minArgs = 1,
        maxArgs = 2,
        category = "Schema",
        parameters = [
            "usdlSchema: USDL schema object with %types directive",
            "prettyPrint: Optional boolean for formatted output (default: true)"
        ],
        returns = "Avro schema JSON string",
        example = "renderAvroSchema(usdlSchema, pretty?)",
        tags = ["schema", "avro", "usdl"],
        since = "1.0"
    )
    /**
     * Render a USDL schema object as an Avro schema JSON string
     *
     * Converts Universal Schema Definition Language (USDL) to Apache Avro schema.
     *
     * Example:
     * ```utlx
     * let usdlSchema = {
     *   "%namespace": "com.example",
     *   "%types": {
     *     "User": {
     *       "%kind": "structure",
     *       "%fields": [...]
     *     }
     *   }
     * }
     * let avroSchema = renderAvroSchema(usdlSchema)
     * # avroSchema is now Avro-format JSON string
     * ```
     */
    fun renderAvroSchema(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "renderAvroSchema")
        val usdlSchema = args[0]
        val prettyPrint = if (args.size > 1) args[1].asBoolean() else true

        // Validate that it's a USDL schema
        if (usdlSchema !is UDM.Object) {
            throw FunctionArgumentException(
                "renderAvroSchema expects a USDL schema object, got ${getTypeDescription(usdlSchema)}. " +
                "Hint: USDL schemas must be objects with '%types' directive."
            )
        }

        if (!usdlSchema.properties.containsKey("%types")) {
            throw FunctionArgumentException(
                "renderAvroSchema expects a USDL schema with '%types' directive. " +
                "Hint: USDL schemas must have a '%types' object containing type definitions."
            )
        }

        return try {
            val serializer = AvroSchemaSerializer(
                namespace = null,  // Namespace from USDL
                prettyPrint = prettyPrint,
                validate = true
            )
            val avroSchemaString = serializer.serialize(usdlSchema)
            UDM.Scalar(avroSchemaString)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "renderAvroSchema failed to serialize USDL to Avro schema: ${e.message}. " +
                "Hint: Ensure the USDL schema is valid with proper %kind, %fields, %type directives."
            )
        }
    }

    // ============================================
    // Future functions (stubs for XSD and JSON Schema)
    // ============================================

    @UTLXFunction(
        description = "Parse an XSD schema string into USDL format (not yet implemented)",
        minArgs = 1,
        maxArgs = 1,
        category = "Schema",
        parameters = [
            "xsdSchemaString: XSD schema XML string"
        ],
        returns = "USDL schema object",
        example = "parseXSDSchema(xsdString)",
        tags = ["schema", "xsd", "usdl", "future"],
        since = "1.0"
    )
    /**
     * Parse an XSD schema string into USDL format
     *
     * **Status:** Not yet implemented - planned for future release
     */
    fun parseXSDSchema(args: List<UDM>): UDM {
        throw FunctionArgumentException(
            "parseXSDSchema is not yet implemented. " +
            "Hint: Use 'input xsd' / 'output json' at the I/O boundary for now."
        )
    }

    @UTLXFunction(
        description = "Render a USDL schema object as an XSD schema string (not yet implemented)",
        minArgs = 1,
        maxArgs = 1,
        category = "Schema",
        parameters = [
            "usdlSchema: USDL schema object"
        ],
        returns = "XSD schema XML string",
        example = "renderXSDSchema(usdlSchema)",
        tags = ["schema", "xsd", "usdl", "future"],
        since = "1.0"
    )
    /**
     * Render a USDL schema object as an XSD schema string
     *
     * **Status:** Not yet implemented - planned for future release
     */
    fun renderXSDSchema(args: List<UDM>): UDM {
        throw FunctionArgumentException(
            "renderXSDSchema is not yet implemented. " +
            "Hint: Use 'input json' / 'output xsd' at the I/O boundary for now."
        )
    }

    @UTLXFunction(
        description = "Parse a JSON Schema string into USDL format (not yet implemented)",
        minArgs = 1,
        maxArgs = 1,
        category = "Schema",
        parameters = [
            "jsonSchemaString: JSON Schema string"
        ],
        returns = "USDL schema object",
        example = "parseJSONSchema(jschString)",
        tags = ["schema", "json-schema", "usdl", "future"],
        since = "1.0"
    )
    /**
     * Parse a JSON Schema string into USDL format
     *
     * **Status:** Not yet implemented - planned for future release
     */
    fun parseJSONSchema(args: List<UDM>): UDM {
        throw FunctionArgumentException(
            "parseJSONSchema is not yet implemented. " +
            "Hint: Use 'input jsch' / 'output json' at the I/O boundary for now."
        )
    }

    @UTLXFunction(
        description = "Render a USDL schema object as a JSON Schema string (not yet implemented)",
        minArgs = 1,
        maxArgs = 1,
        category = "Schema",
        parameters = [
            "usdlSchema: USDL schema object"
        ],
        returns = "JSON Schema string",
        example = "renderJSONSchema(usdlSchema)",
        tags = ["schema", "json-schema", "usdl", "future"],
        since = "1.0"
    )
    /**
     * Render a USDL schema object as a JSON Schema string
     *
     * **Status:** Not yet implemented - planned for future release
     */
    fun renderJSONSchema(args: List<UDM>): UDM {
        throw FunctionArgumentException(
            "renderJSONSchema is not yet implemented. " +
            "Hint: Use 'input json' / 'output jsch' at the I/O boundary for now."
        )
    }

    // ============================================
    // Helper functions
    // ============================================

    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
    }

    private fun requireArgs(args: List<UDM>, range: IntRange, functionName: String) {
        if (args.size !in range) {
            throw FunctionArgumentException(
                "$functionName expects ${range.first}..${range.last} arguments, got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
    }

    private fun UDM.asString(): String {
        return when (this) {
            is UDM.Scalar -> {
                val v = value
                when (v) {
                    is String -> v
                    is Number -> v.toString()
                    is Boolean -> v.toString()
                    null -> ""
                    else -> v.toString()
                }
            }
            else -> throw FunctionArgumentException(
                "Expected string value, but got ${getTypeDescription(this)}. " +
                "Hint: Use toString() to convert values to strings."
            )
        }
    }

    private fun getTypeDescription(udm: UDM): String {
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    is String -> "string"
                    is Number -> "number"
                    is Boolean -> "boolean"
                    null -> "null"
                    else -> value.javaClass.simpleName
                }
            }
            is UDM.Array -> "array"
            is UDM.Object -> "object"
            is UDM.Binary -> "binary"
            is UDM.DateTime -> "datetime"
            is UDM.Date -> "date"
            is UDM.LocalDateTime -> "localdatetime"
            is UDM.Time -> "time"
            is UDM.Lambda -> "lambda"
            else -> udm.javaClass.simpleName
        }
    }

    private fun UDM.asBoolean(): Boolean {
        return when (this) {
            is UDM.Scalar -> {
                val v = value
                when (v) {
                    is Boolean -> v
                    is Number -> v.toDouble() != 0.0
                    is String -> v.isNotEmpty() && v.lowercase() in listOf("true", "yes", "1")
                    null -> false
                    else -> true
                }
            }
            is UDM.Array -> elements.isNotEmpty()
            is UDM.Object -> properties.isNotEmpty()
            else -> true
        }
    }
}
