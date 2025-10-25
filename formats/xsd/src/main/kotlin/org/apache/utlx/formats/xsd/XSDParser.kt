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

        // Step 2: Enhance with XSD-specific metadata
        val enhanced = enhanceWithXSDMetadata(xmlUDM)

        // Step 3: Unwrap if XMLParser wrapped the document
        // Return the actual xs:schema element, not the wrapper
        if (enhanced is UDM.Object && enhanced.name == null) {
            val schemaKey = enhanced.properties.keys.firstOrNull { key ->
                key == "xs:schema" || key == "xsd:schema" || key == "schema"
            }
            if (schemaKey != null) {
                return enhanced.properties[schemaKey] ?: enhanced
            }
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

        return UDM.Object(
            properties = enhancedProperties,
            attributes = schema.attributes,
            name = schema.name,
            metadata = schema.metadata + mapOf(
                "__schemaType" to "xsd-schema",
                "__xsdVersion" to xsdVersion,
                "__targetNamespace" to targetNamespace
            )
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
