# YAML Dynamic Keys - Implementation Status

**Date:** 2025-10-25
**Status:** ⚠️ CRITICAL FINDING - Lambda-based functions not implemented

---

## Executive Summary

While UTL-X has complete **theoretical support** for dynamic keys in both INPUT and OUTPUT scenarios, **critical functions are not yet implemented**. The `mapEntries()`, `filterEntries()`, `reduceEntries()`, and related functions that accept lambda arguments exist as **stub implementations only**.

### Impact

| Scenario | Status | Notes |
|----------|--------|-------|
| **INPUT - Static Keys** | ✅ Working | Direct member access works |
| **INPUT - Wildcard** | ✅ Working | `$input.servers.*` works |
| **INPUT - Introspection** | ✅ Working | `keys()`, `values()`, `hasKey()`, `entries()` all work |
| **INPUT - Bracket Notation** | ❓ Unknown | Not tested, likely works |
| **INPUT - Transform with mapEntries** | ❌ **NOT WORKING** | Lambda argument not handled |
| **OUTPUT - fromEntries** | ✅ Working | Test 08 passes |
| **OUTPUT - mapEntries** | ❌ **NOT WORKING** | Lambda argument not handled |

---

## Critical Discovery

### Location
`stdlib/src/main/kotlin/org/apache/utlx/stdlib/objects/EnhancedObjectFunctions.kt`

### Affected Functions

All functions in `EnhancedObjectFunctions.kt` that take lambda arguments:

1. **mapEntries(obj, mapper)** - Line 251
   - Status: Stub implementation
   - Code: `// TODO: Implement function calling mechanism` (line 262)
   - Impact: Cannot transform object keys/values during OUTPUT

2. **filterEntries(obj, predicate)** - Line 316
   - Status: Stub implementation
   - Code: `// TODO: Implement function calling mechanism` (line 327)
   - Impact: Cannot filter object properties

3. **reduceEntries(obj, reducer, initial)** - Line 382
   - Status: Stub implementation
   - Code: `// TODO: Implement function calling mechanism` (line 395)
   - Impact: Cannot aggregate object entries

4. **someEntry(obj, predicate)** - Line 121
   - Status: Stub implementation
   - Returns: Always `false` (line 143)

5. **everyEntry(obj, predicate)** - Line 190
   - Status: Stub implementation
   - Returns: Always `true` (line 206)

6. **countEntries(obj, predicate)** - Line 434
   - Status: Stub implementation
   - Returns: Total count, ignores predicate (line 449)

7. **mapKeys(obj, mapper)** - Line 486
   - Status: Stub implementation
   - Impact: Cannot transform just keys

8. **mapValues(obj, mapper)** - Line 542
   - Status: Stub implementation
   - Impact: Cannot transform just values

9. **divideBy(obj, n)** - Line 59
   - Status: ✅ IMPLEMENTED (no lambda needed)
   - Works correctly

### Root Cause

The interpreter cannot convert lambda functions to UDM for passing to stdlib functions:

```
Runtime error: Cannot convert FunctionValue to UDM for stdlib function call
	at org.apache.utlx.core.interpreter.Interpreter.runtimeValueToUDM(interpreter.kt:1101)
	at org.apache.utlx.core.interpreter.Interpreter.tryLoadStdlibFunction(interpreter.kt:926)
```

**Key Issue:** `runtimeValueToUDM()` in `interpreter.kt` cannot handle `FunctionValue`.

---

## Test Results

### Working Tests

#### Test 01: Static Keys ✅
```utlx
{
  production: {
    type: $datacontract.servers.production.type,
    host: $datacontract.servers.production.host
  }
}
```
**Status:** PASSING

#### Test 08: fromEntries (OUTPUT) ✅
```utlx
{
  servers: fromEntries(
    $metadata.serverConfigs |> map(server => [
      server.env,
      { type: server.type, host: server.host }
    ])
  )
}
```
**Status:** PASSING - fromEntries works because it takes array, not lambda

### Failing Tests

#### Test 02-07: INPUT Tests ❌
**Status:** Multiple syntax issues, but also mapEntries failures

#### Test 09: mapEntries (OUTPUT) ❌
```utlx
{
  servers: mapEntries($datacontract.servers, (env, config) => {
    key: if (env == "prod") "production" else env,
    value: config
  })
}
```
**Error:**
```
RuntimeError: Cannot convert FunctionValue to UDM for stdlib function call
```

---

## Why fromEntries Works But mapEntries Doesn't

### fromEntries (✅ Works)
```kotlin
// stdlib/src/main/kotlin/org/apache/utlx/stdlib/objects/ObjectFunctions.kt:302
fun fromEntries(args: List<UDM>): UDM {
    requireArgs(args, 1, "fromEntries")
    val array = args[0].asArray() ?: throw FunctionArgumentException(...)

    val properties = mutableMapOf<String, UDM>()
    for (element in array.elements) {
        val pair = element.asArray() ?: throw ...
        val key = pair.elements[0].asString()
        val value = pair.elements[1]
        properties[key] = value
    }

    return UDM.Object(properties)
}
```

**Why it works:**
1. Takes only UDM arguments (an array)
2. No lambda function needed
3. Simple data transformation
4. Full implementation exists

### mapEntries (❌ Doesn't Work)
```kotlin
// stdlib/src/main/kotlin/org/apache/utlx/stdlib/objects/EnhancedObjectFunctions.kt:251
fun mapEntries(args: List<UDM>): UDM {
    if (args.size < 2) {
        throw IllegalArgumentException("mapEntries() requires 2 arguments: object, mapper")
    }

    val obj = args[0]
    if (obj !is UDM.Object) {
        throw IllegalArgumentException("mapEntries() first argument must be an object")
    }

    val mapperArg = args[1]
    // TODO: Implement function calling mechanism  ← PROBLEM

    val result = mutableMapOf<String, UDM>()
    obj.properties.entries.forEach { (key, value) ->
        // Call mapper with key and value
        // val mapped = mapper(UDM.Scalar(key), value)  ← COMMENTED OUT
        // val newKey = mapped["key"]?.asString() ?: key
        // val newValue = mapped["value"] ?: value
        // result[newKey] = newValue

        // Placeholder: keep original entries  ← DOES NOTHING
        result[key] = value
    }

    return UDM.Object(result)
}
```

**Why it doesn't work:**
1. Requires lambda function as second argument
2. No mechanism to call lambda from Kotlin
3. Interpreter can't convert `FunctionValue` to `UDM`
4. All lambda-calling code commented out
5. Placeholder just returns original object unchanged

---

## Architecture Gap

### Current Flow (Broken)

```
UTL-X Script:
  mapEntries($obj, (k, v) => {...})
       ↓
Parser creates:
  FunctionCall("mapEntries", [obj, Lambda(...)])
       ↓
Interpreter evaluates:
  - obj → UDM.Object ✅
  - Lambda → FunctionValue ✅
       ↓
tryLoadStdlibFunction:
  - Calls runtimeValueToUDM(FunctionValue)
       ↓
❌ ERROR: "Cannot convert FunctionValue to UDM"
```

### What's Missing

**Need:** Mechanism for stdlib functions to receive and call lambdas

**Options:**

1. **Add UDM.Lambda type** - But UDM is supposed to be data-only
2. **Special stdlib calling convention** - Pass lambdas separately from UDM args
3. **Higher-order function support in interpreter** - Call lambda from Kotlin
4. **Different stdlib interface** - Accept `RuntimeValue` instead of `UDM`

---

## Workarounds

### For OUTPUT: Use fromEntries Instead of mapEntries

**Instead of (doesn't work):**
```utlx
{
  servers: mapEntries($input.servers, (env, config) => {
    key: upper(env),
    value: config
  })
}
```

**Use (works):**
```utlx
{
  servers: fromEntries(
    entries($input.servers) |> map(entry => [
      upper(entry[0]),    # Transform key
      entry[1]            # Keep value
    ])
  )
}
```

### For INPUT: Use entries + map + filter

**Instead of (doesn't work):**
```utlx
{
  filtered: filterEntries($input.servers, (k, v) => v.port == 5432)
}
```

**Use (works):**
```utlx
{
  filtered: fromEntries(
    entries($input.servers) |> filter(entry => entry[1].port == 5432)
  )
}
```

---

## Impact on Documentation

### yaml-dynamic-keys-output.md

**Pattern 2 Section (Lines 145-233):**
- ⚠️ **Status:** Documented but NOT WORKING
- **Action:** Add warning that this is not yet implemented
- **Workaround:** Show fromEntries + entries alternative

**Example that needs update (Line 165):**
```utlx
# ❌ DOES NOT WORK (mapEntries not implemented)
servers: mapEntries($input.servers, (env, config) => {
  key: upper(env),
  value: config
})

# ✅ USE THIS INSTEAD
servers: fromEntries(
  entries($input.servers) |> map(entry => [
    upper(entry[0]),
    entry[1]
  ])
)
```

### yaml-dynamic-keys-summary.md

**Quick Reference Section (Lines 52-60):**
- Mark Pattern 2 as "⚠️ Not Yet Implemented"
- Show workaround

**Test Suite Status (Lines 93-110):**
```diff
| 05_servers_transform | entries(), mapEntries() | ⚠️ Syntax fixes needed |
+ ⚠️ **Note:** mapEntries() is not implemented, syntax fixes won't help
| 08_generate_datacontract | **OUTPUT pattern** | ✅ **PASSING** |
| 09_transform_datacontract_keys | **OUTPUT pattern** | ❌ **BLOCKED** (mapEntries not implemented) |
```

---

## Path Forward

### Short-term (User Perspective)

1. **Document the limitation** - Update all docs with warnings
2. **Provide workarounds** - Show fromEntries + entries + map pattern
3. **Update tests** - Mark test 09 as blocked, not failed
4. **Clarify status** - Make it clear this is a known implementation gap

### Medium-term (Implementation)

1. **Investigate interpreter** - Study how `FunctionValue` is handled
2. **Design lambda calling** - How should stdlib call user lambdas?
3. **Implement mechanism** - Add support for lambda arguments
4. **Implement functions** - Complete mapEntries, filterEntries, etc.
5. **Test suite** - Verify all lambda-based functions work

### Long-term (Architecture)

1. **UDM.Lambda type?** - Consider if lambdas should be first-class UDM values
2. **Performance** - Optimize lambda calling for hot paths
3. **Composition** - Support higher-order functions more broadly
4. **Parity** - Achieve full DataWeave-like functional programming support

---

## Revised Documentation Claims

### Before

> "UTL-X provides complete support for YAML dynamic keys through:"
> - ✅ INPUT: keys(), values(), entries(), mapEntries()
> - ✅ OUTPUT: fromEntries(), mapEntries()

### After

> "UTL-X provides support for YAML dynamic keys through:"
> - ✅ INPUT: keys(), values(), entries(), hasKey()
> - ⚠️ INPUT: mapEntries(), filterEntries() (not yet implemented, use entries + map)
> - ✅ OUTPUT: fromEntries() (fully working)
> - ⚠️ OUTPUT: mapEntries() (not yet implemented, use entries + map + fromEntries)

---

## Test Status Update

| Test | Pattern | Current Status | Actual Status |
|------|---------|----------------|---------------|
| 01 | Static | ✅ PASSING | ✅ Working |
| 02 | Wildcard | ⚠️ Syntax issues | ✅ Wildcard works, syntax needs fixing |
| 03 | Bracket | ⚠️ Syntax issues | ❓ Unknown (not tested properly) |
| 04 | Introspection | ⚠️ Syntax issues | ✅ Functions work, syntax needs fixing |
| 05 | mapEntries INPUT | ⚠️ Syntax issues | ❌ **BLOCKED** (mapEntries not implemented) |
| 06 | Nested | ⚠️ Syntax issues | ✅ Nesting works, syntax needs fixing |
| 07 | Full spec | ⚠️ Syntax issues | ❌ **BLOCKED** (uses mapEntries) |
| 08 | fromEntries OUTPUT | ✅ PASSING | ✅ Working perfectly |
| 09 | mapEntries OUTPUT | ❓ Not tested | ❌ **BLOCKED** (mapEntries not implemented) |

**Summary:**
- **Actually working:** 1 test (01 - static keys)
- **Fixable with syntax:** 3 tests (02, 04, 06)
- **Blocked by mapEntries:** 3 tests (05, 07, 09)
- **Unknown:** 1 test (03 - bracket notation)
- **Working (fromEntries):** 1 test (08)

---

## Questions for Project Lead

1. **Priority:** Is implementing lambda support in stdlib a priority?
2. **Timeline:** What's the estimated timeline for this feature?
3. **Workaround acceptance:** Should documentation emphasize fromEntries + entries pattern?
4. **Test suite:** Should we mark tests 05, 07, 09 as "skipped" or "blocked"?
5. **Communication:** How should we communicate this to users evaluating UTL-X vs DataWeave?

---

## Conclusion

**Reality Check:** UTL-X's dynamic key support is **partially implemented**:

✅ **What Works:**
- Reading objects with unknown keys (keys, values, entries, hasKey)
- Generating objects from arrays (fromEntries)
- All non-lambda-based operations

❌ **What Doesn't Work:**
- Transforming objects with lambda functions (mapEntries, filterEntries, reduceEntries)
- Any enhanced object function that takes a lambda

⚠️ **Impact:**
- Pattern 1 (fromEntries) is the ONLY working OUTPUT pattern
- Pattern 2 (mapEntries) requires complex workarounds
- Documentation overstates current capabilities

**Recommendation:** Update all documentation to reflect implementation status and provide clear workarounds using fromEntries + entries + map pattern.

---

**Document Version:** 1.0
**Last Updated:** 2025-10-25
**Status:** Critical Finding
