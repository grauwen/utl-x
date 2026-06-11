# IF22: IDE — the Theia **workspace** = the open `.utlxp` project (file-backed docs + git)

**Status:** Proposed (design). **Step 1 implemented** — an explicit **"Open Project as Workspace"**
command (`utlx.project.openAsWorkspace`) sets the Theia workspace to the project root. The deeper
unification (file-backed docs, git, survive-reload) is phased below.
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

## The hard part — switching the workspace **reloads** Theia

`WorkspaceService.open(uri)` **reloads the window** (workspace switch). That **wipes the in-memory
panel state** the current "Open UTLX Project" just loaded. So the two cannot be naively merged into one
action — auto-switching on Open would discard the load. Resolutions:

1. **Keep them separate (Step 1, done):** "Open UTLX Project" loads the panels (light, no reload);
   a distinct **"Open Project as Workspace"** opts into the reload for Navigator/git. Complementary tools.
2. **Survive-reload handshake:** Open Project stores a *pending-open* (project root) in
   `localStorage`/workspace storage, calls `WorkspaceService.open`, and a startup hook re-runs the panel
   load after the reload. Makes "Open Project" both set the workspace *and* populate the panels.
3. **File-backed docs (the real fix):** once `.utlx`/test-input/schemas are workspace **files** opened
   via `EditorManager`/`FileService` (IF03), the in-memory load is moot — the workspace files *are* the
   documents, and they survive reload by definition. This is the destination.
4. **Multi-root alternative:** `WorkspaceService.addRoot(projectUri)` can surface the project tree as an
   *additional* root without a full single-root switch — lighter, but multi-root has its own UX/`.theia-workspace` wrinkles.

## Caveat — where the git boundary is

The example `.utlxp` live **inside** the utl-x repo (the `.git` is at the repo root, not the `.utlxp`),
so opening one as the workspace, git would track the **whole** utl-x repo. The "project = git unit" is
cleanest when a **`.utlxp` is its own repo** — which is exactly the **deploy/CI model** (bundle repo →
build `.utlar` → deploy, EF09). So unifying workspace=project also nudges bundles toward standalone
repos, which is the right direction anyway.

## Phasing

1. **Open Project as Workspace** (✅ done) — `utlx.project.openAsWorkspace`: set the Theia workspace to
   the project root (uses the currently-open project, else a folder pick). Explicit, opt-in (reloads).
2. **Add git** — compose `@theia/git` + `@theia/scm`; the SCM view then versions the project repo. (Pairs
   with IF14 cloud-hardening decisions about exposing SCM.)
3. **File-backed documents** (IF03) — bind the editor + input/output panels to workspace files via
   `EditorManager`/`FileService` so save/undo/dirty/reopen are native and survive reload; then unify
   "Open Project" to set the workspace *and* restore the panes from the files (no separate command needed).
4. **(optional)** multi-root surfacing; search-in-workspace.

## Code pointers

- `browser/utlx-frontend-contribution.ts` — `openProject()` already tracks `currentProjectRoot`;
  Step-1 adds `openProjectAsWorkspace()` using `WorkspaceService` (`@theia/workspace/lib/browser`).
- IF03 file-backing is the substrate for Steps 3–4 (`EditorManager.open` for `.utlx`, `FileService`
  for test-input/schemas).
- `browser-app/package.json` — add `@theia/git` + `@theia/scm` for Step 2.

## Related
- **bundle-format** §7 (save/load), §9 (responsibilities), §10 (standalone-repo direction).
- **IF03** (file-backed target architecture), **IF16** (workbench), **IF18** (File menu), **EF09** (`.utlar`/CI).
