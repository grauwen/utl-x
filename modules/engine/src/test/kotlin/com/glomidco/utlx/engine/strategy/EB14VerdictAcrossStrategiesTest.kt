package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.engine.config.InputSlot
import com.glomidco.utlx.engine.config.TransformConfig
import com.glomidco.utlx.engine.routing.ValidationRoutingPolicy
import com.glomidco.utlx.engine.routing.VerdictCheck
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * EB14 — the validation **verdict** was computed on some execution paths and dropped on others.
 *
 * `VerdictCheck.of(outputUDM)` ran only where the output still existed as a UDM, which was the
 * *compiled* branch of `CompiledStrategy` and both branches of `CopyStrategy`. `TemplateStrategy`
 * and the two **interpreter-fallback** branches of `CompiledStrategy` went through
 * `TransformationService.transform()`, which returns serialised text, so they returned the default
 * `NotInspected`.
 *
 * Why that was not an edge case: as [ValidateAcrossStrategiesTest] records, a namespaced
 * `validate.*` call is **not compilable**, so every validation mapping takes the fallback. The
 * verdict was therefore missing on exactly the mappings it exists for.
 *
 * The fix adds `TransformationService.transformWithUdm`, which hands back the output UDM alongside
 * the text. Re-parsing the serialised output would not have done: a round trip is not guaranteed
 * faithful (B38, B39, B43) and it would make the verdict depend on the output format.
 *
 * These tests assert the three-state distinction `VerdictCheck` exists to express:
 *  - `Decided`      — we looked, and there was a verdict
 *  - `NoVerdict`    — we looked, and the output was an ordinary mapping result
 *  - `NotInspected` — nobody looked. **No strategy may return this.**
 */
class EB14VerdictAcrossStrategiesTest {

    private val failing = """%utlx 1.1
input json
output json
---
${'$'}input |> validate.required(["orderId", "lineItems"])
"""

    private val passing = """%utlx 1.1
input json
output json
---
${'$'}input |> validate.required(["orderId"])
"""

    private val plainMapping = """%utlx 1.0
input json
output json
---
{ id: ${'$'}input.orderId }
"""

    private val input = """{"orderId": 42}"""

    private fun strategies(): List<ExecutionStrategy> =
        listOf(CopyStrategy(), CompiledStrategy(), TemplateStrategy())

    private fun run(strategy: ExecutionStrategy, source: String): ExecutionResult {
        val config = TransformConfig(strategy = strategy.name, inputs = listOf(InputSlot(name = "input")))
        strategy.initialize(source, config)
        return strategy.execute(input)
    }

    @Test
    fun `every strategy inspects the output - none returns NotInspected`() {
        for (s in strategies()) {
            for ((label, src) in listOf("failing" to failing, "passing" to passing, "plain" to plainMapping)) {
                val v = run(s, src).verdict
                assertEquals(
                    false, v == VerdictCheck.NotInspected,
                    "${s.name}/$label returned NotInspected — nothing looked at the output"
                )
            }
        }
    }

    @Test
    fun `a failing validation yields a REJECT verdict on every strategy`() {
        for (s in strategies()) {
            val v = run(s, failing).verdict
            val decided = assertIs<VerdictCheck.Decided>(v, "${s.name}: expected a verdict, got $v")
            assertEquals(
                ValidationRoutingPolicy.Outcome.REJECT, decided.decision.outcome,
                "${s.name}: a missing required field must reject under the fail-closed default"
            )
            assertEquals(1, decided.decision.failed, "${s.name}: one failed check expected")
        }
    }

    @Test
    fun `a passing validation yields a FORWARD verdict on every strategy`() {
        for (s in strategies()) {
            val v = run(s, passing).verdict
            val decided = assertIs<VerdictCheck.Decided>(v, "${s.name}: expected a verdict, got $v")
            assertEquals(
                ValidationRoutingPolicy.Outcome.FORWARD, decided.decision.outcome,
                "${s.name}: all checks passed, so it must forward"
            )
            assertEquals(0, decided.decision.failed)
        }
    }

    @Test
    fun `a plain mapping yields NoVerdict - looked at, nothing to gate`() {
        for (s in strategies()) {
            val v = run(s, plainMapping).verdict
            assertEquals(
                VerdictCheck.NoVerdict, v,
                "${s.name}: a non-validation mapping should be inspected and found to carry no verdict, got $v"
            )
        }
    }

    @Test
    fun `the strategies agree with each other`() {
        for ((label, src) in listOf("failing" to failing, "passing" to passing, "plain" to plainMapping)) {
            val verdicts = strategies().associate { it.name to run(it, src).verdict }
            val distinct = verdicts.values.distinct()
            assertEquals(1, distinct.size, "$label: strategies disagreed — $verdicts")
        }
    }
}
