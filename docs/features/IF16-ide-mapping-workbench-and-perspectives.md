# IF16: IDE — Mapping Workbench + Activity-Bar Perspectives (Mapping / Bundle / Files)

**Status:** Proposed
**Priority:** High (fixes the core layout conflict; foundational for the IDE's information architecture)
**Created:** June 2026
**Component:** IDE stack (Theia extension) — shell layout, view containers, the 3-panel mapping
**Depends on:** the existing mapping panels (input/editor/output, `UTLXEventService`), IF03 (Bundle Explorer)
**Effort:** Large (a layout redesign; phased)

> **Design decisions captured here (not yet implemented):**
> - The UTL-X **mapping** (Input | Transform | Output + toolbar) should be **one cohesive
>   workbench**, not three widgets scattered across Theia's `left`/`main`/`right` shell areas.
>   It must get its **own activity-bar icon**.
> - The **top bar belongs to the mapping**, not the IDE. It currently lives in the global
>   `top` shell area but contains **only mapping controls** — the **Execution ↔ Message
>   Contract mode switch**, **Execute/Validate**, and **AI assist** (Generate / Map). It must
>   move **into the Mapping workbench** and be shown **only in the Mapping perspective**
>   (hidden in Bundle / Files), so a global bar doesn't linger over the explorers.
> - The **File Explorer** and **Bundle Explorer** are **sidebars** (activity-bar view
>   containers) — each its own icon — and must **not** dismember the mapping.
> - Selecting an activity-bar item **swaps the full working area** (a *perspective* model):
>   Mapping ⇄ Bundle ⇄ Files. Theia has **no native perspectives**, so this is **custom
>   `ApplicationShell` orchestration** — the trickiest piece.
> - Implement in **safe phases**; the Bundle-Explorer-own-icon step is a quick early win.

---

## Summary

Today the mapping is **three separate widgets in three shell areas** — Input → `left`,
Transform (editor) → `main`, Output → `right`, toolbar → `top`. Because the **activity bar
controls the `left` side panel**, the File Explorer and the Bundle Explorer (also `left`
views) **compete with the Input pane for the same slot** — selecting their icon tears the
Input pane off the mapping. IF16 restructures the IDE into **perspectives**: the **Mapping**
becomes a single composite workbench (its own activity-bar icon) in the editor area; the
**Bundle Explorer** and **File Explorer** are proper activity-bar sidebars; and selecting an
activity-bar item **swaps the whole working area** rather than cannibalizing the mapping.

## Problem

- The mapping's **Input pane lives in the `left` side panel**, the slot the activity bar
  swaps. So opening **Files** or the **Bundle Explorer** *replaces the Input pane*, breaking
  the triptych. (Root cause.)
- The mapping is **not a cohesive unit** — its three panes + toolbar are independent shell
  widgets, so they can't be shown/hidden/swapped as one.
- There's **no "Mapping" entry** on the activity bar; and no clean way to switch *fully*
  between the mapping and the explorers.

## Goals

- The **Mapping workbench** (Input | Transform | Output + toolbar) is **one unit** with its
  **own activity-bar icon**; selecting it shows the toolbar + the three panes.
- **Bundle Explorer** and **File Explorer** are **separate activity-bar view containers**
  (own icons) and never replace the Input pane.
- Selecting an activity-bar item **swaps the full working area** (perspective behavior):
  Mapping shows the triptych; an explorer shows its tree full, the mapping hidden.
- Preserve the existing panel behavior + `UTLXEventService` wiring (no functional regression
  in the mapping itself).

## Non-Goals

- Re-implementing the panels' internals (input/editor/output keep their current logic).
- A general Eclipse-style perspective *framework* — just the three named perspectives here.
- Multi-mapping / N-triptych (still one active transformation; see IF03's "navigate-N"
  decision).

## Design

### Root fix: get the mapping out of the activity-bar-controlled `left` panel
The Input pane must stop living in the `left` side panel. Two viable structures:

**(Recommended) A composite Mapping workbench in the `main` area.** A
`MappingWorkbenchWidget` hosts Input | Transform | Output in a **split layout** (Theia
`SplitPanel`/Phosphor) with the toolbar on top — a single widget in the editor area. The
three existing widgets become its children (same instances; `UTLXEventService` wiring
unchanged). The `left` side panel is then **free** for sidebars.

**The top bar moves with the mapping.** The `UTLXToolbarWidget` (mode switch / Execute /
Validate / AI assist) is today added to the **global `top` shell area** — so it would hover
over *every* perspective. It must instead be **embedded at the top of the Mapping
workbench** (or shown/hidden with the Mapping perspective), so the mode switch and run/AI
controls appear **only** when the mapping is active. (It's entirely mapping-scoped already;
nothing in it applies to the Bundle/Files perspectives.)

### Activity-bar sidebars (own icons)
- **File Explorer** — Theia's default left view container (already an activity-bar item).
- **Bundle Explorer** — its **own** left view container + activity-bar icon (IF03), no longer
  stacked with the Input pane.

### The "Mapping" activity-bar item + perspective swap (the hard part)
Theia's activity bar natively toggles **left view containers**; the Mapping is a `main`-area
workbench, not a left view. So a **perspective controller** (custom) drives the activity bar:
- An activity-bar **"Mapping"** icon (a command/toggle, custom item) → show the Mapping
  workbench, collapse the side panels.
- **Bundle / Files** icons → expand that sidebar; per the full-swap requirement, **hide the
  Mapping** workbench (collapse/hide `main`) so the explorer is the whole area; re-show the
  Mapping when its icon is selected.
- Mechanism: `ApplicationShell` APIs — `activateWidget`, panel `collapse()`/`expand()`,
  widget `hide()`/`show()`, and (optionally) `getLayoutData`/`setLayoutData` saved per
  perspective. This is **custom orchestration** (Theia has no perspectives) and the main risk
  to iterate on live.

### The Mapping icon
No exact `mapping` codicon exists. Options: a **custom SVG** (recommended — view containers
accept a custom `iconClass`; best for branding), or a codicon stand-in (`arrow-swap`,
`git-compare`, `type-hierarchy` (input→output), or `symbol-namespace`).

## Implementation Notes (phased)
- **Phase 1 (quick win, low risk):** Bundle Explorer → its **own** left view container +
  activity-bar icon (an `AbstractViewContribution`), so it stops replacing the Input pane.
- **Phase 2 (the redesign):** build `MappingWorkbenchWidget` (split layout hosting
  input/editor/output + the toolbar) in `main`; move the three panes off the `left`/`right`
  shell areas **and the toolbar off the global `top` area** into it; keep `UTLXEventService`
  wiring. Frees the side panels for sidebars and scopes the mode switch / run / AI controls
  to the mapping.
- **Phase 3 (perspectives):** the perspective controller + the Mapping activity-bar icon +
  full-area swap via `ApplicationShell` (collapse/expand/hide/show; optional saved layouts).
- The default startup perspective = **Mapping**.

## Acceptance Criteria
- The activity bar shows distinct icons for **Mapping**, **Bundle Explorer**, and **Files**.
- Selecting **Bundle/Files never removes the Input pane** — the mapping is intact when you
  return to it.
- Selecting **Mapping** shows the toolbar + Input | Transform | Output as one workbench.
- The **top bar (mode switch / Execute-Validate / AI assist) appears only in the Mapping
  perspective** — it is **not** shown over the Bundle or Files perspectives.
- Selecting an **explorer** swaps the full area to that tree (mapping hidden), and back.
- The mapping's existing behavior (panels, events, AI assist, coverage) is unchanged.

## Testing
- **Manual/layout:** switch Mapping ⇄ Bundle ⇄ Files repeatedly — no torn panes, no stale
  state; mapping restores fully each time.
- **Regression:** input/output/editor sync, AI assist, MCM coverage still work inside the
  composite workbench.
- **Persistence:** perspective + layout survive reload (ties to IF03/IF09 file-backed state).

## Related
- **IF03** — Bundle Explorer (becomes its own activity-bar perspective here).
- **IF07 / IF14** — desktop & cloud builds (the activity-bar set may differ per build; cloud
  may drop Files).
- **IF08–IF13** — the mapping features that live inside the Mapping workbench.
- IF09 / IF03 — file-backed layout/session state for perspective persistence.

## Effort Estimate
Large, phased. Phase 1 (Bundle-Explorer own icon) is small and an immediate UX fix. Phase 2
(composite Mapping workbench) is the substantial refactor. Phase 3 (perspective swap) is
custom `ApplicationShell` work and needs live iteration — sequence it last and gate on
Phase 2.
