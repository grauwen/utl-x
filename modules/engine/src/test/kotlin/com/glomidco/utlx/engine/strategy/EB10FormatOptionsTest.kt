package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EB10 — **a declared format's options must reach the parser on every strategy.**
 *
 * `FormatSpec` carries `type`, `dialect` and **`options`**, and `parseInputPublic` accepts an
 * options map. Both compiled strategies read only the type name:
 *
 * ```kotlin
 * val declaredFormat = declaredInputs.firstOrNull()?.second?.type?.name?.lowercase()
 * parseInputPublic(input, declaredFormat ?: "json", parseProfile = parseProfile)   // options default to emptyMap()
 * ```
 *
 * So `input csv {headers: true, delimiter: "|"}` was parsed as **comma-separated with no header
 * row**. Every field lookup then returned null, and the result looked like a transformation defect
 * rather than a parsing one — which is exactly how it was first mis-read: six fixtures were filed
 * against `mapGroups`, lambda bindings and grouping, and nine minimal reproductions of those
 * constructs all agreed. The constructs were never the problem; the delimiter was.
 *
 * TemplateStrategy was unaffected because it hands the source and content to `transform()`, which
 * re-reads the header itself. Only the two strategies that parse input themselves had to be told.
 *
 * Five call sites needed it — single and multi input on COMPILED, and single, multi and *schema*
 * parsing on COPY.
 */
class EB10FormatOptionsTest {

    private fun run(strategy: String, source: String, payload: String): String {
        val s: ExecutionStrategy = when (strategy) {
            "TEMPLATE" -> TemplateStrategy()
            "COPY" -> CopyStrategy()
            "COMPILED" -> CompiledStrategy()
            else -> error("unknown strategy $strategy")
        }
        return try {
            s.initialize(source, TransformConfig(strategy = strategy))
            s.execute(payload).output
        } finally {
            runCatching { s.shutdown() }
        }
    }

    /** All three agree, and the value is present — three nulls would agree and prove nothing. */
    private fun assertAllAgree(source: String, payload: String): String {
        val t = run("TEMPLATE", source, payload)
        assertEquals(t, run("COPY", source, payload), "COPY differs")
        assertEquals(t, run("COMPILED", source, payload), "COMPILED differs")
        return t
    }

    private fun csvSource(options: String) =
        "%utlx 1.0\ninput csv {$options}\noutput json\n---\n" +
            "{ first: first(\$input).name, n: count(\$input) }"

    @Test
    fun `a non-default delimiter is honoured on every strategy`() {
        val out = assertAllAgree(csvSource("headers: true, delimiter: \"|\""),
            "name|city\nAlice|Amsterdam\nBob|Berlin\n")
        assertTrue(out.contains("Alice"), "the delimiter option was ignored: $out")
        assertTrue(out.contains("\"n\": 2"), out)
    }

    @Test
    fun `a semicolon delimiter is honoured`() {
        val out = assertAllAgree(csvSource("headers: true, delimiter: \";\""),
            "name;city\nAlice;Amsterdam\n")
        assertTrue(out.contains("Alice"), out)
    }

    @Test
    fun `a tab delimiter is honoured`() {
        val out = assertAllAgree(csvSource("headers: true, delimiter: \"\\t\""),
            "name\tcity\nAlice\tAmsterdam\n")
        assertTrue(out.contains("Alice"), out)
    }

    @Test
    fun `the default comma delimiter still works`() {
        // The common case must be unchanged by threading options through.
        val out = assertAllAgree(csvSource("headers: true"), "name,city\nAlice,Amsterdam\n")
        assertTrue(out.contains("Alice"), out)
    }

    @Test
    fun `headers false is honoured, so rows are positional`() {
        val src = "%utlx 1.0\ninput csv {headers: false}\noutput json\n---\n{ n: count(\$input) }"
        val out = assertAllAgree(src, "Alice,Amsterdam\nBob,Berlin\n")
        // With headers:false both data lines are rows; with headers:true one would be consumed.
        assertTrue(out.contains("\"n\": 2"), "headers:false was ignored: $out")
    }

    @Test
    fun `a format with no options is unaffected`() {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n\$input"
        assertAllAgree(src, """{"a": 1}""")
    }
}
