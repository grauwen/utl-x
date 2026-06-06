# IF16: IDE — Shell Layout (navigators + a persistent Mapping editor; Bundle = MC mapping manager)

> **Command surfaces** (menu bar · toolbar · status bar) are specified in **[IF18: IDE Menu &
> Chrome Structure](IF18-ide-menu-and-chrome-structure.md)**. IF16 owns the *panel/editor layout*;
> IF18 owns the *menus and chrome* around it.

**Status:** Design (corrected June 2026). **Supersedes** the earlier attempts on this branch
(a single composite `MappingWorkbenchWidget` in `main`, a fake "Mapping launcher" icon, and a
`PerspectiveService` that hid/resized `mainPanel`). Those were **not the right code** — they
faked an Eclipse-style "perspective" layer that Theia does not have, and fought the framework.
This document is the design to implement against; the prior code should be reverted.
**Priority:** High (defines the IDE's information architecture)
**Created:** June 2026
**Updated:** June 2026 (redesigned to the idiomatic navigator + persistent-editor model)
**Component:** IDE stack (Theia extension) — shell areas, activity-bar views, the mapping editor
**Depends on:** the mapping panels (input/editor/output, `UTLXEventService`), IF03 (Bundle editor)
**Effort:** Medium

> **The model (authoritative).** The IDE follows the **idiomatic Theia/VS Code model**:
> **navigators on the left, a persistent editor in the middle.** There is **no perspective
> swap** and **no custom shell orchestration**.
>
> - **MAIN area = the Mapping editor** — the *currently selected* transformation's mapping:
>   **input(s) + UTL-X transform + output**. It is **always present** ("the UTL-X editor is
>   always on top"); nothing a navigator does hides it.
> - **LEFT side panel (activity bar) = navigators** — **File Explorer** and the **Bundle
>   editor**. Theia swaps these natively (one at a time).
> - **The Bundle editor is the Message-Contract *mapping manager*.** It lists the bundle's
>   transformations. **Selecting a transformation loads its UTL-X + input(s) + output into the
>   Mapping editor** (refreshes all three). The Bundle context is **always Message-Contract
>   mode**.
> - **Execution mode is separate** — standalone testing of a single transformation with sample
>   data. It is **not** part of the Bundle context. The Execution ↔ Message-Contract switch
>   applies only to the standalone flow; under the Bundle the mapping is always MC.
> - **Config files** (`config.yaml` / `transform.yaml` / `engine.yaml`) opened from the Bundle
>   open as ordinary editors — beside the mapping (a `main` split) or in the bottom panel.

---

## Summary

The IDE is **navigators + a persistent editor**, the way every Theia/VS Code app is built. The
**Mapping editor** (input + UTL-X transform + output) lives in the **main** area and is always
present. The **Bundle editor** and **File Explorer** are **left side-panel navigators**.
Selecting a transformation in the **Bundle editor** — which is the **Message-Contract mapping
manager** — loads that transformation's UTL-X, inputs, and output into the Mapping editor, the
same way clicking a file in the Explorer opens it in the editor. No perspective machinery.

## Problem (what went wrong before)

Earlier work tried to make selecting an activity-bar item swap the *whole* working area
(Eclipse "perspectives"). **Theia has no perspectives**, and **activity-bar icons exist only
for left/right side views** — so faking it required a launcher widget + `mainPanel` hide/show +
flexbox composites that couldn't size Lumino split panes. The result was fragile and wrong.
The correct model is the native one below.

## Goals

- The **Mapping editor** (input + UTL-X + output) is the **persistent main surface**.
- **Bundle editor** and **File Explorer** are **left navigators** (native activity-bar swap),
  and never hide the Mapping editor.
- The **Bundle editor lists transformations**; **selecting one loads its UTL-X + inputs +
  output** into the Mapping editor.
- The **Bundle context is always Message-Contract**; **Execution mode** is a separate
  standalone flow.
- Config files open from the Bundle as ordinary editors.
- Preserve all existing panel behavior + `UTLXEventService` wiring.

## Non-Goals

- Eclipse-style perspectives / full-area swap (Theia has none; do not fake it).
- Re-implementing the panels' internals (input/editor/output keep their logic).
- Multi-mapping at once (one active transformation; the Bundle switches which one).

## Design

### Areas (native Theia, no custom orchestration)
- **MAIN = Mapping editor.** The selected transformation's **input(s) + UTL-X transform +
  output**. Always present. (Implementation: a horizontal arrangement of the three panes in
  the main area — either the existing widgets via native `main` splits, or one composite
  `SplitWidget`. See "Open decision".)
- **LEFT = navigators.** File Explorer (Theia built-in) + **Bundle editor** (IF03) as
  `AbstractViewContribution` views with activity-bar icons. Theia swaps them natively.
- **Config/file editors** opened from a navigator land in `main` (split beside the mapping) or
  the **bottom** panel — they do not replace the mapping.
- **Toolbar** = the mapping's controls (Execute/Validate, AI assist, and — standalone only —
  the Execution ↔ MC switch). It belongs to the mapping editor; show it with the mapping.

### The Bundle editor = Message-Contract mapping manager (the key behavior)
- Lists the bundle's transformations (IF03 already models the tree).
- **Selecting a transformation** reads its `.utlx`, resolves its input sample(s) and output
  contract, and **loads all three into the Mapping editor** — replacing the current UTL-X,
  inputs, and output. (This is exactly the navigator→editor pattern; IF03's
  "open transformation" already does most of this — it must also drive the output.)
- The Bundle context is **always MC**: the mode switch is fixed to / hidden in favor of
  Message-Contract; Execution is not offered from the Bundle.

### Execution vs Message-Contract
- **Message-Contract (Bundle):** design-time contract mapping; the Bundle editor manages these.
- **Execution (standalone):** run a single transformation against sample data to test output.
  Driven by the toolbar's mode switch *outside* the Bundle context, not by the Bundle editor.

### Why this is correct (vs. the abandoned approach)
- It is the **standard Theia model**: navigator opens a document into a persistent main editor.
- **No `PerspectiveService`, no launcher, no `mainPanel` hide/resize, no flexbox/Lumino
  composite fights.** Everything maps to native Theia primitives.

## Open decision (one — needs sign-off before coding)
**Where do the input pane(s) live?**
- **(A, recommended) In the main Mapping editor** (input | UTL-X | output as the editor
  surface). Cohesive with "select transformation loads all three"; one editor reload swaps
  everything. Closest to "the mapping is one document".
- **(B) As a left navigator view** (the multi-input pane gets its own activity-bar icon).
  Keeps `main` to UTL-X + output, but then a Bundle selection updates a *hidden* left view.

Recommend **A**.

## Implementation Notes
- **Bundle editor** (IF03): left `AbstractViewContribution` view; on transformation select →
  load UTL-X (editor `setContent`) + input samples (`loadBundleSamples`) + output contract.
  Force MC mode for the loaded mapping.
- **Mapping editor**: input/editor/output in `main` (native split or one `SplitWidget`); always
  present. Reuse the existing widgets + `UTLXEventService` wiring.
- **No** perspective controller, launcher, or shell hide/show.
- Build the layout in `onDidInitializeLayout` (after the shell layout settles), not `onStart`.

## Acceptance Criteria
- The Mapping editor (input + UTL-X + output) is always present in `main`.
- File Explorer + Bundle editor are left navigators with activity-bar icons; opening either
  never hides the Mapping editor.
- **Selecting a transformation in the Bundle editor refreshes the UTL-X, the input(s), and the
  output** in the Mapping editor.
- The Bundle context is always Message-Contract (no Execution mode there).
- Config files opened from the Bundle edit beside the mapping (or in the bottom panel) without
  hiding it.
- Existing mapping behavior (panels, events, AI assist, MCM coverage) is unchanged.

## Testing
- **Bundle selection:** click several transformations → UTL-X, inputs, output all refresh to
  the selected one each time.
- **Navigators:** open File Explorer / Bundle → Mapping editor stays visible.
- **Config editing:** open `config.yaml` from the Bundle → edits beside the mapping.
- **Modes:** Bundle context is MC; the Execution/MC switch only affects the standalone flow.

## Related
- **IF03** — Bundle editor / project model — the MC mapping manager described here.
- **IF04** — config editor (`transform.yaml`/`engine.yaml`) opened from the Bundle.
- **IF08–IF13** — the mapping/MCM features used in the Mapping editor.
- **IF07 / IF14** — desktop & cloud builds.

## Effort Estimate
Medium. The model is native, so most effort is wiring the Bundle editor's "select transformation
→ load UTL-X + inputs + output (MC)" and arranging the mapping editor in `main`. No custom
shell orchestration.
