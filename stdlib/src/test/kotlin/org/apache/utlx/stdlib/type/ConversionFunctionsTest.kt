package org.apache.utlx.stdlib.type

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversionFunctionsTest {

    // ==================== NUMBER CONVERSION TESTS ====================

    @Test
    fun testParseNumber() {
        // Basic number parsing
        val result1 = ConversionFunctions.parseNumber(listOf(UDM.Scalar("123.45")))
        assertEquals(123.45, (result1 as UDM.Scalar).value)
        
        val result2 = ConversionFunctions.parseNumber(listOf(UDM.Scalar(42)))
        assertEquals(42.0, (result2 as UDM.Scalar).value)
        
        // Clean formatting
        val result3 = ConversionFunctions.parseNumber(listOf(UDM.Scalar("1,234.56")))
        assertEquals(1234.56, (result3 as UDM.Scalar).value)
        
        val result4 = ConversionFunctions.parseNumber(listOf(UDM.Scalar("$99.99")))
        assertEquals(99.99, (result4 as UDM.Scalar).value)
        
        val result5 = ConversionFunctions.parseNumber(listOf(UDM.Scalar("€1,500.00")))
        assertEquals(1500.0, (result5 as UDM.Scalar).value)
    }

    @Test
    fun testParseNumberWithDefault() {
        val result1 = ConversionFunctions.parseNumber(listOf(
            UDM.Scalar("not a number"), 
            UDM.Scalar(0)
        ))
        assertEquals(0, (result1 as UDM.Scalar).value)
        
        val result2 = ConversionFunctions.parseNumber(listOf(
            UDM.Scalar(null), 
            UDM.Scalar(-1)
        ))
        assertEquals(-1, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testParseNumberInvalid() {
        val result1 = ConversionFunctions.parseNumber(listOf(UDM.Scalar("not a number")))
        assertEquals(null, (result1 as UDM.Scalar).value)
        
        val result2 = ConversionFunctions.parseNumber(listOf(UDM.Scalar(null)))
        assertEquals(null, (result2 as UDM.Scalar).value)
        
        val result3 = ConversionFunctions.parseNumber(listOf(UDM.Array(emptyList())))
        assertEquals(null, (result3 as UDM.Scalar).value)
    }

    @Test
    fun testToNumber() {
        val result1 = ConversionFunctions.toNumber(listOf(UDM.Scalar("42")))
        assertEquals(42.0, (result1 as UDM.Scalar).value)
        
        val result2 = ConversionFunctions.toNumber(listOf(UDM.Scalar(3.14)))
        assertEquals(3.14, (result2 as UDM.Scalar).value)
        
        // Should throw on invalid conversion
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.toNumber(listOf(UDM.Scalar("not a number")))
        }
    }

    @Test
    fun testParseInt() {
        val result1 = ConversionFunctions.parseInt(listOf(UDM.Scalar("42")))
        assertEquals(42.0, (result1 as UDM.Scalar).value)
        
        // Truncates decimals
        val result2 = ConversionFunctions.parseInt(listOf(UDM.Scalar("42.7")))
        assertEquals(42.0, (result2 as UDM.Scalar).value)
        
        val result3 = ConversionFunctions.parseInt(listOf(UDM.Scalar(3.9)))
        assertEquals(3.0, (result3 as UDM.Scalar).value)
        
        // With default
        val result4 = ConversionFunctions.parseInt(listOf(
            UDM.Scalar("not a number"), 
            UDM.Scalar(0)
        ))
        assertEquals(0, (result4 as UDM.Scalar).value)
    }

    @Test
    fun testParseFloat() {
        val result1 = ConversionFunctions.parseFloat(listOf(UDM.Scalar("3.14159")))
        assertEquals(3.14159, (result1 as UDM.Scalar).value)
        
        val result2 = ConversionFunctions.parseFloat(listOf(UDM.Scalar("42")))
        assertEquals(42.0, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testParseDouble() {
        val result1 = ConversionFunctions.parseDouble(listOf(UDM.Scalar("3.141592653589793")))
        assertEquals(3.141592653589793, (result1 as UDM.Scalar).value)
    }

    // ==================== STRING CONVERSION TESTS ====================

    @Test
    fun testToString() {
        // Numbers
        val result1 = ConversionFunctions.toString(listOf(UDM.Scalar(42)))
        assertEquals("42", (result1 as UDM.Scalar).value)
        
        val result2 = ConversionFunctions.toString(listOf(UDM.Scalar(3.14)))
        assertEquals("3.14", (result2 as UDM.Scalar).value)
        
        // Integer-like doubles should not show decimals
        val result3 = ConversionFunctions.toString(listOf(UDM.Scalar(42.0)))
        assertEquals("42", (result3 as UDM.Scalar).value)
        
        // Booleans
        val result4 = ConversionFunctions.toString(listOf(UDM.Scalar(true)))
        assertEquals("true", (result4 as UDM.Scalar).value)
        
        val result5 = ConversionFunctions.toString(listOf(UDM.Scalar(false)))
        assertEquals("false", (result5 as UDM.Scalar).value)
        
        // Null
        val result6 = ConversionFunctions.toString(listOf(UDM.Scalar(null)))
        assertEquals("", (result6 as UDM.Scalar).value)
        
        // String
        val result7 = ConversionFunctions.toString(listOf(UDM.Scalar("hello")))
        assertEquals("hello", (result7 as UDM.Scalar).value)
    }

    @Test
    fun testToStringArray() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3)
        ))
        
        val result = ConversionFunctions.toString(listOf(array))
        assertEquals("[1, 2, 3]", (result as UDM.Scalar).value)
    }

    @Test
    fun testToStringObject() {
        val obj = UDM.Object(mapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30)
        ), emptyMap())
        
        val result = ConversionFunctions.toString(listOf(obj))
        val resultStr = (result as UDM.Scalar).value as String
        
        // Check that it contains both properties (order may vary)
        assertTrue(resultStr.contains("name: John"))
        assertTrue(resultStr.contains("age: 30"))
        assertTrue(resultStr.startsWith("{"))
        assertTrue(resultStr.endsWith("}"))
    }

    // ==================== BOOLEAN CONVERSION TESTS ====================

    @Test
    fun testParseBoolean() {
        // True values
        assertEquals(true, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("true"))) as UDM.Scalar).value)
        assertEquals(true, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("yes"))) as UDM.Scalar).value)
        assertEquals(true, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("y"))) as UDM.Scalar).value)
        assertEquals(true, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("1"))) as UDM.Scalar).value)
        assertEquals(true, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("on"))) as UDM.Scalar).value)
        assertEquals(true, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("enabled"))) as UDM.Scalar).value)
        assertEquals(true, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar(1))) as UDM.Scalar).value)
        assertEquals(true, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar(42))) as UDM.Scalar).value)
        
        // False values
        assertEquals(false, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("false"))) as UDM.Scalar).value)
        assertEquals(false, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("no"))) as UDM.Scalar).value)
        assertEquals(false, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("n"))) as UDM.Scalar).value)
        assertEquals(false, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("0"))) as UDM.Scalar).value)
        assertEquals(false, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("off"))) as UDM.Scalar).value)
        assertEquals(false, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("disabled"))) as UDM.Scalar).value)
        assertEquals(false, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar(0))) as UDM.Scalar).value)
        
        // Already boolean
        assertEquals(true, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar(true))) as UDM.Scalar).value)
        assertEquals(false, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar(false))) as UDM.Scalar).value)
        
        // Invalid values
        assertEquals(null, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("invalid"))) as UDM.Scalar).value)
        assertEquals(null, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar(null))) as UDM.Scalar).value)
    }

    @Test
    fun testParseBooleanWithDefault() {
        val result1 = ConversionFunctions.parseBoolean(listOf(
            UDM.Scalar("invalid"), 
            UDM.Scalar(false)
        ))
        assertEquals(false, (result1 as UDM.Scalar).value)
        
        val result2 = ConversionFunctions.parseBoolean(listOf(
            UDM.Scalar("true"), 
            UDM.Scalar(false)
        ))
        assertEquals(true, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testToBooleanCaseSensitivity() {
        // Should be case-insensitive
        assertEquals(true, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("TRUE"))) as UDM.Scalar).value)
        assertEquals(true, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("Yes"))) as UDM.Scalar).value)
        assertEquals(false, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("FALSE"))) as UDM.Scalar).value)
        assertEquals(false, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("No"))) as UDM.Scalar).value)
    }

    @Test
    fun testToBoolean() {
        val result1 = ConversionFunctions.toBoolean(listOf(UDM.Scalar("true")))
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        // Should throw on invalid conversion
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.toBoolean(listOf(UDM.Scalar("invalid")))
        }
    }

    // ==================== DATE CONVERSION TESTS ====================

    @Test
    fun testParseDate() {
        // ISO date format
        val result1 = ConversionFunctions.parseDate(listOf(UDM.Scalar("2023-10-15")))
        assertTrue(result1 is UDM.Scalar)
        val dateStr1 = (result1 as UDM.Scalar).value as String
        assertTrue(dateStr1.contains("2023"))
        
        // ISO datetime format
        val result2 = ConversionFunctions.parseDate(listOf(UDM.Scalar("2023-10-15T14:30:00Z")))
        assertTrue(result2 is UDM.Scalar)
        val dateStr2 = (result2 as UDM.Scalar).value as String
        assertTrue(dateStr2.contains("2023"))
        
        // Invalid date
        val result3 = ConversionFunctions.parseDate(listOf(UDM.Scalar("invalid")))
        assertEquals(null, (result3 as UDM.Scalar).value)
    }

    @Test
    fun testParseDateWithFormat() {
        // Custom format
        val result1 = ConversionFunctions.parseDate(listOf(
            UDM.Scalar("10/15/2023"), 
            UDM.Scalar("MM/dd/yyyy")
        ))
        assertTrue(result1 is UDM.Scalar)
        val dateStr1 = (result1 as UDM.Scalar).value as String
        assertTrue(dateStr1.contains("2023"))
        
        // Invalid format
        val result2 = ConversionFunctions.parseDate(listOf(
            UDM.Scalar("2023-10-15"), 
            UDM.Scalar("MM/dd/yyyy")
        ))
        assertEquals(null, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testParseDateWithDefault() {
        val result = ConversionFunctions.parseDate(listOf(
            UDM.Scalar("invalid"), 
            UDM.Scalar("MM/dd/yyyy"), 
            UDM.Scalar("default")
        ))
        assertEquals("default", (result as UDM.Scalar).value)
    }

    // ==================== ARRAY/OBJECT CONVERSION TESTS ====================

    @Test
    fun testToArray() {
        // Single value to array
        val result1 = ConversionFunctions.toArray(listOf(UDM.Scalar(42)))
        assertTrue(result1 is UDM.Array)
        val array1 = result1 as UDM.Array
        assertEquals(1, array1.elements.size)
        assertEquals(42, (array1.elements[0] as UDM.Scalar).value)
        
        // Array stays array
        val inputArray = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        val result2 = ConversionFunctions.toArray(listOf(inputArray))
        assertEquals(inputArray, result2)
        
        // Object to array of pairs
        val obj = UDM.Object(mapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2)
        ), emptyMap())
        val result3 = ConversionFunctions.toArray(listOf(obj))
        assertTrue(result3 is UDM.Array)
        val array3 = result3 as UDM.Array
        assertEquals(2, array3.elements.size)
        
        // Each element should be a [key, value] pair
        array3.elements.forEach { element ->
            assertTrue(element is UDM.Array)
            assertEquals(2, (element as UDM.Array).elements.size)
        }
    }

    @Test
    fun testToObject() {
        // Object stays object
        val inputObj = UDM.Object(mapOf("key" to UDM.Scalar("value")), emptyMap())
        val result1 = ConversionFunctions.toObject(listOf(inputObj))
        assertEquals(inputObj, result1)
        
        // Array of pairs to object
        val pairsArray = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar(1))),
            UDM.Array(listOf(UDM.Scalar("b"), UDM.Scalar(2)))
        ))
        val result2 = ConversionFunctions.toObject(listOf(pairsArray))
        assertTrue(result2 is UDM.Object)
        val obj2 = result2 as UDM.Object
        assertEquals(2, obj2.properties.size)
        assertEquals(1, (obj2.properties["a"] as UDM.Scalar).value)
        assertEquals(2, (obj2.properties["b"] as UDM.Scalar).value)
        
        // Invalid conversion
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.toObject(listOf(UDM.Scalar("cannot convert")))
        }
    }

    // ==================== SAFE CONVERSION TESTS ====================

    @Test
    fun testNumberOrDefault() {
        val result1 = ConversionFunctions.numberOrDefault(listOf(
            UDM.Scalar("123"), 
            UDM.Scalar(0)
        ))
        assertEquals(123.0, (result1 as UDM.Scalar).value)
        
        val result2 = ConversionFunctions.numberOrDefault(listOf(
            UDM.Scalar("invalid"), 
            UDM.Scalar(0)
        ))
        assertEquals(0, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testStringOrDefault() {
        val result1 = ConversionFunctions.stringOrDefault(listOf(
            UDM.Scalar("hello"), 
            UDM.Scalar("default")
        ))
        assertEquals("hello", (result1 as UDM.Scalar).value)
        
        val result2 = ConversionFunctions.stringOrDefault(listOf(
            UDM.Scalar(null), 
            UDM.Scalar("N/A")
        ))
        assertEquals("N/A", (result2 as UDM.Scalar).value)
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun testInvalidArgumentCounts() {
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.parseNumber(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.parseNumber(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        }
        
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.toNumber(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.toNumber(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        }
        
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.toString(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.toString(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        }
        
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.toArray(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.toObject(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.numberOrDefault(listOf(UDM.Scalar(1)))
        }
        
        assertThrows<IllegalArgumentException> {
            ConversionFunctions.stringOrDefault(listOf(UDM.Scalar("text")))
        }
    }

    // ==================== EDGE CASES ====================

    @Test
    fun testParseNumberEdgeCases() {
        // Various currency symbols and formatting
        assertEquals(1234.56, (ConversionFunctions.parseNumber(listOf(UDM.Scalar("£1,234.56"))) as UDM.Scalar).value)
        assertEquals(99.99, (ConversionFunctions.parseNumber(listOf(UDM.Scalar("¥99.99"))) as UDM.Scalar).value)
        assertEquals(50.0, (ConversionFunctions.parseNumber(listOf(UDM.Scalar("50%"))) as UDM.Scalar).value)
        assertEquals(123.0, (ConversionFunctions.parseNumber(listOf(UDM.Scalar(" 123 "))) as UDM.Scalar).value)
        assertEquals(42.0, (ConversionFunctions.parseNumber(listOf(UDM.Scalar("+42"))) as UDM.Scalar).value)
        
        // Already numbers
        assertEquals(42.0, (ConversionFunctions.parseNumber(listOf(UDM.Scalar(42))) as UDM.Scalar).value)
        assertEquals(3.14, (ConversionFunctions.parseNumber(listOf(UDM.Scalar(3.14))) as UDM.Scalar).value)
    }

    @Test
    fun testToStringEdgeCases() {
        // Large numbers
        val result1 = ConversionFunctions.toString(listOf(UDM.Scalar(1000000)))
        assertEquals("1000000", (result1 as UDM.Scalar).value)
        
        // Very small numbers
        val result2 = ConversionFunctions.toString(listOf(UDM.Scalar(0.000001)))
        assertEquals("1.0E-6", (result2 as UDM.Scalar).value)
        
        // Empty array
        val result3 = ConversionFunctions.toString(listOf(UDM.Array(emptyList())))
        assertEquals("[]", (result3 as UDM.Scalar).value)
        
        // Empty object
        val result4 = ConversionFunctions.toString(listOf(UDM.Object(emptyMap(), emptyMap())))
        assertEquals("{}", (result4 as UDM.Scalar).value)
    }

    @Test
    fun testToObjectEdgeCases() {
        // Array with invalid pairs
        val invalidArray = UDM.Array(listOf(
            UDM.Array(listOf(UDM.Scalar("a"))), // Only one element
            UDM.Scalar("not-an-array"),
            UDM.Array(listOf(UDM.Scalar("b"), UDM.Scalar(2)))
        ))
        
        val result = ConversionFunctions.toObject(listOf(invalidArray))
        assertTrue(result is UDM.Object)
        val obj = result as UDM.Object
        assertEquals(1, obj.properties.size) // Only the valid pair should be included
        assertEquals(2, (obj.properties["b"] as UDM.Scalar).value)
    }

    @Test
    fun testParseIntWithNegativeNumbers() {
        val result1 = ConversionFunctions.parseInt(listOf(UDM.Scalar("-42")))
        assertEquals(-42.0, (result1 as UDM.Scalar).value)
        
        val result2 = ConversionFunctions.parseInt(listOf(UDM.Scalar(-3.7)))
        assertEquals(-3.0, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testParseBooleanWithWhitespace() {
        assertEquals(true, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("  true  "))) as UDM.Scalar).value)
        assertEquals(false, (ConversionFunctions.parseBoolean(listOf(UDM.Scalar("  false  "))) as UDM.Scalar).value)
    }
}