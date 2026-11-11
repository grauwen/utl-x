# EB15: the `%utlx` version gate is not enforced on the compiled strategies

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they call), the
> family of EB01–EB14. This is **EB15**.

**Status:** **FIXED**
**Priority:** High. A **fail-open** version gate on the engine's default strategy.
**Component:** `modules/engine` — all three strategies; with the rule moved into `:core`
(`LanguageVersions`).
**Found by:** diagnosing the `RUNS_ONLY_ON_TEMPLATE` entry in the cross-strategy conformance
baseline, which had never been classified.

> **One-line:** the version check lived inside `Interpreter`, so a *compilable* script never met it —
> `%utlx 2.0` ran as though it were 1.0 on COPY and COMPILED.

## Measured

```
%utlx 2.0        TEMPLATE -> RuntimeError: unsupported %utlx version '2.0'; this engine understands 1.0, 1.1
input json       COPY     -> OK: {"x": 1}
output json      COMPILED -> OK: {"x": 1}
---
$input
```

A script declaring a version this build does not understand **executed**. Any 2.0 semantics it
relied on were silently reinterpreted as 1.0 — which is the precise failure mode a version header
exists to prevent.

## The label hid it

The fixture had been catalogued as `RUNS_ONLY_ON_TEMPLATE`, with the comment *"Runs on TEMPLATE only,
for a reason not yet classified."* That reads as a **TEMPLATE limitation** — something TEMPLATE can
do that the others cannot — and it sat undiagnosed for that reason.

The truth was the reverse: **TEMPLATE was the only strategy that was correct.** The others were not
failing to run it, they were failing to *reject* it.

Worth keeping as a lesson about baselines: a divergence label describes a symptom, and a symptom
phrased as a capability gap invites the wrong priority. The entry should have read "COPY and COMPILED
do not enforce the version gate".

## Root cause

`Interpreter.enforceSupportedVersion` was the only copy of the rule, and it runs when the interpreter
runs. The compiled strategies execute bytecode and only reach the interpreter when an expression is
not compilable. `$input` **is** compilable, so nothing ever asked.

This is why the sibling check *appears* to work: `ValidateAcrossStrategiesTest` asserts the
`validate.*` namespace gate fires on every strategy, and it does — because a namespaced
`validate.*` call is not compilable and therefore falls back to the interpreter. The gate was never
strategy-aware; it was reached by accident, and only on the paths that happened to fall back.

## The fix

The declared version is a **static property of the header**, so the rule belongs where every
execution path can ask. It moved to `:core`:

```kotlin
// LanguageVersions
fun unsupportedReason(declared: String, maxVersion: String = LATEST): String? { … }
```

It returns the message rather than throwing, for two reasons: `:core` should not dictate the
exception type to the engine, and the interpreter wants a `RuntimeError` carrying the `Location` it
already has.

- `Interpreter.enforceSupportedVersion` now **delegates** to it — so the rule cannot fork again,
  which is exactly how it came to have one enforcement point in the first place.
- All three strategies call `requireSupportedLanguageVersion(compiledProgram)` in `initialize`, right
  after the source is compiled — the earliest point the header is known. It throws the same
  `RuntimeError` with the header's own location, so the three report an unsupported version
  identically.

```
%utlx 2.0   TEMPLATE / COPY / COMPILED
            -> RuntimeError: unsupported %utlx version '2.0'; this engine understands 1.0, 1.1
```

## What this does **not** fix — the engine has no language mode

`LanguageVersions.unsupportedReason` takes a `maxVersion`, and the second half of the gate — *"`%utlx
1.1` is not enabled in this engine (1.0-only mode)"* — is what F21's "one engine + a mode switch,
not two artifacts" decision rests on. The strategies pass no `maxVersion`, so they use `LATEST`, and
that half **cannot fire from the engine at all**.

That is a **missing configuration, not a missing check**: `TransformConfig` has no language-mode
field, and nothing in `modules/engine` references `maxLanguageVersion`. The interpreter has the
knob and the CLI sets it (`TransformCommand`, `resolvedLanguageMode`); the engine cannot express it.

So a 1.0-only ("UTLXe") **engine** posture is not enforceable today — it would accept a `%utlx 1.1`
script. The check is now shared and takes the parameter, so closing this is a matter of threading a
`languageMode` through `TransformConfig` to the strategies. Recorded here rather than invented.

## Behaviour change

A script declaring an unsupported `%utlx` version is now rejected on every strategy instead of
running on two of them. Supported versions (`1.0`, `1.1`) and a blank or absent version header are
unaffected — a blank version is still treated as the baseline, for back-compatibility.

The rejection now happens at `initialize` rather than `execute` on the compiled strategies. TEMPLATE
also moves to `initialize`, so all three agree on when as well as what.

**No conformance fixture changed:** 563/563, and the full Gradle suite is green (`:engine` 926
tests).

## Tests

`EB15VersionGateAcrossStrategiesTest` (`modules/engine`), 4 tests:

- `%utlx 2.0` is rejected on every strategy;
- the three report it **identically** — same message *and* same exception type, so a caller can catch
  one thing;
- other unknown versions (`9.9`, `0.1`, `1.2`) are rejected too, so the fix is not special-cased to
  `2.0`;
- `1.0` and `1.1` still run on every strategy.

Revert-verified: removing the three `requireSupportedLanguageVersion` calls fails **3 of the 4** —
the supported-versions guard still passes, which is what shows the change only added rejection.

## Consequence for the parity baseline

`language/version/version_unsupported` was removed from `KNOWN_DIVERGENCES`, taking the inventory
from 4 to 3. Only `COMPILED_UNDEFINED_FUNCTION` remains.

## Related

- [B44](B44-stdlib-results-lose-the-exact-numeric-form.md) — the other baseline entry diagnosed in
  the same pass; there too the compiled strategies were right and TEMPLATE was wrong.
- [EB14](EB14-validation-verdict-dropped-on-the-interpreter-paths.md) — the mirror image: a check
  present on the compiled path and missing on the interpreter path. Together they show the invariant
  worth holding — **a gate must be enforced where the decision is made, not where execution happens
  to land.**
- VB05 — established the same principle for the `validate.*` namespace gate: enforce it statically,
  not at evaluation time.
