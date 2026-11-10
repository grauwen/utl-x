# EB14: the validation verdict is dropped on every path that falls back to the interpreter

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they call), the
> family of EB01–EB13. This is **EB14**.

**Status:** **FIXED**
**Priority:** High. It removed the verdict from exactly the mappings that exist to produce one, and
it did so silently.
**Component:** `modules/engine` — `TemplateStrategy`, `CompiledStrategy`; with a supporting addition
in `modules/cli` — `TransformationService`.
**Found by:** auditing whether `%utlx 1.1` validation is finished, and checking the strategies
against each other rather than trusting [EF23](../features/EF23-engine-validation-enforcement-and-observability.md)'s
status line.

> **One-line:** `VerdictCheck.of(outputUDM)` ran only where the output still existed as a UDM, so
> `TemplateStrategy` and both interpreter-fallback branches of `CompiledStrategy` returned the
> default `NotInspected` — no verdict at all.

## What was measured

| execution path | verdict before | after |
|---|---|---|
| COMPILED — compiled branch | `Decided` | `Decided` |
| COMPILED — **interpreter fallback** (single input) | **`NotInspected`** | `Decided` |
| COMPILED — **interpreter fallback** (multi input) | **`NotInspected`** | `Decided` |
| COPY — both branches | `Decided` | `Decided` |
| **TEMPLATE** | **`NotInspected`** | `Decided` |

`CompiledStrategy` had four `ExecutionResult(` sites and only two of them set a verdict. The two
that did not are both commented `// ── Fallback: interpreter (same as TEMPLATE) ──`.

## Why this was not an edge case

This is the part that makes it High rather than Medium. `ValidateAcrossStrategiesTest` already
records the decisive fact, in its own header:

> "a namespaced `validate.*` call is NOT compilable, so they fall back to the interpreter"

So **every** validation mapping takes the fallback. The verdict was missing on precisely the
mappings it exists for, on both of the strategies a validation deployment would use. And TEMPLATE is
the interpreter, which is what the CLI runs — so the entire CLI path was verdict-free.

A reader of the code would reasonably conclude the opposite, because the compiled branch *does*
compute a verdict and is the branch you read first.

## Root cause

The verdict has to be taken while the output is still structured. The compiled branch has it:

```kotlin
val outputUDM = transformFunction!!.execute(mapOf(inputName to inputUDM))
...
return ExecutionResult(
    output = outputData,
    // EF23: decide here — this is where the output still exists as a UDM.
    verdict = VerdictCheck.of(outputUDM),
)
```

The fallback and TEMPLATE both go through `TransformationService.transform()`, which returns
`Pair<String, String>` — serialised text and a format name. By the time control returned, the UDM had
been serialised and discarded, so there was nothing left to inspect. The comment above states the
requirement that the other paths could not meet.

## The fix

`TransformationService` already had the output UDM one line before it serialised it. It now hands it
back:

```kotlin
data class TransformOutcome(
    val output: String,
    val outputFormat: String,
    val outputUDM: UDM,
)

fun transformWithUdm(...): TransformOutcome
```

`transform()` delegates to it and drops the UDM, so the two entry points cannot diverge — the same
one-implementation discipline that B37's "now shared" note was reaching for (and that
[B38 §1b](B38-non-finite-numbers-serialise-as-invalid-json.md) shows is easy to get wrong).

The three call sites then inspect the UDM exactly as the compiled branch does.

### Why not re-parse the serialised output

It would have been a smaller change and it is wrong:

- **A round trip is not guaranteed faithful.** B39 (leading zeros), B43 (edge whitespace) and B38
  (non-finite values) are all cases where parse∘serialise is not the identity. A verdict derived from
  re-parsed text could differ from the verdict of the value actually produced.
- **It would make the verdict depend on the output format.** A `ValidationResult` written to CSV does
  not survive as an object at all, so the verdict would exist for some output formats and not others
  — reintroducing the same class of inconsistency one level down.

## Behaviour change

`ExecutionResult.verdict` is now `Decided` or `NoVerdict` on every strategy, never `NotInspected`.
Nothing yet *acts* on it — that is still [EF23](../features/EF23-engine-validation-enforcement-and-observability.md)
— so no routing behaviour changes. What changes is that the verdict is now available and consistent,
which is EF23's precondition.

**No conformance fixture changed:** 563/563, and the full Gradle suite is green (`:engine` 916 tests,
`:cli` 88).

## Tests

`EB14VerdictAcrossStrategiesTest` (`modules/engine`), 5 tests over all three strategies, asserting
the three-state distinction `VerdictCheck` exists to express:

- **no strategy returns `NotInspected`** — for a failing, a passing and a plain mapping;
- a failing validation gives `Decided` + `REJECT` with `failed == 1`;
- a passing validation gives `Decided` + `FORWARD`;
- a **plain** mapping gives `NoVerdict` — we looked, and there was nothing to gate. This is the test
  that proves inspection happened, rather than that a verdict happened to exist;
- the three strategies agree with each other on all three mappings.

Revert-verified separately for each half:

| reverted | failures |
|---|---|
| `TemplateStrategy` | **5 of 5** |
| `CompiledStrategy` fallbacks | **4 of 5** |

The one test that survives the `CompiledStrategy` revert is the plain-mapping case — because a plain
`%utlx 1.0` mapping *is* compilable, so it takes the compiled branch, which always had the verdict.
That asymmetry is the mechanism, confirmed from the outside.

## Related

- [EF23](../features/EF23-engine-validation-enforcement-and-observability.md) — the enforcement half.
  Its status line said the enforce component had "zero call sites"; it had four, and two of them were
  wrong. Corrected there.
- EB03–EB13 — the same shape: a silent fallback whose behaviour differs from the path it replaces.
  The cross-strategy conformance harness cannot catch this one, because it compares **output**, and
  the output is identical; only the result *metadata* differed.
- [F21](../features/F21-utlx-1-1-semantic-validation.md) — the language half, which is complete.
