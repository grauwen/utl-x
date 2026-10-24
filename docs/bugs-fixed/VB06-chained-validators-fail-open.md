# VB06: chaining validators with `|>` reports a passing verdict over a failing one (fail-open)

> **VB = Validation bug** (`%utlx 1.1` / `validate.*`), after VB01–VB05. This is **VB06**.

**Status:** **FIXED (2026-10-24, pending commit)** — every validator now refuses a `ValidationResult`
as its payload and reports a malformed rule naming the mistake. See §Implementation.
**Priority:** **High** — fail-open, reachable by writing the most natural-looking composition, and
the resulting verdict is *confidently wrong* rather than obviously broken.
**Component:** `stdlib/.../validate/ValidateFunctions.kt` — all eight named validators plus `assert`.

> **One-line:** `$input |> validate.required(["orderId"]) |> validate.required(["valid","issues"])`
> returned `valid:true, passed:2, failed:0` with the genuine failure nested under `payload`.

## How it was found

Not by a test. While writing `examples/utlx/advanced/validation-guard-passthrough.utlx` — a worked
guard example — the obvious way to express "apply two rules" was to pipe one validator into the next.
Checking what that actually produced exposed the bug.

## Evidence (via the built CLI, before the fix)

Payload `{"qty": 500}` — no `orderId`, and `qty` far out of any sane range.

| Script | Result |
|---|---|
| `$input \|> validate.required(["orderId"])` | ✅ `valid:false, failed:1` — correct |
| `… \|> validate.required(["valid","issues"])` *chained* | ❌ **`valid:true, passed:2, failed:0`** |
| `$input \|> validate.required([…]) \|> validate.inRange("qty",1,100)` | ❌ `valid:false` but for the **wrong reason**: *"Field 'qty' is not a number (inRange)"* — `qty` is 500; the first verdict is gone |

The second row is the dangerous one. A `ValidationResult` **has** fields named `valid` and `issues`,
so `required(["valid","issues"])` legitimately passes *on the result object*. The output was:

```json
{ "valid": true, "passed": 2, "failed": 0,
  "payload": { "valid": false, "failed": 1,
               "issues": [ { "field": "orderId", "message": "Required field 'orderId' is missing" } ] } }
```

A passing verdict wrapped around a failing one.

**Why this is fail-open and not merely confusing:** `ValidationRoutingPolicy.decide` reads
**top-level** `failed`, and `isValidationResult` is satisfied by the outer object. So the engine would
have read `failed: 0` and **FORWARDED** a payload that failed validation. The nesting hides the
failure from exactly the component whose job is to catch it.

## Root cause

Validators take the payload positionally and never ask what it is:

```kotlin
val payload = payloadOf(args)            // args[0] — whatever was piped in
val p = props(payload)                   // "fields of the thing I was given"
```

`|>` passes the left-hand result as the first argument, so a second validator in a pipeline receives
the first one's `ValidationResult` and dutifully inspects *that*. The inner verdict survives only as
the outer result's `payload`, and the outer counts describe the wrong object.

There is nothing wrong with any individual validator. The defect is that **validators do not compose
by piping, and nothing said so.**

## Fix

A shape test plus one guard per validator, reusing VB04's `malformedRule` channel:

```kotlin
private fun looksLikeValidationResult(udm: UDM): Boolean {
    val props = (udm as? UDM.Object)?.properties ?: return false
    return props.containsKey("valid") && props.containsKey("failed") && props.containsKey("issues")
}

// at the head of every validator, right after it takes its payload:
if (looksLikeValidationResult(payload)) return malformedRule(payload, "<fn>", chainedPayloadReason())
```

The message states the diagnosis and the remedy:

```
validate.required: malformed rule — payload is a ValidationResult; validators must not be
chained with |> — each takes the message, not the previous result
```

Same three-field shape test the engine's `ValidationRoutingPolicy.isValidationResult` uses, so the
two agree on what a verdict looks like.

### Why reject rather than auto-unwrap

Two other designs were considered:

- **Auto-unwrap and merge** — if the payload is a `ValidationResult`, validate its `.payload` and
  merge the incoming issues and counts. This would make `a |> v1 |> v2` a genuine composition idiom
  and solve the missing-combinator problem elegantly. Rejected *for now* because it changes what `|>`
  **means** for this namespace — a language design decision, not a bug fix — and because it would
  silently do the wrong thing for the rare payload that legitimately *is* a stored verdict.
- **Do nothing but document it** — rejected: the failure is silent and fail-open, and documentation
  does not protect a deployment from a plausible-looking script.

Rejecting is the fail-closed option and matches the principle already settled three times
(VB01 `check`, VB02 `when`, VB04 arguments): **a rule that cannot be constructed or evaluated is an
error, never a pass.** If auto-unwrap is later adopted it can replace the guard without changing any
correct script, since no correct script chains validators today.

### Correct composition

Until `validate.all(results)` (a planned guard-profile addition) exists:

```utlx
// one aggregated verdict
$input |> validate.assert([ { rule: "…", check: (p) => …, message: "…" }, … ])

// or named validators, each taking the MESSAGE, merged by the caller
let a = $input |> validate.required(["orderId"])
let b = $input |> validate.inRange("qty", 1, 100)
in { failed: a.failed + b.failed, issues: flatten([a.issues, b.issues]) }
```

`+` sums counts but does **not** concatenate arrays — `flatten([...])` does.

## Implementation (2026-10-24)

- **`:stdlib` `ValidateFunctions.kt`** — `looksLikeValidationResult` + `chainedPayloadReason`
  helpers; a two-line guard at the head of all nine entry points (`assert`, `required`, `inRange`,
  `oneOf`, `matches`, `isoDate`, `unique`, `mutuallyExclusive`, `atLeastOne`). No signature or
  result-shape change; `:core` and the engine untouched.

## Tests

- `conformance .../validate/chained_validators_rejected.yaml` — the fail-open script, asserting the
  full result including the nested inner verdict still being visible under `payload`. ✅
- `ValidateFunctionsTest` (`:stdlib`) — 8 added tests: each chained entry point rejected, a fixture
  sanity check so the others cannot pass vacuously, an **over-reach guard** (a business message that
  merely has its own `valid` field must still validate normally — the shape test needs all three
  verdict fields), and a correct-composition test.
  Verified to be a true regression guard: with the nine guards removed, **5 of the 8 fail** — the
  five chaining-rejection tests — and the 3 that pass are exactly the behaviours VB06 did not change.
- Full conformance **544/544** (`language/validate` 15/15).

## Related

- [VB04](VB04-named-validators-fail-open-on-malformed-arguments.md) — the other fail-open family, and
  the `malformedRule` channel this reuses. VB04 was malformed *arguments*; VB06 is a malformed
  *payload*.
- `../architecture/utlxe-validation-enforcement-and-observability.md` — why a fail-open verdict is
  worse than a crash once the engine routes on it, and why `ValidationRoutingPolicy` reading
  top-level `failed` makes this reachable.
- `../proposals/utlx-validate-naming-and-guard-profile.md` — `validate.all(results)` as the proper
  combinator; its absence is what pushed authors towards chaining in the first place.
