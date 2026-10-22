package com.glomidco.utlx.engine.routing

import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F21 / Phase 4 — the fail-closed routing policy over a validate.* ValidationResult.
 * forward iff failed <= maxErrors AND warnings <= maxWarnings (notices never gate).
 */
class ValidationRoutingPolicyTest {

    private fun result(valid: Boolean, failed: Int, warnings: Int, notices: Int): UDM.Object =
        UDM.Object(mapOf(
            "valid" to UDM.Scalar(valid),
            "passed" to UDM.Scalar(0.0),
            "failed" to UDM.Scalar(failed.toDouble()),
            "warnings" to UDM.Scalar(warnings.toDouble()),
            "notices" to UDM.Scalar(notices.toDouble()),
            "issues" to UDM.Array(emptyList()),
            "payload" to UDM.Object(emptyMap())
        ))

    // ── default fail-closed (maxErrors=0, maxWarnings=0) ──

    @Test fun `clean result forwards`() {
        val d = ValidationRoutingPolicy.FAIL_CLOSED.decide(result(true, 0, 0, 0))
        assertTrue(d.forwarded)
    }

    @Test fun `one error rejects (fail-closed)`() {
        val d = ValidationRoutingPolicy.FAIL_CLOSED.decide(result(false, 1, 0, 0))
        assertEquals(ValidationRoutingPolicy.Outcome.REJECT, d.outcome)
        assertTrue(d.reason.contains("errors 1 > budget 0"))
    }

    @Test fun `one warning rejects under strict default`() {
        val d = ValidationRoutingPolicy.FAIL_CLOSED.decide(result(true, 0, 1, 0))
        assertEquals(ValidationRoutingPolicy.Outcome.REJECT, d.outcome)
    }

    @Test fun `notices never gate`() {
        val d = ValidationRoutingPolicy.FAIL_CLOSED.decide(result(true, 0, 0, 5))
        assertTrue(d.forwarded)
        assertEquals(5, d.notices)
    }

    // ── warning budget ──

    @Test fun `warning budget 2 forwards at 2 warnings, rejects at 3`() {
        val policy = ValidationRoutingPolicy(maxErrors = 0, maxWarnings = 2)
        assertTrue(policy.decide(result(true, 0, 2, 0)).forwarded)        // 2 <= 2
        assertFalse(policy.decide(result(true, 0, 3, 0)).forwarded)       // 3 > 2 → block at the 3rd
    }

    // ── error budget ──

    @Test fun `error budget 1 forwards at 1 error, rejects at 2`() {
        val policy = ValidationRoutingPolicy(maxErrors = 1, maxWarnings = 0)
        assertTrue(policy.decide(result(false, 1, 0, 0)).forwarded)       // 1 <= 1
        assertFalse(policy.decide(result(false, 2, 0, 0)).forwarded)      // 2 > 1 → block at the 2nd
    }

    @Test fun `errors and warnings both over budget reports both`() {
        val d = ValidationRoutingPolicy.FAIL_CLOSED.decide(result(false, 1, 1, 0))
        assertEquals(ValidationRoutingPolicy.Outcome.REJECT, d.outcome)
        assertTrue(d.reason.contains("errors") && d.reason.contains("warnings"), "reason: ${d.reason}")
    }

    // ── non-ValidationResult outputs are never gated (plain mappings forward) ──

    @Test fun `plain mapping object is not a ValidationResult and forwards`() {
        val plain = UDM.Object(mapOf("orderId" to UDM.Scalar(42.0), "total" to UDM.Scalar(99.0)))
        assertFalse(ValidationRoutingPolicy.FAIL_CLOSED.isValidationResult(plain))
        assertTrue(ValidationRoutingPolicy.FAIL_CLOSED.decide(plain).forwarded)
    }

    @Test fun `scalar output is not a ValidationResult and forwards`() {
        assertTrue(ValidationRoutingPolicy.FAIL_CLOSED.decide(UDM.Scalar("hello")).forwarded)
    }

    @Test fun `isValidationResult recognises the verdict shape`() {
        assertTrue(ValidationRoutingPolicy.FAIL_CLOSED.isValidationResult(result(true, 0, 0, 0)))
    }
}
