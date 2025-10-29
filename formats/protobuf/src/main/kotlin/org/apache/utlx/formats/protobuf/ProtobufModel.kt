package org.apache.utlx.formats.protobuf

/**
 * Protocol Buffers Schema Model (Proto3 Only)
 *
 * This file defines the data structures that represent a Protocol Buffers schema.
 * These models are used as an intermediate representation when:
 * 1. Serializing USDL → .proto text
 * 2. Parsing .proto text → USDL
 *
 * Design Notes:
 * - Proto3 only (no proto2 support)
 * - Schema-only (no binary data encoding)
 * - Multi-type schema support (1 .proto = N message types)
 */

/**
 * Represents a complete Protocol Buffers schema file (.proto)
 *
 * @property syntax The protobuf syntax version (always "proto3" for UTL-X)
 * @property packageName The package/namespace for the schema
 * @property imports List of imported .proto files (paths only, not resolved)
 * @property options File-level options (e.g., java_package, optimize_for)
 * @property messages Top-level message definitions
 * @property enums Top-level enum definitions
 * @property documentation File-level documentation comment
 */
data class ProtoFile(
    val syntax: String = "proto3",
    val packageName: String? = null,
    val imports: List<String> = emptyList(),
    val options: Map<String, String> = emptyMap(),
    val messages: List<ProtoMessage> = emptyList(),
    val enums: List<ProtoEnum> = emptyList(),
    val documentation: String? = null
)

/**
 * Represents a Protocol Buffers message definition
 *
 * Example:
 * ```protobuf
 * message Order {
 *   string id = 1;
 *   int64 total = 2;
 * }
 * ```
 *
 * @property name The message name (PascalCase)
 * @property fields List of field definitions
 * @property nestedMessages Nested message definitions
 * @property nestedEnums Nested enum definitions
 * @property oneofs Oneof group definitions
 * @property reserved Reserved field numbers and names
 * @property documentation Message-level documentation comment
 */
data class ProtoMessage(
    val name: String,
    val fields: List<ProtoField> = emptyList(),
    val nestedMessages: List<ProtoMessage> = emptyList(),
    val nestedEnums: List<ProtoEnum> = emptyList(),
    val oneofs: List<ProtoOneof> = emptyList(),
    val reserved: ProtoReserved? = null,
    val documentation: String? = null
)

/**
 * Represents a field in a message
 *
 * Example:
 * ```protobuf
 * string name = 1;
 * repeated int32 scores = 2;
 * map<string, int32> inventory = 3;
 * ```
 *
 * @property name The field name (snake_case)
 * @property number The field number (1-536,870,911, excluding 19000-19999)
 * @property type The field type (primitive, message, or enum name)
 * @property label Field label (optional, repeated, or default singular)
 * @property mapKeyType For map fields, the key type
 * @property mapValueType For map fields, the value type
 * @property documentation Field-level documentation comment
 * @property oneofGroup If part of oneof, the oneof group name
 */
data class ProtoField(
    val name: String,
    val number: Int,
    val type: String,
    val label: FieldLabel = FieldLabel.SINGULAR,
    val mapKeyType: String? = null,
    val mapValueType: String? = null,
    val documentation: String? = null,
    val oneofGroup: String? = null
) {
    enum class FieldLabel {
        SINGULAR,  // Default in proto3
        REPEATED,  // For arrays
        MAP        // For map types
    }
}

/**
 * Represents an enum definition
 *
 * Example:
 * ```protobuf
 * enum OrderStatus {
 *   ORDER_STATUS_UNSPECIFIED = 0;
 *   PENDING = 1;
 *   SHIPPED = 2;
 * }
 * ```
 *
 * @property name The enum name (PascalCase)
 * @property values List of enum value definitions
 * @property documentation Enum-level documentation comment
 */
data class ProtoEnum(
    val name: String,
    val values: List<ProtoEnumValue> = emptyList(),
    val documentation: String? = null
) {
    /**
     * Validates that the first enum value has ordinal 0 (proto3 requirement)
     */
    fun validate() {
        if (values.isEmpty()) {
            throw IllegalStateException("Enum '$name' must have at least one value")
        }
        if (values.first().number != 0) {
            throw IllegalStateException(
                "Enum '$name': First value '${values.first().name}' must have ordinal 0 (proto3 requirement). " +
                "Got: ${values.first().number}. " +
                "Suggestion: Add '${name.toUpperSnakeCase()}_UNSPECIFIED = 0' as the first value."
            )
        }
    }
}

/**
 * Represents an enum value
 *
 * @property name The enum value name (UPPER_SNAKE_CASE)
 * @property number The enum value number (ordinal)
 * @property documentation Value-level documentation comment
 */
data class ProtoEnumValue(
    val name: String,
    val number: Int,
    val documentation: String? = null
)

/**
 * Represents a oneof group (mutually exclusive fields)
 *
 * Example:
 * ```protobuf
 * message Payment {
 *   oneof payment_method {
 *     string credit_card = 1;
 *     string paypal = 2;
 *   }
 * }
 * ```
 *
 * @property name The oneof group name (snake_case)
 * @property fields List of fields in this oneof group
 * @property documentation Oneof-level documentation comment
 */
data class ProtoOneof(
    val name: String,
    val fields: List<ProtoField> = emptyList(),
    val documentation: String? = null
)

/**
 * Represents reserved field numbers and names
 *
 * Example:
 * ```protobuf
 * message Order {
 *   reserved 2, 15, 9 to 11;
 *   reserved "foo", "bar";
 * }
 * ```
 *
 * @property numbers Reserved individual field numbers
 * @property ranges Reserved field number ranges (inclusive)
 * @property names Reserved field names
 */
data class ProtoReserved(
    val numbers: List<Int> = emptyList(),
    val ranges: List<IntRange> = emptyList(),
    val names: List<String> = emptyList()
)

/**
 * Helper extension to convert PascalCase to UPPER_SNAKE_CASE
 */
private fun String.toUpperSnakeCase(): String {
    return this.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
}
