package org.apache.utlx.formats.xsd

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.xml.XMLParser
import java.io.Reader
import java.io.StringReader

/**
 * XSD Parser - Converts XML Schema Definition (XSD) files to UDM
 *
 * Supports:
 * - XSD 1.0 and XSD 1.1
 * - All 4 design patterns (Russian Doll, Salami Slice, Venetian Blind, Garden of Eden)
 * - xs:annotation, xs:documentation, xs:appinfo
 * - Namespaces, imports, includes
 * - Pattern detection via scope tagging
 *
 * Mapping:
 * - XSD elements → UDM.Object with __schemaType metadata
 * - Global declarations → tagged with __scope: "global"
 * - Local declarations → tagged with __scope: "local"
 * - Annotations → preserved in properties
 *
 * Example:
 * <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
 *   <xs:element name="customer" type="CustomerType"/>
 * </xs:schema>
 *
 * Becomes:
 * UDM.Object(
 *   name = "schema",
 *   properties = mapOf(
 *     "element" to UDM.Object(
 *       attributes = mapOf("name" to "customer", "type" to "CustomerType"),
 *       metadata = mapOf(
 *         "__schemaType" to "xsd-element",
 *         "__scope" to "global",
 *         "__xsdVersion" to "1.0"
 *       )
 *     )
 *   ),
 *   metadata = mapOf(
 *     "__schemaType" to "xsd-schema",
 *     "__xsdVersion" to "1.0",
 *     "__targetNamespace" to "http://example.com/schema"
 *   )
 * )
 */
class XSDParser(
    private val source: Reader,
    private val arrayHints: Set<String> = emptySet()
) {
    constructor(xsd: String, arrayHints: Set<String> = emptySet()) : this(StringReader(xsd), arrayHints)

    /**
     * Parse XSD to UDM
     */
    fun parse(): UDM {
        // Step 1: Parse as XML with array hints
        val xmlParser = XMLParser(source, arrayHints)
        val xmlUDM = xmlParser.parse()
        if (xmlUDM is UDM.Object) {
        }

        // Step 2: Enhance with XSD-specific metadata
        val enhanced = enhanceWithXSDMetadata(xmlUDM)
        if (enhanced is UDM.Object) {
        }

        // Step 3: Unwrap if XMLParser wrapped the document
        // Return the actual xs:schema element, not the wrapper
        // Check if this is a wrapper object (has null name and contains xs:schema property)
        if (enhanced is UDM.Object) {
            val schemaKey = enhanced.properties.keys.firstOrNull { key ->
                key == "xs:schema" || key == "xsd:schema" || key == "schema"
            }
            if (schemaKey != null && enhanced.properties[schemaKey] is UDM.Object) {
                // Return the unwrapped schema element
                return enhanced.properties[schemaKey]!!
            } else {
            }
        } else {
        }

        return enhanced
    }

    /**
     * Enhance XML UDM with XSD-specific metadata
     * - Detect XSD version
     * - Tag elements with schema types
     * - Tag scope (global vs local)
     * - Extract namespace information
     */
    private fun enhanceWithXSDMetadata(udm: UDM): UDM {
        return when (udm) {
            is UDM.Object -> {
                // XMLParser wraps the document in a root object with name = null
                // The actual xs:schema element is in the properties
                if (udm.name == null) {
                    // Look for xs:schema, xsd:schema, or schema in properties
                    val schemaKey = udm.properties.keys.firstOrNull { key ->
                        key == "xs:schema" || key == "xsd:schema" || key == "schema"
                    }

                    if (schemaKey != null && udm.properties[schemaKey] is UDM.Object) {
                        // Found the schema element - enhance it and return wrapped result
                        val schemaElement = udm.properties[schemaKey] as UDM.Object
                        val enhancedSchema = enhanceSchemaElement(schemaElement)
                        return UDM.Object(
                            properties = mapOf(schemaKey to enhancedSchema),
                            attributes = udm.attributes,
                            name = udm.name,
                            metadata = udm.metadata
                        )
                    }
                }

                // Check if this is an xs:schema element itself
                // Look for xmlns:xs or xmlns:xsd namespace declarations
                val hasXSNamespace = udm.attributes.any { (key, value) ->
                    (key == "xmlns:xs" || key == "xmlns:xsd" || key == "xmlns") &&
                    value.contains("XMLSchema")
                }

                val elementName = udm.name?.substringAfter(":") ?: ""
                val isSchema = (elementName == "schema" && hasXSNamespace) ||
                               udm.attributes.containsKey("targetNamespace") ||
                               udm.name == "xs:schema" ||
                               udm.name == "xsd:schema"

                if (isSchema) {
                    enhanceSchemaElement(udm)
                } else {
                    // Recursively enhance nested objects
                    UDM.Object(
                        properties = udm.properties.mapValues { (_, value) ->
                            enhanceWithXSDMetadata(value)
                        },
                        attributes = udm.attributes,
                        name = udm.name,
                        metadata = enhanceMetadata(udm)
                    )
                }
            }
            is UDM.Array -> {
                UDM.Array(udm.elements.map { enhanceWithXSDMetadata(it) })
            }
            else -> udm
        }
    }

    /**
     * Enhance xs:schema root element
     */
    private fun enhanceSchemaElement(schema: UDM.Object): UDM.Object {
        // Detect XSD version
        val xsdVersion = detectXSDVersion(schema)

        // Extract target namespace
        val targetNamespace = schema.attributes["targetNamespace"] ?: ""

        // Enhance all child elements with scope tagging
        val enhancedProperties = schema.properties.mapValues { (key, value) ->
            tagWithScope(value, isTopLevel = true)
        }

        // Count global declarations for pattern detection
        val globalElementCount = countGlobalDeclarations(enhancedProperties, "xsd-element")
        val globalTypeCount = countGlobalDeclarations(enhancedProperties, "xsd-complexType") +
                             countGlobalDeclarations(enhancedProperties, "xsd-simpleType")
        val inlineTypeCount = countInlineTypes(enhancedProperties)

        // Detect XSD design pattern
        val detectedPattern = detectXSDPattern(globalElementCount, globalTypeCount, inlineTypeCount)

        val newMetadata = schema.metadata + mapOf(
            "__schemaType" to "xsd-schema",
            "__xsdVersion" to xsdVersion,
            "__targetNamespace" to targetNamespace,
            "__xsdPattern" to detectedPattern,
            "__xsdGlobalElements" to globalElementCount.toString(),
            "__xsdGlobalTypes" to globalTypeCount.toString(),
            "__xsdInlineTypes" to inlineTypeCount.toString()
        )
        return UDM.Object(
            properties = enhancedProperties,
            attributes = schema.attributes,
            name = schema.name,
            metadata = newMetadata
        )
    }

    /**
     * Detect XSD version (1.0 or 1.1)
     * XSD 1.1 uses vc:minVersion="1.1" attribute
     */
    private fun detectXSDVersion(schema: UDM.Object): String {
        return schema.attributes["vc:minVersion"] ?: "1.0"
    }

    /**
     * Tag elements and types with scope (global or local)
     * This enables pattern detection
     */
    private fun tagWithScope(udm: UDM, isTopLevel: Boolean): UDM {
        return when (udm) {
            is UDM.Object -> {
                val elementName = udm.name?.substringAfter(":")  ?: ""

                // Determine if this is a schema construct
                val schemaType = when (elementName) {
                    "element" -> "xsd-element"
                    "complexType" -> "xsd-complexType"
                    "simpleType" -> "xsd-simpleType"
                    "attribute" -> "xsd-attribute"
                    "annotation" -> "xsd-annotation"
                    "documentation" -> "xsd-documentation"
                    "appinfo" -> "xsd-appinfo"
                    "sequence" -> "xsd-sequence"
                    "choice" -> "xsd-choice"
                    "all" -> "xsd-all"
                    "group" -> "xsd-group"
                    "attributeGroup" -> "xsd-attributeGroup"
                    "import" -> "xsd-import"
                    "include" -> "xsd-include"
                    else -> null
                }

                // Determine scope
                val scope = if (isTopLevel && schemaType in listOf("xsd-element", "xsd-complexType", "xsd-simpleType")) {
                    "global"
                } else {
                    "local"
                }

                // Recursively tag children (not top-level anymore)
                val enhancedProperties = udm.properties.mapValues { (_, value) ->
                    tagWithScope(value, isTopLevel = false)
                }

                // Add metadata
                val newMetadata = udm.metadata.toMutableMap()
                if (schemaType != null) {
                    newMetadata["__schemaType"] = schemaType
                    newMetadata["__scope"] = scope

                    // Add specific declaration type metadata
                    when (schemaType) {
                        "xsd-element" -> newMetadata["__xsdElementDeclaration"] = scope
                        "xsd-complexType", "xsd-simpleType" -> newMetadata["__xsdTypeDeclaration"] = scope
                    }
                }

                UDM.Object(
                    properties = enhancedProperties,
                    attributes = udm.attributes,
                    name = udm.name,
                    metadata = newMetadata
                )
            }
            is UDM.Array -> {
                UDM.Array(udm.elements.map { tagWithScope(it, isTopLevel) })
            }
            else -> udm
        }
    }

    /**
     * Enhance metadata for non-schema elements
     */
    private fun enhanceMetadata(udm: UDM.Object): Map<String, String> {
        val metadata = udm.metadata.toMutableMap()

        // If this looks like an XSD element but wasn't caught earlier
        val elementName = udm.name?.substringAfter(":") ?: ""
        if (elementName in XSD_ELEMENTS) {
            metadata["__schemaType"] = "xsd-$elementName"
        }

        return metadata
    }

    /**
     * Convert XSD schema to USDL format
     *
     * This is the inverse of XSDSerializer.transformUniversalDSL()
     * Converts XSD structure with metadata → USDL with % directives
     */
    fun toUSDL(xsdSchema: UDM): UDM {
        if (xsdSchema !is UDM.Object) {
            throw IllegalArgumentException("XSD schema must be an object")
        }

        // Extract namespace from schema attributes
        val namespace = xsdSchema.attributes["targetNamespace"]

        // Extract types from schema
        val complexTypes = extractComplexTypes(xsdSchema)
        val simpleTypes = extractSimpleTypes(xsdSchema)
        val inlineTypes = extractInlineTypes(xsdSchema)
        val globalElements = extractGlobalElements(xsdSchema)

        // Detect XSD design pattern
        val detectedPattern = detectXSDPattern(
            globalElementCount = globalElements.size,
            globalTypeCount = complexTypes.size + simpleTypes.size,
            inlineTypeCount = inlineTypes.size
        )

        // Build %types object
        val typesMap = mutableMapOf<String, UDM>()

        // Add named complex types (Venetian Blind, Salami Slice, Garden of Eden patterns)
        complexTypes.forEach { (name, type) ->
            typesMap[name] = complexTypeToUSDL(type)
        }

        // Add simple types (enumerations, restrictions)
        simpleTypes.forEach { (name, type) ->
            typesMap[name] = simpleTypeToUSDL(type)
        }

        // Add inline types with metadata (Russian Doll pattern)
        inlineTypes.forEach { (syntheticName, typeInfo) ->
            val (complexType, elementName) = typeInfo
            val usdlType = complexTypeToUSDL(complexType)

            // Add XSD-specific metadata directives
            typesMap[syntheticName] = addInlineMetadata(usdlType, elementName)
        }

        // Build top-level USDL object
        val topLevelProps = mutableMapOf<String, UDM>(
            "%types" to UDM.Object(properties = typesMap)
        )

        // Add top-level namespace if present
        if (namespace != null) {
            topLevelProps["%namespace"] = UDM.Scalar(namespace)
        }

        // Add detected pattern metadata
        topLevelProps["%xsdPattern"] = UDM.Scalar(detectedPattern)

        // Add pattern statistics for visualization
        topLevelProps["%xsdPatternStats"] = UDM.Object(properties = mapOf(
            "globalElements" to UDM.Scalar(globalElements.size),
            "globalTypes" to UDM.Scalar(complexTypes.size + simpleTypes.size),
            "inlineTypes" to UDM.Scalar(inlineTypes.size),
            "detectedPattern" to UDM.Scalar(detectedPattern)
        ))

        return UDM.Object(properties = topLevelProps)
    }

    /**
     * Detect XSD design pattern based on schema structure
     *
     * Pattern detection rules (order matters - checked from most specific to least):
     * - Garden of Eden: Both global elements AND global types (most specific)
     * - Salami Slice: Multiple global elements, no global types (element reuse)
     * - Russian Doll: Single global element with inline types
     * - Venetian Blind: Global types, any elements (type reuse - default/fallback)
     */
    private fun detectXSDPattern(
        globalElementCount: Int,
        globalTypeCount: Int,
        inlineTypeCount: Int
    ): String {
        return when {
            // Garden of Eden: Both global elements and global types (most specific pattern)
            globalElementCount >= 1 && globalTypeCount >= 1 && inlineTypeCount == 0 -> "garden-of-eden"

            // Salami Slice: Multiple global elements, no global types (prioritize over Russian Doll)
            globalElementCount >= 2 && globalTypeCount == 0 -> "salami-slice"

            // Russian Doll: Single global element with inline types
            globalElementCount == 1 && inlineTypeCount > 0 && globalTypeCount == 0 -> "russian-doll"

            // Venetian Blind: Has global types (most common pattern - default)
            globalTypeCount >= 1 && inlineTypeCount == 0 -> "venetian-blind"

            // Default/Undetectable (doesn't fit standard patterns)
            else -> "undetectable"
        }
    }

    /**
     * Count global declarations of a specific schema type
     */
    private fun countGlobalDeclarations(properties: Map<String, UDM>, schemaType: String): Int {
        return properties.values.sumOf { value ->
            when (value) {
                is UDM.Object -> {
                    if (value.metadata["__schemaType"] == schemaType &&
                        value.metadata["__scope"] == "global") 1 else 0
                }
                is UDM.Array -> {
                    value.elements.count { element ->
                        element is UDM.Object &&
                        element.metadata["__schemaType"] == schemaType &&
                        element.metadata["__scope"] == "global"
                    }
                }
                else -> 0
            }
        }
    }

    /**
     * Count inline/anonymous type declarations (complexType without @name attribute)
     */
    private fun countInlineTypes(properties: Map<String, UDM>): Int {
        return properties.values.sumOf { value ->
            countInlineTypesRecursive(value)
        }
    }

    private fun countInlineTypesRecursive(udm: UDM): Int {
        return when (udm) {
            is UDM.Object -> {
                val isInlineType = udm.metadata["__schemaType"] == "xsd-complexType" &&
                                   udm.attributes["name"] == null &&
                                   udm.metadata["__scope"] == "local"
                val count = if (isInlineType) 1 else 0
                // Recursively count in nested structures
                count + udm.properties.values.sumOf { countInlineTypesRecursive(it) }
            }
            is UDM.Array -> {
                udm.elements.sumOf { countInlineTypesRecursive(it) }
            }
            else -> 0
        }
    }

    /**
     * Extract global xs:element declarations from schema
     */
    private fun extractGlobalElements(schema: UDM.Object): Map<String, UDM.Object> {
        val result = mutableMapOf<String, UDM.Object>()

        val elementValue = schema.properties["xs:element"]
        when (elementValue) {
            is UDM.Object -> {
                val name = elementValue.attributes["name"] ?: "UnnamedElement"
                result[name] = elementValue
            }
            is UDM.Array -> {
                elementValue.elements.forEach { element ->
                    if (element is UDM.Object) {
                        val name = element.attributes["name"] ?: "UnnamedElement"
                        result[name] = element
                    }
                }
            }
            else -> { /* No global elements found */ }
        }

        return result
    }

    /**
     * Add XSD-specific inline type metadata to a USDL type definition
     *
     * Adds three directives:
     * - %xsdInline: true (marks this as an inline/anonymous type)
     * - %xsdElement: parent element name
     * - %xsdPattern: "russian-doll" (pattern indicator)
     *
     * Example:
     * Input USDL type:
     *   {
     *     "%kind": "structure",
     *     "%fields": [...]
     *   }
     *
     * Output with metadata:
     *   {
     *     "%kind": "structure",
     *     "%xsdInline": true,
     *     "%xsdElement": "order",
     *     "%xsdPattern": "russian-doll",
     *     "%fields": [...]
     *   }
     */
    private fun addInlineMetadata(usdlType: UDM, elementName: String): UDM {
        if (usdlType !is UDM.Object) {
            return usdlType
        }

        // Add XSD-specific metadata directives
        val enhancedProps = usdlType.properties.toMutableMap()
        enhancedProps["%xsdInline"] = UDM.Scalar(true)
        enhancedProps["%xsdElement"] = UDM.Scalar(elementName)
        enhancedProps["%xsdPattern"] = UDM.Scalar("russian-doll")

        return UDM.Object(
            properties = enhancedProps,
            attributes = usdlType.attributes,
            name = usdlType.name,
            metadata = usdlType.metadata
        )
    }

    /**
     * Extract xs:complexType elements from schema
     */
    private fun extractComplexTypes(schema: UDM.Object): Map<String, UDM.Object> {
        val result = mutableMapOf<String, UDM.Object>()

        val complexTypeValue = schema.properties["xs:complexType"]
        when (complexTypeValue) {
            is UDM.Object -> {
                val name = complexTypeValue.attributes["name"] ?: "UnnamedType"
                result[name] = complexTypeValue
            }
            is UDM.Array -> {
                complexTypeValue.elements.forEach { element ->
                    if (element is UDM.Object) {
                        val name = element.attributes["name"] ?: "UnnamedType"
                        result[name] = element
                    }
                }
            }
            else -> { /* No complex types found */ }
        }

        return result
    }

    /**
     * Extract xs:simpleType elements from schema
     */
    private fun extractSimpleTypes(schema: UDM.Object): Map<String, UDM.Object> {
        val result = mutableMapOf<String, UDM.Object>()

        val simpleTypeValue = schema.properties["xs:simpleType"]
        when (simpleTypeValue) {
            is UDM.Object -> {
                val name = simpleTypeValue.attributes["name"] ?: "UnnamedType"
                result[name] = simpleTypeValue
            }
            is UDM.Array -> {
                simpleTypeValue.elements.forEach { element ->
                    if (element is UDM.Object) {
                        val name = element.attributes["name"] ?: "UnnamedType"
                        result[name] = element
                    }
                }
            }
            else -> { /* No simple types found */ }
        }

        return result
    }

    /**
     * Extract inline/anonymous complexTypes from xs:element definitions (Russian Doll pattern)
     *
     * Returns map of syntheticName -> (complexType, parentElementName)
     *
     * Example:
     * Input:
     *   <xs:element name="order">
     *     <xs:complexType>  <!-- anonymous inline type -->
     *       <xs:sequence>
     *         <xs:element name="orderId" type="xs:string"/>
     *       </xs:sequence>
     *     </xs:complexType>
     *   </xs:element>
     *
     * Output:
     *   {"order_InlineType" -> (complexType UDM.Object, "order")}
     */
    private fun extractInlineTypes(schema: UDM.Object): Map<String, Pair<UDM.Object, String>> {
        val result = mutableMapOf<String, Pair<UDM.Object, String>>()

        // Recursively search for all xs:element nodes with inline types
        extractInlineTypesRecursive(schema, result)

        return result
    }

    /**
     * Recursive helper to find inline complexTypes at any nesting level
     */
    private fun extractInlineTypesRecursive(
        udm: UDM,
        result: MutableMap<String, Pair<UDM.Object, String>>
    ) {
        when (udm) {
            is UDM.Object -> {
                // Check if this is an xs:element node
                val elementName = udm.name?.substringAfter(":")
                if (elementName == "element") {
                    // Only process if no @type attribute (indicates inline type)
                    if (!udm.attributes.containsKey("type")) {
                        // Look for nested xs:complexType
                        val complexType = udm.properties["xs:complexType"] as? UDM.Object
                        if (complexType != null) {
                            // Found inline type - generate synthetic name
                            val name = udm.attributes["name"] ?: "UnnamedElement"
                            val syntheticName = "${name}_InlineType"

                            result[syntheticName] = Pair(complexType, name)
                        }
                    }
                }

                // Recursively search all properties
                udm.properties.values.forEach { value ->
                    extractInlineTypesRecursive(value, result)
                }
            }
            is UDM.Array -> {
                // Search all array elements
                udm.elements.forEach { element ->
                    extractInlineTypesRecursive(element, result)
                }
            }
            else -> { /* Scalar or other type - no nested elements */ }
        }
    }

    /**
     * Convert xs:complexType to USDL structure definition
     */
    private fun complexTypeToUSDL(complexType: UDM.Object): UDM {
        // Extract documentation
        val doc = extractDocumentation(complexType)

        // Extract sequence/choice/all elements
        val fields = extractFields(complexType)

        // Build USDL structure
        val typeProps = mutableMapOf<String, UDM>(
            "%kind" to UDM.Scalar("structure"),
            "%fields" to UDM.Array(fields)
        )

        if (doc != null) {
            typeProps["%documentation"] = UDM.Scalar(doc)
        }

        return UDM.Object(properties = typeProps)
    }

    /**
     * Convert xs:simpleType to USDL enumeration or primitive
     */
    private fun simpleTypeToUSDL(simpleType: UDM.Object): UDM {
        // Check for xs:restriction with xs:enumeration
        val restriction = simpleType.properties["xs:restriction"] as? UDM.Object
        if (restriction != null) {
            val enumerations = extractEnumerations(restriction)
            if (enumerations.isNotEmpty()) {
                // This is an enumeration type
                return UDM.Object(properties = mapOf(
                    "%kind" to UDM.Scalar("enumeration"),
                    "%values" to UDM.Array(enumerations.map { UDM.Scalar(it) })
                ))
            }
        }

        // Default: primitive type
        return UDM.Object(properties = mapOf(
            "%kind" to UDM.Scalar("primitive"),
            "%type" to UDM.Scalar("string")
        ))
    }

    /**
     * Extract xs:enumeration values from xs:restriction
     */
    private fun extractEnumerations(restriction: UDM.Object): List<String> {
        val result = mutableListOf<String>()

        val enumValue = restriction.properties["xs:enumeration"]
        when (enumValue) {
            is UDM.Object -> {
                enumValue.attributes["value"]?.let { result.add(it) }
            }
            is UDM.Array -> {
                enumValue.elements.forEach { element ->
                    if (element is UDM.Object) {
                        element.attributes["value"]?.let { result.add(it) }
                    }
                }
            }
            else -> { /* No enumerations found */ }
        }

        return result
    }

    /**
     * Extract fields from xs:sequence, xs:choice, or xs:all
     */
    private fun extractFields(complexType: UDM.Object): List<UDM> {
        // Check for xs:sequence first (most common)
        val sequence = complexType.properties["xs:sequence"] as? UDM.Object
        if (sequence != null) {
            return extractElementsFromContainer(sequence)
        }

        // Check for xs:all
        val all = complexType.properties["xs:all"] as? UDM.Object
        if (all != null) {
            return extractElementsFromContainer(all)
        }

        // Check for xs:choice
        val choice = complexType.properties["xs:choice"] as? UDM.Object
        if (choice != null) {
            return extractElementsFromContainer(choice)
        }

        return emptyList()
    }

    /**
     * Extract xs:element items from container (sequence/choice/all)
     */
    private fun extractElementsFromContainer(container: UDM.Object): List<UDM> {
        val result = mutableListOf<UDM>()

        val elementValue = container.properties["xs:element"]
        when (elementValue) {
            is UDM.Object -> {
                result.add(elementToUSDLField(elementValue))
            }
            is UDM.Array -> {
                elementValue.elements.forEach { element ->
                    if (element is UDM.Object) {
                        result.add(elementToUSDLField(element))
                    }
                }
            }
            else -> { /* No elements found */ }
        }

        return result
    }

    /**
     * Convert xs:element to USDL field
     *
     * Handles three cases:
     * 1. Element with @type attribute → use named type
     * 2. Element with inline xs:complexType → generate synthetic name reference
     * 3. Element with no type → default to "string"
     */
    private fun elementToUSDLField(element: UDM.Object): UDM {
        val name = element.attributes["name"] ?: "unnamed"
        val typeAttr = element.attributes["type"]
        val minOccurs = element.attributes["minOccurs"] ?: "1"
        val doc = extractDocumentation(element)

        // Determine the type
        val usdlType = if (typeAttr != null) {
            // Case 1: Element has @type attribute - use it (Venetian Blind, Salami Slice)
            xsdTypeToUSDL(typeAttr)
        } else {
            // Case 2: No @type - check for inline complexType (Russian Doll)
            val inlineComplexType = element.properties["xs:complexType"]
            if (inlineComplexType is UDM.Object) {
                // Inline type found - reference the synthetic name that will be in %types
                // (The extractInlineTypes() method generates these synthetic names)
                "${name}_InlineType"
            } else {
                // Case 3: No type specified - default to string
                "string"
            }
        }

        // Build USDL field
        val fieldProps = mutableMapOf<String, UDM>(
            "%name" to UDM.Scalar(name),
            "%type" to UDM.Scalar(usdlType)
        )

        // Required if minOccurs >= 1
        if (minOccurs != "0") {
            fieldProps["%required"] = UDM.Scalar(true)
        }

        if (doc != null) {
            fieldProps["%documentation"] = UDM.Scalar(doc)
        }

        return UDM.Object(properties = fieldProps)
    }

    /**
     * Convert XSD type name to USDL type name
     */
    private fun xsdTypeToUSDL(xsdType: String): String {
        // Remove xs: or xsd: prefix
        val bareType = xsdType.removePrefix("xs:").removePrefix("xsd:")

        return when (bareType) {
            "string" -> "string"
            "int", "integer" -> "integer"
            "long" -> "long"
            "float" -> "number"
            "double" -> "double"
            "boolean" -> "boolean"
            "byte", "base64Binary", "hexBinary" -> "binary"
            "date" -> "date"
            "dateTime" -> "datetime"
            "time" -> "time"
            else -> bareType  // Pass through complex types
        }
    }

    /**
     * Extract documentation from xs:annotation/xs:documentation
     */
    private fun extractDocumentation(element: UDM.Object): String? {
        val annotation = element.properties["xs:annotation"] as? UDM.Object
        if (annotation != null) {
            val documentation = annotation.properties["xs:documentation"]
            when (documentation) {
                is UDM.Scalar -> return documentation.value?.toString()
                is UDM.Object -> {
                    // Documentation might have text content
                    return documentation.properties["_text"]?.let {
                        (it as? UDM.Scalar)?.value?.toString()
                    }
                }
                else -> { /* No documentation found */ }
            }
        }

        // Check for _documentation property (simplified representation)
        val doc = element.properties["_documentation"] as? UDM.Scalar
        return doc?.value?.toString()
    }

    companion object {
        /**
         * Known XSD element names (without namespace prefix)
         */
        private val XSD_ELEMENTS = setOf(
            "schema", "element", "complexType", "simpleType", "attribute",
            "annotation", "documentation", "appinfo",
            "sequence", "choice", "all", "group", "attributeGroup",
            "restriction", "extension", "simpleContent", "complexContent",
            "enumeration", "pattern", "minLength", "maxLength",
            "import", "include", "redefine", "override",
            "key", "keyref", "unique", "selector", "field",
            "assert", "assertion", "alternative"  // XSD 1.1
        )
    }
}
