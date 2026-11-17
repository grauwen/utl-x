package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * EB20 — a namespaced call could not be compiled, so `validate.*` never ran compiled.
 *
 * `validate.assert(...)` is a `FunctionCall` whose `function` is a **MemberAccess**, not an
 * `Identifier`. `ASTCompiler.isCompilable` accepted only `Identifier`, so every `%utlx 1.1`
 * script fell back to the interpreter on COMPILED — the strategy UTLXe runs by default.
 * Correct answers, quietly slower, and invisible: the fallback logs at INFO.
 *
 * Found by reading the engine log of the first functional test run against Azure, where three
 * transformations reported "compiled to JVM bytecode" and the `validate.*` one reported
 * "Transformation uses unsupported AST nodes — falling back to interpreter".
 *
 * ### The trap underneath it
 *
 * The `%utlx` NAMESPACE gate (`validate.*` requires 1.1) lived only in the interpreter. It was
 * enforced in practice only because no `validate.*` call could be compiled, so every script
 * using one fell back to the interpreter that enforced it. The gate's enforcement was a side
 * effect of this very limitation — lifting the limitation alone would have reopened
 * [EB15](../../../../../../../../docs/bugs-fixed/EB15-version-gate-not-enforced-on-the-compiled-strategies.md)
 * on the default strategy. The gate therefore moved into `requireSupportedLanguageVersion`,
 * which all three strategies already call, and the last test here is what holds that.
 */
class EB20NamespacedCallTest {

    private fun strategies() = listOf(TemplateStrategy(), CopyStrategy(), CompiledStrategy())

    private val script11 = """%utlx 1.1
input json
output json
---
${'$'}input |> validate.assert([
  { rule: "amount-range", inRange: { field: "amount", min: 1, max: 1000 } }
])
"""

    private fun run(s: ExecutionStrategy, src: String, input: String): String {
        s.initialize(src, TransformConfig(strategy = s.name))
        return s.execute(input).output.replace(Regex("\\s+"), "")
    }

    @Test
    fun `every strategy agrees on a valid verdict`() {
        for (s in strategies()) {
            val out = run(s, script11, """{"amount":500}""")
            assertTrue(out.contains("\"valid\":true"), "${s.name}: $out")
        }
    }

    @Test
    fun `every strategy agrees on an invalid verdict`() {
        for (s in strategies()) {
            val out = run(s, script11, """{"amount":99999}""")
            assertTrue(out.contains("\"valid\":false"), "${s.name}: $out")
            assertTrue(out.contains("amount-range"), "${s.name}: rule not named -> $out")
        }
    }

    @Test
    fun `the compiled strategy produces the same bytes as the interpreter`() {
        // COMPILED reaches this verdict by FALLING BACK to the interpreter — compiling
        // `validate.*` was tried and reverted because six language/validate parity fixtures
        // then disagreed with TEMPLATE. This asserts the agreement that the fallback buys;
        // if anyone enables namespaced compilation without fixing lambda semantics first,
        // ConformanceStrategyParityTest is what fails.
        val payloads = listOf("""{"amount":500}""", """{"amount":99999}""", """{"amount":1}""")
        for (p in payloads) {
            assertEquals(
                run(TemplateStrategy(), script11, p),
                run(CompiledStrategy(), script11, p),
                "strategies disagreed on $p"
            )
        }
    }

    @Test
    fun `the namespace version gate holds on every strategy`() {
        // EB15's failure mode, re-armed. A %utlx 1.0 script may not use validate.*, and that
        // must be true regardless of which strategy runs it — previously only the interpreter
        // enforced it, and only the compiler's inability to compile the call kept COMPILED
        // honest.
        val script10 = script11.replaceFirst("%utlx 1.1", "%utlx 1.0")
        for (s in strategies()) {
            try {
                run(s, script10, """{"amount":500}""")
                fail("${s.name}: a %utlx 1.0 script was allowed to call validate.*")
            } catch (e: Exception) {
                val msg = (e.message ?: "") + (e.cause?.message ?: "")
                assertTrue(
                    msg.contains("1.1") || msg.contains("validate"),
                    "${s.name}: rejected, but not for the version gate -> $msg"
                )
            }
        }
    }
}
