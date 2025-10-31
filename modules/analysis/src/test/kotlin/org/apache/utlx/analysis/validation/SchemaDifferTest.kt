// modules/analysis/src/test/kotlin/org/apache/utlx/analysis/validation/SchemaDifferTest.kt
package org.apache.utlx.analysis.validation

import org.apache.utlx.analysis.types.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * Schema comparison results
 */
data class SchemaDiff(
    val changes: List<SchemaChange>,
    val hasBreakingChanges: Boolean
) {
    fun isCompatible() = !hasBreakingChanges
}

sealed class SchemaChange {
    data class PropertyAdded(val path: String, val type: TypeDefinition) : SchemaChange()
    data class PropertyRemoved(val path: String) : SchemaChange()
    data class PropertyTypeChanged(val path: String, val oldType: TypeDefinition, val newType: TypeDefinition) : SchemaChange()
    data class PropertyMadeRequired(val path: String) : SchemaChange()
    data class PropertyMadeOptional(val path: String) : SchemaChange()
    data class ConstraintAdded(val path: String, val constraint: Constraint) : SchemaChange()
    data class ConstraintRemoved(val path: String, val constraint: Constraint) : SchemaChange()
}

/**
 * Mock Schema Differ for testing
 */
class SchemaDiffer {
    fun diff(oldSchema: TypeDefinition, newSchema: TypeDefinition): SchemaDiff {
        val changes = mutableListOf<SchemaChange>()
        val breaking = mutableListOf<Boolean>()
        
        diffTypes(oldSchema, newSchema, "", changes, breaking)
        
        return SchemaDiff(changes, breaking.any { it })
    }
    
    private fun diffTypes(
        oldType: TypeDefinition,
        newType: TypeDefinition,
        path: String,
        changes: MutableList<SchemaChange>,
        breaking: MutableList<Boolean>
    ) {
        when {
            oldType is TypeDefinition.Object && newType is TypeDefinition.Object -> {
                diffObjects(oldType, newType, path, changes, breaking)
            }
            oldType is TypeDefinition.Scalar && newType is TypeDefinition.Scalar -> {
                diffScalars(oldType, newType, path, changes, breaking)
            }
            oldType != newType -> {
                changes.add(SchemaChange.PropertyTypeChanged(path, oldType, newType))
                breaking.add(true)
            }
        }
    }
    
    private fun diffObjects(
        oldObj: TypeDefinition.Object,
        newObj: TypeDefinition.Object,
        path: String,
        changes: MutableList<SchemaChange>,
        breaking: MutableList<Boolean>
    ) {
        // Check for removed properties
        for (oldProp in oldObj.properties.keys) {
            if (!newObj.properties.containsKey(oldProp)) {
                changes.add(SchemaChange.PropertyRemoved("$path.$oldProp"))
                if (oldObj.required.contains(oldProp)) {
                    breaking.add(true)
                }
            }
        }
        
        // Check for added properties
        for (newProp in newObj.properties.keys) {
            if (!oldObj.properties.containsKey(newProp)) {
                changes.add(SchemaChange.PropertyAdded("$path.$newProp", newObj.properties[newProp]!!.type))
                if (newObj.required.contains(newProp)) {
                    breaking.add(true) // Adding required property is breaking
                }
            }
        }
        
        // Check for changed properties
        for ((propName, oldPropType) in oldObj.properties) {
            newObj.properties[propName]?.let { newPropType ->
                val propPath = "$path.$propName"
                
                // Check if required status changed
                if (oldObj.required.contains(propName) != newObj.required.contains(propName)) {
                    if (newObj.required.contains(propName)) {
                        changes.add(SchemaChange.PropertyMadeRequired(propPath))
                        breaking.add(true)
                    } else {
                        changes.add(SchemaChange.PropertyMadeOptional(propPath))
                    }
                }
                
                // Recursively check type changes
                diffTypes(oldPropType.type, newPropType.type, propPath, changes, breaking)
            }
        }
    }
    
    private fun diffScalars(
        oldScalar: TypeDefinition.Scalar,
        newScalar: TypeDefinition.Scalar,
        path: String,
        changes: MutableList<SchemaChange>,
        breaking: MutableList<Boolean>
    ) {
        // Check for removed constraints (relaxation - not breaking)
        for (oldConstraint in oldScalar.constraints) {
            if (!newScalar.constraints.contains(oldConstraint)) {
                changes.add(SchemaChange.ConstraintRemoved(path, oldConstraint))
            }
        }
        
        // Check for added constraints (restriction - breaking)
        for (newConstraint in newScalar.constraints) {
            if (!oldScalar.constraints.contains(newConstraint)) {
                changes.add(SchemaChange.ConstraintAdded(path, newConstraint))
                breaking.add(true)
            }
        }
    }
}

@Disabled("TODO: Requires constraint API implementation")
class SchemaDifferTest {
    
    private val differ = SchemaDiffer()
    
    @Test
    fun `should detect no changes for identical schemas`() {
        val schema = TypeDefinition.Scalar(ScalarKind.STRING)
        
        val diff = differ.diff(schema, schema)
        
        assertTrue(diff.changes.isEmpty())
        assertFalse(diff.hasBreakingChanges)
    }
    
    @Test
    fun `should detect property addition as non-breaking`() {
        val oldSchema = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            )
        )
        val newSchema = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER), nullable = true)
            )
        )
        
        val diff = differ.diff(oldSchema, newSchema)
        
        assertEquals(1, diff.changes.size)
        assertTrue(diff.changes[0] is SchemaChange.PropertyAdded)
        assertFalse(diff.hasBreakingChanges)
    }
    
    @Test
    fun `should detect required property addition as breaking`() {
        val oldSchema = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            ),
            required = setOf("name")
        )
        val newSchema = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER))
            ),
            required = setOf("name", "age")
        )
        
        val diff = differ.diff(oldSchema, newSchema)
        
        assertTrue(diff.hasBreakingChanges)
    }
    
    @Test
    fun `should detect property removal as potentially breaking`() {
        val oldSchema = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER))
            ),
            required = setOf("name", "age")
        )
        val newSchema = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            ),
            required = setOf("name")
        )
        
        val diff = differ.diff(oldSchema, newSchema)
        
        assertTrue(diff.changes.any { it is SchemaChange.PropertyRemoved })
        assertTrue(diff.hasBreakingChanges)
    }
    
    @Test
    fun `should detect property type change as breaking`() {
        val oldSchema = TypeDefinition.Object(
            properties = mapOf(
                "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            )
        )
        val newSchema = TypeDefinition.Object(
            properties = mapOf(
                "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER))
            )
        )
        
        val diff = differ.diff(oldSchema, newSchema)
        
        assertTrue(diff.changes.any { it is SchemaChange.PropertyTypeChanged })
        assertTrue(diff.hasBreakingChanges)
    }
    
    @Test
    fun `should detect property made required as breaking`() {
        val oldSchema = TypeDefinition.Object(
            properties = mapOf(
                "email" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            ),
            required = setOf()
        )
        val newSchema = TypeDefinition.Object(
            properties = mapOf(
                "email" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            ),
            required = setOf("email")
        )
        
        val diff = differ.diff(oldSchema, newSchema)
        
        assertTrue(diff.changes.any { it is SchemaChange.PropertyMadeRequired })
        assertTrue(diff.hasBreakingChanges)
    }
    
    @Test
    fun `should detect property made optional as non-breaking`() {
        val oldSchema = TypeDefinition.Object(
            properties = mapOf(
                "email" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            ),
            required = setOf("email")
        )
        val newSchema = TypeDefinition.Object(
            properties = mapOf(
                "email" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            ),
            required = setOf()
        )
        
        val diff = differ.diff(oldSchema, newSchema)
        
        assertTrue(diff.changes.any { it is SchemaChange.PropertyMadeOptional })
        assertFalse(diff.hasBreakingChanges)
    }
    
    @Test
    fun `should detect constraint addition as breaking`() {
        val oldSchema = TypeDefinition.Scalar(ScalarKind.STRING)
        val newSchema = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.MinLength(5))
        )
        
        val diff = differ.diff(oldSchema, newSchema)
        
        assertTrue(diff.changes.any { it is SchemaChange.ConstraintAdded })
        assertTrue(diff.hasBreakingChanges)
    }
    
    @Test
    fun `should detect constraint removal as non-breaking`() {
        val oldSchema = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.MinLength(5))
        )
        val newSchema = TypeDefinition.Scalar(ScalarKind.STRING)
        
        val diff = differ.diff(oldSchema, newSchema)
        
        assertTrue(diff.changes.any { it is SchemaChange.ConstraintRemoved })
        assertFalse(diff.hasBreakingChanges)
    }
    
    @Test
    fun `should detect multiple changes`() {
        val oldSchema = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "old" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            ),
            required = setOf("name")
        )
        val newSchema = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER))
            ),
            required = setOf("name", "age")
        )
        
        val diff = differ.diff(oldSchema, newSchema)
        
        assertTrue(diff.changes.size >= 2)
    }
    
    @Test
    fun `should track nested property changes`() {
        val oldSchema = TypeDefinition.Object(
            properties = mapOf(
                "address" to PropertyType(
                    TypeDefinition.Object(
                        properties = mapOf(
                            "street" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
                        )
                    )
                )
            )
        )
        val newSchema = TypeDefinition.Object(
            properties = mapOf(
                "address" to PropertyType(
                    TypeDefinition.Object(
                        properties = mapOf(
                            "street" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                            "city" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
                        )
                    )
                )
            )
        )
        
        val diff = differ.diff(oldSchema, newSchema)
        
        assertTrue(diff.changes.isNotEmpty())
    }
    
    @Test
    fun `should provide compatibility check method`() {
        val oldSchema = TypeDefinition.Scalar(ScalarKind.STRING)
        val newSchema = TypeDefinition.Scalar(ScalarKind.STRING)
        
        val diff = differ.diff(oldSchema, newSchema)
        
        assertTrue(diff.isCompatible())
    }
}
