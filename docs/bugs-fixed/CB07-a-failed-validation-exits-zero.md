# CB07 — a failed `validate.*` run exits 0, so a pipeline treats it as success

**Status:** Fixed — 2026-11-16
**Priority:** High — silent success on a failed check; a shell or CI step forwarded data that the
transformation itself judged invalid
**Component:** `:cli` — `commands/TransformCommand.kt` (exit-code handling), with
`ValidationRoutingPolicy` moved `:engine` → `:stdlib` so both surfaces share one implementation
**Found:** 2026-11-16, assessing whether the `validate.*` layer is usable as a guard

## Summary

`%utlx 1.1` validation computes a correct, detailed verdict — and the CLI exits **0** whether the
verdict is `valid: true` or `valid: false`.

```bash
$ echo '{"orderId":"A1","amount":99999}' | utlx transform check.utlx; echo "exit=$?"
{
  "valid": false,
  "passed": 2,
  "failed": 1,
  "issues": [
    { "rule": "amount-range", "field": "amount",
      "message": "Field 'amount' (99999.0) is above maximum 1000.0", "severity": "error" }
  ]
}
exit=0
```

Everything about that output is right except the last line. Any caller that follows shell
convention — `set -e`, `&&`, a CI step, a Camel/Kafka route checking the exit status — concludes
the message passed validation and forwards it. That is the exact failure mode validation exists to
prevent.

## `--exit-status` does not help

F27 added `--exit-status` ("Exit 1 when the result is null or false"). It does not fire here,
because the result of a validator is a **ValidationResult object**, not a boolean:

```bash
$ echo '{"amount":99999}' | utlx transform --exit-status check.utlx >/dev/null; echo $?
0
```

The flag is behaving as documented. The gap is that nothing connects the *verdict* to the exit
code.

## The workaround, and why it is not good enough

You can extract the boolean yourself:

```utlx
($input |> validate.assert([
  { rule: "amount-range", inRange: { field: "amount", min: 1, max: 1000 } }
])).valid
```

```bash
$ echo '{"amount":99999}' | utlx transform --exit-status check.utlx; echo "exit=$?"
false
exit=1          # correct
```

But look at the output: `false`. **You can have the exit code or the diagnostics, not both.** The
rule name, the field, the offending value and the message — everything an operator needs to act —
are gone, because the script had to reduce the verdict to a boolean to make the exit code work.

Keeping the detail and piping to an external tool (`… | jq -e .valid`) works but makes a JSON
processor a hard dependency of running a UTL-X check, which is the opposite of the point.

## Why it matters beyond the shell

This is the CLI-side half of the gap recorded in
[EF23](../features/EF23-engine-validation-enforcement-and-observability.md). The engine stores the
verdict on `ExecutionResult.verdict` — every strategy writes it ([EB14](EB14-validation-verdict-dropped-on-the-interpreter-paths.md))
— and **nothing reads it**. `ValidationRoutingPolicy` (FAIL_CLOSED, REJECT/FORWARD, warning
budgets) exists and is unit-tested with no production call site.

So the language *decides* correctly on every path, and no surface *acts* on the decision. EF23
covers the engine; this report covers the CLI, which is where most people will meet the problem
first because the CLI is how validation is tried out.

## Suggested fix — expose the budgets the engine already has

### First, what is *not* broken

`%utlx 1.1` does **not** imply "fail the pipeline", and that is deliberate.
`ValidationRoutingPolicy` states the contract:

> The `validate.*` functions are **pure**: they report counts (`failed`, `warnings`, `notices`)
> and never apply a threshold (`valid` is purely `failed == 0`). The forward/reject decision is an
> **engine/component policy** — deployment-configured and guard-lockable — kept OUT of the pure
> functions so determinism holds.

That is the decide / enforce / lock split: the script **decides**, the deployment **enforces**, a
guard **locks** the budgets. The same script must serve as a guard (reject the message) and as a
reporter (emit the verdict as data, exit 0), so the threshold cannot live in the script. A CLI
switch is therefore not a workaround for a missing default — it is the designed home for the
decision on that surface.

### What to add

The thresholds already exist and are unit-tested:

```kotlin
ValidationRoutingPolicy(maxErrors = 0, maxWarnings = 0)   // FAIL_CLOSED, the guard posture
forward iff failed <= maxErrors AND warnings <= maxWarnings     // notices never gate
```

So the CLI should expose *those*, not invent a boolean:

```
--validation-budget errors=N,warnings=N
        Gate the exit code on a validate.* verdict. Both default to 0, so
        --validation-budget alone means "any error or warning fails" (FAIL_CLOSED).
        Omitted entirely → no gating, today's behaviour.
        Output is written either way; only the exit code changes.

--fail-on-invalid
        Ergonomic alias for --validation-budget errors=0,warnings=0.
```

"Fail when there are more than 3 warnings" is then `--validation-budget warnings=3`, and it means
exactly what the same budget means in a deployment — which is the point. A CLI policy that
disagreed with the engine's would be the B34/B35/EB03/B48 divergence in a new place.

The reject reason is already human-readable and should be printed on stderr:

```
over budget (warnings 5 > budget 3)
```

### Properties to keep

- **Not the default.** Today's exit 0 must remain when no budget flag is given, or every existing
  pipeline running a validating script changes behaviour silently.
- **Output unchanged.** The diagnostics are the valuable part; the flag must not re-create the
  trade-off this report is about.
- **Silent on plain mappings.** `ValidationRoutingPolicy.decide` already returns FORWARD for any
  output that is not a ValidationResult, so a script that validates nothing is unaffected —
  reuse that rather than re-testing the shape in the CLI.
- **One implementation.** Call `ValidationRoutingPolicy` from `:engine`; do not re-derive the
  comparison. `:modules:cli` would need the dependency, which is the only real design question
  here — the alternative is moving the policy down into `:core` or `:stdlib`, where
  `ValidateFunctions.looksLikeValidationResult` already lives.

## What was built

Two flags, both delegating to [`ValidationRoutingPolicy`](#why-the-policy-moved-to-stdlib):

```
--fail-on-invalid              any error or warning fails          (FAIL_CLOSED)
--validation-budget SPEC       errors=N,warnings=N (both default 0)
```

```bash
# the reported defect
$ utlx transform check.utlx bad.json; echo $?
{"valid": false, "failed": 1, "issues":[…]}
0                                              ← unchanged without a flag

$ utlx transform --fail-on-invalid check.utlx bad.json; echo $?
{"valid": false, "failed": 1, "issues":[…]}    ← verdict still written IN FULL
1
# stderr: Validation failed: over budget (errors 1 > budget 0)

# "fail when warnings exceed 3"
$ utlx transform --validation-budget warnings=3 check.utlx …   # 3 warnings → exit 0
                                                               # 4 warnings → exit 1
```

The trade-off this report was about is gone: you get the exit code **and** the diagnostics.

### Why the policy moved to `:stdlib`

`ValidationRoutingPolicy` lived in `:modules:engine`, and `:modules:cli` does **not** depend on the
engine — only on `:core`, the formats and `:stdlib`. The options were to add an engine dependency
to the CLI (pulling the whole runtime, plus `:modules:bundle`, into the CLI jar) or to re-derive
`failed <= maxErrors && warnings <= maxWarnings` locally. The second is the two-implementations
divergence that produced B34, B35, EB03 and B48 — in a place where the consequence is a check that
passes locally and rejects in production.

So the class moved to `com.glomidco.utlx.stdlib.validate`, beside `ValidateFunctions`, which
already **owns** the ValidationResult shape (`looksLikeValidationResult` — the policy already
called it). Both the CLI and the engine depend on `:stdlib`, so both now gate through one
implementation. `VerdictCheck` stays in `:engine`; only its import changed.

### Design points that fell out of the existing policy

- **Not the default.** No flag → exit code unchanged, because `validate.*` is pure: it decides,
  the deployment enforces, a guard locks. Flipping the default would silently change every
  existing pipeline running a validating script.
- **Plain mappings are unaffected for free.** `decide()` already returns FORWARD for output that
  is not a ValidationResult, so a script that validates nothing needs no special case.
- **Notices are rejected as a budget key**, with a message saying why: they never gate, and
  offering a knob that does nothing is the CB06 mistake.
- **Bad specs fail loudly** — `warnings=abc`, `errors=-1`, `typo=3` are all errors. A silently
  dropped budget is the worst outcome: the user believes a gate is in place when the run is
  ungated.

## Tests

`CB07ValidationBudgetTest` (14 tests, `:modules:cli`) covers the default staying exit 0, both
flags, the verdict surviving in full on a failing run, budgets at and above the limit for errors
and warnings, plain mappings, and each parser rejection.

The last test is the one that matters structurally: **the CLI outcome is compared against
`ValidationRoutingPolicy.decide()` on the same counts.** If the CLI ever stops delegating, that
fails rather than the two quietly drifting.

Full regression: **4956 JVM tests, 0 failures**; 582/582 conformance on the JVM and the GraalVM
native binary, with the gate verified on the native binary too.

## Still open

- **The engine half** — [EF23](../features/EF23-engine-validation-enforcement-and-observability.md):
  routing a rejected payload to a dead-letter channel, counters, verdict-driven logging. The
  policy it needs is now in `:stdlib`, shared and unit-tested, and `ExecutionResult.verdict` is
  already produced on every strategy — but nothing in the engine reads it yet.
- **`utlx validate` and `utlx lint`** do not accept these flags. That is consistent — they analyse
  a script rather than run one over data — but a user reaching for "check this message" may try
  `validate` first.

