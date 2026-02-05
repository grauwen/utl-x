BUG report 03

# BUG: `substring` out-of-bounds exception masked as "Undefined function"

## Summary

Two related bugs cause a confusing error when `substring` (or `truncate`) is called
with an end index greater than the string length.

1. **`substring` does not clamp the end index** -- it delegates directly to
   Java's `String.substring(start, end)` which throws
   `StringIndexOutOfBoundsException` when `end > length`.
2. **The interpreter's error handler masks the real exception** -- the catch
   block in `tryLoadStdlibFunction` swallows the `StringIndexOutOfBoundsException`,
   prints it to stderr as debug output, then falls through to throw
   `RuntimeError("Undefined function: substring")`.

The user sees *"Undefined function: substring"* instead of a bounds error,
making the problem nearly impossible to diagnose.

---

## Reproduction

### Input (excerpt from examples/json `10-employee-timesheet.json`)

```json
{
  "description": "Implemented authentication middleware for API gateway"
}
```

The description is **52 characters** long.

### UTL-X transformation (minimal reproducer)

```utlx
%utlx 1.0
input json
output json
---

{
  result: substring($input.description, 0, 80)
}
```

### Expected behaviour

`substring` should return `"Implemented authentication middleware for API gateway"`
(the full string, clamped at the actual length).

### Actual behaviour

```
DEBUG: Failed to execute stdlib function 'substring':
  StringIndexOutOfBoundsException: begin 0, end 80, length 52

Runtime error at 7:11: Undefined function: substring
```

The user is told the function does not exist, while the real problem is that
`end` (80) exceeds the string length (52).

---

## Root cause

### 1. `StringFunctions.substring` -- no bounds clamping

**File:** `stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/StringFunctions.kt` line 135

```kotlin
fun substring(args: List<UDM>): UDM {
    requireArgs(args, 2..3, "substring")
    val str = args[0].asString()
    val start = args[1].asNumber().toInt()
    val end = if (args.size > 2) args[2].asNumber().toInt() else str.length

    return UDM.Scalar(str.substring(start, end))  // <-- throws if end > str.length
}
```

Most scripting languages (JavaScript, Python, etc.) clamp out-of-range indices
silently. The fix is straightforward:

```kotlin
val clampedStart = start.coerceIn(0, str.length)
val clampedEnd   = end.coerceIn(clampedStart, str.length)
return UDM.Scalar(str.substring(clampedStart, clampedEnd))
```

The same issue likely applies to `truncate` if it delegates to `substring`
internally.

### 2. `Interpreter.tryLoadStdlibFunction` -- exception swallowed

**File:** `modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt` lines 1008-1032

```kotlin
} catch (ex: Exception) {
    val actualException = if (ex is InvocationTargetException && ex.cause != null) {
        ex.cause!!
    } else { ex }

    // Only these two types are rethrown as RuntimeError:
    if (actualException is FunctionArgumentException ||
        actualException is IllegalArgumentException) {
        throw RuntimeError("Error in function '$functionName': ${actualException.message}", location)
    }

    // Everything else is printed to stderr and SWALLOWED:
    System.err.println("DEBUG: Failed to execute stdlib function '$functionName':")
    System.err.println("  ${actualException.javaClass.simpleName}: ${actualException.message}")
    actualException.printStackTrace(System.err)
}

// Execution falls through here and throws the misleading error:
throw RuntimeError("Undefined function: $functionName", location)
```

`StringIndexOutOfBoundsException` is not an `IllegalArgumentException`, so it
is swallowed. The interpreter then throws **"Undefined function"** -- a
completely wrong diagnostic.

**Suggested fix:** rethrow *all* runtime exceptions from stdlib functions as
`RuntimeError` with the real message, or at minimum add
`StringIndexOutOfBoundsException` and `IndexOutOfBoundsException` to the
rethrow list.

---

## Impact

Any stdlib string or array function that receives an out-of-bounds index will
appear to be "undefined" to the user. This affects at least `substring` and
`truncate`, and potentially other functions that can throw unchecked Java
exceptions.
