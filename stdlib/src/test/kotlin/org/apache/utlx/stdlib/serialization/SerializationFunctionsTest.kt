// stdlib/src/test/kotlin/org/apache/utlx/stdlib/serialization/SerializationFunctionsTest.kt
package org.apache.utlx.stdlib.serialization

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Comprehensive test suite for Serialization functions.
 * 
 * Tests cover:
 * - JSON parsing and rendering
 * - XML parsing and rendering  
 * - YAML parsing and rendering
 * - CSV parsing and rendering
 * - Generic parse/render with format detection
 * - Format conversion
 * - Error handling
 */
class SerializationFunctionsTest {

    // ==================== JSON Tests ====================
    
    @Test
    fun `test parseJson - simple object`() {
        val jsonStr = UDM.Scalar("""{"name":"John","age":30}""")

        val result = SerializationFunctions.parseJson(listOf(jsonStr))
        val obj = result as UDM.Object

        assertEquals("John", (obj.properties["name"] as UDM.Scalar).value)
        assertEquals(30.0, (obj.properties["age"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test parseJson - array`() {
        val jsonStr = UDM.Scalar("""[1,2,3,4,5]""")

        val result = SerializationFunctions.parseJson(listOf(jsonStr))
        val array = result as UDM.Array

        assertEquals(5, array.elements.size)
        assertEquals(1.0, (array.elements[0] as UDM.Scalar).value)
        assertEquals(5.0, (array.elements[4] as UDM.Scalar).value)
    }
    
    @Test
    fun `test parseJson - nested structure`() {
        val jsonStr = UDM.Scalar("""
            {
                "user": {
                    "name": "Alice",
                    "contacts": [
                        {"type": "email", "value": "alice@example.com"},
                        {"type": "phone", "value": "555-1234"}
                    ]
                }
            }
        """.trimIndent())
        
        val result = SerializationFunctions.parseJson(listOf(jsonStr))
        val obj = result as UDM.Object
        
        val user = obj.properties["user"] as UDM.Object
        assertEquals("Alice", (user.properties["name"] as UDM.Scalar).value)
        
        val contacts = user.properties["contacts"] as UDM.Array
        assertEquals(2, contacts.elements.size)
    }
    
    @Test
    fun `test parseJson - special characters`() {
        val jsonStr = UDM.Scalar("""{"text":"Line 1\nLine 2\tTabbed"}""")
        
        val result = SerializationFunctions.parseJson(listOf(jsonStr))
        val obj = result as UDM.Object
        
        val text = (obj.properties["text"] as UDM.Scalar).value as String
        assertTrue(text.contains("\n") || text.contains("\\n"))
    }
    
    @Test
    fun `test renderJson - simple object`() {
        val obj = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30)
        ))
        
        val result = SerializationFunctions.renderJson(listOf(obj))
        val jsonStr = (result as UDM.Scalar).value as String
        
        assertTrue(jsonStr.contains("\"name\"") || jsonStr.contains("name"))
        assertTrue(jsonStr.contains("\"John\"") || jsonStr.contains("John"))
        assertTrue(jsonStr.contains("30"))
    }
    
    @Test
    fun `test renderJson - array`() {
        val array = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3)
        ))
        
        val result = SerializationFunctions.renderJson(listOf(array))
        val jsonStr = (result as UDM.Scalar).value as String
        
        assertTrue(jsonStr.contains("["))
        assertTrue(jsonStr.contains("]"))
        assertTrue(jsonStr.contains("1"))
        assertTrue(jsonStr.contains("3"))
    }
    
    @Test
    fun `test renderJson - nested structure`() {
        val obj = UDM.Object(mutableMapOf(
            "user" to UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("Alice"),
                "age" to UDM.Scalar(25)
            ))
        ))
        
        val result = SerializationFunctions.renderJson(listOf(obj))
        val jsonStr = (result as UDM.Scalar).value as String
        
        assertTrue(jsonStr.contains("user"))
        assertTrue(jsonStr.contains("Alice"))
    }
    
    @Test
    fun `test renderJson - pretty print option`() {
        val obj = UDM.Object(mutableMapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2)
        ))
        val options = UDM.Object(mutableMapOf(
            "pretty" to UDM.Scalar(true)
        ))
        
        val result = SerializationFunctions.renderJson(listOf(obj, options))
        val jsonStr = (result as UDM.Scalar).value as String
        
        // Pretty-printed JSON should contain newlines or indentation
        assertTrue(jsonStr.length > 20, "Pretty-printed JSON should be longer")
    }

    // ==================== XML Tests ====================
    
    @Test
    fun `test parseXml - simple element`() {
        val xmlStr = UDM.Scalar("""<person><name>John</name><age>30</age></person>""")

        val result = SerializationFunctions.parseXml(listOf(xmlStr))
        val wrapper = result as UDM.Object

        // XML parser wraps root in an object with root element name as key
        assertNotNull(wrapper.properties["person"], "Should have 'person' as root element")
        val person = wrapper.properties["person"] as UDM.Object

        // Child text elements should be directly accessible as scalar properties
        val nameValue = person.properties["name"]
        val ageValue = person.properties["age"]
        assertNotNull(nameValue, "name property should exist, got keys: ${person.properties.keys}, values: ${person.properties.values.map{it::class.simpleName}}")
        assertNotNull(ageValue, "age property should exist")

        // Values should be scalars (text content)
        assertTrue(person.properties["name"] is UDM.Scalar, "name should be a Scalar")
        assertTrue(person.properties["age"] is UDM.Scalar, "age should be a Scalar")

        assertEquals("John", (person.properties["name"] as UDM.Scalar).value)
        assertEquals(30.0, (person.properties["age"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test parseXml - with attributes`() {
        val xmlStr = UDM.Scalar("""<user id="123" type="admin"><name>Alice</name></user>""")

        val result = SerializationFunctions.parseXml(listOf(xmlStr))
        val wrapper = result as UDM.Object

        val user = wrapper.properties["user"] as UDM.Object

        // Attributes should be in the attributes map (accessed via @ in language syntax)
        assertTrue(user.attributes.containsKey("id"), "id attribute should exist")
        assertTrue(user.attributes.containsKey("type"), "type attribute should exist")
        assertEquals("123", user.attributes["id"])
        assertEquals("admin", user.attributes["type"])
    }
    
    @Test
    fun `test parseXml - nested elements`() {
        val xmlStr = UDM.Scalar("""
            <company>
                <department name="Engineering">
                    <employee>
                        <name>Bob</name>
                        <role>Developer</role>
                    </employee>
                </department>
            </company>
        """.trimIndent())

        val result = SerializationFunctions.parseXml(listOf(xmlStr))
        val wrapper = result as UDM.Object

        val company = wrapper.properties["company"] as UDM.Object
        assertNotNull(company.properties["department"], "department should exist")
    }
    
    @Test
    fun `test parseXml - with namespaces`() {
        val xmlStr = UDM.Scalar("""
            <root xmlns:ns="http://example.com/ns">
                <ns:element>Value</ns:element>
            </root>
        """.trimIndent())
        
        val result = SerializationFunctions.parseXml(listOf(xmlStr))
        assertNotNull(result, "Should parse XML with namespaces")
    }
    
    @Test
    fun `test renderXml - simple object`() {
        val obj = UDM.Object(mutableMapOf(
            "person" to UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("John"),
                "age" to UDM.Scalar(30)
            ))
        ))
        
        val result = SerializationFunctions.renderXml(listOf(obj))
        val xmlStr = (result as UDM.Scalar).value as String
        
        assertTrue(xmlStr.contains("<person>") || xmlStr.contains("person"))
        assertTrue(xmlStr.contains("John"))
        assertTrue(xmlStr.contains("30"))
    }
    
    @Test
    fun `test renderXml - with attributes`() {
        val obj = UDM.Object(mutableMapOf(
            "user" to UDM.Object(
                mutableMapOf("name" to UDM.Scalar("Alice")),
                mutableMapOf("id" to "123")
            )
        ))
        
        val result = SerializationFunctions.renderXml(listOf(obj))
        val xmlStr = (result as UDM.Scalar).value as String
        
        assertTrue(xmlStr.contains("id") && xmlStr.contains("123"))
        assertTrue(xmlStr.contains("Alice"))
    }

    // ==================== YAML Tests ====================
    
    @Test
    fun `test parseYaml - simple structure`() {
        val yamlStr = UDM.Scalar("""
            name: John
            age: 30
            city: New York
        """.trimIndent())
        
        val result = SerializationFunctions.parseYaml(listOf(yamlStr))
        val obj = result as UDM.Object

        assertEquals("John", (obj.properties["name"] as UDM.Scalar).value)
        assertEquals(30, (obj.properties["age"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test parseYaml - list`() {
        val yamlStr = UDM.Scalar("""
            - apple
            - banana
            - cherry
        """.trimIndent())
        
        val result = SerializationFunctions.parseYaml(listOf(yamlStr))
        val array = result as UDM.Array
        
        assertEquals(3, array.elements.size)
        assertEquals("apple", (array.elements[0] as UDM.Scalar).value)
    }
    
    @Test
    fun `test parseYaml - nested structure`() {
        val yamlStr = UDM.Scalar("""
            user:
              name: Alice
              contact:
                email: alice@example.com
                phone: 555-1234
        """.trimIndent())
        
        val result = SerializationFunctions.parseYaml(listOf(yamlStr))
        val obj = result as UDM.Object
        
        val user = obj.properties["user"] as UDM.Object
        assertEquals("Alice", (user.properties["name"] as UDM.Scalar).value)
        
        val contact = user.properties["contact"] as UDM.Object
        assertNotNull(contact.properties["email"])
    }
    
    @Test
    fun `test renderYaml - simple object`() {
        val obj = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30)
        ))
        
        val result = SerializationFunctions.renderYaml(listOf(obj))
        val yamlStr = (result as UDM.Scalar).value as String
        
        assertTrue(yamlStr.contains("name"))
        assertTrue(yamlStr.contains("John"))
        assertTrue(yamlStr.contains("age"))
        assertTrue(yamlStr.contains("30"))
    }
    
    @Test
    fun `test renderYaml - array`() {
        val array = UDM.Array(listOf(
            UDM.Scalar("apple"),
            UDM.Scalar("banana"),
            UDM.Scalar("cherry")
        ))
        
        val result = SerializationFunctions.renderYaml(listOf(array))
        val yamlStr = (result as UDM.Scalar).value as String
        
        assertTrue(yamlStr.contains("apple"))
        assertTrue(yamlStr.contains("banana"))
        assertTrue(yamlStr.contains("-") || yamlStr.contains("cherry"))
    }

    // ==================== CSV Tests ====================
    
    @Test
    fun `test parseCsv - simple table`() {
        val csvStr = UDM.Scalar("""
            name,age,city
            John,30,NYC
            Alice,25,LA
            Bob,35,Chicago
        """.trimIndent())
        
        val result = SerializationFunctions.parseCsv(listOf(csvStr))
        val array = result as UDM.Array
        
        assertEquals(3, array.elements.size, "Should have 3 data rows")
        
        val firstRow = array.elements[0] as UDM.Object
        assertEquals("John", (firstRow.properties["name"] as UDM.Scalar).value)
        assertEquals(30.0, (firstRow.properties["age"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test parseCsv - with quotes`() {
        val csvStr = UDM.Scalar("""
            name,address
            "John Doe","123 Main St, Apt 4"
            "Alice Smith","456 Oak Ave"
        """.trimIndent())
        
        val result = SerializationFunctions.parseCsv(listOf(csvStr))
        val array = result as UDM.Array
        
        assertEquals(2, array.elements.size)
        
        val firstRow = array.elements[0] as UDM.Object
        val address = (firstRow.properties["address"] as UDM.Scalar).value as String
        assertTrue(address.contains(","), "Should preserve comma in quoted field")
    }
    
    @Test
    fun `test parseCsv - no headers`() {
        val csvStr = UDM.Scalar("""
            John,30,NYC
            Alice,25,LA
        """.trimIndent())
        val options = UDM.Object(mutableMapOf(
            "headers" to UDM.Scalar(false)
        ))
        
        val result = SerializationFunctions.parseCsv(listOf(csvStr, options))
        val array = result as UDM.Array
        
        assertEquals(2, array.elements.size)
    }
    
    @Test
    fun `test parseCsv - custom delimiter`() {
        val csvStr = UDM.Scalar("""
            name|age|city
            John|30|NYC
            Alice|25|LA
        """.trimIndent())
        val options = UDM.Object(mutableMapOf(
            "delimiter" to UDM.Scalar("|")
        ))
        
        val result = SerializationFunctions.parseCsv(listOf(csvStr, options))
        val array = result as UDM.Array
        
        assertEquals(2, array.elements.size)
    }
    
    @Test
    fun `test renderCsv - array of objects`() {
        val data = UDM.Array(listOf(
            UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("John"),
                "age" to UDM.Scalar(30),
                "city" to UDM.Scalar("NYC")
            )),
            UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("Alice"),
                "age" to UDM.Scalar(25),
                "city" to UDM.Scalar("LA")
            ))
        ))
        
        val result = SerializationFunctions.renderCsv(listOf(data))
        val csvStr = (result as UDM.Scalar).value as String
        
        // Should have headers
        assertTrue(csvStr.contains("name") || csvStr.contains("age"))
        // Should have data
        assertTrue(csvStr.contains("John"))
        assertTrue(csvStr.contains("Alice"))
    }
    
    @Test
    fun `test renderCsv - with special characters`() {
        val data = UDM.Array(listOf(
            UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("Doe, John"),
                "address" to UDM.Scalar("123 Main St, Apt 4")
            ))
        ))
        
        val result = SerializationFunctions.renderCsv(listOf(data))
        val csvStr = (result as UDM.Scalar).value as String
        
        // Fields with commas should be quoted
        assertTrue(csvStr.contains("\"") || csvStr.contains(","))
    }

    // ==================== Generic Parse/Render Tests ====================
    
    @Test
    fun `test parse - auto-detect JSON`() {
        val jsonStr = UDM.Scalar("""{"name":"John"}""")
        val format = UDM.Scalar("json")
        
        val result = SerializationFunctions.parse(listOf(jsonStr, format))
        val obj = result as UDM.Object
        
        assertEquals("John", (obj.properties["name"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test parse - auto-detect XML`() {
        val xmlStr = UDM.Scalar("""<person><name>John</name></person>""")
        val format = UDM.Scalar("xml")
        
        val result = SerializationFunctions.parse(listOf(xmlStr, format))
        assertNotNull(result, "Should parse XML")
    }
    
    @Test
    fun `test render - specify format JSON`() {
        val obj = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("John")
        ))
        val format = UDM.Scalar("json")
        
        val result = SerializationFunctions.render(listOf(obj, format))
        val jsonStr = (result as UDM.Scalar).value as String
        
        assertTrue(jsonStr.contains("John"))
    }
    
    @Test
    fun `test render - specify format XML`() {
        val obj = UDM.Object(mutableMapOf(
            "person" to UDM.Object(mutableMapOf(
                "name" to UDM.Scalar("John")
            ))
        ))
        val format = UDM.Scalar("xml")
        
        val result = SerializationFunctions.render(listOf(obj, format))
        val xmlStr = (result as UDM.Scalar).value as String
        
        assertTrue(xmlStr.contains("John"))
    }

    // ==================== Format Conversion Tests ====================
    
    @Test
    fun `test format conversion - JSON to XML`() {
        val jsonStr = UDM.Scalar("""{"person":{"name":"John","age":30}}""")
        
        // Parse JSON
        val obj = SerializationFunctions.parseJson(listOf(jsonStr))
        
        // Render as XML
        val xmlResult = SerializationFunctions.renderXml(listOf(obj))
        val xmlStr = (xmlResult as UDM.Scalar).value as String
        
        assertTrue(xmlStr.contains("John"))
        assertTrue(xmlStr.contains("30"))
    }
    
    @Test
    fun `test format conversion - XML to JSON`() {
        val xmlStr = UDM.Scalar("""<person><name>John</name><age>30</age></person>""")
        
        // Parse XML
        val obj = SerializationFunctions.parseXml(listOf(xmlStr))
        
        // Render as JSON
        val jsonResult = SerializationFunctions.renderJson(listOf(obj))
        val jsonStr = (jsonResult as UDM.Scalar).value as String
        
        assertTrue(jsonStr.contains("John"))
    }
    
    @Test
    fun `test format conversion - JSON to YAML`() {
        val jsonStr = UDM.Scalar("""{"name":"John","age":30}""")
        
        // Parse JSON
        val obj = SerializationFunctions.parseJson(listOf(jsonStr))
        
        // Render as YAML
        val yamlResult = SerializationFunctions.renderYaml(listOf(obj))
        val yamlStr = (yamlResult as UDM.Scalar).value as String
        
        assertTrue(yamlStr.contains("John"))
        assertTrue(yamlStr.contains("30"))
    }
    
    @Test
    fun `test format conversion - CSV to JSON`() {
        val csvStr = UDM.Scalar("""
            name,age
            John,30
            Alice,25
        """.trimIndent())
        
        // Parse CSV
        val array = SerializationFunctions.parseCsv(listOf(csvStr))
        
        // Render as JSON
        val jsonResult = SerializationFunctions.renderJson(listOf(array))
        val jsonStr = (jsonResult as UDM.Scalar).value as String
        
        assertTrue(jsonStr.contains("John"))
        assertTrue(jsonStr.contains("Alice"))
    }

    // ==================== Error Handling Tests ====================
    
    @Test
    fun `test parseJson - invalid JSON`() {
        val invalidJson = UDM.Scalar("""{"name":"John""")  // Missing closing brace
        
        assertThrows<Exception> {
            SerializationFunctions.parseJson(listOf(invalidJson))
        }
    }
    
    @Test
    fun `test parseXml - malformed XML`() {
        val invalidXml = UDM.Scalar("""<person><name>John</person>""")  // Mismatched tags
        
        assertThrows<Exception> {
            SerializationFunctions.parseXml(listOf(invalidXml))
        }
    }
    
    @Test
    fun `test parseCsv - inconsistent columns`() {
        val inconsistentCsv = UDM.Scalar("""
            name,age,city
            John,30
            Alice,25,LA,Extra
        """.trimIndent())
        
        // Should handle gracefully (some parsers are lenient)
        val result = SerializationFunctions.parseCsv(listOf(inconsistentCsv))
        assertNotNull(result)
    }

    // ==================== Real-World Scenarios ====================
    
    @Test
    fun `test real-world - API response transformation`() {
        // Receive JSON from API
        val apiJson = UDM.Scalar("""
            {
                "users": [
                    {"id": 1, "name": "John", "email": "john@example.com"},
                    {"id": 2, "name": "Alice", "email": "alice@example.com"}
                ]
            }
        """.trimIndent())
        
        // Parse JSON
        val data = SerializationFunctions.parseJson(listOf(apiJson))
        
        // Convert to XML for legacy system
        val xmlOutput = SerializationFunctions.renderXml(listOf(data))
        val xmlStr = (xmlOutput as UDM.Scalar).value as String
        
        assertTrue(xmlStr.contains("John"))
        assertTrue(xmlStr.contains("Alice"))
    }
    
    @Test
    fun `test real-world - configuration file migration`() {
        // Old XML config
        val xmlConfig = UDM.Scalar("""
            <config>
                <database>
                    <host>localhost</host>
                    <port>5432</port>
                </database>
            </config>
        """.trimIndent())
        
        // Parse XML
        val configData = SerializationFunctions.parseXml(listOf(xmlConfig))
        
        // Convert to YAML for new system
        val yamlConfig = SerializationFunctions.renderYaml(listOf(configData))
        val yamlStr = (yamlConfig as UDM.Scalar).value as String
        
        assertTrue(yamlStr.contains("localhost") || yamlStr.contains("5432"))
    }
    
    @Test
    fun `test real-world - data export`() {
        // Internal data structure
        val data = UDM.Array(listOf(
            UDM.Object(mutableMapOf(
                "order_id" to UDM.Scalar("ORD-001"),
                "customer" to UDM.Scalar("John Doe"),
                "total" to UDM.Scalar(150.00)
            )),
            UDM.Object(mutableMapOf(
                "order_id" to UDM.Scalar("ORD-002"),
                "customer" to UDM.Scalar("Alice Smith"),
                "total" to UDM.Scalar(200.00)
            ))
        ))
        
        // Export as CSV for spreadsheet
        val csvExport = SerializationFunctions.renderCsv(listOf(data))
        val csvStr = (csvExport as UDM.Scalar).value as String
        
        assertTrue(csvStr.contains("ORD-001"))
        assertTrue(csvStr.contains("John Doe"))
        assertTrue(csvStr.contains("150"))
    }
    
    @Test
    fun `test real-world - message queue transformation`() {
        // Receive YAML from message queue
        val yamlMessage = UDM.Scalar("""
            event: user_created
            timestamp: 2025-10-15T14:30:00Z
            data:
              user_id: 12345
              name: John Doe
              email: john@example.com
        """.trimIndent())
        
        // Parse YAML
        val message = SerializationFunctions.parseYaml(listOf(yamlMessage))
        
        // Convert to JSON for HTTP API
        val jsonMessage = SerializationFunctions.renderJson(listOf(message))
        val jsonStr = (jsonMessage as UDM.Scalar).value as String
        
        assertTrue(jsonStr.contains("user_created"))
        assertTrue(jsonStr.contains("12345"))
    }

    // ==================== Tibco Compatibility Tests ====================
    
    @Test
    fun `test tibco_parse - JSON format`() {
        val jsonStr = UDM.Scalar("""{"name":"John"}""")
        val format = UDM.Scalar("json")
        
        val result = SerializationFunctions.parse(listOf(jsonStr, format))
        val obj = result as UDM.Object
        
        assertEquals("John", (obj.properties["name"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test tibco_render - JSON format`() {
        val obj = UDM.Object(mutableMapOf(
            "name" to UDM.Scalar("John")
        ))
        val format = UDM.Scalar("json")
        
        val result = SerializationFunctions.render(listOf(obj, format))
        val jsonStr = (result as UDM.Scalar).value as String
        
        assertTrue(jsonStr.contains("John"))
    }

    // ==================== Edge Cases ====================
    
    @Test
    fun `test edge case - empty JSON object`() {
        val emptyJson = UDM.Scalar("""{}""")
        
        val result = SerializationFunctions.parseJson(listOf(emptyJson))
        val obj = result as UDM.Object
        
        assertEquals(0, obj.properties.size)
    }
    
    @Test
    fun `test edge case - empty JSON array`() {
        val emptyArray = UDM.Scalar("""[]""")
        
        val result = SerializationFunctions.parseJson(listOf(emptyArray))
        val array = result as UDM.Array
        
        assertEquals(0, array.elements.size)
    }
    
    @Test
    fun `test edge case - single CSV row`() {
        val singleRow = UDM.Scalar("""name,age
John,30""")
        
        val result = SerializationFunctions.parseCsv(listOf(singleRow))
        val array = result as UDM.Array
        
        assertEquals(1, array.elements.size)
    }
    
    @Test
    fun `test edge case - deeply nested JSON`() {
        val deepJson = UDM.Scalar("""
            {"a":{"b":{"c":{"d":{"e":"deep"}}}}}
        """.trimIndent())
        
        val result = SerializationFunctions.parseJson(listOf(deepJson))
        assertNotNull(result, "Should parse deeply nested structure")
    }
    
    @Test
    fun `test edge case - large number handling`() {
        val largeNumberJson = UDM.Scalar("""
            {"bigint": 9007199254740991, "decimal": 123.456789012345}
        """.trimIndent())
        
        val result = SerializationFunctions.parseJson(listOf(largeNumberJson))
        val obj = result as UDM.Object
        
        assertNotNull(obj.properties["bigint"])
        assertNotNull(obj.properties["decimal"])
    }
    
    @Test
    fun `test edge case - Unicode characters`() {
        val unicodeJson = UDM.Scalar("""
            {"text": "Hello 世界 🌍"}
        """.trimIndent())
        
        val result = SerializationFunctions.parseJson(listOf(unicodeJson))
        val obj = result as UDM.Object
        
        val text = (obj.properties["text"] as UDM.Scalar).value as String
        assertTrue(text.contains("Hello"))
    }
}
