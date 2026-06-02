# IF12: utlxd — high-quality, all-format schema parsing API (deep `/api/parse-schema`); MCP + IDE delegate, browser parsers retired

**Status:** Proposed
**Priority:** High (unblocks MCM coverage for all schema formats incl. avro/proto; removes browser↔engine divergence)
**Created:** June 2026
**Depends on:** existing analysis parsers (`JSONSchemaParser`, `XSDSchemaParser`), the `formats/*` schema parsers (deep UDM), `modules/analysis` `TypeDefinition`; consumed by IF11 (MCM coverage) and IF10 (input abstract)
**Effort:** Large (phased; daemon-side Kotlin + MCP + IDE TS)

> **Scope note (taxonomy):** "IF" here = the **IDE stack** — Theia frontend **+** the MCP
> server **+** the **utlxd** daemon. It deliberately does **NOT** touch **utlxe** (the
> production engine, `modules/engine`) or **CLI core** (`modules/cli`). Those stay
> byte-for-byte unchanged (see "Module isolation"). This is why it's IF, not EF.

> **Design decisions captured here (not yet implemented):**
> - utlxd becomes the **single source of truth** for parsing a schema into a structured
>   type tree. One endpoint, all six formats, **deep** (recursive), with required/nullable.
> - The IDE and MCP **delegate** to it; the **browser-side** `schema-field-tree-parser.ts`
>   is retired (it duplicates engine logic and lacks avro/proto — the IB03 class of bug).
> - Reuse the engine's **existing** parsers **read-only**; add only daemon-side adapters.
>   **No change to `formats/*`, `modules/analysis`, `modules/cli`, `modules/engine`.**

---

## Summary

Today the IDE parses schemas with **browser code** (`schema-field-tree-parser.ts`) because
utlxd has no high-quality schema-parse capability: `/api/parse-schema` handles only
xsd + json-schema and serializes **shallow** (one level, stringified), and
`/api/udm/export`'s `%types` is also shallow. So the browser re-implements deep parsers
for 4 formats and **can't do avro/proto at all** — leaving the MCM coverage analyzer (IF11)
blind to avro/proto and prone to browser↔engine drift (cf. IB03). IF12 makes **utlxd** the
authoritative schema parser: one endpoint returning a **deep, recursive type tree** for all
six formats, which the MCP and IDE consume — retiring the browser parsers.

## Problem

- `/api/parse-schema`: only `xsd` + `json-schema`; `serializeTypeDefinition` flattens the
  (internally deep) `TypeDefinition` via `describeType()` → one-level, stringified
  (`"customer":"{type=object, nullable=false}"`), losing nested leaves.
- `/api/udm/export` `%types`: all formats but **top-level only** (no `customer.name`,
  no `lines[].sku`).
- Browser `schema-field-tree-parser.ts`: deep for `jsch/xsd/osch/tsch`, but **no avro/proto**,
  and it diverges from the engine (a JSON-Schema quirk handled one way in the browser and
  another in the engine is invisible until it bites — the IB03 class).
- Net: MCM coverage (IF11) can't cover avro/proto (the MC dropdown even offers them), and
  every format is parsed twice (engine + browser) with no guarantee of agreement.

## Goals

- **One endpoint**, `POST /api/parse-schema`, returning a **deep recursive type tree**
  (`TypeNode`) for **all six** schema formats (`jsch, xsd, osch, tsch, avro, proto`), with
  required/nullable preserved.
- **MCP** and **IDE** delegate to it; the browser schema parsers are **retired** (or kept
  only as an offline fallback).
- **Zero** change to utlxe / CLI / `formats/*` / `analysis` — reuse them read-only.

## Non-Goals

- Changing utlxe or CLI behavior (explicitly out of scope — this is IDE-stack work).
- Deepening the USDL `%types` *summary* in `formats/*` (that would touch shared modules and
  **would** affect utlxe — a separate **EF**, only if ever wanted; see "The one EF-adjacent
  thing").
- Schema **inference** (data → schema) — the symmetric direction, covered in **IF13**.
- A new schema *conversion* API (osch→jsch etc.) — separate.

## Design

### The contract — `TypeNode` (deep, recursive)

```
POST /api/parse-schema   { format: "jsch|xsd|osch|tsch|avro|proto", schema: "<content>" }
→ { success: true, type: TypeNode }   |   { success: false, error }

TypeNode = {
  kind: "object" | "array" | "scalar" | "union",
  name?: string,            // field name (when a member of an object)
  required?: boolean,       // object members: in the schema's required set / non-nullable
  nullable?: boolean,
  scalarType?: string,      // scalar: string | number | integer | boolean | date | datetime | …
  constraints?: string,     // optional (maxLength, enum, pattern, …) when the parser has them
  fields?: TypeNode[],      // object: nested members  (DEEP)
  element?: TypeNode        // array: element type      (DEEP)
}
```

Recursive by construction → leaves like `customer.name`, `lines[].sku` are present. Directly
mappable to the IDE's `SchemaFieldInfo` and to MCP coverage hints.

### Daemon implementation (utlxd only)

1. **Recursive `serializeTypeDefinition`** (`RestApiServer.kt`): the engine's
   `TypeDefinition` is already deep (`Object.properties[].type` is itself a `TypeDefinition`);
   the serializer just calls `describeType()`. Make it **recurse** into object properties and
   array element types, emitting `TypeNode`. → fixes `jsch`/`xsd` depth immediately.
2. **Avro / Proto / OSch / TSch**: obtain the **deep raw UDM** from the existing `formats/*`
   parsers (e.g. `AvroSchemaParser.parse` already returns the full Avro structure as UDM —
   its comment: *"The UDM already represents the Avro schema structure directly"*), then a
   **daemon-side `udmSchemaToTypeNode` adapter** walks that deep UDM → `TypeNode` (carrying
   names, types, and required where the structure encodes it — e.g. Avro `["null", X]` union
   → optional). The shallow `%types` summary is **bypassed**.
3. **Dispatch**: `/api/parse-schema` routes all six formats to the above; returns `TypeNode`.

### Module isolation (the utlxe/CLI guarantee)

| Module | Action |
|---|---|
| `modules/daemon` (utlxd) | **edit** — `RestApiServer` + new daemon-side adapters + tests |
| MCP server | **edit** — migrate the consumer to `TypeNode` |
| Theia (`theia-extension`) | **edit** — delegate; retire browser parsers |
| `formats/*`, `modules/analysis`, `modules/cli`, `modules/engine` (utlxe) | **read-only — untouched** |

The daemon already depends on all `formats/*` + `analysis`, so the adapters need **no** new
deps. utlxe and CLI never call `/api/parse-schema`; they keep using the same parsers via the
UDM/transform path. → **utlxe is byte-for-byte unchanged** (same discipline as IB02).

### MCP consumer migration

The MCP server currently consumes the *shallow* `/api/parse-schema`. Migrate it to the new
`TypeNode` (either additive — keep the old `normalized` field alongside a new `type` — or
update the MCP tool in lockstep). Verify no MCP tool regresses.

### IDE delegation (Theia)

- `coverage.ts` `parseSchemaToFields` and IF10's `buildAbstractForInput` call
  `/api/parse-schema` via `UTLXService` (new method, e.g. `parseSchema(content, format)`),
  mapping `TypeNode` → `SchemaFieldInfo`. Coverage becomes **async** (the dialog already
  awaits status checks).
- **Retire** `schema-field-tree-parser.ts` (or keep behind a flag as an offline fallback).
- **Re-verify** all `examples/mcm` deltas against the delegated path (they were verified
  against the browser parser; the engine path should match or be more correct).

## Implementation Notes (phased)

- **Phase 1 — deep serializer:** recursive `serializeTypeDefinition` + `TypeNode` for
  `jsch`/`xsd`; Kotlin tests; keep old field if MCP needs it. (Small, foundational.)
- **Phase 2 — four adapters:** `udmSchemaToTypeNode` + per-format wiring for
  `avro`/`proto`/`osch`/`tsch`, reusing `formats/*` deep UDM; Kotlin tests per format
  against an `examples/<fmt>` file; run the daemon `gradlew` suite.
- **Phase 3 — consumers:** MCP migration; IDE `UTLXService.parseSchema` + coverage/abstract
  delegation (async); retire browser parsers; re-verify `examples/mcm`.

## Acceptance Criteria

- `/api/parse-schema` returns a **deep** `TypeNode` for all six formats, required/nullable
  preserved (verified per format, incl. nested objects + arrays of objects).
- IDE MCM coverage works for **all six** formats including **avro/proto**, and the existing
  `examples/mcm` deltas still hold (or improve) via the delegated path.
- The browser `schema-field-tree-parser.ts` is no longer the coverage source of truth.
- **utlxe and CLI are unchanged** — no file under `modules/engine`, `modules/cli`,
  `modules/analysis`, or `formats/*` is modified (only read/reused).
- No MCP tool regresses.

## Testing

- **Kotlin (daemon):** per-format `/api/parse-schema` → deep `TypeNode` (nested object +
  array-of-objects + required) for jsch/xsd/osch/tsch/avro/proto; round-trip a known schema
  from `examples/<fmt>`.
- **IDE:** re-run the `examples/mcm` coverage verification through the delegated path; the
  10/11 mixed-format scenarios must reproduce their documented deltas.
- **Regression:** daemon `gradlew test`; MCP smoke; confirm utlxe/CLI suites untouched.

## Related

- **IF13** (utlxd schema *inference*, data → schema) — the symmetric sibling; same
  IDE-stack scope, same utlxe/CLI-untouched isolation, same browser-duplication removal.
- **IF11** (MCM AI assist) — the primary consumer; resolves its avro/proto coverage gap.
- **IF10** (input abstract) — can delegate too, for consistent structure.
- **IB03** — the browser↔engine divergence class this removes by retiring browser parsers.
- **EF02** (engine schema validation) — engine-side schema work; *contrast*: IF12 is
  utlxd/IDE-stack and does not touch utlxe.
- **The one EF-adjacent thing (out of scope):** deepening the shallow USDL `%types` summary
  inside `formats/*` would improve utlxe's USDL output but **touches shared modules** → a
  separate EF, explicitly excluded here.

## Effort Estimate

Large, phased. Phase 1 (recursive serializer + tests) small. Phase 2 (four adapters + tests)
is the bulk. Phase 3 (MCP + IDE async delegation + re-verify) medium. Gate each phase on the
prior; keep utlxe/CLI untouched throughout.
