# IF18: IDE — Menu & Chrome Structure (menu bar · toolbar · status bar)

> **Status:** design. Defines the IDE's command surfaces — the top **menu bar**, the top
> **toolbar**, and the bottom **status bar** — and how the default Theia menus are pruned for a
> focused UTL-X mapping IDE in two build profiles (local/desktop, cloud/hardened).
>
> **See also:** **[IF19](IF19-shared-bundle-api-and-management-ui.md)** — if bundle *management*
> moves to a shared-API surface (embedded web UI or native-editors-over-API), this menu bar shrinks
> further (bundle CRUD/deploy leave `File → Bundle ▸`).
>
> **Depends on / pairs with:** IF14 (cloud hardening — the primary control), IF03 (bundle ops),
> IF16 (workbench shell), IF15 (in-product help), and the save/load model in
> [Bundle Format](../architecture/bundle-format.md) §7. Commands live in
> `utlx-frontend-contribution.ts` (already a `CommandContribution` + `MenuContribution`).

## Summary

Out of the box the app shows a general-purpose code-editor menu bar
(`File · Edit · Selection · View · Go · Terminal · Help`) — most of it irrelevant to mapping, and
some of it (Terminal) a security liability (IF14). IF18 replaces it with a **deny-by-default,
mapping-focused chrome** organised by the *nature* of each action:

- **Menu bar** = infrequent, discoverable operations → **`File · Edit · View · Help`** (4).
- **Top toolbar** = frequent, mode-aware actions on the current transformation.
- **Bottom status bar** = persistent status + low-frequency runtime toggles.

Net: the menu bar shrinks from **7 → 4** tabs, no thin "UTL-X actions" tab, and everything stays
reachable via the Command Palette.

## Problem

- The default menu bar is built from the included `@theia/*` packages (`core, editor, monaco,
  navigator, outline-view, markers, messages, output, preferences, workspace, filesystem,
  process, terminal`) → `File · Edit · Selection · View · Go · Terminal · Help`.
- Most items target a multi-file code project, not a single-transformation mapping flow.
- **Terminal** is a shell on the backend host — IF14's #1 thing to remove for cloud.
- A naive "add a UTL-X menu" makes the bar *wider*; and a dedicated **UTL-X actions** menu would be
  near-empty (its actions already belong on the toolbar / status bar).

## Goals
- A minimal, mapping-focused menu bar; **no redundant UTL-X actions tab**.
- Actions placed by nature: documents → menu, frequent actions → toolbar, runtime/status → status bar.
- One extension, **two profiles**: local (full) vs cloud (hardened per IF14).
- Everything still reachable via the **Command Palette**.

## Non-Goals
- The hardening *mechanism* (package omission, preference lockdown, sandboxing) — that's **IF14**.
  IF18 only specifies the **resulting surface** and the menu/toolbar/status-bar layout.
- Bundle build/deploy semantics (IF03/IF05) and help content (IF15).

---

## The three surfaces (placement principle)

| Surface | Holds | Nature |
|---|---|---|
| **Menu bar** (top) | documents, edit, view, help | nouns / infrequent / discoverable |
| **Top toolbar** | Execute · Validate · Toggle Mode (+ project name) | frequent, mode-aware verbs on the current transformation |
| **Bottom status bar** | utlxd health + Restart · MCP status · file-load mode | persistent status + low-frequency runtime toggles |

> Rule of thumb: if it acts on the **current transformation constantly** → toolbar; if it's a
> **runtime/daemon/status** concern → status bar; if it's a **document or app** operation →
> menu bar. The Command Palette mirrors all commands regardless.

---

## Default Theia menu analysis — keep / trim / drop

| Default menu | Verdict | Rationale (mapping focus + IF14) |
|---|---|---|
| **File** | **Keep, repurpose & jail** | Becomes the **documents** menu (transformations + bundles). Drop "Open Folder / host path"; **jail** open/save dialogs to the tenant workspace (IF14). |
| **Edit** | **Keep** | Undo/Redo/Cut/Copy/Paste/Find/Replace are essential for `.utlx`. Add **Select All** (folded from Selection). |
| **Selection** | **Fold into Edit** | Monaco selection/multi-cursor is keyboard-driven; menu rarely used. Keep "Select All" in Edit; rest stay in palette/keybindings. |
| **View** | **Keep, trim** | Command Palette, panel toggles (our views), Problems, Output, Theme. ⚠️ palette exposes all commands → IF14 unregisters dangerous ones. |
| **Go** | **Drop** | Go-to-File/Symbol-across-workspace is pointless editing one `.utlx`; "Go to Line" survives in the palette. |
| **Terminal** | **Drop** | IF14 critical — shell on host. Package omitted in cloud; menu pruned in local too (mapping IDE doesn't need it). |
| **Help** | **Keep, repurpose** | UTL-X Documentation (→ utlx-lang.org), Language/Stdlib Reference, Shortcuts, About (IF15). |
| Preferences | **Keep, lock (cloud)** | Allow-list (theme, editor font); hide locked keys (IF14). |
| (Run/Debug, Tasks, Extensions) | **Absent already** | No `@theia/debug` / `@theia/task` / `@theia/vsx-registry`. |

---

## The menu bar — `File · Edit · View · Help`

```
File   New Transformation · New Bundle/Project · Open Bundle/Project… · Open Transformation…
       ─────
       Save Transformation · Save Transformation As… · Save .utlx
       ─────
       Bundle ▸  Build .utlar… · Validate Bundle · Deploy… (IF05) · Refresh
       ─────
       Close

Edit   Undo · Redo │ Cut · Copy · Paste │ Find · Replace │ Select All

View   Command Palette
       Panels: Input · Output · Editor · Toolbar · Health
       Problems · Output · Appearance/Theme

Help   UTL-X Documentation (utlx-lang.org) · Language Reference · Stdlib Reference
       Keyboard Shortcuts · About
```

- **File = documents** (the "menu we have to make" for save/load lives here, the conventional
  home). Naming follows [Bundle Format §7](../architecture/bundle-format.md): a transformation
  name is required on save (it *is* `transformations/<name>/<name>.utlx`); first save with no open
  bundle defaults the project name to the transformation name (single-tx `.utlxp`).
- **Bundle ▸** is a **submenu inside File** (not a top-level tab) — keeps lifecycle ops
  discoverable without widening the bar.
- **Save modes are context-dictated** (IF03): in the Bundle editor, save is Message-Contract; the
  standalone/ad-hoc save is Execution. The menu item is one "Save Transformation"; the mode is
  implicit (see Bundle Format §7).

## The top toolbar (existing widget)

`utlx-toolbar-widget.tsx` — left: mode badge + Execution/Contract toggle; **center: the open
project `📦 <name>.utlxp`** (empty until a project is saved/loaded — the mode-description subtitle
is removed); right: **Execute ▶ / Validate ✅** (mode-aware). These are the frequent verbs; they are
*not* duplicated as a top-level menu.

## The bottom status bar (existing items + Restart)

Existing: `utlxd-status`, `mcp-status`, `utlx-file-dialog-mode` (the Theia/Browser file-load
toggle). Add **daemon control here**, per the "runtime/status → status bar" rule:

- **⏻ utlxd** — health indicator; **click → small popup: Restart · View status** (replaces the
  `Restart Daemon` menu action).
- **MCP** — status.
- **File load: Theia / Browser** — the existing semaphore toggle.

---

## Profiles — local vs cloud (IF14)

Same **shape** (`File · Edit · View · Help`) in both; the cloud profile is a strict subset:

| Aspect | Local / desktop | Cloud / hardened |
|---|---|---|
| Terminal | pruned from menu (package may be present) | **package omitted** (IF14) |
| File dialogs | workspace + (optional) host paths | **jailed** to per-tenant workspace; no host Open-Folder |
| Command Palette | full UTLX + safe core | **dangerous commands unregistered** (IF14) |
| Preferences | editable | **locked allow-list**, locked keys hidden |
| Help | docs + about | same (docs link must respect network policy, IF14 §5) |

The UTLX extension is **identical**; only the app composition + the IF14 hardening contribution
differ. IF18 commands/menus must therefore be **defensive** (no assumption that a pruned command
exists).

---

## Command / menu mapping

**Existing commands** (`UTLXCommands`, `utlx-frontend-contribution.ts`): `EXECUTE_TRANSFORMATION`,
`VALIDATE_CODE`, `INFER_SCHEMA`, `TOGGLE_MODE`, `CLEAR_PANELS`, `RESTART_DAEMON`,
`utlx.toggleFileDialogMode`. Re-home: Execute/Validate/Toggle → toolbar; Restart → status bar;
the rest → palette/View.

**New commands to add** (File / Bundle):
- `utlx.transformation.new` · `utlx.transformation.open` · `utlx.transformation.save` ·
  `utlx.transformation.saveAs`
- `utlx.bundle.new` · `utlx.bundle.open` · `utlx.bundle.build` (`.utlar`) · `utlx.bundle.validate` ·
  `utlx.bundle.deploy` (IF05) · `utlx.bundle.refresh` · `utlx.bundle.close`
- `utlx.help.docs` (→ utlx-lang.org) · `utlx.help.languageReference` · `utlx.help.stdlib` ·
  `utlx.help.shortcuts`

## Implementation notes

- **Build the new menu bar** by registering UTLX actions under Theia's `CommonMenus.FILE` /
  `EDIT` / `VIEW` / `HELP` groups, and **removing** the unwanted default menus/items
  (`Selection`, `Go`, `Terminal`) — primarily by **not composing** those packages (cloud) and by
  **`unregisterMenuAction` / not contributing** them (local). Per IF14, package omission is the
  primary control; menu pruning is belt-and-suspenders.
- The contribution already binds as `MenuContribution` + `CommandContribution` (see
  `frontend-module.ts`) — extend `registerCommands`/`registerMenus`; replace the current thin
  `['1_utlx']` menu.
- **Status-bar restart**: extend the existing `utlxd-status` `StatusBarEntry` with an `onclick`
  that opens a quick action (restart / status), reusing the health plumbing.
- **Toolbar center**: bind the project name to an event the toolbar subscribes to (set on
  File → Open/Save); render `📦 <name>.utlxp`, hidden when empty.

## References
- **IF14** — Theia Hardening for Cloud (package omission, preference lockdown, sandboxing) — the *mechanism*.
- **IF03** — Bundle Project Model & Explorer (bundle ops, save modes).
- **IF16** — Mapping Workbench & shell layout.
- **IF15** — In-product help & docs (Help menu content).
- **IF05** — Bundle operations (Deploy).
- **[Bundle Format](../architecture/bundle-format.md)** §7 — save/load model, naming, minimal unit.
