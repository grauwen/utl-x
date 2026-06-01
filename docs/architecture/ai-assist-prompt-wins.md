# AI Assist — Prompt Wins (living log)

A running record of **prompt-level** improvements to the UTL-X AI assist (the MCP
`generate_utlx_from_prompt` path). These are steers in the system/user prompt that make
the model produce better UTL-X without engine changes. Record each win here so we don't
re-learn the same lessons.

**Where the prompt lives:**
- `mcp-server/src/llm/prompts/execution-prompt.ts` — system prompt + user-prompt builder
  (Execution mode). The high-signal **COMMON PATTERNS** block sits right before the user
  request; the **UTLX LANGUAGE REFERENCE** (full spec) is embedded from the next file.
- `mcp-server/src/llm/prompts/utlx-language-reference.md` — the language spec embedded
  into the system prompt. (Must be present in `dist/` — see the "language reference
  loading" note below; the build copies it via `npm run copy-assets`.)
- `mcp-server/src/llm/prompts/message-contract-prompt.ts` — Message Contract mode (stub, IF08 v2).

Related: IF08 (mode-aware AI assist), IB02 (custom-named input execute).

---

## How to add a win

When you observe the AI producing bad/clumsy UTL-X:
1. Confirm the language *supports* the better form (check the lexer/stdlib — often it
   already does; the model just isn't using it).
2. Add a steer to **COMMON PATTERNS** (highest signal) and/or the language reference.
3. Append an entry below: **symptom → steer → where**.

---

## Wins

### 1. `mapGroups`, not `groupBy |> entries`
- **Symptom:** model wrote `groupBy(...) |> entries() |> map(e => e.key)` → runtime error
  "Cannot access property 'key' on ArrayValue" (`groupBy` returns an Object; `entries`
  yields `[key,value]` arrays).
- **Steer:** COMMON PATTERNS — use `mapGroups(item => key, group => { group.key … group.value })`.
- **Where:** `execution-prompt.ts` COMMON PATTERNS.

### 2. Wrap a single object before grouping/iterating
- **Symptom:** `mapGroups`/`map` over a single object failed (needs an array).
- **Steer:** `[$input] |> mapGroups(...)`.
- **Where:** `execution-prompt.ts` COMMON PATTERNS.

### 3. Copy fields with spread — don't enumerate 1:1
- **Symptom:** model copied objects field-by-field (`a: item.a, b: item.b, …`) — verbose,
  brittle, more tokens to generate/validate. (UTL-X already has spread + `pick`/`omit`.)
- **Steer:** use `{ ...item }` to copy (with `{ ...item, k: v }` to override); reference an
  identical collection directly (`lineItems: $order.lineItems`, no `map`); `pick`/`omit`
  to select/drop.
- **Where:** `execution-prompt.ts` COMMON PATTERNS + `utlx-language-reference.md` Spread section.

### 4. Output-format structural guidance
- **Symptom:** outputs malformed per target format (e.g. CSV needs a flat array of flat
  rows; OData/XML have shape rules).
- **Steer:** `buildOutputFormatGuidance(outputFormat)` injects format-specific rules
  (csv/xml/json/yaml/odata) into the user prompt before generation.
- **Where:** `execution-prompt.ts` (`buildOutputFormatGuidance`, "structural requirements").

### 5. Format for humans (tidy output, no one-liners)
- **Symptom:** correct but unreadable one-liner output; missing brackets.
- **Steer:** explicit "FORMAT FOR HUMANS" rule (multi-line, balanced brackets).
- **Where:** `execution-prompt.ts` system prompt.

### 6. USDL directives for Tier-2 schema formats
- **Symptom:** model didn't know the `%`-directive vocabulary when a Tier-2 schema format
  (xsd/jsch/avro/proto/osch/tsch) was involved.
- **Steer:** `buildUsdlContext(...)` adds a "USDL Schema Directives" section only when a
  Tier-2 format is an input/output.
- **Where:** `generateUtlx.ts` (`buildUsdlContext`) → user prompt; `llm/usdl-context.ts`.

---

## Context notes (not prompt steers, but they gate prompt quality)

- **Language reference must reach `dist/`.** The full UTL-X spec is embedded into the
  system prompt from `utlx-language-reference.md`. If it's missing from `dist/`, the
  system prompt silently degrades to "reference not available" and the model generates
  bad UTL-X → fails validation → loops to the turn cap. The build now copies it
  (`npm run copy-assets`) and the path resolver also finds it in `src/`. (Discovered while
  debugging "AI doesn't work / max turns".)
- **`maxTurns`** for the agentic Claude Code session is 12 (was 8 → too tight). The agent
  generates → `validate_and_run` → fix; too few turns + a weak prompt = exhaustion. Better
  prompts (the wins above) reduce turns needed → faster, less Max usage.
