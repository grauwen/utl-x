package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B47 — XML attributes survive `renderJson` and the object-rebuilding stdlib functions.
 *
 * Reported as GitHub issue #10: `renderJson` dropped attributes silently while `.@name` read them
 * and `output json` wrote them, so the data was plainly in the UDM and only that one function left
 * it out. The reporter also suspected `mapTree` and `mapKeys` "not verified separately" — they were
 * right.
 *
 * Underneath were four layers of the same habit, rebuilding a value from `properties` alone:
 *
 * 1. `renderJson` had its own `udmToJsonValue` that never read `attributes` — and, being a second
 *    serialiser, also narrowed numbers through `toDouble()` and leaked the internal `_text` marker.
 * 2. `mapKeys` / `mapValues` / `mapTree` returned `UDM.Object(result)`, discarding shape.
 * 3. `Interpreter.udmToRuntimeValue` decomposed into `RuntimeValue.ObjectValue`, **which has no
 *    attribute slot at all** — so fixing (2) alone changed nothing, because the loss happened after
 *    those functions returned.
 *
 * Run on every strategy, because (3) is in the interpreter and the compiled paths reach it only on
 * fallback — the EB14/EB15 shape of defect.
 */
class B47XmlAttributesThroughStdlibTest {

    private val xml = """<Order id="ORD-001"><Customer>Alice</Customer><Sales type="direct"/></Order>"""

    private fun strategies() = listOf(TemplateStrategy(), CopyStrategy(), CompiledStrategy())

    private fun run(s: ExecutionStrategy, body: String): String {
        val src = """%utlx 1.0
input xml
output json
---
$body
"""
        s.initialize(src, TransformConfig(strategy = s.name))
        return s.execute(xml).output.replace(Regex("\\s+"), "")
    }

    private fun onAll(body: String, expect: String) {
        for (s in strategies()) assertEquals(expect, run(s, body), "${s.name} disagreed on `$body`")
    }

    // ── renderJson ──

    @Test
    fun `renderJson keeps attributes on every strategy`() {
        onAll("""renderJson(${'$'}input)""",
            """"{\"Order\":{\"@id\":\"ORD-001\",\"Customer\":\"Alice\",\"Sales\":{\"@type\":\"direct\"}}}"""")
    }

    @Test
    fun `renderJson agrees with output json`() {
        // The invariant that makes a second serialiser impossible to drift: renderJson(x) is
        // exactly what `output json` writes for x.
        for (s in strategies()) {
            val rendered = run(s, """renderJson(${'$'}input)""")
            val direct = run(s, """${'$'}input""")
            // `rendered` is a JSON string literal of `direct`; compare their contents.
            val unquoted = rendered.trim('"').replace("\\\"", "\"")
            assertEquals(direct, unquoted, "${s.name}: renderJson and `output json` disagree")
        }
    }

    @Test
    fun `renderJson no longer leaks the internal _text marker`() {
        for (s in strategies()) {
            assertTrue(!run(s, """renderJson(${'$'}input)""").contains("_text"), s.name)
        }
    }

    @Test
    fun `the writeAttributes argument keeps a leaf attribute`() {
        // The option GitHub issue #10 asked for, named to match `output json { writeAttributes }`.
        for (s in strategies()) {
            val out = run(s, """renderJson(${'$'}input, false, true)""")
            assertTrue(out.contains("""\"@id\":\"ORD-001\""""), "${s.name}: $out")
        }
    }

    // ── the object-rebuilding functions ──

    @Test
    fun `mapKeys keeps the node's own attributes and its children's`() {
        onAll("""mapKeys(${'$'}input.Order, (k) -> upper(k))""",
            """{"@id":"ORD-001","CUSTOMER":"Alice","SALES":{"@type":"direct"}}""")
    }

    @Test
    fun `mapValues and mapTree keep attributes`() {
        onAll("""mapValues(${'$'}input.Order, (v) -> v)""",
            """{"@id":"ORD-001","Customer":"Alice","Sales":{"@type":"direct"}}""")
        onAll("""mapTree(${'$'}input.Order, (v) -> v)""",
            """{"@id":"ORD-001","Customer":"Alice","Sales":{"@type":"direct"}}""")
    }

    @Test
    fun `the reporter's whole use case - clean the keys, then render`() {
        for (s in strategies()) {
            val out = run(s, """renderJson(mapKeys(${'$'}input.Order, (k) -> upper(k)))""")
            assertTrue(out.contains("""\"@id\""""), "${s.name}: attribute lost -> $out")
            assertTrue(out.contains("""\"CUSTOMER\""""), "${s.name}: key not renamed -> $out")
        }
    }

    // ── the numeric half, and what must not change ──

    @Test
    fun `renderJson is numerically exact`() {
        val src = """%utlx 1.0
input json
output json
---
renderJson(${'$'}input)
"""
        for (s in strategies()) {
            s.initialize(src, TransformConfig(strategy = s.name))
            val out = s.execute("""{"big":9007199254740993,"money":1.50}""").output
            assertTrue(out.contains("9007199254740993"), "${s.name}: large integer corrupted -> $out")
            assertTrue(out.contains("1.50"), "${s.name}: scale lost -> $out")
        }
    }

    @Test
    fun `a plain JSON object is unaffected by the UDMValue change`() {
        // udmToRuntimeValue only preserves objects that carry XML shape; a JSON object with no
        // attributes and no element name still becomes an ObjectValue, so nothing on this path moved.
        val src = """%utlx 1.0
input json
output json
---
mapKeys(${'$'}input, (k) -> upper(k))
"""
        for (s in strategies()) {
            s.initialize(src, TransformConfig(strategy = s.name))
            assertEquals("""{"A":1,"B":2}""",
                s.execute("""{"a":1,"b":2}""").output.replace(Regex("\\s+"), ""), s.name)
        }
    }
}
