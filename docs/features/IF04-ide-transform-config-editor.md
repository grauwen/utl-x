# IF04: IDE — Per-Transformation Config Editor (`transform.yaml`)

**Status:** Proposed — *foundations landed (June 2026)*: JSON Schemas for both config files
exist (`docs/api/config/transform-config.schema.json`, `engine-config.schema.json`), and the
Bundle Explorer (IF03) now **opens `transform.yaml` and `engine.yaml` for editing** (text).
Remaining: **schema-assisted** editing (validation + completion) and an optional form UI.
**Priority:** High
**Component:** IDE (Theia Extension)
**Depends on:** IF03 (Bundle Project Model & Explorer), EF09 (Production Bundle Mode), the
config JSON Schemas in `docs/api/config/`
**Effort:** Medium (2-3 weeks)

> **Config contract = JSON Schema (the editing foundation).** Both YAML configs now have
> authoritative JSON Schemas derived from the engine types:
> - **`transform.yaml`** → `docs/api/config/transform-config.schema.json` (`TransformConfig`:
>   `strategy` TEMPLATE/COPY/COMPILED, `validationPolicy` OFF/SKIP/WARN/STRICT, `inputs[]`,
>   `output`, `maxConcurrent` (EF21), `maxInputSize`, and the **EF10 messaging** endpoints
>   `input` / `output_messaging` — Service Bus queue/topic or Event Hub via Dapr).
> - **`engine.yaml`** → `docs/api/config/engine-config.schema.json` (`EngineConfig`).
>
> These are **verified** against every `examples/utlxe/*.utlxp` config (incl. the messaging
> example `invoice-routing.utlxp`). The contract is done; this feature wires it as the
> editor's validation/completion source.
>
> **Editing today:** the Bundle Explorer opens `transform.yaml` (the per-transformation ⚙)
> and `engine.yaml` (project node) as plain text editors.
>
> **Editing target (this feature) — use a YAML language server, NOT `monaco-yaml`.**
> Investigated June 2026: `monaco-yaml` is **incompatible** with Theia 1.64, which uses
> `@theia/monaco-editor-core` (its own repackaged Monaco) + Theia's worker/LSP model rather
> than the standalone `monaco-editor` package `monaco-yaml` targets — wiring it risks worker
> conflicts/build breakage. A hand-rolled ajv-on-parsed-object validator is also insufficient
> on its own: ajv validates the *parsed* object and loses source positions, so diagnostics
> can't point at the offending YAML line (everything lands on line 1). The correct path is
> the **`yaml-language-server`** (redhat) wired via **`MonacoLanguageClient`**, registering
> the two schemas (`docs/api/config/*.schema.json`) for `transform.yaml` / `engine.yaml` by
> filename — it carries the CST + positions, giving real inline validation **and** completion
> (incl. the messaging fields). Optionally add a form UI for the common fields (strategy,
> validation policy, messaging endpoints) on top.
>
> **Done already (the hard part):** the two JSON Schemas exist + are verified against every
> `examples/utlxe/*.utlxp` config (incl. `invoice-routing.utlxp`), and the explorer already
> opens the files. Remaining = the LSP wiring + schema registration (this feature).

---

## Summary

Each transformation in a bundle has a `transform.yaml` that configures its
**execution strategy**, **validation policy**, **input/output schema references**,
and **messaging** (input/output queue/topic names). The IDE has no UI for any of
this today. IF04 adds a per-transformation config editor, surfaced from the Bundle
Explorer (IF03), so developers can configure a transformation without hand-editing
YAML.

This is Phase B of the "Bundle-Level IDE" roadmap.

## Problem

`transform.yaml` is where a transformation becomes deployable:

```yaml
strategy: COMPILED            # TEMPLATE | COPY | COMPILED | AUTO
validationPolicy: strict      # strict | warn | off
inputs:
  - name: order
    schema: order.json        # references schemas/ in the bundle
output:
  schema: invoice.xsd
input:  { queue: orders-in }              # messaging in
output: { queue: orders-out }             # messaging out
```

Without IDE support, users must know the YAML schema by heart, schema references
are unchecked (a typo silently breaks loading), and the strategy/validation
trade-offs documented elsewhere are invisible at the point of decision.

## Goals

- A form-based **config editor** per transformation (opened from the Bundle
  Explorer or the editor toolbar).
- Edit: `strategy` (dropdown with the four values), `validationPolicy`, per-input
  and output **schema references** (picker constrained to the bundle's `schemas/`),
  `maxInputSize`, `maxConcurrent`, and **messaging** in/out (queue/topic/eventhub).
- Validate schema references against the actual files in `schemas/` (flag dangling
  references).
- Two-way: form edits write `transform.yaml`; external YAML edits refresh the form.
- Inline guidance: short descriptions of strategy trade-offs (link to the engine
  lifecycle chapter) so the choice is informed.

## Non-Goals

- Bundle-wide manifest/topology editing — that is IF05.
- Inventing config fields not supported by the engine's `TransformConfig` — the
  editor mirrors the real schema, nothing more.
- Secrets/auth for messaging — by design those live in the environment, never in
  `transform.yaml` (EF09).
- **Collaborative / concurrent editing.** Once transformations are file-backed,
  multiple frontends (two tabs, or two browsers) on the same Theia backend can open
  the *same* file. Theia's browser-app is not an OT/CRDT collaborative editor, so this
  is **last-write-wins** with file-watcher events crossing over — not resolved here.
  (Today this cannot occur: the editor is an in-memory scratch buffer per frontend —
  see IF09.) If real multi-client editing is ever required, it needs its own design.

## Design

**Source of truth is `transform.yaml`.** The editor is a typed view over the YAML;
it never holds config the file cannot represent. The field set is derived from the
engine's `TransformConfig` (Kotlin) so the IDE and engine never diverge — expose
the field/enum metadata via a daemon endpoint (or a generated schema) rather than
duplicating it in TS.

**Schema reference picker.** Inputs/output schema fields are dropdowns populated
from the bundle's `schemas/` directory (IF03 already models it). Selecting a schema
writes the filename; a missing/renamed file is flagged in the form and in the
Bundle Explorer.

**Strategy field.** Dropdown: `TEMPLATE | COPY | COMPILED | AUTO` with one-line
trade-off hints (init cost vs. throughput; see the engine lifecycle chapter).
`COPY + COMPILED` is **not** offered as a value — it is what `COPY` does
automatically — matching the documented engine behavior.

**Messaging.** Group for input and output bindings: type (queue / topic+subscription
/ eventhub) + name. These feed the bundle manifest's `messaging` summary at build
time (IF03 build / IF05 topology).

## Implementation Notes

- New widget: `transform-config-editor.tsx` under `browser/bundle-config/`,
  opened as a tab or side panel for the selected transformation.
- Backend: `readTransformConfig(name)` / `writeTransformConfig(name, cfg)` on
  `UTLXService`, plus `getTransformConfigSchema()` returning field/enum metadata
  derived from the engine's `TransformConfig` (single source of truth).
- Reuse the bundle model from IF03 for the `schemas/` list and dangling-reference
  detection.
- YAML round-trip must preserve comments/ordering where feasible (use a
  comment-preserving YAML lib, or write a minimal targeted patch rather than a
  full reserialize).

## Acceptance Criteria

- Selecting a transformation exposes an editable config form reflecting its
  `transform.yaml`.
- Changing strategy / validation / messaging / schema refs writes valid YAML the
  engine loads without error.
- A schema reference to a non-existent file is flagged (form + explorer).
- Editing the YAML by hand and reopening the form shows the new values.
- No field appears that the engine's `TransformConfig` does not support.

## Testing

- Unit: form ↔ YAML mapping for each field; enum constraints; dangling-reference
  detection.
- Integration: edit config → build bundle (IF03) → engine loads with the chosen
  strategy/validation (assert via daemon engine-info / EF19 introspection).
- Round-trip: hand-edited YAML with comments survives a form save.

## Related

- Design: `theia-extension-design-with-design-time.md` §"Bundle-Level IDE" (Phase B)
- IF03 (bundle project model) — incl. §"Document persistence — fully Theia/Monaco-compliant"
  (the `.utlx` opens as a file-backed editor; samples via `FileService`)
- IF05 (bundle ops & topology)
- IF09 (session persistence) — the `sessionStorage` stopgap this file-backing supersedes
- EF09 (`transform.yaml` schema), engine `TransformConfig`

## Effort Estimate

Medium (2-3 weeks): config form + YAML round-trip (~1 wk), schema-ref picker +
validation reusing IF03 model (~0.5 wk), config-schema endpoint from engine +
tests (~1 wk).
