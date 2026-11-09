# EB13: compiled arithmetic turns a non-numeric operand into zero

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they
> call), the family of EB01–EB12. This is **EB13**.

**Status:** **FIXED.**
**Priority:** High. A missing field became `0` in a money calculation, and the record was reported
as successful.
**Component:** `:engine` — `RuntimeOps.add` / `subtract` / `multiply` / `divide` / `modulo` /
`negate`.

> **One-line:** every arithmetic operation used `toNumber(x) ?: 0.0`, so a null or non-numeric
> operand silently became zero where the interpreter raises.

## What it looked like

```utlx
map($input.items, item => try {
  { name: item.name, taxedPrice: item.price * 1.08, status: "success" }
} catch (e) {
  { name: item.name, taxedPrice: null, status: "error", error: e }
})
```

With `price: null`:

| | result |
|---|---|
| interpreter | raises → the catch fires → `status: "error"` |
| compiled | `taxedPrice: 0`, `status: "success"` |

A plausible-looking record reporting success for a row that had no price. In a money mapping, a
missing field becoming zero is the worst available answer — it is not detectably wrong downstream.

It also **hid [EB12](EB12-compiled-try-catch-emits-unverifiable-bytecode.md)**: because the
arithmetic never raised, the catch never ran, so the mis-compiled try region never executed.

## Three distinct faults

1. **`?: 0.0` on every operand.** The interpreter's `extractNumber` raises for a null or
   non-numeric value; the compiled path substituted zero. Now it raises with the interpreter's
   message. A *numeric string* still coerces, because `extractNumber` accepts one — mirrored, not
   improved.

2. **`add` concatenated whenever a number could not be read.** Its string fallback triggered on
   `toNumber(x) == null`, so `null + 2` produced the **string** `"null2"`. The interpreter decides
   by whether a side genuinely *is* a string, and now so does this.

3. **`negate` went through `Double`.** `-10` on an exact integer came back as `-10.0`, losing the
   exactness introduced for the numeric model. The exact negation rule now lives in
   `ExactArithmetic.negate` in `:core` and **both** the interpreter and the compiled path call it —
   the interpreter's private copy was deleted rather than left to drift.

## Tests

In `EB12TryCatchAndNullArithmeticTest` (`:engine`), alongside EB12's because the two defects only
appeared together:

arithmetic on null raising for `+ - * /`; non-numeric text raising; a numeric string still
coercing; unary minus on null raising; ordinary arithmetic unaffected — which is the case that
caught the `-10.0`; and the combined case showing the catch now firing.

Revert-verified separately: restoring `?: 0.0` fails 4, and restoring the inexact negation fails 1.

## Effect

Together with EB12, known divergences **7 → 5**. Both remaining fixtures in the `try_catch` family
now agree.

## Related

- [EB12](EB12-compiled-try-catch-emits-unverifiable-bytecode.md) — the defect this one concealed.
- [EB03](EB03-compiled-comparison-coerces-types-so-strategies-disagree.md) — the same shape in
  comparison: the compiled path reinterpreting a value rather than failing.
- [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) — the exactness work
  the negation gap belonged to.
