# UTLXe — Validation Enforcement & Observability

*Working document, Glomidco B.V. · 2026-10 · v1.0 · Status: current-state analysis + target design. Describes the engine runtime, not the language.*

| Field | Value |
|---|---|
| Document | utlxe-validation-enforcement-and-observability |
| Status | Current state verified against the code at `%utlx 1.1` / VB05; target design proposed, not implemented |
| Scope | What the engine does with a **validation verdict** at runtime: enforcement (forward vs reject), logging, metrics, dead-letter. Covers both validation systems and how they relate. |
| Out of scope | *Script* validation (parsing/semantic checks on `.utlx` source) — see `VALIDATION_GAPS.md`, `VALIDATION_ENHANCEMENT_PLAN.md`. The `validate.*` language surface — see `../language-guide/validation-rules.md`. |
| Feature doc | [`EF23`](../features/EF23-engine-validation-enforcement-and-observability.md) — this document is its design rationale; EF23 carries the scope, acceptance criteria and the decisions required |
| Related | `EF02-engine-schema-validation.md`, `EF12-runtime-logging-and-diagnostics.md`, `EF14-opentelemetry-tracing.md`, `EF19-health-response-enrichment.md`, `utlx-guard/docs/architecture/civilian-guard.md`, `../proposals/utlx-validate-naming-and-guard-profile.md`, `../bugs-fixed/VB04-…`, `../bugs-fixed/VB05-…` |

> **One line:** a `validate.*` verdict is currently just *output* — nothing enforces it, nothing logs it, and the existing counters classify a rejection as a **successful execution**, so a guard rejecting every payload reports itself healthy.

---

## 1. Why this document exists

A guard is only a guard if a rejection is (a) acted on, (b) visible, and (c) countable. The language
side of `%utlx 1.1` is complete and tested — `validate.*` produces a correct, fail-closed verdict.
The *runtime* side is not: the component that turns a verdict into a routing decision exists but is
connected to nothing, and no part of the observability stack knows what a verdict is.

This document records the verified current state, names the gaps in priority order, and proposes the
target design, so the remaining work is a sequence of decisions rather than a rediscovery.

---

## 2. There are two validation systems, and only one is wired

This is the most common source of confusion, so it comes first.

| | **Schema validation** | **Semantic validation** |
|---|---|---|
| What it checks | payload conforms to XSD / JSON Schema | business rules expressed in the script |
| Where it lives | `:engine` `validation/ValidationOrchestrator.kt` | `:stdlib` `validate/ValidateFunctions.kt` + `:core` interpreter |
| When it runs | pre-transform (inputs) and post-transform (output) | *inside* the transformation, as part of the mapping |
| Feature doc | `EF02-engine-schema-validation.md` | `../language-guide/validation-rules.md` |
| Result shape | `ExecutionResult.validationErrors` | a `ValidationResult` **as the transformation output** |
| Policy vocabulary | `FAIL` / `WARN` / `OFF` / `SKIP` (`TransformConfig.validationPolicy`, **default `SKIP`**), with a runtime override via the admin API | `maxErrors` / `maxWarnings` budgets (`routing/ValidationRoutingPolicy.kt`) |
| Enforced? | **Yes** — `FAIL` aborts, `WARN` annotates the response | **No** — see §4 |
| Surfaced per request? | Yes — every transport maps `validationErrors` into its response | No — the verdict is indistinguishable from ordinary output |

**Important correction to the obvious reading.** The schema side is *enforced* and *surfaced per
request*, but it has no validation metrics and no error routing either:
[EF02](../features/EF02-engine-schema-validation.md) carries its own accuracy note saying
"§4 metrics, §5 error routing … remain **design** (not verified as implemented here)". So this is not
"semantic validation lagging behind schema validation" — **neither system has a counter, a log line,
or a dead-letter path.** They are missing the *same* layer, which is the main argument for designing
it once rather than twice (§7).

Two further consequences worth stating plainly:

1. **The wired system is off by default** (`validationPolicy = "SKIP"`), and the unwired system
   defaults to the strictest posture (`FAIL_CLOSED`, budgets `0/0`). An operator reading only the
   defaults would draw the wrong conclusion about either.
2. **The two policy vocabularies are unrelated.** A switch (`FAIL`/`WARN`/`OFF`) versus per-severity
   budgets. Before both are live, this should either converge or be documented as a deliberate split
   (§7, open question 1).

---

## 3. The decide / enforce / lock model

The `%utlx 1.1` design deliberately separates three concerns (spec §8; see the KDoc on
`ValidationRoutingPolicy`):

```
DECIDE    the script      validate.* produces a ValidationResult; valid = (failed == 0), no thresholds
ENFORCE   the engine      forward iff failed <= maxErrors AND warnings <= maxWarnings
LOCK      deployment      a guard profile pins the budgets (0/0) so a script cannot loosen them
```

The split is what keeps `validate.*` **pure** — the functions report counts and never apply a
threshold, so their output is deterministic and reproducible regardless of deployment. The judgement
about *how strict this deployment is* belongs one level up, in the same configuration family as the
parse profile.

Two properties follow, and both are already true in code:

- **Notices never gate.** They are audit/observability only.
- **Non-validation output forwards untouched.** `isValidationResult()` tests for the verdict shape,
  so the policy governs validation components and never ordinary mappings.

`%utlx 1.1` has DECIDE complete and proven. ENFORCE is built and unit-tested but unreachable. LOCK
does not exist yet.

---

## 4. Current state: what happens to a failed transformation

**It is forwarded, exactly like a passing one.**

A `validate.*` script returning `valid:false` has *executed successfully* — it produced output. The
engine's success/error classification is about execution, not verdicts:

```kotlin
// transport/TransportHandlers.kt — the execute path
instance.recordExecution()                            // always
val result = ValidationOrchestrator.execute(instance, payloadBytes, policyOverride)
if (!result.success) { instance.recordError() }        // only on exception / transform failure
```

A verdict of `valid:false` never reaches `recordError()`. The ValidationResult JSON is then published
to whatever output binding resolves (env override > messaging config > legacy `outputBinding` >
header > none), the same as any other output.

**And the enforcement component is connected to nothing.** Searching the tree for callers of
`ValidationRoutingPolicy` returns only its own unit test — zero production call sites. The
fail-closed policy verified in Phase 4 is dead code awaiting a caller.

So today a "guard" built on `validate.*` is **advisory**. The only way a verdict changes behaviour is
if the *script itself* branches on it and a downstream system interprets the resulting envelope:

```utlx
let v = $input |> validate.required(["orderId", "lineItems"])
in if (v.valid) { status: "forward", payload: v.payload }
   else { status: "dead-letter", failed: v.failed, issues: v.issues }
```

That pattern works and is covered by conformance (`routing_in_script.yaml`), but it pushes an
engine-level policy decision into every script — the opposite of the decide/enforce/lock intent, and
not lockable by a deployment.

---

## 5. Current state: observability

### 5.1 What exists

Real infrastructure, none of it verdict-aware:

| Surface | Carries |
|---|---|
| `GET /metrics` (Prometheus, `health/MetricsCollector.kt`) | `utlxe_up`, `utlxe_uptime_seconds`, `utlxe_transformations_loaded`, `utlxe_executions_total`, `utlxe_errors_total`, and per-transformation `utlxe_transformation_executions_total{transformation,strategy}` / `…_errors_total{…}` |
| Admin API (`admin/AdminEndpoint.kt`) | `messages_processed`, `errors`, `total_errors` — the same two `AtomicLong`s |
| OTel spans (`telemetry/Tracing.kt`, EF14) | `addTransformAttributes(id, strategy, size, messageId, correlationId)`; `recordResult(outputLength, durationUs, error)` |
| Admin log ring buffer (EF12) | level control, search by MessageId, tail since timestamp |

`MetricsCollector` states its design rule explicitly: *"All metrics are derived from existing
AtomicLong counters … no additional instrumentation in the hot path."* Any new counter should respect
that.

### 5.2 The blind spot

`errorCount` counts exceptions. A rejection is not an exception. Therefore:

> **A guard rejecting 100% of payloads shows `utlxe_executions_total` climbing and
> `utlxe_errors_total` flat — i.e. perfect health.** The failure would be noticed downstream, from
> the absence of expected data, not from the engine.

Equally, there is **no log line** for a verdict. The engine logs errors; a rejection is not one, so it
passes silently. `recordResult` puts output length, duration and error on the span — a verdict is
none of those, so the trace carries no evidence either.

`ValidationRoutingPolicy.Decision` already carries `outcome`, `reason`, `failed`, `warnings` and
`notices`. It was designed to be logged and counted, and has no consumer.

---

## 6. Gaps, in priority order

| # | Gap | Severity | Why |
|---|---|---|---|
| 1 | `ValidationRoutingPolicy` has no call site | **High** | Without it there is no enforcement at all; everything below depends on it |
| 2 | No verdict counters | **High** | A rejecting guard is indistinguishable from a healthy one |
| 3 | No verdict logging or span attributes | **High** | A rejection leaves no forensic trace; incident response has nothing to read |
| 4 | No LOCK layer (guard profile pinning budgets) | Medium | A deployment cannot prevent a looser posture |
| 5 | No dead-letter sink | Medium | Rejected payloads have nowhere to go; infra-dependent |
| 6 | Two unrelated policy vocabularies | Medium | Operator-facing confusion once both are live |
| 7 | Verdict absent from the per-request response | Low–Medium | Schema errors are surfaced per request; semantic verdicts are not |

Gaps 1–3 are **not** infra-blocked. They are inside `:engine` and testable with the existing strategy
tests. Gap 5 is the one that genuinely needs messaging infrastructure — and it is correctly *last*:
decide, count, log, then route.

---

## 7. Target design

### 7.1 Enforcement — one call site

After execution, if the output is a `ValidationResult`, run the policy and act on the `Decision`:

```
execute() ─► output UDM
              │
              ├─ isValidationResult(output) == false ──► forward (ordinary mapping, untouched)
              │
              └─ true ──► policy.decide(output) ──► Decision
                                                     ├─ FORWARD ─► output binding
                                                     └─ REJECT  ─► dead-letter sink (§7.4)
                                                                   + counter + log + span
```

The policy must stay pure and transport-agnostic: it returns a `Decision`, it does not perform I/O.
The call site owns the side effects.

### 7.2 Counters

**Do not invent a parallel vocabulary.** [EF02 §4](../features/EF02-engine-schema-validation.md)
already designs validation counters, and they are unimplemented, so the two should land as one set:

```
# EF02 §4, as designed (schema validation)
utlxe_input_validation_total{pipeline,result="pass|fail|skip"}
utlxe_output_validation_total{pipeline,result="pass|fail|skip"}
utlxe_input_validation_errors_total{pipeline,error_type}

# proposed extension for semantic verdicts, same shape
utlxe_semantic_validation_total{pipeline,result="forward|reject"}
utlxe_semantic_validation_issues_total{pipeline,severity="error|warning|notice"}
```

Both are derivable from the `Decision` with no hot-path work, satisfying `MetricsCollector`'s stated
rule. EF02's own framing applies unchanged and is worth repeating: the distinction between *input*
validation failures (upstream data quality), *output* validation failures (a bug in the `.utlx`) and
*transformation* errors (possibly transient) "is critical for alerting". A semantic rejection belongs
with the first: a data-quality signal, not an engine fault.

> **Label discrepancy to resolve first.** EF02's design uses `pipeline=`; the **shipped**
> `MetricsCollector` uses `transformation=` (and `strategy=`). One of the two must give before any
> new counter is added, or dashboards will have to join on two different label names.

### 7.3 Logging and tracing

- **Log** a rejection at `WARN` (not `ERROR` — a rejection is the guard working correctly), carrying
  correlation/message id, transformation id, `Decision.reason`, and the failing rule ids. Rule ids
  matter: `issues[].rule` is what makes a rejection diagnosable without the payload, which is
  important when the payload cannot be logged for data-protection reasons.
- **Span attributes** on the existing transform span: outcome, failed/warnings/notices counts. This
  is an extension of `Tracing.recordResult`, which today has no verdict parameter.
- Keep payload content out of logs by default; the issue list is designed to be sufficient.

### 7.4 Dead-letter

Rejected payloads need a sink. There is no `deadLetter`/`dlq` concept in the engine today — output
resolution ends at a single output binding. The natural shape is a second, optional binding resolved
by the same precedence chain, so a rejection is a *publish to a different destination* rather than a
new transport mechanism. [EF02 §5](../features/EF02-engine-schema-validation.md) already proposes the
config surface for this (`errorRouting.inputValidationFailure: dead-letter`, etc.), so semantic
rejections should extend that block rather than add a separate one. This is the infra-dependent step
and should follow the EF-series messaging work.

### 7.5 Configuration and the LOCK layer

Budgets need an operator-facing home alongside `TransformConfig.validationPolicy`, and a guard
profile must be able to pin them so a transformation cannot loosen them — the same shape as the parse
profile. See `../proposals/utlx-validate-naming-and-guard-profile.md` for the guard-profile tiering.

---

## 8. How the recent language-side fixes interact

Three of the `%utlx 1.1` fixes change what the engine will see, and the enforcement design should
assume them:

- **VB04** — a malformed rule (wrong arity, non-array argument, unparseable regex) now yields an
  `error`-severity issue instead of a silent pass. With budgets `0/0` that means **a typo in a rule
  rejects the payload** rather than disabling the rule. That is the correct fail-closed direction,
  and it makes gap 2 more urgent: without counters, a deployment-wide rule typo looks like a sudden
  unexplained drop in downstream volume.
- **VB05** — the `%utlx` namespace gate is now static, so a script using 1.1 features under a 1.0
  header fails at **load**, not per payload. Enforcement never has to consider that case at runtime.
- **VB01/VB02** — a check or guard that cannot be evaluated is a reported `error` issue, never a
  crash and never a skip. So the engine can rely on *always* receiving a well-formed
  `ValidationResult` from a `validate.*` transformation, rather than having to handle an aborted
  transform as a third outcome.

---

## 9. Open questions

1. **Do the two policy vocabularies converge?** Either express semantic enforcement as
   `FAIL`/`WARN`/`OFF` for consistency with schema validation (losing per-severity budgets), or keep
   budgets and document the split. Recommendation: keep budgets — they are strictly more expressive
   and the guard case needs them — and make the schema policy a documented special case of them.
2. **Should a rejection be a non-success response, or a success with a verdict?** It affects every
   transport's response mapping and any caller's retry logic. *Largely already answered:*
   [EF02 §5](../features/EF02-engine-schema-validation.md) classifies an input-validation failure as
   **not retryable — "dead-letter immediately (bad data won't fix itself)"**, versus a transformation
   runtime error which may be retried. A semantic rejection is the same category, and
   `ErrorClass.PERMANENT` already exists in the proto. What remains open is only the narrower
   question of whether the transport reports `success=false` or `success=true` with a verdict field.
3. **Is the verdict added to `ExecutionResult`?** Doing so would let every transport surface it the
   way `validationErrors` already is (gap 7), at the cost of widening a core type.
4. **Who owns the budgets — transformation or deployment?** The LOCK layer implies deployment wins,
   but a per-transformation default is still useful. Precedence should mirror EF02's
   (config > header, runtime override trumps all).

---

## 10. Verification status of the claims in this document

Everything in §2–§5 was read from the code at the time of writing, not inferred from design docs:
call-site searches for `ValidationRoutingPolicy` and `recordError`, the `MetricsCollector` metric
list, `Tracing.recordResult`'s signature, `TransformConfig.validationPolicy`'s default, and the output
binding resolution chain in `HttpTransport`. The one claim **not** demonstrated on a running engine is
the §5.2 conclusion that a rejecting guard reports as healthy — it follows unambiguously from the
`recordError()` condition, but it has not been observed against a deployed instance. Doing so would
be a worthwhile confirmation before relying on it in an assurance argument.
