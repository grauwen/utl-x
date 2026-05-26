package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.xml.XMLParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * B21: Tests for c14n functions receiving UDM.Object input (from the pipeline).
 * Before B21 fix, all these returned null because parseXML couldn't handle UDM.Object.
 */
class XMLC14nUDMObjectTest {

    private fun parseXml(xml: String): UDM = XMLParser(xml).parse()

    // ========== c14n with UDM.Object input ==========

    @Test
    fun `c14n with UDM Object produces canonical output`() {
        val udm = parseXml("<doc><b/><a/></doc>")
        val result = XMLCanonicalizationFunctions.c14n(listOf(udm))

        assertTrue(result is UDM.Scalar)
        val canonical = (result as UDM.Scalar).value as String
        assertNotNull(canonical)
        assertTrue(canonical.contains("<doc>"))
        assertTrue(canonical.contains("<a>"))
        assertTrue(canonical.contains("<b>"))
    }

    @Test
    fun `c14n with UDM Object self-closing tags expanded`() {
        val udm = parseXml("<root><empty/></root>")
        val result = XMLCanonicalizationFunctions.c14n(listOf(udm))

        assertTrue(result is UDM.Scalar)
        val canonical = (result as UDM.Scalar).value as String
        // Canonical XML expands self-closing tags
        assertTrue(canonical.contains("<empty></empty>"), "Should expand self-closing: $canonical")
    }

    @Test
    fun `c14n with UDM Object preserves text content`() {
        val udm = parseXml("<order><id>ORD-001</id><customer>Müller GmbH</customer></order>")
        val result = XMLCanonicalizationFunctions.c14n(listOf(udm))

        assertTrue(result is UDM.Scalar)
        val canonical = (result as UDM.Scalar).value as String
        assertTrue(canonical.contains("ORD-001"), "Should preserve text: $canonical")
        assertTrue(canonical.contains("Müller GmbH"), "Should preserve unicode: $canonical")
    }

    @Test
    fun `c14n with UDM Object attributes sorted`() {
        val udm = parseXml("""<root z="3" a="1" m="2"/>""")
        val result = XMLCanonicalizationFunctions.c14n(listOf(udm))

        assertTrue(result is UDM.Scalar)
        val canonical = (result as UDM.Scalar).value as String
        // Canonical XML sorts attributes
        val aPos = canonical.indexOf("a=")
        val mPos = canonical.indexOf("m=")
        val zPos = canonical.indexOf("z=")
        assertTrue(aPos < mPos && mPos < zPos, "Attributes should be sorted: $canonical")
    }

    // ========== c14nWithComments with UDM.Object ==========

    @Test
    fun `c14nWithComments with UDM Object`() {
        val udm = parseXml("<doc><a>1</a></doc>")
        val result = XMLCanonicalizationFunctions.c14nWithComments(listOf(udm))

        assertTrue(result is UDM.Scalar)
        assertNotNull((result as UDM.Scalar).value)
    }

    // ========== c14n11 with UDM.Object ==========

    @Test
    fun `c14n11 with UDM Object`() {
        val udm = parseXml("<doc><item>test</item></doc>")
        val result = XMLCanonicalizationFunctions.c14n11(listOf(udm))

        assertTrue(result is UDM.Scalar)
        assertNotNull((result as UDM.Scalar).value)
    }

    // ========== excC14n with UDM.Object ==========

    @Test
    fun `excC14n with UDM Object and namespaces`() {
        val udm = parseXml("""<doc xmlns:x="http://x"><x:a/><b/></doc>""")
        val result = XMLCanonicalizationFunctions.excC14n(listOf(udm))

        assertTrue(result is UDM.Scalar)
        assertNotNull((result as UDM.Scalar).value)
    }

    // ========== c14nHash with UDM.Object ==========

    @Test
    fun `c14nHash with UDM Object produces SHA-256`() {
        val udm = parseXml("<doc><a>hello</a></doc>")
        val result = XMLCanonicalizationFunctions.c14nHash(
            listOf(udm, UDM.Scalar("sha256"), UDM.Scalar("c14n"))
        )

        assertTrue(result is UDM.Scalar)
        val hash = (result as UDM.Scalar).value as String
        assertEquals(64, hash.length, "SHA-256 = 64 hex chars: $hash")
    }

    @Test
    fun `c14nHash consistent for same UDM Object`() {
        val udm = parseXml("<doc><a>test</a></doc>")
        val hash1 = XMLCanonicalizationFunctions.c14nHash(listOf(udm))
        val hash2 = XMLCanonicalizationFunctions.c14nHash(listOf(udm))

        assertEquals((hash1 as UDM.Scalar).value, (hash2 as UDM.Scalar).value)
    }

    // ========== c14nEquals with UDM.Object ==========

    @Test
    fun `c14nEquals with two UDM Objects`() {
        val udm1 = parseXml("""<root a="1" b="2"/>""")
        val udm2 = parseXml("""<root b="2" a="1"></root>""")

        val result = XMLCanonicalizationFunctions.c14nEquals(listOf(udm1, udm2))
        assertTrue(result is UDM.Scalar)
        // Both produce same canonical form after UDM round-trip
        assertTrue((result as UDM.Scalar).value as Boolean, "Same structure should be equal")
    }

    @Test
    fun `c14nEquals false for different UDM Objects`() {
        val udm1 = parseXml("<root><name>Alice</name></root>")
        val udm2 = parseXml("<root><name>Bob</name></root>")

        val result = XMLCanonicalizationFunctions.c14nEquals(listOf(udm1, udm2))
        assertTrue(result is UDM.Scalar)
        assertFalse((result as UDM.Scalar).value as Boolean)
    }

    // ========== c14nFingerprint with UDM.Object ==========

    @Test
    fun `c14nFingerprint with UDM Object`() {
        val udm = parseXml("<doc><item>data</item></doc>")
        val result = XMLCanonicalizationFunctions.c14nFingerprint(listOf(udm))

        assertTrue(result is UDM.Scalar)
        val fp = (result as UDM.Scalar).value as String
        assertEquals(16, fp.length, "Fingerprint = 16 hex chars: $fp")
        assertTrue(fp.matches(Regex("[0-9a-f]{16}")))
    }

    // ========== canonicalizeWithAlgorithm with UDM.Object ==========

    @Test
    fun `canonicalizeWithAlgorithm with UDM Object`() {
        val udm = parseXml("<doc><b/><a/></doc>")
        val result = XMLCanonicalizationFunctions.canonicalizeWithAlgorithm(
            listOf(udm, UDM.Scalar("c14n"))
        )

        assertTrue(result is UDM.Scalar)
        assertNotNull((result as UDM.Scalar).value)
    }

    // ========== Mixed: UDM.Scalar string + UDM.Object produce same c14n ==========

    @Test
    fun `c14n String input and UDM Object input produce same hash`() {
        val xml = "<doc><name>Alice</name><age>30</age></doc>"
        val hashFromString = XMLCanonicalizationFunctions.c14nHash(listOf(UDM.Scalar(xml)))
        val hashFromUDM = XMLCanonicalizationFunctions.c14nHash(listOf(parseXml(xml)))

        // Both paths should produce the same canonical hash
        assertEquals(
            (hashFromString as UDM.Scalar).value,
            (hashFromUDM as UDM.Scalar).value,
            "String and UDM paths should produce same hash"
        )
    }
}
