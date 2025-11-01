# Validation UX Improvements

**Date**: 2025-11-01
**Status**: Draft
**Context**: Monaco Editor integration and validation error reporting

---

## Issue 1: Header vs Content Error Distinction

### Problem Statement

UTL-X scripts have two distinct sections:
```utlx
%utlx 1.0          ← HEADER (directives)
input json         ← HEADER
output json        ← HEADER
---                ← SEPARATOR
{                  ← CONTENT (transformation logic)
  name: $input.name
}                  ← CONTENT
```

**Current behavior**: All parse errors are reported generically as "Syntax Errors"

**User confusion**:
- Header errors (wrong directive, missing separator) are fundamentally different from content errors
- Monaco editor needs to know the context to provide appropriate help
- Error messages should guide users to the right section

### Current Error Output

```
Syntax Errors:
  ✗ 2:1 - Expected '---' separator after directives
```

**Problem**: User doesn't know if this is a header issue or content issue.

### Proposed Solution

#### Option A: Explicit Section Labels

```
Header Errors:
  ✗ 2:1 - Expected '---' separator after directives
    |
      1 | %utlx 1.0
      2 | input json
          ^
      3 | output json

    💡 The header must end with '---' before the transformation

Content Errors:
  ✗ 7:10 - Undefined variable: totalAmount
    |
      6 |   {
      7 |     total: totalAmount
                    ^^^^^^^^^^^
      8 |   }
```

#### Option B: Error Codes with Context

```
Syntax Errors:
  ✗ HEADER:2:1 - Expected '---' separator after directives
  ✗ CONTENT:7:10 - Undefined variable: totalAmount
```

#### Option C: Separate Error Categories (Recommended)

```
Header Validation:
  ✗ 2:1 - Expected '---' separator after directives
    Section: Header (lines 1-3)
    Fix: Add '---' after line 3

Transformation Validation:
  ⚠ 7:10 - Undefined variable: totalAmount
    Section: Content (lines 4-8)
    Fix: Define 'totalAmount' or use existing variable
```

### Implementation Details

#### Parser Enhancement

**File**: `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`

Add section tracking:
```kotlin
enum class ScriptSection {
    HEADER,      // Before ---
    SEPARATOR,   // The --- line
    CONTENT      // After ---
}

data class ParseError(
    val message: String,
    val location: Location,
    val section: ScriptSection,  // NEW
    val code: String = "PARSE_ERROR"
)
```

During parsing:
```kotlin
var currentSection = ScriptSection.HEADER

fun parseProgram(): ParseResult {
    // Parse header
    currentSection = ScriptSection.HEADER
    parseHeader()

    // Parse separator
    currentSection = ScriptSection.SEPARATOR
    parseSeparator()

    // Parse content
    currentSection = ScriptSection.CONTENT
    parseExpression()
}

fun error(message: String): ParseError {
    return ParseError(
        message = message,
        location = currentLocation(),
        section = currentSection  // Track which section
    )
}
```

#### ValidateCommand Enhancement

**File**: `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/ValidateCommand.kt`

Group errors by section:
```kotlin
val headerErrors = parseResult.errors.filter { it.section == ScriptSection.HEADER }
val contentErrors = parseResult.errors.filter { it.section == ScriptSection.CONTENT }

if (headerErrors.isNotEmpty()) {
    printErrors("Header Errors", headerErrors, options.format, scriptContent)
    println("  💡 Fix header issues first - they prevent content parsing")
}

if (contentErrors.isNotEmpty()) {
    printErrors("Transformation Errors", contentErrors, options.format, scriptContent)
}
```

#### Monaco Integration

**Benefits for Monaco**:
```typescript
// Monaco can show different tooltips based on section
function getErrorTooltip(error: ValidationError): string {
    if (error.section === 'HEADER') {
        return `Header Error: ${error.message}

        The header defines input/output formats and must end with '---'.
        See: https://docs.utlx.org/syntax/header`;
    } else {
        return `Transformation Error: ${error.message}

        This is in your transformation logic.
        See: https://docs.utlx.org/syntax/expressions`;
    }
}
```

### User Benefits

1. **Clearer context**: Immediately know which part of the script is wrong
2. **Better IDE support**: Monaco can provide section-specific help
3. **Faster debugging**: Fix header issues before worrying about content
4. **Learning aid**: Understand the structure of UTL-X scripts

---

## Issue 2: Stop-on-First vs Collect-All Errors

### Problem Statement

**Current behavior**: Parser stops at first error
```
Syntax Errors:
  ✗ 7:10 - Expected '}' after object properties

# Script has 5 more errors, but user doesn't see them!
```

**User pain**:
- Fix one error → run validate again → see next error
- Repeat 10 times for 10 errors
- Very frustrating workflow!

### Analysis: Tradeoffs

#### Stop-on-First Error

**Pros**:
- ✅ Simpler to implement
- ✅ Faster parsing (stops early)
- ✅ Avoids cascading errors (one error causing many)

**Cons**:
- ❌ Requires multiple validate runs
- ❌ Slow feedback loop
- ❌ Poor developer experience
- ❌ Wastes time in edit-validate cycle

#### Collect-All Errors (with recovery)

**Pros**:
- ✅ See all problems at once
- ✅ Fix multiple issues before re-running
- ✅ Better developer experience
- ✅ Industry standard (TypeScript, Rust, etc.)

**Cons**:
- ❌ More complex to implement (error recovery)
- ❌ May show cascading errors (confusing)
- ❌ Slower parsing (continues after errors)

### Recommendation: Collect-All with Smart Limits

**Strategy**: Collect errors but stop at reasonable limits

```kotlin
class ValidationConfig {
    val maxErrors: Int = 100          // Stop after 100 errors
    val maxErrorsPerSection: Int = 50 // Max 50 per section
    val enableRecovery: Boolean = true // Try to recover and continue
}
```

### Implementation Plan

#### Phase 1: Basic Error Collection (Simple)

**Current**:
```kotlin
fun parse(): ParseResult {
    try {
        val program = parseProgram()
        return ParseResult.Success(program)
    } catch (e: ParseException) {
        return ParseResult.Failure(listOf(e.toError()))
    }
}
```

**Enhanced**:
```kotlin
fun parse(): ParseResult {
    val errors = mutableListOf<ParseError>()

    try {
        val program = parseProgram()
        return if (errors.isEmpty()) {
            ParseResult.Success(program)
        } else {
            ParseResult.Failure(errors)
        }
    } catch (e: ParseException) {
        errors.add(e.toError())
        return ParseResult.Failure(errors)
    }
}

// Instead of throwing, collect errors
fun error(message: String) {
    errors.add(ParseError(message, currentLocation(), currentSection))
    if (errors.size >= maxErrors) {
        throw TooManyErrorsException()
    }
    // Don't throw - continue parsing!
}
```

**Effort**: 4 hours
**Risk**: Low

---

#### Phase 2: Error Recovery (Complex)

**Goal**: After encountering an error, try to get back to a valid state

**Strategies**:

##### 1. Synchronization Tokens
Find "safe" tokens to resume parsing:
```kotlin
val SYNC_TOKENS = setOf(
    TokenType.SEPARATOR,     // ---
    TokenType.LET,           // let
    TokenType.RBRACE,        // }
    TokenType.SEMICOLON,     // ; (if we add it)
    TokenType.NEWLINE        // newline
)

fun synchronize() {
    while (!isAtEnd()) {
        if (current.type in SYNC_TOKENS) {
            break
        }
        advance()
    }
}
```

##### 2. Panic Mode Recovery
```kotlin
fun parseObjectLiteral(): Expression {
    consume(LBRACE, "Expected '{'")

    val properties = mutableListOf<Property>()

    while (!check(RBRACE) && !isAtEnd()) {
        try {
            properties.add(parseProperty())

            if (!check(RBRACE)) {
                if (!consume(COMMA, "Expected ',' between properties")) {
                    // ERROR: Missing comma
                    // RECOVERY: Try to continue anyway
                    synchronize()
                }
            }
        } catch (e: ParseException) {
            errors.add(e.toError())
            synchronize() // Skip to next property
        }
    }

    consume(RBRACE, "Expected '}'")
    return ObjectLiteral(properties)
}
```

##### 3. Section-Based Recovery
```kotlin
// If header parsing fails, try to find --- and continue with content
fun parseProgram(): Program {
    val header = try {
        parseHeader()
    } catch (e: ParseException) {
        errors.add(e.toError())
        // RECOVERY: Skip to separator
        skipToSeparator()
        null
    }

    parseSeparator()

    val content = try {
        parseExpression()
    } catch (e: ParseException) {
        errors.add(e.toError())
        null
    }

    return Program(header, content)
}
```

**Effort**: 16-24 hours
**Risk**: Medium-High (can introduce bugs)

---

#### Phase 3: Cascading Error Detection

**Problem**: One error can cause many downstream errors

Example:
```utlx
let x = {
  name: "test"
  email: "test@example.com"  # Missing comma
}

let y = x.name  # This will also fail if x parse failed
```

**Solution**: Track "error tainted" expressions
```kotlin
sealed class Type {
    object Error : Type()  // Represents a failed expression
    data class String : Type()
    // ...
}

// When checking y = x.name:
fun checkMemberAccess(obj: Expression, member: String): Type {
    val objType = check(obj)

    if (objType is Type.Error) {
        // Don't report another error - this is cascading
        return Type.Error
    }

    // ... normal type checking
}
```

**Effort**: 8 hours
**Risk**: Low

---

### User Experience Comparison

#### Current (Stop-on-First)
```
$ utlx validate script.utlx
Syntax Errors:
  ✗ 7:10 - Expected ','

$ # Fix error, run again
$ utlx validate script.utlx
Syntax Errors:
  ✗ 9:15 - Undefined variable: x

$ # Fix error, run again
$ utlx validate script.utlx
Type Warnings:
  ⚠ 12:20 - Type mismatch

$ # Fix warning, run again
$ utlx validate script.utlx
✓ Validation passed
```

**Total cycles**: 4
**Developer time**: ~5-10 minutes

---

#### Proposed (Collect-All)
```
$ utlx validate script.utlx

Header Errors: (0)

Transformation Errors: (3)
  ✗ 7:10 - Expected ',' between object properties
    |
      6 |   name: "test"
      7 |   email: "test@example.com"
              ^

  ⚠ 9:15 - Undefined variable: x
    |
      9 | let y = x.name
                  ^

  ⚠ 12:20 - Type mismatch: expected Number, got String
    |
     12 |   total: "100"
                   ^^^^^

3 error(s) found. Fix them and validate again.

$ # Fix all errors, run once
$ utlx validate script.utlx
✓ Validation passed
```

**Total cycles**: 2
**Developer time**: ~1-2 minutes

---

### Error Limits and User Control

Allow users to configure error reporting:

```bash
# Default: Show up to 100 errors
utlx validate script.utlx

# Show all errors (no limit)
utlx validate script.utlx --max-errors=0

# Show only first 10 errors
utlx validate script.utlx --max-errors=10

# Stop on first error (legacy behavior)
utlx validate script.utlx --fail-fast
```

**Configuration file**: `.utlx/config.toml`
```toml
[validation]
max_errors = 100
fail_fast = false
show_cascading_errors = false
```

---

## Implementation Roadmap

### Week 1: Header/Content Distinction
- [ ] Add `ScriptSection` enum to `ParseError`
- [ ] Track current section in parser
- [ ] Update `ValidateCommand` to group errors by section
- [ ] Add section-specific error messages
- [ ] Test with Monaco integration

**Deliverable**: Errors clearly labeled as Header or Content

---

### Week 2: Basic Error Collection
- [ ] Change parser to collect errors instead of throwing
- [ ] Add error count limits
- [ ] Update all error() calls to not throw
- [ ] Add `--max-errors` flag
- [ ] Test with scripts containing multiple errors

**Deliverable**: Validator shows multiple errors at once

---

### Week 3: Error Recovery (Phase 1)
- [ ] Implement synchronization token logic
- [ ] Add section-based recovery
- [ ] Test error recovery on real scripts
- [ ] Measure false positive rate

**Deliverable**: Parser continues after errors

---

### Week 4: Cascading Error Detection
- [ ] Track error-tainted expressions
- [ ] Suppress cascading errors
- [ ] Add `--show-cascading` flag for debugging
- [ ] Tune heuristics

**Deliverable**: Clean error output without cascading noise

---

## Success Metrics

### Quantitative
- **Error Discovery Rate**: Average errors found per validate run
  - Current: ~1.2 errors/run
  - Target: ~3-5 errors/run

- **Edit-Validate Cycles**: Cycles needed to reach validation success
  - Current: ~5-8 cycles for complex scripts
  - Target: ~2-3 cycles

- **Parse Success Rate**: % of scripts that parse to some AST
  - Current: ~40% (stops on first error)
  - Target: ~85% (with recovery)

### Qualitative
- User feedback: "Validation is helpful" vs "Validation is frustrating"
- IDE integration: Monaco shows rich, context-aware errors
- Error message quality: Users understand what to fix

---

## Monaco Editor Integration

### Error Display Enhancement

```typescript
interface ValidationError {
    line: number;
    column: number;
    message: string;
    section: 'HEADER' | 'CONTENT';
    severity: 'error' | 'warning' | 'info';
    code: string;
    suggestion?: string;
}

// Monaco marker with section context
function createMarker(error: ValidationError): monaco.editor.IMarkerData {
    return {
        startLineNumber: error.line,
        startColumn: error.column,
        endLineNumber: error.line,
        endColumn: error.column + 10,
        message: formatMessage(error),
        severity: getSeverity(error),
        source: error.section === 'HEADER' ? 'utlx-header' : 'utlx-content',
        code: error.code
    };
}
```

### Inline Documentation

```typescript
// Show different help based on section
monaco.languages.registerHoverProvider('utlx', {
    provideHover: (model, position) => {
        const line = position.lineNumber;
        const section = getScriptSection(model, line);

        if (section === 'HEADER') {
            return {
                contents: [
                    { value: '**UTL-X Header**' },
                    { value: 'Define input/output formats here' },
                    { value: '[Learn more](https://docs.utlx.org/header)' }
                ]
            };
        } else {
            return {
                contents: [
                    { value: '**Transformation Logic**' },
                    { value: 'Write your data transformation here' },
                    { value: '[Learn more](https://docs.utlx.org/expressions)' }
                ]
            };
        }
    }
});
```

---

## Open Questions

1. **Error Limit**: What's the right default max_errors? 100? 50? Unlimited?

2. **Cascading Detection**: How aggressive should we be in suppressing cascading errors?

3. **Performance**: Does error recovery significantly slow down parsing?

4. **Monaco Integration**: Should we send section info in JSON format?

5. **Backward Compatibility**: Should `--fail-fast` be opt-in or opt-out?

---

## References

- TypeScript error reporting: Multiple errors, error recovery
- Rust compiler: Excellent error messages with suggestions
- ESLint: Configurable error limits
- VSCode: Rich error display with sections
