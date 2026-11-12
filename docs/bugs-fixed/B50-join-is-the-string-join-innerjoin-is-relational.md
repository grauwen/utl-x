# B50 — `join` is the string join, `innerJoin` is relational

**Status:** Fixed — 2026-11-12
**Component:** `:stdlib` — `string/StringFunctions.kt`, `array/JoinFunctions.kt`, function registry
**Found:** 2026-11-12, comparing UTL-X against `jq` idiom by idiom

## The symptom

```
$ utlx functions info join        ->  "Join array elements with delimiter"   (the STRING join)
$ utlx transform -e 'join(a,",")' ->  Error: join requires 4 arguments…      (the RELATIONAL join)
$ utlx functions info joinBy      ->  Error: Function not found              (yet it was callable)
```

Documentation and dispatch disagreed in both directions, and the `join` a user could call was
not the `join` the docs described.

## The naming decision, and why UTL-X deviates from DataWeave

**`join(array, delimiter)` is the string join. `innerJoin(left, right, leftKeyFn, rightKeyFn)`
is the relational one.** DataWeave does the opposite:

| DataWeave | UTL-X | |
|---|---|---|
| `dw::core::Strings::joinBy(array, separator)` | **`join`** | string join |
| `dw::core::Arrays::join(l, r, lCriteria, rCriteria)` | **`innerJoin`** | relational |

This is deliberate, and rests on a convention UTL-X holds more strictly than DataWeave does.

### The `*By` suffix means "by this criteria function, over an array"

Of the **14** registered `*By` functions, **13** take a criteria / key / mapping **function**:

| function | 2nd argument |
|---|---|
| `sortBy`, `groupBy`, `distinctBy`, `maxBy`, `minBy`, `sumBy`, `avgBy`, `countBy`, `chunkBy`, `divideBy` | a function |
| `lookupBy` (3 args), `nestBy` (5 args) | key functions |
| **`joinBy`** | **a plain string delimiter** ← the only exception |

A `joinBy` whose second argument is a delimiter reads as though it took a key function. Under
this convention the *relational* join is the one that belongs in the `*By` family, and the
string join is plain `join`.

### The codebase had already made this choice

The string split is registered as **`split`**, not DataWeave's `splitBy`. So `split`/`join`
was already the plain string pair, with `*By` reserved for array-with-criteria — decided
independently of DataWeave and before this report.

### DataWeave's own convention is weaker

In DataWeave, both `splitBy` and `joinBy` take plain separators, so its `*By` means "by this
thing" generically. Matching DataWeave here would import that looseness into a vocabulary
that is otherwise consistent. **This is a deviation where UTL-X is the more coherent of the
two**, which is the only kind of deviation worth making.

### Why `innerJoin` rather than `joinBy`

`innerJoin` completes its family — `leftJoin`, `rightJoin`, `fullOuterJoin`, `crossJoin` — and
it was the only one of the five not named after its join type. It is also unambiguous:
`joinBy` would not say *which* join.

### `joinBy` is kept as a legacy alias

`joinBy` was the registered name for the string join from 2025-10-16 to 2026-11-12, and
`FunctionsTest` asserts it exists. It remains, via
`registerAlias("joinBy", "join", …)`, so a year of scripts keeps working — and so does a
DataWeave author's muscle memory for the string join.

## Migration note for DataWeave users

A DataWeave relational `join(l, r, lCriteria, rCriteria)` must become **`innerJoin`**. The
arity differs, so the failure is immediate and loud rather than silent:

```
join(orders, customers, (o) -> o.cid, (c) -> c.id)
  -> Error: join expects 2 argument(s), got 4
```

A DataWeave `joinBy(array, separator)` keeps working unchanged.

## Root cause of the symptom: the catalogue keys on the Kotlin function name

`@UTLXFunction` has **no `name` parameter**. The generated registry
(`stdlib/build/generated/function-registry/utlx-functions.json`, from the
`generateFunctionRegistry` Gradle task) therefore files each entry under the **Kotlin
function name**, while dispatch uses the name passed to `register()`.

The string join was declared `fun join` in `StringFunctions` but registered as `joinBy`, so:

- the catalogue filed it as `join`, colliding with `JoinFunctions.join`, and its description won
- nothing was filed as `joinBy`, so `info joinBy` found nothing

**Both Kotlin declarations now match their registered names** — `StringFunctions.join` and
`JoinFunctions.innerJoin` — which removes the collision at source. A test calls both directly,
so a rename on either side stops compiling.

A second defect in the same annotation: `minArgs = 1, maxArgs = 1` for a two-argument
function, plus a phantom third parameter (`replacement`). Corrected to 2/2.

## A wrong turn worth recording

Three attempts were made at this before it settled, and each was corrected by the project
owner:

1. **`join` = string, `joinBy` = string alias, `innerJoin` = relational.** Wrong half:
   `joinBy` as a string alias contradicts the very convention that justifies the change.
2. **Reverted to DataWeave's split** (`join` relational, `joinBy` string) on the grounds that
   DataWeave parity outranks general-language convention. Also wrong, because it discarded
   UTL-X's stricter `*By` convention — which is a *better* reason than either jq-convention
   or DW-parity.
3. **The current state**, which follows the convention and documents the deviation.

The lesson is narrow and worth keeping: a public API name should not be changed on the
strength of one document plus general convention. `F03` argued for (1) from a premise that
had been false for seven months, and checking DataWeave's actual behaviour disproved (1)
without establishing (2). What settled it was the `*By` convention — a fact about *this*
codebase, measurable in it.

`docs/features/F03-nestby-relational-to-hierarchical.md` carries a correction note, because
its stated reason ("the string `join(array, separator)` function that already exists") was
untrue when written. Its conclusion still holds: `nestBy` nests, which is a different
operation from any join.

## Verification

```
join(["prod","eu","critical"], ", ")                -> prod, eu, critical
joinBy(["prod","eu","critical"], ", ")              -> prod, eu, critical   (legacy alias)
innerJoin(orders, custs, (o)->o.c, (c)->c.k)        -> 2 matched rows
leftJoin / rightJoin / fullOuterJoin / crossJoin    -> unchanged
nestBy(orders, lines, …, "lines")                   -> children nested, unaffected
join(orders, custs, (o)->o.c, (c)->c.k)             -> Error: expects 2 arguments, got 4
utlx functions info join / joinBy / innerJoin       -> all three present and correct
```

- `B50JoinNamingTest` — 9 tests, including one that pins the `*By` convention by asserting
  `splitBy` is *not* the string split, so abandoning the premise shows up as a failure
- conformance fixture `stdlib/naming/b50_join_naming.yaml`, which also buys cross-strategy
  parity via `ConformanceStrategyParityTest`
- documentation updated in 10 files plus `TestCategorizer`

## Still open — the underlying drift

Nothing cross-checks the generated catalogue against the dispatch registry. The measurement
that found this bug also found **83 callable but undocumented** names and **8 documented but
not callable** (`createQName`, `endTimer`, `equals`, `ifThenElse`, `isLeapYearFunc`,
`matchesQName`, `resolveQName`, `startTimer`). A name present in both but *meaning different
things* — what `join` was — is invisible to a name-level diff, so the check must compare the
resolved implementation too.
