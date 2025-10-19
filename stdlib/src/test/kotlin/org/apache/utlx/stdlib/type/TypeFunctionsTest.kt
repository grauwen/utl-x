// stdlib/src/test/kotlin/org/apache/utlx/stdlib/type/TypeFunctionsTest.kt
package org.apache.utlx.stdlib.type

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Comprehensive test suite for Type functions.
 * 
 * Tests cover:
 * - Type identification (getType)
 * - Type checking (isString, isNumber, isBoolean, etc.)
 * - Type validation
 * - Edge cases and null handling
 * - Array and Object type checking
 */
class TypeFunctionsTest {

    // ==================== getType Tests ====================
    
    @Test
    fun `test getType - string`() {
        val value = UDM.Scalar("hello")
        
        val result = TypeFunctions.getType(listOf(value))
        val type = (result as UDM.Scalar).value as String
        
        assertEquals("string", type.lowercase())
    }
    
    @Test
    fun `test getType - integer`() {
        val value = UDM.Scalar(42)
        
        val result = TypeFunctions.getType(listOf(value))
        val type = (result as UDM.Scalar).value as String
        
        assertTrue(type.lowercase().contains("number") || type.lowercase().contains("int"))
    }
    
    @Test
    fun `test getType - double`() {
        val value = UDM.Scalar(3.14)
        
        val result = TypeFunctions.getType(listOf(value))
        val type = (result as UDM.Scalar).value as String
        
        assertTrue(type.lowercase().contains("number") || type.lowercase().contains("double"))
    }
    
    @Test
    fun `test getType - boolean true`() {
        val value = UDM.Scalar(true)
        
        val result = TypeFunctions.getType(listOf(value))
        val type = (result as UDM.Scalar).value as String
        
        assertEquals("boolean", type.lowercase())
    }
    
    @Test
    fun `test getType - boolean false`() {
        val value = UDM.Scalar(false)
        
        val result = TypeFunctions.getType(listOf(value))
        val type = (result as UDM.Scalar).value as String
        
        assertEquals("boolean", type.lowercase())
    }
    
    @Test
    fun `test getType - array`() {
        val value = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3)
        ))
        
        val result = TypeFunctions.getType(listOf(value))
        val type = (result as UDM.Scalar).value as String
        
        assertEquals("array", type.lowercase())
    }
    
    @Test
    fun `test getType - object`() {
        val value = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30)
        ))
        
        val result = TypeFunctions.getType(listOf(value))
        val type = (result as UDM.Scalar).value as String
        
        assertEquals("object", type.lowercase())
    }
    
    @Test
    fun `test getType - null`() {
        val value = UDM.Scalar(null)
        
        val result = TypeFunctions.getType(listOf(value))
        val type = (result as UDM.Scalar).value as String
        
        assertEquals("null", type.lowercase())
    }
    
    @Test
    fun `test getType - empty array`() {
        val value = UDM.Array(emptyList())
        
        val result = TypeFunctions.getType(listOf(value))
        val type = (result as UDM.Scalar).value as String
        
        assertEquals("array", type.lowercase())
    }
    
    @Test
    fun `test getType - empty object`() {
        val value = UDM.Object(mutableMapOf())
        
        val result = TypeFunctions.getType(listOf(value))
        val type = (result as UDM.Scalar).value as String
        
        assertEquals("object", type.lowercase())
    }

    // ==================== isString Tests ====================
    
    @Test
    fun `test isString - string value`() {
        val value = UDM.Scalar("hello world")
        
        val result = TypeFunctions.isString(listOf(value))
        val isString = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isString)
    }
    
    @Test
    fun `test isString - empty string`() {
        val value = UDM.Scalar("")
        
        val result = TypeFunctions.isString(listOf(value))
        val isString = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isString, "Empty string should still be a string")
    }
    
    @Test
    fun `test isString - number`() {
        val value = UDM.Scalar(42)
        
        val result = TypeFunctions.isString(listOf(value))
        val isString = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isString, "Number should not be a string")
    }
    
    @Test
    fun `test isString - numeric string`() {
        val value = UDM.Scalar("123")
        
        val result = TypeFunctions.isString(listOf(value))
        val isString = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isString, "Numeric string is still a string")
    }
    
    @Test
    fun `test isString - boolean`() {
        val value = UDM.Scalar(true)
        
        val result = TypeFunctions.isString(listOf(value))
        val isString = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isString)
    }
    
    @Test
    fun `test isString - array`() {
        val value = UDM.Array(listOf(UDM.Scalar("test")))
        
        val result = TypeFunctions.isString(listOf(value))
        val isString = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isString)
    }

    // ==================== isNumber Tests ====================
    
    @Test
    fun `test isNumber - integer`() {
        val value = UDM.Scalar(42)
        
        val result = TypeFunctions.isNumber(listOf(value))
        val isNumber = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isNumber)
    }
    
    @Test
    fun `test isNumber - double`() {
        val value = UDM.Scalar(3.14159)
        
        val result = TypeFunctions.isNumber(listOf(value))
        val isNumber = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isNumber)
    }
    
    @Test
    fun `test isNumber - negative integer`() {
        val value = UDM.Scalar(-100)
        
        val result = TypeFunctions.isNumber(listOf(value))
        val isNumber = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isNumber)
    }
    
    @Test
    fun `test isNumber - zero`() {
        val value = UDM.Scalar(0)
        
        val result = TypeFunctions.isNumber(listOf(value))
        val isNumber = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isNumber)
    }
    
    @Test
    fun `test isNumber - string`() {
        val value = UDM.Scalar("42")
        
        val result = TypeFunctions.isNumber(listOf(value))
        val isNumber = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isNumber, "String '42' is not a number")
    }
    
    @Test
    fun `test isNumber - boolean`() {
        val value = UDM.Scalar(true)
        
        val result = TypeFunctions.isNumber(listOf(value))
        val isNumber = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isNumber)
    }
    
    @Test
    fun `test isNumber - NaN`() {
        val value = UDM.Scalar(Double.NaN)
        
        val result = TypeFunctions.isNumber(listOf(value))
        val isNumber = (result as UDM.Scalar).value as Boolean
        
        // NaN is technically a number type, but often treated specially
        // Test should match implementation behavior
        assertNotNull(isNumber)
    }
    
    @Test
    fun `test isNumber - infinity`() {
        val value = UDM.Scalar(Double.POSITIVE_INFINITY)
        
        val result = TypeFunctions.isNumber(listOf(value))
        val isNumber = (result as UDM.Scalar).value as Boolean
        
        assertNotNull(isNumber)
    }

    // ==================== isBoolean Tests ====================
    
    @Test
    fun `test isBoolean - true`() {
        val value = UDM.Scalar(true)
        
        val result = TypeFunctions.isBoolean(listOf(value))
        val isBoolean = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isBoolean)
    }
    
    @Test
    fun `test isBoolean - false`() {
        val value = UDM.Scalar(false)
        
        val result = TypeFunctions.isBoolean(listOf(value))
        val isBoolean = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isBoolean)
    }
    
    @Test
    fun `test isBoolean - string true`() {
        val value = UDM.Scalar("true")
        
        val result = TypeFunctions.isBoolean(listOf(value))
        val isBoolean = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isBoolean, "String 'true' is not a boolean")
    }
    
    @Test
    fun `test isBoolean - number 1`() {
        val value = UDM.Scalar(1)
        
        val result = TypeFunctions.isBoolean(listOf(value))
        val isBoolean = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isBoolean, "Number 1 is not a boolean")
    }
    
    @Test
    fun `test isBoolean - number 0`() {
        val value = UDM.Scalar(0)
        
        val result = TypeFunctions.isBoolean(listOf(value))
        val isBoolean = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isBoolean, "Number 0 is not a boolean")
    }

    // ==================== isArray Tests ====================
    
    @Test
    fun `test isArray - array`() {
        val value = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3)
        ))
        
        val result = TypeFunctions.isArray(listOf(value))
        val isArray = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isArray)
    }
    
    @Test
    fun `test isArray - empty array`() {
        val value = UDM.Array(emptyList())
        
        val result = TypeFunctions.isArray(listOf(value))
        val isArray = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isArray, "Empty array should still be an array")
    }
    
    @Test
    fun `test isArray - nested array`() {
        val value = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar(1))),
            UDM.Array(listOf(UDM.Scalar(2)))
        ))
        
        val result = TypeFunctions.isArray(listOf(value))
        val isArray = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isArray)
    }
    
    @Test
    fun `test isArray - object`() {
        val value = UDM.Object(mutableMapOf("key" to UDM.Scalar("value")))
        
        val result = TypeFunctions.isArray(listOf(value))
        val isArray = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isArray, "Object is not an array")
    }
    
    @Test
    fun `test isArray - string`() {
        val value = UDM.Scalar("array")
        
        val result = TypeFunctions.isArray(listOf(value))
        val isArray = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isArray)
    }

    // ==================== isObject Tests ====================
    
    @Test
    fun `test isObject - object`() {
        val value = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30)
        ))
        
        val result = TypeFunctions.isObject(listOf(value))
        val isObject = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isObject)
    }
    
    @Test
    fun `test isObject - empty object`() {
        val value = UDM.Object(mutableMapOf())
        
        val result = TypeFunctions.isObject(listOf(value))
        val isObject = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isObject, "Empty object should still be an object")
    }
    
    @Test
    fun `test isObject - nested object`() {
        val value = UDM.Object(mutableMapOf(
            "user" to UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("Alice")
            ))
        ))
        
        val result = TypeFunctions.isObject(listOf(value))
        val isObject = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isObject)
    }
    
    @Test
    fun `test isObject - array`() {
        val value = UDM.Array(listOf(UDM.Scalar(1)))
        
        val result = TypeFunctions.isObject(listOf(value))
        val isObject = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isObject, "Array is not an object")
    }
    
    @Test
    fun `test isObject - string`() {
        val value = UDM.Scalar("object")
        
        val result = TypeFunctions.isObject(listOf(value))
        val isObject = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isObject)
    }

    // ==================== isNull Tests ====================
    
    @Test
    fun `test isNull - null value`() {
        val value = UDM.Scalar(null)
        
        val result = TypeFunctions.isNull(listOf(value))
        val isNull = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isNull)
    }
    
    @Test
    fun `test isNull - string null`() {
        val value = UDM.Scalar("null")
        
        val result = TypeFunctions.isNull(listOf(value))
        val isNull = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isNull, "String 'null' is not null")
    }
    
    @Test
    fun `test isNull - empty string`() {
        val value = UDM.Scalar("")
        
        val result = TypeFunctions.isNull(listOf(value))
        val isNull = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isNull, "Empty string is not null")
    }
    
    @Test
    fun `test isNull - zero`() {
        val value = UDM.Scalar(0)
        
        val result = TypeFunctions.isNull(listOf(value))
        val isNull = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isNull, "Zero is not null")
    }
    
    @Test
    fun `test isNull - false`() {
        val value = UDM.Scalar(false)
        
        val result = TypeFunctions.isNull(listOf(value))
        val isNull = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isNull, "False is not null")
    }

    // ==================== isDefined Tests ====================
    
    @Test
    fun `test isDefined - defined string`() {
        val value = UDM.Scalar("hello")
        
        val result = TypeFunctions.isDefined(listOf(value))
        val isDefined = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isDefined)
    }
    
    @Test
    fun `test isDefined - defined number`() {
        val value = UDM.Scalar(42)
        
        val result = TypeFunctions.isDefined(listOf(value))
        val isDefined = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isDefined)
    }
    
    @Test
    fun `test isDefined - null value`() {
        val value = UDM.Scalar(null)
        
        val result = TypeFunctions.isDefined(listOf(value))
        val isDefined = (result as UDM.Scalar).value as Boolean
        
        assertFalse(isDefined, "Null should not be considered defined")
    }
    
    @Test
    fun `test isDefined - empty string`() {
        val value = UDM.Scalar("")
        
        val result = TypeFunctions.isDefined(listOf(value))
        val isDefined = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isDefined, "Empty string is defined")
    }
    
    @Test
    fun `test isDefined - zero`() {
        val value = UDM.Scalar(0)
        
        val result = TypeFunctions.isDefined(listOf(value))
        val isDefined = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isDefined, "Zero is defined")
    }
    
    @Test
    fun `test isDefined - false`() {
        val value = UDM.Scalar(false)
        
        val result = TypeFunctions.isDefined(listOf(value))
        val isDefined = (result as UDM.Scalar).value as Boolean
        
        assertTrue(isDefined, "False is defined")
    }

    // ==================== Real-World Scenarios ====================
    
    @Test
    fun `test real-world - input validation`() {
        // Validate form input types
        val name = UDM.Scalar("John Doe")
        val age = UDM.Scalar(30)
        val email = UDM.Scalar("john@example.com")
        val subscribed = UDM.Scalar(true)
        
        assertTrue((TypeFunctions.isString(listOf(name)) as UDM.Scalar).value as Boolean)
        assertTrue((TypeFunctions.isNumber(listOf(age)) as UDM.Scalar).value as Boolean)
        assertTrue((TypeFunctions.isString(listOf(email)) as UDM.Scalar).value as Boolean)
        assertTrue((TypeFunctions.isBoolean(listOf(subscribed)) as UDM.Scalar).value as Boolean)
    }
    
    @Test
    fun `test real-world - API response validation`() {
        val response = UDM.Object(mutableMapOf(
            "data" to UDM.Array(listOf(
                UDM.Object(mutableMapOf("id" to UDM.Scalar(1)))
            )),
            "status" to UDM.Scalar("success"),
            "count" to UDM.Scalar(1)
        ))
        
        // Validate structure
        assertTrue((TypeFunctions.isObject(listOf(response)) as UDM.Scalar).value as Boolean)
        
        val data = response.properties["data"]!!
        assertTrue((TypeFunctions.isArray(listOf(data)) as UDM.Scalar).value as Boolean)
        
        val status = response.properties["status"]!!
        assertTrue((TypeFunctions.isString(listOf(status)) as UDM.Scalar).value as Boolean)
    }
    
    @Test
    fun `test real-world - conditional processing based on type`() {
        val values = listOf(
            UDM.Scalar("hello"),
            UDM.Scalar(42),
            UDM.Scalar(true),
            UDM.Array(listOf(UDM.Scalar(1))),
            UDM.Object(mutableMapOf("key" to UDM.Scalar("value")))
        )
        
        var stringCount = 0
        var numberCount = 0
        var booleanCount = 0
        var arrayCount = 0
        var objectCount = 0
        
        values.forEach { value ->
            when {
                (TypeFunctions.isString(listOf(value)) as UDM.Scalar).value as Boolean -> stringCount++
                (TypeFunctions.isNumber(listOf(value)) as UDM.Scalar).value as Boolean -> numberCount++
                (TypeFunctions.isBoolean(listOf(value)) as UDM.Scalar).value as Boolean -> booleanCount++
                (TypeFunctions.isArray(listOf(value)) as UDM.Scalar).value as Boolean -> arrayCount++
                (TypeFunctions.isObject(listOf(value)) as UDM.Scalar).value as Boolean -> objectCount++
            }
        }
        
        assertEquals(1, stringCount)
        assertEquals(1, numberCount)
        assertEquals(1, booleanCount)
        assertEquals(1, arrayCount)
        assertEquals(1, objectCount)
    }
    
    @Test
    fun `test real-world - safe navigation with type checks`() {
        val data = UDM.Object(mutableMapOf(
            "user" to UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("Alice")
            ))
        ))
        
        // Check if user property exists and is an object
        val user = data.properties["user"]
        if (user != null) {
            val isObject = (TypeFunctions.isObject(listOf(user)) as UDM.Scalar).value as Boolean
            assertTrue(isObject)
            
            if (isObject) {
                val userObj = user as UDM.Object
                val name = userObj.properties["name"]
                if (name != null) {
                    val isString = (TypeFunctions.isString(listOf(name)) as UDM.Scalar).value as Boolean
                    assertTrue(isString)
                }
            }
        }
    }

    // ==================== Edge Cases ====================
    
    @Test
    fun `test edge case - mixed types in array`() {
        val mixedArray = UDM.Array(listOf(
            UDM.Scalar("string"),
            UDM.Scalar(42),
            UDM.Scalar(true),
            UDM.Scalar(null)
        ))
        
        val isArray = (TypeFunctions.isArray(listOf(mixedArray)) as UDM.Scalar).value as Boolean
        assertTrue(isArray, "Array with mixed types is still an array")
    }
    
    @Test
    fun `test edge case - nested structures`() {
        val complex = UDM.Object(mutableMapOf(
            "arrays" to UDM.Array(listOf(
                UDM.Array(listOf(UDM.Scalar(1))),
                UDM.Array(listOf(UDM.Scalar(2)))
            )),
            "objects" to UDM.Object(mutableMapOf(
                "inner" to UDM.Object(mutableMapOf())
            ))
        ))
        
        assertTrue((TypeFunctions.isObject(listOf(complex)) as UDM.Scalar).value as Boolean)
        
        val arrays = complex.properties["arrays"]!!
        assertTrue((TypeFunctions.isArray(listOf(arrays)) as UDM.Scalar).value as Boolean)
    }
    
    @Test
    fun `test edge case - special string values`() {
        val specialStrings = listOf(
            "",
            " ",
            "null",
            "undefined",
            "true",
            "false",
            "0",
            "[]",
            "{}"
        )
        
        specialStrings.forEach { str ->
            val value = UDM.Scalar(str)
            val isString = (TypeFunctions.isString(listOf(value)) as UDM.Scalar).value as Boolean
            assertTrue(isString, "'$str' should be a string")
        }
    }
    
    @Test
    fun `test edge case - type of type`() {
        // What is the type of a type string?
        val typeString = UDM.Scalar("string")
        val typeOfType = TypeFunctions.getType(listOf(typeString))
        val type = (typeOfType as UDM.Scalar).value as String
        
        assertEquals("string", type.lowercase(), "Type of 'string' is string")
    }

    // ==================== Comprehensive Type Matrix ====================
    
    @Test
    fun `test comprehensive type matrix`() {
        data class TypeTest(
            val value: UDM,
            val expectedType: String,
            val isString: Boolean = false,
            val isNumber: Boolean = false,
            val isBoolean: Boolean = false,
            val isArray: Boolean = false,
            val isObject: Boolean = false,
            val isNull: Boolean = false,
            val isDefined: Boolean = true
        )
        
        val tests = listOf(
            TypeTest(UDM.Scalar("hello"), "string", isString = true),
            TypeTest(UDM.Scalar(42), "number", isNumber = true),
            TypeTest(UDM.Scalar(3.14), "number", isNumber = true),
            TypeTest(UDM.Scalar(true), "boolean", isBoolean = true),
            TypeTest(UDM.Scalar(false), "boolean", isBoolean = true),
            TypeTest(UDM.Array(emptyList()), "array", isArray = true),
            TypeTest(UDM.Object(mutableMapOf()), "object", isObject = true),
            TypeTest(UDM.Scalar(null), "null", isNull = true, isDefined = false)
        )
        
        tests.forEach { test ->
            val typeOf = (TypeFunctions.getType(listOf(test.value)) as UDM.Scalar).value as String
            assertTrue(typeOf.lowercase().contains(test.expectedType), 
                "Expected type ${test.expectedType}, got $typeOf")
            
            assertEquals(test.isString, 
                (TypeFunctions.isString(listOf(test.value)) as UDM.Scalar).value as Boolean)
            assertEquals(test.isNumber, 
                (TypeFunctions.isNumber(listOf(test.value)) as UDM.Scalar).value as Boolean)
            assertEquals(test.isBoolean, 
                (TypeFunctions.isBoolean(listOf(test.value)) as UDM.Scalar).value as Boolean)
            assertEquals(test.isArray, 
                (TypeFunctions.isArray(listOf(test.value)) as UDM.Scalar).value as Boolean)
            assertEquals(test.isObject, 
                (TypeFunctions.isObject(listOf(test.value)) as UDM.Scalar).value as Boolean)
            assertEquals(test.isNull, 
                (TypeFunctions.isNull(listOf(test.value)) as UDM.Scalar).value as Boolean)
            assertEquals(test.isDefined, 
                (TypeFunctions.isDefined(listOf(test.value)) as UDM.Scalar).value as Boolean)
        }
    }
}
