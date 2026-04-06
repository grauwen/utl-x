package org.apache.utlx.formats.osch

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.xml.XMLParser

/**
 * EDMX Parser - Converts OData EDMX/CSDL metadata documents to UDM with USDL directives
 *
 * Parses EDMX XML (v4.0) and extracts:
 * - EntityType → UDM type with %entityType: true, %key on key fields
 * - ComplexType → UDM type with %entityType: false
 * - EnumType → UDM type with %kind: "enum"
 * - NavigationProperty → %navigation, %target, %cardinality directives
 * - ReferentialConstraint → %referentialConstraint directive
 * - EntityContainer → %entitySets directive
 *
 * The output UDM uses USDL (Universal Schema Description Language) directives
 * to represent the OData schema in a format-independent way.
 *
 * Usage:
 *   val udm = EDMXParser(edmxXml).parse()
 */
class EDMXParser(
    private val content: String,
    private val options: Map<String, Any> = emptyMap()
) {
    /**
     * Parse EDMX/CSDL XML to UDM with USDL directives
     */
    fun parse(): UDM {
        // Step 1: Parse as XML using the XML parser
        val xmlUDM = XMLParser(content).parse()

        // Step 2: Extract EDMX document model from XML UDM
        val edmxDoc = extractEDMXDocument(xmlUDM)

        // Step 3: Convert EDMX model to UDM with USDL directives
        return convertToUSDL(edmxDoc)
    }

    /**
     * Extract EDMX document model from parsed XML UDM
     */
    private fun extractEDMXDocument(udm: UDM): EDMXDocument {
        if (udm !is UDM.Object) {
            throw IllegalArgumentException("Expected XML object, got ${udm::class.simpleName}")
        }

        // XMLParser wraps in root element. Find the edmx:Edmx element
        val edmxRoot = findEdmxRoot(udm)
            ?: throw IllegalArgumentException("Not a valid EDMX document: missing <edmx:Edmx> element")

        // Extract version
        val version = edmxRoot.attributes["Version"] ?: "4.0"

        // Extract references
        val references = extractReferences(edmxRoot)

        // Find DataServices element
        val dataServices = findChildObject(edmxRoot, "edmx:DataServices", "DataServices")
            ?: edmxRoot  // Some EDMX files have Schema directly under Edmx

        // Extract schemas
        val schemas = extractSchemas(dataServices)

        return EDMXDocument(version = version, schemas = schemas, references = references)
    }

    /**
     * Find the edmx:Edmx root element in the parsed XML
     */
    private fun findEdmxRoot(udm: UDM.Object): UDM.Object? {
        // Check top-level properties for edmx:Edmx
        for ((key, value) in udm.properties) {
            if (key.contains("Edmx", ignoreCase = true) && value is UDM.Object) {
                return value
            }
        }
        // Check if the udm itself is the Edmx element
        if (udm.name?.contains("Edmx", ignoreCase = true) == true) {
            return udm
        }
        // Check attributes for EDMX namespace
        if (udm.attributes.any { it.value.contains("schemas.microsoft.com/ado/2007/06/edmx") ||
                    it.value.contains("docs.oasis-open.org/odata/ns/edmx") }) {
            return udm
        }
        return null
    }

    /**
     * Find a child object by checking multiple possible key names
     */
    private fun findChildObject(parent: UDM.Object, vararg names: String): UDM.Object? {
        for (name in names) {
            val child = parent.properties[name]
            if (child is UDM.Object) return child
        }
        // Try case-insensitive match
        for ((key, value) in parent.properties) {
            for (name in names) {
                if (key.equals(name, ignoreCase = true) && value is UDM.Object) {
                    return value
                }
            }
        }
        return null
    }

    /**
     * Extract references from EDMX
     */
    private fun extractReferences(edmxRoot: UDM.Object): List<EDMXReference> {
        val references = mutableListOf<EDMXReference>()
        val refsNode = findChildNode(edmxRoot, "edmx:Reference", "Reference")

        when (refsNode) {
            is UDM.Object -> {
                extractReference(refsNode)?.let { references.add(it) }
            }
            is UDM.Array -> {
                for (element in refsNode.elements) {
                    if (element is UDM.Object) {
                        extractReference(element)?.let { references.add(it) }
                    }
                }
            }
            else -> {}
        }

        return references
    }

    private fun extractReference(refNode: UDM.Object): EDMXReference? {
        val uri = refNode.attributes["Uri"] ?: return null
        val includes = mutableListOf<EDMXInclude>()

        val includeNode = findChildNode(refNode, "edmx:Include", "Include")
        when (includeNode) {
            is UDM.Object -> {
                val ns = includeNode.attributes["Namespace"] ?: ""
                val alias = includeNode.attributes["Alias"]
                includes.add(EDMXInclude(ns, alias))
            }
            is UDM.Array -> {
                for (el in includeNode.elements) {
                    if (el is UDM.Object) {
                        val ns = el.attributes["Namespace"] ?: ""
                        val alias = el.attributes["Alias"]
                        includes.add(EDMXInclude(ns, alias))
                    }
                }
            }
            else -> {}
        }

        return EDMXReference(uri, includes)
    }

    /**
     * Find a child node by checking multiple possible key names
     */
    private fun findChildNode(parent: UDM.Object, vararg names: String): UDM? {
        for (name in names) {
            parent.properties[name]?.let { return it }
        }
        for ((key, value) in parent.properties) {
            for (name in names) {
                if (key.equals(name, ignoreCase = true)) {
                    return value
                }
            }
        }
        return null
    }

    /**
     * Extract all Schema elements from DataServices
     */
    private fun extractSchemas(dataServices: UDM.Object): List<EDMXSchema> {
        val schemas = mutableListOf<EDMXSchema>()
        val schemaNode = findChildNode(dataServices, "Schema")

        when (schemaNode) {
            is UDM.Object -> {
                extractSchema(schemaNode)?.let { schemas.add(it) }
            }
            is UDM.Array -> {
                for (element in schemaNode.elements) {
                    if (element is UDM.Object) {
                        extractSchema(element)?.let { schemas.add(it) }
                    }
                }
            }
            else -> {}
        }

        return schemas
    }

    /**
     * Extract a single Schema element
     */
    private fun extractSchema(schemaNode: UDM.Object): EDMXSchema? {
        val namespace = schemaNode.attributes["Namespace"] ?: return null
        val alias = schemaNode.attributes["Alias"]

        val entityTypes = extractTypedElements(schemaNode, "EntityType") { extractEntityType(it) }
        val complexTypes = extractTypedElements(schemaNode, "ComplexType") { extractComplexType(it) }
        val enumTypes = extractTypedElements(schemaNode, "EnumType") { extractEnumType(it) }
        val entityContainer = findChildObject(schemaNode, "EntityContainer")?.let { extractEntityContainer(it) }

        return EDMXSchema(
            namespace = namespace,
            alias = alias,
            entityTypes = entityTypes,
            complexTypes = complexTypes,
            enumTypes = enumTypes,
            entityContainer = entityContainer
        )
    }

    /**
     * Generic extraction of typed elements from a schema node
     */
    private fun <T> extractTypedElements(
        parent: UDM.Object,
        elementName: String,
        extractor: (UDM.Object) -> T?
    ): List<T> {
        val results = mutableListOf<T>()
        val node = findChildNode(parent, elementName)

        when (node) {
            is UDM.Object -> {
                extractor(node)?.let { results.add(it) }
            }
            is UDM.Array -> {
                for (element in node.elements) {
                    if (element is UDM.Object) {
                        extractor(element)?.let { results.add(it) }
                    }
                }
            }
            else -> {}
        }

        return results
    }

    /**
     * Extract EntityType from UDM
     */
    private fun extractEntityType(node: UDM.Object): EntityType? {
        val name = node.attributes["Name"] ?: return null
        val baseType = node.attributes["BaseType"]
        val abstract = node.attributes["Abstract"]?.toBooleanStrictOrNull() ?: false

        // Extract key properties
        val keyProperties = extractKeyProperties(node)

        // Extract properties
        val properties = extractProperties(node)

        // Extract navigation properties
        val navigationProperties = extractNavigationProperties(node)

        return EntityType(
            name = name,
            keyProperties = keyProperties,
            properties = properties,
            navigationProperties = navigationProperties,
            baseType = baseType,
            abstract = abstract
        )
    }

    /**
     * Extract key property names from <Key><PropertyRef Name="..."/>
     */
    private fun extractKeyProperties(entityTypeNode: UDM.Object): List<String> {
        val keys = mutableListOf<String>()
        val keyNode = findChildNode(entityTypeNode, "Key")

        if (keyNode is UDM.Object) {
            val propRefNode = findChildNode(keyNode, "PropertyRef")
            when (propRefNode) {
                is UDM.Object -> {
                    propRefNode.attributes["Name"]?.let { keys.add(it) }
                }
                is UDM.Array -> {
                    for (el in propRefNode.elements) {
                        if (el is UDM.Object) {
                            el.attributes["Name"]?.let { keys.add(it) }
                        }
                    }
                }
                else -> {}
            }
        }

        return keys
    }

    /**
     * Extract Property elements from a type node
     */
    private fun extractProperties(typeNode: UDM.Object): List<EdmProperty> {
        return extractTypedElements(typeNode, "Property") { propNode ->
            val name = propNode.attributes["Name"] ?: return@extractTypedElements null
            val type = propNode.attributes["Type"] ?: "Edm.String"
            val nullable = propNode.attributes["Nullable"]?.toBooleanStrictOrNull() ?: true
            val maxLength = propNode.attributes["MaxLength"]?.toIntOrNull()
            val precision = propNode.attributes["Precision"]?.toIntOrNull()
            val scale = propNode.attributes["Scale"]?.toIntOrNull()
            val defaultValue = propNode.attributes["DefaultValue"]

            EdmProperty(
                name = name,
                type = type,
                nullable = nullable,
                maxLength = maxLength,
                precision = precision,
                scale = scale,
                defaultValue = defaultValue
            )
        }
    }

    /**
     * Extract NavigationProperty elements
     */
    private fun extractNavigationProperties(typeNode: UDM.Object): List<NavigationProperty> {
        return extractTypedElements(typeNode, "NavigationProperty") { navNode ->
            val name = navNode.attributes["Name"] ?: return@extractTypedElements null
            val type = navNode.attributes["Type"] ?: return@extractTypedElements null
            val nullable = navNode.attributes["Nullable"]?.toBooleanStrictOrNull()
            val partner = navNode.attributes["Partner"]
            val containsTarget = navNode.attributes["ContainsTarget"]?.toBooleanStrictOrNull() ?: false

            // Extract referential constraints
            val referentialConstraints = extractReferentialConstraints(navNode)

            NavigationProperty(
                name = name,
                type = type,
                nullable = nullable,
                partner = partner,
                containsTarget = containsTarget,
                referentialConstraints = referentialConstraints
            )
        }
    }

    /**
     * Extract ReferentialConstraint elements from a NavigationProperty node
     */
    private fun extractReferentialConstraints(navNode: UDM.Object): List<ReferentialConstraint> {
        return extractTypedElements(navNode, "ReferentialConstraint") { rcNode ->
            val property = rcNode.attributes["Property"] ?: return@extractTypedElements null
            val referencedProperty = rcNode.attributes["ReferencedProperty"] ?: return@extractTypedElements null
            ReferentialConstraint(property, referencedProperty)
        }
    }

    /**
     * Extract NavigationPropertyBinding elements from an EntitySet node
     */
    private fun extractNavPropertyBindings(esNode: UDM.Object): List<NavigationPropertyBinding> {
        return extractTypedElements(esNode, "NavigationPropertyBinding") { nbNode ->
            val path = nbNode.attributes["Path"] ?: return@extractTypedElements null
            val target = nbNode.attributes["Target"] ?: return@extractTypedElements null
            NavigationPropertyBinding(path, target)
        }
    }

    /**
     * Extract ComplexType from UDM
     */
    private fun extractComplexType(node: UDM.Object): ComplexType? {
        val name = node.attributes["Name"] ?: return null
        val baseType = node.attributes["BaseType"]
        val properties = extractProperties(node)
        val navigationProperties = extractNavigationProperties(node)

        return ComplexType(
            name = name,
            properties = properties,
            navigationProperties = navigationProperties,
            baseType = baseType
        )
    }

    /**
     * Extract EnumType from UDM
     */
    private fun extractEnumType(node: UDM.Object): EnumType? {
        val name = node.attributes["Name"] ?: return null
        val underlyingType = node.attributes["UnderlyingType"]
        val isFlags = node.attributes["IsFlags"]?.toBooleanStrictOrNull() ?: false

        val members = extractTypedElements(node, "Member") { memberNode ->
            val memberName = memberNode.attributes["Name"] ?: return@extractTypedElements null
            val memberValue = memberNode.attributes["Value"]?.toLongOrNull()
            EnumMember(memberName, memberValue)
        }

        return EnumType(
            name = name,
            underlyingType = underlyingType,
            isFlags = isFlags,
            members = members
        )
    }

    /**
     * Extract EntityContainer from UDM
     */
    private fun extractEntityContainer(node: UDM.Object): EntityContainer? {
        val name = node.attributes["Name"] ?: return null

        val entitySets = extractTypedElements(node, "EntitySet") { esNode ->
            val esName = esNode.attributes["Name"] ?: return@extractTypedElements null
            val entityType = esNode.attributes["EntityType"] ?: return@extractTypedElements null

            val navBindings = extractNavPropertyBindings(esNode)

            EntitySet(esName, entityType, navBindings)
        }

        val singletons = extractTypedElements(node, "Singleton") { sNode ->
            val sName = sNode.attributes["Name"] ?: return@extractTypedElements null
            val sType = sNode.attributes["Type"] ?: return@extractTypedElements null
            Singleton(sName, sType)
        }

        return EntityContainer(name, entitySets, singletons)
    }

    // ==================== UDM CONVERSION ====================

    /**
     * Convert EDMX document to UDM with USDL directives
     */
    private fun convertToUSDL(doc: EDMXDocument): UDM {
        val rootProperties = linkedMapOf<String, UDM>()

        // %version
        rootProperties["%version"] = UDM.Scalar.string(doc.version)

        // Process all schemas
        for (schema in doc.schemas) {
            // %namespace
            rootProperties["%namespace"] = UDM.Scalar.string(schema.namespace)
            if (schema.alias != null) {
                rootProperties["%alias"] = UDM.Scalar.string(schema.alias)
            }

            // %types — all entity types, complex types, enum types
            val types = linkedMapOf<String, UDM>()

            for (entityType in schema.entityTypes) {
                types[entityType.name] = convertEntityType(entityType, schema.namespace)
            }

            for (complexType in schema.complexTypes) {
                types[complexType.name] = convertComplexType(complexType)
            }

            for (enumType in schema.enumTypes) {
                types[enumType.name] = convertEnumType(enumType)
            }

            if (types.isNotEmpty()) {
                rootProperties["%types"] = UDM.Object(properties = types)
            }

            // %entitySets
            if (schema.entityContainer != null) {
                rootProperties["%entityContainer"] = convertEntityContainer(schema.entityContainer)
            }
        }

        return UDM.Object(properties = rootProperties)
    }

    /**
     * Convert EntityType to UDM with USDL directives
     */
    private fun convertEntityType(entityType: EntityType, namespace: String): UDM {
        val properties = linkedMapOf<String, UDM>()

        properties["%entityType"] = UDM.Scalar.boolean(true)

        if (entityType.baseType != null) {
            properties["%baseType"] = UDM.Scalar.string(entityType.baseType)
        }

        if (entityType.abstract) {
            properties["%abstract"] = UDM.Scalar.boolean(true)
        }

        // %fields
        val fields = mutableListOf<UDM>()
        for (prop in entityType.properties) {
            fields.add(convertProperty(prop, entityType.keyProperties))
        }
        if (fields.isNotEmpty()) {
            properties["%fields"] = UDM.Array(fields)
        }

        // %navigation
        if (entityType.navigationProperties.isNotEmpty()) {
            val navProps = linkedMapOf<String, UDM>()
            for (navProp in entityType.navigationProperties) {
                navProps[navProp.name] = convertNavigationProperty(navProp, namespace)
            }
            properties["%navigation"] = UDM.Object(properties = navProps)
        }

        return UDM.Object(properties = properties)
    }

    /**
     * Convert a property to a USDL field definition
     */
    private fun convertProperty(prop: EdmProperty, keyProperties: List<String>): UDM {
        val fieldProps = linkedMapOf<String, UDM>()

        fieldProps["%name"] = UDM.Scalar.string(prop.name)
        fieldProps["%type"] = UDM.Scalar.string(EdmTypeMapping.toUtlxType(prop.type))

        // Preserve original Edm type as schemaType
        if (EdmTypeMapping.isPrimitive(prop.type)) {
            fieldProps["%schemaType"] = UDM.Scalar.string(prop.type)
        }

        // Key property
        if (prop.name in keyProperties) {
            fieldProps["%key"] = UDM.Scalar.boolean(true)
        }

        // Required (non-nullable)
        fieldProps["%required"] = UDM.Scalar.boolean(!prop.nullable)

        // Constraints
        if (prop.maxLength != null) {
            fieldProps["%maxLength"] = UDM.Scalar.number(prop.maxLength.toDouble())
        }
        if (prop.precision != null) {
            fieldProps["%precision"] = UDM.Scalar.number(prop.precision.toDouble())
        }
        if (prop.scale != null) {
            fieldProps["%scale"] = UDM.Scalar.number(prop.scale.toDouble())
        }
        if (prop.defaultValue != null) {
            fieldProps["%defaultValue"] = UDM.Scalar.string(prop.defaultValue)
        }

        return UDM.Object(properties = fieldProps)
    }

    /**
     * Convert NavigationProperty to USDL navigation directive
     */
    private fun convertNavigationProperty(navProp: NavigationProperty, namespace: String): UDM {
        val props = linkedMapOf<String, UDM>()

        // Determine target type and cardinality from the type string
        val cardinality = EdmTypeMapping.getCardinality(navProp.type)
        val targetType = if (navProp.type.startsWith("Collection(")) {
            EdmTypeMapping.extractCollectionType(navProp.type) ?: navProp.type
        } else {
            navProp.type
        }

        // Use short name if target is in same namespace
        val shortTarget = if (targetType.startsWith("$namespace.")) {
            targetType.removePrefix("$namespace.")
        } else {
            targetType
        }

        props["%target"] = UDM.Scalar.string(shortTarget)
        props["%cardinality"] = UDM.Scalar.string(cardinality)

        if (navProp.partner != null) {
            props["%partner"] = UDM.Scalar.string(navProp.partner)
        }

        if (navProp.containsTarget) {
            props["%containsTarget"] = UDM.Scalar.boolean(true)
        }

        if (navProp.nullable != null) {
            props["%nullable"] = UDM.Scalar.boolean(navProp.nullable)
        }

        // Referential constraints
        if (navProp.referentialConstraints.isNotEmpty()) {
            val constraints = navProp.referentialConstraints.map { rc ->
                UDM.Object(properties = linkedMapOf(
                    "%property" to UDM.Scalar.string(rc.property),
                    "%referencedProperty" to UDM.Scalar.string(rc.referencedProperty)
                ))
            }
            props["%referentialConstraints"] = UDM.Array(constraints)
        }

        return UDM.Object(properties = props)
    }

    /**
     * Convert ComplexType to UDM with USDL directives
     */
    private fun convertComplexType(complexType: ComplexType): UDM {
        val properties = linkedMapOf<String, UDM>()

        properties["%entityType"] = UDM.Scalar.boolean(false)

        if (complexType.baseType != null) {
            properties["%baseType"] = UDM.Scalar.string(complexType.baseType)
        }

        // %fields
        val fields = mutableListOf<UDM>()
        for (prop in complexType.properties) {
            fields.add(convertProperty(prop, emptyList()))
        }
        if (fields.isNotEmpty()) {
            properties["%fields"] = UDM.Array(fields)
        }

        // Navigation properties (complex types can have them in OData v4)
        if (complexType.navigationProperties.isNotEmpty()) {
            val navProps = linkedMapOf<String, UDM>()
            for (navProp in complexType.navigationProperties) {
                navProps[navProp.name] = convertNavigationProperty(navProp, "")
            }
            properties["%navigation"] = UDM.Object(properties = navProps)
        }

        return UDM.Object(properties = properties)
    }

    /**
     * Convert EnumType to UDM with USDL directives
     */
    private fun convertEnumType(enumType: EnumType): UDM {
        val properties = linkedMapOf<String, UDM>()

        properties["%kind"] = UDM.Scalar.string("enum")

        if (enumType.underlyingType != null) {
            properties["%underlyingType"] = UDM.Scalar.string(enumType.underlyingType)
        }

        if (enumType.isFlags) {
            properties["%isFlags"] = UDM.Scalar.boolean(true)
        }

        // Members
        val members = enumType.members.map { member ->
            val memberProps = linkedMapOf<String, UDM>()
            memberProps["%name"] = UDM.Scalar.string(member.name)
            if (member.value != null) {
                memberProps["%value"] = UDM.Scalar.number(member.value.toDouble())
            }
            UDM.Object(properties = memberProps)
        }
        if (members.isNotEmpty()) {
            properties["%members"] = UDM.Array(members)
        }

        return UDM.Object(properties = properties)
    }

    /**
     * Convert EntityContainer to UDM
     */
    private fun convertEntityContainer(container: EntityContainer): UDM {
        val properties = linkedMapOf<String, UDM>()

        properties["%name"] = UDM.Scalar.string(container.name)

        if (container.entitySets.isNotEmpty()) {
            val entitySets = linkedMapOf<String, UDM>()
            for (es in container.entitySets) {
                val esProps = linkedMapOf<String, UDM>()
                esProps["%entityType"] = UDM.Scalar.string(es.entityType)
                if (es.navigationPropertyBindings.isNotEmpty()) {
                    val bindings = es.navigationPropertyBindings.map { binding ->
                        UDM.Object(properties = linkedMapOf(
                            "%path" to UDM.Scalar.string(binding.path),
                            "%target" to UDM.Scalar.string(binding.target)
                        ))
                    }
                    esProps["%navigationPropertyBindings"] = UDM.Array(bindings)
                }
                entitySets[es.name] = UDM.Object(properties = esProps)
            }
            properties["%entitySets"] = UDM.Object(properties = entitySets)
        }

        if (container.singletons.isNotEmpty()) {
            val singletons = linkedMapOf<String, UDM>()
            for (s in container.singletons) {
                singletons[s.name] = UDM.Scalar.string(s.type)
            }
            properties["%singletons"] = UDM.Object(properties = singletons)
        }

        return UDM.Object(properties = properties)
    }

    companion object {
        /**
         * Check if content looks like EDMX XML (for auto-detection)
         */
        fun looksLikeEDMX(content: String): Boolean {
            val trimmed = content.trimStart()
            return trimmed.contains("<edmx:Edmx", ignoreCase = true) ||
                   trimmed.contains("schemas.microsoft.com/ado/2007/06/edmx") ||
                   trimmed.contains("docs.oasis-open.org/odata/ns/edmx")
        }
    }
}
