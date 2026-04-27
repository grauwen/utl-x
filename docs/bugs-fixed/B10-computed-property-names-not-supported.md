# BUG: Computed Property Names Not Implemented in Parser

**Status:** Resolved
**Severity:** Parser implementation gap — specified syntax not implemented
**Affected versions:** All versions prior to fix
**Date resolved:** 2026-04-03

## Summary

The UTLX language specification defines computed property names (`[expr]: value`) in object literals. Both the formal grammar (`docs/reference/grammar.md:149`) and the user-facing syntax guide (`docs/language-guide/syntax.md:215-222`) document this syntax. However, the parser never implemented the `[expression]` branch, causing a parse error for any transformation that used computed property names.

## Specification

The formal EBNF grammar defines object properties as:

```ebnf
property ::= (identifier | string-literal | '[' expression ']') ':' expression
           | '...' expression  (* spread operator *)
```

The syntax guide documents computed keys with examples:

```utlx
### Computed Keys

{
  [dynamicKey]: "value",
  ["prefix_" + suffix]: "computed"
}
```

Both files pre-date this fix and were never modified as part of the resolution.

## Symptom

Any UTLX transformation using computed property syntax failed at parse time:

```
Parse exception: Expected property name or spread operator
```

Example failing code:

```utlx
let map = items
  |> map(item => [item.id, item])
  |> reduce((acc, pair) => {...acc, [first(pair)]: last(pair)}, {});
```

The error pointed at the `[` in `[first(pair)]`. The spread operator `...acc` was parsed correctly; only the computed property name branch was missing from the parser.

## Root Cause

The `parseObjectLiteral()` method in `parser_impl.kt` handled property keys for identifiers, quoted strings, keywords, and attribute/directive prefixes — but had no branch for `TokenType.LBRACKET`. The parser fell through to: `throw error("Expected property name or spread operator")`.

Additionally, the AST `Property` data class only stored a static `key: String?`, with no field to hold a computed key expression.

## Fix

Seven files were modified across three modules:

### `modules/core` (parser, AST, interpreter, type checker)

**`ast_nodes.kt`** — Added `computedKey: Expression?` field to `Property`:
```kotlin
data class Property(
    val key: String?,
    val value: Expression,
    val location: Location,
    val isAttribute: Boolean = false,
    val isSpread: Boolean = false,
    val computedKey: Expression? = null  // for [expr]: value
)
```

**`parser_impl.kt`** — Added `LBRACKET` branch in `parseObjectLiteral()`:
```kotlin
} else if (match(TokenType.LBRACKET)) {
    val computedKeyExpr = parseExpression()
    consume(TokenType.RBRACKET, "Expected ']' after computed property name")
    consume(TokenType.COLON, "Expected ':' after computed property name")
    val value = parseExpression()
    properties.add(Property(null, value, ..., computedKey = computedKeyExpr))
    continue
}
```

**`interpreter.kt`** — Added computed key evaluation in `ObjectLiteral` handling. When `prop.computedKey != null`, the key expression is evaluated at runtime and coerced to a string.

**`type_system.kt`** — Added computed property handling in type inference. Since the key is dynamic, the property is type-checked but not added to the static type map.

### `modules/analysis` (graph converter, visualizer, advanced type inference)

**`ASTGraphConverter.kt`** — Label computed properties as `[computed]` in data flow graphs.

**`GraphvizASTVisualizer.kt`** — Label computed properties as `[computed]` in Graphviz AST visualizations.

**`AdvancedTypeInference.kt`** — Handle computed properties in advanced type analysis (analyze the value expression, skip static key registration).

## Verification

```
grammar.md  — pre-existing, unmodified, defines [expression] in property rule
syntax.md   — pre-existing, unmodified, documents computed keys with examples
parser_impl.kt — was missing LBRACKET branch, now implemented
```
