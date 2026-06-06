# IF03: IDE — Bundle Project Model & Explorer

> **Canonical bundle format:** the on-disk layout (`.utlxp`/`.utlar`, `transformations/<tx>/…`,
> `transform.yaml`, `schemas/`, `test-input-<slot>.<ext>`), the three naming namespaces, the
> `inputs[]`-is-the-order rule, terminology (instance / test input), and the execution-save vs
> Message-Contract-save model are specified in **[Bundle Format](../architecture/bundle-format.md)**.
> That file is authoritative; this doc covers the IDE project/explorer UX and defers to it for
> structure. (Note: earlier mentions here of a bundle-root `samples/` and of `manifest.json` for
> the open form are superseded — instances live at `transformations/<tx>/test-input-<slot>.<ext>`,
> and the open `.utlxp` uses `engine.yaml`.)

**Status:** **Phases 1–3 implemented** (June 2026).
- **Phase 1:** Bundle Explorer recognizes an open-mode bundle directory (has
  `transformations/`), lists transformations / shared `schemas/` / manifest, and opens a
  transformation or a schema/manifest file. Frontend-only (`FileService` +
  `FileDialogService` + `OpenerService`); no backend/protocol change.
- **Phase 2 (active-transformation binding):** opening a transformation loads its `.utlx`
  into the editor **and binds its input samples** — the header is parsed for declared
  input names, each matched to a sample file (`<name>.*` / `test-input-<name>.*` /
  `samples/<name>.*`) and pushed into the input panel (overwrite-by-name via a new
  `loadBundleSamples` hook that `syncFromHeaders` consumes). Output format binds via the
  existing header→output-panel sync; the output result is re-run on demand (derived).

- **Phase 3 (Build/Export `.utlar`):** a **"📦 Build .utlar"** action zips the bundle
  (manifest.json at the ZIP root + `transformations/` + `schemas/` + engine.yaml) into a
  deployable archive **structured per the engine's loader** (EF09 `BundleMode.loadUtlar` /
  `readManifestFromUtlar`). Backend `buildBundle(rootUri)` via a **dependency-free STORE ZIP
  writer** (no new deps; verified — valid ZIP, CRC-clean, structure matches the contract).
  The per-transformation **⚙ opens `transform.yaml`** and a project **engine.yaml** node makes
  config editable (text today; live schema-assisted validation is IF04).

**Pending:** `/admin/*` deploy (IF05), `.utlar` open (ZIP expand to a temp workspace),
locked-mode read-only, the full command/menu surface below.
**Priority:** High
**Component:** IDE (Theia Extension)
**Depends on:** EF09 (Production Bundle Mode `.utlar`) — *implemented*, EF03 (Bundle Management API) — *implemented*
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

## Bundle editor = Message-Contract mapping manager

The Bundle Explorer is not just a file tree — it is the **manager of the bundle's
Message-Contract mappings**, and it drives the Mapping editor (see IF16). Defining behavior:

- **It lists the bundle's transformations.** Each transformation is a Message-Contract
  mapping.
- **Selecting a transformation loads its whole mapping into the editor** — it **refreshes the
  UTL-X transform, the input(s), and the output** with the selected transformation's. This is
  the navigator→editor pattern (like clicking a file in the Explorer): the persistent Mapping
  editor in `main` always shows the *currently selected* transformation, and the three panes
  swap together on each selection.
- **The Bundle context is always Message-Contract mode.** Editing a bundle transformation is
  always design-time contract mapping; the Execution ↔ Message-Contract switch is fixed to /
  hidden in favor of MC while working inside the bundle.
- **Execution mode is separate** — running a single transformation against sample data to test
  its output is a standalone flow, **not** driven by the Bundle editor. The Bundle never offers
  Execution.

Implementation: "open transformation" already (Phase 2) loads the `.utlx` into the editor and
binds input samples via `loadBundleSamples`; this section's requirement is that it **also drives
the output** (the selected transformation's output contract/result) and **forces MC mode**, so
one selection refreshes all three panes as a unit. See IF16 for the surrounding shell layout
(navigators + persistent Mapping editor; no perspective swap).

## Saving a transformation — two save modes (+ naming)

Today only the **raw `.utlx`** can be saved (from the middle editor). The full *constellation*
(inputs, transform, output) is not yet saveable. There are **two save modes**, distinguished by
what the transformation carries, and **both write into the bundle project (`.utlxp/`)** —
`transformations/<name>/{ <name>.utlx, transform.yaml }` + optional `samples/` + shared
`schemas/`. (`.utlxp` = the open, editable **project directory**; Build/Export → `.utlar` ZIP.
See "Two project forms" below.)

| Save mode | Saves | Output | Where |
|---|---|---|---|
| **Execution-save** | `.utlx` + **input instances** | **NOT saved** — *derived* by running the saved inputs through the transformation | input instances → `samples/<name>.*`; `.utlx` in the tx folder |
| **Message-Contract-save** | `.utlx` + **input schema** + **output schema** | **output *schema* IS saved** (it is the *contract*, not a derived result) | schemas → shared `schemas/`, referenced from `transform.yaml` / the header `{schema:…}` |

- The difference is **instances (`samples/`) vs contracts (`schemas/`)** — a transformation may
  carry both. Execution-save is the lighter form (test data + transform); MC-save persists the
  contract (the Bundle editor is always MC — see above).
- In **both** modes the **output result instance** is never persisted as work product (it is
  re-run on demand); MC-save persists the output **schema**, execution-save persists nothing for
  output.

### Transformation naming (free filesystem names — NOT identifiers)
The transformation name is the **directory/file name**, a **free filesystem string** — confirmed
in the engine: `BundleLoader.kt` `val name = txDir.name` (line ~55), with **no validation, no
regex, no must-start-with-a-letter rule**; it then prefers `<name>.utlx`. Therefore:

- **No stripping / no sanitizing.** Loading `00-enterprise-order.utlx` keeps the name **verbatim**
  — the `00-` prefix is meaningful (ordering) and is the artifact's identity on disk. Stripping
  it would misrepresent the file and break the on-disk link. (Same rule as schema references in
  IF17.)
- **Leading digits / dots / hyphens are all legal** for a transformation/`.utlx` name. This is a
  **different namespace** from **input/output names**, which *are* in-language identifiers
  (`[a-zA-Z_][a-zA-Z0-9_-]*`, enforced by the lexer/parser). The identifier rule applies only to
  header names, never to bundle artifact filenames.

### Title bar (document title) shows the transformation name
The editor widget's Theia title (`utlx-editor-widget.tsx`: `title.label` "UTLX Transformation",
`title.iconClass = 'codicon codicon-arrow-swap'` — the double-arrow) is the document-title strip
atop the middle pane. On **load/save** it should show the transformation **name verbatim** (e.g.
`00-enterprise-order.utlx`, `00-` kept), with a **dirty marker** (`●`) when there are unsaved
edits — standard editor-tab behavior. The `.utlx` extension may be shown (VS-Code style) or
dropped for tidiness, but the prefix is never stripped.

## Design

**Decision — navigate N, do not run N live editors.** Support N transformations
in one project, navigable and individually editable/testable, but **not** N
simultaneous Input|Transformation|Output triptychs (a resource/UX trap: N daemons,
N previews). Use the standard IDE pattern: project tree → open as tabs → the
three-panel view + live preview/daemon execution bind to the **active**
transformation only.

**Two project forms — `.utlxp` (open) ↔ `.utlar` (locked).** A UTL-X bundle has two
forms, mapping to EF09's two modes:

| Form | What it is | Mode | Editable? |
|---|---|---|---|
| **`<name>.utlxp/`** | the open, editable **project directory** | open (dev/test) | yes |
| **`<name>.utlar`** | the locked, deployable **ZIP archive** (+ manifest) | locked (acc/prd) | no — read-only |

Lifecycle: **edit the `.utlxp` project → Build/Export → `.utlar` → deploy** (CI/CD).

**Project recognition (engine-truthful + IDE convention).** Recognition is **structural**:
a folder is a bundle project (**open mode**) when it contains a **`transformations/`**
directory — exactly the engine's open-mode definition (EF09; the engine does **not** key
off any suffix). The **`.utlxp/`** suffix is the **IDE's project-folder convention** (a
marker, like `.xcodeproj`) — preferred/badged by the IDE and used by "New Bundle", but
not required for recognition. A **`.utlar`** file is the **locked** form, transparently
expanded to a workspace temp dir on open. The **manifest** (`version`, `created`, sha256
checksum) lives **inside the `.utlar`** (EF09) — (re)generated on Build, not required on
disk in open mode. Theia is already a workspace IDE, so a bundle project is "a folder with
a known structure" — mostly recognition and surfacing, not new infrastructure.

**Bundle Explorer** (Theia `ViewContainer` + `TreeWidget`):

```
▾ project: sales.utlxp            (open/editable; or sales.utlar opened read-only)
  ▾ transformations
      orders-in           (orders-in.utlx + transform.yaml + samples/)
      invoice-to-ubl
  ▾ schemas (shared)
      order.json
      invoice.xsd
    manifest.json
```

Selecting a transformation opens/activates its tab and points the three panels at
it. Selecting a schema or the manifest opens a plain editor (IF05 adds richer views).

**Document persistence — fully Theia/Monaco-compliant (the target architecture).**
All working documents are **file-backed through the Theia backend**, exactly how VS
Code (Electron) and Theia SaaS (browser) work — never the browser's ~5 MB Web
Storage. Concretely:

- **Transformation** — opened via Theia's **editor manager** as a file-backed
  `file://…/orders-in.utlx` Monaco model (NOT a standalone `monaco.editor.create`
  with an `inmemory://` URI). This gives native save / dirty-state / undo /
  reopen-on-refresh and hot-exit backup of unsaved edits — all on disk.
- **Input samples** — each input's sample instance is a file under the
  transformation's **`samples/`** dir, loaded/saved via the backend `FileService`.
  The **link is by convention, anchored to the `.utlx` header**: input named
  `orders` (format `json`) ↔ `samples/orders.json`. The header stays the structural
  source of truth; the sample file supplies the data. Renaming an input renames both
  the header entry and its sample file. (Kept out of `transform.yaml`, which mirrors
  the *engine's* runtime config — samples are an IDE concern; convention-by-name
  needs no extra binding file.)
- **Output** — a derived result (re-run on demand); displayed read-only, optionally
  written to `output/`. Not persisted as work product.
- **UI/layout state only** — Theia's `StorageService` (localStorage) is used solely
  for small view/layout state, never document content.

This supersedes the **IF09** `sessionStorage` snapshot, which is an explicit
**non-production stopgap** for surviving a browser refresh before file-backing lands.
The pieces already exist in the stack (Theia backend + `FileService`; the panels
already load files via it) — file-backing is adopting the standard pattern, not new
infrastructure.

**Build / Export.** A "Build `.utlar`" command zips the working directory and
(re)generates `manifest.json` (aggregating each `transform.yaml`'s schema refs and
messaging) — mirroring the CLI/`EF03` bundle build so IDE output is byte-compatible
with CI/CD.

## IDE integration — commands, menus & contributions

Theia wiring is the bulk of this feature's UX. Design-only here (concrete command ids,
handlers, and `when`-keys land in implementation).

### Command catalog (grouped by scope)

- **Bundle** (`utlx.bundle.*`): `open` (Open Bundle… — `.utlar` or directory), `new`,
  `build` (Export `.utlar`), `validate`, `deploy` (→ `/admin/bundle`, IF05), `refresh`,
  `close`, `openManifest`.
- **Transformation** (`utlx.tx.*`): `new`, `open` (default tree action), `editConfig`
  (`transform.yaml`, IF04), `test`, `deploy` (→ `/admin/transformations/{name}`, IF05),
  `pause` / `resume` (operational), `rename`, `duplicate`, `delete`, `addSample`.
- **Shared schema** (`utlx.schema.*`): `add`, `open`, `remove`.

### Placement

| Surface | Contents | Theia mechanism |
|---|---|---|
| **Main menu** | `File → Open Bundle…`; `File → New → UTL-X Bundle / Transformation`; a **"Bundle"** menu: Build `.utlar` · Validate · Deploy · Refresh · Close | `MenuContribution` |
| **Command Palette** | all commands, category-prefixed `UTL-X Bundle: …` | automatic once registered |
| **Explorer view toolbar** | ＋ New Transformation · Build `.utlar` · Refresh · Collapse-All (icons) | `TabBarToolbarContribution` |
| **Context — bundle root** | New Transformation · Add Shared Schema · Build · Validate · Deploy · Refresh · Open Manifest | tree context menu |
| **Context — transformation** | Open · Edit Config · Test · Deploy · Pause/Resume · Rename · Duplicate · Delete · Add Sample | tree context menu |
| **Context — schemas folder / schema** | Add Shared Schema / Open · Remove | tree context menu |
| **Editor title** (active tx tab) | Test · Deploy (quick actions) | `TabBarToolbarContribution` |
| **Keybindings** (sparing) | New Transformation; Build via a chord (e.g. `Ctrl/Cmd K B`) to avoid clobbering Theia's `Cmd B` | `KeybindingContribution` |

**Context-menu groups (ordering):** `1_open` · `2_edit` · `3_lifecycle` (test/deploy/
pause) · `8_modification` (rename/duplicate) · `9_danger` (delete) — destructive actions
separated.

### Contribution classes

`BundleCommandContribution` (CommandContribution), `BundleMenuContribution`
(MenuContribution + tree context-menu paths), `BundleToolbarContribution`
(TabBarToolbarContribution), optional `BundleKeybindingContribution`, plus the
`ViewContribution` / `WidgetFactory` for the explorer.

### Enablement & locked-mode (ties to EF09)

- **`when`-clauses:** transformation commands enabled only on a transformation node;
  `deploy`/`pause`/`resume` only when an engine connection is configured; schema commands
  only on schema nodes — via Theia context keys + the current selection.
- **Locked-mode read-only:** a bundle opened from a **`.utlar`** is locked (EF09) →
  **disable/hide** all modification + `deploy`/`delete` commands, mirroring the engine's
  locked semantics; only read, **Build/Export**, and operational `pause`/`resume` remain.
  (Open-directory bundles are fully editable.)

## Implementation Notes

- New contributions: `bundle-command-contribution.ts`, `bundle-menu-contribution.ts`,
  `bundle-toolbar-contribution.ts` (+ optional keybindings) under `browser/bundle-explorer/`.
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
- Bundle/transformation/schema commands are registered and reachable from the **command
  palette**, the **Bundle menu**, the **explorer toolbar**, and **context menus**, with
  `when`-clauses scoping each to the right node.
- A bundle opened from a **`.utlar` (locked)** is **read-only**: modification + deploy/
  delete commands are disabled/hidden; only read, Build/Export, and pause/resume remain
  (matches EF09 locked semantics). Open-directory bundles are fully editable.
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
