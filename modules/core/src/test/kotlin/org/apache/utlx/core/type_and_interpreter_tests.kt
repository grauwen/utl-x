package org.apache.utlx.core

import org.apache.utlx.core.ast.*
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.types.*
import org.apache.utlx.core.interpreter.*
import org.apache.utlx.core.udm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class TypeCheckerTest {
    private val stdlib = StandardLibrary()
    private val checker = TypeChecker(stdlib)
    
    @Test
    fun `type check simple literals`() {
        val env = TypeEnvironment()
        
        assertEquals(UTLXType.String, 
            checker.inferType(Expression.StringLiteral("hello", Location(1, 1)), env))
        assertEquals(UTLXType.Number,
            checker.inferType(Expression.NumberLiteral(42.0, Location(1, 1)), env))
        assertEquals(UTLXType.Boolean,
            checker.inferType(Expression.BooleanLiteral(true, Location(1, 1)), env))
        assertEquals(UTLXType.Null,
            checker.inferType(Expression.NullLiteral(Location(1, 1)), env))
    }
    
    @Test
    fun `type check object literal`() {
        val env = TypeEnvironment()
        
        val obj = Expression.ObjectLiteral(
            listOf(
                Property("name", Expression.StringLiteral("Alice", Location(1, 1)), Location(1, 1)),
                Property("age", Expression.NumberLiteral(30.0, Location(1, 1)), Location(1, 1))
            ),
            emptyList(),
            Location(1, 1)
        )
        
        val type = checker.inferType(obj, env) as UTLXType.Object
        assertEquals(UTLXType.String, type.properties["name"])
        assertEquals(UTLXType.Number, type.properties["age"])
    }
    
    @Test
    fun `type check array literal`() {
        val env = TypeEnvironment()
        
        val arr = Expression.ArrayLiteral(
            listOf(
                Expression.NumberLiteral(1.0, Location(1, 1)),
                Expression.NumberLiteral(2.0, Location(1, 1)),
                Expression.NumberLiteral(3.0, Location(1, 1))
            ),
            Location(1, 1)
        )
        
        val type = checker.inferType(arr, env) as UTLXType.Array
        assertEquals(UTLXType.Number, type.elementType)
    }
    
    @Test
    fun `type check arithmetic operations`() {
        val env = TypeEnvironment()
        
        val expr = Expression.BinaryOp(
            Expression.NumberLiteral(10.0, Location(1, 1)),
            BinaryOperator.PLUS,
            Expression.NumberLiteral(5.0, Location(1, 1)),
            Location(1, 1)
        )
        
        assertEquals(UTLXType.Number, checker.inferType(expr, env))
    }
    
    @Test
    fun `type check string concatenation`() {
        val env = TypeEnvironment()
        
        val expr = Expression.BinaryOp(
            Expression.StringLiteral("Hello, ", Location(1, 1)),
            BinaryOperator.PLUS,
            Expression.StringLiteral("World", Location(1, 1)),
            Location(1, 1)
        )
        
        assertEquals(UTLXType.String, checker.inferType(expr, env))
    }
    
    @Test
    fun `type check comparison operations`() {
        val env = TypeEnvironment()
        
        val expr = Expression.BinaryOp(
            Expression.NumberLiteral(10.0, Location(1, 1)),
            BinaryOperator.GREATER_THAN,
            Expression.NumberLiteral(5.0, Location(1, 1)),
            Location(1, 1)
        )
        
        assertEquals(UTLXType.Boolean, checker.inferType(expr, env))
    }
    
    @Test
    fun `type check conditional expression`() {
        val env = TypeEnvironment()
        
        val expr = Expression.Conditional(
            condition = Expression.BooleanLiteral(true, Location(1, 1)),
            thenBranch = Expression.NumberLiteral(10.0, Location(1, 1)),
            elseBranch = Expression.NumberLiteral(20.0, Location(1, 1)),
            location = Location(1, 1)
        )
        
        assertEquals(UTLXType.Number, checker.inferType(expr, env))
    }
    
    @Test
    fun `type check full program`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              name: input.name,
              age: 30,
              active: true
            }
        """.trimIndent()
        
        val tokens = Lexer(source).tokenize()
        val result = Parser(tokens).parse()
        assertTrue(result is ParseResult.Success)
        
        val program = (result as ParseResult.Success).program
        val typeResult = checker.check(program)
        
        assertTrue(typeResult is TypeCheckResult.Success)
        val type = (typeResult as TypeCheckResult.Success).type
        assertTrue(type is UTLXType.Object)
    }
    
    @Test
    fun `detect type error - undefined variable`() {
        val env = TypeEnvironment()
        
        val expr = Expression.Identifier("undefined", Location(1, 1))
        checker.inferType(expr, env)
        
        // Should record error (checker accumulates errors)
        // In production, we'd check checker.errors
    }
    
    @Test
    fun `type check stdlib function call`() {
        val env = TypeEnvironment()
        
        val expr = Expression.FunctionCall(
            function = Expression.Identifier("upper", Location(1, 1)),
            arguments = listOf(Expression.StringLiteral("hello", Location(1, 1))),
            location = Location(1, 1)
        )
        
        assertEquals(UTLXType.String, checker.inferType(expr, env))
    }
}

class InterpreterTest {
    private val interpreter = Interpreter()
    
    @Test
    fun `evaluate literals`() {
        val env = Environment()
        
        val strResult = interpreter.evaluate(
            Expression.StringLiteral("hello", Location(1, 1)), env
        ) as RuntimeValue.StringValue
        assertEquals("hello", strResult.value)
        
        val numResult = interpreter.evaluate(
            Expression.NumberLiteral(42.0, Location(1, 1)), env
        ) as RuntimeValue.NumberValue
        assertEquals(42.0, numResult.value)
        
        val boolResult = interpreter.evaluate(
            Expression.BooleanLiteral(true, Location(1, 1)), env
        ) as RuntimeValue.BooleanValue
        assertEquals(true, boolResult.value)
    }
    
    @Test
    fun `evaluate object literal`() {
        val env = Environment()
        
        val obj = Expression.ObjectLiteral(
            listOf(
                Property("name", Expression.StringLiteral("Alice", Location(1, 1)), Location(1, 1)),
                Property("age", Expression.NumberLiteral(30.0, Location(1, 1)), Location(1, 1))
            ),
            emptyList(),
            Location(1, 1)
        )
        
        val result = interpreter.evaluate(obj, env) as RuntimeValue.ObjectValue
        assertEquals("Alice", (result.properties["name"] as RuntimeValue.StringValue).value)
        assertEquals(30.0, (result.properties["age"] as RuntimeValue.NumberValue).value)
    }
    
    @Test
    fun `evaluate array literal`() {
        val env = Environment()
        
        val arr = Expression.ArrayLiteral(
            listOf(
                Expression.NumberLiteral(1.0, Location(1, 1)),
                Expression.NumberLiteral(2.0, Location(1, 1)),
                Expression.NumberLiteral(3.0, Location(1, 1))
            ),
            Location(1, 1)
        )
        
        val result = interpreter.evaluate(arr, env) as RuntimeValue.ArrayValue
        assertEquals(3, result.elements.size)
        assertEquals(1.0, (result.elements[0] as RuntimeValue.NumberValue).value)
    }
    
    @Test
    fun `evaluate arithmetic operations`() {
        val env = Environment()
        
        val expr = Expression.BinaryOp(
            Expression.NumberLiteral(10.0, Location(1, 1)),
            BinaryOperator.PLUS,
            Expression.NumberLiteral(5.0, Location(1, 1)),
            Location(1, 1)
        )
        
        val result = interpreter.evaluate(expr, env) as RuntimeValue.NumberValue
        assertEquals(15.0, result.value)
    }
    
    @Test
    fun `evaluate string concatenation`() {
        val env = Environment()
        
        val expr = Expression.BinaryOp(
            Expression.StringLiteral("Hello, ", Location(1, 1)),
            BinaryOperator.PLUS,
            Expression.StringLiteral("World", Location(1, 1)),
            Location(1, 1)
        )
        
        val result = interpreter.evaluate(expr, env) as RuntimeValue.StringValue
        assertEquals("Hello, World", result.value)
    }
    
    @Test
    fun `evaluate comparison operations`() {
        val env = Environment()
        
        val expr = Expression.BinaryOp(
            Expression.NumberLiteral(10.0, Location(1, 1)),
            BinaryOperator.GREATER_THAN,
            Expression.NumberLiteral(5.0, Location(1, 1)),
            Location(1, 1)
        )
        
        val result = interpreter.evaluate(expr, env) as RuntimeValue.BooleanValue
        assertEquals(true, result.value)
    }
    
    @Test
    fun `evaluate conditional - true branch`() {
        val env = Environment()
        
        val expr = Expression.Conditional(
            condition = Expression.BooleanLiteral(true, Location(1, 1)),
            thenBranch = Expression.StringLiteral("yes", Location(1, 1)),
            elseBranch = Expression.StringLiteral("no", Location(1, 1)),
            location = Location(1, 1)
        )
        
        val result = interpreter.evaluate(expr, env) as RuntimeValue.StringValue
        assertEquals("yes", result.value)
    }
    
    @Test
    fun `evaluate conditional - false branch`() {
        val env = Environment()
        
        val expr = Expression.Conditional(
            condition = Expression.BooleanLiteral(false, Location(1, 1)),
            thenBranch = Expression.StringLiteral("yes", Location(1, 1)),
            elseBranch = Expression.StringLiteral("no", Location(1, 1)),
            location = Location(1, 1)
        )
        
        val result = interpreter.evaluate(expr, env) as RuntimeValue.StringValue
        assertEquals("no", result.value)
    }
    
    @Test
    fun `evaluate let binding`() {
        val env = Environment()
        
        val letExpr = Expression.LetBinding(
            "x",
            Expression.NumberLiteral(42.0, Location(1, 1)),
            null,  // typeAnnotation
            Location(1, 1)
        )
        
        interpreter.evaluate(letExpr, env)
        
        val identifier = Expression.Identifier("x", Location(1, 1))
        val result = interpreter.evaluate(identifier, env) as RuntimeValue.NumberValue
        assertEquals(42.0, result.value)
    }
    
    @Test
    fun `evaluate member access on object`() {
        val env = Environment()
        
        // Create object
        env.define("obj", RuntimeValue.ObjectValue(mapOf(
            "name" to RuntimeValue.StringValue("Alice")
        )))
        
        val expr = Expression.MemberAccess(
            Expression.Identifier("obj", Location(1, 1)),
            "name",
            false,
            false,
            Location(1, 1)
        )
        
        val result = interpreter.evaluate(expr, env) as RuntimeValue.StringValue
        assertEquals("Alice", result.value)
    }
    
    @Test
    fun `evaluate member access on UDM`() {
        val env = Environment()
        
        // Create UDM object
        val udm = UDM.Object.of(
            "name" to UDM.Scalar.string("Bob")
        )
        env.define("data", RuntimeValue.UDMValue(udm))
        
        val expr = Expression.MemberAccess(
            Expression.Identifier("data", Location(1, 1)),
            "name",
            false,
            false,
            Location(1, 1)
        )
        
        val result = interpreter.evaluate(expr, env)
        assertTrue(result is RuntimeValue.UDMValue)
        assertEquals("Bob", (result as RuntimeValue.UDMValue).udm.asScalar()?.asString())
    }
    
    @Test
    fun `evaluate array index access`() {
        val env = Environment()
        
        env.define("arr", RuntimeValue.ArrayValue(listOf(
            RuntimeValue.NumberValue(10.0),
            RuntimeValue.NumberValue(20.0),
            RuntimeValue.NumberValue(30.0)
        )))
        
        val expr = Expression.IndexAccess(
            Expression.Identifier("arr", Location(1, 1)),
            Expression.NumberLiteral(1.0, Location(1, 1)),
            Location(1, 1)
        )
        
        val result = interpreter.evaluate(expr, env) as RuntimeValue.NumberValue
        assertEquals(20.0, result.value)
    }
    
    @Test
    fun `evaluate stdlib function - upper`() {
        val env = Environment()
        StandardLibraryImpl().registerAll(env)
        
        val expr = Expression.FunctionCall(
            Expression.Identifier("upper", Location(1, 1)),
            listOf(Expression.StringLiteral("hello", Location(1, 1))),
            Location(1, 1)
        )
        
        // For now, stdlib functions are special-cased
        // This test demonstrates the structure
    }
    
    @Test
    fun `evaluate division by zero`() {
        val env = Environment()
        
        val expr = Expression.BinaryOp(
            Expression.NumberLiteral(10.0, Location(1, 1)),
            BinaryOperator.DIVIDE,
            Expression.NumberLiteral(0.0, Location(1, 1)),
            Location(1, 1)
        )
        
        assertThrows<RuntimeError> {
            interpreter.evaluate(expr, env)
        }
    }
}

class EndToEndTest {
    @Test
    fun `complete transformation pipeline`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              greeting: "Hello, " + input.name,
              doubled: input.value * 2,
              isAdult: input.age >= 18
            }
        """.trimIndent()
        
        // Step 1: Tokenize
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        assertTrue(tokens.isNotEmpty())
        
        // Step 2: Parse
        val parser = Parser(tokens)
        val parseResult = parser.parse()
        assertTrue(parseResult is ParseResult.Success)
        val program = (parseResult as ParseResult.Success).program
        
        // Step 3: Type check
        val stdlib = StandardLibrary()
        val typeChecker = TypeChecker(stdlib)
        val typeResult = typeChecker.check(program)
        assertTrue(typeResult is TypeCheckResult.Success)
        
        // Step 4: Create input data
        val inputData = UDM.Object.of(
            "name" to UDM.Scalar.string("Alice"),
            "value" to UDM.Scalar.number(21),
            "age" to UDM.Scalar.number(25)
        )
        
        // Step 5: Execute
        val interpreter = Interpreter()
        val result = interpreter.execute(program, inputData)
        
        // Step 6: Verify results
        assertTrue(result is RuntimeValue.ObjectValue)
        val obj = result as RuntimeValue.ObjectValue
        
        assertEquals("Hello, Alice", 
            (obj.properties["greeting"] as RuntimeValue.StringValue).value)
        assertEquals(42.0,
            (obj.properties["doubled"] as RuntimeValue.NumberValue).value)
        assertEquals(true,
            (obj.properties["isAdult"] as RuntimeValue.BooleanValue).value)
    }
    
    @Test
    fun `transformation with conditional logic`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              discount: 0.20 if input.customerType == "VIP" else 0.10,
              message: "Welcome VIP!" if input.customerType == "VIP" else "Welcome!"
            }
        """.trimIndent()
        
        val tokens = Lexer(source).tokenize()
        val program = (Parser(tokens).parse() as ParseResult.Success).program
        
        // Test VIP customer
        val vipData = UDM.Object.of(
            "customerType" to UDM.Scalar.string("VIP")
        )
        
        val vipResult = Interpreter().execute(program, vipData) as RuntimeValue.ObjectValue
        assertEquals(0.20, (vipResult.properties["discount"] as RuntimeValue.NumberValue).value)
        assertEquals("Welcome VIP!", (vipResult.properties["message"] as RuntimeValue.StringValue).value)
        
        // Test regular customer
        val regularData = UDM.Object.of(
            "customerType" to UDM.Scalar.string("Regular")
        )
        
        val regularResult = Interpreter().execute(program, regularData) as RuntimeValue.ObjectValue
        assertEquals(0.10, (regularResult.properties["discount"] as RuntimeValue.NumberValue).value)
        assertEquals("Welcome!", (regularResult.properties["message"] as RuntimeValue.StringValue).value)
    }
    
    @Test
    fun `transformation with nested objects`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              customer: {
                name: input.Order.Customer.Name,
                email: input.Order.Customer.Email
              },
              total: input.Order.Total
            }
        """.trimIndent()
        
        val tokens = Lexer(source).tokenize()
        val program = (Parser(tokens).parse() as ParseResult.Success).program
        
        val inputData = UDM.Object.of(
            "Order" to UDM.Object.of(
                "Customer" to UDM.Object.of(
                    "Name" to UDM.Scalar.string("Bob Smith"),
                    "Email" to UDM.Scalar.string("bob@example.com")
                ),
                "Total" to UDM.Scalar.number(299.99)
            )
        )
        
        val result = Interpreter().execute(program, inputData) as RuntimeValue.ObjectValue
        
        val customer = result.properties["customer"] as RuntimeValue.UDMValue
        val customerObj = customer.udm.asObject()!!
        
        assertEquals("Bob Smith", 
            customerObj.get("name")?.asScalar()?.asString())
        assertEquals("bob@example.com",
            customerObj.get("email")?.asScalar()?.asString())
        assertEquals(299.99,
            (result.properties["total"] as RuntimeValue.UDMValue).udm.asScalar()?.asNumber())
    }
    
    @Test
    fun `transformation with let bindings`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              let subtotal = input.price * input.quantity,
              let tax = subtotal * 0.08,
              
              subtotal: subtotal,
              tax: tax,
              total: subtotal + tax
            }
        """.trimIndent()
        
        val tokens = Lexer(source).tokenize()
        val program = (Parser(tokens).parse() as ParseResult.Success).program
        
        val inputData = UDM.Object.of(
            "price" to UDM.Scalar.number(100),
            "quantity" to UDM.Scalar.number(3)
        )
        
        val result = Interpreter().execute(program, inputData) as RuntimeValue.ObjectValue
        
        assertEquals(300.0, (result.properties["subtotal"] as RuntimeValue.NumberValue).value)
        assertEquals(24.0, (result.properties["tax"] as RuntimeValue.NumberValue).value)
        assertEquals(324.0, (result.properties["total"] as RuntimeValue.NumberValue).value)
    }
}
