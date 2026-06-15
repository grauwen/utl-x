# IF23: MC — Seed mapping from a human specification (analyst mapping sheet)

**Status:** **Proposed.** Pure addition — the MC proposal pipeline already works with no spec (AI plays
the analyst). This adds an *optional* grounding input that, when present, raises proposal confidence and
makes provenance auditable.
**Priority:** Medium-high — the analyst mapping spreadsheet is ubiquitous in SAP/Siebel/EDI integration
work; ingesting it converts the proposer's hardest step (semantic correspondence) from *guessing* into
*extract + validate documented intent*.
**Created:** June 2026
**Component:** mcp-server (MC proposal pipeline — a new role-one "formalise spec" step) + IDE (an import
affordance and a `basis: analyst-spec` provenance colour in the treeview). No engine / daemon-runtime /
conformance change — the spec is design-time only and never reaches Execution.
**Depends on:** IF11 (deterministic coverage + per-output sourcing), IF20 (strategy / correspondence set —
the `basis` field and the proposal request this seeds), IF21 (treeview — where seeded correspondences and
stale-spec flags surface).

> **One-liner:** Let the analyst's *existing mapping spreadsheet* seed the MC mapping. We extract
> `source → target` correspondences from the free-format document, tag them `basis: analyst-spec`,
> **validate each against the live schema**, and hand them to the proposer as grounded semantic bridges.
> Optional: with no spec, the pipeline behaves exactly as today (AI plays the analyst).

---

## Motivation

In a large share of real integrations the mapping was **written down by a human before any tool was
involved**. A business analyst, given the fixed output contract (an SAP RFC/IDOC, a partner invoice
schema), produces a mapping document — almost always a free-format spreadsheet: source field, target
field, a rule or lookup beside it, notes where the logic is subtle. The developer then writes the
transformation with that sheet open as a reference.

That document **never executes** — it is *not* a run-time input. But it is the single richest grounding
for *authoring* a mapping, because it carries exactly the part the deterministic pipeline (IF11/IF20) is
weakest at: the **semantic correspondences**. `KUNNR → CustomerId`, a status translated through a named
crosswalk, two fields concatenated into one — domain facts no schema graph encodes and no structural
matcher recovers. The analyst supplied them by hand.

Today the MC proposer (IF20) must **re-derive** those semantics (name-refinement, `basis: ai-inferred`).
When an analyst sheet exists, we are throwing away a human-authored, higher-confidence answer and guessing
instead.

See the theory book *Many to One*, ch. 12 §"Seeding from a Human Specification" and ch. 7 (the
`analyst-spec` basis row).

## Two distinctions that keep it honest

1. **Specification ≠ run-time input.** A lookup table (e.g. an SAP→Siebel user-id crosswalk) is reference
   *data* — it flows at execution, joined by key (IF20 binding compartment). The analyst spreadsheet is
   reference *specification* — design-time only, grounds the analysis, gone before a message flows. Do not
   conflate them. This feature ingests the **spec**, not the data.
2. **The analyst is a role, not a person.** Authoring the semantic correspondences is a job the pipeline
   always needs done. A human-with-spreadsheet is one way to fill it; AI is another. So this is an
   *addition*, not a fork:
   - **No spec (common case):** unchanged — AI plays the analyst, `basis: ai-inferred` (IF20 step 5).
   - **Spec present:** AI's job shifts from *inventing* semantics to *extracting + validating* documented
     ones — smaller, safer, `basis: analyst-spec`.

## Pipeline (where it plugs into IF20)

The spec is a **role-one (prepare)** input. It augments the correspondence set *before* the role-two
proposal; it does not change the proposal request shape.

```
spec file (xlsx/csv/free text)
   │
   ▼  ① FORMALISE  (new role-one step — AI extraction)
extract { source, target, rule?, lookup?, note? } rows  →  candidate correspondences
   │
   ▼  ② GROUND/VALIDATE against the live typed schemas (IF11/IF20)
   ├─ target field exists?  type-compatible?  named lookup present?
   ├─ YES → correspondence, basis=analyst-spec, confidence high (human-authored)
   └─ NO  → STALE-SPEC FLAG (surfaced for review; never silently mapped)
   │
   ▼  ③ MERGE into the correspondence set (IF20)
   └─ analyst-spec entries pre-fill step-5 name-refinement; AI only fills what the spec left open
   │
   ▼  ④ PROPOSE (IF20 role two) — now grounded in documented intent
```

Key properties:
- **Validate-before-trust.** A spec line pointing at a field that no longer exists is a stale-spec flag,
  not a mapping. The spreadsheet supplies *intent*; the schema supplies *truth*; the pipeline reconciles.
- **Provenance preserved.** Every seeded entry is `basis: analyst-spec`; a reviewer sees what rests on the
  human draft vs. what the model added. Trust order: `fk-reachable` > `user-confirmed` > **`analyst-spec`**
  > `ai-inferred` > `name-heuristic` (analyst-spec ranks above ai-inferred — a human meant it — but below
  user-confirmed — it's unverified against the current schema until validated/accepted).
- **Additive & optional.** Absent a spec, zero behaviour change.

## Input formats (phase the parsing)

- **Phase 1 — structured CSV/TSV/XLSX** with a recognised or user-mapped column convention
  (`source`, `target`, `rule`, `lookup`). Deterministic parse → candidate rows; AI only normalises
  field paths. Lowest risk, covers the cleaned-up sheets teams already maintain.
- **Phase 2 — free-format spreadsheet / pasted text.** AI extraction over messy, merged-cell, multi-block
  sheets. The general case; gated behind phase-1 plumbing.
- **Phase 3 — round-trip.** Export the validated correspondence set *back* to a spreadsheet, so the
  human-readable spec and the machine artifact stay in sync (the spec becomes a diffable view of IF20's
  correspondence-set JSON).

## IDE / daemon surface

- **mcp-server:** a new optional `mappingSpec` grounding input on the MC proposal request; the formalise +
  validate step runs before the existing strategy/proposal call. Returns seeded correspondences and a list
  of stale-spec flags.
- **IDE (IF21 treeview):** an "Import mapping spec…" affordance; seeded output nodes show the
  `analyst-spec` provenance colour; stale-spec flags render as a distinct review badge on the output node.
- **No daemon-runtime / engine change.** The spec is design-time; nothing touches the execution hot path
  or the conformance suite (consistent with IF20's design-time/run-time separation).

## Theory grounding

This is the **Clio move returning in the wild.** Clio took *user-supplied value correspondences* + schema
structure and derived the full mapping. The analyst spreadsheet **is** that input — messier, free-format,
decades-deployed. The only modern additions: a model can now *read* the human's draft out of Excel, and
the draft must be *validated* against schemas the analyst may not have re-checked. (Book: ch. 4 Clio,
ch. 12 §"Seeding from a Human Specification".)

## Out of scope / non-goals

- Not a run-time data source (that's a `lookup` input — IF20 binding compartment).
- Not a replacement for validation: a spec never auto-applies; it proposes, the schema disposes, a human
  confirms.
- Not required: the MC pipeline must remain fully functional with no spec.
