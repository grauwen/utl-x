# IB05: MC-mode AI assist generates a SCHEMA (USDL) instead of a UTL-X mapping — the request sends the contract's *schema* format as the transformation's output *data* format

**Status:** **FIXED in source** — (1) the MC generate path converts the contract **schema**
format to the **data** format it describes (`schemaToDataFormat`: jsch→json, xsd→xml, osch→odata,
tsch→csv, …) so the request's `outputFormat` is a data format, not a schema format; (2) the MC
prompt now explicitly forbids emitting a schema/USDL. The **prompt half is LIVE** (MCP rebuilt +
restarted). The **toolbar half needs a Theia extension rebuild + browser reload** to activate —
deferred so the large open sample isn't disturbed.
**Priority:** High — Message Contract AI assist is unusable: it returns the output contract
(as USDL) instead of a transformation, so the user gets a schema where they asked for a mapping.
**Created:** June 2026
**Component:** IDE toolbar generate-request assembly + the MC prompt's `outputFormat` usage —
front-end + mcp-server prompt. **Not** the daemon, **not** the CLI, **not** the `utlxe` engine.

> **One-line:** In MC mode the editor's `output` directive is the **contract schema format**
> (`jsch`/`xsd`/`usdl`). `extractOutputFormat()` returns that, the request sets
> `outputFormat = <schema format>`, and the MC prompt tells the model *"Output data format:
> usdl/jsch."* — so the model emits a **schema** (the contract echoed as USDL), not a
> `%utlx` mapping. The output *data* format should be what the contract **describes**
> (`xsd → xml`, `jsch → json`), not the schema format itself.

---

## Symptom

In Message Contract mode, "✨ Map to output contract" returns a **USDL schema** instead of a
transformation, e.g.:

```json
{
  "%namespace": "http://www.w3.org/2001/XMLSchema",
  "%version": "1.0",
  "%types": {
    "ShippingManifest": {
      "%kind": "structure",
      "%fields": [
        { "%name": "shipmentId",   "%type": "string",  "%required": true },
        { "%name": "weightKg",     "%type": "decimal", "%required": true }
        /* … the output contract, regurgitated as USDL … */
      ]
    }
  }
}
```

Expected: a `%utlx` mapping (header + `---` + body) that builds a data instance conforming to
the contract from the inputs.

## Root cause

1. **Editor output directive = schema format.** In MC mode the output is the contract (a
   schema), so the header is `output jsch` / `output xsd` / `output usdl`.
2. **The request copies that as the data format.** `utlx-toolbar-widget.tsx`:
   ```ts
   const outputFormat = this.extractOutputFormat(editorContent); // → "jsch" / "xsd" / "usdl"
   const request: GenerateUtlxRequest = { …, outputFormat, mode: 'message-contract', … };
   ```
   `extractOutputFormat` just reads the `output <format>` line — it does **not** convert a
   schema format to the instance data format.
3. **The prompt tells the model to emit that format.** `mcp-server/.../message-contract-prompt.ts`:
   ```ts
   parts.push(`Output data format: ${context.outputFormat}.`);   // "Output data format: usdl."
   ```
   Handed the output **contract schema** + *"conform EXACTLY to the output contract"* +
   *"Output data format: usdl"*, the model produces the **schema** (USDL), not a transformation.

In MC mode the transformation's runtime output is a **data instance** (XML/JSON/…) whose
structure is constrained by the contract schema. The contract is the *target structure*; the
*data format* is a separate thing. The pipeline conflates the two.

## Why it lands on USDL specifically

`usdl` is UTL-X's schema language, and an XSD contract surfaces as USDL in the IDE. Telling the
model "output data format: usdl" + giving it the contract is effectively "emit this schema in
USDL" — which is exactly what came back.

## Proposed fix (deferred — not done)

1. **Convert schema format → instance data format for the MC request.** Before building the
   request, map the contract format to the data format it describes
   (`xsd → xml`, `jsch → json`, `avro → json`, `proto → json`, `osch → json`, …). There is
   already a helper for this — `schemaFormatToInstanceFormat()` (used in
   `utlx-frontend-contribution.ts` for `onOutputSchemaFormatChanged`). The MC generate path
   should use it so `request.outputFormat` is a **data** format, never a schema format.
2. **Harden the MC prompt.** State explicitly that the output is a **data instance** (in the
   given data format) that *conforms to* the contract schema — and that it must **never** emit
   the schema/USDL itself. (Belt-and-suspenders on top of fix #1.)
3. **Guard generation.** If `outputFormat` resolves to a schema format (`jsch`/`xsd`/`usdl`/
   `avro`/`proto`/`osch`) in a *transformation* generate request, treat it as a wiring error
   (convert or reject) rather than passing it to the model.

## Scope / related

- **IB03 / IB04** — the same family: output **format** vs output **schema** are entangled in
  the panel/header. IB03 = named non-json output flips to json; IB04 = loaded schema's format
  not auto-detected. IB05 = the contract's schema format leaks into the transformation's data
  format. They should be reconciled together: a clear separation of "output **data** format"
  (xml/json/…) from "output **contract** schema (+ its format)".
- Surfaced by **IF11** (MC-mode schema-to-schema AI assist).

## Code pointers

- `theia-extension/.../browser/toolbar/utlx-toolbar-widget.tsx`:
  `extractOutputFormat()` (reads `output <format>`), generate-request assembly
  (`const request: GenerateUtlxRequest = { …, outputFormat, mode, …buildMCContractContext() }`).
- `theia-extension/.../browser/utlx-frontend-contribution.ts`:
  `schemaFormatToInstanceFormat()` (the existing schema→instance map to reuse).
- `mcp-server/src/llm/prompts/message-contract-prompt.ts`:
  `buildMessageContractUserPrompt()` — `Output data format: ${context.outputFormat}.` and the
  "conform EXACTLY to the output contract" instructions.
- `mcp-server/src/tools/generateUtlx.ts`: MC mode flows through the same generate→validate pipeline.
