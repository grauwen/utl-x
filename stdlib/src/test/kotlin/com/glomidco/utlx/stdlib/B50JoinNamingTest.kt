package com.glomidco.utlx.stdlib

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * B50 — **`join` is the string join; the relational one is `innerJoin`.**
 *
 * This deviates from DataWeave on purpose, and the reason is UTL-X's own `*By` convention:
 * of the 14 registered `*By` functions, **13 take a criteria/key/mapping function** over an
 * array — `sortBy`, `groupBy`, `distinctBy`, `maxBy`, `minBy`, `sumBy`, `avgBy`, `countBy`,
 * `chunkBy`, `divideBy`, `lookupBy`, `nestBy`. A `joinBy` whose second argument is a plain
 * delimiter is the lone exception and reads as if it took a key function.
 *
 * The codebase had already made the same choice elsewhere: the string split is `split`, not
 * DataWeave's `splitBy`. So `split`/`join` is the plain string pair and `*By` stays reserved
 * for array-with-criteria. DataWeave's own convention is weaker — its `splitBy` and `joinBy`
 * both take plain separators — so matching it here would import an inconsistency.
 *
 * `innerJoin` also completes its family: it sits beside `leftJoin`, `rightJoin`,
 * `fullOuterJoin` and `crossJoin`, and was the only one of the five not named after its join
 * type.
 *
 * `joinBy` is retained as a legacy alias of `join`: it was the registered name for the string
 * join from 2025-10-16 to 2026-11-12, `FunctionsTest` asserts it, and it is also what a
 * DataWeave author would reach for.
 */
class B50JoinNamingTest {

    private fun call(name: String, vararg args: UDM): UDM {
        val fn = StandardLibrary.getFunction(name)
        assertNotNull(fn, "function '$name' is not registered")
        return fn.implementation(args.toList())
    }

    private fun arr(vararg s: String) = UDM.Array(s.map { UDM.Scalar(it) })

    // ── guard against a vacuous harness ──

    @Test
    fun `the registry really resolves these names`() {
        for (n in listOf("join", "joinBy", "innerJoin", "leftJoin", "nestBy", "split")) {
            assertNotNull(StandardLibrary.getFunction(n), "'$n' must be registered")
        }
    }

    // ── join is the STRING join ──

    @Test
    fun `join joins an array with a delimiter`() {
        assertEquals("x,y,z", (call("join", arr("x", "y", "z"), UDM.Scalar(",")) as UDM.Scalar).value)
        assertEquals("a-b", (call("join", arr("a", "b"), UDM.Scalar("-")) as UDM.Scalar).value)
    }

    @Test
    fun `join takes two arguments — a four-argument relational call is rejected`() {
        val fn = StandardLibrary.getFunction("join")!!
        val r = runCatching {
            fn.implementation(listOf(arr("a"), arr("b"), UDM.Scalar("k"), UDM.Scalar("k")))
        }
        assertTrue(r.isFailure, "join is the string join; a 4-arg call must be rejected")
    }

    @Test
    fun `joinBy is retained as a legacy alias and agrees with join`() {
        assertEquals(
            (call("join", arr("x", "y"), UDM.Scalar(",")) as UDM.Scalar).value,
            (call("joinBy", arr("x", "y"), UDM.Scalar(",")) as UDM.Scalar).value
        )
    }

    // ── innerJoin is the RELATIONAL join ──

    @Test
    fun `innerJoin is relational and rejects a two-argument string-join call`() {
        val fn = StandardLibrary.getFunction("innerJoin")!!
        val r = runCatching { fn.implementation(listOf(arr("a", "b"), UDM.Scalar(","))) }
        assertTrue(r.isFailure, "innerJoin is relational; a 2-arg call must be rejected")
    }

    @Test
    fun `the relational family is complete and consistently named`() {
        for (n in listOf("innerJoin", "leftJoin", "rightJoin", "fullOuterJoin", "crossJoin")) {
            assertNotNull(StandardLibrary.getFunction(n), "'$n' must be registered")
        }
    }

    // ── the convention this naming rests on ──

    @Test
    fun `the star-By convention holds — split is split, not splitBy`() {
        // If a future change introduces `splitBy` as the string split, the premise behind
        // naming the string join `join` has been abandoned and this should be revisited.
        assertNotNull(StandardLibrary.getFunction("split"), "the string split must be `split`")
        assertTrue(StandardLibrary.getFunction("splitBy") == null,
            "`splitBy` must not be the string split — `*By` is reserved for array-with-criteria")
    }

    // ── nestBy: a third, distinct operation, unaffected ──

    @Test
    fun `nestBy is still registered — F03's chosen name, untouched`() {
        // nestBy produces HIERARCHY; innerJoin produces ROWS. Behaviour is covered by the
        // conformance fixture, since nestBy takes lambdas only the interpreter constructs.
        assertNotNull(StandardLibrary.getFunction("nestBy"), "nestBy must stay registered")
    }

    // ── catalogue/dispatch alignment, which was the original defect ──

    @Test
    fun `the Kotlin declaration names match the registered names`() {
        // The generated catalogue keys on the Kotlin function name, so a mismatch files the
        // entry under the wrong function (B50). Calling both directly is the assertion: a
        // rename on either side would stop this compiling.
        val sj = com.glomidco.utlx.stdlib.string.StringFunctions.join(
            listOf(arr("p", "q"), UDM.Scalar("|")))
        assertEquals("p|q", (sj as UDM.Scalar).value)
        val rj = runCatching {
            com.glomidco.utlx.stdlib.array.JoinFunctions.innerJoin(listOf(arr("a"), UDM.Scalar(",")))
        }
        assertTrue(rj.isFailure, "JoinFunctions.innerJoin must exist and be relational")
    }
}
