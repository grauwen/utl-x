package org.apache.utlx.core.interpreter

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StandardLibraryIntegrationTest {
    
    @Test
    fun `standard library should register basic functions correctly`() {
        val stdLib = StandardLibraryImpl()
        val env = Environment()
        
        // Register all functions
        stdLib.registerAll(env)

        // Verify some key functions are registered by checking environment
        assertNotNull(env.get("upper"), "upper function should be registered")
        assertNotNull(env.get("lower"), "lower function should be registered")
        assertNotNull(env.get("sum"), "sum function should be registered")
        assertNotNull(env.get("abs"), "abs function should be registered")
    }
    
    @Test
    fun `basic string functions should work`() {
        val stdLib = StandardLibraryImpl()
        val env = Environment()
        stdLib.registerAll(env)

        // Test upper function
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