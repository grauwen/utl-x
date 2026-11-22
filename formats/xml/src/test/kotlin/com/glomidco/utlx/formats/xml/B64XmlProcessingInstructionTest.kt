package com.glomidco.utlx.formats.xml

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * B64 — a processing instruction inside an element reached `parseName` and threw
 * "Invalid name start: ?".
 *
 * B27 gave the parser `skipProcessingInstruction()` and a `skipMisc()` that handles comments,
 * processing instructions and DOCTYPE together, but wired `skipMisc` into the prolog and epilog
 * only. Element content had its own narrower dispatch — `<!--` and `<![CDATA[` and nothing else —
 * so `<order><?pi data?>…` fell through to the child-element branch.
 *
 * The conformance fixture `formats/xml/processing_instruction_in_content.yaml` covers the success
 * path through all three execution strategies. These tests cover what a fixture cannot reach: the
 * parser's own output, the failure cases, and the one design decision that is otherwise unguarded.
 */
class B64XmlProcessingInstructionTest {

    /** `parse()` returns the document wrapper, so the root is one level in. */
    private fun root(xml: String): UDM.Object {
        val doc = XMLParser(xml).parse() as UDM.Object
        return doc.properties.values.first() as UDM.Object
    }

    private fun childNames(xml: String): List<String> =
        root(xml).properties.keys.filterNot { it.startsWith("_") }

    // ── the reported case ──

    @Test
    fun `a processing instruction inside an element parses`() {
        val names = childNames("<order><!-- hi --><?pi data?><orderId>A-1</orderId></order>")
        assertEquals(listOf("orderId"), names,
            "the PI must be skipped entirely, leaving only the real child")
    }

    @Test
    fun `a processing instruction is skipped, not turned into a child`() {
        // The assertion that carries the weight. If a PI became an element the document would
        // still "parse" and every text value would still be right — only the shape would be
        // wrong, which is the failure mode worth guarding.
        val xml = """
            <order>
              <?pi after-open?>
              <orderId>A-1</orderId>
              <?pi between?>
              <customer>Acme</customer>
              <?pi before-close?>
            </order>
        """.trimIndent()
        assertEquals(listOf("orderId", "customer"), childNames(xml),
            "three processing instructions must contribute no children")
    }

    @Test
    fun `a processing instruction with no data parses`() {
        assertEquals(listOf("a"), childNames("<r><?pi?><a>1</a></r>"))
    }

    @Test
    fun `text either side of a processing instruction survives`() {
        val doc = XMLParser("<r>before <?pi x?>after</r>").parse() as UDM.Object
        val text = (doc.properties.values.first() as? UDM.Scalar)?.value?.toString()
            ?: ((doc.properties.values.first() as UDM.Object).properties["_text"] as? UDM.Scalar)?.value?.toString()
        assertTrue(text != null && text.contains("before") && text.contains("after"),
            "the PI is removed from the middle of the text, the text is not: got '$text'")
    }

    // ── what the fixture cannot reach ──

    @Test
    fun `an unterminated processing instruction is still an error`() {
        // Skipping must not mean "swallow to end of input". Without a closing ?> this is
        // malformed and has to say so rather than silently consuming the rest of the document.
        val e = assertFailsWith<XMLParseException> {
            XMLParser("<r><?pi never closed</r>").parse()
        }
        assertTrue(e.message!!.contains("Unterminated processing instruction"),
            "expected the unterminated-PI message, got: ${e.message}")
    }

    @Test
    fun `DOCTYPE inside an element is still rejected`() {
        // This guards a deliberate choice. skipMisc() already handles processing instructions AND
        // DOCTYPE, so reusing it here would have been the shorter fix — and would have made the
        // parser silently accept a DOCTYPE inside an element, which is illegal XML. The content
        // branch therefore calls skipProcessingInstruction() specifically. If someone later
        // "simplifies" it to skipMisc, this test is what fails.
        val e = assertFailsWith<XMLParseException> {
            XMLParser("<r><!DOCTYPE foo><a>1</a></r>").parse()
        }
        assertTrue(e.message!!.contains("Invalid name start: !"),
            "expected the content branch to refuse '!', got: ${e.message}")

        // Vacuity guard: the same DOCTYPE in the prolog is legal and must still be accepted,
        // so the assertion above is about position, not about DOCTYPE being unsupported.
        assertEquals(listOf("a"), childNames("<!DOCTYPE r><r><a>1</a></r>"))
    }

    @Test
    fun `a PI-like sequence inside CDATA stays literal`() {
        // CDATA is character data: the parser must not reach into it looking for instructions.
        val doc = XMLParser("<r><![CDATA[<?pi not an instruction?>]]></r>").parse() as UDM.Object
        val r = doc.properties.values.first()
        val text = (r as? UDM.Scalar)?.value?.toString()
            ?: ((r as UDM.Object).properties["_text"] as? UDM.Scalar)?.value?.toString()
        assertTrue(text != null && text.contains("<?pi"),
            "CDATA content must survive verbatim: got '$text'")
    }

    // ── B27 must keep working ──

    @Test
    fun `prolog and epilog processing instructions still parse`() {
        val names = childNames("<?xml version=\"1.0\"?><?before?><r><a>1</a></r><?after?>")
        assertEquals(listOf("a"), names)
    }
}
