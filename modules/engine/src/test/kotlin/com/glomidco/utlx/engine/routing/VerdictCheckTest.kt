package com.glomidco.utlx.engine.routing

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.engine.strategy.ExecutionResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * EF23 — the three-state verdict check.
 *
 * The point of these tests is the distinction between *no verdict in the output* and *nobody
 * looked*. Collapsing those into a nullable Decision is how the next fail-open bug would start:
 * enforcement reading "absent" as "fine". See the KDoc on [VerdictCheck].
 */
class VerdictCheckTest {

    private fun verdict(failed: Int, warnings: Int = 0) = UDM.Object.of(
        "valid" to UDM.Scalar.boolean(failed == 0),
        "passed" to UDM.Scalar(0),
        "failed" to UDM.Scalar(failed),
        "warnings" to UDM.Scalar(warnings),
        "notices" to UDM.Scalar(0),
        "issues" to UDM.Array(emptyList()),
        "payload" to UDM.Object.of("orderId" to UDM.Scalar(42)),
    )

    @Test fun `a clean verdict decides FORWARD`() {
        val c = assertIs<VerdictCheck.Decided>(VerdictCheck.of(verdict(failed = 0)))
        assertTrue(c.decision.forwarded)
    }

    @Test fun `a failing verdict decides REJECT under the fail-closed default`() {
        val c = assertIs<VerdictCheck.Decided>(VerdictCheck.of(verdict(failed = 1)))
        assertEquals(ValidationRoutingPolicy.Outcome.REJECT, c.decision.outcome)
        assertEquals(1, c.decision.failed)
    }

    @Test fun `a budgeted policy is honoured`() {
        val c = assertIs<VerdictCheck.Decided>(
            VerdictCheck.of(verdict(failed = 1), ValidationRoutingPolicy(maxErrors = 1))
        )
        assertTrue(c.decision.forwarded, "one error is within a budget of 1")
    }

    @Test fun `an ordinary mapping result is NoVerdict, not a vacuous forward decision`() {
        // Distinct from Decided(FORWARD): there was no verdict to gate, so there is nothing to
        // count or log either. A vacuous "forward" would pollute both.
        val plain = UDM.Object.of("orderId" to UDM.Scalar(42), "total" to UDM.Scalar(99))
        assertEquals(VerdictCheck.NoVerdict, VerdictCheck.of(plain))
    }

    @Test fun `a scalar output is NoVerdict`() {
        assertEquals(VerdictCheck.NoVerdict, VerdictCheck.of(UDM.Scalar("hello")))
    }

    @Test fun `ExecutionResult defaults to NotInspected so an omission reads as unknown`() {
        // The safety property: a strategy that never sets a verdict must not look like one that
        // looked and found nothing to gate.
        assertEquals(VerdictCheck.NotInspected, ExecutionResult(output = "{}").verdict)
    }

    @Test fun `NotInspected is distinguishable from NoVerdict`() {
        // Kotlin will not even let these be compared as their concrete types ("Operator '!=' cannot
        // be applied"), which is the strongest form of the guarantee: the two cannot be confused at
        // compile time, let alone conflated into a nullable Decision at runtime.
        val notLooked: VerdictCheck = VerdictCheck.NotInspected
        val lookedAndFoundNothing: VerdictCheck = VerdictCheck.NoVerdict
        assertTrue(notLooked != lookedAndFoundNothing)
    }
}
