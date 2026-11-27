# B69: one name, several signatures — nine ambiguous catalogue entries

> **B = core/formats/stdlib** — the cause is in `:stdlib`'s annotations and registry export;
> `utlx functions` is only where it shows. Split out of
> [B68](B68-working-functions-are-absent-from-the-catalogue.md). (Filed as CB12 at first; see B68's
> header for why that prefix was wrong.)

**Status:** **Fixed — 2026-11-26.** All nine resolved: `concat`
([B71](B71-concat-was-four-operations-behind-one-name.md)), `contains`
([B72](B72-contains-searched-for-empty-text-when-given-null.md)), then `isEmpty`, `titleCase`,
`distinctBy`, `findIndex`, `parseDate`, `parseInt` and finally `hasKey`. The catalogue now holds
**724 entries for 724 distinct names** — no name is listed twice, and the test that used to ratchet
a tolerated list now asserts that absolutely.
**Priority:** Low. An earlier version of this report rated four of the nine Medium, on the grounds
that they were registered twice and file order decided the winner. **That was wrong** — see
*Correction* below. Every one of the eight resolved cases was a documentation defect with no
behaviour at stake.
**Component:** `stdlib` — `@UTLXFunction` annotations and `Functions.exportRegistry()`
**Found:** 2026-11-23, by the two-way audit that closed B68.
**Led to:** [B73](B73-the-interpreter-reimplements-seven-stdlib-functions-and-findindex-disagrees.md)
— verifying one claim made while cleaning this up revealed a genuine TEMPLATE/COMPILED divergence.

## Symptom

The catalogue held **733 entries for 724 distinct names** when this was filed: nine names appeared
more than once, so a lookup returned several different answers without saying which one ran. It now
holds 724 for 724.

```
$ utlx functions info concat
concat(…)   Core    — concatenates values
concat(…)   String  — joins strings
concat(…)   Array   — concatenates arrays
```

All three are documented. Exactly one executes.

Originally:

```
concat  contains  distinctBy  findIndex  hasKey  isEmpty  parseDate  parseInt  titleCase
```

`concat` was the ninth and is **fixed** — see
[B71](B71-concat-was-four-operations-behind-one-name.md). It is worth reading as the worked example
for the rest: the three entries turned out to be one *polymorphic* function plus two unregistered
duplicates, and the fix was to narrow `concat` to the single meaning every call site used
(strings), give the array form the name it deserved (`arrayConcat`, matching the existing
`binaryConcat`), and point the other types at `merge` and `sum`, which already existed. Two of the
three entries vanished without renaming anything a user could call.

## Cause

Three mechanisms, which is why this is not one fix.

**1. Annotated but never registered — CLOSED.** A second object annotated the same method name
while not being registered under it, so the entry documented code that could not be reached.

All four were **the same story**: the function had been deliberately superseded by a wider
implementation, the old registration was commented out *with the reason written beside it*, and
only the `@UTLXFunction` was left behind — still advertising the retired signature. Three of the
four even carried the explanation in the source:

```kotlin
//register("isEmpty", MoreStringFunctions::isEmpty)// now generic UDM isEmpty check for all types…
// register("titleCase", MoreStringFunctions::titleCase) // DUPLICATE: titleCase already registered in CaseFunctions
```

So the pattern is worth looking for by name — *a commented-out registration whose function still
carries an annotation* — rather than waiting for the duplicate to be noticed. None of the four
needed a behaviour decision, because in each case the live function was the one everyone already
called:

| name        | runs                       | also annotated, never registered                    |
|-------------|----------------------------|-----------------------------------------------------|
| ~~`concat`~~ | ~~`CoreFunctions::concat`~~ | **fixed** — B71 |
| ~~`contains`~~ | ~~`CoreFunctions::contains`~~ | **fixed** — dead copy deleted, see below |
| ~~`isEmpty`~~ | ~~`CoreFunctions::isEmpty`~~ | **fixed** — dead copy deleted, see below |
| ~~`titleCase`~~ | ~~`CaseFunctions::titleCase`~~ | **fixed** — dead copy deleted, see below |

These are B68's phantoms again, hidden behind a name that *is* callable — which is why the
phantom check alone does not catch them, and the duplicate check exists.

**2. Registered twice, last one wins — THIS MECHANISM DOES NOT EXIST.** See *Correction* below.
`findIndex`, `parseDate`, `parseInt` and `distinctBy` are mechanism 1 like the rest: their second
registration has been commented out since 2025-10-18, each with the reason beside it, and only the
annotation was left behind. All four are now resolved the same way.

**3. An alias colliding with an annotated method (1 name).** `hasKey` is
`registerAlias("hasKey", "containsKey", …)`, which catalogues the alias; `ObjectFunctions::hasKey`
is also annotated but never registered. Two entries, and the one that runs is `containsKey`'s
implementation, not the annotated `hasKey`.

## Correction: there was never a "registered twice" mechanism

This report claimed four names had two live registrations, with file order silently picking the
winner, and rated that Medium because it was "more than a documentation bug". **It was not true.**
All four read like this, and have since 2025-10-18:

```kotlin
register("parseDate", DateFunctions::parseDate)
// register("parseDate", ConversionFunctions::parseDate) // DUPLICATE: parseDate already registered in DateFunctions
```

The second registration is commented out. One implementation runs, chosen deliberately and
annotated as such.

The error was in the measurement, for the **third** time in this bug family, and in a new disguise
each time. The script that classified the duplicates stripped `/* */` blocks and `//` lines, but
deliberately kept `/** */` KDoc — and several files document their own wiring inside KDoc:

```kotlin
 *     register("update", ValueFunctions::update)
```

Those example lines start with `*`, not `//`, so they survived the strip and were counted as real
registrations. [B68](B68-working-functions-are-absent-from-the-catalogue.md) records the first two
versions of this mistake (counting `//`-commented registrations, then missing `/* */` blocks). The
lesson is the same each time and was already written there: **the running program is the authority
on what is registered, not the source text.** `StandardLibrary.getFunctionNames()` would have
answered this correctly in one call.

## Fix sketch

With mechanism 2 gone, what is left is small. Mechanism 1 is resolved: remove the annotation from
the implementation that is never registered, and delete the implementation too when nothing else
justifies keeping it.

Of the four unreachable implementations, **two were deleted and two kept** — each on its merits,
not out of caution:

**Deleted** — `CriticalArrayFunctions::findIndex` and `EnhancedArrayFunctions::distinctBy`, with
their tests (9 cases from `CriticalArrayFunctionsB23Test`, 8 from `EnhancedArrayFunctionsTest`, and
two methods from `CriticalArrayFunctionsTest`) and the commented-out registrations that described
them. The registered `ArrayFunctions` versions remain at `ArrayFunctions.kt:145` and `:737`, still
catalogued once each and still working.

An earlier version of this section called those two *"equivalent to the registered versions"*. They
were not, and the difference is the reason they were safe to delete rather than a reason to keep
them:

| | registered (runs) | deleted copy |
|---|---|---|
| `findIndex`, no match | `null` | `-1` |
| `distinctBy`, key comparison | by **value** (`UDM` equality) | by **text** (`.asString()`), so `1` and `"1"` collapsed together |

Neither variant is a capability anyone would request — `null` lets `??` supply a default, and
collapsing `1` with `"1"` is closer to a defect than a feature. What was lost by deleting them is
only tests that exercised code no script could reach.

Checking that `-1`/`null` claim is what uncovered
[B73](B73-the-interpreter-reimplements-seven-stdlib-functions-and-findindex-disagrees.md): the CLI
returns `-1` for `findIndex` with no match, which the registered function cannot produce, because
`modules/core`'s interpreter carries a **third** implementation that shadows the registry. So this
cleanup did not create that divergence — it exposed one that had been there all along.

**Exposed, then deleted** — `ConversionFunctions::parseDate` and `::parseInt`. These were not
variants of the same operation: they took an extra argument used as a **fallback when parsing
fails**, where the registered versions throw. Nothing reached that behaviour — not
`parseInt("nope", 0)`, not `parseInt("nope") ?? 0`, not `coalesce(parseInt("nope"), 0)`, because a
thrown error is not a value to defend against. For a CSV column that is mostly numeric with the odd
`"N/A"`, there was no way to say "default it to 0" without pre-validating every field.

That was worth having, so it is now registered as **`parseIntOr`** and **`parseDateOr`**
(2026-11-26):

```
parseIntOr("42", 0)            => 42
parseIntOr("N/A", 0)           => 0
parseDateOr("2020-03-15", null) => a typed date
parseDateOr("junk", null)      => null
```

Two decisions worth recording:

- **Separate names, not an extra parameter.** The capability could not extend `parseDate`, whose
  third argument is already **locale** — its own error proves it: `"pattern: yyyy, locale: N/A"`.
  So `parseDateOr` takes the fallback as argument *two*, with the pattern third.
- **Delegating wrappers, not the old copies.** Registering those copies would have shipped their
  drift: `ConversionFunctions::parseInt` returned a Double via `parseNumber`, and its `parseDate`
  returned `UDM.Scalar(date.toString())` — a string rather than a typed date, with a comment
  admitting "you may need to adjust based on your UDM date representation". The wrappers call the
  registered functions and catch failure, so whatever `parseInt` accepts, `parseIntOr` accepts
  identically, and `parseDateOr` yields a real `UDM.Date`. A wrapper cannot drift from what it
  calls — which is the lesson of [B73](B73-the-interpreter-reimplements-seven-stdlib-functions-and-findindex-disagrees.md),
  where two implementations of one function disagreed for months.

With the capability properly homed, the copies were unreachable duplicates like the rest, and were
deleted with their commented-out registrations and the five test methods that exercised them.
Tests for the new functions are in `B69FallbackParseTest` — including the one asymmetry worth
pinning: a successful parse returns a number, while the fallback is returned **verbatim**, so a
`null` or textual fallback survives unchanged.

`hasKey` (mechanism 3) remains: `registerAlias("hasKey", "containsKey", …)` catalogues the alias
while `ObjectFunctions::hasKey` is annotated but never registered, so what runs is `containsKey`'s
implementation and not the annotated one.

A guard against recurrence belongs in `exportRegistry()`: two annotations claiming one name is
almost always a mistake, and the export is the natural place to refuse it.

## Resolved: `contains`

Not two functions — the remains of a move. `contains` began as a string-only
`StringFunctions::contains`; it was later moved to `CoreFunctions::contains`, which handles
strings, arrays, object keys and binary, and the old registration was commented out with that
reason in `Functions.kt`. The old method and its `@UTLXFunction` were left behind. The annotation
was also wrong — `maxArgs = 1` for a two-argument function — so the catalogue offered a second
`contains` signature that no script could reach.

The live function's behaviour is a strict superset of the dead one, and nothing called the dead
one except its own unit test, which therefore tested code users never run. Unlike `concat`, nothing
needed choosing:

- `StringFunctions.contains` and its annotation deleted, with the commented-out registration;
- the string assertions moved to `CoreFunctionsTest.testContains`, against the registered
  implementation, together with the array and object-key forms that had no unit test at all;
- the `stdlib-complete-reference.md` *String* row for `contains` removed; the *Core* row stays.

No behaviour changes: what runs for `contains(...)` is the same code as before.

## Resolved: `isEmpty`

The same story as `contains`, a third time — and the cheapest of the three, because nothing needed
deciding and no behaviour changed.

`CoreFunctions::isEmpty` is polymorphic: `isEmpty("")`, `isEmpty([])`, `isEmpty({})` and
`isEmpty(null)` are all `true`, binary is empty when it has no bytes, and numbers and booleans are
never empty. `MoreStringFunctions::isEmpty` only stringified its argument and measured the text.
The narrower one had **already been retired on purpose**, with the reason beside the commented-out
registration in `Functions.kt`:

```kotlin
//register("isEmpty", MoreStringFunctions::isEmpty)// now generic UDM isEmpty check for all types: strings, arrays, objects, binary, numbers, datetime
```

The registration was commented out; the annotation was not.

Reviving the string-only version would have been a regression, not a fix: it reaches its answer
through `asString()`, so on a container it measures the **text** form — `isEmpty([])` would be
asking whether an empty array stringifies to `""`, which is a different question from whether the
array is empty.

**The dead function was deleted, not just un-annotated.** The first pass removed the annotation and
kept the method, on the reasoning that it was "unit-tested and harmless". That reasoning was
circular, and checking it settled the matter: nothing in the codebase calls these functions — no
internal reuse, no other stdlib function — so the *only* caller was the unit test, which existed to
test code no script could reach. That is the same thing `contains` was criticised for above, so the
same remedy applies: method, annotation and commented-out registration all deleted, and the
assertions moved to `CoreFunctionsTest` where they run against the implementation that is actually
registered. The ported `isEmpty` test also gained the array, object, number and boolean cases the
string-only copy could not express.

**Unlike `concat`, this polymorphism is correct and stays.** `concat` was four unrelated
*operations* sharing a name; `isEmpty` is one *question* answered sensibly per type, the way `count`
and `length` are. Splitting it would have been the mistake here.

Three annotations also advertised parameters their functions do not take, which is visible to users
in the `Signature:` line — `isEmpty(array, predicate)` and `isNotEmpty(array, element)` for
functions taking a single value (`isNotEmpty` also declared `minArgs = 2`), and an `index`
parameter on `isBlank`. Each corrected to the one argument it actually takes.

## Resolved: `titleCase`

The fourth and last of mechanism 1, and the one that justified B69's own warning to *check before
discarding* — because unlike `contains` and `isEmpty`, these two do **not** agree:

| input | `CaseFunctions` (registered) | `MoreStringFunctions` (deleted) |
|---|---|---|
| `"hello WORLD"` | `"Hello World"` | `"Hello WORLD"` — keeps inner capitals |
| `"hELLo WoRLd"` | `"Hello World"` | `"HELLo WoRLd"` |
| `"a\tb"` | `"A\tB"` | `"A\tb"` — only a space starts a new word |
| `"hello  world"` | `"Hello  World"` | `"Hello  World"` — same |

> An earlier version of this table claimed the retired one *collapsed* runs of spaces. That was
> wrong: `split(" ")` then `joinToString(" ")` round-trips consecutive spaces exactly, and the
> retired function's own test asserted `"Hello  World"`. The real difference is narrower — inner
> capitals, and that only a literal space separates words, so a tab does not.

Deleting it changes nothing, because it was unreachable: the commented-out registration said
*"DUPLICATE: titleCase already registered in CaseFunctions"*. Nothing had to be ported either —
`CaseFunctionsTest.testTitleCase` already covered every case the dead test did, the mixed-case one
included. If the keep-inner-capitals behaviour is ever wanted it needs its **own registered name**,
since it is a different function and not a duplicate; the comparison above is preserved here for
whoever wants it, and the implementation is six lines in git history.

## Resolved: `hasKey` — the last one, and the one that justified the duplicate check

`hasKey` is callable, and always was, through

```kotlin
register("containsKey", ObjectFunctions::containsKey)
registerAlias("hasKey", "containsKey", "Common object method name")
```

`registerAlias` makes the name callable **and** catalogues it against the implementation that runs,
which is correct and wanted. The defect sat beside it: `ObjectFunctions` also contained a
`fun hasKey` carrying an `@UTLXFunction`, never registered — and **byte-for-byte identical** to
`containsKey`, same body, same argument checks, differing only in the function name inside its
error strings. A copy-paste that was annotated and then forgotten.

So the catalogue carried two `hasKey` entries: the alias, pointing at `containsKey`, and the copy,
describing code nothing dispatches to. The copy had no callers anywhere — not even a test.

Deleted. The alias stands, `hasKey({"a":1}, "a")` still answers `true`, and nothing else changed.

**This case is why the duplicate check is not redundant with the phantom check.** A duplicate can
hide behind a name that *is* callable: `hasKey` worked, so `every catalogued function is callable`
passed, and `every callable function appears in the catalogue` passed too. Only counting entries
per name found it. The mutation test shows exactly that — re-introducing the copy fails
`no name is catalogued twice` with `{hasKey=2}` while the other four invariants stay green.

`containsKey`'s annotation also advertised an `array` parameter for a function that takes an object.
Corrected — and the alias inherits its metadata, so both entries now show `(object, key)`.

## The guard, now absolute

`CoreFunctionsAreDiscoverableTest.no name is catalogued twice` began as a ratchet over nine
tolerated names. With all nine resolved it is now an **absolute** invariant: the catalogue may not
list any name twice. That is the better end state — a ratchet records a debt, an invariant forbids
it — and the nine leaving the set one at a time is what the mechanism looked like while the debt
was being paid.
