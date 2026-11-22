# F31: lexical fidelity for numeric values — `--preserve-numeric-lexeme`

> **F = the shared language stack** (`:core` parse options, `formats/{xml,csv,yaml}` readers,
> `:cli` flag). The limitation it exists for is
> [B65](../limitations/B65-numeric-and-lexical-fidelity-on-a-round-trip.md); the precision work it
> builds on is [B35](../bugs-fixed/B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md)
> and the pattern it follows is [B39](../bugs-fixed/B39-numeric-looking-xml-text-loses-leading-zeros.md)
> / [B40](../bugs-fixed/B40-yaml-reads-zero-padded-identifiers-as-octal.md).

**Status:** Implemented — 2026-11-22. CLI only; the engine does not carry the option yet (see
*Remaining*).
**Component:** `modules/core` (`format/ExactNumber.kt`, `format/ParseOptions.kt`), the XML, CSV and
YAML readers, `modules/cli`
**Asked for:** 2026-11-22 — *"Can there be made a switch so both situations where a choice is
offered by parameters?"*

## The problem

B35 made the numeric model exact: `100.10` keeps its scale, `9007199254740993` survives. Three
written forms still arrive with the right value and the wrong spelling, because the loss is
notation rather than precision and a numeric type has nowhere to hold it — `-0 → 0`, `1e3 → 1000`,
and in YAML alone `+2 → 2`. B65 has the mechanism.

For most work that is invisible and correct. It has a wrong answer wherever the **written form is
itself the information**: canonicalisation, a signature taken over rebuilt output, a content guard
comparing what it emitted against what it received.

## What it does

```
$ utlx transform id.utlx in.xml                             # <r><a>-0</a><b>1e3</b></r>
<r><a>0</a><b>1000</b></r>

$ utlx transform id.utlx in.xml --preserve-numeric-lexeme
<r><a>-0</a><b>1e3</b></r>
```

A lexeme in one of those shapes **stays a string**, so the round-trip rebuilds it verbatim. That is
not a new idea in this codebase — it is exactly what B39 and B40 already do for a zero-padded
`007`, and reusing that path is why this is a small change rather than a numeric-model project.

## The design

**One predicate, three shapes.** `ExactNumber.isLexicallyLossy(text)` answers *"would reading this
as a number lose its written form?"*, and nothing else consults it:

| shape | example | why no type holds it |
|---|---|---|
| negative zero | `-0`, `-0.00` | `BigDecimal` has no negative zero; `Long` cannot hold one |
| exponent notation | `1e3`, `1.5e2` | `Double` cannot record that an exponent was written |
| explicit plus | `+2`, `+1.5` | nothing records that a sign was written |

**A third axis, not a rung on the ladder.** The switch is
`ParseOptions.preserveNumericLexeme`, and no profile sets it —
`ParseOptions.of(STRICT).preserveNumericLexeme` is `false`, asserted in the test. Strictness and
fidelity are independent: a content guard wants STRICT **and** maximum fidelity, and folding them
together would force a choice between the two that nothing in the problem requires. `ParseOptions`
already keeps resource bounds and grammar leniency as separate axes; this is the third.

**Off by default**, because it changes a value's **type**. Under the flag, arithmetic on a
preserved value needs `toNumber()` — exactly as it already does for `007`. That cost is the whole
reason it is opt-in rather than the default.

**Scope: the `inferredOrNull` domain only** — the readers that must *guess* whether free text is
numeric. XML, CSV, and YAML's plain scalars.

### Why JSON is excluded

JSON's grammar has already decided the token is a number; `ExactNumber`'s own documentation records
the asymmetry (*"`ofOrNull` parses a lexeme the grammar has already decided is numeric — JSON hands
it one"*). Preserving the lexeme there would emit `"1e3"` **with quotes** — trading a lost notation
for a changed type, which is not an improvement, and which would make the round-trip no more
faithful than it already is.

Note what this does *not* cost: neither `-0 → 0` nor `1e3 → 1000` changes a value, so neither is a
smuggling vector. JSON numeric fidelity is a canonicalisation property, not a security one.

### One thing it fixed on the way

`+2` was the only shape where the formats **disagreed**: XML and CSV had always kept it a string
(B39 declines a leading `+` as non-canonical), while SnakeYAML read it as an `INT` and YAML alone
normalised it to `2`. That is the cross-format divergence B35 was written to end, in a shape B35 did
not cover. Under the flag all three agree. The default is deliberately left alone — changing it
would be a silent type change for every YAML user.

## Verification

```
modules/core/src/test/kotlin/.../format/F31LexicalFidelityTest.kt      11 tests
modules/cli/src/test/kotlin/.../F31LexicalFidelityCliTest.kt            9 tests
```

The load-bearing assertions are the negative ones:

- **`an ordinary number is not lossy`** — `0`, `-1`, `100.10`, `1.0`, `9007199254740993`. A
  predicate that is too wide turns real numbers into strings, which is a far worse failure than the
  limitation it fixes.
- **`YAML's deliberate spellings are left alone`** — `0x1F`, `0o17`, `1_000`, `1:30`, `.inf`,
  `.nan`. B40's standing warning: these are intentional YAML and must keep reaching SnakeYAML.
- **`no profile turns fidelity on`** — the orthogonality claim, asserted across all three profiles
  rather than assumed.
- **`nothing else moves`** — B39's `002` and B35's `100.10` read identically in both modes.
- **`JSON is not affected by the flag`** — the deliberate exclusion, so a later
  "make it consistent" change cannot quietly undo it.

## Remaining

**The engine does not carry the option.** `UtlxEngine.createStrategy` threads a bare `ParseProfile`
into `TemplateStrategy`, `CopyStrategy` and `CompiledStrategy`, and `parseInputPublic` takes a
profile rather than options — about eight touch points. So the flag is CLI-only, and an engine
deployment (the Azure offering included) cannot turn it on.

The clean shape for that work is to pass `ParseOptions` through the strategies instead of
`ParseProfile`, and to add the field to `EngineConfig.ParseConfig` beside `profile` and `locked`.
Two consequences land with it:

- `ConformanceStrategyParityTest` would then cover the option across all three strategies, which is
  the only cross-strategy gate this repo has. Until then there is no conformance coverage — the
  suite also has no per-fixture CLI-argument field.
- **Governance.** `ParseProfilePolicy.resolve` locks a *profile*, not individual fields, so an
  independent fidelity field sits outside the lock and a deployment cannot pin it the way it pins
  strictness. Either the resolver learns to cover fields, or fidelity needs its own lockable
  setting. Worth deciding deliberately rather than discovering later.
