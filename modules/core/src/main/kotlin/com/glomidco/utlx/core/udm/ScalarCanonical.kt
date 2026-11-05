package com.glomidco.utlx.core.udm

import java.math.BigDecimal

/**
 * B35 — **canonical numeric comparison.**
 *
 * Numeric equality has to be independent of how a value happens to be *represented*. The parsers
 * now preserve what they were given, which means the same quantity can legitimately arrive as a
 * `Long`, a `BigDecimal` with trailing zeros, or a `Double`:
 *
 * ```
 * {"a": 100.10, "b": 100.1, "c": 5, "d": 5.0}
 * ```
 *
 * `a` and `b` are the same number written two ways, and so are `c` and `d`. Raw boxed equality says
 * otherwise — `BigDecimal.equals` is scale-sensitive, and a `Long` never equals a `BigDecimal` —
 * so comparison has to canonicalise first. Without this, preserving precision would silently break
 * `==`, trading one defect for a worse one.
 *
 * Canonicalising also *fixes* comparison above 2⁵³, which was the original complaint:
 * `9007199254740993 == 9007199254740992` is now `false`, where narrowing both to `Double` had made
 * two different integers compare equal.
 *
 * This is the same canonicalisation the validation functions have used since the `oneOf`/`unique`
 * membership fix; that implementation now delegates here so there is one definition of what makes
 * two numbers the same, shared by the operators and by the declarative rule kinds. They used to
 * disagree, which for a component whose verdict is an oracle for downstream enforcement is not a
 * tolerable ambiguity.
 */
object ScalarCanonical {

    /**
     * Canonical form for equality: an exact [BigDecimal] with trailing zeros stripped, so scale and
     * integral-vs-decimal spelling stop mattering.
     *
     * Non-finite doubles (`NaN`, `±Infinity`) keep their boxed form — `BigDecimal` cannot represent
     * them, and their existing equality semantics are preserved by leaving them alone.
     *
     * Non-numbers are returned unchanged, deliberately: the string `"50"` is not the number 50.
     */
    fun canonical(value: Any?): Any? = when (value) {
        null -> null
        is Boolean -> value          // Boolean is not a Number; keep it out of the numeric path
        is BigDecimal -> value.stripTrailingZeros()
        is Double -> if (value.isFinite()) BigDecimal(value.toString()).stripTrailingZeros() else value
        is Float -> if (value.isFinite()) BigDecimal(value.toString()).stripTrailingZeros() else value
        is Number -> BigDecimal(value.toString()).stripTrailingZeros()
        else -> value
    }

    /** Are [a] and [b] the same number, whatever their representations? */
    fun numbersEqual(a: Number, b: Number): Boolean = canonical(a) == canonical(b)

    /**
     * Order [a] against [b], exactly when both are representable as [BigDecimal].
     *
     * Falls back to `Double` only for the non-finite cases, where there is nothing exact to compare
     * and `Double.compareTo` already defines the ordering.
     */
    fun compareNumbers(a: Number, b: Number): Int {
        val ca = canonical(a)
        val cb = canonical(b)
        return if (ca is BigDecimal && cb is BigDecimal) ca.compareTo(cb)
        else a.toDouble().compareTo(b.toDouble())
    }
}
