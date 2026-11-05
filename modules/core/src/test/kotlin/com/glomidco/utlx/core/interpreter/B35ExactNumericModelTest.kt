package com.glomidco.utlx.core.interpreter

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B35 root-cause regression tests — **a number must compare as the number it is written as.**
 *
 * Root cause: every numeric literal was a `Double` from parse time onward, and comparison narrowed
 * both operands to `Double` before testing them. So `9007199254740993` *became* …992 while being
 * read, and two different integers compared equal. The data path was already exact in places,
 * which made the result incoherent rather than merely imprecise.
 *
 * The fix has two halves that only work together:
 *
 *  - **exact literals** — `Expression.NumberLiteral` carries the exact value of its written form
 *    alongside the Double reading.
 *  - **canonical comparison** — `valuesEqual` and `compareValues` canonicalise through
 *    `ScalarCanonical` instead of narrowing to `Double`.
 *
 * Landing only the second half would have been *worse than nothing*: with literals still Double, an
 * author writing `$input.id == 9007199254740993` would get a literal of …992, an exact comparison
 * against it, and `false` — the right number typed, the wrong answer returned, failing in the
 * opposite direction. Several tests below exist specifically to catch a future change that keeps one
 * half and drops the other.
 *
 * Canonical comparison also has to stay *scale-insensitive*. Preserving what the parser was given
 * means the same quantity now arrives in different boxes — `100.10` as a BigDecimal of scale 2,
 * `100.1` as scale 1, `5` as a Long, `5.0` as a BigDecimal. Raw boxed equality says those are all
 * different. Trading "two big integers compare equal" for "`5 == 5.0` is false" would be a worse
 * defect than the one being fixed, so both directions are pinned here.
 *
 * Still deliberately NOT fixed: **arithmetic**. `19.99 * 100 == 1999` remains false, because exact
 * computation needs a division scale and rounding mode chosen at the language level. The tests at
 * the end pin that limitation so it is visible and so landing it later is a deliberate act.
 */
class B35ExactNumericModelTest {

    /**
     * Payload bound to `$input`. Values are built as the exact types the format parsers now
     * produce, so these tests exercise the comparison path without depending on a format module.
     */
    private fun run(body: String): UDM {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        val tokens = Lexer(src).tokenize()
        val program = when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        val input = UDM.Object.of(
            "big" to UDM.Scalar(9007199254740993L),
            "bigMinusOne" to UDM.Scalar(9007199254740992L),
            "negBig" to UDM.Scalar(BigDecimal("-9007199254740993")),
            "dec" to UDM.Scalar(BigDecimal("100.10")),
            "decSameValue" to UDM.Scalar(BigDecimal("100.1")),
            "five" to UDM.Scalar(5L),
            "fiveDec" to UDM.Scalar(BigDecimal("5.0")),
            "flag" to UDM.Scalar(true),
            "money" to UDM.Scalar(BigDecimal("19.99")),
            "minLong" to UDM.Scalar(Long.MIN_VALUE)
        )
        return Interpreter().execute(program, input).toUDM()
    }

    private fun value(udm: UDM): Any? = (udm as UDM.Scalar).value

    // ── Guard against vacuous assertions ──

    @Test
    fun `the payload really holds exact types — without this the comparisons prove nothing`() {
        // If the harness bound Doubles, every assertion below would pass for the wrong reason.
        assertEquals(9007199254740993L, value(run("\$input.big")))
        assertEquals(BigDecimal("100.10"), value(run("\$input.dec")))
    }

    // ── Equality above 2^53: the original complaint ──

    @Test
    fun `two different integers above 2^53 are not equal`() {
        assertEquals(false, value(run("9007199254740993 == 9007199254740992")))
    }

    @Test
    fun `payload int64 equals the literal that was actually typed`() {
        assertEquals(true, value(run("\$input.big == 9007199254740993")))
    }

    @Test
    fun `payload int64 does not equal a different literal`() {
        // This is the half-fix detector: with exact comparison but Double literals, the literal
        // below would be …992 and this would wrongly be true via the OTHER literal.
        assertEquals(false, value(run("\$input.big == 9007199254740992")))
        assertEquals(true, value(run("\$input.bigMinusOne == 9007199254740992")))
    }

    @Test
    fun `two payload int64 values that differ are not equal`() {
        assertEquals(false, value(run("\$input.big == \$input.bigMinusOne")))
    }

    @Test
    fun `ordering above 2^53 is correct`() {
        assertEquals(true, value(run("\$input.big > 9007199254740992")))
        assertEquals(false, value(run("\$input.bigMinusOne > 9007199254740993")))
        assertEquals(true, value(run("9007199254740993 > 9007199254740992")))
    }

    @Test
    fun `a negative literal keeps its exact form through unary minus`() {
        assertEquals(true, value(run("\$input.negBig == -9007199254740993")))
        assertEquals(false, value(run("\$input.negBig == -9007199254740992")))
    }

    // ── Scale insensitivity: the regression that preserving precision would otherwise cause ──

    @Test
    fun `trailing zeros do not change equality`() {
        assertEquals(true, value(run("\$input.dec == \$input.decSameValue")))
        assertEquals(true, value(run("\$input.dec == 100.1")))
        assertEquals(true, value(run("\$input.dec == 100.10")))
    }

    @Test
    fun `an integer equals the same value written as a decimal`() {
        assertEquals(true, value(run("\$input.five == \$input.fiveDec")))
        assertEquals(true, value(run("\$input.five == 5.0")))
        assertEquals(true, value(run("\$input.fiveDec == 5")))
    }

    @Test
    fun `ordering works across mixed numeric representations`() {
        assertEquals(true, value(run("\$input.five < \$input.dec")))
        assertEquals(true, value(run("\$input.fiveDec <= 5")))
        assertEquals(false, value(run("\$input.fiveDec < 5")))
    }

    // ── Things that must NOT become numbers ──

    @Test
    fun `a boolean is not a number`() {
        assertEquals(false, value(run("\$input.flag == 1")))
    }

    @Test
    fun `a numeric string is not the number`() {
        assertEquals(false, value(run("\"5\" == 5")))
    }

    // ── Exponent forms keep the pre-existing Double path, but still compare correctly ──

    @Test
    fun `an exponent literal compares equal to the same quantity written plainly`() {
        assertEquals(true, value(run("1e10 == 10000000000")))
        assertEquals(true, value(run("2.5E-1 == 0.25")))
    }

    // ── Known remaining limitation: arithmetic is still Double ──

    @Test
    fun `multiplication is exact — the headline case`() {
        assertEquals(true, value(run("19.99 * 100 == 1999")))
        assertEquals(true, value(run("\$input.money * 100 == 1999")))
    }

    @Test
    fun `addition is exact, so the classic float artefact is gone`() {
        assertEquals(false, value(run("0.1 + 0.2 > 0.3")))
        assertEquals(true, value(run("0.1 + 0.2 == 0.3")))
    }

    @Test
    fun `subtraction above 2^53 no longer collapses`() {
        // Was 0.0: both operands became the same Double before being subtracted.
        // The result is a BigDecimal rather than a Long — exact arithmetic works in one numeric
        // type. The VALUE is what matters and it is exact; it serialises as `1`, and comparison
        // canonicalises, so the difference in box is not observable to an author.
        assertEquals(BigDecimal("1"), value(run("9007199254740993 - 9007199254740992")))
        assertEquals(true, value(run("9007199254740993 - 9007199254740992 == 1")))
    }

    @Test
    fun `division stays approximate, deliberately`() {
        // Exact division needs a digit count and a rounding mode, which are prescribed by law in
        // some tax regimes and so are a business decision rather than an engineering one. If this
        // ever changes it must be a deliberate act, not a side effect — hence the assertion.
        assertEquals(3.3333333333333335, value(run("10 / 3")))
        assertEquals(1.0, value(run("10 % 3")))
    }

    @Test
    fun `a Double operand keeps the whole operation approximate`() {
        // An exponent-form literal carries no exact value, so there is nothing to preserve and the
        // cheap path is used — which is what keeps float-style scripts at float speed.
        assertEquals(150.0, value(run("1e2 * 1.5")))
        // Division yields a Double, so arithmetic after it is approximate again. This is the
        // author-facing rule: division returns you to approximate arithmetic.
        assertEquals(10.0, value(run("(10 / 3) * 3")))
    }

    @Test
    fun `scale follows the inputs`() {
        // Adding keeps the larger scale; multiplying adds them. That is the arithmetically exact
        // answer, and it is why a computed money total now carries its inputs' cents instead of
        // being rounded to look tidy.
        assertEquals(BigDecimal("4.00"), value(run("1.50 + 2.50")))
        assertEquals(BigDecimal("29.985"), value(run("19.99 * 1.5")))
        assertEquals(BigDecimal("8.00"), value(run("100 * 0.08")))
    }

    // ── Pattern literals: `match` must select the arm the author wrote ──

    @Test
    fun `match selects the arm written for an int64 above 2^53`() {
        // Before the fix both arms were the Double ...992: the value matched the WRONG arm. With
        // exact comparison but a Double pattern it matched NO arm. Both halves are needed here too.
        val src = """
            match (${'$'}input.big) {
              9007199254740992 => "neighbour",
              9007199254740993 => "correct",
              _ => "none"
            }
        """.trimIndent()
        assertEquals("correct", value(run(src)))
    }

    @Test
    fun `match on a decimal ignores scale, as equality does`() {
        assertEquals("hit", value(run("match (\$input.dec) { 100.1 => \"hit\", _ => \"none\" }")))
    }

    @Test
    fun `match still falls through when nothing matches`() {
        assertEquals("none", value(run("match (\$input.big) { 1 => \"one\", _ => \"none\" }")))
    }

    // ── UDM accessors over exact values ──

    @Test
    fun `an exact value stringifies as written rather than in exponent form`() {
        // BigDecimal.toString() would give 1E-7 here.
        assertEquals("0.0000001", UDM.Scalar(java.math.BigDecimal("0.0000001")).asString())
        assertEquals("100.10", UDM.Scalar(java.math.BigDecimal("100.10")).asString())
        assertEquals("9007199254740993", UDM.Scalar(9007199254740993L).asString())
    }

    @Test
    fun `asNumber still narrows to Double and isNumber still recognises exact types`() {
        val exact = UDM.Scalar(java.math.BigDecimal("3.14"))
        assertEquals(3.14, exact.asNumber())
        assertTrue(exact.isNumber())
        assertTrue(UDM.Scalar(9007199254740993L).isNumber())
    }

    // ── Design properties asserted elsewhere in prose, pinned here ──

    @Test
    fun `the exact form is excluded from NumberValue equality`() {
        // The field is deliberately not a constructor property, so adding it changed nothing for
        // the dozens of places that construct or compare a NumberValue. If someone promotes it to
        // the constructor, this fails and they have to think about the blast radius.
        val plain = RuntimeValue.NumberValue(5.0)
        val withExact = RuntimeValue.NumberValue.exactly(5.0, 5L)
        assertEquals(plain, withExact)
        assertEquals(plain.hashCode(), withExact.hashCode())
        assertEquals(5L, withExact.exact)
        assertEquals(null, plain.exact)
    }

    @Test
    fun `negating the most negative Long does not wrap around`() {
        // -Long.MIN_VALUE has no Long representation. Negating it in Kotlin yields Long.MIN_VALUE
        // again, so the exact negation has to widen; otherwise `-x` would equal `x`.
        assertEquals(true, value(run("-\$input.minLong == 9223372036854775808")))
        assertEquals(false, value(run("-\$input.minLong == \$input.minLong")))
    }

    @Test
    fun `string concatenation renders a number as written`() {
        // `"x" + 1` used to produce "x1.0", because the only value available was the Double.
        assertEquals("x1", value(run("\"x\" + 1")))
        assertEquals("x1.5", value(run("\"x\" + 1.5")))
        assertEquals("x100.10", value(run("\"x\" + \$input.dec")))
    }
}
