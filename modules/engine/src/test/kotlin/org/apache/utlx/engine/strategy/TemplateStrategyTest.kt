package org.apache.utlx.engine.strategy

import org.apache.utlx.engine.config.InputSlot
import org.apache.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TemplateStrategyTest {

    @Test
    fun `strategy name is TEMPLATE`() {
        val strategy = TemplateStrategy()
        assertEquals("TEMPLATE", strategy.name)
    }

    @Test
    fun `execute simple identity transformation`() {
        val strategy = TemplateStrategy()
        val source = """%utlx 1.0
input json
output json
---
input
"""

        val config = TransformConfig(
            strategy = "TEMPLATE",
            inputs = listOf(InputSlot(name = "input"))
        )

        strategy.initialize(source, config)

        val input = """{"name": "Alice", "age": 30}"""
        val result = strategy.execute(input)

        assertNotNull(result.output)
        assertTrue(result.output.isNotBlank())
        assertTrue(result.validationErrors.isEmpty())
        assertTrue(result.output.contains("Alice"))
        assertTrue(result.output.contains("30"))
    }

    @Test
    fun `execute transformation with field mapping`() {
        val strategy = TemplateStrategy()
        val source = """%utlx 1.0
input json
output json
---
{
    fullName: input.name,
    years: input.age
}
"""

        val config = TransformConfig(
            strategy = "TEMPLATE",
            inputs = listOf(InputSlot(name = "input"))
        )

        strategy.initialize(source, config)

        val input = """{"name": "Bob", "age": 25}"""
        val result = strategy.execute(input)

        assertTrue(result.output.contains("fullName"))
        assertTrue(result.output.contains("Bob"))
        assertTrue(result.output.contains("years"))
    }

    @Test
    fun `executeBatch processes multiple messages`() {
        val strategy = TemplateStrategy()
        val source = """%utlx 1.0
input json
output json
---
input
"""

        val config = TransformConfig(
            strategy = "TEMPLATE",
            inputs = listOf(InputSlot(name = "input"))
        )

        strategy.initialize(source, config)

        val inputs = listOf(
            """{"id": 1}""",
            """{"id": 2}""",
            """{"id": 3}"""
        )

        val results = strategy.executeBatch(inputs)
        assertEquals(3, results.size)
        results.forEach { result ->
            assertTrue(result.validationErrors.isEmpty())
            assertTrue(result.output.isNotBlank())
        }
    }

    @Test
    fun `execute with invalid input throws exception`() {
        val strategy = TemplateStrategy()
        val source = """%utlx 1.0
input json
output json
---
input
"""

        val config = TransformConfig(
            strategy = "TEMPLATE",
            inputs = listOf(InputSlot(name = "input"))
        )

        strategy.initialize(source, config)

        assertFailsWith<Exception> {
            strategy.execute("not valid json {{{")
        }
    }

    @Test
    fun `shutdown completes without error`() {
        val strategy = TemplateStrategy()
        val source = """%utlx 1.0
input json
output json
---
input
"""

        val config = TransformConfig(
            strategy = "TEMPLATE",
            inputs = listOf(InputSlot(name = "input"))
        )

        strategy.initialize(source, config)
        strategy.shutdown()  // should not throw
    }
}
