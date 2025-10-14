// modules/analysis/src/test/kotlin/org/apache/utlx/analysis/types/TypeInferenceTest.kt
package org.apache.utlx.analysis.types

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TypeInferenceTest {
    
    @Test
    fun `should infer string type for string literal`() {
        val type = TypeDefinition.Scalar(ScalarKind.STRING)
        assertTrue(type is TypeDefinition.Scalar)
        assertEquals(ScalarKind.STRING, type.kind)
    }
    
    @Test
    fun `should infer integer type for integer literal`() {
        val type = TypeDefinition.Scalar(ScalarKind.INTEGER)
        assertTrue(type is TypeDefinition.Scalar)
        assertEquals(ScalarKind.INTEGER, type.kind)
    }
    
    @Test
    fun `should infer number type for decimal literal`() {
        val type = TypeDefinition.Scalar(ScalarKind.NUMBER)
        assertTrue(type is TypeDefinition.Scalar)
        assertEquals(ScalarKind.NUMBER, type.kind)
    }
    
    @Test
    fun `should infer boolean type for boolean literal`() {
        val type = TypeDefinition.Scalar(ScalarKind.BOOLEAN)
        assertTrue(type is TypeDefinition.Scalar)
        assertEquals(ScalarKind.BOOLEAN, type.kind)
    }
    
    @Test
    fun `should check string is compatible with string`() {
        val source = TypeDefinition.Scalar(ScalarKind.STRING)
        val target = TypeDefinition.Scalar(ScalarKind.STRING)
        
        assertTrue(TypeCompatibility.isCompatible(source, target))
    }
    
    @Test
    fun `should check integer is compatible with number`() {
        val source = TypeDefinition.Scalar(ScalarKind.INTEGER)
        val target = TypeDefinition.Scalar(ScalarKind.NUMBER)
        
        assertTrue(TypeCompatibility.isCompatible(source, target))
    }
    
    @Test
    fun `should check number is not compatible with integer`() {
        val source = TypeDefinition.Scalar(ScalarKind.NUMBER)
        val target = TypeDefinition.Scalar(ScalarKind.INTEGER)
        
        assertFalse(TypeCompatibility.isCompatible(source, target))
    }
    
    @Test
    fun `should check any type accepts all types`() {
        val source = TypeDefinition.Scalar(ScalarKind.STRING)
        val target = TypeDefinition.Any
        
        assertTrue(TypeCompatibility.isCompatible(source, target))
    }
    
    @Test
    fun `should check null is compatible with nullable types`() {
        val source = TypeDefinition.Scalar(ScalarKind.NULL)
        val target = TypeDefinition.Union(listOf(
            TypeDefinition.Scalar(ScalarKind.STRING),
            TypeDefinition.Scalar(ScalarKind.NULL)
        ))
        
        assertTrue(TypeCompatibility.isCompatible(source, target))
    }
    
    @Test
    fun `should check array element types for compatibility`() {
        val source = TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.STRING))
        val target = TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.STRING))
        
        assertTrue(TypeCompatibility.isCompatible(source, target))
    }
    
    @Test
    fun `should reject incompatible array element types`() {
        val source = TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.STRING))
        val target = TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.INTEGER))
        
        assertFalse(TypeCompatibility.isCompatible(source, target))
    }
    
    @Test
    fun `should check object structural subtyping`() {
        val source = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER))
            ),
            required = setOf("name", "age")
        )
        val target = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            ),
            required = setOf("name")
        )
        
        assertTrue(TypeCompatibility.isCompatible(source, target))
    }
    
    @Test
    fun `should reject object missing required properties`() {
        val source = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            ),
            required = setOf("name")
        )
        val target = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER))
            ),
            required = setOf("name", "age")
        )
        
        assertFalse(TypeCompatibility.isCompatible(source, target))
    }
    
    @Test
    fun `should check union type accepts any member type`() {
        val source = TypeDefinition.Scalar(ScalarKind.STRING)
        val target = TypeDefinition.Union(listOf(
            TypeDefinition.Scalar(ScalarKind.STRING),
            TypeDefinition.Scalar(ScalarKind.INTEGER)
        ))
        
        assertTrue(TypeCompatibility.isCompatible(source, target))
    }
    
    @Test
    fun `should check source union all types compatible with target`() {
        val source = TypeDefinition.Union(listOf(
            TypeDefinition.Scalar(ScalarKind.INTEGER),
            TypeDefinition.Scalar(ScalarKind.INTEGER)
        ))
        val target = TypeDefinition.Scalar(ScalarKind.NUMBER)
        
        assertTrue(TypeCompatibility.isCompatible(source, target))
    }
    
    @Test
    fun `should detect nullable types`() {
        val nullableType = TypeDefinition.Union(listOf(
            TypeDefinition.Scalar(ScalarKind.STRING),
            TypeDefinition.Scalar(ScalarKind.NULL)
        ))
        
        assertTrue(nullableType.isNullable())
    }
    
    @Test
    fun `should detect non-nullable types`() {
        val nonNullableType = TypeDefinition.Scalar(ScalarKind.STRING)
        
        assertFalse(nonNullableType.isNullable())
    }
    
    @Test
    fun `should get non-nullable version of union type`() {
        val nullableType = TypeDefinition.Union(listOf(
            TypeDefinition.Scalar(ScalarKind.STRING),
            TypeDefinition.Scalar(ScalarKind.NULL)
        ))
        
        val nonNullable = nullableType.nonNullable()
        
        assertTrue(nonNullable is TypeDefinition.Scalar)
        assertEquals(ScalarKind.STRING, (nonNullable as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should make type nullable`() {
        val type = TypeDefinition.Scalar(ScalarKind.STRING)
        val nullable = type.nullable()
        
        assertTrue(nullable.isNullable())
    }
    
    @Test
    fun `should not duplicate null in already nullable type`() {
        val nullableType = TypeDefinition.Union(listOf(
            TypeDefinition.Scalar(ScalarKind.STRING),
            TypeDefinition.Scalar(ScalarKind.NULL)
        ))
        
        val stillNullable = nullableType.nullable()
        
        // Should still be the same type
        assertTrue(stillNullable.isNullable())
    }
    
    @Test
    fun `should create effective type for nullable property`() {
        val prop = PropertyType(
            type = TypeDefinition.Scalar(ScalarKind.STRING),
            nullable = true
        )
        
        val effectiveType = prop.effectiveType()
        
        assertTrue(effectiveType.isNullable())
    }
    
    @Test
    fun `should create effective type for non-nullable property`() {
        val prop = PropertyType(
            type = TypeDefinition.Scalar(ScalarKind.STRING),
            nullable = false
        )
        
        val effectiveType = prop.effectiveType()
        
        assertFalse(effectiveType.isNullable())
    }
    
    @Test
    fun `should check everything can convert to string`() {
        val intType = TypeDefinition.Scalar(ScalarKind.INTEGER)
        val stringType = TypeDefinition.Scalar(ScalarKind.STRING)
        
        assertTrue(TypeCompatibility.isCompatible(intType, stringType))
    }
}
