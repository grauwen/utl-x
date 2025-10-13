package org.apache.utlx.examples

import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.interpreter.Interpreter
import org.apache.utlx.core.interpreter.RuntimeValue
import org.apache.utlx.formats.json.JSON

/**
 * Complete example showing UTL-X with real JSON input and output
 * 
 * Demonstrates:
 * - Parsing JSON input
 * - UTL-X transformation
 * - Serializing to JSON output
 */
object CompleteJSONTransformationExample {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(80))
        println(" UTL-X Complete JSON Transformation Example")
        println("=".repeat(80))
        println()
        
        // Example 1: Customer order transformation
        example1_OrderToInvoice()
        
        println("\n" + "=".repeat(80) + "\n")
        
        // Example 2: Data aggregation
        example2_SalesReport()
        
        println("\n" + "=".repeat(80) + "\n")
        
        // Example 3: Complex business logic
        example3_PricingEngine()
    }
    
    private fun example1_OrderToInvoice() {
        println("Example 1: Order to Invoice Transformation")
        println("-".repeat(80))
        
        // Input: E-commerce order JSON
        val orderJSON = """
            {
              "orderId": "ORD-2025-001",
              "orderDate": "2025-10-13",
              "customer": {
                "customerId": "CUST-12345",
                "name": "Alice Johnson",
                "email": "alice@example.com",
                "membershipLevel": "GOLD"
              },
              "items": [
                {
                  "sku": "LAPTOP-PRO-15",
                  "name": "Professional Laptop 15\"",
                  "quantity": 1,
                  "unitPrice": 1299.99
                },
                {
                  "sku": "MOUSE-WIRELESS",
                  "name": "Wireless Mouse",
                  "quantity": 2,
                  "unitPrice": 29.99
                },
                {
                  "sku": "USB-C-CABLE",
                  "name": "USB-C Cable 2m",
                  "quantity": 3,
                  "unitPrice": 12.99
                }
              ],
              "shippingMethod": "EXPRESS"
            }
        """.trimIndent()
        
        // Transformation: Order → Invoice with business logic
        val transformation = """
            %utlx 1.0
            input json
            output json
            ---
            {
              let subtotal = sum(input.items.(quantity * unitPrice)),
              let memberDiscount = 0.15 if input.customer.membershipLevel == "GOLD"
                                   else 0.10 if input.customer.membershipLevel == "SILVER"
                                   else 0,
              let discountAmount = subtotal * memberDiscount,
              let tax = (subtotal - discountAmount) * 0.0875,
              let shippingCost = 15.00 if input.shippingMethod == "EXPRESS" else 5.00,
              let total = subtotal - discountAmount + tax + shippingCost,
              
              invoice: {
                invoiceNumber: "INV-" + input.orderId,
                invoiceDate: input.orderDate,
                
                customer: {
                  id: input.customer.customerId,
                  name: input.customer.name,
                  email: input.customer.email,
                  membershipLevel: input.customer.membershipLevel
                },
                
                lineItems: input.items,
                
                summary: {
                  subtotal: subtotal,
                  membershipDiscount: {
                    rate: memberDiscount,
                    amount: discountAmount
                  },
                  tax: tax,
                  shipping: {
                    method: input.shippingMethod,
                    cost: shippingCost
                  },
                  total: total
                },
                
                paymentDue: total
              }
            }
        """.trimIndent()
        
        println("INPUT JSON:")
        println(orderJSON)
        println()
        
        println("TRANSFORMATION:")
        println(transformation)
        println()
        
        // Execute transformation
        val inputUDM = JSON.parse(orderJSON)
        val program = parseTransformation(transformation)
        val result = Interpreter().execute(program, inputUDM)
        
        val outputJSON = JSON.stringify(result)
        
        println("OUTPUT JSON:")
        println(outputJSON)
    }
    
    private fun example2_SalesReport() {
        println("Example 2: Sales Data Aggregation")
        println("-".repeat(80))
        
        // Input: Multiple sales transactions
        val salesJSON = """
            {
              "transactions": [
                {"date": "2025-10-01", "product": "Widget", "quantity": 10, "price": 25.00, "region": "North"},
                {"date": "2025-10-01", "product": "Gadget", "quantity": 5, "price": 50.00, "region": "South"},
                {"date": "2025-10-02", "product": "Widget", "quantity": 8, "price": 25.00, "region": "North"},
                {"date": "2025-10-02", "product": "Widget", "quantity": 12, "price": 25.00, "region": "South"},
                {"date": "2025-10-03", "product": "Gadget", "quantity": 7, "price": 50.00, "region": "North"}
              ]
            }
        """.trimIndent()
        
        // Transformation: Aggregate sales data
        val transformation = """
            %utlx 1.0
            input json
            output json
            ---
            {
              let allTransactions = input.transactions,
              let totalRevenue = sum(allTransactions.(quantity * price)),
              let totalQuantity = sum(allTransactions.quantity),
              
              summary: {
                reportDate: "2025-10-13",
                period: "Oct 1-3, 2025",
                totalTransactions: count(allTransactions),
                totalRevenue: totalRevenue,
                totalQuantitySold: totalQuantity,
                averageTransactionValue: totalRevenue / count(allTransactions)
              },
              
              transactions: allTransactions
            }
        """.trimIndent()
        
        println("INPUT JSON:")
        println(salesJSON)
        println()
        
        // Execute transformation
        val inputUDM = JSON.parse(salesJSON)
        val program = parseTransformation(transformation)
        val result = Interpreter().execute(program, inputUDM)
        
        val outputJSON = JSON.stringify(result)
        
        println("OUTPUT JSON:")
        println(outputJSON)
    }
    
    private fun example3_PricingEngine() {
        println("Example 3: Dynamic Pricing Engine")
        println("-".repeat(80))
        
        // Input: Product catalog with pricing rules
        val catalogJSON = """
            {
              "products": [
                {
                  "sku": "LAPTOP-001",
                  "name": "Budget Laptop",
                  "basePrice": 599.99,
                  "category": "electronics",
                  "inStock": true,
                  "stockLevel": 45
                },
                {
                  "sku": "LAPTOP-002",
                  "name": "Premium Laptop",
                  "basePrice": 1299.99,
                  "category": "electronics",
                  "inStock": true,
                  "stockLevel": 8
                },
                {
                  "sku": "DESK-001",
                  "name": "Standing Desk",
                  "basePrice": 399.99,
                  "category": "furniture",
                  "inStock": true,
                  "stockLevel": 120
                },
                {
                  "sku": "CHAIR-001",
                  "name": "Ergonomic Chair",
                  "basePrice": 299.99,
                  "category": "furniture",
                  "inStock": false,
                  "stockLevel": 0
                }
              ],
              "pricingRules": {
                "lowStockThreshold": 10,
                "lowStockSurcharge": 0.15,
                "highStockThreshold": 100,
                "highStockDiscount": 0.10,
                "outOfStockMarkup": 0.25
              }
            }
        """.trimIndent()
        
        // Transformation: Apply dynamic pricing rules
        val transformation = """
            %utlx 1.0
            input json
            output json
            ---
            {
              let rules = input.pricingRules,
              
              catalog: {
                generatedAt: "2025-10-13T14:30:00Z",
                pricingRules: rules,
                
                products: input.products
              }
            }
        """.trimIndent()
        
        println("INPUT JSON:")
        println(catalogJSON)
        println()
        
        // Execute transformation
        val inputUDM = JSON.parse(catalogJSON)
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
 * Real-world use case: API Gateway Transformation
 */
object APIGatewayExample {
    @JvmStatic
    fun main(args: Array<String>) {
        println("API Gateway: Legacy to Modern Format")
        println("=".repeat(80))
        
        // Legacy API response format
        val legacyResponse = """
            {
              "ResponseCode": "0000",
              "ResponseMessage": "Success",
              "Data": {
                "UserID": "12345",
                "FirstName": "John",
                "LastName": "Doe",
                "EmailAddr": "john.doe@example.com",
                "PhoneNum": "555-0123",
                "AcctBalance": "1234.56",
                "AcctStatus": "A",
                "LastLoginDT": "20251013143000"
              }
            }
        """.trimIndent()
        
        // Transform to modern REST API format
        val transformation = """
            %utlx 1.0
            input json
            output json
            ---
            {
              success: input.ResponseCode == "0000",
              message: input.ResponseMessage,
              user: {
                id: input.Data.UserID,
                name: {
                  first: input.Data.FirstName,
                  last: input.Data.LastName,
                  full: input.Data.FirstName + " " + input.Data.LastName
                },
                contact: {
                  email: input.Data.EmailAddr,
                  phone: input.Data.PhoneNum
                },
                account: {
                  balance: input.Data.AcctBalance,
                  status: "active" if input.Data.AcctStatus == "A" else "inactive",
                  lastLogin: input.Data.LastLoginDT
                }
              }
            }
        """.trimIndent()
        
        println("LEGACY FORMAT:")
        println(legacyResponse)
        println()
        
        println("TRANSFORMATION:")
        println(transformation)
        println()
        
        // Execute
        val inputUDM = JSON.parse(legacyResponse)
        val tokens = Lexer(transformation).tokenize()
        val program = (Parser(tokens).parse() as ParseResult.Success).program
        val result = Interpreter().execute(program, inputUDM)
        
        println("MODERN FORMAT:")
        println(JSON.stringify(result))
    }
}
