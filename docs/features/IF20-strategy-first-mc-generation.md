# IF20: IDE — strategy-first Message Contract generation (analyze inputs → choose a mapping strategy → strategy-specific prompt)

**Status:** Proposed (design). Not implemented.
**Priority:** High — the current single fixed MC prompt produces verbose, one-by-one mappings,
doesn't use UTL-X's expressive constructs (`...` spread, joins, `map`/`groupBy`), and treats all
inputs as equals (ignores lookup/reference tables). A strategy-first pipeline fixes all three and
makes generation faster *and* more idiomatic.
**Created:** June 2026
**Component:** mcp-server prompt pipeline (`prompt-dispatcher.ts`, `message-contract-prompt.ts`) +
IDE analysis (`utils/coverage.ts`, input/output panels). Front-end + mcp-server. No daemon/CLI/engine.
**Depends on:** IF11 (deterministic coverage + LLM gap refinement), IF10 (per-input UDM/abstract),
the scaffold generator (`utils/scaffold-generator.ts`), the mode dispatcher.
**Related:** IB05 (output **data** vs **schema** format — must be fixed first/with this).

> **Scope note (Jun 2026):** MC mode is the priority (open-m's core). The MC analysis is
> **schema-based**; Execution mode needs its **own, instance-based** analysis (it has real values:
> cardinality, value-overlap, observed keys) — they are **separate analyzers, not shared**. Execution
> is out of scope here / deferred (its prompting works to a degree and is not being touched). The
> placeholder driver/lookup/enrichment roles are being upgraded to a **schema-graph typology** (see
> *Research foundation*); `utils/strategy.ts` is a pragmatic v0. **This doc will be refined.**

---

## Motivation

MC-mode generation today is **one fixed prompt** (`message-contract-prompt.ts`): "fill every output
field from a source." Three problems:

1. **It enumerates fields one-by-one.** Even with a coverage-aware scaffold, the structure *bakes in*
   per-leaf copying — so the model writes 200 assignments instead of `...$driver` + a few overrides.
   The scaffold is right for *synthesis* shapes and wrong for *mirror* shapes.
2. **It ignores the expressive language.** Spread, pipelines, `map`/`groupBy`/`reduce`, joins —
   the nice parts of UTL-X — are rarely produced because the prompt frames the task as field filling.
3. **It treats all inputs as equals.** With multiple inputs, the 2nd/3rd is often a **lookup table**
   (joined by key), not a co-equal source. A flat "source each field" framing misses the join shape.

The deterministic **coverage** (IF11) is fast and is the field-level map — but the *best structural
shape* of the mapping depends on the problem, and no single prompt/scaffold encodes every shape.

## Core idea

Add a **planning layer** before generation. Instead of `mode → prompt`, do:

```
mode → ANALYZE (inputs + output contract + coverage) → STRATEGY → strategy-specific prompt(s) → generate
```

A first pass **classifies the problem and picks a strategy**; generation then uses the prompt (and
scaffold style) that fits that strategy, so it reaches for the right constructs. **Coverage grounds
the field-level sourcing within whichever strategy** — strategy decides the *shape*, coverage decides
the *sources*.

## The MC analysis (schema-based)

**Node typology (format-independent).** Type each schema node by its structural role — derived from
the schema graph + keys/constraints, not from the format or name guesses (XML attributes / CSV columns
are just surface). After the matching / data-exchange literature (see *Research foundation*):

- **entity / collection** — a (repeating) structure; cardinality = array vs object.
- **key** — an identifying field of an entity.
- **reference (foreign key)** — a field whose value identifies a node in *another* input ⇒ a **join edge**.
- **containment** — nesting (parent → child).
- **value / attribute** — a leaf scalar.

This **replaces** the placeholder driver/lookup/enrichment roles: a "lookup table" is just an entity
the driver **references by foreign key**; the driver is the entity the output's top-level collection
iterates. Roles fall out of the **schema graph + FK edges** — far stronger than name heuristics, and
deterministic from the schema.

**(a) Interim roles (current `strategy.ts` v0):** driver / lookup / enrichment via a usage-count +
keyish-name heuristic over the coverage matrix — a pragmatic placeholder to be superseded by the
typology above.

**(b) Output ↔ driver relationship:**
- **mirror** — output ≈ a driver input → `...$driver` + overrides (NEVER enumerate).
- **restructure** — same data, new shape → explicit object construction (today's synthesis).
- **aggregate / group** → `groupBy` / `reduce`.
- **array transform** → `map` over the driver collection.
- **join** → build an index from the lookup input, look it up per driver row.

Real mappings **compose** these (e.g. `map` the driver, spread its fields, look up the reference
table per row, default the gaps).

## The mapping matrix

Generalize the IF11 coverage report (1 input → output) to an **inputs × output-leaves matrix**: for
each output leaf, which input/source (or derivation, or gap) feeds it. The matrix is the analysis
artifact that:
- reveals **input roles** (a column used only as a key ⇒ lookup; a column feeding most leaves ⇒ driver),
- drives **strategy selection** (high mirror-ratio ⇒ spread; key column ⇒ join; array driver ⇒ map),
- still grounds **field-level sourcing** for the chosen strategy.

## Strategy catalog (each carries its own prompt + scaffold style)

| Strategy | When | Generation shape | Scaffold/seed style |
|---|---|---|---|
| **mirror / pass-through** | output ≈ driver | `...$driver` + overrides | spread seed (NOT a per-leaf scaffold) |
| **restructure** | same data, new shape | explicit object construction | coverage-aware field scaffold |
| **array transform** | driver is a collection | `map(driver, x => …)` | element scaffold |
| **join / lookup** | driver + reference table(s) | index the lookup, look up per row | join skeleton (index + lookup) |
| **aggregate / group** | many→few | `groupBy`/`reduce` | group skeleton |

The **scaffold style is per-strategy** — that's what resolves the "spread vs one-by-one" tension:
mirror seeds with `...`, synthesis seeds with field holes, join seeds with the index+lookup pattern.

## Dispatcher change

`prompt-dispatcher.ts` gains a strategy dimension within MC mode:
```
buildPrompt('message-contract', context):
   strategy = analyzeStrategy(context.inputs, context.outputSchema, context.coverage)
   return STRATEGY_PROMPTS[strategy](context)   // each independent, like the mode builders
```
- **Analysis is deterministic-first** (cardinality, key detection, mirror-ratio from the matrix) with
  an **optional small AI call** only to confirm/disambiguate — far cheaper/faster than today's single
  big agentic generate (which is the source of the "extreme time").
- **Self-correct** (validate against UTLXD) stays as a safety net, not the primary mechanism.

## Research foundation

This is the field of **schema matching & mapping / data exchange** — decades of work to build on:

- **Rahm & Bernstein, "A survey of approaches to automatic schema matching"** (VLDB Journal, 2001) —
  the taxonomy: element-level vs **structure-level**, **schema-only vs instance-based**, linguistic vs
  **constraint-based**. Directly justifies *two separate analyses* (MC = schema-only/constraint;
  Execution = instance-based).
- **Clio** — Miller/Haas/Hernández, "Schema Mapping as Query Discovery" (2000); Popa et al.,
  "Translating Web Data" (2002); Fagin et al., "Clio: Schema Mapping Creation and Data Exchange"
  (2009). The foundational mapping generator: **joins & nesting are derived from keys & foreign keys**
  (schema *associations*), not field-by-field — the "2nd input is a lookup" intuition, formalized.
- **Data Exchange** — Fagin, Kolaitis, Miller, Popa, "Data Exchange: Semantics and Query Answering"
  (2005): a mapping is a set of **source-to-target tgds** (the formal object).
- **Matchers** — Cupid (structure-aware), COMA/COMA++ (composite), Similarity Flooding (graph-based).
- **Best single entry point** — *"Schema Matching and Mapping"*, Bellahsene, Bonifati, Rahm (eds.),
  Springer 2011 — surveys all of the above.

**Takeaway for IF20:** drive strategy from the **schema graph + keys/FKs** (Clio-style associations),
use the **element-level + structure-level** split for coverage/matching, and keep MC (schema) and
Execution (instance) as **distinct analyses** per the Rahm/Bernstein axes. The current `strategy.ts`
is a name-heuristic stand-in for this.

## Phasing

1. **Input-role + strategy analyzer** (deterministic, from the coverage matrix + input shapes) →
   returns `{ perInputRole, relationship, joinKeys, arrayDims, mirrorRatio }`. Useful standalone.
2. **Strategy prompt catalog** — split the monolithic MC prompt into per-strategy builders; wire the
   dispatcher to select by analyzer output.
3. **Per-strategy scaffold styles** (spread seed / field scaffold / join skeleton).
4. **Optional AI strategy confirmation** for ambiguous cases.

## Prerequisite

**IB05** — the MC request must send the output **data** format (not the contract **schema** format),
or every strategy still risks emitting a schema. Land IB05 (toolbar `schemaToDataFormat`) first/with this.

## Code pointers

- `mcp-server/src/llm/prompts/prompt-dispatcher.ts` — add the strategy layer.
- `mcp-server/src/llm/prompts/message-contract-prompt.ts` — split into per-strategy builders.
- `theia-extension/.../browser/utils/coverage.ts` — extend the report to the multi-input matrix +
  role/relationship signals (`analyzeStrategy`).
- `theia-extension/.../browser/utils/scaffold-generator.ts` — per-strategy seed/scaffold styles.
- `theia-extension/.../browser/toolbar/utlx-toolbar-widget.tsx` — `snapshotCoverage`/`buildMCContractContext`
  already assemble inputs+coverage; feed the analyzer here.
