package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B45 — the stdlib did not follow the decimal rule the arithmetic operators already implement.
 *
 * Measured against Java `BigDecimal`, UTL-X's operators are standards-correct, and the rule differs
 * by operation — which is why the two cases below look inconsistent but are not:
 *
 * | | UTL-X | BigDecimal | rule |
 * |---|---|---|---|
 * | `1.50 + 1.50` | `3.00` | `3.00` | scale = **max**(s1, s2) |
 * | `1.50 * 3` | `4.50` | `4.50` | scale = **sum** s1 + s2 |
 *
 * ANSI SQL `DECIMAL`, IEEE 754-2008 decimal and XPath `xs:decimal` specify the same two rules. Two
 * places did not follow them:
 *
 * 1. **`runtimeValueToUDM`** — the direction that hands a stdlib function its arguments and takes
 *    its lambda results — dropped `exact`, where `toUDM()` carries it. So `mapValues($p, v => v * 2)`
 *    returned `3` while `$p.apple * 2` returned `3.0`: the same arithmetic, exact as an operator and
 *    not inside a stdlib lambda. The third such conversion after
 *    [B44](../../../../../../../../docs/bugs-fixed)'s `udmToRuntimeValue`.
 * 2. **`Aggregations`** — `sum` folded with `sumOf`, whose selector is a `Double`, and `min`/`max`
 *    recomputed a `Double` instead of returning the element they had chosen.
 *
 * `sum` also **corrupted integers above 2^53** on the way, which is the B37 defect in a different
 * place: `sum([9007199254740993, 1])` was `9007199254740992`.
 *
 * `avg` is deliberately untouched: it divides, and division has no exact answer in general, so it
 * waits on the DECIMAL128 / HALF_EVEN decision.
 */
class B45StdlibArithmeticExactnessTest {

    private val input = """{
        "prices": {"apple": 1.5, "banana": 0.75},
        "xs": [1.50, 1.50], "ys": [1.50, 2.50], "mixed": [1.50, 2.5],
        "ints": [1, 2, 3], "big": [9007199254740993, 1]
    }"""

    private fun strategies() = listOf(TemplateStrategy(), CopyStrategy(), CompiledStrategy())

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

    private fun onAll(body: String, expect: String) {
        for (s in strategies()) assertEquals(expect, run(s, body), "${s.name} disagreed on `$body`")
    }

    // ── 1. the stdlib lambda boundary ──

    @Test
    fun `a lambda inside mapValues gets the exact value, like the operator does`() {
        // 1.5 * 2 -> scale 1 + 0 = 1 -> 3.0, matching `$input.prices.apple * 2`.
        onAll("""mapValues(${'$'}input.prices, v => v * 2)""", """{"apple":3.0,"banana":1.50}""")
    }

    @Test
    fun `the operator and the stdlib lambda now agree`() {
        for (s in strategies()) {
            val viaOperator = run(s, """{ r: ${'$'}input.prices.apple * 2 }""")
            val viaLambda = run(s, """{ r: mapValues(${'$'}input.prices, v => v * 2).apple }""")
            assertEquals(viaOperator, viaLambda, "${s.name}: operator and stdlib lambda disagree")
        }
    }

    // ── 2. aggregations ──

    @Test
    fun `sum follows the addition rule - scale is the max of the operands`() {
        onAll("""{ r: sum(${'$'}input.xs) }""", """{"r":3.00}""")
    }

    @Test
    fun `sum keeps integers exact above two to the fifty-third`() {
        // Was 9007199254740992 — the B37 defect, in sum.
        onAll("""{ r: sum(${'$'}input.big) }""", """{"r":9007199254740994}""")
    }

    @Test
    fun `min and max return the element they chose, not a recomputed double`() {
        onAll("""{ r: min(${'$'}input.ys) }""", """{"r":1.50}""")
        onAll("""{ r: max(${'$'}input.ys) }""", """{"r":2.50}""")
        onAll("""{ r: min(1.50, 2.50) }""", """{"r":1.50}""")
    }

    // ── What must not change ──

    @Test
    fun `integer aggregation is unchanged`() {
        onAll("""{ r: sum(${'$'}input.ints) }""", """{"r":6}""")
        onAll("""{ r: count(${'$'}input.xs) }""", """{"r":2}""")
    }

    @Test
    fun `a mixed array still falls back to double`() {
        // One Double operand means no exact answer is available; the old behaviour stands.
        onAll("""{ r: max(${'$'}input.mixed) }""", """{"r":2.5}""")
    }

    @Test
    fun `avg is deliberately unchanged pending the division decision`() {
        onAll("""{ r: avg(${'$'}input.xs) }""", """{"r":1.5}""")
    }

    @Test
    fun `the empty cases are unchanged`() {
        onAll("""{ r: sum([]) }""", """{"r":0}""")
        onAll("""{ r: min([]) }""", """{"r":null}""")
    }

    @Test
    fun `validation still rejects what it always rejected`() {
        // The first attempt at this fix named its helper `asNumber`, which silently resolved to
        // UDM's MEMBER `asNumber(): Double` — a function that returns 0.0 for a null, a boolean or
        // any non-numeric value. Every one of these stopped raising and returned a wrong answer
        // instead. These tests exist because that was nearly shipped.
        for ((body, fragment) in listOf(
            """{ r: sum([1, null]) }""" to "null value",
            """{ r: min(["a"]) }""" to "Cannot convert",
            """{ r: max([true]) }""" to "boolean value",
        )) {
            for (s in strategies()) {
                val e = runCatching { run(s, body) }.exceptionOrNull()
                    ?: error("${s.name}: `$body` should have raised")
                assertTrue(
                    (e.message ?: "").contains(fragment),
                    "${s.name}: `$body` raised the wrong error: ${e.message}"
                )
            }
        }
    }

    @Test
    fun `numeric strings still aggregate as before`() {
        onAll("""{ r: sum(["1.5", "2.5"]) }""", """{"r":4}""")
    }
}
