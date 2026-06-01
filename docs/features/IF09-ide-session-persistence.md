# IF09: IDE — Session Persistence (survive browser refresh)

**Status:** Proposed
**Priority:** High
**Created:** June 2026
**Depends on:** existing input/output/editor/toolbar widgets; Theia `StatefulWidget`; `sessionStorage`
**Effort:** Small (3-5 days)

> **Design decisions captured here (not yet implemented):**
> - Persist the **work product** across a browser refresh via `StatefulWidget`
>   (`storeState`/`restoreState`), backed by **`sessionStorage`** (NOT localStorage).
>   `sessionStorage` is **per-tab** and survives refresh — so two tabs open on the
>   same IDE can't clobber each other's state (localStorage is shared per-origin).
> - **Do NOT persist transient UI** (active tab, open dialogs, spinners, progress).
> - **Size guard:** always persist names/formats/transformation body/mode; **cap or
>   skip large input sample data** so we stay well under the ~5 MB `sessionStorage` budget.
> - A **`schemaVersion`** field on the stored blob lets a future format change
>   discard incompatible state instead of crashing on restore.
> - This is a **safety net / bridge** to the file/project-backed model (IF03/IF04),
>   not a permanent document store. The editor keeps its `inmemory://` model; we only
>   snapshot/restore its text.

---

## Summary

A browser refresh (⌘R) currently wipes the entire IDE working state — loaded input
data, schemas, input/output names and formats, the transformation body, the output,
and the current mode. None of the custom widgets persist anything, and the editor is
an in-memory scratch buffer. IF09 adds **selective session persistence** so a refresh
(or accidental tab reload) restores the work product, while transient UI is allowed
to reset.

## Problem

The Theia frontend is a single-page app: a refresh re-instantiates every widget from
its default state. Theia ships persistence machinery (`StatefulWidget.storeState`/`restoreState`,
`StorageService`, file-backed editors that reopen), but the UTLX widgets opt into
none of it:

- No widget implements `storeState`/`restoreState`.
- The editor uses `monaco.Uri.parse('inmemory://utlx-editor/transformation.utlx')` —
  the transformation never touches disk.
- The only persisted state anywhere is the AI-assist **prompt history** (localStorage).

So everything of substance lives in React `this.state` + the in-memory model and is
lost on reload. Expected given zero wiring; undesirable as UX — losing loaded inputs
and an in-progress transformation to an accidental refresh is a real footgun.

The fix is selective, because "lose everything" conflates two kinds of state:
**work product** (must survive) and **transient UI** (fine to reset).

## Goals

- A browser refresh **restores the work product**: transformation body, input tabs
  (names, formats, instance/schema content, UDM), output name/format, current mode.
- Persistence is **selective** — transient UI is deliberately not restored.
- A **size guard** keeps `sessionStorage` usage bounded (large sample data capped/skipped).
- A **versioned** stored blob so incompatible old state is discarded, not crashed on.

## Non-Goals

- **File/project-backed documents** — the durable source-of-truth model is IF03
  (bundle/project) + IF04 (transform editor). IF09 is the bridge, not that.
- **Cross-device / server-side persistence** — `sessionStorage` is per-tab, per-browser.
- **Cross-reopen survival** — closing the tab clears it (by design; that's IF03/IF04).
- **Concurrent multi-client editing of the same artifact** — once IF03/IF04 make
  transformations file-backed, two frontends opening the same file is last-write-wins
  (Theia isn't a collaborative editor). That is an IF03/IF04 concern, not IF09; today
  it can't happen because the editor is in-memory per frontend.
- **Undo history / dirty-state semantics** — those come with file-backing (IF04).
- Persisting transient UI (active tab, dialog open state, progress, last-used dir).

## Design

### What persists vs. what doesn't

| Persist (work product) | Do NOT persist (transient) |
|---|---|
| Transformation body (editor text) | Active input tab / sub-tab |
| Input tabs: name, format, instance + schema content, UDM | AI-assist dialog open/closed, `loadedBody` chip |
| Output name + format (+ CSV/XML options) | Progress messages / spinners |
| Current mode (Execution / Message Contract) | Validation dialog, transient toasts |
| `nextInputId` (so new tab ids stay unique after restore) | Last-used directory (already separate) |

### Mechanism

Each custom widget implements Theia `StatefulWidget`:
- `storeState(): object` — return a plain, JSON-serializable snapshot of *only* the
  work-product fields above.
- `restoreState(state): void` — rehydrate `this.state` from the snapshot, then
  re-fire the events that downstream widgets need (e.g. input UDM → editor field
  tree, header rebuild) so the restored state is consistent, not just visually present.

### Storage backend: sessionStorage, NOT localStorage

The snapshot is written to **`sessionStorage`**, not `localStorage`. This is a
deliberate choice driven by multi-tab behavior:

- The Theia **backend is a single process with singleton services** (`UTLXServiceImpl`,
  daemon/MCP clients, the lifecycle manager are all `inSingletonScope`), so two browser
  tabs on the same IDE URL **share the backend** but each has its **own frontend state**.
- `localStorage` is **shared per-origin** — both tabs would write the same key and
  **clobber** each other; on refresh tab A could restore tab B's work.
- `sessionStorage` is **per-tab and survives refresh** — exactly IF09's goal — so the
  two tabs persist independently with no collision.

The clobber concern is **same-browser-only**. Two *different* browsers (e.g. Chrome
and Firefox/Edge) on the same endpoint share **no** web storage at all — `localStorage`
and `sessionStorage` are partitioned per-browser — so they're already fully isolated
client-side and IF09's choice is simply moot there. In every case (two tabs or two
browsers) the **backend, `utlxd`, and MCP are shared singletons**; backend calls are
stateless request→response, so concurrent use is functionally fine.

Tradeoff: `sessionStorage` does **not** survive closing the tab (a fresh tab starts
empty; "Duplicate tab" copies it). That is acceptable — durable, cross-reopen
persistence is IF03/IF04's job; IF09 only needs to survive a refresh. If cross-reopen
survival is ever wanted without file-backing, the fallback is **per-tab-namespaced
localStorage** (a tab id in the key) — more machinery for little gain over
`sessionStorage`.

Because Theia's `StorageService` is localStorage-backed, IF09 reads/writes
`sessionStorage` directly (a thin keyed wrapper) rather than routing through it, while
still using the `StatefulWidget` `storeState`/`restoreState` hooks for the snapshot
shape and lifecycle.

### Size guard

Input sample data can be megabytes; `sessionStorage` is ~5 MB total. So:
- Always persist names, formats, the transformation body, output name, mode
  (small, high-value).
- For instance/schema content, persist up to a per-field cap (e.g. 256 KB) and a
  total cap; beyond that, **skip the data** and persist a flag so the panel shows a
  "content not restored — reload the file" affordance rather than silently truncating.
- `log()`/console-note what was dropped — never silently lose data without a signal.

### Versioning

The stored blob carries `schemaVersion: <n>`. On `restoreState`, a mismatched or
missing version is **discarded** (fall back to defaults) instead of being force-fed
into a changed state shape. Prevents a format change from bricking the IDE on reload.

### Relationship to the editor's in-memory model

The editor keeps its `inmemory://` model in v1 — IF09 only snapshots/restores the
**text**. This composes with IF04: when the editor becomes file-backed, the body is
restored from disk and IF09's editor snapshot shrinks to unsaved-changes recovery.

## Implementation Notes

- Implement `StatefulWidget` on `MultiInputPanelWidget`, `OutputPanelWidget`,
  `UTLXEditorWidget`. Toolbar persists nothing new beyond existing prompt history.
- `storeState` returns work-product fields only; `restoreState` rehydrates + re-fires
  the consistency events (input UDM/field-tree, `updateEditorHeaders`).
- Centralize the cap constants, `schemaVersion`, and the `sessionStorage` keyed
  read/write helper in one small module.
- Guard `restoreState` in try/catch — any error falls back to defaults (never block boot).
- Persist to `sessionStorage` (per-tab); do not route the snapshot through Theia's
  localStorage-backed `StorageService`.

## Acceptance Criteria

- After a refresh: transformation body, input names/formats/content, output
  name/format, and mode are all restored.
- Transient UI (active tab, dialogs, progress) resets to defaults — not restored.
- Input content above the cap is not persisted; the panel signals it wasn't restored.
- A stored blob with an old/missing `schemaVersion` is discarded cleanly (defaults,
  no crash).
- A `restoreState` exception never prevents the IDE from loading.
- **Two tabs** open on the same IDE keep independent persisted state: editing +
  refreshing tab A does not alter tab B's restored state (per-tab `sessionStorage`).

## Testing

- **Unit:** `storeState` includes only work-product fields; `restoreState` round-trips
  a snapshot; version mismatch → defaults; oversized content → skipped + flagged.
- **Manual:** load inputs + write a transformation + set names/mode → refresh → assert
  restored; open a dialog → refresh → assert dialog closed.
- **Multi-tab:** open the IDE in two tabs, diverge their state, refresh each → assert
  each restores its own state (no cross-tab clobber).

## Related

- IF03 (bundle/project model) and IF04 (transform editor) — the durable file-backed
  successor; IF09 is the bridge.
- IF08 (mode-aware AI assist) — `currentMode` is part of the persisted work product.
- Existing prompt-history localStorage (toolbar) — the one pre-existing persisted state.

## Effort Estimate

Small (3-5 days): `StatefulWidget` on three widgets + re-fire consistency events
(~2 d), size guard + versioning module (~1 d), tests + manual verification (~1 d).
