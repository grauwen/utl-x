package org.apache.utlx.formats.avro

/**
 * Avro domain model classes
 *
 * These classes represent Avro schema concepts in a type-safe way.
 */

/**
 * Avro primitive types
 */
enum class AvroPrimitiveType(val avroName: String) {
    NULL("null"),
    BOOLEAN("boolean"),
    INT("int"),
    LONG("long"),
    FLOAT("float"),
    DOUBLE("double"),
    BYTES("bytes"),
    STRING("string");

    companion object {
        fun fromAvroName(name: String): AvroPrimitiveType? {
            return values().firstOrNull { it.avroName == name }
        }
    }
}

/**
 * Avro logical types
 */
enum class AvroLogicalType(val logicalTypeName: String, val baseType: AvroPrimitiveType) {
    DATE("date", AvroPrimitiveType.INT),
    TIME_MILLIS("time-millis", AvroPrimitiveType.INT),
    TIME_MICROS("time-micros", AvroPrimitiveType.LONG),
    TIMESTAMP_MILLIS("timestamp-millis", AvroPrimitiveType.LONG),
    TIMESTAMP_MICROS("timestamp-micros", AvroPrimitiveType.LONG),
    DECIMAL("decimal", AvroPrimitiveType.BYTES), // Can also be FIXED
    UUID("uuid", AvroPrimitiveType.STRING),
    DURATION("duration", AvroPrimitiveType.BYTES); // Actually uses fixed[12]

    companion object {
        fun fromLogicalTypeName(name: String): AvroLogicalType? {
            return values().firstOrNull { it.logicalTypeName == name }
        }
    }
}

/**
 * Avro complex types
 */
sealed class AvroType {
    /**
     * Primitive type
     */
    data class Primitive(val type: AvroPrimitiveType) : AvroType()

    /**
     * Logical type (extends a primitive type)
     */
    data class Logical(
        val logicalType: AvroLogicalType,
        val precision: Int? = null,  // For decimal
        val scale: Int? = null        // For decimal
    ) : AvroType()

    /**
     * Record type (like struct/object)
     */
    data class Record(
        val name: String,
        val namespace: String? = null,
        val doc: String? = null,
        val aliases: List<String> = emptyList(),
        val fields: List<Field>
    ) : AvroType() {
        data class Field(
            val name: String,
            val type: AvroType,
            val doc: String? = null,
            val default: Any? = null,
            val aliases: List<String> = emptyList()
        )
    }

    /**
     * Enum type
     */
    data class Enum(
        val name: String,
        val namespace: String? = null,
        val doc: String? = null,
        val aliases: List<String> = emptyList(),
        val symbols: List<String>
    ) : AvroType()

    /**
     * Array type
     */
    data class Array(val items: AvroType) : AvroType()

    /**
     * Map type (values only, keys are always strings)
     */
    data class Map(val values: AvroType) : AvroType()

    /**
     * Union type (multiple possible types)
     */
    data class Union(val types: List<AvroType>) : AvroType()

    /**
     * Fixed type (fixed-size byte array)
     */
    data class Fixed(
        val name: String,
        val namespace: String? = null,
        val size: Int,
        val aliases: List<String> = emptyList()
    ) : AvroType()
}

/**
 * Avro schema representation
 */
data class AvroSchema(
    val type: AvroType,
    val namespace: String? = null,
    val doc: String? = null
) {
    /**
     * Get the schema as a named type (if applicable)
     */
    fun getNamedType(): AvroType.Record? {
        return type as? AvroType.Record
    }

    /**
     * Get the schema as an enum (if applicable)
     */
    fun getEnumType(): AvroType.Enum? {
        return type as? AvroType.Enum
    }

    /**
     * Check if schema represents a nullable type (union with null)
     */
    fun isNullable(): Boolean {
        return when (val t = type) {
            is AvroType.Union -> t.types.any { it is AvroType.Primitive && it.type == AvroPrimitiveType.NULL }
            else -> false
        }
    }

    /**
     * Get the non-null type from a nullable union
     */
    fun getNonNullType(): AvroType? {
        return when (val t = type) {
            is AvroType.Union -> t.types.firstOrNull {
                !(it is AvroType.Primitive && it.type == AvroPrimitiveType.NULL)
            }
            else -> type
        }
    }
}

/**
 * USDL to Avro type mapping utilities
 */
object AvroTypeMapping {
    /**
     * Map USDL type name to Avro primitive type
     */
    fun usdlToAvroPrimitive(usdlType: String): AvroPrimitiveType? {
        return when (usdlType.lowercase()) {
            "string" -> AvroPrimitiveType.STRING
            "integer", "int" -> AvroPrimitiveType.INT
            "long" -> AvroPrimitiveType.LONG
            "number", "float" -> AvroPrimitiveType.FLOAT
            "double" -> AvroPrimitiveType.DOUBLE
            "boolean", "bool" -> AvroPrimitiveType.BOOLEAN
            "binary", "bytes" -> AvroPrimitiveType.BYTES
            "null" -> AvroPrimitiveType.NULL
            else -> null
        }
    }

    /**
     * Map USDL logical type to Avro logical type
     */
    fun usdlToAvroLogical(logicalTypeName: String): AvroLogicalType? {
        return AvroLogicalType.fromLogicalTypeName(logicalTypeName)
    }

    /**
     * Get the Avro base type name for a logical type
     */
    fun getBaseTypeName(logicalType: AvroLogicalType): String {
        return logicalType.baseType.avroName
    }
}
