# B19: Native Binary — Stdlib Functions Fail Due to Missing Reflection Config

**Status:** Open (fix attempted, awaiting rebuild verification)  
**Priority:** Critical (most stdlib functions broken in native binary)  
**Created:** May 2026  
**Related:** B17 (resource bundle — fixed), B18 (Node.js deprecation — separate)

---

## Problem

After fixing B17 (resource bundle crash), the native binary no longer crashes at startup. However, most stdlib functions fail at runtime with:

```
NoSuchMethodException: org.apache.utlx.stdlib.UTLXFunction.execute(java.util.List)
```

The interpreter loads stdlib functions reflectively via `Class.getMethod("execute", List::class.java)`. GraalVM native-image cannot resolve these reflection calls because the function wrapper classes are not registered in the reflection configuration.

## Which Functions Work vs Fail

| Function | Native binary | JVM | Why |
|----------|:---:|:---:|-----|
| `sha256()` | ✅ | ✅ | Loaded via `tryDirectFunctionInvocation` (non-reflective) |
| `$input.field` | ✅ | ✅ | Interpreter, no stdlib |
| User-defined `function` | ✅ | ✅ | Parsed directly, no reflection |
| `concat()` | ❌ | ✅ | Loaded via reflective `getMethod("execute")` |
| `c14n()` | ❌ | ✅ | Same reflective path |
| `formatDate()` | ❌ | ✅ | Same reflective path |
| `base64Encode()` | ❌ | ✅ | Same reflective path |
| `map()`, `filter()` | Untested | ✅ | Likely same issue |

## Root Cause

In `interpreter.kt` lines 1029-1040:

```kotlin
val stdlibClass = Class.forName("org.apache.utlx.stdlib.StandardLibrary")
val instanceField = stdlibClass.getField("INSTANCE")
val stdlibInstance = instanceField.get(null)
val getAllFunctionsMethod = stdlibClass.getMethod("getAllFunctions")
val functions = getAllFunctionsMethod.invoke(stdlibInstance) as Map<String, Any>

// This is the failing line — stdlibFunction.javaClass is a dynamic wrapper class
val executeMethod = stdlibFunction.javaClass.getMethod("execute", List::class.java)
```

The `stdlibFunction.javaClass` returns anonymous/lambda wrapper classes generated at runtime. GraalVM native-image cannot predict which classes these will be, so it doesn't register them for reflection.

## Fix Attempted

1. Added `StandardLibrary`, `Functions`, `UTLXFunction`, `Interpreter` to `reflect-config.json`
2. Added `org.apache.utlx.stdlib` to `--initialize-at-build-time` in `build.gradle.kts`
3. Added explicit `-H:ReflectionConfigurationFiles` path in `build.gradle.kts`

These may not be sufficient — the dynamic wrapper classes may need broader registration.

## Potential Full Fix

### Option A: GraalVM Tracing Agent

Run the JVM version with the tracing agent to auto-generate reflection config:

```bash
java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image \
  -jar modules/cli/build/libs/cli-1.1.0.jar transform test.utlx < input.json
```

Run multiple transformations (JSON, XML, CSV, all stdlib functions) to capture all reflection targets. The agent generates the exact `reflect-config.json` needed.

### Option B: Refactor Away from Reflection

Change the interpreter to NOT use reflection for stdlib function loading. Instead:
- Register all stdlib functions in the `Environment` at interpreter startup (direct references, no `Class.forName`)
- This eliminates the reflection dependency entirely
- Better for GraalVM AND faster at runtime

### Option C: Register All stdlib Classes

Add all stdlib package classes to reflect-config.json with a pattern or generate the list at build time.

### Recommendation: Option B (eliminate reflection)

Option B is the most robust — it removes the root cause rather than patching the configuration. The current reflective loading was a convenience for lazy initialization but creates fragility with GraalVM.

## Verification

After fix, run the 12-point native binary validation checklist (Step 11 in release plan). All 12 tests must pass.

---

*Bug B19. May 2026. Critical — most stdlib functions broken in native binary.*
*Related to B17 (resource bundle) but a separate issue (reflection vs resources).*
