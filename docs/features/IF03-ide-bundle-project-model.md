# IF03: IDE — Bundle Project Model & Explorer

**Status:** Proposed
**Priority:** High
**Component:** IDE (Theia Extension)
**Depends on:** EF09 (Production Bundle Mode `.utlar`), EF03 (Bundle Management API)
**Effort:** Large (3-4 weeks)

---

## Summary

The IDE currently edits a **single** transformation (Input | Transformation |
Output). The unit the engine loads, CI/CD ships, and the book describes is the
**`.utlar` bundle** — an integration *project* containing N transformations, a
shared `schemas/` directory, and a manifest. IF03 introduces the bundle as the
IDE's project unit: open a `.utlar` (or its expanded directory), browse its
contents in an explorer, open transformations as tabs, and build/export a `.utlar`.

This is Phase A of the "Bundle-Level IDE" roadmap in
`theia-extension-design-with-design-time.md`.

## Problem

There is a structural mismatch between what you can author and what you ship:

- The engine runs bundles (`BundleLoader.kt`, EF09); production is always a `.utlar`.
- The IDE models exactly one transformation, with no notion of the project tier
  above it (multiple transformations, shared schemas, manifest, messaging topology).
- You cannot open, edit, test, or build the actual deployable artifact in the IDE.

A `.utlar` (ZIP) has a known structure:

```
bundle.utlar:
  manifest.json
  transformations/
    orders-in/      { orders-in.utlx, transform.yaml }
    invoice-to-ubl/ { invoice-to-ubl.utlx, transform.yaml }
  schemas/          { order.json, invoice.xsd }   ← shared across transformations
```

## Goals

- Recognize and open a `.utlar` file **or** an expanded bundle directory as a project.
- A **Bundle Explorer** view-container: tree of transformations, shared `schemas/`,
  and the manifest.
- Open a transformation as an editor **tab**; the existing three-panel
  (Input | Transformation | Output) view binds to the **active** transformation.
- **Build / Export `.utlar`** from the working directory (ZIP + manifest generation).
- Surface the shared `schemas/` directory at project level (not per-transformation).
- Work in both the web (`browser-app`) and future Electron shells.

## Non-Goals

- N concurrent live three-panel editors — explicitly rejected (see Design).
- Editing `transform.yaml` fields via UI — that is IF04.
- Validate-All / Test-All and the messaging topology view — that is IF05.
- Changing the single-transformation editor itself (it is reused as-is).

## Design

**Decision — navigate N, do not run N live editors.** Support N transformations
in one project, navigable and individually editable/testable, but **not** N
simultaneous Input|Transformation|Output triptychs (a resource/UX trap: N daemons,
N previews). Use the standard IDE pattern: project tree → open as tabs → the
three-panel view + live preview/daemon execution bind to the **active**
transformation only.

**Project recognition.** A folder is a UTL-X bundle project when it contains
`manifest.json` + `transformations/` (or a `.utlar` is opened, transparently
expanded to a workspace temp dir). Theia is already a workspace IDE, so a bundle
project is "a folder with a known structure" — most of this is recognition and
surfacing, not new infrastructure.

**Bundle Explorer** (Theia `ViewContainer` + `TreeWidget`):

```
▾ bundle: sales.utlar
  ▾ transformations
      orders-in           (orders-in.utlx + transform.yaml)
      invoice-to-ubl
  ▾ schemas (shared)
      order.json
      invoice.xsd
    manifest.json
```

Selecting a transformation opens/activates its tab and points the three panels at
it. Selecting a schema or the manifest opens a plain editor (IF05 adds richer views).

**Build / Export.** A "Build `.utlar`" command zips the working directory and
(re)generates `manifest.json` (aggregating each `transform.yaml`'s schema refs and
messaging) — mirroring the CLI/`EF03` bundle build so IDE output is byte-compatible
with CI/CD.

## Implementation Notes

- New view: `bundle-explorer-widget.tsx` (TreeWidget) under `browser/bundle-explorer/`.
- New backend service methods (extend `UTLXService` in `common/protocol.ts`):
  `openBundle(path)`, `listBundle()`, `buildBundle(outPath)` — implemented in
  `node/services/` against the filesystem + the daemon/CLI bundle builder.
- Reuse the daemon's existing bundle logic where possible: `BundleLoader` (discover)
  and the EF03 bundle build path, rather than re-implementing manifest generation
  in TS.
- The active-transformation binding reuses the existing event bus
  (`UTLXEventService`) — opening a tab fires the same input/output wiring the
  single editor uses today.
- `.utlar` open = expand ZIP to a temp workspace dir; "Build" re-zips. Editing is
  always against the expanded directory (open mode), never the ZIP in place.

## Acceptance Criteria

- Opening a `.utlar` or bundle directory shows the Bundle Explorer with all
  transformations, shared schemas, and the manifest.
- Clicking a transformation opens it; the three panels operate on it; switching
  transformations switches context cleanly (no stale state).
- "Build `.utlar`" produces an archive the engine loads identically to a
  CI/CD-built bundle (manifest + structure match EF09).
- Exactly one transformation is "active" at a time; no N-way live previews.
- Works in `browser-app`; no frontend→service-port coupling (all via JSON-RPC).

## Testing

- Unit: bundle recognition (valid/invalid structure), manifest generation matches
  the CLI builder for a fixture project.
- Integration: open fixture `.utlar` → explorer tree assertions → open each
  transformation → build → diff rebuilt `.utlar` against engine `BundleLoader`.
- Round-trip: open → edit a `.utlx` → build → reload → change persists.

## Related

- Design: `theia-extension-design-with-design-time.md` §"Bundle-Level IDE" (Phase A)
- EF09 (Production Bundle Mode), EF03 (Bundle Management API)
- IF04 (transform.yaml editor), IF05 (bundle ops & topology)

## Effort Estimate

Large (3-4 weeks): explorer view + project recognition (~1.5 wk), build/export +
manifest generation reusing engine logic (~1 wk), active-transformation tab
wiring + tests (~1 wk).
