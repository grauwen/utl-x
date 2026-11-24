# B68: working functions are absent from the function catalogue — and absent functions are advertised

> **B = core/formats/stdlib**, which is where the cause is: `:stdlib`'s registry export.
>
> Filed as **CB11** at first, on the reasoning that `utlx functions` is where a user meets it. That
> was wrong: the prefix names the component **at fault**, not the point of use — otherwise every
> stdlib defect becomes a CLI defect, since the CLI is how most of them are seen. The CLI code here
> is correct; it faithfully displays what `:stdlib` hands it.
> [CB05](CB05-functions-list-ignores-the-category-filter-in-text-output.md) is the contrasting
> shape: also a `utlx functions` bug, but caused in `FunctionsCommand`, so genuinely CB.
> Renumbered 2026-11-24, together with B69 and B70, which I misfiled the same way.

**Status:** Fixed — 2026-11-23.
**Priority:** Medium — nothing computed the wrong answer, but the catalogue could not be trusted,
and an untrustworthy catalogue cannot act as a control (see *How this was found*).
**Component:** `stdlib` — `Functions.exportRegistry()` and the `@UTLXFunction` annotations it scans;
surfaced by `modules/cli` `FunctionsCommand`
**Found:** 2026-11-23, while fixing a single case of exactly this. Not the first sighting:
[B50](B50-join-is-the-string-join-innerjoin-is-relational.md) measured "83 callable but
undocumented" and 8 documented-but-not-callable names, and recorded under *Still open* that
"nothing cross-checks the generated catalogue against the dispatch registry". This is that check.

## Symptom

Two opposite failures, from one cause.

**Callable but unadvertised — 36 functions:**

```
$ utlx -e 'geoDistance(52.37, 4.89, 48.85, 2.35)'
430.4238536539036   ← works

$ utlx functions info geoDistance
Function 'geoDistance' not found
```

**Advertised but not callable — 11 functions.** The mirror defect: the catalogue is where a user
goes to learn a name, and it named things that do not exist.

```
$ utlx functions info endTimer
endTimer - Logs elapsed time since startTimer()        ← documented

$ utlx -e 'endTimer(t)'
Error: Undefined function: endTimer       ← the callable spelling is endDebugTimer
```

Five of the eleven diverged by more than case (`endTimer`/`endDebugTimer`,
`startTimer`/`startDebugTimer`, `ifThenElse`/`if`, `isLeapYearFunc`/`isLeapYear`,
`equals`/`equalsBinary`), so the real name could not be looked up *and* the advertised one could
not be called — the gap ran both ways on the same function. The other six differed only in case,
and `utlx functions info` happens to match case-insensitively (`GEODISTANCE` resolves), so those
were survivable **at the prompt**. They were not survivable in the data: `utlx functions
--format json` and the IDE's function builder consume exact names, which is precisely how the
`round` regression below came to light.

## Cause

`utlx functions` does **not** reflect over the live registry. It reads a snapshot generated at build
time by `:stdlib:generateFunctionRegistry` → `StandardLibrary.exportRegistry()`, which scans
`@UTLXFunction` annotations over a hand-maintained list of function objects.

So UTL-X keeps **two independent lists of its own functions**: what `register("name", …)` makes
callable, and what carries an annotation. Nothing compared them, so they drifted — both ways.

The 36 missing had two distinct causes. Most were plain omissions; the largest single block was
`util/AdvancedUtilities.kt`, where four *registered* objects (`ValueFunctions`, `DiffFunctions`,
`MimeFunctions`, `MultipartFunctions`) carried **no annotations at all** — 14 functions invisible
because one file was never annotated.

The 11 phantoms had a sharper cause: the exporter published `member.name`, the **Kotlin method**
name. Wherever the registered spelling differed, the catalogue advertised a name that does not
exist. The annotations themselves already knew better — `createQName`'s own `example` field reads
`createQname(…)`, the callable spelling, beside an exported name of `createQName`.

Three of the eleven are worse than a spelling difference:

| registered   | Kotlin method                       | and `getPath`/`log`/`reverse` is also… |
|--------------|-------------------------------------|----------------------------------------|
| `getURLPath` | `URLFunctions::getPath`             | `CriticalObjectFunctions::getPath`     |
| `logBase`    | `AdvancedMathFunctions::log`        | `DebugFunctions::log`                  |
| `reverseString` | `MoreStringFunctions::reverse`   | `ArrayFunctions::reverse`              |

Two *different* functions share a method name, so exporting the method name made the catalogue list
one name twice and the other never. The source comments say as much — `// Renamed to avoid conflict
with object getPath` — the rename reached `register(...)` and never reached the export.

## Fix

`@UTLXFunction` gained a `name` field, and the exporter honours it:

```kotlin
allFunctions.add(convertAnnotationToFunctionInfo(
    annotation.name.ifEmpty { member.name }, annotation))
```

Empty means "same as the method", which stays the normal case. Plus 14 annotations added to
`AdvancedUtilities.kt`, and 26 alias spellings converted from `register` to
`registerAlias(alias, canonical, reason)`, which makes a name callable *and* catalogued in one
statement.

Four of the eleven were then fixed properly — **at the source, not with an override** — because
their divergence turned out to be accidental:

- **`createQName` · `resolveQName` · `matchesQName`.** QName is a *qualified* name, so the N is
  capital: the spelling TIBCO BusinessWorks uses, the one the Kotlin methods already had, and the
  one `docs/stdlib/stdlib-complete-reference.md` already documented. Only `register` said `Qname`,
  which is what made the documented name uncallable. `register` now says `QName`, with the old
  spelling kept callable as a deprecated alias so existing scripts keep working — and the three
  overrides are gone, because method and registered name agree again.
- **`if`** was not a naming problem at all: it is unreachable by any spelling, because the parser
  owns the keyword. Split out as
  [B70](B70-the-catalogue-advertised-if-which-the-parser-can-never-reach.md) and now registered
  as `conditional`.

That leaves **seven** overrides, and they divide cleanly: four more are accidental and fixable the
same way, while three (`getURLPath`, `logBase`, `reverseString`) are *structural* — two objects
legitimately own one method name and UTL-X has a single flat namespace, so a mapping must exist
somewhere. [F34](../features/F34-the-registration-should-be-the-only-place-a-function-name-is-written.md)
carries that analysis and the proposal to make the registration the only place a name is written.

Result: **733 catalogue entries / 723 unique**, zero callable-but-missing, zero
advertised-but-absent.

## Two counting mistakes worth recording

This report first claimed **51**. It was wrong twice, the same way both times, and the mistake is
more instructive than the fix.

The first count swept up 15 "registrations" that are **commented out**:

```kotlin
// register("geo-distance", GeoFunctions::distance)
```

A commented registration is not callable (`Error: Undefined function: geo-distance`), so it was
never a gap — 51 became 36.

Then the *reconciled* audit still showed 14 missing: 9 hyphenated UUID names and 6 DataWeave
aliases (`sizeOf`, `orderBy`, `namesOf`, …). Those sit inside `/* */` **block** comments — in
`UUIDFunctions.kt` they are a commented-out *proposal* ("In registerCoreFunctions() or create new
registerUUIDFunctions()"), and in `DataWeaveAliases.kt` the block is explicitly labelled
"commented out for now as it needs StandardLibrary access". An extractor that skipped `//` lines
but not `/* */` blocks counted all 14 as callable.

Both times the error was the same: **treating the source text as the authority on what is callable.**
The authority is the running program. Every candidate here was settled by invoking it, and the
test that now guards this uses `StandardLibrary.getFunctionNames()` — the live map — rather than
any regex over source.

## How this was found, and why it matters beyond discoverability

`round` went missing from the catalogue during the
[B67](B67-round-rounds-the-wrong-way-on-an-inexact-half.md) fix. A private helper was inserted
between `@UTLXFunction` and `fun round`; Kotlin binds an annotation to the **next declaration**, so
the annotation described the helper and `round` lost its own. `round(2.675, 2)` kept working while
`utlx functions info round` reported it missing.

The exported total stayed at **exactly 705 before and after** — the annotation was not lost, it was
*reassigned*, with a private helper occupying `round`'s place. No count-based check could notice it,
and it was caught only because a human saw the IDE's function builder and the CLI disagree.

That is the real cost: a drifting catalogue cannot act as a control. With the two lists reconciled
and asserted, that regression now fails the build.

## Verification

`stdlib/src/test/kotlin/.../CoreFunctionsAreDiscoverableTest.kt` now asserts the **full invariant**
in both directions, against the live registry:

- `every callable function appears in the catalogue` — the 36 gap;
- `every catalogued function is callable` — the 11 phantoms;
- `a private helper never reaches the catalogue` — the B67 slip;
- `no new ambiguous catalogue entries` — ratchets [B69](B69-one-name-several-signatures-in-the-catalogue.md).

Mutation-proven, and usefully the two divergence kinds fail *differently*:

```
# remove name = "createQname"  (a case-only divergence; override since superseded by the rename)
every callable function appears in the catalogue  FAILED  … : [createQname]
every catalogued function is callable             FAILED  … : [createQName]

# remove name = "getURLPath"   (a collision with CriticalObjectFunctions::getPath)
every callable function appears in the catalogue  FAILED  … : [getURLPath]
no new ambiguous catalogue entries                FAILED  … getPath catalogued twice
every catalogued function is callable             PASSED  ← `getPath` IS callable, so no phantom
```

The second case is why the duplicate ratchet is not redundant with the phantom check: when the
method name a collision falls back to happens to be a real function, nothing is advertised that
cannot be called — the damage shows up only as one name carrying two meanings. B50 predicted
exactly this: *"a name present in both but meaning different things … is invisible to a name-level
diff."*

`validate.*` is excluded on both sides: it is the `%utlx 1.1` namespace, version-gated out of the
default catalogue by F21, and catalogued under its *bare* method name ("assert", not
"validate.assert"), so it cannot take part in a name-for-name comparison at any version. That
naming inconsistency is pre-existing and left alone here.

## Deliberately not fixed here

- **Nine ambiguous entries** — one name, several signatures. Split out as
  [B69](B69-one-name-several-signatures-in-the-catalogue.md): four involve a name registered
  *twice* from different objects, so resolving them changes which implementation runs.
- **The 15 commented-out hyphenated XSLT spellings** (`geo-distance`, `analyze-string`, …). They
  cannot be called today in any case: a hyphen lexes as subtraction. Enabling them is a language
  question, not a catalogue one.
- **Generating the catalogue from the registry** instead of from annotations, so that `register(...)`
  is the single authority and a missing annotation is a reportable hole rather than an invisible
  omission. That removes the whole class of bug; the invariant test above makes it detectable
  meanwhile, which was the urgent part.
