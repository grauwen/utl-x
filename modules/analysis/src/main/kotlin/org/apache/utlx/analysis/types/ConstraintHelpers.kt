// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/types/ConstraintHelpers.kt
package org.apache.utlx.analysis.types

/**
 * Helper objects for creating constraints more easily in tests
 */

object MinLength {
    operator fun invoke(value: Int): Constraint = Constraint(ConstraintKind.MIN_LENGTH, value)
}

object MaxLength {
    operator fun invoke(value: Int): Constraint = Constraint(ConstraintKind.MAX_LENGTH, value)
}

object Pattern {
    operator fun invoke(regex: String): Constraint = Constraint(ConstraintKind.PATTERN, regex)
    val regex: String get() = throw UnsupportedOperationException("Use Pattern(regex) to create")
}

object Minimum {
    operator fun invoke(value: Number): Constraint = Constraint(ConstraintKind.MINIMUM, value.toDouble())
}

object Maximum {
    operator fun invoke(value: Number): Constraint = Constraint(ConstraintKind.MAXIMUM, value.toDouble())
}

object Enum {
    operator fun invoke(values: List<Any>): Constraint = Constraint(ConstraintKind.ENUM, values)
    val values: List<Any> get() = throw UnsupportedOperationException("Use Enum(values) to create")
}

/**
 * Extension property to check if a PropertyType is nullable
 */
val PropertyType.isNullable: Boolean
    get() = this.nullable

/**
 * Extension property to get non-nullable version of a PropertyType
 */
val PropertyType.nonNullable: PropertyType
    get() = copy(nullable = false)

/**
 * Extension property to get the effective type (unwrapping nullable if needed)
 */
val PropertyType.effectiveType: TypeDefinition
    get() = type
