package org.apache.utlx.formats.xsd

/**
 * Data classes for XSD-specific structures
 * Used for pattern detection and schema analysis
 */

/**
 * XSD Design Pattern (Russian Doll, Salami Slice, Venetian Blind, Garden of Eden)
 */
enum class XSDPattern {
    RUSSIAN_DOLL,      // Element=local, Type=local
    SALAMI_SLICE,      // Element=global, Type=local
    VENETIAN_BLIND,    // Element=local, Type=global (RECOMMENDED)
    GARDEN_OF_EDEN,    // Element=global, Type=global
    MIXED              // No clear pattern
}

/**
 * Scope of XSD declarations
 */
enum class XSDScope {
    GLOBAL,   // Top-level declarations in xs:schema
    LOCAL     // Nested declarations within types/elements
}

/**
 * XSD Version
 */
enum class XSDVersion {
    V1_0,  // XSD 1.0 (W3C Recommendation 2004)
    V1_1   // XSD 1.1 (W3C Recommendation 2012) - adds assertions, type alternatives
}

/**
 * Metadata keys used in UDM for XSD elements
 */
object XSDMetadata {
    const val SCHEMA_TYPE = "__schemaType"        // Type of schema construct (e.g., "xsd-element")
    const val SCOPE = "__scope"                   // "global" or "local"
    const val XSD_VERSION = "__xsdVersion"        // "1.0" or "1.1"
    const val TARGET_NAMESPACE = "__targetNamespace"  // Target namespace URI
}
