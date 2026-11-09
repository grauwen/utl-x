package com.glomidco.utlx.core.format

import java.math.BigDecimal
import java.math.BigInteger

/**
 * B35 Stage 0 — **exact numeric representation for format parsers.**
 *
 * `UDM.Scalar` holds `Any?` and its factory is `fun number(n: Number)`, so the data model has always
 * accepted any `Number`, `BigDecimal` included. The precision was discarded earlier, by each format
 * parser independently choosing a numeric type: JSON tried `Long` first and fell back to `Double`,
 * while CSV and XML routed *every* numeric value through `Double`. The result was that the same
 * value changed according to which parser read it — `9007199254740993` survived a JSON round-trip
 * and was corrupted to `…992` by a CSV or XML one. For a *universal* data model that is a broken
 * promise rather than a rounding complaint, and it is what this object fixes.
 *
 * The rule is **exactness follows the written form**:
 *
 * | lexeme | result | why |
 * |---|---|---|
 * | `9007199254740993` | `Long` | integral, fits |
 * | 400 digits | `BigInteger` | integral, does not fit |
 * | `100.10` | `BigDecimal` | plain decimal — scale (and so the trailing zero) is part of the value |
 * | `1e10`, `NaN`, `0x1p3` | `Double` | exponent/special forms keep the pre-existing path |
 *
 * Exponent forms stay on `Double` deliberately. `BigDecimal("1e10").toString()` is `1E+10`, so
 * promoting them would change serialiser output for a notation that is already approximation-shaped,
 * buying no exactness anyone asked for. Every value in the B35 report is written in plain form.
 *
 * **Acceptance is unchanged.** [ofOrNull] returns `null` for exactly the inputs
 * `String.toDoubleOrNull()` rejects, so no text that is a number today stops being one, and none
 * that is a string today becomes a number. Only the *representation* improves. That is what keeps
 * this a one-line change per parser instead of a numeric-model project: the interpreter narrows to
 * `Double` on use anyway, and `Number.toDouble()` works on every type returned here.
 *
 * Downstream, nothing needs to change — the serialisers dispatch on `is Number` and call
 * `toString()`, so a `BigDecimal` renders as written.
 */
object ExactNumber {

    /** Plain integral form: optional sign, digits only. No `.`, no exponent. */
    private val PLAIN_INTEGER = Regex("[+-]?\\d+")

    /** Plain decimal form: a `.` with digits on at least one side. No exponent. */
    private val PLAIN_DECIMAL = Regex("[+-]?(\\d+\\.\\d*|\\.\\d+)")

    /**
     * The most exact [Number] that represents [text], or `null` if [text] is not numeric.
     *
     * Leading and trailing whitespace is ignored, matching `Double.parseDouble`, so a parser that
     * hands over raw element text gets the same accept/reject decision it did before.
     */
    fun ofOrNull(text: String): Number? {
        val s = text.trim()
        if (s.isEmpty()) return null

        if (PLAIN_INTEGER.matches(s)) {
            s.toLongOrNull()?.let { return it }
            // Integral but wider than Long — still exact, just unbounded.
            runCatching { return BigInteger(s) }
        }
        if (PLAIN_DECIMAL.matches(s)) {
            // BigDecimal keeps the scale, which is what preserves `100.10` as written.
            runCatching { return BigDecimal(s) }
        }
        // Exponent notation, NaN/Infinity, hex float, `1d`/`1f` suffixes: the pre-B35 path.
        return s.toDoubleOrNull()
    }

    /**
     * Canonical JSON number form: no leading zero, optional fraction, optional exponent.
     *
     * This is the JSON grammar's own definition (`int frac? exp?` where `int` is `0` or
     * `[1-9]\d*`), which is the point — a leading zero is *not* a number in JSON, and in practice
     * it is the marker of an identifier.
     */
    private val CANONICAL_JSON_NUMBER =
        Regex("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?")

    /** A decimal number written with a leading zero: `04.6166`, `0100031012`, `007`. */
    private val ZERO_PADDED = Regex("-?0\\d+(\\.\\d+)?")

    /**
     * B40: is [text] a **zero-padded** decimal — a leading zero followed by another digit?
     *
     * That shape is an identifier in practice (a store code, an article number, a padded
     * reference), and it is not a number in JSON's grammar. `0.5` and `0` are *not* padded and stay
     * numbers; `04.6166`, `0100031012` and `007` are.
     *
     * Narrower than [inferredOrNull] on purpose, for parsers that delegate to a library with its
     * own number syntax. YAML is the case: `0x1F`, `0o17`, `1_000` and `1:30` are deliberate YAML
     * spellings and must keep working, so only the zero-padded form is taken back.
     */
    fun isZeroPadded(text: String): Boolean = ZERO_PADDED.matches(text.trim())

    /**
     * B39: should this free **text** be read as a number at all?
     *
     * Distinct from [ofOrNull] on purpose. `ofOrNull` parses a lexeme the grammar has *already*
     * decided is numeric — JSON hands it one. This answers a different question, which CSV and XML
     * have to ask of arbitrary text: *is this meant to be a number?*
     *
     * It says yes only for the canonical JSON number form, so an identifier keeps its leading
     * zeros and stays a string:
     *
     * | text | before | now |
     * |---|---|---|
     * | `04.6166` (store id) | `4.6166` | `"04.6166"` |
     * | `0100031012` (article no.) | `100031012` | `"0100031012"` |
     * | `007` | `7` | `"007"` |
     * | `12`, `1.50`, `0.5`, `-7`, `1e5` | number | number, unchanged |
     *
     * A leading zero was never recoverable afterwards — the digits are gone before the script runs,
     * and `toString()` returns the normalised form — so this cannot be worked around in a
     * transformation. That is what makes it a defect rather than a preference.
     *
     * Note the asymmetry is deliberate: `ofOrNull` must stay lenient because JSON's own lexer has
     * already accepted the token, and refusing it there would turn a parse into an error.
     */
    fun inferredOrNull(text: String): Number? {
        val s = text.trim()
        if (!CANONICAL_JSON_NUMBER.matches(s)) return null
        return ofOrNull(s)
    }

    /**
     * `BigDecimal` for a **plain decimal** lexeme (optional sign, digits, `.`, digits), `null` for
     * anything else — including integers, exponent forms, and YAML's `.inf` / `.nan` / `1_000.5`.
     *
     * Narrower than [ofOrNull] on purpose. It exists for parsers that delegate scalar construction
     * to a third-party library and want to take over only the case where that library would lose
     * precision, leaving every other form on the library's own well-tested path.
     */
    fun plainDecimalOrNull(text: String): BigDecimal? {
        val s = text.trim()
        if (!PLAIN_DECIMAL.matches(s)) return null
        return runCatching { BigDecimal(s) }.getOrNull()
    }

    /**
     * B37 — the [Long] exactly equal to [d], or `null` if there is none.
     *
     * Serialisers print an integral double without its `.0` ("print `1`, not `1.0`"), and three of
     * them tested integrality with `d % 1.0 == 0.0` before calling `toLong()`. That predicate is
     * true for *every* large double, and `Double.toLong()` **saturates** rather than overflowing, so
     * `1e30` and `1e300` both serialised as `9223372036854775807` — a wrong number, and the same
     * wrong number for two values thirty orders of magnitude apart.
     *
     * Round-tripping through `Long` instead is the test that actually holds: a value is safe to
     * print as an integer only if converting it back gives the value you started with. XML already
     * did it this way, which is why XML alone printed `1.0E30` correctly.
     */
    fun integralLongOrNull(d: Double): Long? {
        if (!d.isFinite()) return null
        val asLong = d.toLong()
        return if (asLong.toDouble() == d) asLong else null
    }

    /**
     * Render [n] as written when it is one of the exact types, or `null` when it is a `Double` or
     * `Float` and the caller's own floating-point formatting should apply.
     *
     * The serialisers each had their own "is this integral?" test written against `Double`, and so
     * narrowed *every* `Number` through `toDouble()` before printing it — which corrupted a `Long`
     * or `BigDecimal` on the way out, undoing on output what the parser had just preserved. Handing
     * the exact types back as strings here fixes that without touching those `Double` predicates,
     * which differ between formats and have conformance fixtures behind them.
     *
     * `BigDecimal` renders via `toPlainString()`: plain `toString()` switches to scientific notation
     * once the adjusted exponent drops below -6, so `0.0000001` would otherwise come back as `1E-7`
     * — exact, but not what the author wrote.
     */
    fun renderExactOrNull(n: Number): String? = when (n) {
        is BigDecimal -> n.toPlainString()
        is BigInteger, is Long, is Int, is Short, is Byte -> n.toString()
        else -> null
    }
}
