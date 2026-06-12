# IB06: MC-mode output **Instance** tab inherits the **schema** format (`jsch`) — shows "jsch — not valid here" instead of `json`

**Status:** **FIXED in source** — `OutputPanelWidget.syncFromHeaders()` now resolves the output
directive's format to the **data** format the schema describes before setting `instanceFormat`
(`jsch → json` *or* `yaml`, `xsd → xml`, `osch → odata`, `tsch → csv` — the schema formats valid as an
MC output contract; `avro`/`proto`/`usdl` do not occur in MC and are omitted). A schema
format given as the output directive is **also captured on the Schema tab** (`schemaFormat`), and an
already-valid data instance is **preserved** (jsch describes JSON *or* YAML — a loaded `yaml` instance
stays `yaml`, it is not clobbered to `json`). Needs a **Theia extension rebuild + browser reload** to
activate.
**Priority:** Medium-High — in Message Contract mode, every project whose output contract is a schema
(the normal case) renders the output Instance tab as an invalid `jsch`, flagged red and unusable.
**Created:** June 2026
**Component:** IDE output panel — `output-panel/output-panel-widget.tsx` (`syncFromHeaders`). **Not**
the daemon, **not** the CLI, **not** the `utlxe` engine. Same schema-vs-data-format family as **IB05**.

> **One-line:** In MC the `output` directive carries the **contract schema** format (`output jsch`).
> `syncFromHeaders()` did `instanceFormat = parsedOutput.format` verbatim, so the **Instance** tab
> inherited `jsch` — a schema format that is invalid for an instance. The instance must be the data
> the schema *describes* (`jsch → json` or `yaml`), never the schema format itself.

---

## Symptom

In Message Contract mode, open a `.utlxp` whose output contract is a JSON Schema (`jsch`):

- The output **Schema** tab correctly shows the loaded `jsch` schema.
- Click the **Instance** tab → the format reads **`jsch`**, rendered red with
  **"jsch — not valid here"** (the `formatInvalid` flag in the dropdown).

Expected: the Instance tab is **`json`** (or **`yaml`** if the instance is already filled with YAML) —
the data the contract describes. An instance is never a schema; `jsch`/`xsd`/… must not appear there.

(Untested but same code path: an `xsd` contract would show `xsd` on the instance instead of `xml`.)

## Root cause

`OutputPanelWidget.syncFromHeaders(parsedOutput)` set the instance format straight from the parsed
output directive:

```ts
this.setState({ instanceFormat: parsedOutput.format, /* … */ });
```

In MC, the editor's `output` directive **is the contract schema format** (`output jsch`/`xsd`/`usdl`)
— exactly the conflation IB05 fixed on the AI-generate path. When `loadProjectFromRoot()` calls
`syncFromHeaders(parsed.output)` for an MC project, `parsed.output.format === 'jsch'`, so the
instance inherits `jsch`. The mapping from a schema format to the data format it describes already
existed in two places (`schemaFormatToInstanceFormat` in the contribution, `schemaToDataFormat` in the
toolbar) but **was never applied here**.

## Fix

`syncFromHeaders()` now splits the directive's format:

- **Instance** ← the **data** format, via `instanceFormatForSchema(fmt, current)`:
  - a schema format maps to the data it describes (`schemaDataFormats`: `jsch → [json, yaml]`,
    `xsd → [xml]`, `osch → [odata]`, `tsch → [csv]` — only the MC-valid contract schema formats;
    `avro`/`proto`/`usdl` don't occur in MC and are omitted);
  - because `jsch` describes JSON **or** YAML, if the current instance is already a valid data format
    for that schema it is **preserved** (a loaded `yaml` stays `yaml`); otherwise the first/default
    (`json`) is used;
  - a data format passes through unchanged.
- **Schema** ← if the directive was a schema format, `schemaFormat` is set to it too, so the Schema
  tab reflects the contract format even when `syncFromHeaders` runs before/without
  `displaySchemaResult`.

So `output jsch` → Instance `json`, Schema `jsch`; `output json` → Instance `json` (schema untouched);
a YAML-filled `jsch` instance → Instance `yaml`.

## Second leak — the **execute** output format (regression on MC-project load)

Same root cause, different sink. After loading an MC `.utlxp` (e.g. `order-enrichment.utlxp`) and
switching to **Execution** to run it, Execute failed with:

```
HTTP 500: Execution failed: UDM does not represent valid JSON Schema.
Expected at least one JSON Schema keyword. Got properties: [enrichedOrder]
```

i.e. the **execute request's output format was `jsch`** — the engine tried to emit the result *as a
JSON Schema*. The execute format is `UTLXFrontendContribution.outputFormat`, set by the
`onOutputSchemaFormatChanged` handler. That handler mapped schema→data **only in MC**
(`if (currentMode === MESSAGE_CONTRACT) … else outputFormat = event.format`). But during
`loadProjectFromRoot()`, `displaySchemaResult()` fires the schema-format event **before**
`fireModeChanged(MESSAGE_CONTRACT)`, so `currentMode` is still the startup default **Execution** — the
else-branch ran and `outputFormat` became `jsch`, which then drove Execute.

**Fix:** the handler now maps schema→data **unconditionally** (`schemaFormatToInstanceFormat`,
jsch→json, …) — this event always carries a *schema* format and the `output` directive / execute
format is always a *data* format, in either mode. Removes the mode gate (and the load-order race).
(`onOutputPresetOn`, the other spot that assigned a raw `schemaFormat`, has **no caller** — dead path,
left as-is.)

## Code pointers

- `browser/output-panel/output-panel-widget.tsx`:
  - `syncFromHeaders()` — resolves `instanceFormat` via `instanceFormatForSchema`; captures
    `schemaFormat` when the directive was a schema format.
  - `instanceFormatForSchema(fmt, current)` / `schemaDataFormats(fmt)` — new helpers (mirror the
    toolbar's `schemaToDataFormat`, with the multi-data-format `jsch → [json, yaml]` nuance).
- `browser/utlx-frontend-contribution.ts` — `onOutputSchemaFormatChanged` now maps schema→data in
  **both** modes (the execute-format leak above); `outputFormat` feeds the execute request.
- Related mappers (kept in sync): `utlx-frontend-contribution.ts` `schemaFormatToInstanceFormat`;
  `toolbar/utlx-toolbar-widget.tsx` `schemaToDataFormat`.

## Related

- **IB05** — same schema-vs-data-format conflation on the MC AI-generate path (the `output` directive
  carrying a schema format). IB06 is the **output-panel display** instance of the same root issue.
