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
     * Check if this type is compatible with another type
     */
    fun isCompatibleWith(other: TypeDefinition): Boolean {
        return when {
            this is Any || other is Any -> true
            this::class != other::class -> false
            this is Scalar && other is Scalar -> this.kind == other.kind
            this is Array && other is Array -> this.elementType.isCompatibleWith(other.elementType)
            this is Object && other is Object -> {
                // Check all required fields in 'other' exist in 'this'
                other.required.all { field ->
                    this.properties.containsKey(field) &&
                    this.properties[field]!!.type.isCompatibleWith(other.properties[field]!!.type)
                }
            }
            this is Union && other is Union -> {
                // Simplified - real implementation would be more sophisticated
                this.types.size == other.types.size
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
    DATETIME;   // Date and time with timezone
    
    /**
     * Check if this kind is numeric
     */
    fun isNumeric(): Boolean = this == INTEGER || this == NUMBER
    
    /**
     * Check if this kind is temporal
     */
    fun isTemporal(): Boolean = this == DATE || this == DATETIME
    
    /**
     * Get compatible JSON type
     */
    fun toJsonType(): String = when (this) {
        STRING -> "string"
        INTEGER -> "integer"
        NUMBER -> "number"
        BOOLEAN -> "boolean"
        NULL -> "null"
        DATE, DATETIME -> "string"
    }
}

/**
 * Constraint on a scalar type
 */
data class Constraint(
    val kind: ConstraintKind,
    val value: Any
) {
    override fun toString(): String = "$kind($value)"
}

/**
 * Types of constraints
 */
enum class ConstraintKind {
    MIN_LENGTH,     // Minimum string length
    MAX_LENGTH,     // Maximum string length
    PATTERN,        // Regular expression pattern
    MINIMUM,        // Minimum numeric value (inclusive)
    MAXIMUM,        // Maximum numeric value (inclusive)
    ENUM;           // Enumeration of allowed values
    
    /**
     * Check if this constraint applies to strings
     */
    fun isStringConstraint(): Boolean = this in setOf(MIN_LENGTH, MAX_LENGTH, PATTERN, ENUM)
    
    /**
     * Check if this constraint applies to numbers
     */
    fun isNumericConstraint(): Boolean = this in setOf(MINIMUM, MAXIMUM, ENUM)
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
            minLength?.let { constraints.add(Constraint(ConstraintKind.MIN_LENGTH, it)) }
            maxLength?.let { constraints.add(Constraint(ConstraintKind.MAX_LENGTH, it)) }
            pattern?.let { constraints.add(Constraint(ConstraintKind.PATTERN, it)) }
            enum?.let { constraints.add(Constraint(ConstraintKind.ENUM, it)) }
            return TypeDefinition.Scalar(ScalarKind.STRING, constraints)
        }
        
        fun integer(
            min: Int? = null,
            max: Int? = null,
            enum: List<Int>? = null
        ): TypeDefinition.Scalar {
            val constraints = mutableListOf<Constraint>()
            min?.let { constraints.add(Constraint(ConstraintKind.MINIMUM, it.toDouble())) }
            max?.let { constraints.add(Constraint(ConstraintKind.MAXIMUM, it.toDouble())) }
            enum?.let { constraints.add(Constraint(ConstraintKind.ENUM, it.map { it.toString() })) }
            return TypeDefinition.Scalar(ScalarKind.INTEGER, constraints)
        }
        
        fun number(
            min: Double? = null,
            max: Double? = null
        ): TypeDefinition.Scalar {
            val constraints = mutableListOf<Constraint>()
            min?.let { constraints.add(Constraint(ConstraintKind.MINIMUM, it)) }
            max?.let { constraints.add(Constraint(ConstraintKind.MAXIMUM, it)) }
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
