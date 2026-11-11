package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B44 — the interpreter dropped the exact numeric form when a **stdlib** function returned it.
 *
 * B35 made numbers exact end to end, but in one direction only. `RuntimeValue.toUDM()` emits the
 * exact form and `literalToRuntimeValue` carries it in; `udmToRuntimeValue` — the path that brings a
 * stdlib function's `UDM` result *back* into the interpreter — narrowed every `Number` through
 * `toDouble()`:
 *
 * ```kotlin
 * is Number -> RuntimeValue.NumberValue(value.toDouble())   // the whole bug
 * ```
 *
 * So a value that merely passed through was exact, and the same value returned by `groupBy` or
 * `nestBy` was not:
 *
 * ```
 * ${'$'}input.orderLines                  ->  15.500     (never left the interpreter)
 * groupBy(${'$'}input.orderLines, ...)     ->  15.5       (round-tripped through UDM)
 * ```
 *
 * Found through the cross-strategy conformance harness, as the `nestBy_basic` divergence: the COPY
 * and COMPILED strategies kept `15.500` because they never make that round trip, so **TEMPLATE was
 * the strategy that was wrong** — the opposite of the usual direction, which is why it is worth a
 * test of its own rather than only a parity entry.
 */
class B44StdlibExactnessTest {

    private val input = """{"orders":[{"orderId":"O1","customer":"Acme"}],""" +
        """"orderLines":[{"orderId":"O1","product":"Gizmo","price":15.500,"qty":3}]}"""

    private fun strategies(): List<ExecutionStrategy> =
        listOf(TemplateStrategy(), CopyStrategy(), CompiledStrategy())

    private fun run(s: ExecutionStrategy, body: String): String {
        val src = """%utlx 1.0
input json
output json
---
$body
"""
        s.initialize(src, TransformConfig(strategy = s.name))
        return s.execute(input).output.replace(Regex("\\s+"), "")
    }

    // ── The scale must survive a stdlib round trip ──

    @Test
    fun `groupBy keeps the scale on every strategy`() {
        for (s in strategies()) {
            val out = run(s, """groupBy(${'$'}input.orderLines, (l) -> l.orderId)""")
            assertTrue(out.contains("15.500"), "${s.name}: scale lost -> $out")
        }
    }

    @Test
    fun `nestBy keeps the scale on every strategy`() {
        for (s in strategies()) {
            val out = run(
                s,
                """nestBy(${'$'}input.orders, ${'$'}input.orderLines, (o) -> o.orderId, (l) -> l.orderId, "lines")"""
            )
            assertTrue(out.contains("15.500"), "${s.name}: scale lost -> $out")
        }
    }

    @Test
    fun `the strategies agree - this is what the parity harness caught`() {
        for (body in listOf(
            """groupBy(${'$'}input.orderLines, (l) -> l.orderId)""",
            """nestBy(${'$'}input.orders, ${'$'}input.orderLines, (o) -> o.orderId, (l) -> l.orderId, "lines")""",
        )) {
            val outs = strategies().associate { it.name to run(it, body) }
            assertEquals(1, outs.values.distinct().size, "strategies disagreed: $outs")
        }
    }

    // ── What must not change ──

    @Test
    fun `a value that never enters a stdlib function was always exact and still is`() {
        for (s in strategies()) {
            assertTrue(run(s, """${'$'}input.orderLines""").contains("15.500"))
        }
    }

    @Test
    fun `integers are unaffected`() {
        for (s in strategies()) {
            val out = run(s, """groupBy(${'$'}input.orderLines, (l) -> l.orderId)""")
            assertTrue(out.contains("\"qty\":3"), "${s.name}: integer changed shape -> $out")
        }
    }

    @Test
    fun `arithmetic still produces a computed value, not a preserved lexeme`() {
        for (s in strategies()) {
            // 15.500 * 2 is a computation; exactness of the *input* must not freeze the result.
            val out = run(s, """{ total: ${'$'}input.orderLines[0].price * 2 }""")
            assertTrue(
                out.contains("31.000") || out.contains("31.0") || out.contains("31"),
                "${s.name}: unexpected product -> $out"
            )
        }
    }
}
