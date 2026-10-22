package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.engine.config.InputSlot
import com.glomidco.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * F21 cross-strategy invariant: `validate.*` and the `%utlx` version gate must behave identically on
 * EVERY execution strategy, not just the interpreted/COPY path.
 *
 * The gate + validation live in the :core Interpreter. The COMPILED path (ASTCompiler → JVM bytecode,
 * StdlibDispatch — which has NO gate) and TEMPLATE both bypass the core interpreter for *compilable*
 * expressions. Today they stay safe because a namespaced `validate.*` call is NOT compilable, so they
 * fall back to the interpreter. This test LOCKS that invariant: if a future ASTCompiler change made such
 * calls compilable (bypassing the gate), a %utlx 1.0 script could silently skip validation — a fail-OPEN
 * hole for a guard. This test would catch that regression.
 */
class ValidateAcrossStrategiesTest {

    private val script11 = """%utlx 1.1
input json
output json
---
${'$'}input |> validate.required(["orderId", "lineItems"])
"""

    private val script10 = """%utlx 1.0
input json
output json
---
${'$'}input |> validate.required(["orderId", "lineItems"])
"""

    private val input = """{"orderId": 42}"""

    private fun strategies(): List<ExecutionStrategy> =
        listOf(CopyStrategy(), CompiledStrategy(), TemplateStrategy())

    private fun run(strategy: ExecutionStrategy, source: String): String {
        val config = TransformConfig(strategy = strategy.name, inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)
        return strategy.execute(input).output
    }

    @Test
    fun `validate runs under utlx 1_1 on every strategy`() {
        for (s in strategies()) {
            val out = run(s, script11)
            assertTrue(
                out.contains("\"valid\"") && out.contains("\"issues\""),
                "${s.name}: expected a ValidationResult, got: $out"
            )
        }
    }

    @Test
    fun `version gate fires under utlx 1_0 on every strategy`() {
        for (s in strategies()) {
            try {
                val out = run(s, script10)
                fail("${s.name}: validate.* under %utlx 1.0 must be gated, but it ran and produced: $out")
            } catch (e: Exception) {
                assertTrue(
                    (e.message ?: "").contains("requires %utlx 1.1"),
                    "${s.name}: expected the version-gate error, got: ${e.message}"
                )
            }
        }
    }
}
