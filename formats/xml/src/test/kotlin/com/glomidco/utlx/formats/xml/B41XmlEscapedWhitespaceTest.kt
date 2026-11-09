package com.glomidco.utlx.formats.xml

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * B41 — whitespace the sender **escaped** is data, and the trim must not eat it.
 *
 * XML leaf text is trimmed, and that default is right: indentation is nearly always the
 * pretty-printer's, not the author's. But it was applied to *all* whitespace, including whitespace
 * written as a character reference or inside a CDATA section — which no formatter emits and no
 * reading of XML treats as layout:
 *
 * ```
 * <A>&#32;&#32;&#32;indented</A>   ->  "indented"   the author's three spaces, discarded
 * <A><![CDATA[   indented]]></A>   ->  "indented"   literal character data, discarded
 * <A>&#32;</A>                     ->  {}           a significant blank became an empty element
 * ```
 *
 * The last line is the loss with no workaround: nothing downstream can tell a blank line from a
 * missing one.
 *
 * This is the answer to the `trimText: false` request in
 * [B39](../../../../../../../../docs/bugs-fixed)'s field report, and a better one than the option
 * asked for: it is **per element** and controlled by the sender, who knows which fields are layout,
 * where a document-wide flag cannot express "indentation is content here, formatting there".
 */
class B41XmlEscapedWhitespaceTest {

    /** The leaf's text, or `null` when the element came back empty (no `_text` at all). */
    private fun text(xml: String, name: String): Any? {
        val doc = XMLParser(xml).parse() as UDM.Object
        val root = doc.properties.values.first() as UDM.Object
        val el = root.properties[name]
        if (el is UDM.Scalar) return el.value
        return ((el as UDM.Object).properties["_text"] as? UDM.Scalar)?.value
    }

    // ── Escaped whitespace survives ──

    @Test
    fun `character references keep their whitespace`() {
        assertEquals("   indented", text("<r><A>&#32;&#32;&#32;indented</A></r>", "A"))
    }

    @Test
    fun `hex character references keep their whitespace`() {
        assertEquals(" x", text("<r><A>&#x20;x</A></r>", "A"))
    }

    @Test
    fun `cdata keeps its whitespace`() {
        assertEquals("   indented", text("<r><A><![CDATA[   indented]]></A></r>", "A"))
    }

    @Test
    fun `a trailing escaped space survives`() {
        assertEquals("x ", text("<r><A>x&#32;</A></r>", "A"))
    }

    // ── The loss this closes: a significant blank is no longer an empty element ──

    @Test
    fun `an escaped blank is a value, not an empty element`() {
        assertEquals(" ", text("<r><A>&#32;</A></r>", "A"))
    }

    @Test
    fun `a cdata blank is a value, not an empty element`() {
        assertEquals(" ", text("<r><A><![CDATA[ ]]></A></r>", "A"))
    }

    @Test
    fun `a genuinely empty element is still empty`() {
        assertNull(text("<r><A></A></r>", "A"))
        assertNull(text("<r><A/></r>", "A"))
    }

    // ── Literal whitespace is still trimmed: the default is unchanged ──

    @Test
    fun `literal indentation is still trimmed`() {
        assertEquals("plain", text("<r><A>   plain   </A></r>", "A"))
        assertEquals("plain", text("<r><A>\n      plain\n   </A></r>", "A"))
    }

    @Test
    fun `a whitespace-only literal element is still empty`() {
        // Recorded, not endorsed: the bare form stays as it was, because `&#32;` and CDATA now give
        // the sender a way to say "this blank matters". See B39's open items.
        assertNull(text("<r><A> </A></r>", "A"))
    }

    @Test
    fun `numeric inference is unaffected`() {
        assertEquals(BigDecimal("299.99"), text("<r><Total> 299.99 </Total></r>", "Total"))
        assertEquals(12L, text("<r><Qty>12</Qty></r>", "Qty"))
        // CDATA is deliberately NOT overloaded to mean "not a number": senders use it to avoid
        // escaping `&`, so reading it as a type signal would be an invention.
        assertEquals(12L, text("<r><Qty><![CDATA[12]]></Qty></r>", "Qty"))
    }

    // ── Trim semantics at the boundary ──

    @Test
    fun `literal whitespace outside an escaped run is trimmed, inside it is kept`() {
        // `  ` + escaped ` ` + `x` + escaped ` ` + `  ` — the outer literal runs go, the escaped
        // spaces stay, and the literal spaces they shelter become interior text.
        assertEquals(" x ", text("<r><A>  &#32;x&#32;  </A></r>", "A"))
    }

    @Test
    fun `an entity that is not whitespace is unaffected`() {
        assertEquals("a&b", text("<r><A>  a&amp;b  </A></r>", "A"))
        assertEquals("<tag>", text("<r><A>&lt;tag&gt;</A></r>", "A"))
    }

    @Test
    fun `attribute values were never trimmed and still are not`() {
        val doc = XMLParser("<r><A pad=\"  kg  \">x</A></r>").parse() as UDM.Object
        val root = doc.properties.values.first() as UDM.Object
        val a = root.properties["A"] as UDM.Object
        assertEquals("  kg  ", a.attributes["pad"])
    }
}
