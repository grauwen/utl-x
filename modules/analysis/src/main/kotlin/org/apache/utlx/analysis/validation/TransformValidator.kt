// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/validation/TransformValidator.kt
package org.apache.utlx.analysis.validation

import org.apache.utlx.core.ast.Program
import org.apache.utlx.analysis.types.TypeDefinition
import org.apache.utlx.analysis.types.AdvancedTypeInference
import org.apache.utlx.analysis.schema.ValidationResult

/**
 * Validates UTL-X transformations against expected output schemas
 * 
 * Performs type checking and compatibility analysis to ensure
 * transformations produce outputs that conform to expected schemas.
 */
class TransformValidator {
    
    private val typeInference = AdvancedTypeInference()
    
    /**
     * Validate transformation against expected output type
     */
    fun validate(
        transformation: Program,
        inputType: TypeDefinition,
        expectedOutputType: TypeDefinition
    ): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            // Infer actual output type from transformation
            val actualOutputType = typeInference.inferOutputType(transformation, inputType)
            
            // Compare actual vs expected
            val compatible = actualOutputType.isCompatibleWith(expectedOutputType)
            
            if (!compatible) {
                errors.add("Output type mismatch: expected ${expectedOutputType}, but transformation produces ${actualOutputType}")
            }
            
            // Additional validation checks
            validateTypeConstraints(actualOutputType, expectedOutputType, errors, warnings)
            
        } catch (e: Exception) {
            errors.add("Type inference failed: ${e.message}")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validate type constraints in detail
     */
    private fun validateTypeConstraints(
        actual: TypeDefinition,
        expected: TypeDefinition,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        when {
            actual is TypeDefinition.Object && expected is TypeDefinition.Object -> {
                validateObjectConstraints(actual, expected, errors, warnings)
            }
            actual is TypeDefinition.Array && expected is TypeDefinition.Array -> {
                validateArrayConstraints(actual, expected, errors, warnings)
            }
            actual is TypeDefinition.Scalar && expected is TypeDefinition.Scalar -> {
                validateScalarConstraints(actual, expected, errors, warnings)
            }
            actual is TypeDefinition.Any -> {
                warnings.add("Output type is Any - cannot verify detailed constraints")
            }
        }
    }
    
    /**
     * Validate object type constraints
     */
    private fun validateObjectConstraints(
        actual: TypeDefinition.Object,
        expected: TypeDefinition.Object,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        // Check required fields
        expected.required.forEach { requiredField ->
            if (!actual.properties.containsKey(requiredField)) {
                errors.add("Missing required field: $requiredField")
            } else if (!actual.required.contains(requiredField)) {
                warnings.add("Field '$requiredField' is required in expected schema but optional in actual")
            }
        }
        
        // Check field types
        expected.properties.forEach { (fieldName, expectedProp) ->
            val actualProp = actual.properties[fieldName]
            if (actualProp == null) {
                if (fieldName in expected.required) {
                    errors.add("Missing required field: $fieldName")
                }
            } else {
                if (!actualProp.type.isCompatibleWith(expectedProp.type)) {
                    errors.add("Field '$fieldName' type mismatch: expected ${expectedProp.type}, got ${actualProp.type}")
                }
                
                if (expectedProp.nullable && !actualProp.nullable) {
                    warnings.add("Field '$fieldName' should be nullable but isn't")
                }
            }
        }
        
        // Check for extra fields
        actual.properties.keys.subtract(expected.properties.keys).forEach { extraField ->
            if (!expected.additionalProperties) {
                warnings.add("Extra field '$extraField' not allowed by schema")
            }
        }
    }
    
    /**
     * Validate array type constraints
     */
    private fun validateArrayConstraints(
        actual: TypeDefinition.Array,
        expected: TypeDefinition.Array,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        // Check element type compatibility
        if (!actual.elementType.isCompatibleWith(expected.elementType)) {
            errors.add("Array element type mismatch: expected ${expected.elementType}, got ${actual.elementType}")
        }
        
        // Check min/max items
        expected.minItems?.let { expectedMin ->
            actual.maxItems?.let { actualMax ->
                if (actualMax < expectedMin) {
                    errors.add("Array may have fewer items ($actualMax) than required minimum ($expectedMin)")
                }
            }
        }
        
        expected.maxItems?.let { expectedMax ->
            actual.minItems?.let { actualMin ->
                if (actualMin > expectedMax) {
                    errors.add("Array may have more items ($actualMin) than allowed maximum ($expectedMax)")
                }
            }
        }
    }
    
    /**
     * Validate scalar type constraints
     */
    private fun validateScalarConstraints(
        actual: TypeDefinition.Scalar,
        expected: TypeDefinition.Scalar,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        // Check scalar kind compatibility
        if (actual.kind != expected.kind) {
            errors.add("Scalar type mismatch: expected ${expected.kind}, got ${actual.kind}")
            return
        }
        
        // Check constraints
        expected.constraints.forEach { expectedConstraint ->
            val actualConstraint = actual.constraints.find { it.kind == expectedConstraint.kind }
            
            if (actualConstraint == null) {
                warnings.add("Missing constraint: ${expectedConstraint.kind}")
            } else {
                // Could add more detailed constraint validation here
                when (expectedConstraint.kind) {
                    org.apache.utlx.analysis.types.ConstraintKind.MIN_LENGTH,
                    org.apache.utlx.analysis.types.ConstraintKind.MAX_LENGTH,
                    org.apache.utlx.analysis.types.ConstraintKind.MINIMUM,
                    org.apache.utlx.analysis.types.ConstraintKind.MAXIMUM -> {
                        // Could check if actual constraint is more/less restrictive
                    }
                    org.apache.utlx.analysis.types.ConstraintKind.PATTERN -> {
                        // Could validate pattern compatibility
                    }
                    org.apache.utlx.analysis.types.ConstraintKind.ENUM -> {
                        // Could check if actual enum is subset of expected
                    }
                }
            }
        }
    }
}