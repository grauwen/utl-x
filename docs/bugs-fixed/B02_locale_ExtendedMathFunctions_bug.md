# Bug Report B02: Locale-Dependent Number Formatting in ExtendedMathFunctions

## Metadata

- **Bug ID**: B02
- **Severity**: High
- **Status**: Fixed
- **Date Reported**: 2025-11-03
- **Date Fixed**: 2025-11-03
- **Affected Component**: stdlib/math/ExtendedMathFunctions
- **Fixed In**: main branch (commit pending)

## Summary

The `format-number()` function produces locale-dependent output, causing test failures and inconsistent behavior across different operating systems and locale settings. Tests pass on US locale systems but fail on German, French, Swiss, and other non-US locale systems.

## Description

The `formatNumber()` function in `ExtendedMathFunctions.kt` was using Java's `DecimalFormat` without explicitly specifying a locale, causing it to use the system's default locale. This resulted in different number formatting depending on the user's locale settings:

- **US Locale**: `1,234.56` (comma thousands separator, period decimal point)
- **German Locale**: `1.234,56` (period thousands separator, comma decimal point)
- **French Locale**: `1 234,56` (space thousands separator, comma decimal point)
- **Swiss Locale**: `1'234.56` (apostrophe thousands separator, period decimal point)

## Affected Tests

The following tests in `ExtendedMathFunctionsTest.kt` fail on non-US locale systems:

1. `testFormatNumber()`
2. `testFormatNumberEdgeCases()`
3. `testRoundTripOperations()`
4. `testSpecialFormattingPatterns()`

All tests hardcode US format expectations (e.g., `assertEquals("1,234.56", result)`).

## Root Cause

**File**: `stdlib/src/main/kotlin/org/apache/utlx/stdlib/math/ExtendedMathFunctions.kt`

**Line**: 40 (before fix)

**Problematic Code**:
```kotlin
fun formatNumber(args: List<UDM>): UDM {
    requireArgs(args, 2, "format-number")
    val number = args[0].asNumber()
    val pattern = args[1].asString()

    return try {
        val formatter = DecimalFormat(pattern)  // BUG: Uses system default locale
        UDM.Scalar(formatter.format(number))
    } catch (e: Exception) {
        throw FunctionArgumentException(
            "Invalid number format pattern: $pattern. " +
            "Hint: Use patterns like '#,##0.00' for decimal formatting or '#,##0' for integers."
        )
    }
}
```

The `DecimalFormat(pattern)` constructor uses the system's default locale to determine:
- Thousands separator character
- Decimal point character
- Grouping rules

## Steps to Reproduce

### On a German Locale System

1. Set system locale to German (`de_DE.UTF-8`)
2. Run the test:
   ```bash
   ./gradlew :stdlib:test --tests "ExtendedMathFunctionsTest.testFormatNumber"
   ```
3. Observe test failure:
   - **Expected**: `"1,234.56"`
   - **Actual**: `"1.234,56"`

### Alternative Reproduction (Any System)

Create a test file with explicit locale setting:
```kotlin
@Test
fun testLocaleIssue() {
    val defaultLocale = Locale.getDefault()
    try {
        Locale.setDefault(Locale.GERMANY)
        val result = ExtendedMathFunctions.formatNumber(
            listOf(UDM.Scalar(1234.56), UDM.Scalar("#,##0.00"))
        )
        // This will produce "1.234,56" instead of "1,234.56"
        assertEquals("1,234.56", (result as UDM.Scalar).value) // FAILS
    } finally {
        Locale.setDefault(defaultLocale)
    }
}
```

## Expected vs Actual Behavior

### Expected Behavior
The `format-number()` function should produce consistent US-formatted output regardless of the system locale:
```
format-number(1234.56, "#,##0.00") => "1,234.56"
```

### Actual Behavior (Before Fix)
Output varies by system locale:
- US system: `"1,234.56"` ✓
- German system: `"1.234,56"` ✗
- French system: `"1 234,56"` ✗
- Swiss system: `"1'234.56"` ✗

## Environment Details

### Environments Where Tests Pass
- macOS 15.6 (US locale)
- Java 17.0.16
- Gradle 8.5
- `LC_NUMERIC="C"` or similar US locale settings

### Environments Where Tests Fail
- Any system with non-US locale settings
- German locale (`de_DE`, `de_CH`, etc.)
- French locale (`fr_FR`, `fr_CH`, etc.)
- Italian locale (`it_IT`, `it_CH`, etc.)
- And many others

## The Fix

### Code Changes

**File**: `stdlib/src/main/kotlin/org/apache/utlx/stdlib/math/ExtendedMathFunctions.kt`

**Added Imports** (lines 7-8):
```kotlin
import java.text.DecimalFormatSymbols
import java.util.Locale
```

**Updated formatNumber Function** (line 40):
```kotlin
// Before:
val formatter = DecimalFormat(pattern)

// After:
val formatter = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
```

### Complete Fixed Function
```kotlin
fun formatNumber(args: List<UDM>): UDM {
    requireArgs(args, 2, "format-number")
    val number = args[0].asNumber()
    val pattern = args[1].asString()

    return try {
        // Force US locale for consistent worldwide behavior
        val formatter = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
        UDM.Scalar(formatter.format(number))
    } catch (e: Exception) {
        throw FunctionArgumentException(
            "Invalid number format pattern: $pattern. " +
            "Hint: Use patterns like '#,##0.00' for decimal formatting or '#,##0' for integers."
        )
    }
}
```

## Verification

### Unit Tests
All ExtendedMathFunctionsTest tests now pass:
```bash
$ ./gradlew :stdlib:test --tests "*ExtendedMathFunctionsTest*"

ExtendedMathFunctionsTest > testParseFloat() PASSED
ExtendedMathFunctionsTest > testFormatNumber() PASSED ✓
ExtendedMathFunctionsTest > testFormatNumberEdgeCases() PASSED ✓
ExtendedMathFunctionsTest > testSpecialFormattingPatterns() PASSED ✓
ExtendedMathFunctionsTest > testParseIntEdgeCases() PASSED
ExtendedMathFunctionsTest > testArgumentValidation() PASSED
ExtendedMathFunctionsTest > testRoundTripOperations() PASSED ✓
ExtendedMathFunctionsTest > testParseInt() PASSED

BUILD SUCCESSFUL
```

### Conformance Suite
Full conformance suite passes with 100% success rate:
```
Results: 456/456 tests passed
Success rate: 100.0%
✓ All tests passed!
```

### Regression Testing
No regressions introduced:
- All CSV regional formatting tests pass
- All number parsing/rendering tests pass
- All existing stdlib tests pass

## Impact Analysis

### Breaking Changes
**None**. This is a bug fix that makes behavior consistent across all locales.

### Behavioral Changes
- **Before**: Locale-dependent output (inconsistent)
- **After**: Always US-formatted output (consistent worldwide)

### Affected Users
- **Positive Impact**: Developers on non-US locale systems will now have passing tests
- **No Impact**: Developers on US locale systems (behavior unchanged)
- **Compatibility**: No API changes, full backward compatibility for US locale users

## Design Decision: Why US Locale?

The decision to force US locale (`Locale.US`) for number formatting is based on:

1. **Test Expectations**: All existing tests expect US format
2. **Industry Standard**: Most data interchange formats use period as decimal separator
3. **JSON Compatibility**: JSON standard uses US number format
4. **Consistency**: Ensures `format-number()` output is predictable worldwide
5. **Programming Convention**: Most programming languages use US format by default

For locale-specific formatting, users should use the regional formatting functions in the stdlib (e.g., `parse-eu-number`, `render-french-number`, etc.).

## Future Considerations

### Potential Enhancements

1. **Optional Locale Parameter**: Consider adding an optional locale parameter:
   ```kotlin
   format-number(1234.56, "#,##0.00", "de_DE") => "1.234,56"
   ```

2. **Locale-Aware Variants**: Create locale-specific formatting functions:
   ```kotlin
   format-number-eu(1234.56, "#,##0.00") => "1.234,56"
   format-number-us(1234.56, "#,##0.00") => "1,234.56"
   ```

3. **Configuration**: Allow global locale configuration in transform header:
   ```yaml
   locale: de_DE
   ```

### Documentation Updates Needed

- Update `format-number()` function documentation to clarify US locale behavior
- Add examples showing difference from regional formatting functions
- Document best practices for international number formatting

## Lessons Learned

1. **Internationalization Testing**: Need to test on multiple locales, not just developer's default
2. **Explicit Locale Specification**: Always specify locale explicitly in formatting operations
3. **CI/CD Enhancement**: Consider adding CI tests with different locale settings
4. **Documentation**: Document locale-dependent behavior clearly

## References

- **Java Documentation**: [DecimalFormat](https://docs.oracle.com/javase/8/docs/api/java/text/DecimalFormat.html)
- **Java Documentation**: [DecimalFormatSymbols](https://docs.oracle.com/javase/8/docs/api/java/text/DecimalFormatSymbols.html)
- **Related Tests**: `stdlib/src/test/kotlin/org/apache/utlx/stdlib/math/ExtendedMathFunctionsTest.kt`
- **Regional Formatting**: See `stdlib/regional` functions for locale-specific formatting

## Reporter Credits

Special thanks to the sharp-eyed developer who reported this environment-specific issue, demonstrating the importance of testing across different system configurations.

---

**Last Updated**: 2025-11-03
**Author**: UTL-X Development Team
