package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EB08 — **`@key:` must become an XML attribute on every strategy.**
 *
 * `Property.isAttribute` is set by the parser for `@key:`, and the interpreter uses it to put the
 * value into `UDM.Object.attributes` rather than into the properties. `ASTCompiler` ignored the flag
 * and handed every key to `RuntimeOps.buildObject`, so an attribute came out as a child element:
 *
 * ```
 * TEMPLATE   <Invoice number="INV-2025"><Status>ok</Status></Invoice>
 * COMPILED   <Invoice><number>INV-2025</number><Status>ok</Status></Invoice>
 * ```
 *
 * It is not only an XML concern, which is why the fix is the attribute map and not a naming
 * convention — every serialiser re-presents attributes in its own way, so the divergence showed up
 * in JSON and YAML output too:
 *
 * ```
 * json   { "@number": "INV-2025", "@count": "42" }
 * yaml   '@number': INV-2025
 * ```
 *
 * Attributes are strings by construction. The stringification mirrors the interpreter deliberately,
 * including that an integral number loses its decimal point — `@count: 42` is `"42"` even in JSON
 * output. Routing it through the exact-number rendering would have been an improvement that made
 * the two strategies disagree, which is the opposite of the point.
 *
 * These tests drive the strategies rather than `ASTCompiler` directly, because the defect was in
 * what reached the **serialiser**, and that is where the three paths have to agree.
 */
class EB08XmlAttributeTest {

    private val payload = """{"year": "2025", "n": 42, "f": 3.5, "flag": true}"""

    private fun run(strategy: String, outputFormat: String, body: String): String {
        val src = "%utlx 1.0\ninput json\noutput $outputFormat\n---\n$body"
        val s: ExecutionStrategy = when (strategy) {
            "TEMPLATE" -> TemplateStrategy()
            "COPY" -> CopyStrategy()
            "COMPILED" -> CompiledStrategy()
            else -> error("unknown strategy $strategy")
        }
        return try {
            s.initialize(src, TransformConfig(strategy = strategy))
            s.execute(payload).output
        } finally {
            runCatching { s.shutdown() }
        }
    }

    /** All three strategies must agree, and the result must actually carry an attribute. */
    private fun assertAllAgree(outputFormat: String, body: String): String {
        val t = run("TEMPLATE", outputFormat, body)
        assertEquals(t, run("COPY", outputFormat, body), "COPY differs [$outputFormat] $body")
        assertEquals(t, run("COMPILED", outputFormat, body), "COMPILED differs [$outputFormat] $body")
        return t
    }

    @Test
    fun `an attribute is emitted as an XML attribute, not a child element`() {
        val out = assertAllAgree("xml", "{ Invoice: { @number: \"INV-\" + \$input.year, Status: \"ok\" } }")
        assertTrue(out.contains("number=\"INV-2025\""), "attribute missing from XML: $out")
        assertTrue(!out.contains("<number>"), "attribute leaked as a child element: $out")
    }

    @Test
    fun `several attributes alongside several children`() {
        val out = assertAllAgree("xml",
            "{ Invoice: { @a: \"1\", @b: \"2\", X: \"x\", Y: \"y\" } }")
        assertTrue(out.contains("a=\"1\"") && out.contains("b=\"2\""), out)
        assertTrue(out.contains("<X>x</X>") && out.contains("<Y>y</Y>"), out)
    }

    @Test
    fun `an object with only attributes and no children`() {
        val out = assertAllAgree("xml", "{ Invoice: { @only: \"yes\" } }")
        assertTrue(out.contains("only=\"yes\""), out)
    }

    @Test
    fun `an object with no attributes is unchanged`() {
        // The common case must be byte-identical to before the fix.
        val out = assertAllAgree("xml", "{ Invoice: { Status: \"ok\" } }")
        assertTrue(out.contains("<Status>ok</Status>"), out)
    }

    @Test
    fun `attribute values are stringified the way the interpreter does`() {
        // An integral number loses its decimal point; a float keeps it; a boolean becomes its text.
        val out = assertAllAgree("xml",
            "{ R: { @count: \$input.n, @ratio: \$input.f, @flag: \$input.flag } }")
        assertTrue(out.contains("count=\"42\""), "integral number should lose its point: $out")
        assertTrue(out.contains("ratio=\"3.5\""), out)
        assertTrue(out.contains("flag=\"true\""), out)
    }

    @Test
    fun `attributes survive to JSON output as at-prefixed string properties`() {
        val out = assertAllAgree("json", "{ R: { @number: \$input.year, Status: \"ok\" } }")
        assertTrue(out.contains("\"@number\""), "JSON lost the attribute: $out")
        assertTrue(out.contains("\"@number\": \"2025\""), out)
    }

    @Test
    fun `attributes survive to YAML output`() {
        val out = assertAllAgree("yaml", "{ R: { @number: \$input.year, Status: \"ok\" } }")
        assertTrue(out.contains("@number"), "YAML lost the attribute: $out")
    }

    @Test
    fun `an attribute nested deeper than the root`() {
        val out = assertAllAgree("xml",
            "{ Root: { Child: { @id: \"7\", Leaf: \"v\" } } }")
        assertTrue(out.contains("id=\"7\""), out)
        assertTrue(!out.contains("<id>"), out)
    }
}
