// modules/analysis/src/test/kotlin/org/apache/utlx/analysis/validation/SchemaValidatorTest.kt
package org.apache.utlx.analysis.validation

import org.apache.utlx.analysis.types.*
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class SchemaValidatorTest {
    
    @Test
    fun `should validate string value against string type`() {
        val value = "hello"
        val type = TypeDefinition.Scalar(ScalarKind.STRING)
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun `should validate string with minLength constraint`() {
        val value = "hello"
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.MinLength(3))
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun `should reject string shorter than minLength`() {
        val value = "hi"
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.MinLength(5))
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertFalse(errors.isEmpty())
        assertTrue(errors[0].contains("less than minimum"))
    }
    
    @Test
    fun `should validate string with maxLength constraint`() {
        val value = "hello"
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.MaxLength(10))
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun `should reject string longer than maxLength`() {
        val value = "this is a very long string"
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.MaxLength(10))
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertFalse(errors.isEmpty())
        assertTrue(errors[0].contains("exceeds maximum"))
    }
    
    @Test
    fun `should validate string matching pattern`() {
        val value = "12345"
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.Pattern("\\d{5}"))
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun `should reject string not matching pattern`() {
        val value = "abc"
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.Pattern("\\d{5}"))
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertFalse(errors.isEmpty())
        assertTrue(errors[0].contains("does not match pattern"))
    }
    
    @Test
    fun `should validate string in enum`() {
        val value = "active"
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.Enum(listOf("active", "inactive", "pending")))
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun `should reject string not in enum`() {
        val value = "deleted"
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(Constraint.Enum(listOf("active", "inactive", "pending")))
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertFalse(errors.isEmpty())
        assertTrue(errors[0].contains("must be one of"))
    }
    
    @Test
    fun `should validate number above minimum`() {
        val value = 50
        val type = TypeDefinition.Scalar(
            ScalarKind.INTEGER,
            listOf(Constraint.Minimum(0.0))
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun `should reject number below minimum`() {
        val value = -10
        val type = TypeDefinition.Scalar(
            ScalarKind.INTEGER,
            listOf(Constraint.Minimum(0.0))
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertFalse(errors.isEmpty())
        assertTrue(errors[0].contains("less than minimum"))
    }
    
    @Test
    fun `should validate number below maximum`() {
        val value = 50
        val type = TypeDefinition.Scalar(
            ScalarKind.INTEGER,
            listOf(Constraint.Maximum(100.0))
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun `should reject number above maximum`() {
        val value = 150
        val type = TypeDefinition.Scalar(
            ScalarKind.INTEGER,
            listOf(Constraint.Maximum(100.0))
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertFalse(errors.isEmpty())
        assertTrue(errors[0].contains("exceeds maximum"))
    }
    
    @Test
    fun `should validate number within range`() {
        val value = 50
        val type = TypeDefinition.Scalar(
            ScalarKind.INTEGER,
            listOf(
                Constraint.Minimum(0.0),
                Constraint.Maximum(100.0)
            )
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun `should validate multiple constraints simultaneously`() {
        val value = "hello"
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(
                Constraint.MinLength(3),
                Constraint.MaxLength(10),
                Constraint.Pattern("[a-z]+")
            )
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun `should collect multiple constraint violations`() {
        val value = "A"
        val type = TypeDefinition.Scalar(
            ScalarKind.STRING,
            listOf(
                Constraint.MinLength(5),
                Constraint.Pattern("[0-9]+")
            )
        )
        
        val errors = TypeChecker.checkConstraints(value, type)
        
        assertEquals(2, errors.size)
    }
}
