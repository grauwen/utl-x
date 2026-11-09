package com.glomidco.utlx.core.udm

import java.math.BigDecimal
import java.math.BigInteger

/**
 * B35 Stage 2 — **exact `+`, `-` and `*`.**
 *
 * `19.99 * 100` did not equal `1999`, which is wrong by any business standard and was the reason
 * B35 was filed. Stage 0 made the parsers preserve what they were given and Stage 1 made comparison
 * exact, but arithmetic still narrowed both operands to `Double`, so the moment a mapping *computed*
 * with money the precision was gone again.
 *
 * ### Why only three operators
 *
 * Addition, subtraction and multiplication of two decimals have an exact decimal answer, always.
 * Nothing has to be chosen, nothing is rounded, and no information is discarded:
 *
 * ```
 * 19.99 * 100  = 1999        exactly
 * 0.1 + 0.2    = 0.3         exactly  (so `0.1 + 0.2 > 0.3` is now false)
 * 10.00 - 9.99 = 0.01        exactly
 * ```
 *
 * **Division is different, and is deliberately left approximate.** `10 / 3` is 3.3333… with no
 * terminating decimal form, so exact division cannot proceed without being told how many digits to
 * keep and which way to round the last one. `BigDecimal` refuses to guess and throws instead. Those
 * two settings are not an implementation detail: in invoicing and tax the rounding rule is sometimes
 * prescribed by law and varies by jurisdiction, which makes it a business decision rather than an
 * engineering one. Until it is taken, `/` and `%` keep the behaviour they have always had.
 *
 * The rule for authors is therefore one sentence: **division returns you to approximate
 * arithmetic.** An exact total that is then divided yields an ordinary double — the same answer as
 * before this change, so nothing regresses; it simply stops being exact from that point on.
 *
 * ### When exactness applies
 *
 * Only when **both** operands already carry an exact value — a decimal or integer that came out of a
 * payload or was written as a literal. If either side is a `Double` (an exponent-form literal, or
 * the result of an earlier division) there is nothing exact to preserve, and the operation stays on
 * the `Double` path. That keeps float-style scripts at float speed, since `BigDecimal` arithmetic
 * costs roughly an order of magnitude more than `double`, and it means the cost is paid only by code
 * that actually carries exact values.
 *
 * ### Scale
 *
 * Exactness means the result's scale follows from the inputs': adding keeps the larger scale
 * (`1.50 + 2.5` → `4.00`), and multiplying **adds** them (`19.99 * 1.5` → `29.985`). That is the
 * arithmetically correct answer, and it is why a computed money total now carries its inputs' cents
 * rather than being rounded to look tidy. It also means repeated multiplication grows the number of
 * decimal places linearly, which is correct but worth knowing about in a long reduction.
 *
 * ### Why this lives in `:core`
 *
 * Both execution strategies call it. The interpreter and the compiled path each used to carry their
 * own arithmetic, and EB03 was precisely what happens when two strategies hold their own copy of a
 * semantic rule: they drift, and the divergence is invisible because the conformance suite exercises
 * only one of them. One definition here, called from both.
 */
object ExactArithmetic {

    /**
     * The exact [BigDecimal] behind [n], or `null` when there is none.
     *
     * `Double` and `Float` return null deliberately: a binary float is not an exact decimal, and
     * treating one as though it were would manufacture precision that was never there — turning
     * `0.1` into the 55-digit value a double actually holds.
     */
    fun exactOrNull(n: Number?): BigDecimal? = when (n) {
        null -> null
        is BigDecimal -> n
        is BigInteger -> BigDecimal(n)
        is Long, is Int, is Short, is Byte -> BigDecimal.valueOf(n.toLong())
        else -> null          // Double, Float, and any other Number implementation
    }

    /** Exact sum when both operands are exact, otherwise [fallback]. */
    fun add(left: Number?, right: Number?, fallback: Double): Number =
        apply(left, right, fallback) { a, b -> a.add(b) }

    /** Exact difference when both operands are exact, otherwise [fallback]. */
    fun subtract(left: Number?, right: Number?, fallback: Double): Number =
        apply(left, right, fallback) { a, b -> a.subtract(b) }

    /** Exact product when both operands are exact, otherwise [fallback]. */
    fun multiply(left: Number?, right: Number?, fallback: Double): Number =
        apply(left, right, fallback) { a, b -> a.multiply(b) }

    /**
     * The exact negation of [n], or `null` when there is none.
     *
     * Shared so the interpreter and the compiled path cannot drift: the compiled `negate` used to
     * go through `Double`, so `-10` on an exact integer came back as `-10.0`.
     *
     * `-Long.MIN_VALUE` has no `Long`, so it widens rather than wrapping to itself.
     */
    fun negate(n: Number?): Number? = when (n) {
        is BigDecimal -> n.negate()
        is BigInteger -> n.negate()
        is Long -> if (n != Long.MIN_VALUE) -n else BigInteger.valueOf(n).negate()
        is Int -> -n.toLong()
        is Short -> -n.toLong()
        is Byte -> -n.toLong()
        else -> null
    }

    private inline fun apply(
        left: Number?,
        right: Number?,
        fallback: Double,
        op: (BigDecimal, BigDecimal) -> BigDecimal
    ): Number {
        val a = exactOrNull(left) ?: return fallback
        val b = exactOrNull(right) ?: return fallback
        return op(a, b)
    }
}
