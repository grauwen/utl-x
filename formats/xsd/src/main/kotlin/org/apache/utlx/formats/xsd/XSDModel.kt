package org.apache.utlx.formats.xsd

/**
 * Data classes for XSD-specific structures
 * Used for pattern detection and schema analysis
 */

/**
 * XSD Design Pattern (7 recognized patterns)
 *
 * Main 4 patterns:
 * - Russian Doll: Local elements, local types (simple, isolated)
 * - Salami Slice: Global elements, local types (modular enterprise)
 * - Venetian Blind: Local elements, global types (RECOMMENDED - balanced reuse)
 * - Garden of Eden: Global elements, global types (frameworks, standards)
 *
 * Extended 3 patterns:
 * - Bologna Sandwich: Mix of global/local elements with global types (controlled hybrid)
 * - Chameleon Schema: Single-root with global types and inline types (shared components)
 * - Swiss Army Knife: Global elements and types but small (monolithic systems)
 */
enum class XSDPattern(val value: String) {
    // Main 4 patterns
    RUSSIAN_DOLL("russian-doll"),           // Element=local (1), Type=local (inline only)
    SALAMI_SLICE("salami-slice"),           // Element=global (≥2), Type=local (inline only)
    VENETIAN_BLIND("venetian-blind"),       // Element=local (≤1), Type=global (RECOMMENDED)
    GARDEN_OF_EDEN("garden-of-eden"),       // Element=global (≥2), Type=global (large schema)

    // Extended patterns
    BOLOGNA_SANDWICH("bologna-sandwich"),   // Element=global (≥2), Type=global+inline (hybrid)
    CHAMELEON_SCHEMA("chameleon-schema"),   // Element=local (≤1), Type=global+inline (variable scope)
    SWISS_ARMY_KNIFE("swiss-army-knife"),   // Element=global (≥2), Type=global (small ≤5 schema)

    UNDETECTABLE("undetectable")            // No clear pattern
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
    const val XSD_PATTERN = "__xsdPattern"        // Detected XSD design pattern (russian-doll, salami-slice, venetian-blind, garden-of-eden, bologna-sandwich, chameleon-schema, swiss-army-knife, undetectable)
    const val XSD_ELEMENT_DECLARATION = "__xsdElementDeclaration"  // "global" | "local"
    const val XSD_TYPE_DECLARATION = "__xsdTypeDeclaration"        // "global" | "local"
    const val XSD_GLOBAL_ELEMENTS = "__xsdGlobalElements"   // Count of global element declarations (as string)
    const val XSD_GLOBAL_TYPES = "__xsdGlobalTypes"         // Count of global type declarations (as string) - complexType + simpleType
    const val XSD_INLINE_TYPES = "__xsdInlineTypes"         // Count of inline/anonymous type declarations (as string)
}
