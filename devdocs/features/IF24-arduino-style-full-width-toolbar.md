# IF24: IDE — a second, **Arduino-style full-width toolbar** under the menu bar (the **Project Bar**, alongside the existing **Action Bar**)

**Status:** **Architecture B implemented (v1).** Architecture A (CSS `flex-wrap` on `area:'top'`) was
tried first and **broke the layout** — the global `#theia-top-panel { flex-wrap: wrap }` collided with
the **Action Bar's own `width:100%`**, so the Action Bar's buttons vanished and the Project Bar rendered
as an empty stripe. Per the documented fallback, placement switched to **Architecture B**: a custom
`ApplicationShell` subclass (`UtlxApplicationShell`) inserts the Project Bar as its **own fixed-height
row** in the shell's vertical layout — **no global top-panel CSS**, so the menu + Action Bar are
untouched. The bar is a **single** injectable `ReactWidget` rendering both sides (items pick a side via
`group: 'left'|'right'`), built on `TabBarToolbarRegistry`, Theia-1.64-correct. **v1 layout = labelled zones:** **PROJECT** (left —
New/Open/Recent/Save/Save As) │ **UTLX ARCHIVE** (the project config — transform.yaml/engine.yaml,
divider after Project) · **TRANSFORMATION** (centre, over the editor — Switch/New Transformation) ·
**EDIT** (right — the standard editor commands: Undo/Redo/Cut/Copy/Paste/Find/Replace, `core.*`). Group
**labels** are shown because the bar must be self-explanatory when the **menu bar is detached**
(Electron/macOS native menu sits in the system bar, not the window). Project/Transformation/Archive items
reuse IF18/IF22 commands; Edit items reuse Theia's `core.*`. *Caveats:* codicon lacks undo/cut/paste/find
glyphs (closest matches used); cut/copy/paste run via `document.execCommand` and depend on editor focus,
so they may be inert when clicked from the bar. **Deferred (v2):** a live project /
transformation **context label + inline switcher dropdown** (needs an `onProjectContextChanged` event +
`switchTransformation(txName)` direct-switch); a **Run** item (needs `EXECUTE_TRANSFORMATION` to gain a
command handler — today execution goes through `fireExecuteTransformation`).

**Ported from the parallel-session scaffold** (`docs/architecture/files.zip`), which targeted an **older
Theia** toolbar API. 1.64 fixes applied: render via `item.render(this)` (the runtime `TabBarToolbarItem`
now carries `render`, so no manual icon/click/enabled code); `TabBarToolbarAction.PRIORITY_COMPARATOR`
(namespace moved); `TabBarToolbarItem` imported from `.../tab-bar-toolbar/tab-toolbar-item` (not the
barrel); `CommandRegistry.onCommandsChanged` (not `onDidChange`); dropped the non-existent
`ReactTabBarToolbarItem`/`RenderedToolbarItem` imports; namespaced everything `utlx-project-bar*` to
avoid colliding with the Action Bar's `utlx-toolbar*` classes.
**Priority:** Medium — UX/affordance: surfaces high-frequency project/transformation/run actions on a
prominent, full-width bar (like Arduino IDE), without disturbing the existing toolbar.
**Created:** June 2026
**Component:** IDE (Theia) — a new toolbar **widget** + **`TabBarToolbarContribution`** (Arch A), or an
**`ApplicationShell` subclass** (Arch B); frontend-module bindings; CSS. **Not** the engine/daemon/CLI.
**Depends on / relates to:** **IF16** (workbench shell), the existing toolbar
(`toolbar/utlx-toolbar-widget.tsx`), **IF18** (the commands this bar surfaces), **IF22**
(project/transformation commands: New/Open/Switch).

---

## Update — both toolbars are full-width shell rows (via DEFERRED ATTACH)

The layout is now two stacked full-width bars — **Action Bar (row 1, black background, the E/MC mode
switch)** then **Project Bar (row 2)** — above the input|editor|output area, so the Action Bar is visible
in **Electron/macOS** too (the `area:'top'` placement would hide with the native menu). Execute and AI
Assist moved from the Action Bar to the Project Bar (the AI Assist button fires `onOpenAiAssist`; the MCP
dialog stays owned by the Action Bar).

**Failed first attempt — direct injection (do NOT do this):** injecting `UTLXToolbarWidget` into
`UtlxApplicationShell` cycled (`shell → ActionBar → ApplicationShell → shell`). Even as property
injections on singletons, inversify recursed during construction → **renderer crash ("Aw, Snap!", code
5)**.

**Working approach — deferred attach:** `createLayout()` lays out an **empty `Panel` slot row**
(`actionBarSlot`) with **no** reference to the Action Bar (no cycle). After startup, `openToolbar()`
resolves the Action Bar via `WidgetManager` and calls `shell.mountActionBar(toolbar)` →
`actionBarSlot.addWidget(...)`. Because the Action Bar is resolved *after* the shell is fully built, its
`@inject(ApplicationShell)` gets the complete shell — no cycle. Files: `shell/utlx-application-shell.ts`
(`actionBarSlot` + `mountActionBar`), `utlx-frontend-contribution.ts` (`openToolbar` mounts into the
slot), `style/toolbar.css` (`.utlx-action-bar-slot { min-height: 45px }`). `UTLXToolbarWidget` stays
non-singleton (`WidgetManager` caches the one instance).

## Goal & decision

Keep the **existing** toolbar **exactly as-is** (same buttons, same place) and **add a second** toolbar
that behaves like Arduino IDE 2.x's: a horizontal strip **directly under the menu bar**, spanning the
**full window width** (left↔right). **Two toolbars, both present (A *and* B):**

- **Action Bar (A)** = the **existing** toolbar — `UTLXToolbarWidget`, id `utlx-toolbar`, `area: 'top'`:
  mode toggle (E / MC), Execute/Run, Generate — *what you DO to the transformation*. **Unchanged.**
- **Project Bar (B)** = the **new** Arduino-style toolbar — id `utlx-project-bar`: full-width row **below
  the menu** — project name · transformation switcher · New Transformation · (Run) — *which project /
  transformation you're in*. Left-group / right-group with `justify-content: space-between`.

**Chosen architecture: A — "the Arduino way"** (`TabBarToolbarRegistry` items + `area:'top'` + a CSS
flex-wrap so it forms its own full-width row). Architecture B (`ApplicationShell` subclass) is the
fallback if the CSS-wrap placement proves flaky.

## Shared grounding — verified facts about this shell (Theia 1.64.0)

- **The menu bar is itself a top-area widget.** `BrowserMenuBarContribution` does
  `shell.addWidget(menu, { area: 'top' })` (and a logo) — `browser-menu-plugin.js`.
- **`area: 'top'` is the `topPanel`** (`theia-top-panel`), a **Lumino `Panel`** (`createTopPanel()` →
  `new Panel()`), styled by CSS **`#theia-top-panel { display: flex }`** (default direction **row**).
  ⇒ widgets added to `area:'top'` are **flex children in the menu's row** — which is exactly why the
  Action Bar currently "shares space with the menu."
- **Because it's CSS flex (not a Lumino absolute `BoxLayout`), `flex-wrap` works** — a top widget with
  `flex: 1 0 100%` **wraps onto a full-width second line** under the menu. *(This corrects an earlier
  note in this doc that dismissed CSS-wrap; it is in fact the mechanism Arch A relies on, and the most
  likely way Arduino does it.)*
- The shell's **top-level vertical layout** is
  `createBoxLayout([this.topPanel, panelForSideAreas, this.statusBar], [0,1,0], {direction:'top-to-bottom'})`
  in the **`protected createLayout()`** method — the seam Arch B uses.
- `createLayout()` runs from `initializeShell()` ← **`@postConstruct init()`** (app-shell.js ~L1996), so
  by the time it runs, inversify has already set **property-injected** fields → a subclass can `@inject`
  the toolbar widget as a field and use it in the override (Arch B relies on this).

---

## Architecture A — **the Arduino way** (CHOSEN): `TabBarToolbarRegistry` items + `area:'top'` + CSS wrap

The key design choice — the same one Arduino made — is **not to invent a new item-registration
system**. The Project Bar widget pulls its items from Theia's **`TabBarToolbarRegistry`**.

**Item model**
- Register items with the standard **`TabBarToolbarContribution.registerToolbarItems(registry)`**.
- The widget renders **`registry.visibleItems(this)`**, sorts by
  **`TabBarToolbarItem.PRIORITY_COMPARATOR`**, and re-renders on **`registry.onDidChange`**.
- Items pick a **side** by checking the host widget in their own `isVisible`:
  `UtlxToolbar.is(w) && w.side === 'left'` (a left widget and a right widget; one registry, two sides).
- Clicks run the bound command through **`CommandRegistry`**; **`isEnabled` / `isToggled`** drive the
  disabled / toggled styling (re-evaluated each render; re-render on `commands.onDidChange`).
- A **dropdown** ("board/port-style", e.g. the transformation switcher) is a **custom-render item** —
  in Theia 1.64 a `RenderedToolbarItem` with a `render(widget)` method (the spec's older name was
  `ReactTabBarToolbarItem`; same capability). The widget detects it and calls `item.render(this)`.

So everything we know about Theia commands / context keys / toolbar items applies — items just render
in a top bar instead of a view tab bar.

**Placement (the full-width-under-menu part)**
- `UtlxToolbarContribution` (a `FrontendApplicationContribution`) mounts a container into the shell in
  `onStart()`: `app.shell.addWidget(container, { area: 'top' })`.
- The container holds the **left** and **right** `ReactWidget` sides.
- CSS makes it its own row: **`#theia-top-panel { flex-wrap: wrap }`** + container
  **`flex: 1 0 100%`** ⇒ the menu stays on row 1, the Project Bar wraps to a **full-width** row 2.

**Architecture diagram (from the spec):**
```
FrontendApplicationContribution            TabBarToolbarContribution
  UtlxToolbarContribution                    UtlxToolbarCommands
        |  onStart()                               |  registerCommands()
        |  app.shell.addWidget(.., {area:'top'})   |  registerToolbarItems()
        v                                          v
  UtlxToolbarContainer (Widget)            TabBarToolbarRegistry  <-- shared data source
     |  attaches                                   ^
     +-- UtlxToolbar 'left'  (ReactWidget) --------+  registry.visibleItems(this)
     +-- UtlxToolbar 'right' (ReactWidget) --------+  + onDidChange -> re-render
```

**Files (Arch A — as implemented)**
| File | Role |
|------|------|
| `browser/toolbar/utlx-project-bar.tsx` | `ReactWidget` rendering one side (`side: 'left'|'right'`); items via `registry.visibleItems(this)`, render via `item.render(this)` |
| `browser/toolbar/utlx-project-bar-contribution.ts` | `FrontendApplicationContribution`: mounts the container into `shell` area `'top'` |
| `browser/toolbar/utlx-project-bar-commands.ts` | `TabBarToolbarContribution.registerToolbarItems` — left/right items reusing IF18/IF22 commands |
| `browser/style/utlx-project-bar.css` | `#theia-top-panel{flex-wrap:wrap}` + container `flex:1 0 100%; order:100`; theme-aware via `--theia-*` |
| `browser/frontend-module.ts` | Bindings folded in (`UtlxProjectBarContribution`→`FrontendApplicationContribution`, `UtlxProjectBarItems`→`TabBarToolbarContribution`) + CSS import |

**Project Bar items (initial)** — reuse existing commands (IF18/IF22), no new behavior:
`utlx.project.switchTransformation` (as a render-item dropdown) · `utlx.project.newTransformation` ·
`utlx.project.new` · `utlx.project.open` · Execute (`UTLXCommands.EXECUTE_TRANSFORMATION`). A live
project/transformation **context** label needs a small event (e.g. `onProjectContextChanged` fired from
`loadProjectFromRoot` with `{ projectName, txName, txNames }`) for the dropdown options + current value.

**Pros:** standard Theia mechanism (commands, context keys, enablement/toggle for free); extensible
(render-item dropdowns); **no `ApplicationShell` rebind**; matches the provided spec; original/from-scratch
(license-clean — see note). **Cons:** the full-width-under-menu look depends on **`flex-wrap`** behaving
across themes / menu widths (mitigated by `flex: 1 0 100%`); needs verification.

## Architecture B — **fallback**: `ApplicationShell` subclass inserts a guaranteed row

Place the Project Bar as its **own box row** in the shell's vertical layout — CSS-independent and
guaranteed separate from the menu row.

```ts
@injectable()
export class UtlxApplicationShell extends ApplicationShell {
  @inject(UtlxProjectBarWidget) protected readonly utlxProjectBar!: UtlxProjectBarWidget;
  protected override createLayout(): Layout {
    // …replicate base createLayout faithfully (panel ids, stretch arrays, spacing:0)…
    return this.createBoxLayout(
      [this.topPanel, this.utlxProjectBar, panelForSideAreas, this.statusBar],
      [0, 0, 1, 0],                 // Project Bar = stretch 0 (fixed height), side areas = stretch 1
      { direction: 'top-to-bottom', spacing: 0 });
  }
}
```
- Bindings: `bind(UtlxProjectBarWidget).toSelf().inSingletonScope();`
  `bind(UtlxApplicationShell).toSelf().inSingletonScope(); rebind(ApplicationShell).toService(UtlxApplicationShell);`
  (inversify 6.2.2; `ContainerModule` callback is positional → `(bind, _unbind, _isBound, rebind) => …`).
- Imports: `ApplicationShell` (`@theia/core/lib/browser`), `TheiaSplitPanel`
  (`.../shell/theia-split-panel`), `Layout` (`@theia/core/shared/@lumino/widgets`).
- The widget can **still use the Arch-A item model** internally (`TabBarToolbarRegistry`) — B only
  changes *placement*, not the item system. So A and B share most code; only the mount differs.

**Pros:** guaranteed full-width row, no CSS-wrap dependency. **Cons:** rebinds `ApplicationShell`
(structural — though well-trodden: `@theia/toolbar`, Arduino); must **replicate `createLayout`
faithfully** (panel ids, stretch arrays, `spacing:0`) and re-sync on Theia upgrades; the box row needs
an explicit widget height in CSS (like the Action Bar's `.utlx-toolbar { height: 45px }`) or it
collapses.

---

## Plan of record

1. Implement **Architecture A**. Keep the **Action Bar untouched** (`openToolbar()` still adds it to
   `area:'top'`); **add the Project Bar** as a second `area:'top'` element that flex-wraps to its own
   full-width row.
2. Build the Project Bar from `TabBarToolbarRegistry` items (left/right sides), reusing IF18/IF22
   commands; add `onProjectContextChanged` for the live project/transformation context + switcher list.
3. **If the CSS-wrap placement is flaky**, switch *only the mount* to **Architecture B**
   (`UtlxApplicationShell.createLayout`) — the item model stays identical.

## Alternatives considered (rejected)

- **`@theia/toolbar` (official, not installed)** — a user-customizable command strip under the menu.
  Gives "full-width under menu" for free, but it's generic drag-to-arrange command buttons, not our
  curated bar with a context dropdown, and adds a dependency. Good only if end-user customization becomes
  a goal.
- **Put the Project Bar in `area:'main'` / a docked widget** — wouldn't span across the left/right side
  panels.

## Licensing note (from the spec)

From-scratch is **not required for license reasons** — Arduino IDE 2.x and UTLX are both AGPL-3.0, so
copying would be permitted; writing original text simply keeps provenance unambiguous. Theia is
EPL-2.0 / GPL-2.0-with-classpath-exception; building an AGPL-3.0 app on Theia's APIs is fine
(classpath exception). General APIs/patterns aren't copyrightable; specific source text is. *Not legal
advice — confirm with counsel if AGPL's network-use clause matters for distribution.*

## Code pointers

- `browser/toolbar/utlx-toolbar-widget.tsx` — Action Bar A (reference for widget shape + CSS).
- `browser/utlx-frontend-contribution.ts` — `openToolbar()` (A, unchanged); `loadProjectFromRoot`
  (fire `onProjectContextChanged`); commands `utlx.project.*` + `UTLXCommands.EXECUTE_TRANSFORMATION`.
- `@theia/core/.../tab-bar-toolbar/` — `TabBarToolbarRegistry.visibleItems/onDidChange`,
  `TabBarToolbarContribution.registerToolbarItems`, `PRIORITY_COMPARATOR`, `RenderedToolbarItem` (Arch A).
- `@theia/core/lib/browser/shell/application-shell.{js,d.ts}` — `createLayout` (protected),
  `initializeShell`, `@postConstruct init`; `theia-split-panel` (Arch B).
- `@theia/core/lib/browser/menu/browser-menu-plugin.js` — proof the menu is an `area:'top'` widget.
- `browser/style/` — `.utlx-toolbar { height:45px }` (height precedent); `#theia-top-panel{display:flex}`.
