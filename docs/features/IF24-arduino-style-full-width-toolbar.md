# IF24: IDE — a second, **Arduino-style full-width toolbar** under the menu bar (the **Project Bar**, alongside the existing **Action Bar**)

**Status:** **Designed — not yet implemented** (findings captured; code pending approval).
**Priority:** Medium — UX/affordance: surfaces high-frequency project/transformation/run actions on a
prominent, full-width bar (like Arduino IDE), without disturbing the existing toolbar.
**Created:** June 2026
**Component:** IDE (Theia) — the **application shell layout** (`ApplicationShell` subclass), a new
toolbar **widget**, frontend-module bindings. **Not** the engine/daemon/CLI.
**Depends on / relates to:** **IF16** (workbench shell), the existing toolbar
(`toolbar/utlx-toolbar-widget.tsx`), **IF18** (menu — the commands this bar would surface),
**IF22** (project/transformation commands: New/Open/Switch).

---

## Goal

Keep the **existing** toolbar exactly as-is, and add a **second** toolbar that behaves like Arduino
IDE's: a horizontal strip **directly under the menu bar**, spanning the **full window width**
(left↔right), independent of the menu row.

**Names (agreed) — used in docs, code ids, and conversation:**
- **Action Bar** = the **existing** toolbar (`UTLXToolbarWidget`, id `utlx-toolbar`, `area: 'top'`):
  mode toggle (E / MC), Execute/Run, Generate — *what you DO to the transformation*. Rendered **inside
  the top panel, in the same horizontal row as the menu bar**. (Id kept as-is to avoid churn.)
- **Project Bar** = the **new** Arduino-style toolbar (id `utlx-project-bar`): a full-width row **below**
  the menu — project name · transformation switcher · New Transformation · (Run) — *which project /
  transformation you're in*. A separate widget; left-group / right-group with
  `justify-content: space-between`.

**Is two toolbars possible? Yes.** They occupy different layout slots — the **Action Bar** is a child of
the top panel (menu row); the **Project Bar** is its own row in the shell's top-to-bottom box layout. No
conflict.

## Why the current toolbar "shares space with the menu"

`UTLXFrontendContribution.openToolbar()` does:
```ts
this.shell.addWidget(toolbar, { area: 'top', rank: 100 });
```
Theia's **`'top'` area is the `topPanel`** (`theia-top-panel`), a horizontal `BoxLayout` that **also
holds the menu bar**. So any `area:'top'` widget lines up **in the menu's row** — hence the shared
width. Full-width-under-the-menu is **not** one of Theia's named areas (`top`/`left`/`right`/`main`/
`bottom`); it requires inserting a widget into the shell's **top-level vertical layout**.

## How the shell builds its vertical layout (Theia 1.64.0)

`ApplicationShell.createLayout()` (`@theia/core/lib/browser/shell/application-shell.js`):
```js
createLayout() {
  const bottomSplitLayout = this.createSplitLayout([this.mainPanel, this.bottomPanel], [1, 0],
    { orientation: 'vertical', spacing: 0 });
  const panelForBottomArea = new TheiaSplitPanel({ layout: bottomSplitLayout });
  panelForBottomArea.id = 'theia-bottom-split-panel';
  const leftRightSplitLayout = this.createSplitLayout(
    [this.leftPanelHandler.container, panelForBottomArea, this.rightPanelHandler.container],
    [0, 1, 0], { orientation: 'horizontal', spacing: 0 });
  const panelForSideAreas = new TheiaSplitPanel({ layout: leftRightSplitLayout });
  panelForSideAreas.id = 'theia-left-right-split-panel';
  return this.createBoxLayout([this.topPanel, panelForSideAreas, this.statusBar], [0, 1, 0],
    { direction: 'top-to-bottom', spacing: 0 });
}
```
The vertical stack is **`[topPanel, panelForSideAreas, statusBar]`**. The **Project Bar** goes **between
`topPanel` and `panelForSideAreas`**:
```js
return this.createBoxLayout(
  [this.topPanel, this.utlxProjectBar, panelForSideAreas, this.statusBar],
  [0, 0, 1, 0],                 // Project Bar = stretch 0 (fixed height), side areas = stretch 1
  { direction: 'top-to-bottom', spacing: 0 });
```

## Why a subclass works (the timing detail that makes it safe)

`createLayout()` runs inside `initializeShell()`, which is called from **`init()`**, and `init()` is
decorated **`@postConstruct`** (application-shell.js ~line 1996). Inversify sets **property-injected
fields before `@postConstruct`** runs — so a subclass can `@inject` the toolbar widget as a **field**
and it **will be defined** when the overridden `createLayout()` executes. (If `createLayout` ran in the
raw constructor, the field would still be undefined — it doesn't.) This is the same pattern Theia's own
`@theia/toolbar` package and the Arduino IDE use (`ToolbarApplicationShell` / `ArduinoToolbar`).

`createLayout()` and the helpers `createBoxLayout()` / `createSplitLayout()` are `protected` →
overridable. `this.topPanel`, `mainPanel`, `bottomPanel`, `left/rightPanelHandler`, `statusBar` are all
assigned **before** `createLayout()` in `initializeShell()`, so the override can use them.

## Implementation plan (4 touch points)

1. **New widget — `toolbar/utlx-project-bar-widget.tsx`** (`ReactWidget`, class `UtlxProjectBarWidget`)
   - `id = 'utlx-project-bar'`, `addClass('utlx-project-bar')`.
   - Fixed height (e.g. **34px**) on the widget node so the `stretch:0` box row sizes correctly
     (mirrors the existing Action Bar `.utlx-toolbar { height: 45px }`).
   - Root `.utlx-project-bar-container { display:flex; justify-content:space-between; width:100% }` with
     a **left** item group and a **right** item group (per the CSS sketch the user provided).
   - Content (initial): project name · **transformation switcher** (calls
     `utlx.project.switchTransformation`) · **New Transformation** · **Run**. Reuses existing commands.

2. **New shell — `shell/utlx-application-shell.ts`**
   ```ts
   @injectable()
   export class UtlxApplicationShell extends ApplicationShell {
     @inject(UtlxProjectBarWidget) protected readonly utlxProjectBar!: UtlxProjectBarWidget;
     protected override createLayout(): Layout { /* base body + insert Project Bar as row 2 */ }
   }
   ```
   - Imports: `ApplicationShell` (`@theia/core/lib/browser`), `TheiaSplitPanel`
     (`@theia/core/lib/browser/shell/theia-split-panel`), `Layout`
     (`@theia/core/shared/@lumino/widgets`).

3. **`frontend-module.ts`** (inversify 6.2.2; `ContainerModule` callback is positional →
   `(bind, _unbind, _isBound, rebind) => …`)
   - `bind(UtlxProjectBarWidget).toSelf().inSingletonScope();` (singleton — the shell injects the
     **same** instance; no `WidgetFactory`/`addWidget` needed for the Project Bar).
   - `bind(UtlxApplicationShell).toSelf().inSingletonScope();`
     `rebind(ApplicationShell).toService(UtlxApplicationShell);`

4. **CSS — `style/toolbar.css`** add `.utlx-project-bar` (height + full-width container, left/right
   groups). The user-supplied sketch (space-between, 34px, `--theia-titleBar-*` colors,
   `.utlx-toolbar-item` 28×28 hover/disabled/toggled) is the visual target.

**The Action Bar is untouched** — `openToolbar()` still adds it to `area:'top'`. No changes to its widget.

## Risks / notes

- **Rebinding `ApplicationShell`** is structural but well-trodden (`@theia/toolbar`, Arduino). The
  subclass only adds one injected field + overrides `createLayout()`; everything else inherited. Keep it
  a **singleton** and `toService` so exactly one shell exists.
- **Replicate `createLayout` faithfully** — including `panelForBottomArea.id` /
  `panelForSideAreas.id`, the stretch arrays, and `spacing:0`; only add the toolbar row. (If a future
  Theia upgrade changes `createLayout`, re-sync this override.)
- **Sizing:** box row is `stretch:0`; the widget needs an explicit height in CSS (like A's 45px) or it
  collapses.
- **Two top elements now**: menu + **Action Bar** share the top panel row; the **Project Bar** is the
  next row. Confirm combined vertical height is acceptable (menu ~ + Action Bar 45px + Project Bar 34px).
  Could later move the Action Bar's controls into the Project Bar and retire the Action Bar — out of
  scope; the ask is **both**.

## Alternatives considered (rejected for this ask)

- **`@theia/toolbar` (official, not installed)** — a user-customizable command strip under the menu.
  Would give "full-width under menu" for free, but it's generic command-buttons (drag-to-arrange), not
  our custom React UI (switcher dropdown, project context), and adds a dependency. Good fallback if we
  want end-user customization instead of a curated bar.
- **CSS-only wrap of the top panel** — make `area:'top'` widgets wrap to a second line. Fails: the top
  panel is a Lumino `BoxLayout` (absolute positioning), not CSS flow — wrapping is unreliable.
- **Put Toolbar B in `area:'main'`/a docked widget** — wouldn't span across the left/right side panels.

## Code pointers (for implementation)

- `browser/utlx-frontend-contribution.ts` — `openToolbar()` (Toolbar A, unchanged); commands
  `utlx.project.switchTransformation` / `utlx.project.newTransformation` / execute (Toolbar B reuses).
- `browser/toolbar/utlx-toolbar-widget.tsx` — Toolbar A (reference for widget shape + CSS conventions).
- `@theia/core/lib/browser/shell/application-shell.{js,d.ts}` — `createLayout` (protected),
  `initializeShell`, `@postConstruct init`.
- `browser/style/toolbar.css` — `.utlx-toolbar` (45px) as the height precedent for `.utlx-fw-toolbar`.
