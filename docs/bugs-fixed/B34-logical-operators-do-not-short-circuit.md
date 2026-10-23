# B34: `&&` and `||` do not short-circuit (and coerce their result to boolean)

> **B = core bug** (`:core` interpreter / parser), the family that also holds B01–B33 — distinct from
> engine (`EB`), IDE (`IB`) and validation (`VB`) bugs. This is **B34**.

**Status:** **FIXED (2026-10-23, pending commit)** — short-circuit added to the `:core` interpreter,
boolean coercion added to the `:engine` compiled path so the two agree. Found 2026-10-23 while
documenting the `%utlx 1.1` validation WoW — the "defensive check" guidance in
[VB01](VB01-validate-assert-raw-check-crashes-on-missing-or-mistyped-data.md) turned out to be
unimplementable. See §Implementation.
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
  calls `evaluate(checkVal.body, …)` directly), so before the fix no `&&` guard inside a `check`
  could protect its right operand on any strategy, leaving `if (p.x == null) true else p.x > 0` as
  the only working defensive form. Post-fix guidance is in
  [Validation Rules](../language-guide/validation-rules.md#writing-a-defensive-check).
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

*(When first reported, the COMPILED column was read from the bytecode emitter rather than executed.
It is now confirmed by execution: `B34CompiledShortCircuitTest` drives `ASTCompiler` directly and
reproduces the `1 || 2` → `1.0` divergence when the fix is removed. See §Tests.)*

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

### Resolved decision — boolean-coercing

The two paths disagreed on more than evaluation order, so the result type had to be settled:

- **Boolean — chosen.** Keeps existing CLI behaviour byte-for-byte (`1 || 2` stays `true`), matches
  `OperatorRegistry.kt:235` ("Returns true if both operands are true"), and leaves the
  default-value job to `??`, which already short-circuits correctly. No script changes meaning.
- **Value-returning (JS/DataWeave-like)** — rejected. It matched the new codegen and would enable
  `x || default`, but `??` already owns that idiom, and it would silently change results for any
  script relying on a boolean.

## Implementation (2026-10-23)

- **`:core` `interpreter.kt`** — `evaluateBinaryOp` gains an early return for `AND`/`OR` ahead of
  the eager operand pair, mirroring the `NULLISH_COALESCE` block above it: evaluate the left, and
  return immediately when it decides the result (`AND` + falsy → `false`, `OR` + truthy → `true`);
  otherwise evaluate the right and coerce. The now-unreachable `when` branches throw
  `"Logical <op> should have been handled earlier"`, the same guard `NULLISH_COALESCE` uses.
- **`:engine` `RuntimeOps.kt`** — new `@JvmStatic fun toBoolean(value: UDM)` beside `not`.
- **`:engine` `ASTCompiler.kt`** — `compileShortCircuit` emits `INVOKESTATIC RuntimeOps.toBoolean`
  after `endLabel`, so the compiled path returns a boolean instead of the deciding operand. The
  jump structure (which was already correct) is untouched.

Scope note: the interpreter half is the `main` defect; the two `:engine` changes exist only to keep
the branch-only compiled path in agreement with it.

## Tests

- `conformance .../core/operators/logical_and_short_circuit.yaml` — `&&` must not evaluate the
  right operand when the left is falsy; includes the real-world `x != null && x > 0` guard. ✅
- `conformance .../core/operators/logical_or_short_circuit.yaml` — `||` must not evaluate the right
  operand when the left is truthy. ✅
- Full conformance **537/537** on **both** JVM and the rebuilt GraalVM native binary
  (`language/validate` 8/8, `core/operators` 4/4 natively). Both fixtures failed with
  `Error: Division by zero` before the fix and pass after it.
- Verified by probe: short-circuit (`false && (1 / 0)` → `false`, `true || (1 / 0)` → `true`,
  `x != null && x > 0` on missing `x` → `false`); result type unchanged (`1 || 2` → `true`,
  `"" || "x"` → `true`, `0 && 5` → `false`); full truth table, `&&`-over-`||` precedence and `??`
  all intact.
- `:modules:core:test` and `:modules:engine:test` green.
- **`B34LogicalShortCircuitTest`** (`:core`, 16 tests) — the Kotlin regression guard. Rather than
  asserting only "no throw" (which cannot distinguish *skipped* from *ran and happened not to
  fail*), it registers a synthetic stdlib function `record(n)` that appends to a list: an empty
  list proves the operand was never evaluated. Covers both skip directions, the evaluate
  directions, a chained `&&`, the two division-by-zero symptoms, the null-guard cases, both truth
  tables, boolean coercion, `&&`-over-`||` precedence, and `??` (whose block neighbours the fix).
  Verified to be a true regression guard: with the fix temporarily reverted, **7 of the 16 fail**
  and the 9 that pass are exactly the ones B34 never broke.

  Two test-isolation notes worth keeping in mind for any similar test:
  `Interpreter.tryRegisterFromLookup` registers into `StandardLibraryImpl.nativeFunctions`, a
  **global** map, and the stored wrapper closes over the lambda it was first given. With
  per-instance recorder state, JUnit's fresh-instance-per-method means the first method to call
  `record` pins the closure to its own now-dead list and every later method sees an empty one —
  so the `isEmpty()` assertions would pass **vacuously**. The recorder is therefore companion
  state reset in `@BeforeEach`, and a first test asserts the recorder itself works, so the
  emptiness assertions can never silently become vacuous again. (This was a real failure during
  development, not a hypothetical.)

Both fixtures are deliberately **neutral on the result-type decision** — every asserted expression
yields the same value under boolean and value-returning semantics — so they pin evaluation order
only and stay valid if that decision is ever revisited.

- **`B34CompiledShortCircuitTest`** (`:engine`, 5 tests) — the cross-strategy guard, closing the
  gap left by the first report. It drives `ASTCompiler` **directly** rather than through
  `CompiledStrategy`, because that strategy silently falls back to the interpreter when
  `compile()` returns null: routed through it, a test could pass while measuring the interpreter
  and prove nothing about the compiled path. Every case asserts `compile(...) != null` first, so a
  future `isCompilable` change that stops compiling logical operators surfaces as a failure rather
  than quietly voiding the test. Covers compiled short-circuit (both operators), the compiled null
  guard, boolean coercion, and a 17-expression table asserting **interpreter and COMPILED return
  identical values**.

  Verified to be a true regression guard: with the `toBoolean` emission temporarily removed, 2 of
  the 5 fail with `expected: <true> but was: <1.0>` and
  `strategy divergence on \`1 || 2\``. The `toBoolean` coercion is therefore now verified by
  **execution**, not by reading the emitted call.
