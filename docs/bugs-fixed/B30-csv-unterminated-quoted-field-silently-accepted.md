# B30: CSV parser silently accepts an *unterminated* quoted field (reads to EOF instead of erroring)

**Status:** **OPEN** — found 2026-10-10 by the `test-corpora` per-profile baseline (Phase 0b).
**Priority:** Medium (correctness; fails **all three** profiles — genuinely malformed, not benign).
**Created:** October 2026
**Component:** `formats/csv/src/main/kotlin/formats/csv/com/glomidco/utlx/formats/csv/csv_parser.kt`
— `parseQuotedField()` (~L154–167).

> **One-line:** a quoted field with no closing quote (`1,"alice⏎`) is **accepted** — the parser reads
> to end-of-input and returns the partial field, swallowing the newline and everything after it into
> one field value, instead of raising an "unterminated quoted field" error.

## Problem

`parseQuotedField()` consumes the opening quote, then loops `while (!isAtEnd())` appending characters;
a closing quote ends the field (with quote-doubling handled). But if **EOF is reached before a closing
quote**, the loop simply exits and the accumulated text is returned as the field — there is **no
end-of-input check** for the still-open quote. So malformed CSV parses "successfully."

## Reproduction

Corpus case `test-corpora/corpora/malformed/csv/unclosed_quote.csv`:
```
id,name
1,"alice
```
Baseline probe (identity transform) → **accepted** (exit 0). All three oracles (`strict`, `standard`,
`lenient`) expect **reject** (`reason: unclosed-quote`), so it is a GAP in every profile in
`test-corpora/baseline.json`.

## Impact

- **Accepts malformed CSV** — an unterminated quote is not a benign deviation; it is broken structure.
- **Content smuggling / corruption** — everything after the stray `"` (including subsequent lines) is
  absorbed into a single field until EOF. Downstream a field can silently contain what should have been
  later rows. For a guard this is exactly the "accept-and-mangle" failure mode fail-closed must prevent.

## Fix

- On EOF inside `parseQuotedField()` with the quote still open → **throw a parse error** ("unterminated
  quoted field at line/col"), matching the XML parser's `Unterminated DOCTYPE` style.
- This is a **reject in every profile** (genuinely broken), not a leniency dial — so it is not
  profile-gated; fix it outright.
- Add a positive case (properly closed quoted field with embedded delimiter/newline) and this negative
  case to lock the behaviour.

## Related
- `docs/architecture/parser-strictness-profiles.md` §4 (behaviour-on-deviation: reject with reason).
- `docs/architecture/utlx-test-corpora.md` §5.7 (malformed input). `test-corpora/baseline.json`.
- **B29** (XML undefined-entity / DOCTYPE passthrough) — same class: a reader *accepts malformed input*
  that every profile should reject.
