// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/XSDSchemaParser.kt
package org.apache.utlx.analysis.schema

import org.apache.utlx.analysis.types.*
import org.w3c.dom.*
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/**
 * Parser for XML Schema Definition (XSD) documents
 * 
 * Converts XSD schemas into UTL-X type definitions for analysis and validation.
 * Supports:
 * - Simple types (string, integer, decimal, boolean, date, etc.)
 * - Complex types with sequences, choices, and all
 * - Attributes
 * - Elements with min/max occurs
 * - Type restrictions (enumerations, patterns, min/max values)
 * - References and imports
 */
class XSDSchemaParser : InputSchemaParser {
    
    private val documentBuilder = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder()
    
    private val xpath = XPathFactory.newInstance().newXPath()
    
    override fun parse(schema: String, format: SchemaFormat): TypeDefinition {
        if (format != SchemaFormat.XSD) {
            throw IllegalArgumentException("XSDSchemaParser only handles XSD format")
        }
        
        val doc = documentBuilder.parse(StringReader(schema).use { 
            it.toString().byteInputStream() 
        })
        
        // Find root element
        val schemaElement = doc.documentElement
        if (schemaElement.localName != "schema") {
            throw XSDParseException("Root element must be xs:schema")
        }
        
        // Build type registry from defined types
        val typeRegistry = buildTypeRegistry(schemaElement)
        
        // Find and parse root element
        val rootElements = schemaElement.getElementsByTagNameNS(XSD_NS, "element")
        if (rootElements.length == 0) {
            throw XSDParseException("No root element found in schema")
        }
        
        val rootElement = rootElements.item(0) as Element
        return parseElement(rootElement, typeRegistry)
    }
    
    /**
     * Build a registry of all named types defined in the schema
     */
    private fun buildTypeRegistry(schemaElement: Element): TypeRegistry {
        val registry = TypeRegistry()
        
        // Register simple types
        val simpleTypes = schemaElement.getElementsByTagNameNS(XSD_NS, "simpleType")
        for (i in 0 until simpleTypes.length) {
            val simpleType = simpleTypes.item(i) as Element
            val name = simpleType.getAttribute("name")
            if (name.isNotEmpty()) {
                registry.registerType(name, parseSimpleType(simpleType, registry))
            }
        }
        
        // Register complex types
        val complexTypes = schemaElement.getElementsByTagNameNS(XSD_NS, "complexType")
        for (i in 0 until complexTypes.length) {
            val complexType = complexTypes.item(i) as Element
            val name = complexType.getAttribute("name")
            if (name.isNotEmpty()) {
                registry.registerType(name, parseComplexType(complexType, registry))
            }
        }
        
        return registry
    }
    
    /**
     * Parse an element definition
     */
    private fun parseElement(element: Element, registry: TypeRegistry): TypeDefinition {
        val name = element.getAttribute("name")
        val typeName = element.getAttribute("type")
        val minOccurs = element.getAttribute("minOccurs").toIntOrNull() ?: 1
        val maxOccurs = element.getAttribute("maxOccurs").let {
            when (it) {
                "unbounded" -> Int.MAX_VALUE
                "" -> 1
                else -> it.toIntOrNull() ?: 1
            }
        }
        
        // Determine element type
        val elementType = when {
            typeName.isNotEmpty() -> {
                // Reference to named type
                resolveType(typeName, registry)
            }
            else -> {
                // Inline type definition
                val inlineSimpleType = element.getElementsByTagNameNS(XSD_NS, "simpleType")
                val inlineComplexType = element.getElementsByTagNameNS(XSD_NS, "complexType")
                
                when {
                    inlineSimpleType.length > 0 -> 
                        parseSimpleType(inlineSimpleType.item(0) as Element, registry)
                    inlineComplexType.length > 0 -> 
                        parseComplexType(inlineComplexType.item(0) as Element, registry)
                    else -> TypeDefinition.Scalar(ScalarKind.STRING)
                }
            }
        }
        
        // Wrap in array if maxOccurs > 1
        return if (maxOccurs > 1) {
            TypeDefinition.Array(
                elementType = elementType,
                minItems = minOccurs,
                maxItems = if (maxOccurs == Int.MAX_VALUE) null else maxOccurs
            )
        } else {
            elementType
        }
    }
    
    /**
     * Parse a complex type definition
     */
    private fun parseComplexType(complexType: Element, registry: TypeRegistry): TypeDefinition {
        val properties = mutableMapOf<String, PropertyType>()
        val required = mutableSetOf<String>()
        
        // Parse sequence
        val sequences = complexType.getElementsByTagNameNS(XSD_NS, "sequence")
        if (sequences.length > 0) {
            val sequence = sequences.item(0) as Element
            parseSequence(sequence, properties, required, registry)
        }
        
        // Parse choice
        val choices = complexType.getElementsByTagNameNS(XSD_NS, "choice")
        if (choices.length > 0) {
            val choice = choices.item(0) as Element
            parseChoice(choice, properties, required, registry)
        }
        
        // Parse all
        val alls = complexType.getElementsByTagNameNS(XSD_NS, "all")
        if (alls.length > 0) {
            val all = alls.item(0) as Element
            parseAll(all, properties, required, registry)
        }
        
        // Parse attributes
        val attributes = complexType.getElementsByTagNameNS(XSD_NS, "attribute")
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Element
            parseAttribute(attr, properties, required, registry)
        }
        
        // Parse simple content (for complex types with text content and attributes)
        val simpleContents = complexType.getElementsByTagNameNS(XSD_NS, "simpleContent")
        if (simpleContents.length > 0) {
            val simpleContent = simpleContents.item(0) as Element
            val extensions = simpleContent.getElementsByTagNameNS(XSD_NS, "extension")
            if (extensions.length > 0) {
                val extension = extensions.item(0) as Element
                val baseType = extension.getAttribute("base")
                
                // Add text content as special property
                properties["_text"] = PropertyType(
                    type = resolveType(baseType, registry),
                    nullable = false
                )
                required.add("_text")
                
                // Parse attributes in extension
                val extAttrs = extension.getElementsByTagNameNS(XSD_NS, "attribute")
                for (i in 0 until extAttrs.length) {
                    parseAttribute(extAttrs.item(i) as Element, properties, required, registry)
                }
            }
        }
        
        return TypeDefinition.Object(
            properties = properties,
            required = required,
            additionalProperties = false
        )
    }
    
    /**
     * Parse a sequence (ordered elements)
     */
    private fun parseSequence(
        sequence: Element,
        properties: MutableMap<String, PropertyType>,
        required: MutableSet<String>,
        registry: TypeRegistry
    ) {
        val elements = sequence.getElementsByTagNameNS(XSD_NS, "element")
        for (i in 0 until elements.length) {
            val element = elements.item(i) as Element
            val name = element.getAttribute("name")
            val minOccurs = element.getAttribute("minOccurs").toIntOrNull() ?: 1
            
            properties[name] = PropertyType(
                type = parseElement(element, registry),
                nullable = minOccurs == 0
            )
            
            if (minOccurs > 0) {
                required.add(name)
            }
        }
    }
    
    /**
     * Parse a choice (one of several elements)
     */
    private fun parseChoice(
        choice: Element,
        properties: MutableMap<String, PropertyType>,
        required: MutableSet<String>,
        registry: TypeRegistry
    ) {
        val elements = choice.getElementsByTagNameNS(XSD_NS, "element")
        val choiceTypes = mutableListOf<TypeDefinition>()
        
        for (i in 0 until elements.length) {
            val element = elements.item(i) as Element
            val name = element.getAttribute("name")
            val elementType = parseElement(element, registry)
            
            properties[name] = PropertyType(
                type = elementType,
                nullable = true  // All choice elements are optional
            )
            
            choiceTypes.add(elementType)
        }
        
        // Don't add any to required since it's a choice
    }
    
    /**
     * Parse an all (unordered elements)
     */
    private fun parseAll(
        all: Element,
        properties: MutableMap<String, PropertyType>,
        required: MutableSet<String>,
        registry: TypeRegistry
    ) {
        // Similar to sequence but unordered
        parseSequence(all, properties, required, registry)
    }
    
    /**
     * Parse an attribute
     */
    private fun parseAttribute(
        attribute: Element,
        properties: MutableMap<String, PropertyType>,
        required: MutableSet<String>,
        registry: TypeRegistry
    ) {
        val name = "@" + attribute.getAttribute("name")  // Prefix with @ to distinguish from elements
        val typeName = attribute.getAttribute("type")
        val use = attribute.getAttribute("use")
        
        val attrType = if (typeName.isNotEmpty()) {
            resolveType(typeName, registry)
        } else {
            TypeDefinition.Scalar(ScalarKind.STRING)
        }
        
        properties[name] = PropertyType(
            type = attrType,
            nullable = use != "required"
        )
        
        if (use == "required") {
            required.add(name)
        }
    }
    
    /**
     * Parse a simple type (with restrictions)
     */
    private fun parseSimpleType(simpleType: Element, registry: TypeRegistry): TypeDefinition {
        val restrictions = simpleType.getElementsByTagNameNS(XSD_NS, "restriction")
        if (restrictions.length == 0) {
            return TypeDefinition.Scalar(ScalarKind.STRING)
        }
        
        val restriction = restrictions.item(0) as Element
        val base = restriction.getAttribute("base")
        val baseScalarKind = xsdTypeToScalarKind(base)
        
        val constraints = mutableListOf<Constraint>()
        
        // Parse enumeration
        val enums = restriction.getElementsByTagNameNS(XSD_NS, "enumeration")
        if (enums.length > 0) {
            val enumValues = mutableListOf<String>()
            for (i in 0 until enums.length) {
                val enum = enums.item(i) as Element
                enumValues.add(enum.getAttribute("value"))
            }
            constraints.add(Constraint(ConstraintKind.ENUM, enumValues))
        }
        
        // Parse pattern
        val patterns = restriction.getElementsByTagNameNS(XSD_NS, "pattern")
        if (patterns.length > 0) {
            val pattern = patterns.item(0) as Element
            constraints.add(Constraint(ConstraintKind.PATTERN, pattern.getAttribute("value")))
        }
        
        // Parse min/max length
        val minLengths = restriction.getElementsByTagNameNS(XSD_NS, "minLength")
        if (minLengths.length > 0) {
            val minLength = minLengths.item(0) as Element
            constraints.add(Constraint(ConstraintKind.MIN_LENGTH, minLength.getAttribute("value").toInt()))
        }
        
        val maxLengths = restriction.getElementsByTagNameNS(XSD_NS, "maxLength")
        if (maxLengths.length > 0) {
            val maxLength = maxLengths.item(0) as Element
            constraints.add(Constraint(ConstraintKind.MAX_LENGTH, maxLength.getAttribute("value").toInt()))
        }
        
        // Parse min/max inclusive
        val minInclusives = restriction.getElementsByTagNameNS(XSD_NS, "minInclusive")
        if (minInclusives.length > 0) {
            val minInclusive = minInclusives.item(0) as Element
            constraints.add(Constraint(ConstraintKind.MINIMUM, minInclusive.getAttribute("value").toDouble()))
        }
        
        val maxInclusives = restriction.getElementsByTagNameNS(XSD_NS, "maxInclusive")
        if (maxInclusives.length > 0) {
            val maxInclusive = maxInclusives.item(0) as Element
            constraints.add(Constraint(ConstraintKind.MAXIMUM, maxInclusive.getAttribute("value").toDouble()))
        }
        
        return TypeDefinition.Scalar(
            kind = baseScalarKind,
            constraints = constraints
        )
    }
    
    /**
     * Resolve a type name to a type definition
     */
    private fun resolveType(typeName: String, registry: TypeRegistry): TypeDefinition {
        // Check if it's a built-in XSD type
        if (typeName.startsWith("xs:") || typeName.startsWith("xsd:")) {
            val localName = typeName.substringAfter(":")
            return TypeDefinition.Scalar(xsdTypeToScalarKind(localName))
        }
        
        // Look up in registry
        return registry.getType(typeName) ?: TypeDefinition.Scalar(ScalarKind.STRING)
    }
    
    /**
     * Map XSD types to scalar kinds
     */
    private fun xsdTypeToScalarKind(xsdType: String): ScalarKind {
        return when (xsdType.lowercase()) {
            "string", "normalizedstring", "token", "anyuri", "qname", "notation" -> ScalarKind.STRING
            "byte", "short", "int", "integer", "long", "unsignedbyte", "unsignedshort", 
            "unsignedint", "unsignedlong", "positiveinteger", "negativeinteger",
            "nonpositiveinteger", "nonnegativeinteger" -> ScalarKind.INTEGER
            "float", "double", "decimal" -> ScalarKind.NUMBER
            "boolean" -> ScalarKind.BOOLEAN
            "date" -> ScalarKind.DATE
            "datetime", "time", "gyear", "gmonth", "gday", "gyearmonth", "gmonthday" -> ScalarKind.DATETIME
            else -> ScalarKind.STRING
        }
    }
    
    companion object {
        private const val XSD_NS = "http://www.w3.org/2001/XMLSchema"
    }
}

/**
 * Registry for storing named types defined in schema
 */
class TypeRegistry {
    private val types = mutableMapOf<String, TypeDefinition>()
    
    fun registerType(name: String, type: TypeDefinition) {
        types[name] = type
    }
    
    fun getType(name: String): TypeDefinition? = types[name]
}

/**
 * Exception thrown when XSD parsing fails
 */
class XSDParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
