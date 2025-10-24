# Placeholder Matching in Conformance Tests

## Overview

The UTL-X conformance test runner supports **placeholder matching** for handling dynamic values in test outputs. This allows tests to verify the format and type of dynamic values (like timestamps, UUIDs, etc.) without requiring exact value matches.

## Why Placeholders?

Some UTL-X functions generate dynamic values that change on every execution:

- `now()` - Current timestamp
- `uuid()` - Random UUID
- `currentDate()` - Current date
- And other functions that depend on runtime state

Without placeholders, tests using these functions would **always fail** because the actual output would never match the expected output.

## Supported Placeholders

### `{{TIMESTAMP}}` or `{{ISO8601}}`

Matches any valid ISO 8601 timestamp string.

**Valid formats:**
- `2024-01-15T10:30:00Z`
- `2024-01-15T10:30:00.123Z`
- `2024-01-15T10:30:00+05:30`
- `2024-01-15T10:30:00.123+05:30`

**Example:**
```yaml
expected:
  format: json
  data: |
    {
      "processed_at": "{{TIMESTAMP}}",
      "user": "Alice"
    }
```

This will match any output with a valid ISO 8601 timestamp in the `processed_at` field.

### `{{UUID}}`

Matches any valid UUID v4 string.

**Valid format:**
- `550e8400-e29b-41d4-a716-446655440000`

**Example:**
```yaml
expected:
  format: json
  data: |
    {
      "requestId": "{{UUID}}",
      "status": "success"
    }
```

### `{{ANY}}`

Matches **any value** of any type (string, number, boolean, null, object, array).

**Example:**
```yaml
expected:
  format: json
  data: |
    {
      "dynamicField": "{{ANY}}",
      "staticField": "known value"
    }
```

### `{{NUMBER}}`

Matches any numeric value (integer or float).

**Example:**
```yaml
expected:
  format: json
  data: |
    {
      "randomValue": "{{NUMBER}}",
      "description": "Random number between 1 and 100"
    }
```

### `{{STRING}}`

Matches any string value.

**Example:**
```yaml
expected:
  format: json
  data: |
    {
      "generatedName": "{{STRING}}",
      "id": 12345
    }
```

### `{{REGEX:pattern}}`

Matches strings against a custom regular expression pattern.

**Example:**
```yaml
expected:
  format: json
  data: |
    {
      "email": "{{REGEX:^[a-z]+@example\\.com$}}",
      "phone": "{{REGEX:\\d{3}-\\d{3}-\\d{4}}}"
    }
```

**Note:** Backslashes in regex patterns must be escaped (e.g., `\\d` instead of `\d`).

## Usage in Tests

### Single Placeholder

```yaml
name: "test_now_function"
transformation: |
  %utlx 1.0
  input json
  output json
  ---
  {
    timestamp: now(),
    message: "Hello"
  }

expected:
  format: json
  data: |
    {
      "timestamp": "{{TIMESTAMP}}",
      "message": "Hello"
    }
```

### Multiple Placeholders

```yaml
name: "test_multiple_dynamic_values"
transformation: |
  %utlx 1.0
  input json
  output json
  ---
  {
    id: uuid(),
    createdAt: now(),
    randomNumber: random(1, 100),
    status: "active"
  }

expected:
  format: json
  data: |
    {
      "id": "{{UUID}}",
      "createdAt": "{{TIMESTAMP}}",
      "randomNumber": "{{NUMBER}}",
      "status": "active"
    }
```

### Nested Placeholders

Placeholders work at any depth in the JSON structure:

```yaml
expected:
  format: json
  data: |
    {
      "order": {
        "id": "ORD-001",
        "metadata": {
          "processedAt": "{{TIMESTAMP}}",
          "requestId": "{{UUID}}"
        }
      }
    }
```

### Array Elements with Placeholders

```yaml
expected:
  format: json
  data: |
    {
      "items": [
        {
          "id": "{{UUID}}",
          "createdAt": "{{TIMESTAMP}}"
        },
        {
          "id": "{{UUID}}",
          "createdAt": "{{TIMESTAMP}}"
        }
      ]
    }
```

## How It Works

1. **Test runner parses expected output** - Loads the expected JSON/XML/YAML structure
2. **Executes transformation** - Runs the UTL-X transformation with the input data
3. **Parses actual output** - Loads the actual JSON/XML/YAML result
4. **Compares recursively** - Walks both structures comparing values
5. **Placeholder matching** - When expected value is a placeholder (e.g., `{{TIMESTAMP}}`):
   - Validates actual value matches the placeholder's format/type
   - If valid, considers it a match (no difference)
   - If invalid, reports a type/format mismatch

## Limitations

### Only for String Placeholders

Placeholders must be **string values** in the expected output. You cannot use placeholders for:

- Object keys (property names)
- Array indices
- Boolean literals
- Null values

**Invalid Examples:**
```yaml
# ❌ INVALID - placeholder as object key
{
  "{{ANY}}": "value"
}

# ❌ INVALID - placeholder as boolean
{
  "active": {{ANY}}
}

# ❌ INVALID - placeholder without quotes
{
  "value": {{NUMBER}}
}
```

**Valid Example:**
```yaml
# ✅ VALID - placeholder as string value
{
  "value": "{{NUMBER}}"
}
```

### Format-Specific

Currently, placeholder matching is **only implemented for JSON output**. For XML, CSV, and YAML outputs, exact string matching is still used.

**Future enhancement:** Extend placeholder matching to XML, CSV, and YAML formats.

## Implementation Details

### Source Code

The placeholder matching implementation is in:
```
conformance-suite/runners/cli-runner/simple-runner.py
```

**Key functions:**
- `is_placeholder_match(expected: str, actual: Any) -> bool` (lines 36-92)
  - Checks if an expected value is a placeholder and validates the actual value
- `find_json_differences(expected_data: Any, actual_data: Any, path: str = '') -> List[Dict]` (lines 94-194)
  - Recursively compares JSON structures, using placeholder matching for string values

### Adding New Placeholders

To add a new placeholder type:

1. **Add detection logic** in `is_placeholder_match()`:
```python
# {{CUSTOM}} - custom validation
if placeholder == 'CUSTOM':
    if not isinstance(actual, str):
        return False
    # Add your validation logic here
    return validate_custom_format(actual)
```

2. **Document the placeholder** in this file

3. **Add test cases** to verify the placeholder works correctly

## Best Practices

### 1. Use Specific Placeholders

Prefer specific placeholders over `{{ANY}}`:

```yaml
# ✅ GOOD - specific placeholder
"timestamp": "{{TIMESTAMP}}"

# ❌ BAD - too permissive
"timestamp": "{{ANY}}"
```

### 2. Mix Static and Dynamic Values

Combine placeholders with exact values to validate structure:

```yaml
# ✅ GOOD - validates both structure and dynamic values
{
  "id": "{{UUID}}",
  "status": "active",
  "createdAt": "{{TIMESTAMP}}",
  "version": 2
}
```

### 3. Use Regex for Custom Formats

For values with specific formats not covered by built-in placeholders:

```yaml
# ✅ GOOD - custom format validation
{
  "orderId": "{{REGEX:^ORD-\\d{6}$}}",
  "phoneNumber": "{{REGEX:\\+1-\\d{3}-\\d{3}-\\d{4}}}"
}
```

### 4. Document Dynamic Fields

Add comments in your test YAML to explain why placeholders are used:

```yaml
name: "sap_integration"
description: "SAP system integration with encoding handling and business logic"
# Note: processed_at uses {{TIMESTAMP}} because now() generates current time
transformation: |
  %utlx 1.0
  input xml
  output json
  ---
  {
    metadata: {
      processed_at: now()  # Dynamic timestamp
    }
  }

expected:
  format: json
  data: |
    {
      "metadata": {
        "processed_at": "{{TIMESTAMP}}"
      }
    }
```

## Examples

### Example 1: Current Timestamp

**Test:**
```yaml
name: "test_now_timestamp"
transformation: |
  %utlx 1.0
  input json
  output json
  ---
  { timestamp: now() }

expected:
  format: json
  data: |
    { "timestamp": "{{TIMESTAMP}}" }
```

**Actual Output:**
```json
{ "timestamp": "2024-10-24T14:23:45Z" }
```

**Result:** ✅ Pass - `2024-10-24T14:23:45Z` matches `{{TIMESTAMP}}` pattern

### Example 2: UUID Generation

**Test:**
```yaml
name: "test_uuid_generation"
transformation: |
  %utlx 1.0
  input json
  output json
  ---
  { requestId: uuid() }

expected:
  format: json
  data: |
    { "requestId": "{{UUID}}" }
```

**Actual Output:**
```json
{ "requestId": "550e8400-e29b-41d4-a716-446655440000" }
```

**Result:** ✅ Pass - Valid UUID format

### Example 3: SAP Integration (Real-World)

**Test:**
```yaml
name: "sap_integration"
transformation: |
  %utlx 1.0
  input xml
  output json
  ---
  {
    metadata: {
      source: "SAP",
      processed_at: now(),
      encoding_detected: detectXMLEncoding($input)
    }
  }

expected:
  format: json
  data: |
    {
      "metadata": {
        "source": "SAP",
        "processed_at": "{{TIMESTAMP}}",
        "encoding_detected": "ISO-8859-1"
      }
    }
```

**Actual Output:**
```json
{
  "metadata": {
    "source": "SAP",
    "processed_at": "2024-10-24T14:30:00.123Z",
    "encoding_detected": "ISO-8859-1"
  }
}
```

**Result:** ✅ Pass - Timestamp validated, other fields match exactly

## Troubleshooting

### Placeholder Not Matching

**Problem:** Test fails even though output looks correct

**Cause:** Placeholder syntax error or format mismatch

**Solution:**
1. Check placeholder syntax (must be `{{PLACEHOLDER}}` with double braces)
2. Ensure expected value is a string (has quotes in JSON)
3. Verify actual value matches the placeholder's expected format

**Example:**
```yaml
# ❌ WRONG - missing quotes
{ "timestamp": {{TIMESTAMP}} }

# ✅ CORRECT - properly quoted
{ "timestamp": "{{TIMESTAMP}}" }
```

### Case Sensitivity

**Problem:** Placeholder not recognized

**Cause:** Placeholder names are case-sensitive

**Solution:** Use exact casing as documented:

```yaml
# ❌ WRONG
{ "id": "{{uuid}}" }
{ "timestamp": "{{Timestamp}}" }

# ✅ CORRECT
{ "id": "{{UUID}}" }
{ "timestamp": "{{TIMESTAMP}}" }
```

### Regex Escaping

**Problem:** Regex placeholder not matching

**Cause:** Backslashes need double-escaping in YAML strings

**Solution:**

```yaml
# ❌ WRONG - single backslash
{ "pattern": "{{REGEX:\d{3}}}" }

# ✅ CORRECT - double backslash
{ "pattern": "{{REGEX:\\d{3}}}" }
```

## Future Enhancements

1. **XML/CSV/YAML Support** - Extend placeholder matching to non-JSON formats
2. **Custom Placeholder Definitions** - Allow tests to define custom placeholders
3. **Placeholder Ranges** - Support numeric ranges: `{{NUMBER:1-100}}`
4. **Date Formats** - More specific date/time placeholders: `{{DATE}}`, `{{TIME}}`
5. **Relative Timestamps** - Match timestamps within a time range: `{{TIMESTAMP:±5s}}`

---

**Last Updated:** 2024-10-24
**Version:** 1.0
**Status:** ✅ Implemented and tested
