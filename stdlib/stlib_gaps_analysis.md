# UTL-X Standard Library - Comprehensive Gap Analysis

**Analysis Date:** October 15, 2025  
**Current Functions:** 120+ (after adding unzip family)  
**Method:** Cross-reference with DataWeave, functional programming patterns, and inverse operations

---

## 🎯 Executive Summary

**Found Issues:**
- 🔴 **Critical Missing:** 8 functions (inverse operations, essential utilities)
- 🟡 **Nice-to-Have:** 12 functions (convenience, advanced features)
- 🟢 **Optional:** 6 functions (specialized, domain-specific)

---

## 🔴 CRITICAL Missing Functions (Priority 1)

### 1. String Functions

| Function | Purpose | Why Critical | Inverse Of |
|----------|---------|--------------|------------|
| **`camelize`** | Convert to camelCase | DataWeave has it, common need | `kebabCase` |
| **`uncamelize`** | Convert from camelCase | Complete the case conversion family | `camelize` |
| **`snakeCase`** | Convert to snake_case | Missing from case family | - |
| **`titleCase`** | Title Case Conversion | Common text formatting | - |

**Status:** ❌ Missing from `StringFunctions.kt`

**Why it matters:** String case conversion is a fundamental transformation. Having `kebabCase` but not `camelize` creates an incomplete API.

**Implementation needed in:** `stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/StringFunctions.kt`

---

### 2. Array Functions - Missing Inverse Operations

| Function | Purpose | Why Critical | Inverse Of |
|----------|---------|--------------|------------|
| **`compact`** | Remove null/empty elements | Common array cleaning | - |
| **`findIndex`** | Find index of matching element | `find` returns element, need index too | `find` |
| **`findLastIndex`** | Find last index of match | Complement to `findIndex` | - |

**Status:** Partially missing

**Verification needed:**
- ✅ `find` exists
- ❓ `findIndex` - VERIFY IF EXISTS
- ❌ `findLastIndex` - MISSING
- ❌ `compact` - MISSING

---

### 3. Object Functions - Critical Gaps

| Function | Purpose | Why Critical | Inverse Of |
|----------|---------|--------------|------------|
| **`invert`** | Swap keys/values | Essential for lookups | Self-inverse |
| **`deepMerge`** | Recursive object merge | `merge` only does shallow | `merge` |

**Status:** `merge` exists (shallow), but missing deep version

**Why it matters:**
```javascript
// shallow merge (current)
merge({a: {b: 1}}, {a: {c: 2}})  // {a: {c: 2}} - overwrites

// deepMerge (needed)
deepMerge({a: {b: 1}}, {a: {c: 2}})  // {a: {b: 1, c: 2}} - merges
```

---

## 🟡 NICE-TO-HAVE Missing Functions (Priority 2)

### 4. String Functions - Advanced

| Function | DataWeave | Purpose | Complexity |
|----------|-----------|---------|------------|
| **`pluralize`** | ✅ Has | English pluralization | Medium |
| **`singularize`** | ❌ | English singularization | Medium |
| **`levenshtein`** | ❌ | String distance (fuzzy match) | Low |
| **`truncate`** | ❌ | Truncate with ellipsis | Low |
| **`wrap`** | ❌ | Word wrap at width | Medium |
| **`slugify`** | ❌ | URL-safe slug | Low |

**Recommendation:** Add `truncate` and `slugify` (low complexity, high utility)

---

### 5. Array Functions - Functional Programming Patterns

| Function | Purpose | Pattern | Priority |
|----------|---------|---------|----------|
| **`scan`** | Like reduce but returns intermediate results | Functional | Medium |
| **`windowed`** | Sliding window over array | Functional | Medium |
| **`zipAll`** | Zip with padding for different lengths | Functional | Low |
| **`groupByKey`** | Group and extract single key | Convenience | Low |

**Status:**
- `scan` - MISSING (should add)
- `windowed` - MISSING (should add)
- `zipAll` - MISSING (useful but lower priority)

---

### 6. Math Functions - Statistical

| Function | DataWeave | Purpose | Priority |
|----------|-----------|---------|----------|
| **`median`** | ❌ | Middle value | Medium |
| **`mode`** | ❌ | Most frequent value | Low |
| **`stdDev`** | ❌ | Standard deviation | Medium |
| **`variance`** | ❌ | Statistical variance | Low |

**Current Math:** abs, ceil, floor, round, max, min, sum, avg, pow, sqrt, mod, random (12 functions)

**Recommendation:** Add `median` and `stdDev` for better statistical coverage

---

## 🟢 OPTIONAL Missing Functions (Priority 3)

### 7. Date Functions - Edge Cases

| Function | Purpose | Usefulness |
|----------|---------|------------|
| **`parseRelativeDate`** | "2 days ago", "next week" | Nice but complex |
| **`businessDays`** | Exclude weekends/holidays | Domain-specific |
| **`quarter`** | Get quarter (Q1-Q4) | Business analytics |
| **`isoWeek`** | ISO 8601 week number | Standards compliance |

**Current Date Functions:** 25 functions (comprehensive)

**Recommendation:** Add `quarter` (low complexity), skip others for now

---

### 8. Encoding Functions - Advanced

| Function | Current Status | Purpose |
|----------|----------------|---------|
| **`jwt`** | ❌ | JWT encoding/decoding |
| **`crc32`** | ❌ | CRC32 checksum |
| **`hmac`** | ❌ | HMAC signatures |

**Current Encoding:** base64, URL, MD5, SHA256, etc. (8 functions)

**Recommendation:** Skip for now (security functions need careful implementation)

---

## 📊 Missing Functions Summary Table

| Category | Critical | Nice-to-Have | Optional | Total Gap |
|----------|----------|--------------|----------|-----------|
| String | 4 | 6 | 0 | 10 |
| Array | 3 | 4 | 0 | 7 |
| Object | 2 | 0 | 0 | 2 |
| Math | 0 | 4 | 0 | 4 |
| Date | 0 | 0 | 4 | 4 |
| Encoding | 0 | 0 | 3 | 3 |
| **TOTAL** | **9** | **14** | **7** | **30** |

---

## 🎯 Recommended Implementation Priority

### Phase 1: Critical (Week 1) - 9 functions

**String (4):**
1. `camelize` - Convert to camelCase
2. `snakeCase` - Convert to snake_case  
3. `titleCase` - Title Case Conversion
4. `uncamelize` - Convert from camelCase

**Array (3):**
5. `compact` - Remove nulls/empties
6. `findIndex` - Find index of element
7. `findLastIndex` - Find last index

**Object (2):**
8. `invert` - Swap keys and values
9. `deepMerge` - Recursive merge

### Phase 2: Nice-to-Have (Week 2) - 8 functions

**String (2):**
10. `truncate` - Truncate with ellipsis
11. `slugify` - URL-safe slug

**Array (2):**
12. `scan` - Reduce with intermediate results
13. `windowed` - Sliding window

**Math (2):**
14. `median` - Middle value
15. `stdDev` - Standard deviation

**Date (2):**
16. `quarter` - Get quarter
17. `isoWeek` - ISO week number

---

## 🔍 Verification Checklist

Before implementing, verify these exist:

### String Functions (Check in StringFunctions.kt)
- [ ] `camelize` - VERIFY
- [ ] `snakeCase` - VERIFY
- [ ] `titleCase` - VERIFY
- [ ] `truncate` - VERIFY
- [ ] `slugify` - VERIFY

### Array Functions (Check in ArrayFunctions.kt)
- [ ] `findIndex` - VERIFY (might exist)
- [ ] `findLastIndex` - VERIFY
- [ ] `compact` - VERIFY
- [ ] `scan` - VERIFY
- [ ] `windowed` - VERIFY

### Object Functions (Check in ObjectFunctions.kt)
- [ ] `invert` - VERIFY
- [ ] `deepMerge` - VERIFY (vs shallow `merge`)

### Math Functions (Check in MathFunctions.kt)
- [ ] `median` - VERIFY
- [ ] `stdDev` - VERIFY
- [ ] `variance` - VERIFY

---

## 🚀 Implementation Scripts

### Quick Verification Command

```bash
# Search for function implementations
cd ~/path/to/utl-x/stdlib/src/main/kotlin

# Check String functions
grep -r "fun camelize" org/apache/utlx/stdlib/string/
grep -r "fun snakeCase" org/apache/utlx/stdlib/string/
grep -r "fun titleCase" org/apache/utlx/stdlib/string/

# Check Array functions  
grep -r "fun findIndex" org/apache/utlx/stdlib/array/
grep -r "fun compact" org/apache/utlx/stdlib/array/
grep -r "fun scan" org/apache/utlx/stdlib/array/

# Check Object functions
grep -r "fun invert" org/apache/utlx/stdlib/objects/
grep -r "fun deepMerge" org/apache/utlx/stdlib/objects/

# Check Math functions
grep -r "fun median" org/apache/utlx/stdlib/math/
grep -r "fun stdDev" org/apache/utlx/stdlib/math/
```

### Function Count Verification

```bash
# Count registered functions
grep -r "register(" stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt | wc -l
```

---

## 📋 Detailed Gap Reports

### String Functions - Current vs Missing

**Currently Have:** upper, lower, trim, substring, split, replace, contains, startsWith, endsWith, capitalize, concat, join, length, indexOf, lastIndexOf, kebabCase, camelCase(?), pascalCase, reverse, repeat, padLeft, padRight, etc. (33 functions)

**Missing Critical:**
- `camelize` (if not present)
- `snakeCase`
- `titleCase`
- `uncamelize`

**Missing Nice-to-Have:**
- `pluralize`
- `truncate`
- `slugify`
- `levenshtein`
- `wrap`

---

## 🎨 Example Use Cases for Missing Functions

### 1. camelize / snakeCase
```utlx
// API field conversion
{
  // From: customer_first_name
  firstName: camelize("customer_first_name"),  
  
  // From: firstName  
  dbField: snakeCase("firstName")
}
```

### 2. compact
```utlx
// Clean up array
let data = [1, null, 2, "", 3, undefined, 4]
let clean = compact(data)  // [1, 2, 3, 4]
```

### 3. findIndex
```utlx
let users = [{id: 1, name: "Alice"}, {id: 2, name: "Bob"}]
let index = findIndex(users, u => u.name == "Bob")  // 1
```

### 4. invert
```utlx
let lookup = {US: "United States", UK: "United Kingdom"}
let reverse = invert(lookup)  
// {"United States": "US", "United Kingdom": "UK"}
```

### 5. deepMerge
```utlx
let config = {db: {host: "localhost", port: 5432}}
let override = {db: {password: "secret"}}
let final = deepMerge(config, override)
// {db: {host: "localhost", port: 5432, password: "secret"}}
```

### 6. scan
```utlx
// Running total
let nums = [1, 2, 3, 4, 5]
let runningSum = scan(nums, (acc, n) => acc + n, 0)
// [1, 3, 6, 10, 15]
```

### 7. median
```utlx
let scores = [85, 90, 78, 92, 88]
let middle = median(scores)  // 88
```

---

## ✅ Next Actions

1. **Run verification script** to confirm what exists
2. **Implement Phase 1** (9 critical functions)
3. **Add comprehensive tests** for new functions
4. **Update documentation** with new functions
5. **Register functions** in Functions.kt
6. **Update CHANGELOG.md**

---

**Status:** Ready for implementation review and verification ✨
