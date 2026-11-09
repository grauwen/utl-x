# B37: serialisers saturate a large double to Long.MAX_VALUE

> **B = core bug** (shared core and the format serialisers built on it), the family of B01–B35.
>
> **B36 does not exist.** It was filed here briefly and reclassified as
> [EB03](EB03-compiled-comparison-coerces-types-so-strategies-disagree.md) once it was clear the
> defect was in `modules/engine`, not the shared core. The number is left unused rather than
> recycled.
> This is **B37**.

**Status:** **FIXED.**
**Priority:** Medium. It produces a wrong number rather than an imprecise one, but only for values
above 2⁶³, which most payloads never reach.
**Component:** the JSON, CSV and YAML serialisers. XML was already correct.

> **One-line:** `1e30` and `1e300` both serialised as `9223372036854775807` — not an approximation
> of either, and the *same* value for two numbers thirty orders of magnitude apart.

## Evidence

Identity transform of `{"a": 1e30, "b": 1e300}`:

| output format | before | after |
|---|---|---|
| json | `9223372036854775807`, `9223372036854775807` | `1.0E30`, `1.0E300` |
| csv | `9223372036854775807`, `9223372036854775807` | `1.0E30`, `1.0E300` |
| yaml | `9223372036854775807`, `9223372036854775807` | `1.0E30`, `1.0E300` |
| xml | `1.0E30`, `1.0E300` | unchanged — already correct |

## Root cause

Serialisers print an integral double without its fractional part, so that `1.0` comes out as `1`.
Three of them tested integrality with a modulo:

```kotlin
if (value is Double && value % 1.0 == 0.0 && …) writer.write(value.toLong().toString())
```

Two facts combine badly:

1. `d % 1.0 == 0.0` is true for **every** double above 2⁵² — at that magnitude the representation
   has no fractional bits left, so every value is "integral".
2. `Double.toLong()` **saturates** rather than overflowing or throwing: anything above `Long.MAX_VALUE`
   converts to `Long.MAX_VALUE`.

So the predicate said "safe to print as an integer" precisely where it was least safe, and the
conversion then silently clamped. The failure is quiet, format-dependent, and loses all magnitude.

XML escaped it because its test was written differently:

```kotlin
if (d == d.toLong().toDouble())
```

which is a **round trip** — print it as an integer only if converting back gives the value you
started with. For `1e30` that is `9.223372036854776E18 != 1.0E30`, so XML correctly fell through to
the decimal path.

## The fix

XML's predicate was the correct one, so it became the shared definition
(`ExactNumber.integralLongOrNull`) and the other three now use it. It also rejects non-finite
doubles, which previously each serialiser guarded separately or not at all.

Sharing matters more than the one-line change: four serialisers had four spellings of the same
decision, three of them wrong in the same way. One definition means they cannot drift apart again —
the same reason the ValidationResult shape test and the numeric canonicaliser were each reduced to a
single definition.

## Behaviour changes callers see

Only for doubles outside `Long` range, where the previous output was wrong. Values that round-trip
through `Long` print exactly as before, so `1` stays `1` and `2.0` stays whatever B35's rules make
of it.

No existing fixture or test changed.

## Tests

- `ExactNumberTest` — the round-trip contract, including two assertions that pin *why* the old
  predicate was wrong (`1e30 % 1.0 == 0.0` is true, and `1e30.toLong()` is `Long.MAX_VALUE`), so the
  reasoning survives independently of this report.
- `large_double_not_saturated` conformance fixture, at `%utlx 1.0`. Output is **XML on purpose**: the
  JSON comparison parses both sides to a float, where `1e30` and a saturated
  `9223372036854775807` are not reliably distinguishable — a JSON fixture could not have detected
  the defect it exists to prevent. The fixture also carries a decimal and a small integer, so a fix
  that broke the ordinary cases fails too.

## Not fixed: non-finite values in JSON output

Found alongside this and **left open deliberately** — see
[B38](B38-non-finite-numbers-serialise-as-invalid-json.md). A YAML input carrying `.inf` or `.nan`
serialises to JSON as the bare tokens `Infinity` and `NaN`, which are not valid JSON. Unlike the
saturation bug there is no single obviously-correct answer — reject, emit `null`, or emit a string —
and the choice should be profile-driven, which needs serialisation-profile plumbing that does not
exist yet. Guessing would have been worse than recording it.

## Related

- [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) — found while
  auditing B35's coverage; the exact-type rendering branch sits directly in front of this predicate
  in every serialiser touched here.
- [EB03](EB03-compiled-comparison-coerces-types-so-strategies-disagree.md) — the other issue that
  audit turned up.
