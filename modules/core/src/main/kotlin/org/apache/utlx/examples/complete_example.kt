package org.apache.utlx.examples

import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.types.StandardLibrary
import org.apache.utlx.core.types.TypeCheckResult
import org.apache.utlx.core.types.TypeChecker
import org.apache.utlx.core.interpreter.Interpreter
import org.apache.utlx.core.interpreter.RuntimeValue
import org.apache.utlx.core.udm.UDM

/**
 * Complete example demonstrating the full UTL-X transformation pipeline
 */
object CompleteTransformationExample {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(70))
        println("UTL-X Complete Transformation Example")
        println("=".repeat(70))
        println()
        
        // Example 1: Simple transformation
        example1_SimpleTransformation()
        
        println()
        
        // Example 2: Order invoice transformation
        example2_OrderInvoice()
        
        println()
        
        // Example 3: Conditional logic
        example3_ConditionalLogic()
        
        println()
        
        // Example 4: Array operations
        example4_ArrayOperations()
    }
    
    private fun example1_SimpleTransformation() {
        println("Example 1: Simple Transformation")
        println("-".repeat(70))
        
        val transformation = """
            %utlx 1.0
            input json
            output json
            ---
            {
              greeting: "Hello, " + input.name,
              age: input.age,
              nextAge: input.age + 1,
              isAdult: input.age >= 18
            }
        """.trimIndent()
        
        println("Transformation:")
        println(transformation)
        println()
        
        val inputData = UDM.Object.of(
            "name" to UDM.Scalar.string("Alice"),
            "age" to UDM.Scalar.number(25)
        )
        
        println("Input:")
        println(inputData.toJsonString())
        println()
        
        val result = executeTransformation(transformation, inputData)
        
        println("Output:")
        println(result.toJsonString())
    }
    
    private fun example2_OrderInvoice() {
        println("Example 2: Order to Invoice Transformation")
        println("-".repeat(70))
        
        val transformation = """
            %utlx 1.0
            input xml
            output json
            ---
            {
              invoice: {
                id: "INV-" + input.Order.@id,
                date: input.Order.@date,
                customer: {
                  name: input.Order.Customer.Name,
                  email: input.Order.Customer.Email
                },
                total: input.Order.Total,
                vip: input.Order.Customer.@type == "VIP"
              }
            }
        """.trimIndent()
        
        println("Transformation:")
        println(transformation)
        println()
        
        // Simulate XML input structure
        val inputData = UDM.Object.of(
            "Order" to UDM.Object.withAttributes(
                properties = mapOf(
                    "Customer" to UDM.Object.withAttributes(
                        properties = mapOf(
                            "Name" to UDM.Scalar.string("Bob Smith"),
                            "Email" to UDM.Scalar.string("bob@example.com")
                        ),
                        attributes = mapOf("type" to "VIP")
                    ),
                    "Total" to UDM.Scalar.number(299.99)
                ),
                attributes = mapOf(
                    "id" to "ORD-001",
                    "date" to "2025-10-01"
                )
            )
        )
        
        println("Input (XML structure):")
        println(inputData.toJsonString())
        println()
        
        val result = executeTransformation(transformation, inputData)
        
        println("Output:")
        println(result.toJsonString())
    }
    
    private fun example3_ConditionalLogic() {
        println("Example 3: Conditional Logic - Discount Calculation")
        println("-".repeat(70))
        
        val transformation = """
            %utlx 1.0
            input json
            output json
            ---
            {
              let subtotal = input.price * input.quantity,
              let discount = 0.20 if input.customerType == "VIP" 
                            else 0.10 if subtotal > 1000 
                            else 0,
              let tax = (subtotal - (subtotal * discount)) * 0.08,
              
              order: {
                subtotal: subtotal,
                discountRate: discount,
                discountAmount: subtotal * discount,
                tax: tax,
                total: subtotal - (subtotal * discount) + tax,
                customerType: input.customerType
              }
            }
        """.trimIndent()
        
        println("Transformation:")
        println(transformation)
        println()
        
        // Test with VIP customer
        println("Test 1: VIP Customer")
        val vipData = UDM.Object.of(
            "price" to UDM.Scalar.number(100),
            "quantity" to UDM.Scalar.number(5),
            "customerType" to UDM.Scalar.string("VIP")
        )
        
        println("Input:")
        println(vipData.toJsonString())
        val vipResult = executeTransformation(transformation, vipData)
        println("Output:")
        println(vipResult.toJsonString())
        
        println()
        
        // Test with regular customer, large order
        println("Test 2: Regular Customer, Large Order")
        val largeOrderData = UDM.Object.of(
            "price" to UDM.Scalar.number(250),
            "quantity" to UDM.Scalar.number(5),
            "customerType" to UDM.Scalar.string("Regular")
        )
        
        println("Input:")
        println(largeOrderData.toJsonString())
        val largeOrderResult = executeTransformation(transformation, largeOrderData)
        println("Output:")
        println(largeOrderResult.toJsonString())
    }
    
    private fun example4_ArrayOperations() {
        println("Example 4: Array Operations")
        println("-".repeat(70))
        
        val transformation = """
            %utlx 1.0
            input json
            output json
            ---
            {
              items: input.items,
              itemCount: count(input.items),
              firstItem: first(input.items),
              lastItem: last(input.items)
            }
        """.trimIndent()
        
        println("Transformation:")
        println(transformation)
        println()
        
        val inputData = UDM.Object.of(
            "items" to UDM.Array.of(
                UDM.Scalar.string("Apple"),
                UDM.Scalar.string("Banana"),
                UDM.Scalar.string("Cherry"),
                UDM.Scalar.string("Date")
            )
        )
        
        println("Input:")
        println(inputData.toJsonString())
        println()
        
        val result = executeTransformation(transformation, inputData)
        
        println("Output:")
        println(result.toJsonString())
    }
    
    /**
     * Execute a transformation with full pipeline
     */
    private fun executeTransformation(source: String, inputData: UDM): RuntimeValue {
        // Step 1: Tokenize
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        
        // Step 2: Parse
        val parser = Parser(tokens)
        val parseResult = parser.parse()
        
        if (parseResult is ParseResult.Failure) {
            throw RuntimeException("Parse failed: ${parseResult.errors}")
        }
        
        val program = (parseResult as ParseResult.Success).program
        
        // Step 3: Type check
        val stdlib = StandardLibrary()
        val typeChecker = TypeChecker(stdlib)
        val typeResult = typeChecker.check(program)
        
        if (typeResult is TypeCheckResult.Failure) {
            println("WARNING: Type check failed:")
            typeResult.errors.forEach { println("  - $it") }
            println("Continuing with execution...")
        }
        
        // Step 4: Execute
        val interpreter = Interpreter()
        return interpreter.execute(program, inputData)
    }
    
    /**
     * Convert UDM to JSON-like string for display
     */
    private fun UDM.toJsonString(indent: Int = 0): String {
        val indentStr = "  ".repeat(indent)
        
        return when (this) {
            is UDM.Scalar -> {
                when (value) {
                    is String -> "\"$value\""
                    null -> "null"
                    else -> value.toString()
                }
            }
            is UDM.Array -> {
                if (elements.isEmpty()) {
                    "[]"
                } else {
                    val items = elements.joinToString(",\n$indentStr  ") { 
                        it.toJsonString(indent + 1) 
                    }
                    "[\n$indentStr  $items\n$indentStr]"
                }
            }
            is UDM.Object -> {
                val props = mutableListOf<String>()
                
                // Add attributes with @ prefix
                attributes.forEach { (key, value) ->
                    props.add("\"@$key\": \"$value\"")
                }
                
                // Add properties
                properties.forEach { (key, value) ->
                    props.add("\"$key\": ${value.toJsonString(indent + 1)}")
                }
                
                if (props.isEmpty()) {
                    "{}"
                } else {
                    val items = props.joinToString(",\n$indentStr  ")
                    "{\n$indentStr  $items\n$indentStr}"
                }
            }
            is UDM.DateTime -> "\"${instant}\""
        }
    }
    
    /**
     * Convert RuntimeValue to JSON-like string for display
     */
    private fun RuntimeValue.toJsonString(indent: Int = 0): String {
        val indentStr = "  ".repeat(indent)
        
        return when (this) {
            is RuntimeValue.StringValue -> "\"$value\""
            is RuntimeValue.NumberValue -> {
                if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
            }
            is RuntimeValue.BooleanValue -> value.toString()
            is RuntimeValue.NullValue -> "null"
            is RuntimeValue.ArrayValue -> {
                if (elements.isEmpty()) {
                    "[]"
                } else {
                    val items = elements.joinToString(",\n$indentStr  ") {
                        it.toJsonString(indent + 1)
                    }
                    "[\n$indentStr  $items\n$indentStr]"
                }
            }
            is RuntimeValue.ObjectValue -> {
                if (properties.isEmpty()) {
                    "{}"
                } else {
                    val items = properties.entries.joinToString(",\n$indentStr  ") {
                        "\"${it.key}\": ${it.value.toJsonString(indent + 1)}"
                    }
                    "{\n$indentStr  $items\n$indentStr}"
                }
            }
            is RuntimeValue.FunctionValue -> "\"<function>\""
            is RuntimeValue.UDMValue -> udm.toJsonString(indent)
        }
    }
}
