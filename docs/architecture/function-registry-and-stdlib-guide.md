# Function Registry and Stdlib Development Guide

**How UTL-X stdlib functions are defined, registered, annotated, indexed, and consumed by the IDE.**

---

## Architecture Overview

```
Developer writes function
        ↓
1. Implement in Kotlin         (stdlib/src/main/kotlin/.../MyFunctions.kt)
2. Annotate with @UTLXFunction (category, description, parameters, example)
3. Register in Functions.kt    (register("functionName", MyFunctions::functionName))
4. Add class to scanner list   (MyFunctions::class in exportRegistry())
5. Run registry generator      (./gradlew :stdlib:generateFunctionRegistry)
        ↓
Generated files:
  stdlib/build/generated/function-registry/utlx-functions.json   ← IDE, MCP server
  stdlib/build/generated/function-registry/utlx-functions.yaml   ← human-readable
  stdlib/build/generated/function-registry/utlx-functions.txt    ← CLI help
```

## Step-by-Step: Adding a New Function

### Step 1: Choose the Right Package

| Package | Category | When to use |
|---------|----------|-------------|
| `stdlib/array/` | Array | Functions that operate on arrays (map, filter, sort, etc.) |
| `stdlib/restructuring/` | Data Restructuring | Functions that change data shape (groupBy, lookupBy, nestBy) |
| `stdlib/objects/` | Object | Functions that operate on objects (keys, values, entries, merge) |
| `stdlib/string/` | String | String manipulation (concat, replace, trim, etc.) |
| `stdlib/math/` | Math | Mathematical operations (abs, round, sqrt, etc.) |
| `stdlib/date/` | Date | Date/time operations (parseDate, formatDate, addDays, etc.) |
| `stdlib/type/` | Type | Type checking and conversion (isArray, toNumber, etc.) |
| `stdlib/encoding/` | Encoding / Security | Encoding, hashing, encryption (base64, sha256, etc.) |
| `stdlib/xml/` | XML | XML-specific (c14n, QName, encoding detection) |
| `stdlib/json/` | JSON | JSON-specific (canonicalize, jsonEquals) |
| `stdlib/csv/` | CSV | CSV-specific (csvFilter, csvSort, etc.) |
| `stdlib/yaml/` | YAML | YAML-specific (yamlPath, yamlSet, etc.) |
| `stdlib/geo/` | Geospatial | Geographic calculations (distance, bearing, etc.) |
| `stdlib/finance/` | Financial | Financial calculations (interest, currency, etc.) |
| `stdlib/core/` | Core | Core utility functions |

### Step 2: Implement the Function

Create or add to a Kotlin `object` in the appropriate package:

```kotlin
// stdlib/src/main/kotlin/org/apache/utlx/stdlib/restructuring/DataRestructuringFunctions.kt
package org.apache.utlx.stdlib.restructuring

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.annotations.UTLXFunction

object DataRestructuringFunctions {

    @UTLXFunction(
        description = "Short description of what the function does.",
        minArgs = 2,
        maxArgs = 3,
        category = "Data Restructuring",
        parameters = [
            "param1: Description of first parameter",
            "param2: Description of second parameter",
            "param3: Optional third parameter (default: ...)"
        ],
        returns = "What the function returns",
        example = "functionName(arg1, arg2) => result",
        notes = "Additional details, edge cases, related functions.",
        tags = ["restructuring", "grouping"],
        since = "1.0"
    )
    fun myFunction(args: List<UDM>): UDM {
        // Validate arguments
        if (args.size < 2) {
            throw IllegalArgumentException(
                "myFunction() requires 2 arguments: param1, param2. " +
                "Example: myFunction(\$input.items, (i) -> i.key)"
            )
        }
        
        // Implementation
        val array = args[0] as? UDM.Array
            ?: throw IllegalArgumentException("myFunction() first argument must be an array")
        
        // ... logic ...
        
        return UDM.Scalar("result")
    }
}
```

### Step 3: Register in Functions.kt

In `stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt`:

```kotlin
// In the appropriate register method (or create a new one):
private fun registerDataRestructuringFunctions() {
    register("myFunction", DataRestructuringFunctions::myFunction)
}

// Call it from registerAllFunctions():
registerDataRestructuringFunctions()
```

### Step 4: Add Class to Scanner List

In `Functions.kt`, find the `exportRegistry()` method's class list and add your class:

```kotlin
// Around line 1300+ in Functions.kt:
val annotatedClasses = listOf(
    // ... existing classes ...
    
    // Data restructuring functions
    org.apache.utlx.stdlib.restructuring.DataRestructuringFunctions::class,
)
```

This tells the `FunctionRegistryGenerator` to scan your class for `@UTLXFunction` annotations and include them in the generated index files.

### Step 5: Write Tests

Create a test file in the matching test package:

```kotlin
// stdlib/src/test/kotlin/org/apache/utlx/stdlib/restructuring/DataRestructuringFunctionsTest.kt
package org.apache.utlx.stdlib.restructuring

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataRestructuringFunctionsTest {
    @Test
    fun testMyFunction() {
        val result = DataRestructuringFunctions.myFunction(listOf(
            UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
            UDM.Lambda { args -> args[0] }
        ))
        assertTrue(result is UDM.Array)
    }
}
```

### Step 6: Add Conformance Tests

Create YAML test files in `conformance-suite/utlx/tests/stdlib/<category>/`:

```yaml
name: "myFunction_basic"
category: "stdlib/restructuring"
description: "myFunction does X with Y"
tags: ["myFunction", "restructuring"]

input:
  format: json
  data: '{"items": [1, 2, 3]}'

transformation: |
  %utlx 1.0
  input json
  output json
  ---
  myFunction($input.items, (x) -> x * 2)

expected:
  format: json
  data: '[2, 4, 6]'
```

### Step 7: Regenerate the Function Registry

```bash
# Delete old generated files and regenerate:
rm -rf stdlib/build/generated/function-registry/
./gradlew :stdlib:generateFunctionRegistry

# Verify your function appears:
grep "myFunction" stdlib/build/generated/function-registry/utlx-functions.json
```

### Step 8: Build and Test Everything

```bash
# Unit tests
./gradlew :stdlib:test

# Full build (CLI + daemon + engine)
./gradlew :modules:cli:assemble :modules:daemon:assemble :modules:engine:assemble

# Conformance suite
python3 conformance-suite/utlx/runners/cli-runner/simple-runner.py
```

## The @UTLXFunction Annotation

Every public stdlib function MUST have this annotation. It's used by:
- The **function registry generator** — to produce index files
- The **IDE function builder** — to show categories, descriptions, parameter hints
- The **MCP server** — to provide function info to LLMs
- The **CLI help** — to show function documentation

### Annotation Fields

| Field | Required | Purpose |
|-------|----------|---------|
| `description` | Yes | One-line description shown in IDE autocomplete |
| `minArgs` | Yes | Minimum number of arguments |
| `maxArgs` | Yes | Maximum number of arguments |
| `category` | Yes | Category for grouping in IDE function builder |
| `parameters` | Yes | Array of `"name: description"` strings |
| `returns` | Yes | Description of return value |
| `example` | Yes | One-line example: `functionName(args) => result` |
| `notes` | No | Multi-line additional documentation |
| `tags` | No | Array of searchable tags |
| `since` | No | Version when function was added |

### Category Values

Use one of the existing categories to maintain consistency in the IDE:

`"Array"`, `"Data Restructuring"`, `"Object"`, `"String"`, `"Math"`, `"Date"`, `"Type"`, `"Core"`, `"Encoding"`, `"Security"`, `"XML"`, `"JSON"`, `"CSV"`, `"YAML"`, `"Binary"`, `"Geospatial"`, `"Financial"`, `"Utility"`, `"Other"`

## Generated Files

The `generateFunctionRegistry` task produces three files:

| File | Format | Consumed by |
|------|--------|-------------|
| `utlx-functions.json` | JSON | IDE extension, MCP server, function builder |
| `utlx-functions.yaml` | YAML | Human inspection, documentation |
| `utlx-functions.txt` | Plain text | CLI `utlx functions` command |

### JSON Format

```json
[
  {
    "name": "groupBy",
    "description": "Group array elements by key...",
    "category": "Data Restructuring",
    "minArgs": 2,
    "maxArgs": 2,
    "parameters": ["array: Input array to group", "keyFunction: Lambda or string"],
    "returns": "Object keyed by group name",
    "example": "groupBy(items, (i) -> i.dept) => {Eng: [...], Sales: [...]}",
    "notes": "...",
    "tags": ["restructuring", "grouping", "lookup"],
    "since": "1.0"
  }
]
```

## How the IDE Consumes the Registry

1. The IDE extension (VS Code / Theia) reads `utlx-functions.json` at startup
2. Functions are grouped by `category` in the function builder sidebar
3. When the user types a function name, autocomplete shows `description` and `parameters`
4. The function builder inserts `name(` and shows parameter hints
5. Hover documentation shows `description`, `parameters`, `returns`, `example`, and `notes`

## Naming Conventions

- **Function names:** camelCase (`groupBy`, `lookupBy`, `parseDate`)
- **User-defined functions:** PascalCase enforced by parser (`CalculateTax`, `FormatPhone`)
- **"By" suffix:** function takes a lambda for key extraction (`groupBy`, `sortBy`, `lookupBy`)
- **No "By" suffix:** function takes a plain value (`unnest`, `flatten`, `trim`)

---

*Last updated: May 2026. See also: B15 (groupBy fix), F04 (lookupBy implementation).*
