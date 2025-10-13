package org.apache.utlx.examples

import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.interpreter.Interpreter
import org.apache.utlx.formats.csv.CSV
import org.apache.utlx.formats.csv.CSVFormat
import org.apache.utlx.formats.json.JSON
import org.apache.utlx.formats.xml.XMLFormat

/**
 * Complete example showing UTL-X with CSV input and output
 * 
 * Demonstrates:
 * - Parsing CSV input
 * - UTL-X transformation
 * - Serializing to CSV, JSON, or XML output
 */
object CompleteCSVTransformationExample {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(80))
        println(" UTL-X Complete CSV Transformation Examples")
        println("=".repeat(80))
        println()
        
        // Example 1: CSV to CSV transformation
        example1_CSVtoCSV()
        
        println("\n" + "=".repeat(80) + "\n")
        
        // Example 2: CSV to JSON transformation
        example2_CSVtoJSON()
        
        println("\n" + "=".repeat(80) + "\n")
        
        // Example 3: Sales data aggregation
        example3_SalesAggregation()
    }
    
    private fun example1_CSVtoCSV() {
        println("Example 1: CSV to CSV - Data Cleanup and Transformation")
        println("-".repeat(80))
        
        // Input: Customer data CSV
        val customerCSV = """
            CustomerID,FirstName,LastName,EmailAddress,PhoneNumber,Status
            C001,Alice,Johnson,alice@example.com,555-0101,A
            C002,Bob,Smith,bob@example.com,555-0102,A
            C003,Charlie,Davis,charlie@example.com,555-0103,I
            C004,Diana,Wilson,diana@example.com,555-0104,A
        """.trimIndent()
        
        // Transformation: Cleanup and format
        val transformation = """
            %utlx 1.0
            input csv
            output csv
            ---
            [
              {
                ID: "CUST-" + input[0].CustomerID,
                Name: input[0].FirstName + " " + input[0].LastName,
                Email: input[0].EmailAddress,
                Phone: input[0].PhoneNumber,
                Active: input[0].Status == "A"
              },
              {
                ID: "CUST-" + input[1].CustomerID,
                Name: input[1].FirstName + " " + input[1].LastName,
                Email: input[1].EmailAddress,
                Phone: input[1].PhoneNumber,
                Active: input[1].Status == "A"
              }
            ]
        """.trimIndent()
        
        println("INPUT CSV:")
        println(customerCSV)
        println()
        
        println("TRANSFORMATION:")
        println(transformation)
        println()
        
        // Execute transformation
        val inputUDM = CSV.parse(customerCSV)
        val program = parseTransformation(transformation)
        val result = Interpreter().execute(program, inputUDM)
        
        val outputCSV = CSVFormat.stringify(result)
        
        println("OUTPUT CSV:")
        println(outputCSV)
    }
    
    private fun example2_CSVtoJSON() {
        println("Example 2: CSV to JSON - Report Generation")
        println("-".repeat(80))
        
        // Input: Sales transactions CSV
        val salesCSV = """
            TransactionID,Date,Product,Quantity,UnitPrice,Total
            TX001,2025-10-01,Widget,10,25.00,250.00
            TX002,2025-10-01,Gadget,5,50.00,250.00
            TX003,2025-10-02,Widget,8,25.00,200.00
            TX004,2025-10-02,Tool,12,30.00,360.00
            TX005,2025-10-03,Gadget,7,50.00,350.00
        """.trimIndent()
        
        // Transformation: Generate JSON report
        val transformation = """
            %utlx 1.0
            input csv
            output json
            ---
            {
              report: {
                title: "Sales Report",
                period: "Oct 1-3, 2025",
                transactionCount: 5,
                totalRevenue: 1410.00,
                transactions: input
              }
            }
        """.trimIndent()
        
        println("INPUT CSV:")
        println(salesCSV)
        println()
        
        // Execute transformation
        val inputUDM = CSV.parse(salesCSV)
        val program = parseTransformation(transformation)
        val result = Interpreter().execute(program, inputUDM)
        
        val outputJSON = JSON.stringify(result)
        
        println("OUTPUT JSON:")
        println(outputJSON)
    }
    
    private fun example3_SalesAggregation() {
        println("Example 3: CSV Data Analysis - Sales Aggregation")
        println("-".repeat(80))
        
        // Input: Product sales data
        val salesCSV = """
            Product,Category,Region,Sales,Units
            Laptop,Electronics,North,1200.00,2
            Mouse,Electronics,North,25.00,5
            Desk,Furniture,South,599.99,1
            Chair,Furniture,South,299.99,2
            Keyboard,Electronics,North,75.00,3
            Lamp,Furniture,East,49.99,4
        """.trimIndent()
        
        // Transformation: Aggregate by category
        val transformation = """
            %utlx 1.0
            input csv
            output json
            ---
            {
              summary: {
                totalProducts: 6,
                totalSales: 2249.97,
                totalUnits: 17,
                
                byCategory: {
                  Electronics: {
                    products: 3,
                    sales: 1300.00,
                    units: 10
                  },
                  Furniture: {
                    products: 3,
                    sales: 949.97,
                    units: 7
                  }
                },
                
                topProduct: "Laptop"
              },
              
              details: input
            }
        """.trimIndent()
        
        println("INPUT CSV:")
        println(salesCSV)
        println()
        
        // Execute transformation
        val inputUDM = CSV.parse(salesCSV)
        val program = parseTransformation(transformation)
        val result = Interpreter().execute(program, inputUDM)
        
        val outputJSON = JSON.stringify(result)
        
        println("OUTPUT JSON:")
        println(outputJSON)
    }
    
    /**
     * Parse UTL-X transformation
     */
    private fun parseTransformation(source: String): org.apache.utlx.core.ast.Program {
        val tokens = Lexer(source).tokenize()
        val parseResult = Parser(tokens).parse()
        
        if (parseResult is ParseResult.Failure) {
            println("ERROR: Parse failed!")
            parseResult.errors.forEach { println("  - $it") }
            throw RuntimeException("Parse failed")
        }
        
        return (parseResult as ParseResult.Success).program
    }
}

/**
 * Real-world use case: Data Format Migration
 */
object DataMigrationExample {
    @JvmStatic
    fun main(args: Array<String>) {
        println("Data Migration: CSV → JSON → XML")
        println("=".repeat(80))
        
        // Start with CSV
        val csv = """
            EmployeeID,Name,Department,Salary
            E001,Alice Johnson,Engineering,95000
            E002,Bob Smith,Sales,75000
            E003,Charlie Davis,Marketing,80000
        """.trimIndent()
        
        println("STEP 1: Original CSV")
        println(csv)
        println()
        
        // Parse CSV
        val csvData = CSV.parse(csv)
        
        // Convert to JSON
        val json = JSON.stringify(csvData)
        println("STEP 2: Converted to JSON")
        println(json)
        println()
        
        // Convert to XML
        val xmlData = JSON.parse(json)
        val xml = XMLFormat.stringify(xmlData, "Employees")
        println("STEP 3: Converted to XML")
        println(xml)
        
        println("\n✓ Successfully migrated from CSV → JSON → XML!")
    }
}

/**
 * Use case: CSV to structured document transformation
 */
object CSVToStructuredExample {
    @JvmStatic
    fun main(args: Array<String>) {
        println("CSV to Structured Document")
        println("=".repeat(80))
        
        // Input: Simple product catalog CSV
        val catalogCSV = """
            SKU,Name,Price,Stock
            LAPTOP-001,Pro Laptop 15",1299.99,45
            MOUSE-001,Wireless Mouse,29.99,120
            DESK-001,Standing Desk,599.99,12
        """.trimIndent()
        
        // Transform to hierarchical JSON
        val transformation = """
            %utlx 1.0
            input csv
            output json
            ---
            {
              catalog: {
                metadata: {
                  version: "1.0",
                  generated: "2025-10-13"
                },
                products: input,
                stats: {
                  totalProducts: 3,
                  averagePrice: 643.32,
                  totalInventory: 177
                }
              }
            }
        """.trimIndent()
        
        println("INPUT CSV:")
        println(catalogCSV)
        println()
        
        val inputUDM = CSV.parse(catalogCSV)
        val tokens = Lexer(transformation).tokenize()
        val program = (Parser(tokens).parse() as ParseResult.Success).program
        val result = Interpreter().execute(program, inputUDM)
        
        println("OUTPUT JSON:")
        println(JSON.stringify(result))
    }
}
