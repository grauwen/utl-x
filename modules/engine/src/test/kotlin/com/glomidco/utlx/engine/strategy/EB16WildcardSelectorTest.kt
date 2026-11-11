package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EB16 — the wildcard selector `.*` silently evaluated to `null` on the compiled strategies.
 *
 * `$obj.*` parses as a `MemberAccess` whose `property` is the **literal string `"*"`**, and the
 * interpreter special-cases it: collect the child values, drop `_text`, with a separate variant for
 * `.@*` attributes. `ASTCompiler.isCompilable` accepted any `MemberAccess` regardless of the
 * property, so the compiled path emitted an ordinary property lookup for a key named `"*"` — which
 * no object has:
 *
 * ```
 * { r: ${'$'}input.servers.* }                     TEMPLATE {"r":[{...},{...}]}   COPY/COMPILED {"r":null}
 * { r: ${'$'}input.servers.* |> map(s => s.host) } TEMPLATE {"r":["h1","h2"]}     COPY/COMPILED threw
 * ```
 *
 * The first line is the dangerous one: a **silent wrong answer**, not an error. It only surfaced as
 * an exception when something downstream insisted on an array.
 *
 * Fixed by refusing to compile it, so the transformation falls back to the interpreter — the same
 * choice EB12 made for try/catch. Implementing the wildcard in the compiler is the follow-up; a
 * slower right answer beats a fast wrong one.
 *
 * Found by the cross-strategy harness after it learned to drive the 39 named-input fixtures, which
 * is where `datacontract/02_servers_wildcard` lives.
 */
class EB16WildcardSelectorTest {

    private val input = """{"servers":{"a":{"host":"h1","port":1},"b":{"host":"h2","port":2}}}"""

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

    @Test
    fun `the wildcard yields the child values on every strategy`() {
        for (s in strategies()) {
            val out = run(s, """{ r: ${'$'}input.servers.* }""")
            assertTrue(out.contains("h1") && out.contains("h2"), "${s.name}: $out")
            assertTrue(!out.contains("\"r\":null"), "${s.name}: wildcard produced null -> $out")
        }
    }

    @Test
    fun `the wildcard feeds a pipeline on every strategy`() {
        for (s in strategies()) {
            assertEquals(
                """{"r":["h1","h2"]}""", run(s, """{ r: ${'$'}input.servers.* |> map(s => s.host) }"""),
                "${s.name} disagreed"
            )
        }
    }

    @Test
    fun `the strategies agree on wildcard expressions`() {
        for (body in listOf(
            """{ r: ${'$'}input.servers.* }""",
            """{ r: ${'$'}input.servers.* |> map(s => s.host) }""",
            """{ r: ${'$'}input.servers.* |> filter(s => s.port == 1) }""",
            """{ r: count(${'$'}input.servers.*) }""",
        )) {
            val outs = strategies().associate { it.name to run(it, body) }
            assertEquals(1, outs.values.distinct().size, "disagreed on `$body`: $outs")
        }
    }

    @Test
    fun `an ordinary property access is still compiled`() {
        // The refusal must be narrow: only the literal "*" property, not every MemberAccess.
        for (s in strategies()) {
            assertEquals("""{"r":"h1"}""", run(s, """{ r: ${'$'}input.servers.a.host }"""), s.name)
        }
    }
}
