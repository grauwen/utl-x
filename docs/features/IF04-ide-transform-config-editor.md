# IF04: IDE — Per-Transformation Config Editor (`transform.yaml`)

**Status:** Proposed
**Priority:** High
**Component:** IDE (Theia Extension)
**Depends on:** IF03 (Bundle Project Model & Explorer), EF09 (Production Bundle Mode)
**Effort:** Medium (2-3 weeks)

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
- IF03 (bundle project model), IF05 (bundle ops & topology)
- EF09 (`transform.yaml` schema), engine `TransformConfig`

## Effort Estimate

Medium (2-3 weeks): config form + YAML round-trip (~1 wk), schema-ref picker +
validation reusing IF03 model (~0.5 wk), config-schema endpoint from engine +
tests (~1 wk).
