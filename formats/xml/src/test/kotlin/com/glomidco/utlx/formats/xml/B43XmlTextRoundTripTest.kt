package com.glomidco.utlx.formats.xml

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * B43 — the XML serialiser wrote significant whitespace literally, so the next read trimmed it away.
 *
 * [B41](../../../../../../../../docs/bugs-fixed) made the **reading** side faithful: whitespace the
 * sender escaped, or wrote inside CDATA, survives the trim. That exposed the writing side, which had
 * no such notion — it emitted the whitespace literally, and a parser (this one included) trims
 * literal whitespace at the edges of element text:
 *
 * ```
 * value "  indented"   ->  <A>  indented</A>   ->  re-read as "indented"
 * value " "            ->  <A> </A>            ->  re-read as an empty element
 * value "x\t"          ->  <A>x\t</A>          ->  re-read as "x"
 * ```
 *
 * So the value survived in memory but not through XML, and an XML→XML transformation quietly lost
 * it. The two halves only make a faithful round trip together.
 *
 * The rule is narrow: whitespace in the **leading or trailing** run becomes a character reference,
 * because trimming only reaches the edges. Interior whitespace, markup escaping and attribute values
 * are untouched.
 */
class B43XmlTextRoundTripTest {

    private fun serialize(udm: UDM): String = XMLSerializer().serialize(udm)

    private fun leafDoc(text: String): UDM =
        UDM.Object(mapOf("A" to UDM.Object(mapOf("_text" to UDM.Scalar.string(text)), emptyMap(), "A")), emptyMap(), "R")

    /** Serialise a one-leaf document, read it back, and return the leaf's text. */
    private fun roundTrip(text: String): Any? {
        val xml = serialize(leafDoc(text))
        val doc = XMLParser(xml).parse() as UDM.Object
        val root = doc.properties.values.first() as UDM.Object
        val el = root.properties["A"]
        if (el is UDM.Scalar) return el.value
        return ((el as UDM.Object).properties["_text"] as? UDM.Scalar)?.value
    }

    // ── The round trip, which is the whole point ──

    @Test
    fun `leading whitespace survives a round trip`() {
        assertEquals("  indented", roundTrip("  indented"))
    }

    @Test
    fun `trailing whitespace survives a round trip`() {
        assertEquals("x\t", roundTrip("x\t"))
        assertEquals("x ", roundTrip("x "))
    }

    @Test
    fun `a whitespace-only value survives a round trip`() {
        assertEquals(" ", roundTrip(" "))
        assertEquals("\n", roundTrip("\n"))
    }

    @Test
    fun `ordinary text round-trips unchanged`() {
        assertEquals("plain", roundTrip("plain"))
        assertEquals("two words", roundTrip("two words"))
        assertEquals("a & b < c", roundTrip("a & b < c"))
    }

    @Test
    fun `interior whitespace round-trips and is not escaped`() {
        assertEquals("a\tb", roundTrip("a\tb"))
        assertEquals("line1\nline2", roundTrip("line1\nline2"))
        // Interior whitespace stays literal in the document — only the edges are escaped.
        val xml = serialize(leafDoc("a\tb"))
        assertEquals(true, xml.contains("a\tb"), xml)
    }

    @Test
    fun `an astral character round-trips`() {
        assertEquals("\uD83D\uDE00", roundTrip("\uD83D\uDE00"))
        assertEquals(" \uD83D\uDE00 ", roundTrip(" \uD83D\uDE00 "))
    }

    // ── What the document looks like ──

    @Test
    fun `edge whitespace is written as a character reference`() {
        val xml = serialize(leafDoc("  indented"))
        assertEquals(true, xml.contains("&#32;&#32;indented"), xml)
    }

    @Test
    fun `a whitespace-only value is written as a character reference`() {
        assertEquals(true, serialize(leafDoc(" ")).contains("&#32;"))
        assertEquals(true, serialize(leafDoc("\t")).contains("&#9;"))
    }

    @Test
    fun `markup escaping is unchanged`() {
        val xml = serialize(leafDoc("a & b < c > d"))
        assertEquals(true, xml.contains("a &amp; b &lt; c &gt; d"), xml)
    }

    @Test
    fun `an empty value writes no character references`() {
        val xml = serialize(leafDoc(""))
        assertEquals(false, xml.contains("&#"), xml)
    }

    @Test
    fun `attribute values are not edge-escaped`() {
        // Attributes are never trimmed on the way in, so there is nothing to protect and no reason
        // to make the document noisier.
        val udm = UDM.Object(
            mapOf("A" to UDM.Object(mapOf("_text" to UDM.Scalar.string("x")), mapOf("pad" to "  kg  "), "A")),
            emptyMap(), "R"
        )
        val xml = serialize(udm)
        assertEquals(true, xml.contains("pad=\"  kg  \""), xml)
    }
}
