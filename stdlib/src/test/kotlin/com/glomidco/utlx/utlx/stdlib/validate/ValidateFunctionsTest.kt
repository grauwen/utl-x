package com.glomidco.utlx.stdlib.validate

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * F21 Tier-1 `validate.*` shortcuts + the `ValidationResult` shape (spec §5–§6).
 * Pure unit tests — no interpreter; end-to-end script resolution is covered elsewhere.
 */
class ValidateFunctionsTest {

    private fun obj(vararg p: Pair<String, UDM>) = UDM.Object.of(*p)
    private fun str(s: String) = UDM.Scalar.string(s)
    private fun num(n: Number) = UDM.Scalar.number(n)
    private fun arr(vararg e: UDM) = UDM.Array(e.toList())

    private fun valid(r: UDM) = ((r as UDM.Object).properties["valid"] as UDM.Scalar).value as Boolean
    private fun count(r: UDM, f: String) = (((r as UDM.Object).properties[f] as UDM.Scalar).value as Number).toInt()
    private fun issueFields(r: UDM) = ((r as UDM.Object).properties["issues"] as UDM.Array).elements
        .map { ((it as UDM.Object).properties["field"] as UDM.Scalar).value }
    private fun severities(r: UDM) = ((r as UDM.Object).properties["issues"] as UDM.Array).elements
        .map { ((it as UDM.Object).properties["severity"] as UDM.Scalar).value }

    private val order = obj("orderId" to num(42), "currency" to str("EUR"), "unitPrice" to num(10.0))

    @Test fun `required passes when all fields present`() {
        val r = ValidateFunctions.required(listOf(order, arr(str("orderId"), str("currency"))))
        assertTrue(valid(r)); assertEquals(2, count(r, "passed")); assertEquals(0, count(r, "failed"))
    }

    @Test fun `required fails and names the missing field at error severity`() {
        val r = ValidateFunctions.required(listOf(order, arr(str("orderId"), str("lineItems"))))
        assertFalse(valid(r)); assertEquals(1, count(r, "failed"))
        assertEquals(listOf<Any?>("lineItems"), issueFields(r))
        assertEquals(listOf<Any?>("error"), severities(r))
    }

    @Test fun `inRange passes within bounds and at the boundary`() {
        assertTrue(valid(ValidateFunctions.inRange(listOf(order, str("unitPrice"), num(0.01), num(999999.99)))))
        assertTrue(valid(ValidateFunctions.inRange(listOf(order, str("unitPrice"), num(10.0), num(20.0)))))  // == min
        assertTrue(valid(ValidateFunctions.inRange(listOf(order, str("unitPrice"), num(0.0), num(10.0)))))   // == max
    }

    @Test fun `inRange fails below minimum`() {
        val r = ValidateFunctions.inRange(listOf(order, str("unitPrice"), num(100.0), num(200.0)))
        assertFalse(valid(r)); assertEquals(1, count(r, "failed"))
    }

    @Test fun `oneOf passes when allowed, fails when not`() {
        assertTrue(valid(ValidateFunctions.oneOf(listOf(order, str("currency"), arr(str("EUR"), str("USD"))))))
        assertFalse(valid(ValidateFunctions.oneOf(listOf(order, str("currency"), arr(str("USD"), str("GBP"))))))
    }

    @Test fun `result carries payload through and zeroes warnings and notices`() {
        val r = ValidateFunctions.required(listOf(order, arr(str("orderId")))) as UDM.Object
        assertEquals(order, r.properties["payload"])
        assertEquals(0, count(r, "warnings")); assertEquals(0, count(r, "notices"))
    }

    @Test fun `matches passes and fails on a regex`() {
        val p = obj("iban" to str("DE44500105175407324931"))
        assertTrue(valid(ValidateFunctions.matches(listOf(p, str("iban"), str("^[A-Z]{2}[0-9]{2}.*")))))
        assertFalse(valid(ValidateFunctions.matches(listOf(p, str("iban"), str("^[0-9]+\$")))))
    }

    @Test fun `isoDate validates against a format`() {
        val p = obj("d" to str("2026-10-13"))
        assertTrue(valid(ValidateFunctions.isoDate(listOf(p, str("d"), str("yyyy-MM-dd")))))
        assertFalse(valid(ValidateFunctions.isoDate(listOf(p, str("d"), str("yyyyMMdd")))))   // wrong format
    }

    @Test fun `unique detects duplicate key values in an array column`() {
        fun line(id: Int) = obj("lineId" to num(id))
        assertTrue(valid(ValidateFunctions.unique(listOf(obj("lines" to arr(line(1), line(2), line(3))), str("lines"), str("lineId")))))
        assertFalse(valid(ValidateFunctions.unique(listOf(obj("lines" to arr(line(1), line(2), line(1))), str("lines"), str("lineId")))))
    }

    @Test fun `mutuallyExclusive allows at most one set`() {
        val fields = arr(str("a"), str("b"))
        assertTrue(valid(ValidateFunctions.mutuallyExclusive(listOf(obj("a" to num(1)), fields))))                        // one
        assertTrue(valid(ValidateFunctions.mutuallyExclusive(listOf(obj(), fields))))                                      // none
        assertFalse(valid(ValidateFunctions.mutuallyExclusive(listOf(obj("a" to num(1), "b" to num(2)), fields))))         // two
    }

    @Test fun `atLeastOne requires one set`() {
        val fields = arr(str("email"), str("phone"))
        assertTrue(valid(ValidateFunctions.atLeastOne(listOf(obj("phone" to str("x")), fields))))
        assertFalse(valid(ValidateFunctions.atLeastOne(listOf(obj(), fields))))
    }

    // assert: the canonical form. `check`/`when` arrive as pre-evaluated booleans (the interpreter
    // evaluates the rule-list expressions before calling the function).
    private fun rule(id: String, check: Boolean, sev: String = "error", whenV: Boolean? = null): UDM {
        val pairs = mutableListOf(
            "rule" to str(id), "check" to UDM.Scalar.boolean(check),
            "message" to str("$id failed"), "severity" to str(sev),
        )
        if (whenV != null) pairs.add("when" to UDM.Scalar.boolean(whenV))
        return UDM.Object.of(*pairs.toTypedArray())
    }

    @Test fun `assert aggregates rules, honours severity and the when-guard`() {
        val r = ValidateFunctions.assert(listOf(order, arr(
            rule("a", check = true),                       // pass
            rule("b", check = false),                      // fail — error → flips valid
            rule("c", check = false, sev = "warning"),     // fail — warning → does NOT flip valid
            rule("d", check = false, whenV = false),       // skipped (when=false) → counts as passed
        )))
        assertFalse(valid(r))                 // one error-severity failure
        assertEquals(1, count(r, "failed"))   // b
        assertEquals(1, count(r, "warnings")) // c
        assertEquals(2, count(r, "passed"))   // a + d(skipped)
    }
}
