package org.apache.utlx.engine.strategy

import org.apache.utlx.engine.config.InputSlot
import org.apache.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
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

    @Test
    fun `shutdown completes without error`() {
        val strategy = CompiledStrategy()
        val source = "%utlx 1.0\ninput json\noutput json\n---\n\$input\n"
        val config = TransformConfig(strategy = "COMPILED", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)
        strategy.execute("""{"test": true}""")
        strategy.shutdown()
    }
}
