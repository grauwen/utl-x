# Error Enhancer Integration with LSP Diagnostics

## Overview

This document describes how the UTLX error enhancers integrate with the LSP diagnostics schema to provide consistent, helpful error messages across different layers of the system.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   User Code (UTLX)                           │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     Parser Layer                             │
│  • Detects syntax errors                                     │
│  • ParseErrorEnhancer.kt applies UTLX-001 detection         │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Interpreter Layer                          │
│  • Detects runtime errors                                    │
│  • RuntimeErrorEnhancer.kt applies UTLX-002, UTLX-003       │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    LSP/IDE Layer                             │
│  • lsp-diagnostics-schema.json defines error codes          │
│  • Monaco editor displays enhanced messages                  │
│  • Quick fixes based on error patterns                       │
└─────────────────────────────────────────────────────────────┘
```

## Error Code Mapping

| Error Code | Layer | Enhancer | Detection Method | Quick Fix Available |
|------------|-------|----------|------------------|---------------------|
| **UTLX-001** | Parse | `ParseErrorEnhancer` | Token pattern: `fn(param) =>` | ✅ Yes - Remove `fn()` wrapper |
| **UTLX-002** | Runtime | `RuntimeErrorEnhancer` | Undefined variable in lambda context | ✅ Yes - Add lambda parameter |
| **UTLX-003** | Runtime | `RuntimeErrorEnhancer` | Missing `$` on input variable | ✅ Yes - Add `$` prefix |
| **UTLX-004** | Parse/LSP | LSP only | Quoted property names | ✅ Yes - Remove quotes |
| **UTLX-005** | LSP | LSP only | Implicit field access warning | ⚠️  Optional - Add explicit param |
| **UTLX-006** | Parse | Future | Assignment operator in comparison | ✅ Yes - Change `=` to `==` |
| **UTLX-007** | Parse | Native | Unbalanced parentheses | ❌ No |
| **UTLX-008** | Runtime | `RuntimeErrorEnhancer` | Type mismatch | ❌ No |

## Implementation Details

### ParseErrorEnhancer.kt

**Location**: `/modules/core/src/main/kotlin/org/apache/utlx/core/parser/ParseErrorEnhancer.kt`

**Integrated Diagnostics**:
- **UTLX-001**: Lambda `fn()` wrapper detection

**How it works**:
1. Parse exception is caught
2. Token stream is analyzed backward from error position
3. Pattern matching detects `fn(param) =>` syntax
4. Enhanced error message is generated with examples
5. Exception is re-thrown with enhanced message

**Example Enhancement**:
```kotlin
// Original error: "Expected ')'"
// Enhanced error:
"""
Invalid lambda syntax: 'fn()' wrapper not allowed in UTLX (UTLX-001)

UTLX uses arrow function syntax without the 'fn' keyword.

❌ Incorrect: filter($data, fn(x) => x.value > 10)
✅ Correct:   filter($data, x => x.value > 10)

Suggestion: Use 'parameter => expression' syntax
Example: filter($collection, x => ...)

See: https://utlx-lang.org/docs/functions#lambda-expressions
"""
```

### RuntimeErrorEnhancer.kt

**Location**: `/modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/RuntimeErrorEnhancer.kt`

**Integrated Diagnostics**:
- **UTLX-002**: Undefined variable in lambda context
- **UTLX-003**: Missing `$` prefix on input variables
- **UTLX-008**: Type mismatch errors

**How it works**:
1. Runtime error is caught in `Environment.get()`
2. Variable name and context are analyzed
3. Pattern matching detects common mistakes:
   - Field-like names (PascalCase) → likely missing lambda parameter
   - Input-like names → likely missing `$` prefix
4. Enhanced error message is generated
5. Exception is thrown with enhanced message

**Example Enhancement**:
```kotlin
// Original error: "Undefined variable: Department"
// Enhanced error:
"""
Undefined variable: 'Department' (UTLX-002)

This error often occurs when accessing fields without a lambda parameter.

Lambda expressions require explicit parameter references:

❌ Incorrect: filter($employees, Department == "Sales")
✅ Correct:   filter($employees, e => e.Department == "Sales")

❌ Incorrect: map($employees, { id: EmployeeID })
✅ Correct:   map($employees, emp => { id: emp.EmployeeID })

Suggestion: Add a lambda parameter and reference it
Example: filter($collection, item => item.Department == value)

See: https://utlx-lang.org/docs/variables#scoping
"""
```

## Integration Points

### 1. Parser Integration

**File**: `/modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`

The parser should call `ParseErrorEnhancer.enhance()` when catching exceptions:

```kotlin
try {
    // Parse code
} catch (e: ParseException) {
    val enhanced = ParseErrorEnhancer.enhance(e, source, tokens, position)
    throw enhanced
}
```

### 2. Interpreter Integration

**File**: `/modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt`

The Environment class now uses `RuntimeErrorEnhancer` for undefined variables:

```kotlin
fun get(name: String): RuntimeValue {
    return bindings[name] ?: parent?.get(name)
        ?: throw RuntimeErrorEnhancer.enhance(
            RuntimeError("Undefined variable: $name"),
            node = null,
            env = this
        )
}
```

### 3. LSP Integration

**File**: `/theia-extension/utlx-theia-extension/src/node/lsp/utlx-language-server.ts`

The LSP server should:
1. Load diagnostics from `lsp-diagnostics-schema.json`
2. Map backend error codes to diagnostic codes
3. Provide quick fixes based on error patterns

```typescript
// Load diagnostics schema
const diagnosticsSchema = require('../../docs/gen-ai/lsp-diagnostics-schema.json');

// Map backend errors to LSP diagnostics
function mapErrorToDiagnostic(error: Error): Diagnostic {
    // Extract error code (e.g., "UTLX-001")
    const codeMatch = error.message.match(/\(UTLX-(\d+)\)/);
    if (codeMatch) {
        const code = `UTLX-${codeMatch[1]}`;
        const diagDef = diagnosticsSchema.diagnostics.find(d => d.code === code);

        return {
            severity: diagDef.severity,
            range: getErrorRange(error),
            message: error.message,
            code: code,
            source: 'utlx',
            codeDescription: {
                href: diagDef.relatedDocs
            }
        };
    }
}
```

## Error Message Guidelines

All enhanced error messages follow this structure:

1. **Error Title**: Brief description with error code
   ```
   Invalid lambda syntax: 'fn()' wrapper not allowed in UTLX (UTLX-001)
   ```

2. **Explanation**: What the error means
   ```
   UTLX uses arrow function syntax without the 'fn' keyword.
   ```

3. **Examples**: Show incorrect and correct usage
   ```
   ❌ Incorrect: filter($data, fn(x) => x.value > 10)
   ✅ Correct:   filter($data, x => x.value > 10)
   ```

4. **Suggestion**: Actionable advice
   ```
   Suggestion: Use 'parameter => expression' syntax
   ```

5. **Documentation Link**: Reference for more info
   ```
   See: https://utlx-lang.org/docs/functions#lambda-expressions
   ```

## Testing Enhanced Errors

### Unit Tests for ParseErrorEnhancer

```kotlin
@Test
fun `should detect fn wrapper in lambda`() {
    val code = "filter(\$data, fn(x) => x.value > 10)"

    val exception = assertThrows<ParseException> {
        Parser.parse(code)
    }

    assertTrue(exception.message.contains("UTLX-001"))
    assertTrue(exception.message.contains("fn() wrapper not allowed"))
}
```

### Unit Tests for RuntimeErrorEnhancer

```kotlin
@Test
fun `should detect missing lambda parameter`() {
    val code = """
        %utlx 1.0
        input employees csv
        output json
        ---
        filter(${"$"}employees, Department == "Sales")
    """.trimIndent()

    val exception = assertThrows<RuntimeError> {
        execute(code, mapOf("employees" to csvData))
    }

    assertTrue(exception.message.contains("UTLX-002"))
    assertTrue(exception.message.contains("explicit parameter references"))
}
```

### Integration Tests

```kotlin
@Test
fun `should provide enhanced error for real-world lambda mistake`() {
    val utlx = File("examples/csv/01-employee-roster.utlx").readText()

    // Replace correct syntax with incorrect fn() syntax
    val incorrectUtlx = utlx.replace("e =>", "fn(e) =>")

    val exception = assertThrows<ParseException> {
        Parser.parse(incorrectUtlx)
    }

    // Verify enhanced error message
    assertTrue(exception.message.contains("UTLX-001"))
    assertTrue(exception.message.contains("arrow function syntax"))
}
```

## Metrics & Monitoring

Track how often each error code appears to identify:
1. Most common user mistakes
2. Areas needing better documentation
3. Opportunities for additional quick fixes

```kotlin
object ErrorMetrics {
    private val errorCounts = mutableMapOf<String, Int>()

    fun recordError(code: String) {
        errorCounts[code] = errorCounts.getOrDefault(code, 0) + 1
    }

    fun getTopErrors(): List<Pair<String, Int>> {
        return errorCounts.toList()
            .sortedByDescending { it.second }
            .take(10)
    }
}
```

## Future Enhancements

### 1. Context-Aware Suggestions
Analyze surrounding code to provide more specific suggestions:
```kotlin
// Detected: User filtering by field that exists in collection
// Suggest: Use actual field name from CSV header
"Did you mean: filter($employees, e => e.DepartmentName == ...)"
```

### 2. Auto-Fix Generation
Generate edit operations for LSP quick fixes:
```kotlin
data class QuickFix(
    val title: String,
    val edits: List<TextEdit>
)

fun generateAutoFix(error: EnhancedError): QuickFix {
    return when (error.code) {
        "UTLX-001" -> QuickFix(
            title = "Remove 'fn()' wrapper",
            edits = listOf(/* remove fn( and matching ) */)
        )
        // ... other fixes
    }
}
```

### 3. Learning from User Fixes
Track how users fix errors to improve suggestions:
```kotlin
// When user fixes error, record the pattern
ErrorLearning.recordFix(
    errorCode = "UTLX-001",
    originalCode = "fn(x) => ...",
    fixedCode = "x => ...",
    wasHelpful = true
)
```

## Summary

The integration of the LSP diagnostics schema with the error enhancers provides:

✅ **Consistent error codes** across parse and runtime layers
✅ **Helpful error messages** with examples and suggestions
✅ **Documentation links** for learning more
✅ **Quick fix support** for common mistakes
✅ **Metrics tracking** for improving the developer experience

This creates a comprehensive error handling system that helps users quickly identify and fix common UTLX syntax mistakes, based on real-world lessons learned from the CSV examples project.
