# B51 — `toString()` loses decimal scale while every other path preserves it

**Status:** Fixed — 2026-11-12
**Component:** `:stdlib` — `type/ConversionFunctions.kt`, `Functions.kt`
**Found:** 2026-11-12, comparing UTL-X against `jq` idiom by idiom
**Severity:** Medium — silent data alteration in string output; the value is right everywhere
else, which makes it hard to spot
**Relationship:** **B35 was incomplete.** Not a new defect; an unfinished one.

## Summary

The same number renders differently depending on which path produces the text:

| value | serializer | `toString(v)` | `"" + v` |
|---|---|---|---|
| `10.50` | `10.50` | **`10.5`** | `10.50` |
| `25.00` | `25.00` | **`25`** | `25.00` |
| `100.10` | `100.10` | **`100.1`** | `100.10` |
| `15.25` | `15.25` | `15.25` | `15.25` |

String concatenation preserves the written scale. `toString()` does not. Only values with
trailing zeros are affected, which is exactly the case B35 existed to protect.

## Reproduction

```bash
echo '{"p":10.50}' > p.json
cat p.json | utlx transform -e '$input.p'            -r   # 10.50  correct
cat p.json | utlx transform -e '"" + $input.p'       -r   # 10.50  correct
cat p.json | utlx transform -e 'toString($input.p)'  -r   # 10.5   WRONG
```

Realistic impact — building a label or a key:

```utlx
map($input.items, (i) -> i.sku + "=" + toString(i.price))
// ["a=10.5", "b=25", "c=15.25"]     prices silently reformatted
```

## Why this is B35's unfinished business, not a regression

Tested against the **released `cli-1.3.1.jar`** (built 2026-09-01) and today's build:

| | 1.3.1 (Sep 1) | today |
|---|---|---|
| serializer on `10.50` | `10.5` | **`10.50`** |
| `toString(10.50)` | `10.5` | `10.5` |

September was **consistent** — both paths were lossy. B35 (`7f31ff3a`, 2026-11-05, *"data
preservation and comparison"*) made the serializer exact and **did not touch the conversion
functions**; its diff covers `ValidateFunctions.kt`, a fixture and a serialization test, and
nothing under `type/`.

So nothing broke today. What happened is that one of two rendering paths was corrected a
week ago and the other was left behind — the project's recurring shape, and the same reason
B48 needed fixing in both the interpreter and `RuntimeOps`.

## The fix

`ExactNumber.renderScalarText(n)` now holds the one sequence that turns a number into
text, and three call sites share it:

1. an exact type renders **as written** (`renderExactOrNull` — a `BigDecimal` keeps scale),
2. otherwise an integral `Double` prints as an integer, but only when it round-trips
   through `Long` (`integralLongOrNull`; B37 — `d == d.toLong().toDouble()` is true for
   every large double and saturates),
3. otherwise the platform's `Double` formatting.

`JSONSerializer.serializeScalar` and the stdlib's `toString()` both call it;
`TransformCommand.renderRaw` inherits it through the serializer. Previously three sites
re-derived this and only the serializer was right.

## Verification

All three paths now agree, including the cases each used to get wrong:

| value | serializer | `toString()` | `"" + v` |
|---|---|---|---|
| `10.50` | `10.50` | `10.50` | `10.50` |
| `25.00` | `25.00` | `25.00` | `25.00` |
| `100.10` | `100.10` | `100.10` | `100.10` |
| `15.25` | `15.25` | `15.25` | `15.25` |
| `9007199254740993` | exact | exact | exact |
| `1.0e30` | `1.0E30` | `1.0E30` | `1.0E30` |
| `0.0000001` | `0.0000001` | `0.0000001` | `0.0000001` |

The last two matter: `1.0E30` confirms B37's saturation is not reintroduced, and
`0.0000001` confirms `BigDecimal.toPlainString()` is still used instead of plain
`toString()`, which would print `1E-7`.

Suites: cli + core + stdlib **2770 passed / 0 failed**, conformance **571/571**.

## Check at the same time

`toNumber()` is already recorded as scale-losing elsewhere in the backlog. Worth
confirming whether it has the same root cause, since the two functions are each other's
inverse and should round-trip:

```bash
cat p.json | utlx transform -e 'toNumber(toString($input.p))' -r   # 10.5
cat p.json | utlx transform -e 'toNumber($input.p)' -r             # 10.5
```

**Confirmed: `toNumber()` loses scale independently**, so it is a separate defect rather
than a knock-on of this one. It is not fixed here.

## Verification needed with the fix

- a conformance fixture emitting **XML or CSV**, not JSON — a `format: json` fixture is
  compared by parsed value, so `10.5` and `10.50` look identical and the test would pass
  while the bug persists
- the fixture also buys cross-strategy parity via `ConformanceStrategyParityTest`
- `./gradlew :modules:engine:test` — this is core numeric semantics, the exact category
  where the interpreter and the compiled path have diverged four times
