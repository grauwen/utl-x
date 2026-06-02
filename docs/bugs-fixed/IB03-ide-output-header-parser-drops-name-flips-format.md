# IB03: IDE — output header parser ignores the output name, silently flipping every named non-`json` output back to `json` on round-trip

**Status:** **FIXED** — the header parser's `OUTPUT` pattern now accepts the optional
output name (`output <name> <format>`) and `parseOutput` returns it. A named output no
longer fails to parse, so the editor↔panel round-trip preserves the real output format.
Needs the extension **rebuild + reload** to go live.
**Priority:** High (silently corrupts a selected output format for ALL named outputs —
Tier 1 and Tier 2; common in plain Execution mode, not just MC)
**Created:** June 2026
**Component:** IDE header parser (`theia-extension/.../browser/parser/utlx-header-parser.ts`).
**Not** the daemon, **not** the CLI, **not** the `utlxe` engine — purely the IDE's
front-end header round-trip.

> **One-line:** `generateHeader` writes `output <name> <format>`, but the parser only
> matched `output <format>`. **Any** named output line (name ≠ `output`) failed to parse
> → the caller defaulted to `{format: 'json'}` → `syncFromHeaders` wrote that back,
> flipping the panel's output format to `json`. This affects **every non-json format** —
> Tier 1 (`xml`/`yaml`/`csv`/`odata`) **and** Tier 2 (`jsch`/`xsd`/`tsch`/`avro`/`proto`/`osch`).
> Only a named **json** output was invisibly unaffected (the parse-failure fallback *is*
> json). It was first *noticed* on a Tier-2 output, but it was never Tier-2-specific.

---

## Problem

In the IDE, give an output a **name** (any name other than the default `output` — outputs
get auto-named from a loaded file, e.g. `claim.json` → `claim`) and select **any non-json
output format**: a Tier-1 data format (`xml`/`yaml`/`csv`/`odata`) or a Tier-2 schema
format (`jsch`/`xsd`/`tsch`/`avro`/`proto`/`osch`, e.g. `jsch %USDL 1.0` — a JSON Schema
output where `%`-type USDL directives are usable). The selection does not stick: the
output format **reverts to `json`** on the next editor↔panel header sync. Switching modes
makes it obvious — e.g. going to Message Contract mode shows the output as `json` instead
of the chosen format.

This was first *noticed* on a Tier-2 (`jsch`) output, but it is **not** Tier-2-specific:
a named `output report xml` flips to `json` just the same in plain Execution mode.

It is **intermittent in appearance** ("works the second time"): once an unrelated
round-trip happens to set the linked `schemaFormat`, a subsequent re-selection appears
to hold — which masks the real, deterministic cause below.

## Reproduction

1. Execution mode. Give the output a name (e.g. load `claim.json` → output auto-named
   `claim`, or type a name) so the header becomes `output claim <format>`.
2. Pick **`jsch`** (or `xsd`/`tsch`/`avro`/`proto`/`osch`) as the output format.
3. The header is generated as `output claim jsch`.
4. On the next header parse (debounced edit / mode switch / paste sync), the output
   format **flips back to `json`**.

A **default-named** output (`output jsch`, no name) does **not** flip — and a **json**
output never appears to flip (its fallback is already json), which is why this hid for so
long.

## Root cause

Two halves that only collide for a *named, non-json* output:

**1. Generator emits the name** — `utlx-editor-widget.tsx` `generateHeader(...)`:

```ts
const hasCustomName = trimmedName !== '' && trimmedName !== 'output';
header.push(hasCustomName ? `output ${trimmedName} ${outputFormat}` : `output ${outputFormat}`);
```

**2. Parser ignored the name** — `parser/utlx-header-parser.ts`, the `OUTPUT` pattern:

```ts
// BEFORE
OUTPUT: /^output\s+(csv|odata|osch|tsch|json|xml|yaml|xsd|jsch|avro|proto)(?:\s+(\{[^}]+\}))?/,
```

For `output claim jsch`, the token after `output ` is `claim`, which is **not** a format
keyword, so the regex **does not match**. `parseOutput` returns `null`, the line is
recorded as an error, and `parseUTLXHeaders` leaves the default:

```ts
output: { format: 'json' }   // ← the silent flip
```

`parseAndUpdatePanels` then fires `HeadersParsed`, the frontend contribution calls
`outputPanel.syncFromHeaders(parsedOutput)`, and `syncFromHeaders` writes
`instanceFormat: parsedOutput.format` = **`json`**, overwriting the user's `jsch`.

The input parser was **not** affected — `SINGLE_INPUT`/`INPUT_PART` already match
`input <name> <format>`. Only the **output** pattern lacked the optional name.

## Why it was not seen before

- Output **names** are recent (per-output name + auto-name-from-filename work, IF-era
  output-panel changes). Before that, every output was the default `output` → header
  `output <format>` → the parser matched → no flip.
- For **json** outputs the bug is invisible: the parse-failure fallback is `json`, which
  equals the intended value.
- It bites **any named** output set to a **non-json** format — Tier 1
  (`xml`/`yaml`/`csv`/`odata`) *and* Tier 2 (`jsch`/`xsd`/…). It just happened to be
  *spotted* in the new Tier-2 / Message-Contract work, but a named `xml`/`csv` output in
  plain Execution mode was silently reverting to `json` too.

## Fix (IDE-only, no daemon/engine/CLI change)

`parser/utlx-header-parser.ts` — accept the optional name and return it:

```ts
// AFTER
OUTPUT: /^output\s+(?:([a-zA-Z_][a-zA-Z0-9_-]*)\s+)?(csv|odata|osch|tsch|json|xml|yaml|xsd|jsch|avro|proto)(?:\s+(\{[^}]+\}))?/,
```

```ts
const [, name, format, optionsStr] = match;
return { format, ...(name ? { name } : {}), ...options };
```

(`ParsedOutput` gains an optional `name`.) Regex backtracking keeps the unnamed form
working: `output json` → no name, `format = json`.

**Verified** by unit round-trip (fixed parser) — note Tier 1 was equally broken:

```
"output jsch"                -> format=jsch   name=(none)
"output result jsch"         -> format=jsch   name=result      (previously flipped to json)
"output report xml"          -> format=xml    name=report      (previously flipped to json)
"output report yaml"         -> format=yaml   name=report      (previously flipped to json)
"output report csv"          -> format=csv    name=report      (previously flipped to json)
"output myOut json"          -> format=json   name=myOut
"output report-2026 jsch {}" -> format=jsch   name=report-2026
```

And the **old** regex, proving every named non-json output failed pre-fix:

```
OLD = /^output\s+(csv|odata|osch|tsch|json|xml|yaml|xsd|jsch|avro|proto)(?:\s+(\{[^}]+\}))?/
"output xml"          -> format=xml
"output report xml"   -> NO MATCH  -> defaults to json   (flip)
"output report yaml"  -> NO MATCH  -> defaults to json   (flip)
"output report jsch"  -> NO MATCH  -> defaults to json   (flip)
```

## Acceptance

- A named output set to **any** non-json format **keeps** that format across header
  round-trips (edit, mode switch, paste sync) — no flip to json. Covers Tier 1
  (`output report xml` stays `xml`) and Tier 2 (`output claim jsch` stays `jsch`).
- Default-named and json outputs are unchanged.
- The parsed output **name** is preserved (named outputs survive copy/paste header sync
  instead of being dropped).
- Downstream MC behavior is now consistent on the **first** try: a schema-format value
  left as the output instance is flagged (`<fmt> — not valid here`, red) and blocks the
  Schema tab, instead of silently appearing as `json`.

## Related

- **IB01 / IB02** — other IDE-layer defects surfaced by recent input/output-panel work
  (IB02 was likewise exposed by filename auto-naming).
- Exposed by per-output naming + auto-name-from-filename (output-panel work).
- Interacts with the MC-mode format-restriction / invalid-format-flagging work (the
  red "not valid here" + schema-tab blocking only behaves consistently once this flip
  is fixed).
