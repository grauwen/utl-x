# UTLX IDE — Playwright UI Map

Authoritative catalog of every interactive control across the three panes + toolbar. Scenario
steps ("click **Execute**", "switch output to **xml**") resolve against this. Code-level mirror:
[`selectors.js`](./selectors.js).

> Derived from the widget source on branch `feature/ide` (June 2026). Every mapping-flow control
> now carries a `data-testid`; re-run recon if widgets change.

## Tagging conventions (two tiers)

| DOM ownership | Hook | Select with |
|---|---|---|
| Our React components (panes, buttons, editors) | `data-testid="utlx-…"` | `page.getByTestId('utlx-…')` |
| Framework-owned DOM (Theia status bar) | namespaced className `.utlx-sb-…` | `page.locator('.utlx-sb-…')` |

`data-testid` is Playwright's default `testIdAttribute`, so `getByTestId` works with no config.
The status-bar switches can't take a `data-testid` (Theia's `StatusBarEntry` exposes only
`className`), so they use the `.utlx-sb-*` className hook — the API-sanctioned anchor.

## Anchoring order (most → least stable)

1. **Command / keybinding** — `commands.executeCommand(id)`; bypasses the DOM. Best for *driving*.
2. **`data-testid`** — `getByTestId`. Best for clicking/reading specific controls.
3. **`.utlx-sb-*` className** — the two status-bar switches only.
4. Text / role — for asserting state (e.g. mode badge wording, Inherit vs Keep).

---

## Stable contracts

### Commands
| Command id | Label |
|---|---|
| `utlx.executeTransformation` | Execute Transformation |
| `utlx.validateCode` | Validate Code |
| `utlx.inferSchema` | Infer Output Schema |
| `utlx.toggleMode` | Toggle Execution/Message Contract Mode |
| `utlx.loadInput` / `utlx.loadSchema` / `utlx.saveOutput` | Load input / Load schema / Save output |
| `utlx.clearPanels` | Clear All Panels |
| `utlx.restartDaemon` / `utlx.showFunctions` | Restart utlxd / Show functions |
| `utlx.toggleFileDialogMode` | Toggle File Load Dialog (Theia / Browser) |
| `utlx.toggleNameOnLoadMode` | Toggle Name on Load (Inherit / Keep) |

### Keybindings (authoritative)
`ctrlcmd+shift+e` execute · `ctrlcmd+shift+v` validate · `ctrlcmd+shift+m` toggle mode ·
`ctrlcmd+shift+c` clear panels.  ⚠️ **No Ctrl+Enter execute.**

### Widget ids / containers
| Pane | Widget id | Root className |
|---|---|---|
| Toolbar | `utlx-toolbar` | — |
| Input | `utlx-input-panel` | `.utlx-multi-input-panel-container` |
| Transform editor | `utlx-editor` | `.utlx-editor-container` |
| Output | `utlx-output-panel` | `.utlx-output-panel-container` |
| Health monitor | `utlx-health-monitor` | — |

### Preconditions (`localStorage`, set before page load)
`utlx.fileDialogMode` = `theia` \| `browser` · `utlx.nameOnLoadMode` = `inherit` \| `keep`.
The two switches are status-bar items: `.utlx-sb-file-dialog-mode`, `.utlx-sb-name-on-load-mode`.

---

## Toolbar Pane  (`#utlx-toolbar`)

| Control | testid | Notes |
|---|---|---|
| Execute / Validate | `utlx-execute` | label flips by mode; or command `utlx.executeTransformation` |
| Mode toggle (Execution ↔ Contract) | `utlx-mode-toggle` | or command `utlx.toggleMode` (preferred for driving) |
| Mode badge (read) | — | `.utlx-mode-badge` text = `▶️ Execution Mode` / `📋 Message Contract Mode` |
| AI Assist | `utlx-ai-assist` | opens MCP dialog (needs MCP `:7780`) |

**MCP dialog** (AI-assist scenarios only): `utlx-mcp-prompt` (prompt box), `utlx-mcp-submit`,
`utlx-mcp-stop`, `utlx-mcp-cancel-prompt`, `utlx-mcp-apply`, `utlx-mcp-retry`, `utlx-mcp-cancel`,
`utlx-mcp-close`, `utlx-mcp-history`, `utlx-mcp-load-current`, `utlx-mcp-clear-body`,
`utlx-mcp-copy-log`, `utlx-mcp-explain`, `utlx-mcp-input-expand`, `utlx-coverage-toggle`,
`utlx-coverage-refine`.

---

## Input Pane  (`#utlx-input-panel`)

| Control | testid |
|---|---|
| Content editor (textarea) | `utlx-input` |
| Input name | `utlx-input-name` |
| Format selector | `utlx-input-format` |
| Load / Clear | `utlx-input-load` / `utlx-input-clear` |
| Add / Delete input | `utlx-input-add` / `utlx-input-delete` |
| Input tab (switch) | `utlx-input-tab` |
| Instance / Schema sub-tabs (Contract) | `utlx-input-tab-instance` / `utlx-input-tab-schema` |
| Schema-only checkbox | `utlx-input-schema-only` |
| Infer schema / Validate | `utlx-input-infer-schema` / `utlx-input-validate` |
| View UDM / Info | `utlx-input-view-udm` / `utlx-input-info` |
| CSV headers / delimiter | `utlx-input-csv-headers` / `utlx-input-csv-delimiter` |

UDM dialog: `utlx-udm-content`, `utlx-udm-view-mode`, `utlx-udm-copy`, `utlx-udm-save`,
`utlx-udm-close`(/`-footer`). Info overlay: `utlx-info-content`, `utlx-info-close`(/`-footer`).
Read-only (no testid): format-match `.utlx-format-indicator`, loaded file `.utlx-input-filename`.

---

## Transform Pane / Monaco  (`#utlx-editor`)

| Control | testid |
|---|---|
| **Monaco container** | `utlx-monaco` (type/click target: `[data-testid="utlx-monaco"] .monaco-editor`) |
| Function Builder | `utlx-editor-function-builder` |
| Load / Save / Clear | `utlx-editor-load` / `utlx-editor-save` / `utlx-editor-clear` |
| Scaffold Output | `utlx-editor-scaffold` |
| Classic / Canvas view toggle (Contract) | `utlx-editor-view-classic` / `utlx-editor-view-canvas` |

**Driving Monaco — edit the BODY only; NEVER touch the header**

The Monaco model is the full `.utlx`: `%utlx 1.0` + input/output declarations + `---` + body.
The header is **IDE-managed** (synced from the panels). **Never recreate or overwrite it** — so
**do NOT `<Mod>+A` + type** (that deletes the header).

Correct way to set the body:
1. Click **Clear** (`utlx-editor-clear`). `clearContent()` keeps the header lines and resets the
   body to `{ // Your transformation code here }`.
2. Replace just the placeholder: focus `[data-testid="utlx-monaco"] .monaco-editor` →
   `<Mod>+F` → type `// Your transformation code here` → `Enter` → `Esc` → `keyboard.type(body)`.
   (Type the inner fields only — Clear already provides the `{ }`.)

⚠️ **`window.monaco` is NOT exposed** in Theia's renderer, so reading via
`window.monaco.editor.getModels()` returns nothing (confirmed). To read the **result**, read the
**output panel** instead: `getByTestId('utlx-output')` (success) / `utlx-output-error`. To assert
the **header survived**, check the visible editor text via the DOM, not the (absent) monaco global.

---

## Output Pane  (`#utlx-output-panel`)

| Control | testid |
|---|---|
| **Output format selector** | `utlx-output-format` |
| View mode (pretty/raw) | `utlx-output-view-mode` |
| Copy / Load / Save / Clear | `utlx-output-copy` / `-load` / `-save` / `-clear` |
| Infer schema | `utlx-output-infer-schema` |
| Instance / Schema tabs | `utlx-output-tab-instance` / `utlx-output-tab-schema` |
| Schema-only checkbox | `utlx-output-schema-only` |
| Output name | `utlx-output-name` |
| **Output text (assert)** | `utlx-output` (the `<pre>`) |
| **Error text (assert)** | `utlx-output-error` (the `<pre>`) |
| Format options | `utlx-output-csv-headers` / `-csv-delimiter` / `-csv-bom` / `-xml-encoding` / `-odata-metadata` / `-odata-context` / `-odata-wrap` |

Read-only (no testid): placeholder `.utlx-placeholder`, diagnostics `.utlx-diagnostics-list li`,
exec time `.utlx-execution-time`, status `.utlx-status`.

**After Execute:** wait for `getByTestId('utlx-output')` (success) **or**
`getByTestId('utlx-output-error')` (failure) to be visible.

---

## Status-bar switches  (className hooks)

| Switch | className | Drive via | Assert state via |
|---|---|---|---|
| Load: Theia / Browser | `.utlx-sb-file-dialog-mode` | command `utlx.toggleFileDialogMode` or `localStorage` | element text |
| Name: Inherit / Keep | `.utlx-sb-name-on-load-mode` | command `utlx.toggleNameOnLoadMode` or `localStorage` | element text (`Keep` / `Inherit Name on Load`) |

For *driving* a scenario, prefer the command or the `localStorage` precondition; the className is
for testing the click gesture and locating the element. State isn't encoded in the className —
assert via text (or fold a `mode-keep`/`mode-inherit` modifier into the entry's className if a
wording-independent signal is wanted).

---

## Known fix applied

`kiosk-demo.js` referenced `.utlx-editor-widget .monaco-editor` (a class that doesn't exist).
Use `[data-testid="utlx-monaco"] .monaco-editor` (or `SEL.monaco`). Centralizing on
[`selectors.js`](./selectors.js) prevents this class of drift.

**Header overwrite (fixed).** The demo did `<Mod>+A` + type in Monaco, which **wiped the
IDE-managed header**. Fixed to: **Clear (`utlx-editor-clear`) → replace the
`// Your transformation code here` placeholder** (see *Driving Monaco* above). The header is
never recreated. `window.monaco` is not available for a model-API approach.

## Live recon gap (unchanged)

The custom Playwright MCP is read-only (`get_page_info`, `take_screenshot`, console/network/
errors, trace) — it can't enumerate the DOM. To let an AI driver "see" clickable elements at
runtime, add a `snapshot`/`list_controls` tool returning the accessibility tree with refs.
