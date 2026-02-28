# Bug: JSON Parser Loses Integer Type — All Numbers Parsed as Double

**Status:** Fixed
**Component:** `formats/json` — `json_parser.kt`
**Severity:** Medium
**Affects:** All formats that consume JSON parser output (json, odata, jsch)

## Description

The JSON parser converts all numeric values to `Double`, losing the distinction between integers and floating-point numbers. JSON `1` becomes `UDM.Scalar(1.0)` instead of `UDM.Scalar(1L)`.

## Root Cause

`json_parser.kt:205-208`:

```kotlin
val value = numStr.toDoubleOrNull()
    ?: throw JSONParseException("Invalid number: $numStr", line, column)

return UDM.Scalar.number(value)
```

Every number — integer or decimal — goes through `toDoubleOrNull()`, producing a `Double`.

## Impact

**1. Int64 precision loss**
Double has a 53-bit mantissa. JSON integers larger than 2^53 (9,007,199,254,740,992) lose precision silently:

```
JSON input:   { "id": 9007199254740993 }
Parsed as:    UDM.Scalar(9007199254740992.0)   ← wrong, off by 1
Should be:    UDM.Scalar(9007199254740993L)     ← correct
```

This matters for database IDs, timestamps-as-integers, and OData `Edm.Int64` values.

**2. OData annotation values stored with decimal point**
The OData parser converts annotation values to strings via `.toString()`:

```
JSON input:   { "@odata.count": 42 }
Attribute:    "odata.count" = "42.0"
Should be:    "odata.count" = "42"
```

**3. Test friction**
Tests must use `1.0` instead of `1L` when asserting parsed integer values, which is unintuitive and error-prone.

## Compensating Hack

`json_serializer.kt:73-76` detects "whole number Doubles" and writes them back as integers:

```kotlin
if (value is Double && value % 1.0 == 0.0 && !value.isInfinite() && !value.isNaN()) {
    writer.write(value.toLong().toString())
}
```

This makes the JSON round-trip visually correct (`1` → `1.0` → `"1"`), but the internal UDM representation is still wrong.

## Proposed Fix

In `json_parser.kt`, replace the single `toDoubleOrNull()` with integer-first parsing:

```kotlin
private fun parseNumber(): UDM.Scalar {
    // ... (existing number string collection) ...

    // Try integer first (preserves Int64 precision)
    if (!numStr.contains('.') && !numStr.contains('e') && !numStr.contains('E')) {
        numStr.toLongOrNull()?.let { return UDM.Scalar.number(it) }
    }

    // Fall back to Double for decimals and scientific notation
    val value = numStr.toDoubleOrNull()
        ?: throw JSONParseException("Invalid number: $numStr", line, column)

    return UDM.Scalar.number(value)
}
```

## Test Impact

After the fix:
- `(obj.properties["ID"] as UDM.Scalar).value` returns `1L` instead of `1.0`
- Tests across JSON, OData, and other modules using JSON input will need updating (`1.0` → `1L`)
- The serializer's `% 1.0` hack becomes unnecessary for Long values but can remain for backward compatibility

## Related

- `formats/json/src/main/kotlin/.../json_parser.kt` — parser
- `formats/json/src/main/kotlin/.../json_serializer.kt` — compensating hack
- `formats/odata/src/test/.../ODataJSONParserTest.kt` — tests use `1.0` as workaround
