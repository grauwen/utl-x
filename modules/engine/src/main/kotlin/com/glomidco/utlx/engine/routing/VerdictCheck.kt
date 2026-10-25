package com.glomidco.utlx.engine.routing

import com.glomidco.utlx.core.udm.UDM

/**
 * EF23 — the result of looking at a transformation's output for a `validate.*` verdict.
 *
 * **Why three states and not a nullable [ValidationRoutingPolicy.Decision].**
 *
 * The policy needs the output as a `UDM`, but by the time a transport holds an `ExecutionResult`
 * the output is already a serialized String — and the strategies do not agree on where the UDM
 * still exists: `CompiledStrategy` and `CopyStrategy` hold it before serializing, while
 * `TemplateStrategy` gets its output back already serialized from
 * `TransformationService.transform`.
 *
 * That makes "no verdict here" and "nobody looked" genuinely different facts, and collapsing them
 * into `null` would be the start of the next fail-open bug: enforcement would read *absent* as
 * *fine*. This feature area has produced that shape three times already — VB04 (malformed
 * arguments passed), VB06 (a chained validator reporting a passing verdict over a failing one) and
 * F24's quantifier — each time because an incomplete enumeration defaulted to "let it through".
 *
 * Note what "fail closed" cannot mean here: without the UDM you also cannot tell whether the
 * output *was* a verdict, so rejecting on [NotInspected] would reject ordinary mappings too. The
 * honest encoding is to record not-knowing and let the enforcement layer decide — which needs to
 * know whether the transformation is *supposed* to produce a verdict, i.e. deployment config that
 * does not exist yet (EF23 item 4).
 *
 * [NotInspected] is the DEFAULT on `ExecutionResult`, deliberately: a strategy that forgets to set
 * this is marked *unknown*, never *fine*.
 */
sealed interface VerdictCheck {

    /** The output was a `ValidationResult`, and here is the routing decision over it. */
    data class Decided(val decision: ValidationRoutingPolicy.Decision) : VerdictCheck

    /** The output was inspected and is an ordinary mapping result — there is nothing to gate. */
    data object NoVerdict : VerdictCheck

    /** The output was never available as a UDM, so no claim is made either way. */
    data object NotInspected : VerdictCheck

    companion object {
        /**
         * Inspect an output UDM. Returns [Decided] only when the output actually carries a verdict,
         * so an ordinary mapping is [NoVerdict] rather than a vacuous "forward" decision.
         */
        fun of(
            outputUDM: UDM,
            policy: ValidationRoutingPolicy = ValidationRoutingPolicy.FAIL_CLOSED,
        ): VerdictCheck =
            if (policy.isValidationResult(outputUDM)) Decided(policy.decide(outputUDM)) else NoVerdict
    }
}
