package org.apache.utlx.formats.csv

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CSVParserTest {
    @Test
    fun `parse simple CSV with headers`() {
        val csv = """
            Name,Age,Active
            Alice,30,true
            Bob,25,false
        """.trimIndent()
        
        val result = CSV.parse(csv) as UDM.Array
        assertEquals(2, result.size())
        
        val first = result.get(0)?.asObject()
        assertEquals("Alice", first?.get("Name")?.asScalar()?.asString())
        assertEquals(30.0, first?.get("Age")?.asScalar()?.asNumber())
        assertEquals(true, first?.get("Active")?.asScalar()?.asBoolean())
    }
    
    @Test
    fun `parse CSV without headers`() {
        val csv = """
            Alice,30,true
            Bob,25,false
        """.trimIndent()
        
        val result = CSV.parse(csv, hasHeaders = false) as UDM.Array
        assertEquals(2, result.size())
        
        val first = result.get(0)?.asArray()
        assertEquals("Alice", first?.get(0)?.asScalar()?.asString())
        assertEquals(30.0, first?.get(1)?.asScalar()?.asNumber())
    }
    
    @Test
    fun `parse empty CSV`() {
        val result = CSV.parse("") as UDM.Array
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun `parse CSV with quoted fields`() {
        val csv = """
            Name,Description
            "Alice, Jr.","A person named ""Alice"""
            Bob,"Simple description"
        """.trimIndent()
        
        val result = CSV.parse(csv) as UDM.Array
        
        val first = result.get(0)?.asObject()
        assertEquals("Alice, Jr.", first?.get("Name")?.asScalar()?.asString())
        assertEquals("A person named \"Alice\"", first?.get("Description")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse CSV with embedded newlines in quoted fields`() {
        val csv = "Name,Bio\n\"Alice\",\"Line 1\nLine 2\"\nBob,\"Single line\""
        
        val result = CSV.parse(csv) as UDM.Array
        
        val first = result.get(0)?.asObject()
        assertEquals("Line 1\nLine 2", first?.get("Bio")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse TSV (tab-separated)`() {
        val tsv = "Name\tAge\tCity\nAlice\t30\tNY\nBob\t25\tLA"
        
        val result = CSV.parseTSV(tsv) as UDM.Array
        assertEquals(2, result.size())
        
        val first = result.get(0)?.asObject()
        assertEquals("Alice", first?.get("Name")?.asScalar()?.asString())
        assertEquals("NY", first?.get("City")?.asScalar()?.asString())
    }
    
    @Test
    fun `parse CSV with semicolon delimiter`() {
        val csv = "Name;Age;Score\nAlice;30;95.5\nBob;25;87.3"
        
        val result = CSV.parse(csv, dialect = CSVDialect.SEMICOLON) as UDM.Array
        
        val first = result.get(0)?.asObject()
        assertEquals("Alice", first?.get("Name")?.asScalar()?.asString())
        assertEquals(95.5, first?.get("Score")?.asScalar()?.asNumber())
    }
    
    @Test
    fun `parse CSV with numeric values`() {
        val csv = """
            Name,Price,Quantity
            Widget,29.99,10
            Gadget,49.99,5
        """.trimIndent()
        
        val result = CSV.parse(csv) as UDM.Array
        
        val first = result.get(0)?.asObject()
        assertEquals(29.99, first?.get("Price")?.asScalar()?.asNumber())
        assertEquals(10.0, first?.get("Quantity")?.asScalar()?.asNumber())
    }
    
    @Test
    fun `parse CSV with boolean values`() {
        val csv = """
            Name,Active,Premium
            Alice,true,false
            Bob,false,true
        """.trimIndent()
        
        val result = CSV.parse(csv) as UDM.Array
        
        val first = result.get(0)?.asObject()
        assertEquals(true, first?.get("Active")?.asScalar()?.asBoolean())
        assertEquals(false, first?.get("Premium")?.asScalar()?.asBoolean())
    }
    
    @Test
    fun `parse CSV with null values`() {
        val csv = """
            Name,MiddleName,Age
            Alice,,30
            Bob,null,25
        """.trimIndent()
        
        val result = CSV.parse(csv) as UDM.Array
        
        val first = result.get(0)?.asObject()
        assertTrue(first?.get("MiddleName")?.asScalar()?.isNull() == true)
    }
    
    @Test
    fun `parse CSV with whitespace`() {
        val csv = """
            Name , Age , City
            Alice , 30 , New York
            Bob , 25 , Los Angeles
        """.trimIndent()
        
        val result = CSV.parse(csv) as UDM.Array
        
        val first = result.get(0)?.asObject()
        // Headers and values should be trimmed
        assertEquals("Alice", first?.get("Name")?.asScalar()?.asString())
        assertEquals(30.0, first?.get("Age")?.asScalar()?.asNumber())
    }
    
    @Test
    fun `parse single column CSV`() {
        val csv = """
            Names
            Alice
            Bob
            Charlie
        """.trimIndent()
        
        val result = CSV.parse(csv) as UDM.Array
        assertEquals(3, result.size())
        
        val first = result.get(0)?.asObject()
        assertEquals("Alice", first?.get("Names")?.asScalar()?.asString())
    }
}

class CSVSerializerTest {
    @Test
    fun `serialize simple array of objects`() {
        val data = UDM.Array(listOf(
            UDM.Object.of(
                "Name" to UDM.Scalar.string("Alice"),
                "Age" to UDM.Scalar.number(30)
            ),
            UDM.Object.of(
                "Name" to UDM.Scalar.string("Bob"),
                "Age" to UDM.Scalar.number(25)
            )
        ))
        
        val csv = CSVFormat.stringify(data)
        
        assertTrue(csv.contains("Name"))
        assertTrue(csv.contains("Alice"))
        assertTrue(csv.contains("30"))
    }
    
    @Test
    fun `serialize without headers`() {
        val data = UDM.Array(listOf(
            UDM.Object.of(
                "Name" to UDM.Scalar.string("Alice"),
                "Age" to UDM.Scalar.number(30)
            )
        ))
        
        val csv = CSVFormat.stringifyWithoutHeaders(data)
        
        assertFalse(csv.contains("Name"))
        assertTrue(csv.contains("Alice"))
    }
    
    @Test
    fun `serialize array of arrays`() {
        val data = UDM.Array(listOf(
            UDM.Array.of(
                UDM.Scalar.string("Alice"),
                UDM.Scalar.number(30)
            ),
            UDM.Array.of(
                UDM.Scalar.string("Bob"),
                UDM.Scalar.number(25)
            )
        ))
        
        val csv = CSVSerializer(includeHeaders = false).serialize(data)
        
        assertTrue(csv.contains("Alice"))
        assertTrue(csv.contains("30"))
    }
    
    @Test
    fun `serialize field with comma - should be quoted`() {
        val data = UDM.Array(listOf(
            UDM.Object.of(
                "Name" to UDM.Scalar.string("Smith, John"),
                "Age" to UDM.Scalar.number(30)
            )
        ))
        
        val csv = CSVFormat.stringify(data)
        
        assertTrue(csv.contains("\"Smith, John\""))
    }
    
    @Test
    fun `serialize field with quotes - should be escaped`() {
        val data = UDM.Array(listOf(
            UDM.Object.of(
                "Name" to UDM.Scalar.string("Alice \"Wonder\" Smith")
            )
        ))
        
        val csv = CSVFormat.stringify(data)
        
        assertTrue(csv.contains("\"Alice \"\"Wonder\"\" Smith\""))
    }
    
    @Test
    fun `serialize to TSV`() {
        val data = UDM.Array(listOf(
            UDM.Object.of(
                "Name" to UDM.Scalar.string("Alice"),
                "Age" to UDM.Scalar.number(30)
            )
        ))
        
        val tsv = CSVFormat.stringifyTSV(data)
        
        assertTrue(tsv.contains("\t"))
        assertFalse(tsv.contains(","))
    }
    
    @Test
    fun `serialize with semicolon delimiter`() {
        val data = UDM.Array(listOf(
            UDM.Object.of(
                "Name" to UDM.Scalar.string("Alice"),
                "Age" to UDM.Scalar.number(30)
            )
        ))
        
        val csv = CSVFormat.stringify(data, CSVDialect.SEMICOLON)
        
        assertTrue(csv.contains(";"))
    }
    
    @Test
    fun `serialize numbers correctly`() {
        val data = UDM.Array(listOf(
            UDM.Object.of(
                "Integer" to UDM.Scalar.number(42),
                "Decimal" to UDM.Scalar.number(3.14)
            )
        ))
        
        val csv = CSVFormat.stringify(data)
        
        assertTrue(csv.contains("42"))
        assertTrue(csv.contains("3.14"))
    }
    
    @Test
    fun `serialize booleans`() {
        val data = UDM.Array(listOf(
            UDM.Object.of(
                "Active" to UDM.Scalar.boolean(true),
                "Premium" to UDM.Scalar.boolean(false)
            )
        ))
        
        val csv = CSVFormat.stringify(data)
        
        assertTrue(csv.contains("true"))
        assertTrue(csv.contains("false"))
    }
    
    @Test
    fun `serialize null values as empty`() {
        val data = UDM.Array(listOf(
            UDM.Object.of(
                "Name" to UDM.Scalar.string("Alice"),
                "MiddleName" to UDM.Scalar.nullValue()
            )
        ))
        
        val csv = CSVFormat.stringify(data)
        val lines = csv.lines()
        
        // Second line should have empty field for MiddleName
        assertTrue(lines[1].contains("Alice,"))
    }
    
    @Test
    fun `round trip - parse and serialize`() {
        val original = """
            Name,Age,Active
            Alice,30,true
            Bob,25,false
        """.trimIndent()
        
        val parsed = CSV.parse(original)
        val serialized = CSVFormat.stringify(parsed)
        val reparsed = CSV.parse(serialized)
        
        // Compare structures
        val arr1 = parsed as UDM.Array
        val arr2 = reparsed as UDM.Array
        
        assertEquals(arr1.size(), arr2.size())
        
        val first1 = arr1.get(0)?.asObject()
        val first2 = arr2.get(0)?.asObject()
        
        assertEquals(
            first1?.get("Name")?.asScalar()?.asString(),
            first2?.get("Name")?.asScalar()?.asString()
        )
    }
}

class CSVIntegrationTest {
    @Test
    fun `parse real-world CSV - sales data`() {
        val csv = """
            Date,Product,Quantity,Price,Total
            2025-01-01,Widget,10,25.00,250.00
            2025-01-02,Gadget,5,50.00,250.00
            2025-01-03,Tool,8,30.00,240.00
        """.trimIndent()
        
        val result = CSV.parse(csv) as UDM.Array
        assertEquals(3, result.size())
        
        val first = result.get(0)?.asObject()
        assertEquals("Widget", first?.get("Product")?.asScalar()?.asString())
        assertEquals(10.0, first?.get("Quantity")?.asScalar()?.asNumber())
    }
    
    @Test
    fun `parse CSV with complex quoted data`() {
        val csv = """
            Name,Address,Notes
            "Alice","123 Main St, Apt 4","Said ""Hello"" to everyone"
            "Bob","456 Oak Ave","Simple note"
        """.trimIndent()
        
        val result = CSV.parse(csv) as UDM.Array
        
        val first = result.get(0)?.asObject()
        assertEquals("123 Main St, Apt 4", first?.get("Address")?.asScalar()?.asString())
        assertEquals("Said \"Hello\" to everyone", first?.get("Notes")?.asScalar()?.asString())
    }
    
    @Test
    fun `serialize and parse maintains data integrity`() {
        val original = UDM.Array(listOf(
            UDM.Object.of(
                "ID" to UDM.Scalar.number(1),
                "Name" to UDM.Scalar.string("Test, Data"),
                "Score" to UDM.Scalar.number(95.5),
                "Pass" to UDM.Scalar.boolean(true)
            )
        ))
        
        val csv = CSVFormat.stringify(original)
        val parsed = CSV.parse(csv) as UDM.Array
        val obj = parsed.get(0)?.asObject()
        
        assertEquals(1.0, obj?.get("ID")?.asScalar()?.asNumber())
        assertEquals("Test, Data", obj?.get("Name")?.asScalar()?.asString())
        assertEquals(95.5, obj?.get("Score")?.asScalar()?.asNumber())
        assertEquals(true, obj?.get("Pass")?.asScalar()?.asBoolean())
    }
}
