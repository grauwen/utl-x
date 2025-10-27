package org.apache.utlx.core

import org.apache.utlx.core.ast.*
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.lexer.TokenType
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.udm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class LexerTest {
    @Test
    fun `tokenize simple expression`() {
        val source = """
            data.Customer.Name
        """.trimIndent()

        val lexer = Lexer(source)
        val tokens = lexer.tokenize()

        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
        assertEquals("data", tokens[0].lexeme)

        assertEquals(TokenType.DOT, tokens[1].type)
        assertEquals(TokenType.IDENTIFIER, tokens[2].type)
        assertEquals("Customer", tokens[2].lexeme)

        assertEquals(TokenType.DOT, tokens[3].type)
        assertEquals(TokenType.IDENTIFIER, tokens[4].type)
        assertEquals("Name", tokens[4].lexeme)
    }
    
    @Test
    fun `tokenize object literal`() {
        val source = """
            {
              name: "Alice",
              age: 30,
              active: true
            }
        """.trimIndent()
        
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        
        assertEquals(TokenType.LBRACE, tokens[0].type)
        assertEquals(TokenType.IDENTIFIER, tokens[1].type)
        assertEquals("name", tokens[1].lexeme)
        assertEquals(TokenType.COLON, tokens[2].type)
        assertEquals(TokenType.STRING, tokens[3].type)
        assertEquals("Alice", tokens[3].literal)
    }
    
    @Test
    fun `tokenize arithmetic expressions`() {
        val source = "42 + 3.14 * (10 - 5)"
        
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        
        assertEquals(TokenType.NUMBER, tokens[0].type)
        assertEquals(42.0, tokens[0].literal)
        assertEquals(TokenType.PLUS, tokens[1].type)
        assertEquals(TokenType.NUMBER, tokens[2].type)
        assertEquals(3.14, tokens[2].literal)
        assertEquals(TokenType.STAR, tokens[3].type)
    }
    
    @Test
    fun `tokenize pipe operator`() {
        val source = "input.items |> filter(x => x.price > 100)"
        
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        
        assertTrue(tokens.any { it.type == TokenType.PIPE })
        assertTrue(tokens.any { it.type == TokenType.ARROW })
    }
    
    @Test
    fun `tokenize comments`() {
        val source = """
            // This is a comment
            input.value // inline comment
            /* multi-line
               comment */
            output.result
        """.trimIndent()
        
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        
        // Comments should be filtered out
        assertFalse(tokens.any { it.type == TokenType.COMMENT })
        assertTrue(tokens.any { it.lexeme == "input" })
        assertTrue(tokens.any { it.lexeme == "output" })
    }
}

class ParserTest {
    @Test
    fun `parse simple program`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              name: input.name
            }
        """.trimIndent()
        
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val result = parser.parse()
        
        assertTrue(result is ParseResult.Success)
        val program = (result as ParseResult.Success).program
        
        assertEquals("1.0", program.header.version)
        assertEquals(FormatType.JSON, program.header.inputFormat.type)
        assertEquals(FormatType.JSON, program.header.outputFormat.type)
        assertTrue(program.body is Expression.ObjectLiteral)
    }
    
    @Test
    fun `parse object literal with properties`() {
        val source = """
            %utlx 1.0
            input xml
            output json
            ---
            {
              id: input.Order.@id,
              customer: input.Order.Customer.Name,
              total: 100.50
            }
        """.trimIndent()
        
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val result = parser.parse()
        
        assertTrue(result is ParseResult.Success)
        val program = (result as ParseResult.Success).program
        val obj = program.body as Expression.ObjectLiteral
        
        assertEquals(3, obj.properties.size)
        assertEquals("id", obj.properties[0].key)
        assertEquals("customer", obj.properties[1].key)
        assertEquals("total", obj.properties[2].key)
    }
    
    @Test
    fun `parse member access chain`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            input.Order.Customer.Address.Street
        """.trimIndent()
        
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val result = parser.parse()
        
        assertTrue(result is ParseResult.Success)
        val program = (result as ParseResult.Success).program
        
        var expr = program.body
        var depth = 0
        while (expr is Expression.MemberAccess) {
            depth++
            expr = expr.target
        }
        
        assertTrue(depth > 0, "Should have member access chain")
    }
    
    @Test
    fun `parse arithmetic expression`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            (10 + 5) * 2 - 3
        """.trimIndent()
        
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val result = parser.parse()
        
        assertTrue(result is ParseResult.Success)
        val program = (result as ParseResult.Success).program
        assertTrue(program.body is Expression.BinaryOp)
    }
    
    @Test
    fun `parse conditional expression`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            42 if input.type == "premium" else 21
        """.trimIndent()
        
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val result = parser.parse()
        
        assertTrue(result is ParseResult.Success)
        val program = (result as ParseResult.Success).program
        assertTrue(program.body is Expression.Conditional)
        
        val cond = program.body as Expression.Conditional
        assertTrue(cond.thenBranch is Expression.NumberLiteral)
        assertTrue(cond.elseBranch is Expression.NumberLiteral)
    }
    
    @Test
    fun `parse pipe expression`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            input.items |> filter(i => i.active) |> map(i => i.name)
        """.trimIndent()
        
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val result = parser.parse()
        
        assertTrue(result is ParseResult.Success)
        val program = (result as ParseResult.Success).program
        assertTrue(program.body is Expression.Pipe)
    }
}

class UDMTest {
    @Test
    fun `create scalar values`() {
        val str = UDM.Scalar.string("hello")
        val num = UDM.Scalar.number(42)
        val bool = UDM.Scalar.boolean(true)
        val nul = UDM.Scalar.nullValue()
        
        assertEquals("hello", str.asString())
        assertEquals(42, num.asNumber())
        assertEquals(true, bool.asBoolean())
        assertTrue(nul.isNull())
    }
    
    @Test
    fun `create array`() {
        val arr = UDM.Array.of(
            UDM.Scalar.number(1),
            UDM.Scalar.number(2),
            UDM.Scalar.number(3)
        )
        
        assertEquals(3, arr.size())
        assertEquals(1.0, arr.get(0)?.asScalar()?.asNumber())
        assertEquals(3.0, arr.last()?.asScalar()?.asNumber())
    }
    
    @Test
    fun `create object`() {
        val obj = UDM.Object.of(
            "name" to UDM.Scalar.string("Alice"),
            "age" to UDM.Scalar.number(30),
            "active" to UDM.Scalar.boolean(true)
        )
        
        assertEquals("Alice", obj.get("name")?.asScalar()?.asString())
        assertEquals(30.0, obj.get("age")?.asScalar()?.asNumber())
        assertEquals(true, obj.get("active")?.asScalar()?.asBoolean())
        assertTrue(obj.hasProperty("name"))
        assertFalse(obj.hasProperty("email"))
    }
    
    @Test
    fun `create nested structure`() {
        val customer = UDM.Object.of(
            "name" to UDM.Scalar.string("Bob"),
            "email" to UDM.Scalar.string("bob@example.com")
        )
        
        val order = UDM.Object.withAttributes(
            mapOf(
                "customer" to customer,
                "total" to UDM.Scalar.number(150.0)
            ),
            mapOf("id" to "ORD-001")
        )
        
        assertEquals("ORD-001", order.getAttribute("id"))
        assertEquals("Bob", 
            order.get("customer")?.asObject()?.get("name")?.asScalar()?.asString())
    }
    
    @Test
    fun `navigate simple path`() {
        val data = UDM.Object.of(
            "Order" to UDM.Object.of(
                "Customer" to UDM.Object.of(
                    "Name" to UDM.Scalar.string("Alice")
                )
            )
        )
        
        val navigator = UDMNavigator(data)
        val result = navigator.navigate("Order.Customer.Name")
        
        assertNotNull(result)
        assertEquals("Alice", result?.asScalar()?.asString())
    }
    
    @Test
    fun `navigate with array index`() {
        val data = UDM.Object.of(
            "items" to UDM.Array.of(
                UDM.Scalar.number(10),
                UDM.Scalar.number(20),
                UDM.Scalar.number(30)
            )
        )
        
        val navigator = UDMNavigator(data)
        val result = navigator.navigate("items[1]")
        
        assertNotNull(result)
        assertEquals(20.0, result?.asScalar()?.asNumber())
    }
    
    @Test
    fun `navigate with attribute`() {
        val data = UDM.Object.withAttributes(
            mapOf("value" to UDM.Scalar.number(100)),
            mapOf("id" to "123", "type" to "premium")
        )
        
        val navigator = UDMNavigator(data)
        val resultId = navigator.navigate("@id")
        val resultType = navigator.navigate("@type")
        
        assertEquals("123", resultId?.asScalar()?.asString())
        assertEquals("premium", resultType?.asScalar()?.asString())
    }
    
    @Test
    fun `recursive descent search`() {
        val data = UDM.Object.of(
            "Order" to UDM.Object.of(
                "Items" to UDM.Array.of(
                    UDM.Object.of("ProductCode" to UDM.Scalar.string("WIDGET-001")),
                    UDM.Object.of("ProductCode" to UDM.Scalar.string("GADGET-002"))
                )
            )
        )
        
        val navigator = UDMNavigator(data)
        val results = navigator.recursiveDescent("ProductCode")
        
        assertEquals(2, results.size)
        assertEquals("WIDGET-001", results[0].asScalar()?.asString())
        assertEquals("GADGET-002", results[1].asScalar()?.asString())
    }
    
    @Test
    fun `wildcard navigation in array`() {
        val data = UDM.Object.of(
            "items" to UDM.Array.of(
                UDM.Object.of("price" to UDM.Scalar.number(10)),
                UDM.Object.of("price" to UDM.Scalar.number(20)),
                UDM.Object.of("price" to UDM.Scalar.number(30))
            )
        )
        
        val navigator = UDMNavigator(data)
        val results = navigator.navigate("items[*].price")
        
        assertNotNull(results)
        assertTrue(results is UDM.Array)
        assertEquals(3, (results as UDM.Array).size())
    }
}

class IntegrationTest {
    @Test
    fun `end-to-end parse and represent simple transformation`() {
        val source = """
            %utlx 1.0
            input xml
            output json
            ---
            {
              invoice: {
                id: input.Order.@id,
                customer: input.Order.Customer.Name,
                total: input.Order.Total
              }
            }
        """.trimIndent()
        
        // Tokenize
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        assertTrue(tokens.isNotEmpty())
        
        // Parse
        val parser = Parser(tokens)
        val result = parser.parse()
        assertTrue(result is ParseResult.Success)
        
        val program = (result as ParseResult.Success).program
        
        // Verify structure
        assertEquals(FormatType.XML, program.header.inputFormat.type)
        assertEquals(FormatType.JSON, program.header.outputFormat.type)
        
        val body = program.body as Expression.ObjectLiteral
        assertEquals(1, body.properties.size)
        assertEquals("invoice", body.properties[0].key)
        
        val invoice = body.properties[0].value as Expression.ObjectLiteral
        assertEquals(3, invoice.properties.size)
    }
    
    @Test
    fun `UDM and navigation work together`() {
        // Create XML-like UDM structure
        val xmlData = UDM.Object.withAttributes(
            mapOf(
                "Customer" to UDM.Object.of(
                    "Name" to UDM.Scalar.string("Alice Johnson"),
                    "Email" to UDM.Scalar.string("alice@example.com")
                ),
                "Total" to UDM.Scalar.number(299.99)
            ),
            mapOf("id" to "ORD-001")
        )
        
        val order = UDM.Object.of("Order" to xmlData)
        
        // Navigate like in UTL-X
        val navigator = UDMNavigator(order)
        
        val orderId = navigator.navigate("Order.@id")
        val customerName = navigator.navigate("Order.Customer.Name")
        val total = navigator.navigate("Order.Total")
        
        assertEquals("ORD-001", orderId?.asScalar()?.asString())
        assertEquals("Alice Johnson", customerName?.asScalar()?.asString())
        assertEquals(299.99, total?.asScalar()?.asNumber())
    }
}
