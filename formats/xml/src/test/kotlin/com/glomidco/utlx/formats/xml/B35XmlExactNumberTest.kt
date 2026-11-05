package com.glomidco.utlx.formats.xml

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B35 — XML inferred numbers through `toDoubleOrNull` only, so like CSV it corrupted int64 values
 * that JSON preserved. Same contract as the CSV tests: exact where the written form allows it, and
 * the decision about what counts as a number left exactly as it was.
 */
class B35XmlExactNumberTest {

    /**
     * Value of the single child element. `parse()` returns the document wrapper
     * (`{r: {child: ...}}`), and an element with only text content may itself arrive wrapped in a
     * `_text` property, so both levels are unwrapped here.
     */
    private fun text(xml: String): Any? {
        val doc = XMLParser(xml).parse() as UDM.Object
        val root = doc.properties.values.first() as UDM.Object
        val el = root.properties.values.first()
        val scalar = (el as? UDM.Scalar)
            ?: ((el as UDM.Object).properties["_text"] as UDM.Scalar)
        return scalar.value
    }

    @Test
    fun `an integer above 2^53 is preserved`() {
        assertEquals(9007199254740993L, text("<r><id>9007199254740993</id></r>"))
    }

    @Test
    fun `a decimal keeps its scale`() {
        val v = text("<r><price>100.10</price></r>")
        assertEquals(BigDecimal("100.10"), v)
        assertEquals(2, (v as BigDecimal).scale())
    }

    @Test
    fun `a decimal beyond Double precision is exact`() {
        assertEquals(BigDecimal("12345678901234567.89"), text("<r><v>12345678901234567.89</v></r>"))
    }

    @Test
    fun `exponent notation stays a Double`() {
        assertTrue(text("<r><v>1.5e3</v></r>") is Double)
    }

    @Test
    fun `non-numeric text is still a string`() {
        assertEquals("abc", text("<r><v>abc</v></r>"))
        assertEquals("1,5", text("<r><v>1,5</v></r>"))
    }

    @Test
    fun `a preserved decimal serialises back as written`() {
        val udm = XMLParser("<r><price>100.10</price></r>").parse()
        val out = XMLSerializer().serialize(udm)
        assertTrue(out.contains("100.10"), "trailing zero lost on output: $out")
    }
}
