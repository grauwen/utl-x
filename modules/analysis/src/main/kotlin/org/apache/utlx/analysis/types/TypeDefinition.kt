// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/types/TypeDefinition.kt
package org.apache.utlx.analysis.types

/**
 * Core type system for UTL-X schema analysis
 * 
 * Represents types in a format-agnostic way that can be:
 * - Parsed from XSD, JSON Schema, CSV Schema, etc.
 * - Inferred from UTL-X transformations
 * - Generated to any schema format
 */
sealed class TypeDefinition {
    
    /**
     * Scalar (primitive) types
     */
    data class Scalar(
        val kind: ScalarKind,
        val constraints: List<Constraint> = emptyList()
    ) : TypeDefinition() {
        
        fun withConstraint(constraint: Constraint): Scalar {
            return copy(constraints = constraints + constraint)
        }
        
        fun withConstraints(newConstraints: List<Constraint>): Scalar {
            return copy(constraints = constraints + newConstraints)
        }
    }
    
    /**
     * Array types
     */
    data class Array(
        val elementType: TypeDefinition,
        val minItems: Int? = null,
        val maxItems: Int? = null
    ) : TypeDefinition() {
        
        fun withMinItems(min: Int): Array = copy(minItems = min)
        fun withMaxItems(max: Int): Array = copy(maxItems = max)
        fun withBounds(min: Int?, max: Int?): Array = copy(minItems = min, maxItems = max)
    }
    
    /**
     * Object types (structures with named properties)
     */
    data class Object(
        val properties: Map<String, PropertyType>,
        val required: Set<String> = emptySet(),
        val additionalProperties: Boolean = false
    ) : TypeDefinition() {
        
        fun withProperty(name: String, type: PropertyType): Object {
            return copy(properties = properties + (name to type))
        }
        
        fun withRequired(fieldName: String): Object {
            return copy(required = required + fieldName)
        }
        
        fun withAdditionalProperties(allowed: Boolean): Object {
            return copy(additionalProperties = allowed)
        }
        
        fun isRequired(fieldName: String): Boolean = fieldName in required
        
        fun getProperty(name: String): PropertyType? = properties[name]
    }
    
    /**
     * Union types (one of several possible types)
     */
    data class Union(
        val types: List<TypeDefinition>
    ) : TypeDefinition() {
        
        init {
            require(types.size >= 2) { "Union must have at least 2 types" }
        }
        
        fun withType(type: TypeDefinition): Union {
            return copy(types = types + type)
        }
    }
    
    /**
     * Any type (unknown or dynamic)
     */
    object Any : TypeDefinition() {
        override fun toString(): String = "Any"
    }

    /**
     * Unknown type - represents a value whose type is not yet determined
     * Can be assigned to anything and accepts anything
     */
    object Unknown : TypeDefinition() {
        override fun toString(): String = "Unknown"
    }

    /**
     * Never type - represents an impossible or error type
     * Cannot be assigned to anything (except Never)
     */
    object Never : TypeDefinition() {
        override fun toString(): String = "Never"
    }

    /**
     * Check if this type is compatible with another type
     */
    fun isCompatibleWith(other: TypeDefinition): Boolean {
        return when {
            // Any type accepts all types
            this is Any || other is Any -> true

            // Source is union - all members must be compatible with target
            this is Union -> this.types.all { it.isCompatibleWith(other) }

            // Target is union - source must be compatible with at least one member
            other is Union -> other.types.any { this.isCompatibleWith(it) }

            // Scalar type compatibility
            this is Scalar && other is Scalar -> {
                when {
                    // Exact match
                    this.kind == other.kind -> true
                    // INTEGER can convert to NUMBER
                    this.kind == ScalarKind.INTEGER && other.kind == ScalarKind.NUMBER -> true
                    // Everything can convert to STRING
                    other.kind == ScalarKind.STRING -> true
                    else -> false
                }
            }

            // Array type compatibility
            this is Array && other is Array -> this.elementType.isCompatibleWith(other.elementType)

            // Object type compatibility (structural)
            this is Object && other is Object -> {
                // Check all required fields in 'other' exist in 'this'
                other.required.all { field ->
                    this.properties.containsKey(field) &&
                    this.properties[field]!!.type.isCompatibleWith(other.properties[field]!!.type)
                }
            }

            else -> false
        }
    }
}

/**
 * Property type with metadata
 */
data class PropertyType(
    val type: TypeDefinition,
    val nullable: Boolean = false,
    val description: String? = null,
    val defaultValue: Any? = null
) {
    fun isNullable(): Boolean = nullable

    /**
     * Get the effective type including nullability
     */
    val effectiveType: TypeDefinition
        get() = if (nullable) type.nullable() else type

    fun withDescription(desc: String): PropertyType {
        return copy(description = desc)
    }

    fun withDefault(value: Any): PropertyType {
        return copy(defaultValue = value)
    }

    fun makeNullable(): PropertyType {
        return copy(nullable = true)
    }
}

/**
 * Scalar type kinds
 */
enum class ScalarKind {
    STRING,     // Text data
    INTEGER,    // Whole numbers
    NUMBER,     // Floating-point numbers
    BOOLEAN,    // true/false
    NULL,       // Null value
    DATE,       // Date only (YYYY-MM-DD)
    DATETIME,   // Date and time with timezone
    TIME,       // Time only (HH:MM:SS)
    DURATION,   // Time duration (e.g., PT1H30M)
    BINARY;     // Binary data
    
    /**
     * Check if this kind is numeric
     */
    fun isNumeric(): Boolean = this == INTEGER || this == NUMBER
    
    /**
     * Check if this kind is temporal
     */
    fun isTemporal(): Boolean = this == DATE || this == DATETIME || this == TIME || this == DURATION
    
    /**
     * Get compatible JSON type
     */
    fun toJsonType(): String = when (this) {
        STRING -> "string"
        INTEGER -> "integer"
        NUMBER -> "number"
        BOOLEAN -> "boolean"
        NULL -> "null"
        DATE, DATETIME, TIME, DURATION, BINARY -> "string"
    }
}

/**
 * Constraint on a scalar type
 */
sealed class Constraint {
    /**
     * Minimum string length constraint
     */
    data class MinLength(val value: Int) : Constraint() {
        override fun toString(): String = "MinLength($value)"
    }

    /**
     * Maximum string length constraint
     */
    data class MaxLength(val value: Int) : Constraint() {
        override fun toString(): String = "MaxLength($value)"
    }

    /**
     * Regular expression pattern constraint
     */
    data class Pattern(val regex: String) : Constraint() {
        override fun toString(): String = "Pattern($regex)"
    }

    /**
     * Minimum numeric value constraint (inclusive)
     */
    data class Minimum(val value: Double) : Constraint() {
        override fun toString(): String = "Minimum($value)"
    }

    /**
     * Maximum numeric value constraint (inclusive)
     */
    data class Maximum(val value: Double) : Constraint() {
        override fun toString(): String = "Maximum($value)"
    }

    /**
     * Enumeration of allowed values
     */
    data class Enum(val values: List<Any>) : Constraint() {
        override fun toString(): String = "Enum($values)"
    }

    /**
     * Custom constraint with name and parameters
     */
    data class Custom(val name: String, val params: Map<String, Any> = emptyMap()) : Constraint() {
        override fun toString(): String = "Custom($name, $params)"
    }

    /**
     * Check if this constraint applies to strings
     */
    fun isStringConstraint(): Boolean = when (this) {
        is MinLength, is MaxLength, is Pattern, is Enum -> true
        else -> false
    }

    /**
     * Check if this constraint applies to numbers
     */
    fun isNumericConstraint(): Boolean = when (this) {
        is Minimum, is Maximum, is Enum -> true
        else -> false
    }
}

/**
 * Builder for creating type definitions fluently
 */
class TypeBuilder {
    
    companion object {
        // Scalar types
        fun string(
            minLength: Int? = null,
            maxLength: Int? = null,
            pattern: String? = null,
            enum: List<String>? = null
        ): TypeDefinition.Scalar {
            val constraints = mutableListOf<Constraint>()
            minLength?.let { constraints.add(Constraint.MinLength(it)) }
            maxLength?.let { constraints.add(Constraint.MaxLength(it)) }
            pattern?.let { constraints.add(Constraint.Pattern(it)) }
            enum?.let { constraints.add(Constraint.Enum(it)) }
            return TypeDefinition.Scalar(ScalarKind.STRING, constraints)
        }

        fun integer(
            min: Int? = null,
            max: Int? = null,
            enum: List<Int>? = null
        ): TypeDefinition.Scalar {
            val constraints = mutableListOf<Constraint>()
            min?.let { constraints.add(Constraint.Minimum(it.toDouble())) }
            max?.let { constraints.add(Constraint.Maximum(it.toDouble())) }
            enum?.let { constraints.add(Constraint.Enum(it.map { it.toString() })) }
            return TypeDefinition.Scalar(ScalarKind.INTEGER, constraints)
        }

        fun number(
            min: Double? = null,
            max: Double? = null
        ): TypeDefinition.Scalar {
            val constraints = mutableListOf<Constraint>()
            min?.let { constraints.add(Constraint.Minimum(it)) }
            max?.let { constraints.add(Constraint.Maximum(it)) }
            return TypeDefinition.Scalar(ScalarKind.NUMBER, constraints)
        }
        
        fun boolean(): TypeDefinition.Scalar {
            return TypeDefinition.Scalar(ScalarKind.BOOLEAN)
        }
        
        fun date(): TypeDefinition.Scalar {
            return TypeDefinition.Scalar(ScalarKind.DATE)
        }
        
        fun datetime(): TypeDefinition.Scalar {
            return TypeDefinition.Scalar(ScalarKind.DATETIME)
        }
        
        fun nullType(): TypeDefinition.Scalar {
            return TypeDefinition.Scalar(ScalarKind.NULL)
        }
        
        // Array type
        fun array(
            elementType: TypeDefinition,
            minItems: Int? = null,
            maxItems: Int? = null
        ): TypeDefinition.Array {
            return TypeDefinition.Array(elementType, minItems, maxItems)
        }
        
        // Object type
        fun obj(
            properties: Map<String, PropertyType> = emptyMap(),
            required: Set<String> = emptySet(),
            additionalProperties: Boolean = false
        ): TypeDefinition.Object {
            return TypeDefinition.Object(properties, required, additionalProperties)
        }
        
        // Union type
        fun union(vararg types: TypeDefinition): TypeDefinition.Union {
            require(types.size >= 2) { "Union requires at least 2 types" }
            return TypeDefinition.Union(types.toList())
        }
        
        // Property type
        fun property(
            type: TypeDefinition,
            nullable: Boolean = false,
            description: String? = null
        ): PropertyType {
            return PropertyType(type, nullable, description)
        }
    }
}

/**
 * Extension functions for easier type building
 */

// Make type nullable
fun TypeDefinition.nullable(): TypeDefinition.Union {
    return TypeDefinition.Union(listOf(this, TypeDefinition.Scalar(ScalarKind.NULL)))
}

// Create array of this type
fun TypeDefinition.asArray(minItems: Int? = null, maxItems: Int? = null): TypeDefinition.Array {
    return TypeDefinition.Array(this, minItems, maxItems)
}

// Check if a type is nullable (i.e., is a Union containing NULL)
val TypeDefinition.isNullable: Boolean
    get() = this is TypeDefinition.Union &&
            this.types.any { it is TypeDefinition.Scalar && it.kind == ScalarKind.NULL }

// Get non-nullable version of a type (removes NULL from Union if present)
val TypeDefinition.nonNullable: TypeDefinition
    get() = when {
        !this.isNullable -> this
        this is TypeDefinition.Union -> {
            val nonNullTypes = this.types.filter {
                !(it is TypeDefinition.Scalar && it.kind == ScalarKind.NULL)
            }
            when (nonNullTypes.size) {
                0 -> TypeDefinition.Never
                1 -> nonNullTypes[0]
                else -> TypeDefinition.Union(nonNullTypes)
            }
        }
        else -> this
    }

// Common type aliases
object CommonTypes {
    val STRING = TypeBuilder.string()
    val INTEGER = TypeBuilder.integer()
    val NUMBER = TypeBuilder.number()
    val BOOLEAN = TypeBuilder.boolean()
    val DATE = TypeBuilder.date()
    val DATETIME = TypeBuilder.datetime()
    val NULL = TypeBuilder.nullType()
    val ANY = TypeDefinition.Any
    
    // Common patterns
    val EMAIL = TypeBuilder.string(
        pattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    )
    
    val URL = TypeBuilder.string(
        pattern = "^https?://.*"
    )
    
    val UUID = TypeBuilder.string(
        pattern = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )
    
    val PHONE = TypeBuilder.string(
        pattern = "^\\+?[1-9]\\d{1,14}$"
    )
    
    val POSTAL_CODE = TypeBuilder.string(
        pattern = "^\\d{5}(-\\d{4})?$"
    )
}
