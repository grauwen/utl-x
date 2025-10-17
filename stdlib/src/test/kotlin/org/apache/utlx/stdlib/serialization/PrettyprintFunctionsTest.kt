// stdlib/src/test/kotlin/org/apache/utlx/stdlib/serialization/PrettyprintFunctionsTest.kt
package org.apache.utlx.stdlib.serialization

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant

class PrettyprintFunctionsTest {

    // ========== JSON PRETTY PRINTING ==========

    @Test
    fun testPrettyPrintJSONSimple() {
        val compactJson = """{"name":"Alice","age":30,"active":true}"""
        val result = PrettyPrintFunctions.prettyPrintJSON(listOf(UDM.Scalar(compactJson)))
        
        assertTrue(result is UDM.Scalar)
        val prettyJson = result.value as String
        assertTrue(prettyJson.contains("\n"))
        assertTrue(prettyJson.contains("  ")) // Default 2-space indent
        assertTrue(prettyJson.contains("\"name\": \"Alice\""))
    }

    @Test
    fun testPrettyPrintJSONCustomIndent() {
        val compactJson = """{"key":"value"}"""
        val result = PrettyPrintFunctions.prettyPrintJSON(listOf(UDM.Scalar(compactJson), UDM.Scalar(4)))
        
        assertTrue(result is UDM.Scalar)
        val prettyJson = result.value as String
        assertTrue(prettyJson.contains("    ")) // 4-space indent
    }

    @Test
    fun testPrettyPrintJSONInvalid() {
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.prettyPrintJSON(listOf(UDM.Scalar("invalid json {")))
        }
    }

    @Test
    fun testUdmToJSON() {
        val udm = UDM.Object(mapOf(
            "name" to UDM.Scalar("Alice"),
            "age" to UDM.Scalar(30),
            "active" to UDM.Scalar(true),
            "scores" to UDM.Array(listOf(UDM.Scalar(85), UDM.Scalar(92)))
        ))
        
        val result = PrettyPrintFunctions.udmToJSON(listOf(udm))
        
        assertTrue(result is UDM.Scalar)
        val json = result.value as String
        assertTrue(json.contains("\"name\": \"Alice\""))
        assertTrue(json.contains("\"age\": 30"))
        assertTrue(json.contains("\"active\": true"))
        assertTrue(json.contains("["))
        assertTrue(json.contains("85"))
    }

    @Test
    fun testCompactJSON() {
        val prettyJson = """{
  "name": "Alice",
  "age": 30
}"""
        val result = PrettyPrintFunctions.compactJSON(listOf(UDM.Scalar(prettyJson)))
        
        assertTrue(result is UDM.Scalar)
        val compactJson = result.value as String
        assertEquals("""{"name":"Alice","age":30}""", compactJson)
    }

    // ========== XML PRETTY PRINTING ==========

    @Test
    fun testPrettyPrintXMLSimple() {
        val compactXml = "<root><name>Alice</name><age>30</age></root>"
        val result = PrettyPrintFunctions.prettyPrintXML(listOf(UDM.Scalar(compactXml)))
        
        assertTrue(result is UDM.Scalar)
        val prettyXml = result.value as String
        assertTrue(prettyXml.contains("\n"))
    }

    @Test
    fun testUdmToXML() {
        val udm = UDM.Object(mapOf(
            "name" to UDM.Scalar("Alice"),
            "age" to UDM.Scalar(30)
        ))
        
        val result = PrettyPrintFunctions.udmToXML(listOf(udm))
        
        assertTrue(result is UDM.Scalar)
        val xml = result.value as String
        assertTrue(xml.contains("<name>Alice</name>"))
        assertTrue(xml.contains("<age>30</age>"))
    }

    @Test
    fun testCompactXML() {
        val prettyXml = """<root>
  <name>Alice</name>
  <age>30</age>
</root>"""
        val result = PrettyPrintFunctions.compactXML(listOf(UDM.Scalar(prettyXml)))
        
        assertTrue(result is UDM.Scalar)
        val compactXml = result.value as String
        assertEquals("<root><name>Alice</name><age>30</age></root>", compactXml)
    }

    // ========== YAML PRETTY PRINTING ==========

    @Test
    fun testPrettyPrintYAML() {
        val yamlString = "name: Alice\nage: 30"
        val result = PrettyPrintFunctions.prettyPrintYAML(listOf(UDM.Scalar(yamlString)))
        
        assertTrue(result is UDM.Scalar)
        val prettyYaml = result.value as String
        assertNotNull(prettyYaml)
    }

    @Test
    fun testUdmToYAML() {
        val udm = UDM.Object(mapOf(
            "name" to UDM.Scalar("Alice"),
            "age" to UDM.Scalar(30),
            "items" to UDM.Array(listOf(UDM.Scalar("item1"), UDM.Scalar("item2")))
        ))
        
        val result = PrettyPrintFunctions.udmToYAML(listOf(udm))
        
        assertTrue(result is UDM.Scalar)
        val yaml = result.value as String
        assertTrue(yaml.contains("name: Alice"))
        assertTrue(yaml.contains("age: 30"))
        assertTrue(yaml.contains("- item1"))
    }

    // ========== CSV PRETTY PRINTING ==========

    @Test
    fun testPrettyPrintCSV() {
        val csvString = "name,age,city\nAlice,30,New York\nBob,25,London"
        val result = PrettyPrintFunctions.prettyPrintCSV(listOf(UDM.Scalar(csvString)))
        
        assertTrue(result is UDM.Scalar)
        val prettyCSV = result.value as String
        assertTrue(prettyCSV.contains("|"))
        assertTrue(prettyCSV.contains("-"))
    }

    @Test
    fun testPrettyPrintCSVCustomDelimiter() {
        val csvString = "name;age;city\nAlice;30;New York"
        val result = PrettyPrintFunctions.prettyPrintCSV(
            listOf(UDM.Scalar(csvString), UDM.Scalar(";"))
        )
        
        assertTrue(result is UDM.Scalar)
        val prettyCSV = result.value as String
        assertTrue(prettyCSV.contains("Alice"))
        assertTrue(prettyCSV.contains("30"))
    }

    @Test
    fun testPrettyPrintCSVNoAlignment() {
        val csvString = "name,age\nAlice,30"
        val result = PrettyPrintFunctions.prettyPrintCSV(
            listOf(UDM.Scalar(csvString), UDM.Scalar(","), UDM.Scalar(false))
        )
        
        assertTrue(result is UDM.Scalar)
        val csv = result.value as String
        assertTrue(csv.contains("Alice"))
        assertTrue(csv.contains("30"))
    }

    @Test
    fun testCompactCSV() {
        val csvString = "  name  ,  age  \n  Alice  ,  30  "
        val result = PrettyPrintFunctions.compactCSV(listOf(UDM.Scalar(csvString)))
        
        assertTrue(result is UDM.Scalar)
        val compactCSV = result.value as String
        assertEquals("name,age\nAlice,30", compactCSV)
    }

    // ========== AUTO-DETECT PRETTY PRINTING ==========

    @Test
    fun testPrettyPrintAutoDetectJSON() {
        val jsonString = """{"name":"Alice"}"""
        val result = PrettyPrintFunctions.prettyPrint(listOf(UDM.Scalar(jsonString)))
        
        assertTrue(result is UDM.Scalar)
        val prettyString = result.value as String
        assertTrue(prettyString.contains("\n"))
    }

    @Test
    fun testPrettyPrintAutoDetectXML() {
        val xmlString = "<root><name>Alice</name></root>"
        val result = PrettyPrintFunctions.prettyPrint(listOf(UDM.Scalar(xmlString)))
        
        assertTrue(result is UDM.Scalar)
        val prettyString = result.value as String
        assertTrue(prettyString.contains("\n"))
    }

    @Test
    fun testPrettyPrintAutoDetectCSV() {
        val csvString = "name,age\nAlice,30\nBob,25"
        val result = PrettyPrintFunctions.prettyPrint(listOf(UDM.Scalar(csvString)))
        
        assertTrue(result is UDM.Scalar)
        val prettyString = result.value as String
        assertNotNull(prettyString)
    }

    @Test
    fun testPrettyPrintAutoDetectUnknown() {
        val plainString = "This is just plain text"
        val result = PrettyPrintFunctions.prettyPrint(listOf(UDM.Scalar(plainString)))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(plainString, result.value)
    }

    // ========== FORMAT-SPECIFIC PRETTY PRINTING ==========

    @Test
    fun testPrettyPrintFormatJSON() {
        val udm = UDM.Object(mapOf("name" to UDM.Scalar("Alice")))
        val result = PrettyPrintFunctions.prettyPrintFormat(
            listOf(udm, UDM.Scalar("json"))
        )
        
        assertTrue(result is UDM.Scalar)
        val formatted = result.value as String
        assertTrue(formatted.contains("\"name\": \"Alice\""))
    }

    @Test
    fun testPrettyPrintFormatXML() {
        val udm = UDM.Object(mapOf("name" to UDM.Scalar("Alice")))
        val result = PrettyPrintFunctions.prettyPrintFormat(
            listOf(udm, UDM.Scalar("xml"))
        )
        
        assertTrue(result is UDM.Scalar)
        val formatted = result.value as String
        assertTrue(formatted.contains("<name>Alice</name>"))
    }

    @Test
    fun testPrettyPrintFormatYAML() {
        val udm = UDM.Object(mapOf("name" to UDM.Scalar("Alice")))
        val result = PrettyPrintFunctions.prettyPrintFormat(
            listOf(udm, UDM.Scalar("yaml"))
        )
        
        assertTrue(result is UDM.Scalar)
        val formatted = result.value as String
        assertTrue(formatted.contains("name: Alice"))
    }

    @Test
    fun testPrettyPrintFormatUnsupported() {
        val udm = UDM.Object(mapOf("name" to UDM.Scalar("Alice")))
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.prettyPrintFormat(
                listOf(udm, UDM.Scalar("unsupported"))
            )
        }
    }

    // ========== DEBUG PRINTING ==========

    @Test
    fun testDebugPrintScalar() {
        val result = PrettyPrintFunctions.debugPrint(listOf(UDM.Scalar("test")))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertTrue(debug.contains("test"))
        assertTrue(debug.contains("String"))
    }

    @Test
    fun testDebugPrintNumber() {
        val result = PrettyPrintFunctions.debugPrint(listOf(UDM.Scalar(42)))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertTrue(debug.contains("42"))
        assertTrue(debug.contains("Number"))
    }

    @Test
    fun testDebugPrintBoolean() {
        val result = PrettyPrintFunctions.debugPrint(listOf(UDM.Scalar(true)))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertTrue(debug.contains("true"))
        assertTrue(debug.contains("Boolean"))
    }

    @Test
    fun testDebugPrintNull() {
        val result = PrettyPrintFunctions.debugPrint(listOf(UDM.Scalar.nullValue()))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertTrue(debug.contains("null"))
    }

    @Test
    fun testDebugPrintArray() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3)
        ))
        val result = PrettyPrintFunctions.debugPrint(listOf(array))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertTrue(debug.contains("Array"))
        assertTrue(debug.contains("3 elements"))
        assertTrue(debug.contains("[0]:"))
        assertTrue(debug.contains("[1]:"))
    }

    @Test
    fun testDebugPrintObject() {
        val obj = UDM.Object(mapOf(
            "name" to UDM.Scalar("Alice"),
            "age" to UDM.Scalar(30)
        ))
        val result = PrettyPrintFunctions.debugPrint(listOf(obj))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertTrue(debug.contains("Object"))
        assertTrue(debug.contains("2 properties"))
        assertTrue(debug.contains("name:"))
        assertTrue(debug.contains("age:"))
    }

    @Test
    fun testDebugPrintNested() {
        val nested = UDM.Object(mapOf(
            "user" to UDM.Object(mapOf(
                "profile" to UDM.Object(mapOf(
                    "name" to UDM.Scalar("Alice")
                ))
            ))
        ))
        val result = PrettyPrintFunctions.debugPrint(listOf(nested))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertTrue(debug.contains("user:"))
        assertTrue(debug.contains("profile:"))
        assertTrue(debug.contains("name:"))
    }

    @Test
    fun testDebugPrintMaxDepth() {
        val deepNested = UDM.Object(mapOf(
            "level1" to UDM.Object(mapOf(
                "level2" to UDM.Object(mapOf(
                    "level3" to UDM.Scalar("deep")
                ))
            ))
        ))
        val result = PrettyPrintFunctions.debugPrint(
            listOf(deepNested, UDM.Scalar(2), UDM.Scalar(1))
        )
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertTrue(debug.contains("..."))
    }

    @Test
    fun testDebugPrintLargeArray() {
        val largeArray = UDM.Array((1..15).map { UDM.Scalar(it) })
        val result = PrettyPrintFunctions.debugPrint(listOf(largeArray))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertTrue(debug.contains("15 elements"))
        assertTrue(debug.contains("... (5 more)"))
    }

    @Test
    fun testDebugPrintCompact() {
        val obj = UDM.Object(mapOf(
            "name" to UDM.Scalar("Alice"),
            "age" to UDM.Scalar(30)
        ))
        val result = PrettyPrintFunctions.debugPrintCompact(listOf(obj))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertEquals("{2 props}", debug)
    }

    @Test
    fun testDebugPrintCompactArray() {
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        val result = PrettyPrintFunctions.debugPrintCompact(listOf(array))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertEquals("[2 items]", debug)
    }

    @Test
    fun testDebugPrintCompactString() {
        val str = UDM.Scalar("Hello World")
        val result = PrettyPrintFunctions.debugPrintCompact(listOf(str))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertEquals("\"Hello World\"", debug)
    }

    @Test
    fun testDebugPrintCompactLongString() {
        val longString = "a".repeat(250)
        val str = UDM.Scalar(longString)
        val result = PrettyPrintFunctions.debugPrintCompact(listOf(str))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertTrue(debug.endsWith("..."))
        assertTrue(debug.length <= 203) // 200 + quotes + ...
    }

    @Test
    fun testDebugPrintCompactCustomMaxLength() {
        val str = UDM.Scalar("Hello World")
        val result = PrettyPrintFunctions.debugPrintCompact(
            listOf(str, UDM.Scalar(5))
        )
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertTrue(debug.endsWith("..."))
        assertTrue(debug.length <= 8) // 5 + ...
    }

    // ========== ERROR HANDLING ==========

    @Test
    fun testPrettyPrintJSONInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.prettyPrintJSON(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.prettyPrintJSON(
                listOf(UDM.Scalar("{}"), UDM.Scalar(2), UDM.Scalar("extra"))
            )
        }
    }

    @Test
    fun testUdmToJSONInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.udmToJSON(listOf())
        }
    }

    @Test
    fun testCompactJSONInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.compactJSON(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.compactJSON(listOf(UDM.Scalar("{}"), UDM.Scalar("extra")))
        }
    }

    @Test
    fun testPrettyPrintXMLInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.prettyPrintXML(listOf())
        }
    }

    @Test
    fun testPrettyPrintYAMLInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.prettyPrintYAML(listOf())
        }
    }

    @Test
    fun testPrettyPrintCSVInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.prettyPrintCSV(listOf())
        }
    }

    @Test
    fun testPrettyPrintInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.prettyPrint(listOf())
        }
    }

    @Test
    fun testPrettyPrintFormatInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.prettyPrintFormat(listOf(UDM.Scalar("test")))
        }
    }

    @Test
    fun testDebugPrintInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.debugPrint(listOf())
        }
    }

    @Test
    fun testDebugPrintCompactInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.debugPrintCompact(listOf())
        }
    }

    @Test
    fun testInvalidStringInput() {
        assertThrows<FunctionArgumentException> {
            PrettyPrintFunctions.prettyPrintJSON(listOf(UDM.Array(listOf())))
        }
    }

    // ========== EDGE CASES ==========

    @Test
    fun testUdmToJSONComplexTypes() {
        val udm = UDM.Object(mapOf(
            "datetime" to UDM.DateTime(Instant.now()),
            "binary" to UDM.Binary(byteArrayOf(1, 2, 3)),
            "null" to UDM.Scalar.nullValue()
        ))
        
        val result = PrettyPrintFunctions.udmToJSON(listOf(udm))
        
        assertTrue(result is UDM.Scalar)
        val json = result.value as String
        assertTrue(json.contains("null"))
    }

    @Test
    fun testPrettyPrintEmptyStructures() {
        val emptyObject = UDM.Object(mapOf())
        val emptyArray = UDM.Array(listOf())
        
        val jsonObject = PrettyPrintFunctions.udmToJSON(listOf(emptyObject))
        val jsonArray = PrettyPrintFunctions.udmToJSON(listOf(emptyArray))
        
        assertEquals("{}", (jsonObject as UDM.Scalar).value)
        assertEquals("[]", (jsonArray as UDM.Scalar).value)
    }

    @Test
    fun testDebugPrintEmptyStructures() {
        val emptyObject = UDM.Object(mapOf())
        val emptyArray = UDM.Array(listOf())
        
        val debugObject = PrettyPrintFunctions.debugPrint(listOf(emptyObject))
        val debugArray = PrettyPrintFunctions.debugPrint(listOf(emptyArray))
        
        assertTrue((debugObject as UDM.Scalar).value.toString().contains("empty"))
        assertTrue((debugArray as UDM.Scalar).value.toString().contains("empty"))
    }

    @Test
    fun testCompactJSONSpecialCharacters() {
        val json = """{"message": "Hello\nWorld\t!"}"""
        val result = PrettyPrintFunctions.compactJSON(listOf(UDM.Scalar(json)))
        
        assertTrue(result is UDM.Scalar)
        assertNotNull(result.value)
    }

    @Test
    fun testPrettyPrintCSVEmptyString() {
        val result = PrettyPrintFunctions.prettyPrintCSV(listOf(UDM.Scalar("")))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("", result.value)
    }

    @Test
    fun testDebugPrintVeryLongString() {
        val veryLongString = "x".repeat(100)
        val scalar = UDM.Scalar(veryLongString)
        val result = PrettyPrintFunctions.debugPrint(listOf(scalar))
        
        assertTrue(result is UDM.Scalar)
        val debug = result.value as String
        assertTrue(debug.contains("..."))
    }

    @Test
    fun testPrettyPrintFormatCaseInsensitive() {
        val udm = UDM.Object(mapOf("test" to UDM.Scalar("value")))
        
        val result1 = PrettyPrintFunctions.prettyPrintFormat(listOf(udm, UDM.Scalar("JSON")))
        val result2 = PrettyPrintFunctions.prettyPrintFormat(listOf(udm, UDM.Scalar("json")))
        
        assertTrue(result1 is UDM.Scalar)
        assertTrue(result2 is UDM.Scalar)
        assertEquals(result1.value, result2.value)
    }
}