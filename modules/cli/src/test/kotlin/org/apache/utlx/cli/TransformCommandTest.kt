package org.apache.utlx.cli

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class TransformCommandTest {
    
    @TempDir
    lateinit var tempDir: File
    
    @Test
    fun `test basic transform`() {
        // Create test files
        val inputXml = tempDir.resolve("input.xml").apply {
            writeText("""
                <Order id="123">
                    <Customer>John Doe</Customer>
                    <Total>100.00</Total>
                </Order>
            """.trimIndent())
        }
        
        val transformUtlx = tempDir.resolve("transform.utlx").apply {
            writeText("""
                %utlx 1.0
                input xml
                output json
                ---
                {
                    orderId: input.Order.@id,
                    customer: input.Order.Customer,
                    total: input.Order.Total
                }
            """.trimIndent())
        }
        
        val output = tempDir.resolve("output.json")
        
        // Run transform
        // TODO: Execute CLI and verify output
        
        assertTrue(output.exists())
    }
}
