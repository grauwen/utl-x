// modules/analysis/src/main/kotlin/com/glomidco/utlx/analysis/types/ConstraintHelpers.kt
package com.glomidco.utlx.analysis.types

/**
 * Helper file for constraint-related extension properties
 *
 * Note: Constraint factory objects are no longer needed since Constraint is now
 * a sealed class hierarchy. Use Constraint.MinLength(value), Constraint.Pattern(regex), etc.
 */

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
