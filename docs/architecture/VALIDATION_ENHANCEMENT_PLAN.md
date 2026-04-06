# Validation Enhancement Plan

**Date**: 2025-11-01
**Status**: Draft
**Goal**: Improve UTL-X validation to catch common errors and provide helpful error messages

---

## Executive Summary

This plan addresses critical gaps in UTL-X validation discovered during conformance test development. The primary focus is implementing **context-aware semantic validation** that can detect misuse of reserved keywords and provide grammar-derived validation rules.

---

## Phase 1: Reserved Keyword Validation (Priority: Critical)

### Objective
Detect when reserved keywords (`input`, `output`) are used without the `$` prefix.

### Tasks

#### 1.1 Define Reserved Keywords
**File**: `modules/core/src/main/kotlin/org/apache/utlx/core/types/ReservedKeywords.kt` (new)

```kotlin
package org.apache.utlx.core.types

/**
 * Reserved keywords in UTL-X that have special meaning
 */
object ReservedKeywords {
    /**
     * Keywords that must be accessed with $ prefix
     */
    val SPECIAL_VARIABLES = setOf("input", "output")

    /**
     * Keywords reserved for future use
     */
    val RESERVED_FUTURE = setOf(
        "import", "export", "class", "interface",
        "enum", "type", "namespace"
    )

    /**
     * All reserved keywords
     */
    val ALL_RESERVED = SPECIAL_VARIABLES + RESERVED_FUTURE

    /**
     * Check if an identifier is a reserved keyword
     */
    fun isReserved(name: String): Boolean = name in ALL_RESERVED

    /**
     * Check if an identifier is a special variable
     */
    fun isSpecialVariable(name: String): Boolean = name in SPECIAL_VARIABLES

    /**
     * Get suggestion for reserved keyword
     */
    fun getSuggestion(name: String): String? {
        return when {
            name in SPECIAL_VARIABLES -> "\$$name"
            name in RESERVED_FUTURE -> "Reserved for future use"
            else -> null
        }
    }
}
```

**Effort**: 2 hours
**Dependencies**: None

---

#### 1.2 Enhance Type Checker
**File**: `modules/core/src/main/kotlin/org/apache/utlx/core/types/type_system.kt` (modify)

**Current code** (approximate location based on typical type checker):
```kotlin
// When checking variable reference
fun checkVariableReference(name: String, location: Location): Type {
    val binding = scope.lookup(name)
    if (binding == null) {
        errors.add(TypeError("Undefined variable: $name", location))
        return Type.Error
    }
    return binding.type
}
```

**Enhanced code**:
```kotlin
fun checkVariableReference(name: String, location: Location): Type {
    // Check for reserved keyword misuse FIRST
    if (ReservedKeywords.isSpecialVariable(name)) {
        val suggestion = ReservedKeywords.getSuggestion(name)
        errors.add(TypeError(
            message = "Reserved keyword '$name' cannot be used as a variable. Did you mean '$suggestion'?",
            location = location,
            severity = ErrorSeverity.ERROR,
            code = "RESERVED_KEYWORD_MISUSE",
            suggestion = "Use $suggestion to access the $name data"
        ))
        return Type.Error
    }

    // Check for future reserved keywords
    if (ReservedKeywords.isReserved(name)) {
        errors.add(TypeError(
            message = "Identifier '$name' is reserved for future use",
            location = location,
            severity = ErrorSeverity.ERROR,
            code = "RESERVED_IDENTIFIER"
        ))
        return Type.Error
    }

    // Normal variable lookup
    val binding = scope.lookup(name)
    if (binding == null) {
        errors.add(TypeError(
            message = "Undefined variable: $name",
            location = location,
            severity = ErrorSeverity.WARNING,
            code = "UNDEFINED_VARIABLE"
        ))
        return Type.Error
    }

    return binding.type
}
```

**Effort**: 4 hours
**Dependencies**: 1.1 (ReservedKeywords.kt)

---

#### 1.3 Add Error Severity Levels
**File**: `modules/core/src/main/kotlin/org/apache/utlx/core/types/type_system.kt` (modify)

Add severity to TypeError:
```kotlin
enum class ErrorSeverity {
    ERROR,    // Fails compilation
    WARNING,  // Continues with warning
    INFO      // Informational only
}

data class TypeError(
    val message: String,
    val location: Location,
    val severity: ErrorSeverity = ErrorSeverity.ERROR,
    val code: String = "TYPE_ERROR",
    val suggestion: String? = null
)
```

**Effort**: 2 hours
**Dependencies**: None

---

#### 1.4 Update ValidateCommand Error Handling
**File**: `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/ValidateCommand.kt` (modify)

Current code treats all type errors as warnings. Need to separate:
- `ErrorSeverity.ERROR` → Exit code 1
- `ErrorSeverity.WARNING` → Exit code 0 (unless --strict)

**Effort**: 3 hours
**Dependencies**: 1.3

---

#### 1.5 Add Validation Tests
**Files**:
- `conformance-suite/utlx/validation-tests/level2-semantic/reserved_keyword_input.yaml`
- `conformance-suite/utlx/validation-tests/level2-semantic/reserved_keyword_output.yaml`
- `conformance-suite/utlx/validation-tests/level2-semantic/reserved_keyword_future.yaml`

**Example test**:
```yaml
name: "reserved_keyword_input_without_dollar"
category: "level2-semantic"
description: "Detect use of 'input' without $ prefix"
tags: ["semantic", "reserved-keyword", "error"]

script: |
  %utlx 1.0
  input json
  output json
  ---
  {
    name: input.customerName,
    email: input.email
  }

validation_expected:
  should_pass: false
  exit_code: 1

  errors:
    - level: "semantic"
      type: "reserved_keyword_misuse"
      message_pattern: ".*[Rr]eserved.*keyword.*'input'.*\\$input.*"
      severity: "error"
      line: 6
      column: 9

  warnings: []
```

**Effort**: 3 hours
**Dependencies**: 1.1, 1.2, 1.4

---

### Phase 1 Summary
- **Total Effort**: ~14 hours (2 days)
- **Files Modified**: 2
- **Files Created**: 4
- **Tests Added**: 3+

---

## Phase 2: Grammar-Derived Validation Rules (Priority: High)

### Objective
Automatically generate validation rules from the UTL-X grammar specification.

### Analysis Required

Before implementation, we need to:

1. **Catalog the Grammar**
   - Where is the grammar formally defined?
   - Is it EBNF, ANTLR, hand-written parser?
   - What format can we parse programmatically?

2. **Identify Derivable Rules**
   - Which syntax errors should be caught by parser?
   - Which require semantic analysis?
   - Which are context-dependent?

3. **Define Rule Categories**
   ```
   - Lexical rules (token formation)
   - Syntactic rules (parse tree structure)
   - Contextual rules (scope, types)
   - Semantic rules (meaning, correctness)
   ```

### Proposed Architecture

```
Grammar Specification (EBNF/BNF)
        ↓
Grammar Parser (reads grammar file)
        ↓
Rule Generator (generates validation code)
        ↓
Validation Rules (Kotlin code)
        ↓
Type Checker / Validator (uses rules)
```

### Tasks (Detailed planning needed)

#### 2.1 Grammar Analysis
- [ ] Document current grammar location
- [ ] Extract all production rules
- [ ] Categorize rules by validation type
- [ ] Identify context-sensitive rules

**Effort**: 8 hours

---

#### 2.2 Rule Generator Design
- [ ] Design rule generator architecture
- [ ] Create rule template system
- [ ] Implement basic rule generation
- [ ] Generate validation code

**Effort**: 16 hours

---

#### 2.3 Integration
- [ ] Integrate generated rules with type checker
- [ ] Add tests for generated rules
- [ ] Document rule generation process

**Effort**: 12 hours

---

### Phase 2 Summary
- **Total Effort**: ~36 hours (5 days)
- **Prerequisite**: Grammar analysis
- **Risk**: High (depends on grammar format)

---

## Phase 3: Enhanced Error Messages (Priority: Medium)

### Objective
Provide actionable, helpful error messages with suggestions.

### Features

#### 3.1 "Did You Mean?" Suggestions
```
Error: Undefined variable: 'custName'
  Did you mean: customerName, customerId?

  Available variables in scope:
    - customerName (String)
    - customerId (Number)
    - input (Object)
```

**Implementation**: Levenshtein distance algorithm for fuzzy matching

**Effort**: 8 hours

---

#### 3.2 Multi-Error Reporting
Currently we exit on first error. Should collect all errors:

```
Found 3 errors in script:

  ✗ Line 6: Reserved keyword 'input' (use $input)
  ✗ Line 7: Undefined variable: totalAmount
  ✗ Line 12: Type mismatch: expected Number, got String

Fix these errors and run validate again.
```

**Effort**: 6 hours

---

#### 3.3 Error Recovery
Parser should recover from errors to find more issues:

**Effort**: 12 hours (complex)

---

### Phase 3 Summary
- **Total Effort**: ~26 hours (3-4 days)
- **Dependencies**: Phase 1 complete

---

## Phase 4: Documentation and Tooling (Priority: Low)

### Tasks

#### 4.1 Error Catalog
Create comprehensive error reference:
- Error codes
- Descriptions
- Examples
- Fixes

**Effort**: 8 hours

---

#### 4.2 Language Reference Updates
- Reserved keywords section
- Validation levels explained
- Common mistakes guide

**Effort**: 6 hours

---

#### 4.3 LSP Integration
Update Language Server Protocol support:
- Real-time validation
- Inline error display
- Quick fixes

**Effort**: 16 hours

---

### Phase 4 Summary
- **Total Effort**: ~30 hours (4 days)

---

## Timeline and Priorities

### Immediate (This Week)
- ✅ Document validation gaps
- ✅ Create enhancement plan
- **Phase 1.1-1.3**: Reserved keyword detection (1 day)

### Short Term (Next 2 Weeks)
- **Phase 1.4-1.5**: Complete Phase 1 (1 day)
- **Phase 2.1**: Grammar analysis (1 day)
- **Phase 3.1**: "Did you mean?" suggestions (1 day)

### Medium Term (Next Month)
- **Phase 2.2-2.3**: Grammar-derived rules (1 week)
- **Phase 3.2-3.3**: Multi-error reporting (3-4 days)

### Long Term (Next Quarter)
- **Phase 4**: Documentation and LSP integration (1 week)

---

## Success Criteria

### Phase 1
- [x] Code context display working
- [ ] Reserved keyword errors detected with exit code 1
- [ ] Helpful error messages with suggestions
- [ ] All validation tests pass
- [ ] Documentation updated

### Phase 2
- [ ] Grammar rules cataloged
- [ ] Rule generator working
- [ ] Generated rules integrated
- [ ] No regression in existing tests

### Phase 3
- [ ] Fuzzy matching working
- [ ] Multiple errors reported
- [ ] Error recovery implemented
- [ ] User feedback positive

### Phase 4
- [ ] Error catalog complete
- [ ] Language reference updated
- [ ] LSP showing real-time errors

---

## Risks and Mitigation

### Risk 1: Grammar Not Formally Defined
**Likelihood**: Medium
**Impact**: High
**Mitigation**:
- Start with hand-written rules
- Document grammar as we go
- Gradually formalize

### Risk 2: Performance Impact
**Likelihood**: Low
**Impact**: Medium
**Mitigation**:
- Benchmark validation performance
- Cache validation results
- Optimize hot paths

### Risk 3: Breaking Changes
**Likelihood**: Medium
**Impact**: High
**Mitigation**:
- Add errors as warnings first
- Use feature flags for new checks
- Provide migration guide

---

## Resources Required

- **Development**: 1 developer, ~2-3 weeks full-time
- **Testing**: Conformance suite expansion
- **Documentation**: Technical writer support (optional)
- **Review**: Architecture review for Phase 2

---

## Next Steps

1. ✅ Get plan approval
2. Create GitHub issues for each phase
3. Start Phase 1.1 (ReservedKeywords.kt)
4. Weekly progress reviews
5. Adjust plan based on learnings

---

## Appendix: Alternative Approaches

### Alternative A: External Validator
Use existing tools (e.g., JSON Schema validator) for validation.

**Pros**: Mature, well-tested
**Cons**: Not UTL-X specific, limited customization

### Alternative B: LSP-First Approach
Build validation into LSP, CLI uses LSP.

**Pros**: Single implementation
**Cons**: Adds complexity, slower CLI

### Alternative C: Attribute Grammar
Use attribute grammar for validation rules.

**Pros**: Formal, powerful
**Cons**: Steep learning curve, tooling needed

**Decision**: Proceed with planned approach, revisit if issues arise.
