# EB20 — `validate.*` never compiles, and the version gate silently depended on that

**Status:** Partly fixed — 2026-11-17. The version-gate half is fixed; the compilation half was
attempted, found unsafe, and reverted with the reason recorded in the code.
**Priority:** Medium — a latent fail-open gate (now closed) plus a permanent performance
characteristic of the flagship `%utlx 1.1` feature
**Component:** `:modules:engine` — `strategy/ExecutionStrategy.kt`,
`strategy/compiled/ASTCompiler.kt`; with a helper added to `:core`
(`semantic/LanguageNamespaces.kt`)
**Found:** 2026-11-17, reading the engine log of the first functional test run against Azure

## Summary

Two coupled findings, discovered together.

**1. A namespaced call cannot be compiled.** `validate.assert(...)` is a `FunctionCall` whose
`function` is a **MemberAccess**, not an `Identifier`, and `ASTCompiler.isCompilable` accepted
only `Identifier`:

```kotlin
is Expression.FunctionCall -> {
    val fn = expr.function
    fn is Expression.Identifier && expr.arguments.all { isCompilable(it, false) }
}
```

So **every `%utlx 1.1` script falls back to the interpreter** on COMPILED — the strategy UTLXe
runs by default in production. The answers are right and the fallback is logged at INFO, so
nothing surfaces it.

**2. The namespace version gate depended on that fallback.**
`LanguageVersionStaticGate.check()` — which enforces that `validate.*` requires `%utlx 1.1` —
was called from exactly one place, `interpreter.kt:281`. It held in practice only because no
`validate.*` call could ever be compiled, so every script using one fell back to the
interpreter that enforced it.

The gate's enforcement was therefore a **side effect of an unrelated limitation in the
compiler**. Lifting the limitation alone would have silently reopened
[EB15](EB15-version-gate-not-enforced-on-the-compiled-strategies.md) on the default strategy.

## How it was found

Not by review, and not by any test. The Admin UI's log view, read after the first functional
suite ran against a live Azure deployment, showed the strategy decision per transformation:

```
apitest-a  json->json  Transformation compiled to JVM bytecode — direct execution enabled
apitest-b  xml->json   Transformation compiled to JVM bytecode — direct execution enabled
apitest-c  arrays      Transformation uses unsupported AST nodes — falling back to interpreter
apitest-d  json->csv   Transformation compiled to JVM bytecode — direct execution enabled
apitest-e  validate    Transformation uses unsupported AST nodes — falling back to interpreter
```

`apitest-c` is EB16 working as designed — the wildcard selector is refused deliberately.
`apitest-e` was not designed, and led here.

## Fixed: the gate now holds on every strategy

`LanguageVersionStaticGate.check()` moved into `requireSupportedLanguageVersion`, which
TEMPLATE, COPY and COMPILED all already call:

```kotlin
com.glomidco.utlx.core.semantic.LanguageVersionStaticGate
    .check(program, program.header.version)
```

The gate no longer depends on what the compiler happens to support. `EB20NamespacedCallTest`
asserts a `%utlx 1.0` script calling `validate.*` is rejected on all three strategies.

## Attempted and reverted: compiling namespaced calls

The change looked clean. Registry keys are already dotted (`"validate.assert"`), so
`StdlibDispatch.call` resolves them unchanged; `isCompilable` was taught to accept a
`MemberAccess` on a declared namespace; `compileFunctionCall` built the dotted name and
refused when a local binding shadowed the namespace.

It also exposed a **second, dormant defect** in `compilePipe`, which prepended the piped source
only for an `Identifier` callee:

```kotlin
if (fn is Expression.Identifier) {
    val allArgs = listOf(expr.source) + target.arguments   // func(source, ...args)
    ...
}
// Fallback: evaluate source, DISCARD, evaluate target
```

A namespaced callee fell to the discard path, so `$input |> validate.assert([rules])` called
`validate.assert([rules])` with the **rules array as its payload**:

```json
{"valid":true,"passed":0,"failed":0,"issues":[],
 "payload":[{"rule":"amount-range","inRange":{...}}]}
```

Unreachable while namespaced calls never compiled; making them compilable turned a dormant
wrong answer into a live one. That part is fixed and kept — `compilePipe` now desugars a
namespaced callee correctly, which is right whether or not anything reaches it.

**But then six `ConformanceStrategyParityTest` fixtures failed**, all under
`language/validate/`, with COPY and COMPILED disagreeing with TEMPLATE:

```
assert_declarative_rule_kinds      assert_every_per_element
assert_lambda_missing_field        assert_lambda_no_short_circuit
assert_when_guard_non_evaluable    assert_when_lambda
```

The compiled path does not reproduce the interpreter's semantics for rules carrying lambdas,
`when` guards or per-element `every` — including how a non-evaluable check reports
`"could not be evaluated"` rather than failing outright.

So the change was **reverted**. A validation feature exists to be trusted; a wrong verdict is
far worse than a slow one, which is the same judgement EB12 and EB16 made. The reason is
recorded in `isCompilable` itself, where the next person will look.

## What a real fix requires

1. Make the compiled path reproduce lambda, `when`-guard and `every` semantics inside validate
   rules — including the non-evaluable reporting paths — rather than only the declarative kinds.
2. Keep `ConformanceStrategyParityTest` green across every `language/validate/` fixture; those
   six are the acceptance criteria.
3. Only then re-enable the `isCompilable` branch. `compilePipe` is already correct for it.

Until that is done, `%utlx 1.1` validation runs interpreted on every strategy. That is a
performance characteristic, not a correctness problem — but it should be a recorded decision
rather than an accident, which is what this report makes it.

## Verified

- `:modules:engine:test` — **1025 tests, 0 failures**, including `ConformanceStrategyParityTest`
- `:modules:core:test` — passes
- `EB20NamespacedCallTest` — all three strategies agree on valid and invalid verdicts, and all
  three reject a `%utlx 1.0` script using `validate.*`
