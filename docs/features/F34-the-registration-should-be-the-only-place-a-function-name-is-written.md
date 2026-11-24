# F34: the registration should be the only place a function name is written

**Status:** Proposed — 2026-11-24.
**Component:** `stdlib` — `Functions.exportRegistry()`, `@UTLXFunction`
**Motivation:** [B68](../bugs-fixed/B68-working-functions-are-absent-from-the-catalogue.md),
[B69](../bugs-fixed/B69-one-name-several-signatures-in-the-catalogue.md),
[B70](../bugs-fixed/B70-the-catalogue-advertised-if-which-the-parser-can-never-reach.md)

## The problem, stated once

A UTL-X function's name exists in up to three places:

1. `register("createQName", QNameFunctions::createQName)` — decides what a user can **call**;
2. the Kotlin method name — what the exporter **published**, until B68;
3. `@UTLXFunction(name = "…")` — B68's fix, a hand-written copy of (1).

Only (1) is authoritative. (2) diverging from (1) caused B68's eleven phantom entries. And (3) is
a *third* place the same string is written, so it can drift too: nothing stops
`name = "createQnam"`. The two-way invariant test catches that, but a guard against a copy is not
as good as not having the copy.

B68's `name` field is therefore correct but not the end state. It is worth keeping only where the
divergence is **structural** rather than accidental — which, after the QName rename, is exactly
three functions.

## The two kinds of divergence

**Accidental (fixable at the source).** The registered name and the Kotlin name differ for no
reason that survives inspection:

| registered | method | why |
|------------|--------|-----|
| `createQName` · `resolveQName` · `matchesQName` | same | **fixed 2026-11-24** — `register` said `Qname`; everything else, including TIBCO BusinessWorks and our own reference doc, says `QName` |
| `startDebugTimer` / `endDebugTimer` | `startTimer` / `endTimer` | no clash; the method could simply be renamed |
| `equalsBinary` | `equals` | the method is called `equals` "to sit alongside its siblings" — but `equalsBinary` is the clearer Kotlin name anyway |
| `isLeapYear` | `isLeapYearFunc` | a genuine local clash with `private fun isLeapYear(year: Int)`; freeing the name means renaming the **private helper** |

Four annotations could lose their `name` this way. The QName row is **done** (2026-11-24): the
registration was corrected to `QName`, the old spelling kept as a deprecated alias, and three
overrides deleted. `conditional` (B70) was registered with the Kotlin method renamed to match, so
it never needed one.

**Structural (not fixable at the source).** UTL-X has one flat global function namespace; Kotlin
names live per object. Two objects can each legitimately own a method name, and only one of them
can have it globally:

| registered | method | collides with |
|------------|--------|---------------|
| `getURLPath` | `URLFunctions::getPath` | `CriticalObjectFunctions::getPath` |
| `logBase` | `AdvancedMathFunctions::log` | `DebugFunctions::log` |
| `reverseString` | `MoreStringFunctions::reverse` | `ArrayFunctions::reverse` |

Here a mapping *must* exist. The only question is where it lives — and the answer should be
"wherever the decision is made", which is the registration.

## Proposal: invert the exporter

Today `exportRegistry()` iterates a hand-maintained list of **classes**, scans annotations, and
infers the name. Instead, iterate the **registry**:

```kotlin
for ((name, impl) in functions) {                 // name is authoritative, by construction
    val meta = (impl as? Function<*>)?.reflect()  // kotlin.reflect.jvm
                 ?.findAnnotation<UTLXFunction>()
    emit(name, meta)                              // no meta ⇒ a visible hole, not an omission
}
```

What this buys:

- **phantoms become impossible** — a name can only be catalogued if it is registered;
- **omissions become impossible** — every registered name is emitted, with or without metadata;
- **`@UTLXFunction(name = …)` disappears**, and with it the third copy of the name. A rename in
  `register(...)` is reflected automatically;
- **`functionClasses` disappears** — a 71-entry hand-maintained list that is itself a source of
  drift (a whole file, `AdvancedUtilities.kt`, was simply never annotated: 14 of B68's 36);
- **B69's duplicates surface as what they are** — two names mapping to one implementation (a
  legitimate alias) versus one name registered twice (an ordering accident).

`register` already takes `(List<UDM>) -> UDM`, and Kotlin method references carry reflection
metadata, so `reflect()` recovers the declaration for `Obj::method`. The known gap is
`register("x") { … }` lambda registrations, which reflect to a synthetic function with no
annotation — they would show up as metadata-less entries, which is accurate: they have no
documentation today either.

## What this does *not* fix

B70: the registry cannot know that `if` is unreachable, because reachability is the **parser's**
answer, not the registry's. That stays a separate invariant — and it is the reason the test suite
now asks the parser directly rather than trusting any list.

## Sequencing

Not before Paris. The containment is already in place (the two-way invariant plus the parse check,
all mutation-proven), and this is a rewrite of the export path that would want its own test pass.
The four accidental renames above are independent and can be done at any time; they shrink the
`name` field from a general-purpose escape hatch to a three-case, clearly-motivated exception,
which makes its presence informative rather than suspicious.
