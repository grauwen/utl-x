# B63: a `null` read from a document is not equal to the `null` literal

> **B = core bug** (`:core` interpreter). The defect is in `valuesEqual`, and the compiled strategy
> was *already* correct — so this is the rare case where fixing `:core` closes a divergence rather
> than opening one.

**Status:** **FIXED (2026-11-19)** — two cases added to `Interpreter.valuesEqual`, with a
conformance fixture that covers all three strategies.
**Priority:** High — a null guard silently failed on real document data, and the interpreted and
compiled strategies gave opposite answers.
**Component:** `:core` `interpreter.kt` `valuesEqual`
**Found:** 2026-11-19, from a field report against the `development` branch (commit `16210388`),
filed there as issue #9 and diagnosed as a `&&`/`||` short-circuit regression.
**Related:** [B34](B34-logical-operators-do-not-short-circuit.md) (short-circuit — *not* the cause
here), [EB03](EB03-compiled-comparison-coerces-types-so-strategies-disagree.md) and
[B48](B48-path-steps-do-not-project-across-a-sequence.md) (the strategy-divergence family)

## Summary

`==` and `!=` against the `null` literal did not recognise a null that came from a parsed
document. An **absent** property compared correctly; a property **present with value `null`** did
not:

```
typeOf($input.nulled)        -> "null"      the engine agrees it is null
$input.nulled == null        -> false       equality disagrees
$input.nulled != null        -> true
$input.missing == null       -> true        an absent property worked
$input.missing != null       -> false
null == null                 -> true
$input.nulled ?? "fallback"  -> "fallback"  ?? treated it as nullish, correctly
```

Three notions of null that contradicted each other, with `==` the odd one out.

## Why it was reported as a short-circuit bug

The author-facing symptom is a guard that does not guard:

```utlx
t.nulled != null && t.nulled.x == "y"
// Error: Cannot access property on non-object UDM type
```

which reads exactly like `&&` failing to short-circuit. It is not. B34's short-circuit is present
and correct in both the interpreter (`interpreter.kt:957`) and the compiled path
(`ASTCompiler.kt:622`); it was being handed a left operand of `true`, so it correctly proceeded to
the right operand, which correctly failed.

The same report's second example was **not a bug at all**:

```utlx
b == 1 || t.missing.x == "y"      // errors when b != 1
```

`||` must evaluate its right operand when the left is falsy. Member access on null throwing is the
language's existing behaviour, and `?.` / `??` is the right tool there — which is what the reporter
had already fallen back on.

## Root cause

A JSON `null` parses to `UDM.Scalar.nullValue()` (`json_parser.kt:251`), so it reaches the
interpreter as `RuntimeValue.UDMValue(UDM.Scalar(null))`. The `null` literal is
`RuntimeValue.NullValue`. `valuesEqual` had a case for `NullValue` × `NullValue`, cases for
`UDMValue` × `StringValue` and `UDMValue` × `BooleanValue`, and a case for `UDMValue` × `UDMValue`
— but none pairing `UDMValue` with `NullValue`:

```kotlin
left is RuntimeValue.NullValue && right is RuntimeValue.NullValue -> true
// … UDMValue × StringValue, UDMValue × BooleanValue, UDMValue × UDMValue …
else -> false        // <-- UDMValue(Scalar(null)) × NullValue landed here
```

An absent property resolves to `NullValue` directly, which is why it matched and the document null
did not. `UDMValue` × `UDMValue` reduces to `leftUdm.value == rightUdm.value`, so a document null
did equal *another* document null. Only the cross-representation comparison — the one every author
actually writes — failed.

## The strategy divergence

The compiled strategy works on `UDM` throughout, never on `RuntimeValue`
(`RuntimeOps.kt:537`):

```kotlin
if (left !is UDM.Scalar || right !is UDM.Scalar) return false
return l == r                      // null == null -> true
```

and `compileNullLiteral` (`ASTCompiler.kt:341`) emits `new UDM.Scalar(null)`. So both operands are
`UDM.Scalar(null)` and the comparison was already right:

| | `docNull == null` | `docNull != null` |
|---|---|---|
| TEMPLATE — interpreter, what the CLI runs | **false** | **true** |
| COMPILED — what UTLXe runs by default | true | false |

This is the B48/EB03 failure mode with the polarity reversed: the compiled path was correct and the
interpreter was wrong. A script developed and tested with the CLI therefore behaved *differently*
in UTLXe — and in this instance production was right and the development tool was wrong, which is
the harder direction to notice.

It also explains why the field report came from an **embedding** integration rather than from
UTLXe: `TransformationService` lives in `modules/cli` and drives the interpreter, so an embedded
caller gets the interpreted semantics even though the engine next to it does not.

## Fix

`interpreter.kt`, two cases in `valuesEqual`:

```kotlin
left is RuntimeValue.UDMValue && right is RuntimeValue.NullValue -> {
    val udm = left.udm
    udm is UDM.Scalar && udm.value == null
}
left is RuntimeValue.NullValue && right is RuntimeValue.UDMValue -> {
    val udm = right.udm
    udm is UDM.Scalar && udm.value == null
}
```

Written with an explicit `is UDM.Scalar` test rather than the shorter

```kotlin
(left.udm as? UDM.Scalar)?.value == null        // WRONG
```

because `as?` yields `null` for an Object or an Array and `null == null` then holds — so the
one-liner would make `$input.someObject == null` true. The conformance fixture asserts against
exactly that, so the wrong fix cannot pass.

`!=` needs no change: `BinaryOperator.NOT_EQUAL` negates `valuesEqual`.

## Verification

- `conformance-suite/utlx/tests/core/operators/null_equality_document_vs_literal.yaml` — 17
  assertions. Picked up automatically by `ConformanceStrategyParityTest`, so it runs on TEMPLATE,
  COPY and COMPILED; confirmed present and passing in the parity results.

  **No hand-written Kotlin test accompanies this fix.** The fixture reaches `valuesEqual` through
  all three strategies, which is strictly more than a unit test on `:core` would prove, and
  `valuesEqual` is private. Per `AGENTS.md`, a fixture buys the cross-strategy coverage and no
  parity test needs writing by hand.

  The fixture was extended once, for a gap worth recording: the fix is **two one-sided `when`
  branches** — `UDMValue × NullValue` and `NullValue × UDMValue` — so each operand order is its own
  code path. The first version tested only `$input.nulled == null` and left the second branch with
  **zero** coverage. The `litLeft*` cases put the literal on the left.
- `:modules:core:test` — 441 tests, 0 failures
- `:modules:engine:test` — 1029 tests, 0 failures (417 parity cases)
- `core/operators` conformance category — 5/5

The fixture's guards are deliberate: `absentEqNull` pins the behaviour that already worked, since
the asymmetry between absent and explicit null is what hid the bug; and `strNotNull`, `numNotNull`,
`objNotNull`, `arrNotNull` exist solely so the over-reaching `as?` fix fails. `numNotNull` uses `0`
because a falsy number must still not be null.

## What the existing coverage missed, and why

`logical_and_short_circuit.yaml` already contained the author-facing null-guard case:

```utlx
nullGuard: $input.qty != null && $input.qty > 0        // qty ABSENT -> false
```

It passed throughout, because `qty` is absent from that fixture's input rather than present and
null. The fixture tested the representation that worked. That is the lesson worth keeping: a null
test that only covers a missing key does not cover null.
