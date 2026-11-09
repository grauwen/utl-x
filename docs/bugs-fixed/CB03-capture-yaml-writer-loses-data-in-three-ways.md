# CB03: the capture YAML writer loses data in three more ways

> **CB = CLI bug** (`modules/cli` — the `utlx` binary and the tooling shipped with it).
> This is **CB03**.

**Status:** **FIXED.**
**Priority:** Medium. Each case silently changes a captured value, in a tool whose only job is to
record one faithfully.
**Component:** `:cli` — `ConformanceGenerator.formatYamlValue` / `quoteYamlString`.

> **One-line:** asked whether the tests covered every case, the answer was no — a round-trip
> property over adversarial values found three further encodings that corrupt data, one of which
> turns `0123` into `83`.

## How these were found

[CB01](CB01-auto-capture-emits-unparseable-yaml.md) and
[CB02](CB02-capture-writes-output-text-where-the-runner-compares-a-value.md) were each found by
following a symptom. These were found by asking the *general* question — **does a captured value
survive the YAML round trip exactly?** — and feeding one adversarial value per branch of the writer.

That property is the test that should have existed from the start. All three earlier defects are
also discoverable from it, and none of them is discoverable from a test that asserts the emitted
string.

## The three defects

### 1. A control character inside a quoted scalar is not preserved

```
captured   "a\rb"
emitted    data: "a<CR>b"      ← the raw character
read back  "a b"               ← folded to a space
```

A double-quoted YAML scalar does not keep a literal newline or carriage return — the newline is
folded to a space and the carriage return is dropped. They have to be written as `\n` and `\r`
escapes. `quoteYamlString` wrote the character raw.

This also reached the expected field through CB02's decode: a JSON document whose value contains a
newline (`"\n"`) decoded to that newline and then came back as a space.

### 2. A literal block scalar silently appends a newline

```
captured   "a\nb"
emitted    data: |            ← `|` always ends the value with exactly one newline
             a
             b
read back  "a\nb\n"           ← one character longer
```

The block form is the readable choice and the corpus uses it widely, so the fix keeps it and picks
the right chomping indicator — `|` when the value already ends in a newline, `|-` when it does not.
Where a block scalar cannot be exact at all (a line with trailing whitespace, an embedded carriage
return, more than one trailing newline) it now falls through to the quoted form, which always can.

### 3. YAML 1.1 mis-reads several numeric forms

The writer emitted anything `toDoubleOrNull` accepted as a raw scalar. YAML 1.1 then re-reads some
of those as a **different number**, and one as a **string**:

| captured | read back as | |
|---|---|---|
| `0123` | `83` | octal |
| `007` | `7` | octal |
| `0x1F` | `31` | hex (not reachable — `toDoubleOrNull` rejects it) |
| `1e5` | `"1e5"` | a **string**, so a number became text |

Only forms YAML reads back unchanged now go out raw — no leading zero, no exponent. Anything else
numeric is normalised to a form that round-trips to the **same number** (`0123` → `123`,
`1e5` → `100000`).

The text deliberately does not survive there, and that is the fix rather than a shortfall: neither
`0123` nor `1e5` is a valid JSON number, so there is no text worth preserving — what matters is that
the fixture carries the right value and the right *type*. Quoting them instead would turn a number
into a string, which is a different defect.

## Tests

`ConformanceGeneratorYamlTest` gained the property and two companions:

- **`input data survives the YAML round trip exactly`** — one value per branch, including every
  shape above: a bare backslash, JSON-escaped content, multi-line with and without a trailing
  newline, a blank line, trailing spaces on a line, tab, carriage return, surrounding spaces,
  YAML 1.1 booleanish text (`yes`), `null`, `~`, `#`, `: `, unicode, and a malformed JSON document.
- **`a numeric form YAML would mis-read is normalised to the same number`** — asserts the value and
  the *type*, and separately that canonical forms pass through untouched so normalisation cannot
  creep.
- **`an object or array round-trips as an equal structure, not as text`** — those are emitted raw on
  purpose, so the text changes while the value does not; asserted separately so the round-trip test
  can demand exact text everywhere else.
- **`a malformed JSON output document is left as text`** — the decode is wrapped in a `runCatching`
  whose failure branch is otherwise invisible. This is the shape a mis-counted literal produces, and
  it must decline rather than guess.

Revert-verified **independently**, each failing a different case:

| reverted | first failure |
|---|---|
| control-character escaping | `trailing spaces on a line` |
| block chomping (`\|-`) | `multi-line, no trailing LF` |
| safe-number guard | `0123 must keep its value — expected 123 but was 83` |

Full suite **4452 tests, 0 failed**; conformance **563/563**.

## What is still not covered

Stated so it is not mistaken for completeness:

- **The `{`/`[` raw branch** relies on JSON being valid YAML flow syntax. True for JSON in practice,
  and the round-trip test covers an object and an array by value, but it is not proven for every
  JSON document — a very long one, or one with unusual escapes, is untested.
- **Multi-document or anchor-bearing content** is not considered; the writer never produces it.
- **The input/expected asymmetry** is pinned by a test but remains a convention of the runner rather
  than something the writer can verify.

## Related

- [CB01](CB01-auto-capture-emits-unparseable-yaml.md) — escaping order; made the files parseable.
- [CB02](CB02-capture-writes-output-text-where-the-runner-compares-a-value.md) — text versus value;
  made them correct.
- CB03 — makes them *faithful*. The three together are one defect class: a writer with no tests.
