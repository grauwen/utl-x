package com.glomidco.utlx.core.interpreter

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * B57 — **a member that cannot answer the step contributes nothing, whatever the reason.**
 *
 * [B48SequenceProjectionTest] established that a step distributes over a sequence and *drops
 * misses*: an object simply lacking the key adds nothing. It left one kind of member fatal —
 * a **scalar**, which threw `Cannot access property on non-object UDM type` before it could be
 * dropped. So the two halves of one rule disagreed:
 *
 * ```
 * {"a":{"name":"A"}, "b":{"other":1}}                   .*.name  ->  ["A"]     dropped
 * {"a":{"name":"A"}, "b":{"name":"B"}, "title":"ROOT"}  .*.name  ->  THREW
 * ```
 *
 * The failing shape is not exotic — a container holding child records beside a scalar label is
 * everyday JSON, and it is precisely what XML mixed content looks like
 * (`<order><title>ACME</title><line>…</line><line>…</line></order>`). Projection was fragile in
 * exactly the documents it was built for.
 *
 * ### The split this pins
 *
 * Skipping is scoped to **projection**. Direct access still throws:
 *
 *  - `$input.*.name` asks "the name of each of these" — a member with no name is simply not an
 *    answer, and the document's shape is not the author's mistake.
 *  - `$input.title.name` asks one named scalar for a property, which cannot ever succeed and is
 *    a mistake in the transformation.
 *
 * Loud where the author is wrong, quiet where the data is heterogeneous. A fix that silenced
 * both would have hidden real errors, which is why the second half is tested here too.
 *
 * ### It also removed a strategy divergence
 *
 * `RuntimeOps.getProperty` on the compiled path returns `UDM.Scalar.nullValue()` for a
 * non-object target, so COMPILED and COPY were *already* skipping scalars while TEMPLATE threw.
 * Its comment claimed it was "matching the interpreter's projectOverSequence"; it was not. The
 * interpreter was the outlier, and this aligns it.
 */
class B57MixedSequenceProjectionTest {

    private fun run(body: String, input: UDM): UDM {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        val program = when (val r = Parser(Lexer(src).tokenize(), src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        return Interpreter().execute(program, input).toUDM()
    }

    private fun values(result: UDM): List<Any?> =
        ((result as UDM.Object).properties["r"] as UDM.Array).elements.map { (it as UDM.Scalar).value }

    /** Child records beside a scalar label — the shape that used to throw. */
    private val mixed = UDM.Object.of(
        "a" to UDM.Object.of("name" to UDM.Scalar("A")),
        "b" to UDM.Object.of("name" to UDM.Scalar("B")),
        "title" to UDM.Scalar("ROOT")
    )

    @Test
    fun `a scalar member is skipped, not fatal`() {
        assertEquals(listOf<Any?>("A", "B"), values(run("{ r: \$input.*.name }", mixed)))
    }

    @Test
    fun `a missing key is still skipped — B48's half of the rule`() {
        val someMissing = UDM.Object.of(
            "a" to UDM.Object.of("name" to UDM.Scalar("A")),
            "b" to UDM.Object.of("other" to UDM.Scalar(1L))
        )
        assertEquals(listOf<Any?>("A"), values(run("{ r: \$input.*.name }", someMissing)))
    }

    @Test
    fun `both reasons skip in the same expression`() {
        val both = UDM.Object.of(
            "a" to UDM.Object.of("name" to UDM.Scalar("A")),
            "b" to UDM.Object.of("other" to UDM.Scalar(1L)),
            "title" to UDM.Scalar("ROOT")
        )
        assertEquals(listOf<Any?>("A"), values(run("{ r: \$input.*.name }", both)))
    }

    @Test
    fun `every member being a scalar yields an empty sequence, not an error`() {
        val allScalars = UDM.Object.of("x" to UDM.Scalar(1L), "y" to UDM.Scalar(2L))
        assertEquals(emptyList<Any?>(), values(run("{ r: \$input.*.name }", allScalars)))
    }

    @Test
    fun `XML mixed content projects over the element children`() {
        // <order><title>ACME</title><line><sku>A1</sku></line><line><sku>B2</sku></line></order>
        val order = UDM.Object.of(
            "order" to UDM.Object.of(
                "title" to UDM.Scalar("ACME"),
                "line" to UDM.Array(listOf(
                    UDM.Object.of("sku" to UDM.Scalar("A1")),
                    UDM.Object.of("sku" to UDM.Scalar("B2"))
                ))
            )
        )
        assertEquals(listOf<Any?>("A1", "B2"), values(run("{ r: \$input.order.*.sku }", order)))
    }

    // ── the half that must stay loud ──

    @Test
    fun `direct access on a scalar still throws`() {
        // Not projection: one named scalar asked for a property it can never have.
        assertFailsWith<RuntimeError> { run("{ r: \$input.title.name }", mixed) }
    }

    @Test
    fun `direct access on a scalar throws even when a sibling would have matched`() {
        // Guards against a fix that routes everything through the projecting path.
        assertFailsWith<RuntimeError> { run("{ r: \$input.title.name.deeper }", mixed) }
    }

    // ── B48's cases must be untouched ──

    @Test
    fun `projection over homogeneous objects is unchanged`() {
        val pure = UDM.Object.of(
            "a" to UDM.Object.of("name" to UDM.Scalar("A")),
            "b" to UDM.Object.of("name" to UDM.Scalar("B"))
        )
        assertEquals(listOf<Any?>("A", "B"), values(run("{ r: \$input.*.name }", pure)))
    }

    @Test
    fun `a plain wildcard over a sequence is still the identity`() {
        // isPlainWildcard: `*` applied to a SEQUENCE is that sequence, not `*` over each member.
        val doc = UDM.Object.of("items" to UDM.Array(listOf(
            UDM.Object.of("price" to UDM.Scalar(10L)),
            UDM.Object.of("price" to UDM.Scalar(25L))
        )))
        val r = (run("{ r: \$input.items[*] }", doc) as UDM.Object).properties["r"] as UDM.Array
        assertEquals(2, r.elements.size)
        assertTrue(r.elements.all { it is UDM.Object })
    }
}
