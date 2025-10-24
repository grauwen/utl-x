# XML Array Handling in UTL-X

## The XML-to-JSON Cardinality Problem

### Overview

When transforming XML to JSON (or YAML), there's a fundamental impedance mismatch between how XML and JSON represent repeated elements. This is **not a bug** in UTL-X, but rather an inherent problem that affects **all XML-to-JSON converters**.

This document explains the issue, why it exists, and the recommended workaround patterns.

---

## The Problem

### XML Structure

XML naturally represents repeated elements by repeating the tag:

```xml
<Access level="full">
  <Module>Users</Module>
  <Module>Reports</Module>
  <Module>Settings</Module>
</Access>
```

In XML, there's no distinction between "single element" and "array of elements" - you just have elements with the same name at the same level.

### JSON Representation Challenge

When converting to JSON, we must choose:

**Option 1: Represent multiple elements as an array**
```json
{
  "Access": {
    "level": "full",
    "Module": ["Users", "Reports", "Settings"]
  }
}
```

✅ Correctly represents multiple values
✅ Easy to iterate with `map()`

**Option 2: What if there's only ONE module?**

```xml
<Access level="readonly">
  <Module>Dashboard</Module>
</Access>
```

Should this become:
```json
{
  "Module": "Dashboard"          // Single value?
}
```

Or:
```json
{
  "Module": ["Dashboard"]        // Array with one element?
}
```

### The Inconsistency

**The core problem**: The JSON representation changes based on how many elements exist in the XML:

| XML | JSON Type | Access Pattern |
|-----|-----------|----------------|
| One `<Module>` | Object/String | `Access.Module` |
| Multiple `<Module>` | Array | `Access.Module[0]` or `map()` |

This makes transformations fragile - code that works for multiple elements breaks when there's only one element (and vice versa).

---

## UTL-X's Current Behavior

UTL-X's XML parser uses **context-dependent parsing**:

```kotlin
// From xml_parser.kt (line 214-223)
val shouldBeArray = childElements.size > 1 || arrayHints.contains(childName)

properties[childName] = if (shouldBeArray) {
    // Multiple elements → array
    UDM.Array(childElements.map { it.second })
} else {
    // Single element → unwrapped object
    childElements[0].second
}
```

**Behavior:**
- ✅ **Multiple elements** with same name → Array
- ✅ **Single element** → Single object (not wrapped in array)
- ✅ **Explicit hints** via `arrayHints` parameter → Always array

**Why this is correct:**
1. Most intuitive for simple cases (single elements don't need `[0]` access)
2. Matches behavior of popular XML-to-JSON libraries (Jackson, lxml, xmltodict)
3. Reduces boilerplate for common single-element access patterns

---

## Why We Can't "Fix" This

### Attempted Solution 1: Always Use Arrays

```kotlin
// Make ALL child elements arrays
properties[childName] = UDM.Array(childElements.map { it.second })
```

**Problems:**
- ❌ Breaks existing code expecting single values
- ❌ Forces `[0]` access everywhere: `customer.Name[0]` instead of `customer.Name`
- ❌ Makes simple XML transformations verbose and ugly
- ❌ Failed XML parser unit tests immediately

**Example impact:**
```xml
<Customer>
  <Name>Alice</Name>
  <Email>alice@example.com</Email>
</Customer>
```

Would require:
```utlx
// Ugly and verbose!
customer.Name[0]._text
customer.Email[0]._text
```

Instead of natural:
```utlx
customer.Name._text
customer.Email._text
```

### Attempted Solution 2: Always Use Single Values

**Problems:**
- ❌ Can't represent multiple elements with same name
- ❌ Loses data when XML has repeated elements
- ❌ Makes repeated elements impossible to process

### Attempted Solution 3: Use Schemas

**Problems:**
- ❌ Requires XML Schema (XSD) for every transformation
- ❌ Schemas often don't exist or are outdated
- ❌ Adds significant complexity and overhead
- ❌ Doesn't work for dynamic/unknown XML structures

---

## The Recommended Solution: Defensive Coding

### Pattern 1: Check with `isArray()` Before Mapping

When you need to iterate over elements that might be single or multiple:

```utlx
%utlx 1.0
input: users json, permissions xml
output xml
---
{
  Users: $users.users |> map(user => {
    let perms = $permissions.Permissions.UserPermissions
                |> filter(p => p.@userId == user.userId)
                |> first();

    User: {
      @id: user.userId,
      Authorization: {
        Role: perms.Role,

        // ✅ Defensive pattern: ensure Module is always an array
        Modules: (if (isArray(perms.Access.Module))
                   perms.Access.Module
                 else
                   [perms.Access.Module])
                 |> map(mod => { Module: mod })
      }
    }
  })
}
```

**How it works:**
- `isArray(perms.Access.Module)` checks if Module is already an array
- If yes → use it directly
- If no → wrap single element in array `[perms.Access.Module]`
- Now you can safely call `map()` knowing you have an array

### Pattern 2: Create a Helper Function

For cleaner code when this pattern is used multiple times:

```utlx
%utlx 1.0
input xml
output json
---

def ensureArray(value) {
  if (isArray(value)) value else [value]
}

{
  Items: ensureArray($input.Order.Items.Item) |> map(item => {
    sku: item.@sku,
    name: item.Name,
    price: item.Price
  }),

  Categories: ensureArray($input.Order.Categories.Category) |> map(cat => {
    id: cat.@id,
    name: cat.Name
  })
}
```

### Pattern 3: Use Safe Navigation with `first()`

If you only need the first element and don't care about others:

```utlx
// Get first module (works for both single and array)
firstModule: if (isArray(perms.Access.Module))
               perms.Access.Module[0]
             else
               perms.Access.Module
```

Or more concisely:
```utlx
// ensureArray then take first
firstModule: ensureArray(perms.Access.Module) |> first()
```

---

## When Do You Need This?

### ✅ Need Defensive Coding When:

1. **XML → JSON/YAML transformations**
   - Source: XML with potentially repeated elements
   - Target: JSON, YAML, or any format where arrays matter
   - Example: SOAP responses, config files, API responses

2. **Element cardinality is unknown**
   - Working with multiple XML sources
   - Schema varies or is not controlled
   - Dynamic/user-provided XML

3. **Using `map()`, `filter()`, or array operations**
   - Need to iterate over elements
   - Can't guarantee multiple elements exist

### ❌ DON'T Need Defensive Coding When:

1. **XML → XML transformations**
   - XML serializer handles both single and array correctly
   - Outputs multiple `<Module>` elements for arrays
   - Outputs single `<Module>` for single elements
   - **Cardinality is preserved automatically**

2. **JSON → JSON transformations**
   - JSON already distinguishes arrays from single values
   - No ambiguity in source data

3. **Fixed schema with known cardinality**
   - You know certain elements always appear once (e.g., `<OrderId>`)
   - You know certain elements always repeat (e.g., `<LineItem>`)
   - Schema is controlled and guaranteed

---

## Real-World Example: Multi-Input User Permissions

### The Scenario

Merging JSON user data with XML permissions that may have varying numbers of modules:

**Input 1: users.json**
```json
{
  "users": [
    { "userId": "U001", "name": "Alice" },
    { "userId": "U002", "name": "Bob" }
  ]
}
```

**Input 2: permissions.xml**
```xml
<Permissions>
  <UserPermissions userId="U001">
    <Access level="full">
      <Module>Users</Module>
      <Module>Reports</Module>
      <Module>Settings</Module>
    </Access>
  </UserPermissions>
  <UserPermissions userId="U002">
    <Access level="readonly">
      <Module>Dashboard</Module>  <!-- Only ONE module -->
    </Access>
  </UserPermissions>
</Permissions>
```

### ❌ Without Defensive Coding (BROKEN)

```utlx
{
  Users: $users.users |> map(user => {
    let perms = $permissions.Permissions.UserPermissions
                |> filter(p => p.@userId == user.userId)
                |> first();

    User: {
      @id: user.userId,
      Modules: perms.Access.Module |> map(mod => { Module: mod })
      //       ^^^^^^^^^^^^^^^^^^^^
      //       ERROR: map() requires array as first argument
      //       Fails for U002 because Module is single object, not array
    }
  })
}
```

**Error for user U002:**
```
Error: map() requires array as first argument
```

### ✅ With Defensive Coding (WORKS)

```utlx
{
  Users: $users.users |> map(user => {
    let perms = $permissions.Permissions.UserPermissions
                |> filter(p => p.@userId == user.userId)
                |> first();

    User: {
      @id: user.userId,
      Modules: (if (isArray(perms.Access.Module))
                 perms.Access.Module
               else
                 [perms.Access.Module])
               |> map(mod => { Module: mod })
      //       ^^^^^^^^^^^^^^^^^^^^^^^^^
      //       Works for both single and multiple modules!
    }
  })
}
```

**Output:**
```xml
<Users>
  <User id="U001">
    <Modules>
      <Module>Users</Module>
      <Module>Reports</Module>
      <Module>Settings</Module>
    </Modules>
  </User>
  <User id="U002">
    <Modules>
      <Module>Dashboard</Module>
    </Modules>
  </User>
</Users>
```

---

## Why This Is the Best Solution

### Industry Standard Approach

Every major XML-to-JSON library has this same issue:

| Library | Language | Same Issue? | Solution |
|---------|----------|-------------|----------|
| **Jackson** | Java | Yes | Manual array wrapping |
| **xmltodict** | Python | Yes | `force_list` parameter |
| **xml2js** | Node.js | Yes | `explicitArray` option |
| **lxml** | Python | Yes | Manual handling |
| **DataWeave** | MuleSoft | Yes | Defensive `map()` patterns |

**UTL-X matches industry best practices** by:
1. Using context-dependent parsing (most intuitive default)
2. Supporting `arrayHints` for known repeated elements
3. Providing `isArray()` for defensive coding

### Comparison with Alternatives

#### DataWeave (MuleSoft)

DataWeave has the **exact same issue**:

```dataweave
%dw 2.0
output application/json
---
{
  // DataWeave also requires defensive patterns
  modules: payload.Permissions.*UserPermissions
            filter ($.@userId == "U001")
            map ($.Access.*Module) // Uses *Module to handle both cases
}
```

DataWeave's solution: Use `.*` selector which returns arrays, but this isn't always intuitive.

#### XMLPath/XQuery

XPath naturally returns **sequences** (similar to arrays), but:
- ❌ Much steeper learning curve
- ❌ Doesn't integrate well with JSON/YAML output
- ❌ Requires XPath/XQuery expertise

#### JSON Schema Validation

- ❌ Requires schemas (often don't exist)
- ❌ Only validates, doesn't solve transformation
- ❌ Adds complexity and runtime overhead

### Why Defensive Coding Wins

1. **✅ Simple**: Just wrap in `if (isArray(...))` when needed
2. **✅ Explicit**: Code clearly shows where cardinality matters
3. **✅ Flexible**: Works with any XML structure
4. **✅ No overhead**: Only added where actually needed
5. **✅ Standard pattern**: Familiar to XML/JSON developers
6. **✅ Self-documenting**: Makes cardinality handling visible in code

---

## Best Practices Summary

### DO: Use Defensive Coding for XML → JSON/YAML

```utlx
// ✅ Good: Defensive array handling
Modules: ensureArray($input.Access.Module) |> map(...)
```

### DO: Create Helper Functions

```utlx
// ✅ Good: Reusable helper
def ensureArray(value) {
  if (isArray(value)) value else [value]
}
```

### DO: Document Assumptions

```utlx
// ✅ Good: Clear documentation
// Module might be single or array depending on permissions
Modules: ensureArray(perms.Access.Module) |> map(...)
```

### DON'T: Assume Cardinality

```utlx
// ❌ Bad: Assumes Module is always an array
Modules: $input.Access.Module |> map(...)  // Breaks for single Module
```

### DON'T: Over-Engineer

```utlx
// ❌ Bad: Unnecessary complexity for XML → XML
// XML serializer handles this automatically
Modules: ensureArray($input.Access.Module)  // Not needed for XML output!
```

---

## Technical Details: Parser Implementation

For those interested in the implementation details:

### Current Parser Logic

From `formats/xml/src/main/kotlin/org/apache/utlx/formats/xml/xml_parser.kt`:

```kotlin
// Line 211-224
val grouped = children.groupBy { it.first }
grouped.forEach { (childName, childElements) ->
    // Check if this element name appears multiple times OR is in arrayHints
    val shouldBeArray = childElements.size > 1 || arrayHints.contains(childName)

    properties[childName] = if (shouldBeArray) {
        // Multiple elements with same name OR in arrayHints → array
        UDM.Array(childElements.map { it.second })
    } else {
        // Single element, not in arrayHints → unwrapped
        childElements[0].second
    }
}
```

### Using arrayHints (Advanced)

If you have a known schema and want specific elements to always be arrays:

```kotlin
// Kotlin API
val xml = """<Access><Module>Single</Module></Access>"""
val parsed = XMLParser(xml, arrayHints = setOf("Module")).parse()
// Module will be array even with single element
```

```utlx
// In UTL-X, arrayHints isn't exposed yet
// Use defensive coding patterns instead
```

---

## Conclusion

The XML-to-JSON cardinality problem is:
- ✅ **Expected behavior** - not a bug
- ✅ **Industry-wide issue** - affects all XML-to-JSON converters
- ✅ **Best solved** with defensive coding patterns
- ✅ **Only affects** XML/CSV → JSON/YAML transformations
- ✅ **Not needed** for XML → XML transformations

**Use the `ensureArray()` pattern when:**
- Iterating over XML elements that might be single or multiple
- Using `map()`, `filter()`, or other array operations
- Cardinality is unknown or variable

**Related Documentation:**
- [XML Format Guide](xml.md)
- [JSON Format Guide](json.md)
- [Multi-Input Transformations](../language-guide/multiple-inputs-outputs.md)
- [Array Functions](../stdlib/array-functions.md)

---

**Last Updated:** 2025-10-24
**Status:** Current behavior is correct and follows industry standards
