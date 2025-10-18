// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/XSDSchemaParser.kt
package org.apache.utlx.analysis.schema

import org.apache.utlx.analysis.types.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parser for XML Schema Definition (XSD) documents
 * 
 * Converts XSD specifications to internal TypeDefinition representation
 * for use in type inference and validation.
 * 
 * Supports basic XSD features:
 * - Simple types (string, int, double, boolean, date, dateTime)
 * - Complex types with sequences
 * - Elements and attributes
 * - Restrictions (minLength, maxLength, pattern, etc.)
 */
class XSDSchemaParser : InputSchemaParser {
    
    companion object {
        private const val XS_NAMESPACE = "http://www.w3.org/2001/XMLSchema"
    }
    
    /**
     * Parse XSD string into TypeDefinition
     */
    override fun parse(schema: String, format: SchemaFormat): TypeDefinition {
        if (format != SchemaFormat.XSD) {
            throw IllegalArgumentException("XSDSchemaParser only handles XSD format")
        }
        
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(schema.byteInputStream())
            
            parseXSDDocument(document)
        } catch (e: Exception) {
            throw SchemaParseException("Failed to parse XSD: ${e.message}", e)
        }
    }
    
    /**
     * Parse XSD document
     */
    private fun parseXSDDocument(document: Document): TypeDefinition {
        val root = document.documentElement
        
        // Find the root element definition
        val elements = root.getElementsByTagNameNS(XS_NAMESPACE, "element")
        
        if (elements.length == 0) {
            return TypeDefinition.Any
        }
        
        // Parse the first root element
        val rootElement = elements.item(0) as Element
        return parseElement(rootElement, root)
    }
    
    /**
     * Parse an XSD element
     */
    private fun parseElement(element: Element, schemaRoot: Element): TypeDefinition {
        val name = element.getAttribute("name")
        val type = element.getAttribute("type")
        
        return when {
            type.isNotEmpty() -> {
                // Reference to a type
                if (type.startsWith("xs:") || type.startsWith("xsd:")) {
                    // Built-in type
                    parseBuiltInType(type)
                } else {
                    // Custom type - try to find its definition
                    findTypeDefinition(type, schemaRoot) ?: TypeDefinition.Any
                }
            }
            else -> {
                // Inline type definition
                parseInlineType(element, schemaRoot)
            }
        }
    }
    
    /**
     * Parse built-in XSD types
     */
    private fun parseBuiltInType(typeName: String): TypeDefinition {
        val localName = typeName.substringAfter(":")
        
        return when (localName) {
            "string" -> TypeDefinition.Scalar(ScalarKind.STRING)
            "int", "integer", "long", "short" -> TypeDefinition.Scalar(ScalarKind.INTEGER)
            "double", "float", "decimal" -> TypeDefinition.Scalar(ScalarKind.NUMBER)
            "boolean" -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            "date" -> TypeDefinition.Scalar(ScalarKind.DATE)
            "dateTime" -> TypeDefinition.Scalar(ScalarKind.DATETIME)
            else -> TypeDefinition.Any
        }
    }
    
    /**
     * Parse inline type definition
     */
    private fun parseInlineType(element: Element, schemaRoot: Element): TypeDefinition {
        // Look for complexType or simpleType children
        val complexTypes = element.getElementsByTagNameNS(XS_NAMESPACE, "complexType")
        val simpleTypes = element.getElementsByTagNameNS(XS_NAMESPACE, "simpleType")
        
        return when {
            complexTypes.length > 0 -> parseComplexType(complexTypes.item(0) as Element, schemaRoot)
            simpleTypes.length > 0 -> parseSimpleType(simpleTypes.item(0) as Element, schemaRoot)
            else -> TypeDefinition.Any
        }
    }
    
    /**
     * Parse complex type definition
     */
    private fun parseComplexType(complexType: Element, schemaRoot: Element): TypeDefinition {
        val properties = mutableMapOf<String, PropertyType>()
        val required = mutableSetOf<String>()
        
        // Look for sequence
        val sequences = complexType.getElementsByTagNameNS(XS_NAMESPACE, "sequence")
        if (sequences.length > 0) {
            val sequence = sequences.item(0) as Element
            
            // Parse elements in sequence
            val elements = sequence.getElementsByTagNameNS(XS_NAMESPACE, "element")
            for (i in 0 until elements.length) {
                val elem = elements.item(i) as Element
                val name = elem.getAttribute("name")
                val minOccurs = elem.getAttribute("minOccurs").takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
                
                if (name.isNotEmpty()) {
                    val elemType = parseElement(elem, schemaRoot)
                    properties[name] = PropertyType(elemType, nullable = minOccurs == 0)
                    
                    if (minOccurs > 0) {
                        required.add(name)
                    }
                }
            }
        }
        
        // Look for attributes
        val attributes = complexType.getElementsByTagNameNS(XS_NAMESPACE, "attribute")
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Element
            val name = attr.getAttribute("name")
            val use = attr.getAttribute("use")
            
            if (name.isNotEmpty()) {
                val attrType = attr.getAttribute("type").takeIf { it.isNotEmpty() }
                    ?.let { parseBuiltInType(it) } ?: TypeDefinition.Scalar(ScalarKind.STRING)
                
                properties["@$name"] = PropertyType(attrType, nullable = use != "required")
                
                if (use == "required") {
                    required.add("@$name")
                }
            }
        }
        
        return TypeDefinition.Object(properties, required, additionalProperties = false)
    }
    
    /**
     * Parse simple type definition
     */
    private fun parseSimpleType(simpleType: Element, schemaRoot: Element): TypeDefinition {
        // Look for restriction
        val restrictions = simpleType.getElementsByTagNameNS(XS_NAMESPACE, "restriction")
        if (restrictions.length > 0) {
            val restriction = restrictions.item(0) as Element
            val base = restriction.getAttribute("base")
            
            val baseType = if (base.startsWith("xs:") || base.startsWith("xsd:")) {
                parseBuiltInType(base)
            } else {
                TypeDefinition.Scalar(ScalarKind.STRING)
            }
            
            // Parse restrictions
            val constraints = mutableListOf<Constraint>()
            
            // Parse facets
            val childNodes = restriction.childNodes
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val facet = node as Element
                    val value = facet.getAttribute("value")
                    
                    when (facet.localName) {
                        "minLength" -> value.toIntOrNull()?.let {
                            constraints.add(Constraint(ConstraintKind.MIN_LENGTH, it))
                        }
                        "maxLength" -> value.toIntOrNull()?.let {
                            constraints.add(Constraint(ConstraintKind.MAX_LENGTH, it))
                        }
                        "pattern" -> constraints.add(Constraint(ConstraintKind.PATTERN, value))
                        "minInclusive" -> value.toDoubleOrNull()?.let {
                            constraints.add(Constraint(ConstraintKind.MINIMUM, it))
                        }
                        "maxInclusive" -> value.toDoubleOrNull()?.let {
                            constraints.add(Constraint(ConstraintKind.MAXIMUM, it))
                        }
                        "enumeration" -> {
                            // Handle enumeration - collect all values
                            val enumValues = mutableListOf<String>()
                            for (j in 0 until childNodes.length) {
                                val enumNode = childNodes.item(j)
                                if (enumNode.nodeType == Node.ELEMENT_NODE &&
                                    (enumNode as Element).localName == "enumeration") {
                                    enumValues.add(enumNode.getAttribute("value"))
                                }
                            }
                            if (enumValues.isNotEmpty()) {
                                constraints.add(Constraint(ConstraintKind.ENUM, enumValues))
                            }
                        }
                    }
                }
            }
            
            return if (baseType is TypeDefinition.Scalar) {
                baseType.copy(constraints = baseType.constraints + constraints)
            } else {
                TypeDefinition.Scalar(ScalarKind.STRING, constraints)
            }
        }
        
        return TypeDefinition.Scalar(ScalarKind.STRING)
    }
    
    /**
     * Find type definition by name
     */
    private fun findTypeDefinition(typeName: String, schemaRoot: Element): TypeDefinition? {
        // Look for complexType with this name
        val complexTypes = schemaRoot.getElementsByTagNameNS(XS_NAMESPACE, "complexType")
        for (i in 0 until complexTypes.length) {
            val complexType = complexTypes.item(i) as Element
            if (complexType.getAttribute("name") == typeName) {
                return parseComplexType(complexType, schemaRoot)
            }
        }
        
        // Look for simpleType with this name
        val simpleTypes = schemaRoot.getElementsByTagNameNS(XS_NAMESPACE, "simpleType")
        for (i in 0 until simpleTypes.length) {
            val simpleType = simpleTypes.item(i) as Element
            if (simpleType.getAttribute("name") == typeName) {
                return parseSimpleType(simpleType, schemaRoot)
            }
        }
        
        return null
    }
}