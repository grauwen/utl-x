package com.glomidco.utlx.formats.xml

import com.glomidco.utlx.core.format.ParseOptions
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * B42 — a character reference above the BMP was silently mangled into a different character.
 *
 * `Int.toChar()` keeps only the low 16 bits, so the emoji the document asked for became a
 * private-use character:
 *
 * ```
 * <A>&#128512;</A>   (U+1F600 😀)  ->  U+F600
 * <A>&#x1F600;</A>   (U+1F600 😀)  ->  U+F600
 * <A>😀</A>          literal       ->  U+1F600, correct all along
 * ```
 *
 * Nothing caught it because the result is a valid string — just not the one the document named. It
 * bites any producer that escapes non-ASCII on the way out, which is common in exports, and reaches
 * emoji, CJK extensions, mathematical alphanumerics and the historic scripts.
 *
 * Found while verifying [B41](../../../../../../../../docs/bugs-fixed) in the same function.
 */
class B42XmlCharacterReferenceTest {

    private fun text(xml: String, options: ParseOptions = ParseOptions.standard()): Any? {
        val doc = XMLParser(xml, options = options).parse() as UDM.Object
        val root = doc.properties.values.first() as UDM.Object
        val el = root.properties["A"]
        if (el is UDM.Scalar) return el.value
        return ((el as UDM.Object).properties["_text"] as? UDM.Scalar)?.value
    }

    // ── Above the BMP: the defect ──

    @Test
    fun `a decimal reference above the BMP resolves to the right code point`() {
        assertEquals("\uD83D\uDE00", text("<r><A>&#128512;</A></r>"))
        assertEquals(0x1F600, (text("<r><A>&#128512;</A></r>") as String).codePointAt(0))
    }

    @Test
    fun `a hex reference above the BMP resolves to the right code point`() {
        assertEquals(0x1F600, (text("<r><A>&#x1F600;</A></r>") as String).codePointAt(0))
        // U+10FFFF, the last legal code point.
        assertEquals(0x10FFFF, (text("<r><A>&#x10FFFF;</A></r>") as String).codePointAt(0))
    }

    @Test
    fun `a literal astral character was never affected and still is not`() {
        assertEquals(0x1F600, (text("<r><A>\uD83D\uDE00</A></r>") as String).codePointAt(0))
    }

    // ── The BMP cases that always worked must keep working ──

    @Test
    fun `BMP references are unchanged`() {
        assertEquals("A", text("<r><A>&#65;</A></r>"))
        assertEquals("é", text("<r><A>&#233;</A></r>"))
        assertEquals("é", text("<r><A>&#xE9;</A></r>"))
        assertEquals("\uFFFD", text("<r><A>&#xFFFD;</A></r>"))
    }

    @Test
    fun `named entities are unchanged`() {
        assertEquals("<", text("<r><A>&lt;</A></r>"))
        assertEquals("a&b", text("<r><A>a&amp;b</A></r>"))
    }

    @Test
    fun `whitespace references still resolve, and B41 keeps them`() {
        assertEquals("\tx", text("<r><A>&#9;x</A></r>"))
        assertEquals(" x", text("<r><A>&#x20;x</A></r>"))
    }

    // ── Illegal references: corrupt output replaced by a located error ──

    @Test
    fun `a reference to an illegal character is rejected under the standard profile`() {
        // Each of these previously produced a corrupt character instead of an error.
        for (ref in listOf("&#0;", "&#xD800;", "&#x110000;", "&#x7FFFFFFF;")) {
            val e = assertFailsWith<XMLParseException>(ref) { text("<r><A>$ref</A></r>") }
            assertEquals(true, e.message!!.contains("Invalid character reference"), e.message)
        }
    }

    @Test
    fun `an unparseable numeric reference is rejected under the standard profile`() {
        assertFailsWith<XMLParseException> { text("<r><A>&#xZZ;</A></r>") }
        assertFailsWith<XMLParseException> { text("<r><A>&#99999999999999;</A></r>") }
    }

    @Test
    fun `the lenient profile keeps an illegal reference as literal text`() {
        // Same axis the undefined-entity case already uses (B29): lenient preserves the source text
        // rather than inventing a character.
        assertEquals("&#0;", text("<r><A>&#0;</A></r>", ParseOptions.lenient()))
        assertEquals("&#xD800;", text("<r><A>&#xD800;</A></r>", ParseOptions.lenient()))
        // A legal reference still resolves under lenient.
        assertEquals(0x1F600, (text("<r><A>&#x1F600;</A></r>", ParseOptions.lenient()) as String).codePointAt(0))
    }
}
