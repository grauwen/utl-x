# IF27: Mapping optimizer — a rewrite pass over generated UTLX (a query planner for mappings)

**Status:** **Proposed.** A semantics-preserving rewrite/cost pass. It never changes *what* a mapping
computes, only *how* — so it sits behind the same validate-gate as any change.
**Priority:** Medium — once mappings are generated (IF20) and trusted, the next question is whether they're
*efficient*. The win is largest on N:M / array-heavy mappings, where a structural rewrite can move a
transform between cost classes.
**Created:** June 2026
**Component:** engine / compiler (the optimizer is an AST → AST pass) + optional IDE surface (show the
rewrite as a reviewable diff with a cost estimate). No change to the language or to mapping *semantics*.
**Depends on:** IF20 (the generated UTLX + correspondence set this optimizes), IF11 (coverage — the
post-rewrite mapping must still cover the contract), the code generator / AST
(`mapping-editor/code-generator.ts`).

> **One-liner:** Treat the delivered UTLX like a SQL query: keep the **source** the clearest correct
> statement, and let an **optimizer** rewrite it to an efficient **plan** using sound, semantics-preserving
> equivalences. Heuristic rewrites are schema-only and always-win; cost-based rewrites need statistics.
> Every rewrite is validated (correct-by-construction or differential test) before it's allowed to stand.

See the theory book *Many to One*, ch. 15 *"The Cost of a Mapping."*

---

## Motivation

"Is the delivered UTLX the most optimized mapping possible?" has a real answer because a mapping is a
formal object with an **equational theory**: UTLX's operators are relational algebra + functional
combinators, both of which come with semantics-preserving equivalences. Optimization is the search, among
equivalent forms, for the cheapest — exactly query optimization (Elmasri), plus functional fusion
(Wadler / Bird–Meertens) and the nested-collection calculi (NRC; Fegaras–Maier) for the nested and
aggregating parts.

Crucially, this is a **plan** concern, not a source concern (the SQL lesson, and the book's
mapping-vs-transformation separation): the author/AI writes the clearest correct mapping; the optimizer
makes it fast. Authors should not uglify UTLX by hand.

## Cost model — streaming vs blocking

| Class | Operators | Cost |
|---|---|---|
| streaming | field copy, `pick`/`omit`, `map`, `filter`, `...spread` | linear, one pass, bounded memory — cheap |
| folding / indexed | `reduce`, `sumBy`, `countBy`; `findBy`/`filterBy` over an init-time index | linear; O(1) probe — moderate |
| **blocking** | `groupBy`, `orderBy`, `distinct`, **un-indexed** `findBy`, `map` nested over a join | materialize / sort / quadratic — expensive |

Blocking operators are pipeline-breakers and are **visible in the schema graph before any data flows** —
so much of the analysis is design-time.

## Rewrite rules (the always-win, heuristic layer — schema-only)

- **Fusion / deforestation:** `map f ∘ map g → map (f∘g)`; `map`+`reduce` → single fold. Eliminates
  intermediate collections.
- **Filter pushdown:** move `filter` ahead of `map` / join when the predicate allows. Shrink early.
- **Projection pushdown:** drop fields not used downstream before an expensive op.
- **Group-once:** replace a `filter`-per-key (O(N·M)/O(N²)) correlation with a single `groupBy` (O(N)).
  The highest-value structural rewrite.
- **Index the lookup:** build a hash index at **init time** so `findBy` is O(1) per probe (the IF/engine
  init-time lookup indexing already does this — make it the optimizer's default for keyed joins).
- **Loop-invariant hoisting / CSE:** lift invariant or repeated subexpressions out of `map` bodies.

The ch. 6 boundary governs which calculus applies: **within-tgd** operators (projection, join, nest) →
relational-algebra rewrites; **beyond-tgd** (groupBy, aggregate, derive, order) → functional/fold fusion.

## Cost-based layer (needs statistics)

Join ordering, access-path choice. Requires cardinalities/selectivities, which schemas don't carry — fed
by **declared cardinalities** (schema metadata) or **runtime profiling** (adaptive). Strictly later than
the heuristic layer; optional.

## Three times (which optimization runs when)

- **design time** — heuristic rewrites over the schema graph (always-win, no data).
- **init time** — build lookup indices (hash-join build side).
- **run time** — cost-based / adaptive, informed by profiling. Never put a blocking decision on the hot
  path that could have been made earlier.

## Soundness & the gate (non-negotiable)

- **Referential transparency is the precondition.** Rewrites are valid only because UTLX is pure /
  deterministic — the *same* property that makes it auditable (book ch. 13). Do not rewrite across any
  impure construct.
- **Every rewrite is validated, not assumed:**
  - *by construction* — the rewrite is an instance of a proven equivalence (verified-pass guarantee); **or**
  - *by refutation* — run pre- and post-rewrite forms on samples and diff outputs (differential test;
    a check, not a proof). Prefer by-construction.
- **The gate is absolute:** an "optimization" that changes output is a **defect**. The post-rewrite mapping
  must pass the same IF11 coverage + IF20 validation as the original.

## Honest limit

Global optimality is **NP-hard / undecidable** (optimal join order; cheapest equivalent program). The
optimizer delivers a **local optimum / normal form** via sound rules + bounded cost search — like any query
planner. AI-generated mappings are *idiomatically* near-optimal (it favors `groupBy` over nested filters —
ch. 13) but not provably optimal, so a deterministic optimizer is a fitting finisher.

## Phasing

- **Phase 1 — measure & advise.** Compute the cost-class profile of a delivered mapping; flag the
  blocking operators and the obvious wins (un-indexed lookup, filter-per-key). No rewrite yet — just a
  report (pairs naturally with IF26).
- **Phase 2 — by-construction rewrites.** Apply the heuristic, proven equivalences (fusion, pushdown,
  group-once, index lookups), each validated, surfaced as a reviewable diff.
- **Phase 3 — cost-based.** Declared-cardinality / profiled join ordering and access-path selection.

## Out of scope / non-goals

- Not a semantics change: outputs must be identical (the whole point).
- Not a source-uglifier: prefer optimizing the plan; only rewrite source where it stays legible, else keep
  the optimization in the compiled plan.
- Not a correctness substitute: the optimizer assumes a correct mapping in, and must preserve it.
