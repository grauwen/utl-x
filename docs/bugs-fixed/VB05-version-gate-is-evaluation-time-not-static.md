# VB05: the `%utlx` namespace gate was evaluation-time, so a 1.0 script could carry 1.1 calls undetected

> **VB = Validation bug** (`%utlx 1.1` / `validate.*`), after
> [VB01](VB01-validate-assert-raw-check-crashes-on-missing-or-mistyped-data.md),
> [VB02](VB02-validate-assert-lambda-when-guard-silently-ignored.md),
> [VB03](VB03-validate-oneOf-numeric-membership-fails.md) and
> [VB04](VB04-named-validators-fail-open-on-malformed-arguments.md). This is **VB05**.

**Status:** **FIXED (2026-10-24, pending commit)** — the gate is now also enforced **statically**, once
per program in `Interpreter.execute`, before any data is touched. The runtime gate remains as a
backstop. See §Implementation.
**Priority:** **Medium-High** — not a wrong answer, a *late* one. A `%utlx 1.0` script using
`validate.*` on a rarely-taken path passed testing and failed in production on the first payload to
reach the call.
**Component:** `:core` — `Interpreter.tryLoadStdlibFunction` (where the gate lived) and
`Interpreter.execute` (where it now also runs).

> **One-line:** whether a `%utlx 1.0` script was a legal 1.0 script depended on its **input**.

## How it was found

By question, not by test. Asked what happens if someone writes `%utlx 1.0` and calls `validate.*`,
the answer was "it throws, exit 1, with a clear message" — true, and correct by design: a version
violation is a *program* error, the same category as a syntax error, not a verdict about a payload
(contrast [VB04](VB04-named-validators-fail-open-on-malformed-arguments.md), which is a verdict and
therefore an issue rather than a throw). Checking *when* it throws is what exposed VB05.

## Evidence (via the built CLI, before the fix)

```utlx
%utlx 1.0
input json
output json
---
if ($input.orderId > 1000) ($input |> validate.required(["orderId"]))
else { status: "no validation needed" }
```

| Input | Result |
|---|---|
| `{"orderId": 42}` | ✅ `{"status":"no validation needed"}` — **exit 0**, gate never fires |
| `{"orderId": 5000}` | ❌ `Error: validate.required requires %utlx 1.1 …` — exit 1 |

Same script. Two different answers to "is this a legal 1.0 program?", decided by the payload.

## Root cause

The gate was called from the lazy stdlib resolution path:

```kotlin
// Interpreter.tryLoadStdlibFunction (interpreter.kt:1248)
enforceLanguageVersion(functionName, location)
```

That is the natural choke point for *resolution*, and F21's note describes the gate as fired "at name
resolution" — which reads as static. But resolution here is **dynamic**: it happens the first time a
call is actually evaluated. A call on an untaken branch, in a lambda that is never applied, or in a
catch block that is never entered is never resolved, so it was never gated.

So the placement was an implementation convenience that quietly changed the gate's meaning from "this
program uses a 1.1 feature" to "this execution used a 1.1 feature".

## Fix

A single static walk of the body, before any data is processed:

```kotlin
// Interpreter.execute, immediately after the header/mode checks
LanguageVersionStaticGate.check(program, declaredLanguageVersion)
```

`core/semantic/LanguageVersionStaticGate.kt` walks every `Expression` once and rejects the program on
the first namespaced call the declared version does not allow, reusing `LanguageNamespaces` so the
rule and the message are identical to the runtime gate. The runtime gate is **kept** as a backstop for
anything that reaches the interpreter by another route.

Two design points worth knowing:

- **The `when` over the AST is deliberately exhaustive and assigned to a value** (`val unit: Unit =
  when (expr) { … }`). Kotlin does not require exhaustiveness for a `when` used as a statement, so
  assigning it means that **adding a node type to the AST breaks this file at compile time** rather
  than silently opening a hole in the gate.
- **Shadowing is tracked.** A namespace is only a namespace when its base identifier is not a bound
  variable — the runtime path depends on the base being undefined, so `let validate = {…} in
  validate.required` is an ordinary member access and must stay legal under 1.0. The walk threads a
  set of bound names through `let`, object-literal `let` bindings, lambda parameters, `try/catch`
  error variables and match variables, and skips a call whose base is shadowed.

### Behaviour change to be aware of

A script declaring `%utlx 1.0` with a `validate.*` call in dead code **used to run** and is now
rejected. That is the intended semantics — it was never a legal 1.0 script — but it is a tightening
that could reject a deployment that happens to work today. The failure is loud and the message names
the call, so the fix is either to declare `%utlx 1.1` or to remove the dead call.

## Implementation (2026-10-24)

- **new** `:core` `semantic/LanguageVersionStaticGate.kt` — the exhaustive, scope-aware walk.
- **`:core` `interpreter.kt`** — one call in `execute`, after `enforceSupportedVersion`. The runtime
  gate in `tryLoadStdlibFunction` is unchanged.
- No parser, stdlib or engine change. All strategies are covered because a namespaced call is not
  compilable (`ASTCompiler.isCompilable` rejects it), so any script containing `validate.*` runs
  through the interpreter.

## Performance — the gate must not ride the hot path

`Interpreter.execute` runs **per payload**: `ExecutionStrategy.executeBatch` is literally
`inputs.map { execute(it) }`. A naive static walk there would re-traverse the entire AST for every
record in a batch — the gate is a property of the *program*, so paying for it per record is pure
waste. Two mitigations, both exact:

1. **Version fast path** (in the gate). `highestGatedVersion` is derived from
   `LanguageNamespaces.NAMESPACE_MIN_VERSION`; a script declaring at least that version cannot reach
   a namespace it is not allowed to use, so the walk is skipped outright. Today that means every
   `%utlx 1.1` script — the forward-looking common case — pays nothing. It is self-maintaining: when
   a `2.0` namespace is registered, `highestGatedVersion` becomes `2.0` and 1.1 scripts resume
   walking automatically.
2. **Per-program memo** (in the interpreter). `gatedProgram !== program` — identity compare — so a
   given program is walked once rather than once per payload.

Measured on a deliberately oversized script (400 properties, each a nested `map` with a lambda and
arithmetic — far larger than a typical mapping):

| | Cost |
|---|---|
| `%utlx 1.0`, full walk | **27 µs** |
| `%utlx 1.1`, version fast path | **206 ns** |
| parse + lex of the same script, for scale | **1161 µs** |

So even the unmitigated worst case is ~2% of the cost of parsing that script once, and ~43× cheaper
than a single parse. With the memo it is paid once per program, not per record. The conclusion is
that this is not a throughput concern — but it would have been a measurable one on large batches
without the memo, which is why the memo is there rather than left as an optimisation for later.

## Tests

- `conformance .../validate/version_gate_untaken_branch.yaml` — the untaken-branch script is rejected. ✅
- `conformance .../validate/version_gate_shadowed_namespace.yaml` — a `let`-bound `validate` under
  `%utlx 1.0` still evaluates, guarding against over-reach. ✅
- `VB05StaticVersionGateTest` (`:core`, 9 tests) — four cases the runtime gate cannot see (untaken
  branch, never-entered catch, a lambda that never runs via `map([], …)`, an uncalled function), the
  plainly-reached case, a 1.1 control, and three shadowing guards (`let`, lambda parameter,
  object-literal `let`).
  Verified to be a true regression guard: with the static call removed, **4 of the 9 fail** — exactly
  the four unreachable-call cases — and the other 5 pass, as they must.

  A note on building those four: the first drafts used `map([1, 2], x => …)` and a `let` whose *value*
  was the call. Both pass with **and** without the fix, because a non-empty `map` does run its lambda
  and `let` values are evaluated eagerly — so they were coverage, not regression tests. They were
  rewritten to `map([], …)` and an uncalled lambda, which the runtime gate genuinely cannot reach.
- Full conformance **543/543** (`language/validate` 14/14) on JVM and the rebuilt native binary.

## Related

- [VB04](VB04-named-validators-fail-open-on-malformed-arguments.md) — the companion distinction: a
  malformed *rule* is a verdict about a payload (an error **issue**, fail-closed), while a version
  violation is a claim about the *program* (a **throw**). VB04 fixed the direction of one; VB05 fixed
  the timing of the other.
- `ValidateAcrossStrategiesTest` (`:engine`) locks the gate across strategies, but exercises the taken
  path — so this hole sat just outside its coverage.
