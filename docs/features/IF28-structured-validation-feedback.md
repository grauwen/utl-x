# IF28: Structured validation feedback — maximize the gradient of the generate-validate-repair loop

**Status:** **Proposed.** A diagnostics / feedback-shaping layer around the existing MC generate→validate→repair
loop. It does not change *what* is generated; it changes *how informatively failures are reported back* to
the LLM.
**Priority:** High — feedback quality is the **rate-limiting factor** of the repair loop. A weak gradient
burns the iteration budget and ends in false success or false failure; a strong one converges in 1–2 passes.
**Created:** June 2026
**Component:** mcp-server (the repair-loop orchestrator + a new **diagnostics builder** that fuses engine
errors with the IF20 analysis) + engine/validator (layered validation surface). IDE surface optional
(show the residual gaps on stall).
**Depends on:** IF20 (correspondence set + inferred functions — the *intent* used to enrich errors),
IF11 (coverage — schema-completeness layer + per-field localization), the UTLX validator/typechecker, the
code generator.

> **One-liner:** The repair loop is conjecture-and-refutation; the feedback is the **gradient**. This makes
> the gradient maximal: validate in **layers**, **localize** each error to a correspondence/output field,
> **enrich** it with intent (the engine's fact + the analysis's meaning), **dedup** to root causes, **re-ground**
> the repair prompt, give the loop **memory**, and stop on a **flat gradient** with a scoped handoff.

See the theory book *Many to One*, ch. 13 §"The Refutation Is a Gradient."

---

## Problem

Current MC AI usage is a loop: prompt → generate UTLX → validate → feed errors into a sharper prompt →
regenerate, to a budget, then succeed or present the best partial. **The model can only repair what the
refutation reveals.** Two failure modes today:

- **Weak validation → false success.** If validation only parses + runs one sample, the loop terminates on
  an *instance-adequate but schema-unsound* mapping (book ch. 3).
- **Weak feedback → no convergence.** "Invalid" / "error at line 42" gives no direction; the loop wanders
  and exhausts the budget.

There is no strategy today that treats **feedback informativeness** as the thing to optimize.

## Precondition 1 — validation coverage (you can't feed back what you don't check)

Validate in ordered layers; the loop converges only to what these can refute:

1. **syntax** — parses?
2. **types** — expressions match contract types?
3. **schema-completeness** — every required output field covered? (IF11)
4. **conformance** — produced output validates against the output schema / USDL?
5. **execution-adequacy** — runs on sample(s) without error?

## Precondition 2 — feedback shaping (the levers)

A `Diagnostic` is the unit; the builder produces a deduped, localized, intent-enriched, layer-ordered list.

| Lever | Rule |
|---|---|
| **layer + fail-fast** | report only the lowest failing layer; suppress cascades from below it |
| **localize** | map every error to a correspondence / output-field path, not a source line |
| **enrich with intent** | engine fact (`expected number, got string`) + analysis meaning (`field is a sum; source typed string, needs cast`) — from IF20 correspondence + IF10/function inference |
| **dedup to root cause** | collapse symptom cascades (one wrong strategy → 50 leaf errors) to the root; **lead with leverage** (structural fix first) |
| **re-ground repair** | repair prompt re-supplies the failing slots' context (correspondence, src/tgt types, inferred function) — not a bare error string (the "preparing beats cold" move, ch. 12) |
| **loop memory** | feed the inter-iteration diff (`fixed 3, broke 1, 2 remain`); never re-propose a rejected fix |

### `Diagnostic` shape (sketch)

```
{
  layer: 'syntax'|'type'|'completeness'|'conformance'|'execution',
  outputPath: 'summary.orderTotal',     // localized to the field
  fact: 'expected number, got string',  // deterministic, from engine
  intent: 'derivation (sum); source `amount` is string → cast needed', // from IF20 analysis
  rootCause: true,                      // vs a suppressed symptom
  leverage: 'structural'|'leaf',
}
```

## Adaptive stop + scoped handoff

- Stop on a **flat gradient**, not a fixed count: if the deduped root-cause error set stops shrinking for
  K iterations, the residue is beyond the loop (genuine gap / missing input / ambiguous contract).
- On stop, present the **residual gaps** (unfilled slots + why each resisted), not the raw partial — a
  scoped, named handoff to a human (ch. 11 "flag the gaps").

## Measurable objective (so this is tunable, not vibes)

- **error-reduction per iteration** (convergence rate)
- **iterations to success or to stall**

Feedback is *good* iff each pass strictly shrinks the root-cause error set. A/B feedback formulations
against this loss; keep the faster-converging one.

## Honest caveats

- **Maximal = maximal signal, not volume.** A 200-line dump / symptom cascade dilutes the gradient. Prefer
  root-cause + localization over completeness.
- **The deepest fix is to need the loop less.** Self-correction is a safety net, not the engine (ch. 11). A
  loop running 10 iterations usually means the *first* proposal was under-prepared (weak analysis / wrong
  strategy / poor scaffold), not that feedback needs more tuning. Track loop length as a health metric on
  the generator, not just the repairer.

## Phasing

- **Phase 1 — layered validation surface** + the convergence metrics (instrument the existing loop; no
  feedback change yet — just measure).
- **Phase 2 — diagnostics builder**: localize, enrich-with-intent, dedup/root-cause, layer-order.
- **Phase 3 — loop intelligence**: re-grounded repair prompts, inter-iteration memory, adaptive stop +
  scoped handoff.

## Out of scope / non-goals

- Not a new generation strategy (that's IF20) — this shapes *feedback*, not proposals.
- Not a correctness substitute: richer feedback speeds convergence to a *validated* mapping; the validate
  gate (ch. 13) still decides success.
