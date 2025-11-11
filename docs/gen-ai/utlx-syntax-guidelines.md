# UTLX Syntax Guidelines & Common Errors

## Document Purpose

This document captures common syntax errors, correct patterns, and best practices for writing UTLX transformations. It serves multiple purposes:

1. **AI Context** - Guidelines for AI assistants generating UTLX code
2. **LSP Integration** - Error messages and quick fixes for Monaco editor
3. **User Documentation** - Learning resource for UTLX developers
4. **Test Cases** - Validation patterns for syntax checking

---

## Table of Contents

- [Lambda Expression Syntax](#lambda-expression-syntax)
- [Object Literal Syntax](#object-literal-syntax)
- [Variable Scoping](#variable-scoping)
- [Function Call Patterns](#function-call-patterns)
- [Common Error Patterns](#common-error-patterns)
- [LSP Quick Fixes](#lsp-quick-fixes)

---

## Lambda Expression Syntax

### ❌ INCORRECT - Using `fn()` wrapper

```utlx
filter($employees, fn(e) => e.Department == "Engineering")
map($employees, fn(emp) => { id: emp.EmployeeID })
```

**Error**: `Parse exception: Expected ')' at Location(line=X, column=Y)`

**Problem**: UTLX does not use `fn()` keyword for lambda expressions.

### ✅ CORRECT - Direct parameter syntax

```utlx
filter($employees, e => e.Department == "Engineering")
map($employees, emp => { id: emp.EmployeeID })
```

**Pattern**: `parameter => expression`

### Rule Summary

| Pattern | Syntax | Example |
|---------|--------|---------|
| **Lambda with single parameter** | `param => expression` | `e => e.Department == "Engineering"` |
| **Lambda with object literal** | `param => { ... }` | `emp => { id: emp.EmployeeID }` |
| **Lambda with complex expression** | `param => expr1 + expr2` | `x => x.first + " " + x.last` |

### LSP Integration

**Diagnostic Message**:
```
Unexpected 'fn' keyword in lambda expression
Expected: parameter => expression
Found: fn(parameter) => expression
```

**Quick Fix**:
- Remove `fn()` wrapper
- Keep parameter name and arrow function

---

## Object Literal Syntax

### ❌ INCORRECT - Quoted property names

```utlx
{
  "company": "TechCorp",
  "totalEmployees": count($employees)
}
```

**Error**: Works but inconsistent with UTLX conventions

### ✅ CORRECT - Unquoted property names

```utlx
{
  company: "TechCorp",
  totalEmployees: count($employees)
}
```

**Pattern**: Property names are identifiers, not strings (unless they contain special characters)

### When to Quote

| Scenario | Example | Reasoning |
|----------|---------|-----------|
| **Normal identifiers** | `company: "value"` | No quotes needed |
| **With spaces** | `"full name": value` | Quotes required |
| **With special chars** | `"email-address": value` | Quotes required |
| **Reserved words** | `type: "value"` | Context-dependent |

### LSP Integration

**Diagnostic Message** (Optional, low priority):
```
Property names should be unquoted identifiers
Consider: company: "TechCorp" instead of "company": "TechCorp"
```

**Quick Fix**:
- Remove quotes from property names that are valid identifiers

---

## Variable Scoping

### ❌ INCORRECT - Implicit field access without parameter

```utlx
filter($employees, Department == "Engineering")
map($employees, { id: EmployeeID })
```

**Error**: `Runtime error: Undefined variable: Department`

**Problem**: Lambda functions require explicit parameter reference to access fields

### ✅ CORRECT - Explicit parameter reference

```utlx
filter($employees, e => e.Department == "Engineering")
map($employees, emp => { id: emp.EmployeeID })
```

**Pattern**: Always reference the lambda parameter when accessing properties

### Special Case: Context-Based Field Access

Some UTLX examples show implicit field access:

```utlx
map(input.customer.orders.order, {
  id: orderId,
  value: amount,
  date: date
})
```

**Context**: This works when the field names (`orderId`, `amount`, `date`) exist in the implicit context created by the specific data structure.

**Recommendation**: For clarity and consistency, **always use explicit parameter references**:

```utlx
map(input.customer.orders.order, order => {
  id: order.orderId,
  value: order.amount,
  date: order.date
})
```

### LSP Integration

**Diagnostic Message**:
```
Undefined variable: 'Department'
Did you mean: 'e.Department'? (where 'e' is the lambda parameter)

Tip: Lambda expressions require explicit parameter references
Example: filter($data, item => item.field == value)
```

**Quick Fix**:
- Suggest adding lambda parameter if missing
- Detect probable parameter name from collection variable

---

## Function Call Patterns

### Standard Library Functions

#### `filter(collection, predicate)`

**Signature**: `filter(array, element => boolean)`

**Examples**:
```utlx
// Correct
filter($employees, e => e.Department == "Engineering")
filter($numbers, n => n > 10)
filter($items, item => item.active == true && item.price < 100)

// Incorrect
filter($employees, fn(e) => e.Department == "Engineering")  // ❌ No fn() wrapper
filter($employees, Department == "Engineering")             // ❌ No parameter
```

#### `map(collection, transform)`

**Signature**: `map(array, element => expression)`

**Examples**:
```utlx
// Correct - with object literal
map($employees, emp => {
  id: emp.EmployeeID,
  name: emp.FirstName + " " + emp.LastName
})

// Correct - with simple expression
map($numbers, n => n * 2)

// Incorrect
map($employees, fn(emp) => { ... })     // ❌ No fn() wrapper
map($employees, { id: EmployeeID })      // ❌ No parameter (unless context-based)
```

#### `count(collection)`

**Signature**: `count(array)`

**Examples**:
```utlx
// Correct
count($employees)
count(filter($employees, e => e.Department == "Sales"))

// Common mistake
count(map($employees, ...))  // ⚠️ Usually want length of original, not mapped
```

### LSP Integration

**Parameter Hints**:
```
filter(↓collection: Array<T>, ↓predicate: (T) => Boolean)
map(↓collection: Array<T>, ↓transform: (T) => R)
count(↓collection: Array<T>)
```

**Signature Help**:
- Show parameter types
- Show return types
- Provide usage examples

---

## Common Error Patterns

### Error Pattern 1: Parse Exception - Expected ')'

**Symptom**: `Parse exception: Expected ')' at Location(line=X, column=Y)`

**Common Causes**:

1. **Using `fn()` wrapper in lambda**
   ```utlx
   filter($data, fn(x) => ...)  // ❌
   filter($data, x => ...)       // ✅
   ```

2. **Mismatched parentheses**
   ```utlx
   count(filter($data, x => x.value > 10)  // ❌ Missing closing paren
   count(filter($data, x => x.value > 10)) // ✅
   ```

3. **Incorrect comparison operator**
   ```utlx
   filter($data, x => x.value = 10)   // ❌ Assignment, not comparison
   filter($data, x => x.value == 10)  // ✅ Equality comparison
   ```

### Error Pattern 2: Runtime Error - Undefined Variable

**Symptom**: `Runtime error: Undefined variable: FieldName`

**Common Causes**:

1. **Missing lambda parameter**
   ```utlx
   filter($employees, Department == "Sales")        // ❌
   filter($employees, e => e.Department == "Sales") // ✅
   ```

2. **Incorrect parameter reference**
   ```utlx
   map($employees, emp => { id: EmployeeID })        // ❌
   map($employees, emp => { id: emp.EmployeeID })    // ✅
   ```

3. **Typo in field name**
   ```utlx
   map($employees, e => e.Departmnet)  // ❌ Typo
   map($employees, e => e.Department)  // ✅
   ```

### Error Pattern 3: Incorrect Input Variable Reference

**Symptom**: `Runtime error: Undefined variable: $inputName`

**Common Causes**:

1. **Missing `$` prefix**
   ```utlx
   filter(employees, e => ...)  // ❌
   filter($employees, e => ...) // ✅
   ```

2. **Incorrect input name**
   ```utlx
   // Header: input employees csv
   filter($employee, e => ...)  // ❌ Singular, should be plural
   filter($employees, e => ...) // ✅
   ```

---

## Best Practices

### 1. Always Use Explicit Lambda Parameters

**Recommended**:
```utlx
map($data, item => {
  field1: item.value1,
  field2: item.value2
})
```

**Avoid**:
```utlx
map($data, {
  field1: value1,  // May work in some contexts, but inconsistent
  field2: value2
})
```

### 2. Use Meaningful Parameter Names

**Good**:
```utlx
filter($employees, employee => employee.Department == "Engineering")
map($orders, order => order.total)
```

**Less Clear**:
```utlx
filter($employees, e => e.Department == "Engineering")  // Acceptable but less readable
map($orders, x => x.total)                              // Too generic
```

### 3. Consistent Property Name Style

**Recommended**:
```utlx
{
  firstName: "John",
  lastName: "Smith",
  employeeId: "E001"
}
```

**Avoid Mixing**:
```utlx
{
  firstName: "John",      // camelCase
  last_name: "Smith",     // snake_case - inconsistent
  "EmployeeID": "E001"    // PascalCase + quoted - inconsistent
}
```

### 4. Nested Object Literals

**Correct**:
```utlx
{
  company: "TechCorp",
  contact: {
    email: emp.Email,
    phone: emp.Phone
  },
  address: if (emp.Address != null) {
    street: emp.Address.Street,
    city: emp.Address.City
  } else null
}
```

### 5. Array Operations Chaining

**Correct**:
```utlx
// Good readability with intermediate variables
let engineering = filter($employees, e => e.Department == "Engineering");
let sorted = sort(engineering, e => e.Salary);
map(sorted, e => { name: e.FirstName, salary: e.Salary })

// Or chained (less readable for complex chains)
map(
  sort(
    filter($employees, e => e.Department == "Engineering"),
    e => e.Salary
  ),
  e => { name: e.FirstName, salary: e.Salary }
)
```

---

## LSP Quick Fixes

### Quick Fix 1: Remove `fn()` Wrapper

**Trigger**: Detecting `fn(param) =>` pattern

**Action**:
```
Before: filter($data, fn(x) => x.value > 10)
After:  filter($data, x => x.value > 10)
```

**Code Action**:
```typescript
{
  title: "Remove 'fn()' wrapper from lambda expression",
  kind: CodeActionKind.QuickFix,
  edit: {
    // Remove 'fn(' and the matching ')'
  }
}
```

### Quick Fix 2: Add Lambda Parameter

**Trigger**: Undefined variable error in lambda expression context

**Action**:
```
Before: filter($employees, Department == "Engineering")
After:  filter($employees, e => e.Department == "Engineering")
```

**Code Action**:
```typescript
{
  title: "Add lambda parameter 'e'",
  kind: CodeActionKind.QuickFix,
  edit: {
    // Insert 'e => ' before expression
    // Add 'e.' prefix to variable references
  }
}
```

### Quick Fix 3: Add Missing `$` Prefix

**Trigger**: Undefined variable matching input name

**Action**:
```
Before: filter(employees, e => ...)
After:  filter($employees, e => ...)
```

**Code Action**:
```typescript
{
  title: "Add '$' prefix to input variable",
  kind: CodeActionKind.QuickFix,
  edit: {
    // Add '$' before variable name
  }
}
```

---

## LSP Diagnostic Messages

### Template Structure

```typescript
interface DiagnosticTemplate {
  code: string;
  severity: 'error' | 'warning' | 'info';
  message: string;
  explanation?: string;
  example?: {
    incorrect: string;
    correct: string;
  };
  quickFix?: QuickFixAction[];
}
```

### Example Diagnostics

#### UTLX-001: Lambda Syntax Error

```json
{
  "code": "UTLX-001",
  "severity": "error",
  "message": "Invalid lambda syntax: 'fn()' wrapper not allowed",
  "explanation": "UTLX uses arrow function syntax without the 'fn' keyword",
  "example": {
    "incorrect": "filter($data, fn(x) => x.value > 10)",
    "correct": "filter($data, x => x.value > 10)"
  },
  "quickFix": ["removeFnWrapper"]
}
```

#### UTLX-002: Undefined Variable in Lambda

```json
{
  "code": "UTLX-002",
  "severity": "error",
  "message": "Undefined variable: '{varName}'",
  "explanation": "Lambda expressions require explicit parameter references. Did you mean '{param}.{varName}'?",
  "example": {
    "incorrect": "filter($employees, Department == 'Sales')",
    "correct": "filter($employees, e => e.Department == 'Sales')"
  },
  "quickFix": ["addLambdaParameter"]
}
```

#### UTLX-003: Missing Input Variable Prefix

```json
{
  "code": "UTLX-003",
  "severity": "error",
  "message": "Input variables must be prefixed with '$'",
  "explanation": "UTLX requires the '$' prefix to reference input variables defined in the header",
  "example": {
    "incorrect": "filter(employees, e => ...)",
    "correct": "filter($employees, e => ...)"
  },
  "quickFix": ["addDollarPrefix"]
}
```

#### UTLX-004: Inconsistent Property Naming

```json
{
  "code": "UTLX-004",
  "severity": "info",
  "message": "Consider using unquoted property names for identifiers",
  "explanation": "Property names that are valid identifiers don't need quotes",
  "example": {
    "incorrect": "{ \"company\": \"TechCorp\" }",
    "correct": "{ company: \"TechCorp\" }"
  },
  "quickFix": ["removePropertyQuotes"]
}
```

---

## Testing Guidelines

### Syntax Validation Tests

```typescript
describe('Lambda Expression Syntax', () => {
  test('should reject fn() wrapper', () => {
    const code = 'filter($data, fn(x) => x.value > 10)';
    expect(parse(code)).toThrow('Expected )');
  });

  test('should accept correct lambda syntax', () => {
    const code = 'filter($data, x => x.value > 10)';
    expect(parse(code)).not.toThrow();
  });
});

describe('Variable Scoping', () => {
  test('should error on undefined variable in lambda', () => {
    const code = 'filter($employees, Department == "Sales")';
    expect(execute(code)).toThrow('Undefined variable: Department');
  });

  test('should accept explicit parameter reference', () => {
    const code = 'filter($employees, e => e.Department == "Sales")';
    expect(execute(code)).not.toThrow();
  });
});
```

---

## AI Context Guidelines

### For Code Generation

When generating UTLX code, always:

1. **Use arrow function syntax without `fn()` wrapper**
   - ✅ `x => expression`
   - ❌ `fn(x) => expression`

2. **Use explicit lambda parameters**
   - ✅ `filter($data, item => item.field == value)`
   - ❌ `filter($data, field == value)`

3. **Reference input variables with `$` prefix**
   - ✅ `filter($employees, ...)`
   - ❌ `filter(employees, ...)`

4. **Use unquoted property names in objects**
   - ✅ `{ company: "value" }`
   - ❌ `{ "company": "value" }`

5. **Always reference lambda parameter when accessing fields**
   - ✅ `map($items, item => item.name)`
   - ❌ `map($items, name)` (even if it might work in some contexts)

### Example Prompts

**Good Prompt Response**:
```
User: "Filter employees by department"
AI: filter($employees, e => e.Department == "Engineering")
```

**Bad Prompt Response**:
```
User: "Filter employees by department"
AI: filter($employees, fn(e) => e.Department == "Engineering")  // ❌ Uses fn()
```

---

## Monaco Editor Integration

### Syntax Highlighting Rules

```typescript
const utlxTokens = {
  keywords: [
    'input', 'output', 'let', 'if', 'then', 'else',
    'true', 'false', 'null'
  ],
  operators: [
    '=>', '==', '!=', '>', '<', '>=', '<=',
    '+', '-', '*', '/', '%',
    '&&', '||', '!'
  ],
  // Note: 'fn' is NOT a keyword in UTLX
};
```

### Hover Information

When hovering over lambda expressions:

```typescript
{
  contents: [
    { value: 'Lambda Expression' },
    { value: 'Syntax: parameter => expression' },
    { value: 'Example: e => e.Department == "Sales"' }
  ]
}
```

### Completion Items

For `filter()` function:

```typescript
{
  label: 'filter',
  kind: CompletionItemKind.Function,
  insertText: 'filter($${1:collection}, ${2:item} => ${3:condition})',
  insertTextFormat: InsertTextFormat.Snippet,
  documentation: {
    value: 'Filters array elements based on a condition\n\n' +
           'Example: filter($employees, e => e.Department == "Sales")'
  }
}
```

---

## Summary Checklist

### Before Submitting UTLX Code

- [ ] Lambda expressions use `param => expr` syntax (NOT `fn(param) =>`)
- [ ] All field accesses in lambdas reference the parameter (e.g., `e.field`)
- [ ] Input variables use `$` prefix (e.g., `$employees`)
- [ ] Property names in objects are unquoted (unless required)
- [ ] All parentheses are balanced
- [ ] Comparison uses `==` not `=`
- [ ] Variable names match the input declarations in the header

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-11-11 | Initial guidelines based on CSV example fixes |

---

## Additional Resources

- [UTLX Language Specification](../README.md)
- [Standard Library Reference](./stdlib-reference.md)
- [LSP Protocol Documentation](./lsp-protocol.md)
- [Example Transformations](../examples/)

---

## Contributing

If you discover additional syntax patterns or errors, please:

1. Document the error pattern
2. Provide incorrect and correct examples
3. Suggest LSP diagnostic messages
4. Add test cases
5. Submit a pull request updating this document
