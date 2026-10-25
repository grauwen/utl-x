# VB07: an unrecognised `severity` leaves a failing rule uncounted, so the verdict stays valid

> **VB = Validation bug** (`%utlx 1.1` / `validate.*`), after VB01–VB06. This is **VB07**.

**Status:** **FIXED (2026-10-25, pending commit)** — severity is now recognised case-insensitively and
normalised, and an unknown value becomes an `error` issue on that rule. Rule-ID discipline added in
the same pass. See §Implementation.
**Priority:** **High** — fail-open. A typo in a severity silently disabled the rule, and the engine's
own `ValidationError` uses the uppercase spelling that triggered it.
**Component:** `stdlib/.../validate/ValidateFunctions.kt` `assert` + `ValidationResult.kt` counting.

> **One-line:** `severity: "banana"` — or `"ERROR"` — produced `valid:true, failed:0` for a rule that
> had actually failed, with its issue sitting in `issues[]`.

## How it was found

By asking what the guard design requires of `validate.*` and checking each requirement against the
code. The guard mandates that *"only `error`-severity rules go into an FPGA table; `warning` and
`notice` stay in UTL-X for audit"* — which only works if severity is a closed set. It wasn't
validated at all, so the next question was what an unknown value does.

## Evidence (via the built CLI, before the fix)

Payload `{"qty": -5}`, rule `check: (p) => p.qty > 0` — which fails.

| `severity` | Result |
|---|---|
| `"error"` | ✅ `valid:false  failed:1` |
| `"banana"` | ❌ **`valid:true  failed:0  warnings:0  notices:0`** |
| `"ERROR"` | ❌ **`valid:true  failed:0  warnings:0  notices:0`** |

## Root cause

`assert` passed the author's string through unchecked:

```kotlin
severity = asString(p["severity"]) ?: "error",
```

and `validationResult` counts by **exact match**:

```kotlin
val failed   = issues.count { it.severity == "error" }
val warnings = issues.count { it.severity == "warning" }
val notices  = issues.count { it.severity == "notice" }
val passed   = (totalChecks - issues.size).coerceAtLeast(0)
...
"valid" to UDM.Scalar.boolean(failed == 0),
```

So an unrecognised severity put the issue in **no bucket**. `failed` stayed 0 → `valid` stayed true →
`ValidationRoutingPolicy` forwards. The issue *was* in `issues[]`, and `passed` *did* drop, so the
report was internally inconsistent — a failure visible in the list but absent from every count.

`"ERROR"` is the trap worth naming: the engine's `ValidationError` (`ExecutionStrategy.kt`) uses
`severity: String = "ERROR"`, so an author following the engine's own convention got a silently dead
rule.

## Fix

```kotlin
private val SEVERITIES = setOf("error", "warning", "notice")

private fun canonicalSeverity(raw: String?): String? {
    if (raw == null) return "error"               // absent → the documented default
    val lower = raw.trim().lowercase()
    return if (lower in SEVERITIES) lower else null
}
```

- **Case-insensitive, normalised to lowercase.** `"ERROR"`, `"Warning"`, `"NOTICE"` are accepted and
  reported in canonical form, so the report never carries two spellings of one severity. Forgiving on
  input, exact on output.
- **An unknown value becomes an `error` issue on that rule**, naming the offending value and the
  permitted set — per-rule isolation, as VB01 does for a non-evaluable check, rather than discarding
  the whole batch.
- The author's own `message` is preserved when the severity merely needed normalising; it is replaced
  only when the severity is genuinely unknown, because then the rule itself is the problem.

## Rule-ID discipline (same pass)

The guard's proof strategy compares *"`valid` **and** the set of failing rule IDs (`issues[].rule`)
with the hardware bitmask"*, and each ID maps to one bit of a bounded table. `rule` was optional and
duplicates were accepted, which makes a report unusable as an audit record. Now:

| | Rule | Where checked | Why there |
|---|---|---|---|
| **Presence** | every rule needs a non-blank `rule` id | `:stdlib` `assert` | a per-rule property; survives `every` expansion, since each expanded rule inherits the author's id |
| **Uniqueness** | ids unique within a rule list | `:core` `evaluateValidateAssert`, **before expansion** | see below |

Both are **malformed-policy** faults: the whole list is rejected rather than one rule failing, because
a report that cannot attribute a failure is not usable at all.

### Why uniqueness could not live in `:stdlib`

The first implementation put both checks in `assert` and **broke `every`**: a quantified rule expands
to one rule per element, all legitimately carrying the author's single id, so the uniqueness check
rejected it (`validate_assert_every_per_element_path` failed, 545/546). Uniqueness is a property of
the **author's pre-expansion list**, which only the interpreter sees — so it moved there, and the
interpreter dispatches a single synthetic failing rule when it finds a collision.

**Not enforced:** a naming *style*. The guard design describes "stable kebab-case" ids, but
`priceCheck` or `price_check` are equally attributable. Stability and uniqueness are semantically
necessary; casing is convention, and is documented rather than rejected.

## Implementation (2026-10-25)

- **`:stdlib` `ValidateFunctions.kt`** — `SEVERITIES` + `canonicalSeverity`; `assert` validates the
  severity per rule and the id presence per list. No result-shape change.
- **`:core` `interpreter.kt`** — `evaluateValidateAssert` checks id uniqueness on the author's list
  before `every`/`some` expansion.

## Tests

- `conformance .../validate/assert_unknown_severity.yaml` — `"Warning"` normalises and keeps its
  non-gating semantics; `"banana"` becomes an error issue. Note the second rule's own check would
  have *passed*, which is why the malformed severity is reported regardless. ✅
- `conformance .../validate/assert_rule_id_required.yaml` — a rule without an id rejects the policy. ✅
- `ValidateFunctionsTest` (`:stdlib`) — 8 added: the unknown severity, uppercase `ERROR` normalising
  while preserving the author's message, mixed-case `Warning`/`NOTICE` keeping their semantics, the
  absent-severity default, a missing id, a blank id, a missing id among valid ones, and that
  duplicates are *not* rejected at this layer.
- `F24QuantifiedRulesTest` (`:core`) — 2 added: duplicate author ids rejected before expansion, and
  `every` sharing one id across its elements explicitly **not** treated as a duplicate.
- Full conformance **548/548**, `language/validate` 19/19.

## Related

- [VB04](VB04-named-validators-fail-open-on-malformed-arguments.md) and
  [VB06](VB06-chained-validators-fail-open.md) — the same fail-open family. VB04 was malformed
  arguments, VB06 a malformed payload, VB07 a malformed severity. Three different ways to write a
  rule that cannot work and have it report success.
- `utlx-guard/docs/proposals/utlx-guard-validate-subset-and-compiler.md` §4.3 — the requirement that
  only `error` rules compile to hardware, which is what makes severity a closed set rather than a
  free-text label.
