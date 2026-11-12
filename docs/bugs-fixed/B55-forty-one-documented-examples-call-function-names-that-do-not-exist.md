# B55 — 41 documented examples call function names that do not exist

**Status:** Fixed — 2026-11-12 (41 of 41; the 2 dead functions removed)
**Component:** `:stdlib` — `@UTLXFunction(example = …)` across 12 files
**Found:** 2026-11-12, while establishing whether hyphens in identifiers are used for anything
**Severity:** Low — documentation only; nothing misbehaves at runtime, but every one of these
examples fails if a user copies it, and the set is identical on `main`

## Summary

**41** `@UTLXFunction` annotations carry an `example` whose callee is not a registered
function — **identically on `main` and on `development`**, so this ships:

| branch | live `register()` names | broken example callees |
|---|---|---|
| `main` | 724 | **41** — 37 kebab-case, 4 camelCase, 0 PascalCase |
| `development` | 743 | **41** — the same set |

```kotlin
@UTLXFunction(example = "base64-encode(\"Hello World\")", …)
```
```bash
utlx -e 'base64-encode("Hello")'   # Error: Undefined function: base64-encode
utlx -e 'base64Encode("Hello")'    # "SGVsbG8="
```

### None of them can be a user-defined function

The obvious objection is that an example might legitimately reference a *user-defined*
function, which would not be in the stdlib registry. It cannot be, and the language enforces
it:

```utlx
function Double(x) { x * 2 }    // ok
function double(x) { x * 2 }    // Parse error: User-defined functions must start with uppercase
function myDouble(x) { x * 2 }  // Parse error: same
```

All 41 callees begin with a **lowercase** letter, so none is a legal user-defined name, and
none is registered. They resolve nowhere. Confirming this from the other direction:
**0** of the unresolved callees are PascalCase — so the audit has no false positives of that
kind. (Note `function My-Func(x)` *is* legal and works, which is the one place hyphens in
identifiers are productively usable — relevant to
[B54](B54-hyphens-in-identifiers-implementation-and-docs-disagree.md).)

### Two distinct classes

**37 kebab-case — spelling drift.** Every one resolves if you normalise case and strip
separators, i.e. they are the XPath-style spelling of a function that exists under
camelCase:

| example says | actually registered as |
|---|---|
| `base64-encode` / `base64-decode` | `base64Encode` / `base64Decode` |
| `child-count` / `child-names` | `childCount` / `childNames` |
| `compare-dates` | `compareDates` |
| `create-qname` / `get-namespaces` | `createQname` / `getNamespaces` |
| `element-path` / `has-attribute` | `elementPath` / `hasAttribute` |
| `format-number` | `formatNumber` |
| `generate-uuid` | `generateUuid` |
| `hex-encode` / `hex-decode` | `hexEncode` / `hexDecode` |

**4 camelCase — not drift, genuinely absent.** These do *not* normalise to anything:

| example says | status |
|---|---|
| `startTimer`, `endTimer` | **no near match in 728 names** — documented in `DebugFunctions.kt`, never implemented or registered |
| `equals` | renamed — the registry has `binaryEquals`, `equalsBinary`, `deepEquals`, `jsonEquals`, `c14nEquals` |
| `name` | renamed — the registry has `localName`, `childNames`, `getNamespaces` |

### There is no case-insensitive alias mechanism to lean on

Worth stating, because it is the natural assumption. Across all 728 registered names there
is exactly **one** pure case-variant pair: `typeOf` / `typeof`. Everything else is a distinct
registration, and the naming is inconsistent:

```bash
lower("AB")      # "ab"     registered
lowerCase("AB")  # "ab"     also registered — a separate name, not an alias rule
lowercase("AB")  # Error: Undefined function
upper / upperCase    # both registered
uppercase            # not registered
```

So `lower` and `lowerCase` coexist while `lowercase` does not; `isLowerCase` and
`isUpperCase` exist too. Nothing normalises a lookup, which is why all 37 kebab spellings
fail rather than falling through to their camelCase twin.

## Why it was not caught

Same shape as [B50](B50-join-is-the-string-join-innerjoin-is-relational.md), which found the
`notes` field quoting a name the function no longer had. The annotation's prose fields —
`example`, `notes`, the `requireArgs` label — are **free text that nothing executes or
checks**. The generated catalogue is keyed on the Kotlin function name, so an `example`
naming something else passes every build.

B50 audited `notes` across all 703 annotations. `example` was not audited at the same time,
which is why this survived.

### One methodology trap, for whoever fixes it

`register("…")` occurrences must be counted with `/* */` blocks **stripped**.
`UUIDFunctions.kt:480-520` contains an instructional comment block — "Add to
`stdlib/.../Functions.kt`" — holding a dozen `register(...)` lines including
`register("generate-uuid", …)` and seven UUID-v7 functions. None of it is live:
`generate-uuid()` and `generate-uuid-v7()` are both `Undefined function`, while
`generateUuidV7()` works. Counting those comments as registrations undercounts this defect
by one and hides a second finding — a wiring instruction written and never carried out.

## The fix

**39 of 41 corrected, by hand, one function at a time.** Not scripted — a first attempt with a
bulk rewriter is what proved the manual route necessary:

- it renamed `fun endTimer(` to `fun endDebugTimer(`, breaking
  `register("endDebugTimer", DebugFunctions::endTimer)`, because the dead spelling and the
  Kotlin declaration are the same string;
- it relabelled `CoreFunctions.generateUuid`'s example to `generateUuid` — a name registered
  to a **different** implementation — so dead code was made to look correct;
- it would have rewritten `"exc-c14n"`, `"c14n-with-comments"`, `"hello-world"`, `"secret-key"`
  and `"yyyy-MM-dd"`, which are **string arguments**, not function names.

Each name occurs in up to five places per function, and only one of them — the Kotlin `fun`
name — is checked by the compiler. All five were corrected where they named the function:
`example`, `notes`, the KDoc `Usage:`/fenced block, the `requireArgs` label, and any error
message text.

| file | functions | note |
|---|---|---|
| `EncodingFunctions.kt` | 8 | also 2 error-message strings; `"secret-key"` left as data |
| `XmlUtilityFunctions.kt` | 9 | also a code comment and an error message |
| `QNameFunctions.kt` | 9 | `resolveQName`→**`resolveQname`**, `createQName`→**`createQname`**, `matchesQName`→**`matchesQname`** — the registered names differ in case from the Kotlin ones; and `name`→`qualifiedName` |
| `ExtendedStringFunctions.kt` | 6 | `"hello-world"`, `"-"`, `"a/b/c.txt"` left as data |
| `ExtendedDateFunctions.kt` | 2 | `validateDate` had 5 further occurrences in error text |
| `ExtendedMathFunctions.kt` | 2 | `parseFloat` deliberately skipped — see below |
| `BinaryFunctions.kt` | 1 | `equals`→`equalsBinary` in example, notes, KDoc and error text |
| `DebugFunctions.kt` | 2 | `startTimer`→`startDebugTimer`, `endTimer`→`endDebugTimer` |
| `XMLCanonicalizationFunctions.kt` | 1 | `canonicalize`→`canonicalizeWithAlgorithm`; the seven algorithm identifiers left as data |

**No `fun` declaration was touched** — asserted after every file, since that is the one edit
that silently breaks a registration.

## The 2 dead functions, removed

These were **not** naming drift and could not be relabelled: in both cases the camelCase name
the example would be corrected to is registered to a *different* implementation, so
"correcting" it would have made unreachable code claim a live function's name. Both functions
were deleted instead, each leaving a comment where the code was:

| annotation | why it is dead | the live owner of that name |
|---|---|---|
| `CoreFunctions.generateUuid` | registered nowhere; the only reference is inside an instructional `/* */` block in `UUIDFunctions.kt` that was never carried out | `UUIDFunctions::generateUuidV4` |
| `ExtendedMathFunctions.parseFloat` | superseded deliberately by **B25** — "was previously registered from ExtendedMathFunctions with a plain `toDouble()` that failed on commas" (`Functions.kt:520`) | `ConversionFunctions::parseFloat` |

`UUIDFunctions.kt` also carried two notes reading "This is the same as the existing
CoreFunctions::generateUuid"; they now say the name was taken over from there.

### What the deletions cost, recorded rather than lost

Both removed functions had an **arity guard** that the live replacements do not:

| removed | guard | live behaviour now |
|---|---|---|
| `CoreFunctions.generateUuid` | `requireArgs(args, 0, …)` — rejected arguments | `generateUuid(1,2,3)` silently returns a UUID |
| `ExtendedMathFunctions.parseFloat` | `requireArgs(args, 1, …)` | `ConversionFunctions.parseFloat` delegates to `parseNumber`, no arity check |

That laxness arrived when the registrations were re-pointed, not with this change. The
assertions that covered it are removed with a comment at each site explaining why there is no
equivalent behaviour to assert.

### Both had tests, which is why "unused" was not the right word

A first reference check looked only for the `::` method-reference form and reported *no
references* — wrong. Both were called from unit tests in the dot form:

- `ExtendedMathFunctionsTest.testParseFloat` — 11 value assertions including scientific
  notation, `Infinity`, `-Infinity`, `NaN`, very large and very small values. **Re-pointed** to
  `ConversionFunctionsTest`, against the live implementation, rather than discarded — plus two
  new assertions for what B25 actually bought (`parseFloat("1,234.56")` and
  `parseFloat("$1,234.56")`, which the removed `str.toDouble()` threw on).
- `ExtendedMathFunctionsTest.testRoundTripOperations` — re-pointed to the live `parseFloat`.
- `CoreFunctionsTest.testGenerateUuid` — **removed**, not moved: `UUIDFunctionsTest` already
  covers the live implementation and covers it more strictly, asserting the v4 version nibble
  and variant bits (`-4[0-9a-f]{3}-[89ab]…`) that this test's regex did not check.

A related correction: `parseFloat("42")` renders as `42` through the CLI and looks like an
integer, which suggested the replacement had changed the return type. It has not —
`ConversionFunctionsTest` asserts `42.0` as a Double and passes. The `42` is the serialiser's
integral rendering (B37).

## Verification

| | |
|---|---|
| `:stdlib:test` | 2328, 0 failures |
| `:modules:core:test` | 342, 0 failures |
| `:modules:cli:test` | 136, 0 failures |
| `:modules:engine:test` | 1013, 0 failures |
| conformance (JVM) | 574/574 |
| conformance (**native**) | 574/574 in 5.4s |
| `fun` declarations changed | **0** |
| registrations still resolving | all (`DebugFunctions::startTimer`, `::endTimer`, `BinaryFunctions::equals` checked explicitly) |

Every corrected example was then run through the CLI as written:

```bash
base64Encode("Hello")                  => "SGVsbG8="
hexDecode("48656c6c6f")                => "Hello"
substringBefore("hello-world","-")     => "hello"
normalizeSpace("hello    world")       => "hello world"
formatNumber(1234.56,"#,##0.00")       => "1,234.56"
validateDate("2025-10-14T00:00:00Z")   => true
xmlEscape("<t>")                       => "&lt;t&gt;"
```

And the arity message now names something callable:

```
$ utlx -e 'base64Encode()'
Error: base64Encode expects 1 argument(s), got 0.
```

### The GraalVM run earned its keep

Re-verified on a freshly built native binary, because deleting code adjacent to the
registration tables can shift native-image reflection config — B19 was exactly that. All 41
renamed functions and both deleted ones behave identically to the JVM, and the corpus is
574/574 natively.

It also caught a miss the JVM suites did not: `qualifiedName` still reported
`Error: name requires an XML element (object)` from a `FunctionArgumentException` in its body —
a *fourth* occurrence of the old spelling in that function, beyond the `example`, the KDoc and
the `requireArgs` label. Nothing asserts error text, so only reading the message surfaced it.
Fixed, rebuilt, re-verified:

```
$ utlx -e 'qualifiedName(42)'
Error: qualifiedName requires an XML element (object), but got number.
```

## The guard

`B55AnnotationExampleNamesTest` (`:stdlib`, 3 tests) reads the Kotlin **sources** — the
generated catalogue does not carry `example` at all, which is why nothing checked these — and
asserts against `StandardLibrary.getAllFunctionNames()` rather than a list, as
`B52ValidateFunctionNamesTest` does:

1. every `example` callee is registered — 688 checked;
2. every function-shaped `requireArgs` label is registered — 303 checked;
3. no `example` callee is kebab-case at all, pinned separately so the reason stays legible.

It blanks `/* */` blocks **length-preservingly** before scanning, because `UUIDFunctions.kt`
holds a dozen `register(...)` lines inside a comment; counting those as registrations would
certify dead spellings as valid. The two dead-code annotations above are listed as explicit
exemptions with their reasons, so the other ~686 stay guarded and a *new* offender fails
instead of hiding behind them.

### Not fixed here: the metadata is template-generated

Visible on every annotation touched: `parameters = ["array: Input array to process"]` on a
string function, `returns = "Result of the operation"`, and `example = "abs(...) => result"`.
The catalogue has `abs` as `abs(num: Num value, exp: Exp value) => Any` with
`minArgs: 1, maxArgs: 1` — two parameters declared for a one-argument function. Corrected in
passing only where a function was already being edited (`equalsBinary`, `endDebugTimer`);
the remaining ~700 are a separate job.
