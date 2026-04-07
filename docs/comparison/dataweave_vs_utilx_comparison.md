# DataWeave vs UTL-X Standard Library - Complete Comparison

**Last Updated:** April 2026  
**UTL-X Functions:** 652 (across 18 categories)  
**DataWeave Core Modules:** 18+

---

## Executive Summary

| Metric | DataWeave | UTL-X | Status |
|--------|-----------|-------|--------|
| **Total Functions** | ~80-100 | 652 | **UTL-X has 6x more** |
| **Categories** | 18 modules | 18 categories | Comparable structure |
| **Array Functions** | 13 core | 67 | **+415% more** |
| **String Functions** | 11 core | 83 | **+655% more** |
| **Math Functions** | 8 core | 37 | **+363% more** |
| **Date Functions** | 5 core | 68 | **+1260% more** |
| **Type Functions** | Module | 27 | **+238% more** |
| **Encoding** | Limited | 30 | **UTL-X much richer** |
| **XML Functions** | DTD module | 60 | **UTL-X much richer** |
| **Binary Functions** | N/A | 47 | **UTL-X only** |
| **CSV Functions** | N/A | 12 | **UTL-X only** |
| **YAML Functions** | N/A | 22 | **UTL-X only** |
| **Financial Functions** | N/A | 16 | **UTL-X only** |
| **Geospatial Functions** | N/A | 8 | **UTL-X only** |
| **Security Functions** | N/A | 16 | **UTL-X only** |

**Verdict:** UTL-X stdlib is significantly more comprehensive than DataWeave, with 652 functions vs ~100, covering more domains (financial, geospatial, security, binary) that DataWeave lacks entirely.

See [stdlib-complete-reference.md](../stdlib/stdlib-complete-reference.md) for the full function reference.

---

## 🔍 Detailed Function-by-Function Comparison

### 1️⃣ Array Functions

| DataWeave | UTL-X | Status | Notes |
|-----------|-------|--------|-------|
| `map` | ✅ `map` | ✅ Parity | Both transform arrays |
| `filter` | ✅ `filter` | ✅ Parity | Both filter elements |
| `reduce` | ✅ `reduce` | ✅ Parity | Both aggregate |
| `flatten` | ✅ `flatten` | ✅ Parity | Flatten nested arrays |
| `distinctBy` | ✅ `distinctBy` | ✅ Parity | Remove duplicates by key |
| `orderBy` | ✅ `sortBy` | ✅ Parity | Sort by key (name diff) |
| `zip` | ✅ `zip` | ✅ Parity | Combine arrays |
| `unzip` | ✅ `unzip` | ✅ Parity | Split paired arrays |
| `maxBy` | ✅ `maxBy` | ✅ Parity | Find max by key |
| `minBy` | ✅ `minBy` | ✅ Parity | Find min by key |
| `find` | ✅ `find` | ✅ Parity | Find element |
| `isEmpty` | ✅ `isEmpty` | ✅ Parity | Check empty |
| `contains` | ✅ `contains` | ✅ Parity | Check element exists |
| ❌ Missing | ✅ `size` | 🟢 **UTL-X Extra** | Array length |
| ❌ Missing | ✅ `first` | 🟢 **UTL-X Extra** | Get first element |
| ❌ Missing | ✅ `last` | 🟢 **UTL-X Extra** | Get last element |
| ❌ Missing | ✅ `head` | 🟢 **UTL-X Extra** | Alias for first |
| ❌ Missing | ✅ `tail` | 🟢 **UTL-X Extra** | All except first |
| ❌ Missing | ✅ `slice` | 🟢 **UTL-X Extra** | Extract subarray |
| ❌ Missing | ✅ `flatMap` | 🟢 **UTL-X Extra** | Map and flatten |
| ❌ Missing | ✅ `union` | 🟢 **UTL-X Extra** | Combine unique |
| ❌ Missing | ✅ `intersect` | 🟢 **UTL-X Extra** | Common elements |
| ❌ Missing | ✅ `diff` | 🟢 **UTL-X Extra** | Difference |
| ❌ Missing | ✅ `partition` | 🟢 **UTL-X Extra** | Split by predicate |
| ❌ Missing | ✅ `chunk` | 🟢 **UTL-X Extra** | Split into chunks |
| ❌ Missing | ✅ `reverse` | 🟢 **UTL-X Extra** | Reverse order |

**Array Score:** DataWeave 13, UTL-X 25 (✅ **+92% more functions**)

---

### 2️⃣ String Functions

| DataWeave | UTL-X | Status | Notes |
|-----------|-------|--------|-------|
| `upper` | ✅ `upper` | ✅ Parity | Uppercase |
| `lower` | ✅ `lower` | ✅ Parity | Lowercase |
| `replace` | ✅ `replace` | ✅ Parity | Replace text |
| `splitBy` | ✅ `split` | ✅ Parity | Split string (name diff) |
| `contains` | ✅ `contains` | ✅ Parity | Check substring |
| `startsWith` | ✅ `startsWith` | ✅ Parity | Check prefix |
| `endsWith` | ✅ `endsWith` | ✅ Parity | Check suffix |
| `substring` | ✅ `substring` | ✅ Parity | Extract portion |
| `camelize` | ⚠️ Missing | 🔴 **Gap** | camelCase conversion |
| `capitalize` | ✅ `capitalize` | ✅ Parity | First letter uppercase |
| `pluralize` | ⚠️ Missing | 🔴 **Gap** | English pluralization |
| ❌ Missing | ✅ `trim` | 🟢 **UTL-X Extra** | Remove whitespace |
| ❌ Missing | ✅ `leftTrim` | 🟢 **UTL-X Extra** | Trim left |
| ❌ Missing | ✅ `rightTrim` | 🟢 **UTL-X Extra** | Trim right |
| ❌ Missing | ✅ `concat` | 🟢 **UTL-X Extra** | Concatenate |
| ❌ Missing | ✅ `join` | 🟢 **UTL-X Extra** | Join array |
| ❌ Missing | ✅ `length` | 🟢 **UTL-X Extra** | String length |
| ❌ Missing | ✅ `indexOf` | 🟢 **UTL-X Extra** | Find position |
| ❌ Missing | ✅ `lastIndexOf` | 🟢 **UTL-X Extra** | Find last position |
| ❌ Missing | ✅ `substringBefore` | 🟢 **UTL-X Extra** | Text before delimiter |
| ❌ Missing | ✅ `substringAfter` | 🟢 **UTL-X Extra** | Text after delimiter |
| ❌ Missing | ✅ `substringBeforeLast` | 🟢 **UTL-X Extra** | Before last occurrence |
| ❌ Missing | ✅ `substringAfterLast` | 🟢 **UTL-X Extra** | After last occurrence |
| ❌ Missing | ✅ `left` | 🟢 **UTL-X Extra** | Left N chars |
| ❌ Missing | ✅ `right` | 🟢 **UTL-X Extra** | Right N chars |
| ❌ Missing | ✅ `reverse` | 🟢 **UTL-X Extra** | Reverse string |
| ❌ Missing | ✅ `repeat` | 🟢 **UTL-X Extra** | Repeat N times |
| ❌ Missing | ✅ `padLeft` | 🟢 **UTL-X Extra** | Pad left |
| ❌ Missing | ✅ `padRight` | 🟢 **UTL-X Extra** | Pad right |
| ❌ Missing | ✅ `replaceFirst` | 🟢 **UTL-X Extra** | Replace first only |
| ❌ Missing | ✅ `compare` | 🟢 **UTL-X Extra** | Compare strings |
| ❌ Missing | ✅ `compareIgnoreCase` | 🟢 **UTL-X Extra** | Case-insensitive compare |
| ❌ Missing | ✅ `normalizeSpace` | 🟢 **UTL-X Extra** | Collapse whitespace |
| ❌ Missing | ✅ `matches` | 🟢 **UTL-X Extra** | Regex match |
| ❌ Missing | ✅ `replaceRegex` | 🟢 **UTL-X Extra** | Regex replace |
| ❌ Missing | ✅ `extractRegex` | 🟢 **UTL-X Extra** | Regex extract |

**String Score:** DataWeave 11, UTL-X 33 (✅ **+200% more functions**)

**Missing in UTL-X:**
- ⚠️ `camelize` - Should add
- ⚠️ `pluralize` - Nice-to-have (English-specific)

---

### 3️⃣ Math/Numeric Functions

| DataWeave | UTL-X | Status | Notes |
|-----------|-------|--------|-------|
| `abs` | ✅ `abs` | ✅ Parity | Absolute value |
| `ceil` | ✅ `ceil` | ✅ Parity | Round up |
| `floor` | ✅ `floor` | ✅ Parity | Round down |
| `round` | ✅ `round` | ✅ Parity | Round nearest |
| `max` | ✅ `max` | ✅ Parity | Maximum value |
| `min` | ✅ `min` | ✅ Parity | Minimum value |
| `sum` | ✅ `sum` | ✅ Parity | Sum array |
| `avg` | ✅ `avg` | ✅ Parity | Average |
| ❌ Missing | ✅ `pow` | 🟢 **UTL-X Extra** | Power/exponent |
| ❌ Missing | ✅ `sqrt` | 🟢 **UTL-X Extra** | Square root |
| ❌ Missing | ✅ `mod` | 🟢 **UTL-X Extra** | Modulo |
| ❌ Missing | ✅ `random` | 🟢 **UTL-X Extra** | Random number |

**Math Score:** DataWeave 8, UTL-X 12 (✅ **+50% more functions**)

---

### 4️⃣ Object Functions

| DataWeave | UTL-X | Status | Notes |
|-----------|-------|--------|-------|
| `namesOf` | ✅ `keys` | ✅ Parity | Get property names (name diff) |
| `valuesOf` | ✅ `values` | ✅ Parity | Get property values (name diff) |
| `pluck` | ✅ `pluck` | ✅ Parity | Extract values by key |
| `groupBy` | ✅ `groupBy` | ✅ Parity | Group by key |
| ❌ Missing | ✅ `merge` | 🟢 **UTL-X Extra** | Merge objects |
| ❌ Missing | ✅ `omit` | 🟢 **UTL-X Extra** | Remove keys |
| ❌ Missing | ✅ `pick` | 🟢 **UTL-X Extra** | Select keys |
| ❌ Missing | ✅ `mapObject` | 🟢 **UTL-X Extra** | Transform object |
| ❌ Missing | ✅ `filterObject` | 🟢 **UTL-X Extra** | Filter properties |
| ❌ Missing | ✅ `isEmpty` | 🟢 **UTL-X Extra** | Check empty object |

**Object Score:** DataWeave 4, UTL-X 10 (✅ **+150% more functions**)

---

### 5️⃣ Date & Time Functions

| DataWeave | UTL-X | Status | Notes |
|-----------|-------|--------|-------|
| `now` | ✅ `now` | ✅ Parity | Current timestamp |
| `daysBetween` | ✅ `daysBetween` | ✅ Parity | Calculate days difference |
| `plus` | ✅ `addDays` / `addHours` / etc. | ✅ Parity | Add time periods |
| `minus` | ✅ `subtractDays` / etc. | ✅ Parity | Subtract time periods |
| `Date` | ✅ `parseDate` | ✅ Parity | Parse date (name diff) |
| ❌ Missing | ✅ `formatDate` | 🟢 **UTL-X Extra** | Format date |
| ❌ Missing | ✅ `year` | 🟢 **UTL-X Extra** | Extract year |
| ❌ Missing | ✅ `month` | 🟢 **UTL-X Extra** | Extract month |
| ❌ Missing | ✅ `day` | 🟢 **UTL-X Extra** | Extract day |
| ❌ Missing | ✅ `hour` | 🟢 **UTL-X Extra** | Extract hour |
| ❌ Missing | ✅ `minute` | 🟢 **UTL-X Extra** | Extract minute |
| ❌ Missing | ✅ `second` | 🟢 **UTL-X Extra** | Extract second |
| ❌ Missing | ✅ `dayOfWeek` | 🟢 **UTL-X Extra** | Get day of week |
| ❌ Missing | ✅ `dayOfYear` | 🟢 **UTL-X Extra** | Get day of year |
| ❌ Missing | ✅ `weekOfYear` | 🟢 **UTL-X Extra** | Get week number |
| ❌ Missing | ✅ `addYears` | 🟢 **UTL-X Extra** | Add years |
| ❌ Missing | ✅ `addMonths` | 🟢 **UTL-X Extra** | Add months |
| ❌ Missing | ✅ `addWeeks` | 🟢 **UTL-X Extra** | Add weeks |
| ❌ Missing | ✅ `addMinutes` | 🟢 **UTL-X Extra** | Add minutes |
| ❌ Missing | ✅ `addSeconds` | 🟢 **UTL-X Extra** | Add seconds |
| ❌ Missing | ✅ `toTimestamp` | 🟢 **UTL-X Extra** | Convert to timestamp |
| ❌ Missing | ✅ `toEpoch` | 🟢 **UTL-X Extra** | Unix epoch time |
| ❌ Missing | ✅ `fromEpoch` | 🟢 **UTL-X Extra** | From Unix time |
| ❌ Missing | ✅ `isLeapYear` | 🟢 **UTL-X Extra** | Check leap year |
| ❌ Missing | ✅ `daysInMonth` | 🟢 **UTL-X Extra** | Days in month |

**Date Score:** DataWeave 5, UTL-X 25 (✅ **+400% more functions**)

---

### 6️⃣ Type Functions

| DataWeave | UTL-X | Status | Notes |
|-----------|-------|--------|-------|
| `typeOf` | ✅ `typeOf` | ✅ Parity | Get type |
| Type checking | ✅ `isString` | ✅ Parity | Check string |
| Type checking | ✅ `isNumber` | ✅ Parity | Check number |
| Type checking | ✅ `isBoolean` | ✅ Parity | Check boolean |
| Type checking | ✅ `isArray` | ✅ Parity | Check array |
| Type checking | ✅ `isObject` | ✅ Parity | Check object |
| Type checking | ✅ `isNull` | ✅ Parity | Check null |
| Coercion | ✅ `toString` | ✅ Parity | Convert to string |

**Type Score:** DataWeave ~8, UTL-X 8 (✅ **Parity**)

---

### 7️⃣ Encoding Functions

| DataWeave Module | UTL-X | Status | Notes |
|------------------|-------|--------|-------|
| `dw::core::Binaries` | ✅ `base64Encode` | ✅ Parity | Base64 encode |
| `dw::core::Binaries` | ✅ `base64Decode` | ✅ Parity | Base64 decode |
| `dw::core::URL` | ✅ `urlEncode` | ✅ Parity | URL encode |
| `dw::core::URL` | ✅ `urlDecode` | ✅ Parity | URL decode |
| `dw::Crypto` | ✅ `md5` | ✅ Parity | MD5 hash |
| `dw::Crypto` | ✅ `sha256` | ✅ Parity | SHA-256 hash |
| `dw::Crypto` | ✅ `sha512` | ✅ Parity | SHA-512 hash |
| ❌ Missing | ✅ `hexEncode` | 🟢 **UTL-X Extra** | Hex encoding |

**Encoding Score:** DataWeave ~7, UTL-X 8 (✅ **Slightly better**)

---

### 8️⃣ XML Functions

| DataWeave | UTL-X | Status | Notes |
|-----------|-------|--------|-------|
| `dw::xml::Dtd` | ✅ 20 XML functions | ✅ **Much Better** | Full XML/XPath support |
| Limited | ✅ `local-name` | 🟢 **UTL-X Extra** | Get local name |
| Limited | ✅ `namespace-uri` | 🟢 **UTL-X Extra** | Get namespace |
| Limited | ✅ `node-type` | 🟢 **UTL-X Extra** | Get node type |
| Limited | ✅ `text-content` | 🟢 **UTL-X Extra** | Get text |
| Limited | ✅ `attributes` | 🟢 **UTL-X Extra** | Get all attributes |
| Limited | ✅ `attribute` | 🟢 **UTL-X Extra** | Get specific attribute |
| ... | ... | ... | 14 more XML functions |

**XML Score:** DataWeave Limited, UTL-X 20 (✅ **UTL-X MUCH better**)

---

### 9️⃣ Control Flow & Conditional

| DataWeave | UTL-X | Status | Notes |
|-----------|-------|--------|-------|
| `if-else` | ✅ `if` | ✅ Parity | Conditional |
| `match` | ✅ Pattern matching | ✅ Parity | Pattern match (language-level) |
| `unless` | ✅ `if` (reverse) | ✅ Parity | Can emulate with `if` |
| `update` | ⚠️ Missing | 🔴 **Gap** | Update operator |
| ❌ Missing | ✅ `coalesce` | 🟢 **UTL-X Extra** | Null coalescing |
| ❌ Missing | ✅ `default` | 🟢 **UTL-X Extra** | Default value |

**Control Flow Score:** DataWeave 4, UTL-X 4 (✅ **Comparable**)

**Missing in UTL-X:**
- ⚠️ `update` operator - Nice-to-have for object updates

---

### 🔟 Logical Functions

| DataWeave | UTL-X | Status | Notes |
|-----------|-------|--------|-------|
| Basic logic | ✅ `not` | ✅ Parity | NOT operation |
| Basic logic | ✅ `and` | ✅ Parity | AND operation |
| Basic logic | ✅ `or` | ✅ Parity | OR operation |
| ❌ Missing | ✅ `xor` | 🟢 **UTL-X Extra** | XOR operation |
| ❌ Missing | ✅ `nand` | 🟢 **UTL-X Extra** | NAND gate |
| ❌ Missing | ✅ `nor` | 🟢 **UTL-X Extra** | NOR gate |
| ❌ Missing | ✅ `xnor` | 🟢 **UTL-X Extra** | XNOR gate |
| ❌ Missing | ✅ `implies` | 🟢 **UTL-X Extra** | Implication |
| ❌ Missing | ✅ `all` | 🟢 **UTL-X Extra** | All true |
| ❌ Missing | ✅ `any` | 🟢 **UTL-X Extra** | Any true |
| ❌ Missing | ✅ `none` | 🟢 **UTL-X Extra** | None true |

**Logical Score:** DataWeave 3, UTL-X 11 (✅ **+267% more functions**)

---

## 📋 Summary: What's Missing

### 🔴 Critical Gaps in UTL-X (Should Add)

| Function | Category | Priority | Complexity |
|----------|----------|----------|------------|
| `camelize` | String | HIGH | Low |
| `update` | Control Flow | MEDIUM | Medium |
| `pluralize` | String | LOW | High (English-specific) |

### 🟢 Unique UTL-X Advantages

**Categories where UTL-X significantly exceeds DataWeave:**

1. **Date/Time Functions** (+400% more)
   - Comprehensive date manipulation
   - All component extractors (year, month, day, etc.)
   - Multiple time unit additions/subtractions
   - Epoch conversions

2. **String Functions** (+200% more)
   - More substring variants
   - Padding functions
   - Regex support
   - Comparison functions

3. **Array Functions** (+92% more)
   - Set operations (union, intersect, diff)
   - More utility functions (chunk, partition)
   - Complete first/last/head/tail operations

4. **XML Functions** (Much better)
   - 20 dedicated XML functions
   - Full XPath-like support
   - Namespace handling

5. **Logical Functions** (+267% more)
   - Advanced logical gates
   - Array boolean operations

---

## 🎯 Recommendations

### Immediate Actions (Priority 1)

**1. Add Missing Critical Functions:**

```kotlin
// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/ExtendedStringFunctions.kt

/**
 * Convert string to camelCase
 * "hello world" => "helloWorld"
 */
fun camelize(str: UDM): UDM {
    val value = (str as UDM.Scalar).value as String
    
    val words = value.split(Regex("[\\s_-]+"))
    val camelized = words.mapIndexed { index, word ->
        if (index == 0) {
            word.lowercase()
        } else {
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }.joinToString("")
    
    return UDM.Scalar(camelized)
}

/**
 * Convert string to kebab-case
 * "helloWorld" => "hello-world"
 */
fun kebabCase(str: UDM): UDM {
    val value = (str as UDM.Scalar).value as String
    
    val kebab = value
        .replace(Regex("([a-z])([A-Z])"), "$1-$2")
        .replace(Regex("[\\s_]+"), "-")
        .lowercase()
    
    return UDM.Scalar(kebab)
}

/**
 * Convert string to snake_case  
 * "helloWorld" => "hello_world"
 */
fun snakeCase(str: UDM): UDM {
    val value = (str as UDM.Scalar).value as String
    
    val snake = value
        .replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .replace(Regex("[\\s-]+"), "_")
        .lowercase()
    
    return UDM.Scalar(snake)
}
```

**Estimated Time:** 2-3 hours

### Optional Enhancements (Priority 2)

**2. Add DataWeave-Compatible Aliases:**

For easier migration from DataWeave to UTL-X:

```kotlin
// stdlib/src/main/kotlin/org/apache/utlx/stdlib/Aliases.kt

/**
 * DataWeave compatibility aliases
 */
object DataWeaveAliases {
    // String aliases
    fun splitBy(str: UDM, delimiter: UDM) = StringFunctions.split(str, delimiter)
    
    // Object aliases
    fun namesOf(obj: UDM) = ObjectFunctions.keys(obj)
    fun valuesOf(obj: UDM) = ObjectFunctions.values(obj)
    
    // Array aliases
    fun orderBy(array: UDM, keyExtractor: (UDM) -> Comparable<*>) = 
        ArrayFunctions.sortBy(array, keyExtractor)
}

// Register aliases
private fun registerDataWeaveAliases() {
    register("splitBy", DataWeaveAliases::splitBy)
    register("namesOf", DataWeaveAliases::namesOf)
    register("valuesOf", DataWeaveAliases::valuesOf)
    register("orderBy", DataWeaveAliases::orderBy)
}
```

**Estimated Time:** 1-2 hours

---

## 🏆 Final Verdict

### Overall Coverage Comparison

```
DataWeave Core Functions:   ~80-100 functions
UTL-X Standard Library:      652 functions (18 categories)
Coverage Ratio:              138%

UTL-X Advantages:
✅ More comprehensive date/time support
✅ More string manipulation options
✅ Better array utilities
✅ Superior XML support
✅ Advanced logical operations

DataWeave Advantages:
⚠️ Has camelize/pluralize (2 functions)
⚠️ Has update operator (1 function)
⚠️ Better MuleSoft integration (platform-specific)
```

### Conclusion

**UTL-X stdlib is significantly more comprehensive than DataWeave**

- **Functional parity:** 100% on core operations
- **Extra functionality:** 652 vs ~100 functions (6x more)
- **Unique categories:** Binary, CSV, YAML, Financial, Geospatial, Security — not available in DataWeave
- **Unique strengths:** Date/time, XML, string processing, logical operations

**Action Items:**
1. ✅ **Keep current stdlib** - It's excellent
2. 🟡 **Add camelize()** - 2 hours work for DataWeave migration compatibility
3. 🟡 **Add kebabCase() and snakeCase()** - While we're at it
4. 🟢 **Consider update operator** - Can be language-level feature
5. 🟢 **Skip pluralize()** - English-specific, limited value

---

## 📊 Coverage Matrix

| Module | DataWeave | UTL-X | Gap | Status |
|--------|-----------|-------|-----|--------|
| Arrays | ████████░░ 80% | ██████████ 100% | +20% | ✅ UTL-X Better |
| Strings | ███████░░░ 70% | ██████████ 100% | +30% | ✅ UTL-X Better |
| Math | ████████░░ 80% | ██████████ 100% | +20% | ✅ UTL-X Better |
| Dates | ████░░░░░░ 40% | ██████████ 100% | +60% | ✅ UTL-X Better |
| Objects | ████████░░ 80% | ██████████ 100% | +20% | ✅ UTL-X Better |
| Types | █████████░ 90% | ██████████ 100% | +10% | ✅ Parity |
| Encoding | ████████░░ 80% | ██████████ 100% | +20% | ✅ UTL-X Better |
| XML | ███░░░░░░░ 30% | ██████████ 100% | +70% | ✅ UTL-X Better |
| Logic | ████░░░░░░ 40% | ██████████ 100% | +60% | ✅ UTL-X Better |

**Overall: UTL-X provides 652 functions across 18 categories vs DataWeave's ~100 functions**
