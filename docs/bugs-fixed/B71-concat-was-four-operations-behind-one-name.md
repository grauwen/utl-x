# B71: `concat` was four operations behind one name, and added numbers

> **B = core/formats/stdlib** — `:stdlib`'s `CoreFunctions`.

**Status:** Fixed — 2026-11-24.
**Priority:** Medium — `concat(42, 58)` returned `100`. No call site hit it, but a function named
`concat` silently performing arithmetic is the kind of thing found in production, not in review.
**Component:** `stdlib` — `core/CoreFunctions.kt`, `array/MoreArrayFunctions.kt`
**Found:** 2026-11-24, by the user reading [B69](B69-one-name-several-signatures-in-the-catalogue.md)'s
output: *"I would leave concat for strings and find other names for the others."*

## Symptom

One registered `concat` dispatched on its first argument's type and performed four unrelated
operations:

```
concat("Hello", " ", "World")   => "Hello World"     string concatenation
concat([1, 2], [3, 4])          => [1, 2, 3, 4]      array concatenation
concat({"a": 1}, {"b": 2})      => {"a":1,"b":2}     object merge
concat(42, 58)                  => 100               addition
```

The last is the defect. In XPath and XSLT — which `docs/comparison/vs-xslt.md` maps `concat()`
straight onto — `concat(42, 58)` is `"4258"`: every argument is rendered as text. Returning their
*sum* from a function named `concat` has no precedent in any language, and nothing in the signature
or the name hints at it.

The other three were not wrong, just redundant: **all of them already had a name.**

## What the measurement showed

Before changing anything, every `concat(` occurrence in the repository was classified:

| where | occurrences | kind |
|---|---|---|
| `conformance-suite` | 30 | 28 strings, **2 array accumulators** |
| `docs` | 21 | all strings |
| `modules/*/src/test` | 9 | all strings |
| `examples` | 3 | all strings |
| `stdlib/src/test` | 7 | Kotlin calls to the *unregistered* implementations |
| **total** | **70** | **68 strings, 2 arrays, 0 objects, 0 numbers** |

The two array call sites are both accumulator patterns, and both are in conformance fixtures:

```utlx
reduceEntries($datacontract.servers, (acc, name, cfg) => concat(acc, [cfg.host]), [])
if (count(largeTransactions) > 0) concat(alerts, [{ type: "LARGE_TRANSACTION", … }]) else alerts
```

> **A correction, recorded because the first version of this report got it wrong.** This section
> originally claimed *"not one array, object or number call site"*. That was false, and the error
> was in the measurement, not the typing: the classification script printed only the first 14 hits
> per directory, the visible ones were all strings, and the conclusion was generalised from the
> sample instead of the population. The full conformance run found the two it had hidden — 586/588.
>
> The conclusion survives the correction (68 of 70 are strings; nothing used the object or number
> forms, and the number form was wrong anyway), but the *evidence* was overstated, which is the
> same mistake [B68](B68-working-functions-are-absent-from-the-catalogue.md) records twice over:
> trusting a partial reading of the source instead of the whole population.

**No test asserted the behaviour being removed.** The object and number forms were exercised
nowhere at all; the array form was exercised only by Kotlin unit tests calling
`MoreArrayFunctions.concat` *directly*, bypassing the registry.

That distinction matters, because changing tests so a behaviour change passes is how a regression
gets laundered into a green build. Every edit here was a **call-site migration with the expected
output untouched** — five Kotlin call sites following the rename, and the two fixtures above moved
to `arrayConcat`, which performs the identical operation. No expected value anywhere was adjusted.

## Fix

**`concat` is strings only**, as in XPath: every argument is rendered and joined.

```kotlin
return UDM.Scalar(args.joinToString("") { it.asString() })
```

`asString()` is the same render path `+` uses, so [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md)'s exact
numerics survive: `concat("x", 100.10)` is `"x100.10"`, not `"x100.1"`. The previous code used
`value?.toString()`, which would have lost that for a `Double` and produced
`DATETIME(INSTANT=…)`-style output for temporal values.

A structured argument is refused with the name of the function that does the job, because this is
precisely the operation `concat` used to perform — a bare type error would strand anyone who
relied on it:

```
$ utlx -e 'concat([1,2],[3,4])'
Error: concat builds a string, but got array.
       Hint: arrayConcat(...) joins arrays; joinBy(array, separator) makes a string from one.

$ utlx -e 'concat({"a":1},{"b":2})'
Error: concat builds a string, but got object. Hint: merge(...) combines objects.
```

**Array concatenation became `arrayConcat`.** The implementation already existed in
`MoreArrayFunctions`, was correct and unit-tested, and was simply never registered — so it is now
registered, with the Kotlin method renamed to match so no `name` override is needed
([F34](../features/F34-the-registration-should-be-the-only-place-a-function-name-is-written.md)).

Named after **`binaryConcat`**, which already existed and is the same operation on binary data:
the library's convention for "concat of a non-string type" is the `<type>Concat` prefix.

It cannot be folded into the existing `union`, which **de-duplicates**:

```
arrayConcat([1,2],[2,3])  => [1,2,2,3]
union([1,2],[2,3])        => [1,2,3]
```

A test asserts those two disagree, since the day they agree one of them is broken.

**Objects use `merge`** and **numbers use `sum`** — both already existed. No new names.

## Not a compatibility break in practice

`%utlx 1.0` scripts calling `concat` on strings are unaffected, which is every known call site.
A script relying on the array, object or number form would now get an error naming its
replacement rather than a wrong answer — and for the number form, the old answer was wrong
anyway.

## Verification

`stdlib/src/test/.../core/B71ConcatIsStringsOnlyTest.kt`, 7 tests: the string forms; `concat(42,
58) == "4258"`; the single-argument case (it used to `return args[0]`, handing back an array from
a function that returns a string); exact-numeric scale; both refusals carrying their alternative;
and `arrayConcat` keeping duplicates where `union` does not.

[B69](B69-one-name-several-signatures-in-the-catalogue.md)'s duplicate ratchet drops from nine
names to eight, and the catalogue now holds exactly one `concat`.
