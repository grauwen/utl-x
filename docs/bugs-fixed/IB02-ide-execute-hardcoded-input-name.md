# IB02: IDE — `/api/execute` binds the single input to a hardcoded `"input"`, breaking custom-named inputs

**Status:** Open
**Priority:** High (breaks AI-assist generation for custom-named / multi-input transformations)
**Created:** June 2026
**Component:** MCP server's use of the daemon's JSON `/api/execute` endpoint. **Not** the IDE Execute button (which uses `/api/execute-multipart` and works), **not** CLI core, **not** the `utlxe` engine.

> **Scope correction (after investigation):** the daemon has **two** execute
> endpoints. The IDE Execute button already uses `/api/execute-multipart`, which
> binds each input **by its real name** and supports N inputs — so it is **not**
> affected. Only the **MCP/AI-assist** path is broken, because it calls the older
> JSON `/api/execute`, which carries a single `input` and binds it to the literal
> `"input"`. The fix is to point the MCP at the multipart endpoint — no daemon change.

---

## Problem

When a transformation declares an input under a **custom name** (anything other than
the default `input`), running it via the IDE fails at execution with:

```
HTTP 500
RuntimeError: Undefined variable: <name>
```

e.g. for an input named `invoice`, `$invoice` is "undefined" at runtime even though
the program is valid and parses cleanly. This breaks:

- **AI assist** — the agentic generator validates (OK) then *executes* its candidate
  to self-correct; every execute returns 500, so the agent can't converge, loops to
  the turn cap, runs slow, and burns LLM usage with no result.

The **IDE Execute button is NOT affected** — it uses a different endpoint
(`/api/execute-multipart`) that binds inputs by name (see Scope).

## Reproduction

```utlx
%utlx 1.0
input invoice json
output json
---
$invoice
```

POST to the daemon with input data:

```
POST /api/execute  { "utlx": "<above>", "input": "{...}", "inputFormat": "json", "outputFormat": "json" }
→ 500  RuntimeError: Undefined variable: invoice
```

The identical transformation with the **default** name works:

```utlx
input json
---
$input          → 200 OK
```

## Root cause

`modules/daemon/src/main/kotlin/org/apache/utlx/daemon/rest/RestApiServer.kt`
(`/api/execute`, ~line 389) hardcodes the input name:

```kotlin
val (outputData, outputFormat) = transformationService.transform(
    utlxSource = completeUtlx,
    inputs = mapOf("input" to TransformationService.InputData(   // ← hardcoded "input"
        content = request.input,
        format = request.inputFormat
    )),
    ...
)
```

The engine binds inputs by the **map key as the variable name**. The program declares
`input invoice json`, so the body references `$invoice` — but the endpoint binds the
data under `"input"`, leaving `$invoice` unbound → `RuntimeError: Undefined variable`.

The `/api/execute` endpoint has **only** this single-input, hardcoded-name path — it
has no named-input alternative.

## Why it was not seen before

The IDE used to default every input's name to `input`, so `$input` matched the
hardcoded `"input"` binding. The bug was masked. It surfaced once the IDE began
**auto-naming inputs from the loaded filename** (`invoice.json` → `invoice`,
`07-shipping-manifest.json` → `shipping-manifest`) — a feature added in the
input-panel work (IF09-era). Now the single input has a custom name and the binding
mismatches.

## Scope — what is and isn't affected

| Path | Named-input binding | Custom name / N inputs |
|---|---|---|
| **`utlxe` engine (Azure)** | ✓ envelope split → named inputs (`CompiledStrategy`/`CopyStrategy`/`TemplateStrategy`; `TransformationRegistry` name→validator) | ✓ works |
| **CLI (`utlx`)** | ✓ `--input name=file` keyed by real names (`TransformCommand`) | ✓ works (bare stdin shorthand = default `$input`, by design) |
| **IDE Execute button** → daemon `/api/execute-multipart` | ✓ each part bound by `metadata.name` (`RestApiServer.kt:495-503`) | ✓ works (custom names **and** N inputs) |
| **AI assist (MCP)** → daemon `/api/execute` (JSON) | ✗ single `input`, hardcoded `"input"` | ✗ **breaks** ← this bug |

So this is **not** a core engine/CLI defect (both bind named inputs), and **not** an
IDE-Execute defect (it uses the multipart endpoint, which binds by name and supports
N inputs). It is isolated to the **MCP's choice of the JSON `/api/execute` endpoint**.
(Same class as **IB01**: utlxd 500 where CLI/engine succeed.)

### What happens with N inputs

- **IDE Execute button:** N inputs **work** — each is sent as a named multipart file
  part and bound by name.
- **AI assist (MCP):** N inputs **do not work** — the MCP both (a) calls the
  single-`input` JSON endpoint and (b) only ever sends **one** sample input
  (`generateUtlx` picks `inputs.find(i => i.originalData…)`). So for a multi-input
  transformation the AI's execute-verification can bind at most one input (under the
  wrong name), every other `$name` is undefined, and it 500s/loops.

### Does the `utlxe` Azure HTTP offering expose this? — No.

`utlxe` (the production engine, `modules/engine`) and `utlxd` (the daemon,
`modules/daemon`) are **separate products with separate HTTP APIs**. The buggy
`/api/execute` lives only on the **daemon** (dev/IDE/LSP tooling). The engine's HTTP
transport (`HttpTransport.kt`) exposes a **different** surface — there is no bare
`/api/execute`:

- `/api/transform`, `/api/load`
- `/api/execute/{id}`, `/api/execute-batch/{id}`, `/api/execute-pipeline`
- Dapr / pub-sub bindings: `/{bindingName}`, `/pubsub/{name}`

These are **id-based** (execute an already-*loaded* transformation) and route through
`TransportHandlers.handleExecute(...)` → the engine **strategies**, which **split the
message envelope into named inputs** bound to the transformation's configured input
slots (`TransformConfig.inputs`, `TransformationRegistry` name→validator). Binding is
by **declared name**, never a hardcoded `"input"`.

**Therefore the Azure offering is not affected by IB02.** This matches the deliberate
daemon-vs-engine split: the daemon's REST API is dev/IDE tooling; the engine's
HTTP/Dapr transport is the production surface. IB02 is entirely daemon-side.

## Proposed fix (MCP-side — no daemon change)

The daemon **already** has the correct endpoint: `/api/execute-multipart` binds each
input by its real name and supports N inputs (it's what the IDE Execute button uses).
The MCP just calls the wrong one. So the fix lives entirely in the **MCP server**:

- Point the MCP's `DaemonClient.execute` at **`/api/execute-multipart`** instead of
  the JSON `/api/execute`, sending the input(s) as named parts (name + format +
  content) — exactly like the IDE's node daemon-client does.
- In `generateUtlx`, send **all** inputs that carry sample data (not just the first),
  each under its real name — so the agent's execute-verification works for multi-input
  transformations too.

This requires **no** change to the daemon, the engine, the CLI, or the shared
`TransformationService`, and unblocks AI assist immediately.

### Secondary fix (recommended, separate) — make `/api/execute` correct too

`/api/execute` is a **documented public endpoint** (OpenAPI `docs/api/openapi.yaml`,
`docs/api/README.md`, curl examples in the daemon REST guide), so it shouldn't 500 on
a valid single-input program just because the input has a non-default name. As a
**separate, daemon-local** change (requires a daemon rebuild): in `RestApiServer.kt`'s
`/api/execute` handler, bind the single input to the program's **declared** input name
(parse the header for the first input name, as the validate path already parses)
instead of the literal `"input"`. Scope is honestly single-input — N inputs remain the
job of `/api/execute-multipart`. No engine / CLI / `TransformationService` change.

Sequencing: #1 (MCP→multipart) is higher priority and rebuild-free; #2 is API hygiene
and independent.

## Acceptance

- A transformation with a custom-named single input (`input invoice json` → `$invoice`)
  executes successfully via `/api/execute`.
- AI-assist generation for a custom-named input converges fast (validate **and**
  execute pass) instead of looping to the turn cap.
- Multi-input transformations execute in AI-assist verification (all inputs bound by name).
- Default-named (`input`) transformations continue to work unchanged.
- No change to `utlxe`, the CLI, the shared `TransformationService`, or the daemon
  (the fix is MCP-side, reusing the existing `/api/execute-multipart`).

## Related

- **IB01** — same class (utlxd 500 where CLI/engine work; root cause in daemon).
- Exposed by the IDE input-panel filename-auto-naming (IF09-era input persistence work).
- Multi-input support: the IDE input panel already supports N named inputs; the
  "better" fix above also unblocks executing multi-input transformations via the daemon.
