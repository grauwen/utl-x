package com.glomidco.utlx.stdlib.validate

import com.glomidco.utlx.core.udm.UDM

/**
 * `ValidationResult` — the output of every `validate.*` function, modelled as a plain UDM `Object`
 * (spec §6; keeps `:core`/UDM frozen, serialized by the existing Object writer). Shape:
 *
 * ```
 * { valid, passed, failed, warnings, notices,
 *   issues: [ { rule?, field?, message, severity } ],   // all severities, each tagged
 *   payload }                                            // the input, passed through unchanged
 * ```
 *
 * `valid` is true iff no **error**-severity issue occurred; `warning`/`notice` never flip it. The
 * routing budgets (block after N errors / M warnings) are an engine-layer policy over these counts
 * (§8) — not computed here; these functions only *produce* the counts.
 */
data class ValidationIssue(
    val field: String?,
    val message: String,
    val severity: String = "error",   // "error" (default) | "warning" | "notice"
    val rule: String? = null,
)

/** Build the `ValidationResult` Object from the payload, the issues found, and how many checks ran. */
fun validationResult(payload: UDM, issues: List<ValidationIssue>, totalChecks: Int): UDM.Object {
    val failed = issues.count { it.severity == "error" }
    val warnings = issues.count { it.severity == "warning" }
    val notices = issues.count { it.severity == "notice" }
    // `passed` counts checks with no issue; a check can yield at most one issue in the Tier-1 shortcuts.
    val passed = (totalChecks - issues.size).coerceAtLeast(0)
    return UDM.Object.of(
        "valid" to UDM.Scalar.boolean(failed == 0),
        "passed" to UDM.Scalar.number(passed),
        "failed" to UDM.Scalar.number(failed),
        "warnings" to UDM.Scalar.number(warnings),
        "notices" to UDM.Scalar.number(notices),
        "issues" to UDM.Array(issues.map { iss ->
            UDM.Object.of(
                "rule" to (iss.rule?.let { UDM.Scalar.string(it) } ?: UDM.Scalar.nullValue()),
                "field" to (iss.field?.let { UDM.Scalar.string(it) } ?: UDM.Scalar.nullValue()),
                "message" to UDM.Scalar.string(iss.message),
                "severity" to UDM.Scalar.string(iss.severity),
            )
        }),
        "payload" to payload,
    )
}
