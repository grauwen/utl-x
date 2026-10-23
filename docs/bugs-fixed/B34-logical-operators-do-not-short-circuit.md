# B34: `&&` and `||` do not short-circuit (and coerce their result to boolean)

> **B = core bug** (`:core` interpreter / parser), the family that also holds B01–B33 — distinct from
> engine (`EB`), IDE (`IB`) and validation (`VB`) bugs. This is **B34**.

**Status:** **OPEN** (found 2026-10-23 while documenting the `%utlx 1.1` validation WoW — the
"defensive check" guidance in [VB01](VB01-validate-assert-raw-check-crashes-on-missing-or-mistyped-data.md)
turned out to be unimplementable).
**Priority:** **High** — a dead operand is still evaluated, so logically unreachable code throws.
Every author-level guard idiom (`x != null && x > 0`) is broken, and the defect is **on `main`**,
affecting every script in every build, not just `%utlx 1.1`.
**Component:** `:core` `interpreter.kt` `evaluateBinaryOp` — `BinaryOperator.AND` / `OR`.

> **One-line:** `false && (1 / 0)` raises `Error: Division by zero`, and `1 || 2` returns `true`
> instead of `1` — both operands of a logical operator are always evaluated, and the result is
> coerced to a boolean.

## Evidence (via the built native CLI)

Script shape: `{ r: <expr> }`, input `{}`.

| Expression | Expected | Actual |
|---|---|---|
| `false && (1 / 0)` | `false` | ❌ **`Error: Division by zero`** |
| `true \|\| (1 / 0)` | `true` | ❌ **`Error: Division by zero`** |
| `$input.qty != null && $input.qty > 0` | `false` | ❌ **`Error: Cannot compare these types`** |
| `1 \|\| 2` | `1` (value-returning) or `true` (boolean) — see §Open decision | `true` |
| `"" \|\| "x"` | `"x"` or `true` | `true` |
| `0 && 5` | `0` or `false` | `false` |
| `true \|\| false && false` | `true` | ✅ `true` — precedence is correct |

Precedence and associativity are fine; this is purely an **evaluation-order** defect, plus a
separate result-type question.

## Root cause

`modules/core/src/main/kotlin/com/glomidco/utlx/core/interpreter/interpreter.kt:781`:

```kotlin
private fun evaluateBinaryOp(expr: Expression.BinaryOp, env: Environment): RuntimeValue {
    // Handle nullish coalescing with short-circuit evaluation          ← :782, correct
    if (expr.operator == BinaryOperator.NULLISH_COALESCE) {
        val left = evaluate(expr.left, env)
        return if (isNull) evaluate(expr.right, env) else left          // right is conditional
    }

    // For all other operators, evaluate both sides                     ← :800, the bug
    val left = evaluate(expr.left, env)
    val right = evaluate(expr.right, env)

    return when (expr.operator) {
        ...
        BinaryOperator.AND -> RuntimeValue.BooleanValue(left.isTruthy() && right.isTruthy())
        BinaryOperator.OR  -> RuntimeValue.BooleanValue(left.isTruthy() || right.isTruthy())
    }
}
```

Both operands are forced at `:800-801`, **before** the operator is dispatched on. The `&&` on the
`AND` line is **Kotlin's** `&&`, short-circuiting over two values that have already been computed —
it reads like short-circuit evaluation while providing none at the UTL-X level. That is most likely
why this survived so long: the code looks correct at a glance.

`??` (`NULLISH_COALESCE`) sits immediately above with exactly the right shape — an early return
ahead of the eager pair. `AND`/`OR` were simply never given the same treatment.

`RuntimeValue.BooleanValue(...)` additionally discards the operand, so `||` cannot act as a
default-value operator on this path.

## Blast radius

**On `main`: uniform and wide.** `interpreter.kt` is on `main`, so every build evaluates every
`&&`/`||` eagerly. Affects mapping and validation alike. Consequences:

- **Any null/type guard is broken.** `x != null && x > 0` throws on absent or mistyped `x` —
  exactly the data a guard exists for.
- **`validate.assert` lambda checks always take the interpreter path** (`evaluateValidateAssert`
  calls `evaluate(checkVal.body, …)` directly), so no `&&` guard inside a `check` can ever protect
  its right operand, on any strategy. This is why `if (p.x == null) true else p.x > 0` is currently
  the only working defensive form — see
  [Validation Rules](../language-guide/validation-rules.md#-and--do-not-short-circuit).
- **VB01's rejected Option C is unimplementable as written** — it recommends
  `isNumber($input.price) and $input.price > 0`. Besides `and` not being a keyword, the conjunction
  would not guard the comparison even spelled `&&`.
- **`x || default`** silently yields `true` rather than the fallback. (`??` is unaffected and
  remains the correct default-value operator.)
- **Wasted work** on every logical operator — both sides always evaluated, including expensive
  selector walks.

**On `feature/utlx-1.1-semantic-validation`: additionally a strategy divergence.** The new
`:engine` COMPILED path (`strategy/compiled/ASTCompiler.kt:376`, 763 lines, **not present on
`main`** — added by F23 engine profile wiring) gets this *right*:

```kotlin
// Short-circuit for && and ||
if (expr.operator == BinaryOperator.AND || expr.operator == BinaryOperator.OR) {
    compileShortCircuit(ctx, expr); return
}
```

`compileShortCircuit` (`:406`) emits a real conditional jump — `DUP`, `isTruthy`, `IFEQ endLabel`,
and only past the label does it `POP` and compile the right operand. It also **leaves the operand
on the stack** ("If left is falsy, return left"), i.e. JS-style value-returning.

| | Interpreter (`:core`, on `main`) | COMPILED (`:engine`, branch only) |
|---|---|---|
| Short-circuits | ❌ both operands always | ✅ real jump over the right |
| `false && (1 / 0)` | `Error: Division by zero` | `false` |
| `1 \|\| 2` | `true` | `1` |
| Result | coerced `BooleanValue` | the operand itself |

So `main` is uniformly wrong (one evaluator, nothing to diverge), while the branch introduces a
**silent, data-dependent semantic split**: the same script yields a crash or a different value
depending on whether `ASTCompiler` managed to compile it — `CompiledStrategy` falls back to the
interpreter for unsupported AST nodes. The divergence becomes live on merge.

*(The COMPILED column is read from the bytecode emitter, not executed — the emitted jumps are
unambiguous, but no test drives that path for operators yet. See §Tests.)*

## Fix

Mirror the `??` shape, above the eager pair:

```kotlin
if (expr.operator == BinaryOperator.AND || expr.operator == BinaryOperator.OR) {
    val left = evaluate(expr.left, env)
    val leftTruthy = left.isTruthy()
    if (expr.operator == BinaryOperator.AND && !leftTruthy) return RuntimeValue.BooleanValue(false)
    if (expr.operator == BinaryOperator.OR  &&  leftTruthy) return RuntimeValue.BooleanValue(true)
    return RuntimeValue.BooleanValue(evaluate(expr.right, env).isTruthy())
}
```

**Where it lands:** the interpreter half is a `main` bug and should be fixed on `main` (or a fix
branch off it), *not* gated behind `%utlx 1.1`. Landing it before or with the 1.1 merge is what
keeps the two evaluation paths consistent.

### Open decision — boolean-coercing or value-returning?

The two paths currently disagree on more than evaluation order, so one answer has to be chosen:

- **Boolean (recommended, and what the sketch above does).** Keeps current CLI behaviour, matches
  `OperatorRegistry.kt:235` ("Returns true if both operands are true"), and leaves the
  default-value job to `??`, which already works correctly. Requires `ASTCompiler` to coerce at the
  end of `compileShortCircuit`.
- **Value-returning (JS/DataWeave-like).** Matches the new codegen and enables `x || default`, but
  changes results for any existing script relying on a boolean.

Either way **both strategies must agree**, and the cross-strategy test is the place to assert it.

## Tests

Added with this report (they **fail until the fix lands** — the suite goes to 535/537):

- `conformance .../core/operators/logical_and_short_circuit.yaml` — `&&` must not evaluate the
  right operand when the left is falsy; includes the real-world `x != null && x > 0` guard.
- `conformance .../core/operators/logical_or_short_circuit.yaml` — `||` must not evaluate the right
  operand when the left is truthy.

Both are deliberately **neutral on the open decision above**: every asserted expression returns the
same result under boolean and value-returning semantics, so they pin evaluation order only and will
not need rewriting whichever way the decision goes.

Still to add once the fix lands:

- An operator case in `ValidateAcrossStrategiesTest` (or an equivalent `:engine` test) asserting
  interpreter and COMPILED agree — the guard that would have caught the divergence.
- A fixture for the result-type decision, once made.
