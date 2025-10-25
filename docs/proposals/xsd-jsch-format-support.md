# XSD and JSON Schema (JSCH) Format Support

**Version:** 1.0
**Status:** Proposal
**Created:** 2025-10-25
**Author:** UTL-X Team

---

## Executive Summary

Add **XSD (XML Schema Definition)** and **JSCH (JSON Schema)** as first-class format types in UTL-X, enabling schema-to-schema transformations, schema validation, and schema documentation generation.

**Key Innovation:** XSD output supports all 4 W3C design patterns (Russian Doll, Salami Slice, Venetian Blind, Garden of Eden) via format options:

```utlx
%utlx 1.0
input xsd
output xsd {ElementDeclaration: global, TypeDeclaration: global}  # Garden of Eden
---
transformSchema($input)
```

**Scope:** XSD and JSCH only. YAML-based schemas (RAML, OpenAPI, AsyncAPI) deferred to later phases.

**Timeline:** 8-10 weeks (2-2.5 months)

---

## Table of Contents

1. [Motivation](#motivation)
2. [Format Type Extensions](#format-type-extensions)
3. [XSD Design Patterns](#xsd-design-patterns)
4. [Format Options Syntax](#format-options-syntax)
5. [Architecture](#architecture)
6. [Standard Library Functions](#standard-library-functions)
7. [Implementation Phases](#implementation-phases)
8. [Testing Strategy](#testing-strategy)
9. [Examples](#examples)
10. [Success Criteria](#success-criteria)

---

## Motivation

### Real-World Use Cases

1. **XSD → JSON Schema Migration**
   - Legacy SOAP/XML systems → Modern REST/JSON APIs
   - Preserve documentation and validation rules
   - Industry-standard migration pattern

2. **DataContract ↔ XSD Transformation**
   - Already happening in conformance test suite (9 DataContract tests)
   - Generate SQL DDL from DataContract models
   - Reverse-engineer schemas from database metadata

3. **Schema Documentation Generation**
   - Extract `<xs:documentation>` from XSD annotations
   - Generate API documentation from JSON Schema
   - Multilingual documentation support (`xml:lang`)

4. **Multi-Source Schema Merging**
   - Combine legacy XSD + modern JSON Schema → unified DataContract
   - Migration workflows for gradual system modernization

5. **XSD Design Pattern Conversion**
   - Convert Russian Doll → Venetian Blind (better maintainability)
   - Normalize schemas across enterprise (consistent pattern enforcement)
   - Tool migration (XMLSpy → Tibco → Stylus Studio compatibility)

### Why Not Just Use XML/JSON Formats?

**Problem:** XSD files ARE valid XML, JSON Schema files ARE valid JSON
- `input xml` would work for `.xsd` files (treats as generic XML)
- `input json` would work for `.schema.json` files (treats as generic JSON)

**But:**
1. **Semantic Intent Unclear:** Developer reading `output xml` doesn't know if it's data or schema
2. **No Schema-Specific Functions:** Can't provide `xsdGetElements()`, `jschValidateSchema()` elegantly
3. **No Type Safety:** Parser can't validate schema structure itself
4. **Poor Error Messages:** Generic XML/JSON errors instead of "Invalid XSD: missing targetNamespace"
5. **No Design Pattern Control:** Can't specify Venetian Blind vs. Garden of Eden output

**Solution:** Treat schemas as distinct formats with their own parsers, serializers, and stdlib functions.

---

## Format Type Extensions

### Core Enumeration

**File:** `modules/core/src/main/kotlin/org/apache/utlx/core/ast/ast_nodes.kt:73`

```kotlin
enum class FormatType {
    // Existing formats
    AUTO, XML, JSON, CSV, YAML, CUSTOM,

    // NEW: Schema formats
    XSD,   // XML Schema Definition (W3C XSD 1.0/1.1)
    JSCH   // JSON Schema (draft-07, 2020-12)
}
```

### File Extension Detection

**File:** `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/TransformCommand.kt:299`

```kotlin
private fun detectFormat(data: String, extension: String?): String {
    // Extension-based detection
    extension?.lowercase()?.let {
        return when (it) {
            "xsd" -> "xsd"
            "schema.json", "jschema" -> "jsch"
            "xml", "json", "csv", "yaml", "yml" -> if (it == "yml") "yaml" else it
            else -> null
        }
    }

    // Content-based detection
    val trimmed = data.trim()
    return when {
        // XSD detection: <xs:schema or <xsd:schema root element
        trimmed.startsWith("<xs:schema") || trimmed.startsWith("<xsd:schema") -> "xsd"

        // JSON Schema detection: has "$schema" and "type" or "properties"
        trimmed.startsWith("{") &&
        (trimmed.contains("\"$schema\"") ||
         (trimmed.contains("\"type\"") && trimmed.contains("\"properties\""))) -> "jsch"

        // Existing detections
        trimmed.startsWith("<") -> "xml"
        trimmed.startsWith("{") || trimmed.startsWith("[") -> "json"
        trimmed.contains("---") || (trimmed.contains(":") && !trimmed.contains(",")) -> "yaml"
        trimmed.contains(",") && !trimmed.startsWith("<") -> "csv"
        else -> "json" // default
    }
}
```

---

## XSD Design Patterns

From `docs/architecture/decisions/xsd-discussions-style.md`, there are **4 main XSD design patterns** based on two binary choices:

| Pattern | Element Declaration | Type Declaration | Use Case |
|---------|-------------------|------------------|----------|
| **Russian Doll** | Local | Local | Simple, isolated schemas |
| **Salami Slice** | Global | Local | Integration scenarios |
| **Venetian Blind** ⭐ | Local | Global | **Modular design (RECOMMENDED)** |
| **Garden of Eden** | Global | Global | Enterprise-wide schemas |

### Pattern Examples

#### 1. Russian Doll (Element=local, Type=local)

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="library">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="book" maxOccurs="unbounded">
          <xs:complexType>
            <xs:sequence>
              <xs:element name="title" type="xs:string"/>
              <xs:element name="author" type="xs:string"/>
            </xs:sequence>
          </xs:complexType>
        </xs:element>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

**Pros:** Simple, self-contained
**Cons:** No reusability, verbose for large schemas

#### 2. Salami Slice (Element=global, Type=local)

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="library">
    <xs:complexType>
      <xs:sequence>
        <xs:element ref="book" maxOccurs="unbounded"/>
      </xs:sequence>
    </xs:complexType>
  </xs:element>

  <xs:element name="book">
    <xs:complexType>
      <xs:sequence>
        <xs:element ref="title"/>
        <xs:element ref="author"/>
      </xs:sequence>
    </xs:complexType>
  </xs:element>

  <xs:element name="title" type="xs:string"/>
  <xs:element name="author" type="xs:string"/>
</xs:schema>
```

**Pros:** Reusable elements, all can be document roots
**Cons:** Flat structure, namespace pollution

#### 3. Venetian Blind ⭐ (Element=local, Type=global) **RECOMMENDED**

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="library">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="book" type="BookType" maxOccurs="unbounded"/>
      </xs:sequence>
    </xs:complexType>
  </xs:element>

  <!-- Global type definitions -->
  <xs:complexType name="BookType">
    <xs:sequence>
      <xs:element name="title" type="xs:string"/>
      <xs:element name="author" type="PersonType"/>
    </xs:sequence>
  </xs:complexType>

  <xs:complexType name="PersonType">
    <xs:sequence>
      <xs:element name="firstName" type="xs:string"/>
      <xs:element name="lastName" type="xs:string"/>
    </xs:sequence>
  </xs:complexType>
</xs:schema>
```

**Pros:** Best balance of reusability and encapsulation, clean namespaces
**Cons:** Slightly more complex than Russian Doll

#### 4. Garden of Eden (Element=global, Type=global)

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <!-- Global elements -->
  <xs:element name="library" type="LibraryType"/>
  <xs:element name="book" type="BookType"/>
  <xs:element name="title" type="xs:string"/>
  <xs:element name="author" type="PersonType"/>

  <!-- Global types -->
  <xs:complexType name="LibraryType">
    <xs:sequence>
      <xs:element ref="book" maxOccurs="unbounded"/>
    </xs:sequence>
  </xs:complexType>

  <xs:complexType name="BookType">
    <xs:sequence>
      <xs:element ref="title"/>
      <xs:element ref="author"/>
    </xs:sequence>
  </xs:complexType>

  <xs:complexType name="PersonType">
    <xs:sequence>
      <xs:element name="firstName" type="xs:string"/>
      <xs:element name="lastName" type="xs:string"/>
    </xs:sequence>
  </xs:complexType>
</xs:schema>
```

**Pros:** Maximum reusability
**Cons:** Most complex, heavy namespace usage

---

## Format Options Syntax

### Proposed Syntax

Format options allow fine-grained control over serialization behavior:

```utlx
%utlx 1.0
input xsd
output xsd {
  ElementDeclaration: global,     # or: local
  TypeDeclaration: global,        # or: local
  namespace: "http://example.com",
  version: "1.0"                  # XSD version: 1.0 or 1.1
}
---
transformSchema($input)
```

### Design Pattern Shortcuts

For convenience, provide pattern name shortcuts:

```utlx
output xsd {pattern: "venetian-blind"}
# Equivalent to: {ElementDeclaration: local, TypeDeclaration: global}

output xsd {pattern: "russian-doll"}
# Equivalent to: {ElementDeclaration: local, TypeDeclaration: local}

output xsd {pattern: "salami-slice"}
# Equivalent to: {ElementDeclaration: global, TypeDeclaration: local}

output xsd {pattern: "garden-of-eden"}
# Equivalent to: {ElementDeclaration: global, TypeDeclaration: global}
```

### JSON Schema Format Options

```utlx
output jsch {
  version: "draft-07",           # or: "draft-04", "2020-12"
  $id: "https://example.com/schema.json",
  title: "Customer Schema",
  includeExamples: true,
  includeDefaults: true
}
```

### Default Behaviors

If format options not specified:

**XSD Defaults:**
- `ElementDeclaration: local`
- `TypeDeclaration: global`
- `pattern: "venetian-blind"` (industry best practice)
- `version: "1.0"` (widest tool compatibility)

**JSCH Defaults:**
- `version: "draft-07"` (most widely supported)
- `includeExamples: true`
- `includeDefaults: true`

### Grammar Extension

**File:** `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`

```kotlin
// Existing: input json, input xml
// NEW: input xsd {options}

private fun parseOutputDeclaration(): Pair<FormatType, Map<String, Any>> {
    expect(TokenType.OUTPUT)
    val formatType = parseFormatType()

    // Parse optional format options
    val options = if (check(TokenType.LBRACE)) {
        parseFormatOptions()
    } else {
        emptyMap()
    }

    return formatType to options
}

private fun parseFormatOptions(): Map<String, Any> {
    expect(TokenType.LBRACE)
    val options = mutableMapOf<String, Any>()

    while (!check(TokenType.RBRACE)) {
        val key = expect(TokenType.IDENTIFIER).value
        expect(TokenType.COLON)
        val value = parseFormatOptionValue() // String, Number, Boolean
        options[key] = value

        if (!check(TokenType.RBRACE)) {
            expect(TokenType.COMMA)
        }
    }

    expect(TokenType.RBRACE)
    return options
}
```

---

## Architecture

### Module Structure

```
formats/
├── xsd/                              # NEW: XSD format module
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/org/apache/utlx/formats/xsd/
│       │   ├── XSDParser.kt          # Parse .xsd → UDM
│       │   ├── XSDSerializer.kt      # UDM → .xsd (with pattern support)
│       │   ├── XSDValidator.kt       # Validate XSD structure
│       │   ├── XSDModel.kt           # Data classes (Element, Type, Annotation)
│       │   └── PatternGenerator.kt   # Generate specific design patterns
│       └── test/kotlin/.../xsd/
│           ├── XSDParserTest.kt
│           ├── XSDSerializerTest.kt
│           └── PatternGeneratorTest.kt
│
├── jsch/                             # NEW: JSON Schema module
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/org/apache/utlx/formats/jsch/
│       │   ├── JSONSchemaParser.kt   # Parse JSON Schema → UDM
│       │   ├── JSONSchemaSerializer.kt # UDM → JSON Schema
│       │   ├── JSONSchemaValidator.kt  # Validate schema structure
│       │   └── JSONSchemaModel.kt    # Data classes (Schema, Property, Definition)
│       └── test/kotlin/.../jsch/
│           ├── JSONSchemaParserTest.kt
│           └── JSONSchemaSerializerTest.kt
│
├── xml/                              # EXISTING (reused by XSD)
├── json/                             # EXISTING (reused by JSCH)
└── yaml/                             # EXISTING
```

### Standard Library Structure

```
stdlib/src/main/kotlin/org/apache/utlx/stdlib/
├── xsd/                              # NEW: XSD functions
│   ├── XSDNavigationFunctions.kt     # getElements, getTypes, getAnnotations
│   ├── XSDDocumentationFunctions.kt  # extractDocs, getDocumentation
│   ├── XSDTransformFunctions.kt      # toJSONSchema, toDataContract
│   └── XSDTypeConversionFunctions.kt # typeToJSONSchema, typeToSQL
│
└── jsch/                             # NEW: JSON Schema functions
    ├── JSCHNavigationFunctions.kt    # getProperties, getDefinitions
    ├── JSCHValidationFunctions.kt    # validateSchema, validateData
    ├── JSCHTransformFunctions.kt     # toXSD, toDataContract
    └── JSCHTypeConversionFunctions.kt # typeToXSD, typeToSQL
```

### UDM Representation

XSD and JSON Schema files are converted to UDM using **schema-aware metadata**:

**Example XSD Element in UDM:**

```kotlin
UDM.Object(
    name = "element",
    properties = mapOf(
        "__elementName" to UDM.Scalar("customer"),
        "__elementType" to UDM.Scalar("CustomerType"),
        "__minOccurs" to UDM.Scalar("1"),
        "__maxOccurs" to UDM.Scalar("1"),
        "annotation" to UDM.Object(
            properties = mapOf(
                "documentation" to UDM.Scalar("Customer entity in CRM system")
            )
        )
    ),
    attributes = mapOf(
        "name" to "customer",
        "type" to "CustomerType"
    ),
    metadata = mapOf(
        "__schemaType" to "xsd-element",
        "__namespace" to "http://example.com/customer"
    )
)
```

**Example JSON Schema Property in UDM:**

```kotlin
UDM.Object(
    name = "property",
    properties = mapOf(
        "type" to UDM.Scalar("string"),
        "description" to UDM.Scalar("Customer email address"),
        "format" to UDM.Scalar("email"),
        "examples" to UDM.Array(listOf(
            UDM.Scalar("customer@example.com")
        ))
    ),
    metadata = mapOf(
        "__schemaType" to "jsch-property",
        "__propertyName" to "email"
    )
)
```

---

## Standard Library Functions

### XSD Functions (stdlib/xsd/)

#### Navigation Functions

| Function | Signature | Description | Example |
|----------|-----------|-------------|---------|
| `xsdGetElements` | `(schema: UDM) -> Array<UDM>` | Get all element declarations | `xsdGetElements($input) \|> map(e => e.@name)` |
| `xsdGetTypes` | `(schema: UDM) -> Array<UDM>` | Get all type definitions | `xsdGetTypes($input) \|> filter(t => contains(t.@name, "Customer"))` |
| `xsdGetAnnotations` | `(element: UDM) -> UDM` | Get xs:annotation node | `xsdGetAnnotations(elem).documentation` |
| `xsdGetComplexTypes` | `(schema: UDM) -> Array<UDM>` | Get complex type definitions | `xsdGetComplexTypes($input)` |
| `xsdGetSimpleTypes` | `(schema: UDM) -> Array<UDM>` | Get simple type definitions | `xsdGetSimpleTypes($input)` |
| `xsdGetNamespace` | `(schema: UDM) -> String` | Get targetNamespace | `xsdGetNamespace($input) => "http://example.com"` |

#### Documentation Functions

| Function | Signature | Description | Example |
|----------|-----------|-------------|---------|
| `xsdExtractDocs` | `(element: UDM) -> String` | Extract documentation text | `xsdExtractDocs(elem) => "Customer entity..."` |
| `xsdGetDocumentation` | `(element: UDM, lang?: String) -> String` | Get documentation by language | `xsdGetDocumentation(elem, "en")` |
| `xsdGetAppInfo` | `(element: UDM) -> UDM` | Get xs:appinfo data | `xsdGetAppInfo(elem).database.table` |
| `xsdGetAllDocs` | `(element: UDM) -> Object` | Get all documentation (multilingual) | `xsdGetAllDocs(elem) => {en: "...", es: "..."}` |

#### Type Conversion Functions

| Function | Signature | Description | Example |
|----------|-----------|-------------|---------|
| `xsdTypeToJSONSchemaType` | `(xsdType: String) -> String` | Convert XSD type to JSON Schema | `xsdTypeToJSONSchemaType("xs:string") => "string"` |
| `xsdTypeToSQLType` | `(xsdType: String) -> String` | Convert XSD type to SQL | `xsdTypeToSQLType("xs:int") => "INTEGER"` |
| `xsdTypeToDataContractType` | `(xsdType: String) -> String` | Convert to DataContract type | `xsdTypeToDataContractType("xs:decimal") => "decimal"` |

#### Transform Functions

| Function | Signature | Description | Example |
|----------|-----------|-------------|---------|
| `xsdToJSONSchema` | `(schema: UDM, options?: Object) -> UDM` | Full XSD → JSON Schema | `xsdToJSONSchema($input, {draft: "2020-12"})` |
| `xsdToDataContract` | `(schema: UDM) -> UDM` | XSD → DataContract YAML | `xsdToDataContract($input)` |

#### Validation Functions

| Function | Signature | Description | Example |
|----------|-----------|-------------|---------|
| `xsdIsComplexType` | `(element: UDM) -> Boolean` | Check if complex type | `xsdIsComplexType(elem) => true` |
| `xsdIsSimpleType` | `(element: UDM) -> Boolean` | Check if simple type | `xsdIsSimpleType(elem) => false` |
| `xsdGetPattern` | `(element: UDM) -> String?` | Get pattern for design style | `xsdGetPattern($input) => "venetian-blind"` |

### JSON Schema Functions (stdlib/jsch/)

#### Navigation Functions

| Function | Signature | Description | Example |
|----------|-----------|-------------|---------|
| `jschGetProperties` | `(schema: UDM) -> Object` | Get properties object | `jschGetProperties($input).customer` |
| `jschGetDefinitions` | `(schema: UDM) -> Object` | Get definitions/components | `jschGetDefinitions($input)` |
| `jschGetRequired` | `(schema: UDM) -> Array<String>` | Get required fields | `jschGetRequired($input) => ["id", "name"]` |
| `jschResolveRef` | `(schema: UDM, ref: String) -> UDM` | Resolve $ref pointer | `jschResolveRef($input, "#/definitions/Customer")` |
| `jschGetSchema` | `(schema: UDM) -> String` | Get $schema version | `jschGetSchema($input) => "draft-07"` |

#### Validation Functions

| Function | Signature | Description | Example |
|----------|-----------|-------------|---------|
| `jschValidateSchema` | `(schema: UDM) -> ValidationResult` | Validate schema structure | `jschValidateSchema($input).valid => true` |
| `jschValidateData` | `(data: UDM, schema: UDM) -> ValidationResult` | Validate data against schema | `jschValidateData($data, $schema).errors` |

#### Type Conversion Functions

| Function | Signature | Description | Example |
|----------|-----------|-------------|---------|
| `jschTypeToXSDType` | `(jschType: String) -> String` | Convert to XSD type | `jschTypeToXSDType("string") => "xs:string"` |
| `jschTypeToSQLType` | `(jschType: String) -> String` | Convert to SQL type | `jschTypeToSQLType("integer") => "INTEGER"` |
| `jschTypeToDataContractType` | `(jschType: String) -> String` | Convert to DataContract | `jschTypeToDataContractType("number") => "decimal"` |

#### Transform Functions

| Function | Signature | Description | Example |
|----------|-----------|-------------|---------|
| `jschToXSD` | `(schema: UDM, namespace: String, options?: Object) -> UDM` | JSON Schema → XSD | `jschToXSD($input, "http://ex.com", {pattern: "venetian-blind"})` |
| `jschToDataContract` | `(schema: UDM) -> UDM` | JSON Schema → DataContract | `jschToDataContract($input)` |

#### Documentation Functions

| Function | Signature | Description | Example |
|----------|-----------|-------------|---------|
| `jschGetDescription` | `(property: UDM) -> String` | Get description field | `jschGetDescription(prop)` |
| `jschGetExamples` | `(property: UDM) -> Array<Any>` | Get examples array | `jschGetExamples(prop)[0]` |
| `jschGetDefault` | `(property: UDM) -> Any` | Get default value | `jschGetDefault(prop)` |

---

## Implementation Phases

### Phase 1: Core Infrastructure (Week 1-2)

**Goal:** Add XSD and JSCH to format system, implement basic parsing

**Tasks:**
1. Extend `FormatType` enum
2. Create `formats/xsd/` module
3. Create `formats/jsch/` module
4. Implement `XSDParser` (reuses XMLParser internally)
5. Implement `JSONSchemaParser` (reuses JSONParser internally)
6. Add metadata tagging (`__schemaType: "xsd-element"`)
7. Unit tests for parsing

**Deliverables:**
- `input xsd` parses `.xsd` files to UDM
- `input jsch` parses `.schema.json` files to UDM
- Metadata preserves schema structure

**Files:**
```
✏️ modules/core/.../ast_nodes.kt (line 73: add XSD, JSCH)
📁 formats/xsd/build.gradle.kts (new)
📁 formats/xsd/src/main/kotlin/.../XSDParser.kt (new)
📁 formats/xsd/src/main/kotlin/.../XSDModel.kt (new)
📁 formats/jsch/build.gradle.kts (new)
📁 formats/jsch/src/main/kotlin/.../JSONSchemaParser.kt (new)
📁 formats/jsch/src/main/kotlin/.../JSONSchemaModel.kt (new)
```

---

### Phase 2: Serialization with Pattern Support (Week 3-4)

**Goal:** Implement serializers with XSD design pattern control

**Tasks:**
1. Implement `XSDSerializer` with pattern support:
   - `PatternGenerator` abstract class
   - `RussianDollGenerator` (local elements, local types)
   - `SalamiSliceGenerator` (global elements, local types)
   - `VenetianBlindGenerator` (local elements, global types) ⭐ default
   - `GardenOfEdenGenerator` (global elements, global types)
2. Implement `JSONSchemaSerializer` (draft-07, 2020-12 support)
3. Parse format options from AST
4. Pass options to serializers
5. Round-trip tests

**Deliverables:**
- `output xsd` generates valid W3C XSD
- `output xsd {pattern: "venetian-blind"}` works
- `output xsd {ElementDeclaration: global, TypeDeclaration: local}` works
- `output jsch` generates valid JSON Schema
- `output jsch {version: "2020-12"}` works
- Round-trip tests pass

**Files:**
```
📁 formats/xsd/src/main/kotlin/.../XSDSerializer.kt (new)
📁 formats/xsd/src/main/kotlin/.../PatternGenerator.kt (new)
📁 formats/xsd/src/main/kotlin/.../RussianDollGenerator.kt (new)
📁 formats/xsd/src/main/kotlin/.../SalamiSliceGenerator.kt (new)
📁 formats/xsd/src/main/kotlin/.../VenetianBlindGenerator.kt (new)
📁 formats/xsd/src/main/kotlin/.../GardenOfEdenGenerator.kt (new)
📁 formats/jsch/src/main/kotlin/.../JSONSchemaSerializer.kt (new)
✏️ modules/core/.../parser_impl.kt (parseFormatOptions())
```

---

### Phase 3: CLI Integration (Week 5)

**Goal:** CLI auto-detection and format options support

**Tasks:**
1. Update `detectFormat()` (extension + content detection)
2. Update `parseData()` switch for XSD/JSCH
3. Update `serializeOutput()` to pass format options
4. Add CLI help text
5. Integration tests

**Deliverables:**
- CLI auto-detects `.xsd` → `"xsd"`, `.schema.json` → `"jsch"`
- Content detection works (`<xs:schema`, `"$schema"`)
- Manual override: `--input-format xsd --output-format jsch`
- CLI help documents XSD pattern options

**Files:**
```
✏️ modules/cli/.../TransformCommand.kt (detectFormat, parseData, serializeOutput)
```

---

### Phase 4: XSD Standard Library (Week 6-7)

**Goal:** Implement XSD-specific stdlib functions

**Tasks:**
1. Create `stdlib/xsd/` directory
2. Implement 15+ XSD functions (see table above)
3. Add @UTLXFunction annotations
4. Register in FunctionRegistry
5. Unit tests for all functions
6. Generate function documentation

**Deliverables:**
- 15+ XSD functions callable from scripts
- Functions appear in `utlx functions` output
- Comprehensive test coverage

**Files:**
```
📁 stdlib/src/main/kotlin/.../xsd/XSDNavigationFunctions.kt (new)
📁 stdlib/src/main/kotlin/.../xsd/XSDDocumentationFunctions.kt (new)
📁 stdlib/src/main/kotlin/.../xsd/XSDTransformFunctions.kt (new)
📁 stdlib/src/main/kotlin/.../xsd/XSDTypeConversionFunctions.kt (new)
📁 stdlib/src/test/kotlin/.../xsd/XSDFunctionsTest.kt (new)
```

---

### Phase 5: JSCH Standard Library (Week 8)

**Goal:** Implement JSON Schema-specific stdlib functions

**Tasks:**
1. Create `stdlib/jsch/` directory
2. Implement 15+ JSCH functions (see table above)
3. Add @UTLXFunction annotations
4. Register in FunctionRegistry
5. Unit tests for all functions

**Deliverables:**
- 15+ JSCH functions callable from scripts
- Validation functions work with real schemas

**Files:**
```
📁 stdlib/src/main/kotlin/.../jsch/JSCHNavigationFunctions.kt (new)
📁 stdlib/src/main/kotlin/.../jsch/JSCHValidationFunctions.kt (new)
📁 stdlib/src/main/kotlin/.../jsch/JSCHTransformFunctions.kt (new)
📁 stdlib/src/main/kotlin/.../jsch/JSCHTypeConversionFunctions.kt (new)
📁 stdlib/src/test/kotlin/.../jsch/JSCHFunctionsTest.kt (new)
```

---

### Phase 6: Testing & Documentation (Week 9-10)

**Goal:** Comprehensive testing and documentation

**Tasks:**
1. Create conformance test suite (25+ tests)
2. XSD pattern conversion tests (all 4 patterns)
3. Real-world schema tests (`customer.xsd`, `ORDERS05_IDOC_Schema.xsd`)
4. Documentation:
   - `docs/formats/xsd.md`
   - `docs/formats/jsch.md`
   - Update `docs/reference/stdlib-reference.md`
   - Update `CLAUDE.md`
5. Tutorial examples
6. Performance benchmarking

**Deliverables:**
- 25+ conformance tests (100% passing)
- Complete documentation
- Tutorial examples
- Performance within 10-20% of XML/JSON

**Files:**
```
📁 conformance-suite/tests/schemas/xsd/ (10+ tests)
📁 conformance-suite/tests/schemas/jsch/ (10+ tests)
📁 conformance-suite/tests/schemas/patterns/ (5 pattern tests)
📁 docs/formats/xsd.md (new)
📁 docs/formats/jsch.md (new)
✏️ docs/reference/stdlib-reference.md (add XSD/JSCH sections)
✏️ CLAUDE.md (add format examples)
📁 examples/schema-transformations/ (5+ examples)
```

---

## Testing Strategy

### 1. Unit Tests

**XSD Parser Tests:**
- Parse basic XSD (elements, attributes)
- Parse complex types (sequences, choices, all)
- Parse simple types (restrictions, patterns, enumerations)
- Parse annotations (documentation, appinfo)
- Parse namespaces (targetNamespace, imports, includes)
- Parse all 4 design patterns
- Error handling (malformed XSD)

**XSD Serializer Tests:**
- Generate Russian Doll pattern
- Generate Salami Slice pattern
- Generate Venetian Blind pattern (default)
- Generate Garden of Eden pattern
- Round-trip: parse → serialize → parse
- Format options parsing

**JSON Schema Tests:**
- Parse draft-07 JSON Schema
- Parse 2020-12 JSON Schema
- Parse with $ref references
- Parse nested definitions
- Generate draft-07 output
- Generate 2020-12 output
- Round-trip tests

### 2. Integration Tests (CLI)

```bash
# XSD → JSON Schema
utlx transform customer.xsd xsd-to-jsch.utlx -o customer.schema.json

# JSON Schema → XSD (Venetian Blind)
utlx transform api.schema.json jsch-to-xsd.utlx -o api.xsd

# XSD pattern conversion (Russian Doll → Venetian Blind)
utlx transform legacy.xsd convert-pattern.utlx -o modern.xsd

# Auto-detection
utlx transform schema.xsd transform.utlx  # Auto-detects XSD
```

### 3. Conformance Tests

**Minimum 25 tests:**

**XSD Tests (10):**
- `01_parse_basic_xsd.yaml` - Parse simple XSD
- `02_parse_annotations.yaml` - Parse xs:documentation
- `03_parse_namespaces.yaml` - Parse with targetNamespace
- `04_generate_russian_doll.yaml` - Output Russian Doll pattern
- `05_generate_venetian_blind.yaml` - Output Venetian Blind (default)
- `06_generate_salami_slice.yaml` - Output Salami Slice
- `07_generate_garden_of_eden.yaml` - Output Garden of Eden
- `08_xsd_to_jsch.yaml` - XSD → JSON Schema conversion
- `09_xsd_documentation_extract.yaml` - Extract docs with stdlib
- `10_customer_xsd_real_world.yaml` - Real customer.xsd file

**JSCH Tests (10):**
- `01_parse_draft07.yaml` - Parse draft-07 schema
- `02_parse_2020_12.yaml` - Parse 2020-12 schema
- `03_parse_refs.yaml` - Parse with $ref
- `04_generate_draft07.yaml` - Output draft-07
- `05_generate_2020_12.yaml` - Output 2020-12
- `06_jsch_to_xsd.yaml` - JSON Schema → XSD
- `07_jsch_validation.yaml` - Validate schema structure
- `08_jsch_data_validation.yaml` - Validate data against schema
- `09_jsch_navigation.yaml` - Use navigation stdlib functions
- `10_openapi_schema.yaml` - Real OpenAPI schema subset

**Pattern Conversion Tests (5):**
- `11_russian_to_venetian.yaml` - Convert patterns
- `12_salami_to_venetian.yaml`
- `13_garden_to_venetian.yaml`
- `14_preserve_docs_pattern_change.yaml`
- `15_complex_pattern_conversion.yaml`

### 4. Performance Tests

**Benchmarks:**
- Parse 1KB XSD: < 10ms
- Parse 100KB XSD: < 100ms
- Parse 1MB XSD: < 1s
- Generate XSD (any pattern): < 20ms
- Pattern conversion: < 30ms
- Stdlib function call overhead: < 1ms

---

## Examples

### Example 1: XSD → JSON Schema with Documentation

**Input:** `customer.xsd`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://example.com/customer"
           xmlns="http://example.com/customer"
           elementFormDefault="qualified">

  <xs:element name="customer" type="CustomerType">
    <xs:annotation>
      <xs:documentation xml:lang="en">
        Customer entity in the CRM system.
      </xs:documentation>
      <xs:appinfo>
        <database table="customers" primaryKey="id"/>
      </xs:appinfo>
    </xs:annotation>
  </xs:element>

  <xs:complexType name="CustomerType">
    <xs:sequence>
      <xs:element name="id" type="xs:int"/>
      <xs:element name="name" type="xs:string"/>
      <xs:element name="email" type="xs:string" minOccurs="0"/>
    </xs:sequence>
  </xs:complexType>
</xs:schema>
```

**Transformation:** `xsd-to-jsch.utlx`

```utlx
%utlx 1.0
input xsd
output jsch {version: "draft-07"}
---
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": xsdGetNamespace($input),
  "title": "Customer Schema",
  "description": xsdExtractDocs(xsdGetElements($input)[0]),
  "type": "object",
  "properties": fromEntries(
    xsdGetElements(xsdGetTypes($input)[0]) |> map(elem => [
      elem.@name,
      {
        "type": xsdTypeToJSONSchemaType(elem.@type),
        "description": xsdExtractDocs(elem)
      }
    ])
  ),
  "required": xsdGetElements(xsdGetTypes($input)[0])
    |> filter(e => e.@minOccurs != "0")
    |> map(e => e.@name)
}
```

**Output:** `customer.schema.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "http://example.com/customer",
  "title": "Customer Schema",
  "description": "Customer entity in the CRM system.",
  "type": "object",
  "properties": {
    "id": {
      "type": "integer",
      "description": ""
    },
    "name": {
      "type": "string",
      "description": ""
    },
    "email": {
      "type": "string",
      "description": ""
    }
  },
  "required": ["id", "name"]
}
```

---

### Example 2: JSON Schema → XSD (Venetian Blind Pattern)

**Input:** `api.schema.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Order API Schema",
  "type": "object",
  "properties": {
    "orderId": {"type": "string"},
    "customerId": {"type": "integer"},
    "total": {"type": "number"}
  },
  "required": ["orderId", "customerId"]
}
```

**Transformation:** `jsch-to-xsd.utlx`

```utlx
%utlx 1.0
input jsch
output xsd {
  pattern: "venetian-blind",
  namespace: "http://api.example.com/order",
  version: "1.0"
}
---
jschToXSD($input, "http://api.example.com/order", {
  pattern: "venetian-blind"
})
```

**Output:** `order.xsd` (Venetian Blind pattern)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://api.example.com/order"
           xmlns="http://api.example.com/order"
           elementFormDefault="qualified">

  <!-- Root element (local) -->
  <xs:element name="order" type="OrderType"/>

  <!-- Global type definition (Venetian Blind) -->
  <xs:complexType name="OrderType">
    <xs:sequence>
      <xs:element name="orderId" type="xs:string"/>
      <xs:element name="customerId" type="xs:int"/>
      <xs:element name="total" type="xs:decimal" minOccurs="0"/>
    </xs:sequence>
  </xs:complexType>

</xs:schema>
```

---

### Example 3: XSD Pattern Conversion (Russian Doll → Venetian Blind)

**Input:** `legacy.xsd` (Russian Doll)

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="order">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="item" maxOccurs="unbounded">
          <xs:complexType>
            <xs:sequence>
              <xs:element name="sku" type="xs:string"/>
              <xs:element name="quantity" type="xs:int"/>
            </xs:sequence>
          </xs:complexType>
        </xs:element>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

**Transformation:** `convert-pattern.utlx`

```utlx
%utlx 1.0
input xsd
output xsd {pattern: "venetian-blind"}
---
$input  # Identity transformation, pattern change via format options
```

**Output:** `modern.xsd` (Venetian Blind)

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <!-- Root element (local) -->
  <xs:element name="order" type="OrderType"/>

  <!-- Global types (Venetian Blind) -->
  <xs:complexType name="OrderType">
    <xs:sequence>
      <xs:element name="item" type="ItemType" maxOccurs="unbounded"/>
    </xs:sequence>
  </xs:complexType>

  <xs:complexType name="ItemType">
    <xs:sequence>
      <xs:element name="sku" type="xs:string"/>
      <xs:element name="quantity" type="xs:int"/>
    </xs:sequence>
  </xs:complexType>
</xs:schema>
```

---

### Example 4: Extract Schema Documentation

**Transformation:** `extract-docs.utlx`

```utlx
%utlx 1.0
input xsd
output markdown
---
let elements = xsdGetElements($input);

"# Schema Documentation\n\n" +
"**Namespace:** " + xsdGetNamespace($input) + "\n\n" +
"## Elements\n\n" +
elements |> map(elem =>
  "### " + elem.@name + "\n\n" +
  "- **Type:** " + elem.@type + "\n" +
  "- **Description:** " + xsdExtractDocs(elem) + "\n\n" +
  (if (xsdGetAppInfo(elem)) {
    let appInfo = xsdGetAppInfo(elem);
    "- **Database Table:** " + appInfo.database.@table + "\n" +
    "- **Primary Key:** " + appInfo.database.@primaryKey + "\n\n"
  } else "")
) |> join("")
```

**Output:** `schema-docs.md`

```markdown
# Schema Documentation

**Namespace:** http://example.com/customer

## Elements

### customer

- **Type:** CustomerType
- **Description:** Customer entity in the CRM system.
- **Database Table:** customers
- **Primary Key:** id
```

---

## Success Criteria

### Functional Requirements

**Parsing:**
- ✅ Parse valid XSD files (all 4 patterns) to UDM
- ✅ Parse valid JSON Schema files (draft-07, 2020-12) to UDM
- ✅ Preserve annotations, documentation, appinfo
- ✅ Preserve namespaces, imports, includes

**Serialization:**
- ✅ Generate valid W3C XSD 1.0 files
- ✅ Generate XSD in all 4 design patterns (controlled via format options)
- ✅ Generate valid JSON Schema (draft-07, 2020-12)
- ✅ Round-trip transformations preserve structure
- ✅ Default to Venetian Blind pattern (best practice)

**CLI:**
- ✅ Auto-detect `.xsd` extension → XSD format
- ✅ Auto-detect `.schema.json`, `.jschema` → JSCH format
- ✅ Content-based detection (`<xs:schema`, `"$schema"`)
- ✅ Format options parsing works
- ✅ Help text documents patterns and options

**Standard Library:**
- ✅ 15+ XSD functions implemented
- ✅ 15+ JSCH functions implemented
- ✅ Functions registered in FunctionRegistry
- ✅ Callable from UTL-X scripts

### Quality Requirements

**Testing:**
- ✅ 25+ conformance tests (100% passing)
- ✅ Unit test coverage > 80%
- ✅ Integration tests cover all CLI workflows
- ✅ Pattern conversion tests (all combinations)

**Performance:**
- ✅ Parse/serialize within 20% of XML/JSON performance
- ✅ Pattern conversion overhead < 10ms
- ✅ Stdlib function call overhead < 1ms

**Documentation:**
- ✅ Complete format docs (`xsd.md`, `jsch.md`)
- ✅ Stdlib reference updated
- ✅ Tutorial examples (5+)
- ✅ CLAUDE.md updated

**Backward Compatibility:**
- ✅ Existing `input xml` still works for XSD files (generic XML mode)
- ✅ Existing `input json` still works for JSON Schema files
- ✅ No breaking changes to existing tests (305/305 passing)
- ✅ No changes to UDM core structure

---

## Open Questions

### For User Decision

**Q1: JSON Schema Version Preference**
- Primary target: draft-07 (most widely supported) OR 2020-12 (latest)?
- Support both as output options?

**Q2: XSD Version Support**
- XSD 1.0 only (widest compatibility) OR also XSD 1.1?
- XSD 1.1 adds assertions, type alternatives (complex but powerful)

**Q3: Validation Library**
- Include JSON Schema validation library for `jschValidateData()`?
- Options: networknt/json-schema-validator, everit-org/json-schema
- Trade-off: 2MB dependency vs. powerful validation

**Q4: Pattern Detection**
- Should `xsdGetPattern($input)` auto-detect current pattern?
- Useful for logging: "Converting from russian-doll to venetian-blind"

**Q5: Default Pattern Override**
- Allow user to set global default via config file?
- Example: `.utlxrc` → `xsd.defaultPattern: "garden-of-eden"`

---

## Timeline

| Phase | Duration | Weeks | Key Deliverable |
|-------|----------|-------|-----------------|
| **Phase 1: Core Infrastructure** | 2 weeks | 1-2 | Parsing works |
| **Phase 2: Serialization + Patterns** | 2 weeks | 3-4 | All 4 patterns generate correctly |
| **Phase 3: CLI Integration** | 1 week | 5 | CLI auto-detection works |
| **Phase 4: XSD Stdlib** | 2 weeks | 6-7 | 15+ XSD functions |
| **Phase 5: JSCH Stdlib** | 1 week | 8 | 15+ JSCH functions |
| **Phase 6: Testing & Docs** | 2 weeks | 9-10 | Production-ready |

**Total: 10 weeks (2.5 months)**

**Milestones:**
- ✅ Week 2: Basic parsing complete
- ✅ Week 4: Pattern generation working
- ✅ Week 5: CLI integration complete
- ✅ Week 7: XSD stdlib complete
- ✅ Week 8: JSCH stdlib complete
- ✅ Week 10: Production-ready with docs

---

## Dependencies

**External Libraries:**
- None required for core functionality (reuse existing parsers)
- Optional: JSON Schema validator (for `jschValidateData()` - Phase 5)

**Internal Dependencies:**
- XML parser (`formats/xml/`) - EXISTING
- JSON parser (`formats/json/`) - EXISTING
- UDM core (`modules/core/udm/`) - EXISTING
- Stdlib infrastructure (`stdlib/`) - EXISTING
- Parser format options support - NEW (Phase 2)

---

## Risks & Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **XSD complexity (4 patterns)** | Medium | High | Phased approach: start with Venetian Blind, add others incrementally |
| **JSON Schema version fragmentation** | Medium | Medium | Focus on draft-07 (most supported), add 2020-12 later |
| **Pattern conversion complexity** | Medium | Medium | Start with simple conversions, add advanced cases iteratively |
| **Circular references** | Low | Medium | Implement $ref resolution with cycle detection |
| **Performance overhead** | Low | Medium | Benchmark early (Phase 1), optimize if needed |
| **Breaking changes** | Low | High | Extensive testing, maintain backward compatibility |

---

## Future Enhancements (Out of Scope)

**YAML-based Schema Formats (Phase 7+):**
- RAML (RESTful API Modeling Language)
- OpenAPI 3.x (Swagger)
- AsyncAPI (Event-driven APIs)

**Advanced XSD Features:**
- XSD 1.1 assertions
- Substitution groups
- Abstract types and type derivation
- Complex namespace hierarchies

**Advanced JSCH Features:**
- JSON Schema bundling ($ref resolution)
- Custom format validators
- Schema composition (allOf, anyOf, oneOf optimization)

**Tooling:**
- VS Code extension: autocomplete for format options
- Schema diff tool: `utlx schema diff old.xsd new.xsd`
- Schema documentation generator: `utlx schema docs schema.xsd -o docs/`

---

## References

- **W3C XML Schema Primer:** https://www.w3.org/TR/xmlschema-0/
- **JSON Schema Specification:** https://json-schema.org/specification
- **XSD Design Patterns:** `docs/architecture/decisions/xsd-discussions-style.md`
- **XML Documentation Analysis:** `docs/architecture/decisions/xml-documentation-analysis.md`
- **DataContract Tests:** `conformance-suite/tests/datacontract/` (9 tests)
- **Existing XSD Files:** `test-data/customer.xsd`, `examples/IDOC/ORDERS05_IDOC_Schema.xsd`

---

## Appendix A: Type Conversion Tables

### XSD → JSON Schema Type Mapping

| XSD Type | JSON Schema Type | Notes |
|----------|------------------|-------|
| `xs:string` | `"string"` | Direct mapping |
| `xs:int`, `xs:integer` | `"integer"` | Direct mapping |
| `xs:decimal`, `xs:float`, `xs:double` | `"number"` | Numeric types |
| `xs:boolean` | `"boolean"` | Direct mapping |
| `xs:date`, `xs:dateTime` | `"string"` + `"format": "date"` | Format annotation |
| `xs:anyURI` | `"string"` + `"format": "uri"` | Format annotation |
| `xs:base64Binary` | `"string"` + `"contentEncoding": "base64"` | Content encoding |
| Complex type | `"object"` | Nested properties |
| Sequence | `"object"` + `"properties"` | Element sequence |
| Choice | `"oneOf"` | Alternative schemas |
| All | `"object"` | All properties required |

### JSON Schema → XSD Type Mapping

| JSON Schema Type | XSD Type | Notes |
|------------------|----------|-------|
| `"string"` | `xs:string` | Default mapping |
| `"string"` + `"format": "date"` | `xs:date` | Format-aware |
| `"string"` + `"format": "date-time"` | `xs:dateTime` | Format-aware |
| `"string"` + `"format": "email"` | `xs:string` + pattern | Custom restriction |
| `"integer"` | `xs:int` | Default to int |
| `"number"` | `xs:decimal` | Decimal for precision |
| `"boolean"` | `xs:boolean` | Direct mapping |
| `"object"` | `xs:complexType` | Complex type |
| `"array"` | `xs:element` + `maxOccurs="unbounded"` | Repeating element |
| `"null"` | `xs:element` + `minOccurs="0"` | Optional element |
| `"oneOf"` | `xs:choice` | Alternative elements |

---

## Appendix B: Format Options Reference

### XSD Format Options

```utlx
output xsd {
  // Design pattern (shortcut)
  pattern: "venetian-blind"  // or: russian-doll, salami-slice, garden-of-eden

  // OR: Explicit control
  ElementDeclaration: global  // or: local
  TypeDeclaration: global     // or: local

  // Namespace configuration
  namespace: "http://example.com/schema"
  targetNamespacePrefix: "tns"

  // XSD version
  version: "1.0"  // or: "1.1"

  // Documentation options
  includeAnnotations: true
  includeAppInfo: true
  documentationLang: "en"  // Primary language for xs:documentation

  // Formatting
  prettyPrint: true
  indent: "  "
}
```

### JSCH Format Options

```utlx
output jsch {
  // JSON Schema version
  version: "draft-07"  // or: draft-04, 2020-12

  // Schema metadata
  $id: "https://example.com/schemas/customer.json"
  title: "Customer Schema"

  // Content options
  includeExamples: true
  includeDefaults: true
  includeDescription: true

  // Validation keywords
  additionalProperties: false

  // Formatting
  prettyPrint: true
  indent: 2
}
```

---

**END OF PROPOSAL**
