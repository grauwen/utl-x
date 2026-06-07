# B25: `parseNumber` fails on the native binary — stdlib lookup not reaching the native image (NOT a missing impl)

**Status:** Open — root cause re-diagnosed (see below). Partial fix applied (no-swallow logging); native-image cause still to confirm + fix.
**Priority:** Critical — breaks the README example **and several shipped `.utlx` examples** on the released v1.2.1 **native** binary (Windows/macOS/Linux), for both XML and JSON inputs. **JVM build is unaffected.**
**Created:** June 2026
**Reported:** GitHub issue #3 (@skin27 / Raymond Meester) — Windows 11, UTL-X CLI v1.2.1, `utlx-windows-x64.exe` (Oracle GraalVM native).
**Related:** B19 (native-binary stdlib reflection — the no-reflection lookup this depends on, and the leftover reflection fallback this hits).

---

## Correction to the original diagnosis

The first version of this doc claimed *"parseNumber has no runtime implementation."* **That is wrong.** Verified on the JVM build of the exact released code (`modules/cli/build/libs/cli-1.2.1.jar`):

```
$ java -jar cli-1.2.1.jar transform pn.utlx in.json
{
  "n": 123.45,   // parseNumber("123.45")
  "t": 42,       // toNumber("42")
  "u": "HI",     // upper("hi")
  "s": 6         // sum([1,2,3])
}
```

`parseNumber` **works on the JVM.** It is implemented (`stdlib/.../type/ConversionFunctions.kt`) and registered (`stdlib/.../Functions.kt:785 register("parseNumber", ConversionFunctions::parseNumber)`, inside `registerConversionFunctions()`, which **is** called from `init` via `registerAllFunctions()` at `Functions.kt:99`). So it **is** present in `StandardLibrary.getAllFunctions()`.

**The bug is native-image-specific.** Same source, native binary → `NoSuchMethodException`.

## Problem (as reported)

```
> utlx-windows-x64.exe transform .\transformations\order-to-invoice.utlx .\examples\order.xml
DEBUG: Failed to execute stdlib function 'parseNumber':
  NoSuchMethodException: org.apache.utlx.stdlib.UTLXFunction.execute(java.util.List)
        at ...Interpreter.tryLoadStdlibFunction(interpreter.kt:1093)
        at ...Interpreter.evaluateFunctionCall(interpreter.kt:1058)
Error: Undefined function: parseNumber
```

Same error with JSON input.

## Root cause

There is exactly one native-safe path from the interpreter to the stdlib module: the **B19 lookup map**. The CLI builds it in `TransformationService.ensureStdlibLookup()` from `getAllFunctions()`, wrapping each function as `{ args -> func.execute(args) }` (a plain interface call — **no reflection**) and hands it to the interpreter via `setStdlibLookup()`.

When a function name is **not** in that lookup, `tryLoadStdlibFunction` falls through to a **reflection branch** (`interpreter.kt:1093`): `Class.forName(...).getMethod("execute", List)` → **`NoSuchMethodException` on the native image** (no reflection metadata) → the error above.

So on the native binary, `parseNumber` was **missing from the runtime lookup**, which means the lookup was **empty or partial in the native image**. The decisive aggravator:

```kotlin
} catch (e: Exception) {
    stdlibLookup = emptyMap()   // ← silently swallows ANY build failure
}
```

If building `getAllFunctions()` throws **in the native image** (e.g. a GraalVM reachability / class-init problem while running the 30+ `register(...)` method-reference calls), the lookup becomes **empty** and *every* stdlib-module function drops onto the broken reflection path — with **no log line** to show why.

### Why only `parseNumber` surfaced
`toNumber` is **also** hardcoded as an interpreter builtin (`StandardLibraryImpl.registerAll` in `interpreter.kt`), so it survives even with an empty lookup. `parseNumber` lives **only** in the stdlib module, so it's the one that breaks. That's also why **`toNumber` is a working workaround** (the advice given on issue #3). The reporter's transform happens to use `parseNumber` as its only stdlib-module function (the rest are builtins/operators), so it's the only failure that showed.

### Two concrete native-image misconfigurations (found in the CLI native config)

**(i) The whole stdlib package is initialized at build time.** `modules/cli/build.gradle.kts:88`:
```
--initialize-at-build-time=kotlin,kotlinx,…,org.apache.utlx.stdlib
```
This runs `StandardLibrary`'s `init` — which registers ~650 functions via Kotlin **method references**
(`ConversionFunctions::parseNumber`, …) — on the *build* JVM, then freezes the resulting
lambda-backed function map into the native image heap. That snapshot is fragile: any entries whose
synthetic lambda classes don't survive the heap capture go **missing at runtime**, so the lookup is
partial/empty and those calls fall to the (broken) reflection path. **If this is the cause, it
affects many stdlib-only functions, not just `parseNumber`** — masked because most demos use few
stdlib functions and the common ones (`toNumber`, `toString`) are also builtins.

**(ii) The reflection fallback registers the wrong class.** The reported error is
`NoSuchMethodException: org.apache.utlx.stdlib.UTLXFunction.execute(java.util.List)` — the
**top-level** `data class UTLXFunction` (`stdlib/.../Functions.kt:1692`). But
`modules/cli/src/main/resources/META-INF/native-image/reflect-config.json` registers a
**different, nested name**:
```
"name": "org.apache.utlx.stdlib.Functions$UTLXFunction"   ← not the real top-level class
```
So the actual `org.apache.utlx.stdlib.UTLXFunction` has **no `allDeclaredMethods` metadata**, and
`getMethod("execute", List)` on it throws `NoSuchMethodException`. Two independent native bugs are
stacked: the lookup misses the function (i), *and* the fallback can't recover (ii).

### Scope is not yet confirmed (only / some / all stdlib)
Whether this breaks **only `parseNumber`**, **a subset**, or **all** stdlib-module functions depends
on whether the build-time snapshot is empty (→ all) or partial (→ some). It is **not yet observed**
on the native binary. To determine it: run `upper("x")` and `sum([1,2])` on the native binary (both
fail → empty/all; only `parseNumber` fails → specific), or rebuild native with the no-swallow
logging from fix (A) and read the printed cause.

## Impact

- README headline example (`order-to-invoice.utlx`) + shipped examples using `parseNumber`
  (`examples/utlx/basic/xml-to-json-simple.utlx`, `examples/utlx/intermediate/template-matching.utlx`,
  `examples/utlx/advanced/recursive-templates.utlx`, `theia-extension/examples/transformations/02-xml-to-json.utlx`)
  → broken on the native binary.
- If the whole lookup is empty in native (likely), **all** stdlib-module functions are affected, not just `parseNumber` — masked because most demos lean on builtins.
- The silent `catch` made a critical native failure invisible.

## Reproduction

- **JVM (works):** `java -jar cli-1.2.1.jar transform pn.utlx in.json` with `pn.utlx` calling `parseNumber("123.45")` → correct output.
- **Native (fails):** same `.utlx` via the `utlx-*` native binary → `NoSuchMethodException` + `Undefined function: parseNumber`.
- **Confirm scope on native (ask reporter / rebuild):** call another stdlib-only fn, e.g. `upper("x")` or `sum([1,2])`. If those **also** fail → the lookup is empty in native (whole-stdlib failure). If only `parseNumber` fails → it's parseNumber-specific.

## Fix

**A. (DONE) Stop swallowing the lookup-build failure.** `ensureStdlibLookup()` now logs the exception (type + message + stack) and warns when the lookup is empty, instead of silently producing `emptyMap()`. This makes the next native build *report the real cause* instead of a mystery `Undefined function`. (`TransformationService.kt`.)

**B. (TODO) Fix the native lookup build.** With (A) in place, rebuild the native binary and read the
logged error. Prime suspect: the `--initialize-at-build-time=…,org.apache.utlx.stdlib` snapshot
(root cause (i)). Options, in order of preference:
- **Drop `org.apache.utlx.stdlib` from `--initialize-at-build-time`** so `StandardLibrary` initializes
  at *runtime* in the native image (the lambda map is then built in-process, exactly as on the JVM
  where it works). Verify startup cost is acceptable; the lookup is already lazy.
- If build-time init must stay for startup reasons, ensure every function-category class and its
  method-reference lambdas are reachable/registered so the snapshot is complete.

**C. (TODO) Remove the reflection fallback** in `tryLoadStdlibFunction` (`interpreter.kt:1093`). It
cannot work in the native image and only converts a clean "Undefined function 'X'" into a misleading
`NoSuchMethodException`. Behavior should be identical JVM vs native. **If the fallback is kept for
now**, at minimum fix the `reflect-config.json` entry: replace `org.apache.utlx.stdlib.Functions$UTLXFunction`
with the real top-level class `org.apache.utlx.stdlib.UTLXFunction` (root cause (ii)) so `getMethod("execute", List)` resolves.

**D. (TODO) Regression guard.** Add a test that asserts the stdlib lookup is non-empty and contains a representative sample (`parseNumber`, `upper`, `sum`, …), plus a conformance check that runs `parseNumber` end-to-end. Ideally run it against the native binary in CI so this can't regress silently.

## Code pointers
- `modules/cli/.../service/TransformationService.kt`: `ensureStdlibLookup()` (lookup build + no-swallow logging), `setStdlibLookup` call.
- `modules/core/.../interpreter/interpreter.kt`: `setStdlibLookup` / `tryRegisterFromLookup` (B19 no-reflection path, ~153–178), `tryLoadStdlibFunction` reflection branch (~1093, to remove).
- `stdlib/.../Functions.kt`: `registerConversionFunctions()` (`register("parseNumber", …)` at 785; called from `init`/`registerAllFunctions()` at 99).
- `stdlib/.../type/ConversionFunctions.kt`: `parseNumber` / `toNumber` / `parseDouble` impls.

## Notes
- The release also prints a `DEBUG:` line + full stack trace to the user on this failure — noisy for a shipped binary; clean up alongside (C).
- The original "type-checks but no runtime impl" framing was incorrect; the analysis `CompleteFunctionRegistry` entry for `parseNumber` matches a real, working runtime impl. The registry/runtime consistency concern is still worth a separate audit, but it is **not** the cause of issue #3.
