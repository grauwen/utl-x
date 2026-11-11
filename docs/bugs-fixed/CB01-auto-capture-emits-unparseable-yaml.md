# CB01: auto-capture emits unparseable YAML, so seven fixtures never ran

> **CB = CLI bug** (`modules/cli` — the `utlx` binary and the tooling shipped with it).
> This is **CB01**, the first of a new series.

**Status:** **FIXED** — generator and the seven affected fixtures.
**Priority:** Medium. No wrong answers; seven conformance fixtures simply asserted nothing, in a
corpus whose headline number is a count of assertions.
**Component:** `:cli` — `ConformanceGenerator.formatYamlValue`.

> **One-line:** the YAML writer escaped quotes but not backslashes, so any captured value containing
> JSON-escaped content produced `\\"` — which YAML reads as a backslash followed by the end of the
> scalar — and the seven files it wrote were silently skipped by every runner.

## Root cause

```kotlin
// String - needs quotes
else -> "\"${value.replace("\"", "\\\"")}\""
```

The quote is escaped; the backslash is not. For a value carrying JSON-escaped content — which is
exactly what `parseJson`, `renderJson` and string-function captures hold — the raw backslash passes
through and the following escaped quote renders as `\\"`. YAML then reads backslash, then an
unescaped `"` that **terminates the scalar**, and the rest of the line is garbage:

```yaml
  data: "\"{\\"name\\": \\"John\\", \\"age\\": 30}\""
#              ^^^ scalar ends here
```

Escaping order is the whole bug: the backslash must be escaped **before** the quote, or the
backslashes introduced by quote-escaping get escaped a second time (or, as here, the original ones
never get escaped at all).

Working forward from the fix reproduces the files byte-for-byte, which is how the intended values
were recovered rather than guessed.

## Why it was invisible

The Python runner's loader swallows it:

```python
def load_test_case(file_path):
    try:
        return yaml.safe_load(f)
    except Exception as e:
        print(f"Error loading {file_path}: {e}")
        return None
```

A `None` is skipped. The fixture counts as **neither a pass nor a failure** — it is simply absent
from the total, and the seven `Error loading` lines scroll past in a run of 556. The suite reported
`556/556 passed` while 400 fixture files existed on disk.

The cross-strategy harness skipped them for the same reason, under its own
`fixture YAML did not parse` category — which is how they were noticed: that category had a count,
and a count invites the question "which ones, and why".

## What running them for the first time found

All seven now parse and are driven. Four passed immediately. **Three failed**, and both reasons were
real:

**1. The generator and the runner disagree about what `expected.data` means.** For `format: json`
the generator writes the output **text**, while the runner treats a string `expected.data` as the
expected **value** — so it compares one escaping level too deep. Input is handled the other way
round: a string starting with `"` is passed as-is.

The three affected fixtures now carry the parsed value, which is the form the runner compares. **The
writer was fixed too — see [CB02](CB02-capture-writes-output-text-where-the-runner-compares-a-value.md).**
Fixing only the escaping would have moved this from "silently skipped" to "fails on the next
capture", so it is tracked and fixed rather than noted. The runner's convention is left as it is;
changing it would touch most of the corpus.

**2. One expectation was stale in substance, not just in form.** `renderJson_auto_39c4ad24` expected
`{"Material":…}` from an XML input whose root is `<SAP_Response>`; the engine produces
`{"SAP_Response":{"Material":…}}`. Root-included is consistent with how XML→UDM behaves everywhere
else in the corpus, so the engine is right and the captured expectation predates that shape.

Each of the three was verified by running the transformation directly and reading the output, not by
adopting whatever the runner printed — the distinction matters, because "expected := actual" is how
a corpus stops being evidence. The reasoning is recorded in each fixture's `notes`.

## Effect

| | before | after |
|---|---|---|
| Python conformance suite | 556 tests | **563** |
| `Error loading` lines per run | 7 | **0** |
| cross-strategy fixtures driven | 354 | **361** |
| of those, genuinely compiled | 321 | **328** |
| skip category "YAML did not parse" | 7 | **gone** |

The only remaining skip reason in the cross-strategy run is multi-input (39). Compiled-coverage and
fixture-count floors were raised to 328 and 355 so none of this can silently regress.

## Tests

No new test class. The fix is a YAML writer whose output is validated by the corpus itself: the seven
files it produced are now parsed and executed by both runners on every run, which is a stronger and
cheaper check than a unit test asserting a string shape. The floors are what prevent regression.

Tests were added with [CB02](CB02-capture-writes-output-text-where-the-runner-compares-a-value.md),
which covers this escaping too — `ConformanceGeneratorYamlTest`. Revert-verified: restoring the
escaping order fails 5 of its 8 cases.

[CB03](CB03-capture-yaml-writer-loses-data-in-three-ways.md) then added the general round-trip
property and found three further lossy encodings — control characters, block-scalar chomping, and
numeric forms YAML 1.1 mis-reads. All three were discoverable from that property, and none from a
test asserting the emitted string, which is the lesson worth keeping.

## Related

- conformance-across-execution-strategies *(design notes — development branch)*
  — the harness whose skip tally surfaced this; finding 5 in its inventory.
- [EB04](EB04-nested-lambdas-emit-duplicate-method-names.md),
  [EB05](EB05-lambda-bodies-cannot-reach-a-named-input.md) — the other two defects that harness found,
  both also invisible because something swallowed the failure.
