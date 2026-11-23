package com.glomidco.utlx.stdlib.validate

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.core.semantic.ValidationRuleKinds
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F32 — `validate.onlyFields` and `validate.absent`, the **field allow-list** (category 3 of the
 * guard rule library's seven), promoted into the core set.
 *
 * The behaviour they replace is a host expression:
 * `count(difference(keys($input), allowed)) == 0`. That produces the right *verdict* and **no rule
 * id** — nothing reaches `issues[]`. Since `issues[].rule` is the oracle a hardware verifier's
 * failed-rule bitmask is proved against, a rejection with an empty `issues[]` has nothing to prove
 * silicon against. These two make the rejection a *named rule*.
 *
 * Every negative path here is a **fail-closed** assertion, following VB01/VB04/VB06/VB07: a rule
 * that cannot be constructed or evaluated must report an error, never a silent pass. For an
 * allow-list that matters more than anywhere else — a silently disabled allow-list forwards the
 * unexpected field it exists to stop.
 */
class F32FieldAllowListTest {

    private fun obj(vararg p: Pair<String, UDM>) = UDM.Object.of(*p)
    private fun str(s: String) = UDM.Scalar.string(s)
    private fun num(n: Number) = UDM.Scalar.number(n)
    private fun arr(vararg e: UDM) = UDM.Array(e.toList())
    private fun nul() = UDM.Scalar.nullValue()

    private fun valid(r: UDM) = ((r as UDM.Object).properties["valid"] as UDM.Scalar).value as Boolean
    private fun count(r: UDM, f: String) = (((r as UDM.Object).properties[f] as UDM.Scalar).value as Number).toInt()
    private fun issueFields(r: UDM) = ((r as UDM.Object).properties["issues"] as UDM.Array).elements
        .map { ((it as UDM.Object).properties["field"] as UDM.Scalar).value }
    private fun messages(r: UDM) = ((r as UDM.Object).properties["issues"] as UDM.Array).elements
        .map { ((it as UDM.Object).properties["message"] as UDM.Scalar).value.toString() }

    private val order = obj("orderId" to str("A1"), "qty" to num(5), "ccy" to str("EUR"))
    private val smuggled = obj("orderId" to str("A1"), "qty" to num(5), "ccy" to str("EUR"),
                               "trace" to str("internal-7"), "isAdmin" to UDM.Scalar.boolean(true))

    // ── onlyFields ──

    @Test fun `onlyFields passes when every key is permitted`() {
        val r = ValidateFunctions.onlyFields(listOf(order, arr(str("orderId"), str("qty"), str("ccy"))))
        assertTrue(valid(r)); assertEquals(3, count(r, "passed")); assertEquals(0, count(r, "failed"))
    }

    @Test fun `onlyFields passes when the allow-list is wider than the message`() {
        // An allow-list permits; it does not require. `required` is the rule that demands presence.
        val r = ValidateFunctions.onlyFields(listOf(order, arr(str("orderId"), str("qty"), str("ccy"), str("trace"))))
        assertTrue(valid(r)); assertEquals(3, count(r, "passed"))
    }

    @Test fun `onlyFields names every unexpected field, one issue each`() {
        val r = ValidateFunctions.onlyFields(listOf(smuggled, arr(str("orderId"), str("qty"), str("ccy"))))
        assertFalse(valid(r))
        assertEquals(2, count(r, "failed"))
        assertEquals(listOf<Any?>("trace", "isAdmin"), issueFields(r))
        assertEquals(3, count(r, "passed"))   // 5 keys checked, 2 failed
        assertTrue(messages(r)[1].contains("isAdmin"), "the message must name the offending field")
    }

    @Test fun `onlyFields ignores values entirely`() {
        // A key-set question. A permitted key with a null value is still permitted; an unexpected
        // key with a null value is still unexpected. This is what distinguishes it from `absent`.
        assertTrue(valid(ValidateFunctions.onlyFields(
            listOf(obj("orderId" to nul()), arr(str("orderId"))))))
        assertFalse(valid(ValidateFunctions.onlyFields(
            listOf(obj("isAdmin" to nul()), arr(str("orderId"))))))
    }

    @Test fun `onlyFields on an empty object passes with zero checks`() {
        // Nothing crossed, so nothing was disallowed — the correct verdict for an allow-list, and
        // distinguishable from a silently-disabled rule by `passed` being 0 rather than valid-by-luck.
        val r = ValidateFunctions.onlyFields(listOf(obj(), arr(str("a"), str("b"))))
        assertTrue(valid(r)); assertEquals(0, count(r, "passed")); assertEquals(0, count(r, "failed"))
    }

    // ── onlyFields: fail-closed. The most important tests in the file. ──

    @Test fun `onlyFields on a non-object is a malformed rule, not a pass`() {
        // VB04's failure mode, in the place it would hurt most: `props()` is empty for a scalar or
        // an array, so without the guard a rule aimed at the wrong node runs zero checks and
        // reports valid — silently disabling the rule that stops an unexpected field crossing.
        for (notAnObject in listOf(str("x"), num(1), arr(str("a")), nul())) {
            val r = ValidateFunctions.onlyFields(listOf(notAnObject, arr(str("a"))))
            assertFalse(valid(r), "a non-object payload must FAIL, got valid for $notAnObject")
            assertTrue(messages(r).single().contains("malformed rule"))
        }
    }

    @Test fun `onlyFields with a non-array field list is a malformed rule`() {
        val r = ValidateFunctions.onlyFields(listOf(order, str("orderId")))
        assertFalse(valid(r))
        assertTrue(messages(r).single().contains("fields must be an array"))
    }

    @Test fun `onlyFields with a non-string entry is a malformed rule`() {
        val r = ValidateFunctions.onlyFields(listOf(order, arr(str("orderId"), num(7))))
        assertFalse(valid(r))
        assertTrue(messages(r).single().contains("field-name strings"))
    }

    @Test fun `onlyFields rejects a chained ValidationResult payload (VB06)`() {
        val previous = ValidateFunctions.required(listOf(order, arr(str("orderId"))))
        val r = ValidateFunctions.onlyFields(listOf(previous, arr(str("orderId"))))
        assertFalse(valid(r))
        assertTrue(messages(r).single().contains("must not be chained"))
    }

    @Test fun `onlyFields with the wrong arity is a malformed rule`() {
        assertFalse(valid(ValidateFunctions.onlyFields(listOf(order))))
        assertFalse(valid(ValidateFunctions.onlyFields(listOf(order, arr(str("a")), str("extra")))))
    }

    // ── absent ──

    @Test fun `absent passes when none of the named fields is set`() {
        val r = ValidateFunctions.absent(listOf(order, arr(str("trace"), str("internalRoutingTag"))))
        assertTrue(valid(r)); assertEquals(2, count(r, "passed"))
    }

    @Test fun `absent names each field that is present`() {
        val r = ValidateFunctions.absent(listOf(smuggled, arr(str("trace"), str("isAdmin"), str("nope"))))
        assertFalse(valid(r)); assertEquals(2, count(r, "failed"))
        assertEquals(listOf<Any?>("trace", "isAdmin"), issueFields(r))
        assertEquals(1, count(r, "passed"))
    }

    @Test fun `absent treats a present-but-null field as not set, mirroring required`() {
        // The deliberate symmetry: `{"trace": null}` passes `absent(["trace"])` exactly as it fails
        // `required(["trace"])`. One notion of presence across the namespace matters more than
        // either reading alone. For "this key must not appear at all", use onlyFields — asserted
        // here so the pair's division of labour is pinned, not just documented.
        val withNull = obj("trace" to nul(), "orderId" to str("A1"))
        assertTrue(valid(ValidateFunctions.absent(listOf(withNull, arr(str("trace"))))))
        assertFalse(valid(ValidateFunctions.required(listOf(withNull, arr(str("trace"))))))
        assertFalse(valid(ValidateFunctions.onlyFields(listOf(withNull, arr(str("orderId"))))))
    }

    @Test fun `absent is fail-closed on malformed arguments`() {
        assertFalse(valid(ValidateFunctions.absent(listOf(order, str("trace")))))
        assertFalse(valid(ValidateFunctions.absent(listOf(order, arr(num(1))))))
        assertFalse(valid(ValidateFunctions.absent(listOf(order))))
        val previous = ValidateFunctions.required(listOf(order, arr(str("orderId"))))
        assertFalse(valid(ValidateFunctions.absent(listOf(previous, arr(str("trace"))))))
    }

    // ── both are declarative kinds, so a rule carries an id ──

    @Test fun `both are registered as declarative rule kinds`() {
        // The whole point. A declarative kind is what lets `validate.assert` attach the author's
        // rule id to the failure; a `check` lambda cannot be compiled to a hardware table and a
        // host expression produces no issue at all.
        assertTrue(ValidationRuleKinds.DECLARATIVE.contains("onlyFields"))
        assertTrue(ValidationRuleKinds.DECLARATIVE.contains("absent"))
        assertTrue(ValidationRuleKinds.ALL.contains("onlyFields"))
        assertTrue(ValidationRuleKinds.ALL.contains("absent"))
    }

    @Test fun `assert carries the rule id onto an allow-list failure`() {
        val rules = arr(
            UDM.Object.of("rule" to str("no-unknown-fields"),
                          "onlyFields" to arr(str("orderId"), str("qty"), str("ccy"))),
            UDM.Object.of("rule" to str("no-internal-tags"), "absent" to arr(str("trace"))),
        )
        val r = ValidateFunctions.assert(listOf(smuggled, rules))
        assertFalse(valid(r))
        val ids = ((r as UDM.Object).properties["issues"] as UDM.Array).elements
            .map { ((it as UDM.Object).properties["rule"] as UDM.Scalar).value }
        assertEquals(listOf<Any?>("no-unknown-fields", "no-unknown-fields", "no-internal-tags"), ids)
    }
}
