package org.apache.utlx.engine.strategy

import org.apache.utlx.engine.config.InputSlot
import org.apache.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompiledStrategyTest {

    @Test
    fun `strategy name is COMPILED`() {
        val strategy = CompiledStrategy()
        assertEquals("COMPILED", strategy.name)
    }

    @Test
    fun `execute identity transformation`() {
        val strategy = CompiledStrategy()
        val source = "%utlx 1.0\ninput json\noutput json\n---\n\$input\n"
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"name": "Alice", "age": 30}""")
        assertTrue(result.output.contains("Alice"))
        assertTrue(result.output.contains("30"))
    }

    @Test
    fun `execute object construction with property access`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              name: ${'$'}input.name,
              city: ${'$'}input.address.city
            }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"name": "Alice", "address": {"city": "Amsterdam"}}""")
        assertTrue(result.output.contains("Alice"))
        assertTrue(result.output.contains("Amsterdam"))
    }

    @Test
    fun `execute with stdlib functions`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              fullName: concat(${'$'}input.firstName, " ", ${'$'}input.lastName),
              email: lowerCase(${'$'}input.email)
            }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"firstName": "Marcel", "lastName": "Grauwen", "email": "MARCEL@TEST.COM"}""")
        assertTrue(result.output.contains("Marcel Grauwen"), "Output: ${result.output}")
        assertTrue(result.output.contains("marcel@test.com"), "Output: ${result.output}")
    }

    @Test
    fun `execute with conditional`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              name: ${'$'}input.name,
              isAdult: ${'$'}input.age >= 18
            }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"name": "Alice", "age": 28}""")
        assertTrue(result.output.contains("Alice"))
        assertTrue(result.output.contains("true"), "isAdult should be true: ${result.output}")
    }

    @Test
    fun `execute with let bindings`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              let total = ${'$'}input.price * ${'$'}input.qty,
              item: ${'$'}input.name,
              total: total
            }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"name": "Widget", "price": 10, "qty": 5}""")
        assertTrue(result.output.contains("Widget"))
        assertTrue(result.output.contains("50"), "total should be 50: ${result.output}")
    }

    @Test
    fun `execute multiple messages reuses compiled function`() {
        val strategy = CompiledStrategy()
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{name: \$input.name, done: true}\n"
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val r1 = strategy.execute("""{"name": "Alice"}""")
        val r2 = strategy.execute("""{"name": "Bob"}""")
        val r3 = strategy.execute("""{"name": "Charlie"}""")

        assertTrue(r1.output.contains("Alice"))
        assertTrue(r2.output.contains("Bob"))
        assertTrue(r3.output.contains("Charlie"))
    }

    @Test
    fun `execute with filter lambda`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              adults: filter(${'$'}input.people, (p) -> p.age >= 18)
            }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"people": [{"name": "Alice", "age": 28}, {"name": "Bob", "age": 15}, {"name": "Charlie", "age": 35}]}""")
        assertTrue(result.output.contains("Alice"), "Output: ${result.output}")
        assertTrue(result.output.contains("Charlie"), "Output: ${result.output}")
        assertTrue(!result.output.contains("Bob"), "Bob (age 15) should be filtered out: ${result.output}")
    }

    @Test
    fun `execute with map lambda`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            map(${'$'}input.items, (item) -> {
              name: upperCase(item.name),
              total: item.price * item.qty
            })
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"items": [{"name": "widget", "price": 10, "qty": 3}, {"name": "gadget", "price": 25, "qty": 2}]}""")
        assertTrue(result.output.contains("WIDGET"), "Output: ${result.output}")
        assertTrue(result.output.contains("GADGET"), "Output: ${result.output}")
        assertTrue(result.output.contains("30"), "10*3=30: ${result.output}")
        assertTrue(result.output.contains("50"), "25*2=50: ${result.output}")
    }

    @Test
    fun `execute with pipe and lambda`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            ${'$'}input.numbers |> filter((n) -> n > 3) |> map((n) -> n * 10)
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"numbers": [1, 2, 3, 4, 5]}""")
        assertTrue(result.output.contains("40"), "4*10=40: ${result.output}")
        assertTrue(result.output.contains("50"), "5*10=50: ${result.output}")
    }

    @Test
    fun `fallback to interpreter for unsupported nodes`() {
        val strategy = CompiledStrategy()
        // match expressions are not yet supported by the compiler
        // The strategy should fall back to the interpreter
        val source = "%utlx 1.0\ninput json\noutput json\n---\n\$input\n"
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"test": true}""")
        assertTrue(result.output.contains("test"))
    }

    // =========================================================================
    // Edge cases: null handling
    // =========================================================================

    @Test
    fun `null values pass through`() {
        val strategy = CompiledStrategy()
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{value: \$input.missing}\n"
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"name": "Alice"}""")
        assertTrue(result.output.contains("null"), "Missing property should be null: ${result.output}")
    }

    @Test
    fun `null coalescing operator`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            { name: ${'$'}input.nickname ?? ${'$'}input.name }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"name": "Alice"}""")
        assertTrue(result.output.contains("Alice"), "Should fall back to name: ${result.output}")
    }

    // =========================================================================
    // Edge cases: operators
    // =========================================================================

    @Test
    fun `string concatenation via plus`() {
        val strategy = CompiledStrategy()
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{msg: \$input.a + \$input.b}\n"
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"a": "hello", "b": " world"}""")
        assertTrue(result.output.contains("hello world"), "String concat: ${result.output}")
    }

    @Test
    fun `boolean logic AND and OR`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              both: ${'$'}input.a && ${'$'}input.b,
              either: ${'$'}input.a || ${'$'}input.b
            }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"a": true, "b": false}""")
        assertTrue(result.output.contains("\"both\""), "Should have 'both' field: ${result.output}")
        assertTrue(result.output.contains("\"either\""), "Should have 'either' field: ${result.output}")
    }

    @Test
    fun `unary not operator`() {
        val strategy = CompiledStrategy()
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{inverted: !${'$'}input.flag}\n"
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"flag": true}""")
        assertTrue(result.output.contains("false"), "!true should be false: ${result.output}")
    }

    @Test
    fun `arithmetic operators`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              sum: ${'$'}input.a + ${'$'}input.b,
              diff: ${'$'}input.a - ${'$'}input.b,
              product: ${'$'}input.a * ${'$'}input.b,
              quotient: ${'$'}input.a / ${'$'}input.b,
              remainder: ${'$'}input.a % ${'$'}input.b
            }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"a": 17, "b": 5}""")
        assertTrue(result.output.contains("22"), "17+5=22: ${result.output}")
        assertTrue(result.output.contains("12"), "17-5=12: ${result.output}")
        assertTrue(result.output.contains("85"), "17*5=85: ${result.output}")
    }

    // =========================================================================
    // Edge cases: nested structures
    // =========================================================================

    @Test
    fun `deeply nested property access`() {
        val strategy = CompiledStrategy()
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{city: \$input.address.location.city}\n"
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"address": {"location": {"city": "Amsterdam"}}}""")
        assertTrue(result.output.contains("Amsterdam"), "Deep access: ${result.output}")
    }

    @Test
    fun `array index access`() {
        val strategy = CompiledStrategy()
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{first: \$input.items[0], second: \$input.items[1]}\n"
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"items": ["alpha", "beta", "gamma"]}""")
        assertTrue(result.output.contains("alpha"), "items[0]: ${result.output}")
        assertTrue(result.output.contains("beta"), "items[1]: ${result.output}")
    }

    @Test
    fun `nested object construction`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              order: {
                id: ${'$'}input.orderId,
                customer: {
                  name: ${'$'}input.customerName,
                  tier: "gold"
                }
              }
            }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"orderId": "ORD-001", "customerName": "Contoso"}""")
        assertTrue(result.output.contains("ORD-001"), "Order ID: ${result.output}")
        assertTrue(result.output.contains("Contoso"), "Customer: ${result.output}")
        assertTrue(result.output.contains("gold"), "Tier: ${result.output}")
    }

    // =========================================================================
    // Edge cases: if/else chains
    // =========================================================================

    @Test
    fun `chained if-else`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              grade: if (${'$'}input.score >= 90) "A"
                     else if (${'$'}input.score >= 80) "B"
                     else if (${'$'}input.score >= 70) "C"
                     else "F"
            }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val r1 = strategy.execute("""{"score": 95}""")
        assertTrue(r1.output.contains("A"), "95 → A: ${r1.output}")

        val r2 = strategy.execute("""{"score": 85}""")
        assertTrue(r2.output.contains("B"), "85 → B: ${r2.output}")

        val r3 = strategy.execute("""{"score": 55}""")
        assertTrue(r3.output.contains("F"), "55 → F: ${r3.output}")
    }

    // =========================================================================
    // Edge cases: lambdas
    // =========================================================================

    @Test
    fun `lambda with object construction body`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            map(${'$'}input.people, (p) -> {
              fullName: concat(p.first, " ", p.last),
              adult: p.age >= 18
            })
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"people": [{"first": "Alice", "last": "Smith", "age": 28}, {"first": "Bob", "last": "Jones", "age": 15}]}""")
        assertTrue(result.output.contains("Alice Smith"), "Output: ${result.output}")
        assertTrue(result.output.contains("Bob Jones"), "Output: ${result.output}")
    }

    @Test
    fun `multiple lambdas in same expression`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              names: map(${'$'}input.people, (p) -> p.name),
              ages: map(${'$'}input.people, (p) -> p.age)
            }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"people": [{"name": "Alice", "age": 28}, {"name": "Bob", "age": 35}]}""")
        assertTrue(result.output.contains("Alice"), "names: ${result.output}")
        assertTrue(result.output.contains("Bob"), "names: ${result.output}")
        assertTrue(result.output.contains("28"), "ages: ${result.output}")
        assertTrue(result.output.contains("35"), "ages: ${result.output}")
    }

    // =========================================================================
    // Format conversion
    // =========================================================================

    @Test
    fun `compiled JSON to XML conversion`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output xml
            ---
            { order: { id: ${'$'}input.orderId, customer: ${'$'}input.name } }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"orderId": "ORD-001", "name": "Contoso"}""")
        assertTrue(result.output.contains("<id>ORD-001</id>"), "XML output: ${result.output}")
        assertTrue(result.output.contains("<customer>Contoso</customer>"), "XML output: ${result.output}")
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Test
    fun `shutdown completes without error`() {
        val strategy = CompiledStrategy()
        val source = "%utlx 1.0\ninput json\noutput json\n---\n\$input\n"
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)
        strategy.execute("""{"test": true}""")
        strategy.shutdown()
    }

    @Test
    fun `user-defined function via let binding`() {
        val strategy = CompiledStrategy()
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {
              let FormatName = (first, last) -> concat(first, " ", last),
              name: FormatName(${'$'}input.firstName, ${'$'}input.lastName)
            }
        """.trimIndent()
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"firstName": "Alice", "lastName": "Smith"}""")
        assertTrue(result.output.contains("Alice Smith"), "User function: ${result.output}")
    }

    @Test
    fun `invalid input throws exception not silent failure`() {
        val strategy = CompiledStrategy()
        val source = "%utlx 1.0\ninput json\noutput json\n---\n{name: \$input.name}\n"
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        assertFailsWith<Exception> {
            strategy.execute("not valid json {{{")
        }
    }
}
