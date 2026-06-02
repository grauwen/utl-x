# IF11: IDE — Message Contract Mode AI Assist (coverage analysis + archetype-guided mapping)

**Status:** In progress.
- **Phase 1 — deterministic coverage analysis: implemented.** `utils/coverage.ts`
  (`buildCoverage` / `buildContractCoverage` / `formatCoverage`) classifies every output
  leaf **direct / derivable / gap** by name + type matching (exact normalized, then fuzzy
  substring) and aggregates required gaps into the **delta**. Surfaced in the AI dialog
  in MC mode only: a **Contract coverage** panel (summary counts + the delta headline +
  an expandable per-field list) computed on dialog open from the input schemas + the
  output panel's expected schema. No AI.
- **Phase 1 — LLM gap refinement: implemented.** Opt-in **"✨ Refine gaps (AI)"**
  button in the coverage panel (shown only when there are gaps and an LLM is available).
  Sends the deterministic gaps + the flattened input fields to the new MCP tool
  **`refine_coverage`** (`mcp-server/src/tools/refineCoverage.ts`), which returns a
  per-gap semantic suggestion (`direct` / `derivable` / `unmappable`, with source +
  optional derivation hint + rationale). Suggestions merge back via
  `mergeCoverageSuggestions` — resolved gaps become ✨-marked direct/derivable rows and
  leave the delta; the LLM may only reference supplied input fields (no invented
  sources). One call per dialog session; graceful when no LLM. Mirrors the IF10
  Explain-AI pattern, keeping the LLM confined to AI assist.
- **Phase 1 — MC generation (constrained synthesis): implemented.** The MC prompt is no
  longer a stub: `message-contract-prompt.ts` builds a constrained-synthesis prompt from
  the **output contract schema** (fixed target) + each **input schema** + the **coverage
  plan** (output field → source / gap). `generate_utlx_from_prompt` now flows MC mode
  through the same pipeline; with schema inputs and no instance data the loop
  **validates-only** (no execution) — the correct design-time behavior. The model fills
  mapped fields from their source and emits a **typed placeholder + `// TODO`** for each
  gap (never fabricating data). New request fields (`outputSchema`, per-input `schema`,
  `coverage`) thread frontend → service → MCP.
- **MCM UX — "map it" by default, prompt optional: implemented.** In Message Contract
  mode the prompt is **optional** (the goal is fixed: map inputs → contract). The primary
  button reads **"✨ Map to output contract"** and is enabled with an empty prompt; the
  label/placeholder reframe the prompt as optional guidance (defaults for gaps, lookups).
  Execution mode is unchanged (prompt still required).
- **Pending (Phase 1 cont.):** richer structural coverage hints for nested/array mappings.
- **Pending:** Phase 2 (archetype matrix + function kits), Phase 3 (schema-conformance +
  synthetic round-trip).
**Priority:** High (fills the IF08 Message Contract stub; the integration sweet spot)
**Created:** June 2026
**Depends on:** IF08 (mode-aware AI assist; `message-contract-prompt.ts` stub), IF10 (per-input abstract / UDM + schema walks), daemon validate/execute, schema field-tree parsers, the 650+ stdlib functions
**Effort:** Large (phased; coverage analysis is the first, standalone slice)

> **Design decisions captured here (not yet implemented):**
> - MC-mode generation is **constrained synthesis**: the output is a **predefined
>   contract** (schema), so the task is "source every output element from the
>   inputs," XSLT/scaffolding-like but stricter — not open generation.
> - **Coverage analysis first.** Before generating, classify each output element as
>   **direct / derivable / GAP**; the gaps are a **delta** = what's missing (→ an
>   extra input, a lookup table, or a default). Deterministic-first, LLM for semantic
>   matches. It grounds generation (prevents hallucinated sources) and is a valuable
>   standalone deliverable.
> - **Archetype matrix as a recursive per-node prior.** Classify each output subtree
>   by (sourceShape, targetShape, cardinality) → a finite set of mapping archetypes →
>   a focused **function kit** (collapsing the 650-function problem). A prior/candidate
>   set, NOT a global oracle — mappings are compositions of per-node cells.
> - **A different test:** no sample data, so validation = coverage + schema-conformance
>   + an optional synthetic round-trip (generate data from the input schema → run →
>   validate output against the output schema).
> - Built on IF10's walks, extended to the **output** schema.

---

## Summary

In Message Contract mode the IDE maps **input schema(s) → a predefined output schema**
(design-time; runtime is instance→instance). That is a fundamentally different AI task
from Execution mode (which invents the output from sample data): the output is fixed by
the contract, there is no sample data, and the hard integration problem is that **inputs
often don't fully satisfy the target** (a lookup/extra input is needed). IF11 fills the
IF08 Message-Contract stub with: (1) **coverage analysis** that reports a source for
every output element and a **delta** of what's missing; (2) **archetype-guided mapping**
that classifies each output subtree and surfaces the right **function kit**; and (3) a
**schema-based test** (no sample data required).

## Problem

IF08 stubs MC-mode generation ("not implemented"). Naively reusing the Execution prompt
fails because:
- **No sample data** — can't validate by executing against a sample.
- **The output is predefined** — the model must hit an exact contract (required fields,
  types, enums), not invent a shape. Open generation overshoots/undershoots it.
- **Inputs rarely fit the output 1:1** — the real integration pain is the missing
  pieces (e.g. you have `currencyCode` but the contract wants `currencyName` → needs a
  lookup). Without surfacing this, the AI **hallucinates sources** for fields it can't map.
- **650+ functions, few used** — for a predefined output you need the right functions
  (often nested/combined), and "which of 650, when?" is unguided.

## Goals

- A **coverage report**: per output element → `direct | derivable | GAP`, with the
  source(s) identified; gaps aggregated into a **delta** (suggested lookup / extra input
  / default). Usable standalone, before any generation.
- **Archetype-guided generation**: classify each output subtree → archetype → focused
  function kit → generate a mapping that fills every output slot from its source.
- **Schema-based validation** that needs no user-supplied data.
- Fill the `message-contract-prompt.ts` builder (IF08) on this basis.

## Non-Goals

- Inventing output structure — the contract is fixed (that's Execution mode).
- A deterministic "solver" — coverage's *derivable* detection and the archetype matrix
  are **priors/candidates**, refined by the LLM and correctable by the user.
- Perfect synthetic data — round-trip testing is best-effort, not exhaustive.

## Design

### Constrained synthesis (predefined output)

The transformation is still UTLX (header + body), but the prompt frames it as: *"produce
EXACTLY this output schema; source each field from the inputs; do not add or omit
contract fields."* The fixed target bounds the search space and raises quality — provided
we know what can be sourced (coverage) and how (archetype).

### Coverage analysis + gap/delta (build this first)

For each output element (esp. required), find candidate source(s) across the inputs:

```
Coverage of OutputContract (12 fields, 9 required)
  ✓ direct      orderId ← Order.id ; customer ← Order.customer.name
  ~ derivable   fullName ← firstName + lastName ; total ← sum(lines[].amount)
  ✗ GAP         currencyName  (have currencyCode — needs a lookup)
  ✗ GAP         region        (no source — default/constant or extra input)
Delta (unsatisfiable from current inputs): currencyName, region
```

- **Deterministic first:** exact/fuzzy name match, type compatibility, structural
  alignment (array↔array, object↔object) from the IF10 walks.
- **LLM refinement:** semantic matches (`firstName+lastName→fullName`,
  `code→description` via lookup) and gap explanations.
- **The delta** = required output fields with no source → "this contract can't be fully
  satisfied with these inputs; add X." A design-time answer *before* writing code, and
  the grounding that stops the model inventing sources.

### Archetype matrix — a recursive per-node prior

A finite set of mapping **archetypes** (well-studied in schema-matching / model
transformation / ETL):

| copy/rename | restructure | project | flatten | nest | map-elements | filter | aggregate/group | join/lookup | pivot | split | merge | derive/compute | constant/default |

Classify each **output subtree** by (sourceShape, targetShape, cardinality):

| source → target | archetype |
|---|---|
| `A[obj] → A[obj]` (1:1) | map-elements |
| `A[obj] → obj` (N:1) | aggregate / reduce |
| `A[obj] + A[obj]·lookup → A[obj]` (N:M) | join / lookup |
| `nested obj → flat obj` | flatten |
| `flat → nested` | nest / group |
| `obj → A` | split |
| `S → S` (transformed) | derive / compute |
| (no source) | constant / default |

**Two honest caveats:** a cell yields **candidate** archetypes, not one answer (semantics
disambiguate — `A[obj]→obj` could be aggregate / take-first / pivot); and real mappings
are **compositions** — classify per output node and compose into a *plan*, not one global
label. So the matrix is **finite at the node level**, applied recursively.

### Function kits (collapse the 650-function problem)

Each archetype has a characteristic function set; surface only those (plus common
compositions / functions-in-functions), not all 650:

- map-elements → `map`, field access
- aggregate/group → `mapGroups`, `sumBy`, `count`, `reduce`
- join/lookup → index via `reduce` → `map` with lookup
- flatten → `flatMap`; format/derive → the string/date/number bulk
- filter → `filter`

This is where the unused ~600 functions start getting used *appropriately*.

### Validation — the "different test" (no sample data)

Three layers, weakest → strongest:
1. **Coverage as test** — every *required* output field has a source.
2. **Schema-conformance** — compile + statically check the produced mapping's output
   structure against the output schema (required present, types/enums compatible).
3. **Synthetic round-trip** — generate sample instance(s) from the input schema → run the
   transformation (daemon execute) → validate the output instance against the output
   schema. A real end-to-end test with no user-supplied data.

### Pipeline

```
walk input schema(s) + output schema (IF10 walks, extended to output)
        │
        ▼
COVERAGE → per output field: direct | derivable | GAP  →  delta (missing → lookup/default/input)
        │
        ▼
CLASSIFY per output subtree → archetype (matrix prior) → function kit
        │
        ▼
GENERATE (constrained): fill every output slot from its source, using the kit
        │
        ▼
VALIDATE: coverage + schema-conformance (+ optional synthetic round-trip)
```

## Implementation Notes (phased)

- **Phase 1 — Coverage analysis (standalone, highest value).** A
  `buildCoverage(inputSchemas, outputSchema)` step (deterministic matcher + optional LLM
  for semantic/derivable) producing the report + delta; surface it in the MC-mode UI and
  feed it to the prompt. Mostly reuses IF10's walks.
- **Phase 2 — Archetype classification + function kits.** Per-output-node classifier
  (shape buckets × cardinality → archetype candidates); a curated function kit per
  archetype; inject archetype + kit + coverage into `message-contract-prompt.ts`.
- **Phase 3 — Validation.** Schema-conformance check; daemon-side synthetic-data
  generation from a schema + output-against-schema validation for the round-trip.
- Reuses IF08's dispatcher/`buildPrompt`; the MC builder stops being a stub.

## Acceptance Criteria

- For a given input schema(s) + output schema, a coverage report classifies every output
  field (direct/derivable/GAP) and lists the delta of unsatisfiable required fields.
- MC-mode generation produces a mapping that fills every *coverable* output field from
  its identified source and never invents a source for a GAP field (it uses a
  default/placeholder and flags it).
- The detected archetype drives a focused function kit (not all 650) into the prompt.
- A generated mapping passes schema-conformance; the synthetic round-trip (when enabled)
  produces an output instance valid against the output schema.
- No sample data is required at any point.

## Testing

- **Unit:** coverage matcher (direct/type/structural) on representative schema pairs;
  archetype classifier on shape-pair fixtures; function-kit selection per archetype.
- **Integration:** end-to-end on a known schema→schema case with a deliberate gap →
  expect the gap in the delta and a flagged default in the output; synthetic round-trip
  yields a conforming instance.

## Related

- IF08 (mode-aware AI assist) — this fills the Message Contract prompt stub.
- IF10 (per-input abstract / walks) — the structural foundation; extend to the output schema.
- `docs/architecture/ai-assist-prompt-wins.md` — archetype/kit steers are prompt wins.
- Message Contract mode / USDL Tier-2; the 650+ stdlib functions.

## Effort Estimate

Large, phased. Phase 1 (coverage + delta) is the high-value first slice and largely
deterministic (≈1.5–2 wk). Phase 2 (archetype classifier + function kits + MC prompt)
(≈2 wk). Phase 3 (schema-conformance + synthetic round-trip, partly daemon-side)
(≈1.5 wk). Sequence and gate each on the prior proving out.
