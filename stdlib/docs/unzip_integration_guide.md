# Unzip Functions - Integration Guide

## 📋 Overview

Adding the missing `unzip` function and related array transformations to complete the UTL-X stdlib array functionality.

## ✅ What's Being Added

| Function | Purpose | Inverse Of |
|----------|---------|------------|
| `unzip` | Split array of pairs into two arrays | `zip` |
| `unzipN` | Split array of N-tuples into N arrays | `zipWith` |
| `transpose` | Swap rows/columns in 2D array | Self-inverse |
| `zipWith` | Combine N arrays into array of N-tuples | `unzipN` |
| `zipWithIndex` | Pair elements with their indices | - |

## 📁 File Integration

### 1. Add Implementation Code

**Location:** `stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/MoreArrayFunctions.kt`

```kotlin
// At the end of MoreArrayFunctions.kt, add the UnzipFunctions object
// (See artifact "Unzip and Related Array Functions")
```

### 2. Add Tests

**Location:** `stdlib/src/test/kotlin/org/apache/utlx/stdlib/array/UnzipFunctionsTest.kt`

Create new file with comprehensive test suite (See artifact "UnzipFunctionsTest.kt").

### 3. Register Functions

**Location:** `stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt`

In the `registerArrayFunctions()` method, add:

```kotlin
private fun registerArrayFunctions() {
    // ... existing registrations ...
    
    // Zip/Unzip operations (ADD THESE)
    register("unzip", UnzipFunctions::unzip)
    register("unzipN", UnzipFunctions::unzipN)
    register("transpose", UnzipFunctions::transpose)
    register("zipWith", UnzipFunctions::zipWith)
    register("zipWithIndex", UnzipFunctions::zipWithIndex)
}
```

## 🔄 Function Relationships

### Inverse Pairs

```
zip(arr1, arr2) ←→ unzip(pairs)
zipWith(arr1, arr2, arr3) ←→ unzipN(tuples)
transpose(matrix) ←→ transpose(transposed)  # self-inverse
```

### Visual Examples

#### 1. Unzip (Inverse of Zip)
```
zip([1,2,3], ["a","b","c"])  →  [[1,"a"], [2,"b"], [3,"c"]]
                                         ↓ unzip
                                  [[1,2,3], ["a","b","c"]]
```

#### 2. UnzipN (Generalized)
```
zipWith([1,2], ["a","b"], [true,false])  →  [[1,"a",true], [2,"b",false]]
                                                    ↓ unzipN
                                            [[1,2], ["a","b"], [true,false]]
```

#### 3. Transpose
```
[[1,2,3],      transpose      [[1,4],
 [4,5,6]]   ←────────────→     [2,5],
                               [3,6]]
```

## 🎯 Use Cases

### 1. CSV Column Extraction
```utlx
// CSV rows as tuples
let rows = [[1, "Alice", 95], [2, "Bob", 87]]

// Extract columns
let columns = unzipN(rows)
// Result: [[1, 2], ["Alice", "Bob"], [95, 87]]

let ids = columns[0]
let names = columns[1]
let scores = columns[2]
```

### 2. Matrix Transposition
```utlx
let matrix = [
  [1, 2, 3],
  [4, 5, 6]
]

let transposed = transpose(matrix)
// Result: [[1,4], [2,5], [3,6]]
```

### 3. Indexed Processing
```utlx
let items = ["apple", "banana", "cherry"]
let indexed = zipWithIndex(items)
// Result: [[0, "apple"], [1, "banana"], [2, "cherry"]]
```

### 4. Combining Multiple Data Sources
```utlx
let ids = [101, 102, 103]
let names = ["Alice", "Bob", "Charlie"]
let scores = [95, 87, 92]
let active = [true, true, false]

let records = zipWith(ids, names, scores, active)
// Result: [[101, "Alice", 95, true], [102, "Bob", 87, true], [103, "Charlie", 92, false]]
```

## 📊 Comparison with DataWeave

| DataWeave | UTL-X | Status |
|-----------|-------|--------|
| `unzip` ❌ Missing | `unzip` ✅ | **UTL-X Better** |
| No equivalent | `unzipN` ✅ | **UTL-X Better** |
| No equivalent | `transpose` ✅ | **UTL-X Better** |
| `zip` (2 args only) | `zipWith` (N args) ✅ | **UTL-X Better** |
| No equivalent | `zipWithIndex` ✅ | **UTL-X Better** |

**Conclusion:** UTL-X now has MORE comprehensive zip/unzip operations than DataWeave! 🎉

## 🧪 Running Tests

```bash
# From project root
./gradlew :stdlib:test --tests UnzipFunctionsTest

# Run specific test
./gradlew :stdlib:test --tests UnzipFunctionsTest.unzip*

# Run all array function tests
./gradlew :stdlib:test --tests "org.apache.utlx.stdlib.array.*"
```

## ✨ Quick Verification

After integration, create this test file:

**File:** `test-unzip.utlx`
```utlx
%utlx 1.0
input auto
output json
---
{
  // Test basic unzip
  test1: {
    input: [[1, "a"], [2, "b"], [3, "c"]],
    output: unzip([[1, "a"], [2, "b"], [3, "c"]])
  },
  
  // Test unzipN with triples
  test2: {
    input: [[1, "a", true], [2, "b", false]],
    output: unzipN([[1, "a", true], [2, "b", false]])
  },
  
  // Test transpose
  test3: {
    input: [[1,2,3], [4,5,6]],
    output: transpose([[1,2,3], [4,5,6]])
  },
  
  // Test zipWith
  test4: {
    arrays: [[1,2], ["a","b"], [true,false]],
    output: zipWith([1,2], ["a","b"], [true,false])
  },
  
  // Test zipWithIndex
  test5: {
    input: ["apple", "banana", "cherry"],
    output: zipWithIndex(["apple", "banana", "cherry"])
  }
}
```

Then run:
```bash
./utlx transform test-unzip.utlx
```

## 📝 Documentation Updates Needed

After integration, update:

1. **docs/reference/stdlib-reference.md**
   - Add unzip functions section
   - Add examples for each function

2. **docs/language-guide/functions.md**
   - Add zip/unzip patterns
   - Show common use cases

3. **docs/examples/cookbook.md**
   - Add CSV column manipulation example
   - Add matrix transformation example

4. **CHANGELOG.md**
   ```markdown
   ## [Unreleased]
   ### Added
   - `unzip` function - inverse of zip
   - `unzipN` function - generalized unzip for N-tuples
   - `transpose` function - matrix row/column swap
   - `zipWith` function - combine N arrays
   - `zipWithIndex` function - pair elements with indices
   ```

## 🎉 Summary

**What was missing:** The inverse operations for `zip`

**What's being added:** Complete set of zip/unzip/transpose operations

**Why it matters:**
- ✅ Feature parity with functional languages
- ✅ Better than DataWeave (which lacks these)
- ✅ Essential for data transformations
- ✅ Completes the array function suite

**Next steps:**
1. ✅ Copy implementation to `MoreArrayFunctions.kt`
2. ✅ Copy tests to `UnzipFunctionsTest.kt`
3. ✅ Register functions in `Functions.kt`
4. ✅ Run tests
5. ✅ Update documentation
6. ✅ Add to CHANGELOG.md

---

**Status:** Ready for integration into github.com/grauwen/utl-x ✨
