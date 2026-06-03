# IF17: IDE — Header-Level Schema Binding (`input/output … {schema: "…"}`)

**Status:** Proposed
**Priority:** High (the IDE silently drops a valid engine header construct on round-trip)
**Created:** June 2026
**Component:** IDE stack (Theia extension) — header parser, input/output panels, MC design-time binding
**Depends on:** the engine header grammar (utlxe `FormatSpec` options), EF02 (schema-validation precedence: config ?? header), IF04 (`transform.yaml` config editor — the other schema-binding surface), IF11 (Message Contract design-time schema/instance binding), IF03 (bundle `schemas/`)
**Effort:** Medium

> **Design decisions captured here (not yet implemented):**
> - utlxe already lets a **format declaration carry a bound schema directly in the `.utlx`
>   header**, via the format **options block**:
>   `input order json {schema: "order.json"}` / `output invoice xml {schema: "invoice.xsd"}`.
>   The IDE must **parse, surface, and re-emit** that binding (today it drops it).
> - This is the natural place to express **design-time** schema↔instance binding: the
>   schema-only toggles + MC schema work (IF11) should **write the binding into the header**,
>   unifying design-time and execution rather than keeping a separate IDE-only notion.
> - It must be **reconciled with IF04** — `transform.yaml`'s `inputs[].schema` /
>   `output.schema` is the *other* surface for the same engine concept. Define precedence;
>   don't invent a third, divergent binding.

---

## Summary

The engine binds a schema to an input/output **format inside the `.utlx` header** using the
format options block — the same `{…}` mechanism the header already uses for `{headers:false}`,
`{delimiter:";"}`, `{arrays:[…]}`. The IDE's header parser captures the options block but
**only extracts CSV/XML/OData keys** — it silently **drops `schema:`** and has no field for
it. So the IDE can't show a header-bound schema, can't let a user set one, and **loses the
binding when it re-emits the header**. IF17 makes header-level schema binding a first-class
IDE capability: parse it, surface it in the input/output panels, write it back losslessly,
and use it as the persistence target for design-time (MC) schema binding.

## Engine truth (verified, source of truth)

The header `FormatSpec` grammar is:

```
formatSpec  ->  formatType [ %dialect version ] [ { options } ]
```

(`modules/core/.../parser/parser_impl.kt` `parseFormatSpec`; AST
`modules/core/.../ast/ast_nodes.kt` `FormatSpec(type, dialect, options, location)`.)

A schema is bound as the **`schema` option key**, consumed by the strategies:

| Strategy | Reads |
|---|---|
| `CompiledStrategy.kt:47/52/54` | `spec.options["schema"] as? String` → `schemaRef`, `inputSchemaRef`, `outputSchemaRef` |
| `CopyStrategy.kt:57/61/63` | `spec.options["schema"]` → `InputSchemaRef(...)`, `inputSchemaRef`, `outputSchemaRef` |
| `TemplateStrategy.kt:25/29/31` | `spec.options["schema"]` → `InputSchemaRef(...)`, `inputSchemaRef`, `outputSchemaRef` |

So the real, runnable syntax is:

```utlx
%utlx 1.0
input order json {schema: "order.json"}
output invoice xml {schema: "invoice.xsd"}
---
…
```

The schema reference is a string path (resolved against the bundle's `schemas/`, like
`transform.yaml` refs). It feeds the strategy's `inputSchemaRef`/`outputSchemaRef`, i.e. it
is what the engine's **validationPolicy** validates against.

## Problem

`src/browser/parser/utlx-header-parser.ts`:

- **Captures** the options block on every form — `SINGLE_INPUT`, `INPUT_PART`, `OUTPUT` all
  end in `(?:\s+(\{[^}]+\}))?` — so a header with `{schema:"x"}` **parses without error**,
  and the **format is read correctly** (no IB03-style format flip).
- But `parseOptions()` extracts **only** `headers`/`delimiter`/`bom` (CSV),
  `encoding`/`arrays` (XML), `metadata`/`context`/`wrapCollection` (OData). There is **no
  `schema` extraction**, and `ParsedInput`/`ParsedOutput` have **no `schema` field**.

Consequences:

1. A header-bound schema is **invisible** in the IDE — the panels never show it.
2. There is **no way in the IDE to set** a header-bound schema.
3. **Silent data loss on round-trip:** when the IDE re-emits the header from panel state,
   `{schema:…}` is gone — a valid engine binding is dropped. (Bug-flavored; this feature
   resolves it. Not filed as a standalone IB because the fix *is* this feature — but the
   round-trip loss is the concrete regression to test against.)

## Goals

- `utlx-header-parser.ts` **parses** `schema` out of the options block into a new
  `schema?: string` on `ParsedInput` / `ParsedOutput` (single, multi-input, output).
- The IDE **re-emits** `{schema: "…"}` losslessly, preserving co-existing options
  (e.g. `{headers:false, schema:"x"}`).
- The **input/output panels** show and let the user set the bound schema — a picker
  constrained to the bundle's `schemas/` (reuse the IF04 picker / IF03 bundle model); a
  dangling reference is flagged.
- **Design-time unification:** the MC schema-only binding (IF11) **writes into the header
  `{schema:}`** (and/or `transform.yaml`, per the precedence decision), so binding a schema
  to an instance at design time is the *same* artifact the engine reads at execution.
- **Reconcile with IF04:** define precedence between header `{schema}` and `transform.yaml`
  `inputs[].schema`/`output.schema`; surface both consistently; never diverge.

## Non-Goals

- Changing the **engine** grammar or strategies — IF17 surfaces an *existing* engine
  capability; no CLI/utlxe change. (Per project rule: don't touch CLI/utlxe unless no other
  way — here there is another way: the IDE layer.)
- Inventing schema-binding semantics the engine doesn't have (only the `schema` option key).
- Schema **content** parsing/inference — that's IF12/IF13 (utlxd). IF17 only binds a *ref*.
- Bundle-wide topology — IF05.

## Design

### Parser (read + write)
- Add `schema?: string` to `ParsedInput` and `ParsedOutput`.
- In `parseOptions()`, add a `SCHEMA: /schema:\s*"([^"]+)"/` pattern (mirrors
  `CSV_DELIMITER`); set `options.schema` when matched. Works for single-input, multi-input
  (`INPUT_PART`), and output — all already pass the options string through `parseOptions`.
- Header **emit** (wherever the panels serialize back to the header) must include
  `schema: "…"` in the options object, merged with any existing options, quoted.

> Note: the capture group is `\{[^}]+\}` — fine for `{schema:"path"}` and
> `{headers:false, schema:"path"}` (string-valued, no nested braces). Schema refs are paths,
> so no nesting concern.

### Panels (surface + set)
- Input panel (`multi-input-panel-widget.tsx`) and Output panel
  (`output-panel-widget.tsx`) gain a **"Bound schema"** field per input/output: a dropdown
  populated from the bundle `schemas/` (IF03 model; IF04 picker), plus "none".
- This composes with the **schema-only toggle** already built: a schema-only input *is* a
  contract; a data input *with* a bound schema is data + its validation contract. The bound
  schema feeds **validationPolicy** at execution.
- A bound ref not present in `schemas/` is flagged (red), consistent with the existing
  invalid-format treatment and IF04's dangling-reference flag.

### Schema reference naming & path normalization
The schema reference is a **quoted string literal** (the value of the `schema` option:
`{schema: "…"}`), **not an identifier** like the input/output *name*. This distinction drives
all of the IDE's loading/naming behaviour:

- **Any filename is allowed — do not sanitize it.** The start-with-a-letter rule
  (`[a-zA-Z_][a-zA-Z0-9_-]*`) applies only to the input/output **name** token. A schema
  reference is a string/path, so leading digits, dots, hyphens, mixed case are all legal:
  `{schema: "00-enterprise-order.json"}`, `{schema: "ACME.Order.v1.json"}` are valid UTLX.
- **Preserve the technical name verbatim — strip only the directory.** Customers generate
  schemas with names like `00-…`, `v2_…`; the reference must **match the file on disk** because
  the engine resolves it with `bundlePath.resolve(schemaRef)` (`UtlxEngine.kt:374`). Rewriting
  `00-enterprise-order.json` → `enterprise-order.json` would create a **dangling reference**
  (load/`--validate` "schema file not found"). So when loading `…/Downloads/00-enterprise-order.json`,
  the IDE keeps the **basename** and **never strips the `00-` prefix or any name characters**.
- **Make it bundle-root-relative (`schemas/<file>`), not a bare basename.** The engine resolves
  refs from the **bundle root**, not from an implicit `schemas/`. A bare
  `"00-enterprise-order.json"` resolves to `bundleRoot/00-…` and won't find a file under
  `schemas/`. So the normalized ref the IDE writes is `schemas/00-enterprise-order.json`
  (drop the absolute source path; prepend the bundle-relative location where the file lives /
  will be copied). This is the only "stripping" — the **path**, never the **name**.
- **Reject (don't mangle) refs the grammar can't carry.** The parser captures the options
  block as `\{[^}]+\}` and the value as `"([^"]+)"`, so a filename containing `}` or `"` would
  break parsing — warn and reject rather than silently rewrite. Per EF02 §2, also reject
  **absolute paths** and `../` **bundle-escapes**.
- **Do not derive the input/output *name* from the schema filename.** A default (unnamed)
  output stays unnamed; the schema is bound via options, not promoted to a name. Inventing a
  name would require sanitizing `00-` (identifier rule) *and* conflate the contract reference
  with the slot name — keep the two separate.

### How a default-named output with a bound schema renders
With the output name left at its default (omitted) and a schema loaded, the header is just
`output <format> {schema: "<schemas-relative ref>"}`:

```utlx
%utlx 1.0
input order json
output json {schema: "schemas/00-enterprise-order.json"}
---
…
```

The `OUTPUT` regex makes the name group optional, so this parses as name=∅, format=`json`,
options=`{schema}` — no name is invented from the file.

> **Don't confuse with the MC schema-*only* output.** Two different intents render
> differently:
> - Output is **data validated against a schema** → `output json {schema: "schemas/00-enterprise-order.json"}`
>   (produces JSON, validated against that contract — the IF17 case).
> - Output **is** the schema/contract (MC schema-only toggle) → `output jsch` — the output
>   *is* a JSON Schema; the loaded file is the design-time **target** to map toward (coverage),
>   **not** a `{schema:}` validation binding.

### Two binding surfaces — precedence is defined in EF02
Both the header `{schema}` **and** `transform.yaml` (`inputs[].schema` / `output.schema`)
express the same engine concept. The header travels *with* the `.utlx` (works without a
bundle); `transform.yaml` is bundle/deploy-time. **The precedence is specified in
[EF02 §1](EF02-engine-schema-validation.md)** — *config wins, header is the developer's
fallback default, the EF03 runtime override trumps all*:

```
Effective schema = runtimeOverride.schema ?? config.schema ?? header.schema
Effective policy = runtimeOverride.policy ?? config.validationPolicy ?? header.validationPolicy ?? <default>
```

| Source | Set by | Persistent | Wins over |
|---|---|---|---|
| Runtime override (EF03 Admin API) | Ops, during an incident | No (ephemeral) | everything |
| `transform.yaml` config | Ops, at deploy time | Yes (on disk) | the header |
| `.utlx` header `{schema}` | Developer, at dev time | Yes (in source) | the built-in default |

Rationale (EF02): config is the operational override (per-environment, deploy-time); the
header is the developer's in-source default so a `.utlx` "works out of the box" yet ops can
override it per environment. The runtime override is the emergency lever (ephemeral; gone on
restart).

> **Implementation reality (verified June 2026 — do not over-promise):** EF02 is **Status:
> Design**, and only the **config path is wired** today. `UtlxEngine.initialize`
> (`UtlxEngine.kt:125-143`) builds validators **solely from `transform.yaml`**
> (`transformConfig.inputs[].schema` / `output.schema` via `resolveValidatorFromConfig`; the
> comment reads *"EF02: Create validators from transform.yaml schema references"*). The header
> `{schema}` **is** read — but by the **strategies** (`spec.options["schema"]` →
> `inputSchemaRef`/`outputSchemaRef` in `CompiledStrategy`/`CopyStrategy`/`TemplateStrategy`),
> for schema-aware compilation/copy — **not** as the `?? header.schema` *validation* fallback
> EF02 describes. So the documented "header as validation fallback" is **not yet implemented**
> in the validator path. Also: the real config field names are `inputs[].schema` /
> `output.schema` with `validationPolicy` defaulting to **`"SKIP"`** (`TransformConfig.kt`) —
> *not* EF02's draft `inputValidation`/`inputSchema`/`"strict"` names. IF17's IDE behaviour
> follows the **documented EF02 order**; where it differs from the engine today, IF17 should
> flag the gap, not assume the engine already merges.
- The IDE should **show both** surfaces and warn on conflict rather than silently pick one,
  presenting the EF02 order (config overrides header).
- The IF04 config editor and the IF17 panel field should read/write a **single reconciled
  model**, not two divergent ones.

### Design-time ↔ execution unification (ties to IF11)
Binding a schema to an instance at design time (MC mode) becomes "set the input/output's
bound schema," persisted into the header `{schema:}`. The MC coverage/contract work then has
a durable, engine-honored home instead of an IDE-only notion.

## Implementation Notes
- Parser change is small and self-contained (`utlx-header-parser.ts`: one interface field +
  one pattern + emit). Start here; it immediately fixes the round-trip data loss.
- Panel field reuses IF03's bundle `schemas/` listing and IF04's picker/dangling-ref logic —
  sequence IF17's UI after (or alongside) IF04.
- Precedence is **already specified in EF02** (config ?? header, runtime override on top) and
  **verified against the code**: the engine wires only the config validator path today, and
  the header `{schema}` reaches the strategies but not the validation fallback. The IDE
  follows the EF02 order and flags where the engine hasn't yet implemented the merge.

## Acceptance Criteria
- A `.utlx` whose header binds a schema (`input order json {schema:"order.json"}`) parses
  with `schema` populated on the input/output, and the format is unchanged.
- Editing in the IDE and re-emitting the header **preserves** `{schema:"…"}` (and any
  co-existing options). No silent drop. (The regression this feature removes.)
- The input/output panels show the bound schema and let the user set/clear it from the
  bundle's `schemas/`; a dangling ref is flagged.
- Loading a schema file **preserves its technical filename verbatim** (e.g.
  `00-enterprise-order.json` is **not** sanitized/de-prefixed); only the source directory is
  stripped and the ref is written **bundle-root-relative** (`schemas/00-enterprise-order.json`).
- A default (unnamed) output with a loaded schema renders as
  `output <format> {schema: "schemas/<file>"}` — **no name is invented** from the filename.
- A filename containing `}` / `"`, an absolute path, or a `../` escape is **rejected with a
  message**, never silently rewritten.
- Header `{schema}` and `transform.yaml` schema refs are shown consistently with a defined,
  documented precedence; a conflict is surfaced, not silently resolved.
- No engine/CLI change; behavior matches what utlxe already reads from `spec.options["schema"]`.

## Testing
- **Unit (parser):** round-trip headers with `{schema:"x"}`, `{headers:false, schema:"x"}`,
  multi-input each with a schema, output with a schema — assert `schema` parsed and re-emitted
  byte-faithfully (modulo formatting).
- **Regression:** the explicit "header with `{schema}` survives an IDE open→edit→save"
  test (guards the data-loss bug).
- **Integration:** set a bound schema in the panel → build bundle (IF03) → engine loads and
  validates against it (validationPolicy), matching a hand-written header.
- **Reconciliation:** header `{schema}` + `transform.yaml` schema both set → IDE shows both,
  flags conflict; engine load matches the documented precedence.
- **Naming/normalization:** load `…/Downloads/00-enterprise-order.json` → ref written is
  `schemas/00-enterprise-order.json` (name verbatim, path stripped); names with leading
  digits/dots/mixed-case round-trip; a `}`/`"`/absolute/`../` ref is rejected, not mangled.

## Related
- **EF02** — *Engine Schema Validation* — **owns the precedence rule** (config ?? header,
  runtime override on top) IF17 follows; the engine-side adaptation of header `{schema}`.
- **IF04** — `transform.yaml` config editor; the *other* schema-binding surface to reconcile.
- **IF11** — MC design-time schema/instance binding; persists into the header via IF17.
- **IF03** — bundle `schemas/` (the ref target) + the schema picker model.
- **IF12 / IF13** — utlxd schema parse/inference (the schema *content*, separate concern).
- **IB03** — sibling header round-trip bug (output name drop); same parser, same class of
  silent-loss-on-round-trip defect.
- Engine: `parser_impl.kt` `parseFormatSpec`, `ast_nodes.kt` `FormatSpec`,
  `CompiledStrategy.kt` / `CopyStrategy.kt` / `TemplateStrategy.kt` (`spec.options["schema"]`).

## Effort Estimate
Medium. Parser read/write + the data-loss fix is small (~2-3 days). The panel field +
`schemas/` picker reuses IF03/IF04 (~0.5 wk). The IF04 reconciliation + precedence
verification and the IF11 design-time wiring are the substantive part (~1 wk).
