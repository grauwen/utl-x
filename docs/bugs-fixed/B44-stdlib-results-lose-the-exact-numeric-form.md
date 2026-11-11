# B44: a stdlib function's result loses the exact numeric form

> **B = core bug** (the shared language stack: `:core`, `formats/*`, `:stdlib`), the family of
> B01–B43. This is **B44**.

**Status:** **FIXED**
**Priority:** Medium. A silent loss of written precision, on a path most transformations use.
**Component:** `:core` — `Interpreter.udmToRuntimeValue`.
**Found by:** diagnosing the one `BOTH_OUTPUT` entry in the cross-strategy conformance baseline
(`stdlib/array/nestBy_basic`).

> **One-line:** `udmToRuntimeValue` narrowed every `Number` through `toDouble()`, so a value returned
> by `groupBy` or `nestBy` came back as `15.5` where the same value passed straight through stayed
> `15.500`.

## The whole bug

```kotlin
is Number -> RuntimeValue.NumberValue(value.toDouble())
```

[B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) made numbers exact end
to end — but in one direction only:

| direction | carries the exact form? |
|---|---|
| `RuntimeValue.toUDM()` | ✅ B35 — `UDM.Scalar.number(exact ?: value)` |
| `literalToRuntimeValue` | ✅ B35 — `NumberValue.exactly(…)` |
| **`udmToRuntimeValue`** | ❌ **narrowed through `toDouble()`** |

The third is the path that brings a **stdlib function's `UDM` result back into the interpreter**. So
exactness survived until a value was handed to a stdlib function and handed back.

## Measured

Input `{"orderLines":[{"orderId":"O1","product":"Gizmo","price":15.500}]}`, through the interpreter:

| expression | before | after |
|---|---|---|
| `$input.orderLines` | `15.500` | `15.500` |
| `$input.orderLines[0].price` | `15.500` | `15.500` |
| `map($input.orderLines, (l) -> l)` | `15.500` | `15.500` |
| **`groupBy(…)`** | **`15.5`** | **`15.500`** |
| **`nestBy(…)`** | **`15.5`** | **`15.500`** |

`map` was unaffected because it never leaves the interpreter's own value domain. `groupBy` and
`nestBy` are `:stdlib` functions taking and returning `UDM`, so they make the round trip.

Note the loss is **not** in the stdlib functions themselves — `buildGroups` adds the original
element objects to each group and rebuilds nothing. That was checked before the interpreter was
suspected.

## How it was found, and why that matters

This did not come from a bug report. It was the single `BOTH_OUTPUT` entry in the cross-strategy
parity baseline: both compiled strategies produced output differing from TEMPLATE on
`nestBy_basic`. Diffing the three outputs gave one line:

```
-  "price": 15.5        TEMPLATE
+  "price": 15.500      COPY and COMPILED
```

**TEMPLATE was the strategy that was wrong.** COPY and COMPILED were right, because they execute
compiled bytecode and never make the UDM→RuntimeValue round trip. That is the opposite of the usual
direction — every EB03–EB13 defect had the compiled path wrong — and it is why the entry sat in the
`BOTH_OUTPUT` class, the class flagged in the harness as "most valuable to look at".

The Python conformance suite could not have found this. It compares JSON by **parsed value**, so
`15.5` and `15.500` are the same float and the difference is invisible — the gotcha recorded in the
suite's own notes. Only a differential comparison of serialised output exposes it.

## Behaviour change

Written scale and width now survive a stdlib call. A transformation whose output went through
`groupBy`, `nestBy` or any other `UDM`-returning stdlib function will render trailing zeros and
large integers as written, matching what the same value does when it passes through directly, and
matching what the compiled strategies already did.

Arithmetic is unaffected: an exact *input* does not freeze a computed *result*, and integers are
untouched. Both are pinned by tests.

**No conformance fixture changed:** 563/563, and the full Gradle suite is green.

## Tests

`B44StdlibExactnessTest` (`modules/engine`, so all three strategies are reachable), 6 tests:

- `groupBy` and `nestBy` keep the scale **on every strategy**;
- the three strategies **agree** — the assertion the parity harness was making implicitly;
- a value that never enters a stdlib function was always exact and still is;
- integers are unaffected;
- arithmetic still computes rather than preserving a lexeme.

Revert-verified: restoring `NumberValue(value.toDouble())` fails **3 of the 6** — the two scale tests
and the agreement test. The three guard tests still pass.

## Consequence for the parity baseline

`stdlib/array/nestBy_basic` was removed from `KNOWN_DIVERGENCES`, taking the inventory from 5 to 4
and emptying the `BOTH_OUTPUT` class. The harness enforces that removal: a listed fixture that stops
diverging fails with *"now agree — delete the entry"*, so the baseline cannot go stale.

## Related

- [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) — the exact numeric
  model. This is the one direction it missed.
- [EB15](EB15-version-gate-not-enforced-on-the-compiled-strategies.md) — the other baseline entry
  diagnosed in the same pass.
- `docs/architecture/conformance-across-execution-strategies.md` — the harness that found it.
