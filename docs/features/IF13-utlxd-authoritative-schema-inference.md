# IF13: utlxd — authoritative schema **inference** (data → schema); IDE delegates, browser inferrer retired (decision-gated)

**Status:** Proposed
**Priority:** Medium-High (symmetric to IF12; removes the second browser↔engine schema duplication)
**Created:** June 2026
**Depends on:** engine inference machinery (`SchemaGenerator`, `JSONSchemaGenerator`, `OutputSchemaInferenceService`); the `formats/*` parsers; consumed by the IDE input/output panels ("Infer Schema"), IF11 (MCM), and pairs with IF12 (parse)
**Effort:** Medium-Large (daemon endpoint + IDE delegation; gated on a maturity comparison)

> **Scope note (taxonomy):** "IF" = the **IDE stack** — Theia + MCP + **utlxd**. This does
> **NOT** touch **utlxe** (`modules/engine`) or **CLI core** (`modules/cli`); they stay
> byte-for-byte unchanged (reuse engine inference **read-only**). Sibling of **IF12**
> (schema *parse*); IF13 is the schema *infer* direction.

> **Design decisions captured here (not yet implemented):**
> - utlxd should own **schema inference (data → schema)** as well as parse (IF12), so the
>   IDE stops inferring schemas in the **browser** (`schema-inferrer.ts`) — the same
>   duplication / divergence / coverage risk as the parser (the IB03 class).
> - **Decision-gated:** migrate the interactive "Infer Schema" button only **after** a
>   scope comparison shows the engine inferrer is meaningfully better than the browser one
>   (it isn't a given — see "Quality: language is not the driver").
> - **Don't overload** `/api/infer-schema` (which today means *transformation → output
>   schema*). Add a distinct **data → schema** endpoint.

---

## Summary

Schema handling has **two directions**: *parse* (schema → type tree, IF12) and *infer*
(data → schema). The IDE currently does **infer in the browser** (`schema-inferrer.ts`:
`inferSchemaFromJson/…Yaml/…Csv/…Xml/inferEdmxFromOData`), a lighter reimplementation of
capability the engine already has (`SchemaGenerator`, `OutputSchemaInferenceService`,
exercised by the CLI + conformance suite). That's the same problem IF12 fixes for parse:
duplicated logic, drift from the engine, partial format coverage. IF13 makes **utlxd** the
authoritative data→schema inferrer and has the IDE delegate — **gated** on confirming the
engine inferrer is actually better, because (unlike a hard correctness bug) inference is
heuristic and the browser version may be "good enough" for an instant, offline button.

## Problem

There are **two different "infer" capabilities**, and they're conflated:

1. **Browser `schema-inferrer.ts`** — infers a schema **from instance DATA**
   (json/yaml/csv/xml/odata → jsch/tsch/xsd/osch). This backs the **"Infer Schema"**
   button in the input/output panels. Browser-side → duplicates engine logic, can drift
   (IB03 class), and its format/edge-case coverage is whatever the IDE reimplemented.
2. **Daemon `/api/infer-schema`** — a *different* thing: infers the **output schema from a
   UTLX transformation** (`OutputSchemaInferenceService`). It is itself **limited**
   (`json-schema`/`xsd` typedef in; always emits `json-schema`).

So the **data→schema** direction the user actually invokes most (the button) runs entirely
in the browser, disconnected from the engine's canonical, conformance-tested inference.

## Goals

- A daemon endpoint that infers a **schema from instance data**, for the IDE's input
  formats (json/yaml/csv/xml/odata) → target schema formats (jsch/xsd/tsch/osch …),
  reusing the engine's inference.
- IDE "Infer Schema" delegates to it (**if** the maturity gate passes).
- Retire / demote the browser `schema-inferrer.ts` to a fallback.
- **Zero** utlxe/CLI change (engine inference reused read-only).

## Non-Goals

- Changing utlxe / CLI behavior (IDE-stack work only).
- The schema *parse* direction — that's **IF12**.
- A schema *conversion* API (osch→jsch, etc.) — separate.

## Design

### Quality: language is **not** the driver

Kotlin is **not** inherently better than TypeScript at inference — quality is set by the
**algorithm and edge-case coverage**, not the runtime. The real levers, specific to this
repo:

- **Canonical vs. second-class impl.** The engine inferrer is the one the **CLI +
  conformance suite** exercise; the browser one is a lighter reimplementation. Delegating
  reuses the *tested* path — the gain is "reuse the mature implementation," **not** "Kotlin
  > TS".
- **Inference is heuristic** → edge cases are everything: type widening, optional/nullable
  detection, merging array-element shapes, CSV type-sniffing, XML attributes vs elements,
  OData specifics. The engine has had more chances to handle (and test) these; a browser
  reimplementation tends to cover the common cases and miss the tail.
- **Consistency** is the prize: the IDE and CLI/engine should infer the **same** schema
  from the same data. Two implementations risk two answers.

### Decision gate (do this BEFORE migrating the button)

A head-to-head **scope comparison**: browser `schema-inferrer.ts` vs engine
`SchemaGenerator`/`OutputSchemaInferenceService` — formats supported, type-widening,
optional/array handling, enum/format detection, and test coverage. Migrate only the
surfaces where the engine is **meaningfully better or consistency matters**. (Assumed, not
yet verified — this gate prevents trading a snappy, offline button for marginal gains.)

### Endpoint design (avoid overloading)

`/api/infer-schema` already means *transformation → output schema* — do **not** overload
it. Add a distinct data→schema endpoint, e.g.:

```
POST /api/infer-schema-from-data
  { data: "<instance>", inputFormat: "json|yaml|csv|xml|odata",
    targetSchemaFormat: "jsch|xsd|tsch|osch", options?: {csvHeaders, delimiter, …} }
→ { success, schema: "<schema content>", schemaFormat }
```

Reuses the engine: data → UDM (existing format parsers) → `SchemaGenerator` → target schema.

### UX trade-off (explicit)

Browser inference is **instant + offline**; delegating makes the button **async +
daemon-dependent**. For an interactive button that may matter more than catching rare
cases. Options: (a) delegate fully; (b) delegate but keep browser as an offline fallback
when the daemon is down; (c) keep browser for the button, use the engine only where
consistency is critical (coverage/generation). The gate decides.

### Module isolation (the utlxe/CLI guarantee)

| Module | Action |
|---|---|
| `modules/daemon` (utlxd) | **edit** — new endpoint + tests |
| MCP server / Theia | **edit** — delegate; demote browser inferrer |
| `formats/*`, `modules/analysis`, `modules/cli`, `modules/engine` (utlxe) | **read-only — untouched** |

utlxe and CLI never call the IDE inference endpoints; their inference path is unchanged.
→ **utlxe byte-for-byte unchanged** (same discipline as IF12 / IB02).

## Implementation Notes (phased) — not started

- **Phase 0 — the gate:** the browser-vs-engine inferrer scope comparison; decide which
  surfaces to migrate and the fallback policy.
- **Phase 1 — daemon endpoint:** `/api/infer-schema-from-data` reusing engine inference;
  Kotlin tests per input format → target schema; run the daemon suite.
- **Phase 2 — IDE/MCP delegation:** `UTLXService.inferSchemaFromData(...)`; the panels'
  "Infer Schema" button calls it (async, with the chosen fallback); demote/retire
  `schema-inferrer.ts`.

## Acceptance Criteria

- The IDE can infer a schema from instance data via utlxd for json/yaml/csv/xml/odata →
  the appropriate schema format, matching (or improving on) today's browser output.
- The "Infer Schema" button behaves correctly with the chosen fallback when the daemon is
  unavailable.
- **utlxe and CLI unchanged** — no file under `modules/engine`, `modules/cli`,
  `modules/analysis`, `formats/*` modified (read/reuse only).
- The decision gate is recorded (which surfaces migrated and why).

## Testing

- **Kotlin (daemon):** data→schema per input format → expected schema, incl. nested
  objects, arrays-of-objects, optional/nullable, CSV type-sniffing; round-trip against
  `examples/<fmt>` data + its known schema.
- **IDE:** the "Infer Schema" button produces the same structure as before (or the gate's
  documented improvement); offline-fallback path works.
- **Regression:** daemon `gradlew test`; utlxe/CLI suites untouched.

## Related

- **IF12** (utlxd schema *parse*) — the sibling direction; same architecture + isolation.
- **IF11** (MCM AI assist) — consumer of consistent schema handling.
- **IB03** — the browser↔engine divergence class this (and IF12) removes.
- **EF02** (engine schema validation) — engine-side; *contrast*: IF13 is IDE-stack, no
  utlxe change.
- `/api/infer-schema` (existing) — transformation→output-schema; **not** to be overloaded.

## Effort Estimate

Medium-Large, gated. Phase 0 (comparison) small but decisive. Phase 1 (daemon endpoint +
tests) medium. Phase 2 (delegation + fallback) medium. Sequence after IF12 (shared
`TypeNode`/mapping plumbing can be reused).
