package com.glomidco.utlx.core.interpreter

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B48/B49 — **a path step that yields many nodes must keep composing.**
 *
 * Root cause: `evaluateMemberAccess` switched on the target's runtime type and handled only
 * `ObjectValue` and `UDMValue`, falling through to `throw RuntimeError` for everything else.
 * The wildcard itself was never the problem — `parser_impl.kt` has parsed `.*`, `.@*` and `.^*`
 * deliberately for a long time, and both branches above evaluated it — but the `ArrayValue` it
 * produced was a dead end. The *next* step threw.
 *
 * That made the most ordinary XML navigation there is fail: `input.r.i.n` over repeated `<i>`
 * elements, which is XPath's `r/i/n`. `docs/language-guide/selectors.md` documented the working
 * behaviour with expected output; it had never worked.
 *
 * Two semantics are pinned here, and the second is the one a careless fix gets wrong:
 *
 *  - **project** — a non-wildcard step distributes over the members and flattens one level,
 *    dropping misses rather than leaving null holes. That is XPath node-set behaviour, and it is
 *    what makes `a.b.c` keep working when `b` repeats.
 *  - **identity** — `*` applied to a SEQUENCE is that sequence, not `*` applied to each member.
 *    The first attempt at this fix projected, so `items[*]` returned every *price* instead of
 *    every item — plausible-looking output for the wrong reason, and `items[*].price` then failed
 *    because it was asking numbers for a `.price`. Applied to an OBJECT, `*` still means "every
 *    value"; that distinction is the whole of `isPlainWildcard`.
 */
class B48SequenceProjectionTest {

    private fun run(body: String, input: UDM): UDM {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        val tokens = Lexer(src).tokenize()
        val program = when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        return Interpreter().execute(program, input).toUDM()
    }

    /** `{"items":[{"price":10},{"price":25},{"price":15}]}` — the example in selectors.md:169-171. */
    private val documented = UDM.Object.of(
        "items" to UDM.Array(
            listOf(
                UDM.Object.of("price" to UDM.Scalar(10L)),
                UDM.Object.of("price" to UDM.Scalar(25L)),
                UDM.Object.of("price" to UDM.Scalar(15L))
            )
        )
    )

    /** An XML-shaped payload: `<r><i><n>a</n></i><i><n>b</n></i></r>` as UDM. */
    private val repeated = UDM.Object.of(
        "r" to UDM.Object.of(
            "i" to UDM.Array(
                listOf(
                    UDM.Object.of("n" to UDM.Scalar("a")),
                    UDM.Object.of("n" to UDM.Scalar("b"))
                )
            )
        )
    )

    private fun scalars(udm: UDM): List<Any?> =
        (udm as UDM.Array).elements.map { (it as UDM.Scalar).value }

    // ── Guard against vacuous assertions ──

    @Test
    fun `the fixtures really hold arrays — otherwise nothing below is projecting`() {
        // If `items` were an object, every assertion here would pass through the old
        // ObjectValue branch and prove nothing about sequences.
        assertTrue(run("\$input.items", documented) is UDM.Array)
        assertTrue(run("\$input.r.i", repeated) is UDM.Array)
    }

    // ── B48: projection ──

    @Test
    fun `a step after a repeated element projects — the XPath r slash i slash n idiom`() {
        assertEquals(listOf<Any?>("a", "b"), scalars(run("\$input.r.i.n", repeated)))
    }

    @Test
    fun `wildcard then a step projects`() {
        assertEquals(listOf<Any?>("a", "b"), scalars(run("\$input.r.*.n", repeated)))
    }

    @Test
    fun `selectors_md line 171 — items dot star dot price`() {
        assertEquals(listOf<Any?>(10L, 25L, 15L), scalars(run("\$input.items.*.price", documented)))
    }

    @Test
    fun `a miss contributes nothing rather than a null hole`() {
        val mixed = UDM.Object.of(
            "xs" to UDM.Array(
                listOf(
                    UDM.Object.of("n" to UDM.Scalar("a")),
                    UDM.Object.of("other" to UDM.Scalar("skip")),
                    UDM.Object.of("n" to UDM.Scalar("b"))
                )
            )
        )
        assertEquals(listOf<Any?>("a", "b"), scalars(run("\$input.xs.n", mixed)))
    }

    @Test
    fun `projection flattens one level so chained steps keep composing`() {
        val nested = UDM.Object.of(
            "g" to UDM.Array(
                listOf(
                    UDM.Object.of("i" to UDM.Array(listOf(UDM.Object.of("n" to UDM.Scalar("a"))))),
                    UDM.Object.of("i" to UDM.Array(listOf(UDM.Object.of("n" to UDM.Scalar("b")))))
                )
            )
        )
        assertEquals(listOf<Any?>("a", "b"), scalars(run("\$input.g.i.n", nested)))
    }

    // ── B49: the bracket spelling ──

    @Test
    fun `selectors_md line 169 — items bracket star is every item, NOT every value of each item`() {
        // The regression this guards: projecting `*` over the members returned
        // [10, 25, 15] here — the prices — which then made line 170 fail.
        val items = run("\$input.items[*]", documented) as UDM.Array
        assertEquals(3, items.elements.size)
        assertTrue(items.elements.all { it is UDM.Object })
    }

    @Test
    fun `selectors_md line 170 — items bracket star dot price`() {
        assertEquals(listOf<Any?>(10L, 25L, 15L), scalars(run("\$input.items[*].price", documented)))
    }

    @Test
    fun `bracket star and dot star agree`() {
        assertEquals(
            scalars(run("\$input.items[*].price", documented)),
            scalars(run("\$input.items.*.price", documented))
        )
    }

    // ── `*` on an object keeps its old meaning ──

    @Test
    fun `star on an OBJECT still means every value`() {
        val obj = UDM.Object.of(
            "x" to UDM.Object.of("name" to UDM.Scalar("a")),
            "y" to UDM.Object.of("name" to UDM.Scalar("b"))
        )
        assertEquals(2, (run("\$input.*", obj) as UDM.Array).elements.size)
        assertEquals(listOf<Any?>("a", "b"), scalars(run("\$input.*.name", obj)))
    }

    // ── things that already worked and must not regress ──

    @Test
    fun `index access is unaffected`() {
        assertEquals(10L, ((run("\$input.items[0].price", documented)) as UDM.Scalar).value)
    }

    @Test
    fun `map over a sequence still works — it was the documented workaround`() {
        assertEquals(
            listOf<Any?>(10L, 25L, 15L),
            scalars(run("map(\$input.items, (t) -> t.price)", documented))
        )
    }
}
