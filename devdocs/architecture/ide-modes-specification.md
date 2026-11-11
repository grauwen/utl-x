# IDE Modes Specification: Execution Mode vs Message Contract Mode

> **Status:** Authoritative. Canonical definition of the UTL-X **IDE modes**. The
> book *UTL-X — One Language, All Formats*, Chapter 7.2 ("Two Modes") is the prose
> source; this is the engineering spec derived from it and the implementation.
>
> **Naming:** the two IDE modes are `UTLXMode.EXECUTION` and
> `UTLXMode.MESSAGE_CONTRACT` (enum values `'execution'` / `'message-contract'`).
> They were **formerly called "Runtime" and "Design-Time"** — renamed precisely to
> stop colliding with the engine lifecycle phases (design-time / init-time /
> runtime), which are a different concept. The engine phase names are unchanged.

## TL;DR

The UTL-X **IDE** has two **modes** — a UI/workflow concept, **distinct from the
engine lifecycle phases**.

| | **Execution Mode** | **Message Contract Mode** |
|---|---|---|
| Purpose | Execute a transformation against sample data | Build a transformation against known input/output **contracts** |
| Allowed tiers | **Any** combination: T1→T1, T1→T2, T2→T1, T2→T2 | **Tier 1 → Tier 1 only** |
| Inputs | Instance data (or a schema treated *as data*) | Tier 1 **instance** docs, each with a **schema** as a structural guide |
| Output | Whatever the transform produces, serialized to the chosen **format** | A **predefined/declared output schema** (the contract) the result must satisfy |
| Schemas | Not used (Tier 2 inputs are just data) | Displayed as guides; **never passed to the engine** |
| Daemon op | `/api/execute` (run against data) | `/api/infer-schema` + compare to declared output schema |
| Verification | Runtime result / errors | Output-schema validation + completeness tracking |
| Scaffolding | n/a | Generates a skeleton with `???(type)` placeholders + TODO comments |
| USDL directives | **Yes — when a Tier 2 format is involved** | **No** |

## ⚠️ Disambiguation: IDE modes ≠ engine phases

Two unrelated concepts that historically both used "design-time/runtime":

- **Engine lifecycle phases** (`utlxe`): *design-time → init-time → runtime* —
  execution phases of the engine (compile-once, init-per-deployment,
  run-per-message). See `three-phase-runtime-design.md` and book Chapter 32.
- **IDE modes** (`utlx` IDE): **Execution Mode** and **Message Contract Mode** — a
  UI workflow concept, described here and in book Chapter 7.2.

Renaming the IDE modes removed the lexical clash; the engine phases keep their
names. Note that `docs/architecture/design-time-schema-analysis.md` describes an
**engine schema-analysis capability** (input schema → inferred output schema) —
that is **NOT** Message Contract mode. Message Contract mode is Tier 1 → Tier 1;
it merely *uses* schema inference internally to validate the result against the
declared output contract.

## Execution Mode

The default mode — a graphical overlay on the UTL-X CLI. You paste sample data,
write a `.utlx`, and the `utlxd` daemon runs the same parser/interpreter as the
CLI on every change, piping the result to the output panel (live preview).

- **Inputs** may be any tier. A Tier 2 format (XSD, JSON Schema, Avro, …) is
  perfectly valid as input — it is simply **processed as data** (schema-as-data).
- **Output** may be any tier. The transformation is constrained only by the
  output **format**; the engine does not enforce any output structure.
- Supports all four tier combinations. In particular **T1→T2, T2→T1 and T2→T2
  mappings are Execution-Mode-only** (e.g. *JSON Schema → XML Schema*).
- **USDL** directive guidance applies whenever a Tier 2 schema format is an input
  or the output — because Tier 2 data is expressed via USDL `%`-directives
  (`%types`, `%fields`, …). See `usdl-enrichment-design.md`.

## Message Contract Mode

For schema-driven, contract-first development. **Both** the input schema and the
output schema are known in advance; the job is to write the `.utlx` that bridges
them. This is how Tibco BW, SAP CPI, and MuleSoft approach mapping. The name
refers to the message-level schema contract (one input message shape → one output
message shape), not an API-level contract.

Key properties:

- **Tier 1 → Tier 1 only.** You are mapping instance documents (JSON, XML, CSV,
  YAML) while *using* schemas as structural guides.
- **Schemas are an authoring-time aid, never runtime input.** At runtime `$input`
  receives a concrete instance document, not the schema. The schemas are loaded
  (or inferred, or fetched from API docs/contracts) purely to guide the developer.
- **The output is a predefined contract.** The declared output schema is the
  target. The IDE infers the transformation's output schema and compares it to
  the declared one, flagging mismatches and **unmapped (incomplete) fields** in
  real time.
- **Scaffolding.** The IDE can generate a transformation skeleton from the output
  contract: every target field present, with `???(type)` placeholder expressions
  and TODO comments where business logic is required. (See
  `theia-extension/.../browser/utils/scaffold-generator.ts`.)
- **Use case:** contract-driven process chaining — e.g. OpenM / EAI pipelines —
  where one step's output format and the next step's input format are fixed
  contracts that must match exactly.

## AI-assisted generation per mode

Per the book §45.6 ("AI at Design-Time Only"), AI assists at **authoring time,
never in the engine's runtime hot path** — production transforms are
deterministic `.utlx`, no LLM per message. AI assist therefore applies in **both
IDE modes**, but with a **different setup** in each:

### Execution Mode (implemented)
- Sends: prompt, input(s) with instance data / UDM, output **format**, header.
- Verifies: validate **+ execute** the full program against sample input (catches
  runtime errors), then refines. See `mcp-server/src/tools/generateUtlx.ts`.
- Injects **USDL** directive context when a Tier 2 format is involved.

### Message Contract Mode (not yet implemented)
A genuinely different request shape is required. AI assist should receive:
- the **input schema(s)** and the **output (target) schema** (the contract),
- the optional input/output **instance documents**,
- the input/output **UDMs**,
- and ideally the **scaffold** (so it fills `???` placeholders rather than
  generating from scratch).

Its goal is to **satisfy the predefined output contract** (complete the scaffold),
and verification is **schema-level**: infer the output schema and check it against
the declared output schema + completeness — **not** execution, and **no USDL**.

> **Implementation gap:** the current `GenerateUtlxRequest` carries only
> `{prompt, inputs[{name,format,originalData,udm}], outputFormat, originalHeader}`
> and is mode-agnostic. The output schema, input schemas, structured UDMs, and
> scaffold are available in the IDE widget state but are **not sent**. Wiring the
> `UTLXMode` plus the Message-Contract contract data through is the prerequisite
> for Message-Contract-mode AI assist.

## Source references

- Book *UTL-X — One Language, All Formats*: Ch. 7.2 (IDE modes), Ch. 32 (engine
  lifecycle), §45.6 (AI at design-time only).
- Code: `protocol.ts#UTLXMode` (enum), `scaffold-generator.ts` (scaffold),
  `utlx-frontend-contribution.ts` (execute vs infer-schema by mode),
  `output-panel-widget.tsx#getExpectedSchema` (declared output contract),
  `mcp-server/src/tools/generateUtlx.ts` (AI generate).
- Related: `usdl-enrichment-design.md`, `theia-extension-design-with-design-time.md`.
