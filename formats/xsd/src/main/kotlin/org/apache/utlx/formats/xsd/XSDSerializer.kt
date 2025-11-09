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
 * - Pattern preservation (Russian Doll inline types)
 */
class XSDSerializer(
    private val pattern: XSDPattern? = null,
    private val version: String = "1.0",
    private val addDocumentation: Boolean = true,
    private val elementFormDefault: String = "qualified",
    private val prettyPrint: Boolean = true,
    private val preservePattern: Boolean = true
) {

    /**
     * XSD Design Patterns
     */
    enum class XSDPattern(val value: String) {
        /**
         * Russian Doll: Single global element with all types defined inline
         * Best for: Small schemas, encapsulated structures
         */
        RUSSIAN_DOLL("russian-doll"),

        /**
         * Salami Slice: All elements global, minimal types
         * Best for: Flexible composition, element reuse
         */
        SALAMI_SLICE("salami-slice"),

        /**
         * Venetian Blind: Global types, local elements
         * Best for: Type reuse, large schemas (most common pattern)
         */
        VENETIAN_BLIND("venetian-blind"),

        /**
         * Garden of Eden: All elements and types global
         * Best for: Maximum reusability, schema composition
         */
        GARDEN_OF_EDEN("garden-of-eden")
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

                    // USDL mode: Has %types directive
                    udm.properties.containsKey("%types") -> SerializationMode.UNIVERSAL_DSL

                    // Default: Low-level
                    else -> SerializationMode.LOW_LEVEL
                }
            }
            else -> SerializationMode.LOW_LEVEL
        }
    }

    /**
     * Partition types into inline vs global based on %xsdInline metadata
     *
     * Returns Pair(inlineTypes, globalTypes) where:
     * - inlineTypes: Map of typeName -> (typeDef, elementName)
     * - globalTypes: Map of typeName -> typeDef
     */
    private fun partitionTypes(types: UDM.Object): Pair<Map<String, Pair<UDM.Object, String>>, Map<String, UDM.Object>> {
        val inlineTypes = mutableMapOf<String, Pair<UDM.Object, String>>()
        val globalTypes = mutableMapOf<String, UDM.Object>()

        types.properties.forEach { (typeName, typeDef) ->
            if (typeDef !is UDM.Object) return@forEach

            // Check for %xsdInline directive
            val isInline = (typeDef.properties["%xsdInline"] as? UDM.Scalar)?.value as? Boolean ?: false

            if (isInline && preservePattern) {
                // Extract %xsdElement directive (parent element name)
                val elementName = (typeDef.properties["%xsdElement"] as? UDM.Scalar)?.value as? String
                    ?: typeName.removeSuffix("_InlineType")

                inlineTypes[typeName] = Pair(typeDef, elementName)
            } else {
                // Global type (Venetian Blind style)
                globalTypes[typeName] = typeDef
            }
        }

        return Pair(inlineTypes, globalTypes)
    }

    /**
     * Generate xs:element with inline xs:complexType (Russian Doll style)
     *
     * Example output:
     * <xs:element name="order">
     *   <xs:complexType>
     *     <xs:sequence>
     *       <xs:element name="orderId" type="xs:string"/>
     *     </xs:sequence>
     *   </xs:complexType>
     * </xs:element>
     */
    private fun generateInlineElement(
        elementName: String,
        typeDef: UDM.Object
    ): UDM {
        // Extract %fields directive
        val fields = typeDef.properties["%fields"] as? UDM.Array ?: return UDM.Object(
            properties = emptyMap(),
            attributes = mapOf("name" to elementName, "type" to "xs:string"),
            name = "xs:element"
        )

        // Extract %documentation directive
        val doc = (typeDef.properties["%documentation"] as? UDM.Scalar)?.value as? String

        // Generate xs:element for each field (same as global type generation)
        val elements = fields.elements.mapNotNull { fieldUdm ->
            if (fieldUdm !is UDM.Object) return@mapNotNull null

            val name = (fieldUdm.properties["%name"] as? UDM.Scalar)?.value as? String ?: return@mapNotNull null
            val type = (fieldUdm.properties["%type"] as? UDM.Scalar)?.value as? String ?: return@mapNotNull null
            val required = (fieldUdm.properties["%required"] as? UDM.Scalar)?.value as? Boolean ?: false
            val description = (fieldUdm.properties["%description"] as? UDM.Scalar)?.value as? String

            val elemAttrs = mutableMapOf("name" to name, "type" to "xs:$type")
            if (!required) elemAttrs["minOccurs"] = "0"

            val elemProps = if (description != null) {
                mapOf("_documentation" to UDM.Scalar(description))
            } else emptyMap()

            UDM.Object(
                properties = elemProps,
                attributes = elemAttrs,
                name = "xs:element"
            )
        }

        // Build inline xs:complexType
        val complexTypeProps = mutableMapOf<String, UDM>()

        if (doc != null) {
            complexTypeProps["_documentation"] = UDM.Scalar(doc)
        }

        complexTypeProps["xs:sequence"] = UDM.Object(
            properties = if (elements.isEmpty()) emptyMap() else mapOf(
                "xs:element" to if (elements.size == 1) elements[0] else UDM.Array(elements)
            ),
            attributes = emptyMap(),
            name = "xs:sequence"
        )

        val inlineComplexType = UDM.Object(
            properties = complexTypeProps,
            attributes = emptyMap(),  // No @name attribute for inline types
            name = "xs:complexType"
        )

        // Build xs:element with nested xs:complexType
        return UDM.Object(
            properties = mapOf("xs:complexType" to inlineComplexType),
            attributes = mapOf("name" to elementName),
            name = "xs:element"
        )
    }

    /**
     * Transform USDL (Universal Schema Definition Language) to XSD UDM structure
     *
     * Supports USDL 1.0 Tier 1 and Tier 2 directives for XSD generation.
     *
     * Required USDL directives:
     * - %types: Object mapping type names to type definitions
     * - %kind: "structure" for complex types
     * - %fields: Array of field definitions
     * - %name: Field name
     * - %type: Field type
     *
     * Optional USDL directives:
     * - %namespace: Target namespace URI
     * - %elementFormDefault: "qualified" or "unqualified"
     * - %documentation: Type-level documentation
     * - %description: Field-level documentation
     * - %required: Boolean indicating if field is required
     *
     * XSD Pattern Preservation (Tier 3):
     * - %xsdInline: Boolean marking inline/anonymous types
     * - %xsdElement: Parent element name for inline types
     * - %xsdPattern: Original XSD pattern ("russian-doll", etc.)
     */
    private fun transformUniversalDSL(schema: UDM.Object): UDM {
        // Extract metadata using USDL % directives
        val namespace = (schema.properties["%namespace"] as? UDM.Scalar)?.value as? String
        val elemFormDefault = (schema.properties["%elementFormDefault"] as? UDM.Scalar)?.value as? String ?: this.elementFormDefault

        // Extract types using %types directive
        val types = schema.properties["%types"] as? UDM.Object
            ?: throw IllegalArgumentException("USDL schema requires '%types' directive")

        // Build XSD schema attributes (no @ prefix - that's syntax only, not UDM)
        val schemaAttrs = mutableMapOf<String, String>()
        schemaAttrs["xmlns:xs"] = "http://www.w3.org/2001/XMLSchema"
        if (namespace != null) {
            schemaAttrs["targetNamespace"] = namespace
        }
        schemaAttrs["elementFormDefault"] = elemFormDefault

        // Partition types into inline (Russian Doll) vs global (Venetian Blind)
        val (inlineTypes, globalTypes) = partitionTypes(types)

        // Generate inline xs:element nodes (Russian Doll pattern)
        val xsdElements = mutableListOf<UDM>()
        inlineTypes.forEach { (_, typeInfo) ->
            val (typeDef, elementName) = typeInfo
            val kind = (typeDef.properties["%kind"] as? UDM.Scalar)?.value as? String
            if (kind == "structure") {
                xsdElements.add(generateInlineElement(elementName, typeDef))
            }
        }

        // Generate global xs:complexType nodes (Venetian Blind pattern)
        val xsdComplexTypes = mutableListOf<UDM>()
        globalTypes.forEach { (typeName, typeDef) ->
            if (typeDef !is UDM.Object) return@forEach

            // Check %kind directive
            val kind = (typeDef.properties["%kind"] as? UDM.Scalar)?.value as? String
            if (kind == "structure") {
                // Extract %fields directive
                val fields = typeDef.properties["%fields"] as? UDM.Array ?: return@forEach

                // Extract %documentation directive
                val doc = (typeDef.properties["%documentation"] as? UDM.Scalar)?.value as? String

                // Generate xs:element for each field
                val elements = fields.elements.mapNotNull { fieldUdm ->
                    if (fieldUdm !is UDM.Object) return@mapNotNull null

                    // Extract field directives: %name, %type, %required, %description
                    val name = (fieldUdm.properties["%name"] as? UDM.Scalar)?.value as? String ?: return@mapNotNull null
                    val type = (fieldUdm.properties["%type"] as? UDM.Scalar)?.value as? String ?: return@mapNotNull null
                    val required = (fieldUdm.properties["%required"] as? UDM.Scalar)?.value as? Boolean ?: false
                    val description = (fieldUdm.properties["%description"] as? UDM.Scalar)?.value as? String

                    val elemAttrs = mutableMapOf("name" to name, "type" to "xs:$type")
                    if (!required) elemAttrs["minOccurs"] = "0"

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

                // Add documentation first (xs:annotation must come before xs:sequence in XSD)
                if (doc != null) {
                    typeProps["_documentation"] = UDM.Scalar(doc)
                }

                // Then add sequence
                typeProps["xs:sequence"] = UDM.Object(
                    properties = if (elements.isEmpty()) emptyMap() else mapOf(
                        "xs:element" to if (elements.size == 1) elements[0] else UDM.Array(elements)
                    ),
                    attributes = emptyMap(),
                    name = "xs:sequence"
                )

                xsdComplexTypes.add(UDM.Object(
                    properties = typeProps,
                    attributes = mapOf("name" to typeName),
                    name = "xs:complexType"
                ))
            }
        }

        // Build schema properties (combine inline elements and global types)
        val schemaProps = mutableMapOf<String, UDM>()

        // Add inline elements first (Russian Doll pattern)
        if (xsdElements.isNotEmpty()) {
            schemaProps["xs:element"] = if (xsdElements.size == 1) {
                xsdElements[0]
            } else {
                UDM.Array(xsdElements)
            }
        }

        // Add global complex types (Venetian Blind pattern)
        if (xsdComplexTypes.isNotEmpty()) {
            schemaProps["xs:complexType"] = if (xsdComplexTypes.size == 1) {
                xsdComplexTypes[0]
            } else {
                UDM.Array(xsdComplexTypes)
            }
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
                if (udm.attributes.containsKey("xmlns:xs")) {
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
                    // Create annotation element
                    val annotation = UDM.Object(
                        properties = mapOf(
                            "xs:documentation" to documentation
                        ),
                        attributes = emptyMap(),
                        name = "xs:annotation"
                    )

                    // Build new properties with annotation first, then other properties (except _documentation)
                    linkedMapOf<String, UDM>().apply {
                        put("xs:annotation", annotation)
                        udm.properties.forEach { (key, value) ->
                            if (key != "_documentation") {
                                put(key, value)
                            }
                        }
                    }
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
                enhancedAttrs["xmlns:vc"] = "http://www.w3.org/2007/XMLSchema-versioning"
                enhancedAttrs["vc:minVersion"] = "1.1"

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
