package org.apache.utlx.formats.osch

/**
 * OData Schema Intermediate Model
 *
 * Represents EDMX/CSDL metadata structure for clean conversion between
 * OData schema documents and UDM with USDL directives.
 */

data class EDMXDocument(
    val version: String,                    // "4.0" or "4.01"
    val schemas: List<EDMXSchema>,
    val references: List<EDMXReference> = emptyList()
)

data class EDMXReference(
    val uri: String,
    val includes: List<EDMXInclude> = emptyList()
)

data class EDMXInclude(
    val namespace: String,
    val alias: String? = null
)

data class EDMXSchema(
    val namespace: String,
    val alias: String? = null,
    val entityTypes: List<EntityType> = emptyList(),
    val complexTypes: List<ComplexType> = emptyList(),
    val enumTypes: List<EnumType> = emptyList(),
    val entityContainer: EntityContainer? = null
)

data class EntityType(
    val name: String,
    val keyProperties: List<String> = emptyList(),
    val properties: List<EdmProperty> = emptyList(),
    val navigationProperties: List<NavigationProperty> = emptyList(),
    val baseType: String? = null,
    val abstract: Boolean = false
)

data class ComplexType(
    val name: String,
    val properties: List<EdmProperty> = emptyList(),
    val navigationProperties: List<NavigationProperty> = emptyList(),
    val baseType: String? = null
)

data class EnumType(
    val name: String,
    val underlyingType: String? = null,
    val isFlags: Boolean = false,
    val members: List<EnumMember> = emptyList()
)

data class EnumMember(
    val name: String,
    val value: Long? = null
)

data class EdmProperty(
    val name: String,
    val type: String,                       // "Edm.String", "Edm.Int32", etc.
    val nullable: Boolean = true,
    val maxLength: Int? = null,
    val precision: Int? = null,
    val scale: Int? = null,
    val defaultValue: String? = null
)

data class NavigationProperty(
    val name: String,
    val type: String,                       // "Collection(NS.EntityType)" or "NS.EntityType"
    val nullable: Boolean? = null,
    val partner: String? = null,
    val containsTarget: Boolean = false,
    val referentialConstraints: List<ReferentialConstraint> = emptyList()
)

data class ReferentialConstraint(
    val property: String,                   // Local property
    val referencedProperty: String          // Target property
)

data class EntityContainer(
    val name: String,
    val entitySets: List<EntitySet> = emptyList(),
    val singletons: List<Singleton> = emptyList()
)

data class EntitySet(
    val name: String,
    val entityType: String,
    val navigationPropertyBindings: List<NavigationPropertyBinding> = emptyList()
)

data class Singleton(
    val name: String,
    val type: String
)

data class NavigationPropertyBinding(
    val path: String,
    val target: String
)

/**
 * Edm primitive type to UTLX type mapping
 */
object EdmTypeMapping {
    /**
     * Map Edm primitive types to UTLX canonical types
     */
    fun toUtlxType(edmType: String): String {
        // Handle Collection(T) → array
        if (edmType.startsWith("Collection(") && edmType.endsWith(")")) {
            return "array"
        }

        return when (edmType) {
            "Edm.String", "Edm.Guid", "Edm.Duration" -> "string"
            "Edm.Boolean" -> "boolean"
            "Edm.Byte", "Edm.SByte", "Edm.Int16", "Edm.Int32", "Edm.Int64" -> "integer"
            "Edm.Single", "Edm.Double", "Edm.Decimal" -> "number"
            "Edm.Date" -> "date"
            "Edm.TimeOfDay" -> "time"
            "Edm.DateTimeOffset" -> "datetime"
            "Edm.Binary", "Edm.Stream" -> "binary"
            else -> {
                // Geography/Geometry types → any
                if (edmType.startsWith("Edm.Geography") || edmType.startsWith("Edm.Geometry")) {
                    "any"
                } else {
                    // Non-primitive (complex/entity type reference) → object
                    "object"
                }
            }
        }
    }

    /**
     * Check if an Edm type is a primitive type
     */
    fun isPrimitive(edmType: String): Boolean {
        if (edmType.startsWith("Collection(")) return false
        return edmType.startsWith("Edm.")
    }

    /**
     * Map UTLX canonical type back to Edm type for serialization
     */
    fun toEdmType(utlxType: String, schemaType: String? = null): String {
        // If original schema type is preserved, use it
        if (schemaType != null && schemaType.startsWith("Edm.")) {
            return schemaType
        }

        return when (utlxType) {
            "string" -> "Edm.String"
            "boolean" -> "Edm.Boolean"
            "integer" -> "Edm.Int32"
            "number" -> "Edm.Decimal"
            "date" -> "Edm.Date"
            "time" -> "Edm.TimeOfDay"
            "datetime" -> "Edm.DateTimeOffset"
            "binary" -> "Edm.Binary"
            else -> "Edm.String"
        }
    }

    /**
     * Extract the element type from a Collection type string
     * "Collection(NS.EntityType)" → "NS.EntityType"
     */
    fun extractCollectionType(collectionType: String): String? {
        if (collectionType.startsWith("Collection(") && collectionType.endsWith(")")) {
            return collectionType.substring(11, collectionType.length - 1)
        }
        return null
    }

    /**
     * Determine cardinality from an OData type string
     * Collection(T) → "*", otherwise → "1"
     */
    fun getCardinality(type: String): String {
        return if (type.startsWith("Collection(")) "*" else "1"
    }

    /**
     * Extract the short type name from a qualified name
     * "Microsoft.OData.SampleService.Models.TripPin.Person" → "Person"
     */
    fun shortName(qualifiedName: String): String {
        return qualifiedName.substringAfterLast('.')
    }
}
