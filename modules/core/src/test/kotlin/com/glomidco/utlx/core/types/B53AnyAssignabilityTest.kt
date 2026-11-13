package com.glomidco.utlx.core.types

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B53 — **`Any` is the checker saying "I don't know", so it must be permissive both ways.**
 *
 * `isAssignableTo` carried only `other is Any -> true`: a value of type `Any` could *receive*
 * anything but could never be *given* to anything. Everything read from `$input` is typed `Any`
 * when there is no schema, so every core signature rejected its argument the moment it came
 * from the document:
 *
 * ```
 * upper("a")        passes --strict
 * upper($input.s)   FAILED --strict      ... and both run, returning "HI"
 * ```
 *
 * 12 of 12 functions holding a core signature behaved that way, and a function *without* one
 * passed — so `groupBy($input.items, …)` validated while `filter($input.items, …)` did not, and
 * adding a signature made validation worse. `--strict` could not go near CI.
 *
 * ### Why the negatives are half the test
 *
 * The fix is `this is Any || other is Any -> true`, and both clauses have to stay. Collapsing
 * them into something that fires whenever either side is `Any`-*shaped* would let
 * `String -> Number` through and turn an over-strict checker into a useless one. Every
 * assertion here therefore has a mirror: what must now pass, and what must still fail.
 *
 * ### What this does not do
 *
 * It removes false alarms; it does not add coverage. Only ~12 functions carry a signature, and
 * `$input` is still not typed from a schema — `--schema` feeds `AdvancedTypeInference` in
 * `:modules:analysis`, never this checker. `--strict` is usable after this, not thorough.
 */
class B53AnyAssignabilityTest {

    // ── the symmetry itself ──

    @Test
    fun `Any is assignable in both directions`() {
        val concrete = listOf(
            UTLXType.String, UTLXType.Number, UTLXType.Boolean,
            UTLXType.Array(UTLXType.Number), UTLXType.Object(mapOf("a" to UTLXType.String))
        )
        for (t in concrete) {
            assertTrue(UTLXType.Any.isAssignableTo(t), "Any should be assignable to $t")
            assertTrue(t.isAssignableTo(UTLXType.Any), "$t should be assignable to Any")
        }
    }

    @Test
    fun `Any matches the symmetry Unknown already had`() {
        // The policy was not invented for B53 — the line below the fix has always been
        // symmetric. `Any` simply never got the same treatment.
        for (t in listOf(UTLXType.String, UTLXType.Number)) {
            assertTrue(UTLXType.Unknown.isAssignableTo(t))
            assertTrue(t.isAssignableTo(UTLXType.Unknown))
            assertTrue(UTLXType.Any.isAssignableTo(t))
            assertTrue(t.isAssignableTo(UTLXType.Any))
        }
    }

    @Test
    fun `the symmetry reaches element types through the recursion`() {
        // `sum` wants Array<Number>; an Array<Any> argument recurses into the element types and
        // asks Any -> Number. One clause covers nesting, which is why the fix is one line.
        assertTrue(
            UTLXType.Array(UTLXType.Any).isAssignableTo(UTLXType.Array(UTLXType.Number)),
            "Array<Any> should satisfy Array<Number>"
        )
        assertTrue(
            UTLXType.Array(UTLXType.Number).isAssignableTo(UTLXType.Array(UTLXType.Any))
        )
    }

    // ── the negatives: what a sloppier fix would have broken ──

    @Test
    fun `a genuine mismatch between concrete types is still rejected`() {
        assertFalse(UTLXType.String.isAssignableTo(UTLXType.Number),
            "String -> Number must stay false; this is the assertion that stops the fix being " +
            "'simplified' into a rule that passes whenever either side is Any-shaped")
        assertFalse(UTLXType.Number.isAssignableTo(UTLXType.String))
        assertFalse(UTLXType.Boolean.isAssignableTo(UTLXType.Number))
    }

    @Test
    fun `a mismatch inside an array is still rejected`() {
        assertFalse(
            UTLXType.Array(UTLXType.String).isAssignableTo(UTLXType.Array(UTLXType.Number))
        )
    }

    @Test
    fun `Null keeps its own rule`() {
        // `Null` sits below the Any clause and is not Any, so ordering does not disturb it.
        assertFalse(UTLXType.Null.isAssignableTo(UTLXType.String))
        assertTrue(UTLXType.Null.isAssignableTo(UTLXType.Null))
        // ...but Null -> Any is fine, since Any accepts anything.
        assertTrue(UTLXType.Null.isAssignableTo(UTLXType.Any))
    }

    @Test
    fun `a union is unaffected`() {
        val stringOrNumber = UTLXType.Union(setOf(UTLXType.String, UTLXType.Number))
        assertTrue(UTLXType.String.isAssignableTo(stringOrNumber))
        assertFalse(UTLXType.Boolean.isAssignableTo(stringOrNumber))
        assertTrue(UTLXType.Any.isAssignableTo(stringOrNumber))
    }
}
