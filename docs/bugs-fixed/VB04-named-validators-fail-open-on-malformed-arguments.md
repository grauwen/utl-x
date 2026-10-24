# VB04: the named validators fail **open** when their arguments are malformed

> **VB = Validation bug** (`%utlx 1.1` / `validate.*`), after
> [VB01](VB01-validate-assert-raw-check-crashes-on-missing-or-mistyped-data.md),
> [VB02](VB02-validate-assert-lambda-when-guard-silently-ignored.md) and
> [VB03](VB03-validate-oneOf-numeric-membership-fails.md). This is **VB04**.

**Status:** **FIXED (2026-10-24, pending commit)** — a malformed rule now reports an `error`-severity
issue instead of a silent pass, across all eight named validators, plus the exact-arity guard the
`@UTLXFunction` annotations declare but nothing enforced. See §Implementation.
**Priority:** **High** — this is the *dangerous* direction. An exception stops a pipeline loudly; a
`valid:true` on a fail-closed guard (`maxErrors = 0`) **forwards the payload**. A typo in a rule
silently disabled that rule and waved the data through, with nothing in the ValidationResult to say
so.
**Component:** `stdlib/.../validate/ValidateFunctions.kt` — all eight named validators.

> **One-line:** `$input |> validate.required("orderId")` — the array brackets forgotten — ran **zero
> checks** and returned `valid:true, passed:0, failed:0`.

## How it was found

Not by a failing test. While answering whether the named validators should take lambdas like
`validate.assert` does (to "avoid exceptions anywhere"), the right first step was to check whether
they throw at all. They don't: nine hostile inputs — invalid regex `"["`, nonsense date format, a
non-array where an array is expected, a missing argument — all returned a clean ValidationResult.

The audit answered a different question than the one asked. There were no exceptions to contain;
there were four **fail-open** paths.

## Evidence (via the built CLI, before the fix)

| Call | Result | Why it is wrong |
|---|---|---|
| `validate.required("price")` | `valid:true  passed:0  failed:0` | brackets forgotten → **zero checks ran**, reported valid |
| `validate.inRange("price", 0)` | `valid:true  passed:1` | `max` omitted → max comparison skipped, **counted as passed** |
| `validate.unique("items", "id")` on `items: 7` | `valid:true  passed:1` | not an array → zero keys → no duplicates → **counted as passed** |
| `validate.inRange("price","abc",100)` | `valid:true  passed:1` | garbage lower bound → min comparison skipped, **counted as passed** |
| `validate.mutuallyExclusive("a")` | `valid:true  passed:1` | non-array → zero fields → "at most one is set" trivially true |

Three further cases were already fail-*closed* but blamed the **payload** for a broken **rule**:
`matches` with an unparseable regex reported "does not match", `isoDate` with an unusable format
reported "is not a valid date", and `oneOf` with a non-array reported "is not one of []".

## Root cause

Every validator reached its arguments through a tolerant accessor that substitutes a harmless
default when the argument is the wrong shape:

```kotlin
val fields = (args.getOrNull(1) as? UDM.Array)?.elements?.mapNotNull { asString(it) } ?: emptyList()
val min    = asNumber(args.getOrNull(2))           // null when absent OR non-numeric
val array  = (props(payload)[arrayField] as? UDM.Array)?.elements ?: emptyList()
```

Each `?: emptyList()` and each nullable bound turns "this rule is broken" into "this rule has nothing
to check". And a rule with nothing to check **passes** — `validationResult` computes
`valid = (failed == 0)`, so zero issues is zero failures:

```kotlin
min != null && v < min -> listOf(ValidationIssue(...))   // a null bound simply skips the comparison
```

The tolerance was defensive in intent — avoid throwing — but it was pointed the wrong way. Tolerance
towards *data* is correct for a validator; tolerance towards *its own rule being broken* is not.

Separately, the declared arity was never enforced on this path. The machinery exists
(`Functions.kt:1735` throws when `args.size < minArgs`), but `inRange` accepted 3 arguments despite
declaring `minArgs = 4` — for `validate.*` the annotation's arity was documentation only.

## Fix — the principle this codebase had already settled twice

| | A rule that cannot be… | Outcome |
|---|---|---|
| VB01 | …be **evaluated** (`check` throws) | `error` issue, never a pass |
| VB02 | …have its **guard** evaluated (`when` throws) | `error` issue, never a skip |
| **VB04** | …be **constructed** (arguments malformed) | `error` issue, never a pass |

VB04 is that same rule extended one level outward, from evaluation to construction. One helper
carries it:

```kotlin
private fun malformedRule(payload: UDM, fn: String, reason: String, field: String? = null): UDM =
    validationResult(
        payload,
        listOf(ValidationIssue(field = field, message = "validate.$fn: malformed rule — $reason")),
        totalChecks = 1,
    )
```

Guards added per validator: exact arity; a field-name argument must be a non-blank **string**; an
array argument must actually be an array, and its entries strings; `inRange`'s bounds must be
numbers; `matches`'s pattern must compile; `isoDate`'s format must be a usable
`DateTimeFormatter` pattern; `unique`'s target field must resolve to an array.

The message always names the function and the reason —
`validate.required: malformed rule — fields must be an array of field names` — so the issue points at
the script, not the payload.

### Deliberate limits

- **An empty array argument is still legal.** `validate.required([])` checks nothing and passes.
  That is a plausible dynamic case (a computed, possibly-empty field list), and unlike a non-array it
  is not evidence of a mistake.
- **`min > max` in `inRange` is not flagged.** It makes every value fail, so it is already
  fail-closed; flagging it would improve diagnostics but not safety, and could misfire on computed
  bounds. Considered and left out to keep this change to the fail-open paths.
- **Data problems still blame the data.** `"QQQQ"` *is* a legal `DateTimeFormatter` pattern
  (quarter-of-year), so a value failing to parse against it stays a data issue, not a malformed rule.
  There is a test asserting exactly this, to stop the guards over-reaching.

### Why not lambdas (the question that started this)

`validate.inRange("price", 0, 100)` taking `(p) => p.price` instead would make things **worse**, for
two reasons:

1. The named validators are exception-free by **avoidance** — null-aware lookup and explicit type
   checks in Kotlin, never raw interpreter arithmetic. `assert` is exception-free by **containment**
   — try/catch per rule. Avoidance is the stronger guarantee; a lambda would move the robust tier
   down to containment, re-creating the VB01 exposure where it does not currently exist.
2. It would **discard the field name**. `inRange("price", …)` reports `field: "price"` in the issue,
   which is what dead-letter routing and operators key on. A lambda hands over a *value* with no
   name, so the name would have to be passed alongside it anyway — buying nothing, costing
   robustness.

## Implementation (2026-10-24)

- **`:stdlib` `ValidateFunctions.kt`** — `malformedRule` plus four small argument accessors
  (`arityReason`, `fieldArg`, `arrayArg`, `fieldNames`); guards at the head of all eight validators.
  No signature or result-shape change, so `:core` and the engine are untouched.

## Tests

- `conformance .../validate/malformed_rule_is_error.yaml` — the `validate.required("orderId")` typo
  end-to-end, asserting the full ValidationResult. ✅
- `ValidateFunctionsTest` (`:stdlib`) — 12 added tests: the five fail-open paths, the three
  blame-the-data-for-a-broken-rule paths, a non-string entry in a fields array, plus **two
  over-reach guards** (a valid-but-unmatched date pattern must still blame the data; all well-formed
  calls must be unaffected). A shared `assertMalformed` checks each one is `valid:false`,
  `failed:1`, `passed:0`, severity `error`, and names the rule as malformed.
  Verified to be a true regression guard: against the pre-fix validators, **10 of the 12 fail**, and
  the 2 that pass are exactly the two over-reach guards — i.e. the tests fail for the defect and not
  for anything else.
- Full conformance **541/541**, `language/validate` 12/12, on JVM and the rebuilt native binary.
- `:stdlib:test` green (2267 tests).

## Related

- [VB01](VB01-validate-assert-raw-check-crashes-on-missing-or-mistyped-data.md) — its blast-radius
  table presented the named validators as the robust tier. VB04 (with VB03) is the second correction
  to that claim: they did not crash, but they could silently validate **nothing**. "Does not throw"
  was never the same as "is safe".
