# IF05: IDE — Bundle Operations & Messaging Topology

**Status:** Proposed
**Priority:** Medium
**Created:** May 2026
**Depends on:** IF03 (Bundle Project Model), IF04 (Transform Config Editor), EF09 (Bundle Mode)
**Effort:** Large (3-4 weeks)

---

## Summary

With the bundle as the IDE's project unit (IF03) and per-transformation config
editable (IF04), IF05 adds the **bundle-wide** operations and views: validate and
test the whole bundle, edit/inspect the manifest, and visualize the **messaging
topology** — how the bundle's transformations are wired together via queues and
topics. The topology view is the one genuinely new surface beyond the
Input | Transformation | Output editor.

This is Phase C of the "Bundle-Level IDE" roadmap in
`theia-extension-design-with-design-time.md`.

## Problem

A bundle is more than a set of independent transformations — it is a *pipeline*.
Today there is no way in the IDE to:

- Validate or test the bundle as a whole (only one transformation at a time).
- See the manifest (the aggregate of all `transform.yaml` schema refs + messaging).
- Understand how transformations connect: which queue/topic one writes that another
  reads — the integration topology that the `messaging` manifest summary encodes.

```json
"messaging": {
  "queues": ["orders-in", "orders-out"],
  "topics": [ { "name": "raw-invoices", "subscription": "utlxe-transform" },
              { "name": "normalized-invoices" } ]
}
```

## Goals

- **Validate All** — validate every transformation in the bundle, check that all
  schema references resolve, and lint the manifest; report results per
  transformation in one panel.
- **Test All** — run a representative sample through each transformation
  (Execution-mode, one sample each), summarizing pass/fail across the bundle.
- **Manifest view** — read (and where safe, edit) `manifest.json`; regenerate it
  from the current `transform.yaml` set so it never drifts.
- **Messaging topology diagram** — a node/edge graph of transformations connected
  by shared queue/topic names (output binding of A == input binding of B → edge).
- Surface all of the above from the **UT­L-X menu** and command palette.

## Non-Goals

- Executing the bundle against live Azure Service Bus / Event Hub — the IDE tests
  logic with samples; real messaging is a deployment concern.
- Editing secrets/auth — those live in the environment, never the bundle.
- Replacing per-transformation testing (Execution mode) — IF05 aggregates it.

## Design

**Validate All / Test All** iterate the bundle's transformations (from IF03's
model), reusing the existing single-transformation validate/execute paths against
`utlxd`, and aggregate results into a **Bundle Results** panel (tree: transformation
→ status → diagnostics). Schema-reference resolution and manifest lint are
bundle-level checks layered on top.

**Manifest view.** The manifest is *derived* — the safest model is "regenerate
from `transform.yaml` + `schemas/`," shown read-mostly, with the build step (IF03)
as the writer. Direct manifest editing, if offered, is constrained and re-validated
against the actual transformations to prevent drift.

**Messaging topology diagram.** Build a graph from the manifest's messaging summary:
nodes = transformations (+ external queue/topic endpoints); edges = a name written
by one transformation's output binding and read by another's input binding. Render
with the IDE's existing diagram capability (the editor already has a canvas/diagram
mode used in Message Contract mode — reuse that rendering layer rather than adding
a new graph library). This makes the integration flow visible at a glance and is
the headline value of Phase C.

**Menu & commands.** Add the top-level **UT­L-X** menu (or extend it if IF03
created it): New Bundle · Open Bundle · Build `.utlar` · Validate Bundle · Test All ·
New Transformation · Add Schema · Edit Manifest · Show Topology. Theia's
contribution model (menus/commands/keybindings) covers this directly.

## Implementation Notes

- New views: `bundle-results-widget.tsx` (validate/test aggregation),
  `manifest-view.tsx`, `topology-diagram-widget.tsx` under `browser/bundle-ops/`.
- Backend: `validateBundle()` / `testBundle(samples)` on `UTLXService`, iterating
  IF03's bundle model and delegating to existing daemon validate/execute per
  transformation; `getManifest()` / `regenerateManifest()`.
- Topology graph derivation is pure data (manifest in → nodes/edges out); keep it
  testable independently of rendering.
- Reuse the editor's canvas/diagram renderer (Message Contract mode) for the
  topology view to avoid a new dependency.
- Commands/menus via a Theia `CommandContribution` + `MenuContribution`.

## Acceptance Criteria

- "Validate All" reports per-transformation results and flags any unresolved schema
  reference or manifest inconsistency.
- "Test All" runs a sample through each transformation and shows a pass/fail summary.
- The manifest view reflects the current bundle and can be regenerated without drift.
- The topology diagram shows transformations and their queue/topic connections;
  an output→input name match renders as an edge.
- All operations are reachable from the UT­L-X menu and command palette.

## Testing

- Unit: topology derivation from a fixture manifest (nodes/edges correct, dangling
  endpoints shown); manifest regeneration matches the build output.
- Integration: a multi-transformation fixture bundle → Validate All (inject a bad
  schema ref, assert it's flagged) → Test All (assert per-transformation results) →
  Show Topology (assert edges).

## Related

- Design: `theia-extension-design-with-design-time.md` §"Bundle-Level IDE" (Phase C)
- IF03 (bundle project model), IF04 (transform.yaml editor)
- EF09 (manifest + messaging summary)

## Effort Estimate

Large (3-4 weeks): validate/test-all aggregation + results panel (~1 wk), manifest
view + regeneration (~0.5 wk), topology derivation + diagram reusing the canvas
renderer (~1.5 wk), menu/commands + tests (~1 wk).
