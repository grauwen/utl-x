# EB16: the wildcard selector `.*` silently evaluates to null on the compiled strategies

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they call), the
> family of EB01–EB15. This is **EB16**.

**Status:** **FIXED** (by refusing to compile it — see *Scope of the fix*)
**Priority:** High. A silent wrong answer, not an error.
**Component:** `modules/engine` — `ASTCompiler.isCompilable`.
**Found by:** teaching the cross-strategy conformance harness to drive the 39 named-input fixtures,
which brought `datacontract/02_servers_wildcard` into the comparison for the first time.

> **One-line:** `$obj.*` parses as a `MemberAccess` whose property is the literal string `"*"`, and
> the compiler emitted an ordinary property lookup for a key named `"*"` — which no object has.

## Measured

```
{ r: $input.servers.* }                       TEMPLATE {"r":[{...},{...}]}   COPY/COMPILED {"r":null}
{ r: $input.servers.* |> map(s => s.host) }   TEMPLATE {"r":["h1","h2"]}     COPY/COMPILED threw
```

The first line is the dangerous one. `null` is a legitimate value, so a transformation that selected
nothing simply produced nothing, and nobody downstream could tell. It only became visible as an
error when something insisted on an array:

```
map requires an array as first argument, but got null
```

That error is also why it was found at all — and it is worth noting that the *louder* symptom is the
lucky case.

## Root cause

The parser turns `.*` into a `MemberAccess` carrying `"*"` as the property name
(`parser_impl.kt`: *"Allow wildcard selector: .\* or .@\* or .^\*"*). The interpreter then
special-cases that string and gives it real semantics — collect the child values, exclude `_text`,
with a separate branch for `.@*` attributes.

`ASTCompiler.isCompilable` accepted **any** `MemberAccess`:

```kotlin
is Expression.MemberAccess -> isCompilable(expr.target, false)
```

so the wildcard was declared compilable and compiled as a plain key lookup. The property name is the
entire difference between an ordinary access and a wildcard, and the compiler never looked at it.

## Scope of the fix

```kotlin
is Expression.MemberAccess ->
    expr.property != "*" && isCompilable(expr.target, false)
```

The compiler now **refuses** the wildcard, so a transformation containing one falls back to the
interpreter in full — the same choice [EB12](EB12-compiled-try-catch-emits-unverifiable-bytecode.md)
made for try/catch. A slower right answer beats a fast wrong one, and refusal is the only option that
cannot be subtly incomplete: the interpreter's wildcard has three behaviours (child values, `_text`
exclusion, the attribute variant) and a partial reimplementation would simply move the divergence.

**Implementing the wildcard in the compiler is the follow-up**, not this fix. It costs those
transformations their bytecode path — `genuinely COMPILED` moved 363 → 362 — which is the honest
price and is recorded in the coverage floor.

The refusal is deliberately narrow, and a test pins that: an ordinary `$input.servers.a.host` still
compiles.

## Behaviour change

A transformation using `.*` now produces the same result on every strategy. Before, it produced
`null` on COPY and COMPILED. Nothing that was previously *correct* changes.

**No conformance fixture changed:** 563/563, and the full Gradle suite is green.

## Tests

`EB16WildcardSelectorTest` (`modules/engine`), 4 tests:

- the wildcard yields the child values on every strategy, and specifically **not** `null`;
- it feeds a pipeline (`|> map`) identically on every strategy;
- the strategies agree across four wildcard expressions (bare, `map`, `filter`, `count`);
- an ordinary property access is **still compiled** — the guard that keeps the refusal narrow.

Revert-verified: restoring the unconditional `MemberAccess` arm fails **3 of the 4**. The
ordinary-access guard still passes, which is what shows the change only removed the wildcard from the
compilable set.

## What the same pass found and did **not** fix

Driving the named-input fixtures surfaced two further divergences, both diagnosed and recorded in
`KNOWN_DIVERGENCES` with their causes rather than their symptoms:

1. **`datacontract/02_servers_wildcard` — COPY only.** With the wildcard fixed, COPY still differs:
   it carries a Kotlin runtime rendering into user data.
   ```
   server.host + ":" + toString(server.port)
     TEMPLATE/COMPILED -> "prod.db.example.com:5432"
     COPY              -> "prod.db.example.com:UDMValue(udm=Scalar(value=5432))"
   ```
   Minimised to: a wildcard-produced child, inside a lambda, passed to `toString()`, on COPY only,
   for both JSON and YAML input. `Interpreter.runtimeValueToUDM` unwraps a `UDMValue` correctly and
   COMPILED agrees with TEMPLATE, so the defect is in **COPY's skeleton-merge marshalling** — not
   the compiler, not the stdlib. Needs its own change.

2. **`stdlib/object-lambda/mapValues_basic` — both compiled strategies.** Numeric exactness is not
   applied uniformly across the stdlib, so the strategies disagree on *computed* scale. Measured on
   the interpreter:
   | expression | result | exact? |
   |---|---|---|
   | `$d.prices.apple * 2` | `3.0` | yes |
   | `map([1.5, 0.75], v -> v * 2)` | `3.0`, `1.50` | yes |
   | `mapValues(…, v => v * 2)` | `3`, `1.5` | **no** |
   | `sum([1.50, 1.50])` | `3` | **no** (`3.00` would be) |

   [B44](B44-stdlib-results-lose-the-exact-numeric-form.md) closed one such edge; these are further
   ones. Left listed rather than patched because the right answer needs a **decision** first: should
   a *computed* result keep BigDecimal's scale (`1.50 * 3 = 4.50`, as today) or normalise
   (`3.0` → `3`)? [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md)
   settled scale as **written**; it never settled computed scale, and these fixtures were authored
   expecting normalisation.

## The harness change that found all of this

The parity harness skipped any fixture declaring a named `inputs:` map — 39 of them — because
`execute()` takes one payload string. That was a loader limitation, not a product one: the engine
dispatches on `header.inputs.size`, so

- **1 named input** behaves exactly like `input:` and wants that input's data verbatim (17 fixtures
  were skipped purely because the loader read the wrong key);
- **2 or more** want the JSON envelope the engine already documents — keyed by input name, with
  non-JSON inputs embedded as JSON strings holding their raw text (22 fixtures).

Result: **357 → 396 fixtures driven**, and the only remaining skips are the 4 non-deterministic
ones, which a differential harness can never drive. Both guard floors were re-measured
(`MIN_FIXTURES` 355 → 390, `MIN_COMPILED` 325 → 362).

## Related

- [EB12](EB12-compiled-try-catch-emits-unverifiable-bytecode.md) — the precedent for refusing to
  compile a construct rather than compiling it wrongly.
- [EB15](EB15-version-gate-not-enforced-on-the-compiled-strategies.md),
  [B44](B44-stdlib-results-lose-the-exact-numeric-form.md) — the other two baseline entries
  diagnosed in this pass.
- `docs/architecture/conformance-across-execution-strategies.md` — the harness.
