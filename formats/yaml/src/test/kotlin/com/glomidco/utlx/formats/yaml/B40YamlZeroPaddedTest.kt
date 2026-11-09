package com.glomidco.utlx.formats.yaml

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B40 — a **zero-padded** number in YAML is an identifier, and YAML 1.1 made it worse than that.
 *
 * Found while checking whether the XML defect ([B39](../../../../../../../../docs/bugs-fixed)) had
 * siblings in the other parsers. It did, and YAML was the worst of them:
 *
 * ```
 * store:   04.6166        ->  4.6166      leading zero lost, as in XML
 * article: 0100031012     ->  16790026    YAML 1.1 OCTAL — an unrelated number
 * ```
 *
 * The second is the serious one. Truncation at least leaves a recognisably wrong value; silent
 * reinterpretation as octal produces a plausible number with no relation to the input, and nothing
 * downstream can tell.
 *
 * The fix is deliberately narrower than the XML one. SnakeYAML owns YAML's number syntax, and
 * `0x1F`, `0o17`, `1_000` and YAML 1.1 sexagesimals are **deliberate spellings** a user means — so
 * only the zero-padded decimal form is taken back, and everything else still goes to SnakeYAML.
 *
 * JSON and OData needed no change: the JSON lexer rejects a leading zero outright, as the grammar
 * requires, so `{"a": 04.6166}` is a parse error rather than a silent conversion.
 */
class B40YamlZeroPaddedTest {

    private fun value(yaml: String, key: String): Any? {
        val obj = YAMLParser.parseYAML(yaml) as UDM.Object
        return (obj.properties[key] as UDM.Scalar).value
    }

    // ── The identifiers ──

    @Test
    fun `a zero-padded decimal stays a string`() {
        assertEquals("04.6166", value("store: 04.6166", "store"))
    }

    @Test
    fun `a zero-padded integer stays a string instead of becoming octal`() {
        // Was 16790026 — YAML 1.1 read it as octal.
        assertEquals("0100031012", value("article: 0100031012", "article"))
        assertEquals("007", value("padded: 007", "padded"))
        assertEquals("-007", value("neg: -007", "neg"))
    }

    @Test
    fun `an explicitly quoted identifier is unaffected`() {
        assertEquals("007", value("padded: \"007\"", "padded"))
    }

    // ── What must stay a number ──

    @Test
    fun `zero and a leading-zero fraction are still numbers`() {
        // `0` and `0.5` are canonical: the zero is not padding.
        assertEquals(0L, (value("zero: 0", "zero") as Number).toLong())
        assertEquals(BigDecimal("0.5"), value("half: 0.5", "half"))
        assertEquals(BigDecimal("0.50"), value("half: 0.50", "half"))
    }

    @Test
    fun `ordinary numbers are untouched`() {
        assertEquals(12L, (value("n: 12", "n") as Number).toLong())
        assertEquals(-7L, (value("n: -7", "n") as Number).toLong())
        assertEquals(BigDecimal("1.50"), value("p: 1.50", "p"))
        assertEquals(123456789012345678L, (value("b: 123456789012345678", "b") as Number).toLong())
    }

    // ── YAML's own spellings must keep working ──

    @Test
    fun `deliberate YAML number syntax still goes to SnakeYAML`() {
        assertEquals(31L, (value("v: 0x1F", "v") as Number).toLong())
        assertEquals(1000L, (value("v: 1_000", "v") as Number).toLong())
        assertEquals(90L, (value("v: 1:30", "v") as Number).toLong())
    }

    @Test
    fun `non-finite and special floats still delegate`() {
        assertEquals(Double.POSITIVE_INFINITY, value("v: .inf", "v"))
        val nan = value("v: .nan", "v")
        assertTrue(nan is Double && nan.isNaN(), "expected NaN, got $nan")
        assertTrue(value("v: 1.5e3", "v") is Double)
    }
}
