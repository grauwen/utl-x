# B24: Core — `$input` silently aliased to first input, masking a declared-but-empty input (preview ≠ engine)

**Status:** Open — proposed fix below (not yet implemented)
**Priority:** High — silently routes the *wrong* input, and the IDE/daemon preview disagrees with the production engine (a transformation can pass in preview and fail in production)
**Created:** June 2026
**Related:** IB02 (IDE execute hardcoded input name), EF01 (multi-input per stage); engine strictness in `CompiledStrategy`/`CopyStrategy`

---

## Problem

A transformation that declares **two** inputs — the first literally named `input` (left empty),
the second `enterprise-order` (carrying the data) — and references **`$input`** throughout the body
runs **successfully in the IDE/daemon preview**, with `$input` resolving to the *enterprise-order*
data. The empty `input` is silently ignored; `$input` "takes the first available valid input."

```
%utlx 1.0
input: input json, enterprise-order json     # 'input' empty, 'enterprise-order' has the data
output yaml
---
{ orderId: $input.orderId, ... }             # references $input — silently gets enterprise-order
```

This is surprising and unsafe: the reference resolves to a *different* input than the one named
`input`, with no warning.

## Root cause (core)

`modules/core/.../interpreter/interpreter.kt:215-226`:

```kotlin
// Bind all named inputs to environment
namedInputs.forEach { (name, data) -> env.define(name, RuntimeValue.UDMValue(data)) }

// Backward compatibility: if no "input" was provided, use first input
if (!namedInputs.containsKey("input") && namedInputs.isNotEmpty()) {
    val firstInput = namedInputs.values.first()
    env.define("input", RuntimeValue.UDMValue(firstInput))   // ← silent substitution
}
```

- **Empty inputs are dropped upstream** (the daemon/IDE builds `namedInputs` from non-empty
  payloads), so a declared-but-empty `input` is **absent** from the runtime map.
- The fallback keys off the **runtime map**, not the **declared header** (even though the header is
  available — it's used at `:202` for metadata). So `!namedInputs.containsKey("input")` is `true`,
  and `$input` is aliased to `namedInputs.values.first()` = the enterprise-order data.

The fallback cannot distinguish *"no input was ever named `input`"* (alias is helpful) from
*"an input named `input` was explicitly declared but is empty/missing"* (alias is wrong).

## The core ↔ engine divergence (why this is worse than a quirk)

The **production engine** binds inputs **strictly by declared name** and has **no fallback** —
`CompiledStrategy.executeMultiInput` (and `CopyStrategy`):

```kotlin
val inputUDMs = declaredInputs.associate { (name, formatSpec) ->
    val node = envelope.get(name)
        ?: throw IllegalArgumentException("Envelope missing required input '$name'. Expected keys: …")
    …
}
```

So with the same transformation:

| Path | Behavior with empty `input` slot |
|---|---|
| **core interpreter** (daemon / IDE preview) | **silently substitutes** `$input` = enterprise-order → "works" |
| **engine** (`CompiledStrategy`, production) | **throws** `Envelope missing required input 'input'` |

→ A transformation can **pass in the IDE and fail in production** — a dev/prod parity break, caused
by core's lenient fallback masking what the engine treats as an error.

## Reproduction

1. Declare two inputs; name the first `input`, leave it empty; put data in the second.
2. Reference `$input` in the body.
3. Run via the IDE/daemon (`/api/execute`) → succeeds, `$input` = the second input's data.
4. Deploy the same bundle to the engine and send an envelope without `input` → throws
   `Envelope missing required input 'input'`.

## Impact

- **Silent wrong-input routing** — `$input` resolves to an unintended input; behavior depends on
  *which* slot happens to be populated.
- **Preview ≠ production** — masks an error the engine raises.

## Proposed fix (core — make the fallback header-aware **and** count-aware)

The alias must consult `program.header.inputs`, not just the runtime map. Rule:

> Alias `$input` to the first input **only when the header does not declare an input named
> `input`** — and only when there is exactly **one** declared input. Keep the single-input
> convenience; never silently substitute otherwise.

```kotlin
val declaresInput = program.header.inputs.any { (name, _) -> name == "input" }

if (declaresInput) {
    // $input IS the declared input (bound by name in the forEach above).
    // If it is empty/missing at runtime → leave $input unbound → null / clear error.
    // NEVER substitute another input. (Matches the engine's "missing required input".)
} else if (program.header.inputs.size == 1) {
    // No input named "input", exactly one input → alias $input to it (convenience kept).
    namedInputs.values.firstOrNull()?.let { env.define("input", RuntimeValue.UDMValue(it)) }
} else {
    // No input named "input", and MORE THAN ONE input → do NOT alias.
    // $input is ambiguous; referencing it should error: "use $<name> (e.g. $enterpriseOrder)".
}
```

Summary of the rule (per the three cases):
1. **Header declares `input`** → `$input` = that declared input; if empty/missing, surface it
   (null/error) — no substitution.
2. **No `input` declared, exactly one input** → alias `$input` to it (existing convenience stays).
3. **No `input` declared, >1 inputs** → no alias; `$input` is ambiguous → error.

This kills the silent mis-bind, keeps the single-input convenience, and makes **core match the
engine** (preview == production).

## Must NOT break — preserved behavior (regression guards)

**The basic `$input` default must keep working** — it is the backbone of scripting, piped use, and
`-e` evaluations. The fix is safe for these **because they never go through the fallback**: they
supply an input under the literal map key `"input"`, so `$input` is bound by the **forEach**
(`interpreter.kt:218`), which is **unchanged**. The fix only edits the fallback branch (the
*no-`"input"`-key* case).

Paths that MUST continue to work (all bind `"input"` directly → unaffected):
- **Single-input pipe / CLI** — `utlx tx.utlx < data.json` → `execute(program, mapOf("input" to inputData))` (`interpreter.kt:192`). `$input` = the input.
- **`-e` / `--expression` inline evaluation** — `echo … | utlx -e '$input.field'` → `mapOf("input" to InputData(…))` (`TransformCommand.kt:248/256`). `$input` = the input.
- **REPL / `evaluate` expressions** — same single-`"input"` binding.

Path that relies on the fallback and must ALSO keep working (covered by case 2):
- **Single input declared under a *different* name** (e.g. `input order json`) where the body uses
  `$input` → no `"input"` key in the map → fallback aliases `$input` to the one input. **Keep this.**

Only behavior that changes:
- **Multiple inputs, none named `input`, body uses `$input`** → was silently aliased to the first;
  now an **error** (ambiguous — use `$<name>`).
- **An input *named* `input` that is empty/missing** → was silently replaced by another input; now
  `$input` is null / a clear error (matching the engine).

**Regression tests to add/keep green:**
1. `utlx tx.utlx < data.json` with `$input.x` → resolves (single-input default).
2. `echo '{"a":1}' | utlx -e '$input.a'` → `1` (eval default).
3. Header `input order json`, body `$input.x`, one input → resolves (case 2 alias).
4. Header `input order json, customer json`, body `$input` → **error** (case 3 ambiguous).
5. Header with `input` declared but empty + another input with data, body `$input` → **null/error**,
   *not* the other input (the B24 case), matching the engine.

## Workaround (until fixed)

Don't rely on the alias with multiple inputs. Either declare a single input named `input` and feed
the data there, or reference each input by its declared name (use identifier-safe names, e.g.
`enterpriseOrder`, not `enterprise-order`).

## Code pointers
- Core: `modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt:215-226`
  (the fallback) and `:202` (header already in scope).
- Engine (strict, for parity): `modules/engine/.../strategy/CompiledStrategy.kt:130-175`,
  `CopyStrategy.kt`.
- Trigger (upstream): empty inputs dropped when building `namedInputs` (daemon `/api/execute` / IDE).
