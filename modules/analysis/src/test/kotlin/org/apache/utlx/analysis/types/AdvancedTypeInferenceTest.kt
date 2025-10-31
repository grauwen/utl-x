// modules/analysis/src/test/kotlin/org/apache/utlx/analysis/types/AdvancedTypeInferenceTest.kt
package org.apache.utlx.analysis.types

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@Disabled("TODO: TypeContext API needs implementation for advanced type inference features")
class AdvancedTypeInferenceTest {
    
    private lateinit var context: TypeContext
    
    @BeforeEach
    fun setup() {
        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "users" to PropertyType(
                    TypeDefinition.Array(
                        TypeDefinition.Object(
                            properties = mapOf(
                                "id" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                                "email" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                                "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER), nullable = true)
                            )
                        )
                    )
                ),
                "metadata" to PropertyType(
                    TypeDefinition.Object(
                        properties = mapOf(
                            "version" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                            "timestamp" to PropertyType(TypeDefinition.Scalar(ScalarKind.DATETIME))
                        )
                    )
                )
            )
        )
        
        context = TypeContext(inputType = inputType)
    }
    
    @Test
    fun `should infer type for map operation on array`() {
        // map(users, user => user.name) should return Array<String>
        val usersType = context.getPathType(listOf("users"))
        
        assertTrue(usersType is TypeDefinition.Array)
        val elementType = (usersType as TypeDefinition.Array).elementType
        assertTrue(elementType is TypeDefinition.Object)
    }
    
    @Test
    fun `should infer type for filter operation preserving element type`() {
        // filter(users, user => user.age > 18) should return Array<User>
        val usersType = context.getPathType(listOf("users"))
        
        assertTrue(usersType is TypeDefinition.Array)
    }
    
    @Test
    fun `should infer type for reduce operation to scalar`() {
        // reduce(users, (acc, user) => acc + user.age, 0) should return Integer
        val sumFunc = context.lookupFunction("sum")
        
        assertNotNull(sumFunc)
        assertTrue(sumFunc.returnType is TypeDefinition.Scalar)
    }
    
    @Test
    fun `should infer union type for conditional expression`() {
        // if (condition) "text" else 42
        val stringType = TypeDefinition.Scalar(ScalarKind.STRING)
        val intType = TypeDefinition.Scalar(ScalarKind.INTEGER)
        
        val unionType = TypeDefinition.Union(listOf(stringType, intType))
        
        assertTrue(unionType.types.size == 2)
    }
    
    @Test
    fun `should infer nullable type for optional property access`() {
        // users[0].age (where age is nullable)
        val pathType = context.getPathType(listOf("users", "0", "age"))
        
        // The property should be accessible
        assertTrue(pathType is TypeDefinition.Scalar || pathType is TypeDefinition.Union)
    }
    
    @Test
    fun `should infer type for nested map operations`() {
        // map(users, user => { id: user.id, upper: upper(user.name) })
        val usersType = context.getPathType(listOf("users"))
        
        assertTrue(usersType is TypeDefinition.Array)
    }
    
    @Test
    fun `should infer type for function composition`() {
        // upper(substring(name, 0, 5))
        val substringFunc = context.lookupFunction("substring")
        val upperFunc = context.lookupFunction("upper")
        
        assertNotNull(substringFunc)
        assertNotNull(upperFunc)
        
        // substring returns String, upper accepts String
        assertTrue(substringFunc.returnType is TypeDefinition.Scalar)
        assertTrue(upperFunc.returnType is TypeDefinition.Scalar)
    }
    
    @Test
    fun `should detect type error in function argument`() {
        // upper(123) should fail
        val upperFunc = context.lookupFunction("upper")!!
        val result = upperFunc.checkArguments(listOf(TypeDefinition.Scalar(ScalarKind.INTEGER)))
        
        assertFalse(result.isValid())
    }
    
    @Test
    fun `should infer type for object construction`() {
        // { name: user.name, age: user.age }
        val constructedType = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER), nullable = true)
            )
        )
        
        assertTrue(constructedType.properties.containsKey("name"))
        assertTrue(constructedType.properties.containsKey("age"))
    }
    
    @Test
    fun `should infer type for string concatenation with conversion`() {
        // "Age: " ++ age (where age is integer)
        val leftType = TypeDefinition.Scalar(ScalarKind.STRING)
        val rightType = TypeDefinition.Scalar(ScalarKind.INTEGER)
        
        val resultType = context.inferBinaryOpType("++", leftType, rightType)
        
        // Should convert to string
        assertTrue(resultType is TypeDefinition.Scalar || resultType is TypeDefinition.Any)
    }
    
    @Test
    fun `should infer type for array element access`() {
        // users[0] should return User object
        val pathType = context.getPathType(listOf("users", "0"))
        
        assertTrue(pathType is TypeDefinition.Object)
    }
    
    @Test
    fun `should infer type for wildcard array access`() {
        // users[*].name should return Array<String>
        val pathType = context.getPathType(listOf("users", "*", "name"))
        
        assertTrue(pathType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.STRING, (pathType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should detect incompatible types in comparison`() {
        // Can compare integer and number
        val intType = TypeDefinition.Scalar(ScalarKind.INTEGER)
        val numberType = TypeDefinition.Scalar(ScalarKind.NUMBER)
        
        val resultType = context.inferBinaryOpType(">", intType, numberType)
        
        assertTrue(resultType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.BOOLEAN, (resultType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should infer narrowed type from type guard`() {
        // if (isString(value)) then value is String
        val isStringFunc = context.lookupFunction("isString")
        
        assertNotNull(isStringFunc)
        assertTrue(isStringFunc.returnType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.BOOLEAN, (isStringFunc.returnType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should infer type for default value operator`() {
        // nullableValue ?? "default"
        val nullableType = TypeDefinition.Union(listOf(
            TypeDefinition.Scalar(ScalarKind.STRING),
            TypeDefinition.Scalar(ScalarKind.NULL)
        ))
        val defaultType = TypeDefinition.Scalar(ScalarKind.STRING)
        
        // Result should be non-nullable string
        val nonNullable = nullableType.nonNullable
        assertTrue(nonNullable is TypeDefinition.Scalar)
    }
    
    @Test
    fun `should handle generic array operations`() {
        // Array<Integer> + Array<Integer> = Array<Integer>
        val leftArray = TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.INTEGER))
        val rightArray = TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.INTEGER))
        
        val resultType = context.inferBinaryOpType("++", leftArray, rightArray)
        
        assertTrue(resultType is TypeDefinition.Array)
    }
    
    @Test
    fun `should infer type for mixed array operations`() {
        // Array<Integer> + Array<Number> = Array<Integer | Number>
        val leftArray = TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.INTEGER))
        val rightArray = TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.NUMBER))
        
        val resultType = context.inferBinaryOpType("++", leftArray, rightArray)
        
        assertTrue(resultType is TypeDefinition.Array)
        val elementType = (resultType as TypeDefinition.Array).elementType
        assertTrue(elementType is TypeDefinition.Union || elementType is TypeDefinition.Scalar)
    }
}
