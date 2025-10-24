# Proposal: Migrate from `@` to `$` for Input References

**Status:** Draft
**Author:** Claude Code Analysis
**Date:** 2025-10-24
**Related Issues:** Conformance test investigation, @ symbol ambiguity

## Executive Summary

This proposal recommends migrating UTL-X from using `@` for input references to using `$`, while keeping `@` exclusively for XML attribute access. This change aligns with 25+ years of industry standards (XSLT, XPath, JSONata, JQ) and eliminates a significant source of confusion.

## Problem Statement

### Current Ambiguity

The `@` symbol currently has **two different meanings** in UTL-X:

1. **Input reference prefix:**
   ```utlx
   $input        // Default input
   $orders       // Named input "orders"
   $customers    // Named input "customers"
   ```

2. **XML attribute accessor:**
   ```utlx
   element.@id           // Attribute "id"
   Order.@customerId     // Attribute "customerId"
   ```

### Confusion Examples

```utlx
// How many different meanings of @?
$orders.Order[0].@customerId
  ↑              ↑
  input ref      attribute

// Extremely confusing
$input.Order.@id.@type
  ↑         ↑   ↑
  Which is which?

// Ambiguous naming
input: input xml    // Can create input named "input"
$input.data         // Is this the input or an attribute?
```

### Real-World Impact

From conformance test investigation:
- SAP test had incorrect path: `$input.Material` vs `$input.SAP_Response.Material`
- Developers confused about when to use `$input` vs `input`
- Multi-input tests use `$orders`, `$customers` making expressions harder to read
- Example: `$customers |> filter(c => c.@id == $orders[0].@customerId)` has 4 `@` symbols with 2 different meanings

## Industry Standards Analysis

| Language | XML Attributes | Variables/Inputs | Since |
|----------|---------------|------------------|-------|
| **XPath 1.0** | `@attribute` | `$variable` | 1999 |
| **XSLT 1.0** | `@attribute` | `$variable` | 1999 |
| **XSLT 2.0/3.0** | `@attribute` | `$variable` | 2007/2017 |
| **XQuery** | `@attribute` | `$variable` | 2007 |
| **JSONata** | N/A | `$variable` | 2016 |
| **JQ** | N/A | `$variable` | 2012 |
| **DataWeave** | `.@attribute` | `payload`, bindings | 2015 |

**Industry consensus:**
- `@` = XML attributes (from XPath, established 1999)
- `$` = Variables, inputs, data references (from XSLT/XPath, JQ, JSONata)

## Proposed Solution

### Syntax Change

**Before (current):**
```utlx
%utlx 1.0
input: orders xml, customers xml
output json
---
{
  enrichedOrders: $orders |> map(order => {
    let customer = $customers
      |> filter(c => c.@id == order.@customerId)
      |> first()

    {
      orderId: order.@id,
      customerName: customer.name
    }
  })
}
```

**After (proposed):**
```utlx
%utlx 1.0
input: orders xml, customers xml
output json
---
{
  enrichedOrders: $orders |> map(order => {
    let customer = $customers
      |> filter(c => c.@id == order.@customerId)
      |> first()

    {
      orderId: order.@id,
      customerName: customer.name
    }
  })
}
```

### Clear Semantic Separation

```utlx
$orders.Order[0].@id
 ↑               ↑
 $ = input       @ = attribute
 (data source)   (metadata)
```

### Syntax Rules

**After migration:**

1. **`$` prefix** - Input references only
   - `$input` - Default/single input
   - `$orders` - Named input "orders"
   - `$customers` - Named input "customers"
   - `$data`, `$config`, etc.

2. **`@` prefix** - XML attributes only
   - `.@id` - Attribute access
   - `.@customerId` - Attribute access
   - Only valid after element navigation

3. **No prefix** - Everything else
   - `input` - Built-in input keyword (single input, no `$` needed)
   - Let bindings: `let x = 10`
   - Function calls: `map()`, `filter()`
   - Properties: `order.product`

## Migration Strategy

### Phase 1: Parser Updates (Week 1-2)

**Add `$` support to lexer:**
```kotlin
// modules/core/src/main/kotlin/org/apache/utlx/core/lexer/lexer_impl.kt
enum class TokenType {
    // ...existing tokens...
    DOLLAR,           // $ for input references
    AT,               // @ for attributes only
    // ...
}
```

**Update parser to handle both:**
```kotlin
// Support both @ and $ during transition
fun parseInputReference(): ASTNode {
    return when (currentToken.type) {
        TokenType.DOLLAR -> {
            // New preferred syntax
            consume(TokenType.DOLLAR)
            val name = parseIdentifier()
            InputReference(name, usesDollar = true)
        }
        TokenType.AT -> {
            // Legacy syntax - emit deprecation warning
            consume(TokenType.AT)
            val name = parseIdentifier()
            warnings.add("Use \$$name instead of @$name for input references")
            InputReference(name, usesDollar = false)
        }
        else -> error("Expected input reference")
    }
}
```

### Phase 2: Deprecation Warnings (Week 2-3)

**Add compiler warnings:**
```
Warning: $orders is deprecated, use $orders instead
  --> transformation.utlx:5:15
   |
 5 |   data: $orders.Order[0]
   |         ^^^^^^^ use $orders here
   |
   = note: @ is reserved for XML attributes, use $ for inputs
   = help: Replace $orders with $orders
```

**Configuration option:**
```yaml
# .utlx-config.yaml
warnings:
  deprecated-at-input: error  # or 'warn' or 'ignore'

migration:
  allow-legacy-at-syntax: true  # false after v2.0
```

### Phase 3: Documentation Updates (Week 3)

**Update all documentation:**

1. **README.md**
   - Change all examples from `$orders` to `$orders`
   - Add migration note

2. **Language Guide** (`docs/language-guide/`)
   - Update multiple-inputs-outputs.md
   - Update quick-reference-multi-$input.md
   - Add migration guide section

3. **Examples**
   - Update all example files
   - Add "old vs new" comparison examples

4. **API Documentation**
   - Update function signature examples
   - Update best practices

### Phase 4: Test Suite Migration (Week 4)

**Automated migration script:**
```bash
#!/bin/bash
# scripts/migrate-at-to-dollar.sh

# Migrate all test files
find conformance-suite/tests -name "*.yaml" | while read file; do
    # Replace $input with $input (but not .@attribute)
    sed -i.bak 's/@\([a-zA-Z_][a-zA-Z0-9_]*\)\([^.]|$\)/$\1\2/g' "$file"

    # Manual review needed for edge cases
    echo "Migrated: $file"
done
```

**Manual migration of conformance tests:**
- Update all 286 conformance tests
- Verify each test still passes
- Update test documentation

### Phase 5: Breaking Change Release (v2.0.0)

**Version 2.0.0 changes:**

1. **Remove `@` for input references**
   - Parser error: "$inputName is no longer valid, use $inputName"
   - Keep `@` for attributes only

2. **Update error messages:**
   ```
   Error: Unexpected @ before identifier 'orders'
     --> transformation.utlx:3:10
      |
    3 |   data: $orders.Order
      |         ^ expected $orders ($ for inputs, @ for attributes)
   ```

3. **Migration timeline:**
   - v1.x: Both `@` and `$` work (with deprecation warnings)
   - v2.0: Only `$` works for inputs, `@` for attributes only

### Phase 6: Community Communication

**Announcement plan:**

1. **GitHub Discussion** - Explain rationale, gather feedback
2. **Release Notes** - Detailed migration guide
3. **Blog Post** - "Why $ is Better Than @ for Inputs"
4. **Migration Tool** - Automated script to update user code

**Sample announcement:**
```markdown
## UTL-X v2.0: Input Syntax Migration

### TL;DR
- ❌ Old: `$orders.Order.@id`
- ✅ New: `$orders.Order.@id`

### Why?
1. Industry standard (XSLT, XPath, JSONata, JQ)
2. Clear distinction: $ = data, @ = attributes
3. Less confusing for developers

### Migration
Run: `utlx migrate --at-to-dollar myfile.utlx`

### Timeline
- v1.5: Both work (deprecation warnings)
- v2.0: Only $ works (Q1 2026)
```

## Implementation Details

### Parser Changes

**File:** `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`

```kotlin
// Add to Parser class
private fun parseAtomicExpression(): ASTNode {
    return when (currentToken.type) {
        TokenType.DOLLAR -> parseDollarExpression()
        TokenType.AT -> parseAtExpression()  // Attributes or deprecated input
        // ...
    }
}

private fun parseDollarExpression(): ASTNode {
    // $ always means input reference
    consume(TokenType.DOLLAR)
    val name = parseIdentifier()

    // Validate input name exists
    if (name !in declaredInputs) {
        error("Unknown input: \$$name",
              hint = "Declared inputs: ${declaredInputs.joinToString()}")
    }

    return InputReference(name)
}

private fun parseAtExpression(): ASTNode {
    // @ can be:
    // 1. Attribute (after .) - e.g., element.@id
    // 2. Legacy input ref (deprecated) - e.g., $orders

    consume(TokenType.AT)
    val name = parseIdentifier()

    // Check context: after dot = attribute, otherwise = legacy input
    if (previousToken.type == TokenType.DOT) {
        return AttributeAccess(name)
    } else {
        // Legacy input syntax
        warnings.add(
            DeprecationWarning(
                location = currentToken.location,
                message = "Use \$$name instead of @$name for input references",
                suggestion = "\$$name"
            )
        )
        return InputReference(name, isLegacySyntax = true)
    }
}
```

### AST Node Updates

**File:** `modules/core/src/main/kotlin/org/apache/utlx/core/ast/ast_nodes.kt`

```kotlin
// Existing node (update)
data class InputReference(
    val inputName: String,
    val isLegacySyntax: Boolean = false,  // Track for migration warnings
    override val location: Location
) : ASTNode

// Existing attribute access (no change needed)
data class AttributeAccess(
    val attributeName: String,
    override val location: Location
) : ASTNode

// Member access now clearly separated
data class MemberAccess(
    val target: ASTNode,
    val memberName: String,
    val isAttributeAccess: Boolean = false,  // true if .@attr
    override val location: Location
) : ASTNode
```

### Lexer Changes

**File:** `modules/core/src/main/kotlin/org/apache/utlx/core/lexer/lexer_impl.kt`

```kotlin
class Lexer(private val input: String) {
    // ...existing code...

    private fun scanToken(): Token {
        return when (val c = advance()) {
            '$' -> Token(TokenType.DOLLAR, "$", currentLocation())
            '@' -> Token(TokenType.AT, "@", currentLocation())
            // ...rest of tokens...
        }
    }
}
```

### Migration Tool

**File:** `tools/migrate-at-to-dollar/src/main/kotlin/Main.kt`

```kotlin
package org.apache.utlx.tools.migrate

import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: utlx migrate [--at-to-dollar] <file>")
        return
    }

    val file = File(args.last())
    if (!file.exists()) {
        println("Error: File not found: ${file.path}")
        return
    }

    println("Migrating ${file.path}...")
    val content = file.readText()
    val migrated = migrateAtToDollar(content)

    // Create backup
    file.copyTo(File("${file.path}.bak"), overwrite = true)

    // Write migrated content
    file.writeText(migrated)
    println("✓ Migration complete. Backup saved to ${file.path}.bak")
}

fun migrateAtToDollar(content: String): String {
    // Regex to match $inputName (but not .@attribute)
    val inputRefPattern = Regex("""(?<!\.)\@([a-zA-Z_][a-zA-Z0-9_]*)\b""")

    return inputRefPattern.replace(content) { matchResult ->
        val inputName = matchResult.groupValues[1]
        "\$$inputName"
    }
}
```

## Benefits

### 1. Clarity and Readability

**Before:**
```utlx
$orders.Order[0].@id
// Is @ an input or attribute? Have to read context!
```

**After:**
```utlx
$orders.Order[0].@id
// Clear: $ = input source, @ = attribute
```

### 2. Industry Alignment

Developers familiar with XSLT, XPath, JSONata, or JQ will immediately understand:
- `$` = variable/input reference
- `@` = XML attribute

### 3. Better Error Messages

**Before:**
```
Error: Cannot access property 'Material' on NullValue
```

**After:**
```
Error: Unknown input: $order
  --> line 5
   |
 5 | let data = $order.items
   |            ^^^^^^
   |
   = note: Did you mean $orders?
   = help: Available inputs: $orders, $customers, $products
```

### 4. Reduced Cognitive Load

No need to mentally track two meanings of `@` - each symbol has one clear purpose.

### 5. Future Extensibility

- `$` reserved for runtime values (inputs, variables)
- `@` reserved for structural metadata (attributes, annotations)
- `#` could be used for comments or other features
- Clear namespace separation

## Risks and Mitigation

### Risk 1: Breaking Existing Code

**Impact:** High - All multi-input code breaks in v2.0

**Mitigation:**
- Long deprecation period (6+ months)
- Automated migration tool
- Clear warnings in v1.x
- Detailed migration guide
- Support both syntaxes in v1.x

### Risk 2: Community Resistance

**Impact:** Medium - Users may resist change

**Mitigation:**
- Clear communication of benefits
- Show industry alignment (XSLT, XPath users will approve)
- Provide easy migration path
- Listen to feedback during deprecation period

### Risk 3: Documentation Drift

**Impact:** Medium - Old examples on internet

**Mitigation:**
- Update all official documentation immediately
- Add "legacy syntax" warnings to old docs
- Create migration guide with examples
- Version documentation clearly

### Risk 4: Third-party Tools

**Impact:** Low - Few tools exist yet

**Mitigation:**
- Notify known integrators
- Provide migration timeline early
- Offer backward-compatibility mode if needed

## Alternatives Considered

### Alternative 1: Keep @ for Both

**Pros:**
- No breaking change
- Current code works

**Cons:**
- Continued confusion
- Not industry standard
- Hard to explain to new users
- Can't fix the ambiguity

**Verdict:** Not recommended

### Alternative 2: Use Different Symbol (Not $)

Options: `#input`, `~input`, `^input`, `&input`

**Cons:**
- Not industry standard
- Less familiar to developers
- Harder to type (some symbols)

**Verdict:** Not recommended - `$` is the clear choice

### Alternative 3: No Prefix for Inputs

Just use `orders`, `customers` directly

**Pros:**
- Cleaner syntax

**Cons:**
- Namespace collision with functions
- Harder to visually identify inputs
- No clear way to reference default input

**Verdict:** Not recommended

### Alternative 4: Prefix Notation

Use `in.orders`, `in.customers`

**Pros:**
- Readable
- Clear namespace

**Cons:**
- More verbose
- Not industry standard
- Unusual syntax

**Verdict:** Not recommended

## Success Metrics

**v1.x (Deprecation Period):**
- ≥80% of tests updated to use `$`
- Migration tool used successfully on sample projects
- <10 reported issues with migration

**v2.0 (Breaking Change):**
- All official examples use `$`
- ≥95% of community code migrated
- Positive feedback on clarity improvement
- Reduced "how do I reference inputs?" questions

## Timeline

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| **Design & Approval** | 1 week | This proposal, community feedback |
| **Parser Implementation** | 2 weeks | Lexer/parser updates, both syntaxes work |
| **Tooling** | 1 week | Migration tool, linter rules |
| **Documentation** | 1 week | All docs updated with new syntax |
| **Testing** | 2 weeks | Conformance tests migrated, verified |
| **v1.5 Release** | - | Deprecation warnings active |
| **Deprecation Period** | 6 months | Community migration, support |
| **v2.0 Release** | - | Breaking change, `@` = attributes only |

**Total to v2.0:** ~7 months from approval

## Conclusion

Migrating from `@` to `$` for input references:
- ✅ Eliminates ambiguity
- ✅ Aligns with 25+ years of industry standards
- ✅ Improves code readability
- ✅ Makes UTL-X easier to learn
- ✅ Provides better error messages
- ✅ Allows future extensibility

The migration cost is justified by long-term clarity and alignment with developer expectations.

## Recommendations

1. **Approve this proposal** for v2.0 roadmap
2. **Implement parser changes** to support both `@` and `$`
3. **Release v1.5** with deprecation warnings
4. **Provide 6-month migration period**
5. **Release v2.0** with breaking change, `$` only

## Open Questions

1. Should `input` (no prefix) still work for default input? **Recommendation: Yes**
2. Should we allow `$input` even when `input` is declared? **Recommendation: Yes, for consistency**
3. What about backwards compatibility flag? **Recommendation: v1.x only**
4. Should migration tool be built-in to CLI? **Recommendation: Yes - `utlx migrate`**

## References

- XPath 1.0 Specification: https://www.w3.org/TR/xpath/
- XSLT 1.0 Specification: https://www.w3.org/TR/xslt
- JSONata Documentation: https://jsonata.org/
- JQ Manual: https://stedolan.github.io/jq/manual/
- DataWeave Documentation: https://docs.mulesoft.com/dataweave/
- UTL-X Conformance Test Results (261/286 passing, 91.3%)
- UTL-X @ Symbol Ambiguity Analysis (this investigation)

---

**Next Steps:**
1. Review and approve this proposal
2. Create GitHub issue for tracking implementation
3. Schedule work for v1.5/v2.0 milestones
4. Begin parser implementation
