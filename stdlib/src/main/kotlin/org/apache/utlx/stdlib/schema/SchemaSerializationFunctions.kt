// stdlib/src/main/kotlin/org/apache/utlx/stdlib/schema/SchemaSerializationFunctions.kt
package org.apache.utlx.stdlib.schema

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction
import org.apache.utlx.formats.avro.AvroSchemaParser
import org.apache.utlx.formats.avro.AvroSchemaSerializer
import org.apache.utlx.formats.protobuf.ProtobufSchemaParser
import org.apache.utlx.formats.protobuf.ProtobufSchemaSerializer

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
 * - parseXSDSchema() - XSD → USDL
 * - renderXSDSchema() - USDL → XSD
 * - parseJSONSchema() - JSON Schema → USDL
 * - renderJSONSchema() - USDL → JSON Schema
 * - parseProtobufSchema() - Protocol Buffers (.proto) → USDL
 * - renderProtobufSchema() - USDL → Protocol Buffers (.proto)
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
        description = "Parse an XSD schema XML string into USDL format",
        minArgs = 1,
        maxArgs = 1,
        category = "Schema",
        parameters = [
            "xsdSchemaString: XSD schema XML string"
        ],
        returns = "USDL schema object with %types, %namespace, etc.",
        example = "parseXSDSchema(\"<xs:schema>...</xs:schema>\")",
        tags = ["schema", "xsd", "usdl"],
        since = "1.0"
    )
    /**
     * Parse an XSD schema XML string into USDL format
     *
     * Converts XML Schema Definition (XSD) to Universal Schema Definition Language (USDL).
     *
     * Example:
     * ```utlx
     * let xsdSchema = '<xs:schema>...</xs:schema>'
     * let usdlSchema = parseXSDSchema(xsdSchema)
     * # usdlSchema now has %types, %namespace, etc.
     * ```
     */
    fun parseXSDSchema(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseXSDSchema")
        val xsdSchemaString = args[0].asString()

        if (xsdSchemaString.isBlank()) {
            throw FunctionArgumentException(
                "parseXSDSchema cannot parse empty or blank XSD schema string. " +
                "Hint: Provide a valid XSD schema XML like '<xs:schema>...</xs:schema>'."
            )
        }

        return try {
            val parser = org.apache.utlx.formats.xsd.XSDParser(xsdSchemaString)
            val xsdSchemaUdm = parser.parse()
            // Convert XSD structure to USDL format
            parser.toUSDL(xsdSchemaUdm)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "parseXSDSchema failed to parse XSD schema: ${e.message}. " +
                "Hint: Ensure the string is a valid XSD schema (XML format). " +
                "Check for required elements like xs:schema, xs:complexType, xs:element."
            )
        }
    }

    @UTLXFunction(
        description = "Render a USDL schema object as an XSD schema XML string",
        minArgs = 1,
        maxArgs = 3,
        category = "Schema",
        parameters = [
            "usdlSchema: USDL schema object with %types directive",
            "prettyPrint: Optional boolean for formatted output (default: true)",
            "preservePattern: Optional boolean to preserve Russian Doll pattern (default: true)"
        ],
        returns = "XSD schema XML string",
        example = "renderXSDSchema(usdlSchema, pretty?, preservePattern?)",
        tags = ["schema", "xsd", "usdl"],
        since = "1.0"
    )
    /**
     * Render a USDL schema object as an XSD schema XML string
     *
     * Converts Universal Schema Definition Language (USDL) to XML Schema Definition (XSD).
     *
     * **Pattern Preservation:**
     * - If `preservePattern = true` (default), inline types with %xsdInline metadata are rendered as Russian Doll pattern
     * - If `preservePattern = false`, all types are rendered as Venetian Blind pattern (global types)
     *
     * Example:
     * ```utlx
     * let usdlSchema = {
     *   "%namespace": "http://example.com/schema",
     *   "%types": {
     *     "Person": {
     *       "%kind": "structure",
     *       "%fields": [...]
     *     }
     *   }
     * }
     * let xsdSchema = renderXSDSchema(usdlSchema)
     * # xsdSchema is now XSD XML format
     * ```
     *
     * Example with pattern conversion:
     * ```utlx
     * # Force conversion to Venetian Blind (global types)
     * let xsdSchema = renderXSDSchema(usdlSchema, true, false)
     * ```
     */
    fun renderXSDSchema(args: List<UDM>): UDM {
        requireArgs(args, 1..3, "renderXSDSchema")
        val usdlSchema = args[0]
        val prettyPrint = if (args.size > 1) args[1].asBoolean() else true
        val preservePattern = if (args.size > 2) args[2].asBoolean() else true

        // Validate that it's a USDL schema
        if (usdlSchema !is UDM.Object) {
            throw FunctionArgumentException(
                "renderXSDSchema expects a USDL schema object, got ${getTypeDescription(usdlSchema)}. " +
                "Hint: USDL schemas must be objects with '%types' directive."
            )
        }

        if (!usdlSchema.properties.containsKey("%types")) {
            throw FunctionArgumentException(
                "renderXSDSchema expects a USDL schema with '%types' directive. " +
                "Hint: USDL schemas must have a '%types' object containing type definitions."
            )
        }

        return try {
            val serializer = org.apache.utlx.formats.xsd.XSDSerializer(
                pattern = null,  // Auto-detect pattern
                version = "1.0",
                addDocumentation = true,
                elementFormDefault = "qualified",
                prettyPrint = prettyPrint,
                preservePattern = preservePattern
            )
            val xsdSchemaString = serializer.serialize(usdlSchema)
            UDM.Scalar(xsdSchemaString)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "renderXSDSchema failed to serialize USDL to XSD schema: ${e.message}. " +
                "Hint: Ensure the USDL schema is valid with proper %kind, %fields, %type directives."
            )
        }
    }

    @UTLXFunction(
        description = "Parse a JSON Schema string into USDL format",
        minArgs = 1,
        maxArgs = 1,
        category = "Schema",
        parameters = [
            "jsonSchemaString: JSON Schema string"
        ],
        returns = "USDL schema object with %types, %namespace, etc.",
        example = "parseJSONSchema(\"{\\\"type\\\": \\\"object\\\", \\\"properties\\\": {...}}\")",
        tags = ["schema", "json-schema", "usdl"],
        since = "1.0"
    )
    /**
     * Parse a JSON Schema string into USDL format
     *
     * Converts JSON Schema (draft-07, 2019-09, 2020-12) to Universal Schema Definition Language (USDL).
     *
     * Example:
     * ```utlx
     * let jsonSchema = '{"$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object", ...}'
     * let usdlSchema = parseJSONSchema(jsonSchema)
     * # usdlSchema now has %types, %title, %documentation, etc.
     * ```
     */
    fun parseJSONSchema(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseJSONSchema")
        val jsonSchemaString = args[0].asString()

        if (jsonSchemaString.isBlank()) {
            throw FunctionArgumentException(
                "parseJSONSchema cannot parse empty or blank JSON Schema string. " +
                "Hint: Provide a valid JSON Schema like '{\"type\": \"object\", \"properties\": {...}}'."
            )
        }

        return try {
            val parser = org.apache.utlx.formats.jsch.JSONSchemaParser(jsonSchemaString)
            val jsonSchemaUdm = parser.parse()
            // Convert JSON Schema structure to USDL format
            parser.toUSDL(jsonSchemaUdm)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "parseJSONSchema failed to parse JSON Schema: ${e.message}. " +
                "Hint: Ensure the string is a valid JSON Schema (draft-07, 2019-09, or 2020-12). " +
                "Check for required fields like 'type', 'properties', or '\$schema'."
            )
        }
    }

    @UTLXFunction(
        description = "Render a USDL schema object as a JSON Schema string",
        minArgs = 1,
        maxArgs = 2,
        category = "Schema",
        parameters = [
            "usdlSchema: USDL schema object with %types directive",
            "prettyPrint: Optional boolean for formatted output (default: true)"
        ],
        returns = "JSON Schema string",
        example = "renderJSONSchema(usdlSchema, pretty?)",
        tags = ["schema", "json-schema", "usdl"],
        since = "1.0"
    )
    /**
     * Render a USDL schema object as a JSON Schema string
     *
     * Converts Universal Schema Definition Language (USDL) to JSON Schema (2020-12).
     *
     * Example:
     * ```utlx
     * let usdlSchema = {
     *   "%types": {
     *     "User": {
     *       "%kind": "structure",
     *       "%fields": [...]
     *     }
     *   }
     * }
     * let jsonSchema = renderJSONSchema(usdlSchema)
     * # jsonSchema is now JSON Schema format string
     * ```
     */
    fun renderJSONSchema(args: List<UDM>): UDM {
        requireArgs(args, 1..2, "renderJSONSchema")
        val usdlSchema = args[0]
        val prettyPrint = if (args.size > 1) args[1].asBoolean() else true

        // Validate that it's a USDL schema
        if (usdlSchema !is UDM.Object) {
            throw FunctionArgumentException(
                "renderJSONSchema expects a USDL schema object, got ${getTypeDescription(usdlSchema)}. " +
                "Hint: USDL schemas must be objects with '%types' directive."
            )
        }

        if (!usdlSchema.properties.containsKey("%types")) {
            throw FunctionArgumentException(
                "renderJSONSchema expects a USDL schema with '%types' directive. " +
                "Hint: USDL schemas must have a '%types' object containing type definitions."
            )
        }

        return try {
            val serializer = org.apache.utlx.formats.jsch.JSONSchemaSerializer(
                draft = "2020-12",
                addDescriptions = true,
                prettyPrint = prettyPrint,
                strict = true
            )
            val jsonSchemaString = serializer.serialize(usdlSchema)
            UDM.Scalar(jsonSchemaString)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "renderJSONSchema failed to serialize USDL to JSON Schema: ${e.message}. " +
                "Hint: Ensure the USDL schema is valid with proper %kind, %fields, %type directives."
            )
        }
    }

    // ============================================
    // Protocol Buffers Schema Functions
    // ============================================

    @UTLXFunction(
        description = "Parse a Protocol Buffers (.proto) schema string into USDL format",
        minArgs = 1,
        maxArgs = 1,
        category = "Schema",
        parameters = [
            "protoSchemaString: Protocol Buffers schema (.proto) string"
        ],
        returns = "USDL schema object with %types, %namespace, etc.",
        example = "parseProtobufSchema(\"syntax = \\\"proto3\\\"; message User { string name = 1; }\")",
        tags = ["schema", "protobuf", "proto3", "usdl"],
        since = "1.0"
    )
    /**
     * Parse a Protocol Buffers (.proto) schema string into USDL format
     *
     * Converts Protocol Buffers schema (proto3) to Universal Schema Definition Language (USDL).
     *
     * **Proto3 Only**: Only proto3 syntax is supported (not proto2).
     *
     * **Multi-Type Support**: A single .proto file can contain multiple message types and enums.
     * All types are extracted and placed in the USDL %types directive.
     *
     * Example:
     * ```utlx
     * let protoSchema = "syntax = \"proto3\";\n" +
     *                   "package example;\n" +
     *                   "message User { string name = 1; int32 age = 2; }"
     * let usdlSchema = parseProtobufSchema(protoSchema)
     * # usdlSchema now has %types with User message
     * ```
     *
     * **Field Numbers**: Protobuf field numbers (1-536,870,911, excluding 19000-19999)
     * are preserved in USDL %field_number metadata.
     *
     * **Enum Requirements**: Proto3 requires first enum value to have ordinal 0.
     */
    fun parseProtobufSchema(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseProtobufSchema")
        val protoSchemaString = args[0].asString()

        if (protoSchemaString.isBlank()) {
            throw FunctionArgumentException(
                "parseProtobufSchema cannot parse empty or blank Protocol Buffers schema string. " +
                "Hint: Provide a valid .proto file content with 'syntax = \"proto3\";' declaration."
            )
        }

        // Check for proto3 syntax (required)
        if (!protoSchemaString.contains("syntax") || !protoSchemaString.contains("proto3")) {
            throw FunctionArgumentException(
                "parseProtobufSchema requires proto3 syntax. Got schema without 'syntax = \"proto3\";' declaration. " +
                "Hint: Proto2 is not supported. Add 'syntax = \"proto3\";' at the top of your .proto file."
            )
        }

        return try {
            val parser = ProtobufSchemaParser()
            parser.parse(protoSchemaString)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "parseProtobufSchema failed to parse Protocol Buffers schema: ${e.message}. " +
                "Hint: Ensure the .proto file has valid proto3 syntax. " +
                "Check for required 'syntax = \"proto3\";' declaration, proper message/enum definitions, " +
                "valid field numbers (1-536,870,911, excluding 19000-19999), and that first enum value has ordinal 0."
            )
        }
    }

    @UTLXFunction(
        description = "Render a USDL schema object as a Protocol Buffers (.proto) schema string",
        minArgs = 1,
        maxArgs = 1,
        category = "Schema",
        parameters = [
            "usdlSchema: USDL schema object with %types directive"
        ],
        returns = "Protocol Buffers (.proto) schema string (proto3)",
        example = "renderProtobufSchema(usdlSchema)",
        tags = ["schema", "protobuf", "proto3", "usdl"],
        since = "1.0"
    )
    /**
     * Render a USDL schema object as a Protocol Buffers (.proto) schema string
     *
     * Converts Universal Schema Definition Language (USDL) to Protocol Buffers schema (proto3).
     *
     * **Proto3 Only**: Output is always proto3 syntax (not proto2).
     *
     * **Multi-Type Support**: USDL %types with multiple message/enum definitions
     * are all written to a single .proto file.
     *
     * Example:
     * ```utlx
     * let usdlSchema = {
     *   "%namespace": "example",
     *   "%types": {
     *     "User": {
     *       "%kind": "structure",
     *       "%fields": [
     *         { "%name": "name", "%type": "string", "%field_number": 1 },
     *         { "%name": "age", "%type": "int32", "%field_number": 2 }
     *       ]
     *     }
     *   }
     * }
     * let protoSchema = renderProtobufSchema(usdlSchema)
     * # protoSchema is now proto3 format string
     * ```
     *
     * **Field Numbers**: USDL %field_number metadata must be provided for all fields.
     * Valid range: 1-536,870,911, excluding 19000-19999.
     *
     * **Enum Requirements**: For enums, first value must have ordinal 0 (proto3 requirement).
     */
    fun renderProtobufSchema(args: List<UDM>): UDM {
        requireArgs(args, 1, "renderProtobufSchema")
        val usdlSchema = args[0]

        // Validate that it's a USDL schema
        if (usdlSchema !is UDM.Object) {
            throw FunctionArgumentException(
                "renderProtobufSchema expects a USDL schema object, got ${getTypeDescription(usdlSchema)}. " +
                "Hint: USDL schemas must be objects with '%types' directive."
            )
        }

        if (!usdlSchema.properties.containsKey("%types")) {
            throw FunctionArgumentException(
                "renderProtobufSchema expects a USDL schema with '%types' directive. " +
                "Hint: USDL schemas must have a '%types' object containing type definitions."
            )
        }

        return try {
            val serializer = ProtobufSchemaSerializer()
            val protoSchemaString = serializer.serialize(usdlSchema)
            UDM.Scalar(protoSchemaString)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "renderProtobufSchema failed to serialize USDL to Protocol Buffers schema: ${e.message}. " +
                "Hint: Ensure the USDL schema is valid with proper %kind, %fields, %type directives. " +
                "For proto3: all fields must have %field_number (1-536,870,911 excluding 19000-19999), " +
                "enums must have first value with ordinal 0, and %kind must be 'structure' or 'enum'."
            )
        }
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
