# B07: Cannot Convert Function to UDM

**Status: OPEN - Complex Issue**

## Summary

When user-defined functions with internal `let ... in` expressions are called with computed arguments inside `map` lambdas, the interpreter returns a function object instead of the function's return value, causing the UDM serializer to fail.

## Error Message
```
Cannot convert function to UDM
```

## Affected Transformations
- `29-tr-fatura-to-shipping-manifest.utlx`

## Complexity Assessment

This bug is **complex** due to the interaction of multiple language features:

1. **User-defined functions** - stored as closures with captured environments
2. **`let ... in` desugaring** - converted to immediately-invoked lambda expressions
3. **Nested lambda contexts** - `map` creates a lambda, function body is another lambda
4. **Environment/scope chaining** - multiple nested scopes must resolve correctly

The bug likely involves subtle issues in how closures capture and resolve variables across multiple nested evaluation contexts.

## Reproduction

```utlx
function PackageType(weight, count) {
  let total = weight * count in        // Desugared to: ((total) => ...)(weight * count)
  if (total > 500) "PALLET"
  else if (total > 50) "CRATE"
  else "BOX"
}

{
  items: $input.items |> map(item => {   // Lambda context 1
    let estimatedWeight = item.weight / 10;
    {
      packageType: PackageType(estimatedWeight, 1)  // Fails here
    }
  })
}
```

## The Desugaring Chain

When `let x = value in body` is parsed, it becomes:
```
FunctionCall(
  Lambda([x], body),   // Inner lambda
  [value]              // Arguments
)
```

So a function like:
```utlx
function Foo(a) {
  let b = a * 2 in
  b + 1
}
```

Becomes (conceptually):
```utlx
function Foo(a) {
  ((b) => b + 1)(a * 2)   // Immediately-invoked lambda
}
```

When `Foo` is called inside a `map` lambda:
```
map lambda → Foo call → desugared let (another lambda call)
```

This creates **three nested evaluation contexts**. Somewhere the innermost lambda is being returned instead of evaluated.

## Conditions That Trigger the Bug

| Condition | Required? |
|-----------|-----------|
| User-defined function | Yes |
| Function uses `let ... in` internally | Yes |
| Called with computed arguments | Yes |
| Called inside `map` lambda | Yes |
| Result assigned to object property | Yes |

## Working Patterns (No Bug)

```utlx
// Direct call with literals - works
PackageType(100, 2)

// Call outside map - works
let result = PackageType(someValue, 1)

// Function WITHOUT let...in - works
function Simple(x) { x * 2 }
map(items, i => { value: Simple(i.x) })

// Inline let...in (not in function) - works
map(items, i => {
  let total = i.weight * 2 in
  { value: total }
})
```

## Investigation Areas

### 1. UDM Conversion
Find where "Cannot convert function to UDM" is thrown - this happens when `RuntimeValue.FunctionValue` reaches the serializer.

### 2. Function Call Evaluation
In `interpreter.kt`, check `evaluateFunctionCall()`:
- When `function.body` is itself a `FunctionCall` (from `let...in`), is it fully evaluated?

### 3. Closure Capture
When a lambda is created, it captures the current environment. Check if the closure has the right scope when deeply nested.

### 4. Possible Root Causes

1. **Incomplete evaluation**: Desugared `let...in` lambda returned instead of invoked
2. **Environment mismatch**: Inner lambda's closure missing function parameters
3. **Evaluation short-circuit**: Early return with lambda instead of result
4. **Type confusion**: Evaluator returns `FunctionValue` instead of evaluating

## Key Files

- `modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt`
- `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt` (let...in desugaring)

## Workaround

Inline the logic instead of using `let ... in` in functions:

```utlx
// Instead of function with let...in:
{
  items: $input.items |> map(item => {
    let total = item.weight * item.count in
    {
      packageType: if (total > 500) "PALLET" else "BOX"
    }
  })
}
```

## Related

- **B06 (Fixed)**: FunctionCall/LetBinding cast error - same context, parser layer
