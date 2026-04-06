# Linked Data and Standards Discussion for UTLX

**Date**: November 7, 2025  
**Topic**: Linked Data, RDF, JSON-LD Integration in UTLX 1.0, and Related Standards

---

## Part 1: Linked Data Integration in UTLX

### What is Linked Data?

Linked Data is a way to create a network of standards-based machine interpretable data across different documents and Web sites, allowing applications to start at one piece of Linked Data and follow embedded links to other pieces hosted on different sites across the Web.

**Key Technologies**:
1. **RDF** (Resource Description Framework) - The data model
2. **JSON-LD** - JSON for Linked Data (W3C Standard since 2010)
3. **Schema.org** - Common vocabulary for structured data
4. **IRIs/URIs** - Universal identifiers for concepts

### How Linked Data Fits in UTLX

**JSON-LD is essentially JSON with semantic annotations**, which means:

✅ **JSON-LD files ARE valid JSON files**  
✅ **Can use `jsch` (JSON Schema) for structural validation**  
✅ **But also need semantic context validation**

---

## Recommendation: Add `jsonld` Type Identifier

**Type Identifier**: **`jsonld`** - For Linked Data/RDF serialization in JSON

### UTLX Schema Type Matrix (Updated)

| Format | Primary Schema | Alternative Schema | Linked Data |
|--------|---------------|-------------------|-------------|
| JSON | `jsch` | `avro` | **`jsonld`** ✨ |
| YAML | `jsch` | `avro` | **`jsonld`** ✨ |
| CSV | `tsch` | - | - |
| XML | `xsd` | - | `rdf/xml` |
| Binary | `avro` | - | - |

---

## JSON-LD Examples

### Regular JSON
```json
{
  "name": "John Lennon",
  "born": "1940-10-09",
  "spouse": "Cynthia Lennon"
}
```

### JSON-LD (with Linked Data semantics)
```json
{
  "@context": "https://json-ld.org/contexts/person.jsonld",
  "@id": "http://dbpedia.org/resource/John_Lennon",
  "name": "John Lennon",
  "born": "1940-10-09",
  "spouse": "http://dbpedia.org/resource/Cynthia_Lennon"
}
```

**Key Additions**:
- `@context` - Maps properties to ontology URIs
- `@id` - Unique identifier for this entity
- `spouse` - Now a **link** to another entity (not just text)

---

## UTLX Usage Patterns

### 1. Validate Structure with `jsch`, Semantics with `jsonld`

```utlx
%utlx 1.0
input json
output rdf
schema person-schema.json type:jsch        # Structural validation
schema person-context.jsonld type:jsonld   # Semantic validation
---
// Transform JSON-LD to RDF triples
{
  subject: $input["@id"],
  triples: extractRdfTriples($input)
}
```

### 2. Transform Regular JSON to JSON-LD

```utlx
%utlx 1.0
input json
output json
---
{
  "@context": "https://schema.org",
  "@type": "Person",
  "@id": "https://example.com/people/" + $input.id,
  "name": $input.fullName,
  "email": $input.emailAddress,
  "birthDate": $input.dob
}
```

### 3. Extract Data from Linked Data

```utlx
%utlx 1.0
input json
schema knowledge-graph.jsonld type:jsonld
output json
---
{
  people: $input["@graph"] 
    |> filter(node => node["@type"] == "Person")
    |> map(person => {
      name: person.name,
      url: person["@id"]
    })
}
```

### 4. Multi-Format Linked Data

```utlx
%utlx 1.0
input json-ld    # Special input type for JSON-LD
output turtle    # RDF Turtle format
schema schema.org type:jsonld
---
// Automatic conversion from JSON-LD to Turtle RDF
$input
```

---

## Why Linked Data Matters for UTLX

### 1. Semantic Web Integration

JSON-LD is used by Schema.org, Google Knowledge Graph for search engine optimization, biomedical informatics, provenance information, Activity Streams, ActivityPub (federated social networking), and IoT applications.

**Use Cases**:
- SEO optimization (structured data for search engines)
- Knowledge graphs (connecting related data)
- API interoperability (semantic APIs)
- Data integration (merging data from multiple sources)

### 2. Industry Adoption

**Major Users**:
- Google (Knowledge Graph, Rich Snippets)
- Schema.org (structured data standard)
- ORCID (researcher identifiers)
- Biomedical databases
- Government open data

### 3. Transformation Power

**UTLX can bridge worlds**:
```
CSV → UTLX → JSON-LD → Knowledge Graph
XML → UTLX → JSON-LD → RDF Triples  
JSON → UTLX → JSON-LD → Linked Data
```

---

## Implementation in USDL

### JSON-LD Context Validation

**Context File** (person-context.jsonld):
```json
{
  "@context": {
    "@vocab": "http://schema.org/",
    "Person": "http://schema.org/Person",
    "name": "http://schema.org/name",
    "email": "http://schema.org/email",
    "birthDate": {
      "@id": "http://schema.org/birthDate",
      "@type": "http://www.w3.org/2001/XMLSchema#date"
    }
  }
}
```

**USDL Integration**:
```kotlin
// USDL handles JSON-LD contexts
when (schemaType) {
    "jsch" -> validateJsonSchema(data, schema)
    "jsonld" -> {
        validateJsonStructure(data)  // Still JSON
        validateJsonLdContext(data)  // @context validation
        validateRdfTriples(data)     // RDF semantics
    }
}
```

---

## Comparison: `jsch` vs `jsonld`

| Aspect | `jsch` (JSON Schema) | `jsonld` (JSON-LD Context) |
|--------|---------------------|---------------------------|
| **Purpose** | Structure validation | Semantic validation |
| **Validates** | Data types, constraints | Ontology mappings, IRIs |
| **Focus** | "Is this valid JSON?" | "What does this JSON mean?" |
| **Example** | Required fields, min/max | IRI resolution, vocabulary |
| **Standard** | JSON Schema Draft 2020-12 | W3C JSON-LD 1.1 |
| **Used For** | API contracts | Knowledge graphs |

**Both can be used together**:
```utlx
schema structure.json type:jsch      # Validate structure
schema semantics.jsonld type:jsonld  # Validate semantics
```

---

# Part 2: RDF vs Linked Data Relationship

## Understanding the Distinction

### Simple Answer

**RDF is the technology/standard. Linked Data is the methodology/practice.**

Think of it like:
- **HTTP** (technology) vs. **The Web** (what you build with it)
- **SQL** (technology) vs. **Database Design** (how you use it)
- **RDF** (technology) vs. **Linked Data** (how you publish data with it)

---

## Detailed Breakdown

### RDF (Resource Description Framework)

RDF is a standard model for data interchange on the Web, developed by W3C.

**What RDF IS**:
- A **data model** for representing information as triples (subject-predicate-object)
- A family of **serialization formats** (Turtle, RDF/XML, JSON-LD, N-Triples)
- A **framework** for expressing relationships between resources
- Foundation for the **Semantic Web**

**RDF Triple Structure**:
```
Subject → Predicate → Object

Example:
<http://example.org/John> <http://schema.org/name> "John Lennon"
<http://example.org/John> <http://schema.org/born> "1940-10-09"
<http://example.org/John> <http://schema.org/spouse> <http://example.org/Cynthia>
```

**RDF Can Be Used For**:
- Internal knowledge representation
- Database storage (triple stores)
- Application-specific data models
- Private data integration

---

### Linked Data

Linked Data is a **methodology** for publishing structured data using RDF, defined by Tim Berners-Lee's four principles:

#### The Four Principles of Linked Data

1. **Use URIs as names for things**
   - Everything has a unique identifier on the web

2. **Use HTTP URIs so people can look up those names**
   - URIs should be dereferenceable (you can fetch them)

3. **When someone looks up a URI, provide useful information using standards (RDF, SPARQL)**
   - Return structured data when URI is accessed

4. **Include links to other URIs so they can discover more things**
   - Create a web of data through links

**What Linked Data IS**:
- A **set of best practices** for publishing RDF data on the web
- A **methodology** for creating interconnected datasets
- A way to make data **discoverable and interoperable**
- The basis for the **Web of Data**

---

## The Relationship: RDF ⊂ Linked Data

### Venn Diagram Concept

```
┌─────────────────────────────────────┐
│  All Data on the Web                │
│  ┌───────────────────────────────┐  │
│  │  RDF Data                     │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │  Linked Data            │  │  │
│  │  │  (RDF + Best Practices) │  │  │
│  │  └─────────────────────────┘  │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

### Examples to Illustrate

#### RDF but NOT Linked Data

```turtle
# Internal RDF data - not published on web with HTTP URIs
_:person1 schema:name "John" .
_:person1 schema:age 30 .
```

**Why NOT Linked Data?**
- ❌ Uses blank nodes (not HTTP URIs)
- ❌ Not accessible via HTTP
- ❌ No links to external resources

#### Linked Data (which uses RDF)

```json-ld
{
  "@context": "https://schema.org",
  "@id": "http://example.com/people/john",
  "@type": "Person",
  "name": "John Lennon",
  "birthPlace": "http://dbpedia.org/resource/Liverpool",
  "knows": "http://example.com/people/paul"
}
```

**Why this IS Linked Data:**
- ✅ HTTP URIs (dereferenceable)
- ✅ Links to external resources (DBpedia)
- ✅ Standard vocabularies (Schema.org)
- ✅ Can be discovered and followed

---

## Analogy: Building a Website

| Concept | Web Analogy | RDF/Linked Data Analogy |
|---------|-------------|-------------------------|
| **Technology** | HTML | RDF |
| **Protocol** | HTTP | HTTP (same!) |
| **Methodology** | Web Standards | Linked Data Principles |
| **Result** | The Web | The Semantic Web / Web of Data |

- You can write HTML without following web standards (bad practices)
- You can use RDF without following Linked Data principles (not linked)
- **Linked Data = RDF + Best Practices + HTTP + URIs + Links**

---

## Related Technologies

### Semantic Web Technology Stack

```
┌──────────────────────────────────┐
│ OWL (Ontology Language)          │  ← Advanced reasoning
├──────────────────────────────────┤
│ RDFS (RDF Schema)                │  ← Basic vocabularies
├──────────────────────────────────┤
│ RDF (Data Model)                 │  ← Core triples
├──────────────────────────────────┤
│ SPARQL (Query Language)          │  ← Querying RDF
├──────────────────────────────────┤
│ Formats: Turtle, JSON-LD, etc.   │  ← Serializations
└──────────────────────────────────┘

All of these are USED BY Linked Data methodology
```

---

## For UTLX: What Does This Mean?

### 1. RDF Support = Support the data model

```kotlin
// RDF Triple representation
data class RdfTriple(
    val subject: String,    // URI
    val predicate: String,  // URI
    val object: RdfValue    // URI or Literal
)
```

### 2. Linked Data Support = Support the methodology

```utlx
%utlx 1.0
input json-ld    # JSON-LD is RDF serialization
output turtle    # Turtle is RDF serialization
---
// Transform while preserving Linked Data principles
{
  // Ensure URIs are HTTP URIs
  // Preserve links to external resources
  // Use standard vocabularies
}
```

### 3. Both Are Interconnected

**You can't have Linked Data without RDF**, but you can have RDF without Linked Data.

For UTLX:
- **RDF support** = Handle RDF formats (JSON-LD, Turtle, RDF/XML)
- **Linked Data support** = Validate/preserve Linked Data principles during transformations

---

## Summary Table

| Aspect | RDF | Linked Data |
|--------|-----|-------------|
| **Nature** | Technology/Standard | Methodology/Practice |
| **Defined By** | W3C Specification | Tim Berners-Lee's 4 Principles |
| **Scope** | Data model + formats | Best practices for publishing |
| **Can exist alone?** | Yes (internal use) | No (requires RDF) |
| **Primary Purpose** | Represent graph data | Publish interconnected web data |
| **UTL-X Role** | Format handler | Validation + transformation guidelines |

---

## Recommendation for UTL-X Documentation

When documenting, be clear about the distinction:

✅ **"UTL-X supports RDF formats"** - Accurate  
✅ **"UTL-X can transform Linked Data"** - Accurate  
✅ **"UTL-X preserves Linked Data principles"** - Advanced feature  
❌ **"UTL-X supports Linked Data as a format"** - Misleading (it's not a format)

**Better phrasing**:  
"UTL-X supports RDF-based formats (JSON-LD, Turtle, RDF/XML) enabling transformation of Linked Data while preserving semantic relationships."

**Key Insight**: **RDF is the foundation, Linked Data is how you use it on the web.**

---

# Part 3: JSON-LD and Standards Comparison

## Question: JSON-LD and standards like Schematron or FEEL, how can that be seen?

These are **completely different types of standards** serving different purposes:

| Standard | Category | Purpose | Layer |
|----------|----------|---------|-------|
| **JSON-LD** | Data Format + Semantics | Linked Data representation | Data Layer |
| **Schematron** | Validation Language | Business rules for XML | Validation Layer |
| **FEEL** | Expression Language | Decision logic & calculations | Logic Layer |

---

## Detailed Breakdown

### 1. JSON-LD (Data Format)

**Category**: Data serialization format with semantic extensions

**What it is**:
- JSON-based serialization of RDF (Linked Data)
- Adds semantic meaning to regular JSON
- W3C Standard since 2014

**Purpose**: Represent graph data in JSON

**Example**:
```json
{
  "@context": "https://schema.org",
  "@type": "Person",
  "name": "Alice",
  "knows": {
    "@id": "http://example.com/bob"
  }
}
```

**In UTLX**:
```utlx
input json-ld
output turtle
schema person.jsonld type:jsonld
```

---

### 2. Schematron (Validation Language)

**Category**: Rule-based validation language for XML

**What it is**:
- ISO standard (ISO/IEC 19757-3)
- Expresses validation rules XML Schema (XSD) cannot
- Uses XPath for complex business rules

**Purpose**: Validate business rules and context-dependent constraints

**Example**:
```xml
<sch:pattern>
  <sch:rule context="invoice">
    <sch:assert test="total = sum(lineItem/price)">
      Invoice total must equal sum of line items
    </sch:assert>
  </sch:rule>
</sch:pattern>
```

**What XSD Cannot Express but Schematron Can**:
- Cross-field validation (e.g., end date must be after start date)
- Conditional requirements (if type=X then field Y is required)
- Business rules (invoice total = sum of items)
- Context-dependent constraints

**In UTLX**:
```utlx
schema invoice.xsd type:xsd                    # Structure
schema invoice-rules.sch type:schematron      # Business rules
```

---

### 3. FEEL (Expression Language)

**Category**: Expression language for decision modeling

**What it is**:
- **F**riendly **E**nough **E**xpression **L**anguage
- Part of DMN (Decision Model and Notation) standard
- OMG standard for business decision logic

**Purpose**: Express business decisions and calculations in human-readable form

**Example**:
```feel
if applicant.age < 18
then "rejected"
else if applicant.creditScore >= 700
     then "approved"
     else "manual review"
```

**Comparison to Other Expression Languages**:

| Feature | FEEL | JavaScript | XPath |
|---------|------|-----------|-------|
| Domain | Decision modeling | General programming | XML querying |
| Readability | Very high (business users) | Medium | Medium |
| Standardized | Yes (OMG DMN) | Yes (ECMA) | Yes (W3C) |
| Side effects | No (pure functional) | Yes | No |

**In UTLX** (hypothetical):
```utlx
schema pricing.dmn type:dmn
---
{
  // Use DMN decision table with FEEL expressions
  price: evaluateDecision("PricingDecision", $input)
}
```

**Why FEEL could be valuable**:
- FEEL is designed specifically for decision modeling with human-readable syntax for business users, while maintaining expressive power for complex decision-making
- Standardized across DMN-compliant systems
- Rich built-in functions for dates, lists, calculations
- Side-effect free (referentially transparent)

---

## How They Relate: Orthogonal Concerns

These standards operate at **different layers** and are **complementary**:

```
┌─────────────────────────────────────┐
│ Logic Layer: FEEL                   │  ← Decision logic
├─────────────────────────────────────┤
│ Validation Layer: Schematron        │  ← Business rules
├─────────────────────────────────────┤
│ Structure Layer: XSD/JSON Schema    │  ← Data structure
├─────────────────────────────────────┤
│ Data Layer: JSON-LD/XML/CSV         │  ← Actual data
└─────────────────────────────────────┘
```

### Example: Complete Pipeline

```utlx
%utlx 1.0
input json-ld                              # Data format
schema person.jsonld type:jsonld           # Semantic validation
schema person.json type:jsch               # Structure validation
schema person-rules.sch type:schematron    # Business rules (if XML)
---
{
  // Use FEEL for decision logic (hypothetical)
  category: %feel{ 
    if age < 18 then "minor"
    else if age < 65 then "adult"
    else "senior"
  }
}
```

---

## Recommendation for UTLX

### Priority 1: JSON-LD (Data Format) ✅ Already Planned
**Status**: Discussed in previous analysis  
**Benefit**: Semantic web integration, knowledge graphs

### Priority 2: Schematron (Validation) ⚠️ Consider
**Status**: Not yet discussed  
**Benefit**: Express XML business rules XSD cannot  
**Use Case**: Complex validation scenarios

```utlx
schema contract.xsd type:xsd                    # Structure
schema contract-rules.sch type:schematron      # Business rules
```

### Priority 3: FEEL (Expression Language) 🤔 Interesting but...
**Status**: Would be a major addition  
**Benefit**: Industry-standard decision logic  
**Challenge**: UTLX already has its own expression syntax

**Key Question**: Should UTLX support FEEL expressions?

**Pros**:
- Standards-based (OMG DMN)
- Business-user friendly
- DMN ecosystem compatibility

**Cons**:
- Duplicate expression language
- Additional complexity
- UTLX syntax already powerful

**Possible Middle Ground**:
```utlx
// Native UTLX syntax
price: if ($input.quantity > 100) then $input.unitPrice * 0.9 else $input.unitPrice

// FEEL syntax (optional, for DMN compatibility)
price: %feel{ if quantity > 100 then unitPrice * 0.9 else unitPrice }
```

---

## Summary Comparison Table

| Standard | Category | UTLX Fit | Priority |
|----------|----------|----------|----------|
| **JSON-LD** | Data Format | Input/output format | ✅ High |
| **Schematron** | Validation | Validation layer | ⚠️ Medium |
| **FEEL** | Expression Language | Alternative syntax | 🤔 Low (duplication) |

**Key Insight**: These are orthogonal standards - you could theoretically use all three together:
1. **Data** in JSON-LD format
2. **Validated** with Schematron rules
3. **Processed** using FEEL expressions

But for UTLX, **JSON-LD is the most natural fit** as it's a data format, while FEEL would compete with UTLX's own expression language.

---

## Conclusion

The discussion covered three major areas:

1. **Linked Data Integration** - Adding JSON-LD support as a `jsonld` type identifier for semantic validation alongside structural validation
2. **RDF vs Linked Data** - Understanding that RDF is the technology while Linked Data is the methodology for publishing RDF on the web
3. **Related Standards** - Comparing JSON-LD (data format), Schematron (XML validation), and FEEL (decision logic) as complementary but orthogonal standards

### Next Steps for UTLX

**Immediate**:
- Implement JSON-LD support with `type:jsonld`
- Define clear RDF format handling

**Consider**:
- Schematron validation for complex XML business rules
- FEEL expression language as optional syntax for DMN compatibility

**Document**:
- Clear distinction between RDF support and Linked Data principles
- Usage patterns for semantic web integration

---

**Document Version**: 1.0  
**Last Updated**: November 7, 2025  
**Author**: Discussion analysis for UTL-X project  
**Status**: Technical architecture recommendations
