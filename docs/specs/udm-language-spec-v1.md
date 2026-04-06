# UDM Language Specification v1.0

## Status: DRAFT - Implementation In Progress

**Date**: 2025-11-12
**Version**: 1.0
**Authors**: UTL-X Team

---

## 1. Introduction

### 1.1 Purpose

UDM Language (`.udm` file format) is a meta-format for representing Universal Data Model (UDM) structures with complete fidelity. Unlike standard serialization formats (YAML/JSON/XML), UDM Language preserves:

- Type information (Scalar types, DateTime, Date, Binary, Lambda)
- Metadata (source info, line numbers, validation state)
- Attributes (XML attributes, hints)
- Element names (XML element context)
- Internal state for perfect round-trip capability

### 1.2 Motivation

**Problem**: Standard formats lose UDM metadata

```yaml
# Standard YAML (transformation output)
name: Alice
age: 30
```

**Lost information**:
- Is this from XML `<Customer>` or JSON `{customer: ...}`?
- Is `age` a `Scalar<Number>` or just a string?
- What metadata was attached during parsing?

**Solution**: UDM Language preserves everything

```udm
@Object(
  name: "Customer",
  metadata: {source: "xml", lineNumber: "42"}
) {
  properties: {
    name: "Alice",
    age: 30
  }
}
```

---

## 2. Design Principles

1. **Complete Fidelity**: Round-trip must be perfect (UDM → `.udm` → UDM)
2. **Human Readable**: Developers can read and edit `.udm` files
3. **Format Independent**: UDM Language is NOT YAML/JSON/XML
4. **Tool Friendly**: Easy to parse, easy to generate
5. **Extensible**: Can evolve as UDM evolves

---

## 3. Syntax

### 3.1 File Structure

```udm
@udm-version: 1.0
@source: "optional-source-info"
@parsed-at: "optional-timestamp"

<udm-value>
```

### 3.2 Type System

#### Scalar Values

```udm
# Explicit form
@Scalar<String>("Hello")
@Scalar<Number>(42)
@Scalar<Boolean>(true)
@Scalar<Null>(null)

# Shorthand (type inferred)
"Hello"         # → @Scalar<String>
42              # → @Scalar<Number>
3.14            # → @Scalar<Number>
true            # → @Scalar<Boolean>
false           # → @Scalar<Boolean>
null            # → @Scalar<Null>
```

#### Arrays

```udm
# Explicit form
@Array [
  "item1",
  "item2",
  42
]

# Shorthand
[
  "item1",
  "item2",
  42
]
```

#### Objects

**Simple Object** (no metadata/attributes):

```udm
# Shorthand
{
  name: "Alice",
  age: 30,
  active: true
}
```

**Object with Metadata**:

```udm
@Object(
  name: "Customer",
  metadata: {source: "xml", lineNumber: "42"}
) {
  properties: {
    name: "Alice",
    age: 30
  }
}
```

**Object with Attributes**:

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

**Object with Everything**:

```udm
@Object(
  name: "Order",
  metadata: {source: "xml", validated: "true"}
) {
  attributes: {
    id: "ORD-001",
    status: "confirmed"
  },
  properties: {
    customer: "Alice",
    total: 1299.99
  }
}
```

#### Date/Time Types

```udm
@DateTime("2024-01-15T10:30:00Z")       # ISO 8601 instant
@Date("2024-01-15")                      # ISO date
@LocalDateTime("2024-01-15T10:30:00")   # ISO local datetime
@Time("10:30:00")                        # ISO time
```

#### Binary Data

```udm
# With size only
@Binary(size: 1024)

# With metadata
@Binary(size: 1024, encoding: "base64", ref: "file:///data.bin")

# Inline base64
@Binary("SGVsbG8gV29ybGQ=")
```

#### Lambda Functions

```udm
@Lambda()                              # Simple
@Lambda(id: "filter-fn-123", arity: 2) # With metadata
```

---

## 4. Grammar

### 4.1 EBNF Grammar

```ebnf
udm_file       = udm_header, udm_value ;
udm_header     = version_marker, { meta_entry } ;
version_marker = "@udm-version:", version_number, newline ;
meta_entry     = ("@source:" | "@parsed-at:"), string, newline ;

udm_value      = scalar | array | object | datetime | date
               | local_datetime | time | binary | lambda ;

scalar         = explicit_scalar | shorthand_scalar ;
explicit_scalar = "@Scalar<", type_name, ">(", value, ")" ;
shorthand_scalar = string | number | boolean | null ;

array          = "@Array", "[", [ udm_value, { ",", udm_value } ], "]"
               | "[", [ udm_value, { ",", udm_value } ], "]" ;

object         = explicit_object | shorthand_object ;
explicit_object = "@Object", [ object_meta ], "{", object_body, "}" ;
shorthand_object = "{", properties, "}" ;

object_meta    = "(", object_meta_entry, { ",", object_meta_entry }, ")" ;
object_meta_entry = "name:", string
                  | "metadata:", metadata_map ;

object_body    = [ attributes_section ], properties_section ;
attributes_section = "attributes:", "{", key_value_pairs, "}", [","] ;
properties_section = "properties:", "{", key_value_pairs, "}" ;

key_value_pairs = [ key_value_pair, { ",", key_value_pair } ] ;
key_value_pair  = (identifier | string), ":", udm_value ;

datetime       = "@DateTime(", string, ")" ;
date           = "@Date(", string, ")" ;
local_datetime = "@LocalDateTime(", string, ")" ;
time           = "@Time(", string, ")" ;
binary         = "@Binary(", binary_meta, ")" ;
lambda         = "@Lambda(", [ lambda_meta ], ")" ;
```

### 4.2 Lexical Rules

```ebnf
string         = '"', { string_char }, '"' ;
string_char    = any character except '"', '\', newline
               | '\', escape_char ;
escape_char    = '"' | '\' | 'n' | 'r' | 't' ;

number         = [ "-" ], digits, [ ".", digits ], [ exponent ] ;
digits         = digit, { digit } ;
exponent       = ("e" | "E"), [ "+" | "-" ], digits ;

boolean        = "true" | "false" ;
null           = "null" ;

identifier     = letter, { letter | digit | "_" } ;
version_number = digits, ".", digits ;

comment        = "#", { any character except newline }, newline
               | "/*", { any character }, "*/" ;

whitespace     = " " | "\t" | "\r" | "\n" ;
```

---

## 5. Semantics

### 5.1 Type Mappings

| UDM Type | UDM Language Syntax | Notes |
|----------|---------------------|-------|
| `UDM.Scalar(null)` | `null` | Shorthand |
| `UDM.Scalar("text")` | `"text"` | Shorthand |
| `UDM.Scalar(42)` | `42` | Shorthand |
| `UDM.Scalar(true)` | `true` | Shorthand |
| `UDM.Array([...])` | `[...]` | Shorthand or `@Array [...]` |
| `UDM.Object(...)` | `{...}` or `@Object {...}` | Depends on metadata |
| `UDM.DateTime(instant)` | `@DateTime("ISO-8601")` | Explicit |
| `UDM.Date(date)` | `@Date("ISO-date")` | Explicit |
| `UDM.LocalDateTime(dt)` | `@LocalDateTime("ISO")` | Explicit |
| `UDM.Time(time)` | `@Time("ISO-time")` | Explicit |
| `UDM.Binary(data)` | `@Binary(...)` | Explicit |
| `UDM.Lambda(fn)` | `@Lambda()` | Explicit |

### 5.2 Metadata Preservation

**Object Metadata**:
- `name`: Element name (for XML context)
- `metadata`: Internal metadata map (source, line numbers, etc.)
- `attributes`: XML attributes or hints
- `properties`: Actual data properties

**Round-Trip Guarantee**:
```kotlin
val original: UDM.Object = ...
val serialized: String = original.toUDMLanguage()
val parsed: UDM.Object = UDMLanguageParser.parse(serialized)

assert(original == parsed)  // Must be true
```

### 5.3 Escaping Rules

**String Escaping**:
- `\"` → Quotation mark
- `\\` → Backslash
- `\n` → Newline
- `\r` → Carriage return
- `\t` → Tab

**Key Escaping**:
- Keys with special characters must be quoted: `"my-key": value`
- Keys starting with `@` must be quoted: `"@special": value`
- Alphanumeric keys can be unquoted: `myKey: value`

---

## 6. Examples

### 6.1 Simple Scalar

```udm
@udm-version: 1.0

"Hello, World!"
```

### 6.2 Simple Array

```udm
@udm-version: 1.0

[
  "item1",
  "item2",
  42,
  true
]
```

### 6.3 Simple Object

```udm
@udm-version: 1.0

{
  name: "Alice",
  age: 30,
  active: true
}
```

### 6.4 Nested Structure

```udm
@udm-version: 1.0

{
  customer: {
    name: "Alice",
    email: "alice@example.com"
  },
  orders: [
    {
      id: "ORD-001",
      total: 100.0
    },
    {
      id: "ORD-002",
      total: 200.0
    }
  ]
}
```

### 6.5 Object with Metadata (XML Context)

```udm
@udm-version: 1.0
@source: "order.xml"

@Object(
  name: "Order",
  metadata: {source: "xml", lineNumber: "10"}
) {
  attributes: {
    id: "ORD-001",
    status: "confirmed"
  },
  properties: {
    customer: "Alice",
    total: 1299.99,
    orderDate: @DateTime("2024-01-15T10:30:00Z")
  }
}
```

### 6.6 Complex Real-World Example

```udm
@udm-version: 1.0
@source: "complex-order.xml"
@parsed-at: "2024-01-15T10:30:00Z"

@Object(
  name: "Order",
  metadata: {
    source: "xml",
    lineNumber: "10",
    validated: "true",
    schema: "order-v2.xsd"
  }
) {
  attributes: {
    id: "ORD-001",
    status: "confirmed",
    priority: "high"
  },
  properties: {
    customer: @Object(
      name: "Customer"
    ) {
      properties: {
        id: "CUST-789",
        name: "Alice Johnson",
        email: "alice@example.com",
        registeredDate: @Date("2023-06-15")
      }
    },
    items: [
      @Object(
        name: "Item"
      ) {
        attributes: {
          sku: "LAPTOP-X1"
        },
        properties: {
          name: "Laptop X1 Pro",
          price: 1299.99,
          quantity: 1,
          inStock: true
        }
      },
      @Object(
        name: "Item"
      ) {
        attributes: {
          sku: "MOUSE-M2"
        },
        properties: {
          name: "Wireless Mouse M2",
          price: 49.99,
          quantity: 2,
          inStock: true
        }
      }
    ],
    total: 1399.97,
    tax: 112.00,
    grandTotal: 1511.97,
    orderDate: @DateTime("2024-01-15T10:30:00Z"),
    shippingAddress: {
      street: "123 Main St",
      city: "San Francisco",
      state: "CA",
      zip: "94102"
    }
  }
}
```

---

## 7. Implementation Status

### 7.1 Completed

✅ **UDMLanguageSerializer** - Serializes UDM to `.udm` format
- All UDM types supported
- Pretty-print with indentation
- Metadata and attributes preserved
- Extension function: `udm.toUDMLanguage()`

✅ **Test Suite** - Comprehensive tests for serializer
- 18 test cases covering all types
- Edge cases (empty arrays, special characters, etc.)
- Complex nested structures

✅ **ANTLR4 Grammar** - Parser grammar defined
- Complete grammar for all UDM types
- Comments and whitespace handling
- Shorthand and explicit forms

### 7.2 In Progress

🚧 **UDMLanguageParser** - Parses `.udm` to UDM
- ANTLR4 parser generation
- Visitor pattern implementation
- Error handling and diagnostics

### 7.3 Planned

⏳ **CLI Commands**
- `utlx udm export` - Convert input to `.udm`
- `utlx udm import` - Load `.udm` file
- `utlx udm validate` - Validate syntax
- `utlx udm format` - Pretty-print

⏳ **LSP Integration**
- Syntax highlighting for `.udm` files
- Error diagnostics
- Hover information

⏳ **Tooling**
- VS Code extension
- Visual tree viewer
- Diff tool

---

## 8. Usage

### 8.1 Serialization (Available Now)

```kotlin
import org.apache.utlx.core.udm.*

// Create UDM structure
val udm = UDM.Object(
    name = "Customer",
    metadata = mapOf("source" to "xml"),
    attributes = mapOf("id" to "CUST-789"),
    properties = mapOf(
        "name" to UDM.Scalar("Alice"),
        "age" to UDM.Scalar(30)
    )
)

// Serialize to UDM Language
val udmLang = udm.toUDMLanguage()

// Save to file
File("output.udm").writeText(udmLang)

// With source info
val udmLang2 = udm.toUDMLanguage(
    sourceInfo = mapOf(
        "source" to "customer.xml",
        "parsed-at" to "2024-01-15T10:30:00Z"
    )
)
```

### 8.2 Parsing (Coming Soon)

```kotlin
// Load from file
val udmLang = File("input.udm").readText()

// Parse to UDM
val udm = UDMLanguageParser.parse(udmLang)

// Use in transformation
val result = transform(udm)
```

---

## 9. Migration from Standard Formats

### 9.1 From YAML

**Before** (YAML - loses metadata):
```yaml
name: Alice
age: 30
```

**After** (UDM Language - preserves metadata):
```udm
@udm-version: 1.0

{
  name: "Alice",
  age: 30
}
```

### 9.2 From JSON with Type Info

**Before** (JSON - implicit types):
```json
{
  "name": "Alice",
  "age": 30,
  "registered": "2024-01-15"
}
```

**After** (UDM Language - explicit types):
```udm
@udm-version: 1.0

{
  name: "Alice",
  age: 30,
  registered: @Date("2024-01-15")
}
```

---

## 10. Design Decisions

### 10.1 Why Not YAML?

**Problems with YAML**:
- Implicit typing (everything is string unless quoted/formatted)
- No built-in support for DateTime, Binary, Lambda
- Metadata requires awkward workarounds
- Round-trip loses type information

### 10.2 Why Not JSON?

**Problems with JSON**:
- Limited type system (string, number, boolean, null, array, object)
- No Date/Time types
- No Binary type
- No comments
- Verbose for metadata

### 10.3 Why Custom Format?

**Advantages**:
- Explicit type system matching UDM
- Metadata as first-class construct
- Human-readable with shorthand syntax
- Perfect round-trip guarantee
- Comment support
- Tool-friendly grammar

---

## 11. Future Extensions

### 11.1 Schema Definitions

```udm
@schema Customer {
  @required id: String
  @required name: String
  @optional email: String
  @optional orders: Array<Order>
}
```

### 11.2 References

```udm
@ref customer1: @Object {
  properties: {
    id: "CUST-789",
    name: "Alice"
  }
}

@Object {
  properties: {
    customer: @ref(customer1)
  }
}
```

### 11.3 Binary Format

For large UDMs, compressed binary format:

```
order.udm      # Text format (10 MB)
order.udmb     # Binary format (1.5 MB)
```

---

## 12. Conformance

An implementation conforms to this specification if:

1. **Serialization**: Produces valid UDM Language output for all UDM types
2. **Parsing**: Parses valid UDM Language input to equivalent UDM structure
3. **Round-trip**: `parse(serialize(udm)) == udm` for all UDM values
4. **Metadata**: Preserves all metadata, attributes, and element names
5. **Error Handling**: Provides clear error messages with line numbers

---

## 13. References

- **UDM Core**: `/modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt`
- **Architecture Doc**: `/docs/architecture/udm-as-a-language.md`
- **Serializer**: `/modules/core/src/main/kotlin/org/apache/utlx/core/udm/UDMLanguageSerializer.kt`
- **Grammar**: `/modules/core/src/main/antlr4/org/apache/utlx/core/udm/UDMLang.g4`
- **Examples**: `/docs/examples/udm-language-example.udm`

---

## Appendix A: Complete ANTLR4 Grammar

See `/modules/core/src/main/antlr4/org/apache/utlx/core/udm/UDMLang.g4`

---

## Appendix B: Implementation Notes

### B.1 Performance Considerations

- **Parse Time**: Target < 50% of original format parsing
- **Serialize Time**: Target < 10ms for typical structures
- **File Size**: Acceptable 2-3x larger than YAML for metadata
- **Memory**: Store metadata efficiently (don't duplicate strings)

### B.2 Error Messages

Provide helpful error messages:

```
Error at line 15, column 22:
  Expected ':' after property name

  14 |   properties: {
  15 |     customer "Alice",
                   ^
                   Expected ':'
```

---

**END OF SPECIFICATION**
