# UDM Parsing in IDE: Path Discrepancy Analysis

**Date:** 2024-11-15
**Author:** Architecture Investigation
**Status:** Issue Identified - Fix Required

## Executive Summary

There is a critical discrepancy between how paths work in `.utlx` files (CLI) versus the IDE's tree view. The root cause is that the TypeScript UDM parser in the Theia IDE extension treats `attributes:` and `properties:` as data fields, when they are actually **structural metadata** in the UDM language format.

**Impact:**
- CLI path: `$input.providers.address.street` ✅ Correct
- IDE path: `$input.properties.providers.properties.address.properties.street` ❌ Wrong

## Investigation Results

### 1. Understanding the UDM Language Spec

#### What are `attributes:` and `properties:`?

From the UDM Language Specification (`/docs/specs/udm-language-spec-v1.md`):

**Simple Object (Shorthand - No metadata):**
```udm
{
  name: "Alice",
  age: 30,
  active: true
}
```

**Object with Attributes (Full Format - Has metadata):**
```udm
@Object {
  attributes: {
    id: "CUST-789",
    status: "active"
  },
  properties: {
    name: "Alice",
    age: 30
  }
}
```

**Key Rule from UDMLanguageSerializer.kt (lines 130-143):**
```kotlin
// Properties section
if (obj.attributes.isNotEmpty() || needsAnnotation) {
    // Need explicit properties label
    if (prettyPrint) sb.append(indent(depth + 1))
    sb.append("properties: {")
    // ...
} else {
    // Shorthand: properties directly in object (NO "properties:" label)
    serializeProperties(obj.properties, sb, depth + 1)
}
```

**Conclusion:** `properties:` and `attributes:` are **syntactic constructs** in the .udm file format, NOT data fields in the original JSON/XML.

### 2. The UDM Object Model

From `modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt`:

```kotlin
data class Object(
    val properties: Map<String, UDM>,              // Actual data fields
    val attributes: Map<String, String> = emptyMap(), // XML attributes
    val name: String? = null,                      // Element name
    val metadata: Map<String, String> = emptyMap() // Internal metadata
) : UDM() {
    fun get(key: String): UDM? = properties[key]  // ← Direct access to properties!
}
```

**Critical Point:** The `get()` method directly accesses the `properties` map without requiring "properties" in the path.

### 3. How Path Resolution Works in CLI

From `modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt` (lines 439-498):

```kotlin
private fun evaluateMemberAccess(expr: Expression.MemberAccess, env: Environment): RuntimeValue {
    val target = evaluate(expr.target, env)

    return when (target) {
        is RuntimeValue.UDMValue -> {
            when (val udm = target.udm) {
                is UDM.Object -> {
                    if (expr.isAttribute) {
                        // Access attributes with @
                        val attrValue = udm.getAttribute(expr.property)
                        // ...
                    } else {
                        // Normal property access - directly calls udm.get()
                        val propValue = udm.get(expr.property)  // ← No "properties" in path!
                        // ...
                    }
                }
            }
        }
    }
}
```

**Path Resolution in CLI:**
- `$input.providers` → calls `udm.get("providers")` → accesses `properties["providers"]`
- `$input.@id` → calls `udm.getAttribute("id")` → accesses `attributes["id"]`
- **NO "properties" or "attributes" keywords in the path!**

### 4. The Problem in TypeScript Parser

Location: `theia-extension/utlx-theia-extension/src/browser/function-builder/udm-parser.ts`

**Current Behavior (INCORRECT):**

```typescript
function parseFieldValue(name: string, value: string): UdmField | null {
    if (value.startsWith('@Object')) {
        // Parses the object body
        const objectBody = extractBracedContent(value, bodyStartIndex);

        // Calls parseFieldsFromContent on entire body
        const allFields = parseFieldsFromContent(objectBody);

        // This INCLUDES "attributes" and "properties" as field nodes!
        return { name, type: 'object', fields: allFields };
    }
}
```

**What's happening:**
1. Parser encounters: `@Object { attributes: {...}, properties: {...} }`
2. Calls `parseFieldsFromContent()` on the entire object body
3. Finds two "fields": `attributes` and `properties`
4. Creates expandable field nodes for both
5. Tree displays: `$input.properties.providers.properties.address`

**What SHOULD happen:**
1. Parser encounters: `@Object { attributes: {...}, properties: {...} }`
2. Recognizes `attributes:` and `properties:` as **metadata sections**, not data fields
3. Extracts only the contents from inside `properties: {...}`
4. Tree displays: `$input.providers.address`

## Answers to Key Questions

### Q1: Is the Kotlin serialization wrong?

**NO** - The Kotlin serialization is correct. It follows the UDM Language Specification:
- Objects with attributes or metadata MUST use the full format with `properties:` label
- Objects without attributes CAN use shorthand (no `properties:` label)
- This is by design and documented in the spec

**Reference:** `/docs/specs/udm-language-spec-v1.md` lines 116-154

### Q2: Is the UDM concept wrong?

**NO** - The UDM concept is correct. The separation between:
- `properties` (data fields)
- `attributes` (XML attributes or metadata)
- `metadata` (system metadata)

This is a clean data model that supports multiple input formats (JSON, XML, CSV, etc.)

**Reference:** `modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt` lines 58-79

### Q3: Is the IDE TypeScript parser wrong?

**YES** - This is the root cause. The TypeScript parser treats `attributes:` and `properties:` as data fields instead of structural metadata.

**Location:** `theia-extension/utlx-theia-extension/src/browser/function-builder/udm-parser.ts`

## Physical Path vs. Logical Path

### Physical Path (in .udm file format)

```udm
@Object {
  metadata: {
    __schemaType: "json-object"
  },
  attributes: {
    id: "123"
  },
  properties: {
    providers: {
      address: {
        street: "123 Main St"
      }
    }
  }
}
```

### Logical Path (in .utlx code and IDE tree)

**Should be:**
```
$input.providers.address.street
$input.@id
```

**Currently in IDE (WRONG):**
```
$input.metadata.properties.providers.properties.address.properties.street
$input.attributes.id
```

## Example Comparison

### Input UDM
```udm
@Object(name: "root") {
  attributes: {
    version: "1.0"
  },
  properties: {
    customer: @Object {
      properties: {
        name: "Alice",
        age: 30
      }
    }
  }
}
```

### Current IDE Parse (WRONG)

```
Tree View:
└─ attributes (object)
   └─ version (string)
└─ properties (object)
   └─ customer (object)
      └─ properties (object)
         └─ name (string)
         └─ age (number)

Paths:
$input.attributes.version          ❌
$input.properties.customer.properties.name  ❌
```

### Correct IDE Parse (SHOULD BE)

```
Tree View:
└─ @version (string)
└─ customer (object)
   └─ name (string)
   └─ age (number)

Paths:
$input.@version        ✅ (matches CLI)
$input.customer.name   ✅ (matches CLI)
```

## Required Fix

### Location
`theia-extension/utlx-theia-extension/src/browser/function-builder/udm-parser.ts`

### Changes Needed

1. **Modify `parseFieldValue()` for `@Object` annotations:**
   ```typescript
   if (value.startsWith('@Object')) {
       const objectBody = extractBracedContent(value, bodyStartIndex);

       // NEW: Check if body has properties/attributes sections
       const hasPropertiesSection = objectBody.includes('properties:');
       const hasAttributesSection = objectBody.includes('attributes:');

       if (hasPropertiesSection || hasAttributesSection) {
           const fields: UdmField[] = [];

           // Extract from properties: {...} section
           if (hasPropertiesSection) {
               const propsContent = extractPropertiesSection(objectBody);
               fields.push(...parseFieldsFromContent(propsContent));
           }

           // Extract from attributes: {...} section with @ prefix
           if (hasAttributesSection) {
               const attrsContent = extractAttributesSection(objectBody);
               const attrFields = parseFieldsFromContent(attrsContent);
               // Prefix attribute names with @
               attrFields.forEach(f => f.name = '@' + f.name);
               fields.push(...attrFields);
           }

           return { name, type: 'object', fields };
       } else {
           // Shorthand format - parse all fields directly
           return { name, type: 'object', fields: parseFieldsFromContent(objectBody) };
       }
   }
   ```

2. **Add helper functions:**
   ```typescript
   function extractPropertiesSection(objectBody: string): string {
       const propsIndex = objectBody.indexOf('properties:');
       const propsOpenBrace = objectBody.indexOf('{', propsIndex);
       return extractBracedContent(objectBody, propsOpenBrace);
   }

   function extractAttributesSection(objectBody: string): string {
       const attrsIndex = objectBody.indexOf('attributes:');
       const attrsOpenBrace = objectBody.indexOf('{', attrsIndex);
       return extractBracedContent(objectBody, attrsOpenBrace);
   }
   ```

3. **Handle metadata section:**
   - `metadata:` should also be skipped (not shown in tree)
   - It's internal system metadata, not user data

### Expected Behavior After Fix

**Test Case 1: JSON Input**
```json
{
  "providers": [
    {
      "name": "Provider A",
      "address": {
        "street": "123 Main St"
      }
    }
  ]
}
```

**Tree should show:**
```
└─ providers (array)
   └─ name (string)
   └─ address (object)
      └─ street (string)
```

**Path:** `$input.providers[0].address.street`

---

**Test Case 2: XML Input**
```xml
<root id="123" version="1.0">
  <customer name="Alice" age="30">
    <address street="456 Oak Ave"/>
  </customer>
</root>
```

**Tree should show:**
```
└─ @id (string)
└─ @version (string)
└─ customer (object)
   └─ @name (string)
   └─ @age (string)
   └─ address (object)
      └─ @street (string)
```

**Paths:**
- `$input.@id`
- `$input.customer.@name`
- `$input.customer.address.@street`

## Related Code References

1. **UDM Language Spec:** `/docs/specs/udm-language-spec-v1.md` (lines 116-154)
2. **UDM Core Model:** `modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt` (lines 58-79)
3. **UDM Serializer:** `modules/core/src/main/kotlin/org/apache/utlx/core/udm/UDMLanguageSerializer.kt` (lines 130-143)
4. **UDM Parser:** `modules/core/src/main/kotlin/org/apache/utlx/core/udm/UDMLanguageParser.kt` (lines 289-307)
5. **CLI Interpreter:** `modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt` (lines 439-498)
6. **IDE TypeScript Parser:** `theia-extension/utlx-theia-extension/src/browser/function-builder/udm-parser.ts`

## Conclusion

The TypeScript UDM parser in the IDE needs to be updated to:
1. Treat `properties:`, `attributes:`, and `metadata:` as **structural metadata**, not data fields
2. Extract data fields ONLY from inside these sections
3. Never create tree nodes for the keywords themselves
4. Prefix attribute field names with `@` to match CLI syntax
5. Support both full format (with sections) and shorthand format (without sections)

This will align the IDE's tree view and path syntax with the CLI's behavior, providing a consistent user experience across both interfaces.
