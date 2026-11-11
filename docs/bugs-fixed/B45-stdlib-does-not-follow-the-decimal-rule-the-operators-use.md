# B45: the stdlib does not follow the decimal rule the arithmetic operators already use

> **B = core bug** (the shared language stack: `:core`, `formats/*`, `:stdlib`), the family of
> B01–B44. This is **B45**.

**Status:** **FIXED**
**Priority:** Medium–High. Silent loss of written precision, plus corruption of integers above 2⁵³
inside `sum`.
**Component:** `:core` — `Interpreter.runtimeValueToUDM`; `:stdlib` — `Aggregations`.
**Found by:** the cross-strategy harness, as the `mapValues_basic` divergence, after it learned to
drive the named-input fixtures.

> **One-line:** the same arithmetic was exact as an operator and not inside a stdlib call, so
> `$p.apple * 2` gave `3.0` while `mapValues($p, v => v * 2)` gave `3`.

## There was no design decision to make

This was first written up as an open question — whether a *computed* result should keep its scale.
Measuring against Java `BigDecimal` settled it: **UTL-X's operators already implement the decimal
standard exactly**, and the rule differs by operation, which is why the two cases look inconsistent
but are not.

| | UTL-X | BigDecimal | rule |
|---|---|---|---|
| `1.50 + 1.50` | `3.00` | `3.00` | scale = **max**(s₁, s₂) |
| `1.50 - 1.50` | `0.00` | `0.00` | max |
| `1.50 * 3` | `4.50` | `4.50` | scale = **sum** s₁ + s₂ |
| `0.001 * 0.001` | `0.000001` | `0.000001` | sum |
| `1.50³` | `3.375000` | `3.375000` | sum |

ANSI SQL `DECIMAL`, IEEE 754-2008 decimal and XPath `xs:decimal` specify the same two rules. So the
rule is settled and implemented — the **stdlib simply did not follow it**, and the fixtures that
expected otherwise were stale.

Division remains the one genuinely open case, because unlike `+`, `-` and `*` it has no exact answer
(`1/3`), so a precision and rounding mode must be chosen. `avg` is deliberately untouched for that
reason.

## The two sites

### 1. `runtimeValueToUDM` dropped the exact form

The **third** such conversion, after `toUDM()` (fixed by B35) and `udmToRuntimeValue` (fixed by
[B44](B44-stdlib-results-lose-the-exact-numeric-form.md)). This is the direction that hands a stdlib
function its arguments and takes its lambda results back:

```kotlin
is RuntimeValue.NumberValue -> UDM.Scalar(value.value)            // before — Double only
is RuntimeValue.NumberValue -> UDM.Scalar(value.exact ?: value.value)   // after, as toUDM() does
```

### 2. `Aggregations` computed in `Double`

`sum` folded with `sumOf`, whose selector returns a `Double`; `min`/`max` recomputed a `Double`
instead of returning the element they had already chosen.

```kotlin
// sum — fold through ExactArithmetic, falling back to the Double sum as soon as any element is one
var total: Number = 0L
for (element in array.elements) { … total = ExactArithmetic.add(total, n, fallback) }

// min/max — return the element, compared through ScalarCanonical (the one ordering definition)
array.elements.reduce { a, b -> if (compareNumbers(…) <= 0) a else b }.asSelectedNumber()
```

`sum` was also **corrupting integers above 2⁵³** — the B37 defect in another place:

```
sum([9007199254740993, 1])   before 9007199254740992   after 9007199254740994
```

## Measured

| | before | after |
|---|---|---|
| `mapValues($p, v => v * 2)` | `3`, `1.5` | `3.0`, `1.50` |
| `$p.apple * 2` (operator) | `3.0` | `3.0` — unchanged |
| `sum([1.50, 1.50])` | `3` | **`3.00`** |
| `sum([9007199254740993, 1])` | `9007199254740992` | **`9007199254740994`** |
| `min([1.50, 2.50])` | `1.5` | **`1.50`** |
| `sum([1, 2, 3])` | `6` | `6` |
| `max([1.50, 2.5])` (mixed) | `2.5` | `2.5` — a Double operand means no exact answer |
| `avg([1.50, 1.50])` | `1.5` | `1.5` — division, deliberately untouched |

## Two mistakes made getting here, both worth recording

**A member function silently shadowed the helper.** The first version named its helper
`UDM.asNumber()`. `UDM` already has a **member** `asNumber(): Double` that returns `0.0` for a null,
a boolean or any non-numeric value — and a member always wins over an extension of the same name. It
compiled, ran, and resolved to the member, so:

- the exact fold fell back to `Double` on every element (`sum([1.50,1.50])` stayed `3`), and
- **validation disappeared**: `sum([1, null])` returned `1`, `min(["a"])` returned `"a"`,
  `max([true])` returned `true`, where all three had correctly raised before.

Renaming it to `numberAsWritten()` fixed both at once. The lesson is that the symptom of shadowing
here was a *silently wrong answer*, not a compile error — so the name has to differ for the
resolution to be visible. There is now a test whose comment says exactly this.

**`reduce` skips its lambda on a single-element list.** Validating inside the comparator therefore
let `min(["a"])` through. Every element is now validated before the reduce.

## Behaviour change

A computed value that previously narrowed to `Double` now carries its exact decimal form. Concretely:

- **Trailing zeros appear where the scale says they should**: `sum([1.50, 1.50])` is `3.00`.
- **A JSON `int` can become a `float`**: `mapValues(prices, v => v * 2)` with `1.5` yields `3.0`,
  not `3`, because the product's scale is 1. This is the one change a consumer could notice — a
  strict JSON Schema distinguishing `integer` from `number` would see a different type. It is the
  standard answer (and the same answer the `*` operator and both compiled strategies already gave),
  but it is worth stating rather than discovering.
- **IEEE-754 noise disappears**: a real fixture's expected value was `1059.2600000000002` and is now
  `1059.26`.
- Integer-only aggregation is unchanged in output; only the internal boxed type differs
  (`Long`/`BigDecimal` rather than `Double`).

### Fixtures and tests re-authored

Two conformance fixtures and three unit assertions encoded the pre-B35 Double model:

| fixture | was | now | why |
|---|---|---|---|
| `examples/real-world/financial_data_processing` | `balance_change: 1059.2600000000002` | `1059.26` | float noise removed |
| | `credits.total: 2500` | `2500.00` | the same fixture already expected `amount: 2500.00` for **the same value** — it had documented the inconsistency |
| | `financial_health.net_income: 2500` | `2500.00` | as above |
| `stdlib/object-lambda/mapValues_basic` | `doubledPrices`: `3`, `4`, `1.5` | `3.0`, `4.0`, `1.50` | product scale = s₁ + s₂; now matches what the `*` operator always returned |
| | `pricesWithTax`: `1.62`, `2.16`, `0.81` | `1.620`, `2.160`, `0.8100` | `× 1.08` adds scale 2: `1.5`→scale 3, `0.75`→scale 4 |

The `financial_data_processing` row is the clearest evidence these were stale rather than
authoritative: `total` and `amount` held the same number written two different ways, because one went
through `sum` and the other did not.

`AggregationsTest`'s three failing assertions were asserting the **boxed type** (`0.0`, `5.0`) where
they meant to assert the value; they now compare `.toDouble()`, which is what they were always
testing.

> **A trap worth naming, because it caught me twice.** The first version of these two fixtures was
> written from the runner's *diff message* rather than from the actual output. The message renders
> values through Python's `float`, so:
>
> - `2500.00` printed as `2500.0` — and `2500.0` is what got written in;
> - and the message reported only **2** differences for `mapValues_basic`, because `1.62` and
>   `1.620` compare **equal** as floats — so `pricesWithTax` was never flagged at all and stayed
>   stale.
>
> The suite passed in both states, because a `format: json` fixture is compared **by parsed value**.
> **A fixture can be textually wrong and green.** Worse, the first verification attempt was itself
> *vacuous*: it invoked the fixture without `--input name=file`, got no output at all, found no
> literals to compare, and reported "match exactly".
>
> The check that actually works: run it the way the runner does (named inputs via `--input`), assert
> the output is non-empty, and compare the **numeric literals positionally, as written** — 9 literals
> for `mapValues_basic`, 23 for `financial_data_processing`, all matching. The conformance note in
> memory records the same hazard from the other direction: numeric text can only be *pinned* by an
> XML or CSV fixture.

**Conformance: 563/563 on the JVM and 563/563 on the GraalVM native binary**, and the full Gradle
suite is green.

## Tests

`B45StdlibArithmeticExactnessTest` (`modules/engine`, so all three strategies are covered), 11 tests:

- a lambda inside `mapValues` gets the exact value, and the operator and the stdlib lambda now agree;
- `sum` follows the addition rule, and keeps integers exact above 2⁵³;
- `min`/`max` return the element they chose;
- unchanged: integer aggregation, `count`, a mixed array falling back to Double, `avg`, the empty
  cases, and numeric strings;
- **validation still rejects what it always rejected** — the test that exists because the shadowing
  bug above nearly shipped.

Revert-verified separately for each half: `runtimeValueToUDM` → **3 of 11** fail; `Aggregations` →
**3 of 11** fail. They overlap on `min`/`max`, which needs both halves (the exact value must arrive,
*and* the element must be returned).

## Consequence for the parity baseline

`stdlib/object-lambda/mapValues_basic` was removed from `KNOWN_DIVERGENCES`. The inventory is now 4,
in two classes: `COMPILED_UNDEFINED_FUNCTION` (3) and `COPY_OUTPUT` (1).

## Related

- [B44](B44-stdlib-results-lose-the-exact-numeric-form.md) — the previous direction of the same
  conversion; B45 is the one it faces.
- [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) — the exact numeric
  model, and the first `toUDM()` direction.
- [B37](B37-serializers-saturate-large-doubles-to-long-max.md) — `sum`'s 2⁵³ corruption is the same
  defect in a third place.
- [EB16](EB16-compiled-wildcard-selector-evaluates-to-null.md) — the harness change that surfaced
  this.
