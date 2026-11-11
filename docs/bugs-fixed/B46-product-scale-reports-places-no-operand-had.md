# B46: a product reports decimal places no operand had

> **B = core bug** (the shared language stack: `:core`, `formats/*`, `:stdlib`), the family of
> B01–B45. This is **B46**.

**Status:** **FIXED**
**Priority:** Medium. Not a wrong value — a misleading one. The digits claim precision the data never
had.
**Component:** `:core` — `ExactArithmetic.multiply`.
**Found by:** a user reading B45's output and asking why `0.75 * 1.08` had four decimal places.

> **One-line:** multiplication's scale rule is the **sum** of the operand scales, so `1.5 * 1.08`
> reported `1.620` and `0.75 * 1.08` reported `0.8100` — places neither operand had.

## The asymmetry

`BigDecimal`, ANSI SQL `DECIMAL` and IEEE 754-2008 decimal all define the two rules this way, and
UTL-X implemented both faithfully:

| operation | scale rule | can it invent a place? |
|---|---|---|
| `+` / `-` | `max(s₁, s₂)` | **No** — the result's scale is always one of the operands' |
| `*` | `s₁ + s₂` | **Yes** — always, unless an operand is an integer |

So addition's trailing zeros are information: `1.50 + 1.50 = 3.00` says "both inputs were
two-decimal money, so the total is too". A product's extra zeros are not:

```
1.5  * 1.08  ->  1.620     the third place is an artifact
0.75 * 1.08  ->  0.8100    four places for a value worth 81 cents
1.50 * 1.50  ->  2.2500
```

`1.620` **is** `1.62` — same number, same value, two more characters claiming precision that nothing
in the data supports. In the domain UTL-X serves — invoices, prices, tax — that is actively
misleading.

This is why it is a defect and not a preference: a database column declared `DECIMAL(10,4)` has a
type to honour, so printing `0.8100` is correct there. A transformation output has no declared type;
it is data someone else reads, and the scale is the only precision claim it carries.

## The rule

Trim a product's trailing zeros, with a **floor at the widest operand's scale** — so every zero that
*was* in the data survives:

```kotlin
private fun normaliseProduct(product: BigDecimal, floor: Int): BigDecimal {
    val stripped = product.stripTrailingZeros()
    // stripTrailingZeros can hand back a NEGATIVE scale (1000 -> 1E+3); the floor is never
    // negative, so raising to it also restores plain form.
    return if (stripped.scale() < floor) stripped.setScale(floor) else stripped
}
```

Measured, it satisfies every case that motivated it — including the two that no single rule had
satisfied before:

| | before | after | why |
|---|---|---|---|
| `1.5 * 1.08` | `1.620` | **`1.62`** | trimmed to the widest operand (2) |
| `0.75 * 1.08` | `0.8100` | **`0.81`** | trimmed to 2 |
| `1.50 * 1.50` | `2.2500` | **`2.25`** | trimmed to 2 |
| `1.50 * 3` | `4.50` | `4.50` | floor 2 holds the money formatting |
| `1.5 * 2` | `3.0` | `3.0` | floor 1 holds it |
| `0.001 * 0.001` | `0.000001` | `0.000001` | 6 places are *needed*; the floor never caps |
| `1000 * 2` | `2000` | `2000` | the floor also undoes `stripTrailingZeros`' `2E+3` |
| `1.50 + 1.50` | `3.00` | `3.00` | addition untouched |
| `1.5 * 1.08 * 2` | `3.240` | **`3.24`** | chains normalise at each step |

Comparison is unaffected — `1.50 * 3 == 4.5` and `1.5 * 1.08 == 1.62` are both true, before and
after, because `ScalarCanonical` compares numerically.

### Where it is applied, and why there

In `ExactArithmetic.multiply`, not in a serialiser, for two reasons:

1. **The operand scales are only known there.** A serialiser sees the finished value and cannot tell
   whether a trailing zero came from the data or from the multiply rule.
2. **Both execution paths call it.** `Interpreter` and `RuntimeOps` (the compiled path) both go
   through `ExactArithmetic.multiply`, so they cannot drift — which is the failure mode every one of
   EB03–EB16 shared.

That second point is also why this is **not configurable yet**; see below.

## On making it configurable

The natural follow-up is a header option — a default plus a way to deviate — and it is the right
instinct. It is deliberately **not** in this change, for a specific reason rather than for scope:

The compiled strategies execute bytecode that calls `RuntimeOps.multiply` **statically**. Threading a
per-script setting into those call sites is real work, and getting it wrong produces precisely an
EB-class divergence: the interpreter honours the option, the compiled path ignores it, and the same
mapping gives two answers depending on the strategy. Putting the rule *inside* the shared function
makes that impossible — there is one behaviour and both paths get it.

So the sequencing is: **the default now, unconditionally and un-divergeable; the option built
properly next**, with the threading solved and a cross-strategy test pinning it.

**The option is evidenced, not speculative** — which is a stronger position than `trimText`
([B41](B41-xml-trim-discards-escaped-whitespace.md)) or B38's `nonFinite` were in. DataWeave ran this
exact experiment: Mule 4.5 began **stripping** trailing zeros, users pushed back, and MuleSoft
shipped an escape hatch (`-Dcom.mulesoft.dw.stripTrailingZeroes=false`, or pinning `minMuleVersion`
to `4.4.0`). A comparable product tried both postures and found neither satisfies everyone. See the
verified comparison table in
[B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md).

Two design notes that follow from that, for whoever builds it:

- **Put it at the output boundary, not in arithmetic.** The value is identical either way, so this is
  presentation. `output xml { decimalForm: "canonical" }` / `output json { … }` keeps it per mapping
  and beside the existing format options, and sidesteps the bytecode-threading hazard entirely —
  a serialiser is called from one place. The catch is that a serialiser cannot see operand scales, so
  only a *blanket* strip is expressible there, not B46's floor; B46 therefore has to stay where it is
  and the option would layer on top.
- **Don't change the default arithmetic to get controlled precision.** DataWeave exposes explicit
  functions instead (`dw::util::Math::decimalMultiply` with an `OperationContext` carrying precision
  and rounding mode). That is the better model for the still-open division decision too: an opt-in
  rounding call rather than a silent global rule.

## Behaviour change

A product shows fewer trailing zeros. No value changes, no comparison changes, and nothing that was
previously *correct* becomes incorrect — `4.50` and `3.0` and `0.000001` are all preserved.

**Conformance: 563/563 on the JVM and 563/563 native.**

### Fixtures and tests re-authored

Four conformance fixtures held numeric text that no longer matched — note that **all four were green
throughout**, because a `format: json` fixture is compared by parsed value:

| fixture | was | now | stale since |
|---|---|---|---|
| `stdlib/object-lambda/mapValues_basic` | `1.620`, `2.160`, `0.8100` | `1.62`, `2.16`, `0.81` | B46 — and these were the file's *original* values |
| `stdlib/array/nestBy_basic` | `price: 15.5` | `15.500` | [B44](B44-stdlib-results-lose-the-exact-numeric-form.md) |
| `multi-input/06_json_csv_to_json` | `CostPrice: 15.5` | `15.50` | B44 |
| `examples/intermediate/try_catch_with_error_variable` | `taxedPrice: 10.8` | `10.80` | B45 |

`ExactArithmeticTest`'s scale-growth test moved from `402` to `400`: trimming removes the padding
zeros, but `1.05^n` is `105^n / 100^n` and `105^n` never ends in a zero, so the **significant** digits
still accumulate. Growth is a property of the arithmetic, not of the presentation, and the test's
lesson — round explicitly in a long reduction — is unchanged.

## Two further defects this sweep uncovered — NOT fixed here

Verifying the written form of every fixture in the corpus (rather than trusting the by-value
comparison) turned up two unrelated defects. Both are recorded for their own change:

1. **A `Double` ≥ 10⁷ serialises in exponent notation.**
   `parseIndianNumber("1,23,45,678.90")` emits `1.23456789E7`; the fixture says `12345678.9`. JSON
   permits the form and the comparison passes, so this has been invisible. `Double.toString()`
   switches to scientific notation at 10⁷ and the serialiser's fallback is `d.toString()`. Also seen
   as `1.2345678901234569E14` from `123456789012345678 / 1000`. Many consumers mishandle exponent
   form in a price field.
2. **`toNumber()` loses the written scale.** `examples/real-world/sap_integration` expects
   `amount: 125.50` from an XML `125.50` and gets `125.5` — the same exactness edge as B44/B45, in
   the string→number conversion.

## Tests

`B46ProductScaleTest` (`modules/core`), 9 tests: artifacts trimmed; the money and one-place floors
held; the floor never capping a value that needs more places; integers staying plain rather than
`2E+3`; negatives; addition untouched; the `Double` fallback untouched; and one test asserting the
**value is unchanged** (`1.620.compareTo(1.62) == 0`) — the rule is presentation, not arithmetic.

Revert-verified: restoring `a.multiply(b)` fails **2 of the 9** — the two artifact tests. The other
seven pass either way, which is what shows the rule only removes padding.

## Related

- [B45](B45-stdlib-does-not-follow-the-decimal-rule-the-operators-use.md) — made the stdlib follow
  the standard rules, which is what exposed this one.
- [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) — the exact numeric
  model these rules operate on.
- Division remains the one open numeric decision (DECIMAL128 / HALF_EVEN), and `avg` waits on it.
