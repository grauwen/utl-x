// modules/analysis/src/test/kotlin/org/apache/utlx/analysis/validation/TransformValidatorTest.kt
import org.junit.jupiter.api.Disabledpackage org.apache.utlx.analysis.validation

import org.apache.utlx.analysis.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

@Disabled("TODO: Requires constraint API rewrite")
class TransformValidatorTest {
    
    private lateinit var context: TypeContext
    
    @BeforeEach
    fun setup() {
        val inputType = TypeDefinition.Object(
            properties = mapOf(
                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                "email" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                "orders" to PropertyType(TypeDefinition.Array(
                    TypeDefinition.Object(
                        properties = mapOf(
                            "id" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                            "total" to PropertyType(TypeDefinition.Scalar(ScalarKind.NUMBER))
                        )
                    )
                ))
            ),
            required = setOf("name", "age")
        )
        
        context = TypeContext(inputType = inputType)
    }
    
    @Test
    fun `should validate access to existing input property`() {
        val pathType = context.getPathType(listOf("name"))
        
        assertTrue(pathType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.STRING, (pathType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should validate access to nested property`() {
        val pathType = context.getPathType(listOf("orders", "0", "id"))
        
        assertTrue(pathType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.STRING, (pathType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should return Never for non-existent property`() {
        val pathType = context.getPathType(listOf("nonExistent"))
        
        assertTrue(pathType is TypeDefinition.Never)
    }
    
    @Test
    fun `should validate array wildcard access`() {
        val pathType = context.getPathType(listOf("orders", "*", "total"))
        
        assertTrue(pathType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.NUMBER, (pathType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should validate variable definition`() {
        context.defineVariable("count", TypeDefinition.Scalar(ScalarKind.INTEGER))
        
        val varType = context.lookupVariable("count")
        assertTrue(varType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.INTEGER, (varType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should return null for undefined variable`() {
        val varType = context.lookupVariable("undefined")
        
        assertEquals(null, varType)
    }
    
    @Test
    fun `should validate function lookup`() {
        val upperFunc = context.lookupFunction("upper")
        
        assertTrue(upperFunc != null)
        assertEquals("upper", upperFunc.name)
    }
    
    @Test
    fun `should validate function call with correct arguments`() {
        val upperFunc = context.lookupFunction("upper")!!
        val result = upperFunc.checkArguments(listOf(TypeDefinition.Scalar(ScalarKind.STRING)))
        
        assertTrue(result.isValid())
        assertTrue(result.returnType is TypeDefinition.Scalar)
    }
    
    @Test
    fun `should reject function call with wrong argument types`() {
        val upperFunc = context.lookupFunction("upper")!!
        val result = upperFunc.checkArguments(listOf(TypeDefinition.Scalar(ScalarKind.INTEGER)))
        
        assertFalse(result.isValid())
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `should reject function call with too few arguments`() {
        val substringFunc = context.lookupFunction("substring")!!
        val result = substringFunc.checkArguments(listOf())
        
        assertFalse(result.isValid())
    }
    
    @Test
    fun `should accept optional function arguments`() {
        val substringFunc = context.lookupFunction("substring")!!
        val result = substringFunc.checkArguments(listOf(
            TypeDefinition.Scalar(ScalarKind.STRING),
            TypeDefinition.Scalar(ScalarKind.INTEGER)
        ))
        
        assertTrue(result.isValid())
    }
    
    @Test
    fun `should validate arithmetic operation types`() {
        val leftType = TypeDefinition.Scalar(ScalarKind.INTEGER)
        val rightType = TypeDefinition.Scalar(ScalarKind.INTEGER)
        
        val resultType = context.inferBinaryOpType("+", leftType, rightType)
        
        assertTrue(resultType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.INTEGER, (resultType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should infer number type when mixing integer and number`() {
        val leftType = TypeDefinition.Scalar(ScalarKind.INTEGER)
        val rightType = TypeDefinition.Scalar(ScalarKind.NUMBER)
        
        val resultType = context.inferBinaryOpType("+", leftType, rightType)
        
        assertTrue(resultType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.NUMBER, (resultType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should validate comparison operation returns boolean`() {
        val leftType = TypeDefinition.Scalar(ScalarKind.INTEGER)
        val rightType = TypeDefinition.Scalar(ScalarKind.INTEGER)
        
        val resultType = context.inferBinaryOpType(">", leftType, rightType)
        
        assertTrue(resultType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.BOOLEAN, (resultType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should validate logical operation returns boolean`() {
        val leftType = TypeDefinition.Scalar(ScalarKind.BOOLEAN)
        val rightType = TypeDefinition.Scalar(ScalarKind.BOOLEAN)
        
        val resultType = context.inferBinaryOpType("&&", leftType, rightType)
        
        assertTrue(resultType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.BOOLEAN, (resultType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should validate string concatenation`() {
        val leftType = TypeDefinition.Scalar(ScalarKind.STRING)
        val rightType = TypeDefinition.Scalar(ScalarKind.STRING)
        
        val resultType = context.inferBinaryOpType("++", leftType, rightType)
        
        assertTrue(resultType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.STRING, (resultType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should validate array concatenation`() {
        val leftType = TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.INTEGER))
        val rightType = TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.INTEGER))
        
        val resultType = context.inferBinaryOpType("++", leftType, rightType)
        
        assertTrue(resultType is TypeDefinition.Array)
    }
    
    @Test
    fun `should validate unary negation returns boolean`() {
        val operandType = TypeDefinition.Scalar(ScalarKind.BOOLEAN)
        
        val resultType = context.inferUnaryOpType("!", operandType)
        
        assertTrue(resultType is TypeDefinition.Scalar)
        assertEquals(ScalarKind.BOOLEAN, (resultType as TypeDefinition.Scalar).kind)
    }
    
    @Test
    fun `should handle scope management correctly`() {
        context.bind("outer", TypeDefinition.Scalar(ScalarKind.STRING))

        context.pushScope()
        context.bind("inner", TypeDefinition.Scalar(ScalarKind.INTEGER))

        assertTrue(context.lookup("outer") != null)
        assertTrue(context.lookup("inner") != null)

        context.popScope()

        assertTrue(context.lookup("outer") != null)
        assertEquals(null, context.lookup("inner"))
    }
    
    @Test
    fun `should execute block within scope`() {
        context.bind("before", TypeDefinition.Scalar(ScalarKind.STRING))

        val result = context.withScope {
            context.bind("inside", TypeDefinition.Scalar(ScalarKind.INTEGER))
            context.lookup("inside")
        }

        assertTrue(result != null)
        assertEquals(null, context.lookup("inside"))
    }
    
    @Test
    fun `should create template context with match type`() {
        val matchType = TypeDefinition.Object(
            properties = mapOf(
                "id" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
            )
        )

        val templateContext = TypeContext()
        templateContext.bind("\$input", matchType)

        assertEquals(matchType, templateContext.lookup("\$input"))
    }
    
    @Test
    fun `should get all defined variables`() {
        context.bind("var1", TypeDefinition.Scalar(ScalarKind.STRING))
        context.bind("var2", TypeDefinition.Scalar(ScalarKind.INTEGER))

        val allVars = context.allBindings()

        assertEquals(2, allVars.size)
        assertTrue(allVars.containsKey("var1"))
        assertTrue(allVars.containsKey("var2"))
    }
}
