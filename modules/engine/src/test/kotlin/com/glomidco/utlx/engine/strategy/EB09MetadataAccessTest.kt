package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EB09 — **`^metadata` must read the same on every strategy.**
 *
 * `UDM.Object` carries three things: properties, attributes and **metadata**. Metadata is how the
 * schema formats expose what they are — `^schemaType`, `^xsdVersion`, `^targetNamespace` — and how
 * XML carries its namespace context. Two separate places lost it:
 *
 *  - **`ASTCompiler.compileMemberAccess`** had a branch for `isAttribute` and none for `isMetadata`,
 *    so `$input.^schemaType` compiled as an ordinary property lookup and evaluated to null.
 *  - **`CopyStrategy`** rebuilds `UDM.Object` in three places — `deepClone`, `buildEmptySkeleton`
 *    and `mergeDataIntoSkeleton` — and every one of them passed `properties`, `attributes` and
 *    `name` while omitting `metadata`, so COPY lost it even once the compiler could read it.
 *
 * Both failed **silently**, returning null rather than erroring, which is why this surfaced as a
 * value difference across 15 fixtures rather than as an exception anywhere.
 *
 * The two halves are independent: the first was found by reading the compiler, the second only
 * showed up because fixing the first left COPY as the sole remaining difference on the same
 * fixtures. Tests here cover both, and the revert check in the report confirms each is
 * load-bearing on its own.
 */
class EB09MetadataAccessTest {

    /** A minimal XSD — the schema formats are where metadata actually comes from. */
    private val xsd = """
        <?xml version="1.0" encoding="UTF-8"?>
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                   targetNamespace="http://example.com/test"
                   elementFormDefault="qualified">
          <xs:element name="root" type="xs:string"/>
        </xs:schema>
    """.trimIndent()

    private fun run(strategy: String, body: String): String {
        val src = "%utlx 1.0\ninput xsd\noutput json\n---\n$body"
        val s: ExecutionStrategy = when (strategy) {
            "TEMPLATE" -> TemplateStrategy()
            "COPY" -> CopyStrategy()
            "COMPILED" -> CompiledStrategy()
            else -> error("unknown strategy $strategy")
        }
        return try {
            s.initialize(src, TransformConfig(strategy = strategy))
            s.execute(xsd).output
        } finally {
            runCatching { s.shutdown() }
        }
    }

    /** All three agree, and the value is actually present rather than all three being null. */
    private fun assertAllAgree(body: String): String {
        val t = run("TEMPLATE", body)
        assertEquals(t, run("COPY", body), "COPY differs for `$body`")
        assertEquals(t, run("COMPILED", body), "COMPILED differs for `$body`")
        return t
    }

    @Test
    fun `a metadata key is readable on every strategy`() {
        val out = assertAllAgree("{ t: \$input.^schemaType }")
        assertTrue(out.contains("xsd-schema"), "metadata was null everywhere, so agreement proves nothing: $out")
    }

    @Test
    fun `the double-underscore internal keys resolve through the bare name`() {
        // The parser stores `__schemaType`; UDM.getMetadata tries the bare name then the prefix.
        // Both strategies must go through that accessor, not the raw map.
        val out = assertAllAgree("{ a: \$input.^schemaType, b: \$input.^targetNamespace }")
        assertTrue(out.contains("xsd-schema"), out)
        assertTrue(out.contains("http://example.com/test"), out)
    }

    @Test
    fun `several metadata keys in one object`() {
        val out = assertAllAgree(
            "{ schemaType: \$input.^schemaType, version: \$input.^xsdVersion, " +
                "ns: \$input.^targetNamespace }")
        assertTrue(out.contains("xsd-schema") && out.contains("1.0"), out)
    }

    @Test
    fun `an absent metadata key is null, not an error`() {
        val out = assertAllAgree("{ v: \$input.^noSuchKey ?? \"absent\" }")
        assertTrue(out.contains("absent"), out)
    }

    @Test
    fun `metadata and a property read from the same object`() {
        // Guards the branch order: adding the metadata case must not shadow property access.
        val out = assertAllAgree("{ m: \$input.^schemaType, p: hasKey(\$input, \"xs:element\") }")
        assertTrue(out.contains("xsd-schema") && out.contains("true"), out)
    }

    @Test
    fun `metadata survives a deeper traversal on every strategy`() {
        // COPY rebuilds every nested object, so a nested read exercises deepClone and the merge.
        assertAllAgree("{ inner: { t: \$input.^schemaType } }")
    }
}
