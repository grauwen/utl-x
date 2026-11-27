# B73: the interpreter re-implements seven stdlib functions, and `findIndex` disagrees with them

> **B = core/formats/stdlib.** The two implementations at issue live in `modules/core` and
> `stdlib`. `modules/engine` contains **no** implementation of any of them — its compiled path
> resolves the name through `StandardLibrary.getFunction(name)` (`StdlibDispatch.kt`) and invokes
> whatever the registry holds. The engine only *surfaces* the disagreement, so this is not an EB.

**Status:** Fixed — 2026-11-26. `findIndex` and `findLastIndex` answer `null` when nothing
matches, on every strategy, and a test now pins the two implementations to each other.
**Priority:** Medium for `findIndex` itself — one function, and only when nothing matches. Higher
for the cause: seven core functions have two implementations, and nothing compares them.
**Component:** `modules/core` — `interpreter/interpreter.kt`; `stdlib` — `array/ArrayFunctions.kt`
**Found:** while removing duplicate catalogue entries for
[B69](B69-one-name-several-signatures-in-the-catalogue.md). Deleting an unreachable `findIndex`
copy prompted the question *"the registered function is still there — point me where so I can
check myself"*, and checking it showed the CLI returning a value the registered function cannot
produce.

## Symptom

One script, one input, two answers depending on execution strategy:

```
%utlx 1.0
input json
output json
---
{ i: findIndex($input.a, x => x > 100) }      // input: {"a": [1, 2, 3]}
```

```
TEMPLATE  (the interpreter — what the CLI runs):     { "i": -1 }
COMPILED  (the ASTCompiler — what UTLXe runs):       { "i": null }
```

Measured, not reasoned: added as a temporary `ParityCase` to
`modules/engine/.../strategy/StrategyParityTest.kt`, which runs each case through all three
strategies and asserts they agree:

```
probe_findIndex_no_match: COMPILED output differs from TEMPLATE
  TEMPLATE: { "i": -1 }
  COMPILED: { "i": null }
```

The probe was removed again, so the suite is green; re-adding those four lines reproduces it.

## Cause

`modules/core`'s interpreter registers its **own** copies of seven stdlib functions directly into
the evaluation environment, where they shadow the registry entirely:

| name | interpreter copy | stdlib implementation |
|---|---|---|
| `map` | `interpreter.kt:1980` | `ArrayFunctions::map` |
| `filter` | `interpreter.kt:2008` | `CoreFunctions::filter` |
| `find` | `interpreter.kt:2111` | `ArrayFunctions::find` |
| **`findIndex`** | **`interpreter.kt:2144`** | **`ArrayFunctions::findIndex`** |
| `reduce` | `interpreter.kt:2180` | `ArrayFunctions::reduce` |
| `first` | `interpreter.kt:2219` | `ArrayFunctions::first` |
| `last` | `interpreter.kt:2246` | `ArrayFunctions::last` |

The compiled path has none of these, so it uses the stdlib versions. Two implementations of one
language function, and which one runs depends on the strategy. For `findIndex` they disagree:

```kotlin
// stdlib/.../array/ArrayFunctions.kt:161
return UDM.Scalar(if (index >= 0) index.toDouble() else null)

// modules/core/.../interpreter.kt — the shadow copy returns the raw indexOfFirst result, so -1
```

**The interpreter is not even self-consistent about it.** Two adjacent registrations in the same
file take opposite views of "not found":

```
find       no match → null    (interpreter.kt:2140, `found ?: RuntimeValue.NullValue`)
findIndex  no match → -1      (interpreter.kt:2144)
```

That is not a considered split between a value-returning and an index-returning function; `find`
follows the UTL-X idiom where `??` can supply a default, and `findIndex` follows JavaScript's
`Array.prototype.findIndex`. They were written at different moments.

Not every shadow diverges — `first` and `last` were checked and agree, both throwing on an empty
array with the same message. So the risk is per function, and `map`, `filter` and `reduce` remain
**unverified**: no one has compared them.

## Why nothing caught it

`ConformanceStrategyParityTest` auto-discovers the conformance corpus and runs every fixture
through TEMPLATE, COPY and COMPILED — exactly the control this needs. It passed, because **no
fixture calls `findIndex` with no match.** 421 parity tests over 590 fixtures, and this path was
simply never travelled. The same is true of the other six.

This is the fifth bug of this shape. [B48](B48-path-steps-do-not-project-across-a-sequence.md) is the
warning AGENTS.md repeats: an interpreter-only fix passed core, stdlib, CLI *and* the whole
conformance corpus while the compiled path silently disagreed. The lesson applies here in its
strongest form, because the CLI is what a developer tests with and the engine is what runs in
production — a script verified at the prompt can behave differently once deployed.

## The not-found convention already exists, and `findIndex` is the outlier

Measured across every sibling that answers "where is it / is it there":

| function | not found | implementation that runs |
|---|---|---|
| `indexOf` | **null** | stdlib |
| `lastIndexOf` | **null** | stdlib |
| `find` | **null** | interpreter shadow |
| `findIndex` | `-1` | interpreter shadow |
| `findLastIndex` | `-1` | stdlib `CriticalArrayFunctions` |

So `null` is already UTL-X's answer in three of five cases — including `indexOf`, the closest
analogue, since it also returns an index. The `-1` is not a house convention; it is two functions
borrowing `Array.prototype.findIndex` from JavaScript.

**And it makes the compiled path internally inconsistent.** `findLastIndex` returns
`UDM.Scalar(index.toDouble())` (`CriticalArrayFunctions.kt:114`), i.e. `-1`, and is *not* shadowed,
so stdlib's version runs on every strategy:

```
TEMPLATE : findIndex -> -1    findLastIndex -> -1     consistent, by accident
COMPILED : findIndex -> null  findLastIndex -> -1     inconsistent with itself
```

The CLI is accidentally coherent and the engine — the thing that ships — is not. Whatever is
decided for `findIndex` has to be decided for `findLastIndex` in the same breath, or the fix just
moves the inconsistency.

## Why a boolean mode argument is the wrong fix

The obvious-looking option is `findIndex(array, predicate, minusOneWhenMissing?)`. It should be
rejected, for four reasons:

1. **`??` already does it, and does more.** With `null`, every default is reachable and reads
   plainly: `findIndex(a, p) ?? -1`, `?? 0`, `?? "none"`. A boolean offers exactly two outcomes.
2. **The direction matters.** `null` → anything is a single operator. `-1` → `null` needs
   `if (findIndex(…) == -1) null else findIndex(…)`, which evaluates the search twice. Returning
   `null` is therefore strictly the more expressive default; returning `-1` loses information.
3. **It does not fix this bug.** Two implementations would both have to implement the flag, so the
   surface that can drift doubles. The defect here is duplication, not a missing option.
4. **A boolean at a call site is unreadable.** `findIndex(a, p, true)` tells a reader nothing;
   `?? -1` tells them everything. The only boolean flags in this stdlib today — `prettyPrint` on
   the three `render*Schema` functions, and `preservePattern` — control how output is *formatted*,
   never which value comes back. That line is worth keeping.

## The documentation is wrong either way

`utlx functions info findIndex` now describes `ArrayFunctions::findIndex` — the single catalogue
entry left after B69 — whose contract is `null`. The CLI returns `-1`. So the shipped catalogue
describes behaviour the CLI does not have, no matter which implementation is eventually judged
correct.

Note also that nothing *documents* the not-found case: `docs/language-guide/` does not mention it,
and the catalogue's `returns` is only *"Index of the element"*. So neither implementation violates
a written contract, and fixing this is a decision about intent rather than a correction against a
spec.

## Fix

**1. `findIndex` and `findLastIndex` answer `null` when nothing matches** — in *both*
implementations, so TEMPLATE and COMPILED agree. `null` matches `indexOf`, `lastIndexOf` and
`find`; `?? -1` recovers the old value at the call site, while the reverse needs a comparison and
searches twice. `findLastIndex` had to move in the same breath, or the inconsistency would simply
have relocated.

**2. The shadowing stays — and the original recommendation here was wrong.**

This section first said *"remove the shadowing, which is the actual defect"*, and that the
interpreter should resolve all seven through `StandardLibrary` the way the compiled path does. That
was attempted: the seven copies were deleted (287 lines) and the names were left to fall through to
stdlib. The CLI still worked. Then `modules/core`'s own tests failed with

```
Runtime error: Undefined variable: map
```

**`modules/core` cannot depend on `:stdlib` — the dependency runs the other way**
(`stdlib/build.gradle.kts` declares `implementation(project(":modules:core"))`, and
`modules/core/build.gradle.kts` declares no project dependencies at all). The interpreter owns
those seven implementations so that core can evaluate a script *standalone*, with no stdlib on the
classpath. It is deliberate, and `StandardLibraryIntegrationTest` pins it by name:

```kotlin
listOf("map", "filter", "reduce", "find", "findIndex", "first", "last").forEach { name ->
    assertNotNull(env.get(name), "$name should be registered by StandardLibraryImpl")
}
```

The fall-through only *looked* viable because the CLI injects the full registry via
`setStdlibLookup` (`TransformationService.kt:207`), and `tryRegisterFromLookup` installs a stdlib
function only when the interpreter has not already claimed the name. Remove the claim and the CLI
is fine — but a bare `Interpreter` is not, and that is a supported configuration.

So two implementations of these seven is **by design**, and the 287 lines were restored. What was
missing was never a single implementation; it was a **control** holding the two to the same answer.

## Verification

**`modules/engine/.../strategy/B73InterpreterAndStdlibAgreeTest.kt`** — the control that was
missing. It runs the cases that can distinguish the two implementations (no match, empty array, and
the ordinary path) through TEMPLATE and COMPILED and requires identical output, for all seven
functions plus `findLastIndex`. It is deliberately indifferent to *which* value is chosen: its job
is that the two implementations never disagree again.

Mutation-proven: putting `-1` back in the interpreter fails it with the disagreement named, listing
TEMPLATE against COMPILED per case.

**`conformance-suite/utlx/tests/stdlib/array/b73_findindex_not_found_is_null.yaml`** — covers
`findIndex`, `findLastIndex`, the empty array, `?? -1` recovery, and `indexOf` alongside them as
the sibling convention. Per AGENTS.md this buys cross-strategy coverage for free, since
`ConformanceStrategyParityTest` auto-discovers the corpus: the exact gap that let this bug live —
*no fixture called `findIndex` with no match* — is now closed in the place that gap existed.

`first` and `last` were also checked and already agreed (both throw on an empty array); `map`,
`filter` and `reduce` are now covered by the agreement test rather than assumed.
