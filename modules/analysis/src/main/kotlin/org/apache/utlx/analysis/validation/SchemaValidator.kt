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

// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/validation/SchemaDiffer.kt
package org.apache.utlx.analysis.validation

import org.apache.utlx.analysis.types.*

/**
 * Compares two schemas and identifies differences
 * 
 * Categorizes changes as:
 * - Breaking changes (incompatible with existing data)
 * - Non-breaking additions (new optional fields)
 * - Removals (removed fields/constraints)
 * - Modifications (changed types/constraints)
 */
class SchemaDiffer {
    
    /**
     * Compare two schemas and return differences
     */
    fun diff(oldSchema: TypeDefinition, newSchema: TypeDefinition): SchemaDiff {
        val breakingChanges = mutableListOf<String>()
        val additions = mutableListOf<String>()
        val removals = mutableListOf<String>()
        val modifications = mutableListOf<String>()
        
        compareTypes(oldSchema, newSchema, "root", breakingChanges, additions, removals, modifications)
        
        return SchemaDiff(
            breakingChanges = breakingChanges,
            additions = additions,
            removals = removals,
            modifications = modifications
        )
    }
    
    /**
     * Compare two type definitions
     */
    private fun compareTypes(
        oldType: TypeDefinition,
        newType: TypeDefinition,
        path: String,
        breaking: MutableList<String>,
        additions: MutableList<String>,
        removals: MutableList<String>,
        modifications: MutableList<String>
    ) {
        // Different type classes is always breaking
        if (oldType::class != newType::class) {
            breaking.add("Type changed at '$path': ${oldType::class.simpleName} → ${newType::class.simpleName}")
            return
        }
        
        when (oldType) {
            is TypeDefinition.Scalar -> {
                compareScalars(oldType, newType as TypeDefinition.Scalar, path, breaking, additions, removals, modifications)
            }
            is TypeDefinition.Array -> {
                compareArrays(oldType, newType as TypeDefinition.Array, path, breaking, additions, removals, modifications)
            }
            is TypeDefinition.Object -> {
                compareObjects(oldType, newType as TypeDefinition.Object, path, breaking, additions, removals, modifications)
            }
            is TypeDefinition.Union -> {
                compareUnions(oldType, newType as TypeDefinition.Union, path, breaking, additions, removals, modifications)
            }
            is TypeDefinition.Any -> {} // No meaningful comparison
        }
    }
    
    /**
     * Compare scalar types
     */
    private fun compareScalars(
        old: TypeDefinition.Scalar,
        new: TypeDefinition.Scalar,
        path: String,
        breaking: MutableList<String>,
        additions: MutableList<String>,
        removals: MutableList<String>,
        modifications: MutableList<String>
    ) {
        // Different scalar kind is breaking
        if (old.kind != new.kind) {
            breaking.add("Scalar kind changed at '$path': ${old.kind} → ${new.kind}")
            return
        }
        
        // Compare constraints
        val oldConstraints = old.constraints.groupBy { it.kind }
        val newConstraints = new.constraints.groupBy { it.kind }
        
        // Added constraints (may be breaking if more restrictive)
        newConstraints.keys.subtract(oldConstraints.keys).forEach { constraintKind ->
            when (constraintKind) {
                ConstraintKind.MIN_LENGTH, ConstraintKind.MINIMUM -> 
                    breaking.add("Added more restrictive constraint at '$path': $constraintKind")
                ConstraintKind.MAX_LENGTH, ConstraintKind.MAXIMUM -> 
                    breaking.add("Added more restrictive constraint at '$path': $constraintKind")
                ConstraintKind.PATTERN -> 
                    breaking.add("Added pattern constraint at '$path'")
                ConstraintKind.ENUM -> 
                    breaking.add("Added enum constraint at '$path' (restricts allowed values)")
            }
        }
        
        // Removed constraints (may be non-breaking relaxation)
        oldConstraints.keys.subtract(newConstraints.keys).forEach { constraintKind ->
            removals.add("Removed constraint at '$path': $constraintKind")
        }
        
        // Modified constraints
        oldConstraints.keys.intersect(newConstraints.keys).forEach { constraintKind ->
            val oldValue = oldConstraints[constraintKind]!!.first().value
            val newValue = newConstraints[constraintKind]!!.first().value
            
            if (oldValue != newValue) {
                when (constraintKind) {
                    ConstraintKind.MIN_LENGTH -> {
                        if ((newValue as Int) > (oldValue as Int)) {
                            breaking.add("Increased minLength at '$path': $oldValue → $newValue")
                        } else {
                            modifications.add("Decreased minLength at '$path': $oldValue → $newValue")
                        }
                    }
                    ConstraintKind.MAX_LENGTH -> {
                        if ((newValue as Int) < (oldValue as Int)) {
                            breaking.add("Decreased maxLength at '$path': $oldValue → $newValue")
                        } else {
                            modifications.add("Increased maxLength at '$path': $oldValue → $newValue")
                        }
                    }
                    ConstraintKind.MINIMUM -> {
                        if ((newValue as Double) > (oldValue as Double)) {
                            breaking.add("Increased minimum at '$path': $oldValue → $newValue")
                        } else {
                            modifications.add("Decreased minimum at '$path': $oldValue → $newValue")
                        }
                    }
                    ConstraintKind.MAXIMUM -> {
                        if ((newValue as Double) < (oldValue as Double)) {
                            breaking.add("Decreased maximum at '$path': $oldValue → $newValue")
                        } else {
                            modifications.add("Increased maximum at '$path': $oldValue → $newValue")
                        }
                    }
                    else -> {
                        modifications.add("Modified constraint at '$path': $constraintKind")
                    }
                }
            }
        }
    }
    
    /**
     * Compare array types
     */
    private fun compareArrays(
        old: TypeDefinition.Array,
        new: TypeDefinition.Array,
        path: String,
        breaking: MutableList<String>,
        additions: MutableList<String>,
        removals: MutableList<String>,
        modifications: MutableList<String>
    ) {
        // Compare element types
        compareTypes(old.elementType, new.elementType, "$path[]", breaking, additions, removals, modifications)
        
        // Compare min/max items
        if (old.minItems != new.minItems) {
            if (new.minItems != null && (old.minItems == null || new.minItems > old.minItems)) {
                breaking.add("Increased minItems at '$path': ${old.minItems} → ${new.minItems}")
            } else {
                modifications.add("Changed minItems at '$path': ${old.minItems} → ${new.minItems}")
            }
        }
        
        if (old.maxItems != new.maxItems) {
            if (new.maxItems != null && (old.maxItems == null || new.maxItems < old.maxItems)) {
                breaking.add("Decreased maxItems at '$path': ${old.maxItems} → ${new.maxItems}")
            } else {
                modifications.add("Changed maxItems at '$path': ${old.maxItems} → ${new.maxItems}")
            }
        }
    }
    
    /**
     * Compare object types
     */
    private fun compareObjects(
        old: TypeDefinition.Object,
        new: TypeDefinition.Object,
        path: String,
        breaking: MutableList<String>,
        additions: MutableList<String>,
        removals: MutableList<String>,
        modifications: MutableList<String>
    ) {
        // Check for removed fields
        old.properties.keys.subtract(new.properties.keys).forEach { removedField ->
            if (removedField in old.required) {
                breaking.add("Removed required field: '$path.$removedField'")
            } else {
                removals.add("Removed optional field: '$path.$removedField'")
            }
        }
        
        // Check for added fields
        new.properties.keys.subtract(old.properties.keys).forEach { addedField ->
            if (addedField in new.required) {
                breaking.add("Added required field: '$path.$addedField'")
            } else {
                additions.add("Added optional field: '$path.$addedField'")
            }
        }
        
        // Check for modified fields
        old.properties.keys.intersect(new.properties.keys).forEach { fieldName ->
            val oldProp = old.properties[fieldName]!!
            val newProp = new.properties[fieldName]!!
            
            // Check if required status changed
            val wasRequired = fieldName in old.required
            val isRequired = fieldName in new.required
            
            if (!wasRequired && isRequired) {
                breaking.add("Field '$path.$fieldName' is now required")
            } else if (wasRequired && !isRequired) {
                modifications.add("Field '$path.$fieldName' is now optional")
            }
            
            // Check if nullability changed
            if (!oldProp.nullable && newProp.nullable) {
                modifications.add("Field '$path.$fieldName' is now nullable")
            } else if (oldProp.nullable && !newProp.nullable) {
                breaking.add("Field '$path.$fieldName' is no longer nullable")
            }
            
            // Recursively compare types
            compareTypes(oldProp.type, newProp.type, "$path.$fieldName", breaking, additions, removals, modifications)
        }
        
        // Check additionalProperties
        if (old.additionalProperties && !new.additionalProperties) {
            breaking.add("AdditionalProperties disabled at '$path'")
        } else if (!old.additionalProperties && new.additionalProperties) {
            modifications.add("AdditionalProperties enabled at '$path'")
        }
    }
    
    /**
     * Compare union types
     */
    private fun compareUnions(
        old: TypeDefinition.Union,
        new: TypeDefinition.Union,
        path: String,
        breaking: MutableList<String>,
        additions: MutableList<String>,
        removals: MutableList<String>,
        modifications: MutableList<String>
    ) {
        // Simplified comparison - could be more sophisticated
        if (old.types.size != new.types.size) {
            modifications.add("Union type count changed at '$path': ${old.types.size} → ${new.types.size}")
        }
    }
}

/**
 * Result of schema comparison
 */
data class SchemaDiff(
    val breakingChanges: List<String>,
    val additions: List<String>,
    val removals: List<String>,
    val modifications: List<String>
) {
    fun hasBreakingChanges(): Boolean = breakingChanges.isNotEmpty()
    fun hasAnyChanges(): Boolean = breakingChanges.isNotEmpty() || 
        additions.isNotEmpty() || removals.isNotEmpty() || modifications.isNotEmpty()
    
    override fun toString(): String = buildString {
        if (!hasAnyChanges()) {
            appendLine("No changes detected")
            return@buildString
        }
        
        if (breakingChanges.isNotEmpty()) {
            appendLine("Breaking Changes:")
            breakingChanges.forEach { appendLine("  ✗ $it") }
            appendLine()
        }
        
        if (removals.isNotEmpty()) {
            appendLine("Removals:")
            removals.forEach { appendLine("  - $it") }
            appendLine()
        }
        
        if (additions.isNotEmpty()) {
            appendLine("Additions:")
            additions.forEach { appendLine("  + $it") }
            appendLine()
        }
        
        if (modifications.isNotEmpty()) {
            appendLine("Modifications:")
            modifications.forEach { appendLine("  ~ $it") }
        }
    }
}
