# CB06 — the parse profile is undiscoverable at the point of rejection

**Status:** Fixed — 2026-11-15
**Priority:** Medium — the feature worked; nobody could find it when they needed it
**Component:** `:cli` — `commands/TransformCommand.kt` (help text),
`service/TransformationService.kt` (the hint), plus `docs/cli/cli-reference.md`
**Found:** 2026-11-15, asking what happens when an input document has a duplicate field

## Summary

F23 gave the parsers three strictness profiles and B31 made JSON, XML and YAML agree on them, so a
caller **can** decide how much malformed input to tolerate. A user who met the rejection had no way
to learn that. Three gaps, in descending order of cost:

1. **The error named no remedy.** This is all you got:

   ```
   Error: Error parsing input: JSON parse error at 1:28 - Duplicate object key: 'id'
   ```

   Nothing suggests `--parse-profile lenient` would accept it, at the exact moment the user is
   looking for a way forward. Compare B38's non-finite error, which ends with
   *"Hint: guard it with isFinite(x)…"* — that is the house standard and this message missed it.

2. **The help listed the values without their meaning:**

   ```
   --parse-profile PROFILE     Parser strictness: strict | standard | lenient (default standard)
   ```

   Which one rejects a duplicate key? The line does not say. The very next entry,
   `--language-mode`, does explain its consequence, so the omission reads as an oversight rather
   than a style.

3. **No user-facing documentation at all.** `docs/cli`, `docs/getting-started` and
   `docs/language-guide` mentioned `--parse-profile` zero times. It existed only in `--help`.

The practical effect: a user with a feed they do not control — the case the profiles were built
for — would conclude UTL-X cannot read their data.

## The fix

**Help text** now states the behavioural difference, not just the vocabulary:

```
--parse-profile PROFILE     Parser strictness: strict | standard | lenient (default standard).
                            standard and strict REJECT malformed input (duplicate keys,
                            excessive nesting); lenient ACCEPTS it — last value wins on a
                            duplicate — for feeds you do not control.
```

`convert` shares this text, so both data-reading commands get it. (`validate` and `lint` correctly
do **not** accept the flag — they analyse the script, not an input document.)

**The rejection now names the remedy:**

```
Error: Error parsing input: JSON parse error at 1:28 - Duplicate object key: 'id'
       Hint: this input is rejected under the 'standard' parse profile. Re-run with
       --parse-profile lenient to accept it (the last value wins).
```

Added in the CLI service rather than the parsers: `:formats` is reached by the engine and the
daemon too, and neither has a `--parse-profile` flag to name.

**`docs/cli/cli-reference.md`** gained an "Input strictness" section with the full profile table,
both use cases, where else the profile can be set (`EngineConfig`; `UTLX_PARSE_PROFILE` /
`-Dutlx.parse.profile` for the daemon, per IB08), and the positional wart below.

## Where the hint must stay silent — and the mistake that proved it

A hint on every parse failure is noise; a hint promising relief it cannot deliver is worse. **The
first implementation made exactly that mistake**, claiming `lenient` would allow deeper nesting.
It does not: `maxDepth` is **256 under both `standard` and `lenient`**, and only `strict` differs
at 64. Depth is a *resource bound* (B32), not a grammar rule, so leniency does not relax it.

The implementation therefore asks the profile table whether `lenient` is genuinely more permissive
for the specific setting involved, instead of assuming:

| situation | hint? |
|---|---|
| duplicate key under `standard` | yes — lenient allows it |
| depth 300 under `standard` | **no** — lenient has the same 256 limit |
| depth 100 under `strict` | yes — "the depth limit rises from 64 to 256" |
| truncated JSON | **no** — no profile makes it parse |
| anything under `lenient` | **no** — nothing stricter to blame |

Reading the real `ParseOptions` also keeps the hint correct if the §4 table is ever retuned.

## Also documented: the flag is position-sensitive

`--parse-profile` must precede the file arguments:

```bash
utlx transform --parse-profile lenient script.utlx messy.json   # works
utlx transform script.utlx messy.json --parse-profile lenient   # prints usage, no explanation
```

The second form cost a round-trip during this investigation. Not fixed here — argument-order
tolerance is a broader CLI change — but it is now written down in the reference.

## Tests

`ParseProfileHintTest` (6 tests, `:modules:cli`). Four assert **silence**, which is the half that
matters: the hint must not fire where no profile would help. The depth-under-standard case is the
one that caught the first implementation.

**Revert-verified:** removing the hint call fails the two positive tests; the four silence tests
pass either way, as they should.

Full regression: **4931 JVM tests, 0 failures**; **581/581 conformance on the JVM and the GraalVM
native binary**, and the native binary emits the hint identically.

## Still open

- **CSV ignores the profile for ragged rows.** `a,b,c` with a row of `3,4,5,6` is accepted under
  **every** profile, silently dropping the extra value. The `test-corpora` harness already lists
  this as a `strict` GAP (`ragged_rows.csv expected=reject actual=accept`), so it is known
  Phase-0c work rather than new, but it means "strict" does not yet mean strict for CSV.
- **Argument-order tolerance** for flags after positionals (above).
