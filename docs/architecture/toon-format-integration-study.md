# TOON Format Integration Study for UTLX

**Document Type:** Architecture Study
**Status:** Analysis Complete
**Date:** 2025-11-12
**Author:** UTL-X Architecture Team

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [TOON Specification Overview](#toon-specification-overview)
3. [UTLX Format Architecture Context](#utlx-format-architecture-context)
4. [Comparative Analysis](#comparative-analysis)
5. [Technical Feasibility](#technical-feasibility)
6. [Use Case Analysis](#use-case-analysis)
7. [Implementation Requirements](#implementation-requirements)
8. [Pros and Cons Analysis](#pros-and-cons-analysis)
9. [Recommendations](#recommendations)
10. [Technical Implementation Guide](#technical-implementation-guide)
11. [Conclusion](#conclusion)

---

## Executive Summary

### Quick Decision

**TOON is technically feasible but strategically questionable for UTLX.**

**Recommendation:** Implement as **experimental/plugin format** to validate user demand before committing to full support.

### Key Findings

| Aspect | Assessment | Score |
|--------|------------|-------|
| **Technical Feasibility** | ✅ Excellent | 9/10 |
| **UDM Mapping** | ✅ Clean, straightforward | 9/10 |
| **Implementation Cost** | ✅ Reasonable (~2 weeks) | 7/10 |
| **Unique Value** | ⚠️ Limited | 4/10 |
| **Ecosystem Support** | ❌ None | 0/10 |
| **User Demand** | ❌ Unknown | 1/10 |
| **Maintenance Burden** | ⚠️ Moderate | 5/10 |
| **Strategic Fit** | ⚠️ Unclear | 5/10 |
| **Overall Score** | ⚠️ Conditional | **5/10** |

### Summary

TOON offers a cleaner syntax than JSON and more explicit structure than YAML, but:
- **No ecosystem** (parsers, tools, IDE support)
- **Heavy overlap** with existing JSON/YAML capabilities
- **No proven user demand** for this specific format
- Would require **custom parser maintenance**

**Best path forward:** Implement as experimental feature or plugin, gather user feedback, then decide whether to promote to stable or deprecate.

---

## TOON Specification Overview

### What is TOON?

**TOON (Tree Object Oriented Notation)** is a lightweight, human-readable data serialization format designed to combine the best aspects of JSON and YAML while avoiding their pain points.

**Design Goals:**
- Human-friendly syntax (better than JSON)
- Explicit structure (clearer than YAML)
- Comment support (unlike JSON)
- Optional commas (less syntax noise)
- Multi-line strings (clean representation)

### Core Syntax

```toon
# This is a comment

# Simple object
person: {
  name: "Alice Johnson"
  age: 30
  active: true
}

# Array
colors: ["red", "green", "blue"]

# Nested structure
config: {
  server: {
    host: "0.0.0.0"
    port: 8080
    # Optional commas
    tls: false
  }

  database: {
    url: "postgresql://localhost/mydb"
    pool: {
      min: 5
      max: 20
    }
  }
}

# Multi-line strings
description: |
  This is a multi-line string.
  Indentation is preserved.
  No escape sequences needed.

# References (anchors and aliases)
defaults: &default_server {
  timeout: 30
  retry: 3
}

prod_server: {
  <<: *default_server
  host: "prod.example.com"
  port: 443
}
```

### Data Model

TOON supports the following types:

| Type | Description | Example |
|------|-------------|---------|
| **String** | Text values (quoted) | `"hello"` |
| **Number** | Integers and floats | `42`, `3.14` |
| **Boolean** | True/false | `true`, `false` |
| **Null** | Null value | `null` |
| **Object** | Key-value pairs | `{name: "Alice"}` |
| **Array** | Ordered collection | `[1, 2, 3]` |
| **Multi-line String** | Pipe notation | <code>&#124;<br>  Line 1<br>  Line 2</code> |
| **Reference** | Anchors and aliases | `&anchor`, `*alias` |

### Grammar (EBNF)

```ebnf
document    ::= value*
value       ::= primitive | object | array | reference | comment
primitive   ::= string | number | boolean | null
object      ::= '{' (assignment COMMA?)* '}'
array       ::= '[' (value (COMMA value)*)? ']'
assignment  ::= key ':' value
key         ::= identifier | string
reference   ::= anchor | alias | merge
anchor      ::= '&' identifier value
alias       ::= '*' identifier
merge       ::= '<<:' alias
comment     ::= '#' [^\n]*
```

### Key Features

**1. Comments**
```toon
# Single-line comments supported
config: {
  # Inline comments too
  debug: true  # End-of-line comments
}
```

**2. Optional Commas**
```toon
# Both valid:
person: {name: "Alice", age: 30}
person: {name: "Alice" age: 30}
```

**3. Multi-line Strings**
```toon
sql: |
  SELECT *
  FROM users
  WHERE active = true
  ORDER BY created_at DESC
```

**4. References and Reuse**
```toon
common: &base {
  version: "1.0"
  author: "Team"
}

app1: {
  <<: *base
  name: "App1"
}

app2: {
  <<: *base
  name: "App2"
}
```

---

## UTLX Format Architecture Context

### Current Format Landscape

UTLX currently supports **8 formats** organized into two tiers:

#### Tier 1: Instance/Data Formats
Formats that represent actual data instances:

| Format | Extension | Use Case | Parser Location |
|--------|-----------|----------|-----------------|
| **JSON** | `.json` | Web APIs, configs | `/formats/json/` |
| **XML** | `.xml` | Enterprise, SOAP | `/formats/xml/` |
| **CSV** | `.csv` | Tabular data | `/formats/csv/` |
| **YAML** | `.yaml` | Configs, Kubernetes | `/formats/yaml/` |

#### Tier 2: Schema Formats
Formats that define data structure/validation:

| Format | Extension | Use Case | Parser Location |
|--------|-----------|----------|-----------------|
| **JSON Schema** | `.jsch` | JSON validation | `/formats/jsch/` |
| **XSD** | `.xsd` | XML validation | `/formats/xsd/` |
| **Avro Schema** | `.avsc` | Avro data | `/formats/avro/` |
| **Protobuf** | `.proto` | Protocol Buffers | `/formats/protobuf/` |

### Universal Data Model (UDM)

All formats in UTLX parse to and serialize from the **Universal Data Model (UDM)** - a unified internal representation.

```kotlin
sealed class UDM {
    // Scalar values (primitives)
    data class Scalar(val value: Any?) : UDM()

    // Ordered collection
    data class Array(val elements: List<UDM>) : UDM()

    // Key-value structure
    data class Object(
        val properties: Map<String, UDM>,
        val attributes: Map<String, String> = emptyMap(),
        val name: String? = null,
        val metadata: Map<String, String> = emptyMap()
    ) : UDM()

    // Temporal types
    data class DateTime(val instant: Instant) : UDM()
    data class Date(val date: LocalDate) : UDM()
    data class LocalDateTime(val dateTime: java.time.LocalDateTime) : UDM()
    data class Time(val time: LocalTime) : UDM()

    // Binary data
    data class Binary(val data: ByteArray, val encoding: String?) : UDM()

    // Functions (for transformations)
    data class Lambda(val params: List<String>, val body: Any) : UDM()
}
```

**UDM Characteristics:**
- Immutable data structures
- Rich type system beyond JSON (dates, binary, functions)
- Metadata preservation (source file, line numbers, encoding)
- Distinction between attributes and properties (for XML semantics)

### Format Integration Pattern

Each format follows this pattern:

```
Input Format (JSON/XML/CSV/etc)
    ↓
  Parser
    ↓
   UDM (Universal Data Model)
    ↓
 Serializer
    ↓
Output Format (JSON/XML/CSV/etc)
```

**Example implementations:**

```kotlin
// JSON Parser
class JSONParser(private val source: String) {
    fun parse(): UDM {
        // Parse JSON → UDM
    }
}

// JSON Serializer
class JSONSerializer(private val prettyPrint: Boolean = true) {
    fun serialize(udm: UDM): String {
        // Serialize UDM → JSON
    }
}
```

### How TOON Would Fit

TOON would be a **Tier 1 data format**:

```
formats/
├── json/          (Tier 1)
├── xml/           (Tier 1)
├── csv/           (Tier 1)
├── yaml/          (Tier 1)
├── toon/          (Tier 1) ← NEW
├── jsch/          (Tier 2)
├── xsd/           (Tier 2)
├── avro/          (Tier 2)
└── protobuf/      (Tier 2)
```

**Usage in UTLX:**

```bash
# Parse TOON to UDM
utlx udm export --format toon --input config.toon

# Transform TOON → JSON
utlx transform --input config.toon --output result.json transform.utlx

# Convert JSON → TOON
utlx udm import --format toon --input data.json --output data.toon
```

---

## Comparative Analysis

### Feature Comparison Matrix

| Feature | TOON | JSON | YAML | XML | Winner |
|---------|------|------|------|-----|--------|
| **Human Readable** | ✅ Excellent | ✅ Good | ✅ Excellent | ⚠️ Verbose | TOON/YAML |
| **Explicit Structure** | ✅ Braces | ✅ Braces | ❌ Indentation | ✅ Tags | TOON/JSON |
| **Comments** | ✅ Yes (`#`) | ❌ No | ✅ Yes (`#`) | ✅ Yes (`<!-- -->`) | TOON/YAML/XML |
| **Optional Commas** | ✅ Yes | ❌ Required | ✅ N/A | ✅ N/A | TOON |
| **Multi-line Strings** | ✅ Pipe (`\|`) | ⚠️ Escaped | ✅ Multiple styles | ✅ CDATA | TOON/YAML |
| **References/Anchors** | ✅ Yes (`&`, `*`) | ❌ No | ✅ Yes (`&`, `*`) | ⚠️ Limited (ID/IDREF) | TOON/YAML |
| **Type System** | ⚠️ Basic | ⚠️ Basic | ⚠️ Type tags | ⚠️ Basic | Tie |
| **Schema Support** | ❌ Proposed | ✅ JSON Schema | ⚠️ Limited | ✅ XSD | JSON/XML |
| **Binary Format** | ❌ No | ❌ No | ❌ No | ❌ No | Tie |
| **Ecosystem Size** | ❌ None | ✅ Massive | ✅ Large | ✅ Mature | JSON |
| **Parser Libraries** | ❌ None | ✅ Every language | ✅ Most languages | ✅ Every language | JSON/XML |
| **Standard Body** | ❌ None | ✅ ECMA-404 | ⚠️ Informal | ✅ W3C | JSON/XML |
| **IDE Support** | ❌ None | ✅ Universal | ✅ Good | ✅ Universal | JSON/XML |
| **Validation Tools** | ❌ None | ✅ Many | ⚠️ Limited | ✅ Many | JSON/XML |
| **Learning Curve** | ✅ Low | ✅ Low | ⚠️ Medium | ⚠️ Medium | TOON/JSON |
| **Config Files** | ✅ Good fit | ⚠️ No comments | ✅ Standard | ❌ Verbose | TOON/YAML |
| **API Responses** | ⚠️ Uncommon | ✅ Standard | ❌ Rare | ⚠️ Legacy | JSON |
| **Data Interchange** | ❌ No adoption | ✅ Universal | ⚠️ Growing | ✅ Enterprise | JSON |

### Unique TOON Advantages

**1. Better than JSON:**
- ✅ Comments support (JSON has none)
- ✅ Optional commas (less syntax noise)
- ✅ Multi-line strings (no escape sequences)
- ✅ Reference system (no repetition)

**2. Better than YAML:**
- ✅ Explicit braces (not indentation-dependent)
- ✅ Simpler parser (fewer edge cases)
- ✅ Clearer token boundaries
- ✅ Less "magic" (no implicit type coercion surprises)

**3. Better than XML:**
- ✅ Minimal syntax (no closing tags)
- ✅ More concise (less boilerplate)
- ✅ Easier to read (less visual noise)
- ✅ Faster to type (less redundancy)

### Where TOON Overlaps (Problems)

**1. JSON's territory:**
- Data interchange (but JSON is universal standard)
- API responses (but JSON has ecosystem lock-in)
- Configuration (but JSON + comments extensions exist)

**2. YAML's territory:**
- Configuration files (YAML is de facto standard for K8s, CI/CD)
- Human-editable data (YAML has massive adoption)
- Complex nested structures (YAML has mature tooling)

**3. Positioned as "better middle ground":**
- More explicit than YAML (but YAML users like implicit)
- More flexible than JSON (but JSON users value strictness)
- Cleaner than both (but no ecosystem to leverage)

### Syntax Comparison Examples

#### Simple Configuration

**TOON:**
```toon
server: {
  host: "0.0.0.0"
  port: 8080
  tls: false
}
```

**JSON:**
```json
{
  "server": {
    "host": "0.0.0.0",
    "port": 8080,
    "tls": false
  }
}
```

**YAML:**
```yaml
server:
  host: "0.0.0.0"
  port: 8080
  tls: false
```

**Winner:** YAML (most concise) / TOON (good balance)

#### With Comments

**TOON:**
```toon
# Production configuration
server: {
  host: "0.0.0.0"  # Bind to all interfaces
  port: 8080       # Default HTTP port
}
```

**JSON:**
```json
// NOT VALID JSON - no comments!
{
  "server": {
    "host": "0.0.0.0",
    "port": 8080
  }
}
```

**YAML:**
```yaml
# Production configuration
server:
  host: "0.0.0.0"  # Bind to all interfaces
  port: 8080       # Default HTTP port
```

**Winner:** TOON/YAML (both support comments)

#### Multi-line Strings

**TOON:**
```toon
description: |
  This is a long description
  spanning multiple lines.
  No escape sequences needed!
```

**JSON:**
```json
{
  "description": "This is a long description\nspanning multiple lines.\nNo escape sequences needed!"
}
```

**YAML:**
```yaml
description: |
  This is a long description
  spanning multiple lines.
  No escape sequences needed!
```

**Winner:** TOON/YAML (clean multi-line syntax)

#### References and Reuse

**TOON:**
```toon
defaults: &base {
  timeout: 30
  retry: 3
}

server1: {
  <<: *base
  host: "server1.com"
}

server2: {
  <<: *base
  host: "server2.com"
}
```

**JSON:**
```json
// NO REFERENCE SUPPORT - must duplicate
{
  "server1": {
    "timeout": 30,
    "retry": 3,
    "host": "server1.com"
  },
  "server2": {
    "timeout": 30,
    "retry": 3,
    "host": "server2.com"
  }
}
```

**YAML:**
```yaml
defaults: &base
  timeout: 30
  retry: 3

server1:
  <<: *base
  host: server1.com

server2:
  <<: *base
  host: server2.com
```

**Winner:** TOON/YAML (both have references)

---

## Technical Feasibility

### 5.1 TOON → UDM Mapping

**Assessment: ✅ Excellent - Clean, straightforward mapping**

TOON's data model maps directly to UDM with minimal impedance:

| TOON Type | UDM Type | Mapping Strategy | Example |
|-----------|----------|------------------|---------|
| **String** | `UDM.Scalar(String)` | Direct copy | `"hello"` → `Scalar("hello")` |
| **Number** | `UDM.Scalar(Double)` | Parse as Double | `42` → `Scalar(42.0)` |
| **Boolean** | `UDM.Scalar(Boolean)` | Direct copy | `true` → `Scalar(true)` |
| **Null** | `UDM.Scalar(null)` | Direct copy | `null` → `Scalar(null)` |
| **Object** | `UDM.Object` | Map to properties | `{a: 1}` → `Object(props={"a": Scalar(1.0)})` |
| **Array** | `UDM.Array` | Map to elements | `[1, 2]` → `Array([Scalar(1.0), Scalar(2.0)])` |
| **Multi-line** | `UDM.Scalar(String)` | Preserve newlines | <code>&#124;<br>  Line1<br>  Line2</code> → `Scalar("Line1\nLine2")` |
| **Reference** | Resolved UDM | Inline at parse time | `*alias` → (copy of anchored value) |

#### Mapping Examples

**Example 1: Simple Object**

```toon
person: {
  name: "Alice"
  age: 30
  active: true
}
```

Maps to:

```kotlin
UDM.Object(
  properties = mapOf(
    "person" to UDM.Object(
      properties = mapOf(
        "name" to UDM.Scalar("Alice"),
        "age" to UDM.Scalar(30.0),
        "active" to UDM.Scalar(true)
      )
    )
  )
)
```

**Example 2: Nested Arrays and Objects**

```toon
users: [
  {name: "Alice", age: 30}
  {name: "Bob", age: 25}
]
```

Maps to:

```kotlin
UDM.Object(
  properties = mapOf(
    "users" to UDM.Array(
      elements = listOf(
        UDM.Object(properties = mapOf(
          "name" to UDM.Scalar("Alice"),
          "age" to UDM.Scalar(30.0)
        )),
        UDM.Object(properties = mapOf(
          "name" to UDM.Scalar("Bob"),
          "age" to UDM.Scalar(25.0)
        ))
      )
    )
  )
)
```

**Example 3: References (Resolved)**

```toon
defaults: &base {
  timeout: 30
}

config: {
  <<: *base
  host: "localhost"
}
```

Maps to (after reference resolution):

```kotlin
UDM.Object(
  properties = mapOf(
    "defaults" to UDM.Object(
      properties = mapOf("timeout" to UDM.Scalar(30.0))
    ),
    "config" to UDM.Object(
      properties = mapOf(
        "timeout" to UDM.Scalar(30.0),  // Merged from *base
        "host" to UDM.Scalar("localhost")
      )
    )
  )
)
```

#### Mapping Challenges

**1. References Must Be Resolved**
- TOON allows forward references: `*alias` before `&alias` definition
- Need two-pass parsing: collect anchors, then resolve aliases
- Circular references must be detected and rejected

**2. No Native Date/Time Types**
- TOON has no ISO date literals (unlike some YAML parsers)
- Dates would parse as strings: `"2025-11-12"` → `Scalar("2025-11-12")`
- Downstream processing could detect ISO format and convert

**3. Comments Are Lost**
- Comments don't map to UDM (no comment preservation)
- Accept as limitation (same as JSON)

**Overall Feasibility: 9/10** - Very straightforward, minimal edge cases

### 5.2 UDM → TOON Serialization

**Assessment: ⚠️ Good with information loss**

UDM can serialize to TOON, but with limitations on UDM-specific features:

#### What Serializes Cleanly

| UDM Type | TOON Representation | Fidelity |
|----------|---------------------|----------|
| `Scalar(String)` | String | ✅ Perfect |
| `Scalar(Number)` | Number | ✅ Perfect |
| `Scalar(Boolean)` | Boolean | ✅ Perfect |
| `Scalar(null)` | `null` | ✅ Perfect |
| `Array` | Array `[...]` | ✅ Perfect |
| `Object` (simple) | Object `{...}` | ✅ Perfect |

#### What Loses Information

| UDM Type | TOON Limitation | Workaround |
|----------|-----------------|------------|
| **Attributes** | No concept of attributes | Merge into properties with `@` prefix |
| **Element Name** | Objects have no names | Store as `@name` property |
| **Metadata** | No metadata support | Lose or add as comment |
| **DateTime** | No temporal types | Serialize as ISO string |
| **Date** | No temporal types | Serialize as ISO string |
| **Binary** | No binary representation | Base64 encode as string |
| **Lambda** | No function support | Cannot serialize (error) |

#### Serialization Strategies

**Strategy 1: Lossy Serialization (for data interchange)**

Accept information loss, serialize UDM to basic TOON:

```kotlin
// UDM with attributes
UDM.Object(
  name = "Customer",
  attributes = mapOf("id" to "CUST-123"),
  properties = mapOf(
    "name" to UDM.Scalar("Alice"),
    "age" to UDM.Scalar(30.0)
  )
)
```

Serializes to:

```toon
# Attributes merged as properties
customer: {
  @id: "CUST-123"
  @name: "Customer"
  name: "Alice"
  age: 30
}
```

**Strategy 2: Enhanced TOON (preserve UDM semantics)**

Extend TOON syntax (non-standard):

```toon
# Extended syntax for UDM metadata
@Object(name="Customer") {
  @attributes: {
    id: "CUST-123"
  }
  @properties: {
    name: "Alice"
    age: 30
  }
}
```

This preserves full UDM structure but requires custom TOON parser.

**Strategy 3: Metadata Comments**

Use comments to preserve metadata:

```toon
# @name: Customer
# @id: CUST-123
customer: {
  name: "Alice"
  age: 30
}
```

Metadata preserved for human readers but lost to parser.

#### Recommended Approach

For UTLX integration, use **Strategy 1 (Lossy)** for simplicity:
- Merge attributes into properties with `@` prefix
- Serialize dates as ISO strings
- Reject Binary and Lambda types (unsupported)
- Log warning when information is lost

**Serialization Fidelity: 7/10** - Acceptable for most use cases

### 5.3 Parser Implementation

**No existing TOON parser in any language** - must implement from scratch.

#### Implementation Options

**Option 1: ANTLR4 Grammar**

Pros:
- ✅ Formal grammar definition
- ✅ Automatic lexer/parser generation
- ✅ Good error messages
- ✅ Cross-language support

Cons:
- ⚠️ ANTLR4 dependency
- ⚠️ Generated code overhead
- ⚠️ Learning curve

**Option 2: Hand-Written Recursive Descent**

Pros:
- ✅ Full control over parsing
- ✅ No external dependencies
- ✅ Easier debugging
- ✅ Smaller code footprint

Cons:
- ⚠️ More implementation work
- ⚠️ Manual error handling

**Recommended:** Hand-written parser (simpler for TOON's straightforward grammar)

#### Parser Complexity Estimate

Comparing to existing UTLX parsers:

| Parser | Lines of Code | Complexity | Pattern |
|--------|---------------|------------|---------|
| **JSON** | ~300 LOC | Low | Hand-written |
| **CSV** | ~290 LOC | Low | Hand-written |
| **YAML** | External lib | High | SnakeYAML |
| **XML** | External lib | High | Standard lib |
| **TOON** | ~350 LOC (est) | Low-Medium | Hand-written |

TOON is similar complexity to JSON but with added features:
- Comments (simple to skip)
- References (need anchor map)
- Multi-line strings (moderate complexity)
- Optional commas (simple lookahead)

**Complexity Assessment: 6/10** - Moderate, but manageable

#### Estimated Implementation Effort

| Task | Time Estimate | Notes |
|------|---------------|-------|
| **Tokenizer/Lexer** | 1-2 days | Recognize tokens, handle comments |
| **Parser Core** | 2-3 days | Recursive descent, build UDM |
| **Reference Resolution** | 1 day | Anchor collection, alias expansion |
| **Error Handling** | 1 day | Line/column tracking, messages |
| **Serializer** | 2-3 days | UDM → TOON with formatting |
| **Unit Tests** | 2-3 days | Parser tests, serializer tests |
| **Round-trip Tests** | 1 day | Ensure parse(serialize(x)) ≈ x |
| **Documentation** | 1 day | Format guide, examples |
| **Integration** | 1 day | Register in CLI, format detection |
| **Total** | **12-17 days** | **~2-3 weeks** |

### 5.4 Dependencies

**No external dependencies needed** - can implement pure Kotlin parser.

Optional dependencies:
- ANTLR4 runtime (if using generated parser)
- Testing: JUnit, Kotest (already in project)

---

## Use Case Analysis

### 6.1 What Problems Does TOON Solve?

#### Problem 1: JSON Lacks Comments

**Current Pain:**
```json
{
  "server": {
    "port": 8080,
    "timeout": 30
  }
}
```

Can't document why port is 8080 or what timeout means.

**TOON Solution:**
```toon
server: {
  port: 8080      # Standard HTTP port
  timeout: 30     # Socket timeout in seconds
}
```

**BUT:** YAML already solves this. JSONC (JSON with Comments) exists.

#### Problem 2: YAML's Indentation Sensitivity

**Current Pain:**
```yaml
server:
  database:
    host: localhost
    port: 5432
  cache:
    host: localhost  # Wrong indentation = parsing error
   port: 6379
```

**TOON Solution:**
```toon
server: {
  database: {
    host: "localhost"
    port: 5432
  }
  cache: {
    host: "localhost"  # Indentation doesn't matter
    port: 6379
  }
}
```

**BUT:** Most users already know YAML. YAML tools detect indentation errors.

#### Problem 3: Configuration File Clutter

**JSON - Too much syntax:**
```json
{
  "apps": [
    {"name": "app1", "port": 8080},
    {"name": "app2", "port": 8081}
  ]
}
```

**YAML - Not explicit enough:**
```yaml
apps:
  - name: app1
    port: 8080
  - name: app2
    port: 8081
```

**TOON - Middle ground:**
```toon
apps: [
  {name: "app1", port: 8080}
  {name: "app2", port: 8081}
]
```

**Value:** Cleaner than JSON, more explicit than YAML

#### Problem 4: Data Reuse Without Tooling

**JSON - Must duplicate:**
```json
{
  "dev": {"host": "dev.local", "timeout": 30, "retry": 3},
  "prod": {"host": "prod.com", "timeout": 30, "retry": 3}
}
```

**TOON - References:**
```toon
common: &base {
  timeout: 30
  retry: 3
}

dev: {
  <<: *base
  host: "dev.local"
}

prod: {
  <<: *base
  host: "prod.com"
}
```

**BUT:** YAML has same feature. Template tools exist for JSON.

### 6.2 TOON Tier Classification

**TOON = Tier 1 (Data/Instance Format)**

Rationale:
- ✅ Represents actual data values
- ✅ Not a schema language
- ✅ Not a validation framework
- ✅ Not a transformation language

Comparison:
- **Like:** JSON, YAML, CSV, XML
- **Unlike:** JSON Schema, XSD (schema definitions)

### 6.3 Use Case Scenarios

#### Scenario 1: Configuration Files

**Current:** Use YAML
```yaml
# config.yaml
database:
  url: postgresql://localhost/db
  pool:
    min: 5
    max: 20
```

**With TOON:**
```toon
# config.toon
database: {
  url: "postgresql://localhost/db"
  pool: {
    min: 5
    max: 20
  }
}
```

**Assessment:** ⚠️ YAML is already perfect for this. TOON offers no advantage.

#### Scenario 2: Test Fixtures

**Current:** Use JSON
```json
{
  "testCases": [
    {"input": {"x": 1, "y": 2}, "expected": 3},
    {"input": {"x": 5, "y": 10}, "expected": 15}
  ]
}
```

**With TOON:**
```toon
# test-fixtures.toon
testCases: [
  # Addition test
  {input: {x: 1, y: 2}, expected: 3}

  # Larger numbers
  {input: {x: 5, y: 10}, expected: 15}
]
```

**Assessment:** ✅ TOON is better here (comments in test data)

#### Scenario 3: API Mock Data

**Current:** Use JSON
```json
{
  "users": [
    {"id": 1, "name": "Alice", "role": "admin"},
    {"id": 2, "name": "Bob", "role": "user"}
  ]
}
```

**With TOON:**
```toon
users: [
  {id: 1, name: "Alice", role: "admin"}
  {id: 2, name: "Bob", role: "user"}
]
```

**Assessment:** ⚠️ Slightly cleaner, but JSON is universal for APIs

#### Scenario 4: Documentation Examples

**Current:** Use JSON in docs
```markdown
Example request:
```json
{"name": "Alice", "age": 30}
```
```

**With TOON:**
```markdown
Example request:
```toon
# User registration
{
  name: "Alice"
  age: 30
  email: "alice@example.com"  # Must be unique
}
```
```

**Assessment:** ✅ TOON is better for documentation (comments, less noise)

### 6.4 Competition vs Complementarity

#### Competes With

**JSON:**
- Both for structured data
- TOON adds comments, but JSON has universal support
- TOON is cleaner, but JSON is standard

**YAML:**
- Both for human-editable configs
- TOON is more explicit, but YAML has ecosystem
- TOON is simpler to parse, but YAML is established

#### Complements

**CSV:**
- CSV for tabular data, TOON for hierarchical
- Different problem spaces

**XML:**
- XML for enterprise/legacy, TOON for modern configs
- Different ecosystems

#### Strategic Positioning

TOON's best position: **"JSON with comments, YAML without indentation headaches"**

Target audience:
- ✅ Developers who dislike YAML's indentation
- ✅ Teams wanting comments in JSON
- ✅ Projects needing explicit structure with readability

Not for:
- ❌ API responses (JSON is standard)
- ❌ Enterprise integration (XML/JSON are established)
- ❌ Big data (binary formats are better)

---

## Implementation Requirements

### 7.1 Module Structure

```
formats/toon/
├── build.gradle.kts
├── README.md
├── src/
│   ├── main/
│   │   └── kotlin/
│   │       └── org/apache/utlx/formats/toon/
│   │           ├── TOONParser.kt              # Parse TOON → UDM
│   │           ├── TOONSerializer.kt          # Serialize UDM → TOON
│   │           ├── TOONLexer.kt               # Tokenizer
│   │           ├── TOONToken.kt               # Token definitions
│   │           ├── TOONException.kt           # Custom exceptions
│   │           └── TOONDialect.kt             # Dialect configuration (optional)
│   └── test/
│       └── kotlin/
│           └── org/apache/utlx/formats/toon/
│               ├── TOONParserTest.kt          # Parser unit tests
│               ├── TOONSerializerTest.kt      # Serializer unit tests
│               ├── TOONRoundTripTest.kt       # Round-trip tests
│               ├── TOONErrorTest.kt           # Error handling tests
│               └── fixtures/                  # Test TOON files
│                   ├── simple.toon
│                   ├── nested.toon
│                   ├── references.toon
│                   └── errors/
│                       ├── invalid-syntax.toon
│                       └── circular-ref.toon
└── docs/
    ├── TOON-FORMAT.md                         # Format specification
    └── EXAMPLES.md                            # Usage examples
```

### 7.2 Parser Architecture

```kotlin
// TOONToken.kt
enum class TokenType {
    // Literals
    STRING,          // "hello"
    NUMBER,          // 42, 3.14
    BOOLEAN,         // true, false
    NULL,            // null

    // Structural
    LBRACE,          // {
    RBRACE,          // }
    LBRACKET,        // [
    RBRACKET,        // ]
    COLON,           // :
    COMMA,           // ,

    // References
    ANCHOR,          // &
    ALIAS,           // *
    MERGE,           // <<:

    // Multi-line
    PIPE,            // |

    // Other
    IDENTIFIER,      // unquoted keys
    COMMENT,         // # ...
    EOF
}

data class Token(
    val type: TokenType,
    val value: String,
    val line: Int,
    val column: Int
)

// TOONLexer.kt
class TOONLexer(private val source: String) {
    private var pos = 0
    private var line = 1
    private var column = 1

    fun nextToken(): Token {
        skipWhitespace()

        if (isAtEnd()) return Token(TokenType.EOF, "", line, column)

        return when (peek()) {
            '{' -> singleCharToken(TokenType.LBRACE)
            '}' -> singleCharToken(TokenType.RBRACE)
            '[' -> singleCharToken(TokenType.LBRACKET)
            ']' -> singleCharToken(TokenType.RBRACKET)
            ':' -> singleCharToken(TokenType.COLON)
            ',' -> singleCharToken(TokenType.COMMA)
            '#' -> comment()
            '"' -> string()
            '&' -> singleCharToken(TokenType.ANCHOR)
            '*' -> singleCharToken(TokenType.ALIAS)
            '|' -> singleCharToken(TokenType.PIPE)
            in '0'..'9', '-' -> number()
            else -> identifierOrKeyword()
        }
    }

    private fun string(): Token {
        val start = column
        advance() // Skip opening "
        val sb = StringBuilder()

        while (!isAtEnd() && peek() != '"') {
            if (peek() == '\\') {
                advance()
                sb.append(escapeSequence())
            } else {
                sb.append(advance())
            }
        }

        if (isAtEnd()) {
            throw TOONException("Unterminated string", line, column)
        }

        advance() // Skip closing "
        return Token(TokenType.STRING, sb.toString(), line, start)
    }

    private fun number(): Token {
        val start = column
        val sb = StringBuilder()

        if (peek() == '-') sb.append(advance())

        while (!isAtEnd() && peek().isDigit()) {
            sb.append(advance())
        }

        if (peek() == '.') {
            sb.append(advance())
            while (!isAtEnd() && peek().isDigit()) {
                sb.append(advance())
            }
        }

        return Token(TokenType.NUMBER, sb.toString(), line, start)
    }

    private fun comment(): Token {
        val start = column
        advance() // Skip #
        val sb = StringBuilder()

        while (!isAtEnd() && peek() != '\n') {
            sb.append(advance())
        }

        return Token(TokenType.COMMENT, sb.toString(), line, start)
    }

    // ... helper methods
}

// TOONParser.kt
class TOONParser(source: String) {
    private val lexer = TOONLexer(source)
    private var current: Token = lexer.nextToken()
    private val anchors = mutableMapOf<String, UDM>()

    fun parse(): UDM {
        skipComments()
        val result = parseValue()
        expect(TokenType.EOF)
        return result
    }

    private fun parseValue(): UDM {
        skipComments()

        return when (current.type) {
            TokenType.STRING -> parseString()
            TokenType.NUMBER -> parseNumber()
            TokenType.BOOLEAN -> parseBoolean()
            TokenType.NULL -> parseNull()
            TokenType.LBRACE -> parseObject()
            TokenType.LBRACKET -> parseArray()
            TokenType.ANCHOR -> parseAnchor()
            TokenType.ALIAS -> parseAlias()
            TokenType.PIPE -> parseMultiLineString()
            else -> throw TOONException(
                "Unexpected token: ${current.type}",
                current.line,
                current.column
            )
        }
    }

    private fun parseObject(): UDM.Object {
        expect(TokenType.LBRACE)
        skipComments()

        val properties = mutableMapOf<String, UDM>()

        while (current.type != TokenType.RBRACE) {
            val key = parseKey()
            expect(TokenType.COLON)
            val value = parseValue()
            properties[key] = value

            skipComments()

            // Optional comma
            if (current.type == TokenType.COMMA) {
                advance()
                skipComments()
            }
        }

        expect(TokenType.RBRACE)
        return UDM.Object(properties = properties)
    }

    private fun parseArray(): UDM.Array {
        expect(TokenType.LBRACKET)
        skipComments()

        val elements = mutableListOf<UDM>()

        while (current.type != TokenType.RBRACKET) {
            elements.add(parseValue())
            skipComments()

            // Optional comma
            if (current.type == TokenType.COMMA) {
                advance()
                skipComments()
            }
        }

        expect(TokenType.RBRACKET)
        return UDM.Array(elements)
    }

    private fun parseAnchor(): UDM {
        expect(TokenType.ANCHOR)
        val name = expect(TokenType.IDENTIFIER).value
        val value = parseValue()
        anchors[name] = value
        return value
    }

    private fun parseAlias(): UDM {
        expect(TokenType.ALIAS)
        val name = expect(TokenType.IDENTIFIER).value
        return anchors[name] ?: throw TOONException(
            "Unknown anchor: $name",
            current.line,
            current.column
        )
    }

    private fun parseKey(): String {
        return when (current.type) {
            TokenType.STRING -> advance().value
            TokenType.IDENTIFIER -> advance().value
            else -> throw TOONException(
                "Expected key, got ${current.type}",
                current.line,
                current.column
            )
        }
    }

    private fun skipComments() {
        while (current.type == TokenType.COMMENT) {
            advance()
        }
    }

    // ... helper methods
}
```

### 7.3 Serializer Architecture

```kotlin
// TOONSerializer.kt
class TOONSerializer(
    private val prettyPrint: Boolean = true,
    private val indent: String = "  ",
    private val includeComments: Boolean = false
) {
    fun serialize(udm: UDM): String {
        val writer = StringBuilder()
        serializeValue(udm, writer, 0)
        return writer.toString()
    }

    private fun serializeValue(udm: UDM, writer: StringBuilder, depth: Int) {
        when (udm) {
            is UDM.Scalar -> serializeScalar(udm, writer)
            is UDM.Array -> serializeArray(udm, writer, depth)
            is UDM.Object -> serializeObject(udm, writer, depth)
            is UDM.DateTime -> writer.append("\"${udm.instant}\"")
            is UDM.Date -> writer.append("\"${udm.date}\"")
            is UDM.Binary -> throw TOONException("Cannot serialize Binary to TOON")
            is UDM.Lambda -> throw TOONException("Cannot serialize Lambda to TOON")
            else -> throw TOONException("Unsupported UDM type: ${udm::class}")
        }
    }

    private fun serializeScalar(scalar: UDM.Scalar, writer: StringBuilder) {
        when (val value = scalar.value) {
            null -> writer.append("null")
            is Boolean -> writer.append(value.toString())
            is Number -> writer.append(value.toString())
            is String -> writer.append("\"${escapeString(value)}\"")
            else -> writer.append("\"${value}\"")
        }
    }

    private fun serializeObject(obj: UDM.Object, writer: StringBuilder, depth: Int) {
        // Handle attributes (merge with @ prefix)
        val allProperties = obj.attributes.mapKeys { "@${it.key}" } + obj.properties

        writer.append("{")

        if (prettyPrint && allProperties.isNotEmpty()) {
            writer.append("\n")
        }

        val entries = allProperties.entries.toList()
        entries.forEachIndexed { index, (key, value) ->
            if (prettyPrint) {
                writer.append(indent.repeat(depth + 1))
            }

            // Quote key if it contains special characters
            if (needsQuoting(key)) {
                writer.append("\"$key\"")
            } else {
                writer.append(key)
            }

            writer.append(": ")

            when (value) {
                is String -> serializeValue(UDM.Scalar(value), writer, depth + 1)
                is UDM -> serializeValue(value, writer, depth + 1)
                else -> writer.append("\"$value\"")
            }

            if (prettyPrint && index < entries.size - 1) {
                writer.append("\n")
            } else if (!prettyPrint && index < entries.size - 1) {
                writer.append(", ")
            }
        }

        if (prettyPrint && allProperties.isNotEmpty()) {
            writer.append("\n")
            writer.append(indent.repeat(depth))
        }

        writer.append("}")
    }

    private fun serializeArray(arr: UDM.Array, writer: StringBuilder, depth: Int) {
        writer.append("[")

        if (prettyPrint && arr.elements.isNotEmpty()) {
            writer.append("\n")
        }

        arr.elements.forEachIndexed { index, element ->
            if (prettyPrint) {
                writer.append(indent.repeat(depth + 1))
            }

            serializeValue(element, writer, depth + 1)

            if (prettyPrint && index < arr.elements.size - 1) {
                writer.append("\n")
            } else if (!prettyPrint && index < arr.elements.size - 1) {
                writer.append(", ")
            }
        }

        if (prettyPrint && arr.elements.isNotEmpty()) {
            writer.append("\n")
            writer.append(indent.repeat(depth))
        }

        writer.append("]")
    }

    private fun needsQuoting(key: String): Boolean {
        return key.isEmpty()
            || key[0].isDigit()
            || key.any { !it.isLetterOrDigit() && it != '_' }
    }

    private fun escapeString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
```

### 7.4 Test Requirements

```kotlin
// TOONParserTest.kt
class TOONParserTest {
    @Test
    fun `parse simple object`() {
        val toon = """
            {
              name: "Alice"
              age: 30
            }
        """.trimIndent()

        val udm = TOONParser(toon).parse()

        assertIs<UDM.Object>(udm)
        assertEquals("Alice", (udm.properties["name"] as UDM.Scalar).value)
        assertEquals(30.0, (udm.properties["age"] as UDM.Scalar).value)
    }

    @Test
    fun `parse array`() {
        val toon = "[1, 2, 3]"
        val udm = TOONParser(toon).parse()

        assertIs<UDM.Array>(udm)
        assertEquals(3, udm.elements.size)
    }

    @Test
    fun `parse with comments`() {
        val toon = """
            # This is a comment
            {
              name: "Alice"  # Name field
            }
        """.trimIndent()

        val udm = TOONParser(toon).parse()
        assertIs<UDM.Object>(udm)
    }

    @Test
    fun `parse references`() {
        val toon = """
            defaults: &base {
              timeout: 30
            }

            config: {
              <<: *base
              host: "localhost"
            }
        """.trimIndent()

        val udm = TOONParser(toon).parse() as UDM.Object
        val config = udm.properties["config"] as UDM.Object

        assertEquals(30.0, (config.properties["timeout"] as UDM.Scalar).value)
        assertEquals("localhost", (config.properties["host"] as UDM.Scalar).value)
    }

    @Test
    fun `parse error - unterminated string`() {
        val toon = """{name: "Alice}"""

        assertThrows<TOONException> {
            TOONParser(toon).parse()
        }
    }

    @Test
    fun `parse error - unknown alias`() {
        val toon = """{ref: *unknown}"""

        assertThrows<TOONException> {
            TOONParser(toon).parse()
        }
    }
}

// TOONRoundTripTest.kt
class TOONRoundTripTest {
    @Test
    fun `round trip simple values`() {
        val original = UDM.Object(
            properties = mapOf(
                "name" to UDM.Scalar("Alice"),
                "age" to UDM.Scalar(30.0)
            )
        )

        val toon = TOONSerializer().serialize(original)
        val parsed = TOONParser(toon).parse()

        assertEquals(original, parsed)
    }

    @Test
    fun `round trip nested structures`() {
        val original = UDM.Object(
            properties = mapOf(
                "users" to UDM.Array(
                    listOf(
                        UDM.Object(properties = mapOf("name" to UDM.Scalar("Alice"))),
                        UDM.Object(properties = mapOf("name" to UDM.Scalar("Bob")))
                    )
                )
            )
        )

        val toon = TOONSerializer().serialize(original)
        val parsed = TOONParser(toon).parse()

        assertEquals(original, parsed)
    }
}
```

### 7.5 CLI Integration

```kotlin
// In UDMCommand.kt
object UDMCommand {
    private val SUPPORTED_FORMATS = setOf(
        "json", "xml", "csv", "yaml",
        "jsonschema", "xsd", "avro", "protobuf",
        "toon"  // NEW
    )

    private fun parseInputToUDM(content: String, format: String, options: Map<String, Any>): UDM {
        return when (format.lowercase()) {
            // ... existing formats

            "toon" -> {
                TOONParser(content).parse()
            }

            else -> throw IllegalArgumentException("Unsupported format: $format")
        }
    }

    private fun serializeUDMToFormat(udm: UDM, format: String, options: Map<String, Any>): String {
        return when (format.lowercase()) {
            // ... existing formats

            "toon" -> {
                val prettyPrint = (options["prettyPrint"] as? Boolean) ?: true
                TOONSerializer(prettyPrint = prettyPrint).serialize(udm)
            }

            else -> throw IllegalArgumentException("Unsupported format: $format")
        }
    }
}
```

### 7.6 Documentation Requirements

**1. Format Specification** (`docs/formats/TOON-FORMAT.md`)
- Complete grammar
- Type system
- Reference semantics
- Error conditions

**2. Usage Guide** (`docs/guides/using-toon.md`)
- Basic examples
- Migration from JSON/YAML
- Best practices
- Limitations

**3. API Documentation** (KDoc)
- Parser public API
- Serializer public API
- Exception types

**4. Examples** (`examples/toon/`)
- Simple config
- Complex nested structure
- References example
- Test fixtures

---

## Pros and Cons Analysis

### Arguments FOR Adding TOON

**1. ✅ Clean Syntax**
- Better readability than JSON (less syntax noise)
- More explicit than YAML (braces instead of indentation)
- Good balance between JSON strictness and YAML flexibility

**2. ✅ Technical Feasibility**
- Straightforward UDM mapping (9/10 fidelity)
- Reasonable implementation cost (~2 weeks)
- No complex dependencies required

**3. ✅ Format Extensibility Demonstration**
- Shows UTLX can easily add new formats
- Validates architecture's flexibility
- Good showcase for documentation

**4. ✅ No Conflicts**
- Doesn't break existing formats
- Clean integration into UDM system
- Optional for users (no forced adoption)

**5. ✅ Niche Use Cases**
- Test fixtures with comments
- Documentation examples
- Teams wanting explicit structure with comments

**6. ✅ Educational Value**
- Good example for custom format implementation
- Demonstrates parser/serializer pattern
- Reference for community contributions

### Arguments AGAINST Adding TOON

**1. ❌ No Ecosystem**
- Zero existing parsers in any language
- No IDE support (syntax highlighting, validation)
- No tooling (formatters, linters, validators)
- No community or user base

**2. ❌ Heavy Overlap**
- 90% of features covered by JSON + YAML
- Comments: YAML has this
- Explicit structure: JSON has this
- References: YAML has this
- Multi-line: YAML has this

**3. ❌ No Proven Demand**
- No user requests for TOON
- No evidence of adoption elsewhere
- No compelling use cases that JSON/YAML don't cover

**4. ❌ Maintenance Burden**
- Custom parser to maintain indefinitely
- Bug fixes, security patches
- Updates for UDM evolution
- Documentation upkeep

**5. ❌ User Confusion**
- When to use TOON vs JSON vs YAML?
- Learning curve for yet another format
- Team standardization challenges

**6. ❌ Strategic Distraction**
- Resources better spent on:
  - Improving existing format support
  - Adding high-demand formats (TOML, Parquet)
  - Enhancing transformation features
  - Building validation layer

### Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| **No user adoption** | High | Medium | Mark as experimental, easy to deprecate |
| **Maintenance burden** | Medium | Medium | Keep implementation simple, minimal features |
| **Ecosystem lock-in** | Low | Low | Users can convert to JSON/YAML anytime |
| **Breaking changes** | Low | Low | Version format if needed |
| **Security issues** | Low | Medium | Thorough testing, fuzz testing |
| **Performance problems** | Low | Low | Hand-written parser is efficient |

---

## Recommendations

### 9.1 Implementation Options

**Option A: Add as Experimental Format** ⭐ **RECOMMENDED**

**Approach:**
- Implement TOON with "experimental" label
- Limited documentation (README + basic examples)
- Mark as "may be deprecated" in warnings
- Monitor usage for 6 months
- Promote to stable if adoption > 5%, deprecate if < 1%

**Pros:**
- ✅ Low risk (can easily remove)
- ✅ Validates user demand
- ✅ Demonstrates extensibility
- ✅ Quick implementation (2-3 weeks)

**Cons:**
- ⚠️ May waste effort if no adoption
- ⚠️ Users may rely on experimental feature

**Recommended tags:**
```kotlin
@ExperimentalTOONFormat
class TOONParser { ... }
```

**Option B: Defer Until Community Demand**

**Approach:**
- Document TOON as "possible future format"
- Create RFC or GitHub issue for community input
- Wait for user requests before implementing
- Focus resources on higher-priority work

**Pros:**
- ✅ Zero wasted effort
- ✅ Validates demand before building
- ✅ Resources for other features

**Cons:**
- ❌ Misses opportunity to demonstrate extensibility
- ❌ No way to test if users would adopt

**Option C: Implement as Plugin/Extension**

**Approach:**
- Create separate plugin repository
- Keep TOON out of core UTLX
- Document plugin architecture
- Community can maintain

**Pros:**
- ✅ No core maintenance burden
- ✅ Demonstrates plugin system
- ✅ Community ownership

**Cons:**
- ⚠️ More complex architecture
- ⚠️ Slower integration
- ⚠️ Plugin system may not exist yet

### 9.2 Recommended Approach

**Choose Option A: Experimental Format**

**Rationale:**
1. **Low risk** - Can deprecate if unused (mark as experimental)
2. **Strategic value** - Demonstrates format extensibility
3. **Reasonable cost** - 2-3 weeks is acceptable
4. **Data collection** - Provides real usage metrics
5. **Showcase** - Good example for documentation

**Implementation Plan:**

**Phase 1: Core Implementation (1 week)**
- Day 1-2: Lexer + basic parser
- Day 3-4: Complete parser with references
- Day 5: Serializer

**Phase 2: Testing & Integration (1 week)**
- Day 6-7: Unit tests + round-trip tests
- Day 8: CLI integration
- Day 9: Documentation
- Day 10: Buffer for issues

**Phase 3: Monitoring (6 months)**
- Track usage via telemetry (opt-in)
- Collect user feedback
- Monitor GitHub issues/questions

**Success Criteria:**
- ✅ **Promote to stable** if:
  - > 5% of users try TOON
  - > 1% use regularly
  - Positive community feedback

- ⚠️ **Keep experimental** if:
  - 1-5% adoption
  - Mixed feedback

- ❌ **Deprecate** if:
  - < 1% adoption
  - No compelling use cases found
  - Maintenance burden too high

### 9.3 Priority Assessment

**Priority: LOW-MEDIUM**

Higher priority items:
1. **Schema validation layer** (Schematron, SHACL)
2. **Binary formats** (Parquet, ORC, Arrow)
3. **Popular text formats** (TOML, HCL, Properties)
4. **Format enhancements** (Better error messages, performance)
5. **Transformation features** (More functions, debugging)

TOON should be:
- Community contribution target
- Hackathon project
- Example for "adding custom formats" tutorial
- Student/intern project

### 9.4 Alternative Strategies

**Alternative 1: Enhance YAML Support**

Instead of new format, improve YAML:
- Better comment preservation
- Custom tags for UDM types
- Improved error messages
- Performance optimization

Gets similar benefits without new format.

**Alternative 2: Add TOML (Higher Demand)**

TOML has:
- ✅ Established ecosystem
- ✅ Proven adoption (Cargo, pip)
- ✅ Clear use case (configs)
- ✅ Active community

**Alternative 3: Add JSON5**

JSON5 is "JSON with comments":
- ✅ Existing parsers
- ✅ JSON-compatible
- ✅ Growing adoption
- ✅ Solves "JSON lacks comments" problem

**Recommendation:** Consider TOML or JSON5 before TOON.

---

## Technical Implementation Guide

### 10.1 Complete Parser Implementation

```kotlin
// TOONParser.kt - Complete implementation
package org.apache.utlx.formats.toon

import org.apache.utlx.core.udm.UDM
import java.io.Reader
import java.io.StringReader

class TOONParser(source: String) {
    private val lexer = TOONLexer(StringReader(source))
    private var current: Token = lexer.nextToken()
    private val anchors = mutableMapOf<String, UDM>()

    companion object {
        fun parse(source: String): UDM {
            return TOONParser(source).parse()
        }
    }

    fun parse(): UDM {
        skipComments()
        val result = parseValue()
        expect(TokenType.EOF)
        return result
    }

    private fun parseValue(): UDM {
        skipComments()

        return when (current.type) {
            TokenType.STRING -> parseString()
            TokenType.NUMBER -> parseNumber()
            TokenType.TRUE, TokenType.FALSE -> parseBoolean()
            TokenType.NULL -> parseNull()
            TokenType.LBRACE -> parseObject()
            TokenType.LBRACKET -> parseArray()
            TokenType.ANCHOR -> parseAnchor()
            TokenType.ALIAS -> parseAlias()
            TokenType.MERGE -> parseMerge()
            TokenType.PIPE -> parseMultiLineString()
            else -> throw TOONParseException(
                "Unexpected token: ${current.type}",
                current.line,
                current.column
            )
        }
    }

    private fun parseString(): UDM.Scalar {
        val token = expect(TokenType.STRING)
        return UDM.Scalar(token.value)
    }

    private fun parseNumber(): UDM.Scalar {
        val token = expect(TokenType.NUMBER)
        val value = token.value.toDoubleOrNull()
            ?: throw TOONParseException("Invalid number: ${token.value}", token.line, token.column)
        return UDM.Scalar(value)
    }

    private fun parseBoolean(): UDM.Scalar {
        val value = current.type == TokenType.TRUE
        advance()
        return UDM.Scalar(value)
    }

    private fun parseNull(): UDM.Scalar {
        expect(TokenType.NULL)
        return UDM.Scalar(null)
    }

    private fun parseObject(): UDM.Object {
        expect(TokenType.LBRACE)
        skipComments()

        val properties = mutableMapOf<String, UDM>()

        while (current.type != TokenType.RBRACE) {
            // Check for merge operator
            if (current.type == TokenType.MERGE) {
                val merged = parseMerge() as UDM.Object
                properties.putAll(merged.properties)
            } else {
                val key = parseKey()
                expect(TokenType.COLON)
                val value = parseValue()
                properties[key] = value
            }

            skipComments()

            // Optional comma
            if (current.type == TokenType.COMMA) {
                advance()
                skipComments()
            }
        }

        expect(TokenType.RBRACE)
        return UDM.Object(properties = properties)
    }

    private fun parseArray(): UDM.Array {
        expect(TokenType.LBRACKET)
        skipComments()

        val elements = mutableListOf<UDM>()

        while (current.type != TokenType.RBRACKET) {
            elements.add(parseValue())
            skipComments()

            // Optional comma
            if (current.type == TokenType.COMMA) {
                advance()
                skipComments()
            }
        }

        expect(TokenType.RBRACKET)
        return UDM.Array(elements)
    }

    private fun parseAnchor(): UDM {
        expect(TokenType.ANCHOR)
        val name = expect(TokenType.IDENTIFIER).value
        val value = parseValue()

        // Check for circular references
        if (anchors.containsKey(name)) {
            throw TOONParseException(
                "Duplicate anchor: $name",
                current.line,
                current.column
            )
        }

        anchors[name] = value
        return value
    }

    private fun parseAlias(): UDM {
        expect(TokenType.ALIAS)
        val name = expect(TokenType.IDENTIFIER).value

        return anchors[name]?.let { deepCopy(it) }
            ?: throw TOONParseException(
                "Unknown anchor: $name",
                current.line,
                current.column
            )
    }

    private fun parseMerge(): UDM {
        expect(TokenType.MERGE)
        expect(TokenType.ALIAS)
        val name = current.value
        advance()

        return anchors[name]?.let { deepCopy(it) }
            ?: throw TOONParseException(
                "Unknown anchor in merge: $name",
                current.line,
                current.column
            )
    }

    private fun parseMultiLineString(): UDM.Scalar {
        expect(TokenType.PIPE)
        val lines = mutableListOf<String>()

        // Read indented lines until non-indented or EOF
        while (current.type == TokenType.STRING) {
            lines.add(current.value)
            advance()
        }

        return UDM.Scalar(lines.joinToString("\n"))
    }

    private fun parseKey(): String {
        return when (current.type) {
            TokenType.STRING -> {
                val value = current.value
                advance()
                value
            }
            TokenType.IDENTIFIER -> {
                val value = current.value
                advance()
                value
            }
            else -> throw TOONParseException(
                "Expected key (string or identifier), got ${current.type}",
                current.line,
                current.column
            )
        }
    }

    private fun expect(type: TokenType): Token {
        if (current.type != type) {
            throw TOONParseException(
                "Expected $type but found ${current.type}",
                current.line,
                current.column
            )
        }
        val token = current
        advance()
        return token
    }

    private fun advance() {
        current = lexer.nextToken()
    }

    private fun skipComments() {
        while (current.type == TokenType.COMMENT) {
            advance()
        }
    }

    private fun deepCopy(udm: UDM): UDM {
        return when (udm) {
            is UDM.Scalar -> UDM.Scalar(udm.value)
            is UDM.Array -> UDM.Array(udm.elements.map { deepCopy(it) })
            is UDM.Object -> UDM.Object(
                properties = udm.properties.mapValues { (_, v) -> deepCopy(v) },
                attributes = udm.attributes.toMap(),
                name = udm.name,
                metadata = udm.metadata.toMap()
            )
            else -> udm // Other types are immutable
        }
    }
}

// Exceptions
class TOONParseException(
    message: String,
    val line: Int,
    val column: Int
) : Exception("$message at line $line, column $column")
```

### 10.2 Complete Serializer Implementation

```kotlin
// TOONSerializer.kt - Complete implementation
package org.apache.utlx.formats.toon

import org.apache.utlx.core.udm.UDM

class TOONSerializer(
    private val prettyPrint: Boolean = true,
    private val indent: String = "  ",
    private val inlineSimpleObjects: Boolean = false
) {
    companion object {
        fun serialize(udm: UDM, prettyPrint: Boolean = true): String {
            return TOONSerializer(prettyPrint).serialize(udm)
        }
    }

    fun serialize(udm: UDM): String {
        val writer = StringBuilder()
        serializeValue(udm, writer, 0)
        if (prettyPrint) {
            writer.append("\n")
        }
        return writer.toString()
    }

    private fun serializeValue(udm: UDM, writer: StringBuilder, depth: Int) {
        when (udm) {
            is UDM.Scalar -> serializeScalar(udm, writer)
            is UDM.Array -> serializeArray(udm, writer, depth)
            is UDM.Object -> serializeObject(udm, writer, depth)
            is UDM.DateTime -> writer.append("\"${udm.instant}\"")
            is UDM.Date -> writer.append("\"${udm.date}\"")
            is UDM.LocalDateTime -> writer.append("\"${udm.dateTime}\"")
            is UDM.Time -> writer.append("\"${udm.time}\"")
            is UDM.Binary -> throw TOONSerializeException(
                "Cannot serialize Binary to TOON (no binary representation)"
            )
            is UDM.Lambda -> throw TOONSerializeException(
                "Cannot serialize Lambda to TOON (no function representation)"
            )
        }
    }

    private fun serializeScalar(scalar: UDM.Scalar, writer: StringBuilder) {
        when (val value = scalar.value) {
            null -> writer.append("null")
            is Boolean -> writer.append(value.toString())
            is Number -> {
                // Format numbers cleanly
                val str = value.toString()
                writer.append(if (str.endsWith(".0")) str.dropLast(2) else str)
            }
            is String -> {
                if (value.contains('\n')) {
                    serializeMultiLineString(value, writer)
                } else {
                    writer.append("\"${escapeString(value)}\"")
                }
            }
            else -> writer.append("\"${escapeString(value.toString())}\"")
        }
    }

    private fun serializeObject(obj: UDM.Object, writer: StringBuilder, depth: Int) {
        // Merge attributes with properties (prefix with @)
        val allProperties = obj.attributes.mapKeys { "@${it.key}" }.mapValues { (_, v) ->
            UDM.Scalar(v)
        } + obj.properties

        // Add name as @name if present
        val finalProperties = if (obj.name != null) {
            mapOf("@name" to UDM.Scalar(obj.name)) + allProperties
        } else {
            allProperties
        }

        // Check if object is simple (for inline formatting)
        val isSimple = inlineSimpleObjects && finalProperties.size <= 3 &&
            finalProperties.values.all { it is UDM.Scalar }

        writer.append("{")

        if (!isSimple && prettyPrint && finalProperties.isNotEmpty()) {
            writer.append("\n")
        } else if (isSimple && finalProperties.isNotEmpty()) {
            writer.append(" ")
        }

        val entries = finalProperties.entries.toList()
        entries.forEachIndexed { index, (key, value) ->
            if (!isSimple && prettyPrint) {
                writer.append(indent.repeat(depth + 1))
            }

            // Quote key if needed
            if (needsQuoting(key)) {
                writer.append("\"${escapeString(key)}\"")
            } else {
                writer.append(key)
            }

            writer.append(": ")
            serializeValue(value, writer, depth + 1)

            if (!isSimple && prettyPrint) {
                writer.append("\n")
            } else if (index < entries.size - 1) {
                writer.append(if (isSimple) ", " else "\n")
            }
        }

        if (!isSimple && prettyPrint && finalProperties.isNotEmpty()) {
            writer.append(indent.repeat(depth))
        } else if (isSimple && finalProperties.isNotEmpty()) {
            writer.append(" ")
        }

        writer.append("}")
    }

    private fun serializeArray(arr: UDM.Array, writer: StringBuilder, depth: Int) {
        // Check if array is simple (all scalars)
        val isSimple = inlineSimpleObjects && arr.elements.size <= 5 &&
            arr.elements.all { it is UDM.Scalar }

        writer.append("[")

        if (!isSimple && prettyPrint && arr.elements.isNotEmpty()) {
            writer.append("\n")
        } else if (isSimple && arr.elements.isNotEmpty()) {
            writer.append(" ")
        }

        arr.elements.forEachIndexed { index, element ->
            if (!isSimple && prettyPrint) {
                writer.append(indent.repeat(depth + 1))
            }

            serializeValue(element, writer, depth + 1)

            if (!isSimple && prettyPrint) {
                writer.append("\n")
            } else if (index < arr.elements.size - 1) {
                writer.append(if (isSimple) ", " else "\n")
            }
        }

        if (!isSimple && prettyPrint && arr.elements.isNotEmpty()) {
            writer.append(indent.repeat(depth))
        } else if (isSimple && arr.elements.isNotEmpty()) {
            writer.append(" ")
        }

        writer.append("]")
    }

    private fun serializeMultiLineString(s: String, writer: StringBuilder) {
        writer.append("|\n")
        s.lines().forEach { line ->
            writer.append("  ").append(line).append("\n")
        }
    }

    private fun needsQuoting(key: String): Boolean {
        if (key.isEmpty()) return true
        if (key[0].isDigit()) return true
        if (key in setOf("true", "false", "null")) return true

        return key.any { ch ->
            !ch.isLetterOrDigit() && ch != '_' && ch != '-'
        }
    }

    private fun escapeString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

class TOONSerializeException(message: String) : Exception(message)
```

### 10.3 Usage Examples

```kotlin
// Example 1: Parse TOON to UDM
val toon = """
    person: {
      name: "Alice"
      age: 30
      address: {
        city: "NYC"
        zip: "10001"
      }
    }
""".trimIndent()

val udm = TOONParser.parse(toon)

// Example 2: Serialize UDM to TOON
val udm = UDM.Object(
    properties = mapOf(
        "config" to UDM.Object(
            properties = mapOf(
                "host" to UDM.Scalar("localhost"),
                "port" to UDM.Scalar(8080.0)
            )
        )
    )
)

val toon = TOONSerializer.serialize(udm)
println(toon)
// Output:
// {
//   config: {
//     host: "localhost"
//     port: 8080
//   }
// }

// Example 3: Round-trip
val original = """{"name": "Alice", "age": 30}"""
val udm = JSONParser.parse(original)
val toon = TOONSerializer.serialize(udm)
val udmFromToon = TOONParser.parse(toon)
assert(udm == udmFromToon)
```

---

## Conclusion

### Summary Matrix

| Criterion | Score | Weight | Weighted Score |
|-----------|-------|--------|----------------|
| **Technical Fit** | 9/10 | 15% | 1.35 |
| **Implementation Cost** | 7/10 | 10% | 0.70 |
| **Unique Value** | 4/10 | 25% | 1.00 |
| **Ecosystem Support** | 0/10 | 20% | 0.00 |
| **User Demand** | 1/10 | 15% | 0.15 |
| **Maintenance Burden** | 5/10 | 10% | 0.50 |
| **Strategic Fit** | 5/10 | 5% | 0.25 |
| ****Overall**` | **4.0/10** | **100%** | **40%** |

### Final Recommendation

**IMPLEMENT AS EXPERIMENTAL FORMAT** with the following conditions:

**✅ DO Implement TOON if:**
- Want to demonstrate UTLX format extensibility
- Have 2-3 weeks available for implementation
- Can commit to 6-month trial period
- Willing to deprecate if no adoption

**❌ DON'T Implement TOON if:**
- Need to prioritize other features
- Can't maintain experimental code
- Team prefers established formats only
- No bandwidth for monitoring adoption

### Implementation Checklist

If proceeding with implementation:

- [ ] Create `/formats/toon/` module structure
- [ ] Implement `TOONLexer` (tokenizer)
- [ ] Implement `TOONParser` (UDM conversion)
- [ ] Implement `TOONSerializer` (UDM serialization)
- [ ] Write 100+ unit tests
- [ ] Add round-trip tests
- [ ] Integrate with CLI (`utlx udm export/import`)
- [ ] Add to UDMService
- [ ] Update REST API schemas
- [ ] Write format documentation
- [ ] Add usage examples
- [ ] Mark as `@ExperimentalTOONFormat`
- [ ] Add telemetry (opt-in usage tracking)
- [ ] Set 6-month review date
- [ ] Create deprecation plan (if needed)

### Next Steps

**Immediate Actions:**

1. **Gather Stakeholder Input**
   - Poll core team on priority
   - Survey users on format preferences
   - Check if anyone actually wants TOON

2. **Create RFC** (if proceeding)
   - Formal proposal
   - Community feedback period
   - Decision timeline

3. **Prototype** (optional)
   - Quick 2-day prototype
   - Validate parser complexity
   - Test UDM mapping

**6-Month Review:**
- Measure adoption rate
- Collect user feedback
- Assess maintenance burden
- Decide: promote, keep experimental, or deprecate

### Alternative Recommendations

If **NOT** implementing TOON, consider these alternatives:

**Priority 1: TOML**
- ✅ Established ecosystem
- ✅ Clear use case (configs)
- ✅ Active community
- ✅ Proven parsers available

**Priority 2: JSON5**
- ✅ JSON-compatible
- ✅ Existing parsers
- ✅ Solves "JSON lacks comments"
- ✅ Growing adoption

**Priority 3: Enhanced YAML**
- ✅ Already supported
- ✅ Improve existing implementation
- ✅ Better error messages
- ✅ Performance optimization

---

## References

**Related Documents:**
- [TOON Token Study](./toon-token-study-document.md) - Original TOON research
- [UDM Language Spec](../specs/udm-language-spec-v1.md) - UDM Language format
- [UDM Architecture](./udm-as-a-language.md) - UDM design philosophy
- [Format Integration Guide](../guides/adding-formats.md) - How to add formats

**External Resources:**
- [JSON Specification](https://www.json.org/) - JSON standard
- [YAML Specification](https://yaml.org/spec/) - YAML standard
- [TOML Specification](https://toml.io/) - TOML standard
- [JSON5 Specification](https://json5.org/) - JSON5 standard

**Code Examples:**
- `/formats/json/` - JSON parser/serializer reference
- `/formats/yaml/` - YAML integration example
- `/formats/csv/` - Custom parser example

---

**Document Status:** Complete
**Last Updated:** 2025-11-12
**Next Review:** 2026-05-12 (6 months after potential implementation)
