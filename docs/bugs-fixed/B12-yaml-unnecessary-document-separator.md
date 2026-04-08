# B12: YAML Serializer Outputs Unnecessary `---` Document Separator

**Status:** Fixed
**Severity:** Low
**Component:** formats/yaml (YAMLSerializer)
**Found:** 2026-04-08
**Fixed:** 2026-04-08

## Description

When serializing to YAML, the output always started with `---` (YAML document separator). While technically valid YAML, the separator is unnecessary for single-document output and unexpected by most users.

## Symptoms

```bash
echo '{"name":"Alice","city":"Edinburgh"}' | utlx --to yaml
```

**Before (with `---`):**
```yaml
---
name: Alice
city: Edinburgh
```

**After (clean):**
```yaml
name: Alice
city: Edinburgh
```

## Root Cause

In `formats/yaml/src/main/kotlin/org/apache/utlx/formats/yaml/YAMLSerializer.kt`, the `SerializeOptions` data class defaulted `explicitStart` to `true`:

```kotlin
// BEFORE
val explicitStart: Boolean = true,
```

## Fix

Changed the default to `false`:

```kotlin
// AFTER
val explicitStart: Boolean = false,
```

**File:** `formats/yaml/src/main/kotlin/org/apache/utlx/formats/yaml/YAMLSerializer.kt`, line 35

The `---` separator can still be enabled via header options if needed for multi-document YAML.

## Testing

```bash
echo '{"name":"Alice"}' | utlx --to yaml
```

Output should start directly with `name: Alice` without a leading `---`.
