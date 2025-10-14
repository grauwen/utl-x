// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/validation/SchemaValidator.kt
package org.apache.utlx.analysis.validation

import org.apache.utlx.analysis.types.*
import org.apache.utlx.core.udm.*

/**
 * Validator for data against schemas
 * 
 * Validates actual data (UDM) against type definitions to ensure:
 * - Data conforms to schema structure
 * - Required fields are present
 * - Types match expected types
 * - Constraints are satisfied
 */
class SchemaValidator {
    
    /**
     * Validate UDM data against a type definition
     */
    fun validate(data: UDM, schema: TypeDefinition): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        validateValue(data, schema, "root", errors, warnings)
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validate a single value
     */
    private fun validateValue(
        value: UDM,
        expectedType: TypeDefinition,
        path: String,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        when (expectedType) {
            is TypeDefinition.Scalar -> validateScalar(value, expectedType, path, errors, warnings)
            is TypeDefinition.Array -> validateArray(value, expectedType, path, errors, warnings)
            is TypeDefinition.Object -> validateObject(value, expectedType, path, errors, warnings)
            is TypeDefinition.Union -> validateUnion(value, expectedType, path, errors, warnings)
            is TypeDefinition.Any -> {} // Any type accepts anything
        }
    }
    
    /**
     * Validate scalar value
     */
    private fun validateScalar(
        value: UDM,
        expected: TypeDefinition.Scalar,
        path: String,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        // Check type matches
        val isValidType = when (expected.kind) {
            ScalarKind.STRING -> value is UDMString
            ScalarKind.INTEGER -> value is UDMNumber && value.value % 1.0 == 0.0
            ScalarKind.NUMBER -> value is UDMNumber
            ScalarKind.BOOLEAN -> value is UDMBoolean
            ScalarKind.NULL -> value is UDMNull
            ScalarKind.DATE, ScalarKind.DATETIME -> value is UDMDate
        }
        
        if (!isValidType) {
            errors.add("Type mismatch at '$path': expected ${expected.kind}, got ${value::class.simpleName}")
            return
        }
        
        // Check constraints
        expected.constraints.forEach { constraint ->
            validateConstraint(value, constraint, path, errors, warnings)
        }
    }
    
    /**
     * Validate constraint
     */
    private fun validateConstraint(
        value: UDM,
        constraint: Constraint,
        path: String,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        when (constraint.kind) {
            ConstraintKind.MIN_LENGTH -> {
                if (value is UDMString) {
                    val minLength = constraint.value as Int
                    if (value.value.length < minLength) {
                        errors.add("String at '$path' is too short: ${value.value.length} < $minLength")
                    }
                }
            }
            
            ConstraintKind.MAX_LENGTH -> {
                if (value is UDMString) {
                    val maxLength = constraint.value as Int
                    if (value.value.length > maxLength) {
                        errors.add("String at '$path' is too long: ${value.value.length} > $maxLength")
                    }
                }
            }
            
            ConstraintKind.PATTERN -> {
                if (value is UDMString) {
                    val pattern = Regex(constraint.value as String)
                    if (!pattern.matches(value.value)) {
                        errors.add("String at '$path' does not match pattern: ${constraint.value}")
                    }
                }
            }
            
            ConstraintKind.MINIMUM -> {
                if (value is UDMNumber) {
                    val minimum = constraint.value as Double
                    if (value.value < minimum) {
                        errors.add("Number at '$path' is too small: ${value.value} < $minimum")
                    }
                }
            }
            
            ConstraintKind.MAXIMUM -> {
                if (value is UDMNumber) {
                    val maximum = constraint.value as Double
                    if (value.value > maximum) {
                        errors.add("Number at '$path' is too large: ${value.value} > $maximum")
                    }
                }
            }
            
            ConstraintKind.ENUM -> {
                @Suppress("UNCHECKED_CAST")
                val allowedValues = constraint.value as List<String>
                val actualValue = when (value) {
                    is UDMString -> value.value
                    is UDMNumber -> value.value.toString()
                    is UDMBoolean -> value.value.toString()
                    else -> null
                }
                
                if (actualValue != null && actualValue !in allowedValues) {
                    errors.add("Value at '$path' is not in allowed set: $actualValue not in $allowedValues")
                }
            }
        }
    }
    
    /**
     * Validate array value
     */
    private fun validateArray(
        value: UDM,
        expected: TypeDefinition.Array,
        path: String,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        if (value !is UDMArray) {
            errors.add("Type mismatch at '$path': expected array, got ${value::class.simpleName}")
            return
        }
        
        // Check min/max items
        expected.minItems?.let { minItems ->
            if (value.elements.size < minItems) {
                errors.add("Array at '$path' has too few items: ${value.elements.size} < $minItems")
            }
        }
        
        expected.maxItems?.let { maxItems ->
            if (value.elements.size > maxItems) {
                errors.add("Array at '$path' has too many items: ${value.elements.size} > $maxItems")
            }
        }
        
        // Validate each element
        value.elements.forEachIndexed { index, element ->
            validateValue(element, expected.elementType, "$path[$index]", errors, warnings)
        }
    }
    
    /**
     * Validate object value
     */
    private fun validateObject(
        value: UDM,
        expected: TypeDefinition.Object,
        path: String,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        if (value !is UDMObject) {
            errors.add("Type mismatch at '$path': expected object, got ${value::class.simpleName}")
            return
        }
        
        // Check required fields
        expected.required.forEach { requiredField ->
            if (!value.properties.containsKey(requiredField)) {
                errors.add("Missing required field: '$path.$requiredField'")
            }
        }
        
        // Validate each property
        expected.properties.forEach { (propertyName, propertyType) ->
            val propertyValue = value.properties[propertyName]
            
            if (propertyValue != null) {
                validateValue(propertyValue, propertyType.type, "$path.$propertyName", errors, warnings)
            } else if (propertyName in expected.required) {
                // Already reported as missing required field
            } else {
                // Optional field not present - this is okay
            }
        }
        
        // Check for unexpected fields if additionalProperties is false
        if (!expected.additionalProperties) {
            value.properties.keys.forEach { actualField ->
                if (!expected.properties.containsKey(actualField)) {
                    warnings.add("Unexpected field: '$path.$actualField' (not in schema)")
                }
            }
        }
    }
    
    /**
     * Validate union value
     */
    private fun validateUnion(
        value: UDM,
        expected: TypeDefinition.Union,
        path: String,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        // Value must match at least one of the union types
        val matchingTypes = expected.types.filter { type ->
            val tempErrors = mutableListOf<String>()
            val tempWarnings = mutableListOf<String>()
            validateValue(value, type, path, tempErrors, tempWarnings)
            tempErrors.isEmpty()
        }
        
        if (matchingTypes.isEmpty()) {
            errors.add(
                "Value at '$path' does not match any of the union types: " +
                expected.types.joinToString(", ") { it::class.simpleName ?: "Unknown" }
            )
        }
    }
}
