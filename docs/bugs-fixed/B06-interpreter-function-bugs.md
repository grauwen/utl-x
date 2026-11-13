# B06: FunctionCall Cannot Be Cast to LetBinding

**Status:** Closed — 2026-11-13, verified no longer reproducing (see below)

**Status: FIXED**

## Summary

Parser bug where `let ... in` syntax inside object literals caused a ClassCastException.

## Bug 1: FunctionCall Cannot Be Cast to LetBinding (FIXED)

### Error Message
```
class com.glomidco.utlx.core.ast.Expression$FunctionCall cannot be cast to class com.glomidco.utlx.core.ast.Expression$LetBinding
```

### Affected Transformations
- `09-service-contract-to-billing-schedule.utlx`
- `13-eu-customer-order-de-to-shipping-label.utlx`

### Reproduction

The bug occurs when a user-defined function is called within a `map` operation that also uses `let` bindings:

```utlx
function MyFunc(x) {
  if (x > 10) "HIGH" else "LOW"
}

{
  items: $input.items |> map(item => {
    let computed = MyFunc(item.value);  // This pattern triggers the bug
    {
      result: computed
    }
  })
}
```

### Probable Cause

The interpreter appears to incorrectly handle the AST when:
1. A user-defined function is called inside a lambda
2. The lambda also contains `let` bindings
3. The result is used in an object construction

The interpreter may be confusing `FunctionCall` nodes with `LetBinding` nodes during evaluation, possibly in the scope resolution or closure handling code.

### Fix Applied

**File:** `modules/core/src/main/kotlin/com/glomidco/utlx/core/parser/parser_impl.kt`

**Line 1008:** Changed unsafe cast to handle both `LetBinding` and `FunctionCall` returns:

```kotlin
when (letExpr) {
    is Expression.LetBinding -> letBindings.add(letExpr)
    is Expression.FunctionCall -> {
        // Desugared let...in expression - return as block content
        consume(TokenType.RBRACE, "Expected '}' after let...in expression")
        val allExpressions: List<Expression> = letBindings + letExpr
        return Expression.Block(allExpressions, Location.from(startToken))
    }
    else -> throw error("Unexpected expression type")
}
```

---

## Bug 2: Moved to B07

The "Cannot convert function to UDM" bug has been moved to [B07-function-to-udm-conversion.md](B07-function-to-udm-conversion.md) as it is a separate issue in the interpreter.

---

## Closed 2026-11-13 — verified no longer reproducing

Found during a backlog sweep: this report still said **Open**, but the exact reproducer from this report now evaluates correctly.

```bash
# function MyFunc called from a `let` inside a map lambda
$input.items |> map((item) -> { let computed = MyFunc(item.value), result: computed })
=> [{"result":"LOW"},{"result":"HIGH"}]
```

No cast failure. The interpreter's handling of let bindings inside lambdas was
reworked by [B52](B52-type-checker-reports-every-stdlib-function-as-an-undefined-variable.md)
and [EB17](EB17-a-captured-binding-is-readable-but-not-callable.md); this is almost certainly
where it was fixed.

A stale `Open` is as misleading as a stale `Fixed` — it hides real work behind noise. The
behaviour is now pinned by `conformance-suite/utlx/tests/edge-cases/stale_open_bug_regressions.yaml`,
so closing it cannot be undone silently.
