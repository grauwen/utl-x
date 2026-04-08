# B11: OData Serializer Wraps Nested Arrays in {"value": [...]}

**Status:** Fixed
**Severity:** Medium
**Component:** formats/odata (ODataJSONSerializer)
**Found:** 2026-04-08
**Fixed:** 2026-04-08

## Description

When outputting OData JSON format, the OData serializer incorrectly wraps **all** arrays in `{"value": [...]}` objects — including nested arrays within properties. Per the OData specification, only **root-level collection responses** should be wrapped in `{"value": [...]}`.

## Symptoms

A JSON-to-OData transformation produces incorrect output for any property that contains an array:

**Input:**
```json
{
  "allergens": ["Fish", "Dairy", "Gluten"],
  "items": [
    {"name": "Salmon", "price": 12.95},
    {"name": "Steak", "price": 32.50}
  ]
}
```

**Expected OData output (nested arrays are plain):**
```json
{
  "allergens": ["Fish", "Dairy", "Gluten"],
  "items": [
    {"name": "Salmon", "price": 12.95},
    {"name": "Steak", "price": 32.50}
  ]
}
```

**Actual output (every array wrapped):**
```json
{
  "allergens": {
    "value": ["Fish", "Dairy", "Gluten"]
  },
  "items": {
    "value": [
      {"name": "Salmon", "price": 12.95},
      {"name": "Steak", "price": 32.50}
    ]
  }
}
```

## Root Cause

In `formats/odata/src/main/kotlin/org/apache/utlx/formats/odata/ODataJSONSerializer.kt`, the `annotateArray()` method checked `wrapCollection` without checking `isRoot`:

```kotlin
// BEFORE (broken)
if (wrapCollection) {
    // Wraps ALL arrays, including nested ones
}
```

The `wrapCollection` option (default `true`) was applied to every array at every nesting level.

## Fix

Added `isRoot` check so only root-level arrays get the OData collection wrapper:

```kotlin
// AFTER (fixed)
if (wrapCollection && isRoot) {
    // Only wraps root-level collection responses
}
```

**File:** `formats/odata/src/main/kotlin/org/apache/utlx/formats/odata/ODataJSONSerializer.kt`, line 110

## Testing

Test OData output:
```bash
echo '{"items": [1, 2, 3], "nested": {"arr": ["a", "b"]}}' | utlx --to odata
```

Nested arrays should be plain arrays, not wrapped in `{"value": [...]}`.
