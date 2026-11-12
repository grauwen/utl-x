# F27 — Shell ergonomics: raw output and shell-supplied values

**Status:** Implemented — 2026-11-12
**Component:** `:modules:cli` — `TransformCommand`
**Raised:** 2026-11-11, from replacing `jq` with UTL-X in `scripts/check-action-versions.sh`

## Correction to the original report

This report was filed claiming **three** gaps, the first being "no inline expression —
every query needs a file on disk". **That was wrong.** `-e/--expression` had been
implemented all along (`Main.kt:105`, `TransformCommand.kt:558`); it was simply absent
from `utlx transform --help`.

The claim was made by reading `--help` rather than running the flag, and the one test
that *was* run passed a positional input file, which the parser correctly read as the
*script* argument — so the error returned ("Cannot use -e/--expression with a script
file") was the feature working, and it was misread as the feature missing.

Worth recording because the failure mode is the same one that produced the B48
half-fix a day earlier: **trusting a secondary source instead of executing the thing.**
A feature nobody can find in `--help` is nearly as useless as one that does not exist,
which is why documentation is treated here as part of the fix rather than a follow-up.

## What was actually missing

| | before | after |
|---|---|---|
| inline expression | **worked**, undocumented | documented, with examples |
| `-r` on a scalar | worked | unchanged |
| `-r` on an array | printed pretty JSON | **one element per line** |
| shell-supplied value | nothing | **`--arg name=value`** |
| `-e`, `-r`, `--charset` in `--help` | absent | documented |

## The fix

### `-r` over a sequence

`-r` was a post-serialization string operation: if the rendered text started and ended
with `"`, strip the quotes. That cannot tell a scalar from a sequence, so an array came
out as pretty JSON and every list extraction needed a `tail`/`tr` pipeline.

`TransformCommand` now calls `TransformationService.transformWithUdm` (added for EB14),
which returns the result UDM alongside the rendered text, and `renderRaw` decides from
the structure:

- **scalar** → bare text
- **array of scalars** → one element per line
- **object, or array containing structure** → the serialized form, untouched

The last row is deliberate. Flattening an object would invent a format, and emitting
something lossy without saying so is worse than emitting JSON the caller can parse.

### Numbers go through the serializer, not `toString()`

The first attempt rendered scalars with `value.toString()`. That silently disagreed
with every other output path: `count()` gave `2.0` where the serializer gives `2`
(B37's integral-Long path), and an exact decimal gave `10.5` where the serializer gives
`10.50` (B35). Non-`String` scalars are now rendered by `JSONSerializer`, so `-r`
differs from the normal path *only* by the absence of JSON string quoting.

### `--arg name=value`

Values arrive as **strings**, as in `jq`; coerce with `toNumber()` where a number is
wanted. They are injected as `let` bindings ahead of the synthesized body:

```
%utlx 1.0
input auto
output json
---
let min = "12"
filter($input.items, (i) -> i.p > toNumber($min)).*.n
```

A top-level `let` followed by the body expression is valid UTL-X, so this needs no
language change, no header plumbing, and no new binding concept.

Names are validated as identifiers (`[A-Za-z_][A-Za-z0-9_]*`) so a bad name produces a
clear CLI error rather than a confusing parse error in generated source.

**Expression mode only.** With a script file there is no synthesis point, and splicing
bindings into someone else's source is the kind of textual surgery that breaks on the
first script containing `---` inside a string literal. Using `--arg` with a script file
is rejected with a message pointing at `--input name=file`.

### The unescaping bug this exposed

Round-tripping a string through JSON and back cannot be undone with sequential
`replace` calls, and the pre-existing `-r` code did exactly that. Unescaping `\\`
first leaves a backslash that then pairs with the following character, so
`--arg p=a\tb` came back as `a<TAB>b`, and a Windows path `C:\tmp\x` became
`C:\<TAB>mp\x`.

The fix removes the round-trip: a `String` scalar is returned **verbatim** from the UDM
and never serialized. Only non-strings reach the serializer, and those have no quotes
to strip. Every value now round-trips unchanged — verified against `a\qb`, `a\\b`,
`a\tb`, `C:\tmp\x`, `say "hi"`, `a=b`.

## Verification

Raw and non-raw agree on every scalar shape, differing only in string quoting:

| expression | no `-r` | `-r` |
|---|---|---|
| `$input.tag` | `"v3.0.4"` | `v3.0.4` |
| `count($input.items)` | `2` | `2` |
| `$input.items[0].p` | `10.50` | `10.50` |
| `sum($input.items[*].p)` | `35.50` | `35.50` |
| `$input.flag` | `true` | `true` |
| `$input.big` | `9007199254740993` | `9007199254740993` |
| `$input.missing` | `null` | `null` |

Sequences: `$input.items[*].n -r` → `a\nb`; `$input.items[*].p -r` → `10.50\n25.00`.

Suites: `:modules:cli:test`, `:modules:core:test`, conformance.

## `--argjson`

`--argjson name=<json>` binds a structured value with its types intact, so
`$cfg.min + 1` is arithmetic rather than string concatenation.

**It is read by the JSON parser, not spliced in as a literal.** The obvious
implementation is `let name = (<json>)`, and it very nearly works: the expression lexer
and the JSON reader were compared across ten edge cases — `10.50` scale, `1e3`,
`9007199254740993`, `-0.0`, `1.0e-7`, `\uXXXX` escapes, keys with spaces and dashes,
empty containers, escaped quotes, deep nesting — and agreed on **nine**.

The tenth is the reason not to do it:

| `{"a":1,"a":2}` | result |
|---|---|
| JSON reader (standard profile) | `Error: Duplicate object key` |
| expression lexer | `{"a":2}` — silently last-wins |

That is **B01's** failure mode, and **F23's** parse-strictness profiles exist precisely
to govern it. Literal injection would have created a second JSON policy that
`--parse-profile` could not reach.

So each `--argjson` value joins the inputs map as a JSON document and is read by the
same reader as any other input. Verified: `--parse-profile lenient` permits last-wins,
`standard` and `strict` reject — the profile reaches the arg.

Two side effects were checked rather than assumed:

- **Argument entries are appended after the real inputs**, because
  `inputs.values.first()` selects the primary input for the synthesized script and an
  argument must never become it.
- **`hasMultipleInputs` reads `options.namedInputs`**, not the augmented map, so adding
  entries cannot flip the multi-input code paths.
- A name colliding with an existing input is rejected rather than silently clobbering it.
- `--from xml` still applies to the primary input while the arg stays JSON.

## Still open

**`-r` does not flatten nested structure**, by design (see above). `jq -r` on an object
is also a type error, so this matches.

## Also worth knowing

Two process notes from building this, both of which cost time:

- A `$` inside a Kotlin triple-quoted string is a **template reference**. Adding
  `$input` examples to the help text produced seven `Unresolved reference: input`
  compile errors; the original help avoided `$` entirely for this reason. Escape as
  `${'$'}input`.
- `[ -f build/libs/*.jar ] && echo ok` tests **existence, not freshness**. A stale jar
  passed that check while the fix under test had not been compiled in, which sent the
  escaping investigation down a false path. Compare timestamps, or use
  `--rerun-tasks`.
