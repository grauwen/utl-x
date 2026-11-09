# CB02: capture writes output text where the runner compares a value

> **CB = CLI bug** (`modules/cli` — the `utlx` binary and the tooling shipped with it).
> This is **CB02**.

**Status:** **FIXED.**
**Priority:** Medium. Every future capture of a transformation whose JSON output is a *string*
would have produced a fixture that parses and then fails.
**Component:** `:cli` — `ConformanceGenerator`.

> **One-line:** [CB01](CB01-auto-capture-emits-unparseable-yaml.md) made the captured fixtures
> parseable; this makes them *correct*. The writer emitted the output **text**, but the runner
> compares a string `expected.data` as the output **value**.

## Why this is separate from CB01

CB01 was an escaping-order mistake that made seven files unparseable. Fixing it was necessary and not
sufficient: the files then loaded, and three of the seven **failed**. The failure was not stale
expectations — it was a disagreement between the writer and the reader about what the field means.

That distinction is the point. Fixing only CB01 would have moved the problem from "silently skipped"
to "fails on the next capture", which is better but still wrong.

## Root cause

The runner's rule, in its own code:

```python
if isinstance(expected_data, str) and '\n' in expected_data and expected_data.strip()[0] in '{[':
    expected_data = json.loads(expected_data)       # treat as TEXT
# otherwise: the string IS the expected value
```

So a string `expected.data` is the expected **value**, unless it is a multi-line block beginning
with `{` or `[`. The writer always emitted the raw output text, which agrees with that rule only by
accident:

| output document | emitted | runner reads it as | agreed? |
|---|---|---|---|
| multi-line object/array | text | text (re-parsed) | ✅ by luck |
| bare number or boolean | raw | the value | ✅ by luck |
| **a JSON string** | text | **the value** | ❌ one level too deep |

`upper($input)` and `renderJson(...)` both produce a JSON string, so a captured fixture for either
asserted that the output *value* was the escaped *text* — and failed the moment it could run.

Note the **asymmetry with `input.data`**, which the runner uses as-is when it looks like JSON. The
input side must therefore stay as text. This is the runner's convention and the writer now respects
it on both sides rather than trying to change it: changing the convention would touch every fixture
that depends on it, which is most of the corpus.

## The fix

`formatExpectedYamlValue` decodes a JSON-string output document and emits what it decodes to,
leaving every other shape alone. Two details that are easy to get wrong:

- **A decoded string must always be quoted.** The decoded value of `"{\"name\":\"John\"}"` is the
  *string* `{"name":"John"}`, which starts with `{` — so routing it back through the general
  formatter would hit the `{`/`[` branch, emit it raw, and YAML would read a **mapping** instead of
  a string. The runner would then compare a map against a string.
- **Only JSON-ish output formats.** CSV and XML expectations are compared as text, so decoding
  would corrupt them.

The quoting itself is now one shared function, which is also where CB01's escaping order lives, so
the two cannot drift apart.

## Tests

`ConformanceGeneratorYamlTest` (`:cli`) — the writer had **no tests**, which is why both defects
shipped. Every case parses the emitted YAML and compares the decoded value, because asserting the
emitted *string* would re-encode the bug rather than catch it.

Covers: a value carrying JSON-escaped content, a bare backslash, a sweep asserting every emitted
document parses at all (CB01); a JSON string output written as its value, a JSON-*looking* decoded
string staying a string rather than becoming a mapping, an object output left unchanged, a
non-JSON format left as text, and the input/expected asymmetry held in one test so it cannot be
"tidied" away (CB02).

Revert-verified separately: restoring the text-for-value behaviour fails 3 of 8; restoring CB01's
escaping order fails 5 of 8. Different sets, so each fix is independently load-bearing.

One test-authoring note worth keeping, because it cost real time: Kotlin raw strings make quote
counting treacherous — `""""\"X\""""` is a *malformed* JSON document, one quote short, and the
generator then correctly declines to decode it, so the test fails for a reason that looks like a
product bug. The tests build these documents from named one-character pieces instead.

## Related

- [CB01](CB01-auto-capture-emits-unparseable-yaml.md) — the escaping defect in the same writer; this
  is the half that fixing it revealed.
- [CB03](CB03-capture-yaml-writer-loses-data-in-three-ways.md) — three further lossy encodings in the
  same writer, found by asking whether these tests covered every case. They did not.
- [conformance-across-execution-strategies](../architecture/conformance-across-execution-strategies.md)
  — the harness whose skip tally started this thread.
