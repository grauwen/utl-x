# B23: Stdlib Functions with Placeholder Implementations — Lambda/Logic Never Invoked

**Status:** Fixed (categories 1-6). JWT verification deferred to F11.  
**Severity:** High (15+ registered stdlib functions produced wrong results silently)  
**Scope:** Multiple stdlib files — array, utility, serialization, regex, join functions  
**Affects:** JVM and native binary  
**Created:** May 2026  
**Fixed:** May 2026  
**Discovered during:** Systematic TODO/placeholder audit after B21/B22

---

## Summary

A systematic audit of all TODO/FIXME/Placeholder comments in the codebase revealed 15+ stdlib functions that are registered and callable but have placeholder implementations that silently produce wrong results. The common pattern: a lambda/predicate/mapper argument is received but never invoked.

## Category 1: Lambda never invoked (array functions)

All in `stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/EnhancedArrayFunctions.kt`:

| Function | Expected | Actual | Placeholder |
|---|---|---|---|
| `partition(arr, predicate)` | Split by predicate | All elements in "true" | `matching.add(element)` unconditionally |
| `countBy(arr, predicate)` | Count matching | Counts ALL | `true` placeholder |
| `sumBy(arr, mapper)` | Sum mapped values | Returns 0 | Mapper never called |
| `maxBy(arr, comparator)` | Max by comparator | Returns first element | Comparator ignored |
| `minBy(arr, comparator)` | Min by comparator | Returns first element | Comparator ignored |
| `distinctBy(arr, keyFn)` | Deduplicate by key | May appear correct by coincidence | Key function fallback |
| `avgBy(arr, mapper)` | Average mapped values | Returns 0 | Mapper never called |

## Category 2: Lambda never invoked (critical array functions)

In `stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/CriticalArrayFunctions.kt`:

| Function | Expected | Actual | Placeholder |
|---|---|---|---|
| `findIndex(arr, predicate)` | Index of first match | **Appears to work** — needs verification | `true` placeholder but result may be coincidentally correct |
| `findLastIndex(arr, predicate)` | Index of last match | **Appears to work** — needs verification | Same |
| `scan(arr, initial, fn)` | Running accumulation | Returns array of `<function>` strings | Lambda passed but never called as accumulator |

## Category 3: Tree operations — lambda never invoked

In `stdlib/src/main/kotlin/org/apache/utlx/stdlib/util/UtilityFunctions.kt`:

| Function | Expected | Actual | Placeholder |
|---|---|---|---|
| `treeMap(tree, fn)` | Transform every node | Returns tree unchanged | "return tree as-is (placeholder)" |
| `treeFilter(tree, predicate)` | Filter nodes by predicate | Returns tree unchanged | "Placeholder - in real impl would apply predicate" |
| `measure(fn)` | Execute fn and measure time | **Works partially** — returns result + elapsed, but elapsed may not be accurate | "Placeholder - requires function execution support" |

## Category 4: Serialization — wrong output

In `stdlib/src/main/kotlin/org/apache/utlx/stdlib/serialization/SerializationFunctions.kt`:

| Function | Expected | Actual | Placeholder |
|---|---|---|---|
| `renderXML(udm)` | XML string | `obj.toString()` (Kotlin Object representation) | **Not registered** — function not found at CLI level |
| `renderYAML(udm)` | YAML string | Same | **Not registered** |
| `renderCSV(udm)` | CSV string | Same | **Not registered** |

Note: These may not be registered in the function table — they fail with "Undefined function." If not registered, they're dead code with placeholder implementations, not a user-facing bug.

## Category 5: Regex — lambda never invoked

In `stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/AdvancedRegexFunctions.kt`:

| Function | Expected | Actual | Placeholder |
|---|---|---|---|
| `regexReplaceWith(str, pattern, fn)` | Replace matches using function | Returns matches unchanged | `matchResult.value` — no function call. **Not registered** at CLI level. |

## Category 6: Join functions — lambda partially broken

In `stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/JoinFunctions.kt`:

| Function | Issue | Placeholder |
|---|---|---|
| `evaluateKeyFunction(keyFn, item)` | When keyFn is Lambda, returns item identity instead of invoking | "For now, return the item itself as key" |
| Join combiner | Uses `{l: leftItem, r: rightItem}` instead of calling combiner function | TODO comment |

## Category 7: JWT — unverified result

In `stdlib-security/src/main/kotlin/org/apache/utlx/stdlib/jwt/JWTVerification.kt`:

| Function | Issue |
|---|---|
| JWT verify | "This is a placeholder implementation that returns unverified result" |

## Root cause

All these functions were scaffolded with the intention to add lambda invocation support later. The core array functions (`map`, `filter`, `reduce`, `find`, `some`, `every`, `flatMap`) work correctly because they're implemented in the interpreter with direct lambda support. The `*By` and utility functions in stdlib lack the same mechanism.

## How to fix

The `UDM.Lambda` type already has an `apply` function:

```kotlin
data class Lambda(val apply: (List<UDM>) -> UDM) : UDM()
```

For all Category 1-3 functions, the fix is the same pattern:

```kotlin
// Before (broken):
val predicateArg = args[1]
// TODO: Implement function calling mechanism
true // Placeholder

// After (fixed):
val predicate = args[1] as? UDM.Lambda
    ?: throw IllegalArgumentException("second argument must be a function")
predicate.apply(listOf(element)).asBoolean()
```

## Tests that need updating

The existing Kotlin tests assert **placeholder behavior** (they expect wrong results):

| Test file | Issue |
|---|---|
| `EnhancedArrayFunctionsTest.kt` | ~30 assertions expecting placeholder results (0, first element, all elements) |
| `CriticalArrayFunctionsTest.kt` | Uses `UDM.Scalar("placeholder")` instead of real lambdas |
| `EnhancedObjectFunctionsTest.kt` | Asserts placeholder behavior |
| `UtilityFunctionsTest.kt` | Asserts placeholder behavior for treeMap/treeFilter |

All these tests must be rewritten to pass actual `UDM.Lambda` arguments and assert correct results.

## Priority

| Category | Priority | Rationale |
|---|---|---|
| 1. EnhancedArrayFunctions (7) | **High** | **FIXED** — 57 tests |
| 2. CriticalArrayFunctions (3) | **High** | **FIXED** — 18 tests |
| 3. Tree operations (2) | **Medium** | **FIXED** — 17 tests |
| 4. Join functions (2) | **Medium** | **FIXED** — 6 tests |
| 5. Regex replaceWithFunction (1) | **Medium** | **FIXED** — 9 new + 2 updated tests |
| 6. Serialization renderXml/Yaml/Csv (3) | **High** | **FIXED** — 19 tests. Also added `parseOdata`/`renderOdata` (new) |
| 7. JWT verify | **Low** | **Deferred to F11** — lives in `stdlib-security`, not `stdlib`. Requires crypto work (F11: Advanced Security Functions) |

## What was fixed

### Source code changes

| File | Change |
|---|---|
| `stdlib/.../array/EnhancedArrayFunctions.kt` | 7 functions: `(args[n] as UDM.Lambda).apply()` replaces placeholder |
| `stdlib/.../array/CriticalArrayFunctions.kt` | `findIndex`, `findLastIndex`, `scan`: lambda invocation added |
| `stdlib/.../array/JoinFunctions.kt` | `evaluateKeyFunction` Lambda case + join combiner invoke lambda |
| `stdlib/.../util/UtilityFunctions.kt` | `treeMap` and `treeFilter`: traverse tree, apply lambda to leaf nodes |
| `stdlib/.../string/AdvancedRegexFunctions.kt` | `replaceWithFunction`: invoke lambda per regex match |
| `stdlib/.../serialization/SerializationFunctions.kt` | `renderXml` uses `XMLSerializer`, `renderYaml` uses `YAMLSerializer`, `renderCsv` uses `CSVSerializer`. Added `parseOdata`/`renderOdata` pair. |
| `stdlib/build.gradle.kts` | Added `formats:json` and `formats:odata` dependencies |
| `stdlib/.../Functions.kt` | Registered `parseOdata` and `renderOdata` |

### Test files

| Test file | Tests | Covers |
|---|---|---|
| `EnhancedArrayFunctionsTest.kt` | 57 | Rewritten: all 7 functions with real lambdas, edge cases, error handling |
| `CriticalArrayFunctionsB23Test.kt` | 18 | findIndex, findLastIndex, scan with real lambdas |
| `CriticalArrayFunctionsTest.kt` | 3 updated | Old tests rewritten to use real lambdas |
| `TreeFunctionsB23Test.kt` | 17 | treeMap, treeFilter with real lambdas |
| `JoinFunctionsB23Test.kt` | 6 | joinWith with lambda key functions and combiners |
| `ReplaceWithFunctionB23Test.kt` | 9 | replaceWithFunction with real lambdas |
| `AdvancedRegexFunctionsTest.kt` | 2 updated | Old placeholder tests rewritten |
| `RenderFunctionsB23Test.kt` | 19 | renderXml/Yaml/Csv proper format output validation |

### Parse/Render symmetry (complete set)

| Parse | Render | Status |
|---|---|---|
| `parseJson` | `renderJson` | Works (was already correct) |
| `parseXml` | `renderXml` | **Fixed** — was returning Kotlin `toString()` |
| `parseYaml` | `renderYaml` | **Fixed** — was returning Kotlin `toString()` |
| `parseCsv` | `renderCsv` | **Fixed** — was returning Kotlin `toString()` |
| `parseOdata` | `renderOdata` | **New** — OData JSON with annotation handling |
| `parse(data, "format")` | `render(data, "format")` | Works — supports all 5 formats including "odata" |

---

*Bug B23. May 2026. Discovered during systematic TODO audit. 15+ stdlib functions fixed with 128 new/updated tests. JWT verification deferred to F11 (stdlib-security module, requires crypto implementation).*
