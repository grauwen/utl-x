package com.glomidco.utlx.stdlib.validate

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * VB08 — **the value rule kinds could not read an XML field.**
 *
 * `xml_parser` wraps every text-only element "for consistent structure":
 *
 *     <qty>500</qty>   →   UDM.Object(mapOf("_text" to Scalar(500)))
 *
 * Member access unwraps that, so `$input.order.qty` is a number to the interpreter and `isNumber`
 * agrees. The validators read `properties["qty"]` directly and saw the wrapper, so every rule kind
 * that reads a *value* failed on XML input with a **type** complaint instead of a value one:
 * `inRange` reported "Field 'qty' is not a number" for `<qty>500</qty>`.
 *
 * Two things made this hide for the life of the namespace:
 *
 *  - the **verdict was still invalid**, so a test asserting only `valid` passed either way. What was
 *    wrong was the *message*, and a correct message is the entire value of a named rule.
 *  - **key-based rules were unaffected** — `required`, `onlyFields` and `absent` ask about keys, not
 *    values — so a policy built from those worked perfectly on XML and gave no hint.
 *
 * It contradicted the premise the namespace exists for: one rule set over the UDM, applied to any
 * format, *"because the rules run on UDM, not on the wire format"*. They did run on UDM. They read
 * one shape of it.
 *
 * The fix routes the scalar readers through `UDM.unwrapXmlTextNode()` — `:core`'s existing
 * definition, the same one member access uses — so there is one opinion about the shape, not two.
 */
class VB08XmlFieldReadTest {

    private fun str(s: String) = UDM.Scalar.string(s)
    private fun num(n: Number) = UDM.Scalar.number(n)
    private fun arr(vararg e: UDM) = UDM.Array(e.toList())

    /** Exactly what `xml_parser` builds for a text-only leaf. */
    private fun xmlLeaf(value: UDM) = UDM.Object(mapOf("_text" to value), emptyMap(), "leaf", emptyMap())

    /** An XML-shaped record: every leaf wrapped, as the parser emits it. */
    private val xmlOrder = UDM.Object(
        mapOf(
            "orderId" to xmlLeaf(str("A-1")),
            "qty" to xmlLeaf(num(500)),
            "ccy" to xmlLeaf(str("XXX")),
        ),
        emptyMap(), "order", emptyMap(),
    )

    /** The same record as JSON parses it — unwrapped. This half always worked. */
    private val jsonOrder = UDM.Object.of(
        "orderId" to str("A-1"), "qty" to num(500), "ccy" to str("XXX"))

    private fun valid(r: UDM) = ((r as UDM.Object).properties["valid"] as UDM.Scalar).value as Boolean
    private fun message(r: UDM) = ((r as UDM.Object).properties["issues"] as UDM.Array).elements
        .map { ((it as UDM.Object).properties["message"] as UDM.Scalar).value.toString() }
        .joinToString(" | ")

    // ── the regression: the MESSAGE, not the verdict ──

    @Test
    fun `inRange reads a wrapped number and reports the value, not a type error`() {
        val r = ValidateFunctions.inRange(listOf(xmlOrder, str("qty"), num(1), num(100)))
        assertFalse(valid(r))
        assertTrue(message(r).contains("above maximum"),
            "the value must be READ and compared; got: ${message(r)}")
        assertFalse(message(r).contains("is not a number"),
            "a type complaint means the wrapper was not unwrapped: ${message(r)}")
    }

    @Test
    fun `oneOf reads a wrapped string`() {
        val r = ValidateFunctions.oneOf(listOf(xmlOrder, str("ccy"), arr(str("EUR"), str("USD"))))
        assertFalse(valid(r))
        assertTrue(message(r).contains("'XXX'"), "the value must appear in the message: ${message(r)}")
    }

    @Test
    fun `matches reads a wrapped string and can PASS`() {
        // The strongest direction: a rule that cannot read its field fails. Passing proves a read.
        val r = ValidateFunctions.matches(listOf(xmlOrder, str("orderId"), str("^[A-Z]-[0-9]+$")))
        assertTrue(valid(r), "matches must read through the wrapper: ${message(r)}")
    }

    @Test
    fun `a wrapped value inside range passes`() {
        val ok = UDM.Object(mapOf("qty" to xmlLeaf(num(5))), emptyMap(), "order", emptyMap())
        assertTrue(valid(ValidateFunctions.inRange(listOf(ok, str("qty"), num(1), num(100)))))
    }

    // ── the property that was promised: XML and JSON must agree ──

    @Test
    fun `the same rule gives the same verdict AND the same message on XML and JSON`() {
        // This is the namespace's premise stated as a test. Before the fix the verdicts agreed
        // (both invalid) and the messages did not, which is exactly how the bug stayed invisible.
        for (rule in listOf<(UDM) -> UDM>(
            { p -> ValidateFunctions.inRange(listOf(p, str("qty"), num(1), num(100))) },
            { p -> ValidateFunctions.oneOf(listOf(p, str("ccy"), arr(str("EUR")))) },
            { p -> ValidateFunctions.matches(listOf(p, str("orderId"), str("^[A-Z]-[0-9]+$"))) },
        )) {
            val x = rule(xmlOrder); val j = rule(jsonOrder)
            assertEquals(valid(j), valid(x), "verdict differs between XML and JSON")
            assertEquals(message(j), message(x), "message differs between XML and JSON")
        }
    }

    // ── what was never broken, asserted so the asymmetry is recorded ──

    @Test
    fun `key-based rules were unaffected`() {
        // required/onlyFields/absent ask about keys, so they worked on XML throughout. Pinning it
        // explains why the gap survived: a policy built only from these gives no hint of it.
        assertTrue(valid(ValidateFunctions.required(listOf(xmlOrder, arr(str("orderId"), str("qty"))))))
        assertTrue(valid(ValidateFunctions.onlyFields(
            listOf(xmlOrder, arr(str("orderId"), str("qty"), str("ccy"))))))
        assertFalse(valid(ValidateFunctions.onlyFields(listOf(xmlOrder, arr(str("orderId"))))))
        assertTrue(valid(ValidateFunctions.absent(listOf(xmlOrder, arr(str("trace"))))))
    }

    @Test
    fun `unwrapping only applies to the single-underscore-text shape`() {
        // Vacuity guard on the fix itself: a real object with other properties must NOT be
        // unwrapped into its _text, or a mixed-content element would read as its text alone.
        val mixed = UDM.Object(
            mapOf("_text" to num(5), "unit" to str("kg")), emptyMap(), "qty", emptyMap())
        val r = ValidateFunctions.inRange(listOf(
            UDM.Object(mapOf("qty" to mixed), emptyMap(), "order", emptyMap()),
            str("qty"), num(1), num(100)))
        assertFalse(valid(r), "a mixed-content element is not a bare scalar and must not read as one")
        assertTrue(message(r).contains("is not a number"), message(r))
    }
}
