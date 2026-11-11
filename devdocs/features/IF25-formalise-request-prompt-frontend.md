# IF25: MC — Formalise the request (prompt front-end: intent → grounded spec → additions)

**Status:** **Proposed.** Pure addition and a *front-end* — it runs before the IF20 proposal pipeline and
changes none of it. With no user prompt (a plain "propose a mapping"), it is a no-op.
**Priority:** Medium-high — a natural-language request ("add Dutch VAT, all levels") is the most common way
a user actually asks for a mapping change; today that intent reaches the proposer raw and unvalidated.
**Created:** June 2026
**Component:** mcp-server (a new **request-formalisation** stage ahead of the IF20 pipeline) + IDE
(a prompt box, a confirm-the-plan step, and surfacing of any proposed additions). The formaliser may
**inject constants into the generated transformation or propose a new typed input**, so it touches the
*generated artifact* (design-time) — but **not** the engine, daemon runtime, or conformance suite.
**Depends on:** IF11 (coverage / per-output sourcing — to ground feasibility), IF20 (strategy +
correspondence set + `basis` — what this seeds), IF23 (sibling intent-formaliser; shares the validate-gate
and `basis` discipline), IF21 (treeview — where additions and clarifications surface).

> **One-liner:** Turn a free-text request into an **exact, grounded, confirmed specification** before the
> proposer runs. Parse intent → ground against the schemas/current mapping → propose any required
> **additions** (new field, derivation, or world-knowledge constant) → ask the clarifying questions →
> **get the user to approve the plan** → hand the formalised request to IF20. Borrows plan-then-confirm
> from agentic coding assistants.

---

## Motivation

A user rarely speaks in correspondences. They say *"add Dutch VAT — high, low, and all the levels."* That
sentence is **unformalised intent** and cannot drive IF20 raw:

- It names a domain concept (Dutch VAT) whose **rates live in neither the input nor the output** — the
  model must supply them (21% standard / 9% reduced / 0% zero-rated / exempt).
- Its scope is open ("all the levels") — the closure must be made explicit, not silently chosen.
- It may demand **additions** the deployment does not permit (a new lookup table where the input set is
  sealed).

Formalising the request is *itself an N:1 problem* (inputs: prompt + output schema + current inputs +
existing mapping + world knowledge → one spec). See book *Many to One*, ch. 12 §"Formalising the Request".

## Pipeline (front-end to IF20)

```
user prompt ("add Dutch VAT, all levels")
   │
   ▼  ① PARSE INTENT (AI)        → operation + domain concept + scope
   ▼  ② GROUND (IF11/IF20)       → output has VAT fields? inputs carry net amount + category?
   ▼  ③ PLAN ADDITIONS           → new field(s), derivation(s), world-knowledge constants
   │        └─ placement decided by INPUT-SET POLICY × volatility (see below)
   ▼  ④ CLARIFY                  → per-line vs per-document? as-of date? category→rate map?
   ▼  ⑤ EMIT SPEC + CONFIRM      → read back the exact interpretation; WAIT for user approval
   │
   ▼  hand formalised request → IF20 role-one prep → role-two propose → validate
```

Nothing downstream of the hand-off changes; this stage only produces the grounded request IF20 already
expects, plus any approved new inputs.

## Input-set extensibility policy (the key new config)

A prompt can **demand an addition the deployment forbids**. So the formaliser must know, per workspace /
per pipeline slot, whether the input set is:

- **`open`** — new `lookup` / `enumeration` inputs may be added (files, reference tables); or
- **`sealed`** — a fixed roster of slots (e.g. **Open-M's seven**) that admits no new input.

This is an explicit policy the front-end **checks before proposing any new input**. It is the single most
important guard: in a sealed deployment the formaliser must *not* propose an eighth input — it must route
the knowledge elsewhere.

## Placement of injected constants (provenance absolute, placement constrained)

Where a world-knowledge constant lands is decided by **policy × volatility**; what never bends is that it
is **surfaced in the spec, tagged with a `basis`, and confirmed**.

| | input set `open` | input set `sealed` |
|---|---|---|
| **volatile** (VAT rates, code maps) | materialise as `lookup`/`enumeration` (best audit) | carry in existing binding (e.g. `config`); else **inline + "revisit on change" flag** |
| **stable** (fixed scalar) | `enumeration` or `config` | **inline** — legitimate compile-time constant |

- `basis: ai-world-knowledge` — a fact the model supplied (e.g. the VAT rates). **Always flagged for human
  confirmation** (legally/financially sensitive).
- `basis: user-requested` — a value the prompt fixed explicitly.
- **Anti-pattern guard:** the smell is not an inlined `0.21`; it is a *silent* one. Every injected constant
  — inline or input — must appear in the confirmed spec and carry provenance. Volatile constants that had
  to be inlined (sealed set) get a "revisit on legal change" review flag.

## Borrowed from agentic coding assistants

| Assistant technique | Here |
|---|---|
| plan → approve → **then** act | emit spec as a plan; **no proposal until the user confirms** |
| clarify underspecification | per-line vs per-doc? as-of date? category→rate? |
| gather context before editing | interpret prompt **against** loaded schemas + current mapping, never blind |
| decompose into discrete steps | split "add VAT" → field, lookup, derivation, summary |
| augment with tools/knowledge | recall rates; place as a provenanced constant |
| verify after acting | re-run IF11 coverage + validation; confirm schema-complete |

## Phasing

- **Phase 1 — scoped edits to an existing mapping.** Prompt + current mapping + schemas → a plan of
  discrete additions, confirm, apply. Covers "add VAT", "drop field X", "map Y from Z".
- **Phase 2 — additions with world-knowledge injection.** Constant sourcing + the placement table +
  input-set policy enforcement + sensitive-fact confirmation.
- **Phase 3 — cold start from prompt.** Formalise a from-scratch request into the full IF20 input set.

## IDE / daemon surface

- **mcp-server:** new optional `requestPrompt` + `inputSetPolicy: open|sealed` on the MC request; returns
  a **formalised spec** (directives + proposed additions + clarifying questions) and *withholds the
  proposal until an `approved` flag returns*.
- **IDE:** a prompt box; a **plan/confirm panel** (the read-back of intent, additions, and questions);
  proposed new inputs and injected constants shown with `basis` colour (reuse IF21 provenance styling);
  stale/forbidden additions flagged (e.g. "input set is sealed — rates will be inlined; confirm?").
- **No engine/runtime/conformance change.** Generation is design-time; inlined constants reach the
  generated transformation but the engine is untouched.

## Out of scope / non-goals

- Not an autonomous editor: the proposal never runs until the user approves the formalised plan
  (author-not-executor, book ch. 13).
- Not a run-time prompt: this is design-time mapping authoring, not per-message NL processing.
- Not a bypass of validation: every proposed addition is grounded against the live schema and confirmed.
