# F33: output presentation — `--format table`, and why it is not `--to table`

> **F = the shared language stack** — here `modules/cli` only; no language or format change, which
> is the point. Related: [F27](F27-shell-ergonomics-for-inline-queries.md) (`-r`, `-e`, `--arg`),
> which drew the same line for *selection*.

**Status:** Implemented — 2026-11-23.
**Component:** `modules/cli` — `TransformCommand` (the flag), `TransformationService` (`renderTable`), `Main` (identity-mode routing)
**Asked for:** 2026-11-23 — a demo step read
> `utlx transform policy-xml.utlx bad.xml | grep -E '"message"'`
> *"is there no option in our utlx to view it nicely? Can't we get rid of the grep and pipe sed?"*

## Two axes, not one

```
--to / --output-format    what the data IS      json, xml, csv — parseable, round-trippable
--format                  how it is SHOWN       table — read once by a human, never parsed
```

```
$ utlx transform report.utlx bad.json --format table
rule         | field     | severity
-----------------------------------
qty-in-range | qty       | error
ccy-allowed  | ccy       | error
legacy-field | legacyRef | warning
```

Works in all four modes: a script file, `-e` expression mode, identity mode
(`cat x.json | utlx --format table`) and `convert`.

## Why `--format` and not something new

`--format` is **already** the presentation flag in this CLI — `lint`, `validate`,
`functions` and `udm` all use it (`--format human | json | compact`), and `transform` deliberately
had none, because its own axis is `--from` / `--to`. It is also docker's and git's name for exactly
this. `-o` was unavailable: here it means the output **file**.

An earlier draft proposed `--view`. That would have been a *fifth* name for a concept this CLI
already names, so it was dropped. Surveying the field first would have saved the detour:

| tool | flag | values |
|---|---|---|
| kubectl | `-o` | `json` `yaml` `wide` `name` `jsonpath=` — default **is** a table |
| aws | `--output` | `json` `text` `table` `yaml` |
| docker | `--format` | Go template, with a literal `table …` prefix |
| psql | `\pset format` | `aligned` `csv` `html` |

Worth noting what that survey shows: **mainstream CLIs do not separate format from presentation** —
they put `json`, `yaml`, `table` and `wide` in one flag, because to a user it is one question. UTL-X
has a second axis they lack: the output format is part of the *transformation contract*, declared in
the `%utlx` header and overridden by `--to`. That is a pipeline concern that also lives in the
script, so it cannot be collapsed into a display choice.

## No `-f` short form

`--format` has **no short form on `transform`**, though `lint` and `validate` have had `-f` for
longer. Short flags are a scarce resource and `-f` is the most heavily spent letter in Unix — it
means **file** in jq (`--from-file`), kubectl (`--filename`), tar, make, grep and `docker build`.
Docker, whose `--format` this follows, spends no short flag on it at all.

On `transform` the collision is live rather than theoretical, because the script is a positional
argument. With `-f` accepted, correct muscle memory produced this:

```
$ utlx transform -f report.utlx bad.json
Error: Parse errors:
  Expected '---' separator after header (not found in script)
```

`report.utlx` was eaten as the format value and `bad.json` became the *script* — an error about
something entirely unrelated. Without `-f` the same command says `Unknown option: -f`, which is
true and findable.

`lint` and `validate` keep theirs: neither takes a script positionally, so the hazard does not
exist there. That difference is the reason, not an oversight, and is recorded in the code so it is
not "made consistent" later.

## The first implementation was wrong, and said so clearly

`table` was first added as a value of `--to`. It needed **two special cases** to survive:

1. a refusal in `parseInput` — because every other `--to` value is also a valid `--from` value, and
   a table has no parser;
2. a substitution in the `-e` header synthesis — because `output table` is not in the `%utlx`
   grammar, and adding it would have made `table` a **reserved word** for a flag's benefit.

Both are **gone** in the shipped version, not rewritten. A non-format in the format slot is what
created them. The dividend: `--format wide` or `--format summary` later costs no grammar, no parser and no
round-trip story.

A guard remains where the confusion is likely, answering with the fix rather than a dead end:

```
$ utlx transform report.utlx bad.json --to table
Error: 'table' is a presentation, not a data format — use --format table (or --format table).
        --to selects what the data IS (json, xml, csv, …).
```

And an explicit `--to` alongside `--format table` warns rather than failing, because `--to` may be
inherited from a script header rather than typed. The check deliberately lives in `TransformCommand`,
where an explicit flag is distinguishable from one identity mode computed for itself — the first
version warned spuriously on every `-e` run.

## Rendering, and the defect found on the way

Built from the UDM directly. The first attempt composed the CSV serializer with the stdlib's
`prettyPrintCSV`, reusing existing code — and that is broken, because `prettyPrintCSV` splits on the
delimiter without honouring CSV quoting:

```
csv     ccy-allowed,"Field 'ccy' value 'GBP' is not one of [EUR, USD]"
table   ccy-allowed | "Field 'ccy' value 'GBP' is not one of [EUR | USD]"
```

The comma inside a quoted value became a column break and the quotes survived into the output. Any
message, address or list in a cell was mangled. **That is a defect in a public stdlib function, not
an unlucky composition, and is reported separately.**

Shape rules, chosen so the view never fails on real data:

| input | rendered as |
|---|---|
| array of objects | columns = union of keys in first-seen order |
| single object | one row |
| array of scalars, or anything else | a one-column `value` table |
| a nested value in a cell | compact JSON — never a Kotlin `toString()` dump |
| numbers | via `ExactNumber.renderScalarText`, so a `BigDecimal` keeps its scale (B35) |
| no rows | empty output, not a bare header |

A row missing a key leaves that cell **blank with its separator** (`3 |`) rather than shifting the
row — the reader sees a column that is empty, not a row that is short. No line carries trailing
whitespace.

## Selection is not presentation

Half of the original grep was doing *selection*, and that belongs to the language, not a flag:

```utlx
v.issues |> map(i => i.message)        # with -r: one message per line, no grep
```

A `--select` flag would be a second, weaker query language bolted onto the first. F33 deliberately
does not add one.

## Verification

`modules/cli/src/test/kotlin/.../F33PresentationTableTest.kt` — 12 tests. The load-bearing ones pin
the **axis**, not the rendering:

- **`--to table is refused, and the error names the right flag`** — a presentation must never be
  accepted as a format.
- **`--format table works in expression mode, which synthesizes a header`** — the mode that broke when
  `table` was a format, because the synthesized header carried `output table`.
- **`a comma inside a cell is not a column break`** — the regression that forced the rewrite,
  asserting both the intact value *and* that no quoting leaked into the view.
- **`a nested value renders as compact JSON, never a Kotlin dump`** — asserts the absence of
  `Scalar(` and `Array(elements`, which is what the CSV route produced.

No conformance fixture: the suite has no per-fixture CLI-argument field, and presentation is a CLI
concern with no `%utlx` surface to exercise.
