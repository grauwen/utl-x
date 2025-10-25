package org.apache.utlx.formats.jsch

/**
 * Data classes for JSON Schema-specific structures
 */

/**
 * JSON Schema Version
 */
enum class JSONSchemaVersion(val uri: String) {
    DRAFT_04("http://json-schema.org/draft-04/schema#"),
    DRAFT_07("http://json-schema.org/draft-07/schema#"),
    DRAFT_2020_12("https://json-schema.org/draft/2020-12/schema");

    companion object {
        fun fromUri(uri: String): JSONSchemaVersion? {
            return values().firstOrNull { uri.contains(it.name.lowercase().replace("_", "-")) }
        }
    }
}

/**
 * JSON Schema Types
 */
enum class JSONSchemaType {
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    OBJECT,
    ARRAY,
    NULL
}

/**
 * Metadata keys used in UDM for JSON Schema elements
 */
object JSONSchemaMetadata {
    const val SCHEMA_TYPE = "__schemaType"        // Type of schema construct (e.g., "jsch-property")
    const val VERSION = "__version"               // "draft-04", "draft-07", or "2020-12"
}
