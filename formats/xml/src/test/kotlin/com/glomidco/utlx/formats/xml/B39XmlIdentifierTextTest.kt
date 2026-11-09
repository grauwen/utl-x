package com.glomidco.utlx.formats.xml

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B39 — XML text that looks numeric but is an **identifier** must stay a string.
 *
 * Reported from the field against v1.3.1, on a Centric ORS POS feed: `RetailStoreID` is `04.6166`
 * and became `4.6166`, so the chain prefix was lost; article numbers like `0100031012` lost their
 * leading zero. The report notes the decisive part — a script cannot undo it, because the text is
 * converted before the script runs, and `toString()` afterwards returns the normalised form.
 *
 * The fix needs no option: a leading zero is **not** a number in JSON's grammar (`0` or `[1-9]\d*`),
 * and in practice it is exactly the marker of an identifier. Inference from free text now uses that
 * grammar.
 */
class B39XmlIdentifierTextTest {

    private fun field(xml: String, name: String): Any? {
        val doc = XMLParser(xml).parse() as UDM.Object
        val root = doc.properties.values.first() as UDM.Object
        val el = root.properties[name]
        val scalar = (el as? UDM.Scalar) ?: ((el as UDM.Object).properties["_text"] as UDM.Scalar)
        return scalar.value
    }

    @Test
    fun `a store id with a leading zero keeps its text form`() {
        assertEquals("04.6166", field("<r><RetailStoreID>04.6166</RetailStoreID></r>", "RetailStoreID"))
    }

    @Test
    fun `an article number with a leading zero keeps its text form`() {
        assertEquals("0100031012", field("<r><ArticleNumber>0100031012</ArticleNumber></r>", "ArticleNumber"))
    }

    @Test
    fun `ordinary numeric text is still a number`() {
        assertEquals(12L, field("<r><Qty>12</Qty></r>", "Qty"))
        assertEquals(BigDecimal("1.50"), field("<r><Price>1.50</Price></r>", "Price"))
        assertEquals(BigDecimal("0.5"), field("<r><Half>0.5</Half></r>", "Half"))
        assertEquals(-7L, field("<r><Delta>-7</Delta></r>", "Delta"))
    }

    @Test
    fun `a long barcode keeps every digit`() {
        // The other half of the same report: on v1.3.1 an 18-digit barcode lost its last digits
        // and a 22-digit GTIN was destroyed. Both are exact now.
        assertEquals(123456789012345678L,
            field("<r><Barcode>123456789012345678</Barcode></r>", "Barcode"))
        assertEquals(BigInteger("2200000000000000000012"),
            field("<r><GTIN>2200000000000000000012</GTIN></r>", "GTIN"))
    }

    @Test
    fun `non-numeric text is unaffected`() {
        assertEquals("ABC-123", field("<r><Code>ABC-123</Code></r>", "Code"))
        assertEquals("1,5", field("<r><Amt>1,5</Amt></r>", "Amt"))
    }

    @Test
    fun `leading whitespace in text is still trimmed — a separate, open issue`() {
        // Recorded rather than fixed: the same report asks for leading spaces to survive in POS
        // block text used to lay out a receipt. XML leaf text is trimmed before any numeric check,
        // so that needs an option of its own and is tracked separately.
        assertEquals("indented", field("<r><Block>   indented</Block></r>", "Block"))
    }
}
