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

    // ── VB07: an unrecognised severity must not make a failing rule invisible ──

    private fun messagesOf(r: UDM) = ((r as UDM.Object).properties["issues"] as UDM.Array).elements
        .map { ((it as UDM.Object).properties["message"] as UDM.Scalar).value as String }

    @Test fun `VB07 an unknown severity is reported, not silently uncounted`() {
        // The bug: validationResult counts by EXACT severity match, so "banana" landed in no
        // bucket — failed stayed 0, valid stayed true, and a fail-closed policy forwarded a
        // payload whose rule had actually failed.
        val r = ValidateFunctions.assert(listOf(order, arr(rule("r", check = false, sev = "banana"))))
        assertFalse(valid(r), "an unknown severity must not leave the verdict valid")
        assertEquals(1, count(r, "failed"))
        assertEquals(listOf<Any?>("error"), severities(r))
        assertTrue(messagesOf(r).single().contains("unknown severity 'banana'"), "got: ${messagesOf(r)}")
    }

    @Test fun `VB07 uppercase ERROR is normalised rather than ignored`() {
        // The engine's own ValidationError uses uppercase "ERROR", so this spelling is likely to be
        // copied. It used to produce valid:true. Accepted and normalised, author message preserved.
        val r = ValidateFunctions.assert(listOf(order, arr(rule("r", check = false, sev = "ERROR"))))
        assertFalse(valid(r)); assertEquals(1, count(r, "failed"))
        assertEquals(listOf<Any?>("error"), severities(r))
        assertEquals("r failed", messagesOf(r).single(), "the author's message must survive")
    }

    @Test fun `VB07 mixed-case warning and notice normalise and keep their semantics`() {
        val w = ValidateFunctions.assert(listOf(order, arr(rule("r", check = false, sev = "Warning"))))
        assertTrue(valid(w), "a warning must not flip valid"); assertEquals(1, count(w, "warnings"))
        assertEquals(listOf<Any?>("warning"), severities(w))

        val n = ValidateFunctions.assert(listOf(order, arr(rule("r", check = false, sev = "NOTICE"))))
        assertTrue(valid(n)); assertEquals(1, count(n, "notices"))
        assertEquals(listOf<Any?>("notice"), severities(n))
    }

    @Test fun `VB07 an absent severity still defaults to error`() {
        val bare = UDM.Object.of("rule" to str("r"), "check" to UDM.Scalar.boolean(false),
            "message" to str("m"))
        val r = ValidateFunctions.assert(listOf(order, arr(bare)))
        assertFalse(valid(r)); assertEquals(listOf<Any?>("error"), severities(r))
    }

    // ── Rule-ID discipline: the id is what a failure is attributed to ──

    @Test fun `a rule without an id is a malformed policy`() {
        val noId = UDM.Object.of("check" to UDM.Scalar.boolean(false), "message" to str("m"))
        assertMalformed(ValidateFunctions.assert(listOf(order, arr(noId))), "non-empty `rule` id")
    }

    @Test fun `a blank rule id is a malformed policy`() {
        val blank = UDM.Object.of("rule" to str("   "), "check" to UDM.Scalar.boolean(false),
            "message" to str("m"))
        assertMalformed(ValidateFunctions.assert(listOf(order, arr(blank))), "non-empty `rule` id")
    }

    @Test fun `a missing id is reported even when other rules have one`() {
        val noId = UDM.Object.of("check" to UDM.Scalar.boolean(true), "message" to str("m"))
        assertMalformed(
            ValidateFunctions.assert(listOf(order, arr(rule("ok", check = true), noId))),
            "1 of 2",
        )
    }

    @Test fun `duplicate ids are NOT rejected at the stdlib layer`() {
        // Uniqueness is checked on the author's pre-expansion list, in the interpreter: a quantified
        // rule (`every`) legitimately expands to many rules sharing one id. See F24QuantifiedRulesTest
        // and VB07's report. Here two same-id rules aggregate normally.
        val r = ValidateFunctions.assert(listOf(order, arr(rule("a", check = false), rule("a", check = false))))
        assertEquals(2, count(r, "failed"))
    }

    // ── validate.all — the sanctioned combinator (VB06 closed off chaining) ──

    @Test fun `all merges results and recomputes the counts from the merged issues`() {
        val a = ValidateFunctions.required(listOf(order, arr(str("orderId"), str("customer")))) // 2 checks, 1 fails
        val b = ValidateFunctions.inRange(listOf(order, str("unitPrice"), num(100), num(200)))  // 1 check, fails
        val r = ValidateFunctions.all(listOf(order, arr(a, b)))
        assertFalse(valid(r))
        assertEquals(2, count(r, "failed"))
        assertEquals(1, count(r, "passed"), "passed is the sum of the parts, not a recount")
        assertEquals(listOf<Any?>("customer", "unitPrice"), issueFields(r))
    }

    @Test fun `all over passing results is valid`() {
        val a = ValidateFunctions.required(listOf(order, arr(str("orderId"))))
        val b = ValidateFunctions.oneOf(listOf(order, str("currency"), arr(str("EUR"), str("USD"))))
        val r = ValidateFunctions.all(listOf(order, arr(a, b)))
        assertTrue(valid(r)); assertEquals(2, count(r, "passed")); assertEquals(0, count(r, "failed"))
    }

    @Test fun `all preserves severities, so a warning still does not flip valid`() {
        val warn = ValidateFunctions.assert(listOf(order, arr(rule("w", check = false, sev = "warning"))))
        val ok = ValidateFunctions.required(listOf(order, arr(str("orderId"))))
        val r = ValidateFunctions.all(listOf(order, arr(warn, ok)))
        assertTrue(valid(r), "a merged warning must not flip valid")
        assertEquals(1, count(r, "warnings")); assertEquals(0, count(r, "failed"))
    }

    @Test fun `all over an empty list checks nothing and is vacuously valid`() {
        val r = ValidateFunctions.all(listOf(order, arr()))
        assertTrue(valid(r)); assertEquals(0, count(r, "passed")); assertEquals(0, count(r, "failed"))
    }

    @Test fun `all rejects an element that is not a ValidationResult`() {
        // The likely mistake: passing payloads or rule lists instead of validator OUTPUTS.
        assertMalformed(ValidateFunctions.all(listOf(order, arr(order))), "results[0] is not a ValidationResult")
    }

    @Test fun `all rejects a non-array results argument`() {
        assertMalformed(ValidateFunctions.all(listOf(order, str("nope"))), "results must be an array")
    }

    @Test fun `all refuses a ValidationResult as its own payload`() {
        val a = ValidateFunctions.required(listOf(order, arr(str("orderId"))))
        assertMalformed(ValidateFunctions.all(listOf(a, arr())), "must not be chained")
    }

    @Test fun `all re-canonicalises a severity so a hand-built result cannot smuggle one through`() {
        // A result assembled by hand (not by validate.*) could carry an uncounted severity; the
        // merge normalises it rather than propagating a VB07-style invisible failure.
        val handBuilt = UDM.Object.of(
            "valid" to UDM.Scalar.boolean(false), "passed" to num(0), "failed" to num(1),
            "warnings" to num(0), "notices" to num(0),
            "issues" to arr(UDM.Object.of(
                "rule" to str("x"), "field" to str("f"), "message" to str("m"),
                "severity" to str("ERROR"))),
            "payload" to order,
        )
        val r = ValidateFunctions.all(listOf(order, arr(handBuilt)))
        assertFalse(valid(r)); assertEquals(1, count(r, "failed"))
        assertEquals(listOf<Any?>("error"), severities(r))
    }

    // ── F25: declarative rule kinds — the policy stays data, and failures carry the rule id ──

    private fun kindRule(id: String, kind: String, spec: UDM, sev: String? = null, msg: String? = null): UDM {
        val pairs = mutableListOf("rule" to str(id), kind to spec)
        if (sev != null) pairs.add("severity" to str(sev))
        if (msg != null) pairs.add("message" to str(msg))
        return UDM.Object.of(*pairs.toTypedArray())
    }

    private fun issueRules(r: UDM) = ((r as UDM.Object).properties["issues"] as UDM.Array).elements
        .map { ((it as UDM.Object).properties["rule"] as UDM.Scalar).value }

    @Test fun `F25 a declarative rule carries the author's rule id into the issue`() {
        // This is the whole point: composing named validators with validate.all gave rule:null,
        // because a named validator has no id to report.
        val r = ValidateFunctions.assert(listOf(order, arr(
            kindRule("order-fields-present", "required", arr(str("orderId"), str("lineItems"))))))
        assertFalse(valid(r))
        assertEquals(listOf<Any?>("order-fields-present"), issueRules(r))
        assertEquals(listOf<Any?>("lineItems"), issueFields(r))
    }

    @Test fun `F25 a declarative kind contributes the validator's own check count`() {
        // `required` over two fields is two checks, exactly as the standalone call reports.
        val r = ValidateFunctions.assert(listOf(order, arr(
            kindRule("fields", "required", arr(str("orderId"), str("lineItems"))))))
        assertEquals(1, count(r, "passed")); assertEquals(1, count(r, "failed"))
    }

    @Test fun `F25 every declarative kind delegates to its named validator`() {
        val rules = arr(
            kindRule("a", "required", arr(str("orderId"))),
            kindRule("b", "inRange", obj("field" to str("unitPrice"), "min" to num(0), "max" to num(100))),
            kindRule("c", "oneOf", obj("field" to str("currency"), "values" to arr(str("EUR")))),
            kindRule("d", "matches", obj("field" to str("currency"), "pattern" to str("^[A-Z]{3}$"))),
            kindRule("e", "mutuallyExclusive", arr(str("orderId"))),
            kindRule("f", "atLeastOne", arr(str("orderId"))),
        )
        val r = ValidateFunctions.assert(listOf(order, rules))
        assertTrue(valid(r), "all six should pass on this payload: ${issueRules(r)}")
    }

    @Test fun `F25 the rule's own severity applies to the kind's issues`() {
        val r = ValidateFunctions.assert(listOf(order, arr(
            kindRule("w", "required", arr(str("lineItems")), sev = "warning"))))
        assertTrue(valid(r), "a warning must not flip valid")
        assertEquals(1, count(r, "warnings")); assertEquals(listOf<Any?>("warning"), severities(r))
    }

    @Test fun `F25 the rule's own message overrides the validator's wording`() {
        val r = ValidateFunctions.assert(listOf(order, arr(
            kindRule("m", "required", arr(str("lineItems")), msg = "an order needs line items"))))
        assertEquals("an order needs line items", messagesOf(r).single())
    }

    @Test fun `F25 a rule with no kind is reported`() {
        val bare = UDM.Object.of("rule" to str("r"), "message" to str("m"))
        val r = ValidateFunctions.assert(listOf(order, arr(bare)))
        assertFalse(valid(r))
        assertTrue(messagesOf(r).single().contains("no rule kind"), "got: ${messagesOf(r)}")
    }

    @Test fun `F25 a rule with two kinds is reported`() {
        val two = UDM.Object.of("rule" to str("r"),
            "required" to arr(str("orderId")),
            "inRange" to obj("field" to str("unitPrice"), "min" to num(0), "max" to num(1)))
        val r = ValidateFunctions.assert(listOf(order, arr(two)))
        assertFalse(valid(r))
        assertTrue(messagesOf(r).single().contains("2 rule kinds"), "got: ${messagesOf(r)}")
    }

    @Test fun `F25 a malformed kind spec names the shape it expected`() {
        val bad = UDM.Object.of("rule" to str("r"),
            "inRange" to obj("field" to str("unitPrice"), "min" to num(0)))   // no max
        val r = ValidateFunctions.assert(listOf(order, arr(bad)))
        assertFalse(valid(r))
        assertTrue(messagesOf(r).single().contains("{ field, min, max }"), "got: ${messagesOf(r)}")
    }

    @Test fun `F25 a non-array spec for a list-shaped kind is reported`() {
        val bad = UDM.Object.of("rule" to str("r"), "required" to str("orderId"))
        val r = ValidateFunctions.assert(listOf(order, arr(bad)))
        assertTrue(messagesOf(r).single().contains("an array of field names"), "got: ${messagesOf(r)}")
    }

    @Test fun `F25 a false when guard skips a declarative rule and counts it as passed`() {
        val skipped = UDM.Object.of("rule" to str("r"),
            "required" to arr(str("lineItems")), "when" to UDM.Scalar.boolean(false))
        val r = ValidateFunctions.assert(listOf(order, arr(skipped)))
        assertTrue(valid(r)); assertEquals(1, count(r, "passed"))
    }

    @Test fun `F25 a declarative kind inherits the validator's own hardening`() {
        // `unique` over a field that is not an array is a malformed rule (VB04); the declarative
        // form must report that rather than passing, and must attribute it to this rule id.
        val r = ValidateFunctions.assert(listOf(obj("lines" to num(7)), arr(
            kindRule("lines-unique", "unique", obj("arrayField" to str("lines"), "keyField" to str("sku"))))))
        assertFalse(valid(r))
        assertEquals(listOf<Any?>("lines-unique"), issueRules(r))
        assertTrue(messagesOf(r).single().contains("is not an array"), "got: ${messagesOf(r)}")
    }

    @Test fun `F25 lambda and declarative rules mix in one policy`() {
        val r = ValidateFunctions.assert(listOf(order, arr(
            kindRule("declared", "required", arr(str("orderId"))),
            rule("lambda", check = true),
        )))
        assertTrue(valid(r)); assertEquals(2, count(r, "passed"))
    }
}
