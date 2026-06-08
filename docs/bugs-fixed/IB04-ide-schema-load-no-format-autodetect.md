# IB04: IDE — loading a output schema file does not auto-detect its format (and the Load picker filters to the *selected* format), so a mismatched schema (e.g. XSD loaded while the format is JSCH) is tagged with the wrong format and silently breaks MC contract coverage

**Status:** **OPEN** — diagnosed, not fixed (deferred). No code change yet.
**Priority:** Medium — silent + misleading: the schema *is* loaded but the contract-coverage
panel says "Load an output schema…", so the user thinks nothing loaded. MC-mode AI assist
then has no usable coverage to ground generation.
**Created:** June 2026
**Component:** IDE output-panel schema loader + the coverage snapshot — front-end only.
**Not** the daemon, **not** the CLI, **not** the `utlxe` engine.

> **One-line:** The output **Schema** Load keeps the *currently selected* schema format
> (e.g. `jsch`) instead of detecting the loaded file's real format. Load an `.xsd` while the
> dropdown says `jsch` and the content is XSD but tagged `jsch`. Coverage then parses XSD
> with the JSCH parser → no usable output-field tree → the coverage panel falls back to its
> empty "Load an output schema (and input schemas)…" hint, **even though a schema is loaded**.
> Compounding it: the file picker's filter is built from the selected format, so the `.xsd`
> isn't even shown until you switch the dialog to "All Files".

---

## Problem (two coupled defects)

In **Message Contract mode**, on the output **Schema** tab:

1. **Picker filters to the selected format.** With the output schema format set to `jsch`
   (the default), the Load dialog only offers JSON/JSCH files. To pick an `.xsd` you must
   manually switch the dialog to **All Files**.
2. **No format auto-detection on load.** After loading the `.xsd`, the format stays whatever
   the dropdown was (`jsch`). The schema *content* is XSD but it is *tagged* `jsch`.

Downstream, the tag is trusted: `getExpectedSchema()` returns
`{ content: <XSD>, format: 'jsch' }`, and the coverage analysis parses the XSD **with the
JSCH parser**. It extracts no output contract fields, so the AI-assist "Contract coverage"
panel shows its empty state:

> *Load an output schema (and input schemas) to see how well the inputs cover the output contract.*

— which is misleading, because a schema **is** loaded. The real fault (format mismatch) is
hidden behind a "nothing loaded" message.

## Reproduction

1. Message Contract mode. Output panel → **Schema** tab; format dropdown = **`jsch`** (default).
2. Click **Load**. The dialog filters to JSON/JSCH — choose **All Files** to see your `.xsd`.
3. Select an **XSD** output schema. It loads; the format dropdown **stays `jsch`** (not `xsd`).
4. Open AI assist → **Contract coverage** shows "Load an output schema (and input schemas)…"
   despite the schema being loaded. MC generation has no coverage plan to work from.

(Loading a JSCH file while the dropdown is `jsch` works — the mismatch is the trigger.)

## Root cause

- **Load keeps the dropdown format.** `output-panel-widget.tsx` `handleLoadSchema()` fires
  `RequestLoadOutputSchema({ schemaFormat: <current dropdown> })`; the contribution's
  `onRequestLoadOutputSchema → loadOutputSchemaFromFile(event.schemaFormat)` carries that
  format through to `displaySchemaResult({ schemaContent, schemaFormat })`. **The file's
  actual format is never sniffed** (no extension/content detection). So a wrong-format file
  is stored with the wrong `schemaFormat`.
- **Picker filter built from the selected format.** The schema Load dialog's `accept` /
  `filters` are derived from the selected format's extensions (same pattern as the instance
  loaders, `getInstanceFileExtensions`), so a non-matching file is hidden by default.
- **Coverage trusts the tag.** `utlx-toolbar-widget.tsx` `snapshotCoverage()` →
  `buildContractCoverage(inputs, expected.content, expected.format)` (`utils/coverage.ts`)
  parses `expected.content` (XSD) as `expected.format` (`jsch`). With no parseable contract
  fields it yields no usable coverage, and `renderCoverage()` shows the empty-state hint.

## Why it's misleading

`getExpectedSchema()` only guards on *presence* of `schemaContent` + `schemaFormat`, not on
whether the content actually parses as that format. So a wrong-format schema is "present but
unparseable" — and the coverage empty-state cannot tell that apart from "no schema loaded".

## Proposed fix (deferred — not done)

1. **Auto-detect schema format on load.** From the file extension
   (`.xsd`→`xsd`, `.json`/`.jsch`→`jsch`, `.avsc`→`avro`, `.proto`→`proto`, `.osch`/EDMX→`osch`, …)
   and/or a content sniff (`<xs:schema`, `{"$schema"`, `syntax = "proto"`, …). Set the
   dropdown + `schemaFormat` to the **detected** format, and surface a notice when it differs
   from the current selection ("Loaded XSD — switched output schema format jsch → xsd").
2. **Broaden the schema Load picker.** Offer all supported schema extensions (or default to
   "All Files") so a different-format schema doesn't require manually overriding the filter.
3. **Make the coverage empty-state honest.** Distinguish "no schema loaded" from "schema
   loaded but couldn't be parsed as `<format>`" — show a parse/format error instead of the
   "Load an output schema…" hint.

## Scope / related

- The **same pattern** likely affects the **input** schema load and instance loads (filter
  from the selected format + no auto-detect) — worth fixing together.
- **IB03** — adjacent output-header/format handling (named non-json output flipping to json).
  Once IB03 + IB04 are both fixed, the output format/schema story is consistent end to end.
- Surfaced while wiring **IF11** (MC-mode schema-to-schema AI assist), whose coverage panel
  is what made the silent mismatch visible.

## Code pointers

- `theia-extension/.../browser/output-panel/output-panel-widget.tsx`:
  `handleLoad()` (schema tab → `handleLoadSchema()`), `handleLoadSchema()` (sends the current
  `schemaFormat`), `displaySchemaResult()` (sets `schemaContent` + `schemaFormat` from the
  result), `getExpectedSchema()` (presence-only guard).
- `theia-extension/.../browser/utlx-frontend-contribution.ts`:
  `onRequestLoadOutputSchema → loadOutputSchemaFromFile(event.schemaFormat)` and its
  browser/Theia file pickers (filters from the format; format passed straight through).
- `theia-extension/.../browser/toolbar/utlx-toolbar-widget.tsx`:
  `snapshotCoverage()`, `renderCoverage()` (empty-state).
- `theia-extension/.../browser/utils/coverage.ts`: `buildContractCoverage(...)`.
