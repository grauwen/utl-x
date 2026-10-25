# EF23: Engine Validation Enforcement & Observability (UTLXe Adaptation for F21)

**Status:** Design — **not implemented**. The decide layer (F21) is complete; the enforce component exists but has **zero call sites**.
**Priority:** High — a guard whose rejections are neither acted on nor counted is not a guard.
**Created:** October 2026
**Depends on:** [F21](F21-utlx-1-1-semantic-validation.md) (`validate.*` + the `%utlx` namespace gate)
**Overlaps / supersedes:** [EF02](EF02-engine-schema-validation.md) §4 (metrics) and §5 (error routing) — both still design; EF23 and those sections describe **one** layer and must land together
**Design rationale:** [`../architecture/utlxe-validation-enforcement-and-observability.md`](../architecture/utlxe-validation-enforcement-and-observability.md) — current-state analysis, verified against the code

---

## Summary

F21 gave the language a correct, fail-closed verdict: `validate.*` returns a `ValidationResult`
(`valid`, `passed`, `failed`, `warnings`, `notices`, `issues[]`, `payload`) and never applies a
threshold. EF23 is the engine half — turning that verdict into a routing decision, and making the
decision visible.

What exists today, verified:

| Layer | Component | State |
|---|---|---|
| **Decide** | `validate.*` (`:stdlib` + `:core`) | ✅ complete, 543/543 conformance |
| **Enforce** | `engine/routing/ValidationRoutingPolicy.kt` | ⚠️ built and unit-tested, **no production caller** |
| **Observe** | counters / logs / spans | ❌ nothing is verdict-aware |
| **Route** | dead-letter sink | ❌ no `deadLetter`/`dlq` concept in the engine |
| **Lock** | guard profile pinning budgets | ❌ not started |

Consequence, and the reason this is High: a `validate.*` verdict of `valid:false` is an ordinary
successful execution. It is forwarded like any other output, `recordError()` is never reached, and
**a guard rejecting every payload reports itself healthy.**

## Scope

1. **Wire the enforcement call site** — after execution, if the output is a `ValidationResult`, run
   `ValidationRoutingPolicy.decide` and act on the `Decision`. The policy stays pure and
   transport-agnostic; the call site owns the side effects.
2. **Counters** — extend EF02 §4's design to semantic verdicts, same shape, one metric set.
3. **Logging and tracing** — `WARN` on rejection (the guard working correctly is not an `ERROR`),
   carrying correlation id, transformation id, `Decision.reason` and the failing rule ids; verdict
   counts as span attributes. Rule ids rather than payload content, so a rejection stays diagnosable
   where the payload cannot be logged for data-protection reasons.
4. **Budgets in configuration** — an operator-facing home for `maxErrors` / `maxWarnings` alongside
   `TransformConfig.validationPolicy`.
5. **Dead-letter routing** — extend EF02 §5's `errorRouting` block rather than adding a parallel one.
6. **Lock** — a guard profile that pins the budgets so a transformation cannot loosen them, in the
   same configuration family as the parse profile ([F23](F23-parser-strictness-profiles.md)).

Items 1–3 are **not infra-blocked**: they are inside `:engine` and testable with the existing
strategy tests. Item 5 is the infra-dependent one and is deliberately last — decide, count, log,
*then* route.

## Not in scope

- The `validate.*` language surface — that is F21, complete.
- *Script* validation (source-level checks): `../architecture/VALIDATION_GAPS.md`.
- Schema validation's own enforcement, which is already wired (`FAIL` aborts, `WARN` annotates) —
  EF23 only shares its **metrics and routing** layer.

## Acceptance criteria

1. A transformation whose output is a `ValidationResult` with `failed > maxErrors` is **not**
   forwarded to the normal output binding.
2. Output that is not a `ValidationResult` is forwarded unchanged — a plain mapping is never gated
   (already guaranteed by `isValidationResult()`; must stay true end-to-end).
3. `GET /metrics` distinguishes forwarded from rejected, per transformation.
4. A rejection produces exactly one `WARN` log line carrying the correlation id and the failing rule
   ids, and no payload content by default.
5. A rejection is classified **non-retryable** (EF02 §5: "bad data won't fix itself").
6. Budgets are settable per transformation and pinnable by a deployment profile; a script cannot
   loosen a pinned budget.
7. Cross-strategy: the decision is identical on COPY, TEMPLATE and COMPILED — extend
   `ValidateAcrossStrategiesTest`.

## Blocker found 2026-10-24: there is no single point that holds the output UDM

`ValidationRoutingPolicy.decide` takes a `UDM`. By the time `TransportHandlers` has an
`ExecutionResult`, `output` is **already a serialized String**. So item 1 cannot simply be "add a
call after execution" — the verdict has to be read where the output is still a UDM, and the
strategies do not agree on where that is:

| Strategy | Holds the output UDM? | How it serializes |
|---|---|---|
| `CompiledStrategy` | yes, 2 sites | explicit `transformationService.serializeOutputPublic(outputUDM, …)` |
| `CopyStrategy` | yes, 2 sites | same |
| `TemplateStrategy` | **no** | `transformationService.transform(...)` returns output **already serialized** |

So there is no choke point. `serializeOutputPublic` is the closest thing — four of the six sites go
through it — but `TemplateStrategy` bypasses it entirely, and a serializer is the wrong home for a
routing policy anyway. (Worth noting in passing: `serializeOutputPublic` lives in
`modules/cli/.../TransformationService.kt`, so the engine reaches into the CLI module for it.)

**Why this matters more than it looks.** Wiring the policy only at the sites that happen to hold a
UDM means `TemplateStrategy` silently does not enforce — fail-open **by omission**. That is the exact
failure shape that has now occurred three times in this feature area
([VB04](../bugs-fixed/VB04-named-validators-fail-open-on-malformed-arguments.md) malformed arguments,
[VB06](../bugs-fixed/VB06-chained-validators-fail-open.md) chained validators,
[F24](F24-quantified-validation-rules.md) §fail-open), each time because an enumeration of call sites
or rule keys was incomplete and the default consequence of the omission was "let it through".

### Options

- **(a) Widen `ExecutionResult`** to carry the output UDM (or a `Decision` computed by one shared
  helper the strategies call), and decide **once** in `ValidationOrchestrator`. N small strategy
  changes, but only one place *decides*, so an omission shows up as a missing verdict rather than as
  silent forwarding. **Recommended.**
- **(b) Expose the UDM from `TransformationService.transform`** so `TemplateStrategy` can hold it
  too, then decide at a single post-execution point. Cleanest conceptually; touches the
  CLI-module service the engine depends on.
- **(c) Decide inside `serializeOutputPublic`** — the nearest existing funnel, but it misses
  `TemplateStrategy` and puts policy in a serializer. Not recommended.
- **(d) Re-parse the serialized output** in `TransportHandlers` — uniform and structurally free, but
  it pays a parse per payload to recover information that was just thrown away. Not recommended.

### The guard case breaks "infer the verdict from the output" — added 2026-10-25

Reading the guard design (`../guard/utlx-guard-validate-subset-and-compiler.md`, imported 2026-10-25)
shows that every option above shares a hidden assumption: that the transformation's **output is the
verdict**. For the guard it is not.

The A-side pipeline is:

```
parse source → UDM → validate.* (A) → strip → serialise simple form
```

The output is the **stripped simple form** — the thing to emit, BINF-framed, for the FPGA's `out_r`
port. The verdict travels on a *separate* port (`verdict`: a pass bit, an ICD reason code, and a
failed-rule bitmask). So:

- `VerdictCheck.of(outputUDM)` would inspect the stripped payload, find no `ValidationResult` shape,
  and return **`NoVerdict`** — a *wrong* answer, not merely an absent one. The verdict would be lost
  while the engine reported "nothing to gate".
- Only `NotInspected` being the **default** keeps this from being another silent fail-open: a
  strategy that never looks is recorded as unknown. But a strategy that looks at the wrong thing
  still produces a confident `NoVerdict`.

So inferring the verdict from the output works for the in-script envelope pattern
(`routing_in_script.yaml`, where the script returns `{status, payload, issues}`) and **not** for a
validate-then-strip-then-emit guard, which is the shape the product actually needs.

### Option (e): capture the verdict where validation ran

The verdict has to be taken from `validate.*` itself — a side channel out of the interpreter — rather
than recovered from whatever the script happened to return. Implications:

- `:core` records the `ValidationResult`(s) produced during one execution; the engine reads them from
  the execution context instead of from the output.
- It composes with the guard pipeline: the output stays a pure payload (as `out_r` wants), and the
  verdict is out-of-band (as `verdict` wants).
- It answers "which rules failed" directly from `issues[].rule`, which the guard needs anyway to
  compare against the hardware bitmask — their proof strategy is *"compare `valid` **and** the set of
  failing rule IDs with the hardware bitmask"*.
- Cost: it is more invasive than (a)–(d), touches `:core`, and raises a question none of the others
  do — what if a script calls `validate.*` more than once? (Several results; the guard's A-side
  policy is one `assert` call, but nothing enforces that.)

**This is now the recommended direction**, superseding (a). Options (a)–(d) remain valid for the
UTLXe/envelope case, but a design that only works when the output *is* the verdict cannot serve the
guard, and the guard is why `validate.*` exists.

### Non-negotiable, whichever option wins

**"I could not obtain the output UDM" must not mean "forward".** The policy already distinguishes
*not a ValidationResult* (→ forward, correct for a plain mapping) from *a failing verdict* (→
reject). A third state — *could not tell* — must be explicit and fail-closed for a validation
component, or the wiring reintroduces the bug class it exists to prevent.

This becomes **decision 0**: it gates item 1, and items 2 and 3 depend on having a `Decision` to
count and log.

## Decisions required before implementation

These are open in the design rationale (§9 there) and block parts of the scope:

| # | Question | Blocks |
|---|---|---|
| 0 | Where does the verdict get read, given no strategy-independent point holds the output UDM? (§Blocker) | items 1, 2, 3 |
| 1 | Do the two policy vocabularies converge — `FAIL`/`WARN`/`OFF`/`SKIP` vs `maxErrors`/`maxWarnings` budgets? | item 4 |
| 2 | `pipeline=` (EF02 §4 design) or `transformation=` (shipped `MetricsCollector`) as the metric label? | item 2 |
| 3 | Does a rejection report `success=false`, or `success=true` with a verdict field? | items 1, 3 |
| 4 | Does the verdict join `ExecutionResult` so every transport surfaces it like `validationErrors`? | item 1 |
| 5 | Budget precedence — transformation vs deployment (mirror EF02's config > header, runtime override on top?) | items 4, 6 |

Decision 2 is the cheapest and should be taken first: EF02's design and the shipped metrics disagree
on the label name, and adding counters before resolving it means dashboards joining on two names.

## Interaction with the VB-series fixes

- **[VB04](../bugs-fixed/VB04-named-validators-fail-open-on-malformed-arguments.md)** — a malformed
  rule now yields an `error` issue instead of a silent pass. Under budgets `0/0` a **rule typo
  rejects the payload**. Correct, but it raises the stakes on item 2: without counters, a
  deployment-wide typo presents only as an unexplained drop in downstream volume.
- **[VB05](../bugs-fixed/VB05-version-gate-is-evaluation-time-not-static.md)** — the namespace gate is
  static, so a 1.0 script using 1.1 features fails at load. Enforcement never meets that case at
  runtime.
- **[VB01](../bugs-fixed/VB01-validate-assert-raw-check-crashes-on-missing-or-mistyped-data.md) /
  [VB02](../bugs-fixed/VB02-validate-assert-lambda-when-guard-silently-ignored.md)** — a check or
  guard that cannot be evaluated is a reported `error` issue, never a crash and never a skip. So the
  engine can rely on always receiving a well-formed `ValidationResult`, and does not need an
  "aborted transform" third outcome.
