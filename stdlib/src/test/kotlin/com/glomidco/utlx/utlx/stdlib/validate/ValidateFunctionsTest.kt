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

    // ── VB03: numeric membership / key identity must not depend on the numeric box ──

    @Test fun `VB03 oneOf matches an Int payload against Double list entries`() {
        // JSON `50` parses to an Int; the script literal `[50, 60]` is evaluated as Doubles.
        // `Any?.equals` is type-sensitive, so this reported invalid before VB03.
        val p = obj("price" to UDM.Scalar(50))
        assertTrue(valid(ValidateFunctions.oneOf(listOf(p, str("price"), arr(num(50.0), num(60.0))))))
    }

    @Test fun `VB03 oneOf matches a Double payload against Int list entries`() {
        val p = obj("price" to UDM.Scalar(50.0))
        assertTrue(valid(ValidateFunctions.oneOf(listOf(p, str("price"), arr(UDM.Scalar(50), UDM.Scalar(60))))))
    }

    @Test fun `VB03 oneOf is insensitive to trailing-zero scale`() {
        val p = obj("price" to UDM.Scalar(java.math.BigDecimal("50.00")))
        assertTrue(valid(ValidateFunctions.oneOf(listOf(p, str("price"), arr(num(50))))))
    }

    @Test fun `VB03 oneOf still rejects a value outside the list`() {
        val p = obj("price" to UDM.Scalar(55))
        assertFalse(valid(ValidateFunctions.oneOf(listOf(p, str("price"), arr(num(50.0), num(60.0))))))
    }

    @Test fun `VB03 oneOf does not coerce a string into a number`() {
        // Deliberate: oneOf is a controlled-value check, not a coercion. "50" is not 50.
        val p = obj("price" to str("50"))
        assertFalse(valid(ValidateFunctions.oneOf(listOf(p, str("price"), arr(num(50.0), num(60.0))))))
    }

    @Test fun `VB03 oneOf keeps booleans out of the numeric path`() {
        val p = obj("flag" to UDM.Scalar.boolean(true))
        assertTrue(valid(ValidateFunctions.oneOf(listOf(p, str("flag"), arr(UDM.Scalar.boolean(true))))))
        assertFalse(valid(ValidateFunctions.oneOf(listOf(p, str("flag"), arr(num(1))))))
    }

    @Test fun `VB03 oneOf message renders numbers without a trailing point-zero`() {
        val p = obj("price" to UDM.Scalar(55))
        val r = ValidateFunctions.oneOf(listOf(p, str("price"), arr(num(50.0), num(60.0))))
        val msg = ((((r as UDM.Object).properties["issues"] as UDM.Array).elements[0] as UDM.Object)
            .properties["message"] as UDM.Scalar).value as String
        assertEquals("Field 'price' value '55' is not one of [50, 60]", msg)
    }

    @Test fun `VB03 unique treats the same number in different boxes as one key`() {
        val rows = arr(
            obj("lineId" to UDM.Scalar(7)),
            obj("lineId" to UDM.Scalar(7.0)),
        )
        val p = obj("orderLines" to rows)
        assertFalse(
            valid(ValidateFunctions.unique(listOf(p, str("orderLines"), str("lineId")))),
            "Int 7 and Double 7.0 are the same key — this must be reported as a duplicate",
        )
    }

    @Test fun `VB03 unique still passes on genuinely distinct keys`() {
        val rows = arr(obj("lineId" to UDM.Scalar(7)), obj("lineId" to UDM.Scalar(8.0)))
        assertTrue(valid(ValidateFunctions.unique(listOf(obj("orderLines" to rows), str("orderLines"), str("lineId")))))
    }

    // ── VB04: a rule that cannot be CONSTRUCTED is an error, never a silent pass ──

    private fun messages(r: UDM) = ((r as UDM.Object).properties["issues"] as UDM.Array).elements
        .map { ((it as UDM.Object).properties["message"] as UDM.Scalar).value as String }

    /** Every malformed rule must be invalid, at error severity, counted as failed. */
    private fun assertMalformed(r: UDM, expectedFragment: String) {
        assertFalse(valid(r), "a malformed rule must not report valid: ${messages(r)}")
        assertEquals(1, count(r, "failed"), "a malformed rule must count as failed: ${messages(r)}")
        assertEquals(0, count(r, "passed"), "a malformed rule must not count a passed check")
        assertEquals(listOf<Any?>("error"), severities(r))
        val msg = messages(r).single()
        assertTrue(msg.contains("malformed rule"), "message should name the rule as malformed: $msg")
        assertTrue(msg.contains(expectedFragment), "expected '$expectedFragment' in: $msg")
    }

    // The four that used to report valid:true — the fail-open paths.

    @Test fun `VB04 required with a non-array fields argument is an error, not a silent pass`() {
        // `validate.required("price")` — brackets forgotten. Used to run 0 checks and pass.
        assertMalformed(ValidateFunctions.required(listOf(order, str("price"))), "fields must be an array")
    }

    @Test fun `VB04 inRange with too few arguments is an error, not a silent pass`() {
        // max omitted. Used to skip the max comparison and count a passed check.
        assertMalformed(ValidateFunctions.inRange(listOf(order, str("unitPrice"), num(0))), "expected 4 arguments")
    }

    @Test fun `VB04 inRange with a non-numeric bound is an error, not a silent pass`() {
        assertMalformed(
            ValidateFunctions.inRange(listOf(order, str("unitPrice"), str("abc"), num(100))),
            "min must be a number",
        )
    }

    @Test fun `VB04 unique on a field that is not an array is an error, not a silent pass`() {
        // Used to find zero keys, hence no duplicates, hence valid:true — nothing was checked.
        val p = obj("orderLines" to num(7))
        assertMalformed(
            ValidateFunctions.unique(listOf(p, str("orderLines"), str("lineId"))),
            "is not an array",
        )
    }

    @Test fun `VB04 mutuallyExclusive with a non-array fields argument is an error`() {
        assertMalformed(ValidateFunctions.mutuallyExclusive(listOf(order, str("a"))), "fields must be an array")
    }

    // These were already fail-closed, but blamed the payload for a broken rule.

    @Test fun `VB04 matches with an unparseable regex blames the rule, not the data`() {
        val r = ValidateFunctions.matches(listOf(order, str("currency"), str("[")))
        assertMalformed(r, "not a valid regular expression")
    }

    @Test fun `VB04 isoDate with an unusable format blames the rule, not the data`() {
        val p = obj("d" to str("2024-01-15"))
        assertMalformed(ValidateFunctions.isoDate(listOf(p, str("d"), str("'"))), "not a valid date pattern")
    }

    @Test fun `VB04 oneOf with a non-array values argument is an error`() {
        assertMalformed(ValidateFunctions.oneOf(listOf(order, str("currency"), str("EUR"))), "values must be an array")
    }

    @Test fun `VB04 atLeastOne with a non-array fields argument is an error`() {
        assertMalformed(ValidateFunctions.atLeastOne(listOf(order, str("email"))), "fields must be an array")
    }

    @Test fun `VB04 required rejects a non-string entry in the fields array`() {
        // Previously mapNotNull dropped it silently, so the rule checked fewer fields than written.
        assertMalformed(
            ValidateFunctions.required(listOf(order, arr(str("orderId"), obj("nested" to num(1))))),
            "must be a string",
        )
    }

    @Test fun `VB04 a valid date pattern still blames the data when the value does not match`() {
        // Guard against over-reach: "QQQQ" IS a legal DateTimeFormatter pattern (quarter-of-year),
        // so a value that fails to parse against it is a DATA problem, not a malformed rule.
        val p = obj("d" to str("2024-01-15"))
        val r = ValidateFunctions.isoDate(listOf(p, str("d"), str("QQQQ")))
        assertFalse(valid(r))
        assertFalse(messages(r).single().contains("malformed"), "should blame the data: ${messages(r)}")
    }

    @Test fun `VB04 well-formed rules are completely unaffected`() {
        assertTrue(valid(ValidateFunctions.required(listOf(order, arr(str("orderId"), str("currency"))))))
        assertTrue(valid(ValidateFunctions.inRange(listOf(order, str("unitPrice"), num(0), num(100)))))
        assertTrue(valid(ValidateFunctions.oneOf(listOf(order, str("currency"), arr(str("EUR"), str("USD"))))))
        assertTrue(valid(ValidateFunctions.matches(listOf(order, str("currency"), str("^[A-Z]{3}$")))))
        assertTrue(valid(ValidateFunctions.atLeastOne(listOf(order, arr(str("orderId"))))))
        assertTrue(valid(ValidateFunctions.mutuallyExclusive(listOf(order, arr(str("orderId"))))))
    }

    // ── VB06: a validator must refuse a ValidationResult as its payload (chaining) ──

    /** A ValidationResult, as produced by a first validator in a chain. */
    private fun aResult() = ValidateFunctions.required(listOf(order, arr(str("missingField"))))

    @Test fun `VB06 the fixture result really is a failing ValidationResult`() {
        // Guards the tests below from passing vacuously on a malformed fixture.
        val r = aResult()
        assertFalse(valid(r))
        assertEquals(1, count(r, "failed"))
    }

    @Test fun `VB06 chaining into required is rejected instead of inspecting the result`() {
        // The fail-open shape: `valid` and `issues` ARE present on a ValidationResult, so this
        // chain used to report valid:true, burying the real failure under `payload`.
        val r = ValidateFunctions.required(listOf(aResult(), arr(str("valid"), str("issues"))))
        assertMalformed(r, "must not be chained")
    }

    @Test fun `VB06 chaining into inRange is rejected`() {
        assertMalformed(
            ValidateFunctions.inRange(listOf(aResult(), str("failed"), num(0), num(99))),
            "must not be chained",
        )
    }

    @Test fun `VB06 chaining into oneOf is rejected`() {
        assertMalformed(
            ValidateFunctions.oneOf(listOf(aResult(), str("valid"), arr(UDM.Scalar.boolean(false)))),
            "must not be chained",
        )
    }

    @Test fun `VB06 chaining into assert is rejected`() {
        val rules = arr(UDM.Object.of("rule" to str("r"), "check" to UDM.Scalar.boolean(true), "message" to str("m")))
        assertMalformed(ValidateFunctions.assert(listOf(aResult(), rules)), "must not be chained")
    }

    @Test fun `VB06 chaining into unique, matches, isoDate, mutuallyExclusive and atLeastOne is rejected`() {
        val res = aResult()
        assertMalformed(ValidateFunctions.unique(listOf(res, str("issues"), str("field"))), "must not be chained")
        assertMalformed(ValidateFunctions.matches(listOf(res, str("valid"), str(".*"))), "must not be chained")
        assertMalformed(ValidateFunctions.isoDate(listOf(res, str("valid"), str("yyyy"))), "must not be chained")
        assertMalformed(ValidateFunctions.mutuallyExclusive(listOf(res, arr(str("valid")))), "must not be chained")
        assertMalformed(ValidateFunctions.atLeastOne(listOf(res, arr(str("valid")))), "must not be chained")
    }

    @Test fun `VB06 an ordinary payload that merely has a valid field is NOT mistaken for a result`() {
        // Over-reach guard: the shape test needs all three verdict fields. A business message with
        // its own `valid` flag must still validate normally.
        val msg = obj("valid" to UDM.Scalar.boolean(true), "orderId" to num(42))
        val r = ValidateFunctions.required(listOf(msg, arr(str("orderId"))))
        assertTrue(valid(r), "a message with a `valid` field must not be treated as a ValidationResult")
    }

    @Test fun `VB06 the correct composition is unaffected`() {
        // Each validator takes the MESSAGE; results are merged by the caller.
        val a = ValidateFunctions.required(listOf(order, arr(str("orderId"))))
        val b = ValidateFunctions.inRange(listOf(order, str("unitPrice"), num(0), num(100)))
        assertTrue(valid(a) && valid(b))
        assertEquals(0, count(a, "failed") + count(b, "failed"))
    }
}
