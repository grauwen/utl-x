# B07: Cannot Convert Function to UDM

## Summary

When user-defined functions with internal `let ... in` expressions are called with computed arguments inside `map` lambdas, the interpreter returns a function object instead of the function's return value, causing the UDM serializer to fail.

## Error Message
```
Cannot convert function to UDM
```

## Affected Transformations
- `29-tr-fatura-to-shipping-manifest.utlx`

## Reproduction

The bug occurs with this pattern:

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
      packageType: PackageType(estimatedWeight, 1)  // Fails here
    }
  })
}
```

### Conditions that trigger the bug:
1. User-defined function uses `let ... in` syntax internally
2. Function is called with computed arguments (variables, not literals)
3. Call happens inside a `map` lambda
4. Result is assigned to an object property

### Working patterns (no bug):
```utlx
// Direct call with literals - works
PackageType(100, 2)

// Call outside map - works
let result = PackageType(someValue, 1)

// Function without let...in - works
function Simple(x) { x * 2 }
map(items, i => { value: Simple(i.x) })
```

## Probable Cause

The interpreter evaluates the function call but returns the function closure instead of executing it and returning the result. This happens specifically when:

1. The function body contains `let ... in` (desugared to lambda application)
2. The function is called within a nested lambda context (inside `map`)
3. Arguments are expressions rather than literals

The closure capture or environment scoping may be incorrect, causing the function to be returned unevaluated.

## Investigation Areas

1. **Function call evaluation** in `interpreter.kt` (~lines 935-973)
   - Check if `FunctionValue` is being returned instead of evaluated

2. **Closure handling** when functions contain desugared `let ... in`
   - The function body is itself a `FunctionCall` (lambda application)
   - Nested function calls may not be fully evaluated

3. **Environment scoping** in nested lambda contexts
   - Variables from outer `map` lambda may affect function resolution

## Related

- B06 (Fixed): FunctionCall/LetBinding cast error - similar context but different root cause
- Both bugs involve user-defined functions in `map` lambdas with `let` expressions

## Workaround

Avoid using `let ... in` inside user-defined functions, or inline the logic:

```utlx
// Instead of:
function PackageType(weight, count) {
  let total = weight * count in
  if (total > 500) "PALLET" else "BOX"
}

// Use inline:
{
  items: $input.items |> map(item => {
    let total = item.weight * item.count;
    {
      packageType: if (total > 500) "PALLET" else "BOX"
    }
  })
}
```
