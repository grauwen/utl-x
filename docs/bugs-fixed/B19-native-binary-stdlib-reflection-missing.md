# B19: Native Binary — Stdlib Functions Fail Due to Missing Reflection Config

**Status:** Fixed (May 2026) — Option B implemented (reflection eliminated)  
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

## Fixes Applied

### Commit 1: Reflection + resource config
1. Added `StandardLibrary`, `Functions`, `UTLXFunction`, `Interpreter` to `reflect-config.json`
2. Added `org.apache.utlx.stdlib` to `--initialize-at-build-time` in `build.gradle.kts`
3. Added Apache XML Security resource bundle to `resource-config.json` (B17)

### Commit 2: Remove broken build arg
- Removed `-H:ReflectionConfigurationFiles=src/main/resources/META-INF/native-image/reflect-config.json` from `build.gradle.kts`
- This path was resolved relative to the native-image working directory (not the project root), causing build failure
- The `reflect-config.json` in `META-INF/native-image/` is auto-discovered from the classpath — no explicit path needed

These were insufficient — the dynamic wrapper classes could not be registered.

### Commit 3: Option B — Eliminate reflection entirely (final fix)

**Problem with eager registration:** Registering all 657 functions at startup added ~50ms overhead, making the CLI noticeably slower for trivial operations (jq comparison concern).

**Solution: Lazy registration without reflection.** Functions are registered on first use, not at startup:

1. `TransformationService` builds a lookup map (`Map<String, (List<UDM>) -> UDM>`) from `StandardLibrary.getAllFunctions()` — built once (lazy singleton via double-checked locking), zero cost if no stdlib function is called
2. `Interpreter.setStdlibLookup(map)` passes the map to the interpreter — no functions registered yet
3. `tryRegisterFromLookup(name)` is called on first unknown function — O(1) HashMap lookup, registers the function, then calls it
4. Subsequent calls to the same function hit the `nativeFunctions` map directly — already registered

**Performance characteristics:**
- Startup: zero overhead (map built lazily on first stdlib call)
- First call to any stdlib function: ~0.01ms (HashMap lookup + register)
- Subsequent calls: direct (same as before)
- No `Class.forName`, no `getMethod`, no `invoke` — no reflection at all

**Why not override core functions:** `tryRegisterFromLookup` skips functions already in `StandardLibraryImpl.nativeFunctions` — this preserves core implementations (e.g., `round(value, decimals)`) that accept different argument counts than the stdlib version.

**Files changed:**
- `modules/core/.../interpreter/interpreter.kt` — added `setStdlibLookup()`, `tryRegisterFromLookup()`, wired into `tryLoadStdlibFunction()` before reflection fallback
- `modules/cli/.../service/TransformationService.kt` — lazy singleton `stdlibLookup` map, passed to interpreter at transform time

### UTLXe consideration: Eager pre-warming (EF02)

The lazy approach is correct for the CLI (startup speed matters, most invocations use few functions). For the UTLXe production engine, the opposite is true:
- The engine is a long-running process — startup cost is paid once
- First-message latency matters — one customer's request should not pay the initialization cost
- All 657 functions should be pre-registered at engine startup (during `--validate` or init phase)

When implementing EF02, add an `Interpreter.preRegisterAllStdlib()` method that eagerly walks the lookup map and registers all functions. Call it during engine initialization, not per-message. This eliminates the "slow first message" syndrome.

```kotlin
// For utlxe — call once at startup
fun preRegisterAllStdlib() {
    stdlibLookup.forEach { (name, _) -> tryRegisterFromLookup(name) }
}
```

This is NOT needed for the CLI (each invocation is a fresh process) but IS needed for the engine (long-running, latency-sensitive).

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

### Manual: Step 11 in release plan
After fix, run the 12-point native binary validation checklist (Step 11 in `docs/release/version-release-plan.md`). All tests must pass.

### Automated: GitHub Actions workflow
The native-build workflow (`.github/workflows/native-build.yml`) now runs a 10-point validation checklist after each native binary build, BEFORE uploading artifacts. If any test fails, the build fails and no broken binary is published.

Tests embedded in workflow:
1. `--version` — binary starts
2. `concat()` — stdlib function via reflection
3. XML input — XML parser in native
4. CSV input — CSV parser in native
5. YAML input — YAML parser in native
6. `sha256()` — crypto in native
7. `base64Encode()` — encoding in native
8. XSD enrichment — F08 USDL in native
9. F02 newline separators — parser change in native
10. User-defined function — function definition in native

This prevents a repeat of B17/B19 — broken binaries are caught at build time, not after release.

---

*Bug B19. May 2026. Critical — most stdlib functions broken in native binary.*
*Related to B17 (resource bundle) but a separate issue (reflection vs resources).*
