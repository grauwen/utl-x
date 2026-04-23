package org.apache.utlx.engine.strategy

import org.apache.utlx.engine.config.InputSlot
import org.apache.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CopyStrategyTest {

    @Test
    fun `strategy name is COPY`() {
        val strategy = CopyStrategy()
        assertEquals("COPY", strategy.name)
    }

    @Test
    fun `execute identity transformation`() {
        val strategy = CopyStrategy()
        val source = """%utlx 1.0
input json
output json
---
${'$'}input
"""
        val config = TransformConfig(strategy = "COPY", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"name": "Alice", "age": 30}""")
        assertTrue(result.output.contains("Alice"))
        assertTrue(result.output.contains("30"))
    }

    @Test
    fun `execute transformation with stdlib functions`() {
        val strategy = CopyStrategy()
        val source = """%utlx 1.0
input json
output json
---
{
  fullName: concat(${'$'}input.firstName, " ", ${'$'}input.lastName),
  email: lowerCase(${'$'}input.email)
}
"""
        val config = TransformConfig(strategy = "COPY", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"firstName": "Marcel", "lastName": "Grauwen", "email": "MARCEL@TEST.COM"}""")
        assertTrue(result.output.contains("Marcel Grauwen"))
        assertTrue(result.output.contains("marcel@test.com"))
    }

    @Test
    fun `skeleton is reused across executions`() {
        val strategy = CopyStrategy()
        val source = """%utlx 1.0
input json
output json
---
{ name: ${'$'}input.name, processed: true }
"""
        val config = TransformConfig(strategy = "COPY", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        // Execute multiple times — skeleton built from first, reused for rest
        val r1 = strategy.execute("""{"name": "Alice"}""")
        val r2 = strategy.execute("""{"name": "Bob"}""")
        val r3 = strategy.execute("""{"name": "Charlie"}""")

        assertTrue(r1.output.contains("Alice"))
        assertTrue(r2.output.contains("Bob"))
        assertTrue(r3.output.contains("Charlie"))
    }

    @Test
    fun `execute with format conversion JSON to XML`() {
        val strategy = CopyStrategy()
        val source = """%utlx 1.0
input json
output xml
---
{ order: { id: ${'$'}input.orderId, customer: ${'$'}input.name } }
"""
        val config = TransformConfig(strategy = "COPY", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val result = strategy.execute("""{"orderId": "ORD-001", "name": "Contoso"}""")
        assertTrue(result.output.contains("<id>ORD-001</id>"))
        assertTrue(result.output.contains("<customer>Contoso</customer>"))
    }

    @Test
    fun `executeBatch processes multiple messages`() {
        val strategy = CopyStrategy()
        val source = """%utlx 1.0
input json
output json
---
{ name: upperCase(${'$'}input.name) }
"""
        val config = TransformConfig(strategy = "COPY", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        val results = strategy.executeBatch(listOf(
            """{"name": "alice"}""",
            """{"name": "bob"}""",
            """{"name": "charlie"}"""
        ))

        assertEquals(3, results.size)
        assertTrue(results[0].output.contains("ALICE"))
        assertTrue(results[1].output.contains("BOB"))
        assertTrue(results[2].output.contains("CHARLIE"))
    }

    @Test
    fun `execute with invalid input throws exception`() {
        val strategy = CopyStrategy()
        val source = """%utlx 1.0
input json
output json
---
${'$'}input
"""
        val config = TransformConfig(strategy = "COPY", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)

        assertFailsWith<Exception> {
            strategy.execute("not valid json {{{")
        }
    }

    @Test
    fun `shutdown clears pool`() {
        val strategy = CopyStrategy()
        val source = """%utlx 1.0
input json
output json
---
${'$'}input
"""
        val config = TransformConfig(strategy = "COPY", inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)
        strategy.execute("""{"test": true}""")
        strategy.shutdown() // Should not throw
    }
}
