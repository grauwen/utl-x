package org.apache.utlx.stdlib.csv

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CSVFunctionsTest {

    private val sampleCsv = "name,age,city\nAlice,30,NYC\nBob,25,LA\nCharlie,35,Chicago"
    
    @Test
    fun testCsvRows() {
        val result = CSVFunctions.csvRows(listOf(UDM.Scalar(sampleCsv)))
        
        assertTrue(result is UDM.Array)
        val rows = result as UDM.Array
        assertEquals(3, rows.elements.size)
        
        val firstRow = rows.elements[0] as UDM.Object
        assertEquals("Alice", (firstRow.properties["name"] as UDM.Scalar).value)
        assertEquals("30", (firstRow.properties["age"] as UDM.Scalar).value)
        assertEquals("NYC", (firstRow.properties["city"] as UDM.Scalar).value)
    }
    
    @Test
    fun testCsvColumns() {
        val result = CSVFunctions.csvColumns(listOf(UDM.Scalar(sampleCsv)))
        
        assertTrue(result is UDM.Array)
        val columns = result as UDM.Array
        assertEquals(3, columns.elements.size)
        assertEquals("name", (columns.elements[0] as UDM.Scalar).value)
        assertEquals("age", (columns.elements[1] as UDM.Scalar).value)
        assertEquals("city", (columns.elements[2] as UDM.Scalar).value)
    }
    
    @Test
    fun testCsvColumn() {
        val result = CSVFunctions.csvColumn(listOf(UDM.Scalar(sampleCsv), UDM.Scalar("name")))
        
        assertTrue(result is UDM.Array)
        val nameColumn = result as UDM.Array
        assertEquals(3, nameColumn.elements.size)
        assertEquals("Alice", (nameColumn.elements[0] as UDM.Scalar).value)
        assertEquals("Bob", (nameColumn.elements[1] as UDM.Scalar).value)
        assertEquals("Charlie", (nameColumn.elements[2] as UDM.Scalar).value)
    }
    
    @Test
    fun testCsvRow() {
        val result = CSVFunctions.csvRow(listOf(UDM.Scalar(sampleCsv), UDM.Scalar(0)))
        
        assertTrue(result is UDM.Object)
        val firstRow = result as UDM.Object
        assertEquals("Alice", (firstRow.properties["name"] as UDM.Scalar).value)
        assertEquals("30", (firstRow.properties["age"] as UDM.Scalar).value)
        assertEquals("NYC", (firstRow.properties["city"] as UDM.Scalar).value)
    }
    
    @Test
    fun testCsvCell() {
        val result = CSVFunctions.csvCell(listOf(UDM.Scalar(sampleCsv), UDM.Scalar(1), UDM.Scalar("name")))
        
        assertTrue(result is UDM.Scalar)
        assertEquals("Bob", (result as UDM.Scalar).value)
    }
    
    @Test
    fun testCsvTranspose() {
        val simpleCsv = "name,age\nAlice,30\nBob,25"
        val result = CSVFunctions.csvTranspose(listOf(UDM.Scalar(simpleCsv)))
        
        assertTrue(result is UDM.Scalar)
        val transposed = (result as UDM.Scalar).value as String
        assertTrue(transposed.contains("field,0,1"))
        assertTrue(transposed.contains("name,Alice,Bob"))
        assertTrue(transposed.contains("age,30,25"))
    }
    
    @Test
    fun testCsvFilter() {
        val result = CSVFunctions.csvFilter(listOf(
            UDM.Scalar(sampleCsv), 
            UDM.Scalar("age"), 
            UDM.Scalar("gt"), 
            UDM.Scalar("27")
        ))
        
        assertTrue(result is UDM.Scalar)
        val filtered = (result as UDM.Scalar).value as String
        assertTrue(filtered.contains("Alice"))
        assertTrue(filtered.contains("Charlie"))
        assertTrue(!filtered.contains("Bob"))
    }
    
    @Test
    fun testCsvSort() {
        val result = CSVFunctions.csvSort(listOf(
            UDM.Scalar(sampleCsv), 
            UDM.Scalar("name"), 
            UDM.Scalar(true)
        ))
        
        assertTrue(result is UDM.Scalar)
        val sorted = (result as UDM.Scalar).value as String
        val lines = sorted.split("\n")
        assertEquals("name,age,city", lines[0])
        assertTrue(lines[1].startsWith("Alice"))
        assertTrue(lines[2].startsWith("Bob"))
        assertTrue(lines[3].startsWith("Charlie"))
    }
    
    @Test
    fun testCsvAddColumn() {
        val result = CSVFunctions.csvAddColumn(listOf(
            UDM.Scalar(sampleCsv), 
            UDM.Scalar("country"), 
            UDM.Scalar("USA")
        ))
        
        assertTrue(result is UDM.Scalar)
        val withNewColumn = (result as UDM.Scalar).value as String
        assertTrue(withNewColumn.contains("name,age,city,country"))
        assertTrue(withNewColumn.contains("Alice,30,NYC,USA"))
    }
    
    @Test
    fun testCsvRemoveColumns() {
        val result = CSVFunctions.csvRemoveColumns(listOf(
            UDM.Scalar(sampleCsv), 
            UDM.Array(listOf(UDM.Scalar("age")))
        ))
        
        assertTrue(result is UDM.Scalar)
        val withoutAge = (result as UDM.Scalar).value as String
        assertTrue(withoutAge.contains("name,city"))
        assertTrue(!withoutAge.contains("age"))
        assertTrue(withoutAge.contains("Alice,NYC"))
    }
    
    @Test
    fun testCsvSelectColumns() {
        val result = CSVFunctions.csvSelectColumns(listOf(
            UDM.Scalar(sampleCsv), 
            UDM.Array(listOf(UDM.Scalar("name"), UDM.Scalar("city")))
        ))
        
        assertTrue(result is UDM.Scalar)
        val selected = (result as UDM.Scalar).value as String
        assertTrue(selected.contains("name,city"))
        assertTrue(!selected.contains("age"))
        assertTrue(selected.contains("Alice,NYC"))
    }
    
    @Test
    fun testCsvSummarize() {
        val result = CSVFunctions.csvSummarize(listOf(
            UDM.Scalar(sampleCsv), 
            UDM.Array(listOf(UDM.Scalar("age")))
        ))
        
        assertTrue(result is UDM.Object)
        val summary = result as UDM.Object
        assertNotNull(summary.properties["age"])
        
        val ageStats = summary.properties["age"] as UDM.Object
        assertEquals(3.0, (ageStats.properties["count"] as UDM.Scalar).value)
        assertEquals(90.0, (ageStats.properties["sum"] as UDM.Scalar).value)
        assertEquals(30.0, (ageStats.properties["avg"] as UDM.Scalar).value)
    }
    
    @Test
    fun testInvalidArguments() {
        // Test with wrong number of arguments
        assertThrows<FunctionArgumentException> {
            CSVFunctions.csvRows(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            CSVFunctions.csvColumn(listOf(UDM.Scalar(sampleCsv)))
        }
        
        // Test with non-existent column
        assertThrows<FunctionArgumentException> {
            CSVFunctions.csvColumn(listOf(UDM.Scalar(sampleCsv), UDM.Scalar("nonexistent")))
        }
        
        // Test with invalid row index
        assertThrows<FunctionArgumentException> {
            CSVFunctions.csvRow(listOf(UDM.Scalar(sampleCsv), UDM.Scalar(10)))
        }
    }
    
    @Test
    fun testEmptyCSV() {
        val emptyCsv = "name,age\n"
        val result = CSVFunctions.csvRows(listOf(UDM.Scalar(emptyCsv)))
        
        assertTrue(result is UDM.Array)
        val rows = result as UDM.Array
        assertEquals(0, rows.elements.size)
    }
}