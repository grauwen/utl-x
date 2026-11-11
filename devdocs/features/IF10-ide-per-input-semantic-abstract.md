# IF10: IDE — Per-Input Semantic Abstract ("reversed prompt")

**Status:** Mostly implemented.
- **Deterministic abstract** (`utils/input-abstract.ts`), **schema-aware** (data → UDM walk; JSCH/XSD/OSch/TSch → schema field tree, with **required fields + constraints**), single-root unwrap, relative arrays. One shared `buildAbstractForInput(input)` feeds **both** the AI-dialog input list and the input-panel **"Info" button** (compact overlay).
- **LLM gloss (v2)** — **opt-in "✨ Explain (AI)" button in the AI dialog only** (MCP `describe_input` tool → `UTLXService.describeInput`), one cached call per input, graceful when no LLM. NOT in the input-panel Info button (no LLM scattered outside AI assist).
- **Pending:** complexity-gated injection of the abstract into the **generation prompt** (model-facing); editable/curated abstracts.
**Priority:** Medium
**Created:** June 2026
**Depends on:** input panel + UDM (`extractInputPaths`/`formatPathsAsSimpleList`); AI assist prompt (IF08, `execution-prompt.ts`)
**Effort:** Medium (1-2 weeks for v1 deterministic; +1 wk for LLM/editable v2)

> **Design decisions captured here (not yet implemented):**
> - Generate a concise **per-input semantic abstract** — a description of *what the
>   input message is* (entity, structure, cardinalities, depth) — derived backwards
>   from the data. The inverse of the user's task prompt, hence "reversed prompt".
> - **Deterministic-first.** v1 builds the abstract from the **UDM/schema** (no LLM):
>   field summary + which paths are arrays + nesting depth. It cannot hallucinate.
> - **LLM domain sentence is v2, cached + opt-in.** One call per input adds a
>   one-line "this is a purchase order…" gloss; cached and reused across generations.
> - **Editable / curatable** (v2): the abstract is a draft the user can refine; the
>   curated version then grounds every future generation for that input.
> - Plugs into the AI-assist prompt as a per-input `## Input semantics` block,
>   **supplementing or replacing** the truncated raw-data dump.

---

## Summary

AI assist generates UTL-X from a task prompt + the input's structure. Today, per
input, the model receives either compact **UDM paths** or **raw sample data truncated
to ~800–1500 chars**. For deeply nested messages the truncation drops most of the
structure, leaving the model half-blind. IF10 adds a **per-input semantic abstract**:
a short, accurate description of what the message *is* — generated from the data
("reversed prompt"). It grounds generation better than truncated data, is reusable
and cacheable (describe once, generate many), and doubles as input documentation and
an on-ramp to Message Contract mode.

## Problem

`buildUTLXGenerationUserPrompt` (`execution-prompt.ts`) emits, per input, either the
UDM path list or `originalData` truncated to 800–1500 chars. For a large nested
message (e.g. an enterprise order: customer → contacts → addresses → coordinates →
metadata; lineItems[] → productDetails → specifications → testingMetadata; payment →
banking → creditScore …), the truncated data conveys almost none of the real shape,
and the full path list can be hundreds of lines. So:

- The model misses structure → wrong/clumsy mappings, more validation/runtime
  failures, more refine turns (cost + Max usage).
- The same bulky context is re-sent on every generation against the same input.

There is no concise, semantic, reusable description of an input.

## Goals

- A **concise per-input abstract** of the message: entity/shape, top-level fields,
  **array cardinalities**, nesting depth — small enough to always include in full.
- **Deterministic** (v1): derived from the UDM/schema; accurate, zero LLM cost.
- **Cached + reusable** across multiple generations against the same input.
- Wired into the AI-assist prompt as `## Input semantics`, reducing reliance on
  raw-data truncation.

## Non-Goals

- Replacing UDM **paths** — the model still needs exact accessors to write code; the
  abstract is *semantic grounding*, complementary to paths.
- A full schema/contract format — that's Message Contract mode / USDL Tier-2. The
  abstract is a sketch, not a normative schema.
- Mandatory LLM calls — v1 is deterministic; the LLM gloss (v2) is opt-in.

## Design

### What the abstract contains

A small structured + prose summary, e.g. for the enterprise order:

```
Input "enterprise-order" (json) — semantic abstract
• Entity: a purchase order (top-level object).
• Top fields: orderId, orderDate, orderStatus, totalAmount, currency,
  customer{…}, shippingDetails{…}, lineItems[…], paymentDetails{…}, orderMetadata{…}
• Collections (arrays): lineItems, orderMetadata.approvalChain.approvers
• Depth: up to 7 levels (e.g. lineItems[].productDetails.specifications
  .technicalDetails.performanceMetrics.testingMetadata)
• Notable: addresses+coordinates+locationMetadata structure repeats under several contacts
```

### Deterministic baseline (v1 — the source of truth)

Walk the UDM/schema (we already extract paths via `extractInputPaths`):
- top-level field names + kinds (object/array/scalar),
- **array paths** (cardinality markers),
- **max nesting depth** and the deepest exemplar path,
- optional: repeated-substructure detection (same field set seen under multiple parents).

No LLM → it cannot invent fields. This alone is enough to ground the model far better
than truncated data.

### LLM domain gloss (v2 — cached, opt-in)

One call per input adds a single domain sentence ("This is an enterprise B2B purchase
order…"). It is **flavor on top of the deterministic facts**, never the source of
field truths (avoids hallucinated accessors). Cached (keyed by a UDM hash) and reused
across every generation for that input.

### Editable / curated (v2)

Show the abstract as a generated draft the user can edit. The curated text then
grounds all future generations for that input — a one-time investment that compounds.
Persisted with the input (IF09 session now; bundle file under IF03/IF04 later).

### Where it plugs in

Two surfaces:

**1. The AI-assist prompt (model-facing).** In the per-input section of
`buildUTLXGenerationUserPrompt`, add `## Input semantics` with the abstract, and
**supplement or replace** the truncated `originalData` block. Net effect: more
signal, fewer tokens, stable across runs.

**2. The AI dialog (user-facing) — a compact input list above the prompt.** Today the
dialog asks "describe what transformation you want" with no reminder of what's
available. Add, **above** the prompt textarea, a compact read-only list of the inputs
the AI will use:

```
Inputs (3):  enterprise-order · json ▸    customers · csv ▸    rates · json ▸
```

- Each row **expands inline** to show that input's abstract (the "reversed prompt").
  **Collapsed by default** — the dialog already holds prompt history, the Load chip,
  the scaffold hint, and the activity log, so another always-open panel would crowd it.
- **Lazy + cached:** the deterministic abstract is instant; a v2 LLM gloss is
  generated on first expand and cached.
- **Friendly label, not jargon:** the expander reads **"What is this input?"** /
  **"Input summary"** — "reversed prompt" / "input profile" stays the internal name.
- **Read-only here** (curation/editing is v2). Purpose differs from the input panel
  (which loads/edits inputs): this is "what the AI will use, summarized," placed where
  you're about to prompt — so it's complementary, not duplicative.

Why it helps: the user references the **exact** input names (`$enterprise-order`, not
"the orders") and sees the structure before describing the mapping — improving prompt
precision. It also extends the "user sees what the AI sees" cluster (Load-current-UTLX,
activity monitor, show-with-warning) from IF08.

### Caching & invalidation

Compute on demand; cache keyed by a hash of the input's UDM. Invalidate when the
input content/format changes (the input panel already tracks this).

### Relation to Message Contract mode

The abstract is a semantic description of a message — the same thing a contract
describes informally. It's a natural precursor to Message Contract / USDL Tier-2
work: a curated abstract could later seed or cross-check a real contract.

## Implementation Notes

- **v1 (deterministic):** an `buildInputAbstract(udm)` util (browser-side, near
  `udm-path-extractor`) producing the structured summary; surface it per input and
  include it in the `GenerateUtlxInput` sent to the MCP; render it in `## Input
  semantics` in `execution-prompt.ts`.
- **Caching:** memoize by UDM hash in the input panel; recompute on input change.
- **AI dialog list:** a compact read-only input list (name · format) above the prompt
  textarea in the toolbar AI dialog, each row inline-expandable to its abstract
  (collapsed by default, lazy-filled, friendly label). Read-only in v1.
- **v2 (LLM gloss):** one cached MCP call (`describeInput`) for the domain sentence;
  opt-in; never used for field facts.
- **v2 (editable):** the dialog row's abstract becomes editable; persisted per input
  (IF09 → IF03/IF04).

## Acceptance Criteria

- Each input exposes a concise abstract (entity, top fields, array cardinalities,
  depth) derived deterministically from its UDM — no LLM required.
- The abstract appears in the AI-assist prompt as `## Input semantics`; the
  truncated raw-data block shrinks or drops.
- On a deeply nested input, generation grounds on the abstract (measurably fewer
  refine turns / better first-shot than truncated data) — the success metric for v1.
- The abstract is cached and recomputed only when the input changes.
- The AI dialog shows a compact input list (name · format) above the prompt; each row
  expands inline to its abstract (collapsed by default), so the user can reference
  exact `$names` and see the structure before prompting.
- (v2) An optional cached domain sentence; an editable/curated abstract.

## Testing

- **Unit:** `buildInputAbstract` on representative UDMs — correct array cardinalities,
  depth, top-field list; stable output; empty/edge inputs.
- **Manual:** generate against the enterprise-order input with vs without the abstract
  — compare attempts/turns and output quality (use the IF08 activity monitor).

## Related

- IF08 (mode-aware AI assist) — the abstract is per-input prompt context; pairs with
  the activity monitor for measuring its effect.
- `docs/architecture/ai-assist-prompt-wins.md` — the abstract is a structural prompt win.
- Message Contract mode / USDL Tier-2 — the abstract is a semantic precursor.
- IF09 (session persistence) / IF03–IF04 (bundle) — where a curated abstract is stored.

## Effort Estimate

Medium. v1 deterministic abstract + prompt wiring + caching + unit tests (~1-2 wk).
v2 cached LLM domain gloss + editable/curated abstract (~1 wk). Gate v2 on v1 showing
a measurable generation improvement on nested inputs.
