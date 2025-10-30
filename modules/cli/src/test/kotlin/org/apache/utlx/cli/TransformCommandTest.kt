// modules/cli/src/test/kotlin/org/apache/utlx/cli/TransformCommandTest.kt
package org.apache.utlx.cli

import org.apache.utlx.cli.commands.TransformCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransformCommandTest {
    
    @TempDir
    lateinit var tempDir: Path
    
    @Test
    fun `test simple XML to JSON transformation`() {
        // Create input XML
        val inputXml = tempDir.resolve("input.xml").toFile()
        inputXml.writeText("""
            <person>
                <name>John Doe</name>
                <age>30</age>
            </person>
        """.trimIndent())
        
        // Create UTL-X script
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input xml
            output json
            ---
            {
              name: input.person.name,
              age: input.person.age
            }
        """.trimIndent())
        
        // Output file
        val output = tempDir.resolve("output.json").toFile()
        
        // Execute transformation
        val args = arrayOf(
            script.absolutePath,
            inputXml.absolutePath,
            "-o", output.absolutePath
        )
        
        TransformCommand.execute(args)
        
        // Verify output
        assertTrue(output.exists(), "Output file should exist")
        val outputContent = output.readText()
        assertTrue(outputContent.contains("\"name\""), "Should contain name field")
        assertTrue(outputContent.contains("John Doe"), "Should contain name value")
    }
    
    @Test
    fun `test JSON to XML transformation`() {
        val inputJson = tempDir.resolve("input.json").toFile()
        inputJson.writeText("""
            {
              "order": {
                "id": "ORD-001",
                "customer": "Alice"
              }
            }
        """.trimIndent())
        
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output xml
            ---
            {
              Order: {
                @id: input.order.id,
                Customer: input.order.customer
              }
            }
        """.trimIndent())
        
        val output = tempDir.resolve("output.xml").toFile()
        
        val args = arrayOf(
            script.absolutePath,
            inputJson.absolutePath,
            "-o", output.absolutePath
        )
        
        TransformCommand.execute(args)
        
        assertTrue(output.exists())
        val outputContent = output.readText()
        assertTrue(outputContent.contains("<Order"))
        assertTrue(outputContent.contains("id=\"ORD-001\""))
    }
    
    @Test
    fun `test CSV to JSON transformation`() {
        val inputCsv = tempDir.resolve("input.csv").toFile()
        inputCsv.writeText("""
            Name,Age,City
            Alice,25,New York
            Bob,30,San Francisco
        """.trimIndent())
        
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input csv
            output json
            ---
            {
              people: input |> map(row => {
                name: row.Name,
                age: row.Age,
                city: row.City
              })
            }
        """.trimIndent())
        
        val output = tempDir.resolve("output.json").toFile()
        
        val args = arrayOf(
            script.absolutePath,
            inputCsv.absolutePath,
            "-o", output.absolutePath
        )
        
        TransformCommand.execute(args)
        
        assertTrue(output.exists())
        val outputContent = output.readText()
        assertTrue(outputContent.contains("\"people\""))
        assertTrue(outputContent.contains("Alice"))
        assertTrue(outputContent.contains("New York"))
    }
    
    @Test
    fun `test auto format detection`() {
        val inputXml = tempDir.resolve("input.xml").toFile()
        inputXml.writeText("<data><value>42</value></data>")
        
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input auto
            output json
            ---
            { value: input.data.value }
        """.trimIndent())
        
        val output = tempDir.resolve("output.json").toFile()
        
        val args = arrayOf(
            script.absolutePath,
            inputXml.absolutePath,
            "-o", output.absolutePath
        )

        TransformCommand.execute(args)
        
        assertTrue(output.exists())
    }
    
    @Test
    fun `test verbose mode`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("{\"test\": true}")
        
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            { result: input.test }
        """.trimIndent())
        
        val output = tempDir.resolve("output.json").toFile()
        
        val args = arrayOf(
            script.absolutePath,
            input.absolutePath,
            "-o", output.absolutePath,
            "--verbose"
        )
        
        // Should not throw exception
        TransformCommand.execute(args)

        assertTrue(output.exists())
    }
}
