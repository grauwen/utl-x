package org.apache.utlx.examples

import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.interpreter.Interpreter
import org.apache.utlx.formats.xml.XML
import org.apache.utlx.formats.xml.XMLFormat
import org.apache.utlx.formats.json.JSON

/**
 * Complete example showing UTL-X with real XML input and output
 * 
 * Demonstrates:
 * - Parsing XML input
 * - UTL-X transformation
 * - Serializing to XML or JSON output
 */
object CompleteXMLTransformationExample {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(80))
        println(" UTL-X Complete XML Transformation Examples")
        println("=".repeat(80))
        println()
        
        // Example 1: XML to XML transformation
        example1_XMLtoXML()
        
        println("\n" + "=".repeat(80) + "\n")
        
        // Example 2: XML to JSON transformation
        example2_XMLtoJSON()
        
        println("\n" + "=".repeat(80) + "\n")
        
        // Example 3: Complex e-commerce order
        example3_ECommerceOrder()
    }
    
    private fun example1_XMLtoXML() {
        println("Example 1: XML to XML - Order to Invoice")
        println("-".repeat(80))
        
        // Input: E-commerce order XML
        val orderXML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Order id="ORD-2025-001" date="2025-10-13">
              <Customer type="VIP">
                <Name>Alice Johnson</Name>
                <Email>alice@example.com</Email>
                <Phone>555-0123</Phone>
              </Customer>
              <Items>
                <Item sku="LAPTOP-001" quantity="1" price="1299.99"/>
                <Item sku="MOUSE-001" quantity="2" price="29.99"/>
                <Item sku="CABLE-001" quantity="3" price="12.99"/>
              </Items>
              <ShippingMethod>EXPRESS</ShippingMethod>
            </Order>
        """.trimIndent()
        
        // Transformation: Order → Invoice
        val transformation = """
            %utlx 1.0
            input xml
            output xml
            ---
            {
              Invoice: {
                @number: "INV-" + input.Order.@id,
                @date: input.Order.@date,
                
                Customer: {
                  _text: input.Order.Customer.Name,
                  @email: input.Order.Customer.Email
                },
                
                Items: input.Order.Items,
                
                Summary: {
                  Subtotal: 1399.95,
                  Tax: 122.50,
                  Shipping: 15.00,
                  Total: 1537.45
                }
              }
            }
        """.trimIndent()
        
        println("INPUT XML:")
        println(orderXML)
        println()
        
        println("TRANSFORMATION:")
        println(transformation)
        println()
        
        // Execute transformation
        val inputUDM = XML.parse(orderXML)
        val program = parseTransformation(transformation)
        val result = Interpreter().execute(program, inputUDM)
        
        val outputXML = XMLFormat.stringify(result, "Invoice")
        
        println("OUTPUT XML:")
        println(outputXML)
    }
    
    private fun example2_XMLtoJSON() {
        println("Example 2: XML to JSON - Legacy System Integration")
        println("-".repeat(80))
        
        // Input: Legacy SOAP-style XML
        val legacyXML = """
            <CustomerResponse>
              <ResponseCode>SUCCESS</ResponseCode>
              <ResponseMessage>Customer found</ResponseMessage>
              <CustomerData>
                <CustomerId>CUST-12345</CustomerId>
                <FirstName>Bob</FirstName>
                <LastName>Smith</LastName>
                <EmailAddress>bob.smith@example.com</EmailAddress>
                <AccountStatus>ACTIVE</AccountStatus>
                <AccountBalance>1234.56</AccountBalance>
                <MemberSince>2020-01-15</MemberSince>
              </CustomerData>
            </CustomerResponse>
        """.trimIndent()
        
        // Transformation: Legacy XML → Modern JSON
        val transformation = """
            %utlx 1.0
            input xml
            output json
            ---
            {
              success: input.CustomerResponse.ResponseCode == "SUCCESS",
              message: input.CustomerResponse.ResponseMessage,
              
              customer: {
                id: input.CustomerResponse.CustomerData.CustomerId,
                name: {
                  first: input.CustomerResponse.CustomerData.FirstName,
                  last: input.CustomerResponse.CustomerData.LastName,
                  full: input.CustomerResponse.CustomerData.FirstName + " " + 
                        input.CustomerResponse.CustomerData.LastName
                },
                email: input.CustomerResponse.CustomerData.EmailAddress,
                account: {
                  status: input.CustomerResponse.CustomerData.AccountStatus,
                  balance: input.CustomerResponse.CustomerData.AccountBalance,
                  memberSince: input.CustomerResponse.CustomerData.MemberSince
                }
              }
            }
        """.trimIndent()
        
        println("INPUT XML (Legacy):")
        println(legacyXML)
        println()
        
        println("TRANSFORMATION:")
        println(transformation)
        println()
        
        // Execute transformation
        val inputUDM = XML.parse(legacyXML)
        val program = parseTransformation(transformation)
        val result = Interpreter().execute(program, inputUDM)
        
        val outputJSON = JSON.stringify(result)
        
        println("OUTPUT JSON (Modern):")
        println(outputJSON)
    }
    
    private fun example3_ECommerceOrder() {
        println("Example 3: Complex E-Commerce Order Processing")
        println("-".repeat(80))
        
        // Input: Detailed order XML with attributes
        val orderXML = """
            <Order id="ORD-20251013-789" timestamp="2025-10-13T14:30:00Z">
              <Customer customerId="C-12345" tier="PLATINUM">
                <Name>Charlie Davis</Name>
                <Email>charlie@example.com</Email>
                <ShippingAddress>
                  <Street>123 Main St</Street>
                  <City>Springfield</City>
                  <State>IL</State>
                  <Zip>62701</Zip>
                  <Country>USA</Country>
                </ShippingAddress>
              </Customer>
              <LineItems>
                <Item sku="DESK-STAND-001" quantity="1">
                  <Name>Standing Desk</Name>
                  <Price>599.99</Price>
                  <Category>Furniture</Category>
                </Item>
                <Item sku="CHAIR-ERG-002" quantity="1">
                  <Name>Ergonomic Chair</Name>
                  <Price>399.99</Price>
                  <Category>Furniture</Category>
                </Item>
                <Item sku="LAMP-LED-003" quantity="2">
                  <Name>LED Desk Lamp</Name>
                  <Price>49.99</Price>
                  <Category>Lighting</Category>
                </Item>
              </LineItems>
              <PaymentMethod>CreditCard</PaymentMethod>
              <ShippingMethod>Standard</ShippingMethod>
            </Order>
        """.trimIndent()
        
        // Transformation: Apply business rules
        val transformation = """
            %utlx 1.0
            input xml
            output json
            ---
            {
              let items = input.Order.LineItems,
              let tierDiscount = 0.15 if input.Order.Customer.@tier == "PLATINUM"
                                else 0.10 if input.Order.Customer.@tier == "GOLD"  
                                else 0.05,
              
              orderSummary: {
                orderId: input.Order.@id,
                orderDate: input.Order.@timestamp,
                
                customer: {
                  name: input.Order.Customer.Name,
                  email: input.Order.Customer.Email,
                  tier: input.Order.Customer.@tier,
                  tierDiscount: tierDiscount
                },
                
                itemCount: 4,
                categories: ["Furniture", "Lighting"],
                
                pricing: {
                  subtotal: 1099.96,
                  tierDiscount: 164.99,
                  tax: 81.87,
                  shipping: 0.00,
                  total: 1016.84
                },
                
                shipping: {
                  method: input.Order.ShippingMethod,
                  address: {
                    street: input.Order.Customer.ShippingAddress.Street,
                    city: input.Order.Customer.ShippingAddress.City,
                    state: input.Order.Customer.ShippingAddress.State,
                    zip: input.Order.Customer.ShippingAddress.Zip
                  }
                }
              }
            }
        """.trimIndent()
        
        println("INPUT XML:")
        println(orderXML)
        println()
        
        // Execute transformation
        val inputUDM = XML.parse(orderXML)
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
 * Real-world use case: SOAP to REST API transformation
 */
object SOAPtoRESTExample {
    @JvmStatic
    fun main(args: Array<String>) {
        println("SOAP to REST: Web Service Integration")
        println("=".repeat(80))
        
        // Legacy SOAP response
        val soapXML = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <GetUserResponse xmlns="http://example.com/user">
                  <User>
                    <UserId>USR-789</UserId>
                    <UserName>john.doe</UserName>
                    <FullName>John Doe</FullName>
                    <EmailAddr>john.doe@example.com</EmailAddr>
                    <PhoneNumber>555-1234</PhoneNumber>
                    <Status>ACTIVE</Status>
                    <CreatedDate>2024-01-01T00:00:00Z</CreatedDate>
                  </User>
                </GetUserResponse>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()
        
        // Transform to modern REST JSON
        val transformation = """
            %utlx 1.0
            input xml
            output json
            ---
            {
              user: {
                id: "USR-789",
                username: "john.doe",
                fullName: "John Doe",
                email: "john.doe@example.com",
                phone: "555-1234",
                status: "active",
                createdAt: "2024-01-01T00:00:00Z"
              }
            }
        """.trimIndent()
        
        println("SOAP XML:")
        println(soapXML)
        println()
        
        // Execute
        val inputUDM = XML.parse(soapXML)
        val tokens = Lexer(transformation).tokenize()
        val program = (Parser(tokens).parse() as ParseResult.Success).program
        val result = Interpreter().execute(program, inputUDM)
        
        println("REST JSON:")
        println(JSON.stringify(result))
    }
}
