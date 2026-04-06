# B06: FunctionCall Cannot Be Cast to LetBinding

**Status: FIXED**

## Summary

Parser bug where `let ... in` syntax inside object literals caused a ClassCastException.

## Bug 1: FunctionCall Cannot Be Cast to LetBinding (FIXED)

### Error Message
```
class org.apache.utlx.core.ast.Expression$FunctionCall cannot be cast to class org.apache.utlx.core.ast.Expression$LetBinding
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

**File:** `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`

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
