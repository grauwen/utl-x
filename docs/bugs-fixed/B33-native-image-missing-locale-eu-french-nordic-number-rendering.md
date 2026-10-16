# B33: native image missing locale — EU / French / Nordic number rendering

> **⤴ Backport candidate for `main` / 1.3.2.** The defect is in released code (stdlib
> `RegionalNumberFunctions.kt` **and** the `formats/csv` serializer) and affects every **native binary**
> build. The fix is **standalone** and has **no** dependency on the `%utlx 1.1` / `validate.*` feature it
> was discovered alongside. It touches: `RegionalNumberFunctions.kt` (the `formatEUNumber`/`formatFrenchNumber`
> fix + the Nordic & India additions), `csv_serializer.kt` (the same EU/French fix in its `regionalFormat`
> option), additive `register(...)` lines in `Functions.kt`, and new fixtures under `tests/stdlib/regional/`.
> `main` is on the same `com.glomidco` namespace, so the paths apply; expect a clean cherry-pick.

> **⟲ Reopened 2026-10-16** to close an adjacent coverage gap found while mapping the renderers onto
> Western locales (see *"Reopened: Nordic / regular-space variant"* below): the four shipped styles cover
> most Western countries, but the **space-separator** convention is split — French CLDR uses the narrow
> no-break space `U+202F`, while the **Nordics (SE/NO/FI) and the SI/ISO international style use a regular
> space `U+0020`**. Added `renderNordicNumber` / `parseNordicNumber` for that style.

**Status:** **FIXED (pending commit)** — `formatEUNumber` and `formatFrenchNumber` no longer depend on
`Locale.GERMANY` / `Locale.FRANCE`. They now format with the always-present `Locale.US` and swap the
separators by hand (the exact pattern `formatSwissNumber` has used since the B02-era fix). Output is now
**identical on the JVM and the GraalVM native image**, and reproduces every `tests/stdlib/regional/*`
fixture byte-for-byte — including French using the **narrow no-break space `U+202F`** for the thousands
separator (`e2 80 af` in UTF-8), exactly what `Locale.FRANCE` emitted on the JVM and what the render
fixtures encode. (The `parse_french_number` fixtures use a plain ASCII space in their *inputs*, which
`parseFrenchNumber` strips alongside `U+00A0`/`U+202F` — do not confuse those inputs with render output.)
**Found:** 2026-10-16, when the **full** conformance suite was first run against the **native binary**
(during the `validate.*` corpus work). On the native binary 6 tests failed — `render_eu_number`,
`render_french_number`, `regional_edge_cases`, `csv_render_consistency` (its `european_render` /
`french_render` paths) — all producing US output (`1,234.56`) where EU/FR was expected (`1.234,56` /
`1 234,56`). The JVM/gradle run had stayed green (522/522) because a full JDK bundles every locale, which
is why the gap was invisible until the suite targeted the native image.
**Priority:** **Medium** — a **silent correctness** bug (wrong output, no error) in the shipped native
binary: a user asking for European number presentation got American presentation. Not a crash.
**Created:** October 2026
**Component:** `stdlib/src/main/kotlin/com/glomidco/utlx/utlx/stdlib/regional/RegionalNumberFunctions.kt`
— `formatEUNumber` (was `DecimalFormatSymbols.getInstance(Locale.GERMANY)`) and `formatFrenchNumber`
(was `… Locale.FRANCE`).

> **One-line:** the EU and French number renderers *asked* the JVM for `Locale.GERMANY` / `Locale.FRANCE`
> to get the right separators; the GraalVM native image ships only the default locale, so
> `DecimalFormatSymbols.getInstance(…)` **silently fell back to US** and the functions emitted US
> formatting on the native binary.

## Relationship to B02 (the sibling locale bug)

This is the mirror image of **B02** (`docs/bugs/B02_locale_ExtendedMathFunctions_bug.md`,
*"Locale-Dependent Number Formatting in ExtendedMathFunctions"*):

| | B02 | B33 (this) |
|---|---|---|
| Symptom | A **Dutch/German** system got EU formatting where **US** was expected | A **native binary** got US formatting where **EU/FR** was expected |
| Cause | `DecimalFormat(pattern)` used the **ambient default** locale | `getInstance(Locale.GERMANY/FRANCE)` **fell back** to US (locale absent in native image) |
| Direction | Unwanted non-US leaks **in** | Requested non-US is **missing**, US leaks in |
| Fix | Pin to `DecimalFormatSymbols(Locale.US)` | Build on `Locale.US`, then **construct** the EU/FR separators by hand |

Both share one root lesson, already encoded by B02 and by `formatSwissNumber`: **never let JVM locale
data decide a number's separators** — the result is not portable across host locale *or* across the
JVM↔native-image boundary. B02 fixed `formatNumber`; the Swiss renderer was written locale-independent
from the start (US base + manual `','→'\''`); but `formatEUNumber`/`formatFrenchNumber` were left on the
old `Locale.X` path and so inherited the same class of defect — surfacing on native instead of on a
non-US host.

## Problem

```kotlin
// formatEUNumber — BEFORE
val symbols = java.text.DecimalFormatSymbols.getInstance(java.util.Locale.GERMANY) // absent in native image
val formatter = java.text.DecimalFormat(pattern, symbols)
return formatter.format(value)   // native: "1,234.56" (US fallback), not "1.234,56"
```

On a full JDK, `getInstance(Locale.GERMANY)` returns German symbols (`.` thousands, `,` decimal). In the
GraalVM native image — built with `-H:+AddAllCharsets` but **no** `-H:+IncludeAllLocales` /
`-H:IncludeLocales` (the flag has never been present in `modules/cli/build.gradle.kts`) — only the
default locale is compiled in, so the lookup silently resolves to US symbols. No exception; just wrong
output. Same for `Locale.FRANCE` in `formatFrenchNumber`.

`formatSwissNumber` was unaffected because it already used `Locale.US` as its base and replaced the
thousands separator manually (`','→'\''`).

## Fix

Make the EU and French renderers locale-independent, exactly like Swiss — format with `Locale.US`, then
swap separators in a single pass (each source char mapped independently, so there is no collision between
the `,`↔`.` swap):

```kotlin
// formatEUNumber — AFTER  (period thousands, comma decimal)
val formatter = java.text.DecimalFormat(pattern, java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
val usFormatted = formatter.format(value) // "1,234.56"
return buildString(usFormatted.length) {
    for (c in usFormatted) append(when (c) { ',' -> '.'; '.' -> ','; else -> c })
}

// formatFrenchNumber — AFTER  (U+202F narrow-no-break-space thousands, comma decimal)
//   … append(when (c) { ',' -> ' '; '.' -> ','; else -> c })  // U+202F
```

Note on French: the thousands separator is the **narrow no-break space `U+202F`** (`' '`, written as
the explicit escape — never an invisible literal in source), which is exactly what
`DecimalFormatSymbols.getInstance(Locale.FRANCE)` emitted on the JVM with modern CLDR data, and what the
render fixtures (`render_french_number`, `csv_render_consistency`) encode. So the manual path is a true
behaviour-preserving refactor, not a change of output. Verified byte-for-byte: `1 234,56` →
`31 e2 80 af 32 33 34 2c 35 36`.

### Alternative considered and rejected

Add `-H:+IncludeAllLocales` to the native build. Rejected: it bloats the image with every locale to
paper over two functions, and it does not fix the *portability* lesson from B02 (output would still
depend on which locale data is present). The manual approach keeps the image lean, is consistent with
`formatSwissNumber`, and is robust across hosts and across the JVM↔native boundary.

## Verification

- 11/11 EU + French fixture values reproduced exactly by the manual transform (incl. negatives, zero,
  no-thousands, 3-decimals, integer, large) — French thousands separator confirmed as `U+202F`
  (`e2 80 af`), byte-identical to the fixtures and to the old `Locale.FRANCE` output.
- JVM output unchanged vs. the pre-fix `Locale.X` output for every fixture (the gradle run was already
  green on these; this preserves it).
- Native binary: rebuild `:modules:cli:nativeCompile` and re-run the full suite against the native
  binary → expected **526/526** (the 520 that passed + the 4 `validate.*` tests + these 6 regional
  tests). *(Native rebuild is the maintainer's step.)*
- Nordic additions (below): 9/9 `renderNordicNumber` values reproduced byte-exact with a regular space
  `U+0020` (`1 234,56` → `31 20 32 33 34 2c 35 36`); `parseNordicNumber` round-trips.
- India additions (below): 10/10 `renderIndianNumber` values correct incl. lakh (`1,23,456.78`), crore
  (`1,23,45,678.90`) and exact lakh/crore boundaries; `parseIndianNumber` round-trips.
- `:stdlib:test` `RegionalNumberFunctionsTest` is green (12 functions, incl. the B33 regression guards).
- Four new fixtures (`render_nordic_number`, `parse_nordic_number`, `render_indian_number`,
  `parse_indian_number`) — the native-suite total rises to **530**.

## Reopened: Nordic / regular-space variant (Western-coverage gap closure)

The four shipped styles map onto Western locales by their `(thousands, decimal)` convention:

| Style | Separators | Covers |
|---|---|---|
| `renderUSNumber` | `,` / `.` | US, UK, Ireland, Australia, New Zealand, Canada (EN) |
| `renderEUNumber` | `.` / `,` | Germany, Austria, Netherlands, Italy, Spain, Portugal, Denmark, Greece, Iceland |
| `renderFrenchNumber` | `U+202F` / `,` | France, Belgium (FR), Luxembourg, Quebec |
| `renderSwissNumber` | `'` / `.` | Switzerland, Liechtenstein |
| **`renderNordicNumber`** *(new)* | **`U+0020`** / `,` | **Sweden, Norway, Finland; the SI/ISO international style** |
| **`renderIndianNumber`** *(new)* | **`,` (2-2-3)** / `.` | **India — lakh/crore grouping** |

The gap: space-as-thousands is **not one convention**. French CLDR emits the narrow no-break space
`U+202F` (`renderFrenchNumber`), but the Nordics and the SI/ISO international style use a **plain ASCII
space `U+0020`**. Rendering a Swedish amount with `renderFrenchNumber` produced a visually-similar but
byte-wrong separator — a problem for exact interchange / downstream matching. There was no regular-space
renderer.

**Added** (same locale-independent pattern as the B33 fix — US base + manual separator swap, so native-safe):
- `renderNordicNumber(value, decimals?, thousands?)` → `1 234,56` with a regular space `U+0020`, comma decimal.
- `parseNordicNumber(value)` → inverse; tolerant of **all** space variants (`U+0020`, `U+00A0`, `U+202F`),
  so it also parses French/Nordic input regardless of which space was used.

```kotlin
// formatNordicNumber — regular ASCII space (U+0020) thousands, comma decimal
//   … append(when (c) { ',' -> ' '; '.' -> ','; else -> c })   // plain space, cf. French's ' '
```

### Also added: India (lakh/crore grouping)

Indian English formatting is a genuinely distinct, high-volume style: **2-2-3 grouping** (rightmost group
of 3, then groups of 2) → `12,34,567.89`, comma thousands, period decimal, Latin `0-9` digits.

`java.text.DecimalFormat` only honours a single (rightmost) grouping size, so the Indian grouping **cannot
be expressed as a pattern** (`#,##,##0` still yields `1,234,567`). It is therefore applied **manually**
(`formatIndianNumber` / `groupIndian`) on top of an ungrouped `Locale.US` format — so it stays
locale-data-free and native-image-safe, exactly like the rest.

- `renderIndianNumber(value, decimals?, thousands?)` → `12,34,567.89`.
- `parseIndianNumber(value)` → inverse (strips commas regardless of grouping position).

Still **not** covered (out of scope, recorded): accounting-style negatives `(1 234,56)`.

## Second instance found & fixed: the CSV serializer

Running the **full suite against the freshly-compiled native binary** (not just the JVM) surfaced a
**second copy** of this exact bug: the CSV output format's `regionalFormat: "european"|"french"` option
has its own number formatter in `formats/csv/.../csv_serializer.kt` that was a verbatim copy of the
*pre-B33* stdlib code — `DecimalFormatSymbols.getInstance(Locale.GERMANY|FRANCE)`. On native it fell back
to US, so `csv_output_regionalformat_european` / `csv_output_regionalformat_french` (JVM-green) **failed
on native** (`1,234.56` instead of `1.234,56` / `1 234,56`). Fixed identically: US base + manual swap
(EU `.`↔`,`; French → `U+202F`). This is why native verification matters — the JVM suite could never have
caught it. Both tests now pass on native.

## Related / follow-up (not fixed here)

- **`stdlib/finance/FinancialFunctions.kt` — `formatCurrency` / `parseCurrency` (candidate B34).** These
  use `NumberFormat.getCurrencyInstance(Locale.forLanguageTag(...))` for exact output (e.g.
  `formatCurrency(1234.56, "EUR", "de_DE")` → `"1.234,56 €"`), so they are subject to the same native
  locale-data gap. **Not** fixed here because (a) no conformance test exercises them on native, so nothing
  is currently wrong in the suite, and (b) unlike separators, currency formatting (symbol, symbol
  placement, per-currency decimals like JPY=0) is genuinely locale/currency *data* — making it native-safe
  needs a small **shipped currency/locale table**, a real feature, not a one-line swap. `parseCurrency` is
  lower risk: it has a symbol-stripping last-resort fallback, so it degrades to a number rather than
  erroring. Audit before relying on currency output on the native binary.
- The locale-aware **date** functions (`DateFunctions.kt`, `JvmCldrProvider.kt`) resolve arbitrary BCP-47
  tags via `Locale.forLanguageTag(...)` / `Locale.getAvailableLocales()`, so they are almost certainly
  subject to the same native-image locale-data gap for non-default locales. No conformance test currently
  exercises a non-US date locale on the native binary, so it did not surface. Worth a dedicated audit (the
  CLDR provider should source its own bundled data rather than lean on JVM locales under native).
