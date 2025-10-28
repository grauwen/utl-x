package org.apache.utlx.formats.avro

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.json.JSONSerializer
import java.io.Writer
import java.io.StringWriter

/**
 * Avro Schema Serializer - Converts UDM to Avro Schema (JSON format)
 *
 * Supports:
 * - Avro 1.11.x schema specification
 * - USDL 1.0 (Universal Schema Definition Language)
 * - Schema evolution via aliases
 * - Logical types (date, timestamp, decimal, uuid, duration)
 * - Record, enum, array, map, union types
 *
 * Features:
 * - Automatic USDL to Avro schema transformation
 * - Schema validation with Apache Avro library
 * - Namespace support
 * - Documentation (doc field) support
 */
class AvroSchemaSerializer(
    private val namespace: String? = null,
    private val prettyPrint: Boolean = true,
    private val validate: Boolean = true
) {

    /**
     * Serialization modes
     */
    private enum class SerializationMode {
        LOW_LEVEL,      // User provides Avro schema structure directly
        UNIVERSAL_DSL   // User provides Universal Schema DSL (USDL)
    }

    /**
     * Serialize UDM to Avro schema string
     */
    fun serialize(udm: UDM): String {
        val writer = StringWriter()
        serialize(udm, writer)
        return writer.toString()
    }

    /**
     * Serialize UDM to Avro schema via Writer
     */
    fun serialize(udm: UDM, writer: Writer) {
        // Step 1: Detect mode and transform if needed
        val mode = detectMode(udm)
        val avroStructure = when (mode) {
            SerializationMode.UNIVERSAL_DSL -> transformUniversalDSL(udm as UDM.Object)
            SerializationMode.LOW_LEVEL -> udm
        }

        // Step 2: Validate Avro schema structure if requested
        if (validate) {
            validateAvroSchema(avroStructure)
        }

        // Step 3: Serialize using JSON serializer (Avro schemas are JSON)
        val jsonSerializer = JSONSerializer(prettyPrint)
        writer.write(jsonSerializer.serialize(avroStructure))
    }

    /**
     * Detect serialization mode based on UDM structure
     */
    private fun detectMode(udm: UDM): SerializationMode {
        return when (udm) {
            is UDM.Object -> {
                when {
                    // Low-level: Has Avro schema keywords
                    udm.properties.containsKey("type") -> SerializationMode.LOW_LEVEL
                    udm.properties.containsKey("fields") -> SerializationMode.LOW_LEVEL
                    udm.properties.containsKey("name") -> SerializationMode.LOW_LEVEL

                    // USDL mode: Has %types directive
                    udm.properties.containsKey("%types") -> SerializationMode.UNIVERSAL_DSL

                    // Default: Low-level
                    else -> SerializationMode.LOW_LEVEL
                }
            }
            else -> SerializationMode.LOW_LEVEL
        }
    }

    // Type registry for resolving custom type references
    private var typeRegistry: Map<String, UDM.Object> = emptyMap()
    private var currentNamespace: String? = null
    // Track which types have been inlined to avoid redefinition
    private val inlinedTypes = mutableSetOf<String>()

    /**
     * Transform USDL (Universal Schema Definition Language) to Avro schema UDM structure
     *
     * Supports USDL 1.0 directives for Avro schema generation.
     *
     * Required USDL directives:
     * - %types: Object mapping type names to type definitions
     * - %kind: "structure" for records, "enumeration" for enums, "array", "union"
     * - %fields: Array of field definitions (for structures)
     * - %name: Field name
     * - %type: Field type
     *
     * Optional USDL directives:
     * - %namespace: Package/namespace for types
     * - %documentation: Type-level description (maps to "doc")
     * - %description: Field-level description (maps to "doc")
     * - %required: Boolean indicating if field is required
     * - %default: Default value
     * - %logicalType: Semantic type annotation (date, timestamp-millis, decimal, uuid, etc.)
     * - %aliases: Array of alternative names for schema evolution
     * - %precision: Decimal precision
     * - %scale: Decimal scale
     * - %array: Boolean indicating array type
     * - %map: Boolean indicating map type
     */
    private fun transformUniversalDSL(schema: UDM.Object): UDM {
        // Extract metadata using USDL % directives
        val defaultNamespace = namespace ?: (schema.properties["%namespace"] as? UDM.Scalar)?.value as? String
        currentNamespace = defaultNamespace

        // Extract types using %types directive
        val types = schema.properties["%types"] as? UDM.Object
            ?: throw IllegalArgumentException("USDL schema requires '%types' directive")

        // Build type registry for custom type resolution
        typeRegistry = types.properties.mapValues { (_, typeDef) -> typeDef as UDM.Object }
        inlinedTypes.clear()

        // If there's only one type, return it directly as the root schema
        if (types.properties.size == 1) {
            val (typeName, typeDef) = types.properties.entries.first()
            return transformType(typeName, typeDef as UDM.Object, defaultNamespace)
        }

        // Multiple types: determine the main type
        // Strategy: Find the structure (record) that references other types
        val mainType = findMainType(types.properties)
        if (mainType != null) {
            val (typeName, typeDef) = mainType
            return transformType(typeName, typeDef as UDM.Object, defaultNamespace)
        }

        // Fallback: return the first structure type, or just the first type
        val firstStructure = types.properties.entries.find { (_, typeDef) ->
            val kind = ((typeDef as? UDM.Object)?.properties?.get("%kind") as? UDM.Scalar)?.value as? String
            kind == "structure"
        }

        if (firstStructure != null) {
            val (typeName, typeDef) = firstStructure
            return transformType(typeName, typeDef as UDM.Object, defaultNamespace)
        }

        // Final fallback: first type
        val (firstTypeName, firstTypeDef) = types.properties.entries.first()
        return transformType(firstTypeName, firstTypeDef as UDM.Object, defaultNamespace)
    }

    /**
     * Find the main/root type in a multi-type schema
     *
     * Heuristic: The main type is usually the structure that:
     * 1. Is a structure (record)
     * 2. Comes last in iteration order (often named after the schema, e.g., "EventLog")
     * 3. Or is the only structure
     */
    private fun findMainType(types: Map<String, UDM>): Map.Entry<String, UDM>? {
        val structures = types.entries.filter { (_, typeDef) ->
            val kind = ((typeDef as? UDM.Object)?.properties?.get("%kind") as? UDM.Scalar)?.value as? String
            kind == "structure"
        }

        // Return the last structure (most likely the main record)
        return structures.lastOrNull()
    }

    /**
     * Transform a single USDL type definition to Avro schema
     * @param inline If true, omit namespace (used when inlining types into field definitions)
     */
    private fun transformType(typeName: String, typeDef: UDM.Object, defaultNamespace: String?, inline: Boolean = false): UDM {
        // Extract %kind directive
        val kind = (typeDef.properties["%kind"] as? UDM.Scalar)?.value as? String
            ?: "structure" // Default to structure

        return when (kind) {
            "structure" -> transformStructure(typeName, typeDef, defaultNamespace, inline)
            "enumeration" -> transformEnumeration(typeName, typeDef, defaultNamespace, inline)
            "fixed" -> transformFixed(typeName, typeDef, defaultNamespace, inline)
            "array" -> transformArray(typeDef, defaultNamespace)
            "union" -> transformUnion(typeDef, defaultNamespace)
            "primitive" -> transformPrimitive(typeDef)
            else -> throw IllegalArgumentException("Unsupported USDL kind: $kind")
        }
    }

    /**
     * Transform USDL structure to Avro record
     * @param inline If true, omit namespace (used when inlining types into field definitions)
     */
    private fun transformStructure(typeName: String, typeDef: UDM.Object, defaultNamespace: String?, inline: Boolean = false): UDM {
        // Extract directives
        val fields = typeDef.properties["%fields"] as? UDM.Array
            ?: throw IllegalArgumentException("Structure type requires '%fields' directive")
        val doc = (typeDef.properties["%documentation"] as? UDM.Scalar)?.value as? String
        val typeNamespace = (typeDef.properties["%namespace"] as? UDM.Scalar)?.value as? String ?: defaultNamespace
        val aliases = (typeDef.properties["%aliases"] as? UDM.Array)?.elements
            ?.mapNotNull { (it as? UDM.Scalar)?.value as? String }

        // Build Avro fields array
        val avroFields = fields.elements.map { fieldUdm ->
            if (fieldUdm !is UDM.Object) {
                throw IllegalArgumentException("Field must be an object")
            }
            transformField(fieldUdm, defaultNamespace)
        }

        // Build Avro record
        val recordProps = mutableMapOf<String, UDM>(
            "type" to UDM.Scalar("record"),
            "name" to UDM.Scalar(typeName),
            "fields" to UDM.Array(avroFields)
        )

        // Only add namespace when not inlining (inlined types inherit namespace from parent)
        if (typeNamespace != null && !inline) {
            recordProps["namespace"] = UDM.Scalar(typeNamespace)
        }

        if (doc != null) {
            recordProps["doc"] = UDM.Scalar(doc)
        }

        if (aliases != null && aliases.isNotEmpty()) {
            recordProps["aliases"] = UDM.Array(aliases.map { UDM.Scalar(it) })
        }

        return UDM.Object(properties = recordProps)
    }

    /**
     * Transform USDL field to Avro field
     */
    private fun transformField(fieldDef: UDM.Object, defaultNamespace: String?): UDM {
        // Extract field directives
        val name = (fieldDef.properties["%name"] as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("Field requires '%name' directive")
        val type = (fieldDef.properties["%type"] as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("Field requires '%type' directive")
        val required = (fieldDef.properties["%required"] as? UDM.Scalar)?.value as? Boolean ?: true
        val default = fieldDef.properties["%default"]
        val doc = (fieldDef.properties["%documentation"] as? UDM.Scalar)?.value as? String
        val logicalType = (fieldDef.properties["%logicalType"] as? UDM.Scalar)?.value as? String
        val aliases = (fieldDef.properties["%aliases"] as? UDM.Array)?.elements
            ?.mapNotNull { (it as? UDM.Scalar)?.value as? String }
        val isArray = (fieldDef.properties["%array"] as? UDM.Scalar)?.value as? Boolean ?: false
        val isMap = (fieldDef.properties["%map"] as? UDM.Scalar)?.value as? Boolean ?: false
        val precision = (fieldDef.properties["%precision"] as? UDM.Scalar)?.value as? Number
        val scale = (fieldDef.properties["%scale"] as? UDM.Scalar)?.value as? Number

        // Map USDL type to Avro type
        var avroType = mapUSDLTypeToAvro(type, logicalType, precision, scale)

        // Handle array
        if (isArray) {
            avroType = UDM.Object(properties = mapOf(
                "type" to UDM.Scalar("array"),
                "items" to avroType
            ))
        }

        // Handle map
        if (isMap) {
            avroType = UDM.Object(properties = mapOf(
                "type" to UDM.Scalar("map"),
                "values" to avroType
            ))
        }

        // Handle nullable (union with null)
        if (!required) {
            avroType = UDM.Array(listOf(
                UDM.Scalar("null"),
                avroType
            ))
        }

        // Build field object
        val fieldProps = mutableMapOf<String, UDM>(
            "name" to UDM.Scalar(name),
            "type" to avroType
        )

        if (default != null) {
            fieldProps["default"] = default
        } else if (!required && default == null) {
            // For optional fields without explicit default, add null as default
            fieldProps["default"] = UDM.Scalar(null)
        }

        if (doc != null) {
            fieldProps["doc"] = UDM.Scalar(doc)
        }

        if (aliases != null && aliases.isNotEmpty()) {
            fieldProps["aliases"] = UDM.Array(aliases.map { UDM.Scalar(it) })
        }

        return UDM.Object(properties = fieldProps)
    }

    /**
     * Transform USDL enumeration to Avro enum
     * @param inline If true, omit namespace (used when inlining types into field definitions)
     */
    private fun transformEnumeration(typeName: String, typeDef: UDM.Object, defaultNamespace: String?, inline: Boolean = false): UDM {
        // Extract directives
        val values = typeDef.properties["%values"] as? UDM.Array
            ?: throw IllegalArgumentException("Enumeration type requires '%values' directive")
        val doc = (typeDef.properties["%documentation"] as? UDM.Scalar)?.value as? String
        val typeNamespace = (typeDef.properties["%namespace"] as? UDM.Scalar)?.value as? String ?: defaultNamespace
        val aliases = (typeDef.properties["%aliases"] as? UDM.Array)?.elements
            ?.mapNotNull { (it as? UDM.Scalar)?.value as? String }

        // Build Avro enum
        val enumProps = mutableMapOf<String, UDM>(
            "type" to UDM.Scalar("enum"),
            "name" to UDM.Scalar(typeName),
            "symbols" to values
        )

        // Only add namespace when not inlining (inlined types inherit namespace from parent)
        if (typeNamespace != null && !inline) {
            enumProps["namespace"] = UDM.Scalar(typeNamespace)
        }

        if (doc != null) {
            enumProps["doc"] = UDM.Scalar(doc)
        }

        if (aliases != null && aliases.isNotEmpty()) {
            enumProps["aliases"] = UDM.Array(aliases.map { UDM.Scalar(it) })
        }

        return UDM.Object(properties = enumProps)
    }

    /**
     * Transform USDL fixed to Avro fixed type
     * @param inline If true, omit namespace (used when inlining types into field definitions)
     */
    private fun transformFixed(typeName: String, typeDef: UDM.Object, defaultNamespace: String?, inline: Boolean = false): UDM {
        // Extract directives
        val size = (typeDef.properties["%size"] as? UDM.Scalar)?.value
            ?: throw IllegalArgumentException("Fixed type requires '%size' directive")
        val sizeValue = when (size) {
            is Number -> size.toInt()
            is String -> size.toIntOrNull() ?: throw IllegalArgumentException("Fixed type '%size' must be a number")
            else -> throw IllegalArgumentException("Fixed type '%size' must be a number")
        }

        val doc = (typeDef.properties["%documentation"] as? UDM.Scalar)?.value as? String
        val typeNamespace = (typeDef.properties["%namespace"] as? UDM.Scalar)?.value as? String ?: defaultNamespace
        val aliases = (typeDef.properties["%aliases"] as? UDM.Array)?.elements
            ?.mapNotNull { (it as? UDM.Scalar)?.value as? String }

        // Build Avro fixed type
        val fixedProps = mutableMapOf<String, UDM>(
            "type" to UDM.Scalar("fixed"),
            "name" to UDM.Scalar(typeName),
            "size" to UDM.Scalar(sizeValue)
        )

        // Only add namespace when not inlining (inlined types inherit namespace from parent)
        if (typeNamespace != null && !inline) {
            fixedProps["namespace"] = UDM.Scalar(typeNamespace)
        }

        if (doc != null) {
            fixedProps["doc"] = UDM.Scalar(doc)
        }

        if (aliases != null && aliases.isNotEmpty()) {
            fixedProps["aliases"] = UDM.Array(aliases.map { UDM.Scalar(it) })
        }

        return UDM.Object(properties = fixedProps)
    }

    /**
     * Transform USDL array to Avro array
     */
    private fun transformArray(typeDef: UDM.Object, defaultNamespace: String?): UDM {
        val itemType = (typeDef.properties["%itemType"] as? UDM.Scalar)?.value as? String
            ?: "string" // Default to string

        val avroItemType = mapUSDLTypeToAvro(itemType, null, null, null)

        return UDM.Object(properties = mapOf(
            "type" to UDM.Scalar("array"),
            "items" to avroItemType
        ))
    }

    /**
     * Transform USDL union to Avro union
     */
    private fun transformUnion(typeDef: UDM.Object, defaultNamespace: String?): UDM {
        val unionTypes = typeDef.properties["%unionTypes"] as? UDM.Array
            ?: throw IllegalArgumentException("Union type requires '%unionTypes' directive")

        val avroTypes = unionTypes.elements.map { typeUdm ->
            when (typeUdm) {
                is UDM.Scalar -> mapUSDLTypeToAvro(typeUdm.value as String, null, null, null)
                is UDM.Object -> transformType("", typeUdm, defaultNamespace)
                else -> throw IllegalArgumentException("Invalid union type")
            }
        }

        return UDM.Array(avroTypes)
    }

    /**
     * Transform USDL primitive to Avro primitive
     */
    private fun transformPrimitive(typeDef: UDM.Object): UDM {
        val baseType = (typeDef.properties["%type"] as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("Primitive type requires '%type' directive")
        val logicalType = (typeDef.properties["%logicalType"] as? UDM.Scalar)?.value as? String
        val precision = (typeDef.properties["%precision"] as? UDM.Scalar)?.value as? Number
        val scale = (typeDef.properties["%scale"] as? UDM.Scalar)?.value as? Number

        return mapUSDLTypeToAvro(baseType, logicalType, precision, scale)
    }

    /**
     * Map USDL type to Avro type with optional logical type
     */
    private fun mapUSDLTypeToAvro(
        usdlType: String,
        logicalType: String?,
        precision: Number?,
        scale: Number?
    ): UDM {
        // If logical type is specified, create logical type structure
        if (logicalType != null) {
            return createLogicalType(usdlType, logicalType, precision, scale)
        }

        // Simple type mapping for primitives
        return when (usdlType.lowercase()) {
            "string" -> UDM.Scalar("string")
            "integer" -> UDM.Scalar("int")
            "long" -> UDM.Scalar("long")
            "number", "float" -> UDM.Scalar("float")
            "double" -> UDM.Scalar("double")
            "boolean" -> UDM.Scalar("boolean")
            "binary", "bytes" -> UDM.Scalar("bytes")
            "null" -> UDM.Scalar("null")
            else -> {
                // Check if this is a custom type reference
                val customTypeDef = typeRegistry[usdlType]
                if (customTypeDef != null) {
                    // Check if this type has already been inlined
                    if (inlinedTypes.contains(usdlType)) {
                        // Return fully qualified name reference to avoid redefinition
                        val fqn = if (currentNamespace != null) "$currentNamespace.$usdlType" else usdlType
                        UDM.Scalar(fqn)
                    } else {
                        // First occurrence: inline the full definition and mark as inlined
                        inlinedTypes.add(usdlType)
                        transformType(usdlType, customTypeDef, null, inline = true)
                    }
                } else {
                    // Unknown type - pass through as string (might be a forward reference)
                    UDM.Scalar(usdlType)
                }
            }
        }
    }

    /**
     * Create Avro logical type structure
     */
    private fun createLogicalType(
        baseType: String,
        logicalType: String,
        precision: Number?,
        scale: Number?
    ): UDM {
        val avroBaseType = when (logicalType) {
            "date" -> "int"
            "time-millis" -> "int"
            "time-micros" -> "long"
            "timestamp-millis" -> "long"
            "timestamp-micros" -> "long"
            "decimal" -> "bytes" // or "fixed"
            "uuid" -> "string"
            "duration" -> "fixed"
            else -> mapUSDLTypeToAvro(baseType, null, null, null).let {
                (it as? UDM.Scalar)?.value as? String ?: "string"
            }
        }

        val props = mutableMapOf<String, UDM>(
            "type" to UDM.Scalar(avroBaseType),
            "logicalType" to UDM.Scalar(logicalType)
        )

        // Add precision and scale for decimal
        if (logicalType == "decimal") {
            if (precision != null) {
                props["precision"] = UDM.Scalar(precision.toInt())
            }
            if (scale != null) {
                props["scale"] = UDM.Scalar(scale.toInt())
            }
        }

        // Duration requires fixed size of 12
        if (logicalType == "duration") {
            props["size"] = UDM.Scalar(12)
        }

        return UDM.Object(properties = props)
    }

    /**
     * Validate Avro schema structure using Apache Avro library
     */
    private fun validateAvroSchema(udm: UDM) {
        try {
            // Serialize to JSON string
            val jsonSerializer = JSONSerializer(false)
            val schemaJson = jsonSerializer.serialize(udm)

            // Validate with Apache Avro library
            org.apache.avro.Schema.Parser().parse(schemaJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Avro schema: ${e.message}", e)
        }
    }
}
