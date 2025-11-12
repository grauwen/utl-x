# UDM as a Language: A Meta-Format for Universal Data Representation

## Date: 2025-11-12

## Conceptual Distinction

### The Critical Difference

There is a fundamental distinction between:

1. **Transformation Output** - Converting UDM to YAML/JSON/XML for end-user consumption
2. **UDM Serialization** - Representing the UDM structure itself as a format

```
┌─────────────────────────────────────────────────────────────┐
│                    TWO DIFFERENT CONCEPTS                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Concept A: UDM → Target Format (Transformation)            │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                               │
│  Input:  CSV with headers                                    │
│          ↓                                                    │
│  Parse:  UDM.Array(UDM.Object(properties: {...}))           │
│          ↓                                                    │
│  Output: YAML (normal YAML, no UDM metadata)                │
│                                                               │
│  name: Alice                                                 │
│  age: 30                                                     │
│                                                               │
│  Purpose: End-user consumable data                          │
│                                                               │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Concept B: UDM → UDM Language (Meta-Format)                │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                               │
│  Input:  UDM structure in memory                            │
│          ↓                                                    │
│  Export: UDM Language (.udm file)                           │
│                                                               │
│  @Object(name="Customer", metadata={source: "csv"}) {       │
│    properties: {                                             │
│      name: @Scalar(type="String", value="Alice"),           │
│      age: @Scalar(type="Number", value=30)                  │
│    },                                                        │
│    attributes: {customerId: "CUST-789"}                     │
│  }                                                           │
│                                                               │
│  Purpose: Preserve complete UDM MODEL with all metadata     │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### Why This Matters

**Transformation YAML** (Concept A):
- Data representation for end users
- No type information
- No UDM metadata
- No element names preserved
- **NOT** round-trippable to exact UDM

**UDM Language YAML** (Concept B):
- Model representation for tools/debugging
- Full type annotations
- All UDM metadata preserved
- Element names, attributes, internal state preserved
- **Fully** round-trippable to exact UDM

---

## The UDM Model Context

### What Makes UDM Special

UDM (Universal Data Model) is more than just data - it's a **rich model** with:

```kotlin
sealed class UDM {
    data class Object(
        val properties: Map<String, UDM>,     // The actual data
        val attributes: Map<String, String>,  // XML attributes, metadata hints
        val name: String?,                    // Element name (XML context)
        val metadata: Map<String, String>     // Internal metadata (line numbers, source info, etc.)
    )
}
```

**Context that needs preservation:**

1. **Type Information**
   - Is this a `Scalar<String>` or `Scalar<Number>`?
   - Is this `DateTime` or just a string?
   - Is this `Binary` or base64-encoded text?

2. **Structural Metadata**
   - Element name (for XML: `<Order>` vs `<Customer>`)
   - Attributes vs properties distinction
   - Array vs Object distinction

3. **Processing Metadata**
   - Source format (parsed from CSV/JSON/XML)
   - Line numbers (for error messages)
   - Parsing hints (encoding, dialect)
   - Validation state

4. **Semantic Context**
   - Is this field required?
   - What's the cardinality?
   - Schema constraints applied?

**Example:**

```kotlin
// This UDM structure:
UDM.Object(
    name = "Order",
    metadata = mapOf(
        "source" to "xml",
        "lineNumber" to "42",
        "validated" to "true"
    ),
    attributes = mapOf(
        "id" to "ORD-001",
        "status" to "confirmed"
    ),
    properties = mapOf(
        "total" to UDM.Scalar(1299.99),
        "orderDate" to UDM.DateTime(Instant.parse("2024-01-15T10:30:00Z"))
    )
)
```

**Standard YAML serialization loses:**
- ❌ Element name (`"Order"`)
- ❌ Metadata (`source`, `lineNumber`, `validated`)
- ❌ Type distinction (`Scalar` vs `DateTime`)
- ❌ Attributes vs properties

**UDM Language preserves:**
- ✅ ALL metadata
- ✅ ALL type information
- ✅ ALL structural context
- ✅ Round-trippable

---

## Use Cases for UDM Language

### 1. Debugging & Development

**Scenario**: Developer wants to understand transformation intermediate state

```utlx
%utlx 1.0
input orders xml
output json
---
let parsed = $orders
// How do I see the EXACT UDM structure here?
```

**Solution**: Export to UDM Language
```kotlin
debugExportUDM(parsed, "debug/parsed-orders.udm")
```

Result: `.udm` file showing:
- Exact types
- All metadata from XML parsing
- Element names
- Attributes
- Can be re-imported for testing

### 2. Caching & Performance

**Scenario**: Large XML file parsed repeatedly

**Problem**: Parsing XML takes 2 seconds each time

**Solution**: Parse once, export UDM, cache it
```kotlin
// First run
val udm = XMLParser(largeXml).parse()
UDMLanguageSerializer.save(udm, "cache/orders.udm")

// Subsequent runs (10x faster)
val udm = UDMLanguageParser.load("cache/orders.udm")
```

**Requirements**:
- Must be faster than original parsing
- Must preserve ALL context
- Must be byte-for-byte identical after round-trip

### 3. LSP Type Information

**Scenario**: LSP needs field types for intelligent completion

**Current**: Parse input, lose type info
**Better**: Parse input, export UDM with types, send to LSP

```typescript
// LSP receives:
{
  "type": "Array",
  "elements": [
    {
      "type": "Object",
      "properties": {
        "name": {"type": "Scalar", "valueType": "String"},
        "age": {"type": "Scalar", "valueType": "Number"},
        "orderDate": {"type": "DateTime"}
      }
    }
  ]
}
```

**Now LSP knows**:
- `age` is Number → suggest numeric operators
- `orderDate` is DateTime → suggest date functions
- `name` is String → suggest string functions

### 4. Test Fixtures & Golden Masters

**Scenario**: Testing transformations with complex inputs

**Problem**: Test data is verbose (XML/JSON/CSV files)

**Solution**: Store as UDM Language
```
tests/
  fixtures/
    complex-order.udm       # UDM Language format
    expected-output.udm     # Expected UDM result
```

**Benefits**:
- Consistent format across all tests
- Can manually edit UDM for edge cases
- Preserves exact structure for golden master testing

### 5. Visual Debugging & Documentation

**Scenario**: Need to visualize UDM tree structure

```
Order (Object)
├─ @id: "ORD-001"
├─ @status: "confirmed"
├─ customer (Object)
│  ├─ name: "Alice" (Scalar<String>)
│  └─ email: "alice@example.com" (Scalar<String>)
└─ items (Array[2])
   ├─ [0] (Object)
   │  ├─ sku: "LAPTOP-X1" (Scalar<String>)
   │  └─ price: 1299.99 (Scalar<Number>)
   └─ [1] (Object)
      └─ ...
```

**From UDM Language, can generate**:
- Tree diagrams
- GraphViz visualizations
- Interactive HTML viewers

---

## Design Principles for UDM Language

### 1. Complete Fidelity

**Principle**: Round-trip must be perfect

```
UDM (in memory) → UDM Language (.udm file) → UDM (in memory)
                                                      ↓
                                            Must be IDENTICAL
```

**What this means**:
- All fields preserved
- All metadata preserved
- All type information preserved
- Binary data preserved (base64 or external reference)
- Lambda functions preserved (as references or serialized AST)

### 2. Human Readability

**Principle**: Should be readable and editable by developers

**Good**:
```udm
@Object(name="Customer") {
  properties: {
    name: @Scalar<String>("Alice"),
    age: @Scalar<Number>(30),
    active: @Scalar<Boolean>(true)
  }
}
```

**Bad** (too verbose):
```udm
UDM$Object(
  properties=HashMap(
    entry(key="name", value=UDM$Scalar(value="Alice", type=STRING)),
    entry(key="age", value=UDM$Scalar(value=30, type=NUMBER))
  ),
  attributes=HashMap(),
  name=Optional("Customer"),
  metadata=HashMap()
)
```

### 3. Format Independence

**Principle**: UDM Language is NOT YAML/JSON/XML - it's its own format

**Why?**
- YAML has implicit typing (everything is string unless quoted)
- JSON has limited types (no Date, no Binary)
- XML has verbose syntax

**UDM Language needs**:
- Explicit type declarations
- Compact syntax
- Metadata annotations
- Schema-like structure

### 4. Tool Friendly

**Principle**: Easy to parse, easy to generate

**Requirements**:
- Clear grammar (ANTLR/PEG parseable)
- Streaming support (for large UDMs)
- Incremental parsing (for IDE features)
- Error messages with line numbers

### 5. Extensibility

**Principle**: Can evolve as UDM evolves

**Considerations**:
- Version markers (`@udm-version: 1.0`)
- Optional fields (default values)
- Custom metadata extensions
- Backward compatibility

---

## Proposed UDM Language Syntax

### Version 1.0 Proposal

#### Basic Syntax

```udm
@udm-version: 1.0

# Scalar types
@Scalar<String>("Hello")
@Scalar<Number>(42)
@Scalar<Number>(3.14)
@Scalar<Boolean>(true)
@Scalar<Null>(null)

# Shorthand for common cases
"Hello"                    # Implies @Scalar<String>
42                         # Implies @Scalar<Number>
true                       # Implies @Scalar<Boolean>
null                       # Implies @Scalar<Null>

# Date/Time types (explicit)
@DateTime("2024-01-15T10:30:00Z")
@Date("2024-01-15")
@LocalDateTime("2024-01-15T10:30:00")
@Time("10:30:00")

# Binary (reference or inline)
@Binary(size=1024, encoding="base64", ref="file:///data.bin")
@Binary("SGVsbG8gV29ybGQ=")  # Inline base64

# Lambda (reference to function)
@Lambda(id="filter-fn-123", arity=2)

# Array
@Array [
  "item1",
  "item2",
  @Scalar<Number>(42)
]

# Object (full form)
@Object(
  name: "Customer",
  metadata: {source: "xml", lineNumber: "42"}
) {
  attributes: {
    id: "CUST-789",
    status: "active"
  },
  properties: {
    name: "Alice",
    age: 30,
    email: "alice@example.com",
    orders: @Array [
      @Object(name: "Order") {
        attributes: {id: "ORD-001"},
        properties: {
          total: 1299.99,
          date: @DateTime("2024-01-15T10:30:00Z")
        }
      }
    ]
  }
}

# Shorthand for simple objects (no metadata/attributes)
@Object {
  name: "Alice",
  age: 30
}

# Even shorter (implies @Object)
{
  name: "Alice",
  age: 30
}
```

#### Complete Example

```udm
@udm-version: 1.0
@source: "orders.xml"
@parsed-at: "2024-01-15T10:30:00Z"

@Array [
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
      customer: @Object(name: "Customer") {
        properties: {
          id: "CUST-789",
          name: "Alice Johnson",
          email: "alice@example.com",
          registeredDate: @Date("2023-06-15")
        }
      },
      items: @Array [
        @Object(name: "Item") {
          attributes: {sku: "LAPTOP-X1"},
          properties: {
            name: "Laptop X1 Pro",
            price: 1299.99,
            quantity: 1,
            inStock: true
          }
        },
        @Object(name: "Item") {
          attributes: {sku: "MOUSE-M2"},
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
      shippingAddress: @Object {
        properties: {
          street: "123 Main St",
          city: "San Francisco",
          state: "CA",
          zip: "94102"
        }
      }
    }
  }
]
```

---

## Comparison: UDM Language vs Standard Formats

### Example UDM Structure

```kotlin
UDM.Object(
    name = "Order",
    metadata = mapOf("source" to "xml", "lineNumber" to "42"),
    attributes = mapOf("id" to "ORD-001", "status" to "confirmed"),
    properties = mapOf(
        "customer" to UDM.Scalar("Alice"),
        "total" to UDM.Scalar(1299.99),
        "orderDate" to UDM.DateTime(Instant.parse("2024-01-15T10:30:00Z"))
    )
)
```

### Standard YAML (Transformation Output)

```yaml
# ❌ Loses metadata, element name, type info
customer: Alice
total: 1299.99
orderDate: "2024-01-15T10:30:00Z"
_attributes:
  "@id": "ORD-001"
  "@status": "confirmed"
```

**Problems**:
- No element name (`"Order"`)
- No metadata (`source`, `lineNumber`)
- No type info (`orderDate` is string, not DateTime)
- Attributes mixed with data

### UDM Language (Model Export)

```udm
# ✅ Preserves everything
@Object(
  name: "Order",
  metadata: {source: "xml", lineNumber: "42"}
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

**Advantages**:
- ✅ Element name preserved
- ✅ Metadata preserved
- ✅ Type info preserved (`@DateTime`)
- ✅ Clear separation (attributes vs properties)
- ✅ Round-trippable

### Enhanced JSON (Alternative)

```json
{
  "__udm_version__": "1.0",
  "__udm_type__": "Object",
  "__udm_name__": "Order",
  "__udm_metadata__": {
    "source": "xml",
    "lineNumber": "42"
  },
  "__udm_attributes__": {
    "id": "ORD-001",
    "status": "confirmed"
  },
  "properties": {
    "customer": {
      "__udm_type__": "Scalar",
      "__udm_value_type__": "String",
      "value": "Alice"
    },
    "total": {
      "__udm_type__": "Scalar",
      "__udm_value_type__": "Number",
      "value": 1299.99
    },
    "orderDate": {
      "__udm_type__": "DateTime",
      "value": "2024-01-15T10:30:00Z"
    }
  }
}
```

**Pros**: Standard JSON, machine-readable
**Cons**: Very verbose, not human-friendly

---

## Implementation Considerations

### 1. Parser (UDM Language → UDM)

**Technology Options**:

- **ANTLR4** - Generate parser from grammar
- **Kotlin Parser Combinators** - Hand-written parser
- **PEG (Parsing Expression Grammar)** - Simple and efficient

**Grammar Sketch (ANTLR4-style)**:

```antlr
grammar UDMLang;

udm_file
    : udm_header udm_value EOF
    ;

udm_header
    : '@udm-version:' VERSION_NUMBER
      ('@source:' STRING)?
      ('@parsed-at:' DATETIME)?
    ;

udm_value
    : scalar
    | array
    | object
    | datetime
    | date
    | time
    | binary
    | lambda
    ;

object
    : '@Object' object_meta? '{' object_body '}'
    | '{' simple_properties '}'  // Shorthand
    ;

object_meta
    : '(' 'name:' STRING (',' metadata_map)? ')'
    ;

object_body
    : ('attributes:' '{' key_value_pairs '}' ',')?
      'properties:' '{' key_value_pairs '}'
    ;

scalar
    : '@Scalar' '<' type_name '>' '(' value ')'
    | STRING | NUMBER | BOOLEAN | NULL  // Shorthand
    ;

array
    : '@Array' '[' udm_value (',' udm_value)* ']'
    | '[' udm_value (',' udm_value)* ']'  // Shorthand
    ;

datetime
    : '@DateTime' '(' STRING ')'
    ;
```

### 2. Serializer (UDM → UDM Language)

**Implementation**:

```kotlin
object UDMLanguageSerializer {
    fun serialize(udm: UDM, indent: Boolean = true): String {
        val sb = StringBuilder()
        sb.append("@udm-version: 1.0\n\n")
        serializeValue(udm, sb, indent = indent, depth = 0)
        return sb.toString()
    }

    private fun serializeValue(udm: UDM, sb: StringBuilder, indent: Boolean, depth: Int) {
        when (udm) {
            is UDM.Scalar -> serializeScalar(udm, sb)
            is UDM.Array -> serializeArray(udm, sb, indent, depth)
            is UDM.Object -> serializeObject(udm, sb, indent, depth)
            is UDM.DateTime -> sb.append("@DateTime(\"${udm.instant}\")")
            is UDM.Date -> sb.append("@Date(\"${udm.date}\")")
            is UDM.Binary -> sb.append("@Binary(size=${udm.data.size})")
            is UDM.Lambda -> sb.append("@Lambda()")
            // ... other types
        }
    }

    private fun serializeObject(obj: UDM.Object, sb: StringBuilder, indent: Boolean, depth: Int) {
        sb.append("@Object")

        // Metadata
        if (obj.name != null || obj.metadata.isNotEmpty()) {
            sb.append("(\n")
            if (obj.name != null) {
                sb.append("${indentation(depth + 1)}name: \"${obj.name}\"\n")
            }
            if (obj.metadata.isNotEmpty()) {
                sb.append("${indentation(depth + 1)}metadata: {")
                obj.metadata.forEach { (k, v) ->
                    sb.append("$k: \"$v\", ")
                }
                sb.append("}\n")
            }
            sb.append("${indentation(depth)})")
        }

        sb.append(" {\n")

        // Attributes
        if (obj.attributes.isNotEmpty()) {
            sb.append("${indentation(depth + 1)}attributes: {\n")
            obj.attributes.forEach { (k, v) ->
                sb.append("${indentation(depth + 2)}$k: \"$v\",\n")
            }
            sb.append("${indentation(depth + 1)}},\n")
        }

        // Properties
        sb.append("${indentation(depth + 1)}properties: {\n")
        obj.properties.forEach { (k, v) ->
            sb.append("${indentation(depth + 2)}$k: ")
            serializeValue(v, sb, indent, depth + 2)
            sb.append(",\n")
        }
        sb.append("${indentation(depth + 1)}}\n")

        sb.append("${indentation(depth)}}")
    }

    private fun indentation(depth: Int): String = "  ".repeat(depth)
}
```

### 3. Performance

**Benchmarks to track**:
- Parse time: UDM Language → UDM (should be < 50% of original format parsing)
- Serialize time: UDM → UDM Language (should be < 10ms for typical structures)
- File size: UDM Language vs YAML (acceptable: 2-3x larger for metadata)
- Round-trip accuracy: Must be 100% identical

### 4. Tooling

**What to build**:

1. **CLI Tool**:
   ```bash
   utlx udm export input.xml output.udm
   utlx udm import cached.udm
   utlx udm validate schema.udm
   utlx udm diff old.udm new.udm
   utlx udm visualize tree.udm --format=svg
   ```

2. **LSP Integration**:
   - Parse `.udm` files for type information
   - Show UDM structure in hover tooltips
   - Export parsed inputs to `.udm` for caching

3. **IDE Plugin** (VS Code, IntelliJ):
   - Syntax highlighting for `.udm` files
   - Tree view of UDM structure
   - Diff view for comparing UDMs

4. **Debug Tools**:
   - Interactive UDM browser
   - Step-through UDM transformations
   - Visual UDM tree

---

## Migration Path

### Phase 1: Specification & Prototype

1. Finalize UDM Language syntax
2. Write formal grammar (ANTLR4)
3. Implement basic parser
4. Implement basic serializer
5. Test round-trip accuracy

**Deliverables**:
- UDM Language Specification v1.0
- Parser/Serializer prototype
- Test suite (100 test cases)

### Phase 2: Integration

1. Add CLI commands (`utlx udm export/import`)
2. Integrate with debugging functions
3. Add LSP support for `.udm` files
4. Update documentation

**Deliverables**:
- CLI tool
- LSP integration
- Documentation

### Phase 3: Tooling & Enhancement

1. IDE plugins
2. Visual tree viewer
3. Diff tools
4. Performance optimization

**Deliverables**:
- VS Code extension
- Web-based UDM viewer
- Benchmark suite

---

## Open Questions

1. **File Extension**: `.udm` or `.udm.yaml` or `.umd`?

2. **Compact vs Verbose**: Should there be two modes?
   - Compact: For human editing
   - Verbose: For complete fidelity

3. **Schema Support**: Should UDM Language support schema definitions?
   ```udm
   @schema Order {
     @required id: String
     @required total: Number
     @optional customer: Customer
   }
   ```

4. **Incremental Updates**: Support for partial UDM updates?
   ```udm
   @update {
     path: "orders[0].total",
     value: 1999.99
   }
   ```

5. **Compression**: For large UDMs, support binary format?
   ```
   order.udm      # Text format (10 MB)
   order.udm.gz   # Compressed (2 MB)
   order.udmb     # Binary format (1.5 MB)
   ```

---

## Conclusion

**UDM as a Language is fundamentally different from UDM as data:**

| Aspect | Transformation (Data) | UDM Language (Model) |
|--------|----------------------|---------------------|
| **Purpose** | End-user consumption | Debugging, caching, tooling |
| **Format** | YAML/JSON/XML/CSV | `.udm` custom format |
| **Metadata** | Minimal | Complete |
| **Type Info** | Implicit | Explicit |
| **Round-trip** | Lossy | Perfect fidelity |
| **Readability** | Very readable | Readable with annotations |
| **Use Case** | Data transformation | Model preservation |

**Recommendation**: Implement UDM Language as a separate format (`.udm`) with:
- Custom syntax optimized for UDM structure
- Complete metadata preservation
- Perfect round-trip capability
- Human-readable and tool-friendly
- Integration with CLI, LSP, and debug tools

This creates a powerful meta-format for working with UDM structures while keeping the transformation outputs clean and user-friendly.
