# Validation Gaps and Limitations

This document tracks known gaps in UTL-X validation that should be addressed in future releases.

## Critical Issues

### 1. Reserved Keyword Misuse Not Detected

**Status**: Open
**Priority**: High
**Discovered**: 2025-11-01

#### Description

The validator does not detect when reserved keywords like `input` and `output` are used incorrectly without the `$` prefix.

#### Current Behavior

```utlx
%utlx 1.0
input json
output json
---
{
  name: input.customerName,    # SHOULD ERROR: missing $ prefix
  total: undefinedVariable + 10
}
```

**Current output**:
```
Type Warnings:
  ⚠ 6:9 - Undefined variable: input
  ⚠ 7:10 - Undefined variable: undefinedVariable
```

#### Expected Behavior

```
Semantic Errors:
  ✗ 6:9 - Reserved keyword 'input' must be accessed as '$input'
    |
      5 | {
      6 |   name: input.customerName,
               ^^^^^
      7 |   total: undefinedVariable + 10

Type Warnings:
  ⚠ 7:10 - Undefined variable: undefinedVariable
    |
      6 |   name: input.customerName,
      7 |   total: undefinedVariable + 10
                  ^^^^^^^^^^^^^^^^
      8 | }
```

#### Root Cause

The type checker treats `input` as a regular undefined variable reference rather than recognizing it as a misused reserved keyword.

**Location**: `modules/core/src/main/kotlin/org/apache/utlx/core/types/type_system.kt`

The type checker needs:
1. A list of reserved keywords (`input`, `output`, and potentially others)
2. Logic to check variable references against reserved keywords
3. Context-aware error messages that suggest the correct usage

#### Impact

- **User Experience**: Confusing error messages that don't explain the actual problem
- **Learning Curve**: New users may not understand why `input` doesn't work
- **Debugging**: Harder to diagnose common mistakes

#### Proposed Solution

Add a **Reserved Keyword Checker** to the type checker that:

1. Maintains a set of reserved identifiers:
   ```kotlin
   val RESERVED_KEYWORDS = setOf("input", "output")
   val SPECIAL_VARIABLES = setOf("$input", "$output")
   ```

2. In variable reference checking, before reporting "undefined variable":
   ```kotlin
   when {
       name in RESERVED_KEYWORDS ->
           error("Reserved keyword '$name' must be accessed as '\$$name'")
       !scope.contains(name) ->
           error("Undefined variable: $name")
   }
   ```

3. Provide helpful suggestions:
   ```
   Did you mean: $input?
   Reserved keywords: input, output (use $ prefix to access)
   ```

#### Related Issues

- Grammar should explicitly define reserved keywords
- LSP hover/completion should understand reserved keywords
- Documentation should list all reserved keywords

#### Workaround

Users must remember to use `$input` and `$output`. No automated detection currently exists.

---

## Medium Priority Issues

### 2. Grammar-Derived Validation Rules Missing

**Status**: Open
**Priority**: Medium
**Discovered**: 2025-11-01

#### Description

Many validation errors should be derivable from the grammar specification, but are not currently checked systematically.

#### Examples

- Operator precedence violations
- Invalid syntax constructs that pass parsing
- Context-sensitive grammar rules not enforced

#### Proposed Solution

Create a validation rule generator that:
1. Analyzes the grammar specification
2. Generates validation checks automatically
3. Ensures grammar and validator stay in sync

---

## Documentation Updates Needed

- [ ] Add "Reserved Keywords" section to language reference
- [ ] Update error catalog with reserved keyword errors
- [ ] Add common mistakes guide for new users
- [ ] Document validation levels and what each checks

---

## Testing Coverage

- [ ] Add validation tests for reserved keyword misuse
- [ ] Add tests for all grammar-derived rules
- [ ] Add regression tests for error message quality
- [ ] Test error recovery and multiple errors

---

## Future Enhancements

### Enhanced Error Messages

- Show "Did you mean?" suggestions
- Provide fix-it hints for common mistakes
- Link to documentation for complex errors

### Static Analysis

- Detect unreachable code
- Warn about unused imports
- Suggest performance improvements

### IDE Integration

- Real-time validation in LSP
- Quick fixes for common errors
- Refactoring support
