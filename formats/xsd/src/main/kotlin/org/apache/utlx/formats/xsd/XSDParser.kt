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

        val newMetadata = schema.metadata + mapOf(
            "schemaType" to "xsd-schema",
            "xsdVersion" to xsdVersion,
            "targetNamespace" to targetNamespace
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
                    newMetadata["schemaType"] = schemaType
                    newMetadata["scope"] = scope
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
            metadata["schemaType"] = "xsd-$elementName"
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

        // Build %types object
        val typesMap = mutableMapOf<String, UDM>()

        // Add complex types
        complexTypes.forEach { (name, type) ->
            typesMap[name] = complexTypeToUSDL(type)
        }

        // Add simple types (enumerations, restrictions)
        simpleTypes.forEach { (name, type) ->
            typesMap[name] = simpleTypeToUSDL(type)
        }

        // Build top-level USDL object
        val topLevelProps = mutableMapOf<String, UDM>(
            "%types" to UDM.Object(properties = typesMap)
        )

        // Add top-level namespace if present
        if (namespace != null) {
            topLevelProps["%namespace"] = UDM.Scalar(namespace)
        }

        return UDM.Object(properties = topLevelProps)
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
     */
    private fun elementToUSDLField(element: UDM.Object): UDM {
        val name = element.attributes["name"] ?: "unnamed"
        val type = element.attributes["type"] ?: "xs:string"
        val minOccurs = element.attributes["minOccurs"] ?: "1"
        val doc = extractDocumentation(element)

        // Convert XSD type to USDL type
        val usdlType = xsdTypeToUSDL(type)

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
