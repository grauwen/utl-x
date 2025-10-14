// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/XSDGenerator.kt
package org.apache.utlx.analysis.schema

import org.apache.utlx.analysis.types.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter

/**
 * Generator for XML Schema Definition (XSD) from UTL-X type definitions
 * 
 * Converts internal type representations to XSD format for:
 * - XML validation
 * - SOAP/WSDL definitions
 * - Legacy system integration
 * - Enterprise application integration
 */
class XSDGenerator : SchemaGenerator {
    
    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private val transformer = TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
    }
    
    companion object {
        private const val XSD_NS = "http://www.w3.org/2001/XMLSchema"
        private const val XSD_PREFIX = "xs"
    }
    
    override fun generate(
        type: TypeDefinition,
        format: SchemaFormat,
        options: GeneratorOptions
    ): String {
        if (format != SchemaFormat.XSD) {
            throw IllegalArgumentException("XSDGenerator only handles XSD format")
        }
        
        val doc = documentBuilder.newDocument()
        
        // Create schema root element
        val schemaElement = doc.createElementNS(XSD_NS, "$XSD_PREFIX:schema")
        schemaElement.setAttribute("xmlns:$XSD_PREFIX", XSD_NS)
        schemaElement.setAttribute("elementFormDefault", "qualified")
        schemaElement.setAttribute("attributeFormDefault", "unqualified")
        
        options.targetNamespace?.let { namespace ->
            schemaElement.setAttribute("targetNamespace", namespace)
            schemaElement.setAttribute("xmlns:tns", namespace)
        }
        
        doc.appendChild(schemaElement)
        
        // Add root element
        val rootElementName = options.rootElementName ?: "Root"
        val rootElement = createElementDefinition(doc, rootElementName, type, options)
        schemaElement.appendChild(rootElement)
        
        // Convert DOM to string
        return documentToString(doc, options.pretty)
    }
    
    /**
     * Create element definition
     */
    private fun createElementDefinition(
        doc: Document,
        name: String,
        type: TypeDefinition,
        options: GeneratorOptions,
        minOccurs: Int? = null,
        maxOccurs: String? = null
    ): Element {
        val element = doc.createElementNS(XSD_NS, "$XSD_PREFIX:element")
        element.setAttribute("name", name)
        
        minOccurs?.let { element.setAttribute("minOccurs", it.toString()) }
        maxOccurs?.let { element.setAttribute("maxOccurs", it) }
        
        // Add type definition
        when (type) {
            is TypeDefinition.Scalar -> {
                val xsdType = scalarToXSDType(type)
                element.setAttribute("type", "$XSD_PREFIX:$xsdType")
                
                // Add restrictions if present
                if (type.constraints.isNotEmpty()) {
                    element.removeAttribute("type")
                    val simpleType = createSimpleTypeWithRestrictions(doc, type, options)
                    element.appendChild(simpleType)
                }
            }
            
            is TypeDefinition.Array -> {
                // Arrays in XSD are represented as elements with maxOccurs > 1
                // This is a simplified approach - actual array element
                return createElementDefinition(
                    doc,
                    name,
                    type.elementType,
                    options,
                    minOccurs = type.minItems ?: 0,
                    maxOccurs = if (type.maxItems != null) type.maxItems.toString() else "unbounded"
                )
            }
            
            is TypeDefinition.Object -> {
                val complexType = createComplexType(doc, type, options)
                element.appendChild(complexType)
            }
            
            is TypeDefinition.Union -> {
                // Union types in XSD use xs:union
                val simpleType = doc.createElementNS(XSD_NS, "$XSD_PREFIX:simpleType")
                val union = doc.createElementNS(XSD_NS, "$XSD_PREFIX:union")
                
                val memberTypes = type.types.mapNotNull { unionType ->
                    if (unionType is TypeDefinition.Scalar) {
                        scalarToXSDType(unionType)
                    } else null
                }.joinToString(" ") { "$XSD_PREFIX:$it" }
                
                union.setAttribute("memberTypes", memberTypes)
                simpleType.appendChild(union)
                element.appendChild(simpleType)
            }
            
            is TypeDefinition.Any -> {
                element.setAttribute("type", "$XSD_PREFIX:anyType")
            }
        }
        
        return element
    }
    
    /**
     * Create complex type definition for objects
     */
    private fun createComplexType(
        doc: Document,
        obj: TypeDefinition.Object,
        options: GeneratorOptions
    ): Element {
        val complexType = doc.createElementNS(XSD_NS, "$XSD_PREFIX:complexType")
        
        // Separate elements and attributes
        val elements = obj.properties.filter { !it.key.startsWith("@") }
        val attributes = obj.properties.filter { it.key.startsWith("@") }
        
        if (elements.isNotEmpty()) {
            val sequence = doc.createElementNS(XSD_NS, "$XSD_PREFIX:sequence")
            
            elements.forEach { (name, property) ->
                val element = createElementDefinition(
                    doc,
                    name,
                    property.type,
                    options,
                    minOccurs = if (obj.required.contains(name) && !property.nullable) 1 else 0
                )
                
                if (options.includeComments && property.description != null) {
                    val annotation = createAnnotation(doc, property.description)
                    element.insertBefore(annotation, element.firstChild)
                }
                
                sequence.appendChild(element)
            }
            
            complexType.appendChild(sequence)
        }
        
        // Add attributes
        attributes.forEach { (attrName, property) ->
            val name = attrName.substring(1) // Remove @ prefix
            val attribute = doc.createElementNS(XSD_NS, "$XSD_PREFIX:attribute")
            attribute.setAttribute("name", name)
            
            if (property.type is TypeDefinition.Scalar) {
                val xsdType = scalarToXSDType(property.type as TypeDefinition.Scalar)
                attribute.setAttribute("type", "$XSD_PREFIX:$xsdType")
            }
            
            if (obj.required.contains(attrName)) {
                attribute.setAttribute("use", "required")
            }
            
            complexType.appendChild(attribute)
        }
        
        return complexType
    }
    
    /**
     * Create simple type with restrictions
     */
    private fun createSimpleTypeWithRestrictions(
        doc: Document,
        scalar: TypeDefinition.Scalar,
        options: GeneratorOptions
    ): Element {
        val simpleType = doc.createElementNS(XSD_NS, "$XSD_PREFIX:simpleType")
        val restriction = doc.createElementNS(XSD_NS, "$XSD_PREFIX:restriction")
        
        val baseType = scalarToXSDType(scalar)
        restriction.setAttribute("base", "$XSD_PREFIX:$baseType")
        
        scalar.constraints.forEach { constraint ->
            val restrictionElement = when (constraint.kind) {
                ConstraintKind.MIN_LENGTH -> {
                    doc.createElementNS(XSD_NS, "$XSD_PREFIX:minLength").apply {
                        setAttribute("value", constraint.value.toString())
                    }
                }
                ConstraintKind.MAX_LENGTH -> {
                    doc.createElementNS(XSD_NS, "$XSD_PREFIX:maxLength").apply {
                        setAttribute("value", constraint.value.toString())
                    }
                }
                ConstraintKind.PATTERN -> {
                    doc.createElementNS(XSD_NS, "$XSD_PREFIX:pattern").apply {
                        setAttribute("value", constraint.value.toString())
                    }
                }
                ConstraintKind.MINIMUM -> {
                    doc.createElementNS(XSD_NS, "$XSD_PREFIX:minInclusive").apply {
                        setAttribute("value", constraint.value.toString())
                    }
                }
                ConstraintKind.MAXIMUM -> {
                    doc.createElementNS(XSD_NS, "$XSD_PREFIX:maxInclusive").apply {
                        setAttribute("value", constraint.value.toString())
                    }
                }
                ConstraintKind.ENUM -> {
                    null // Handle enums separately
                }
            }
            
            restrictionElement?.let { restriction.appendChild(it) }
        }
        
        // Handle enumerations
        scalar.constraints
            .filter { it.kind == ConstraintKind.ENUM }
            .forEach { constraint ->
                @Suppress("UNCHECKED_CAST")
                val values = constraint.value as List<String>
                values.forEach { value ->
                    val enumElement = doc.createElementNS(XSD_NS, "$XSD_PREFIX:enumeration")
                    enumElement.setAttribute("value", value)
                    restriction.appendChild(enumElement)
                }
            }
        
        simpleType.appendChild(restriction)
        return simpleType
    }
    
    /**
     * Create annotation element for documentation
     */
    private fun createAnnotation(doc: Document, description: String): Element {
        val annotation = doc.createElementNS(XSD_NS, "$XSD_PREFIX:annotation")
        val documentation = doc.createElementNS(XSD_NS, "$XSD_PREFIX:documentation")
        documentation.textContent = description
        annotation.appendChild(documentation)
        return annotation
    }
    
    /**
     * Map scalar types to XSD types
     */
    private fun scalarToXSDType(scalar: TypeDefinition.Scalar): String {
        return when (scalar.kind) {
            ScalarKind.STRING -> "string"
            ScalarKind.INTEGER -> "integer"
            ScalarKind.NUMBER -> "decimal"
            ScalarKind.BOOLEAN -> "boolean"
            ScalarKind.NULL -> "string" // XSD doesn't have null type
            ScalarKind.DATE -> "date"
            ScalarKind.DATETIME -> "dateTime"
        }
    }
    
    /**
     * Convert DOM document to string
     */
    private fun documentToString(doc: Document, pretty: Boolean): String {
        val writer = StringWriter()
        val source = DOMSource(doc)
        val result = StreamResult(writer)
        
        transformer.setOutputProperty(OutputKeys.INDENT, if (pretty) "yes" else "no")
        transformer.transform(source, result)
        
        return writer.toString()
    }
    
    companion object {
        /**
         * Quick generate method
         */
        fun toXSD(
            type: TypeDefinition,
            rootElementName: String = "Root",
            targetNamespace: String? = null,
            pretty: Boolean = true
        ): String {
            return XSDGenerator().generate(
                type,
                SchemaFormat.XSD,
                GeneratorOptions(
                    pretty = pretty,
                    rootElementName = rootElementName,
                    namespace = targetNamespace
                )
            )
        }
    }
}
