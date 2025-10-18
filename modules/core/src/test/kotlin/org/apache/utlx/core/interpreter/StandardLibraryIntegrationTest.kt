package org.apache.utlx.core.interpreter

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandardLibraryIntegrationTest {
    
    @Test
    fun `standard library should register basic functions correctly`() {
        val stdLib = StandardLibraryImpl()
        val env = Environment()
        
        // Register all functions
        stdLib.registerAll(env)
        
        // Verify some key functions are registered by checking environment
        assertTrue(env.get("upper") != null, "upper function should be registered")
        assertTrue(env.get("lower") != null, "lower function should be registered")
        assertTrue(env.get("sum") != null, "sum function should be registered")
        assertTrue(env.get("abs") != null, "abs function should be registered")
    }
    
    @Test
    fun `basic string functions should work`() {
        val stdLib = StandardLibraryImpl()
        val env = Environment()
        stdLib.registerAll(env)
        
        // Test upper function
        val upperFunc = env.get("upper") as RuntimeValue.FunctionValue
        val upperResult = StandardLibraryImpl.nativeFunctions["upper"]!!(
            listOf(RuntimeValue.StringValue("hello"))
        )
        assertEquals("HELLO", (upperResult as RuntimeValue.StringValue).value)
        
        // Test lower function  
        val lowerResult = StandardLibraryImpl.nativeFunctions["lower"]!!(
            listOf(RuntimeValue.StringValue("WORLD"))
        )
        assertEquals("world", (lowerResult as RuntimeValue.StringValue).value)
    }
    
    @Test
    fun `basic math functions should work`() {
        val stdLib = StandardLibraryImpl()
        val env = Environment()
        stdLib.registerAll(env)
        
        // Test abs function
        val absResult = StandardLibraryImpl.nativeFunctions["abs"]!!(
            listOf(RuntimeValue.NumberValue(-42.0))
        )
        assertEquals(42.0, (absResult as RuntimeValue.NumberValue).value)
        
        // Test sum function
        val sumResult = StandardLibraryImpl.nativeFunctions["sum"]!!(
            listOf(RuntimeValue.ArrayValue(listOf(
                RuntimeValue.NumberValue(1.0),
                RuntimeValue.NumberValue(2.0),
                RuntimeValue.NumberValue(3.0)
            )))
        )
        assertEquals(6.0, (sumResult as RuntimeValue.NumberValue).value)
    }
}