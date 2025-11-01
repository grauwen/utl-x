# Header/Content Separation: Detailed Architectural Plan

**Date**: 2025-11-01
**Question**: How deep should we go with header/content distinction? Separate parsers?
**Answer**: **Single parser with section tracking** (simplest, most maintainable)

---

## Executive Summary

**Recommendation**: **Option 2 - Single Parser with Section Context** (see below)

- ✅ Minimal code changes
- ✅ No duplication
- ✅ Easy to maintain
- ✅ Achieves all UX goals
- ❌ No separation of concerns at parser level

**Complexity**: Low
**Risk**: Low
**Effort**: 1-2 days

---

## Current Architecture

### Parser Structure
```
Parser (single class)
├── parseProgram()
│   ├── parseHeader()          ← HEADER section
│   │   ├── parseDirective()
│   │   ├── parseInputs()
│   │   └── parseOutputs()
│   ├── match(TRIPLE_DASH)     ← SEPARATOR
│   └── parseExpression()      ← CONTENT section
│       ├── parseAssignment()
│       ├── parsePipe()
│       └── parseConditional()
```

**Key observation**: Already naturally separated in code structure!
- Lines 94-116: `parseHeader()` - handles header
- Lines 58-92: `parseProgram()` - orchestrates both sections
- Lines 71-89: Content parsing

### Error Tracking
```kotlin
// Current (line 1212)
data class ParseError(
    val message: String,
    val location: Location
)
```

**Missing**: Section context (which part of script failed)

---

## Three Architectural Options

### Option 1: Separate Parsers (Maximum Separation)

#### Architecture
```
                  ┌─────────────┐
                  │   Lexer     │
                  └──────┬──────┘
                         │
                         ├─► tokens
                         │
                  ┌──────▼──────┐
                  │  Coordinator│
                  └──────┬──────┘
                         │
          ┌──────────────┼──────────────┐
          │                             │
   ┌──────▼──────┐              ┌──────▼──────┐
   │HeaderParser │              │ContentParser│
   └──────┬──────┘              └──────┬──────┘
          │                             │
   ┌──────▼──────┐              ┌──────▼──────┐
   │   Header    │              │ Expression  │
   └─────────────┘              └─────────────┘
```

#### Implementation
```kotlin
// New file: HeaderParser.kt
class HeaderParser(private val tokens: List<Token>) {
    fun parse(): HeaderParseResult {
        val header = parseHeader()
        val separatorIndex = findTripleDash()
        return HeaderParseResult(header, separatorIndex, errors)
    }

    private fun parseHeader(): Header { /* ... */ }
}

// New file: ContentParser.kt
class ContentParser(private val tokens: List<Token>) {
    fun parse(startIndex: Int): ContentParseResult {
        current = startIndex
        val expression = parseExpression()
        return ContentParseResult(expression, errors)
    }

    private fun parseExpression(): Expression { /* ... */ }
}

// Modified: Parser.kt (becomes coordinator)
class Parser(private val tokens: List<Token>) {
    fun parse(): ParseResult {
        val headerParser = HeaderParser(tokens)
        val headerResult = headerParser.parse()

        val contentParser = ContentParser(tokens)
        val contentResult = contentParser.parse(headerResult.separatorIndex + 1)

        val allErrors = headerResult.errors + contentResult.errors

        return if (allErrors.isEmpty()) {
            ParseResult.Success(
                Program(headerResult.header, contentResult.expression)
            )
        } else {
            ParseResult.Failure(allErrors)
        }
    }
}
```

#### Pros
- ✅ Clean separation of concerns
- ✅ Each parser is simpler individually
- ✅ Can test header/content parsing independently
- ✅ Could reuse ContentParser for REPL mode (no header)

#### Cons
- ❌ Code duplication (both parsers need helper methods)
- ❌ More files to maintain (3 instead of 1)
- ❌ Coordination complexity (sharing token state)
- ❌ Harder to recover from header errors (different parser instances)
- ❌ OVERKILL for the problem we're solving

**Complexity**: High
**Effort**: 3-4 days
**Verdict**: ❌ **Not recommended** - too much complexity for minimal gain

---

### Option 2: Single Parser with Section Context (RECOMMENDED)

#### Architecture
```
┌─────────────────────────────────────┐
│           Parser                    │
│                                     │
│  currentSection: ScriptSection     │ ← NEW: Track where we are
│                                     │
│  parseProgram()                    │
│  ├─ section = HEADER               │
│  ├─ parseHeader()                  │
│  ├─ section = SEPARATOR            │
│  ├─ parseSeparator()               │
│  ├─ section = CONTENT              │
│  └─ parseExpression()              │
│                                     │
│  error(msg) → creates ParseError   │
│                with currentSection  │
└─────────────────────────────────────┘
```

#### Implementation

**Step 1**: Add section tracking to ParseError

```kotlin
// File: modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt
// Line: ~1210

// Add before ParseError
enum class ScriptSection {
    HEADER,      // Lines before ---
    SEPARATOR,   // The --- line itself
    CONTENT;     // Lines after ---

    fun displayName(): String = when (this) {
        HEADER -> "Header"
        SEPARATOR -> "Separator"
        CONTENT -> "Transformation"
    }
}

// Modify existing ParseError (line 1212)
data class ParseError(
    val message: String,
    val location: Location,
    val section: ScriptSection = ScriptSection.CONTENT  // Default for backward compat
)
```

**Step 2**: Track current section in Parser

```kotlin
// File: modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt
// Line: ~33 (in Parser class)

class Parser(private val tokens: List<Token>) {
    private var current = 0
    private val errors = mutableListOf<ParseError>()

    // NEW: Track which section we're parsing
    private var currentSection = ScriptSection.HEADER

    // ... rest of class
}
```

**Step 3**: Update section as we parse

```kotlin
// File: modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt
// Modify parseProgram() (line 58)

private fun parseProgram(): Program {
    val startToken = peek()

    // Parsing header
    currentSection = ScriptSection.HEADER
    val header = parseHeader()

    // Parsing separator
    currentSection = ScriptSection.SEPARATOR
    if (!match(TokenType.TRIPLE_DASH)) {
        error("Expected '---' separator after header")
    }

    // Parsing content
    currentSection = ScriptSection.CONTENT
    val allExpressions = mutableListOf<Expression>()

    while (!isAtEnd()) {
        val expr = parseExpression()
        allExpressions.add(expr)
    }

    // ... rest of method
}
```

**Step 4**: Update error() method to include section

```kotlin
// File: modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt
// Modify error() method (around line 1197)

private fun error(message: String): Nothing {
    val error = ParseError(
        message = message,
        location = peek().location,
        section = currentSection  // Include current section
    )
    errors.add(error)
    throw ParseException(message, peek().location)
}
```

**Step 5**: Update ValidateCommand to group by section

```kotlin
// File: modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/ValidateCommand.kt
// Modify around line 89-100

is ParseResult.Failure -> {
    hasErrors = true

    // Group errors by section
    val headerErrors = parseResult.errors.filter {
        it.section == ScriptSection.HEADER || it.section == ScriptSection.SEPARATOR
    }
    val contentErrors = parseResult.errors.filter {
        it.section == ScriptSection.CONTENT
    }

    // Print header errors first (if any)
    if (headerErrors.isNotEmpty()) {
        printErrors("Header Errors", headerErrors.map { error ->
            ValidationError(
                level = ErrorLevel.ERROR,
                message = error.message,
                location = error.location,
                code = "HEADER_ERROR"
            )
        }, options.format, scriptContent)

        if (options.verbose) {
            println("  💡 Fix header issues first - they may prevent content parsing")
        }
    }

    // Print content errors (if any)
    if (contentErrors.isNotEmpty()) {
        printErrors("Transformation Errors", contentErrors.map { error ->
            ValidationError(
                level = ErrorLevel.ERROR,
                message = error.message,
                location = error.location,
                code = "PARSE_ERROR"
            )
        }, options.format, scriptContent)
    }

    exitProcess(1)
}
```

#### File Changes Summary

| File | Lines Changed | Type | Complexity |
|------|---------------|------|------------|
| `parser_impl.kt` | +15, ~5 | Add enum, modify error | Low |
| `ValidateCommand.kt` | ~30 | Group by section | Low |
| **Total** | **~50 lines** | **2 files** | **Low** |

#### Pros
- ✅ Minimal code changes (~50 lines)
- ✅ No duplication
- ✅ Easy to understand
- ✅ Backward compatible (default section)
- ✅ Achieves all UX goals
- ✅ Low risk

#### Cons
- ❌ Not "pure" separation (same parser for both)
- ❌ Section tracking is manual (developer must remember to update)

**Complexity**: Low
**Effort**: 1-2 days (including tests)
**Verdict**: ✅ **RECOMMENDED**

---

### Option 3: Hybrid (Separate Header Logic, Shared Infrastructure)

#### Architecture
```
┌──────────────────────────────────┐
│      ParserBase                  │
│  ├─ Token management             │
│  ├─ Error handling               │
│  └─ Helper methods               │
└────────────┬─────────────────────┘
             │ extends
   ┌─────────┴─────────┐
   │                   │
┌──▼──────────┐  ┌─────▼────────┐
│HeaderParser │  │ContentParser │
└─────────────┘  └──────────────┘
```

#### Implementation
```kotlin
// Base class with shared logic
abstract class ParserBase(protected val tokens: List<Token>) {
    protected var current = 0
    protected val errors = mutableListOf<ParseError>()

    protected fun peek(): Token = /* ... */
    protected fun advance(): Token = /* ... */
    protected fun match(vararg types: TokenType): Boolean = /* ... */
    protected fun error(msg: String, section: ScriptSection): Nothing = /* ... */
}

// Specialized parsers
class HeaderParser(tokens: List<Token>) : ParserBase(tokens) {
    fun parseHeader(): Header { /* ... */ }
}

class ContentParser(tokens: List<Token>) : ParserBase(tokens) {
    fun parseExpression(): Expression { /* ... */ }
}
```

#### Pros
- ✅ Better separation than Option 2
- ✅ Shared infrastructure (no duplication)
- ✅ Can test independently

#### Cons
- ❌ More complex than Option 2
- ❌ Still need coordination layer
- ❌ Inheritance can be tricky

**Complexity**: Medium
**Effort**: 2-3 days
**Verdict**: ⚠️ **Possible alternative** if we want more separation

---

## Detailed Comparison Matrix

| Aspect | Option 1 (Separate) | Option 2 (Single + Section) | Option 3 (Hybrid) |
|--------|--------------------|-----------------------------|-------------------|
| **Code Changes** | 300+ lines | 50 lines | 150 lines |
| **New Files** | +2 files | 0 files | +1 file |
| **Complexity** | High | Low | Medium |
| **Maintainability** | Medium | High | Medium |
| **Testability** | High | Medium | High |
| **Risk** | Medium | Low | Low-Medium |
| **Effort** | 3-4 days | 1-2 days | 2-3 days |
| **UX Benefit** | Same | Same | Same |
| **Future Flexibility** | High | Low | Medium |

---

## Recommendation: Option 2 (Single Parser with Section Context)

### Why Option 2?

1. **Principle of Least Change**: Achieves all UX goals with minimal code changes
2. **Low Risk**: Small, localized changes to proven parser
3. **Maintainability**: Less code = fewer bugs
4. **Sufficient**: Section tracking is all we need for error categorization
5. **Pragmatic**: Don't over-engineer for theoretical future needs

### When to Reconsider?

Consider Option 1 or 3 if:
- We add a **REPL mode** that only parses expressions (no header)
- We need **different error recovery strategies** for header vs content
- We want to **parse headers in parallel** with content (performance)
- We support **header extensions/plugins** that need isolation

**Current status**: None of these apply → Option 2 is best

---

## Implementation Plan (Option 2)

### Phase 1: Core Changes (4 hours)

#### Task 1.1: Add ScriptSection enum
**File**: `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`
**Location**: Before line 1212
**Code**: (as shown above in Option 2, Step 1)
**Test**: Compile successfully

---

#### Task 1.2: Modify ParseError
**File**: `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`
**Location**: Line 1212
**Code**: (as shown above in Option 2, Step 1)
**Test**: All existing tests still pass (default section)

---

#### Task 1.3: Add currentSection field
**File**: `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`
**Location**: Line 35 (in Parser class)
**Code**: (as shown above in Option 2, Step 2)
**Test**: Compile successfully

---

#### Task 1.4: Update parseProgram()
**File**: `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`
**Location**: Lines 58-92
**Code**: (as shown above in Option 2, Step 3)
**Test**: Parser still works, section is tracked

---

#### Task 1.5: Update error() method
**File**: `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`
**Location**: Around line 1197
**Code**: (as shown above in Option 2, Step 4)
**Test**: Errors include correct section

---

### Phase 2: ValidateCommand Integration (3 hours)

#### Task 2.1: Group errors by section
**File**: `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/ValidateCommand.kt`
**Location**: Lines 89-100
**Code**: (as shown above in Option 2, Step 5)
**Test**: Errors grouped correctly

---

#### Task 2.2: Update error display
**Enhancement**: Show section name in error messages
```kotlin
printErrors("Header Errors", headerErrors, ...)
printErrors("Transformation Errors", contentErrors, ...)
```
**Test**: Manual testing with header/content errors

---

### Phase 3: Testing (3 hours)

#### Task 3.1: Unit tests for ScriptSection
**File**: `modules/core/src/test/kotlin/org/apache/utlx/core/parser/ScriptSectionTest.kt` (new)
```kotlin
class ScriptSectionTest {
    @Test
    fun `header error has HEADER section`() {
        val parser = Parser(tokenize("%utlx 1.0\ninput json"))
        val result = parser.parse()

        assertIs<ParseResult.Failure>(result)
        assertEquals(ScriptSection.HEADER, result.errors[0].section)
    }

    @Test
    fun `content error has CONTENT section`() {
        val parser = Parser(tokenize("%utlx 1.0\ninput json\noutput json\n---\n{"))
        val result = parser.parse()

        assertIs<ParseResult.Failure>(result)
        assertEquals(ScriptSection.CONTENT, result.errors[0].section)
    }
}
```

---

#### Task 3.2: Integration tests for ValidateCommand
**File**: `modules/cli/src/test/kotlin/org/apache/utlx/cli/commands/ValidateCommandSectionTest.kt` (new)
```kotlin
class ValidateCommandSectionTest {
    @Test
    fun `header errors displayed separately`() {
        val output = captureOutput {
            ValidateCommand().execute(arrayOf("test_header_error.utlx"))
        }

        assertTrue(output.contains("Header Errors:"))
        assertFalse(output.contains("Transformation Errors:"))
    }

    @Test
    fun `content errors displayed separately`() {
        val output = captureOutput {
            ValidateCommand().execute(arrayOf("test_content_error.utlx"))
        }

        assertFalse(output.contains("Header Errors:"))
        assertTrue(output.contains("Transformation Errors:"))
    }
}
```

---

#### Task 3.3: Conformance tests
**Files** (add to validation suite):
- `validation-tests/level1-syntax/header_missing_directive.yaml`
- `validation-tests/level1-syntax/header_missing_separator.yaml`
- `validation-tests/level1-syntax/content_missing_brace.yaml`

**Example**:
```yaml
name: "header_missing_separator"
category: "level1-syntax"
description: "Missing --- separator should be header error"

script: |
  %utlx 1.0
  input json
  output json
  {
    name: $input.name
  }

validation_expected:
  should_pass: false
  exit_code: 1

  errors:
    - level: "syntax"
      type: "header_error"
      section: "header"
      message_pattern: ".*separator.*"
```

---

### Phase 4: Documentation (2 hours)

#### Task 4.1: Update error documentation
**File**: `docs/reference/error-codes.md` (create or update)
```markdown
## Error Sections

UTL-X scripts have two sections:
- **Header**: Directives (`%utlx`, `input`, `output`, `---`)
- **Transformation**: Your data transformation logic

Errors are categorized by section to help you fix them faster.

### Header Errors
Fix these first! Header errors often prevent the transformation from being parsed.

Examples:
- Missing `%utlx 1.0` directive
- Missing `---` separator
- Invalid format specification

### Transformation Errors
Errors in your transformation logic.

Examples:
- Syntax errors in expressions
- Undefined variables
- Type mismatches
```

---

#### Task 4.2: Update Monaco integration guide
**File**: `docs/editors/monaco-integration.md`
```markdown
## Error Sections

Validation errors include a `section` field:
```typescript
interface ValidationError {
    section: 'HEADER' | 'SEPARATOR' | 'CONTENT';
    // ... other fields
}
```

Use this to provide context-specific help:
```typescript
if (error.section === 'HEADER') {
    // Show header documentation
} else {
    // Show transformation documentation
}
```
```

---

## Timeline

| Phase | Duration | Dependencies | Deliverable |
|-------|----------|--------------|-------------|
| Phase 1 | 4 hours | None | Section tracking working |
| Phase 2 | 3 hours | Phase 1 | Grouped error display |
| Phase 3 | 3 hours | Phase 2 | All tests passing |
| Phase 4 | 2 hours | Phase 3 | Documentation complete |
| **Total** | **12 hours** | | **1.5 days** |

---

## Risk Assessment

### Technical Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Forgot to update section | Medium | Low | Code review checklist |
| Backward compatibility break | Low | Medium | Default section value |
| Performance impact | Very Low | Low | Section is just an enum |
| Test coverage gaps | Medium | Medium | Comprehensive test plan |

### Process Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Scope creep | Medium | High | Stick to Option 2, reject additions |
| Over-engineering | Low | Medium | Simple implementation, no abstractions |
| Delayed by other work | Medium | Low | Small, can pause/resume |

---

## Success Criteria

### Must Have
- [ ] Errors tagged with correct section (HEADER/CONTENT)
- [ ] ValidateCommand groups errors by section
- [ ] Error messages show section name
- [ ] All existing tests still pass
- [ ] No performance regression

### Should Have
- [ ] Unit tests for section tracking
- [ ] Integration tests for grouped display
- [ ] Conformance tests for both sections
- [ ] Documentation updated

### Nice to Have
- [ ] Monaco integration example
- [ ] Better error messages with section-specific help
- [ ] Visual separator between error groups

---

## Alternative Rejected: Metadata Approach

**Idea**: Don't change parser, just infer section from line numbers

```kotlin
fun inferSection(line: Int, scriptLines: List<String>): ScriptSection {
    val separatorLine = scriptLines.indexOfFirst { it.trim() == "---" }
    return when {
        line <= separatorLine -> ScriptSection.HEADER
        else -> ScriptSection.CONTENT
    }
}
```

**Rejected because**:
- ❌ Fragile (depends on line counting)
- ❌ Separator might be missing (how to infer?)
- ❌ Less accurate than parser tracking
- ❌ Duplication of logic (parser knows section, why infer?)

---

## Questions & Answers

### Q: Should we have separate parsers for header and content?
**A**: No. Single parser with section tracking is simpler and achieves the same UX goals.

### Q: What if we add more sections later (imports, functions)?
**A**: Easy to extend ScriptSection enum. Current design doesn't preclude future sections.

### Q: How does this help Monaco?
**A**: Monaco receives `section` field in error JSON, can show section-specific tooltips and documentation.

### Q: Performance impact?
**A**: Negligible. Adding one enum field to errors is ~0% overhead.

### Q: Can we still do error recovery?
**A**: Yes! Section tracking is orthogonal to error recovery (both can coexist).

---

## Next Steps

1. ✅ Review and approve this plan
2. Create GitHub issue for implementation
3. Implement Phase 1 (Core Changes)
4. Review PR for Phase 1
5. Implement Phase 2-4
6. Update validation conformance suite
7. Document in release notes

---

## Appendix: Code Diff Preview

### parser_impl.kt
```diff
+ enum class ScriptSection {
+     HEADER, SEPARATOR, CONTENT;
+     fun displayName() = when(this) {
+         HEADER -> "Header"
+         SEPARATOR -> "Separator"
+         CONTENT -> "Transformation"
+     }
+ }

  data class ParseError(
      val message: String,
-     val location: Location
+     val location: Location,
+     val section: ScriptSection = ScriptSection.CONTENT
  )

  class Parser(private val tokens: List<Token>) {
      private var current = 0
+     private var currentSection = ScriptSection.HEADER

      private fun parseProgram(): Program {
+         currentSection = ScriptSection.HEADER
          val header = parseHeader()

+         currentSection = ScriptSection.SEPARATOR
          if (!match(TokenType.TRIPLE_DASH)) {
              error("Expected '---' separator")
          }

+         currentSection = ScriptSection.CONTENT
          val body = parseExpression()
          return Program(header, body)
      }

      private fun error(msg: String): Nothing {
-         val err = ParseError(msg, peek().location)
+         val err = ParseError(msg, peek().location, currentSection)
          errors.add(err)
          throw ParseException(msg, peek().location)
      }
  }
```

**Total changes**: ~15 lines added, ~3 lines modified

---

## Conclusion

**Go with Option 2**: Single parser with section tracking.

It's the simplest solution that meets all requirements without over-engineering.
