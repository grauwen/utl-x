# IB02: IDE — `/api/execute` binds the single input to a hardcoded `"input"`, breaking custom-named inputs

**Status:** Open
**Priority:** High (breaks AI-assist generation **and** the IDE Execute button for any custom-named input)
**Created:** June 2026
**Component:** `utlxd` daemon REST API (`/api/execute`) — surfaced via the IDE. **Not** CLI core, **not** the `utlxe` engine.

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
- **The IDE Execute button** — directly fails for any custom-named single input.

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

| Path | Named-input binding | Custom-named single input |
|---|---|---|
| **`utlxe` engine (Azure)** | ✓ envelope split → named inputs (`CompiledStrategy`/`CopyStrategy`/`TemplateStrategy`; `TransformationRegistry` name→validator) | ✓ works |
| **CLI (`utlx`)** | ✓ `--input name=file` keyed by real names (`TransformCommand`) | ✓ works (bare stdin shorthand = default `$input`, by design) |
| **daemon `/api/execute` (IDE)** | ✗ only hardcoded `"input"`, no named path | ✗ **breaks** ← this bug |

So this is **not** a core engine/CLI defect — both bind named inputs correctly. It is
isolated to the daemon REST endpoint that the IDE consumes. (Same class as **IB01**:
utlxd 500 where CLI/engine succeed.)

## Proposed fix (daemon-only)

Bring `/api/execute` in line with the engine and CLI, entirely within
`RestApiServer.kt` — do **not** touch the shared `TransformationService`, the CLI, or
the engine:

- **Minimal:** bind the single input to the program's **declared** input name (from
  the parsed header's first input) instead of the literal `"input"`.
- **Better (and matches the IDE's multi-input panel):** extend `ExecutionRequest` to
  accept **named inputs** (a `name → data` map) and bind them as-is; keep the single
  `input` field as a back-compat default that maps to the declared name.

Either keeps the fix in the daemon REST layer.

## Acceptance

- A transformation with a custom-named single input (`input invoice json` → `$invoice`)
  executes successfully via `/api/execute`.
- AI-assist generation for a custom-named input converges fast (validate **and**
  execute pass) instead of looping to the turn cap.
- Default-named (`input`) transformations continue to work unchanged.
- No change to `utlxe`, the CLI, or the shared `TransformationService`.

## Related

- **IB01** — same class (utlxd 500 where CLI/engine work; root cause in daemon).
- Exposed by the IDE input-panel filename-auto-naming (IF09-era input persistence work).
- Multi-input support: the IDE input panel already supports N named inputs; the
  "better" fix above also unblocks executing multi-input transformations via the daemon.
