# B65: a number's written form is not always recoverable from its value

> **B = the shared language stack** (`:core` numeric model, `formats/*` readers). Same component as
> [B35](../bugs-fixed/B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md),
> which made the model exact, and the same number line — see
> [`README.md`](README.md) for why a limitation keeps the component prefix.

**Status:** Permanent limitation — **will not be fixed** in the numeric model. An opt-in escape
hatch ships as [F31](../features/F31-lexical-fidelity-for-numeric-values.md)
(`--preserve-numeric-lexeme`), which trades the type rather than removing the boundary.
**Component:** `modules/core` — `format/ExactNumber.kt`, the exact numeric representation
**Found:** 2026-11-22, while checking what survives a canonical rebuild for a content-guard
demonstration. Not reported by a user.

## What you observe

```
$ utlx transform id.utlx in.xml          # <r><a>-0</a><b>-0.00</b><c>1e3</c></r>
<r><a>0</a><b>0.00</b><c>1000</c></r>
```

Three written forms do not come back:

| lexeme | reads as | renders as | what is lost |
|---|---|---|---|
| `-0`, `-0.00` | `0L`, `BigDecimal("-0.00")` | `0`, `0.00` | the **sign** |
| `1e3`, `1.5e2` | `Double` | `1000`, `150` | the **notation** |
| `+2` *(YAML only)* | `2L` | `2` | the **written plus** |

Format- and direction-independent for the first two: XML→JSON, JSON→JSON and JSON→XML all do it.

What is **not** lost, and is the reason this is narrow: `100.10` keeps its scale, `-0.00` keeps its
*scale* while losing its sign, `9007199254740993` survives intact, and `007` keeps its padding by
staying a string (B39/B40).

## The mechanism

B35 replaced a `Double`-based model with exact types — `Long`, `BigInteger`, `BigDecimal` — chosen
by the written form. That fixed precision. It cannot fix notation, because:

- **`BigDecimal` has no negative zero.** `BigDecimal("-0.00").signum()` is `0` and
  `toPlainString()` returns `"0.00"`. The scale is a field; the sign of zero is not representable
  at all. `Long` cannot hold one either.
- **`Double` cannot record that an exponent was written.** `1e3` and `1000` are the same `double`.
  `ExactNumber` routes exponent forms to `Double` deliberately — promoting them to `BigDecimal`
  would make `toString()` emit `1E+3`, changing serializer output for a notation that is already
  approximation-shaped.
- **No numeric type records a leading `+`.** XML and CSV never lost it (B39 declines a leading `+`
  as non-canonical, so `+2` stays a string); SnakeYAML reads it as an `INT`, so YAML alone
  normalised it.

The common cause: **`-0 == 0`, `1e3 == 1000` and `+2 == 2` are numerically identical.** These are
not precision defects. No value changes. The distinction is *lexical*, and a numeric model has
nowhere to put it.

## The cost to change

Carrying the lexeme inside the numeric model — the fix that would preserve the form *and* keep the
value numeric — needs a type that is both:

```kotlin
class LexicalNumber(val lexeme: String, val value: Number) : Number()
```

It must extend `java.lang.Number`, or every `is Number` dispatch in the serializers, the
interpreter and the stdlib stops matching. That part is small. The cost is **equality**: does
`LexicalNumber("-0", 0L)` equal `0L`?

That is precisely the failure mode of
[B63](../bugs-fixed/B63-document-null-is-not-equal-to-the-null-literal.md) — a scalar
representation that compares unequal to the plain one, and diverges between the interpreter and the
compiled path. Semantics live in two places (`interpreter.kt` and
`strategy/compiled/ASTCompiler.kt` + `RuntimeOps.kt`), `ConformanceStrategyParityTest` is the only
gate that catches a divergence, and B48 is on record as an interpreter-only fix that passed every
suite while returning the wrong answer on two strategies.

So: a new numeric representation is a **B35-sized project**, not a patch. That is the sentence that
should stop this being re-litigated.

## What ships instead

[F31](../features/F31-lexical-fidelity-for-numeric-values.md) — `--preserve-numeric-lexeme`. Under
the flag, a lexeme in one of the three shapes **stays a string**, exactly as B39/B40 already keep
`007` a string, so a round-trip rebuilds it verbatim:

```
$ utlx transform id.utlx in.xml --preserve-numeric-lexeme
<r><a>-0</a><b>-0.00</b><c>1e3</c></r>
```

It does not remove the limitation, it relocates the cost: the value's **type** changes, so
arithmetic on one of these needs `toNumber()`. And it is confined to the readers that must *guess*
whether free text is numeric — XML, CSV, YAML's plain scalars.

### Where the limitation is not a limitation

On a **content guard** — the context this was found in — `-0 → 0` and `1e3 → 1000` are the
*intended* behaviour, not a defect to work around. A guard's egress rebuilds the message in one
normal form on purpose, because a single canonical form is what defeats parser-differential
smuggling. Turning F31 on there would reopen that channel. The flag is for a pipeline that must
**reproduce** what it received; a guard must deliberately not. See
[F31 §Where this does not apply](../features/F31-lexical-fidelity-for-numeric-values.md).

### What is still limited with the flag on

- **JSON is excluded by design.** Its grammar has already decided the token is a number. Keeping
  the lexeme would emit `"1e3"` *with quotes* — a changed type rather than a preserved form, which
  is not an improvement. A JSON round-trip of `1e3` is therefore still `1000`.
- **The engine does not carry the option.** It threads a bare `ParseProfile` through all three
  strategies, so `--preserve-numeric-lexeme` is CLI-only. An engine deployment — including the
  Azure offering — cannot turn it on. See F31 §Remaining.
- **Arithmetic on a preserved value is a string operation** until `toNumber()` is called. `"002" + 0`
  is `"0020"`, and so is `"-0" + 0`.
- **Leading and trailing whitespace is still trimmed** on XML element text. That is XML semantics,
  not a fidelity question, and the flag deliberately does not touch it.

## Verification

The boundary is pinned so that it cannot move silently:

- `modules/core/src/test/kotlin/com/glomidco/utlx/core/format/F31LexicalFidelityTest.kt` — the
  predicate's boundaries, including the half that matters most: **an ordinary number must not match**
  (`0`, `-1`, `100.10`, `1.0`, `9007199254740993`), or the escape hatch would turn real numbers into
  strings.
- `modules/cli/src/test/kotlin/com/glomidco/utlx/cli/F31LexicalFidelityCliTest.kt` — the limitation
  itself, asserted in **both** modes: default behaviour unchanged, and `JSON is not affected by the
  flag` so the deliberate exclusion cannot be quietly "made consistent" later.

No conformance fixture: the suite has no per-fixture CLI-argument field, and
`ConformanceStrategyParityTest` runs through the engine strategies, which do not carry the option.
Both follow from the engine gap above, and close with it.
