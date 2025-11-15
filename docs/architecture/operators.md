# UTLX Operators

**Version:** 1.0
**Last Updated:** 2025-01-15
**Status:** Documentation & Architecture Proposal

---

## Table of Contents

1. [Overview](#overview)
2. [Operator Reference](#operator-reference)
   - [Arithmetic Operators](#arithmetic-operators)
   - [Comparison Operators](#comparison-operators)
   - [Logical Operators](#logical-operators)
   - [Special Operators](#special-operators)
3. [Precedence and Associativity](#precedence-and-associativity)
4. [Current Implementation](#current-implementation)
5. [Architecture Analysis](#architecture-analysis)
6. [Proposed API Architecture](#proposed-api-architecture)
7. [Future Considerations](#future-considerations)

---

## Overview

UTLX operators are the building blocks for expressions in UTLX transformations. They enable arithmetic, comparison, logical operations, and special features like functional composition, safe navigation, and attribute access.

### Categories

UTLX supports **20 operators** organized into **4 categories**:

- **Arithmetic** (6 operators): Mathematical operations
- **Comparison** (6 operators): Value comparisons
- **Logical** (3 operators): Boolean logic
- **Special** (5 operators): Advanced language features

### Design Principles

1. **Familiar Syntax**: Operators follow JavaScript/TypeScript conventions where applicable
2. **Type Safety**: Operators respect UTLX's type system
3. **Composability**: Operators work seamlessly with functions and pipes
4. **UDM Integration**: Special operators interact with Universal Data Model metadata

---

## Operator Reference

### Arithmetic Operators

Arithmetic operators perform mathematical operations on numeric values.

#### `+` Addition

**Description:** Adds two numbers or concatenates strings.

**Syntax:** `value1 + value2`

**Precedence:** 5 (left associative)

**Examples:**
```utlx
10 + 5  // 15
"Hello" + " World"  // "Hello World"
$input[0].price + $input[1].price
```

---

#### `-` Subtraction / Negation

**Description:** Subtracts the second number from the first, or negates a number (unary).

**Syntax:** `value1 - value2` or `-value`

**Precedence:** 5 (left associative)

**Unary:** Yes

**Examples:**
```utlx
10 - 5  // 5
-42  // -42
$input[0].total - $input[0].discount
```

---

#### `*` Multiplication

**Description:** Multiplies two numbers.

**Syntax:** `value1 * value2`

**Precedence:** 4 (left associative)

**Examples:**
```utlx
10 * 5  // 50
$input[0].quantity * $input[0].price
3.14 * radius * radius
```

---

#### `/` Division

**Description:** Divides the first number by the second.

**Syntax:** `value1 / value2`

**Precedence:** 4 (left associative)

**Examples:**
```utlx
10 / 5  // 2
$input[0].total / $input[0].quantity
100 / 3  // 33.333...
```

---

#### `%` Modulo

**Description:** Returns the remainder of division.

**Syntax:** `value1 % value2`

**Precedence:** 4 (left associative)

**Examples:**
```utlx
10 % 3  // 1
100 % 7  // 2
$input[0].id % 2 == 0  // Check if even
```

---

#### `**` Exponentiation

**Description:** Raises the first number to the power of the second.

**Syntax:** `base ** exponent`

**Precedence:** 3 (right associative)

**Examples:**
```utlx
2 ** 3  // 8
10 ** 2  // 100
radius ** 2 * 3.14159  // Circle area
```

---

### Comparison Operators

Comparison operators evaluate relationships between values and return boolean results.

#### `==` Equal

**Description:** Checks if two values are equal.

**Syntax:** `value1 == value2`

**Precedence:** 7 (left associative)

**Examples:**
```utlx
$input[0].status == "active"
count == 0
$input[0].id == $input[1].id
```

---

#### `!=` Not Equal

**Description:** Checks if two values are not equal.

**Syntax:** `value1 != value2`

**Precedence:** 7 (left associative)

**Examples:**
```utlx
$input[0].status != "deleted"
result != null
$input[0].type != $input[1].type
```

---

#### `<` Less Than

**Description:** Checks if the first value is less than the second.

**Syntax:** `value1 < value2`

**Precedence:** 6 (left associative)

**Examples:**
```utlx
$input[0].price < 100
age < 18
$input[0].date < parseDate("2024-01-01")
```

---

#### `>` Greater Than

**Description:** Checks if the first value is greater than the second.

**Syntax:** `value1 > value2`

**Precedence:** 6 (left associative)

**Examples:**
```utlx
$input[0].price > 100
age > 65
$input[0].score > threshold
```

---

#### `<=` Less Than or Equal

**Description:** Checks if the first value is less than or equal to the second.

**Syntax:** `value1 <= value2`

**Precedence:** 6 (left associative)

**Examples:**
```utlx
$input[0].price <= 100
age <= 18
$input[0].count <= maxCount
```

---

#### `>=` Greater Than or Equal

**Description:** Checks if the first value is greater than or equal to the second.

**Syntax:** `value1 >= value2`

**Precedence:** 6 (left associative)

**Examples:**
```utlx
$input[0].price >= 100
age >= 18
$input[0].score >= passingGrade
```

---

### Logical Operators

Logical operators perform boolean logic operations.

#### `&&` Logical AND

**Description:** Returns true if both operands are true.

**Syntax:** `condition1 && condition2`

**Precedence:** 8 (left associative)

**Examples:**
```utlx
$input[0].status == "active" && $input[0].price > 0
age >= 18 && hasLicense
x > 0 && x < 100
```

---

#### `||` Logical OR

**Description:** Returns true if at least one operand is true.

**Syntax:** `condition1 || condition2`

**Precedence:** 9 (left associative)

**Examples:**
```utlx
$input[0].status == "active" || $input[0].status == "pending"
age < 18 || age > 65
isAdmin || isOwner
```

---

#### `!` Logical NOT

**Description:** Negates a boolean value.

**Syntax:** `!condition`

**Precedence:** 2 (right associative)

**Unary:** Yes

**Examples:**
```utlx
!$input[0].isDeleted
!(x > 10)
!isEmpty($input)
```

---

### Special Operators

Special operators provide advanced language features unique to UTLX.

#### `|>` Pipe

**Description:** Pipes the result of one expression into another function. Enables functional composition.

**Syntax:** `value |> function`

**Precedence:** 12 (right associative)

**Examples:**
```utlx
$input |> filter(x => x.active) |> map(x => x.name)
parseDate($input[0].date) |> formatDate("yyyy-MM-dd")
$input |> groupBy(x => x.category) |> count
```

**Use Cases:**
- Chaining transformations
- Functional programming style
- Data pipeline composition

---

#### `?.` Safe Navigation

**Description:** Safely accesses a property. Returns null if the object is null/undefined instead of throwing an error.

**Syntax:** `object?.property`

**Precedence:** 1 (left associative)

**Examples:**
```utlx
$input[0]?.address?.city
user?.profile?.email
$input?.data?.items
```

**Use Cases:**
- Accessing nested properties that might not exist
- Avoiding null pointer errors
- Optional data handling

---

#### `??` Nullish Coalescing

**Description:** Returns the right operand if the left operand is null or undefined, otherwise returns the left operand.

**Syntax:** `value ?? defaultValue`

**Precedence:** 10 (left associative)

**Examples:**
```utlx
$input[0].name ?? "Unknown"
user.age ?? 0
$input[0]?.email ?? "no-email@example.com"
```

**Use Cases:**
- Providing default values
- Handling optional fields
- Fallback logic

---

#### `=>` Lambda Arrow

**Description:** Defines a lambda (anonymous function). Used in map, filter, and other higher-order functions.

**Syntax:** `param => expression`

**Precedence:** 1 (right associative)

**Examples:**
```utlx
map($input, x => x.name)
filter($input, item => item.price > 100)
sortBy($input, e => e.date)
```

**Use Cases:**
- Inline function definitions
- Callbacks for higher-order functions
- Transformation logic

---

#### `...` Spread

**Description:** Spreads properties from one object into another object literal.

**Syntax:** `{ ...object, newProp: value }`

**Precedence:** 1 (left associative)

**Examples:**
```utlx
{ ...user, status: "active" }
{ name: "John", ...otherFields }
{ ...$input[0], updatedAt: now() }
```

**Use Cases:**
- Object composition
- Adding/overriding properties
- Merging objects

---

## Precedence and Associativity

Operator precedence determines the order of evaluation in expressions. Lower numbers = higher precedence (evaluated first).

| Precedence | Operators | Associativity | Description |
|------------|-----------|---------------|-------------|
| 1 | `?.`, `=>`, `...` | left/right | Member access, lambdas, spread |
| 2 | `!`, `-` (unary) | right | Unary negation |
| 3 | `**` | right | Exponentiation |
| 4 | `*`, `/`, `%` | left | Multiplicative |
| 5 | `+`, `-` | left | Additive |
| 6 | `<`, `>`, `<=`, `>=` | left | Relational |
| 7 | `==`, `!=` | left | Equality |
| 8 | `&&` | left | Logical AND |
| 9 | `||` | left | Logical OR |
| 10 | `??` | left | Nullish coalescing |
| 12 | `|>` | right | Pipe |

### Precedence Examples

```utlx
// Different precedence: multiplication before addition
2 + 3 * 4  // 14, not 20
// Evaluated as: 2 + (3 * 4)

// Same precedence: left-to-right evaluation
a * b / c % d  // ((a * b) / c) % d
20 * 10 / 5 % 3  // ((200) / 5) % 3 = 40 % 3 = 1
// All multiplicative operators have same precedence (level 4)
// Left associativity means evaluate left-to-right

// Right associativity: exponentiation evaluates right-to-left
2 ** 3 ** 2  // 2 ** (3 ** 2) = 2 ** 9 = 512, not 64
// Right associativity means evaluate right-to-left

// Comparison before logical AND
x > 0 && x < 100  // (x > 0) && (x < 100)

// Pipe has lowest precedence
$input |> filter(x => x.price > 100) |> map(x => x.name)
```

---

## Current Implementation

### Frontend (TypeScript)

**Location:** `theia-extension/utlx-theia-extension/src/browser/function-builder/operators-data.ts`

**Structure:**
```typescript
export interface OperatorInfo {
    symbol: string;           // e.g., "+"
    name: string;            // e.g., "Addition"
    category: string;        // "Arithmetic", "Comparison", "Logical", "Special"
    description: string;     // Full description
    syntax: string;          // e.g., "value1 + value2"
    precedence: number;      // Operator precedence level
    associativity: 'left' | 'right';
    examples: string[];      // Usage examples
    tooltip: string;         // Brief tooltip text
    unary?: boolean;         // True if unary operator
}

export const UTLX_OPERATORS: OperatorInfo[] = [ /* 20 operators */ ];
```

**Usage:**
- Function Builder Operators tab
- Operator tree display with categories
- Help dialog with operator documentation
- Smart context-aware insertion

**Files:**
- `operators-data.ts` - Operator definitions
- `operators-tree.tsx` - UI component for operator tree
- `operator-insertion-generator.ts` - Smart insertion logic
- `function-builder-dialog.tsx` - Integration with Function Builder

---

### Backend (Kotlin)

**Locations:**

1. **Lexer** - `modules/core/src/main/kotlin/org/apache/utlx/core/lexer/token_types.kt`
   - Token type definitions for each operator
   - Example: `PLUS`, `MINUS`, `STAR`, `SLASH`, `PERCENT`, etc.

2. **AST** - `modules/core/src/main/kotlin/org/apache/utlx/core/ast/ast_nodes.kt`
   - `BinaryOperator` enum: PLUS, MINUS, MULTIPLY, DIVIDE, MODULO, EXPONENT, etc.
   - `UnaryOperator` enum: MINUS, NOT

3. **Parser** - `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`
   - Operator precedence defined implicitly via parsing method hierarchy
   - Recursive descent parsing determines evaluation order
   - Lines 788-850: expression → term → factor → exponentiation → unary

**Limitations:**
- No centralized operator registry
- No metadata export capability
- Precedence embedded in parsing logic (not exposed)
- No REST API endpoint for operators

---

### Current Duplication

**Problem:** Operator information exists in two places:

1. **Frontend** (`operators-data.ts`):
   - Rich metadata (descriptions, examples, tooltips)
   - Precedence and associativity
   - User-facing documentation

2. **Backend** (lexer/parser/AST):
   - Token definitions
   - Parsing rules
   - Evaluation logic

**Risks:**
- Frontend metadata can drift from backend implementation
- Precedence in frontend might not match parser
- Examples might become outdated
- No compile-time guarantee of consistency

---

## Architecture Analysis

### How Functions Are Handled (Comparison)

Functions follow a centralized API pattern that operators should emulate:

```
Backend (Kotlin)                    REST API                Frontend (TypeScript)
================                    =========               =====================
StandardLibrary              →      /api/functions     →    getFunctions()
  .exportRegistry()                                              ↓
                                                           FunctionInfo[]
Contains:                                                        ↓
- Name, category                                      Function Builder Dialog
- Signature, description
- Parameters, return type
- Examples
```

**Key Characteristics:**
1. Backend is single source of truth
2. Metadata exported via REST API
3. Frontend fetches dynamically
4. No hardcoded duplication
5. Extensible and maintainable

### Why This Pattern Works

✅ **Single Source of Truth:** Backend owns language semantics
✅ **Consistency:** Frontend always reflects backend capabilities
✅ **Maintainability:** Update in one place
✅ **Extensibility:** Add functions without touching frontend code
✅ **Tooling Support:** LSP/MCP can use same API
✅ **Multi-Frontend:** VS Code, web IDE can share metadata

---

### Current Approach (Hardcoded Frontend)

**Pros:**
- ✅ Works offline (no API dependency)
- ✅ Fast (no network calls)
- ✅ Simple implementation
- ✅ Complete control over metadata

**Cons:**
- ❌ Duplication between frontend and backend
- ❌ Risk of inconsistency
- ❌ Must update two codebases for new operators
- ❌ LSP/MCP can't access metadata
- ❌ Doesn't scale to multiple frontends
- ❌ Precedence might not match parser

---

## Proposed API Architecture

### Overview

Create a backend API endpoint `/api/operators` that exposes operator metadata, following the proven pattern used for `/api/functions`.

### Backend Implementation

#### 1. Create Operator Registry

**File:** `stdlib/src/main/kotlin/org/apache/utlx/stdlib/OperatorRegistry.kt`

```kotlin
package org.apache.utlx.stdlib

import org.apache.utlx.core.ast.BinaryOperator
import org.apache.utlx.core.ast.UnaryOperator
import org.apache.utlx.core.lexer.TokenType

/**
 * Registry of all UTLX operators with metadata for tooling and documentation.
 */
object OperatorRegistry {

    data class OperatorInfo(
        val symbol: String,
        val name: String,
        val category: String,
        val description: String,
        val syntax: String,
        val precedence: Int,
        val associativity: String,  // "left" or "right"
        val examples: List<String>,
        val tooltip: String,
        val unary: Boolean = false,
        val tokenType: String,
        val binaryOp: String? = null,
        val unaryOp: String? = null
    )

    data class OperatorRegistryData(
        val operators: List<OperatorInfo>,
        val version: String = "1.0"
    )

    fun exportRegistry(): OperatorRegistryData {
        return OperatorRegistryData(
            operators = listOf(
                // Arithmetic
                OperatorInfo(
                    symbol = "+",
                    name = "Addition",
                    category = "Arithmetic",
                    description = "Adds two numbers or concatenates strings.",
                    syntax = "value1 + value2",
                    precedence = 5,
                    associativity = "left",
                    examples = listOf(
                        "10 + 5  // 15",
                        "\"Hello\" + \" World\"  // \"Hello World\"",
                        "\$input[0].price + \$input[1].price"
                    ),
                    tooltip = "Addition / String concatenation",
                    tokenType = "PLUS",
                    binaryOp = "PLUS"
                ),
                // ... all 20 operators
            )
        )
    }
}
```

#### 2. Add REST Endpoint

**File:** `modules/daemon/src/main/kotlin/.../RestApiServer.kt`

```kotlin
// Add to RestApiServer routing:

get("/api/operators") {
    try {
        val registry = OperatorRegistry.exportRegistry()
        call.respond(registry)
    } catch (e: Exception) {
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to e.message)
        )
    }
}
```

### Frontend Implementation

#### 1. Update Protocol

**File:** `protocol.ts`

```typescript
// OperatorInfo interface already exists!
export interface OperatorInfo {
    symbol: string;
    name: string;
    category: string;
    description: string;
    syntax: string;
    precedence: number;
    associativity: 'left' | 'right';
    examples: string[];
    tooltip: string;
    unary?: boolean;
}

// Add to UTLXService interface
export interface UTLXService {
    // ... existing methods
    getOperators(): Promise<OperatorInfo[]>;
}
```

#### 2. Implement Service

**File:** `utlx-service-impl.ts`

```typescript
async getOperators(): Promise<OperatorInfo[]> {
    try {
        const response = await this.daemonClient.getOperators();
        return response.operators;
    } catch (error) {
        console.error('[UTLXService] Failed to fetch operators:', error);
        // Fallback to hardcoded operators if API fails
        return UTLX_OPERATORS;
    }
}
```

**File:** `daemon-client.ts`

```typescript
async getOperators(): Promise<{ operators: OperatorInfo[], version: string }> {
    const response = await fetch(`${this.baseUrl}/api/operators`);
    if (!response.ok) {
        throw new Error(`Failed to fetch operators: ${response.statusText}`);
    }
    return response.json();
}
```

#### 3. Update Function Builder

**File:** `function-builder-dialog.tsx`

```typescript
const [operators, setOperators] = React.useState<OperatorInfo[]>(UTLX_OPERATORS);
const [operatorsLoading, setOperatorsLoading] = React.useState(true);

React.useEffect(() => {
    async function loadOperators() {
        try {
            const ops = await utlxService.getOperators();
            setOperators(ops);
        } catch (error) {
            console.error('Failed to load operators, using defaults:', error);
        } finally {
            setOperatorsLoading(false);
        }
    }
    loadOperators();
}, []);
```

### Migration Strategy

1. **Phase 1:** Create backend registry and API endpoint
2. **Phase 2:** Update frontend to fetch from API
3. **Phase 3:** Keep hardcoded data as fallback
4. **Phase 4:** Monitor for issues, then remove fallback
5. **Phase 5:** Update LSP/MCP to use API

### Benefits

✅ **Single Source of Truth:** Backend defines operators
✅ **Consistency:** Frontend always matches backend
✅ **Maintainability:** Update in one place
✅ **Extensibility:** Add operators without frontend changes
✅ **LSP Support:** Language server can use operator metadata
✅ **MCP Support:** AI assistants can query operators
✅ **Multi-Frontend:** VS Code, web IDE share same data
✅ **Accurate Precedence:** From actual parser implementation

### Implementation Effort

| Task | Estimated Time |
|------|----------------|
| Create OperatorRegistry.kt | 1-2 hours |
| Add REST endpoint | 30 minutes |
| Update protocol.ts | 15 minutes |
| Implement service methods | 45 minutes |
| Update Function Builder | 1 hour |
| Testing and validation | 1 hour |
| **Total** | **~4-5 hours** |

---

## Future Considerations

### 1. LSP (Language Server Protocol) Integration

When an LSP server is implemented for UTLX, it will need operator metadata for:

- **Syntax Highlighting:** Distinguish operators from identifiers
- **Autocomplete:** Suggest operators in appropriate contexts
- **Hover Information:** Show operator documentation on hover
- **Signature Help:** Display operator syntax and precedence
- **Diagnostics:** Validate operator usage and precedence

The `/api/operators` endpoint provides all necessary information.

### 2. MCP (Model Context Protocol) Server

The MCP server can expose operator metadata to AI assistants for:

- **Code Generation:** Generate correct UTLX expressions
- **Expression Building:** Help construct complex expressions
- **Documentation Queries:** Answer questions about operators
- **Example Suggestions:** Provide relevant examples

**Potential MCP Tools:**
- `get_operators` - List all operators
- `get_operator_info(symbol)` - Get detailed info
- `validate_expression(expr)` - Check operator usage

### 3. Custom Operators (Future Extension)

If UTLX adds support for user-defined operators:

- Backend registry can include custom operators
- API returns standard + custom operators
- Precedence conflicts can be validated
- Documentation auto-generated

### 4. Operator Overloading

If operator overloading is added (e.g., `+` for arrays):

- Registry can include type-specific behavior
- Examples can show different type combinations
- Type inference can use operator signatures

### 5. IDE Extensions

VS Code, IntelliJ, or other IDE extensions can:

- Query `/api/operators` for accurate metadata
- Show inline documentation
- Provide context-aware completions
- Validate expressions in real-time

---

## Summary

### Current State

- **20 operators** across 4 categories
- **Hardcoded in frontend** (`operators-data.ts`)
- **Defined in backend** (lexer, parser, AST) but not exposed
- **Duplication risk** between frontend and backend
- **No API** for external tools

### Recommendation

✅ **Create `/api/operators` endpoint** following the functions pattern
✅ **Centralize metadata in backend**
✅ **Enable LSP/MCP integration**
✅ **Improve maintainability**
✅ **Support multi-frontend architecture**

### Next Steps

1. **Document** operators (✅ this document)
2. **Discuss** API design with team
3. **Implement** backend registry (if approved)
4. **Update** frontend to use API
5. **Test** and validate consistency
6. **Enable** LSP/MCP integration

---

**Document Version:** 1.0
**Author:** UTLX Team
**Date:** 2025-01-15
**Related Files:**
- `theia-extension/utlx-theia-extension/src/browser/function-builder/operators-data.ts`
- `modules/core/src/main/kotlin/org/apache/utlx/core/lexer/token_types.kt`
- `modules/core/src/main/kotlin/org/apache/utlx/core/ast/ast_nodes.kt`
- `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`
