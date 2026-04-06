# B07: Cannot Convert Function to UDM

**Status: FIXED**

## Summary

When using the pipe operator `|>` with a lambda expression as the target (e.g., `value |> (x => {...})`), the interpreter returned the lambda itself as a FunctionValue instead of evaluating it with the piped value. This caused the UDM serializer to fail when trying to convert the result to output format.

## Error Message
```
Cannot convert function to UDM
```

## Root Cause

In `interpreter.kt`, the pipe operator (`Expression.Pipe`) had handling for `FunctionCall` targets but not for `Lambda` targets. When a lambda was used directly after the pipe, the code fell through to the `else` branch which just evaluated the lambda expression, returning a `FunctionValue` instead of calling it.

**Before fix (lines 399-418):**
```kotlin
is Expression.Pipe -> {
    val sourceValue = evaluate(expr.source, env)
    val pipeEnv = env.createChild()
    pipeEnv.define("$", sourceValue)

    when (expr.target) {
        is Expression.FunctionCall -> {
            // Handled correctly
        }
        else -> evaluate(expr.target, pipeEnv)  // Lambda returned as FunctionValue!
    }
}
```

## Fix

Added explicit handling for `Expression.Lambda` targets that binds the piped value to the lambda's parameter and evaluates the body:

```kotlin
is Expression.Lambda -> {
    val lambda = expr.target
    if (lambda.parameters.isEmpty()) {
        // No parameters - just evaluate the body with $ available
        evaluate(lambda.body, pipeEnv)
    } else {
        // Bind the piped value to the first parameter
        val lambdaEnv = env.createChild()
        lambdaEnv.define(lambda.parameters[0].name, sourceValue)
        evaluate(lambda.body, lambdaEnv)
    }
}
```

## Affected Transformations
- `29-tr-fatura-to-shipping-manifest.utlx`
- Any transformation using the pattern `expr |> (x => {...})`

## Reproduction (Before Fix)

```utlx
{
  Rate: first($input.items) |> (item => {
    {
      Name: item.name
    }
  })
}
```

This returned a FunctionValue instead of the object `{ Name: "..." }`.

## Files Modified
- `modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt`
- `modules/cli/src/main/kotlin/org/apache/utlx/cli/Main.kt` (CLI bug fix: error messages weren't displayed)

## Additional Fix: CLI Error Display

During investigation, discovered that the CLI wasn't displaying error messages. Fixed `Main.kt` to print the error message from `CommandResult.Failure`:

```kotlin
is CommandResult.Failure -> {
    if (result.message.isNotEmpty()) {
        System.err.println("Error: ${result.message}")
    }
    exitProcess(result.exitCode)
}
```

## Related

- **B06 (Fixed)**: FunctionCall/LetBinding cast error in parser
