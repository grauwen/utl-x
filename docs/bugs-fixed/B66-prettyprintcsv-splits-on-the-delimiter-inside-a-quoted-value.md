# B66: `prettyPrintCSV` splits on the delimiter inside a quoted value

> **B = the shared language stack** (`:stdlib` — `serialization/PrettyprintFunctions.kt`).

**Status:** **FIXED (2026-11-23)** — split via `CSVParser.parseRows()`, option 1 below.
**Priority:** Medium — a public stdlib function that corrupts valid CSV, silently and visibly.
**Component:** `stdlib` — `PrettyPrintFunctions.prettyPrintCSV` / `prettyPrintCSVInternal`
**Found:** 2026-11-23, while implementing
[F33](../features/F33-output-presentation-and-the-table-view.md). The first implementation of
`--format table` composed the CSV serializer with this function; the composition failed, and the
cause was this.

## Reproduction

```utlx
prettyPrintCSV("rule,message\nccy-allowed,\"Field 'ccy' value 'GBP' is not one of [EUR, USD]\"")
```

```
rule        | message
---------------------
ccy-allowed | "Field 'ccy' value 'GBP' is not one of [EUR | USD]"
```

Two defects in one line:

1. the comma **inside the quoted value** became a column break, so one field rendered as two;
2. the surrounding **quotes survived** into the output, where they are not data — they were CSV's
   escaping, which an aligned view should consume.

The header says two columns and the row has three. Any value containing a comma is affected:
a message, an address, a list, a formatted number under a European regional profile.

## Cause

`prettyPrintCSVInternal` splits each line on the delimiter directly, with no CSV reader — so it has
no notion of quoting, escaped quotes, or embedded newlines. The function is documented as "Formats
a CSV string with aligned columns", which commits it to understanding CSV.

## Why it is worth fixing rather than documenting

It is **public**, it is in the catalogue, and the failure is not a refusal but a *wrong answer* that
looks plausible — the table renders, the columns line up, and one row quietly has an extra field.
Anyone using it for a report will believe it.

It also sits next to the right tool: `CSVParser` already handles quoting, and it is the parser the
rest of the product uses. The reason `prettyPrintCSV` does not use it is a module direction —
`:stdlib` would need `formats/csv` — which is the real decision in this fix.

## Fix options

1. **Use `CSVParser`** for the split. Correct by construction, and shares one definition of CSV with
   every other reader. Needs `:stdlib` → `formats:csv`; check that against the existing dependency
   direction (the `:stdlib` → `:core` rule is explicit, `formats` is not stated).
2. **A minimal quote-aware splitter** inside the function. No new dependency, but it is a second CSV
   reader in the codebase — the thing `looksLikeValidationResult` and `ScalarCanonical` exist to
   avoid elsewhere.

Option 1 is preferable unless the dependency is refused, in which case option 2 should say in a
comment that it is a deliberate duplicate and why.

Either way, **strip the quoting** as part of the fix: an aligned view should show the value, not
CSV's escaping of it.

## Not a blocker for F33

`--format table` does **not** use this function. It renders from the UDM directly, which is why it
handles a comma in a cell correctly:

```
rule        | message
---------------------
ccy-allowed | Field 'ccy' value 'GBP' is not one of [EUR, USD]
```

The composition was abandoned *because* of this bug, and the F33 KDoc records it so the shortcut is
not re-attempted.

## Fix as applied

**Option 1**, and the dependency turned out to be a non-issue: `:stdlib` already declares
`implementation(project(":formats:csv"))`, so no new edge was added.

`CSVParser` computed exactly the right thing internally — `parseRow()` resolves quoting, escaped
quotes and embedded delimiters — but only `parse(hasHeaders)` was public, returning UDM. So the
grid is now exposed directly:

```kotlin
fun parseRows(): List<List<String>>
```

`parse()` was refactored onto it, so there is one traversal and no duplicated loop.
`prettyPrintCSV` calls it, and falls back to the old naive split if the text will not parse as CSV
at all — it is a *formatter*, and throwing would turn a cosmetic problem into a broken report.

```
before   ccy-allowed | "Field 'ccy' value 'GBP' is not one of [EUR | USD]"
after    ccy-allowed | Field 'ccy' value 'GBP' is not one of [EUR, USD]
```

## Verification

`stdlib/src/test/kotlin/.../serialization/B66PrettyPrintCsvQuotingTest.kt` — 7 tests:

- `a delimiter inside a quoted value is not a column break` — counts the separators on the data
  row, because asserting the text alone would pass while the column count was wrong;
- `the quoting does not survive into the view` — asserts no `"` anywhere;
- `an escaped quote renders as one quote` — `""` → `"`, and asserts no `""` remains;
- `plain CSV aligns exactly as before`, `columns are padded to the widest cell`, `a single column
  still works` — the regressions;
- `unparseable input falls back rather than failing a report` — the formatter contract.

One pre-existing behaviour is asserted rather than changed: this function pads **every** cell
including the last, so lines can end in whitespace. Changing that inside a quoting fix would have
been a second, unrelated change. (F33's own renderer trims; the two deliberately differ.)
