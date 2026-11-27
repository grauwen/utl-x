# B67: `round(x, n)` rounds the wrong way on an inexact half

> **B = the shared language stack** (`:stdlib` — `math/MathFunctions.kt`, and the same pattern in
> `finance/FinancialFunctions.kt`). Narrowing to `Double` is what
> [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) removed from the
> readers; `round` still did it, at the one place a money value is most likely to pass through.
>
> **A second finding came out of the same investigation:** `roundToCents` ignored the two decimals
> its own documentation promises. Kept here rather than split out, because both are the same line of
> code and the same decision about scale — see *Two defects, not one*.

**Status:** **FIXED (2026-11-23)** — both halves: the wrong values, and the scale. `round(x, n)`
now returns a value carrying `n` decimals.
**Priority:** High — it returns **wrong numbers**, silently, in the rounding function a monetary
calculation is most likely to call.
**Component:** `stdlib` — `MathFunctions.round`, and `FinancialFunctions.roundToCents` /
`roundToDecimalPlaces` share the defect
**Found:** 2026-11-23, from a demo: an invoice step showed a line total as `20` beside `29.99`, and
the ragged column turned out to be the visible end of a correctness bug.

## Reproduction

Every case below was a defect, and all are fixed:

```
round(1.015, 2)   →  1.01   want 1.02    WRONG NUMBER — a different value, silently
round(1.005, 2)   →  1      want 1.01    WRONG NUMBER, and the scale as well
round(20.00, 2)   →  20     want 20.00   scale discarded
round(29.995, 2)  →  30     want 30.00   scale discarded
roundToCents(20)  →  20     want 20.00   scale discarded — its own KDoc documents 10.00 for 9.999
```

Two distinct causes behind one line of code, which is why the list looks heterogeneous:

- **the wrong numbers.** Scaling by a power of ten in `Double` rounds an already-inexact value:
  `1.015` is `1.01499…`, so `× 100` is `101.4999…`, which rounds **down** to `101`.
- **the discarded scale.** `asNumber()` narrowed to `Double` before any rounding happened, and the
  result came back as a `Double` too, so a whole value printed without its decimals.

**A note on how this report evolved,** because the reasoning is more useful than the conclusion.
The list above is the original one. Mid-investigation the last three were reclassified as *correct*
— `20` and `20.00` are the same number, four conformance fixtures asserted the plain form, and
Python, R and PHP all agree with them. That reclassification was wrong, and two things overturned
it:

- those four fixtures record a choice someone made earlier, not a truth, and one of them was
  **visibly inconsistent with itself** — a years-employed column reading `4.6, 5.3, 3, 6.8, 2.5`,
  where the single whole number had lost its decimal;
- Python, R and PHP are not precedent, because a `float` *cannot* carry a scale. Among the
  languages that have the choice — SQL's `NUMERIC`, Python's `Decimal` — keeping it is unanimous.

So the first instinct was right and the middle position was the error. Worth recording: "several
existing tests disagree" felt like strong evidence and was not.

The exact model is right everywhere a value's scale was *written* rather than requested, which is
why `round` was never the right place to look for it:

```
20.00          →  20.00        scale survives a read (B35)
20.00 + 0.00   →  20.00        and arithmetic
2 * 10.00      →  20.00        and multiplication
```

## Cause

```kotlin
val num = args[0].asNumber()                       // narrows to Double — scale gone here
val places = args[1].asNumber().toInt()
val factor = 10.0.pow(places)
return UDM.Scalar(round(num * factor) / factor)    // and the arithmetic is Double too
```

Two separate consequences from one line:

1. **`asNumber()` narrows to `Double`**, so a `BigDecimal` loses its scale before any rounding
   happens. The result is a `Double`, which the serializer then prints without a fraction when it
   is integral (`20`, not `20.00`).
2. **`round(num * factor) / factor` is floating-point.** `1.015` is not representable: as a
   `Double` it is `1.01499999…`, so `× 100` gives `101.4999…`, which rounds **down** to `101`, and
   `/100` yields `1.01`. The scale-ten trick is the textbook way to get this wrong.

The second is the serious half. It is not a formatting complaint — it is a different number.

## Why this matters more than its size

B35 replaced a `Double`-based numeric model precisely so that `100.10` stays `100.10` and
`9007199254740993` survives, and the project's own documentation leads with that. `round(x, n)` is
the single most likely call in a monetary transformation, and it was the last place still doing
arithmetic in `Double`. (The *scale* it also dropped turned out to be correct for `round` —
see the decision below. The wrong **values** are what make this High.)
A user who reads "exact decimals" and then rounds a price has been told the truth about the parser
and misled about the arithmetic.

It also affects a verdict: a `validate.inRange` check against a rounded value can pass or fail on
the wrong side of a boundary.

## Two causes, one line of code

| | | |
|---|---|---|
| **(a)** | `round(1.015, 2)` → `1.01` instead of `1.02` | a wrong **number** |
| **(b)** | `round(20.00, 2)` → `20`, not `20.00` | a discarded **scale** |

(a) is not arguable: `1.015` is `1.01499…` as a `Double`, so scaling by a power of ten rounds it
down. A different number, silently, in a money function.

(b) took a detour worth recording. It was first judged a defect, then reclassified as correct
behaviour, then judged a defect again. What overturned the middle position:

- the four conformance fixtures that disagreed record a choice someone made earlier, not a truth —
  and one was **visibly inconsistent with itself**:

  ```
  years_employed:  4.6, 5.3, 3, 6.8, 2.5      ← one whole number, stripped of its decimal
  ```

  That column comes from `round(days / 365.25, 1)`. Four values carry one decimal; the fifth lost
  it only because it landed on a whole number. The same raggedness in a demo invoice — `20` beside
  `29.99` — is what started this investigation in the first place.

- Python, R and PHP returning `20` is not precedent, because a `float` **cannot** carry a scale.
  Among languages that have the choice, keeping it is unanimous (see the survey below).

So the ten expectation changes across four fixtures are the correct new outputs, not tests bent to
fit. The lesson: *"several existing tests disagree"* felt like strong evidence and was not.

## Fix as applied

```kotlin
exact.setScale(places, RoundingMode.HALF_UP)
```

`setScale` answers both halves at once: it rounds correctly **and** the result carries the scale it
rounded to. No `stripTrailingZeros` and no `.toDouble()` — the value stays a `BigDecimal`, so a
large one keeps the exactness B35 gave it.

Three details settled along the way:

- **Rounding mode.** `HALF_UP`, stated rather than inherited: it matches the documented example
  (`round(3.14159, 2) => 3.14`) and what money conventionally wants, and `Math.round` is not
  symmetric about zero.
- **`round(x)` with one argument stays integral** — a whole number, not `x.00`. That is its
  documented behaviour and scripts depend on it; only the two-argument form carries a scale.
- **A genuine `Double` input** cannot gain exactness it never had. Reading it via
  `BigDecimal(d.toString())` rather than `BigDecimal(d)` keeps the shortest representation and
  avoids `0.1` becoming `0.1000000000000000055511151231257827`.

`roundToCents` and `roundToDecimalPlaces` had the same treatment. Both already rounded on
`BigDecimal` correctly and then discarded it with `.toDouble()`; and `roundToDecimalPlaces` used
`HALF_EVEN` while the other two used `HALF_UP`, so one amount could round two ways depending on
which function a script reached for. All three now agree, in value and in scale.

## Verification

`stdlib/src/test/kotlin/.../math/B67RoundExactScaleTest.kt` — 14 tests. Assertions are on the
**rendered** value, via the same `ExactNumber.renderScalarText` every output path uses, because that
is what a user sees and what the fixtures compare.

- **`a half that is not representable as a Double still rounds up`** — `1.015` → `1.02`,
  `1.005` → `1.01`. First in the file on purpose: a test asserting only the scale would pass
  against a fix that still rounded wrongly.
- **`halves that happened to work still work`** — `2.675`, `8.835` were correct before by luck of
  the binary representation, so the fix must not trade one set of wrong answers for another.
- **`the result carries the scale that was asked for`** — the (b) half, including the
  `round(3.04, 1)` → `3.0` case that the fixture column made the argument for.
- **`the scale comes from the request, not from the input`** — `round(1.4999, 1)` is `1.5` and
  `round(1.4999, 2)` is `1.50`.
- **`one argument still rounds to an integer`** — the behaviour that must *not* change.
- **`HALF_UP is symmetric about zero`** — since `Math.round` is not.
- **`a non-finite value is passed through rather than throwing`** — `BigDecimal("NaN")` would throw.
- **`a Double is read by its shortest representation`** — keeps `round(0.1, 3)` from becoming a
  55-digit expansion.
- **`roundToCents carries two decimals, as its own documentation promised`** and
  **`roundToDecimalPlaces agrees with round, scale included`** — the family consistency.

Full gate: **4,105 JVM tests, parity 421, 587/587 conformance** — the parity count matters here,
because it is the only thing that runs a fixture through COPY and COMPILED as well as the
interpreter.

To get the plain form back where a script wants it, `toNumber(round(x, 2))` drops the trailing
zeros and leaves `40.6` alone — unlike `round(x)` or `floor(x)`, which would round it. Note that
arithmetic will not strip it: `40.0 + 0` is `40.0`, because B35 treats scale as part of the value.

## The contract: a rounding function returns the scale it was asked for

| function | scale comes from | `… (20)` renders |
|---|---|---|
| `round(x, n)` | the caller, as an argument | **`20.00`** |
| `roundToDecimalPlaces(x, n)` | the caller | **`20.00`** |
| `roundToCents(x)` | the function's own meaning — cents *are* two decimals | **`20.00`** |

All three agree, which is the simpler rule: **ask for `n` decimals, get `n` decimals.**

To go back to the plain form, say so in the script — `toNumber(round(x, 2))` drops the trailing
zeros and leaves `40.6` alone, unlike `round(x)` or `floor(x)` which would round it. Note that
arithmetic will *not* strip it for you: `40.0 + 0` is `40.0`, because B35 treats scale as part of
the value. Two decimals on screen is a presentation question, and the library already
answers it — with strings, which is the honest type for a presentation:

```
formatNumber(20, "0.00")    →  "20.00"
renderUSNumber(20, 2)       →  "20.00"
renderEUNumber(20, 2)       →  "20,00"
formatCurrency(20, "EUR")   →  "€20.00"
```

That is the same division of labour as the CLI's
([F33](../features/F33-output-presentation-and-the-table-view.md)): `--to` says what the data **is**,
`--format` says how it is **shown**. `round` is on the data side; these four are on the presentation
side. Had (b) been "fixed", every integral result would have gained a fractional part — an averaged
salary rendered `1200.0` — which is a worse answer, not a better one, and is exactly what the four
fixtures were protecting.

Where a value genuinely *should* carry its scale, it already does, because that is B35's job and not
`round`'s:

```
2 * 10.00   →  20.00        exact arithmetic preserves the written scale
20.00       →  20.00        and so does a round trip
```

### There is no single truth here, and it is worth knowing which one we picked

Languages disagree, and they disagree along one line — **whether the type carries scale**:

| | `round(20.00, 2)` | why |
|---|---|---|
| **Python** | `20.0` | a `float` has no scale to carry |
| **R** | `20` | same, and it drops the decimal when printing |
| **PHP** | `20` | same |
| **SQL** (PostgreSQL, MySQL, SQL Server) | **`20.00`** | `NUMERIC(p,s)` *does* carry scale, and `ROUND` keeps it |

UTL-X sits awkwardly between them, because B35 gave it both: a plain decimal is a `BigDecimal`, so
it **can** behave like SQL — unlike Python, which cannot. So this was a genuine fork, not a forced
move, and **SQL is the strongest argument against the choice made here**: of the four, it is the
closest analogue to a data-transformation language with exact decimals.

Look again at the middle column of that table. **R, PHP and JavaScript have nothing in it** — not
because they chose to drop the scale, but because a `float` cannot carry one. They offer string
formatting as the only route to `20.00`. The two languages with a real decimal type — SQL's
`NUMERIC` and Python's `Decimal` — **both keep it**.

So the survey is not "SQL versus Python". It is **decimal types versus float types**, and among
those that can carry a scale the vote is unanimous. UTL-X has a decimal type, so the float
languages are not precedent for it; they are evidence of a limitation it does not share.

That is what decided it, together with the fixture column above. The counter-argument — "most users
arrive from Python, so match Python" — is weak for the same reason: they arrive from languages that
had no choice.

```
20.00          →  20.00     the scale is in the data (B35)
2 * 10.00      →  20.00     exact arithmetic carries it
round(x, 2)    →  20.00     the caller asked for two decimals
roundToCents   →  20.00     two decimals are what cents are
```

One rule rather than a split: **a scale, once stated, is kept** — whether it was written in the
data, produced by exact arithmetic, or requested from a rounding function. Stripping it is a
presentation choice, and presentation is `formatNumber`'s job, or `toNumber`'s if a script wants the
plain number back.

`roundToCents` needed no judgement at all: **its own documentation already committed to two
decimals** while the code did not.

```
roundToCents(1.234) → 1.23
roundToCents(1.235) → 1.24
roundToCents(9.999) → 10.00      ← the KDoc said this; the code returned 10
```

So that half is a contract violation, not a preference. Every caller in `examples/json/` — invoices,
payroll, timesheet billing, customs declarations — is a monetary figure where a bare `10` is wrong,
and no conformance fixture pinned the old output.

Two metadata errors on the same function were fixed in passing, both of which made the catalogue
lie about it:

- `minArgs = 2, maxArgs = 2`, while the implementation takes **one** argument and throws
  "requires 1 argument". `parameters` also carried a `"rate: Rate value"` copied from a
  neighbouring function.
- `description = "Rounds **up** to the nearest cent"` reads as ceiling; it is HALF_UP, so
  `roundToCents(1.231)` is `1.23`. The notes said "standard commercial rounding" and were right,
  so the two disagreed with each other.

Three tests pin the family: `the result carries the scale that was asked for`,
`roundToCents carries two decimals, as its own documentation promised`, and
`roundToDecimalPlaces agrees with round, scale included`. A future change has a failing test and
this section to answer.
