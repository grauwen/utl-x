# B52 — the type checker reports every stdlib function as "Undefined variable"

**Status:** Fixed — 2026-11-12
**Component:** `:core` — `types/type_system.kt`
**Found:** 2026-11-12, while checking whether a new function alias needed a type signature
**Severity:** High — `utlx validate` cannot distinguish a real stdlib function from a typo,
which is the single most useful thing level-2 validation could do

## Summary

```bash
$ utlx validate script.utlx

Type Warnings:
  ⚠ 5:6 - Undefined variable: join
    |
      5 | { r: join(["a","b"],"-") }
             ^

✓ Validation passed
```

`join` is a registered stdlib function, it is in the analysis type registry, and it runs
correctly. The warning is wrong.

**And the same warning is produced for a function that does not exist**, so the diagnostic
carries no information either way:

| expression | runs? | validate says |
|---|---|---|
| `upper("a")` | yes | `Undefined variable: upper` |
| `count([1,2])` | yes | `Undefined variable: count` |
| `keys({"a":1})` | yes | `Undefined variable: keys` |
| `sortBy([2,1],(x)->x)` | yes | `Undefined variable: sortBy` |
| `join(["a"],"-")` | yes | `Undefined variable: join` |
| `sum([1,2])` | yes | `Undefined variable: sum` |
| `nope123("x")` | **no — undefined** | `Undefined variable: nope123` |

Every stdlib call in every script produces a spurious warning, and a genuine typo produces
an identical one. A user learns to ignore the whole section, which is worse than silence.

## Root cause

`type_system.kt:229`:

```kotlin
is Expression.Identifier -> {
    env.lookup(expr.name) ?: run {
        errors.add(TypeError("Undefined variable: ${expr.name}", expr.location))
        UTLXType.Unknown
    }
}
```

A function name in call position parses as an `Expression.Identifier`, so it arrives here.
`env.lookup` searches the **variable** environment only.

The type system does have a function table — `val functions = mutableMapOf<String, StdLibFunction>()`
at line 712, populated by 15 `register(...)` calls from line 716 — but the identifier path
never consults it. So even `upper`, which *is* registered there at line 716, is reported
undefined.

Two separate problems, and the second is the reason the first is not merely cosmetic:

1. **The identifier path does not fall back to the function table**, so every call warns.
2. **The message is the wrong one.** A name in call position that resolves nowhere is an
   *undefined function*, not an undefined variable. Reporting it as a variable hides the
   actual class of error.

## Related: three function registries, none authoritative

Noted while diagnosing, because it explains why a fix needs a decision about *which* table
to consult:

| registry | file | count | purpose |
|---|---|---|---|
| dispatch | `stdlib/Functions.kt` | ~764 | what actually runs |
| analysis types | `modules/analysis/.../FunctionTypeRegistry.kt` | — | `utlx validate`, IDE |
| core types | `core/types/type_system.kt:712` | **15** | this type checker |

The core type system knows **15** functions out of ~764. Consulting it alone would silence
the warning for `upper` and `sum` while still mis-reporting `join`, `keys` and `sortBy`.

The inconsistency is already visible in the analysis registry too: `joinBy` and `typeOf` are
present, while `innerJoin`, `daysBetween` and `hasKey` — two of them working aliases — are absent.
Nothing breaks when a function is missing from either type table, which is precisely because
this code path never reads them.

## The fix

Three changes, in `core/types/type_system.kt` unless noted.

**1. Resolve a named callee as a function, in `Expression.FunctionCall`.** The branch opened
with `val funcType = inferType(expr.function, env)` run first and unconditionally, so an
identifier in call position reached the `Identifier` branch above and was recorded as
"Undefined variable" before anything function-shaped was consulted. Resolution order is now:

| order | source | on a hit |
|---|---|---|
| 1 | `env.lookup(name)` — a `let`-bound lambda | fall through to the generic value path |
| 2 | `stdlib.getFunction(name)` — core's 15 signatures | check arity and argument types |
| 3 | `knownFunctionNames` — the authoritative set | silent; no signature to check against |
| — | none of the three | `Undefined function: <name>` at the **callee's** location |

**2. An optional authoritative name set.** `TypeChecker` takes
`knownFunctionNames: Set<String> = emptySet()`, and when that set is **empty it stays
silent**. This is not defensiveness: the module dependency runs `stdlib → core`, so core
cannot see the dispatch registry at all, and any caller constructing a bare `TypeChecker`
would otherwise start failing `--strict` on the ~750 functions core has no signature for.
`ValidateCommand` — which *can* see `:stdlib` — supplies
`StandardLibrary.getAllFunctionNames()`, so the real diagnostic only exists where the real
answer does.

**3. Object-literal `let` bindings get a child scope.** Found by introducing it: once a named
callee resolved as a function first, `{ let f = (x) -> x * 2, r: f(21) }` reported
`Undefined function: f`, because the `ObjectLiteral` branch ignored `expr.letBindings`
entirely and type-checked every property against the outer environment. The lambda had never
been found as a variable either — the old code simply reported it as one undefined name
rather than another, so the omission was invisible.

### A fourth thing, corrected before it shipped

Checking stdlib *ahead of* the environment made a shadowing `let` fail against the signature
it shadows:

```utlx
{ let count = (a, b) -> a + b, r: count(1, 2) }   # runs; yields 3
```

reported `Function 'count' expects 1 arguments, got 2` plus an argument type mismatch. The
interpreter gives a binding precedence over a stdlib function of the same name
(`{ let upper = (x) -> "S", r: upper("a") }` yields `"S"`), so the type checker now does the
same — hence order 1 above sitting ahead of order 2.

## Verification

| suite | before | after |
|---|---|---|
| `:modules:core:test` | — | 342, 0 failures (incl. `B52FunctionCallTypeCheckTest`, 9) |
| `:modules:cli:test` | — | 136, 0 failures (incl. `B52ValidateFunctionNamesTest`, 16) |
| `:stdlib:test` | — | 2326, 0 failures |
| `:modules:engine:test` | 999 | **1013**, 0 failures |
| conformance (JVM) | 573 | **574**, 100% |
| conformance (native) | — | **574**, 100% in 6.6s |

Checked natively on purpose rather than by habit: `ValidateCommand` now calls
`StandardLibrary.getAllFunctionNames()`, so the dispatch registry has to be fully populated
inside a GraalVM image — a registry assembled by `register(...)` calls at class-init time is
exactly the shape that can come back empty under native image, and an empty set makes the
new diagnostic go **silent** rather than fail loudly. It does not: all four behaviours above
are identical on `modules/cli/build/native/nativeCompile/utlx`.

Behaviour, end to end:

```bash
$ utlx validate ok.utlx          # { a: upper("x"), b: count([1,2]), c: sum([1,2]), … }
✓ Validation passed

$ utlx validate typo.utlx
Type Warnings:
  ⚠ 5:6 - Undefined function: totallyMadeUpFunction
    |
      5 | { r: totallyMadeUpFunction(1) }
             ^
✓ Validation passed

$ utlx validate typo.utlx --strict ; echo $?
Type Errors:
  ⚠ 5:6 - Undefined function: totallyMadeUpFunction
✗ Validation failed
1
```

### The registry sweep

`B52ValidateFunctionNamesTest` asserts against
`StandardLibrary.getAllFunctionNames()` rather than a hand-written list, because a list
drifts and the drift *is* the bug. Of 728 registered names, 718 are identifier-shaped (the
other 10 are the `validate.*` namespace), and **717 of those validate without a false
"Undefined function"**.

The one exception is pinned exactly rather than tolerated: **`if` is registered as a function
but cannot be called**, because it is a reserved word, so `{ r: if() }` is a parse error. A
pre-existing wart, unrelated to B52, recorded here because the test's skip branch is what
could otherwise hollow the sweep out — if a change made many names unparseable the sweep
would pass while checking almost nothing, so the skipped set is asserted to be exactly
`["if"]`.

## Why there is no conformance fixture for the warning

B52's own "verification needed" section asked for a fixture under `language/validate/`
asserting the warning text. **That cannot be written.** The Python runner hard-codes the
subcommand — `cmd = [str(utlx_cli), 'transform', transform_file]` in
`simple-runner.py` — and has no command dimension, so no fixture can invoke `utlx validate`
at all. The `language/validate/` fixtures that exist test the `validate.*` **stdlib
namespace** reached through `transform`, which is a different thing.

So the warning text, the caret column, the `--strict` exit code and the registry sweep live
in `B52ValidateFunctionNamesTest` (`:modules:cli`) — the only layer that both wires in the
authoritative name set and can call the command.

What *was* worth a fixture is the runtime half of change 3:
`language/let-bindings/b52_object_let_lambda_scope.yaml` pins object-literal `let` lambdas —
direct call, inside `map`, visible to a nested object, shadowed by a nearer binding, and
still the outer one afterwards. Nothing in the corpus had covered that.

## What the fixture found: EB17

Adding it cost two days' worth of compiled-path bugs and paid for itself immediately.
`ConformanceStrategyParityTest` ran the new fixture through all three strategies and COPY and
COMPILED both failed where the interpreter succeeded — see
[EB17](EB17-a-captured-binding-is-readable-but-not-callable.md). Two defects, both
pre-existing, neither related to the type checker:

- a `let`-bound lambda invoked from **inside another lambda** threw `Undefined function` at
  runtime, and
- a nested object's `let` **overwrote** an outer binding of the same name for the rest of the
  enclosing object.

Fixing them also emptied the `COMPILED_UNDEFINED_FUNCTION` class from the parity baseline —
three fixtures whose recorded diagnosis ("a missing capability, not a coding error") had been
wrong for as long as they sat there.

## Still open

- the three function registries remain unreconciled (dispatch ~764, analysis types, core
  types 15); a name-only check stops the false positives but does not give `--strict` real
  signatures. Same shape as [B50](B50-join-is-the-string-join-innerjoin-is-relational.md).
- `if` is registered but uncallable (above).
