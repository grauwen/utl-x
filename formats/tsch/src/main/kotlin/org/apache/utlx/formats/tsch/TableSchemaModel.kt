package org.apache.utlx.formats.tsch

/**
 * Data classes for Frictionless Table Schema structures
 *
 * Implements the Frictionless Table Schema specification:
 * https://specs.frictionlessdata.io/table-schema/
 */

/**
 * Table Schema field types (all 15 types from the spec)
 */
enum class TableSchemaFieldType {
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    DATE,
    TIME,
    DATETIME,
    YEAR,
    YEARMONTH,
    DURATION,
    GEOPOINT,
    GEOJSON,
    OBJECT,
    ARRAY,
    ANY
}

/**
 * Geopoint format options
 */
enum class TableSchemaGeopointFormat {
    DEFAULT,  // "lon, lat" (comma-separated in a string)
    ARRAY,    // [lon, lat] (JSON array)
    OBJECT    // {"lon": ..., "lat": ...} (JSON object)
}

/**
 * Table Schema field descriptor
 */
data class TableSchemaField(
    val name: String,
    val type: String = "string",
    val format: String? = null,
    val title: String? = null,
    val description: String? = null,
    val example: Any? = null,
    val constraints: TableSchemaConstraints? = null,
    val rdfType: String? = null,
    val trueValues: List<String>? = null,
    val falseValues: List<String>? = null,
    val decimalChar: String? = null,
    val groupChar: String? = null,
    val bareNumber: Boolean? = null
)

/**
 * Table Schema field constraints
 */
data class TableSchemaConstraints(
    val required: Boolean? = null,
    val unique: Boolean? = null,
    val enum: List<Any>? = null,
    val pattern: String? = null,
    val minimum: Any? = null,
    val maximum: Any? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null
)

/**
 * Table Schema foreign key definition
 */
data class TableSchemaForeignKey(
    val fields: List<String>,
    val reference: TableSchemaForeignKeyReference
)

/**
 * Table Schema foreign key reference
 */
data class TableSchemaForeignKeyReference(
    val resource: String,
    val fields: List<String>
)

/**
 * Table Schema root object
 */
data class TableSchema(
    val fields: List<TableSchemaField>,
    val primaryKey: List<String>? = null,
    val foreignKeys: List<TableSchemaForeignKey>? = null,
    val missingValues: List<String>? = null
)

/**
 * Bidirectional type mapping between Table Schema types and UTL-X types
 */
object TableSchemaTypeMapping {
    /**
     * Map Table Schema type to UTL-X type
     */
    fun toUtlxType(tschType: String): String {
        return when (tschType.lowercase()) {
            "string" -> "string"
            "integer" -> "integer"
            "number" -> "number"
            "boolean" -> "boolean"
            "date" -> "date"
            "time" -> "time"
            "datetime" -> "datetime"
            "year" -> "string"
            "yearmonth" -> "string"
            "duration" -> "string"
            "geopoint" -> "string"
            "geojson" -> "object"
            "object" -> "object"
            "array" -> "array"
            "any" -> "any"
            else -> "string"  // Default to string for unknown types
        }
    }

    /**
     * Map UTL-X type to Table Schema type
     */
    fun toTableSchemaType(utlxType: String): String {
        return when (utlxType.lowercase()) {
            "string" -> "string"
            "integer" -> "integer"
            "number" -> "number"
            "boolean" -> "boolean"
            "date" -> "date"
            "time" -> "time"
            "datetime" -> "datetime"
            "object" -> "object"
            "array" -> "array"
            "any" -> "any"
            "null" -> "string"
            else -> "string"  // Default to string for unknown types
        }
    }
}

/**
 * Metadata keys used in UDM for Table Schema elements
 */
object TableSchemaMetadata {
    const val SCHEMA_TYPE = "__schemaType"
    const val TSCH_SCHEMA = "tsch-schema"
    const val TSCH_FIELD = "tsch-field"
}
