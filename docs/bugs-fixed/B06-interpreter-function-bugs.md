# B06: Interpreter Function Handling Bugs

## Summary

Two related interpreter bugs prevent certain transformations from executing when user-defined functions are used in specific contexts.

## Bug 1: FunctionCall Cannot Be Cast to LetBinding

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

### Workaround

None known. Simplifying the transformation by removing either the function call or the let binding may help, but this limits expressiveness.

---

## Bug 2: Cannot Convert Function to UDM

### Error Message
```
Cannot convert function to UDM
```

### Affected Transformations
- `29-tr-fatura-to-shipping-manifest.utlx`

### Reproduction

The bug occurs when user-defined functions are used in certain complex expressions:

```utlx
function PackageType(weight, count) {
  let total = weight * count in
  if (total > 500) "PALLET"
  else if (total > 50) "CRATE"
  else "BOX"
}

{
  items: $input.items |> map(item => {
    let estimatedWeight = item.weight / 10;
    {
      packageType: PackageType(estimatedWeight, 1),  // Triggers the bug
      // ... more fields
    }
  })
}
```

### Probable Cause

When evaluating the function call, the interpreter returns a function reference (closure) instead of the function's return value. This happens in specific contexts where:
1. The function uses `let ... in` syntax internally
2. The function is called with computed arguments (not literals)
3. The result is assigned to an object property

The UDM serializer then fails because it receives a function object instead of a scalar/array/object value.

### Workaround

None known.

---

## Affected Code Locations

Based on the stack traces, the bugs likely originate in:

- `modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt`
  - Function call evaluation
  - Let binding scope management
  - Lambda/closure handling

## Priority

**Medium-High** - These bugs block 3 out of 12 example transformations (25%) and limit the expressiveness of user-defined functions in complex scenarios.

## Related Issues

- The bugs do NOT affect:
  - Simple function calls at the top level
  - Functions without `let` bindings
  - Built-in stdlib functions

## Test Cases

Working pattern (no bug):
```utlx
function Simple(x) { x * 2 }
{ result: Simple(5) }  // Works: returns { result: 10 }
```

Failing pattern (Bug 1):
```utlx
function Categorize(x) { if (x > 10) "HIGH" else "LOW" }
{
  items: [1, 20, 5] |> map(n => {
    let cat = Categorize(n);
    { value: n, category: cat }
  })
}
// Fails: FunctionCall cannot be cast to LetBinding
```

Failing pattern (Bug 2):
```utlx
function Compute(a, b) {
  let result = a + b in
  result * 2
}
{
  items: $input.values |> map(v => {
    let x = v.amount / 10;
    { computed: Compute(x, 5) }
  })
}
// Fails: Cannot convert function to UDM
```

Fix: B06 Bug 1 - FunctionCall Cannot Be Cast to LetBinding

 Problem

 When using let ... in syntax inside object literals within lambdas, the parser throws:
 class org.apache.utlx.core.ast.Expression$FunctionCall cannot be cast to
 class org.apache.utlx.core.ast.Expression$LetBinding

 Affects transformations: 09-service-contract, 13-eu-customer-order-de

 Root Cause

 File: modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt

 Line 1008: Unsafe type cast in parseObjectLiteral():
 letBindings.add(letExpr as Expression.LetBinding)  // UNSAFE CAST!

 The problem is that parseLetBinding() (lines 1168-1194) can return TWO different types:
 - Expression.LetBinding for simple let x = value
 - Expression.FunctionCall for let x = value in body (desugared to lambda application)

 But the calling code at line 1008 assumes it ALWAYS gets LetBinding.

 Failing Pattern

 map(item => {
   let computed = someFunction(item.value) in  // Uses "in" keyword
   {
     result: computed  // Object literal triggers parseObjectLiteral()
   }
 })

 Fix

 Step 1: Update parseObjectLiteral() at line 1008

 Change from:
 letBindings.add(letExpr as Expression.LetBinding)

 To handle both return types:
 when (letExpr) {
     is Expression.LetBinding -> letBindings.add(letExpr)
     is Expression.FunctionCall -> {
         // Desugared let...in expression - add to expressions to evaluate
         // The FunctionCall wraps a lambda that should be executed
         scopedExpressions.add(letExpr)
     }
     else -> error("Unexpected expression type from let binding: ${letExpr::class.simpleName}")
 }

 Step 2: Update object literal construction

 The object literal needs to wrap any scoped expressions (FunctionCalls from let...in) around the final object construction. This may require restructuring how the final expression is built.

 Step 3: Add unit tests

 File: modules/core/src/test/kotlin/org/apache/utlx/core/parser/LetInObjectLiteralTest.kt

 Test cases:
 1. Simple let ... in inside object literal
 2. let ... in with function call inside lambda
 3. Multiple let ... in bindings
 4. Nested let ... in expressions

 Files to Modify

 1. modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt - Fix unsafe cast at line 1008
 2. modules/core/src/test/kotlin/org/apache/utlx/core/parser/ - Add tests

