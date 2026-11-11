# UTLX Standards Classification: Complete Tier Model

**Date**: November 7, 2025  
**Purpose**: Complete classification of data standards in UTLX architecture  
**Version**: 1.0

---

## Overview: The UTLX Tier Architecture

UTLX operates on a multi-tier architecture where each tier serves a specific purpose in data processing:

```
┌────────────────────────────────────────────────────┐
│ Tier 4: Expression/Logic Layer                     │
│ (Decision logic, calculations, transformations)    │
├────────────────────────────────────────────────────┤
│ Tier 3: Validation Layer                          │
│ (Business rules, constraints, semantic validation) │
├────────────────────────────────────────────────────┤
│ Tier 2: Schema Layer                              │
│ (Structure definition, type systems)               │
├────────────────────────────────────────────────────┤
│ Tier 1: Instance/Data Layer                       │
│ (Actual data, serialization formats)               │
└────────────────────────────────────────────────────┘
```

---

## Tier 1: Instance/Data Layer

**Purpose**: Actual data representation and serialization formats

**Characteristics**:
- Contains the actual data values
- Defines how data is encoded/serialized
- No validation or schema information
- Raw data files

### Text-Based Formats

| Format | Extension | Description | UTLX Support |
|--------|-----------|-------------|--------------|
| **JSON** | .json | JavaScript Object Notation | ✅ Primary |
| **XML** | .xml | eXtensible Markup Language | ✅ Primary |
| **CSV** | .csv | Comma-Separated Values | ✅ Primary |
| **YAML** | .yaml, .yml | YAML Ain't Markup Language | ✅ Primary |
| **JSON-LD** | .jsonld | JSON for Linked Data (RDF) | ✅ Semantic |
| **Turtle** | .ttl | Terse RDF Triple Language | ⚠️ Consider |
| **N-Triples** | .nt | Line-based RDF format | ⚠️ Consider |
| **RDF/XML** | .rdf | RDF in XML serialization | ⚠️ Consider |
| **TSV** | .tsv | Tab-Separated Values | ✅ CSV variant |
| **TOML** | .toml | Tom's Obvious Minimal Language | 🤔 Consider |
| **Properties** | .properties | Java properties files | 🤔 Consider |
| **INI** | .ini | Configuration files | 🤔 Consider |

### Binary Formats

| Format | Extension | Description | UTLX Support |
|--------|-----------|-------------|--------------|
| **Avro (Binary)** | .avro | Apache Avro binary | ✅ Primary |
| **Protobuf (Binary)** | .pb | Protocol Buffers binary | ✅ Primary |
| **Parquet** | .parquet | Columnar storage format | 🤔 Consider |
| **ORC** | .orc | Optimized Row Columnar | 🤔 Consider |
| **MessagePack** | .msgpack | Binary JSON-like format | 🤔 Consider |
| **CBOR** | .cbor | Concise Binary Object Representation | 🤔 Consider |

### Document Formats

| Format | Extension | Description | UTLX Support |
|--------|-----------|-------------|--------------|
| **HTML** | .html | HyperText Markup Language | 🤔 Consider |
| **Markdown** | .md | Lightweight markup | 🤔 Consider |

### Example Files (Tier 1)

```
data/
├── customers.json          # JSON instance
├── orders.xml              # XML instance
├── products.csv            # CSV instance
├── config.yaml             # YAML instance
├── person.jsonld           # JSON-LD instance (RDF)
├── graph.ttl               # Turtle instance (RDF)
├── events.avro             # Avro binary instance
└── messages.pb             # Protobuf binary instance
```

---

## Tier 2: Schema Layer

**Purpose**: Define structure, data types, and format of data

**Characteristics**:
- Describes what valid data looks like
- Type definitions and constraints
- Structural validation
- No business logic or rules

### Schema Languages

| Schema Type | Code | For Format | Standard Body | Description |
|-------------|------|------------|---------------|-------------|
| **JSON Schema** | `jsch` | JSON, YAML | IETF | Structural validation for JSON |
| **XML Schema** | `xsd` | XML | W3C | Structure and types for XML |
| **Avro Schema** | `avro` | JSON, Binary | Apache | Schema with binary serialization |
| **Protocol Buffers** | `proto` | Binary | Google | IDL with code generation |
| **CSV Schema** | `csvsch` | CSV | - | Column definitions for CSV |
| **Table Schema** | `tsch` | CSV, Tables | Frictionless Data | Tabular data schemas |
| **CSVW Metadata** | `csvw` | CSV | W3C | CSV on the Web metadata |
| **RelaxNG** | `rng` | XML | OASIS | Alternative XML schema |
| **DTD** | `dtd` | XML | W3C | Document Type Definition (legacy) |
| **ASN.1** | `asn1` | Various | ITU-T/ISO | Telecom/crypto schemas |
| **Thrift IDL** | `thrift` | Binary | Apache | Cross-language services |
| **Cap'n Proto** | `capnp` | Binary | - | Fast data interchange |

### UTLX Usage Examples (Tier 2)

```utlx
%utlx 1.0
input json
schema customer.json type:jsch
---
// Transform data
```

```utlx
%utlx 1.0
input xml
schema order.xsd type:xsd
---
// Transform data
```

```utlx
%utlx 1.0
input csv
schema products.json type:tsch
---
// Transform data
```

### Schema Type Matrix

| Data Format | Primary Schema | Alternative Schemas | Notes |
|-------------|---------------|---------------------|-------|
| JSON | `jsch` | `avro`, `proto` | JSON Schema most common |
| XML | `xsd` | `rng`, `dtd` | XSD is standard |
| CSV | `tsch` | `csvsch`, `csvw` | Table Schema recommended |
| YAML | `jsch` | `avro` | Treat as JSON |
| Binary (Avro) | `avro` | - | Self-describing |
| Binary (Protobuf) | `proto` | - | Requires schema |
| RDF formats | - | - | Use Tier 3 instead |

---

## Tier 3: Validation Layer

**Purpose**: Business rules, semantic validation, and context-dependent constraints

**Characteristics**:
- Complex validation beyond structure
- Business logic and rules
- Semantic meaning and relationships
- Context-dependent constraints

### Validation Standards

| Standard | Code | For Format | Standard Body | Description |
|----------|------|------------|---------------|-------------|
| **Schematron** | `schematron` | XML | ISO | Business rules for XML |
| **JSON-LD Context** | `jsonld` | JSON | W3C | Semantic/RDF validation |
| **SHACL** | `shacl` | RDF | W3C | Shapes for RDF graphs |
| **ShEx** | `shex` | RDF | W3C | Shape Expressions for RDF |
| **OWL** | `owl` | RDF | W3C | Ontology language (reasoning) |
| **RDFS** | `rdfs` | RDF | W3C | RDF Schema (vocabulary) |
| **JSON Rules** | `jsonrules` | JSON | - | Custom business rules |
| **XPath Assertions** | `xpath` | XML | W3C | XPath-based validation |

### Tier 3 Characteristics

**What Tier 2 (Schema) CANNOT Express:**

❌ Cross-field validation (end_date > start_date)  
❌ Conditional requirements (if type=X then field Y required)  
❌ Business rules (total = sum of line items)  
❌ Semantic relationships (person knows person)  
❌ Ontology reasoning (if X is subclass of Y...)  
❌ Context-dependent constraints  

**What Tier 3 (Validation) CAN Express:**

✅ All of the above  
✅ Complex business logic  
✅ Semantic meaning and relationships  
✅ Inference and reasoning  
✅ Multi-document validation  

### UTLX Usage Examples (Tier 3)

#### Schematron for Business Rules

```utlx
%utlx 1.0
input xml
schema invoice.xsd type:xsd                    # Tier 2: Structure
schema invoice-rules.sch type:schematron      # Tier 3: Business rules
---
// Transform validated data
```

**invoice-rules.sch**:
```xml
<sch:pattern>
  <sch:rule context="invoice">
    <sch:assert test="total = sum(lineItem/price)">
      Invoice total must equal sum of line items
    </sch:assert>
    <sch:assert test="dueDate > issueDate">
      Due date must be after issue date
    </sch:assert>
  </sch:rule>
</sch:pattern>
```

#### JSON-LD for Semantic Validation

```utlx
%utlx 1.0
input json
schema person.json type:jsch               # Tier 2: Structure
schema person-context.jsonld type:jsonld   # Tier 3: Semantics
---
// Transform with semantic preservation
```

**person-context.jsonld**:
```json
{
  "@context": {
    "@vocab": "http://schema.org/",
    "Person": "http://schema.org/Person",
    "knows": {
      "@id": "http://schema.org/knows",
      "@type": "@id"
    }
  }
}
```

#### SHACL for RDF Validation

```utlx
%utlx 1.0
input turtle
schema person-shapes.ttl type:shacl       # Tier 3: RDF constraints
output json-ld
---
// Validate and transform RDF
```

**person-shapes.ttl**:
```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix schema: <http://schema.org/> .

schema:PersonShape
  a sh:NodeShape ;
  sh:targetClass schema:Person ;
  sh:property [
    sh:path schema:name ;
    sh:minCount 1 ;
    sh:datatype xsd:string ;
  ] ;
  sh:property [
    sh:path schema:age ;
    sh:minInclusive 0 ;
    sh:maxInclusive 150 ;
  ] .
```

### Validation Layer Comparison

| Standard | Primary Use | Strengths | Limitations |
|----------|-------------|-----------|-------------|
| **Schematron** | XML business rules | XPath power, readable | XML only |
| **JSON-LD** | Semantic JSON | Web-scale linking | Complexity |
| **SHACL** | RDF constraints | Comprehensive, standard | RDF only |
| **ShEx** | RDF shapes | Concise, modular | Less adoption |
| **OWL** | Reasoning | Logic inference | Complex, slow |

---

## Tier 4: Expression/Logic Layer

**Purpose**: Computational logic, decision-making, and data transformation

**Characteristics**:
- Executable expressions
- Decision logic
- Calculations and transformations
- Procedural or functional logic

### Expression Languages

| Language | Code | Standard Body | Description |
|----------|------|---------------|-------------|
| **UTLX Native** | (default) | - | Built-in UTLX expression syntax |
| **FEEL** | `feel` | OMG | Friendly Enough Expression Language |
| **XPath** | `xpath` | W3C | XML path expressions |
| **XQuery** | `xquery` | W3C | XML query language |
| **SPARQL** | `sparql` | W3C | RDF query language |
| **JMESPath** | `jmespath` | - | JSON query language |
| **JSONPath** | `jsonpath` | - | XPath for JSON |
| **GraphQL** | `graphql` | GraphQL Foundation | Query language for APIs |
| **SQL** | `sql` | ISO | Database query language |

### UTLX Native Expression Syntax (Default)

```utlx
%utlx 1.0
input json
output json
---
{
  // UTLX native expressions (Tier 4)
  fullName: $input.firstName + " " + $input.lastName,
  age: calculateAge($input.birthDate),
  category: if ($input.age < 18) then "minor" else "adult",
  orders: $input.orders 
    |> filter(o => o.total > 100)
    |> map(o => o.id)
}
```

### FEEL Integration (Hypothetical)

```utlx
%utlx 1.0
input json
schema pricing.dmn type:dmn
output json
---
{
  // Option 1: Inline FEEL expression
  discount: %feel{ 
    if quantity > 100 then 0.1 
    else if quantity > 50 then 0.05 
    else 0 
  },
  
  // Option 2: Reference DMN decision
  priceCategory: evaluateDMN("PricingDecision", $input)
}
```

### SPARQL for RDF Querying

```utlx
%utlx 1.0
input turtle
output json
---
// Use SPARQL to query RDF (Tier 4)
{
  people: sparqlQuery("""
    SELECT ?name ?age
    WHERE {
      ?person a schema:Person ;
              schema:name ?name ;
              schema:age ?age .
      FILTER (?age > 18)
    }
  """)
}
```

### XQuery for XML Processing

```utlx
%utlx 1.0
input xml
output json
---
// Use XQuery for complex XML transformation (Tier 4)
{
  summary: xquery("""
    for $order in //order
    where $order/total > 1000
    return <summary>
      <id>{$order/@id}</id>
      <customer>{$order/customer/text()}</customer>
    </summary>
  """)
}
```

---

## Complete Standards Classification Table

| Standard | Tier | Code | Category | Standard Body |
|----------|------|------|----------|---------------|
| **JSON** | 1 | - | Data format | ECMA |
| **XML** | 1 | - | Data format | W3C |
| **CSV** | 1 | - | Data format | RFC 4180 |
| **YAML** | 1 | - | Data format | - |
| **JSON-LD** | 1 | - | Data format (RDF) | W3C |
| **Turtle** | 1 | - | Data format (RDF) | W3C |
| **RDF/XML** | 1 | - | Data format (RDF) | W3C |
| **Avro Binary** | 1 | - | Binary format | Apache |
| **Protobuf Binary** | 1 | - | Binary format | Google |
| **JSON Schema** | 2 | `jsch` | Schema | IETF |
| **XML Schema** | 2 | `xsd` | Schema | W3C |
| **Avro Schema** | 2 | `avro` | Schema | Apache |
| **Protocol Buffers** | 2 | `proto` | Schema/IDL | Google |
| **Table Schema** | 2 | `tsch` | Schema | Frictionless |
| **CSV Schema** | 2 | `csvsch` | Schema | - |
| **CSVW** | 2 | `csvw` | Schema | W3C |
| **RelaxNG** | 2 | `rng` | Schema | OASIS |
| **DTD** | 2 | `dtd` | Schema (legacy) | W3C |
| **Schematron** | 3 | `schematron` | Validation | ISO |
| **JSON-LD Context** | 3 | `jsonld` | Semantic validation | W3C |
| **SHACL** | 3 | `shacl` | RDF validation | W3C |
| **ShEx** | 3 | `shex` | RDF validation | W3C |
| **OWL** | 3 | `owl` | Ontology/reasoning | W3C |
| **RDFS** | 3 | `rdfs` | RDF vocabulary | W3C |
| **UTLX Expressions** | 4 | (default) | Expression language | - |
| **FEEL** | 4 | `feel` | Expression language | OMG |
| **XPath** | 4 | `xpath` | Query language | W3C |
| **XQuery** | 4 | `xquery` | Query language | W3C |
| **SPARQL** | 4 | `sparql` | Query language | W3C |
| **JMESPath** | 4 | `jmespath` | Query language | - |
| **JSONPath** | 4 | `jsonpath` | Query language | - |

---

## Visual Tier Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ TIER 4: EXPRESSION/LOGIC LAYER                              │
│                                                               │
│ UTLX Native | FEEL | XPath | XQuery | SPARQL | JMESPath     │
│ ├─ Expressions    ├─ Queries    ├─ Transformations          │
│ └─ Decision logic └─ Data access └─ Calculations            │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ TIER 3: VALIDATION LAYER                                     │
│                                                               │
│ Schematron | JSON-LD | SHACL | ShEx | OWL | RDFS            │
│ ├─ Business rules        ├─ RDF constraints                 │
│ └─ Semantic validation   └─ Ontologies                      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ TIER 2: SCHEMA LAYER                                         │
│                                                               │
│ JSON Schema | XSD | Avro | Proto | Table Schema | CSVW      │
│ ├─ Structure definition  ├─ Type systems                    │
│ └─ Format constraints    └─ Validation rules                │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ TIER 1: INSTANCE/DATA LAYER                                  │
│                                                               │
│ JSON | XML | CSV | YAML | JSON-LD | Turtle | Avro | Protobuf│
│ ├─ Text formats          ├─ Binary formats                  │
│ └─ Actual data values    └─ Serialized data                 │
└─────────────────────────────────────────────────────────────┘
```

---

## UTLX Multi-Tier Example

### Complete Pipeline Using All Tiers

```utlx
%utlx 1.0

# TIER 1: Define data formats
input xml                           # Input instance format
output json-ld                      # Output instance format

# TIER 2: Define schemas
schema order.xsd type:xsd          # Structural schema

# TIER 3: Define validations
schema order-rules.sch type:schematron    # Business rules
schema order-context.jsonld type:jsonld   # Semantic mapping

# TIER 4: Transformation logic (in body)
---
{
  # Native UTLX expressions (Tier 4)
  "@context": "https://schema.org",
  "@type": "Order",
  "@id": "https://example.com/orders/" + $input.order/@id,
  
  "orderNumber": $input.order/@id,
  "orderDate": $input.order/date,
  
  # Complex logic (Tier 4)
  "totalAmount": sum($input.order/items/item/price),
  
  "customer": {
    "@id": "https://example.com/customers/" + $input.order/customer/@id,
    "name": $input.order/customer/name
  },
  
  # Conditional logic (Tier 4)
  "priority": if ($input.order/total > 1000) then "high" else "normal",
  
  # Array transformation (Tier 4)
  "items": $input.order/items/item
    |> map(item => {
      "name": item/name,
      "price": item/price,
      "quantity": item/quantity
    })
}
```

**What happens in this example:**

1. **Tier 1**: Reads XML instance, outputs JSON-LD instance
2. **Tier 2**: Validates XML structure against XSD schema
3. **Tier 3**: Validates business rules with Schematron, applies JSON-LD semantic context
4. **Tier 4**: Executes transformation logic with UTLX expressions

---

## Recommendations for UTLX Implementation

### Priority Matrix

| Tier | Priority Level | Standards to Implement |
|------|----------------|------------------------|
| **Tier 1** | ✅ HIGH | JSON, XML, CSV, YAML, JSON-LD, Avro, Protobuf |
| **Tier 2** | ✅ HIGH | JSON Schema (jsch), XSD, Avro, Proto, Table Schema (tsch) |
| **Tier 3** | ⚠️ MEDIUM | JSON-LD context, Schematron, SHACL |
| **Tier 4** | ✅ HIGH | UTLX native expressions |
| **Tier 4** | 🤔 LOW | FEEL, SPARQL, XQuery (optional integrations) |

### Implementation Phases

**Phase 1: Core (MVP)**
- Tier 1: JSON, XML, CSV, YAML
- Tier 2: jsch, xsd, tsch
- Tier 4: UTLX native expressions

**Phase 2: Binary & Semantic**
- Tier 1: JSON-LD, Avro, Protobuf
- Tier 2: avro, proto
- Tier 3: jsonld (JSON-LD context)

**Phase 3: Advanced Validation**
- Tier 3: schematron, shacl

**Phase 4: External Languages (Optional)**
- Tier 4: feel, sparql, xquery

---

## Conclusion

The UTLX tier architecture provides a clear separation of concerns:

- **Tier 1** = What the data looks like (format)
- **Tier 2** = What structure is valid (schema)
- **Tier 3** = What business rules apply (validation)
- **Tier 4** = How to transform it (logic)

This separation allows for:
- ✅ Clear understanding of standard roles
- ✅ Modular implementation
- ✅ Flexible combinations
- ✅ Standards-based interoperability
- ✅ Progressive enhancement

---

**Document Version**: 1.0  
**Last Updated**: November 7, 2025  
**Author**: Complete standards classification for UTL-X project  
**Status**: Architecture reference document
