# Technical Analysis: Linked Data Integration in UTL-X
## Addressing Syntax Conflicts and Architectural Constraints

**Author Context**: Marcel A. Grauwen (UTL-X Project)  
**Version**: 1.0 Technical Analysis  
**Date**: November 2025

---

## Executive Summary

**Challenge**: Integrating JSON-LD (Linked Data) into UTL-X presents three architectural conflicts:

1. **@ Symbol Conflict**: UTLX uses `@` for XML attributes; JSON-LD uses `@context`, `@id`, `@type`
2. **USDL Schema Language**: How to represent Linked Data schemas in %USDL notation
3. **UDM Model**: How RDF triples map to the Universal Data Model

**Solution**: Treat JSON-LD as a **data format with semantic annotations**, not a separate schema type. Use standard `jsch` for structural validation, and provide UDM extensions for RDF semantics.

---

## Table of Contents

1. [The @ Symbol Conflict](#1-the--symbol-conflict)
2. [JSON-LD in the UDM Model](#2-json-ld-in-the-udm-model)
3. [USDL Representation](#3-usdl-representation)
4. [Proposed Solution Architecture](#4-proposed-solution-architecture)
5. [Implementation Strategy](#5-implementation-strategy)
6. [Usage Examples](#6-usage-examples)
7. [Comparison with Other Approaches](#7-comparison-with-other-approaches)
8. [Recommendations](#8-recommendations)

---

## 1. The @ Symbol Conflict

### 1.1 Current UTLX Usage of @

**In UTLX, `@` accesses XML attributes and JSON special properties**:

```utlx
// XML Attribute Access
$input.Order.@id                    // XML: <Order id="123">
$input.Customer.@type               // XML: <Customer type="premium">

// Works across formats
$input.Order.@id  →  XML: attribute, JSON: property named "@id"
```

**UTLX Syntax Rule**: `@` is a **path accessor**, not part of property names.

### 1.2 JSON-LD Usage of @

**JSON-LD uses `@` as property name prefixes**:

```json
{
  "@context": "https://schema.org",
  "@id": "https://example.com/person/123",
  "@type": "Person",
  "name": "John Doe"
}
```

**Property names**: `@context`, `@id`, `@type` (literal strings)

### 1.3 The Conflict

```utlx
// What does this mean in UTLX?
$input.@context

// Option A: Access property named "@context" (JSON-LD interpretation)
// Option B: Syntax error (@ expects attribute name after it)
// Option C: Access attribute "context" (UTLX XML interpretation)
```

**Problem**: Ambiguous! The `@` symbol has different semantics.

### 1.4 Resolution Strategy

**Treat `@` properties as regular properties with escaped names**:

```utlx
// Access JSON-LD special properties using bracket notation
$input["@context"]       // Access the @context property
$input["@id"]            // Access the @id property
$input["@type"]          // Access the @type property

// Or use helper functions
getContext($input)       // stdlib function
getId($input)            // stdlib function
getType($input)          // stdlib function

// Regular properties work normally
$input.name              // "John Doe"
$input.email             // "john@example.com"
```

**Key Insight**: JSON-LD `@` properties are **data properties**, not syntax.

---

## 2. JSON-LD in the UDM Model

### 2.1 Universal Data Model (UDM) Recap

**UDM Types** (from `udm_core.kt`):
```kotlin
sealed class UdmValue
data class UdmObject(val properties: Map<String, UdmValue>) : UdmValue()
data class UdmArray(val items: List<UdmValue>) : UdmValue()
data class UdmScalar(val value: Any, val type: ScalarType) : UdmValue()
object UdmNull : UdmValue()
```

**Format Conversion**:
```
JSON → UdmObject/UdmArray/UdmScalar → UTLX Processing → Output Format
```

### 2.2 JSON-LD as UDM

**JSON-LD is just JSON with semantic conventions**:

```json
{
  "@context": "https://schema.org",
  "@id": "https://example.com/person/123",
  "@type": "Person",
  "name": "John Doe",
  "knows": {
    "@id": "https://example.com/person/456",
    "name": "Jane Smith"
  }
}
```

**Maps to UDM**:
```kotlin
UdmObject(
    "@context" → UdmScalar("https://schema.org", STRING),
    "@id" → UdmScalar("https://example.com/person/123", STRING),
    "@type" → UdmScalar("Person", STRING),
    "name" → UdmScalar("John Doe", STRING),
    "knows" → UdmObject(
        "@id" → UdmScalar("https://example.com/person/456", STRING),
        "name" → UdmScalar("Jane Smith", STRING)
    )
)
```

**Key Point**: From UDM's perspective, `@context` is just another string property!

### 2.3 UDM Extensions for RDF Semantics

**Add semantic layer on top of UDM**:

```kotlin
// Extension to UdmValue for RDF semantics
data class UdmRdfNode(
    val iri: String?,                      // @id value
    val types: List<String>,               // @type values
    val properties: Map<String, UdmValue>, // Regular properties
    val context: RdfContext?               // Resolved @context
) : UdmValue()

// RDF Triple representation
data class RdfTriple(
    val subject: String,    // IRI
    val predicate: String,  // IRI
    val object: RdfValue    // IRI or Literal
)

sealed class RdfValue
data class RdfIri(val iri: String) : RdfValue()
data class RdfLiteral(val value: String, val type: String?, val lang: String?) : RdfValue()
```

**Conversion**:
```
JSON-LD File → JSON Parser → UdmObject → RdfConverter → UdmRdfNode → RDF Triples
```

### 2.4 UDM Design Decision

**Recommendation**: Keep UDM format-agnostic, add RDF layer as optional extension.

```kotlin
// In formats/json/src/main/kotlin/
class JsonLdParser : JsonParser() {
    
    override fun parse(input: InputStream): UdmValue {
        // Step 1: Parse as normal JSON
        val jsonValue = super.parse(input)
        
        // Step 2: If JSON-LD detected, add RDF annotations
        if (isJsonLd(jsonValue)) {
            return enrichWithRdfSemantics(jsonValue)
        }
        
        return jsonValue
    }
    
    private fun isJsonLd(value: UdmValue): Boolean {
        return value is UdmObject && value.properties.containsKey("@context")
    }
}
```

---

## 3. USDL Representation

### 3.1 Current USDL for JSON (jsch)

**USDL references external JSON Schema**:

```utlx
%utlx 1.0
input json
output xml
schema person-schema.json type:jsch
---
// Transformation
```

**person-schema.json** (JSON Schema):
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["name", "email"],
  "properties": {
    "name": {"type": "string"},
    "email": {"type": "string", "format": "email"}
  }
}
```

### 3.2 Challenge: JSON-LD Context is Not a Schema

**JSON-LD Context** defines semantic mappings, not structure:

```json
{
  "@context": {
    "@vocab": "http://schema.org/",
    "name": "http://schema.org/name",
    "email": "http://schema.org/email"
  }
}
```

**This is metadata, not validation rules!**

### 3.3 Two-Schema Approach

**Use BOTH structural and semantic schemas**:

```utlx
%utlx 1.0
input json
output rdf
schema person-structure.json type:jsch     # Structural validation
context person-context.jsonld type:jsonld  # Semantic context
---
// Transformation
```

**Key Distinction**:
- `schema` → Validates structure (JSON Schema)
- `context` → Defines semantics (JSON-LD Context)

### 3.4 USDL Extension: %context Directive

**Proposed USDL extension for semantic context**:

```utlx
%utlx 1.0
input json
output rdf
---
%context {
  vocab: "http://schema.org/"
  mappings: {
    "name": "http://schema.org/name"
    "email": "http://schema.org/email"
    "birthDate": {
      id: "http://schema.org/birthDate"
      type: "http://www.w3.org/2001/XMLSchema#date"
    }
  }
}
---
{
  // Transformation with semantic awareness
  person: {
    uri: getId($input),
    name: $input.name,
    email: $input.email
  }
}
```

**But**: This duplicates JSON-LD functionality. **Better approach**: Reference external context.

### 3.5 Recommended USDL Approach

**Keep it simple - reference external JSON-LD context**:

```utlx
%utlx 1.0
input json
output rdf
schema person-schema.json type:jsch                    # Structure
context https://schema.org/Person type:jsonld          # Semantics
---
// Or load context from input data
context $input["@context"] type:jsonld
---
// Transformation
```

**No new USDL syntax needed!** Just a new directive: `context`

---

## 4. Proposed Solution Architecture

### 4.1 Three-Layer Architecture

```
┌─────────────────────────────────────────────────┐
│ Layer 3: RDF/Linked Data (Optional)            │
│ - RDF triple extraction                         │
│ - SPARQL queries                                │
│ - Ontology reasoning                            │
└─────────────────────────────────────────────────┘
                    ↑
┌─────────────────────────────────────────────────┐
│ Layer 2: JSON-LD Awareness (Format Handler)    │
│ - Detect @context, @id, @type                   │
│ - Resolve contexts                              │
│ - Annotate UDM with RDF metadata                │
└─────────────────────────────────────────────────┘
                    ↑
┌─────────────────────────────────────────────────┐
│ Layer 1: Standard JSON (UDM Core)              │
│ - Parse JSON structure                          │
│ - Create UdmObject/UdmArray/UdmScalar          │
│ - Standard UTLX transformations                 │
└─────────────────────────────────────────────────┘
```

### 4.2 Format Handler Enhancement

**JSON parser with JSON-LD detection**:

```kotlin
// formats/json/src/main/kotlin/org/apache/utlx/formats/json/

class JsonFormatHandler : FormatHandler {
    
    override fun parse(input: InputStream, options: ParseOptions): UdmValue {
        val jsonValue = parseJson(input)
        
        // Check if JSON-LD
        if (options.enableJsonLd && hasJsonLdMarkers(jsonValue)) {
            return JsonLdHandler.parse(jsonValue, options.context)
        }
        
        return jsonValue
    }
    
    private fun hasJsonLdMarkers(value: UdmValue): Boolean {
        return value is UdmObject && 
               (value.properties.containsKey("@context") ||
                value.properties.containsKey("@id") ||
                value.properties.containsKey("@type"))
    }
}

class JsonLdHandler {
    fun parse(jsonValue: UdmValue, contextUrl: String?): UdmValue {
        // 1. Parse JSON normally
        // 2. Resolve @context
        // 3. Annotate with RDF metadata
        // 4. Return enhanced UdmValue
    }
}
```

### 4.3 UTLX Path Resolution

**Handle @ properties in paths**:

```utlx
// These should all work:
$input["@context"]              // Bracket notation (explicit)
$input."@context"               // Quoted property (explicit)
getContext($input)              // Helper function (recommended)

// Standard properties
$input.name                     // No conflict
$input.email                    // No conflict
```

**Parser Rule**: 
- `@` followed by identifier → XML attribute access
- Property name starts with `@` → use bracket notation or helper

### 4.4 Standard Library Extensions

**Add JSON-LD helper functions**:

```kotlin
// stdlib/src/main/kotlin/org/apache/utlx/stdlib/jsonld/

object JsonLdFunctions {
    
    @UtlxFunction
    fun getContext(obj: UdmObject): UdmValue? {
        return obj.properties["@context"]
    }
    
    @UtlxFunction
    fun getId(obj: UdmObject): String? {
        return (obj.properties["@id"] as? UdmScalar)?.value as? String
    }
    
    @UtlxFunction
    fun getType(obj: UdmObject): String? {
        return (obj.properties["@type"] as? UdmScalar)?.value as? String
    }
    
    @UtlxFunction
    fun getTypes(obj: UdmObject): List<String> {
        return when (val typeValue = obj.properties["@type"]) {
            is UdmScalar -> listOf(typeValue.value as String)
            is UdmArray -> typeValue.items.map { (it as UdmScalar).value as String }
            else -> emptyList()
        }
    }
    
    @UtlxFunction
    fun expandContext(obj: UdmObject, contextUrl: String): UdmObject {
        // Expand properties using JSON-LD context
        // Returns new UdmObject with expanded IRIs
    }
    
    @UtlxFunction
    fun toRdfTriples(obj: UdmObject): List<RdfTriple> {
        // Convert JSON-LD object to RDF triples
    }
}
```

**Usage in UTLX**:

```utlx
%utlx 1.0
input json
output json
---
{
  // Access JSON-LD properties
  entityId: getId($input),
  entityType: getType($input),
  contextUrl: getContext($input),
  
  // Regular properties
  name: $input.name,
  email: $input.email,
  
  // RDF operations
  triples: toRdfTriples($input) |> count()
}
```

---

## 5. Implementation Strategy

### 5.1 Phase 1: Basic JSON-LD Support (Weeks 1-4)

**Goal**: Parse and access JSON-LD files

**Deliverables**:
1. **JSON-LD Detection**: 
   - Detect `@context`, `@id`, `@type` in JSON
   - Flag as JSON-LD variant

2. **Path Access**:
   - Support bracket notation for `@` properties
   - Add helper functions to stdlib

3. **Documentation**:
   - How to access JSON-LD properties
   - Examples with Schema.org data

**Code**:
```kotlin
// formats/json/src/main/kotlin/.../JsonLdDetector.kt
object JsonLdDetector {
    fun isJsonLd(udmValue: UdmValue): Boolean {
        return udmValue is UdmObject && 
               udmValue.properties.keys.any { it.startsWith("@") }
    }
}
```

### 5.2 Phase 2: Context Resolution (Weeks 5-8)

**Goal**: Resolve JSON-LD contexts and expand properties

**Deliverables**:
1. **Context Loading**:
   - Load external contexts (URLs)
   - Cache contexts
   - Parse context definitions

2. **Property Expansion**:
   - Expand short names to full IRIs
   - Apply type coercion
   - Handle language tags

3. **UTLX Directive**:
   - Add `context` directive
   - Validate context URLs

**Code**:
```kotlin
// formats/json/src/main/kotlin/.../JsonLdContext.kt
data class JsonLdContext(
    val vocab: String?,
    val mappings: Map<String, TermDefinition>,
    val base: String?
)

data class TermDefinition(
    val iri: String,
    val type: String?,
    val container: String?,
    val language: String?
)

class ContextResolver {
    private val cache = mutableMapOf<String, JsonLdContext>()
    
    fun resolve(contextUrl: String): JsonLdContext {
        return cache.getOrPut(contextUrl) {
            loadAndParseContext(contextUrl)
        }
    }
}
```

### 5.3 Phase 3: RDF Triple Generation (Weeks 9-12)

**Goal**: Convert JSON-LD to RDF triples

**Deliverables**:
1. **Triple Extraction**:
   - Convert JSON-LD to RDF triples
   - Handle blank nodes
   - Process lists and containers

2. **RDF Output Formats**:
   - Turtle serialization
   - N-Triples serialization
   - RDF/XML serialization

3. **Query Support**:
   - SPARQL-like queries on JSON-LD
   - Graph traversal functions

**Code**:
```kotlin
// formats/rdf/src/main/kotlin/.../RdfConverter.kt
class RdfConverter(private val context: JsonLdContext) {
    
    fun toTriples(jsonLd: UdmObject): List<RdfTriple> {
        val subject = extractSubject(jsonLd)
        val triples = mutableListOf<RdfTriple>()
        
        jsonLd.properties.forEach { (key, value) ->
            if (!key.startsWith("@")) {
                val predicate = expandProperty(key)
                triples.addAll(valueToTriples(subject, predicate, value))
            }
        }
        
        return triples
    }
}
```

### 5.4 Phase 4: Ontology Integration (Weeks 13-16)

**Goal**: Full semantic web integration

**Deliverables**:
1. **RDFS/OWL Support**:
   - Load and reason with ontologies
   - Validate against ontologies
   - Infer new triples

2. **Schema.org Integration**:
   - Built-in Schema.org vocabulary
   - Validation against Schema.org
   - SEO-focused transformations

3. **Advanced Features**:
   - Framing (reshape JSON-LD)
   - Compaction (minimize context)
   - Flattening (normalize structure)

---

## 6. Usage Examples

### 6.1 Basic JSON-LD Access

**Input** (person.json):
```json
{
  "@context": "https://schema.org",
  "@id": "https://example.com/person/123",
  "@type": "Person",
  "name": "John Doe",
  "email": "john@example.com"
}
```

**Transformation**:
```utlx
%utlx 1.0
input json
output json
---
{
  // Access JSON-LD properties using helpers
  id: getId($input),
  type: getType($input),
  
  // Regular properties work normally
  displayName: $input.name,
  contact: $input.email,
  
  // Or use bracket notation
  context: $input["@context"]
}
```

**Output**:
```json
{
  "id": "https://example.com/person/123",
  "type": "Person",
  "displayName": "John Doe",
  "contact": "john@example.com",
  "context": "https://schema.org"
}
```

### 6.2 Converting JSON to JSON-LD

**Input** (plain-person.json):
```json
{
  "id": 123,
  "fullName": "John Doe",
  "emailAddress": "john@example.com",
  "dateOfBirth": "1990-05-15"
}
```

**Transformation**:
```utlx
%utlx 1.0
input json
output json
---
{
  "@context": "https://schema.org",
  "@type": "Person",
  "@id": "https://example.com/person/" + toString($input.id),
  "name": $input.fullName,
  "email": $input.emailAddress,
  "birthDate": $input.dateOfBirth
}
```

**Output** (person-linked.json):
```json
{
  "@context": "https://schema.org",
  "@type": "Person",
  "@id": "https://example.com/person/123",
  "name": "John Doe",
  "email": "john@example.com",
  "birthDate": "1990-05-15"
}
```

### 6.3 Extracting RDF Triples

**Input** (linked-person.json):
```json
{
  "@context": "https://schema.org",
  "@id": "https://example.com/person/123",
  "@type": "Person",
  "name": "John Doe",
  "knows": {
    "@id": "https://example.com/person/456",
    "name": "Jane Smith"
  }
}
```

**Transformation**:
```utlx
%utlx 1.0
input json
output turtle
context $input["@context"]
---
// Automatic conversion to RDF Turtle format
$input
```

**Output** (person.ttl):
```turtle
@prefix schema: <https://schema.org/> .
@prefix ex: <https://example.com/person/> .

ex:123 a schema:Person ;
    schema:name "John Doe" ;
    schema:knows ex:456 .

ex:456 schema:name "Jane Smith" .
```

### 6.4 Combining Multiple JSON-LD Sources

**Transformation**:
```utlx
%utlx 1.0
input json as people from "people.jsonld"
input json as orgs from "organizations.jsonld"
output json
---
{
  "@context": "https://schema.org",
  "@graph": [
    // Include all people
    ...$people["@graph"],
    
    // Include all organizations
    ...$orgs["@graph"],
    
    // Add relationships
    {
      "@type": "Organization",
      "@id": "https://example.com/org/acme",
      "employee": $people["@graph"] 
        |> filter(p => getType(p) == "Person")
        |> map(p => ({ "@id": getId(p) }))
    }
  ]
}
```

### 6.5 Schema.org SEO Generation

**Input** (product.json):
```json
{
  "name": "Premium Widget",
  "description": "A high-quality widget",
  "price": 99.99,
  "currency": "USD",
  "imageUrl": "https://example.com/widget.jpg"
}
```

**Transformation**:
```utlx
%utlx 1.0
input json
output json
---
{
  "@context": "https://schema.org",
  "@type": "Product",
  "name": $input.name,
  "description": $input.description,
  "image": $input.imageUrl,
  "offers": {
    "@type": "Offer",
    "price": toString($input.price),
    "priceCurrency": $input.currency,
    "availability": "https://schema.org/InStock"
  }
}
```

---

## 7. Comparison with Other Approaches

### 7.1 Option A: Special JSON-LD Schema Type

**Approach**: Create `jsonld` as separate schema type

```utlx
schema person.jsonld type:jsonld
```

**Problems**:
- ❌ JSON-LD context is NOT a validation schema
- ❌ Conflates semantics with structure
- ❌ Doesn't solve @ symbol conflict
- ❌ Creates confusion about what "schema" means

### 7.2 Option B: Escape @ in Syntax

**Approach**: Allow `@@` or `\@` for literal @ access

```utlx
$input.@@context    // Access @context
$input.@id          // Access @id (but conflicts with XML)
```

**Problems**:
- ❌ Breaks existing @ semantics
- ❌ Confusing for users
- ❌ Not backward compatible

### 7.3 Option C: Recommended - Helper Functions + Context Directive ✅

**Approach**: Keep @ as-is, use helpers and bracket notation

```utlx
getId($input)              // Helper function
$input["@context"]         // Bracket notation
context $input["@context"] // New directive for semantics
```

**Advantages**:
- ✅ No syntax changes needed
- ✅ Clear separation: structure vs semantics
- ✅ Backward compatible
- ✅ Works with existing UDM model

---

## 8. Recommendations

### 8.1 Core Recommendation

**Treat JSON-LD as a JSON variant with semantic annotations, not a separate format**

1. **No new schema type** - Use `jsch` for structure validation
2. **Add `context` directive** - For semantic context (optional)
3. **Use helper functions** - For accessing @ properties
4. **Extend UDM minimally** - Add RDF annotations as metadata, not core types

### 8.2 Implementation Checklist

**Phase 1 (Essential)**:
- [ ] JSON-LD detection in JSON parser
- [ ] Helper functions: `getId()`, `getType()`, `getContext()`
- [ ] Bracket notation for @ properties
- [ ] Documentation and examples

**Phase 2 (Important)**:
- [ ] Context resolution (load external contexts)
- [ ] `context` directive in UTLX
- [ ] Property expansion to IRIs
- [ ] Caching for contexts

**Phase 3 (Advanced)**:
- [ ] RDF triple extraction
- [ ] Output formats: Turtle, N-Triples, RDF/XML
- [ ] Graph traversal functions
- [ ] SPARQL-like queries

**Phase 4 (Future)**:
- [ ] Ontology reasoning
- [ ] Schema.org built-in support
- [ ] JSON-LD framing/compaction
- [ ] SHACL validation

### 8.3 USDL Schema Approach

**Recommended pattern for JSON-LD files**:

```utlx
%utlx 1.0
input json
output rdf
schema person-schema.json type:jsch        # Structure (optional)
context https://schema.org/Person          # Semantics (optional)
---
// Transformation
```

**Key points**:
- `schema` validates JSON structure
- `context` provides RDF semantics
- Both are optional (JSON-LD can be self-describing)
- No new USDL syntax needed

### 8.4 Final Architecture

```
┌────────────────────────────────────────────────┐
│ UTLX Transformation Layer                      │
│ - Helper functions: getId(), getType()         │
│ - Bracket notation: $input["@context"]         │
│ - Standard transformations                     │
└────────────────────────────────────────────────┘
                      ↑
┌────────────────────────────────────────────────┐
│ JSON-LD Format Handler (Optional Enhancement)  │
│ - Detect @context, @id, @type                  │
│ - Resolve contexts                             │
│ - Annotate UDM with RDF metadata               │
└────────────────────────────────────────────────┘
                      ↑
┌────────────────────────────────────────────────┐
│ Standard JSON Parser + UDM                     │
│ - Parse JSON structure                         │
│ - Create UdmObject/UdmArray/UdmScalar         │
│ - @ properties are just string keys            │
└────────────────────────────────────────────────┘
```

---

## Appendix A: Syntax Comparison

### Current UTLX Syntax (XML-focused)

```utlx
$input.Order.@id          // XML attribute "id"
$input.Customer.@type     // XML attribute "type"
```

### JSON-LD Property Access (Recommended)

```utlx
// Use helper functions (clean, recommended)
getId($input.Order)
getType($input.Customer)

// Or bracket notation (explicit)
$input.Order["@id"]
$input.Customer["@type"]

// Regular properties (no change)
$input.Order.total
$input.Customer.name
```

### Why This Works

**In XML**: `@id` means "the id attribute"  
**In JSON-LD**: `@id` is a property name containing the @ character  
**Solution**: Different access methods for different semantic meanings

---

## Appendix B: UDM Type Extensions

### Core UDM (No Changes)

```kotlin
sealed class UdmValue
data class UdmObject(val properties: Map<String, UdmValue>) : UdmValue()
data class UdmArray(val items: List<UdmValue>) : UdmValue()
data class UdmScalar(val value: Any, val type: ScalarType) : UdmValue()
object UdmNull : UdmValue()
```

### Optional RDF Extensions (Separate Module)

```kotlin
// modules/rdf/src/main/kotlin/.../RdfAnnotations.kt

// Annotation attached to UdmValue instances
data class RdfAnnotation(
    val subject: String?,              // Resolved @id
    val types: List<String>,           // Resolved @type
    val context: ResolvedContext?,     // Expanded context
    val triples: List<RdfTriple>?      // Extracted triples
)

// Extension property
var UdmValue.rdfAnnotation: RdfAnnotation?
    get() = // retrieve from metadata map
    set(value) = // store in metadata map

// Usage
val person: UdmObject = parseJsonLd("person.jsonld")
val personId = person.rdfAnnotation?.subject  // Get RDF subject IRI
```

---

**Document Version**: 1.0  
**Last Updated**: November 7, 2025  
**Author**: Technical analysis for UTL-X project by Marcel A. Grauwen  
**Status**: Architecture recommendation for Linked Data integration
