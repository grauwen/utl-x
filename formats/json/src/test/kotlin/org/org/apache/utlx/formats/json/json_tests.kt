package org.apache.utlx.formats.json

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.core.interpreter.RuntimeValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class JSONParserTest {
    @Test
    fun `parse null`() {
        val result = JSONParser("null").parse()
        assertTrue(result is UDM.Scalar)
        assertTrue((result as UDM.Scalar).isNull())
    }
    
    @Test
    fun `parse boolean true`() {
        val result = JSONParser("true").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(true, (result as UDM.Scalar).asBoolean())
    }
    
    @Test
    fun `parse boolean false`() {
        val result = JSONParser("false").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(false, (result as UDM.Scalar).asBoolean())
    }
    
    @Test
    fun `parse integer`() {
        val result = JSONParser("42").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(42L, (result as UDM.Scalar).value)
    }

    @Test
    fun `parse negative number`() {
        val result = JSONParser("-123").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(-123L, (result as UDM.Scalar).value)
    }

    @Test
    fun `parse zero as Long`() {
        val result = JSONParser("0").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(0L, (result as UDM.Scalar).value)
    }

    @Test
    fun `parse integer preserves Int64 precision`() {
        val result = JSONParser("9007199254740993").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(9007199254740993L, (result as UDM.Scalar).value)
    }

    @Test
    fun `parse Long MAX_VALUE as Long`() {
        val result = JSONParser("9223372036854775807").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(Long.MAX_VALUE, (result as UDM.Scalar).value)
    }

    @Test
    fun `parse beyond Long MAX_VALUE falls back to Double`() {
        val result = JSONParser("9223372036854775808").parse()
        assertTrue(result is UDM.Scalar)
        assertTrue((result as UDM.Scalar).value is Double)
    }

    @Test
    fun `parse decimal number`() {
        val result = JSONParser("3.14159").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(3.14159, (result as UDM.Scalar).value)
    }

    @Test
    fun `parse negative decimal as Double`() {
        val result = JSONParser("-3.14").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(-3.14, (result as UDM.Scalar).value)
    }

    @Test
    fun `parse scientific notation lowercase e as Double`() {
        val result = JSONParser("1.5e10").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(1.5e10, (result as UDM.Scalar).value)
    }

    @Test
    fun `parse scientific notation uppercase E as Double`() {
        val result = JSONParser("2.5E10").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(2.5e10, (result as UDM.Scalar).value)
    }

    @Test
    fun `parse integer with scientific notation as Double`() {
        // 1e5 has no decimal point but has 'e', so it must be Double
        val result = JSONParser("1e5").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(1e5, (result as UDM.Scalar).value)
    }

    @Test
    fun `parse negative scientific notation as Double`() {
        val result = JSONParser("-1.5e-3").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals(-1.5e-3, (result as UDM.Scalar).value)
    }
    
    @Test
    fun `parse simple string`() {
        val result = JSONParser("\"hello world\"").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals("hello world", (result as UDM.Scalar).asString())
    }
    
    @Test
    fun `parse string with escapes`() {
        val result = JSONParser("\"line1\\nline2\\ttab\"").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals("line1\nline2\ttab", (result as UDM.Scalar).asString())
    }
    
    @Test
    fun `parse string with unicode escape`() {
        val result = JSONParser("\"Hello \\u0041\"").parse()
        assertTrue(result is UDM.Scalar)
        assertEquals("Hello A", (result as UDM.Scalar).asString())
    }
    
    @Test
    fun `parse empty array`() {
        val result = JSONParser("[]").parse()
        assertTrue(result is UDM.Array)
        assertEquals(0, (result as UDM.Array).size())
    }
    
    @Test
    fun `parse array of numbers`() {
        val result = JSONParser("[1, 2, 3, 4, 5]").parse()
        assertTrue(result is UDM.Array)
        val arr = result as UDM.Array
        assertEquals(5, arr.size())
        assertEquals(1.0, arr.get(0)?.asScalar()?.asNumber())
        assertEquals(5.0, arr.get(4)?.asScalar()?.asNumber())
    }
    
    @Test
    fun `parse array of mixed types`() {
        val result = JSONParser("""[1, "hello", true, null]""").parse()
        assertTrue(result is UDM.Array)
        val arr = result as UDM.Array
        assertEquals(4, arr.size())
        assertEquals(1.0, arr.get(0)?.asScalar()?.asNumber())
        assertEquals("hello", arr.get(1)?.asScalar()?.asString())
        assertEquals(true, arr.get(2)?.asScalar()?.asBoolean())
        assertTrue(arr.get(3)?.asScalar()?.isNull() == true)
    }
    
    @Test
    fun `parse empty object`() {
        val result = JSONParser("{}").parse()
        assertTrue(result is UDM.Object)
        assertEquals(0, (result as UDM.Object).keys().size)
    }
    
    @Test
    fun `parse simple object`() {
        val result = JSONParser("""{"name": "Alice", "age": 30}""").parse()
        assertTrue(result is UDM.Object)
        val obj = result as UDM.Object
        assertEquals("Alice", obj.get("name")?.asScalar()?.asString())
        assertEquals(30.0, obj.get("age")?.asScalar()?.asNumber())
    }
    
    @Test
    fun `parse nested object`() {
        val json = """
            {
              "person": {
                "name": "Bob",
                "address": {
                  "city": "New York",
                  "zip": "10001"
                }
              }
            }
        """.trimIndent()
        
        val result = JSONParser(json).parse() as UDM.Object
        val person = result.get("person")?.asObject()
        assertNotNull(person)
        assertEquals("Bob", person?.get("name")?.asScalar()?.asString())
        
        val address = person?.get("address")?.asObject()
        assertNotNull(address)
        assertEquals("New York", address?.get("city")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse array of objects`() {
        val json = """
            [
              {"id": 1, "name": "Alice"},
              {"id": 2, "name": "Bob"}
            ]
        """.trimIndent()
        
        val result = JSONParser(json).parse() as UDM.Array
        assertEquals(2, result.size())
        
        val first = result.get(0)?.asObject()
        assertEquals(1.0, first?.get("id")?.asScalar()?.asNumber())
        assertEquals("Alice", first?.get("name")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse complex nested structure`() {
        val json = """
            {
              "order": {
                "id": "ORD-001",
                "customer": {
                  "name": "Charlie",
                  "email": "charlie@example.com"
                },
                "items": [
                  {"sku": "WIDGET-001", "price": 29.99, "quantity": 2},
                  {"sku": "GADGET-002", "price": 49.99, "quantity": 1}
                ],
                "total": 109.97
              }
            }
        """.trimIndent()
        
        val result = JSONParser(json).parse() as UDM.Object
        val order = result.get("order")?.asObject()
        assertNotNull(order)
        
        assertEquals("ORD-001", order?.get("id")?.asScalar()?.asString())
        
        val customer = order?.get("customer")?.asObject()
        assertEquals("Charlie", customer?.get("name")?.asScalar()?.asString())
        
        val items = order?.get("items")?.asArray()
        assertEquals(2, items?.size())
        
        assertEquals(109.97, order?.get("total")?.asScalar()?.asNumber())
    }
    
    @Test
    fun `parse with whitespace`() {
        val json = """
            
            {
              "key"  :   "value"  
            }
            
        """.trimIndent()
        
        val result = JSONParser(json).parse() as UDM.Object
        assertEquals("value", result.get("key")?.asScalar()?.asString())
    }
    
    @Test
    fun `error on invalid JSON`() {
        assertThrows<JSONParseException> {
            JSONParser("{invalid}").parse()
        }
    }
    
    @Test
    fun `error on unterminated string`() {
        assertThrows<JSONParseException> {
            JSONParser("\"unterminated").parse()
        }
    }
    
    @Test
    fun `error on trailing comma in object`() {
        assertThrows<JSONParseException> {
            JSONParser("""{"key": "value",}""").parse()
        }
    }
    
    @Test
    fun `error on missing colon`() {
        assertThrows<JSONParseException> {
            JSONParser("""{"key" "value"}""").parse()
        }
    }
}

class JSONSerializerTest {
    @Test
    fun `serialize null`() {
        val result = JSONSerializer(prettyPrint = false).serialize(UDM.Scalar.nullValue())
        assertEquals("null", result)
    }
    
    @Test
    fun `serialize boolean`() {
        val trueResult = JSONSerializer(prettyPrint = false).serialize(UDM.Scalar.boolean(true))
        assertEquals("true", trueResult)
        
        val falseResult = JSONSerializer(prettyPrint = false).serialize(UDM.Scalar.boolean(false))
        assertEquals("false", falseResult)
    }
    
    @Test
    fun `serialize number`() {
        val intResult = JSONSerializer(prettyPrint = false).serialize(UDM.Scalar.number(42))
        assertEquals("42", intResult)

        val decimalResult = JSONSerializer(prettyPrint = false).serialize(UDM.Scalar.number(3.14))
        assertEquals("3.14", decimalResult)
    }

    @Test
    fun `serialize Long number`() {
        val result = JSONSerializer(prettyPrint = false).serialize(UDM.Scalar.number(42L))
        assertEquals("42", result)
    }

    @Test
    fun `serialize large Long preserves precision`() {
        val result = JSONSerializer(prettyPrint = false).serialize(UDM.Scalar.number(9007199254740993L))
        assertEquals("9007199254740993", result)
    }

    @Test
    fun `serialize whole-number Double writes as integer`() {
        // The compensating hack: Double 42.0 is written as "42"
        val result = JSONSerializer(prettyPrint = false).serialize(UDM.Scalar.number(42.0))
        assertEquals("42", result)
    }
    
    @Test
    fun `serialize string`() {
        val result = JSONSerializer(prettyPrint = false).serialize(UDM.Scalar.string("hello"))
        assertEquals("\"hello\"", result)
    }
    
    @Test
    fun `serialize string with escapes`() {
        val result = JSONSerializer(prettyPrint = false).serialize(UDM.Scalar.string("line1\nline2\ttab"))
        assertEquals("\"line1\\nline2\\ttab\"", result)
    }
    
    @Test
    fun `serialize empty array`() {
        val result = JSONSerializer(prettyPrint = false).serialize(UDM.Array.empty())
        assertEquals("[]", result)
    }
    
    @Test
    fun `serialize array`() {
        val arr = UDM.Array.of(
            UDM.Scalar.number(1),
            UDM.Scalar.number(2),
            UDM.Scalar.number(3)
        )
        val result = JSONSerializer(prettyPrint = false).serialize(arr)
        assertEquals("[1,2,3]", result)
    }
    
    @Test
    fun `serialize empty object`() {
        val result = JSONSerializer(prettyPrint = false).serialize(UDM.Object.empty())
        assertEquals("{}", result)
    }
    
    @Test
    fun `serialize simple object`() {
        val obj = UDM.Object.of(
            "name" to UDM.Scalar.string("Alice"),
            "age" to UDM.Scalar.number(30)
        )
        val result = JSONSerializer(prettyPrint = false).serialize(obj)
        // Order may vary, check both possibilities
        assertTrue(result.contains("\"name\":\"Alice\""))
        assertTrue(result.contains("\"age\":30"))
    }
    
    @Test
    fun `serialize object with attributes`() {
        val obj = UDM.Object.withAttributes(
            properties = mapOf("value" to UDM.Scalar.number(100)),
            attributes = mapOf("id" to "123")
        )
        val result = JSONSerializer(prettyPrint = false).serialize(obj)
        assertTrue(result.contains("\"@id\":\"123\""))
        assertTrue(result.contains("\"value\":100"))
    }
    
    @Test
    fun `serialize nested structure`() {
        val obj = UDM.Object.of(
            "person" to UDM.Object.of(
                "name" to UDM.Scalar.string("Bob"),
                "age" to UDM.Scalar.number(25)
            )
        )
        val result = JSONSerializer(prettyPrint = false).serialize(obj)
        assertTrue(result.contains("\"person\":{"))
        assertTrue(result.contains("\"name\":\"Bob\""))
    }
    
    @Test
    fun `serialize with pretty print`() {
        val obj = UDM.Object.of(
            "name" to UDM.Scalar.string("Alice"),
            "age" to UDM.Scalar.number(30)
        )
        val result = JSONSerializer(prettyPrint = true).serialize(obj)
        assertTrue(result.contains("\n"))
        assertTrue(result.contains("  "))
    }
    
    @Test
    fun `serialize RuntimeValue`() {
        val obj = RuntimeValue.ObjectValue(mapOf(
            "message" to RuntimeValue.StringValue("Hello"),
            "count" to RuntimeValue.NumberValue(42.0)
        ))
        val result = JSONSerializer(prettyPrint = false).serialize(obj)
        assertTrue(result.contains("\"message\":\"Hello\""))
        assertTrue(result.contains("\"count\":42"))
    }
    
    @Test
    fun `round trip - parse and serialize`() {
        val original = """{"name":"Alice","age":30,"active":true}"""
        val parsed = JSONParser(original).parse()
        val serialized = JSONSerializer(prettyPrint = false).serialize(parsed)

        // Parse again to compare structures
        val reparsed = JSONParser(serialized).parse()

        assertTrue(parsed is UDM.Object)
        assertTrue(reparsed is UDM.Object)

        val obj1 = parsed as UDM.Object
        val obj2 = reparsed as UDM.Object

        assertEquals(obj1.get("name")?.asScalar()?.asString(),
                     obj2.get("name")?.asScalar()?.asString())
        assertEquals(obj1.get("age")?.asScalar()?.asNumber(),
                     obj2.get("age")?.asScalar()?.asNumber())
    }

    @Test
    fun `round trip preserves positive integer as Long`() {
        val serialized = roundTrip("42")
        assertEquals("42", serialized)
        assertEquals(42L, JSONParser(serialized).parse().asScalar()?.value)
    }

    @Test
    fun `round trip preserves negative integer as Long`() {
        val serialized = roundTrip("-123")
        assertEquals("-123", serialized)
        assertEquals(-123L, JSONParser(serialized).parse().asScalar()?.value)
    }

    @Test
    fun `round trip preserves zero as Long`() {
        val serialized = roundTrip("0")
        assertEquals("0", serialized)
        assertEquals(0L, JSONParser(serialized).parse().asScalar()?.value)
    }

    @Test
    fun `round trip preserves large Int64`() {
        val serialized = roundTrip("9007199254740993")
        assertEquals("9007199254740993", serialized)
        assertEquals(9007199254740993L, JSONParser(serialized).parse().asScalar()?.value)
    }

    @Test
    fun `round trip preserves decimal as Double`() {
        val serialized = roundTrip("3.14")
        assertEquals("3.14", serialized)
        assertEquals(3.14, JSONParser(serialized).parse().asScalar()?.value)
    }

    @Test
    fun `round trip preserves negative decimal as Double`() {
        val serialized = roundTrip("-0.5")
        assertEquals("-0.5", serialized)
        assertEquals(-0.5, JSONParser(serialized).parse().asScalar()?.value)
    }

    @Test
    fun `round trip whole-number scientific notation narrows to Long`() {
        // 1.5e10 = 15000000000.0 (whole number Double)
        // Serializer writes "15000000000" (no dot) → reparsed as Long
        val parsed = JSONParser("1.5e10").parse() as UDM.Scalar
        assertTrue(parsed.value is Double)
        val serialized = JSONSerializer(prettyPrint = false).serialize(parsed)
        assertEquals("15000000000", serialized)
        val reparsed = JSONParser(serialized).parse() as UDM.Scalar
        assertEquals(15000000000L, reparsed.value)
    }

    @Test
    fun `round trip fractional scientific notation stays Double`() {
        // 2.5E-1 = 0.25 (not a whole number) → stays Double
        val parsed = JSONParser("2.5E-1").parse() as UDM.Scalar
        assertTrue(parsed.value is Double)
        val serialized = JSONSerializer(prettyPrint = false).serialize(parsed)
        assertEquals("0.25", serialized)
        val reparsed = JSONParser(serialized).parse() as UDM.Scalar
        assertTrue(reparsed.value is Double)
        assertEquals(0.25, reparsed.value)
    }

    @Test
    fun `round trip integer scientific notation`() {
        // 1e5 has no decimal but has 'e' → Double → serialized → reparsed
        val parsed = JSONParser("1e5").parse() as UDM.Scalar
        assertTrue(parsed.value is Double)
        val serialized = JSONSerializer(prettyPrint = false).serialize(parsed)
        // Double 100000.0 is a whole number → serializer writes "100000"
        assertEquals("100000", serialized)
        // Reparsed as Long (no dot, no e in serialized form)
        val reparsed = JSONParser(serialized).parse() as UDM.Scalar
        assertEquals(100000L, reparsed.value)
    }

    @Test
    fun `round trip negative scientific notation`() {
        val parsed = JSONParser("-1.5e-3").parse() as UDM.Scalar
        val serialized = JSONSerializer(prettyPrint = false).serialize(parsed)
        val reparsed = JSONParser(serialized).parse() as UDM.Scalar
        assertTrue(reparsed.value is Double)
        assertEquals(-0.0015, reparsed.value)
    }

    @Test
    fun `round trip mixed object preserves types`() {
        val original = """{"count":42,"price":9.99,"big":9007199254740993,"neg":-7,"zero":0}"""
        val parsed = JSONParser(original).parse() as UDM.Object
        val serialized = JSONSerializer(prettyPrint = false).serialize(parsed)
        val reparsed = JSONParser(serialized).parse() as UDM.Object

        assertEquals(42L, reparsed.get("count")?.asScalar()?.value)
        assertEquals(9.99, reparsed.get("price")?.asScalar()?.value)
        assertEquals(9007199254740993L, reparsed.get("big")?.asScalar()?.value)
        assertEquals(-7L, reparsed.get("neg")?.asScalar()?.value)
        assertEquals(0L, reparsed.get("zero")?.asScalar()?.value)
    }

    private fun roundTrip(json: String): String {
        val parsed = JSONParser(json).parse()
        return JSONSerializer(prettyPrint = false).serialize(parsed)
    }

    @Test
    fun `UDM accessors work for Long-backed scalar`() {
        val parsed = JSONParser("42").parse() as UDM.Scalar

        // Raw value is Long
        assertEquals(42L, parsed.value)
        assertTrue(parsed.value is Long)

        // asNumber() converts to Double
        assertEquals(42.0, parsed.asNumber())

        // asString() gives "42" not "42.0"
        assertEquals("42", parsed.asString())

        // isNumber() recognizes Long as Number
        assertTrue(parsed.isNumber())
    }

    @Test
    fun `UDM accessors work for Double-backed scalar`() {
        val parsed = JSONParser("3.14").parse() as UDM.Scalar

        assertEquals(3.14, parsed.value)
        assertTrue(parsed.value is Double)
        assertEquals(3.14, parsed.asNumber())
        assertEquals("3.14", parsed.asString())
        assertTrue(parsed.isNumber())
    }
}

class JSONConvenienceTest {
    @Test
    fun `JSON parse convenience`() {
        val result = JSON.parse("""{"key": "value"}""")
        assertTrue(result is UDM.Object)
        assertEquals("value", (result as UDM.Object).get("key")?.asScalar()?.asString())
    }
    
    @Test
    fun `JSON stringify convenience`() {
        val obj = UDM.Object.of("key" to UDM.Scalar.string("value"))
        val result = JSON.stringify(obj)
        assertTrue(result.contains("\"key\""))
        assertTrue(result.contains("\"value\""))
    }
    
    @Test
    fun `JSON stringify compact`() {
        val obj = UDM.Object.of("key" to UDM.Scalar.string("value"))
        val result = JSON.stringifyCompact(obj)
        assertFalse(result.contains("\n"))
    }
    
    @Test
    fun `full round trip with complex data`() {
        val original = """
            {
              "order": {
                "id": "ORD-123",
                "items": [
                  {"name": "Widget", "price": 29.99},
                  {"name": "Gadget", "price": 49.99}
                ],
                "total": 79.98
              }
            }
        """.trimIndent()
        
        val parsed = JSON.parse(original)
        val serialized = JSON.stringify(parsed)
        val reparsed = JSON.parse(serialized)
        
        // Verify structure is preserved
        val order1 = (parsed as UDM.Object).get("order")?.asObject()
        val order2 = (reparsed as UDM.Object).get("order")?.asObject()
        
        assertEquals(order1?.get("id")?.asScalar()?.asString(),
                     order2?.get("id")?.asScalar()?.asString())
        assertEquals(order1?.get("total")?.asScalar()?.asNumber(),
                     order2?.get("total")?.asScalar()?.asNumber())
    }
}
