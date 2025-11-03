# Complete USDL Implementation - Session Summary 2025-10-27

## Overview

This session completed the comprehensive implementation of USDL (Universal Schema Definition Language) support in UTL-X, enabling format-agnostic schema generation with a unified % directive syntax.

## Executive Summary


## Example: Complete USDL Workflow

### Single USDL Definition
```utlx
%utlx 1.0
input json
output xsd %usdl 1.0  // or: output jsch %usdl 1.0
---
{
  %namespace: "http://example.com/customer",
  %title: "Customer Schema",
  %documentation: "Schema for customer information",

  %types: {
    Customer: {
      %kind: "structure",
      %documentation: "Customer entity",
      %fields: [
        {%name: "id", %type: "string", %required: true},
        {%name: "email", %type: "string", %required: false}
      ]
    },

    Status: {
      %kind: "enumeration",
      %values: ["active", "inactive"]
    }
  }
}
```

### Generated XSD
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://example.com/customer"
           elementFormDefault="qualified">
  <xs:complexType name="Customer">
    <xs:annotation>
      <xs:documentation>Customer entity</xs:documentation>
    </xs:annotation>
    <xs:sequence>
      <xs:element name="id" type="xs:string"/>
      <xs:element name="email" type="xs:string" minOccurs="0"/>
    </xs:sequence>
  </xs:complexType>
</xs:schema>
```

### Generated JSON Schema
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "Customer Schema",
  "description": "Schema for customer information",
  "$defs": {
    "Customer": {
      "type": "object",
      "description": "Customer entity",
      "properties": {
        "id": {"type": "string"},
        "email": {"type": "string"}
      },
      "required": ["id"]
    },
    "Status": {
      "type": "string",
      "enum": ["active", "inactive"]
    }
  }
}
```

**Key Point**: Same USDL definition generates both XSD and JSON Schema. Change `output xsd` to `output jsch` and get JSON Schema instead.

---

## Architecture Decisions

### Separate Schema Module
**Decision**: Created `schema/` module separate from `stdlib/`

**Rationale**:
- Different concerns: runtime (stdlib) vs design-time (schema)
- Independent versioning: stdlib evolves rapidly, USDL stable
- Smaller dependencies: transformation users don't need schema libraries

### Directive Syntax
**Decision**: All USDL keywords use `%` prefix

**Rationale**:
- No naming collisions (user can have field named "namespace")
- Better error messages with typo suggestions
- Syntax highlighting and autocomplete friendly
- Clear semantic distinction: %xxx = keyword, xxx = user data

### UDM Attribute Model
**Decision**: Attributes in UDM do NOT have @ prefix

**Rationale**:
- @ is UTL-X syntax only, not part of UDM model
- XMLSerializer expects clean attribute names
- Discovered during XSDSerializer testing
- Fixed across all serializers

### JSON Schema $defs vs definitions
**Decision**: Use `$defs` (2020-12) instead of `definitions` (draft-07)

**Rationale**:
- Modern keyword in 2020-12 draft
- Forward-compatible with future versions
- More semantic clarity

### Enumeration Support 
**Decision**: Implement enumerations in JSON Schema, defer for XSD

**Rationale**:
- Simpler in JSON Schema (string + enum)
- More complex in XSD (simpleType restriction)
- Demonstrates USDL's multi-kind support
- Can be added to XSD later

---

### Current Scope
1. **Type Coverage**: Structures and enumerations only
   - No union types
   - No composition types
   - No type references ($ref)

2. **Constraint Support**: Limited validation directives
   - No string length constraints
   - No pattern validation
   - No numeric ranges
   - No array constraints

3. **Advanced Features**: Not yet supported
   - XSD: No xs:choice, xs:all, complex content extension
   - JSON Schema: No oneOf/anyOf/allOf, no if/then/else
   - No schema imports/includes

### Future Work
- Tier 3 directives (format-specific constraints)
- Type composition and references
- Schema imports and includes
- Advanced validation rules
- Schema migration and diffing tools

### Use Cases
- **API Development**: Generate both XSD (SOAP) and JSON Schema (REST) from one definition
- **Data Integration**: Share schema across XML and JSON systems
- **Enterprise Architecture**: Central schema repository, multiple output formats
- **Migration Projects**: Convert between XSD and JSON Schema via USDL intermediate
- **Documentation**: Auto-generate schema docs from USDL %documentation


## Performance Characteristics

### Compilation
- USDL parsing: O(n) where n = number of directives
- Directive validation: O(n) with O(1) lookup
- Type transformation: O(t) where t = number of types

### Runtime
- Mode detection: O(1) - single property check
- USDL transformation: O(types × fields)
- Serialization: O(output size)

### Memory
- Schema catalog: ~80KB (loaded once)
- UDM intermediate: Proportional to schema size
- No caching overhead (stateless transformations)


