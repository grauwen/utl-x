package org.apache.utlx.formats.osch

import org.apache.utlx.core.udm.UDM

/**
 * EDMX Serializer - Converts UDM with USDL directives to EDMX/CSDL XML
 *
 * Supports two modes (following XSD serializer pattern):
 * 1. USDL mode: UDM has %types directive → transform directives to EDMX XML
 * 2. Low-level mode: UDM already has EDMX XML structure → pass through via XMLSerializer
 *
 * Usage:
 *   val edmx = EDMXSerializer(namespace = "MyNamespace").serialize(udm)
 */
class EDMXSerializer(
    private val namespace: String? = null,
    private val prettyPrint: Boolean = true,
    private val options: Map<String, Any> = emptyMap()
) {
    constructor(options: Map<String, Any>) : this(
        namespace = options["namespace"] as? String,
        prettyPrint = (options["prettyPrint"] as? Boolean) ?: true,
        options = options
    )

    private val indent = "  "

    /**
     * Serialize UDM to EDMX XML string
     */
    fun serialize(udm: UDM): String {
        if (udm !is UDM.Object) {
            throw IllegalArgumentException("Expected UDM.Object for EDMX serialization, got ${udm::class.simpleName}")
        }

        val mode = detectMode(udm)
        return when (mode) {
            SerializationMode.UNIVERSAL_DSL -> serializeFromUSDL(udm)
            SerializationMode.LOW_LEVEL -> serializeLowLevel(udm)
        }
    }

    private enum class SerializationMode {
        LOW_LEVEL,
        UNIVERSAL_DSL
    }

    /**
     * Detect whether the UDM uses USDL directives or low-level EDMX structure
     */
    private fun detectMode(udm: UDM.Object): SerializationMode {
        return when {
            udm.properties.containsKey("%types") -> SerializationMode.UNIVERSAL_DSL
            udm.properties.containsKey("%namespace") -> SerializationMode.UNIVERSAL_DSL
            udm.properties.keys.any { it.contains("edmx:", ignoreCase = true) } -> SerializationMode.LOW_LEVEL
            else -> SerializationMode.UNIVERSAL_DSL
        }
    }

    /**
     * Serialize from USDL directives to EDMX XML
     */
    private fun serializeFromUSDL(udm: UDM.Object): String {
        val sb = StringBuilder()

        // XML declaration
        sb.appendLine("""<?xml version="1.0" encoding="utf-8"?>""")

        // Extract USDL directives
        val version = (udm.properties["%version"] as? UDM.Scalar)?.value as? String ?: "4.0"
        val ns = namespace
            ?: (udm.properties["%namespace"] as? UDM.Scalar)?.value as? String
            ?: "Default.Namespace"
        val alias = (udm.properties["%alias"] as? UDM.Scalar)?.value as? String
        val types = udm.properties["%types"] as? UDM.Object
        val entityContainer = udm.properties["%entityContainer"] as? UDM.Object

        // EDMX wrapper
        sb.appendLine("""<edmx:Edmx Version="$version" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">""")
        sb.appendLine("${indent}<edmx:DataServices>")

        // Schema element
        val aliasAttr = if (alias != null) """ Alias="$alias"""" else ""
        sb.appendLine("""${indent}${indent}<Schema Namespace="$ns"$aliasAttr xmlns="http://docs.oasis-open.org/odata/ns/edm">""")

        // Serialize types
        if (types != null) {
            for ((typeName, typeDef) in types.properties) {
                if (typeDef !is UDM.Object) continue

                val kind = (typeDef.properties["%kind"] as? UDM.Scalar)?.value as? String
                val isEntityType = (typeDef.properties["%entityType"] as? UDM.Scalar)?.value as? Boolean

                when {
                    kind == "enum" -> serializeEnumType(sb, typeName, typeDef, 3)
                    isEntityType == true -> serializeEntityType(sb, typeName, typeDef, ns, 3)
                    isEntityType == false -> serializeComplexType(sb, typeName, typeDef, 3)
                    else -> serializeEntityType(sb, typeName, typeDef, ns, 3)
                }
            }
        }

        // Serialize entity container
        if (entityContainer != null) {
            serializeEntityContainer(sb, entityContainer, ns, 3)
        } else if (types != null) {
            // Auto-generate entity container from entity types
            generateEntityContainer(sb, types, ns, 3)
        }

        sb.appendLine("${indent}${indent}</Schema>")
        sb.appendLine("${indent}</edmx:DataServices>")
        sb.appendLine("</edmx:Edmx>")

        return sb.toString()
    }

    /**
     * Serialize an EntityType from USDL directives
     */
    private fun serializeEntityType(sb: StringBuilder, name: String, typeDef: UDM.Object, namespace: String, depth: Int) {
        val pad = indent.repeat(depth)
        val innerPad = indent.repeat(depth + 1)
        val isAbstract = (typeDef.properties["%abstract"] as? UDM.Scalar)?.value as? Boolean ?: false
        val baseType = (typeDef.properties["%baseType"] as? UDM.Scalar)?.value as? String

        val abstractAttr = if (isAbstract) """ Abstract="true"""" else ""
        val baseTypeAttr = if (baseType != null) """ BaseType="$baseType"""" else ""

        sb.appendLine("""${pad}<EntityType Name="$name"$abstractAttr$baseTypeAttr>""")

        // Key properties
        val fields = typeDef.properties["%fields"] as? UDM.Array
        val keyFields = fields?.elements?.filterIsInstance<UDM.Object>()?.filter { field ->
            (field.properties["%key"] as? UDM.Scalar)?.value == true
        }

        if (!keyFields.isNullOrEmpty()) {
            sb.appendLine("${innerPad}<Key>")
            for (keyField in keyFields) {
                val keyName = (keyField.properties["%name"] as? UDM.Scalar)?.value as? String ?: continue
                sb.appendLine("""${innerPad}${indent}<PropertyRef Name="$keyName"/>""")
            }
            sb.appendLine("${innerPad}</Key>")
        }

        // Properties
        if (fields != null) {
            for (field in fields.elements) {
                if (field !is UDM.Object) continue
                serializeProperty(sb, field, depth + 1)
            }
        }

        // Navigation properties
        val navigation = typeDef.properties["%navigation"] as? UDM.Object
        if (navigation != null) {
            for ((navName, navDef) in navigation.properties) {
                if (navDef !is UDM.Object) continue
                serializeNavigationProperty(sb, navName, navDef, namespace, depth + 1)
            }
        }

        sb.appendLine("${pad}</EntityType>")
    }

    /**
     * Serialize a Property element
     */
    private fun serializeProperty(sb: StringBuilder, field: UDM.Object, depth: Int) {
        val pad = indent.repeat(depth)
        val name = (field.properties["%name"] as? UDM.Scalar)?.value as? String ?: return
        val utlxType = (field.properties["%type"] as? UDM.Scalar)?.value as? String ?: "string"
        val schemaType = (field.properties["%schemaType"] as? UDM.Scalar)?.value as? String
        val required = (field.properties["%required"] as? UDM.Scalar)?.value as? Boolean ?: false

        val edmType = EdmTypeMapping.toEdmType(utlxType, schemaType)
        val nullableAttr = if (!required) "" else """ Nullable="false""""

        val attrs = StringBuilder()
        attrs.append(""" Name="$name" Type="$edmType"$nullableAttr""")

        // Constraints
        val maxLength = (field.properties["%maxLength"] as? UDM.Scalar)?.value
        if (maxLength != null) {
            val mlValue = if (maxLength is Number) maxLength.toInt() else maxLength
            attrs.append(""" MaxLength="$mlValue"""")
        }

        val precision = (field.properties["%precision"] as? UDM.Scalar)?.value
        if (precision != null) {
            val pValue = if (precision is Number) precision.toInt() else precision
            attrs.append(""" Precision="$pValue"""")
        }

        val scale = (field.properties["%scale"] as? UDM.Scalar)?.value
        if (scale != null) {
            val sValue = if (scale is Number) scale.toInt() else scale
            attrs.append(""" Scale="$sValue"""")
        }

        val defaultValue = (field.properties["%defaultValue"] as? UDM.Scalar)?.value as? String
        if (defaultValue != null) {
            attrs.append(""" DefaultValue="$defaultValue"""")
        }

        sb.appendLine("""${pad}<Property$attrs/>""")
    }

    /**
     * Serialize a NavigationProperty element
     */
    private fun serializeNavigationProperty(sb: StringBuilder, name: String, navDef: UDM.Object, namespace: String, depth: Int) {
        val pad = indent.repeat(depth)
        val innerPad = indent.repeat(depth + 1)

        val target = (navDef.properties["%target"] as? UDM.Scalar)?.value as? String ?: return
        val cardinality = (navDef.properties["%cardinality"] as? UDM.Scalar)?.value as? String ?: "1"
        val partner = (navDef.properties["%partner"] as? UDM.Scalar)?.value as? String
        val containsTarget = (navDef.properties["%containsTarget"] as? UDM.Scalar)?.value as? Boolean ?: false
        val nullable = (navDef.properties["%nullable"] as? UDM.Scalar)?.value as? Boolean

        // Build the Type attribute
        val qualifiedTarget = if (target.contains('.')) target else "$namespace.$target"
        val typeValue = if (cardinality == "*") "Collection($qualifiedTarget)" else qualifiedTarget

        val partnerAttr = if (partner != null) """ Partner="$partner"""" else ""
        val containsTargetAttr = if (containsTarget) """ ContainsTarget="true"""" else ""
        val nullableAttr = if (nullable != null) """ Nullable="$nullable"""" else ""

        // Check for referential constraints
        val refConstraints = navDef.properties["%referentialConstraints"] as? UDM.Array
        val hasChildren = refConstraints != null && refConstraints.elements.isNotEmpty()

        if (hasChildren) {
            sb.appendLine("""${pad}<NavigationProperty Name="$name" Type="$typeValue"$partnerAttr$containsTargetAttr$nullableAttr>""")
            for (rc in refConstraints!!.elements) {
                if (rc !is UDM.Object) continue
                val property = (rc.properties["%property"] as? UDM.Scalar)?.value as? String ?: continue
                val refProperty = (rc.properties["%referencedProperty"] as? UDM.Scalar)?.value as? String ?: continue
                sb.appendLine("""${innerPad}<ReferentialConstraint Property="$property" ReferencedProperty="$refProperty"/>""")
            }
            sb.appendLine("${pad}</NavigationProperty>")
        } else {
            sb.appendLine("""${pad}<NavigationProperty Name="$name" Type="$typeValue"$partnerAttr$containsTargetAttr$nullableAttr/>""")
        }
    }

    /**
     * Serialize a ComplexType from USDL directives
     */
    private fun serializeComplexType(sb: StringBuilder, name: String, typeDef: UDM.Object, depth: Int) {
        val pad = indent.repeat(depth)
        val baseType = (typeDef.properties["%baseType"] as? UDM.Scalar)?.value as? String
        val baseTypeAttr = if (baseType != null) """ BaseType="$baseType"""" else ""

        sb.appendLine("""${pad}<ComplexType Name="$name"$baseTypeAttr>""")

        val fields = typeDef.properties["%fields"] as? UDM.Array
        if (fields != null) {
            for (field in fields.elements) {
                if (field !is UDM.Object) continue
                serializeProperty(sb, field, depth + 1)
            }
        }

        sb.appendLine("${pad}</ComplexType>")
    }

    /**
     * Serialize an EnumType from USDL directives
     */
    private fun serializeEnumType(sb: StringBuilder, name: String, typeDef: UDM.Object, depth: Int) {
        val pad = indent.repeat(depth)
        val innerPad = indent.repeat(depth + 1)
        val underlyingType = (typeDef.properties["%underlyingType"] as? UDM.Scalar)?.value as? String
        val isFlags = (typeDef.properties["%isFlags"] as? UDM.Scalar)?.value as? Boolean ?: false

        val underlyingAttr = if (underlyingType != null) """ UnderlyingType="$underlyingType"""" else ""
        val flagsAttr = if (isFlags) """ IsFlags="true"""" else ""

        sb.appendLine("""${pad}<EnumType Name="$name"$underlyingAttr$flagsAttr>""")

        val members = typeDef.properties["%members"] as? UDM.Array
        if (members != null) {
            for (member in members.elements) {
                if (member !is UDM.Object) continue
                val memberName = (member.properties["%name"] as? UDM.Scalar)?.value as? String ?: continue
                val memberValue = (member.properties["%value"] as? UDM.Scalar)?.value
                val valueAttr = if (memberValue != null) {
                    val v = if (memberValue is Number) memberValue.toLong() else memberValue
                    """ Value="$v""""
                } else ""
                sb.appendLine("""${innerPad}<Member Name="$memberName"$valueAttr/>""")
            }
        }

        sb.appendLine("${pad}</EnumType>")
    }

    /**
     * Serialize an EntityContainer from USDL directives
     */
    private fun serializeEntityContainer(sb: StringBuilder, container: UDM.Object, namespace: String, depth: Int) {
        val pad = indent.repeat(depth)
        val innerPad = indent.repeat(depth + 1)
        val name = (container.properties["%name"] as? UDM.Scalar)?.value as? String ?: "Container"

        sb.appendLine("""${pad}<EntityContainer Name="$name">""")

        // Entity sets
        val entitySets = container.properties["%entitySets"] as? UDM.Object
        if (entitySets != null) {
            for ((esName, esDef) in entitySets.properties) {
                if (esDef !is UDM.Object) continue
                val entityType = (esDef.properties["%entityType"] as? UDM.Scalar)?.value as? String ?: continue
                val navBindings = esDef.properties["%navigationPropertyBindings"] as? UDM.Array

                if (navBindings != null && navBindings.elements.isNotEmpty()) {
                    sb.appendLine("""${innerPad}<EntitySet Name="$esName" EntityType="$entityType">""")
                    for (binding in navBindings.elements) {
                        if (binding !is UDM.Object) continue
                        val path = (binding.properties["%path"] as? UDM.Scalar)?.value as? String ?: continue
                        val target = (binding.properties["%target"] as? UDM.Scalar)?.value as? String ?: continue
                        sb.appendLine("""${innerPad}${indent}<NavigationPropertyBinding Path="$path" Target="$target"/>""")
                    }
                    sb.appendLine("${innerPad}</EntitySet>")
                } else {
                    sb.appendLine("""${innerPad}<EntitySet Name="$esName" EntityType="$entityType"/>""")
                }
            }
        }

        // Singletons
        val singletons = container.properties["%singletons"] as? UDM.Object
        if (singletons != null) {
            for ((sName, sType) in singletons.properties) {
                val typeStr = (sType as? UDM.Scalar)?.value as? String ?: continue
                sb.appendLine("""${innerPad}<Singleton Name="$sName" Type="$typeStr"/>""")
            }
        }

        sb.appendLine("${pad}</EntityContainer>")
    }

    /**
     * Auto-generate EntityContainer from entity types
     */
    private fun generateEntityContainer(sb: StringBuilder, types: UDM.Object, namespace: String, depth: Int) {
        val pad = indent.repeat(depth)
        val innerPad = indent.repeat(depth + 1)

        // Collect entity types that should have entity sets
        val entityTypes = types.properties.filter { (_, typeDef) ->
            typeDef is UDM.Object &&
            (typeDef.properties["%entityType"] as? UDM.Scalar)?.value == true
        }

        if (entityTypes.isEmpty()) return

        sb.appendLine("""${pad}<EntityContainer Name="Container">""")
        for ((typeName, _) in entityTypes) {
            // Convention: EntitySet name is plural of EntityType name
            val setName = pluralize(typeName)
            sb.appendLine("""${innerPad}<EntitySet Name="$setName" EntityType="$namespace.$typeName"/>""")
        }
        sb.appendLine("${pad}</EntityContainer>")
    }

    /**
     * Simple pluralization (append 's' or 'es')
     */
    private fun pluralize(name: String): String {
        return when {
            name.endsWith("s") || name.endsWith("x") || name.endsWith("z") ||
            name.endsWith("ch") || name.endsWith("sh") -> "${name}es"
            name.endsWith("y") && name.length > 1 && !name[name.length - 2].isVowel() ->
                "${name.dropLast(1)}ies"
            else -> "${name}s"
        }
    }

    private fun Char.isVowel(): Boolean = this in "aeiouAEIOU"

    /**
     * Low-level mode: serialize UDM that already has EDMX XML structure
     * Delegates to XMLSerializer
     */
    private fun serializeLowLevel(udm: UDM.Object): String {
        val xmlSerializer = org.apache.utlx.formats.xml.XMLSerializer(
            prettyPrint = prettyPrint
        )
        return xmlSerializer.serialize(udm)
    }
}
