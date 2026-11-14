# CB05 — `utlx functions list --category` ignores the filter in text output

**Status:** Fixed — 2026-11-14
**Priority:** Medium — the filter silently did nothing; the JSON path was correct, so the two disagreed
**Component:** `:cli` (`commands/FunctionsCommand.kt`)
**Found:** 2026-11-14, while clearing compiler warnings after the 1.4.0 release

## Summary

`utlx functions list --category <name>` filters correctly for `--format json` and not at
all for the default text output, which prints the whole library — every category, 705
entries — while reporting the filter as accepted.

## Repro

```bash
$ ./utlx functions list --category Array | wc -l
754

$ ./utlx functions list --category Array --format json | python3 -c "import json,sys; print(len(json.load(sys.stdin)))"
66
```

The text output also opens with `UTL-X Standard Library Functions (705 total)`, so the
header is honest about what it is doing even though the command asked for one category.

## Root cause

`printTextList` is handed the filtered list and iterates the unfiltered registry instead:

```kotlin
private fun printTextList(functions: List<FunctionInfo>, registry: FunctionRegistry) {
    println("UTL-X Standard Library Functions (${registry.totalFunctions} total)")
    // ...
    registry.categories.entries.sortedBy { it.key }.forEach { (category, funcs) ->
        println("$category (${funcs.size}):")
        funcs.sortedBy { it.name }.forEach { func -> /* ... */ }
    }
}
```

`functions` — the filtered list the caller built — is never read. The compiler says so:

```
w: ...FunctionsCommand.kt:250 Parameter 'functions' is never used
```

## Why it was not noticed

The JSON path filters properly, and that is the path everything automated uses (the IDE,
the docs generator, the tests). The text path is the one a human types, and its output is
long enough that nobody counts it.

## Fixing it

Iterate `functions` instead of `registry.categories`, grouping by `category` for the
headings, and report the filtered count in the header rather than `registry.totalFunctions`.
The fix is small; it needs a CLI test asserting that a category listing contains only that
category, because nothing covers the text path today.

## The fix

`printTextList` now groups the list it is handed (`functions.groupBy { it.category }`) instead of
iterating `registry.categories`, and the header distinguishes the two cases:

```
utlx functions list                   ->  UTL-X Standard Library Functions (705 total)
utlx functions list --category Array   ->  UTL-X Standard Library Functions (66 of 705)
```

An empty result now says "No functions matched." rather than printing a header and nothing else.

Verified: `--category Array` prints 73 lines and exactly one heading (was 754 lines and 22
headings), matching the 66 the JSON path reports; an unfiltered listing still prints all 22
categories.

## Tests

`CB05FunctionsCategoryFilterTest` (8 tests, `:modules:cli`) pins both halves of the contract — the
filter is honoured **and** an unfiltered listing still shows everything, since "filter correctly"
is trivially satisfied by printing nothing. It also asserts the text and JSON paths agree on the
count, which is the invariant that was broken.

**Revert-verified:** restoring `registry.categories.entries` in `printTextList` fails four of the
eight — `filtering by category prints only that category`, `filtering by category does not print
the whole library`, `an unknown category prints no headings and says so`, and `text and json agree
on how many functions a category has`. The other four pass either way, which is correct: they
assert the unfiltered behaviour this fix must not change.

Two traps worth recording, because both produced a *wrong* test against a *correct* fix:

- Counting `"name":` in the JSON output does not count functions. Each entry's `parameters` array
  carries its own `name`, so a regex count returns 209 where the answer is 66. The test parses the
  JSON instead.
- Reading only the **first** category heading from the text output does not discriminate. On the
  buggy output "Array" sorts first, so its count matched the JSON count while the 705-function
  listing continued below it — the test passed against the bug. It now sums every heading, which is
  what makes it fail on revert.
