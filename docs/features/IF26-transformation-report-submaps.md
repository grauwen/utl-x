# IF26: Generate a human-readable transformation report (sub-map decomposition)

**Status:** **Proposed.** A read-only *output* feature — it documents a finished, tested transformation and
changes nothing about how mappings are authored or run.
**Priority:** Medium-high — the artifact business analysts, auditors, and maintainers actually ask for;
closes the round-trip opened by IF23 (spreadsheet in → mapping; now mapping → spreadsheet).
**Created:** June 2026
**Component:** mcp-server (a new **report generator**: UTLX AST + correspondence set → workbook model) +
IDE (an "Export mapping report…" affordance). No engine / daemon-runtime / conformance change.
**Depends on:** IF11 (per-output sourcing / coverage), IF20 (strategy + correspondence set + `basis`),
IF23 (the inverse direction; shares the correspondence-set serialization so reports round-trip),
the code generator / AST (`mapping-editor/code-generator.ts`) as the source of truth.

> **One-liner:** After the UTLX is written and tested, generate a **human-readable report** of what it
> does — a multi-sheet spreadsheet that decomposes the transformation into **N:1 sub-maps** (the
> Mercator/WTX idea), one legible sheet per sub-map, with a per-field table in business language. Generated
> from the **tested AST** (not pre-generation intent), structure extracted deterministically, prose added
> by AI over those facts.

---

## Motivation

A business analyst rarely produces a good mapping document up front — which is why AI authors the UTLX
(IF20/IF25). But once it exists and passes tests, several readers need to *understand* it without reading
UTLX: the **BA** who signs off, the **auditor** who traces a field (integration carries payments,
prescriptions, shipments), and the **maintainer** who inherits it. A flat "source → target" sheet collapses
on a real **N:M** mapping — many inputs converge, arrays iterate, values are derived not copied.

The fix is the book's own thesis: **N:1 is the atom**, so it is also the unit of *explanation*. Render the
transformation as a composition of **N:1 sub-maps**, each individually legible, plus an overview of how they
compose. (Book *Many to One*, ch. 14 §"Explaining It Back: Sub-Maps as Documentation".)

## What "sub-map" means here

Exactly Mercator / IBM WebSphere Transformation Extender's idea — a complex map decomposed into named,
self-contained sub-maps — but the carving lines are **not invented for the report**; they are ch. 14's three
output relations:

- **independent** sub-maps stand alone (separate N:1s sharing inputs);
- **co-derived** sub-maps share a stated intermediate derivation;
- **chained** sub-maps feed one another (a pipeline of N:1s).

If the generated UTLX is **modular** (a named function per entity/section — which IF20 strategy-first
already favors), each function *is* a sub-map and the report sections fall out of the code for free.

## Source of truth: the tested AST, not the pre-gen intent

The report MUST describe **what runs**, so it is generated from the **UTLX AST** (read structurally),
*annotated* by the correspondence set (IF20) for semantics and `basis`. Generating from the pre-generation
analysis instead would document *intent*, not behavior — the preview-≠-engine divergence (cf. B24) in paper
form. AST = truth; correspondence set = provenance/semantics overlay.

## Generation = the "reversed prompt," aimed at the output

Same two-layer split as the input reversed prompt (IF10), the output end:

1. **Deterministic (facts):** walk the AST → per target field: kind (copy / lookup / derivation / constant /
   gap), source path(s), the function/expression. Cannot hallucinate.
2. **AI (meaning):** render each rule in business language ("Order Total = sum of all line-item amounts",
   not `sum(lineItems[*].amount)`), name the sub-maps, write the overview. BA-validated. Tagged so prose is
   distinguishable from extracted fact.

## Workbook shape

- **Overview / index sheet** — the sub-map composition (the N:M → N:1 decomposition), ideally with a small
  diagram; lists each sub-map and its inputs/output.
- **One sheet per sub-map** — a per-field table:

  | Target field | Kind | Source(s) | Rule (business language) | Notes / provenance |
  |---|---|---|---|---|
  | `header.invoiceId` | copy | `order.orderId` | direct copy | — |
  | `header.billingRegion` | constant | — | fixed: "EU-NL" | injected constant (`basis: config`) |
  | `lines[*].productName` | lookup | `catalogue` by `productCode` | look up product name by code | — |
  | `summary.orderTotal` | derivation | `lines[*].amount` | sum of all line-item amounts | computed, not sourced |

- Arrays show the iteration ("for each order line …").

## Honesty constraints

- **Lossy projection.** A sheet can't capture every conditional / edge case — state it, and link each sheet
  back to the UTLX for the full truth (same discipline as the "from sample" lower-bound label in IF10).
- **Derived, never source.** Regenerate from the current AST; version with the code. A report that drifts
  from the code is worse than none.
- **Round-trips.** Because it is one more reading of the IF20 correspondence set, a BA's corrections to the
  report can re-enter via the IF23 reader — author→reviewer flip, loop closed.

## Phasing

- **Phase 1 — flat field table** from the AST (deterministic), single sheet, no sub-map decomposition.
- **Phase 2 — sub-map decomposition** (overview + per-sub-map sheets) using ch. 14's relations / the UTLX
  function structure.
- **Phase 3 — AI business prose + round-trip** (readable rules, named sub-maps, corrections back through
  IF23).

## Out of scope / non-goals

- Not an authoring surface (that's IF21 treeview / IF20); this is read-only documentation.
- Not a runtime artifact; design-time only, generated on demand.
- Not a substitute for the UTLX: the report is a projection, the code is the truth.
