# UTLX IDE — Playwright scenarios

Human-written scenarios that get turned into Playwright scripts (demo loops and/or
tests). **One `.md` per scenario**, numbered (`01-basic-mapping.md`, `02-mode-toggle.md`, …).

- Generated **demo** → `theia-extension/playwright/kiosk-demo.js`
- Generated **tests** → `theia-extension/playwright/tests/*.spec.ts`

Both drive Theia at **http://localhost:4000** with the runner's **own** browser.
Behind the IDE: core actions (load / Execute / Validate / mode toggle) use **utlxd**;
AI-assist (Generate UTLX / Map to contract) uses the **UTLX MCP (`:7780`)**. Note which
your scenario needs (see the template). The Playwright **MCP** / CDP `:9223` are **not**
used by scripts.

## Template — copy `_template.md`

```
# Scenario: <short name>

- **Goal:** what it demonstrates / verifies
- **Mode:** demo (paced, loops, narration) | test (assertions, runs once)
- **Needs:** Theia :4000 · utlxd · UTLX MCP :7780? (only if an AI-assist step is used)
- **Data:** which example input/transform (path under examples/…) or inline

## Steps
1. <action>  →  <expected / what the audience should see>
2. <action>  →  <expected>
...

## Notes
- pacing / narration cues (for a talk)
- anything fragile or order-dependent
```

## Stable selectors (the hook surface)

**Every interactive control is tagged** — ~104 `data-testid` hooks across all panels, the toolbar,
and every dialog. Scripts target these, never fragile text/CSS. Convention:
`[data-testid="utlx-<area>-<action>"]`.

Groups (count): `utlx-execute` · `utlx-mode-toggle` · `utlx-ai-assist` · **input** (18) ·
**output** (20) · **editor** (7) · **mcp** dialog (14) · **fb** = Function Builder dialog (23) ·
**field** = FB inputs tree (10) · **op** = FB operators tree (3) · **udm** viewer (6) ·
**info** summary (3) · **coverage** (2). Monaco itself: `.utlx-editor-widget .monaco-editor`.

**Status bar** (class hooks — Theia StatusBar takes no data-testid):
`.utlx-sb-file-dialog-mode` (Load: Theia/Browser) · `.utlx-sb-name-on-load-mode` (Name: Inherit/Keep)

To list every hook (authoritative):
```bash
grep -rhoE "data-testid='utlx-[a-z-]+'" utlx-theia-extension/src/browser | sort -u
```

> These appear in the DOM only **after the extension is rebuilt** (`rebuild-and-start-mcp.sh`).

Tips that make a scenario easy to script reliably:
- Refer to UI by **what the user sees** ("click **Execute**", "the **Output** panel shows …")
  — I'll map those to stable selectors (adding `data-testid` hooks where needed).
- Keep example data **small and legible** (good on a slide); link a file under `examples/`.
- For a talk: prefer deterministic steps (utlxd) for the core; bracket any AI-assist step
  as optional (LLM latency/variance is risky live — can be pre-recorded).
