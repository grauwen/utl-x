package org.apache.utlx.core.interpreter

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandardLibraryIntegrationTest {
    
    @Test
    fun `standard library registry should register functions correctly`() {
        val registry = StandardLibraryRegistry()
        val env = Environment()
        
        // Register all functions
        registry.registerAll(env)
        
        // Verify some key functions are registered
        val expectedFunctions = listOf(
            "upper", "lower", "trim", "length", "substring",
            "sum", "avg", "min", "max", "count",
            "abs", "ceil", "floor", "round", "sqrt",
            "keys", "values", "entries", "merge",
            "typeOf", "isEmpty", "isNull", "isArray"
        )
        
        expectedFunctions.forEach { funcName ->
            assertTrue(env.has(funcName), "Function '$funcName' should be registered")
        }
    }
    
    @Test
    fun `registry should provide function metadata`() {
        val registry = StandardLibraryRegistry()
        
        // Check that functions have proper metadata
        val upperFunc = registry.getFunctionInfo("upper")
        assertEquals("upper", upperFunc?.name)
        assertEquals("string", upperFunc?.module)
        assertTrue(upperFunc?.description?.contains("uppercase") == true)
        
        val sumFunc = registry.getFunctionInfo("sum")
        assertEquals("sum", sumFunc?.name)
        assertEquals("array", sumFunc?.module)
    }
    
    @Test
    fun `registry should group functions by module`() {
        val registry = StandardLibraryRegistry()
        
        // Get string functions
        val stringFunctions = registry.getFunctionsByModule("string")
        assertTrue(stringFunctions.any { it.name == "upper" })
        assertTrue(stringFunctions.any { it.name == "lower" })
        assertTrue(stringFunctions.any { it.name == "trim" })
        
        // Get math functions
        val mathFunctions = registry.getFunctionsByModule("math")
        assertTrue(mathFunctions.any { it.name == "abs" })
        assertTrue(mathFunctions.any { it.name == "sqrt" })
        
        // Get array functions
        val arrayFunctions = registry.getFunctionsByModule("array")
        assertTrue(arrayFunctions.any { it.name == "sum" })
        assertTrue(arrayFunctions.any { it.name == "count" })
    }
    
    @Test
    fun `interpreter should execute stdlib functions`() {
        val interpreter = Interpreter()
        
        // Test string function
        val stringResult = testFunctionCall(interpreter, "upper", listOf(UDM.Scalar("hello")))
        assertEquals("HELLO", (stringResult as RuntimeValue.StringValue).value)
        
        // Test array function
        val arrayInput = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        val sumResult = testFunctionCall(interpreter, "sum", listOf(arrayInput))
        assertEquals(6.0, (sumResult as RuntimeValue.NumberValue).value)
        
        // Test math function
        val mathResult = testFunctionCall(interpreter, "abs", listOf(UDM.Scalar(-5)))
        assertEquals(5.0, (mathResult as RuntimeValue.NumberValue).value)
    }
    
    @Test
    fun `interpreter should handle function errors gracefully`() {
        val interpreter = Interpreter()
        
        try {
            // Try to call non-existent function
            testFunctionCall(interpreter, "nonExistentFunction", listOf(UDM.Scalar("test")))
            assertTrue(false, "Should have thrown an error for non-existent function")
        } catch (e: RuntimeError) {
            assertTrue(e.message?.contains("Undefined variable") == true)
        }
    }
    
    private fun testFunctionCall(interpreter: Interpreter, functionName: String, args: List<UDM>): RuntimeValue {
        val env = Environment()
        val registry = StandardLibraryRegistry()
        registry.registerAll(env)
        
        // Create function call expression
        val funcExpr = org.apache.utlx.core.ast.Expression.Identifier(
            functionName, 
            org.apache.utlx.core.ast.Location(0, 0)
        )
        
        val argExprs = args.map { udm ->
            when (udm) {
                is UDM.Scalar -> when (val value = udm.value) {
                    is String -> org.apache.utlx.core.ast.Expression.StringLiteral(
                        value, 
                        org.apache.utlx.core.ast.Location(0, 0)
                    )
                    is Number -> org.apache.utlx.core.ast.Expression.NumberLiteral(
                        value.toDouble(), 
                        org.apache.utlx.core.ast.Location(0, 0)
                    )
                    is Boolean -> org.apache.utlx.core.ast.Expression.BooleanLiteral(
                        value, 
                        org.apache.utlx.core.ast.Location(0, 0)
                    )
                    null -> org.apache.utlx.core.ast.Expression.NullLiteral(
                        org.apache.utlx.core.ast.Location(0, 0)
                    )
                    else -> org.apache.utlx.core.ast.Expression.StringLiteral(
                        value.toString(), 
                        org.apache.utlx.core.ast.Location(0, 0)
                    )
                }
                is UDM.Array -> {
                    // For simplicity, create array literal with the UDM data
                    val elements = udm.elements.map { element ->
                        when (element) {
                            is UDM.Scalar -> when (val v = element.value) {
                                is Number -> org.apache.utlx.core.ast.Expression.NumberLiteral(
                                    v.toDouble(), 
                                    org.apache.utlx.core.ast.Location(0, 0)
                                )
                                else -> org.apache.utlx.core.ast.Expression.StringLiteral(
                                    v.toString(), 
                                    org.apache.utlx.core.ast.Location(0, 0)
                                )
                            }
                            else -> org.apache.utlx.core.ast.Expression.NullLiteral(
                                org.apache.utlx.core.ast.Location(0, 0)
                            )
                        }
                    }
                    org.apache.utlx.core.ast.Expression.ArrayLiteral(
                        elements, 
                        org.apache.utlx.core.ast.Location(0, 0)
                    )
                }
                else -> org.apache.utlx.core.ast.Expression.NullLiteral(
                    org.apache.utlx.core.ast.Location(0, 0)
                )
            }
        }
        
        val callExpr = org.apache.utlx.core.ast.Expression.FunctionCall(
            funcExpr,
            argExprs,
            org.apache.utlx.core.ast.Location(0, 0)
        )
        
        return interpreter.evaluate(callExpr, env)
    }
}