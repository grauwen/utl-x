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

    /**
     * Exact product when both operands are exact, otherwise [fallback].
     *
     * The scale of the result is **normalised** — see [normaliseProduct].
     */
    fun multiply(left: Number?, right: Number?, fallback: Double): Number =
        apply(left, right, fallback) { a, b ->
            normaliseProduct(a.multiply(b), maxOf(a.scale(), b.scale()))
        }

    /**
     * B46: trim a product's trailing zeros, but never below the widest operand's scale.
     *
     * `BigDecimal` (and ANSI SQL, and IEEE 754-2008 decimal) define a product's scale as the **sum**
     * of the operand scales. That is correct arithmetic and it is how the digits fall out of long
     * multiplication — but for multiplication, unlike addition, it manufactures decimal places that
     * no operand had:
     *
     * ```
     * 1.5  * 1.08  ->  1.620     the 3rd place is an artifact; 1.620 IS 1.62
     * 0.75 * 1.08  ->  0.8100    four places claimed for a value worth 81 cents
     * 1.50 * 1.50  ->  2.2500
     * ```
     *
     * Addition cannot do this: `max(s1, s2)` never exceeds an operand's own scale, so `1.50 + 1.50
     * = 3.00` is genuinely informative — both inputs were two-decimal money. A product's extra
     * zeros are not information, and presenting them as precision is misleading in exactly the
     * domain UTL-X serves.
     *
     * So the trailing zeros come off, with a floor at the widest operand's scale, which keeps every
     * zero that *was* in the data:
     *
     * ```
     * 1.5  * 1.08   ->  1.62        stripped to 2 (floor 2)
     * 0.75 * 1.08   ->  0.81        stripped to 2 (floor 2)
     * 1.50 * 3      ->  4.50        floor 2 holds the money formatting
     * 1.5  * 2      ->  3.0         floor 1 holds it
     * 0.001*0.001   ->  0.000001    6 places are NEEDED, so the floor does not cap it
     * 1000 * 2      ->  2000        stripTrailingZeros gives 2E+3; the floor restores plain form
     * ```
     *
     * This is a **presentation** rule over exact arithmetic: the value is unchanged, only how many
     * places it reports. It is deliberately NOT what a database would print — a `DECIMAL(10,4)`
     * column has a declared type to honour, and a transformation output does not.
     *
     * Applied here rather than in a serialiser for two reasons: the operand scales are only known
     * at this point, and both the interpreter and `RuntimeOps` (the compiled path) call this
     * function — so they cannot drift apart, which is the failure mode EB03-EB16 were all about.
     */
    private fun normaliseProduct(product: BigDecimal, floor: Int): BigDecimal {
        val stripped = product.stripTrailingZeros()
        // stripTrailingZeros can hand back a NEGATIVE scale (1000 -> 1E+3); the floor is never
        // negative, so raising to it also restores plain form.
        return if (stripped.scale() < floor) stripped.setScale(floor) else stripped
    }

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
