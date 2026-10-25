package com.glomidco.utlx.engine.routing

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.validate.ValidateFunctions

/**
 * F21 / Phase 4 — **fail-closed routing policy** over a `validate.*` `ValidationResult`.
 *
 * The `validate.*` functions are **pure**: they report counts (`failed` = errors, `warnings`, `notices`)
 * and never apply a threshold (`valid` is purely `failed == 0`). The forward/reject decision is an
 * **engine/component policy** over those counts — deployment-configured and guard-lockable, in the same
 * §5.2 axis family as the parse profile — kept OUT of the pure functions so determinism holds
 * (spec §8 "Routing policy — per-severity budgets").
 *
 * Decision:
 * ```
 * forward  iff  failed <= maxErrors  AND  warnings <= maxWarnings      (notices never gate)
 * ```
 * - [maxErrors]   — error budget. **Default 0** → any error rejects (fail-closed). A guard locks this to 0.
 * - [maxWarnings] — warning budget. **Default 0** (strict); looser profiles raise it. Over budget → reject.
 * - notices       — never gate (audit/observability only).
 *
 * This is the "enforce" in the decide/enforce/lock split: `validate.*` **decides** (produces the result),
 * this policy **enforces** (forward vs dead-letter), a guard **locks** the budgets. It is pure and
 * transport-agnostic — WHERE a rejected payload goes (dead-letter channel, audit sink) is deployment
 * plumbing layered on top of this decision.
 */
class ValidationRoutingPolicy(
    val maxErrors: Int = 0,
    val maxWarnings: Int = 0
) {
    enum class Outcome { FORWARD, REJECT }

    data class Decision(
        val outcome: Outcome,
        val reason: String,
        val failed: Int,
        val warnings: Int,
        val notices: Int
    ) {
        val forwarded: Boolean get() = outcome == Outcome.FORWARD
    }

    /**
     * Decide forward/reject for a transformation output. If [output] is not a `ValidationResult`
     * (an ordinary mapping result), there is nothing to gate → **FORWARD** (the policy only governs
     * validation components, never plain mappings).
     */
    fun decide(output: UDM): Decision {
        if (!isValidationResult(output)) {
            return Decision(Outcome.FORWARD, "not a ValidationResult — nothing to gate", 0, 0, 0)
        }
        val obj = output as UDM.Object
        val failed = intOf(obj, "failed")
        val warnings = intOf(obj, "warnings")
        val notices = intOf(obj, "notices")
        return decide(failed, warnings, notices)
    }

    /** Decide from explicit counts (notices accepted for the audit record; they never gate). */
    fun decide(failed: Int, warnings: Int, notices: Int = 0): Decision {
        val errorsOk = failed <= maxErrors
        val warningsOk = warnings <= maxWarnings
        return if (errorsOk && warningsOk) {
            Decision(Outcome.FORWARD, "within budget (failed=$failed<=$maxErrors, warnings=$warnings<=$maxWarnings)",
                failed, warnings, notices)
        } else {
            val why = buildString {
                if (!errorsOk) append("errors $failed > budget $maxErrors")
                if (!errorsOk && !warningsOk) append("; ")
                if (!warningsOk) append("warnings $warnings > budget $maxWarnings")
            }
            Decision(Outcome.REJECT, "over budget ($why)", failed, warnings, notices)
        }
    }

    /**
     * A UDM Object is a ValidationResult if it carries the verdict shape (`valid` + the count
     * fields).
     *
     * Delegates to `:stdlib`, which owns the definition because it also *produces* the shape
     * (`validationResult`). This class used to carry its own copy of the three-field test in a
     * different spelling, so a change to the ValidationResult shape would have had to be found in
     * two modules. `:engine` already declares `implementation(project(":stdlib"))`, so this is a
     * plain compile-time call — no reflection, and nothing for GraalVM to configure (contrast
     * B19/B25, where an undeclarable boundary was bridged reflectively and broke the native image).
     */
    fun isValidationResult(udm: UDM): Boolean = ValidateFunctions.looksLikeValidationResult(udm)

    private fun intOf(obj: UDM.Object, key: String): Int {
        val v = (obj.get(key) as? UDM.Scalar)?.value ?: return 0
        return when (v) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: 0
            else -> 0
        }
    }

    companion object {
        /** The default, strictest posture a guard locks to: any error or warning rejects. */
        val FAIL_CLOSED = ValidationRoutingPolicy(maxErrors = 0, maxWarnings = 0)
    }
}
