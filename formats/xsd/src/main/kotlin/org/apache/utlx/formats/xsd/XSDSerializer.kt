package org.apache.utlx.formats.xsd

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.xml.XMLSerializer
import java.io.Writer
import java.io.StringWriter

/**
 * XSD Serializer - Converts UDM to XML Schema Definition (XSD)
 *
 * Enforces XSD design patterns and best practices:
 * - Russian Doll: 1 global element, all types inline
 * - Salami Slice: All elements global, simple types only
 * - Venetian Blind: Types global, elements local
 * - Garden of Eden: All elements and types global
 *
 * Features:
 * - Automatic xs:annotation/xs:documentation injection
 * - Pattern validation
 * - Namespace management
 * - XSD 1.0 and 1.1 support
 */
class XSDSerializer(
    private val pattern: XSDPattern? = null,
    private val version: String = "1.0",
    private val addDocumentation: Boolean = true,
    private val elementFormDefault: String = "qualified",
    private val prettyPrint: Boolean = true
) {

    /**
     * XSD Design Patterns
     */
    enum class XSDPattern {
        /**
         * Russian Doll: Single global element with all types defined inline
         * Best for: Small schemas, encapsulated structures
         */
        RUSSIAN_DOLL,

        /**
         * Salami Slice: All elements global, minimal types
         * Best for: Flexible composition, element reuse
         */
        SALAMI_SLICE,

        /**
         * Venetian Blind: Global types, local elements
         * Best for: Type reuse, large schemas (most common pattern)
         */
        VENETIAN_BLIND,

        /**
         * Garden of Eden: All elements and types global
         * Best for: Maximum reusability, schema composition
         */
        GARDEN_OF_EDEN
    }

    /**
     * Serialize UDM to XSD string
     */
    fun serialize(udm: UDM): String {
        val writer = StringWriter()
        serialize(udm, writer)
        return writer.toString()
    }

    /**
     * Serialization modes
     */
    private enum class SerializationMode {
        LOW_LEVEL,      // User provides XSD XML structure
        UNIVERSAL_DSL   // User provides Universal Schema DSL
    }

    /**
     * Serialize UDM to XSD via Writer
     */
    fun serialize(udm: UDM, writer: Writer) {
        // Step 1: Detect mode and transform if needed
        val mode = detectMode(udm)
        val xsdStructure = when (mode) {
            SerializationMode.UNIVERSAL_DSL -> transformUniversalDSL(udm as UDM.Object)
            SerializationMode.LOW_LEVEL -> udm
        }

        // Step 2: Validate UDM represents valid XSD structure
        validateXSDStructure(xsdStructure)

        // Step 3: Inject documentation if requested
        val enhanced = if (addDocumentation) {
            injectDocumentation(xsdStructure)
        } else {
            xsdStructure
        }

        // Step 4: Enforce design pattern if specified
        if (pattern != null) {
            validatePattern(enhanced, pattern)
        }

        // Step 5: Add XSD version annotation if 1.1
        val final = if (version == "1.1") {
            addVersionAnnotation(enhanced)
        } else {
            enhanced
        }

        // Step 6: Serialize using XML serializer
        val xmlSerializer = XMLSerializer(
            prettyPrint = prettyPrint,
            includeDeclaration = true
        )
        xmlSerializer.serialize(final, writer, "xs:schema")
    }

    /**
     * Detect serialization mode based on UDM structure
     */
    private fun detectMode(udm: UDM): SerializationMode {
        return when (udm) {
            is UDM.Object -> {
                when {
                    // Low-level: Has XSD XML structure
                    udm.properties.containsKey("xs:schema") -> SerializationMode.LOW_LEVEL
                    udm.properties.keys.any { it.startsWith("xs:") } -> SerializationMode.LOW_LEVEL

                    // High-level: Has Universal Schema DSL
                    udm.properties.containsKey("types") -> SerializationMode.UNIVERSAL_DSL

                    // Default: Low-level
                    else -> SerializationMode.LOW_LEVEL
                }
            }
            else -> SerializationMode.LOW_LEVEL
        }
    }

    /**
     * Transform Universal Schema DSL to XSD UDM structure
     *
     * This is a simplified implementation supporting basic structure types.
     * Full implementation will be added in subsequent iterations.
     */
    private fun transformUniversalDSL(schema: UDM.Object): UDM {
        // Extract metadata
        val namespace = (schema.properties["namespace"] as? UDM.Scalar)?.value as? String
        val elemFormDefault = (schema.properties["elementFormDefault"] as? UDM.Scalar)?.value as? String ?: this.elementFormDefault

        // Extract types
        val types = schema.properties["types"] as? UDM.Object
            ?: throw IllegalArgumentException("Universal Schema DSL requires 'types' property")

        // Build XSD schema attributes
        val schemaAttrs = mutableMapOf<String, String>()
        schemaAttrs["@xmlns:xs"] = "http://www.w3.org/2001/XMLSchema"
        if (namespace != null) {
            schemaAttrs["@targetNamespace"] = namespace
        }
        schemaAttrs["@elementFormDefault"] = elemFormDefault

        // Generate XSD complex types (simplified - only structures for now)
        val xsdComplexTypes = mutableListOf<UDM>()

        types.properties.forEach { (typeName, typeDef) ->
            if (typeDef !is UDM.Object) return@forEach

            val kind = (typeDef.properties["kind"] as? UDM.Scalar)?.value as? String
            if (kind == "structure") {
                val fields = typeDef.properties["fields"] as? UDM.Array ?: return@forEach
                val doc = (typeDef.properties["documentation"] as? UDM.Scalar)?.value as? String

                // Generate xs:element for each field
                val elements = fields.elements.mapNotNull { fieldUdm ->
                    if (fieldUdm !is UDM.Object) return@mapNotNull null

                    val name = (fieldUdm.properties["name"] as? UDM.Scalar)?.value as? String ?: return@mapNotNull null
                    val type = (fieldUdm.properties["type"] as? UDM.Scalar)?.value as? String ?: return@mapNotNull null
                    val required = (fieldUdm.properties["required"] as? UDM.Scalar)?.value as? Boolean ?: false
                    val description = (fieldUdm.properties["description"] as? UDM.Scalar)?.value as? String

                    val elemAttrs = mutableMapOf("@name" to name, "@type" to "xs:$type")
                    if (!required) elemAttrs["@minOccurs"] = "0"

                    val elemProps = if (description != null) {
                        mapOf("_documentation" to UDM.Scalar(description))
                    } else emptyMap()

                    UDM.Object(
                        properties = elemProps,
                        attributes = elemAttrs,
                        name = "xs:element"
                    )
                }

                val typeProps = mutableMapOf<String, UDM>()
                typeProps["xs:sequence"] = UDM.Object(
                    properties = if (elements.isEmpty()) emptyMap() else mapOf(
                        "xs:element" to if (elements.size == 1) elements[0] else UDM.Array(elements)
                    ),
                    attributes = emptyMap(),
                    name = "xs:sequence"
                )

                if (doc != null) {
                    typeProps["_documentation"] = UDM.Scalar(doc)
                }

                xsdComplexTypes.add(UDM.Object(
                    properties = typeProps,
                    attributes = mapOf("@name" to typeName),
                    name = "xs:complexType"
                ))
            }
        }

        // Build schema properties
        val schemaProps = if (xsdComplexTypes.isEmpty()) {
            emptyMap()
        } else {
            mapOf(
                "xs:complexType" to if (xsdComplexTypes.size == 1) {
                    xsdComplexTypes[0]
                } else {
                    UDM.Array(xsdComplexTypes)
                }
            )
        }

        // Create schema UDM
        return UDM.Object(
            properties = mapOf(
                "xs:schema" to UDM.Object(
                    properties = schemaProps,
                    attributes = schemaAttrs,
                    name = "xs:schema"
                )
            ),
            attributes = emptyMap(),
            name = null
        )
    }

    /**
     * Validate that UDM has xs:schema root structure
     */
    private fun validateXSDStructure(udm: UDM) {
        when (udm) {
            is UDM.Object -> {
                // Check if it's a wrapped schema
                if (udm.properties.containsKey("xs:schema")) {
                    return // Valid: {"xs:schema": {...}}
                }

                // Check if it has xmlns:xs attribute (direct schema)
                if (udm.attributes.containsKey("@xmlns:xs") ||
                    udm.attributes.containsKey("xmlns:xs")) {
                    return // Valid: direct xs:schema object
                }

                // Allow if it looks like it will become a schema
                if (udm.properties.keys.any { it.startsWith("xs:") }) {
                    return // Valid: has XSD elements
                }

                throw IllegalArgumentException(
                    "UDM does not represent valid XSD structure. " +
                    "Expected xs:schema root element with xmlns:xs namespace. " +
                    "Got properties: ${udm.properties.keys}"
                )
            }
            else -> throw IllegalArgumentException(
                "UDM must be Object type for XSD serialization. Got: ${udm::class.simpleName}"
            )
        }
    }

    /**
     * Inject xs:annotation/xs:documentation for fields with _documentation property
     */
    private fun injectDocumentation(udm: UDM): UDM {
        return when (udm) {
            is UDM.Object -> {
                // Check if this element has _documentation property
                val documentation = udm.properties["_documentation"] as? UDM.Scalar

                val enhancedProperties = if (documentation != null) {
                    // Remove _documentation and add xs:annotation
                    val newProps = udm.properties.toMutableMap()
                    newProps.remove("_documentation")

                    // Add xs:annotation with xs:documentation
                    newProps["xs:annotation"] = UDM.Object(
                        properties = mapOf(
                            "xs:documentation" to documentation
                        ),
                        attributes = emptyMap(),
                        name = "xs:annotation"
                    )

                    newProps
                } else {
                    udm.properties
                }

                // Recursively process all properties
                UDM.Object(
                    properties = enhancedProperties.mapValues { (_, value) ->
                        injectDocumentation(value)
                    },
                    attributes = udm.attributes,
                    name = udm.name,
                    metadata = udm.metadata
                )
            }
            is UDM.Array -> {
                UDM.Array(udm.elements.map { injectDocumentation(it) })
            }
            else -> udm
        }
    }

    /**
     * Validate UDM conforms to specified XSD design pattern
     */
    private fun validatePattern(udm: UDM, pattern: XSDPattern) {
        val schema = extractSchema(udm)
        val globalElements = countGlobalElements(schema)
        val globalTypes = countGlobalTypes(schema)
        val hasLocalElements = hasLocalElements(schema)
        val hasLocalTypes = hasLocalTypes(schema)

        when (pattern) {
            XSDPattern.RUSSIAN_DOLL -> {
                require(globalElements == 1) {
                    "Russian Doll pattern requires exactly 1 global element. Found: $globalElements"
                }
                require(globalTypes == 0) {
                    "Russian Doll pattern requires 0 global types (all inline). Found: $globalTypes"
                }
            }

            XSDPattern.SALAMI_SLICE -> {
                require(globalElements > 1) {
                    "Salami Slice pattern requires multiple global elements. Found: $globalElements"
                }
                require(globalTypes == 0 || !hasLocalTypes) {
                    "Salami Slice pattern should minimize types (prefer elements)"
                }
            }

            XSDPattern.VENETIAN_BLIND -> {
                require(globalTypes > 0) {
                    "Venetian Blind pattern requires global types. Found: $globalTypes"
                }
                require(hasLocalElements || globalElements > 0) {
                    "Venetian Blind pattern should have elements (local or global)"
                }
            }

            XSDPattern.GARDEN_OF_EDEN -> {
                require(globalElements > 0) {
                    "Garden of Eden pattern requires global elements. Found: $globalElements"
                }
                require(globalTypes > 0) {
                    "Garden of Eden pattern requires global types. Found: $globalTypes"
                }
                require(!hasLocalElements && !hasLocalTypes) {
                    "Garden of Eden pattern requires all elements and types to be global"
                }
            }
        }
    }

    /**
     * Extract schema object from potentially wrapped UDM
     */
    private fun extractSchema(udm: UDM): UDM.Object {
        return when (udm) {
            is UDM.Object -> {
                // Check if wrapped
                (udm.properties["xs:schema"] as? UDM.Object) ?: udm
            }
            else -> throw IllegalArgumentException("Expected Object")
        }
    }

    /**
     * Count global xs:element declarations
     */
    private fun countGlobalElements(schema: UDM.Object): Int {
        val elements = schema.properties["xs:element"]
        return when (elements) {
            is UDM.Array -> elements.elements.size
            is UDM.Object -> 1
            null -> 0
            else -> 0
        }
    }

    /**
     * Count global xs:complexType and xs:simpleType declarations
     */
    private fun countGlobalTypes(schema: UDM.Object): Int {
        val complexTypes = schema.properties["xs:complexType"]
        val simpleTypes = schema.properties["xs:simpleType"]

        val complexCount = when (complexTypes) {
            is UDM.Array -> complexTypes.elements.size
            is UDM.Object -> 1
            else -> 0
        }

        val simpleCount = when (simpleTypes) {
            is UDM.Array -> simpleTypes.elements.size
            is UDM.Object -> 1
            else -> 0
        }

        return complexCount + simpleCount
    }

    /**
     * Check if schema contains local element definitions
     */
    private fun hasLocalElements(schema: UDM.Object): Boolean {
        // Simplified check - looks for xs:element inside xs:complexType/xs:sequence
        return searchForPattern(schema, listOf("xs:complexType", "xs:sequence", "xs:element"))
    }

    /**
     * Check if schema contains local type definitions
     */
    private fun hasLocalTypes(schema: UDM.Object): Boolean {
        // Simplified check - looks for inline xs:complexType or xs:simpleType
        return searchForPattern(schema, listOf("xs:element", "xs:complexType")) ||
               searchForPattern(schema, listOf("xs:element", "xs:simpleType"))
    }

    /**
     * Search for nested pattern in UDM structure
     */
    private fun searchForPattern(udm: UDM, path: List<String>): Boolean {
        if (path.isEmpty()) return true

        return when (udm) {
            is UDM.Object -> {
                val nextKey = path.first()
                val remaining = path.drop(1)

                val value = udm.properties[nextKey]
                when {
                    value != null && remaining.isEmpty() -> true
                    value != null -> searchForPattern(value, remaining)
                    else -> udm.properties.values.any { searchForPattern(it, path) }
                }
            }
            is UDM.Array -> {
                udm.elements.any { searchForPattern(it, path) }
            }
            else -> false
        }
    }

    /**
     * Add XSD 1.1 version annotation
     */
    private fun addVersionAnnotation(udm: UDM): UDM {
        return when (udm) {
            is UDM.Object -> {
                val schema = if (udm.properties.containsKey("xs:schema")) {
                    udm.properties["xs:schema"] as UDM.Object
                } else {
                    udm
                }

                // Add vc:minVersion attribute for XSD 1.1
                val enhancedAttrs = schema.attributes.toMutableMap()
                enhancedAttrs["@xmlns:vc"] = "http://www.w3.org/2007/XMLSchema-versioning"
                enhancedAttrs["@vc:minVersion"] = "1.1"

                val enhancedSchema = UDM.Object(
                    properties = schema.properties,
                    attributes = enhancedAttrs,
                    name = schema.name,
                    metadata = schema.metadata
                )

                // Re-wrap if originally wrapped
                if (udm.properties.containsKey("xs:schema")) {
                    UDM.Object(
                        properties = mapOf("xs:schema" to enhancedSchema),
                        attributes = udm.attributes,
                        name = udm.name,
                        metadata = udm.metadata
                    )
                } else {
                    enhancedSchema
                }
            }
            else -> udm
        }
    }
}
