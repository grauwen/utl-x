# IF22: IDE — the Theia **workspace** = the open `.utlxp` project (file-backed docs + git)

**Status:** **Unified open implemented** (June 2026). There is now **one** command —
**File → Open UTLX Project** (`utlx.project.open`) — and **opening a `.utlxp` IS the workspace**
(like VS Code "Open Folder"): it `WorkspaceService.open()`s the project (the window reloads, rooted
there), and on the next startup the IDE **detects the `.utlxp` workspace** (`transformations/`),
**auto-loads** the transformation + panels, and **switches to Message-Contract** — so the project
comes back **loaded, in MC**, with the Explorer rooted at it. **Mode is DERIVED from the workspace**
(`mode = f(workspace)`), durable across reloads, not a transient toggle. The earlier two-command
split (a separate `utlx.project.openAsWorkspace`) has been **removed** — "workspace" is plumbing, not
a user concept; you open a *project*. Still pending: **git** (`@theia/git`/`@theia/scm` not composed —
Step 2) and **file-backed documents** (Step 3).
**Priority:** Medium-High — unlocks git/versioning, native save/undo, and a real project tree, and is
the prerequisite for treating a bundle as a versioned, deployable repo.
**Created:** June 2026
**Component:** IDE (Theia) — `WorkspaceService`, the Navigator, the editor/panels; later `@theia/git`.
**Depends on / relates to:** **bundle-format §7–9**, **IF03** (file-backed "target architecture"),
**IF16** (workbench shell), **IF18** (menu).

---

## Problem — the workspace is decoupled from the project

Theia is a **workspace IDE**: it boots rooted at a folder (today `~/data/utlx-workspace`), and a lot of
machinery keys off that root. But the UTL-X mapping flow **bypasses** it:
- "Open UTLX Project" (IF18/bundle-format §7) reads files **into the panels** from anywhere and never
  changes the workspace; the editor is **in-memory** (Monaco + session-storage stopgap, IF09).
- So the workspace is just a **scratch boot-root** + the default file-dialog folder + terminal cwd +
  layout/session storage. No git, no file-backed save/undo, no project tree.

**Composed today** (browser-app): `@theia/workspace`, `@theia/navigator`, `@theia/filesystem`,
`@theia/editor`, `@theia/preferences`, `@theia/terminal`. **Absent:** `@theia/git`, `@theia/scm`,
`@theia/search-in-workspace`.

## The model — workspace = the open `.utlxp`

If opening a project set the **workspace** to the `.utlxp` (`WorkspaceService.open(rootUri)`), all of
Theia's workspace machinery would operate on the **project**:

- **Git** — `@theia/git` tracks the **workspace root as the repository**. Workspace = `.utlxp` ⇒ the
  SCM view gives **diff / stage / commit / branch / history** on `*.utlx`, `transform.yaml`, `schemas/`,
  `engine.yaml`. (Git isn't composed yet → add `@theia/git` + `@theia/scm`; it then works because the
  **workspace is the unit git versions.** A bundle is a project; a project is a repo.)
- **File-backed documents** (IF03 "target architecture") — `.utlx`/`transform.yaml`/schemas open as
  **real editor files**: native save, dirty-state, undo, reopen-on-refresh, hot-exit — replacing the
  in-memory + session-storage stopgap.
- **Project tree** in the Navigator (≈ IF03 Bundle Explorer for free), **search-in-workspace**,
  **external-edit reload** (file watch), **per-project settings** (`.theia/settings.json`, e.g. the
  `utlx-config` schema prefs), **terminal rooted at the project**.

## The reload — and how the unified open handles it (implemented)

`WorkspaceService.open(uri)` **reloads the window** (workspace switch), which **wipes the in-memory
panel state**. So "set the workspace" and "load the panels" can't happen in one synchronous action — the
load must happen **after** the reload. The implemented solution makes the **workspace itself the durable
signal**:

- **Open** = `WorkspaceService.open(.utlxp)` → reload, rooted at the project.
- **Startup** = `maybeLoadProjectWorkspace()` asks *"is the workspace root a `.utlxp` (has
  `transformations/`)?"* → if so, `loadProjectFromRoot()` loads the transformation + panels and fires
  **Message-Contract**. **Mode is DERIVED, not asserted** — the `.utlxp` survives the reload, so MC is
  re-derived on every startup (no race, no `localStorage` handshake needed).

Considered but not taken: a `localStorage` *pending-open* handshake (works, but the workspace is already
a durable signal); **multi-root** `addRoot` (lighter, but `.theia-workspace` UX wrinkles). The **real
destination** is still **file-backed documents** (Step 3): once `.utlx`/test-input/schemas are opened as
workspace *files* via `EditorManager`/`FileService` (IF03), the in-memory load is moot and the documents
survive the reload by definition.

## Caveat — where the git boundary is

The example `.utlxp` live **inside** the utl-x repo (the `.git` is at the repo root, not the `.utlxp`),
so opening one as the workspace, git would track the **whole** utl-x repo. The "project = git unit" is
cleanest when a **`.utlxp` is its own repo** — which is exactly the **deploy/CI model** (bundle repo →
build `.utlar` → deploy, EF09). So unifying workspace=project also nudges bundles toward standalone
repos, which is the right direction anyway.

## Phasing

1. **Unified open + new** (✅ done) — `utlx.project.open` opens an existing `.utlxp` **as the
   workspace**; `utlx.project.new` **scaffolds** a minimal `.utlxp` (one stub transformation) and adopts
   it as the workspace the same way. Both reload; startup `maybeLoadProjectWorkspace()` auto-loads the
   transformation + **Message-Contract**. **Creating a project IS opening it as the workspace**;
   **mode = f(workspace)**. (The separate `openAsWorkspace` command was **removed**.) *Note:* `Save
   Project As` writes a `.utlxp` but does **not** adopt it as the workspace — a deliberate gap (saving a
   copy ≠ switching projects); revisit if "Save As → switch into it" is wanted.
   *Follow-up:* let the **toolbar read the same `transformations/` signal at init** so it boots straight
   into MC (avoids the brief E→MC flash on startup).
2. **Add git** — compose `@theia/git` + `@theia/scm`; the SCM view then versions the project repo. (Pairs
   with IF14 cloud-hardening decisions about exposing SCM.)
3. **File-backed documents** (IF03) — bind the editor + input/output panels to workspace files via
   `EditorManager`/`FileService` so save/undo/dirty/reopen are native (and the load survives the reload
   without the startup re-read).
4. **(optional)** multi-root surfacing; search-in-workspace.

## Code pointers

- `browser/utlx-frontend-contribution.ts` — `newProject()` (scaffold a minimal `.utlxp` via
  `buildSavePlan` + `WorkspaceService.open`), `openProject()` (pick `.utlxp` + `WorkspaceService.open`),
  `maybeLoadProjectWorkspace()` (startup: detect `transformations/`), `loadProjectFromRoot()` (load
  panels + fire `MESSAGE_CONTRACT`). `WorkspaceService` from `@theia/workspace/lib/browser`.
- `browser/toolbar/utlx-toolbar-widget.tsx` — now **listens** to `onModeChanged` (badge + backend
  `setMode` follow an external switch), so MC derived at startup propagates to the whole IDE.
- IF03 file-backing is the substrate for Steps 3–4 (`EditorManager.open` for `.utlx`, `FileService`
  for test-input/schemas).
- `browser-app/package.json` — add `@theia/git` + `@theia/scm` for Step 2.

## Related
- **bundle-format** §7 (save/load), §9 (responsibilities), §10 (standalone-repo direction).
- **IF03** (file-backed target architecture), **IF16** (workbench), **IF18** (File menu), **EF09** (`.utlar`/CI).
