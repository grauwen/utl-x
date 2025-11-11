# UTLX AI Quick Reference

> **For AI Code Generation**: Essential UTLX syntax rules to avoid common errors

## Critical Rules

### 1. Lambda Syntax - NO `fn()` Wrapper

```utlx
❌ WRONG: filter($data, fn(x) => x.value > 10)
✅ RIGHT: filter($data, x => x.value > 10)

❌ WRONG: map($items, fn(item) => { id: item.id })
✅ RIGHT: map($items, item => { id: item.id })
```

**Pattern**: `parameter => expression` (NOT `fn(parameter) => expression`)

### 2. Always Use Explicit Parameter References

```utlx
❌ WRONG: filter($employees, Department == "Sales")
✅ RIGHT: filter($employees, e => e.Department == "Sales")

❌ WRONG: map($employees, { id: EmployeeID })
✅ RIGHT: map($employees, emp => { id: emp.EmployeeID })
```

**Rule**: Lambda body must reference the parameter to access fields

### 3. Input Variables Need `$` Prefix

```utlx
❌ WRONG: filter(employees, e => e.active)
✅ RIGHT: filter($employees, e => e.active)

❌ WRONG: count(data)
✅ RIGHT: count($data)
```

**Rule**: Input variables declared in header are referenced with `$`

### 4. Object Properties - No Quotes (Usually)

```utlx
❌ AVOID: { "company": "TechCorp", "total": 100 }
✅ PREFER: { company: "TechCorp", total: 100 }
```

**Rule**: Use unquoted identifiers unless property name has spaces/special chars

## Common Functions

### filter(collection, predicate)
```utlx
filter($employees, e => e.Department == "Engineering")
filter($numbers, n => n > 10 && n < 100)
filter($items, item => item.active == true)
```

### map(collection, transform)
```utlx
map($employees, emp => { id: emp.ID, name: emp.FirstName })
map($numbers, n => n * 2)
map($items, item => item.value + 10)
```

### count(collection)
```utlx
count($employees)
count(filter($employees, e => e.Department == "Sales"))
```

## Complete Example

```utlx
%utlx 1.0
input employees csv
output json
---
{
  company: "TechCorp",
  totalEmployees: count($employees),
  departments: {
    Engineering: count(filter($employees, e => e.Department == "Engineering")),
    Sales: count(filter($employees, e => e.Department == "Sales"))
  },
  employees: map($employees, emp => {
    id: emp.EmployeeID,
    fullName: emp.FirstName + " " + emp.LastName,
    department: emp.Department,
    salary: emp.Salary
  })
}
```

## Quick Checklist

When generating UTLX code:
- [ ] No `fn()` in lambda expressions
- [ ] Lambda parameters explicitly used (e.g., `e.field`)
- [ ] Input variables have `$` prefix
- [ ] Object properties unquoted
- [ ] Parentheses balanced
- [ ] `==` for equality (not `=`)
