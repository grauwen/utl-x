# USDL Syntax and Design Rationale

**Document Type:** Design Decision Record
**Version:** 1.0
**Status:** Approved
**Last Updated:** 2025-10-27
**Authors:** UTL-X Design Team

---

## Table of Contents

1. [Introduction](#introduction)
2. [Output Format Options Comparison](#output-format-options-comparison)
3. [The "%usdl 1.0" Declaration](#the-usdl-10-declaration)
4. [Explicit "%" Directives](#explicit--directives)
5. [Complete USDL 1.0 Directive Reference](#complete-usdl-10-directive-reference)
6. [Complete Examples](#complete-examples)
7. [Mode Detection Algorithm](#mode-detection-algorithm)
8. [Comparison Matrix](#comparison-matrix)
9. [Implementation Impact](#implementation-impact)
10. [Future Considerations](#future-considerations)
11. [Summary of Decisions](#summary-of-decisions)

---

## Introduction

### What is USDL?

**USDL (Universal Schema Definition Language)** is a format-agnostic schema definition language that enables schema transformations across multiple schema formats (XSD, JSON Schema, Protobuf, Avro, etc.) using a single transformation definition.

### The Problem

Organizations need to transform schemas between different formats:
- **API Migration**: Convert OpenAPI JSON Schema to XSD for legacy SOAP services
- **Data Modeling**: Enterprise architects model schemas in CSV spreadsheets, need to generate production XSD/JSON Schema
- **Schema Normalization**: Convert XSD from one design pattern to another (e.g., Russian Doll → Venetian Blind)
- **Multi-Platform**: Same logical schema needs to be expressed in multiple formats

**Current approaches require:**
- Learning format-specific syntax (XSD XML, JSON Schema JSON)
- Writing separate transformations for each output format
- Manual construction of boilerplate (namespaces, schema declarations, etc.)

### The Solution

USDL provides a **format-agnostic intermediate representation** for schemas. Users describe the semantic structure once, and UTL-X generates the appropriate format-specific output.

**Key principles:**
1. **Format abstraction**: Define schema semantics, not syntax
2. **Write once, output anywhere**: Same USDL transforms to XSD or JSON Schema
3. **Explicit and validated**: Clear directives with compile-time validation
4. **Consistent with UTL-X**: Follows `%utlx` convention

### Relationship to UTL-X

- **UTL-X**: Format-agnostic **data** transformation language
- **USDL**: Format-agnostic **schema** definition language
- **Together**: Transform data schemas just like you transform data

---

## Output Format Options Comparison

UTL-X provides **four distinct approaches** for schema generation:

| Syntax | Mode | Purpose | User Writes | Serializer Does |
|--------|------|---------|-------------|-----------------|
| `output xml` | Low-level | Manual XSD XML construction | `{"xs:schema": {...}}` | Convert to XML syntax |
| `output json` | Low-level | Manual JSON Schema construction | `{"$schema": "...", "type": "object"}` | Convert to JSON syntax |
| `output xsd %usdl 1.0` | High-level (USDL) | USDL → XSD generation | `{%types: {...}}` | Generate XSD structure from USDL |
| `output jsch %usdl 1.0` | High-level (USDL) | USDL → JSON Schema generation | `{%types: {...}}` | Generate JSON Schema from USDL |

### Why These Distinctions?

**`output xml` vs `output xsd`:**
- `xml` is generic - produces XML for any purpose
- `xsd` is schema-specific - produces XML Schema Definition
- Both produce XML, but `xsd` **implies** schema generation

**`output json` vs `output jsch`:**
- `json` is generic - produces JSON for any purpose
- `jsch` is schema-specific - produces JSON Schema
- Both produce JSON, but `jsch` **implies** JSON Schema

**Why we need both low-level and high-level:**
- **Low-level (`xml`, `json`)**: Full control, access to all format features, edge cases
- **High-level (`xsd %usdl`, `jsch %usdl`)**: Format abstraction, portability, less verbose

### Design Decision

✅ **Keep all four options:**
1. `output xml` - For manual XSD XML construction (or any XML)
2. `output json` - For manual JSON Schema construction (or any JSON)
3. `output xsd [%usdl 1.0]` - For XSD generation (USDL or auto-detect)
4. `output jsch [%usdl 1.0]` - For JSON Schema generation (USDL or auto-detect)

This provides flexibility while maintaining clarity of intent.

---

## The "%usdl 1.0" Declaration

### Syntax

```utlx
%utlx 1.0
input csv
output xsd %usdl 1.0
---
{%types: {...}}
```

### Why Explicit Declaration?

We evaluated three options:

#### Option A: Implicit (Auto-Detection Only)

```utlx
output xsd
---
{%types: {...}}  // Auto-detect USDL mode based on %types
```

**Pros:**
- ✅ Simpler, less boilerplate
- ✅ Less to type

**Cons:**
- ❌ "Magic" behavior - not obvious to reader what's happening
- ❌ Hard to version USDL separately from output format
- ❌ Tooling can't distinguish mode until parsing transformation body
- ❌ Error messages less specific ("expected %types" vs "USDL 1.0 requires %types")

#### Option B: Explicit Declaration Only (Strict)

```utlx
output xsd %usdl 1.0  // Required!
---
{%types: {...}}
```

**Pros:**
- ✅ Crystal clear intent from header
- ✅ Versioned - USDL can evolve independently
- ✅ Better error messages ("USDL 1.0 requires...")
- ✅ Tooling knows mode before parsing body
- ✅ Explicit is better than implicit

**Cons:**
- ❌ More verbose
- ❌ Breaking change if we require it

#### Option C: Hybrid (Support Both) - **RECOMMENDED**

```utlx
// Recommended: Explicit
output xsd %usdl 1.0
---
{%types: {...}}

// Also supported: Auto-detect
output xsd
---
{%types: {...}}  // Presence of %types triggers USDL mode
```

**Pros:**
- ✅ Best of both worlds
- ✅ Explicit when you want clarity (recommended)
- ✅ Auto-detect for convenience
- ✅ Backward compatible
- ✅ Can warn: "Recommend adding %usdl 1.0 for clarity"

**Cons:**
- ❌ Two ways to do the same thing

### Decision: Option C (Hybrid with Recommendation)

**Implementation:**
- `output xsd %usdl 1.0` - Explicit USDL mode **(recommended, clearer)**
- `output xsd` - Auto-detect mode (check for `%types` key, fallback to low-level)
- `output xml` - Always low-level (manual XSD XML construction)
- `output jsch %usdl 1.0` - Explicit USDL mode **(recommended)**
- `output jsch` - Auto-detect mode
- `output json` - Always low-level (manual JSON Schema JSON)

**Rationale:**
1. **Explicit is preferred**: Documentation and examples use `%usdl 1.0`
2. **Auto-detect for convenience**: Quick prototyping doesn't require boilerplate
3. **Linter can warn**: "Consider adding %usdl 1.0 for clarity"
4. **Versioning**: When USDL 2.0 arrives, explicit declaration is critical

---

## Explicit "%" Directives

### The Question

Should USDL keywords like `namespace`, `types`, `kind`, etc. use the `%` prefix?

### Three Options Evaluated

#### Option A: Plain Properties (Like Regular JSON)

```utlx
{
  namespace: "http://example.com",
  types: {
    Customer: {
      kind: "structure",
      documentation: "Customer info",
      fields: [
        {name: "id", type: "string", required: true}
      ]
    }
  }
}
```

**Pros:**
- ✅ Looks like standard JSON/object syntax
- ✅ Less verbose (no `%` everywhere)
- ✅ Familiar to users

**Cons:**
- ❌ **Not obvious which are USDL keywords** vs user-defined data
- ❌ **Naming collisions**: What if user wants a type named "namespace" or "types"?
- ❌ **Typos not caught**: Is `namepsace` a typo or user field?
- ❌ **Inconsistent**: We use `%utlx` and `%usdl`, why not `%namespace`?
- ❌ **Parser can't validate**: Can't distinguish unknown keyword from user data

**Example collision:**
```utlx
{
  types: {
    namespace: {  // User's type named "namespace"
      kind: "structure",
      fields: [...]
    }
  }
}
```
Is `namespace` a USDL directive or a type name? Ambiguous!

#### Option B: Explicit % Directives - **RECOMMENDED**

```utlx
{
  %namespace: "http://example.com",
  %types: {
    Customer: {
      %kind: "structure",
      %documentation: "Customer info",
      %fields: [
        {%name: "id", %type: "string", %required: true}
      ]
    }
  }
}
```

**Pros:**
- ✅ **Crystal clear**: `%xxx` = USDL keyword, not user data
- ✅ **No collisions**: User can freely use "namespace", "types", etc. as type names
- ✅ **Parser validates**: "Unknown USDL directive %namepsace. Did you mean %namespace?"
- ✅ **Consistent**: Matches `%utlx 1.0`, `%usdl 1.0` convention
- ✅ **Syntax highlighting**: IDEs can color `%` directives specially
- ✅ **Autocomplete friendly**: Type `%` and IDE shows USDL directives
- ✅ **Self-documenting**: Clear what's language vs data

**Cons:**
- ❌ More verbose (every directive has `%`)
- ❌ Looks less like standard JSON

**Example - No collision:**
```utlx
{
  %types: {
    namespace: {  // ✅ OK! This is a type name, not a directive
      %kind: "structure",
      %fields: [...]
    }
  }
}
```
Clear distinction!

#### Option C: Hybrid (% on Top-Level Only)

```utlx
{
  %namespace: "http://example.com",
  %types: {
    Customer: {
      kind: "structure",      // No % here
      documentation: "Customer info",
      fields: [
        {name: "id", type: "string"}
      ]
    }
  }
}
```

**Pros:**
- ✅ Less verbose than Option B
- ✅ Distinguishes top-level USDL keywords

**Cons:**
- ❌ **Inconsistent**: Why `%types` but not `%kind`?
- ❌ **Harder to explain**: "Use % for top-level, except..."
- ❌ **Partial collision prevention**: Still can't use "kind", "fields", etc.
- ❌ **Tooling confusion**: Different rules at different levels

### Decision: Option B (Full Explicit % Directives)

**All USDL keywords use `%` prefix.**

**Rationale:**

1. **Consistency is paramount**
   - We use `%utlx 1.0` for language version
   - We use `%usdl 1.0` for dialect
   - Natural to use `%namespace`, `%types`, `%kind`, etc.

2. **Semantic clarity**
   - `%` means "this is a directive/keyword"
   - No `%` means "this is user data"
   - Clear, simple rule

3. **No naming collisions**
   ```utlx
   %types: {
     types: {...},      // User's type named "types" - OK!
     namespace: {...},  // User's type named "namespace" - OK!
     kind: {...}        // User's type named "kind" - OK!
   }
   ```

4. **Superior error messages**
   ```
   Error at line 5: Unknown USDL directive '%namepsace'
   Did you mean '%namespace'?

   Valid USDL 1.0 directives:
   - %namespace, %version, %types
   - %kind, %documentation, %fields
   - %name, %type, %required
   ```

5. **Tooling benefits**
   - **Autocomplete**: Type `%` → IDE shows USDL directives
   - **Syntax highlighting**: `%` directives colored differently
   - **Validation**: IDE knows `%xxx` must be valid USDL directive
   - **Documentation**: Hover over `%kind` → shows USDL docs

6. **Future-proof**
   - Can add new directives without worrying about collisions
   - USDL 2.0 can add `%allOf`, `%anyOf`, etc. safely

**The verbosity concern is mitigated by:**
- Helper functions (`SimpleSchema()`) for common cases
- You write it once, it's read many times
- Explicitness aids understanding
- Modern editors autocomplete `%` directives

---

## Complete USDL 1.0 Directive Reference

### Top-Level Directives

| Directive | Type | Required | Description |
|-----------|------|----------|-------------|
| `%namespace` | String | No | Schema namespace (XSD targetNamespace / JSON Schema $id base) |
| `%version` | String | No | Schema version |
| `%elementFormDefault` | String | No | XSD-specific: "qualified" or "unqualified" (default: "qualified") |
| `%types` | Object | **Yes** | Type definitions (at least one type required) |

### Type Definition Directives

Used within `%types: { TypeName: {...} }`:

| Directive | Type | Required | Description |
|-----------|------|----------|-------------|
| `%kind` | String | **Yes** | Type kind: "structure", "enumeration", "primitive", "array", "union" |
| `%documentation` | String | No | Type-level documentation → xs:annotation/xs:documentation (XSD) or description (JSON Schema) |
| `%fields` | Array | Conditional | Array of field definitions (required for `%kind: "structure"`) |
| `%values` | Array | Conditional | Array of enumeration values (required for `%kind: "enumeration"`) |
| `%itemType` | String | Conditional | Element type (required for `%kind: "array"`) |
| `%baseType` | String | Conditional | Base type (required for `%kind: "primitive"`) |
| `%options` | Array | Conditional | Type references (required for `%kind: "union"`) |
| `%constraints` | Object | No | Type-level constraints |

### Field Definition Directives

Used within `%fields: [{...}]`:

| Directive | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `%name` | String | **Yes** | - | Field/element name |
| `%type` | String | **Yes** | - | Field type (primitive or type reference) |
| `%required` | Boolean | No | `false` | Is this field required? |
| `%description` | String | No | - | Field-level documentation |
| `%array` | Boolean | No | `false` | Is this field an array? |
| `%default` | Any | No | - | Default value for field |
| `%constraints` | Object | No | - | Field-level constraints |

### Constraint Directives

Used within `%constraints: {...}`:

| Directive | Type | Applies To | Description |
|-----------|------|------------|-------------|
| `%minLength` | Integer | String | Minimum string length |
| `%maxLength` | Integer | String | Maximum string length |
| `%pattern` | String | String | Regex pattern (XSD: xs:pattern, JSON Schema: pattern) |
| `%minimum` | Number | Numeric | Minimum value (inclusive) |
| `%maximum` | Number | Numeric | Maximum value (inclusive) |
| `%exclusiveMinimum` | Number | Numeric | Minimum value (exclusive) |
| `%exclusiveMaximum` | Number | Numeric | Maximum value (exclusive) |
| `%enum` | Array | Any | Allowed values (enumeration) |
| `%format` | String | String | Format hint: "email", "uri", "date", "date-time", etc. |

### Enumeration Value Directives

Values in `%values` can be:
- Simple: `["active", "inactive"]`
- With documentation: `[{%value: "active", %description: "Account is active"}]`

---

## Complete Examples

### Example 1: CSV to XSD using USDL (Explicit - Recommended)

**Input CSV** (enterprise architect's schema metadata):
```csv
fieldName,dataType,required,documentation
customerId,string,true,Unique customer identifier
email,string,true,Contact email address
age,integer,false,Customer age in years
```

**Transformation:**
```utlx
%utlx 1.0
input csv
output xsd %usdl 1.0
---
{
  %namespace: "http://example.com/customer",
  %version: "1.0",

  %types: {
    Customer: {
      %kind: "structure",
      %documentation: "Customer information from CRM system",

      %fields: map($input, field => {
        %name: field.fieldName,
        %type: field.dataType,
        %required: field.required == true,
        %description: field.documentation
      })
    }
  }
}
```

**Output XSD:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://example.com/customer"
           version="1.0"
           elementFormDefault="qualified">
  <xs:complexType name="Customer">
    <xs:annotation>
      <xs:documentation>Customer information from CRM system</xs:documentation>
    </xs:annotation>
    <xs:sequence>
      <xs:element name="customerId" type="xs:string">
        <xs:annotation><xs:documentation>Unique customer identifier</xs:documentation></xs:annotation>
      </xs:element>
      <xs:element name="email" type="xs:string">
        <xs:annotation><xs:documentation>Contact email address</xs:documentation></xs:annotation>
      </xs:element>
      <xs:element name="age" type="xs:integer" minOccurs="0">
        <xs:annotation><xs:documentation>Customer age in years</xs:documentation></xs:annotation>
      </xs:element>
    </xs:sequence>
  </xs:complexType>
</xs:schema>
```

---

### Example 2: CSV to XSD using Low-Level (Manual XML Construction)

**Same CSV input, manual approach:**

```utlx
%utlx 1.0
input csv
output xml
---
{
  "xs:schema": {
    "@xmlns:xs": "http://www.w3.org/2001/XMLSchema",
    "@targetNamespace": "http://example.com/customer",
    "@version": "1.0",
    "@elementFormDefault": "qualified",

    "xs:complexType": {
      "@name": "Customer",
      "_documentation": "Customer information from CRM system",

      "xs:sequence": {
        "xs:element": map($input, field => {
          "@name": field.fieldName,
          "@type": "xs:" + field.dataType,
          "@minOccurs": if (field.required == true) null else "0",
          "_documentation": field.documentation
        })
      }
    }
  }
}
```

**Comparison:**
- USDL: `%namespace`, `%types`, `%kind` → abstracted
- Low-level: `"xs:schema"`, `"xs:complexType"`, `"xs:sequence"` → manual XSD XML
- USDL: Portable (can change to `output jsch %usdl 1.0`)
- Low-level: XSD-specific, no format switching

---

### Example 3: Same Transformation, JSON Schema Output

**Just change line 3!**

```utlx
%utlx 1.0
input csv
output jsch %usdl 1.0  // ← Only change!
---
{
  %namespace: "http://example.com/customer",
  %version: "1.0",

  %types: {
    Customer: {
      %kind: "structure",
      %documentation: "Customer information from CRM system",

      %fields: map($input, field => {
        %name: field.fieldName,
        %type: field.dataType,
        %required: field.required == true,
        %description: field.documentation
      })
    }
  }
}
```

**Output JSON Schema:**
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "http://example.com/customer",
  "version": "1.0",
  "$defs": {
    "Customer": {
      "type": "object",
      "description": "Customer information from CRM system",
      "properties": {
        "customerId": {
          "type": "string",
          "description": "Unique customer identifier"
        },
        "email": {
          "type": "string",
          "description": "Contact email address"
        },
        "age": {
          "type": "integer",
          "description": "Customer age in years"
        }
      },
      "required": ["customerId", "email"]
    }
  }
}
```

**Same transformation, different output format!**

---

### Example 4: JSCH to XSD (Schema-to-Schema Transformation)

**Input** (OpenAPI JSON Schema):
```json
{
  "type": "object",
  "title": "Customer",
  "properties": {
    "customerId": {"type": "string"},
    "email": {"type": "string", "format": "email"},
    "age": {"type": "integer", "minimum": 0}
  },
  "required": ["customerId", "email"]
}
```

**Transformation:**
```utlx
%utlx 1.0
input jsch
output xsd %usdl 1.0
---
{
  %namespace: "http://soap.example.com/customer",

  %types: {
    [$input.title ?? "Root"]: {
      %kind: "structure",
      %documentation: $input.description,

      %fields: map(entries($input.properties), ([name, prop]) => {
        %name: name,
        %type: prop.type,
        %required: contains($input.required ?? [], name),
        %description: prop.description,

        %constraints: {
          ...if (prop.format != null) {%format: prop.format},
          ...if (prop.pattern != null) {%pattern: prop.pattern},
          ...if (prop.minimum != null) {%minimum: prop.minimum},
          ...if (prop.maximum != null) {%maximum: prop.maximum}
        }
      })
    }
  }
}
```

**Output XSD:**
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://soap.example.com/customer">
  <xs:complexType name="Customer">
    <xs:sequence>
      <xs:element name="customerId" type="xs:string"/>
      <xs:element name="email">
        <xs:simpleType>
          <xs:restriction base="xs:string">
            <xs:pattern value="[^@]+@[^@]+"/>
          </xs:restriction>
        </xs:simpleType>
      </xs:element>
      <xs:element name="age" minOccurs="0">
        <xs:simpleType>
          <xs:restriction base="xs:integer">
            <xs:minInclusive value="0"/>
          </xs:restriction>
        </xs:simpleType>
      </xs:element>
    </xs:sequence>
  </xs:complexType>
</xs:schema>
```

---

### Example 5: Auto-Detection (Without %usdl Declaration)

```utlx
%utlx 1.0
input csv
output xsd  // No %usdl - will auto-detect
---
{
  %types: {  // Presence of %types triggers USDL mode
    Customer: {
      %kind: "structure",
      %fields: map($input, f => {
        %name: f.fieldName,
        %type: f.dataType,
        %required: f.required == true
      })
    }
  }
}
```

**This works!** Parser detects `%types` and switches to USDL mode.

**However, we recommend explicit:**
```utlx
output xsd %usdl 1.0  // ← Explicit is clearer!
```

---

### Example 6: Enumeration Type

```utlx
%utlx 1.0
output xsd %usdl 1.0
---
{
  %namespace: "http://example.com/order",

  %types: {
    OrderStatus: {
      %kind: "enumeration",
      %documentation: "Possible order statuses",

      %values: [
        {%value: "pending", %description: "Order received, awaiting processing"},
        {%value: "processing", %description: "Order is being prepared"},
        {%value: "shipped", %description: "Order has been shipped"},
        {%value: "delivered", %description: "Order delivered to customer"}
      ]
    }
  }
}
```

**Output XSD:**
```xml
<xs:simpleType name="OrderStatus">
  <xs:annotation>
    <xs:documentation>Possible order statuses</xs:documentation>
  </xs:annotation>
  <xs:restriction base="xs:string">
    <xs:enumeration value="pending">
      <xs:annotation><xs:documentation>Order received, awaiting processing</xs:documentation></xs:annotation>
    </xs:enumeration>
    <xs:enumeration value="processing">
      <xs:annotation><xs:documentation>Order is being prepared</xs:documentation></xs:annotation>
    </xs:enumeration>
    <xs:enumeration value="shipped">
      <xs:annotation><xs:documentation>Order has been shipped</xs:documentation></xs:annotation>
    </xs:enumeration>
    <xs:enumeration value="delivered">
      <xs:annotation><xs:documentation>Order delivered to customer</xs:documentation></xs:annotation>
    </xs:enumeration>
  </xs:restriction>
</xs:simpleType>
```

---

## Mode Detection Algorithm

```kotlin
/**
 * Determine serialization mode based on output format, dialect, and UDM structure
 */
fun detectMode(format: String, dialect: Dialect?, udm: UDM): SerializationMode {
  // 1. Explicit dialect declaration takes precedence
  if (dialect?.name == "usdl") {
    return SerializationMode.USDL
  }

  // 2. Format-specific detection
  return when (format) {
    // xml/json are always low-level (manual construction)
    "xml", "json" -> SerializationMode.LOW_LEVEL

    // xsd/jsch support both modes - auto-detect if no dialect
    "xsd", "jsch" -> {
      if (udm is UDM.Object && udm.properties.containsKey("%types")) {
        SerializationMode.USDL  // Has %types = USDL mode
      } else {
        SerializationMode.LOW_LEVEL  // No %types = low-level
      }
    }

    // Unknown formats default to low-level
    else -> SerializationMode.LOW_LEVEL
  }
}
```

**Detection logic:**
1. **Explicit `%usdl 1.0`** → Always USDL mode
2. **Format `xml` or `json`** → Always low-level
3. **Format `xsd` or `jsch` with `%types`** → USDL mode (auto-detect)
4. **Format `xsd` or `jsch` without `%types`** → Low-level
5. **Everything else** → Low-level

---

## Comparison Matrix

| Feature | `output xml` | `output xsd %usdl 1.0` | `output xsd` (auto) | `output json` | `output jsch %usdl 1.0` |
|---------|-------------|----------------------|-------------------|---------------|----------------------|
| **Mode** | Low-level | USDL (explicit) | Auto-detect | Low-level | USDL (explicit) |
| **User writes** | XSD XML tags | USDL `%` directives | USDL or XSD XML | JSON Schema JSON | USDL `%` directives |
| **Schema abstraction** | None | Full | Full (if `%types`) | None | Full |
| **Format switching** | No | Yes (to `jsch`) | Yes (if USDL) | No | Yes (to `xsd`) |
| **Verbosity** | High | Medium | Varies | High | Medium |
| **Control** | Full | High | Varies | Full | High |
| **Pattern enforcement** | Manual | Automatic | Automatic (if USDL) | N/A | N/A |
| **Doc injection** | Manual (`_documentation`) | Automatic (`%documentation`) | Automatic (if USDL) | Manual | Automatic |
| **Boilerplate** | All manual | Auto-generated | Auto-generated (if USDL) | All manual | Auto-generated |
| **Type safety** | None (strings) | Validated directives | Validated (if USDL) | None | Validated directives |
| **When to use** | Edge cases, XSD-specific features | Standard schemas, portability | Quick prototyping | Edge cases | Standard schemas, portability |

---

## Implementation Impact

### Parser Changes

**1. Support `%identifier` as Object Property Key:**

Current: Only string keys allowed
```utlx
{
  "namespace": "http://example.com"  // String key
}
```

New: Also support `%identifier` keys
```utlx
{
  %namespace: "http://example.com"  // Directive key
}
```

**Parser modification:**
```kotlin
fun parseObjectProperty(): Pair<String, Expression> {
  val key = when {
    match(TokenType.STRING) -> previous().literal as String
    match(TokenType.PERCENT) -> {  // NEW: % directive
      val ident = consume(TokenType.IDENTIFIER, "Expected directive name after %")
      "%" + ident.lexeme
    }
    match(TokenType.IDENTIFIER) -> previous().lexeme
    else -> error("Expected property key")
  }

  consume(TokenType.COLON, "Expected ':'")
  val value = parseExpression()

  return key to value
}
```

**2. Parse `output FORMAT [%DIALECT VERSION]?` Syntax:**

Current:
```kotlin
fun parseOutputs(): FormatSpec {
  val format = consume(IDENTIFIER).lexeme
  val options = if (match(LBRACE)) parseFormatOptions() else emptyMap()
  return FormatSpec(format, options)
}
```

New:
```kotlin
fun parseOutputs(): FormatSpec {
  val format = consume(IDENTIFIER).lexeme

  // Check for %dialect version
  val dialect = if (match(PERCENT)) {
    val dialectName = consume(IDENTIFIER, "Expected dialect name").lexeme
    val version = consume(NUMBER, "Expected version number").literal as Double
    Dialect(dialectName, version.toString())
  } else null

  val options = if (match(LBRACE)) parseFormatOptions() else emptyMap()
  return FormatSpec(format, dialect, options)
}
```

### Serializer Changes

**1. Mode Detection:**
```kotlin
class XSDSerializer {
  fun serialize(udm: UDM, formatSpec: FormatSpec) {
    val mode = detectMode(formatSpec.format, formatSpec.dialect, udm)

    val xsdStructure = when (mode) {
      SerializationMode.USDL -> transformUSDL(udm)
      SerializationMode.LOW_LEVEL -> udm
    }

    // Continue with existing serialization...
  }
}
```

**2. USDL Transformation:**
```kotlin
private fun transformUSDL(udm: UDM.Object): UDM {
  // Extract %namespace, %types, etc.
  val namespace = (udm.properties["%namespace"] as? UDM.Scalar)?.value as? String
  val types = udm.properties["%types"] as? UDM.Object
    ?: throw IllegalArgumentException("USDL requires %types property")

  // Generate XSD structure from USDL directives
  return generateXSDFromUSDL(namespace, types)
}
```

### Validation

**Unknown Directive Detection:**
```kotlin
val VALID_USDL_DIRECTIVES = setOf(
  "%namespace", "%version", "%types",
  "%kind", "%documentation", "%fields", "%values",
  "%name", "%type", "%required", "%description"
  // ... etc
)

fun validateUSDL(udm: UDM.Object) {
  udm.properties.keys.filter { it.startsWith("%") }.forEach { key ->
    if (key !in VALID_USDL_DIRECTIVES) {
      val suggestion = findClosestMatch(key, VALID_USDL_DIRECTIVES)
      throw USDLValidationException(
        "Unknown USDL directive '$key'." +
        (suggestion?.let { " Did you mean '$it'?" } ?: "")
      )
    }
  }
}
```

**Error Messages:**

Before:
```
Error: Missing required property 'types'
```

After (with explicit %usdl):
```
Error: USDL 1.0 requires %types property
Expected structure:
  {
    %types: {
      TypeName: {%kind: "structure", ...}
    }
  }
```

After (with typo):
```
Error: Unknown USDL directive '%namepsace' at line 5
Did you mean '%namespace'?

Valid USDL 1.0 top-level directives:
  %namespace, %version, %types
```

### Documentation Updates

**1. Update all examples:**
- Replace `namespace:` with `%namespace:`
- Replace `types:` with `%types:`
- Replace `kind:` with `%kind:`
- etc.

**2. Add comparison guide:**
- When to use `output xml` vs `output xsd`
- When to use `%usdl 1.0` (recommended) vs auto-detect (convenience)
- Low-level vs high-level trade-offs

**3. Migration guide:**
- For existing code using implicit detection
- How to add `%` to directives
- Tool to auto-convert (if needed)

---

## Future Considerations

### USDL Versioning

**USDL 1.0** (Initial release):
- Basic types: `structure`, `enumeration`, `primitive`, `array`
- Core constraints: pattern, min/max, enum, format
- Supports XSD and JSON Schema output

**USDL 1.1** (Future):
- Add: `%kind: "union"` (oneOf/choice)
- Add: `%kind: "tuple"` (fixed-length arrays)
- Add: `%kind: "map"` (key-value pairs)
- Enhanced constraints: `%dependencies`, `%multipleOf`

**USDL 2.0** (Future):
- Schema composition: `%allOf`, `%anyOf`, `%not`
- Conditional schemas: `%if`, `%then`, `%else`
- External references: `%ref` to other schemas
- Protobuf-specific features

### Additional Output Formats

With USDL in place, adding new schema formats is straightforward:

```utlx
output proto %usdl 1.0   # Protocol Buffers
output avro %usdl 1.0    # Apache Avro
output graphql %usdl 1.0 # GraphQL Schema
output thrift %usdl 1.0  # Apache Thrift
```

Same USDL, different output!

### Conformance Tests to Create

**Low-level tests:**
1. CSV → XSD (using `output xml`, manual construction)
2. CSV → JSON Schema (using `output json`, manual construction)

**USDL tests:**
3. CSV → XSD (using `output xsd %usdl 1.0`)
4. CSV → JSON Schema (using `output jsch %usdl 1.0`)
5. Same USDL transformation → both XSD and JSCH (portability test)

**Schema-to-schema tests:**
6. JSCH → XSD (using USDL)
7. XSD → JSCH (using USDL)

**Pattern tests:**
8. XSD Venetian Blind pattern generation
9. XSD Russian Doll pattern generation

**Comparison tests:**
10. Identical output from low-level vs USDL (validation)

---

## Summary of Decisions

### ✅ Approved Decisions:

1. **Use explicit `%usdl 1.0` declaration**
   - Syntax: `output xsd %usdl 1.0`
   - Recommended but not required
   - Auto-detection supported for convenience

2. **Support auto-detection**
   - `output xsd` (without `%usdl`) checks for `%types`
   - Provides backward compatibility
   - Linter can warn to add explicit declaration

3. **Use `%` prefix for ALL USDL directives**
   - All keywords: `%namespace`, `%types`, `%kind`, `%fields`, etc.
   - Consistency with `%utlx`, `%usdl` convention
   - Prevents naming collisions
   - Enables validation and better errors

4. **Keep four distinct output modes:**
   - `output xml` - Low-level, manual XSD XML
   - `output json` - Low-level, manual JSON Schema JSON
   - `output xsd [%usdl 1.0]` - Schema-specific, USDL or low-level
   - `output jsch [%usdl 1.0]` - Schema-specific, USDL or low-level

5. **Full directive specification:**
   - Top-level: `%namespace`, `%version`, `%types`
   - Type-level: `%kind`, `%documentation`, `%fields`, `%values`, etc.
   - Field-level: `%name`, `%type`, `%required`, `%description`, `%constraints`
   - Constraint-level: `%pattern`, `%minimum`, `%maximum`, `%enum`, `%format`

6. **Mode detection algorithm:**
   - Explicit `%usdl 1.0` → Always USDL
   - Format `xml`/`json` → Always low-level
   - Format `xsd`/`jsch` → Auto-detect based on `%types`

7. **Implementation roadmap:**
   - Parser: Support `%identifier` keys and `%dialect` syntax
   - Serializer: Mode detection and USDL transformation
   - Validation: Unknown directive detection with suggestions
   - Documentation: Update all examples, add comparison guide

### 🎯 Benefits Achieved:

- **Format abstraction**: Write schema once, output to XSD or JSON Schema
- **Clarity**: `%` directives are obviously keywords, not data
- **Versioning**: USDL can evolve independently
- **Validation**: Parse-time detection of typos and errors
- **Tooling**: Autocomplete, syntax highlighting, inline docs
- **Consistency**: Aligns with `%utlx` convention
- **Future-proof**: Easy to add new formats and features

---

**Document Status:** Approved for implementation
**Next Steps:** Update Universal Schema DSL specification, implement parser and serializer changes, create conformance tests

---

## References

- [Universal Schema DSL Specification](../language-guide/universal-schema-dsl.md)
- [UTL-X Language Guide](../language-guide/quick-reference.md)
- [UDM Specification](../architecture/udm-specification.md)
- [XSD Design Patterns](https://www.w3.org/TR/xmlschema-0/)
- [JSON Schema 2020-12](https://json-schema.org/draft/2020-12/schema)
