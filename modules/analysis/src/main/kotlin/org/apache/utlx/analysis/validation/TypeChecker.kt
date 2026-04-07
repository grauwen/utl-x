// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/validation/TypeChecker.kt
package org.apache.utlx.analysis.validation

import org.apache.utlx.analysis.types.*

/**
 * Type checker for validating values against type definitions with constraints
 *
 * Provides runtime validation of values to ensure they conform to
 * specified type definitions and their associated constraints.
 */
object TypeChecker {

    /**
     * Check if a value satisfies all constraints of a type definition
     *
     * @param value The value to validate
     * @param type The type definition with constraints to check against
     * @return List of error messages (empty if valid)
     */
    fun checkConstraints(value: Any?, type: TypeDefinition): List<String> {
        val errors = mutableListOf<String>()

        // Check null values
        if (value == null) {
            if (!type.isNullable) {
                errors.add("Value is null but type is not nullable")
            }
            return errors
        }

        // Validate based on type
        when (type) {
            is TypeDefinition.Scalar -> checkScalarConstraints(value, type, errors)
            is TypeDefinition.Array -> checkArrayConstraints(value, type, errors)
            is TypeDefinition.Object -> checkObjectConstraints(value, type, errors)
            is TypeDefinition.Union -> checkUnionConstraints(value, type, errors)
            is TypeDefinition.Any -> { /* Any type accepts everything */ }
            is TypeDefinition.Unknown -> { /* Unknown type - no validation */ }
            is TypeDefinition.Never -> errors.add("Value cannot be of type Never")
        }

        return errors
    }

    /**
     * Check scalar type constraints
     */
    private fun checkScalarConstraints(
        value: Any,
        type: TypeDefinition.Scalar,
        errors: MutableList<String>
    ) {
        // Check basic type match
        val actualKind = getScalarKind(value)
        if (actualKind != type.kind && !isCompatibleScalarKind(actualKind, type.kind)) {
            errors.add("Type mismatch: expected ${type.kind}, got $actualKind")
            return
        }

        // Check each constraint
        type.constraints.forEach { constraint ->
            when (constraint) {
                is Constraint.MinLength -> {
                    if (value is String && value.length < constraint.value) {
                        errors.add("String length ${value.length} is less than minimum ${constraint.value}")
                    }
                }
                is Constraint.MaxLength -> {
                    if (value is String && value.length > constraint.value) {
                        errors.add("String length ${value.length} exceeds maximum ${constraint.value}")
                    }
                }
                is Constraint.Pattern -> {
                    if (value is String && !value.matches(Regex(constraint.regex))) {
                        errors.add("String does not match pattern ${constraint.regex}")
                    }
                }
                is Constraint.Minimum -> {
                    val numValue = when (value) {
                        is Number -> value.toDouble()
                        else -> null
                    }
                    if (numValue != null && numValue < constraint.value) {
                        errors.add("Number $numValue is less than minimum ${constraint.value}")
                    }
                }
                is Constraint.Maximum -> {
                    val numValue = when (value) {
                        is Number -> value.toDouble()
                        else -> null
                    }
                    if (numValue != null && numValue > constraint.value) {
                        errors.add("Number $numValue exceeds maximum ${constraint.value}")
                    }
                }
                is Constraint.Enum -> {
                    if (!constraint.values.contains(value)) {
                        errors.add("Value must be one of: ${constraint.values}")
                    }
                }
                is Constraint.Custom -> {
                    // Custom constraints would need custom validation logic
                    // For now, we skip them
                }
            }
        }
    }

    /**
     * Check array type constraints
     */
    private fun checkArrayConstraints(
        value: Any,
        type: TypeDefinition.Array,
        errors: MutableList<String>
    ) {
        if (value !is List<*>) {
            errors.add("Value is not an array/list")
            return
        }

        // Check array size constraints
        type.minItems?.let { min ->
            if (value.size < min) {
                errors.add("Array has ${value.size} items, less than minimum $min")
            }
        }

        type.maxItems?.let { max ->
            if (value.size > max) {
                errors.add("Array has ${value.size} items, exceeds maximum $max")
            }
        }

        // Check each element against element type
        value.forEachIndexed { index, element ->
            val elementErrors = checkConstraints(element, type.elementType)
            elementErrors.forEach { error ->
                errors.add("Element $index: $error")
            }
        }
    }

    /**
     * Check object type constraints
     */
    private fun checkObjectConstraints(
        value: Any,
        type: TypeDefinition.Object,
        errors: MutableList<String>
    ) {
        if (value !is Map<*, *>) {
            errors.add("Value is not an object/map")
            return
        }

        // Check required properties
        type.required.forEach { requiredProp ->
            if (!value.containsKey(requiredProp)) {
                errors.add("Missing required property: $requiredProp")
            }
        }

        // Check property types
        type.properties.forEach { (propName, propType) ->
            val propValue = value[propName]
            if (propValue != null || value.containsKey(propName)) {
                val propErrors = checkConstraints(propValue, propType.effectiveType)
                propErrors.forEach { error ->
                    errors.add("Property '$propName': $error")
                }
            }
        }

        // Check for additional properties
        if (!type.additionalProperties) {
            val extraProps = value.keys.filterNot { type.properties.containsKey(it.toString()) }
            if (extraProps.isNotEmpty()) {
                errors.add("Additional properties not allowed: $extraProps")
            }
        }
    }

    /**
     * Check union type constraints (value must match at least one type)
     */
    private fun checkUnionConstraints(
        value: Any,
        type: TypeDefinition.Union,
        errors: MutableList<String>
    ) {
        // Try each type in the union
        val allErrors = mutableMapOf<String, List<String>>()
        var hasMatch = false

        for ((index, memberType) in type.types.withIndex()) {
            val memberErrors = checkConstraints(value, memberType)
            if (memberErrors.isEmpty()) {
                hasMatch = true
                break
            }
            allErrors["Option ${index + 1} (${formatType(memberType)})"] = memberErrors
        }

        if (!hasMatch) {
            errors.add("Value does not match any type in union:")
            allErrors.forEach { (typeName, typeErrors) ->
                errors.add("  $typeName: ${typeErrors.joinToString(", ")}")
            }
        }
    }

    /**
     * Get the scalar kind for a runtime value
     */
    private fun getScalarKind(value: Any): ScalarKind {
        return when (value) {
            is String -> ScalarKind.STRING
            is Int, is Long, is Short, is Byte -> ScalarKind.INTEGER
            is Double, is Float -> ScalarKind.NUMBER
            is Boolean -> ScalarKind.BOOLEAN
            else -> ScalarKind.STRING // Default fallback
        }
    }

    /**
     * Check if two scalar kinds are compatible
     */
    private fun isCompatibleScalarKind(actual: ScalarKind, expected: ScalarKind): Boolean {
        return when {
            actual == expected -> true
            // INTEGER can be used where NUMBER is expected
            actual == ScalarKind.INTEGER && expected == ScalarKind.NUMBER -> true
            // Everything can be converted to STRING
            expected == ScalarKind.STRING -> true
            else -> false
        }
    }

    /**
     * Format a type for error messages
     */
    private fun formatType(type: TypeDefinition): String = when (type) {
        is TypeDefinition.Scalar -> type.kind.name
        is TypeDefinition.Array -> "Array<${formatType(type.elementType)}>"
        is TypeDefinition.Object -> "Object"
        is TypeDefinition.Union -> type.types.joinToString(" | ") { formatType(it) }
        is TypeDefinition.Any -> "Any"
        is TypeDefinition.Unknown -> "Unknown"
        is TypeDefinition.Never -> "Never"
    }
}
