package org.apache.utlx.formats.avro

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.json.JSONParser
import java.io.InputStream

/**
 * Avro Schema Parser - Converts Avro Schema (JSON format) to UDM
 *
 * Supports parsing Avro 1.11.x schemas to UDM representation for:
 * - Record types
 * - Enum types
 * - Array types
 * - Map types
 * - Union types
 * - Primitive types
 * - Logical types
 *
 * The parsed UDM can be:
 * 1. Serialized back to Avro schema (round-trip)
 * 2. Transformed to other schema formats (XSD, JSON Schema)
 * 3. Used for schema analysis and documentation
 */
class AvroSchemaParser {

    /**
     * Parse Avro schema from JSON string
     */
    fun parse(schemaJson: String): UDM {
        val jsonParser = JSONParser(schemaJson)
        val udm = jsonParser.parse()
        return parseSchema(udm)
    }

    /**
     * Parse Avro schema from InputStream
     */
    fun parse(inputStream: InputStream): UDM {
        val jsonParser = JSONParser(inputStream.reader())
        val udm = jsonParser.parse()
        return parseSchema(udm)
    }

    /**
     * Parse Avro schema UDM (already parsed from JSON)
     */
    private fun parseSchema(udm: UDM): UDM {
        // Validate with Apache Avro library if possible
        try {
            val jsonSerializer = org.apache.utlx.formats.json.JSONSerializer(false)
            val schemaJson = jsonSerializer.serialize(udm)
            org.apache.avro.Schema.Parser().parse(schemaJson)
        } catch (e: Exception) {
            // Not a fatal error - just means validation failed
            // We'll continue parsing anyway
        }

        // Return the UDM as-is (low-level mode)
        // The UDM already represents the Avro schema structure directly
        return udm
    }

    /**
     * Convert Avro schema to USDL format (optional utility)
     *
     * This is the inverse of AvroSchemaSerializer.transformUniversalDSL()
     * Useful for generating USDL from existing Avro schemas.
     */
    fun toUSDL(avroSchema: UDM): UDM {
        if (avroSchema !is UDM.Object) {
            throw IllegalArgumentException("Avro schema must be an object")
        }

        val typeValue = avroSchema.properties["type"] as? UDM.Scalar
        val typeName = typeValue?.value as? String ?: "UnnamedType"

        return when (typeName) {
            "record" -> recordToUSDL(avroSchema)
            "enum" -> enumToUSDL(avroSchema)
            "array" -> arrayToUSDL(avroSchema)
            "map" -> mapToUSDL(avroSchema)
            else -> primitiveToUSDL(avroSchema)
        }
    }

    /**
     * Convert Avro record to USDL structure
     */
    private fun recordToUSDL(record: UDM.Object): UDM {
        val name = (record.properties["name"] as? UDM.Scalar)?.value as? String ?: "UnnamedRecord"
        val namespace = (record.properties["namespace"] as? UDM.Scalar)?.value as? String
        val doc = (record.properties["doc"] as? UDM.Scalar)?.value as? String
        val aliases = (record.properties["aliases"] as? UDM.Array)?.elements
            ?.mapNotNull { (it as? UDM.Scalar)?.value as? String }
        val fields = record.properties["fields"] as? UDM.Array

        // Convert fields
        val usdlFields = fields?.elements?.mapNotNull { fieldUdm ->
            if (fieldUdm !is UDM.Object) return@mapNotNull null
            fieldToUSDL(fieldUdm)
        } ?: emptyList()

        // Build USDL structure
        val typeDefProps = mutableMapOf<String, UDM>(
            "%kind" to UDM.Scalar("structure"),
            "%fields" to UDM.Array(usdlFields)
        )

        if (doc != null) {
            typeDefProps["%documentation"] = UDM.Scalar(doc)
        }

        if (aliases != null && aliases.isNotEmpty()) {
            typeDefProps["%aliases"] = UDM.Array(aliases.map { UDM.Scalar(it) })
        }

        // Add namespace to type definition if present (type-specific namespace)
        if (namespace != null) {
            typeDefProps["%namespace"] = UDM.Scalar(namespace)
        }

        // Build top-level USDL object with %types
        val topLevelProps = mutableMapOf<String, UDM>(
            "%types" to UDM.Object(properties = mapOf(
                name to UDM.Object(properties = typeDefProps)
            ))
        )

        // Add top-level namespace if present (schema-level default namespace)
        // This supports multi-type schemas and cross-format transformations
        if (namespace != null) {
            topLevelProps["%namespace"] = UDM.Scalar(namespace)
        }

        return UDM.Object(properties = topLevelProps)
    }

    /**
     * Convert Avro field to USDL field
     */
    private fun fieldToUSDL(field: UDM.Object): UDM {
        val name = (field.properties["name"] as? UDM.Scalar)?.value as? String ?: return UDM.Object(properties = emptyMap())
        val fieldType = field.properties["type"]
        val default = field.properties["default"]
        val doc = (field.properties["doc"] as? UDM.Scalar)?.value as? String
        val aliases = (field.properties["aliases"] as? UDM.Array)?.elements
            ?.mapNotNull { (it as? UDM.Scalar)?.value as? String }

        // Parse field type
        val (usdlType, isNullable, isArray, isMap, logicalType) = parseFieldType(fieldType)

        // Build USDL field
        val fieldProps = mutableMapOf<String, UDM>(
            "%name" to UDM.Scalar(name),
            "%type" to UDM.Scalar(usdlType)
        )

        if (!isNullable) {
            fieldProps["%required"] = UDM.Scalar(true)
        }

        if (default != null) {
            fieldProps["%default"] = default
        }

        if (doc != null) {
            fieldProps["%documentation"] = UDM.Scalar(doc)
        }

        if (aliases != null && aliases.isNotEmpty()) {
            fieldProps["%aliases"] = UDM.Array(aliases.map { UDM.Scalar(it) })
        }

        if (isArray) {
            fieldProps["%array"] = UDM.Scalar(true)
        }

        if (isMap) {
            fieldProps["%map"] = UDM.Scalar(true)
        }

        if (logicalType != null) {
            fieldProps["%logicalType"] = UDM.Scalar(logicalType)
        }

        return UDM.Object(properties = fieldProps)
    }

    /**
     * Parse Avro field type and extract properties
     *
     * Returns: (baseType, isNullable, isArray, isMap, logicalType)
     */
    private fun parseFieldType(fieldType: UDM?): FieldTypeInfo {
        return when (fieldType) {
            is UDM.Scalar -> {
                // Simple type
                val typeName = fieldType.value as? String ?: "string"
                FieldTypeInfo(
                    baseType = avroTypeToUSDL(typeName),
                    isNullable = typeName == "null",
                    isArray = false,
                    isMap = false,
                    logicalType = null
                )
            }
            is UDM.Array -> {
                // Union type - check if nullable
                val types = fieldType.elements
                val hasNull = types.any { (it as? UDM.Scalar)?.value == "null" }
                val nonNullType = types.firstOrNull { (it as? UDM.Scalar)?.value != "null" }

                if (nonNullType != null) {
                    val info = parseFieldType(nonNullType)
                    info.copy(isNullable = hasNull)
                } else {
                    FieldTypeInfo("null", true, false, false, null)
                }
            }
            is UDM.Object -> {
                // Complex type
                val typeValue = (fieldType.properties["type"] as? UDM.Scalar)?.value as? String

                when (typeValue) {
                    "array" -> {
                        val items = fieldType.properties["items"]
                        val itemInfo = parseFieldType(items)
                        itemInfo.copy(isArray = true)
                    }
                    "map" -> {
                        val values = fieldType.properties["values"]
                        val valueInfo = parseFieldType(values)
                        valueInfo.copy(isMap = true)
                    }
                    else -> {
                        // Check for logical type
                        val logicalType = (fieldType.properties["logicalType"] as? UDM.Scalar)?.value as? String
                        val baseType = typeValue ?: "string"
                        FieldTypeInfo(
                            baseType = avroTypeToUSDL(baseType),
                            isNullable = false,
                            isArray = false,
                            isMap = false,
                            logicalType = logicalType
                        )
                    }
                }
            }
            else -> FieldTypeInfo("string", false, false, false, null)
        }
    }

    /**
     * Field type information
     */
    private data class FieldTypeInfo(
        val baseType: String,
        val isNullable: Boolean,
        val isArray: Boolean,
        val isMap: Boolean,
        val logicalType: String?
    )

    /**
     * Convert Avro enum to USDL enumeration
     */
    private fun enumToUSDL(enum: UDM.Object): UDM {
        val name = (enum.properties["name"] as? UDM.Scalar)?.value as? String ?: "UnnamedEnum"
        val namespace = (enum.properties["namespace"] as? UDM.Scalar)?.value as? String
        val doc = (enum.properties["doc"] as? UDM.Scalar)?.value as? String
        val symbols = enum.properties["symbols"] as? UDM.Array
        val aliases = (enum.properties["aliases"] as? UDM.Array)?.elements
            ?.mapNotNull { (it as? UDM.Scalar)?.value as? String }

        // Build USDL enumeration
        val typeDefProps = mutableMapOf<String, UDM>(
            "%kind" to UDM.Scalar("enumeration")
        )

        if (symbols != null) {
            typeDefProps["%values"] = symbols
        }

        if (namespace != null) {
            typeDefProps["%namespace"] = UDM.Scalar(namespace)
        }

        if (doc != null) {
            typeDefProps["%documentation"] = UDM.Scalar(doc)
        }

        if (aliases != null && aliases.isNotEmpty()) {
            typeDefProps["%aliases"] = UDM.Array(aliases.map { UDM.Scalar(it) })
        }

        // Build top-level USDL object with %types
        val topLevelProps = mutableMapOf<String, UDM>(
            "%types" to UDM.Object(properties = mapOf(
                name to UDM.Object(properties = typeDefProps)
            ))
        )

        // Add top-level namespace if present (schema-level default namespace)
        // This supports multi-type schemas and cross-format transformations
        if (namespace != null) {
            topLevelProps["%namespace"] = UDM.Scalar(namespace)
        }

        return UDM.Object(properties = topLevelProps)
    }

    /**
     * Convert Avro array to USDL array
     */
    private fun arrayToUSDL(array: UDM.Object): UDM {
        val items = array.properties["items"]
        val itemInfo = parseFieldType(items)

        return UDM.Object(properties = mapOf(
            "%kind" to UDM.Scalar("array"),
            "%itemType" to UDM.Scalar(itemInfo.baseType)
        ))
    }

    /**
     * Convert Avro map to USDL (represented as field with %map directive)
     */
    private fun mapToUSDL(map: UDM.Object): UDM {
        val values = map.properties["values"]
        val valueInfo = parseFieldType(values)

        return UDM.Object(properties = mapOf(
            "%kind" to UDM.Scalar("primitive"),
            "%type" to UDM.Scalar(valueInfo.baseType),
            "%map" to UDM.Scalar(true)
        ))
    }

    /**
     * Convert Avro primitive to USDL
     */
    private fun primitiveToUSDL(primitive: UDM.Object): UDM {
        val typeValue = (primitive.properties["type"] as? UDM.Scalar)?.value as? String ?: "string"
        val logicalType = (primitive.properties["logicalType"] as? UDM.Scalar)?.value as? String

        val props = mutableMapOf<String, UDM>(
            "%kind" to UDM.Scalar("primitive"),
            "%type" to UDM.Scalar(avroTypeToUSDL(typeValue))
        )

        if (logicalType != null) {
            props["%logicalType"] = UDM.Scalar(logicalType)
        }

        return UDM.Object(properties = props)
    }

    /**
     * Map Avro type name to USDL type name
     */
    private fun avroTypeToUSDL(avroType: String): String {
        return when (avroType) {
            "null" -> "null"
            "boolean" -> "boolean"
            "int" -> "integer"
            "long" -> "long"
            "float" -> "number"
            "double" -> "double"
            "bytes" -> "binary"
            "string" -> "string"
            else -> avroType // Pass through complex types
        }
    }
}
