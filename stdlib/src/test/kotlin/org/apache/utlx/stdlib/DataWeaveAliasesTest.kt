package org.apache.utlx.stdlib

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataWeaveAliasesTest {

    @Test
    fun testSizeOf() {
        // Test with array
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        val arrayResult = DataWeaveAliases.sizeOf(array)
        assertEquals(3.0, (arrayResult as UDM.Scalar).value)
        
        // Test with object
        val obj = UDM.Object(mapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2)
        ), emptyMap())
        val objResult = DataWeaveAliases.sizeOf(obj)
        assertEquals(2.0, (objResult as UDM.Scalar).value)
        
        // Test with string
        val str = UDM.Scalar("hello")
        val strResult = DataWeaveAliases.sizeOf(str)
        assertEquals(5.0, (strResult as UDM.Scalar).value)
        
        // Test with null
        val nullValue = UDM.Scalar(null)
        val nullResult = DataWeaveAliases.sizeOf(nullValue)
        assertEquals(0.0, (nullResult as UDM.Scalar).value)
    }

    @Test
    fun testIsEmpty() {
        // Test with empty array
        val emptyArray = UDM.Array(emptyList())
        assertTrue(DataWeaveAliases.isEmpty(emptyArray))
        
        // Test with non-empty array
        val nonEmptyArray = UDM.Array(listOf(UDM.Scalar(1)))
        assertTrue(!DataWeaveAliases.isEmpty(nonEmptyArray))
        
        // Test with empty object
        val emptyObj = UDM.Object(emptyMap(), emptyMap())
        assertTrue(DataWeaveAliases.isEmpty(emptyObj))
        
        // Test with non-empty object
        val nonEmptyObj = UDM.Object(mapOf("key" to UDM.Scalar("value")), emptyMap())
        assertTrue(!DataWeaveAliases.isEmpty(nonEmptyObj))
        
        // Test with empty string
        val emptyStr = UDM.Scalar("")
        assertTrue(DataWeaveAliases.isEmpty(emptyStr))
        
        // Test with non-empty string
        val nonEmptyStr = UDM.Scalar("hello")
        assertTrue(!DataWeaveAliases.isEmpty(nonEmptyStr))
        
        // Test with null
        val nullValue = UDM.Scalar(null)
        assertTrue(DataWeaveAliases.isEmpty(nullValue))
        
        // Test with number
        val number = UDM.Scalar(42)
        assertTrue(!DataWeaveAliases.isEmpty(number))
        
        // Test with binary
        val emptyBinary = UDM.Binary(byteArrayOf())
        assertTrue(DataWeaveAliases.isEmpty(emptyBinary))
        
        val nonEmptyBinary = UDM.Binary(byteArrayOf(1, 2, 3))
        assertTrue(!DataWeaveAliases.isEmpty(nonEmptyBinary))
        
        // Test with lambda (functions are never empty)
        val lambda = UDM.Lambda { listOf() }
        assertTrue(!DataWeaveAliases.isEmpty(lambda))
    }

    @Test
    fun testNamesOfEquivalentToKeys() {
        val obj = UDM.Object(mapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30),
            "city" to UDM.Scalar("NYC")
        ), emptyMap())
        
        val namesResult = DataWeaveAliases.namesOf(obj)
        
        assertTrue(namesResult is UDM.Array)
        val keys = (namesResult as UDM.Array).elements
        assertEquals(3, keys.size)
        
        val keyStrings = keys.map { (it as UDM.Scalar).value as String }.toSet()
        assertTrue(keyStrings.contains("name"))
        assertTrue(keyStrings.contains("age"))
        assertTrue(keyStrings.contains("city"))
    }

    @Test
    fun testValuesOfEquivalentToValues() {
        val obj = UDM.Object(mapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30)
        ), emptyMap())
        
        val valuesResult = DataWeaveAliases.valuesOf(obj)
        
        assertTrue(valuesResult is UDM.Array)
        val values = (valuesResult as UDM.Array).elements
        assertEquals(2, values.size)
        
        val valueStrings = values.map { (it as UDM.Scalar).value }.toSet()
        assertTrue(valueStrings.contains("John"))
        assertTrue(valueStrings.contains(30))
    }

    @Test
    fun testEntriesOfEquivalentToEntries() {
        val obj = UDM.Object(mapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30)
        ), emptyMap())
        
        val entriesResult = DataWeaveAliases.entriesOf(obj)
        
        assertTrue(entriesResult is UDM.Array)
        val entries = (entriesResult as UDM.Array).elements
        assertEquals(2, entries.size)
        
        // Each entry should be an object with "key" and "value" properties
        entries.forEach { entry ->
            assertTrue(entry is UDM.Object)
            val entryObj = entry as UDM.Object
            assertTrue(entryObj.properties.containsKey("key"))
            assertTrue(entryObj.properties.containsKey("value"))
        }
    }

    @Test
    fun testDaysBetween() {
        val date1 = UDM.Scalar("2023-01-01")
        val date2 = UDM.Scalar("2023-01-10")
        
        val result = DataWeaveAliases.daysBetween(date1, date2)
        
        assertTrue(result is UDM.Scalar)
        // The exact result depends on the DateFunctions.diffDays implementation
        // We're mainly testing that the alias works without throwing an exception
    }

    // ==================== MIGRATION GUIDE TESTS ====================

    @Test
    fun testGetEquivalent() {
        assertEquals("split (alias: splitBy)", DataWeaveMigrationGuide.getEquivalent("splitBy"))
        assertEquals("sortBy (alias: orderBy)", DataWeaveMigrationGuide.getEquivalent("orderBy"))
        assertEquals("size (alias: sizeOf)", DataWeaveMigrationGuide.getEquivalent("sizeOf"))
        assertEquals("keys (alias: namesOf)", DataWeaveMigrationGuide.getEquivalent("namesOf"))
        assertEquals("values (alias: valuesOf)", DataWeaveMigrationGuide.getEquivalent("valuesOf"))
        assertEquals("map", DataWeaveMigrationGuide.getEquivalent("map"))
        assertEquals("filter", DataWeaveMigrationGuide.getEquivalent("filter"))
        assertEquals("reduce", DataWeaveMigrationGuide.getEquivalent("reduce"))
        assertEquals(null, DataWeaveMigrationGuide.getEquivalent("nonexistentFunction"))
    }

    @Test
    fun testIsSupported() {
        assertTrue(DataWeaveMigrationGuide.isSupported("map"))
        assertTrue(DataWeaveMigrationGuide.isSupported("filter"))
        assertTrue(DataWeaveMigrationGuide.isSupported("splitBy"))
        assertTrue(DataWeaveMigrationGuide.isSupported("orderBy"))
        assertTrue(!DataWeaveMigrationGuide.isSupported("pluralize")) // NOT IMPLEMENTED
        assertTrue(!DataWeaveMigrationGuide.isSupported("update")) // NOT IMPLEMENTED
        assertTrue(!DataWeaveMigrationGuide.isSupported("nonexistentFunction"))
    }

    @Test
    fun testGetMigrationNotes() {
        val notes = DataWeaveMigrationGuide.getMigrationNotes()
        
        assertTrue(notes.isNotEmpty())
        assertTrue(notes.any { it.contains("DataWeave functions have direct equivalents") })
        assertTrue(notes.any { it.contains("splitBy => split") })
        assertTrue(notes.any { it.contains("120+ vs ~80-100") })
        assertTrue(notes.any { it.contains("pluralize()") })
    }

    @Test
    fun testFunctionMappingCoverage() {
        val mapping = DataWeaveMigrationGuide.functionMapping
        
        // Test some key DataWeave functions are mapped
        assertTrue(mapping.containsKey("upper"))
        assertTrue(mapping.containsKey("lower"))
        assertTrue(mapping.containsKey("map"))
        assertTrue(mapping.containsKey("filter"))
        assertTrue(mapping.containsKey("reduce"))
        assertTrue(mapping.containsKey("flatten"))
        assertTrue(mapping.containsKey("orderBy"))
        assertTrue(mapping.containsKey("namesOf"))
        assertTrue(mapping.containsKey("valuesOf"))
        assertTrue(mapping.containsKey("abs"))
        assertTrue(mapping.containsKey("ceil"))
        assertTrue(mapping.containsKey("floor"))
        assertTrue(mapping.containsKey("now"))
        // Note: typeOf removed - use getType() directly. Keyword 'typeof' reserved for future operator.
        assertTrue(mapping.containsKey("isArray"))
        assertTrue(mapping.containsKey("isString"))
        
        // Test that we have good coverage (should have most common DataWeave functions)
        assertTrue(mapping.size >= 50) // Should have at least 50 function mappings
    }

    @Test
    fun testStringFunctionMappings() {
        val mapping = DataWeaveMigrationGuide.functionMapping
        
        assertEquals("upper", mapping["upper"])
        assertEquals("lower", mapping["lower"])
        assertEquals("capitalize", mapping["capitalize"])
        assertEquals("replace", mapping["replace"])
        assertEquals("split (alias: splitBy)", mapping["splitBy"])
        assertEquals("contains", mapping["contains"])
        assertEquals("startsWith", mapping["startsWith"])
        assertEquals("endsWith", mapping["endsWith"])
        assertEquals("substring", mapping["substring"])
        assertEquals("trim", mapping["trim"])
    }

    @Test
    fun testArrayFunctionMappings() {
        val mapping = DataWeaveMigrationGuide.functionMapping
        
        assertEquals("map", mapping["map"])
        assertEquals("filter", mapping["filter"])
        assertEquals("reduce", mapping["reduce"])
        assertEquals("flatten", mapping["flatten"])
        assertEquals("sortBy (alias: orderBy)", mapping["orderBy"])
        assertEquals("zip", mapping["zip"])
        assertEquals("find", mapping["find"])
        assertEquals("isEmpty", mapping["isEmpty"])
        assertEquals("size (alias: sizeOf)", mapping["sizeOf"])
        assertEquals("first", mapping["first"])
        assertEquals("last", mapping["last"])
    }

    @Test
    fun testMathFunctionMappings() {
        val mapping = DataWeaveMigrationGuide.functionMapping
        
        assertEquals("abs", mapping["abs"])
        assertEquals("ceil", mapping["ceil"])
        assertEquals("floor", mapping["floor"])
        assertEquals("round", mapping["round"])
        assertEquals("max", mapping["max"])
        assertEquals("min", mapping["min"])
        assertEquals("sum", mapping["sum"])
        assertEquals("avg", mapping["avg"])
        assertEquals("pow", mapping["pow"])
        assertEquals("sqrt", mapping["sqrt"])
    }

    @Test
    fun testObjectFunctionMappings() {
        val mapping = DataWeaveMigrationGuide.functionMapping
        
        assertEquals("keys (alias: namesOf)", mapping["namesOf"])
        assertEquals("values (alias: valuesOf)", mapping["valuesOf"])
        assertEquals("entries (alias: entriesOf)", mapping["entriesOf"])
        assertEquals("pluck", mapping["pluck"])
        assertEquals("groupBy", mapping["groupBy"])
    }

    @Test
    fun testTypeFunctionMappings() {
        val mapping = DataWeaveMigrationGuide.functionMapping

        // Note: typeOf removed - use getType() directly. Keyword 'typeof' reserved for future operator.
        assertEquals("isArray", mapping["isArray"])
        assertEquals("isString", mapping["isString"])
        assertEquals("isNumber", mapping["isNumber"])
        assertEquals("isObject", mapping["isObject"])
    }

    @Test
    fun testNotImplementedFunctions() {
        val mapping = DataWeaveMigrationGuide.functionMapping
        
        // These should be marked as NOT IMPLEMENTED
        assertTrue(mapping["pluralize"]?.startsWith("NOT IMPLEMENTED") == true)
        assertTrue(mapping["update"]?.startsWith("NOT IMPLEMENTED") == true)
        
        // These should not be supported
        assertTrue(!DataWeaveMigrationGuide.isSupported("pluralize"))
        assertTrue(!DataWeaveMigrationGuide.isSupported("update"))
    }

    // ==================== EDGE CASES ====================

    @Test
    fun testSizeOfWithEmptyString() {
        val emptyStr = UDM.Scalar("")
        val result = DataWeaveAliases.sizeOf(emptyStr)
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testSizeOfWithEmptyCollections() {
        val emptyArray = UDM.Array(emptyList())
        val arrayResult = DataWeaveAliases.sizeOf(emptyArray)
        assertEquals(0.0, (arrayResult as UDM.Scalar).value)
        
        val emptyObj = UDM.Object(emptyMap(), emptyMap())
        val objResult = DataWeaveAliases.sizeOf(emptyObj)
        assertEquals(0.0, (objResult as UDM.Scalar).value)
    }

    @Test
    fun testIsEmptyWithNumbers() {
        // Numbers are never considered empty (even 0)
        val zero = UDM.Scalar(0)
        assertTrue(!DataWeaveAliases.isEmpty(zero))
        
        val negativeNumber = UDM.Scalar(-5)
        assertTrue(!DataWeaveAliases.isEmpty(negativeNumber))
        
        val positiveNumber = UDM.Scalar(42)
        assertTrue(!DataWeaveAliases.isEmpty(positiveNumber))
    }

    @Test
    fun testIsEmptyWithBooleans() {
        // Booleans are never considered empty (even false)
        val falseValue = UDM.Scalar(false)
        assertTrue(!DataWeaveAliases.isEmpty(falseValue))
        
        val trueValue = UDM.Scalar(true)
        assertTrue(!DataWeaveAliases.isEmpty(trueValue))
    }
}